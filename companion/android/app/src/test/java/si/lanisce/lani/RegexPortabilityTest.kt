package si.lanisce.lani

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app's regexes compile on Android too. Android's regex engine (ICU) rejects a brace that isn't a quantifier and
 * isn't escaped, where the JVM's, which these tests run on, reads it as a literal: `Regex("""\{n}""")` passes every
 * unit test and throws on the phone (the village's counting dialogs crashed that way, 0.1.452 to 0.1.467). So this
 * reads the sources' regex literals and asks for every such brace to be escaped.
 */
class RegexPortabilityTest {

    private val sources = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }

    /** `Regex("""…""")`, `Regex("…")`, `"""…""".toRegex()` and `"…".toRegex()`: the pattern as the regex engine gets it. */
    private val literal = Regex(
        "Regex\\(\\s*(?:\"\"\"([\\s\\S]*?)\"\"\"|\"((?:[^\"\\\\]++|\\\\.)*+)\")|(?:\"\"\"([\\s\\S]*?)\"\"\"|\"((?:[^\"\\\\]++|\\\\.)*+)\")\\.toRegex\\(",
    )

    /** The braces in [p] that ICU would reject: outside a quantifier (`{2}`, `{1,3}`), `\p{L}` and a Kotlin `${…}`. */
    private fun loneBraces(p: String): List<Int> {
        val bad = mutableListOf<Int>()
        var i = 0
        var inClass = false
        fun skipTo(close: Char, from: Int): Int = p.indexOf(close, from).let { if (it < 0) p.length else it + 1 }
        while (i < p.length) {
            val c = p[i]
            when {
                c == '\\' && i + 2 < p.length && p[i + 1] in "pPN" && p[i + 2] == '{' -> i = skipTo('}', i + 3)
                c == '\\' -> i += 2
                c == '$' && i + 1 < p.length && p[i + 1] == '{' -> i = skipTo('}', i + 2)
                inClass && c == ']' -> { inClass = false; i++ }
                inClass && (c == '{' || c == '}') -> { bad += i; i++ }
                inClass -> i++
                c == '[' -> { inClass = true; i++; if (i < p.length && p[i] == '^') i++; if (i < p.length && p[i] == ']') i++ }
                c == '{' -> {
                    val q = Regex("""\{\d+(,\d*)?\}""").matchAt(p, i)
                    if (q != null) i = q.range.last + 1 else { bad += i; i++ }
                }
                c == '}' -> { bad += i; i++ }
                else -> i++
            }
        }
        return bad
    }

    @Test fun `every brace in the app's regexes is a quantifier or escaped, as Android's regex engine wants`() {
        val found = mutableListOf<String>()
        var patterns = 0
        for (f in sources.walkTopDown().filter { it.extension == "kt" }) {
            val src = f.readText()
            for (m in literal.findAll(src)) {
                val raw = m.groups[1]?.value ?: m.groups[3]?.value
                val quoted = m.groups[2]?.value ?: m.groups[4]?.value
                // an escaped string's pattern: its "\\" is one backslash
                val p = raw ?: quoted!!.replace("\\\\", "\\")
                patterns++
                val bad = loneBraces(p)
                if (bad.isNotEmpty()) found += "${f.relativeTo(sources)}:${src.substring(0, m.range.first).count { it == '\n' } + 1}: $p"
            }
        }
        assertTrue("regex literals found: $patterns", patterns > 50)
        assertTrue("unescaped braces (escape them: \\{ \\}):\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test fun `the check finds a lone brace and passes the rest`() {
        assertTrue(loneBraces("""\{n}""").isNotEmpty())
        assertTrue(loneBraces("""\{(!?)([^{}]*)\}""").isNotEmpty())
        assertTrue(loneBraces("""\{(!?)([^\{\}]*)\}""").isEmpty())
        assertTrue(loneBraces("""\p{L}+\d{2,4}x{3}""").isEmpty())
        assertTrue(loneBraces("(^|[^\\p{L}])\${Regex.escape(w)}($|[^\\p{L}])").isEmpty())
    }
}
