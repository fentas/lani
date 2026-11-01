package si.lanisce.lani.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import si.lanisce.lani.game.HintPiece
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.road.RoadPlay
import si.lanisce.lani.road.RoadPrompts
import java.io.File

/**
 * The 🔊 of "📖 Namig · Hint" (companion/SCENES.md, "The hint"): its spoken TL;DR ([si.lanisce.lani.game.HintSpeech])
 * played as one. The base-language pieces by the phone's offline voice of the base when it is on the phone (Piper speaks
 * any of its voices: [si.lanisce.lani.audio.OfflineVoice.speaksIn]), else by the phone's own text-to-speech, rendered to
 * files as the car's prompts are ([RoadPrompts]; kept in the cache, so a hint heard again starts at once); the Slovene ones
 * by the app's voice: the voice store's clip on the phone (on a visit the town's), else the node voices it now (as a
 * line's 🔊 does, [Clips]), else the offline voice of the language learned, else the phone's voice for it, else that piece
 * is left out. Once every piece is here they play one after the other in one player, without gaps.
 *
 * One voice at a time: a tap stops what [Speaker] says, and [Speaker] stops this whenever it says or stops anything (a
 * line, the 🎤 opening). The background sounds duck under it as under any voice ([sounding], read by [Speaker.talking]).
 */
class HintVoice(context: Context, private val scope: CoroutineScope, private val speaker: Speaker) {
    private val app = context.applicationContext
    private val dir = File(context.cacheDir, "hint-voice")
    private val main = Handler(Looper.getMainLooper())

    /** The script loading or playing now (its 🔊 shows ⏹), else null. Compose state, written on the main thread. */
    var current by mutableStateOf<List<HintPiece>?>(null)
        private set

    /** The pieces of [current] are being got ready: rendered, or voiced by the node (the 🔊 spins). */
    var loading by mutableStateOf(false)
        private set

    /** Playing now: the background sounds duck under it. Read from any thread. */
    @Volatile
    var sounding = false
        private set

    private var job: Job? = null
    private var player: ExoPlayer? = null

    /** The phone's voices by language, each opened once (the base's, and the fallback for the language learned). */
    private val voices = HashMap<Lang, Deferred<RoadPrompts?>>()
    private val opened = mutableListOf<RoadPrompts>()

    /** The renders under way, by file name: a second ask for the same piece waits for the first. */
    private val rendering = HashMap<String, Deferred<File?>>()

    /** A tap on the 🔊: [script] plays, or stops when it is the one playing ([needsBase]: see [play]). */
    fun toggle(script: List<HintPiece>, needsBase: Boolean = true) = if (current == script) stop() else play(script, needsBase)

    /**
     * Plays [script]. [needsBase]: without a phone's voice for the learner's base nothing plays (a hint's Slovene words
     * alone explain nothing); the village's first words (ui/game/GameIntro) play their language learned all the same.
     */
    fun play(script: List<HintPiece>, needsBase: Boolean = true) {
        speaker.stop() // what a line says now
        stop() // and a hint playing or loading
        if (script.isEmpty()) return
        current = script
        loading = true
        job = scope.launch {
            val files = try {
                files(script, needsBase)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            loading = false
            if (files.isEmpty()) current = null else start(files)
        }
    }

    /**
     * The card opened: [script]'s base-language pieces rendered ahead, so a tap plays at once. Nothing is asked of the
     * node until the 🔊 is tapped.
     */
    fun warm(script: List<HintPiece>) {
        scope.launch {
            runCatching { for (p in script) if (!p.target) base(p.lang ?: L10n.pair.base, p.text) }
        }
    }

    /** Silences it (any thread): what's loading is dropped, what's playing stops. */
    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { stop() }
            return
        }
        job?.cancel()
        job = null
        player?.let {
            it.stop()
            it.release()
        }
        player = null
        sounding = false
        loading = false
        current = null
    }

    /** The app is gone: the player and the phone's voices let go. */
    fun release() {
        stop()
        voices.clear()
        opened.forEach { it.close() }
        opened.clear()
    }

    /**
     * Each piece's files, in order ([playable]): a base piece rendered by the phone's voice, a Slovene one from the voice
     * store or the phone's voice (else left out). Each piece's source is logged (tag HintVoice: QA's evidence).
     */
    private suspend fun files(script: List<HintPiece>, needsBase: Boolean): List<File> {
        val got = sourced(script)
        return playable(script, got.map { it?.files }, needsBase)
    }

    /**
     * Each piece of [script] got ready, in order (null: nothing could say it): a base piece by the offline voice of its
     * language or the phone's voice ([base]), a Slovene one from the voice store or the phone ([target]). Each piece's source
     * is logged (tag HintVoice: QA's evidence).
     */
    internal suspend fun sourced(script: List<HintPiece>): List<Sourced?> = coroutineScope {
        val pair = L10n.pair
        // each piece's language: its own (the first launch's), else the pair's
        val langs = script.map { p -> p.lang ?: if (p.target) pair.target else pair.base }
        val got = script.mapIndexed { i, p ->
            val lang = langs[i]
            async { if (p.target) target(p.text, lang, own = p.lang != null) else base(lang, p.text) }
        }.awaitAll()
        script.forEachIndexed { i, p -> Log.i(TAG, "${i + 1}/${script.size} ${langs[i].code} ${got[i]?.source ?: "none"} \"${p.text.take(60)}\"") }
        got
    }

    /**
     * A piece's files and where they came from: "clip" (on the phone), "node" (voiced now), "clip:other", "offline:<lang>"
     * (the offline voice of that language), "tts:<lang>".
     */
    internal class Sourced(val files: List<File>, val source: String)

    /** A base-language piece: the offline voice of [lang] when it is on the phone, else the phone's voice ([render]). */
    private suspend fun base(lang: Lang, text: String): Sourced? =
        offline(lang, text) ?: render(lang, text, BASE_RATE)?.let { Sourced(listOf(it), "tts:${lang.code}") }

    /**
     * [text] by the phone's offline voice of [lang] ([si.lanisce.lani.audio.OfflineVoice.renderIn], at its own pace, as a
     * line's is), when that voice is on the phone; null otherwise or when it can't.
     */
    private suspend fun offline(lang: Lang, text: String): Sourced? {
        val o = speaker.offline?.takeIf { it.speaksIn(lang.code) } ?: return null
        val f = try {
            o.renderIn(lang.code, text, 1f)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return f?.let { Sourced(listOf(it), "offline:${lang.code}") }
    }

    /**
     * A Slovene piece's clips: the narrator's on the phone, else the node gets or voices it now (waiting as long as a
     * line's 🔊 does), else another narrator's on the phone, else the offline voice of the language learned, else the
     * phone's own voice for it; null when none can say it. [lang]: the piece's language; [own]: it is the piece's own (game/SetupSpeech), not the pair's.
     */
    private suspend fun target(text: String, lang: Lang, own: Boolean = false): Sourced? {
        val c = speaker.clips
        // the narrator's clips are the village's language: a piece in another (the first launch's, before pairing) is the phone's
        if (c != null && lang == L10n.pair.target) {
            c.onDevice(text, NARRATOR.take(1))?.let { return Sourced(it, "clip") }
            if (c.online && !c.missedRecently(text, NARRATOR) && withTimeoutOrNull(Clips.WAIT_MS) { c.load(text, NARRATOR) } == true) {
                c.onDevice(text, NARRATOR)?.let { return Sourced(it, "node") }
            }
            c.onDevice(text, NARRATOR.drop(1))?.let { return Sourced(it, "clip:other") }
        }
        // no clip: the offline voice of its language when it is on the phone, as a line's (a visit's town language too)
        offline(lang, text)?.let { return it }
        // [Speaker.available] is about the pair's language; a piece in a language of its own asks the phone's voice for it
        if (!own && !speaker.available) return null
        return render(lang, text, TARGET_RATE)?.let { Sourced(listOf(it), "tts:${lang.code}") }
    }

    /** [text] in [lang] by the phone's voice, as a file in the cache (rendered once); null when the phone can't say it. */
    private suspend fun render(lang: Lang, text: String, rate: Float): File? {
        val f = File(dir, RoadPlay.promptFile(lang.code, text))
        if (withContext(Dispatchers.IO) { f.isFile && f.setLastModified(System.currentTimeMillis()) }) return f
        val d = rendering[f.name] ?: scope.async {
            val v = voice(lang, rate) ?: return@async null
            if (v.render(text, f)) f.also { trim() } else null
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

    /** Plays [files] one after the other, without gaps; done, it lets go. */
    private fun start(files: List<File>) {
        val p = ExoPlayer.Builder(app)
            // a voice of the app's own, like a line's clip: it takes no audio focus from other apps
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), false)
            .build()
        player = p
        p.addListener(object : Player.Listener {
            // let go after the callback, not inside it
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) main.post { if (player === p) stop() }
            }

            override fun onPlayerError(error: PlaybackException) {
                main.post { if (player === p) stop() }
            }
        })
        p.setMediaItems(files.map { MediaItem.fromUri(Uri.fromFile(it)) })
        p.prepare()
        sounding = true
        p.play()
    }

    companion object {
        private const val TAG = "HintVoice"

        /** The narrators: the Slovene of a hint is nobody's line. */
        private val NARRATOR = listOf(Clips.FEMALE, Clips.MALE)

        /**
         * What plays of [script], whose pieces got [got] (null: nothing could say it), in order. [needsBase]: when every
         * base piece failed (the phone has no voice for the learner's base), nothing; else what was got, the rest left out.
         */
        fun <F> playable(script: List<HintPiece>, got: List<List<F>?>, needsBase: Boolean): List<F> {
            val said = script.indices.filter { !script[it].target }
            if (needsBase && said.isNotEmpty() && said.all { got[it] == null }) return emptyList()
            return got.flatMap { it.orEmpty() }
        }

        /** The phone's voice in the learner's own language at its own pace; the Slovene a little slower, as [Speaker]'s. */
        private const val BASE_RATE = 1f
        private const val TARGET_RATE = 0.9f

        /** The rendered pieces kept in the cache. */
        private const val MAX_BYTES = 20L * 1024 * 1024
    }
}
