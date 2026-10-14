package si.lanisce.lani.spike.validators

import android.os.Debug
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bridge's content validators in each engine on a phone, on the conformance corpus (Harness.kt): every case's answer
 * against bun's, and the engine's costs. A spike for docs/plans/05-phase-0.md, check 4.
 *
 * Writes results-<engine>.json to the app's external files directory (adb pull) and logs a summary (tag LaniValidators).
 * Run: build the bundle and the corpus (companion/bridge: bun validators/build.ts; bun test/conformance/build-corpus.ts),
 * then from companion/android
 *   ./gradlew -p spike-validators assembleNoneDebug assembleNoneDebugAndroidTest
 * install both APKs, `adb shell am instrument -w -e class si.lanisce.lani.spike.validators.ConformanceTest#quickjs
 * si.lanisce.lani.spike.validators.test/androidx.test.runner.AndroidJUnitRunner` (and #jsengine), pull, uninstall.
 */
@RunWith(AndroidJUnit4::class)
class ConformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    private val target = instrumentation.targetContext

    /** Jetpack JavaScriptEngine: an isolate in the WebView's sandboxed process (the sandbox shared: closing closes the isolate). */
    class Sandbox(val sandbox: JavaScriptSandbox) : Js {
        val isolate: JavaScriptIsolate = sandbox.createIsolate(IsolateStartupParameters().apply {
            if (sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) maxHeapSizeBytes = 512L shl 20
            maxEvaluationReturnSizeBytes = 16 shl 20
        })
        override fun eval(script: String): String = isolate.evaluateJavaScriptAsync(script).get()
        override fun close() = isolate.close()
    }

    private fun text(name: String) = assets.open(name).bufferedReader().use { it.readText() }
    private val harness = Harness(
        read = ::text,
        names = { dir -> assets.list(dir).orEmpty().toList() },
        lines = { each -> assets.open("cases.jsonl").bufferedReader().useLines { it.forEach(each) } },
    )

    /** What `dumpsys meminfo` says of the processes whose name has [part]: their total PSS (KB). */
    private fun pssOf(vararg parts: String): Map<String, Long> {
        val pfd = instrumentation.uiAutomation.executeShellCommand("dumpsys meminfo")
        val out = ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        // "    123,456K: com.google.android.webview:sandboxed_process0:… (pid 1234)"
        return Regex("""([\d,]+)K: (\S+) \(pid (\d+)""").findAll(out)
            .filter { m -> parts.any { it in m.groupValues[2] } }
            .associate { "pss_kb ${it.groupValues[2]} (${it.groupValues[3]})" to it.groupValues[1].replace(",", "").toLong() }
    }

    private fun probe(point: String): Map<String, Long> =
        mapOf("native_heap_bytes" to Debug.getNativeHeapAllocatedSize(), "java_heap_bytes" to Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }) +
            pssOf("sandboxed_process", target.packageName)

    private fun write(engine: String, result: JsonObject) {
        File(target.getExternalFilesDir(null), "results-$engine.json").writeText(result.toString())
        Log.i(TAG, "$engine: ${result["cases"]} cases, ${result["mismatches"]} mismatches; results-$engine.json")
        (result["mismatch_list"] as? kotlinx.serialization.json.JsonArray)?.take(10)?.forEach { Log.i(TAG, "$engine mismatch: ${it.toString().take(600)}") }
    }

    /** [result] with the village's session (Harness.session) first: measured in a fresh process (or sandbox) state. */
    private fun withSession(session: JsonObject, result: JsonObject) = JsonObject(result + ("session" to session))

    @Test
    fun quickjs() {
        val session = harness.session({ Quick() }) { probe("session") }
        write("quickjs", withSession(session, harness.run({ Quick() }, ::probe) { e, out -> Quick.bytecode(e as Quick, text("validators-lexicon.js"), out) }))
    }

    @Test
    fun jsengine() {
        check(JavaScriptSandbox.isSupported()) { "JavaScriptSandbox is not supported on this device" }
        val features = listOf(
            JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION, JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN,
            JavaScriptSandbox.JS_FEATURE_PROVIDE_CONSUME_ARRAY_BUFFER, JavaScriptSandbox.JS_FEATURE_WASM_COMPILATION,
            JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE, JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT,
            JavaScriptSandbox.JS_FEATURE_CONSOLE_MESSAGING, JavaScriptSandbox.JS_FEATURE_EVALUATE_FROM_FD, JavaScriptSandbox.JS_FEATURE_MESSAGE_PORTS,
        )
        // the sandbox (its service and process) once; each engine an isolate in it
        val t0 = System.nanoTime()
        val sandbox = JavaScriptSandbox.createConnectedInstanceAsync(target).get()
        val connectMs = (System.nanoTime() - t0) / 1e6
        val session = harness.session({ Sandbox(sandbox) }) { probe("session") }
        val result = harness.run({ Sandbox(sandbox) }, ::probe) { _, out ->
            out["sandbox_connect_ms"] = JsonPrimitive(connectMs)
            out["features"] = JsonObject(features.associateWith { JsonPrimitive(sandbox.isFeatureSupported(it)) })
            out["webview"] = JsonPrimitive(runCatching { target.packageManager.getPackageInfo("com.google.android.webview", 0).versionName }.getOrNull())
            // the bundle from a file descriptor, in an isolate of its own: no string through the binder
            if (sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_FROM_FD)) {
                val f = File(target.cacheDir, "validators-lexicon.js").apply { writeText(text("validators-lexicon.js")) }
                Sandbox(sandbox).use { s ->
                    val t = System.nanoTime()
                    ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { s.isolate.evaluateJavaScriptAsync(it).get() }
                    out["load_full_fd_ms"] = JsonPrimitive((System.nanoTime() - t) / 1e6)
                }
            }
        }
        sandbox.close()
        write("jsengine", withSession(session, result))
    }

    companion object {
        const val TAG = "LaniValidators"
    }
}
