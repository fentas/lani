package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonObject
import java.time.DayOfWeek
import java.time.LocalDate

class StatsTest {
    private val today = LocalDate.of(2026, 9, 23) // a Wednesday
    private fun d(s: String) = LocalDate.parse(s)
    private fun days(vararg s: String) = s.map(::d).toSet()

    // --- streaks ---

    @Test fun `no practice is no streak`() {
        assertEquals(Streaks(0, 0, false), Stats.streaks(emptySet(), today))
    }

    @Test fun `streak counts back from today`() {
        assertEquals(Streaks(3, 3, true), Stats.streaks(days("2026-09-21", "2026-09-22", "2026-09-23"), today))
    }

    @Test fun `today not yet practised keeps yesterday's streak alive`() {
        assertEquals(Streaks(2, 2, false), Stats.streaks(days("2026-09-21", "2026-09-22"), today))
    }

    @Test fun `a missed day breaks the streak`() {
        assertEquals(Streaks(0, 2, false), Stats.streaks(days("2026-09-20", "2026-09-21"), today))
    }

    @Test fun `best streak survives gaps`() {
        val s = Stats.streaks(days("2026-08-01", "2026-08-02", "2026-08-03", "2026-08-04", "2026-08-10", "2026-09-22", "2026-09-23"), today)
        assertEquals(2, s.current)
        assertEquals(4, s.best)
    }

    @Test fun `future dates don't count`() {
        assertEquals(Streaks(1, 1, true), Stats.streaks(days("2026-09-23", "2026-09-25", "2026-09-26"), today))
    }

    // --- calendar and weekly buckets ---

    @Test fun `several sessions a day are summed`() {
        val m = Stats.daily(listOf(DayActivity(today, 6, 1, 1), DayActivity(today, 10, 20, 1), DayActivity(today.minusDays(1), 5, 3, 1)))
        assertEquals(DayActivity(today, 16, 21, 2), m[today])
        assertEquals(2, m.size)
    }

    @Test fun `calendar is 12 Monday-first weeks ending this week, future days empty`() {
        val c = Stats.calendar(mapOf(today to DayActivity(today, 16, 21, 2)), today)
        assertEquals(12, c.size)
        assertTrue(c.all { it.size == 7 })
        assertEquals(DayOfWeek.MONDAY, c.first().first()!!.date.dayOfWeek)
        assertEquals(d("2026-07-06"), c.first().first()!!.date)
        val last = c.last()
        assertEquals(DayActivity(today, 16, 21, 2), last[2])
        assertNull(last[3]) // Thursday hasn't happened yet
        assertEquals(0, last[0]!!.minutes)
    }

    @Test fun `weekly buckets include empty weeks and split on Monday`() {
        val m = Stats.daily(
            listOf(
                DayActivity(d("2026-09-20"), 10, 5, 1), // Sunday: previous week
                DayActivity(d("2026-09-21"), 6, 4, 1), // Monday
                DayActivity(d("2026-09-23"), 10, 20, 1),
                DayActivity(d("2026-08-26"), 15, 10, 1),
            ),
        )
        val w = Stats.weekly(m, today, weeks = 5)
        assertEquals(listOf("2026-08-24", "2026-08-31", "2026-09-07", "2026-09-14", "2026-09-21").map(::d), w.map { it.start })
        assertEquals(listOf(10, 0, 0, 5, 24), w.map { it.exercises })
        assertEquals(WeekBucket(d("2026-09-21"), 24, 16, 2), w.last())
    }

    @Test fun `heat intensity by minutes`() {
        assertEquals(0, Stats.intensity(null))
        assertEquals(0, Stats.intensity(DayActivity(today, 0, 0, 0)))
        assertEquals(1, Stats.intensity(DayActivity(today, 0, 3, 1))) // exercises without minutes still show
        assertEquals(1, Stats.intensity(DayActivity(today, 9, 3, 1)))
        assertEquals(2, Stats.intensity(DayActivity(today, 10, 3, 1)))
        assertEquals(3, Stats.intensity(DayActivity(today, 25, 3, 1)))
        assertEquals(4, Stats.intensity(DayActivity(today, 60, 3, 1)))
    }

    // --- words and projection ---

    @Test fun `words trend is cumulative per week`() {
        val created = listOf("2026-08-26", "2026-08-26", "2026-08-27", "2026-09-23").map(::d)
        assertEquals(listOf(3, 3, 3, 3, 4), Stats.wordsTrend(created, today, weeks = 5))
        assertEquals(listOf(0, 3, 3), Stats.wordsTrend(created, d("2026-09-01"), weeks = 3)) // weeks of 17 Aug, 24 Aug, 31 Aug
    }

    @Test fun `projection needs two weeks of history`() {
        val created = List(40) { today.minusDays((it % 10).toLong()) }
        assertEquals(Projection.NotEnoughData, Stats.projection(40, 300, created, today))
    }

    @Test fun `projection needs recent words`() {
        val created = List(30) { d("2026-06-01") } + listOf(today, today)
        assertEquals(Projection.NotEnoughData, Stats.projection(32, 300, created, today))
    }

    @Test fun `no words is not enough data`() {
        assertEquals(Projection.NotEnoughData, Stats.projection(0, 300, emptyList(), today))
    }

    @Test fun `projection extrapolates the recent pace`() {
        // 40 words in the last 28 days = 10 per week; 200 to go = 20 weeks.
        val created = List(60) { d("2026-07-01") } + List(40) { today.minusDays((it % 28).toLong()) }
        val p = Stats.projection(100, 300, created, today)
        assertEquals(Projection.Eta(today.plusDays(140), 10.0), p)
    }

    @Test fun `a very slow pace says too far instead of a wild date`() {
        val created = List(5) { today.minusDays(it.toLong()) } + d("2026-01-01")
        val p = Stats.projection(6, 8000, created, today)
        assertTrue(p is Projection.TooFar)
    }

    @Test fun `goal already reached`() {
        assertEquals(Projection.Reached, Stats.projection(300, 300, emptyList(), today))
    }

    // --- due forecast and mistakes ---

    @Test fun `due forecast folds overdue into today`() {
        val due = listOf("2026-08-28", "2026-09-22", "2026-09-23", "2026-09-24", "2026-09-24", "2026-09-29", "2026-09-30").map(::d)
        val f = Stats.dueForecast(due, today)
        assertEquals(7, f.size)
        assertEquals(today, f.first().date)
        assertEquals(listOf(3, 2, 0, 0, 0, 0, 1), f.map { it.count })
    }

    @Test fun `mistake trend`() {
        assertEquals(Trend.BETTER, Stats.trend(1, 0, listOf(today), today))
        assertEquals(Trend.WORSE, Stats.trend(0, 2, emptyList(), today))
        assertEquals(Trend.WORSE, Stats.trend(0, 1, listOf(today.minusDays(2)), today))
        assertEquals(Trend.STEADY, Stats.trend(0, 1, listOf(d("2026-08-26")), today))
        assertEquals(Trend.STEADY, Stats.trend(0, 1, listOf(today.minusDays(3), today.minusDays(20)), today))
    }

    @Test fun `accuracy on the same date is weighted by exercises`() {
        val a = Stats.accuracy(listOf(AccuracyPoint(today, 1f, 1), AccuracyPoint(today, 0.5f, 3), AccuracyPoint(today.minusDays(3), 0.7f, 10)))
        assertEquals(listOf(today.minusDays(3), today), a.map { it.date })
        assertEquals(0.625f, a.last().accuracy, 1e-4f)
        assertEquals(4, a.last().exercises)
    }

    @Test fun `next level and goal`() {
        assertEquals("A2", Stats.nextLevel("A1"))
        assertEquals(300, Stats.vocabGoal("A1"))
        assertEquals(1000, Stats.vocabGoal("A2"))
        assertEquals("C2", Stats.nextLevel("C2"))
    }

    // --- parsing a real /state ---

    /** Trimmed copy of a real GET /state (read-db.py output). */
    private val state = """
        {"databases":{
          "learner_profile":{"learner":{"name":"Jan","current_level":"A1","target_level":"B2"},"current_streak_days":1},
          "progress_db":{"accuracy_trend":[
            {"date":"2026-08-26","accuracy":0.7,"exercises":10},{"date":"2026-08-27","accuracy":0.8,"exercises":10},
            {"date":"2026-09-23","accuracy":0.95,"exercises":21}]},
          "session_log":{"sessions":[
            {"session_id":"session-001","date":"2026-08-26","duration_minutes":15,"exercises_completed":10,"accuracy":0.7},
            {"session_id":"session-002","date":"2026-08-27","duration_minutes":20,"exercises_completed":10,"accuracy":0.8},
            {"session_id":"session-003","date":"2026-09-23","duration_minutes":6,"exercises_completed":1,"accuracy":1.0},
            {"session_id":"session-004","date":"2026-09-23","duration_minutes":10,"exercises_completed":20,"accuracy":0.95}]},
          "mastery_db":{"skills":{
            "writing":{"mastery_level":1,"confidence_score":0.667,"last_practiced":"2026-08-27"},
            "speaking":{"mastery_level":1,"confidence_score":0.667,"last_practiced":"2026-08-26"},
            "vocabulary":{"mastery_level":2,"confidence_score":0.88,"last_practiced":"2026-09-23"},
            "reading":{"mastery_level":0,"confidence_score":0.0,"last_practiced":null},
            "listening":{"mastery_level":0,"confidence_score":0.0,"last_practiced":null}}},
          "spaced_repetition":{"items":{
            "vocab_dober_dan":{"type":"vocabulary","content":"dober dan","answer":"good day","created_date":"2026-08-26","due_date":"2026-09-29","repetitions":2},
            "vocab_hvala":{"type":"vocabulary","content":"hvala","answer":"thanks","created_date":"2026-08-26","due_date":"2026-09-24","repetitions":0,"mastery_level":1},
            "vocab_ampak":{"type":"vocabulary","content":"ampak","answer":"but","created_date":"2026-09-23","due_date":"2026-09-23","repetitions":0},
            "missing_copula_je":{"type":"error_pattern","content":"moja partnerka iz Gorice","answer":"moja partnerka je iz Gorice","due_date":"2026-08-28"}}},
          "mistakes_db":{"error_patterns":{
            "register_formal_informal":{"category":"grammar","severity":"moderate","frequency":1,"last_seen":"2026-08-26",
              "consecutive_correct":0,"consecutive_incorrect":1,"examples":[{"incorrect":"zivjo (to a stranger)","correct":"Dober dan","date":"2026-08-26"}]},
            "clitic_placement_se":{"category":"syntax","severity":"critical","frequency":3,"last_seen":"2026-08-27","description":"",
              "consecutive_correct":0,"consecutive_incorrect":2,"examples":[{"incorrect":"imenujem jan se","correct":"Imenujem se Jan","date":"2026-08-27"}]}}}
        },"computed":{"today":"2026-09-23","due_reviews_count":2}}
    """.trimIndent()

    @Test fun `parses the learner databases`() {
        val p = Stats.parse(state, fallbackToday = d("2030-01-01"))
        assertEquals(today, p.today) // the node's today wins
        assertEquals("Jan", p.name)
        assertEquals("A2", p.nextLevel)
        // learned: answered right at least once (repetitions or mastery); "ampak" is tracked, not learned yet
        assertEquals(2, p.wordsKnown)
        assertEquals(3, p.wordsTracked) // vocabulary only, not the error pattern
        assertEquals(2, p.wordsTrend.last())
        assertEquals(2, p.wordsTrend[p.wordsTrend.size - 2])
        assertEquals(Projection.NotEnoughData, p.projection) // no word learned recently is no pace
        assertEquals(Streaks(1, 2, true), p.streaks)
        assertEquals(51, p.totalMinutes)
        assertEquals(DayActivity(today, 16, 21, 2), p.calendar.last()[2])
        assertEquals(listOf(0.7f, 0.8f, 0.95f), p.accuracy.map { it.accuracy })
        assertEquals(Stats.SKILL_ORDER, p.skills.map { it.skill })
        assertEquals(SkillMastery("vocabulary", 2, 0.88f, today), p.skills[2])
        assertEquals(listOf("clitic_placement_se", "register_formal_informal"), p.mistakes.map { it.id })
        val top = p.mistakes.first()
        assertEquals("Clitic placement se", top.label) // blank description falls back to the id
        assertEquals(Trend.WORSE, top.trend)
        assertEquals("imenujem jan se", top.wrong)
        assertEquals(listOf(1, 1, 0, 0, 0, 0, 1), p.due.map { it.count }) // the error pattern isn't a card
        assertEquals(0, p.overdue)
        assertEquals(21, p.weeks.last().exercises)
    }

    @Test fun `parses an empty state`() {
        val p = Stats.parse("""{"databases":{}}""", today)
        assertEquals(0, p.wordsKnown)
        assertEquals(Projection.NotEnoughData, p.projection)
        assertEquals(Streaks(0, 0, false), p.streaks)
        assertEquals(5, p.skills.size)
        assertTrue(p.mistakes.isEmpty())
    }
    @Test fun `a word answered right while learning it counts at once, a looked-up or failed one doesn't`() {
        fun item(json: String) = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
        // learned in a pack, answered right (quality 4), first review tomorrow
        assertTrue(Stats.learned(item("""{"repetitions":0,"total_reviews":0,"last_quality":4}""")))
        // learned in a pack, answered wrong
        assertTrue(!Stats.learned(item("""{"repetitions":0,"total_reviews":0,"last_quality":2}""")))
        // added from the word card: never answered
        assertTrue(!Stats.learned(item("""{"repetitions":0,"total_reviews":0,"last_quality":3,"source":"lookup"}""")))
        // reviewed and failed since: SM-2 reset its repetitions
        assertTrue(!Stats.learned(item("""{"repetitions":0,"total_reviews":2,"last_quality":1}""")))
        // reviewed right
        assertTrue(Stats.learned(item("""{"repetitions":1,"total_reviews":1,"last_quality":4}""")))
    }

    @Test fun `a word whose last review failed is rusty until a review gets it right`() {
        fun item(json: String) = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
        // failed its last review: SM-2 reset its repetitions and kept the low quality
        val failed = item("""{"repetitions":0,"total_reviews":3,"last_quality":1,"mastery_level":1}""")
        assertTrue(Stats.rusty(failed))
        assertTrue("not learned, whatever its mastery", !Stats.learned(failed))
        // right again (the next day's review, or polished at once): repetitions 1
        val relearned = item("""{"repetitions":1,"total_reviews":4,"last_quality":4,"mastery_level":1}""")
        assertTrue(!Stats.rusty(relearned))
        assertTrue(Stats.learned(relearned))
        // almost right counts as right (quality 3)
        assertTrue(!Stats.rusty(item("""{"repetitions":1,"total_reviews":1,"last_quality":3}""")))
        // never reviewed: answered wrong while learning it in a pack is "not learned yet", not rusty
        assertTrue(!Stats.rusty(item("""{"repetitions":0,"total_reviews":0,"last_quality":2}""")))
        // looked up in a dialog: never learned, never rusty
        assertTrue(!Stats.rusty(item("""{"repetitions":0,"total_reviews":1,"last_quality":1,"source":"lookup"}""")))
        // no quality stored: nothing to say it failed
        assertTrue(!Stats.rusty(item("""{"repetitions":0,"total_reviews":2}""")))
    }
}
