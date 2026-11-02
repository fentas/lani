package si.lanisce.lani.ui.school

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * The school's buttons that name the next step (ui/school/SchoolScreen.kt): "★ Naslednja enota: …" after a test, "▶️
 * Naprej: …" on a unit. Each half of the paired label names the step in its own language; the title shows once per
 * half, not the pair in both ("Naslednja enota: Hrana · Food · Next unit: Hrana · Food").
 */
class SchoolStepLabelsTest {
    private val food = mapOf("sl" to "Hrana", "en" to "Food", "de" to "Essen", "it" to "Cibo")

    @Test fun `the next unit's title once in each half, in that half's language`() {
        assertEquals("Naslednja enota: Hrana · Next unit: Food", nextUnitLabel(food, LangPair(Lang.SL, Lang.EN)))
        assertEquals("Prossima unità: Cibo · Nächste Einheit: Essen", nextUnitLabel(food, LangPair(Lang.IT, Lang.DE)))
        assertEquals("Nächste Einheit: Essen · Next unit: Food", nextUnitLabel(food, LangPair(Lang.DE, Lang.EN)))
        assertEquals("Next unit: Food · Naslednja enota: Hrana", nextUnitLabel(food, LangPair(Lang.EN, Lang.SL)))
    }

    @Test fun `a title without the base's language reads in English there`() {
        assertEquals("Prossima unità: Cibo · Nächste Einheit: Food", nextUnitLabel(mapOf("it" to "Cibo", "en" to "Food"), LangPair(Lang.IT, Lang.DE)))
    }

    @Test fun `a unit's next worksheet, its intro and its test, named once in each half`() {
        val sheet = mapOf("sl" to "Tabela: biti", "en" to "Table: to be")
        assertEquals("Naprej: Tabela: biti · Next: Table: to be", continueLabel(sheet, LangPair(Lang.SL, Lang.EN)))
        assertEquals("Naprej: Uvod · Next: Introduction", continueKeyLabel("school.intro", LangPair(Lang.SL, Lang.EN)))
        assertEquals("Avanti: Introduzione · Weiter: Einführung", continueKeyLabel("school.intro", LangPair(Lang.IT, Lang.DE)))
        assertEquals("Naprej: Test · Next: Test", continueKeyLabel("school.test", LangPair(Lang.SL, Lang.EN)))
    }
}
