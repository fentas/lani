package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.AudioTrial.Ask
import si.lanisce.lani.data.AudioTrial.Card
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The trial's state as the device keeps it (docs/plans/05-built/first-minutes.md, "What this leaves for the audio trial"). */
class AudioTrialTest {
    private val start = Instant.parse("2026-11-05T10:00:00Z").toEpochMilli()
    private val end = Instant.parse("2026-11-19T10:00:00Z").toEpochMilli()
    private val day = AudioTrial.DAY_MS
    private val active = TrialAnswer(AudioTrial.ACTIVE, start, end, 14)
    private val running = AudioTrial(id = "a".repeat(32)).answered(active, start)

    @Test fun `an install id is 32 lower-case hex characters, new each time`() {
        val id = AudioTrial.newId()
        assertTrue(id, Regex("[0-9a-f]{32}").matches(id))
        assertNotEquals(id, AudioTrial.newId())
        assertEquals("lani_$id", AudioTrial.token(id))
    }

    @Test fun `the id is made once`() {
        val t = AudioTrial().withId { "first" }
        assertEquals("first", t.id)
        assertEquals("first", t.withId { "second" }.id)
    }

    @Test fun `what the voice service answers is read`() {
        val a = TrialAnswer.parse("""{"status":"active","started_at":"2026-11-05T10:00:00Z","ends_at":"2026-11-19T10:00:00Z","days":14}""")
        assertEquals(active, a)
        assertEquals(AudioTrial.ENDED, TrialAnswer.parse("""{"status":"ended","started_at":"2026-11-05T10:00:00.000Z","ends_at":"2026-11-19T10:00:00.000Z","days":14}""")?.status)
        assertEquals(TrialAnswer(AudioTrial.CLOSED), TrialAnswer.parse("""{"status":"closed"}"""))
        assertEquals(TrialAnswer(AudioTrial.NONE), TrialAnswer.parse("""{"status":"none"}"""))
    }

    @Test fun `an answer that says nothing changes nothing`() {
        for (raw in listOf("", "[]", "{}", """{"status":"maybe"}""", """{"status":"active"}""", """{"status":"active","started_at":"then","ends_at":"later","days":14}""")) {
            assertNull(raw, TrialAnswer.parse(raw))
        }
    }

    @Test fun `it starts when nothing is known, and is read once a day while it lasts`() {
        assertEquals(Ask.START, AudioTrial().ask(start))
        assertNull(running.ask(start))
        assertNull(running.ask(start + day - 1))
        assertEquals(Ask.READ, running.ask(start + day))
        val read = running.answered(active, start + day)
        assertNull(read.ask(start + day + 1))
    }

    @Test fun `closed says nothing and is asked again tomorrow`() {
        val t = AudioTrial(id = "x").answered(TrialAnswer(AudioTrial.CLOSED), start)
        assertEquals(AudioTrial.CLOSED, t.status)
        assertNull(t.ask(start + day - 1))
        assertEquals(Ask.START, t.ask(start + day))
        assertNull(t.card(hosted = true, now = start))
        assertFalse(t.said)
        assertEquals(active.endsAt, t.answered(active, start + day).endsAt)
        assertEquals(AudioTrial.ACTIVE, t.answered(active, start + day).status)
    }

    @Test fun `closed doesn't end one under way`() {
        val t = running.answered(TrialAnswer(AudioTrial.CLOSED), start + day)
        assertEquals(AudioTrial.ACTIVE, t.status)
        assertTrue(t.live(start + day))
        assertNull("read today", t.ask(start + day + 1))
    }

    @Test fun `a trial the voice service doesn't know is started again`() {
        val t = running.answered(TrialAnswer(AudioTrial.NONE), start + day)
        assertEquals(Ask.START, t.ask(start + day))
        assertEquals("the clips it left are still the phone's", start, t.startedAt)
    }

    @Test fun `it lasts until its date, then is over without a word from the voice service`() {
        assertTrue(running.live(start))
        assertTrue(running.live(end - 1))
        assertFalse(running.live(end))
        assertNull("nothing to read past the end", running.ask(end))
        assertEquals(running, running.settled(end - 1))
        assertEquals(AudioTrial.ENDED, running.settled(end).status)
    }

    @Test fun `a 403 from a voice route ends it at once, and the date says so`() {
        val t = running.ended(start + day)
        assertEquals(AudioTrial.ENDED, t.status)
        assertEquals(start + day, t.endsAt)
        assertFalse(t.live(start + day))
        assertEquals("before one nothing ends", AudioTrial(), AudioTrial().ended(start))
    }

    @Test fun `after the end nothing is asked and nothing starts it again`() {
        val t = running.ended(start + day)
        assertNull(t.ask(end + 10 * day))
        assertEquals(t, t.answered(active, end))
        assertEquals(t, t.answered(TrialAnswer(AudioTrial.CLOSED), end))
        assertEquals(t, t.ended(end))
    }

    @Test fun `the voice service saying ended ends it`() {
        val t = running.answered(TrialAnswer(AudioTrial.ENDED, start, end, 14), start + day)
        assertEquals(AudioTrial.ENDED, t.status)
        assertEquals(Card.END, t.card(hosted = true, now = end))
    }

    @Test fun `each card comes once, the end's only after the end`() {
        assertEquals(Card.START, running.card(hosted = true, now = start))
        val seen = running.seen(Card.START)
        assertNull(seen.card(hosted = true, now = start))
        val over = seen.ended(start + day)
        assertEquals(Card.END, over.card(hosted = true, now = start + day))
        assertNull(over.seen(Card.END).card(hosted = true, now = end))
    }

    @Test fun `a learner who never saw the start still reads the end, once`() {
        val over = running.ended(start + day)
        assertEquals(Card.END, over.card(hosted = true, now = start + day))
        assertNull(over.seen(Card.END).card(hosted = true, now = start + day))
    }

    @Test fun `no card on a node`() {
        assertNull(running.card(hosted = false, now = start))
        assertNull(running.ended(start).card(hosted = false, now = start))
        assertNull(AudioTrial().card(hosted = true, now = start))
    }

    @Test fun `the settings speak of a trial that began or ended`() {
        assertFalse(AudioTrial().said)
        assertTrue(running.said)
        assertTrue(running.ended(start).said)
    }

    @Test fun `the end date is the day here`() {
        assertEquals(LocalDate.of(2026, 11, 19), running.endDay(ZoneOffset.UTC))
        assertEquals(LocalDate.of(2026, 11, 20), running.copy(endsAt = Instant.parse("2026-11-19T23:30:00Z").toEpochMilli()).endDay(ZoneOffset.ofHours(1)))
    }

    @Test fun `what is kept on the device reads back`() {
        val t = running.seen(Card.START).ended(start + day)
        assertEquals(t, AudioTrial.decode(t.encode()))
        assertEquals(AudioTrial(), AudioTrial.decode(null))
        assertEquals(AudioTrial(), AudioTrial.decode("not json"))
    }

    @Test fun `a voice route's 403 says the trial ended only in those words`() {
        assertTrue(AudioTrial.endedBy("""{"error":{"code":"trial_ended","message":"over"}}"""))
        for (raw in listOf("", "<Error><Code>AccessDenied</Code></Error>", """{"error":{"code":"forbidden"}}""", """{"code":"trial_ended"}""")) {
            assertFalse(raw, AudioTrial.endedBy(raw))
        }
    }
}
