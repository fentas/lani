package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.audio.AudioSettings

/**
 * What the app says on a visit when a line can't be heard (companion/README.md, "Voices on a visit"): the town's clips, the
 * node voicing the line in the town's cast voice, the phone's offline voice of the town's language and Android's
 * text-to-speech all failed. Pure: [prompt] decides.
 */
object VisitVoicePolicy {
    enum class Kind {
        /** "🗣️ Prenesi slovenski glas za obiske (61 MB)? · Download the Slovene voice for visits?": the node has it, the phone not. */
        OFFER,
        /** "🔇 Ta naprava nima glasu za slovenščino · No Slovene voice on this device", with "⬇️ Prenesi": the offer was declined before. */
        NOTICE_DOWNLOAD,
        /** The same, nothing to download (no node offers it: the hosted mode, an older node). */
        NOTICE,
    }

    /**
     * On the first line of a visit that nothing could say: what to show, once a visit. [installed]: the town language's offline
     * voice is on the phone (it was tried: nothing to offer); [offered]: the node has it to download; [declined]: the offer
     * for that language was declined on an earlier visit; [downloading]: it is on its way already.
     */
    fun prompt(installed: Boolean, offered: Boolean, declined: Boolean, downloading: Boolean): Kind? = when {
        installed || downloading -> null
        offered && !declined -> Kind.OFFER
        offered -> Kind.NOTICE_DOWNLOAD
        else -> Kind.NOTICE
    }
}

/** What shows: [kind] for the town's [language], the offline voice's size and name when the node offers it. */
data class VisitVoicePrompt(val kind: VisitVoicePolicy.Kind, val language: String, val bytes: Long = 0, val name: String = "")

/**
 * The visit's note when nothing can say a line ([si.lanisce.lani.data.Speaker.onSilent]): once a visit, the offer of the
 * town language's offline voice ([VisitVoicePolicy.OFFER]: "Ne zdaj · Not now" remembered for that language, so the next
 * visits say only that nothing speaks it, with the download), else the note ("🔇 …"). The download goes on in the background
 * (its progress in the note); Piper speaks the town's lines once it is on the phone. Compose state, on the main thread.
 */
class VisitVoiceController(
    private val scope: CoroutineScope,
    private val audio: OfflineAudioController,
    private val settings: AudioSettings,
) {
    /** What shows now; null: nothing. */
    var prompt by mutableStateOf<VisitVoicePrompt?>(null)
        private set

    /** The visited town's language; null at home. */
    private var language: String? = null

    /** This visit said it once already. */
    private var told = false

    /** The languages of words at home nothing could say ([silentIn]), said once each while the app runs. */
    private val toldAt = mutableSetOf<String>()

    /** A visit to a town of [language] begins: nothing said yet. */
    fun start(language: String) {
        this.language = language
        told = false
        prompt = null
    }

    /** Home again. */
    fun end() {
        language = null
        prompt = null
    }

    /** A line of the visit nothing could say: the offer or the note, once a visit. */
    fun silent() {
        val lang = language ?: return
        if (told) return
        told = true
        decide(lang)
    }

    /**
     * A word of [lang] nothing could say (its card at home: a word of a town visited, [si.lanisce.lani.data.Speaker.sayIn]):
     * on a visit to a town of [lang], as [silent]; at home the offer or the note for [lang], once for each language while
     * the app runs.
     */
    fun silentIn(lang: String) {
        if (language != null) {
            if (lang == language) silent()
            return
        }
        if (!toldAt.add(lang)) return
        decide(lang)
    }

    /** The offer or the note for [lang] ([VisitVoicePolicy.prompt]), unless a visit began or ended meanwhile. */
    private fun decide(lang: String) {
        val at = language
        scope.launch {
            val installed = withContext(Dispatchers.IO) { audio.voices.installed(lang) != null }
            val offer = audio.offerFor(lang)
            val kind = VisitVoicePolicy.prompt(installed, offer != null, settings.visitVoiceDeclined(lang), lang in audio.visitProgress)
            // the visit may have begun or ended meanwhile
            if (language == at) prompt = kind?.let { VisitVoicePrompt(it, lang, offer?.bytes ?: 0, offer?.name.orEmpty()) }
            android.util.Log.i(TAG, "a line of the visit couldn't be heard: ${kind ?: "the offline voice of $lang is on the phone, or on its way"}")
        }
    }

    /** "⬇️ Prenesi · Download": the voice comes in the background; the note shows its progress. */
    fun download() {
        val p = prompt ?: return
        audio.downloadFor(p.language)
        prompt = p.copy(kind = VisitVoicePolicy.Kind.NOTICE_DOWNLOAD)
    }

    /** "Ne zdaj · Not now": not offered for this language again (the note comes instead, with the download). */
    fun decline() {
        prompt?.let { settings.declineVisitVoice(it.language) }
        prompt = null
    }

    /** The note's ✕. */
    fun dismiss() {
        prompt = null
    }

    /** QA's "audio:visitvoice:reset" (a debug build): the offers declined forgotten, this visit's note to come again. */
    fun qaReset() {
        settings.forgetVisitVoiceDeclines()
        told = false
        toldAt.clear()
        prompt = null
    }

    /** QA's "audio:visitvoice:again" (a debug build): this visit's note may come again, the offers declined kept (the 🔇 note then). */
    fun qaAgain() {
        told = false
        prompt = null
    }

    private companion object {
        const val TAG = "VisitVoice"
    }
}
