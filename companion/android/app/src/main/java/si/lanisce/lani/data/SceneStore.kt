package si.lanisce.lani.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.parseScenes
import java.io.File

/**
 * The village scenes on the phone, so they work offline: the node's list, the packs their words come
 * from (with the ids the learner already knows), and which things the learner found in each scene.
 * Plain files in [dir] (the app's filesDir), written atomically like the village cache.
 */
class SceneStore(private val dir: File) {
    private val scenes = File(dir, "scenes.json")
    private val packs = File(dir, "scenes-packs.json")
    private val found = File(dir, "scenes-found.json")

    fun readScenes(): List<SceneSpec>? = read(scenes)?.let { runCatching { parseScenes(it) }.getOrNull() }

    fun writeScenes(list: List<SceneSpec>) = write(scenes, json.encodeToString(ListSerializer(SceneSpec.serializer()), list))

    fun readPacks(): Map<String, Pack> =
        read(packs)?.let { runCatching { json.decodeFromString(PACKS, it) }.getOrNull() }.orEmpty()

    fun writePacks(m: Map<String, Pack>) = write(packs, json.encodeToString(PACKS, m))

    fun readFound(): Map<String, Set<String>> =
        read(found)?.let { runCatching { json.decodeFromString(FOUND, it) }.getOrNull() }.orEmpty()
            .mapValues { it.value.toSet() }

    fun writeFound(m: Map<String, Set<String>>) = write(found, json.encodeToString(FOUND, m.mapValues { it.value.sorted() }))

    private fun read(f: File): String? = synchronized(LOCK) { runCatching { f.readText() }.getOrNull() }

    private fun write(f: File, raw: String) = synchronized(LOCK) {
        dir.mkdirs()
        val tmp = File(dir, "${f.name}.tmp")
        tmp.writeText(raw)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    private companion object {
        val LOCK = Any()
        val PACKS = MapSerializer(String.serializer(), Pack.serializer())
        val FOUND = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    }
}

/** What the learner found in the scenes: scene id → object slots tapped at least once. */
object SceneFound {
    /** [found] with [slot] of [scene] found; the same map when it's not one of the scene's things or already found. */
    fun discover(found: Map<String, Set<String>>, scene: SceneSpec, slot: String): Map<String, Set<String>> {
        if (scene.objects.none { it.slot == slot }) return found
        val have = found[scene.id].orEmpty()
        if (slot in have) return found
        return found + (scene.id to have + slot)
    }

    /** How many of [scene]'s things are found (slots the scene no longer has don't count). */
    fun count(found: Map<String, Set<String>>, scene: SceneSpec): Int {
        val have = found[scene.id].orEmpty()
        return scene.objects.count { it.slot in have }
    }
}
