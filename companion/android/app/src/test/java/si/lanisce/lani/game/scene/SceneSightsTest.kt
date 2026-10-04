package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.render.Moon
import java.time.LocalDateTime

/**
 * When a scene's things are in its picture ([SceneSights], companion/SCENES.md "The words panel"): the words panel's three
 * groups by day and by night, a word found at night seen before by day, and the hints of every condition. That the painters
 * draw by the same conditions: SceneSightsRenderTest.
 */
class SceneSightsTest {
    private fun thing(slot: String, emoji: String? = null, needs: SceneNeeds? = null) = SceneObject(slot, slot, needs = needs, sl = slot, emoji = emoji)

    private val campfire = SceneSpec(
        id = "ob-ognju", title = "Ob ognju", art = "campfire", from = listOf("fire"), pack = "ob-ognju",
        objects = listOf(thing("fire", "🔥"), thing("logs"), thing("stars", "✨"), thing("moon", "🌙"), thing("tent", "⛺")),
    )
    private val forest = SceneSpec(
        id = "v-gozdu", title = "V gozdu", art = "forest", from = listOf("forest"), pack = "v-gozdu",
        objects = listOf(thing("tree"), thing("owl", "🦉"), thing("hare", "🐇"), thing("bat", "🦇"), thing("boar", "🐗")),
    )

    private val noon = LocalDateTime.of(2026, 6, 20, 13, 0)
    private val midnight = LocalDateTime.of(2026, 6, 20, 23, 30)

    private fun at(t: LocalDateTime, world: SceneWorld = SceneWorld.ALL, moon: Float = Moon.FULL) = SceneSights.frame(t, world, village = 7L, moon = moon)

    private fun SceneSights.Groups.slots() = Triple(now.map { it.slot }, seen.map { it.first.slot }, hidden.map { it.first.slot })

    @Test fun `by day the campfire's stars and the full moon are still hidden, at night they are here now`() {
        val day = SceneSights.group(campfire, at(noon), found = setOf("fire"))
        assertEquals(Triple(listOf("fire", "logs", "tent"), emptyList<String>(), listOf("stars", "moon")), day.slots())
        assertEquals(listOf(SceneHint.NIGHT), day.hidden.first { it.first.slot == "stars" }.second)
        assertEquals(listOf(SceneHint.MOON), day.hidden.first { it.first.slot == "moon" }.second)
        assertEquals(listOf(SceneHint.NIGHT, SceneHint.MOON), day.hints)

        val night = SceneSights.group(campfire, at(midnight), found = setOf("fire"))
        assertEquals(Triple(listOf("fire", "logs", "stars", "moon", "tent"), emptyList<String>(), emptyList<String>()), night.slots())
        assertTrue(night.hints.isEmpty())
        // a new moon isn't in the sky at midnight either
        val dark = SceneSights.group(campfire, at(midnight, moon = 0f), found = emptySet())
        assertEquals(listOf("moon"), dark.slots().third)
    }

    @Test fun `a word found at night is seen before by day, with when it shows, and the rest stays hidden`() {
        val day = SceneSights.group(campfire, at(noon), found = setOf("fire", "stars"))
        assertEquals(listOf("stars" to listOf(SceneHint.NIGHT)), day.seen.map { it.first.slot to it.second })
        assertEquals(listOf("moon"), day.hidden.map { it.first.slot })
        // found and here now: in the picture's group, as always
        val night = SceneSights.group(campfire, at(midnight), found = setOf("fire", "stars"))
        assertTrue(night.now.any { it.slot == "stars" } && night.seen.isEmpty())
    }

    @Test fun `the forest's animals are here while they are out, the others wait for their time`() {
        val frame = at(noon).copy(wild = setOf("hare"))
        val g = SceneSights.group(forest, frame, found = setOf("bat"))
        assertEquals(Triple(listOf("tree", "owl", "hare"), listOf("bat"), listOf("boar")), g.slots())
        assertEquals(listOf(SceneHint.DUSK, SceneHint.NIGHT, SceneHint.NOT_WINTER, SceneHint.SOMETIMES), g.seen.single().second)
        assertEquals(listOf(SceneHint.NIGHT, SceneHint.SOMETIMES), g.hidden.single().second)
        // at night, the day's mix: whatever of them is out is here, the hare (by day) isn't
        val night = at(midnight)
        val out = Wildlife.out("forest", 7L, night.day, night.hour, 6)
        val gn = SceneSights.group(forest, night, found = emptySet())
        for (a in listOf("hare", "bat", "boar")) assertEquals(a, a in out, gn.now.any { it.slot == a })
        assertFalse(gn.now.any { it.slot == "hare" })
    }

    @Test fun `a dialog's cue that sends the bats away takes their word out of the picture`() {
        val bat = forest.objects.first { it.slot == "bat" }
        val frame = at(midnight).copy(wild = setOf("bat"))
        assertTrue(SceneSights.visibleNow("forest", bat, frame))
        assertFalse(SceneSights.visibleNow("forest", bat, frame.copy(fx = mapOf("bats" to 0f))))
        assertFalse(SceneSights.visibleNow("forest", bat, frame.copy(fx = mapOf("bats" to 0.1f))))
        assertTrue(SceneSights.visibleNow("forest", bat, frame.copy(fx = mapOf("bats" to 0.5f))))
        // a cue brings bats the day hasn't: scenery, not a word to tap
        assertFalse(SceneSights.visibleNow("forest", bat, frame.copy(wild = emptySet(), fx = mapOf("bats" to 1f))))
    }

    @Test fun `the market's pigeons flown off for the day are back tomorrow`() {
        val market = SceneSpec(id = "na-trznici", title = "Na tržnici", art = "market", from = listOf("market"), objects = listOf(thing("stall"), thing("pigeon", "🕊️")))
        val flying = SceneSights.group(market, at(noon).copy(fx = mapOf("pigeons" to 0.6f)), found = emptySet())
        assertEquals(listOf("stall", "pigeon"), flying.now.map { it.slot }) // in the air, still to tap
        val gone = SceneSights.group(market, at(noon).copy(fx = mapOf("pigeons" to 1f)), found = setOf("pigeon"))
        assertEquals(listOf("pigeon" to listOf(SceneHint.LATER)), gone.seen.map { it.first.slot to it.second })
        assertTrue(SceneSights.whenShown("market", thing("pigeon")).isEmpty())
    }

    @Test fun `hints for every condition`() {
        fun hints(art: String, slot: String, world: SceneWorld = SceneWorld.ALL, needs: SceneNeeds? = null) =
            SceneSights.whenShown(art, thing(slot, needs = needs), world)
        // the sky
        assertEquals(listOf(SceneHint.NIGHT), hints("campfire", "stars"))
        assertEquals(listOf(SceneHint.MOON), hints("campfire", "moon"))
        assertEquals(listOf(SceneHint.DAY), hints("square", "sun"))
        // the wild animals: the parts of the day, the months they're awake, the day's dice
        assertEquals(listOf(SceneHint.DAWN, SceneHint.DAY, SceneHint.DUSK, SceneHint.SOMETIMES), hints("forest", "hare"))
        assertEquals(listOf(SceneHint.DAY, SceneHint.NOT_WINTER, SceneHint.SOMETIMES), hints("forest", "butterfly"))
        assertEquals(listOf(SceneHint.DAWN, SceneHint.DUSK, SceneHint.SOMETIMES), hints("forest", "stag"))
        assertEquals(listOf(SceneHint.NIGHT, SceneHint.NOT_WINTER, SceneHint.SOMETIMES), hints("forest", "dormouse"))
        assertEquals(listOf(SceneHint.DAWN, SceneHint.SOMETIMES), hints("pond", "fox"))
        assertEquals(listOf(SceneHint.DAWN, SceneHint.DAY, SceneHint.SOMETIMES), hints("alps", "ibex"))
        // what the village builds: the kozolec, the mill (a project), the kitchen's clock (its level), an age
        assertEquals(listOf(SceneHint(SceneHint.Kind.BUILD, building = BuildingType.KOZOLEC)), hints("field", "kozolec", SceneWorld.NONE))
        assertEquals(listOf(SceneHint(SceneHint.Kind.PROJECT, project = "mlin")), hints("stream", "mill", SceneWorld.NONE))
        assertEquals(listOf(SceneHint(SceneHint.Kind.UPGRADE, level = 3)), hints("kitchen", "clock", SceneWorld.room(1)))
        assertEquals(listOf(SceneHint(SceneHint.Kind.AGE, age = Age.VAS)), hints("forest", "tree", SceneWorld.NONE, SceneNeeds(age = "vas")))
        assertEquals(
            listOf(SceneHint(SceneHint.Kind.BUILD, building = BuildingType.SMITHY, level = 2)),
            hints("forest", "tree", SceneWorld.NONE, SceneNeeds(building = "SMITHY", level = 2)),
        )
        // built, nothing to wait for; a thing that went with the upgrade (the open hearth) doesn't come back
        assertTrue(hints("field", "kozolec").isEmpty())
        assertTrue(hints("campfire", "fire").isEmpty())
        assertEquals(listOf(SceneHint.GONE), hints("kitchen", "hearth", SceneWorld.room(2)))
        // a slot's own need: the hay lies on the kozolec
        assertEquals(listOf(SceneHint(SceneHint.Kind.BUILD, building = BuildingType.KOZOLEC)), hints("field", "hay", SceneWorld.NONE))
    }

    @Test fun `what the village hasn't built is still hidden, what went with an upgrade only when it was found`() {
        val field = SceneSpec(id = "na-njivi", title = "Na njivi", art = "field", from = listOf("field"), objects = listOf(thing("wheat"), thing("kozolec", "🪜")))
        val g = SceneSights.group(field, at(noon, SceneWorld.NONE), found = emptySet())
        assertEquals(Triple(listOf("wheat"), emptyList<String>(), listOf("kozolec")), g.slots())
        assertEquals(BuildingType.KOZOLEC, g.hidden.single().second.single().building)

        val kitchen = SceneSpec(id = "v-kuhinji", title = "V kuhinji", art = "kitchen", from = listOf("hut"), objects = listOf(thing("pot"), thing("hearth"), thing("clock")))
        val level2 = at(noon, SceneWorld.room(2))
        assertEquals(Triple(listOf("pot"), emptyList<String>(), listOf("clock")), SceneSights.group(kitchen, level2, found = emptySet()).slots())
        val seen = SceneSights.group(kitchen, level2, found = setOf("hearth"))
        assertEquals(listOf("hearth" to listOf(SceneHint.GONE)), seen.seen.map { it.first.slot to it.second })
    }

    @Test fun `the picture's inputs come from the clock to the minute`() {
        val f = SceneSights.frame(LocalDateTime.of(2026, 12, 24, 17, 45), village = 3L)
        assertEquals(17.75f, f.hour, 1e-4f)
        assertEquals(12, f.month)
        assertEquals(java.time.LocalDate.of(2026, 12, 24).toEpochDay(), f.day)
        assertEquals(3L, f.village)
        assertEquals(Wildlife.out("forest", 3L, f.day, f.hour, 12), f.wildOf("forest"))
    }
}
