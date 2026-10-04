package si.lanisce.lani.road

import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.game.scene.Notebook
import si.lanisce.lani.game.scene.StoryBooks
import si.lanisce.lani.game.scene.StoryTelling

/**
 * A story's evening on the road (companion/README.md, "Im Auto · In the car"): a short setup in the learner's base
 * language, then the teller tells a whole paragraph in Slovene only (his lines between two of the learner's turns,
 * [StoryBooks.paragraphs]) and a short recap in the base language follows: at A1 after every paragraph, at A2 after
 * every second, from B1 only at the end. Pure.
 */
object RoadStory {
    /** About how long the recap of one paragraph is, in characters (about ten seconds spoken); one sentence at least. */
    const val RECAP_CHARS = 140

    /** A sentence of what a paragraph tells: [lead] the first of its note or line (kept first when it is shortened). */
    data class Said(val text: String, val lead: Boolean)

    /** What is said in the base language: [setup] before the telling, [recaps] after the paragraph of each index. */
    data class Evening(val setup: String?, val recaps: Map<Int, String>)

    /**
     * The evening of a telling ([lines]) at [level]: the setup is its [teaser] ("Janez tells the story of Zlatorog"), else
     * what its first paragraph tells; the recaps as [schedule] has them, each paragraph of one shortened to its share of
     * [RECAP_CHARS] ([shorten]). [notes]: the story's written-down paragraphs for this evening, if it has them ([gists]).
     * [lang]: the story's language, [base] the learner's (the lines' and notes' `en`).
     */
    fun evening(lines: List<DialogLine>, level: String, teaser: String?, notes: List<DialogReply>, lang: String, base: String): Evening {
        val gists = gists(lines, notes, lang, base)
        val setup = teaser?.trim()?.takeIf { it.isNotEmpty() } ?: gists.firstOrNull { it.isNotEmpty() }?.let { shorten(it, RECAP_CHARS) }
        val recaps = LinkedHashMap<Int, String>()
        for ((after, covered) in schedule(gists.map { it.isNotEmpty() }, level)) {
            val budget = RECAP_CHARS / covered.size
            val text = covered.joinToString(" ") { shorten(gists[it], budget) }
            // a setup that was the first paragraph's meaning isn't said twice
            if (text != setup) recaps[after] = text
        }
        return Evening(setup, recaps)
    }

    /**
     * After which paragraphs a recap comes, each with the paragraphs it tells of: of those with something to tell ([has]),
     * at A1 every one, at A2 every second, from B1 none; and after the last always, with what wasn't told yet.
     */
    fun schedule(has: List<Boolean>, level: String): Map<Int, List<Int>> {
        val told = has.indices.filter { has[it] }
        val every = when (level.uppercase()) {
            "A1" -> 1
            "A2" -> 2
            else -> 0
        }
        val out = LinkedHashMap<Int, List<Int>>()
        var from = 0
        told.forEachIndexed { n, p ->
            if ((every > 0 && (n + 1) % every == 0) || n == told.lastIndex) {
                out[p] = told.subList(from, n + 1)
                from = n + 1
            }
        }
        return out
    }

    /**
     * What each paragraph of [lines] tells, in the base language, sentence by sentence. From the story's [notes] (the
     * notebook's paragraphs, written down past tense without the teller's asides) when it has them: each note goes with the
     * paragraph it shares the most words with ([align]), in their order. A paragraph no note goes with, or a story without
     * notes: its lines' meanings without the teller's asides to the listener ([Notebook.kept]). A paragraph of asides only
     * (a greeting, "sit by the fire") tells nothing.
     */
    fun gists(lines: List<DialogLine>, notes: List<DialogReply>, lang: String, base: String): List<List<Said>> {
        val runs = StoryBooks.paragraphs(StoryTelling(lines))
        val own = runs.map { run ->
            run.flatMap { l -> Notebook.kept(l.sl.orEmpty(), l.en.orEmpty(), lang, base).second.mapIndexed { i, s -> Said(s, i == 0) } }
        }
        val written = notes.filter { it.en.isNotBlank() }
        val told = own.indices.filter { own[it].isNotEmpty() }
        if (written.isEmpty() || told.isEmpty()) return own
        val at = align(told.map { i -> runs[i].joinToString(" ") { it.sl.orEmpty() } }, written.map { it.sl })
        val fromNotes = own.map { mutableListOf<Said>() }
        written.forEachIndexed { j, n -> fromNotes[told[at[j]]] += StoryBooks.sentences(n.en).mapIndexed { i, s -> Said(s, i == 0) } }
        return own.indices.map { i -> fromNotes[i].ifEmpty { own[i] } }
    }

    /**
     * For each of [notes], the index of the paragraph of [paragraphs] it tells of: in their order (a note never before the
     * one ahead of it), the most words shared in all ([stems]; the earliest paragraph where it's a tie).
     */
    fun align(paragraphs: List<String>, notes: List<String>): IntArray {
        val at = IntArray(notes.size)
        if (notes.isEmpty() || paragraphs.isEmpty()) return at
        val p = paragraphs.map(::stems)
        val n = notes.map(::stems)
        val k = p.size
        // best[j][i]: the most words notes 0..j share with their paragraphs, note j with paragraph i; from: note j-1's
        val best = Array(n.size) { IntArray(k) }
        val from = Array(n.size) { IntArray(k) }
        for (j in n.indices) {
            var m = 0
            for (i in 0 until k) {
                val shared = p[i].count { it in n[j] }
                if (j == 0) {
                    best[j][i] = shared
                } else {
                    if (best[j - 1][i] > best[j - 1][m]) m = i
                    best[j][i] = shared + best[j - 1][m]
                    from[j][i] = m
                }
            }
        }
        var i = best.last().indices.maxByOrNull { best.last()[it] } ?: 0
        for (j in n.indices.reversed()) {
            at[j] = i
            i = from[j][i]
        }
        return at
    }

    /** The words of [text] that say something (four letters or more), by their first four letters: "Zlatoroga" → "zlat". */
    fun stems(text: String): Set<String> =
        text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 4 }.map { it.take(4) }.toSet()

    /**
     * [said] in about [budget] characters, in its order: the leads (the first sentence of each note or line) as far as
     * they fit, the first always, then any other sentence of a lead kept that still fits.
     */
    fun shorten(said: List<Said>, budget: Int): String {
        val keep = BooleanArray(said.size)
        var used = 0
        fun take(i: Int) {
            keep[i] = true
            used += said[i].text.length + 1
        }
        for (i in said.indices) {
            if (!said[i].lead) continue
            if (used > 0 && used + said[i].text.length > budget) break
            take(i)
        }
        var lead = -1
        for (i in said.indices) {
            if (said[i].lead) lead = i
            else if ((lead < 0 || keep[lead]) && used + said[i].text.length <= budget) take(i)
        }
        if (keep.none { it } && said.isNotEmpty()) keep[0] = true
        return said.indices.filter { keep[it] }.joinToString(" ") { said[it].text }
    }
}
