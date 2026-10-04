package si.lanisce.lani.ui.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Module
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackGiver
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.QuestMeta
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.game.Challenge
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.VillagerLines

class IntrosTest {
    private val luka = StagePerson(
        id = "luka", name = "Pastir Luka", emoji = "🐑", art = "shepherd", voice = Clips.MALE, role = "Pastir · Shepherd", register = "ti",
        lines = VillagerLines(greet = listOf(VillagerLine("Dober dan!", "Good day!"), VillagerLine("Ej, Jan! Kako si?", "Hey, Jan! How are you?", 1))),
        level = 1,
    )
    private val stranger = Cast.standIn("Teta Ančka", "📻")

    private val cloze = Exercise.Cloze("Jaz ___ Jan.", listOf("sem"))
    private val choice = Exercise.Choice("Kaj pomeni 'hvala'?", listOf("thanks", "hello"), 0)
    private val listen = Exercise.Choice("", listOf("a", "b"), 0, audio = "Dober dan")

    private fun challenge(title: String, intro: String, emoji: String, n: Int = 6, skill: Res = Res.WOOD, time: Int? = null, mark: Int = 0) = Challenge(
        title = title, intro = intro, emoji = emoji,
        exercises = List(n) { if (it % 2 == 0) listen else choice },
        skills = List(n) { skill }, cardIds = List(n) { null }, timeLimitSeconds = time, passMark = mark,
    )

    private val quest = Quest(
        id = "q-1", giver = "Pastir Luka", emoji = "🐑", title = "Izgubljena ovca · The lost sheep",
        story = "Ena ovca je zbežala v Trnovski gozd. Luka jo kliče, poslušaj z njim. · One sheep has run off into the Trnovo forest. Luka is calling it, listen with him.",
        skill = Res.WOOD, reward = mapOf(Res.WOOD to 39, Res.STONE to 13),
    )

    @Test fun `a quest intro has the giver greeting and asking, the story, the practice and the reward`() {
        val c = challenge("🐑 ${quest.title}", "${quest.story}\n4/6 ✔ · 39 🪵, 13 🪨", "🐑", mark = 4)
        val info = Intros.quest(quest, c, luka)
        assertEquals(IntroKind.QUEST, info.kind)
        assertEquals("Ej, Jan! Kako si? Mi pomagaš?", info.request.sl)
        assertEquals("Hey, Jan! How are you? Will you help me?", info.request.en)
        assertEquals(1, info.story.size) // the goal line isn't narration, the story isn't repeated
        assertEquals("Ena ovca je zbežala v Trnovski gozd. Luka jo kliče, poslušaj z njim.", info.story[0].sl)
        assertTrue(info.story[0].en.startsWith("One sheep"))
        assertEquals(listOf(Res.WOOD), info.practice.skills)
        assertEquals(6, info.practice.count)
        assertEquals(4, info.practice.passMark)
        assertEquals(listOf("poslušanje · listening" to 3, "izbira · choice" to 3), info.practice.kinds)
        assertEquals(2, info.practice.minutes) // 3 × 20 s + 3 × 15 s
        assertEquals(quest.reward, info.pays.reward)
        assertEquals(mapOf(Res.WOOD to 36), info.pays.answers)
        assertEquals(Bonds.QUEST, info.pays.bond)
        assertEquals(StagePlace.FOREST, info.place)
    }

    @Test fun `a stranger's quest pays no friendship`() {
        val c = challenge("📻 x", "story", "📻")
        assertEquals(0, Intros.quest(quest.copy(giver = "Teta Ančka"), c, stranger).pays.bond)
    }

    @Test fun `an event intro has an urgent ask without a greeting, the clock, the stakes and the warning`() {
        val tone = luka.copy(id = "tone", name = "Kovač Tone", art = "smith")
        val c = challenge(
            "🐺 Volkovi pred palisado! · Wolves at the palisade!",
            "Slovnica jih prežene. · Grammar drives them off.\n5/8 ✔ · 82 s", "🐺", n = 8, skill = Res.STONE, time = 82, mark = 5,
        )
        val info = Intros.event(EventKind.WOLVES, c, tone, bond = 1)
        assertEquals("Volkovi so pri vasi! Pomagaj mi!", info.request.sl)
        assertEquals("Volkovi pred palisado! · Wolves at the palisade!", info.title)
        assertEquals(listOf(Said("Slovnica jih prežene.", "Grammar drives them off.")), info.story)
        assertEquals(82, info.practice.timeLimit)
        assertEquals(2, info.practice.minutes)
        assertEquals(5, info.practice.passMark)
        assertNotNull(info.pays.stake)
        assertTrue(info.warning!!.startsWith(Intros.LEAVE_WARNING))
        assertNotNull(info.note)
        assertEquals(StagePlace.NIGHT, info.place)
        assertTrue(info.start.startsWith("⚔️"))
    }

    @Test fun `a gathering intro keeps the fallback note`() {
        val c = challenge(
            "🪵 V gozd · Into the forest",
            "Poslušanje prinese les. · Listening brings in wood.\nŠe ni vaj za poslušanje, zato bereš. · No listening drills yet, so you read.",
            "🪵", n = 7,
        )
        val info = Intros.gather(Res.WOOD, c, luka, bond = 1)
        assertEquals("V gozd · Into the forest", info.title)
        assertEquals("Ej, Jan! Kako si? Greva v gozd po les?", info.request.sl)
        assertEquals(2, info.story.size)
        assertEquals(1, info.pays.bond)
        assertEquals(mapOf(Res.WOOD to 42), info.pays.answers)
        assertEquals(StagePlace.FOREST, info.place)
    }

    @Test fun `a module intro pays its open quest, a done one only the answers`() {
        val m = Module("dvojina", 2, "Dvojina · The dual", level = "A2", exercises = listOf(cloze, cloze, choice))
        val q = Quest(
            "tutor-dvojina-v2", "Učiteljica Mojca", "👩‍🏫", m.title, "Midva greva. · The two of us go.", Res.STONE,
            mapOf(Res.STONE to 60), QuestSource.TUTOR, moduleId = m.id,
        )
        val mojca = Cast.standIn("Učiteljica Mojca", "👩‍🏫").copy(id = "mojca", register = "vi")
        val open = Intros.module(m, q, null, mojca, bond = 1)
        assertEquals("Mi pomagate?", open.request.sl)
        assertEquals(mapOf(Res.STONE to 60), open.pays.reward)
        assertEquals(Bonds.QUEST, open.pays.bond)
        assertEquals(Said("Midva greva.", "The two of us go."), open.story.single())
        assertEquals(StagePlace.SCHOOL, open.place)
        val done = Intros.module(m, q.copy(done = true), null, mojca, bond = 1)
        assertTrue(done.pays.reward.isEmpty())
        assertEquals(1, done.pays.bond)
        // Without a quest: today's companion invites, the description is the story (English).
        val plain = Intros.module(m.copy(description = "Practise the dual."), null, null, luka, bond = 0)
        assertEquals("Ej, Jan! Kako si? Gremo vadit!", plain.request.sl)
        assertEquals(Said("", "Practise the dual."), plain.story.single())
        assertEquals("📘", plain.emoji)
        // A quest block the village hasn't made a quest of yet still names the giver and the story.
        val meta = Intros.module(m, null, QuestMeta("Učiteljica Mojca", "👩‍🏫", "Midva greva. · The two of us go."), mojca, bond = 1)
        assertEquals("👩‍🏫", meta.emoji)
        assertEquals("Midva greva.", meta.story.single().sl)
    }

    @Test fun `a word pack intro counts the words to meet and their time`() {
        val words = List(6) { PackWord("w$it", "beseda$it", "word$it") }
        val p = Pack("v-gozdu", "V gozdu · In the forest", "🌲", description = "Luka's forest above Trnovo.", giver = PackGiver("Pastir Luka", "🐑"), words = words)
        val info = Intros.pack(p, words, List(12) { choice }, luka, bond = 1)
        assertEquals("Ej, Jan! Kako si? Naučim te nekaj novih besed.", info.request.sl)
        assertEquals(6, info.practice.words)
        assertEquals(12, info.practice.count)
        assertEquals(3 + 2, info.practice.minutes) // 12 × 15 s, then 6 × 20 s to meet the words
        assertEquals(Said("", "Luka's forest above Trnovo."), info.story.single())
        assertEquals("✨ 6 novih besed · 6 new words  ·  📝 12 vaj · 12 exercises  ·  ⏱ ~5 min", practiceLine(info.practice))
    }

    @Test fun `a role-play intro opens with the scenario's first line`() {
        val s = Scenario(
            "v-pekarni", "V pekarni · At the bakery", "🥖", setting = "A small bakery in Nova Gorica.",
            role = "Prodajalka Nina, a baker", voice = Clips.FEMALE, goals = listOf("Greet", "Buy bread"),
            openerSl = "Dober dan! Kaj bo danes? Imamo svež kruh.", openerEn = "Good day! What will it be today? We have fresh bread.",
        )
        val nina = Cast.talker(s, emptyList(), null)
        val info = Intros.talk(s, nina)
        assertEquals(Said("Dober dan! Kaj bo danes?", "Good day! What will it be today?"), info.request)
        assertEquals(listOf("Greet", "Buy bread"), info.practice.goals)
        assertEquals("🎯 2 cilja · 2 goals  ·  ⏱ ~10 min", practiceLine(info.practice))
        assertEquals(Res.WISDOM, info.pays.perLine)
        assertEquals(0, info.pays.bond)
        assertEquals(Said("", "A small bakery in Nova Gorica."), info.story.single())
        // With a villager, the talk grows the friendship.
        assertEquals(Bonds.TALK, Intros.talk(s.copy(id = "villager:luka"), luka).pays.bond)
    }

    @Test fun `requests stay short`() {
        val chatty = luka.copy(lines = VillagerLines(greet = listOf(VillagerLine("Ej, Jan, kako si kaj, že dolgo te nisem videl na planini!", "…"))))
        assertEquals("Mi pomagaš?", Intros.request(chatty, Run.Quest("Pastir Luka", "🐑")).sl)
        assertEquals(Said("Greva ponavljat?", "Shall we review, the two of us?"), Intros.request(stranger, Run.Review))
        val tone = luka.copy(register = "vi", lines = VillagerLines(greet = listOf(VillagerLine("Dan.", "Day. (short for dober dan)"))))
        assertEquals(Said("Dan. Mi pomagate?", "Day. Will you help me?"), Intros.request(tone, Run.Quest("Kovač Tone", "⚒️")))
    }

    @Test fun `bilingual texts split at the middle dot`() {
        assertEquals(Said("Dober dan.", "Good day."), Intros.split("Dober dan. · Good day."))
        assertEquals(Said("Samo slovensko."), Intros.split("Samo slovensko."))
        assertEquals(Said("", "Only English."), Intros.english("Only English."))
    }

    @Test fun `minutes add up by exercise kind`() {
        assertEquals(1, Intros.minutes(emptyList()))
        assertEquals(1, Intros.minutes(listOf(cloze, cloze)))
        assertEquals(2, Intros.minutes(listOf(Exercise.Free("p", "r"))))
        assertNull(Intros.kindLabel(Exercise.Unsupported("new")))
    }
}
