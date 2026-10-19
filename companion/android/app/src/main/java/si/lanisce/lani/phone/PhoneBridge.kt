package si.lanisce.lani.phone

import com.dokar.quickjs.MemoryUsage
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.evaluate
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.typeOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.http.HttpMethod

/**
 * The bridge in the app (docs/plans/05-built/phone-runtime.md): the bridge's TypeScript, bundled (companion/bridge/phone:
 * bridge-phone.js), in one QuickJS runtime (quickjs-kt) on a thread of its own, made on first use. The app talks to it
 * as to a node: [handle] takes a request of the bridge's routes and gives the answer (an event stream part by part);
 * the in-app tutor calls its tools ([callTool]) and hears its channel's events ([notifications]), the content and meta
 * Claude Code would get.
 *
 * Node's built-ins in the bundle are shims over the host this class defines in the runtime (`globalThis.__lani`): files
 * only under [Config.roots] ([HostFs]), Java's crypto ([HostCrypto]), OkHttp for fetch, timers on the runtime's thread,
 * the clock and time zone, the log. Nothing blocks the runtime's thread: a timer, a fetch, a stream waits outside it and
 * calls back into the bundle when it's time, each call a short evaluation.
 */
class PhoneBridge(private val config: Config) : Closeable {

    class Config(
        /** The curated content, as the repository has it (companion/…): `<filesDir>/lani/project`, unpacked from the APK. */
        val projectDir: File,
        /** The learner's data repository (the 6 databases, app/): `<filesDir>/lani/data`. */
        val dataDir: File,
        /** The bridge's home (HOME, its caches and tmp): `<filesDir>/lani`. */
        val home: File,
        /** Where the bridge may read and write: [home] (writable) and [projectDir] (read only), unless given. */
        val roots: List<HostFs.Root> = listOf(HostFs.Root(home, writable = true), HostFs.Root(projectDir, writable = false)),
        /** The bundle's source (the APK's assets/phone/bridge-phone.js). */
        val bundle: () -> String,
        /** More environment for the bridge (process.env), over the defaults ([environment]). */
        val env: Map<String, String> = emptyMap(),
        /** The bridge's log (its console, its `log`): level (info, warn, error, debug) and text. */
        val log: (level: String, text: String) -> Unit = { _, _ -> },
        /** QuickJS's heap limit, bytes. */
        val memoryLimit: Long = 384L shl 20,
        /** QuickJS's stack limit, bytes (the runtime's thread has [threadStack]). */
        val maxStack: Long = 4L shl 20,
        val threadStack: Long = 16L shl 20,
        /** A single evaluation running longer is interrupted (a loop gone wrong); 0: never. */
        val evaluationTimeoutMs: Long = 60_000,
        /** How long a request or a tool call may take before it fails. */
        val requestTimeoutMs: Long = 120_000,
        /**
         * fetch's client. Its timeouts are Bun's fetch's (none but an idle one of 5 minutes: the relay's mail waits on an
         * answer longer than OkHttp's 10 s), a request's own from its AbortSignal.timeout.
         */
        val http: OkHttpClient = defaultHttp(),
        /** The token the bridge takes as the app's (LANI_BRIDGE_TOKEN): added to every request that has none. */
        val token: String = randomToken(),
        /** Where the compiled bundle is kept between starts (QuickJS bytecode, by the source's hash); none: compiled each time. */
        val bytecodeDir: File? = null,
    )

    class Request(val method: String, val url: String, val headers: List<Pair<String, String>> = emptyList(), val body: ByteArray? = null) {
        companion object {
            fun get(url: String) = Request("GET", url)
            fun json(method: String, url: String, json: String) = Request(method, url, listOf("content-type" to "application/json"), json.toByteArray())
        }
    }

    /** An answer: its status, headers and body; an event stream's parts come through [stream] instead (collected once). */
    class Response(val status: Int, val headers: List<Pair<String, String>>, val body: ByteArray, val stream: Flow<ByteArray>?) {
        val text: String get() = body.decodeToString()
        fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
    }

    /** An event into the in-app tutor's session: what Claude Code's channel gets (`<channel source="lani" …>`). */
    data class ChannelEvent(val content: String, val meta: Map<String, String>)

    /** A tool's result ([ToolResult] of the bridge): its text and whether it is an error; [json] as the bridge made it. */
    data class ToolResult(val text: String, val isError: Boolean, val json: String)

    /** The bridge loaded: how long the bundle took (compiled or from bytecode), and what the bridge said of itself. */
    data class Boot(val loadMs: Double, val bootMs: Double, val fromBytecode: Boolean, val summary: String)

    private val executor = ScheduledThreadPoolExecutor(1) { r -> Thread(null, r, "lani-phone-bridge", config.threadStack).apply { isDaemon = true } }
        .apply { removeOnCancelPolicy = true }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, e -> log("error", "phone bridge: ${e.stackTraceToString()}") })
    private val fs = HostFs(config.roots)
    private val json = Json { ignoreUnknownKeys = true }
    private val ids = AtomicLong()
    private val starting = Mutex()

    private var js: QuickJs? = null
    private var boot: Boot? = null
    private var closed = false

    private class Pending(val request: Request, val answer: CompletableDeferred<Response> = CompletableDeferred())
    private class PendingCall(val name: String, val args: String, val result: CompletableDeferred<ToolResult> = CompletableDeferred())
    private class Fetched(val head: String, val body: ByteArray?)

    private val requests = ConcurrentHashMap<Long, Pending>()
    private val streams = ConcurrentHashMap<Long, Channel<ByteArray>>()
    private val calls = ConcurrentHashMap<Long, PendingCall>()
    private val timers = ConcurrentHashMap<Long, ScheduledFuture<*>>()
    private val fetches = ConcurrentHashMap<Long, Call>()
    private val fetched = ConcurrentHashMap<Long, Fetched>()
    private val events = Channel<ChannelEvent>(Channel.UNLIMITED)

    /** The channel's events for the in-app tutor, in order; held until collected (one collector). */
    val notifications: Flow<ChannelEvent> = events.receiveAsFlow()

    private fun log(level: String, text: String) = config.log(level, text)

    /** The environment the bridge sees: its home and its caches under [Config.home], the project, the data, the token. */
    fun environment(): Map<String, String> {
        val home = config.home.path
        return linkedMapOf(
            "HOME" to home,
            "TMPDIR" to "$home/tmp",
            "LANI_CONFIG_DIR" to "$home/config",
            "XDG_CACHE_HOME" to "$home/cache",
            "XDG_STATE_HOME" to "$home/state",
            "XDG_DATA_HOME" to "$home/share",
            "CLAUDE_PROJECT_DIR" to config.projectDir.path,
            "LANI_DATA_DIR" to config.dataDir.path,
            "LANI_BRIDGE_TOKEN" to config.token,
            // no voices of one's own are designed on the phone (no voice store, no ElevenLabs)
            "LANI_VOICE_DESIGN" to "off",
            "TZ" to TimeZone.getDefault().id,
        ) + config.env
    }

    // --- the runtime -----------------------------------------------------------------------------------------------------

    private suspend fun <T> onThread(block: suspend () -> T): T = withContext(dispatcher) { block() }

    /** Runs [code] in the runtime (on its thread); a failure of the bundle's own is logged, not thrown. */
    private suspend fun eval(code: String): Any? = onThread {
        val q = js ?: return@onThread null
        try {
            q.evaluate<Any?>(code, "host.js", false)
        } catch (e: Exception) {
            if (!closed) log("error", "phone bridge: $code: ${e.message}")
            null
        }
    }

    /** The bundle loaded and the bridge built, once (later calls wait for the first); the features' work started. */
    suspend fun start(): Boot = starting.withLock {
        boot ?: onThread {
            check(!closed) { "the phone bridge is stopped" }
            File(config.home, "tmp").mkdirs()
            val q = QuickJs.create(dispatcher)
            q.memoryLimit = config.memoryLimit
            q.maxStackSize = config.maxStack
            q.evaluationTimeoutMillis = config.evaluationTimeoutMs
            defineHost(q)
            js = q
            val t0 = System.nanoTime()
            val (fromBytecode, summary, t1) = try {
                val fromBytecode = load(q, config.bundle())
                val t1 = System.nanoTime()
                val summary = q.evaluate<String>("__laniPhone.boot()", typeOf<String>(), "boot.js", false)
                q.evaluate<Any?>("__laniPhone.start()", typeOf<Any?>(), "start.js", false)
                Triple(fromBytecode, summary, t1)
            } catch (e: Throwable) {
                // a bundle that doesn't load or boot: the runtime goes, the next call tries again
                js = null
                q.close()
                throw e
            }
            val t2 = System.nanoTime()
            Boot((t1 - t0) / 1e6, (t2 - t1) / 1e6, fromBytecode, summary).also {
                boot = it
                log("info", "phone bridge: bundle ${"%.0f".format(it.loadMs)} ms${if (fromBytecode) " (bytecode)" else ""}, boot ${"%.0f".format(it.bootMs)} ms: $summary")
            }
        }
    }

    /** Evaluates the bundle: from its bytecode when [Config.bytecodeDir] has it for this source, else compiled (and kept). */
    private suspend fun load(q: QuickJs, source: String): Boolean {
        val dir = config.bytecodeDir ?: run {
            q.evaluate<Any?>(source, typeOf<Any?>(), "bridge-phone.js", false)
            return false
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        val file = File(dir, "bridge-phone-$hash.qbc")
        if (file.isFile) {
            try {
                q.evaluate<Any?>(file.readBytes(), typeOf<Any?>())
                return true
            } catch (e: Exception) {
                log("warn", "phone bridge: the kept bytecode didn't load (${e.message}): compiling")
                file.delete()
            }
        }
        val code = q.compile(source, "bridge-phone.js", false)
        dir.mkdirs()
        dir.listFiles { f -> f.name.startsWith("bridge-phone-") && f.name.endsWith(".qbc") }?.forEach { it.delete() }
        File(dir, "${file.name}.tmp").apply { writeBytes(code) }.renameTo(file)
        q.evaluate<Any?>(code, typeOf<Any?>())
        return false
    }

    /** The request's answer, as a node's bridge would give it over HTTP (the token added when [request] has none). */
    suspend fun handle(request: Request): Response {
        start()
        val id = ids.incrementAndGet()
        val headers = if (request.headers.any { it.first.equals("authorization", ignoreCase = true) }) request.headers else request.headers + ("authorization" to "Bearer ${config.token}")
        val p = Pending(Request(request.method.uppercase(), absolute(request.url), headers, request.body))
        requests[id] = p
        try {
            eval("__laniPhone.handle($id)")
            return withTimeout(config.requestTimeoutMs) { p.answer.await() }
        } finally {
            requests.remove(id)
        }
    }

    /** [url] as the bridge's router reads it: absolute, percent-encoded (a path alone is the phone's own origin). */
    private fun absolute(url: String): String = (if (url.startsWith("/")) "http://lani.phone$url" else url).toHttpUrl().toString()

    /** The bridge's tool [name] with [args] (a JSON object), as Claude Code calls it over MCP. */
    suspend fun callTool(name: String, args: String = "{}"): ToolResult {
        start()
        val id = ids.incrementAndGet()
        val c = PendingCall(name, args)
        calls[id] = c
        try {
            eval("__laniPhone.call($id)")
            return withTimeout(config.requestTimeoutMs) { c.result.await() }
        } finally {
            calls.remove(id)
        }
    }

    /** The tools as the session sees them: JSON [{name, description, inputSchema}]. */
    suspend fun tools(): String = start().let { eval("__laniPhone.tools()") as String }

    /** The channel's instructions as Claude Code gets them (the rules that fit its limit). */
    suspend fun instructions(): String = start().let { eval("__laniPhone.instructions()") as String }

    /** The whole guide (channel_guide's text). */
    suspend fun guide(): String = start().let { eval("__laniPhone.guide()") as String }

    /** A new tutor session: its first conversation's event asks for the guide again (as a new Claude Code session's does). */
    suspend fun newSession() {
        start()
        eval("__laniPhone.attach()")
    }

    /** What waits in the bundle's loop: JSON {timers, fetches, streams}. */
    suspend fun pending(): String = start().let { eval("__laniPhone.pending()") as String }

    /** QuickJS's heap now (after a collection when [gc]). */
    suspend fun memory(gc: Boolean = false): MemoryUsage = onThread {
        val q = js ?: error("the phone bridge isn't started")
        if (gc) q.gc()
        q.memoryUsage
    }

    /** Stops what runs in the runtime now (an evaluation gone on too long). */
    fun interrupt() {
        js?.interruptEvaluation()
    }

    /** The bridge ends: its events kept, the streams closed, the timers and fetches let go, the runtime closed. */
    fun stop() {
        if (closed) return
        runBlocking { if (boot != null) eval("__laniPhone.stop()") }
        closed = true
        timers.values.forEach { it.cancel(false) }
        timers.clear()
        fetches.values.forEach { it.cancel() }
        fetches.clear()
        streams.values.forEach { it.close() }
        streams.clear()
        requests.values.forEach { it.answer.completeExceptionally(IOException("the phone bridge stopped")) }
        calls.values.forEach { it.result.completeExceptionally(IOException("the phone bridge stopped")) }
        events.close()
        runBlocking(dispatcher) { js?.close() }
        js = null
        scope.cancel()
        executor.shutdown()
    }

    override fun close() = stop()

    // --- the host: globalThis.__lani (companion/bridge/phone/shims/host.ts) -------------------------------------------------

    private fun Any?.long(): Long = (this as Number).toLong()
    private fun Any?.str(): String = this as String
    private fun Any?.bytes(): ByteArray? = this as ByteArray?

    private fun pairs(list: List<Pair<String, String>>) = buildJsonArray { for ((k, v) in list) add(JsonArray(listOf(JsonPrimitive(k), JsonPrimitive(v)))) }

    private fun pairsOf(text: String): List<Pair<String, String>> =
        json.parseToJsonElement(text).jsonArray.map { it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content }

    private fun defineHost(q: QuickJs) = q.define("__lani") {
        // files
        function("fsReadText") { a -> fs.readText(a[0].str()) }
        function("fsReadBytes") { a -> fs.readBytes(a[0].str()) }
        function("fsWriteText") { a -> fs.write(a[0].str(), a[1].str().toByteArray(), a[2] as Boolean, a[3] as Boolean) }
        function("fsWriteBytes") { a -> fs.write(a[0].str(), a[1].bytes() ?: ByteArray(0), a[2] as Boolean, a[3] as Boolean) }
        function("fsExists") { a -> fs.exists(a[0].str()) }
        function("fsStat") { a -> fs.stat(a[0].str()) }
        function("fsReaddir") { a -> fs.readdir(a[0].str()) }
        function("fsMkdir") { a -> fs.mkdir(a[0].str(), a[1] as Boolean) }
        function("fsRename") { a -> fs.rename(a[0].str(), a[1].str()) }
        function("fsUnlink") { a -> fs.unlink(a[0].str()) }
        function("fsRmdir") { a -> fs.rmdir(a[0].str()) }
        function("fsRm") { a -> fs.rm(a[0].str(), a[1] as Boolean, a[2] as Boolean) }
        function("fsCopy") { a -> fs.copy(a[0].str(), a[1].str(), a[2] as Boolean) }
        function("fsRealpath") { a -> fs.realpath(a[0].str()) }
        function("fsChmod") { a -> fs.chmod(a[0].str()) }
        // time
        function("tz") { _ -> TimeZone.getDefault().id }
        function("tzOffset") { a -> -TimeZone.getDefault().getOffset(a[0].long()) / 60_000 }
        // crypto
        function("randomBytes") { a -> HostCrypto.randomBytes(a[0].long().toInt()) }
        function("randomUUID") { _ -> java.util.UUID.randomUUID().toString() }
        function("hash") { a -> HostCrypto.hash(a[0].str(), a[1].bytes() ?: ByteArray(0)) }
        function("hmac") { a -> HostCrypto.hmac(a[0].str(), a[1].bytes() ?: ByteArray(0), a[2].bytes() ?: ByteArray(0)) }
        function("ecGenerate") { _ -> HostCrypto.ecGenerate() }
        function("ecPublic") { a -> HostCrypto.ecPublic(a[0].str()) }
        function("ecSign") { a -> HostCrypto.ecSign(a[0].str(), a[1].bytes() ?: ByteArray(0)) }
        function("ecVerify") { a -> HostCrypto.ecVerify(a[0].bytes() ?: ByteArray(0), a[1].bytes() ?: ByteArray(0), a[2].bytes() ?: ByteArray(0)) }
        // text and bytes
        function("utf8Encode") { a -> HostText.utf8Encode(a[0].str()) }
        function("utf8Decode") { a -> HostText.utf8Decode(a[0].bytes() ?: ByteArray(0)) }
        function("base64Encode") { a -> HostText.base64Encode(a[0].bytes() ?: ByteArray(0), a[1] as Boolean) }
        function("base64Decode") { a -> HostText.base64Decode(a[0].str()) }
        function("gunzip") { a -> HostText.gunzip(a[0].bytes() ?: ByteArray(0)) }
        function("gzip") { a -> HostText.gzip(a[0].bytes() ?: ByteArray(0)) }
        function("collate") { a -> HostText.collate(a[0].str(), a[1].str(), a[2].str()) }
        // the loop
        function("setTimer") { a -> setTimer(a[0].long(), a[1].long()) }
        function("clearTimer") { a -> timers.remove(a[0].long())?.cancel(false); Unit }
        function("fetch") { a -> startFetch(a[0].long(), a[1].str(), a[2].str(), a[3].str(), a[4].bytes(), a[5].long(), a[6].str()) }
        function("fetchCancel") { a -> fetches.remove(a[0].long())?.cancel(); Unit }
        function("fetchResult") { a -> fetched[a[0].long()]?.head ?: """{"error":"no answer"}""" }
        function("fetchBody") { a -> fetched.remove(a[0].long())?.body }
        function("sleep") { a -> Thread.sleep(a[0].long()) }
        // the app's side
        function("config") { _ ->
            buildJsonObject {
                put("projectDir", config.projectDir.path)
                put("dataDir", config.dataDir.path)
                put("home", config.home.path)
                // the app's process (a JVM test has none of Android's: 1)
                put("pid", runCatching { android.os.Process.myPid() }.getOrDefault(1))
            }.toString()
        }
        function("env") { _ -> JsonObject(environment().mapValues { JsonPrimitive(it.value) }).toString() }
        function("log") { a -> log(a[0].str(), a[1].str()) }
        function("request") { a ->
            val r = requests[a[0].long()]?.request ?: error("no request ${a[0]}")
            buildJsonObject {
                put("method", r.method)
                put("url", r.url)
                put("headers", pairs(r.headers))
            }.toString()
        }
        function("requestBody") { a -> requests[a[0].long()]?.request?.body }
        function("respond") { a -> respond(a[0].long(), a[1].str(), a[2] as String?, a[3].bytes()) }
        function("chunk") { a -> streams[a[0].long()]?.trySend((a[1] as String?)?.toByteArray() ?: a[2].bytes() ?: ByteArray(0)); Unit }
        function("end") { a -> streams.remove(a[0].long())?.close((a[1] as String?)?.let { IOException(it) }); Unit }
        function("toolCall") { a ->
            val c = calls[a[0].long()] ?: error("no tool call ${a[0]}")
            buildJsonObject {
                put("name", c.name)
                put("args", json.parseToJsonElement(c.args))
            }.toString()
        }
        function("toolDone") { a ->
            val r = json.parseToJsonElement(a[1].str()).jsonObject
            val text = r["content"]?.jsonArray?.joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" } ?: ""
            calls[a[0].long()]?.result?.complete(ToolResult(text, r["isError"]?.jsonPrimitive?.boolean ?: false, a[1].str()))
            Unit
        }
        function("channel") { a ->
            val meta = json.parseToJsonElement(a[1].str()).jsonObject.mapValues { it.value.jsonPrimitive.content }
            events.trySend(ChannelEvent(a[0].str(), meta))
            Unit
        }
    }

    private fun setTimer(id: Long, ms: Long) {
        timers.remove(id)?.cancel(false)
        if (closed) return
        timers[id] = executor.schedule({
            timers.remove(id)
            scope.launch { eval("__laniLoop.timer($id)") }
        }, ms, TimeUnit.MILLISECONDS)
    }

    private fun respond(id: Long, head: String, text: String?, bytes: ByteArray?) {
        val p = requests[id] ?: return
        val h = json.parseToJsonElement(head).jsonObject
        val status = h["status"]!!.jsonPrimitive.int
        val headers = h["headers"]!!.jsonArray.map { it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content }
        if (h["stream"]?.jsonPrimitive?.boolean == true) {
            val parts = Channel<ByteArray>(Channel.UNLIMITED)
            streams[id] = parts
            val flow = parts.consumeAsFlow().onCompletion {
                // the app let go before the stream ended: the bridge's side ends as a closed connection
                if (streams.remove(id) != null) scope.launch { eval("__laniPhone.cancel($id)") }
            }
            p.answer.complete(Response(status, headers, ByteArray(0), flow))
        } else {
            p.answer.complete(Response(status, headers, text?.toByteArray() ?: bytes ?: ByteArray(0), null))
        }
    }

    // --- fetch over OkHttp ----------------------------------------------------------------------------------------------

    private fun startFetch(id: Long, method: String, url: String, headersJson: String, body: ByteArray?, timeoutMs: Long, redirect: String) {
        val headers = pairsOf(headersJson)
        val contentType = headers.firstOrNull { it.first.equals("content-type", ignoreCase = true) }?.second?.toMediaTypeOrNull()
        val requestBody = body?.toRequestBody(contentType) ?: if (HttpMethod.requiresRequestBody(method)) ByteArray(0).toRequestBody(contentType) else null
        val request = try {
            okhttp3.Request.Builder().url(url).method(method, requestBody).apply { for ((k, v) in headers) addHeader(k, v) }.build()
        } catch (e: IllegalArgumentException) {
            fetched[id] = Fetched(buildJsonObject { put("error", e.message ?: "invalid request") }.toString(), null)
            scope.launch { eval("__laniLoop.fetched($id)") }
            return
        }
        val client = if (timeoutMs > 0 || redirect != "follow") config.http.newBuilder().apply {
            if (timeoutMs > 0) callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            if (redirect != "follow") followRedirects(false).followSslRedirects(false)
        }.build() else config.http
        val call = client.newCall(request)
        fetches[id] = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                fetches.remove(id)
                val timeout = e is InterruptedIOException && (e.message ?: "").contains("timeout", ignoreCase = true)
                fetched[id] = Fetched(buildJsonObject { put("error", e.message ?: e.toString()); put("timeout", timeout) }.toString(), null)
                if (!call.isCanceled()) scope.launch { eval("__laniLoop.fetched($id)") }
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                fetches.remove(id)
                val result = try {
                    response.use { r ->
                        val bytes = r.body?.bytes()
                        Fetched(buildJsonObject {
                            put("status", r.code)
                            put("statusText", r.message)
                            put("headers", pairs(r.headers.map { it.first to it.second }))
                            put("url", r.request.url.toString())
                            put("redirected", r.priorResponse != null)
                        }.toString(), bytes)
                    }
                } catch (e: IOException) {
                    Fetched(buildJsonObject { put("error", e.message ?: e.toString()) }.toString(), null)
                }
                fetched[id] = result
                if (!call.isCanceled()) scope.launch { eval("__laniLoop.fetched($id)") }
            }
        })
    }

    companion object {
        fun randomToken(): String = HostText.base64Encode(HostCrypto.randomBytes(24), url = true)

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()
    }
}
