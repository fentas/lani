package si.lanisce.lani.game

import si.lanisce.lani.data.Course
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Sheets
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import kotlin.random.Random

/**
 * The placement test ("🧭 Uvrstitveni test · Placement test"; companion/COURSE.md "The placement test", GAME.md "The
 * school", docs/plans/05-built/placement.md): a starter who knows some of the language begins the school at their level,
 * instead of at A1 testing out of one unit after another. Optional: offered at setup and in the school, never run on its
 * own, and nothing changes until the learner takes its result ([apply]), which only adds: units not done marked done,
 * levels opened, the level raised (never lowered). Pure.
 *
 * - **Parts.** Each level's written units, in the course's order, in [PARTS] parts (a level of fewer units: a part a unit):
 *   A1 1/3 … B2 3/3, twelve in a course of four written levels ([parts]).
 * - **A probe** of a part ([probe]): exercises of its units' own tests, each of another unit, the ones that ask the most
 *   in the least time first, another kind before the same (a table, a list or a sort cut short, [asked]), until it has its
 *   [Version.items] items in [Version.least] exercises at least, or its [Version.exercises]. It passes at [PASS] % of
 *   its items, an almost counting half, as a unit's test counts.
 * - **Adaptive** ([next], [record]): the first probe is just below the learner's level ([startAt]: A1's middle for an A1
 *   learner, A2's end for a B1 one), and after the parts whose units are all done already ([Test.known]). Passed, it
 *   climbs two parts, then four, then eight; once a part fails, it halves the gap between the highest part passed and the
 *   lowest failed until they meet. It ends there, after [Version.probes] probes, when the next probe wouldn't fit the
 *   time left ([Version.seconds], measured), or when the learner stops. Every part up to the highest passed counts as known.
 * - **The result** ([result]): the units of the parts known that aren't done yet, to mark done (tested out, still open to
 *   do again); the levels up to the one the learner starts in, to open; the level suggested: the first part not known's
 *   (B2 at most, the course's last).
 * - **A child's version** ([Version.CHILD]: Teo, 7): tap exercises only (a choice, a multi, a sort, word chips, a gap text
 *   with its word bank), A1 to B1, shorter probes and fewer of them (ten minutes at most).
 */
object LevelPlacement {
    /** The parts of a level. */
    const val PARTS = 3

    /** A probe passes at this share of its items (percent; an almost counts half). */
    const val PASS = 75

    /** A probe's time over its estimate that counts at most ([record]): a pause doesn't use up the test. */
    private const val SLOW = 2

    /**
     * Who takes it: [items] a probe asks at least, in [least] exercises at least (as many units) and [exercises] at most;
     * at most [probes] probes in [seconds]; the [levels] it probes.
     */
    enum class Version(val items: Int, val least: Int, val exercises: Int, val probes: Int, val seconds: Int, val levels: List<String>) {
        ADULT(items = 8, least = 2, exercises = 3, probes = 6, seconds = 15 * 60, levels = School.LEVELS),
        CHILD(items = 4, least = 3, exercises = 4, probes = 4, seconds = 10 * 60, levels = listOf("A1", "A2", "B1")),
    }

    /** A part of the course: [level]'s [part] of [parts] ([units], in the course's order), [index] in the course's [parts]. */
    data class Part(val index: Int, val level: String, val part: Int, val parts: Int, val units: List<String>) {
        /** "A2 2/3"; a level of one part "A2". */
        val label: String get() = if (parts > 1) "$level $part/$parts" else level
    }

    /** An exercise a probe asks: unit [unit]'s test exercise [index], as asked ([exercise]: cut short, [asked]). */
    data class Item(val unit: String, val index: Int, val exercise: Exercise) {
        val key: String get() = "$unit/$index"
        val items: Int get() = count(exercise)
    }

    /** A probe of part [part]: its [items], and once answered their [verdicts] (each item's, the paid one) and its [seconds]. */
    data class Probe(val part: Int, val items: List<Item>, val verdicts: List<Verdict>? = null, val seconds: Int = 0) {
        val answered: Boolean get() = verdicts != null
        val passed: Boolean get() = verdicts?.let { Sheets.passed(it, PASS) } == true
    }

    /**
     * A placement test under way, in [language] for [version]: the learner's level as it began ([from]), every part of the
     * course ([parts]; it probes the first [asked]: the version's levels), the last part whose units and every one's
     * before are done already ([known]: -1 none), the part it begins with ([first]); the highest part passed ([lo]: [known]
     * at first) and the lowest failed ([hi]: [asked] at first); the [probes] so far, the [seconds] they took; [stopped]:
     * the learner stopped it.
     */
    data class Test(
        val language: String,
        val version: Version,
        val from: String,
        val parts: List<Part>,
        val asked: Int,
        val known: Int,
        val first: Int,
        val lo: Int,
        val hi: Int,
        val probes: List<Probe> = emptyList(),
        val seconds: Int = 0,
        val stopped: Boolean = false,
    ) {
        /** A part failed: it halves the gap from now on. */
        val failed: Boolean get() = hi < asked
        val verdicts: List<Verdict> get() = probes.flatMap { it.verdicts.orEmpty() }
    }

    /** The course's parts: each written level's units in [PARTS] parts as even as can be (the first ones smaller), in order. */
    fun parts(course: Course): List<Part> {
        var i = 0
        return School.LEVELS.flatMap { level ->
            val ids = course.playable(level).map { it.id }
            val n = minOf(PARTS, ids.size)
            (0 until n).map { k -> Part(i++, level, k + 1, n, ids.subList(k * ids.size / n, (k + 1) * ids.size / n)) }
        }
    }

    /** The items of [e]: a gap, a cell, a line, a word sorted each; one for any other exercise. */
    fun count(e: Exercise): Int = Sheets.items(e).size.coerceAtLeast(1)

    /**
     * [e] as a probe of [version] asks it, or null when it doesn't: never what needs a sound, a microphone or the tutor;
     * an adult's table cut to its first rows (at most 6 cells), a list to its first 4 lines, a sort to 6 words (a column at
     * a time), a gap text of 6 gaps at most; a child's only by tap: a choice, a multi, a sort of 6 words, word chips of 6,
     * a gap text of 3 gaps with its word bank.
     */
    fun asked(e: Exercise, version: Version): Exercise? = when (version) {
        Version.ADULT -> when (e) {
            is Exercise.Choice -> e.takeIf { it.audio == null }
            is Exercise.Multi -> e
            is Exercise.Reorder -> e.takeIf { it.audio == null }
            is Exercise.Cloze -> e.takeIf { it.audio == null }
            is Exercise.GapText -> e.takeIf { it.gaps.size in 1..6 }
            is Exercise.Table -> cut(e, 6)
            is Exercise.Transform -> e.takeIf { it.items.isNotEmpty() }?.copy(items = e.items.take(4))
            is Exercise.Sort -> cut(e, 6)
            else -> null
        }
        Version.CHILD -> when (e) {
            is Exercise.Choice -> e.takeIf { it.audio == null }
            is Exercise.Multi -> e
            is Exercise.Reorder -> e.takeIf { it.audio == null && it.tokens.size + it.distractors.size <= 6 }
            is Exercise.GapText -> e.takeIf { it.bank && it.gaps.size in 1..3 }
            is Exercise.Sort -> e.takeIf { words(it) in 1..6 }
            else -> null
        }
    }

    private fun words(s: Exercise.Sort): Int = s.columns.sumOf { it.words.size }

    /** [s] with at most [most] of its words, taken a column at a time (each column keeps its first); null with none. */
    private fun cut(s: Exercise.Sort, most: Int): Exercise.Sort? {
        if (words(s) <= most) return s.takeIf { words(it) > 0 }
        val kept = s.columns.map { mutableListOf<String>() }
        var n = 0
        var i = 0
        while (n < most && kept.indices.any { kept[it].size < s.columns[it].words.size }) {
            val c = i++ % s.columns.size
            s.columns[c].words.getOrNull(kept[c].size)?.let { kept[c] += it; n++ }
        }
        return s.copy(columns = s.columns.mapIndexed { c, col -> col.copy(words = kept[c]) }.filter { it.words.isNotEmpty() })
            .takeIf { it.columns.size >= 2 }
    }

    /** [t] with its first rows that have at most [most] cells to fill between them (at least its first row); null with none. */
    private fun cut(t: Exercise.Table, most: Int): Exercise.Table? {
        val rows = mutableListOf<si.lanisce.lani.data.TableRow>()
        var n = 0
        for (r in t.rows) {
            val f = r.cells.count { it.toFill }
            if (rows.isNotEmpty() && n + f > most) break
            rows += r
            n += f
        }
        return t.copy(rows = rows).takeIf { n > 0 }
    }

    /**
     * The seconds [e] takes, about (for the order of a probe's exercises and its time): a choice 15, a gap 15, a table's
     * cell 12, a list's line 25, a word sorted 6 …, and 10 to read its feedback; half as long again for a child.
     */
    fun seconds(e: Exercise, version: Version): Int {
        val answer = when (e) {
            is Exercise.Choice -> 15
            is Exercise.Multi -> 25
            is Exercise.Reorder -> 30
            is Exercise.Cloze -> 20
            is Exercise.GapText -> 10 + 15 * e.gaps.size
            is Exercise.Table -> 12 * count(e)
            is Exercise.Transform -> 25 * e.items.size
            is Exercise.Sort -> 6 * words(e)
            else -> 30
        }
        val all = answer + 10
        return if (version == Version.CHILD) all * 3 / 2 else all
    }

    /**
     * The part the test begins with for a learner at [level] ([Introduction.rank]): A1's second part (its first when it has
     * one), or from A2 on the last part of the level below (B1 → A2's last: a B1 learner knows A2); a level above the
     * course's, its last part. Within the first [asked] parts.
     */
    fun startAt(parts: List<Part>, level: String, asked: Int = parts.size): Int {
        if (parts.isEmpty() || asked <= 0) return 0
        val r = Introduction.rank(level) ?: 0
        val at = if (r == 0) parts.indexOfFirst { it.level == School.LEVELS[0] && it.part == 2 }.takeIf { it >= 0 } ?: 0
        else {
            val below = School.LEVELS[minOf(r, School.LEVELS.size) - 1]
            parts.indexOfLast { it.level == below }.takeIf { it >= 0 } ?: parts.indexOfLast { (School.LEVELS.indexOf(it.level)) < r }.coerceAtLeast(0)
        }
        return at.coerceIn(0, asked - 1)
    }

    /**
     * A test of [course] for a learner at [level] ([version]: a child's or not), whose village is [s] (its units done: the
     * parts done count as known, and the test begins after them).
     */
    fun start(course: Course, s: GameState?, level: String, version: Version): Test {
        val parts = parts(course)
        val asked = parts.count { it.level in version.levels }
        val done = { id: String -> s?.let { School.progress(it, course.language, id)?.done } != null }
        val open = parts.indexOfFirst { p -> !p.units.all(done) }
        val known = if (open < 0) parts.size - 1 else open - 1
        val first = maxOf(startAt(parts, level, asked), known + 1)
        return Test(course.language, version, LevelReadiness.norm(level), parts, asked, known, first, lo = known, hi = maxOf(asked, known + 1))
    }

    /** The part to probe next; null once the test is done (the parts passed and failed meet, its probes or its time spent, stopped). */
    fun next(t: Test): Int? {
        if (t.stopped || t.hi - t.lo <= 1 || t.probes.size >= t.version.probes) return null
        // the next probe would likely run over the time: the probes so far's pace
        if (t.probes.isNotEmpty() && t.seconds + t.seconds / t.probes.size > t.version.seconds) return null
        if (t.probes.isEmpty()) return t.first.coerceIn(t.lo + 1, t.hi - 1)
        // climbing while nothing failed: two parts, then four, eight …; then halving the gap
        return if (!t.failed) minOf(t.lo + (1 shl t.probes.count { it.passed }), t.hi - 1) else (t.lo + t.hi) / 2
    }

    /**
     * A probe of part [part] for [t] ([random]: which units and exercises): one exercise of each of its units (shuffled),
     * not one asked before in the test, each the one that asks the most of what the probe still needs in the least time
     * (an exercise of a kind the probe hasn't got yet counting a quarter more), until it has its items ([Version.items])
     * in its least exercises ([Version.least]), or its most; another round over its units when it is still short. Empty
     * when the part has nothing this version asks.
     */
    fun probe(course: Course, t: Test, part: Int, random: Random): Probe {
        val p = t.parts[part]
        val v = t.version
        val used = t.probes.flatMap { pr -> pr.items.map { it.key } }.toMutableSet()
        val picked = mutableListOf<Item>()
        val units = p.units.shuffled(random)
        fun full() = picked.size >= v.exercises || (picked.size >= v.least && picked.sumOf { it.items } >= v.items)
        for (round in 0..1) {
            for (u in units) {
                if (full()) break
                if (round == 0 && picked.any { it.unit == u }) continue
                val options = course.units[u]?.test?.exercises.orEmpty()
                    .mapIndexedNotNull { i, e -> asked(e, v)?.let { Item(u, i, it) } }
                    .filter { it.key !in used }
                if (options.isEmpty()) continue
                val kinds = picked.map { it.exercise::class }.toSet()
                val need = (v.items - picked.sumOf { it.items }).coerceAtLeast(1)
                val best = options.shuffled(random).maxBy { o ->
                    minOf(o.items, need).toDouble() / seconds(o.exercise, v) * (if (o.exercise::class in kinds) 1.0 else 1.25)
                }
                picked += best
                used += best.key
            }
        }
        return Probe(part, picked)
    }

    /**
     * [probe] answered ([verdicts]: each item's, as paid) in [seconds] ([complete]: to its end; else the learner stopped
     * the test, and the probe decides nothing). Passed, the part is known; failed, every part from it on isn't.
     */
    fun record(t: Test, probe: Probe, verdicts: List<Verdict>, seconds: Int, complete: Boolean = true): Test {
        val most = probe.items.sumOf { seconds(it.exercise, t.version) } * SLOW
        val took = seconds.coerceIn(0, most.coerceAtLeast(1))
        val p = probe.copy(verdicts = verdicts, seconds = took)
        val n = t.copy(probes = t.probes + p, seconds = t.seconds + took)
        return when {
            !complete -> n.copy(stopped = true)
            // a part with nothing to ask can't be known: the test ends below it
            probe.items.isEmpty() -> n.copy(hi = minOf(t.hi, probe.part))
            p.passed -> n.copy(lo = maxOf(t.lo, probe.part))
            else -> n.copy(hi = minOf(t.hi, probe.part))
        }
    }

    /** The learner stopped the test ("Done" on a probe): what is known so far stands. */
    fun stop(t: Test): Test = t.copy(stopped = true)

    /**
     * What the test found ([Test.lo]: the last part known): [start], the first part not known (where the school goes on;
     * null past the course's last); [level], its level (the course's last past it); [units], the units of the parts known
     * not done yet in [s]; [opens], the levels up to [level] (none when no part is known); the items answered.
     */
    data class Result(
        val known: Int,
        val start: Part?,
        val level: String,
        val units: List<String>,
        val opens: List<String>,
        val right: Int,
        val almost: Int,
        val of: Int,
    ) {
        /** Anything to take: units to mark, or a level the learner isn't at yet ([rises]). */
        fun changes(current: String): Boolean = units.isNotEmpty() || rises(current)

        /** The level suggested is above [current]. */
        fun rises(current: String): Boolean = (Introduction.rank(level) ?: 0) > (Introduction.rank(current) ?: 0)
    }

    fun result(t: Test, s: GameState?): Result {
        val known = t.lo.coerceIn(-1, t.parts.size - 1)
        val start = t.parts.getOrNull(known + 1)
        val level = start?.level ?: t.parts.lastOrNull()?.level ?: School.LEVELS.first()
        val done = { id: String -> s?.let { School.progress(it, t.language, id)?.done } != null }
        val units = t.parts.take(known + 1).flatMap { it.units }.filterNot(done)
        val reached = School.LEVELS.indexOf(level)
        val opens = if (known < 0) emptyList() else School.LEVELS.take(reached + 1).filter { l -> t.parts.any { it.level == l } }
        val v = t.verdicts
        return Result(known, start, level, units, opens, v.count { it == Verdict.CORRECT }, v.count { it == Verdict.ALMOST }, v.size)
    }

    /**
     * The learner takes [r] ("✓ Začnem tukaj · Start here") for the test [t] of [course], at level [current] before it:
     * each unit of [Result.units] done today, tested out and [UnitProgress.placed] (its intro counted as skipped, as "⏭
     * Poznam to" does; what it had kept), the levels of [Result.opens] opened, the result kept ([CourseProgress.placements]:
     * [report], the outbox entry that tells the node; confirmed at once when the level doesn't rise), a line in the
     * chronicle. A unit done already, a level open already, a sheet's or a test's best try: as they were. No unit's bonus:
     * that is a test passed's.
     */
    fun apply(s: GameState, course: Course, t: Test, r: Result, current: String, today: LocalDate, now: Long, report: String? = null): GameState {
        val c = s.course ?: CourseProgress()
        val day = today.toString()
        val k = { id: String -> School.key(course.language, id) }
        val marked = r.units.filter { c.units[k(it)]?.done == null }.associate { id ->
            val p = c.units[k(id)]
            k(id) to (p ?: UnitProgress()).copy(started = p?.started ?: day, introDone = true, done = day, testedOut = true, placed = true)
        }
        val opened = r.opens.map(k).filter { it !in c.opened }.associateWith { day }
        val rises = r.rises(current)
        val placed = Placed(
            day = day, from = LevelReadiness.norm(current), level = if (rises) r.level else LevelReadiness.norm(current),
            start = r.start?.level, part = r.start?.part ?: 0, parts = r.start?.parts ?: 0, units = marked.size,
            right = r.right, almost = r.almost, of = r.of, minutes = (t.seconds + 30) / 60, child = t.version == Version.CHILD,
            report = report, confirmed = !rises,
        )
        val n = s.copy(course = c.copy(units = c.units + marked, opened = c.opened + opened, placements = c.placements + (course.language to placed)))
        val line = when {
            r.start == null -> bi("placement.loggedAll", "n" to marked.size)
            else -> bi("placement.logged", "level" to r.start.label, "n" to marked.size)
        }
        return n.logged(now, "🧭" to line)
    }

    /**
     * The learner's level as the app goes by it: the node's ([node]), or the level the placement test raised it to
     * ([placed], the village's language's) while the node doesn't have it yet; never lower than the node's.
     */
    fun level(node: String, placed: Placed?): String {
        if (placed == null || placed.confirmed) return node
        return if ((Introduction.rank(placed.level) ?: 0) > (Introduction.rank(node) ?: 0)) placed.level else node
    }

    /** The node shows [node] for [language]: a placement's level it has (or passed) is confirmed, the node's goes again. */
    fun confirm(s: GameState, language: String, node: String): GameState {
        val c = s.course ?: return s
        val p = c.placements[language]?.takeIf { !it.confirmed } ?: return s
        if ((Introduction.rank(node) ?: 0) < (Introduction.rank(p.level) ?: 0)) return s
        return s.copy(course = c.copy(placements = c.placements + (language to p.copy(confirmed = true))))
    }

    /**
     * How the school offers the test ([CourseProgress.placements] and the units in [s]): [Offer.CARD], a card at the top,
     * to a learner who hasn't done a unit of [course] nor taken it nor [dismissed] the card; [Offer.ROW], a quiet row under the levels, to everyone
     * else (Jan's, Teo's school: offered, never pressed). None without a course with units.
     */
    fun offer(s: GameState?, course: Course?, dismissed: Boolean = false): Offer {
        if (course == null || parts(course).isEmpty()) return Offer.NONE
        if (dismissed) return Offer.ROW
        val c = s?.course
        val begun = c?.units?.any { (key, p) -> key.startsWith("${course.language}/") && (p.done != null || p.started != null) } == true
        return if (begun || c?.placements?.containsKey(course.language) == true) Offer.ROW else Offer.CARD
    }

    enum class Offer { NONE, CARD, ROW }
}
