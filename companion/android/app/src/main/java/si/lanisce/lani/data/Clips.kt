package si.lanisce.lani.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
import si.lanisce.lani.audio.DayAudio
import si.lanisce.lani.audio.JoinPart
import si.lanisce.lani.audio.LineJoin
import si.lanisce.lani.audio.PrefetchWorker
import si.lanisce.lani.audio.PreviousClips
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

/**
 * A text the app asks the node to voice ahead ([ClipsApi.prepare]): in [voice]; with [lang], an explanation in that
 * language (a unit's intro step: [IntroSpeech.key]), else a line in the language learned.
 */
data class ClipAsk(val text: String, val voice: String, val lang: String? = null)

/** The node doesn't know this voice (HTTP 400: e.g. a character voice on an older bridge). */
class VoiceRefused(voice: String) : IOException("voice $voice refused")

/**
 * Where a voice store answers the app (companion/bridge/src/features/voice.ts): the node's own ([OWN]: /voice/…), or on a
 * visit the visited town's, through the node ([visit]: /towns/<id>/voice/…, companion/bridge/src/features/visit-voices.ts:
 * the host's own clips, else the node voices the line in the host's cast voice). A visit has no re-record nor a prepare
 * ([redo], [prepare] null: the node's prepare voices what it hasn't); [ahead]: a visit's first lines got ahead, the town's
 * own clips only, never voiced for it (null at home).
 */
data class ClipRoutes(val index: String, val say: String, val profiles: String, val redo: String?, val prepare: String?, val ahead: String? = null, val queue: String? = null) {
    companion object {
        val OWN = ClipRoutes("/voice/index", "/voice/say", "/voice/profiles", "/voice/redo", "/voice/prepare", queue = "/voice/queue")

        /** The visited town [town]'s voices, through the node. */
        fun visit(town: String) = ClipRoutes("/towns/$town/voice/index", "/towns/$town/voice/say", "/towns/$town/voice/profiles", null, null, "/towns/$town/voice/prepare")
    }
}

/**
 * HTTP for the node's voice store (companion/bridge/src/voice.ts), with the app token; on a visit the town's
 * ([ClipRoutes.visit]). [connection] answers what is the learner's own: their voice profiles, a re-record, a visit's routes.
 * The routes that make or fetch clips ask [plan] at every request ([AudioPlan]: the connection, or while it lasts the audio
 * trial), so everything that builds one of these follows it: the app's [Clips], the day's prefetch, the car's library.
 */
class ClipsApi(val connection: BridgeConfig, private val routes: ClipRoutes = ClipRoutes.OWN, private val plan: VoicePlan = AudioPlan) {
    /** A voice route's 403 trial_ended ends the trial at once ([AudioTrial.ended]); the caller gets the failure as any other. */
    private val http = Http.client(connectSeconds = 3, readSeconds = 90).newBuilder().addInterceptor { chain ->
        chain.proceed(chain.request()).also { r ->
            if (r.code == 403 && planned && AudioTrial.endedBy(r.peekBody(2048).string())) plan.ended()
        }
    }.build()

    /** Who answers index, say, prepare and a clip's file for this request. */
    val voices: BridgeConfig get() = if (routes == ClipRoutes.OWN) plan.voices(connection) else connection

    /** Someone else than the connection answers them now: it knows no "player" ([Clips.ask]). */
    val planned: Boolean get() = voices != connection

    /** The index came from the connection, and the clips the phone got elsewhere stay ([PreviousClips.heard]). */
    val keepsHeard: Boolean get() = routes == ClipRoutes.OWN && plan.keepsHeard(connection)

    suspend fun index(): String = http.text(voices.request(routes.index).build())

    /**
     * The index asked with the ETag of the one the phone has ([etag]): null when it is the same (a bridge with ETags
     * answers 304, nothing sent), else its text and its own ETag (null from a bridge without them). An [IOException]
     * for another status or when the node can't be reached. [from]: who is asked ([voices]).
     */
    suspend fun indexIfChanged(etag: String?, from: BridgeConfig = voices): Pair<String, String?>? {
        val a = http.raw(from.request(routes.index).apply { etag?.let { header("If-None-Match", it) } }.build())
        if (a.code == 304 && etag != null) return null
        if (a.code !in 200..299) throw IOException("HTTP ${a.code}: ${a.text.take(200)}")
        return a.text to a.etag
    }

    /**
     * Asks the node to voice [text] now: the clip URL and its voice, or null when no engine is available (204). With
     * [lang], an explanation in the learner's base language (the intro's, voiced in one go; never the reserve kept for
     * live lines): the store keys it apart ([IntroSpeech.key]).
     */
    suspend fun say(text: String, voice: String, lang: String? = null): Pair<String, String>? {
        val body = sayBody(text, voice, lang).toString().toRequestBody(Http.jsonType)
        val (code, raw) = http.exchange(voices.request(routes.say).post(body).build())
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
        val path = routes.redo ?: throw IOException("no re-record on a visit")
        val body = buildJsonObject { put("text", text); put("voice", voice); put("client_id", UUID.randomUUID().toString()) }.toString().toRequestBody(Http.jsonType)
        val (_, raw) = http.exchange(connection.request(path).post(body).build())
        return RedoAnswer.parse(raw, voice, text)
    }

    /**
     * Asks the node for clips of [texts] (text and voice; at most 20) while getting ready for the road ("🚗 Za pot"'s
     * quiz): each the clip it has, or one voiced now but not as a live line (within its quota, never the reserve kept for
     * live lines; companion/bridge/src/features/voice.ts). Each URL in their order, null where none could be had; null for
     * all from a bridge without it (404).
     */
    suspend fun prepare(texts: List<Pair<String, String>>): List<String?>? = prepareAll(texts.map { (t, v) -> ClipAsk(t, v) })

    /** [prepare] for [texts] that may be explanations in the learner's base language too ([ClipAsk.lang]). */
    suspend fun prepareAll(texts: List<ClipAsk>): List<String?>? {
        val path = routes.prepare ?: return null
        val body = buildJsonObject {
            put("texts", kotlinx.serialization.json.buildJsonArray {
                for (t in texts) add(buildJsonObject { put("text", t.text); put("voice", t.voice); t.lang?.let { put("lang", it) } })
            })
        }.toString().toRequestBody(Http.jsonType)
        val (code, raw) = http.exchange(voices.request(path).post(body).build())
        if (code == 404) return null
        if (code !in 200..299) throw IOException("HTTP $code")
        val clips = json.parseToJsonElement(raw).jsonObject["clips"] as? kotlinx.serialization.json.JsonArray ?: return texts.map { null }
        return texts.indices.map { i -> (clips.getOrNull(i) as? JsonObject)?.get("url")?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() } }
    }

    /**
     * The clip at [url] into [to]. A line joined from pieces ([LineJoin]: its manifest and its parts, kept beside it as clips of
     * their own, which other lines use too) is joined here; anything that goes wrong doing that gets the line's finished file
     * from the node at the same URL, as an older app does.
     */
    suspend fun download(url: String, to: File) {
        if (LineJoin.isLine(url) && runCatching { join(url, to) }.onFailure { Log.w("Clips", "a line wasn't joined here: ${it.javaClass.name}: ${it.message}") }.getOrDefault(false)) return
        http.downloadTo(voices.request(url).build(), to)
    }

    /** Line [url] joined into [to]: false when the node gives no manifest this app can read. */
    private suspend fun join(url: String, to: File): Boolean {
        val (code, raw) = http.exchange(voices.request(LineJoin.manifestUrl(url)).build())
        if (code !in 200..299) return false
        val m = LineJoin.parse(raw) ?: return false
        val dir = to.parentFile ?: return false
        val files = m.parts.map { p ->
            p.url?.let { u -> File(dir, DayAudio.fileOf(u)).also { f -> if (!ClipFiles.get(f, emptyList()) { t -> http.downloadTo(voices.request(u).build(), t) }) throw IOException("no part") } }
        }
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val parts = m.parts.zip(files).map { (p, f) ->
                if (f == null) JoinPart.Gap(p.ms) else JoinPart.Sound(LineJoin.samplesOf(f, p.ms), p.gain).also { f.setLastModified(now) }
            }
            LineJoin.write(parts, to)
        }
        return true
    }

    /**
     * Tells the node what the learner has just opened ([id]), its [lines] in play order, so it gets those it lacks ready ahead;
     * no lines: they left it. Best effort: nothing comes of an older bridge, a node that doesn't preload, or none at all.
     */
    suspend fun queue(id: String, lines: List<ClipAsk>) {
        val path = routes.queue ?: return
        val body = buildJsonObject {
            put("id", id)
            put("lines", kotlinx.serialization.json.buildJsonArray { for (t in lines) add(buildJsonObject { put("text", t.text); put("voice", t.voice) }) })
        }.toString().toRequestBody(Http.jsonType)
        http.exchange(connection.request(path).post(body).build())
    }

    /**
     * A visit's first lines got ahead ([ClipRoutes.ahead]: POST /towns/<id>/voice/prepare, at most 20): each one's clip URL
     * and the voice it is in, kept on the node or the host's own (never voiced for it), null where none is there; null for
     * all from a bridge without the route (404) or at home.
     */
    suspend fun ahead(texts: List<ClipAsk>): List<Pair<String, String>?>? {
        val path = routes.ahead ?: return null
        val body = buildJsonObject {
            put("texts", kotlinx.serialization.json.buildJsonArray {
                for (t in texts) add(buildJsonObject { put("text", t.text); put("voice", t.voice) })
            })
        }.toString().toRequestBody(Http.jsonType)
        val (code, raw) = http.exchange(connection.request(path).post(body).build())
        if (code == 404) return null
        if (code !in 200..299) throw IOException("HTTP $code")
        return parseAhead(raw, texts.size)
    }

    companion object {
        /** POST /towns/<id>/voice/prepare's answer for [n] lines: each clip's URL and voice, null where none came. */
        fun parseAhead(raw: String, n: Int): List<Pair<String, String>?> {
            val clips = json.parseToJsonElement(raw).jsonObject["clips"] as? kotlinx.serialization.json.JsonArray ?: return List(n) { null }
            return (0 until n).map { i ->
                val o = clips.getOrNull(i) as? JsonObject ?: return@map null
                val url = runCatching { o["url"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: return@map null
                val voice = runCatching { o["voice"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: return@map null
                url to voice
            }
        }

        /** POST /voice/say's body: {text, voice}, with [lang] an explanation's ({text, voice, lang}: the bridge's sayIn). */
        fun sayBody(text: String, voice: String, lang: String? = null): JsonObject =
            buildJsonObject { put("text", text); put("voice", voice); lang?.let { put("lang", it) } }
    }

    /** Everyone's voice profile; null from an older bridge without them. */
    suspend fun profiles(people: List<PersonVoice>? = null): String? {
        val req = if (people == null) connection.request(routes.profiles).build() else {
            val body = buildJsonObject {
                put("people", kotlinx.serialization.json.buildJsonArray {
                    for (p in people) add(buildJsonObject {
                        put("id", p.id); put("name", p.name); put("gender", p.gender); put("speaker", p.speaker)
                        p.stage?.let { put("stage", it) }; put("cast", p.cast); p.order?.let { put("order", it) }; put("present", true)
                    })
                })
            }.toString().toRequestBody(Http.jsonType)
            connection.request(routes.profiles).post(body).build()
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
 * family recording. A line the node re-voiced keeps playing its earlier clip on the phone until the new one is here
 * ([PreviousClips]).
 *
 * [visit]: the clips of a visited town (companion/README.md, "Voices on a visit"), asked of the node at the town's routes
 * ([ClipRoutes.visit]): its index lives only as long as the visit, in memory; its files share the clip cache (and its
 * cap) with the learner's own, under their own names; nothing of the learner's own index, earlier clips or re-records is
 * touched, and a long press records nothing again.
 */
class Clips(context: Context, private val scope: CoroutineScope, private val visit: Boolean = false, private val plan: VoicePlan = AudioPlan) {
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

    /**
     * The voice the node says the learner's own lines in ([PLAYER]: its cast's `player` for their gender, "male" for Jan),
     * from their voice profiles; null until it said (offline, an older bridge).
     */
    var player by mutableStateOf<String?>(null)
        private set

    /** The learner profile's gender ("male", "female"; null when it says none): their voice until the node says ([player]). */
    var playerGender: () -> String? = { null }

    /**
     * The learner's own voice as the node says it (GET /voice/profiles' "mine": the voice they chose or the default, its
     * gender and age, off, whether they have chosen; data/MyVoice.kt); null until it said (offline, an older node, a visit).
     */
    var mine by mutableStateOf<MineVoice?>(null)
        private set

    /** The node said no voice may say the learner's lines (its "player" null): none of the phone's clips stands in. */
    private var playerNone = false

    /**
     * The voice the learner's own lines are in on the phone: the node's ([mine], [player]), else their gender's narrator
     * ([playerOf]); [NONE] when the node has none for them (the phone's own voice says them).
     */
    val playerVoice: String get() = if (playerNone) NONE else player ?: playerOf(playerGender())

    /** [voices] as the clips on the phone know them: the learner's own ([PLAYER]) as the voice they are in ([playerVoice]). */
    private fun known(voices: List<String>): List<String> = knownAs(voices, playerVoice)

    /** Everyone's voice profile and the learner's voice ([player]), as the node says them (GET or POST /voice/profiles). */
    internal fun profilesSaid(raw: String) {
        runCatching { parseProfiles(raw) }.getOrNull()?.let { profiles = it }
        runCatching { parsePlayer(raw) }.getOrNull()?.let { player = it }
        playerNone = runCatching { playerSaidNone(raw) }.getOrDefault(false)
        runCatching { MyVoices.parseMine(raw) }.getOrNull()?.let { mine = it }
    }

    /**
     * The learner chose their voice ("🗣️ Moj glas · My voice"): their lines from now on in [m]'s voice, as the node said it
     * after keeping the choice; their clips in the voice before stay (nothing is voiced again in bulk).
     */
    fun mineSaid(m: MineVoice) {
        mine = m
        if (m.voice != null) player = m.voice
        playerNone = m.voice == null
    }
    /** The people last told to the node, so an unchanged list isn't sent again. */
    private var told: List<PersonVoice>? = null
    /** The node's index arrived, so the (older) disk copy must not overwrite it. */
    private var fresh = false

    init {
        if (!visit) initOwn()
    }

    /** The learner's own clips: the index kept on the phone, the earlier clips of lines re-voiced on the node. */
    private fun initOwn() {
        val me = java.lang.ref.WeakReference(this)
        PreviousClips.appIndex = { me.get()?.index.orEmpty() }
        scope.launch {
            val cached = withContext(Dispatchers.IO) { loadIndex().also { PreviousClips.read(dir) } }
            if (!fresh) index = cached + index // phrases voiced meanwhile stay
            // a line's new clip that came meanwhile (the car's library got it): its earlier one goes
            withContext(Dispatchers.IO) { settle() }
        }
    }

    /** The node has a clip for [text] (any voice), which also proves the text is Slovene. */
    fun has(text: String): Boolean = has(index, text)

    /**
     * The node has a voice store with clips, so buttons can speak even without a TTS voice; on a visit, the node can be
     * asked (the town's clips come with the lines, the first ones with the visit).
     */
    val enabled: Boolean get() = index.isNotEmpty() || (visit && online)
    val online: Boolean get() = api != null

    fun connect(config: BridgeConfig, routes: ClipRoutes = ClipRoutes.OWN) {
        api = ClipsApi(config, routes, plan)
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
            profilesSaid(raw)
        }
    }

    /**
     * The ETag of the index the phone read last ([etag], this run of the app; the copy on disk is read whole once), of [api]
     * and of who answered ([from]: the trial's or the connection's own): asked with it, the same index costs a 304 and
     * nothing else (2026-10-30, docs/plans/05-built/nav-perf.md: reading, parsing and writing it again on every Back to Home
     * made tens of MB for the collector).
     */
    private class IndexTag(val api: ClipsApi, val from: BridgeConfig, val etag: String)

    private var indexTag: IndexTag? = null

    /** Reloads the index from the node (on start and when content was published or voiced); the same as before, nothing. */
    fun refresh() {
        val a = api ?: return
        scope.launch {
            val raw = withContext(Dispatchers.IO) { runCatching { a.profiles() }.getOrNull() } ?: return@launch
            profilesSaid(raw)
        }
        scope.launch {
            // A bad index must not crash the app: parse inside the catch, write the file off the main thread. The same index
            // as the last one read (a 304): nothing to do.
            val from = a.voices
            val tag = indexTag?.takeIf { it.api === a && it.from == from }?.etag
            val got = withContext(Dispatchers.IO) { runCatching { a.indexIfChanged(tag, from) } }.getOrElse { return@launch } ?: return@launch
            val (raw, parsed) = withContext(Dispatchers.IO) { runCatching { got.first to parseIndex(got.first) }.getOrNull() } ?: return@launch
            indexTag = got.second?.let { IndexTag(a, from, it) }
            if (visit) {
                // a visit's index: in memory only, with what was voiced for it meanwhile
                index = parsed + index.filterKeys { it !in parsed }
                fresh = true
                misses.clear()
                return@launch
            }
            val before = index
            index = withContext(Dispatchers.IO) {
                val disk = loadIndex()
                // after the audio trial the connection knows nothing of the clips it left: those on the phone stay
                val now = if (a.keepsHeard) PreviousClips.heard(listOf(disk, before), parsed, PreviousClips.read(dir), PreviousClips.onPhone(files)) else parsed
                runCatching { File(dir, INDEX).writeText(if (now === parsed) raw else PrefetchWorker.encodeIndex(now)) }
                // the lines re-voiced since: their earlier clips on the phone play until the new ones are here
                runCatching { PreviousClips.swapIn(files, listOf(disk, before), now, referenced = emptySet()) }
                now
            }
            fresh = true
            misses.clear()
            renewFailed.clear()
        }
    }

    private fun loadIndex(): ClipIndex = runCatching { parseIndex(File(dir, INDEX).readText()) }.getOrDefault(emptyMap())

    fun file(url: String) = File(dir, url.substringAfterLast('/'))

    /** A clip [name] on the phone: in the clip cache, or in the car's library (then linked into the cache: one file). */
    private fun here(name: String): Boolean = File(dir, name).isFile || File(files.roadClips, name).isFile

    /** [name] in the clip cache, linked from the car's library when only it has it; null when neither has it. */
    private fun local(name: String): File? {
        val f = File(dir, name)
        if (f.isFile) return f
        val road = File(files.roadClips, name)
        return if (road.isFile && AudioFiles.link(road, f)) f else null
    }

    /**
     * The clips for [text] in the first of [voices] that has them (see [chain]), when all are on the
     * phone already (marked as used, for the LRU). A line re-voiced on the node whose new clip isn't here yet plays its
     * earlier clip, when that is here ([PreviousClips]); online the new one is fetched meanwhile, for the next time.
     */
    fun onDevice(text: String, voices: List<String>): List<File>? {
        val (got, stale) = local(text, known(voices)) ?: return null
        stale?.let(::renew)
        return got
    }

    /**
     * The clip of an explanation in [lang] ([text]: [IntroSpeech.plain]) in the first of [voices] that has one, when it is
     * on the phone already (marked as used, for the LRU). A re-voiced one's earlier clip stands in as for a line.
     */
    fun explanationOnDevice(text: String, lang: String, voices: List<String>): List<File>? {
        val idx = index
        val voice = voices.firstOrNull { resolve(idx, text, it, lang) != null } ?: return null
        val pick = PreviousClips.pick(idx, earlier(), text, voice, ::here, lang) ?: return null
        val got = pick.urls.map { local(PreviousClips.fileOf(it)) ?: return null }
        val now = System.currentTimeMillis()
        got.forEach { it.setLastModified(now) }
        return got
    }

    /** The node has the clip of an explanation in [lang] in one of [voices] (it may not be on the phone yet). */
    fun hasExplanation(text: String, lang: String, voices: List<String>): Boolean = voices.any { resolve(index, text, it, lang) != null }

    /**
     * Gets the clip of an explanation in [lang] ([text]) onto the phone: the node's in the first of [voices], else it has
     * the node voice it now (in the first of [voices], as /voice/say does a line, but never with the reserve kept for live
     * lines), else one the node has in another of them. Null when it can't be had now (offline, no engine, the quota at
     * its reserve: not asked again for a while). The work goes on in the background, so a caller that can't wait has it
     * next time.
     */
    fun explanation(text: String, lang: String, voices: List<String>): Deferred<List<File>?> {
        val key = explainKey(text, lang, voices)
        return synchronized(fetching) {
            fetching[key]?.takeIf { it.isActive } ?: scope.async(Dispatchers.IO) { runCatching { fetchExplanation(text, lang, voices) }.getOrNull() }.also { d ->
                fetching[key] = d
                d.invokeOnCompletion { synchronized(fetching) { if (fetching[key] === d) fetching.remove(key) } }
            }
        }
    }

    /**
     * The node couldn't voice this explanation just now ([explanation] gave none): it isn't asked again for a minute
     * ([EXPLAIN_MISS_MS]; mostly ElevenLabs was busy for a moment), the step is read in its runs meanwhile.
     */
    fun explanationMissed(text: String, lang: String, voices: List<String>): Boolean =
        misses[explainKey(text, lang, voices)]?.let { System.currentTimeMillis() - it < EXPLAIN_MISS_MS } == true

    /** The clip of this explanation is on its way ([explanation]: being voiced by the node, or downloading). */
    fun explaining(text: String, lang: String, voices: List<String>): Boolean =
        synchronized(fetching) { fetching[explainKey(text, lang, voices)]?.isActive == true }

    /** An explanation's key among the [misses] and the clips [fetching]: apart from the lines'. */
    private fun explainKey(text: String, lang: String, voices: List<String>) = "x|${IntroSpeech.key(lang, text)}|${voices.firstOrNull()}"

    private suspend fun fetchExplanation(text: String, lang: String, voices: List<String>): List<File>? {
        val a = api ?: return null
        val key = IntroSpeech.key(lang, text)
        val first = voices.firstOrNull() ?: return null
        val url = index[key]?.get(first) ?: run {
            var asked: Pair<String, String>? = null
            // a voice the node refuses (an older bridge) tries the next; one it can't voice now (204), or an error, stops
            if (!explanationMissed(text, lang, voices)) for (v in voices) {
                try {
                    asked = a.say(text, v, lang)
                    break
                } catch (_: VoiceRefused) {
                    continue
                } catch (_: Exception) {
                    break
                }
            }
            if (asked == null) {
                misses[explainKey(text, lang, voices)] = System.currentTimeMillis()
                // the node can't now: another of their voices' clip it has
                return@run voices.drop(1).firstNotNullOfOrNull { v -> index[key]?.get(v) }
            }
            withContext(Dispatchers.Main) { index = index + (key to (index[key].orEmpty() + (asked.second to asked.first))) }
            asked.first
        } ?: return null
        val f = file(url)
        if (!ClipFiles.get(f, listOf(File(files.roadClips, f.name))) { to -> a.download(url, to) }) throw IOException("no clip")
        f.setLastModified(System.currentTimeMillis())
        trim()
        return listOf(f)
    }

    /** The earlier clips of lines re-voiced on the node ([PreviousClips]): the learner's own; none on a visit. */
    private fun earlier(): ClipIndex = if (visit) emptyMap() else PreviousClips.known(dir)

    /** [onDevice]'s files, and the voice whose new clip is worth getting when an earlier clip stands in. */
    private fun local(text: String, voices: List<String>): Pair<List<File>, Pair<String, String>?>? {
        val idx = index
        val voice = resolveFirst(idx, text, voices)?.first ?: return null
        val pick = PreviousClips.pick(idx, earlier(), text, voice, ::here) ?: return null
        val got = pick.urls.map { local(PreviousClips.fileOf(it)) ?: return null }
        val now = System.currentTimeMillis()
        got.forEach { it.setLastModified(now) }
        if (pick.stale) Log.i(TAG, "earlier clip stands in: ${Voice.normalize(text)} ($voice): ${pick.old.joinToString { PreviousClips.fileOf(it) }}")
        return got to (text to voice).takeIf { pick.stale }
    }

    /**
     * QA (a debug build, app/QaHooks "audio:revoice:<text>|<url>"): [text]'s clip in the female narrator's voice gets a new
     * URL, [url] or one the node doesn't have, as a re-voice on the node would, through the same swap as [refresh].
     */
    fun qaRevoice(text: String, url: String? = null) {
        val idx = index
        val keys = keys(idx, text, FEMALE) ?: return Unit.also { Log.w(TAG, "qa revoice: no clip of $text") }
        val new = idx + keys.associateWith { k ->
            idx.getValue(k) + (FEMALE to (url ?: "/voice/file/${UUID.nameUUIDFromBytes("qa|$k".toByteArray()).toString().replace("-", "")}qa00000000.mp3"))
        }
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { PreviousClips.swapIn(files, listOf(idx), new, referenced = emptySet()) } }
            index = new
            misses.clear()
            renewFailed.clear()
            Log.i(TAG, "qa revoice: ${keys.joinToString()} → ${keys.map { new.getValue(it).getValue(FEMALE) }}; earlier: ${PreviousClips.known(dir).filterKeys { it in keys }}")
        }
    }

    /** Lines whose new clip couldn't be fetched in the background lately (normalized text|voice → when): not asked again soon. */
    private val renewFailed = ConcurrentHashMap<String, Long>()

    /** [line] (text, voice) played an earlier clip: its new one comes in the background (online), so the next play has it. */
    private fun renew(line: Pair<String, String>) {
        val (text, voice) = line
        val key = missKey(text, voice)
        val now = System.currentTimeMillis()
        if (!online || missedRecently(text, listOf(voice)) || renewFailed[key]?.let { now - it < MISS_MS } == true) return
        val work = fetchOnce(text, listOf(voice))
        scope.launch { if (work.await() == null) renewFailed[key] = System.currentTimeMillis() }
    }

    fun missedRecently(text: String, voices: List<String>): Boolean {
        val at = misses[missKey(text, voices.first())] ?: return false
        return System.currentTimeMillis() - at < MISS_MS
    }

    /**
     * Gets the clips for [text] in the first of [voices] (downloading them, or asking the node to voice the
     * phrases it hasn't got in it; another of [voices] only when the node can't, see [pick]) and hands them to
     * [then] within [waitMs]; when they don't come in time (or can't be had), a re-voiced line's earlier clip on the phone
     * ([onDevice]), else null so the caller speaks otherwise now. The work goes on in the background either way, so the
     * clip is there next time.
     */
    fun prepare(text: String, voices: List<String>, waitMs: Long = WAIT_MS, then: (List<File>?) -> Unit) {
        val work = fetchOnce(text, voices)
        scope.launch { then(withTimeoutOrNull(waitMs) { work.await() } ?: local(text, known(voices.take(1)))?.first) }
    }

    /**
     * A visit's first lines onto the phone ahead ([lines]: each text and the voices a [Speaker.say] of it asks for, best
     * first; app/VisitAhead), while the visit begins: those not here yet asked of the node at the town's route
     * ([ClipRoutes.ahead]: kept there, else the host's own; never voiced for it, so no credits go to a line maybe never
     * heard), at most [AHEAD_MAX], 20 at a time; each downloaded into the clip cache, within the learner's cap ([trim]), and
     * in the visit's index, so the line plays at once when it is said. Nothing is marked missed: a line not got is asked for
     * when it is said (the node voices it then). Not with the cap off ("Off": nothing got ahead). How many came.
     */
    suspend fun ahead(lines: List<Pair<String, List<String>>>): Int {
        if (!visit || !settings.cap.prefetch) return 0
        val a = api ?: return 0
        val todo = lines
            .filter { (t, v) -> v.isNotEmpty() && v.first() != PLAYER && parts(t).size == 1 && Voice.normalize(t).isNotEmpty() && onDevice(t, v.take(1)) == null }
            .distinctBy { (t, v) -> missKey(t, v.first()) }
            .take(AHEAD_MAX)
        var got = 0
        for (batch in todo.chunked(AHEAD_AT_ONCE)) {
            val answers = withContext(Dispatchers.IO) { runCatching { a.ahead(batch.map { (t, v) -> ClipAsk(t, v.first()) }) }.getOrNull() } ?: break
            for ((i, r) in answers.withIndex()) {
                val (url, voice) = r ?: continue
                val key = Voice.normalize(batch[i].first)
                val f = file(url)
                val here = withContext(Dispatchers.IO) {
                    runCatching { ClipFiles.get(f, emptyList()) { to -> a.download(url, to) } }.getOrDefault(false).also { if (it) f.setLastModified(System.currentTimeMillis()) }
                }
                if (!here) continue
                withContext(Dispatchers.Main) { index = index + (key to (index[key].orEmpty() + (voice to url))) }
                got++
            }
        }
        if (got > 0) withContext(Dispatchers.IO) { trim() }
        Log.i(TAG, "a visit's first lines got ahead: $got of ${todo.size}")
        return got
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

    /** What the node is getting ready for what the learner has open ([queue]); null: nothing. */
    private var queued: String? = null

    /** The last of what was told the node about it: each waits for the one before, or a cancel could overtake its start. */
    private var telling: kotlinx.coroutines.Job? = null

    /** Tells the node [lines] for [id], after whatever was told it before. */
    private fun tell(a: ClipsApi, id: String, lines: List<ClipAsk>) {
        val before = telling
        telling = scope.launch(Dispatchers.IO) {
            before?.join()
            runCatching { a.queue(id, lines) }
        }
    }

    /**
     * What the learner has just opened says its [lines] (each as [Speaker] would ask for it), in play order: the node gets those
     * the phone lacks ready ahead. Anything before is left.
     */
    fun queue(lines: List<ClipAsk>) {
        val a = api ?: return
        val left = lines.filter { Voice.normalize(it.text).isNotEmpty() && onDevice(it.text, listOf(it.voice)) == null }.take(QUEUE_MAX)
        if (left.isEmpty()) return unqueue()
        val id = UUID.randomUUID().toString().also { queued = it }
        tell(a, id, left)
    }

    /** The learner left what was opened: what is left of it isn't made. */
    fun unqueue() {
        val a = api ?: return
        val id = queued ?: return
        queued = null
        tell(a, id, emptyList())
    }

    /** The clips of a line on their way (downloading, or being voiced by the node): a second ask waits for the first. */
    private val fetching = HashMap<String, Deferred<List<File>?>>()

    private fun fetchOnce(text: String, voices: List<String>): Deferred<List<File>?> = synchronized(fetching) {
        val key = missKey(text, voices.first())
        fetching[key]?.takeIf { it.isActive } ?: scope.async(Dispatchers.IO) {
            runCatching { fetch(text, voices) }.onFailure { Log.w(TAG, "no clip of a line: ${it.javaClass.name}: ${it.message}", it) }.getOrNull()
        }.also { d ->
            fetching[key] = d
            d.invokeOnCompletion { synchronized(fetching) { if (fetching[key] === d) fetching.remove(key) } }
        }
    }

    private suspend fun fetch(text: String, voices: List<String>): List<File>? {
        val a = api ?: return null
        // the clips on the phone by the voice they are in; the node asked in the voices asked for (the learner's own as
        // "player": it says them in their voice, a word to three in its carrier sentence)
        val here = known(voices)
        val urls = resolve(index, text, here.first()) ?: run {
            val got = mutableListOf<String>()
            for (p in parts(text)) {
                val r = pick(index, Voice.normalize(p), here) { ask(a, p, voices) }
                if (r == null) {
                    misses[missKey(text, voices.first())] = System.currentTimeMillis()
                    return null
                }
                val key = Voice.normalize(p)
                // the node answered in another voice with a newer clip than the index's: the earlier one stands in meanwhile
                if (!visit) index[key]?.get(r.second)?.takeIf { it != r.first }?.let { old ->
                    runCatching { PreviousClips.replacedIn(files, key, r.second, old, r.first) }
                }
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
        if (visit) return got
        // a re-voiced line's new clip is here: its earlier one goes, once it isn't playing (it just played, often: again then)
        val keys = (listOf(text) + parts(text)).mapTo(HashSet()) { Voice.normalize(it) }
        settle(only = keys)
        if (PreviousClips.known(dir).keys.any { it in keys }) scope.launch(Dispatchers.IO) {
            delay(PreviousClips.BUSY_MS + 1_000)
            settle(only = keys)
        }
        return got
    }

    /** A pass over the earlier clips against the index ([PreviousClips.settleIn]): those whose new clip is here go. */
    private fun settle(only: Set<String>? = null) {
        runCatching { PreviousClips.settleIn(files, index, referenced = emptySet(), only = only) }
    }

    /**
     * Asks the node to voice [phrase] in the first of [voices] it knows: a voice it refuses (an older
     * bridge has only female and male) tries the next. Null when no engine can make it now. Only a node knows
     * [PLAYER]: the audio trial is asked in the voice the learner's lines are in ([known]), and for none when they have none.
     */
    private suspend fun ask(a: ClipsApi, phrase: String, voices: List<String>): Pair<String, String>? {
        for (v in if (a.planned) known(voices) - NONE else voices) {
            try {
                return a.say(phrase, v)
            } catch (_: VoiceRefused) {
                continue
            } catch (e: Exception) {
                Log.w(TAG, "the node didn't voice a line in $v: ${e.javaClass.name}: ${e.message}", e)
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
            // asked for anew: no earlier clip stands in for it any more (the old take is dropped on purpose)
            val idx = this@Clips.index
            if (!visit) scope.launch(Dispatchers.IO) { runCatching { PreviousClips.forgetIn(dir, key, voice, idx) } }
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
        val a = api?.takeUnless { visit } ?: return then(ReRecord.Offline)
        scope.launch {
            val r = try {
                ReRecorder(cache) { p, v -> a.redo(p, v) }.run(text, known(voices))
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

        /** At most this many of a visit's first lines are got ahead ([ahead]); a greeting's clip is a few KB. */
        const val AHEAD_MAX = 40

        /** The most lines one start asks the node to get ready ([queue]; its route takes as many). */
        const val QUEUE_MAX = 60

        /** The lines one ask of [ahead] takes (the node's route takes at most 20). */
        private const val AHEAD_AT_ONCE = 20

        /**
         * The learner's own lines in a dialog (companion/SCENES.md, "The learner's voice"): asked of the node in this voice,
         * which says them in its cast's `player` for the learner's gender (companion/voice-cast.json: the male narrator's
         * Luka for Jan); on the phone their clips are that voice's ([playerVoice]), so a narrator's clip of a text serves.
         */
        const val PLAYER = "player"

        /**
         * The voice the learner's lines are in while the node hasn't said ([player]): the narrator of the profile's [gender]
         * ("male", "m": the male one), the female one when it says none, as the node's cast has it.
         */
        fun playerOf(gender: String?): String = if (gender?.trim()?.lowercase() in setOf("m", "male")) MALE else FEMALE

        /** [voices] with the learner's own ([PLAYER]) as the voice their clips are in, [player] ("male"): what the index knows. */
        fun knownAs(voices: List<String>, player: String): List<String> = voices.map { if (it == PLAYER) player else it }.distinct()

        /** The learner's lines have no voice of the node's ([playerVoice]): no clip is kept under it. */
        const val NONE = "-"

        /** The node said "player": null (no voice of its cast may say the learner's lines), not left it out (an older bridge). */
        fun playerSaidNone(raw: String): Boolean = json.parseToJsonElement(raw).jsonObject["player"] is kotlinx.serialization.json.JsonNull

        /** The voice the learner's lines are in, from GET or POST /voice/profiles ("player": "male"); null from an older bridge. */
        fun parsePlayer(raw: String): String? =
            json.parseToJsonElement(raw).jsonObject["player"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != PLAYER }
        private const val TAG = "Clips"

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
        /**
         * An explanation the node couldn't voice isn't asked for again within a minute (a line's: [MISS_MS]): the node
         * answers so mostly while ElevenLabs is busy for a moment, and the step is read once, so a long wait is a lost clip.
         */
        const val EXPLAIN_MISS_MS = 60_000L
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

        /**
         * [index] has a clip for [text] in any voice, or one for each of its phrases in a narrator's ([Clips.has]; the node's
         * port, companion/bridge/src/course-speech.ts indexHas, reads the same: IntroSpeechParityTest).
         */
        fun has(index: ClipIndex, text: String): Boolean =
            index[Voice.normalize(text)]?.isNotEmpty() == true || resolve(index, text, FEMALE) != null || resolve(index, text, MALE) != null

        /** The first of [voices] with clips for [text], and those clip URLs. */
        fun resolveFirst(index: ClipIndex, text: String, voices: List<String>, lang: String? = null): Pair<String, List<String>>? =
            voices.firstNotNullOfOrNull { v -> resolve(index, text, v, lang)?.let { v to it } }

        /**
         * Clip URLs for [text] in [voice]: one for the whole text, or one per phrase when all have one. With [lang], an
         * explanation in that language ([IntroSpeech.key]): its one clip.
         */
        fun resolve(index: ClipIndex, text: String, voice: String, lang: String? = null): List<String>? =
            keys(index, text, voice, lang)?.map { index.getValue(it).getValue(voice) }

        /**
         * The phrase keys (normalized) whose clips in [voice] say [text] ([resolve]): the whole text's, or each phrase's;
         * with [lang], the explanation's own key ([IntroSpeech.key]), never cut into phrases.
         */
        fun keys(index: ClipIndex, text: String, voice: String, lang: String? = null): List<String>? {
            if (lang != null) return IntroSpeech.key(lang, text).takeIf { index[it]?.get(voice) != null }?.let(::listOf)
            val whole = Voice.normalize(text)
            if (index[whole]?.get(voice) != null) return listOf(whole)
            val ps = Voice.phrases(text)
            if (ps.size < 2) return null
            return ps.map { p -> Voice.normalize(p).takeIf { index[it]?.get(voice) != null } ?: return null }
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
