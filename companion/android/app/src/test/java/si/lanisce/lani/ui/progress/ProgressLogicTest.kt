package si.lanisce.lani.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.CourseFixture
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.CourseProgress
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.HuntStatus
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.Readiness
import si.lanisce.lani.game.RuleRecord
import si.lanisce.lani.game.SheetBest
import si.lanisce.lani.game.Station
import si.lanisce.lani.game.StationRecord
import si.lanisce.lani.game.TreasureHunt
import si.lanisce.lani.game.UnitProgress
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDate

/**
 * The progress screen's way to the next level ("🧭 Do A2 · To A2": the words and the treasure map's three signs, the
 * map's state) and its grammar section (the school's units, the book's pages, the counting cards). Fixture learners,
 * never the real data.
 */
class ProgressLogicTest {
    private val today: LocalDate = LocalDate.of(2026, 10, 18)

    /** At A1: 5 of the 26 tested pages secure (19 needed), 24 reviews kept at 22, 12 days practised over 20. */
    private val notReady = Readiness(
        from = "A1", to = "A2",
        pages = (1..26).map { "p$it" }, secure = (1..5).map { "p$it" }, needSecure = 19,
        reviews = 24, kept = 22, practised = 12, atLevel = 20,
    )

    /** Every sign met. */
    private val ready = notReady.copy(secure = (1..20).map { "p$it" }, reviews = 40, kept = 36)

    private fun path(r: Readiness = notReady, known: Int = 312, status: HuntStatus = HuntStatus.NONE, hunt: TreasureHunt? = null, waiting: Boolean = false) =
        ProgressLogic.path(r, known, status, hunt, Station.entries.toList(), "Stari Janez", waiting, today)!!

    @Test fun `each sign with its numbers, met or not`() {
        val p = path()
        assertEquals("A1", p.from)
        assertEquals("A2", p.to)
        assertEquals(listOf(PathSign.WORDS, PathSign.GRAMMAR, PathSign.REVIEWS, PathSign.PRACTICE), p.lines.map { it.sign })
        // the words past A1's 300; the grammar short; the reviews too few (24 of 30), though 92 % right; the practice there
        assertEquals(listOf(true, false, false, true), p.lines.map { it.met })
        assertEquals(listOf("312 / 300", "5 / 19", "92 %", "12 / 10"), p.lines.map { it.value })
    }

    @Test fun `the lines read in both languages, and in the base for TalkBack`() {
        val (words, grammar, reviews, practice) = path().lines
        assertEquals("Besede · Words", words.title)
        assertNull(words.caption)
        assertEquals("Utrjena pravila ravni A1 · Rules of A1 secure", grammar.title)
        assertEquals("Potrebno: 19 od 26 strani ravni (70 %) · Needed: 19 of the level's 26 pages (70 %)", grammar.caption)
        assertEquals("Ponovitve, zadnjih 14 dni · Reviews, last 14 days", reviews.title)
        assertEquals("24 od vsaj 30 ponovitev; pravilnih: vsaj 80 % · 24 of at least 30 reviews; right: at least 80 %", reviews.caption)
        assertEquals("Dnevi vaje na ravni A1 · Days of practice at A1", practice.title)
        assertEquals("Na ravni A1 si 20 dni (vsaj 14) · You've been at A1 for 20 days (at least 14)", practice.caption)
        assertEquals("Rules of A1 secure: 5 / 19. Needed: 19 of the level's 26 pages (70 %). not yet", grammar.description)
        assertEquals("Words: 312 / 300. met", words.description)
    }

    @Test fun `in German the base half is German`() {
        val before = L10n.pair
        try {
            L10n.pair = LangPair(Lang.SL, Lang.DE)
            val practice = path().lines.last()
            assertEquals("Na ravni A1 si 20 dni (vsaj 14) · Du bist seit 20 Tagen auf A1 (mindestens 14)", practice.caption)
            assertEquals("Übungstage auf A1: 12 / 10. Du bist seit 20 Tagen auf A1 (mindestens 14). erfüllt", practice.description)
        } finally {
            L10n.pair = before
        }
    }

    @Test fun `few words and no reviews yet`() {
        val p = path(notReady.copy(reviews = 0, kept = 0, practised = 3, atLevel = 1), known = 120)
        assertEquals(listOf(false, false, false, false), p.lines.map { it.met })
        assertEquals("120 / 300", p.lines[0].value)
        assertEquals("—", p.lines[2].value)
        assertEquals("Na ravni A1 si 1 dan (vsaj 14) · You've been at A1 for 1 day (at least 14)", p.lines[3].caption)
    }

    @Test fun `the pages still to secure while the grammar sign isn't met, in the book's order`() {
        assertEquals((6..13).map { "p$it" }, path().toSecure) // the first eight of the 21
        assertEquals(13, path().toSecureMore)
        assertTrue(path(ready).toSecure.isEmpty())
        assertEquals(0, path(ready).toSecureMore)
    }

    @Test fun `a page's chip is its title up to the colon`() {
        val dual = GrammarPage("dvojina", "sl", "A1", "✌️", mapOf("sl" to "Dvojina: midva sva, dva brata", "en" to "The dual"), mapOf("en" to "…"))
        assertEquals("Dvojina", ProgressLogic.chipTitle(dual))
        val place = dual.copy(title = mapOf("sl" to "Kje? Predlogi kraja"))
        assertEquals("Kje? Predlogi kraja", ProgressLogic.chipTitle(place))
    }

    @Test fun `the map is coming, waiting once ready or offered, the hunt on, or found`() {
        assertEquals(MapState.Coming("Stari Janez"), path().map)
        // ready: the storyteller offers it at the next check; offered: it waits, whatever the signs say now
        assertEquals(MapState.Waiting("Stari Janez"), path(ready).map)
        assertEquals(MapState.Waiting("Stari Janez"), path(status = HuntStatus.OFFERED, hunt = TreasureHunt("A1", "A2", offered = "2026-10-17")).map)
        // on: two stations passed of the six
        val on = TreasureHunt(
            "A1", "A2", offered = "2026-10-10", taken = "2026-10-11",
            stations = mapOf("letter" to StationRecord(tries = 1, passed = "2026-10-11"), "riddle" to StationRecord(tries = 2, passed = "2026-10-12")),
        )
        assertEquals(MapState.On(2, 6), path(ready, status = HuntStatus.ON, hunt = on).map)
        assertNull(path(ready, status = HuntStatus.ON, hunt = on).found)
        // found: the level rose to A2 (the path goes on to B1), the node doesn't have it yet
        val found = on.copy(found = "2026-10-14")
        val next = path(notReady.copy(from = "A2", to = "B1"), known = 312, status = HuntStatus.FOUND, hunt = found, waiting = true)
        assertEquals(TreasureFound("A2", tutorWaits = true), next.found)
        assertEquals(MapState.Coming("Stari Janez"), next.map)
        assertEquals("312 / 1000", next.lines[0].value) // A2's words: B1's 1000
        assertFalse(next.lines[0].met)
        // the tutor has it: told for a week after it was found, then no more
        val a2 = notReady.copy(from = "A2", to = "B1")
        assertEquals(TreasureFound("A2", tutorWaits = false), path(a2, status = HuntStatus.FOUND, hunt = found.copy(confirmed = true)).found)
        assertNull(path(a2, status = HuntStatus.FOUND, hunt = found.copy(found = "2026-10-01", confirmed = true)).found)
        assertEquals(TreasureFound("A2", tutorWaits = true), path(a2, status = HuntStatus.FOUND, hunt = found.copy(found = "2026-10-01"), waiting = true).found)
    }

    @Test fun `no path at the top`() {
        assertNull(ProgressLogic.path(notReady.copy(from = "C2", to = null), 9000, HuntStatus.NONE, null, emptyList(), "Stari Janez", false))
    }

    // --- the grammar section ---

    private fun page(id: String, level: String) = GrammarPage(id, "sl", level, "📖", mapOf("sl" to id, "en" to "$id (en)"), mapOf("en" to "…"))

    /** Six A1 pages and an A2 one; four of the A1 pages met. */
    private val pages = listOf("biti", "spol", "zanikanje", "mnozina", "tozilnik", "rodilnik").map { page(it, "A1") } + page("dajalnik", "A2")

    private val state = GameState(
        age = Age.ZASELEK,
        grammar = mapOf(
            "sl/biti" to RuleRecord("2026-09-27", right = 21, wrong = 2),
            "sl/spol" to RuleRecord("2026-10-13", right = 5, wrong = 3),
            "sl/zanikanje" to RuleRecord("2026-10-14", right = 1, wrong = 2),
            "sl/mnozina" to RuleRecord("2026-10-16"),
            "sl/dajalnik" to RuleRecord("2026-10-16"),
        ),
        course = CourseProgress(
            classroom = "2026-10-13", opened = mapOf("sl/A1" to "2026-10-13"),
            units = mapOf(
                "sl/biti" to UnitProgress(started = "2026-10-13", intro = 3, introDone = true, test = SheetBest(2, 0, 2, "2026-10-15"), done = "2026-10-15"),
                "sl/spol" to UnitProgress(started = "2026-10-16", intro = 2),
            ),
        ),
    )

    private val mastery: (String) -> Mastery? = { id ->
        when (id) {
            "biti" -> Mastery.MASTERED
            "spol", "zanikanje" -> Mastery.LEARNING
            "mnozina" -> Mastery.NEW
            "dajalnik" -> Mastery.NOT_YET
            else -> Mastery.NEW
        }
    }
    private val meter: (String) -> Int? = { id -> mapOf("biti" to 5, "spol" to 3, "zanikanje" to 1)[id] }

    private fun grammar(level: String = "A1", s: GameState? = state, counting: List<ReviewCard>? = null) =
        ProgressLogic.grammar(level, "sl", CourseFixture.course, s, pages, mastery, meter, counting, today)

    @Test fun `the school's units of the level, done, begun, and the one suggested next`() {
        val sc = grammar().school!!
        assertEquals("A1", sc.level)
        // biti, spol, osebni-zaimki (planned) and extra (written, not in the outline): one done
        assertEquals(1, sc.done)
        assertEquals(4, sc.of)
        assertEquals(listOf("spol"), sc.started.map { it.id })
        assertEquals("spol", sc.next?.id) // the first written one not done
        assertTrue(sc.open)
        // the class not begun yet (the camp): nothing open
        assertFalse(grammar(s = GameState(age = Age.OGENJ)).school!!.open)
        // a level the course has no units of yet
        assertNull(grammar(level = "B1").school)
    }

    @Test fun `the book's pages of the level, met and secure, the weakest first`() {
        val b = grammar().book!!
        assertEquals("A1", b.level)
        assertEquals(6, b.pages)
        assertEquals(4, b.met) // dajalnik is A2's
        assertEquals(1, b.secure) // biti, mastered
        // the least stars first, then the page not practised yet
        assertEquals(listOf("zanikanje" to 1, "spol" to 3, "mnozina" to null), b.weakest.map { it.page.id to it.stars })
        assertEquals(1, grammar(level = "A2").book!!.pages)
    }

    @Test fun `the counting cards while counting is on`() {
        assertNull(grammar().counting)
        val cards = listOf(
            ReviewCard("c1", "en stol", "", kind = "counting", repetitions = 3, lastQuality = 5, due = today.plusDays(4)),
            ReviewCard("c2", "ena miza", "", kind = "counting", repetitions = 1, lastQuality = 4, due = today),
            ReviewCard("c3", "eno okno", "", kind = "counting", repetitions = 0, lastQuality = 2, due = today.minusDays(1)),
        )
        assertEquals(CountingStanding(cards = 3, secure = 1, due = 2), grammar(counting = cards).counting)
    }

    @Test fun `nothing without the village`() {
        val g = grammar(s = null)
        assertTrue(g.empty)
    }
}
