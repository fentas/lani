package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.MistakeNote
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.tapTurn
import java.time.LocalDate

/**
 * How often the learner met a rule in the dialogs before it was introduced (companion/GAME.md, "Rules not yet"): the
 * turns that said a line of its form without asking for it ([Introduction.dialog]), [times] in all, on [days] days, the
 * [first] and the [last] of them (ISO).
 */
@Serializable
data class RuleMeetings(val times: Int = 0, val first: String = "", val last: String = "", val days: Int = 0)

/**
 * A learner's turn with a rule not yet introduced to the learner ([Mastery.NOT_YET]), as they meet it: the choices it
 * keeps ([keep], the file's indices: the right ones and the wrong ones about meaning), and [pages], the rules not yet it
 * says without asking for them. [echo]: nothing wrong is left to choose; the learner hears the right line and repeats it.
 */
data class Gate(val pages: List<String>, val keep: List<Int>, val echo: Boolean)

/**
 * A dialog as the learner meets it: its turns with a rule not yet trimmed to what they can choose ([dialog]), the echo
 * turns ([echo], by line index), and each such turn's rules not yet ([notYet], by line index: met there, not answered).
 */
data class Gated(val dialog: Dialog, val echo: Set<Int> = emptySet(), val notYet: Map<Int, List<String>> = emptyMap())

/**
 * What the learner is asked grows with the grammar introduced to them (companion/SCENES.md, "Rules not yet"; GAME.md,
 * "Rules not yet"). What the others say stays natural; what the learner must choose or produce is only the rules they
 * have been introduced to. Pure.
 *
 * - **Not yet** ([Mastery.NOT_YET], below new): a rule (a page of the grammar book) whose page's level is above the
 *   learner's ([above]) and that wasn't introduced ([introduced]: its page unlocked, or answers, cards or mistakes on it).
 * - **A turn** that asks for a rule not yet ([turn]) doesn't: its wrong choices of that rule go, and so do the other wrong
 *   choices of its form; those about meaning stay, to choose from. With none left it is an echo turn: the right line to
 *   hear and repeat. A tap turn is tapped as ever, its right tap a meeting.
 * - **Meetings** ([met]): each such turn passed meets its rules. [RIPE] meetings of a rule one level above the learner's
 *   make it ripe to introduce (the tutor hears of it, [ripe]); [UNLOCK] on [UNLOCK_DAYS] days or more, and the book opens
 *   its page itself ([unlocks]).
 */
object Introduction {
    /** Meetings that make a rule ripe to introduce: the tutor hears of it. */
    const val RIPE = 3

    /** Meetings after which the book opens the page itself (on [UNLOCK_DAYS] days or more). */
    const val UNLOCK = 6

    /** The days the [UNLOCK] meetings are spread over, at least. */
    const val UNLOCK_DAYS = 2

    /** The CEFR levels, in order. */
    val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    private val LEVEL = Regex("""([ABC])\s*([12])""", RegexOption.IGNORE_CASE)

    /** A level's place in [LEVELS] ("A2" → 1; "a2", "A2+" alike); null for one not known. */
    fun rank(level: String?): Int? {
        val m = LEVEL.find(level ?: return null) ?: return null
        return LEVELS.indexOf(m.groupValues[1].uppercase() + m.groupValues[2]).takeIf { it >= 0 }
    }

    /** Whether a page of level [page] is above a learner at [learner] (a level not known: not). */
    fun above(page: String?, learner: String?): Boolean {
        val p = rank(page) ?: return false
        val l = rank(learner) ?: return false
        return p > l
    }

    /** Whether a page of level [page] is the one level above a learner at [learner] (i + 1): the next to introduce. */
    fun next(page: String?, learner: String?): Boolean {
        val p = rank(page) ?: return false
        val l = rank(learner) ?: return false
        return p == l + 1
    }

    /**
     * Whether the rule was introduced to the learner: its page unlocked ([met]: by a module, a tutor's page, the book, a
     * why), or they have answers ([record]), grammar [cards] or [mistakes] on it.
     */
    fun introduced(met: Boolean, record: RuleRecord?, cards: List<ReviewCard>, mistakes: List<MistakeNote>): Boolean =
        met || record != null || cards.isNotEmpty() || mistakes.isNotEmpty()

    /**
     * How far the learner has a rule whose page is at [pageLevel], at their level [learnerLevel]: [Mastery.NOT_YET] when the
     * page is above it and the rule wasn't [introduced]; else as practised ([Masteries.of]).
     */
    fun mastery(
        pageLevel: String?, learnerLevel: String?, met: Boolean, record: RuleRecord?, cards: List<ReviewCard>, mistakes: List<MistakeNote>,
    ): Mastery =
        if (above(pageLevel, learnerLevel) && !introduced(met, record, cards, mistakes)) Mastery.NOT_YET
        else Masteries.of(record, cards, mistakes)

    // --- turns ----------------------------------------------------------------------------------------------------

    /**
     * Learner's turn [line] with its rules [notYet] (a page id → whether it is not yet); null when it asks for none.
     *
     * A wrong choice goes when its rule ([si.lanisce.lani.game.scene.DialogChoice.rule]: its page, its turn's, else the
     * guess) is not yet; and when the turn tests a form ([Forms.turn]: one right choice, wrong ones that differ from it in
     * one word) and one of the form's wrong choices is of a rule not yet, all its form's wrong choices of a rule go: the
     * form isn't asked for. A wrong choice about meaning (other words, or one word of no rule) stays. With one right choice
     * and no wrong one left, the turn is an echo. A tap turn keeps its choices: finding a place isn't a form; a right tap on
     * a page not yet only meets it. [Gate.pages]: the rules not yet it says (the wrong choices', the turn's, the right
     * choices').
     */
    fun turn(line: DialogLine, notYet: (String) -> Boolean): Gate? {
        val c = line.choices
        if (c.size < 2) return null
        val rights = c.indices.filter { c[it].ok }
        if (rights.isEmpty()) return null
        fun rule(i: Int): String? = c[i].rule(line)
        fun later(i: Int): Boolean = rule(i)?.let(notYet) == true
        val named = (c.indices.filter { !c[it].ok }.mapNotNull(::rule) + listOfNotNull(line.grammar) + rights.mapNotNull { c[it].grammar })
            .distinct().filter(notYet)
        if (named.isEmpty()) return null
        if (line.tapTurn) return Gate(named, c.indices.toList(), echo = false)
        val form = rights.singleOrNull()?.let { r -> Forms.turn(c.map { it.sl }, r) }
        val formLater = form != null && form.wrong.any(::later)
        val dropped = c.indices.filter { i -> !c[i].ok && (later(i) || (formLater && i in form!!.wrong && rule(i) != null)) }.toSet()
        val keep = c.indices.filter { it !in dropped }
        return Gate(named, keep, echo = rights.size == 1 && keep.size == 1)
    }

    /**
     * [d] as the learner meets it, their rules [notYet]: each turn that asks for one trimmed to its [Gate.keep] (an echo
     * turn to its right choice), and which are echoes and what each meets ([Gated]). As it is when none does.
     */
    fun dialog(d: Dialog, notYet: (String) -> Boolean): Gated {
        val gates = d.lines.withIndex().mapNotNull { (i, l) -> turn(l, notYet)?.let { i to it } }.toMap()
        if (gates.isEmpty()) return Gated(d)
        val lines = d.lines.mapIndexed { i, l ->
            val g = gates[i] ?: return@mapIndexed l
            if (g.keep.size == l.choices.size) l else l.copy(choices = g.keep.map { l.choices[it] })
        }
        return Gated(d.copy(lines = lines), gates.filterValues { it.echo }.keys, gates.mapValues { it.value.pages })
    }

    // --- meetings -------------------------------------------------------------------------------------------------

    /** The meetings of [language]'s rules, by page id. */
    fun meetings(s: GameState, language: String): Map<String, RuleMeetings> {
        val prefix = "$language/"
        return s.meetings.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
    }

    /** The learner met the rules of [ids] (each once, whatever the list repeats) in a dialog [today], without being asked for them. */
    fun met(s: GameState, language: String, ids: Collection<String>, today: LocalDate): GameState {
        if (ids.isEmpty()) return s
        val day = today.toString()
        val add = ids.distinct().associate { id ->
            val k = GrammarBook.key(language, id)
            val m = s.meetings[k]
            k to if (m == null || m.times == 0) RuleMeetings(1, day, day, 1)
            else m.copy(times = m.times + 1, last = day, days = m.days + if (m.last == day) 0 else 1)
        }
        return s.copy(meetings = s.meetings + add)
    }

    /**
     * Whether a rule met [m] times is ripe to introduce: [RIPE] meetings or more, and its page (at [pageLevel]) the next
     * level of the learner's ([learnerLevel], [next]). The tutor hears of it; a rule further up waits for the learner.
     */
    fun ripe(m: RuleMeetings?, pageLevel: String?, learnerLevel: String?): Boolean =
        m != null && m.times >= RIPE && next(pageLevel, learnerLevel)

    /**
     * Whether the book opens the page of a rule met [m] times itself: [UNLOCK] meetings or more on [UNLOCK_DAYS] days or
     * more, of a rule of the next level ([next]). Never a wall: no tutor needed.
     */
    fun unlocks(m: RuleMeetings?, pageLevel: String?, learnerLevel: String?): Boolean =
        m != null && m.times >= UNLOCK && m.days >= UNLOCK_DAYS && next(pageLevel, learnerLevel)
}
