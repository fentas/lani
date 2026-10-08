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
import org.junit.Assert.assertFalse
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

    private fun start(items: List<RoadItem>, quiz: QuizHooks? = null, promptMs: Int = 800, clipMs: Int = 600) {
        val store = RoadStore(tmp.newFolder("road"))
        items.forEach { i -> RoadWork.prompts(i).forEach { wav(store.prompt("en", it), promptMs) } }
        items.flatMap(RoadWork::clips).distinct().forEach { wav(store.clip(it), clipMs) }
        val byId = items.associateBy { it.id }
        exo = TestExoPlayerBuilder(context).setMediaSourceFactory(RoadSources(context, store, { "en" }, byId::get)).build()
        road = RoadPlayer(exo, byId::get, { held += it }, quiz)
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

    // --- a quiz's question ------------------------------------------------------------------------------------------

    /**
     * A question of three options, played: the question (a prompt of 2 s, a pause of 0.7 s), then each option's number (a
     * clip of 1.5 s), a short pause, the option (a prompt of 2 s) and its gap (1.2 s), twice. The first option's number
     * begins at 2.7 s, the second's at 7.55 s (its option at 9.2 s, its gap at 11.2 s). Long pieces: the main thread's view
     * of the position trails the playback a little here.
     */
    private fun quiz(): RoadQuiz.QuizPlay {
        val q = QuizQuestion(
            "meaning:c1", QuizKind.MEANING, "hvala", listOf(Sound.Prompt("What does it mean?")),
            listOf("thank you", "please", "sorry").map { QuizOption(it, Sound.Prompt(it)) }, 0, listOf(Sound.Clip("hvala", listOf("hvala.mp3"))), "hvala",
        )
        val numbers = listOf("ena", "dve", "tri").map { Sound.Clip(it, listOf("$it.mp3")) }
        return RoadQuiz.compose(RoadQuiz.item(q), numbers, listOf(0, 1, 2))
    }

    private val picks = mutableListOf<Int>()

    private fun hooks(play: RoadQuiz.QuizPlay) = object : QuizHooks {
        override fun play(mediaId: String) = play.takeIf { mediaId == it.item.id }
        override fun pick(shown: Int) {
            picks += shown
        }
    }

    /** The sound of the question playing ([RoadPlay.pieces]). */
    private fun sound(play: RoadQuiz.QuizPlay): RoadQuiz.Part = play.parts[RoadPlay.pieces(play.item.sounds)[piece()]]

    /** At [ms] of the question, paused (exact: the position doesn't run on), every piece up to it prepared. */
    private fun at(ms: Long) {
        exo.pause()
        exo.seekTo(0, ms)
        TestPlayerRunHelper.run(exo).untilPendingCommandsAreFullyHandled()
    }

    @Test fun `in a quiz's question next picks the option being read or just read, previous asks it again, play or pause picks too`() {
        val play = quiz()
        start(listOf(play.item, card(2)), hooks(play), promptMs = 2_000, clipMs = 1_500)
        // next or previous on the first item at its start: offered all the same (the car's buttons reach the app)
        assertTrue(road.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        TestPlayerRunHelper.play(exo).untilState(Player.STATE_READY)
        // every piece prepared: the question played through once
        TestPlayerRunHelper.run(exo).untilPosition(1, 100)
        // during the question: on to the options at once
        at(600)
        assertEquals(RoadQuiz.Role.ASK, sound(play).role)
        road.seekToNext()
        TestPlayerRunHelper.run(exo).untilPendingCommandsAreFullyHandled()
        readAll(road)
        assertEquals(2_700, road.currentPosition)
        assertEquals(RoadQuiz.Part(RoadQuiz.Role.NUMBER, 0), sound(play))
        assertTrue(picks.isEmpty())
        // the second option being read: it
        at(10_000)
        assertEquals(RoadQuiz.Part(RoadQuiz.Role.TEXT, 1), sound(play))
        road.seekToNext()
        assertEquals(listOf(1), picks)
        assertEquals(0, road.currentMediaItemIndex) // the service plays the feedback; here nothing moves
        // previous: the question again from its start
        road.seekToPrevious()
        TestPlayerRunHelper.run(exo).untilPendingCommandsAreFullyHandled()
        assertEquals(0, road.currentMediaItemIndex)
        assertEquals(0, road.currentPosition)
        // just into "Dve" (the grace after the first): the first; later in it, the second
        at(7_800)
        assertEquals(RoadQuiz.Part(RoadQuiz.Role.NUMBER, 1), sound(play))
        road.seekToNextMediaItem() // the sheet's ⏭
        at(8_300)
        road.seekToNext()
        assertEquals(listOf(1, 0, 1), picks)
        // in the gap after the third: play or pause picks it, and the question plays on
        at(16_500)
        assertEquals(RoadQuiz.Part(RoadQuiz.Role.GAP, 2), sound(play))
        exo.play()
        road.pause()
        assertEquals(listOf(1, 0, 1, 2), picks)
        assertTrue(road.playWhenReady)
        // during the question play or pause pauses
        at(100)
        exo.play()
        road.setPlayWhenReady(false)
        assertFalse(road.playWhenReady)
        assertEquals(4, picks.size)
        // not a question (a card): next as ever, the answer at once in the pause to say it
        exo.seekTo(1, 2_500) // its prompt is 2 s here: the pause to say it
        TestPlayerRunHelper.run(exo).untilPendingCommandsAreFullyHandled()
        assertEquals(1, piece())
        road.seekToNext()
        TestPlayerRunHelper.run(exo).untilPendingCommandsAreFullyHandled()
        assertEquals(1, road.currentMediaItemIndex)
        assertEquals(2, piece())
        assertEquals(4, picks.size)
        readAll(road)
    }
}
