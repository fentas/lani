package si.lanisce.lani.tutor

/**
 * A tool call's input as it streams (plan 5, "Streaming into the chat sheet"): the call's [id] and [name], and its
 * input's JSON so far ([json], usually cut off mid-way). The Messages API streams it with `eager_input_streaming` (its
 * input_json_delta events), the Responses API as function-call argument deltas.
 */
data class ToolInputDelta(val id: String, val name: String, val json: String)

/**
 * A partial parse of streamed JSON: one string field of an object cut off anywhere ([string]). Enough for the chat's
 * live bubble: the `reply` tool's `text` as far as it is written. The final, validated input replaces it.
 */
object PartialJson {
    /**
     * The value of the top-level string field [field] in [json] (an object's text so far): its characters as far as they
     * came, escapes decoded (an escape cut off is left out); null while the field hasn't begun. A nested object's field
     * of the same name is skipped: only the top level counts.
     */
    fun string(json: String, field: String): String? {
        var i = json.indexOf('{')
        if (i < 0) return null
        i++
        var depth = 1
        while (i < json.length) {
            val c = json[i]
            when {
                c == '"' -> {
                    val (key, end) = readString(json, i + 1)
                    if (end < 0) return null // a key or a value cut off: not the field yet
                    i = end
                    // a key is followed by a colon: at depth 1, the field's value is a string
                    var j = i
                    while (j < json.length && json[j].isWhitespace()) j++
                    if (j < json.length && json[j] == ':' && depth == 1 && key == field) {
                        j++
                        while (j < json.length && json[j].isWhitespace()) j++
                        if (j >= json.length) return null
                        if (json[j] != '"') return null // not a string
                        return readString(json, j + 1).first
                    }
                }
                c == '{' || c == '[' -> { depth++; i++ }
                c == '}' || c == ']' -> { depth--; i++ }
                else -> i++
            }
        }
        return null
    }

    /** The string from [start] (after its opening quote): its decoded text and the index after its closing quote (-1: cut off). */
    private fun readString(s: String, start: Int): Pair<String, Int> {
        val out = StringBuilder()
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (c == '"') return out.toString() to i + 1
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            if (i + 1 >= s.length) return out.toString() to -1 // an escape cut off
            when (val e = s[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'b' -> out.append('\b')
                'f' -> out.append('\u000c')
                '/', '\\', '"' -> out.append(e)
                'u' -> {
                    if (i + 6 > s.length) return out.toString() to -1
                    val code = s.substring(i + 2, i + 6).toIntOrNull(16) ?: return out.toString() to -1
                    out.append(code.toChar())
                    i += 6
                    continue
                }
                else -> out.append(e)
            }
            i += 2
        }
        return out.toString() to -1
    }
}
