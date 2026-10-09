package si.lanisce.lani

/**
 * The renderers' time budgets in the unit tests, scaled for the machine: 1 on a desktop, more on a slow shared runner
 * (CI sets LANI_PERF_SCALE=4; Gradle hands it over as the system property lani.perfScale). A budget still catches a
 * renderer that got several times slower, without failing because the runner is.
 */
object FrameBudget {
    val scale: Double = System.getProperty("lani.perfScale")?.toDoubleOrNull()?.takeIf { it >= 1.0 } ?: 1.0

    /** [budget] (ms, or µs: whatever the test measures in) on this machine. */
    fun scaled(budget: Double): Double = budget * scale
}
