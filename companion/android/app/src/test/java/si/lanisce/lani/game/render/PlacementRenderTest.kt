package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.MARKET
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.SCHOOL
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Placement
import si.lanisce.lani.game.Res
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Villages built through the engine (so each building stands where [Placement] puts it), rendered for review in
 * build/village-snapshots/placement-*.png: the kozolec beside its field, the well, the linden, the church and the
 * market together; and an older save's lone kozolec before and after it moves beside its field.
 */
class PlacementRenderTest {
    private val t0 = 1_000_000L

    /** Builds [types] in order, each in its [Age] (the age set as it comes), the engine choosing the plots. */
    private fun grow(vararg steps: Pair<Age, List<BuildingType>>): GameState {
        var s = GameState(seed = 42, villagers = 6, morale = 70, fire = 70, resources = Res.entries.associateWith { 9999 })
        for ((age, types) in steps) {
            s = s.copy(age = age)
            for (t in types) {
                val next = GameEngine.build(s, t, t0)
                assertEquals("$t in $age", s.buildings.size + 1, next.buildings.size)
                s = next.copy(resources = Res.entries.associateWith { 9999 })
            }
        }
        return s
    }

    private val hamlet = arrayOf(
        Age.OGENJ to listOf(TENT, FIELD),
        Age.TABOR to listOf(TENT, KOZOLEC, WELL, PALISADE),
        Age.ZASELEK to listOf(LIPA, CHURCH, HUT, HUT),
    )
    private val village = hamlet + arrayOf(Age.VAS to listOf(MARKET, FIELD, KOZOLEC, HOUSE, SCHOOL))

    /** The kozolec stands beside a field, and each of the square's buildings beside another of them. */
    private fun assertTogether(s: GameState) {
        val plots = { t: BuildingType -> s.buildings.filter { it.type == t }.map { it.plot } }
        for (k in plots(KOZOLEC)) assertTrue("kozolec on $k by a field ${plots(FIELD)}", Placement.beside(k, plots(FIELD)))
        val square = s.buildings.filter { it.type in setOf(WELL, LIPA, CHURCH, MARKET) }.map { it.plot }
        for (p in square) assertTrue("square: $p with $square", Placement.beside(p, square - p))
    }

    @Test fun `render villages built by the placement`() {
        val dir = File("build/village-snapshots").apply { mkdirs() }
        val zaselek = grow(*hamlet)
        val vas = grow(*village)
        assertTogether(zaselek)
        assertTogether(vas)
        println("placement: Zaselek ${zaselek.buildings.map { "${it.type}@${it.plot}" }}")
        println("placement: Vas ${vas.buildings.map { "${it.type}@${it.plot}" }}")
        // an older save: the plots in today's order, the kozolec (plot 5) far from the field (plot 1); in Vas it moves beside it
        val oldTypes = listOf(TENT, FIELD, TENT, HUT, WELL, KOZOLEC, LIPA, HUT, CHURCH, BEEHIVE)
        val old = GameState(
            seed = 42, age = Age.VAS, villagers = 6, morale = 70, fire = 70,
            buildings = oldTypes.mapIndexed { i, t -> Building("b$i", t, plot = i) } + Building("p", PALISADE, plot = -1),
        )
        val moved = Placement.hayracksByFields(old)
        assertTrue(Placement.beside(moved.buildings.single { it.type == KOZOLEC }.plot, listOf(1)))
        val fit = SceneFit.town(1080, 2400)
        val frame = Frame(time = 3.0, hour = 11f, month = 7)
        for ((name, s) in listOf("zaselek" to zaselek, "vas" to vas, "old-before" to old, "old-after" to moved)) {
            val c = PixelCanvas(fit.width, fit.height)
            VillageRenderer().render(c, s, frame)
            write(c, File(dir, "placement-$name.png"), crop = centre(fit.width, fit.height))
        }
    }

    /** The village's middle: the plots of Vas and the land round them. */
    private fun centre(w: Int, h: Int): IntArray {
        val comp = VillageLayout.composition(w, h, false)
        val cw = 260.coerceAtMost(w); val ch = 170.coerceAtMost(h)
        val x0 = (comp.fireX - cw / 2).coerceIn(0, w - cw); val y0 = (comp.fireY - ch / 2 - 10).coerceIn(0, h - ch)
        return intArrayOf(x0, y0, cw, ch)
    }

    private fun write(c: PixelCanvas, f: File, crop: IntArray, scale: Int = 4) {
        val (x0, y0, w, h) = crop.toList()
        val img = BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) img.setRGB(x, y, c.pixels[(y0 + y / scale) * c.width + x0 + x / scale])
        ImageIO.write(img, "png", f)
    }
}
