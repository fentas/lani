package si.lanisce.lani.ui.talk

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import si.lanisce.lani.data.SttSpeech
import si.lanisce.lani.data.SttWord

/** A word of the draft the node's recognizer was unsure of: [start] until [end] in the text. */
data class Unclear(val id: Int, val start: Int, val end: Int, val word: String)

/** A turn as it goes out: the text, the recognizer's other readings of it, and whether it was typed. */
data class Said(val text: String, val alternatives: List<String>, val typed: Boolean)

/**
 * The input field of a talk (and of the question under its debrief). What the 🎤 recognizes lands here, sentence by
 * sentence, to be checked and corrected before Send; nothing goes out by itself. It keeps where the text came from (a
 * spoken turn stays spoken when Jan fixes a word in it) and the words the recognizer was unsure of ([marks]), which
 * follow their edits.
 */
@Stable
class TalkDraft {
    var value by mutableStateOf(TextFieldValue(""))
        private set
    var marks by mutableStateOf<List<Unclear>>(emptyList())
        private set
    /** Some of the text came from the microphone: the turn counts as spoken. */
    var spoken by mutableStateOf(false)
        private set
    /** The marked word Jan tapped (in the field, or its chip): what to do with it shows. */
    var picked by mutableStateOf<Unclear?>(null)
        private set
    /** The recognizer's other readings, sent while the text is still the one piece they belong to. */
    private var alternatives: List<String> = emptyList()
    private var recognized: String? = null
    private var ids = 0

    val text: String get() = value.text

    /**
     * A recognized [piece] goes at the end, after a space; [unclear] are ranges in the piece to mark,
     * [alternatives] the recognizer's other readings of it.
     */
    fun append(piece: String, unclear: List<IntRange> = emptyList(), alternatives: List<String> = emptyList()) {
        val p = piece.trim()
        if (p.isEmpty()) return
        val was = value.text.takeIf { it.isNotBlank() }.orEmpty()
        val sep = if (was.isEmpty() || was.last().isWhitespace()) "" else " "
        val at = was.length + sep.length
        val text = was + sep + p
        marks = (if (was.isEmpty()) emptyList() else marks) +
            unclear.filter { it.first >= 0 && it.last < p.length }.map { Unclear(ids++, at + it.first, at + it.last + 1, p.substring(it)) }
        this.alternatives = if (was.isEmpty()) alternatives else emptyList()
        recognized = if (was.isEmpty()) p else null
        spoken = true
        picked = null
        value = TextFieldValue(text, TextRange(text.length))
    }

    /** A sentence ends here: a full stop after the text, unless it ends in one. */
    fun punctuate() {
        val t = value.text.trimEnd()
        if (t.isEmpty() || t.last() in ENDS) return
        value = TextFieldValue("$t.", TextRange(t.length + 1))
    }

    /** Jan typed, or moved the cursor: a cursor put in a marked word picks it. */
    fun edit(v: TextFieldValue) {
        if (v.text == value.text) {
            value = v
            picked = marks.firstOrNull { inside(v.selection, it) }
            return
        }
        marks = if (v.text.isBlank()) emptyList() else relocate(value.text, v.text, marks)
        if (v.text.isBlank()) {
            spoken = false
            alternatives = emptyList()
            recognized = null
        }
        picked = null
        value = v
    }

    /** The chip of a marked word was tapped. */
    fun pick(m: Unclear?) {
        picked = m?.let { marks.firstOrNull { x -> x.id == it.id } }
    }

    /** "Type it": the word is selected, so what Jan types replaces it. */
    fun select(m: Unclear) {
        val cur = marks.firstOrNull { it.id == m.id } ?: return
        picked = null
        value = value.copy(selection = TextRange(cur.start, cur.end))
    }

    /** The mark goes; the word stays as it is. */
    fun unmark(m: Unclear) {
        marks = marks.filter { it.id != m.id }
        if (picked?.id == m.id) picked = null
    }

    /** The word of mark [id], said again: [heard] takes its place. False when the mark is gone (Jan edited it). */
    fun replace(id: Int, heard: String): Boolean {
        val m = marks.firstOrNull { it.id == id } ?: return false
        val w = fit(m.word, heard)
        if (w.isEmpty()) return false
        val text = value.text.replaceRange(m.start, m.end, w)
        val delta = w.length - (m.end - m.start)
        marks = marks.filter { it.id != id }.map { if (it.start >= m.end) it.copy(start = it.start + delta, end = it.end + delta) else it }
        spoken = true
        recognized = null
        picked = null
        value = TextFieldValue(text, TextRange(m.start + w.length))
        return true
    }

    /** The turn to send and a fresh field; null when there's nothing to send. */
    fun take(): Said? {
        val t = value.text.trim()
        if (t.isEmpty()) return null
        val said = Said(t, if (t == recognized) alternatives else emptyList(), typed = !spoken)
        clear()
        return said
    }

    fun clear() {
        value = TextFieldValue("")
        marks = emptyList()
        spoken = false
        picked = null
        alternatives = emptyList()
        recognized = null
    }

    companion object {
        private const val ENDS = ".!?…"

        private fun inside(s: TextRange, m: Unclear) =
            if (s.collapsed) s.start in m.start..m.end else s.min == m.start && s.max == m.end

        /**
         * [marks] of [old] in [new]: the text before and after the change keeps them (moved by what was added or
         * taken out); a mark whose word was changed goes.
         */
        fun relocate(old: String, new: String, marks: List<Unclear>): List<Unclear> {
            val max = minOf(old.length, new.length)
            var p = 0
            while (p < max && old[p] == new[p]) p++
            var s = 0
            while (s < max - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
            val oldEnd = old.length - s
            val delta = new.length - old.length
            return marks.mapNotNull { m ->
                when {
                    m.end <= p -> m
                    m.start >= oldEnd -> m.copy(start = m.start + delta, end = m.end + delta)
                    else -> null
                }
            }.filter { m ->
                // Still the whole word: typing right against it changed it.
                new.regionMatches(m.start, m.word, 0, m.word.length) &&
                    new.getOrNull(m.start - 1)?.isLetterOrDigit() != true && new.getOrNull(m.end)?.isLetterOrDigit() != true
            }
        }

        /**
         * A word said again, to stand where [original] was: without the punctuation Whisper puts around a lone word
         * ("Poštar." → "poštar" in the middle of a sentence), a single word cased like the one it replaces.
         */
        fun fit(original: String, heard: String): String {
            val h = heard.trim().trim { !it.isLetterOrDigit() }
            if (h.isEmpty() || h.any { it.isWhitespace() }) return h
            val o = original.firstOrNull() ?: return h
            return when {
                o.isLowerCase() && h[0].isUpperCase() -> h[0].lowercase() + h.substring(1)
                o.isUpperCase() && h[0].isLowerCase() -> h[0].uppercase() + h.substring(1)
                else -> h
            }
        }

        /**
         * The words of a transcribed [text] the recognizer was unsure of (probability under [threshold]), as ranges in
         * it: the [max] least sure, one-letter words left out (Whisper is unsure of "v", "z", "s" however they're said).
         */
        fun unclear(text: String, words: List<SttWord>, max: Int, threshold: Double = SttSpeech.UNCLEAR): List<IntRange> {
            if (max <= 0) return emptyList()
            val found = ArrayList<Pair<IntRange, Double>>()
            var from = 0
            for (w in words) {
                val core = SttSpeech.core(w.word) ?: continue
                val at = text.indexOf(core, from, ignoreCase = true)
                if (at < 0) continue
                from = at + core.length
                val prob = w.prob ?: continue
                if (prob < threshold && core.length >= 2) found += (at until at + core.length) to prob
            }
            return found.sortedBy { it.second }.take(max).map { it.first }.sortedBy { it.first }
        }
    }
}
