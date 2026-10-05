package si.lanisce.lani.l10n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The curated content said to Jan (a man: the name it had before its placeholders) reads exactly as it did at the commit
 * the placeholders came in after, file by file (bridge/test/fixtures/learner-neutral.json, bridge/test/learner-content.ts:
 * the same texts are the same voice clips; the smoke checks the bridge's rendering the same way). A file edited since
 * isn't compared. And said to a woman, the lines the fixture samples agree with her.
 */
class LearnerContentTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "bridge/test/fixtures").isDirectory }
    private val repo = companion.canonicalFile.parentFile
    private val fixture = Json.parseToJsonElement(File(companion, "bridge/test/fixtures/learner-neutral.json").readText()).jsonObject

    /** As learner-content.ts canonical(): each text a "path<TAB>text" line, the keys sorted. */
    private fun canonical(e: JsonElement): String {
        val lines = ArrayList<String>()
        fun walk(x: JsonElement, at: String) {
            when (x) {
                is JsonPrimitive -> if (x.isString) lines += "$at\t${x.content}"
                is JsonArray -> x.forEachIndexed { i, y -> walk(y, "$at/$i") }
                is JsonObject -> x.keys.sorted().forEach { k -> walk(x.getValue(k), "$at/$k") }
            }
        }
        walk(e, "")
        return lines.joinToString("\n")
    }

    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test fun `said to Jan, every text of the curated content reads as it did`() {
        val jan = Learner.of("Jan", "male")
        val files = fixture.getValue("files").jsonObject
        var compared = 0
        val differ = ArrayList<String>()
        for ((path, h) in files) {
            val f = File(repo, path)
            if (!f.isFile) continue
            val raw = f.readText()
            if (sha256(raw) != h.jsonObject.getValue("after").jsonPrimitive.content) continue // edited since
            compared++
            if (sha256(canonical(Json.parseToJsonElement(jan.renderJson(raw)))) != h.jsonObject.getValue("before").jsonPrimitive.content) differ += path
        }
        assertTrue("compared $compared of ${files.size}", compared > files.size / 2)
        assertEquals("files whose texts said to Jan differ from before", emptyList<String>(), differ)
    }

    @Test fun `said to a woman, the sampled lines agree with her`() {
        val render = Json.parseToJsonElement(File(companion, "bridge/test/fixtures/learner-render.json").readText()).jsonObject
        val samples = (render["content"] as? JsonArray).orEmpty().map { it.jsonObject }
        val learner = render["content_learner"]?.jsonObject
        if (samples.isEmpty() || learner == null) return
        val l = Learner.of(learner["name"]?.jsonPrimitive?.content, learner["gender"]?.jsonPrimitive?.content)
        for (s in samples) {
            val path = s.getValue("file").jsonPrimitive.content
            var at: JsonElement = Json.parseToJsonElement(l.renderJson(File(repo, path).readText()))
            for (step in s.getValue("path").jsonPrimitive.content.split('/').filter { it.isNotEmpty() }) {
                at = if (at is JsonArray) at[step.toInt()] else at.jsonObject.getValue(step)
            }
            assertEquals("$path ${s.getValue("path").jsonPrimitive.content}", s.getValue("said").jsonPrimitive.content, at.jsonPrimitive.content)
        }
    }
}
