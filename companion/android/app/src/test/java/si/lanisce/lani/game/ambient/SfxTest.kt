package si.lanisce.lani.game.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.Sky
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SfxTest {
    private fun poke(art: String, id: String, step: Int, start: Double = 20.0) = Sfx.poke(art, id, step, start)

    @Test fun `the square's bell tapped rings at each end of its swing, as long as it swings, dying down`() {
        val start = 20.0
        val cues = poke("square", "tower", 0, start)
        assertTrue("${cues.size} strikes", cues.size in 4..6)
        for (c in cues) {
            assertEquals(Sound.BELL, c.sound)
            // the painter swings it as sin(5.5 t): a strike where it's at either end
            assertEquals("at ${c.at}", 1.0, abs(sin(c.at * 5.5)), 1e-9)
            assertTrue(c.at > start && c.at < start + 3.2)
            assertTrue(c.gain in 0f..1f)
        }
        for ((a, b) in cues.zipWithNext()) assertEquals(PI / 5.5, b.at - a.at, 1e-9)
        assertTrue("it dies down", cues.last().gain < cues.maxOf { it.gain })
        // the church's rope: its bell swings slower, and longer the second time
        val rope = poke("church", "rope", 0, start)
        for ((a, b) in rope.zipWithNext()) assertEquals(PI / 4.2, b.at - a.at, 1e-9)
        assertTrue(poke("church", "rope", 1, start).last().at > rope.last().at)
    }

    @Test fun `the anvil rings once, then twice`() {
        assertEquals(listOf(20.0), poke("smithy", "anvil", 0).map { it.at })
        assertEquals(listOf(20.0, 20.65), poke("smithy", "anvil", 1).map { it.at })
        assertTrue(poke("smithy", "anvil", 1).all { it.sound == Sound.ANVIL })
    }

    @Test fun `the cow's bell clanks as she shakes her head, her lowing and the others have no sound`() {
        assertEquals(emptyList<Cue>(), poke("alps", "cow", 0))
        val shake = poke("alps", "cow", 1)
        assertTrue(shake.size >= 8)
        assertTrue(shake.all { it.sound == Sound.COWBELL && it.at in 20.0..22.6 })
        for ((a, b) in shake.zipWithNext()) assertEquals(PI / 14, b.at - a.at, 1e-9)
        assertTrue("dying down", shake.last().gain < shake.first().gain)
        // the two sides of the bell
        assertTrue(shake[0].rate != shake[1].rate)
        for ((art, id) in listOf("square" to "dog", "field" to "scarecrow", "market" to "hen", "watchtower" to "horn", "kitchen" to "nothing")) {
            for (step in 0..2) assertEquals("$art/$id", emptyList<Cue>(), poke(art, id, step))
        }
    }

    @Test fun `the marmot whistles, the owl hoots at the third tap, the pot rattles, the cat purrs at the second`() {
        assertEquals(listOf(Cue(Sound.WHISTLE, 20.3)), poke("alps", "marmot", 0))
        assertEquals(emptyList<Cue>(), poke("alps", "marmot", 1))
        assertEquals(emptyList<Cue>(), poke("forest", "owl", 0))
        assertEquals(listOf(Sound.HOOT), poke("forest", "owl", 2).map { it.sound })
        assertEquals(20.6, poke("forest", "owl", 2).single().at, 1e-9)
        assertEquals(listOf(Sound.LID), poke("kitchen", "pot", 0).map { it.sound })
        assertEquals(20.42, poke("kitchen", "pot", 1).single().at, 1e-9)
        assertEquals(emptyList<Cue>(), poke("kitchen", "cat", 0))
        assertEquals(listOf(Sound.PURR), poke("kitchen", "cat", 1).map { it.sound })
        assertEquals(emptyList<Cue>(), poke("kitchen", "cat", 2))
        assertTrue(poke("campfire", "fire", 1).single().gain > poke("campfire", "fire", 0).single().gain)
    }

    @Test fun `a dialog's bell rings a peal as it comes on, not again while it stays, and again after it stopped`() {
        val now = 50.0
        val peal = Sfx.fx("square", emptyMap(), mapOf("bell" to 1f), now)
        assertTrue(peal.size >= 5)
        assertTrue(peal.all { it.sound == Sound.BELL && it.at > now && it.at < now + Sfx.PEAL_S })
        for (c in peal) assertEquals(1.0, abs(sin(c.at * 5.5)), 1e-9)
        assertTrue("the last strikes fade", peal.last().gain < peal.first().gain)
        assertEquals(emptyList<Cue>(), Sfx.fx("square", mapOf("bell" to 1f), mapOf("bell" to 1f), now))
        assertEquals(emptyList<Cue>(), Sfx.fx("square", mapOf("bell" to 1f), mapOf("bell" to 0f), now))
        assertEquals(emptyList<Cue>(), Sfx.fx("square", mapOf("bell" to 0.9f), mapOf("bell" to 1f), now))
        assertEquals(peal.size, Sfx.fx("square", mapOf("bell" to 0f), mapOf("bell" to 1f), now).size)
        // half as loud at half the level
        val soft = Sfx.fx("square", emptyMap(), mapOf("bell" to 0.5f), now)
        assertEquals(peal.first().gain / 2, soft.first().gain, 1e-5f)
        // over the field the village's bell comes from far off, as its rings go out (every 1 / 1.1 s)
        val far = Sfx.fx("field", emptyMap(), mapOf("bell" to 1f), now)
        assertTrue(far.isNotEmpty() && far.all { it.sound == Sound.BELL_FAR })
        for (c in far) assertEquals(0.0, (c.at * 1.1) - Math.round(c.at * 1.1), 1e-9)
        // the market's hand bell: higher and quicker, softer
        val hand = Sfx.fx("market", emptyMap(), mapOf("bell" to 1f), now)
        assertTrue(hand.all { it.rate > 1.4f && it.gain <= 0.6f })
        // an art without a bell of its own rings nothing
        assertEquals(emptyList<Cue>(), Sfx.fx("kitchen", emptyMap(), mapOf("bell" to 1f), now))
    }

    @Test fun `the fires catch with a whoosh, the campfire as it flares up, the cows' bells and the smith's blows`() {
        val now = 10.0
        assertEquals(listOf(Sound.WHOOSH), Sfx.fx("kitchen", emptyMap(), mapOf("oven" to 1f), now).map { it.sound })
        assertEquals(listOf(Sound.WHOOSH), Sfx.fx("livingroom", emptyMap(), mapOf("stove" to 1f), now).map { it.sound })
        assertEquals(listOf(Sound.WHOOSH), Sfx.fx("smithy", emptyMap(), mapOf("forge" to 1f), now).map { it.sound })
        assertEquals(1, Sfx.fx("campfire", mapOf("flare" to 0.3f), mapOf("flare" to 1f), now).size)
        assertEquals("a small rise is no whoosh", 0, Sfx.fx("campfire", mapOf("flare" to 0.3f), mapOf("flare" to 0.5f), now).size)
        assertEquals("nor dying down", 0, Sfx.fx("campfire", mapOf("flare" to 1f), mapOf("flare" to 0.3f), now).size)
        val herd = Sfx.fx("alps", emptyMap(), mapOf("cowbells" to 1f), now)
        assertTrue(herd.size >= 6 && herd.all { it.sound == Sound.COWBELL && it.at in now..now + 2.5 })
        assertEquals(herd.sortedBy { it.at }, herd)
        val blows = Sfx.fx("smithy", emptyMap(), mapOf("sparks" to 1f), now)
        assertTrue(blows.size >= 3 && blows.all { it.sound == Sound.ANVIL })
        // on the painter's blows: every 0.62 s
        for (c in blows) assertEquals(0.0, c.at / 0.62 - Math.round(c.at / 0.62), 1e-9)
        // effects without a sound
        assertEquals(0, Sfx.fx("tent", emptyMap(), mapOf("lantern" to 1f), now).size)
        assertEquals(0, Sfx.fx("kitchen", emptyMap(), mapOf("steam" to 1f), now).size)
    }

    @Test fun `every sound is of a poke or an effect its art has, never above its gain, and ready before it's wanted`() {
        val pokes = mapOf(
            "square" to listOf("tower"), "church" to listOf("rope"), "smithy" to listOf("anvil"), "alps" to listOf("cow", "marmot"),
            "forest" to listOf("owl"), "kitchen" to listOf("pot", "cat"), "campfire" to listOf("fire"),
        )
        for ((art, ids) in pokes) for (id in ids) {
            assertTrue("$art has a poke $id", ScenePainters.of(art).pokes.any { it.id == id })
            for (step in 0..2) for (c in Sfx.poke(art, id, step, 3.0)) {
                assertTrue("$art/$id: ${c.sound} ready", c.sound in Sfx.sounds(Place.Scene(art), Sky.CLEAR))
                assertTrue(c.gain in 0f..1f && c.rate in 0.5f..2f && c.at >= 3.0)
            }
        }
        val effects = SceneArt.effects.flatMap { (art, names) -> names.map { art to it } }
        for (art in Sfx.BELLS.keys) assertTrue("$art has a bell", "bell" in SceneArt.effects[art].orEmpty())
        for ((art, name) in effects) for (c in Sfx.fx(art, emptyMap(), mapOf(name to 1f), 3.0)) {
            assertTrue("$art $name: ${c.sound} ready", c.sound in Sfx.sounds(Place.Scene(art), Sky.CLEAR))
            assertTrue(c.gain in 0f..1f && c.rate in 0.5f..2f && c.at >= 3.0)
        }
        // every effect with a sound is one an art has
        for (name in listOf("cowbells", "oven", "stove", "forge", "flare", "sparks")) assertTrue(name, effects.any { it.second == name })
        // the thunder is ready where it flashes
        assertTrue(Sound.THUNDER_2 in Sfx.sounds(Place.Village(storm = true), Sky.CLEAR))
        assertTrue(Sound.THUNDER_1 !in Sfx.sounds(Place.Scene("field"), Sky.CLEAR))
        assertTrue(Sound.THUNDER_3 in Sfx.sounds(Place.Scene("field"), Sky(lightning = true)))
    }

    @Test fun `a swinging bell strikes where its sine is at either end`() {
        val t = Sfx.strikes(5.0, 1.0, 11.0)
        assertTrue(t.first() >= 1.0 && t.last() < 11.0)
        for (x in t) assertEquals(1.0, abs(sin(5.0 * x)), 1e-9)
        assertTrue("${t.size}", abs(t.size - (11.0 - 1.0) / (PI / 5.0)) <= 1.0)
        val shifted = Sfx.strikes(7.0, 0.0, 3.0, phase = 2.1)
        for (x in shifted) assertEquals(1.0, abs(sin(7.0 * x + 2.1)), 1e-9)
        assertEquals(emptyList<Double>(), Sfx.strikes(5.0, 2.0, 2.0))
    }
}
