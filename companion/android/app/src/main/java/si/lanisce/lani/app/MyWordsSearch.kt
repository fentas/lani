package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.lanisce.lani.data.MyWord
import si.lanisce.lani.data.MyWords
import si.lanisce.lani.data.WordEntry
import si.lanisce.lani.data.WordLookup

/** What the dictionary said of the search (GET /lookup), under the learner's own words that match it. */
sealed interface DictionaryLook {
    /** Nothing to look up: no search, too short, or one of the learner's words as it is. */
    data object Idle : DictionaryLook
    data class Looking(val word: String) : DictionaryLook
    /** The dictionary's entries of [word] (its lemmas; [WordEntry.known]: one of the learner's). */
    data class Found(val word: String, val entries: List<WordEntry>) : DictionaryLook
    data class NotFound(val word: String) : DictionaryLook
    /** The node can't be reached now. */
    data class Offline(val word: String) : DictionaryLook
    /** The node is older than lookups (404). */
    data object Unavailable : DictionaryLook
}

/**
 * The search of "📚 Moje besede · My words" (ui/words/MyWordsScreen): what is typed ([query]; the list filters it at
 * once, [MyWords.filter]) and, a moment after the typing stops ([debounceMs]), the dictionary's word for it when it isn't
 * one of the learner's ([MyWords.lookupFor], [look]); a newer search drops the one under way. [lookup]: GET /lookup
 * ([WordsController.lookUp]: null from an older node, an exception when it can't be reached).
 */
class MyWordsSearch(
    private val scope: CoroutineScope,
    private val lookup: suspend (String) -> WordLookup?,
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    var query by mutableStateOf("")
        private set
    var look by mutableStateOf<DictionaryLook>(DictionaryLook.Idle)
        private set
    private var job: Job? = null

    /** [text] typed, with the learner's [words] (what isn't looked up). */
    fun type(text: String, words: List<MyWord>) {
        query = text
        val w = MyWords.lookupFor(text, words)
        if (w == null) {
            job?.cancel()
            look = DictionaryLook.Idle
            return
        }
        // the same word again (a space typed after it): what was found stays
        if (wordOf(look)?.equals(w, ignoreCase = true) == true && look !is DictionaryLook.Offline) return
        job?.cancel()
        look = DictionaryLook.Looking(w)
        job = scope.launch {
            delay(debounceMs)
            val got = try {
                when (val l = lookup(w)) {
                    null -> DictionaryLook.Unavailable
                    else -> if (l.entries.isEmpty()) DictionaryLook.NotFound(w) else DictionaryLook.Found(w, l.entries)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DictionaryLook.Offline(w)
            }
            if (MyWords.lookupFor(query, words)?.equals(w, ignoreCase = true) == true) look = got
        }
    }

    /** The search emptied (its ✕). */
    fun clear() = type("", emptyList())

    private fun wordOf(l: DictionaryLook): String? = when (l) {
        is DictionaryLook.Looking -> l.word
        is DictionaryLook.Found -> l.word
        is DictionaryLook.NotFound -> l.word
        is DictionaryLook.Offline -> l.word
        DictionaryLook.Idle, DictionaryLook.Unavailable -> null
    }

    companion object {
        /** How long the typing rests before the dictionary is asked. */
        const val DEBOUNCE_MS = 350L
    }
}
