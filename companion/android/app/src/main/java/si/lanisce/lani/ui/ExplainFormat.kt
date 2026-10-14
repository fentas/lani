package si.lanisce.lani.ui

/**
 * An exercise's explanation as the feedback shows it (Markdown): one written in markdown as it is; a plain one in lines,
 * without rewording it. Pure.
 *
 * - one sentence per line ("dela: … she. I work = delam, …" in two);
 * - a sentence of "X = Y" pairs ("I work = delam, you work = delaš", apart by commas or semicolons) as a small table of
 *   two columns;
 * - a line's leading "word:" (one or two words: "dela:", "Masculine:", "on/ona:") in bold, as what it is about;
 * - an ending written with its dash ("-m", "-š", "(-a)") in bold.
 */
object ExplainFormat {
    /** Markdown's own marks: an explanation with one is formatted already. */
    private val MARKDOWN = Regex("""\*\*|\n|^\s*([-*•] |\d+[.)] |\||#|>)|`""")

    /** A sentence's end: ".", "!", "?", "…" before a space and a capital, a digit or a quote. */
    private val SENTENCE = Regex("""(?<=[.!?…])\s+(?=[\p{Lu}\p{N}„«"(])""")

    /** What ends with a full stop without ending a sentence. */
    private val ABBREVIATIONS = setOf("npr", "itd", "tj", "oz", "idr", "etc", "eg", "ie", "vs", "cf", "approx", "mr", "mrs", "dr", "st", "sv")

    private val PAIR = Regex("""^\s*([^=]+?)\s+=\s+([^=]+?)\s*$""")
    private val SUBJECT = Regex("""^([\p{L}'’]+(?:[ /][\p{L}'’]+)?):(?=\s)""")
    private val ENDING = Regex("""(?<![\p{L}\p{N}_-])-\p{L}{1,4}(?![\p{L}\p{N}-])""")

    fun format(explain: String): String {
        val text = explain.trim()
        if (text.isEmpty() || MARKDOWN.containsMatchIn(text)) return text
        return sentences(text).joinToString("\n") { line(it) }
    }

    /** [text] in sentences, a full stop after an abbreviation ("e.g.", "npr.") not ending one. */
    internal fun sentences(text: String): List<String> {
        val out = ArrayList<String>()
        var from = 0
        for (m in SENTENCE.findAll(text)) {
            val before = text.substring(from, m.range.first)
            val last = Regex("""([\p{L}.]+)\.$""").find(before)?.groupValues?.get(1)?.replace(".", "")?.lowercase()
            if (last != null && last in ABBREVIATIONS) continue
            out += before.trim()
            from = m.range.last + 1
        }
        out += text.substring(from).trim()
        return out.filter { it.isNotEmpty() }
    }

    /** One sentence: its pairs as a table, else its subject and its endings in bold. */
    private fun line(sentence: String): String {
        pairs(sentence)?.let { rows -> return rows.joinToString("\n") { (x, y) -> "| ${endings(x)} | ${endings(y)} |" } }
        val subject = SUBJECT.find(sentence) ?: return endings(sentence)
        return "**${subject.groupValues[1]}**" + endings(sentence.substring(subject.groups[1]!!.range.last + 1))
    }

    /** "I work = delam, you work = delaš." as its pairs; null unless the whole sentence is two pairs or more. */
    internal fun pairs(sentence: String): List<Pair<String, String>>? {
        val parts = sentence.trim().trimEnd('.', ';').split(Regex("""\s*[,;]\s+"""))
        if (parts.size < 2) return null
        return parts.map { p -> PAIR.matchEntire(p)?.let { it.groupValues[1].trim() to it.groupValues[2].trim() } ?: return null }
    }

    /** [s]'s endings in bold; never inside a link ("https://fran.si/-ka"), which the bold would cut. */
    private fun endings(s: String): String {
        val links = si.lanisce.lani.data.Links.find(s).map { it.range }
        return ENDING.replace(s) { m -> if (links.any { m.range.first in it }) m.value else "**${m.value}**" }
    }
}
