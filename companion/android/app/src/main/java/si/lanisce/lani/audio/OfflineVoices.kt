package si.lanisce.lani.audio

import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.RequestBody.Companion.toRequestBody
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.data.Http
import si.lanisce.lani.data.downloadTo
import si.lanisce.lani.data.exchange
import si.lanisce.lani.data.json
import si.lanisce.lani.data.request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** One file of an offline voice: where it goes in the voice's folder, its size and SHA-256. */
@Serializable
data class OfflineVoiceFile(val path: String, val bytes: Long, val sha256: String)

/** How the voice is spoken (its Piper config's `inference`): sherpa-onnx takes them as they are. */
@Serializable
data class OfflineVoiceInference(
    @SerialName("noise_scale") val noiseScale: Float = 0.667f,
    @SerialName("length_scale") val lengthScale: Float = 1f,
    @SerialName("noise_w") val noiseW: Float = 0.8f,
)

/** Bytes [done] of [total] while the node fetches the voice. */
@Serializable
data class OfflineVoiceProgress(val done: Long = 0, val total: Long = 0)

/**
 * An offline voice as the node offers it (GET /voice/offline, companion/bridge/src/offline-voice.ts): a Piper voice of the
 * target [language] (Slovene: "Artur", sl_SI-artur-medium), its size, whether the node has it ([status]: "missing",
 * "fetching", "ready", "failed"), its licence and, once ready, its [files] with their SHA-256.
 */
@Serializable
data class OfflineVoiceInfo(
    val language: String,
    val id: String,
    val name: String = "",
    val quality: String = "",
    val bytes: Long = 0,
    val status: String = "missing",
    val progress: OfflineVoiceProgress? = null,
    val error: String? = null,
    val licence: String? = null,
    val attribution: String? = null,
    val source: String? = null,
    val files: List<OfflineVoiceFile> = emptyList(),
    val inference: OfflineVoiceInference? = null,
    @SerialName("sample_rate") val sampleRate: Int? = null,
    val espeak: String? = null,
) {
    val ready: Boolean get() = status == "ready" && files.isNotEmpty()
}

/** The node's offline voices (companion/bridge/src/features/offline-voice.ts), with the app token. */
class OfflineVoiceApi(private val config: BridgeConfig) {
    private val http = Http.client(connectSeconds = 5, readSeconds = 120)

    /** Every voice the node offers; null from an older bridge without them (404). */
    suspend fun list(): List<OfflineVoiceInfo>? {
        val (code, raw) = http.exchange(config.request("/voice/offline").build())
        if (code == 404) return null
        if (code !in 200..299) throw IOException("HTTP $code")
        return parseList(raw)
    }

    /** Asks the node to get the voice of [language] (from Hugging Face, once): it answers where it is. */
    suspend fun prepare(language: String): OfflineVoiceInfo {
        val (code, raw) = http.exchange(config.request("/voice/offline/$language").post(ByteArray(0).toRequestBody(null)).build())
        if (code == 404) throw IOException("no offline voice for $language")
        if (code !in 200..299 && code != 503) throw IOException("HTTP $code")
        return json.decodeFromString(OfflineVoiceInfo.serializer(), raw)
    }

    /** Where the node is with the voice of [language] (null: it has none for it, or is older). */
    suspend fun one(language: String): OfflineVoiceInfo? {
        val (code, raw) = http.exchange(config.request("/voice/offline/$language").build())
        if (code == 404) return null
        if (code !in 200..299) throw IOException("HTTP $code")
        return json.decodeFromString(OfflineVoiceInfo.serializer(), raw)
    }

    /** A file of the voice, [f] (`?sha256=`: the URL names its content, so no cache on the way serves an older one). */
    suspend fun download(language: String, f: OfflineVoiceFile, to: File) =
        http.downloadTo(config.request("/voice/offline/$language/${f.path}?sha256=${f.sha256}").build(), to)

    companion object {
        fun parseList(raw: String): List<OfflineVoiceInfo> =
            (json.parseToJsonElement(raw).jsonObject["voices"]?.jsonArray).orEmpty()
                .mapNotNull { e -> runCatching { json.decodeFromJsonElement(OfflineVoiceInfo.serializer(), e as JsonObject) }.getOrNull() }
    }
}

/**
 * The offline voices on the phone (filesDir/piper/<language>/: model.onnx, tokens.txt, espeak-ng-data/, MODEL_CARD, and
 * voice.json, the node's description of it, written last): got through the node, each file checked against its SHA-256,
 * into <language>.part/ and moved into place only once all are there. A download stopped halfway goes on where it stopped.
 */
class OfflineVoices(filesDir: File) {
    val dir = File(filesDir, "piper")

    /** The voice of [language] on the phone, whole (every file of its voice.json there at its size), else null. */
    fun installed(language: String): OfflineVoiceInfo? {
        val home = File(dir, language)
        val info = runCatching { json.decodeFromString(OfflineVoiceInfo.serializer(), File(home, VOICE_JSON).readText()) }.getOrNull() ?: return null
        return info.takeIf { i -> i.files.isNotEmpty() && i.files.all { File(home, it.path).length() == it.bytes } }
    }

    fun folder(language: String) = File(dir, language)

    /** What the voice of [language] takes on the phone (0 when none). */
    fun bytes(language: String): Long = folder(language).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** The voice of [language] off the phone. */
    fun remove(language: String) {
        File(dir, "$language.part").deleteRecursively()
        folder(language).deleteRecursively()
    }

    /**
     * Gets the voice of [language] onto the phone through the node ([api]): it asks the node to have it ready (fetched from
     * Hugging Face once, which takes a minute or so: polled every [pollMs]), then downloads each file and checks its size
     * and SHA-256 ([check]); a file that doesn't match is deleted and the install fails (the next try fetches it again). The
     * files already checked stay. [progress] gets the bytes done of all. Returns the voice; at once when it's there already.
     */
    suspend fun install(
        language: String,
        api: OfflineVoiceApi,
        pollMs: Long = 2_000,
        maxWaitMs: Long = 15 * 60_000L,
        progress: suspend (Long, Long) -> Unit = { _, _ -> },
    ): OfflineVoiceInfo {
        installed(language)?.let { return it }
        var info = api.prepare(language)
        var waited = 0L
        while (!info.ready) {
            if (info.status == "failed") throw IOException("the node couldn't get the voice: ${info.error ?: "failed"}")
            if (waited >= maxWaitMs) throw IOException("the node is still getting the voice")
            info.progress?.let { progress(it.done, it.total) } // the node fetching it first
            delay(pollMs)
            waited += pollMs
            info = api.one(language) ?: throw IOException("no offline voice for $language")
        }
        return install(info, progress) { f, to -> api.download(language, f, to) }
    }

    /**
     * The files of [info] into <language>.part/ ([fetch] downloads one), each checked, then voice.json, then the folder moved
     * into place (the old one gone): the voice is there whole or not at all.
     */
    suspend fun install(
        info: OfflineVoiceInfo,
        progress: suspend (Long, Long) -> Unit = { _, _ -> },
        fetch: suspend (OfflineVoiceFile, File) -> Unit,
    ): OfflineVoiceInfo {
        val part = File(dir, "${info.language}.part")
        val total = info.files.sumOf { it.bytes }
        var done = 0L
        for (f in info.files) {
            val to = safe(part, f.path) ?: throw IOException("bad path ${f.path}")
            done += f.bytes
            if (to.length() == f.bytes && check(to, f.bytes, f.sha256)) continue
            to.parentFile?.mkdirs()
            to.delete()
            fetch(f, to)
            if (!check(to, f.bytes, f.sha256)) {
                to.delete()
                throw IOException("${f.path}: the checksum doesn't match")
            }
            progress(done, total)
        }
        File(part, VOICE_JSON).writeText(json.encodeToString(OfflineVoiceInfo.serializer(), info))
        val home = folder(info.language)
        home.deleteRecursively()
        if (!part.renameTo(home)) throw IOException("couldn't move the voice into place")
        return info
    }

    companion object {
        const val VOICE_JSON = "voice.json"

        /** [file] is [bytes] long and its SHA-256 is [sha256] (hex). */
        fun check(file: File, bytes: Long, sha256: String): Boolean {
            if (!file.isFile || file.length() != bytes) return false
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }.equals(sha256, ignoreCase = true)
        }

        /** [path] (the node's: "espeak-ng-data/lang/zls/sl") inside [root], or null when it would lead out of it. */
        fun safe(root: File, path: String): File? {
            if (path.isBlank() || path.startsWith("/") || path.split('/').any { it == ".." || it.isEmpty() }) return null
            val f = File(root, path)
            return f.takeIf { it.canonicalPath.startsWith(root.canonicalPath + File.separator) }
        }
    }
}

/**
 * Gets the offline voice of the target language onto the phone in the background ([OfflineVoices.install]), when the learner
 * asks (the settings, the offer after pairing): on any network, though Wi-Fi is recommended (it is about 65 MB). Its
 * progress (bytes done of all) for the settings; a failure's reason in its output.
 */
class OfflineVoiceWorker(context: android.content.Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        si.lanisce.lani.l10n.LangSetting(app).apply()
        val config = si.lanisce.lani.data.Prefs(app).config() ?: return Result.failure(androidx.work.workDataOf(ERROR to "not paired"))
        val language = inputData.getString(LANGUAGE) ?: si.lanisce.lani.l10n.L10n.ownPair.target.code
        return try {
            OfflineVoices(app.filesDir).install(language, OfflineVoiceApi(config)) { done, total ->
                setProgress(androidx.work.workDataOf(DONE to done, TOTAL to total))
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("OfflineVoice", "not got", e)
            Result.failure(androidx.work.workDataOf(ERROR to (e.message ?: e.javaClass.simpleName)))
        }
    }

    companion object {
        const val NAME = "lani-offline-voice"
        const val LANGUAGE = "language"
        const val DONE = "done"
        const val TOTAL = "total"
        const val ERROR = "error"

        fun start(context: android.content.Context, language: String) {
            val req = androidx.work.OneTimeWorkRequestBuilder<OfflineVoiceWorker>()
                .setConstraints(androidx.work.Constraints(requiredNetworkType = androidx.work.NetworkType.CONNECTED, requiresStorageNotLow = true))
                .setInputData(androidx.work.workDataOf(LANGUAGE to language))
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(NAME, androidx.work.ExistingWorkPolicy.KEEP, req)
        }
    }
}
