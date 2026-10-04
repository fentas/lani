package si.lanisce.lani.ui.talk

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import si.lanisce.lani.data.SilenceGate
import si.lanisce.lani.data.SttResult
import si.lanisce.lani.data.SttUnavailable
import si.lanisce.lani.data.SttWord
import si.lanisce.lani.l10n.bi
import java.io.File

/** The recognizers a [TalkMic] drives: the node's (a recording Whisper transcribes) and the phone's. Fakes in tests. */
interface TalkEars {
    /** Starts recording for the node, [limitMs] at most; throws when the microphone can't open. */
    fun record(limitMs: Int)

    /** Stops recording: the take, or null when nothing usable was recorded. */
    fun stopRecording(): File?

    /** What the node's Whisper heard in [take] (deleted afterwards); [expected] steers its spelling. */
    suspend fun transcribe(take: File, expected: String?): SttResult

    /** The phone's recognizer starts listening; [again]: after a sentence, to go on with the next. */
    fun listen(again: Boolean)

    /** It stops and recognizes what it heard so far. */
    fun stopListening()

    /** It stops and drops what it heard. */
    fun cancelListening()
}

/**
 * The 🎤 of a talk, as a state machine; the screen drives it ([TalkVoice]): the recognizers' callbacks, and [tick]
 * every 100 ms with the recorder's level while it records.
 *
 * A take runs from a tap until the next tap, or until a long pause ([Config.longPauseMs]); short pauses between
 * sentences don't end it. The node's Whisper ([Engine.NODE]) gets one recording per sentence group: "Next sentence"
 * ([next]) sends what was said so far at once, with a full stop, and records on, and a long stretch is cut at a pause
 * (the node takes 30 s at most); the transcripts land in [draft] in the order they were said, whatever order they come
 * back in, with the words Whisper was unsure of marked (at most [Config.maxUnclear] a take). The phone's recognizer
 * ([Engine.PHONE]) ends each sentence by itself: it listens again after each one until the take ends, appending them.
 * Nothing is sent: the text waits in [draft] until Jan sends it ([canSend]).
 */
@Stable
class TalkMic(
    private val scope: CoroutineScope,
    private val ears: TalkEars,
    val draft: TalkDraft,
    val config: Config = Config(),
) {
    data class Config(
        /** This long quiet after speech ends the take. */
        val longPauseMs: Long = 3_000,
        /** Nothing said this long after the start (or after "Next sentence"): the take ends. */
        val giveUpMs: Long = 10_000,
        /** The longest stretch without "Next sentence". */
        val maxMs: Long = 45_000,
        /** A node recording this long is cut at the next short pause ([SPLIT_QUIET_MS]) and goes on in a new one... */
        val splitAfterMs: Long = 18_000,
        /** ...or here at the latest (the node's Whisper takes 30 s at most). */
        val segmentMaxMs: Long = 28_000,
        /** Words marked as unclear, at most, per take. */
        val maxUnclear: Int = 3,
        /** Saying one word again: the pause that ends it, and its longest take. */
        val redoPauseMs: Long = 900,
        val redoMaxMs: Long = 6_000,
    ) {
        fun gate() = SilenceGate(speech = SPEECH, quietMs = longPauseMs, giveUpMs = giveUpMs)
        fun redoGate() = SilenceGate(speech = SPEECH, quietMs = redoPauseMs, giveUpMs = minOf(giveUpMs, redoMaxMs))
    }

    enum class Engine { NODE, PHONE }

    /** The recognizer of the take on now; null while the microphone is off. */
    var engine by mutableStateOf<Engine?>(null)
        private set
    val recording: Boolean get() = engine != null
    /** Recordings on their way to the node. */
    var uploads by mutableIntStateOf(0)
        private set
    val transcribing: Boolean get() = uploads > 0
    /** Recording, or text still to come: the draft isn't finished. */
    val busy: Boolean get() = recording || transcribing
    /** 0..1, for the animation. */
    var level by mutableFloatStateOf(0f)
        private set
    /** What the phone's recognizer hears of the sentence being said. */
    var partial by mutableStateOf("")
        private set
    /** Something for Jan to know ("I didn't catch that", the node is down). */
    var message by mutableStateOf<String?>(null)
    /** The marked word being said again. */
    var redo by mutableStateOf<Unclear?>(null)
        private set
    /** The microphone is asked for: the explanation shows before the system prompt. */
    var asking by mutableStateOf(false)
    /** The microphone was refused. */
    var denied by mutableStateOf(false)

    /** Send is allowed: something to send, and nothing still being recognized ("nothing goes out half-finished"). */
    fun canSend(enabled: Boolean): Boolean = enabled && !busy && draft.text.isNotBlank()

    private class Piece(val text: String, val words: List<SttWord> = emptyList(), val alternatives: List<String> = emptyList(), val redo: Unclear? = null)

    private val order = InOrder<Piece>()

    // the take
    private var runStart = 0L
    private var heard = false
    private var appended = 0
    private var marked = 0

    // the node's recording
    private var gate = config.gate()
    private var segment = -1
    private var segmentStart = 0L
    /** Ticks of speech in the recording, and its loudest one. */
    private var loud = 0
    private var peak = 0

    // the phone's recognizer
    private var continuous = true
    private var lastSpeech = -1L
    private var stopBy = -1L
    private var boundary = false

    /** 🎤: a take begins on [engine]. */
    fun start(engine: Engine, now: Long) {
        if (recording) return
        marked = 0
        begin(engine, now, null)
    }

    /** The marked word [m], said again: a short take whose text replaces it. */
    fun sayAgain(m: Unclear, engine: Engine, now: Long) {
        if (recording) return
        begin(engine, now, m)
    }

    private fun begin(engine: Engine, now: Long, mark: Unclear?) {
        message = null
        heard = false
        appended = 0
        runStart = now
        redo = mark
        when (engine) {
            Engine.NODE -> {
                gate = if (mark != null) config.redoGate() else config.gate()
                if (!record(now)) return
                this.engine = engine
            }
            Engine.PHONE -> {
                continuous = mark == null
                lastSpeech = -1
                stopBy = -1
                boundary = false
                partial = ""
                this.engine = engine
                ears.listen(again = false)
            }
        }
    }

    /** ⏹ (a tap, or the hold let go): the take ends; what was said so far is recognized. */
    fun stop(now: Long) {
        when (engine) {
            Engine.NODE -> end(now, tapped = true)
            Engine.PHONE -> askStop(now)
            null -> Unit
        }
    }

    /** "Next sentence": what was said so far ends with a full stop and goes to be recognized; recording goes on. */
    fun next(now: Long) {
        if (redo != null) return
        when (engine) {
            Engine.NODE -> cut(now, fullStop = true)
            Engine.PHONE -> {
                runStart = now
                lastSpeech = -1
                if (partial.isNotBlank()) {
                    boundary = true
                    ears.stopListening() // its result comes, gets the full stop, and it listens again
                } else draft.punctuate()
            }
            null -> Unit
        }
    }

    /** Every 100 ms while recording; [amplitude]: the recorder's loudest sample since the last tick (0..32767). */
    fun tick(now: Long, amplitude: Int) {
        when (engine) {
            Engine.NODE -> {
                level = (amplitude / 12_000f).coerceIn(0f, 1f)
                if (amplitude >= SPEECH) loud++
                peak = maxOf(peak, amplitude)
                val run = now - runStart
                val seg = now - segmentStart
                when {
                    gate.feed(amplitude, run) -> end(now, tapped = false)
                    redo != null -> if (seg >= config.redoMaxMs) end(now, tapped = false)
                    run >= config.maxMs -> end(now, tapped = false)
                    seg >= config.segmentMaxMs || (seg >= config.splitAfterMs && gate.quietFor(run) >= SPLIT_QUIET_MS) -> cut(now, fullStop = false)
                }
            }
            Engine.PHONE -> when {
                stopBy >= 0 -> if (now >= stopBy) {
                    keepPartial()
                    endPhone()
                }
                due(now) -> askStop(now)
            }
            null -> Unit
        }
    }

    /** Leaving the screen or the app: the take on now is dropped (sentences already sent still arrive). */
    fun cancel() {
        when (engine) {
            Engine.NODE -> {
                ears.stopRecording()?.delete()
                deliver(order.fill(segment, null))
            }
            Engine.PHONE -> ears.cancelListening()
            null -> return
        }
        engine = null
        level = 0f
        partial = ""
        stopBy = -1
        if (uploads == 0) redo = null
    }

    // --- the phone's recognizer --------------------------------------------------------------

    fun phoneLevel(rmsdB: Float) {
        if (engine == Engine.PHONE) level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
    }

    fun phonePartial(text: String, now: Long) {
        if (engine != Engine.PHONE) return
        if (text.isNotBlank() && text != partial) lastSpeech = now
        partial = text
    }

    /** A sentence's final result: it goes in, and the recognizer listens again unless the take is over. */
    fun phoneResults(alternatives: List<String>, now: Long) {
        if (engine != Engine.PHONE) return
        val alts = alternatives.map { it.trim() }.filter { it.isNotEmpty() }
        partial = ""
        alts.firstOrNull()?.let {
            lastSpeech = now
            recognized(it, alts.drop(1))
        }
        afterSentence(now)
    }

    /** Nothing recognized (no match, a timeout, stopped before a result): what was heard so far counts. */
    fun phoneNothing(now: Long) {
        if (engine != Engine.PHONE) return
        keepPartial()
        afterSentence(now)
    }

    /** The recognizer can't go on ([why] for Jan, or null to say nothing): the take ends with what it heard. */
    fun phoneFailed(why: String?) {
        if (engine != Engine.PHONE) return
        keepPartial()
        message = why
        endPhone()
    }

    /** One result of the system's voice dialog (it listens for one sentence): into the draft, or for [mark]. */
    fun dialogHeard(alternatives: List<String>, mark: Unclear? = null) {
        val alts = alternatives.map { it.trim() }.filter { it.isNotEmpty() }
        val best = alts.firstOrNull() ?: return run { message = NOT_HEARD }
        message = null
        deliver(order.fill(order.open(), Piece(best, alternatives = alts.drop(1), redo = mark)))
    }

    private fun keepPartial() {
        val p = partial.trim()
        partial = ""
        if (p.isNotEmpty()) recognized(p, emptyList())
    }

    private fun recognized(text: String, alternatives: List<String>) {
        heard = true
        val t = if (boundary) sentence(text, fullStop = true) else text
        val alts = if (boundary) emptyList() else alternatives
        boundary = false
        deliver(order.fill(order.open(), Piece(t, alternatives = alts, redo = redo)))
    }

    private fun afterSentence(now: Long) {
        if (boundary) {
            draft.punctuate()
            boundary = false
        }
        if (stopBy >= 0 || !continuous || due(now)) endPhone() else ears.listen(again = true)
    }

    private fun due(now: Long): Boolean {
        val run = now - runStart
        val quiet = if (lastSpeech >= 0) now - lastSpeech >= config.longPauseMs else run >= config.giveUpMs
        return quiet || run >= (if (redo != null) config.redoMaxMs else config.maxMs)
    }

    private fun askStop(now: Long) {
        if (stopBy >= 0) return
        stopBy = now + STOP_WAIT_MS // the recognizer's last result, or what it heard by then
        ears.stopListening()
    }

    private fun endPhone() {
        engine = null
        level = 0f
        partial = ""
        stopBy = -1
        ears.cancelListening()
        if (!heard && message == null) message = NOT_HEARD
        if (uploads == 0) redo = null
    }

    // --- the node's Whisper ------------------------------------------------------------------

    private fun record(now: Long): Boolean {
        try {
            ears.record((if (redo != null) config.redoMaxMs else config.segmentMaxMs).toInt() + 2_000)
        } catch (e: Exception) {
            message = "🎤 ${bi("nodeMic.microphoneCantOpenedRight")}"
            engine = null
            level = 0f
            if (uploads == 0) redo = null
            return false
        }
        segment = order.open()
        segmentStart = now
        loud = 0
        peak = 0
        return true
    }

    /** The recording so far goes to the node; a new one starts ([fullStop]: Jan said the sentence is done). */
    private fun cut(now: Long, fullStop: Boolean) {
        send(ears.stopRecording(), tapped = fullStop, now = now, fullStop = fullStop)
        if (fullStop) {
            gate = config.gate() // a new stretch: a pause to think about the next sentence first is fine
            runStart = now
        }
        if (!record(now)) settle()
    }

    private fun end(now: Long, tapped: Boolean) {
        send(ears.stopRecording(), tapped, now, fullStop = false)
        engine = null
        level = 0f
        settle()
    }

    private fun send(take: File?, tapped: Boolean, now: Long, fullStop: Boolean) {
        val i = segment
        // A tap means "I said it" even when the level stayed low (a soft voice); silence isn't sent (Whisper makes up
        // words for it: "Hvala.").
        val said = loud >= MIN_LOUD || (tapped && now - segmentStart >= 600 && peak >= SPEECH / 3)
        if (take == null || !said) {
            take?.delete()
            deliver(order.fill(i, null))
            return
        }
        heard = true
        uploads++
        val mark = redo
        scope.launch {
            var piece: Piece? = null
            try {
                val r = ears.transcribe(take, mark?.word)
                piece = Piece(sentence(r.text, fullStop), r.words, redo = mark).takeIf { it.text.isNotEmpty() }
            } catch (e: CancellationException) {
                uploads--
                throw e
            } catch (e: SttUnavailable) {
                message = "📶 ${bi("nodeMic.nodesSpeechRecognitionIsnt")}"
            } catch (e: Exception) {
                message = "📶 ${bi("nodeMic.cantReachNodeTry")}"
            }
            uploads--
            deliver(order.fill(i, piece))
            settle()
        }
    }

    // --- into the draft -------------------------------------------------------------------------

    private fun deliver(ready: List<Piece>) {
        for (p in ready) {
            val m = p.redo
            if (m != null) {
                draft.replace(m.id, p.text)
                if (redo?.id == m.id) redo = null
            } else {
                val unclear = TalkDraft.unclear(p.text, p.words, config.maxUnclear - marked)
                marked += unclear.size
                draft.append(p.text, unclear, p.alternatives)
            }
            appended++
        }
    }

    /** The take is over and everything came: say so when nothing did. */
    private fun settle() {
        if (recording || transcribing) return
        if (appended == 0 && message == null) message = NOT_HEARD
        redo = null
    }

    companion object {
        /** A sample this loud (0..32767) is speech. */
        const val SPEECH = 1_500
        /** Loud ticks (100 ms each) a recording needs to be worth sending. */
        private const val MIN_LOUD = 2
        /** A pause this long is where a long recording is cut. */
        const val SPLIT_QUIET_MS = 300L
        /** After ⏹ the phone's recognizer gets this long for its last result. */
        const val STOP_WAIT_MS = 1_500L
        private const val ENDS = ".!?…"

        val NOT_HEARD: String get() = "🤔 ${bi("talkInput.iDidntCatchTry")}"

        /** A transcript as a sentence of the draft; [fullStop]: it ends with one. */
        fun sentence(text: String, fullStop: Boolean): String {
            val t = text.trim()
            return if (fullStop && t.isNotEmpty() && t.last() !in ENDS) "$t." else t
        }
    }
}

/** Results that may arrive out of order (transcripts), handed on in the order they were asked for. */
class InOrder<T> {
    private class Slot<T>(val value: T?)

    private val arrived = HashMap<Int, Slot<T>>()
    private var next = 0
    private var opened = 0

    /** Asked for, not handed on yet. */
    val pending: Int get() = opened - next

    /** A new slot, after every slot opened so far. */
    fun open(): Int = opened++

    /** [value] for slot [i] (null: nothing came); the values now ready, in order. */
    fun fill(i: Int, value: T?): List<T> {
        if (i < next || i >= opened) return emptyList()
        arrived[i] = Slot(value)
        val out = ArrayList<T>()
        while (true) {
            val s = arrived.remove(next) ?: break
            next++
            s.value?.let(out::add)
        }
        return out
    }
}
