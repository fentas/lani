package si.lanisce.lani.game.scene

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Teaser
import si.lanisce.lani.game.Tomorrow
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.scene.DialogRun
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The storyteller's evening story (companion/SCENES.md, "Stories"): the culture packs' stories as the bridge serves them
 * with the campfire, read in the learner's pair; tonight's pick (the learner's level, the next one not heard, a legend's
 * chapters in order, the rotation, the retellings a level up, a new story first); the happening; "Jutri".
 */
class StoriesTest {
    private val companion = StoryFixtures.companion
    private val pair = L10n.pair
    private val day = LocalDate.of(2026, 9, 24)
    private val evening = LocalDateTime.of(2026, 9, 24, 19, 30)
    private val village = GameState(seed = 7, age = Age.TABOR)

    @After fun restore() {
        L10n.pair = pair
    }

    private fun obj(f: File) = StoryFixtures.obj(f)

    /** The campfire of [culture]'s village with its storyteller's stories, read by a learner of [p]. */
    private fun campfire(culture: String = "primorska", p: LangPair = LangPair(Lang.SL, Lang.EN)): SceneSpec = StoryFixtures.campfire(culture, p)

    private val fire by lazy { campfire() }
    private val janez by lazy { fire.people.first { it.id == "janez" } }
    private val stories by lazy { Stories.of(fire, janez) }
    private val order = listOf(
        "zlatorog", "kralj-matjaz", "martin-krpan", "peter-klepec", "lepa-vida", "povodni-moz", "zvon-zelja", "desetnica", "ajdovska-deklica",
        "zmaj-ljubljanski", "mojca-pokrajculja", "bogatinovo-zlato", "pegam-in-lambergar", "kekec", "veronika-deseniska", "jurij-kozjak",
    )

    @Test fun `the campfire comes with Janez's stories, in their order, read in Jan's pair`() {
        assertEquals(order, stories.map { it.id })
        assertTrue(stories.all { "A1" in it.levelsTold && "A2" in it.levelsTold })
        assertEquals(
            listOf("zlatorog", "kralj-matjaz", "lepa-vida", "zmaj-ljubljanski", "bogatinovo-zlato", "pegam-in-lambergar", "veronika-deseniska"),
            stories.filter { "B1" in it.levelsTold }.map { it.id },
        )
        // the gold of Bogatin goes on from Zlatorog, and joins its book
        assertEquals("zlatorog", stories.first { it.id == "bogatinovo-zlato" }.continues)
        val krpan = stories.first { it.id == "martin-krpan" }
        assertEquals(4, krpan.parts.size)
        assertEquals("Janez pove, kako je velikan Brdavs prišel na Dunaj", krpan.parts[1].teaser?.sl)
        assertEquals("Janez tells how the giant Brdavs came to Vienna", krpan.parts[1].teaser?.en)
        val z = stories.first()
        assertEquals("Zlatorog · Goldhorn", z.title)
        assertEquals("zgodbo o Zlatorogu, ki sem ti jo povedal ob ognju", z.memory?.sl)
        assertEquals("gams" to "chamois", z.words.first { it.id == "gams" }.let { it.sl to it.en })
        // the campfire's own happening tells them; the scene has no dialog for it
        val h = fire.happenings.single { it.who == "janez" }
        assertTrue(h.stories && h.dialog == null)
    }

    @Test fun `tonight's story is told at the learner's level`() {
        assertEquals("A1", Stories.tonight(stories, emptyMap(), "A1")!!.level)
        assertEquals("A2", Stories.tonight(stories, emptyMap(), "A2")!!.level)
        // higher: the highest there is (Zlatorog has B1, Peter Klepec only A2)
        assertEquals("B1", Stories.tonight(stories, emptyMap(), "B2")!!.level)
        val klepec = stories.first { it.id == "peter-klepec" }
        assertEquals("A2", Stories.levelFor(klepec, "C1"))
        // a story the tutor wrote at B1 only, for an A1 learner: its easiest, all it has
        val b1 = klepec.copy(levels = mapOf("B1" to klepec.levels.getValue("A2")))
        assertEquals("B1", Stories.levelFor(b1, "A1"))
        assertEquals(listOf("B1"), b1.levelsTold)
    }

    @Test fun `every evening the next one not heard, a legend's chapters in order, then the retellings a level up`() {
        var heard = emptyMap<String, StoryHeard>()
        val told = ArrayList<String>()
        repeat(22) { i ->
            val t = Stories.tonight(stories, heard, "A1")!!
            assertFalse(t.retold)
            assertEquals("A1", t.level)
            told += if (t.chapters > 1) "${t.story.id}#${t.chapter + 1}" else t.story.id
            heard = Stories.heard(heard, t, day.plusDays(i.toLong()))
        }
        assertEquals(
            listOf(
                "zlatorog", "kralj-matjaz", "martin-krpan#1", "martin-krpan#2", "martin-krpan#3", "martin-krpan#4", "peter-klepec", "lepa-vida", "povodni-moz", "zvon-zelja", "desetnica", "ajdovska-deklica",
                "zmaj-ljubljanski", "mojca-pokrajculja", "pegam-in-lambergar", "kekec", "veronika-deseniska#1", "veronika-deseniska#2", "jurij-kozjak#1", "jurij-kozjak#2", "jurij-kozjak#3",
                // the continuation of Zlatorog after the other new ones
                "bogatinovo-zlato",
            ),
            told,
        )
        assertTrue(stories.all { heard[it.id]?.told == 1 && heard[it.id]?.chapter == 0 && heard[it.id]?.level == "A1" })
        assertEquals("2026-10-05", heard["ajdovska-deklica"]?.on)
        assertEquals("2026-10-15", heard["bogatinovo-zlato"]?.on)
        // all heard: from the first again, a level up (A1 → A2; A2 → B1 where there is one, else the story's highest)
        val again = Stories.tonight(stories, heard, "A1")!!
        assertEquals("zlatorog", again.story.id)
        assertTrue(again.retold)
        assertEquals("A2", again.level)
        assertEquals("B1", Stories.tonight(stories, heard, "A2")!!.level)
        val klepecAgain = Stories.tonight(stories, stories.filter { it.id != "peter-klepec" }.associate { it.id to StoryHeard(told = 2) } + ("peter-klepec" to StoryHeard(told = 1)), "A2")!!
        assertEquals("peter-klepec" to "A2", klepecAgain.story.id to klepecAgain.level)
    }

    @Test fun `a legend under way goes on before anything else`() {
        val t = Stories.tonight(stories, mapOf("martin-krpan" to StoryHeard(chapter = 2)), "A2")!!
        assertEquals("martin-krpan", t.story.id)
        assertEquals(2, t.chapter)
        assertFalse(t.last)
        assertEquals("Martin Krpan (3/4)", t.title)
        val last = Stories.heard(mapOf("martin-krpan" to StoryHeard(chapter = 3, level = "A2", levels = mapOf("A2" to 3))), t.copy(chapter = 3), day)
        assertEquals(StoryHeard(told = 1, chapter = 0, level = "A2", on = "2026-09-24", levels = mapOf("A2" to 4), days = mapOf(4 to "2026-09-24")), last["martin-krpan"])
    }

    @Test fun `a continuation comes after the story it goes on from, the new ones first, then the continuations, then the retellings`() {
        val sequel = stories.first { it.id == "povodni-moz" }.copy(id = "krpan-se-vrne", continues = "martin-krpan", source = "tutor")
        val all = stories + sequel
        // it waits for Krpan, wherever it stands in the rotation, and even when nothing else is left
        assertEquals("zlatorog", Stories.tonight(listOf(sequel) + stories, emptyMap(), "A1")!!.story.id)
        val rest = stories.filter { it.id != "martin-krpan" }.associate { it.id to StoryHeard(told = 1) }
        assertEquals("martin-krpan", Stories.tonight(all, rest, "A1")!!.story.id)
        // Krpan heard: the other new stories first, then the sequel, then the retellings
        var heard = mapOf("martin-krpan" to StoryHeard(told = 1, level = "A1"))
        val told = ArrayList<String>()
        repeat(20) { i ->
            val t = Stories.tonight(all, heard, "A1")!!
            told += t.story.id
            heard = Stories.heard(heard, t, day.plusDays(i.toLong()))
        }
        assertEquals(
            listOf(
                "zlatorog", "kralj-matjaz", "peter-klepec", "lepa-vida", "povodni-moz", "zvon-zelja", "desetnica", "ajdovska-deklica", "zmaj-ljubljanski", "mojca-pokrajculja",
                "pegam-in-lambergar", "kekec", "veronika-deseniska", "veronika-deseniska", "jurij-kozjak", "jurij-kozjak", "jurij-kozjak", "bogatinovo-zlato", "krpan-se-vrne", "zlatorog",
            ),
            told,
        )
        // retold: the sequel again only after Krpan was retold
        val round = all.associate { it.id to StoryHeard(told = 1) }
        val order = ArrayList<String>()
        var h = round
        repeat(24) { i ->
            val t = Stories.tonight(all, h, "A1")!!
            order += t.story.id
            h = Stories.heard(h, t, day.plusDays(i.toLong()))
        }
        assertTrue("$order", order.indexOf("krpan-se-vrne") > order.lastIndexOf("martin-krpan"))
        assertTrue(order.indexOf("bogatinovo-zlato") > order.indexOf("zlatorog"))
        // a story that goes on from one not told here (removed) is a story of its own; one going on from itself too
        val orphan = sequel.copy(continues = "gone")
        assertEquals("krpan-se-vrne", Stories.tonight(stories + orphan, stories.associate { it.id to StoryHeard(told = 1) }, "A1")!!.story.id)
        val self = sequel.copy(continues = "krpan-se-vrne")
        assertEquals("krpan-se-vrne", Stories.tonight(listOf(self), emptyMap(), "A1")!!.story.id)
    }

    @Test fun `the levels a story was heard at are kept, also read from a village from before the books`() {
        val krpan = stories.first { it.id == "martin-krpan" }
        val zlatorog = stories.first()
        assertEquals(mapOf("A1" to 1), Stories.levelsHeard(zlatorog, StoryHeard(told = 1, level = "A1")))
        assertEquals(mapOf("A1" to 2), Stories.levelsHeard(krpan, StoryHeard(chapter = 2, level = "A1")))
        // retold a level up, under way: the level below whole
        assertEquals(mapOf("A1" to 4, "A2" to 1), Stories.levelsHeard(krpan, StoryHeard(told = 1, chapter = 1, level = "A2")))
        assertEquals(emptyMap<String, Int>(), Stories.levelsHeard(krpan, StoryHeard()))
        // heard evening by evening: at A1 whole, then a level up
        var heard = emptyMap<String, StoryHeard>()
        repeat(4) { c -> heard = Stories.heard(heard, StoryTonight(krpan, c, "A1", retold = false), day) }
        heard = Stories.heard(heard, StoryTonight(krpan, 0, "A2", retold = true), day)
        assertEquals(mapOf("A1" to 4, "A2" to 1), heard.getValue("martin-krpan").levels)
        assertEquals(1 to 1, heard.getValue("martin-krpan").let { it.told to it.chapter })
    }

    @Test fun `a new story joins the rotation before the retellings`() {
        val tutor = stories.first { it.id == "povodni-moz" }.copy(id = "tutor-zmaj", source = "tutor")
        val heard = stories.associate { it.id to StoryHeard(told = 1) }
        val t = Stories.tonight(stories + tutor, heard, "A2")!!
        assertEquals("tutor-zmaj", t.story.id)
        assertFalse(t.retold)
        assertEquals("zlatorog", Stories.after(stories + tutor, heard, "A2", t, day)!!.story.id)
    }

    @Test fun `every telling plays to its end with the right choices, told by the teller, with the fire's effects`() {
        for (s in stories) for (ci in s.parts.indices) for (lv in s.levelsTold) {
            val d = StoryTonight(s, ci, lv, retold = false).dialog()
            var run = DialogRun.start(d, "janez")
            var turns = 0
            while (run.step != DialogRun.Step.END) {
                run = if (run.step == DialogRun.Step.CHOOSE) run.choose(run.choices.indexOfFirst { it.ok }).also { turns++ } else run.next()
            }
            assertEquals("${s.id}/$ci/$lv: the teller first", "janez", run.said.first().who)
            assertTrue("${s.id}/$ci/$lv: a turn for the learner", turns >= 1 && run.mistakes == 0)
            val fx = d.lines.flatMap { l -> l.fx.keys + l.choices.flatMap { it.reply?.fx.orEmpty().keys } }
            assertTrue("${s.id}/$ci/$lv: effects $fx", fx.all { it in SceneArt.effects.getValue("campfire") })
            assertTrue("${s.id}/$ci/$lv: every wrong choice says why", d.lines.all { l -> l.choices.all { it.ok || !it.why.isNullOrBlank() } })
        }
    }

    @Test fun `every village's storyteller tells ten stories at least, and every telling plays through each wrong choice to its end with the teller's reaction`() {
        val villages = listOf(
            Triple("primorska", "janez", LangPair(Lang.SL, Lang.EN)), Triple("friuli", "bepi", LangPair(Lang.IT, Lang.SL)),
            Triple("kaernten", "sepp", LangPair(Lang.DE, Lang.SL)), Triple("lakeland", "wilf", LangPair(Lang.EN, Lang.DE)),
        )
        var tellings = 0
        for ((culture, villager, p) in villages) {
            val fire = campfire(culture, p)
            val teller = fire.people.first { it.villager == villager }
            val his = Stories.of(fire, teller)
            assertTrue("$culture: ${his.size} stories", his.size >= 10)
            assertTrue("$culture: each told at A1 and A2", his.all { "A1" in it.levelsTold && "A2" in it.levelsTold })
            for (s in his) for (ci in s.parts.indices) for (lv in s.levelsTold) {
                val at = "${s.id}/$ci/$lv"
                var run = DialogRun.start(StoryTonight(s, ci, lv, retold = false).dialog(), teller.id)
                var wrong = 0
                while (run.step != DialogRun.Step.END) {
                    if (run.step != DialogRun.Step.CHOOSE) { run = run.next(); continue }
                    val next = run.choices.indices.firstOrNull { !run.choices[it].ok && it !in run.tried }
                    if (next == null) { run = run.choose(run.choices.indexOfFirst { it.ok }); continue }
                    run = run.choose(next)
                    wrong++
                    val r = run.reaction
                    assertTrue("$at: the teller reacts to a wrong choice, never with the weather", r != null && r.sl.isNotBlank() && r.sky == null)
                    assertEquals("$at: the reaction is the teller's", teller.id, run.said.last().who)
                }
                assertEquals("$at: every wrong choice counted once", wrong, run.mistakes)
                tellings++
            }
        }
        assertTrue("$tellings tellings", tellings > 100)
    }

    @Test fun `Janez's evening happening is on every evening while there is a story, first of all`() {
        val on = Happenings.active(listOf(fire), village, evening)
        assertEquals("ob-ognju/zgodba", on.first().key)
        assertTrue((0L until 20L).all { d -> Happenings.active(listOf(fire), village, evening.plusDays(d)).any { it.happening.stories } })
        assertTrue(Happenings.active(listOf(fire), village, evening.withHour(10)).none { it.happening.stories })
        assertTrue(Happenings.active(listOf(fire.copy(stories = emptyList())), village, evening).none { it.happening.stories })
        // someone of the cast is only there if they live here: Janez too
        val withoutJanez = village.copy(residents = listOf(Resident("micka", "2026-01-01")), villagers = 1)
        assertTrue(Happenings.active(listOf(fire), withoutJanez, evening).none { it.happening.stories })
    }

    @Test fun `Jutri teases tonight's story, then tomorrow's`() {
        val lv = { _: String -> "A2" }
        val tonight = Stories.teaser(listOf(fire), village, day, lv)!!
        assertEquals(Teaser.Kind.STORY, tonight.kind)
        assertEquals("🐐 Nocoj ob ognju: Janez pove zgodbo o Zlatorogu · Tonight by the fire: Janez tells the story of Zlatorog", tonight.line)
        // heard tonight: tomorrow's
        val t = Stories.tonight(stories, village.stories, "A2")!!
        val heard = Stories.record(village, t, day).copy(happeningsDone = mapOf("ob-ognju/zgodba" to day.toString()))
        assertEquals("👑 Jutri: Janez pove zgodbo o kralju Matjažu · Tomorrow: Janez tells the story of King Matjaž", Stories.teaser(listOf(fire), heard, day, lv)!!.line)
        // a legend under way: its next chapter
        val krpan = village.copy(stories = mapOf("zlatorog" to StoryHeard(told = 1), "kralj-matjaz" to StoryHeard(told = 1), "martin-krpan" to StoryHeard(chapter = 1)), happeningsDone = mapOf("ob-ognju/zgodba" to day.toString()))
        assertEquals("🧂 Jutri: Janez pove, kako je velikan Brdavs prišel na Dunaj · Tomorrow: Janez tells how the giant Brdavs came to Vienna", Stories.teaser(listOf(fire), krpan, day, lv)!!.line)
        // all heard: once more
        val all = village.copy(stories = stories.associate { it.id to StoryHeard(told = 1) })
        assertEquals("🐐 Nocoj ob ognju še enkrat: Janez pove zgodbo o Zlatorogu · Tonight by the fire, once more: Janez tells the story of Zlatorog", Stories.teaser(listOf(fire), all, day, lv)!!.line)
        // nobody tells stories: nothing; in the list, after the project step and before the pedlar
        assertNull(Stories.teaser(listOf(fire.copy(stories = emptyList())), village, day, lv))
        val kinds = Tomorrow.teasers(village, day, tonight).map { it.kind }
        assertTrue(Teaser.Kind.STORY in kinds && kinds.indexOf(Teaser.Kind.STORY) < kinds.indexOf(Teaser.Kind.SURPRISE))
        assertEquals(Tomorrow.teasers(village, day).size + 1, kinds.size)
    }

    @Test fun `what was heard is kept with the village`() {
        val s = Stories.record(village, Stories.tonight(stories, emptyMap(), "A1")!!, day)
        val back = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), s))
        assertEquals(StoryHeard(told = 1, level = "A1", on = "2026-09-24", levels = mapOf("A1" to 1), days = mapOf(1 to "2026-09-24")), back.stories["zlatorog"])
        // an older save without stories reads as nothing heard
        assertTrue(json.decodeFromString(GameState.serializer(), """{"seed": 7}""").stories.isEmpty())
    }

    @Test fun `the second learner hears Nonno Bepi's in Italian, meant in Slovene`() {
        val second = LangPair(Lang.IT, Lang.SL)
        val fuoco = campfire("friuli", second)
        val bepi = fuoco.people.first { it.villager == "bepi" }
        val his = Stories.of(fuoco, bepi)
        assertEquals(10, his.size)
        assertTrue(his.all { it.language == "it" && "A1" in it.levelsTold && "A2" in it.levelsTold })
        val t = Stories.tonight(his, emptyMap(), "A1")!!
        val first = t.telling.lines.first()
        assertTrue(first.sl!!.isNotBlank() && first.en!!.isNotBlank() && first.sl != first.en)
        assertTrue(t.story.words.all { it.sl.isNotBlank() && it.en.isNotBlank() })
        val teaser = Stories.teaser(listOf(fuoco), village, day) { "A1" }
        assertNotNull(teaser)
        assertTrue(teaser!!.text, teaser.text.startsWith("Stasera vicino al fuoco: Nonno Bepi") && " · Nocoj ob ognju: " in teaser.text)
    }

    @Test fun `a learner of English hears Old Wilf's in English, meant in their base`() {
        val fromDe = LangPair(Lang.EN, Lang.DE)
        val fire = campfire("lakeland", fromDe)
        val wilf = fire.people.first { it.villager == "wilf" }
        val his = Stories.of(fire, wilf)
        assertEquals(
            listOf(
                "the-borrowdale-cuckoo", "the-bowder-stone", "the-lady-of-the-lake", "the-giants-grave", "the-hob",
                "the-crier-of-claife", "long-meg-and-her-daughters", "the-luck-of-edenhall", "king-dunmails-crown", "tom-fool-of-muncaster",
            ),
            his.map { it.id },
        )
        assertTrue(his.all { it.language == "en" && "A1" in it.levelsTold && "A2" in it.levelsTold })
        val file = obj(File(companion, "cultures/lakeland/stories/the-borrowdale-cuckoo.json"))
        val line = file["levels"]!!.jsonObject["A1"]!!.jsonObject["lines"]!!.jsonArray.first().jsonObject
        val t = Stories.tonight(his, emptyMap(), "A1")!!
        assertEquals("the-borrowdale-cuckoo", t.story.id)
        // said in English, meant in German, never the English twice
        val first = t.telling.lines.first()
        assertEquals(line["en"]!!.jsonPrimitive.content to line["de"]!!.jsonPrimitive.content, first.sl to first.en)
        assertTrue(t.story.words.all { it.sl.isNotBlank() && it.en.isNotBlank() && it.sl != it.en })
        assertTrue(Stories.teaser(listOf(fire), village, day) { "A1" }!!.text.contains(file["teaser"]!!.jsonObject["en"]!!.jsonPrimitive.content))
    }
}
