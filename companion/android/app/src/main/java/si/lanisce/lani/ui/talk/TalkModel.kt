package si.lanisce.lani.ui.talk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import si.lanisce.lani.app.ChatMessage
import si.lanisce.lani.app.launchSafely
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.RoleplayReply
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.data.Talk
import si.lanisce.lani.data.TalkPhase
import si.lanisce.lani.data.TalkState
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.ui.game.Reward
import si.lanisce.lani.ui.villagers.FriendGain
import si.lanisce.lani.ui.villagers.VillagerLogic
import java.time.LocalDate

/**
 * "Pogovor · Talk": the scenario list, the chosen scenario's intro, and one running conversation.
 * Owned by the view model, so a conversation survives leaving the screen; replies come in via [onReply].
 */
class TalkModel(
    private val scope: CoroutineScope,
    private val send: suspend (kind: String, conversationId: String, text: String, data: JsonObject) -> Unit,
    private val fetch: suspend () -> List<Scenario>,
    private val reward: (List<Verdict>) -> Reward?,
    /**
     * A talk with a villager (scenario "villager:<id>") ended, or its debrief brought a memory: the
     * friendship grows by `points` and they keep `memory` (see VillagerController.talked).
     */
    private val befriend: (villagerId: String, points: Int, memory: Memory?) -> FriendGain? = { _, _, _ -> null },
    /** A conversation was ended by the learner (before its debrief): a talk on a visit records who was met. */
    private val finished: (TalkState) -> Unit = {},
    /**
     * A conversation's debrief came ([data]: the tutor's, e.g. a market haggle's deal and score), also when another
     * conversation is on screen by now: scenario id, conversation id, data.
     */
    private val debriefed: (scenarioId: String, conversationId: String, data: JsonObject?) -> Unit = { _, _, _ -> },
    /** A reply in the chat under a debrief that is no longer on screen: it goes to the tutor chat instead. */
    private val stray: (BridgeEvent.Reply) -> Unit = {},
) {
    var scenarios by mutableStateOf<List<Scenario>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    /** The scenario whose intro is shown. */
    var picked by mutableStateOf<Scenario?>(null)
        private set
    var state by mutableStateOf<TalkState?>(null)
        private set
    /** A message that didn't reach the bridge; [retry] sends it again. */
    var sendError by mutableStateOf<String?>(null)
        private set
    var earned by mutableStateOf<Reward?>(null)
        private set
    /** How the friendship grew in a talk with a villager; its [FriendGain.memory] once the debrief brings one. */
    var friend by mutableStateOf<FriendGain?>(null)
        private set

    /** Ended talks with villagers whose debrief may still bring a memory: conversation id → villager id. */
    private val awaitingMemory = HashMap<String, String>()

    /** Ended conversations whose debrief hasn't come yet: conversation id → scenario id. */
    private val awaitingDebrief = HashMap<String, String>()

    private var lastSent: Triple<String, String, JsonObject>? = null

    /**
     * The chat under the debrief ("Vprašaj učitelja · Ask your tutor"): questions about the role-play on screen, on
     * their own conversation ([Talk.debriefThread]), and the tutor's answers.
     */
    val thread = mutableStateListOf<ChatMessage>()
    /** The tutor is answering in [thread]. */
    var threadWaiting by mutableStateOf(false)
        private set
    /** A question that didn't reach the bridge; [retryThread] sends it again. */
    var threadError by mutableStateOf<String?>(null)
        private set
    /** The role-play conversation [thread] is about. */
    private var threadOf: String? = null
    private var threadLast: Pair<String, JsonObject>? = null

    fun load() {
        loading = true
        scope.launchSafely({ loadError = it.message ?: "connection failed" }) {
            try {
                scenarios = fetch()
                loadError = null
            } finally {
                loading = false
            }
        }
    }

    fun pick(s: Scenario?) { picked = s }

    fun start(now: Long = System.currentTimeMillis()) {
        val s = picked ?: return
        state = Talk.start(s, now, onScreen = ScreenClock.app.now())
        earned = null
        friend = null
        sendError = null
        closeThread()
    }

    /** Leaves the conversation without a debrief (nothing said yet, or Jan gave up). */
    fun abandon() {
        state = null
        sendError = null
        closeThread()
    }

    /** A question to the tutor under the debrief: a chat about this role-play, on the debrief's own conversation. */
    fun ask(text: String) {
        val st = state ?: return
        val t = text.trim()
        if (t.isEmpty() || st.phase == TalkPhase.CHAT) return
        if (threadOf != st.conversationId) {
            closeThread()
            threadOf = st.conversationId
        }
        thread += ChatMessage(fromMe = true, text = t)
        sendThread(st.conversationId, t, Talk.debriefData(st))
    }

    fun retryThread() {
        val (text, data) = threadLast ?: return
        sendThread(threadOf ?: return, text, data)
    }

    private fun sendThread(conv: String, text: String, data: JsonObject) {
        threadLast = text to data
        threadWaiting = true
        threadError = null
        scope.launchSafely({
            threadError = it.message ?: "not sent"
            threadWaiting = false
        }) { send("chat", Talk.debriefThread(conv), text, data) }
    }

    /** The thread is left behind (another conversation, or none): answers still on their way go to the tutor chat. */
    private fun closeThread() {
        thread.clear()
        threadOf = null
        threadLast = null
        threadWaiting = false
        threadError = null
    }

    /** An answer in a debrief's chat: into [thread] while that debrief is on screen, else to the tutor chat. */
    private fun threadReply(ev: BridgeEvent.Reply) {
        val on = threadOf
        if (on != null && state?.conversationId == on && ev.conversationId == Talk.debriefThread(on)) {
            thread += ChatMessage(fromMe = false, text = ev.text)
            threadWaiting = false
        } else stray(ev)
    }

    fun say(text: String, alternatives: List<String>, typed: Boolean) {
        val st = state ?: return
        if (text.isBlank() || st.waiting || st.phase != TalkPhase.CHAT) return
        val next = Talk.said(st, text, typed, System.currentTimeMillis())
        state = next
        post("roleplay", text.trim(), Talk.turnData(next, text.trim(), alternatives))
    }

    fun hint() {
        val st = state ?: return
        if (st.hintLoading || st.waiting || st.phase != TalkPhase.CHAT) return
        state = Talk.askedHint(st)
        post("roleplay", "💡 hint", Talk.turnData(st, heard = null))
    }

    /** Ends the scene: credits the village right away, then asks the tutor for the debrief. */
    fun finish() {
        val st = state ?: return
        if (st.phase != TalkPhase.CHAT) return
        if (st.turn == 0) return abandon()
        state = Talk.ending(st)
        earned = reward(Talk.verdicts(st))
        VillagerLogic.villagerOfScenario(st.scenario.id)?.let { id ->
            awaitingMemory[st.conversationId] = id
            friend = befriend(id, Bonds.TALK, null)
        }
        awaitingDebrief[st.conversationId] = st.scenario.id
        finished(st)
        post("roleplay_end", "Role-play finished: ${st.scenario.title}", Talk.transcript(st, ScreenClock.app.now()))
    }

    /** Same scenario again, from the opener. */
    fun again() {
        state?.scenario?.let { picked = it }
        state = null
        start()
    }

    fun retry() {
        val (kind, text, data) = lastSent ?: return
        post(kind, text, data)
    }

    fun onReply(ev: BridgeEvent.Reply) {
        if (Talk.isDebriefThread(ev.conversationId)) return threadReply(ev)
        remember(ev)
        debrief(ev)
        val st = state ?: return
        if (ev.conversationId != st.conversationId) return // an old conversation's late reply
        state = Talk.replied(st, RoleplayReply.parse(ev.text, ev.data))
    }

    /**
     * The debrief of a talk with a villager may carry `data.memory` ({sl, en}): they keep it, even when
     * another conversation is on screen by now.
     */
    private fun remember(ev: BridgeEvent.Reply) {
        val id = awaitingMemory[ev.conversationId] ?: return
        val m = VillagerLogic.memoryOf(ev.data, LocalDate.now()) ?: return
        awaitingMemory.remove(ev.conversationId)
        val gain = befriend(id, 0, m)
        if (state?.conversationId == ev.conversationId) friend = friend?.copy(memory = m) ?: gain
    }

    /** The debrief of an ended conversation (not a line in character): [debriefed] hears it once. */
    private fun debrief(ev: BridgeEvent.Reply) {
        val scenario = awaitingDebrief[ev.conversationId] ?: return
        if (RoleplayReply.parse(ev.text, ev.data).inCharacter) return // a late line of the scene
        awaitingDebrief.remove(ev.conversationId)
        debriefed(scenario, ev.conversationId, ev.data)
    }

    private fun post(kind: String, text: String, data: JsonObject) {
        val conv = state?.conversationId ?: return
        lastSent = Triple(kind, text, data)
        sendError = null
        scope.launchSafely({ sendError = it.message ?: "not sent" }) { send(kind, conv, text, data) }
    }
}
