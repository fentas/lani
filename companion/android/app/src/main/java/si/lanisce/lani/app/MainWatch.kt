package si.lanisce.lani.app

import android.os.Debug
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Printer
import si.lanisce.lani.data.NetWatch

/**
 * QA's watch on the main thread (QaHooks' "main:watch" and "main:report:<from>"; docs/plans/05-built/home-return.md): how
 * long each message of the main thread's loop took, from the Looper's own logging (a line before each message and one
 * after), kept for the last [SIZE] messages. The report logs (tag [TAG]) the messages that began from a moment on
 * ([SystemClock.elapsedRealtime], ms: QA reads it as /proc/uptime) for [WINDOW_MS]: how many, their sum, the longest and
 * how many took longer than a frame, all of them and those besides the screen's frames (Choreographer's: a frame's
 * message also waits for the screen's render thread, which an emulator's software graphics make long; dumpsys gfxinfo
 * framestats says a frame's own work). A method trace can't say it: its sampling waits for every garbage collection, and
 * the main thread looks blocked where it wasn't; this costs a line of text per message and nothing else.
 *
 * The same report says what else the moment cost (docs/plans/05-built/nav-perf.md), over the longer [NET_WINDOW_MS]: the
 * requests the app made ([NetWatch]: how many, each one's path, status and length), and what the runtime allocated on
 * every thread and how many collections it ran (ART's own counts, `art.gc.bytes-allocated` and `art.gc.gc-count`, read
 * every [SAMPLE_MS] by a thread of the watch's: a few short strings a second).
 */
internal object MainWatch {
    const val TAG = "LaniMain"
    private const val SIZE = 4096
    const val WINDOW_MS = 3_000L
    const val NET_WINDOW_MS = 6_000L
    private const val SAMPLE_MS = 50L
    private const val SAMPLES = 4096

    // written and read on the main thread only (the Looper's printer, the hook's report)
    private val began = LongArray(SIZE)
    private val took = LongArray(SIZE)
    private val frame = BooleanArray(SIZE)
    private var count = 0
    private var startNs = 0L
    private var startAt = 0L
    private var isFrame = false

    private val printer = Printer { line ->
        if (line.startsWith(">")) {
            startNs = System.nanoTime()
            startAt = SystemClock.elapsedRealtime()
            isFrame = "Choreographer" in line
        } else if (line.startsWith("<") && startNs != 0L) {
            val i = count % SIZE
            began[i] = startAt
            took[i] = System.nanoTime() - startNs
            frame[i] = isFrame
            count++
            startNs = 0L
        }
    }

    // the runtime's counts, sampled by the watch's own thread: when, the bytes allocated so far, the collections so far
    private val sampledAt = LongArray(SAMPLES)
    private val allocated = LongArray(SAMPLES)
    private val collections = LongArray(SAMPLES)
    private var samples = 0
    private var sampler: Thread? = null

    private fun stat(name: String): Long = Debug.getRuntimeStat(name)?.toLongOrNull() ?: 0L

    /** From now on each main-thread message is timed, the app's requests logged and the runtime's allocations sampled. */
    fun watch() {
        Looper.getMainLooper().setMessageLogging(printer)
        NetWatch.on = true
        synchronized(sampledAt) {
            if (sampler != null) return
            sampler = Thread({
                while (true) {
                    val at = SystemClock.elapsedRealtime()
                    val bytes = stat("art.gc.bytes-allocated")
                    val gcs = stat("art.gc.gc-count")
                    synchronized(sampledAt) {
                        val i = samples % SAMPLES
                        sampledAt[i] = at; allocated[i] = bytes; collections[i] = gcs
                        samples++
                    }
                    Thread.sleep(SAMPLE_MS)
                }
            }, "LaniAllocWatch").apply { isDaemon = true; start() }
        }
    }

    /** The runtime's counts at the last sample before [at] and at the first from [until] on; null without both. */
    private fun counted(at: Long, until: Long): Pair<Long, Long>? = synchronized(sampledAt) {
        val all = (maxOf(0, samples - SAMPLES) until samples).map { it % SAMPLES }
        val a = all.lastOrNull { sampledAt[it] <= at } ?: return null
        val b = all.firstOrNull { sampledAt[it] >= until } ?: all.lastOrNull() ?: return null
        (allocated[b] - allocated[a]) to (collections[b] - collections[a])
    }

    /** The messages that began within [WINDOW_MS] from [from] (elapsedRealtime, ms), said in a line and logged. */
    fun report(from: Long): String {
        val all = (maxOf(0, count - SIZE) until count).map { it % SIZE }.filter { began[it] in from until from + WINDOW_MS }
        fun f(v: Double) = String.format(java.util.Locale.ROOT, "%.1f", v)
        fun said(ms: List<Double>) = "${ms.size} messages, ${f(ms.sum())} ms, the longest ${f(ms.maxOrNull() ?: 0.0)} ms, " +
            "${ms.count { it > 16.7 }} over 16.7 ms, ${ms.count { it > 100.0 }} over 100 ms"
        val line = "main from $from: ${said(all.map { took[it] / 1e6 })}; besides the frames: ${said(all.filterNot { frame[it] }.map { took[it] / 1e6 })}"
        Log.i(TAG, line)
        Log.i(TAG, net(from))
        return line
    }

    /**
     * The requests that began within [NET_WINDOW_MS] from [from] and what the runtime allocated then: "net from <from>: 3
     * requests in 6000 ms, 12.5 MB allocated, 2 collections: GET /state 200 2510345 B, …".
     */
    private fun net(from: Long): String {
        val until = from + NET_WINDOW_MS
        val reqs = NetWatch.between(from, until)
        val (bytes, gcs) = counted(from, until) ?: (-1L to -1L)
        val mb = if (bytes < 0) "?" else String.format(java.util.Locale.ROOT, "%.1f", bytes / 1048576.0)
        val list = reqs.joinToString(", ") { "${it.what} ${it.code} ${it.length} B" }
        return "net from $from: ${reqs.size} requests in $NET_WINDOW_MS ms, $mb MB allocated, ${if (gcs < 0) "?" else gcs} collections: $list"
    }
}
