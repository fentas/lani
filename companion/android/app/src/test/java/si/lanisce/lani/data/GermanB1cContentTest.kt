package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * German B1's last six units (haette-waere to nicht-nur-sondern: docs/plans/05-built/course-de-b1-c.md) as the app grades
 * them: the mood's umlaut is grammar, never a slip (würde and wurde, käme and kame, wüsste and wusste); hätt, wär and würd are
 * one answer with hätte, wäre, würde, as hab is habe; the wo- and da- words are each a word of their own (darum, warum,
 * worum; davon, davor), and the r is what they get wrong (woauf, darmit); wieder and weder, doch and noch are no slips. What
 * people say is right where it is listed and never a wrong option: Auf was, drauf, dran, wer, nix, täte.
 */
class GermanB1cContentTest {
    private fun de(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.DE)
    private fun assertGraded(verdict: Verdict, hint: Hint, r: Grading.Result) {
        assertEquals("verdict of «${r.expected}»", verdict, r.verdict)
        assertEquals("hint of «${r.expected}»", hint, r.hint)
    }

    /** A sheet's item with [accept] and [spoken]: what the sheet makes of [answer]. */
    private fun sheet(answer: String, accept: List<String>, spoken: List<String> = emptyList()) =
        Sheets.check(SheetItem(accept, null, null, null, "", spoken = spoken), answer, Lang.DE)

    private val ids = listOf("haette-waere", "wenn-ich-haette", "passiv", "woran-daran", "jemand-irgendwer", "nicht-nur-sondern")
    private val course = checkNotNull(Courses.bundled("de"))

    @Test fun `würde, wurde and werde are three words, either way`() {
        for ((typed, right) in listOf("wurde" to "würde", "würde" to "wurde", "werde" to "wurde", "wurde" to "werde", "werde" to "würde", "würde" to "werde", "wurden" to "würden", "würden" to "wurden", "werden" to "wurden", "wurden" to "werden", "wurdest" to "würdest", "würdet" to "wurdet")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("wuerde", "würde"))
    }

    @Test fun `the short forms of speech are one answer with the whole word, and only those`() {
        assertEquals(Verdict.CORRECT, de("ich hätt gern einen Kaffee", "ich hätte gern einen Kaffee").verdict)
        assertEquals(Verdict.CORRECT, de("das wär schön", "das wäre schön").verdict)
        assertEquals(Verdict.CORRECT, de("ich würd gern tanzen", "ich würde gern tanzen").verdict)
        assertEquals(Verdict.CORRECT, de("hätt", "hätte").verdict)
        // war and hatte stay the past
        assertEquals(Verdict.WRONG, de("das war schön", "das wäre schön").verdict)
        assertEquals(Verdict.WRONG, de("hatte", "hätte").verdict)
        assertEquals(Verdict.WRONG, de("hatt", "hätte").verdict)
    }

    @Test fun `the mood's umlaut is grammar, on the strong verbs and the modals`() {
        for ((typed, right) in listOf("kame" to "käme", "kamen" to "kämen", "kämen" to "kamen", "gabe" to "gäbe", "wusste" to "wüsste", "wüsste" to "wusste", "wussten" to "wüssten", "brauchte" to "bräuchte", "bräuchte" to "brauchte", "söllte" to "sollte", "wöllte" to "wollte", "solltet" to "solltest", "könnte" to "konnte", "konnte" to "könnte", "müsste" to "musste")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("kaeme", "käme"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("koennte", "könnte"))
        // the rule is for these words: a slip in another word's stem stays one
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("Kafee", "Kaffee"))
    }

    @Test fun `a participle without its ge and gewaschen for gewachsen are mistakes, a slip elsewhere stays one`() {
        assertEquals(Verdict.WRONG, de("Die Äpfel werden waschen.", "Die Äpfel werden gewaschen.").verdict)
        assertEquals(Verdict.WRONG, de("Die Äpfel wurden sammelt.", "Die Äpfel wurden gesammelt.").verdict)
        assertEquals(Verdict.WRONG, de("gewachsen", "gewaschen").verdict)
        assertEquals(Verdict.WRONG, de("gewaschen", "gewachsen").verdict)
        assertEquals(Verdict.WRONG, de("backen", "gebacken").verdict)
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("gepflükt", "gepflückt"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("reperiert", "repariert"))
    }

    @Test fun `the wo- and da- words are words of their own, and the r is no slip`() {
        for ((typed, right) in listOf(
            "woauf" to "worauf", "daauf" to "darauf", "woan" to "woran", "daan" to "daran", "woüber" to "worüber",
            "darmit" to "damit", "wormit" to "womit", "darfür" to "dafür", "worfür" to "wofür", "darvon" to "davon",
            "darum" to "warum", "warum" to "darum", "worum" to "warum", "warum" to "worum", "worum" to "darum",
            "davon" to "davor", "davor" to "davon", "wovon" to "wovor", "wovor" to "wovon",
            "daraus" to "darauf", "darauf" to "daraus", "woraus" to "worauf", "darin" to "daran", "daran" to "darin", "worin" to "woran",
            "darauf" to "worauf", "worauf" to "darauf", "damit" to "womit", "dafür" to "wofür",
        )) assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("wofuer", "wofür"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("darueber", "darüber"))
    }

    @Test fun `weder and wieder, noch and doch are words of their own, a slip in sowohl and entweder stays one`() {
        assertEquals(Verdict.WRONG, de("wieder", "weder").verdict)
        assertEquals(Verdict.WRONG, de("jeder", "weder").verdict)
        assertEquals(Verdict.WRONG, de("weder", "wieder").verdict)
        assertEquals(Verdict.WRONG, de("doch", "noch").verdict)
        assertEquals(Verdict.WRONG, de("noch", "doch").verdict)
        assertEquals(Verdict.WRONG, de("nach", "noch").verdict)
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("swohl", "sowohl"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("entwder", "entweder"))
    }

    @Test fun `an indefinite pronoun's ending is the ending, and jemand with none is right where the sheet lists it`() {
        assertEquals(Verdict.WRONG, de("niemandem", "niemanden").verdict)
        assertEquals(Verdict.WRONG, de("jemanden", "jemandem").verdict)
        assertEquals(Verdict.WRONG, de("irgendwem", "irgendwen").verdict)
        assertEquals(Verdict.WRONG, de("keinen", "keinem").verdict)
        assertEquals(Verdict.WRONG, de("demselben", "denselben").verdict)
        assertEquals(Verdict.WRONG, de("dasselbe", "dieselbe").verdict)
        assertEquals(Verdict.WRONG, de("nicht", "nichts").verdict)
        // Duden: Akkusativ jemanden, auch jemand; niemanden, niemand
        assertEquals(Verdict.CORRECT, sheet("niemand", listOf("niemanden", "niemand")).verdict)
    }

    @Test fun `a spoken form of these units is right, labelled with the standard one`() {
        for ((said, standard) in listOf("Auf was" to "Worauf", "an was" to "woran", "Vor was" to "Wovor", "drauf" to "darauf", "dran" to "daran", "drüber" to "darüber", "auf ihn" to "darauf", "wer" to "jemand", "nix" to "nichts", "was" to "etwas", "täte" to "würde", "tät" to "würde")) {
            val m = sheet(said, listOf(standard), listOf(said))
            assertEquals("$said for $standard", Verdict.CORRECT to standard, m.verdict to m.standard)
        }
        val line = sheet("Ja, ich warte auf ihn.", listOf("Ja, ich warte darauf."), listOf("Ja, ich warte drauf.", "Ja, ich warte auf ihn."))
        assertEquals(Verdict.CORRECT to "Ja, ich warte darauf.", line.verdict to line.standard)
    }

    @Test fun `what people say is listed on the sheets and never offered as a wrong choice`() {
        val said = setOf("auf was", "an was", "für was", "über was", "vor was", "von was", "drauf", "dran", "drüber", "nix", "täte", "tät", "hätt", "wär", "würd", "der gleiche", "den gleichen", "das gleiche")
        fun offered(e: Exercise?): List<String> = when (e) {
            is Exercise.Choice -> e.options.filterIndexed { i, _ -> i != e.answer }
            else -> emptyList()
        }
        val spoken = mutableSetOf<String>()
        for (id in ids) {
            val u = course.units.getValue(id)
            val all = u.worksheets.flatMap { it.exercises } + u.test.exercises
            val options = (all + u.intro!!.steps.mapNotNull { it.ask }).flatMap(::offered).map { it.lowercase() }
            assertEquals("$id: a spoken form as a wrong option", emptyList<String>(), options.filter { it in said })
            all.filter { it !is Exercise.Sort }.flatMap { Sheets.items(it, Lang.DE) }.forEach { item ->
                spoken += item.spoken.map { it.lowercase() }
                assertTrue("$id: a spoken form that is a distractor", item.wrong.none { w -> item.spoken.any { it.equals(w, true) } })
            }
        }
        for (s in listOf("auf was", "an was", "drauf", "wer", "nix", "täte")) assertTrue("listed on a sheet: $s", s in spoken)
    }

    @Test fun `the units are at the level's size, and the five new pages are in the book with their own cards`() {
        for (id in ids) {
            val u = course.units.getValue(id)
            assertEquals("$id: its level", "B1", u.level)
            assertEquals("$id: three sheets", 3, u.worksheets.size)
            assertTrue("$id: five steps or more", u.intro!!.steps.size >= 5)
            assertTrue("$id: a quick question a step", u.intro!!.steps.all { it.ask != null })
        }
        val book = Grammar.bundled("de").associateBy { it.id }
        for (p in listOf("konjunktiv-zwei", "passiv", "wo-da-woerter", "indefinitpronomen", "doppelkonnektoren")) {
            assertTrue("$p: its page", book.containsKey(p))
            assertEquals("$p: at B1", "B1", book.getValue(p).level)
        }
    }
}
