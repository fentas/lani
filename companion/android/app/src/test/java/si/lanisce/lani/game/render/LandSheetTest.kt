package si.lanisce.lani.game.render

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
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.WATCHTOWER
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Landscape
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Contact sheets of generated lands for review (build/land-snapshots/sheet-…): twelve seeds of each landscape, the first
 * age as the setup's preview shows it, and a village (Vas) on a phone held upright and on its side.
 */
class LandSheetTest {
    private val dir = File("build/land-snapshots").apply { mkdirs() }

    private val seeds = listOf(1L, 2L, 3L, 7L, 11L, 42L, 99L, 123L, 777L, 2024L, 31337L, 8993091993378563000L)

    private val vasTypes = listOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE)

    private fun vas(s: GameState) = s.copy(
        age = Age.VAS, villagers = 10,
        buildings = vasTypes.mapIndexed { i, t -> Building("b$i", t, if (t == PALISADE) -1 else i, level = if (i % 4 == 3) 2 else 1) },
    )

    private fun sheet(name: String, states: List<GameState>, w: Int, h: Int, crop: IntArray, frame: Frame, cols: Int = 4) {
        val cw = crop[2]; val ch = crop[3]
        val rows = (states.size + cols - 1) / cols
        val img = BufferedImage(cols * (cw + 2), rows * (ch + 2), BufferedImage.TYPE_INT_RGB)
        for ((n, s) in states.withIndex()) {
            val c = PixelCanvas(w, h)
            VillageRenderer().render(c, s, frame)
            val ox = (n % cols) * (cw + 2); val oy = (n / cols) * (ch + 2)
            for (y in 0 until ch) for (x in 0 until cw) img.setRGB(ox + x, oy + y, c.pixels[(y + crop[1]) * w + x + crop[0]])
        }
        ImageIO.write(img, "png", File(dir, "sheet-$name.png"))
    }

    /**
     * What the founding screen's preview shows (ui/game/FoundingScreen: the town's framing of a card on a phone, about
     * 950 × 594 px, by day): a new village of each landscape and region, four rerolls each, from the same first seed.
     */
    @Test fun `render the founding previews`() {
        val fit = SceneFit.town(950, 594)
        val rows = listOf("primorska" to null, "friuli" to "coast", "friuli" to null, "kaernten" to null, "primorska" to "mountains")
        val founded = rows.flatMap { (culture, landscape) ->
            generateSequence(si.lanisce.lani.game.Founding.start(culture, landscape, 2026L)) { it.reroll() }.take(4).map { it.preview() }.toList()
        }
        sheet("founding-previews", founded, fit.width, fit.height, intArrayOf(0, 0, fit.width, fit.height), Frame(time = 3.0, hour = 11f, month = 6))
    }

    @Test fun `render the contact sheets`() {
        val tall = VillageLayout.composition(360, 800, false)
        for (l in Landscape.entries.filter { it != Landscape.CLASSIC }) {
            val first = seeds.map { GameEngine.newGame(it, 0L, l) }
            sheet("${l.id}-first-age", first, 360, 800, intArrayOf(0, tall.horizon - 70, 360, 470), Frame(time = 3.0, hour = 11f, month = 6, today = "2026-09-27"))
            sheet("${l.id}-vas-tall", first.map(::vas), 360, 800, intArrayOf(0, tall.horizon - 70, 360, 470), Frame(time = 3.0, hour = 11f, month = 6, today = "2026-09-27"))
            sheet("${l.id}-vas-side", first.map(::vas), 400, 180, intArrayOf(0, 0, 400, 180), Frame(time = 3.0, hour = 15f, month = 9, today = "2026-09-27"), cols = 3)
        }
    }
}
