package si.lanisce.lani.sync

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
import java.time.Instant

/**
 * The data's sync against a fake GitHub (FakeGitHub: MockWebServer over an in-memory git): the first push into an empty
 * repository, a pull onto an empty phone, changes both ways, a divergence kept on both sides, the private files, the
 * lease's commits, the rate limit and the ETag. docs/plans/05-built/git-sync.md, "The sync".
 */
class SyncEngineTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var fake: FakeGitHub
    private lateinit var data: File
    private val me = Party(Party.PHONE, "p_test01", "Pixel 8")
    private val node = Party(Party.NODE, "jan-desktop", "jan-desktop")
    private var now = Instant.parse("2026-10-18T21:00:00Z")

    @Before fun setUp() {
        fake = FakeGitHub()
        data = tmp.newFolder("lani", "data")
    }

    @After fun tearDown() = fake.close()

    private fun engine(dir: File = data, state: File = File(dir.parentFile, "sync/state.json"), party: Party = me, pulled: (List<String>) -> Unit = {}) =
        SyncEngine(dir, state, GitHub({ fake.token }, fake.url, clock = { now }), fake.ref, party, clock = { now }, onPulled = pulled)

    private fun write(rel: String, text: String, dir: File = data) = File(dir, rel).apply { parentFile.mkdirs(); writeText(text) }
    private fun read(rel: String, dir: File = data) = File(dir, rel).takeIf { it.isFile }?.readText()
    private fun leaseOf(text: String?) = Lease.parse(text)

    /** What a phone in the hosted mode has: the six databases, the bridge's files, and its private ones. */
    private fun hostedPhone() {
        write("learner-profile.json", """{"learner": {"name": "Ana"}}""")
        write("progress-db.json", """{"overall_stats": {"total_sessions": 3}}""")
        write("app/game.json", """{"village": "Moja vas"}""")
        write("app/notebook.json", """{"notes": ["likes cats"]}""")
        // private: never pushed
        write("app/town-key.pem", "-----BEGIN PRIVATE KEY-----\nsecret\n-----END PRIVATE KEY-----\n")
        write("app/family-token", "family-secret")
        write("app/bridge-token", "bridge-secret")
        write("app/answers.json", """{"answers": ["kurim"]}""")
        write("app/dev/shadow-2026-10-18.json", "{}")
        write("app/chat-images/2026-10/0123456789abcdef0123456789abcdef.jpg", "jpeg")
        write("app/voice/files/a.mp3", "mp3")
        write("app/pairing/x.json", "{}")
        write("progress-db.json.tmp", "half")
        write("learner-profile.json.backup-20261018-101010", "{}")
    }

    @Test fun `the first push fills an empty repository, without the private files, and the phone holds the lease`() = runBlocking {
        hostedPhone()
        val r = engine().sync()
        assertTrue(r.toString(), r is SyncOutcome.Done)
        val files = fake.files()
        assertEquals(setOf("learner-profile.json", "progress-db.json", "app/game.json", "app/notebook.json", Lease.FILE), files.keys)
        assertEquals("""{"village": "Moja vas"}""", files["app/game.json"])
        // the lease: the phone's, in the repository and on the phone
        assertEquals(LeaseState.MINE, leaseOf(files[Lease.FILE]).stateFor(me, now))
        assertEquals(LeaseState.OTHER, leaseOf(files[Lease.FILE]).stateFor(node, now))
        // an empty repository starts through the contents API (the Git Data API refuses one), then one commit on top
        assertTrue(fake.requests.toString(), fake.requests.any { it.startsWith("PUT /repos/ana/lani-data/contents/") })
        assertEquals(2, fake.commitCount())
        // text goes inline in the tree: no blob requests
        assertFalse(fake.requests.any { it == "POST /repos/ana/lani-data/git/blobs" })
        // and again: nothing to do, and the branch asked with its ETag
        val again = engine().sync()
        assertEquals(SyncOutcome.Done(), again)
    }

    @Test fun `an empty phone pulls the repository, and an unchanged branch costs a 304`() = runBlocking {
        fake.nodePushes(mapOf("learner-profile.json" to """{"learner": {"name": "Jan"}}""", "app/game.json" to "{}", "results/s1.md" to "# one"))
        var told = emptyList<String>()
        val r = engine(pulled = { told = it }).sync()
        assertEquals(SyncOutcome.Done(pulled = listOf("app/game.json", "learner-profile.json", "results/s1.md")), r)
        assertEquals("# one", read("results/s1.md"))
        assertEquals(listOf("app/game.json", "learner-profile.json", "results/s1.md"), told)
        // no lease in a node's repository: the phone may not write it (unclaimed)
        assertEquals(LeaseState.UNCLAIMED, engine().lease().stateFor(me, now))
        fake.requests.clear()
        assertEquals(SyncOutcome.Done(), engine().sync())
        assertEquals(listOf("GET /repos/ana/lani-data/git/ref/heads/main"), fake.requests)
        // the node pushes again: pulled
        fake.nodePushes(mapOf("app/game.json" to """{"day": 2}""", "results/s1.md" to null))
        val r2 = engine().sync()
        assertEquals(SyncOutcome.Done(pulled = listOf("app/game.json", "results/s1.md")), r2)
        assertEquals("""{"day": 2}""", read("app/game.json"))
        assertNull(read("results/s1.md"))
    }

    @Test fun `the phone's changes are pushed only while it holds the lease`() = runBlocking {
        fake.nodePushes(mapOf("progress-db.json" to "{}", "app/game.json" to "{}"))
        engine().sync()
        write("progress-db.json", """{"phone": true}""")
        val r = engine().sync()
        assertTrue(r.toString(), r is SyncOutcome.NotHolder && r.files == listOf("progress-db.json") && r.state == LeaseState.UNCLAIMED)
        assertEquals("{}", fake.files()["progress-db.json"]) // not pushed
        assertEquals("""{"phone": true}""", read("progress-db.json")) // and kept on the phone
    }

    @Test fun `take, write, hand over, the lease's commits and the phone's pushes`() = runBlocking {
        fake.nodePushes(mapOf("progress-db.json" to "{}", Lease.FILE to Lease.offered(Party(Party.PHONE), node, now).toJson()))
        assertEquals(LeaseState.OFFERED, run { engine().sync(); engine().lease().stateFor(me, now) })
        val took = engine().take()
        assertTrue(took.toString(), took is SyncOutcome.Done && (took as SyncOutcome.Done).pushed == listOf(Lease.FILE))
        assertEquals(LeaseState.MINE, leaseOf(fake.files()[Lease.FILE]).stateFor(me, now))
        assertTrue(fake.message().startsWith("lease: the phone (Pixel 8) takes the data"))
        // a change is pushed now, as one commit
        write("progress-db.json", """{"sessions": 1}""")
        val pushed = engine().sync()
        assertEquals(listOf("progress-db.json"), (pushed as SyncOutcome.Done).pushed)
        assertEquals("""{"sessions": 1}""", fake.files()["progress-db.json"])
        assertTrue(fake.message(), fake.message().startsWith("phone: the phone (Pixel 8): 1 file\n\nprogress-db.json"))
        // handed over to the node: the change first, then the offer
        write("app/notebook.json", """{"notes": []}""")
        val handed = engine().handOver(Party(Party.NODE))
        assertTrue(handed.toString(), handed is SyncOutcome.Done)
        val lease = leaseOf(fake.files()[Lease.FILE])
        assertEquals(LeaseState.OFFERED, lease.stateFor(node, now))
        assertEquals(LeaseState.OFFERED_AWAY, lease.stateFor(me, now))
        assertEquals("""{"notes": []}""", fake.files()["app/notebook.json"])
        // the phone writes no more
        write("progress-db.json", """{"sessions": 2}""")
        assertTrue(engine().sync() is SyncOutcome.NotHolder)
    }

    @Test fun `a lease another side holds isn't taken without the learner's confirmation`() = runBlocking {
        fake.nodePushes(mapOf("progress-db.json" to "{}", Lease.FILE to Lease.held(node, now).toJson()))
        val refused = engine().take()
        assertTrue(refused.toString(), refused is SyncOutcome.LeaseRefused && refused.state == LeaseState.OTHER)
        assertEquals(LeaseState.MINE, leaseOf(fake.files()[Lease.FILE]).stateFor(node, now))
        val forced = engine().take(force = true)
        assertTrue(forced.toString(), forced is SyncOutcome.Done)
        assertTrue(fake.message().endsWith("(without a handover)"))
        assertEquals(LeaseState.MINE, leaseOf(fake.files()[Lease.FILE]).stateFor(me, now))
        // a node's lease that ran out may be taken without one
        fake.nodePushes(mapOf(Lease.FILE to Lease.held(node, now.minus(Lease.TERM).minusSeconds(60)).toJson()))
        assertTrue(engine().take() is SyncOutcome.Done)
    }

    @Test fun `changes on both sides to different files, the repository's pulled, the phone's pushed on top`() = runBlocking {
        fake.nodePushes(mapOf("progress-db.json" to "{}", "app/game.json" to "{}", Lease.FILE to Lease.offered(Party(Party.PHONE), node, now).toJson()))
        engine().take()
        // the node wrote a story overnight (another file), the phone a session
        fake.nodePushes(mapOf("app/stories/night.json" to """{"id": "night"}"""), "node: a story overnight")
        write("progress-db.json", """{"sessions": 1}""")
        val r = engine().sync()
        assertEquals(SyncOutcome.Done(pulled = listOf("app/stories/night.json"), pushed = listOf("progress-db.json"), commit = (r as SyncOutcome.Done).commit), r)
        assertEquals("""{"id": "night"}""", read("app/stories/night.json"))
        assertEquals(mapOf("progress-db.json" to """{"sessions": 1}""", "app/stories/night.json" to """{"id": "night"}"""), fake.files().filterKeys { it in setOf("progress-db.json", "app/stories/night.json") })
    }

    @Test fun `the same file changed on both sides, nothing is overwritten, the phone's version kept on a branch`() = runBlocking {
        fake.nodePushes(mapOf("progress-db.json" to "{}", Lease.FILE to Lease.offered(Party(Party.PHONE), node, now).toJson()))
        engine().take()
        val main = fake.branches.getValue("main")
        fake.nodePushes(mapOf("progress-db.json" to """{"node": 1}"""))
        write("progress-db.json", """{"phone": 1}""")
        val r = engine().sync()
        assertTrue(r.toString(), r is SyncOutcome.Diverged)
        r as SyncOutcome.Diverged
        assertEquals(listOf("progress-db.json"), r.files)
        assertEquals("""{"phone": 1}""", read("progress-db.json")) // the phone's stays
        assertEquals("""{"node": 1}""", fake.files()["progress-db.json"]) // the repository's too
        assertTrue(fake.ancestor(main, fake.branches.getValue("main")))
        val branch = r.branch!!
        assertTrue(branch, branch.startsWith("lani/phone-p_test01-20261018-"))
        assertEquals("""{"phone": 1}""", fake.files(branch)["progress-db.json"])
        // it waits for the learner: a sync does nothing meanwhile
        fake.requests.clear()
        assertTrue(engine().sync() is SyncOutcome.Diverged)
        assertTrue(fake.requests.isEmpty())
        // "use the repository's": the phone gets it (its own is on the branch)
        assertTrue(engine().useRepository() is SyncOutcome.Done)
        assertEquals("""{"node": 1}""", read("progress-db.json"))
        assertTrue(engine().sync() is SyncOutcome.Done)
    }

    @Test fun `keeping the phone's version puts it on top of the repository's, fast-forward`() = runBlocking {
        fake.nodePushes(mapOf("progress-db.json" to "{}", Lease.FILE to Lease.offered(Party(Party.PHONE), node, now).toJson()))
        engine().take()
        fake.nodePushes(mapOf("progress-db.json" to """{"node": 1}"""))
        val theirs = fake.branches.getValue("main")
        write("progress-db.json", """{"phone": 1}""")
        assertTrue(engine().sync() is SyncOutcome.Diverged)
        val r = engine().keepPhone()
        assertTrue(r.toString(), r is SyncOutcome.Done)
        assertEquals("""{"phone": 1}""", fake.files()["progress-db.json"])
        assertTrue("the repository's version stays in the history", fake.ancestor(theirs, fake.branches.getValue("main")))
    }

    @Test fun `a phone with data meets a repository with other data, both kept, and keeping the phone's asks for the lease`() = runBlocking {
        fake.nodePushes(mapOf("progress-db.json" to """{"node": true}""", Lease.FILE to Lease.held(node, now).toJson()))
        hostedPhone()
        val r = engine().sync()
        assertTrue(r.toString(), r is SyncOutcome.Diverged)
        assertEquals("""{"overall_stats": {"total_sessions": 3}}""", read("progress-db.json"))
        val refused = engine().keepPhone()
        assertTrue(refused.toString(), refused is SyncOutcome.LeaseRefused && refused.state == LeaseState.OTHER)
        assertTrue(engine().keepPhone(takeLease = true) is SyncOutcome.Done)
        assertEquals("""{"overall_stats": {"total_sessions": 3}}""", fake.files()["progress-db.json"])
        assertEquals(LeaseState.MINE, leaseOf(fake.files()[Lease.FILE]).stateFor(me, now))
    }

    @Test fun `a rewritten repository history is a divergence, not a pull`() = runBlocking {
        fake.nodePushes(mapOf("progress-db.json" to "{}"))
        engine().sync()
        fake.rewrite(mapOf("progress-db.json" to """{"rewritten": true}"""))
        val r = engine().sync()
        assertTrue(r.toString(), r is SyncOutcome.Diverged)
        assertEquals("{}", read("progress-db.json"))
    }

    @Test fun `the private files never leave the phone, and a repository's file the phone keeps out is never removed`() = runBlocking {
        // a node's repository that holds a file the phone's filter keeps out (committed before a .gitignore line)
        fake.nodePushes(mapOf("progress-db.json" to "{}", "app/devices.json" to "{}", "app/answers.json" to "[old]", Lease.FILE to Lease.offered(Party(Party.PHONE), node, now).toJson()))
        engine().sync()
        assertNull("not pulled", read("app/answers.json"))
        hostedPhone()
        engine().take()
        write("progress-db.json", """{"x": 1}""")
        engine().sync()
        val files = fake.files()
        assertEquals("[old]", files["app/answers.json"]) // left as it was, neither replaced nor removed
        for (p in listOf("app/town-key.pem", "app/family-token", "app/bridge-token", "app/dev/shadow-2026-10-18.json",
            "app/chat-images/2026-10/0123456789abcdef0123456789abcdef.jpg", "app/voice/files/a.mp3", "app/pairing/x.json",
            "progress-db.json.tmp", "learner-profile.json.backup-20261018-101010")) {
            assertFalse(p, p in files)
        }
    }

    @Test fun `a path from the repository that would leave the data directory is never written`() = runBlocking {
        fake.nodePushes(mapOf("../outside.json" to "{}", "app/../../escape.json" to "{}", "ok.json" to "{}"))
        engine().sync()
        assertFalse(File(data.parentFile, "outside.json").exists())
        assertFalse(File(data.parentFile.parentFile, "escape.json").exists())
        assertEquals("{}", read("ok.json"))
    }

    @Test fun `the rate limit stops the sync until GitHub's reset, without asking again`() = runBlocking {
        fake.nodePushes(mapOf("progress-db.json" to "{}"))
        fake.remaining = 0
        fake.resetAt = now.plusSeconds(1800).epochSecond
        val e = engine()
        val r = e.sync()
        assertTrue(r.toString(), r is SyncOutcome.Failed && r.retryAt == now.plusSeconds(1800))
        fake.requests.clear()
        assertTrue(e.sync() is SyncOutcome.Failed)
        assertTrue("no request while the limit is used up", fake.requests.isEmpty())
        assertNull(read("progress-db.json"))
    }

    @Test fun `a file over GitHub's limit stays on the phone and is reported`() = runBlocking {
        fake.nodePushes(mapOf(Lease.FILE to Lease.offered(Party(Party.PHONE), node, now).toJson()))
        engine().take()
        File(data, "big.bin").apply { java.io.RandomAccessFile(this, "rw").use { it.setLength(GitHub.MAX_FILE + 1) } }
        write("small.json", "{}")
        val r = engine().sync() as SyncOutcome.Done
        assertEquals(listOf("big.bin"), r.skipped)
        assertEquals(listOf("small.json"), r.pushed)
    }

    @Test fun `a rewrite of the same size in the same tick of the clock is still seen`() = runBlocking {
        fake.nodePushes(mapOf(Lease.FILE to Lease.offered(Party(Party.PHONE), node, now).toJson()))
        engine().take()
        val f = write("progress-db.json", """{"n": 1}""")
        val tick = f.lastModified()
        assertEquals(listOf("progress-db.json"), (engine().sync() as SyncOutcome.Done).pushed)
        f.writeText("""{"n": 2}""")
        f.setLastModified(tick) // the same size and modification time as when it was hashed
        assertEquals(listOf("progress-db.json"), (engine().sync() as SyncOutcome.Done).pushed)
        assertEquals("""{"n": 2}""", fake.files()["progress-db.json"])
    }

    @Test fun `a binary file goes up as a blob of its own`() = runBlocking {
        fake.nodePushes(mapOf(Lease.FILE to Lease.offered(Party(Party.PHONE), node, now).toJson()))
        engine().take()
        val bytes = byteArrayOf(0, -1, -2, 0x50, 0x4b)
        File(data, "app/audio/rec.bin").apply { parentFile.mkdirs(); writeBytes(bytes) }
        engine().sync()
        assertTrue(fake.requests.contains("POST /repos/ana/lani-data/git/blobs"))
        assertTrue(fake.blobs.values.any { it.contentEquals(bytes) })
    }
}
