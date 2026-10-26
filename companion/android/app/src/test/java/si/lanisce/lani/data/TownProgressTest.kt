package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/** Progress between towns as the app reads it (data/TownProgress.kt): GET /towns/:id/progress, the sharing, the weekly note. */
class TownProgressTest {
    private val luka = "a1b2c3d4e5f60718293a4b5c6d7e8f90"

    /** A child's progress at details, as the bridge answers it (test/smoke/progress.ts makes the same). */
    private val details = """
        {"v":1,"town":{"id":"$luka","name":"Il mio villaggio","learner":"Luka","culture":"friuli","language":"it"},
         "level":"details","date":"2026-10-28","child":true,"guardian":true,"base":"sl",
         "summary":{"streak":4,"practised_today":true,
           "week":{"start":"2026-10-26","days":[true,true,true,false,false,false,false],"days_practised":3,"minutes":35,"reviews":18,"new_words":2},
           "words":{"known":8,"tracked":10},"level":"A1","target_level":"A2",
           "course":{"level":"A1","done":1,"of":18,"current":{"id":"pozdravi","title":{"it":"Saluti","sl":"Pozdravi"}}}},
         "details":{"learning":[{"id":"vocab_cane","word":"il cane","meanings":{"sl":"pes","en":"the dog"},"added":"2026-10-28","state":"learning"},
             {"id":"vocab_casa","word":"la casa","meanings":{"sl":"hiša"},"added":"2026-10-27","state":"rusty"}],
           "hard":{"patterns":[{"id":"articles_il_la","name":"Articoli: il / la","category":"grammar","count":4,"last":"2026-10-27","trend":"worse",
               "items":[{"id":"vocab_casa","word":"la casa","meanings":{"sl":"hiša"}}]}],
             "words":[{"id":"vocab_mela","word":"la mela","meanings":{"sl":"jabolko"},"misses":1,"last":"2026-10-26"}]},
           "due":{"today":1,"week":6}},
         "challenges":[{"id":"ch-1","at":"2026-10-28T10:00:00Z","type":"translate","text":"pes","answered":true,"answered_at":"2026-10-28T11:00:00Z","score":9,"note":"Luka knew it."}],
         "fetched_at":"2026-10-28T12:00:00Z",
         "sharing":{"share":"summary","share_default":true,"choices":["village","summary","details"],"theirs":{"share":"details","at":"2026-10-28T12:00:00Z","guardian":true}},
         "something_newer":{"x":1}}
    """.trimIndent()

    @Test fun `a child's progress at details reads whole, its parent's view`() {
        val p = TownProgresses.parse(details)
        assertEquals(ShareLevels.DETAILS, p.level)
        assertTrue(p.child && p.guardian)
        assertEquals("sl", p.base)
        val s = p.summary!!
        assertEquals(4, s.streak)
        assertEquals(listOf(true, true, true, false, false, false, false), s.week.days)
        assertEquals(3, s.week.daysPractised)
        assertEquals(35, s.week.minutes)
        assertEquals(2, s.week.newWords)
        assertEquals(8, s.words.known)
        assertEquals("Saluti", s.course?.current?.title?.get("it"))
        val d = p.details!!
        assertEquals("il cane", d.learning.first().word)
        assertEquals(TownProgresses.STATE_RUSTY, d.learning[1].state)
        assertEquals("Articoli: il / la", d.hard.patterns.single().name)
        assertEquals("la casa", d.hard.patterns.single().items.single().word)
        assertEquals(1, d.hard.words.single().misses)
        assertEquals(6, d.due.week)
        assertEquals(9.0, p.challenges.single().score!!, 0.0)
        // what each shares: this learner the default summary, the child details to its guardian
        assertEquals(ShareLevels.SUMMARY, p.sharing?.share)
        assertTrue(p.sharing!!.shareDefault)
        assertEquals(ShareLevels.DETAILS, p.sharing!!.theirs?.share)
        assertTrue(p.sharing!!.theirs!!.guardian)
    }

    @Test fun `a summary has no details, just the village neither, and an older field is left alone`() {
        val summary = TownProgresses.parse("""{"town":{"id":"$luka","learner":"Jan"},"level":"summary","date":"2026-10-28","summary":{"streak":2,"week":{"days":[true]},"words":{"known":3}}}""")
        assertNull(summary.details)
        assertEquals(2, summary.summary?.streak)
        assertFalse(summary.child)
        val village = TownProgresses.parse("""{"town":{"id":"$luka"},"level":"village","date":"2026-10-28"}""")
        assertNull(village.summary)
        assertNull(village.details)
        assertTrue(village.challenges.isEmpty())
    }

    @Test fun `a refusal keeps the sharing, and says why in the learner's pair`() = withPair {
        val why = TownProgresses.parseProblem("""{"error":"x","code":"unreachable","sharing":{"share":"details","guardian":true,"floor":"details","choices":["details"]}}""")
        assertEquals("unreachable", why.code)
        assertEquals(listOf(ShareLevels.DETAILS), why.sharing?.choices)
        assertTrue(TownProgresses.problem(502, "unreachable").startsWith("📡"))
        assertTrue(TownProgresses.problem(502, "unsupported").startsWith("🧰"))
        assertTrue(TownProgresses.problem(403, "guardian").startsWith("👪"))
        assertTrue(TownProgresses.problem(409, "not_for_a_child").startsWith("👨‍👩‍👦"))
        assertTrue(TownProgresses.problem(500, null).contains("500"))
        assertEquals(ProgressProblem(), TownProgresses.parseProblem("not json"))
    }

    @Test fun `GET towns rows carry what each shares, and an older bridge none`() {
        val list = Towns.parseList(
            """{"self":{"id":"x","child":true},"towns":[{"id":"$luka","status":"ok","sharing":{"share":"details","guardian":true,"floor":"details","choices":["details"]}},{"id":"b","status":"ok"}]}""",
        )
        assertTrue(list.towns[0].sharing!!.guardian)
        assertEquals("details", list.towns[0].sharing!!.floor)
        assertNull(list.towns[1].sharing)
    }

    @Test fun `the weekly note comes as an event and leads to the town's progress`() = withPair {
        val ev = BridgeEvent.parse("""{"type":"town_progress","town":"$luka","name":"Il mio villaggio","learner":"Teo","week":"2026-W44","days":4,"new_words":23,"minutes":50,"streak":4,"hard":["la/il"]}""")
        assertEquals(BridgeEvent.TownProgressNote(luka, "Teo", "2026-W44", 4, 23, listOf("la/il")), ev)
        assertEquals(DeepLink.TownProgress(luka), DeepLink.of(ev!!))
        assertEquals(DeepLink.TownProgress(luka), DeepLink.parse(DeepLink.encode(DeepLink.TownProgress(luka))))
        assertNull(DeepLink.parse("progress:not-a-town"))
        assertNull(BridgeEvent.parse("""{"type":"town_progress","town":"x"}"""))
        assertEquals("Teo ta teden: 4 dnevi, 23 novih besed, težave: la/il · Teo this week: 4 days, 23 new words, hard: la/il", (ev as BridgeEvent.TownProgressNote).text())
        val none = BridgeEvent.TownProgressNote(luka, "Teo", "2026-W44", 1, 1, emptyList()).text()
        assertEquals("Teo ta teden: 1 dan, 1 nova beseda · Teo this week: 1 day, 1 new word", none)
    }

    private fun withPair(block: () -> Unit) {
        val before = L10n.pair
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        try {
            block()
        } finally {
            L10n.pair = before
        }
    }
}
