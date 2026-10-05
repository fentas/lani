package si.lanisce.lani.l10n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The string tables against the code and each other. A key the code uses must be in sl and en (Jan's pair, and the
 * fallbacks); another table's missing keys are a report, not a failure: they show in English, then Slovene.
 */
class L10nTablesTest {
    private val sources: List<Pair<String, String>> by lazy {
        val src = File("src/main/java")
        check(src.isDirectory) { "run from the app module (${File(".").absolutePath})" }
        src.walk().filter { it.extension == "kt" }.map { it.relativeTo(src).path to it.readText() }.toList()
    }

    /**
     * Keys the code shows: the first string argument of bi, inTarget, inBase, L10n.text and L10n.label (the functions,
     * not a culture pack text's methods of the same names: `lines.won.bi("name" to …)`).
     */
    private val used: Map<String, List<String>> by lazy {
        val call = Regex("""(?<![.\w])(?:bi|inTarget|inBase|L10n\.text|L10n\.label)\((?:[^"()]*,\s*)?"([A-Za-z0-9_.]+)"""")
        sources.flatMap { (path, text) -> call.findAll(text).map { it.groupValues[1] to path } }.groupBy({ it.first }, { it.second })
    }

    /** Keys the code names anywhere, e.g. in a list it picks from ("townScroll.monday"). */
    private val named: Set<String> by lazy {
        val literal = Regex(""""([a-z][A-Za-z0-9]*\.[A-Za-z0-9.]+)"""")
        sources.flatMap { (_, text) -> literal.findAll(text).map { it.groupValues[1] } }.toSet()
    }

    private val all: Set<String> by lazy { used.keys + L10n.table(Lang.SL).keys + L10n.table(Lang.EN).keys }

    private fun missing(lang: Lang) = (all - L10n.table(lang).keys).sorted()

    @Test
    fun `every key the code uses is in the sl and en tables`() {
        for (lang in listOf(Lang.SL, Lang.EN)) {
            val m = missing(lang)
            assertTrue("${lang.code}.json misses ${m.size}: " + m.joinToString { "$it (${used[it]?.distinct()?.joinToString()})" }, m.isEmpty())
        }
    }

    @Test
    fun `the tables report what they miss`() {
        // A report, not a failure: a missing key shows in English, then Slovene. build/reports/l10n/missing.txt
        val report = Lang.entries.joinToString("\n\n") { lang ->
            val m = missing(lang)
            "${lang.code}.json: ${all.size - m.size} of ${all.size} keys" + if (m.isEmpty()) "" else ", missing ${m.size}:\n" + m.joinToString("\n") { "  $it" }
        }
        File("build/reports/l10n").apply { mkdirs() }.resolve("missing.txt").writeText(report + "\n")
        println(report)
    }

    @Test
    fun `no table keeps a key nobody uses`() {
        val unused = (L10n.table(Lang.SL).keys + L10n.table(Lang.EN).keys) - used.keys - named
        assertTrue("unused keys: ${unused.sorted()}", unused.isEmpty())
        for (lang in Lang.entries) {
            val extra = L10n.table(lang).keys - all
            assertTrue("${lang.code}.json has keys no other table has: ${extra.sorted()}", extra.isEmpty())
        }
    }

    @Test
    fun `every message parses, with the Slovene message's arguments`() {
        val sl = L10n.table(Lang.SL)
        for (lang in Lang.entries) for ((key, msg) in L10n.table(lang)) {
            val m = runCatching { Message.parse(msg) }.getOrElse { throw AssertionError("${lang.code} $key: ${it.message}") }
            // targetLang and learnerGender come with every message (L10n.text), whether the code passes them or not
            val want = Message.parse(sl[key] ?: continue).args + Message.parse(L10n.table(Lang.EN)[key] ?: continue).args + L10n.TARGET_LANG + L10n.LEARNER_GENDER
            assertTrue("${lang.code} $key uses ${m.args - want}, which the code doesn't pass", (m.args - want).isEmpty())
        }
    }

    @Test
    fun `sl and en hold the same keys`() {
        assertEquals((L10n.table(Lang.SL).keys - L10n.table(Lang.EN).keys).sorted(), emptyList<String>())
        assertEquals((L10n.table(Lang.EN).keys - L10n.table(Lang.SL).keys).sorted(), emptyList<String>())
    }

    @Test
    fun `every key the code names is in the tables`() {
        // a key in a list the code picks from, not in a call: a typo would show as the key itself
        val tables = L10n.table(Lang.SL).keys
        val prefixes = tables.map { it.substringBefore('.') }.toSet()
        val file = Regex("""\.(json|txt|png|webp|mp3|m4a|wav|ogg)$""")
        val missing = named.filter { it.substringBefore('.') in prefixes && it !in tables && !file.containsMatchIn(it) }
        assertTrue("keys the code names but no table has: $missing", missing.isEmpty())
    }
}
