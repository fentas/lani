package si.lanisce.lani.road

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * The phone's own player for the road ([RoadService]), for Bluetooth without Android Auto: which session to play, and
 * play, pause, next, again and the two ratings, as big buttons. Compose state; connect while it shows, release after.
 */
class RoadRemote(private val context: Context) {
    var title by mutableStateOf<String?>(null)
        private set
    var subtitle by mutableStateOf<String?>(null)
        private set
    var session by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    /** The item playing is a card or a word: the ratings count. */
    var rateable by mutableStateOf(false)
        private set
    var connected by mutableStateOf(false)
        private set

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = update(player)
    }

    fun connect() {
        if (future != null) return
        val f = MediaController.Builder(context, SessionToken(context, ComponentName(context, RoadService::class.java))).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            if (future !== f) return@addListener c.release()
            controller = c
            c.addListener(listener)
            update(c)
            connected = true
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let(MediaController::releaseFuture)
        future = null
        controller = null
        connected = false
    }

    private fun update(p: Player) {
        val m = p.currentMediaItem
        title = m?.mediaMetadata?.title?.toString()
        subtitle = m?.mediaMetadata?.artist?.toString()
        session = m?.mediaMetadata?.albumTitle?.toString()
        playing = p.isPlaying
        rateable = m?.mediaId?.let { it.startsWith("card:") || it.startsWith("word:") } == true
    }

    /** Plays session [id] ([RoadService.MIX] …) from its start. */
    fun play(id: String) {
        val c = controller ?: return
        c.setMediaItem(MediaItem.Builder().setMediaId(id).build())
        c.prepare()
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNextMediaItem() }

    /** The item again from its start. */
    fun again() { controller?.seekTo(0) }

    fun rate(knew: Boolean) {
        controller?.sendCustomCommand(if (knew) RoadService.KNEW_COMMAND else RoadService.DIDNT_COMMAND, Bundle.EMPTY)
    }
}
