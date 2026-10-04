package si.lanisce.lani.road

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The base-language prompts ("good day", "You say: …"), spoken by the phone's own text-to-speech and rendered to files
 * once ([render]), so the car plays them like the clips, offline. The prompts are the learner's own language: any phone
 * voice for it will do. The hint's 🔊 renders its pieces the same way ([si.lanisce.lani.data.HintVoice]).
 */
class RoadPrompts(private val context: Context) : AutoCloseable {
    private var tts: TextToSpeech? = null
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /** Starts the phone's voice in [locale] at [rate] (1: the voice's own pace); false when it can't speak it. */
    suspend fun open(locale: Locale, rate: Float = 1f): Boolean {
        val ready = CompletableDeferred<Boolean>()
        val t = TextToSpeech(context.applicationContext) { ready.complete(it == TextToSpeech.SUCCESS) }
        tts = t
        if (withTimeoutOrNull(15_000) { ready.await() } != true) return false
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { utteranceId?.let { waiting.remove(it)?.complete(true) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { utteranceId?.let { waiting.remove(it)?.complete(false) } }
            override fun onError(utteranceId: String?, errorCode: Int) { utteranceId?.let { waiting.remove(it)?.complete(false) } }
        })
        val r = t.setLanguage(locale)
        if (rate != 1f) t.setSpeechRate(rate)
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /** [text] spoken into [to] (a WAV file); false when the phone's voice couldn't. */
    suspend fun render(text: String, to: File): Boolean {
        val t = tts ?: return false
        to.parentFile?.mkdirs()
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>()
        waiting[id] = done
        val part = File(to.parentFile, to.name + ".part")
        if (t.synthesizeToFile(text, Bundle(), part, id) != TextToSpeech.SUCCESS) {
            waiting.remove(id)
            return false
        }
        val ok = withTimeoutOrNull(30_000) { done.await() } == true
        waiting.remove(id)
        // a WAV header alone (44 bytes) is no prompt
        return ok && part.length() > 100 && (part.renameTo(to) || run { to.delete(); part.renameTo(to) })
    }

    override fun close() {
        tts?.shutdown()
        tts = null
    }
}
