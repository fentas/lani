package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WATCHTOWER
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The maypole stands in the middle of the village (the last step's line: "…mlaj zdaj stoji sredi vasi!"): by the plaza
 * round the fire, clear of it, at every age, on the classic land and every generated one, Jan's village among them, and
 * on the strip without the woods. Down for the season (after May) its place stays its own: nothing drawn there, the other
 * landmarks where they were. Close-ups in build/maypole-snapshots.
 */
class MaypolePlacementTest {
    private val dir = File("build/maypole-snapshots").apply { mkdirs() }
    private val fit = SceneFit.town(1080, 2400)

    /** Jan's village when the maypole was raised (data/app/game.json, 2026-10-11): a hamlet, the church and the linden at the side. */
    private val jan = GameState(
        seed = 8993091993378563000L, age = Age.ZASELEK, villagers = 9, morale = 70, fire = 70,
        buildings = listOf(
            Building("b0", TENT, NO_PLOT), Building("b1", FIELD, 1), Building("b2", HUT, 2, level = 3), Building("b3", WELL, 3),
            Building("b4", PALISADE, NO_PLOT), Building("b5", KOZOLEC, 8), Building("b6", BEEHIVE, 5, level = 2),
            Building("b7", CHURCH, 6), Building("b8", HOUSE, 0, level = 2), Building("b9", SMITHY, 4), Building("b10", LIPA, 7),
        ),
        projects = mapOf("most" to 2, "mlin" to 1, "mlaj" to 5), land = Land.CLASSIC,
    )

    private val hamletTypes = arrayOf(FIELD, HUT, WELL, KOZOLEC, TENT, PALISADE, HUT, FIELD, LIPA)
    private val villageTypes = arrayOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE)
    private val trgTypes = villageTypes + arrayOf(MARKET, HOUSE, SCHOOL, KOZOLEC, HOUSE, BEEHIVE)
    private val townTypes = trgTypes + arrayOf(HOUSE, WELL, HOUSE, MARKET, HOUSE, HUT, HOUSE, WELL)

    private fun typesOf(age: Age): Array<BuildingType> = when (age) {
        Age.ZASELEK -> hamletTypes
        Age.VAS -> villageTypes
        Age.TRG -> trgTypes
        else -> townTypes
    }

    /** A village of [age] on [land], every plot of the age built, every project of the age finished. */
    private fun village(age: Age, land: Land?, seed: Long = 42): GameState = GameState(
        seed = seed, age = age, villagers = 10, morale = 70, fire = 70,
        buildings = typesOf(age).take(PLOTS_PER_AGE[age.ordinal]).mapIndexed { i, t -> Building("b$i", t, i) },
        projects = si.lanisce.lani.game.Catalog.projects.filter { it.age <= age }.associate { it.id to it.steps.size }, land = land,
    )

    private fun cases(): List<Pair<String, GameState>> {
        val out = ArrayList<Pair<String, GameState>>()
        out += "jan" to jan
        for (age in listOf(Age.ZASELEK, Age.VAS, Age.TRG, Age.MESTO)) out += "classic-${age.name.lowercase()}" to village(age, null)
        for (ls in Landscape.entries.filter { it != Landscape.CLASSIC }) for (seed in listOf(7L, 1234L, 99L)) for (age in listOf(Age.ZASELEK, Age.VAS, Age.MESTO)) {
            out += "${ls.id}-$seed-${age.name.lowercase()}" to village(age, Land.of(ls, seed), seed)
        }
        return out
    }

    @Test fun `the maypole stands by the plaza in the middle of the village, at every age and on every land`() {
        for ((name, s) in cases()) {
            val sp = LandmarkLayout.of(s, fit.width, fit.height).firstOrNull { it.landmark.project == "mlaj" }
            assertTrue("$name: the maypole has a place", sp != null)
            sp!!
            val sx = (sp.cx - sp.cy) * 4f; val sy = (sp.cx + sp.cy) * 2f
            val metric = VillageLayout.screenMetric(sx, sy)
            val land = VillageLayout.of(s)
            val built = s.buildings.filter { it.type.onPlot && it.plot >= 0 }
            val nearer = built.count { b -> val c = land.centers[b.plot]; VillageLayout.metric(c[0], c[1]) < metric }
            println("maypole %s: cell (%.2f, %.2f) screen (%.0f, %.0f) metric %.0f of the clearing's %.0f, buildings nearer %d of %d".format(name, sp.cx, sp.cy, sx, sy, metric, land.clearing(s.age), nearer, built.size))
            // in the middle: within half the clearing's reach from the fire, and nearer the fire than three in four buildings
            assertTrue("$name: %.0f px from the fire, the clearing %.0f".format(metric, land.clearing(s.age)), metric <= land.clearing(s.age) * 0.5f)
            assertTrue("$name: $nearer of ${built.size} buildings nearer the fire", nearer * 4 <= built.size)
            // the strip without the woods puts it where the town does
            val strip = SceneFit.of(1080, 700, true)
            assertEquals(name, listOf(sp.x, sp.y), LandmarkLayout.of(s, strip.width, strip.height, compact = true).first { it.landmark.project == "mlaj" }.let { listOf(it.x, it.y) })
            snapshot(name, s)
        }
    }

    @Test fun `down for the season, nothing stands there and the others keep their places`() {
        for ((name, s) in listOf("jan" to jan, "classic-mesto" to village(Age.MESTO, null), "lake-7-vas" to village(Age.VAS, Land.of(Landscape.LAKE, 7), 7))) {
            val down = s.copy(landmarksDown = setOf("mlaj"))
            val up = LandmarkLayout.of(s, fit.width, fit.height)
            val spots = LandmarkLayout.of(down, fit.width, fit.height)
            assertNull(name, spots.firstOrNull { it.landmark.project == "mlaj" })
            assertEquals(name, up.filter { it.landmark.project != "mlaj" }.map { listOf(it.landmark.project, it.x, it.y) }, spots.map { listOf(it.landmark.project, it.x, it.y) })
            // its place kept for when it's raised again
            assertEquals(name, up.first { it.landmark.project == "mlaj" }.let { listOf(it.x, it.y) }, LandmarkLayout.all(down, fit.width, fit.height).first { it.landmark.project == "mlaj" }.let { listOf(it.x, it.y) })
            // no tap finds it, no scene's place is there
            assertTrue(name, TownAnchors.landmarks(down, fit.width, fit.height).none { it.first.landmark.project == "mlaj" })
            assertTrue(name, si.lanisce.lani.game.scene.TownPlace.Landmark("mlaj") !in TownAnchors.withSpots(down, fit.width, fit.height))
        }
        // a village whose only project is the maypole, down: drawn as if it had never been started
        val only = jan.copy(projects = mapOf("mlaj" to 5))
        val frame = Frame(time = 3.0, hour = 11f, month = 10)
        val gone = PixelCanvas(fit.width, fit.height).also { VillageRenderer().render(it, only.copy(landmarksDown = setOf("mlaj")), frame) }
        val never = PixelCanvas(fit.width, fit.height).also { VillageRenderer().render(it, only.copy(projects = emptyMap()), frame) }
        assertTrue("down, nothing of the maypole is drawn", gone.pixels.contentEquals(never.pixels))
        snapshot("jan-down", jan.copy(landmarksDown = setOf("mlaj")), month = 10)
    }

    /** The middle of the village of [s], close up (build/maypole-snapshots/<name>.png). */
    private fun snapshot(name: String, s: GameState, month: Int = 5) {
        val c = PixelCanvas(fit.width, fit.height)
        VillageRenderer().render(c, s, Frame(time = 3.0, hour = 11f, month = month))
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val w = 240; val h = 140; val scale = 3
        val x0 = comp.fireX - w / 2; val y0 = comp.fireY - h / 2 - 10
        val img = BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) {
            val px = (x0 + x / scale).coerceIn(0, c.width - 1); val py = (y0 + y / scale).coerceIn(0, c.height - 1)
            img.setRGB(x, y, c.pixels[py * c.width + px])
        }
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }
}
