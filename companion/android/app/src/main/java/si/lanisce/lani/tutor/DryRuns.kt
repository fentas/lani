package si.lanisce.lani.tutor

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

/**
 * What a write gives the model in shadow mode: the live tool's success, made from the input alone, as the live tool
 * answers it: the bridge (publish_gloss, publish_sentence_note, set_rhythm), update-db.py (record_session), card.py
 * (card's writes) and the app's report flow (report_problem, plan 5 "Reports"). The tutor must behave as it would live
 * (plan 5, phase 0), so nothing here says the write didn't happen: the call's record keeps `dryRun = true`, which the
 * panel and the export show. A live refusal that the result's shape depends on (a card write without its card, a report
 * without a session that adds no words) is refused as live refuses it.
 */
object DryRuns {
    /** A write's result: the live success, or the live refusal ([isError]). */
    data class Result(val text: String, val isError: Boolean = false)

    /** The bridge's language names (companion/bridge/src/learners.ts `LANGUAGES`). */
    private val LANGUAGES = mapOf(
        "sl" to "Slovene", "it" to "Italian", "en" to "English", "de" to "German", "hr" to "Croatian", "sr" to "Serbian",
        "bs" to "Bosnian", "fr" to "French", "es" to "Spanish", "pt" to "Portuguese", "nl" to "Dutch", "pl" to "Polish",
        "cs" to "Czech", "sk" to "Slovak", "hu" to "Hungarian", "uk" to "Ukrainian", "ru" to "Russian",
    )

    /** The rhythm's defaults (companion/bridge/src/rhythm.ts `rhythmSettings`), in its order. */
    private val RHYTHM = listOf(
        "enabled" to JsonPrimitive(true), "morning" to JsonPrimitive("07:30"), "evening" to JsonPrimitive("20:30"),
        "weekly_day" to JsonPrimitive(0), "weekly" to JsonPrimitive("19:00"),
    )

    /** update-db.py's practice fields, which a report with `record_session: false` must not have (SESSION_FIELDS). */
    private val SESSION_FIELDS = listOf(
        "duration_minutes", "skill_scores", "review_results", "errors", "milestones", "skills_practiced", "topics_covered",
        "breakthroughs", "achievements_earned", "level",
    )

    /** card.py's output: JSON indented by two. */
    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    /** [name]'s live result for [input]; [learner] the learner's name, [now] the time (a split card's due day). */
    fun of(name: String, input: JsonObject, learner: String? = null, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Result =
        when (name) {
            "publish_gloss" -> gloss(input)
            "publish_sentence_note" -> sentenceNote(input, learner)
            "set_rhythm" -> rhythm(input)
            "record_session" -> session(input)
            "report_problem" -> report(input)
            "card" -> card(input, now, zone)
            else -> Result("$name: done")
        }

    /** The bridge's publish_gloss: `kept: form → lemma (glosses; language); the next lookup shows it`. */
    private fun gloss(input: JsonObject): Result {
        val glosses = (input["gloss"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
        val lang = input.s("gloss_lang")?.trim()?.lowercase()?.let { LANGUAGES[it] ?: it }
        return Result("kept: ${input.s("form")?.trim()} → ${input.s("lemma")?.trim()} (${glosses.joinToString("; ")}${lang?.let { "; $it" } ?: ""}); the next lookup shows it")
    }

    /** The bridge's publish_sentence_note: `kept: "sentence" (language); the app shows it the next time … long-presses the sentence`. */
    private fun sentenceNote(input: JsonObject, learner: String?): Result {
        val lang = input.s("gloss_lang")?.trim()?.lowercase()?.let { LANGUAGES[it] ?: it }
        val who = learner?.takeIf { it.isNotBlank() } ?: "the learner"
        return Result("kept: \"${input.s("sentence")?.trim()}\"${lang?.let { " ($it)" } ?: ""}; the app shows it the next time $who long-presses the sentence")
    }

    /** The bridge's set_rhythm: the settings after the change, as JSON (the defaults where the input says nothing). */
    private fun rhythm(input: JsonObject): Result =
        Result(pretty.encodeToString(JsonElement.serializer(), JsonObject(RHYTHM.associate { (k, v) -> k to (input[k] ?: v) })))

    /** update-db.py's summary of a session, or of review items added without one. */
    private fun session(input: JsonObject): Result {
        val code = input.s("language")?.trim()?.takeIf { it.isNotEmpty() }
        val language = code?.let { LANGUAGES[it.lowercase()] ?: it.replaceFirstChar { c -> c.uppercaseChar() } }
        if ((input["record_session"] as? JsonPrimitive)?.booleanOrNull == false) {
            val practice = SESSION_FIELDS.filter { truthy(input[it]) }
            if (practice.isNotEmpty()) return Result("[Lani] Error: a report with record_session false adds review items only; remove ${practice.joinToString(", ")}", true)
            val ids = (input["new_vocabulary"] as? JsonArray).orEmpty().mapNotNull { ((it as? JsonObject)?.get("item_id") as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
            if (ids.isEmpty()) return Result("[Lani] Error: 'new_vocabulary' must be a non-empty list of items with an item_id (or 'resurface' non-empty)", true)
            return Result("[Lani] ✅ Added ${ids.size} review item(s)${language?.let { " in $it" } ?: ""} (${ids.joinToString(", ")}); no session recorded")
        }
        val scores = (input["skill_scores"] as? JsonObject).orEmpty().values.mapNotNull { it as? JsonObject }
        val exercises = scores.sumOf { (it["exercises"] as? JsonPrimitive)?.intOrNull ?: 0 }
        val correct = scores.sumOf { (it["correct"] as? JsonPrimitive)?.intOrNull ?: 0 }
        return Result(
            buildString {
                append("[Lani] ✅ Updated 6 databases for session ${input.s("session_id")}${language?.let { " ($it)" } ?: ""}\n")
                append(if (exercises > 0) "[Lani] 📊 This session: $correct/$exercises correct (${Math.round(correct * 100.0 / exercises)}%)" else "[Lani] 📊 No exercises recorded")
            },
        )
    }

    /** The app's report flow, after the learner confirmed: a new discussion in its category (plan 5, "Reports"). */
    private fun report(input: JsonObject): Result {
        val category = if (input.s("kind") == "wish") "Ideas" else "Content fixes"
        return Result("posted: \"${input.s("title")?.trim()}\", a new discussion in Lani's $category (the learner confirmed it)")
    }

    /** card.py's result of a write: what changed, as JSON; `dry_run` as the input asked. */
    private fun card(input: JsonObject, now: Long, zone: ZoneId): Result {
        val action = input.s("action").orEmpty()
        fun missing(vararg keys: String) = keys.firstOrNull { k ->
            when (val v = input[k]) {
                null, JsonNull -> true
                is JsonPrimitive -> v.isString && v.content.isBlank()
                else -> false
            }
        }?.let { Result("[Lani] Error: $action: $it is required", true) }
        val id = input.s("card_id")?.trim()
        val dry = (input["dry_run"] as? JsonPrimitive)?.booleanOrNull == true
        val out = when (action) {
            "edit" -> {
                missing("card_id")?.let { return it }
                val after = texts(input)
                if (after.isEmpty()) return Result("[Lani] Error: edit: nothing to change (give front, back, note or category with a new text)", true)
                buildJsonObject {
                    put("id", id)
                    put("after", after)
                    put("warnings", JsonArray(emptyList()))
                }
            }
            "regrade" -> {
                missing("card_id", "quality")?.let { return it }
                buildJsonObject {
                    put("id", id)
                    put("review", buildJsonObject {
                        input.s("date")?.let { put("date", it) }
                        put("quality", buildJsonObject { put("to", input["quality"]!!) })
                    })
                    put("note", "the review's session keeps its score in progress-db and the session log; only the card changes")
                }
            }
            "merge" -> {
                missing("card_id", "drop_id")?.let { return it }
                val drop = input.s("drop_id")!!.trim()
                if (drop == id) return Result("[Lani] Error: merge: two different cards", true)
                buildJsonObject {
                    put("kept", id)
                    put("dropped", drop)
                    put("after", texts(input))
                    put("warnings", buildJsonArray { add(JsonPrimitive("a review of $drop still in the app (not yet sent) is ignored when it arrives")) })
                }
            }
            "split" -> {
                missing("card_id", "new_id", "new_front", "new_back")?.let { return it }
                val note = input.s("new_note")?.trim()?.takeIf { it.isNotEmpty() }
                buildJsonObject {
                    put("id", id)
                    put("after", texts(input))
                    put("new", buildJsonObject {
                        put("id", input.s("new_id")!!.trim())
                        put("content", input.s("new_front")!!.trim())
                        put("answer", input.s("new_back")!!.trim() + (note?.let { " ($it)" } ?: ""))
                        put("due_date", Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).toString())
                        put("interval_days", 1)
                        put("repetitions", 0)
                        put("mastery_level", 0)
                        put("split_from", id)
                    })
                    put("warnings", JsonArray(emptyList()))
                }
            }
            "forgive" -> {
                missing("pattern_id")?.let { return it }
                buildJsonObject {
                    put("pattern", input.s("pattern_id")!!.trim())
                    val removed = buildJsonObject {
                        input.s("date")?.let { put("date", it) }
                        input.s("answer")?.let { put("incorrect", it) }
                    }
                    if (removed.isNotEmpty()) put("removed", removed)
                }
            }
            else -> return Result("[Lani] Error: card: $action is not a write", true)
        }
        return Result(pretty.encodeToString(JsonElement.serializer(), JsonObject(out + ("dry_run" to JsonPrimitive(dry)))))
    }

    /** A card write's new texts, as card.py names them (front: content, back: answer). */
    private fun texts(input: JsonObject): JsonObject = buildJsonObject {
        input.s("front")?.let { put("content", it.trim()) }
        input.s("back")?.let { put("answer", it.trim()) }
        input.s("note")?.let { put("note", it.trim()) }
        input.s("category")?.let { put("category", it.trim()) }
    }

    /** Python's truth of a report's field (update-db.py `report.get(k)`). */
    private fun truthy(e: JsonElement?): Boolean = when (e) {
        null, JsonNull -> false
        is JsonObject -> e.isNotEmpty()
        is JsonArray -> e.isNotEmpty()
        is JsonPrimitive -> if (e.isString) e.content.isNotEmpty() else e.booleanOrNull ?: (e.doubleOrNull?.let { it != 0.0 } ?: false)
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
