package si.lanisce.lani.tutor.live

import kotlinx.serialization.json.JsonObject
import si.lanisce.lani.tutor.Handling
import si.lanisce.lani.tutor.ShadowEvent
import si.lanisce.lani.tutor.ShadowImage
import si.lanisce.lani.tutor.str
import si.lanisce.lani.tutor.tutorJson

/**
 * The channel's notifications as the live tutor handles them (plan 5, "The channel's events", "Events to turns"): each
 * inbox item becomes an event of its kind; how it is handled follows the plan's table, as far as phase A goes:
 * - at once: a chat, an answer (the learner waits for the grade), the rhythm (morning, evening, the week), a level-up,
 *   the dictionary's gaps (stateless, on the routine model), a role-play's turns (their own short thread), the family's
 *   and the towns' events (their untrusted text marked as the bridge marks it), and anything new;
 * - into the day's digest: reviews and session ends (at once with "react at once"), words, readings, a dialog's words;
 * - content writing (phase C: an arrival, dialog variants, stories running low): the studio's job (tutor/live/Studio),
 *   acknowledged when it ends; on a bridge without the studio's side it stays in the inbox and goes back to the Claude
 *   Code session when the learner switches back, as before.
 */
object LiveEvents {
    /** The event's id for inbox item [id] ([inboxId] reads it back). */
    fun idOf(id: Long): String = "in_$id"

    /** The inbox id of event [eventId], or null (not an inbox event). */
    fun inboxId(eventId: String): Long? = eventId.removePrefix("in_").takeIf { eventId.startsWith("in_") }?.toLongOrNull()

    /** The kinds that write content (phase C): the studio's jobs (left for Claude Code on a bridge without the studio). */
    val CONTENT = setOf("villager_arrived", "dialog_variants_heard", "stories_low")

    /** The routine kinds that wait for the day's digest. */
    val ROUTINE = setOf("words_added", "words_learned", "reading_done", "cases_done", "dialog_words")

    fun handling(kind: String, reactAtOnce: Boolean): Pair<Handling, String?> = when (kind) {
        in CONTENT -> Handling.RECORDED to "content writing: the studio's job (on a bridge without the studio: left for Claude Code)"
        "session_end", "review_done" -> (if (reactAtOnce) Handling.AT_ONCE else Handling.DIGEST) to null
        in ROUTINE -> Handling.DIGEST to null
        else -> Handling.AT_ONCE to null
    }

    /** Inbox item [item] as an event: its kind, conversation and time, the channel's text as it came, its photo's id. */
    fun of(item: LiveBridge.Item, reactAtOnce: Boolean): ShadowEvent {
        val kind = item.meta["kind"]?.takeIf { it.isNotBlank() } ?: "chat"
        val (handling, note) = handling(kind, reactAtOnce)
        return ShadowEvent(
            id = idOf(item.id), kind = kind, at = item.at.takeIf { it > 0 } ?: System.currentTimeMillis(), path = "inbox",
            conversationId = item.meta["conversation_id"]?.takeIf { it.isNotBlank() }, text = item.content, handling = handling, note = note,
            image = photoOf(item.content)?.let { ShadowImage(it) },
        )
    }

    /** The data block of the channel's text (```json …```), when it has one. */
    fun data(content: String): JsonObject? =
        Regex("```json\\n([\\s\\S]*?)\\n```").find(content)?.groupValues?.get(1)?.let { runCatching { tutorJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    /** The chat photo's id an event carries (data.image.id: the bridge's copy; the phone has its own by that id). */
    fun photoOf(content: String): String? = (data(content)?.get("image") as? JsonObject)?.str("id")

    /**
     * Why a digest of [events] is worth a call (the plan: "a digest with nothing worth saying ends without a call"; the
     * quality pass: a notable review and new words are worth a word too), or null.
     */
    fun worth(events: List<ShadowEvent>): String? {
        if (events.any { it.kind == "session_end" }) return "a practice run ended"
        if (events.any { it.kind == "level_up" }) return "a new level"
        var wrong = 0
        var notable = 0
        for (e in events.filter { it.kind == "review_done" }) {
            val m = Regex("""(\d+)/(\d+) recalled""").find(e.text) ?: continue
            val (right, all) = m.destructured.let { (a, b) -> a.toInt() to b.toInt() }
            wrong += (all - right).coerceAtLeast(0)
            if (right == all && all >= 20) notable = maxOf(notable, all)
        }
        if (wrong > 0) return "$wrong answer(s) wrong"
        if (notable > 0) return "a notable review: all $notable right"
        if (events.any { it.kind == "words_learned" }) return "words learned from a pack"
        return null
    }
}
