package si.lanisce.lani.game

import si.lanisce.lani.data.ReviewCard
import java.time.LocalDate
import java.time.ZoneOffset

internal object Fixtures {
    val cards = listOf(
        ReviewCard("vocab_dober_dan", "dober dan", "good day"),
        ReviewCard("vocab_zivjo", "živjo", "hi"),
        ReviewCard("vocab_dobro_jutro", "dobro jutro", "good morning", repetitions = 3, lastQuality = 5),
        ReviewCard("vocab_nasvidenje", "nasvidenje", "goodbye"),
        ReviewCard("vocab_hvala", "hvala (lepa)", "thank you (very much)", repetitions = 2, lastQuality = 4),
        ReviewCard("vocab_prosim", "prosim", "please"),
        ReviewCard("vocab_oprostite", "oprostite", "excuse me"),
        ReviewCard("vocab_opravicujem_se", "opravičujem se", "I apologize", repetitions = 1, lastQuality = 2),
        ReviewCard("vocab_ni_za_kaj", "ni za kaj", "you're welcome"),
        ReviewCard("vocab_kako_ste", "Kako ste? / Kako si?", "How are you?"),
        ReviewCard("vocab_jaz_sem", "Jaz sem Jan. / Sem Jan.", "I am Jan.", repetitions = 4, lastQuality = 5),
        ReviewCard("vocab_od_kod_ste", "Od kod ste?", "Where are you from?"),
        ReviewCard("vocab_nemcija", "Nemčija → iz Nemčije", "Germany → from Germany"),
        ReviewCard("vocab_moj_moja", "moj / moja / moje", "my (m/f/n)", kind = "grammar_rule"),
        ReviewCard("vocab_ampak", "ampak", "but"),
    )

    fun pool(canSpeak: Boolean = true, modules: List<Pair<String, si.lanisce.lani.data.Exercise>> = emptyList()) =
        ContentPool(cards, modules, canSpeak)

    val day0: LocalDate = LocalDate.of(2026, 9, 1)

    fun noon(d: LocalDate): Long = d.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
}
