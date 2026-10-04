package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.Placement.NEIGHBOUR
import si.lanisce.lani.game.Placement.distance

/**
 * The learner chooses the plot (GAME.md "Buildings", "The learner chooses the plot"): the free plots to choose from, the
 * ★ the game suggests (a field by the fields, a hut by the huts …), building on the plot chosen or the suggested one,
 * going back with nothing spent, moving a building (free) and two swapping places.
 */
class PlotChoiceTest {
    private val t0 = noon(day0)
    private val rich = Res.entries.associateWith { 5000 }

    /** A village of [age] with [plots] built (type → plot), rich enough to build anything. */
    private fun village(age: Age, vararg plots: Pair<BuildingType, Int>) = GameState(
        seed = 1,
        age = age,
        resources = rich,
        buildings = plots.mapIndexed { i, (t, p) -> Building("b$i", t, p, level = 1 + i % 2, damaged = i == 1, builtAt = 1L + i) },
        lastTick = day0.toString(),
        foundedOn = day0.toString(),
    )

    private fun plotsOf(s: GameState, t: BuildingType) = s.buildings.filter { it.type == t }.map { it.plot }

    // --- the plots to choose from ---------------------------------------------------------------------------------

    @Test fun `a new building chooses among the free plots of the age`() {
        val s = village(Age.ZASELEK, TENT to 0, FIELD to 1, HUT to 3, PALISADE to NO_PLOT)
        val c = PlotChoice.build(s, FIELD)
        assertEquals(listOf(2, 4, 5, 6, 7, 8), c.free(s))
        assertEquals(emptyList<Int>(), c.swaps(s))
        assertTrue(c.open(s))
        // a taken plot, one of the next age and none change nothing
        for (p in listOf(0, 1, 3, 9, 27, -1, null)) assertEquals("$p", c, c.pick(s, p))
        // a free one is chosen
        assertEquals(5, c.pick(s, 5).chosen)
        // the tent at the pond takes none: its plot is free
        val pond = village(Age.ZASELEK, TENT to NO_PLOT, FIELD to 1)
        assertTrue(0 in PlotChoice.build(pond, HUT).free(pond))
    }

    @Test fun `the choice starts at the suggestion, beside what it goes with`() {
        val s = village(Age.VAS, TENT to 0, FIELD to 1, TENT to 2, HUT to 3, WELL to 4, KOZOLEC to 8)
        for (t in listOf(FIELD, KOZOLEC, HUT, LIPA, SMITHY)) {
            val c = PlotChoice.build(s, t)
            assertEquals("$t", Placement.plotFor(s, t), c.chosen)
            assertEquals("$t", c.chosen, c.suggested(s))
        }
        // a building without anything to go with: the next free plot, as before
        assertEquals(Placement.freePlots(s).first(), PlotChoice.build(s, SMITHY).chosen)
    }

    // --- more of the same, side by side ---------------------------------------------------------------------------

    @Test fun `a new field goes beside the fields, before a kozolec`() {
        // the kozolec by the field on plot 1; the free plot nearest the kozolec isn't beside the field
        val s = village(Age.VAS, TENT to 0, FIELD to 1, TENT to 2, HUT to 3, WELL to 4, KOZOLEC to 8)
        val p = PlotChoice.build(s, FIELD).chosen!!
        assertTrue("field on $p beside the field on 1", distance(p, 1) <= NEIGHBOUR)
        // three fields, one after the other, each on the ★: all side by side
        var v = s
        repeat(3) { v = PlotChoice.build(v, FIELD).confirm(v, t0)!! }
        val fields = plotsOf(v, FIELD)
        assertEquals(4, fields.size)
        for (f in fields) assertTrue("field $f beside another of $fields", Placement.beside(f, fields - f))
    }

    @Test fun `huts, houses and bee houses go beside their own kind`() {
        val s = village(Age.TRG, TENT to 0, FIELD to 1, TENT to 2, WELL to 4, HUT to 9, HOUSE to 12, BEEHIVE to 15)
        for ((t, own) in listOf(HUT to 9, HOUSE to 12, BEEHIVE to 15)) {
            val p = PlotChoice.build(s, t).chosen!!
            val nearest = Placement.freePlots(s).minWith(compareBy<Int> { distance(it, own) }.thenBy { it })
            assertEquals("$t", nearest, p)
            assertTrue("$t on $p beside its own on $own", distance(p, own) <= NEIGHBOUR)
        }
        // without one of its own: the next free plot, as before
        val none = village(Age.TRG, TENT to 0, FIELD to 1)
        for (t in listOf(HUT, HOUSE, BEEHIVE)) assertEquals("$t", Placement.freePlots(none).first(), PlotChoice.build(none, t).chosen)
    }

    // --- confirm, the suggestion, back ----------------------------------------------------------------------------

    @Test fun `confirmed, it is built on the plot chosen and paid for then`() {
        val s = village(Age.ZASELEK, TENT to 0, FIELD to 1, HUT to 3)
        val c = PlotChoice.build(s, FIELD).pick(s, 7)
        assertNotEquals("7 isn't the suggestion", c.suggested(s), 7)
        val after = c.confirm(s, t0)!!
        val field = after.buildings.last()
        assertEquals(FIELD, field.type)
        assertEquals(7, field.plot)
        assertEquals(t0, field.builtAt)
        for ((r, n) in Catalog.spec(FIELD).cost) assertEquals("$r", s.res(r) - n, after.res(r))
        assertTrue(after.log.last().emoji == "🔨")
        // the one-time kozolec move of an older save is done: the game moves nothing the learner placed
        assertTrue(Placement.HAYRACKS_BY_FIELDS in after.migrated)
    }

    @Test fun `the suggestion builds on the star`() {
        val s = village(Age.ZASELEK, TENT to 0, FIELD to 1, HUT to 3)
        val c = PlotChoice.build(s, FIELD).pick(s, 7)
        val after = c.confirm(s, t0, plot = c.suggested(s))!!
        assertEquals(c.suggested(s), after.buildings.last().plot)
        // as the engine's own placement would have
        assertEquals(GameEngine.build(s, FIELD, t0).buildings.last().plot, after.buildings.last().plot)
    }

    @Test fun `nothing is spent while choosing, and going back leaves the village as it was`() {
        val s = village(Age.ZASELEK, TENT to 0, FIELD to 1, HUT to 3)
        val c = PlotChoice.build(s, FIELD).pick(s, 6)
        val shown = c.preview(s)
        // the map shows it there, unpaid; the village itself is untouched
        assertEquals(Building(PlotChoice.PREVIEW, FIELD, 6), shown.buildings.last())
        assertEquals(s.resources, shown.resources)
        assertEquals(s.log, shown.log)
        assertEquals(3, s.buildings.size)
        assertEquals(c.marks(s).shown, PlotChoice.PREVIEW)
        // nothing chosen: nothing shown
        assertSame(s, c.copy(chosen = null).preview(s))
        assertNull(c.copy(chosen = null).confirm(s, t0))
    }

    @Test fun `a plot taken meanwhile, or the resources gone, is not built on`() {
        val s = village(Age.ZASELEK, TENT to 0, FIELD to 1, HUT to 3)
        val c = PlotChoice.build(s, FIELD).pick(s, 6)
        val taken = GameEngine.build(s, HUT, t0, plot = 6)
        assertEquals(6, taken.buildings.last().plot)
        assertNull(c.confirm(taken, t0))
        assertNull(c.confirm(s.copy(resources = emptyMap()), t0))
        // the engine refuses a taken plot, one beyond the age, and a plot for the palisade
        assertSame(s, GameEngine.build(s, FIELD, t0, plot = 3))
        assertSame(s, GameEngine.build(s, FIELD, t0, plot = 9))
        assertSame(s, GameEngine.build(s, PALISADE, t0, plot = 6))
        // the palisade still goes up on none
        assertEquals(NO_PLOT, GameEngine.build(s, PALISADE, t0).buildings.last().plot)
    }

    @Test fun `with the neighbours' help it is paid as a moba`() {
        val s = village(Age.ZASELEK, TENT to 0, FIELD to 1, HUT to 3).copy(help = 50)
        val c = PlotChoice.build(s, FIELD, withMoba = true).pick(s, 7)
        val after = c.confirm(s, t0)!!
        assertEquals(7, after.buildings.last().plot)
        assertTrue("🤝 spent", after.help < s.help)
    }

    // --- moving ---------------------------------------------------------------------------------------------------

    @Test fun `a building moves to a free plot, free of charge, only its plot changing`() {
        val s = village(Age.ZASELEK, TENT to 0, FIELD to 1, HUT to 3, KOZOLEC to 5)
        val hut = s.buildings.single { it.type == HUT }
        val c = PlotChoice.move(s, hut).pick(s, 7)
        assertEquals(listOf(2, 4, 6, 7, 8), c.free(s))
        assertEquals(listOf(0, 1, 5), c.swaps(s))
        val after = c.confirm(s, t0)!!
        assertEquals(hut.copy(plot = 7), after.buildings.single { it.id == hut.id })
        assertEquals(s.buildings.map { it.id }, after.buildings.map { it.id })
        assertEquals(s.buildings.filter { it.id != hut.id }, after.buildings.filter { it.id != hut.id })
        assertEquals("free", s.resources, after.resources)
        assertEquals("↔️", after.log.last().emoji)
        assertTrue(Placement.HAYRACKS_BY_FIELDS in after.migrated)
        // the same as the engine's move, and the map showed it there before
        assertEquals(after, GameEngine.move(s, hut.id, 7, t0))
        assertEquals(after.buildings, c.preview(s).buildings)
        assertEquals(hut.id, c.marks(s).shown)
    }

    @Test fun `two buildings swap places`() {
        val s = village(Age.ZASELEK, TENT to 0, FIELD to 1, HUT to 3, KOZOLEC to 5)
        val hut = s.buildings.single { it.type == HUT }
        val field = s.buildings.single { it.type == FIELD }
        val c = PlotChoice.move(s, hut).pick(s, 1)
        assertEquals(field, c.swapWith(s))
        val after = c.confirm(s, t0)!!
        assertEquals(1, after.buildings.single { it.id == hut.id }.plot)
        assertEquals(3, after.buildings.single { it.id == field.id }.plot)
        assertEquals(field.copy(plot = 3), after.buildings.single { it.id == field.id })
        assertEquals(s.resources, after.resources)
        assertEquals(c.preview(s).buildings, after.buildings)
        assertEquals(mapOf("b0" to 0, "b1" to 1, "b3" to 5), c.marks(s).standing)
    }

    @Test fun `moving suggests a plot beside what it goes with, if it isn't there already`() {
        // a field apart from the others: the ★ is beside them
        val s = village(Age.VAS, TENT to 0, FIELD to 1, TENT to 2, HUT to 3, FIELD to 12)
        val apart = s.buildings.single { it.plot == 12 }
        val star = PlotChoice.move(s, apart).chosen!!
        assertTrue("$star beside the field on 1", distance(star, 1) <= NEIGHBOUR)
        // one already beside its kind stays: no ★
        val beside = village(Age.VAS, TENT to 0, FIELD to 1, TENT to 2, HUT to 3, FIELD to Placement.plotFor(village(Age.VAS, TENT to 0, FIELD to 1, TENT to 2, HUT to 3), FIELD)!!)
        assertNull(PlotChoice.move(beside, beside.buildings.last()).chosen)
        // one that goes with nothing: no ★ either, the learner chooses
        val smithy = village(Age.VAS, TENT to 0, SMITHY to 6)
        assertNull(PlotChoice.move(smithy, smithy.buildings.last()).suggested(smithy))
    }

    @Test fun `the palisade and the tent at the pond don't move`() {
        val s = village(Age.ZASELEK, TENT to NO_PLOT, FIELD to 1, PALISADE to NO_PLOT)
        for (b in s.buildings.filter { it.plot == NO_PLOT }) {
            val c = PlotChoice.move(s, b).copy(chosen = 4)
            assertNull(c.building(s))
            assertEquals(emptyList<Int>(), c.swaps(s))
            assertNull(c.confirm(s, t0))
            assertSame(s, GameEngine.move(s, b.id, 4, t0))
        }
        // nor onto its own plot, or one beyond the age
        assertSame(s, GameEngine.move(s, "b1", 1, t0))
        assertSame(s, GameEngine.move(s, "b1", 9, t0))
        assertSame(s, GameEngine.move(s, "nobody", 4, t0))
    }

    @Test fun `what the learner moved stays where they put it`() {
        // an older save whose kozolec (plot 5) stands apart from the field (1) and waits for a free plot beside it
        val full = village(Age.ZASELEK, TENT to 0, FIELD to 1, TENT to 2, HUT to 3, KOZOLEC to 5, LIPA to 4, HUT to 6, CHURCH to 7, SMITHY to 8)
        assertFalse(Placement.HAYRACKS_BY_FIELDS in Placement.hayracksByFields(full).migrated)
        // the learner swaps the kozolec with the church: from then on the game leaves it where it is, the next age too
        val moved = GameEngine.move(full, "b4", 7, t0)
        val vas = GameEngine.tick(moved.copy(age = Age.VAS), day0.plusDays(1), t0 + 86_400_000, Fixtures.pool()).state
        assertEquals(7, vas.buildings.single { it.type == KOZOLEC }.plot)
        assertEquals(5, vas.buildings.single { it.type == CHURCH }.plot)
    }

    @Test fun `a moved building keeps working wherever it stands`() {
        val s = village(Age.ZASELEK, TENT to 0, FIELD to 1, HUT to 3, KOZOLEC to 5)
        val after = GameEngine.move(s, "b2", 8, t0)
        assertEquals(GameEngine.attributes(s), GameEngine.attributes(after))
        assertEquals(s.plotsTaken, after.plotsTaken)
        assertEquals(Placement.freePlots(s).size, Placement.freePlots(after).size)
        assertNotNull(after.buildings.single { it.plot == 8 })
    }
}
