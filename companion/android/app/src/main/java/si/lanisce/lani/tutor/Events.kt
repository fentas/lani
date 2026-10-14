package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The learner's events as the shadow sees them: each write the app delivered to the real tutor's node (the outbox's
 * POST /message, /reviews, /words, /packs/:id/learn, /readings/done, /level) becomes the channel's kind of event
 * (plan 5, "The channel's events"); how it's handled follows the plan's "Events to turns".
 */
object ShadowEvents {
    private val packLearn = Regex("""^/packs/([a-z0-9-]+)/learn$""")

    /**
     * The event of a delivered write ([path], its JSON [body]); null for a write that isn't one of the tutor's events.
     * [reactAtOnce]: reviews and session ends get a turn of their own.
     */
    fun of(id: String, path: String, body: String, at: Long, reactAtOnce: Boolean = false): ShadowEvent? {
        val o = runCatching { tutorJson.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val data = JsonObject(o - "client_id")
        val (kind, conv, text, d) = when {
            path == "/message" -> Quad(o.str("kind") ?: "chat", o.str("conversation_id") ?: "main", o.str("text").orEmpty(), o["data"] as? JsonObject)
            path == "/reviews" -> Quad(if (o["dialog"] is JsonObject) "dialog_words" else "review_done", null, reviewLine(data), data)
            path == "/words" -> Quad("words_added", null, "added a word: ${o.str("sl").orEmpty()} (${o.str("en").orEmpty()})", data)
            packLearn.matches(path) -> Quad("words_learned", null, "learned words of the pack ${packLearn.find(path)!!.groupValues[1]}", JsonObject(data + ("pack_id" to JsonPrimitive(packLearn.find(path)!!.groupValues[1]))))
            path == "/readings/done" -> Quad("reading_done", null, "reading practice done", data)
            path == "/level" -> Quad("level_up", null, "a new level", data)
            else -> return null
        }
        val (handling, note) = handling(kind, reactAtOnce)
        return ShadowEvent(id = id, kind = kind, at = at, path = path, conversationId = conv, text = text, data = d, handling = handling, note = note)
    }

    /** A role-play's turn or end (sent past the outbox, straight to the node): recorded, not run (phase B). */
    fun roleplay(id: String, kind: String, conversationId: String, text: String, data: JsonObject?, at: Long) =
        ShadowEvent(id, kind, at, "/message", conversationId, text, data, Handling.RECORDED, "role-play: phase B (seen, not run)")

    fun handling(kind: String, reactAtOnce: Boolean): Pair<Handling, String?> = when (kind) {
        "chat", "answer" -> Handling.AT_ONCE to null
        "session_end", "review_done" -> (if (reactAtOnce) Handling.AT_ONCE else Handling.DIGEST) to null
        "villager_arrived" -> Handling.RECORDED to "an arrival: the culture pack's template plays until phase C"
        "roleplay", "roleplay_end" -> Handling.RECORDED to "role-play: phase B (seen, not run)"
        else -> Handling.DIGEST to null
    }

    private fun reviewLine(o: JsonObject): String {
        val results = (o["results"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("quality")?.let { q -> (q as? JsonPrimitive)?.intOrNull } }
        val wrong = results.count { it < 3 }
        return "a review: ${results.size} card(s), $wrong wrong${o.str("from")?.let { " (from $it)" } ?: ""}"
    }

    /**
     * Why a digest of [events] is worth a call (plan 5: "a digest with nothing worth saying (no answers typed wrong, no
     * milestone) ends without a call"), or null when it isn't.
     */
    fun worth(events: List<ShadowEvent>): String? {
        if (events.any { it.kind == "session_end" }) return "a practice run ended"
        if (events.any { it.kind == "level_up" }) return "a new level"
        val wrong = events.filter { it.kind == "review_done" || it.kind == "dialog_words" }.sumOf { e ->
            (e.data?.get("results") as? JsonArray).orEmpty().count { r -> ((r as? JsonObject)?.get("quality") as? JsonPrimitive)?.intOrNull?.let { it < 3 } == true }
        }
        if (wrong > 0) return "$wrong answer(s) wrong"
        val readingWrong = events.filter { it.kind == "reading_done" }.any { e ->
            (e.data?.get("questions") as? JsonArray).orEmpty().any { q -> ((q as? JsonObject)?.get("correct") as? JsonPrimitive)?.booleanOrNull == false }
        }
        if (readingWrong) return "a reading's questions answered wrong"
        return null
    }

    private data class Quad(val kind: String, val conv: String?, val text: String, val data: JsonObject?)
}

/**
 * The turns' user messages (plan 5, "Events to turns": the event's JSON in the channel's shapes, the local time and
 * where the learner is; the first turn of a day also carries the notebook). Text the learner didn't write (none in
 * phase A; family and towns in phase D) would come inside a marked block, as the bridge's untrustedJson does.
 */
object TurnText {
    private val time = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm", Locale.ENGLISH)

    fun now(at: Long, zone: ZoneId, where: String?): String =
        "Local time: ${time.format(Instant.ofEpochMilli(at).atZone(zone))} (${zone.id})." + (where?.let { " The learner is in the app ($it)." } ?: "")

    /** One event as the channel had it: its kind, conversation and id, its text, its data as a JSON block. */
    fun event(e: ShadowEvent, zone: ZoneId): String = buildString {
        append("<event kind=\"${e.kind}\"")
        e.conversationId?.let { append(" conversation_id=\"$it\"") }
        append(" msg_id=\"${e.id}\" at=\"${Instant.ofEpochMilli(e.at).atZone(zone).toOffsetDateTime()}\">\n")
        if (e.text.isNotBlank()) append(e.text.take(8000)).append('\n')
        e.data?.takeIf { it.isNotEmpty() }?.let { append("\n```json\n").append(it.toString().take(12_000)).append("\n```\n") }
        append("</event>")
    }

    /** The day's digest: its events, and what to do with it. */
    fun digest(events: List<ShadowEvent>, why: String, zone: ZoneId): String = buildString {
        append("<digest events=\"${events.size}\" why=\"$why\">\n")
        events.forEach { append(event(it, zone)).append('\n') }
        append("</digest>\n")
        append("The day's routine events since the last digest (already persisted by the app). React only if it helps the learner: ")
        append("a tip on what they typed wrong, a word on a milestone. Answer with the reply tool on conversation_id \"main\", or end the turn without a reply.")
    }

    fun notebook(n: NotebookState): String =
        if (n.text.isBlank()) "<notebook>\n(empty: nothing written yet. Start one from learner_state and what you learn today.)\n</notebook>"
        else "<notebook updated=\"${Instant.ofEpochMilli(n.updatedAt)}\">\n${n.text}\n</notebook>"

    fun lines(title: String, lines: List<String>): String = if (lines.isEmpty()) "" else "$title\n" + lines.joinToString("\n") { "- $it" }

    /** The closing call's message: the notebook rewritten (at the day's end, or before the thread restarts). */
    fun closing(why: String): String =
        "[notebook_write] $why. Rewrite the notebook now with notebook_write: what you know about the learner (goals, level, " +
            "life, the family's names), how they like to learn, recurring mistakes (the mistakes database's pattern ids, what helped), " +
            "plans for today and this week, open promises, and the last days in a few lines. At most about 2,500 tokens. " +
            "Don't reply to the learner."
}
