package si.lanisce.lani.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.AgeStep
import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.FestivalDay
import si.lanisce.lani.game.GameState
import java.time.LocalDate

/** What the village's HUD shows and when (companion/GAME.md, "The village's HUD"): [VillageHudLogic]. */
class VillageHudLogicTest {
    private val s = GameState(age = Age.ZASELEK, fire = 80)
    private fun step(have: Int, need: Int) = AgeStep(AgeStep.Kind.RESOURCE, "🌾", "Hrana · Food", have, need)
    /** On the way to Vas: 3 of 5 steps done. */
    private val way = AdvanceCheck(Age.VAS, ok = false, missing = listOf("+132 🌾", "+303 🪵"), steps = List(3) { step(5, 5) } + List(2) { step(1, 5) })

    @Test fun `the row by default, the panel when opened, the pill when the learner minimized it`() {
        assertEquals(HudMode.COLLAPSED, VillageHudLogic.mode(minimized = false, expanded = false, choosing = false))
        assertEquals(HudMode.EXPANDED, VillageHudLogic.mode(minimized = false, expanded = true, choosing = false))
        assertEquals(HudMode.MINIMIZED, VillageHudLogic.mode(minimized = true, expanded = false, choosing = false))
    }

    @Test fun `the plot chooser minimizes it by itself, until its pill is tapped`() {
        assertEquals(HudMode.MINIMIZED, VillageHudLogic.mode(minimized = false, expanded = false, choosing = true))
        assertEquals("an open panel too", HudMode.MINIMIZED, VillageHudLogic.mode(minimized = false, expanded = true, choosing = true))
        assertEquals(HudMode.COLLAPSED, VillageHudLogic.mode(minimized = false, expanded = false, choosing = true, peek = true))
        assertEquals("the learner's own choice gives way to the pill tapped", HudMode.COLLAPSED, VillageHudLogic.mode(minimized = true, expanded = false, choosing = true, peek = true))
        assertEquals("no chooser, no peek", HudMode.MINIMIZED, VillageHudLogic.mode(minimized = true, expanded = false, choosing = false, peek = true))
    }

    @Test fun `the status chip shows the next age and its steps done`() {
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
        assertEquals(null, VillageHudLogic.steps(AdvanceCheck(null, ok = false, missing = emptyList())))
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

    @Test fun `the badges are the fire dying, the chest's news, a project step today and news not seen yet`() {
        assertEquals(emptyList<HudBadge>(), VillageHudLogic.badges(s, chestNew = false, stepToday = false, news = listOf("a"), seen = setOf("a")))
        val all = VillageHudLogic.badges(s.copy(fire = 20), chestNew = true, stepToday = true, news = listOf("a", "b"), seen = setOf("a"))
        assertEquals(listOf(HudBadge.Kind.FIRE, HudBadge.Kind.CHEST, HudBadge.Kind.PROJECT, HudBadge.Kind.NEWS), all.map { it.kind })
        assertEquals("Ogenj · Fire: 20, ugaša · dying", all[0].description)
        assertEquals("Skrinja · The chest: nekaj novega · something new", all[1].description)
        assertFalse("the fire at 25 isn't dying", VillageHudLogic.badges(s.copy(fire = 25), false, false, emptyList(), emptySet()).any { it.kind == HudBadge.Kind.FIRE })
        assertFalse("no news, nothing unseen", VillageHudLogic.unseen(emptyList(), emptySet()))
    }

    @Test fun `TalkBack reads the status, then each badge`() {
        val st = VillageHudLogic.status(s, way, null)
        val badges = VillageHudLogic.badges(s, chestNew = true, stepToday = false, news = emptyList(), seen = emptySet())
        assertEquals("${st.description}; Skrinja · The chest: nekaj novega · something new", VillageHudLogic.description(st, badges))
        assertEquals(st.description, VillageHudLogic.description(st, emptyList()))
    }
}
