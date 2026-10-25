package si.lanisce.lani.ui.scene

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import si.lanisce.lani.game.TurnMode
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply

/**
 * The learner's own line said in their voice (companion/SCENES.md, "The learner's voice"; Jan, 2026-10-25: "If I select a
 * sentence in a dialog, it should speak it, unless I selected it via the microphone"): a line tapped or typed right is
 * said, then the reply once it has been heard; a line said into the 🎤 isn't said again.
 */
class DialogVoiceTest {
    /** Micka at the market: ten eggs (a form turn: it may be typed or said), then a goodbye. */
    private val market = Dialog(
        id = "jajca",
        lines = listOf(
            DialogLine(who = "micka", sl = "Koliko jajc?", en = "How many eggs?"),
            DialogLine(choices = listOf(
                DialogChoice("Deset jajc, prosim.", "Ten eggs, please.", ok = true, reply = DialogReply("Izvoli.", "Here you are.")),
                DialogChoice("Deset jajce, prosim.", "Ten eggs, please.", why = "From five on, the genitive plural: deset jajc.", grammar = "stevila-samostalniki",
                    reply = DialogReply("Samo eno jajce? Deset?", "Only one egg? Ten?")),
                DialogChoice("Deset jajca, prosim.", "Ten eggs, please.", why = "Jajca is for two, three and four.", grammar = "stevila-samostalniki"),
            )),
            DialogLine(choices = listOf(
                DialogChoice("Nasvidenje!", "Goodbye!", ok = true),
                DialogChoice("Dober dan!", "Hello!", why = "Leaving: nasvidenje."),
            )),
        ),
    )

    private fun start(modes: Map<Int, TurnMode> = emptyMap()) = DialogRun.start(market, "micka", seed = 7, modes = modes)
    private fun DialogRun.right() = choices.indexOfFirst { it.ok }
    private fun DialogRun.choice(sl: String) = choices.indexOfFirst { it.sl == sl }

    /** What the dialog says after [step], as it went from [before]: (who, text), the learner's as "me". */
    private fun said(before: DialogRun, step: DialogRun) = DialogVoice.toSay(step.said, before.said.size).map { (it.who ?: "me") to it.sl }

    @Test fun `a choice tapped is said in the learner's voice, then the reply`() {
        val r = start()
        val tapped = r.choose(r.right())
        assertEquals(Said.By.PICK, tapped.said[1].by)
        assertTrue(tapped.said[1].voiced)
        assertEquals(listOf("me" to "Deset jajc, prosim.", "micka" to "Izvoli."), said(r, tapped))
        // a wrong choice tapped with a reaction is said too: the person answers what they heard
        val wrong = r.choose(r.choice("Deset jajce, prosim."))
        assertEquals(listOf("me" to "Deset jajce, prosim.", "micka" to "Samo eno jajce? Deset?"), said(r, wrong))
        // a choice without a reply, the next turn up at once: the learner's line alone
        val bye = tapped.choose(tapped.right())
        assertEquals(listOf("me" to "Nasvidenje!"), said(tapped, bye))
    }

    @Test fun `a turn typed right is said, a slip typed is not, a form typed that is a wrong choice is that choice said`() {
        val r = start(mapOf(1 to TurnMode.TYPE))
        val typed = r.type("jajc")
        assertEquals(Said.By.TYPE, typed.said[1].by)
        assertEquals(listOf("me" to "Deset jajc, prosim.", "micka" to "Izvoli."), said(r, typed))
        // a form none of the choices has: the learner's own wording, never said (no clip of it), no 🔊; the person puzzled
        val slip = r.type("jajcev")
        assertEquals(Said.By.SLIP, slip.said[1].by)
        assertFalse(slip.said[1].voiced)
        assertFalse(slip.said[1].replayable)
        assertEquals(listOf("micka" to "Hm? Kako, prosim?"), said(r, slip))
        // a wrong choice's form: that choice, said as a wrong pick is
        val asChoice = r.type("jajce")
        assertEquals(listOf("me" to "Deset jajce, prosim.", "micka" to "Samo eno jajce? Deset?"), said(r, asChoice))
    }

    @Test fun `a line said into the microphone is not said again, only the reply, and it keeps its 🔊`() {
        val r = start()
        val heard = r.choose(r.right(), Said.By.MIC)
        assertEquals(Said.By.MIC, heard.said[1].by)
        assertFalse(heard.said[1].voiced)
        assertTrue(heard.said[1].replayable)
        assertEquals(listOf("micka" to "Izvoli."), said(r, heard))
        // a turn said whole (the 🎤 the only way): the same
        val sayTurn = start(mapOf(1 to TurnMode.SAY))
        val spoken = sayTurn.say(listOf("deset jajc prosim"))!!
        assertEquals(Said.By.MIC, spoken.said[1].by)
        assertEquals(listOf("micka" to "Izvoli."), said(sayTurn, spoken))
        // a wrong choice said clearly at a said turn: that choice, not said again either
        val wrongSaid = sayTurn.say(listOf("deset jajce prosim"))!!
        assertEquals(listOf("micka" to "Samo eno jajce? Deset?"), said(sayTurn, wrongSaid))
    }

    @Test fun `an echo gone on from is said, an echo said into the microphone is not`() {
        val echo = Dialog(
            id = "z-mamo",
            lines = listOf(
                DialogLine(who = "micka", sl = "S kom greš?", en = "Who are you going with?"),
                DialogLine(choices = listOf(DialogChoice("Grem z mamo.", "I'm going with mum.", ok = true, reply = DialogReply("Lepo!", "Nice!")))),
            ),
        )
        val r = DialogRun.start(echo, "micka", modes = mapOf(1 to TurnMode.ECHO))
        assertEquals(listOf("me" to "Grem z mamo.", "micka" to "Lepo!"), said(r, r.echo()))
        assertEquals(listOf("micka" to "Lepo!"), said(r, r.echo(listOf("grem z mamo"))!!))
    }

    @Test fun `a dialog just shown says only its newest line when it is someone's, and nothing new says nothing`() {
        val r = start()
        assertEquals(listOf("micka" to "Koliko jajc?"), DialogVoice.toSay(r.said, -1).map { it.who to it.sl })
        val tapped = r.choose(r.right())
        // come back to it (the screen built again): the reply, never the learner's line again
        assertEquals(listOf("micka" to "Izvoli."), DialogVoice.toSay(tapped.said, -1).map { it.who to it.sl })
        val bye = tapped.choose(tapped.right())
        assertTrue(DialogVoice.toSay(bye.said, -1).isEmpty())
        assertTrue(DialogVoice.toSay(tapped.said, tapped.said.size).isEmpty())
        // a dialog that went on by more steps meanwhile: only the newest step's lines
        assertEquals(listOf(null to "Nasvidenje!"), DialogVoice.toSay(bye.said, r.said.size).map { it.who to it.sl })
    }

    @Test fun `the learner's line a moment after it shows, the reply a breath after it, without it the reply comes as before`() {
        val r = start()
        val tapped = r.choose(r.right())
        assertEquals(listOf(DialogMood.SAY_MS, LinesInTurn.GAP_MS), DialogVoice.pauses(tapped, DialogVoice.toSay(tapped.said, r.said.size)))
        val heard = r.choose(r.right(), Said.By.MIC)
        assertEquals(listOf(DialogMood.SAY_AFTER_REACTION_MS), DialogVoice.pauses(heard, DialogVoice.toSay(heard.said, r.said.size)))
    }

    /** A voice that says a line until the test ends it (its clip played to its end). */
    private class FakeVoice {
        val events = mutableListOf<String>()
        private val playing = HashMap<String, CompletableDeferred<Unit>>()

        suspend fun say(who: String, l: Said) {
            val end = CompletableDeferred<Unit>()
            playing[l.sl] = end
            events += "start $who ${l.sl}"
            end.await()
            events += "end $who ${l.sl}"
        }

        fun end(s: String) = playing.getValue(s).complete(Unit)
    }

    private suspend fun until(what: String, f: () -> Boolean) {
        withTimeoutOrNull(5_000) { while (!f()) delay(2) } ?: fail("timed out waiting: $what")
    }

    @Test fun `the reply waits until the learner's line has been heard, and after a 🔊 tapped meanwhile`() = runBlocking {
        val r = start()
        val voice = DialogVoice()
        val v = FakeVoice()
        val quiet = CompletableDeferred<Unit>()
        // the dialog's first line, as it opens
        val first = launch { voice.say(r, { v.say("me", it) }, { v.say(it.who!!, it) }) }
        until("Micka's first line") { v.events.isNotEmpty() }
        assertEquals("micka", voice.talking)
        v.end("Koliko jajc?")
        first.join()
        assertNull(voice.talking)
        // the learner taps the right choice: their line, then Micka's reply
        val tapped = r.choose(r.right())
        val job = launch { voice.say(tapped, { v.say("me", it) }, { v.say(it.who!!, it) }, quiet = { quiet.await() }) }
        until("the learner's line said") { v.events.size == 3 }
        assertEquals("start me Deset jajc, prosim.", v.events.last())
        assertNull("nobody is drawn talking while the learner's line is said", voice.talking)
        delay(LinesInTurn.GAP_MS + 200)
        assertEquals("the reply waits for the learner's line", 3, v.events.size)
        v.end("Deset jajc, prosim.")
        until("the learner's line heard") { v.events.size == 4 }
        delay(LinesInTurn.GAP_MS + 200)
        assertEquals("… and for the 🔊 tapped meanwhile", 4, v.events.size)
        quiet.complete(Unit)
        until("the reply said") { v.events.size == 5 }
        assertEquals(listOf("start micka Koliko jajc?", "end micka Koliko jajc?", "start me Deset jajc, prosim.", "end me Deset jajc, prosim.", "start micka Izvoli."), v.events)
        assertEquals("micka", voice.talking)
        v.end("Izvoli.")
        job.join()
        assertNull(voice.talking)
    }

    @Test fun `a choice said into the microphone, only the reply, no wait for a line of the learner's`() = runBlocking {
        val r = start()
        val voice = DialogVoice()
        val v = FakeVoice()
        voice.say(r, { v.say("me", it) }, { l -> v.events += "said ${l.sl}" })
        v.events.clear()
        val heard = r.choose(r.right(), Said.By.MIC)
        voice.say(heard, { fail("the learner's line said again: ${it.sl}") }, { l -> v.events += "said ${l.sl}" }, quiet = { fail("waited for quiet with no line of the learner's before") })
        assertEquals(listOf("said Izvoli."), v.events)
    }
}
