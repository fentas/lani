package si.lanisce.lani.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * QA's log of the app's requests (QaHooks' "main:watch" turns it on; docs/plans/05-built/nav-perf.md): every request of
 * [Http]'s client (the node's, the phone's own bridge in the hosted mode, a visited town's through the node), when it
 * began (SystemClock.elapsedRealtime, ms), its method and path, the status and the length of the body as sent (-1 when
 * the answer didn't say), for the last [SIZE]. Off, it costs a volatile read per request and nothing else.
 */
internal object NetWatch : Interceptor {
    private const val SIZE = 1024

    /** One request: [at] when it began, [what] its method and path with the query, [code] its status (0 while none). */
    class Entry(val at: Long, val what: String) {
        @Volatile var code = 0
        @Volatile var length = -1L
    }

    @Volatile var on = false

    private val entries = ArrayDeque<Entry>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (!on) return chain.proceed(req)
        val q = req.url.encodedQuery
        val e = Entry(android.os.SystemClock.elapsedRealtime(), "${req.method} ${req.url.encodedPath}${if (q != null) "?$q" else ""}")
        synchronized(entries) {
            entries.addLast(e)
            if (entries.size > SIZE) entries.removeFirst()
        }
        val res = chain.proceed(req)
        e.code = res.code
        e.length = res.body?.contentLength() ?: -1L
        return res
    }

    /** The requests that began from [from] until [until] (elapsedRealtime, ms), oldest first. */
    fun between(from: Long, until: Long): List<Entry> = synchronized(entries) { entries.filter { it.at in from until until } }
}
