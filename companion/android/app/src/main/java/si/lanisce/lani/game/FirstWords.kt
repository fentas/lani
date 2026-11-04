package si.lanisce.lani.game

import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * The first thing after the founding (ui/game/FirstMoment; docs/plans/05-built/first-minutes.md): the place said aloud, a
 * neighbour's greeting said aloud with what it means, and the learner greets back with one tap: their first word in the
 * village. The greeting and the wrong choice are each language's own ("firstRun.greeting", "firstRun.other"); the place
 * is the culture's ([GameIntroSpeech.arrive]). Nothing depends on a tutor, a node or a clip: the phone's voice reads it where
 * no clip is. Pure.
 */
object FirstWords {
    /** One way to answer: [right] is the greeting said back. */
    data class Choice(val text: String, val right: Boolean)

    /** The greeting in [l]: said in the language learned, its meaning in the base. */
    fun greeting(l: Lang): String = L10n.text(l, "firstRun.greeting", target = l)

    /** The two answers: the greeting back and another, the right one not always first (it's the same for a language). */
    fun choices(pair: LangPair): List<Choice> {
        val right = Choice(greeting(pair.target), true)
        val other = Choice(L10n.text(pair.target, "firstRun.other", target = pair.target), false)
        return if (pair.target.ordinal % 2 == 0) listOf(other, right) else listOf(right, other)
    }

    /** What's read as the moment opens: the place in both languages, who speaks, the greeting, what to do. */
    fun opening(pair: LangPair, arrive: (Lang) -> String = { GameIntroSpeech.arrive(it, pair) }): List<HintPiece> = kept(
        HintPiece(arrive(pair.target), true, pair.target),
        HintPiece(arrive(pair.base), false, pair.base),
        HintPiece(L10n.text(pair.base, "firstRun.who", target = pair.target), false, pair.base),
        HintPiece(greeting(pair.target), true, pair.target),
        HintPiece(L10n.text(pair.base, "firstRun.sayBack", target = pair.target), false, pair.base),
    )

    /** The greeting again, after a wrong tap: listen, and try once more. */
    fun again(pair: LangPair): List<HintPiece> = kept(
        HintPiece(L10n.text(pair.base, "firstRun.again", target = pair.target), false, pair.base),
        HintPiece(greeting(pair.target), true, pair.target),
    )

    /** The right answer: the word said back, then the win in both languages. */
    fun won(pair: LangPair): List<HintPiece> = kept(
        HintPiece(greeting(pair.target), true, pair.target),
        HintPiece(L10n.text(pair.target, "firstRun.won", target = pair.target), true, pair.target),
        HintPiece(L10n.text(pair.base, "firstRun.won", target = pair.target), false, pair.base),
    )

    /** What the node voices ahead as the moment opens. */
    fun learned(pair: LangPair): List<String> = (opening(pair) + won(pair)).filter { it.target }.map { it.text }.distinct()

    private fun kept(vararg p: HintPiece): List<HintPiece> = p.map { it.copy(text = ReadAloud.spoken(it.text)) }.filter { it.text.isNotBlank() }
}
