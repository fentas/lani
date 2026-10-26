package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.GameCache
import si.lanisce.lani.data.GamePut
import si.lanisce.lani.data.GameSnapshot
import si.lanisce.lani.data.GameSync
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.ContentPool
import si.lanisce.lani.game.Founding
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.culture.Cultures
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

/**
 * The water of a land is on every canvas of the town (GAME.md, "The land"): the sea along the left of a coast at every row
 * from the horizon down past the plots, a lake at the clearing's right, both on the narrowest canvas (a tablet, a phone held
 * upright: [SceneFit.TOWN_W] wide), so the village after founding is the land its preview showed. Until 2026-10-26 the
 * sea of half the coasts lay 160 to 180 px left of the fire, off those canvases: Teo's coast (2026-10-25, Friuli, land
 * seed 551323397) had its sea's sound and none of its beach in view, nor anywhere to pan to.
 */
class CoastTest {
    /** Teo's village: founded on the coast with the dice's seed whose land is 551323397. */
    private val teo = Founding("friuli", Landscape.COAST, 551323397L)

    private val seeds = (1L..300L) + listOf(551323397L, -5L, -123456789L, 8993091993378563000L)

    /** The narrowest canvas of the town: 160 nominal px either side of the fire. */
    private val half = SceneFit.TOWN_W / 2f

    private val canvases = listOf(320 to 200, 320 to 512, 360 to 800, 400 to 180)

    @Test fun `Teo's village is founded on the coast with its own land`() {
        val v = teo.village(1_000L)
        assertEquals(Land("coast", 551323397), v.land)
        assertEquals(Age.OGENJ, v.age)
        assertTrue(Terrain.of(v).sea != null)
    }

    @Test fun `the sea is on the narrowest canvas of the town at every row, from the horizon past the last plot`() {
        for (seed in seeds) {
            val t = Terrain.of(Land.of(Landscape.COAST, seed))
            var least = Float.MAX_VALUE
            for (sy in -VillageLayout.HORIZON..56 step 2) least = min(least, half + t.shoreX(sy.toFloat()))
            assertTrue("coast:$seed: $least px of sea", least >= 14f)
            // and never further in than the plots leave it room
            for (p in t.centers) assertTrue("coast:$seed: a plot on the beach", !t.wet(p[0], p[1], 11f))
            // nor over the woods' pond, at any age
            for (age in Age.entries) for ((w, h) in listOf(320 to 512, 360 to 800)) {
                val fg = Foreground.of(age, w, h, t) ?: continue
                val fireY = VillageLayout.composition(w, h, false).fireY
                for (dy in -1..1) {
                    val y = fg.pondY + dy * fg.pondRy
                    assertTrue("coast:$seed $age ${w}x$h: the sea over the pond", w / 2f + t.shoreX(y - fireY) + Terrain.BEACH < fg.pondX - fg.pondRx)
                }
            }
        }
    }

    @Test fun `a lake is on the narrowest canvas of the town`() {
        for (seed in seeds) {
            val l = Terrain.of(Land.of(Landscape.LAKE, seed)).lake!!
            assertTrue("lake:$seed: ${half - (l[0] - l[2])} px of lake", half - (l[0] - l[2]) >= 24f)
        }
    }

    /**
     * The water takes some places a plot could be: the last plot lies at most a little further out than on the land without
     * it (where it lay beyond [132 px], as on half the lands of every landscape, the water leaves that), and on most coasts not
     * at all.
     */
    @Test fun `the water leaves the plots about the room the land without it has`() {
        var pushed = 0
        for (l in listOf(Landscape.COAST, Landscape.LAKE)) for (seed in seeds) {
            val land = Land.of(l, seed)
            // the valley of the same seed: the same stream, road and rows, without the water
            val dry = Terrain.of(Land(Landscape.VALLEY.id, land.seed)).centers[27][2]
            val last = Terrain.of(land).centers[27][2]
            assertTrue("${l.id}:$seed: the last plot at $last, without the water at $dry", last <= max(132f, dry) + 20f)
            if (l == Landscape.COAST && last > max(132f, dry) + 0.01f) pushed++
        }
        assertTrue("coasts whose last plot is further out: $pushed of ${seeds.size}", pushed <= seeds.size / 10)
    }

    @Test fun `Teo's village shows the sea and the beach at every age, on every canvas, from its home view`() {
        val dir = File("build/land-snapshots").apply { mkdirs() }
        try {
            Cultures.use("friuli")
            val founded = teo.village(1_000L)
            for (age in listOf(Age.OGENJ, Age.ZASELEK, Age.VAS, Age.MESTO)) {
                val s = grown(founded, age)
                val t = Terrain.of(s)
                for ((w, h) in canvases) {
                    val c = PixelCanvas(w, h)
                    VillageRenderer().render(c, s, Frame(time = 3.0, hour = 11f, month = 6, sea = true))
                    val comp = VillageLayout.composition(w, h, false)
                    // the sea's water and its beach's sand on the left of every row from the horizon to the fire's
                    val rows = (comp.horizon + 2..comp.fireY)
                    val seen = rows.count { y -> (0 until w / 4).any { x -> c.pixels[y * w + x] in SEA } }
                    val sand = rows.count { y -> (0 until w / 4).any { x -> c.pixels[y * w + x] in SAND } }
                    assertTrue("$age ${w}x$h: the sea on $seen of ${rows.count()} rows", seen >= rows.count() * 9 / 10)
                    assertTrue("$age ${w}x$h: the beach on $sand of ${rows.count()} rows", sand >= rows.count() * 3 / 4)
                    // the town view's home frames the beach and some of the water
                    val home = TownAnchors.home(s, w, h)
                    assertTrue("$age ${w}x$h: home $home leaves the sea out", home.left <= max(0f, comp.fireX + t.shoreX(0f) - 12f))
                    if (age == Age.OGENJ && w == 320) png(File(dir, "coast-teo-${w}x$h.png"), c)
                }
            }
        } finally {
            Cultures.use(Cultures.DEFAULT)
        }
    }

    @Test fun `the coast is kept through a tick, a sync and the node's copy`() {
        val founded = teo.village(1_000L)
        val ticked = GameEngine.tick(founded, LocalDate.of(2026, 10, 26), 2_000L, ContentPool(emptyList())).state
        assertEquals(founded.land, ticked.land)
        // what the app sends (PUT /game) and what the node hands back
        val sent = json.encodeToString(GameSnapshot.serializer(), GameSnapshot(0, ticked))
        val node = json.decodeFromString(GameSnapshot.serializer(), sent)
        assertEquals(Land("coast", 551323397), node.state.land)
        assertTrue(sent.contains("\"land\":{\"kind\":\"coast\",\"seed\":551323397}"))
        // the node's copy adopted on another start; the phone's cache after a PUT
        val adopted = GameSync.reconcile(null, GameSnapshot(1, node.state)) as GameSync.Decision.Adopt
        assertEquals(founded.land, adopted.snapshot.state.land)
        assertEquals(founded.land, GameSync.afterPut(ticked, ticked, GamePut.Saved(2)).state.land)
        assertEquals(founded.land, GameSync.decode(GameSync.encode(GameCache(2, ticked)))!!.state.land)
        // and drawn on it: the same land
        assertEquals(Terrain.of(founded), Terrain.of(adopted.snapshot.state))
    }

    /** [s] grown to [age], its plots built (tents at first, then huts and houses). */
    private fun grown(s: GameState, age: Age): GameState {
        val n = PLOTS_PER_AGE[age.ordinal]
        val types = if (age.ordinal <= Age.TABOR.ordinal) listOf(BuildingType.TENT, BuildingType.TENT)
        else List(n) { i -> listOf(BuildingType.HUT, BuildingType.FIELD, BuildingType.HOUSE, BuildingType.WELL)[i % 4] }
        return s.copy(
            age = age, villagers = 4 + n, morale = 70, fire = 70,
            buildings = types.take(n).mapIndexed { i, t -> Building("b$i", t, if (t.onPlot) i else NO_PLOT) },
        )
    }

    private fun png(f: File, c: PixelCanvas) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
        ImageIO.write(img, "png", f)
    }

    private companion object {
        val SEA = setOf(VillageRenderer.SURF, VillageRenderer.SHALLOWS, VillageRenderer.SEA_DEEP)
        val SAND = setOf(VillageRenderer.SAND_L, VillageRenderer.SAND_M, VillageRenderer.SAND_D, VillageRenderer.SAND_WET)
    }
}
