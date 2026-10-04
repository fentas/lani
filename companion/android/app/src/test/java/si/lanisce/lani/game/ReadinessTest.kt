package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.Drill
import si.lanisce.lani.data.DrillKind
import si.lanisce.lani.data.DrillText
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.ReviewMark
import si.lanisce.lani.data.TransformItem
import si.lanisce.lani.data.TransformSet
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.SceneSpec
import java.time.LocalDate

/**
 * Readiness for the next level (companion/GAME.md, "The treasure map"): most of the level's tested grammar pages secure,
 * the reviews of the level's cards going well over two weeks, and two weeks of practice at the level. A fixture learner,
 * never the real data.
 */
class ReadinessTest {
    private val today: LocalDate = LocalDate.of(2026, 10, 20)

    private fun page(id: String, level: String) =
        GrammarPage(id, "sl", level, "📖", mapOf("sl" to id, "en" to id), mapOf("en" to "…"))

    /** Ten A1 pages, two A2 pages. */
    private val pages = (1..10).map { page("a1-$it", "A1") } + listOf(page("dajalnik", "A2"), page("orodnik", "A2"))

    /** Every A1 page named by the content, the A2 ones too. */
    private val named = pages.map { it.id }.toSet()

    /** [n] of the A1 pages secure (the first ones), one mastered among them; the rest learning. */
    private fun mastery(n: Int): (String) -> Mastery? = { id ->
        val i = id.removePrefix("a1-").toIntOrNull()
        when {
            i == null -> Mastery.NOT_YET
            i == 1 && n >= 1 -> Mastery.MASTERED
            i <= n -> Mastery.SECURE
            else -> Mastery.LEARNING
        }
    }

    /** [n] reviews a day for the last two weeks, [wrongEvery]th of them wrong. */
    private fun reviews(n: Int = 3, wrongEvery: Int = 10, level: String? = "A1"): List<ReviewMark> {
        var k = 0
        return (0 until 14).flatMap { d -> List(n) { ReviewMark(today.minusDays(d.toLong()), if (++k % wrongEvery == 0) 2 else 4, level) } }
    }

    /** Practised every weekday of the last three weeks. */
    private val weekdays = (0 until 21).map { today.minusDays(it.toLong()) }.filter { it.dayOfWeek.value <= 5 }.toSet()

    private val since = today.minusDays(30)

    private fun check(secure: Int = 8, marks: List<ReviewMark> = reviews(), days: Set<LocalDate> = weekdays, from: LocalDate? = since, level: String = "A1") =
        LevelReadiness.of(level, pages, named, mastery(secure), marks, days, from, today)

    @Test fun `ready with the rules secure, the reviews kept and two weeks of practice`() {
        val r = check()
        assertEquals("A1", r.from)
        assertEquals("A2", r.to)
        assertEquals(10, r.pages.size)
        assertEquals(7, r.needSecure) // 70 % of 10
        assertEquals(8, r.secure.size) // a mastered page counts
        assertEquals(42, r.reviews)
        assertEquals(0.9f, r.retention, 0.01f)
        assertTrue(r.grammar && r.remembered && r.seasoned && r.ready)
        assertEquals(listOf("a1-9", "a1-10"), r.toSecure)
    }

    @Test fun `too few rules secure`() {
        val r = check(secure = 6)
        assertFalse(r.grammar)
        assertFalse(r.ready)
        assertTrue(r.remembered && r.seasoned)
    }

    @Test fun `only the pages the content tests count, else all the level's`() {
        // the content names six A1 pages: 70 % of six is five
        val some = (1..6).map { "a1-$it" }.toSet() + "dajalnik"
        val r = LevelReadiness.of("A1", pages, some, mastery(5), reviews(), weekdays, since, today)
        assertEquals((1..6).map { "a1-$it" }, r.pages)
        assertEquals(5, r.needSecure)
        assertTrue(r.grammar)
        // content that names none of the level's pages: all of them
        assertEquals(10, LevelReadiness.tested("A1", pages, setOf("dajalnik")).size)
        assertEquals(listOf("dajalnik", "orodnik"), LevelReadiness.tested("A2", pages, emptySet()))
        // the rounding: 70 % rounded up
        assertEquals(1, LevelReadiness.needSecure(1))
        assertEquals(3, LevelReadiness.needSecure(4))
        assertEquals(19, LevelReadiness.needSecure(26))
        assertEquals(0, LevelReadiness.needSecure(0))
    }

    @Test fun `a level without pages has nothing to secure`() {
        val r = LevelReadiness.of("B2", pages, named, { Mastery.NEW }, reviews(level = "B2"), weekdays, since, today)
        assertTrue(r.pages.isEmpty())
        assertTrue(r.grammar)
        assertEquals("C1", r.to)
    }

    @Test fun `the reviews, two weeks of the level's cards, enough of them, most kept`() {
        // too many wrong
        assertFalse(check(marks = reviews(wrongEvery = 3)).remembered)
        // too few: 14 reviews in two weeks
        assertFalse(check(marks = reviews(n = 1)).remembered)
        // older ones don't count
        val old = reviews(wrongEvery = 2).map { it.copy(day = it.day.minusDays(20)) }
        assertEquals(42, check(marks = reviews() + old).reviews)
        // a card of a higher level is a stretch, a card that doesn't say counts
        assertEquals(0, check(marks = reviews(level = "A2")).reviews)
        assertEquals(42, check(marks = reviews(level = null)).reviews)
        // at A2, the A1 cards are of the level too
        assertEquals(42, check(marks = reviews(level = "A1"), level = "A2").reviews)
        assertEquals(42, check(marks = reviews(level = "A2"), level = "A2").reviews)
    }

    @Test fun `the time, ten days of practice over two weeks at the level`() {
        // a week of every day: seven days
        val week = (0 until 7).map { today.minusDays(it.toLong()) }.toSet()
        assertFalse(check(days = week).seasoned)
        // ten days, but the level began nine days ago
        val tenDays = (0 until 10).map { today.minusDays(it.toLong()) }.toSet()
        assertFalse(check(days = tenDays, from = today.minusDays(9)).seasoned)
        assertTrue(check(days = tenDays, from = today.minusDays(14)).seasoned)
        // days before the level began don't count
        val before = (20 until 40).map { today.minusDays(it.toLong()) }.toSet()
        assertEquals(0, check(days = before, from = today.minusDays(15)).practised)
        // not known when it began: as if today
        assertEquals(0, check(from = null).atLevel)
    }

    @Test fun `the top level has no next one`() {
        val r = check(level = "C2")
        assertNull(r.to)
        assertFalse(r.ready)
        assertEquals("A1", LevelReadiness.norm("beginner"))
        assertEquals("A2", LevelReadiness.norm("a2"))
        assertNull(LevelReadiness.next("C2"))
    }

    @Test fun `what the content names`() {
        val scene = SceneSpec(
            id = "trg", title = "Trg", art = "square", from = listOf("fire"),
            dialogs = listOf(Dialog("d", listOf(
                DialogLine(who = "micka", sl = "Kam greš?", en = "Where are you going?"),
                DialogLine(grammar = "kam-tozilnik", choices = listOf(
                    DialogChoice("Grem v trgovino.", ok = true),
                    DialogChoice("Grem v trgovini.", why = "Kam? the accusative", grammar = "kam-tozilnik-2"),
                )),
            ))),
        )
        val ex = listOf(Exercise.Cloze("Nimam ___.", listOf("časa"), grammar = "rodilnik-nikalnica"), Exercise.Flashcard("a", "b"))
        val drill = Drill("preobrat", DrillKind.TRANSFORM, "sl", "🔄", DrillText(mapOf("en" to "T")), transforms = listOf(
            TransformSet("preteklik", "pretekli-cas", "A1", DrillText(mapOf("en" to "Into the past.")), listOf(
                TransformItem(DrillText(mapOf("sl" to "Kuha.")), DrillText(mapOf("sl" to "Je kuhala."))),
            )),
        ))
        assertEquals(setOf("kam-tozilnik", "kam-tozilnik-2", "rodilnik-nikalnica", "pretekli-cas"), LevelReadiness.named(listOf(scene), ex, listOf(drill)))
    }

    @Test fun `the dashboard reads the reviews, the days practised and when the level began`() {
        val raw = """
            {"databases": {
              "learner_profile": {"profile_created": "2026-08-25", "learner": {"name": "Ana", "current_level": "A1", "level_since": "2026-09-01"}},
              "spaced_repetition": {"items": {
                "vocab_miza": {"type": "vocabulary", "content": "miza", "answer": "table", "difficulty": "A1",
                  "review_history": [{"date": "2026-09-02", "quality": 5}, {"date": "2026-09-09", "quality": 2}]},
                "vocab_kljub": {"type": "vocabulary", "content": "kljub", "answer": "despite", "difficulty": "",
                  "review_history": [{"date": "2026-09-10", "quality": 4}]},
                "genitive_after_iz": {"type": "error_pattern", "content": "iz Nemčija",
                  "review_history": [{"date": "2026-09-10", "quality": 1}]}}},
              "session_log": {"sessions": [{"date": "2026-09-02"}, {"date": "2026-09-02T20:00"}, {"date": "2026-09-09"}]}},
             "computed": {"today": "2026-09-10"}}
        """
        val d = Dashboard.parse(raw)
        assertEquals(
            listOf(ReviewMark(LocalDate.of(2026, 9, 2), 5, "A1"), ReviewMark(LocalDate.of(2026, 9, 9), 2, "A1"), ReviewMark(LocalDate.of(2026, 9, 10), 4, null)),
            d.reviewMarks,
        )
        assertEquals(setOf(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 9)), d.practised)
        assertEquals(LocalDate.of(2026, 9, 1), d.levelSince)
        // without level_since: when the profile was made
        assertEquals(LocalDate.of(2026, 8, 25), Dashboard.parse(raw.replace(""", "level_since": "2026-09-01"""", "")).levelSince)
    }
}
