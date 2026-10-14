package si.lanisce.lani.spike.validators

import com.dokar.quickjs.QuickJs
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking

/** QuickJS (quickjs-kt) in this process, on a thread of its own; [stack]: its stack limit when given (bytes). */
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

    override fun gc() = runBlocking(dispatcher) { js.gc() }

    override fun close() {
        runBlocking(dispatcher) { js.close() }
        thread.shutdown()
    }

    companion object {
        /** The bundle compiled once and loaded as bytecode in a fresh runtime: what an app could cache. */
        fun bytecode(js: Quick, bundle: String, out: MutableMap<String, kotlinx.serialization.json.JsonElement>) {
            var t = System.nanoTime()
            val code = js.compile(bundle)
            out["compile_ms"] = kotlinx.serialization.json.JsonPrimitive((System.nanoTime() - t) / 1e6)
            out["bytecode_bytes"] = kotlinx.serialization.json.JsonPrimitive(code.size)
            Quick().use { f ->
                t = System.nanoTime()
                f.evalBytecode(code)
                out["load_bytecode_ms"] = kotlinx.serialization.json.JsonPrimitive((System.nanoTime() - t) / 1e6)
            }
            out["version"] = kotlinx.serialization.json.JsonPrimitive(js.js.version)
            out["max_stack_size"] = kotlinx.serialization.json.JsonPrimitive(js.js.maxStackSize)
        }
    }
}
