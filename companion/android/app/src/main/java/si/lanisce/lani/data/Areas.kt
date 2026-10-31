package si.lanisce.lani.data

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * The bridge's areas (docs/plans/05-built/changes-areas.md, companion/bridge/src/changes.ts): GET /changes says the version
 * of each (the learner's own data, the village, the towns, the family's, the content), and each read the app makes again on
 * a Back says the versions of those it depends on as they were before its answer was made (the header [HEADER]). Kept here
 * for every answer of [Http]'s client that says them, by its URL: a read whose areas are all still at those versions
 * ([fresh]) has nothing new, and Home's reading after a Back leaves it out.
 *
 * An answer without the header (an older bridge, a read with no areas, an error besides a 404) forgets its URL's, so it
 * is never fresh; a request that got no answer (offline) keeps what its URL had: the app still has what it read then.
 */
internal object Areas : Interceptor {
    /** The header of an answer that says its areas' versions: `learner=…, content=…`. */
    const val HEADER = "lani-areas"

    /** The areas' versions each URL's last answer said. */
    private val said = ConcurrentHashMap<String, Map<String, String>>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val res = chain.proceed(req)
        if (req.method == "GET") {
            val key = req.url.toString()
            val areas = res.header(HEADER)?.let(::parse)
            if (areas != null && areas.isNotEmpty() && res.code in SAID) said[key] = areas else said.remove(key)
        }
        return res
    }

    /** `learner=v1, content=v2` as a map; null when it doesn't parse. */
    fun parse(header: String): Map<String, String>? = buildMap {
        for (part in header.split(',')) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val i = p.indexOf('=')
            if (i <= 0 || i == p.length - 1) return null
            put(p.substring(0, i), p.substring(i + 1))
        }
    }

    /**
     * Whether [url]'s last answer said its areas and each is still at the version it said, by [now] (GET /changes' areas):
     * nothing it depends on moved since it was made.
     */
    fun fresh(url: String, now: Map<String, String>): Boolean {
        val a = said[url] ?: return false
        return a.all { (area, v) -> now[area] == v }
    }

    /** Whether [url] was answered with its areas (none yet: never asked, or it said none). */
    fun known(url: String): Boolean = said.containsKey(url)

    /** The areas [url]'s last answer said (tests). */
    fun of(url: String): Map<String, String>? = said[url]

    /** Forgets everything (another node, a new reading of everything; tests). */
    fun clear() = said.clear()

    /** The statuses whose answers say their areas: an answer, the same one (304), none for this village (404). */
    private val SAID = setOf(200, 304, 404)
}
