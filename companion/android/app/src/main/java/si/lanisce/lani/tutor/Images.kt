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
 * (base64 JPEG, before the event's text), as the Messages API takes one. Its tokens are the call's usage, metered as
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

    /** How many image blocks [messages] carry. */
    fun count(messages: List<JsonObject>): Int = messages.sumOf { m -> m.blocks().count { it.str("type") == "image" } }

    /**
     * [e] with every image block's data replaced by a note of its size ("‹an image, 312 KB, left out›"): what the export
     * keeps of a photo the shadow sent.
     */
    fun omit(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> {
            val source = e["source"] as? JsonObject
            val data = (source?.get("data") as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (e.str("type") == "image" && source != null && data != null) {
                JsonObject(e + ("source" to JsonObject(source + ("data" to JsonPrimitive("‹an image, ${data.length * 3 / 4 / 1024} KB, left out›")))))
            } else JsonObject(e.mapValues { (_, v) -> omit(v) })
        }
        is JsonArray -> JsonArray(e.map(::omit))
        else -> e
    }
}
