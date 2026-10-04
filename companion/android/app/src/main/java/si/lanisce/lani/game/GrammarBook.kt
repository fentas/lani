package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.MistakeNote
import si.lanisce.lani.data.ReviewCard
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * What the learner met of a grammar page: the day it unlocked ([on], ISO), the answers on its rule ([right], [wrong]),
 * and the latest of them: sentences said right ([said]) and slips ([slips]), [GrammarBook.KEEP] of each.
 *
 * [run]: the right answers in a row now (a slip steps it back a level, [Masteries.afterSlip]), since the day [since];
 * [last]: the day of the latest answer on the run (a right one after the hint leaves the run and its days alone). How
 * well the rule is mastered goes by them ([Masteries.of]). A record from before runs were kept has none: [streak] and
 * [runSince] read it.
 */
@Serializable
data class RuleRecord(
    val on: String,
    val right: Int = 0,
    val wrong: Int = 0,
    val said: List<String> = emptyList(),
    val slips: List<RuleSlip> = emptyList(),
    val run: Int? = null,
    val since: String? = null,
    val last: String? = null,
) {
    /** The right answers in a row: [run]; in a record from before, every answer when none was wrong. */
    val streak: Int get() = run ?: if (wrong == 0) right else 0

    /** The day the [streak] began: [since]; in a record from before without a slip, the day the page unlocked. */
    val runSince: String? get() = since ?: if (run == null && wrong == 0 && right > 0) on else null
}

/** A wrong answer on a rule: what the learner wrote, and what was right. */
@Serializable
data class RuleSlip(val answer: String, val correct: String)

/**
 * The grammar book's chapter in the village book (companion/GAME.md, "The grammar book"): the pages the learner has met
 * ([GameState.grammar], by "<language>/<page id>": a village's book is of its language), what unlocks one, the answers on
 * a rule, and how well it sits. Pure, like the engine.
 */
object GrammarBook {
    /** Sentences and slips kept per rule. */
    const val KEEP = 3

    fun key(language: String, id: String) = "$language/$id"

    /** The pages of [language] met, by page id. */
    fun met(s: GameState, language: String): Map<String, RuleRecord> {
        val prefix = "$language/"
        return s.grammar.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
    }

    fun isMet(s: GameState, language: String, id: String): Boolean = key(language, id) in s.grammar

    /**
     * The learner meets the rules of [ids] (an exercise, a challenge, a module that names them): the pages not met yet
     * unlock [today]. Returns the village and the pages newly met, in [ids]' order.
     */
    fun meet(s: GameState, language: String, ids: Collection<String>, today: LocalDate): Pair<GameState, List<String>> {
        val fresh = ids.distinct().filter { !isMet(s, language, it) }
        if (fresh.isEmpty()) return s to emptyList()
        return s.copy(grammar = s.grammar + fresh.associate { key(language, it) to RuleRecord(today.toString()) }) to fresh
    }

    /**
     * An answer on rule [id] ([right] or not): the page is met (if it wasn't), the answer counted, and kept: the whole
     * sentence said right ([said]), or the [slip]. A right one lengthens the run of right answers, a wrong one steps it
     * back a level ([Masteries.afterSlip]). A right one given after the turn's hint ([hinted]: "📖 Namig", companion/
     * SCENES.md "The hint") counts right and keeps its sentence, but leaves the run as it was, neither longer nor broken:
     * what makes a rule secure or mastered is answered without help. Returns the village and whether the page was newly met.
     */
    fun answered(
        s: GameState, language: String, id: String, right: Boolean, said: String?, slip: RuleSlip?, today: LocalDate, hinted: Boolean = false,
    ): Pair<GameState, Boolean> {
        val (met, fresh) = meet(s, language, listOf(id), today)
        val k = key(language, id)
        val r = met.grammar.getValue(k)
        val day = today.toString()
        val n = if (right && hinted) {
            // the run kept as it is (a record from before runs were kept gets its run written out: its streak would grow with right)
            r.copy(
                right = r.right + 1, said = said?.takeIf { it.isNotBlank() }?.let { (r.said - it + it).takeLast(KEEP) } ?: r.said,
                run = r.streak, since = r.runSince,
            )
        } else if (right) {
            r.copy(
                right = r.right + 1, said = said?.takeIf { it.isNotBlank() }?.let { (r.said - it + it).takeLast(KEEP) } ?: r.said,
                run = r.streak + 1, since = r.runSince.takeIf { r.streak > 0 } ?: day, last = day,
            )
        } else {
            val run = Masteries.afterSlip(r.streak)
            r.copy(
                wrong = r.wrong + 1, slips = slip?.let { (r.slips - it + it).takeLast(KEEP) } ?: r.slips,
                run = run, since = r.runSince.takeIf { run > 0 }, last = day,
            )
        }
        return met.copy(grammar = met.grammar + (k to n)) to fresh.isNotEmpty()
    }

    /**
     * The chapter: the pages of [pages] met, in the order met (then the book's), the rules not introduced yet the dialogs
     * said ([seen], with their meetings, most met first: [Introduction]), and how many more pages are still to come.
     */
    data class Chapter(val met: List<GrammarPage>, val more: Int, val seen: List<Pair<GrammarPage, RuleMeetings>> = emptyList())

    fun chapter(pages: List<GrammarPage>, s: GameState?, language: String): Chapter {
        val m = s?.let { met(it, language) }.orEmpty()
        val order = pages.withIndex().associate { (i, p) -> p.id to i }
        val shown = pages.filter { it.id in m }.sortedWith(compareBy<GrammarPage>({ m[it.id]?.on.orEmpty() }, { order[it.id] ?: 0 }))
        val meetings = s?.let { Introduction.meetings(it, language) }.orEmpty()
        val seen = pages.filter { it.id !in m }.mapNotNull { p -> meetings[p.id]?.takeIf { it.times > 0 }?.let { p to it } }
            .sortedWith(compareBy({ -it.second.times }, { order[it.first.id] ?: 0 }))
        return Chapter(shown, pages.size - shown.size - seen.size, seen)
    }

    /**
     * How well the rule sits, 0 to 5 (null: nothing practised on it yet): the answers on it in the app (right of all,
     * weighing up to 8 answers), and the learner's grammar cards on it (how far their reviews are: 5 at 5 in a row, a
     * failed last review 1), weighed together; each mistake still open on it ([stillOpen]) takes one off (two at most).
     */
    fun meter(record: RuleRecord?, cards: List<ReviewCard>, mistakes: List<MistakeNote>): Int? {
        val answers = (record?.right ?: 0) + (record?.wrong ?: 0)
        val parts = buildList {
            if (answers > 0) add(5.0 * record!!.right / answers to answers.coerceAtMost(8).toDouble())
            for (c in cards.take(8)) add((if (c.lastQuality < 3) 1.0 else c.repetitions.coerceIn(1, 5).toDouble()) to 1.0)
        }
        if (parts.isEmpty()) return if (mistakes.isEmpty()) null else 0
        val level = parts.sumOf { it.first * it.second } / parts.sumOf { it.second }
        return (level.roundToInt() - mistakes.count { stillOpen(it, record) }.coerceAtMost(2)).coerceIn(0, 5)
    }

    /**
     * Mistake [m] on a rule is still to fix: it is [MistakeNote.open] (its last answer wrong, no review right since), and
     * the run of right answers on the rule in the app ([record]) didn't begin after it last happened.
     */
    fun stillOpen(m: MistakeNote, record: RuleRecord?): Boolean {
        if (!m.open) return false
        val since = record?.runSince ?: return true
        return m.lastSeen == null || since <= m.lastSeen
    }
}
