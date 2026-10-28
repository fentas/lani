package si.lanisce.lani.app

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Printer

/**
 * QA's watch on the main thread (QaHooks' "main:watch" and "main:report:<from>"; docs/plans/05-built/home-return.md): how
 * long each message of the main thread's loop took, from the Looper's own logging (a line before each message and one
 * after), kept for the last [SIZE] messages. The report logs (tag [TAG]) the messages that began from a moment on
 * ([SystemClock.elapsedRealtime], ms: QA reads it as /proc/uptime) for [WINDOW_MS]: how many, their sum, the longest and
 * how many took longer than a frame, all of them and those besides the screen's frames (Choreographer's: a frame's
 * message also waits for the screen's render thread, which an emulator's software graphics make long; dumpsys gfxinfo
 * framestats says a frame's own work). A method trace can't say it: its sampling waits for every garbage collection, and
 * the main thread looks blocked where it wasn't; this costs a line of text per message and nothing else.
 */
internal object MainWatch {
    const val TAG = "LaniMain"
    private const val SIZE = 4096
    const val WINDOW_MS = 3_000L

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

    /** From now on each main-thread message is timed. */
    fun watch() = Looper.getMainLooper().setMessageLogging(printer)

    /** The messages that began within [WINDOW_MS] from [from] (elapsedRealtime, ms), said in a line and logged. */
    fun report(from: Long): String {
        val all = (maxOf(0, count - SIZE) until count).map { it % SIZE }.filter { began[it] in from until from + WINDOW_MS }
        fun f(v: Double) = String.format(java.util.Locale.ROOT, "%.1f", v)
        fun said(ms: List<Double>) = "${ms.size} messages, ${f(ms.sum())} ms, the longest ${f(ms.maxOrNull() ?: 0.0)} ms, " +
            "${ms.count { it > 16.7 }} over 16.7 ms, ${ms.count { it > 100.0 }} over 100 ms"
        val line = "main from $from: ${said(all.map { took[it] / 1e6 })}; besides the frames: ${said(all.filterNot { frame[it] }.map { took[it] / 1e6 })}"
        Log.i(TAG, line)
        return line
    }
}
