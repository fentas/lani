package si.lanisce.lani.l10n

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Days of the year in each language: java.time's month names by locale ("11 November", "11 novembre", "11. November"),
 * except in Slovene, where a date after a number takes the month in the genitive ("11. novembra") and java.time has the
 * nominative ("11. november"): Slovene keeps its own formatter, so Jan's texts read exactly as they did.
 */
object Dates {
    /** A day and month in [lang]'s own order and punctuation; "d MMMM" where not known. */
    private val PATTERNS = mapOf(Lang.EN to "d MMMM", Lang.IT to "d MMMM", Lang.DE to "d. MMMM")

    /** Slovene months in the genitive, as a date has them: "11. novembra". */
    private val SL_MONTHS = listOf("januarja", "februarja", "marca", "aprila", "maja", "junija", "julija", "avgusta", "septembra", "oktobra", "novembra", "decembra")

    private val formatters = ConcurrentHashMap<Lang, DateTimeFormatter>()

    /** "11. novembra", "11 November", "11 novembre", "11. November". */
    fun dayMonth(d: LocalDate, lang: Lang): String = when (lang) {
        Lang.SL -> "${d.dayOfMonth}. ${SL_MONTHS[d.monthValue - 1]}"
        else -> formatters.getOrPut(lang) { DateTimeFormatter.ofPattern(PATTERNS[lang] ?: "d MMMM", Locale.forLanguageTag(lang.code)) }.format(d)
    }

    /** The day and month in few characters where a language writes them so ("8. 3." in Slovene), else [dayMonth]. */
    fun short(d: LocalDate, lang: Lang): String = when (lang) {
        Lang.SL -> "${d.dayOfMonth}. ${d.monthValue}."
        else -> dayMonth(d, lang)
    }

    /** A day with its year: "9. novembra 2026", "9 November 2026", "9 novembre 2026", "9. November 2026". */
    fun long(d: LocalDate, lang: Lang): String = "${dayMonth(d, lang)} ${d.year}"

    /** Months abbreviated as each language writes them in a range of days: "sept.", "Sept", "Sept.", "set". */
    private val SHORT_MONTHS = mapOf(
        Lang.SL to listOf("jan.", "feb.", "mar.", "apr.", "maj", "jun.", "jul.", "avg.", "sept.", "okt.", "nov.", "dec."),
        Lang.EN to listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec"),
        Lang.DE to listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sept.", "Okt.", "Nov.", "Dez."),
        Lang.IT to listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic"),
    )

    /**
     * The days [from] to [to] (a week of the chat's archive), short: "22.–28. sept.", "22–28 Sept", "22.–28. Sept.",
     * "22–28 set"; across months "28. sept.–4. okt."; with [year], the year after the last day ("22.–28. sept. 2025"),
     * or after both when they differ ("29. dec. 2025–4. jan. 2026").
     */
    fun range(from: LocalDate, to: LocalDate, lang: Lang, year: Boolean = false): String {
        val months = SHORT_MONTHS[lang] ?: SHORT_MONTHS.getValue(Lang.EN)
        val dot = if (lang == Lang.SL || lang == Lang.DE) "." else ""
        fun day(d: LocalDate) = "${d.dayOfMonth}$dot"
        fun dayMonth(d: LocalDate) = "${day(d)} ${months[d.monthValue - 1]}"
        fun y(d: LocalDate) = if (year) " ${d.year}" else ""
        return when {
            from.year != to.year -> "${dayMonth(from)}${y(from)}–${dayMonth(to)}${y(to)}"
            from.month != to.month -> "${dayMonth(from)}–${dayMonth(to)}${y(to)}"
            else -> "${day(from)}–${dayMonth(to)}${y(to)}"
        }
    }

    /** [range] as a message argument. */
    fun range(from: LocalDate, to: LocalDate, year: Boolean = false): Localized = Localized { range(from, to, it, year) }

    /** [long] as a message argument. */
    fun long(d: LocalDate): Localized = Localized { long(d, it) }

    /** [dayMonth] as a message argument: it reads in each message's language. */
    fun dayMonth(d: LocalDate): Localized = Localized { dayMonth(d, it) }

    /** [short] as a message argument. */
    fun short(d: LocalDate): Localized = Localized { short(d, it) }
}
