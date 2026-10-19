package si.lanisce.lani.phone

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer

/**
 * The app's bridge client against the phone's own bridge (the hosted mode, docs/plans/05-built/hosted-mode.md): an OkHttp
 * interceptor that answers every request to the reserved address [BASE_URL] in-process, through [PhoneBridge.handle],
 * and passes every other request on. So the app's whole client (data/Http.kt, Bridge, the outbox, the event stream, the
 * clips, the family, the chat's photos) works unchanged in the hosted mode: only its base URL differs.
 *
 * - **The address:** `http://phone.lani.invalid`. `.invalid` never resolves (RFC 2606), so a request that missed the
 *   interceptor fails at once and reaches no network.
 * - **The token:** the app's (BridgeConfig.token) is the phone bridge's own ([PhoneBridge.Config.token]), so the
 *   request goes in as it is, as to a node.
 * - **A stream** (`/events`): the answer's parts ([PhoneBridge.Response.stream]) become a streaming body, read as a node's
 *   chunked answer is; the call cancelled or the body closed ends the stream on the bridge's side, as a closed
 *   connection does.
 * - **Writes:** after a mutating request (POST, PUT, PATCH, DELETE) the bridge took (2xx, 3xx), [written] hears it: the
 *   hosted mode's sync pushes the data a moment later (DataSync.touched).
 * - **No bridge:** [bridge] gives null (the hosted mode isn't on) or fails: an IOException, which the app takes as a
 *   node out of reach.
 */
class PhoneTransport(
    /** The phone's bridge, booting it when it isn't yet; null when the hosted mode isn't on. */
    private val bridge: suspend () -> PhoneBridge?,
    /** A mutating request the bridge took: its method and path. */
    private val written: (method: String, path: String) -> Unit = { _, _ -> },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.url.host != HOST) return chain.proceed(req)
        val path = req.url.encodedPath + (req.url.encodedQuery?.let { "?$it" } ?: "")
        val body = req.body?.let { b -> Buffer().also(b::writeTo).readByteArray() }
        // an application interceptor sees the body's type before OkHttp puts it in the headers
        val type = req.body?.contentType()?.takeIf { req.header("content-type") == null }
        val headers = req.headers.map { it.first to it.second } + listOfNotNull(type?.let { "content-type" to it.toString() })
        val answer = try {
            runBlocking {
                val b = bridge() ?: throw IOException("Lani on this phone isn't running")
                b.handle(PhoneBridge.Request(req.method, path, headers, body))
            }
        } catch (e: IOException) {
            throw e
        } catch (e: CancellationException) {
            throw InterruptedIOException("the phone's bridge didn't answer: ${e.message}")
        } catch (e: Exception) {
            // OkHttp's asynchronous calls (the event stream) treat anything but an IOException as a crash
            throw IOException("the phone's bridge: ${e.message ?: e}", e)
        }
        if (req.method in MUTATING && answer.status in 200..399) {
            try {
                written(req.method, path)
            } catch (_: Exception) {
            }
        }
        val contentType = answer.header("content-type")?.toMediaTypeOrNull()
        val responseBody = answer.stream?.let { FlowSource(it, chain.call()).buffer().asResponseBody(contentType, -1) }
            ?: answer.body.toResponseBody(contentType)
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(answer.status)
            .message(REASONS[answer.status] ?: "")
            .headers(Headers.Builder().apply { answer.headers.forEach { (k, v) -> addUnsafeNonAscii(k, v) } }.build())
            .body(responseBody)
            .build()
    }

    /**
     * A stream's parts as a body: collected on a coroutine of their own into a queue, read from it with a short wait, so
     * a cancelled call ([Call.isCanceled]: the event source let go) ends the read within [POLL_MS]. Closing it stops
     * the collection, which ends the stream on the bridge's side.
     */
    private class FlowSource(parts: Flow<ByteArray>, private val call: Call) : Source {
        private object End
        private class Broke(val error: Throwable)

        private val queue = LinkedBlockingQueue<Any>()
        private val job = scope.launch {
            try {
                parts.collect { queue.put(it) }
                queue.put(End)
            } catch (e: CancellationException) {
                queue.put(End)
                throw e
            } catch (e: Throwable) {
                queue.put(Broke(e))
            }
        }
        private var current: ByteArray? = null
        private var at = 0
        private var ended = false
        @Volatile private var closed = false

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (ended) return -1
            while (true) {
                val c = current
                if (c != null && at < c.size) {
                    val n = minOf(byteCount, (c.size - at).toLong()).toInt()
                    sink.write(c, at, n)
                    at += n
                    return n.toLong()
                }
                current = null
                at = 0
                if (closed || call.isCanceled()) throw IOException("Canceled")
                when (val next = queue.poll(POLL_MS, TimeUnit.MILLISECONDS)) {
                    null -> continue
                    End -> {
                        ended = true
                        return -1
                    }
                    is Broke -> throw IOException(next.error.message ?: "the stream broke off", next.error)
                    else -> current = next as ByteArray
                }
            }
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            closed = true
            job.cancel()
        }
    }

    companion object {
        /** The reserved host of the phone's own bridge: never on the network (RFC 2606). */
        const val HOST = "phone.lani.invalid"
        /** The hosted mode's BridgeConfig.baseUrl. */
        const val BASE_URL = "http://$HOST"
        /** How long a stream's read waits before it looks whether its call was cancelled. */
        const val POLL_MS = 250L

        private val MUTATING = setOf("POST", "PUT", "PATCH", "DELETE")
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val REASONS = mapOf(
            200 to "OK", 201 to "Created", 202 to "Accepted", 204 to "No Content", 304 to "Not Modified", 400 to "Bad Request",
            401 to "Unauthorized", 403 to "Forbidden", 404 to "Not Found", 405 to "Method Not Allowed", 409 to "Conflict",
            413 to "Payload Too Large", 423 to "Locked", 429 to "Too Many Requests", 500 to "Internal Server Error", 503 to "Service Unavailable",
        )

        /** Whether [baseUrl] is the phone's own bridge (the hosted mode) rather than a node's. */
        fun isPhone(baseUrl: String?): Boolean = baseUrl?.trimEnd('/') == BASE_URL
    }
}
