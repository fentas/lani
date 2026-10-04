package si.lanisce.lani.game.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.scene.DaySky
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.Sky
import java.time.LocalDate

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
    }

    @Test fun `the fire is only where one burns`() {
        val fires = SceneArt.objects.keys.filter { scene(it, 12f, 6).of(Layer.FIRE) > 0f }.toSet()
        assertEquals(setOf("campfire", "kitchen", "smithy"), fires)
        assertTrue(scene("smithy", 12f, 6, fx = mapOf("forge" to 1f)).of(Layer.FIRE) > scene("smithy", 12f, 6).of(Layer.FIRE))
        assertTrue(scene("livingroom", 20f, 1, fx = mapOf("stove" to 1f)).of(Layer.FIRE) > 0f)
    }

    @Test fun `indoors the rain is fainter, in the cellar faintest, under the roof louder`() {
        val rain = Sky(rain = 0.7f)
        val out = scene("field", 12f, 6, rain).of(Layer.RAIN)
        val kitchen = scene("kitchen", 12f, 6, rain).of(Layer.RAIN)
        val attic = scene("attic", 12f, 6, rain).of(Layer.RAIN)
        val cellar = scene("cellar", 12f, 6, rain).of(Layer.RAIN)
        assertTrue("$cellar < $kitchen < $attic < $out", cellar < kitchen && kitchen < attic && attic < out)
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
