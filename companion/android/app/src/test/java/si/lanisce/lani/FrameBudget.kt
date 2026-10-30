package si.lanisce.lani

import java.lang.management.ManagementFactory

/**
 * The renderers' time budgets in the unit tests, and how a test measures against one.
 *
 * A test measures the CPU time of its own thread (the one that renders), not the wall clock. On a busy machine (other
 * builds, a load of 40 on 24 hardware threads) the thread waits for a CPU for part of the wall time, and that wait
 * says nothing about the renderer: frames of 18 ms took 25 to 28 ms by the wall clock at a load of 33 (October 2026).
 * Each measurement is also the best of [ROUNDS] rounds of the same work. A round that a garbage collection, the JIT or
 * a page fault slowed is left out. A renderer that really got slower makes every round slower, so it still fails its
 * budget.
 *
 * The budgets are scaled for a slow runner: CI sets LANI_PERF_SCALE=4, and Gradle passes it on as the system property
 * lani.perfScale. A budget still catches a renderer that got several times slower, without failing because the runner
 * is slow.
 */
object FrameBudget {
    val scale: Double = System.getProperty("lani.perfScale")?.toDoubleOrNull()?.takeIf { it >= 1.0 } ?: 1.0

    /** [budget] (ms, or µs: whatever the test measures in) on this machine. */
    fun scaled(budget: Double): Double = budget * scale

    /** How many rounds of the same work [ms] and [best] take the best of. */
    const val ROUNDS = 3

    private val threads = ManagementFactory.getThreadMXBean()

    /** Whether [now] is this thread's CPU time (HotSpot on Linux, macOS and Windows), else the wall clock. */
    val cpuTime: Boolean = threads.isCurrentThreadCpuTimeSupported && threads.isThreadCpuTimeEnabled

    /** This thread's CPU time in ns (the wall clock's System.nanoTime where the JVM can't tell): only differences count. */
    fun now(): Long = if (cpuTime) threads.currentThreadCpuTime else System.nanoTime()

    /** The CPU time this thread spends in [block], in ms. */
    inline fun cpuMs(block: () -> Unit): Double {
        val t0 = now()
        block()
        return (now() - t0) / 1e6
    }

    /** The best (the lowest) of [rounds] measurements by [measure]. */
    inline fun best(rounds: Int = ROUNDS, measure: () -> Double): Double {
        var b = Double.POSITIVE_INFINITY
        repeat(rounds) { b = minOf(b, measure()) }
        return b
    }

    /**
     * The CPU time of one of [runs] runs of [block] (it gets the run's index, 0 until [runs]), in ms: the best of
     * [rounds] rounds of the same runs.
     */
    inline fun ms(runs: Int, rounds: Int = ROUNDS, block: (Int) -> Unit): Double =
        best(rounds) { cpuMs { repeat(runs) { block(it) } } / runs }

    /** [ms] in µs. */
    inline fun us(runs: Int, rounds: Int = ROUNDS, block: (Int) -> Unit): Double = ms(runs, rounds, block) * 1000
}
