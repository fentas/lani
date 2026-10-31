package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.TargetNext
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * A raised target that starts with the village's next age (companion/GAME.md "Ages", "A target raised later";
 * docs/plans/05-built/target-next-age.md): the step the village is on keeps the words of the target it began with, the
 * raise counts from the age-up on, exactly then and never before, and the bridge's applying it in the profile changes
 * nothing for the app.
 */
class AimsTest {
    private val t0 = noon(day0)
    private val was = L10n.pair

    /** Teo: A2 from setup's old default, B1 from Vas on (2026-10-31). */
    private val teo = TargetNext("B1", "VAS", "2026-10-31")

    @Before fun setUp() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = LangPair(Lang.SL, Lang.EN)
    }

    @After fun tearDown() {
        L10n.pair = was
    }

    /** A hamlet with everything Vas asks but the words: its buildings, its stores and three friends. */
    private fun hamlet() = GameState(
        age = Age.ZASELEK,
        resources = Res.entries.associateWith { 2000 },
        buildings = listOf(BuildingType.PALISADE, BuildingType.FIELD, BuildingType.CHURCH, BuildingType.LIPA, BuildingType.HUT, BuildingType.HUT)
            .mapIndexed { i, t -> Building("b$i", t, i) },
        bonds = mapOf("micka" to Bond(30), "luka" to Bond(30), "janez" to Bond(30)),
    )

    private fun words(c: AdvanceCheck) = c.steps.first { it.kind == AgeStep.Kind.WORDS }

    @Test fun `the step the village is on keeps its target's words until the age-up`() {
        val s = hamlet()
        assertEquals("A2", Aims.at("A2", teo, s.age))
        val check = GameEngine.advanceCheck(s, 45, Aims.at("A2", teo, s.age))
        assertEquals(45 to 45, words(check).have to words(check).need)
        assertTrue(check.missing.toString(), check.ok)
        // B1 at once would have asked 150: the raise doesn't touch this step
        assertEquals(150, words(GameEngine.advanceCheck(s, 45, "B1")).need)
    }

    @Test fun `the raise counts from the age-up on, exactly then`() {
        val s = hamlet()
        val vas = GameEngine.advance(s, 45, t0, Aims.at("A2", teo, s.age))
        assertEquals(Age.VAS, vas.age)
        assertEquals("B1", Aims.at("A2", teo, vas.age))
        // the new step counts B1's words: Trg 500 (A2's would have been 150), and the village can't go on with 45
        val next = GameEngine.advanceCheck(vas, 45, Aims.at("A2", teo, vas.age))
        assertEquals(Age.TRG, next.next)
        assertEquals(500, words(next).need)
        assertFalse(next.ok)
        assertSame(vas, GameEngine.advance(vas, 45, t0, Aims.at("A2", teo, vas.age)))
    }

    @Test fun `never before its age, and the same past it`() {
        assertEquals(listOf("A2", "A2", "A2", "B1", "B1", "B1"), Age.entries.map { Aims.at("A2", teo, it) })
        assertEquals(listOf(teo, teo, teo, null, null, null), Age.entries.map { Aims.waiting(teo, it) })
        assertEquals("A2", Aims.at("A2", null, Age.MESTO))
        // an age the app doesn't know: no raise
        assertEquals("A2", Aims.at("A2", TargetNext("B1", "METROPOLA"), Age.MESTO))
    }

    @Test fun `the bridge applying it changes nothing for the app`() {
        val waiting = Dashboard.parse(state("""{"current_level": "A1", "target_level": "A2", "target_level_next": {"level": "B1", "from_age": "VAS", "set": "2026-10-31"}}"""))
        val applied = Dashboard.parse(state("""{"current_level": "A1", "target_level": "B1", "target_level_since": "2026-11-02"}"""))
        assertEquals("A2", waiting.targetLevel)
        assertEquals(teo, waiting.targetNext)
        assertNull(applied.targetNext)
        for (age in listOf(Age.VAS, Age.TRG, Age.MESTO)) assertEquals(age.name, Aims.at(applied.targetLevel, applied.targetNext, age), Aims.at(waiting.targetLevel, waiting.targetNext, age))
        // nothing waits once applied: no note
        assertNull(Aims.note(applied.targetNext, Age.VAS))
    }

    @Test fun `a lowered target is the profile's own at once`() {
        // the bridge writes a lower target at once (src/aim.ts): the hamlet's step to Vas asks A2's 45, not B2's 300
        val lowered = Dashboard.parse(state("""{"current_level": "A1", "target_level": "A2"}"""))
        assertEquals(45, words(GameEngine.advanceCheck(hamlet(), 45, Aims.at(lowered.targetLevel, lowered.targetNext, Age.ZASELEK))).need)
        assertEquals(300, words(GameEngine.advanceCheck(hamlet(), 45, Aims.at("B2", null, Age.ZASELEK))).need)
    }

    @Test fun `a pending target the app can't read is no raise`() {
        assertNull(TargetNext.parse(kotlinx.serialization.json.JsonPrimitive("B1")))
        val bad = Dashboard.parse(state("""{"target_level": "a2", "target_level_next": {"level": "B9", "from_age": "VAS"}}"""))
        assertEquals("A2", bad.targetLevel)
        assertNull(bad.targetNext)
        // a placeholder of the template is B2, the default
        assertEquals("B2", Dashboard.parse(state("""{"target_level": "{A2|B1|B2|C1|C2}"}""")).targetLevel)
    }

    @Test fun `the age card and the progress screen say it, in the learner's pair`() {
        assertEquals("🎯 Od naslednje dobe: B1 · From the next age: B1", Aims.note(teo, Age.ZASELEK))
        assertEquals("${Age.TRG.emoji} ${Age.TRG.names.target}: 500 besed · ${Age.TRG.names.base}: 500 words", Aims.thenLine(teo, Age.ZASELEK))
        assertEquals(Age.TRG to 500, Aims.then(teo, Age.ZASELEK))
        assertNull(Aims.note(teo, Age.VAS))
        assertNull(Aims.thenLine(teo, Age.VAS))
        // Teo's pair: Italian from German
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        assertEquals("🎯 Dalla prossima età: B1 · Ab dem nächsten Zeitalter: B1", Aims.note(teo, Age.ZASELEK))
        assertTrue(Aims.thenLine(teo, Age.ZASELEK)!!.endsWith(": 500 parole · ${Age.TRG.names.base}: 500 Wörter"))
        // a raise that starts with the town: nothing after it to name
        assertNull(Aims.then(TargetNext("C1", "MESTO"), Age.TRG))
    }

    private fun state(learner: String) = """{"databases": {"learner_profile": {"learner": $learner}}, "computed": {"today": "2026-10-31"}}"""
}
