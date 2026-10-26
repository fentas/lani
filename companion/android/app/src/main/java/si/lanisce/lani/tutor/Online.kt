package si.lanisce.lani.tutor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Whether the phone can reach the internet, for the tutors' queues (the live tutor's, the shadow's and the matrix's
 * lanes'). The matrix (2026-10-19 to 10-22): every failed turn was a network failure while the app was in the
 * background ("Unable to resolve host" for api.anthropic.com and auth.openai.com while Android held the app's network),
 * and each burned about 20 retried calls (the loop's 4 attempts a call × the queue's 5 tries). Now a network error while
 * the phone is offline isn't retried: the turn goes back to the queue with its try not counted, and the queue waits for
 * the network ([await]) before it runs the next turn.
 */
interface Online {
    /** Whether a default network is up with internet and isn't blocked for the app (Doze, app standby, data saver). */
    fun now(): Boolean

    /** Suspends until [now] is true (at once when it is). */
    suspend fun await()

    companion object {
        /** Always online: where no network callback is known (the tests that don't play the network, the QA hooks). */
        val ALWAYS: Online = object : Online {
            override fun now() = true
            override suspend fun await() = Unit
        }
    }
}

/**
 * The network as a callback reports it ([AndroidOnline]'s ConnectivityManager callback, a test's switch): [set] on each
 * change, read by [now], waited for by [await].
 */
class OnlineState(initial: Boolean = true) : Online {
    private val state = MutableStateFlow(initial)

    override fun now(): Boolean = state.value

    override suspend fun await() {
        state.first { it }
    }

    fun set(on: Boolean) {
        state.value = on
    }
}
