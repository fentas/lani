package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.villagers.Villager

class SceneWordsTest {
    private fun words(vararg ids: String) = ids.map { PackWord(it, it, it) }
    private val kitchen = Pack("v-kuhinji", "V kuhinji", words = words("miza", "kruh", "voda", "mleko", "sol", "juha", "jajce", "kroznik", "kozarec", "vilice", "noz", "krompir"))
    private val home = Pack("dom-in-hisa", "Dom", words = words("okno", "vrata", "stol"))
    private val scene = SceneSpec(
        id = "kuhinja", title = "V kuhinji", art = "kitchen", from = listOf("hut"), pack = "v-kuhinji",
        objects = listOf(
            SceneObject("window", "okno", pack = "dom-in-hisa"),
            SceneObject("table", "miza"),
            SceneObject("bread", "kruh"),
            SceneObject("chair", "stol", pack = "dom-in-hisa"),
            SceneObject("water", "voda"),
            SceneObject("milk", "mleko"),
        ),
    )

    @Test fun `a run takes the scene's own pack first, its unlearned words in scene order`() {
        val packs = mapOf("v-kuhinji" to kitchen.copy(learned = listOf("kruh", "sol")), "dom-in-hisa" to home)
        val (pack, run) = SceneWords.toLearn(scene, packs)!!
        assertEquals("v-kuhinji", pack.id)
        assertEquals(listOf("miza", "voda", "mleko"), run.map { it.id })
        assertEquals(1, SceneWords.learnedCount(scene, packs)) // kruh
        assertEquals(listOf("okno", "miza", "stol", "voda", "mleko"), SceneWords.left(scene, packs).map { it.word })
    }

    @Test fun `when the scene's pack is done the other pack's words come next`() {
        val packs = mapOf("v-kuhinji" to kitchen.copy(learned = listOf("miza", "kruh", "voda", "mleko")), "dom-in-hisa" to home.copy(learned = listOf("vrata")))
        val (pack, run) = SceneWords.toLearn(scene, packs)!!
        assertEquals("dom-in-hisa", pack.id)
        assertEquals(listOf("okno", "stol"), run.map { it.id })
    }

    @Test fun `nothing to learn when every word is known, or the pack isn't loaded`() {
        val all = mapOf("v-kuhinji" to kitchen.copy(learned = kitchen.words.map { it.id }), "dom-in-hisa" to home.copy(learned = listOf("okno", "stol")))
        assertNull(SceneWords.toLearn(scene, all))
        assertTrue(SceneWords.left(scene, all).isEmpty())
        assertEquals(6, SceneWords.learnedCount(scene, all))
        assertNull(SceneWords.toLearn(scene, emptyMap()))
        assertEquals(6, SceneWords.left(scene, emptyMap()).size) // not loaded: nothing counts as learned
        // Only the second pack loaded: its words.
        assertEquals(listOf("okno", "stol"), SceneWords.toLearn(scene, mapOf("dom-in-hisa" to home))!!.second.map { it.id })
    }

    @Test fun `a long list is learned six at a time, up to eight at once`() {
        val slots = listOf("stove", "pot", "bowl", "table", "chair", "bread", "plate", "glass", "cup", "spoon")
        val big = scene.copy(objects = kitchen.words.take(10).mapIndexed { i, w -> SceneObject(slots[i], w.id) })
        assertEquals(kitchen.words.take(6), SceneWords.toLearn(big, mapOf("v-kuhinji" to kitchen))!!.second)
        val eight = big.copy(objects = big.objects.take(8))
        assertEquals(8, SceneWords.toLearn(eight, mapOf("v-kuhinji" to kitchen))!!.second.size)
    }

    @Test fun `a word the pack doesn't have is left out of the run`() {
        val odd = scene.copy(objects = listOf(SceneObject("salt", "sol-morska"), SceneObject("table", "miza")))
        assertEquals(listOf("miza"), SceneWords.toLearn(odd, mapOf("v-kuhinji" to kitchen))!!.second.map { it.id })
        assertFalse(SceneWords.learned(odd, odd.objects[0], mapOf("v-kuhinji" to kitchen)))
    }

    @Test fun `rewards and voices`() {
        assertEquals(mapOf(Res.FOOD to 15, Res.WISDOM to 3), SceneWords.reward(mapOf("food" to 15, "Wisdom" to 3, "gold" to 9, "wood" to 0)))
        assertEquals(Clips.MALE, SceneWords.voiceOf("grandpa"))
        assertEquals(Clips.FEMALE, SceneWords.voiceOf("grandma"))
        assertEquals(Clips.FEMALE, SceneWords.voiceOf("child2"))
        // a villager speaks with their speaker, falling back to their gender; anyone else by the sprite
        val nejc = Villager("nejc", "Nejc", art = "child1", voice = "male", speaker = "boy")
        assertEquals("their own voice, by who they are", Spoken("boy", Clips.MALE, "nejc"), SceneWords.voiceOf("child1", nejc))
        assertEquals("an older bridge: no speaker", Spoken(Clips.MALE, Clips.MALE, "nejc"), SceneWords.voiceOf("child1", nejc.copy(speaker = null)))
        assertEquals(Spoken(Clips.FEMALE), SceneWords.voiceOf("child1", null))
    }
}
