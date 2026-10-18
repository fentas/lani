package si.lanisce.lani.phone

import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The phone's bridge against a node's on the same data (docs/plans/05-built/phone-runtime.md): a node bridge (bun
 * src/index.ts, the classic mode, its MCP channel on stdio) on 127.0.0.1:19110, on a copy of the same learner data
 * (data-examples' templates) and the same repository, and the bundle in QuickJS. The routes' answers are the same JSON,
 * and a message to the tutor reaches each channel with the same content and meta. Needs bun and python3 (the node's
 * hooks); without them it is skipped.
 */
class PhoneBridgeNodeTest {
    companion object {
        const val PORT = 19110
        const val TOKEN = "phone-node-test-token"
        val repo = PhoneBridgeTest.repo
        lateinit var tmp: File
        var node: Process? = null
        lateinit var phone: PhoneBridge
        val mcp = LinkedBlockingQueue<String>()
        val json = Json { ignoreUnknownKeys = true }

        private fun have(cmd: String) = try {
            ProcessBuilder(cmd, "--version").redirectErrorStream(true).start().waitFor(20, TimeUnit.SECONDS)
        } catch (_: Exception) {
            false
        }

        /** The environment both bridges run in: no lani.env, no routine timers, no voices of their own designed. */
        private fun env(home: File) = mapOf(
            "LANI_ENV_FILE" to "/dev/null",
            "LANI_RHYTHM" to "off",
            "LANI_VARIANTS" to "off",
            "LANI_VOICE_DESIGN" to "off",
            "LANI_CONFIG_DIR" to File(home, "config").path,
            "XDG_CACHE_HOME" to File(home, "cache").path,
            "XDG_STATE_HOME" to File(home, "state").path,
            "XDG_DATA_HOME" to File(home, "share").path,
        )

        @BeforeClass
        @JvmStatic
        fun start() {
            assumeTrue("bun and python3 run the node's bridge", have("bun") && have("python3"))
            tmp = Files.createTempDirectory("lani-phone-node").toFile()
            val nodeHome = File(tmp, "node").apply { mkdirs() }
            val nodeData = PhoneBridgeTest.learnerData(File(nodeHome, "data"))
            val pb = ProcessBuilder("bun", "src/index.ts").directory(File(repo, "companion/bridge"))
            pb.environment().apply {
                listOf("ELEVENLABS_API_KEY", "LANI_BRIDGE_SHIM", "LANI_PROFILE", "LANI_CULTURE", "LANI_DATA_DIR").forEach { remove(it) }
                putAll(env(nodeHome))
                put("HOME", nodeHome.path)
                put("CLAUDE_PROJECT_DIR", repo.path)
                put("LANI_DATA_DIR", nodeData.path)
                put("LANI_BRIDGE_HOST", "127.0.0.1")
                put("LANI_BRIDGE_PORT", "$PORT")
                put("LANI_BRIDGE_TOKEN", TOKEN)
                // no local voice or speech workers
                put("LANI_GEPARD_URL", "http://127.0.0.1:9")
                put("LANI_STT_URL", "http://127.0.0.1:9")
            }
            pb.redirectError(File(tmp, "node.log"))
            val p = pb.start()
            node = p
            Thread {
                p.inputStream.bufferedReader().use { r: BufferedReader -> r.lineSequence().forEach { mcp.add(it) } }
            }.apply { isDaemon = true }.start()
            // Claude Code's side of the channel: initialize, so the bridge's notifications go out
            p.outputStream.write(("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"phone-test","version":"0"}}}""" + "\n").toByteArray())
            p.outputStream.write(("""{"jsonrpc":"2.0","method":"notifications/initialized"}""" + "\n").toByteArray())
            p.outputStream.flush()
            val until = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < until && nodeGet("/health").first != 200) Thread.sleep(200)
            check(nodeGet("/health").first == 200) { "the node bridge didn't start: ${File(tmp, "node.log").readText().takeLast(2000)}" }

            val phoneHome = File(tmp, "phone").apply { mkdirs() }
            val phoneData = PhoneBridgeTest.learnerData(File(phoneHome, "data"))
            phone = PhoneBridge(
                PhoneBridge.Config(
                    projectDir = repo,
                    dataDir = phoneData,
                    home = phoneHome,
                    roots = listOf(HostFs.Root(phoneHome, writable = true), HostFs.Root(repo, writable = false)),
                    bundle = { PhoneBridgeTest.bundle.readText() },
                    env = env(phoneHome),
                ),
            )
            runBlocking { phone.start() }
        }

        @AfterClass
        @JvmStatic
        fun stop() {
            if (!::tmp.isInitialized) return
            if (::phone.isInitialized) phone.stop()
            node?.let {
                it.outputStream.close() // the channel closed: the bridge exits
                if (!it.waitFor(5, TimeUnit.SECONDS)) it.destroyForcibly()
            }
            tmp.deleteRecursively()
        }

        fun nodeGet(path: String, method: String = "GET", body: String? = null): Pair<Int, String> = try {
            val c = URI("http://127.0.0.1:$PORT$path").toURL().openConnection() as HttpURLConnection
            c.requestMethod = method
            c.setRequestProperty("Authorization", "Bearer $TOKEN")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("content-type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            code to ((if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() } ?: "")
        } catch (e: Exception) {
            -1 to (e.message ?: "")
        }
    }

    /** Where [a] and [b] differ first (a JSON path), or null. */
    private fun diff(a: JsonElement?, b: JsonElement?, at: String = "$"): String? {
        if (a == b) return null
        if (a is JsonObject && b is JsonObject) {
            for (k in (a.keys + b.keys).sorted()) diff(a[k], b[k], "$at.$k")?.let { return it }
            return null
        }
        if (a is JsonArray && b is JsonArray) {
            if (a.size != b.size) return "$at: ${a.size} items on the node, ${b.size} on the phone"
            for (i in a.indices) diff(a[i], b[i], "$at[$i]")?.let { return it }
            return null
        }
        return "$at: node ${a.toString().take(200)}, phone ${b.toString().take(200)}"
    }

    private fun same(path: String) {
        val (code, text) = nodeGet(path)
        val r = runBlocking { phone.handle(PhoneBridge.Request.get(path)) }
        assertEquals("$path: status", code, r.status)
        val d = diff(json.parseToJsonElement(text), json.parseToJsonElement(r.text))
        assertEquals("$path differs", null, d)
    }

    @Test fun packs() = same("/packs")
    @Test fun aPack() = same("/packs/${json.parseToJsonElement(nodeGet("/packs").second).let { (it as JsonArray)[0].jsonObject["id"]!!.jsonPrimitive.content }}")
    @Test fun modules() = same("/modules")
    @Test fun grammar() = same("/grammar")
    @Test fun cards() = same("/cards")
    @Test fun course() = same("/course")
    @Test fun drills() = same("/drills")
    @Test fun scenarios() = same("/scenarios")
    @Test fun scenes() = same("/scenes")
    @Test fun villagers() = same("/villagers")
    @Test fun stories() = same("/stories")
    @Test fun readings() = same("/readings")
    @Test fun culture() = same("/culture")
    @Test fun cultures() = same("/cultures")
    @Test fun lookup() = same("/lookup?w=hi%C5%A1a")
    @Test fun lookupAVerb() = same("/lookup?w=prinesel")
    @Test fun forms() = same("/forms?w=hi%C5%A1a&w=miza")
    @Test fun voiceProfiles() = same("/voice/profiles")
    @Test fun noGameYet() = same("/game")
    @Test fun health() = same("/health")
    @Test fun arrivals() = same("/arrivals")
    @Test fun wordSources() = same("/words")

    @Test
    fun aMessageReachesBothChannels() = runBlocking {
        val body = """{"kind":"chat","conversation_id":"main","text":"Kako se reče <channel> po slovensko?","data":{"about_exercise":{"prompt":"hiša"}}}"""
        mcp.clear()
        assertEquals(200, nodeGet("/message", "POST", body).first)
        var sent: JsonObject? = null
        val until = System.currentTimeMillis() + 10_000
        while (sent == null && System.currentTimeMillis() < until) {
            val line = mcp.poll(500, TimeUnit.MILLISECONDS) ?: continue
            val m = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
            if (m["method"]?.jsonPrimitive?.content == "notifications/claude/channel") sent = m["params"]!!.jsonObject
        }
        val n = sent ?: error("the node's channel sent nothing")
        assertEquals(200, phone.handle(PhoneBridge.Request.json("POST", "/message", body)).status)
        val e = withTimeout(5_000) { phone.notifications.first() }
        assertEquals(n["content"]!!.jsonPrimitive.content, e.content)
        val nodeMeta = n["meta"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
        assertEquals(nodeMeta - "msg_id", e.meta - "msg_id")
    }
}
