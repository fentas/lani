package si.lanisce.lani.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.IntroSpeech

/**
 * Links in the tutor's Markdown (README "Tutor chat", "Links"): drawn as links ([LinkAnnotation.Url], which the app's
 * [LinkGate] opens), with the words to hear or look up around them as before, and as plain text when copied.
 */
class MarkdownLinksTest {
    /** The URL links of [a]: what each covers, and where it goes. */
    private fun urls(a: AnnotatedString) =
        a.getLinkAnnotations(0, a.length).mapNotNull { r -> (r.item as? LinkAnnotation.Url)?.let { a.text.substring(r.start, r.end) to it.url } }

    private fun tags(a: AnnotatedString) =
        a.getLinkAnnotations(0, a.length).mapNotNull { r -> (r.item as? LinkAnnotation.Clickable)?.let { a.text.substring(r.start, r.end) to it.tag } }

    private val slovene = SpeakSpans({ t -> t.any { it in "šžčŠŽČ" } || t.startsWith("Dober") || t == "hiša" }) { }

    @Test fun `a Markdown link shows its text, linked to its URL`() {
        val a = inlineMarkdown("Look up **hiša** on [Fran](https://fran.si/iskanje?Query=hiša).")
        assertEquals("Look up hiša on Fran.", a.text)
        assertEquals(listOf("Fran" to "https://fran.si/iskanje?Query=hiša"), urls(a))
    }

    @Test fun `a bare URL is linked whole, a star in it no italics`() {
        val a = inlineMarkdown("see https://example.com/a*b*c now")
        assertEquals("see https://example.com/a*b*c now", a.text)
        assertEquals(listOf("https://example.com/a*b*c" to "https://example.com/a*b*c"), urls(a))
        assertTrue(a.spanStyles.isEmpty())
        // in italics, the marks around it, not in it
        val i = inlineMarkdown("*https://fran.si/x*")
        assertEquals("https://fran.si/x", i.text)
        assertEquals(listOf("https://fran.si/x" to "https://fran.si/x"), urls(i))
    }

    @Test fun `a link's text keeps its own marks, without a link of its own`() {
        val a = inlineMarkdown("[**SSKJ**](https://fran.si/130)")
        assertEquals("SSKJ", a.text)
        assertEquals(listOf("SSKJ" to "https://fran.si/130"), urls(a))
        assertTrue(a.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 4 })
        val named = inlineMarkdown("[https://fran.si](https://example.com)")
        assertEquals(listOf("https://fran.si" to "https://example.com"), urls(named))
    }

    @Test fun `a link in code stays code, other schemes stay text`() {
        val code = inlineMarkdown("`https://fran.si/x`")
        assertEquals("https://fran.si/x", code.text)
        assertEquals(emptyList<Any>(), urls(code))
        val js = inlineMarkdown("[click](javascript:alert(1)) or intent://x")
        assertEquals("[click](javascript:alert(1)) or intent://x", js.text)
        assertEquals(emptyList<Any>(), urls(js))
    }

    @Test fun `Slovene to hear and words to look up stay beside a link, never around it`() {
        val a = inlineMarkdown("**Dober dan** — https://fran.si", speak = slovene)
        assertEquals(listOf("Dober dan 🔈" to "say"), tags(a))
        assertEquals(listOf("https://fran.si" to "https://fran.si"), urls(a))
        // a Slovene span with a link inside: the link, no tap to hear around it
        val inside = inlineMarkdown("**hiša [na Franu](https://fran.si/x)**", speak = slovene)
        assertEquals("hiša na Franu", inside.text)
        assertEquals(emptyList<Any>(), tags(inside))
        assertEquals(listOf("na Franu" to "https://fran.si/x"), urls(inside))
        // the cheat sheet's words to look up
        val looked = inlineMarkdown("**hiša**, see https://fran.si", speak = slovene, look = { _, _ -> })
        assertEquals(listOf("hiša" to "word"), tags(looked))
        assertEquals(listOf("https://fran.si" to "https://fran.si"), urls(looked))
    }

    @Test fun `a link is part of the segment it stands in, as a step's reading counts them`() {
        val s = "Odpri https://fran.si in poišči **hiša**."
        assertEquals(3, IntroSpeech.segments(s).size)
        val a = inlineMarkdown(s, mark = 0..0)
        val marked = a.spanStyles.filter { it.item == MARK }.single()
        assertEquals("Odpri https://fran.si in poišči ", a.text.substring(marked.start, marked.end))
        assertEquals(listOf("https://fran.si" to "https://fran.si"), urls(a))
    }

    @Test fun `copied, a Markdown link reads its text and its URL, a bare one as it is`() {
        assertEquals(
            "Look it up: SSKJ (https://fran.si/130), or https://example.com.",
            markdownPlain("Look it up: [**SSKJ**](https://fran.si/130), or https://example.com."),
        )
        assertEquals("• https://fran.si/x", markdownPlain("- [https://fran.si/x](https://fran.si/x)"))
    }

    @Test fun `an explanation's endings in bold, never inside a link`() {
        assertEquals("**Feminine**: **-a**, see https://fran.si/x-ka", ExplainFormat.format("Feminine: -a, see https://fran.si/x-ka"))
        assertEquals("ends in **-ka**: https://fran.si/-ka", ExplainFormat.format("ends in -ka: https://fran.si/-ka"))
    }
}
