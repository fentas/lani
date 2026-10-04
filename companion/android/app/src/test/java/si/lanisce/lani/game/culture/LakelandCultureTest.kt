package si.lanisce.lani.game.culture

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.Chest
import si.lanisce.lani.game.ContentPool
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Surprise
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.stage.Cast
import si.lanisce.lani.ui.stage.Lines
import si.lanisce.lani.ui.villagers.GiftReaction
import si.lanisce.lani.ui.villagers.GiftTalk
import si.lanisce.lani.ui.villagers.VillagerLogic
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The English village (the lakeland culture pack: a dale in the Lake District, English with Slovene, Italian and
 * German) switches the whole game for a learner of English, from Slovene and from German: names, the chronicle,
 * requests and their thanks, goods, tools and the smith, the calendar with its English feasts on their days and their
 * word packs, the surprises, the projects, the events, the people who move in, the cast's lines (first names, and "Mr"
 * or "Mrs" for four) and the stage's own. English is what is said here, so it is never the translation too. Primorska
 * reads as it always did after (CultureParityTest holds every text).
 */
class LakelandCultureTest {
    private val day = LocalDate.of(2026, 9, 1)
    private val t0 = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val pair = L10n.pair
    private val fromSl = LangPair(Lang.EN, Lang.SL)
    private val fromDe = LangPair(Lang.EN, Lang.DE)

    private val adults = setOf(
        "Granny Maggie", "Shepherd Jack", "Old Wilf", "Mr Tyson", "Auntie Edna", "Beekeeper Arthur", "Mr Dixon",
        "Brewer Sam", "Mrs Birkett", "Mrs Hartley",
    )

    private val cards = listOf("bread" to "kruh", "apple" to "jabolko", "milk" to "mleko", "sheep" to "ovca", "dog" to "pes")
        .mapIndexed { i, (en, sl) -> ReviewCard("c$i", en, sl, category = if (i < 3) "food" else "animals") }

    private fun village(age: Age = Age.VAS, vararg types: BuildingType) = GameEngine.newGame(3, t0).copy(
        age = age, resources = Res.entries.associateWith { 2000 }, villagers = 4, morale = 60,
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i) }, lastTick = day.toString(), foundedOn = day.minusDays(20).toString(),
    )

    /** The lakeland cast as the bridge serves it (GET /villagers: the files of cultures/lakeland/villagers). */
    private val cast by lazy {
        val dir = listOf(File("../../cultures/lakeland/villagers"), File("../cultures/lakeland/villagers")).first { it.isDirectory }
        parseVillagers(dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.joinToString(",", "[", "]") { it.readText() })
    }

    @Before fun english() {
        Cultures.use("lakeland")
        L10n.pair = fromSl
    }

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    @Test fun `the pack is complete, in English, and marked as machine-written`() {
        val c = Cultures.current
        assertEquals("lakeland", c.id)
        assertEquals(Lang.EN, c.language)
        assertEquals("machine-written, not reviewed by a native speaker", c.manifest.review)
        val all = listOf(Lang.EN, Lang.SL, Lang.IT, Lang.DE)
        for (r in Res.entries) assertTrue(r.name, all.all { c.name(r).has(it) })
        for (a in Age.entries) assertTrue(a.name, all.all { c.name(a).has(it) })
        for (t in BuildingType.entries) assertTrue(t.name, all.all { c.name(t).has(it) })
        assertEquals(Catalog.projectFrames.map { it.id }, c.projects.map { it.id })
    }

    @Test fun `the names of things, and who is on the stage, are the village's`() {
        assertEquals("Village · Vas", "${Age.VAS.sl} · ${Age.VAS.en}")
        assertEquals("Hay barn · Senik", "${BuildingType.KOZOLEC.sl} · ${BuildingType.KOZOLEC.en}")
        assertEquals("Oak tree · Hrast", "${BuildingType.LIPA.sl} · ${BuildingType.LIPA.en}")
        assertEquals("Granny Maggie", Cast.HOST)
        assertEquals("Mr Dixon", Cast.defender(EventKind.WOLVES))
        assertEquals("Old Wilf", Cast.defender(EventKind.STORM))
        assertEquals("Mr Tyson", Cast.gatherer(Res.FOOD))
        assertEquals("Shepherd Jack", Cast.gatherer(Res.WOOD))
        assertEquals("A family gathered round the fire · Ob ognju se je zbrala družina", GameEngine.newGame(1, t0).log.single().text)
        // from German: the same English, the German beside it
        L10n.pair = fromDe
        assertEquals("Village · Dorf", "${Age.VAS.sl} · ${Age.VAS.en}")
        assertEquals("A family gathered round the fire · Eine Familie hat sich um das Feuer versammelt", GameEngine.newGame(1, t0).log.single().text)
    }

    @Test fun `requests come from the English cast and thank with its goods`() {
        val (_, added) = si.lanisce.lani.game.Quests.refill(village(), day, t0, ContentPool(cards))
        assertTrue(added.map { it.giver }.toString(), added.isNotEmpty() && added.all { it.giver in adults })
        assertTrue(added.map { it.title }.toString(), added.all { " · " in it.title })
        val givers = Cultures.current.quests.requests.map { it.giver }.toSet()
        assertEquals(adults, givers)
        assertTrue(Cultures.current.quests.requests.all { it.memory != null })
        val q = Quest("q", "Granny Maggie", "👵", "t", "s", Res.FOOD, mapOf(Res.FOOD to 10))
        val (s, r) = GameEngine.completeQuest(village().copy(quests = listOf(q)), "q", 5, 5, t0, giverId = "maggie")
        assertEquals("pudding", r.thanks?.id)
        assertEquals("Maggie thanks you with a sticky toffee pudding · Maggie ti v zahvalo da sticky toffee puding", s.log.last().text)
        // Mr Dixon keeps his "Mr" in every language
        val d = Quest("d", "Mr Dixon", "⚒️", "t", "s", Res.STONE, mapOf(Res.STONE to 10))
        L10n.pair = fromDe
        val (s2, _) = GameEngine.completeQuest(village().copy(quests = listOf(d)), "d", 5, 5, t0, giverId = "dixon")
        assertEquals("Mr Dixon thanks you with a horseshoe for luck · Mr Dixon schenkt dir zum Dank ein Hufeisen als Glücksbringer", s2.log.last().text)
    }

    @Test fun `goods, tools and the smith are the village's, their prices the game's`() {
        assertTrue(listOf("pudding", "oats", "honey", "wool", "ale", "tatie_pot", "mint_cake", "ham", "christmas_cake").all { it in Catalog.goods })
        assertEquals(listOf("ham", "shrimps", "tea", "spices"), Catalog.pedlarGoods)
        assertEquals(setOf("poppy", "harry", "alfie"), Catalog.children)
        assertEquals(Catalog.GOOD_PRICES.getValue("precious"), Catalog.goods.getValue("ham").value)
        assertTrue("ale is not for children", Catalog.goods.getValue("ale").adult && Catalog.goods.getValue("sparkling_wine").adult)
        var s = Bonds.add(village(), "jack", 30, day, now = t0)
        assertEquals("Jack gives you an axe 🪓 · Jack ti podari sekiro: +10 % 🪵", s.log.last().text)
        s = s.copy(buildings = listOf(Building("b0", BuildingType.SMITHY, 0)), chest = s.chest.copy(goods = mapOf("apples" to 1)))
        val forged = Chest.forge(s, "jack", t0)
        assertEquals("Mr Dixon the smith improved the axe ★★ · Kovač Dixon je izboljšal sekiro: +15 % 🪵", forged.log.last().text)
        L10n.pair = fromDe
        val again = Chest.forge(s, "jack", t0)
        assertEquals("Mr Dixon the smith improved the axe ★★ · Schmied Dixon hat die Axt verbessert: +15 % 🪵", again.log.last().text)
    }

    @Test fun `the English feasts fall on their days, with their words`() {
        val f = Calendar.festivals.associateBy { it.id }
        assertEquals(
            listOf(
                "new_year", "pancake_day", "mothering_sunday", "st_george", "easter", "egg_rolling", "may_day", "rushbearing", "show", "harvest",
                "bonfire_night", "shepherds_meet", "christmas", "boxing_day",
            ),
            Calendar.festivals.map { it.id },
        )
        // 2026: Easter is on 5 April
        assertEquals(LocalDate.of(2026, 2, 17), f.getValue("pancake_day").rule.date(2026))
        assertEquals(LocalDate.of(2026, 3, 15), f.getValue("mothering_sunday").rule.date(2026))
        assertEquals(LocalDate.of(2026, 4, 6), f.getValue("egg_rolling").rule.date(2026))
        assertEquals(LocalDate.of(2026, 8, 31), f.getValue("show").rule.date(2026))
        assertEquals(LocalDate.of(2026, 9, 27), f.getValue("harvest").rule.date(2026))
        assertEquals(LocalDate.of(2026, 11, 5), f.getValue("bonfire_night").rule.date(2026))
        assertEquals(LocalDate.of(2026, 11, 28), f.getValue("shepherds_meet").rule.date(2026))
        assertEquals(LocalDate.of(2026, 12, 26), f.getValue("boxing_day").rule.date(2026))
        for (fest in Calendar.festivals) {
            // every feast has its word pack (cultures/lakeland/packs), in English, meant in Slovene
            assertTrue(fest.id, fest.words.size >= Calendar.EXERCISES + 2)
            assertTrue(fest.id, fest.words.all { it.lang == "en" && it.en == it.word && it.sl == it.meaning })
            assertTrue(fest.id, fest.leaders.first() in Catalog.likes)
            assertTrue(fest.id, fest.good == null || fest.good in Catalog.goods)
        }
        // on the day: its words asked in English, the Slovene meanings the choices
        val run = Calendar.run(f.getValue("christmas"), 7, LocalDate.of(2026, 12, 25))
        val (word, first) = run.first()
        val means = first as Exercise.Choice
        assertEquals("What does “${word.word}” mean? · Kaj pomeni »${word.word}«?", means.prompt)
        assertEquals(word.sl, means.options[means.answer])
        val (other, second) = run[1]
        val say = second as Exercise.Choice
        assertEquals("How do you say “${other.sl}” in English? · Kako se po angleško reče »${other.sl}«?", say.prompt)
        assertEquals(other.en, say.options[say.answer])
        // from German: meant in German
        L10n.pair = fromDe
        val words = Calendar.festivals.first { it.id == "bonfire_night" }.words
        assertTrue(words.all { it.de == it.meaning && it.en == it.word })
    }

    @Test fun `the surprises, the projects and the events are the village's`() {
        assertEquals("jack", Surprises.SHEPHERD)
        assertEquals("Packman", Surprises.strangers.getValue(Surprises.PEDLAR).name)
        val way = Surprises.exercises(Surprise(day.toString(), Surprises.PILGRIM, 0), 1, canSpeak = false).first() as Exercise.Choice
        assertTrue(way.prompt, way.prompt.startsWith("🥾 The pilgrim: “How do I get to St Herbert's Island?”") && "Kako mu rečeš" in way.prompt)
        assertTrue(way.options[way.answer] in Cultures.current.surprises.pilgrim.ways.map { it.options.first() })
        // what the learner is to say is shown in their base, never in English (the answer)
        assertTrue(Cultures.current.surprises.pilgrim.ways.all { !it.say.has(Lang.EN) && it.say.has(Lang.SL) && it.say.has(Lang.DE) })

        val spec = Catalog.projects.first { it.id == "mlaj" }
        assertEquals("The maypole · Mlaj", spec.name)
        assertEquals("jack", spec.leader)
        val (_, r) = Projects.finishStep(village(Age.ZASELEK).copy(help = 50), "mlaj", 5, 5, day, t0)
        assertEquals("🌲 Jack and the lads chose a tall larch in the wood. · Jack in fantje so v gozdu izbrali visok macesen.", r.message)

        val e = GameEvent("ev", EventKind.STORM, 2, t0, t0 + 1000)
        assertEquals("⛈️ A storm over the fells! · Nevihta nad hribi!", si.lanisce.lani.game.Events.title(village(), e))
    }

    @Test fun `the people who move in have English names and English news`() {
        var s = GameState(seed = 9, age = Age.VAS, villagers = 6, buildings = listOf(Building("b0", BuildingType.FIELD, 0)))
        val news = ArrayList<String>()
        val people = Cultures.current.people
        for (i in 0 until 6) {
            val (n, lines) = Residents.reconcile(s, cast.filter { it.id == "maggie" }, day.plusDays(i.toLong()))
            news += lines.map { it.second }
            s = n
        }
        assertEquals("Granny Maggie moved into the village · Granny Maggie se je preselila v vas", news.first())
        val names = s.residents.mapNotNull { it.name }.filter { it != "Granny Maggie" }
        assertTrue(names.toString(), names.isNotEmpty() && names.all { n ->
            n.substringBefore(' ') in people.firstNames.female + people.firstNames.male && n.substringAfter(' ') in people.surnames
        })
        // their plain lines are English, translated into the learner's base
        val newcomer = Residents.villagerOf(s.residents.first { it.name != null && it.name != "Granny Maggie" }, cast, day)
        val hello = checkNotNull(newcomer).lines.greet.first()
        assertEquals(hello.by.getValue("en"), hello.target)
        assertEquals(hello.by.getValue("sl"), hello.base)
        L10n.pair = fromDe
        assertEquals(hello.by.getValue("de"), hello.base)
    }

    @Test fun `the cast speaks English, translated into Slovene and German, and remembers in every language`() {
        assertEquals(13, cast.size)
        assertEquals(adults + setOf("Poppy", "Harry", "Alfie"), cast.map { it.name }.toSet())
        // English has one "you": the four the learner calls Mr or Mrs are the polite register
        assertEquals(setOf("Mr Tyson", "Mr Dixon", "Mrs Birkett", "Mrs Hartley"), cast.filter { it.register == "vi" }.map { it.name }.toSet())
        for (v in cast) {
            assertTrue(v.id, " · " in v.role && v.likes.all { " · " in it })
            val all = with(v.lines) { greet + thanks + remember + idle + cheer + comfort + listen + bye + gift.liked + gift.ordinary + gift.rare }
            assertEquals(v.id, listOf(3, 2, 1), with(v.lines.gift) { listOf(liked.size, ordinary.size, rare.size) })
            for (l in all) {
                assertEquals(v.id, l.by.getValue("en"), l.target(fromSl))
                assertEquals(v.id, l.by.getValue("sl"), l.base(fromSl))
                assertEquals(v.id, l.by.getValue("de"), l.base(fromDe))
                assertEquals(v.id, l.by.getValue("it"), l.base(LangPair(Lang.EN, Lang.IT)))
            }
            assertTrue(v.id, v.lines.remember.all { l -> l.by.values.all { "{memory}" in it } })
        }
        val maggie = cast.first { it.id == "maggie" }
        assertEquals("Grandmother · Babica", maggie.role)
        // what she keeps of a gift and of a request fits her remember lines, in both languages
        val gift = VillagerLogic.giftMemory(Catalog.goods.getValue("honey"), maggie, day)
        assertTrue(gift.sl, gift.sl.endsWith("you gave me") && gift.en.endsWith("od tebe"))
        val said = VillagerLogic.remember(maggie, 3, listOf(gift), day)
        assertNotNull(said)
        assertTrue(said!!.sl, gift.sl in said.sl && gift.en in said.en)
        val request = Cultures.current.quests.requests.first { it.giver == "Granny Maggie" }
        val quest = Quest("q", "Granny Maggie", "👵", request.title.bi(), "s", Res.FOOD, emptyMap())
        val m = VillagerLogic.questMemory(quest, maggie, day)
        assertEquals(request.memory!!.target to request.memory!!.base, m.sl to m.en)
        // a gift: honey is one of her favourites, her own pudding is not; she thanks in English
        val liked = GiftTalk.reply(maggie, GiftTalk.reaction(Catalog.goods.getValue("honey"), "maggie"), 0, day)
        assertTrue(liked.sl, maggie.lines.gift.liked.any { it.by["en"] == liked.sl && it.by["sl"] == liked.en })
        assertEquals(GiftReaction.ORDINARY, GiftTalk.reaction(Catalog.goods.getValue("pudding"), "maggie"))
        assertEquals(GiftReaction.RARE, GiftTalk.reaction(Catalog.goods.getValue("christmas_cake"), "maggie"))
    }

    @Test fun `handing over a gift has no wrong register in English, the polite form in the translation`() {
        val maggie = cast.first { it.id == "maggie" }
        val dixon = cast.first { it.id == "dixon" }
        for (v in listOf(maggie, dixon)) {
            val said = GiftTalk.phrases(v.register, v.name, day, v.id, fromSl)
            assertEquals(v.id, 3, said.size)
            assertTrue(v.id, said.all { it.right && it.why == null })
            assertTrue(v.id, said.map { it.target }.toSet() == setOf("Here you are, this is for you.", "I have something for you.", "I hope you like it."))
        }
        // what the learner says: "zate" to Maggie, "za vas" to Mr Dixon
        assertTrue(GiftTalk.phrases("ti", "Maggie", day, "maggie", fromSl).any { it.base == "Izvoli, to je zate." })
        assertTrue(GiftTalk.phrases("vi", "Mr Dixon", day, "dixon", fromSl).any { it.base == "Izvolite, to je za vas." })
        // never the English line as its own translation
        assertTrue(GiftTalk.phrases("ti", "Maggie", day, "maggie", fromDe).none { it.base == it.target })
        // Jan's Slovene village still has one wrong way, in the other register
        assertEquals(1, GiftTalk.phrases("vi", "Tone", day, "tone", LangPair.DEFAULT).count { !it.right })
    }

    @Test fun `the stage's own lines are English, meant in the learner's base`() {
        val cheer = Lines.cheer("vi").first()
        assertEquals("Well done!" to "Bravo!", cheer.target to cheer.base)
        val cloze = Exercise.Cloze("The ___ is in the kitchen.", listOf("cat"))
        assertTrue(Lines.leadIns(cloze, "ti").all { it.target != it.base })
        assertEquals("9 of 10 right. You did excellently!", Lines.closing(9, 10, 10, "ti")!!.target)
        val ask = Lines.ask(si.lanisce.lani.ui.stage.Run.Quest("Granny Maggie", "👵"), "ti")
        assertEquals("Will you help me?" to "Mi pomagaš?", ask.target to ask.base)
        // from German and from Italian: their line of the stage's own
        L10n.pair = fromDe
        val gut = Lines.cheer("ti").first()
        assertEquals("Well done!" to "Super!", gut.target to gut.base)
        L10n.pair = LangPair(Lang.EN, Lang.IT)
        assertEquals("Well done!" to "Bravo!", Lines.cheer("ti").first().let { it.target to it.base })
    }
}
