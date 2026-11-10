package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The streak's rule ([Streak.of], companion/GAME.md "The streak") against the shared cases of
 * bridge/test/fixtures/streak-cases.json, which the bridge (src/streak.ts, the smoke's streak section) and the scripts
 * (.claude/hooks/lani_streak.py, tests/test_streak.py) read too: so the app, the tutor and the hooks can't say two
 * streaks. Each case's days are the session log's dates as they are (a timestamp, an unpadded date, what isn't a date).
 */
class StreakParityTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "bridge/test/fixtures").isDirectory }
    private val fixture = Json.parseToJsonElement(File(companion, "bridge/test/fixtures/streak-cases.json").readText()).jsonObject

    /** A session's date as the session log holds it: a string, or anything else (which is no date). */
    private fun raw(e: JsonElement): String? = (e as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun expected(o: JsonObject) = Streaks(
        current = o["current"]!!.jsonPrimitive.int,
        best = o["best"]!!.jsonPrimitive.int,
        practisedToday = o["practised_today"]!!.jsonPrimitive.boolean,
        freezes = o["freezes"]!!.jsonPrimitive.int,
        covered = o["covered"]!!.jsonArray.map { LocalDate.parse(it.jsonPrimitive.content) },
        since = o["since"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.let(LocalDate::parse),
        todayCovered = o["today_covered"]!!.jsonPrimitive.boolean,
        lastRun = o["last_run"]!!.jsonPrimitive.int,
        toNextFreeze = o["to_next_freeze"]!!.jsonPrimitive.int,
    )

    @Test fun `the rule is the shared one`() {
        val rule = fixture["rule"]!!.jsonObject
        assertEquals(Streak.START_FREEZES, rule["start_freezes"]!!.jsonPrimitive.int)
        assertEquals(Streak.EARN_EVERY, rule["earn_every"]!!.jsonPrimitive.int)
        assertEquals(Streak.MAX_FREEZES, rule["max_freezes"]!!.jsonPrimitive.int)
    }

    @Test fun `every shared case answers as the fixture says`() {
        val cases = (fixture["cases"] as JsonArray).map { it.jsonObject }
        assertTrue("the fixture has its cases", cases.size >= 20)
        val wrong = cases.mapNotNull { c ->
            val got = Streak.ofDates(c["days"]!!.jsonArray.map(::raw), LocalDate.parse(c["today"]!!.jsonPrimitive.content))
            val want = expected(c["expect"]!!.jsonObject)
            if (got == want) null else "${c["name"]!!.jsonPrimitive.content}:\n  got  $got\n  want $want"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun `the morning after, the days a freeze kept the run`() {
        val today = LocalDate.of(2026, 11, 10)
        val week = (1..7).map { LocalDate.of(2026, 11, it) }
        val two = Streak.of(week, today)
        assertEquals(listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9)), two.keptToday(today))
        assertTrue(two.isCovered(LocalDate.of(2026, 11, 9)))
        // practised today, or the run over: nothing kept today
        assertTrue(Streak.of(week + today, today).keptToday(today).isEmpty())
        assertTrue(Streak.of(listOf(LocalDate.of(2026, 11, 5)), today).keptToday(today).isEmpty())
        // a covered day with practice after it is no longer the morning after
        assertTrue(Streak.of(listOf(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 9)), today).keptToday(today).isEmpty())
    }

    @Test fun `a session's day as the scripts read one`() {
        assertEquals(LocalDate.of(2026, 4, 24), Streak.dayOf("2026-4-24"))
        assertEquals(LocalDate.of(2026, 11, 8), Streak.dayOf("2026-11-08T21:30:00"))
        assertEquals(LocalDate.of(2026, 11, 7), Streak.dayOf("2026-11- 7"))
        for (bad in listOf(null, "", "garbage", "2026-02-30", "0000-01-01", "2026-13-01", "2026-011-01", " 2026-11-01")) {
            assertEquals(bad, null, Streak.dayOf(bad))
        }
    }
}
