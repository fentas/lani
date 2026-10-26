package si.lanisce.lani.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/**
 * The phone's network back after it was lost, while the app is on screen ([start] … [stop]): [onBack] then, on the
 * system's network thread. The app sends its outbox at once (its backoff ignored) and reconnects the live events, instead
 * of waiting for the outbox's next retry (minutes after a long time offline) or an event stream that didn't notice the
 * network go: a word asked for offline ("➕ Dodaj med moje besede") reaches the tutor as soon as the phone is online.
 */
class NetworkBack(private val context: Context, private val onBack: () -> Unit) {
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Whether the default network was lost since [start]: only its coming back counts (not the first one there is). */
    @Volatile private var lost = false

    fun start() {
        if (callback != null) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                lost = true
            }

            override fun onAvailable(network: Network) {
                if (!lost) return
                lost = false
                onBack()
            }
        }
        // a phone without the permission or the service (a test, a restricted profile): nothing, as before
        if (runCatching { cm.registerDefaultNetworkCallback(cb) }.isSuccess) callback = cb
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        lost = false
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
    }
}
