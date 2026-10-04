package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Terrain
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang

/**
 * Founding a new village (Founding, ui/game/FoundingScreen): the region's landscapes, the first choice (the node's, else
 * the region's usual one), the reroll's fixed sequence, and the village founded as the preview showed it.
 */
class FoundingTest {
    @Test fun `a region offers every landscape, the coast only where it has the sea`() {
        val inland = listOf(Landscape.VALLEY, Landscape.HILLS, Landscape.LAKE, Landscape.MOUNTAINS)
        assertEquals(inland, Founding.choices("primorska"))
        assertEquals(inland, Founding.choices("kaernten"))
        assertEquals(inland, Founding.choices("lakeland"))
        assertEquals(inland + Landscape.COAST, Founding.choices("friuli"))
        // a pack the app can't read offers the inland ones
        assertEquals(inland, Founding.choices("atlantis"))
        assertFalse(Landscape.CLASSIC in Founding.choices("friuli"))
    }

    @Test fun `the first choice is the node's when the region has it, else the region's usual landscape`() {
        assertEquals(Landscape.VALLEY, Founding.start("primorska", null, 1).landscape)
        assertEquals(Landscape.HILLS, Founding.start("friuli", null, 1).landscape)
        assertEquals(Landscape.LAKE, Founding.start("kaernten", null, 1).landscape)
        assertEquals(Landscape.LAKE, Founding.start("lakeland", null, 1).landscape)
        assertEquals(Landscape.MOUNTAINS, Founding.start("primorska", "mountains", 1).landscape)
        assertEquals(Landscape.COAST, Founding.start("friuli", "coast", 1).landscape)
        // the coast where there is no sea, the classic valley, a landscape this app doesn't know: the usual one
        assertEquals(Landscape.VALLEY, Founding.start("primorska", "coast", 1).landscape)
        assertEquals(Landscape.VALLEY, Founding.start("primorska", "classic", 1).landscape)
        assertEquals(Landscape.LAKE, Founding.start("kaernten", "volcano", 1).landscape)
        assertEquals(7L, Founding.start("primorska", null, 7).seed)
    }

    @Test fun `another place is the next of a fixed sequence, and always another land`() {
        val f = Founding("primorska", Landscape.VALLEY, 42L)
        // the same seed rolls the same next ones, every time
        val a = generateSequence(f) { it.reroll() }.take(6).map { it.seed }.toList()
        val b = generateSequence(Founding("primorska", Landscape.HILLS, 42L)) { it.reroll() }.take(6).map { it.seed }.toList()
        assertEquals(a, b)
        assertEquals(6, a.toSet().size)
        assertEquals(Founding.next(42L), f.reroll().seed)
        // each roll another land: its stream and its plots elsewhere
        val lands = a.map { Terrain.of(Land.of(Landscape.VALLEY, it)) }
        for (i in 1 until lands.size) {
            assertNotEquals(lands[i - 1].streamX(3f), lands[i].streamX(3f))
            assertNotEquals(lands[i - 1].centers.map { it.toList() }, lands[i].centers.map { it.toList() })
        }
        // a landscape of its own keeps the seed: the same place, another land
        assertEquals(f.seed, f.on(Landscape.LAKE).seed)
        assertEquals(Landscape.LAKE, f.on(Landscape.LAKE).landscape)
    }

    @Test fun `the village is founded where the preview showed it`() {
        val f = Founding("friuli", Landscape.COAST, -9876543210L)
        val preview = f.preview()
        val v = f.village(1_000L)
        assertEquals(Age.OGENJ, preview.age)
        assertEquals(Land.of(Landscape.COAST, -9876543210L), v.land)
        assertEquals(preview.land, v.land)
        assertEquals(f.seed, v.seed)
        assertEquals(Terrain.of(v.land!!).centers.map { it.toList() }, Terrain.of(preview.land!!).centers.map { it.toList() })
        assertTrue(v.buildings.isEmpty())
    }

    @Test fun `every landscape is named in every language`() {
        for (lang in listOf(Lang.SL, Lang.EN, Lang.IT, Lang.DE)) {
            val table = L10n.table(lang)
            for (l in Landscape.entries) assertTrue("${lang.code}: ${l.key}", l.key == "landscape.${l.id}" && !table[l.key].isNullOrBlank())
            for (k in listOf("title", "about", "region", "regionOnNode", "landscape", "anotherPlace", "here", "once")) {
                assertTrue("${lang.code}: founding.$k", !table["founding.$k"].isNullOrBlank())
            }
        }
    }
}
