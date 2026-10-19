package si.lanisce.lani.tutor

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64

/**
 * The loopback listener an OAuth sign-in's browser comes back to (docs/plans/05-built/providers.md): an HTTP server on
 * 127.0.0.1 for one redirect, started before the browser opens. "Sign in with ChatGPT" ([Loopback]) and "Continue
 * with OpenRouter" ([OpenRouterAuth]) both use it.
 *
 * - It binds [bindPort] (0: the system picks a free one). The redirect must name the port it really got ([port], the
 *   server socket's: a constructor argument named like the property once shadowed it, and the URL said 127.0.0.1:0).
 * - It answers GETs on [path] whose Host is `127.0.0.1:<port>` through [handle]; anything else gets a 404, and never
 *   ends the attempt (a stray request, a favicon). [handle] answers with a page, and says whether the attempt is over.
 * - Its pages send the browser back to the app ([LoopbackPage.BACK], an intent filter of MainActivity).
 */
class LoopbackServer(
    bindPort: Int,
    private val path: String,
    threadName: String,
    private val handle: (Hit) -> Answer,
) : Closeable {
    /** A GET on [path]: its query's parameters, in order (a name may come more than once). */
    class Hit(val params: List<Pair<String, String>>) {
        fun all(k: String): List<String> = params.filter { it.first == k }.map { it.second }
    }

    /**
     * What the listener answers ([code], a page), whether the attempt is over ([done]: no more answers then), and what
     * to do once the page is sent ([then]: the attempt's result).
     */
    data class Answer(val code: Int, val html: String, val done: Boolean, val then: (() -> Unit)? = null)

    private val server = ServerSocket(bindPort, 8, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))

    /** The port it listens on: the one asked for, or the system's pick for 0. */
    val port: Int = server.localPort

    /** `http://127.0.0.1:<port><path>`: the redirect to send, with the real port. */
    val uri: String = "http://127.0.0.1:${server.localPort}$path"

    @Volatile private var over = false

    init {
        Thread({
            while (!server.isClosed && !over) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                runCatching { socket.use(::serve) }
            }
        }, threadName).apply { isDaemon = true }.start()
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 10_000
        val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
        val line = input.readLine() ?: return
        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }.take(100).toList()
        val host = headers.firstOrNull { it.startsWith("host:", ignoreCase = true) }?.substringAfter(':')?.trim()
        val (method, target) = line.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val out = socket.getOutputStream()
        fun respond(code: Int, html: String) {
            val bytes = html.toByteArray(Charsets.UTF_8)
            out.write(
                ("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                    "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: ${LoopbackPage.CSP}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII),
            )
            out.write(bytes)
            out.flush()
        }
        if (method != "GET" || target.substringBefore('?') != path || host != "127.0.0.1:$port" || over) return respond(404, LoopbackPage.page("Not found", ""))
        val params = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.map {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        val a = handle(Hit(params))
        if (a.done) over = true
        respond(a.code, a.html)
        a.then?.invoke()
    }

    override fun close() {
        over = true
        runCatching { server.close() }
    }
}

/** The loopback's pages: small, no outside resources, a strict CSP, a way back to the app. */
object LoopbackPage {
    /** Where the pages send the browser on: back to the app (MainActivity's BROWSABLE intent filter). */
    const val BACK = "lani://tutor/signed-in"
    const val SCRIPT = "setTimeout(function(){location.href='$BACK'},400);"
    val CSP = "default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-" +
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(SCRIPT.toByteArray())) + "'; base-uri 'none'; frame-ancestors 'none'"
    const val BACK_LINK = "<p><a href=\"$BACK\">↩ Nazaj v Lani · Back to Lani</a></p>"

    fun page(title: String, body: String) =
        "<!doctype html><html lang=\"sl\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Lani</title>" +
            "<style>body{font:17px system-ui,sans-serif;max-width:32rem;margin:15vh auto;padding:24px;color:#202123}h1{font-size:24px}" +
            "a{display:inline-block;margin-top:12px;padding:12px 18px;border-radius:12px;background:#1f6f43;color:#fff;text-decoration:none}</style>" +
            "<h1>$title</h1>$body</html>"

    /** The page of a sign-in that went through: the app checks it now, and the browser goes back to it. */
    fun signedIn(what: String) = page("✓ Prijava uspela · Signed in", "<p>$what</p>$BACK_LINK<script>$SCRIPT</script>")

    /** Constant-time equality of two values (an attempt's state). */
    fun same(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}

/**
 * A sign-in's last step while Android may hold the app's network (the browser comes back to the loopback while it is
 * still in front, so the app is in the background: battery saver, data saver, "Unable to resolve host"): [block] tried
 * again after each of [waits] while it fails for want of the network ([ApiError.Network]), then [offline] said plainly.
 * A refusal by the provider isn't tried again. ChatGPT's exchange found it (ChatGPTAuth.OFFLINE_WAITS); OpenRouter's
 * exchange does the same.
 */
object WhileOffline {
    /** The waits between the tries: about a minute in all. */
    val WAITS = listOf(1_000L, 2_000L, 3_000L, 5_000L, 8_000L, 10_000L, 15_000L, 20_000L)

    suspend fun <T> run(waits: List<Long>, pause: suspend (Long) -> Unit, offline: (ApiError.Network) -> Exception, block: suspend () -> T): T {
        for (wait in waits) {
            try {
                return block()
            } catch (e: ApiError.Network) {
                pause(wait)
            }
        }
        return try {
            block()
        } catch (e: ApiError.Network) {
            throw offline(e)
        }
    }
}
