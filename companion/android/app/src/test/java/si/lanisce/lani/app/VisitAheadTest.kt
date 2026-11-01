package si.lanisce.lani.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.VoiceProfile
import si.lanisce.lani.game.CultureProbes
import si.lanisce.lani.ui.villagers.VillagerLogic
import si.lanisce.lani.game.villagers.Bond
import java.time.LocalDateTime

/**
 * A visit's first lines got ahead (companion/README.md "Voices on a visit"; [VisitAhead]): everyone out greets ("Dober dan!"
 * in a place), then says hello on their card, each in the voice the speaker asks for them (someone's own first), at most
 * [Clips.AHEAD_MAX], each once. A synthetic Slovene town of the Primorska cast at ten in the morning.
 */
class VisitAheadTest {
    private val cast = CultureProbes.cast
    private val morning = LocalDateTime.of(2026, 11, 2, 10, 0)

    @Test fun `the hour's greeting by everyone out first, then each one's hello on their card, in their voices`() {
        val micka = cast.first { it.id == "micka" }
        val janez = cast.first { it.id == "janez" }
        // Micka has a voice of her own on the town's node; Janez speaks in his archetype's
        val profiles = mapOf("micka" to VoiceProfile("@micka", "grandma", status = "own"), "janez" to VoiceProfile("grandpa", "grandpa", pitch = 0.95f))
        val lines = VisitAhead.lines(listOf(micka, janez), emptyList(), null, cast, profiles, morning)
        val hello = "${si.lanisce.lani.ui.greeting(10)}!"
        assertEquals(hello to listOf("@micka"), lines[0])
        assertEquals(hello, lines[1].first)
        assertEquals("grandpa", lines[1].second.first())
        // then what each says on their card to a guest, in the same voices
        val mickaSays = VillagerLogic.greeting(micka, Bond(), morning.toLocalDate(), si.lanisce.lani.game.scene.TimeOfDay.MORNING).map { it.sl }
        assertTrue(mickaSays.isNotEmpty())
        for (t in mickaSays) assertTrue(t, lines.contains(t to listOf("@micka")))
    }

    @Test fun `each line once, at most as many as are got ahead`() {
        val lines = VisitAhead.lines(cast + cast, emptyList(), null, cast, emptyMap(), morning)
        assertTrue(lines.size <= Clips.AHEAD_MAX)
        assertEquals(lines.size, lines.distinctBy { "${si.lanisce.lani.data.Voice.normalize(it.first)}|${it.second.first()}" }.size)
        // no placeholder left in what is asked for, nor anything over what the node voices
        assertTrue(lines.none { '{' in it.first || it.first.length > 300 })
    }
}
