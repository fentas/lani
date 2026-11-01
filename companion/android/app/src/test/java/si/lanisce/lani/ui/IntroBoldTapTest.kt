package si.lanisce.lani.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Courses
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.IntroSpeech
import si.lanisce.lani.data.Spoken
import si.lanisce.lani.l10n.Lang

/**
 * The bold forms of an intro's explanation a tap hears (ui/school/IntroScreen, the teacher's bubble; companion/COURSE.md
 * "Read aloud"): every span the intro makes the language learned, by the app's check or by its own words
 * ([IntroSpeech.learned]), the same she reads in her voice; the base language's words not. Teo's unit riflessivi
 * (companion/course/it/units/riflessivi.json): "**si lava**" is underlined as "**mi lavo**" is, though the app's check alone
 * knows only the second.
 */
class IntroBoldTapTest {
    /** The app's check for Teo's pair without clips ([si.lanisce.lani.data.Speaker.isSlovene]: Spoken.looks). */
    private val app: (String) -> Boolean = { Spoken.looks(it, Lang.IT, Lang.DE) }

    private val unit = checkNotNull(Courses.bundled("it")?.units?.get("riflessivi")) { "the unit riflessivi (companion/course/it)" }
    private val intro = checkNotNull(unit.intro)
    private val learned = IntroSpeech.learned(intro, unit.language, unit.title[unit.language], app, unit.words)

    /** What a tap on [text]'s spans does, as the bubble draws it with [isLearned]: the text of each link to hear. */
    private fun heard(text: String, isLearned: (String) -> Boolean): List<String> {
        val a: AnnotatedString = inlineMarkdown(text, SpeakSpans(isLearned) {})
        return a.getLinkAnnotations(0, a.length).mapNotNull { r -> (r.item as? LinkAnnotation.Clickable)?.takeIf { it.tag == "say" }?.let { a.text.substring(r.start, r.end).removeSuffix(" 🔈") } }
    }

    @Test fun `si lava is underlined for a tap from the intro's own words, as mi lavo is, a German bold word not`() {
        val say = Grammar.text(intro.steps[0].say, "de")
        assertTrue(say, "**si lava**" in say && "**mi lavo**" in say && "*ich wasche mich*" in say)
        // the app's check alone: mi lavo and ti lavi, not si lava (what QA saw)
        assertEquals(listOf("mi lavo", "ti lavi", "Mi chiamo Giulia."), heard(say, app))
        // with the intro's own words: si lava too (lavarsi of its words, si lava of its table), in the order shown
        assertEquals(listOf("mi lavo", "ti lavi", "si lava", "Mi chiamo Giulia."), heard(say, learned))
        // the German in italics stays the phone's German, and so does a German word in bold
        assertFalse(learned("ich wasche mich"))
        val german = "Merke dir **Achtung**: **si lava** (er wäscht sich), **sich waschen** auf Deutsch."
        assertEquals(listOf("si lava"), heard(german, learned))
    }

    @Test fun `every bold form the teacher reads in her voice is underlined, and only those`() {
        for ((i, step) in intro.steps.withIndex()) {
            val say = Grammar.text(step.say, "de")
            val script = IntroSpeech.introStep("riflessivi/$i", intro, i, unit.language, "de", unit.title[unit.language], app, unit.words)
            val voiced = script.explanation!!.runs.filter { it.slovene }.map { it.text }
            assertEquals("step $i", voiced, heard(say, learned))
        }
    }
}
