package si.lanisce.lani.tutor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** A stream's two `usage` objects merged: the breakdown by lifetime comes in message_start only. */
class UsageMergeTest {
    private fun usage(raw: String) = Json.parseToJsonElement(raw).jsonObject

    @Test fun `the 1-hour part of message_start survives the delta's total`() {
        // as the API sent them for Jan's first call of a thread (2026-10-16, Sonnet 5.5)
        val start = usage(
            """{"input_tokens": 4, "cache_creation_input_tokens": 14407, "cache_read_input_tokens": 0,
               "cache_creation": {"ephemeral_5m_input_tokens": 2074, "ephemeral_1h_input_tokens": 12333}, "output_tokens": 16}""",
        )
        val delta = usage("""{"input_tokens": 4, "cache_creation_input_tokens": 14407, "cache_read_input_tokens": 0, "output_tokens": 121}""")
        val u = Usage.merge(Usage.merge(Usage(), start), delta)
        assertEquals(Usage(input = 4, cacheWrite5m = 2074, cacheWrite1h = 12333, cacheRead = 0, output = 121), u)
    }

    @Test fun `a total alone, with no breakdown before, is a 5-minute write`() {
        val u = Usage.merge(Usage(), usage("""{"input_tokens": 2, "cache_creation_input_tokens": 400, "output_tokens": 9}"""))
        assertEquals(Usage(input = 2, cacheWrite5m = 400, cacheWrite1h = 0, output = 9), u)
    }

    @Test fun `a delta's total below the 1-hour part keeps no more than the total`() {
        val before = Usage(cacheWrite5m = 100, cacheWrite1h = 5000)
        val u = Usage.merge(before, usage("""{"cache_creation_input_tokens": 3000}"""))
        assertEquals(0L, u.cacheWrite5m)
        assertEquals(3000L, u.cacheWrite1h)
    }
}
