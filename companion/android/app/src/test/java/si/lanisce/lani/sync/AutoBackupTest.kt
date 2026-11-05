package si.lanisce.lani.sync

import androidx.work.NetworkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The automatic backup (docs/plans/05-built/last-small.md, "The app's backup"): when it runs and on which network
 * ([AutoBackupPolicy]), what it sends, how a failure is kept and backs off, one backup at a time, and the relay client's
 * timeouts. Against the fake relay of BackupTest, which answers as the relay does.
 */
class AutoBackupTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var relay: FakeRelay
    private var now = Instant.parse("2026-11-05T09:00:00Z")
    private val box = MemoryBox()
    private val planned = mutableListOf<Pair<BackupPlan, Boolean>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun setUp() {
        relay = FakeRelay()
        relay.now = { now.toEpochMilli() }
    }

    @After fun tearDown() {
        relay.server.shutdown()
        scope.cancel()
    }

    private fun phone(name: String): File = tmp.newFolder(name, "lani", "data").also { d ->
        File(d, "learner-profile.json").writeText("""{"learner": {"name": "Ana"}}""")
        File(d, "app").mkdirs()
        File(d, "app/notebook.json").writeText("""{"notes": ["likes cats"]}""")
    }.parentFile

    private fun sync(root: File, url: String = relay.url, clock: () -> Instant = { now }) =
        DataSync(root, scope, box, "Pixel 8", clientId = "", relayUrl = url, clock = clock, scheduleBackup = { p, fromWork -> planned += p to fromWork })

    private fun sent() = relay.store.values.sumOf { it.size }

    private val hour = Duration.ofHours(1).toMillis()

    // --- the policy ------------------------------------------------------------------------------------

    @Test fun `once a day, after a failure 1 h doubling to at most a day, unmetered unless a week has passed`() {
        assertEquals(listOf(1L, 2, 4, 8, 16, 24, 24, 24), (1..8).map { AutoBackupPolicy.backoff(it).toHours() })
        val t = Instant.parse("2026-11-05T09:00:00Z")
        assertEquals("none yet: now, on any network", BackupPlan(0, true), AutoBackupPolicy.plan(null, BackupRecord(), t))
        assertNull("a fresh one: nothing to do", AutoBackupPolicy.plan(t.minus(Duration.ofHours(19)), BackupRecord(), t))
        assertEquals("a day old: now, unmetered", BackupPlan(0, false), AutoBackupPolicy.plan(t.minus(Duration.ofHours(21)), BackupRecord(), t))
        assertEquals("over a week old: any network", BackupPlan(0, true), AutoBackupPolicy.plan(t.minus(Duration.ofDays(8)), BackupRecord(), t))
        val failed = BackupRecord(failedAt = t.toString(), problem = "UNREACHABLE", failures = 3)
        assertEquals("the third failure: 4 h on", BackupPlan(4 * hour, false), AutoBackupPolicy.plan(t.minus(Duration.ofDays(2)), failed, t))
        assertEquals("the tenth: a day on", BackupPlan(24 * hour, false), AutoBackupPolicy.plan(t.minus(Duration.ofDays(2)), failed.copy(failures = 10), t))
        val checked = BackupRecord(checkedAt = t.minus(Duration.ofHours(2)).toString())
        assertNull("data found unchanged counts as backed up", AutoBackupPolicy.plan(t.minus(Duration.ofDays(3)), checked, t))
    }

    // --- through DataSync ------------------------------------------------------------------------------

    @Test fun `going to the background plans the work when one is due, on the network the policy says`() = runBlocking {
        val d = sync(phone("one"))
        d.onBackground()
        assertTrue("not set up: nothing", planned.isEmpty())
        d.setUpCode()
        d.onBackground()
        assertEquals(BackupPlan(0, true) to false, planned.last())
        assertEquals(DataSync.AutoOutcome.DONE, d.runAutoBackup())
        planned.clear()
        d.onBackground()
        assertTrue("fresh: nothing", planned.isEmpty())
        now = now.plus(Duration.ofHours(21))
        d.onBackground()
        assertEquals("a day on: unmetered", BackupPlan(0, false) to false, planned.last())
    }

    @Test fun `the automatic backup sends the data, then not again while it is the same`() = runBlocking {
        val root = phone("one")
        val d = sync(root)
        d.setUpCode()
        assertEquals(DataSync.AutoOutcome.DONE, d.runAutoBackup())
        assertEquals(1, sent())
        assertNotNull(d.settings.lastBackup)
        assertEquals(BackupArchive.digest(File(root, "data")), d.record.digest)
        assertEquals("fresh", DataSync.AutoOutcome.NOT_DUE, d.runAutoBackup())
        now = now.plus(Duration.ofHours(21))
        assertEquals(DataSync.AutoOutcome.UNCHANGED, d.runAutoBackup())
        assertEquals("nothing sent", 1, sent())
        assertEquals(now, Instant.parse(d.record.checkedAt))
        now = now.plus(Duration.ofHours(21))
        File(root, "data/app/notebook.json").writeText("""{"notes": ["likes cats", "and dogs"]}""")
        assertEquals(DataSync.AutoOutcome.DONE, d.runAutoBackup())
        assertEquals(2, sent())
    }

    @Test fun `a failure is kept with its reason and backs off, and the next success clears it`() = runBlocking {
        val gone = MockWebServer().apply { start() }
        val dead = gone.url("/").toString().trimEnd('/')
        gone.shutdown()
        val d = sync(phone("one"), url = dead)
        d.setUpCode()
        assertEquals(DataSync.AutoOutcome.FAILED, d.runAutoBackup())
        assertEquals(1, d.record.failures)
        assertEquals(BackupProblem.UNREACHABLE, d.record.failedProblem)
        assertEquals(now, Instant.parse(d.record.failedAt))
        assertEquals("its next try, planned after the running one", BackupPlan(hour, true) to true, planned.last())
        assertEquals("backed off", DataSync.AutoOutcome.NOT_DUE, d.runAutoBackup())
        now = now.plus(Duration.ofHours(1))
        assertEquals(DataSync.AutoOutcome.FAILED, d.runAutoBackup())
        assertEquals(BackupPlan(2 * hour, true) to true, planned.last())
        d.setRelay(relay.url)
        now = now.plus(Duration.ofHours(2))
        assertEquals(DataSync.AutoOutcome.DONE, d.runAutoBackup())
        assertNull(d.record.failedAt)
        assertEquals(0, d.record.failures)
    }

    @Test fun `a phone whose clock is off fails with that reason`() = runBlocking {
        val d = sync(phone("one"), clock = { now.plusSeconds(600) })
        d.setUpCode()
        assertEquals(DataSync.AutoOutcome.FAILED, d.runAutoBackup())
        assertEquals(BackupProblem.CLOCK_OFF, d.record.failedProblem)
    }

    @Test fun `one backup at a time, whichever DataSync asks, the button, the work and the background wait for it`() = runBlocking {
        val root = phone("one")
        val app = sync(root)
        app.setUpCode()
        val gate = CountDownLatch(1)
        val fake = relay
        relay.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "PUT") gate.await(10, TimeUnit.SECONDS)
                return fake.dispatch(request)
            }
        }
        val manual = scope.async { app.backupNow() }
        repeat(100) { if (!DataSync.running) delay(50) }
        assertTrue("the button's backup runs", DataSync.running)
        app.onBackground()
        assertTrue("the background plans nothing while it runs", planned.isEmpty())
        val work = sync(root)
        assertEquals("the work's", DataSync.AutoOutcome.RUNNING, work.runAutoBackup())
        assertEquals("the button again", BackupProblem.RUNNING, (runCatching { work.backupNow() }.exceptionOrNull() as BackupError).problem)
        gate.countDown()
        manual.await()
        assertFalse(DataSync.running)
        assertEquals(1, sent())
    }

    @Test fun `a backup's work writes the files in a DataSync of its own, and the app's keeps what it wrote`() = runBlocking {
        val root = phone("one")
        val app = sync(root)
        app.setUpCode()
        val work = sync(root)
        assertEquals(DataSync.AutoOutcome.DONE, work.runAutoBackup())
        assertNull("the app's still has its own", app.settings.lastBackup)
        app.setRelay(relay.url)
        assertEquals("a write of the app's keeps the work's", work.settings.lastBackup, app.settings.lastBackup)
        app.refresh()
        assertEquals(work.record.digest, app.record.digest)
    }

    @Test fun `a relay that takes the body slowly is waited for past GitHub's ten seconds to write`() = runBlocking {
        assertEquals(10_000, GitHub.DEFAULT_CLIENT.writeTimeoutMillis)
        java.net.ServerSocket(0).use { server ->
            var conn: java.net.Socket? = null
            val accepting = kotlin.concurrent.thread { conn = runCatching { server.accept() }.getOrNull() } // it never reads
            val root = phone("one")
            File(root, "data/big.bin").writeBytes(ByteArray(24 * 1024 * 1024).also(java.util.Random(1)::nextBytes)) // past any socket buffer
            val d = sync(root, url = "http://127.0.0.1:${server.localPort}")
            d.setUpCode()
            val run = scope.async { d.runAutoBackup() }
            delay(12_000)
            assertFalse("still sending after 12 s, its buffers full", run.isCompleted)
            accepting.join(1_000)
            conn?.close()
            assertEquals(DataSync.AutoOutcome.FAILED, run.await())
        }
    }
}

/** The work WorkManager runs for a plan. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupWorkTest {
    @Test fun `the work waits its delay, for an unmetered network unless any will do`() {
        val wifi = BackupWork.request(BackupPlan(3_600_000, anyNetwork = false)).workSpec
        assertEquals(NetworkType.UNMETERED, wifi.constraints.requiredNetworkType)
        assertEquals(3_600_000, wifi.initialDelay)
        assertEquals(NetworkType.CONNECTED, BackupWork.request(BackupPlan(0, anyNetwork = true)).workSpec.constraints.requiredNetworkType)
    }
}
