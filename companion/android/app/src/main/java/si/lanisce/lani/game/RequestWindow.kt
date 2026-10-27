package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Villager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * How the village's requests come and go on the map (companion/GAME.md "Quests", "A window of requests"), kept with the
 * village: the requests the window shows now ([up]: they stay until done, for the day they came up), who rests after a
 * run of theirs ([rests]), the requests tried below the pass mark and when each comes back ([back]), and the learner's
 * roll ([roll] requests finished, the last at [rollAt]), which lets the window grow. An older app drops it (the window
 * starts afresh), an older bridge keeps it as it is.
 */
@Serializable
data class RequestPace(
    val up: List<RequestUp> = emptyList(),
    /** By giver ([Quest.giver]): when their last run ended, and until when they ask nothing. */
    val rests: Map<String, RequestRest> = emptyMap(),
    /** By request id: when a request tried below the pass mark comes back (epoch ms). */
    val back: Map<String, Long> = emptyMap(),
    val roll: Int = 0,
    val rollAt: Long = 0,
)

/** A request the map's window shows, since [at] (epoch ms). */
@Serializable
data class RequestUp(val id: String, val at: Long)

/** A giver after a run of theirs: it ended [at], [passed] or not; they ask nothing [until] (epoch ms). */
@Serializable
data class RequestRest(val at: Long, val until: Long, val passed: Boolean = true)

/**
 * What the learner has to practise now, from the review data on the phone ([Dashboard]): the cards due and the rusty
 * ones ([cards], [rusty]), the mistakes' patterns ([topics]: the mistakes database's weak patterns), and the tutor's modules
 * that target one of them ([modules]). A request that practises any of it ranks high ([RequestWindow.order]).
 */
data class Practice(
    val cards: Set<String> = emptySet(),
    val rusty: Set<String> = emptySet(),
    val topics: Set<String> = emptySet(),
    val modules: Set<String> = emptySet(),
) {
    companion object {
        val NONE = Practice()

        /** From the dashboard (null before it came) and the module list: their targets against the weak patterns. */
        fun of(d: Dashboard?, modules: List<ModuleInfo>, today: LocalDate = LocalDate.now()): Practice {
            if (d == null) return NONE
            val topics = d.weakPatterns.map(::norm).filter { it.isNotEmpty() }.toSet()
            val due = d.dueCards.filter { c -> c.due?.let { !it.isAfter(today) } ?: true }.map { it.id }
            val rusty = d.rusty.map { it.id }.toSet()
            return Practice(
                cards = (due + rusty).toSet(),
                rusty = rusty,
                topics = topics,
                modules = modules.filter { m -> m.targets.any { t -> norm(t) in topics } }.map { it.id }.toSet(),
            )
        }

        /** "Clitic placement", "clitic_placement" and "clitic-placement" alike. */
        fun norm(s: String): String = s.trim().lowercase().replace(Regex("[\\s_-]+"), "_")
    }
}

/**
 * What the next age still needs that a request can bring ([RequestWindow.order]'s first tier): the resources short of its
 * cost ([res]: how much each; a request whose reward covers what is short completes that step), the givers whose pass makes
 * them a friend when it needs friends ([friends]), and whether rusty words hold it back ([rusty]: a request with one of them
 * polishes it).
 */
data class AgeNeeds(val res: Map<Res, Int> = emptyMap(), val friends: Set<String> = emptySet(), val rusty: Boolean = false) {
    companion object {
        val NONE = AgeNeeds()

        /** For [state] ([cast]: who the givers are, for their friendship), with [practice]'s rusty words. */
        fun of(state: GameState, cast: List<Villager>, practice: Practice = Practice.NONE): AgeNeeds {
            val next = Age.entries.getOrNull(state.age.ordinal + 1) ?: return NONE
            val rule = Catalog.ageRules[next] ?: return NONE
            val res = rule.cost.mapNotNull { (r, n) -> (n - state.res(r)).takeIf { it > 0 }?.let { r to it } }.toMap()
            val friend = Bonds.THRESHOLDS[Bonds.FRIEND]
            val friends = if (rule.friends <= Bonds.friends(state)) emptySet() else cast.filter { v ->
                val p = state.bonds[v.id]?.points ?: 0
                p < friend && p + Bonds.QUEST >= friend
            }.map { it.name }.toSet()
            return AgeNeeds(res, friends, rusty = practice.rusty.isNotEmpty())
        }
    }
}

/**
 * The map's window of requests (companion/GAME.md "Quests", "A window of requests"): a few at a time, one per villager,
 * the best for the learner first; each one cleared lets the next come up; a roll of them lets the window grow, a long
 * while untouched brings it back. Pure, but for the app's settings of the moment ([base], [practice], [near]).
 */
object RequestWindow {
    /** The window's start on a phone, and on a tablet (the screen's shorter side 600 dp or more). */
    const val PHONE = 3
    const val TABLET = 4

    /** At most this many more while the learner is on a roll. */
    const val GROW_MAX = 3

    /** No request finished for this long: the roll is over, the window back at its start. */
    const val ROLL_GAP_MS = 4 * 3600_000L

    /** A giver rests at least this long after a run of theirs, and into the next part of the day. */
    const val REST_MS = 3 * 3600_000L

    /** A request tried below the pass mark comes back the next day, from this hour (the morning's start, [si.lanisce.lani.game.scene.TimeOfDay]). */
    const val BACK_HOUR = 5

    /** The parts of the day start at these hours (morning, afternoon, evening, night: [si.lanisce.lani.game.scene.TimeOfDay.of]). */
    val PARTS = listOf(5, 11, 17, 22)

    /** After a run, this long the giver's thanks shows on the map when the learner comes back to it. */
    const val THANKS_MS = 10 * 60_000L

    /** The window's start on this device ([PHONE] or [TABLET]; the app sets it from the screen: [baseFor]). */
    @Volatile var base: Int = PHONE

    /** What the learner has to practise now (the app sets it: the dashboard's due and rusty cards, the weak patterns). */
    @Volatile var practice: () -> Practice = { Practice.NONE }

    /**
     * How near the map's view centre a request's giver is (screen px; null: not known), for a tie between two of the same
     * rank; the map sets it as its camera comes to rest.
     */
    @Volatile var near: ((Quest) -> Float?)? = null

    /**
     * The window as the app has it for [state] on [today]: now (the clock's time when [today] is today, else its noon), the
     * village's pace, this device's [base] grown by the roll ([size]), what the next age needs, what the learner has to
     * practise ([practice]) and the map's view ([near]).
     */
    fun window(state: GameState, cast: List<Villager>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): QuestQueue.Window {
        val now = clock(today, zone)
        val p = practice()
        return QuestQueue.Window(now, state.pace, size(base, state.pace, now), AgeNeeds.of(state, cast, p), p, near, zone)
    }

    /** The time of [today] to judge rests by: now when it is today (the phone's clock), else its noon. */
    fun clock(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = System.currentTimeMillis()
        return if (Instant.ofEpochMilli(now).atZone(zone).toLocalDate() == today) now else today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }

    /** [PHONE], or [TABLET] for a screen whose shorter side is 600 dp or more. */
    fun baseFor(smallestWidthDp: Int): Int = if (smallestWidthDp >= 600) TABLET else PHONE

    /**
     * The window's size at [now]: [base], one more each time the learner cleared as many as it showed in their roll (3,
     * then 4, then 5 on a phone), at most [GROW_MAX] more; back at [base] once no request was finished for [ROLL_GAP_MS].
     */
    fun size(base: Int, pace: RequestPace, now: Long): Int {
        if (pace.roll <= 0 || now - pace.rollAt > ROLL_GAP_MS) return base
        var size = base
        var left = pace.roll
        while (size < base + GROW_MAX && left >= size) {
            left -= size
            size++
        }
        return size
    }

    /** Until when a giver rests after a run that ended [at]: [REST_MS], and at least into the next part of the day. */
    fun restUntil(at: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val next = PARTS.map { h -> t.toLocalDate().atTime(h, 0).atZone(zone) }.firstOrNull { it.isAfter(t) }
            ?: t.toLocalDate().plusDays(1).atTime(PARTS.first(), 0).atZone(zone)
        return maxOf(at + REST_MS, next.toInstant().toEpochMilli())
    }

    /** When a request tried below the pass mark at [at] comes back: the next day's morning ([BACK_HOUR]). */
    fun backAt(at: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.of(Instant.ofEpochMilli(at).atZone(zone).toLocalDate().plusDays(1), LocalTime.of(BACK_HOUR, 0), zone).toInstant().toEpochMilli()

    /**
     * Request [q]'s run ended at [now], [passed] or not: it leaves the window (passed it is done; below the mark it comes back
     * the next day, [backAt]); its giver thanks and rests ([restUntil]); the roll counts it. What was paid and the try itself
     * are [GameEngine.completeQuest]'s.
     */
    fun finished(state: GameState, q: Quest, passed: Boolean, now: Long, zone: ZoneId = ZoneId.systemDefault()): GameState {
        val p = state.pace
        val open = state.quests.filter { !it.done }.map { it.id }.toSet()
        val pace = RequestPace(
            up = p.up.filter { it.id != q.id },
            rests = p.rests.filterValues { it.until > now } + (q.giver to RequestRest(now, restUntil(now, zone), passed)),
            back = p.back.filter { (id, at) -> at > now && id in open && id != q.id } + (if (passed) emptyMap() else mapOf(q.id to backAt(now, zone))),
            roll = if (p.roll > 0 && now - p.rollAt <= ROLL_GAP_MS) p.roll + 1 else 1,
            rollAt = now,
        )
        return state.copy(pace = pace)
    }

    /**
     * The window as [queue] shows it at [now], kept with the village: the requests up stay up while they're open (for the
     * day they came up), whatever comes later; nothing else changes. [state] as it is when nothing changed.
     */
    fun settle(state: GameState, queue: QuestQueue.Queue, now: Long, zone: ZoneId = ZoneId.systemDefault()): GameState {
        // wolves, a bear or a storm about: what was up comes back as it was once it's over
        if (queue.later.any { it.why is QuestQueue.Why.Danger }) return state
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val was = state.pace.up.associateBy { it.id }
        val up = queue.up.map { q -> was[q.id]?.takeIf { day(it.at, zone) == today } ?: RequestUp(q.id, now) }
        // a rest or a request's way back over: forgotten
        val rests = state.pace.rests.filterValues { it.until > now || now - it.at < THANKS_MS }
        val open = state.quests.filter { !it.done }.map { it.id }.toSet()
        val back = state.pace.back.filter { (id, at) -> at > now && id in open }
        val pace = state.pace.copy(up = up, rests = rests, back = back)
        return if (pace == state.pace) state else state.copy(pace = pace)
    }

    /** The requests the window keeps up at [now]: the ones it showed today ([RequestPace.up]). */
    fun pinned(pace: RequestPace, now: Long, zone: ZoneId = ZoneId.systemDefault()): Set<String> {
        val today = day(now, zone)
        return pace.up.filter { day(it.at, zone) == today }.map { it.id }.toSet()
    }

    private fun day(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** The givers who thank the learner now: a run of theirs ended less than [THANKS_MS] ago (passed or not). */
    fun thanking(pace: RequestPace, now: Long): Map<String, RequestRest> = pace.rests.filterValues { now - it.at in 0 until THANKS_MS }

    // --- the order -------------------------------------------------------------------------------------------------

    /** The window's tiers, best first (companion/GAME.md "Quests", "A window of requests"). */
    enum class Tier {
        /** What the next age needs: it completes a step (a resource's last bit, a new friend it needs) or polishes a rusty word. */
        AGE,
        /** It practises the learner's weak or due items: a drill of the tutor's, a task back from its drill, due or rusty cards, a weak pattern. */
        WEAK,
        /** Started and almost passed: the closest to its pass mark first. */
        CLOSE,
        /** The tutor's newest task. */
        TUTOR,
        /** The oldest of the villagers' own. */
        LOCAL,
    }

    /** Where [q] stands: its [tier] and its [score] within it (higher first). */
    data class Rank(val tier: Tier, val score: Double)

    /** [q]'s rank among [all] (the village's quests, the newest last) for [needs] and [practice]. */
    fun rank(q: Quest, all: List<Quest>, needs: AgeNeeds, practice: Practice): Rank {
        val index = all.indexOfFirst { it.id == q.id }.let { if (it < 0) all.size else it }
        // a step of the next age it completes: a resource's last bit, the friend it needs, a rusty word polished
        val completes = q.reward.count { (r, n) -> needs.res[r]?.let { n >= it } == true }
        val rustyHits = if (needs.rusty) q.cardIds.count { it in practice.rusty } else 0
        val friend = q.giver in needs.friends
        if (completes > 0 || friend || rustyHits > 0) return Rank(Tier.AGE, completes * STEP_SCORE + (if (friend) STEP_SCORE else 0.0) + rustyHits)
        val drill = q.source == QuestSource.TUTOR && q.helps != null
        val weak = (if (drill || QuestQueue.returning(q, all)) DRILL_SCORE else 0.0) +
            q.cardIds.count { it in practice.cards } +
            (if (q.moduleId != null && q.moduleId in practice.modules) PATTERN_SCORE else 0.0) +
            (if (q.category != null && Practice.norm(q.category) in practice.topics) PATTERN_SCORE else 0.0)
        if (weak > 0) return Rank(Tier.WEAK, weak)
        if (q.tries > 0 && q.bestOf > 0) return Rank(Tier.CLOSE, q.best.toDouble() / Quests.passMark(q.bestOf))
        return if (q.source == QuestSource.TUTOR) Rank(Tier.TUTOR, index.toDouble()) else Rank(Tier.LOCAL, -index.toDouble())
    }

    private const val STEP_SCORE = 100.0
    private const val DRILL_SCORE = 5.0
    private const val PATTERN_SCORE = 2.0

    /**
     * The order of [all]'s requests for the window, best first: by [rank] (its tier, then its score), a tie by the giver
     * nearer the map's view centre ([near]), then the one asked first.
     */
    fun order(all: List<Quest>, needs: AgeNeeds, practice: Practice, near: ((Quest) -> Float?)? = null): Comparator<Quest> {
        val ranks = HashMap<String, Rank>()
        val index = all.withIndex().associate { (i, q) -> q.id to i }
        fun r(q: Quest) = ranks.getOrPut(q.id) { rank(q, all, needs, practice) }
        return compareBy<Quest> { r(it).tier.ordinal }
            .thenByDescending { r(it).score }
            .thenBy { near?.invoke(it) ?: Float.MAX_VALUE }
            .thenBy { index[it.id] ?: Int.MAX_VALUE }
    }
}
