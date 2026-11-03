package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * German B2's last six units (indem-sodass to oesterreichisch: docs/plans/05-built/course-de-b2-c.md) as the app grades them:
 * so dass, in Frage and andrerseits are one answer with sodass, infrage and andererseits; a word one letter off another is no slip
 * (teuer for heuer, Fieber for Feber, versuche for ersuche, stehle for stelle); Austria's word and Germany's are both right;
 * spoken Carinthian and dir's are right on a sheet, labelled with the standard; a choice never offers what the table says people say.
 */
class GermanB2cContentTest {
    private fun de(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.DE)
    private fun assertGraded(verdict: Verdict, hint: Hint, r: Grading.Result) {
        assertEquals("verdict of «${r.expected}»", verdict, r.verdict)
        assertEquals("hint of «${r.expected}»", hint, r.hint)
    }

    private val ids = listOf("indem-sodass", "zwar-aber", "nomen-verb", "satzbau", "formeller-brief", "oesterreichisch")
    private val course = checkNotNull(Courses.bundled("de"))

    /** Every item of the six units' sheets and tests. */
    private val items: List<SheetItem> by lazy {
        ids.flatMap { id ->
            val u = course.units.getValue(id)
            (u.worksheets.flatMap { it.exercises } + u.test.exercises).filter { it !is Exercise.Sort }.flatMap { Sheets.items(it, Lang.DE) }
        }
    }

    @Test fun `two spellings of one word are one answer`() {
        assertEquals(Verdict.CORRECT, de("Es regnete, so dass wir blieben.", "Es regnete, sodass wir blieben.").verdict)
        assertEquals(Verdict.CORRECT, de("sodass", "so dass").verdict)
        assertEquals(Verdict.CORRECT, de("Das kommt nicht infrage.", "Das kommt nicht in Frage.").verdict)
        assertEquals(Verdict.CORRECT, de("in Frage", "infrage").verdict)
        assertEquals(Verdict.CORRECT, de("andrerseits", "andererseits").verdict)
        assertEquals(Verdict.CORRECT, de("anderseits", "andererseits").verdict)
    }

    @Test fun `indem is no in dem, and dass is no das`() {
        assertEquals(Verdict.WRONG, de("in dem", "indem").verdict)
        assertEquals(Verdict.WRONG, de("ohne das", "ohne dass").verdict)
        assertEquals(Verdict.WRONG, de("das", "dass").verdict)
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("indm", "indem"))
    }

    @Test fun `a word one letter off another is no slip, a slip in the word stays one`() {
        for ((typed, right) in listOf(
            "war" to "zwar", "teuer" to "heuer", "neuer" to "heuer", "heuer" to "teuer", "Fieber" to "Feber", "Feber" to "Fieber", "Pause" to "Jause", "Jause" to "Pause",
            "Tropfen" to "Topfen", "Topfen" to "Tropfen", "Kessel" to "Sessel", "Sessel" to "Kessel", "Kosten" to "Kasten", "Karten" to "Kasten", "Kisten" to "Kasten", "Kasten" to "Kosten", "Männer" to "Jänner", "Jänner" to "Männer",
            "biete" to "bitte", "bitte" to "biete", "versuche" to "ersuche", "ersuche" to "versuche", "stehle" to "stelle", "stehlen" to "stellen", "sehen" to "stehen", "seht" to "steht",
            "lassen" to "fassen", "passt" to "fasst", "stellt" to "steht", "steht" to "stellt", "Ober" to "Obers",
        )) assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("Tofen", "Topfen"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("Stige", "Stiege"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("Rücksich", "Rücksicht"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("Jaenner", "Jänner"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("waehrend", "während"))
    }

    @Test fun `Erdapfel for the plural stays a mistake, the plural's umlaut being grammar`() {
        assertEquals(Verdict.WRONG, de("Erdapfel", "Erdäpfel").verdict)
        assertEquals(Verdict.CORRECT, de("Kartoffeln", "Erdäpfel", "Kartoffeln").verdict)
    }

    @Test fun `every word Austria has of its own on these sheets takes Germany's too`() {
        val pairs = listOf("heuer" to "dieses Jahr", "Jänner" to "Januar", "Marille" to "Aprikose", "Topfen" to "Quark", "Spital" to "Krankenhaus", "Stiege" to "Treppe", "Sackerl" to "Tüte", "Kasten" to "Schrank", "Erdäpfel" to "Kartoffeln", "Marillen" to "Aprikosen", "Obers" to "Sahne", "Sessel" to "Stuhl")
        val bySheet = items.filter { it.accept.size >= 2 }
        for ((austrian, german) in pairs) {
            val item = bySheet.firstOrNull { it.accept.first() == austrian }
            assertTrue("a sheet asks for $austrian", item != null)
            assertEquals(Verdict.CORRECT, Sheets.check(item!!, german, Lang.DE).verdict)
            assertEquals(Verdict.CORRECT, Sheets.check(item, austrian, Lang.DE).verdict)
        }
        // whole lines, in every mix
        for ((said, own) in listOf(
            "Wir fahren heuer im Januar in den Urlaub." to "Wir fahren dieses Jahr im Januar in den Urlaub.",
            "Lena isst eine Semmel mit Tomate." to "Lena isst ein Brötchen mit Tomate.",
            "Ich habe auf dem Sessel gesessen." to "Ich habe auf dem Stuhl gesessen.",
        )) {
            val item = items.first { own in it.accept }
            assertEquals(said, Verdict.CORRECT, Sheets.check(item, said, Lang.DE).verdict)
        }
    }

    @Test fun `ist gesessen and hat gesessen are both right where the helper is asked`() {
        val item = items.first { it.accept == listOf("ist", "hat") }
        assertEquals(Verdict.CORRECT, Sheets.check(item, "ist", Lang.DE).verdict)
        assertEquals(Verdict.CORRECT, Sheets.check(item, "hat", Lang.DE).verdict)
    }

    @Test fun `what people say on these sheets is right, labelled with the standard form`() {
        val spoken = items.filter { it.spoken.isNotEmpty() }
        val said = spoken.flatMap { it.spoken }
        for (form in listOf("Ich komme lei morgen.", "Die Tür ist ofn.", "Ich bin miad.", "Das ist nit teuer.", "Hast du eppes gehört?", "Auch wenn es regnet, wir gehen.", "Kathi zeigt ihm's.", "Den Most hat der Stefan gemacht.", "Wegen dem Regen fahren wir nicht auf die Alm.")) {
            assertTrue("listed: $form", form in said)
        }
        for (item in spoken) for (s in item.spoken) {
            val m = Sheets.check(item, s, Lang.DE)
            assertEquals("«$s»", Verdict.CORRECT to item.accept.first(), m.verdict to m.standard)
        }
    }

    @Test fun `a word bank's distractor stays wrong, and no choice of the six offers what people say`() {
        for (id in ids) {
            val u = course.units.getValue(id)
            val all = u.worksheets.flatMap { it.exercises } + u.test.exercises + u.intro!!.steps.mapNotNull { it.ask }
            for (e in all) if (e is Exercise.Choice && Regex("_{3,}").containsMatchIn(e.prompt)) {
                val right = e.options[e.answer]
                for ((i, o) in e.options.withIndex()) if (i != e.answer) {
                    val inIt = Grading.check(e.prompt.replace(Regex("_{3,}"), o), listOf(e.prompt.replace(Regex("_{3,}"), right)), Lang.DE)
                    assertTrue("$id: «$o» in «${e.prompt}»", inIt.hint != Hint.NONSTANDARD && inIt.verdict != Verdict.CORRECT)
                }
            }
        }
    }

    @Test fun `the units are at the level's size, and the six new pages are in the book with their own cards`() {
        for (id in ids) {
            val u = course.units.getValue(id)
            assertEquals("$id: its level", "B2", u.level)
            assertEquals("$id: three sheets", 3, u.worksheets.size)
            assertTrue("$id: five steps or more", u.intro!!.steps.size >= 5)
            assertTrue("$id: a quick question a step", u.intro!!.steps.all { it.ask != null })
            assertEquals("$id: its own page first", id, u.grammar.first())
        }
        val book = Grammar.bundled("de").associateBy { it.id }
        for (p in ids) {
            assertTrue("$p: its page", book.containsKey(p))
            assertEquals("$p: at B2", "B2", book.getValue(p).level)
            assertTrue("$p: its own card", book.getValue(p).card != null)
        }
    }
}
