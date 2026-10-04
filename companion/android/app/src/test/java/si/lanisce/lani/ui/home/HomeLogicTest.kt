package si.lanisce.lani.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.FamilyAnswer
import si.lanisce.lani.data.FamilyChallenge
import si.lanisce.lani.data.FamilyFeedback
import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.data.PackInfo
import si.lanisce.lani.data.QuestMeta
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.Happening
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.villagers.Arrival
import si.lanisce.lani.game.villagers.Villager

class HomeLogicTest {
    private fun dash(
        due: Int = 0,
        weak: List<String> = emptyList(),
        level: String = "A1",
        minutes: Int = 0,
        goal: Int = 0,
        practised: Boolean = false,
        words: Int = 27,
    ) = Dashboard(
        name = "Jan", level = level, targetLevel = "B2", streak = 2, achievements = 0, xp = 0, wordsTracked = words,
        dueCards = List(due) { ReviewCard("c$it", "f", "b") }, weakPatterns = weak,
        minutesToday = minutes, practisedToday = practised, goalMinutes = goal,
    )

    private val dvojina = ModuleInfo("dvojina", 1, "Dvojina", "A2", targets = listOf("dual_verb_forms"), quest = QuestMeta("Učiteljica Mojca", "👩‍🏫", "…"))
    private val clitic = ModuleInfo("clitic", 1, "Where does se go?", "A1", targets = listOf("clitic_placement_se"))
    private val biti = ModuleInfo("biti", 1, "Biti", "A2")
    private val kitchen = PackInfo("v-kuhinji", "V kuhinji", total = 24, learned = 3)
    private val numbers = PackInfo("stevila", "Števila", total = 24, learned = 0)
    private val question = FamilyChallenge("f1", "Maja", text = "Kako si?")

    // --- today ------------------------------------------------------------------------------------

    @Test fun `due reviews come first, then ideas of other kinds`() {
        val t = HomeLogic.today(dash(due = 12, weak = listOf("clitic_placement_se")), listOf(dvojina, clitic), setOf("dvojina"), listOf(kitchen), emptySet())
        assertEquals(HomeAction.Review(12), t.primary)
        assertEquals(listOf("module:clitic", "pack:v-kuhinji"), t.more.map { it.key })
        assertEquals(12, t.due)
    }

    @Test fun `without reviews a module for a weak spot leads, new ones before seen ones`() {
        val weakNew = clitic.copy(id = "clitic2")
        val t = HomeLogic.today(dash(weak = listOf("clitic_placement_se")), listOf(clitic, weakNew, biti), setOf("clitic2", "biti"), listOf(kitchen), emptySet())
        val p = t.primary as HomeAction.Module
        assertEquals("clitic2", p.info.id)
        assertTrue(p.weak)
        assertTrue(p.isNew)
        // Interleaving: no second module next to a module.
        assertTrue(t.more.none { it is HomeAction.Module })
        assertEquals(listOf("pack:v-kuhinji", "talk"), t.more.map { it.key })
    }

    @Test fun `a module the tutor just published leads when no weak spot has one`() {
        val t = HomeLogic.today(dash(weak = listOf("something_else")), listOf(clitic, biti), setOf("biti"), listOf(kitchen), emptySet())
        assertEquals("module:biti", t.primary.key)
        assertFalse((t.primary as HomeAction.Module).weak)
    }

    @Test fun `then new words, the pack in progress before an untouched one`() {
        val t = HomeLogic.today(dash(), listOf(clitic), emptySet(), listOf(numbers, kitchen), emptySet())
        assertEquals("pack:v-kuhinji", t.primary.key)
        assertEquals(listOf("talk"), t.more.map { it.key })
    }

    @Test fun `with nothing else, talking`() {
        val t = HomeLogic.today(dash(), emptyList(), emptySet(), listOf(kitchen.copy(learned = 24)), emptySet())
        assertEquals(HomeAction.Talk(null), t.primary)
        assertTrue(t.more.isEmpty())
    }

    @Test fun `a conversation left half-way and a family question are the first ideas`() {
        val t = HomeLogic.today(dash(due = 3), listOf(clitic), emptySet(), listOf(kitchen), emptySet(), talking = "Pri tašči", family = listOf(question))
        assertEquals(HomeAction.Review(3), t.primary)
        assertEquals(listOf(HomeAction.Talk("Pri tašči"), HomeAction.Family(question)), t.more)
    }

    @Test fun `answered family questions are not offered`() {
        val answered = question.copy(answer = FamilyAnswer("Dobro."))
        val t = HomeLogic.today(dash(due = 3), emptyList(), emptySet(), emptyList(), emptySet(), family = listOf(answered))
        assertTrue(t.more.none { it is HomeAction.Family })
    }

    @Test fun `at most two ideas and never the primary again`() {
        val t = HomeLogic.today(dash(due = 1, weak = listOf("clitic_placement_se")), listOf(clitic, biti), setOf("biti"), listOf(kitchen), emptySet(), talking = "X", family = listOf(question))
        assertEquals(Today.MAX_MORE, t.more.size)
        assertTrue(t.more.none { it.key == t.primary.key })
    }

    @Test fun `minutes against the goal`() {
        assertEquals(0.5f, HomeLogic.today(dash(minutes = 30, goal = 60), emptyList(), emptySet(), emptyList(), emptySet()).progress)
        assertEquals(1f, HomeLogic.today(dash(minutes = 86, goal = 60), emptyList(), emptySet(), emptyList(), emptySet()).progress)
        assertEquals(0f, HomeLogic.today(dash(minutes = 20, goal = 0), emptyList(), emptySet(), emptyList(), emptySet()).progress)
        val t = HomeLogic.today(dash(practised = true), emptyList(), emptySet(), emptyList(), emptySet())
        assertTrue(t.practised)
        assertEquals(2, t.streak)
    }

    // --- villagers who need Jan ------------------------------------------------------------------

    private fun quest(id: String, giver: String, emoji: String = "🧑", source: QuestSource = QuestSource.LOCAL, module: String? = null, done: Boolean = false) =
        Quest(id, giver, emoji, "Prošnja $id", "…", Res.FOOD, emptyMap(), source = source, moduleId = module, done = done)

    private val kitchenScene = SceneSpec(
        "kuhinja", "Kuhinja", art = "kitchen", from = listOf("hut"),
        people = listOf(ScenePerson("babica", "Babica Micka", "👵", "grandma", "stove"), ScenePerson("ancka", "Teta Ančka", "📻", "aunt", "table", villager = "ancka")),
        happenings = listOf(Happening("potica", "Babica peče potico · Grandma bakes potica", "babica"), Happening("radio", "Radio", "ancka")),
    )

    private fun on(h: String) = kitchenScene.happenings.first { it.id == h }.let { ActiveHappening(kitchenScene, it, kitchenScene.people.first { p -> p.id == it.who }) }

    @Test fun `one card per person, their own requests first, then what is on now`() {
        val s = GameState(quests = listOf(
            quest("t1", "Učiteljica Mojca", source = QuestSource.TUTOR, module = "biti"),
            quest("l1", "Kovač Tone", "⚒️"),
            quest("l2", "Kovač Tone", "⚒️"),
            quest("done", "Pastir Luka", done = true),
        ))
        val needs = HomeLogic.needs(s, listOf(on("potica")), people = emptyList(), scenes = listOf(kitchenScene))
        assertEquals(listOf("Kovač Tone", "Učiteljica Mojca", "Babica Micka"), needs.map { it.name })
        assertEquals(1, needs[0].more)
        assertEquals(NeedTarget.Quest("l1"), needs[0].target)
        assertFalse(needs[0].now)
        val micka = needs[2]
        assertTrue(micka.now)
        assertEquals("grandma", micka.art) // from the scene, while the cast isn't loaded
        assertEquals(NeedTarget.Scene("kuhinja", "kuhinja/potica"), micka.target)
        assertEquals(null, micka.villagerId)
    }

    @Test fun `a request and a happening of the same person make one card`() {
        val s = GameState(quests = listOf(quest("l1", "Babica Micka", "👵")))
        val needs = HomeLogic.needs(s, listOf(on("potica")), emptyList(), listOf(kitchenScene))
        assertEquals(1, needs.size)
        assertEquals(1, needs[0].more)
        assertEquals("grandma", needs[0].art) // a quest giver's portrait comes from a scene they're in
    }

    @Test fun `the village's people give ids and portraits, by name or by the scene's villager id`() {
        val cast = listOf(
            Villager("tone", "Kovač Tone", "⚒️", art = "smith"),
            Villager("ancka", "Teta Ančka", "📻", art = "aunt2"),
        )
        val s = GameState(quests = listOf(quest("l1", "Kovač Tone")))
        val needs = HomeLogic.needs(s, listOf(on("radio")), cast, listOf(kitchenScene))
        assertEquals(listOf("tone", "ancka"), needs.map { it.villagerId })
        assertEquals(listOf("smith", "aunt2"), needs.map { it.art })
        assertEquals("⚒️", needs[0].emoji)
    }

    @Test fun `tutor quests for a module Home offers already are left out, and nobody without a portrait gets a fake one`() {
        val s = GameState(quests = listOf(quest("t1", "Učiteljica Mojca", "👩‍🏫", QuestSource.TUTOR, module = "dvojina"), quest("t2", "Neznanec", "🧙")))
        val needs = HomeLogic.needs(s, emptyList(), emptyList(), listOf(kitchenScene), offered = setOf("dvojina"))
        assertEquals(listOf("Neznanec"), needs.map { it.name })
        assertEquals(null, needs[0].art)
    }

    @Test fun `no village means nobody, and at most a strip full`() {
        assertTrue(HomeLogic.needs(null, emptyList(), emptyList(), emptyList()).isEmpty())
        val s = GameState(quests = List(9) { quest("q$it", "Person $it") })
        assertEquals(HomeLogic.MAX_NEEDS, HomeLogic.needs(s, emptyList(), emptyList(), emptyList()).size)
    }

    // --- header, tiles, road, plan ---------------------------------------------------------------

    @Test fun `the header names an event first, then a dying fire, else the age`() {
        val e = GameEvent("e", EventKind.WOLVES, 1, 0, 1000)
        assertEquals(VillageHint.Event(e), HomeLogic.villageHint(GameState(event = e, fire = 5)))
        assertEquals(VillageHint.FireLow, HomeLogic.villageHint(GameState(fire = 20)))
        assertEquals(VillageHint.Calm(Age.TABOR), HomeLogic.villageHint(GameState(age = Age.TABOR)))
        // Someone waiting for room nudges to build; waiting for a building (the school) doesn't.
        val room = Arrival(Arrival.Kind.BIRTH, "Furlan", "🍼", "prostor · room")
        val school = Arrival(Arrival.Kind.CAST, "Učiteljica Mojca", "👩‍🏫", "Šola · School")
        assertEquals(VillageHint.NeedsRoom(room), HomeLogic.villageHint(GameState(), listOf(school, room)))
        assertEquals(VillageHint.Calm(Age.OGENJ), HomeLogic.villageHint(GameState(), listOf(school)))
        assertEquals(VillageHint.FireLow, HomeLogic.villageHint(GameState(fire = 20), listOf(room)))
        assertTrue(HomeLogic.roomText(room).startsWith("🍼 Družina Furlan pričakuje otroka"))
        assertTrue(HomeLogic.threat(EventKind.STORM))
        assertFalse(HomeLogic.threat(EventKind.FESTIVAL))
    }

    @Test fun `word tiles count new packs, else the words left`() {
        assertEquals(TileStatus("2 nova paketa · 2 new", attention = true), HomeLogic.words(listOf(kitchen, numbers), setOf("v-kuhinji", "stevila")))
        assertEquals(TileStatus("še 45 besed · 45 to learn"), HomeLogic.words(listOf(kitchen, numbers), emptySet()))
        assertEquals(TileStatus("še 1 beseda · 1 to learn"), HomeLogic.words(listOf(kitchen.copy(learned = 23)), emptySet()))
        assertEquals("✅ Vse znaš · All learned", HomeLogic.words(listOf(kitchen.copy(learned = 24)), emptySet()).text)
    }

    @Test fun `challenge, talk and family tiles`() {
        assertEquals(TileStatus("3 novi · 3 new", attention = true), HomeLogic.challenges(listOf(dvojina, clitic, biti), setOf("dvojina", "clitic", "biti")))
        assertEquals(TileStatus("2 izziva · 2 to play"), HomeLogic.challenges(listOf(dvojina, clitic), emptySet()))
        assertEquals("Še ni izzivov · None yet", HomeLogic.challenges(emptyList(), emptySet()).text)

        assertTrue(HomeLogic.talk("Pri tašči").attention)
        assertFalse(HomeLogic.talk(null).attention)

        assertEquals(TileStatus("1 vprašanje čaka · 1 waiting", attention = true), HomeLogic.family(listOf(question)))
        val answered = question.copy(answer = FamilyAnswer("Dobro."))
        assertEquals("⏳ Učitelj pregleduje · Tutor is reviewing", HomeLogic.family(listOf(answered)).text)
        assertEquals("🧑‍🏫 Povratna informacija · Feedback", HomeLogic.family(listOf(answered.copy(feedback = FamilyFeedback("Lepo!")))).text)
        assertEquals("Posnemite besede · Record words", HomeLogic.family(emptyList()).text)
    }

    @Test fun `the road runs from the level now to the target`() {
        val r = HomeLogic.road(dash(words = 150))
        assertEquals(listOf("A1", "A2", "B1", "B2"), r.steps)
        assertEquals("A2", r.next)
        assertEquals(300, r.goal)
        assertEquals(0.5f, r.progress)
        assertEquals(listOf("B2", "C1"), HomeLogic.road(dash(level = "B2")).steps) // at the target: the next step still shows
    }

    @Test fun `the plan folds to its first bullet`() {
        val plan = "**Dobro jutro, Jan!** ☀️\n\n- Ponovi *12 kartic* (5 min)\n- Dvojina z Mojco\n"
        assertEquals("Ponovi 12 kartic (5 min)", HomeLogic.planPreview(plan))
        assertEquals("Danes samo pogovor.", HomeLogic.planPreview("Danes samo pogovor."))
        assertEquals("Prvo", HomeLogic.planPreview("1. Prvo\n2. Drugo"))
    }
}
