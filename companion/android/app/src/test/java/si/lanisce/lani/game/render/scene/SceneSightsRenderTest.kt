package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneSights
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.Sky
import si.lanisce.lani.game.scene.Wildlife
import si.lanisce.lani.game.sky.SkyNow
import si.lanisce.lani.game.sky.SkyPlace
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The words panel's chips agree with the picture ([SceneSights], companion/SCENES.md "The words panel"): for every art, a
 * thing is "here now" exactly when its painter draws it as a thing to tap, over whole days in the four seasons, at the
 * moon's phases, under the real sky, in every weather, with the day's wild animals of a few villages and days, the
 * effects a dialog leaves, and what the village has built (nothing, a room's levels, everything).
 */
class SceneSightsRenderTest {
    private fun things(art: String) = SceneArt.objects.getValue(art).map { SceneObject(it, it) }

    private val skies = listOf(
        Sky(rain = 0.9f, gloom = 0.8f), Sky(snow = 0.9f, wind = 0.6f), Sky(fog = 0.9f), Sky(rain = 1f, wind = 1f, gloom = 1f, lightning = true),
    )

    /** The frames every art is checked in; [art]'s own (the moon's phases for the campfire, the wild animals' days). */
    private fun frames(art: String): List<SceneFrame> = buildList {
        val all = SceneArt.objects.getValue(art).toSet()
        val base = SceneFrame(time = 2.0, objects = all, village = 7L, day = LocalDate.of(2026, 6, 20).toEpochDay())
        // a whole day in each season, the wild animals all out (their hours are the day's mix's, below)
        val moons = if (art == "campfire") listOf(0f, 3.5f, 7.4f, Moon.FULL, 22f, 26f) else listOf(Moon.FULL)
        for (month in listOf(1, 4, 7, 10)) for (hh in 0 until 48) for (moon in moons) {
            add(base.copy(time = 1.0 + hh, hour = hh / 2f + 0.1f, month = month, moon = moon, wild = Wildlife.all(art)))
        }
        // every month round dawn, noon, dusk and midnight; the weathers
        for (month in 1..12) for (hour in listOf(6.5f, 12f, 19.25f, 23.5f)) {
            add(base.copy(hour = hour, month = month, wild = Wildlife.all(art)))
            for (sky in skies) add(base.copy(hour = hour, month = month, sky = sky))
        }
        // the day's wild animals of two villages over five days in three seasons, round the clock
        if (Wildlife.animals.containsKey(art)) for (village in listOf(1L, 42L)) for (date in listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 5, 3), LocalDate.of(2026, 8, 14))) {
            for (d in 0 until 5) for (hour in 0 until 24 step 2) add(base.copy(hour = hour + 0.3f + d % 2, month = date.monthValue, village = village, day = date.toEpochDay() + d))
        }
        // the effects a dialog leaves for the day, one at a time, low and high
        for (fx in SceneArt.effects[art].orEmpty()) for (level in listOf(0f, 0.1f, 0.6f, 1f)) for (hour in listOf(12f, 22.5f)) {
            add(base.copy(hour = hour, fx = mapOf(fx to level), wild = Wildlife.all(art)))
            add(base.copy(hour = hour, fx = mapOf(fx to level), wild = emptySet()))
        }
        // the real sky over the village: the real moon, up and down
        for (h in 0 until 24 step 3) for (date in listOf(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 12, 29))) {
            val millis = date.atTime(h, 20).toInstant(ZoneOffset.ofHours(1)).toEpochMilli()
            add(base.copy(hour = h + 0.33f, month = date.monthValue, skyNow = SkyNow.at(millis, SkyPlace.DEFAULT), wild = emptySet()))
        }
        // what the village has built: nothing, a room at each level, everything
        for (world in listOf(SceneWorld.NONE, SceneWorld.room(1), SceneWorld.room(2), SceneWorld.room(3), SceneWorld.ALL)) for (hour in listOf(12f, 23f)) {
            add(base.copy(hour = hour, world = world, wild = Wildlife.all(art)))
        }
    }

    @Test fun `every art's things are here now exactly while its painter draws them`() {
        var checked = 0
        for (art in SceneArt.arts) {
            val painter = ScenePainters.create(art)
            val c = PixelCanvas(240, 160)
            val objects = things(art)
            for (frame in frames(art)) {
                val hits = painter.render(c, frame)
                for (o in objects) {
                    val drawn = hits.any { it.target == SceneTarget.Thing(o.slot) }
                    val chip = SceneSights.visibleNow(art, o, frame)
                    assertEquals("$art/${o.slot} at ${frame.hour} h in month ${frame.month}, ${frame.sky}, fx ${frame.fx}, world ${frame.world.age}/${frame.world.hereLevel}, moon ${frame.moon}${if (frame.skyNow != null) " (real)" else ""}", drawn, chip)
                    checked++
                }
            }
        }
        assertTrue("checked $checked", checked > 20_000)
    }

    @Test fun `a thing the picture doesn't show has a hint of when it comes`() {
        // whatever isn't there at some hour, in some season or build, has a hint for it in that build
        for (art in SceneArt.arts) for (o in things(art)) for (frame in frames(art)) {
            if (!SceneSights.visibleNow(art, o, frame)) assertTrue("$art/${o.slot}", SceneSights.whenShown(art, o, frame.world, frame.fx).isNotEmpty())
        }
    }
}
