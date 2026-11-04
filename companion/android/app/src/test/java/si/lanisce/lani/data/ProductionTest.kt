package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang
import java.io.File

/**
 * Speaking and free writing in a unit (companion/COURSE.md, "Speaking and writing"; data/Production.kt): the rules as the
 * bridge reads them (bridge/test/fixtures/production-rules.json, which the smoke checks too), a text checked and its verdict,
 * a recognizer's take graded (what people say right, a sound-spelling the standard, an open answer by its checks), a
 * child's version, and the items as a unit's JSON gives them.
 */
class ProductionTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "bridge/test/fixtures").isDirectory }
    private val fixture = Json.parseToJsonElement(File(companion, "bridge/test/fixtures/production-rules.json").readText()).jsonObject

    @Test fun `the fixture's cases match as often as the bridge's port says`() {
        val cases = fixture["cases"]!!.jsonArray
        assertTrue(cases.size >= 15)
        for (c in cases.map { it.jsonObject }) {
            val lang = Lang.of(c["lang"]!!.jsonPrimitive.content)!!
            val rules = c["rules"]!!.jsonArray.map { it.jsonPrimitive.content }
            val text = c["text"]!!.jsonPrimitive.content
            assertEquals("$text ~ $rules", c["n"]!!.jsonPrimitive.int, Production.matches(text, rules, lang).size)
        }
    }

    @Test fun `the fixture's bad rules are refused, so none matches anything`() {
        for (r in fixture["refused"]!!.jsonArray.map { it.jsonPrimitive.content }) {
            assertFalse(r, Production.valid(r, Lang.SL))
            assertEquals(r, emptyList<String>(), Production.matches("bi šel bi l'ho šel", listOf(r), Lang.SL))
        }
    }

    @Test fun `German folds by its table - würde and wuerde are one, wurde another`() {
        assertEquals(1, Production.matches("Ich wuerde gehen.", listOf("würde"), Lang.DE).size)
        assertEquals(0, Production.matches("Ich wurde gefragt.", listOf("würde"), Lang.DE).size)
    }

    private val must = listOf(
        Must("bi + -l", listOf("bi *l", "bi *la", "*l bi", "*la bi"), n = 2),
        Must("če", listOf("če")),
    )
    private val avoid = listOf(Avoid(listOf("bom *l", "bom *la"), "**bom šel** is the future"))

    @Test fun `a text checked - each must, the length, the mistakes avoided - and its verdict`() {
        val ok = Production.check("Če bi imel čas, bi šel v hribe. Spal bi dolgo.", must, avoid, Length(8, 40), Lang.SL)
        assertEquals(listOf(true, true), ok.must.map { it.met })
        assertEquals(0, ok.missing)
        assertEquals(Verdict.CORRECT, Production.verdict(ok))
        // one missing: almost; the č forgotten still meets it (the checks fold)
        val one = Production.check("Ce bi imel cas, grem v hribe in spim dolgo.", must, avoid, Length(8, 40), Lang.SL)
        assertEquals(listOf(false, true), one.must.map { it.met })
        assertEquals(Verdict.ALMOST, Production.verdict(one))
        // the mistake the unit is about: a check not met
        val future = Production.check("Če bom imel čas, bi šel v hribe in bi spal.", must, avoid, Length(8, 40), Lang.SL)
        assertEquals(listOf("bom imel"), future.avoid.single().hits)
        assertEquals(Verdict.ALMOST, Production.verdict(future))
        // too short by half, nothing written: wrong
        assertEquals(Verdict.WRONG, Production.verdict(Production.check("Če bi šel.", must, avoid, Length(12), Lang.SL)))
        assertEquals(Verdict.WRONG, Production.verdict(Production.check("  ", must, avoid, null, Lang.SL)))
    }

    private val speaking = Exercise.Speaking(
        prompt = "What would you do on a free day?", say = "Na prost dan bi šel v hribe.", ask = "Kaj bi naredil na prost dan?",
        accept = listOf("Šel bi v hribe."), spoken = listOf("Na prost dan bi šu v hribe."),
    )

    @Test fun `a take graded - the model, an accepted answer, an ending wrong`() {
        assertEquals(Verdict.CORRECT, Production.grade(speaking, "na prost dan bi šel v hribe", Lang.SL).verdict)
        assertEquals(Verdict.CORRECT, Production.grade(speaking, "Šel bi v hribe", Lang.SL).verdict)
        assertEquals(Verdict.WRONG, Production.grade(speaking, "Na prost dan bi šla v hribe.", Lang.SL).verdict)
    }

    @Test fun `what people say is right, labelled with the standard - the item's spoken, the table's nonstandard`() {
        val r = Production.grade(speaking, "na prost dan bi šu v hribe", Lang.SL)
        assertEquals(Triple(Verdict.CORRECT, Hint.SPOKEN, "Na prost dan bi šel v hribe."), Triple(r.verdict, r.hint, r.expected))
        // English: "She sings good" is said (grading/en.json nonstandard): almost in writing, right aloud
        val en = Exercise.Speaking(prompt = "Say how Granny sings.", say = "She sings well.")
        assertEquals(Verdict.ALMOST, Grading.check("She sings good", listOf("She sings well."), Lang.EN).verdict)
        val e = Production.grade(en, "she sings good", Lang.EN)
        assertEquals(Triple(Verdict.CORRECT, Hint.SPOKEN, "She sings well."), Triple(e.verdict, e.hint, e.expected))
        // a form nobody grown up says stays wrong
        assertEquals(Verdict.WRONG, Production.grade(Exercise.Speaking(prompt = "x", say = "She sang."), "she singed", Lang.EN).verdict)
    }

    @Test fun `a recognizer's sound-spelling is the standard - u vasi for v vasi`() {
        val v = Exercise.Speaking(prompt = "Where are you?", say = "Sem v vasi.")
        assertEquals("Sem v vasi", Production.asWritten("Sem u vasi", Lang.SL))
        assertEquals(Verdict.CORRECT, Production.heard(v, listOf("sem u vasi"), null, Lang.SL)!!.result.verdict)
        // only Slovene's, and only the preposition: "u" inside a word stays
        assertEquals("Sem u vasi", Production.asWritten("Sem u vasi", Lang.IT))
        assertEquals("ura", Production.asWritten("ura", Lang.SL))
    }

    @Test fun `an open answer is right by its checks, the model too, the best of the alternatives`() {
        val open = speaking.copy(must = listOf(Must("bi + -l", listOf("bi *l", "bi *la", "*l bi", "*la bi", "bi … *l", "bi … *la"))), minWords = 3)
        assertEquals(Verdict.CORRECT, Production.grade(open, "Na prost dan bi bral knjigo", Lang.SL).verdict)
        assertEquals(Verdict.WRONG, Production.grade(open, "Na prost dan berem knjigo", Lang.SL).verdict)
        // too short: one check not met
        assertEquals(Verdict.ALMOST, Production.grade(open, "bi bral", Lang.SL).verdict)
        val best = Production.heard(open, listOf("na prost dan berem knjigo", "na prost dan bi bral knjigo"), floatArrayOf(0.9f, 0.6f), Lang.SL)!!
        assertEquals("na prost dan bi bral knjigo", best.text)
    }

    @Test fun `a child's version - its keys over the item's, the rest kept`() {
        val kid = speaking.copy(child = SpeakingChild(prompt = "Say one thing.", say = "Jutri bi se igral.", show = true)).forChild()
        assertEquals(listOf("Say one thing.", "Jutri bi se igral.", "Kaj bi naredil na prost dan?"), listOf(kid.prompt, kid.say, kid.ask))
        assertTrue(kid.show)
        assertNull(kid.child)
        assertEquals(speaking, speaking.forChild())
        val w = Exercise.Writing(prompt = "Write three sentences.", model = "Če bi imel čas, bi šel v hribe.", must = must, avoid = avoid, length = Length(12, 60),
            child = WritingChild(prompt = "Say one thing.", model = "Jutri bi se igral.", must = listOf(Must("bi + -l", listOf("bi se *l"))), length = Length(3, 12)))
        val c = w.forChild()
        assertEquals(listOf("Jutri bi se igral.", "3"), listOf(c.model, c.length!!.min.toString()))
        assertEquals(avoid, c.avoid)
        // said first: a speaking item of its model and checks, at least its length's words
        val s = c.spoken()
        assertEquals(Triple("Jutri bi se igral.", 3, c.must), Triple(s.say, s.minWords, s.must))
        assertEquals(Verdict.CORRECT, Production.grade(s, "jutri bi se igral z žogo", Lang.SL).verdict)
    }

    @Test fun `a unit's JSON gives the items, each text in the learner's base`() {
        val raw = Json.parseToJsonElement(
            """{"type": "writing", "grammar": "pogojnik", "prompt": {"en": "Write to Mojca.", "de": "Schreib Mojca."}, "model": "Če bi imel čas, bi šel.",
                "must": [{"label": {"en": "if: če", "de": "wenn: če"}, "any": ["če"]}], "avoid": [{"any": ["bom *l"], "explain": {"en": "future", "de": "Futur"}}],
                "length": {"min": 5, "max": 40}, "child": {"prompt": {"en": "Say it.", "de": "Sag es."}, "model": "Jutri bi šel."}}""",
        ).jsonObject
        val w = Courses.exercise(raw, "de") as Exercise.Writing
        assertEquals(listOf("Schreib Mojca.", "wenn: če", "Futur", "Sag es."), listOf(w.prompt, w.must.single().label, w.avoid.single().explain, w.child!!.prompt))
        assertEquals(Length(5, 40), w.length)
        val s = Courses.exercise(Json.parseToJsonElement("""{"type": "speaking", "prompt": "Say it.", "say": "Dober dan.", "min_words": 2, "show": true}""").jsonObject, "en") as Exercise.Speaking
        assertEquals(Triple(2, true, listOf("Dober dan.")), Triple(s.minWords, s.show, s.answers()))
    }
}
