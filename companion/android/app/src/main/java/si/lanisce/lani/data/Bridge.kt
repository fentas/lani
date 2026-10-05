package si.lanisce.lani.data

import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.game.scene.parseScenes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import si.lanisce.lani.game.GameState
import si.lanisce.lani.l10n.Learner
import java.io.File
import java.io.IOException

data class BridgeConfig(val baseUrl: String, val token: String)

/** HTTP client for lani-bridge (companion/bridge). */
class Bridge(private val config: BridgeConfig) {
    private val http = Http.client(connectSeconds = 10, readSeconds = 60) // the SSE stream pings every 25 s

    private fun request(path: String) = config.request(path)
    /**
     * A GET's answer, its texts said to the learner of this phone ({learner}, {m:…|f:…}: l10n/Learner.kt). The bridge
     * renders what it serves already; this is for one that doesn't (a newer content than it).
     */
    private suspend fun get(path: String) = Learner.current.renderJson(http.text(request(path).build()))
    /** [exchange] for content: the body said to the learner, as [get]. */
    private suspend fun content(req: Request): Pair<Int, String> = exchange(req).let { (code, body) -> code to Learner.current.renderJson(body) }
    private suspend fun post(path: String, body: JsonElement) =
        http.text(request(path).post(body.toString().toRequestBody(Http.jsonType)).build())
    private suspend fun exchange(req: Request) = http.exchange(req)

    /** The home language's dashboard; with [language], that language's (its own review deck: GET /state?language=). */
    suspend fun dashboard(language: String? = null): Dashboard =
        Dashboard.parse(get(if (language == null) "/state" else "/state?language=$language"))
    /** GET /state as sent: all six learner databases plus computed fields. */
    suspend fun stateRaw(): String = get("/state")

    /** A raw write for the outbox: the status code and body, throwing only when the node is unreachable. */
    suspend fun send(method: String, path: String, body: String): Pair<Int, String> =
        exchange(request(path).method(method, body.toRequestBody(Http.jsonType)).build())
    suspend fun modules(): List<ModuleInfo> = json.decodeFromString(get("/modules"))
    suspend fun module(id: String): Module = parseModule(get("/modules/$id"))
    suspend fun packs(): List<PackInfo> = parsePacks(get("/packs"))
    suspend fun pack(id: String): Pack = parsePack(get("/packs/$id"))

    /** The grammar book's pages (GET /grammar: the curated ones with the tutor's additions, then the tutor's); null from an older bridge (404). */
    suspend fun grammar(): List<GrammarPage>? {
        val (code, body) = content(request("/grammar").build())
        return when (code) {
            200 -> Grammar.parseList(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }
    /** The car's audio drills (GET /drills, lani.drill/v0); null from an older bridge without them (404): the app's own then. */
    suspend fun drills(): List<Drill>? {
        val (code, body) = content(request("/drills").build())
        return when (code) {
            200 -> Drills.parseList(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }
    suspend fun scenarios(): List<Scenario> = parseScenarios(get("/scenarios"))
    /**
     * Close-up scenes of the village, resolved (companion/SCENES.md), as far as this app draws them: it says which arts
     * it has ([SceneArt.VERSION]); an app that doesn't say gets what the first arts drew.
     */
    suspend fun scenes(): List<SceneSpec> = parseScenes(get("/scenes?arts=${si.lanisce.lani.game.scene.SceneArt.VERSION}"))
    /**
     * The people of the village (companion/VILLAGERS.md). The phone's [time] of day says the app reads a line's `when`
     * (an older app gets the lines without it).
     */
    suspend fun villagers(time: TimeOfDay = TimeOfDay.now()): List<Villager> = parseVillagers(get("/villagers?time=${time.name.lowercase()}"))
    /**
     * A role-play with villager [id] played by the tutor (scenario id "villager:<id>"), built for the current friendship
     * and the phone's [time] of day (its opener a greeting for it: `?time=evening`).
     */
    suspend fun villagerScenario(id: String, time: TimeOfDay = TimeOfDay.now()): Scenario =
        json.decodeFromString(get("/villagers/$id/scenario?time=${time.name.lowercase()}"))

    /**
     * The tutor's introductions of the people who joined the village (GET /arrivals, companion/VILLAGERS.md "Arrivals"),
     * as the node sends them; null from an older bridge without them (404). The culture pack's are bundled in the app.
     */
    suspend fun arrivals(): String? {
        val (code, body) = content(request("/arrivals").build())
        return when (code) {
            200 -> body
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * The culture pack the learner's village is in (GET /culture: its id; the app bundles the packs), and the landscape
     * the node suggests for a new village (`lani-profile add --landscape`; null when it doesn't). Null from an older
     * bridge without it (404).
     */
    suspend fun culture(): CultureInfo? {
        val (code, body) = exchange(request("/culture").build())
        return when (code) {
            200 -> json.decodeFromString<CultureInfo>(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * What [word] means, as said in [line] (its translation [en]), explained in [meaningIn] (the pair's base); a word of
     * another town's [language] on a visit. Null from an older bridge without lookups (404); an answer without entries
     * means the dictionary doesn't know the word.
     */
    suspend fun lookup(word: String, line: String?, en: String?, language: String? = null, meaningIn: String? = null): WordLookup? {
        val (code, body) = exchange(request(Words.url(config.baseUrl, word, line, en, language, meaningIn).toString()).build())
        return when (code) {
            200 -> Words.parse(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * The forms of [words] (lemmas, at most [WordFormsWire.BATCH]): GET /forms, companion/README.md "Word forms". Null
     * from an older bridge without them (404) or one that won't take the request (400): no form questions then.
     */
    suspend fun forms(words: List<String>): FormsAnswer? {
        val (code, body) = exchange(request(WordFormsWire.url(config.baseUrl, words).toString()).build())
        return when (code) {
            200 -> WordFormsWire.parse(body)
            400, 404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * The tutor's explanation of [sentence]'s grammar, kept from an earlier "Ask the tutor" (GET /sentence,
     * publish_sentence_note), in [base] when there is one; a line of another town's [language] on a visit. Null when there
     * is none, and from an older bridge without it (404).
     */
    suspend fun sentenceNote(sentence: String, language: String? = null, base: String? = null): String? {
        val (code, body) = exchange(request(Sentences.url(config.baseUrl, sentence, language, base).toString()).build())
        return when (code) {
            200 -> Sentences.note(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * What POST /words takes as `from` (GET /words: "reading" too); null from an older bridge that doesn't say (404),
     * which takes [Words.OLD_SOURCES].
     */
    suspend fun wordSources(): Set<String>? {
        val (code, body) = exchange(request("/words").build())
        return when (code) {
            200 -> Words.parseSources(body)
            404, 405 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * The reading corner's readings (GET /readings: the culture pack's, then the tutor's; data/ReadingPractice.kt); null
     * from an older bridge without them (404).
     */
    suspend fun readings(): List<si.lanisce.lani.game.culture.ReadingFile>? {
        val (code, body) = content(request("/readings").build())
        return when (code) {
            200 -> ReadingPractice.parseServed(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /** The towns linked with this learner's (GET /towns, see data/Towns.kt); null from an older bridge without towns (404). */
    suspend fun towns(): TownsList? {
        val (code, body) = exchange(request("/towns").build())
        return when (code) {
            200 -> Towns.parseList(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * Part [kind] of a visit to linked town [id] as answered, the status and body: its public state, its people or its
     * places ([Visits.PUBLIC], [Visits.VILLAGERS], [Visits.SCENES]; GET /towns/:id/<kind>, see data/Visits.kt). The
     * phone's [time] of day says the app reads a line's `when` (the people's lines keep it); the places come as far as this
     * app draws them ([SceneArt.VERSION], as [scenes]).
     */
    suspend fun townPart(id: String, kind: String, time: TimeOfDay = TimeOfDay.now()): Pair<Int, String> =
        content(
            request(
                "/towns/$id/$kind?time=${time.name.lowercase()}" + if (kind == Visits.SCENES) "&arts=${si.lanisce.lani.game.scene.SceneArt.VERSION}" else "",
            ).build(),
        )

    /**
     * A visit's write to linked town [id], as answered: [kind] is [TownActs.HELP], [TownActs.GIFT] or [TownActs.TRADE]
     * (POST /towns/:id/<kind>, see data/TownActs.kt); the same [body] (its id) again is answered the same.
     */
    suspend fun townWrite(id: String, kind: String, body: JsonObject): Pair<Int, String> =
        exchange(request("/towns/$id/$kind").post(body.toString().toRequestBody(Http.jsonType)).build())

    /**
     * A role-play on a visit to town [id] (POST /towns/:id/talk: {villager} or {market: {give, get}}), as answered; with
     * the phone's [time] of day, so the one met greets for it.
     */
    suspend fun townTalk(id: String, body: JsonObject, time: TimeOfDay = TimeOfDay.now()): Pair<Int, String> {
        val timed = JsonObject(body + ("time" to JsonPrimitive(time.name.lowercase())))
        return content(request("/towns/$id/talk").post(timed.toString().toRequestBody(Http.jsonType)).build())
    }

    /** What guests from linked towns brought this town (GET /towns/guests); null from an older bridge (404). */
    suspend fun townGuests(): List<GuestEntry>? {
        val (code, body) = exchange(request("/towns/guests").build())
        return when (code) {
            200 -> TownActs.parseGuests(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * The friendship with each linked town (GET /towns/friendship, see data/Friendship.kt): the level both towns agree on,
     * what it brings; null from an older bridge (404).
     */
    suspend fun friendships(): FriendshipList? {
        val (code, body) = exchange(request("/towns/friendship").build())
        return when (code) {
            200 -> Friendships.parse(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * A friendship's write to linked town [id], as answered: [what] is "aid" (help against its trouble), "moves" (offer a
     * move), or "moves/<move>/accept" and "…/decline"; the same [body] (its id) again is answered the same.
     */
    suspend fun friendshipWrite(id: String, what: String, body: JsonObject = buildJsonObject { }): Pair<Int, String> =
        exchange(request("/towns/$id/$what").post(body.toString().toRequestBody(Http.jsonType)).build())
    /** GET /towns/questions as answered (data/TownQuestions.kt): the status and body; an older bridge answers 404. */
    suspend fun townQuestions(): Pair<Int, String> = exchange(request("/towns/questions").build())

    /** POST /towns/invite as answered: the status and body (a child's bridge answers 403). */
    suspend fun townInvite(): Pair<Int, String> = exchange(request("/towns/invite").post("{}".toRequestBody(Http.jsonType)).build())

    /** POST /towns/accept with an invitation's text, as answered: the status and body. */
    suspend fun townAccept(invite: String): Pair<Int, String> =
        exchange(request("/towns/accept").post(buildJsonObject { put("invite", invite) }.toString().toRequestBody(Http.jsonType)).build())

    suspend fun latestRelease(): Release? = runCatching { json.decodeFromString<Release>(get("/app/latest")) }.getOrNull()

    /** The village, or null when the node has none yet (404). */
    suspend fun game(): GameSnapshot? {
        val (code, body) = exchange(request("/game").build())
        return when (code) {
            200 -> json.decodeFromString(GameSnapshot.serializer(), body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /** Writes the village based on [rev]; a 409 hands back the node's newer copy. */
    suspend fun putGame(rev: Long, state: GameState): GamePut {
        val body = json.encodeToString(GameSnapshot.serializer(), GameSnapshot(rev, state))
        val (code, raw) = exchange(request("/game").put(body.toRequestBody(Http.jsonType)).build())
        return when (code) {
            200 -> GamePut.Saved(json.decodeFromString<RevOnly>(raw).rev)
            409 -> GamePut.Conflict(json.decodeFromString(GameSnapshot.serializer(), raw))
            else -> throw IOException("HTTP $code: ${raw.take(200)}")
        }
    }

    @Serializable
    private data class RevOnly(val rev: Long)

    /** GET /culture, as far as the app needs it: the pack it bundles to play, and a new village's suggested landscape. */
    @Serializable
    data class CultureInfo(val id: String, val landscape: String? = null)

    /** Events buffered after [since], for background checks. */
    suspend fun backlog(since: Long): List<Pair<Long, BridgeEvent>> {
        val arr = json.parseToJsonElement(get("/events/backlog?since=$since")) as JsonArray
        return arr.mapNotNull { e ->
            val o = e as JsonObject
            val id = (o["id"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return@mapNotNull null
            BridgeEvent.parse(o["event"].toString())?.let { id to it }
        }
    }

    suspend fun download(path: String, to: File, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        http.newCall(request(path).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            to.parentFile?.mkdirs()
            to.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) progress(read.toFloat() / total)
                    }
                }
            }
        }
    }

    suspend fun message(kind: String, conversationId: String, text: String, data: JsonObject? = null) {
        post("/message", buildJsonObject {
            put("kind", kind)
            put("conversation_id", conversationId)
            put("text", text)
            if (data != null) put("data", data)
        })
    }

    suspend fun permission(requestId: String, allow: Boolean) {
        post("/permission", buildJsonObject {
            put("request_id", requestId)
            put("behavior", if (allow) "allow" else "deny")
        })
    }

    /**
     * One SSE connection, as (event id, event); the id is null when the node sent none. Completes (normally or
     * with an error) when the stream ends. [onOpen] runs once connected, on OkHttp's thread. Unbounded: a replay
     * after a long time offline (up to 200 events) must not drop events while the collector saves each one.
     */
    fun events(lastEventId: Long, onOpen: () -> Unit = {}): Flow<Pair<Long?, BridgeEvent>> = callbackFlow {
        val req = request("/events").header("Last-Event-ID", lastEventId.toString()).build()
        val source = EventSources.createFactory(http).newEventSource(req, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) = onOpen()
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val ev = BridgeEvent.parse(data) ?: return
                trySend(id?.toLongOrNull() to ev)
            }
            override fun onClosed(eventSource: EventSource) { close() }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: if (response?.code == 401) Unpaired() else IOException("event stream failed: HTTP ${response?.code}"))
            }
        })
        awaitClose { source.cancel() }
    }.buffer(Channel.UNLIMITED)
}
