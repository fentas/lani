package si.lanisce.lani.l10n

/**
 * The languages the app's labels come in (docs/plans/02-worlds-languages-multiplayer.md, §1). Each has a string
 * table (app/src/main/resources/l10n/<code>.json); a table may still be empty ([DE]).
 */
enum class Lang(
    /** ISO 639-1, as learner-profile.json's *_language_code and the tables' file names have it. */
    val code: String,
    /** The BCP 47 locale the phone's speech recognizer and text-to-speech take for it: "it-IT". */
    val locale: String,
    /** Its name in itself, the same in every pair: "italiano". */
    val ownName: String,
    /** Other names learner-profile.json may give instead of a code: in English, in Slovene, in Italian and in German. */
    private vararg val names: String,
) {
    SL("sl", "sl-SI", "slovenščina", "Slovene", "Slovenian", "slovensko", "sloveno", "Slowenisch"),
    EN("en", "en-GB", "English", "angleščina", "angleško", "inglese", "Englisch"),
    IT("it", "it-IT", "italiano", "Italian", "italijanščina", "italijansko", "Italienisch"),
    DE("de", "de-DE", "Deutsch", "German", "nemščina", "nemško", "tedesco");

    companion object {
        /** "it", "Italian" or "italiano" → [IT]; null when blank or not one of ours. */
        fun of(s: String?): Lang? {
            val t = s?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { l -> l.code == t || l.ownName.lowercase() == t || l.names.any { it.lowercase() == t } }
        }
    }
}

/**
 * The learner's two languages: their town speaks [target] (the first half of every label), and [base] explains (the
 * second half): "Danes · Today". Jan: sl · en. The second learner: it · sl.
 */
data class LangPair(val target: Lang, val base: Lang) {
    /** "it-sl", as the setting keeps it. */
    val code: String get() = "${target.code}-${base.code}"

    override fun toString() = "${target.code} · ${base.code}"

    /** Learning [t] instead; when that's the base, the two swap. */
    fun withTarget(t: Lang) = if (t == base) LangPair(t, target) else LangPair(t, base)

    /** Explained in [b] instead; when that's the target, the two swap. */
    fun withBase(b: Lang) = if (b == target) LangPair(base, b) else LangPair(target, b)

    companion object {
        /** Jan's pair, and every learner's until their profile says otherwise. */
        val DEFAULT = LangPair(Lang.SL, Lang.EN)

        /** "it-sl" → it · sl; null when it isn't two different languages of ours. */
        fun parse(code: String?): LangPair? {
            val parts = code?.split('-')?.takeIf { it.size == 2 } ?: return null
            val t = Lang.of(parts[0]) ?: return null
            val b = Lang.of(parts[1]) ?: return null
            return if (t != b) LangPair(t, b) else null
        }

        /**
         * The pair from learner-profile.json's `learner`: target_language_code, else target_language (a name); the same
         * for the base, from base_language_code or base_language. Not native_language: English stays Jan's base
         * whatever their mother tongue (plan 2, "Jan's decisions"). What's missing or unknown keeps [DEFAULT]'s.
         */
        fun fromProfile(targetCode: String?, target: String?, baseCode: String?, base: String?): LangPair {
            val t = Lang.of(targetCode) ?: Lang.of(target) ?: DEFAULT.target
            val b = Lang.of(baseCode) ?: Lang.of(base) ?: DEFAULT.base
            return when {
                t != b -> LangPair(t, b)
                // A profile that names one language twice: keep the target, explain in the default's other language.
                t == DEFAULT.base -> LangPair(t, DEFAULT.target)
                else -> LangPair(t, DEFAULT.base)
            }
        }
    }
}
