package si.lanisce.lani.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import si.lanisce.lani.audio.AudioFiles
import si.lanisce.lani.audio.AudioSettings
import si.lanisce.lani.audio.ClipFiles
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Where [Speaker.say] gets its audio from, in this order. */
enum class Route { FAMILY, CLIP, NODE, TTS }

/**
 * normalized text → voice → clip URL on the node. A voice is a speaker of the node's voice cast
 * (companion/voice-cast.json): the narrators "female" and "male", or a character ("grandma", "boy").
 */
typealias ClipIndex = Map<String, Map<String, String>>

/**
 * How someone's lines are played (the node's voice profiles, companion/bridge/src/profiles.ts): in [speaker] (their
 * own voice "@micka", else their archetype "grandma") at their own [pitch] and [rate] (1 with a voice of their own).
 */
data class VoiceProfile(val speaker: String, val archetype: String, val pitch: Float = 1f, val rate: Float = 1f, val status: String = "shared") {
    /** They speak with a voice of their own, made for them. */
    val own: Boolean get() = speaker.startsWith("@")
}

/** Someone the app tells the node about, so they get a voice profile: a villager of the cast or a resident. */
data class PersonVoice(val id: String, val name: String, val gender: String, val speaker: String, val stage: String? = null, val cast: Boolean = false, val order: Int? = null)

/** The node doesn't know this voice (HTTP 400: e.g. a character voice on an older bridge). */
class VoiceRefused(voice: String) : IOException("voice $voice refused")

/** HTTP for the node's voice store (companion/bridge/src/voice.ts), with the app token. */
class ClipsApi(private val config: BridgeConfig) {
    private val http = Http.client(connectSeconds = 3, readSeconds = 90)

    suspend fun index(): String = http.text(config.request("/voice/index").build())

    /** Asks the node to voice [text] now: the clip URL and its voice, or null when no engine is available (204). */
    suspend fun say(text: String, voice: String): Pair<String, String>? {
        val body = buildJsonObject { put("text", text); put("voice", voice) }.toString().toRequestBody(Http.jsonType)
        val (code, raw) = http.exchange(config.request("/voice/say").post(body).build())
        if (code == 204) return null
        if (code == 400) throw VoiceRefused(voice)
        if (code !in 200..299) throw IOException("HTTP $code")
        val o = json.parseToJsonElement(raw).jsonObject
        val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return null
        return url to (o["voice"]?.jsonPrimitive?.contentOrNull ?: voice)
    }

    /**
     * Asks the node to record [text] in [voice] again now (a long press on 🔊), once a clip a day within its daily cap.
     * An [IOException] when the node can't be reached. The client_id makes a retried request harmless.
     */
    suspend fun redo(text: String, voice: String): RedoAnswer {
        val body = buildJsonObject { put("text", text); put("voice", voice); put("client_id", UUID.randomUUID().toString()) }.toString().toRequestBody(Http.jsonType)
        val (_, raw) = http.exchange(config.request("/voice/redo").post(body).build())
        return RedoAnswer.parse(raw, voice, text)
    }

    /**
     * Asks the node for clips of [texts] (text and voice; at most 20) while getting ready for the road ("🚗 Za pot"'s
     * quiz): each the clip it has, or one voiced now but not as a live line (within its quota, never the reserve kept for
     * live lines; companion/bridge/src/features/voice.ts). Each URL in their order, null where none could be had; null for
     * all from a bridge without it (404).
     */
    suspend fun prepare(texts: List<Pair<String, String>>): List<String?>? {
        val body = buildJsonObject {
            put("texts", kotlinx.serialization.json.buildJsonArray {
                for ((t, v) in texts) add(buildJsonObject { put("text", t); put("voice", v) })
            })
        }.toString().toRequestBody(Http.jsonType)
        val (code, raw) = http.exchange(config.request("/voice/prepare").post(body).build())
        if (code == 404) return null
        if (code !in 200..299) throw IOException("HTTP $code")
        val clips = json.parseToJsonElement(raw).jsonObject["clips"] as? kotlinx.serialization.json.JsonArray ?: return texts.map { null }
        return texts.indices.map { i -> (clips.getOrNull(i) as? JsonObject)?.get("url")?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() } }
    }

    suspend fun download(url: String, to: File) = http.downloadTo(config.request(url).build(), to)

    /** Everyone's voice profile; null from an older bridge without them. */
    suspend fun profiles(people: List<PersonVoice>? = null): String? {
        val req = if (people == null) config.request("/voice/profiles").build() else {
            val body = buildJsonObject {
                put("people", kotlinx.serialization.json.buildJsonArray {
                    for (p in people) add(buildJsonObject {
                        put("id", p.id); put("name", p.name); put("gender", p.gender); put("speaker", p.speaker)
                        p.stage?.let { put("stage", it) }; put("cast", p.cast); p.order?.let { put("order", it) }; put("present", true)
                    })
                })
            }.toString().toRequestBody(Http.jsonType)
            config.request("/voice/profiles").post(body).build()
        }
        val (code, raw) = http.exchange(req)
        return if (code in 200..299) raw else null
    }
}

/**
 * Node voice clips: natural Slovene made once on the node (ElevenLabs or the local model) and kept here, in the app's files
 * (voice-clips/, which Android doesn't clear behind the learner's back): the clips played and the day's clips got ready on
 * Wi-Fi ([si.lanisce.lani.audio.Prefetch]), the least recently used dropped beyond the learner's cap
 * ([si.lanisce.lani.audio.AudioCap]; with it off, beyond [MAX_BYTES] as before). [Speaker.say] plays them when there is no
 * family recording.
 */
class Clips(context: Context, private val scope: CoroutineScope) {
    private val files = AudioFiles(context.filesDir)
    private val settings = AudioSettings(context)
    val dir: File = files.clips.also { moveOldCache(File(context.cacheDir, AudioFiles.CLIPS), it) }.apply { mkdirs() }
    private var api: ClipsApi? = null
    /** Texts the node could not voice lately (normalized text|first voice → when), so TTS speaks at once. */
    private val misses = ConcurrentHashMap<String, Long>()

    /** Compose state: written on the main thread only. Starts empty; the disk copy loads in the background. */
    var index by mutableStateOf<ClipIndex>(emptyMap())
        private set

    /** Everyone's voice profile by person id (the node's), so each person always sounds like themselves. */
    var profiles by mutableStateOf<Map<String, VoiceProfile>>(emptyMap())
        private set
    /** The people last told to the node, so an unchanged list isn't sent again. */
    private var told: List<PersonVoice>? = null
    /** The node's index arrived, so the (older) disk copy must not overwrite it. */
    private var fresh = false

    init {
        scope.launch {
            val cached = withContext(Dispatchers.IO) { loadIndex() }
            if (!fresh) index = cached + index // phrases voiced meanwhile stay
        }
    }

    /** The node has a clip for [text] (any voice), which also proves the text is Slovene. */
    fun has(text: String): Boolean =
        index[Voice.normalize(text)]?.isNotEmpty() == true || resolve(index, text, FEMALE) != null || resolve(index, text, MALE) != null

    /** The node has a voice store with clips, so buttons can speak even without a TTS voice. */
    val enabled: Boolean get() = index.isNotEmpty()
    val online: Boolean get() = api != null

    fun connect(config: BridgeConfig) {
        api = ClipsApi(config)
    }

    /** [person]'s voice profile, when the node has one. */
    fun profile(person: String?): VoiceProfile? = person?.let { profiles[it] }

    /**
     * Tells the node who lives in the village (the cast among them and the residents), so everyone has a voice
     * profile (and the cast a voice of their own when there's room); takes back everyone's profile.
     */
    fun syncProfiles(people: List<PersonVoice>) {
        val a = api ?: return
        if (people == told) return
        scope.launch {
            val raw = withContext(Dispatchers.IO) { runCatching { a.profiles(people) }.getOrNull() } ?: return@launch
            told = people
            runCatching { parseProfiles(raw) }.getOrNull()?.let { profiles = it }
        }
    }

    /** Reloads the index from the node (on start and when content was published or voiced). */
    fun refresh() {
        val a = api ?: return
        scope.launch {
            val raw = withContext(Dispatchers.IO) { runCatching { a.profiles() }.getOrNull() } ?: return@launch
            runCatching { parseProfiles(raw) }.getOrNull()?.let { profiles = it }
        }
        scope.launch {
            // A bad index must not crash the app: parse inside the catch, write the file off the main thread.
            val (raw, parsed) = withContext(Dispatchers.IO) { runCatching { a.index().let { it to parseIndex(it) } }.getOrNull() } ?: return@launch
            index = parsed
            fresh = true
            misses.clear()
            withContext(Dispatchers.IO) { runCatching { File(dir, INDEX).writeText(raw) } }
        }
    }

    private fun loadIndex(): ClipIndex = runCatching { parseIndex(File(dir, INDEX).readText()) }.getOrDefault(emptyMap())

    fun file(url: String) = File(dir, url.substringAfterLast('/'))

    /**
     * The clips for [text] in the first of [voices] that has them (see [chain]), when all are on the
     * phone already (marked as used, for the LRU).
     */
    fun onDevice(text: String, voices: List<String>): List<File>? {
        val files = resolveFirst(index, text, voices)?.second?.map { file(it) }?.takeIf { fs -> fs.all { it.exists() } } ?: return null
        files.forEach { it.setLastModified(System.currentTimeMillis()) }
        return files
    }

    fun missedRecently(text: String, voices: List<String>): Boolean {
        val at = misses[missKey(text, voices.first())] ?: return false
        return System.currentTimeMillis() - at < MISS_MS
    }

    /**
     * Gets the clips for [text] in the first of [voices] (downloading them, or asking the node to voice the
     * phrases it hasn't got in it; another of [voices] only when the node can't, see [pick]) and hands them to
     * [then] within [waitMs], or null so the caller speaks otherwise now. The work goes on in the background
     * either way, so the clip is there next time.
     */
    fun prepare(text: String, voices: List<String>, waitMs: Long = WAIT_MS, then: (List<File>?) -> Unit) {
        val work = fetchOnce(text, voices)
        scope.launch { then(withTimeoutOrNull(waitMs) { work.await() }) }
    }

    /**
     * Gets [text]'s clips in the first of [voices] onto the phone ahead, without playing them (a timed run's sounds, before
     * its clock starts): true once they are here, false when the node can't make them now (the line is spoken otherwise).
     */
    suspend fun load(text: String, voices: List<String>): Boolean {
        if (onDevice(text, voices.take(1)) != null) return true
        if (!online || missedRecently(text, voices)) return false
        return fetchOnce(text, voices).await() != null
    }

    /** The clips of a line on their way (downloading, or being voiced by the node): a second ask waits for the first. */
    private val fetching = HashMap<String, Deferred<List<File>?>>()

    private fun fetchOnce(text: String, voices: List<String>): Deferred<List<File>?> = synchronized(fetching) {
        val key = missKey(text, voices.first())
        fetching[key]?.takeIf { it.isActive } ?: scope.async(Dispatchers.IO) { runCatching { fetch(text, voices) }.getOrNull() }.also { d ->
            fetching[key] = d
            d.invokeOnCompletion { synchronized(fetching) { if (fetching[key] === d) fetching.remove(key) } }
        }
    }

    private suspend fun fetch(text: String, voices: List<String>): List<File>? {
        val a = api ?: return null
        val urls = resolve(index, text, voices.first()) ?: run {
            val got = mutableListOf<String>()
            for (p in parts(text)) {
                val r = pick(index, Voice.normalize(p), voices) { ask(a, p, voices) }
                if (r == null) {
                    misses[missKey(text, voices.first())] = System.currentTimeMillis()
                    return null
                }
                val key = Voice.normalize(p)
                withContext(Dispatchers.Main) { index = index + (key to (index[key].orEmpty() + (r.second to r.first))) }
                got += r.first
            }
            got
        }
        val got = urls.map { u ->
            val f = file(u)
            // the car's library may have it (one file, linked); one on its way for the car or the prefetch is waited for
            if (!ClipFiles.get(f, listOf(File(files.roadClips, f.name))) { to -> a.download(u, to) }) throw IOException("no clip")
            f.setLastModified(System.currentTimeMillis())
            f
        }
        trim()
        return got
    }

    /**
     * Asks the node to voice [phrase] in the first of [voices] it knows: a voice it refuses (an older
     * bridge has only female and male) tries the next. Null when no engine can make it now.
     */
    private suspend fun ask(a: ClipsApi, phrase: String, voices: List<String>): Pair<String, String>? {
        for (v in voices) {
            try {
                return a.say(phrase, v)
            } catch (_: VoiceRefused) {
                continue
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    /** The index and the cached files, as a re-record changes them (the index on the main thread, the files off it). */
    private val cache = object : ClipCache {
        override val index: ClipIndex get() = this@Clips.index

        override fun put(key: String, voice: String, url: String) {
            this@Clips.index = this@Clips.index + (key to (this@Clips.index[key].orEmpty() + (voice to url)))
        }

        override suspend fun drop(url: String) {
            withContext(Dispatchers.IO) { file(url).delete() }
        }

        override suspend fun fetch(url: String): File = withContext(Dispatchers.IO) {
            val f = file(url)
            val a = api ?: throw IOException("not connected")
            if (!ClipFiles.get(f, emptyList()) { to -> a.download(url, to) }) throw IOException("no clip")
            f.setLastModified(System.currentTimeMillis())
            f
        }
    }

    /**
     * A long press on 🔊: [text] recorded again on the node in the voice the phone plays it in (the first of [voices] with
     * a clip, else the first), the old clip dropped from the phone, the new one in the index and downloaded ([ReRecorder]);
     * [then] gets what came of it, on the main thread. Without a node (not paired, or on a visit) it says so at once:
     * nothing is queued.
     */
    fun reRecord(text: String, voices: List<String>, then: (ReRecord) -> Unit) {
        val a = api ?: return then(ReRecord.Offline)
        scope.launch {
            val r = try {
                ReRecorder(cache) { p, v -> a.redo(p, v) }.run(text, voices)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ReRecord.Failed
            }
            if (r is ReRecord.Done) {
                voices.firstOrNull()?.let { misses.remove(missKey(text, it)) }
                withContext(Dispatchers.IO) { trim() }
            }
            then(r)
        }
    }

    /**
     * Drops the least recently used clips beyond the learner's cap, with the car's library counted in it (never the day's
     * clips: [AudioFiles.trim]); with the cap off, beyond [MAX_BYTES] as before.
     */
    private fun trim() {
        runCatching { files.trim(settings.cap) }
    }

    companion object {
        const val FEMALE = "female"
        const val MALE = "male"

        /**
         * The clips were in the app's cache (cache/voice-clips) before the cap: moved once into the files (one rename on the
         * same file system), so nothing is downloaded again.
         */
        private fun moveOldCache(old: File, to: File) {
            if (!old.isDirectory) return
            if (!to.exists() && old.renameTo(to)) return
            old.deleteRecursively()
        }
        /**
         * How long a line waits for the node to voice it (ElevenLabs takes a few seconds for a new line) before
         * the phone's TTS speaks instead; a node that can't (no engine, an error) answers at once.
         */
        const val WAIT_MS = 8_000L
        const val MAX_BYTES = 50L * 1024 * 1024
        private const val MISS_MS = 10 * 60_000L
        /** The index's copy on the phone (the prefetch writes it too, with what the node voiced for it). */
        const val INDEX = "index.json"

        private fun missKey(text: String, voice: String) = "${Voice.normalize(text)}|$voice"

        /** {"people": {"micka": {"speaker": "@micka", "archetype": "grandma", "pitch": 1, "rate": 1, "status": "own"}}} */
        fun parseProfiles(raw: String): Map<String, VoiceProfile> {
            val people = json.parseToJsonElement(raw).jsonObject["people"] as? JsonObject ?: return emptyMap()
            return people.mapNotNull { (id, v) ->
                val o = v as? JsonObject ?: return@mapNotNull null
                val speaker = o["speaker"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                id to VoiceProfile(
                    speaker = speaker,
                    archetype = o["archetype"]?.jsonPrimitive?.contentOrNull ?: speaker,
                    pitch = o["pitch"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.coerceIn(0.8f, 1.2f) ?: 1f,
                    rate = o["rate"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.coerceIn(0.8f, 1.2f) ?: 1f,
                    status = o["status"]?.jsonPrimitive?.contentOrNull ?: "shared",
                )
            }.toMap()
        }

        fun parseIndex(raw: String): ClipIndex =
            (json.parseToJsonElement(raw) as JsonObject).mapValues { (_, v) ->
                (v as? JsonObject)?.mapNotNull { (voice, url) -> url.jsonPrimitive.contentOrNull?.let { voice to it } }?.toMap().orEmpty()
            }.filterValues { it.isNotEmpty() }

        /** The whole text when it is one phrase, else its phrases ("Kako ste? / Kako si?"). */
        fun parts(text: String): List<String> = Voice.phrases(text).takeIf { it.size > 1 } ?: listOf(text)

        /**
         * The voices to try for a line, best first: [voice] (a speaker of the voice cast, "grandma"), then
         * [fallback] (their gender voice), then the female narrator. Plain texts are just female.
         */
        fun chain(voice: String = FEMALE, fallback: String? = null): List<String> = listOfNotNull(voice, fallback, FEMALE).distinct()

        /**
         * The clip for one phrase ([key], normalized) as (url, voice): the node's in the first of [voices], else
         * the one [ask] gets it to voice now (it may answer in another voice when it can't make that one), else,
         * when it can't make any, a clip the node has in another of [voices]. So an old narrator clip never
         * stands in for a villager's voice while the villager's can be had.
         */
        inline fun pick(index: ClipIndex, key: String, voices: List<String>, ask: () -> Pair<String, String>?): Pair<String, String>? {
            val first = voices.firstOrNull() ?: return null
            index[key]?.get(first)?.let { return it to first }
            ask()?.let { return it }
            return voices.drop(1).firstNotNullOfOrNull { v -> index[key]?.get(v)?.let { it to v } }
        }

        /** The first of [voices] with clips for [text], and those clip URLs. */
        fun resolveFirst(index: ClipIndex, text: String, voices: List<String>): Pair<String, List<String>>? =
            voices.firstNotNullOfOrNull { v -> resolve(index, text, v)?.let { v to it } }

        /** Clip URLs for [text] in [voice]: one for the whole text, or one per phrase when all have one. */
        fun resolve(index: ClipIndex, text: String, voice: String): List<String>? {
            index[Voice.normalize(text)]?.get(voice)?.let { return listOf(it) }
            val ps = Voice.phrases(text)
            if (ps.size < 2) return null
            return ps.map { index[Voice.normalize(it)]?.get(voice) ?: return null }
        }

        /**
         * The lookup order: a family recording on the phone, then a node clip on the phone, then the
         * node (download or voice it now, unless it just failed), then TTS.
         */
        fun route(family: Boolean, clip: Boolean, online: Boolean, missedRecently: Boolean): Route = when {
            family -> Route.FAMILY
            clip -> Route.CLIP
            online && !missedRecently -> Route.NODE
            else -> Route.TTS
        }

        /** Names of the least recently used files to delete so the rest fit in [max] bytes. */
        fun evict(files: List<Triple<String, Long, Long>>, max: Long): Set<String> {
            var total = files.sumOf { it.second }
            val out = mutableSetOf<String>()
            for ((name, size, _) in files.sortedBy { it.third }) {
                if (total <= max) break
                out += name
                total -= size
            }
            return out
        }
    }
}
