package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import si.lanisce.lani.l10n.bi

/** The snackbar's one-shot messages. Every controller reports through the same instance. */
class Notices {
    /** News: a new drill, a family message, "saved". */
    var banner by mutableStateOf<String?>(null)
    /** A failure, shown with ⚠️. */
    var error by mutableStateOf<String?>(null)

    fun fail(e: Throwable) {
        error = e.message
    }

    /** Runs a village step; a game bug is reported but never ends the practice flow. */
    fun <T> village(block: () -> T): T? = try {
        block()
    } catch (e: Throwable) {
        villageFailed(e)
        null
    }

    fun villageFailed(e: Throwable) {
        error = "${bi("common.village")}: ${e.message ?: e::class.simpleName}"
    }
}

/**
 * Launches [block]; an exception goes to [onError] instead of crashing the app. Cancellation is
 * not an error and passes through.
 */
fun CoroutineScope.launchSafely(onError: (Exception) -> Unit, block: suspend CoroutineScope.() -> Unit): Job = launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
    }
}
