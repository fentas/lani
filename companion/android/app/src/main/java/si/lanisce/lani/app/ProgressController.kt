package si.lanisce.lani.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.ProgressStats
import si.lanisce.lani.data.Stats
import si.lanisce.lani.l10n.bi
import java.io.File

/** "Napredek · Progress": statistics from all six learner databases, cached for offline viewing. */
class ProgressController(
    context: Context,
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val notices: Notices,
) {
    var stats by mutableStateOf<ProgressStats?>(null)
        private set
    private val cache = File(context.filesDir, "state.json")

    /** Shows the cached stats at once, then the node's current ones. */
    fun load() = scope.launch {
        if (stats == null) stats = withContext(Dispatchers.IO) {
            runCatching { Stats.parse(cache.readText()) }.getOrNull()
        }
        val b = bridge() ?: return@launch
        try {
            val raw = b.stateRaw()
            stats = withContext(Dispatchers.Default) { Stats.parse(raw) }
            withContext(Dispatchers.IO) { runCatching { cache.writeText(raw) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (stats == null) notices.error = "${bi("common.progress")}: ${e.message}"
        }
    }
}
