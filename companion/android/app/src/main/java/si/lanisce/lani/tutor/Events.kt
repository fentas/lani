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
            path == "/reviews" -> Quad(if (o["dialog"] is JsonObject) "dialog_words" else "review_done", null, reviewLine(data), JsonObject(data + ("counts" to reviewCounts(data))))
            path == "/words" -> Quad("words_added", null, "added a word: ${o.str("sl").orEmpty()} (${o.str("en").orEmpty()})", data)
            packLearn.matches(path) -> Quad("words_learned", null, "learned words of the pack ${packLearn.find(path)!!.groupValues[1]}", JsonObject(data + ("pack_id" to JsonPrimitive(packLearn.find(path)!!.groupValues[1]))))
            path == "/readings/done" -> Quad("reading_done", null, "reading practice done", data)
            path == "/cases/done" -> Quad("cases_done", null, "case trainer practice done", data)
            path == "/level" -> Quad("level_up", null, "a new level", data)
            else -> return null
        }
        val (handling, note) = handling(kind, reactAtOnce)
        // a chat's photo (POST /message `image: {id}`, data/Outbox.kt Writes.chatPhoto): the turn sends the phone's copy
        val image = (o["image"] as? JsonObject)?.str("id")?.takeIf { path == "/message" }?.let { ShadowImage(it) }
        return ShadowEvent(id = id, kind = kind, at = at, path = path, conversationId = conv, text = text, data = d, handling = handling, note = note, image = image)
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
        val c = reviewCounts(o)
        fun n(k: String) = (c[k] as? JsonPrimitive)?.intOrNull ?: 0
        val inside = buildList {
            if (n("forms_asked") > 0) add("${n("forms_asked")} form(s) asked of them, ${n("forms_wrong")} wrong")
            if (n("steps") > 0) add("${n("steps")} step(s) of counting and forms cards, ${n("steps_wrong")} wrong")
        }
        return "a review: ${n("cards")} card(s), ${n("cards_wrong")} wrong" +
            (if (inside.isNotEmpty()) "; within those cards (not cards of their own): ${inside.joinToString("; ")}" else "") +
            (o.str("from")?.let { " (from $it)" } ?: "")
    }

    /**
     * A review's numbers, card by card (the matrix, 2026-10: lanes counted form gaps as cards, "17 of 20" where the app
     * said 19 of 20): the cards and those wrong (quality under 3), the forms asked of them and those wrong, the counting
     * and forms cards' steps and cells and those wrong.
     */
    fun reviewCounts(o: JsonObject): JsonObject {
        val results = (o["results"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("quality")?.let { q -> (q as? JsonPrimitive)?.intOrNull } }
        val forms = (o["forms"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun right(e: JsonObject) = (e["right"] as? JsonPrimitive)?.booleanOrNull
        val steps = ((o["counting"] as? JsonArray).orEmpty().flatMap { ((it as? JsonObject)?.get("steps") as? JsonArray).orEmpty() } +
            (o["forms_cards"] as? JsonArray).orEmpty().flatMap { ((it as? JsonObject)?.get("cells") as? JsonArray).orEmpty() }).mapNotNull { it as? JsonObject }
        return kotlinx.serialization.json.buildJsonObject {
            put("cards", JsonPrimitive(results.size))
            put("cards_wrong", JsonPrimitive(results.count { it < 3 }))
            if (forms.isNotEmpty()) {
                put("forms_asked", JsonPrimitive(forms.size))
                put("forms_wrong", JsonPrimitive(forms.count { right(it) == false }))
            }
            if (steps.isNotEmpty()) {
                put("steps", JsonPrimitive(steps.size))
                put("steps_wrong", JsonPrimitive(steps.count { right(it) == false }))
            }
        }
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

    /**
     * One event as the channel had it: its kind, conversation and id, its text, its data as a JSON block. A chat's photo
     * is the image block before it ([ShadowImages.block]); its data says so (`image`, where the bridge gives a path).
     */
    fun event(e: ShadowEvent, zone: ZoneId, imageSent: Boolean = false): String = buildString {
        append("<event kind=\"${e.kind}\"")
        e.conversationId?.let { append(" conversation_id=\"$it\"") }
        append(" msg_id=\"${e.id}\" at=\"${Instant.ofEpochMilli(e.at).atZone(zone).toOffsetDateTime()}\">\n")
        if (e.text.isNotBlank()) append(e.text.take(8000)).append('\n')
        val data = e.image?.let { img ->
            val said = if (imageSent) "the photo is the image block just before this event" else "the photo isn't in this message"
            JsonObject(e.data.orEmpty() + ("image" to kotlinx.serialization.json.buildJsonObject { put("id", JsonPrimitive(img.id)); put("in_this_message", JsonPrimitive(said)) }))
        } ?: e.data
        data?.takeIf { it.isNotEmpty() }?.let { append("\n```json\n").append(it.toString().take(12_000)).append("\n```\n") }
        append("</event>")
    }

    /** The day's digest: its events, and what to do with it. */
    fun digest(events: List<ShadowEvent>, why: String, zone: ZoneId): String = buildString {
        append("<digest events=\"${events.size}\" why=\"$why\">\n")
        events.forEach { append(event(it, zone)).append('\n') }
        append("</digest>\n")
        append("The day's routine events since the last digest. The app has persisted all of them except a session_end: persist ")
        append("each session_end with record_session first (the session-end topic). React only if it helps the learner: ")
        append("a tip on what they typed wrong, a word on a milestone. Answer with the reply tool on conversation_id \"main\", or end the turn without a reply.")
    }

    fun notebook(n: NotebookState): String =
        if (n.text.isBlank()) "<notebook>\n(empty: nothing written yet. Start one from learner_state and what you learn today.)\n</notebook>"
        else "<notebook updated=\"${Instant.ofEpochMilli(n.updatedAt)}\">\n${n.text}\n</notebook>"

    fun lines(title: String, lines: List<String>): String = if (lines.isEmpty()) "" else "$title\n" + lines.joinToString("\n") { "- $it" }

    /**
     * What the shown replies are, said outright (the matrix, 2026-10: GPT-6 Luna took them as its own and "corrected"
     * them, retracting correct rules and a grammar page another tutor had added; Haiku named "the other tutor").
     */
    private const val NOT_YOURS = "These replies are not your words and not your actions: never correct, retract, " +
        "apologise for or claim them (nor what they say was done: a card fixed, a page added), and never mention them or " +
        "another tutor to the learner. Take them as what the learner saw; their next messages answer them."

    /** [shown]'s title on the day's thread: the replies since its last turn. */
    const val SHOWN = "What the learner was shown in reply since your last turn (another tutor answered). $NOT_YOURS"

    /** [shown]'s title in a stateless turn, next to "Today so far". */
    const val SHOWN_TODAY = "What the learner was shown in reply today (another tutor answered). $NOT_YOURS"

    /** A reply in [shown] is cut here. */
    const val SHOWN_MAX = 1_500

    /**
     * Of [real] (a day's real replies), those the learner saw after [after] and before [before] (what their next event
     * answers), the newest [max]: never one to [own] (the turn's own events: a turn doesn't see the real answer to what it
     * answers), nor a role-play's lines (rp-…, recorded only).
     */
    fun shownOf(real: List<RealReply>, after: Long, before: Long, own: Collection<String>, max: Int = 5): List<RealReply> =
        real.filter { r ->
            r.reply.at > after && r.reply.at < before && r.eventId !in own && !r.reply.conversationId.startsWith("rp-") && r.reply.text.isNotBlank()
        }.sortedBy { it.reply.at }.takeLast(max)

    /**
     * What the learner was actually shown: the real tutor's [replies] (the shadow's own never reach them), each in a
     * `<shown>` block with its time, conversation and the event it answers, cut at [SHOWN_MAX]. Empty without any.
     */
    fun shown(replies: List<RealReply>, zone: ZoneId, title: String = SHOWN): String = if (replies.isEmpty()) "" else buildString {
        append(title)
        for (r in replies) {
            val at = Instant.ofEpochMilli(r.reply.at).atZone(zone).toLocalTime().withSecond(0).withNano(0)
            append("\n<shown at=\"$at\" conversation_id=\"${r.reply.conversationId}\"")
            r.eventId?.let { append(" in_reply_to=\"$it\"") }
            val text = r.reply.text.trim()
            append(">\n").append(if (text.length > SHOWN_MAX) text.take(SHOWN_MAX).trimEnd() + " …" else text).append("\n</shown>")
        }
    }

    /** The closing call's message: the notebook rewritten (at the day's end, or before the thread restarts). */
    fun closing(why: String): String =
        "[notebook_write] $why. Rewrite the notebook now with notebook_write: what you know about the learner (goals, level, " +
            "life, the family's names), how they like to learn, recurring mistakes (the mistakes database's pattern ids, what helped), " +
            "plans for today and this week, open promises, and the last days in a few lines. At most about 2,500 tokens. " +
            "Don't reply to the learner."
}
