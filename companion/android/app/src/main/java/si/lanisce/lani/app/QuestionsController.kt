package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.data.DraftCheck
import si.lanisce.lani.data.QuestionSend
import si.lanisce.lani.data.ReceivedQuestion
import si.lanisce.lani.data.TownQuestions
import si.lanisce.lani.data.TownQuestionsList
import si.lanisce.lani.data.Towns
import si.lanisce.lani.l10n.bi

/**
 * Questions between towns (data/TownQuestions.kt; plan 2, §3.2 "The host takes part"): the learner's questions to linked
 * towns and guests' questions to them, as their bridge keeps them ([list]); a draft checked by their tutor first
 * ([check]); a question sent on a visit ([ask], once by its id: a retry of the same sends the same), a guest's question
 * answered ([answer]). The tutor's replies to their notes ("tq_…") come as [onReply].
 */
class QuestionsController(
    private val scope: CoroutineScope,
    /** GET /towns/questions: the status and body; null when not connected. */
    private val fetch: suspend () -> Pair<Int, String>?,
    /** A POST to the learner's bridge ([path], [body]): the status and body; null when not connected. */
    private val post: suspend (path: String, body: JsonObject) -> Pair<Int, String>?,
    private val notices: Notices,
    /** Every list that came: the village counts the questions answered with each town (game/TownQuestionTies). */
    private val onList: (TownQuestionsList) -> Unit = {},
    /** The learner answered a guest's question: it pays like a family challenge's answer. */
    private val onAnswered: () -> Unit = {},
) {
    /** Both sides as the bridge keeps them; null until they came (or from a bridge older than questions). */
    var list by mutableStateOf<TownQuestionsList?>(null)
        private set
    /** The draft the learner's tutor checks now (one at a time): waiting for its reply, or with it. */
    var check by mutableStateOf<DraftCheck?>(null)
        private set
    /** A question or an answer is on its way (the buttons wait). */
    var sending by mutableStateOf(false)
        private set

    /** The id of each question not sent yet, by its town and text: a retry of the same question sends the same id. */
    private val ids = HashMap<String, String>()

    private val unreachable: String get() = "📡 ${bi("towns.cantReachTutor")}"

    fun load(): Job = scope.launch {
        val (status, body) = try {
            fetch() ?: return@launch
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@launch // offline: the list shows what it had
        }
        if (status != 200) return@launch // an older bridge: no questions between towns
        val l = runCatching { TownQuestions.parse(body) }.getOrNull() ?: return@launch
        list = l
        onList(l)
    }

    /** Asks the learner's tutor to check [text]: a question to town [town], or the answer to guest's question [key]. */
    fun checkDraft(text: String, town: String? = null, key: String? = null): Job = scope.launch {
        val t = TownQuestions.clean(text, if (key != null) TownQuestions.MAX_ANSWER else TownQuestions.MAX_QUESTION)
        if (t.isBlank()) return@launch
        val body = buildJsonObject {
            put("text", t)
            town?.let { put("town", it) }
            key?.let { put("key", it) }
        }
        val (status, raw) = call("/towns/questions/check", body) ?: return@launch run { notices.banner = unreachable }
        val conversation = TownQuestions.checkConversation(raw)
        if (status == 200 && conversation != null) check = DraftCheck(conversation, t)
        else notices.banner = TownQuestions.problem(status, Towns.errorCode(raw), "")
    }

    fun clearCheck() {
        check = null
    }

    /**
     * Sends question [text] to linked town [town] ([name]): [done] gets [QuestionSend.Sent], or why not. It counts as
     * checked when the tutor's check was of this very text.
     */
    fun ask(town: String, name: String, text: String, done: (QuestionSend) -> Unit = {}): Job = scope.launch {
        val t = TownQuestions.clean(text, TownQuestions.MAX_QUESTION)
        if (t.isBlank()) return@launch
        val slot = "$town:$t"
        val id = ids.getOrPut(slot) { TownQuestions.newId() }
        val checked = check?.let { it.text == t && !it.waiting } == true
        val body = buildJsonObject {
            put("id", id)
            put("text", t)
            put("checked", checked)
        }
        val out = send("/towns/$town/questions", body) { status, raw ->
            // answered either way: a retry of something refused is a new try
            if (status in 200..499) ids.remove(slot)
            TownQuestions.askOutcome(status, raw, name)
        }
        if (out is QuestionSend.Sent) {
            check = null
            load()
        }
        done(out)
    }

    /**
     * Answers guest's question [q] with [text] (in the learner's town's language): the bridge keeps it, the tutor grades
     * it, and it goes back to the guest's town; kept for later when that town isn't answering ([QuestionSend.Kept]).
     */
    fun answer(q: ReceivedQuestion, text: String, done: (QuestionSend) -> Unit = {}): Job = scope.launch {
        val t = TownQuestions.clean(text, TownQuestions.MAX_ANSWER)
        if (t.isBlank()) return@launch
        val first = q.answer == null
        val body = buildJsonObject {
            put("key", q.key)
            put("text", t)
        }
        val out = send("/towns/questions/answer", body) { status, raw -> TownQuestions.answerOutcome(status, raw, q.from.name) }
        if (out !is QuestionSend.Refused) {
            check = null
            if (first) onAnswered()
            load()
        }
        done(out)
    }

    private suspend fun call(path: String, body: JsonObject): Pair<Int, String>? = try {
        post(path, body)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun send(path: String, body: JsonObject, outcome: (Int, String) -> QuestionSend): QuestionSend {
        sending = true
        try {
            val (status, raw) = call(path, body) ?: return QuestionSend.Refused(unreachable)
            return outcome(status, raw)
        } finally {
            sending = false
        }
    }

    /**
     * The tutor's reply on a conversation of questions ("tq_…"): the check's feedback, or the grading of a question or an
     * answer sent (the bridge keeps it with them: the list comes again). False for any other conversation.
     */
    fun onReply(ev: BridgeEvent.Reply): Boolean {
        if (!ev.conversationId.startsWith(TownQuestions.CONVERSATION)) return false
        val c = check
        if (c != null && c.conversation == ev.conversationId) check = TownQuestions.checked(c, ev) else load()
        return true
    }

    /** A guest asked the learner a question, or the town they asked answered: the list comes again. */
    fun onEvent(@Suppress("UNUSED_PARAMETER") ev: BridgeEvent.TownQuestion) {
        load()
    }
}
