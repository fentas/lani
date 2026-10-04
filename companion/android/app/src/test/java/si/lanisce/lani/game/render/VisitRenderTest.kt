package si.lanisce.lani.game.render

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.app.TownVisit
import si.lanisce.lani.app.VisitController
import si.lanisce.lani.data.PublicBuilding
import si.lanisce.lani.data.PublicResident
import si.lanisce.lani.data.PublicSky
import si.lanisce.lani.data.PublicTown
import si.lanisce.lani.data.PublicVisitor
import si.lanisce.lani.data.TownHead
import si.lanisce.lani.data.VisitContent
import si.lanisce.lani.data.VisitFetch
import si.lanisce.lani.data.VisitWorld
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.l10n.L10n
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * A visited town drawn as the visitor sees it (ui/towns/VisitScreen): Mia's Friulian village at Vas, from its public
 * state (a fixture as her bridge shows it), in the friuli culture's look with the sea at the horizon, its people out
 * about the fire, the fields and their homes. PNG snapshots in build/visit-snapshots for review: the whole town, and a
 * closer look round the fire.
 */
class VisitRenderTest {
    private val dir = File("build/visit-snapshots").apply { mkdirs() }
    private val fit = SceneFit.town(1080, 2400)
    private val rig = CameraRig.town(fit, 1080, 2400)
    private val today = LocalDate.of(2026, 9, 26)
    private val pair = L10n.pair

    private val town = PublicTown(
        town = TownHead("fec16ecd34869e522e575d0fb9ba45de", "Il mio villaggio", "Mia", "friuli", "it"),
        date = today.toString(), age = "VAS", foundedOn = "2026-07-01", villagers = 8,
        buildings = listOf(
            PublicBuilding("TENT", -1), PublicBuilding("FIELD", 0), PublicBuilding("HUT", 1, level = 2), PublicBuilding("WELL", 2),
            PublicBuilding("KOZOLEC", 3), PublicBuilding("HOUSE", 4), PublicBuilding("PALISADE", -1), PublicBuilding("LIPA", 5),
            PublicBuilding("BEEHIVE", 6), PublicBuilding("FIELD", 7, damaged = true), PublicBuilding("SMITHY", 8), PublicBuilding("HOUSE", 9),
        ),
        projects = mapOf("mlaj" to 5, "vinograd" to 3),
        residents = listOf(
            PublicResident("rosa", "Nonna Rosa"), PublicResident("davide", "Pastore Davide"), PublicResident("bepi", "Nonno Bepi"),
            PublicResident("franco", "Mugnaio Franco"), PublicResident("bruno", "Fabbro Bruno"), PublicResident("giulia", "Giulia", "child"),
            PublicResident("n-chiara-furlan", "Chiara Furlan", "child", "👧", "child2"), PublicResident("n-marco-furlan", "Marco Furlan", "adult", "👨", "man"),
        ),
        visitor = PublicVisitor("nives", "Zia Nives"),
        sky = PublicSky(scene = "field", wind = 0.3f),
    )

    /** The friuli cast, as the visitor's bridge passes it on (the files of cultures/friuli/villagers). */
    private fun cast() = parseVillagers(
        listOf(File("../../cultures/friuli/villagers"), File("../cultures/friuli/villagers")).first { it.isDirectory }
            .listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.joinToString(",", "[", "]") { it.readText() },
    )

    private fun visit(): TownVisit = VisitController.settle(
        TownVisit(town.town.id, town.town.name, town.town.learner, "friuli", "it"),
        VisitFetch.Got(town, false, null),
        VisitFetch.Got(VisitContent(cast(), null, false), false, null),
        VisitFetch.Got(VisitContent(emptyList(), null, false), false, null),
        today,
    )

    @After fun home() {
        VisitWorld.leave()
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    private fun frame(v: TownVisit, lens: Lens = Lens()) = Frame(
        time = 2.0, hour = 11f, month = 9, people = v.people(today), today = today.toString(), lens = lens,
        sea = Cultures.current.world.backdrop?.sea == true,
    )

    @Test fun `a visited friuli town is drawn from its public state, in its look, with its people`() {
        VisitWorld.enter("it", "friuli")
        val v = visit()
        val s = v.state!!
        assertEquals(12, s.buildings.size)
        assertTrue("friuli's horizon is the sea", Cultures.current.world.backdrop?.sea == true)
        val r = VillageRenderer()
        val c = PixelCanvas(fit.width, fit.height)
        r.render(c, s, frame(v))
        // the people who live there are out (Nives is visiting), the cast who don't aren't
        val out = r.villagerAnchors.keys
        for (id in listOf("rosa", "davide", "n-chiara-furlan", "nives")) assertTrue("$id is drawn: $out", id in out)
        for (id in listOf("aldo", "elena", "matteo")) assertTrue("$id doesn't live there: $out", id !in out)
        write(c, File(dir, "visit-friuli.png"), 2)

        // the same town in the visitor's own culture has no sea: the look is the host's
        VisitWorld.leave()
        val own = PixelCanvas(fit.width, fit.height)
        VillageRenderer().render(own, s, frame(v))
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        var sea = 0
        for (y in comp.horizon - 14..comp.horizon) for (x in 0 until fit.width / 2) if (own.pixels[y * fit.width + x] != c.pixels[y * fit.width + x]) sea++
        assertTrue("the sea shows only in the host's look ($sea px)", sea > 100)
    }

    @Test fun `render a closer look round the fire of the visited town`() {
        VisitWorld.enter("it", "friuli")
        val v = visit()
        val s = v.state!!
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val cam = rig.lookAt(comp.fireX.toFloat(), comp.fireY.toFloat() - 10f, (rig.minScale * 2).toFloat())
        val lens = Lens.of(2, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
        val c = PixelCanvas(fit.width, fit.height)
        VillageRenderer().render(c, s, frame(v, lens))
        val w = 1080; val h = 1400
        val img = BufferedImage(w / 2, h / 2, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until img.height) for (x in 0 until img.width) {
            val nx = lens.cx(cam.toCanvasX(x * 2f)).toInt().coerceIn(0, c.width - 1)
            val ny = lens.cy(cam.toCanvasY(500f + y * 2f)).toInt().coerceIn(0, c.height - 1)
            img.setRGB(x, y, c.pixels[ny * c.width + nx])
        }
        ImageIO.write(img, "png", File(dir, "visit-friuli-close.png"))
    }

    /** [c] at [scale] screen px per canvas px. */
    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }
}
