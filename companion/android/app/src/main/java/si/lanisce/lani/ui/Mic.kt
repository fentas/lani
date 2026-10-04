package si.lanisce.lani.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import si.lanisce.lani.data.MicOpen

/** The learner allowed Lani the microphone (RECORD_AUDIO): speech recognition in the app, and recordings. */
fun micAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** While [on] (the phone's recognizer listens), the microphone counts as open: the background sounds keep quiet ([MicOpen]). */
@Composable
fun MicHold(on: Boolean) {
    DisposableEffect(on) {
        val hold = if (on) MicOpen.hold() else null
        onDispose { hold?.release() }
    }
}
