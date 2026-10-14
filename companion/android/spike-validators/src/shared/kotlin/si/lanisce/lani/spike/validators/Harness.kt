package si.lanisce.lani.spike.validators

import java.io.Closeable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One engine: evaluate a script, its value as text. */
interface Js : Closeable {
    fun eval(script: String): String
    /** What the engine says of its memory (bytes), as it measures it. */
    fun memory(): Map<String, Long> = emptyMap()
    /** Collects garbage, when the engine lets us. */
    fun gc() {}
}

/**
 * The conformance corpus (companion/bridge/test/conformance/build-corpus.ts) run through the validators' bundle
 * (companion/bridge/validators/build.ts) in an engine: every case's answer against bun's, and what it costs. The same
 * for the app's instrumented test (both engines on a phone) and the host's unit test (QuickJS on the JVM).
 *
 * [read]: a file of the bundle's or the corpus's directory by name ("validators.js", "refs/primorska.json"); [names]:
 * the files of one of its directories; [lines]: the corpus's cases, one line at a time (~55 MB: never whole).
 */
class Harness(
    private val read: (String) -> String,
    private val names: (String) -> List<String>,
    private val lines: ((String) -> Unit) -> Unit,
) {
    private fun q(s: String) = JsonPrimitive(s).toString()
    private fun now() = System.nanoTime()
    private fun ms(t0: Long) = (System.nanoTime() - t0) / 1e6

    private fun cases(each: (JsonObject) -> Unit) = lines { if (it.isNotBlank()) each(Json.parseToJsonElement(it).jsonObject) }

    /** The files a case's validators read, mounted once each (a broken copy of a pack: a copy of its base, then changed). */
    private inner class Mounts(val js: Js) {
        val took = mutableMapOf<String, Double>()
        val chars = mutableMapOf<String, Int>()
        private val done = mutableSetOf<String>()
        fun ensure(name: String) {
            if (!done.add(name)) return
            val m = Json.parseToJsonElement(read("mounts/$name.json")).jsonObject
            val root = m["root"]!!.jsonPrimitive.content
            val base = m["base"]?.jsonPrimitive?.content
            base?.let { ensure(it) }
            val t0 = now()
            var n = 0
            if (base != null) {
                val b = Json.parseToJsonElement(read("mounts/$base.json")).jsonObject
                val rels = JsonArray(b["files"]!!.jsonObject.keys.map { JsonPrimitive(it) }).toString()
                js.eval("lani.copy(${q(b["root"]!!.jsonPrimitive.content)}, ${q(root)}, ${q(rels)})")
            }
            // the files in scripts of up to ~2 MB each
            val batch = StringBuilder()
            for ((rel, t) in m["files"]!!.jsonObject) {
                val text = t.jsonPrimitive.content
                val call = "lani.mount(${q("$root/$rel")}, ${q(text)});"
                n += text.length
                if (batch.isNotEmpty() && batch.length + call.length > 2_000_000) { js.eval(batch.toString()); batch.clear() }
                batch.append(call)
            }
            if (batch.isNotEmpty()) js.eval(batch.toString())
            for (r in m["remove"]?.jsonArray.orEmpty()) js.eval("lani.unmount(${q("$root/${r.jsonPrimitive.content}")})")
            took[name] = ms(t0)
            chars[name] = n
        }
    }

    /**
     * One village's session, as the app would have it, in a fresh engine: the bundle, the Slovene village's references
     * (primorska), its culture pack and what its check reads beside it (the dictionary, the curated scenes), the pack's
     * check at start, each curated scene and story checked once, then the dictionary's check of woven words (its first
     * and second call); the time and the memory after each step (the engine's own measure and the platform's [probe]).
     */
    fun session(make: () -> Js, probe: () -> Map<String, Long> = { emptyMap() }): JsonObject {
        val steps = mutableListOf<JsonObject>()
        lateinit var js: Js
        fun step(name: String, f: () -> Unit) {
            val t = now()
            f()
            val took = ms(t)
            steps += buildJsonObject {
                put("step", name); put("ms", took)
                for ((k, v) in js.memory()) put("engine_$k", v)
                for ((k, v) in probe()) put(k, v)
            }
        }
        step("start") { js = make() }
        step("load bundle (validators-lexicon.js)") { js.eval(read("validators-lexicon.js")) }
        step("refs primorska") { js.eval("lani.setRefs('primorska', ${q(read("refs/primorska.json"))})") }
        val mounts = Mounts(js)
        step("mount culture-primorska") { mounts.ensure("culture-primorska") }
        val cases = mutableListOf<JsonObject>()
        cases { c -> if (c["mutation"] == null && c["refs"]?.let { it is JsonPrimitive && it.content.contains("\"use\":\"primorska\"") } == true) cases += c }
        fun validate(c: JsonObject) = js.eval("lani.validate(${q(c["kind"]!!.jsonPrimitive.content)}, ${q(c["input"]!!.jsonPrimitive.content)}, ${q(c["refs"]!!.jsonPrimitive.content)}, null)")
        step("check culture primorska") { cases.filter { it["kind"]!!.jsonPrimitive.content == "culture" }.forEach { validate(it) } }
        step("check ${cases.count { it["kind"]!!.jsonPrimitive.content in setOf("scene", "story") }} scenes and stories") {
            cases.filter { it["kind"]!!.jsonPrimitive.content in setOf("scene", "story") }.forEach { validate(it) }
        }
        val woven = mutableListOf<JsonObject>()
        cases { c -> if (c["kind"]!!.jsonPrimitive.content == "woven" && woven.size < 2) woven += c }
        step("mount lexicon-sl") { mounts.ensure("lexicon-sl") }
        step("refs primorska-woven (the learner's cards)") { js.eval("lani.setRefs('primorska-woven', ${q(read("refs/primorska-woven.json"))})") }
        val answers = mutableListOf<String>()
        step("woven: first (reads the dictionary)") { answers += validate(woven[0]) }
        step("woven: second") { answers += validate(woven[1]) }
        step("gc") { js.gc() }
        js.close()
        return buildJsonObject { put("steps", JsonArray(steps)); put("woven_answers", JsonArray(answers.map { JsonPrimitive(it.take(300)) })) }
    }

    /** The dictionary's main file (companion/lexicon/sl.json, from the corpus's mount): the biggest text a check needs. */
    fun dictionary(): String = Json.parseToJsonElement(read("mounts/lexicon-sl.json")).jsonObject["files"]!!.jsonObject["sl.json"]!!.jsonPrimitive.content

    /**
     * One way of handing [text] to an engine, timed and checked: [send] puts it in the script's global `__t` (and answers
     * its length), timed; then, untimed, `h(__t)` (its length and FNV-1a, [HASH_JS]) must be the text's own. Its error
     * when the way fails.
     */
    fun inputPath(js: Js, name: String, text: String, send: () -> String): JsonObject {
        js.eval(HASH_JS)
        val want = "${text.length}:${fnv1a(text)}"
        val t = now()
        val sent = runCatching { send() }
        val took = ms(t)
        val got = sent.mapCatching { js.eval("h(globalThis.__t)") }
        return buildJsonObject {
            put("path", name); put("chars", text.length); put("ms", took)
            got.onSuccess { put("ok", it == want); if (it != want) put("got", it.take(200)) }
            got.onFailure { put("ok", false); put("error", it.toString().take(400)) }
        }
    }

    companion object {
        /** h(s): its length and FNV-1a over its UTF-16 units; utf8(bytes): a Uint8Array's text (no TextDecoder needed). */
        const val HASH_JS = """
globalThis.h = s => { let x = 0x811c9dc5; for (let i = 0; i < s.length; i++) { x ^= s.charCodeAt(i); x = Math.imul(x, 0x01000193) >>> 0 } return s.length + ':' + x.toString(16).padStart(8, '0') };
globalThis.utf8 = b => { const out = []; let chunk = []; for (let i = 0; i < b.length;) { let c = b[i++];
  if (c >= 0xf0) { c = ((c & 7) << 18) | ((b[i++] & 63) << 12) | ((b[i++] & 63) << 6) | (b[i++] & 63); c -= 0x10000; chunk.push(0xd800 + (c >> 10), 0xdc00 + (c & 1023)) }
  else if (c >= 0xe0) chunk.push(((c & 15) << 12) | ((b[i++] & 63) << 6) | (b[i++] & 63))
  else if (c >= 0xc0) chunk.push(((c & 31) << 6) | (b[i++] & 63))
  else chunk.push(c)
  if (chunk.length > 8000) { out.push(String.fromCharCode.apply(null, chunk)); chunk = [] } }
  out.push(String.fromCharCode.apply(null, chunk)); return out.join('') };
'ok'"""

        fun fnv1a(s: String): String {
            var h = 0x811c9dc5.toInt()
            for (ch in s) { h = h xor ch.code; h *= 0x01000193 }
            return Integer.toHexString(h).padStart(8, '0')
        }
    }

    private fun quantiles(xs: List<Double>): JsonObject {
        val s = xs.sorted()
        fun at(p: Double) = s[minOf(s.size - 1, (p * s.size).toInt())]
        return buildJsonObject { put("n", s.size); put("median_ms", at(0.5)); put("p95_ms", at(0.95)); put("max_ms", s.last()); put("total_ms", s.sum()) }
    }

    /**
     * Runs everything in engines [make] makes: the start (cold, then warm), each bundle's load, the references, the
     * conformance pass (every case, with its digest), the timing pass (the curated files as the app checks them). [probe]:
     * a point's memory as the platform measures it ("loaded", "after_corpus"); [extra]: the engine's own measures.
     */
    fun run(make: () -> Js, probe: (String) -> Map<String, Long> = { emptyMap() }, extra: (Js, MutableMap<String, JsonElement>) -> Unit = { _, _ -> }): JsonObject {
        val out = linkedMapOf<String, JsonElement>()
        val probes = linkedMapOf<String, JsonElement>()
        probes["before"] = JsonObject(probe("before").mapValues { JsonPrimitive(it.value) })
        var t = now()
        val js = make()
        out["start_cold_ms"] = JsonPrimitive(ms(t))
        t = now(); make().close(); out["start_warm_ms"] = JsonPrimitive(ms(t))
        val core = read("validators.js")
        val full = read("validators-lexicon.js")
        make().use { e -> t = now(); e.eval(core); out["load_core_ms"] = JsonPrimitive(ms(t)) }
        t = now(); js.eval(full); out["load_full_ms"] = JsonPrimitive(ms(t))
        out["bundle_chars"] = buildJsonObject { put("validators.js", core.length); put("validators-lexicon.js", full.length) }
        out["kinds"] = JsonPrimitive(js.eval("lani.kinds()"))
        extra(js, out)
        probes["loaded"] = JsonObject(probe("loaded").mapValues { JsonPrimitive(it.value) })

        // the references: each set once
        val refsMs = linkedMapOf<String, JsonElement>()
        val refsChars = linkedMapOf<String, JsonElement>()
        for (f in names("refs").sorted()) {
            val name = f.removeSuffix(".json")
            val body = read("refs/$f")
            t = now(); js.eval("lani.setRefs(${q(name)}, ${q(body)})"); refsMs[name] = JsonPrimitive(ms(t))
            refsChars[name] = JsonPrimitive(body.length)
        }
        out["refs_ms"] = JsonObject(refsMs)
        out["refs_chars"] = JsonObject(refsChars)

        // the conformance pass
        val mounts = Mounts(js)
        val times = linkedMapOf<String, MutableList<Double>>()
        val mismatches = mutableListOf<JsonObject>()
        val parser = mutableListOf<JsonObject>()
        var n = 0
        var firstWoven: Double? = null
        var nextWoven: Double? = null
        cases { c ->
            n++
            val kind = c["kind"]!!.jsonPrimitive.content
            c["mounts"]?.jsonArray?.forEach { mounts.ensure(it.jsonPrimitive.content) }
            val refs = c["refs"].let { if (it == null || it is JsonNull) "null" else q(it.jsonPrimitive.content) }
            val script = "lani.validate(${q(kind)}, ${q(c["input"]!!.jsonPrimitive.content)}, $refs, '{\"digest\":true}')"
            t = now()
            val raw = runCatching { js.eval(script) }.getOrElse { """{"engine_error":${q(it.toString())}}""" }
            val took = ms(t)
            if (kind == "woven") { if (firstWoven == null) firstWoven = took else if (nextWoven == null) nextWoven = took }
            times.getOrPut(kind) { mutableListOf() }.add(took)
            val got = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrElse { JsonObject(mapOf("unparsable" to JsonPrimitive(raw))) }
            val want = c["expected"]!!.jsonObject
            val id = c["id"]!!.jsonPrimitive.content
            if (want["json"] == JsonPrimitive(false)) {
                if (got["json"] != JsonPrimitive(false) || got["ok"] != JsonPrimitive(false)) mismatches += buildJsonObject { put("id", id); put("got", got); put("expected", want) }
                else if (got["errors"] != want["errors"]) parser += buildJsonObject { put("id", id); put("engine", got["errors"] ?: JsonNull); put("bun", want["errors"] ?: JsonNull) }
            } else if (got != want) mismatches += buildJsonObject { put("id", id); put("got", got); put("expected", want) }
        }
        out["cases"] = JsonPrimitive(n)
        out["mismatches"] = JsonPrimitive(mismatches.size)
        out["mismatch_list"] = JsonArray(mismatches.take(80))
        out["parser_messages"] = JsonArray(parser)
        out["mount_ms"] = JsonObject(mounts.took.mapValues { JsonPrimitive(it.value) })
        out["mount_chars"] = JsonObject(mounts.chars.mapValues { JsonPrimitive(it.value) })
        out["woven_first_ms"] = JsonPrimitive(firstWoven)
        out["woven_second_ms"] = JsonPrimitive(nextWoven)
        out["conformance_ms"] = JsonObject(times.mapValues { quantiles(it.value) })

        // the timing pass: each curated file as the app would check it (no digest)
        val curated = linkedMapOf<String, MutableList<Double>>()
        var biggest: Triple<String, Int, Double>? = null
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
            if (kind == "scene" && input.length > (biggest?.second ?: 0)) biggest = Triple(c["id"]!!.jsonPrimitive.content, input.length, took)
        }
        out["curated_ms"] = JsonObject(curated.mapValues { quantiles(it.value) })
        out["curated_files_ms"] = quantiles(curated.filterKeys { it != "culture" && it != "learner" }.values.flatten())
        out["biggest_scene"] = buildJsonObject { put("id", biggest?.first); put("chars", biggest?.second); put("ms", biggest?.third) }
        out["memory_engine"] = JsonObject(js.memory().mapValues { JsonPrimitive(it.value) })
        probes["after_corpus"] = JsonObject(probe("after_corpus").mapValues { JsonPrimitive(it.value) })
        out["memory_platform"] = JsonObject(probes)
        js.close()
        return JsonObject(out)
    }
}
