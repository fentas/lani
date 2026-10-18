package si.lanisce.lani.data

import java.text.Normalizer
import java.time.LocalDate

/**
 * A word of the learner's ("📚 Moje besede · My words"; companion/README.md "My words"): its review card's [id], the word
 * as its card has it ([word]: its front, "hiša", "dober dan"), what it means ([meaning]: its back), [learned] (answered
 * right and not rusty since: what Napredek's "words known" counts) or still being learned, the day it was [added], its
 * pack ([category]).
 */
data class MyWord(
    val id: String,
    val word: String,
    val meaning: String,
    val learned: Boolean,
    val added: LocalDate? = null,
    val category: String? = null,
)

/** A part of the word list: the newest words ([MyWords.RECENT]), or one letter's ("Č"). */
data class MyWordsGroup(val key: String, val words: List<MyWord>) {
    val recent: Boolean get() = key == MyWords.RECENT
}

/**
 * The learner's word list ("📚 Moje besede · My words", opened from Napredek's words and the packs' screen): their
 * vocabulary cards, one per word, the learned ones and those still being learned (Napredek's "x / 300" counts the learned
 * ones, [Stats.learned]); the newest few on top ([groups]: added in the last [RECENT_DAYS] days), then all of them by
 * letter in Slovene's order (c, č, d …; s, š; z, ž: each its own letter); a search that filters them as it is typed
 * ([filter]: č, š, ž or not, the word or its meaning) and names the word to look up in the dictionary when it isn't one
 * of theirs ([lookupFor]). Pure.
 */
object MyWords {
    /** The key of the newest words' group. */
    const val RECENT = "recent"
    /** The newest words shown on top at most ... */
    const val RECENT_MAX = 8
    /** ... added within this many days. */
    const val RECENT_DAYS = 14L
    /** A search is looked up in the dictionary from this many letters, up to [LOOKUP_MAX] (GET /lookup's). */
    const val LOOKUP_MIN = 2
    const val LOOKUP_MAX = 40

    /**
     * The learner's words from their review cards ([pool]: vocabulary only, not a counting or a forms card) and the ids
     * of those [learned]: one per word (the learned card of it first, else the newest), in Slovene's alphabetical order.
     */
    fun of(pool: List<ReviewCard>, learned: Set<String>): List<MyWord> =
        pool.asSequence()
            .filter { it.kind == "vocabulary" && it.front.isNotBlank() }
            .map { MyWord(it.id, it.front.trim(), it.back.trim(), it.id in learned, it.created, it.category) }
            .sortedWith(compareBy<MyWord> { !it.learned }.thenByDescending { it.added })
            .distinctBy { fold(it.word) }
            .sortedWith(ORDER)
            .toList()

    /** Slovene's alphabetical order: c before č, s before š, z before ž, each its own letter; an accent elsewhere after its letter. */
    val ORDER: Comparator<MyWord> = compareBy<MyWord> { sortKey(it.word) }.thenBy { it.word }

    /** [w] as [ORDER] compares it: each letter its base, then č, š, ž as letters of their own after c, s, z. */
    fun sortKey(w: String): String = buildString {
        for (ch in w.lowercase()) {
            when (ch) {
                'č' -> append("c\u0002")
                'š' -> append("s\u0002")
                'ž' -> append("z\u0002")
                else -> {
                    val f = fold(ch.toString()).firstOrNull() ?: ch
                    append(f)
                    append(if (f == ch) '\u0001' else '\u0003')
                }
            }
        }
    }

    /** The letter [w] is listed under: its first letter, č, š and ž their own, another accent its base ("ć" under C); "#" for a digit or a sign. */
    fun letter(w: String): String {
        val c = w.trim().lowercase().firstOrNull() ?: return "#"
        if (!c.isLetter()) return "#"
        val l = if (c in "čšž") c.toString() else fold(c.toString()).ifEmpty { c.toString() }
        return l.uppercase()
    }

    /**
     * The list without a search: the newest words on top (added within [RECENT_DAYS] of [today], the newest first, at most
     * [RECENT_MAX]; only when the list is longer than that, or it would be the list twice), then every word by its letter,
     * in Slovene's order ("#" last).
     */
    fun groups(words: List<MyWord>, today: LocalDate): List<MyWordsGroup> {
        val recent = if (words.size <= RECENT_MAX) emptyList() else words
            .filter { w -> w.added?.let { !it.isAfter(today) && !it.isBefore(today.minusDays(RECENT_DAYS)) } == true }
            .sortedWith(compareByDescending<MyWord> { it.added }.thenBy { sortKey(it.word) })
            .take(RECENT_MAX)
        val letters = words.sortedWith(ORDER).groupBy { letter(it.word) }.toList()
            .sortedWith(compareBy<Pair<String, List<MyWord>>> { it.first == "#" }.thenBy { sortKey(it.first) })
            .map { (k, v) -> MyWordsGroup(k, v) }
        return listOfNotNull(recent.takeIf { it.isNotEmpty() }?.let { MyWordsGroup(RECENT, it) }) + letters
    }

    /**
     * [words] matching [query] as it is typed, č, š, ž typed or not ("zlica" finds žlica), the best first: the word
     * begins with it, then a word of it does ("dan" finds "dober dan"); from two letters on also where it is within the
     * word, then its meaning ("house" finds hiša), a meaning's word beginning with it first. Alphabetical within each. All
     * of them for an empty query.
     */
    fun filter(words: List<MyWord>, query: String): List<MyWord> {
        val q = fold(query.trim().replace(Regex("\\s+"), " "))
        if (q.isEmpty()) return words
        val parts = Regex("[^\\p{L}\\p{N}']+")
        fun rank(w: MyWord): Int? {
            val f = fold(w.word)
            val m = fold(w.meaning)
            return when {
                f.startsWith(q) -> 0
                f.split(parts).any { it.startsWith(q) } -> 1
                q.length < 2 -> null
                q in f -> 2
                m.split(parts).any { it.startsWith(q) } -> 3
                q in m -> 4
                else -> null
            }
        }
        return words.mapNotNull { w -> rank(w)?.let { it to w } }
            .sortedWith(compareBy<Pair<Int, MyWord>> { it.first }.thenBy { sortKey(it.second.word) })
            .map { it.second }
    }

    /**
     * The word to look [query] up as in the dictionary (GET /lookup), or null: a word of letters ([LOOKUP_MIN] to
     * [LOOKUP_MAX]; a space, an apostrophe or a hyphen in it too) that isn't one of the learner's [words] as it is (their
     * card shows it in the list already). A form of one of theirs ("hiše") is looked up: the dictionary names its word.
     */
    fun lookupFor(query: String, words: List<MyWord>): String? {
        val q = query.trim().replace(Regex("\\s+"), " ")
        if (q.length < LOOKUP_MIN || q.length > LOOKUP_MAX) return null
        if (!q.all { it.isLetter() || it == ' ' || it == '\'' || it == '’' || it == '-' }) return null
        val f = fold(q)
        if (words.any { fold(it.word) == f }) return null
        return q
    }

    /** [s] to compare as typed: lower case, without accents (č is c, é is e), one apostrophe. */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase().replace('’', '\''), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
