package si.lanisce.lani.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Process
import android.util.Log
import si.lanisce.lani.game.ambient.AmbientBus
import si.lanisce.lani.game.ambient.AmbientMixer
import si.lanisce.lani.game.ambient.Cue
import si.lanisce.lani.game.ambient.Layer
import si.lanisce.lani.game.ambient.LoopCut
import si.lanisce.lani.game.ambient.Sound
import si.lanisce.lani.game.ambient.Thunder
import si.lanisce.lani.game.scene.Lightning
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Plays the background loops: [AmbientMixer]'s blocks through one [AudioTrack] (the media volume: a game's sound), on a
 * thread of its own. [set] says what plays and how loud; a loop is decoded from its asset the first time it's wanted and
 * kept. The short sounds ([Sound]) join in: [play] gives them with their times on the picture's clock, and [sky] the
 * lightning, whose flashes the thread looks for on that clock as it goes, each flash's thunder waiting for its time
 * ([Thunder]). Under a voice ([talking]) it all ducks; while the microphone records ([micOpen]) or the app is away ([away])
 * the track stops at once and what was still to come is dropped; with nothing to play it pauses, the thread asleep.
 */
internal class AmbientPlayer(
    private val context: Context,
    private val talking: () -> Boolean,
    private val micOpen: () -> Boolean,
) {
    private val lock = Object()
    private val n = Layer.entries.size

    // written on the main thread, read by the mixer's, under [lock]
    private val targets = FloatArray(n)
    private var on = false
    private var volume = AmbientBus.DEFAULT_VOLUME
    private var away = false
    private var lightning: Lightning? = null
    private var origin: () -> Long = { -1L }
    private var heard = 1f
    private var shown = false

    @Volatile
    private var released = false
    @Volatile
    private var thread: Thread? = null

    /** Loops decoded and waiting for the mixer's thread to take them. */
    private val ready = ConcurrentHashMap<Layer, ShortArray>()
    /** Loops asked for (decoding, decoded or failed): each is decoded once. */
    private val asked = ConcurrentHashMap.newKeySet<Layer>()

    /** The short sounds decoded, and those asked for (each decoded once). */
    private val shots = ConcurrentHashMap<Sound, ShortArray>()
    private val askedShots = ConcurrentHashMap.newKeySet<Sound>()

    /** Short sounds given ([play]) for the thread to take. */
    private val incoming = ConcurrentLinkedQueue<Pending>()

    /** A short sound to begin at [at] (System.nanoTime). */
    private class Pending(val sound: Sound, val at: Long, val gain: Float, val rate: Float)

    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "ambient-load").apply { isDaemon = true } }

    /** The loops at [levels] (0..1), the whole at the learner's [volume] (0..1) while [on]. */
    fun set(levels: Map<Layer, Float>, on: Boolean, volume: Float) {
        synchronized(lock) {
            for (l in Layer.entries) targets[l.ordinal] = levels[l] ?: 0f
            this.on = on
            this.volume = volume
            lock.notifyAll()
        }
        if (!on || levels.isEmpty()) return
        levels.keys.forEach(::want)
        start()
    }

    /**
     * The picture on screen: its clock's [origin] (System.nanoTime of its second 0, -1 before its first frame; read as the
     * thread goes), its [lightning] (null: none) and how much of the thunder is heard there ([heard]); [shown]: a place is
     * on screen at all (with none, nothing still to come is played).
     */
    fun sky(lightning: Lightning?, origin: () -> Long, heard: Float, shown: Boolean) {
        val on = synchronized(lock) {
            this.lightning = lightning
            this.origin = origin
            this.heard = heard
            this.shown = shown
            lock.notifyAll()
            this.on
        }
        // a place with nothing else to play (a church on a clear day) still has its thunder to look for
        if (lightning != null && on) start()
    }

    /** [cues] to play when their time comes on the clock of [origin] (System.nanoTime of its second 0). */
    fun play(cues: List<Cue>, origin: Long) {
        if (cues.isEmpty() || !synchronized(lock) { on }) return
        for (c in cues) {
            want(c.sound)
            incoming.add(Pending(c.sound, origin + (c.at * 1e9).toLong(), c.gain, c.rate))
        }
        wake()
        start()
    }

    /** Decodes [sounds] now, so they're ready when wanted (a tap is heard at once). */
    fun prepare(sounds: Set<Sound>) = sounds.forEach(::want)

    /** The app left the screen ([away] true) or came back. */
    fun away(away: Boolean) = synchronized(lock) {
        this.away = away
        lock.notifyAll()
    }

    /** Something the thread waits for changed (the microphone): look again now. */
    fun wake() = synchronized(lock) { lock.notifyAll() }

    fun release() {
        released = true
        wake()
        loader.shutdownNow()
    }

    private fun start() {
        if (thread != null || released) return
        thread = Thread(::run, "ambient").apply { isDaemon = true; start() }
    }

    private fun want(layer: Layer) {
        if (!asked.add(layer)) return
        loader.execute {
            val pcm = runCatching { decode(layer.file)?.let { LoopCut.cut(it, layer.seconds * AmbientMixer.RATE) } }
                .onFailure { Log.w(TAG, "can't decode ${layer.file}", it) }.getOrNull()
            if (pcm != null && pcm.isNotEmpty()) {
                ready[layer] = pcm
                wake()
            }
        }
    }

    private fun want(sound: Sound) {
        if (released || !askedShots.add(sound)) return
        loader.execute {
            val pcm = runCatching { decode(sound.file) }.onFailure { Log.w(TAG, "can't decode ${sound.file}", it) }.getOrNull()
            if (pcm != null && pcm.size > 1) shots[sound] = pcm
        }
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val mixer = AmbientMixer()
        val block = ShortArray(BLOCK)
        val want = FloatArray(n)
        var track: AudioTrack? = null
        var playing = false
        val random = Random(System.nanoTime())
        // the short sounds waiting for their time; the picture's clock when its flashes were last looked at, and whose clock
        val pending = ArrayList<Pending>()
        var looked = Double.NaN
        var lookedOrigin = -1L
        try {
            while (!released) {
                for (l in Layer.entries) ready.remove(l)?.let { mixer.load(l, it, start = random.nextInt(it.size)) }
                while (true) pending.add(incoming.poll() ?: break)
                val on: Boolean
                val volume: Float
                val away: Boolean
                val lightning: Lightning?
                val clock: () -> Long
                val heard: Float
                val shown: Boolean
                synchronized(lock) {
                    targets.copyInto(want)
                    on = this.on
                    volume = this.volume
                    away = this.away
                    lightning = this.lightning
                    clock = this.origin
                    heard = this.heard
                    shown = this.shown
                }
                val now = System.nanoTime()
                val talking = talking()
                val busTarget = AmbientBus.target(on, volume, talking)
                val silenced = AmbientBus.silenced(micOpen(), away)
                // the flashes since the last look (the picture drew them): their thunder waits for its time. Nothing is
                // looked at while the sounds are stopped, and a new clock (another screen) starts looking afresh.
                val origin = if (lightning != null && on && shown && !silenced) clock() else -1L
                if (lightning == null || origin < 0) looked = Double.NaN
                else {
                    val pic = (now - origin) / 1e9
                    if (origin == lookedOrigin && pic > looked) {
                        for (c in Thunder.after(lightning, looked, pic, heard)) {
                            want(c.sound)
                            pending.add(Pending(c.sound, origin + (c.at * 1e9).toLong(), c.gain, c.rate))
                        }
                    }
                    if (origin != lookedOrigin || !(looked >= pic)) looked = pic
                    lookedOrigin = origin
                }
                // switched off, stopped, or nowhere on screen: what was still to come isn't played
                if (!on || silenced || !shown) pending.clear()
                if (silenced || (mixer.silent(want, busTarget) && pending.isEmpty())) {
                    if (playing) {
                        // the platform ramps a pause down in a few milliseconds: no click, and nothing left queued
                        track?.pause()
                        track?.flush()
                        playing = false
                    }
                    if (silenced) mixer.stop() else mixer.hush()
                    synchronized(lock) { if (!released) lock.wait(IDLE_MS) }
                    continue
                }
                // what begins in this block: it is heard once the track has played what it holds before it
                val start = now + (if (playing) track?.bufferSizeInFrames ?: 0 else 0) * NANOS / AmbientMixer.RATE
                val end = start + BLOCK * NANOS / AmbientMixer.RATE
                val due = pending.iterator()
                while (due.hasNext()) {
                    val p = due.next()
                    if (p.at >= end) continue
                    val late = start - p.at
                    val pcm = shots[p.sound]
                    if (pcm == null && late <= LATE_NS && p.sound in askedShots) continue // still decoding
                    due.remove()
                    if (pcm != null && late <= LATE_NS) {
                        mixer.play(pcm, p.gain, p.rate, ((p.at - start).coerceAtLeast(0L) * AmbientMixer.RATE / NANOS).toInt())
                    }
                }
                mixer.mix(block, want, busTarget, AmbientBus.seconds(mixer.bus, busTarget, talking))
                val t = track ?: newTrack()?.also { track = it } ?: return
                if (!playing) {
                    t.play()
                    playing = true
                }
                if (t.write(block, 0, block.size) < 0) {
                    // the audio went away (a device change): a new track next time
                    t.release()
                    track = null
                    playing = false
                }
            }
        } catch (e: InterruptedException) {
            // released
        } catch (e: Exception) {
            Log.w(TAG, "background sounds stopped", e)
        } finally {
            runCatching { track?.release() }
            thread = null
        }
    }

    private fun newTrack(): AudioTrack? = runCatching {
        val min = AudioTrack.getMinBufferSize(AmbientMixer.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(AmbientMixer.RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setBufferSizeInBytes(maxOf(min, BLOCK * 2 * 3))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }.onFailure { Log.w(TAG, "no audio track", it) }.getOrNull()

    /** The asset `ambient/<file>.ogg` decoded to 16-bit mono at [AmbientMixer.RATE]. */
    private fun decode(file: String): ShortArray? {
        val ex = MediaExtractor()
        try {
            val path = "$DIR/$file.ogg"
            try {
                context.assets.openFd(path).use { ex.setDataSource(it) }
            } catch (e: java.io.FileNotFoundException) {
                // stored compressed in the APK: read it from a copy in the cache
                val copy = File(context.cacheDir, "ambient-$file.ogg")
                if (!copy.exists()) context.assets.open(path).use { input -> copy.outputStream().use { input.copyTo(it) } }
                ex.setDataSource(copy.path)
            }
            val index = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return null
            ex.selectTrack(index)
            val format = ex.getTrackFormat(index)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var float = false
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            val out = Samples()
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var idle = 0 // tries without output since the input ended: a decoder that never ends isn't waited for
                while (idle < MAX_IDLE) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (i >= 0) {
                            val size = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (o == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) idle++
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    } else if (o >= 0) {
                        idle = 0
                        val buf = codec.getOutputBuffer(o)!!.apply { position(info.offset); limit(info.offset + info.size) }.order(ByteOrder.nativeOrder())
                        if (float) {
                            val fb = buf.asFloatBuffer()
                            while (fb.remaining() >= channels) {
                                var sum = 0f
                                repeat(channels) { sum += fb.get() }
                                out.add((sum / channels * 32767f).toInt())
                            }
                        } else {
                            val sb = buf.asShortBuffer()
                            while (sb.remaining() >= channels) {
                                var sum = 0
                                repeat(channels) { sum += sb.get() }
                                out.add(sum / channels)
                            }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            return if (rate == AmbientMixer.RATE) out.toArray() else resample(out.toArray(), rate, AmbientMixer.RATE)
        } finally {
            ex.release()
        }
    }

    /** A growing buffer of samples. */
    private class Samples {
        private var a = ShortArray(1 shl 16)
        private var size = 0

        fun add(v: Int) {
            if (size == a.size) a = a.copyOf(a.size * 2)
            a[size++] = v.coerceIn(-32768, 32767).toShort()
        }

        fun toArray(): ShortArray = a.copyOf(size)
    }

    companion object {
        private const val TAG = "Ambience"

        /** The loops' folder in the assets. */
        const val DIR = "ambient"

        /** Samples per block written to the track: about 43 ms. */
        private const val BLOCK = 1024

        /** How long the thread sleeps with nothing to play, between looks (a change wakes it sooner). */
        private const val IDLE_MS = 1_000L

        /** A short sound this late (its sound still decoding, the thread held up) is played late; later, it's left out. */
        private const val LATE_NS = 400_000_000L

        private const val NANOS = 1_000_000_000L

        private const val TIMEOUT_US = 10_000L

        /** A second of nothing from the decoder after the input ended: it is done. */
        private const val MAX_IDLE = 100

        /** [pcm] at [from] Hz to [to] Hz, linearly (the loops are made at the player's rate; this is for a file that isn't). */
        fun resample(pcm: ShortArray, from: Int, to: Int): ShortArray {
            if (pcm.isEmpty() || from <= 0 || from == to) return pcm
            val out = ShortArray((pcm.size.toLong() * to / from).toInt())
            for (i in out.indices) {
                val x = i.toDouble() * from / to
                val j = x.toInt().coerceAtMost(pcm.size - 1)
                val k = (j + 1).coerceAtMost(pcm.size - 1)
                val f = x - j
                out[i] = (pcm[j] * (1 - f) + pcm[k] * f).toInt().toShort()
            }
            return out
        }
    }
}
