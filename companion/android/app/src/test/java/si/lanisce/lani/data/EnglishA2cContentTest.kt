package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang
import java.io.File

/**
 * English A2's units 13-18 (companion/course/en, docs/plans/05-built/course-en-a2-c.md): what grading/en.json gained for them
 * (a contraction is one answer: 's before going to and a participle, I'd love), a contraction typed without its apostrophe as
 * almost, the paired mistakes a slip in the stem would forgive; the spoken forms they name; their outline lines and their three
 * new pages with a hand-written card in every base.
 */
class EnglishA2cContentTest {
    private fun en(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.EN)
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "course/en/course.json").isFile }
    private val json = Json { ignoreUnknownKeys = true }
    private val units = listOf("going-to", "will", "have-to", "would-like", "have-you-ever", "if-it-rains")

    @Test fun `going to and a participle after 's are one answer with the long form`() {
        assertEquals(Verdict.CORRECT, en("Jack's going to wash the van.", "Jack is going to wash the van.").verdict)
        assertEquals(Verdict.CORRECT, en("She's seen a raven.", "She has seen a raven.").verdict)
        assertEquals(Verdict.CORRECT, en("She has seen a raven.", "She's seen a raven.").verdict)
        assertEquals(Verdict.CORRECT, en("Jack's never been to Keswick.", "Jack has never been to Keswick.").verdict)
        assertEquals(Verdict.CORRECT, en("Who's gone?", "Who has gone?").verdict)
        assertEquals(Verdict.CORRECT, en("It's stopped raining.", "It has stopped raining.").verdict)
        assertEquals(Verdict.CORRECT, en("She's lost her key.", "She has lost her key.").verdict)
        assertEquals(Verdict.CORRECT, en("She's just finished.", "She has just finished.").verdict)
        assertEquals(Verdict.CORRECT, en("I'd love to.", "I would love to.").verdict)
        assertEquals(Verdict.CORRECT, en("I'd like to go.", "I would like to go.").verdict)
    }

    @Test fun `be for have and a passive stay what they are`() {
        assertEquals(Verdict.WRONG, en("She is seen a raven.", "She has seen a raven.").verdict)
        assertEquals(Verdict.WRONG, en("He is gone to Keswick.", "He has gone to Keswick.").verdict)
        assertEquals(Verdict.CORRECT, en("It's written on the gate.", "It is written on the gate.").verdict)
        assertEquals(Verdict.CORRECT, en("She's called Poppy.", "She is called Poppy.").verdict)
        assertEquals(Verdict.CORRECT, en("It's closed.", "It is closed.").verdict)
    }

    @Test fun `a contraction typed without its apostrophe is almost, before another word only`() {
        for ((typed, right) in listOf(
            "Hes seen a raven." to "He's seen a raven.", "Shes never been there." to "She's never been there.",
            "Id like a cup of tea." to "I'd like a cup of tea.", "Wed love to." to "We'd love to.",
            "Ill help you." to "I'll help you.", "Well go tomorrow." to "We'll go tomorrow.",
            "I wont forget." to "I won't forget.", "You mustnt go." to "You mustn't go.", "You shouldnt go." to "You shouldn't go.",
        )) {
            val r = en(typed, right)
            assertEquals(typed, Verdict.ALMOST to Hint.APOSTROPHE, r.verdict to r.hint)
        }
        assertEquals(Verdict.WRONG, en("well", "we'll").verdict)
        assertEquals(Verdict.CORRECT, en("Well done!", "Well done!").verdict)
    }

    @Test fun `the paired mistakes are wrong while a slip on another word stays one`() {
        for ((typed, right) in listOf(
            "never" to "ever", "ever" to "never", "gone" to "done", "done" to "gone", "been" to "seen", "seen" to "been",
            "came" to "come", "come" to "came", "gave" to "give", "give" to "gave", "made" to "make", "make" to "made",
            "wrote" to "write", "write" to "wrote", "met" to "meet", "could" to "would", "would" to "could",
            "went" to "want", "want" to "went", "then" to "when", "when" to "then", "swam" to "swum", "sang" to "sung",
            "drank" to "drunk", "began" to "begun",
        )) assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        assertEquals(Verdict.ALMOST, en("aready", "already").verdict)
        assertEquals(Verdict.ALMOST, en("promse", "promise").verdict)
    }

    @Test fun `the spoken forms are right, labelled with the standard form`() {
        val spoken = SheetItem(accept = listOf("are going to walk"), grammar = null, hint = null, explain = null, frame = "you ___", spoken = listOf("are gonna walk"))
        val mark = Sheets.check(spoken, "are gonna walk", Lang.EN)
        assertEquals(Verdict.CORRECT, mark.verdict)
        assertEquals("are going to walk", mark.standard)
    }

    @Test fun `the six units are written in the outline's order at A2, naming their pages`() {
        val a2 = json.parseToJsonElement(File(companion, "course/en/course.json").readText()).jsonObject["levels"]!!.jsonArray
            .map { it.jsonObject }.first { it["level"]!!.jsonPrimitive.content == "A2" }["units"]!!.jsonArray.map { it.jsonObject }
        assertEquals(units, a2.takeLast(6).map { it["id"]!!.jsonPrimitive.content })
        val pages = Grammar.bundled("en").associateBy { it.id }
        val want = mapOf(
            "going-to" to listOf("future", "present-continuous"), "will" to listOf("future"), "have-to" to listOf("must-have-to", "can"),
            "would-like" to listOf("would-like"), "have-you-ever" to listOf("present-perfect", "past-simple"), "if-it-rains" to listOf("first-conditional", "future"),
        )
        for (line in a2.takeLast(6)) {
            val id = line["id"]!!.jsonPrimitive.content
            assertTrue("$id is written", "planned" !in line)
            val named = line["grammar"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals("$id's pages", want[id], named)
            named.forEach { assertNotNull("$id names $it", pages[it]) }
            val unit = json.parseToJsonElement(File(companion, "course/en/units/$id.json").readText()).jsonObject
            assertEquals("$id: the unit's pages as the outline's", named, unit["grammar"]!!.jsonArray.map { it.jsonPrimitive.content })
        }
    }

    @Test fun `each new page has its own card, in every base`() {
        for (id in listOf("must-have-to", "would-like", "first-conditional")) {
            val page = json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject
            assertEquals(id, "A2", page["level"]!!.jsonPrimitive.content)
            val card = page["card"]!!.jsonObject
            for (key in listOf("title", "rule", "tip")) {
                val texts = card[key] as JsonObject
                assertTrue("$id card's $key: ${texts.keys}", texts.keys.containsAll(listOf("en", "sl", "de", "it")))
            }
            assertTrue("$id: two examples on its card", card["examples"]!!.jsonArray.size >= 2)
            val parsed = Grammar.bundled("en").first { it.id == id }.card
            assertNotNull("$id: the app reads its card", parsed)
        }
    }
}
