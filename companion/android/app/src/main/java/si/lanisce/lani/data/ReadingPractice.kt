package si.lanisce.lani.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.culture.ReadingFile

/**
 * Reading practice the app reports to the node (companion/README.md, `POST /readings/done`): a reading's questions
 * answered, a story heard to its end by the fire, a reading aloud. The bridge persists each as a session with
 * update-db.py (the learner's reading skill; a reading aloud their speaking too), and the tutor hears a summary. A node
 * from before readings (no `/readings`) gets it as a `session_end` for the tutor to persist instead. Pure.
 */
object ReadingPractice {
    const val READING = "reading"
    const val STORY = "story"
    const val ALOUD = "aloud"

    /** The readings GET /readings serves, each read on its own (one the app can't read is left out). */
    fun parseServed(raw: String): List<ReadingFile> =
        (runCatching { json.parseToJsonElement(si.lanisce.lani.l10n.Learner.current.renderJson(raw)) }.getOrNull() as? JsonArray).orEmpty().mapNotNull { e ->
            runCatching { json.decodeFromJsonElement(ReadingFile.serializer(), e) }.getOrNull()
        }

    private fun base(kind: String, id: String, title: String?, level: String?, exercises: Int, correct: Int, minutes: Int) = buildJsonObject {
        put("kind", kind)
        put("id", id)
        title?.takeIf { it.isNotBlank() }?.let { put("title", it.take(200)) }
        level?.takeIf { Regex("[ABC][12]").matches(it) }?.let { put("level", it) }
        put("exercises", exercises.coerceIn(1, 200))
        put("correct", correct.coerceIn(0, exercises.coerceIn(1, 200)))
        put("duration_minutes", minutes.coerceIn(1, 240))
    }

    /**
     * How often the text was brought back while its questions were asked ([looks], companion/GAME.md "Read first, then
     * answer"), when it was: a node from before it ignores the field.
     */
    private fun looked(looks: Int): Map<String, JsonPrimitive> = if (looks > 0) mapOf("looks" to JsonPrimitive(looks.coerceAtMost(99))) else emptyMap()

    /**
     * Reading [r]'s questions answered, [verdicts] one a question (the first pick), in [minutes] on screen, the text looked
     * at again [looks] times; each question's type from the reading.
     */
    fun reading(r: ReadingFile, title: String, verdicts: List<Verdict>, minutes: Int, looks: Int = 0): JsonObject {
        val right = verdicts.count { it == Verdict.CORRECT }
        return JsonObject(base(READING, r.id, title, r.level, verdicts.size, right, minutes) + ("questions" to buildJsonArray {
            verdicts.take(10).forEachIndexed { i, v ->
                addJsonObject {
                    r.questions.getOrNull(i)?.type?.let { put("type", it) }
                    put("right", v == Verdict.CORRECT)
                }
            }
        }) + looked(looks))
    }

    /** What a story's notebook entry is called as a reading ("book:zlatorog"), and in the village's `read`. */
    const val BOOK = "book:"

    /**
     * A story of the notebook tested ("🎯 Preveri se", [id] its story's), its questions (the telling's turns) answered at [level],
     * [verdicts] one a question, in [minutes] on screen, the text looked at again [looks] times: a reading like the
     * corner's, the node's `/readings/done` as it is.
     */
    fun book(id: String, title: String, level: String, verdicts: List<Verdict>, minutes: Int, looks: Int = 0): JsonObject {
        val right = verdicts.count { it == Verdict.CORRECT }
        return JsonObject(base(READING, "$BOOK$id", title, level, verdicts.size, right, minutes) + ("questions" to buildJsonArray {
            verdicts.take(10).forEach { v -> addJsonObject { put("right", v == Verdict.CORRECT) } }
        }) + looked(looks))
    }

    /**
     * A story heard to its end: its [turns] (the learner's choices), [missed] of them not right the first time, in
     * [minutes]. A story told without a turn counts as one, heard.
     */
    fun story(id: String, title: String, level: String?, turns: Int, missed: Int, minutes: Int): JsonObject {
        val n = turns.coerceAtLeast(1)
        return base(STORY, id, title, level, n, (n - missed.coerceIn(0, turns)).coerceAtLeast(if (turns == 0) 1 else 0), minutes)
    }

    /** Reading [r] (or a part of it) read aloud: [takes], the last of each part read; in [minutes] on screen. */
    fun aloud(r: ReadingFile, title: String, takes: List<ReadAloud.Take>, minutes: Int): JsonObject {
        val s = ReadAloud.score(takes)
        val by = if (takes.any { it.by == ReadAloud.By.NODE }) "node" else "phone"
        return JsonObject(base(ALOUD, r.id, title, r.level, s.sentences, s.readWell, minutes) + ("aloud" to buildJsonObject {
            put("words", s.words)
            put("right", s.right)
            put("misread", s.misread)
            put("skipped", s.skipped)
            s.wpm?.let { put("wpm", it.coerceIn(0, 400)) }
            put("recognizer", by)
            putJsonArray("misses") {
                ReadAloud.misses(takes).forEach { (word, heard) ->
                    addJsonObject {
                        put("word", word.take(60))
                        heard?.let { put("heard", it.take(60)) }
                    }
                }
            }
        }))
    }

    /**
     * [body] (one of the above) as a `session_end` for a node from before readings: the summary, and the report the tutor
     * persists with update-db.py (reading, and a reading aloud's speaking too).
     */
    fun sessionEnd(body: JsonObject, summary: String): Pair<String, JsonObject> {
        val kind = (body["kind"] as? JsonPrimitive)?.content ?: READING
        val skills = if (kind == ALOUD) listOf("reading", "speaking") else listOf("reading")
        val score = buildJsonObject {
            put("exercises", body["exercises"] ?: JsonPrimitive(1))
            put("correct", body["correct"] ?: JsonPrimitive(0))
            put("time_minutes", body["duration_minutes"] ?: JsonPrimitive(1))
        }
        val data = buildJsonObject {
            put("reading_practice", body)
            putJsonObject("report") {
                put("command_used", "/lani-app-" + if (kind == ALOUD) "read-aloud" else kind)
                putJsonArray("skills_practiced") { skills.forEach { add(it) } }
                putJsonObject("skill_scores") { skills.forEach { put(it, score) } }
                put("duration_minutes", body["duration_minutes"] ?: JsonPrimitive(1))
            }
        }
        return "$summary (reading practice in the app: persist it as the report says, with update-db.py)" to data
    }

    /** A line about [body] for the chat and the tutor: "Prebral · Read "Jutro v vasi": 3/4". */
    fun summary(body: JsonObject): String {
        val kind = (body["kind"] as? JsonPrimitive)?.content ?: READING
        val title = (body["title"] as? JsonPrimitive)?.content ?: (body["id"] as? JsonPrimitive)?.content.orEmpty()
        val n = (body["exercises"] as? JsonPrimitive)?.content
        val ok = (body["correct"] as? JsonPrimitive)?.content
        return when (kind) {
            ALOUD -> "Read \"$title\" aloud in the app: $ok/$n sentences read well"
            STORY -> "Heard the story \"$title\" to its end: $ok/$n turns right"
            else -> "Read \"$title\" in the app: $ok/$n questions right"
        }
    }
}
