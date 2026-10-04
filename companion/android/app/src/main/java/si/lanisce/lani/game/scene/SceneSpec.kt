package si.lanisce.lani.game.scene

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import si.lanisce.lani.data.json
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.villagers.Label
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDateTime
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Close-up scenes of the village (lani.scene/v0; the format is described in companion/SCENES.md).
// The bridge serves them resolved: every object carries its word, so a scene is self-contained.

/** One place to step into: the campfire, a kitchen, the forest, the square; the hills or the sea a culture brings. */
@Serializable
data class SceneSpec(
    val id: String,
    /** "Ob ognju · At the campfire"; a file may give it per language (read in the learner's pair, [Label]). */
    @Serializable(with = Label::class) val title: String,
    /**
     * The language its lines are in: the village's ("sl", or "it" in the Italian village's scenes). The app reads a scene
     * in the learner's pair ([inPair]): the texts' "sl" and "en" are then what is said and what it means.
     */
    val language: String = "sl",
    val emoji: String = "📍",
    val level: String = "A1",
    /** The painter that draws it; one of [SceneArt.arts]. */
    val art: String,
    /**
     * Where the village opens it: "fire", "forest" or building types in lower case ("hut", "house"). The
     * first one that exists in the village is where its bubbles show.
     */
    val from: List<String>,
    /** The earliest age it is open in ("ogenj" … "mesto"); null = as soon as [from] exists. */
    val needs: String? = null,
    /**
     * A room of a house (it opens from "house"): who lives in the house whose room it is, villager ids ("marko"). Each
     * house opens one room, its own ([Homes.deal]): the rooms go to the houses as their people moved in, the same room
     * every time for the same house. Empty: anyone's (a tutor's room goes to a house left over).
     */
    val household: List<String> = emptyList(),
    /** The word pack its objects teach. */
    val pack: String? = null,
    val objects: List<SceneObject> = emptyList(),
    val people: List<ScenePerson> = emptyList(),
    val happenings: List<Happening> = emptyList(),
    val dialogs: List<Dialog> = emptyList(),
    /**
     * The stories told here (lani.story/v0): the bridge sends a scene where someone tells stories (a happening with
     * [Happening.stories]) with that teller's stories, in the rotation's order; [Stories] picks tonight's.
     */
    val stories: List<Story> = emptyList(),
    /** curated | tutor */
    val source: String = "curated",
    /** The learner's pair its texts were read in ([inPair], "en-sl"): a scene the app kept reads as it is. */
    @SerialName("read_in") val readIn: String? = null,
)

/** A tappable thing in the scene, drawn in its painter's [slot], with the word it teaches. */
@Serializable
data class SceneObject(
    val slot: String,
    /** The word's id in [SceneSpec.pack] (or in [pack], when it comes from another pack). */
    val word: String,
    val pack: String? = null,
    /**
     * What the thing needs to be there: a building standing, a project far enough (the mill by the stream, see
     * [SceneNeeds]); without it the painter doesn't draw it and it's no word to find here. Its slot's own need
     * ([SceneFixtures]) holds besides.
     */
    val needs: SceneNeeds? = null,
    // Resolved by the bridge from the pack:
    val sl: String = "",
    val en: String = "",
    val emoji: String? = null,
    /** m | f | n */
    val gender: String? = null,
    val plural: String? = null,
    @SerialName("example_sl") val exampleSl: String? = null,
    @SerialName("example_en") val exampleEn: String? = null,
)

/** Someone who can be in the scene. They show up through [Happening]s, or always when [always]. */
@Serializable
data class ScenePerson(
    val id: String,
    /** "Babica Micka" */
    val name: String,
    val emoji: String = "🙂",
    /** Sprite; one of [SceneArt.people]. */
    val art: String,
    /** Where they stand; one of the art's [SceneArt.personSlots]. */
    val slot: String,
    val always: Boolean = false,
    /** The villager this is ([si.lanisce.lani.game.villagers.Villager.id]), so the friendship counts here too. */
    val villager: String? = null,
)

/**
 * Something going on today: someone is there (and has something to say), at some times of day. Which
 * happenings are on is decided per day, the same all day long (see [Happenings.active]).
 */
@Serializable
data class Happening(
    val id: String,
    /** "Babica kuha juho · Grandma is cooking soup"; a file may give it per language (read in the learner's pair, [Label]). */
    @Serializable(with = Label::class) val title: String,
    /** A [ScenePerson.id] of this scene. */
    val who: String,
    /** Times of day it can be seen; empty = all day. */
    val `when`: List<TimeOfDay> = emptyList(),
    /** ISO weekdays 1 (Monday) … 7 (Sunday); empty = every day. */
    val weekdays: List<Int> = emptyList(),
    /** Probability per day, 0..1. */
    val chance: Float = 1f,
    /** A [Dialog.id] of this scene to play when the learner talks to [who]; with [dialogs], what an older app plays. */
    val dialog: String? = null,
    /**
     * Its variants, [Dialog.id]s of this scene in order: one of them plays each day ([DialogVariants.pick]), so the talk
     * isn't the same word for word every time. Empty: [dialog] is the one.
     */
    val dialogs: List<String> = emptyList(),
    /** Paid once per day for finishing the dialog: resource name (food, wood, stone, wisdom) → amount. */
    val reward: Map<String, Int> = emptyMap(),
    /** Shown in the village above the place (an emoji), so the learner sees something is going on. */
    val marker: String = "💬",
    /**
     * What it needs besides its scene being open: the earliest age (like [SceneSpec.needs], "vas"), a building standing,
     * a project far enough, or not yet ([SceneNeeds]): Luka brings in the hay only where a kozolec stands.
     */
    val needs: SceneNeeds? = null,
    /**
     * What [who] remembers once the dialog is done, in their words, fitting "Še vedno mislim na {memory}":
     * {"sl": "juho, ki sva jo jedla ob ognju", "en": "the soup we had by the fire"}.
     */
    val memory: DialogReply? = null,
    /** Meant for a guest ("Sei di fuori?"): only a visitor from a linked town meets it (see [Happenings.forGuests]). */
    val guests: Boolean = false,
    /**
     * [who] tells tonight's story instead of a [dialog]: the next one of theirs the learner hasn't heard, at their level
     * ([Stories.tonight]); there is no happening without a story to tell.
     */
    val stories: Boolean = false,
)

@Serializable
enum class TimeOfDay {
    @SerialName("morning") MORNING,
    @SerialName("afternoon") AFTERNOON,
    @SerialName("evening") EVENING,
    @SerialName("night") NIGHT,

    /**
     * Dawn: the first hours of the morning, round the sunrise of the month ([dawn]: 5–7 in June, 7–9 in December). A part
     * of the [MORNING], not a part of the day of its own: [of] never gives it, and a happening "when" the morning is on
     * at dawn too; one "when" dawn only then (the hunter on his high seat, the mowing in the dew).
     */
    @SerialName("dawn") DAWN;

    companion object {
        /** morning 5–11, afternoon 11–17, evening 17–22, night 22–5 (local hours). */
        fun of(hour: Int): TimeOfDay = when (hour) {
            in 5..10 -> MORNING
            in 11..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> NIGHT
        }

        /**
         * The part of the day it is on the phone at [at] (now): the village's clock, as [of] the hour gives it, so dawn is
         * the morning. What the villagers say goes by it (a line's `when`, [si.lanisce.lani.game.villagers.VillagerLine.fits]).
         */
        fun now(at: LocalDateTime = LocalDateTime.now()): TimeOfDay = of(at.hour)

        /**
         * The hours of dawn in [month] (1..12): from about an hour before the sun rises (the scenes' sun, [Env.sunrise])
         * until an hour after, within the morning (5..10).
         */
        fun dawn(month: Int): IntRange {
            val rise = Env(12f, month, false).sunrise
            return max(5, floor(rise - 0.75f).toInt())..min(10, floor(rise + 1f).toInt())
        }

        /** Whether [hour] (0..23) of a day in [month] is at dawn. */
        fun isDawn(hour: Int, month: Int): Boolean = hour in dawn(month)
    }
}

/** A short scripted conversation: lines, and choices the learner answers with. */
@Serializable
data class Dialog(
    val id: String,
    val lines: List<DialogLine>,
    /** A role-play scenario id (lani.scenario/v0) to go on with the tutor afterwards. */
    val talk: String? = null,
    /**
     * Whether the sky its cues reached stays over the scene and the village until midnight (see [DaySky]); null: it
     * does when a line or a reply has a [DialogLine.sky].
     */
    @SerialName("sky_stays") val skyStays: Boolean? = null,
    /** Whether the effects its cues set ([DialogLine.fx]) stay in the scene until midnight (see [DayFx]); by default they go with the dialog. */
    @SerialName("fx_stays") val fxStays: Boolean? = null,
    /**
     * Whether where its stage directions leave people ([DialogLine.act]) stays until midnight (see [DayAct]: Luka by the
     * lantern while it rains); by default they go back to their spots when it closes.
     */
    @SerialName("act_stays") val actStays: Boolean? = null,
    /** Meant for a guest: only a visitor from a linked town plays it (see [Happenings.forGuests]). */
    val guests: Boolean = false,
    /** A number from the village it counts ({n} and the forms that agree in its texts): see [Counts]. */
    val count: DialogCount? = null,
    /** What the person remembers of this variant, instead of the happening's [Happening.memory]. */
    val memory: DialogReply? = null,
    /** curated | tutor: a variant the tutor wrote (publish_dialog_variant). */
    val source: String = "curated",
)

/**
 * Either a line someone says ([who] + [sl]/[en]) or the learner's turn ([choices]). [sl] is what is said, in the scene's
 * language, and [en] what it means in the learner's base (for Jan the Slovene and the English, as the files have them;
 * an Italian scene's "it" and, for a learner from Slovene, its "sl": see [inPair]); likewise the choices and replies.
 */
@Serializable
data class DialogLine(
    /** A [ScenePerson.id]; null with [choices]. */
    val who: String? = null,
    val sl: String? = null,
    val en: String? = null,
    val choices: List<DialogChoice> = emptyList(),
    /** The weather changes when this line plays (a learner's turn: when it comes up); see [SkyCue]. */
    val sky: SkyCue? = null,
    /** The scene's effects change when this line plays: name → level 0..1 (one of the art's [SceneArt.effects]). */
    @Serializable(with = FxLevels::class) val fx: Map<String, Float> = emptyMap(),
    /**
     * Stage directions when this line plays: person id → where they go and how ([ActCue]: a spot of the art's stage,
     * [SceneArt.stage], and a pose); see [actAt].
     */
    val act: Map<String, ActCue> = emptyMap(),
    /** The learner's turn: the grammar book's page of the rule its choices are about (a choice's own wins). */
    val grammar: String? = null,
    /**
     * A learner's turn: how the person reacts to a wrong answer that has no reaction of its own (the learner's own trap,
     * a form typed wrong); null: the app's "Hm? Kako, prosim?" (companion/SCENES.md, "Own traps").
     */
    val puzzled: DialogReply? = null,
)

@Serializable
data class DialogChoice(
    val sl: String,
    val en: String = "",
    /** A right answer here. At least one choice per turn is; where people would accept more than one, two are. */
    val ok: Boolean = false,
    /** Why a wrong choice is wrong (English, one sentence). */
    val why: String? = null,
    /**
     * The grammar book's page of the rule a wrong choice breaks ("kam-tozilnik"): its why leads there, and the pick meets
     * the page (companion/SCENES.md, "A why that is a rule of the grammar book").
     */
    val grammar: String? = null,
    /**
     * What the other person answers to this choice: to a right one, before the dialog goes on; to a wrong one, their
     * reaction to what the learner said, taken at face value, and the learner picks again (a passing [DialogReply.fx], no sky).
     */
    val reply: DialogReply? = null,
    /**
     * A counting dialog's wrong choice ([Dialog.count]): its forms take a wrong one where the right choice's agree, 1 the
     * one learners most often put there, 2 the next ([Numbers.wrong]).
     */
    @SerialName("wrong_form") val wrongForm: Int? = null,
    /**
     * A tap turn's choice (companion/SCENES.md, "Tap turns"): what it stands for in the picture, a spot of the art's stage
     * (behind the door), an object slot of the art (the door) or a person of the scene. Tapping it there picks this choice.
     */
    val tap: String? = null,
    /**
     * The page a wrong choice seems to test, guessed from its English why when a Slovene scene names no page at all ([inPair],
     * [si.lanisce.lani.game.Forms.rule]: a tutor's scene, or one an older bridge served without its `grammar`). It only
     * decides how the turn is asked and which of the learner's traps fits (companion/SCENES.md, "Adaptive turns"): no why
     * leads there, no answer counts there.
     */
    val guess: String? = null,
) {
    /** The page of the rule this wrong choice tests, as far as the app can tell: its own, its turn's, else the guess. */
    fun rule(turn: DialogLine): String? = grammar ?: turn.grammar ?: guess
}

/** The choices of a learner's turn that go on with the dialog: the right ones (all, in a turn without a right one). */
val DialogLine.answers: List<DialogChoice> get() = choices.filter { it.ok }.ifEmpty { choices }

/**
 * A learner's turn answered by tapping the scene (companion/SCENES.md, "Tap turns"): every choice stands for a place, a
 * thing or a person in the picture ([DialogChoice.tap]). Chosen, never typed or said whole.
 */
val DialogLine.tapTurn: Boolean get() = choices.size >= 2 && choices.all { it.tap != null }

/** What someone says back (a choice's reply), or remembers ([Happening.memory]). */
@Serializable
data class DialogReply(
    val sl: String,
    val en: String = "",
    /** The weather changes when this reply is said; see [SkyCue]. */
    val sky: SkyCue? = null,
    /** The scene's effects change when this reply is said; see [DialogLine.fx]. */
    @Serializable(with = FxLevels::class) val fx: Map<String, Float> = emptyMap(),
    /** Stage directions when this reply is said (a reaction's pass with the learner's next pick); see [DialogLine.act]. */
    val act: Map<String, ActCue> = emptyMap(),
)

/** GET /scenes: every scene, resolved, read in the learner's pair ([inPair]). */
fun parseScenes(raw: String): List<SceneSpec> {
    val list = json.parseToJsonElement(raw) as? JsonArray ?: return json.decodeFromString(raw)
    return json.decodeFromJsonElement(ListSerializer(SceneSpec.serializer()), JsonArray(list.map { (it as? JsonObject)?.let(::inPair) ?: it }))
}

fun parseScene(raw: String): SceneSpec {
    val o = json.parseToJsonElement(raw) as? JsonObject ?: return json.decodeFromString(raw)
    return json.decodeFromJsonElement(SceneSpec.serializer(), inPair(o))
}

/** The languages a scene's texts can come in (lani.scene/v0: the scene's own, then translations). */
private val TEXT_LANGS = listOf("sl", "en", "it", "de")

/**
 * [scene] as the app reads it for a learner of [pair]. The files give every text in the scene's language (`language`)
 * and translations: {"sl", "en"} in a Slovene scene, {"it", "sl", "en"} in an Italian one; the app keeps what is said in
 * "sl" and what it means in the learner's base (else English) in "en", for the lines, the choices and their replies,
 * the memories, the objects' words and examples, and the stories told here (their teasers, memories, tellings, words,
 * pictures' captions and notes); a `why` per language is read in the base. A Slovene scene read
 * by Jan (sl · en) keeps its Slovene and English as the file has them, and a scene read once reads the same again (the
 * app keeps the scenes it got): a scene in another language says the pair it was read in (`read_in`), since an English
 * scene's lines are its "en", the very place where the app keeps what a line means.
 */
fun inPair(scene: JsonObject, pair: LangPair = L10n.pair): JsonObject {
    (scene["variants"] as? JsonArray)?.let { v -> return inPair(withVariants(scene, v), pair) }
    if (scene["read_in"] != null) return scene
    val lang = (scene["language"] as? JsonPrimitive)?.contentOrNull ?: "sl"
    val base = pair.base.code
    fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** A text object: "sl" what is said, "en" its translation; the other languages go. Without the scene's language, as it is. */
    fun text(o: JsonObject, prefix: String = ""): JsonObject {
        val said = str(o, prefix + lang) ?: return o
        val meant = (if (base != lang) str(o, prefix + base) else null) ?: str(o, prefix + "en")
        val out = LinkedHashMap<String, JsonElement>(o)
        for (l in TEXT_LANGS) out.remove(prefix + l)
        out[prefix + "sl"] = JsonPrimitive(said)
        meant?.let { out[prefix + "en"] = JsonPrimitive(it) }
        return JsonObject(out)
    }
    fun why(o: JsonObject): JsonObject {
        val w = o["why"] as? JsonObject ?: return o
        val t = str(w, base) ?: str(w, "en") ?: w.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull } ?: return o
        return JsonObject(o + ("why" to JsonPrimitive(t)))
    }
    fun objects(e: JsonElement?, f: (JsonObject) -> JsonObject): JsonElement? = (e as? JsonArray)?.let { a -> JsonArray(a.map { (it as? JsonObject)?.let(f) ?: it }) }
    fun reply(o: JsonObject): JsonObject = (o["reply"] as? JsonObject)?.let { JsonObject(o + ("reply" to text(it))) } ?: o
    // A Slovene scene that names no grammar page at all (a tutor's, or one an older bridge served without its `grammar`):
    // a wrong choice's page is guessed from its English why (DialogChoice.guess). One that names pages means what it says:
    // a choice it leaves without one tests no rule of the book.
    val guessing = lang == "sl" && (scene["dialogs"] as? JsonArray).orEmpty().none { d ->
        ((d as? JsonObject)?.get("lines") as? JsonArray).orEmpty().any { l ->
            val line = l as? JsonObject
            line?.get("grammar") != null || (line?.get("choices") as? JsonArray).orEmpty().any { (it as? JsonObject)?.get("grammar") != null }
        }
    }
    fun rule(o: JsonObject): JsonObject {
        if (!guessing || o["guess"] != null || (o["ok"] as? JsonPrimitive)?.content == "true") return o
        val w = o["why"]
        val en = (w as? JsonObject)?.let { str(it, "en") } ?: (w as? JsonPrimitive)?.takeIf { it.isString }?.content
        val page = si.lanisce.lani.game.Forms.rule(en) ?: return o
        return JsonObject(o + ("guess" to JsonPrimitive(page)))
    }
    fun puzzled(l: JsonObject): JsonObject = (l["puzzled"] as? JsonObject)?.let { JsonObject(l + ("puzzled" to text(it))) } ?: l
    fun line(o: JsonObject): JsonObject = puzzled(text(o)).let { l -> objects(l["choices"]) { reply(why(text(rule(it)))) }?.let { JsonObject(l + ("choices" to it)) } ?: l }
    // a happening's memory, and a variant's own
    fun happening(o: JsonObject): JsonObject = (o["memory"] as? JsonObject)?.let { JsonObject(o + ("memory" to text(it))) } ?: o
    fun dialog(o: JsonObject): JsonObject = happening(objects(o["lines"], ::line)?.let { JsonObject(o + ("lines" to it)) } ?: o)
    fun thing(o: JsonObject): JsonObject = text(text(o), "example_")
    // a story: its teaser and memory, each chapter's teaser, and every telling's lines (a level → {lines}), its words,
    // its pictures' captions, its notes' paragraphs
    fun texts(o: JsonObject, vararg keys: String): JsonObject =
        keys.fold(o) { acc, k -> (acc[k] as? JsonObject)?.let { JsonObject(acc + (k to text(it))) } ?: acc }
    fun tellings(o: JsonObject): JsonObject = (o["levels"] as? JsonObject)?.let { lv ->
        JsonObject(o + ("levels" to JsonObject(lv.mapValues { (_, t) -> (t as? JsonObject)?.let(::dialog) ?: t })))
    } ?: o
    fun note(o: JsonObject): JsonObject = objects(o["text"], ::text)?.let { JsonObject(o + ("text" to it)) } ?: o
    fun story(o: JsonObject): JsonObject {
        val s = tellings(texts(o, "teaser", "memory"))
        val parts = objects(s["chapters"]) { tellings(texts(it, "teaser")) }?.let { JsonObject(s + ("chapters" to it)) } ?: s
        val worded = objects(parts["words"], ::text)?.let { JsonObject(parts + ("words" to it)) } ?: parts
        val pictured = objects(worded["pictures"]) { texts(it, "caption") }?.let { JsonObject(worded + ("pictures" to it)) } ?: worded
        return objects(pictured["notes"], ::note)?.let { JsonObject(pictured + ("notes" to it)) } ?: pictured
    }

    val out = LinkedHashMap<String, JsonElement>(scene)
    objects(scene["dialogs"], ::dialog)?.let { out["dialogs"] = it }
    objects(scene["happenings"], ::happening)?.let { out["happenings"] = it }
    objects(scene["objects"], ::thing)?.let { out["objects"] = it }
    objects(scene["stories"], ::story)?.let { out["stories"] = it }
    if (lang != "sl") out["read_in"] = JsonPrimitive(pair.code)
    return JsonObject(out)
}

/**
 * A scene file's `variants` (the happenings' other dialogs, kept apart so an older bridge reads the file as before) among
 * its `dialogs`, as the bridge serves them.
 */
private fun withVariants(scene: JsonObject, variants: JsonArray): JsonObject {
    val out = LinkedHashMap<String, JsonElement>(scene)
    out.remove("variants")
    out["dialogs"] = JsonArray(((scene["dialogs"] as? JsonArray).orEmpty()) + variants)
    return JsonObject(out)
}
