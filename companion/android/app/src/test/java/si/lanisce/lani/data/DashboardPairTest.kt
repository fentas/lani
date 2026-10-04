package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/** The learner's languages come from learner-profile.json (the bridge's /state), sl · en when it doesn't say. */
class DashboardPairTest {
    private fun pair(learner: String) = Dashboard.parse(
        """{"databases": {"learner_profile": {"learner": {"name": "X", $learner "current_level": "A1"}}}}""",
    ).pair

    @Test fun `Jan's profile is Slovene, and English explains whatever their native language is`() {
        assertEquals(LangPair.DEFAULT, pair(""))
        assertEquals(LangPair.DEFAULT, pair(""""target_language": "Slovene", "native_language": "German","""))
        assertEquals(LangPair.DEFAULT, pair(""""target_language": "Slovenian", "native_language": "English","""))
    }

    @Test fun `the second learner's profile, as lani-profile writes it`() {
        val p = pair(
            """"native_language": "Slovene", "base_language": "Slovene", "base_language_code": "sl",
               "target_language": "Italian", "target_language_code": "it",""",
        )
        assertEquals(LangPair(Lang.IT, Lang.SL), p)
    }

    @Test fun `a template's placeholders keep the default`() {
        assertEquals(LangPair.DEFAULT, pair(""""target_language": "{LANGUAGE_YOU_WANT_TO_LEARN}", "native_language": "{YOUR_NATIVE_LANGUAGE}","""))
    }
}
