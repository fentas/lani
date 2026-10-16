package si.lanisce.lani.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.AgeStep
import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.FestivalDay
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Teaser
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.Localized
import java.time.LocalDate

/** What the village's HUD shows and when (companion/GAME.md, "The village's HUD"): [VillageHudLogic]. */
class VillageHudLogicTest {
    private val s = GameState(age = Age.ZASELEK, fire = 80)
    private fun step(have: Int, need: Int) = AgeStep(AgeStep.Kind.RESOURCE, "🌾", "Hrana · Food", have, need, res = Res.FOOD)
    /** On the way to Vas: 3 of 5 steps done. */
    private val way = AdvanceCheck(Age.VAS, ok = false, missing = listOf("+132 🌾", "+303 🪵"), steps = List(3) { step(5, 5) } + List(2) { step(1, 5) })
    private val friday = LocalDate.of(2026, 10, 16)
    private fun said(sl: String, en: String) = Localized { if (it == Lang.SL) sl else en }

    @Test fun `the cards show by default, the ring hides them, a plot chooser hides them until the ring is tapped`() {
        assertTrue(VillageHudLogic.cards(shown = true, choosing = false))
        assertFalse(VillageHudLogic.cards(shown = false, choosing = false))
        assertFalse(VillageHudLogic.cards(shown = true, choosing = true))
        assertTrue("the ring tapped while choosing", VillageHudLogic.cards(shown = false, choosing = true, peek = true))
        assertFalse("no chooser, no peek", VillageHudLogic.cards(shown = false, choosing = false, peek = true))
    }

    @Test fun `the ring shows the next age and its steps done`() {
        val st = VillageHudLogic.status(s, way, festival = null)
        assertEquals(Age.VAS.emoji, st.emoji)
        assertEquals("3/5", st.text)
        assertEquals(HudStatus.Tone.PLAIN, st.tone)
        assertTrue(st.description, st.description.startsWith("Doba · Age: ${Age.ZASELEK.sl} · ${Age.ZASELEK.en}"))
        assertTrue("the steps still to go: ${st.description}", st.description.endsWith("še 2 koraka · 2 steps to go"))
        assertEquals(3 to 5, VillageHudLogic.steps(way))
    }

    @Test fun `ready to advance it glows, at the last age it names the age`() {
        val ready = VillageHudLogic.status(s, way.copy(ok = true, steps = List(5) { step(5, 5) }), festival = null)
        assertEquals(HudStatus.Tone.READY, ready.tone)
        assertEquals("✨", ready.text)
        val last = VillageHudLogic.status(s.copy(age = Age.MESTO), AdvanceCheck(null, ok = false, missing = emptyList()), festival = null)
        assertEquals(Age.MESTO.emoji, last.emoji)
        assertEquals(Age.MESTO.sl, last.text)
        assertNull(VillageHudLogic.steps(AdvanceCheck(null, ok = false, missing = emptyList())))
    }

    @Test fun `a festival that is on shows itself, gold, a quiet day plain, and a kept one gives the age back`() {
        val m = Calendar.byId("martinovo")!!
        val day = LocalDate.parse("2026-11-11")
        val feast = VillageHudLogic.status(s, way, FestivalDay(m, day, 0, done = false))
        assertEquals(m.emoji, feast.emoji)
        assertEquals("Danes", feast.text)
        assertEquals(HudStatus.Tone.FESTIVAL, feast.tone)
        assertEquals("Danes: Martinovo 🍷 · Today: St Martin's Day", feast.description)
        assertEquals(HudStatus.Tone.QUIET, VillageHudLogic.status(s, way, FestivalDay(m.copy(quiet = true), day, 0, done = false)).tone)
        assertEquals("3/5", VillageHudLogic.status(s, way, FestivalDay(m, day, 0, done = true)).text)
    }

    @Test fun `today's lines first, then what's coming in its order, each without its day`() {
        val m = Calendar.byId("martinovo")!!
        val village = VillageToday(
            festival = FestivalDay(m, friday, 0, done = false),
            teasers = listOf(
                Teaser(Teaser.Kind.PEDLAR, "🎒", "Krošnjar pride v torek · The pedlar comes on Tuesday", day = 4, what = said("Krošnjar pride", "The pedlar comes")),
                Teaser(Teaser.Kind.STORY, "⛰️", "Nocoj ob ognju: … · Tonight by the fire: …", day = 0, evening = true, what = said("Janez pove zgodbo", "Janez tells a story")),
            ),
        )
        val arrival = TodayItem.Arrival("ana", "🧳", "Ana se je preselila · Ana moved in", TownPlace.Fire)
        val lines = VillageHudLogic.soon(village, listOf(arrival), friday)
        assertEquals(listOf("festival:martinovo", "arrival:ana", "teaser:STORY", "teaser:PEDLAR"), lines.map { it.key.split(':').take(2).joinToString(":") })
        assertEquals("today's and tonight's story are news, the pedlar's day isn't", listOf(true, true, true, false), lines.map { it.news })
        assertEquals(listOf(SoonAction.Festival("martinovo"), SoonAction.Arrival("ana"), null, null), lines.map { it.action })
        assertEquals("Krošnjar pride", lines[3].what.of(Lang.SL))
        assertEquals("The pedlar comes", lines[3].what.of(Lang.EN))
        assertEquals("Ana se je preselila", lines[1].what.of(L10n.pair.target))
        assertEquals("Ana moved in", lines[1].what.of(L10n.pair.base))
        assertTrue("a feast in gold", lines[0].gold)
    }

    @Test fun `when a line comes, in both languages`() {
        assertEquals("Danes" to "Today", VillageHudLogic.whenOf(0, false, friday))
        assertEquals("Nocoj" to "Tonight", VillageHudLogic.whenOf(0, true, friday))
        assertEquals("Jutri" to "Tomorrow", VillageHudLogic.whenOf(1, false, friday))
        assertEquals("Torek" to "Tuesday", VillageHudLogic.whenOf(4, false, friday))
        assertEquals("Čez 9 dni" to "In 9 days", VillageHudLogic.whenOf(9, false, friday))
    }

    @Test fun `a two-language label splits at its middle separator`() {
        assertEquals("Les" to "Wood", VillageHudLogic.halves("Les · Wood"))
        assertEquals("Ogenj · fire" to "Fire · fire", VillageHudLogic.halves("Ogenj · fire · Fire · fire"))
        assertEquals("+60 🪵" to null, VillageHudLogic.halves("+60 🪵"))
    }

    @Test fun `each open step has its action, a done one none`() {
        assertEquals("🪵 Nabiraj" to "Gather", VillageHudLogic.actionOf(AgeStep(AgeStep.Kind.RESOURCE, "🪵", "Les · Wood", 200, 260, res = Res.WOOD)))
        assertEquals("🔩 Očisti" to "Polish", VillageHudLogic.actionOf(AgeStep(AgeStep.Kind.RUSTY, "🔩", "4 zarjavele besede · 4 rusty words", 236, 240)))
        assertEquals("📚 Uči se" to "Learn", VillageHudLogic.actionOf(AgeStep(AgeStep.Kind.WORDS, "📚", "Besede · Words", 252, 300)))
        assertNull(VillageHudLogic.actionOf(AgeStep(AgeStep.Kind.RESOURCE, "🌾", "Hrana · Food", 600, 300, res = Res.FOOD)))
    }

    @Test fun `a dot only for something new, gone once its sheet showed it`() {
        val goal = NextGoal.Grow(Age.VAS, way.missing, Res.FOOD)
        val today = SoonLine("arrival:ana", 0, false, "🧳", said("Ana", "Ana"), news = true)
        val later = SoonLine("teaser:PEDLAR", 4, false, "🎒", said("Krošnjar pride", "The pedlar comes"))
        fun view(stepsSeen: Set<String> = emptySet(), soonSeen: Set<String> = emptySet(), chestNew: Boolean = false) =
            HudView(s, GameEngine.attributes(s), way, goal, null, chestNew, null, listOf(today, later), soonSeen, stepsSeen)
        assertEquals(HudDots(goal = true, soon = true, settlement = false), view().dots)
        val keys = VillageHudLogic.stepKeys(goal, way).toSet()
        assertEquals("one key for the two open food steps", setOf("VAS:RESOURCE:FOOD"), keys)
        assertEquals(HudDots(goal = false, soon = false, settlement = true), view(keys, setOf("arrival:ana"), chestNew = true).dots)
        assertFalse("the pedlar's day is never news", view(keys, setOf("arrival:ana")).dots.any)
    }

    @Test fun `TalkBack reads the status, then which cards have something new`() {
        val st = VillageHudLogic.status(s, way, null)
        assertEquals(st.description, VillageHudLogic.description(st, HudDots(false, false, false)))
        assertEquals(
            "${st.description}; novo · new: Naslednji cilj · Next goal, Skrinja · The chest",
            VillageHudLogic.description(st, HudDots(goal = true, soon = false, settlement = true)),
        )
    }
}
