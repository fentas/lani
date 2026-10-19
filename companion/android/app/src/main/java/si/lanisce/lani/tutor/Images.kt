package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.Base64

/**
 * A chat's photo for the shadow tutor (companion/README.md "Tutor chat", "Photos"): the real tutor opens the node's
 * copy with its Read tool; the in-app loop has no files, so the photo goes in the turn's user message as an image block
 * (base64 JPEG, before the event's text), as the Messages API takes one (the Responses API's thread makes it an
 * input_image with a data URL: [OpenAIFormat.part]). Its tokens are the call's usage, metered as
 * any; the call's record counts the images it carried ([CallRecord.images]). The photo itself never leaves the phone
 * but to the API: the export (the share sheet's file and the node's copy, app/DevUpload) has a note in its place ([omit]).
 */
object ShadowImages {
    /** The image block of the JPEG [file]. */
    fun block(file: File): JsonObject = buildJsonObject {
        put("type", "image")
        putJsonObject("source") {
            put("type", "base64")
            put("media_type", "image/jpeg")
            put("data", Base64.getEncoder().encodeToString(file.readBytes()))
        }
    }

    /**
     * How many images [messages] carry (the Messages API's image blocks, the Responses API's input_image parts, chat
     * completions' image_url parts, Gemini's inlineData parts).
     */
    fun count(messages: List<JsonObject>): Int = messages.sumOf { m ->
        m.blocks().count { it.str("type") == "image" || it.str("type") == "input_image" || it.str("type") == "image_url" } +
            GeminiFormat.parts(m).count { it["inlineData"] is JsonObject }
    }

    /**
     * [e] with every image's data replaced by a note of its size ("‹an image, 312 KB, left out›"): what the export keeps
     * of a photo the shadow sent (an image block's base64 data, an input_image's data URL); and a reasoning item's
     * encrypted content by a note of its length (opaque to anyone but OpenAI, and large).
     */
    fun omit(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> {
            val source = e["source"] as? JsonObject
            val data = (source?.get("data") as? JsonPrimitive)?.takeIf { it.isString }?.content
            val url = e.str("image_url")
            when {
                e.str("type") == "image" && source != null && data != null ->
                    JsonObject(e + ("source" to JsonObject(source + ("data" to JsonPrimitive(note(data.length))))))
                e.str("type") == "input_image" && url != null && url.startsWith("data:") ->
                    JsonObject(e + ("image_url" to JsonPrimitive(url.substringBefore(',') + "," + note(url.length - url.indexOf(',') - 1))))
                // Gemini's inlineData, chat completions' image_url: the data left out the same way
                e["inlineData"] is JsonObject && (e["inlineData"] as JsonObject).str("data") != null -> {
                    val d = e["inlineData"] as JsonObject
                    JsonObject(e + ("inlineData" to JsonObject(d + ("data" to JsonPrimitive(note(d.str("data")!!.length))))))
                }
                e.str("type") == "image_url" && (e["image_url"] as? JsonObject)?.str("url")?.startsWith("data:") == true -> {
                    val u = (e["image_url"] as JsonObject).str("url")!!
                    JsonObject(e + ("image_url" to JsonObject(mapOf("url" to JsonPrimitive(u.substringBefore(',') + "," + note(u.length - u.indexOf(',') - 1))))))
                }
                e.str("type") == "reasoning" && e.str("encrypted_content") != null ->
                    JsonObject(e + ("encrypted_content" to JsonPrimitive("‹encrypted reasoning, ${e.str("encrypted_content")!!.length} characters, left out›")))
                else -> JsonObject(e.mapValues { (_, v) -> omit(v) })
            }
        }
        is JsonArray -> JsonArray(e.map(::omit))
        else -> e
    }

    private fun note(base64Chars: Int) = "‹an image, ${base64Chars * 3 / 4 / 1024} KB, left out›"
}
