package si.lanisce.lani.app

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.app.ChatArchive.ArchiveRow
import si.lanisce.lani.app.ChatArchive.DayRow
import si.lanisce.lani.app.ChatArchive.EmptyRow
import si.lanisce.lani.app.ChatArchive.MessageRow
import si.lanisce.lani.app.ChatArchive.Open
import si.lanisce.lani.app.ChatArchive.TodayRow
import si.lanisce.lani.app.ChatArchive.UndatedRow
import si.lanisce.lani.app.ChatArchive.WeekRow
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The chat as the sheet shows it: today up front, the archive by week and day, the jump to a message, bookmarks, replies. */
class ChatArchiveTest {
    private val ljubljana = ZoneId.of("Europe/Ljubljana")
    /** A Tuesday: this week began yesterday. */
    private val today = LocalDate.of(2026, 9, 29)

    /** A message written at [local] ("2026-09-28T18:00") in [zone]; none: from before the archive. */
    private fun msg(id: String, local: String?, fromMe: Boolean = false, text: String = "sporočilo $id", about: String? = null, zone: ZoneId = ljubljana) =
        ChatMessage(fromMe = fromMe, text = text, about = about, id = id, at = local?.let { LocalDateTime.parse(it).atZone(zone).toInstant().toEpochMilli() })

    private fun ids(ms: List<ChatMessage>) = ms.map { it.id }
    private fun keys(rows: List<ChatArchive.Row>) = rows.map { it.key }

    private val history = listOf(
        msg("old", null, fromMe = true),
        msg("sat", "2026-09-19T10:00"), // two weeks back
        msg("mon21", "2026-09-21T09:00", fromMe = true),
        msg("sun", "2026-09-27T21:00"), // last week's Sunday
        msg("mon", "2026-09-28T18:00", fromMe = true),
        msg("late", "2026-09-28T23:59"),
        msg("early", "2026-09-29T00:01", fromMe = true),
        msg("now", "2026-09-29T09:00"),
    )

    @Test fun `today's conversation is since midnight, the days before it are the archive's, by week from Monday`() {
        val t = ChatArchive.timeline(history, today, ljubljana)
        assertEquals(listOf("early", "now"), ids(t.todays))
        assertEquals(listOf("old"), ids(t.undated))
        assertEquals(listOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28)), t.weeks.map { it.start })
        assertEquals(listOf(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27)), t.weeks[1].days.map { it.date })
        assertEquals(listOf("mon", "late"), ids(t.weeks[2].days.single().messages))
        assertEquals(6, t.archived)
    }

    @Test fun `the rollover - at midnight today's conversation goes to the archive once the tutor has said something since`() {
        // the tutor's latest message ("now", the 29th) stays up front the next day, under its day, as Home's bar shows it
        val tomorrow = ChatArchive.timeline(history, today.plusDays(1), ljubljana)
        assertEquals(today, tomorrow.from)
        assertEquals(listOf("early", "now"), ids(tomorrow.todays))
        assertEquals(listOf(today), tomorrow.upFront.map { it.date })
        assertEquals(LocalDate.of(2026, 9, 28), tomorrow.weeks.last().days.last().date)
        // the tutor answers on the 30th: the 29th goes to the archive
        val answered = ChatArchive.timeline(history + msg("next", "2026-09-30T08:00"), today.plusDays(1), ljubljana)
        assertEquals(listOf("next"), ids(answered.todays))
        val yesterday = answered.weeks.last().days.last()
        assertEquals(today, yesterday.date)
        assertEquals(listOf("early", "now"), ids(yesterday.messages))
        // a Monday: this week's only day is today, so the archive's last week is last week
        val monday = ChatArchive.timeline(history, LocalDate.of(2026, 10, 5), ljubljana)
        assertEquals(LocalDate.of(2026, 9, 28), monday.weeks.last().start)
    }

    @Test fun `what Home's tutor bar shows is never folded away - the conversation up front begins on its day`() {
        // the tutor answered late last night; Jan has written this morning, no answer yet
        val chat = listOf(
            msg("sun", "2026-09-27T21:00"),
            msg("q", "2026-09-28T22:00", fromMe = true),
            msg("a", "2026-09-28T23:50"),
            msg("hi", "2026-09-29T08:00", fromMe = true),
        )
        val t = ChatArchive.timeline(chat, today, ljubljana)
        assertEquals(LocalDate.of(2026, 9, 28), t.from)
        assertEquals(listOf("q", "a", "hi"), ids(t.todays))
        assertEquals(listOf("archive", "on:2026-09-28", "m:q", "m:a", "today", "m:hi"), keys(ChatArchive.rows(t, Open())))
        // nothing written today: yesterday's conversation, no "today" and no invitation under it
        val quiet = ChatArchive.timeline(chat.dropLast(1), today, ljubljana)
        assertEquals(listOf("archive", "on:2026-09-28", "m:q", "m:a"), keys(ChatArchive.rows(quiet, Open())))
        // days ago, and nothing archived before it: still up front, under its day
        val days = ChatArchive.timeline(listOf(msg("a", "2026-09-25T10:00")), today, ljubljana)
        assertEquals(listOf("on:2026-09-25", "m:a"), keys(ChatArchive.rows(days, Open())))
        assertEquals(0, days.archived)
        // the jump to a message up front leaves the archive as it was
        assertEquals(Open(), ChatArchive.reveal(t, Open(), "a"))
        // the tutor's message "after" today (the clock set back) is today's: today's conversation as always
        val future = ChatArchive.timeline(listOf(msg("q", "2026-09-28T10:00", fromMe = true), msg("f", "2026-10-02T10:00")), today, ljubljana)
        assertEquals(today, future.from)
        assertEquals(listOf("archive", "today", "m:f"), keys(ChatArchive.rows(future, Open())))
        // only the learner's messages before today (no answer ever): folded, the invitation up front
        val unanswered = ChatArchive.timeline(listOf(msg("q", "2026-09-28T10:00", fromMe = true)), today, ljubljana)
        assertEquals(listOf("archive", "today", "empty"), keys(ChatArchive.rows(unanswered, Open())))
    }

    @Test fun `yesterday is named so, earlier days by their date`() {
        assertEquals("Včeraj · Yesterday", ChatArchive.sinceTitle(today.minusDays(1), today))
        assertEquals(ChatArchive.dayTitle(today.minusDays(4), today), ChatArchive.sinceTitle(today.minusDays(4), today))
    }

    @Test fun `the day is the phone's zone's`() {
        // 23:30 UTC on the 28th is 01:30 on the 29th in Ljubljana, 19:30 on the 28th in New York
        // the learner's messages: a tutor's latest before today would stay up front, not in the archive
        val m = msg("x", "2026-09-28T23:30", fromMe = true, zone = ZoneId.of("UTC"))
        assertEquals(listOf("x"), ids(ChatArchive.timeline(listOf(m), today, ljubljana).todays))
        assertEquals(LocalDate.of(2026, 9, 28), ChatArchive.timeline(listOf(m), today, ZoneId.of("UTC")).weeks.single().days.single().date)
        assertEquals(LocalDate.of(2026, 9, 28), ChatArchive.dayOf(m, ZoneId.of("America/New_York")))
        // the night summer time ends (25 October, 03:00 → 02:00) is still one day
        val night = listOf(msg("a", "2026-10-25T01:30", fromMe = true), msg("b", "2026-10-25T02:30", fromMe = true), msg("c", "2026-10-25T23:30", fromMe = true))
        assertEquals(listOf(LocalDate.of(2026, 10, 25)), ChatArchive.timeline(night, LocalDate.of(2026, 10, 26), ljubljana).weeks.single().days.map { it.date })
    }

    @Test fun `a message written after today (the clock was set back since) is today's`() {
        val t = ChatArchive.timeline(listOf(msg("future", "2026-10-02T10:00")), today, ljubljana)
        assertEquals(listOf("future"), ids(t.todays))
        assertEquals(0, t.archived)
    }

    @Test fun `folded, the archive is one row above today's messages`() {
        val t = ChatArchive.timeline(history, today, ljubljana)
        assertEquals(listOf("archive", "today", "m:early", "m:now"), keys(ChatArchive.rows(t, Open())))
        assertEquals(ArchiveRow(open = false, count = 6), ChatArchive.rows(t, Open()).first())
        // nothing said today yet, and the tutor's last word long folded with no answer since: the invitation to ask
        val quiet = ChatArchive.timeline(history.filter { it.fromMe || it.at == null }, today.plusDays(1), ljubljana)
        assertEquals(listOf("archive", "today", "empty"), keys(ChatArchive.rows(quiet, Open())))
        // no archive: as the chat always was
        assertEquals(listOf(EmptyRow), ChatArchive.rows(ChatArchive.timeline(emptyList(), today, ljubljana), Open()))
        assertEquals(listOf("m:x"), keys(ChatArchive.rows(ChatArchive.timeline(listOf(msg("x", "2026-09-29T08:00")), today, ljubljana), Open())))
    }

    @Test fun `open, the archive reads oldest first - the older messages, weeks, their days, a day's messages`() {
        val t = ChatArchive.timeline(history, today, ljubljana)
        val lastWeek = LocalDate.of(2026, 9, 21)
        val open = Open(archive = true).toggleWeek(lastWeek).toggleDay(LocalDate.of(2026, 9, 27))
        assertEquals(
            listOf("archive", "undated", "w:2026-09-14", "w:2026-09-21", "d:2026-09-21", "d:2026-09-27", "m:sun", "w:2026-09-28", "today", "m:early", "m:now"),
            keys(ChatArchive.rows(t, open)),
        )
        val rows = ChatArchive.rows(t, open.toggleUndated())
        assertEquals(UndatedRow(open = true, count = 1, hint = "sporočilo old"), rows[1])
        assertEquals("m:old", rows[2].key)
        assertTrue((rows.first { it is WeekRow && it.week.start == lastWeek } as WeekRow).open)
        // folding a day again leaves its week open
        assertEquals(
            listOf("archive", "undated", "w:2026-09-14", "w:2026-09-21", "d:2026-09-21", "d:2026-09-27", "w:2026-09-28", "today", "m:early", "m:now"),
            keys(ChatArchive.rows(t, open.toggleDay(LocalDate.of(2026, 9, 27)))),
        )
    }

    @Test fun `the jump opens the archive to the message, and finds its row`() {
        val t = ChatArchive.timeline(history, today, ljubljana)
        val open = ChatArchive.reveal(t, Open(), "mon21")
        assertEquals(Open(archive = true, weeks = setOf(LocalDate.of(2026, 9, 21)), days = setOf(LocalDate.of(2026, 9, 21))), open)
        val rows = ChatArchive.rows(t, open)
        val i = ChatArchive.indexOf(rows, "mon21")
        assertEquals("d:2026-09-21", rows[i - 1].key) // right under its day's title, which the jump shows too
        assertTrue(i < ChatArchive.indexOf(rows, "early"))
        // what else was open stays open
        val both = ChatArchive.reveal(t, open, "late")
        assertEquals(setOf(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28)), both.weeks)
        assertTrue(ChatArchive.indexOf(ChatArchive.rows(t, both), "mon21") >= 0)
        // a message from before the archive, today's (nothing to open) and one that's gone
        assertEquals(Open(archive = true, undated = true), ChatArchive.reveal(t, Open(), "old"))
        assertEquals(Open(), ChatArchive.reveal(t, Open(), "now"))
        assertEquals(3, ChatArchive.indexOf(ChatArchive.rows(t, Open()), "now"))
        assertEquals(Open(), ChatArchive.reveal(t, Open(), "gone"))
        assertEquals(-1, ChatArchive.indexOf(ChatArchive.rows(t, Open()), "gone"))
    }

    @Test fun `a day's hint is its first question, the exercise or page it was about, else how the tutor began`() {
        assertEquals("Kako se reče „fork“?", ChatArchive.hint(listOf(msg("a", null, text = "**Plan za danes**"), msg("b", null, fromMe = true, text = "  Kako se reče „fork“?\nIn „spoon“?"))))
        assertEquals("📎 📖 Rodilnik", ChatArchive.hint(listOf(msg("a", null, fromMe = true, text = "Zakaj?", about = "📖 Rodilnik"))))
        assertEquals("Plan za danes", ChatArchive.hint(listOf(msg("a", null, text = "## **Plan za danes**\n- 10 kartic"))))
        val long = "Ali mi lahko razložiš, zakaj se po besedi „brez“ vedno uporablja rodilnik, tudi pri živih bitjih?"
        val hint = ChatArchive.hint(listOf(msg("a", null, fromMe = true, text = long)))!!
        assertTrue(hint.length <= 60)
        assertTrue(hint.startsWith("Ali mi lahko razložiš") && hint.endsWith("…"))
        assertNull(ChatArchive.hint(emptyList()))
        // a day's row carries it
        val t = ChatArchive.timeline(history, today, ljubljana)
        val day = ChatArchive.rows(t, ChatArchive.reveal(t, Open(), "mon")).first { it is DayRow } as DayRow
        assertEquals("sporočilo mon", day.hint)
    }

    @Test fun `bookmarks - added, noted, taken away, listed newest first`() {
        val marked = history.map { if (it.id in setOf("sat", "mon", "now")) ChatArchive.bookmarked(it, on = true, now = 5) else it }
        assertEquals(listOf("now", "mon", "sat"), ids(ChatArchive.bookmarks(marked)))
        val m = marked.first { it.id == "mon" }
        assertEquals(ChatBookmark(5), m.bookmark)
        // bookmarking again keeps when it was first bookmarked
        assertEquals(ChatBookmark(5), ChatArchive.bookmarked(m, on = true, now = 9).bookmark)
        val noted = ChatArchive.noted(m, " zakaj: dajalnik ", now = 9)
        assertEquals(ChatBookmark(5, "zakaj: dajalnik"), noted.bookmark)
        assertEquals(ChatBookmark(5), ChatArchive.noted(noted, "  ", now = 10).bookmark)
        assertEquals(ChatArchive.MAX_NOTE, ChatArchive.noted(m, "x".repeat(500), now = 9).bookmark?.note?.length)
        // a note on a message not bookmarked bookmarks it
        assertEquals(ChatBookmark(7, "važno"), ChatArchive.noted(history[1], "važno", now = 7).bookmark)
        assertNull(ChatArchive.bookmarked(noted, on = false, now = 11).bookmark)
    }

    @Test fun `a reply quotes the message, and tells the tutor about it in reply_to`() {
        val about = buildJsonObject { put("about_exercise", buildJsonObject { put("prompt", "Daj ___ knjigo.") }) }
        val quoted = msg("q", "2026-09-27T18:42:05", fromMe = true, text = "Zakaj **mi**?", about = "Daj ___ knjigo. → mi").copy(data = about)
        val quote = ChatArchive.quote(quoted)
        assertEquals(ChatQuote("q", fromMe = true, text = "Zakaj mi?", at = quoted.at), quote)

        val grammar = buildJsonObject { put("about_grammar", buildJsonObject { put("id", "rodilnik") }) }
        val data = ChatArchive.outgoing(grammar, quoted, ljubljana)!!
        assertEquals(grammar["about_grammar"], data["about_grammar"]) // the page attached now stays where it was
        val r = data[ChatArchive.REPLY_TO]!!.jsonObject
        assertEquals("learner", r["from"]!!.jsonPrimitive.content)
        assertEquals("2026-09-27T18:42:05+02:00", r["at"]!!.jsonPrimitive.content)
        assertEquals("Zakaj **mi**?", r["text"]!!.jsonPrimitive.content)
        assertEquals("Daj ___ knjigo. → mi", r["about"]!!.jsonPrimitive.content)
        assertEquals(about, r["attached"])

        // the tutor's message from before the archive: no time; nothing attached now
        val tutor = ChatArchive.outgoing(null, msg("t", null, text = "Rodilnik …"), ljubljana)!![ChatArchive.REPLY_TO]!!.jsonObject
        assertEquals(setOf("from", "text"), tutor.keys)
        assertEquals("tutor", tutor["from"]!!.jsonPrimitive.content)
        // no reply: the data as it was
        assertEquals(grammar, ChatArchive.outgoing(grammar, null, ljubljana))
        assertNull(ChatArchive.outgoing(null, null, ljubljana))
        // a long message goes whole, up to 4000 characters
        val long = ChatArchive.outgoing(JsonObject(emptyMap()), msg("l", null, text = "a".repeat(9000)), ljubljana)!![ChatArchive.REPLY_TO]!!.jsonObject
        assertEquals(4000, long["text"]!!.jsonPrimitive.content.length)
    }

    @Test fun `weeks and days are named as Jan's example has them`() {
        val week = { start: LocalDate -> ChatArchive.Week(start, emptyList()) }
        assertEquals("Ta teden · This week", ChatArchive.weekTitle(week(LocalDate.of(2026, 9, 28)), today))
        assertEquals("Prejšnji teden · Last week", ChatArchive.weekTitle(week(LocalDate.of(2026, 9, 21)), today))
        assertEquals("14.–20. sept.", ChatArchive.weekTitle(week(LocalDate.of(2026, 9, 14)), today))
        assertEquals("31. avg.–6. sept.", ChatArchive.weekTitle(week(LocalDate.of(2026, 8, 31)), today))
        assertEquals("29. dec. 2025–4. jan. 2026", ChatArchive.weekTitle(week(LocalDate.of(2025, 12, 29)), today))
        assertEquals("22.–28. sept. 2025", ChatArchive.weekTitle(week(LocalDate.of(2025, 9, 22)), today))

        assertEquals("Ponedeljek, 28. septembra", ChatArchive.dayTitle(LocalDate.of(2026, 9, 28), today))
        assertEquals("Nedelja, 27. septembra", ChatArchive.dayTitle(LocalDate.of(2026, 9, 27), today))
        assertEquals("Sreda, 31. decembra 2025", ChatArchive.dayTitle(LocalDate.of(2025, 12, 31), today))
        val args = { d: LocalDate -> mapOf("weekday" to d.dayOfWeek.value.toString(), "date" to Dates.dayMonth(d)) }
        val monday = LocalDate.of(2026, 9, 28)
        assertEquals("Monday, 28 September", L10n.text(Lang.EN, "chatSheet.day", args(monday)))
        assertEquals("Montag, 28. September", L10n.text(Lang.DE, "chatSheet.day", args(monday)))
        assertEquals("Lunedì 28 settembre", L10n.text(Lang.IT, "chatSheet.day", args(monday)))

        assertEquals("Danes · Today", ChatArchive.dayLabel(history.last(), today, ljubljana))
        assertEquals("Ponedeljek, 28. septembra", ChatArchive.dayLabel(history[4], today, ljubljana))
        assertEquals("Starejša sporočila · Older messages", ChatArchive.dayLabel(history[0], today, ljubljana))
    }
}
