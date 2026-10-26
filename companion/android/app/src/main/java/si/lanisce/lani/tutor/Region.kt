package si.lanisce.lani.tutor

import java.util.Locale

/**
 * Where the phone is, for what a provider allows there. Google's Gemini API Additional Terms of Service
 * (ai.google.dev/gemini-api/terms, effective 2026-03-23, read 2026-10-19): "You may use only Paid Services when making
 * API Clients available to users in the European Economic Area, Switzerland, or the United Kingdom". So there the Tutor
 * section offers no free tier ([geminiPaidOnly]), and a key the learner marked free before is treated as paid (priced,
 * capped, Argon offered, not paced), its privacy note kept ([GeminiTerms]).
 */
object Region {
    /** The European Economic Area: the EU's 27 and Iceland, Liechtenstein, Norway (ISO 3166-1 alpha-2; Greece is GR). */
    val EEA = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
        "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO",
    )

    /** Where Google allows only the Gemini API's paid services for an app's users: the EEA, Switzerland, the UK ("UK" too, as some report it). */
    val GEMINI_PAID_ONLY = EEA + setOf("CH", "GB", "UK")

    /**
     * The phone's country: its mobile network's (where it is now), else its SIM's, else the locale's region; upper case,
     * or null when none says (a tablet without a SIM, a locale without a region).
     */
    fun country(network: String?, sim: String?, locale: String? = Locale.getDefault().country): String? =
        listOf(network, sim, locale).firstNotNullOfOrNull { c -> c?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 && it.all(Char::isLetter) } }

    /** Whether the Gemini API's free tier may not be offered in [country] ([GEMINI_PAID_ONLY]); false when not known. */
    fun geminiPaidOnly(country: String?): Boolean = country?.uppercase(Locale.ROOT) in GEMINI_PAID_ONLY
}
