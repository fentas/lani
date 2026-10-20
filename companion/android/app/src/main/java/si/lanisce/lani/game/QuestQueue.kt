package si.lanisce.lani.game

import si.lanisce.lani.game.villagers.Reactions
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Which open requests show now (companion/GAME.md, "Quests": "At most two at once"). A villager shows at most [UP] requests:
 * their own (the local one) first, then a task of the tutor's that comes back from its drill, then the tutor's newest. The
 * rest wait in the Scroll's "📜 Kasneje · Later", in the order they come up as those are done, and any of them opens from
 * there. A tutor's task waits behind a smaller drill for it at the same villager (the drill's [Quest.helps]) until the drill
 * is passed; a local request never started for [STALE_DAYS] days waits too (the villager asks again another day); a drill
 * whose task is passed already is practice for later ([Why.Spent]). While wolves, a bear or a storm are about the requests
 * up fold away until it's over ([Why.Danger]). Pure.
 */
object QuestQueue {
    /** Requests a villager shows at once (a bubble on the map, a row on their card, one in "Prošnje vaščanov"). */
    const val UP = 2

    /**
     * A local request never started waits in "Later" from this many days after it was asked: the map shows the villager's own
     * request on the day it is asked and the next, and it still expires [Quests.LIFETIME_DAYS] days after it was asked.
     */
    const val STALE_DAYS = 2

    /** Tries below the pass mark after which a task without a drill offers "🎯 Vadi, kar ti ne gre · Practise what's hard". */
    const val HARD_TRIES = 3

    /** The tag of the tutor's short drills (lani-studio): such a module may be a drill for an older task it shares a page with. */
    const val SHORT = "short"

    /** At most this many pages kept in [Quest.missed]. */
    const val MISSED_MAX = 5

    /** Why a request waits in "Later". */
    sealed interface Why {
        /** Behind [drill], the tutor's smaller drill for it at the same villager: it comes back once the drill is passed. */
        data class After(val drill: Quest) : Why

        /** The villager has [UP] requests up already: it comes up as those are done. */
        data object Queued : Why

        /** The villager's own request, never started for [STALE_DAYS] days: they ask again another day. */
        data object Stale : Why

        /**
         * Up but for [kind] about (wolves, a bear, a storm: [Reactions.quieting]): off the map until it's over, then up again
         * as it was (companion/GAME.md "People react").
         */
        data class Danger(val kind: EventKind) : Why

        /** A drill whose task ([task], the request its [Quest.helps] names) is passed: practice now, no "!" ([spentFor]). */
        data class Spent(val task: Quest) : Why
    }

    /** A request waiting in "Later", and why. */
    data class Waiting(val quest: Quest, val why: Why)

    /** The open requests of who is here: [up] now (on the map), [later] in the Scroll; each villager's together, in order. */
    data class Queue(val up: List<Quest>, val later: List<Waiting>) {
        fun upOf(giver: String): List<Quest> = up.filter { it.giver == giver }
        fun laterOf(giver: String): List<Waiting> = later.filter { it.quest.giver == giver }

        /** Why request [id] waits; null when it is up (or not open). */
        fun why(id: String): Why? = later.firstOrNull { it.quest.id == id }?.why
    }

    /**
     * The requests [open] (the open ones of who is here, [si.lanisce.lani.game.villagers.Residents.openQuests]) as they
     * show [today]; [all] are the village's quests (the newest last: their order says which is newer, a drill passed brings
     * its task back, and a task passed leaves its drill for practice). [event]: the village's event now; wolves, a bear or a
     * storm ([Reactions.quieting]) fold the requests up away ([Why.Danger], first of each villager's in "Later"), the
     * merchant and a festival nothing.
     */
    fun of(all: List<Quest>, open: List<Quest>, today: LocalDate, event: EventKind? = null): Queue {
        val index = all.withIndex().associate { (i, q) -> q.id to i }
        val danger = event?.takeIf { Reactions.quieting(it) }
        val up = ArrayList<Quest>()
        val later = ArrayList<Waiting>()
        for (mine in open.filter { !it.done }.groupBy { it.giver }.values) {
            val parked = mine.mapNotNull { q -> drillFor(q, mine)?.let { Waiting(q, Why.After(it)) } }
            val spent = mine.filter { q -> parked.none { it.quest.id == q.id } }.mapNotNull { q -> spentFor(q, all)?.let { Waiting(q, Why.Spent(it)) } }
            val waiting = (parked + spent).map { it.quest.id }.toSet()
            val stale = mine.filter { it.id !in waiting && stale(it, today) }.map { Waiting(it, Why.Stale) }
            // their own in the order asked (the first expires first), the tutor's newest first
            val rest = mine.filter { q -> q.id !in waiting && stale.none { it.quest.id == q.id } }
                .sortedWith(compareBy<Quest> { rank(it, all) }.thenBy { (index[it.id] ?: 0).let { i -> if (it.source == QuestSource.LOCAL) i else -i } })
            if (danger == null) up += rest.take(UP) else later += rest.take(UP).map { Waiting(it, Why.Danger(danger)) }
            later += rest.drop(UP).map { Waiting(it, Why.Queued) }
            later += parked
            later += stale
            later += spent
        }
        return Queue(up, later)
    }

    /** Local first, then a tutor's task back from its drill, then the other tasks of the tutor's. */
    private fun rank(q: Quest, all: List<Quest>): Int = when {
        q.source == QuestSource.LOCAL -> 0
        returning(q, all) -> 1
        else -> 2
    }

    /** A tutor's task whose drill (at the same villager) was passed: it comes back first. */
    fun returning(q: Quest, all: List<Quest>): Boolean =
        q.source == QuestSource.TUTOR && q.moduleId != null &&
            all.any { d -> d.done && d.source == QuestSource.TUTOR && d.giver == q.giver && d.id != q.id && d.helps?.let { names(it, q) } == true }

    /**
     * The open drill of [others] that tutor task [q] waits behind: the tutor's, at the same villager, whose [Quest.helps] names
     * [q] ([names]: its module; two that name each other wait for neither); null when there is none.
     */
    fun drillFor(q: Quest, others: List<Quest>): Quest? {
        if (q.moduleId == null || q.source != QuestSource.TUTOR || q.done) return null
        return others.firstOrNull { d ->
            d.id != q.id && !d.done && d.source == QuestSource.TUTOR && d.giver == q.giver &&
                d.helps?.let { names(it, q) } == true && q.helps?.let { names(it, d) } != true
        }
    }

    /** The tutor's request ids: "tutor-<module>-v<version>" ([Quests.tutorId]). */
    private val TUTOR_ID = Regex("""tutor-(.+)-v\d+""")

    /**
     * Whether [helps] (a drill's [Quest.helps]: a module's id, as the tutor writes it, or a request's id) names request [q]:
     * its module, its id, or its module by the tutor's request id of another version ("tutor-kaksno-vino-v1" for v2).
     */
    fun names(helps: String, q: Quest): Boolean =
        q.moduleId == helps || q.id == helps || (q.moduleId != null && TUTOR_ID.matchEntire(helps)?.groupValues?.get(1) == q.moduleId)

    /**
     * The task drill [d] helps with ([Quest.helps]), when it is passed (a request of [all] it names done, none open): [d] is
     * practice now, in "Later" without a "!" and not one of the [UP] ("✓ Naloga je opravljena: za vajo"); null for any
     * other request, a drill whose task is open (it comes first, as ever) or one whose task the village doesn't have (not
     * published yet, or gone without a pass). Passed later all the same, [d] is done as any request.
     */
    fun spentFor(d: Quest, all: List<Quest>): Quest? {
        val task = d.helps ?: return null
        if (d.source != QuestSource.TUTOR || d.done) return null
        val named = all.filter { it.id != d.id && names(task, it) }
        if (named.any { !it.done }) return null
        return named.lastOrNull { it.done }
    }

    /**
     * Whether done request [q] is the task an open drill of [quests] helps with: it is kept (not pruned, not dropped with its
     * module) while that drill is open, so the drill stays practice ([spentFor]) and doesn't ask with a "!" again.
     */
    fun helpedByOpen(q: Quest, quests: List<Quest>): Boolean =
        q.done && quests.any { d -> d.id != q.id && !d.done && d.source == QuestSource.TUTOR && d.helps?.let { names(it, q) } == true }

    /**
     * A local request (not the tent's move, which waits for good) never started, asked [STALE_DAYS] days ago or more. One
     * asked before the app counted tries ([Quest.since] null) may have been started: it never waits by age.
     */
    fun stale(q: Quest, today: LocalDate): Boolean =
        q.source == QuestSource.LOCAL && !q.done && q.tries == 0 && q.id != TentMove.ID &&
            since(q)?.let { ChronoUnit.DAYS.between(it, today) >= STALE_DAYS } == true

    /** The day [q] was asked ([Quest.since]); null for one asked before the app kept it. */
    fun since(q: Quest): LocalDate? = q.since?.let { s -> runCatching { LocalDate.parse(s) }.getOrNull() }

    /**
     * Tried [HARD_TRIES] times or more without a pass, and no drill for it among [quests] (the village's): it offers "🎯 Vadi,
     * kar ti ne gre · Practise what's hard" (the rules it missed) and stays open.
     */
    fun hard(q: Quest, quests: List<Quest>): Boolean = !q.done && q.tries >= HARD_TRIES && drillFor(q, quests) == null

    /** The pass mark of [q]'s best try (8 of 12); 0 before the first. */
    fun passMark(q: Quest): Int = if (q.bestOf > 0) Quests.passMark(q.bestOf) else 0

    /**
     * [q] played: [correct] of [total], [missed] the pages its wrong answers named (most missed first). One try more; the best
     * try is the one with the most right of its questions (the latest of equals); the pages missed now first.
     */
    fun tried(q: Quest, correct: Int, total: Int, missed: List<String> = emptyList()): Quest {
        val better = q.bestOf == 0 || correct.toLong() * q.bestOf >= q.best.toLong() * total
        return q.copy(
            tries = q.tries + 1,
            best = if (better) correct else q.best,
            bestOf = if (better) total else q.bestOf,
            missed = (missed + q.missed).distinct().take(MISSED_MAX),
        )
    }

    /**
     * [quests] with [Quest.helps] found for the tutor's short drills that don't say it (an older bridge drops the field): a
     * drill ([short]: module ids tagged [SHORT]) for an older open task of the tutor's at the same giver, when a grammar page
     * names both modules ([pagesOf]: the pages of a module). Most shared pages first, then the newest task. A full module on a
     * rule another one touches (the dual after "imeti in iti") is no drill: only a short one is. A link found stays; [quests]
     * as they are when none is found.
     */
    fun link(quests: List<Quest>, pagesOf: (String) -> Set<String>, short: Set<String>): List<Quest> {
        var changed = false
        val out = quests.mapIndexed { i, d ->
            val module = d.moduleId
            if (d.source != QuestSource.TUTOR || d.done || d.helps != null || module == null || module !in short) return@mapIndexed d
            val pages = pagesOf(module)
            if (pages.isEmpty()) return@mapIndexed d
            val task = quests.take(i).withIndex().mapNotNull { (j, t) ->
                val other = t.moduleId
                if (other == null || other == module || t.source != QuestSource.TUTOR || t.done || t.giver != d.giver || t.helps == module) return@mapNotNull null
                val shared = (pagesOf(other) intersect pages).size
                if (shared == 0) null else Triple(j, t, shared)
            }.maxWithOrNull(compareBy<Triple<Int, Quest, Int>> { it.third }.thenBy { it.first })?.second
            if (task == null) d else d.copy(helps = task.moduleId).also { changed = true }
        }
        return if (changed) out else quests
    }
}
