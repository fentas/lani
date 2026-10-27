package si.lanisce.lani.game.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Founding
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.render.Camera
import si.lanisce.lani.game.render.CameraRig
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.render.SceneFit
import si.lanisce.lani.game.render.Terrain
import si.lanisce.lani.game.render.TownAnchors
import si.lanisce.lani.game.render.VillageLayout
import si.lanisce.lani.game.scene.DaySky
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.Sky
import si.lanisce.lani.ui.game.HOME_ABOVE
import si.lanisce.lani.ui.game.freeArea
import java.time.LocalDate
import kotlin.math.log10
import kotlin.math.roundToInt

class SoundscapeTest {
    private fun scene(art: String, hour: Float, month: Int, sky: Sky = Sky.CLEAR, fx: Map<String, Float> = emptyMap()) =
        Soundscape.mix(Place.Scene(art, fx), hour, month, sky)

    private fun Map<Layer, Float>.of(l: Layer) = this[l] ?: 0f

    @Test fun `birds sing in the forest on a June noon, and nothing of the night`() {
        val m = scene("forest", 12.5f, 6)
        assertTrue(m.of(Layer.BIRDS) >= 0.8f)
        assertFalse(Layer.CRICKETS in m)
        assertFalse(Layer.OWL in m)
        assertFalse(Layer.RAIN in m)
    }

    @Test fun `on a July night the forest has crickets and the owl, and no birds`() {
        val m = scene("forest", 0.5f, 7)
        assertFalse(Layer.BIRDS in m)
        assertTrue(m.of(Layer.CRICKETS) > 0.2f)
        assertTrue(m.of(Layer.OWL) > 0.3f)
    }

    @Test fun `the dawn chorus is louder than noon, and dusk brings the crickets in slowly`() {
        val rise = Env(12f, 5, false).sunrise
        assertTrue(scene("field", rise + 0.5f, 5).of(Layer.BIRDS) > scene("field", 12.5f, 5).of(Layer.BIRDS))
        val set = Env(12f, 7, false).sunset
        val before = scene("stream", set - 0.5f, 7).of(Layer.CRICKETS)
        val dusk = scene("stream", set + 0.25f, 7).of(Layer.CRICKETS)
        val dark = scene("stream", set + 1.5f, 7).of(Layer.CRICKETS)
        assertTrue("$before < $dusk < $dark", before < dusk && dusk < dark)
    }

    @Test fun `a winter night has the owl but no crickets or frogs, and fewer birds by day`() {
        val night = Soundscape.mix(Place.Village(), 23f, 1)
        assertTrue(night.of(Layer.OWL) > 0.3f)
        assertFalse(Layer.CRICKETS in night)
        assertFalse(Layer.FROGS in scene("pond", 23f, 1))
        assertTrue(Soundscape.mix(Place.Village(), 12f, 1).of(Layer.BIRDS) < Soundscape.mix(Place.Village(), 12f, 5).of(Layer.BIRDS))
    }

    @Test fun `rain hushes the birds, and a heavy one is heard as rain`() {
        val clear = Soundscape.mix(Place.Village(), 12f, 6)
        val shower = Soundscape.mix(Place.Village(), 12f, 6, Sky(rain = 0.2f))
        val pour = Soundscape.mix(Place.Village(), 12f, 6, Sky(rain = 0.8f, wind = 0.4f))
        assertTrue(shower.of(Layer.BIRDS) < clear.of(Layer.BIRDS) && shower.of(Layer.BIRDS) > 0f)
        assertFalse(Layer.BIRDS in pour)
        assertTrue(shower.of(Layer.RAIN) in 0.3f..0.6f)
        assertTrue(pour.of(Layer.RAIN) > 0.8f)
        assertEquals(0.4f, pour.of(Layer.WIND), 0.001f)
        assertFalse(Layer.RAIN in clear)
    }

    @Test fun `the storm event rains over the village, else the sky a dialog left today`() {
        val today = LocalDate.of(2026, 9, 28)
        val storm = GameState(event = GameEvent("e", EventKind.STORM, 2, 0L, Long.MAX_VALUE))
        assertEquals(Soundscape.STORM, Soundscape.villageSky(storm, today))
        assertTrue(Soundscape.mix(Place.Village(), 12f, 9, Soundscape.villageSky(storm, today)).of(Layer.RAIN) > 0.8f)
        val shower = GameState(sky = DaySky(today.toString(), "na-njivi", Sky(rain = 0.6f)))
        assertEquals(Sky(rain = 0.6f), Soundscape.villageSky(shower, today))
        assertEquals(Sky.CLEAR, Soundscape.villageSky(shower, today.plusDays(1)))
        assertEquals(Sky.CLEAR, Soundscape.villageSky(null, today))
    }

    @Test fun `the campfire crackles day and night, and higher when it flares`() {
        val day = scene("campfire", 13f, 8).of(Layer.FIRE)
        assertTrue(day > 0.5f)
        assertEquals(day, scene("campfire", 1f, 8).of(Layer.FIRE), 0.001f)
        assertTrue(scene("campfire", 1f, 8, fx = mapOf("flare" to 1f)).of(Layer.FIRE) > day)
    }

    @Test fun `the fire's scene crackles as its fire burns, silent when it is out until a dialog lights it`() {
        val burning = Soundscape.mix(Place.Scene("campfire", fire = 1f), 20f, 8, Sky.CLEAR).of(Layer.FIRE)
        val coals = Soundscape.mix(Place.Scene("campfire", fire = 0.22f), 10f, 8, Sky.CLEAR).of(Layer.FIRE)
        assertTrue("the coals a murmur under the burning fire", coals in 0.01f..burning * 0.5f)
        assertEquals("out: silent", 0f, Soundscape.mix(Place.Scene("campfire", fire = 0f), 14f, 7, Sky.CLEAR).of(Layer.FIRE), 0.001f)
        assertTrue("lit by a dialog", Soundscape.mix(Place.Scene("campfire", mapOf("flare" to 1f), fire = 0f), 14f, 7, Sky.CLEAR).of(Layer.FIRE) > 0f)
        assertTrue("the kres louder", Soundscape.mix(Place.Scene("campfire", fire = 1.3f), 21f, 8, Sky.CLEAR).of(Layer.FIRE) > burning)
    }

    @Test fun `the water scenes have the stream, the mountains their brook and wind, the coast its sea`() {
        for (art in listOf("stream", "pond", "alps")) assertTrue(art, scene(art, 12f, 6).of(Layer.STREAM) > 0.2f)
        assertTrue(scene("stream", 12f, 6).of(Layer.STREAM) > scene("pond", 12f, 6).of(Layer.STREAM))
        val alps = scene("alps", 23f, 7)
        assertTrue(alps.of(Layer.WIND) > 0.3f)
        assertFalse("no crickets up there", Layer.CRICKETS in alps)
        assertTrue(scene("sea", 12f, 7).of(Layer.SEA) > 0.7f)
        val coast = Soundscape.mix(Place.Village(sea = true), 12f, 7).of(Layer.SEA)
        assertTrue(coast > 0f && coast < scene("sea", 12f, 7).of(Layer.SEA))
        assertFalse(Layer.SEA in Soundscape.mix(Place.Village(), 12f, 7))
        // a village on the coast hears its sea nearer than one with the sea only at its horizon, still less than the lagoon
        val beach = Soundscape.mix(Place.Village(sea = true, beach = true), 12f, 7).of(Layer.SEA)
        assertTrue(beach > coast && beach < scene("sea", 12f, 7).of(Layer.SEA))
    }

    /**
     * A loop at [level] (before its trim) at the default volume, in dB under the voices: they are at −18 LUFS, the loops are
     * made at −26 (GAME.md, "Background sounds"), and under a voice ([talking]) the whole ducks.
     */
    private fun underVoices(level: Float, layer: Layer, talking: Boolean = false): Float {
        val gain = level * layer.trim * AmbientBus.target(on = true, volume = AmbientBus.DEFAULT_VOLUME, talking = talking)
        return -18f - (-26f + 20f * log10(gain))
    }

    /**
     * Teo's coast (2026-10-26, "the sea sounds are way too loud"): the village on the coast played its sea at 0.6, louder
     * than its birds by day and its crickets at night. Now it is a quiet background under them and well under the voices,
     * fainter still where only the horizon shows the sea, nearer with the beach in view, and loudest in the sea's own scene.
     */
    @Test fun `the coast's sea is a quiet background in the village, under its birds, its crickets and the voices`() {
        val coast = Place.Village(sea = true, beach = true)
        val sea = Soundscape.mix(coast, 12f, 7).of(Layer.SEA)
        assertEquals(Soundscape.COAST_SEA, sea, 1e-6f)
        // at least 8 dB under the 0.6 it was at, the whole day through
        for (hour in 0 until 24) assertTrue("$hour h", 20f * log10(Soundscape.mix(coast, hour + 0.5f, 7).of(Layer.SEA) / 0.6f) <= -8f)
        // under the birds of a summer's noon and the crickets of a summer's night, after the trims
        fun heard(m: Map<Layer, Float>, l: Layer) = m.of(l) * l.trim
        val noon = Soundscape.mix(coast, 12f, 7)
        val night = Soundscape.mix(coast, 0.5f, 7)
        assertTrue("${heard(noon, Layer.SEA)} under the birds' ${heard(noon, Layer.BIRDS)}", heard(noon, Layer.SEA) < heard(noon, Layer.BIRDS) * 0.6f)
        assertTrue("${heard(night, Layer.SEA)} under the crickets' ${heard(night, Layer.CRICKETS)}", heard(night, Layer.SEA) < heard(night, Layer.CRICKETS) * 0.6f)
        // at the default volume about 40 dB under the voices, and 10 dB further under one while it speaks
        assertTrue("${underVoices(sea, Layer.SEA)} dB", underVoices(sea, Layer.SEA) >= 36f)
        assertTrue("${underVoices(sea, Layer.SEA, talking = true)} dB", underVoices(sea, Layer.SEA, talking = true) >= 46f)
        // the sea only at the horizon (a culture's backdrop): at least 6 dB fainter than the coast's
        val horizon = Soundscape.mix(Place.Village(sea = true), 12f, 7).of(Layer.SEA)
        assertTrue("$horizon", horizon > 0f && 20f * log10(horizon / sea) <= -6f)
        // with the beach in the middle of the view it comes nearer, about as loud as the birds at noon, and the lagoon's
        // scene louder still
        val beach = Soundscape.mix(coast.copy(beachView = 1f), 12f, 7).of(Layer.SEA)
        assertEquals(Soundscape.BEACH_SEA, beach, 1e-6f)
        assertTrue("$beach", 20f * log10(beach / sea) >= 6f && beach * Layer.SEA.trim <= heard(noon, Layer.BIRDS) * 1.1f)
        assertTrue(underVoices(beach, Layer.SEA) >= 30f)
        val lagoon = scene("sea", 12f, 7).of(Layer.SEA)
        assertTrue("$beach < $lagoon", 20f * log10(lagoon / beach) >= 4f)
        // half way there, half way between
        assertEquals((sea + beach) / 2f, Soundscape.mix(coast.copy(beachView = 0.5f), 12f, 7).of(Layer.SEA), 1e-6f)
        // the view counts only on the coast
        assertEquals(horizon, Soundscape.mix(Place.Village(sea = true, beachView = 1f), 12f, 7).of(Layer.SEA), 0f)
        assertFalse(Layer.SEA in Soundscape.mix(Place.Village(beachView = 1f), 12f, 7))
    }

    /** The sea ducks under a voice with the rest of the whole, from wherever it plays (the mixer: one bus for all loops). */
    @Test fun `the sea ducks under a voice with the rest`() {
        val mixer = AmbientMixer()
        mixer.load(Layer.SEA, ShortArray(AmbientMixer.RATE) { 10_000.toShort() })
        mixer.load(Layer.BIRDS, ShortArray(AmbientMixer.RATE) { 10_000.toShort() })
        val levels = Soundscape.mix(Place.Village(sea = true, beach = true), 12f, 7)
        val t = FloatArray(Layer.entries.size).also { levels.forEach { (l, v) -> it[l.ordinal] = v } }
        val quiet = AmbientBus.target(on = true, volume = AmbientBus.DEFAULT_VOLUME, talking = false)
        val ducked = AmbientBus.target(on = true, volume = AmbientBus.DEFAULT_VOLUME, talking = true)
        val out = ShortArray(480)
        repeat(AmbientMixer.RATE * 3 / out.size) { mixer.mix(out, t, quiet, AmbientBus.FADE_S) }
        val open = out.last().toFloat()
        assertEquals(10_000f * (t[Layer.SEA.ordinal] * Layer.SEA.trim + t[Layer.BIRDS.ordinal] * Layer.BIRDS.trim) * quiet, open, 2f)
        repeat(AmbientMixer.RATE / out.size) { mixer.mix(out, t, ducked, AmbientBus.DUCK_S) }
        assertEquals(open * AmbientBus.DUCK, out.last().toFloat(), 2f)
        // the sea alone ducks the same
        val sea = FloatArray(Layer.entries.size).also { it[Layer.SEA.ordinal] = t[Layer.SEA.ordinal] }
        repeat(AmbientMixer.RATE * 2 / out.size) { mixer.mix(out, sea, ducked, AmbientBus.DUCK_S) }
        assertEquals(10_000f * t[Layer.SEA.ordinal] * Layer.SEA.trim * ducked, out.last().toFloat(), 2f)
    }

    /**
     * Teo's village from its home view (the clearing with the beach and a strip of the sea at the view's edge, as the town
     * view frames it) hears the coast's quiet sea, at every age and on a phone held upright, a small one, a tablet and a phone
     * held sideways; zoomed in on the beach the sea is near, and in between it comes nearer in quarters.
     */
    @Test fun `the town view hears the sea nearer only on the beach`() {
        val teo = Founding("friuli", Landscape.COAST, 551323397L).village(0L)
        val land = Terrain.of(teo)
        var nearest = Float.MAX_VALUE
        for ((vw, vh) in listOf(1080 to 2400, 720 to 1600, 1600 to 2560, 2400 to 1080)) {
            val fit = SceneFit.town(vw, vh)
            val rig = CameraRig.town(fit, vw, vh)
            val comp = VillageLayout.composition(fit.width, fit.height, compact = false)
            fun heard(c: Camera) = Soundscape.beachView(land, fit.width, fit.height, c.toCanvasX(vw / 2f), c.toCanvasY(vh / 2f))
            for (age in Age.entries) {
                val s = teo.copy(age = age)
                val area = freeArea(vw.toFloat(), vh.toFloat(), top = vh * 0.12f, bottom = vh * 0.08f, margin = 36f)
                val home = rig.frame(TownAnchors.home(s, fit.width, fit.height), area, maxFrame = rig.minScale * 2, above = HOME_ABOVE)
                assertEquals("${vw}x$vh $age: home", 0f, heard(home), 0f)
                val row = maxOf(home.toCanvasY(vh / 2f), comp.horizon.toFloat()) - comp.fireY
                nearest = minOf(nearest, home.toCanvasX(vw / 2f) - comp.fireX - land.shoreX(row))
            }
            assertEquals("${vw}x$vh: fitted", 0f, heard(rig.fitted()), 0f)
            // the beach's sand in the middle of the view, zoomed in four times
            val sand = comp.fireX + land.shoreX(0f) + Terrain.BEACH / 2f
            val onBeach = rig.lookAt(sand, comp.fireY.toFloat(), rig.minScale * 4f)
            assertEquals("${vw}x$vh: on the beach", 1f, heard(onBeach), 0f)
            // a little inland of it, a step between, in quarters
            val inland = rig.lookAt(sand + 36f, comp.fireY.toFloat(), rig.minScale * 4f)
            val between = heard(inland)
            assertTrue("${vw}x$vh: $between", between > 0f && between < 1f && between * 4f == (between * 4f).roundToInt().toFloat())
        }
        println("Teo's home view: its middle at least $nearest px inland of the shore")
        // a land without the sea has no beach to look at
        val valley = Founding("friuli", Landscape.VALLEY, 551323397L).village(0L)
        assertEquals(0f, Soundscape.beachView(Terrain.of(valley), 360, 800, 10f, 200f), 0f)
        assertEquals(0f, Soundscape.beachView(land, 360, 800, Float.NaN, 200f), 0f)
    }

    /**
     * A lake's water, checked as the sea's (2026-10-26): the village by a lake plays no water of its own over the village
     * (its lake is still water, nothing to hear from above: only the birds, the crickets and the owl, as on any land), and
     * where still water is heard close up (the pond, the pasture's brook in the Alps by their lake) it stays under the voices
     * as the sea's background does on the coast, and ducks under a voice with the rest.
     */
    @Test fun `a lake's water is no louder than the coast's sea`() {
        val lake = Founding("kaernten", Landscape.LAKE, 551323397L).village(0L)
        val land = Terrain.of(lake)
        assertTrue(land.lake != null && land.sea == null)
        // what the town view of a lake village says it is (VillageSound): no beach, the culture's horizon without the sea
        val village = Place.Village(sea = false, beach = land.sea != null, beachView = Soundscape.beachView(land, 360, 800, 30f, 300f))
        for (hour in listOf(0.5f, 7f, 12f, 21f)) {
            val m = Soundscape.mix(village, hour, 7)
            for (l in listOf(Layer.SEA, Layer.STREAM, Layer.FROGS)) assertFalse("$hour h: $l over a lake village", l in m)
        }
        // still water close up: the pond's brook and the Alps' pasture brook, under the voices at least as far as the
        // coast's sea from the beach, and further still while one speaks
        val beach = underVoices(Soundscape.BEACH_SEA, Layer.SEA)
        for (art in listOf("pond", "alps")) {
            val brook = scene(art, 12f, 7).of(Layer.STREAM)
            assertTrue("$art: ${underVoices(brook, Layer.STREAM)} dB, the beach's sea $beach dB", underVoices(brook, Layer.STREAM) >= beach - 3f)
            assertTrue(underVoices(brook, Layer.STREAM, talking = true) - underVoices(brook, Layer.STREAM) >= 10f)
        }
    }

    @Test fun `the fire is only where one burns`() {
        val fires = SceneArt.objects.keys.filter { scene(it, 12f, 6).of(Layer.FIRE) > 0f }.toSet()
        assertEquals(setOf("campfire", "kitchen", "smithy"), fires)
        assertTrue(scene("smithy", 12f, 6, fx = mapOf("forge" to 1f)).of(Layer.FIRE) > scene("smithy", 12f, 6).of(Layer.FIRE))
        assertTrue(scene("livingroom", 20f, 1, fx = mapOf("stove" to 1f)).of(Layer.FIRE) > 0f)
    }

    @Test fun `indoors the rain is muffled, in the cellar faintest, under the roof loudest`() {
        val rain = Sky(rain = 0.7f)
        val out = scene("field", 12f, 6, rain).of(Layer.RAIN)
        // a room hears the rain through its walls and roof: the muffled loop, not the rain itself
        for (art in listOf("kitchen", "livingroom", "workshop", "school", "smithy", "attic", "cellar", "church")) {
            assertFalse(art, Layer.RAIN in scene(art, 12f, 6, rain))
            assertTrue(art, scene(art, 12f, 6, rain).of(Layer.RAIN_IN) > 0f)
        }
        val kitchen = scene("kitchen", 12f, 6, rain).of(Layer.RAIN_IN)
        val attic = scene("attic", 12f, 6, rain).of(Layer.RAIN_IN)
        val cellar = scene("cellar", 12f, 6, rain).of(Layer.RAIN_IN)
        assertTrue("$cellar < $kitchen < $attic", cellar < kitchen && kitchen < attic)
        assertTrue("$kitchen < $out", kitchen < out)
        assertFalse(Layer.RAIN_IN in scene("field", 12f, 6, rain))
        assertEquals(out, scene("tent", 12f, 6, rain).of(Layer.RAIN), 0.001f) // the canvas drums
        assertFalse(Layer.BIRDS in scene("church", 12f, 6))
        assertTrue(scene("kitchen", 12f, 6).of(Layer.BIRDS) < scene("square", 12f, 6).of(Layer.BIRDS))
    }

    @Test fun `grasshoppers chirp on a summer meadow by day, not in the forest`() {
        assertTrue(scene("field", 13f, 7).of(Layer.CRICKETS) > 0.2f)
        assertFalse(Layer.CRICKETS in scene("forest", 13f, 7))
        assertFalse(Layer.CRICKETS in scene("field", 13f, 3))
    }

    @Test fun `frogs at the pond on spring and summer nights, halved by day, not quieted by rain`() {
        val night = scene("pond", 23f, 5).of(Layer.FROGS)
        assertTrue(night > 0.6f)
        assertTrue(scene("pond", 13f, 5).of(Layer.FROGS) in 0.1f..(night / 2f))
        assertTrue(scene("pond", 23f, 5, Sky(rain = 0.8f)).of(Layer.FROGS) >= night * 0.5f)
        assertFalse(Layer.FROGS in scene("forest", 23f, 5))
    }

    @Test fun `a dialog's hoot brings the owl in by day`() {
        assertFalse(Layer.OWL in scene("forest", 12f, 10))
        assertTrue(scene("forest", 12f, 10, fx = mapOf("hoot" to 1f)).of(Layer.OWL) > 0.5f)
    }

    @Test fun `every art has its sounds, a newer one those of the open`() {
        val missing = SceneArt.objects.keys - Soundscape.SPOTS.keys
        assertTrue("arts without sounds: $missing", missing.isEmpty())
        assertEquals(Soundscape.mix(Place.Scene("forest"), 12f, 6).isEmpty(), false)
        val newer = scene("lighthouse", 12f, 6)
        assertEquals(Soundscape.OPEN.birds, newer.of(Layer.BIRDS), 0.001f)
    }

    @Test fun `every level is within 0 and 1, and a silent loop is left out`() {
        for (art in SceneArt.objects.keys + "lighthouse") for (month in 1..12) for (hour in 0 until 24) {
            for (sky in listOf(Sky.CLEAR, Sky(rain = 1f, wind = 1f), Sky(snow = 1f, fog = 1f))) {
                val m = scene(art, hour + 0.5f, month, sky, mapOf("flare" to 1f, "forge" to 1f, "waves" to 1f, "hoot" to 1f))
                for ((l, v) in m) assertTrue("$art $month $hour $l $v", v >= Soundscape.MIN && v <= 1f)
            }
        }
    }

    @Test fun `daylight is whole at noon, none at night, and in between at sunrise`() {
        assertEquals(1f, Soundscape.daylight(12.5f, 6), 0f)
        assertEquals(0f, Soundscape.daylight(2f, 6), 0f)
        assertEquals(0f, Soundscape.daylight(20f, 12), 0f)
        val rise = Env(12f, 3, false).sunrise
        assertTrue(Soundscape.daylight(rise, 3) in 0.1f..0.9f)
        // the same any hour of the clock, however it's written
        assertEquals(Soundscape.daylight(2f, 6), Soundscape.daylight(26f, 6), 0f)
    }
}
