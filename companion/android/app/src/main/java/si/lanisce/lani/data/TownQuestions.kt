package si.lanisce.lani.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import si.lanisce.lani.l10n.bi
import java.util.UUID

// The host takes part (plan 2, §3.2; companion/README.md, "Questions between towns"). Like a family partner challenge,
// between two towns: on a visit the guest asks the host a question in the host town's language (POST
// /towns/:id/questions), the host answers in it (POST /towns/questions/answer), and each one's own tutor grades their
// text and records it as practice in that language; either may have the tutor check it first (POST
// /towns/questions/check: the reply comes on the check's conversation, "tq_…"). GET /towns/questions: both sides, with
// the tutor's feedback and the answers. The texts from the other town are plain text, shown as such.

/** What the learner's own tutor said of their question or answer: its reply, score and a corrected text. */
@Serializable
data class QuestionFeedback(val text: String = "", val score: Double? = null, val corrected: String? = null, val at: String = "")

/** The learner's answer to a guest's question: [sent] once it reached the guest's town; the tutor's [feedback]. */
@Serializable
data class HostAnswer(val text: String = "", val at: String = "", val sent: Boolean = false, val conversation: String? = null, val feedback: QuestionFeedback? = null)

/** A question a guest from linked town [from] asked the learner, in the learner's town's [language]; their [answer]. */
@Serializable
data class ReceivedQuestion(
    val key: String,
    val id: String = "",
    val from: GuestFrom = GuestFrom(),
    val at: String = "",
    val language: String = "",
    val text: String = "",
    val answer: HostAnswer? = null,
) {
    /** Who asked: their learner's name, else their village's. */
    val who: String get() = from.learner.ifBlank { from.name }
}

/** The host's answer to the learner's question, as it came. */
@Serializable
data class GuestAnswer(val text: String = "", val at: String = "")

/** A question the learner asked linked town [town], in its [language]; the tutor's [feedback], the host's [answer]. */
@Serializable
data class AskedQuestion(
    val key: String,
    val id: String = "",
    val town: GuestFrom = GuestFrom(),
    val at: String = "",
    val language: String = "",
    val text: String = "",
    val conversation: String? = null,
    val feedback: QuestionFeedback? = null,
    val answer: GuestAnswer? = null,
) {
    /** Who was asked: the town's learner, else the town. */
    val who: String get() = town.learner.ifBlank { town.name }
}

/**
 * Both sides, newest first: the questions the learner [asked] other towns, the ones guests asked them ([received]), and
 * how many were answered with each linked town, both ways ([answered]: the friendship between towns grows by them).
 */
data class TownQuestionsList(
    val asked: List<AskedQuestion> = emptyList(),
    val received: List<ReceivedQuestion> = emptyList(),
    val answered: Map<String, Int> = emptyMap(),
) {
    /** Guests' questions the learner hasn't answered yet. */
    val open: List<ReceivedQuestion> get() = received.filter { it.answer == null }

    /** The learner's questions to town [town]. */
    fun to(town: String): List<AskedQuestion> = asked.filter { it.town.id == town }
}

/**
 * A draft ([text]) the learner's tutor checks before it is sent, on conversation [conversation]: waiting while [feedback]
 * is null; then the tutor's feedback, [score] and the [corrected] text.
 */
data class DraftCheck(val conversation: String, val text: String, val feedback: String? = null, val score: Int? = null, val corrected: String? = null) {
    val waiting: Boolean get() = feedback == null
}

/** What sending a question or an answer came to. */
sealed interface QuestionSend {
    /** It reached the other town ([key]: the question's). */
    data class Sent(val key: String) : QuestionSend

    /** An answer kept by the learner's bridge that hasn't reached the guest's town yet ([problem]); it goes later. */
    data class Kept(val key: String, val problem: String) : QuestionSend

    /** Not sent ([problem], "target · base"); the draft stays. */
    data class Refused(val problem: String) : QuestionSend
}

object TownQuestions {
    /** A question: one line of plain text, at most this long (the bridges cut it there too). */
    const val MAX_QUESTION = 200
    /** An answer: one line of plain text, at most this long. */
    const val MAX_ANSWER = 300
    const val QUESTION = "question"
    const val ANSWER = "answer"
    /** The tutor's notes whose replies belong to questions: a check's, a question's or an answer's grading. */
    const val CONVERSATION = "tq_"

    /** GET /towns/questions; entries that can't be read are left out. */
    fun parse(body: String): TownQuestionsList {
        val o = json.parseToJsonElement(body) as? JsonObject ?: return TownQuestionsList()
        val asked = (o["asked"] as? JsonArray).orEmpty().mapNotNull { runCatching { json.decodeFromJsonElement(AskedQuestion.serializer(), it) }.getOrNull() }
        val received = (o["received"] as? JsonArray).orEmpty().mapNotNull { runCatching { json.decodeFromJsonElement(ReceivedQuestion.serializer(), it) }.getOrNull() }
        val answered = (o["answered"] as? JsonObject).orEmpty().mapNotNull { (k, v) -> (v as? JsonPrimitive)?.intOrNull?.let { k to it } }.toMap()
        return TownQuestionsList(asked, received, answered)
    }

    /** A text as it may be sent: one line, printable, at most [max] characters. */
    fun clean(s: String, max: Int): String = s.replace(Regex("[\\u0000-\\u001f\\u007f<>]"), " ").replace(Regex("\\s+"), " ").trim().take(max)

    /** A new question's id; a retry of the same question sends it again. */
    fun newId(): String = "ask-${UUID.randomUUID()}"

    /** Slovene accusative of a first name, as "Vprašaj Mio / Jana" needs it: Mia → Mio, Jan → Jana, Marko → Marka. */
    fun accusativeSl(name: String): String {
        val n = name.trim()
        return if (n.endsWith("a", true)) n.dropLast(1) + "o" else Family.genitive(n)
    }

    /** A name as the tables take it: [who] as it is, and the Slovene forms "od {whoGen}", "vprašaj {whoAcc}". */
    fun whoArgs(who: String): Array<Pair<String, Any?>> = arrayOf("who" to who, "whoGen" to Family.genitive(who), "whoAcc" to accusativeSl(who))

    /** "❓ Vprašanje od Jana · A question from Jan", "💬 Odgovor od Mie · An answer from Mia". */
    fun title(ev: BridgeEvent.TownQuestion): String =
        if (ev.kind == ANSWER) "💬 ${bi("questions.answerFrom", *whoArgs(ev.who))}" else "❓ ${bi("questions.from", *whoArgs(ev.who))}"

    /** Why a question or an answer didn't go, "target · base". */
    fun problem(status: Int, code: String?, town: String): String = when (code) {
        "too_many_open" -> "⏳ ${bi("questions.waitForAnswers")}"
        "not_for_a_child" -> "🧒 ${bi("questions.noLinksForChild")}"
        "answered" -> "✅ ${bi("questions.alreadyAnswered")}"
        "unknown_question" -> "❓ ${bi("questions.unknown")}"
        "rate_limited" -> "⏳ ${bi("visit.enoughToday")}"
        else -> Visits.problem(status, code, town)
    }

    private fun field(body: String, key: String): String? =
        runCatching { ((json.parseToJsonElement(body) as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull }.getOrNull()

    /** POST /towns/:id/questions as answered ([status], [body]) by the learner's bridge, to town [town] (its name). */
    fun askOutcome(status: Int, body: String, town: String): QuestionSend {
        if (status == 200) return field(body, "key")?.let { QuestionSend.Sent(it) } ?: QuestionSend.Refused("⚠️ ${bi("towns.answerNotUnderstood")}")
        return QuestionSend.Refused(problem(status, Towns.errorCode(body), town))
    }

    /**
     * POST /towns/questions/answer as answered: sent; kept (the learner's bridge has it, the guest's town didn't get it
     * yet: its `code` says why); or refused.
     */
    fun answerOutcome(status: Int, body: String, town: String): QuestionSend {
        if (status != 200) return QuestionSend.Refused(problem(status, Towns.errorCode(body), town))
        val o = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return QuestionSend.Refused("⚠️ ${bi("towns.answerNotUnderstood")}")
        val key = (o["key"] as? JsonPrimitive)?.contentOrNull ?: return QuestionSend.Refused("⚠️ ${bi("towns.answerNotUnderstood")}")
        val sent = (o["sent"] as? JsonPrimitive)?.contentOrNull == "true"
        return if (sent) QuestionSend.Sent(key) else QuestionSend.Kept(key, problem(502, (o["code"] as? JsonPrimitive)?.contentOrNull ?: "unreachable", town))
    }

    /** POST /towns/questions/check's conversation id; null when the answer isn't one. */
    fun checkConversation(body: String): String? = field(body, "conversation_id")?.takeIf { it.startsWith(CONVERSATION) }

    /** A check with the tutor's reply [ev]: its text, score and corrected text (one line, plain). */
    fun checked(c: DraftCheck, ev: BridgeEvent.Reply): DraftCheck {
        val d = ev.data
        val score = (d?.get("score") as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }?.coerceIn(0, 10)
        val corrected = (d?.get("corrected") as? JsonPrimitive)?.contentOrNull?.let { clean(it, MAX_ANSWER) }?.takeIf { it.isNotBlank() && it != c.text }
        return c.copy(feedback = ev.text.ifBlank { "✔" }, score = score, corrected = corrected)
    }
}
