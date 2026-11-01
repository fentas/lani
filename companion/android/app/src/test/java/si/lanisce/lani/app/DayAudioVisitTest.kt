package si.lanisce.lani.app

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.VillagerLines
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.greeting
import java.time.LocalDateTime

/**
 * The day's audio is the learner's own village's, in its language, also while a visit shows another town (app/DayAudioGather
 * `pair`, ui/greeting `lang`). A learner of Italian (Friuli, German base) visiting a Slovene town: the labels' pair is the
 * town's then (L10n.visit), and the plan asked the node for Nonna Rosa's lines in their Slovene translation and for
 * "Dobro jutro", which the node voiced with language_code it (docs/plans/05-built/visit-voice-lang.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DayAudioVisitTest {
    private val own = L10n.ownPair
    private val visit = L10n.visit

    @After fun back() {
        L10n.visit = visit
        L10n.pair = own
    }

    private val rosa = Villager(
        id = "rosa", name = "Nonna Rosa", art = "grandma",
        lines = VillagerLines(
            greet = listOf(VillagerLine(linkedMapOf("it" to "Vieni, vieni. Siediti.", "sl" to "Pridi, pridi. Sedi.", "de" to "Komm, komm. Setz dich."))),
            idle = listOf(VillagerLine(linkedMapOf("it" to "Che bella giornata.", "sl" to "Kako lep dan.", "de" to "Was für ein schöner Tag."))),
        ),
    )

    private fun gather(pair: LangPair) = DayAudioGather(
        ApplicationProvider.getApplicationContext(), cards = emptyList(), packs = emptyList(), pack = { null },
        state = null, scenes = emptyList(), cast = listOf(rosa), profiles = emptyMap(), level = { "A1" },
        grammar = emptyList(), mastery = { null }, ispy = { null },
        greetings = listOf(6, 12, 20).map { greeting(it, pair.target) }, target = pair.target.code, base = pair.base.code, pair = pair,
    )

    @Test fun `on a visit to a Slovene town the day's lines are still the own village's Italian ones`() = runBlocking {
        val teo = LangPair(Lang.IT, Lang.DE)
        L10n.pair = teo
        L10n.visit = LangPair(Lang.SL, Lang.DE) // the labels as the Slovene town shows them
        val texts = gather(L10n.ownPair).plan(LocalDateTime.of(2026, 11, 1, 7, 0)).days.flatMap { d -> d.wants.map { it.text } }
        assertTrue(texts.toString(), "Vieni, vieni. Siediti." in texts && "Che bella giornata." in texts)
        assertFalse(texts.toString(), texts.any { it in setOf("Pridi, pridi. Sedi.", "Kako lep dan.", "Dobro jutro", "Dober dan", "Dober večer") })
        assertEquals("the greetings in Italian", L10n.text(Lang.IT, "components.goodMorning"), greeting(6, L10n.ownPair.target))
        assertTrue(L10n.text(Lang.IT, "components.goodMorning") in texts)
    }

    @Test fun `a greeting is in the language asked, by default the labels' own`() {
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        L10n.visit = LangPair(Lang.SL, Lang.DE)
        assertEquals(L10n.text(Lang.SL, "components.goodDay"), greeting(12))
        assertEquals(L10n.text(Lang.IT, "components.goodDay"), greeting(12, Lang.IT))
    }
}
