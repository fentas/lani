package si.lanisce.lani.data

import java.net.IDN

/**
 * Links in what the tutor writes (companion/README.md "Tutor chat", "Links"): a Markdown link `[text](https://…)` or a
 * bare `https://…` in a chat message, an exercise's explanation, a card pinned on the cheat sheet. Only `https://` and
 * `http://` are links; anything else (`javascript:`, `intent:`, `file:`, `content:`, a scheme of an app) stays text, as
 * written. A tap on a link to [ALLOWED] opens it at once; any other asks first, showing its domain (ui/Links.kt). Pure.
 */
object Links {
    /**
     * Where a link opens at once on a tap, https only: every other one asks first, its domain shown (a prompt injection
     * could put a link in a reply, so the learner sees where it goes before leaving the app). The one list; README
     * "Tutor chat" and the tutor's guide (bridge/src/features/chat.ts) say the same.
     */
    val ALLOWED: List<Allowed> = listOf(
        // the Slovene dictionaries (SSKJ, Pravopis, the etymological one …): the tutor's links for a word; its subdomains too
        Allowed("fran.si"),
        // Lani's own repository: its docs, issues and discussions (a report or a wish the tutor points to); not the rest of GitHub
        Allowed("github.com", "/fentas/lani"),
    )

    /**
     * A domain and its subdomains; with [path], only its pages under that path (segment-wise, any case), on the domain
     * itself or its www (not another subdomain: gist.github.com is someone else's).
     */
    data class Allowed(val domain: String, val path: String? = null) {
        fun admits(host: String, path: String): Boolean {
            val under = this.path ?: return host == domain || host.endsWith(".$domain")
            if (host != domain && host != "www.$domain") return false
            // what a browser makes of "/fentas/lani/../../other": not under it; nor an encoded / . or \ that may turn into one
            val segments = path.split('/')
            if (segments.any { it == "." || it == ".." } || ENCODED_SEPARATOR.containsMatchIn(path)) return false
            val p = path.lowercase()
            val u = under.lowercase().trimEnd('/')
            return p == u || p.startsWith("$u/")
        }
    }

    /**
     * A link a tap can open: [url] as written, its [host] (lower case; an international name in its ASCII form,
     * "xn--…", as the browser reaches it), whether it is https ([secure]) and [direct]: allowed ([ALLOWED]), so it opens
     * at once.
     */
    data class Link(val url: String, val host: String, val secure: Boolean, val direct: Boolean)

    /**
     * [url] as a link, or null when it is none: not `https://` or `http://`; with a space, a control or an invisible
     * character, a backslash (a browser reads it as "/", which would move the host), `<`, `>`, `"` or a backtick; with a user name or
     * password before the host (`https://fran.si@other.example`); an IPv6 address; a host that isn't a name.
     */
    fun parse(url: String): Link? {
        val scheme = SCHEME.find(url)?.takeIf { it.range.first == 0 } ?: return null
        // nor an invisible character (a right-to-left mark, a zero-width space) that could make it read as another
        if (url.any { it.isWhitespace() || it.isISOControl() || it in REFUSED || Character.getType(it) == Character.FORMAT.toInt() }) return null
        val rest = url.substring(scheme.range.last + 1)
        val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, end)
        if (authority.isEmpty() || '@' in authority || '[' in authority || ']' in authority) return null
        val colon = authority.indexOf(':')
        val name = if (colon < 0) authority else authority.substring(0, colon)
        if (colon >= 0 && !PORT.matches(authority.substring(colon + 1))) return null
        val host = asciiHost(name.removeSuffix(".")) ?: return null
        val path = rest.substring(end).takeWhile { it != '?' && it != '#' }
        val secure = scheme.value.lowercase() == "https://"
        return Link(url, host, secure, direct = secure && ALLOWED.any { it.admits(host, path) })
    }

    /** [name] as its ASCII host, lower case: letters (any script), digits and hyphens between dots; else null. */
    private fun asciiHost(name: String): String? {
        if (name.isEmpty() || name.split('.').any { label -> label.isEmpty() || !label.all { it.isLetterOrDigit() || it == '-' } }) return null
        return runCatching { IDN.toASCII(name, IDN.USE_STD3_ASCII_RULES) }.getOrNull()?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    /**
     * A link in a text: where it stands ([range]: the whole of `[text](url)`, or of the bare URL, with its `<` `>` when
     * it has them), what shows ([text]: the Markdown link's text, or the URL), where it goes ([link]); [markdown]: written
     * as `[text](url)`.
     */
    data class Found(val range: IntRange, val text: String, val link: Link, val markdown: Boolean)

    /**
     * The links in [s], in order: Markdown links `[text](url)` (the URL may hold balanced parentheses, may be in `<…>`,
     * and may have a title after it), then bare URLs in the rest, from `http://` or `https://` (not inside a word) up to
     * a space, a quote or a `<`, without what ends a sentence after them (`.`, `,`, `:`, `;`, `!`, `?`, `…`, a quote mark,
     * `*`, `_`, `~`) and without a closing `)` or `]` they don't open themselves. One whose URL isn't a link ([parse])
     * stays text.
     */
    fun find(s: String): List<Found> {
        if ("://" !in s) return emptyList()
        val out = mutableListOf<Found>()
        var i = 0
        while (true) {
            val open = s.indexOf('[', i)
            if (open < 0) break
            val m = markdownAt(s, open)
            if (m != null) {
                out += m
                i = m.range.last + 1
            } else i = open + 1
        }
        val bare = mutableListOf<Found>()
        var from = 0
        for (m in out + null) {
            val until = m?.range?.first ?: s.length
            bare += bareIn(s, from, until)
            m?.let { from = it.range.last + 1 }
        }
        return (out + bare).sortedBy { it.range.first }
    }

    /** The Markdown link starting at [open] (its `[`), if one is there and its URL is a link. */
    private fun markdownAt(s: String, open: Int): Found? {
        val close = s.indexOf(']', open + 1)
        if (close < 0 || s.getOrNull(close + 1) != '(') return null
        val text = s.substring(open + 1, close)
        if (text.isBlank() || '[' in text || '\n' in text) return null
        var j = close + 2
        while (j < s.length && s[j] == ' ') j++
        val url: String
        if (s.getOrNull(j) == '<') {
            val gt = s.indexOf('>', j + 1)
            if (gt < 0) return null
            url = s.substring(j + 1, gt)
            j = gt + 1
        } else {
            val start = j
            var depth = 0
            while (j < s.length) {
                val c = s[j]
                if (c.isWhitespace()) break
                if (c == '(') depth++
                if (c == ')') { if (depth == 0) break; depth-- }
                j++
            }
            url = s.substring(start, j)
        }
        // an optional title: [text](url "title")
        var k = j
        while (k < s.length && s[k] == ' ') k++
        if (k < s.length && k > j && s[k] in "\"'") {
            val q = s.indexOf(s[k], k + 1)
            if (q < 0) return null
            k = q + 1
            while (k < s.length && s[k] == ' ') k++
        }
        if (s.getOrNull(k) != ')') return null
        val link = parse(url) ?: return null
        return Found(open..k, text, link, markdown = true)
    }

    /** The bare URLs in [s] between [from] and [until]. */
    private fun bareIn(s: String, from: Int, until: Int): List<Found> {
        val out = mutableListOf<Found>()
        val part = s.substring(from, until)
        for (m in SCHEME.findAll(part)) {
            val start = from + m.range.first
            if (out.lastOrNull()?.let { start <= it.range.last } == true) continue
            if (start > 0 && (s[start - 1].isLetterOrDigit() || s[start - 1] == '/')) continue // inside a word, or a URL's
            var end = start + m.value.length
            while (end < until && !s[end].isWhitespace() && s[end] !in STOPS) end++
            var url = s.substring(start, end)
            while (url.isNotEmpty()) {
                val last = url.last()
                url = when {
                    last in TRAILING -> url.dropLast(1)
                    last == ')' && url.count { it == '(' } < url.count { it == ')' } -> url.dropLast(1)
                    last == ']' && url.count { it == '[' } < url.count { it == ']' } -> url.dropLast(1)
                    else -> break
                }
            }
            if (url.length <= m.value.length) continue
            val link = parse(url) ?: continue
            val first = if (start > 0 && s[start - 1] == '<' && s.getOrNull(start + url.length) == '>') start - 1 else start
            val last = if (first < start) start + url.length else start + url.length - 1
            out += Found(first..last, url, link, markdown = false)
        }
        return out
    }

    /** [s] with each Markdown link as its text alone ("[SSKJ](https://fran.si/…)": "SSKJ"); a bare URL stays. */
    fun unlinked(s: String): String {
        val found = find(s).filter { it.markdown }
        if (found.isEmpty()) return s
        return buildString {
            var i = 0
            for (f in found) {
                append(s, i, f.range.first)
                append(f.text)
                i = f.range.last + 1
            }
            append(s, i, s.length)
        }
    }

    private val SCHEME = Regex("""(?i)https?://""")
    private val PORT = Regex("""\d{1,5}""")
    /** "%2F", "%2E", "%5C": a / . or \ written encoded. */
    private val ENCODED_SEPARATOR = Regex("""(?i)%(2f|2e|5c)""")
    private const val REFUSED = "\\<>\"`"
    /** Where a bare URL ends: quotes, angle brackets, a backtick. */
    private const val STOPS = "<>\"`“”„«»‘’"
    /** What ends a sentence after a bare URL rather than the URL. */
    private const val TRAILING = ".,:;!?'*_~…"
}
