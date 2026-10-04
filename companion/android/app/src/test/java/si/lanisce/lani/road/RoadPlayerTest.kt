package si.lanisce.lani.road

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The road's player (RoadControls' [RoadPlayer]) over a real ExoPlayer and a media session, as the car has it: an item is
 * one window of its pieces, a period each ([RoadSources]: prompts and clips from files, pauses of silence), and a piece
 * not prepared yet has no duration. The session reads the player's whole state at every change; the test reads it too.
 * A player that rebuilds the timeline as a playlist (0.1.543's ForwardingSimpleBasePlayer) threw here ("Periods other than
 * last need a duration") and closed the app as a session started.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoadPlayerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var exo: ExoPlayer
    private lateinit var road: RoadPlayer
    private lateinit var session: MediaSession
    private val held = mutableListOf<Boolean>()
    private val window = Timeline.Window()

    /** [ms] of 16-bit mono silence at 16 kHz, as a WAV file (what the phone's voice renders a prompt to). */
    private fun wav(to: File, ms: Int) {
        val data = 16_000 * 2 * ms / 1000
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        to.parentFile?.mkdirs()
        to.writeBytes(b.array())
    }

    private fun card(n: Int) = RoadItem(
        "card:$n", Kind.CARD, "hvala $n", "thank you $n",
        listOf(
            Sound.Prompt("thank you $n"), Sound.Pause(2_500, Gap.SAY), Sound.Clip("hvala", listOf("hvala.mp3")),
            Sound.Pause(2_000, Gap.REPEAT), Sound.Clip("hvala", listOf("hvala.mp3")), Sound.Pause(1_200),
        ),
        Rate("$n"),
    )

    /** Everything a session or a car reads of the player, read. */
    private fun readAll(p: Player) {
        p.availableCommands
        p.playbackState
        p.playWhenReady
        p.isPlaying
        p.currentTimeline
        p.currentMediaItem
        p.currentMediaItemIndex
        p.currentPeriodIndex
        p.currentPosition
        p.contentPosition
        p.bufferedPosition
        p.totalBufferedDuration
        p.duration
        p.contentDuration
        p.isCurrentMediaItemSeekable
        p.isCurrentMediaItemDynamic
        p.hasNextMediaItem()
        p.hasPreviousMediaItem()
        p.mediaMetadata
        p.playlistMetadata
        p.playbackParameters
        p.repeatMode
        p.shuffleModeEnabled
        p.deviceInfo
        p.audioAttributes
        p.currentTracks
        p.trackSelectionParameters
        p.maxSeekToPreviousPosition
        p.seekBackIncrement
        p.seekForwardIncrement
    }

    private fun start(items: List<RoadItem>) {
        val store = RoadStore(tmp.newFolder("road"))
        items.forEach { i -> RoadWork.prompts(i).forEach { wav(store.prompt("en", it), 800) } }
        items.flatMap(RoadWork::clips).distinct().forEach { wav(store.clip(it), 600) }
        val byId = items.associateBy { it.id }
        exo = TestExoPlayerBuilder(context).setMediaSourceFactory(RoadSources(context, store, { "en" }, byId::get)).build()
        road = RoadPlayer(exo, byId::get) { held += it }
        road.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = readAll(player)
        })
        session = MediaSession.Builder(context, road).setId("road-test").build()
        road.setMediaItems(items.map { MediaItem.Builder().setMediaId(it.id).build() })
        road.prepare()
        readAll(road)
    }

    @After fun release() {
        if (::session.isInitialized) session.release()
        if (::road.isInitialized) road.release()
    }

    /** The piece of the item playing ([RoadPlay.pieces]). */
    private fun piece(): Int {
        road.currentTimeline.getWindow(road.currentMediaItemIndex, window)
        return road.currentPeriodIndex - window.firstPeriodIndex
    }

    @Test fun `a session plays a road item, its pieces a period each, its state read at every step`() {
        start(listOf(card(1), card(2)))
        TestPlayerRunHelper.play(exo).untilState(Player.STATE_READY)
        readAll(road)
        // the ExoPlayer's timeline as it is: an item one window of six pieces
        assertEquals(2, road.currentTimeline.windowCount)
        road.currentTimeline.getWindow(0, window)
        assertEquals(6, window.lastPeriodIndex - window.firstPeriodIndex + 1)
        // what a car can send while something is queued: next, and fast-forward and rewind for a held button
        for (c in listOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_BACK)) {
            assertTrue("$c", road.isCommandAvailable(c) && road.availableCommands.contains(c))
        }
        TestPlayerRunHelper.run(exo).untilPosition(0, 1_200)
        assertEquals(1, piece()) // the pause to say it
        road.seekToNext()
        TestPlayerRunHelper.run(exo).untilPendingCommandsAreFullyHandled()
        readAll(road)
        assertEquals(0, road.currentMediaItemIndex)
        assertEquals(2, piece()) // the answer at once
        road.seekToNext()
        TestPlayerRunHelper.run(exo).untilPendingCommandsAreFullyHandled()
        assertEquals(1, road.currentMediaItemIndex) // any other moment: the next item
        road.seekForward()
        road.seekBack()
        assertEquals(listOf(true, false), held)
        TestPlayerRunHelper.play(exo).untilState(Player.STATE_ENDED)
        readAll(road)
    }

    @Test fun `a session reads the player's state while its pieces are still being prepared`() {
        // many pieces, not all prepared at once: some have no duration yet
        start((1..6).map(::card))
        var reads = 0
        road.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                readAll(road)
                reads++
            }
        })
        road.play()
        TestPlayerRunHelper.run(exo).untilState(Player.STATE_READY)
        TestPlayerRunHelper.run(exo).untilPosition(2, 500)
        assertTrue("$reads", reads > 0)
        assertEquals(2, road.currentMediaItemIndex)
    }
}
