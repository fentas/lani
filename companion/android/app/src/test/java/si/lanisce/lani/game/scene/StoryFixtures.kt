package si.lanisce.lani.game.scene

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import si.lanisce.lani.data.json
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import java.io.File

/** The culture packs' stories as the bridge serves them with a village's campfire, for the story and book tests. */
object StoryFixtures {
    val companion: File = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory && File(it, "scenes").isDirectory }

    /** [f] as the app reads it: said to the learner ({learner}, {m:…|f:…}: l10n/Learner.kt). */
    fun obj(f: File): JsonObject = json.parseToJsonElement(Learner.current.renderJson(f.readText())).jsonObject

    /** [culture]'s story files, in their order. */
    fun files(culture: String): List<File> =
        File(companion, "cultures/$culture/stories").listFiles()!!.filter { it.extension == "json" }.sortedBy { obj(it)["order"]?.jsonPrimitive?.int ?: 999 }

    /** [culture]'s stories as the bridge serves them with its campfire: in their order, the words resolved from the packs. */
    fun served(culture: String, language: String): JsonArray {
        val packs = (File(companion, if (language == "sl") "packs" else "packs/$language").listFiles().orEmpty().toList() + File(companion, "cultures/$culture/packs").listFiles().orEmpty().toList())
            .filter { it.extension == "json" }.map(::obj).associateBy { it["id"]!!.jsonPrimitive.content }
        return JsonArray(files(culture).map(::obj).map { s ->
            val pack = s["pack"]?.jsonPrimitive?.content
            val words = (s["words"] as? JsonArray).orEmpty().mapNotNull { w ->
                val (id, p) = if (w is JsonPrimitive) w.content to pack else w.jsonObject["word"]!!.jsonPrimitive.content to w.jsonObject["pack"]!!.jsonPrimitive.content
                val pw = packs[p]?.get("words")?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["id"]!!.jsonPrimitive.content == id } ?: return@mapNotNull null
                JsonObject(pw.filterKeys { it in setOf("sl", "en", "it", "de", "emoji", "gender") } + mapOf("id" to JsonPrimitive(id), "pack" to JsonPrimitive(p)))
            }
            JsonObject(s + ("words" to JsonArray(words)))
        })
    }

    /** The campfire of [culture]'s village with its storyteller's stories, read by a learner of [p]. */
    fun campfire(culture: String = "primorska", p: LangPair = LangPair(Lang.SL, Lang.EN)): SceneSpec {
        L10n.pair = p
        val file = when (culture) {
            "primorska" -> File(companion, "scenes/ob-ognju.json")
            "lakeland" -> File(companion, "cultures/$culture/scenes/by-the-fire.json")
            "kaernten" -> File(companion, "cultures/$culture/scenes/am-feuer.json")
            else -> File(companion, "cultures/$culture/scenes/al-fuoco.json")
        }
        val o = obj(file)
        val language = o["language"]?.jsonPrimitive?.content ?: "sl"
        return parseScene(JsonObject(o + ("stories" to served(culture, language))).toString())
    }
}
