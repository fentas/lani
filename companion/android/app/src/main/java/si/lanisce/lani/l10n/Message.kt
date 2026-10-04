package si.lanisce.lani.l10n

import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * One message of a string table, in ICU MessageFormat syntax (the part of it labels need):
 * - `{name}`: an argument, written as Kotlin writes it in a string template (so 1000 stays "1000", as the literals
 *   had it; MessageFormat would group it by locale, and turning that off needs API 30);
 * - `{name, plural, =0 {…} one {…} two {…} few {…} many {…} other {…}}`: the branch for the number's CLDR plural
 *   category in the message's language, from ICU's PluralRules ([PluralCategories]): the Slovene dual, the Italian
 *   and German one/other. `=N` matches that number exactly and goes first; `#` in a branch is the number;
 * - `{name, select, a {…} b {…} other {…}}`: the branch named by the argument's text.
 *
 * Apostrophes are plain text (Italian needs many of them), unlike ICU's quoting; a table has no use for literal braces.
 */
class Message private constructor(private val parts: List<Part>) {
    private sealed interface Part
    private data class Text(val s: String) : Part
    private data class Arg(val name: String) : Part
    private data class Choice(val name: String, val plural: Boolean, val branches: Map<String, List<Part>>) : Part
    private data object Hash : Part

    /** The names of the arguments this message reads. */
    val args: Set<String> by lazy { buildSet { collect(parts) } }

    private fun MutableSet<String>.collect(ps: List<Part>) {
        for (p in ps) when (p) {
            is Arg -> add(p.name)
            is Choice -> { add(p.name); p.branches.values.forEach { collect(it) } }
            else -> Unit
        }
    }

    /** The text in [lang] (whose plural rules pick the branches) with [args]; a missing argument shows as `{name}`. */
    fun format(lang: Lang, args: Map<String, Any?>): String = buildString { write(parts, lang, args, null) }

    private fun StringBuilder.write(ps: List<Part>, lang: Lang, args: Map<String, Any?>, number: Any?) {
        for (p in ps) when (p) {
            is Text -> append(p.s)
            is Arg -> if (p.name in args) append(args[p.name]) else append('{').append(p.name).append('}')
            Hash -> append(number)
            is Choice -> {
                val v = args[p.name]
                val branch = if (p.plural) {
                    val n = (v as? Number) ?: v?.toString()?.toDoubleOrNull()
                    val d = n?.toDouble()
                    d?.let { x -> p.branches.entries.firstOrNull { it.key.startsWith("=") && it.key.drop(1).toDoubleOrNull() == x }?.value }
                        ?: d?.let { p.branches[PluralCategories.select(lang, it)] }
                        ?: p.branches["other"]
                } else {
                    p.branches[v?.toString()] ?: p.branches["other"]
                }
                branch?.let { write(it, lang, args, if (p.plural) v else number) }
            }
        }
    }

    override fun toString() = parts.toString()

    companion object {
        private val cache = ConcurrentHashMap<String, Message>()

        /** [pattern] parsed once, then from the cache. Throws [IllegalArgumentException] on a malformed pattern. */
        fun of(pattern: String): Message = cache[pattern] ?: parse(pattern).also { cache[pattern] = it }

        fun parse(pattern: String): Message {
            val p = Parser(pattern)
            val parts = p.parts(inPlural = false)
            if (p.i < pattern.length) p.fail("unmatched }")
            return Message(parts)
        }
    }

    private class Parser(val s: String) {
        var i = 0

        fun fail(why: String): Nothing = throw IllegalArgumentException("$why at $i in message \"$s\"")

        /** Parts up to the end, or up to (not past) a closing brace. */
        fun parts(inPlural: Boolean): List<Part> {
            val out = mutableListOf<Part>()
            val text = StringBuilder()
            fun flush() { if (text.isNotEmpty()) out += Text(text.toString()).also { text.clear() } }
            while (i < s.length) {
                when (val c = s[i]) {
                    '}' -> break
                    '{' -> { flush(); out += arg(inPlural) }
                    '#' -> if (inPlural) { flush(); out += Hash; i++ } else { text.append(c); i++ }
                    else -> { text.append(c); i++ }
                }
            }
            flush()
            return out
        }

        private fun word(): String {
            skipSpace()
            val start = i
            while (i < s.length && s[i] !in ",{} \t\n") i++
            return s.substring(start, i).also { if (it.isEmpty()) fail("expected a name") }
        }

        private fun skipSpace() { while (i < s.length && s[i].isWhitespace()) i++ }

        private fun expect(c: Char) {
            skipSpace()
            if (i >= s.length || s[i] != c) fail("expected $c")
            i++
        }

        /** `{name}`, `{name, plural, …}` or `{name, select, …}`, from its opening brace. */
        private fun arg(inPlural: Boolean): Part {
            i++ // {
            val name = word()
            skipSpace()
            if (i < s.length && s[i] == '}') { i++; return Arg(name) }
            expect(',')
            val type = word()
            if (type != "plural" && type != "select") fail("unknown argument type $type")
            skipSpace()
            if (i < s.length && s[i] == '}') fail("$type without branches")
            expect(',')
            val branches = linkedMapOf<String, List<Part>>()
            while (true) {
                skipSpace()
                if (i >= s.length) fail("unclosed {$name")
                if (s[i] == '}') { i++; break }
                val key = word()
                expect('{')
                branches[key] = parts(inPlural = type == "plural" || inPlural)
                expect('}')
            }
            if ("other" !in branches) fail("{$name, $type} without an other branch")
            return Choice(name, type == "plural", branches)
        }
    }
}

/**
 * CLDR plural categories ("zero", "one", "two", "few", "many", "other") of a number in a language: ICU's
 * PluralRules. On the phone that's android.icu; a plain JVM (the unit tests) has only android.jar's stubs, so it
 * registers ICU4J as a service (src/test/resources/META-INF/services), the same library by its own name.
 */
fun interface PluralCategories {
    fun category(lang: Lang, n: Double): String

    companion object {
        private val rules: PluralCategories by lazy {
            ServiceLoader.load(PluralCategories::class.java, PluralCategories::class.java.classLoader).firstOrNull() ?: AndroidIcuPlurals()
        }

        fun select(lang: Lang, n: Double): String = rules.category(lang, n)
    }
}

private class AndroidIcuPlurals : PluralCategories {
    private val byLang = ConcurrentHashMap<Lang, android.icu.text.PluralRules>()

    override fun category(lang: Lang, n: Double): String =
        byLang.getOrPut(lang) { android.icu.text.PluralRules.forLocale(android.icu.util.ULocale.forLanguageTag(lang.code)) }.select(n)
}
