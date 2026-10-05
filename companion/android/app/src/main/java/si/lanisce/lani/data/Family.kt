package si.lanisce.lani.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import si.lanisce.lani.l10n.bi

// --- partner challenges (companion/bridge/src/family.ts) ------------------------------------

@Serializable
data class FamilyAnswer(val text: String, val spoken: Boolean = false, val at: String = "")

@Serializable
data class FamilyFeedback(
    val text: String,
    @SerialName("partner_note") val partnerNote: String? = null,
    val score: Double? = null,
    val at: String = "",
)

/** A message from the family: question | translate | say. */
@Serializable
data class FamilyChallenge(
    val id: String,
    val from: String,
    @SerialName("from_sl") val fromSl: String? = null,
    val emoji: String = "💌",
    val type: String = "question",
    val text: String,
    val hint: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    val answer: FamilyAnswer? = null,
    val feedback: FamilyFeedback? = null,
) {
    /** "💌 Od Maje · From Maja" */
    val title: String get() = "$emoji ${Family.fromLabel(from, fromSl)}"
}

@Serializable
data class FamilySettings(
    @SerialName("partner_name") val partnerName: String = "",
    @SerialName("partner_emoji") val partnerEmoji: String = "💌",
    @SerialName("learner_name") val learnerName: String = "",
) {
    /** The learner's name as the family page has it, else as the content does (l10n/Learner.kt). */
    val learner: String get() = learnerName.ifBlank { si.lanisce.lani.l10n.Learner.current.name }
}

/** Something worth recording (GET /family/api/words): pack words, examples, review cards. */
@Serializable
data class FamilyWord(val text: String, val en: String? = null, val source: String = "", val recorded: Int = 0)

object Family {
    /** Answering a family challenge pays wisdom like this many correct answers. */
    const val REWARD_ANSWERS = 3

    /**
     * Slovene "from" form of a first name: Maja → Maje, Marko → Marka, Jan → Jana, Pavel → Pavla (l10n/Learner.kt declines
     * it; a name in -e, -i or -u stays as it is: whose it is, Tone's or Beti's, the name alone doesn't say).
     */
    fun genitive(name: String): String {
        val n = name.trim()
        if (n.isEmpty() || n.last().lowercaseChar() in "eiu") return n
        return si.lanisce.lani.l10n.Learner.slovene(n, female = n.endsWith("a", true)).getValue("gen")
    }

    /** "Od Maje · From Maja"; [fromSl] is the family's own spelling of "Maje", if they gave one. */
    fun fromLabel(from: String, fromSl: String? = null): String {
        val sl = fromSl?.takeIf { it.isNotBlank() } ?: if (from.isBlank()) "družine" else genitive(from)
        return bi("family.from", "sl" to sl, "name" to from, "family" to if (from.isBlank()) "yes" else "no")
    }

    /** What Jan is asked to do, by challenge type. */
    fun instruction(type: String): String = when (type) {
        "translate" -> "🔁 ${bi("family.translate")}"
        "say" -> "🗣️ ${bi("family.sayOutLoud")}"
        else -> "❓ ${bi("family.answerSlovene")}"
    }

    fun parseChallenges(raw: String): List<FamilyChallenge> = json.decodeFromString(raw)
}

/** HTTP for the family routes, with the app token. */
class FamilyApi(private val config: BridgeConfig) {
    private val http = Http.client(connectSeconds = 10, readSeconds = 30)

    private suspend fun get(path: String) = http.text(config.request(path).build())

    suspend fun challenges() = Family.parseChallenges(get("/family/api/challenges"))
    suspend fun settings(): FamilySettings = json.decodeFromString(get("/family/api/settings"))
    suspend fun words(): List<FamilyWord> = json.decodeFromString(get("/family/api/words"))
    suspend fun index(): String = get("/audio/index")

    suspend fun answer(id: String, text: String, spoken: Boolean): FamilyChallenge {
        val body = buildJsonObject { put("text", text); put("spoken", spoken) }.toString().toRequestBody(Http.jsonType)
        return json.decodeFromString(http.text(config.request("/family/api/challenges/$id/answer").post(body).build()))
    }

    suspend fun upload(text: String, speaker: String, file: File) {
        val url = (config.baseUrl.trimEnd('/') + "/audio").toHttpUrl().newBuilder()
            .addQueryParameter("text", text).addQueryParameter("speaker", speaker).build()
        http.text(config.request(url.toString()).post(file.asRequestBody("audio/mp4".toMediaType())).build())
    }

    suspend fun delete(file: String) {
        http.text(config.request("/audio/file/$file").delete().build())
    }

    suspend fun download(file: String, to: File) = http.downloadTo(config.request("/audio/file/$file").build(), to)
}

/** Family state for the app: challenges, the voice index and its audio cache, the recording list. */
class FamilyHub(context: Context, private val scope: CoroutineScope) {
    val voice = Voice(context).also { v -> v.onMissing = { prefetch() } }
    private val prefs = context.getSharedPreferences("family", Context.MODE_PRIVATE)
    private var api: FamilyApi? = null
    private val fetching = Mutex()

    var challenges by mutableStateOf<List<FamilyChallenge>>(emptyList())
        private set
    var settings by mutableStateOf(FamilySettings())
        private set
    var words by mutableStateOf<List<FamilyWord>?>(null)
        private set
    /** Who is recording on this phone, e.g. "👵 Micka". */
    var recordAs by mutableStateOf(prefs.getString("record_as", "").orEmpty())
        private set

    /** Challenges Jan hasn't answered yet. */
    val open: List<FamilyChallenge> get() = challenges.filter { it.answer == null }

    init {
        scope.launch { voice.loadCached() }
    }

    fun connect(config: BridgeConfig) {
        api = FamilyApi(config)
    }

    /** Reloads challenges, settings and the voice index; quietly skips what an older bridge lacks. */
    fun refresh() {
        val a = api ?: return
        scope.launch {
            // Parsing inside the catch: a malformed index is skipped instead of crashing the app.
            runCatching { voice.updateIndex(a.index()) }.onSuccess { prefetch() }
            runCatching { a.challenges() }.onSuccess { challenges = it }
            runCatching { a.settings() }.onSuccess { settings = it }
        }
    }

    fun challenge(id: String) = challenges.firstOrNull { it.id == id }

    suspend fun answer(id: String, text: String, spoken: Boolean) {
        val a = api ?: throw IOException("not connected")
        val c = a.answer(id, text, spoken)
        challenges = challenges.map { if (it.id == c.id) c else it }
    }

    suspend fun loadWords() {
        val a = api ?: return
        words = a.words()
        runCatching { voice.updateIndex(a.index()) }
    }

    fun saveRecordAs(name: String) {
        recordAs = name
        prefs.edit().putString("record_as", name).apply()
    }

    /** Saves a take on the node and makes it playable here at once. */
    suspend fun upload(text: String, take: File) {
        val a = api ?: throw IOException("not connected")
        a.upload(text, recordAs.ifBlank { "Družina" }, take)
        voice.updateIndex(a.index())
        prefetch()
    }

    suspend fun delete(r: Recording) {
        val a = api ?: throw IOException("not connected")
        a.delete(r.file)
        withContext(Dispatchers.IO) { voice.file(r).delete() }
        voice.updateIndex(a.index())
    }

    /** Plays a stored recording, downloading it first if needed. */
    suspend fun play(r: Recording) {
        val f = voice.file(r)
        if (!f.exists()) api?.download(r.file, f)
        voice.playFile(f, r)
    }

    /** Downloads recordings missing on the device and drops ones no longer in the index. */
    private fun prefetch() {
        val a = api ?: return
        scope.launch(Dispatchers.IO) {
            if (!fetching.tryLock()) return@launch
            try {
                val wanted = voice.index.values.flatten().map { it.file }.toSet()
                voice.dir.listFiles()?.filter { it.name != "index.json" && it.name !in wanted }?.forEach { it.delete() }
                for (f in wanted) {
                    val target = File(voice.dir, f)
                    if (!target.exists()) runCatching { a.download(f, target) }
                }
            } finally {
                fetching.unlock()
            }
        }
    }
}

/** Records one take as AAC in an .m4a file (max [MAX_MS]). */
class FamilyRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    /** Keeps the background sounds quiet while it records. */
    private var mic: MicOpen.Hold? = null
    val take: File = File(context.cacheDir, "family-take.m4a")

    /** Starts recording; [onLimit] runs when the maximum length stops it. */
    fun start(onLimit: () -> Unit) {
        release()
        take.delete()
        val hold = MicOpen.hold()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setMaxDuration(MAX_MS)
            r.setOutputFile(take.path)
            r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit() }
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            hold.release()
            throw e
        }
        recorder = r
        mic = hold
    }

    /** Stops; the take, or null when nothing usable was recorded. */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        mic?.release()
        mic = null
        return take.takeIf { ok && it.length() > 0 }
    }

    fun release() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        mic?.release()
        mic = null
    }

    companion object {
        const val MAX_MS = 15_000
    }
}
