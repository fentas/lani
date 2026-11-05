package si.lanisce.lani.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.io.IOException
import java.util.Base64

/**
 * The relay's boxes for a family (companion/relay/src/drop.ts): a note in, what waits out. What goes through here was
 * sealed on one device and is opened on the other. Blocking: for a background thread.
 */
class HttpDrop(private val relay: String) : FamilyLink.Drop {
    private val http = Http.client(connectSeconds = 5, readSeconds = 35)
    private val text = "application/octet-stream".toMediaType()

    private fun url(box: String) = relay.trimEnd('/') + "/v1/drop/$box"

    override fun put(box: String, note: String, ttlS: Int) {
        val req = Request.Builder().url(url(box)).header("x-lani-drop-ttl", ttlS.toString()).put(note.toByteArray().toRequestBody(text)).build()
        http.newCall(req).execute().use { r -> if (!r.isSuccessful) throw IOException("the relay answered ${r.code}") }
    }

    override fun take(box: String, waitS: Int): List<String> {
        val req = Request.Builder().url(url(box) + "?wait=$waitS").build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("the relay answered ${r.code}")
            parseItems(r.body?.string().orEmpty())
        }
    }

    companion object {
        /** {items: [base64]}; an answer that isn't one is a relay that can't be reached as far as a family goes. */
        internal fun parseItems(raw: String): List<String> = try {
            json.parseToJsonElement(raw).jsonObject["items"]!!.jsonArray.map { Base64.getDecoder().decode(it.jsonPrimitive.content).decodeToString() }
        } catch (e: RuntimeException) {
            throw IOException("not an answer of the relay's", e)
        }
    }
}

/**
 * Finding a parent's device on the Wi-Fi this one is on, by the name [FamilyPost.serviceName] gives its family: the system's
 * multicast DNS, so neither side needs to know an address that changes. Between two emulators nothing goes across, so QA
 * gives the address instead ("family:peer:").
 */
class FamilyNsd(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    /** Answers to [name] on [port] until closed. */
    fun advertise(name: String, port: Int): Closeable {
        val m = nsd ?: return Closeable { }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = FamilyPost.SERVICE_TYPE
            setPort(port)
        }
        runCatching { m.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
        return Closeable { runCatching { m.unregisterService(listener) } }
    }

    /**
     * Where [name] answers now, looking for at most [timeoutMs]; null when nobody does. The discovery always stops: the system allows
     * an app only so many at once, and one left running would end the Wi-Fi's post until the app restarts.
     */
    suspend fun find(name: String, timeoutMs: Long): Pair<String, Int>? {
        val m = nsd ?: return null
        val found = CompletableDeferred<Pair<String, Int>?>()
        var resolving = false
        val finding = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                found.complete(null)
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                // the system adds " (2)" when two claim a name
                if (resolving || !info.serviceName.startsWith(name)) return
                resolving = true
                @Suppress("DEPRECATION")
                m.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                        resolving = false
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        found.complete(info.host?.hostAddress?.let { it to info.port })
                    }
                })
            }
        }
        if (runCatching { m.discoverServices(FamilyPost.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, finding) }.isFailure) return null
        return try {
            withTimeoutOrNull(timeoutMs) { found.await() }
        } finally {
            runCatching { m.stopServiceDiscovery(finding) }
        }
    }
}
