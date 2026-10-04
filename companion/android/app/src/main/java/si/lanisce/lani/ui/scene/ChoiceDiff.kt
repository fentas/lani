package si.lanisce.lani.ui.scene

import si.lanisce.lani.ui.words.WordToken
import si.lanisce.lani.ui.words.WordTokens

/**
 * The words that set a turn's choices apart, shown bold: in each choice, the words it doesn't share with every other
 * choice, word by word in order (case and punctuation aside). "Deset **jajc**, prosim." beside "Deset **jajce**,
 * prosim." Every choice gets its own, so the bold shows where they differ, never which one is right.
 *
 * Choices that say different things ("Adijo!", "Lahko noč!") get none: when a choice would have more than [MAX_WORDS]
 * words bold, or most of its words, the whole choice is the difference.
 */
object ChoiceDiff {
    const val MAX_WORDS = 3

    fun marks(texts: List<String>): List<List<WordToken>> {
        val none = texts.map { emptyList<WordToken>() }
        if (texts.size < 2) return none
        val tokens = texts.map { WordTokens.of(it) }
        val words = tokens.map { ts -> ts.map { it.text.lowercase() } }
        val marks = tokens.mapIndexed { i, ts ->
            val shared = BooleanArray(ts.size) { true }
            for (j in texts.indices) {
                if (j == i) continue
                val kept = kept(words[i], words[j])
                for (k in ts.indices) if (!kept[k]) shared[k] = false
            }
            ts.filterIndexed { k, _ -> !shared[k] }
        }
        val apart = marks.indices.any { i -> marks[i].size > MAX_WORDS || marks[i].size * 2 > tokens[i].size }
        return if (apart) none else marks
    }

    /** Which of [a]'s words a longest common subsequence with [b] keeps. */
    private fun kept(a: List<String>, b: List<String>): BooleanArray {
        val n = a.size
        val m = b.size
        val common = Array(n + 1) { IntArray(m + 1) }
        for (x in n - 1 downTo 0) {
            for (y in m - 1 downTo 0) {
                common[x][y] = if (a[x] == b[y]) common[x + 1][y + 1] + 1 else maxOf(common[x + 1][y], common[x][y + 1])
            }
        }
        val keep = BooleanArray(n)
        var x = 0
        var y = 0
        while (x < n && y < m) {
            when {
                a[x] == b[y] -> { keep[x] = true; x++; y++ }
                common[x + 1][y] > common[x][y + 1] -> x++
                common[x + 1][y] < common[x][y + 1] -> y++
                // either way as long: the shorter word is the one that moved ("se", not "vidimo"), in every choice alike
                a[x].length <= b[y].length -> x++
                else -> y++
            }
        }
        return keep
    }
}
