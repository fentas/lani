package si.lanisce.lani.hosted

import java.io.File
import java.nio.file.Files
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The hosted mode's wake-ups for the rhythm (hosted/HostedWork): the next moment and what is due, as the bridge's
 * rhythm.ts decides it (each routine once a day, caught up within its window: morning 4 h, evening 3 h, weekly 24 h).
 */
class HostedWorkTest {
    private val zone = ZoneId.of("Europe/Ljubljana")
    private fun at(s: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(s), zone)

    @Test
    fun theNextMomentIsTheSoonest() {
        val r = Rhythm()
        // a Monday morning before 07:30: the morning first
        assertEquals("morning" to at("2026-10-19T07:30"), r.upcoming(at("2026-10-19T06:00")).first())
        // after the morning ran: the evening
        val ran = r.copy(last = mapOf("morning" to "2026-10-19"))
        assertEquals("evening" to at("2026-10-19T20:30"), ran.upcoming(at("2026-10-19T09:00")).first())
        // late in the evening: tomorrow's morning; the week's on Sunday at 19:00
        val up = r.upcoming(at("2026-10-19T22:00"))
        assertEquals("morning" to at("2026-10-20T07:30"), up.first())
        assertEquals(at("2026-10-25T19:00"), up.first { it.first == "weekly" }.second)
        // on a Sunday before 19:00, the week's moment is today
        assertEquals(at("2026-10-25T19:00"), r.upcoming(at("2026-10-25T10:00")).first { it.first == "weekly" }.second)
    }

    @Test
    fun whatIsDueCatchesUpWithinItsWindow() {
        val r = Rhythm()
        assertEquals(listOf("morning"), r.due(at("2026-10-19T08:00")))
        assertEquals(emptyList<String>(), r.due(at("2026-10-19T12:00"))) // more than 4 h late: skipped
        assertEquals(emptyList<String>(), r.copy(last = mapOf("morning" to "2026-10-19")).due(at("2026-10-19T08:00")))
        assertEquals(listOf("evening", "weekly"), r.due(at("2026-10-25T20:45")))
        assertEquals(emptyList<String>(), r.copy(enabled = false).due(at("2026-10-19T08:00")))
        assertEquals(emptyList<String>(), r.copy(enabled = false).upcoming(at("2026-10-19T08:00")))
    }

    @Test
    fun theBridgesFileIsRead() {
        val dir = Files.createTempDirectory("lani-rhythm").toFile()
        try {
            val f = File(dir, "rhythm.json")
            assertEquals(Rhythm(), Rhythm.read(f)) // none: the defaults
            f.writeText("""{"settings":{"enabled":true,"morning":"08:15","evening":null,"weekly_day":3,"weekly":"18:00"},"last":{"morning":"2026-10-19"}}""")
            val r = Rhythm.read(f)
            assertEquals(LocalTime.of(8, 15), r.morning)
            assertEquals(null, r.evening)
            assertEquals(3, r.weeklyDay)
            assertEquals(LocalTime.of(18, 0), r.weekly)
            assertEquals("2026-10-19", r.last["morning"])
            // the evening off: tomorrow's morning, then Wednesday's week
            val up = r.upcoming(at("2026-10-19T09:00"))
            assertEquals(listOf("morning" to at("2026-10-20T08:15"), "weekly" to at("2026-10-21T18:00")), up)
            f.writeText("not json")
            assertEquals(Rhythm(), Rhythm.read(f))
        } finally {
            dir.deleteRecursively()
        }
    }
}

/** The phone bridge's environment in the hosted mode (hosted/HostedRuntime.phoneEnvironment): the lease's phone, the relay. */
class HostedEnvironmentTest {
    @Test
    fun thePhoneIsTheLeasesPhoneAndTheRelayIsShared() {
        val home = Files.createTempDirectory("lani-env").toFile()
        try {
            // no sync settings yet: the phone's side, no device, no relay (the build's is empty)
            assertEquals(mapOf("LANI_SIDE" to "phone", "LANI_STORIES_CHECK_DELAY_MS" to "20000"), HostedRuntime.phoneEnvironment(home, buildRelay = ""))
            // the build's relay: towns and the family page through it
            assertEquals(
                mapOf("LANI_SIDE" to "phone", "LANI_STORIES_CHECK_DELAY_MS" to "20000", "LANI_RELAY_URL" to "https://relay.example", "LANI_TOWN_URL" to "relay"),
                HostedRuntime.phoneEnvironment(home, buildRelay = "https://relay.example/"),
            )
            // the sync's device id, and the relay set on the phone over the build's
            File(home, "sync").mkdirs()
            File(home, "sync/settings.json").writeText("""{"device":"p_0a1b2c3d","relay":"https://mine.example"}""")
            assertEquals(
                mapOf("LANI_SIDE" to "phone", "LANI_STORIES_CHECK_DELAY_MS" to "20000", "LANI_LEASE_DEVICE" to "p_0a1b2c3d", "LANI_RELAY_URL" to "https://mine.example", "LANI_TOWN_URL" to "relay"),
                HostedRuntime.phoneEnvironment(home, buildRelay = "https://relay.example"),
            )
        } finally {
            home.deleteRecursively()
        }
    }
}
