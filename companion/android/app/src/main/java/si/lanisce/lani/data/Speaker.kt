package si.lanisce.lani.data

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.audio.OfflineVoice
import si.lanisce.lani.audio.Piper
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import java.io.File
import java.util.Locale

/**
 * Speaks the learner's target language (Slovene for Jan): a family recording, else a node clip ([Clips]), else the phone's
 * offline voice ([offline]: Piper, when its voice of the target language is on the phone), else text-to-speech. [available]
 * is false when the phone has no TTS voice for it (sl-SI, it-IT …).
 */
class Speaker(private val context: Context) {
    /**
     * The phone's own voice of the target language ([Piper], got through the node): it speaks when no clip can be had,
     * before Android's text-to-speech, and only in its own language ([fallback]).
     */
    var offline: OfflineVoice? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Compose state, so screens update once the engine has initialised. */
    var available by mutableStateOf(false)
        private set

    private var ready = false

    /** Family recordings: when one exists for the text, it plays instead of TTS. Also plays [clips]. */
    var voice: Voice? = null

    /** Natural voice clips from the node, after family recordings and before TTS. */
    var clips: Clips? = null

    /** Something can speak: a TTS voice, the node's voice store, or the offline voice of the target language. */
    val canSay: Boolean get() = available || clips?.enabled == true || offline?.speaks(L10n.pair.target.code) == true

    /** Worth a 🔊: a clip or family recording exists for [text], or it reads as the target language (Slovene for Jan). */
    fun isSlovene(text: String): Boolean =
        clips?.has(text) == true || voice?.has(text) == true || L10n.pair.let { Spoken.looks(text, it.target, it.base) }

    /** Bumped by every say/stop, so a clip that arrives late doesn't talk over what came next. */
    private var turn = 0

    /**
     * The phone's voice is speaking, or about to (set as a line is handed to it, so whoever waits for the line waits from
     * the start; its callbacks come on another thread).
     */
    @Volatile
    private var ttsTalking = false

    /** The phone's voice's utterance asked for last ("say-<turn>"): only its own callbacks say whether it is speaking. */
    @Volatile
    private var utterance: String? = null

    /**
     * The hint's 🔊 ([HintVoice]): another voice of the app's, so one voice at a time: whatever this says or stops stops it
     * too, and it counts as [talking].
     */
    var hint: HintVoice? = null

    /** A unit's introduction read aloud ([IntroVoice]): one voice at a time with it too, as with the hint's. */
    var intro: IntroVoice? = null

    /** A voice is speaking now: a recording, a clip, the phone's voice, the hint's or the intro's. The background sounds duck under it. */
    val talking: Boolean get() = voice?.sounding == true || ttsTalking || hint?.sounding == true || intro?.sounding == true

    /** The line said last is still on its way from the node (voiced on demand), not playing yet. */
    @Volatile
    private var loading = false

    /** A line is loading or being spoken: a timed run's clock waits for it (companion/GAME.md, "Events"). */
    val busy: Boolean get() = loading || talking

    /**
     * Gets the clips of [texts] onto the phone ahead, each in the voice [voiceOf] gives it (as [say] would ask for it), a
     * few at a time: a timed run's sounds, before its clock starts, so none waits for the node while it runs. A line with a
     * family recording, a clip on the phone already, or none the node can make now is left as it is.
     */
    suspend fun preload(texts: List<String>, voiceOf: (String) -> String = { Clips.FEMALE }) {
        val c = clips ?: return
        val v = voice ?: return
        if (!c.online) return
        coroutineScope {
            for (batch in texts.distinct().chunked(PRELOAD_AT_ONCE)) batch.map { t ->
                async {
                    if (v.ready(t)) return@async
                    c.load(t, voicesFor(voiceOf(t), null, null).voices)
                }
            }.awaitAll()
        }
    }

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        refresh()
    }.apply {
        // an utterance flushed by the next one reports its stop after the next one has begun: only the current one counts
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { if (utteranceId == utterance) ttsTalking = true }
            override fun onDone(utteranceId: String?) { if (utteranceId == utterance) ttsTalking = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == utterance) ttsTalking = false }
            override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == utterance) ttsTalking = false }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { if (utteranceId == utterance) ttsTalking = false }
        })
    }

    /**
     * Re-checks the phone's voice for the learner's target language (sl-SI for Jan; it-IT for a learner of Italian, whose
     * culture pack may have no voices on the node yet: then this speaks everything), e.g. after the user installed it in
     * system settings.
     */
    fun refresh() {
        if (!ready) return
        val r = tts.setLanguage(locale(L10n.pair.target))
        available = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /** What speaks a line when no clip of the voice wanted can be had (see [fallback]). */
    enum class Fallback { CLIP, OFFLINE, TTS, NONE }

    companion object {
        /** The text-to-speech locale of [lang] (the recognizer takes the same). */
        fun locale(lang: Lang): Locale = Locale.forLanguageTag(lang.locale)

        /** How many lines [preload] asks the node for at once. */
        private const val PRELOAD_AT_ONCE = 3

        /**
         * What speaks when the wanted voice's clip can't be had (offline, the node can't make it now, a clip that won't
         * play): another voice of theirs whose clip is on the phone ([clip]), else the phone's offline voice ([offline]: it
         * speaks the target language, [target], and nothing else), else Android's text-to-speech when it has a voice
         * ([tts]), else nothing.
         */
        fun fallback(clip: Boolean, offline: String?, target: String, tts: Boolean): Fallback = when {
            clip -> Fallback.CLIP
            offline != null && offline == target -> Fallback.OFFLINE
            tts -> Fallback.TTS
            else -> Fallback.NONE
        }

        /**
         * The voices for a line said by [voiceName] (falling back to [fallback], their gender's narrator) as [profile]'s (a
         * villager's or resident's: their own voice "@micka", else their archetype first): the ones to try, best first;
         * [Voices.shared]: their archetype's and the chain's; [Voices.own]: they have a voice of their own, heard only in it
         * unless the node can't make it now. The day's audio asks for the same ([si.lanisce.lani.audio.DayAudio]).
         */
        fun voices(voiceName: String = Clips.FEMALE, fallback: String? = null, profile: VoiceProfile? = null): Voices {
            val own = profile?.own == true
            // their archetype first (the node's profile knows it even where the caller passes a narrator), then the chain
            val shared = (listOfNotNull(profile?.takeIf { !own }?.speaker) + Clips.chain(voiceName, fallback)).distinct()
            return Voices(if (own) listOf(profile!!.speaker) else shared, shared, own)
        }

        private const val TAG = "Speaker"

        /** How often [sayThrough] looks whether the line is still being said. */
        private const val POLL_MS = 40L

        /**
         * How long [text] takes to read when nothing can say it (no voice at all): lines in a row ([sayInTurn]) keep that
         * much apart, and someone is drawn talking for as long.
         */
        fun readingMs(text: String): Long = 700L + text.length * 70L

        /** The longest [heard] waits for one line (a node's clip on its way, then played): a player that never ends doesn't hold the next line for good. */
        private const val LONGEST_MS = 45_000L

        /**
         * Waits while the line said in turn [t] is on its way or playing ([busy]), at most [longestMs]; [turn]: the turn now
         * (a later say or a stop cuts it); [talking]: a voice is heard. True when a voice was heard saying it, false when
         * nothing was (no voice could say it, or it was cut while it was still on its way).
         */
        internal suspend fun heard(t: Int, turn: () -> Int, busy: () -> Boolean, talking: () -> Boolean, pollMs: Long = POLL_MS, longestMs: Long = LONGEST_MS): Boolean {
            var sounded = talking()
            var waited = 0L
            while (turn() == t && busy() && waited < longestMs) {
                delay(pollMs)
                waited += pollMs
                if (turn() == t) sounded = sounded || talking()
            }
            return sounded
        }

        /**
         * QA's signal (a debug or qa build): how [text] was said, the last line for a text being what played ("node clip
         * (young-woman)", "clip on the phone", "offline voice", "tts"; companion/bin/qa's voice-node-clip).
         */
        private fun said(text: String, how: String) {
            if (BuildConfig.QA) Log.i(TAG, "said «${Voice.normalize(text)}»: $how")
        }

        private const val KEY_TIP_SEEN = "re_record_tip_seen"
        private const val KEY_FIRST_USED = "first_used"
    }

    /**
     * [voiceName] picks the node clip's voice: a speaker of the node's voice cast ("female" by default,
     * "male", or a character such as "grandma" for a villager's lines); without that clip it falls back to
     * [fallback] (the villager's gender voice), then female, then TTS ([Clips.chain]).
     *
     * [person] (a villager's or resident's id) makes it theirs: with a voice of their own ([VoiceProfile.own]) the
     * line is played in it; else in their archetype's (the profile's speaker, else [voiceName]) at their own pitch
     * and pace, so everyone always sounds like themselves.
     *
     * The first voice is the one wanted: its clip on the phone plays at once; else the node is asked for it
     * (voicing it on demand, a few seconds the first time). A clip in another voice of the chain plays only when
     * the node can't make the line now (offline, no engine, it just failed), then TTS.
     */
    fun say(text: String, slow: Boolean = false, voiceName: String = Clips.FEMALE, fallback: String? = null, person: String? = null) {
        val t = ++turn
        loading = false
        hint?.stop()
        intro?.stop()
        val v = voice
        val c = clips
        val (voices, shared, own) = voicesFor(voiceName, fallback, person)
        val (pitch, rate) = pitchRate(person)
        // only the wanted voice's clip plays at once: another voice's waits until the node can't make this one
        val local = c?.onDevice(text, voices.take(1))
        val route = Clips.route(
            family = v?.ready(text) == true,
            clip = local != null && v != null,
            online = c?.online == true && v != null,
            missedRecently = c?.missedRecently(text, voices) == true,
        )
        if (route != Route.TTS && ready) tts.stop()
        /** No clip to play: the offline voice, else TTS ([fallback]). */
        fun withoutClip() = speakOffline(t, text, slow, pitch, rate)
        /**
         * A clip on the phone in another voice of theirs (their shared one, for a voice of their own), when the wanted one
         * couldn't be had; else the offline voice, else TTS.
         */
        fun otherwise() {
            val alt = c?.onDevice(text, if (own) shared else voices.drop(1))
            if (alt != null && v != null) {
                said(text, "another voice's clip")
                v.playClips(alt, slow, pitch, rate) { withoutClip() }
            } else withoutClip()
        }
        when (route) {
            Route.FAMILY -> {
                said(text, "family")
                v!!.play(text, slow) { withoutClip() }
            }
            Route.CLIP -> {
                said(text, "clip on the phone")
                v!!.playClips(local!!, slow, pitch, rate) { withoutClip() }
            }
            Route.NODE -> {
                v!!.stop()
                loading = true
                c!!.prepare(text, voices) { files ->
                    if (t != turn) return@prepare
                    loading = false
                    if (files != null) {
                        said(text, "node clip (${voices.first()})")
                        v.playClips(files, slow, pitch, rate) { withoutClip() }
                    } else otherwise()
                }
            }
            Route.TTS -> {
                v?.stop()
                if (c != null) otherwise() else withoutClip()
            }
        }
    }

    /**
     * Says [text] as [say] does and returns once it has been heard: its clip (or the phone's voice) played to its end,
     * at once when nothing can say it, or as soon as what is said or stopped after it cuts it (a tap on another 🔊, the
     * microphone). Lines said one after another wait for each other this way ([sayInTurn]; companion/SCENES.md, "I spy").
     * [stopIfLeft]: the caller cancelled (the game left) while the line is still being said, it stops at once; else it
     * plays on (a dialog's line, which only the next one cuts). True when a voice was heard saying it.
     */
    suspend fun sayThrough(text: String, voiceName: String = Clips.FEMALE, fallback: String? = null, person: String? = null, stopIfLeft: Boolean = true): Boolean {
        say(text, voiceName = voiceName, fallback = fallback, person = person)
        val t = turn
        try {
            return heard(t, { turn }, { busy }, { talking })
        } catch (e: CancellationException) {
            if (stopIfLeft && turn == t) stop()
            throw e
        }
    }

    /** Waits until no voice of the app's is speaking or loading: a 🔊 tapped meanwhile is heard to its end before the next line. */
    suspend fun awaitQuiet() {
        while (busy) delay(POLL_MS)
    }

    /**
     * One of several lines in a row (a game's lines, a villager's greeting): once what is being said has ended
     * ([awaitQuiet]), [text] is said to its end ([sayThrough]; cancelled, it stops), or, when nothing can say it, it
     * takes the time to read it ([readingMs]). The next line starts after it, never over it.
     */
    suspend fun sayInTurn(text: String, voiceName: String = Clips.FEMALE, fallback: String? = null, person: String? = null) {
        awaitQuiet()
        val from = System.currentTimeMillis()
        if (!sayThrough(text, voiceName, fallback, person)) delay((readingMs(text) - (System.currentTimeMillis() - from)).coerceAtLeast(0L))
        if (BuildConfig.QA) Log.i(TAG, "heard «${Voice.normalize(text)}» after ${System.currentTimeMillis() - from} ms")
    }

    /**
     * [text] without a clip ([fallback]): the phone's offline voice when it speaks the target language (rendered in the
     * background, 🐢 through its length scale, then played as a clip at the person's pitch and pace), else text-to-speech.
     * [t]: the turn it was asked in; a line that comes late doesn't talk over the next one.
     */
    private fun speakOffline(t: Int, text: String, slow: Boolean, pitch: Float, rate: Float) {
        val o = offline
        val v = voice
        val target = L10n.pair.target.code
        if (o == null || v == null || fallback(false, o.language, target, available) != Fallback.OFFLINE) return synthesize(t, text, slow)
        said(text, "offline voice")
        loading = true
        scope.launch {
            val f: File? = runCatching { o.render(text, if (slow) Piper.SLOW else 1f) }.getOrNull()
            if (t != turn) return@launch
            loading = false
            if (f != null) v.playClips(listOf(f), false, pitch, rate) { synthesize(t, text, slow) } else synthesize(t, text, slow)
        }
    }

    /**
     * The voices for a line (see [say]): the ones to try, best first; [shared]: their archetype's and the chain's; [own]:
     * they have a voice of their own, and the line is heard only in it unless the node can't make it now.
     */
    data class Voices(val voices: List<String>, val shared: List<String>, val own: Boolean) {
        /** Every voice it may be heard in from the phone, best first: the wanted one, then the ones played offline. */
        val all: List<String> get() = (voices + shared).distinct()
    }

    private fun voicesFor(voiceName: String, fallback: String?, person: String?): Voices = voices(voiceName, fallback, clips?.profile(person))

    /** [person]'s pitch and pace with their archetype's voice (1 with a voice of their own, or for no one). */
    private fun pitchRate(person: String?): Pair<Float, Float> {
        val profile = clips?.profile(person)
        return if (profile != null && !profile.own) profile.pitch to profile.rate else 1f to 1f
    }

    /** A long press on 🔊 can have the node record the clip again: it can be reached, and it speaks what's shown. */
    val canReRecord: Boolean get() = clips?.online == true && voice != null

    /**
     * A long press on 🔊: the node records [text] again in the voice a tap plays it in ([Clips.reRecord]), and the new
     * take plays; [then] gets what came of it, for the button to say. Offline it says so at once.
     */
    fun reRecord(text: String, voiceName: String = Clips.FEMALE, fallback: String? = null, person: String? = null, then: (ReRecord) -> Unit) {
        sawReRecordTip() // found it: no tip needed
        val c = clips
        val v = voice
        if (c == null || v == null) return then(ReRecord.Offline)
        val t = ++turn
        hint?.stop()
        intro?.stop()
        v.stop()
        if (ready) tts.stop()
        val (pitch, rate) = pitchRate(person)
        c.reRecord(text, voicesFor(voiceName, fallback, person).voices) { r ->
            if (r is ReRecord.Done && r.files.isNotEmpty() && t == turn) v.playClips(r.files, false, pitch, rate) {}
            then(r)
        }
    }

    private val prefs = context.getSharedPreferences("speaker", Context.MODE_PRIVATE)

    /** The tip about the long press was shown, or Jan found it themselves. */
    private var tipSeen = prefs.getBoolean(KEY_TIP_SEEN, false)

    /** When this phone first spoke with Lani (for the tip, a week later). */
    private val firstUsed: Long = prefs.getLong(KEY_FIRST_USED, 0L).takeIf { it > 0 }
        ?: System.currentTimeMillis().also { prefs.edit().putLong(KEY_FIRST_USED, it).apply() }

    /**
     * True once, the first time after a week of use that it's asked (a tap on 🔊): the tip about the long press shows
     * then, and never again; never when Jan has long-pressed already, or when the node can't re-record.
     */
    fun reRecordTipOnce(): Boolean {
        if (!canReRecord || !ReRecord.tipDue(firstUsed, System.currentTimeMillis(), tipSeen)) return false
        sawReRecordTip()
        return true
    }

    private fun sawReRecordTip() {
        if (tipSeen) return
        tipSeen = true
        prefs.edit().putBoolean(KEY_TIP_SEEN, true).apply()
    }

    /** [text] by Android's text-to-speech, asked in turn [t]: speaking from now on, until its own callbacks say otherwise. */
    private fun synthesize(t: Int, text: String, slow: Boolean) {
        said(text, if (available) "tts" else "nothing (no tts voice)")
        if (!available) return
        tts.setSpeechRate(if (slow) 0.55f else 0.9f)
        val id = "say-$t"
        utterance = id
        ttsTalking = true
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS && utterance == id) ttsTalking = false
    }

    /** Silences speech, e.g. before the microphone opens. */
    fun stop() {
        turn++
        loading = false
        hint?.stop()
        intro?.stop()
        voice?.stop()
        if (ready) tts.stop()
        ttsTalking = false
    }

    /** Opens the system screen where the Slovenian voice can be installed. */
    fun openVoiceSettings() = context.startActivity(
        Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun shutdown() {
        voice?.stop()
        tts.shutdown()
    }
}
