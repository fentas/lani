package si.lanisce.lani.data

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Milliseconds the app has been on screen: what a session's duration_minutes counts. The wall clock
 * counted a word pack left open while the phone slept as 240 minutes (the cap); this clock stops while
 * the app is in the background or the screen is off.
 */
class ScreenClock(private val ticks: () -> Long) : DefaultLifecycleObserver {
    private var total = 0L
    private var since: Long? = null

    /** Once, from MainActivity: follows the whole app coming to the screen and leaving it. */
    fun install() = ProcessLifecycleOwner.get().lifecycle.addObserver(this)

    @Synchronized
    fun shown() {
        if (since == null) since = ticks()
    }

    @Synchronized
    fun hidden() {
        since?.let { total += ticks() - it }
        since = null
    }

    @Synchronized
    fun now(): Long = total + (since?.let { ticks() - it } ?: 0L)

    override fun onStart(owner: LifecycleOwner) = shown()

    override fun onStop(owner: LifecycleOwner) = hidden()

    companion object {
        val app = ScreenClock { SystemClock.elapsedRealtime() }
    }
}
