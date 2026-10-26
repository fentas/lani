package si.lanisce.lani.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The core prompt as the prefix sends it (the matrix's rules in it, its comments for whoever edits it never), and the
 * turns' texts the matrix made plainer.
 */
class CorePromptTest {
    private val prefix = Prefix.bundled(PhaseA.load(), Guide())

    @Test fun `the comments are for the editor, never sent`() {
        assertTrue(Bundle.text("core-prompt.md")!!.trimStart().startsWith("<!--"))
        assertFalse(prefix.system.contains("<!--") || prefix.system.contains("-->"))
        assertTrue(prefix.system.startsWith("# You are the learner's tutor in Lani"))
        assertEquals("a\nb", Prefix.uncommented("<!--\nnote\n-->\na\n<!-- inline -->b"))
        assertEquals("keep <b>", Prefix.uncommented("keep <!-- x --><b>"))
    }

    @Test fun `the matrix's rules - the card's fault, follow-ups, the score, persisting a session's end`() {
        val p = prefix.system
        assertTrue(p.contains("**A card's fault isn't the learner's.**"))
        assertTrue(p.contains("`overlaps`") && p.contains("`regrade` the answer"))
        assertTrue(p.contains("**A short follow-up goes on with the last topic.**"))
        assertTrue(p.contains("Only an answer's\n  reply carries a score"))
        assertTrue(p.contains("The reply is refused until `record_session` ran."))
        assertTrue(p.contains("Most replies end without a question"))
    }

    @Test fun `correct Slovene - only what is certain, the disputed items wait for the native check`() {
        val p = prefix.system
        assertTrue(p.contains("5. mestnik (locative), 6. orodnik (instrumental)"))
        assertTrue(p.contains("*v Trnovem*") && p.contains("*v mestu*"))
        assertTrue(p.contains("masculine *-la*") && p.contains("feminine and neuter *-li*"))
        assertTrue(p.contains("*u vasi*"))
        // the native speaker's list (the matrix's section 8): not in the prompt
        for (w in listOf("bojo", "nohet", "Pazi se", "Me zebe", "kruh pa", "trebati")) assertFalse(w, p.contains(w))
    }

    @Test fun `the shown replies say outright they aren't the model's`() {
        // GPT-6 Luna "corrected" and retracted the real tutor's replies as its own; Haiku named "the other tutor"
        for (title in listOf(TurnText.SHOWN, TurnText.SHOWN_TODAY)) {
            assertTrue(title.contains("not your words"))
            assertTrue(title.contains("never correct, retract"))
            assertTrue(title.contains("never mention them or another tutor"))
        }
    }

    @Test fun `the digest says a session's end in it isn't persisted yet`() {
        val end = ShadowEvent("ev_end", "session_end", 0, "/message", "main")
        val d = TurnText.digest(listOf(end), "a practice run ended", java.time.ZoneId.of("Europe/Ljubljana"))
        assertTrue(d.contains("except a session_end"))
        assertTrue(d.contains("record_session first"))
    }
}
