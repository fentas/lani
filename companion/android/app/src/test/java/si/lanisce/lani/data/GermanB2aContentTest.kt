package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * German B2's first six units (haette-gewusst to soll-duerfte: docs/plans/05-built/course-de-b2-a.md) as the app grades them:
 * worden, wurden and werden are three words, as soll and will are, and a long participle without its ge is no slip. What
 * people say is never a wrong option: the present and the Konjunktiv II for a reported Konjunktiv I (Er sagt, er ist krank; er wäre
 * krank) and the present after als ob are almost, with the form writing wants shown, for those verbs only and only after a
 * verb of saying or als ob; wie wenn is one answer with als wenn (DWDS has no label on it), and wenn ich kommen hätte können is right, labelled.
 */
class GermanB2aContentTest {
    private fun de(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.DE)

    /** A sheet's item with [accept] and [spoken], in the sentence [frame]: what the sheet makes of [answer]. */
    private fun sheet(answer: String, accept: List<String>, frame: String = Sheets.BLANK, spoken: List<String> = emptyList()) =
        Sheets.check(SheetItem(accept, null, null, null, frame, spoken = spoken), answer, Lang.DE)

    private val ids = listOf("haette-gewusst", "als-ob", "konjunktiv-eins", "passiv-zeiten", "passiv-ersatz", "soll-duerfte")
    private val course = checkNotNull(Courses.bundled("de"))

    private fun assertNonstandard(said: String, standard: String) {
        val r = de(said, standard)
        assertEquals("«$said» for «$standard»", Verdict.ALMOST to Hint.NONSTANDARD, r.verdict to r.hint)
        assertEquals("«$said» shows «$standard»", standard, r.expected)
    }

    @Test fun `worden, wurden and werden are three words, either way, and worden and geworden too`() {
        for ((typed, right) in listOf("wurden" to "worden", "werden" to "worden", "worden" to "wurden", "worden" to "werden", "geworden" to "worden", "worden" to "geworden")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        assertEquals(Verdict.WRONG, de("Das Haus ist gebaut geworden.", "Das Haus ist gebaut worden.").verdict)
        assertEquals(Verdict.WRONG, de("Das Haus ist gebaut wurden.", "Das Haus ist gebaut worden.").verdict)
        assertEquals(Verdict.WRONG, de("Das Dach muss repariert worden.", "Das Dach muss repariert werden.").verdict)
        assertEquals(Hint.TYPO, de("Das Haus ist gebaut wrden.", "Das Haus ist gebaut worden.").hint)
    }

    @Test fun `soll and will are words of their own in every form, a slip stays one`() {
        for ((typed, right) in listOf("wolle" to "solle", "solle" to "wolle", "wollen" to "sollen", "sollen" to "wollen", "wollte" to "sollte", "sollte" to "wollte", "will" to "soll", "soll" to "will")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        for ((typed, right) in listOf("durfte" to "dürfte", "dürfte" to "durfte", "musste" to "müsste", "konnte" to "könnte", "könnte" to "konnte")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        assertEquals(Hint.UMLAUT, de("duerfte", "dürfte").hint)
    }

    @Test fun `a long participle without its ge is a mistake, a slip inside it is still one`() {
        for ((typed, right) in listOf("schlafen" to "geschlafen", "arbeitet" to "gearbeitet", "blieben" to "geblieben", "öffnet" to "geöffnet", "schlossen" to "geschlossen", "strichen" to "gestrichen", "ankommen" to "angekommen", "eröffnet" to "geöffnet", "geöffnet" to "eröffnet")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        assertEquals(Hint.TYPO, de("gestichen", "gestrichen").hint)
        assertEquals(Hint.UMLAUT, de("geoeffnet", "geöffnet").hint)
    }

    @Test fun `hatte and war stay the past, and the unit's forms need their own person`() {
        for ((typed, right) in listOf("hatte" to "hätte", "hatten" to "hätten", "hattest" to "hättest", "war" to "wäre", "waren" to "wären", "warst" to "wärst", "wart" to "wärt", "hättest" to "hättet", "wärst" to "wärt", "sei" to "seien", "seien" to "sei", "sie" to "sei", "sein" to "sei", "seinen" to "seien", "seine" to "seien")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        assertEquals(Verdict.CORRECT, de("Hätt ich das nur gewusst!", "Hätte ich das nur gewusst!").verdict)
        assertEquals(Verdict.CORRECT, de("Wär er doch gekommen!", "Wäre er doch gekommen!").verdict)
    }

    @Test fun `after a verb of saying the present and the Konjunktiv II are what people say, almost with the written form shown`() {
        for ((said, standard) in listOf(
            "Er sagt, er ist krank." to "Er sagt, er sei krank.",
            "Er sagt, dass er krank ist." to "Er sagt, dass er krank sei.",
            "Er hat gesagt, er wäre krank." to "Er hat gesagt, er sei krank.",
            "Toni sagt, er hat keine Zeit." to "Toni sagt, er habe keine Zeit.",
            "Toni sagt, er hätte keine Zeit." to "Toni sagt, er habe keine Zeit.",
            "Mitzi sagt, sie kommt morgen." to "Mitzi sagt, sie komme morgen.",
            "Toni sagt, er kann morgen kommen." to "Toni sagt, er könne morgen kommen.",
            "Die Kinder sagen, sie haben Hunger." to "Die Kinder sagen, sie hätten Hunger.",
            "Die Gäste sagen, sie kommen morgen." to "Die Gäste sagen, sie kämen morgen.",
            "Die Bauern sagen, sie sind zufrieden." to "Die Bauern sagen, sie seien zufrieden.",
            "Sie erzählt, er ist krank." to "Sie erzählt, er sei krank.",
            "Sie erklärt, sie ist müde." to "Sie erklärt, sie sei müde.",
            "Kathi sagt, ich soll warten." to "Kathi sagt, ich solle warten.",
        )) assertNonstandard(said, standard)
        // a gap: the sentence before it says who said so
        val inGap = sheet("ist", listOf("sei"), "Der Bürgermeister sagt, die Brücke ___ alt.")
        assertEquals(Verdict.ALMOST to Hint.NONSTANDARD, inGap.verdict to inGap.hint)
        assertEquals("sei", inGap.expected)
    }

    @Test fun `with no verb of saying, after ich or for another verb those forms stay wrong`() {
        assertEquals(Verdict.WRONG, de("Ist still!", "Sei still!").verdict)
        assertEquals(Verdict.WRONG, de("ist", "sei").verdict)
        assertEquals(Verdict.WRONG, de("Er ist krank.", "Er sei krank.").verdict)
        assertEquals(Verdict.WRONG, de("kommt", "komme").verdict)
        assertEquals(Verdict.WRONG, de("Stefan sagt: Ich kommt morgen.", "Stefan sagt: Ich komme morgen.").verdict)
        assertEquals(Verdict.WRONG, de("Stefan sagt: Ich hat keine Zeit.", "Stefan sagt: Ich habe keine Zeit.").verdict)
        // another verb altogether
        assertEquals(Verdict.WRONG, de("Mitzi sagt, sie geht morgen.", "Mitzi sagt, sie komme morgen.").verdict)
        assertEquals(Verdict.WRONG, de("Er sagt, er war krank.", "Er sagt, er sei krank.").verdict)
        // one way only: the reported form where the present is asked
        assertEquals(Verdict.WRONG, de("Er sagt, er sei krank.", "Er sagt, er ist krank.").verdict)
    }

    @Test fun `after als ob the present is what people say, almost, and the word order is no such thing`() {
        for ((said, standard) in listOf(
            "Er tut, als ob er krank ist." to "Er tut, als ob er krank wäre.",
            "Er tut, als wenn er krank ist." to "Er tut, als wenn er krank wäre.",
            "Er tut, als ob er schläft." to "Er tut, als ob er schliefe.",
            "Franz tut, als ob er Geld hat." to "Franz tut, als ob er Geld hätte.",
            "Als ob ich das nicht weiß!" to "Als ob ich das nicht wüsste!",
        )) assertNonstandard(said, standard)
        val inGap = sheet("hat", listOf("hätte", "habe"), "Franz tut, als ob er keine Zeit ___.")
        assertEquals(Verdict.ALMOST to Hint.NONSTANDARD, inGap.verdict to inGap.hint)
        assertEquals(Verdict.WRONG, de("Er tut, als er krank wäre.", "Er tut, als wäre er krank.").verdict)
        assertEquals(Verdict.WRONG, de("Er tut, als ob wäre er krank.", "Er tut, als ob er krank wäre.").verdict)
        assertEquals(Verdict.WRONG, de("Wenn er krank ist", "Wenn er krank wäre").verdict)
    }

    @Test fun `wie wenn is right with no label, and the Austrian order of two infinitives is right, labelled`() {
        val lines = course.units.getValue("als-ob").worksheets.flatMap { it.exercises }.filter { it !is Exercise.Sort }.flatMap { Sheets.items(it, Lang.DE) }
        val line = checkNotNull(lines.firstOrNull { "Er tut, wie wenn er krank wäre." in it.accept })
        val w = Sheets.check(line, "Er tut, wie wenn er krank wäre.", Lang.DE)
        assertEquals(Verdict.CORRECT to null, w.verdict to w.standard)
        val order = course.units.getValue("haette-gewusst").worksheets.flatMap { it.exercises }.filter { it !is Exercise.Sort }.flatMap { Sheets.items(it, Lang.DE) }
            .firstOrNull { "Ich bin böse, weil du anrufen hättest sollen." in it.spoken }
        assertNotNull("wenn ich kommen hätte können's order is listed on a sheet", order)
        val o = Sheets.check(order!!, "Ich bin böse, weil du anrufen hättest sollen.", Lang.DE)
        assertEquals(Verdict.CORRECT to "Ich bin böse, weil du hättest anrufen sollen.", o.verdict to o.standard)
        // the participle where two infinitives go stays a learner's mistake
        assertEquals(Verdict.WRONG, de("Ich hätte gekommen können.", "Ich hätte kommen können.").verdict)
    }

    @Test fun `both standards are accepted, wäre gesessen and hätte gesessen, and the Konjunktiv I after als ob`() {
        val table = course.units.getValue("haette-gewusst").worksheets.first().exercises.first()
        val cell = Sheets.items(table, Lang.DE).first { "wäre gesessen" in it.accept }
        assertEquals("the Austrian form first", "wäre gesessen", cell.accept.first())
        assertTrue("hätte gesessen, Germany's", "hätte gesessen" in cell.accept)
        assertEquals(Verdict.CORRECT, Sheets.check(cell, "hätte gesessen", Lang.DE).verdict)
        assertEquals(Verdict.CORRECT, sheet("sei", listOf("wäre", "sei"), "Er spricht, als ___ er der Bürgermeister.").verdict)
    }

    @Test fun `the units are at the level's size, and the six new pages are in the book with their own cards`() {
        for (id in ids) {
            val u = course.units.getValue(id)
            assertEquals("$id: its level", "B2", u.level)
            assertEquals("$id: three sheets", 3, u.worksheets.size)
            assertTrue("$id: five steps or more", u.intro!!.steps.size >= 5)
            assertTrue("$id: a quick question a step", u.intro!!.steps.all { it.ask != null })
            val items = u.worksheets.flatMap { it.exercises }.sumOf { e -> if (e is Exercise.Sort) e.columns.sumOf { it.words.size } else if (e is Exercise.Choice) 1 else Sheets.items(e, Lang.DE).size }
            assertTrue("$id: $items items on its sheets, at most 35", items in 30..35)
        }
        val book = Grammar.bundled("de").associateBy { it.id }
        for (p in listOf("konjunktiv-vergangenheit", "als-ob", "indirekte-rede", "passiv-zeiten", "passiv-ersatz", "modalverben-vermutung")) {
            assertTrue("$p: its page", book.containsKey(p))
            assertEquals("$p: at B2", "B2", book.getValue(p).level)
        }
        for ((id, first) in ids.zip(listOf("konjunktiv-vergangenheit", "als-ob", "indirekte-rede", "passiv-zeiten", "passiv-ersatz", "modalverben-vermutung"))) {
            assertEquals("$id: its summary card", first, course.units.getValue(id).grammar.first())
        }
    }
}
