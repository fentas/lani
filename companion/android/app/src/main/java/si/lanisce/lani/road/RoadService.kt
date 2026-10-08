package si.lanisce.lani.road

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import androidx.media3.session.MediaConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import si.lanisce.lani.MainActivity
import si.lanisce.lani.R
import si.lanisce.lani.app.QaHooks
import si.lanisce.lani.data.Outbox
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.deliver
import si.lanisce.lani.l10n.LangSetting
import si.lanisce.lani.l10n.bi
import java.io.FileDescriptor
import java.io.PrintWriter
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * "🚗 Za pot · For the road" in the car (companion/README.md, "Im Auto · In the car"): a media library that Android Auto
 * browses and the steering wheel's and Bluetooth's play, pause, next and previous control. It plays what the app got
 * ready on the phone ([RoadStore]): nothing needs the node, the screen or typing.
 *
 * The browse tree is short: one tab with the sessions ([RoadMix], the drills' [RoadDrills]); an item of a session is one
 * card, word, dialog, evening's story, phrase or drill's item (next skips it, or during the pause to say the Slovene plays
 * the answer at once; previous plays it again: [RoadPlayer]). A card or a word has two buttons, "✓ Znal sem · I knew it" and "✗ Nisem · I didn't", which record
 * its review ([RoadRatings]), as a steering-wheel button held does (fast-forward 👍, rewind 👎); nothing pressed, nothing
 * graded.
 */
@OptIn(UnstableApi::class)
class RoadService : MediaLibraryService() {
    private lateinit var store: RoadStore
    private lateinit var player: ExoPlayer

    /** The player the session and the car see: next and the held buttons as [RoadControls] has them. */
    private lateinit var roadPlayer: RoadPlayer
    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The library on the phone; read again when a session starts (the app may have got it ready meanwhile). */
    @Volatile
    private var library: RoadLibrary = RoadLibrary()

    /** The items queued, by media id, for the player's sources ([RoadSources]) and the ratings. */
    private val queued = ConcurrentHashMap<String, RoadItem>()

    /** The session playing ([MIX] goes on with a new block at its end). */
    @Volatile
    private var playing: String? = null

    /** The next block of [MIX] is on its way. */
    private var extending = false

    override fun onCreate() {
        super.onCreate()
        LangSetting(this).apply()
        si.lanisce.lani.l10n.LearnerSetting(this).apply() // and the content said to the learner of the profile
        store = RoadStore(this)
        library = store.readLibrary() ?: RoadLibrary()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(RoadSources(this, store, { library.base }, { queued[it] }))
            // speech: a navigation prompt or a call pauses it (a word isn't missed), and it goes on after
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            // previous plays the item again from its start; pressed twice quickly, the one before
            .setMaxSeekToPreviousPositionMs(1_000)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                scope.launch(Dispatchers.IO) { store.heard(id) }
                refreshButtons()
                continueMix()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // paused (a button, the car switched off, another app's music): the ratings so far go to the outbox as one
                // session. A navigation prompt or a call only holds it (playback suppressed), which isn't a pause here.
                if (!playWhenReady) commitRatings()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) commitRatings()
            }
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // a steering-wheel button held (fast-forward, rewind): 👍 or 👎 on a card or a word
        roadPlayer = RoadPlayer(player, { queued[it] }, ::rate)
        session = MediaLibrarySession.Builder(this, roadPlayer, Callback())
            .setSessionActivity(open)
            .build()
        commitRatings() // any left from a service stopped before it could
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // the app swiped away: stop unless a session is playing (then the notification stays until it's paused)
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        commitRatings()
        session?.release()
        session = null
        roadPlayer.release() // and the ExoPlayer under it
        scope.cancel()
        super.onDestroy()
    }

    // --- the sessions ----------------------------------------------------------------------------------------

    private fun today(): String = LocalDate.now().toString()

    /** The items of session [id] on the phone ([RoadMix.session]), least recently heard first. */
    private fun items(id: String): List<RoadItem> {
        library = store.readLibrary() ?: library
        return RoadMix.session(id, library, store.readHeard(), today()).filter { store.playable(it, library.base) }
    }

    private fun mediaItem(item: RoadItem, album: String): MediaItem {
        queued[item.id] = item
        // a card or a word shows its meaning only: the Slovene is what the learner says before hearing it
        val flash = item.kind == Kind.CARD || item.kind == Kind.WORD
        return MediaItem.Builder()
            .setMediaId(item.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(if (flash) item.subtitle else item.title)
                    .setArtist(if (flash) null else item.subtitle.ifBlank { null })
                    .setAlbumTitle(album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
    }

    /** The playlist of session [id]: its items as media items; empty when there is nothing on the phone for it. */
    private fun playlist(id: String): List<MediaItem> {
        val label = label(id)
        playing = id
        return items(id).map { mediaItem(it, label) }
    }

    /**
     * "🌙": from any session to "🌙 Mirno · Easy listening" at once (sudden heavy traffic, tiredness), from Mirno back to
     * "🚗 Za pot"; the session's items made off the main thread. Nothing changes when there is nothing to play there.
     */
    private fun toggleEasy() {
        val to = if (playing == EASY) MIX else EASY
        scope.launch {
            val list = kotlinx.coroutines.withContext(Dispatchers.IO) { items(to).map { mediaItem(it, label(to)) } }
            if (list.isEmpty()) return@launch
            playing = to
            player.setMediaItems(list)
            player.prepare()
            player.play()
            refreshButtons()
        }
    }

    /** "🚗 Za pot" near its end: the next block, with items not queued yet (made off the main thread). */
    private fun continueMix() {
        if (playing != MIX || extending || player.mediaItemCount - player.currentMediaItemIndex > 2) return
        val have = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId.substringBefore('#') }.toSet()
        extending = true
        scope.launch {
            try {
                val next = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    // read again: while the phone is still getting ready, the library grows (RoadPrepService)
                    library = store.readLibrary() ?: library
                    RoadMix.block(library, store.readHeard(), today(), exclude = have).filter { store.playable(it, library.base) }.map { mediaItem(it, label(MIX)) }
                }
                if (playing == MIX && next.isNotEmpty()) player.addMediaItems(next)
            } finally {
                extending = false
            }
        }
    }

    /** [work] off the main thread (it reads the library and checks its files), as the future a session callback returns. */
    private fun <T : Any> later(work: () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        scope.launch(Dispatchers.IO) { runCatching(work).onSuccess { f.set(it) }.onFailure { f.setException(it) } }
        return f
    }

    private fun label(id: String): String = when (id) {
        MIX -> "🚗 ${bi("road.title")}"
        EASY -> "🌙 ${bi("road.easy")}"
        SHADOW -> "🗣️ ${bi("road.shadow")}"
        REVIEWS -> "🔁 ${bi("road.reviews", "count" to library.due(today()).size)}"
        WORDS -> "🆕 ${bi("road.words")}"
        DIALOGS -> "💬 ${bi("road.dialogs")}"
        STORIES -> "📖 ${bi("road.stories")}"
        TRANSFORMS -> "🔄 ${bi("road.transform")}"
        RAPID -> "⚡ ${bi("road.rapid")}"
        BUILDS -> "🧱 ${bi("road.build")}"
        RIDDLES -> "🕵️ ${bi("road.riddles")}"
        else -> id
    }

    private fun node(id: String, title: String, playable: Boolean, browsable: Boolean, subtitle: String? = null): MediaItem =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsPlayable(playable)
                .setIsBrowsable(browsable)
                .setMediaType(if (browsable) MediaMetadata.MEDIA_TYPE_FOLDER_MIXED else MediaMetadata.MEDIA_TYPE_PLAYLIST)
                // Android Auto: the sessions as a list of big rows, not a grid
                .setExtras(if (browsable) Bundle().apply { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM) } else null)
                .build(),
        ).build()

    /** The tab's items: the sessions, or where to get ready first. */
    private fun sessions(): List<MediaItem> {
        library = store.readLibrary() ?: RoadLibrary()
        if (library.items.isEmpty()) return listOf(node(NONE, "📱 ${bi("road.firstInApp")}", playable = false, browsable = false))
        return ORDER.map { node(it, label(it), playable = true, browsable = false) }
    }

    /** A session's media id, or an item's queued before: what a controller asked to play. */
    private fun resolve(items: List<MediaItem>): List<MediaItem> {
        val first = items.singleOrNull()?.mediaId
        if (first != null && first in SESSIONS) return playlist(first)
        return items.mapNotNull { m -> queued[m.mediaId]?.let { mediaItem(it, m.mediaMetadata.albumTitle?.toString() ?: "") } }
    }

    /**
     * What plays, for companion/bin/qa's road step in a debug build (`adb shell dumpsys activity service
     * si.lanisce.lani/.road.RoadService`): the session, the item, its piece ([RoadPlay.pieces]) and what that is (a
     * pause to say the Slovene, a clip …), the player's state; and how many items each session has on the phone.
     */
    override fun dump(fd: FileDescriptor?, writer: PrintWriter, args: Array<out String>?) {
        if (!QaHooks.enabled) return
        val item = current()
        val timeline = player.currentTimeline
        val piece = if (timeline.isEmpty) -1 else player.currentPeriodIndex - timeline.getWindow(player.currentMediaItemIndex, Timeline.Window()).firstPeriodIndex
        val sound = when (val s = RoadControls.sound(item, piece)) {
            null -> "-"
            is Sound.Pause -> "pause" + (s.gap?.let { ":$it" } ?: "")
            is Sound.Clip -> "clip"
            is Sound.Spoken -> "spoken"
            is Sound.Prompt -> "prompt"
        }
        writer.println(
            "road: session=$playing item=${item?.id ?: "-"} piece=$piece/${item?.let { RoadPlay.pieces(it.sounds).size } ?: 0} sound=$sound " +
                "state=${player.playbackState} playing=${player.isPlaying} position=${player.currentPosition} queued=${player.mediaItemCount}",
        )
        writer.println("road: sessions " + ORDER.joinToString(" ") { "$it=${items(it).size}" })
    }

    // --- rating ----------------------------------------------------------------------------------------------

    private val ratedNow = ConcurrentHashMap<String, Boolean>()

    private fun current(): RoadItem? = player.currentMediaItem?.mediaId?.let { queued[it] }

    private fun rate(knew: Boolean) {
        val item = current() ?: return
        val r = item.rate ?: return
        ratedNow[item.id.substringBefore('#')] = knew
        val rating = Rating(r.id, r.pack, if (knew) RoadRatings.KNEW else RoadRatings.DIDNT, System.currentTimeMillis())
        scope.launch(Dispatchers.IO) { store.rate(rating) }
        refreshButtons()
    }

    /**
     * The buttons for the item playing: ✓ and ✗ for a card or a word (filled once pressed), and on every item "🌙 Mirno"
     * ([toggleEasy]; in Mirno "🚗 Za pot").
     */
    private fun buttons(): List<CommandButton> {
        val item = current() ?: return emptyList()
        val easy = playing == EASY
        val toggle = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(if (easy) R.drawable.ic_road_mix else R.drawable.ic_road_easy)
            .setDisplayName(if (easy) "🚗 ${bi("road.title")}" else "🌙 ${bi("road.easy")}")
            .setSessionCommand(EASY_COMMAND)
            .build()
        if (item.rate == null) return listOf(toggle)
        val rated = ratedNow[item.id.substringBefore('#')]
        return listOf(
            CommandButton.Builder(if (rated == true) CommandButton.ICON_THUMB_UP_FILLED else CommandButton.ICON_THUMB_UP_UNFILLED)
                .setDisplayName("✓ ${bi("road.knew")}")
                .setSessionCommand(KNEW_COMMAND)
                .build(),
            CommandButton.Builder(if (rated == false) CommandButton.ICON_THUMB_DOWN_FILLED else CommandButton.ICON_THUMB_DOWN_UNFILLED)
                .setDisplayName("✗ ${bi("road.didnt")}")
                .setSessionCommand(DIDNT_COMMAND)
                .build(),
            toggle,
        )
    }

    private fun refreshButtons() {
        session?.setMediaButtonPreferences(buttons())
    }

    /** The ratings pressed so far go to the app's outbox, and out to the node when it can be reached. */
    private fun commitRatings() {
        scope.launch(Dispatchers.IO) {
            val outbox = Outbox(this@RoadService)
            if (store.commit(outbox) == 0 && outbox.size == 0) return@launch
            val config = runCatching { Prefs(this@RoadService).config() }.getOrNull() ?: return@launch
            val b = Bridge(config)
            runCatching { outbox.flush { b.deliver(it) } }
        }
    }

    private inner class Callback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().add(KNEW_COMMAND).add(DIDNT_COMMAND).add(EASY_COMMAND).build(),
                )
                .setMediaButtonPreferences(buttons())
                .build()

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                KNEW -> rate(true)
                DIDNT -> rate(false)
                TOGGLE_EASY -> toggleEasy()
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(node(ROOT, "Lani", playable = false, browsable = true), params))

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return when (parentId) {
                ROOT -> Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(node(TAB, "🚗 ${bi("road.title")}", playable = false, browsable = true)), params))
                TAB -> later { LibraryResult.ofItemList(ImmutableList.copyOf(sessions()), params) }
                else -> Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            }
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (mediaId) {
                ROOT -> node(ROOT, "Lani", playable = false, browsable = true)
                TAB -> node(TAB, "🚗 ${bi("road.title")}", playable = false, browsable = true)
                in SESSIONS -> node(mediaId, label(mediaId), playable = true, browsable = false)
                else -> queued[mediaId]?.let { mediaItem(it, "") } ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            }
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
            later { resolve(mediaItems).toMutableList() }

        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val session = mediaItems.singleOrNull()?.mediaId?.takeIf { it in SESSIONS }
            return later {
                val list = resolve(mediaItems)
                val start = when {
                    list.isEmpty() -> C.INDEX_UNSET
                    session != null -> 0
                    else -> startIndex.coerceIn(0, list.size - 1)
                }
                MediaSession.MediaItemsWithStartPosition(list, start, if (session != null || list.isEmpty()) C.TIME_UNSET else startPositionMs)
            }
        }

        /** The car's play button with nothing queued (after a restart): "🚗 Za pot". */
        override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            later {
                val list = playlist(MIX)
                if (list.isEmpty()) throw UnsupportedOperationException("nothing on the phone for the road yet")
                MediaSession.MediaItemsWithStartPosition(list, 0, C.TIME_UNSET)
            }
    }

    companion object {
        const val ROOT = "road:root"
        const val TAB = "road:tab"
        const val NONE = "road:none"
        const val MIX = "road:mix"
        const val EASY = "road:easy"
        const val SHADOW = "road:shadow"
        const val REVIEWS = "road:reviews"
        const val WORDS = "road:words"
        const val DIALOGS = "road:dialogs"
        const val STORIES = "road:stories"
        const val TRANSFORMS = "road:transform"
        const val RAPID = "road:rapid"
        const val BUILDS = "road:build"
        const val RIDDLES = "road:riddles"

        /** The sessions in the browse tree's order: the drills' ([RoadDrills]) after the others. */
        val ORDER = RoadMix.SESSIONS
        val SESSIONS = ORDER.toSet()

        const val KNEW = "si.lanisce.lani.road.KNEW"
        const val DIDNT = "si.lanisce.lani.road.DIDNT"
        const val TOGGLE_EASY = "si.lanisce.lani.road.EASY"
        val KNEW_COMMAND = SessionCommand(KNEW, Bundle.EMPTY)
        val DIDNT_COMMAND = SessionCommand(DIDNT, Bundle.EMPTY)
        val EASY_COMMAND = SessionCommand(TOGGLE_EASY, Bundle.EMPTY)
    }
}
