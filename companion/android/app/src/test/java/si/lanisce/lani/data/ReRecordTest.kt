package si.lanisce.lani.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.L10n
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** The long press on 🔊: the node records the clip again, the phone drops the old one and plays the new one. */
class ReRecordTest {
    private val dir: File = Files.createTempDirectory("clips").toFile()

    /** The phone's clips: an index and files in [dir]; a download writes the file (or fails, [offline]). */
    private inner class Phone(start: ClipIndex) : ClipCache {
        var current: ClipIndex = start
        override val index: ClipIndex get() = current
        val dropped = mutableListOf<String>()
        val downloaded = mutableListOf<String>()
        var offline = false

        fun file(url: String) = File(dir, url.substringAfterLast('/'))

        override fun put(key: String, voice: String, url: String) {
            current = current + (key to (current[key].orEmpty() + (voice to url)))
        }

        override suspend fun drop(url: String) {
            dropped += url
            file(url).delete()
        }

        override suspend fun fetch(url: String): File {
            val f = file(url)
            if (!f.exists()) {
                if (offline) throw IOException("offline")
                downloaded += url
                f.writeText("new take")
            }
            return f
        }
    }

    /** A node that answers [answer] for each phrase, and remembers what it was asked. */
    private class Node(val answer: (phrase: String, voice: String) -> RedoAnswer) {
        val asked = mutableListOf<Pair<String, String>>()
        suspend fun ask(phrase: String, voice: String): RedoAnswer {
            asked += phrase to voice
            return answer(phrase, voice)
        }
    }

    private fun made(url: String, again: Boolean = false) = { p: String, v: String -> RedoAnswer.Made(Voice.normalize(p), v, url, again) }

    private fun cached(name: String) = File(dir, name).apply { writeText("old take") }

    @Test fun `a re-record drops the old clip, takes the new one into the index, and it plays`() = runBlocking {
        val old = cached("old.mp3")
        val phone = Phone(mapOf("kosilo" to mapOf("female" to "/voice/file/old.mp3", "male" to "/voice/file/m.mp3")))
        val node = Node(made("/voice/file/new.mp3"))
        val r = ReRecorder(phone, node::ask).run("Kosilo", Clips.chain())
        assertEquals(listOf("Kosilo" to "female"), node.asked)
        assertTrue(r is ReRecord.Done)
        assertEquals(listOf(File(dir, "new.mp3")), (r as ReRecord.Done).files)
        assertFalse(r.again)
        assertFalse("the old clip is gone from the phone", old.exists())
        assertEquals(listOf("/voice/file/old.mp3"), phone.dropped)
        assertEquals(listOf("/voice/file/new.mp3"), phone.downloaded)
        assertEquals("/voice/file/new.mp3", phone.index["kosilo"]?.get("female"))
        assertEquals("the other voice's clip stays", "/voice/file/m.mp3", phone.index["kosilo"]?.get("male"))
        assertEquals("✓ Posneto znova · Recorded again", ReRecord.note(r))
    }

    @Test fun `it re-records the voice a tap plays`() {
        val voices = Clips.chain("grandma", Clips.FEMALE)
        // only the narrator's clip on the phone: that is what 🔊 plays, so that is re-recorded
        assertEquals("female" to listOf("Dober dan!"), ReRecorder.plan(mapOf("dober dan" to mapOf("female" to "/f.mp3")), "Dober dan!", voices))
        // her own clip there: hers
        assertEquals("grandma" to listOf("Dober dan!"), ReRecorder.plan(mapOf("dober dan" to mapOf("female" to "/f.mp3", "grandma" to "/g.mp3")), "Dober dan!", voices))
        // no clip yet: the voice wanted
        assertEquals("grandma" to listOf("Dober dan!"), ReRecorder.plan(emptyMap(), "Dober dan!", voices))
        assertNull(ReRecorder.plan(emptyMap(), "Dober dan!", emptyList()))
    }

    @Test fun `a text of two phrases re-records each, unless the node has it whole`() = runBlocking {
        val index = mapOf("kako ste" to mapOf("female" to "/voice/file/s.mp3"), "kako si" to mapOf("female" to "/voice/file/i.mp3"))
        assertEquals("female" to listOf("Kako ste?", "Kako si?"), ReRecorder.plan(index, "Kako ste? / Kako si?", Clips.chain()))
        assertEquals("female" to listOf("Kako ste? / Kako si?"), ReRecorder.plan(index + ("kako ste kako si" to mapOf("female" to "/w.mp3")), "Kako ste? / Kako si?", Clips.chain()))
        cached("s.mp3")
        cached("i.mp3")
        val phone = Phone(index)
        val node = Node { p, v -> RedoAnswer.Made(Voice.normalize(p), v, "/voice/file/new-${Voice.normalize(p).replace(' ', '-')}.mp3") }
        val r = ReRecorder(phone, node::ask).run("Kako ste? / Kako si?", Clips.chain())
        assertEquals(listOf("Kako ste?" to "female", "Kako si?" to "female"), node.asked)
        assertEquals(listOf("new-kako-ste.mp3", "new-kako-si.mp3"), (r as ReRecord.Done).files.map { it.name })
        assertEquals(listOf("/voice/file/s.mp3", "/voice/file/i.mp3"), phone.dropped)
    }

    @Test fun `the day's re-records spent, nothing changes and the note says why`() = runBlocking {
        val old = cached("old.mp3")
        val index = mapOf("kosilo" to mapOf("female" to "/voice/file/old.mp3"))
        val phone = Phone(index)
        val r = ReRecorder(phone, Node { _, _ -> RedoAnswer.Limit("cap") }::ask).run("kosilo", Clips.chain())
        assertEquals(ReRecord.Limit("cap"), r)
        assertTrue(old.exists())
        assertEquals(index, phone.index)
        assertTrue(phone.dropped.isEmpty() && phone.downloaded.isEmpty())
        assertEquals("⏳ Danes ni več posnetkov · No re-recordings left today", ReRecord.note(r))
        assertEquals("⏳ Znakov je malo: ostanejo za pogovore · Few characters left: they're kept for talks", ReRecord.note(ReRecord.Limit("reserve")))
    }

    @Test fun `offline, nothing is queued or changed and the note says so`() = runBlocking {
        val old = cached("old.mp3")
        val index = mapOf("kosilo" to mapOf("female" to "/voice/file/old.mp3"))
        val phone = Phone(index)
        val r = ReRecorder(phone, Node { _, _ -> throw IOException("unreachable") }::ask).run("kosilo", Clips.chain())
        assertEquals(ReRecord.Offline, r)
        assertTrue(old.exists())
        assertEquals(index, phone.index)
        assertEquals("📡 Brez povezave: ni novega posnetka · Offline: no re-recording", ReRecord.note(r))
        assertEquals("⚠️ Zdaj ne gre · Can't re-record now", ReRecord.note(ReRecord.Failed))
        assertEquals("🔄 Nov posnetek · Re-recording…", ReRecord.working())
    }

    @Test fun `the new take made but not downloaded, the index has it for the next tap`() = runBlocking {
        cached("old.mp3")
        val phone = Phone(mapOf("kosilo" to mapOf("female" to "/voice/file/old.mp3"))).apply { offline = true }
        val r = ReRecorder(phone, Node(made("/voice/file/new.mp3"))::ask).run("kosilo", Clips.chain())
        assertEquals(ReRecord.Offline, r)
        assertEquals("/voice/file/new.mp3", phone.index["kosilo"]?.get("female"))
        assertEquals(listOf("/voice/file/old.mp3"), phone.dropped)
    }

    @Test fun `re-recorded today already, that take plays and the note says so`() = runBlocking {
        cached("new.mp3")
        val phone = Phone(mapOf("kosilo" to mapOf("female" to "/voice/file/new.mp3")))
        val r = ReRecorder(phone, Node(made("/voice/file/new.mp3", again = true))::ask).run("kosilo", Clips.chain())
        assertEquals(listOf(File(dir, "new.mp3")), (r as ReRecord.Done).files)
        assertTrue(r.again)
        assertTrue("nothing dropped, nothing downloaded", phone.dropped.isEmpty() && phone.downloaded.isEmpty())
        assertEquals("✓ Danes je že posneto znova · Already re-recorded today", ReRecord.note(r))
    }

    @Test fun `the node's answers`() {
        val done = """{"status":"done","url":"/voice/file/${"a".repeat(40)}.mp3","file":"x","key":"female:kosilo","norm":"kosilo","voice":"female","method":"carrier","today":3,"daily":40}"""
        assertEquals(RedoAnswer.Made("kosilo", "female", "/voice/file/${"a".repeat(40)}.mp3"), RedoAnswer.parse(done, "female", "Kosilo"))
        val again = """{"status":"not_needed","url":"/voice/file/b.mp3","voice":"female","message":"once a day"}"""
        assertEquals("without norm: the phrase's", RedoAnswer.Made("kosilo", "female", "/voice/file/b.mp3", again = true), RedoAnswer.parse(again, "female", "Kosilo!"))
        assertEquals(RedoAnswer.Limit("reserve"), RedoAnswer.parse("""{"status":"limit","reason":"reserve","message":"…"}""", "female", "x"))
        assertEquals(RedoAnswer.Limit("cap"), RedoAnswer.parse("""{"status":"limit"}""", "female", "x"))
        assertEquals(RedoAnswer.Failed, RedoAnswer.parse("""{"status":"failed","message":"ElevenLabs cannot make it now"}""", "female", "x"))
        assertEquals("an older node without the route", RedoAnswer.Failed, RedoAnswer.parse("""{"error":"not found"}""", "female", "x"))
        assertEquals(RedoAnswer.Failed, RedoAnswer.parse("""{"status":"done"}""", "female", "x"))
        assertEquals(RedoAnswer.Failed, RedoAnswer.parse("<html>", "female", "x"))
    }

    @Test fun `the tip about the long press shows once, after a week, unless it was found`() {
        val day = 86_400_000L
        val first = 1_000_000L
        assertFalse(ReRecord.tipDue(first, first + 6 * day, seen = false))
        assertTrue(ReRecord.tipDue(first, first + 7 * day, seen = false))
        assertFalse(ReRecord.tipDue(first, first + 30 * day, seen = true))
        assertFalse(ReRecord.tipDue(0, first + 30 * day, seen = false))
        assertEquals("💡 Dolg pritisk na 🔊: nov posnetek · Long-press 🔊 to re-record", ReRecord.tip())
    }

    @Test fun `the notes are in every table`() {
        val keys = listOf("action", "already", "done", "failed", "limit", "offline", "reserve", "tip", "working").map { "reRecord.$it" }
        for (lang in listOf(Lang.SL, Lang.EN, Lang.IT)) {
            val missing = keys - L10n.table(lang).keys
            assertTrue("${lang.code} misses $missing", missing.isEmpty())
        }
    }
}
