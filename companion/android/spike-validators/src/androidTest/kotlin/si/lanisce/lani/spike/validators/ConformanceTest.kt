package si.lanisce.lani.spike.validators

import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dokar.quickjs.QuickJs
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bridge's content validators (companion/bridge/src/validate-entry.ts, bundled by validators/build.ts) in each engine,
 * on the conformance corpus (companion/bridge/test/conformance/build-corpus.ts): every case's answer against the bridge's
 * (bun's), and the engine's costs: its start, the bundle's load, the references and the files passed in (the dictionary
 * among them), each file's check, the memory. A spike for docs/plans/05-phase-0.md, check 4.
 *
 * Writes results-<engine>.json to the app's external files directory (adb pull) and logs a summary (tag LaniValidators).
 * Run: build the bundle and the corpus (companion/bridge), then from companion/android
 *   ./gradlew -p spike-validators assembleNoneDebug assembleNoneDebugAndroidTest
 * install both APKs, `adb shell am instrument -w -e class si.lanisce.lani.spike.validators.ConformanceTest#quickjs
 * si.lanisce.lani.spike.validators.test/androidx.test.runner.AndroidJUnitRunner` (and #jsengine), pull, uninstall.
 */
@RunWith(AndroidJUnit4::class)
class ConformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    private val target = instrumentation.targetContext

    /** One engine: evaluate a script, its value as text. */
    interface Js : Closeable {
        fun eval(script: String): String
        /** What the engine says of its memory (bytes), as it measures it. */
        fun memory(): Map<String, Long> = emptyMap()
    }

    /** QuickJS (quickjs-kt) in this process, on a thread of its own. */
    class Quick(stack: Long = 0) : Js {
        private val thread = Executors.newSingleThreadExecutor()
        private val dispatcher = thread.asCoroutineDispatcher()
        val js: QuickJs = QuickJs.create(dispatcher).also { if (stack > 0) it.maxStackSize = stack }
        override fun eval(script: String): String = runBlocking(dispatcher) { js.evaluate<Any?>(script, "main.js", false).toString() }
        fun evalBytecode(code: ByteArray): String = runBlocking(dispatcher) { js.evaluate<Any?>(code).toString() }
        fun compile(script: String): ByteArray = runBlocking(dispatcher) { js.compile(script, "validators.js", false) }
        override fun memory(): Map<String, Long> = js.memoryUsage.let { m ->
            mapOf("malloc_size" to m.mallocSize, "memory_used_size" to m.memoryUsedSize, "str_size" to m.strSize, "obj_count" to m.objCount)
        }
        override fun close() {
            runBlocking(dispatcher) { js.close() }
            thread.shutdown()
        }
    }

    /** Jetpack JavaScriptEngine: an isolate in the WebView's sandboxed process (the sandbox shared: closing closes the isolate). */
    class Sandbox(val sandbox: JavaScriptSandbox) : Js {
        val isolate: JavaScriptIsolate = sandbox.createIsolate(IsolateStartupParameters().apply {
            if (sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) maxHeapSizeBytes = 512L shl 20
            maxEvaluationReturnSizeBytes = 16 shl 20
        })
        override fun eval(script: String): String = isolate.evaluateJavaScriptAsync(script).get()
        override fun close() = isolate.close()
    }

    // --- the corpus ---------------------------------------------------------------------------------------------------

    private fun text(name: String) = assets.open(name).bufferedReader().use { it.readText() }
    private fun q(s: String) = JsonPrimitive(s).toString()
    private val ms = { t0: Long -> (SystemClock.elapsedRealtimeNanos() - t0) / 1e6 }
    private fun now() = SystemClock.elapsedRealtimeNanos()

    /** The cases, one line at a time (the file is ~55 MB: never whole in memory). */
    private fun cases(each: (JsonObject) -> Unit) =
        assets.open("cases.jsonl").bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.forEach { each(Json.parseToJsonElement(it).jsonObject) } }

    private class Mounts(val js: Js, val read: (String) -> String, val log: MutableMap<String, Double>, val sizes: MutableMap<String, Int>) {
        private val done = mutableSetOf<String>()
        fun ensure(name: String) {
            if (!done.add(name)) return
            val m = Json.parseToJsonElement(read("mounts/$name.json")).jsonObject
            val root = m["root"]!!.jsonPrimitive.content
            val files = m["files"]!!.jsonObject
            val base = m["base"]?.jsonPrimitive?.content
            base?.let { ensure(it) }
            val t0 = SystemClock.elapsedRealtimeNanos()
            var bytes = 0
            if (base != null) {
                val b = Json.parseToJsonElement(read("mounts/$base.json")).jsonObject
                val rels = JsonArray(b["files"]!!.jsonObject.keys.map { JsonPrimitive(it) }).toString()
                js.eval("lani.copy(${JsonPrimitive(b["root"]!!.jsonPrimitive.content)}, ${JsonPrimitive(root)}, ${JsonPrimitive(rels)})")
            }
            // the files in scripts of up to ~2 MB each
            val batch = StringBuilder()
            for ((rel, t) in files) {
                val call = "lani.mount(${JsonPrimitive("$root/$rel")}, ${JsonPrimitive(t.jsonPrimitive.content)});"
                bytes += t.jsonPrimitive.content.length
                if (batch.isNotEmpty() && batch.length + call.length > 2_000_000) { js.eval(batch.toString()); batch.clear() }
                batch.append(call)
            }
            if (batch.isNotEmpty()) js.eval(batch.toString())
            for (r in m["remove"]?.jsonArray.orEmpty()) js.eval("lani.unmount(${JsonPrimitive("$root/${r.jsonPrimitive.content}")})")
            log[name] = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
            sizes[name] = bytes
        }
    }

    private fun quantiles(xs: List<Double>): JsonObject {
        val s = xs.sorted()
        fun at(p: Double) = s[minOf(s.size - 1, (p * s.size).toInt())]
        return buildJsonObject {
            put("n", s.size); put("median_ms", at(0.5)); put("p95_ms", at(0.95)); put("max_ms", s.last()); put("total_ms", s.sum())
        }
    }

    /** What `dumpsys meminfo` says of the processes whose name has [part]: their total PSS (KB). */
    private fun pssOf(part: String): Map<String, Long> {
        val pfd = instrumentation.uiAutomation.executeShellCommand("dumpsys meminfo")
        val out = ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        // "    123,456K: com.google.android.webview:sandboxed_process0:… (pid 1234)"
        return Regex("""([\d,]+)K: ([^\s]+) \(pid (\d+)""").findAll(out)
            .filter { part in it.groupValues[2] }
            .associate { "${it.groupValues[2]} (${it.groupValues[3]})" to it.groupValues[1].replace(",", "").toLong() }
    }

    private fun run(engine: String, make: () -> Js, extra: (Js, MutableMap<String, JsonElement>) -> Unit = { _, _ -> }) {
        val out = mutableMapOf<String, JsonElement>()
        val heap0 = Debug.getNativeHeapAllocatedSize()
        // the engine's start: cold (the first in this process), then warm
        var t = now()
        val js = make()
        out["start_cold_ms"] = JsonPrimitive(ms(t))
        t = now(); make().close(); out["start_warm_ms"] = JsonPrimitive(ms(t))
        // the bundle: each build, in an engine of its own, then the one with the dictionary's checks in this one
        val core = text("validators.js")
        val full = text("validators-lexicon.js")
        make().use { e -> t = now(); e.eval(core); out["load_core_ms"] = JsonPrimitive(ms(t)) }
        t = now(); js.eval(full); out["load_full_ms"] = JsonPrimitive(ms(t))
        out["bundle_bytes"] = buildJsonObject { put("validators.js", core.length); put("validators-lexicon.js", full.length) }
        out["kinds"] = JsonPrimitive(js.eval("lani.kinds()"))
        extra(js, out)
        val heapLoaded = Debug.getNativeHeapAllocatedSize()

        // the references: each set once
        val refsMs = mutableMapOf<String, Double>()
        val refsBytes = mutableMapOf<String, Int>()
        for (f in assets.list("refs").orEmpty().sorted()) {
            val name = f.removeSuffix(".json")
            val body = text("refs/$f")
            t = now(); js.eval("lani.setRefs(${q(name)}, ${q(body)})"); refsMs[name] = ms(t)
            refsBytes[name] = body.length
        }
        out["refs_ms"] = JsonObject(refsMs.mapValues { JsonPrimitive(it.value) })
        out["refs_bytes"] = JsonObject(refsBytes.mapValues { JsonPrimitive(it.value) })

        // the conformance pass: every case, with the digest of what was read
        val mountMs = mutableMapOf<String, Double>()
        val mountBytes = mutableMapOf<String, Int>()
        val mounts = Mounts(js, ::text, mountMs, mountBytes)
        val times = mutableMapOf<String, MutableList<Double>>()
        val mismatches = mutableListOf<JsonObject>()
        val parser = mutableListOf<JsonObject>()
        var n = 0
        var firstWoven: Double? = null
        cases { c ->
            n++
            val kind = c["kind"]!!.jsonPrimitive.content
            c["mounts"]?.jsonArray?.forEach { mounts.ensure(it.jsonPrimitive.content) }
            val refs = c["refs"].let { if (it == null || it is JsonNull) "null" else q(it.jsonPrimitive.content) }
            val script = "lani.validate(${q(kind)}, ${q(c["input"]!!.jsonPrimitive.content)}, $refs, '{\"digest\":true}')"
            t = now()
            val raw = js.eval(script)
            val took = ms(t)
            if (kind == "woven" && firstWoven == null) firstWoven = took
            times.getOrPut(kind) { mutableListOf() }.add(took)
            val got = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrElse { JsonObject(mapOf("unparsable" to JsonPrimitive(raw))) }
            val want = c["expected"]!!.jsonObject
            val id = c["id"]!!.jsonPrimitive.content
            if (want["json"] == JsonPrimitive(false)) {
                if (got["json"] != JsonPrimitive(false) || got["ok"] != JsonPrimitive(false)) mismatches += buildJsonObject { put("id", id); put("got", got); put("expected", want) }
                else if (got["errors"] != want["errors"]) parser += buildJsonObject { put("id", id); put("got", got["errors"] ?: JsonNull); put("bun", want["errors"] ?: JsonNull) }
            } else if (got != want) mismatches += buildJsonObject { put("id", id); put("got", got); put("expected", want) }
        }
        out["cases"] = JsonPrimitive(n)
        out["mismatches"] = JsonPrimitive(mismatches.size)
        out["mismatch_list"] = JsonArray(mismatches.take(60))
        out["parser_messages"] = JsonArray(parser)
        out["mount_ms"] = JsonObject(mountMs.mapValues { JsonPrimitive(it.value) })
        out["mount_chars"] = JsonObject(mountBytes.mapValues { JsonPrimitive(it.value) })
        out["first_woven_ms"] = JsonPrimitive(firstWoven)
        out["conformance_ms"] = JsonObject(times.mapValues { quantiles(it.value) })

        // the timing pass: each curated file as the app would check it (no digest), in the order of the corpus
        val curated = mutableMapOf<String, MutableList<Double>>()
        var biggest: Pair<String, Int>? = null
        var biggestMs = 0.0
        cases { c ->
            if (c["mutation"] != null) return@cases
            val kind = c["kind"]!!.jsonPrimitive.content
            val input = c["input"]!!.jsonPrimitive.content
            val refs = c["refs"].let { if (it == null || it is JsonNull) "null" else q(it.jsonPrimitive.content) }
            val script = "lani.validate(${q(kind)}, ${q(input)}, $refs, null)"
            t = now()
            js.eval(script)
            val took = ms(t)
            curated.getOrPut(kind) { mutableListOf() }.add(took)
            if (kind == "scene" && input.length > (biggest?.second ?: 0)) { biggest = c["id"]!!.jsonPrimitive.content to input.length; biggestMs = took }
        }
        out["curated_ms"] = JsonObject(curated.mapValues { quantiles(it.value) })
        out["curated_all"] = quantiles(curated.filterKeys { it != "culture" }.values.flatten())
        out["biggest_scene"] = buildJsonObject { put("id", biggest?.first); put("chars", biggest?.second); put("ms", biggestMs) }

        // memory: the engine's own measure, this process's native heap, the sandbox's process
        out["memory_engine"] = JsonObject(js.memory().mapValues { JsonPrimitive(it.value) })
        out["native_heap_bytes"] = buildJsonObject {
            put("before", heap0); put("loaded", heapLoaded); put("after_corpus", Debug.getNativeHeapAllocatedSize())
        }
        out["pss_kb"] = JsonObject((pssOf("sandboxed_process") + pssOf("si.lanisce.lani.spike")).mapValues { JsonPrimitive(it.value) })
        js.close()

        val result = JsonObject(out)
        File(target.getExternalFilesDir(null), "results-$engine.json").writeText(result.toString())
        Log.i(TAG, "$engine: ${n} cases, ${mismatches.size} mismatches, ${parser.size} parser messages; written to results-$engine.json")
        for (m in mismatches.take(10)) Log.i(TAG, "$engine mismatch: ${m.toString().take(600)}")
    }

    @Test
    fun quickjs() = run("quickjs", { Quick() }) { e, out ->
        val q = e as Quick
        out["version"] = JsonPrimitive(q.js.version)
        out["max_stack_size"] = JsonPrimitive(q.js.maxStackSize)
        // the bundle as bytecode: compiled once (what the app could cache), then loaded in a fresh runtime
        val full = text("validators-lexicon.js")
        var t = now()
        val code = q.compile(full)
        out["compile_ms"] = JsonPrimitive(ms(t))
        out["bytecode_bytes"] = JsonPrimitive(code.size)
        Quick().use { f -> t = now(); f.evalBytecode(code); out["load_bytecode_ms"] = JsonPrimitive(ms(t)) }
    }

    @Test
    fun jsengine() {
        val supported = JavaScriptSandbox.isSupported()
        val features = listOf(
            JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION, JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN,
            JavaScriptSandbox.JS_FEATURE_PROVIDE_CONSUME_ARRAY_BUFFER, JavaScriptSandbox.JS_FEATURE_WASM_COMPILATION,
            JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE, JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT,
            JavaScriptSandbox.JS_FEATURE_CONSOLE_MESSAGING, JavaScriptSandbox.JS_FEATURE_EVALUATE_FROM_FD, JavaScriptSandbox.JS_FEATURE_MESSAGE_PORTS,
        )
        check(supported) { "JavaScriptSandbox is not supported on this device" }
        // the sandbox (the service and its process) once; each engine an isolate in it
        val t0 = now()
        val sandbox = JavaScriptSandbox.createConnectedInstanceAsync(target).get()
        val connectMs = ms(t0)
        run("jsengine", { Sandbox(sandbox) }) { e, out ->
            val s = (e as Sandbox).sandbox
            out["sandbox_connect_ms"] = JsonPrimitive(connectMs)
            out["features"] = JsonObject(features.associateWith { JsonPrimitive(s.isFeatureSupported(it)) })
            out["webview"] = JsonPrimitive(runCatching { target.packageManager.getPackageInfo("com.google.android.webview", 0).versionName }.getOrNull())
            // the bundle from a file descriptor, in an isolate of its own
            if (s.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_FROM_FD)) {
                val f = File(target.cacheDir, "validators-lexicon.js").apply { writeText(text("validators-lexicon.js")) }
                Sandbox(s).isolate.use { iso ->
                    val t = now()
                    ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { iso.evaluateJavaScriptAsync(it).get() }
                    out["load_full_fd_ms"] = JsonPrimitive(ms(t))
                }
            }
        }
        sandbox.close()
    }

    companion object {
        const val TAG = "LaniValidators"
    }
}
