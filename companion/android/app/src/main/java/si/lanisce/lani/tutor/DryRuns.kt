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

/**
 * The shadow's dry-run card writes as later reads see them (the matrix, 2026-10: a dry-run edit followed by `card show`
 * returned the old card, so models told Jan "the change didn't seem to save" and four turns looped to the call limit):
 * the day's `card` edits, merges, splits and regrades the shadow answered as done (live they would have written the
 * deck) are laid over the deck before `card` find, show or overlaps reads it, the way card.py writes them: the new
 * texts and an `edits` entry, a merged card gone into the kept one, a split's new card, a review's new grade. Never a
 * write the model asked as a dry run itself (`dry_run: true`: live, card.py writes nothing then), nor a refused one.
 */
object CardOverlay {
    private val WRITES = setOf("edit", "merge", "split", "regrade")
    private val PAREN = Regex("""\s*\(([^)]*)\)""")

    /** The writes of [calls] (a day's tool calls, oldest first) to lay over the deck: their inputs, in order. */
    fun writes(calls: List<ToolCallRecord>): List<JsonObject> = calls.filter { c ->
        c.name == "card" && c.dryRun && !c.isError && c.input.s("action") in WRITES &&
            (c.input["dry_run"] as? JsonPrimitive)?.booleanOrNull != true
    }.map { it.input }

    /** [items] (spaced_repetition's, by id) after [writes], on [today] (the learner's day, YYYY-MM-DD). */
    fun apply(items: JsonObject, writes: List<JsonObject>, today: String): JsonObject {
        if (writes.isEmpty()) return items
        val deck = items.toMutableMap()
        for (w in writes) {
            val id = w.s("card_id")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val item = deck[id] as? JsonObject ?: continue
            when (w.s("action")) {
                "edit" -> deck[id] = retext(item, w, today, "edit")
                "merge" -> {
                    val drop = w.s("drop_id")?.trim()?.takeIf { it.isNotEmpty() && it != id && deck[it] is JsonObject } ?: continue
                    val kept = retext(item, w, today, "merge")
                    val merged = (kept["merged"] as? JsonArray).orEmpty() + buildJsonObject { put("date", today); put("id", drop) }
                    deck[id] = JsonObject(kept + ("merged" to JsonArray(merged)))
                    deck.remove(drop)
                }
                "split" -> {
                    val newId = w.s("new_id")?.trim()?.takeIf { it.isNotEmpty() && it !in deck } ?: continue
                    val front = w.s("new_front")?.trim().orEmpty()
                    val back = w.s("new_back")?.trim().orEmpty().let { b -> w.s("new_note")?.let { withNote(b, it) } ?: b }
                    if (front.isEmpty() || back.isEmpty()) continue
                    deck[id] = retext(item, w, today, "split")
                    deck[newId] = buildJsonObject {
                        put("id", newId)
                        put("type", item.s("type") ?: "vocabulary")
                        put("content", front)
                        put("answer", back)
                        put("category", item.s("category").orEmpty())
                        put("created_date", today)
                        runCatching { java.time.LocalDate.parse(today).plusDays(1).toString() }.getOrNull()?.let { put("due_date", it) }
                        put("interval_days", 1)
                        put("repetitions", 0)
                        put("mastery_level", 0)
                        put("total_reviews", 0)
                        put("split_from", id)
                    }
                }
                "regrade" -> deck[id] = regraded(item, w, today)
            }
        }
        return JsonObject(deck)
    }

    /** card.py's retext: --front, --back, --note (the back's only note), --category; the old texts in `edits`. */
    private fun retext(item: JsonObject, w: JsonObject, today: String, action: String): JsonObject {
        val note = w.s("note")
        var back = w.s("back")?.trim()
        if (note != null) back = withNote(back ?: item.s("answer").orEmpty(), note)
        val after = buildMap<String, String> {
            w.s("front")?.trim()?.let { put("content", it) }
            back?.let { put("answer", it) }
            w.s("category")?.trim()?.let { put("category", it) }
        }.filter { (k, v) -> item.s(k) != v }
        if (after.isEmpty()) return item
        val entry = buildJsonObject {
            put("date", today)
            put("action", action)
            for (k in after.keys) item[k]?.let { put(k, it) }
            w.s("reason")?.trim()?.takeIf { it.isNotEmpty() }?.let { put("reason", it) }
        }
        val edits = (item["edits"] as? JsonArray).orEmpty() + entry
        return JsonObject(item + after.mapValues { JsonPrimitive(it.value) } + ("edits" to JsonArray(edits)))
    }

    /** card.py's regrade: the review on `date` (else the last) with its new quality, `regraded` its old one; a card never reviewed, its first grade. */
    private fun regraded(item: JsonObject, w: JsonObject, today: String): JsonObject {
        val quality = (w["quality"] as? JsonPrimitive)?.intOrNull ?: return item
        val history = (item["review_history"] as? JsonArray).orEmpty().map { it as? JsonObject ?: return item }
        if (history.isEmpty()) {
            val old = item["last_quality"]
            val entry = buildJsonObject { put("date", today); put("action", "regrade"); old?.let { put("last_quality", it) } }
            return JsonObject(item + ("last_quality" to JsonPrimitive(quality)) + ("edits" to JsonArray((item["edits"] as? JsonArray).orEmpty() + entry)))
        }
        val date = w.s("date")?.trim()
        val at = if (date != null) history.indexOfLast { it.s("date")?.take(10) == date }.takeIf { it >= 0 } ?: return item else history.lastIndex
        val old = history[at]["quality"]
        val changed = JsonObject(history[at] + ("quality" to JsonPrimitive(quality)) + ("regraded" to buildJsonObject { old?.let { put("from", it) }; put("date", today) }))
        val next = history.toMutableList().also { it[at] = changed }
        val last = if (at == history.lastIndex) mapOf("last_quality" to JsonPrimitive(quality)) else emptyMap()
        return JsonObject(item + ("review_history" to JsonArray(next)) + last)
    }

    /** card.py's with_note: the back's meaning with [note] as its only note ("" removes the notes). */
    private fun withNote(back: String, note: String): String {
        val base = if (PAREN.containsMatchIn(back)) PAREN.replace(back, "").trim().ifEmpty { back } else back.trim()
        return if (note.isNotBlank()) "$base (${note.trim()})" else base
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
