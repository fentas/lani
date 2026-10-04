package si.lanisce.lani.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Release
import si.lanisce.lani.data.Updater
import si.lanisce.lani.l10n.bi

/** Self-update: a newer APK published on the node, its download, and its install. */
class UpdateController(
    context: Context,
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val notices: Notices,
) {
    private val updater = Updater(context)

    /** A newer APK published on the node, if any. */
    var release by mutableStateOf<Release?>(null)
        private set
    var progress by mutableStateOf<Float?>(null)
        private set
    /** versionCode of the APK already downloaded to the cache, if any. */
    var ready by mutableStateOf<Int?>(null)
        private set
    /** Asked once: it only changes with a reinstall, which restarts the app anyway (a PackageManager call). */
    val silent: Boolean by lazy { updater.canUpdateSilently() }

    fun check() = scope.launchSafely(notices::fail) {
        val r = bridge()?.latestRelease()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        release = r
        // When updates are silent, fetch ahead of time; the install happens when the app goes to the background.
        if (r != null && silent && ready != r.versionCode) download(r.versionCode, showProgress = false)
    }

    private suspend fun download(code: Int, showProgress: Boolean) {
        val b = bridge() ?: return
        if (showProgress) progress = 0f
        try {
            var shown = -1
            b.download("/app/apk", updater.apk) { f ->
                // Runs on the IO thread for every 64 KB: hand whole percents to the main thread only.
                val percent = (f * 100).toInt()
                if (showProgress && percent != shown) {
                    shown = percent
                    scope.launch(Dispatchers.Main) { if (progress != null) progress = f }
                }
            }
            ready = code
        } finally {
            progress = null
        }
    }

    /** Banner tap: install now. The first self-update shows Android's confirmation once. */
    fun install() = scope.launchSafely(notices::fail) {
        val u = release ?: return@launchSafely
        if (!updater.canInstall()) {
            notices.banner = bi("updateController.allowInstallsLaniThen")
            updater.requestInstallPermission()
            return@launchSafely
        }
        if (ready != u.versionCode) download(u.versionCode, showProgress = true)
        withContext(Dispatchers.IO) { updater.install() } // copies the whole APK: never on the main thread
    }

    /** The app left the screen: apply a downloaded update without interrupting practice. */
    fun onBackground() {
        val u = release ?: return
        // Off the main thread (onStop must not copy an APK), and not in the view model's scope: leaving
        // the app with Back clears the view model, and the install must still happen.
        if (ready == u.versionCode && silent) installing.launch { runCatching { updater.install() } }
    }

    private companion object {
        val installing = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
