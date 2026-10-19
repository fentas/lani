package si.lanisce.lani.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Debug
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The phone bridge on a device, timed (debug and qa builds only; docs/plans/05-built/phone-runtime.md "On the
 * emulator"): the content unpacked, the bundle compiled and then loaded from its bytecode, the first GET /state, a
 * /lookup and the pack list, a message to the tutor's channel, the learner-data scripts (20 words kept, a review of
 * them, the state again: update-db and read-db in TypeScript), and the memory (QuickJS's heap, the app's native heap
 * and its PSS). On a data directory of its own (<filesDir>/lani/probe/data, from the templates), never the learner's.
 *
 *     adb shell am broadcast -n si.lanisce.lani/.phone.PhoneBridgeProbe
 *     adb logcat -s PhoneBridgeProbe LaniBridge
 *
 * The result is logged (tag PhoneBridgeProbe) and kept in <filesDir>/lani/probe/result.json.
 */
class PhoneBridgeProbe : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val done = goAsync()
        Thread({
            try {
                val result = probe(context.applicationContext)
                Log.i(TAG, result.toString())
            } catch (e: Throwable) {
                Log.e(TAG, "probe failed", e)
            } finally {
                done.finish()
            }
        }, "phone-bridge-probe").start()
    }

    private fun ms(t0: Long) = (System.nanoTime() - t0) / 1e6

    private fun probe(context: Context): JsonObject = runBlocking {
        val home = AppBridge.home(context)
        val dir = File(home, "probe").apply { deleteRecursively(); mkdirs() }
        File(home, "cache/bytecode").deleteRecursively()
        val pssBefore = Debug.getPss()
        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        var t = System.nanoTime()
        val (first, unpacked) = AppBridge.create(context, dataDir = data(context, dir))
        val unpackMs = ms(t)
        // the first start compiles the bundle (and keeps its bytecode); the second loads the bytecode
        val cold = first.start()
        first.stop()
        val (bridge, _) = AppBridge.create(context, dataDir = File(dir, "data"))
        val warm = bridge.start()
        t = System.nanoTime()
        val state = bridge.handle(PhoneBridge.Request.get("/state"))
        val stateMs = ms(t)
        t = System.nanoTime()
        val lookup = bridge.handle(PhoneBridge.Request.get("/lookup?w=hiša"))
        val lookupMs = ms(t)
        t = System.nanoTime()
        val lookup2 = bridge.handle(PhoneBridge.Request.get("/lookup?w=miza"))
        val lookup2Ms = ms(t)
        t = System.nanoTime()
        val packs = bridge.handle(PhoneBridge.Request.get("/packs"))
        val packsMs = ms(t)
        t = System.nanoTime()
        val message = bridge.handle(PhoneBridge.Request.json("POST", "/message", """{"kind":"chat","conversation_id":"main","text":"Živjo!"}"""))
        val heard = withTimeoutOrNull(5_000) { bridge.notifications.first() }
        val messageMs = ms(t)
        // the learner-data scripts in QuickJS (src/db): 20 words looked up and kept (update-db each), a review of all 20
        // (update-db with 20 results, a mid-size session), then the state with them (read-db)
        val words = listOf("miza", "stol", "okno", "vrata", "kruh", "voda", "mleko", "sir", "jabolko", "hruška", "pes", "mačka", "konj", "krava", "drevo", "cvet", "reka", "gora", "nebo", "sonce")
        t = System.nanoTime()
        val items = words.mapIndexed { i, w ->
            val r = bridge.handle(PhoneBridge.Request.json("POST", "/words", """{"client_id":"probe-word-${"%05d".format(i)}","sl":"$w","en":"thing $i","from":"chat"}"""))
            Regex("\"item_id\":\"([^\"]+)\"").find(r.text)?.groupValues?.get(1) ?: "vocab_word_$w"
        }
        val wordsMs = ms(t) / words.size
        val results = items.mapIndexed { i, id -> """{"item_id":"$id","quality":${2 + i % 4}}""" }.joinToString(",")
        t = System.nanoTime()
        val review = bridge.handle(PhoneBridge.Request.json("POST", "/reviews", """{"client_id":"probe-review-0001","results":[$results],"duration_minutes":6}"""))
        val reviewMs = ms(t)
        t = System.nanoTime()
        val state2 = bridge.handle(PhoneBridge.Request.get("/state"))
        val state2Ms = ms(t)
        val heap = bridge.memory(gc = false)
        val heapGc = bridge.memory(gc = true)
        val result = buildJsonObject {
            put("unpack", buildJsonObject {
                put("unpacked", unpacked.unpacked); put("files", unpacked.files); put("bytes", unpacked.bytes); put("ms", unpackMs)
            })
            put("bundle_compile_ms", cold.loadMs)
            put("bundle_bytecode_ms", warm.loadMs)
            put("boot_ms_cold", cold.bootMs)
            put("boot_ms", warm.bootMs)
            put("state", buildJsonObject { put("status", state.status); put("ms", stateMs); put("answer", state.text.take(200)) })
            put("lookup", buildJsonObject { put("status", lookup.status); put("ms", lookupMs); put("bytes", lookup.body.size) })
            put("lookup_second", buildJsonObject { put("status", lookup2.status); put("ms", lookup2Ms) })
            put("packs", buildJsonObject { put("status", packs.status); put("ms", packsMs); put("bytes", packs.body.size) })
            put("message", buildJsonObject { put("status", message.status); put("ms", messageMs); put("heard", heard != null) })
            put("words_update_db_ms_each", wordsMs)
            put("review_20_update_db", buildJsonObject { put("status", review.status); put("ms", reviewMs); put("answer", review.text.take(200)) })
            put("state_with_20_cards", buildJsonObject { put("status", state2.status); put("ms", state2Ms); put("bytes", state2.body.size) })
            put("quickjs_heap", buildJsonObject {
                put("malloc_bytes", heap.mallocSize); put("used_bytes", heap.memoryUsedSize); put("objects", heap.objCount)
                put("malloc_bytes_after_gc", heapGc.mallocSize)
            })
            put("native_heap_bytes", Debug.getNativeHeapAllocatedSize() - nativeBefore)
            put("pss_kb_before", pssBefore)
            put("pss_kb_after", Debug.getPss())
        }
        bridge.stop()
        File(dir, "result.json").writeText(result.toString())
        result
    }

    /** A new learner's data, from the templates the content brings (data-examples). */
    private fun data(context: Context, dir: File): File {
        val data = File(dir, "data").apply { mkdirs() }
        val templates = File(AppBridge.projectDir(context), "data-examples")
        // the content is unpacked by AppBridge.create; before the first unpack, from the APK
        if (templates.isDirectory) templates.listFiles { f -> f.name.endsWith("-template.json") }?.forEach { it.copyTo(File(data, it.name.replace("-template", ""))) }
        else for (name in context.assets.list("phone/project/data-examples").orEmpty().filter { it.endsWith("-template.json") }) {
            context.assets.open("phone/project/data-examples/$name").use { i -> File(data, name.replace("-template", "")).outputStream().use { i.copyTo(it) } }
        }
        return data
    }

    companion object {
        const val TAG = "PhoneBridgeProbe"
    }
}
