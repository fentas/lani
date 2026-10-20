package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.BuildingType.*
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.CycleKind
import si.lanisce.lani.game.scene.SpotCycles
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDate
import java.time.ZoneId

/**
 * The forge on Oglar Miha's charcoal and the village's wood for his pile (companion/GAME.md "Hearths"; Primorska's world.json
 * `spots.kopa.cycle`, its felling and stacking `wood: true`): a new smithy's sacks and each pile's charcoal last until the
 * next pile and a week, the pile takes its wood once on its wood days keeping two days of the hearths', Miha waits without
 * it and the pile isn't built when they pass, and the piles due before the village kept its hearths count as given.
 */
class KopaWoodTest {
    @Before fun primorska() {
        Cultures.use("primorska")
        L10n.pair = LangPair(Lang.SL, Lang.EN)
    }

    private val day = LocalDate.of(2026, 7, 1)

    /** A village of [age] with [types] on plots 0, 1, … (built a year back) and [wood] 🪵. */
    private fun village(age: Age, vararg types: BuildingType, wood: Int = 500) = GameState(
        seed = 7, age = age, resources = mapOf(Res.FOOD to 1000, Res.WOOD to wood),
        buildings = types.mapIndexed { i, t -> Building("${t.name.lowercase()}-$i", t, i, builtAt = millis(day.minusDays(400))) },
        lastTick = day.minusDays(1).toString(), foundedOn = day.minusDays(400).toString(), villagers = 6, morale = 70,
    )

    private fun millis(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** [s]'s tick on [d], no event coming (a village founded yesterday: none in its first days). */
    private fun tick(s: GameState, d: LocalDate = day) = GameEngine.tick(s.copy(foundedOn = d.minusDays(1).toString()), d, noon(d), Fixtures.pool()).state

    // --- the forge and the charcoal pile --------------------------------------------------------------------------------

    private val pile get() = Hearths.kopa()!!
    private fun rounds(year: Int) = SpotCycles.rounds(pile.second, pile.first, year)

    @Test fun `a new smithy's sacks last until Miha's next pile and a week, a pile given its wood until the next one`() {
        val r = rounds(2026)[1]
        val from = r.woodFrom!!
        // a smithy built a week before the pile's wood days (2026: June)
        val built = from.minusDays(7)
        val s = village(Age.VAS, HUT, HOUSE, wood = 1000).let { v ->
            v.copy(buildings = v.buildings + Building("smithy-9", SMITHY, 2, builtAt = millis(built)), lastTick = built.toString(), hearths = HearthDay(on = built.toString(), since = built.toString()))
        }
        val first = rounds(2026).firstOrNull { it.delivery!!.isAfter(built) }!!
        assertEquals(first.delivery!!.plusDays(Catalog.CHARCOAL_MARGIN.toLong()), Hearths.charcoalUntil(s, built))
        // the pile's wood days: the village gives it (and keeps two days of the hearths' wood), once
        var v = s
        var d = built
        while (!d.isAfter(r.woodUntil!!)) { d = d.plusDays(1); v = tick(v, d) }
        assertEquals(listOf(r.key), v.cycles["kopa"]!!.stacked)
        // its charcoal: from its delivery until the next pile's and a week
        val next = rounds(2026).first { it.n == r.n + 1 }
        assertEquals(next.delivery!!.plusDays(Catalog.CHARCOAL_MARGIN.toLong()), Hearths.charcoalUntil(v, r.delivery!!.plusDays(1)))
        // the season's last pile carries the forge through the winter, to the next spring's
        val last = rounds(2026).last()
        val spring = rounds(2027).first().delivery!!
        val all = v.copy(cycles = v.cycles + ("kopa" to v.cycles["kopa"]!!.copy(stacked = rounds(2026).map { it.key })))
        assertEquals(spring.plusDays(Catalog.CHARCOAL_MARGIN.toLong()), Hearths.charcoalUntil(all, last.delivery!!))
        assertTrue((Hearths.charcoal(all, LocalDate.of(2027, 2, 1)) ?: 0) > 100)
    }

    @Test fun `without the wood Miha waits, the days passed unpaid the pile isn't built and the forge goes cold`() {
        val r = rounds(2026)[1]
        val from = r.woodFrom!!
        val start = from.minusDays(1)
        // a smithy since last year, the village short of wood (the hearths' 2 days kept: the pile can't take its 120)
        var v = village(Age.VAS, HUT, SMITHY, wood = 50).copy(lastTick = start.toString(), hearths = HearthDay(on = start.toString(), since = rounds(2026)[0].woodUntil!!.plusDays(1).toString()))
        v = tick(v, from)
        // he waits: his day is the waiting one, the HUD's sheet shows the need
        val waiting = SpotCycles.day(v, "kopa", from)!!
        assertEquals(CycleKind.WAITING, waiting.kind)
        assertEquals("waiting", waiting.id)
        val need = Hearths.kopaWood(v, from)!!
        assertTrue("$need", need.waiting && !need.paid)
        assertEquals(Catalog.kopaWood(Age.VAS), need.amount)
        assertEquals("Oglar Miha", need.keeper)
        // the days pass unpaid: no pile, the ground bare, no delivery
        var d = from
        while (!d.isAfter(r.woodUntil!!.plusDays(1))) { d = d.plusDays(1); v = tick(v.copy(resources = v.resources + (Res.WOOD to 50)), d) }
        assertEquals(listOf(r.key), v.cycles["kopa"]!!.unbuilt)
        assertEquals(CycleKind.UNBUILT, SpotCycles.day(v, "kopa", r.woodUntil!!.plusDays(3))!!.kind)
        assertFalse(Hearths.delivers(v, "kopa", pile.second, r))
        assertEquals(emptyList<Pair<String, String>>(), SpotCycles.news(v.copy(residents = listOf(si.lanisce.lani.game.villagers.Resident("tone", "2026-01-01"))), r.delivery!!.minusDays(1), r.delivery!!))
        // the charcoal of the pile before lasts until this one's delivery and a week: then the forge is cold
        val cold = r.delivery!!.plusDays(Catalog.CHARCOAL_MARGIN + 1L)
        assertEquals(0, Hearths.charcoal(v, cold))
        val after = tick(v.copy(lastTick = cold.minusDays(1).toString()), cold)
        assertTrue("${after.hearths}", "smithy-1" in after.hearths!!.out)
        assertFalse("a cold forge isn't a want of wood", Hearths.short(after.copy(resources = after.resources + (Res.WOOD to 500), hearths = after.hearths!!.copy(out = listOf("smithy-1")))))
    }

    @Test fun `with the wood Miha takes it on the first of his days the store can spare it, and the chronicle says so`() {
        val r = rounds(2026)[1]
        val from = r.woodFrom!!
        val v = village(Age.VAS, HUT, SMITHY, wood = 1000).copy(lastTick = from.minusDays(1).toString(), hearths = HearthDay(since = "2026-01-01"))
        val res = GameEngine.tick(v.copy(foundedOn = from.toString()), from, noon(from), Fixtures.pool())
        assertEquals(listOf(r.key), res.state.cycles["kopa"]!!.stacked)
        // the hearths' day (the hut 1, the ring 1) and the pile's 120
        assertEquals(1000 - 2 - 120, res.state.res(Res.WOOD))
        assertTrue(res.news.any { it.text.startsWith("Oglar Miha je dobil iz vasi 120 🪵 za kopo") })
        assertEquals(CycleKind.STAGE, SpotCycles.day(res.state, "kopa", from)!!.kind)
        // the store keeps two days of the hearths' wood: with 120 + 3 it waits
        val tight = v.copy(resources = v.resources + (Res.WOOD to 120 + 2 + 3))
        assertTrue(GameEngine.tick(tight, from, noon(from), Fixtures.pool()).state.cycles["kopa"]?.stacked.isNullOrEmpty())
        // a village without a smithy doesn't give it: Miha burns his own wood
        val none = village(Age.VAS, HUT, wood = 1000).copy(lastTick = from.minusDays(1).toString(), hearths = HearthDay(since = "2026-01-01"))
        assertTrue(GameEngine.tick(none, from, noon(from), Fixtures.pool()).state.cycles["kopa"]?.stacked.isNullOrEmpty())
        assertNull(Hearths.kopaWood(none, from))
        assertEquals(CycleKind.STAGE, SpotCycles.day(none, "kopa", from)!!.kind)
    }

    @Test fun `the piles whose wood was due before the village kept its hearths count as given`() {
        // Jan's case: a save from before the hearths on 2026-10-20, the year's last pile stacked in September
        val today = LocalDate.of(2026, 10, 20)
        val last = rounds(2026).last()
        assertTrue(last.woodUntil!!.isBefore(today) && last.delivery!!.isAfter(today))
        val old = village(Age.VAS, HUT, HOUSE, SMITHY, wood = 300).copy(lastTick = today.toString(), fire = 100)
        val v = tick(old, today)
        assertTrue(Hearths.delivers(v, "kopa", pile.second, last))
        // the forge stays on charcoal through the winter, until a week after next spring's first pile
        assertEquals(rounds(2027).first().delivery!!.plusDays(Catalog.CHARCOAL_MARGIN.toLong()), Hearths.charcoalUntil(v, last.delivery!!))
        assertTrue("lit now", "smithy-2" in Hearths.lit(v, today))
    }
}
