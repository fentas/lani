package si.lanisce.lani.game

import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.tapTurn

/**
 * One of the learner's word cards as a dialog meets it (companion/SCENES.md, "Your words in the dialogs"): the card's
 * [id], its [word] (the card's front, a lemma: "žlica", "sesti"), what it [means] (the card's back) and the [forms] the
 * word takes, lower case (the bridge's table of it, GET /forms; the word itself when there is none).
 */
data class MyWord(val id: String, val word: String, val means: String, val forms: Set<String>)

/** The learner's words by their forms: what a dialog's turns are checked against. */
class MyWords(words: List<MyWord>) {
    private val byForm: Map<String, List<MyWord>> = HashMap<String, MutableList<MyWord>>().also { m ->
        for (w in words) for (f in w.forms + w.word.lowercase()) m.getOrPut(f) { mutableListOf() }.let { l -> if (w !in l) l += w }
    }

    val isEmpty: Boolean get() = byForm.isEmpty()

    /**
     * The learner's word [form] is a form of ("žlico" → žlica): the one whose word it is written as first ("sedi": the
     * card sedeti, not sesti, when the learner has both and neither is written so), else the first; null when none is.
     */
    fun of(form: String): MyWord? {
        val f = form.lowercase()
        val all = byForm[f] ?: return null
        return all.firstOrNull { it.word.lowercase() == f } ?: all.first()
    }

    /** Whether [form] is a form of [word] (another case, person or tense of it: what a form turn's wrong choice is). */
    fun isFormOf(word: MyWord, form: String): Boolean = byForm[form.lowercase()]?.contains(word) == true

    companion object {
        val NONE = MyWords(emptyList())
    }
}

/**
 * How a turn tests one of the learner's words: [MEANING], a wrong choice says another word in its place ("Daj mi
 * žlico." / "Daj mi vilico."); [FORM], a wrong choice says another form of it ("Deset jajc." / "Deset jajce.": the grammar
 * book's rule counts it, the word is known); [THING], a tap turn's right place is the thing the word names.
 */
enum class WordTest { MEANING, FORM, THING }

/** A word of the learner's a turn tests: their [card], the word as the right choice says it ([said]), and where ([slot]: -1 for a thing tapped). */
data class TurnWord(val card: MyWord, val said: String, val slot: Int)

/**
 * A turn's first answer on one of the learner's words it tests ([DialogWords]): [right] or not, [how] the turn tested it
 * (a wrong one: how the learner's answer got it wrong), [produced]: typed or said (not chosen or tapped), at the turn of
 * line [turn], what the learner [said] and the right choice they were [expected] to give.
 */
data class WordAnswer(val word: TurnWord, val right: Boolean, val how: WordTest, val produced: Boolean, val turn: Int, val said: String, val expected: String) {
    val card: MyWord get() = word.card
}

/**
 * The learner's words a turn tests ([DialogWords.turn]): for each right choice (its index in the file), the words its
 * wrong choices test and how each wrong choice tests them ([by]: the wrong choice's index → [WordTest]); a tap turn, the
 * thing its right choice stands for ([thing]).
 */
data class TurnWords(val byRight: Map<Int, List<Tested>> = emptyMap(), val thing: TurnWord? = null) {
    data class Tested(val word: TurnWord, val by: Map<Int, WordTest>)

    val isEmpty: Boolean get() = byRight.values.all { it.isEmpty() } && thing == null

    /** The words picking right choice [right] answers right: those its wrong choices test. */
    fun answered(right: Int): List<TurnWord> = byRight[right].orEmpty().map { it.word }

    /**
     * The words wrong choice [wrong] got wrong, and how: those it tests against a right choice of the turn, the card once.
     * A wrong choice tests words only of a right choice it nearly copies ([DialogWords.tested]: the rest shared), so with
     * two right choices ("Kaj je to? Toliko čebel!", "Ojoj, čebele! Ali pičijo?") it is the one it was an attempt at ("Kdo
     * je to? Toliko čebel!": kaj).
     */
    fun missed(wrong: Int): List<Pair<TurnWord, WordTest>> =
        byRight.values.flatMap { l -> l.mapNotNull { t -> t.by[wrong]?.let { t.word to it } } }.distinctBy { it.first.card.id }

    /** The word in the gap of a form turn (its [slot] in the right choice [right]): what typing or saying the turn tests. */
    fun inGap(right: Int, slot: Int): TurnWord? = byRight[right].orEmpty().firstOrNull { it.word.slot == slot }?.word
}

/**
 * Which of the learner's words a dialog's turns test (companion/SCENES.md, "Your words in the dialogs"). Pure.
 *
 * A turn counts for a word only when it tests that word: a word that is just there, in someone's line or in a choice
 * where the choices differ in something else, counts for nothing. Picking the right one of three sentences that differ in
 * a greeting shows nothing about the other words in them. A turn tests a word of its right choice when a wrong choice
 * differs from it there: another word in its place (its meaning), or another form of it (its form, which the grammar book
 * counts too); a tap turn tests the thing its right place is. [tested] is the rule, the bridge's `publish_dialog_variant`
 * checks a variant's declared words with the same (bridge/src/dialog-words.ts).
 */
object DialogWords {
    /** At most this many words of the right choice may differ from a wrong one for it to test them: the rest is shared. */
    const val MAX_DIFFERING = 2

    /**
     * Where wrong choice [wrong] differs from right choice [right], when it tests words of it: the positions of [right]'s
     * words ([Forms.words]) it tests, each with the position of the word [wrong] says in its place (null: none in its place,
     * left out). Aligned word by word (a longest common subsequence, case and punctuation aside), the words of [right] off
     * it are where they differ; it tests them when they are one or two and at least as many words are shared ("Daj mi eno
     * žlico." / "Daj mi dve vilici." tests eno and žlico; "Ja, zelo." / "Dobro jutro!" nothing), a one-word answer always
     * ("Žlica." / "Vilica.").
     */
    fun tested(right: String, wrong: String): Map<Int, Int?> {
        val r = Forms.words(right)
        val w = Forms.words(wrong)
        if (r.isEmpty()) return emptyMap()
        val pairs = lcs(r, w)
        val matched = pairs.map { it.first }.toSet()
        val off = r.indices.filter { it !in matched }
        if (off.isEmpty() || off.size > MAX_DIFFERING) return emptyMap()
        if (r.size > 1 && r.size - off.size < off.size) return emptyMap()
        // between the same two anchors, as many words of the wrong choice as of the right: said in their place, in order
        val anchors = listOf(-1 to -1) + pairs + listOf(r.size to w.size)
        val instead = HashMap<Int, Int?>()
        for ((a, b) in anchors.zipWithNext()) {
            val mine = (a.first + 1 until b.first).toList()
            if (mine.isEmpty()) continue
            val theirs = (a.second + 1 until b.second).toList()
            mine.forEachIndexed { k, i -> instead[i] = if (theirs.size == mine.size) theirs[k] else null }
        }
        return off.associateWith { instead[it] }
    }

    /** The index pairs (in [a], in [b]) of a longest common subsequence of [a] and [b], in order. */
    private fun lcs(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val n = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            n[i][j] = if (a[i] == b[j]) n[i + 1][j + 1] + 1 else maxOf(n[i + 1][j], n[i][j + 1])
        }
        val out = mutableListOf<Pair<Int, Int>>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) when {
            a[i] == b[j] -> { out += i to j; i++; j++ }
            n[i + 1][j] >= n[i][j + 1] -> i++
            else -> j++
        }
        return out
    }

    /**
     * How a wrong choice saying [instead] in the place of the learner's [word] tests it: its form when [instead] is another
     * form of the same word ([MyWords.isFormOf]); its meaning when it is one of the learner's other words, or none is in
     * its place; else its form when it looks like one (the same stem, another ending of three letters at most: a wrong
     * form the dictionary doesn't list) or the wrong choice names a rule of the grammar book ([named]: the content says
     * it is about a form), its meaning otherwise.
     */
    fun how(word: MyWord, instead: String?, words: MyWords, named: Boolean): WordTest {
        if (instead == null) return WordTest.MEANING
        if (words.isFormOf(word, instead)) return WordTest.FORM
        if (words.of(instead) != null) return WordTest.MEANING // another of the learner's words
        return if (named || sameStem(word.word, instead)) WordTest.FORM else WordTest.MEANING
    }

    /**
     * How a word [typed] into the gap of the learner's [word] got it wrong (none of the turn's choices): its form when it is
     * another form of it or looks like one ([how]), its meaning otherwise.
     */
    fun typed(word: MyWord, typed: String): WordTest {
        val t = Forms.words(typed).singleOrNull() ?: return WordTest.MEANING
        return if (t in word.forms || sameStem(word.word, t)) WordTest.FORM else WordTest.MEANING
    }

    /** Another ending on the same stem: the shorter's letters but its last three at most are the other's start. */
    private fun sameStem(a: String, b: String): Boolean {
        val x = a.lowercase()
        val y = b.lowercase()
        val common = x.commonPrefixWith(y).length
        return common >= 3 && minOf(x.length, y.length) - common <= 3
    }

    /**
     * The learner's [words] the turn [line] tests (its choices as the learner meets them: trimmed, with their own traps),
     * and in a tap turn the thing its right place is when it is one of theirs ([things]: an object slot of the scene →
     * the learner's word for it). Null when it tests none of theirs.
     */
    fun turn(line: DialogLine, words: MyWords, things: Map<String, MyWord> = emptyMap()): TurnWords? {
        val choices = line.choices
        if (choices.size < 2 || words.isEmpty) return null
        if (line.tapTurn) {
            val right = choices.firstOrNull { it.ok }?.tap ?: return null
            return things[right]?.let { TurnWords(thing = TurnWord(it, it.word, -1)) }
        }
        val rights = choices.indices.filter { choices[it].ok }
        val wrongs = choices.indices.filter { !choices[it].ok }
        if (rights.isEmpty() || wrongs.isEmpty()) return null
        val byRight = rights.associateWith { r ->
            val said = Forms.spans(choices[r].sl).map { it.value }
            val at = LinkedHashMap<Int, MutableMap<Int, WordTest>>()
            for (w in wrongs) {
                val named = choices[w].grammar != null || line.grammar != null
                for ((i, j) in tested(choices[r].sl, choices[w].sl)) {
                    val card = words.of(said[i]) ?: continue
                    val instead = j?.let { Forms.spans(choices[w].sl).getOrNull(it)?.value }
                    at.getOrPut(i) { LinkedHashMap() }[w] = how(card, instead, words, named)
                }
            }
            at.map { (i, by) -> TurnWords.Tested(TurnWord(words.of(said[i])!!, said[i], i), by) }.distinctBy { it.word.card.id }
        }
        return TurnWords(byRight.filterValues { it.isNotEmpty() }).takeIf { !it.isEmpty }
    }

    /** The turns of [dialog] that test the learner's [words], by their line index ([turn]). */
    fun of(dialog: Dialog, words: MyWords, things: Map<String, MyWord> = emptyMap()): Map<Int, TurnWords> {
        if (words.isEmpty) return emptyMap()
        return dialog.lines.withIndex().mapNotNull { (i, l) -> turn(l, words, things)?.let { i to it } }.toMap()
    }

    /** The learner's words [dialog] tests, each once: what "📇 3 tvoje besede" in the scroll counts. */
    fun cards(tested: Map<Int, TurnWords>): List<MyWord> =
        tested.values.flatMap { t -> t.byRight.values.flatten().map { it.word.card } + listOfNotNull(t.thing?.card) }.distinctBy { it.id }
}
