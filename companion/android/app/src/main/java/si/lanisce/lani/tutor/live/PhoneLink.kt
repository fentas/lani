package si.lanisce.lani.tutor.live

import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import si.lanisce.lani.phone.PhoneBridge

/**
 * The live tutor's bridge in-process (docs/plans/05-built/tutor-live.md, "What waits for the phone runtime"): the
 * phone's own bridge ([PhoneBridge], the bridge's TypeScript in QuickJS) behind [LiveBridge]'s calls, so the tutor
 * session's routes (/tutor/session, attach, the inbox, the tools) are the same contract on a node and on the phone.
 * An OkHttp interceptor: nothing goes to the network; the phone's bridge adds its own token.
 */
class PhoneLink(private val bridge: suspend () -> PhoneBridge) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val r = chain.request()
        val body = r.body?.let { b -> Buffer().also { b.writeTo(it) }.readByteArray() }
        // in-process: the phone's bridge takes its own token (PhoneBridge.handle adds it)
        val headers = r.headers.filter { (k, _) -> !k.equals("authorization", ignoreCase = true) }
        val path = r.url.encodedPath + (r.url.encodedQuery?.let { "?$it" } ?: "")
        val answer = runBlocking { bridge().handle(PhoneBridge.Request(r.method, path, headers, body)) }
        val type = answer.header("content-type")?.toMediaTypeOrNull()
        return Response.Builder()
            .request(r).protocol(Protocol.HTTP_1_1).code(answer.status).message("in-process")
            .headers(Headers.Builder().apply { answer.headers.forEach { (k, v) -> addUnsafeNonAscii(k, v) } }.build())
            .body(answer.body.toResponseBody(type))
            .build()
    }

    companion object {
        /** A client for [LiveBridge] whose every call goes to [bridge] in-process. */
        fun client(bridge: suspend () -> PhoneBridge): OkHttpClient = OkHttpClient.Builder().addInterceptor(PhoneLink(bridge)).build()
    }
}
