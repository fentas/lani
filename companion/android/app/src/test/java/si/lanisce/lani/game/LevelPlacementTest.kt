package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Course
import si.lanisce.lani.data.Courses
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.LevelPlacement.Version
import java.time.LocalDate
import kotlin.random.Random

/**
 * The placement test (game/LevelPlacement; docs/plans/05-built/placement.md) on the four bundled courses: its parts, what
 * a probe asks (an adult's, a child's), the adaptive way through the parts for a simulated learner who knows the course up to
 * a part (never placed above it, found where it can be, in its time), and what taking the result does to a village: it only
 * adds (Jan's and Teo's progress as it was), never lowers the level, and is offered, never pressed.
 */
/** A probe as a line ("A2 3/3: 3 exercises, 8 items: table, gaptext"). */
private object PlacementReportProbe {
    fun line(t: LevelPlacement.Test, p: LevelPlacement.Probe): String =
        "${t.parts[p.part].label}: ${p.items.size} exercises, ${p.items.sumOf { it.items }} items: ${p.items.joinToString { it.exercise::class.simpleName.orEmpty().lowercase() }}"
}

class LevelPlacementTest {
    private val languages = listOf("sl", "it", "de", "en")
    private val courses: Map<String, Course> = languages.associateWith { checkNotNull(Courses.bundled(it)) { "the bundled course of $it" } }
    private val day = LocalDate.of(2026, 11, 3)
    private val now = 1_790_000_000_000L
    private val levels = listOf("A1", "A2", "B1", "B2", "C1")

    /**
     * The test played by a learner who knows every part up to [truth] (-1: none), each item right there and wrong above;
     * each probe taking its estimate. Returns the test at its end.
     */
    private fun play(course: Course, truth: Int, level: String, version: Version, s: GameState? = null, seed: Int = 1): LevelPlacement.Test {
        var t = LevelPlacement.start(course, s, level, version)
        val r = Random(seed)
        while (true) {
            val part = LevelPlacement.next(t) ?: return t
            val p = LevelPlacement.probe(course, t, part, r)
            val v = p.items.flatMap { List(it.items) { if (part <= truth) Verdict.CORRECT else Verdict.WRONG } }
            t = LevelPlacement.record(t, p, v, p.items.sumOf { LevelPlacement.seconds(it.exercise, version) })
        }
    }

    // --- the parts ----------------------------------------------------------------------------------------------------

    @Test fun `every course has three parts a level, each unit in one, in the course's order`() {
        for ((lang, c) in courses) {
            val parts = LevelPlacement.parts(c)
            assertEquals("$lang: four levels of three parts", 12, parts.size)
            assertEquals(lang, School.LEVELS.flatMap { l -> c.playable(l).map { it.id } }, parts.flatMap { it.units })
            for (p in parts) assertTrue("$lang ${p.label}: at least four units", p.units.size >= 4)
            assertEquals(listOf("A1 1/3", "A1 2/3", "A1 3/3", "A2 1/3"), parts.take(4).map { it.label })
        }
    }

    @Test fun `every part of every course has enough for an adult's probe, and the child's parts for theirs`() {
        for ((lang, c) in courses) {
            val parts = LevelPlacement.parts(c)
            for (version in Version.entries) {
                val t = LevelPlacement.start(c, null, "A1", version)
                for (p in parts.take(t.asked)) {
                    val probe = LevelPlacement.probe(c, t, p.index, Random(7))
                    val items = probe.items.sumOf { it.items }
                    assertTrue("$lang $version ${p.label}: a probe of ${probe.items.size} exercises, $items items", probe.items.size >= version.least && (items >= version.items || probe.items.size == version.exercises))
                    assertTrue("$lang $version ${p.label}: its least exercises each of another unit", probe.items.map { it.unit }.distinct().size >= minOf(version.least, p.units.size))
                    assertTrue("$lang ${p.label}: every exercise of the part's units", probe.items.all { it.unit in p.units })
                }
            }
        }
    }

    @Test fun `an adult's probe asks nothing heard or said, cuts a table and a list short`() {
        for ((lang, c) in courses) {
            for (u in c.units.values) for (e in u.test.exercises) {
                val a = LevelPlacement.asked(e, Version.ADULT) ?: continue
                assertNull("$lang ${u.id}: nothing heard", a.heard)
                when (a) {
                    is Exercise.Table -> assertTrue("$lang ${u.id}: a table cut to 6 cells or its first row", LevelPlacement.count(a) <= maxOf(6, a.rows.first().cells.count { it.toFill }))
                    is Exercise.Transform -> assertTrue(a.items.size <= 4)
                    is Exercise.GapText -> assertTrue(a.gaps.size <= 6)
                    is Exercise.Speak, is Exercise.Free, is Exercise.Dictation -> throw AssertionError("$lang ${u.id}: $a")
                    else -> Unit
                }
            }
        }
    }

    @Test fun `a child's probe is by tap only`() {
        for ((lang, c) in courses) {
            for (u in c.units.values) for (e in u.test.exercises) {
                val a = LevelPlacement.asked(e, Version.CHILD) ?: continue
                assertTrue(
                    "$lang ${u.id}: ${a::class.simpleName}",
                    a is Exercise.Choice || a is Exercise.Multi || a is Exercise.Sort || a is Exercise.Reorder || (a is Exercise.GapText && a.bank),
                )
            }
        }
    }

    // --- adaptive -----------------------------------------------------------------------------------------------------

    @Test fun `a learner is never placed above what they know, and found where the parts passed and failed meet`() {
        for ((lang, c) in courses) for (version in Version.entries) for (level in levels) {
            val t0 = LevelPlacement.start(c, null, level, version)
            for (truth in -1 until t0.parts.size) {
                val t = play(c, truth, level, version)
                val r = LevelPlacement.result(t, null)
                val at = "$lang $version from $level knowing up to $truth"
                assertTrue("$at: placed at ${r.known}", r.known <= truth)
                if (t.hi - t.lo <= 1) assertEquals(at, minOf(truth, t.asked - 1), r.known)
                assertTrue("$at: ${t.probes.size} probes", t.probes.size <= version.probes)
                assertEquals("$at: no exercise asked twice", t.probes.sumOf { it.items.size }, t.probes.flatMap { p -> p.items.map { it.key } }.distinct().size)
            }
        }
    }

    @Test fun `an adult starting at A1 is found anywhere in the course, a child anywhere to the end of B1, within a part when time is short`() {
        for ((lang, c) in courses) {
            var exact = 0
            for (truth in -1 until 12) {
                val known = LevelPlacement.result(play(c, truth, "A1", Version.ADULT), null).known
                assertTrue("$lang adult knowing up to $truth: $known", known in (truth - 1)..truth)
                if (known == truth) exact++
            }
            assertTrue("$lang adult: found exactly $exact of 13", exact >= 11)
            for (truth in -1 until 9) {
                val known = LevelPlacement.result(play(c, truth, "A2", Version.CHILD), null).known
                assertTrue("$lang child knowing up to $truth: $known", known in (truth - 1)..truth)
            }
        }
    }

    @Test fun `an adult's test takes at most about fifteen minutes, a child's about eight`() {
        for ((lang, c) in courses) for (version in Version.entries) for (level in levels) for (truth in -1 until 12) for (seed in 1..3) {
            val t = play(c, truth, level, version, seed = seed)
            val most = version.seconds + version.seconds / 6
            assertTrue("$lang $version from $level knowing up to $truth: ${t.seconds} s", t.seconds <= most)
        }
    }

    /** The probes and minutes by course and version over every true place, from A1 (the doc's numbers: the test's system-out). */
    @Test fun `the probes and the minutes, for the doc`() {
        for ((lang, c) in courses) for (version in Version.entries) {
            val runs = (-1 until LevelPlacement.parts(c).size).map { play(c, it, if (version == Version.CHILD) "A2" else "A1", version) }
            val probes = runs.map { it.probes.size }
            val minutes = runs.map { it.seconds / 60.0 }
            val items = runs.map { it.verdicts.size }
            println("placement $lang $version: probes ${probes.min()}–${probes.max()}, items ${items.min()}–${items.max()}, minutes %.1f–%.1f (mean %.1f)".format(minutes.min(), minutes.max(), minutes.average()))
            for (t in runs.take(1) + runs.takeLast(1)) for (p in t.probes) println("  ${PlacementReportProbe.line(t, p)}")
        }
    }

    @Test fun `a beginner is done in two probes, nothing marked, the level as it was`() {
        for ((lang, c) in courses) {
            val t = play(c, -1, "A1", Version.ADULT)
            assertEquals(lang, 2, t.probes.size)
            val r = LevelPlacement.result(t, null)
            assertEquals(lang, -1, r.known)
            assertEquals(lang, "A1", r.level)
            assertTrue(lang, r.units.isEmpty() && r.opens.isEmpty())
            assertFalse(lang, r.changes("A1"))
        }
    }

    @Test fun `the first probe is just below the learner's level`() {
        val parts = LevelPlacement.parts(courses.getValue("sl"))
        assertEquals("A1 2/3", parts[LevelPlacement.startAt(parts, "A1")].label)
        assertEquals("A1 3/3", parts[LevelPlacement.startAt(parts, "A2")].label)
        assertEquals("A2 3/3", parts[LevelPlacement.startAt(parts, "B1")].label)
        assertEquals("B1 3/3", parts[LevelPlacement.startAt(parts, "B2")].label)
        assertEquals("B2 3/3", parts[LevelPlacement.startAt(parts, "C1")].label)
        // a child's test probes to the end of B1
        assertEquals("B1 3/3", parts[LevelPlacement.startAt(parts, "B2", asked = 9)].label)
    }

    @Test fun `stopped, the probe on screen decides nothing`() {
        val c = courses.getValue("it")
        var t = LevelPlacement.start(c, null, "A1", Version.ADULT)
        val p = LevelPlacement.probe(c, t, LevelPlacement.next(t)!!, Random(1))
        t = LevelPlacement.record(t, p, listOf(Verdict.CORRECT), 20, complete = false)
        assertTrue(t.stopped)
        assertNull(LevelPlacement.next(t))
        assertEquals(-1, LevelPlacement.result(t, null).known)
    }

    // --- an existing learner: Jan's, Teo's ----------------------------------------------------------------------------

    /** A village whose learner did [done] of [c] long ago (their tests' best tries kept) and began [begun]. */
    private fun village(c: Course, done: List<String>, begun: List<String> = emptyList()): GameState {
        val old = UnitProgress(started = "2026-10-01", intro = 4, introDone = true, test = SheetBest(9, 1, 10, "2026-10-02", 2), done = "2026-10-02")
        val units = done.associate { School.key(c.language, it) to old } +
            begun.associate { School.key(c.language, it) to UnitProgress(started = "2026-10-20", sheets = mapOf("$it-1" to SheetBest(5, 0, 8, "2026-10-20"))) }
        return GameState(seed = 1, age = Age.VAS, course = CourseProgress(classroom = "2026-10-01", opened = mapOf("${c.language}/A1" to "2026-10-01", "${c.language}/A2" to "2026-10-15"), units = units))
    }

    @Test fun `a learner's parts done count as known, the test begins after them`() {
        val c = courses.getValue("sl")
        val s = village(c, c.playable("A1").map { it.id }, begun = listOf(c.playable("A2")[0].id))
        val t = LevelPlacement.start(c, s, "A1", Version.ADULT)
        assertEquals(2, t.known)
        assertEquals("A2 1/3", t.parts[LevelPlacement.next(t)!!].label)
        // knowing nothing more, nothing to take
        val r = LevelPlacement.result(play(c, 2, "A1", Version.ADULT, s), s)
        assertEquals(2, r.known)
        assertTrue(r.units.isEmpty())
    }

    @Test fun `taking the result only adds, a unit done or begun keeps what it had, nothing is lowered`() {
        val c = courses.getValue("sl")
        val a1 = c.playable("A1").map { it.id }
        val a2 = c.playable("A2").map { it.id }
        val s = village(c, a1.take(5), begun = listOf(a1[6], a2[0]))
        val t = play(c, 4, "A1", Version.ADULT, s) // knows A1 and A2 1/3 … 2/3
        val r = LevelPlacement.result(t, s)
        assertEquals("A2", r.level)
        assertEquals(listOf("A1", "A2"), r.opens)
        val n = LevelPlacement.apply(s, c, t, r, "A1", day, now, report = "out-1")
        val k = { id: String -> School.key("sl", id) }
        // done before: as it was
        for (id in a1.take(5)) assertEquals(s.course!!.units[k(id)], n.course!!.units[k(id)])
        // begun before: its sheets kept, now done, placed
        val begun = n.course!!.units.getValue(k(a1[6]))
        assertEquals(s.course!!.units.getValue(k(a1[6])).sheets, begun.sheets)
        assertEquals("2026-10-20", begun.started)
        assertTrue(begun.placed && begun.testedOut && begun.introDone)
        assertEquals(day.toString(), begun.done)
        // the rest of A1, A2's first parts: done, placed; A2's last part not
        val parts = LevelPlacement.parts(c)
        for (id in parts.take(5).flatMap { it.units }) assertNotNull(id, n.course!!.units[k(id)]?.done)
        for (id in parts[5].units) assertNull(id, n.course!!.units[k(id)]?.done.takeIf { id != a2[0] })
        // the levels open as they were (their days), no B1
        assertEquals(s.course!!.opened, n.course!!.opened)
        // the result kept, the level not raised at A2 for an A1 learner? it is: A1 → A2
        val placed = n.course!!.placements.getValue("sl")
        assertEquals("A2", placed.level)
        assertEquals("A1", placed.from)
        assertFalse(placed.confirmed)
        assertEquals("out-1", placed.report)
        assertEquals(parts.take(5).sumOf { it.units.size } - 5, placed.units)
        // no unit's bonus: the stores as they were
        assertEquals(s.resources, n.resources)
        assertEquals("🧭", n.log.last().emoji)
    }

    @Test fun `a level the node has already above the test's stays, never lowered, confirmed at once`() {
        val c = courses.getValue("it")
        val s = village(c, emptyList())
        val t = play(c, 2, "B1", Version.CHILD, s) // knows A1 only
        val r = LevelPlacement.result(t, s)
        assertEquals("A2", r.level)
        assertFalse(r.rises("B1"))
        val n = LevelPlacement.apply(s, c, t, r, "B1", day, now)
        val placed = n.course!!.placements.getValue("it")
        assertEquals("B1", placed.level)
        assertTrue(placed.confirmed)
        assertTrue(placed.child)
        assertEquals("B1", LevelPlacement.level("B1", placed))
    }

    @Test fun `the level the test raised counts until the node has it`() {
        val p = Placed(day = "2026-11-03", from = "A1", level = "B1")
        assertEquals("B1", LevelPlacement.level("A1", p))
        assertEquals("B2", LevelPlacement.level("B2", p))
        assertEquals("A1", LevelPlacement.level("A1", p.copy(confirmed = true)))
        val s = GameState(seed = 1, course = CourseProgress(placements = mapOf("sl" to p)))
        assertEquals(s, LevelPlacement.confirm(s, "sl", "A1"))
        assertTrue(LevelPlacement.confirm(s, "sl", "B1").course!!.placements.getValue("sl").confirmed)
        assertEquals(s, LevelPlacement.confirm(s, "it", "B2"))
    }

    @Test fun `a learner past the course's last part starts B2's units all done, at B2`() {
        val c = courses.getValue("de")
        val t = play(c, 11, "B2", Version.ADULT)
        val r = LevelPlacement.result(t, null)
        assertNull(r.start)
        assertEquals("B2", r.level)
        assertEquals(School.LEVELS, r.opens)
        assertEquals(c.units.size, r.units.size)
        val n = LevelPlacement.apply(GameState(seed = 1), c, t, r, "A1", day, now)
        assertEquals(c.units.size, n.course!!.units.values.count { it.placed })
        assertEquals(School.LEVELS.map { "de/$it" }.toSet(), n.course!!.opened.keys)
    }

    @Test fun `the school offers it with a card to a learner who hasn't begun, a quiet row to everyone else`() {
        val c = courses.getValue("sl")
        assertEquals(LevelPlacement.Offer.NONE, LevelPlacement.offer(null, null))
        assertEquals(LevelPlacement.Offer.CARD, LevelPlacement.offer(null, c))
        assertEquals(LevelPlacement.Offer.CARD, LevelPlacement.offer(GameState(seed = 1), c))
        assertEquals(LevelPlacement.Offer.ROW, LevelPlacement.offer(village(c, listOf("biti")), c))
        assertEquals(LevelPlacement.Offer.ROW, LevelPlacement.offer(village(c, emptyList(), begun = listOf("biti")), c))
        // another language's units don't count
        assertEquals(LevelPlacement.Offer.CARD, LevelPlacement.offer(village(courses.getValue("it"), listOf("ciao")), c))
        val taken = GameState(seed = 1, course = CourseProgress(placements = mapOf("sl" to Placed("2026-11-03", "A1", "A1", confirmed = true))))
        assertEquals(LevelPlacement.Offer.ROW, LevelPlacement.offer(taken, c))
    }

    @Test fun `taking it never runs on its own, a village loaded is the same village`() {
        // nothing but apply changes a village; start, probe, record and result are of the test alone
        val c = courses.getValue("en")
        val s = village(c, c.playable("A1").take(3).map { it.id })
        val t = play(c, 7, "A2", Version.ADULT, s)
        LevelPlacement.result(t, s)
        assertEquals(village(c, c.playable("A1").take(3).map { it.id }), s)
    }

    @Test fun `a raised level can be sent again from the day after, while the node doesn't have it`() {
        val p = Placed(day = "2026-11-01", from = "A1", level = "A2")
        assertFalse(LevelPlacement.waiting(p, java.time.LocalDate.parse("2026-11-01")))
        assertTrue(LevelPlacement.waiting(p, java.time.LocalDate.parse("2026-11-02")))
        assertFalse(LevelPlacement.waiting(p.copy(confirmed = true), java.time.LocalDate.parse("2026-11-02")))
        assertFalse(LevelPlacement.waiting(null, java.time.LocalDate.parse("2026-11-02")))
    }
}
