package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * The learner's own voice, "🗣️ Moj glas · My voice" (companion/SCENES.md, "The learner's voice"; docs/plans/05-built/
 * my-voice.md): the voice their own lines in a dialog are said in, chosen on the node from its cast's voices for their
 * gender and age (2–3, a child's for a child), or off. The node keeps the choice (their learner profile) and says their
 * lines in it; the phone asks for them as [Clips.PLAYER] as before.
 */

/**
 * A voice to choose: [id] as the node's cast names it ("male.sl", "player-boy-b"), [voice] as its clips are kept
 * ("male", "player-boy-b"), the [name] to show, its [gender] and [age] ("adult", "child"), its place on the list ([pick]),
 * the [default] first.
 */
data class MyVoiceOption(val id: String, val voice: String, val name: String, val gender: String, val age: String, val pick: Int, val default: Boolean) {
    val child: Boolean get() = age == MyVoices.CHILD
}

/**
 * What the node says of the learner's voice (GET and POST /learner/voice): the [options] offered them, the one in effect
 * ([voice]: an option's id, null when none may say their lines), [off] (their lines aren't said), [chosen] (they have
 * chosen: the offer at the first line isn't shown again), [age], and the [sample] line a ▶ says ("Ciao! Sono Teo.").
 */
data class MyVoiceState(
    val options: List<MyVoiceOption>,
    val voice: String?,
    val off: Boolean,
    val chosen: Boolean,
    val age: String,
    val sample: String,
    val language: String,
) {
    /** The option in effect, when there is one. */
    val current: MyVoiceOption? get() = options.firstOrNull { it.id == voice }
}

/**
 * The learner's voice as the phone plays their lines with (GET /voice/profiles' "mine"): the [voice] their clips are kept
 * in (null: none, the phone's own voice says them), its [gender] and [age] (what the phone's voice imitates when no clip can
 * be had: a child's higher), [off], and whether they have [chosen].
 */
data class MineVoice(val voice: String?, val id: String?, val name: String?, val gender: String?, val age: String, val off: Boolean, val chosen: Boolean) {
    val child: Boolean get() = age == MyVoices.CHILD
}

object MyVoices {
    const val CHILD = "child"
    const val ADULT = "adult"
    const val OFF = "off"
    const val DEFAULT = "default"

    private fun JsonObject.str(k: String): String? = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
    private fun JsonObject.bool(k: String): Boolean? = this[k]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }

    /** GET or POST /learner/voice's answer; null when it isn't one (an older node: 404). */
    fun parseState(raw: String): MyVoiceState? = runCatching {
        val o = json.parseToJsonElement(raw).jsonObject
        val options = o["options"]?.jsonArray.orEmpty().mapNotNull { e ->
            val x = e as? JsonObject ?: return@mapNotNull null
            val id = x.str("id") ?: return@mapNotNull null
            MyVoiceOption(
                id = id,
                voice = x.str("voice") ?: id,
                name = x.str("name") ?: id,
                gender = x.str("gender") ?: Clips.FEMALE,
                age = x.str("age") ?: ADULT,
                pick = x["pick"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() } ?: 0,
                default = x.bool("default") == true,
            )
        }
        MyVoiceState(
            options = options,
            voice = o.str("voice"),
            off = o.bool("off") == true,
            chosen = o.bool("chosen") == true,
            age = o.str("age") ?: ADULT,
            sample = o.str("sample").orEmpty(),
            language = o.str("language").orEmpty(),
        )
    }.getOrNull()

    /** GET /voice/profiles' "mine"; null from an older node (then the lines go as before: the voice "player" says). */
    fun parseMine(raw: String): MineVoice? = runCatching {
        val m = json.parseToJsonElement(raw).jsonObject["mine"] as? JsonObject ?: return null
        MineVoice(
            voice = m.str("voice"),
            id = m.str("id"),
            name = m.str("name"),
            gender = m.str("gender"),
            age = m.str("age") ?: ADULT,
            off = m.bool("off") == true,
            chosen = m.bool("chosen") == true,
        )
    }.getOrNull()

    /** The learner's voice for the phone after the node said [s] (a choice just made): as /voice/profiles would say it. */
    fun mineOf(s: MyVoiceState): MineVoice {
        val o = s.current
        return MineVoice(voice = o?.voice, id = o?.id, name = o?.name, gender = o?.gender, age = o?.age ?: s.age, off = s.off, chosen = s.chosen)
    }

    /**
     * Whether the choice is offered now, at the learner's line being said ([MineVoice] as the node said it): once, while they
     * haven't chosen (a voice, off, or the default kept), their lines are on, and there is something to choose from; and not
     * twice while the app runs ([offered]). Never a wall: the line is said meanwhile and the dialog goes on.
     */
    fun offerDue(mine: MineVoice?, offered: Boolean): Boolean = mine != null && !mine.chosen && !mine.off && mine.voice != null && !offered
}

/**
 * How the phone's own voice says a learner's line when no clip of their voice can be had (companion/SCENES.md, "The
 * learner's voice"): in their voice's gender, a child's higher. Android has no gender for a voice, so it is read from the
 * engines' voice names and features: Samsung's "it-IT-SMTm00" (m: a man's, f: a woman's), Google's older
 * "en-us-x-sfg#male_1-local", an engine's "male"/"female" feature; none known, the default voice, a man's line a little
 * lower.
 */
object PlayerTts {
    /** The phone's voice for a child's line: higher, as a child's is (a man's or a woman's voice at 1.3 ≈ a child's range). */
    const val CHILD_PITCH = 1.3f

    /** A man's line in a voice that isn't known to be a man's (most default voices are a woman's): somewhat lower. */
    const val MAN_PITCH = 0.85f

    /** A voice of the phone's: its [name], [locale], its engine's [features], whether it needs the network, its [quality]. */
    data class VoiceInfo(val name: String, val locale: Locale, val features: Set<String>, val network: Boolean, val quality: Int)

    /** What the phone's voice says the learner's line with: the voice [voice] (null: the default) at [pitch]. */
    data class Style(val voice: String?, val pitch: Float)

    private val SAMSUNG = Regex("""SMT([fm])\d""", RegexOption.IGNORE_CASE)
    private val GOOGLE = Regex("""#(female|male)_\d""", RegexOption.IGNORE_CASE)

    /** The gender [v] is known to speak in ("male", "female"), else null. */
    fun genderOf(v: VoiceInfo): String? {
        SAMSUNG.find(v.name)?.let { return if (it.groupValues[1].equals("m", true)) Clips.MALE else Clips.FEMALE }
        GOOGLE.find(v.name)?.let { return it.groupValues[1].lowercase() }
        val f = v.features.map { it.lowercase() }
        return when {
            f.any { it == "female" || it.endsWith(":female") || it.endsWith("=female") } -> Clips.FEMALE
            f.any { it == "male" || it.endsWith(":male") || it.endsWith("=male") } -> Clips.MALE
            else -> null
        }
    }

    /**
     * The style for a line of [mine] in [locale] among the phone's [voices]: a voice of their gender in that language (on the
     * phone itself before one that needs the network, the better first), else the default; a child's at [CHILD_PITCH], a
     * man's in a voice not known to be a man's at [MAN_PITCH], else as it is.
     */
    fun style(mine: MineVoice?, locale: Locale, voices: List<VoiceInfo>): Style {
        val gender = mine?.gender
        val child = mine?.child == true
        val same = voices.filter { it.locale.language == locale.language && (locale.country.isEmpty() || it.locale.country.isEmpty() || it.locale.country == locale.country) }
        val pick = gender?.let { g -> same.filter { genderOf(it) == g }.sortedWith(compareBy<VoiceInfo> { it.network }.thenByDescending { it.quality }).firstOrNull() }
        val pitch = when {
            child -> CHILD_PITCH
            pick == null && gender == Clips.MALE -> MAN_PITCH
            else -> 1f
        }
        return Style(pick?.name, pitch)
    }
}

/** HTTP for the learner's voice on the node (companion/bridge/src/features/voice.ts): the choice and a voice's sample. */
class MyVoiceApi(private val config: BridgeConfig) {
    private val http = Http.client(connectSeconds = 3, readSeconds = 20)

    /** What the node says of the learner's voice; null from an older node (404). */
    suspend fun state(): MyVoiceState? {
        val (code, raw) = http.exchange(config.request(PATH).build())
        if (code == 404) return null
        if (code !in 200..299) throw IOException("HTTP $code")
        return MyVoices.parseState(raw) ?: throw IOException("not the learner's voice")
    }

    /** Keeps [choice] (an option's id, [MyVoices.OFF] or [MyVoices.DEFAULT]) on the node; its answer, the state after. */
    suspend fun choose(choice: String): MyVoiceState {
        val body = buildJsonObject { put("voice", choice) }.toString().toRequestBody(Http.jsonType)
        val (code, raw) = http.exchange(config.request(PATH).post(body).build())
        if (code !in 200..299) throw IOException("HTTP $code: ${raw.take(200)}")
        return MyVoices.parseState(raw) ?: throw IOException("not the learner's voice")
    }

    /**
     * [option]'s sample (POST /voice/sample): its text and the clip's URL on the node, null when the node can't make it now
     * (the phone's voice says it then).
     */
    suspend fun sample(option: String): Pair<String, String?> {
        val body = buildJsonObject { put("voice", option) }.toString().toRequestBody(Http.jsonType)
        val (code, raw) = http.exchange(config.request(SAMPLE).post(body).build())
        if (code !in 200..299) throw IOException("HTTP $code")
        val o = json.parseToJsonElement(raw).jsonObject
        val text = o["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return text to o["url"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
    }

    suspend fun download(url: String, to: File) = http.downloadTo(config.request(url).build(), to)

    companion object {
        const val PATH = "/learner/voice"
        const val SAMPLE = "/voice/sample"
    }
}
