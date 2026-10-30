package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.SCHOOL
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.NO_PLOT
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The bridge over the stream in each culture's look on the village map ([MapLook.bridge], companion/GAME.md "The map in a
 * culture's look"): the map's timber bridge on stone pillars (Primorska's, as it always was), Friuli's pillars of river
 * cobbles under a course of brick, Carinthia's deck and railings of larch, the Lake District's packhorse bridge of slate with
 * one round arch, each as its project's steps build it. The sheet (build/map-looks/04-bridge.png): a row a culture, a
 * column a step, by day, at night and in winter closer up.
 */
class MapBridgeLooksTest {
    private val dir = File("build/map-looks").apply { mkdirs() }
    private val cultures = listOf("primorska", "friuli", "kaernten", "lakeland")

    /** Jan's village of the bridge's first renders (BridgesTest), its bridge at [st] of its six steps. */
    private fun village(st: Int) = GameState(
        seed = 8993091993378563000L, age = Age.VAS, villagers = 12, morale = 70, fire = 70, land = Land.CLASSIC,
        buildings = listOf(
            Building("b0", TENT, NO_PLOT), Building("b1", FIELD, 1), Building("b2", HUT, 2, level = 3), Building("b3", WELL, 3),
            Building("b4", PALISADE, NO_PLOT), Building("b5", KOZOLEC, 8), Building("b6", BEEHIVE, 5, level = 2),
            Building("b7", CHURCH, 6), Building("b8", HOUSE, 0, level = 3), Building("b9", SMITHY, 4), Building("b10", LIPA, 7),
            Building("b11", SCHOOL, 9),
        ),
        projects = if (st == 0) emptyMap() else mapOf(Crossing.BRIDGE to st),
    )

    private val k = 3; private val cw = 90; private val ch = 56

    /** The crossing closer up (k = 3), [cw] × [ch] nominal px round it, in [culture]'s look. */
    private fun shot(st: Int, culture: String, frame: Frame = Frame(time = 3.0, hour = 11f, month = 10)): PixelCanvas {
        val fit = SceneFit.town(1080, 2400)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val b = Crossing.box(Terrain.CLASSIC, 1f)
        val cx = b[0] + b[2] / 2f; val cy = b[1] + b[3] / 2f
        val fx = comp.fireX + ((cx - cy) * 4f).toInt(); val fy = comp.fireY + ((cx + cy) * 2f).toInt()
        val lens = Lens(k, fx * k - cw * k / 2, fy * k - ch * k / 2, fit.width, fit.height)
        return PixelCanvas(cw * k, ch * k).also { VillageRenderer().render(it, village(st), frame.copy(lens = lens, culture = culture)) }
    }

    @Test fun `render the bridge in each culture's look at every step`() {
        val rows = cultures.map { c ->
            c to ((1..6).map { st -> "$st" to shot(st, c) } + listOf(
                "night" to shot(6, c, Frame(time = 3.0, hour = 22.5f, month = 10)),
                "winter" to shot(6, c, Frame(time = 3.0, hour = 12.5f, month = 1)),
            ))
        }
        val cols = rows.first().second.size
        val sc = 1; val lab = 14
        val img = BufferedImage(cols * cw * k * sc, rows.size * (ch * k * sc + lab), BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(30, 30, 34); g.fillRect(0, 0, img.width, img.height); g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        for ((r, row) in rows.withIndex()) for ((col, cell) in row.second.withIndex()) {
            val (name, c) = cell
            val ox = col * cw * k * sc; val oy = r * (ch * k * sc + lab)
            for (y in 0 until c.height * sc) for (x in 0 until c.width * sc) img.setRGB(ox + x, oy + lab + y, c.pixels[(y / sc) * c.width + x / sc])
            g.color = Color(230, 230, 230); g.drawString("${row.first} $name", ox + 3, oy + 11)
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "04-bridge.png"))
    }

    @Test fun `each culture's bridge is its own once its pillars stand, the map's own the default's`() {
        for (st in 3..6) {
            val own = shot(st, "primorska").pixels.toList()
            assertEquals("an unknown culture's is the map's own at $st", own, shot(st, "nowhere").pixels.toList())
            for (c in listOf("friuli", "lakeland")) assertNotEquals("$c's at $st", own, shot(st, c).pixels.toList())
            // Carinthia's larch is its deck's and its railings'
            if (st >= 5) assertNotEquals("kaernten's at $st", own, shot(st, "kaernten").pixels.toList())
        }
        assertEquals(Bridge.TIMBER, MapLook.DEFAULT.bridge)
        assertEquals(listOf(Bridge.COBBLES, Bridge.LARCH, Bridge.PACKHORSE), listOf("friuli", "kaernten", "lakeland").map { MapLook.of(it).bridge })
    }
}
