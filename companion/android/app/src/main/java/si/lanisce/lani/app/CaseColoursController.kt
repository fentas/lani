package si.lanisce.lani.app

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.CaseLens
import si.lanisce.lani.data.CasePrefs
import si.lanisce.lani.data.CaseSpan
import si.lanisce.lani.data.VisitWorld
import si.lanisce.lani.l10n.L10n

/**
 * "🎨 Skloni · Cases" (docs/plans/05-built/case-lens.md): the lines of a reading, a story or a dialog with each noun
 * underlined in its case's colour, asked of the bridge (POST /case-lens, its own analysis: in the phone's bridge offline) a
 * few dozen lines at a time and kept; the drawing is the text's (WordText), nothing computed on the main thread. Only
 * Slovene's, at home ([available]); off until the learner turns it on ([CasePrefs]).
 */
class CaseColoursController(
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    val prefs: CasePrefs,
) {
    /** The lines' words, by the line as sent; a line asked for and not answered (offline, an older bridge) has none. */
    private val known = mutableStateMapOf<String, List<CaseSpan>>()
    private val waiting = LinkedHashSet<String>()
    private val asked = HashSet<String>()
    private var job: Job? = null

    /** Whether the lens can colour here: the village's Slovene, not a visit. */
    val available: Boolean get() = L10n.pair.target.code in CaseLens.LANGUAGES && VisitWorld.language == null

    /** Whether the colours show now. */
    val on: Boolean get() = available && prefs.colours

    fun toggle() = prefs.showColours(!prefs.colours)

    /** [line]'s words with a case, once the bridge answered ([request]); null until then, or while the colours are off. */
    fun known(line: String): List<CaseSpan>? = if (on) known[line.take(CaseLens.MAX_CHARS)] else null

    /** Asks the bridge for [line]'s words with a case, gathered with the other lines asked for a moment (once a line). */
    fun request(line: String) {
        if (on) want(line.take(CaseLens.MAX_CHARS))
    }

    private fun want(line: String) {
        if (line.isBlank() || line in asked) return
        asked += line
        waiting += line
        if (job?.isActive == true) return
        // the lines of a screen arrive one composable at a time: gathered a moment, then asked at once
        job = scope.launch {
            delay(GATHER_MS)
            while (waiting.isNotEmpty()) {
                val batch = waiting.take(CaseLens.BATCH)
                waiting.removeAll(batch.toSet())
                val b = bridge()
                val answer = try {
                    b?.cases(batch)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (answer == null) {
                    // offline, or an older bridge: asked again the next time the lines show
                    asked.removeAll(batch.toSet())
                    return@launch
                }
                batch.forEachIndexed { i, l -> known[l] = answer.lines.getOrNull(i)?.words.orEmpty() }
                if (known.size > MAX_KEPT) known.keys.take(known.size - MAX_KEPT).forEach { known.remove(it) }
            }
        }
    }

    private companion object {
        const val GATHER_MS = 60L
        const val MAX_KEPT = 800
    }
}
