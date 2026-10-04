package si.lanisce.lani.game.culture

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.Message

/**
 * A text of a culture pack, in several languages: `{"sl": "Mickina kuhinja", "en": "Micka's kitchen"}`, the pack's own
 * language first, then translations into the base languages it serves. A text may be a message with arguments in ICU
 * syntax (l10n/Message: `{name}`, `{g, select, f {…} other {…}}`, plurals); a [Localized] argument shows in the
 * message's language.
 *
 * A language the text lacks shows in English, then in the pack's language (its first).
 */
@Serializable(with = Text.Serializer::class)
class Text(val by: Map<String, String>) : Localized {
    init {
        require(by.isNotEmpty()) { "a text in no language" }
    }

    /** The language [lang]'s text comes in, and the pattern. */
    fun pattern(lang: Lang): Pair<Lang, String> {
        by[lang.code]?.let { return lang to it }
        by[Lang.EN.code]?.let { return Lang.EN to it }
        val (code, s) = by.entries.first()
        return (Lang.of(code) ?: lang) to s
    }

    fun has(lang: Lang): Boolean = lang.code in by

    /** In [lang], with [args]. */
    fun of(lang: Lang, args: Map<String, Any?>): String {
        val (l, p) = pattern(lang)
        return if ('{' !in p) p else Message.of(p).format(l, Localized.resolve(args, l))
    }

    override fun of(lang: Lang): String = of(lang, emptyMap())

    /** In the learner's target language ("Mickina kuhinja"). */
    val target: String get() = of(L10n.pair.target)

    /** In the learner's base language ("Micka's kitchen"). */
    val base: String get() = of(L10n.pair.base)

    /** "target · base", as the game's texts have always read: "Mickina kuhinja · Micka's kitchen". */
    fun bi(vararg args: Pair<String, Any?>): String = bi(args.toMap())

    fun bi(args: Map<String, Any?>): String = "${of(L10n.pair.target, args)} · ${of(L10n.pair.base, args)}"

    fun inTarget(vararg args: Pair<String, Any?>): String = of(L10n.pair.target, args.toMap())

    fun inBase(vararg args: Pair<String, Any?>): String = of(L10n.pair.base, args.toMap())

    /** This text in every language, [f] applied: "Mlaj" → "mlaj". */
    fun map(f: (String) -> String): Text = Text(by.mapValues { f(it.value) })

    /** This text in every language, [f] applied with its language code ("de" keeps a noun's capital). */
    fun mapIn(f: (lang: String, text: String) -> String): Text = Text(by.mapValues { (lang, text) -> f(lang, text) })

    override fun equals(other: Any?): Boolean = other is Text && other.by == by
    override fun hashCode(): Int = by.hashCode()
    override fun toString(): String = by.entries.joinToString(" | ") { "${it.key}: ${it.value}" }

    companion object {
        /** A text in one language (a name, a word that is the same in every language). */
        fun of(lang: Lang, s: String) = Text(mapOf(lang.code to s))
    }

    object Serializer : KSerializer<Text> {
        private val map = MapSerializer(String.serializer(), String.serializer())
        override val descriptor: SerialDescriptor = map.descriptor
        override fun serialize(encoder: Encoder, value: Text) = map.serialize(encoder, value.by)
        override fun deserialize(decoder: Decoder): Text {
            val m = map.deserialize(decoder)
            if (m.isEmpty()) throw SerializationException("a text needs at least one language: {\"sl\": \"…\", \"en\": \"…\"}")
            return Text(LinkedHashMap(m))
        }
    }
}

/** [f] in each language: a value computed per language, like "pri Furlanovih" in Slovene and "Furlan" in English. */
fun localized(f: (Lang) -> String): Localized = Localized(f)
