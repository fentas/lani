package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Adaptive
import si.lanisce.lani.game.Confusion
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.Traps
import si.lanisce.lani.game.TurnMode
import si.lanisce.lani.game.scene.ActCue
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.game.scene.Placement
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.ui.scene.DialogRun.Step

/**
 * Tap turns and stage directions in a dialog (companion/SCENES.md, "Tap turns", "Stage directions"): a turn answered by
 * tapping the picture, graded as a pick; where everyone stands after each step; what passes with a reaction.
 */
class DialogTapTest {
    private val seek = Dialog(
        id = "skrivalnice",
        lines = listOf(
            DialogLine(who = "zala", sl = "Jan, se greva skrivalnice? Ti mižiš, jaz se skrijem!", en = "Shall we play hide-and-seek?"),
            DialogLine(choices = listOf(
                DialogChoice("… devet, deset! Grem iskat!", "… nine, ten! Here I come!", ok = true,
                    reply = DialogReply("Skrila sem se! Kje sem?", "I've hidden! Where am I?", act = mapOf("zala" to ActCue("behind-door", "hide")))),
                DialogChoice("… devet, deset! Greš iskat!", "… nine, ten! You're coming!", why = "You seek: grem.", grammar = "glagoli-sedanjik",
                    reply = DialogReply("Jaz? Ne, ne, ti iščeš!", "Me? No, you seek!")),
            )),
            DialogLine(choices = listOf(
                DialogChoice("Aha, za vrati si!", "Aha, you're behind the door!", ok = true, tap = "behind-door", grammar = "kje-mestnik-orodnik",
                    reply = DialogReply("Našel si me!", "You found me!", act = mapOf("zala" to ActCue(to = "door")))),
                DialogChoice("Aha, pod mizo si!", "Aha, you're under the table!", why = "Look for a clue.", tap = "under-table",
                    reply = DialogReply("Tu me ni! Hi hi!", "I'm not here! Hee hee!", act = mapOf("zala" to ActCue(pose = "peek")))),
                DialogChoice("Aha, za pečjo si!", "Aha, you're behind the stove!", why = "Look for a clue.", tap = "stove",
                    reply = DialogReply("Mrzlo, mrzlo!", "Cold, cold!")),
            )),
            DialogLine(who = "zala", sl = "Videl si mojo kito, kajne?", en = "You saw my braid, didn't you?"),
        ),
    )
    private val base = mapOf("zala" to Placement.at("stove"), "janez" to Placement.at("table"))

    /** At the tap turn, the choices shuffled by [seed]. */
    private fun atTap(seed: Long? = 7) = DialogRun.start(seek, "zala", seed = seed, modes = DialogRun.tapModes(seek)).let { r ->
        r.choose(r.choices.indexOfFirst { it.ok }) // past the count (the first turn, as shown)
    }

    @Test fun `a tap turn is asked by tapping the picture, its places are what its choices stand for`() {
        assertEquals(mapOf(2 to TurnMode.TAP), DialogRun.tapModes(seek))
        val r = atTap()
        assertEquals(Step.CHOOSE, r.step)
        assertEquals(TurnMode.TAP, r.mode)
        assertEquals(listOf("behind-door", "under-table", "stove"), r.taps)
        assertEquals(emptyList<String>(), DialogRun.start(seek, "zala").taps) // the count isn't one
        // "✋ Let me choose": the same turn, its choices; typing does nothing to a tap turn
        assertEquals(TurnMode.CHOOSE, r.letMeChoose().mode)
        assertSame(r, r.type("za vrati"))
    }

    @Test fun `tapping the right place is picking its choice, whatever order the choices show in`() {
        for (seed in 1L..12L) {
            val r = atTap(seed)
            val found = r.tap("behind-door")
            assertEquals(Said(null, "Aha, za vrati si!", "Aha, you're behind the door!"), found.said[found.said.size - 2])
            assertEquals(Said("zala", "Našel si me!", "You found me!"), found.said.last())
            assertEquals(listOf(0, 0), found.picks) // the file's choice, as a tap on it would be
            assertEquals(found, r.choose(r.choices.indexOfFirst { it.tap == "behind-door" }))
        }
    }

    @Test fun `a wrong place is that wrong choice, said, the reaction, the why, a mistake once, a place no choice is changes nothing`() {
        val r = atTap()
        val miss = r.tap("under-table")
        assertEquals(Step.CHOOSE, miss.step)
        assertEquals(Said(null, "Aha, pod mizo si!", "Aha, you're under the table!", wrong = true), miss.said[miss.said.size - 2])
        assertEquals(Said("zala", "Tu me ni! Hi hi!", "I'm not here! Hee hee!"), miss.said.last())
        assertEquals("Look for a clue.", miss.why)
        assertEquals(r.mistakes + 1, miss.mistakes)
        assertSame(miss, miss.tap("under-table")) // the same wrong place again doesn't count twice
        assertSame(r, r.tap("clock"))
        assertEquals(TurnMode.TAP, miss.mode) // still tapping
    }

    @Test fun `a right first tap counts on the page its right choice names, a wrong place is no slip on a rule`() {
        val r = atTap()
        assertEquals(listOf(DialogRun.RuleAnswer("kje-mestnik-orodnik", true, "Aha, za vrati si!", null)), r.answersOf(r.tap("behind-door")))
        val miss = r.tap("stove")
        assertEquals(emptyList<DialogRun.RuleAnswer>(), r.answersOf(miss))
        assertNull(miss.rule)
        assertEquals(emptyList<DialogRun.RuleAnswer>(), miss.answersOf(miss.tap("behind-door"))) // right after a miss: nothing counted
    }

    @Test fun `where everyone is after each step, hidden with the count's reply, peeking with a reaction until the next pick, out when found`() {
        val start = DialogRun.start(seek, "zala", seed = 7, modes = DialogRun.tapModes(seek))
        assertEquals(emptyMap<String, Placement>(), start.act(base))
        val hidden = atTap()
        assertEquals(mapOf("zala" to Placement("behind-door", "hide")), hidden.act(base))
        val peek = hidden.tap("under-table")
        assertEquals(mapOf("zala" to Placement("behind-door", "peek")), peek.act(base))
        val cold = peek.tap("stove") // another reaction: its own (none), the peek gone
        assertEquals(mapOf("zala" to Placement("behind-door", "hide")), cold.act(base))
        val found = cold.tap("behind-door")
        assertEquals(mapOf("zala" to Placement("door", "stand")), found.act(base))
        assertEquals(mapOf("zala" to Placement("door", "stand")), found.next().act(base)) // to the end
    }

    @Test fun `the person reacts to a tap as to a pick, puzzled at a wrong place they answer, a hop at the right one`() {
        val r = atTap()
        val miss = r.tap("under-table")
        assertEquals(Pose.THINK, DialogMood.react(r, miss, Mood.NONE, 1000L).pose)
        assertEquals(Pose.HAPPY, DialogMood.react(miss, miss.tap("behind-door"), Mood.NONE, 2000L).pose)
    }

    @Test fun `a tap turn is chosen, never typed, and gets no trap`() {
        val levels = { _: String -> Mastery.MASTERED }
        // its choices name pages, and differ from each other in a word or two: still never typed or said
        val formLike = seek.copy(lines = seek.lines.take(2) + DialogLine(choices = listOf(
            DialogChoice("Za vrati si!", "Behind the door!", ok = true, tap = "behind-door"),
            DialogChoice("Za pečjo si!", "Behind the stove!", why = "Look.", tap = "stove", grammar = "kje-mestnik-orodnik"),
        )))
        assertTrue(2 !in Adaptive.modes(formLike, levels, canSay = true))
        val c = Confusion("vrata", "vrati", "za_orodnik", "za vrata" to "za vrati")
        assertEquals(formLike.lines[2], Traps.dialog(formLike, listOf(c), { setOf("kje-mestnik-orodnik") }, { true }, DialogReply("Hm?", "Hm?"), { "why" }).lines[2])
    }
}
