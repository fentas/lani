package si.lanisce.lani.l10n

/**
 * A value that reads differently in each language: a name from the culture pack ("Zaselek", "Hamlet"), a date
 * ("11. novembra", "11 November"). Given as a message's argument (to [bi], [L10n.text] or a culture pack's text), it
 * shows in the language of the message it fills, so "Potrebuje {age} · Needs {age}" reads "Potrebuje Zaselek · Needs
 * Hamlet" from one argument.
 */
fun interface Localized {
    fun of(lang: Lang): String

    companion object {
        /** [args] for a message in [lang]: every [Localized] one in that language, the others as they are. */
        fun resolve(args: Map<String, Any?>, lang: Lang): Map<String, Any?> =
            if (args.values.none { it is Localized }) args else args.mapValues { (_, v) -> if (v is Localized) v.of(lang) else v }
    }
}
