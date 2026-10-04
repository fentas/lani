package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType.*
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Sleep
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.Whereabouts
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.parseVillagers
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * The Primorska village through a day and a night on the map ([TownPeople], [Routine]): at work by day, the fire emptying in the
 * evening, the rituals with their lanterns, the windows going dark as the houses go to bed. PNGs in
 * build/village-snapshots/day for review.
 */
class TownDayRenderTest {
    private val dir = File("build/village-snapshots/day").apply { mkdirs() }
    private val cultures = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }
    private val cast: List<Villager> by lazy {
        File(cultures, "primorska/villagers").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }.sortedBy { it.order }
    }
    private val scenes: List<SceneSpec> by lazy {
        listOf(File(cultures, "../scenes"), File(cultures, "primorska/scenes")).flatMap { d -> d.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }
    }

    /** The Primorska village, everyone moved in: the camp's tent and hut, four houses, the workplaces round the fire. */
    private val town by lazy {
        val types = listOf(HUT, FIELD, WELL, TENT, KOZOLEC, HOUSE, LIPA, BEEHIVE, HOUSE, SMITHY, MARKET, HOUSE, SCHOOL, WATCHTOWER, HOUSE, CHURCH)
        GameState(
            seed = 42, age = Age.VAS, villagers = cast.size, morale = 80, fire = 80,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i, level = 2, builtAt = i.toLong()) },
            residents = cast.map { Resident(it.id, "2026-01-01") },
        )
    }
    private val day by lazy { Routine.Day(homes = Sleep.homes(scenes, town, cast), tellers = Whereabouts.tellers(scenes)) }
    private val fit = SceneFit.town(1080, 2400)

    private fun frame(date: LocalDate, h: Int, m: Int, time: Double = 3.0, d: Routine.Day = day) =
        Frame(time = time, hour = h + m / 60f, month = date.monthValue, people = cast, today = date.toString(), day = d)

    private fun placed(date: LocalDate, h: Int, m: Int, d: Routine.Day = day) =
        TownPeople.of(town, cast, 3.0, h + m / 60f, date.monthValue, fit.width, fit.height, date = date, day = d)

    private val june = LocalDate.of(2026, 6, 24)
    private val december = LocalDate.of(2026, 12, 9)

    @Test fun `by day they are at work, in the evening round the fire, at night home with the lights out`() {
        // half past ten on a June Wednesday: everyone out, the children at school, the smith at his anvil
        val work = placed(june, 10, 30)
        assertEquals(cast.size, work.size)
        assertTrue(work.filter { it.id in setOf("zala", "tine", "nejc", "mojca") }.all { it.place == TownPlace.At(SCHOOL) })
        // a December evening at seven: nobody wanders in the dark, those out are by the fire or on their way
        val evening = placed(december, 19, 0)
        assertTrue("$evening", evening.all { it.activity == Activity.WARM || it.walking })
        assertTrue(evening.any { it.id == "janez" })
        // twenty to ten: Luka at the sheep with his lantern, Jože gone from the tower, the fire emptier
        val rituals = placed(december, 21, 35)
        val luka = rituals.first { it.id == "luka" }
        assertTrue(luka.lantern)
        assertEquals(TownPlace.Spot("meadow"), luka.place)
        // a quarter to midnight: all in bed, the storyteller too; his lamp burns a little while, then every window is dark
        assertTrue(placed(december, 23, 45).isEmpty())
        val janez = (day.homes.getValue("janez") as TownPlace.At).let { h -> h.id ?: town.buildings.first { it.type == h.type }.id }
        assertEquals(setOf(janez), town.buildings.map { it.id }.toSet() - TownPeople.darkWindows(town, cast, 23.75f, 12, december, day))
        assertEquals(town.buildings.map { it.id }.toSet(), TownPeople.darkWindows(town, cast, 0.25f, 12, december.plusDays(1), day))
        // seven in the evening the houses are lit where someone is home awake
        val lit = town.buildings.map { it.id }.toSet() - TownPeople.darkWindows(town, cast, 19f, 12, december, day)
        assertTrue("$lit", lit.isNotEmpty())
    }

    @Test fun `the day and the night rendered`() {
        val r = VillageRenderer()
        val rig = CameraRig.town(fit, 1080, 2400)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val shots = listOf(
            Triple("01-june-1030-work", june, 10 to 30),
            Triple("02-june-1745-evening", june, 17 to 45),
            Triple("03-december-1900-fire", december, 19 to 0),
            Triple("04-december-2135-rituals", december, 21 to 35),
            Triple("05-december-2345-asleep", december, 23 to 45),
            Triple("06-december-0445-stove", december, 4 to 45),
        )
        val sums = HashMap<String, Long>()
        for ((name, date, hm) in shots) {
            // the village as the map's default view has it: the clearing and the woods below, cropped, 4× (nearest neighbour)
            val c = PixelCanvas(fit.width, fit.height)
            r.render(c, town, frame(date, hm.first, hm.second))
            val x0 = (comp.fireX - 95).coerceAtLeast(0); val y0 = (comp.fireY - 75).coerceAtLeast(0)
            val cw = minOf(190, c.width - x0); val ch = minOf(190, c.height - y0)
            val img = BufferedImage(cw * 4, ch * 4, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until ch * 4) for (x in 0 until cw * 4) img.setRGB(x, y, c.pixels[(y0 + y / 4) * c.width + x0 + x / 4])
            ImageIO.write(img, "png", File(dir, "$name.png"))
            sums[name] = c.pixels.sumOf { ((it shr 16) and 0xff) + ((it shr 8) and 0xff).toLong() }
            // a closer look round the fire at the finest detail: the people with their faces, the lanterns
            val cam = rig.lookAt(comp.fireX.toFloat(), comp.fireY.toFloat() + 10f, rig.minScale * 6f)
            val lens = Lens.of(3, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val f = PixelCanvas(fit.width, fit.height)
            r.render(f, town, frame(date, hm.first, hm.second).copy(lens = lens))
            val big = BufferedImage(f.width, f.height, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until f.height) for (x in 0 until f.width) big.setRGB(x, y, f.pixels[y * f.width + x])
            ImageIO.write(big, "png", File(dir, "$name-close.png"))
        }
        // the village gets darker as its windows go out: midnight darker than seven in the evening
        assertTrue(sums.getValue("05-december-2345-asleep") < sums.getValue("03-december-1900-fire"))
        // the rituals close up, a lantern each: Luka with the sheep in the meadow, Tone banking the forge, Micka's wood before dawn
        val smithy = TownAnchors.top(town.buildings.first { it.type == SMITHY }, town, fit.width, fit.height)
        for ((name, at, whenOn) in listOf(
            Triple("07-ritual-luka-sheep", TownAnchors.spotAnchor(town, fit.width, fit.height, "meadow")!!.let { CanvasPoint(it[0].toFloat(), it[1].toFloat()) }, Triple(december, 21, 35)),
            Triple("08-ritual-tone-forge", CanvasPoint(smithy.x, smithy.y + 16f), Triple(december, 21, 52)),
            Triple("09-ritual-micka-stove", TownAnchors.spotAnchor(town, fit.width, fit.height, "woodpile")!!.let { CanvasPoint(it[0].toFloat(), it[1].toFloat()) }, Triple(december, 4, 45)),
            // and by day, closer: the field, the market and the fire in the morning; the smithy and the well in the afternoon
            Triple("10-day-field-market", CanvasPoint(comp.fireX + 20f, comp.fireY + 14f), Triple(june, 10, 30)),
            Triple("11-day-smithy-well", CanvasPoint(smithy.x - 30f, smithy.y + 26f), Triple(june, 15, 20)),
        )) {
            val cam = rig.lookAt(at.x, at.y, rig.minScale * (if (name.startsWith("1")) 9f else 12f))
            val lens = Lens.of(3, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val f = PixelCanvas(fit.width, fit.height)
            r.render(f, town, frame(whenOn.first, whenOn.second, whenOn.third).copy(lens = lens))
            val img = BufferedImage(f.width * 2, f.height * 2, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until f.height * 2) for (x in 0 until f.width * 2) img.setRGB(x, y, f.pixels[(y / 2) * f.width + x / 2])
            ImageIO.write(img, "png", File(dir, "$name.png"))
        }
    }
}
