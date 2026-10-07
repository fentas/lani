package si.lanisce.lani.ui.scene

import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.SpeechGrading
import si.lanisce.lani.game.DialogWords
import si.lanisce.lani.game.FormGap
import si.lanisce.lani.game.Forms
import si.lanisce.lani.game.TurnMode
import si.lanisce.lani.game.TurnWord
import si.lanisce.lani.game.TurnWords
import si.lanisce.lani.game.WordAnswer
import si.lanisce.lani.game.WordTest
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.game.scene.Placement
import si.lanisce.lani.game.scene.Sky
import si.lanisce.lani.game.scene.actAt
import si.lanisce.lani.game.scene.actOver
import si.lanisce.lani.game.scene.fxAt
import si.lanisce.lani.game.scene.skyAt
import si.lanisce.lani.game.scene.tapTurn
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget

/**
 * One line said in a scene dialog: by a person ([who] = their id) or by the learner ([who] null). [wrong]: the
 * learner's wrong choice, said when the person reacts to it (see [DialogRun.choose]).
 */
data class Said(val who: String?, val sl: String, val en: String, val wrong: Boolean = false)

/**
 * A scene dialog being played (companion/SCENES.md, `dialogs[]`). Pure: the scene screen shows it.
 *
 * The person's lines come one at a time ([Step.LISTEN]: the learner taps on). At the learner's turn
 * ([Step.CHOOSE]) they pick one of the choices: a wrong one counts as a mistake and shows [why], and they
 * try again; a right one is said, the person's reply (if it has one) follows, and the dialog goes on.
 * A wrong choice with a reply is said too (marked [Said.wrong]), and the person reacts to it, taken at face value
 * ("Žejen? Tu imaš vodo."): its [reaction]. A turn may have more than one right choice, each with its own reply.
 * A line followed by the learner's turn shows the choices straight away. [Step.END] after the last line.
 *
 * A turn that tests a form may be typed or said instead of chosen ([modes], companion/SCENES.md "Adaptive turns"). Every
 * answer on a rule of the grammar book, picked, typed or said, is in [answers] once ([RuleAnswer]): the dialog's screen
 * counts the new ones on their pages ([answersOf]).
 *
 * @param partner who answers with a choice's reply: the person of the happening
 * @param at the next line to play
 * @param said everything said so far, in order
 * @param tried the wrong choices of the current turn already picked (as shown, see [choices])
 * @param mistakes wrong picks over the whole dialog (each wrong choice counts once)
 * @param picks the right choice taken at each turn passed, in order (its index in the dialog's file: the cues follow it)
 * @param seed the order the choices of each turn are shown in: shuffled, so the right one isn't where the file has it
 *   (always first); null keeps the file's order
 * @param repliers who answers a choice at a turn (the turn's line index → their id) when it isn't [partner]: in a dialog
 *   of several people (an arrival: Micka introduces Zala), whoever spoke last
 * @param missedTurns the turns whose first pick was wrong (a story's turns right the first time are the others: reading
 *   practice, app/ReadingsController)
 * @param rule the grammar book's page of the wrong answer just given (its choice's own `grammar`, else its turn's): the
 *   [why] leads there; null after a right one
 * @param modes how each turn (its line index) is asked when not by choosing: a turn that tests a form the learner has
 *   secure is typed into its gap ([TurnMode.TYPE], [type]), one they have mastered said whole ([TurnMode.SAY], [say]);
 *   "✋ Let me choose" ([letMeChoose]) and a wrong answer bring back the choices. A tap turn in a scene is answered by
 *   tapping the picture ([TurnMode.TAP], [tap], [tapModes]); "✋ Let me choose" brings its choices too
 * @param answers the answers on the grammar book's rules so far, in order ([RuleAnswer])
 * @param hinted the learner opened the current turn's hint ("📖 Namig", [hint]): a right answer to it counts right, but
 *   not on the rule's run (companion/SCENES.md, "The hint")
 * @param notYet the turns (by line index) that say a rule not introduced to the learner yet without asking for it, and
 *   those rules ([si.lanisce.lani.game.Introduction.dialog]): nothing counts on them there, and passing the turn meets
 *   them ([meetings]); an echo turn ([TurnMode.ECHO], [echo]) is only heard and repeated
 * @param meetings the rules not yet met so far, in order, one for each such turn passed: the dialog's screen records the
 *   new ones ([meetingsOf])
 * @param words the turns (by line index) that test the learner's own words, and which ([DialogWords]; companion/SCENES.md,
 *   "Your words in the dialogs"): a word only there, in a line or a choice where the choices differ otherwise, isn't one
 * @param wordAnswers the first answer of each turn on the learner's words it tests, in order ([WordAnswer]): the dialog's
 *   screen counts the new ones as reviews of their cards ([wordsOf]); its end lists them ("Tvoje besede · Your words")
 * @param wordCards the word cards to show after the turns got wrong: each after the line said at its index ([WordShown])
 */
data class DialogRun(
    val dialog: Dialog,
    val partner: String?,
    val at: Int = 0,
    val said: List<Said> = emptyList(),
    val tried: Set<Int> = emptySet(),
    val why: String? = null,
    /** The wrong choice just picked, if the last pick was wrong. */
    val wrongPick: Int? = null,
    val mistakes: Int = 0,
    /** Someone just spoke and the learner taps on (see [Step.LISTEN]). */
    val listening: Boolean = false,
    val picks: List<Int> = emptyList(),
    val seed: Long? = null,
    val repliers: Map<Int, String> = emptyMap(),
    val missedTurns: Int = 0,
    val rule: String? = null,
    val modes: Map<Int, TurnMode> = emptyMap(),
    val answers: List<RuleAnswer> = emptyList(),
    /** The current turn was answered wrong once already (a pick, or a form typed or said). */
    val missed: Boolean = false,
    /** How the person reacted to a form typed wrong that is none of the choices (the turn's puzzled reaction). */
    val puzzledBy: DialogReply? = null,
    val hinted: Boolean = false,
    val notYet: Map<Int, List<String>> = emptyMap(),
    val meetings: List<String> = emptyList(),
    val words: Map<Int, TurnWords> = emptyMap(),
    val wordAnswers: List<WordAnswer> = emptyList(),
    val wordCards: List<WordShown> = emptyList(),
) {
    /** The card of a word the learner got wrong ([answer]), shown after the line said at index [after] of [said]. */
    data class WordShown(val after: Int, val answer: WordAnswer)

    /** The answers on the learner's words [next] (this run after an answer) adds: what the dialog counts on their cards. */
    fun wordsOf(next: DialogRun?): List<WordAnswer> = next?.wordAnswers?.drop(wordAnswers.size).orEmpty()

    /** The learner's words the dialog tested, each once, by the first answer on it: what its end lists. */
    val wordSummary: List<WordAnswer> get() = wordAnswers.distinctBy { it.card.id }

    /**
     * An answer on a rule of the grammar book: its [page], whether it was [right], what the learner [said], and (a wrong
     * one) what was [correct].
     *
     * A wrong choice picked (not tried yet) is a slip on its page (the choice's `grammar`, else the turn's); a form typed
     * wrong that is none of the choices, on the pages of the choices that differ in that word. A right answer, the turn's
     * first, is right on the turn's page: picked, the turn's, else the one page its choices name; typed or said, the
     * turn's, else the pages of its form's choices (what the gap tests). A page only the app guessed
     * ([DialogChoice.guess]) counts nothing. [hinted]: a right answer given after the turn's hint (it counts right, but
     * not on the rule's run: [si.lanisce.lani.game.GrammarBook.answered]).
     */
    data class RuleAnswer(val page: String, val right: Boolean, val said: String, val correct: String?, val hinted: Boolean = false)

    /** The answers on rules [next] (this run after an answer) adds: what the answer counts in the grammar book. */
    fun answersOf(next: DialogRun?): List<RuleAnswer> = next?.answers?.drop(answers.size).orEmpty()

    /** The rules not yet [next] (this run after an answer) met: what the book records as met, not answered ([meetings]). */
    fun meetingsOf(next: DialogRun?): List<String> = next?.meetings?.drop(meetings.size).orEmpty()

    /** The rules not introduced yet the current turn says without asking for them ([notYet]); empty elsewhere. */
    val later: List<String> get() = if (step == Step.CHOOSE) notYet[at].orEmpty() else emptyList()

    /** What picking choice [k] of this turn (as shown) says about a rule, before it is picked; null when it counts nothing. */
    fun ruleAnswer(k: Int): RuleAnswer? = if (step == Step.CHOOSE) answersOf(choose(k)).firstOrNull() else null

    enum class Step { LISTEN, CHOOSE, END }

    val step: Step
        get() = when {
            listening -> Step.LISTEN
            turnAt(at) -> Step.CHOOSE
            else -> Step.END
        }

    /** The learner's turn now, as the dialog has it (its choices in the file's order); null unless it's their turn. */
    val turn: DialogLine? get() = if (step == Step.CHOOSE) dialog.lines[at] else null

    /** "📖 Namig · Hint" opened at the current turn: a right answer to it no longer lengthens the rule's run. */
    fun hint(): DialogRun = if (step == Step.CHOOSE && !hinted) copy(hinted = true) else this

    /** How the learner answers the turn now: [TurnMode.CHOOSE] unless the turn is typed or said ([modes]). */
    val mode: TurnMode get() = if (step == Step.CHOOSE) modes[at] ?: TurnMode.CHOOSE else TurnMode.CHOOSE

    /** The learner's options now, in the order shown; empty unless it's their turn. */
    val choices: List<DialogChoice> get() = if (step == Step.CHOOSE) dialog.lines[at].choices.let { turn -> order(turn.size).map { turn[it] } } else emptyList()

    /** Where each choice shown of this turn is in the file: the same for the whole turn, another order the next. */
    private fun order(n: Int): List<Int> = seed?.let { (0 until n).shuffled(kotlin.random.Random(it * 31 + at)) } ?: (0 until n).toList()

    /** Lines played so far and in all, for a progress hint. */
    val progress: Pair<Int, Int> get() = at.coerceAtMost(dialog.lines.size) to dialog.lines.size

    /**
     * How the person reacted to the wrong choice just picked (its reply), or to a form typed wrong, while the learner picks
     * again; null after a right pick, or when that choice has none.
     */
    val reaction: DialogReply? get() = wrongPick?.let { choices.getOrNull(it) }?.reply ?: puzzledBy

    /**
     * The turn as a sentence with a gap, when its choices test a form ([Forms.turn]: one right choice, wrong ones that
     * differ from it in one word): "Deset ____, prosim." with "jajc" to type. Null otherwise.
     */
    val gap: FormGap? get() = if (step == Step.CHOOSE) form(dialog.lines[at])?.gap else null

    /** What the turn means, for typing and saying it: the right choice's translation. */
    val intent: String? get() = if (step == Step.CHOOSE) dialog.lines[at].choices.firstOrNull { it.ok }?.en?.takeIf { it.isNotBlank() } else null

    private fun form(line: DialogLine): Forms.Turn? {
        val turn = line.choices
        val right = turn.indexOfFirst { it.ok }.takeIf { r -> r >= 0 && turn.count { it.ok } == 1 } ?: return null
        return Forms.turn(turn.map { it.sl }, right)
    }

    /** The pages (named, not guessed) the wrong choices of [line]'s form test: what its gap is about. */
    private fun gapPages(line: DialogLine): List<String> =
        form(line)?.wrong.orEmpty().mapNotNull { line.choices[it].grammar ?: line.grammar }.distinct()

    /** The places the current turn's choices stand for in the picture, when it is a tap turn ([tapTurn]); else empty. */
    val taps: List<String> get() = if (step == Step.CHOOSE && dialog.lines[at].tapTurn) dialog.lines[at].choices.mapNotNull { it.tap } else emptyList()

    /**
     * The learner tapped [target] in the picture (a spot, a thing or a person: [DialogChoice.tap]) at a tap turn: the choice
     * that stands for it is picked, as a tap on it would be ([choose]); a place no choice stands for changes nothing.
     */
    fun tap(target: String): DialogRun {
        if (step != Step.CHOOSE || !dialog.lines[at].tapTurn) return this
        val turn = dialog.lines[at].choices
        val filed = turn.indexOfFirst { it.tap == target }.takeIf { it >= 0 } ?: return this
        return choose(order(turn.size).indexOf(filed))
    }

    /** "✋ Izberi raje · Let me choose": the turn is asked by its choices after all (never a wall). An echo has none to choose. */
    fun letMeChoose(): DialogRun = if (mode == TurnMode.CHOOSE || mode == TurnMode.ECHO) this else copy(modes = modes - at)

    /**
     * [TurnMode.ECHO] ("✓ Naprej · Next", or the line said): the right line is said as the learner's, the person's reply
     * follows, and the rules it says are met ([meetings]). Nothing is graded: no answer counts, no mistake is possible.
     */
    fun echo(): DialogRun {
        if (mode != TurnMode.ECHO) return this
        val turn = dialog.lines[at].choices
        val right = turn.indexOfFirst { it.ok }.takeIf { it >= 0 } ?: return this
        return pick(order(turn.size).indexOf(right), produced = false, graded = false)
    }

    /**
     * [TurnMode.ECHO] said out loud: what the recognizer heard ([alternatives], most likely first), taken gently: the line
     * graded like a speak exercise, or most of its words heard ([ECHO_HEARD]), goes on as [echo]. Null otherwise: the
     * learner says it again or taps on; it is never a mistake.
     */
    fun echo(alternatives: List<String>): DialogRun? {
        if (mode != TurnMode.ECHO) return null
        val line = dialog.lines[at].choices.firstOrNull { it.ok }?.sl ?: return null
        val heard = alternatives.filter { it.isNotBlank() }.ifEmpty { return null }
        return if (echoed(line, heard)) echo() else null
    }

    /**
     * [TurnMode.TYPE] (or a turn to say where nothing can listen): the learner typed [text] into the gap (the word, or the
     * whole sentence), graded like a cloze ([Grading.check]: č/š/ž and a slip in the stem forgiven, never the ending).
     * Right: the sentence is said, as the right choice. Wrong: a mistake, and the choices come; a form that is one of the
     * wrong choices is that choice picked (its reaction, its why), another is said as typed and the person is puzzled (the
     * turn's `puzzled`, else "Hm? Kako, prosim?").
     */
    fun type(text: String): DialogRun {
        if (mode == TurnMode.CHOOSE || mode == TurnMode.TAP || mode == TurnMode.ECHO || text.isBlank()) return this
        val g = gap ?: return letMeChoose()
        val turn = dialog.lines[at].choices
        val right = turn.indexOfFirst { it.ok }
        if (Grading.check(text, g.accept).verdict != Grading.Verdict.WRONG) return pick(order(turn.size).indexOf(right), produced = true)
        val typed = text.trim()
        val word = typed.takeIf { Forms.words(it).size == 1 }
        val sentence = word?.let { g.before + it + g.after } ?: typed
        val back = copy(modes = modes - at)
        // one of the file's wrong choices: as if picked
        val k = turn.indices.firstOrNull { i -> i != right && Grading.fold(turn[i].sl) == Grading.fold(sentence) }
        if (k != null) return back.choose(order(turn.size).indexOf(k))
        return back.slipped(sentence)
    }

    /**
     * [TurnMode.SAY]: what the recognizer heard ([alternatives], most likely first), graded like a speak exercise
     * ([SpeechGrading]: numbers written as digits read as words). The right sentence: said, as the right choice. A wrong
     * choice said clearly ([ChoiceMatch]): that choice picked, and the choices come. Null when neither is clear: the
     * learner says it again (or chooses).
     */
    fun say(alternatives: List<String>): DialogRun? {
        if (mode != TurnMode.SAY) return null
        val turn = dialog.lines[at].choices
        val rights = turn.filter { it.ok }.map { it.sl }
        val heard = SpeechGrading.best(alternatives, null, rights)
        if (heard != null && heard.result.verdict != Grading.Verdict.WRONG) {
            val filed = turn.indexOfFirst { it.ok && it.sl == heard.result.expected }.takeIf { it >= 0 } ?: turn.indexOfFirst { it.ok }
            return pick(order(turn.size).indexOf(filed), produced = true)
        }
        val m = ChoiceMatch.match(alternatives, choices.map { it.sl }) as? ChoiceMatch.Result.Said ?: return null
        if (choices[m.index].ok) return null // the right one, but not quite: again
        return copy(modes = modes - at).choose(m.index)
    }

    /** A form typed wrong that is none of the choices: said, the person puzzled, a slip on the gap's pages; the learner picks. */
    private fun slipped(sentence: String): DialogRun {
        val by = repliers[at] ?: partner
        val line = dialog.lines[at]
        val puzzled = line.puzzled ?: DialogReply(inTarget("adaptive.puzzled"), inBase("adaptive.puzzled"))
        val right = line.choices.firstOrNull { it.ok }?.sl
        val pages = gapPages(line).filter { it !in notYet[at].orEmpty() }
        // the learner's word in the gap, got wrong the first time: its form (another ending of it) or its meaning
        val word = if (missed) null else gapWord(line)?.let { w ->
            WordAnswer(w, false, DialogWords.typed(w.card, sentence.let { s -> Forms.words(s).getOrNull(w.slot) ?: s }), produced = true, at, sentence, right.orEmpty())
        }
        return copy(
            said = said + Said(null, sentence, "", wrong = true) + Said(by, puzzled.sl, puzzled.en),
            why = bi("adaptive.notThatForm"), wrongPick = null, puzzledBy = puzzled, mistakes = mistakes + 1,
            missedTurns = missedTurns + if (missed) 0 else 1, missed = true, rule = line.grammar ?: pages.firstOrNull(),
            answers = answers + pages.map { RuleAnswer(it, false, sentence, right) },
            wordAnswers = wordAnswers + listOfNotNull(word),
        )
    }

    /** The learner's word in the gap of the form turn [line] (typed or said: [TurnWords.inGap]); null when it isn't one of theirs. */
    private fun gapWord(line: DialogLine): TurnWord? {
        val tw = words[at] ?: return null
        val right = line.choices.indexOfFirst { it.ok }.takeIf { it >= 0 } ?: return null
        val slot = form(line)?.gap?.slot ?: return null
        return tw.inGap(right, slot)
    }

    /**
     * What answering choice [filed] (its index in the file) the first time says about the learner's words the turn tests
     * ([words]): a right one answers them right (typed or said, [produced]: the word in the gap; a tap turn, the thing
     * tapped); a wrong one gets wrong those it tests (a tap turn, the thing the right place was). Empty when the turn tests
     * none of theirs, isn't graded (an echo), or was answered before.
     */
    private fun wordAnswersOf(line: DialogLine, filed: Int, right: Boolean, produced: Boolean): List<WordAnswer> {
        val tw = words[at] ?: return emptyList()
        if (missed) return emptyList()
        val c = line.choices[filed]
        val expected = line.choices.firstOrNull { it.ok }?.sl.orEmpty()
        tw.thing?.let { t -> return listOf(WordAnswer(t, right, WordTest.THING, produced = false, at, c.sl, expected)) }
        if (!right) return tw.missed(filed).map { (w, how) -> WordAnswer(w, false, how, produced, at, c.sl, expected) }
        val got = if (produced) listOfNotNull(form(line)?.gap?.slot?.let { tw.inGap(filed, it) }) else tw.answered(filed)
        return got.map { w ->
            val how = tw.byRight[filed].orEmpty().firstOrNull { it.word.card.id == w.card.id }?.by?.values.orEmpty()
            WordAnswer(w, true, if (WordTest.MEANING in how) WordTest.MEANING else WordTest.FORM, produced, at, c.sl, c.sl)
        }
    }

    /** The sky the dialog's cues have made of [base] so far (see [skyAt]). */
    fun sky(base: Sky): Sky = dialog.skyAt(at, picks, base)

    /**
     * The scene's effects the dialog's cues have set over [base] so far (see [fxAt]), and those of the [reaction] while
     * it lasts: they pass with it (Micka brings the water, and takes it away once Jan says what they meant).
     */
    fun fx(base: Map<String, Float>): Map<String, Float> = dialog.fxAt(at, picks, base).let { m -> reaction?.fx?.takeIf { it.isNotEmpty() }?.let { m + it } ?: m }

    /**
     * Where the dialog's stage directions have put people so far, over [base] (everyone's placement before it, see
     * [actAt]), and the [reaction]'s while it lasts (Zala peeks out, "Tu me ni!", and hides again at the next pick). Only
     * the people moved.
     */
    fun act(base: Map<String, Placement>): Map<String, Placement> = actOver(dialog.actAt(at, picks, base), reaction?.act.orEmpty(), base)

    /** On from a line the learner listened to. */
    fun next(): DialogRun = if (step == Step.LISTEN) copy(listening = false).play() else this

    /** The learner picks choice [k] of their turn, as shown ([choices]). */
    fun choose(k: Int): DialogRun = pick(k, produced = false)

    /**
     * Choice [k] (as shown) answered: picked, or ([produced]) typed or said as the right one; not [graded]: an echo, heard
     * and repeated, which counts on no rule. A rule not yet of the turn ([notYet]) counts nothing either way: passing the
     * turn meets it.
     */
    private fun pick(k: Int, produced: Boolean, graded: Boolean = true): DialogRun {
        if (step != Step.CHOOSE) return this
        val line = dialog.lines[at]
        val turn = line.choices
        val filed = order(turn.size).getOrNull(k) ?: return this
        val c = turn[filed]
        val right = c.ok || turn.none { it.ok } // a turn without a right answer must not trap the learner
        val by = repliers[at] ?: partner
        val notHere = notYet[at].orEmpty()
        if (!right) {
            if (k in tried) return this // the same wrong choice again doesn't count twice
            // with a reaction, the wrong choice is said and the person answers what they heard; the learner picks again
            val heard = c.reply?.let { listOf(Said(null, c.sl, c.en, wrong = true), Said(by, it.sl, it.en)) }.orEmpty()
            val page = (c.grammar ?: line.grammar)?.takeIf { it !in notHere }
            return copy(
                said = said + heard, tried = tried + k, why = c.why ?: bi("dialogRun.tryAnotherOne"), wrongPick = k, mistakes = mistakes + 1,
                missedTurns = missedTurns + if (missed) 0 else 1, missed = true, puzzledBy = null, rule = page,
                answers = answers + listOfNotNull(page?.let { RuleAnswer(it, false, c.sl, turn.firstOrNull { o -> o.ok }?.sl) }),
                wordAnswers = wordAnswers + if (graded) wordAnswersOf(line, filed, right = false, produced) else emptyList(),
            )
        }
        // right the first time: on the turn's page (picked: the one its choices name; typed or said: what its gap tests)
        val pages = when {
            missed || !graded -> emptyList()
            produced -> line.grammar?.let(::listOf) ?: gapPages(line)
            else -> listOfNotNull(line.grammar ?: turn.mapNotNull { it.grammar }.distinct().singleOrNull())
        }.filter { it !in notHere }
        // the learner's words the turn tests: right the first time, or (got wrong before) their cards after the turn
        val words = if (graded) wordAnswersOf(line, filed, right = true, produced) else emptyList()
        val missedHere = wordAnswers.filter { it.turn == at && !it.right }
        val mine = copy(
            at = at + 1, said = said + Said(null, c.sl, c.en), tried = emptySet(), why = null, wrongPick = null, picks = picks + filed,
            rule = null, missed = false, puzzledBy = null, answers = answers + pages.map { RuleAnswer(it, true, c.sl, null, hinted) }, hinted = false,
            meetings = meetings + notHere, wordAnswers = wordAnswers + words,
        )
        val shown = { r: DialogRun -> if (missedHere.isEmpty()) r else r.copy(wordCards = r.wordCards + missedHere.map { WordShown(r.said.lastIndex, it) }) }
        val reply = c.reply ?: return shown(mine).play()
        return shown(mine.copy(said = mine.said + Said(by, reply.sl, reply.en))).afterLine()
    }

    /** Plays from [at]: someone's line is said; the learner's turn waits for a choice; past the end is [Step.END]. */
    private fun play(): DialogRun {
        val line = dialog.lines.getOrNull(at) ?: return this
        if (line.choices.isNotEmpty()) return this
        val sl = line.sl
        if (sl.isNullOrBlank()) return copy(at = at + 1).play() // nothing to say: skip it
        return copy(at = at + 1, said = said + Said(line.who ?: partner, sl, line.en.orEmpty())).afterLine()
    }

    /** After someone spoke: the learner's turn right away when one follows, else they listen and tap on. */
    private fun afterLine(): DialogRun = if (turnAt(at)) this else copy(listening = true)

    private fun turnAt(i: Int) = i < dialog.lines.size && dialog.lines[i].choices.isNotEmpty()

    companion object {
        /** Of an echo's words, at least this share heard makes it said ([echo]). */
        const val ECHO_HEARD = 0.6

        /** Whether one of [heard] (the recognizer's alternatives) says [line]: graded right or close, or most of its words there. */
        internal fun echoed(line: String, heard: List<String>): Boolean {
            val best = SpeechGrading.best(heard, null, listOf(line)) ?: return false
            if (best.result.verdict != Grading.Verdict.WRONG) return true
            val words = Forms.words(line).size.takeIf { it > 0 } ?: return false
            return heard.any { h -> 1.0 - SpeechGrading.differingWords(line, h).size.toDouble() / words >= ECHO_HEARD }
        }

        /**
         * [modes]: how its turns are asked when not by choosing (by the line index; see [DialogRun]); [notYet]: the turns
         * that say rules not introduced yet, and those rules ([si.lanisce.lani.game.Introduction.dialog]); [words]: the turns
         * that test the learner's own words ([DialogWords.of], on [dialog] as it is played).
         */
        fun start(
            dialog: Dialog, partner: String?, seed: Long? = null, repliers: Map<Int, String> = emptyMap(), modes: Map<Int, TurnMode> = emptyMap(),
            notYet: Map<Int, List<String>> = emptyMap(), words: Map<Int, TurnWords> = emptyMap(),
        ): DialogRun =
            DialogRun(
                dialog, partner, seed = seed, repliers = repliers, modes = modes.filterValues { it != TurnMode.CHOOSE }, notYet = notYet.filterValues { it.isNotEmpty() },
                words = words.filterValues { !it.isEmpty },
            ).play()

        /**
         * [dialog]'s tap turns asked by tapping the picture ([TurnMode.TAP]), by their line index: where it is played in its
         * scene, the picture above it (not over the village, nor in a story).
         */
        fun tapModes(dialog: Dialog): Map<Int, TurnMode> =
            dialog.lines.withIndex().filter { it.value.tapTurn }.associate { it.index to TurnMode.TAP }
    }
}
