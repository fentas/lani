package si.lanisce.lani.tutor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import si.lanisce.lani.data.BridgeConfig
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/** A request the shadow's bridge client refused: in shadow mode nothing but a GET goes to the bridge. */
class ShadowWriteRefused(method: String) : IOException("shadow mode: the bridge gets GETs only, not $method")

/**
 * Refuses every request that isn't a GET (and any that carries the API key's header), before it leaves the phone: the
 * shadow's guard that nothing of it reaches the bridge but reads.
 */
object GetOnly : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val r = chain.request()
        if (r.method != "GET" || r.body != null) throw ShadowWriteRefused(r.method)
        if (r.header("x-api-key") != null) throw ShadowWriteRefused("a request with the API key")
        return chain.proceed(r)
    }
}

/**
 * The shadow's own client of the paired bridge (the app's existing auth: its bearer token), for reads only: GET
 * routes, with [GetOnly] on the client itself, so no code path through it can write.
 */
class ShadowBridge(private val config: BridgeConfig, base: OkHttpClient = defaultClient) {
    private val client = base.newBuilder().apply {
        interceptors().removeAll { it === GetOnly }
        interceptors().add(0, GetOnly) // first: before anything else sees the request
    }.build()

    /** GET [path] (with [query]): the status and body. */
    suspend fun get(path: String, query: Map<String, String> = emptyMap()): Pair<Int, String> = withContext(Dispatchers.IO) {
        val url = (config.baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${config.token}").get().build()
        client.newCall(req).execute().use { r -> r.code to r.body?.string().orEmpty() }
    }

    /** Only to show the guard holds: any other method is refused by the client ([GetOnly]). */
    internal suspend fun send(method: String, path: String, body: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(config.baseUrl.trimEnd('/') + path).header("Authorization", "Bearer ${config.token}")
            .method(method, if (method == "GET" || method == "HEAD") null else body.toRequestBody())
            .build()
        client.newCall(req).execute().use { r -> r.code to r.body?.string().orEmpty() }
    }

    companion object {
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        }
    }
}

/** What a read tool gives the model: [text], or an error the model reads ([isError]). */
data class ReadResult(val text: String, val isError: Boolean = false)

/** The read tools' answers in shadow mode. */
fun interface ReadSource {
    suspend fun read(name: String, input: JsonObject): ReadResult

    /**
     * [read], with the day's dry-run card [writes] laid over the deck first ([CardOverlay]: what the tutor "saved" shows
     * in its later `card` reads, as live); a source without a deck of its own reads as [read].
     */
    suspend fun read(name: String, input: JsonObject, writes: List<JsonObject>): ReadResult = read(name, input)
}

/**
 * The read tools over the paired bridge's GET routes (/state, /game, /packs, /grammar, /lookup), summed up for the
 * model as the bridge's tools sum them up; `recent_answers` from what the shadow saw itself (the hosted app keeps the
 * answers log, plan 5). A result is cut at [max] characters.
 */
class BridgeReads(
    private val bridge: () -> ShadowBridge?,
    private val events: () -> List<ShadowEvent>,
    private val max: Int = 12_000,
    /** The learner's day now (YYYY-MM-DD): a dry-run write's date in [CardOverlay]. */
    private val today: () -> String = { java.time.LocalDate.now().toString() },
) : ReadSource {
    override suspend fun read(name: String, input: JsonObject): ReadResult = read(name, input, emptyList())

    override suspend fun read(name: String, input: JsonObject, writes: List<JsonObject>): ReadResult = try {
        val r = when (name) {
            "learner_state" -> get("/state", input.s("language")?.let { mapOf("language" to it) }.orEmpty()) { Summaries.learnerState(it) }
            "get_village" -> get("/game") { Summaries.village(it) }
            "list_packs" -> get("/packs") { Summaries.packs(it) }
            "list_grammar" -> get("/grammar") { Summaries.grammar(it) }
            "get_grammar" -> {
                val id = input.s("id")?.takeIf { Regex("[a-z0-9-]{1,64}").matches(it) } ?: return ReadResult("id: a grammar page's id (list_grammar lists them)", true)
                get("/grammar/$id") { it }
            }
            "dictionary_lookup" -> {
                val w = input.s("word")?.trim()?.takeIf { it.isNotEmpty() } ?: return ReadResult("word: required", true)
                val q = buildMap { put("w", w); input.s("line")?.let { put("line", it.take(300)) } }
                get("/lookup", q) { "<untrusted_dictionary_content source=\"Lani's dictionary\">\n$it\n</untrusted_dictionary_content>" }
            }
            "card" -> get("/state") { Summaries.card(it, input, writes, today()) }
            "recent_answers" -> ReadResult(Summaries.recentAnswers(events(), input))
            else -> ReadResult("$name: not a read tool", true)
        }
        if (r.text.length > max) r.copy(text = r.text.take(max) + "\n… (cut at $max characters)") else r
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ReadResult("$name failed: ${e.message ?: e.javaClass.simpleName}", true)
    }

    private suspend fun get(path: String, query: Map<String, String> = emptyMap(), sum: (String) -> String): ReadResult {
        val b = bridge() ?: return ReadResult("not connected to the tutor's node: no data to read", true)
        val (code, body) = b.get(path, query)
        return when (code) {
            in 200..299 -> ReadResult(sum(body))
            404 -> ReadResult("not found (HTTP 404)", true)
            else -> ReadResult("HTTP $code: ${body.take(200)}", true)
        }
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/** The read tools' summaries of the bridge's answers: small enough for a phone-sized thread. */
object Summaries {
    private fun obj(raw: String): JsonObject? = runCatching { tutorJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
    private fun pretty(e: JsonElement): String = prettyJson.encodeToString(JsonElement.serializer(), e)
    private val prettyJson = kotlinx.serialization.json.Json { prettyPrint = true }

    /** read-db.py's output summed up: the computed fields, the profile's learner, streak, the top mistakes, mastery, the last sessions. */
    fun learnerState(raw: String): String {
        val o = obj(raw) ?: return raw.take(4000)
        val dbs = o["databases"] as? JsonObject
        val profile = dbs?.get("learner_profile") as? JsonObject
        val mistakes = ((dbs?.get("mistakes_db") as? JsonObject)?.get("error_patterns") as? JsonObject).orEmpty()
        val top = mistakes.entries.sortedByDescending { (_, p) -> ((p as? JsonObject)?.get("frequency") as? JsonPrimitive)?.intOrNull ?: 0 }.take(8)
        val sessions = ((dbs?.get("session_log") as? JsonObject)?.get("sessions") as? JsonArray).orEmpty().takeLast(3)
        val out = buildJsonObject {
            o["computed"]?.let { put("computed", it) }
            o["language"]?.let { put("language", it) }
            o["languages"]?.let { put("languages", it) }
            profile?.get("learner")?.let { put("learner", it) }
            listOf("current_streak_days", "total_sessions", "total_study_minutes").forEach { k -> profile?.get(k)?.let { put(k, it) } }
            ((dbs?.get("progress_db") as? JsonObject)?.get("overall_stats"))?.let { put("overall_stats", it) }
            ((dbs?.get("mastery_db") as? JsonObject)?.get("skills"))?.let { put("mastery", it) }
            put(
                "top_mistakes",
                buildJsonObject {
                    top.forEach { (id, p) ->
                        val po = p as? JsonObject ?: return@forEach
                        put(id, buildJsonObject {
                            listOf("category", "subcategory", "frequency", "mastery_level", "last_occurred", "consecutive_incorrect").forEach { k -> po[k]?.let { put(k, it) } }
                            (po["examples"] as? JsonArray)?.lastOrNull()?.let { put("last_example", it) }
                        })
                    }
                },
            )
            put("recent_sessions", JsonArray(sessions))
            val items = ((dbs?.get("spaced_repetition") as? JsonObject)?.get("items") as? JsonObject)
            items?.let { put("review_items", it.size) }
        }
        return pretty(out)
    }

    /**
     * The village (GET /game) summed up: its age, resources, buildings, people, quests, the event on (the bridge's
     * get_village adds the calendar and the chronicle). No read's text says it is the shadow's: the tutor acts as live.
     */
    fun village(raw: String): String {
        val s = (obj(raw)?.get("state") as? JsonObject) ?: return raw.take(4000)
        val out = buildJsonObject {
            listOf("age", "resources", "help", "event", "founded", "name").forEach { k -> s[k]?.let { put(k, it) } }
            (s["buildings"] as? JsonArray)?.let { b ->
                put("buildings", buildJsonArray { b.forEach { e -> (e as? JsonObject)?.let { add(buildJsonObject { listOf("type", "level", "damaged").forEach { k -> it[k]?.let { v -> put(k, v) } } }) } } })
            }
            (s["residents"] as? JsonArray)?.let { r ->
                put("residents", buildJsonArray { r.forEach { e -> (e as? JsonObject)?.let { add(buildJsonObject { listOf("id", "name", "role").forEach { k -> it[k]?.let { v -> put(k, v) } } }) } } })
            }
            (s["quests"] as? JsonArray)?.let { put("quests", JsonArray(it.take(8))) }
            (s["projects"])?.let { put("projects", it) }
        }
        return pretty(out)
    }

    /** GET /packs: each pack's id, title, level and progress. */
    fun packs(raw: String): String {
        val list = runCatching { tutorJson.parseToJsonElement(raw) as JsonArray }.getOrNull() ?: return raw.take(4000)
        return pretty(JsonArray(list.map { e -> (e as? JsonObject)?.let { JsonObject(it.filterKeys { k -> k !in setOf("words", "image") }) } ?: e }))
    }

    /** GET /grammar: each page's id, level, title and tags (get_grammar has a page whole). */
    fun grammar(raw: String): String {
        val list = runCatching { tutorJson.parseToJsonElement(raw) as JsonArray }.getOrNull() ?: return raw.take(4000)
        return pretty(JsonArray(list.mapNotNull { e ->
            (e as? JsonObject)?.let { p -> buildJsonObject { listOf("id", "level", "title", "target", "emoji", "tags", "modules").forEach { k -> p[k]?.let { put(k, it) } } } }
        }))
    }

    private fun fold(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /**
     * card's reads (find, show, overlaps) over the review deck (GET /state's spaced_repetition): the items as kept, with
     * the day's dry-run card [writes] laid over them ([CardOverlay], on [today]).
     */
    fun card(raw: String, input: JsonObject, writes: List<JsonObject> = emptyList(), today: String = java.time.LocalDate.now().toString()): String {
        val kept = ((((obj(raw)?.get("databases") as? JsonObject)?.get("spaced_repetition") as? JsonObject)?.get("items")) as? JsonObject) ?: JsonObject(emptyMap())
        val items = CardOverlay.apply(kept, writes, today)
        val action = (input["action"] as? JsonPrimitive)?.content
        val id = (input["card_id"] as? JsonPrimitive)?.content
        val text = (input["text"] as? JsonPrimitive)?.content?.let(::fold)
        val found = when {
            id != null && action == "show" -> items[id]?.let { listOf(id to it) }.orEmpty()
            text != null -> items.entries.filter { (k, v) -> fold(k + " " + v.toString()).contains(text) }.map { it.key to it.value }
            id != null -> items.entries.filter { (k, v) -> k == id || fold(v.toString()).contains(fold(id.substringAfter('_'))) }.map { it.key to it.value }
            else -> emptyList()
        }.take(10)
        if (found.isEmpty()) return "no card ${id ?: ""}${text?.let { " with \"$it\"" } ?: ""} in the deck (${items.size} cards)"
        return "${found.size} card(s) as the deck keeps them:\n" + pretty(JsonObject(found.toMap()))
    }

    /** recent_answers from the review events the shadow saw (their answers), newest first. */
    fun recentAnswers(events: List<ShadowEvent>, input: JsonObject): String {
        val id = (input["card_id"] as? JsonPrimitive)?.content
        val text = (input["text"] as? JsonPrimitive)?.content?.let(::fold)
        val wrong = (input["wrong_only"] as? JsonPrimitive)?.booleanOrNull == true
        val limit = ((input["limit"] as? JsonPrimitive)?.intOrNull ?: 20).coerceIn(1, 100)
        val answers = events.filter { it.kind == "review_done" || it.kind == "dialog_words" }.sortedByDescending { it.at }.flatMap { e ->
            val qualities = (e.data?.get("results") as? JsonArray).orEmpty().mapNotNull { r ->
                val o = r as? JsonObject ?: return@mapNotNull null
                (o["item_id"] as? JsonPrimitive)?.content?.let { it to ((o["quality"] as? JsonPrimitive)?.intOrNull ?: -1) }
            }.toMap()
            (e.data?.get("answers") as? JsonArray).orEmpty().mapNotNull { a ->
                val o = a as? JsonObject ?: return@mapNotNull null
                val item = (o["item_id"] as? JsonPrimitive)?.content ?: (o["card_id"] as? JsonPrimitive)?.content
                Triple(e, item, JsonObject(o + ("quality" to JsonPrimitive(qualities[item] ?: -1))))
            }
        }.filter { (_, item, o) ->
            (id == null || item == id) && (text == null || fold(o.toString()).contains(text)) &&
                (!wrong || ((o["quality"] as? JsonPrimitive)?.intOrNull ?: 5) < 3)
        }.take(limit)
        if (answers.isEmpty()) return "No answers${id?.let { " for $it" } ?: ""}${text?.let { " with \"$it\"" } ?: ""} in the recent reviews."
        return "${answers.size} answer(s), newest first:\n" + answers.joinToString("\n") { (e, _, o) -> "${java.time.Instant.ofEpochMilli(e.at)} ${e.data?.get("from") ?: ""} $o" }
    }
}
