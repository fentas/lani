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

    @Test fun `the general rules come first, then the language's own, none of another's`() {
        val p = prefix.system
        assertTrue(p.indexOf("## Correct language, in every language") in 1 until p.indexOf("## Correct Slovene"))
        assertTrue(p.contains("**Grade by the exercise's own accepted answers**") && p.contains("**What you can't check, you don't know.**"))
        assertFalse(p.contains("## Correct Italian") || p.contains("## A child") || p.contains("## Writing") || p.contains("<!--"))
    }

    @Test fun `each language has its own part, with no Slovene rule in it`() {
        for ((code, name, own) in listOf(Triple("it", "Italian", "*sono andato*"), Triple("de", "German", "*Jänner*"), Triple("en", "English", "*whom*"))) {
            val p = Prefix.bundled(PhaseA.load(), Guide(), Bundle::text, PromptLearner(code)).system
            assertTrue(name, p.contains("## Correct $name") && p.contains(own))
            assertTrue(name, p.contains("## Correct language, in every language"))
            assertFalse(name, p.contains("## Correct Slovene") || p.contains("*grem spat*") || p.contains("## A child") || p.contains("## Writing"))
        }
    }

    @Test fun `a child's rules and their language's case end a child's prefix, and only theirs`() {
        val child = Prefix.bundled(PhaseA.load(), Guide(), Bundle::text, PromptLearner("it", child = true)).system
        assertTrue(child.contains("## The learner is a child") && child.contains("Never ask for personal data") && child.contains("## A child learning Italian"))
        // the terminal's sentence is a node's: a phone has no terminal
        assertFalse(child.contains("terminal"))
        assertFalse(prefix.system.contains("## The learner is a child"))
    }

    @Test fun `the studio reads how to write the language it writes`() {
        val sl = StudioSpecs.studioPrefix(StudioSpecs.load(StudioSpecs.STUDIO)).system
        assertTrue(sl.contains("## Writing Slovene") && sl.contains("*fant*") && sl.contains("## Correct Slovene"))
        val it = StudioSpecs.studioPrefix(StudioSpecs.load(StudioSpecs.STUDIO), who = PromptLearner("it")).system
        assertTrue(it.contains("## Writing Italian") && it.contains("*lo zaino*") && it.contains("## Correct Italian"))
        assertFalse(it.contains("*fant*") || it.contains("## Correct Slovene"))
        assertFalse(it.contains("## Correct language, in every language"))
    }

    @Test fun `the guide's topic is the village's, for the learner's base`() {
        val jan = Guide()
        assertTrue(jan.topic("role-play")!!.contains("simple Slovene"))
        val mia = Guide(Bundle::text) { "it" to "de" }
        val rp = mia.topic("role-play", "Mia")!!
        assertTrue(rp, rp.contains("simple Italian") && rp.contains("short German why") && rp.contains("Mia"))
        assertFalse(rp, rp.contains("simple Slovene") || rp.contains("{base"))
        // no copy of its own: the topic as it is
        assertEquals(jan.topic("rhythm"), Guide(Bundle::text) { "it" to "de" }.topic("rhythm"))
        for (lang in listOf("it", "de", "en")) for (t in jan.topics) {
            val text = Guide(Bundle::text) { lang to "sl" }.topic(t.id)!!
            assertFalse("$lang ${t.id}", text.contains("{base"))
        }
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
