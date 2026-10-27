package si.lanisce.lani.tutor.live

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import si.lanisce.lani.tutor.Online
import si.lanisce.lani.tutor.OnlineState

/**
 * The phone's network for the tutors' queues and the app's outbox ([Online]; app/NetworkBack sends the outbox when it comes
 * back), from ConnectivityManager's default-network callback, the process's only one: online
 * while there is a default network with internet that isn't blocked for the app. Android blocks a background app's
 * network (Doze, app standby, data saver): `getActiveNetwork()` is null for it then, and the callback says so
 * (`onBlockedStatusChanged`, API 29+; on older ones the network is lost and found again). Not "validated" too: a VPN
 * (Tailscale) or a network Android hasn't validated yet may still reach the API, and a queue that waits wrongly would
 * never answer; a call that fails there is retried as before. One callback for the process, registered at the first
 * use; ACCESS_NETWORK_STATE (a normal permission) is in the manifest.
 */
object AndroidOnline {
    @Volatile private var state: OnlineState? = null

    /** The process's [Online]; [Online.ALWAYS] when the callback can't be registered (it then never pauses a queue). */
    fun of(context: Context): Online = state ?: synchronized(this) {
        state ?: runCatching { register(context.applicationContext) }.getOrNull()?.also { state = it } ?: Online.ALWAYS
    }

    private fun register(app: Context): OnlineState {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: error("no ConnectivityManager")
        val s = OnlineState(usable(cm.activeNetwork?.let(cm::getNetworkCapabilities), blocked = false))
        cm.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                @Volatile private var caps: NetworkCapabilities? = null
                @Volatile private var blocked = false

                override fun onAvailable(network: Network) {
                    caps = cm.getNetworkCapabilities(network)
                    s.set(usable(caps, blocked))
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    caps = networkCapabilities
                    s.set(usable(caps, blocked))
                }

                override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                    this.blocked = blocked
                    s.set(usable(caps, blocked))
                }

                override fun onLost(network: Network) {
                    caps = null
                    s.set(false)
                }
            },
        )
        return s
    }

    /** Whether a network of [caps] may reach the internet for the app: it has internet, and isn't [blocked]. */
    private fun usable(caps: NetworkCapabilities?, blocked: Boolean): Boolean =
        !blocked && caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
