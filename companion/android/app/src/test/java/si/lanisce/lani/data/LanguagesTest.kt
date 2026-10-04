package si.lanisce.lani.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDate

/**
 * A language profile per language (docs/DB_SCRIPTS.md, "Languages"): GET /state is the home language's databases and
 * sums up every language; GET /state?language=it is Italian's own, a deck of its own. The home language's stats, words
 * learned, rusty words and the village's ages never count another language's cards.
 */
class LanguagesTest {
    /** Jan's home state (Slovene) after a visit in Italian: the summaries name both, the databases are Slovene's. */
    private val home = """
        {"databases": {
          "learner_profile": {"learner": {"name": "Jan", "target_language": "Slovene", "current_level": "A2", "target_level": "B2"},
                              "home_language": "sl", "languages": [{"code": "it", "name": "Italian", "level": "A1", "source": "visits", "since": "2026-09-20"}]},
          "spaced_repetition": {"items": {
            "vocab_hvala": {"type": "vocabulary", "content": "hvala", "answer": "thanks", "repetitions": 2, "created_date": "2026-09-01", "due_date": "2026-09-26"},
            "vocab_kruh": {"type": "vocabulary", "content": "kruh", "answer": "bread", "repetitions": 0, "total_reviews": 2, "last_quality": 1, "due_date": "2026-09-27"}}}},
         "computed": {"today": "2026-09-26", "due_review_items": ["vocab_hvala"]},
         "language": "sl",
         "languages": [
           {"code": "sl", "name": "Slovene", "level": "A2", "words": 1, "due": 1, "source": "home"},
           {"code": "it", "name": "Italian", "level": "A1", "words": 85, "due": 3, "source": "visits", "since": "2026-09-20"}]}
    """

    /** GET /state?language=it: Italian's own databases, in the same shape; the learner is Jan, the language Italian. */
    private val italian = """
        {"databases": {
          "learner_profile": {"learner": {"name": "Jan", "base_language": "English", "target_language": "Italian", "target_language_code": "it", "current_level": "A1", "target_level": "A2"},
                              "language": "it", "source": "visits", "current_streak_days": 2},
          "spaced_repetition": {"items": {
            "vocab_word_ciao": {"type": "vocabulary", "content": "ciao", "answer": "hi", "repetitions": 1},
            "vocab_word_grazie": {"type": "vocabulary", "content": "grazie", "answer": "thank you", "repetitions": 0, "total_reviews": 1, "last_quality": 2},
            "articolo_lo": {"type": "error_pattern", "content": "il zaino", "answer": "lo zaino"}}}},
         "computed": {"today": "2026-09-26", "due_review_items": ["vocab_word_ciao", "vocab_word_grazie", "articolo_lo"], "next_session_id": "session-it-003"},
         "language": "it",
         "languages": [
           {"code": "sl", "name": "Slovene", "level": "A2", "words": 1, "due": 1, "source": "home"},
           {"code": "it", "name": "Italian", "level": "A1", "words": 85, "due": 3, "source": "visits"}]}
    """

    @Test fun `the dashboard parses every language, the home one first`() {
        val d = Dashboard.parse(home)
        assertEquals("sl", d.language)
        assertEquals(listOf("sl", "it"), d.languages.map { it.code })
        assertTrue(d.languages[0].home)
        assertEquals(LanguageSummary("it", "Italian", "A1", 85, 3, "visits"), d.otherLanguages.single())
    }

    @Test fun `the level in a language is the home one's, another's own, A1 for one never practised`() {
        val d = Dashboard.parse(home)
        assertEquals("A2", d.levelIn("sl"))
        assertEquals("A1", d.levelIn("it"))
        assertEquals("A1", d.levelIn("de"))
        // an older bridge says no language: the home language is the target's
        assertEquals("A2", d.copy(language = null).levelIn(d.pair.target.code))
    }

    @Test fun `the home language's words, rusty words and due cards are its own`() {
        val d = Dashboard.parse(home)
        assertEquals(1, d.wordsLearned) // not Italian's 85
        assertEquals(listOf("vocab_kruh"), d.rusty.map { it.id })
        assertEquals(listOf("vocab_hvala"), d.dueCards.map { it.id })
        assertEquals(LangPair.DEFAULT, d.pair)
    }

    @Test fun `another language's deck is its own dashboard`() {
        val d = Dashboard.parse(italian)
        assertEquals("it", d.language)
        assertEquals(listOf("vocab_word_ciao", "vocab_word_grazie"), d.dueCards.map { it.id }) // error patterns aren't cards
        assertEquals(setOf("ciao", "grazie"), d.pool.map { it.front }.toSet()) // its own distractors, no Slovene card
        assertEquals(LangPair(Lang.IT, Lang.EN), d.pair)
        assertEquals(1, d.wordsLearned)
    }

    @Test fun `an older bridge sends no languages`() {
        val d = Dashboard.parse("""{"databases": {}, "computed": {"today": "2026-09-26"}}""")
        assertNull(d.language)
        assertTrue(d.languages.isEmpty())
        assertTrue(d.otherLanguages.isEmpty())
        assertTrue(Stats.parse("""{"databases": {}}""", LocalDate.parse("2026-09-26")).otherLanguages.isEmpty())
    }

    @Test fun `summaries forgive odd values`() {
        val all = LanguageSummary.parseAll(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"languages": [{"code": null, "source": "home"}, {"code": "it", "words": 2.0, "due": "x"}, 7]}""",
            ).jsonObject,
        )
        assertEquals(listOf(LanguageSummary("", "", "A1", 0, 0, "home"), LanguageSummary("it", "", "A1", 2, 0, "visits")), all)
        // a home language the profile doesn't name is still the home one, never another deck
        assertTrue(Dashboard.parse("""{"databases": {}, "languages": [{"code": null, "source": "home"}]}""").otherLanguages.isEmpty())
    }

    @Test fun `the progress stats are the home language's, with the others beside them`() {
        val p = Stats.parse(home, LocalDate.parse("2026-01-01"))
        assertEquals(1, p.wordsKnown)
        assertEquals("A2", p.level)
        assertEquals(listOf("it"), p.otherLanguages.map { it.code })
        assertEquals(85, p.otherLanguages.single().words)
        assertEquals(2, p.languages.size)
    }

    @Test fun `a language's name as a label`() {
        val was = L10n.pair
        L10n.pair = LangPair.DEFAULT
        try {
            assertEquals("Italijanščina · Italian", LanguageSummary("it", "Italian", "A1", 85, 3, "visits").label())
            assertEquals("French", LanguageSummary("fr", "French", "A1", 0, 0, "visits").label())
            assertEquals("Italijanščina", LanguageSummary("it", "Italian", "A1", 0, 0, "visits").nameIn { L10n.text(Lang.SL, it) })
            assertEquals("85 besed · 85 words", si.lanisce.lani.l10n.bi("progressScreen.languageWords", "n" to 85))
            assertEquals("2 besedi · 2 words", si.lanisce.lani.l10n.bi("progressScreen.languageWords", "n" to 2))
        } finally {
            L10n.pair = was
        }
    }

    @Test fun `a review of another language's deck says which`() {
        val it = Writes.reviews(listOf("vocab_word_ciao" to 4), 2, "it", id = "r1", now = 1)
        val body = kotlinx.serialization.json.Json.parseToJsonElement(it.body).jsonObject
        assertEquals(JsonPrimitive("it"), body["language"])
        assertEquals("/reviews", it.path)
        val home = kotlinx.serialization.json.Json.parseToJsonElement(Writes.reviews(listOf("vocab_hvala" to 4), 2, id = "r2", now = 1).body).jsonObject
        assertFalse("language" in home)
    }
}
