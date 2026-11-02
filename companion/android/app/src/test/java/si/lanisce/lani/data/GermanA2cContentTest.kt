package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * German A2's last six units (adjektiv-der to dass-wenn-ob: docs/plans/05-built/course-de-a2-c.md) as the app grades them:
 * an adjective's ending is never a slip; the comparative's umlaut, hoch's hohe and höher, and denn, dann, wenn, wann for
 * each other are known mistakes, each only for a word of its group; the spoken wie for als, weil with the verb second and
 * wann for wenn are right, labelled with the standard form.
 */
class GermanA2cContentTest {
    private fun de(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.DE)
    private fun assertGraded(verdict: Verdict, hint: Hint, r: Grading.Result) {
        assertEquals("verdict of «${r.expected}»", verdict, r.verdict)
        assertEquals("hint of «${r.expected}»", hint, r.hint)
    }

    /** A sheet's item with [accept] and [spoken]: what the sheet makes of [answer]. */
    private fun sheet(answer: String, accept: List<String>, spoken: List<String> = emptyList()) =
        Sheets.check(SheetItem(accept, null, null, null, "", spoken = spoken), answer, Lang.DE)

    @Test fun `an adjective's ending is the ending, on a long stem as on a short one`() {
        assertGraded(Verdict.WRONG, Hint.ENDING, de("der großen Hund", "der große Hund"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("den große Hund", "den großen Hund"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("ein große Hund", "ein großer Hund"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("ein kleine Haus", "ein kleines Haus"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("mit frischen Brot", "mit frischem Brot"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("mit warmem Milch", "mit warmer Milch"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("die alte Häuser", "die alten Häuser"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("gute Morgen", "guten Morgen"))
        // the adjective left bare before a noun, and an ending after sein
        assertGraded(Verdict.WRONG, Hint.ENDING, de("der groß Hund", "der große Hund"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("der Hund ist große", "der Hund ist groß"))
        // what a keyboard lacks stays almost, and a slip in the stem a slip
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("der grosse Hund", "der große Hund"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("mit frishem Brot", "mit frischem Brot"))
    }

    @Test fun `hoch before a noun is hohe, and hoche a mistake, not a slip`() {
        assertGraded(Verdict.WRONG, Hint.NONE, de("der hoche Berg", "der hohe Berg"))
        assertGraded(Verdict.WRONG, Hint.NONE, de("den hochen Berg", "den hohen Berg"))
    }

    @Test fun `the comparative's umlaut is grammar - left out, or put where none goes, it's wrong`() {
        assertGraded(Verdict.WRONG, Hint.NONE, de("Oma ist alter als Mitzi.", "Oma ist älter als Mitzi."))
        assertGraded(Verdict.WRONG, Hint.NONE, de("Stefan ist junger als Franz.", "Stefan ist jünger als Franz."))
        assertGraded(Verdict.WRONG, Hint.NONE, de("Klagenfurt ist grosser als Villach.", "Klagenfurt ist größer als Villach."))
        assertGraded(Verdict.WRONG, Hint.NONE, de("Der Jänner ist kalter als der März.", "Der Jänner ist kälter als der März."))
        assertGraded(Verdict.WRONG, Hint.NONE, de("Opa ist am altesten.", "Opa ist am ältesten."))
        assertGraded(Verdict.WRONG, Hint.NONE, de("ein größer Hund", "ein großer Hund"))
        assertGraded(Verdict.WRONG, Hint.NONE, de("ein älter Traktor", "ein alter Traktor"))
        assertGraded(Verdict.WRONG, Hint.NONE, de("Der Berg ist hoher als der Turm.", "Der Berg ist höher als der Turm."))
        assertGraded(Verdict.WRONG, Hint.NONE, de("Der Berg ist hocher als der Turm.", "Der Berg ist höher als der Turm."))
        assertGraded(Verdict.WRONG, Hint.NONE, de("Hier ist er am größesten.", "Hier ist er am größten."))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("Oma ist aelter als Mitzi.", "Oma ist älter als Mitzi."))
        // a slip that isn't the umlaut stays one
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("Der Traktor ist schneler.", "Der Traktor ist schneller."))
    }

    @Test fun `denn, dann, wenn, wann for each other and das for dass are wrong, while a slip elsewhere stays one`() {
        assertEquals(Verdict.WRONG, de("dann", "denn").verdict)
        assertEquals(Verdict.WRONG, de("denn", "dann").verdict)
        assertEquals(Verdict.WRONG, de("wenn", "denn").verdict)
        assertEquals(Verdict.WRONG, de("wann", "wenn").verdict)
        assertEquals(Verdict.WRONG, de("wenn", "wann").verdict)
        assertEquals(Verdict.WRONG, de("das", "dass").verdict)
        assertEquals(Verdict.WRONG, de("dass", "das").verdict)
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("deshlab", "deshalb"))
    }

    @Test fun `a spoken form of these units is right, labelled with the standard one, and only where it is spoken`() {
        val als = sheet("wie", listOf("als"), listOf("wie"))
        assertEquals(Verdict.CORRECT to "als", als.verdict to als.standard)
        assertEquals(Verdict.WRONG, sheet("als", listOf("wie")).verdict)
        val wenn = sheet("Wann", listOf("Wenn"), listOf("Wann"))
        assertEquals(Verdict.CORRECT to "Wenn", wenn.verdict to wenn.standard)
        val weil = sheet("Toni kommt nicht, weil er muss arbeiten.", listOf("Toni kommt nicht, weil er arbeiten muss."), listOf("Toni kommt nicht, weil er muss arbeiten."))
        assertEquals(Verdict.CORRECT to "Toni kommt nicht, weil er arbeiten muss.", weil.verdict to weil.standard)
        // the mistake after deshalb has no such standing: the verb comes right after it
        assertEquals(Verdict.WRONG, sheet("Es regnet, deshalb Stefan bleibt zu Hause.", listOf("Es regnet, deshalb bleibt Stefan zu Hause.")).verdict)
    }

    @Test fun `the units are at the level's size, and the five new pages are in the book with their own cards`() {
        val course = checkNotNull(Courses.bundled("de"))
        val ids = listOf("adjektiv-der", "adjektiv-ein", "adjektiv-null", "groesser-als", "weil-denn", "dass-wenn-ob")
        for (id in ids) {
            val u = course.units.getValue(id)
            assertEquals("$id: its level", "A2", u.level)
            assertEquals("$id: three sheets", 3, u.worksheets.size)
            assertTrue("$id: five steps or more", u.intro!!.steps.size >= 5)
            assertTrue("$id: a quick question a step", u.intro!!.steps.all { it.ask != null })
        }
        val book = Grammar.bundled("de").associateBy { it.id }
        for (p in listOf("adjektiv-der", "adjektiv-ein", "adjektiv-null", "steigerung", "konnektoren"))
            assertTrue("$p: its page", book.containsKey(p))
    }
}
