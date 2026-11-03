package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * German B1's middle six units (relativsatz to obwohl-trotzdem: docs/plans/05-built/course-de-b1-b.md) as the app grades
 * them: denn or deren for denen, a comparative or superlative with its ending but without its umlaut, a separable verb's
 * zu-infinitive without its zu, and Stadt for statt are known mistakes, each only for a word of its group; a relative's
 * welcher is right beside der; obwohl with the verb second, trotzdem as a conjunction and der Tag, wo are spoken: right,
 * labelled with the standard form.
 */
class GermanB1bContentTest {
    private val units = listOf("relativsatz", "relativsatz-praeposition", "adjektiv-alle-faelle", "schnellere", "um-zu", "obwohl-trotzdem")
    private fun de(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.DE)
    private fun assertGraded(verdict: Verdict, hint: Hint, r: Grading.Result) {
        assertEquals("verdict of «${r.expected}»", verdict, r.verdict)
        assertEquals("hint of «${r.expected}»", hint, r.hint)
    }

    /** A sheet's item with [accept] and [spoken]: what the sheet makes of [answer]. */
    private fun sheet(answer: String, accept: List<String>, spoken: List<String> = emptyList()) =
        Sheets.check(SheetItem(accept, null, null, null, "", spoken = spoken), answer, Lang.DE)

    @Test fun `the units are in the outline at B1, in its order, at the level's size`() {
        val course = checkNotNull(Courses.bundled("de"))
        for (id in units) {
            val u = course.units.getValue(id)
            assertEquals("$id: its level", "B1", u.level)
            assertEquals("$id: three sheets", 3, u.worksheets.size)
            assertTrue("$id: five steps or more", u.intro!!.steps.size >= 5)
            assertTrue("$id: a quick question a step", u.intro!!.steps.all { it.ask != null })
        }
        val first = mapOf("relativsatz" to "relativsatz", "relativsatz-praeposition" to "relativsatz", "adjektiv-alle-faelle" to "adjektiv-tabelle", "schnellere" to "adjektiv-tabelle", "um-zu" to "infinitiv-zu", "obwohl-trotzdem" to "obwohl-trotzdem")
        for ((unit, page) in first) assertEquals(unit, page, course.units.getValue(unit).grammar.first())
    }

    @Test fun `the four new pages are in the book at B1, each with its own card`() {
        val book = Grammar.bundled("de").associateBy { it.id }
        for (id in listOf("relativsatz", "adjektiv-tabelle", "infinitiv-zu", "obwohl-trotzdem")) {
            val page = checkNotNull(book[id]) { "$id isn't in the book" }
            assertEquals(id, "B1", page.level)
            assertNotNull("$id has no hand-written card", page.card)
        }
    }

    @Test fun `denen is its own word - denn and deren for it are mistakes, while a slip elsewhere stays one`() {
        assertEquals(Verdict.WRONG, de("denn", "denen").verdict)
        assertEquals(Verdict.WRONG, de("den", "denen").verdict)
        assertEquals(Verdict.WRONG, de("dem", "denen").verdict)
        assertEquals(Verdict.WRONG, de("deren", "denen").verdict)
        assertEquals(Verdict.WRONG, de("denen", "deren").verdict)
        assertEquals(Verdict.WRONG, de("Das sind die Kinder, denn Kathi Kuchen gibt.", "Das sind die Kinder, denen Kathi Kuchen gibt.").verdict)
        assertEquals(Verdict.CORRECT, de("denen", "denen").verdict)
        // a relative's one s, and the letters of a pronoun under four letters are never a slip
        assertEquals(Verdict.WRONG, de("dass", "das").verdict)
        assertEquals(Verdict.WRONG, de("dem", "den").verdict)
        assertEquals(Verdict.WRONG, de("der", "die").verdict)
        // the long word beside it: a slip in the stem stays one
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("Das ist der Bus, der nach Vilach fährt.", "Das ist der Bus, der nach Villach fährt."))
    }

    @Test fun `welcher is right beside der, in a gap and in a line`() {
        val gap = sheet("welchen", listOf("den", "welchen"))
        assertEquals(Verdict.CORRECT, gap.verdict)
        val line = listOf("Das ist der Traktor, den Stefan gekauft hat.", "Das ist der Traktor, welchen Stefan gekauft hat.")
        assertEquals(Verdict.CORRECT, sheet("Das ist der Traktor, welchen Stefan gekauft hat.", line).verdict)
        assertEquals(Verdict.WRONG, sheet("Das ist der Traktor, der Stefan gekauft hat.", line).verdict)
    }

    @Test fun `no contraction before a relative, and the two-way preposition's case is the case`() {
        assertEquals(Verdict.WRONG, de("Das Haus, im ich wohne.", "Das Haus, in dem ich wohne.").verdict)
        assertEquals(Verdict.WRONG, de("Der Tisch, auf den das Brot liegt.", "Der Tisch, auf dem das Brot liegt.").verdict)
        assertEquals(Verdict.WRONG, de("Der Tisch, auf dem ich das Brot lege.", "Der Tisch, auf den ich das Brot lege.").verdict)
    }

    @Test fun `der Tag, wo is spoken, right and labelled, and der Tag, an dem the standard`() {
        val r = sheet("Das ist der Tag, wo wir feiern.", listOf("Das ist der Tag, an dem wir feiern."), listOf("Das ist der Tag, wo wir feiern."))
        assertEquals(Verdict.CORRECT to "Das ist der Tag, an dem wir feiern.", r.verdict to r.standard)
    }

    @Test fun `the comparative's umlaut with its ending is grammar, left out it's wrong`() {
        assertEquals(Verdict.WRONG, de("die altere Frau", "die ältere Frau").verdict)
        assertEquals(Verdict.WRONG, de("einen großeren Keller", "einen größeren Keller").verdict)
        assertEquals(Verdict.WRONG, de("die jungere Schwester", "die jüngere Schwester").verdict)
        assertEquals(Verdict.WRONG, de("der hohere Berg", "der höhere Berg").verdict)
        assertEquals(Verdict.WRONG, de("der hochere Berg", "der höhere Berg").verdict)
        assertEquals(Verdict.WRONG, de("ein altester Freund", "ein ältester Freund").verdict)
        assertEquals(Verdict.WRONG, de("die jungste Frau", "die jüngste Frau").verdict)
        // what a keyboard lacks stays almost, and a word with no umlaut a slip
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("die aeltere Frau", "die ältere Frau"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("einen groesseren Keller", "einen größeren Keller"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("der schnelere Zug", "der schnellere Zug"))
    }

    @Test fun `the comparative's ending is the ending, never a slip`() {
        assertGraded(Verdict.WRONG, Hint.ENDING, de("ein schnellere Zug", "ein schnellerer Zug"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("dem schnellerer Zug", "dem schnelleren Zug"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("das größere Haus", "das größeres Haus"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("mein beste Freund", "mein bester Freund"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("der schneller Zug", "der schnellere Zug"))
    }

    @Test fun `a separable verb's zu goes inside - without it, or where none goes, it's a mistake`() {
        assertEquals(Verdict.WRONG, de("aufstehen", "aufzustehen").verdict)
        assertEquals(Verdict.WRONG, de("anrufen", "anzurufen").verdict)
        assertEquals(Verdict.WRONG, de("mitkommen", "mitzukommen").verdict)
        assertEquals(Verdict.WRONG, de("einkaufen", "einzukaufen").verdict)
        assertEquals(Verdict.WRONG, de("zumachen", "zuzumachen").verdict)
        assertEquals(Verdict.WRONG, de("zu aufstehen", "aufzustehen").verdict)
        assertEquals(Verdict.WRONG, de("aufzustehen", "aufstehen").verdict)
        assertEquals(Verdict.WRONG, de("Ich muss früh aufzustehen.", "Ich muss früh aufstehen.").verdict)
        // a slip in it stays one, and a verb that doesn't separate keeps its zu apart
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("aufzusthen", "aufzustehen"))
        assertEquals(Verdict.CORRECT, de("zu besuchen", "zu besuchen").verdict)
        assertEquals(Verdict.WRONG, de("zubesuchen", "zu besuchen").verdict)
    }

    @Test fun `Stadt for statt is another word, and a modal's verb takes no zu`() {
        assertEquals(Verdict.WRONG, de("stadt", "statt").verdict)
        assertEquals(Verdict.WRONG, de("statt", "stadt").verdict)
        assertEquals(Verdict.WRONG, de("Kathi will zu kochen.", "Kathi will kochen.").verdict)
        assertEquals(Verdict.WRONG, de("Stefan fährt nach Villach, für Most zu holen.", "Stefan fährt nach Villach, um Most zu holen.").verdict)
    }

    @Test fun `obwohl with the verb second and trotzdem as a conjunction are spoken, right and labelled`() {
        val second = sheet("Stefan arbeitet, obwohl er ist krank.", listOf("Stefan arbeitet, obwohl er krank ist."), listOf("Stefan arbeitet, obwohl er ist krank."))
        assertEquals(Verdict.CORRECT to "Stefan arbeitet, obwohl er krank ist.", second.verdict to second.standard)
        val conj = sheet("Trotzdem", listOf("Obwohl"), listOf("Trotzdem"))
        assertEquals(Verdict.CORRECT to "Obwohl", conj.verdict to conj.standard)
        // the main verb after a clause that comes first has no such standing, nor one after trotzdem
        assertEquals(Verdict.WRONG, sheet("Obwohl es regnet, wir gehen spazieren.", listOf("Obwohl es regnet, gehen wir spazieren.")).verdict)
        assertEquals(Verdict.WRONG, sheet("Es regnet, trotzdem wir gehen spazieren.", listOf("Es regnet, trotzdem gehen wir spazieren.", "Es regnet, wir gehen trotzdem spazieren.")).verdict)
        // trotzdem in its other place is simply right
        assertEquals(Verdict.CORRECT, sheet("Es regnet, wir gehen trotzdem spazieren.", listOf("Es regnet, trotzdem gehen wir spazieren.", "Es regnet, wir gehen trotzdem spazieren.")).verdict)
    }

    @Test fun `a spoken form of these units is never a wrong option or a distractor`() {
        val course = checkNotNull(Courses.bundled("de"))
        val spoken = setOf("trotzdem", "wo")
        val obwohl = course.units.getValue("obwohl-trotzdem")
        val choices = (obwohl.worksheets.flatMap { it.exercises } + obwohl.test.exercises).filterIsInstance<Exercise.Choice>()
        assertTrue("the unit has choices", choices.isNotEmpty())
        for (c in choices) {
            val wrong = c.options.filterIndexed { i, _ -> i != c.answer }.map { it.lowercase() }
            if ("___ es regnet" in c.prompt) assertTrue(c.prompt, wrong.none { it in spoken })
        }
    }
}
