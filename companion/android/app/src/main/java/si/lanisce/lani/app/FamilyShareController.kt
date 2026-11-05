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
import si.lanisce.lani.data.FamilyPost
import java.net.Inet4Address
import java.net.InetAddress

/**
 * The sending side of "Add a family member" (data/FamilyLink; ui/settings/FamilyShare): the QR code, good on the Wi-Fi this device is
 * on and through the relay when one is set, the device that claimed it, the parent's decision. The gift is read only when the
 * parent has held the button, and lives in memory for as long as it takes to seal it. Never from a child's device. Compose state,
 * on the main thread.
 */
class FamilyShareController(
    private val scope: CoroutineScope,
    private val context: Context,
    private val devices: FamilyDevices,
    private val child: () -> Boolean,
    private val relay: () -> String,
    private val gift: suspend () -> FamilyLink.Gift,
) {
    sealed interface Phase {
        data object Idle : Phase
        /** No Wi-Fi and no relay: nothing could reach this device. */
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
    private var ways: List<FamilyLink.Way> = emptyList()

    /** The way the claim came: the parent's decision goes back by it. */
    @Volatile private var claimedBy: FamilyLink.Way? = null
    private var job: Job? = null

    /** A new code, shown until it's claimed or expires; the one before it is gone. */
    fun show() {
        close()
        if (child()) return
        val address = wifiAddress(context)
        val through = relay().takeIf { it.isNotBlank() }
        // QA's forwarded port reaches the emulator on its loopback: every address in a debug build, the Wi-Fi's alone otherwise
        val l = address?.let { a -> runCatching { FamilyLink.Listener(a.takeUnless { BuildConfig.QA }) }.getOrNull() }
        if (l == null && through == null) return Unit.also { phase = Phase.NoWifi }
        val s = FamilyLink.Sender(if (l != null) address.hostAddress.orEmpty() else "", l?.port ?: 0, System.currentTimeMillis(), relay = through)
        ways = listOfNotNull(l, through?.let { FamilyLink.RelayWay(si.lanisce.lani.data.HttpDrop(it), s.offer) })
        sender = s
        phase = Phase.Showing(s.offer.text())
        if (BuildConfig.QA) Log.i(TAG, "showing ${s.offer.text()}") // a one-time code and a public key: never a provider's key
        job = scope.launch {
            for (way in ways) launch(Dispatchers.IO) {
                way.serve(s) { device ->
                    claimedBy = way
                    scope.launch { phase = Phase.Asking(device) }
                }
            }
            delay(FamilyLink.TTL_MS)
            if (phase !is Phase.Sent) over()
        }
    }

    /** The parent held the button: the gift sealed for the device that claimed the code, sent, and the code spent. */
    fun confirm() {
        val s = sender ?: return
        val device = (phase as? Phase.Asking)?.device ?: return
        val way = claimedBy ?: return
        if (child()) return
        scope.launch {
            val family = devices.familyId()
            val member = FamilyPost.newId()
            val sealed = s.confirm(gift().copy(family = family, member = member, from = android.os.Build.MODEL ?: "Android"), System.currentTimeMillis())
            val bond = s.bond
            // on the list before it goes: a device with a bond the parent can't see is worse than a row that never hears
            if (sealed != null && bond != null) devices.added(family, member, device, bond)
            val went = withContext(Dispatchers.IO) { way.send(sealed) }
            if (sealed != null && went) phase = Phase.Sent(device)
            else {
                if (sealed != null) devices.drop(member)
                over()
            }
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
        val all = ways
        ways = emptyList()
        claimedBy = null
        sender = null
        if (all.isNotEmpty()) scope.launch(Dispatchers.IO) { all.forEach { runCatching { it.close() } } }
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
