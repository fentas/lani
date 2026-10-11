package si.lanisce.lani.game

import si.lanisce.lani.game.culture.Text
import java.time.LocalDate
import java.time.MonthDay

/**
 * When a finished project's landmark stands (the culture's projects.json, `stands`; companion/GAME.md "Village projects"):
 * the village raises it again by itself every year on [raised] (the maypole on 30 April, the eve of May Day) and takes it
 * down after [until] (the end of May). Once raised it stands at least [minDays]: one the village finished outside its
 * season, for its own feast, stands that long. [upText] and [downText] are the chronicle's lines when the village raises
 * it again and when it takes it down ("Fantje so podrli mlaj. Do prihodnjega maja! · …"). A project without one stands
 * all year once finished.
 */
data class Stands(val raised: MonthDay, val until: MonthDay, val minDays: Int, val upText: Text, val downText: Text)

/**
 * The seasons of the projects' landmarks ([Stands]): whether a finished project's landmark stands on a day, and the tick's
 * part of it ([update]): the landmarks down now kept in [GameState.landmarksDown] (the map, the scenes and the bridge read
 * it), the chronicle's line when the village raises one again or takes it down. The project stays finished all the while:
 * its bonus, its memory and its helpers' friendship are the village's for good. Pure, like the engine.
 */
internal object ProjectSeasons {
    /**
     * QA's day (a debug build's "season:<day>", app/QaHooks): the landmarks stand or not as on that day, without a line in
     * the chronicle; null: as today.
     */
    @Volatile
    var qaDay: LocalDate? = null

    /**
     * The day [spec] was finished: when its last step was done ([GameState.projectsFinished]); for a project finished before
     * the game kept that, the day its people's memory of it is from ("naš mlaj", kept with the day: its leader and its
     * helpers remember it, see [Projects]); null when neither tells (it counts as raised long ago).
     */
    fun finishedOn(s: GameState, spec: ProjectSpec): LocalDate? {
        s.projectsFinished[spec.id]?.let(::isoDay)?.let { return it }
        val said = spec.memoryText.by.values.toSet()
        return s.bonds.values.asSequence().flatMap { it.memories }
            .filter { it.kind == "project" && it.sl in said }
            .mapNotNull { isoDay(it.on) }.maxOrNull()
    }

    /**
     * The day it was last raised by [day]: the day it was [finished], or the village's yearly raising since (only once it
     * was finished; for a project without a date ([finished] null) the yearly raising alone); null when it wasn't finished
     * yet by [day].
     */
    fun raisedOn(st: Stands, finished: LocalDate?, day: LocalDate): LocalDate? {
        val yearly = st.raised.atYear(day.year).let { if (it.isAfter(day)) st.raised.atYear(day.year - 1) else it }
        return when {
            finished == null -> yearly
            finished.isAfter(day) -> null
            else -> maxOf(finished, yearly)
        }
    }

    /**
     * The day the village takes it down once raised on [raised]: after its season's end ([Stands.until]) when it was raised
     * in its season (or so near its start that it would come down only to go up again within [Stands.minDays]), and never
     * before it has stood [Stands.minDays].
     */
    fun downOn(st: Stands, raised: LocalDate): LocalDate {
        val start = st.raised.atYear(raised.year)
        val end = st.until.atYear(raised.year)
        val atLeast = raised.plusDays(st.minDays.toLong())
        val season = !raised.isAfter(end) && !atLeast.plusDays(st.minDays.toLong()).isBefore(start)
        return if (season) maxOf(atLeast, end.plusDays(1)) else atLeast
    }

    /** Whether a landmark of [st], [finished] on that day (null: long ago), stands on [day]. */
    fun standsOn(st: Stands, finished: LocalDate?, day: LocalDate): Boolean =
        raisedOn(st, finished, day)?.let { day.isBefore(downOn(st, it)) } ?: false

    /** The finished projects whose landmark is down on [day] (the culture's projects with a season, [Stands]). */
    fun down(s: GameState, day: LocalDate): Set<String> = Catalog.projects.mapNotNullTo(LinkedHashSet()) { p ->
        val st = p.stands ?: return@mapNotNullTo null
        p.id.takeIf { Projects.finished(s, p) && !standsOn(st, finishedOn(s, p), day) }
    }

    /**
     * The tick's part ([GameEngine.tick]): the landmarks down [today] (QA's day instead, [qaDay]) in
     * [GameState.landmarksDown], and the chronicle's line for each the village raised again or took down since it was last
     * kept (once: the next tick finds it kept). A save from before seasons has none kept: a landmark down now is taken down
     * with its line at the first tick. [tell] false (QA's hook, setting its day or letting it go): no line.
     */
    fun update(s: GameState, today: LocalDate, now: Long, tell: Boolean = true): GameState {
        val down = down(s, qaDay ?: today)
        if (down == s.landmarksDown) return s
        val lines = if (qaDay != null || !tell) emptyList() else Catalog.projects.mapNotNull { p ->
            val st = p.stands ?: return@mapNotNull null
            when {
                p.id in s.landmarksDown && p.id !in down -> p.emoji to st.upText.bi()
                p.id !in s.landmarksDown && p.id in down -> p.emoji to st.downText.bi()
                else -> null
            }
        }
        return s.copy(landmarksDown = down).logged(now, *lines.toTypedArray())
    }
}
