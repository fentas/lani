package si.lanisce.lani.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import si.lanisce.lani.audio.Piper
import si.lanisce.lani.data.IntroSpeech.Line
import si.lanisce.lani.data.IntroSpeech.Mark
import si.lanisce.lani.data.IntroSpeech.Script
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.road.RoadPlay
import si.lanisce.lani.road.RoadPrompts
import java.io.File
import kotlin.coroutines.resume

/**
 * A step of a unit's guided introduction read aloud (companion/COURSE.md, "Read aloud"; what is read: [IntroSpeech]), in
 * the teacher's voice:
 *
 * 1. the explanation as one clip of the voice store, voiced in one go by the node in the learner's base language with the
 *    Slovene inside it ([Clips.explanation]): on the phone it plays at once; the node having it, it's waited for a moment
 *    ([EXPLAIN_WAIT_MS]: a download); the node making it now, a little longer ([EXPLAIN_MAKE_MS], as a line's 🔊 waits
 *    for its clip; the intro asks for its steps' explanations as it opens, [ahead], so the first step's is mostly on its
 *    way already and the next ones' here); else it's read in its runs ([Script.stitched]: the base-language parts by the
 *    phone's own voice of that language, rendered to files as the car's prompts are, [RoadPrompts]; the Slovene parts in
 *    the teacher's voice, got ready while the clip is waited for) and the clip is there next time;
 * 2. then the examples, the quick question as far as it gives nothing away, each in the teacher's voice: her clip on the
 *    phone, else the node voices it now (as a line's 🔊), else another of her voices' on the phone, else the phone's
 *    offline voice (Piper), else the phone's voice for the language learned; else that line is left out.
 *
 * With short pauses between, what is read now marked ([now]), the teacher drawn talking while a voice plays ([speaking]).
 * One voice at a time: reading stops what [Speaker] says, and [Speaker] stops it whenever it says or stops anything (a
 * line's 🔊, the 🎤, the app going to the background). The background sounds duck under it ([sounding]). The phone without
 * a voice for the learner's base language: only the Slovene is read ([noBaseVoice]).
 */
class IntroVoice(context: Context, private val scope: CoroutineScope, private val speaker: Speaker) {
    private val app = context.applicationContext
    private val prefs = context.getSharedPreferences("school", Context.MODE_PRIVATE)
    private val dir = File(context.cacheDir, "intro-voice")
    private val main = Handler(Looper.getMainLooper())

    /** "🔊 Beri razlago · Read the explanations": each step is read as it appears (on by default); off, only its 🔊 reads. */
    var auto by mutableStateOf(prefs.getBoolean(KEY_AUTO, true))
        private set

    fun readAuto(on: Boolean) {
        auto = on
        prefs.edit().putBoolean(KEY_AUTO, on).apply()
        if (!on) stop()
    }

    /** What is read now: the script's id and the piece's mark. */
    data class Now(val id: String, val mark: Mark)

    /** The piece being read (it stays marked through the pause after it), else null. Compose state, on the main thread. */
    var now by mutableStateOf<Now?>(null)
        private set

    /** The script being read ([Script.id]: getting ready or playing), else null. */
    var reading by mutableStateOf<String?>(null)
        private set

    /** A voice plays now (not a pause, not getting ready): the teacher is drawn talking. */
    var speaking by mutableStateOf(false)
        private set

    /** Playing now: the background sounds duck under it ([Speaker.talking]). Read from any thread. */
    @Volatile
    var sounding = false
        private set

    /** The phone has no voice for the learner's base language: only the Slovene of an explanation was read. */
    var noBaseVoice by mutableStateOf(false)
        private set

    /** The note on [noBaseVoice] was shown: it's said once. */
    var noBaseVoiceNoted by mutableStateOf(prefs.getBoolean(KEY_NOTED, false))
        private set

    fun notedNoBaseVoice() {
        if (noBaseVoiceNoted) return
        noBaseVoiceNoted = true
        prefs.edit().putBoolean(KEY_NOTED, true).apply()
    }

    /** The screen reader (TalkBack) is on: it reads the text itself, so nothing is read by itself. */
    val screenReader: Boolean
        get() = (app.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

    /**
     * The node voices an explanation as one clip (GET /state's features has [FEATURE]): only then is it asked for one; an
     * older bridge would voice the English as a Slovene line. Its clips on the phone play all the same.
     */
    var explains: () -> Boolean = { false }

    /** Who reads: the teacher's voice of the cast ([voiceName]: "teacher"), her gender's narrator ([fallback]), as [person]. */
    data class Reader(val voiceName: String, val fallback: String?, val person: String?)

    private var job: Job? = null
    private var player: MediaPlayer? = null

    /** The explanations asked for ahead ([ahead]), one after the other. */
    private var aheadJob: Job? = null

    /** The phone's voices by language, each opened once (the base's, and the fallback for the language learned). */
    private val voices = HashMap<Lang, Deferred<RoadPrompts?>>()
    private val opened = mutableListOf<RoadPrompts>()

    /** The renders under way, by file name: a second ask for the same text waits for the first. */
    private val rendering = HashMap<String, Deferred<File?>>()

    /**
     * Gets the explanations' clips of [scripts] (an intro's steps, from the one shown on) onto the phone ahead, in their
     * order and one at a time (the node voices each in a few seconds; never a burst), so a step finds its clip as it's
     * read: asked for as the intro opens, while the teacher walks in. Only from a node that voices them ([explains]); an
     * explanation on the phone, or the node couldn't just now, is passed over. What was asked ahead before stops.
     */
    fun ahead(scripts: List<Script>, who: Reader) {
        aheadJob?.cancel()
        val c = speaker.clips ?: return
        if (!c.online || !explains()) return
        aheadJob = scope.launch {
            val v = voicesOf(who)
            for (e in scripts.mapNotNull { it.explanation }) {
                if (c.explanationOnDevice(e.plain, e.lang, v.voices.take(1)) != null || c.explanationMissed(e.plain, e.lang, v.voices)) continue
                if (c.explanation(e.plain, e.lang, v.voices).await() == null) Log.i(TAG, "ahead: no clip now for ${e.lang} \"${e.plain.take(40)}\"")
            }
        }
    }

    /** No more explanations asked for ahead (the intro left); the one on its way still comes. */
    fun stopAhead() {
        aheadJob?.cancel()
        aheadJob = null
    }

    /** [who]'s voices, as their lines are played ([Speaker.voices]: their profile's speaker, their archetype, the chain). */
    private fun voicesOf(who: Reader): Speaker.Voices = Speaker.voices(who.voiceName, who.fallback, speaker.clips?.profile(who.person))

    /** Reads [script] in [who]'s voice ([slow]: 🐢), from its start; what was read before stops. */
    fun play(script: Script, who: Reader, slow: Boolean = false) {
        speaker.stop() // a line's 🔊, the hint, and what this reads
        stop()
        reading = script.id
        job = scope.launch {
            try {
                read(script, who, slow)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "${script.id}: not read", e)
            } finally {
                // not when another reading took over meanwhile (this one stopped for it)
                if (job == null || job === coroutineContext[Job]) done()
            }
        }
    }

    /** Silences it (any thread): what is getting ready is dropped, what plays stops. */
    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { stop() }
            return
        }
        job?.cancel()
        job = null
        player?.let { p ->
            runCatching { p.stop() }
            p.release()
        }
        player = null
        done()
    }

    /** Stops it when it reads [id] (a step left). */
    fun stop(id: String) {
        if (reading == id) stop()
    }

    private fun done() {
        sounding = false
        speaking = false
        now = null
        reading = null
    }

    /** The app is gone: the phone's voices let go. */
    fun release() {
        aheadJob?.cancel()
        stop()
        voices.clear()
        opened.forEach { it.close() }
        opened.clear()
    }

    /** What a piece plays: its [files], where they came from ([source], for the log and QA), [baked]: slow already (Piper's 🐢). */
    private class Sound(val files: List<File>, val source: String, val baked: Boolean = false)

    /** A piece of the script: its [line], the sound it gets ([sound]), in the teacher's voice or the phone's ([slovene]). */
    private class Piece(val line: Line, val sound: Deferred<Sound?>)

    private suspend fun read(script: Script, who: Reader, slow: Boolean) = coroutineScope {
        val c = speaker.clips
        val profile = c?.profile(who.person)
        val v = Speaker.voices(who.voiceName, who.fallback, profile)
        val pitch = if (profile != null && !profile.own) profile.pitch else 1f
        val rate = if (profile != null && !profile.own) profile.rate else 1f
        val e = script.explanation
        // the explanation's clip asked for first (the node voices what it's asked in turn), then the lines after it got
        // ready meanwhile; while the clip is waited for, its runs' Slovene too, in case it doesn't come in time (their
        // base-language parts by the phone's voice only then)
        val onPhone = e?.let { c?.explanationOnDevice(it.plain, it.lang, v.voices.take(1)) }
        val whole = if (e != null && onPhone == null) async { explanationClip(e, v) } else null
        val after = script.lines.map { l -> Piece(l, async { sound(l, v, slow) }) }
        val runs = if (whole != null) script.stitched.map { l -> Piece(l, async(start = if (l.slovene) CoroutineStart.DEFAULT else CoroutineStart.LAZY) { sound(l, v, slow) }) } else emptyList()
        val clip = onPhone ?: whole?.await()
        val first = when {
            e == null -> emptyList()
            clip != null -> {
                runs.forEach { it.sound.cancel() } // the clips they asked the node for still come, for the 🔊 of a form
                listOf(Piece(Line(e.plain, false, Mark.Whole), CompletableDeferred(Sound(clip, "explanation:${e.lang}"))))
            }
            else -> runs.onEach { it.sound.start() }
        }
        val pieces = first + after
        for ((i, p) in pieces.withIndex()) {
            val sound = p.sound.await() ?: continue
            now = Now(script.id, p.line.mark)
            val explained = p.line.mark == Mark.Whole
            Log.i(TAG, "${script.id}: ${i + 1}/${pieces.size} ${if (p.line.slovene) "sl" else e?.lang ?: L10n.pair.base.code} ${sound.source} \"${p.line.text.take(60)}\"")
            // the Slovene at the teacher's pitch and pace (her archetype's voice), the phone's own voice as it is
            val (sp, pi) = when {
                explained -> rate * (if (slow) EXPLAIN_SLOW else 1f) to pitch
                p.line.slovene -> rate * (if (slow && !sound.baked) CLIP_SLOW else 1f) to pitch
                else -> (if (slow) BASE_SLOW else 1f) to 1f
            }
            for (f in sound.files) playFile(f, sp, pi)
            pieces.getOrNull(i + 1)?.let { next -> delay(pause(p.line, next.line)) }
        }
    }

    /** The pause before [next] after [prev]: a breath within a line, more between lines, between parts of the step most. */
    private fun pause(prev: Line, next: Line): Long {
        val a = prev.mark
        val b = next.mark
        return when {
            a is Mark.Part && b is Mark.Part -> if (a.line == b.line) PAUSE_RUN else PAUSE_LINE
            a is Mark.Example && b is Mark.Example -> PAUSE_EXAMPLE
            a == b -> PAUSE_RUN
            else -> PAUSE_PART
        }
    }

    /**
     * The explanation's one clip: on the phone in her wanted voice, else (the node reachable, and it voices them) the
     * node's: waited for a moment when the node has it ([EXPLAIN_WAIT_MS]: a download), a little longer when the node
     * makes it now or is making it (asked ahead, [EXPLAIN_MAKE_MS]); what doesn't come in time comes for next time.
     * Offline, a clip in another of her voices on the phone. Null: it's read in its runs.
     */
    private suspend fun explanationClip(e: IntroSpeech.Explanation, v: Speaker.Voices): List<File>? {
        val c = speaker.clips ?: return null
        c.explanationOnDevice(e.plain, e.lang, v.voices.take(1))?.let { return it }
        if (c.online && explains() && !c.explanationMissed(e.plain, e.lang, v.voices)) {
            val wait = if (c.hasExplanation(e.plain, e.lang, v.voices)) EXPLAIN_WAIT_MS else EXPLAIN_MAKE_MS
            val work = c.explanation(e.plain, e.lang, v.voices)
            withTimeoutOrNull(wait) { work.await() }?.let { return it }
            Log.i(TAG, "explanation clip not here in ${wait} ms, for next time: ${e.lang} \"${e.plain.take(40)}\"")
        }
        return c.explanationOnDevice(e.plain, e.lang, v.all)
    }

    /** A line's sound: the Slovene in the teacher's voice ([target]), the rest by the phone's voice of the learner's base. */
    private suspend fun sound(l: Line, v: Speaker.Voices, slow: Boolean): Sound? =
        if (l.slovene) target(l.text, v, slow) else base(l.text)

    /** [text] by the phone's voice of the learner's base language; null (and [noBaseVoice]) when the phone has none. */
    private suspend fun base(text: String): Sound? {
        val lang = L10n.pair.base
        noBaseVoice = voice(lang, BASE_RATE) == null
        if (noBaseVoice) return null
        return render(lang, text, BASE_RATE)?.let { Sound(listOf(it), "tts:${lang.code}") }
    }

    /**
     * [text] in the teacher's voice: her clip on the phone, else the node gets or voices it now (waiting as long as a
     * line's 🔊 does), else another of her voices' clip on the phone, else the phone's offline voice (Piper), else the
     * phone's voice for the language learned; null when none can say it.
     */
    private suspend fun target(text: String, v: Speaker.Voices, slow: Boolean): Sound? {
        val c = speaker.clips
        if (c != null) {
            c.onDevice(text, v.voices.take(1))?.let { return Sound(it, "clip") }
            if (c.online && !c.missedRecently(text, v.voices) && withTimeoutOrNull(Clips.WAIT_MS) { c.load(text, v.voices) } == true) {
                c.onDevice(text, v.voices)?.let { return Sound(it, "node") }
            }
            c.onDevice(text, if (v.own) v.shared else v.voices.drop(1))?.let { return Sound(it, "clip:other") }
        }
        val target = L10n.pair.target
        val o = speaker.offline
        if (o != null && o.speaks(target.code)) {
            runCatching { o.render(text, if (slow) Piper.SLOW else 1f) }.getOrNull()?.let { return Sound(listOf(it), "piper", baked = true) }
        }
        return render(target, text, TARGET_RATE)?.let { Sound(listOf(it), "tts:${target.code}") }
    }

    /** [text] in [lang] by the phone's voice, as a file in the cache (rendered once); null when the phone can't say it. */
    private suspend fun render(lang: Lang, text: String, rate: Float): File? {
        val f = File(dir, RoadPlay.promptFile("${lang.code}@$rate", text))
        if (withContext(Dispatchers.IO) { f.isFile && f.setLastModified(System.currentTimeMillis()) }) return f
        val d = rendering[f.name] ?: scope.async {
            val p = voice(lang, rate) ?: return@async null
            if (p.render(text, f)) f.also { trim() } else null
        }.also { d ->
            rendering[f.name] = d
            d.invokeOnCompletion { main.post { if (rendering[f.name] === d) rendering.remove(f.name) } }
        }
        return d.await()
    }

    /** The phone's voice for [lang], opened once; null when it has none (asked again next time: it may be installed). */
    private suspend fun voice(lang: Lang, rate: Float): RoadPrompts? {
        val d = voices.getOrPut(lang) {
            scope.async {
                val p = RoadPrompts(app)
                if (p.open(Speaker.locale(lang), rate)) p.also { opened += it } else null.also { p.close() }
            }
        }
        return d.await() ?: null.also { if (voices[lang] === d) voices.remove(lang) }
    }

    /** Drops the least recently played renders over [MAX_BYTES]. */
    private suspend fun trim() = withContext(Dispatchers.IO) {
        val all = dir.listFiles()?.filter { it.name.endsWith(".wav") }.orEmpty()
        val drop = Clips.evict(all.map { Triple(it.name, it.length(), it.lastModified()) }, MAX_BYTES)
        all.filter { it.name in drop }.forEach { it.delete() }
    }

    /** Plays [f] to its end at [speed] and [pitch] (a voice of the app's, on the media stream, as a line's clip). */
    private suspend fun playFile(f: File, speed: Float, pitch: Float) = suspendCancellableCoroutine { cont ->
        val mp = MediaPlayer()
        player = mp
        fun finish() {
            if (player === mp) {
                player = null
                speaking = false
                sounding = false
            }
            mp.release()
            if (cont.isActive) cont.resume(Unit)
        }
        mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        mp.setOnPreparedListener {
            // setting a speed (or a pitch) on a prepared player starts it
            if ((speed == 1f && pitch == 1f) || runCatching { it.playbackParams = it.playbackParams.setSpeed(speed).setPitch(pitch) }.isFailure) it.start()
            speaking = true
            sounding = true
        }
        mp.setOnCompletionListener { finish() }
        mp.setOnErrorListener { _, _, _ -> finish(); true }
        try {
            mp.setDataSource(f.path)
            mp.prepareAsync()
        } catch (e: Exception) {
            finish()
        }
        cont.invokeOnCancellation {
            main.post {
                if (player === mp) {
                    runCatching { mp.stop() }
                    mp.release()
                    player = null
                }
            }
        }
    }

    companion object {
        /** What GET /state's features say when the node voices an explanation as one clip. */
        const val FEATURE = "explanations"

        private const val TAG = "IntroVoice"
        private const val KEY_AUTO = "read_explanations"
        private const val KEY_NOTED = "no_base_voice_noted"

        /** How long the node's explanation clip is waited for (a download) before its runs are read instead. */
        private const val EXPLAIN_WAIT_MS = 3_000L

        /**
         * How long an explanation the node makes now is waited for (ElevenLabs takes about two to five seconds for a step's
         * few lines; a line's 🔊 waits [Clips.WAIT_MS]) before its runs are read instead; the clip comes for next time.
         */
        private const val EXPLAIN_MAKE_MS = 6_000L

        /** The phone's voice in the learner's own language at its own pace; the Slovene a little slower, as [Speaker]'s. */
        private const val BASE_RATE = 1f
        private const val TARGET_RATE = 0.9f

        /** 🐢: the Slovene clips as a line's 🐢 plays them, the explanation's clip and the phone's own voice a little less. */
        private const val CLIP_SLOW = 0.75f
        private const val EXPLAIN_SLOW = 0.8f
        private const val BASE_SLOW = 0.85f

        /** The pauses: within a line, between lines, between the examples, between the parts (explanation, examples, question). */
        private const val PAUSE_RUN = 120L
        private const val PAUSE_LINE = 350L
        private const val PAUSE_EXAMPLE = 500L
        private const val PAUSE_PART = 700L

        /** The rendered pieces kept in the cache. */
        private const val MAX_BYTES = 20L * 1024 * 1024
    }
}
