package si.lanisce.lani.game

import si.lanisce.lani.data.Exercise
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.tapTurn

/** A choice exercise as the learner meets it: its options (with their own trap), how it is asked, and its sentence with the gap. */
data class AdaptedChoice(val exercise: Exercise.Choice, val mode: TurnMode, val gap: FormGap?)

/**
 * A dialog as the learner meets it (companion/SCENES.md, "Adaptive turns", "Own traps", "Rules not yet"): its turns trimmed
 * of the rules not introduced yet and with the learner's own traps ([dialog]), how each turn is asked where not by
 * choosing ([modes], by line index: typed, said, an echo), and the rules not yet each turn only meets ([notYet]).
 */
data class AdaptedDialog(val dialog: Dialog, val modes: Map<Int, TurnMode> = emptyMap(), val notYet: Map<Int, List<String>> = emptyMap())

/**
 * The turns and exercises that follow how well the learner has their rule (companion/GAME.md, "Mastery and adaptive
 * turns"; companion/SCENES.md, "Adaptive turns"). Pure: the grammar controller hands it the masteries and the traps.
 */
object Adaptive {
    /**
     * [d] as the learner meets it, all of it pure: first the turns of rules not introduced yet ([Introduction.dialog],
     * [levelOf] says [Mastery.NOT_YET]) trimmed to what they can choose, or echoed; then the learner's own traps in the other
     * turns ([trap]: the dialog, the lines to leave alone → the dialog with them); then how the other turns are asked
     * ([modes]). A turn of a rule not yet is chosen, or an echo: never typed or said, and no trap of theirs is set in it.
     */
    fun dialog(d: Dialog, levelOf: (String) -> Mastery?, canSay: Boolean, trap: (Dialog, Set<Int>) -> Dialog = { x, _ -> x }): AdaptedDialog {
        val gated = Introduction.dialog(d) { levelOf(it) == Mastery.NOT_YET }
        val trapped = trap(gated.dialog, gated.notYet.keys)
        val modes = modes(trapped, levelOf, canSay) - gated.notYet.keys + gated.echo.associateWith { TurnMode.ECHO }
        return AdaptedDialog(trapped, modes, gated.notYet)
    }

    /**
     * How each turn of [d] is asked, by its line index, where not by choosing: a turn that tests a form ([Forms.turn]: one
     * right choice, wrong ones that differ from it in one word) whose wrong choices of that word all have a rule (their
     * page, their turn's, else the guess: [si.lanisce.lani.game.scene.DialogChoice.rule]; [levelOf], null for one the
     * book lacks: new), asked as its weakest rule allows ([Masteries.mode]). A turn with such a choice of no rule, or two
     * right choices, is chosen, as always; so is a tap turn ([si.lanisce.lani.game.scene.tapTurn]: each choice a place in
     * the picture).
     */
    fun modes(d: Dialog, levelOf: (String) -> Mastery?, canSay: Boolean): Map<Int, TurnMode> =
        d.lines.withIndex().mapNotNull { (i, line) ->
            val turn = line.choices
            if (line.tapTurn) return@mapNotNull null // a tap turn is chosen: tapped in the picture, or picked
            val right = turn.indexOfFirst { it.ok }.takeIf { r -> r >= 0 && turn.count { it.ok } == 1 } ?: return@mapNotNull null
            val form = Forms.turn(turn.map { it.sl }, right) ?: return@mapNotNull null
            val rules = form.wrong.map { turn[it].rule(line) }
            if (rules.any { it == null }) return@mapNotNull null
            val mode = Masteries.mode(rules.filterNotNull().distinct().map { levelOf(it) ?: Mastery.NEW }, canSay)
            if (mode == TurnMode.CHOOSE) null else i to mode
        }.toMap()

    /**
     * A choice exercise on [rule] (at [level]): with a sentence to fill ([Forms.gap], two or more underscores; not a
     * listening one) it is typed once the rule is secure and said once mastered ([Masteries.mode]); its options get the
     * learner's own trap ([trap]: the options, the right one's index → the options to show). Without a rule or a gap, as it is.
     */
    fun choice(ex: Exercise.Choice, rule: String?, level: Mastery?, canSay: Boolean, trap: (List<String>, Int) -> List<String>): AdaptedChoice {
        val options = if (rule != null && ex.audio == null) trap(ex.options, ex.answer) else ex.options
        val shown = if (options == ex.options) ex else ex.copy(options = options)
        val gap = ex.options.getOrNull(ex.answer)?.takeIf { ex.audio == null }?.let { Forms.gap(ex.prompt, it) }
        val mode = if (rule == null || gap == null) TurnMode.CHOOSE else Masteries.mode(listOf(level ?: Mastery.NEW), canSay)
        return AdaptedChoice(shown, mode, gap)
    }
}
