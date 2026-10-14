package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The part of JSON Schema the tools' input schemas use (the bridge's MCP schemas, the app's own): `type` (one or a
 * list), `properties`, `required`, `additionalProperties`, `enum`, `const`, `items`, `minLength`/`maxLength`,
 * `minimum`/`maximum`, `minItems`/`maxItems`, `pattern`, `anyOf`/`oneOf`. A tool's input is checked before it runs or
 * (a write in shadow mode) is recorded as a dry run: the model reads the errors, as it reads the bridge's `fail()`.
 */
object Schema {
    /** What's wrong with [value] against [schema] ("$.text: required"), at most [max]; empty when it fits. */
    fun check(schema: JsonObject, value: JsonElement, max: Int = 12): List<String> {
        val out = mutableListOf<String>()
        walk(schema, value, "$", out)
        return out.take(max)
    }

    private fun typeOf(v: JsonElement): String = when (v) {
        JsonNull -> "null"
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonPrimitive -> when {
            v.isString -> "string"
            v.booleanOrNull != null -> "boolean"
            v.longOrNull != null -> "integer"
            v.doubleOrNull != null -> "number"
            else -> "string"
        }
    }

    private fun fits(type: String, v: JsonElement): Boolean {
        val t = typeOf(v)
        return t == type || (type == "number" && t == "integer")
    }

    private fun walk(s: JsonObject, v: JsonElement, at: String, out: MutableList<String>) {
        val types = when (val t = s["type"]) {
            is JsonPrimitive -> listOf(t.content)
            is JsonArray -> t.mapNotNull { (it as? JsonPrimitive)?.content }
            else -> emptyList()
        }
        if (types.isNotEmpty() && types.none { fits(it, v) }) {
            out += "$at: expected ${types.joinToString(" or ")}, got ${typeOf(v)}"
            return
        }
        (s["enum"] as? JsonArray)?.let { e -> if (v !in e) out += "$at: must be one of ${e.joinToString(", ")}" }
        s["const"]?.let { c -> if (v != c) out += "$at: must be $c" }
        (s["anyOf"] as? JsonArray ?: s["oneOf"] as? JsonArray)?.let { alts ->
            val ok = alts.any { a -> (a as? JsonObject)?.let { mutableListOf<String>().also { e -> walk(it, v, at, e) }.isEmpty() } == true }
            if (!ok) out += "$at: fits none of its ${alts.size} forms"
        }
        when (v) {
            is JsonPrimitive -> if (v.isString) {
                val len = v.content.codePointCount(0, v.content.length)
                s.int("minLength")?.let { if (len < it) out += "$at: at least $it characters" }
                s.int("maxLength")?.let { if (len > it) out += "$at: at most $it characters (has $len)" }
                s.string("pattern")?.let { p -> runCatching { Regex(p) }.getOrNull()?.let { if (!it.containsMatchIn(v.content)) out += "$at: doesn't match $p" } }
            } else v.doubleOrNull?.let { n ->
                s.number("minimum")?.let { if (n < it) out += "$at: at least $it" }
                s.number("maximum")?.let { if (n > it) out += "$at: at most $it" }
            }
            is JsonArray -> {
                s.int("minItems")?.let { if (v.size < it) out += "$at: at least $it items" }
                s.int("maxItems")?.let { if (v.size > it) out += "$at: at most $it items" }
                (s["items"] as? JsonObject)?.let { item -> v.forEachIndexed { i, e -> walk(item, e, "$at[$i]", out) } }
            }
            is JsonObject -> {
                val props = s["properties"] as? JsonObject
                (s["required"] as? JsonArray)?.forEach { r ->
                    val k = (r as? JsonPrimitive)?.content ?: return@forEach
                    if (k !in v) out += "$at.$k: required"
                }
                for ((k, e) in v) {
                    val p = props?.get(k) as? JsonObject
                    when {
                        p != null -> walk(p, e, "$at.$k", out)
                        s["additionalProperties"] == JsonPrimitive(false) -> out += "$at.$k: not a field of this tool"
                        s["additionalProperties"] is JsonObject -> walk(s["additionalProperties"] as JsonObject, e, "$at.$k", out)
                    }
                }
            }
            JsonNull -> Unit
        }
    }

    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.number(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.string(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
