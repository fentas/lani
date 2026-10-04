package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import si.lanisce.lani.l10n.bi
import java.text.Normalizer

/**
 * A word of a line as the tutor reads it along when asked about the line: the [form] as written, its dictionary form and
 * reading ([lemma], [reading]), why that form as the app explains it ([why], in the learner's base) and its [page].
 */
data class SentenceWord(val form: String, val lemma: String?, val reading: String?, val why: String?, val page: String?)

/**
 * The pure parts of "🔍 Slovnica stavka · The sentence's grammar" (companion/SCENES.md, "The sentence's grammar"): the
 * tutor's explanation kept for a line (GET /sentence, the bridge's publish_sentence_note) and asking the tutor about one
 * (a chat with `about_sentence`).
 */
object Sentences {
    /** The longest line the bridge keeps an explanation of. */
    const val MAX = 400

    /** `/sentence?s=<the line>&language=<a visit's>&base=<the pair's base>`. */
    fun url(base: String, sentence: String, language: String? = null, meaningIn: String? = null): HttpUrl =
        (base.trimEnd('/') + "/sentence").toHttpUrl().newBuilder()
            .addQueryParameter("s", sentence.trim().take(MAX))
            .apply {
                language?.let { addQueryParameter("language", it) }
                meaningIn?.takeIf { it != language }?.let { addQueryParameter("base", it) }
            }
            .build()

    /** GET /sentence: the explanation's text, null when the tutor has written none. */
    fun note(raw: String): String? {
        val note = (json.parseToJsonElement(raw) as? JsonObject)?.get("note") as? JsonObject ?: return null
        return (note["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    }

    /** A line as the bridge keeps it (sentences.ts `sentenceKey`): case and punctuation aside. */
    fun key(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC).lowercase().replace(Regex("""[^\p{L}\p{M}\p{N}]+"""), " ").trim()

    /** Whether [a] and [b] are the same line for the bridge's notes. */
    fun same(a: String, b: String): Boolean = key(a) == key(b)

    /** "🔍 Razloži mi slovnico tega stavka. · Explain this sentence's grammar to me." and the line below. */
    fun askText(sentence: String): String = "🔍 ${bi("sentence.askText")}\n«${sentence.trim()}»"

    /**
     * The chat message's data when the tutor is asked about a line: `{"about_sentence": {sentence, en, language, scene,
     * scene_title, dialog, person, pages, words}}`: where it was said, the rule of the learner's own line ([pages]: its
     * turn's), and its words as the app read them. The bridge keeps the answer (publish_sentence_note).
     */
    fun askData(
        sentence: String, en: String, language: String, scene: String?, sceneTitle: String?, dialog: String?, person: String?,
        pages: List<String>, words: List<SentenceWord>,
    ): JsonObject = buildJsonObject {
        put("about_sentence", buildJsonObject {
            put("sentence", sentence.trim().take(MAX))
            en.takeIf { it.isNotBlank() }?.let { put("en", it) }
            put("language", language)
            scene?.let { put("scene", it) }
            sceneTitle?.let { put("scene_title", it) }
            dialog?.let { put("dialog", it) }
            person?.let { put("person", it) }
            if (pages.isNotEmpty()) put("pages", buildJsonArray { pages.forEach { add(JsonPrimitive(it)) } })
            put("words", buildJsonArray {
                for (w in words) add(buildJsonObject {
                    put("form", w.form)
                    w.lemma?.let { put("lemma", it) }
                    w.reading?.let { put("reading", it) }
                    w.why?.let { put("why", it) }
                    w.page?.let { put("page", it) }
                })
            })
        })
    }
}
