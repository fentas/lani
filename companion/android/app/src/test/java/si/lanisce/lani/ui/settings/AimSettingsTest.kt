package si.lanisce.lani.ui.settings

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.AimApi
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.TargetNext
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * The settings' "🎯 Cilj · Aim" (companion/GAME.md "A target raised later"): the aim the village's step counts with, a
 * raise waiting for the next age, the chip marked, the section's line, what a choice did; and the node's answer read.
 */
class AimSettingsTest {
    private val was = L10n.pair

    @Before fun setUp() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = LangPair(Lang.SL, Lang.EN)
    }

    @After fun tearDown() {
        L10n.pair = was
    }

    private fun dashboard(learner: String) = Dashboard.parse("""{"databases": {"learner_profile": {"learner": $learner}}, "computed": {"today": "2026-10-31"}}""")

    private val teo = dashboard("""{"current_level": "A1", "target_level": "A2", "target_level_next": {"level": "B1", "from_age": "VAS", "set": "2026-10-31"}}""")

    @Test fun `a raise waiting at the hamlet is marked and said`() {
        val a = AimShown.of(teo, Age.ZASELEK)!!
        assertEquals("A2", a.target)
        assertEquals(TargetNext("B1", "VAS", "2026-10-31"), a.waiting)
        assertEquals("B1", a.selected)
        assertEquals(listOf("A2", "B1", "B2", "C1", "C2"), a.choices)
        assertEquals("Zdaj: A1 → A2 · Now: A1 → A2", a.now())
        assertEquals("A1 → A2 · 🎯 Od naslednje dobe: B1 · From the next age: B1", a.summary())
    }

    @Test fun `once the village is at its age the raise is the aim, nothing waits`() {
        // the bridge hasn't applied it yet (the save on its way): the app counts with it already
        val a = AimShown.of(teo, Age.VAS)!!
        assertEquals("B1", a.target)
        assertNull(a.waiting)
        assertEquals("B1", a.selected)
        assertEquals("A1 → B1", a.summary())
        // … and the same once the bridge has
        assertEquals(a, AimShown.of(dashboard("""{"current_level": "A1", "target_level": "B1"}"""), Age.VAS))
    }

    @Test fun `no learner's data yet, nothing to show`() {
        assertNull(AimShown.of(null, Age.OGENJ))
    }

    @Test fun `what a choice did, for the banner`() {
        assertEquals("🎯 Cilj B1 velja od naslednje dobe · Aim B1 from the next age", AimShown.banner(AimApi.Aim("A1", "A2", "B1", "VAS", "next_age")))
        assertEquals("🎯 Cilj je zdaj A2 · Your aim is A2 now", AimShown.banner(AimApi.Aim("A1", "A2", change = "now")))
        assertEquals("🎯 Cilj je zdaj B2 · Your aim is B2 now", AimShown.banner(AimApi.Aim("A1", "B2", change = "unchanged")))
        // an older node: no change said, the target it keeps
        assertEquals("🎯 Cilj je zdaj B2 · Your aim is B2 now", AimShown.banner(AimApi.Aim("A1", "B2")))
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        assertEquals("🎯 Obiettivo B1 dalla prossima età · Ziel B1 ab dem nächsten Zeitalter", AimShown.banner(AimApi.Aim("A1", "A2", "B1", "VAS", "next_age")))
    }

    @Test fun `the node's answer, with a raise waiting and what changed, or as an older node says it`() {
        assertEquals(
            AimApi.Aim("A1", "A2", "B1", "VAS", "next_age"),
            AimApi.parse("""{"level": "A1", "target": "A2", "next": {"level": "B1", "from_age": "VAS"}, "change": "next_age"}"""),
        )
        assertEquals(AimApi.Aim("A1", "A2"), AimApi.parse("""{"level": "A1", "target": "A2", "next": null}"""))
        assertEquals(AimApi.Aim("A2", null), AimApi.parse("""{"level": "A2", "target": null}"""))
        assertNull(AimApi.parse("""{"error": "an aim above the learner's level (A2)"}"""))
    }
}
