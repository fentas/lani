package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's "Danes · Today" reads today's minutes, whether today counts for the streak, and the daily goal. */
class DashboardTodayTest {
    private fun state(sessions: String, goal: String = "\"daily_goal_minutes\": 60,") = """
        {"databases": {
          "learner_profile": {"learner": {"name": "Jan", $goal "current_level": "A1"}, "current_streak_days": 2},
          "session_log": {"sessions": [$sessions]}},
         "computed": {"today": "2026-09-24"}}
    """

    @Test fun `sums today's sessions only`() {
        val d = Dashboard.parse(state("""
            {"date": "2026-09-23", "duration_minutes": 40},
            {"date": "2026-09-24", "duration_minutes": 18},
            {"date": "2026-09-24T19:05:00", "duration_minutes": 12.6}
        """))
        assertEquals(30, d.minutesToday)
        assertTrue(d.practisedToday)
        assertEquals(60, d.goalMinutes)
    }

    @Test fun `nothing today, no goal`() {
        val d = Dashboard.parse(state("""{"date": "2026-09-23", "duration_minutes": 40}""", goal = ""))
        assertEquals(0, d.minutesToday)
        assertFalse(d.practisedToday)
        assertEquals(0, d.goalMinutes)
    }
}
