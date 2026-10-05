package si.lanisce.lani.l10n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's labels in the learner's languages (docs/plans/02-worlds-languages-multiplayer.md, §1).
 *
 * A label is a key in the string tables, one per language (app/src/main/resources/l10n/<code>.json: key → an ICU
 * message, see [Message]). [bi] shows it as "target · base", like the "Slovene · English" literals it replaced
 * ("Danes · Today"); [inTarget] and [inBase] show one language, where the app showed one. The pair is the
 * learner's ([pair]): Jan's is sl · en, so Jan's screens read as they always did.
 *
 * A key missing from a table comes in English, then in Slovene, and is reported once ([onMissing]: logged in debug
 * builds); missing everywhere, the key itself shows.
 */
object L10n {
    /** The learner's own pair: the learner profile's, or the one chosen on the phone (LangSetting). */
    @Volatile
    private var own: LangPair = LangPair.DEFAULT

    /**
     * While visiting another town (plan 2, §3.2; [si.lanisce.lani.data.VisitWorld]): the host town's language, explained
     * in the learner's base. It is what every label shows until the visit ends, whatever sets the learner's own pair
     * meanwhile (the widget, the background check); null at home.
     */
    @Volatile
    var visit: LangPair? = null

    /** The pair every label is shown in: a visit's, else the learner's own. Setting it sets the own pair. */
    var pair: LangPair
        get() = visit ?: own
        set(value) {
            own = value
        }

    /** The learner's own pair, also during a visit. */
    val ownPair: LangPair get() = own

    /** Where a table misses a key, in this order. */
    val FALLBACK = listOf(Lang.EN, Lang.SL)

    /** Told once per language and key missing from its table; the app logs it in debug builds. */
    @Volatile
    var onMissing: ((Lang, String) -> Unit)? = null

    private val reported = ConcurrentHashMap.newKeySet<String>()
    private val tables = ConcurrentHashMap<Lang, Map<String, String>>()

    /** [lang]'s table: its keys and messages, without the notes (keys starting with "_"). Empty when it has none. */
    fun table(lang: Lang): Map<String, String> = tables.getOrPut(lang) {
        L10n::class.java.getResourceAsStream("/l10n/${lang.code}.json")?.use { parseTable(it.readBytes().decodeToString()) }.orEmpty()
    }

    fun parseTable(json: String): Map<String, String> =
        Json.parseToJsonElement(json).jsonObject.entries
            .filter { (k, v) -> !k.startsWith("_") && v is JsonPrimitive && v.isString }
            .associate { (k, v) -> k to (v as JsonPrimitive).content }

    /** [key]'s message and the language it's in: [lang]'s, else the first [FALLBACK] that has it. */
    fun lookup(lang: Lang, key: String): Pair<Lang, String>? {
        table(lang)[key]?.let { return lang to it }
        missing(lang, key)
        return FALLBACK.firstNotNullOfOrNull { l -> table(l)[key]?.let { l to it } }
    }

    /**
     * The argument every message may read without the code passing it: the code of the language learned ([target]),
     * so a text can name it, "{targetLang, select, it {Say it in Italian} other {Say it in Slovene}}".
     */
    const val TARGET_LANG = "targetLang"

    /**
     * The other argument every message may read: the learner's gender ([Learner.current]), "f" or "m", so a label
     * agrees with them: "{learnerGender, select, f {Pripravljena} other {Pripravljen}} si!" (companion/SCENES.md, "The
     * learner in the content": the content's own texts say it {m:…|f:…}; a table is ICU, and its {learner} is an argument).
     */
    const val LEARNER_GENDER = "learnerGender"

    /**
     * [key] in [lang] with [args]; formatted by the plural rules of the language it came in. An argument that is
     * [Localized] (an age's name, a date) shows in that language too. [TARGET_LANG] is [target]'s code, [LEARNER_GENDER]
     * the learner's.
     */
    fun text(lang: Lang, key: String, args: Map<String, Any?> = emptyMap(), target: Lang = pair.target): String {
        val (l, pattern) = lookup(lang, key) ?: return key
        val withTarget = if (TARGET_LANG in args) args else args + (TARGET_LANG to target.code)
        val all = if (LEARNER_GENDER in withTarget) withTarget else withTarget + (LEARNER_GENDER to (if (Learner.current.female) "f" else "m"))
        return Message.of(pattern).format(l, Localized.resolve(all, l))
    }

    /** "target · base": [key] in both of [pair]'s languages, as the app's labels have always read. */
    fun label(pair: LangPair, key: String, args: Map<String, Any?> = emptyMap()): String =
        "${text(pair.target, key, args, pair.target)} · ${text(pair.base, key, args, pair.target)}"

    private fun missing(lang: Lang, key: String) {
        if (reported.add("${lang.code}:$key")) onMissing?.invoke(lang, key)
    }

    /** Tests: [lang]'s table replaced by [entries], or read from its file again (null). */
    internal fun useTable(lang: Lang, entries: Map<String, String>?) {
        if (entries == null) tables.remove(lang) else tables[lang] = entries
        reported.removeAll { it.startsWith("${lang.code}:") }
    }
}

/** "Danes · Today": [key] in the learner's target language, then their base. */
fun bi(key: String, vararg args: Pair<String, Any?>): String = L10n.label(L10n.pair, key, args.toMap())

/** [key] in the learner's target language alone ("Moja vas"), where the app shows one language. */
fun inTarget(key: String, vararg args: Pair<String, Any?>): String = L10n.text(L10n.pair.target, key, args.toMap())

/** [key] in the learner's base language alone. */
fun inBase(key: String, vararg args: Pair<String, Any?>): String = L10n.text(L10n.pair.base, key, args.toMap())
