package si.lanisce.lani.ui.scene

import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Story
import si.lanisce.lani.game.villagers.Villager

/** Who reads a line: the speaker's voice, their gender to fall back to, and who they are (for their own voice). */
data class Spoken(val voice: String, val fallback: String? = null, val person: String? = null)

/**
 * A scene's words are pack words (companion/SCENES.md): what the learner knows of them, and which ones a
 * "learn these words" run takes. [packs] are the packs as the node serves them (id → pack with its
 * `learned` ids); a pack that isn't there (offline, never loaded) counts as nothing learned.
 */
object SceneWords {
    /** The pack [o]'s word is in: its own, else the scene's. */
    fun packOf(scene: SceneSpec, o: SceneObject): String? = o.pack ?: scene.pack

    fun learned(scene: SceneSpec, o: SceneObject, packs: Map<String, Pack>): Boolean {
        val p = packOf(scene, o) ?: return false
        return packs[p]?.learned?.contains(o.word) == true
    }

    /** How many of the scene's things have a learned word. */
    fun learnedCount(scene: SceneSpec, packs: Map<String, Pack>): Int = scene.objects.count { learned(scene, it, packs) }

    /** The scene's words not learned yet (one per word, in scene order). */
    fun left(scene: SceneSpec, packs: Map<String, Pack>): List<SceneObject> =
        scene.objects.filter { packOf(scene, it) != null && !learned(scene, it, packs) }.distinctBy { packOf(scene, it) + "/" + it.word }

    /**
     * The next "learn these words" run: the scene's unlearned words of one pack (the scene's own pack
     * first, then the others as they come), in scene order, as the pack's words. Like a pack run it takes
     * all of them when that's 8 or fewer, else 6. Null when every word is learned, or the pack isn't
     * loaded (its words can only be saved to a pack the node has).
     */
    fun toLearn(scene: SceneSpec, packs: Map<String, Pack>): Pair<Pack, List<PackWord>>? {
        val left = left(scene, packs)
        val order = left.mapNotNull { packOf(scene, it) }.distinct().sortedBy { if (it == scene.pack) 0 else 1 }
        for (id in order) {
            val pack = packs[id] ?: continue
            val byId = pack.words.associateBy { it.id }
            val words = left.filter { packOf(scene, it) == id }.mapNotNull { byId[it.word] }
            if (words.isEmpty()) continue
            return pack to if (words.size <= 8) words else words.take(6)
        }
        return null
    }

    /**
     * The next "learn the story's words" run: [story]'s words not learned yet of one pack (the one with the most of
     * them; the story's words come from their packs), in the story's order, all of them when 8 or fewer, else 6. Null
     * when every word is learned or no pack of them is loaded.
     */
    fun toLearn(story: Story, packs: Map<String, Pack>): Pair<Pack, List<PackWord>>? {
        val left = story.words.filter { w -> w.pack != null && packs[w.pack]?.learned?.contains(w.id) != true }
        for (id in left.groupBy { it.pack!! }.entries.sortedByDescending { it.value.size }.map { it.key }) {
            val pack = packs[id] ?: continue
            val byId = pack.words.associateBy { it.id }
            val words = left.filter { it.pack == id }.mapNotNull { byId[it.id] }
            if (words.isEmpty()) continue
            return pack to if (words.size <= 8) words else words.take(6)
        }
        return null
    }

    /** A happening's reward ("food" → 15) as village resources; unknown names are left out. */
    fun reward(r: Map<String, Int>): Map<Res, Int> =
        r.mapNotNull { (k, n) -> Res.entries.firstOrNull { it.name.equals(k, ignoreCase = true) }?.let { it to n } }
            .filter { it.second > 0 }
            .toMap()

    /** The voice a person's lines are read in (node clips) when they aren't a villager: by their sprite. */
    fun voiceOf(art: String): String = when (art) {
        "grandpa", "shepherd", "smith", "farmer", "beekeeper", "winemaker", "pedlar", "pilgrim", "hunter", "burner" -> Clips.MALE
        else -> Clips.FEMALE // grandma, aunt, teacher, innkeeper, and the children's higher voices
    }

    /**
     * Who reads a person's lines, for [si.lanisce.lani.data.Speaker.say]: their villager's speaker with
     * their gender to fall back to and who they are (their own voice), else their sprite's voice.
     */
    fun voiceOf(art: String, villager: Villager?): Spoken =
        villager?.let { Spoken(it.speakerVoice, it.voice, it.id) } ?: Spoken(voiceOf(art))
}
