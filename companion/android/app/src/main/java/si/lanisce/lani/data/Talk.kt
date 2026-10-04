package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Grading.Verdict
import java.util.UUID

// --- role-play scenarios (lani.scenario/v0, see companion/bridge/src/scenarios.ts) -----

@Serializable
data class VocabHint(val sl: String, val en: String)

@Serializable
data class Scenario(
    val id: String,
    val title: String,
    val emoji: String = "🗣️",
    val level: String = "A1",
    val setting: String,
    /** Who the tutor plays: "Tašča Marija, the mother-in-law, …". */
    val role: String,
    /** The character's gender: "female" | "male" (node voice clips, and the fallback of [speaker]). */
    val voice: String? = null,
    /** A voice from the node's voice cast ("grandma"; a villager's role-play carries theirs). */
    val speaker: String? = null,
    val goals: List<String>,
    @SerialName("opener_sl") val openerSl: String,
    @SerialName("opener_en") val openerEn: String,
    @SerialName("vocabulary_hints") val vocabularyHints: List<VocabHint> = emptyList(),
    /** curated | tutor */
    val source: String = "curated",
) {
    /** The character's name: the role up to its first comma. */
    val character: String get() = role.substringBefore(',').trim().take(40)

    /** The node clip voice for the character's lines: their speaker, else their gender ([characterGender]). */
    val characterVoice: String get() = speaker ?: characterGender

    /** The villager this is a talk with ("villager:<id>"), whose own voice reads the character's lines. */
    val villager: String? get() = id.removePrefix("villager:").takeIf { id.startsWith("villager:") }

    /** The character's gender voice: where [characterVoice] falls back to. */
    val characterGender: String get() = if (voice == Clips.MALE) Clips.MALE else Clips.FEMALE
}

fun parseScenarios(raw: String): List<Scenario> = json.decodeFromString(raw)
fun parseScenario(raw: String): Scenario = json.decodeFromString(raw)

/**
 * The tutor's `data` on a roleplay reply. An in-character line has [sl] (and [en]); a hint
 * ([hint] true) is a line Jan could say; after roleplay_end the reply [text] is the debrief.
 */
data class RoleplayReply(
    val text: String,
    val sl: String,
    val en: String? = null,
    val correction: String? = null,
    /** Goals reached so far; null when the tutor didn't say. */
    val goalsDone: List<Int>? = null,
    val end: Boolean = false,
    val hint: Boolean = false,
    /** A line of the scene (or a hint), not the debrief (`debrief: true`); late ones are dropped after the end. */
    val inCharacter: Boolean = hint,
) {
    companion object {
        fun parse(text: String, data: JsonObject?): RoleplayReply {
            fun s(k: String) = (data?.get(k) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            fun b(k: String) = (data?.get(k) as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "true") } ?: false
            val goals = (data?.get("goals_done") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.let { p -> p.intOrNull ?: p.contentOrNull?.toIntOrNull() } }
            return RoleplayReply(
                text = text,
                sl = s("sl") ?: text.trim(),
                en = s("en"),
                correction = s("correction"),
                goalsDone = goals,
                end = b("end"),
                hint = b("hint"),
                inCharacter = (s("sl") != null || b("hint")) && !b("debrief"),
            )
        }
    }
}

// --- a conversation, as a pure state + reducer ------------------------------------------

data class TalkLine(
    val id: Int,
    val fromMe: Boolean,
    val sl: String,
    val en: String? = null,
    /** On Jan's lines: the tutor's gentle correction, if there was a real mistake. */
    val correction: String? = null,
    val typed: Boolean = false,
)

data class TalkHint(val sl: String, val en: String?)

enum class TalkPhase { CHAT, ENDING, DONE }

data class TalkState(
    val scenario: Scenario,
    val conversationId: String,
    val startedAt: Long,
    val lines: List<TalkLine>,
    val goalsDone: Set<Int> = emptySet(),
    /** Waiting for the character's answer since [waitingSince]. */
    val waiting: Boolean = false,
    val waitingSince: Long = 0,
    val hint: TalkHint? = null,
    val hintLoading: Boolean = false,
    val hintsUsed: Int = 0,
    /** The tutor said the scene reached its natural end. */
    val sceneOver: Boolean = false,
    val phase: TalkPhase = TalkPhase.CHAT,
    val debrief: String? = null,
) {
    /** Jan's lines so far; the next one is turn [turn] + 1. */
    val turn: Int get() = lines.count { it.fromMe }
    val corrections: Int get() = lines.count { it.fromMe && it.correction != null }
}

object Talk {
    fun newConversationId() = "rp-" + UUID.randomUUID()

    /** The chat under a role-play's debrief ("Ask your tutor") has its own conversation: "<role-play's>-debrief". */
    fun debriefThread(conversationId: String) = "$conversationId$THREAD"

    fun isDebriefThread(conversationId: String) = conversationId.startsWith("rp-") && conversationId.endsWith(THREAD)

    private const val THREAD = "-debrief"
    private const val MAX_DEBRIEF = 2_000

    /**
     * `data` of a chat message under the debrief: `about_roleplay` ([aboutRoleplay]) tells the tutor which role-play
     * the chat continues.
     */
    fun debriefData(st: TalkState): JsonObject = buildJsonObject { put("about_roleplay", aboutRoleplay(st)) }

    /** The role-play a chat is about: its scenario, the role-play's conversation, and the debrief the tutor gave. */
    fun aboutRoleplay(st: TalkState): JsonObject = buildJsonObject {
        put("scenario_id", st.scenario.id)
        put("title", st.scenario.title)
        put("conversation_id", st.conversationId)
        put("turns", st.turn)
        put("goals_done", buildJsonArray { st.goalsDone.sorted().forEach { add(it) } })
        put("goals_total", st.scenario.goals.size)
        st.debrief?.let { put("debrief", it.take(MAX_DEBRIEF)) }
    }

    /** A fresh conversation: the character has already said the opener. */
    /** [onScreen]: ScreenClock.app.now(), what the duration counts from (the wall clock [now] by default, in tests). */
    fun start(s: Scenario, now: Long, conversationId: String = newConversationId(), onScreen: Long = now) =
        TalkState(s, conversationId, onScreen, listOf(TalkLine(0, fromMe = false, sl = s.openerSl, en = s.openerEn)))

    fun said(st: TalkState, text: String, typed: Boolean, now: Long): TalkState = st.copy(
        lines = st.lines + TalkLine(st.lines.size, fromMe = true, sl = text.trim(), typed = typed),
        waiting = true,
        waitingSince = now,
        hint = null,
        hintLoading = false,
    )

    fun askedHint(st: TalkState) = st.copy(hintLoading = true, hint = null, hintsUsed = st.hintsUsed + 1)

    fun replied(st: TalkState, r: RoleplayReply): TalkState = when {
        st.phase == TalkPhase.DONE || (st.phase == TalkPhase.ENDING && r.inCharacter) -> st
        st.phase == TalkPhase.ENDING -> st.copy(phase = TalkPhase.DONE, debrief = r.text.trim())
        r.hint || (st.hintLoading && !st.waiting) -> st.copy(hint = TalkHint(r.sl, r.en), hintLoading = false)
        else -> {
            val mine = st.lines.indexOfLast { it.fromMe }
            val lines = st.lines.mapIndexed { i, l -> if (i == mine && r.correction != null) l.copy(correction = r.correction) else l }
            val valid = r.goalsDone.orEmpty().filter { it in st.scenario.goals.indices }
            st.copy(
                lines = lines + TalkLine(lines.size, fromMe = false, sl = r.sl, en = r.en),
                goalsDone = st.goalsDone + valid, // goals never untick
                waiting = false,
                sceneOver = st.sceneOver || r.end,
            )
        }
    }

    fun ending(st: TalkState) = st.copy(phase = TalkPhase.ENDING, waiting = false, hintLoading = false)

    /** One village entry per line Jan said: correct unless the tutor corrected it. */
    fun verdicts(st: TalkState): List<Verdict> =
        st.lines.filter { it.fromMe }.map { if (it.correction == null) Verdict.CORRECT else Verdict.ALMOST }

    /** `data` of a roleplay message: Jan's line, already in [st] (or a hint request when [heard] is null). */
    fun turnData(st: TalkState, heard: String?, alternatives: List<String> = emptyList()): JsonObject = buildJsonObject {
        put("scenario_id", st.scenario.id)
        put("turn", if (heard == null) st.turn + 1 else st.turn) // Jan's line number, 1-based
        if (heard == null) {
            put("hint", true)
        } else {
            put("heard", heard)
            val alts = alternatives.map { it.trim() }.filter { it.isNotEmpty() && it != heard }.distinct().take(4)
            if (alts.isNotEmpty()) put("alternatives", buildJsonArray { alts.forEach { add(it) } })
        }
    }

    /** `data` of roleplay_end: the whole scene, for the debrief and the session log. [now] is on the clock [TalkState.startedAt] is. */
    fun transcript(st: TalkState, now: Long): JsonObject = buildJsonObject {
        put("scenario_id", st.scenario.id)
        put("title", st.scenario.title)
        put("turns", st.turn)
        put("duration_minutes", ((now - st.startedAt) / 60_000).toInt().coerceIn(1, 240))
        put("goals_done", buildJsonArray { st.goalsDone.sorted().forEach { add(it) } })
        put("goals_total", st.scenario.goals.size)
        put("hints_used", st.hintsUsed)
        put("scene_finished", st.sceneOver)
        put("transcript", buildJsonArray {
            for (l in st.lines) add(buildJsonObject {
                put("who", if (l.fromMe) "jan" else "character")
                put("sl", l.sl)
                if (l.typed) put("typed", true)
                l.correction?.let { put("correction", it) }
            })
        })
    }
}
