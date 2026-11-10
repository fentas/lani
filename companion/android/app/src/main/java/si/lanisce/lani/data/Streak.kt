package si.lanisce.lani.data

import java.time.LocalDate

/**
 * The streak on a day ([Streak.of]): [current] the practised days of the run going on (0 when none is: it ended, or
 * none began), [best] the longest run (the current one included), [practisedToday], [freezes] held now, [covered] every
 * day a freeze covered (oldest first), [since] the current run's first practised day, [todayCovered] a miss today would
 * be covered (a run going on, today open, a freeze held), [lastRun] the run that ended last (0 when none did),
 * [toNextFreeze] the practised days still to go for the next freeze (0 while the most are held).
 */
data class Streaks(
    val current: Int,
    val best: Int,
    val practisedToday: Boolean,
    val freezes: Int = Streak.START_FREEZES,
    val covered: List<LocalDate> = emptyList(),
    val since: LocalDate? = null,
    val todayCovered: Boolean = false,
    val lastRun: Int = 0,
    val toNextFreeze: Int = Streak.EARN_EVERY,
) {
    private val coveredDays: Set<LocalDate> by lazy { covered.toSet() }

    /** A freeze covered [day]: the week's dots and the calendar show it as a freeze, not as practice. */
    fun isCovered(day: LocalDate): Boolean = day in coveredDays

    /**
     * The morning after ([today]): the days of the gap still open that a freeze covered, oldest first. Empty once today
     * is practised, or when the run is over: the line that says a freeze kept the streak shows that day only.
     */
    fun keptToday(today: LocalDate): List<LocalDate> {
        if (practisedToday || current == 0) return emptyList()
        return generateSequence(today.minusDays(1)) { it.minusDays(1) }.takeWhile { it in coveredDays }.toList().reversed()
    }

    companion object {
        val NONE = Streaks(0, 0, false)
    }
}

/**
 * The streak: one number, derived from the practice days, with freezes (companion/GAME.md, "The streak"). Never the
 * stored counter (learner_profile.current_streak_days, which only a session's end writes): the morning after a missed
 * day every screen says the same as the tutor. The bridge's src/streak.ts and the scripts' .claude/hooks/lani_streak.py
 * are its twins; companion/bridge/test/fixtures/streak-cases.json keeps the three equal (StreakParityTest).
 *
 * The rule: a run counts its practised days; today is open, never a miss. Each learner starts with 1 freeze, every 7th
 * practised day of a run earns 1 more, at most 2 held. A day with no practice, before today, is covered by a held
 * freeze, spent by itself: it keeps the run and adds nothing to its count. A gap longer than the freezes held ends the
 * run and spends none. Nothing new is stored, so the rule holds for every past gap too; the longest streak is the
 * longest run under it.
 */
object Streak {
    const val START_FREEZES = 1
    const val EARN_EVERY = 7
    const val MAX_FREEZES = 2

    // date_of's forms (Python's strptime "%Y-%m-%d"): "2026-04-24", "2026-4-24"; the year's digits any decimal digits
    private val DATE = Regex("""(\p{Nd}{4})-(1[0-2]|0[1-9]|[1-9])-(3[01]|[12]\p{Nd}|0[1-9]|[1-9]| [1-9])""")

    private fun dateOf(s: String): LocalDate? {
        val m = DATE.matchEntire(s) ?: return null
        val (y, mo, d) = m.destructured
        return runCatching { LocalDate.of(y.toInt(), mo.trim().toInt(), d.trim().toInt()) }.getOrNull()?.takeIf { it.year >= 1 }
    }

    /** A session's day as the streak counts it: its date, or the date a timestamp starts with ("2026-11-08T21:30"); else null. */
    fun dayOf(v: String?): LocalDate? = v?.let { dateOf(it) ?: dateOf(it.take(10)) }

    /** The streak of the session log's dates [dates], as they are (null for a session without one), on [today]. */
    fun ofDates(dates: List<String?>, today: LocalDate): Streaks = of(dates.mapNotNull(::dayOf), today)

    /** The streak of the practice days [days] on [today]. */
    fun of(days: Collection<LocalDate>, today: LocalDate): Streaks {
        val sorted = days.filter { !it.isAfter(today) }.toSortedSet()
        var freezes = START_FREEZES
        var run = 0
        var best = 0
        var lastRun = 0
        val covered = ArrayList<LocalDate>()
        var since: LocalDate? = null
        var prev: LocalDate? = null

        // [missed] days without practice after [start]: covered while freezes last, else the run ends
        fun gap(start: LocalDate, missed: Long) {
            if (missed <= 0) return
            if (missed <= freezes) {
                freezes -= missed.toInt()
                for (k in 1..missed) covered += start.plusDays(k)
            } else {
                lastRun = run
                run = 0
                since = null
            }
        }

        for (day in sorted) {
            prev?.let { gap(it, day.toEpochDay() - it.toEpochDay() - 1) }
            run++
            if (since == null) since = day
            if (run % EARN_EVERY == 0 && freezes < MAX_FREEZES) freezes++
            best = maxOf(best, run)
            prev = day
        }
        val practisedToday = prev == today
        prev?.takeIf { !practisedToday }?.let { gap(it, today.toEpochDay() - it.toEpochDay() - 1) } // the gap still open
        return Streaks(
            current = run,
            best = best,
            practisedToday = practisedToday,
            freezes = freezes,
            covered = covered,
            since = since,
            todayCovered = run > 0 && !practisedToday && freezes >= 1,
            lastRun = lastRun,
            toNextFreeze = if (freezes >= MAX_FREEZES) 0 else EARN_EVERY - run % EARN_EVERY,
        )
    }
}
