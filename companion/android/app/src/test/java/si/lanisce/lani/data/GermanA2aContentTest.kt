package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * German A2, units 1 to 6 (the dative and where things are): the units as the app plays them and what the grading makes
 * of the mistakes they invite. [GermanCourseContentTest] grades every right answer; here, the mistakes a slip in the
 * stem would forgive, the long forms beside the contractions and the spoken forms.
 */
class GermanA2aContentTest {
    private val course = checkNotNull(Courses.bundled("de"))
    private fun verdict(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.DE).verdict

    private val pages = mapOf(
        "wem-dativ" to listOf("dativ", "faelle"),
        "mir-dir" to listOf("pronomen-dativ", "dativ", "akkusativ"),
        "mit-nach-bei" to listOf("praepositionen-dativ", "dativ"),
        "fuer-ohne" to listOf("praepositionen-akkusativ", "akkusativ"),
        "wo-wohin" to listOf("wechselpraepositionen", "dativ", "akkusativ"),
        "stehen-stellen" to listOf("stehen-stellen", "wechselpraepositionen"),
    )

    @Test fun `the six units are written at A2, each naming its pages, the outline's first six`() {
        val a2 = course.playable("A2").map { it.id }
        for ((id, want) in pages) {
            val u = course.units.getValue(id)
            assertEquals("$id: its level", "A2", u.level)
            assertEquals("$id: its pages", want, u.grammar)
            assertTrue("$id: playable", id in a2)
            assertTrue("$id: 5 or 6 steps", u.intro?.steps?.size in 5..6)
            assertEquals("$id: three worksheets", 3, u.worksheets.size)
        }
    }

    @Test fun `the new pages are in the bundled book, each with its own card`() {
        val book = Grammar.bundled("de").associateBy { it.id }
        for (id in listOf("pronomen-dativ", "praepositionen-dativ", "praepositionen-akkusativ", "stehen-stellen")) {
            assertTrue("$id: bundled", id in book)
            assertTrue("$id: a hand-written card", book.getValue(id).card != null)
        }
    }

    @Test fun `the case's endings are never a slip - dem for den, einem for einen, Kinder for Kindern`() {
        assertEquals(Verdict.WRONG, verdict("den Hund", "dem Hund"))
        assertEquals(Verdict.WRONG, verdict("einen", "einem"))
        assertEquals(Verdict.WRONG, verdict("meinen", "meinem"))
        assertEquals(Verdict.WRONG, verdict("ihrem", "ihren"))
        assertEquals(Verdict.WRONG, verdict("den Kinder", "den Kindern"))
        assertEquals(Verdict.WRONG, verdict("zur", "zum"))
        assertEquals(Verdict.WRONG, verdict("ins", "im"))
    }

    @Test fun `the pronouns for each other are wrong - mir, mich, ihm, ihn, ihnen, ihren, euch, auch`() {
        assertEquals(Verdict.WRONG, verdict("mich", "mir"))
        assertEquals(Verdict.WRONG, verdict("dir", "dich"))
        assertEquals(Verdict.WRONG, verdict("ihn", "ihm"))
        assertEquals(Verdict.WRONG, verdict("ihren", "ihnen"))
        assertEquals(Verdict.WRONG, verdict("ihnen", "ihren"))
        assertEquals(Verdict.WRONG, verdict("auch", "euch"))
    }

    @Test fun `legen and liegen, setzen and sitzen are other verbs, never a slip, and only for each other`() {
        for ((typed, right) in listOf("legt" to "liegt", "liegt" to "legt", "lege" to "liege", "legen" to "liegen", "setzt" to "sitzt", "sitzt" to "setzt", "setzen" to "sitzen")) {
            assertEquals("$typed for $right", Verdict.WRONG, verdict(typed, right))
        }
        assertEquals("a slip in another word stays one", Verdict.ALMOST, verdict("Flasche", "Flosche"))
        assertEquals("a slip in another word stays one", Verdict.ALMOST, verdict("Tusch", "Tisch"))
    }

    @Test fun `noch for nach, geben for gegen and ich hilfe are wrong, a slip elsewhere stays one`() {
        assertEquals(Verdict.WRONG, verdict("noch", "nach"))
        assertEquals(Verdict.WRONG, verdict("geben", "gegen"))
        assertEquals(Verdict.WRONG, verdict("gehen", "gegen"))
        assertEquals(Verdict.WRONG, verdict("hilfe", "helfe"))
        assertEquals("umlaut typed out", Verdict.ALMOST, verdict("fuer", "für"))
        assertEquals("a slip in the stem", Verdict.ALMOST, verdict("Kuche", "Küche"))
    }

    @Test fun `the long form is right beside the contraction, and the spoken forms are right and labelled`() {
        fun items(id: String) = course.units.getValue(id).let { u -> (u.worksheets.flatMap { it.exercises } + u.test.exercises) }.flatMap { e -> if (e is Exercise.Sort) emptyList() else Sheets.items(e, Lang.DE) }
        val zum = items("mit-nach-bei").first { "zum Arzt" in it.accept }
        assertEquals(Verdict.CORRECT, Sheets.check(zum, "zu dem Arzt", Lang.DE).verdict)
        val home = items("mit-nach-bei").first { it.spoken.any { s -> "nach Haus." in s } }
        val heard = Sheets.check(home, home.spoken.first(), Lang.DE)
        assertEquals(Verdict.CORRECT, heard.verdict)
        assertEquals(home.accept.first(), heard.standard)
        val table = items("stehen-stellen").first { it.spoken.any { s -> "am Tisch" in s } }
        assertEquals(Verdict.CORRECT, Sheets.check(table, table.spoken.first(), Lang.DE).verdict)
        assertTrue("the standard stays the answer", "auf dem Tisch" in table.accept.first())
    }
}
