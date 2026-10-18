package si.lanisce.lani.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/** The lease's rules (as .claude/hooks/lani_lease.py's: tests/test_lease.py) and what the phone never pushes. */
class LeaseAndFilterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val phone = Party(Party.PHONE, "p_0123abcd", "Pixel 8")
    private val tablet = Party(Party.PHONE, "p_ffff0000", "Tablet")
    private val node = Party(Party.NODE, "jan-desktop", "jan-desktop")
    private val t0 = Instant.parse("2026-10-18T21:00:00Z")

    private fun found(l: Lease) = LeaseRead.Found(l)

    @Test fun `no lease is the node's, and a phone takes it only when the repository is empty`() {
        assertEquals(LeaseState.MINE, LeaseRead.None.stateFor(node, t0))
        assertEquals(LeaseState.UNCLAIMED, LeaseRead.None.stateFor(phone, t0))
        assertFalse(LeaseState.UNCLAIMED.takeable)
        assertEquals(LeaseState.FREE, LeaseRead.None.stateFor(phone, t0, hasData = false))
        assertFalse(LeaseRead.None.mayWrite(phone, t0))
    }

    @Test fun `a held lease lets only its holder write, an offer waits, a lease that ran out is takeable`() {
        val held = found(Lease.held(phone, t0))
        assertEquals(LeaseState.MINE, held.stateFor(phone, t0))
        assertEquals(LeaseState.OTHER, held.stateFor(tablet, t0))
        assertEquals(LeaseState.OTHER, held.stateFor(node, t0))
        assertEquals(LeaseState.MINE, found(Lease.held(node.copy(device = "new-machine"), t0)).stateFor(node, t0))
        val offer = found(Lease.offered(phone, node, t0))
        assertEquals(LeaseState.OFFERED, offer.stateFor(phone, t0))
        assertEquals(LeaseState.OTHER, offer.stateFor(tablet, t0))
        assertEquals(LeaseState.OFFERED_AWAY, offer.stateFor(node, t0))
        assertFalse(offer.mayWrite(phone, t0))
        assertEquals(LeaseState.OFFERED, found(Lease.offered(Party(Party.PHONE), node, t0)).stateFor(tablet, t0))
        val later = t0.plus(Lease.TERM).plusSeconds(60)
        assertEquals(LeaseState.EXPIRED, held.stateFor(node, later))
        assertTrue(LeaseState.EXPIRED.takeable)
        assertEquals(LeaseState.MINE, held.stateFor(phone, later))
    }

    @Test fun `renewal once a day, by the holder only`() {
        val l = Lease.held(phone, t0)
        assertNull(l.renewedBy(phone, t0.plusSeconds(23 * 3600)))
        val again = l.renewedBy(phone, t0.plusSeconds(25 * 3600))!!
        assertEquals(l.since, again.since)
        assertEquals(Lease.iso(t0.plusSeconds(25 * 3600).plus(Lease.TERM)), again.expires)
        assertNull(l.renewedBy(node, t0.plusSeconds(3 * 86400)))
    }

    @Test fun `the node's lease file reads, nulls and all, and a broken one lets nobody write`() {
        // as lani_lease.py dump()s it
        val py = """
            {
              "schema": "lani.lease/v1",
              "holder": null,
              "offer": {"side": "phone", "device": "p_0123abcd", "name": "Pixel 8", "mode": "hosted", "by": {"side": "node", "device": "jan-desktop", "name": "jan-desktop", "mode": "self-hosted"}, "at": "2026-10-18T21:00:00Z"},
              "since": "2026-10-18T21:00:00Z",
              "renewed": "2026-10-18T21:00:00Z",
              "expires": "2026-11-01T21:00:00Z"
            }
        """.trimIndent()
        val r = Lease.parse(py)
        assertEquals(LeaseState.OFFERED, r.stateFor(phone, t0))
        assertEquals(LeaseState.OFFERED_AWAY, r.stateFor(node, t0))
        assertEquals(LeaseRead.Unreadable, Lease.parse("{broken"))
        assertEquals(LeaseRead.Unreadable, Lease.parse("""{"schema": "other/v1"}"""))
        assertEquals(LeaseState.OTHER, Lease.parse("{broken").stateFor(node, t0))
        // and the phone's file, with its nulls written out, reads back the same
        val mine = Lease.held(phone, t0)
        assertTrue(mine.toJson().contains("\"offer\": null") || mine.toJson().contains("\"offer\":null"))
        assertEquals(mine, Lease.parse(mine.toJson()).lease)
    }

    // --- the filter -------------------------------------------------------------------------------------

    private val shared get() = DataFilter.sharedText

    @Test fun `the shared gitignore is bundled, the file lani_data_repo py reads`() {
        val repoFile = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, ".claude/hooks/data-repo.gitignore") }.firstOrNull { it.isFile }
        assertTrue("the resource sync/data-repo.gitignore", shared.contains("/app/bridge-key.pem"))
        if (repoFile != null) assertEquals(repoFile.readText(), shared)
    }

    @Test fun `the private files, caches and runtime files never leave the phone`() {
        val f = DataFilter(DataFilter.parse(shared))
        for (p in listOf("app/bridge-token", "app/family-token", "app/bridge-key.pem", "app/town-key.pem", "x/key.pem", "secret.key",
            "keys.env", "app/pairing/abc.json", "app/town-invites/def.json", "app/voice/voice.db", "app/voice/files/a.mp3",
            "app/release/latest.json", "lani.apk", "app/client-ids.json", "app/devices-seen.json", "app/events.json",
            "app/channel-queue.json", "app/answers.json", "app/answers.json.tmp-1-abc", "app/dev/shadow.json",
            "app/chat-images/2026-10/a.jpg", ".backups/x.json", "progress-db.json.backup-20261018", "a.tmp", "db-wal",
            ".DS_Store", "x/__pycache__/y.pyc", ".git/config", "app/game.json" + DataFilter.TEMP_SUFFIX)) {
            assertTrue(p, f.ignored(p))
        }
        for (p in listOf("learner-profile.json", "progress-db.json", "app/game.json", "app/devices.json", "app/lease.json",
            "app/notebook.json", "app/stories/x.json", "results/session-001.md", ".gitignore", "languages/it/progress-db.json")) {
            assertTrue(p, f.pushes(p))
        }
    }

    @Test fun `the repository's own gitignore adds lines, and can't bring a secret back`() {
        val dir = tmp.newFolder("data")
        File(dir, ".gitignore").writeText("/app/wishlist.md\n!/app/town-key.pem\n!*.pem\n")
        val f = DataFilter.of(dir, shared)
        assertTrue(f.ignored("app/wishlist.md"))
        assertTrue(f.ignored("app/town-key.pem"))
        assertTrue(f.pushes("app/game.json"))
    }

    @Test fun `git's rules, anchors, directories, globs and negation`() {
        val f = DataFilter(DataFilter.parse("/top.txt\nlogs/\n*.log\n!keep.log\ndocs/**/draft.md\n[ab].bin\n\\#hash"))
        assertTrue(f.ignored("top.txt"))
        assertFalse(f.ignored("sub/top.txt"))
        assertTrue(f.ignored("logs/x.json"))
        assertTrue(f.ignored("a/logs/x.json"))
        assertFalse(f.ignored("logs")) // a file named like the directory
        assertTrue(f.ignored("x/y.log"))
        assertFalse(f.ignored("keep.log"))
        assertTrue(f.ignored("docs/draft.md"))
        assertTrue(f.ignored("docs/a/b/draft.md"))
        assertTrue(f.ignored("a.bin"))
        assertFalse(f.ignored("c.bin"))
        assertTrue(f.ignored("#hash"))
        // a file in an ignored directory stays ignored, whatever comes after
        val g = DataFilter(DataFilter.parse("/app/dev/\n!/app/dev/keep.json"))
        assertTrue(g.ignored("app/dev/keep.json"))
    }
}
