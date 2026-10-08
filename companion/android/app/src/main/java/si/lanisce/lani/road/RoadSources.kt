package si.lanisce.lani.road

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * An item of the road ([RoadItem]) as one media source: its prompts, clips and pauses one after the other, so the player's
 * next and previous go from item to item, not from sound to sound. [itemOf] finds the item of a media id; [base] is the
 * prompts' language. Only files on the phone ([RoadStore]).
 */
@UnstableApi
class RoadSources(
    context: Context,
    private val store: RoadStore,
    private val base: () -> String,
    private val itemOf: (String) -> RoadItem?,
) : MediaSource.Factory {
    private val files = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = apply { files.setDrmSessionManagerProvider(provider) }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = apply { files.setLoadErrorHandlingPolicy(policy) }

    override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val item = itemOf(mediaItem.mediaId)
        val b = ConcatenatingMediaSource2.Builder().setMediaItem(mediaItem)
        var any = false
        // a child source each of the item's pieces, in their order (RoadPlay.pieces: next finds the answer by them)
        for (s in item?.sounds.orEmpty()) {
            val ms = (RoadPlay.seconds(s) * 1000).toLong().coerceAtLeast(1)
            when (s) {
                is Sound.Clip -> s.files.forEach { f ->
                    b.add(files.createMediaSource(MediaItem.fromUri(Uri.fromFile(store.clip(f)))), ms / s.files.size.coerceAtLeast(1))
                    any = true
                }
                is Sound.Prompt -> { b.add(files.createMediaSource(MediaItem.fromUri(Uri.fromFile(store.prompt(base(), s.text)))), ms); any = true }
                // the node's MP3 or the phone's WAV, one file name: the extractors tell them apart by their content
                is Sound.Spoken -> { b.add(files.createMediaSource(MediaItem.fromUri(Uri.fromFile(store.spoken(s.voice, s.text)))), ms); any = true }
                is Sound.Pause -> { b.add(silence(s.ms), s.ms); any = true }
            }
        }
        if (!any) b.add(silence(500), 500) // an item no longer on the phone: a moment of silence, and on
        return b.build()
    }

    private fun silence(ms: Long): MediaSource = SilenceMediaSource.Factory().setDurationUs(ms * 1000).createMediaSource()
}
