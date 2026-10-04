package si.lanisce.lani.l10n

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jan's screens read exactly as before: every literal the extraction (companion/android/tools/l10n_extract.py) moved
 * into the tables, rendered for sl · en with sample arguments, is the text the old literal made
 * (src/test/resources/l10n/parity.json keeps the old literals, and what they rendered for each sample).
 */
class L10nParityTest {
    @Serializable
    data class Sample(val args: Map<String, JsonPrimitive> = emptyMap(), val prefix: String = "", val suffix: String = "", val expect: String)

    /** [kind]: "bi" for a "target · base" label, "target" or "base" for one shown in one language (inTarget, inBase). */
    @Serializable
    data class Entry(val file: String, val key: String, val old: String, val samples: List<Sample>, val kind: String = "bi")

    private val entries: List<Entry> = Json.decodeFromString(
        checkNotNull(javaClass.getResourceAsStream("/l10n/parity.json")) { "the parity fixture" }.use { it.readBytes().decodeToString() },
    )

    @Test
    fun `every extracted literal renders for sl · en as the old literal did`() {
        assertTrue("the fixture is empty", entries.isNotEmpty())
        val wrong = entries.flatMap { e ->
            e.samples.mapNotNull { s ->
                val args = s.args.mapValues { (_, v) -> v.intOrNull ?: v.content }
                val text = when (e.kind) {
                    "target" -> L10n.text(LangPair.DEFAULT.target, e.key, args)
                    "base" -> L10n.text(LangPair.DEFAULT.base, e.key, args)
                    else -> L10n.label(LangPair.DEFAULT, e.key, args)
                }
                val got = s.prefix + text + s.suffix
                "${e.file} ${e.key} ${s.args}:\n  was «${s.expect}»\n  now «$got»".takeIf { got != s.expect }
            }
        }
        assertTrue("${wrong.size} of ${entries.sumOf { it.samples.size }} render differently:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun `every sample renders in the other pairs too, every argument filled`() {
        // all 12 pairs among sl, en, it and de
        val pairs = Lang.entries.flatMap { t -> Lang.entries.filter { it != t }.map { b -> LangPair(t, b) } }
        val wrong = entries.flatMap { e ->
            e.samples.flatMap { s ->
                val args = s.args.mapValues { (_, v) -> v.intOrNull ?: v.content }
                pairs.mapNotNull { p ->
                    val got = L10n.label(p, e.key, args)
                    "$p ${e.key} ${s.args}: «$got»".takeIf { got.contains(Regex("""\{\w+[},]""")) || got.isBlank() }
                }
            }
        }
        assertTrue("${wrong.size} leave a placeholder:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }
}
