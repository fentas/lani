package si.lanisce.lani.game

import si.lanisce.lani.data.MistakeNote
import si.lanisce.lani.data.ReviewCard

/**
 * How far the learner has a rule (a grammar book page: "the dual", "the genitive after nimam"), from the spaced-repetition
 * data and the mistakes (companion/GAME.md, "Mastery and adaptive turns"). A turn that tests the rule is asked as it
 * suits ([TurnMode]): choosing while [LEARNING], typing the missing word once [SECURE], saying the whole sentence once
 * [MASTERED]; a rule [NOT_YET] introduced isn't asked for at all ([Introduction]).
 */
enum class Mastery {
    /**
     * Not introduced yet, and above the learner's level: its page's level is higher than theirs, its page isn't unlocked,
     * and they have no answers, cards or mistakes on it ([Introduction.mastery]). Its turns don't ask for its form: the
     * learner chooses about meaning, or hears the line and repeats it ([TurnMode.ECHO]); they only meet it.
     */
    NOT_YET,

    /** Nothing practised on the rule yet: no answers, no grammar cards, no mistakes. */
    NEW,

    /** Practised, not secure yet (or a mistake on it is still open). */
    LEARNING,

    /** [Masteries.SECURE_RUN] right in a row, at least [Masteries.SECURE_STARS] stars, no open mistake. */
    SECURE,

    /** [Masteries.MASTERED_RUN] right in a row over two days or more, at least [Masteries.MASTERED_STARS] stars, no open mistake. */
    MASTERED,
}

/** How a turn that tests a rule is answered ([Masteries.mode]). */
enum class TurnMode {
    /** Pick one of the choices, as always. */
    CHOOSE,

    /** Type the missing word into the sentence, with the letter chips; graded like a cloze ([si.lanisce.lani.data.Grading]). */
    TYPE,

    /** Say the whole sentence; graded like a speak exercise ([si.lanisce.lani.data.SpeechGrading]). */
    SAY,

    /**
     * Tap the place, the thing or the person in the scene's picture a choice stands for (a tap turn, companion/SCENES.md
     * "Tap turns"); the choices wait behind "✋ Izberi raje · Let me choose". Only a scene's dialog, where the picture shows.
     */
    TAP,

    /**
     * Hear the right line and repeat it (companion/SCENES.md, "Rules not yet"): a turn whose form is of a rule not yet
     * introduced ([Mastery.NOT_YET]) and has nothing else to choose. The line with its translation and 🔊, the 🎤 to say it
     * (never failing) and "✓ Naprej · Next"; nothing is graded, no mistake is possible, the rule is met ([Introduction]).
     */
    ECHO,
}

/** The mastery of a rule, and the turn's mode it leads to. Pure. */
object Masteries {
    /** Right answers in a row for [Mastery.SECURE]. */
    const val SECURE_RUN = 4

    /** Right answers in a row for [Mastery.MASTERED]. */
    const val MASTERED_RUN = 8

    /** The grammar page's stars ([GrammarBook.meter]) at least, for [Mastery.SECURE]. */
    const val SECURE_STARS = 3

    /** The stars at least, for [Mastery.MASTERED]. */
    const val MASTERED_STARS = 4

    /**
     * The rule's mastery: what the learner answered on it in the app ([record]: the run of right answers, the answers),
     * their grammar [cards] on it (the reviews), and their [mistakes] on it (mistakes-db), as the grammar page weighs them
     * ([GrammarBook.meter]). A mistake still open ([GrammarBook.stillOpen]) keeps it [Mastery.LEARNING].
     */
    fun of(record: RuleRecord?, cards: List<ReviewCard>, mistakes: List<MistakeNote>): Mastery {
        val stars = GrammarBook.meter(record, cards, mistakes) ?: return Mastery.NEW
        if (mistakes.any { GrammarBook.stillOpen(it, record) }) return Mastery.LEARNING
        val run = record?.streak ?: 0
        // mastered is kept over days, not in one sitting: the run began on an earlier day than its latest answer
        val days = record?.runSince?.let { since -> record.last?.let { it > since } } == true
        return when {
            stars >= MASTERED_STARS && run >= MASTERED_RUN && days -> Mastery.MASTERED
            stars >= SECURE_STARS && run >= SECURE_RUN -> Mastery.SECURE
            else -> Mastery.LEARNING
        }
    }

    /** The run after a slip: a level back ([MASTERED_RUN] or more → [SECURE_RUN]; else nothing). */
    fun afterSlip(run: Int): Int = if (run >= MASTERED_RUN) SECURE_RUN else 0

    /**
     * How a turn is asked when its rules are at [levels] (a turn may test several: the weakest decides): choosing
     * unless all are secure; typing when secure; saying when mastered and something can listen ([canSay]), else typing.
     * A turn of no rule is chosen, and so is one of a rule [Mastery.NOT_YET] here: [Introduction.dialog] trims it first.
     */
    fun mode(levels: Collection<Mastery>, canSay: Boolean): TurnMode = when (levels.minOrNull()) {
        Mastery.SECURE -> TurnMode.TYPE
        Mastery.MASTERED -> if (canSay) TurnMode.SAY else TurnMode.TYPE
        else -> TurnMode.CHOOSE
    }
}
