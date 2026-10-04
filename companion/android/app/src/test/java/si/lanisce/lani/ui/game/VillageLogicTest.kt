package si.lanisce.lani.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.AgeStep
import si.lanisce.lani.game.BuildOption
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res

class VillageLogicTest {
    private val card = Exercise.Flashcard("hvala", "thanks")
    private val cloze = Exercise.Cloze("Jaz ___ Jan.", listOf("sem"))
    private val byType: (Exercise) -> Res = { if (it is Exercise.Cloze) Res.STONE else Res.FOOD }

    @Test fun `earn pairs use the exercise's resource`() {
        val p = earnPairs(listOf(card, cloze), listOf(Verdict.CORRECT, Verdict.WRONG), resourceOf = byType)
        assertEquals(listOf(Res.FOOD to Verdict.CORRECT, Res.STONE to Verdict.WRONG), p)
    }

    @Test fun `challenge skills override and stopping early only pays what was answered`() {
        val p = earnPairs(listOf(card, cloze, card), listOf(Verdict.ALMOST), listOf(Res.WOOD, Res.WOOD, Res.WOOD), byType)
        assertEquals(listOf(Res.WOOD to Verdict.ALMOST), p)
    }

    @Test fun `challenge reviews persist only card-backed answers`() {
        val r = challengeReviews(listOf("a", null, "c"), listOf(Verdict.CORRECT, Verdict.CORRECT, Verdict.WRONG))
        assertEquals(listOf("a" to 4, "c" to 1), r)
        assertEquals(3, challengeQuality(Verdict.ALMOST))
    }

    @Test fun `only correct answers count towards the pass mark`() {
        assertEquals(1, correctCount(listOf(Verdict.CORRECT, Verdict.ALMOST, Verdict.WRONG)))
        assertEquals(0, correctCount(listOf(Verdict.ALMOST, Verdict.ALMOST)))
        assertEquals(0, correctCount(emptyList()))
    }

    @Test fun `a reward from a finished run counts every answer as answered`() {
        assertEquals(6, Reward(emptyMap(), correct = 4, total = 6).answered)
        assertEquals(2, Reward(emptyMap(), correct = 1, total = 6, answered = 2).answered)
    }

    @Test fun `formats resources in a stable order`() {
        assertEquals("+18 🌾 +6 🪨", formatRes(mapOf(Res.STONE to 6, Res.FOOD to 18, Res.WOOD to 0)))
        assertEquals("", formatRes(emptyMap()))
        assertEquals(mapOf(Res.FOOD to 5, Res.WOOD to 2), addRes(mapOf(Res.FOOD to 3), mapOf(Res.FOOD to 2, Res.WOOD to 2)))
    }

    @Test fun `countdown labels`() {
        val h = 3_600_000L
        assertEquals("3 d", timeLeft(72 * h, 0))
        assertEquals("5 h", timeLeft(5 * h + 1, 0))
        assertEquals("42 min", timeLeft(42 * 60_000L, 0))
        assertEquals("<1 min", timeLeft(-5, 0))
        assertEquals("1:05", clock(65))
    }

    private fun option(t: BuildingType, cost: Map<Res, Int>, available: Boolean = true) =
        BuildOption(t, cost, available, if (available) null else "locked", "+1")

    @Test fun `next goal prefers fire, then advancing, then a new affordable building`() {
        val rich = GameState(resources = mapOf(Res.FOOD to 50, Res.WOOD to 50, Res.STONE to 50, Res.WISDOM to 50))
        val opts = listOf(
            option(BuildingType.TENT, mapOf(Res.WOOD to 5)),
            option(BuildingType.FIELD, mapOf(Res.WOOD to 10)),
            option(BuildingType.WELL, mapOf(Res.STONE to 1), available = false),
        )
        val noAdvance = AdvanceCheck(Age.TABOR, false, listOf("2 tents"))
        assertEquals(NextGoal.FeedFire, nextGoal(rich.copy(fire = 10), opts, noAdvance, emptyMap()))
        assertEquals(NextGoal.Advance(Age.TABOR), nextGoal(rich, opts, AdvanceCheck(Age.TABOR, true, emptyList()), emptyMap()))
        val withTent = rich.copy(buildings = listOf(Building("b1", BuildingType.TENT, 0)))
        assertEquals(BuildingType.FIELD, (nextGoal(withTent, opts, noAdvance, emptyMap()) as NextGoal.Build).option.type)
    }

    @Test fun `growing gathers what the next age is shortest of, else the scarcest resource`() {
        val poor = GameState(resources = mapOf(Res.FOOD to 9, Res.WOOD to 1, Res.STONE to 5, Res.WISDOM to 5))
        val caps = Res.entries.associateWith { 10 }
        // Tabor: a tent (25 🌾 15 🪵) and 20 🌾 10 🪵 → 36 🌾 short, 24 🪵 short: food, never stone
        val g = nextGoal(poor, listOf(option(BuildingType.TENT, mapOf(Res.WOOD to 5))), AdvanceCheck(Age.TABOR, false, listOf("x")), caps)
        assertEquals(NextGoal.Grow(Age.TABOR, listOf("x"), Res.FOOD), g)
        val qa = GameState(resources = mapOf(Res.FOOD to 10, Res.WOOD to 10))
        assertEquals(Res.FOOD, goalGather(qa, Age.TABOR))
        val oneTent = qa.copy(resources = mapOf(Res.FOOD to 45, Res.WOOD to 4), buildings = listOf(Building("t", BuildingType.TENT, 0)))
        assertEquals(Res.WOOD, goalGather(oneTent, Age.TABOR)) // the tent stands: 20 🌾 of 45, 6 🪵 short
        assertEquals(null, goalGather(GameState(resources = all(999)), Age.TABOR)) // nothing short
        assertEquals(NextGoal.Gather(Res.WOOD), nextGoal(poor, emptyList(), AdvanceCheck(null, false, emptyList()), caps))
    }

    private fun all(n: Int) = Res.entries.associateWith { n }

    @Test fun `steps left use Slovene dual and plural`() {
        assertEquals("še 1 korak · 1 step to go", stepsLeft(1))
        assertEquals("še 2 koraka · 2 steps to go", stepsLeft(2))
        assertEquals("še 3 koraki · 3 steps to go", stepsLeft(3))
        assertEquals("še 5 korakov · 5 steps to go", stepsLeft(5))
        assertEquals("še 101 korak · 101 steps to go", stepsLeft(101))
    }

    @Test fun `result line separates almost and unanswered`() {
        assertEquals("5 / 7 pravilno · correct", tallyText(Reward(emptyMap(), correct = 5, total = 7)))
        assertEquals("✅ 2 · 🟡 1 · 3/7 odgovorjenih · answered", tallyText(Reward(emptyMap(), correct = 2, total = 7, answered = 3, almost = 1)))
        assertEquals("✅ 2 · 3/7 odgovorjenih · answered", tallyText(Reward(emptyMap(), correct = 2, total = 7, answered = 3)))
    }

    @Test fun `a run past the day's full-pay answers says so, with the right number form`() {
        assertEquals(null, tiredText(Reward(emptyMap())))
        assertTrue(tiredText(Reward(emptyMap(), tired = 1))!!.startsWith("🌙 1 odgovor za pol nagrade"))
        assertTrue(tiredText(Reward(emptyMap(), tired = 2))!!.startsWith("🌙 2 odgovora za"))
        assertTrue(tiredText(Reward(emptyMap(), tired = 3))!!.startsWith("🌙 3 odgovori za"))
        assertTrue(tiredText(Reward(emptyMap(), tired = 12))!!.startsWith("🌙 12 odgovorov za"))
    }

    @Test fun `when only friends and words are missing, the goal is to make friends`() {
        val s = GameState(age = Age.TABOR, resources = mapOf(Res.FOOD to 999, Res.WOOD to 999, Res.STONE to 999))
        val steps = listOf(
            si.lanisce.lani.game.AgeStep(si.lanisce.lani.game.AgeStep.Kind.BUILDING, "🪵", "Palisada", 1, 1),
            si.lanisce.lani.game.AgeStep(si.lanisce.lani.game.AgeStep.Kind.WORDS, "📚", "Besede", 30, 60),
            si.lanisce.lani.game.AgeStep(si.lanisce.lani.game.AgeStep.Kind.FRIENDS, "💞", "Prijatelji", 0, 1),
        )
        val check = AdvanceCheck(Age.ZASELEK, false, listOf("60 besed · words (you know 30)", "1 prijatelj · 1 friend (0/1)"), steps)
        val opts = listOf(option(BuildingType.TENT, mapOf(Res.WOOD to 5)))
        assertEquals(NextGoal.Friends(Age.ZASELEK, check.missing), nextGoal(s, opts, check, emptyMap()))
        // friends made, every plot built: learn
        val full = s.copy(buildings = (0 until 5).map { Building("b$it", BuildingType.TENT, it) })
        val words = check.copy(missing = check.missing.take(1), steps = steps.take(2))
        assertEquals(NextGoal.Learn(Age.ZASELEK, words.missing), nextGoal(full, opts, words, emptyMap()))
        assertEquals("Prijatelji · Friends: 🛖 Zaselek", NextGoal.Friends(Age.ZASELEK, emptyList()).short())
    }

    @Test fun `every step that holds the age back leads somewhere now, rusty words to polishing them`() {
        fun step(kind: AgeStep.Kind, have: Int, need: Int, res: Res? = null, broken: Boolean = false) = AgeStep(kind, "", "", have, need, res, broken)
        assertEquals(StepAction.POLISH, stepAction(step(AgeStep.Kind.RUSTY, 33, 36)))
        assertEquals(null, stepAction(step(AgeStep.Kind.RUSTY, 36, 36)))
        assertEquals(StepAction.LEARN, stepAction(step(AgeStep.Kind.WORDS, 30, 60)))
        assertEquals(StepAction.HELP, stepAction(step(AgeStep.Kind.FRIENDS, 0, 1)))
        assertEquals(StepAction.GATHER, stepAction(step(AgeStep.Kind.RESOURCE, 5, 20, Res.FOOD)))
        assertEquals(StepAction.BUILD, stepAction(step(AgeStep.Kind.BUILDING, 0, 1)))
        assertEquals(null, stepAction(step(AgeStep.Kind.BUILDING, 0, 1, broken = true))) // repaired from its own sheet
    }

    @Test fun `when rusty words (and words or friends) are all that is missing, the goal is to polish them`() {
        val s = GameState(age = Age.TABOR, resources = mapOf(Res.FOOD to 999, Res.WOOD to 999, Res.STONE to 999))
        val rusty = AgeStep(AgeStep.Kind.RUSTY, "🔩", "3 zarjavele besede", 57, 60)
        val friends = AgeStep(AgeStep.Kind.FRIENDS, "💞", "Prijatelji", 0, 1)
        val built = AgeStep(AgeStep.Kind.BUILDING, "🪵", "Palisada", 1, 1)
        val check = AdvanceCheck(Age.ZASELEK, false, listOf("3 zarjavele besede", "1 prijatelj"), listOf(built, rusty, friends))
        val opts = listOf(option(BuildingType.TENT, mapOf(Res.WOOD to 5)))
        assertEquals(NextGoal.Polish(Age.ZASELEK, check.missing, 3), nextGoal(s, opts, check, emptyMap()))
        assertEquals("Zarjavele besede · Rusty words: 🛖 Zaselek", NextGoal.Polish(Age.ZASELEK, emptyList(), 3).short())
        // a building missing too: growing comes first (its card offers the polishing as well)
        val palisade = built.copy(have = 0)
        val grow = check.copy(steps = listOf(palisade, rusty))
        assertTrue(nextGoal(s, opts, grow, emptyMap()) !is NextGoal.Polish)
    }

    @Test fun `with every plot taken the goal is to learn towards the next age`() {
        val full = GameState(
            resources = mapOf(Res.FOOD to 99, Res.WOOD to 99),
            buildings = listOf(Building("a", BuildingType.TENT, 0), Building("b", BuildingType.FIELD, 1)),
        )
        val check = AdvanceCheck(Age.TABOR, false, listOf("Nauči se 30 besed · Know 30 words (12)"))
        assertEquals(NextGoal.Learn(Age.TABOR, check.missing), nextGoal(full, listOf(option(BuildingType.TENT, mapOf(Res.WOOD to 5))), check, emptyMap()))
    }
}
