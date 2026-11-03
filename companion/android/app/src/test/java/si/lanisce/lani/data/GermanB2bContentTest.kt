package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * German B2's middle six units (herr-kollege to nominalisierung: docs/plans/05-built/course-de-b2-b.md) as the app grades them. A weak
 * noun with no -e and without its ending is what people say (den Mensch, dem Bub, mit Herr Moser): almost, the standard form shown, and
 * only after den, dem and the like, so a plural stays wrong; a noun in -e has no such form (dem Kollege is wrong). Herren and Herrn are
 * one for the other in Duden's rarer forms. -heit and -keit are no slips, nor a Partizip I without its d or a Partizip II without its ge.
 * Spoken forms beside the point are right, labelled: was Neues, nix Gutes, drauf, wegen dem Regen. froh um and Interesse für are accepted.
 */
class GermanB2bContentTest {
    private fun de(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.DE)
    private fun gap(before: String, answer: String, right: String) = Grading.check(answer, listOf(right), Lang.DE, before)
    private fun assertPeopleSay(r: Grading.Result, right: String) {
        assertEquals("verdict of «$right»", Verdict.ALMOST, r.verdict)
        assertEquals("hint of «$right»", Hint.NONSTANDARD, r.hint)
        assertEquals(right, r.expected)
    }
    private fun sheet(answer: String, accept: List<String>, spoken: List<String> = emptyList()) =
        Sheets.check(SheetItem(accept, null, null, null, "", spoken = spoken), answer, Lang.DE)

    private val ids = listOf("herr-kollege", "der-bekannte", "partizip-adjektiv", "partizipialattribut", "stolz-auf", "nominalisierung")
    private val course = checkNotNull(Courses.bundled("de"))

    /** Every item of a unit's worksheets, a choice or a cloze counted as one. */
    private fun size(u: CourseUnit) = u.worksheets.flatMap { it.exercises }.sumOf { e ->
        val n = Sheets.items(e, Lang.DE).size
        if (n > 0) n else 1
    }

    @Test fun `a weak noun with no -e without its ending is almost after den and dem, the standard form shown`() {
        for ((before, typed, right) in listOf(
            Triple("Ich sehe den ", "Mensch", "Menschen"), Triple("Die Mutter ruft den ", "Bub", "Buben"), Triple("Der Arzt spricht mit dem ", "Student", "Studenten"),
            Triple("Kathi zeigt dem ", "Tourist", "Touristen"), Triple("Ich frage den ", "Polizist", "Polizisten"), Triple("Der Arzt spricht mit dem ", "Soldat", "Soldaten"),
            Triple("Von einem alten ", "Bauer", "Bauern"), Triple("Kathi hat einen neuen ", "Nachbar", "Nachbarn"), Triple("das Bild von einem ", "Bär", "Bären"), Triple("beim ", "Fotograf", "Fotografen"),
            Triple("Das Schaf gehört dem ", "Hirt", "Hirten"),
        )) assertPeopleSay(gap(before, typed, right), right)
        assertPeopleSay(de("Ich frage den Student.", "Ich frage den Studenten."), "Ich frage den Studenten.")
        assertPeopleSay(de("Stefan spricht mit dem Nachbar.", "Stefan spricht mit dem Nachbarn."), "Stefan spricht mit dem Nachbarn.")
    }

    @Test fun `without den or dem in front such a form stays wrong, in the plural and the nominative`() {
        assertEquals(Verdict.WRONG, gap("Viele ", "Mensch", "Menschen").verdict)
        assertEquals(Verdict.WRONG, gap("Wo sind die ", "Student", "Studenten").verdict)
        assertEquals(Verdict.WRONG, de("Die Tourist sind hier.", "Die Touristen sind hier.").verdict)
        assertEquals(Verdict.WRONG, gap("Auf dem Tisch des ", "Fotograf", "Fotografen").verdict)
        // another noun's rule doesn't count for this one
        assertEquals(Verdict.WRONG, gap("Ich sehe den ", "Mensch", "Studenten").verdict)
    }

    @Test fun `a noun in -e has no such form, and a noun of no group takes no -n`() {
        for ((before, typed, right) in listOf(
            Triple("Toni arbeitet mit dem ", "Kollege", "Kollegen"), Triple("Stanko gibt dem ", "Kunde", "Kunden"), Triple("Ich kenne den ", "Neffe", "Neffen"),
            Triple("Der Hund jagt den ", "Hase", "Hasen"), Triple("Das Schaf des ", "Hirte", "Hirten"), Triple("Stefan spricht mit dem ", "Schmieden", "Schmied"), Triple("Lena spricht mit dem ", "Arzten", "Arzt"),
        )) assertEquals("$typed for $right", Verdict.WRONG, gap(before, typed, right).verdict)
        assertEquals(Verdict.ALMOST, de("Kolegen", "Kollegen").verdict)
        assertEquals(Hint.TYPO, de("Kolegen", "Kollegen").hint)
    }

    @Test fun `Herrn and Herr and Herren are almost for each other as the dictionary and speech have them, the rest stays wrong`() {
        assertPeopleSay(de("Herr", "Herrn"), "Herrn")
        assertPeopleSay(de("Ich kenne Herr Moser.", "Ich kenne Herrn Moser."), "Ich kenne Herrn Moser.")
        assertPeopleSay(de("Herren", "Herrn"), "Herrn")
        assertPeopleSay(de("Herrn", "Herren"), "Herren")
        assertEquals(Verdict.WRONG, de("Herr", "Herren").verdict)
        assertEquals(Verdict.WRONG, de("Herrem", "Herrn").verdict)
    }

    @Test fun `the rarer forms of Bauer, Nachbar, Bub and Bär are almost, der Name's genitive is its ending`() {
        assertPeopleSay(de("Bauers", "Bauern"), "Bauern")
        assertPeopleSay(de("Nachbars", "Nachbarn"), "Nachbarn")
        assertPeopleSay(de("Bubs", "Buben"), "Buben")
        assertPeopleSay(de("Bärs", "Bären"), "Bären")
        for ((typed, right) in listOf("Namen" to "Namens", "Namens" to "Namen", "Name" to "Namen", "Herz" to "Herzen", "Herzen" to "Herzens", "Gedanke" to "Gedanken")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
    }

    @Test fun `an adjective used as a noun has its ending, what people say around it is right, labelled`() {
        for ((typed, right) in listOf("Bekannte" to "Bekannter", "Bekannter" to "Bekannte", "Bekannten" to "Bekannter", "Verwandte" to "Verwandten", "Gutes" to "Gute", "Gute" to "Gutes", "Neue" to "Neues")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        for ((said, standard) in listOf("Das ist was Neues." to "Das ist etwas Neues.", "Das ist nix Gutes." to "Das ist nichts Gutes.")) {
            val m = sheet(said, listOf(standard), listOf(said))
            assertEquals(said, Verdict.CORRECT to standard, m.verdict to m.standard)
        }
        val listed = course.units.getValue("der-bekannte").worksheets.flatMap { it.exercises }.flatMap { Sheets.items(it, Lang.DE) }.flatMap { it.spoken }
        assertTrue("listed on a sheet", "Das ist was Neues." in listed)
    }

    @Test fun `a participle without its d or its ge is no slip, its ending is the ending, a slip in the stem stays one`() {
        for ((typed, right) in listOf(
            "spielenen" to "spielenden", "lachenes" to "lachendes", "backene" to "gebackene", "presster" to "gepresster", "füllten" to "gefüllten",
            "spielend" to "spielende", "spielenden" to "spielende", "gebacken" to "gebackene", "gekochte" to "gekochtes", "kochendes" to "kochendem",
        )) assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        assertEquals(Hint.TYPO, de("gebakene", "gebackene").hint)
        assertEquals(Verdict.ALMOST, de("gebakene", "gebackene").verdict)
        assertEquals(Hint.UMLAUT, de("gefuellten", "gefüllten").hint)
    }

    @Test fun `a preposition is its own word, and froh um and Interesse für are right on the sheet`() {
        for ((typed, right) in listOf("an" to "auf", "aus" to "auf", "am" to "an", "von" to "vor", "vor" to "von", "für" to "vor", "mit" to "mir")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        val items = course.units.getValue("stolz-auf").worksheets.first().exercises.flatMap { Sheets.items(it, Lang.DE) }
        assertTrue("froh um", items.any { "über" in it.accept && "um" in it.accept })
        assertTrue("Interesse für", items.any { "an" in it.accept && "für" in it.accept })
        val m = sheet("Stefan ist stolz drauf, dass sein Most gut ist.", listOf("Stefan ist stolz darauf, dass sein Most gut ist."), listOf("Stefan ist stolz drauf, dass sein Most gut ist."))
        assertEquals(Verdict.CORRECT, m.verdict)
    }

    @Test fun `-heit and -keit are no slips, and wegen dem Regen is right, labelled`() {
        for ((typed, right) in listOf("Gesundkeit" to "Gesundheit", "Freundlichheit" to "Freundlichkeit", "Krankkeit" to "Krankheit", "Höflichheit" to "Höflichkeit", "Regen" to "Regens", "Bei" to "Beim", "Vom" to "Von")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        assertEquals(Hint.TYPO, de("Gesuntheit", "Gesundheit").hint)
        val line = course.units.getValue("nominalisierung").worksheets.last().exercises.flatMap { Sheets.items(it, Lang.DE) }.first()
        val m = Sheets.check(line, "Wegen dem Regen bleiben wir zu Hause.", Lang.DE)
        assertEquals(Verdict.CORRECT to "Wegen des Regens bleiben wir zu Hause.", m.verdict to m.standard)
        assertEquals(Verdict.CORRECT, Sheets.check(line, "Wegen Regen bleiben wir zu Hause.", Lang.DE).verdict)
    }

    @Test fun `the units are at the level's size, and the six new pages are in the book with their own cards`() {
        for (id in ids) {
            val u = course.units.getValue(id)
            assertEquals("$id: its level", "B2", u.level)
            assertEquals("$id: three sheets", 3, u.worksheets.size)
            assertTrue("$id: five steps or more", u.intro!!.steps.size >= 5)
            assertTrue("$id: a quick question a step", u.intro!!.steps.all { it.ask != null })
            assertTrue("$id: at most 35 items, it has ${size(u)}", size(u) in 15..35)
        }
        val book = Grammar.bundled("de").associateBy { it.id }
        for (p in listOf("n-deklination", "adjektiv-nomen", "partizip-adjektiv", "partizipialattribut", "adjektive-praepositionen", "nominalisierung")) {
            assertTrue("$p: its page", book.containsKey(p))
            assertEquals("$p: at B2", "B2", book.getValue(p).level)
        }
    }
}
