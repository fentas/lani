package si.lanisce.lani.data

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateMapOf
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang

/**
 * The phone's speech recognition in the learner's target language ([language]: the pair's target, so sl-SI for Jan and
 * it-IT for a learner of Italian; [Lang.locale]). Each language has its own [Support], remembered across launches:
 * YES once a recognizer is known to take it, either because the service says so (API 33+, or the voice-details
 * broadcast) or because it returned a result in it.
 */
class Recognizer(private val context: Context, private val language: () -> Lang = { L10n.pair.target }) {
    enum class Support { UNKNOWN, YES, NO }

    private val prefs = context.getSharedPreferences("speech", Context.MODE_PRIVATE)

    /** Compose state, per language; the last definite answer for each is remembered across launches. */
    private val supports = mutableStateMapOf<Lang, Support>().apply { Lang.entries.forEach { l -> stored(l)?.let { put(l, it) } } }

    /** The language listened for now. */
    val lang: Lang get() = language()

    /** Its locale, as the recognizer takes it: "it-IT". */
    val tag: String get() = lang.locale

    /** Whether the recognizer takes the target language. */
    val support: Support get() = supports[lang] ?: Support.UNKNOWN

    /** The recognizer is known to take the target language. */
    val ready: Boolean get() = support == Support.YES

    /** In-app recognition ([SpeechRecognizer]); needs RECORD_AUDIO. */
    val inApp: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** The system's voice-input dialog; runs in another app, so it needs no permission of ours. */
    val dialog: Boolean get() = intent().resolveActivity(context.packageManager) != null

    /**
     * [silenceMs]: how long a pause may be before a sentence counts as said (a talk's free speech; recognizers may
     * ignore it, so the talk also listens again after each sentence).
     */
    fun intent(prompt: String? = null, silenceMs: Long? = null): Intent = intentFor(tag)
        .apply { prompt?.let { putExtra(RecognizerIntent.EXTRA_PROMPT, it) } }
        .apply {
            silenceMs?.let {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, it)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, it)
            }
        }

    private fun intentFor(tag: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

    /** Asks the recognition service whether it takes the target language. Call on the main thread. */
    fun refresh() {
        val l = lang
        runCatching {
            when {
                !inApp && !dialog -> settle(l, false)
                Build.VERSION.SDK_INT >= 33 && inApp -> checkService(l)
                else -> checkVoiceDetails(l)
            }
        }
    }

    /** A result in the target language came back: the recognizer works. */
    fun confirm() = settle(lang, true)

    /** The recognizer refused the target language (language not supported). */
    fun reject() = settle(lang, false)

    private fun settle(l: Lang, ok: Boolean) {
        val s = if (ok) Support.YES else Support.NO
        supports[l] = s
        prefs.edit().putString(key(l), s.name).apply()
    }

    private fun stored(l: Lang): Support? =
        (prefs.getString(key(l), null) ?: if (l == Lang.SL) prefs.getString(LEGACY_KEY, null) else null)
            ?.let { runCatching { Support.valueOf(it) }.getOrNull() }

    @RequiresApi(33)
    private fun checkService(l: Lang) {
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = intentFor(l.locale)
        sr.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(s: RecognitionSupport) {
                val ready = s.installedOnDeviceLanguages + s.onlineLanguages
                val downloadable = s.supportedOnDeviceLanguages + s.pendingOnDeviceLanguages
                when {
                    ready.any { speaks(it, l) } -> settle(l, true)
                    downloadable.any { speaks(it, l) } -> runCatching { sr.triggerModelDownload(intent) }
                    // Services that don't list online languages may still take the language online.
                    s.onlineLanguages.isNotEmpty() -> settle(l, false)
                    else -> checkVoiceDetails(l)
                }
                sr.destroy()
            }

            override fun onError(error: Int) {
                sr.destroy()
                checkVoiceDetails(l)
            }
        })
    }

    /** Older API: the recognizer app answers an ordered broadcast with its languages, if it still does. */
    private fun checkVoiceDetails(l: Lang) {
        val details = RecognizerIntent.getVoiceDetailsIntent(context) ?: return
        context.sendOrderedBroadcast(details, null, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val langs = getResultExtras(true).getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES) ?: return
                settle(l, langs.any { speaks(it, l) })
            }
        }, null, Activity.RESULT_OK, null, null)
    }

    /** Where the user can add the language to voice input. */
    fun openSettings() = runCatching {
        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        /** Before languages other than Slovene: the one remembered answer, Slovene's. */
        private const val LEGACY_KEY = "slovene"

        private fun key(l: Lang) = "support_${l.code}"

        /** Whether a recognizer's language tag ("it-IT", "it", "it_IT") is [l]. */
        fun speaks(tag: String, l: Lang): Boolean =
            tag.equals(l.code, true) || tag.startsWith("${l.code}-", true) || tag.startsWith("${l.code}_", true)
    }
}
