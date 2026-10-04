package si.lanisce.lani.game.villagers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import si.lanisce.lani.data.json
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

// The people of the village (lani.villager/v0; described in companion/VILLAGERS.md). One cast for
// everything: quest givers, the people in scenes, the companion on the training stage, and the
// characters the tutor plays when Jan talks to them.

@Serializable
data class Villager(
    /** "micka" */
    val id: String,
    /** "Babica Micka" */
    val name: String,
    val emoji: String = "🙂",
    /** Sprite; one of [si.lanisce.lani.game.scene.SceneArt.people]. */
    val art: String,
    /** female | male: their gender (Slovene agreement), and the voice their lines fall back to. */
    val voice: String = "female",
    /**
     * Their voice in the node's voice cast (companion/voice-cast.json): "grandma", "young-man". Null (an
     * older bridge, or no speaker set): the narrator of their gender. See [speakerVoice].
     */
    val speaker: String? = null,
    /**
     * "Babica · Grandmother": what they are in the village, target · English; a file may give it per language
     * ({"it": "Nonna", "sl": "Babica", "en": "Grandmother"}), shown in the learner's pair ([Label]).
     */
    @Serializable(with = Label::class) val role: String = "",
    /**
     * Where they are in the village, best first: building types in lower case ("hut", "house") that
     * count once built, and spots ("spot:woodpile", see [si.lanisce.lani.game.scene.TownSpots]),
     * which always exist. Their bubbles and their sprite show at the first one that exists.
     */
    val home: List<String> = emptyList(),
    /** ti | vi: how the learner addresses them (Slovene ti and vi; Italian tu and Lei), and on the stage how they speak to the learner. */
    val register: String = "vi",
    /** A few sentences for the tutor who plays them: character, manner, speech (English). */
    val personality: String = "",
    /** Their story in the village, one or two sentences (English, may name Slovene things). */
    val story: String = "",
    /** Topics they love to talk about ("potica", "bees", "the burja"): each a [Label]. */
    val likes: List<@Serializable(with = Label::class) String> = emptyList(),
    /** The skill their requests train: food (vocabulary), wood (listening), stone (grammar), wisdom (conversation). */
    val skill: String? = null,
    /** From which age they live in the village ("ogenj" … "mesto"); null = from the start. */
    val since: String? = null,
    /** Who moves in first when there's room: lower first (see game/villagers/Residents). */
    val order: Int = 100,
    /**
     * An extra: a smaller person of the culture (the hunter, some day a postman or a priest) with fewer lines and no role
     * the game needs. The cast moves in first; an extra moves in as a newcomer does, taking turns with the names the
     * village makes up ([Residents]); plan 2, "Many people per culture".
     */
    val extra: Boolean = false,
    val lines: VillagerLines = VillagerLines(),
    /** curated | tutor */
    val source: String = "curated",
    /**
     * When they get up and go to bed, and their night rituals ([RoutineSpec], companion/VILLAGERS.md "A day in the
     * village"); null: their kind's ([Routine.plan]). An older bridge leaves it out.
     */
    val routine: RoutineSpec? = null,
    /**
     * Where they sleep, when it isn't where they are by day ([home]): a building with a roof ("smithy", "school", "house") or
     * a room of one, "house:<art>" ("house:cellar": the cellar of the house that has it, a bed there). Null: the first roof of
     * their [home] (see [si.lanisce.lani.game.scene.Sleep.bedrooms]). An older bridge leaves it out.
     */
    val sleeps: String? = null,
) {
    /** The voice their lines are played in: their speaker, falling back to [voice] (see [si.lanisce.lani.data.Speaker.say]). */
    val speakerVoice: String get() = speaker ?: voice
}

/**
 * Things they say, by situation. A line shows from bond [VillagerLine.level] on, so they grow warmer as
 * the friendship grows; "{memory}" in a remember line is replaced by a memory's text.
 */
@Serializable
data class VillagerLines(
    /** Meeting Jan. */
    val greet: List<VillagerLine> = emptyList(),
    /** After Jan helped (a quest, a dialog). */
    val thanks: List<VillagerLine> = emptyList(),
    /** Bringing up something they remember: "Še vedno mislim na {memory}". */
    val remember: List<VillagerLine> = emptyList(),
    /** Small talk when nothing is going on. */
    val idle: List<VillagerLine> = emptyList(),
    /** On the training stage: a right answer. */
    val cheer: List<VillagerLine> = emptyList(),
    /** On the training stage: a wrong answer, kindly. */
    val comfort: List<VillagerLine> = emptyList(),
    /** On the training stage: before a listening exercise. */
    val listen: List<VillagerLine> = emptyList(),
    /** Saying goodbye. */
    val bye: List<VillagerLine> = emptyList(),
    /** Jan gave them a good from the chest (the gift moment): what they say, by the good. */
    val gift: GiftLines = GiftLines(),
)

/**
 * What a villager says when Jan gives them a good (`lines.gift`): for one they like ([liked]: delight), an ordinary one
 * ([ordinary]: warm thanks), a rare one ([rare]: special joy). A villager without them (a tutor's, a newcomer) says
 * the plain ones ([si.lanisce.lani.ui.villagers.GiftTalk]).
 */
@Serializable
data class GiftLines(
    val liked: List<VillagerLine> = emptyList(),
    val ordinary: List<VillagerLine> = emptyList(),
    val rare: List<VillagerLine> = emptyList(),
)

/**
 * One line, in every language it has: {"sl": "Dober dan!", "en": "Good day!"} (the format as it always was), or in
 * another village's language with translations: {"it": "Buongiorno!", "sl": "Dober dan!", "en": "Good day!"}. What
 * is said ([target]) is the line in the learner's target language, else in Slovene (a line in the old format), else in
 * its first language that isn't English; its translation ([base]) is in the learner's base language when the line has
 * it, else in English. For Jan (sl · en) that is the "sl" and the "en", as ever.
 *
 * A line bound to a time of day says when ([times], the file's `"when": ["evening", "night"]`: "Lahko noč, fant."):
 * it is only said then ([fits]); a line without it fits any time.
 */
@Serializable(with = VillagerLine.Serializer::class)
class VillagerLine(val by: Map<String, String>, val level: Int = 0, val times: Set<TimeOfDay> = emptySet()) {
    /** A line in the old format: Slovene, and its English. */
    constructor(sl: String, en: String = "", level: Int = 0, times: Set<TimeOfDay> = emptySet()) :
        this(if (en.isEmpty()) mapOf("sl" to sl) else linkedMapOf("sl" to sl, "en" to en), level, times)

    /** Whether it can be said at [time] (the phone's: [TimeOfDay.now]; dawn is the morning): always, without a `when`. */
    fun fits(time: TimeOfDay): Boolean = times.isEmpty() || (if (time == TimeOfDay.DAWN) TimeOfDay.MORNING else time) in times

    /** The language the line is said in for a learner of [pair] (see the class). */
    fun spokenIn(pair: LangPair = L10n.pair): String = when {
        pair.target.code in by -> pair.target.code
        "sl" in by -> "sl"
        else -> by.keys.firstOrNull { it != Lang.EN.code } ?: Lang.EN.code
    }

    /** What is said, for a learner of [pair]: "Dober dan!", "Buongiorno!". */
    fun target(pair: LangPair = L10n.pair): String = by[spokenIn(pair)].orEmpty()

    /** Its translation for a learner of [pair]: in their base language when the line has it, else English; "" when none. */
    fun base(pair: LangPair = L10n.pair): String {
        val said = spokenIn(pair)
        val b = pair.base.code.takeIf { it != said && it in by } ?: Lang.EN.code.takeIf { it != said }
        return b?.let { by[it] }.orEmpty()
    }

    /** What is said, in the learner's pair of the moment. */
    val target: String get() = target(L10n.pair)

    /** Its translation, in the learner's pair of the moment. */
    val base: String get() = base(L10n.pair)

    /** This line with every language's text changed by [f] (a memory put in, two lines joined); the level and times kept. */
    fun map(f: (lang: String, text: String) -> String): VillagerLine = VillagerLine(by.mapValues { (l, s) -> f(l, s) }, level, times)

    override fun equals(other: Any?): Boolean = other is VillagerLine && other.by == by && other.level == level && other.times == times
    override fun hashCode(): Int = (by.hashCode() * 31 + level) * 31 + times.hashCode()
    override fun toString(): String =
        "VillagerLine(${by.entries.joinToString { "${it.key}=${it.value}" }}, level=$level${if (times.isEmpty()) "" else ", when=${times.map { it.name.lowercase() }}"})"

    companion object {
        private val LANG = Regex("[a-z]{2,3}")

        /** The parts of the day a line's `when` names (dawn is the morning's, not a part of its own). */
        val PARTS: List<TimeOfDay> = listOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON, TimeOfDay.EVENING, TimeOfDay.NIGHT)

        /**
         * [a] then [b] as one line ("Dober dan! Poslušaj …"): each language joined, a language one of them lacks taken
         * as that one says it; the level of the warmer, and the times both fit.
         */
        fun join(a: VillagerLine, b: VillagerLine): VillagerLine {
            val langs = (a.by.keys + b.by.keys)
            val pick = { l: VillagerLine, lang: String -> l.by[lang] ?: l.by[l.spokenIn()] ?: "" }
            val times = when {
                a.times.isEmpty() -> b.times
                b.times.isEmpty() -> a.times
                else -> a.times intersect b.times
            }
            return VillagerLine(langs.associateWith { "${pick(a, it)} ${pick(b, it)}".trim() }, maxOf(a.level, b.level), times)
        }
    }

    object Serializer : KSerializer<VillagerLine> {
        override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

        override fun deserialize(decoder: Decoder): VillagerLine {
            val input = decoder as? JsonDecoder ?: throw SerializationException("a villager line is read from JSON")
            val o = input.decodeJsonElement() as? JsonObject ?: throw SerializationException("a villager line is an object: {\"sl\": …, \"en\": …}")
            var level = 0
            var times: Set<TimeOfDay> = emptySet()
            val by = LinkedHashMap<String, String>()
            for ((k, v) in o) {
                if (k == "when") {
                    times = parts(v)
                    continue
                }
                val p = v as? JsonPrimitive ?: throw SerializationException("a villager line's \"$k\" isn't a text")
                if (k == "level") level = p.intOrNull ?: throw SerializationException("a villager line's level is a number (0 … 4)")
                else if (LANG.matches(k) && p.isString) by[k] = p.contentOrNull.orEmpty()
                else throw SerializationException("a villager line has \"$k\": its keys are language codes (sl, en, it …), \"level\" and \"when\"")
            }
            if (by.isEmpty()) throw SerializationException("a villager line in no language")
            return VillagerLine(by, level, times)
        }

        /** A line's `when`: ["evening", "night"], the parts of the day it is said in ([PARTS]), at least one. */
        private fun parts(v: JsonElement): Set<TimeOfDay> {
            val names = PARTS.associateBy { it.name.lowercase() }
            val list = v as? JsonArray ?: throw SerializationException("a villager line's \"when\" is a list: [\"evening\", \"night\"]")
            if (list.isEmpty()) throw SerializationException("a villager line's \"when\" names a part of the day (morning, afternoon, evening, night)")
            return list.map { e ->
                val s = (e as? JsonPrimitive)?.takeIf { it.isString }?.content
                names[s] ?: throw SerializationException("a villager line's \"when\" has \"$s\": morning, afternoon, evening or night")
            }.toSortedSet()
        }

        override fun serialize(encoder: Encoder, value: VillagerLine) {
            val out = encoder as? JsonEncoder ?: throw SerializationException("a villager line is written as JSON")
            out.encodeJsonElement(buildJsonObject {
                for ((k, v) in value.by) put(k, v)
                if (value.level != 0) put("level", value.level)
                if (value.times.isNotEmpty()) put("when", buildJsonArray { for (t in PARTS) if (t in value.times) add(t.name.lowercase()) })
            })
        }
    }
}

/**
 * The lines of these that can be said at [time]: those without a `when`, and those for it ([VillagerLine.fits]); and that
 * name only people the learner knows now ([Mentions.sayable]: Zala doesn't talk of Nejc before he lives here and is met).
 */
fun List<VillagerLine>.at(time: TimeOfDay): List<VillagerLine> = filter { it.fits(time) && Mentions.sayable(it) }

/**
 * A label a file may give as one string ("Babica · Grandmother": target · English, as the formats always had it) or
 * per language ({"it": "Nonna", "sl": "Babica", "en": "Grandmother"}): read as "target · base" in the learner's pair
 * when it's read (for a villager's role and likes, a word pack's title).
 */
object Label : KSerializer<String> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    /** A label's text per language as "target · base" in [pair]: the target's (else English, else the first), then the base's. */
    fun shown(by: Map<String, String>, pair: LangPair = L10n.pair): String {
        val t = by[pair.target.code] ?: by[Lang.EN.code] ?: by.values.firstOrNull().orEmpty()
        val b = by[pair.base.code]?.takeIf { pair.base.code in by && it != t } ?: by[Lang.EN.code]?.takeIf { pair.target.code in by && it != t }
        return if (b.isNullOrEmpty()) t else "$t · $b"
    }

    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val e: JsonElement = input.decodeJsonElement()) {
            is JsonPrimitive -> e.contentOrNull.orEmpty()
            is JsonObject -> shown(e.mapValues { (k, v) -> (v as? JsonPrimitive)?.contentOrNull ?: throw SerializationException("a label's \"$k\" isn't a text") })
            else -> throw SerializationException("a label is a text or {\"sl\": …, \"en\": …}")
        }
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/**
 * A label a file may give as one string (English, as a word pack's description always was) or per language
 * ({"sl": "…", "en": "…"}): read in the learner's base language (else English) when it's read.
 */
object BaseLabel : KSerializer<String> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val e: JsonElement = input.decodeJsonElement()) {
            is JsonPrimitive -> e.contentOrNull.orEmpty()
            is JsonObject -> {
                val by = e.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }
                by[L10n.pair.base.code] ?: by[Lang.EN.code] ?: by.values.firstOrNull().orEmpty()
            }
            else -> throw SerializationException("a text or {\"sl\": …, \"en\": …}")
        }
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/** GET /villagers */
fun parseVillagers(raw: String): List<Villager> = json.decodeFromString(raw)
