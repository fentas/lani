package si.lanisce.lani.ui.scene

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * A run's lines brought out one after another (companion/SCENES.md, "I spy"). A game of «Vidim, vidim» adds several lines at
 * once (the game's line and the first clue; a guess, the child's reaction and the next clue), and each of the child's lines
 * comes out (shown, typed in) and is said as the one before it has been heard to its end, never over it. [out]: how many of
 * the run's lines are out; [talking]: one is being said (the child is drawn talking). The learner's own lines (no speaker)
 * come out in their turn, at once and unsaid. A line added meanwhile waits its turn.
 *
 * [first]: the pause before a line after a quiet spell (the panel, and the picture's reaction to a tap, come first);
 * [gap]: between two lines in a row.
 */
@Stable
class LinesInTurn(private val first: Long = FIRST_MS, private val gap: Long = GAP_MS) {
    var out by mutableIntStateOf(0)
        private set
    var talking by mutableStateOf(false)
        private set

    /**
     * Follows [lines] (the run's lines as they grow) until cancelled: each line out in turn, the child's said by [say],
     * which returns once the line has been heard (Speaker.sayInTurn: cancelled, the game left, it stops the line at once).
     */
    suspend fun follow(lines: Flow<List<Said>>, say: suspend (Said) -> Unit): Unit = coroutineScope {
        val latest = lines.stateIn(this)
        // a line was just said and the next one is already waiting: only the short gap between them
        var row = false
        try {
            while (true) {
                if (latest.value.size <= out) row = false
                val said = latest.first { it.size != out }
                // fewer lines than are out: another run (not one of this game's)
                if (said.size < out) {
                    out = said.size
                    continue
                }
                val line = said[out]
                if (line.who == null) {
                    out++
                    continue
                }
                delay(if (row) gap else first)
                out++
                talking = true
                try {
                    say(line)
                } finally {
                    talking = false
                }
                row = true
            }
        } finally {
            talking = false
        }
    }

    companion object {
        /** Before the first of the child's lines after a quiet spell, as before a dialog's line (DialogMood). */
        const val FIRST_MS = 350L

        /** Between two of the child's lines in a row: a breath. */
        const val GAP_MS = 250L
    }
}
