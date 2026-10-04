package si.lanisce.lani.game

import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.l10n.inBase
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Something to come back for, "Slovene · English" (see [Tomorrow]). */
data class Teaser(val kind: Kind, val emoji: String, val text: String) {
    enum class Kind { FESTIVAL, PROJECT, STORY, PEDLAR, FEAST, SURPRISE }

    /** "🍷 Čez 3 dni: Martinovo … · …" */
    val line: String get() = "$emoji $text"
}

/**
 * "Jutri · Tomorrow": what's coming, so there's always something to come back for — a festival's countdown, the next
 * step of a project, the storyteller's story (tonight's, or tomorrow's once it's heard: [si.lanisce.lani.game.scene.Stories.teaser]),
 * the pedlar's next day, the next feast, and (always) tomorrow's surprise at the road. The most telling first. Pure: the
 * dates and the day's dice decide. Never a friend's gift, however near: it comes as a surprise (Jan).
 */
object Tomorrow {
    /** How far ahead the pedlar is announced, and the feast. */
    const val DAYS = 7L

    /** [story]: the storyteller's, when someone tells stories in the village ([si.lanisce.lani.game.scene.Stories.teaser]). */
    fun teasers(s: GameState, today: LocalDate, story: Teaser? = null): List<Teaser> = buildList {
        Calendar.upcoming(today)?.let { (f, d) ->
            add(Teaser(Teaser.Kind.FESTIVAL, f.emoji, Calendar.countdown(f, ChronoUnit.DAYS.between(today, d)).replace(" ${f.emoji}", "")))
        }
        project(s, today)?.let(::add)
        story?.let(::add)
        if (s.age >= Age.TABOR) pedlar(s, today)?.let(::add)
        feast(s, today)?.let(::add)
        if (s.age >= Age.TABOR) add(Teaser(Teaser.Kind.SURPRISE, "🎁", bi("tomorrow.anotherSurprise")))
    }

    /** The next step of a project, when the village worked on one today: "Jutri: naslednji korak · …". */
    private fun project(s: GameState, today: LocalDate): Teaser? {
        if (!Projects.workedToday(s, today)) return null
        val next = Projects.open(s).map { Projects.option(s, it, today) }.firstOrNull { o ->
            o.next != null && o.waiting == ProjectOption.Why.TODAY
        } ?: return null
        return Teaser(
            Teaser.Kind.PROJECT, next.spec.emoji,
            bi("tomorrow.projectStep", "name" to next.spec.inText, "step" to next.done + 1, "steps" to next.steps),
        )
    }

    /** "Krošnjar pride v soboto · The pedlar comes on Saturday": the next pedlar day within [DAYS]. */
    private fun pedlar(s: GameState, today: LocalDate): Teaser? {
        val day = (1..DAYS).map { today.plusDays(it) }.firstOrNull { Surprises.roll(s, it)?.kind == Surprises.PEDLAR } ?: return null
        return Teaser(Teaser.Kind.PEDLAR, "🎒", bi("tomorrow.pedlarComes", "day" to dayOf(day, today)))
    }

    /** "Veselica bo spet v soboto · The next feast is on Saturday", within [DAYS]. */
    private fun feast(s: GameState, today: LocalDate): Teaser? {
        if (Chest.feastOption(s, today) == null || s.buildings.none { it.type == BuildingType.LIPA && !it.damaged }) return null
        val next = Chest.nextFeast(s, today) ?: return null // a feast can be held today: that's today's, not tomorrow's
        if (ChronoUnit.DAYS.between(today, next) > DAYS) return null
        return Teaser(Teaser.Kind.FEAST, "🎪", bi("tomorrow.nextFeast", "day" to dayOf(next, today)))
    }

    /** [day] for the messages' "{day, select, …}": 0 for tomorrow, else its ISO weekday (1 Monday … 7 Sunday). */
    private fun dayOf(day: LocalDate, today: LocalDate): String =
        if (day == today.plusDays(1)) "0" else day.dayOfWeek.value.toString()

    /** "jutri · tomorrow", "v soboto · on Saturday" (accusative after "v"): the target's words and the base's. */
    fun on(day: LocalDate, today: LocalDate): Pair<String, String> =
        dayOf(day, today).let { inTarget("tomorrow.on", "day" to it) to inBase("tomorrow.on", "day" to it) }
}
