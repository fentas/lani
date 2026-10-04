package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.MARKET
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.Placement.NEIGHBOUR
import si.lanisce.lani.game.Placement.distance
import si.lanisce.lani.game.render.Terrain
import si.lanisce.lani.game.render.VillageLayout

class PlacementTest {
    private val t0 = noon(day0)
    private val rich = Res.entries.associateWith { 5000 }

    /** A village of [age] with [plots] built (type → plot), rich enough to build anything. */
    private fun village(age: Age, vararg plots: Pair<BuildingType, Int>) = GameState(
        seed = 1,
        age = age,
        resources = rich,
        buildings = plots.mapIndexed { i, (t, p) -> Building("b$i", t, p, builtAt = 1L + i) },
        lastTick = day0.toString(),
        foundedOn = day0.toString(),
    )

    private fun built(s: GameState, t: BuildingType): Building {
        val after = GameEngine.build(s, t, t0)
        assertEquals("$t was built", s.buildings.size + 1, after.buildings.size)
        return after.buildings.last()
    }

    /** What the rule says, worked out here from the plot centres: the free plot nearest one of [anchors], a neighbour of it. */
    private fun nearestFree(s: GameState, anchors: List<Int>): Int = Placement.freePlots(s)
        .map { p -> p to anchors.minOf { distance(p, it) } }
        .filter { it.second <= NEIGHBOUR }
        .minWith(compareBy<Pair<Int, Float>> { it.second }.thenBy { it.first }).first

    @Test fun `the neighbour distance tells the plots beside from the next but one`() {
        val cs = Terrain.CLASSIC.centers
        for (i in cs.indices) {
            val d = cs.indices.filter { it != i }.map { distance(i, it) }.sorted()
            assertTrue("plot $i has a neighbour", d.first() <= NEIGHBOUR)
            // no plot sits right on the line: the neighbours are clearly closer, the rest clearly farther
            assertTrue("plot $i: ${d.filter { it > NEIGHBOUR - 0.25f && it < NEIGHBOUR + 0.25f }}", d.none { it > NEIGHBOUR - 0.25f && it < NEIGHBOUR + 0.25f })
        }
    }

    @Test fun `a kozolec goes up beside the field, on the free plot nearest it`() {
        // Zaselek: the field stands on plot 4, away from the next free plot (3)
        val s = village(Age.ZASELEK, TENT to 0, TENT to 1, HUT to 2, FIELD to 4)
        val k = built(s, KOZOLEC)
        assertEquals(nearestFree(s, listOf(4)), k.plot)
        assertTrue(distance(k.plot, 4) <= NEIGHBOUR)
        assertNotEquals("today's rule would have taken the next free plot", Placement.freePlots(s).first(), k.plot)
        // the nearest of several fields
        val two = village(Age.VAS, TENT to 0, FIELD to 1, TENT to 2, HUT to 3, FIELD to 9)
        val k2 = built(two, KOZOLEC)
        assertEquals(nearestFree(two, listOf(1, 9)), k2.plot)
        assertTrue(listOf(1, 9).any { distance(k2.plot, it) <= NEIGHBOUR })
    }

    @Test fun `a field goes up beside a kozolec`() {
        val s = village(Age.ZASELEK, TENT to 0, TENT to 1, HUT to 2, KOZOLEC to 4)
        val f = built(s, FIELD)
        assertEquals(nearestFree(s, listOf(4)), f.plot)
        assertNotEquals(3, f.plot)
    }

    @Test fun `the square's buildings stand together`() {
        var s = village(Age.VAS, TENT to 0, FIELD to 1, TENT to 2, KOZOLEC to 3, HUT to 5)
        // the first of them: the next free plot (the nearest the fire), as before
        val well = built(s, WELL)
        assertEquals(4, well.plot)
        s = GameEngine.build(s, WELL, t0)
        val square = mutableListOf(well.plot)
        for (t in listOf(LIPA, CHURCH, MARKET)) {
            val b = built(s, t)
            assertEquals("$t", nearestFree(s, square), b.plot)
            assertTrue("$t beside the square", square.any { distance(b.plot, it) <= NEIGHBOUR })
            s = GameEngine.build(s, t, t0)
            square += b.plot
        }
    }

    @Test fun `without a partner, or with no free plot beside one, the next free plot as before`() {
        // no field yet: the kozolec takes the next free plot
        val none = village(Age.ZASELEK, TENT to 0, TENT to 1, HUT to 2)
        assertEquals(3, built(none, KOZOLEC).plot)
        // the field's neighbours are all built (plot 2's: 0, and 9 is Vas's): the next free plot
        val walled = village(Age.ZASELEK, TENT to 0, TENT to 1, FIELD to 2, HUT to 3)
        assertTrue(Placement.freePlots(walled).none { distance(it, 2) <= NEIGHBOUR })
        assertEquals(4, built(walled, KOZOLEC).plot)
        // a building without partners ignores them: a hut takes the next free plot, a field beside it or not
        val hut = village(Age.ZASELEK, TENT to 0, TENT to 1, HUT to 2, FIELD to 4)
        assertEquals(3, built(hut, HUT).plot)
        assertEquals(3, built(hut, SMITHY).plot)
        // the palisade still takes none
        assertEquals(NO_PLOT, built(hut, PALISADE).plot)
    }

    @Test fun `placement is deterministic`() {
        val s = village(Age.VAS, TENT to 0, FIELD to 1, TENT to 2, WELL to 4)
        for (t in listOf(KOZOLEC, LIPA, CHURCH, HOUSE)) assertEquals(Placement.plotFor(s, t), Placement.plotFor(s, t))
        assertEquals(GameEngine.build(s, KOZOLEC, t0), GameEngine.build(s, KOZOLEC, t0))
    }

    // --- the kozolec of an older save moves beside its field --------------------------------

    @Test fun `an older save's lone kozolec moves beside the field, only its plot changes`() {
        // today's rule put the kozolec on plot 5, far from the field on plot 1; plot 8 beside the field is free
        val old = village(Age.ZASELEK, TENT to 0, FIELD to 1, TENT to 2, HUT to 3, WELL to 4, KOZOLEC to 5)
            .let { s -> s.copy(buildings = s.buildings.map { if (it.type == KOZOLEC) it.copy(level = 2, damaged = true) else it }) }
        assertFalse(Placement.beside(5, listOf(1)))
        val moved = Placement.hayracksByFields(old)
        val k = moved.buildings.single { it.type == KOZOLEC }
        assertEquals(nearestFree(old, listOf(1)), k.plot)
        assertTrue(distance(k.plot, 1) <= NEIGHBOUR)
        // its plot only: the same id, level, damage and date
        assertEquals(old.buildings.single { it.type == KOZOLEC }.copy(plot = k.plot), k)
        // no other building moved, the order kept
        assertEquals(old.buildings.filter { it.type != KOZOLEC }, moved.buildings.filter { it.type != KOZOLEC })
        assertEquals(old.buildings.map { it.id }, moved.buildings.map { it.id })
        assertTrue(Placement.HAYRACKS_BY_FIELDS in moved.migrated)
        // the tick runs it
        val ticked = GameEngine.tick(old, day0, t0, Fixtures.pool()).state
        assertEquals(moved.buildings, ticked.buildings)
        assertTrue(Placement.HAYRACKS_BY_FIELDS in ticked.migrated)
    }

    @Test fun `a kozolec already beside a field stays`() {
        val s = village(Age.ZASELEK, TENT to 0, FIELD to 1, TENT to 2, KOZOLEC to 3)
        val after = Placement.hayracksByFields(s)
        assertSame(s.buildings, after.buildings)
        assertTrue(Placement.HAYRACKS_BY_FIELDS in after.migrated)
        // no kozolec, or no field: nothing to move, done
        assertTrue(Placement.HAYRACKS_BY_FIELDS in Placement.hayracksByFields(village(Age.TABOR, TENT to 0, FIELD to 1)).migrated)
        val noField = village(Age.TABOR, TENT to 0, KOZOLEC to 4)
        assertSame(noField.buildings, Placement.hayracksByFields(noField).buildings)
        assertTrue(Placement.HAYRACKS_BY_FIELDS in Placement.hayracksByFields(noField).migrated)
    }

    @Test fun `a lone kozolec with no free plot beside a field waits for one`() {
        // Zaselek full: the field on plot 2 has no free neighbour, the kozolec on plot 4 stays for now
        val full = village(Age.ZASELEK, TENT to 0, TENT to 1, FIELD to 2, HUT to 3, KOZOLEC to 4, LIPA to 5, HUT to 6, CHURCH to 7, SMITHY to 8)
        val waiting = Placement.hayracksByFields(full)
        assertSame(full, waiting)
        assertFalse(Placement.HAYRACKS_BY_FIELDS in waiting.migrated)
        // Vas brings plot 9, beside the field: the kozolec moves there
        val vas = Placement.hayracksByFields(waiting.copy(age = Age.VAS))
        val k = vas.buildings.single { it.type == KOZOLEC }
        assertEquals(9, k.plot)
        assertTrue(distance(9, 2) <= NEIGHBOUR)
        assertTrue(Placement.HAYRACKS_BY_FIELDS in vas.migrated)
    }

    @Test fun `the move runs once`() {
        val old = village(Age.ZASELEK, TENT to 0, FIELD to 1, TENT to 2, HUT to 3, WELL to 4, KOZOLEC to 5)
        val once = Placement.hayracksByFields(old)
        // again: nothing changes
        assertSame(once, Placement.hayracksByFields(once))
        assertSame(once.buildings, GameEngine.tick(once, day0, t0, Fixtures.pool()).state.buildings)
        // done for good: a kozolec standing apart later (built where the plots allowed) stays where it is
        val later = once.copy(buildings = once.buildings.map { if (it.type == KOZOLEC) it.copy(plot = 6) else it })
        assertFalse(Placement.beside(6, listOf(1)))
        assertSame(later, Placement.hayracksByFields(later))
        // a new village's first tick finds nothing to move, and its kozolec is placed by the rule from then on
        val fresh = GameEngine.tick(GameEngine.newGame(7, t0), day0, t0, Fixtures.pool()).state
        assertTrue(Placement.HAYRACKS_BY_FIELDS in fresh.migrated)
    }

    @Test fun `the done migrations are saved`() {
        val s = Placement.hayracksByFields(village(Age.TABOR, TENT to 0, FIELD to 1))
        val text = si.lanisce.lani.data.json.encodeToString(GameState.serializer(), s)
        assertEquals(s.migrated, si.lanisce.lani.data.json.decodeFromString(GameState.serializer(), text).migrated)
        assertEquals(emptySet<String>(), si.lanisce.lani.data.json.decodeFromString(GameState.serializer(), """{"seed":1}""").migrated)
    }
}
