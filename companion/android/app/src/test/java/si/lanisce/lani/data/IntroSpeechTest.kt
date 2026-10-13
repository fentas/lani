package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.IntroSpeech.Line
import si.lanisce.lani.data.IntroSpeech.Mark
import si.lanisce.lani.data.IntroSpeech.Run
import si.lanisce.lani.l10n.Lang

class IntroSpeechTest {
    /** The app's check for Jan's pair without clips ([Speaker.isSlovene]: Spoken.looks). */
    private val looks: (String) -> Boolean = { Spoken.looks(it, Lang.SL, Lang.EN) }

    /** The first step of the unit biti, in English (companion/course/sl/units/biti.json). */
    private val biti = "**Biti** (to be) has a short form for each person, and Slovene never leaves it out. One person first: " +
        "**jaz sem** (I am), **ti si** (you are), **on, ona, ono je** (he, she, it is). The pronoun is usually dropped: the form already says who."

    /** The unit's own words: its title in Slovene, its table ([IntroSpeech.vocabulary] gathers them). */
    private val known = setOf("biti", "sem", "si", "je", "jaz", "ti", "on", "ona", "ono")
    private val slovene: (String) -> Boolean = { looks(it) || IntroSpeech.within(it, known) }

    @Test fun `the biti step in its runs, the forms in the teacher's voice, the English between them by the phone's, in order`() {
        assertEquals(
            listOf(
                Run("Biti", true, 0, 0..0),
                Run("(to be) has a short form for each person, and Slovene never leaves it out. One person first:", false, 0, 1..1),
                Run("jaz sem", true, 0, 2..2),
                Run("(I am),", false, 0, 3..3),
                Run("ti si", true, 0, 4..4),
                Run("(you are),", false, 0, 5..5),
                Run("on, ona, ono je", true, 0, 6..6),
                Run("(he, she, it is). The pronoun is usually dropped: the form already says who.", false, 0, 7..7),
            ),
            IntroSpeech.runs(biti, slovene),
        )
        // the app's check alone doesn't know "Biti": the intro's own words do
        assertFalse(looks("Biti"))
        assertEquals(Run("Biti (to be) has a short form for each person, and Slovene never leaves it out. One person first:", false, 0, 0..1), IntroSpeech.runs(biti, looks).first())
    }

    @Test fun `the explanation as the node voices it, its text without the marks, the Slovene inside it, keyed apart from the lines`() {
        val plain = IntroSpeech.plain(biti)
        assertEquals(
            "Biti (to be) has a short form for each person, and Slovene never leaves it out. One person first: jaz sem (I am), ti si (you are), " +
                "on, ona, ono je (he, she, it is). The pronoun is usually dropped: the form already says who.",
            plain,
        )
        val key = IntroSpeech.key("en", plain)
        assertTrue(key, key.startsWith("en|biti to be has a short form"))
        // a line's key never has a "|": an explanation never stands in for a Slovene line, nor the other way
        assertFalse(Voice.normalize("Biti | to be").contains("|"))
        assertEquals(listOf("/e.mp3"), Clips.resolve(mapOf(key to mapOf("teacher" to "/e.mp3")), plain, "teacher", lang = "en"))
        assertNull(Clips.resolve(mapOf(key to mapOf("teacher" to "/e.mp3")), plain, "teacher"))
        assertNull(Clips.resolve(mapOf(Voice.normalize(plain) to mapOf("teacher" to "/l.mp3")), plain, "teacher", lang = "en"))
        // the same explanation in German is another clip
        assertFalse(IntroSpeech.key("de", plain) == key)
    }

    @Test fun `quotes and italics are spans too, English in bold stays English`() {
        val say = "Say „Dober dan“ in the day, *dober večer* at night, and **hello** is English."
        assertEquals(
            listOf(
                Run("Say", false, 0, 0..0),
                Run("Dober dan", true, 0, 1..1),
                Run("in the day,", false, 0, 2..2),
                Run("dober večer", true, 0, 3..3),
                Run("at night, and hello is English.", false, 0, 4..6),
            ),
            IntroSpeech.runs(say, looks),
        )
        assertEquals("Say Dober dan in the day, dober večer at night, and hello is English.", IntroSpeech.plain(say))
    }

    @Test fun `a sentence without Slovene is one run, a code span stays in it`() {
        assertEquals(listOf(Run("This step has no Slovene at all, see -l.", false, 0, 0..2)), IntroSpeech.runs("This step has no Slovene at all, see `-l`.", looks))
        assertEquals(emptyList<Run>(), IntroSpeech.runs("  \n\n", looks))
    }

    @Test fun `lines by their source index, a list's items, no table, no emoji, nothing left to say left out`() {
        val say = "Two forms:\n- **sem** (I am) 🙂\n\n| a | b |\n|---|---|\n- **si**\n---\n> **smo, ste, so**"
        assertEquals(
            listOf(
                Run("Two forms:", false, 0, 0..0),
                Run("sem", true, 1, 0..0),
                Run("(I am)", false, 1, 1..1),
                Run("si", true, 5, 0..0),
                Run("smo, ste, so", true, 7, 0..0),
            ),
            IntroSpeech.runs(say, looks),
        )
        assertEquals("Two forms:\nsem (I am)\nsi\nsmo, ste, so", IntroSpeech.plain(say))
        // ", " between two forms: nothing to say
        assertEquals(listOf("jaz sem", "ti si"), IntroSpeech.runs("**jaz sem**, **ti si**", looks).map { it.text })
    }

    @Test fun `the segments are the Markdown's own, in order`() {
        val segs = IntroSpeech.segments("A **b** c *d* `e` „f“")
        assertEquals(listOf("A ", "b", " c ", "d", " ", "e", " ", "f"), segs.map { it.body })
        assertEquals(
            listOf(IntroSpeech.Segment.Kind.PLAIN, IntroSpeech.Segment.Kind.BOLD, IntroSpeech.Segment.Kind.PLAIN, IntroSpeech.Segment.Kind.ITALIC, IntroSpeech.Segment.Kind.PLAIN, IntroSpeech.Segment.Kind.CODE, IntroSpeech.Segment.Kind.PLAIN, IntroSpeech.Segment.Kind.QUOTED),
            segs.map { it.kind },
        )
    }

    @Test fun `the quick question gives nothing away, the gap a pause, a choice's options not read, the translation left out`() {
        val choice = Exercise.Choice(prompt = "Luka ___ na pašniku.", options = listOf("sem", "si", "je"), answer = 2, sayOptions = true)
        assertEquals("Luka … na pašniku.", IntroSpeech.question(choice))
        assertEquals(listOf(Line("Luka … na pašniku.", true, Mark.Question)), IntroSpeech.questionLines(choice, looks))
        val cloze = Exercise.Cloze(text = "Otroci ___ v šoli. (The children are at school.)", accept = listOf("so"), hint = "otroci = oni", instruction = "Fill in the form.")
        assertEquals("Otroci … v šoli.", IntroSpeech.question(cloze))
        assertEquals(listOf(Line("Fill in the form.", false, Mark.Question), Line("Otroci … v šoli.", true, Mark.Question)), IntroSpeech.questionLines(cloze, looks))
        // none of the lines read says the answer
        assertTrue(IntroSpeech.questionLines(cloze, looks).none { "so" in it.text.split(" ") })
        // a question in the base with its Slovene marked: in its runs
        val base = Exercise.Choice(prompt = "Which form goes with **mi**? ___", options = listOf("smo", "ste"), answer = 0)
        assertEquals(listOf(Line("Which form goes with", false, Mark.Question), Line("mi", true, Mark.Question)), IntroSpeech.questionLines(base) { looks(it) || it == "mi" })
        assertNull(IntroSpeech.question(Exercise.Reorder(prompt = "x", tokens = listOf("a"), solutions = listOf(listOf("a")))))
    }

    @Test fun `a step's script, the explanation, its runs as the stitched reading, then the examples and the question`() {
        val ask = Exercise.Choice(prompt = "Luka ___ na pašniku.", options = listOf("sem", "si", "je"), answer = 2, sayOptions = true)
        val s = IntroSpeech.step("biti/0", biti, "en", listOf("Jaz sem Jan.", "Babica Micka je v kuhinji."), ask, slovene)
        assertEquals("biti/0", s.id)
        assertEquals("en", s.explanation?.lang)
        assertEquals(IntroSpeech.plain(biti), s.explanation?.plain)
        assertEquals(
            listOf(Line("Jaz sem Jan.", true, Mark.Example(0)), Line("Babica Micka je v kuhinji.", true, Mark.Example(1)), Line("Luka … na pašniku.", true, Mark.Question)),
            s.lines,
        )
        assertEquals(Line("Biti", true, Mark.Part(0, 0..0)), s.stitched.first())
        assertEquals(listOf(true, false, true, false, true, false, true, false), s.stitched.map { it.slovene })
        // a step with nothing to explain reads only its lines
        assertNull(IntroSpeech.step("x", "🙂", "en", listOf("Dober dan!"), null, looks).explanation)
    }

    @Test fun `the intro's own words make its spans Slovene`() {
        val intro = Intro(
            head = listOf(GrammarCell(text = mapOf("en" to "who")), GrammarCell(text = mapOf("en" to "to be"))),
            steps = listOf(
                IntroStep(
                    say = mapOf("en" to biti),
                    examples = listOf(GrammarExample(mapOf("sl" to "Babica Micka je v kuhinji.", "en" to "Granny Micka is in the kitchen."))),
                    rows = listOf(listOf(GrammarCell(words = "jaz"), GrammarCell(words = "sem"))),
                    ask = Exercise.Choice(prompt = "Which one means *I am*?", options = listOf("jaz sem", "I am"), answer = 0),
                ),
            ),
        )
        val known = IntroSpeech.vocabulary(intro, "sl", "Biti: sem, si, je", looks)
        assertTrue(IntroSpeech.within("Biti", known))
        assertTrue(IntroSpeech.within("v kuhinji", known))
        assertTrue(IntroSpeech.within("jaz sem", known))
        // the prompt in English and the English option add nothing
        assertFalse("which" in known || "means" in known || "am" in known)
        assertFalse(IntroSpeech.within("two", known))
        assertFalse(IntroSpeech.within("", known))
    }
}

/** What is read marked in the bubble: the run's segments of its line, as the Markdown draws them. */
class IntroMarkTest {
    @Test fun `the run read now is marked in the line's text, nothing else`() {
        val line = "One person first: **jaz sem** (I am), **ti si** (you are)."
        // segment 1: "jaz sem"
        val shown = si.lanisce.lani.ui.inlineMarkdown(line, mark = 1..1)
        val marked = shown.spanStyles.filter { it.item == si.lanisce.lani.ui.MARK }
        assertEquals(listOf("jaz sem"), marked.map { shown.text.substring(it.start, it.end) })
        // a base run of several segments: from the plain text to the end
        val tail = si.lanisce.lani.ui.inlineMarkdown(line, mark = 2..4)
        assertEquals(listOf(" (I am), ", "ti si", " (you are)."), tail.spanStyles.filter { it.item == si.lanisce.lani.ui.MARK }.map { tail.text.substring(it.start, it.end) })
        // the same numbering as the reading's
        assertEquals(IntroSpeech.segments(line).size, 5)
        assertTrue(si.lanisce.lani.ui.inlineMarkdown(line).spanStyles.none { it.item == si.lanisce.lani.ui.MARK })
    }
}
