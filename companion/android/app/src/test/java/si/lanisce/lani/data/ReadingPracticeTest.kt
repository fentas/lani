package si.lanisce.lani.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.Lang

/**
 * Reading practice reported to the node (data/ReadingPractice.kt; the bridge's POST /readings/done persists it as the
 * learner's reading, and a reading aloud's speaking too): a reading's questions, a story heard to its end, a reading aloud.
 */
class ReadingPracticeTest {
    private val frtalja = Cultures.load("primorska").readings.getValue("frtalja")

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content
    private fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.content?.toInt()

    @Test fun `a reading's questions, how many, how many right the first time, the minutes on screen`() {
        val b = ReadingPractice.reading(frtalja, "Frtalja z zelišči", listOf(Verdict.CORRECT, Verdict.WRONG), minutes = 4)
        assertEquals("reading", b.str("kind"))
        assertEquals("frtalja" to "A2", b.str("id") to b.str("level"))
        assertEquals(Triple(2, 1, 4), Triple(b.int("exercises"), b.int("correct"), b.int("duration_minutes")))
        assertEquals(listOf("true", "false"), b["questions"]!!.jsonArray.map { it.jsonObject.str("right") })
    }

    @Test fun `how often the text was looked at again goes with a reading and a book, when it was`() {
        assertNull(ReadingPractice.reading(frtalja, "Frtalja", listOf(Verdict.CORRECT), minutes = 1)["looks"])
        assertEquals(3, ReadingPractice.reading(frtalja, "Frtalja", listOf(Verdict.CORRECT), minutes = 1, looks = 3).int("looks"))
        assertEquals(2, ReadingPractice.book("zlatorog", "Zlatorog", "A1", listOf(Verdict.WRONG), minutes = 2, looks = 2).int("looks"))
        assertNull(ReadingPractice.book("zlatorog", "Zlatorog", "A1", listOf(Verdict.WRONG), minutes = 2)["looks"])
    }

    @Test fun `a story heard to its end, its turns, those right the first time`() {
        val b = ReadingPractice.story("zlatorog", "Zlatorog", "A1", turns = 3, missed = 1, minutes = 6)
        assertEquals(Triple("story", 3, 2), Triple(b.str("kind"), b.int("exercises"), b.int("correct")))
        // a telling without a turn counts once, heard
        val none = ReadingPractice.story("x", "X", "A1", turns = 0, missed = 0, minutes = 1)
        assertEquals(1 to 1, none.int("exercises") to none.int("correct"))
        assertNull(ReadingPractice.story("x", "X", "C9", 1, 0, 1).str("level")) // not a level
    }

    @Test fun `a book of the storyteller's read to its questions' end is reading practice, as a reading`() {
        val b = ReadingPractice.book("martin-krpan", "Martin Krpan", "A2", listOf(Verdict.CORRECT, Verdict.CORRECT, Verdict.WRONG), minutes = 7)
        assertEquals(Triple("reading", "book:martin-krpan", "A2"), Triple(b.str("kind"), b.str("id"), b.str("level")))
        assertEquals(Triple(3, 2, 7), Triple(b.int("exercises"), b.int("correct"), b.int("duration_minutes")))
        assertEquals(listOf("true", "true", "false"), b["questions"]!!.jsonArray.map { it.jsonObject.str("right") })
        // what the node takes as it is (`/readings/done`), and the tutor's line
        assertEquals("POST" to "/readings/done", Writes.readingDone(b, id = "b1").let { it.method to it.path })
        assertEquals("Read \"Martin Krpan\" in the app: 2/3 questions right", ReadingPractice.summary(b))
    }

    @Test fun `a reading aloud, its sentences read well, the words and the pace`() {
        val t = ReadAloud.gradePhone("Ena dva tri štiri pet. Šest sedem osem devet deset.", listOf("ena dva tri štiri šest sedem osem"), seconds = 6.0, lang = Lang.SL)!!
        val b = ReadingPractice.aloud(frtalja, "Frtalja", listOf(t), minutes = 2)
        assertEquals(Triple("aloud", 2, 1), Triple(b.str("kind"), b.int("exercises"), b.int("correct")))
        val a = b["aloud"]!!.jsonObject
        assertEquals(listOf(10, 7, 0, 3), listOf("words", "right", "misread", "skipped").map { a.int(it) })
        assertEquals(70, a.int("wpm")) // 7 words in 6 s
        assertEquals("phone", a.str("recognizer"))
        assertEquals(listOf("pet", "devet", "deset"), (a["misses"] as JsonArray).map { it.jsonObject.str("word") })
    }

    @Test fun `the body posts to readings-done through the outbox`() {
        val e = Writes.readingDone(ReadingPractice.story("zlatorog", "Zlatorog", "A1", 2, 0, 3), id = "r1")
        assertEquals("POST" to "/readings/done", e.method to e.path)
        val body = json.parseToJsonElement(e.body).jsonObject
        assertEquals("r1", body.str("client_id"))
        assertEquals("story", body.str("kind"))
    }

    @Test fun `a node from before readings gets it as a session_end, with the report to persist`() {
        val body = ReadingPractice.aloud(frtalja, "Frtalja", listOf(ReadAloud.gradePhone("Dober dan.", listOf("dober dan"), lang = Lang.SL)!!), minutes = 1)
        val (summary, data) = ReadingPractice.sessionEnd(body, ReadingPractice.summary(body))
        assertTrue(summary, summary.startsWith("Read \"Frtalja\" aloud in the app: 1/1 sentences read well"))
        val report = data["report"]!!.jsonObject
        assertEquals("/lani-app-read-aloud", report.str("command_used"))
        assertEquals(listOf("reading", "speaking"), report["skills_practiced"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(setOf("reading", "speaking"), report["skill_scores"]!!.jsonObject.keys)
        assertEquals(body, data["reading_practice"])
    }

    @Test fun `served readings are read one by one, one the app can't read is left out`() {
        val raw = """[
            {"id": "a", "kind": "page", "level": "A1", "title": {"sl": "A", "en": "A"}, "lines": [{"text": {"sl": "Dober dan.", "en": "Hello."}}],
             "questions": [{"type": "true_false", "ask": {"sl": "Je dan?", "en": "Is it day?"}, "answer": true, "explain": {"sl": "Da.", "en": "Yes."}}],
             "source": "tutor", "machine_written": true, "published_at": 1.7E12, "later_field": 1},
            {"id": "b", "kind": "page"}
        ]"""
        val got = ReadingPractice.parseServed(raw)
        assertEquals(listOf("a"), got.map { it.id })
        assertEquals("tutor" to true, got[0].source to got[0].machineWritten)
        assertTrue(ReadingPractice.parseServed("not json").isEmpty())
        // … and checked as the bundled ones are
        assertTrue(Cultures.check("primorska", got[0]).isEmpty())
        val broken = got[0].copy(questions = listOf(got[0].questions[0].copy(answer = null)))
        assertFalse(Cultures.check("primorska", broken).isEmpty())
    }
}
