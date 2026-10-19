package si.lanisce.lani.tutor.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * `dictionary_lookup`'s outside dictionary for Slovene (plan 5, "New tools the hosted tutor needs"): fran.si's search
 * (SSKJ and its other dictionaries), fetched by the app itself, its page's text returned as marked untrusted data, so
 * nothing leaves the phone but the word. Only ever to https://fran.si; a page that can't be had is null (the app's own
 * dictionary still answers).
 */
class FranLookup(base: OkHttpClient = defaultClient, private val max: Int = 3_000) {
    private val client = base.newBuilder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()

    suspend fun look(word: String): String? = withContext(Dispatchers.IO) {
        val w = word.trim().take(60).takeIf { it.isNotEmpty() } ?: return@withContext null
        val url = "$HOST/iskanje".toHttpUrl().newBuilder().addQueryParameter("View", "1").addQueryParameter("Query", w).build()
        try {
            client.newCall(Request.Builder().url(url).header("accept", "text/html").get().build()).execute().use { r ->
                if (!r.isSuccessful || r.request.url.host != "fran.si") return@withContext null
                text(r.body?.string().orEmpty()).takeIf { it.isNotBlank() }?.let { if (it.length > max) it.take(max) + " …" else it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val HOST = "https://fran.si"
        private val defaultClient: OkHttpClient by lazy { OkHttpClient() }

        /** A page's readable text: its body without scripts, styles, navigation and tags, the entities decoded, the spaces folded. */
        fun text(html: String): String {
            val body = Regex("(?is)<body[^>]*>(.*)</body>").find(html)?.groupValues?.get(1) ?: html
            val main = Regex("(?is)<main[^>]*>(.*)</main>").find(body)?.groupValues?.get(1) ?: body
            return main
                .replace(Regex("(?is)<(script|style|nav|header|footer|form|noscript)[^>]*>.*?</\\1>"), " ")
                .replace(Regex("(?i)<br\\s*/?>|</(p|div|li|h\\d|tr)>"), "\n")
                .replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
                .lines().map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        }
    }
}
