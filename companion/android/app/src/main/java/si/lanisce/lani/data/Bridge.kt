package si.lanisce.lani.data

import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.game.scene.parseScenes
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import okio.ByteString
import si.lanisce.lani.game.GameState
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * A call's request and the reading of its answer off the main thread (2026-10-28, docs/plans/05-built/home-return.md):
     * the app's calls come from the main thread, and an answer's parsing (the learner's state, the scenes: hundreds of KB
     * of JSON) took it for hundreds of ms each when Home came back and read everything anew. The caller gets the result
     * on its own thread.
     */
    private suspend fun <T> off(block: suspend CoroutineScope.() -> T): T = withContext(Dispatchers.Default, block)

    /** What reading an answer depends on besides its text: the learner it is said to, the language pair, the day. */
    private data class Said(val learner: Learner, val pair: LangPair, val day: LocalDate) {
        companion object { fun now() = Said(Learner.current, L10n.pair, LocalDate.now()) }
    }

    /** An answer read before: its text's digest as sent, what else its reading depended on, the value it gave. */
    private class Read(val digest: ByteString, val said: Said, val value: Any?)

    /** The last answer read of each path ([remembered]). */
    private val reads = ConcurrentHashMap<String, Read>()

    /**
     * GET [path] read by [parse] (its text said to the learner first, as [get]); an answer the same as the last one read
     * of [path] (the same text, learner, pair and day) is not read again: the value read then comes back, the same
     * instance (2026-10-28, docs/plans/05-built/home-return.md). Home reads everything anew each time it comes back
     * (AppViewModel.shown) and mostly nothing changed since: reading the learner's state and the scenes again took the
     * processor and made tens of MB for the collector while Home was drawn, and a new value, equal to the last, was
     * compared field by field on the main thread before the screen's state kept the one it had. A status other than 2xx
     * is an [IOException] naming it, as [get] has it.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> remembered(path: String, parse: (String) -> T): T = answer(path, notFound = false, parse) as T

    /** [remembered] for a route an older bridge hasn't: null for its 404, an [IOException] for a status other than 200, as [content] has it. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> rememberedOrNone(path: String, parse: (String) -> T?): T? = answer(path, notFound = true, parse) as T?

    private suspend fun answer(path: String, notFound: Boolean, parse: (String) -> Any?): Any? {
        val a = http.raw(request(path).build())
        if (notFound && a.code == 404) return null
        if (a.code !in 200..299 || (notFound && a.code != 200)) throw IOException("HTTP ${a.code}: ${a.text.take(200)}")
        val said = Said.now()
        val digest = a.body.sha256()
        reads[path]?.let { if (it.digest == digest && it.said == said) return it.value }
        return parse(said.learner.renderJson(a.text)).also { reads[path] = Read(digest, said, it) }
    }

    /** The home language's dashboard; with [language], that language's (its own review deck: GET /state?language=). */
    suspend fun dashboard(language: String? = null): Dashboard = off {
        remembered(if (language == null) "/state" else "/state?language=$language", Dashboard::parse)
    }
    /** GET /state as sent: all six learner databases plus computed fields. */
    suspend fun stateRaw(): String = off { get("/state") }

    /** A raw write for the outbox: the status code and body, throwing only when the node is unreachable. */
    suspend fun send(method: String, path: String, body: String): Pair<Int, String> = off {
        exchange(request(path).method(method, body.toRequestBody(Http.jsonType)).build())
    }

    /** A file for the outbox ([OutboxUpload]: a chat photo): `PUT` [bytes] as [type] to [path]; the status code and body. */
    suspend fun put(path: String, bytes: ByteArray, type: String): Pair<Int, String> = off {
        exchange(request(path).put(bytes.toRequestBody(type.toMediaType())).build())
    }

    /**
     * A chat photo from the node (GET /chat/images/<id>) into [to]: for a message whose photo the phone hasn't (another
     * phone, the app's files cleared). An [IOException] when the node hasn't it (404) or can't be reached.
     */
    suspend fun chatImage(id: String, to: File) = off { http.downloadTo(request("/chat/images/$id").build(), to) }
    suspend fun modules(): List<ModuleInfo> = off { remembered("/modules") { json.decodeFromString<List<ModuleInfo>>(it) } }
    suspend fun module(id: String): Module = off { parseModule(get("/modules/$id")) }
    suspend fun packs(): List<PackInfo> = off { remembered("/packs", ::parsePacks) }
    suspend fun pack(id: String): Pack = off { parsePack(get("/packs/$id")) }

    /** The grammar book's pages (GET /grammar: the curated ones with the tutor's additions, then the tutor's); null from an older bridge (404). */
    suspend fun grammar(): List<GrammarPage>? = off { rememberedOrNone("/grammar") { Grammar.parseList(it) } }
    /** The cheat sheet's cards no grammar page fits (GET /cards, lani.card/v0: the curated ones, then the tutor's); null from an older bridge (404). */
    suspend fun cards(): List<DeckCard>? = off { rememberedOrNone("/cards") { Cards.parseList(it) } }
    /**
     * The school's course of the village's language (GET /course, companion/COURSE.md: the outline, the units, the tutor's
     * homework); null from an older bridge (404), or for a language without a course: the app's own then.
     */
    suspend fun course(): Course? = off { rememberedOrNone("/course") { Courses.parse(it) } }
    /**
     * A printed sheet or booklet of the course as a page to print ([Prints.path]: GET /course/sheet/<unit>, GET
     * /course/booklet/<level>, companion/COURSE.md "Printed sheets"): self-contained HTML, its fonts and fit.js inlined.
     * [PrintMissing] when the bridge has none (an older bridge, a unit it doesn't have).
     */
    suspend fun printPage(path: String): String = off {
        val (code, body) = exchange(request(path).header("Accept", "text/html").build())
        return@off when (code) {
            200 -> body
            404 -> throw PrintMissing()
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }
    /** The car's audio drills (GET /drills, lani.drill/v0); null from an older bridge without them (404): the app's own then. */
    suspend fun drills(): List<Drill>? = off {
        val (code, body) = content(request("/drills").build())
        return@off when (code) {
            200 -> Drills.parseList(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }
    suspend fun scenarios(): List<Scenario> = off { parseScenarios(get("/scenarios")) }
    /**
     * Close-up scenes of the village, resolved (companion/SCENES.md), as far as this app draws them: it says which arts
     * it has ([SceneArt.VERSION]); an app that doesn't say gets what the first arts drew.
     */
    suspend fun scenes(): List<SceneSpec> = off { remembered("/scenes?arts=${si.lanisce.lani.game.scene.SceneArt.VERSION}", ::parseScenes) }
    /**
     * The people of the village (companion/VILLAGERS.md). The phone's [time] of day says the app reads a line's `when`
     * (an older app gets the lines without it).
     */
    suspend fun villagers(time: TimeOfDay = TimeOfDay.now()): List<Villager> = off { remembered("/villagers?time=${time.name.lowercase()}", ::parseVillagers) }
    /**
     * A role-play with villager [id] played by the tutor (scenario id "villager:<id>"), built for the current friendship
     * and the phone's [time] of day (its opener a greeting for it: `?time=evening`).
     */
    suspend fun villagerScenario(id: String, time: TimeOfDay = TimeOfDay.now()): Scenario = off {
        json.decodeFromString(get("/villagers/$id/scenario?time=${time.name.lowercase()}"))
    }

    /**
     * The tutor's introductions of the people who joined the village (GET /arrivals, companion/VILLAGERS.md "Arrivals"),
     * as the node sends them; null from an older bridge without them (404). The culture pack's are bundled in the app.
     */
    suspend fun arrivals(): String? = off {
        val (code, body) = content(request("/arrivals").build())
        return@off when (code) {
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
    suspend fun culture(): CultureInfo? = off {
        val (code, body) = exchange(request("/culture").build())
        return@off when (code) {
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
    suspend fun lookup(word: String, line: String?, en: String?, language: String? = null, meaningIn: String? = null, nth: Int = 0): WordLookup? = off {
        val (code, body) = exchange(request(Words.url(config.baseUrl, word, line, en, language, meaningIn, nth).toString()).build())
        return@off when (code) {
            200 -> Words.parse(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * Each of [lines]' words with a case, as the colours draw them ("Skloni · Cases": POST /case-lens, data/CaseLens.kt), at
     * most [CaseLens.BATCH] lines of [CaseLens.MAX_CHARS] characters. Null from an older bridge without them (404) or one
     * that won't take the request (400): no colours then.
     */
    suspend fun cases(lines: List<String>): CasesAnswer? = off {
        val (code, text) = exchange(request("/case-lens").post(CaseLens.casesBody(lines).toString().toRequestBody(Http.jsonType)).build())
        return@off when (code) {
            200 -> CaseLens.parseCases(text)
            400, 404 -> null
            else -> throw IOException("HTTP $code: ${text.take(200)}")
        }
    }

    /**
     * The forms of [words] (lemmas, at most [WordFormsWire.BATCH]): GET /forms, companion/README.md "Word forms". Null
     * from an older bridge without them (404) or one that won't take the request (400): no form questions then.
     */
    suspend fun forms(words: List<String>): FormsAnswer? = off {
        val (code, body) = exchange(request(WordFormsWire.url(config.baseUrl, words).toString()).build())
        return@off when (code) {
            200 -> WordFormsWire.parse(body)
            400, 404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * Counting cards of [words] (lemma, its card's id), made by the node from the dictionary's forms, a few a day (POST
     * /counting, data/Counting.kt). Null from an older bridge without them (404) or one that won't take the request (400).
     */
    suspend fun counting(words: List<Pair<String, String>>): CountingAnswer? = off {
        val body = buildJsonObject {
            put("words", buildJsonArray { words.forEach { (w, id) -> add(buildJsonObject { put("word", w); put("item_id", id) }) } })
        }
        val (code, text) = exchange(request("/counting").post(body.toString().toRequestBody(Http.jsonType)).build())
        return@off when (code) {
            200 -> json.decodeFromString(CountingAnswer.serializer(), text)
            400, 404 -> null
            else -> throw IOException("HTTP $code: ${text.take(200)}")
        }
    }

    /**
     * Forms cards of [words] (lemma, its card's id; none: only the cards [introduced] pages bring back, data/FormsCard.kt),
     * made by the node from the dictionary's forms (POST /forms-cards): a few new a day, or at once when [asked] (a word's
     * "📋 Vse oblike"); [introduced]: the grammar pages introduced to the learner, so the node knows what is tested and
     * which cards come back soon. Null from an older bridge without them (404) or one that won't take the request (400).
     */
    suspend fun formsCards(words: List<Pair<String, String?>>, introduced: Collection<String>, asked: Boolean = false): FormsCardsAnswer? = off {
        val body = buildJsonObject {
            put("words", buildJsonArray { words.forEach { (w, id) -> add(buildJsonObject { put("word", w); id?.let { put("item_id", it) } }) } })
            put("introduced", buildJsonArray { introduced.sorted().take(200).forEach { add(JsonPrimitive(it)) } })
            if (asked) put("asked", true)
        }
        val (code, text) = exchange(request("/forms-cards").post(body.toString().toRequestBody(Http.jsonType)).build())
        return@off when (code) {
            200 -> json.decodeFromString(FormsCardsAnswer.serializer(), text)
            400, 404 -> null
            else -> throw IOException("HTTP $code: ${text.take(200)}")
        }
    }

    /**
     * Each of [words]' tables as its forms card would have them, to read (GET /forms-cards: the overview, "📋 Vse oblike ·
     * All forms"); nothing is made. Null from an older bridge without it (404) or one that won't take the request (400).
     */
    suspend fun formsTables(words: List<String>): FormsTablesAnswer? = off {
        val (code, text) = exchange(request(FormsCard.tablesUrl(config.baseUrl, words).toString()).build())
        return@off when (code) {
            200 -> FormsCard.parseTables(text)
            400, 404, 405 -> null
            else -> throw IOException("HTTP $code: ${text.take(200)}")
        }
    }

    /**
     * The tutor's explanation of [sentence]'s grammar, kept from an earlier "Ask the tutor" (GET /sentence,
     * publish_sentence_note), in [base] when there is one; a line of another town's [language] on a visit. Null when there
     * is none, and from an older bridge without it (404).
     */
    suspend fun sentenceNote(sentence: String, language: String? = null, base: String? = null): String? = off {
        val (code, body) = exchange(request(Sentences.url(config.baseUrl, sentence, language, base).toString()).build())
        return@off when (code) {
            200 -> Sentences.note(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * What POST /words takes as `from` (GET /words: "reading" too); null from an older bridge that doesn't say (404),
     * which takes [Words.OLD_SOURCES].
     */
    /** The words asked for that wait for the tutor's meaning (GET /words/requests); null from an older bridge without them. */
    suspend fun wordRequests(): List<WaitingWord>? = off {
        val (code, body) = exchange(request("/words/requests").build())
        return@off when (code) {
            200 -> Words.parseWaiting(body)
            404, 405 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    suspend fun wordSources(): Set<String>? = off {
        val (code, body) = exchange(request("/words").build())
        return@off when (code) {
            200 -> Words.parseSources(body)
            404, 405 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * The reading corner's readings (GET /readings: the culture pack's, then the tutor's; data/ReadingPractice.kt); null
     * from an older bridge without them (404).
     */
    suspend fun readings(): List<si.lanisce.lani.game.culture.ReadingFile>? = off { rememberedOrNone("/readings") { ReadingPractice.parseServed(it) } }

    /** The towns linked with this learner's (GET /towns, see data/Towns.kt); null from an older bridge without towns (404). */
    suspend fun towns(): TownsList? = off {
        val (code, body) = exchange(request("/towns").build())
        return@off when (code) {
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
    suspend fun townPart(id: String, kind: String, time: TimeOfDay = TimeOfDay.now()): Pair<Int, String> = off {
        content(
            request(
                "/towns/$id/$kind?time=${time.name.lowercase()}" + if (kind == Visits.SCENES) "&arts=${si.lanisce.lani.game.scene.SceneArt.VERSION}" else "",
            ).build(),
        )
    }

    /**
     * A visit's write to linked town [id], as answered: [kind] is [TownActs.HELP], [TownActs.GIFT] or [TownActs.TRADE]
     * (POST /towns/:id/<kind>, see data/TownActs.kt); the same [body] (its id) again is answered the same.
     */
    suspend fun townWrite(id: String, kind: String, body: JsonObject): Pair<Int, String> = off {
        exchange(request("/towns/$id/$kind").post(body.toString().toRequestBody(Http.jsonType)).build())
    }

    /**
     * A role-play on a visit to town [id] (POST /towns/:id/talk: {villager} or {market: {give, get}}), as answered; with
     * the phone's [time] of day, so the one met greets for it.
     */
    suspend fun townTalk(id: String, body: JsonObject, time: TimeOfDay = TimeOfDay.now()): Pair<Int, String> = off {
        val timed = JsonObject(body + ("time" to JsonPrimitive(time.name.lowercase())))
        return@off content(request("/towns/$id/talk").post(timed.toString().toRequestBody(Http.jsonType)).build())
    }

    /** What guests from linked towns brought this town (GET /towns/guests); null from an older bridge (404). */
    suspend fun townGuests(): List<GuestEntry>? = off {
        val (code, body) = exchange(request("/towns/guests").build())
        return@off when (code) {
            200 -> TownActs.parseGuests(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * The friendship with each linked town (GET /towns/friendship, see data/Friendship.kt): the level both towns agree on,
     * what it brings; null from an older bridge (404).
     */
    suspend fun friendships(): FriendshipList? = off {
        val (code, body) = exchange(request("/towns/friendship").build())
        return@off when (code) {
            200 -> Friendships.parse(body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /**
     * A friendship's write to linked town [id], as answered: [what] is "aid" (help against its trouble), "moves" (offer a
     * move), or "moves/<move>/accept" and "…/decline"; the same [body] (its id) again is answered the same.
     */
    suspend fun friendshipWrite(id: String, what: String, body: JsonObject = buildJsonObject { }): Pair<Int, String> = off {
        exchange(request("/towns/$id/$what").post(body.toString().toRequestBody(Http.jsonType)).build())
    }
    /**
     * Linked town [id]'s progress as it shares it with this learner (GET /towns/:id/progress, data/TownProgress.kt), as
     * answered: the status and body. [fresh]: asked again now, not the minute's copy.
     */
    suspend fun townProgress(id: String, fresh: Boolean = false): Pair<Int, String> = off {
        exchange(request("/towns/$id/progress" + if (fresh) "?fresh=1" else "").build())
    }

    /** PUT /towns/:id/sharing as answered: what this learner shares with town [id] ({share}), a weekly note of theirs ({weekly}). */
    suspend fun townSharing(id: String, body: JsonObject): Pair<Int, String> = off {
        exchange(request("/towns/$id/sharing").put(body.toString().toRequestBody(Http.jsonType)).build())
    }

    /** POST /towns/:id/challenges as answered: a challenge to town [id]'s learner ({id, type, text, hint?}; a retry sends the same id). */
    suspend fun townChallenge(id: String, body: JsonObject): Pair<Int, String> = off {
        exchange(request("/towns/$id/challenges").post(body.toString().toRequestBody(Http.jsonType)).build())
    }

    /** GET /towns/questions as answered (data/TownQuestions.kt): the status and body; an older bridge answers 404. */
    suspend fun townQuestions(): Pair<Int, String> = off { exchange(request("/towns/questions").build()) }

    /** POST /towns/invite as answered: the status and body (a child's bridge answers 403). */
    suspend fun townInvite(): Pair<Int, String> = off { exchange(request("/towns/invite").post("{}".toRequestBody(Http.jsonType)).build()) }

    /** POST /towns/accept with an invitation's text, as answered: the status and body. */
    suspend fun townAccept(invite: String): Pair<Int, String> = off {
        exchange(request("/towns/accept").post(buildJsonObject { put("invite", invite) }.toString().toRequestBody(Http.jsonType)).build())
    }

    /** GET /towns/children as answered (data/Towns.kt [TownChildren]): a parent's children's towns; an older bridge answers 404. */
    suspend fun townChildren(): Pair<Int, String> = off { exchange(request("/towns/children").build()) }

    /**
     * A POST to the routes of a child's links that wait for a parent ([path]: /towns/requests/<id>/withdraw,
     * /towns/children/<town>/link, /towns/children/<town>/requests/<id>), as answered.
     */
    suspend fun townPost(path: String, body: JsonObject = buildJsonObject { }): Pair<Int, String> = off {
        exchange(request(path).post(body.toString().toRequestBody(Http.jsonType)).build())
    }

    suspend fun latestRelease(): Release? = off { runCatching { json.decodeFromString<Release>(get("/app/latest")) }.getOrNull() }

    /** The village, or null when the node has none yet (404). */
    suspend fun game(): GameSnapshot? = off {
        val (code, body) = exchange(request("/game").build())
        return@off when (code) {
            200 -> json.decodeFromString(GameSnapshot.serializer(), body)
            404 -> null
            else -> throw IOException("HTTP $code: ${body.take(200)}")
        }
    }

    /** Writes the village based on [rev]; a 409 hands back the node's newer copy. */
    suspend fun putGame(rev: Long, state: GameState): GamePut = off {
        val body = json.encodeToString(GameSnapshot.serializer(), GameSnapshot(rev, state))
        val (code, raw) = exchange(request("/game").put(body.toRequestBody(Http.jsonType)).build())
        return@off when (code) {
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
    suspend fun backlog(since: Long): List<Pair<Long, BridgeEvent>> = off {
        val arr = json.parseToJsonElement(get("/events/backlog?since=$since")) as JsonArray
        return@off arr.mapNotNull { e ->
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

    /**
     * The developer's data for the node (app/DevUpload): POST /dev/shadow (the shadow's export, JSON) or /dev/logs (the
     * app's log, text) with [body] as [type]; the status code.
     */
    suspend fun devUpload(path: String, body: String, type: String): Int = off {
        exchange(request(path).post(body.toRequestBody(type.toMediaType())).build()).first
    }

    suspend fun message(kind: String, conversationId: String, text: String, data: JsonObject? = null) = off {
        post("/message", buildJsonObject {
            put("kind", kind)
            put("conversation_id", conversationId)
            put("text", text)
            if (data != null) put("data", data)
        })
    }

    suspend fun permission(requestId: String, allow: Boolean) = off {
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
