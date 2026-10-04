package si.lanisce.lani.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Installs APKs published by companion/bin/release-app via a PackageInstaller session.
 *
 * Once Lani has installed itself (it is then the "installer of record"), Android 12+ lets
 * later updates run without a confirmation dialog, so [canUpdateSilently] turns true and the
 * app can update when it goes to the background. Until then Android asks the user once.
 */
class Updater(private val context: Context) {
    val apk: File get() = File(context.cacheDir, "updates/lani.apk")

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun canUpdateSilently(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val source = runCatching { context.packageManager.getInstallSourceInfo(context.packageName) }.getOrNull()
        return source?.installingPackageName == context.packageName
    }

    /** Opens "Install unknown apps" for Lani; needed once before the first self-update. */
    fun requestInstallPermission() = context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun install(file: File = apk) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("lani.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // Mutable: the installer adds the status and, if needed, the confirmation intent.
            val callback = PendingIntent.getBroadcast(
                context, id, Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(callback.intentSender)
        }
    }
}

/** Shows Android's confirmation screen when the install still needs the user (first self-update). */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        @Suppress("DEPRECATION")
        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
