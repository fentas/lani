package si.lanisce.lani.l10n

import com.ibm.icu.text.PluralRules
import com.ibm.icu.util.ULocale
import java.util.concurrent.ConcurrentHashMap

/**
 * ICU's plural rules on a plain JVM: ICU4J, which android.icu repackages. Registered as a service
 * (src/test/resources/META-INF/services), so every test formats plurals as the phone does.
 */
class Icu4jPlurals : PluralCategories {
    private val byLang = ConcurrentHashMap<Lang, PluralRules>()

    override fun category(lang: Lang, n: Double): String =
        byLang.getOrPut(lang) { PluralRules.forLocale(ULocale.forLanguageTag(lang.code)) }.select(n)
}
