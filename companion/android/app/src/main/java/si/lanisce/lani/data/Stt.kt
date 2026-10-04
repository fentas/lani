package si.lanisce.lani.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase

// --- speech recognition on the node: Whisper behind POST /stt (companion/bridge/src/stt.ts) ---------

/** One word the node's recognizer heard; [prob] is how sure it was (0..1), null when not reported. */
@Serializable
data class SttWord(val word: String, val start: Double? = null, val end: Double? = null, val prob: Double? = null)

@Serializable
data class SttSegment(val start: Double? = null, val end: Double? = null, val text: String = "", val words: List<SttWord> = emptyList())

/** POST /stt: the transcript, its segments with per-word probabilities, and the audio length in seconds. */
@Serializable
data class SttResult(
    val text: String,
    val segments: List<SttSegment> = emptyList(),
    val language: String = "sl",
    val duration: Double? = null,
    val model: String? = null,
) {
    val words: List<SttWord> get() = segments.flatMap { it.words }
}

/** Thrown when the node answers but can't recognize now (503: not running, starting or busy). */
class SttUnavailable(message: String) : IOException(message)

/** The pure parts: request, parsing, and turning word probabilities into grading input and hints. */
object SttSpeech {
    /**
     * Words below this probability count as unclear. On clean Slovene TTS clips 19% of the
     * sentences had such a word, mostly the ones the voice itself slurred (companion/stt-local/README.md).
     */
    const val UNCLEAR = 0.3
    private const val MAX_PROMPT = 300

    /**
     * `/stt?language=it&prompt=<expected>`: the language is the learner's target (the pair's, [L10n.pair]), and the
     * expected sentence steers spelling toward it.
     */
    fun url(base: String, expected: String?, language: String = L10n.pair.target.code): HttpUrl =
        (base.trimEnd('/') + "/stt").toHttpUrl().newBuilder()
            .addQueryParameter("language", language)
            .apply { expected?.trim()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("prompt", it.take(MAX_PROMPT)) } }
            .build()

    fun request(config: BridgeConfig, audio: File, expected: String?, language: String = L10n.pair.target.code): Request = Request.Builder()
        .url(url(config.baseUrl, expected, language))
        .header("Authorization", "Bearer ${config.token}")
        .post(audio.asRequestBody("audio/mp4".toMediaType()))
        .build()

    fun parse(raw: String): SttResult = json.decodeFromString(SttResult.serializer(), raw)

    /** `/stt/status?language=it`: whether the node recognizes that language (its model is there, or can be loaded). */
    fun statusUrl(base: String, language: String): HttpUrl =
        (base.trimEnd('/') + "/stt/status").toHttpUrl().newBuilder().addQueryParameter("language", language).build()

    /** The mean word probability, as the recognizer's confidence (0..1), or null when none was reported. */
    fun confidence(r: SttResult): Float? =
        r.words.mapNotNull { it.prob }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    /** What was heard, graded like any recognizer result, with the unclear words attached. */
    fun heard(r: SttResult, accept: List<String>): SpeechGrading.Heard? {
        if (r.text.isBlank()) return null
        val h = SpeechGrading.best(listOf(r.text), confidence(r)?.let { floatArrayOf(it) }, accept) ?: return null
        return h.copy(unclear = unclear(r, h.result.expected))
    }

    /**
     * Words the recognizer was unsure of (probability under [threshold]), spelled as the learner
     * should say them: a heard word that lines up with a word of [expected] gives that word; a
     * heard word with no counterpart is left out (it is extra, not mispronounced), and so are
     * one-letter words (v, z, s, k: Whisper is often unsure of them however they are said). At most [max].
     */
    fun unclear(r: SttResult, expected: String, threshold: Double = UNCLEAR, max: Int = 3): List<String> {
        val heard = r.words.mapNotNull { w -> core(w.word)?.let { it to (w.prob ?: 1.0) } }
        val exp = expected.split(Regex("\\s+")).mapNotNull(::core)
        val pairs = align(heard.map { it.first }, exp)
        val out = LinkedHashSet<String>()
        for ((i, j) in pairs) {
            if (j == null || heard[i].second >= threshold || exp[j].length < 2) continue
            out += exp[j]
        }
        return out.take(max)
    }

    /** "🎯 izgovorjava · pronunciation: 'čebelnjak' was unclear", or null when every word was clear. */
    fun hint(words: List<String>): String? = when (words.size) {
        0 -> null
        else -> "🎯 ${bi("stt.pronunciation")}: ${inBase("stt.unclear", "n" to words.size, "words" to words.joinToString(", ") { "'$it'" })}"
    }

    /** The word without surrounding punctuation, or null when nothing is left. */
    internal fun core(w: String): String? = Regex("[\\p{L}\\p{N}].*[\\p{L}\\p{N}]|[\\p{L}\\p{N}]").find(w)?.value

    private fun key(w: String) = Grading.fold(Grading.normalize(w))

    /**
     * Word alignment of [a] (heard) to [b] (expected) by edit distance: equal words cost 0, similar
     * ones (a few letters apart, like "čebelniak"/"čebelnjak") 2, other words 4, a missing or extra
     * word 3. Returns, for each index of [a], the index of [b] it stands for, or null when extra.
     */
    internal fun align(a: List<String>, b: List<String>): List<Pair<Int, Int?>> {
        val ka = a.map(::key)
        val kb = b.map(::key)
        fun sub(i: Int, j: Int): Int = when {
            ka[i] == kb[j] -> 0
            Grading.distance(ka[i], kb[j]) <= maxOf(1, maxOf(ka[i].length, kb[j].length) / 3) -> 2
            else -> 4
        }
        val d = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 0..a.size) d[i][0] = 3 * i
        for (j in 0..b.size) d[0][j] = 3 * j
        for (i in 1..a.size) for (j in 1..b.size) {
            d[i][j] = minOf(d[i - 1][j] + 3, d[i][j - 1] + 3, d[i - 1][j - 1] + sub(i - 1, j - 1))
        }
        val out = ArrayList<Pair<Int, Int?>>()
        var i = a.size
        var j = b.size
        while (i > 0) when {
            j > 0 && d[i][j] == d[i - 1][j - 1] + sub(i - 1, j - 1) -> { out += (i - 1) to (j - 1); i--; j-- }
            d[i][j] == d[i - 1][j] + 3 -> { out += (i - 1) to null; i-- }
            else -> j--
        }
        return out.reversed()
    }
}

/**
 * When to stop recording on its own, fed MediaRecorder amplitudes (0..32767) with their time in
 * ms: once speech has started (at least [speech] for [startMs]), after [quietMs] below a sixth of
 * the loudest level so far; or after [giveUpMs] when nothing loud enough came. Tapping stops too.
 */
class SilenceGate(
    private val speech: Int = 1_500,
    private val startMs: Long = 150,
    private val quietMs: Long = 1_400,
    private val giveUpMs: Long = 8_000,
) {
    /** Speech was heard: the take is worth sending. */
    var started = false
        private set
    private var peak = 0
    private var first = -1L
    private var loudSince = -1L
    private var quietSince = -1L

    /** True when recording should stop now. */
    fun feed(amplitude: Int, atMs: Long): Boolean {
        if (first < 0) first = atMs
        peak = maxOf(peak, amplitude)
        if (!started) {
            if (amplitude >= speech) {
                if (loudSince < 0) loudSince = atMs
                if (atMs - loudSince >= startMs) started = true
            } else loudSince = -1
            return !started && atMs - first >= giveUpMs
        }
        if (amplitude < maxOf(speech / 2, peak / 6)) {
            if (quietSince < 0) quietSince = atMs
            return atMs - quietSince >= quietMs
        }
        quietSince = -1
        return false
    }

    /** How long it has been quiet at [atMs] since speech started (0 while loud, or before speech): a pause to cut at. */
    fun quietFor(atMs: Long): Long = if (quietSince < 0) 0 else atMs - quietSince
}

/** HTTP for POST /stt and GET /stt/status, with the app token. */
class SttApi(private val config: BridgeConfig) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(audio: File, expected: String?, language: String): SttResult = withContext(Dispatchers.IO) {
        http.newCall(SttSpeech.request(config, audio, expected, language)).execute().use { r ->
            val body = r.body?.string().orEmpty()
            when {
                r.code == 503 || r.code == 404 -> throw SttUnavailable("HTTP ${r.code}")
                !r.isSuccessful -> throw IOException("HTTP ${r.code}: ${body.take(200)}")
                else -> SttSpeech.parse(body)
            }
        }
    }

    /** Whether the node recognizes [language] now (a bridge from before languages answers for its one model). */
    suspend fun up(language: String): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(SttSpeech.statusUrl(config.baseUrl, language)).header("Authorization", "Bearer ${config.token}").build()
        http.newCall(req).execute().use { r ->
            r.isSuccessful && json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject["up"]?.jsonPrimitive?.booleanOrNull == true
        }
    }
}

/**
 * The node's recognizer for the app, in the learner's target language ([language]): whether it is up for that language
 * ([available], checked on connect, before use and when the pair changes) and whether the learner chose it over the
 * phone's ([accurate], remembered).
 */
class Stt(context: Context, private val scope: CoroutineScope, private val language: () -> Lang = { L10n.pair.target }) {
    private val prefs = context.getSharedPreferences("speech", Context.MODE_PRIVATE)
    private var api: SttApi? = null

    /** Null until checked. */
    var available by mutableStateOf<Boolean?>(null)
        private set

    /** "Natančneje · More accurate": record and let the node recognize, even when the phone could. */
    var accurate by mutableStateOf(prefs.getBoolean(KEY_ACCURATE, false))
        private set

    fun connect(config: BridgeConfig) {
        api = SttApi(config)
        refresh()
    }

    fun refresh() {
        val a = api ?: return
        val code = language().code
        scope.launch { available = runCatching { a.up(code) }.getOrDefault(false) }
    }

    fun choose(accurate: Boolean) {
        this.accurate = accurate
        prefs.edit().putBoolean(KEY_ACCURATE, accurate).apply()
    }

    /** Sends the recording and deletes it, whatever the outcome. */
    suspend fun transcribe(audio: File, expected: String?): SttResult {
        val a = api ?: throw SttUnavailable("not connected")
        try {
            return a.transcribe(audio, expected, language().code).also { available = true }
        } catch (e: SttUnavailable) {
            available = false
            throw e
        } finally {
            audio.delete()
        }
    }

    /**
     * A reading aloud of [text], heard twice (data/ReadAloud.kt): with the text as the prompt, then without. The second
     * pass is a second opinion: when it fails, the first comes alone (its second is null). Deletes the recording.
     */
    suspend fun transcribeTwice(audio: File, text: String): Pair<SttResult, SttResult?> {
        val a = api ?: throw SttUnavailable("not connected")
        try {
            val prompted = try {
                a.transcribe(audio, text, language().code).also { available = true }
            } catch (e: SttUnavailable) {
                available = false
                throw e
            }
            val plain = try {
                a.transcribe(audio, null, language().code)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null // busy or gone for a moment: the prompted pass alone
            }
            return prompted to plain
        } finally {
            audio.delete()
        }
    }

    private companion object {
        const val KEY_ACCURATE = "stt_accurate"
    }
}
