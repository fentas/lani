package si.lanisce.lani.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The controller against the fake GitHub: a token checked and kept sealed, a repository chosen (the first sync pulls),
 * the handover's lease taken, writes pushed once they stop (debounced, one commit), the background's push at once, and
 * mayWrite as the hosted mode's bridge asks it.
 */
class DataSyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fake: FakeGitHub
    private lateinit var scope: CoroutineScope
    private val box = MemoryBox()

    @Before fun setUp() {
        fake = FakeGitHub(token = "gho_testtoken_0123456789abcdef")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After fun tearDown() {
        scope.cancel()
        fake.close()
    }

    private fun dataSync(root: File) = DataSync(root, scope, box, "Pixel 8", clientId = "", relayUrl = "", api = fake.url, quietMs = 300, maxWaitMs = 2_000)

    private suspend fun until(what: String, secs: Double = 10.0, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + (secs * 1000).toLong()
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("waited for $what")
            delay(50)
        }
    }

    @Test fun `connect, pull, take over, and writes pushed once they stop`() = runBlocking {
        fake.nodePushes(mapOf("progress-db.json" to "{}", Lease.FILE to Lease.offered(Party(Party.PHONE), Party(Party.NODE, "jan-desktop"), java.time.Instant.now()).toJson()))
        val root = tmp.newFolder("lani")
        val d = dataSync(root)
        assertTrue("nothing connected: the hosted bridge may write", d.mayWrite)
        assertFalse(File(root, "sync/settings.json").exists()) // nothing written before it is used
        assertEquals("not a GitHub token", d.useToken("short", "token"))
        assertNull(d.useToken(fake.token, "token"))
        assertEquals("ana", d.settings.login)
        assertEquals(fake.token, box.values[SecretBox.GIT_TOKEN])
        assertFalse("the token is never in the settings file", File(root, "sync/settings.json").readText().contains(fake.token))
        val repo = d.repos().single()
        d.choose(repo)
        until("the first pull") { File(root, "data/progress-db.json").isFile && !d.busy }
        assertEquals(LeaseState.OFFERED, d.leaseState)
        assertFalse("handed over, not taken: the phone doesn't write yet", d.mayWrite)
        assertTrue(d.take() is SyncOutcome.Done)
        assertTrue(d.mayWrite)
        val commits = fake.commitCount()
        // three writes in a row: one push, after they stopped
        for (i in 1..3) {
            File(root, "data/progress-db.json").writeText("""{"sessions": $i}""")
            d.touched()
            delay(100)
        }
        assertEquals("not while the writes go on", commits, fake.commitCount())
        until("the debounced push") { fake.commitCount() == commits + 1 }
        assertEquals("""{"sessions": 3}""", fake.files()["progress-db.json"])
        delay(600)
        assertEquals("one push for the three", commits + 1, fake.commitCount())
        // to the background: what waits goes at once
        File(root, "data/app/notebook.json").apply { parentFile.mkdirs(); writeText("""{"notes": []}""") }
        d.touched()
        d.onBackground()
        until("the background's push", 3.0) { fake.files().containsKey("app/notebook.json") }
        // hand it back: the phone writes no more
        assertTrue(d.handOver() is SyncOutcome.Done)
        assertFalse(d.mayWrite)
        assertEquals(LeaseState.OFFERED, Lease.parse(fake.files()[Lease.FILE]).stateFor(Party(Party.NODE)))
        // disconnect: the token goes, the data stays
        d.disconnect()
        assertNull(box.values[SecretBox.GIT_TOKEN])
        assertTrue(File(root, "data/progress-db.json").isFile)
        assertTrue(d.mayWrite)
    }
}
