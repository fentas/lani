package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.Course
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.Sheets
import si.lanisce.lani.data.UnitRef
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The learner's way through the school's course (companion/COURSE.md, "The learner's progress"; GAME.md "The school"):
 * the day the class began ([classroom]: the village a hamlet, the teacher coming over), the course levels opened ([opened], by
 * "<language>/<level>": a level once open stays open), and each unit's progress ([units], by "<language>/<unit id>").
 * [v]: the format's version. An older save has none; an older bridge keeps it as it is.
 */
@Serializable
data class CourseProgress(
    val v: Int = 1,
    val classroom: String? = null,
    val opened: Map<String, String> = emptyMap(),
    val units: Map<String, UnitProgress> = emptyMap(),
)

/**
 * A unit's progress: the day it began ([started]), the intro's steps done ([intro]) and whether it was finished or
 * skipped ([introDone]), each worksheet's best try by its id ([sheets], homework too), the test's best try ([test]), the
 * day the test was passed ([done]), and whether that was straight away, without the worksheets ([testedOut]).
 */
@Serializable
data class UnitProgress(
    val started: String? = null,
    val intro: Int = 0,
    val introDone: Boolean = false,
    val sheets: Map<String, SheetBest> = emptyMap(),
    val test: SheetBest? = null,
    val done: String? = null,
    val testedOut: Boolean = false,
)

/** A sheet's best try: [right] and [almost] of [of] items, its [day], and how often it was done ([tries]). */
@Serializable
data class SheetBest(val right: Int = 0, val almost: Int = 0, val of: Int = 0, val day: String = "", val tries: Int = 1) {
    /** Right items, an almost counting half. */
    val score: Double get() = right + 0.5 * almost

    companion object {
        fun of(items: List<Verdict>, day: LocalDate, tries: Int = 1) =
            SheetBest(items.count { it == Verdict.CORRECT }, items.count { it == Verdict.ALMOST }, items.size, day.toString(), tries)
    }
}

/**
 * What the school's upgrade to [toLevel] asks of the course (GAME.md, "The school"): every unit of course level [level]
 * done (the level it needs, or one before it while that isn't reached: [School.gate]); [left] still to go. Like an
 * upgrade's words, it holds the upgrade back until it's done, the neighbours' help too.
 */
data class CourseGate(val building: String, val toLevel: Int, val level: String, val left: Int) {
    val done: Boolean get() = left <= 0
}

/**
 * The school (companion/GAME.md, "The school"; COURSE.md): the class from the hamlet on (by the fire, under the linden,
 * in the school), the course levels it opens, the unit suggested next, what each sheet leaves in the progress, and the school's upgrades the course
 * holds. Pure, like the engine.
 */
object School {
    /** The levels the school teaches: the class A1, the school A2, its upgrades B1 and B2. */
    val LEVELS = listOf("A1", "A2", "B1", "B2")

    fun key(language: String, id: String) = "$language/$id"

    fun progress(s: GameState, language: String, unit: String): UnitProgress? = s.course?.units?.get(key(language, unit))

    /** The school building, standing or not. */
    fun schoolBuilding(s: GameState): Building? = s.buildings.firstOrNull { it.type == BuildingType.SCHOOL }

    /** The class is indoors: the school stands, undamaged. */
    fun indoors(s: GameState): Boolean = schoolBuilding(s)?.damaged == false

    /**
     * The class meets: from the hamlet on (Zaselek, right after the camp: GAME.md, "The school"), by the fire, under the
     * linden once it stands, and in the school once that stands; also a village whose class began before ([classroom]).
     */
    fun open(s: GameState): Boolean = s.age >= Age.ZASELEK || s.course?.classroom != null || indoors(s)

    /** Where the class meets now. */
    enum class Place { FIRE, LINDEN, SCHOOL }

    fun place(s: GameState): Place = when {
        indoors(s) -> Place.SCHOOL
        s.buildings.any { it.type == BuildingType.LIPA && !it.damaged } -> Place.LINDEN
        else -> Place.FIRE
    }

    /**
     * What the Build sheet says on the school's card while none stands (GAME.md "The school"): "Pouk že poteka pod lipo;
     * šola ga preseli pod streho in po A1 odpre A2 · Class is already on under the linden; the school brings it indoors
     * and, after A1, opens A2" (by the fire before the linden, and "opens A2" once A1 is done). Null without a [course],
     * before the class begins, or with a school standing.
     */
    fun buildNote(s: GameState, course: Course?): String? {
        if (course == null || !open(s) || schoolBuilding(s) != null) return null
        val at = if (place(s) == Place.LINDEN) "linden" else "fire"
        return bi("school.buildNote", "place" to at, "a1" to if (levelDone(s, course, LEVELS.first())) "done" else "todo")
    }

    // --- the levels -------------------------------------------------------------------------------------------------

    /** What a course level waits for. */
    sealed interface Need {
        /** The village a hamlet (Zaselek): the class begins with it. */
        data object Hamlet : Need
        /** The school standing. */
        data object School : Need
        /** The school at [level] (its upgrade). */
        data class SchoolLevel(val level: Int) : Need
        /** [left] units of [level] still to do. */
        data class Units(val level: String, val left: Int) : Need
    }

    /**
     * A course level as the school shows it: [open], else what it waits for ([needs]); its units done ([done]) of those
     * written ([of]) and how many more are [planned].
     */
    data class LevelState(val level: String, val open: Boolean, val needs: List<Need>, val done: Int, val of: Int, val planned: Int) {
        val complete: Boolean get() = done >= of
    }

    /** The units of [level] written so far and not done: what holds the next level (or the school's upgrade) back. */
    fun unitsLeft(s: GameState, course: Course, level: String): Int =
        course.playable(level).count { progress(s, course.language, it.id)?.done == null }

    /** Every unit of [level] written so far is done (a level without units is: it holds nothing back). */
    fun levelDone(s: GameState, course: Course, level: String): Boolean = unitsLeft(s, course, level) == 0

    /**
     * What opens [level] now (its index in [LEVELS]): A1 the class (the hamlet); A2 the school and A1 done; B1 and B2 the school's level 2
     * and 3 and the level before done.
     */
    private fun needs(s: GameState, course: Course, level: String): List<Need> {
        val i = LEVELS.indexOf(level)
        val school = schoolBuilding(s)
        return buildList {
            when {
                i <= 0 -> if (!open(s)) add(Need.Hamlet)
                i == 1 -> {
                    if (!indoors(s)) add(Need.School)
                    unitsLeft(s, course, LEVELS[0]).takeIf { it > 0 }?.let { add(Need.Units(LEVELS[0], it)) }
                }
                else -> {
                    // the school at the level that opens it (its upgrade waits for the level before: [gate]), and that level
                    // done (a school upgraded before the course existed opens nothing ahead of it)
                    if ((school?.takeIf { !it.damaged }?.level ?: 0) < i) {
                        if (school == null || school.damaged) add(Need.School) else add(Need.SchoolLevel(i))
                    }
                    unitsLeft(s, course, LEVELS[i - 1]).takeIf { it > 0 }?.let { add(Need.Units(LEVELS[i - 1], it)) }
                }
            }
        }
    }

    /** Whether [level] is open: opened before (it stays), or nothing holds it back now. */
    fun isOpen(s: GameState, course: Course, level: String): Boolean =
        key(course.language, level) in s.course?.opened.orEmpty() || (level in LEVELS && needs(s, course, level).isEmpty())

    fun level(s: GameState, course: Course, level: String): LevelState {
        val playable = course.playable(level)
        val open = isOpen(s, course, level)
        return LevelState(
            level, open, if (open) emptyList() else needs(s, course, level),
            playable.count { progress(s, course.language, it.id)?.done != null }, playable.size,
            course.refs(level).size - playable.size,
        )
    }

    /** The school's levels of [course] as it shows them, in order. */
    fun levels(s: GameState, course: Course): List<LevelState> = LEVELS.filter { l -> course.levels.any { it.level == l } }.map { level(s, course, it) }

    /**
     * The class begun and the levels open now that weren't kept yet: the day the class began kept ([CourseProgress.classroom],
     * once, with a line in the chronicle on who comes to teach: [teacher], the school's person by name; not for a village
     * whose school already stands), each level opened kept with its day, a line for each but A1. Unchanged when nothing is
     * new. Called whenever the village may have changed (an age, a building, the school opened).
     */
    fun opened(s: GameState, course: Course, today: LocalDate, now: Long, teacher: Localized? = null): GameState {
        if (!open(s)) return s
        val c = s.course ?: CourseProgress()
        val begins = c.classroom == null && !indoors(s)
        val fresh = LEVELS.filter { l -> course.levels.any { it.level == l } && key(course.language, l) !in c.opened && needs(s, course, l).isEmpty() }
        if (fresh.isEmpty() && !begins) return s
        val day = today.toString()
        val n = s.copy(course = c.copy(classroom = if (begins) day else c.classroom, opened = c.opened + fresh.associate { key(course.language, it) to day }))
        val lines = listOfNotNull(
            if (!begins) null else "📚" to (if (teacher != null) bi("school.classBeginsBy", "teacher" to teacher) else bi("school.classBegins")),
        ) + fresh.filter { it != LEVELS.first() }.map { "🏫" to bi("school.levelOpened", "level" to it) }
        return if (lines.isEmpty()) n else n.logged(now, *lines.toTypedArray())
    }

    // --- the units --------------------------------------------------------------------------------------------------

    /** Where a unit stands for the learner. */
    enum class Status { DONE, NEXT, OPEN, LOCKED, PLANNED }

    /** The unit suggested next: the first written one not done, of the open levels, in the course's order. */
    fun next(s: GameState, course: Course): UnitRef? =
        LEVELS.filter { isOpen(s, course, it) }.firstNotNullOfOrNull { l ->
            course.playable(l).firstOrNull { progress(s, course.language, it.id)?.done == null }
        }

    fun status(s: GameState, course: Course, ref: UnitRef, next: UnitRef? = next(s, course)): Status = when {
        ref.planned || ref.id !in course.units -> Status.PLANNED
        progress(s, course.language, ref.id)?.done != null -> Status.DONE
        !isOpen(s, course, course.levelOf(ref.id) ?: "") -> Status.LOCKED
        ref.id == next?.id -> Status.NEXT
        else -> Status.OPEN
    }

    /** What a unit's next step is: its intro, a worksheet not done yet, its test, or nothing (done). */
    sealed interface Step {
        data object Intro : Step
        data class Sheet(val id: String) : Step
        data object Test : Step
        data object Done : Step
    }

    fun nextStep(unit: CourseUnit, p: UnitProgress?): Step = when {
        p?.done != null -> Step.Done
        unit.intro != null && p?.introDone != true -> Step.Intro
        else -> unit.worksheets.firstOrNull { it.id !in p?.sheets.orEmpty() }?.let { Step.Sheet(it.id) } ?: Step.Test
    }

    /**
     * The lesson the class goes on with (GAME.md "The school", "The lesson in the class's place"): unit [unit], [resume]d
     * when it was begun (its next step), else begun (its page).
     */
    data class Lesson(val unit: String, val resume: Boolean)

    /**
     * The class's lesson now: a unit begun and not done, of an open level (the first in the course's order), goes on; else
     * the unit suggested next ([next], ★) begins. Null before the class begins, or with every unit of the open levels done.
     */
    fun lesson(s: GameState, course: Course): Lesson? {
        if (!open(s)) return null
        val begun = LEVELS.filter { isOpen(s, course, it) }.firstNotNullOfOrNull { l ->
            course.playable(l).firstOrNull { progress(s, course.language, it.id)?.let { p -> p.done == null } == true }
        }
        if (begun != null) return Lesson(begun.id, resume = true)
        return next(s, course)?.let { Lesson(it.id, resume = false) }
    }

    /**
     * Where [lesson] of [unit] ([p]: its progress) goes: a unit begun to its next step (its intro where it was left, the next
     * worksheet, the test, as the unit's main button), null for its page (a unit not begun yet, or done meanwhile).
     */
    fun lessonStep(lesson: Lesson, unit: CourseUnit, p: UnitProgress?): Step? =
        if (!lesson.resume) null else nextStep(unit, p).takeIf { it != Step.Done }

    private fun GameState.unit(language: String, id: String, today: LocalDate, f: (UnitProgress) -> UnitProgress): GameState {
        val c = course ?: CourseProgress()
        val k = key(language, id)
        val p = c.units[k] ?: UnitProgress(started = today.toString())
        return copy(course = c.copy(units = c.units + (k to f(p.copy(started = p.started ?: today.toString())))))
    }

    /** The intro of unit [id] went as far as [steps] ([done]: finished, or skipped: "Poznam to · I know this"). */
    fun introProgress(s: GameState, language: String, id: String, steps: Int, done: Boolean, today: LocalDate): GameState =
        s.unit(language, id, today) { p -> p.copy(intro = maxOf(p.intro, steps), introDone = p.introDone || done) }

    /** Worksheet [sheet] of unit [id] (or its homework) was done with [items]: the best try kept, the tries counted. */
    fun sheetDone(s: GameState, language: String, id: String, sheet: String, items: List<Verdict>, today: LocalDate): GameState {
        if (items.isEmpty()) return s
        return s.unit(language, id, today) { p ->
            val before = p.sheets[sheet]
            val now = SheetBest.of(items, today, (before?.tries ?: 0) + 1)
            val best = if (before == null || now.score / now.of.coerceAtLeast(1) >= before.score / before.of.coerceAtLeast(1)) now else before.copy(tries = now.tries)
            p.copy(sheets = p.sheets + (sheet to best))
        }
    }

    /**
     * Unit [unit]'s test was taken with [items]: passed at its [UnitTest.pass][si.lanisce.lani.data.UnitTest] percent,
     * the unit is done (the day kept; [testedOut] when no worksheet of it was done), with a line in the chronicle and,
     * the first time, the unit's bonus ([unitBonus]: the scarcest resource). Either way the try counts and the best is kept. Returns the village
     * and whether the unit was done now (passed the first time).
     */
    fun testDone(s: GameState, unit: CourseUnit, items: List<Verdict>, today: LocalDate, now: Long): Pair<GameState, Boolean> {
        if (items.isEmpty()) return s to false
        val passed = Sheets.passed(items, unit.test.pass)
        val before = progress(s, unit.language, unit.id)
        val first = passed && before?.done == null
        var n = s.unit(unit.language, unit.id, today) { p ->
            val t = SheetBest.of(items, today, (p.test?.tries ?: 0) + 1)
            val best = if (p.test == null || t.score / t.of.coerceAtLeast(1) >= p.test.score / p.test.of.coerceAtLeast(1)) t else p.test.copy(tries = t.tries)
            if (!first) p.copy(test = best)
            else p.copy(test = best, done = today.toString(), testedOut = p.sheets.keys.none { k -> unit.worksheets.any { it.id == k } })
        }
        if (!first) return n to false
        n = credit(n, unitBonus(n)).first
        val title = Localized { lang -> Grammar.text(unit.title, lang.code) }
        return n.logged(now, unit.emoji to bi("school.unitDone", "unit" to title)) to true
    }

    /**
     * What a unit done brings once, besides its answers' pay: the village's scarcest resource (the lowest share of its cap,
     * as a request's side reward), more with the age.
     */
    fun unitBonus(s: GameState): Map<Res, Int> {
        val caps = GameEngine.attributes(s).caps
        val scarce = Res.entries.minBy { s.res(it).toFloat() / caps.getValue(it).coerceAtLeast(1) }
        return mapOf(scarce to (Catalog.UNIT_DONE_BONUS * (1f + 0.3f * s.age.ordinal)).roundToInt())
    }

    // --- the school's upgrades --------------------------------------------------------------------------------------

    /** The course level the school's upgrade to [toLevel] asks to be done: level 2 A2, 3 B1, 4 and 5 B2. */
    fun upgradeNeeds(toLevel: Int): String? = if (toLevel < 2) null else LEVELS[(toLevel - 1).coerceAtMost(LEVELS.size - 1)]

    /**
     * What upgrading [buildingId] asks of the course: for the school, every written unit of the level its next level
     * needs ([upgradeNeeds]), a level the course has reached ([isOpen]): one not reached yet isn't done, however few units
     * it has, and what holds the upgrade back is the first level before it with units to go (A2 not written yet: the
     * school's level 2 waits for A1, also a school built in the hamlet). Null for any other building, one at the top, or
     * without a course.
     */
    fun gate(s: GameState, course: Course?, buildingId: String): CourseGate? {
        course ?: return null
        val b = s.buildings.firstOrNull { it.id == buildingId && it.type == BuildingType.SCHOOL } ?: return null
        val to = b.level + 1
        if (to > Catalog.MAX_LEVEL) return null
        val level = upgradeNeeds(to) ?: return null
        val back = if (isOpen(s, course, level)) level else LEVELS.takeWhile { it != level }.firstOrNull { unitsLeft(s, course, it) > 0 } ?: level
        return CourseGate(b.id, to, back, unitsLeft(s, course, back))
    }
}
