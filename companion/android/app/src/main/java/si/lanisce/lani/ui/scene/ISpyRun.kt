package si.lanisce.lani.ui.scene

import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.scene.ISpy
import si.lanisce.lani.game.scene.ISpyLine
import si.lanisce.lani.game.scene.ISpyLines
import si.lanisce.lani.game.scene.ISpyRound

/**
 * A round of «Vidim, vidim» played: the thing spied ([slot]); [found]: tapped (else shown at the end); [clues]: how many
 * clues the learner had heard when it was found or shown; [wrong]: the wrong taps on the way.
 */
data class ISpyResult(val slot: String, val found: Boolean, val clues: Int, val wrong: Int) {
    /** Found within [ISpy.QUICK] clues: a right answer, and a review of its word's card. */
    val quick: Boolean get() = found && clues <= ISpy.QUICK

    /** What the round pays as an answer: right when quick, almost when found later, wrong (still something) when shown. */
    val verdict: Verdict get() = when {
        quick -> Verdict.CORRECT
        found -> Verdict.ALMOST
        else -> Verdict.WRONG
    }
}

/**
 * A game of «Vidim, vidim» being played (companion/SCENES.md, "I spy"). Pure: the scene screen shows it, a tap in the
 * picture answers it.
 *
 * Each round the child ([child], a person of the scene) says the game's line and its first clue, and the learner taps a
 * thing in the picture ([Step.FIND]). The right thing: the learner's guess is said ("Nož?") and the child cheers
 * ("Ja, to je nož! Bravo!"), the round is solved ([Step.SOLVED]). A wrong one: the guess is said marked wrong, the child
 * reacts kindly (warm near it, cold far from it) and gives the next clue; with none left, the child shows the thing ("To
 * je nož! Glej, tukaj je."). "Še en namig" ([more]) asks the next clue without a tap, "Pokaži mi" ([reveal]) gives up.
 * Then [next]: the next round, or the end ([Step.END]) with the child's goodbye.
 *
 * [names]: every thing of the scene by slot, its word and what it means: what a guess says.
 */
data class ISpyRun(
    val child: String,
    val rounds: List<ISpyRound>,
    val lines: ISpyLines,
    val names: Map<String, Pair<String, String>>,
    val round: Int = 0,
    /** Clues said in this round. */
    val heard: Int = 0,
    val said: List<Said> = emptyList(),
    val step: Step = Step.FIND,
    val results: List<ISpyResult> = emptyList(),
    /** Wrong taps this round, and their things (a second tap on one gets no new clue). */
    val tried: List<String> = emptyList(),
    /** The reactions said so far, so they take turns. */
    val reactions: Int = 0,
) {
    enum class Step { FIND, SOLVED, END }

    val current: ISpyRound? get() = rounds.getOrNull(round)

    /** The thing of the round shown or found: the picture outlines it and its word opens over it. */
    val solved: String? get() = current?.slot?.takeIf { step == Step.SOLVED }

    /** Whether a clue is left to give this round. */
    val moreClues: Boolean get() = step == Step.FIND && heard < (current?.clues?.size ?: 0)

    /** Every round found. */
    val allFound: Boolean get() = results.size == rounds.size && results.all { it.found }

    private fun his(l: ISpyLine) = Said(child, l.sl, l.en)

    /** The thing [slot] tapped in the picture; [near]: close to the thing spied (warm). Nothing outside a round's find. */
    fun tap(slot: String, near: Boolean = false): ISpyRun {
        val r = current ?: return this
        if (step != Step.FIND) return this
        val (word, meaning) = names[slot] ?: return this
        val guess = Said(null, "${word.replaceFirstChar { it.uppercaseChar() }}?", if (meaning.isBlank()) "" else "$meaning?", wrong = slot != r.slot)
        if (slot == r.slot) {
            val found = (if (r.plural) lines.foundPlural else lines.found).with(r.word, r.meaning)
            return copy(said = said + guess + his(found), step = Step.SOLVED, results = results + ISpyResult(r.slot, true, heard, tried.size))
        }
        val again = slot in tried
        val pool = if (near) lines.warm else lines.cold
        val reacted = copy(said = said + guess + his(pool[reactions % pool.size]), reactions = reactions + 1, tried = if (again) tried else tried + slot)
        return if (again) reacted else reacted.clue()
    }

    /** "💡 Še en namig · Another clue": the next clue without a tap; the thing shown when none is left. */
    fun more(): ISpyRun = if (step == Step.FIND) clue() else this

    /** "🙈 Pokaži mi · Show me": the child shows the thing. */
    fun reveal(): ISpyRun = if (step == Step.FIND) show() else this

    /** The round's next clue, or the thing shown after the last one. */
    private fun clue(): ISpyRun {
        val r = current ?: return this
        val c = r.clues.getOrNull(heard) ?: return show()
        return copy(said = said + his(c.line), heard = heard + 1)
    }

    private fun show(): ISpyRun {
        val r = current ?: return this
        val shown = (if (r.plural) lines.showPlural else lines.show).with(r.word, r.meaning)
        return copy(said = said + his(shown), step = Step.SOLVED, results = results + ISpyResult(r.slot, false, heard, tried.size))
    }

    /** After a round: the next one (the child's line and its first clue), or the end and the child's goodbye. */
    fun next(): ISpyRun {
        if (step != Step.SOLVED) return this
        if (round + 1 >= rounds.size) return copy(step = Step.END, said = said + his(if (allFound) lines.endAll else lines.end))
        return copy(round = round + 1, heard = 0, step = Step.FIND, tried = emptyList(), said = said + his(lines.again)).clue()
    }

    companion object {
        /** A game of [rounds] with [child] (a person id), the child's [lines] in the scene's language and the scene's things' [names]. */
        fun start(child: String, rounds: List<ISpyRound>, lines: ISpyLines, names: Map<String, Pair<String, String>>): ISpyRun =
            ISpyRun(child, rounds, lines, names, said = listOf(Said(child, lines.start.sl, lines.start.en))).clue()
    }
}
