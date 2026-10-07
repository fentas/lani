package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.bi

val json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    explicitNulls = false
}

// --- modules (lani.module/v0, see companion/bridge/src/spec.ts) ---------------------

@Serializable
data class ModuleInfo(
    val id: String,
    val version: Int,
    val title: String,
    val level: String,
    val targets: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    /** Present when the tutor wrote this module as a village sidequest. */
    val quest: QuestMeta? = null,
)

/** A module's quest block (lani.module/v0 `quest`): shows it in the village as a villager's request. */
@Serializable
data class QuestMeta(
    val giver: String,
    val emoji: String = "🧑",
    val story: String,
    /** food | wood | stone | wisdom; which skill it trains. */
    val skill: String? = null,
    /** Resource name (food, wood, stone, wisdom) → amount. */
    val reward: Map<String, Int> = emptyMap(),
    /**
     * The module of an open task at the same giver this one is a smaller drill for: the task waits behind it (companion/GAME.md,
     * "Quests"). Absent from an older bridge, which drops it: the app then finds the link by a shared grammar page.
     */
    val helps: String? = null,
)

data class Module(
    val id: String,
    val version: Int,
    val title: String,
    val description: String? = null,
    val level: String,
    val targets: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val exercises: List<Exercise>,
)

@Serializable
private data class ModuleWire(
    val id: String,
    val version: Int,
    val title: String,
    val description: String? = null,
    val level: String,
    val targets: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val exercises: List<JsonObject>,
)

/**
 * Decodes a module exercise by exercise, so a type this app version doesn't know yet
 * (published by a newer bridge) becomes [Exercise.Unsupported] instead of failing the module.
 */
fun parseModule(raw: String): Module {
    val w = json.decodeFromString<ModuleWire>(si.lanisce.lani.l10n.Learner.current.renderJson(raw))
    val exercises = w.exercises.map { o ->
        runCatching { json.decodeFromJsonElement(Exercise.serializer(), o) }
            .getOrElse { Exercise.Unsupported(o["type"]?.jsonPrimitive?.contentOrNull ?: "?") }
    }
    return Module(w.id, w.version, w.title, w.description, w.level, w.targets, w.tags, exercises)
}

@Serializable
sealed interface Exercise {
    /**
     * The grammar book's page of the rule it practises ("kje-mestnik-orodnik", lani.grammar/v0): answering it unlocks
     * the page, and its "why" leads there. Null when it names none.
     */
    val grammar: String? get() = null

    /**
     * What it plays as it comes up and asks about: a listening choice's, a dictation's, and by ear a cloze's (the gap
     * heard, companion/GAME.md "Events") and a reorder's (the words heard); null for one that isn't heard.
     */
    val heard: String? get() = when (this) {
        is Choice -> audio
        is Dictation -> audio
        is Cloze -> audio
        is Reorder -> audio
        else -> null
    }

    @Serializable @SerialName("flashcard")
    data class Flashcard(val front: String, val back: String, val note: String? = null, val speak: Boolean = true, override val grammar: String? = null) : Exercise

    @Serializable @SerialName("choice")
    data class Choice(
        val prompt: String,
        val options: List<String>,
        val answer: Int,
        val explain: String? = null,
        val audio: String? = null,
        val instruction: String? = null,
        val say: String? = null,
        @SerialName("say_options") val sayOptions: Boolean = false,
        override val grammar: String? = null,
    ) : Exercise

    /** A gap to type; with [audio], a gap by ear: the whole sentence is heard, the missing word typed. */
    @Serializable @SerialName("cloze")
    data class Cloze(
        val text: String, val accept: List<String>, val hint: String? = null, val explain: String? = null, val instruction: String? = null,
        override val grammar: String? = null,
        val audio: String? = null,
    ) : Exercise

    /** Words to put in order; with [audio], word chips by ear: the sentence is heard, its words tapped in order. */
    @Serializable @SerialName("reorder")
    data class Reorder(
        val prompt: String,
        val tokens: List<String>,
        val solutions: List<List<String>>,
        val explain: String? = null,
        val distractors: List<String> = emptyList(),
        val instruction: String? = null,
        val say: String? = null,
        override val grammar: String? = null,
        val audio: String? = null,
    ) : Exercise

    @Serializable @SerialName("translate")
    data class Translate(
        val prompt: String,
        val accept: List<String>,
        val grade: String = "match",
        val explain: String? = null,
        val instruction: String? = null,
        val say: String? = null,
        override val grammar: String? = null,
    ) : Exercise

    @Serializable @SerialName("free")
    data class Free(val prompt: String, val rubric: String, val grade: String = "claude", override val grammar: String? = null) : Exercise

    @Serializable @SerialName("multi")
    data class Multi(
        val prompt: String,
        val options: List<String>,
        val answers: List<Int>,
        val explain: String? = null,
        val instruction: String? = null,
        @SerialName("say_options") val sayOptions: Boolean = false,
        override val grammar: String? = null,
    ) : Exercise

    /** Hear Slovene (TTS only, no text shown), type what was said. */
    @Serializable @SerialName("dictation")
    data class Dictation(
        val audio: String, val accept: List<String>, val explain: String? = null, val instruction: String? = null,
        override val grammar: String? = null,
    ) : Exercise

    @Serializable @SerialName("scenario")
    data class Scenario(
        val scene: String,
        val prompt: String,
        val speaker: String? = null,
        val line: String? = null,
        val options: List<String>? = null,
        val answer: Int? = null,
        val accept: List<String>? = null,
        val explain: String? = null,
        override val grammar: String? = null,
    ) : Exercise

    /**
     * Say [say] out loud; graded by speech recognition against [say] and [accept].
     * [show] false hides [say] until the answer, so it is produced from the English [prompt] alone.
     */
    @Serializable @SerialName("speak")
    data class Speak(
        val say: String,
        val prompt: String,
        val accept: List<String> = emptyList(),
        val show: Boolean = true,
        val instruction: String? = null,
        val explain: String? = null,
        override val grammar: String? = null,
    ) : Exercise {
        fun answers(): List<String> = (listOf(say) + accept).distinct()
    }

    /** A type newer than this app. Rendered as a skip card with an update hint. */
    @Serializable @SerialName("__unsupported")
    data class Unsupported(val kind: String) : Exercise
}

// --- learner state (GET /state → read-db.py output) --------------------------------

/**
 * One of the learner's languages, as GET /state sums them up (read-db.py's `languages`, docs/DB_SCRIPTS.md
 * "Languages"): the home language (their town's, [source] "home") and each one practised elsewhere ("visits"), with
 * its own level, words learned and cards due in its own review deck.
 */
data class LanguageSummary(
    /** ISO 639-1, "it"; empty when the profile doesn't say (a template's placeholders). */
    val code: String,
    /** Its English name from the profile ("Italian"), for a code the app has no label of. */
    val name: String,
    val level: String,
    val words: Int,
    val due: Int,
    val source: String,
) {
    val home: Boolean get() = source == HOME

    /** Its name as a label, "Italijanščina · Italian"; one the string tables don't name shows the profile's name. */
    fun label(): String = NAME_KEYS[code]?.let { bi(it) } ?: name.ifEmpty { code }

    /** Its name in one language of the learner's pair ([inTarget] or [inBase]): "Italijanščina". */
    fun nameIn(show: (String) -> String): String = NAME_KEYS[code]?.let(show) ?: name.ifEmpty { code }

    companion object {
        const val HOME = "home"
        const val VISITS = "visits"

        /** The string tables' names of the languages ("languages.it": "Italijanščina" / "Italian" / "Italiano"). */
        private val NAME_KEYS = mapOf("sl" to "languages.sl", "it" to "languages.it", "en" to "languages.en", "de" to "languages.de")

        /** The state's `languages`, the home one first; empty from an older bridge that doesn't send them. */
        fun parseAll(root: JsonObject): List<LanguageSummary> = (root["languages"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
            fun n(k: String) = (o[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() } ?: 0
            LanguageSummary(s("code"), s("name"), s("level").ifEmpty { "A1" }, n("words"), n("due"), s("source").ifEmpty { VISITS })
        }
    }
}

/**
 * A spaced-repetition item with both sides. [front] is Slovene, [back] the meaning.
 * [category] is the item's topic ("greetings", "politeness", a word-pack id, ...), if it has one.
 */
data class ReviewCard(
    val id: String,
    val front: String,
    val back: String,
    val kind: String = "vocabulary",
    val repetitions: Int = 0,
    val lastQuality: Int = 0,
    val category: String? = null,
    /** Added without an answer (looked up in a dialog): it never rusts ([Stats.rusty]). */
    val lookup: Boolean = false,
    /** When its next review is due (the item's `due_date`, else `next_review`); null when the node doesn't say. */
    val due: java.time.LocalDate? = null,
    /** When it was last reviewed (the item's `last_reviewed`); null when never or the node doesn't say. */
    val lastReviewed: java.time.LocalDate? = null,
) {
    /** A failed review ([quality] below 3) leaves this card rusty ([Stats.rusty]). */
    fun rustsAt(quality: Int): Boolean = quality < 3 && kind == "vocabulary" && !lookup
}

/**
 * A mistake pattern of the learner's (mistakes-db.json `error_patterns`): its [id] ("accusative_feminine_a_to_o"), how
 * often it happened, the right and wrong answers in a row since ([rightRun], [wrongRun]), and its examples: what was
 * written, what was right. The grammar book shows the ones its pages' tags match; the app's own traps come from the
 * examples (game/Traps.kt).
 */
data class MistakeNote(
    val id: String,
    val frequency: Int,
    val rightRun: Int = 0,
    val wrongRun: Int = 0,
    val examples: List<Pair<String, String>> = emptyList(),
    /** What kind of slip: mistakes-db's `category` ("grammar", "spelling", "vocabulary") and `subcategory` ("listening"). */
    val category: String? = null,
    val subcategory: String? = null,
    /** The day it last happened (`last_seen`, ISO); null when the database doesn't say. */
    val lastSeen: String? = null,
    /**
     * Its review card (the spaced-repetition item of the same id, an `error_pattern`) was answered right on a later day
     * than it last happened: fixed since, as far as the reviews tell.
     */
    val reviewedRight: Boolean = false,
) {
    /** Its last answer was wrong, and no review got it right since: still a mistake to fix. */
    val open: Boolean get() = wrongRun >= 1 && !reviewedRight
}

/**
 * A review answered (a spaced-repetition item's `review_history`): its [day], its [quality] (0–5, SM-2's) and its card's
 * level ("A1", the item's `difficulty`; null when the card doesn't say). What the readiness for the next level weighs
 * (game/Readiness.kt).
 */
data class ReviewMark(val day: java.time.LocalDate, val quality: Int, val level: String? = null)

data class Dashboard(
    val name: String,
    val level: String,
    val targetLevel: String,
    val streak: Int,
    val achievements: Int,
    val xp: Int,
    /** Vocabulary cards under review, learned or not. */
    val wordsTracked: Int,
    val dueCards: List<ReviewCard>,
    val weakPatterns: List<String>,
    /** Every card with both sides; the source of distractors for review variants. */
    val pool: List<ReviewCard> = emptyList(),
    val daysSinceLastSession: Int = 0,
    /** Minutes practised today, summed over today's sessions in the session log. */
    val minutesToday: Int = 0,
    /** A session was logged today, so today counts for the streak. */
    val practisedToday: Boolean = false,
    /** The learner's daily goal in minutes (learner profile); 0 when not set. */
    val goalMinutes: Int = 0,
    /**
     * Words learned: vocabulary answered right at least once ([Stats.learned]). What "you know N words" means,
     * and what the village's ages wait for.
     */
    val wordsLearned: Int = wordsTracked,
    /**
     * The rusty words ([Stats.rusty]: their last review failed) as cards: not learned until a review gets them
     * right, and the village's next age waits for them; a short review of them polishes them right away. A rusty
     * item the app can't show as a card (no answer) isn't here, so it holds nothing back.
     */
    val rusty: List<ReviewCard> = emptyList(),
    /** The learner's languages (learner profile): their town's (target) and the one explaining (base); sl · en by default. */
    val pair: LangPair = LangPair.DEFAULT,
    /**
     * The language these databases are of (GET /state's `language`): the home language's code for the home state, the
     * other language's for GET /state?language=; null from an older bridge (always the home language then).
     */
    val language: String? = null,
    /** Every language the learner has, the home one first ([LanguageSummary]); empty from an older bridge. */
    val languages: List<LanguageSummary> = emptyList(),
    /** The learner's mistake patterns (mistakes-db), the most frequent first: the grammar book's pages show theirs. */
    val mistakes: List<MistakeNote> = emptyList(),
    /** Every review answered of the cards (not the error patterns'), with its card's level: the readiness weighs them. */
    val reviewMarks: List<ReviewMark> = emptyList(),
    /** The days with a session in the log: the days practised. */
    val practised: Set<java.time.LocalDate> = emptySet(),
    /**
     * The day the learner's current [level] began: the profile's `learner.level_since` (update-db.py sets it when a report
     * changes the level), else the day the profile was made, else the first session's; null when none says.
     */
    val levelSince: java.time.LocalDate? = null,
    /**
     * The vocabulary items that count as learned ([Stats.learned]: answered right at least once, not rusty), by id: the
     * words a building's upgrade asks for count when they're here (companion/GAME.md, "Knowledge builds").
     */
    val learned: Set<String> = emptySet(),
    /**
     * Who the content speaks to (l10n/Learner.kt): the profile's `learner.gender` ("male", "female"; null when it doesn't
     * say: male) and `learner.name_forms` (the cases of [name] the rules get wrong).
     */
    val gender: String? = null,
    val nameForms: Map<String, String> = emptyMap(),
) {
    /** The profile's learner as the content addresses them (l10n/LearnerSetting.kt keeps it). */
    val addressee: si.lanisce.lani.l10n.LearnerSetting.Profile
        get() = si.lanisce.lani.l10n.LearnerSetting.Profile(name.takeIf { it.isNotBlank() }, gender, nameForms)

    /** The languages other than the home one: each has its own review deck and stats. */
    val otherLanguages: List<LanguageSummary> get() = languages.filter { !it.home && it.code.isNotEmpty() }

    /**
     * Review answers ([results]: item id → quality) the node hasn't counted yet: a word answered right counts at once, a
     * rusty one polished, one not learned yet (answered wrong while it was learned) learned; the node's own count comes
     * with the next reload, new rust included.
     */
    fun reviewed(results: List<Pair<String, Int>>): Dashboard {
        val right = results.filter { it.second >= 3 }.map { it.first }.toSet()
        if (right.isEmpty()) return this
        val fresh = (pool + rusty).filter { it.id in right && it.kind == "vocabulary" && it.id !in learned }.map { it.id }.toSet()
        val polished = rusty.filter { it.id in right }
        if (fresh.isEmpty() && polished.isEmpty()) return this
        return copy(rusty = rusty - polished.toSet(), learned = learned + fresh, wordsLearned = wordsLearned + fresh.size)
    }

    /**
     * Words of pack [packId] learned in the app ([results]: word id → quality; [words]: the pack's words), before the node
     * has them: each becomes a card, as the node makes it ([PackSession.card]), and one answered right counts at once.
     * A word the learner has already (its card, or another card with the same word) stays as it is: the node skips it too.
     */
    fun learnedWords(packId: String, words: List<PackWord>, results: List<Pair<String, Int>>): Dashboard {
        val byId = words.associateBy { it.id }
        val ids = pool.map { it.id }.toSet()
        val known = pool.filter { it.kind == "vocabulary" }.flatMap { PackSession.wordKeys(it.front) }.toMutableSet()
        val added = results.mapNotNull { (id, q) ->
            val w = byId[id] ?: return@mapNotNull null
            val item = PackSession.itemId(packId, id)
            if (item in ids || !known.add(PackSession.wordKey(w.word))) return@mapNotNull null
            PackSession.card(packId, w).copy(lastQuality = q, category = packId) to (q >= 3)
        }
        if (added.isEmpty()) return this
        val right = added.filter { it.second }.map { it.first.id }
        return copy(
            pool = pool + added.map { it.first }, learned = learned + right,
            wordsTracked = wordsTracked + added.size, wordsLearned = wordsLearned + right.size,
        )
    }

    /**
     * The learner's level in [language] (a code, "sl"): the home language's is [level]; another's is its own, from
     * [languages] (A1 for one never practised). The storyteller tells a story at it.
     */
    fun levelIn(language: String): String {
        val home = this.language ?: pair.target.code
        if (language == home) return level
        return languages.firstOrNull { it.code == language }?.level ?: "A1"
    }

    companion object {
        private fun JsonElement?.obj() = this as? JsonObject ?: JsonObject(emptyMap())
        private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
        private fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull ?: 0
        private fun dayOf(s: String?) = s?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }

        fun parse(raw: String): Dashboard {
            val root = json.parseToJsonElement(raw).jsonObject
            val db = root["databases"].obj()
            val profile = db["learner_profile"].obj()
            val learner = profile["learner"].obj()
            val stats = db["progress_db"].obj()["overall_stats"].obj()
            val items = db["spaced_repetition"].obj()["items"].obj()
            val due = (root["computed"].obj()["due_review_items"] as? JsonArray).orEmpty()
                .mapNotNull { it.jsonPrimitive.contentOrNull }

            // Only items with both sides make a card; error patterns are drilled via modules.
            fun card(id: String): ReviewCard? {
                val item = items[id].obj()
                // An error pattern's content is the learner's WRONG answer: never show it as something to learn.
                if (item.str("type") == "error_pattern") return null
                val front = item.str("content") ?: return null
                val back = item.str("answer") ?: return null
                return ReviewCard(
                    id, front, back, item.str("type") ?: "vocabulary", item.int("repetitions"), item.int("last_quality"),
                    category = item.str("category")?.trim()?.takeIf { it.isNotEmpty() },
                    lookup = item.str("source") == "lookup",
                    due = dayOf(item.str("due_date") ?: item.str("next_review")),
                    lastReviewed = dayOf(item.str("last_reviewed")),
                )
            }
            val cards = due.mapNotNull(::card).take(20)

            val patterns = db["mistakes_db"].obj()["error_patterns"].obj()
            val weak = patterns.entries
                .filter { (_, p) -> p.obj().int("consecutive_incorrect") >= 1 }
                .sortedByDescending { (_, p) -> p.obj().int("frequency") }
                .map { it.key }

            val today = root["computed"].obj().str("today")?.take(10) ?: java.time.LocalDate.now().toString()
            val sessions = (db["session_log"].obj()["sessions"] as? JsonArray).orEmpty().map { it.obj() }
            val todays = sessions.filter { it.str("date")?.take(10) == today }
            fun day(s: String?) = s?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            val practised = sessions.mapNotNull { day(it.str("date")) }.toSet()
            // the reviews of the cards (an error pattern is the learner's wrong answer, drilled through modules)
            val marks = items.values.map { it.obj() }.filter { it.str("type") != "error_pattern" }.flatMap { item ->
                val level = item.str("difficulty")?.trim()?.takeIf { it.isNotEmpty() }
                (item["review_history"] as? JsonArray).orEmpty().mapNotNull { h ->
                    val o = h.obj()
                    val d = day(o.str("date")) ?: return@mapNotNull null
                    val q = (o["quality"] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() } ?: return@mapNotNull null
                    ReviewMark(d, q, level)
                }
            }

            return Dashboard(
                name = learner.str("name") ?: "",
                level = learner.str("current_level") ?: "A1",
                targetLevel = learner.str("target_level") ?: "B2",
                streak = profile.int("current_streak_days"),
                achievements = (profile["achievements"] as? JsonArray)?.size ?: 0,
                // XP is derived, never stored: 10 per correct answer, 25 per session.
                xp = stats.int("total_correct") * 10 + stats.int("total_sessions") * 25,
                wordsTracked = items.values.count { it.obj().str("type") == "vocabulary" },
                wordsLearned = items.values.count { it.obj().str("type") == "vocabulary" && Stats.learned(it.obj()) },
                learned = items.filter { (_, v) -> v.obj().str("type") == "vocabulary" && Stats.learned(v.obj()) }.keys,
                rusty = items.filter { (_, v) -> v.obj().str("type") == "vocabulary" && Stats.rusty(v.obj()) }.keys.mapNotNull(::card),
                dueCards = cards,
                weakPatterns = weak,
                pool = items.keys.mapNotNull(::card),
                daysSinceLastSession = root["computed"].obj().int("days_since_last_session"),
                minutesToday = todays.sumOf { s -> s["duration_minutes"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0 },
                practisedToday = todays.isNotEmpty(),
                goalMinutes = learner.int("daily_goal_minutes"),
                pair = LangPair.fromProfile(
                    learner.str("target_language_code"), learner.str("target_language"),
                    learner.str("base_language_code"), learner.str("base_language"),
                ),
                language = (root["language"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                languages = LanguageSummary.parseAll(root),
                mistakes = patterns.map { (id, p) ->
                    val o = p.obj()
                    val seen = (o.str("last_seen") ?: o.str("last_occurred"))?.take(10)
                    // its review card: answered right on a later day than the mistake last happened
                    val review = items[id].obj().takeIf { it.str("type") == "error_pattern" }
                    val reviewed = review?.str("last_reviewed")?.take(10)
                    MistakeNote(
                        id, o.int("frequency"), o.int("consecutive_correct"), o.int("consecutive_incorrect"),
                        (o["examples"] as? JsonArray).orEmpty().mapNotNull { e ->
                            val x = e.obj()
                            val wrong = x.str("incorrect") ?: return@mapNotNull null
                            val right = x.str("correct") ?: return@mapNotNull null
                            wrong to right
                        },
                        category = o.str("category")?.takeIf { it.isNotBlank() },
                        subcategory = o.str("subcategory")?.takeIf { it.isNotBlank() },
                        lastSeen = seen,
                        reviewedRight = seen != null && reviewed != null && reviewed > seen && review.int("last_quality") >= 3,
                    )
                }.sortedByDescending { it.frequency },
                reviewMarks = marks,
                practised = practised,
                levelSince = day(learner.str("level_since")) ?: day(profile.str("profile_created")) ?: practised.minOrNull(),
                gender = learner.str("gender")?.trim()?.takeIf { it.isNotEmpty() },
                nameForms = learner["name_forms"].obj().mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap(),
            )
        }
    }
}

@Serializable
data class Release(val versionCode: Int, val versionName: String, val file: String, val notes: String? = null)

// --- events (GET /events) -------------------------------------------------------------

sealed interface BridgeEvent {
    /**
     * [data] is the reply's structured payload (e.g. a roleplay line's sl/en/goals_done);
     * [plan]: the tutor's plan for today (the daily rhythm), pinned on Home until tomorrow.
     */
    data class Reply(
        val conversationId: String,
        val text: String,
        val score: Int?,
        val data: JsonObject? = null,
        val plan: Boolean = false,
    ) : BridgeEvent
    data class ModulePublished(val id: String, val version: Int, val title: String?, val note: String?) : BridgeEvent
    data class PermissionRequest(val requestId: String, val tool: String, val description: String, val preview: String, val at: Long) : BridgeEvent
    data class AppUpdate(val versionCode: Int, val versionName: String, val notes: String?) : BridgeEvent
    data class PackPublished(val id: String, val title: String?, val note: String?) : BridgeEvent
    /** The tutor wrote something to read for the reading corner (at [level]), or ([removed]) took it back. */
    data class ReadingPublished(val id: String, val title: String?, val level: String?, val note: String?, val removed: Boolean = false) : BridgeEvent
    /** The tutor wrote a page of the grammar book, or added to one ([extended]). */
    data class GrammarPublished(val id: String, val title: String?, val note: String?, val extended: Boolean = false) : BridgeEvent
    /** A family member sent Jan a challenge from the family page. */
    data class PartnerChallenge(val id: String, val from: String, val fromSl: String?, val emoji: String, val text: String) : BridgeEvent
    /** The node voiced new content: reload the clip index. */
    data object VoiceUpdated : BridgeEvent
    /**
     * The tutor published (or, with [removed], took back) a scene of the village; with [story], a story the storyteller
     * tells there ([id] the scene where it is told, empty when none is; [title] the story's); with [variant], a new dialog
     * of a happening there (a variant, companion/SCENES.md "Variants": news only with its [note]).
     */
    data class ScenePublished(
        val id: String, val title: String?, val emoji: String?, val note: String?, val removed: Boolean = false, val story: Boolean = false,
        val variant: Boolean = false,
    ) : BridgeEvent
    /** Words became the learner's review items (POST /words, or the tutor added them). */
    data class WordsAdded(val itemIds: List<String>) : BridgeEvent
    /** The node learned more about [word] (the tutor explained it): an open word card can look again. */
    data class LexiconUpdated(val word: String) : BridgeEvent
    /** The tutor explained [sentence]'s grammar (publish_sentence_note): an open sheet of it shows the explanation. */
    data class SentenceExplained(val sentence: String) : BridgeEvent
    /** The tutor published (or took back) how someone who joined the village is introduced ([id]; companion/VILLAGERS.md "Arrivals"). */
    data class ArrivalPublished(val id: String, val note: String?) : BridgeEvent
    /**
     * A guest from a linked town brought something ([kind]: help, gift or trade; [from] their village, [learner] who):
     * the village applies the guest book (GET /towns/guests).
     */
    data class TownGuest(val kind: String, val from: String, val learner: String) : BridgeEvent
    /**
     * A question between towns (data/TownQuestions.kt): [kind] "question", a guest asked the learner one; "answer", the town
     * the learner asked answered. [from] its village, [learner] who, [text] the question or the answer (plain text), [key]
     * the question's.
     */
    data class TownQuestion(val kind: String, val key: String, val from: String, val learner: String, val text: String) : BridgeEvent {
        val who: String get() = learner.ifBlank { from }
    }

    companion object {
        fun parse(data: String): BridgeEvent? {
            val o = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull
            return when (s("type")) {
                "reply" -> Reply(
                    conversationId = s("conversation_id") ?: "main",
                    text = s("text") ?: "",
                    score = (o["data"] as? JsonObject)?.get("score")?.jsonPrimitive?.intOrNull,
                    data = o["data"] as? JsonObject,
                    plan = (o["data"] as? JsonObject)?.get("plan")?.jsonPrimitive?.booleanOrNull == true,
                )
                "module_published" -> ModulePublished(s("id") ?: return null, o["version"]?.jsonPrimitive?.intOrNull ?: 1, s("title"), s("note"))
                "permission_request" -> PermissionRequest(
                    requestId = s("request_id") ?: return null,
                    tool = s("tool_name") ?: "?",
                    description = s("description") ?: "",
                    preview = s("input_preview") ?: "",
                    at = s("at")?.toLongOrNull() ?: 0,
                )
                "pack_published" -> PackPublished(s("id") ?: return null, s("title"), s("note"))
                "reading_published" -> ReadingPublished(s("id") ?: return null, s("title"), s("level"), s("note"))
                "reading_removed" -> ReadingPublished(s("id") ?: return null, null, null, null, removed = true)
                "grammar_published" -> GrammarPublished(s("id") ?: return null, s("title"), s("note"), o["extended"]?.jsonPrimitive?.booleanOrNull == true)
                "partner_challenge" -> PartnerChallenge(s("id") ?: return null, s("from") ?: "", s("from_sl"), s("emoji") ?: "💌", s("text") ?: "")
                "voice_updated" -> VoiceUpdated
                "scene_published" -> ScenePublished(s("id") ?: return null, s("title"), s("emoji"), s("note"))
                "scene_removed" -> ScenePublished(s("id") ?: return null, null, null, null, removed = true)
                "story_published" -> ScenePublished(s("scene").orEmpty(), s("title") ?: s("id") ?: return null, s("emoji"), s("note"), story = true)
                "story_removed" -> ScenePublished("", null, null, null, removed = true, story = true)
                "variant_published" -> ScenePublished(s("scene") ?: return null, s("title"), s("emoji"), s("note"), variant = true)
                "variant_removed" -> ScenePublished(s("scene") ?: return null, null, null, null, removed = true, variant = true)
                "app_update" -> AppUpdate(o["versionCode"]?.jsonPrimitive?.intOrNull ?: 0, s("versionName") ?: "?", s("notes"))
                "words_added" -> WordsAdded((o["item_ids"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
                "lexicon_updated" -> LexiconUpdated(s("word") ?: return null)
                "sentence_explained" -> SentenceExplained(s("sentence") ?: return null)
                "arrival_published" -> ArrivalPublished(s("id") ?: return null, s("note"))
                "arrival_removed" -> ArrivalPublished(s("id") ?: return null, null)
                "town_guest" -> TownGuest(s("kind") ?: return null, s("from").orEmpty(), s("learner").orEmpty())
                "town_question" -> TownQuestion(TownQuestions.QUESTION, s("key") ?: return null, s("from").orEmpty(), s("learner").orEmpty(), s("text").orEmpty())
                "town_answer" -> TownQuestion(TownQuestions.ANSWER, s("key") ?: return null, s("from").orEmpty(), s("learner").orEmpty(), s("text").orEmpty())
                else -> null
            }
        }
    }
}
