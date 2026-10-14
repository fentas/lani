package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Links in the tutor's texts (README "Tutor chat", "Links"): what is one, where it ends, what it shows, and which open
 * at once ([Links.ALLOWED]) or only after asking.
 */
class LinksTest {
    private fun urls(s: String) = Links.find(s).map { it.link.url }
    private fun direct(url: String) = Links.parse(url)?.direct

    @Test fun `a Markdown link shows its text and goes to its URL, fran-si at once`() {
        val s = "Look it up: [hiša na Franu](https://fran.si/iskanje?View=1&Query=hiša)."
        val f = Links.find(s).single()
        assertEquals("hiša na Franu", f.text)
        assertEquals("https://fran.si/iskanje?View=1&Query=hiša", f.link.url)
        assertEquals("[hiša na Franu](https://fran.si/iskanje?View=1&Query=hiša)", s.substring(f.range))
        assertTrue(f.markdown)
        assertEquals("fran.si", f.link.host)
        assertTrue(f.link.secure && f.link.direct)
    }

    @Test fun `a bare URL ends before what ends the sentence`() {
        assertEquals(listOf("https://fran.si/iskanje?Query=hiša"), urls("Glej https://fran.si/iskanje?Query=hiša."))
        assertEquals(listOf("https://fran.si/a"), urls("(see https://fran.si/a)"))
        assertEquals(listOf("https://fran.si/a"), urls("„https://fran.si/a“"))
        assertEquals(listOf("https://example.com/b"), urls("italic *https://example.com/b*!"))
        assertEquals(listOf("https://example.com/b"), urls("bold **https://example.com/b**"))
        assertEquals(listOf("https://example.com/x", "https://example.org/"), urls("https://example.com/x, https://example.org/; done"))
        assertEquals(listOf("https://example.com/q?a=1"), urls("Is it https://example.com/q?a=1?"))
        assertEquals(listOf("https://example.com"), urls("„https://example.com“ … https://"))
    }

    @Test fun `parentheses inside a URL stay, an unbalanced closing one goes`() {
        assertEquals(listOf("https://en.wikipedia.org/wiki/Kranj_(mesto)"), urls("https://en.wikipedia.org/wiki/Kranj_(mesto)"))
        assertEquals(listOf("https://en.wikipedia.org/wiki/Kranj_(mesto)"), urls("(https://en.wikipedia.org/wiki/Kranj_(mesto))."))
        val md = Links.find("[Kranj](https://en.wikipedia.org/wiki/Kranj_(mesto)) is a town").single()
        assertEquals("https://en.wikipedia.org/wiki/Kranj_(mesto)", md.link.url)
        assertEquals("Kranj", md.text)
        val titled = Links.find("""[SSKJ](https://fran.si/130 "Slovar") and more""").single()
        assertEquals("https://fran.si/130", titled.link.url)
        assertEquals("""[SSKJ](https://fran.si/130 "Slovar")""", """[SSKJ](https://fran.si/130 "Slovar") and more""".substring(titled.range))
    }

    @Test fun `an autolink in angle brackets shows the URL without them`() {
        val s = "Fran: <https://fran.si/> ok"
        val f = Links.find(s).single()
        assertEquals("<https://fran.si/>", s.substring(f.range))
        assertEquals("https://fran.si/", f.text)
        assertEquals(listOf("https://fran.si/x"), urls("[Fran](<https://fran.si/x>)"))
    }

    @Test fun `fran-si and its subdomains open at once, the rest asks`() {
        assertEquals(true, direct("https://fran.si/"))
        assertEquals(true, direct("https://www.fran.si/iskanje?Query=pes"))
        assertEquals(true, direct("https://FRAN.SI/"))
        assertEquals(true, direct("https://fran.si.:443/x"))
        assertEquals(false, direct("https://fran.si.example.com/"))
        assertEquals(false, direct("https://notfran.si/"))
        assertEquals(false, direct("https://example.com/fran.si"))
        // http, even fran.si's, asks: not secure
        val http = Links.parse("http://fran.si/")!!
        assertFalse(http.secure)
        assertFalse(http.direct)
    }

    @Test fun `Lani's repository opens at once, not the rest of GitHub`() {
        assertEquals(true, direct("https://github.com/fentas/lani"))
        assertEquals(true, direct("https://github.com/fentas/lani/discussions/12"))
        assertEquals(true, direct("https://github.com/Fentas/Lani/blob/main/companion/README.md#tutor-chat"))
        assertEquals(true, direct("https://github.com/fentas/lani?tab=readme"))
        assertEquals(false, direct("https://github.com/fentas/lani-other"))
        assertEquals(false, direct("https://github.com/fentas"))
        assertEquals(false, direct("https://github.com/someone/else"))
        assertEquals(false, direct("https://github.com/fentas/lani/../../someone/else"))
        assertEquals(false, direct("https://github.com/fentas/lani/%2e%2e/%2e%2e/someone/else"))
        assertEquals(false, direct("https://gist.github.com/fentas/lani"))
    }

    @Test fun `other schemes stay text`() {
        for (s in listOf(
            "[click](javascript:alert(1))", "javascript:alert(1)", "intent://scan/#Intent;scheme=zxing;end", "file:///sdcard/x",
            "content://si.lanisce.lani/x", "lani://open/chat", "mailto:jan@example.com", "[x](ftp://example.com/)",
            "[x](www.fran.si)", "data:text/html,<b>x</b>",
        )) assertEquals(s, emptyList<String>(), urls(s))
        assertNull(Links.parse("javascript:alert(1)"))
        assertNull(Links.parse("HTTPS:fran.si"))
    }

    @Test fun `a host hidden behind a user name, a backslash or an invisible character is no link`() {
        assertNull(Links.parse("https://fran.si@example.com/"))
        assertNull(Links.parse("https://example.com\\@fran.si/"))
        assertNull(Links.parse("https://fran.si\u200B.example.com/"))
        assertNull(Links.parse("https://example.com/\u202Eis.narf"))
        assertNull(Links.parse("https://[::1]/"))
        assertNull(Links.parse("https://fr%61n.si/"))
        assertNull(Links.parse("https:///x"))
        assertNull(Links.parse("https://fran.si:port/"))
        assertEquals(emptyList<String>(), urls("[Fran](https://fran.si@example.com/)"))
    }

    @Test fun `an international name is shown as the browser reaches it, never as a look-alike`() {
        // a Cyrillic "а" in "fr\u0430n.si"
        val l = Links.parse("https://fr\u0430n.si/")!!
        assertTrue(l.host, l.host.startsWith("xn--"))
        assertFalse(l.direct)
        assertEquals("xn--bea.si", Links.parse("https://č.si/")!!.host)
    }

    @Test fun `a URL inside a word or another URL isn't one of its own`() {
        assertEquals(emptyList<String>(), urls("xhttps://fran.si"))
        assertEquals(listOf("https://example.com/?u=https://fran.si"), urls("https://example.com/?u=https://fran.si"))
        // a Markdown link's text is its text, not a second link
        val f = Links.find("[https://fran.si](https://example.com)").single()
        assertEquals("https://example.com", f.link.url)
        assertEquals("example.com", f.link.host)
        assertFalse("its text names fran.si, the tap asks for example.com", f.link.direct)
    }

    @Test fun `Markdown links as their text alone, for a quote or the tutor bar`() {
        assertEquals("SSKJ and https://example.com", Links.unlinked("[SSKJ](https://fran.si/x) and https://example.com"))
        assertEquals("no links", Links.unlinked("no links"))
        assertEquals("Fran", previewLine("[**Fran**](https://fran.si)"))
    }

    @Test fun `the allowed list is short and https`() {
        assertEquals(listOf("fran.si", "github.com"), Links.ALLOWED.map { it.domain })
        assertTrue(Links.ALLOWED.all { it.admits(it.domain, it.path ?: "/") })
    }
}
