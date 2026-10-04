package si.lanisce.lani.app

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The tutor chat as the sheet shows it (pure): today's conversation up front, since midnight by the phone's clock and
 * zone (the app's day, as the village's), and everything before folded into "📦 Arhiv · Archive": by week (Monday
 * first: "Ta teden · This week", "Prejšnji teden · Last week", then "14.–20. sept."), each week into its days
 * ("Ponedeljek, 28. septembra", with a hint of what was talked about), each day into its messages. The messages kept
 * before the archive had no times: they are "Starejša sporočila · Older messages", the archive's oldest part.
 * The tutor's latest message (Home's tutor bar shows it) is never folded away: when it came before today, the
 * conversation up front begins on its day ("Včeraj · Yesterday", then "Danes · Today").
 */
object ChatArchive {
    /** A day of the archive: its messages, in the order they came. */
    data class Day(val date: LocalDate, val messages: List<ChatMessage>)

    /** A week of the archive, from its Monday ([start]); its days in order. */
    data class Week(val start: LocalDate, val days: List<Day>) {
        val end: LocalDate get() = start.plusDays(6)
        val size: Int get() = days.sumOf { it.messages.size }
    }

    /**
     * The chat on [today]: [undated] messages (kept before the archive), the archive's [weeks] (oldest first) and
     * [todays] messages: the conversation up front, since [from] (today, or the day of the tutor's latest message when
     * that was before). A message written "after" today (the clock was set back since) counts as today's.
     */
    data class Timeline(
        val today: LocalDate,
        val undated: List<ChatMessage>,
        val weeks: List<Week>,
        val todays: List<ChatMessage>,
        val from: LocalDate = today,
        /** [todays] by day, when they begin before today ([from]): the days before, then today's (if any). */
        val upFront: List<Day> = emptyList(),
    ) {
        val archived: Int get() = undated.size + weeks.sumOf { it.size }
    }

    /** What of the archive is open: the archive itself, its undated part, weeks (by their Monday) and days. */
    data class Open(
        val archive: Boolean = false,
        val undated: Boolean = false,
        val weeks: Set<LocalDate> = emptySet(),
        val days: Set<LocalDate> = emptySet(),
    ) {
        fun toggleArchive() = copy(archive = !archive)
        fun toggleUndated() = copy(undated = !undated)
        fun toggleWeek(start: LocalDate) = copy(weeks = if (start in weeks) weeks - start else weeks + start)
        fun toggleDay(date: LocalDate) = copy(days = if (date in days) days - date else days + date)
    }

    /** A row of the chat's list; [key] stays the same while it is shown. */
    sealed interface Row {
        val key: String
    }

    /** "📦 Arhiv · Archive", with how many messages it holds. */
    data class ArchiveRow(val open: Boolean, val count: Int) : Row {
        override val key get() = "archive"
    }

    /** "Starejša sporočila · Older messages": those kept before the archive. */
    data class UndatedRow(val open: Boolean, val count: Int, val hint: String?) : Row {
        override val key get() = "undated"
    }

    data class WeekRow(val week: Week, val open: Boolean) : Row {
        override val key get() = "w:${week.start}"
    }

    data class DayRow(val day: Day, val open: Boolean, val hint: String?) : Row {
        override val key get() = "d:${day.date}"
    }

    data class MessageRow(val message: ChatMessage) : Row {
        override val key get() = "m:${message.id}"
    }

    /** "Danes · Today" between the archive and today's messages. */
    data object TodayRow : Row {
        override val key get() = "today"
    }

    /**
     * A day before today in the conversation up front ("Včeraj · Yesterday", else its [dayTitle]): the day of the
     * tutor's latest message and those after it.
     */
    data class SinceRow(val date: LocalDate) : Row {
        override val key get() = "on:$date"
    }

    /** Nothing said today yet: the invitation to ask. */
    data object EmptyRow : Row {
        override val key get() = "empty"
    }

    /** The day [m] was written on in [zone]; null when it has no time. */
    fun dayOf(m: ChatMessage, zone: ZoneId): LocalDate? = dayOf(m.at, zone)

    /** The day of time [at] in [zone]. */
    fun dayOf(at: Long?, zone: ZoneId): LocalDate? = at?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    /** The Monday of [d]'s week. */
    fun weekOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun timeline(messages: List<ChatMessage>, today: LocalDate, zone: ZoneId): Timeline {
        // the tutor's latest message stays up front, as Home's tutor bar shows it
        val from = messages.lastOrNull { !it.fromMe }?.let { dayOf(it, zone) }?.takeIf { it < today } ?: today
        val undated = mutableListOf<ChatMessage>()
        val todays = mutableListOf<ChatMessage>()
        val byDay = sortedMapOf<LocalDate, MutableList<ChatMessage>>()
        for (m in messages) {
            val d = dayOf(m, zone)
            when {
                d == null -> undated += m
                d >= from -> todays += m
                else -> byDay.getOrPut(d) { mutableListOf() } += m
            }
        }
        val weeks = byDay.entries.groupBy({ weekOf(it.key) }) { Day(it.key, it.value) }.map { (start, days) -> Week(start, days) }
        // a message "after" today (the clock set back) is today's
        val upFront = if (from == today) emptyList()
        else todays.groupBy { dayOf(it, zone)!!.coerceAtMost(today) }.map { (d, ms) -> Day(d, ms) }.sortedBy { it.date }
        return Timeline(today, undated, weeks, todays, from, upFront)
    }

    /**
     * The list's rows: the archive's row and, as far as [open], its weeks, days and messages, oldest first as a chat
     * reads; then the conversation up front: today's messages under "Danes · Today" (when there is an archive), or the
     * invitation to ask; and when it begins before today ([Timeline.from]), each earlier day's under its own title.
     */
    fun rows(t: Timeline, open: Open): List<Row> = buildList {
        if (t.archived > 0) {
            add(ArchiveRow(open.archive, t.archived))
            if (open.archive) {
                if (t.undated.isNotEmpty()) {
                    add(UndatedRow(open.undated, t.undated.size, hint(t.undated)))
                    if (open.undated) t.undated.forEach { add(MessageRow(it)) }
                }
                for (w in t.weeks) {
                    val weekOpen = w.start in open.weeks
                    add(WeekRow(w, weekOpen))
                    if (weekOpen) for (d in w.days) {
                        val dayOpen = d.date in open.days
                        add(DayRow(d, dayOpen, hint(d.messages)))
                        if (dayOpen) d.messages.forEach { add(MessageRow(it)) }
                    }
                }
            }
        }
        if (t.upFront.isNotEmpty()) {
            // the days before today up front, each under its title; today's under "Danes · Today" when there are any
            for (d in t.upFront) {
                add(if (d.date == t.today) TodayRow else SinceRow(d.date))
                d.messages.forEach { add(MessageRow(it)) }
            }
            return@buildList
        }
        if (t.archived > 0) add(TodayRow)
        if (t.todays.isEmpty()) add(EmptyRow) else t.todays.forEach { add(MessageRow(it)) }
    }

    /** "Včeraj · Yesterday", else the day's [dayTitle]: a [SinceRow]'s title. */
    fun sinceTitle(d: LocalDate, today: LocalDate): String = if (d == today.minusDays(1)) bi("chatSheet.yesterday") else dayTitle(d, today)

    /** [open] with message [id] shown: its week and day (or the undated part) open; as it was when [id] is today's or gone. */
    fun reveal(t: Timeline, open: Open, id: String): Open {
        if (t.undated.any { it.id == id }) return open.copy(archive = true, undated = true)
        for (w in t.weeks) for (d in w.days) {
            if (d.messages.any { it.id == id }) return open.copy(archive = true, weeks = open.weeks + w.start, days = open.days + d.date)
        }
        return open
    }

    /** Where message [id] is among [rows]; -1 when it isn't shown. */
    fun indexOf(rows: List<Row>, id: String): Int = rows.indexOfFirst { it is MessageRow && it.message.id == id }

    /**
     * One line of what a day was about: its first question of the learner's (the exercise or page it was about, else
     * its text), else how the tutor began.
     */
    fun hint(messages: List<ChatMessage>): String? {
        val mine = messages.firstOrNull { it.fromMe }
        val raw = mine?.about?.let { "📎 $it" } ?: mine?.text ?: messages.firstOrNull()?.text?.let(::plain)
        return raw?.let { oneLine(it, HINT) }?.ifEmpty { null }
    }

    /** The bookmarked messages, newest first. */
    fun bookmarks(messages: List<ChatMessage>): List<ChatMessage> = messages.filter { it.bookmark != null }.reversed()

    /** [m] bookmarked at [now] ([on]; a bookmark it has stays as it was), or its bookmark and note taken away. */
    fun bookmarked(m: ChatMessage, on: Boolean, now: Long): ChatMessage = m.copy(bookmark = if (on) m.bookmark ?: ChatBookmark(now) else null)

    /** [m] with [note] on its bookmark ("zakaj · why"), bookmarked at [now] if it wasn't; a blank note takes the note away. */
    fun noted(m: ChatMessage, note: String, now: Long): ChatMessage =
        m.copy(bookmark = (m.bookmark ?: ChatBookmark(now)).copy(note = note.trim().take(MAX_NOTE).ifEmpty { null }))

    /** What a message goes to the tutor with: [about] (the exercise attached, under its key) and, answering [quoted], [REPLY_TO]. */
    fun outgoing(about: JsonObject?, quoted: ChatMessage?, zone: ZoneId): JsonObject? =
        if (quoted == null) about else JsonObject(about.orEmpty() + (REPLY_TO to replyTo(quoted, zone)))

    /** [m] as a reply quotes it: its start, in one line. */
    fun quote(m: ChatMessage): ChatQuote = ChatQuote(m.id, m.fromMe, oneLine(plain(m.text), QUOTE), m.at)

    /** Where a message's data carries the message it answers. */
    const val REPLY_TO = "reply_to"

    /**
     * What a reply tells the tutor about the message it answers (`data.reply_to`): who wrote it (`from`: learner or
     * tutor), when (`at`, ISO with the offset, in [zone]; none for a message kept before the archive), its `text`, the
     * exercise or page it was about (`about`, its label) and the data it went with (`attached`, e.g.
     * `{"about_exercise": {…}}`).
     */
    fun replyTo(m: ChatMessage, zone: ZoneId): JsonObject = buildJsonObject {
        put("from", if (m.fromMe) "learner" else "tutor")
        m.at?.let { put("at", Instant.ofEpochMilli(it).atZone(zone).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS).toString()) }
        put("text", m.text.take(REPLY_TEXT))
        m.about?.let { put("about", it) }
        m.data?.let { put("attached", it) }
    }

    /** "Ta teden · This week", "Prejšnji teden · Last week", else the week's days in the target language ("14.–20. sept."). */
    fun weekTitle(w: Week, today: LocalDate): String {
        val thisWeek = weekOf(today)
        return when (w.start) {
            thisWeek -> bi("chatSheet.thisWeek")
            thisWeek.minusWeeks(1) -> bi("chatSheet.lastWeek")
            else -> Dates.range(w.start, w.end, L10n.pair.target, year = w.start.year != today.year || w.end.year != today.year)
        }
    }

    /** "Ponedeljek, 28. septembra" in the target language; with the year when it isn't this year's. */
    fun dayTitle(d: LocalDate, today: LocalDate): String =
        inTarget("chatSheet.day", "weekday" to d.dayOfWeek.value.toString(), "date" to if (d.year == today.year) Dates.dayMonth(d) else Dates.long(d))

    /** The day a bookmark or a quote names: "Danes · Today", its [dayTitle], or "Starejša sporočila · Older messages". */
    fun dayLabel(m: ChatMessage, today: LocalDate, zone: ZoneId): String = when (val d = dayOf(m, zone)) {
        null -> bi("chatSheet.undated")
        else -> if (d >= today) bi("common.today") else dayTitle(d, today)
    }

    /** Markdown's marks taken out, for a line about a tutor's message. */
    fun plain(md: String): String = md
        .replace(Regex("""(?m)^\s*(#+|>|[-*•]|\d+\.)\s+"""), "")
        .replace(Regex("""\*\*|__|[*_`|]"""), "")

    /** The first line of [s] with text, its spaces collapsed, cut at [max] characters with "…". */
    fun oneLine(s: String, max: Int): String {
        val line = s.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().replace(Regex("""\s+"""), " ")
        return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
    }

    private const val HINT = 60
    private const val QUOTE = 140
    /** A bookmark's note is a reminder, not a text. */
    const val MAX_NOTE = 200
    /** The message a reply answers goes to the tutor whole, up to this (a message is at most 8000, a reply 32000). */
    private const val REPLY_TEXT = 4000
}
