package si.lanisce.lani.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.data.FamilyLink
import java.net.Inet4Address
import java.net.InetAddress

/**
 * The sending side of "Add a family member" (data/FamilyLink; ui/settings/FamilyShare): the QR code shown on the Wi-Fi this device
 * is on, the device that claimed it, the parent's decision. The gift is read only when the parent has held the button, and
 * lives in memory for as long as it takes to seal it. Compose state, on the main thread.
 */
class FamilyShareController(private val scope: CoroutineScope, private val context: Context, private val gift: suspend () -> FamilyLink.Gift) {
    sealed interface Phase {
        data object Idle : Phase
        /** No Wi-Fi: nothing could reach this device. */
        data object NoWifi : Phase
        data class Showing(val text: String) : Phase
        /** [device] claimed the code: what the parent decides on. */
        data class Asking(val device: String) : Phase
        data class Sent(val device: String) : Phase
        /** The time ran out, or the parent said no: the code means nothing now. */
        data object Over : Phase
    }

    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set

    private var sender: FamilyLink.Sender? = null
    private var listener: FamilyLink.Listener? = null
    private var job: Job? = null

    /** A new code, shown until it's claimed or expires; the one before it is gone. */
    fun show() {
        close()
        val address = wifiAddress(context) ?: return Unit.also { phase = Phase.NoWifi }
        // QA's forwarded port reaches the emulator on its loopback: every address in a debug build, the Wi-Fi's alone otherwise
        val l = runCatching { FamilyLink.Listener(address.takeUnless { BuildConfig.QA }) }.getOrNull() ?: return Unit.also { phase = Phase.NoWifi }
        val s = FamilyLink.Sender(address.hostAddress.orEmpty(), l.port, System.currentTimeMillis())
        listener = l
        sender = s
        phase = Phase.Showing(s.offer.text())
        if (BuildConfig.QA) Log.i(TAG, "showing ${s.offer.text()}") // a one-time code and a public key: never a provider's key
        job = scope.launch {
            launch(Dispatchers.IO) { l.serve(s) { device -> scope.launch { phase = Phase.Asking(device) } } }
            delay(FamilyLink.TTL_MS)
            if (phase !is Phase.Sent) over()
        }
    }

    /** The parent held the button: the gift sealed for the device that claimed the code, sent, and the code spent. */
    fun confirm() {
        val s = sender ?: return
        val device = (phase as? Phase.Asking)?.device ?: return
        scope.launch {
            val sealed = s.confirm(gift(), System.currentTimeMillis())
            withContext(Dispatchers.IO) { listener?.send(sealed) }
            if (sealed != null) phase = Phase.Sent(device) else over()
            stop()
        }
    }

    /** No, or the page left: nothing goes, and the code means nothing any more. */
    fun refuse() {
        sender?.refuse()
        over()
    }

    fun close() {
        sender?.refuse()
        stop()
        phase = Phase.Idle
    }

    private fun over() {
        stop()
        phase = Phase.Over
    }

    private fun stop() {
        job?.cancel()
        job = null
        val l = listener
        listener = null
        sender = null
        if (l != null) scope.launch(Dispatchers.IO) { runCatching { l.close() } }
    }

    companion object {
        private const val TAG = "Family"

        /** This device's address on its Wi-Fi (or Ethernet), not a mobile network's; null without one. */
        fun wifiAddress(context: Context): InetAddress? {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
            val n = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(n) ?: return null
            // an emulator's network is both: QA reaches it through a forwarded port
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return null
            return cm.getLinkProperties(n)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        }
    }
}
