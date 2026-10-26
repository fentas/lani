package si.lanisce.lani.game.render

import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Founding
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.culture.Cultures
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * A coast's sea runs from the beach to the horizon as one water (GAME.md, "The land"; docs/plans/05-built/coast-horizon.md;
 * [Sea]): the land's sea comes up to the horizon's row, and beyond it the far sea goes on, the same water, up to its line
 * against the sky. Until 2026-10-26 a strip of the hills behind the village showed between the two on the left (the far sea
 * was a band 6 nominal px above the horizon, laid out to stand behind the far treeline, which a coast leaves out where its
 * sea comes up), dark at night, and the far sea read as another sea moving behind the village (Teo's coast, Friuli, land
 * seed 551323397, seen on a visit with a phone held upright).
 */
class CoastHorizonTest {
    private val teo = Founding("friuli", Landscape.COAST, 551323397L)

    /** The town's canvases: a phone upright, a tablet on its side (Teo's), a phone on its side, a tall tablet, the founding's preview. */
    private val views = listOf(1080 to 2400, 2560 to 1600, 2400 to 1080, 1600 to 2560)

    /** By day, at dusk and at night in August (no falling leaves over the sea to count). */
    private val times = listOf("day" to 11f, "dusk" to 20.3f, "night" to 22.5f)

    /** [s] grown to [age], its plots built. */
    private fun grown(s: GameState, age: Age): GameState {
        val n = PLOTS_PER_AGE[age.ordinal]
        val types = if (age.ordinal <= Age.TABOR.ordinal) listOf(BuildingType.TENT, BuildingType.TENT)
        else List(n) { i -> listOf(BuildingType.HUT, BuildingType.FIELD, BuildingType.HOUSE, BuildingType.WELL)[i % 4] }
        return s.copy(age = age, villagers = 4 + n, morale = 70, fire = 70, buildings = types.take(n).mapIndexed { i, t -> Building("b$i", t, if (t.onPlot) i else NO_PLOT) })
    }

    /**
     * Every colour the sea may have under [env]: the water, its glints, the shallows and the surf, with any share of the
     * sky's haze, lit as the land is lit; and its line against the sky.
     */
    private fun seaColours(env: Env): Set<Int> {
        val out = HashSet<Int>()
        for (b in listOf(Pal.WATER_M, Pal.WATER_L, VillageRenderer.SEA_DEEP, VillageRenderer.SHALLOWS, VillageRenderer.SURF))
            for (i in 0..(Sea.HAZE_FAR / Sea.STEP).toInt() + 1) out += env.lit(Col.mix(b, env.skyHorizon, i * Sea.STEP))
        out += Sea.line(env)
        return out
    }

    /**
     * The strip Jan saw: on the sea's side of the horizon, from just under the far sea's line down past the horizon's row,
     * the pixels are the sea's (left of the shore there, off the beach; but for a stray one, a bird flying over), on every
     * canvas of the town, at every detail, by day, at dusk and at night. With the strip about two fifths of them weren't.
     */
    @Test fun `the sea runs up to its line against the sky, with no strip of the hills between`() {
        try {
            Cultures.use("friuli")
            val states = listOf(teo.village(1_000L).copy(morale = 70, fire = 70), grown(teo.village(1_000L), Age.VAS)) +
                listOf(7L, 42L, -5L).map { grown(teo.village(1_000L).copy(land = Land.of(Landscape.COAST, it)), Age.ZASELEK) }
            for (s in states) for ((vw, vh) in views) for ((name, hour) in times) for (k in 1..3) {
                val shot = Shot(s, vw, vh, hour, k)
                val off = shot.notSea(seaColours(shot.env))
                assertTrue("${Terrain.of(s).key} ${shot.tag} $name: no strip to look at", shot.region > 100)
                assertTrue("${Terrain.of(s).key} ${shot.tag} $name: ${off.size} of ${shot.region} px of the strip aren't the sea's, e.g. ${off.take(3)}", off.size <= shot.region / 100)
            }
        } finally {
            Cultures.use(Cultures.DEFAULT)
        }
    }

    /**
     * Where the far sea meets the land's at the horizon's row there is no edge: the two rows either side differ no more
     * than the rows of the far sea or of the land's sea do from each other (the middle of their columns' differences: a
     * glint or a streak of rain is no edge), in the storm's grey and the winter's light too.
     */
    @Test fun `the far sea and the land's sea join without an edge`() {
        val s = grown(teo.village(1_000L), Age.VAS)
        val storm = s.copy(event = GameEvent("e", EventKind.STORM, 3, 0, 0))
        for ((vw, vh) in views) for ((state, hour, month) in listOf(Triple(s, 11f, 8), Triple(s, 20.3f, 8), Triple(s, 22.5f, 8), Triple(storm, 14f, 8), Triple(s, 11f, 1))) {
            val shot = Shot(state, vw, vh, hour, 1, month)
            val seam = shot.step(shot.h - 1)
            val far = (shot.h - 5 until shot.h - 1).maxOf { shot.step(it) }
            val near = (shot.h + 1 until shot.h + 6).maxOf { shot.step(it) }
            assertTrue("${shot.tag} at $hour/$month: the seam differs by $seam, the far rows by up to $far, the near by up to $near", seam <= maxOf(far, near) + 6.0)
        }
    }

    /** The lake's water is drawn on the land, well below the horizon, on every canvas of the town: nothing cuts it there. */
    @Test fun `a lake keeps well below the horizon`() {
        for (seed in (1L..300L) + listOf(551323397L, 42L, -5L)) {
            val t = Terrain.of(Land.of(Landscape.LAKE, seed))
            val l = t.lake!!
            // its far shore, the noise of its shore at its most (lakeD's 0.1 of its radii) taken in
            val top = l[1] - l[3] * 1.12f
            assertTrue("lake:$seed: its far shore $top px from the fire", top > 8f - VillageLayout.HORIZON)
        }
    }

    /** The classic land and a valley keep the horizon they had: the far sea of a culture by the sea only behind the treeline. */
    @Test fun `the classic land and the dry lands keep their horizon`() {
        try {
            Cultures.use("friuli")
            for (land in listOf(Land.CLASSIC, Land(Landscape.VALLEY.id, 551323397), Land.of(Landscape.HILLS, 42L), Land.of(Landscape.LAKE, 42L))) {
                val s = grown(teo.village(1_000L).copy(land = land), Age.VAS)
                val shot = Shot(s, 1080, 2400, 22f, 1)
                // the treeline stands along the whole horizon, the far sea's foot behind it
                for (x in 0 until shot.c.width) assertTrue("${land.kind}: the treeline at $x", shot.c.pixels[(shot.h + 1) * shot.c.width + x] !in seaColours(shot.env))
            }
        } finally {
            Cultures.use(Cultures.DEFAULT)
        }
    }

    /** A picture of [s] on the town's canvas for a [vw] × [vh] screen, looking at the sea's horizon at detail [k]. */
    private class Shot(val s: GameState, vw: Int, vh: Int, val hour: Float, val k: Int, val month: Int = 8) {
        val fit = SceneFit.town(vw, vh)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val land = Terrain.of(s)
        val lens: Lens
        val c = PixelCanvas(fit.width, fit.height)
        val env = Env(hour, month, s.event?.kind == EventKind.STORM)
        /** The horizon's row (nominal) and the shore's x there. */
        val h = comp.horizon
        val shore = comp.fireX + land.shoreX((comp.horizon - comp.fireY).toFloat())
        val tag = "${fit.width}x${fit.height} k=$k"

        init {
            val rig = CameraRig.town(fit, vw, vh)
            val cam = rig.lookAt(shore - 10f, h + 20f, (rig.minScale * k).toFloat())
            lens = Lens.of(k, cam, vw, vh, fit.width, fit.height, fit.width, fit.height)
            VillageRenderer().render(c, s, Frame(time = 2.0, hour = hour, month = month, lens = lens, sea = true, calm = true))
        }

        /** How many canvas pixels the strip's region has (see [notSea]). */
        var region = 0

        /**
         * The canvas pixels of the strip's region that aren't one of [sea]'s colours: on the sea's side of the horizon (left
         * of the shore, 8 px off the beach), from 2 nominal rows under the far sea's line down to 6 rows under the horizon.
         */
        fun notSea(sea: Set<Int>): List<String> {
            val out = ArrayList<String>()
            region = 0
            for (y in 0 until c.height) {
                val by = lens.by(y + 0.5f)
                if (by < h - Sea.FAR_ROWS + 2f || by > h + 6f) continue
                for (x in 0 until c.width) {
                    val bx = lens.bx(x + 0.5f)
                    if (bx >= shore - 8f) continue
                    region++
                    val p = c.pixels[y * c.width + x]
                    if (p !in sea) out += "($x, $y: nominal %.1f, %.1f) #%06X".format(bx, by, p and 0xFFFFFF)
                }
            }
            return out
        }

        /**
         * How much nominal row [y] differs from the row below it on the sea's side: the middle of its columns' differences
         * (each the mean of the channels').
         */
        fun step(y: Int): Double {
            val d = (0 until (shore - 8f).toInt()).map { x ->
                val a = c.pixels[y * c.width + x]; val b = c.pixels[(y + 1) * c.width + x]
                (abs(Col.r(a) - Col.r(b)) + abs(Col.g(a) - Col.g(b)) + abs(Col.b(a) - Col.b(b))) / 3.0
            }.sorted()
            return if (d.isEmpty()) 0.0 else d[d.size / 2]
        }
    }

    @Test fun `render the coast's horizon for review`() {
        val dir = File("build/land-snapshots").apply { mkdirs() }
        try {
            Cultures.use("friuli")
            val s = teo.village(1_000L).copy(morale = 70, fire = 70)
            for ((name, hour) in times) for ((vw, vh, k) in listOf(Triple(1080, 2400, 1), Triple(1080, 2400, 3), Triple(2560, 1600, 1), Triple(2560, 1600, 2))) {
                val shot = Shot(s, vw, vh, hour, k)
                val rows = if (k == 1) shot.h + 40 else shot.c.height
                val scale = if (k == 1) 3 else 1
                val img = BufferedImage(shot.c.width * scale, rows * scale, BufferedImage.TYPE_INT_RGB)
                for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, shot.c.pixels[(y / scale) * shot.c.width + x / scale])
                ImageIO.write(img, "png", File(dir, "coast-horizon-$name-${shot.fit.width}x${shot.fit.height}-k$k.png"))
            }
        } finally {
            Cultures.use(Cultures.DEFAULT)
        }
    }
}
