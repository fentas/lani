package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.AskedQuestion
import si.lanisce.lani.data.GuestAnswer
import si.lanisce.lani.data.GuestFrom
import si.lanisce.lani.data.HostAnswer
import si.lanisce.lani.data.ReceivedQuestion
import si.lanisce.lani.data.TownQuestionsList
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair

/**
 * The questions answered between towns in the ties with each (game/TownQuestionTies.kt): both ways, each once, with a
 * line in the chronicle; what isn't answered yet (or hasn't reached the guest's town) doesn't count. The friendship between
 * towns (M5) grows by [TownTies.questions].
 */
class TownQuestionTiesTest {
    private val t0 = noon(day0)
    private val pair = L10n.pair
    private val mia = GuestFrom("b".repeat(32), "Il mio villaggio", "Mia", "friuli", "it")
    private val luka = GuestFrom("c".repeat(32), "Il villaggio di Luka", "Luka", "friuli", "it")

    @After fun back() {
        L10n.pair = pair
    }

    private val list = TownQuestionsList(
        asked = listOf(
            AskedQuestion("${mia.id}:ask-1", "ask-1", mia, text = "Come si chiama il tuo cane?", answer = GuestAnswer("Si chiama Fido.", "2026-09-26T11:00:00Z")),
            AskedQuestion("${mia.id}:ask-2", "ask-2", mia, text = "E il gatto?"), // no answer yet
        ),
        received = listOf(
            ReceivedQuestion("${mia.id}:ask-m1", "ask-m1", mia, text = "Kako je ime tvoji vasi?", answer = HostAnswer("Moja vas.", "2026-09-26T12:00:00Z", sent = true)),
            ReceivedQuestion("${luka.id}:ask-l1", "ask-l1", luka, text = "Kako se reče pes?", answer = HostAnswer("Pes.", "x", sent = false)), // not there yet
            ReceivedQuestion("${luka.id}:ask-l2", "ask-l2", luka, text = "Živjo?"),
        ),
    )

    @Test fun `answered questions count in the ties both ways, once each, with a line in the chronicle`() {
        L10n.pair = LangPair.DEFAULT
        val s0 = GameEngine.newGame(1, t0)
        val s1 = TownQuestionTies.apply(s0, list, day0, t0)
        assertEquals(2, s1.towns[mia.id]?.questions)
        assertEquals("Il mio villaggio", s1.towns[mia.id]?.name)
        assertEquals(null, s1.towns[luka.id]) // Luka's: not answered, or not arrived
        assertTrue(TownQuestionTies.keyOf("${mia.id}:ask-1") in s1.guestbook && TownQuestionTies.keyOf("${mia.id}:ask-m1") in s1.guestbook)
        val lines = s1.log.takeLast(2).map { it.text }
        assertEquals(listOf("Odgovor od Mie (Il mio villaggio) · An answer from Mia (Il mio villaggio)", "Tvoj odgovor za Mio (Il mio villaggio) · You answered Mia (Il mio villaggio)"), lines)
        // again: nothing new
        assertSame(s1, TownQuestionTies.apply(s1, list, day0, t0))
        // Luka's answer arrived: it counts now, once
        val arrived = list.copy(received = list.received.map { if (it.from.id == luka.id && it.answer != null) it.copy(answer = it.answer!!.copy(sent = true)) else it })
        val s2 = TownQuestionTies.apply(s1, arrived, day0, t0)
        assertEquals(1, s2.towns[luka.id]?.questions)
        assertEquals(2, s2.towns[mia.id]?.questions)
    }
}
