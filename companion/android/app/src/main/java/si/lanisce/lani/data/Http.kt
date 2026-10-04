package si.lanisce.lani.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Shared HTTP plumbing for the node's APIs ([Bridge], [FamilyApi], [ClipsApi]). */
internal object Http {
    /** One connection pool and dispatcher for the whole app; each API derives its timeouts from it. */
    private val base = OkHttpClient()

    val jsonType = "application/json".toMediaType()

    fun client(connectSeconds: Long, readSeconds: Long): OkHttpClient =
        base.newBuilder().connectTimeout(connectSeconds, TimeUnit.SECONDS).readTimeout(readSeconds, TimeUnit.SECONDS).build()
}

/** The node refused this phone's token (401): it was never valid, or the phone was unpaired (lani-pair --revoke). */
class Unpaired : IOException("HTTP 401: this phone isn't paired with the tutor")

/** A request to the node at [path] (or a full URL starting with http), with the app token. */
internal fun BridgeConfig.request(path: String): Request.Builder = Request.Builder()
    .url(if (path.startsWith("http")) path else baseUrl.trimEnd('/') + path)
    .header("Authorization", "Bearer $token")

/** Runs [req] off the main thread: the body, or an [IOException] naming the status for a non-2xx answer. */
internal suspend fun OkHttpClient.text(req: Request): String = withContext(Dispatchers.IO) {
    newCall(req).execute().use { r ->
        val body = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${body.take(200)}")
        body
    }
}

/** Like [text], but hands back the status code instead of throwing (for expected 404 or 409 answers). */
internal suspend fun OkHttpClient.exchange(req: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
    newCall(req).execute().use { r -> r.code to r.body?.string().orEmpty() }
}

/** Saves [req]'s body as [to] through a ".part" file, so an interrupted download never looks complete. */
internal suspend fun OkHttpClient.downloadTo(req: Request, to: File) = withContext(Dispatchers.IO) {
    newCall(req).execute().use { r ->
        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
        val body = r.body ?: throw IOException("empty body")
        val tmp = File(to.path + ".part")
        tmp.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        if (!tmp.renameTo(to)) throw IOException("could not store ${to.name}")
    }
}
