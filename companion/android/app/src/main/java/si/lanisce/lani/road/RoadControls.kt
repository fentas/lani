package si.lanisce.lani.road

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi

/**
 * What the car's buttons do by what plays (companion/README.md, "Im Auto · In the car", Controls). Pure: [RoadPlayer]
 * asks it, the tests check it.
 */
object RoadControls {
    /** What next does. */
    sealed interface Next {
        /** On to the next item, the player's own next. */
        data object Skip : Next

        /** The answer at once: the item's piece [piece] ([RoadPlay.pieces]), the Slovene after the pause to say it. */
        data class Reveal(val piece: Int) : Next
    }

    /**
     * Next while piece [piece] of [item] plays: during a pause to say the Slovene ([Gap.SAY]) the answer at once (the learner
     * said it already), at any other moment the next item.
     */
    fun next(item: RoadItem?, piece: Int): Next {
        val sounds = item?.sounds ?: return Next.Skip
        val s = sound(item, piece)
        return if (s is Sound.Pause && s.gap == Gap.SAY && piece + 1 < RoadPlay.pieces(sounds).size) Next.Reveal(piece + 1) else Next.Skip
    }

    /** The sound piece [piece] of [item] plays ([RoadPlay.pieces]), or null. */
    fun sound(item: RoadItem?, piece: Int): Sound? {
        val sounds = item?.sounds ?: return null
        return RoadPlay.pieces(sounds).getOrNull(piece)?.let(sounds::get)
    }

    /**
     * A button held (the car's fast-forward, [forward], or rewind): on a card or a word 👍 "Znal sem" (true) or 👎 "Nisem"
     * (false), as the ✓ and ✗ buttons do; on anything else nothing (null).
     */
    fun hold(item: RoadItem?, forward: Boolean): Boolean? = if (item?.rate != null) forward else null
}

/**
 * The road's player as the session and the car see it: the service's ExoPlayer ([exo]) with next and the held buttons as
 * [RoadControls] has them. It offers fast-forward and rewind always while something is queued (a car sends them for a
 * steering-wheel button held), and next on the last item too (to hear its answer). [itemOf] finds the item of a media id;
 * [onHold] records 👍 (true) or 👎 (false).
 *
 * A plain [ForwardingPlayer]: it hands the session the ExoPlayer's own timeline as it is. (A ForwardingSimpleBasePlayer
 * rebuilds it as a playlist, whose items' periods other than the last must have a duration; an item here is one window of
 * its pieces, a period each (RoadSources), and a piece not prepared yet has none, so starting a session crashed the app:
 * "Periods other than last need a duration".)
 */
@UnstableApi
class RoadPlayer(
    private val exo: Player,
    private val itemOf: (String) -> RoadItem?,
    private val onHold: (Boolean) -> Unit,
) : ForwardingPlayer(exo) {
    private val window = Timeline.Window()
    private val period = Timeline.Period()

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (exo.mediaItemCount == 0) return base
        return base.buildUpon().addAll(Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_TO_NEXT).build()
    }

    override fun isCommandAvailable(command: Int): Boolean =
        (exo.mediaItemCount > 0 && command in HELD) || super.isCommandAvailable(command)

    override fun seekToNext() {
        val at = reveal()
        if (at != null) exo.seekTo(at) else super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        val at = reveal()
        if (at != null) exo.seekTo(at) else super.seekToNextMediaItem()
    }

    override fun seekForward() {
        RoadControls.hold(current(), true)?.let(onHold)
    }

    override fun seekBack() {
        RoadControls.hold(current(), false)?.let(onHold)
    }

    private fun current(): RoadItem? = exo.currentMediaItem?.mediaId?.let(itemOf)

    /** Where the answer starts, when next should play it now ([RoadControls.next]): a position in the item playing. */
    private fun reveal(): Long? {
        val timeline = exo.currentTimeline
        if (timeline.isEmpty) return null
        timeline.getWindow(exo.currentMediaItemIndex, window)
        // an item is one window of its pieces, a period each (RoadSources)
        val next = RoadControls.next(current(), exo.currentPeriodIndex - window.firstPeriodIndex) as? RoadControls.Next.Reveal ?: return null
        val p = window.firstPeriodIndex + next.piece
        if (p > window.lastPeriodIndex) return null
        return (timeline.getPeriod(p, period).positionInWindowUs + 999) / 1000
    }

    private companion object {
        val HELD = setOf(Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_TO_NEXT)
    }
}
