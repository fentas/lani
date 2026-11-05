package si.lanisce.lani.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.serialization.Serializable
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.data.json
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A line with the learner's name, joined on the phone (docs/plans/05-built/audio-names.md): the node sends a manifest of shared
 * pieces and the name's clip, each an MP3 any other line may use too, and they become one sound here, sample by sample: the
 * pauses are the manifest's, and there is no seam a player could open a gap in. The line is then a clip like any other: the
 * file under the line's name in the clip cache, played, kept and dropped as they are. It holds WAV, not MP3 (the phone has no
 * MP3 encoder); players read what a file holds, not its name.
 */
@Serializable
data class LinePart(val kind: String, val url: String? = null, val ms: Int = 0, val gain: Float = 0f)

/** GET /voice/line/<id>.json. */
@Serializable
data class LineManifest(val v: Int, val id: String, val text: String = "", val voice: String = "", val parts: List<LinePart>)

/** What goes into a line: samples at a gain (dB), or a pause. */
sealed interface JoinPart {
    class Sound(val samples: ShortArray, val gain: Float = 0f) : JoinPart
    class Gap(val ms: Int) : JoinPart
}

object LineJoin {
    /** The rate the node's clips are made at. */
    const val RATE = 44_100

    /** The manifests this app reads. */
    const val VERSION = 1

    /** The node's URL of a joined line: its finished file there, its manifest beside it. */
    fun isLine(url: String): Boolean = url.substringBefore('?').let { it.contains("/voice/line/") && it.endsWith(".mp3") }

    fun manifestUrl(url: String): String = url.substringBefore('?').removeSuffix(".mp3") + ".json"

    /** A manifest this app can join (a later version of it: null, and the finished file comes instead); null when it isn't one. */
    fun parse(raw: String): LineManifest? {
        val m = runCatching { json.decodeFromString(LineManifest.serializer(), raw) }.getOrNull() ?: return null
        val usable = m.v == VERSION && m.parts.isNotEmpty() && m.parts.all { p -> if (p.kind == "gap") p.ms in 0..MAX_GAP_MS else p.kind in SOUNDS && !p.url.isNullOrBlank() }
        return m.takeIf { usable }
    }

    /** [parts] as one sound: each in turn, a gain applied and held within the samples' range. */
    fun join(parts: List<JoinPart>): ShortArray {
        val n = parts.sumOf { p -> when (p) { is JoinPart.Sound -> p.samples.size; is JoinPart.Gap -> gapSamples(p.ms) } }
        val out = ShortArray(n)
        var at = 0
        for (p in parts) when (p) {
            is JoinPart.Gap -> at += gapSamples(p.ms)
            is JoinPart.Sound -> {
                val g = 10.0.pow(p.gain / 20.0).toFloat()
                for (s in p.samples) out[at++] = if (p.gain == 0f) s else (s * g).roundToInt().coerceIn(-32_768, 32_767).toShort()
            }
        }
        return out
    }

    fun gapSamples(ms: Int): Int = (ms.toLong() * RATE / 1000).toInt()

    /** [samples] (mono, 16 bits) as a WAV file's bytes. */
    fun wav(samples: ShortArray, rate: Int = RATE): ByteArray {
        val data = samples.size * 2
        val b = ByteBuffer.allocate(WAV_HEADER + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(WAV_HEADER - 8 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (s in samples) b.putShort(s)
        return b.array()
    }

    /**
     * Where a part's [want] samples start in the [decoded] a decoder gave. The manifest says a part's true length, and that tells
     * what the decoder did: one that honours the gapless header gives that length and nothing to take off (an emulator on
     * API 36 did, to a sample; taking the delay off as well cost every part 13 ms of its start); one that doesn't gives the
     * encoder's [delay] before the sound and the padding after it.
     */
    fun soundStart(decoded: Int, want: Int, delay: Int): Int = if (decoded - want <= TRIMMED_SLACK) 0 else delay.coerceAtMost(decoded - want)

    /** A decoder gave all of a part: else the line would be kept with a hole in it, the name maybe, and the finished file comes instead. */
    fun whole(decoded: Int, want: Int): Boolean = decoded >= want - TRIMMED_SLACK

    /** The MP3 [file] as the samples its encoder was given: [ms] of what the decoder gives, from [soundStart]. */
    fun samplesOf(file: File, ms: Int): ShortArray {
        val all = Mp3Decoder.decode(file)
        val want = (ms.toLong() * RATE / 1000).toInt()
        val delay = Mp3Gapless.delay(file)
        val from = soundStart(all.size, want, delay)
        if (BuildConfig.QA) Log.i(TAG, "decoded ${file.name}: ${all.size} samples, wanted $want, from $from")
        if (!whole(all.size, want)) throw IOException("${file.name} decoded to ${all.size} samples of $want")
        return all.copyOfRange(from, (from + want).coerceAtMost(all.size))
    }

    /** [parts] joined into [to], whole or not at all. */
    fun write(parts: List<JoinPart>, to: File) {
        val tmp = File(to.path + ".part")
        val joined = join(parts)
        tmp.writeBytes(wav(joined))
        if (!tmp.renameTo(to)) throw IOException("could not store ${to.name}")
        if (BuildConfig.QA) Log.i(TAG, "joined ${to.name}: ${parts.size} parts, ${joined.size} samples, ${joined.size * 1000L / RATE} ms")
    }

    /** A decoder that trimmed gives a part's length within a rounding of the manifest's milliseconds. */
    private const val TRIMMED_SLACK = 64

    private const val TAG = "LineJoin"
    private const val WAV_HEADER = 44
    private const val MAX_GAP_MS = 2_000
    private val SOUNDS = setOf("name", "piece")
}

/** What the encoder of an MP3 put before its sound, from its LAME header. */
object Mp3Gapless {
    /**
     * The samples before the sound where a decoder leaves them ([LineJoin.soundStart]): the encoder's delay; Android takes the
     * decoder's own 529 off itself. LAME's 576 when the header doesn't say.
     */
    const val LAME_DELAY = 576

    fun delay(file: File): Int = file.inputStream().use { s ->
        val b = ByteArray(HEAD)
        val n = s.read(b).coerceAtLeast(0)
        delayIn(b.copyOf(n)) ?: LAME_DELAY
    }

    /** The delay the LAME header in [b] (a file's first bytes) says; null when it has none. */
    fun delayIn(b: ByteArray): Int? {
        var at = 0
        // an ID3 tag first, its length in the 7-bit bytes of its header
        if (b.size > 10 && b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()) {
            at = 10 + (6..9).fold(0) { n, i -> (n shl 7) or (b[i].toInt() and 0x7f) }
        }
        val tag = listOf("Info", "Xing").mapNotNull { find(b, it, at) }.minOrNull() ?: return null
        val flags = int(b, tag + 4) ?: return null
        var p = tag + 8
        if (flags and 1 != 0) p += 4 // frames
        if (flags and 2 != 0) p += 4 // bytes
        if (flags and 4 != 0) p += 100 // the seek table
        if (flags and 8 != 0) p += 4 // quality
        // the encoder's name (9), its version and method, lowpass, replay gain, flags, bitrate: then 12 bits of delay
        val d = p + 21
        if (d + 2 >= b.size) return null
        return ((b[d].toInt() and 0xff) shl 4) or ((b[d + 1].toInt() and 0xf0) shr 4)
    }

    private fun find(b: ByteArray, s: String, from: Int): Int? {
        val w = s.toByteArray()
        val last = (from + SEARCH).coerceAtMost(b.size - w.size)
        for (i in from..last) if (w.indices.all { b[i + it] == w[it] }) return i
        return null
    }

    private fun int(b: ByteArray, at: Int): Int? =
        if (at + 3 >= b.size) null else (0..3).fold(0) { n, i -> (n shl 8) or (b[at + i].toInt() and 0xff) }

    private const val HEAD = 8_192
    private const val SEARCH = 64
}

/** An MP3 as mono samples, through the phone's own decoder. */
internal object Mp3Decoder {
    private const val TIMEOUT_US = 10_000L

    /** How many turns in a row with nothing from the decoder end it: a codec that never comes to its end mustn't hold a line up. */
    private const val MAX_IDLE = 300

    fun decode(file: File): ShortArray {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(file.path)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: throw IOException("no audio in ${file.name}")
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            val out = Pcm()
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var idle = 0
            while (true) {
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val n = ex.readSampleData(c.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, TIMEOUT_US)
                idle = if (o == MediaCodec.INFO_TRY_AGAIN_LATER) idle + 1 else 0
                if (idle > MAX_IDLE) throw IOException("the decoder stopped in ${file.name}")
                when {
                    o >= 0 -> {
                        val buf = c.getOutputBuffer(o)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        out.add(buf.order(ByteOrder.nativeOrder()).asShortBuffer(), channels)
                        c.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> c.outputFormat.let {
                        channels = it.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        rate = it.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                }
            }
            if (rate != LineJoin.RATE) throw IOException("${file.name} is at $rate Hz")
            return out.samples()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }

    /** Samples as they come, the channels of a stereo file mixed. */
    private class Pcm {
        private var a = ShortArray(1 shl 16)
        private var n = 0

        fun add(b: java.nio.ShortBuffer, channels: Int) {
            val frames = b.remaining() / channels
            if (n + frames > a.size) a = a.copyOf(maxOf(a.size * 2, n + frames))
            for (i in 0 until frames) {
                var s = 0
                for (c in 0 until channels) s += b.get()
                a[n++] = (s / channels).toShort()
            }
        }

        fun samples(): ShortArray = a.copyOf(n)
    }
}
