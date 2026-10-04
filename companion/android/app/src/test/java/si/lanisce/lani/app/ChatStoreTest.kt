package si.lanisce.lani.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/** Where the chat is kept: a file a month, the history from before the archive, bookmarks, and a year kept. */
class ChatStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dir: File get() = File(tmp.root, "chat")
    private val legacy: File get() = File(tmp.root, "chat.json")
    private fun store() = ChatStore(dir, legacy)
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun msg(id: String, at: String?, fromMe: Boolean = false) = ChatMessage(fromMe = fromMe, text = "sporočilo $id", id = id, at = at?.let(::t))
    private val now = t("2026-09-29T08:00:00Z")

    /** Writes every file [all] is kept in, as the controller does for what changed. */
    private fun ChatStore.writeAll(all: List<ChatMessage>) = all.map(ChatStore::partOf).toSet().forEach { write(it, all) }

    @Test fun `a message is kept in its month's file, by UTC`() {
        // 1 October 00:30 in Ljubljana is still 30 September in UTC
        assertEquals("2026-09", ChatStore.partOf(msg("a", "2026-09-30T22:30:00Z")))
        assertEquals("2026-10", ChatStore.partOf(msg("b", "2026-10-01T00:00:00Z")))
        assertEquals(ChatStore.LEGACY, ChatStore.partOf(msg("c", null)))
        val all = listOf(msg("a", "2026-08-31T12:00:00Z"), msg("b", "2026-09-01T12:00:00Z"), msg("c", "2026-09-28T12:00:00Z"))
        store().writeAll(all)
        assertEquals(listOf("2026-08.json", "2026-09.json"), dir.list()!!.sorted())
        assertEquals(listOf("b", "c"), ChatHistory.decode(File(dir, "2026-09.json").readText()).map { it.id })
        assertFalse(legacy.exists()) // nothing undated: the old file isn't written
    }

    @Test fun `the history from before the archive comes first, then the months in order`() {
        legacy.writeText("""[{"fromMe":true,"text":"Živjo","id":"old1"},{"fromMe":false,"text":"Zdravo!","id":"old2"}]""")
        store().writeAll(listOf(msg("sept", "2026-09-02T10:00:00Z"), msg("aug", "2026-08-30T10:00:00Z")))
        val loaded = store().load(now)
        assertEquals(listOf("old1", "old2", "aug", "sept"), loaded.map { it.id })
        assertNull(loaded[0].at)
    }

    @Test fun `a write touches only its own month`() {
        val all = listOf(msg("aug", "2026-08-30T10:00:00Z"), msg("sept", "2026-09-02T10:00:00Z"))
        store().writeAll(all)
        val august = File(dir, "2026-08.json").readText()
        val changed = all.map { if (it.id == "sept") ChatArchive.bookmarked(it, on = true, now = now) else it } + msg("new", "2026-09-29T07:00:00Z")
        store().write("2026-09", changed)
        assertEquals(august, File(dir, "2026-08.json").readText())
        assertEquals(listOf("sept", "new"), ChatHistory.decode(File(dir, "2026-09.json").readText()).map { it.id })
    }

    @Test fun `a bookmark and its note are kept, and taking it away is too`() {
        val m = msg("q", "2026-09-28T17:00:00Z", fromMe = true)
        val marked = ChatArchive.noted(ChatArchive.bookmarked(m, on = true, now = now), "  ker pozabljam dajalnik ", now + 1)
        store().write("2026-09", listOf(marked))
        val back = store().load(now).single()
        assertEquals(now, back.bookmark?.at)
        assertEquals("ker pozabljam dajalnik", back.bookmark?.note)

        store().write("2026-09", listOf(ChatArchive.bookmarked(back, on = false, now = now)))
        assertNull(store().load(now).single().bookmark)
    }

    @Test fun `a bookmark on a message from before the archive rewrites the old file, with only those`() {
        legacy.writeText("""[{"fromMe":false,"text":"Rodilnik …","id":"old"}]""")
        val all = store().load(now).map { ChatArchive.bookmarked(it, on = true, now = now) } + msg("new", "2026-09-29T07:00:00Z")
        store().write(ChatStore.partOf(all[0]), all)
        val old = ChatHistory.decode(legacy.readText())
        assertEquals(listOf("old"), old.map { it.id })
        assertEquals(now, old[0].bookmark?.at)
    }

    @Test fun `a year is kept, older months go, but not their bookmarked messages`() {
        val oldMarked = ChatArchive.bookmarked(msg("kept", "2025-09-10T10:00:00Z"), on = true, now = t("2025-09-10T11:00:00Z"))
        store().writeAll(
            listOf(
                msg("gone1", "2025-08-01T10:00:00Z"),
                msg("gone2", "2025-09-01T10:00:00Z"), oldMarked,
                msg("year", "2025-10-01T10:00:00Z"), // the twelfth month back from September 2026: kept
                msg("now", "2026-09-28T10:00:00Z"),
            ),
        )
        assertEquals(listOf("kept", "year", "now"), store().load(now).map { it.id })
        assertFalse(File(dir, "2025-08.json").exists())
        assertEquals(listOf("kept"), ChatHistory.decode(File(dir, "2025-09.json").readText()).map { it.id })
        assertTrue(File(dir, "2025-10.json").exists())
    }

    @Test fun `no files yet, or damaged ones, read as an empty chat`() {
        assertTrue(store().load(now).isEmpty())
        dir.mkdirs()
        File(dir, "2026-09.json").writeText("[{\"fromMe\":tr")
        File(dir, "notes.txt").writeText("not a month")
        assertTrue(store().load(now).isEmpty())
    }
}
