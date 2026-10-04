package si.lanisce.lani.road

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * QA's car (app/QaHooks, "road:car"; a debug build only): browses the road's library and plays each session as Android
 * Auto does, through the platform's media browser and controller (the protocol a car speaks): the root, its tab, the
 * sessions, then each session played from its media id until its first item plays, or not (in a car, "Failed to load
 * selection"). What it finds goes to the log, a line each (tag RoadCar), for companion/bin/qa's road step; at the end the
 * session is paused and "done" is logged. Nothing here is asked of a release build.
 */
object RoadCarCheck {
    private const val TAG = "RoadCar"
    private const val WAIT_MS = 10_000L

    private var browser: MediaBrowser? = null
    private val main = Handler(Looper.getMainLooper())

    /** Starts the check (on the main thread); what it finds is logged as it goes. */
    fun run(context: Context) {
        browser?.disconnect()
        lateinit var b: MediaBrowser
        b = MediaBrowser(context, ComponentName(context, RoadService::class.java), object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                log("connected: root ${b.root}")
                browse(context, b)
            }

            override fun onConnectionFailed() = end(b, null, "FAILED: the browser can't connect")

            override fun onConnectionSuspended() = log("FAILED: the connection was suspended (the service died?)")
        }, null)
        browser = b
        b.connect()
    }

    private fun browse(context: Context, b: MediaBrowser) {
        children(b, b.root) { top ->
            val tab = top.firstOrNull { it.isBrowsable } ?: return@children end(b, null, "FAILED: no tab under the root")
            log("tab: ${tab.mediaId} \"${tab.description.title}\"")
            children(b, tab.mediaId.orEmpty()) { sessions ->
                val playable = sessions.filter { it.isPlayable }
                log("sessions: ${playable.size} (${playable.joinToString(" ") { it.mediaId.orEmpty() }})")
                if (playable.isEmpty()) return@children end(b, null, "FAILED: no session to play (${sessions.firstOrNull()?.description?.title})")
                val c = MediaController(context, b.sessionToken)
                play(b, c, playable.map { it.mediaId.orEmpty() to it.description.title?.toString().orEmpty() }, 0, 0)
            }
        }
    }

    private fun children(b: MediaBrowser, id: String, then: (List<MediaBrowser.MediaItem>) -> Unit) {
        b.subscribe(id, object : MediaBrowser.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowser.MediaItem>) {
                b.unsubscribe(parentId)
                then(children)
            }

            override fun onError(parentId: String) = end(b, null, "FAILED: the children of $parentId")
        })
    }

    /**
     * Plays session [at] of [sessions] (media id and title) as a car does (play from its media id), and waits until its
     * first item plays (the item's album is the session's title) or [WAIT_MS] passed; then the next.
     */
    private fun play(b: MediaBrowser, c: MediaController, sessions: List<Pair<String, String>>, at: Int, played: Int) {
        val (id, title) = sessions.getOrNull(at) ?: return end(b, c, "done: $played of ${sessions.size} sessions played")
        var settled = false
        lateinit var callback: MediaController.Callback
        val timeout = Runnable {
            if (settled) return@Runnable
            settled = true
            c.unregisterCallback(callback)
            log("session $id: nothing plays (state ${c.playbackState?.state}, ${c.playbackState?.errorMessage ?: "no error"})")
            main.postDelayed({ play(b, c, sessions, at + 1, played) }, 300)
        }
        callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = check()
            override fun onMetadataChanged(metadata: MediaMetadata?) = check()

            fun check() {
                if (settled) return
                val s = c.playbackState ?: return
                val album = c.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                val line = when {
                    s.state == PlaybackState.STATE_ERROR -> "session $id: FAILED: ${s.errorMessage}"
                    s.state == PlaybackState.STATE_PLAYING && album == title -> "session $id: playing \"${c.metadata?.description?.title}\""
                    else -> return
                }
                settled = true
                main.removeCallbacks(timeout)
                c.unregisterCallback(this)
                log(line)
                main.postDelayed({ play(b, c, sessions, at + 1, played + if ("playing" in line) 1 else 0) }, 1_500)
            }
        }
        c.registerCallback(callback, main)
        c.transportControls.playFromMediaId(id, null)
        main.postDelayed(timeout, WAIT_MS)
    }

    private fun end(b: MediaBrowser, c: MediaController?, line: String) {
        c?.transportControls?.pause()
        log(line)
        main.postDelayed({ b.disconnect() }, 1_000)
        if (browser === b) browser = null
    }

    private fun log(line: String) {
        Log.i(TAG, line)
    }
}
