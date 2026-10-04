package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import si.lanisce.lani.l10n.bi
import java.io.File
import java.io.IOException

// A long press on 🔊 re-records the clip on the node (POST /voice/redo, companion/bridge/src/voice.ts VoiceStore.redo):
// the phone keeps the clips it has and never asks for them again, so this is how Jan gets a new take of a word they hear
// wrong. See companion/README.md, "Voice".

/** What the node said to a re-record of one phrase. */
sealed interface RedoAnswer {
    /**
     * The clip at [url] in [voice] for the phrase [key] (normalized): a new take, or ([again]) the one made on request
     * earlier today (the node re-records a clip once a day).
     */
    data class Made(val key: String, val voice: String, val url: String, val again: Boolean = false) : RedoAnswer

    /** Not now: [reason] "cap" (the day's re-records are spent) or "reserve" (the characters left are kept for talks). */
    data class Limit(val reason: String) : RedoAnswer

    /** The node can't now (ElevenLabs down or not set up, an error), or it is older and can't re-record. */
    data object Failed : RedoAnswer

    companion object {
        /**
         * The node's answer ([raw] JSON, whatever the status code: 200 done or not_needed, 429 limit, 503 failed) to a
         * re-record of [phrase] in [voice]; an older node's 404 is [Failed].
         */
        fun parse(raw: String, voice: String, phrase: String): RedoAnswer {
            val o = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return Failed
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull
            return when (val status = s("status")) {
                "done", "not_needed" -> Made(s("norm") ?: Voice.normalize(phrase), s("voice") ?: voice, s("url") ?: return Failed, again = status == "not_needed")
                "limit" -> Limit(s("reason") ?: "cap")
                else -> Failed
            }
        }
    }
}

/** What a long press on 🔊 came to; the button says so ([note]). */
sealed interface ReRecord {
    /** The new clips, on the phone, to play. [again]: re-recorded earlier today already, and these are that take. */
    data class Done(val files: List<File>, val again: Boolean = false) : ReRecord

    /** The node said not now ([RedoAnswer.Limit.reason]). */
    data class Limit(val reason: String) : ReRecord

    /** The node can't be reached (or the phone isn't paired, or it speaks another town's language on a visit): nothing is queued. */
    data object Offline : ReRecord

    /** The node couldn't make it now. */
    data object Failed : ReRecord

    companion object {
        /** After this much use the tip about the long press shows, once. */
        const val TIP_AFTER_MS = 7 * 86_400_000L

        /** What the button says while the node records it again. */
        fun working(): String = "🔄 ${bi("reRecord.working")}"

        /** What the button says about [r], bilingual like the rest of the app. */
        fun note(r: ReRecord): String = when (r) {
            is Done -> if (r.again) "✓ ${bi("reRecord.already")}" else "✓ ${bi("reRecord.done")}"
            is Limit -> if (r.reason == "reserve") "⏳ ${bi("reRecord.reserve")}" else "⏳ ${bi("reRecord.limit")}"
            Offline -> "📡 ${bi("reRecord.offline")}"
            Failed -> "⚠️ ${bi("reRecord.failed")}"
        }

        /** The tip about the long press, shown once. */
        fun tip(): String = "💡 ${bi("reRecord.tip")}"

        /** The tip is due: a week after [firstUsed], unless it was shown or Jan found the long press themselves ([seen]). */
        fun tipDue(firstUsed: Long, now: Long, seen: Boolean): Boolean = !seen && firstUsed > 0 && now - firstUsed >= TIP_AFTER_MS
    }
}

/** The phone's side of the clips a re-record changes: the index and the cached files ([Clips] has one). */
interface ClipCache {
    val index: ClipIndex

    /** Puts [url] in the index for the phrase [key] (normalized) in [voice]. */
    fun put(key: String, voice: String, url: String)

    /** Deletes the clip at [url] from the phone. */
    suspend fun drop(url: String)

    /** The clip at [url] on the phone, downloaded when it isn't there; an [IOException] when it can't be. */
    suspend fun fetch(url: String): File
}

/**
 * A long press on 🔊: the node records the text again ([ask]: POST /voice/redo, an [IOException] when the node can't be
 * reached) in the voice the phone plays it in; [cache] then drops the old clips, takes the new ones into the index and
 * downloads them, so the next tap plays the new take too.
 */
class ReRecorder(private val cache: ClipCache, private val ask: suspend (phrase: String, voice: String) -> RedoAnswer) {
    suspend fun run(text: String, voices: List<String>): ReRecord {
        val (voice, phrases) = plan(cache.index, text, voices) ?: return ReRecord.Failed
        val made = mutableListOf<RedoAnswer.Made>()
        var stop: ReRecord? = null
        for (p in phrases) {
            val a = try {
                ask(p, voice)
            } catch (_: IOException) {
                stop = ReRecord.Offline
                break
            }
            when (a) {
                is RedoAnswer.Made -> made += a
                is RedoAnswer.Limit -> stop = ReRecord.Limit(a.reason)
                RedoAnswer.Failed -> stop = ReRecord.Failed
            }
            if (stop != null) break
        }
        // what was made replaces the old clips on the phone, also when a later phrase couldn't be
        for (m in made) {
            val old = cache.index[m.key]?.get(m.voice)
            cache.put(m.key, m.voice, m.url)
            if (old != null && old != m.url) cache.drop(old)
        }
        stop?.let { return it }
        val files = try {
            made.map { cache.fetch(it.url) }
        } catch (_: IOException) {
            return ReRecord.Offline
        }
        return ReRecord.Done(files, again = made.all { it.again })
    }

    companion object {
        /**
         * The voice to record [text] in, and its phrases: the first of [voices] the phone has a clip of it in (what 🔊
         * plays), else the first; the whole text when the index has it whole, else each phrase of "a / b". Null
         * without voices.
         */
        fun plan(index: ClipIndex, text: String, voices: List<String>): Pair<String, List<String>>? {
            val voice = Clips.resolveFirst(index, text, voices)?.first ?: voices.firstOrNull() ?: return null
            val whole = index[Voice.normalize(text)]?.get(voice) != null
            return voice to if (whole) listOf(text) else Clips.parts(text)
        }
    }
}
