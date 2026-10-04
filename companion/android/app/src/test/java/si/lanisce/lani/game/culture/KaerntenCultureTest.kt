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
 * A village in Carinthia: the kaernten culture pack, German with Slovene, English and Italian, switches the whole game
 * for a learner of German, from Slovene first: names, the chronicle, requests and their thanks, goods, tools and the
 * smith, the calendar with its Carinthian feasts on their days and their word packs, the surprises, the projects, the
 * events, the people who move in, the cast's lines and the stage's own (du to the learner, Sie from the learner to four
 * of them). Then the same pack for a learner from English and from Italian: the German stays, the translations follow
 * the base. Primorska reads as it always did after (CultureParityTest holds every text).
 */
class KaerntenCultureTest {
    private val day = LocalDate.of(2026, 9, 1)
    private val t0 = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val pair = L10n.pair

    private val adults = setOf(
        "Oma Resi", "Hirte Florian", "Opa Sepp", "Müller Franz", "Tante Mitzi", "Imker Stanko", "Schmied Toni",
        "Mostbauer Stefan", "Wirtin Kathi", "Lehrerin Eva",
    )

    private val cards = listOf("das Brot" to "kruh", "der Apfel" to "jabolko", "die Milch" to "mleko", "das Schaf" to "ovca", "der Hund" to "pes")
        .mapIndexed { i, (de, sl) -> ReviewCard("c$i", de, sl, category = if (i < 3) "essen" else "animals") }

    private fun village(age: Age = Age.VAS, vararg types: BuildingType) = GameEngine.newGame(3, t0).copy(
        age = age, resources = Res.entries.associateWith { 2000 }, villagers = 4, morale = 60,
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i) }, lastTick = day.toString(), foundedOn = day.minusDays(20).toString(),
    )

    /** The kaernten cast as the bridge serves it (GET /villagers: the files of cultures/kaernten/villagers). */
    private val cast by lazy {
        val dir = listOf(File("../../cultures/kaernten/villagers"), File("../cultures/kaernten/villagers")).first { it.isDirectory }
        parseVillagers(dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.joinToString(",", "[", "]") { it.readText() })
    }

    @Before fun german() {
        Cultures.use("kaernten")
        L10n.pair = LangPair(Lang.DE, Lang.SL)
    }

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    @Test fun `the pack is complete, in German, and marked as machine-written`() {
        val c = Cultures.current
        assertEquals("kaernten", c.id)
        assertEquals(Lang.DE, c.language)
        assertEquals("machine-written, not reviewed by a native speaker", c.manifest.review)
        for (r in Res.entries) assertTrue(r.name, Lang.entries.all { c.name(r).has(it) })
        for (a in Age.entries) assertTrue(a.name, Lang.entries.all { c.name(a).has(it) })
        for (t in BuildingType.entries) assertTrue(t.name, Lang.entries.all { c.name(t).has(it) })
        assertEquals(Catalog.projectFrames.map { it.id }, c.projects.map { it.id })
        // Oma Resi's recipes and Opa Sepp's books, to read in the chest
        assertEquals(listOf("altes-buch", "kasnudeln", "reindling", "sprichwoerter"), c.readings.keys.sorted())
    }

    @Test fun `the names of things, and who is on the stage, are the village's`() {
        assertEquals("Dorf · Vas", "${Age.VAS.sl} · ${Age.VAS.en}")
        assertEquals("Harpfe · Kozolec", "${BuildingType.KOZOLEC.sl} · ${BuildingType.KOZOLEC.en}")
        assertEquals("Linde · Lipa", "${BuildingType.LIPA.sl} · ${BuildingType.LIPA.en}")
        assertEquals("Oma Resi", Cast.HOST)
        assertEquals("Schmied Toni", Cast.defender(EventKind.WOLVES))
        assertEquals("Opa Sepp", Cast.defender(EventKind.STORM))
        assertEquals("Müller Franz", Cast.gatherer(Res.FOOD))
        assertEquals("Hirte Florian", Cast.gatherer(Res.WOOD))
        assertEquals("Eine Familie hat sich um das Feuer versammelt · Ob ognju se je zbrala družina", GameEngine.newGame(1, t0).log.single().text)
    }

    @Test fun `requests come from the Carinthian cast and thank with its goods`() {
        val (_, added) = si.lanisce.lani.game.Quests.refill(village(), day, t0, ContentPool(cards))
        assertTrue(added.map { it.giver }.toString(), added.isNotEmpty() && added.all { it.giver in adults })
        assertTrue(added.map { it.title }.toString(), added.all { " · " in it.title })
        val givers = Cultures.current.quests.requests.map { it.giver }.toSet()
        assertEquals(adults, givers)
        assertTrue(Cultures.current.quests.requests.all { it.memory != null })
        val q = Quest("q", "Oma Resi", "👵", "t", "s", Res.FOOD, mapOf(Res.FOOD to 10))
        val (s, r) = GameEngine.completeQuest(village().copy(quests = listOf(q)), "q", 5, 5, t0, giverId = "resi")
        assertEquals("reindling", r.thanks?.id)
        assertEquals("Resi schenkt dir zum Dank einen Reindling · Resi ti v zahvalo da reindling (koroški kvašeni kolač)", s.log.last().text)
    }

    @Test fun `goods, tools and the smith are the village's, their prices the game's`() {
        assertTrue(listOf("reindling", "kasnudeln", "almkaese", "most", "speck", "kirchtagssuppe", "kekse").all { it in Catalog.goods })
        assertEquals(listOf("safran", "gewuerze", "olivenoel", "kaffee"), Catalog.pedlarGoods)
        assertEquals(setOf("lena", "maxi", "jakob"), Catalog.children)
        assertEquals(Catalog.GOOD_PRICES.getValue("precious"), Catalog.goods.getValue("safran").value)
        assertTrue(Catalog.goods.getValue("most").adult)
        var s = Bonds.add(village(), "florian", 30, day, now = t0)
        assertEquals("Florian schenkt dir eine Axt 🪓 · Florian ti podari sekiro: +10 % 🪵", s.log.last().text)
        s = s.copy(buildings = listOf(Building("b0", BuildingType.SMITHY, 0)), chest = s.chest.copy(goods = mapOf("aepfel" to 1)))
        val forged = Chest.forge(s, "florian", t0)
        assertEquals("Der Schmied Toni hat die Axt verbessert ★★ · Kovač Toni je izboljšal sekiro: +15 % 🪵", forged.log.last().text)
    }

    @Test fun `the Carinthian feasts fall on their days, with their words`() {
        val f = Calendar.festivals.associateBy { it.id }
        assertEquals(
            listOf(
                "neujahr", "dreikoenig", "fasching", "palmsonntag", "ostern", "maibaum", "kufenstechen", "sonnwende", "kirchtag", "almabtrieb",
                "nationalfeiertag", "martini", "nikolo", "weihnachten",
            ),
            Calendar.festivals.map { it.id },
        )
        // 2026: Easter is on 5 April, Whit Monday on 25 May
        assertEquals(LocalDate.of(2026, 2, 17), f.getValue("fasching").rule.date(2026))
        assertEquals(LocalDate.of(2026, 3, 29), f.getValue("palmsonntag").rule.date(2026))
        assertEquals(LocalDate.of(2026, 4, 5), f.getValue("ostern").rule.date(2026))
        assertEquals(LocalDate.of(2026, 5, 25), f.getValue("kufenstechen").rule.date(2026))
        assertEquals(LocalDate.of(2026, 8, 30), f.getValue("kirchtag").rule.date(2026))
        assertEquals(LocalDate.of(2026, 9, 26), f.getValue("almabtrieb").rule.date(2026))
        assertEquals(LocalDate.of(2026, 12, 24), f.getValue("weihnachten").rule.date(2026))
        assertTrue(Calendar.countdown(f.getValue("weihnachten"), 1).endsWith("Jutri: Božič 🎄"))
        for (fest in Calendar.festivals) {
            // every feast has its word pack (cultures/kaernten/packs), in German, meant in Slovene
            assertTrue(fest.id, fest.words.size >= Calendar.EXERCISES + 2)
            assertTrue(fest.id, fest.words.all { it.lang == "de" && it.de == it.word && it.sl == it.meaning })
            assertTrue(fest.id, fest.leaders.first() in Catalog.likes)
            assertTrue(fest.id, fest.good == null || fest.good in Catalog.goods)
        }
        // on the day: its words asked in German, the Slovene meanings the choices
        val run = Calendar.run(f.getValue("kirchtag"), 7, LocalDate.of(2026, 8, 30))
        val (word, first) = run.first()
        val means = first as Exercise.Choice
        assertEquals("Was bedeutet „${word.word}“? · Kaj pomeni »${word.word}«?", means.prompt)
        assertEquals(word.sl, means.options[means.answer])
        val (other, second) = run[1]
        val say = second as Exercise.Choice
        assertEquals("Wie sagt man „${other.sl}“ auf Deutsch? · Kako se po nemško reče »${other.sl}«?", say.prompt)
        assertEquals(other.de, say.options[say.answer])
    }

    @Test fun `the surprises, the projects and the events are the village's`() {
        assertEquals("florian", Surprises.SHEPHERD)
        assertEquals("Kraxenträger", Surprises.strangers.getValue(Surprises.PEDLAR).name)
        val way = Surprises.exercises(Surprise(day.toString(), Surprises.PILGRIM, 0), 1, canSpeak = false).first() as Exercise.Choice
        assertTrue(way.prompt, way.prompt.startsWith("🥾 Der Pilger: „Wo geht es zum Luschariberg?“") && "Kako mu rečeš" in way.prompt)
        assertTrue(way.options[way.answer] in Cultures.current.surprises.pilgrim.ways.map { it.options.first() })

        val spec = Catalog.projects.first { it.id == "mlaj" }
        assertEquals("Der Maibaum · Mlaj", spec.name)
        assertEquals("florian", spec.leader)
        val (_, r) = Projects.finishStep(village(Age.ZASELEK).copy(help = 50), "mlaj", 5, 5, day, t0)
        assertEquals("🌲 Florian und die Burschen haben im Wald eine hohe Fichte ausgesucht. · Florian in fantje so v gozdu izbrali visoko smreko.", r.message)

        val e = GameEvent("ev", EventKind.STORM, 2, t0, t0 + 1000)
        assertEquals("⛈️ Ein Unwetter über den Karawanken! · Nevihta nad Karavankami!", si.lanisce.lani.game.Events.title(village(), e))
    }

    @Test fun `the people who move in have Carinthian names and German news`() {
        var s = GameState(seed = 9, age = Age.VAS, villagers = 6, buildings = listOf(Building("b0", BuildingType.FIELD, 0)))
        val news = ArrayList<String>()
        val people = Cultures.current.people
        for (i in 0 until 6) {
            val (n, lines) = Residents.reconcile(s, cast.filter { it.id == "resi" }, day.plusDays(i.toLong()))
            news += lines.map { it.second }
            s = n
        }
        assertEquals("Oma Resi ist ins Dorf gezogen · Oma Resi se je preselila v vas", news.first())
        // a newcomer's trade keeps its German capital in the news ("… Holzfällerin …", not "holzfällerin")
        val trades = people.trades.flatMap { listOf(it.female, it.male) }.map { it.by.getValue("de") }
        val moved = news.drop(1).map { it.substringBefore(" · ") }
        assertTrue(moved.toString(), moved.any { line -> trades.any { it in line } })
        assertTrue(moved.toString(), moved.none { line -> trades.any { it.lowercase() != it && it.lowercase() in line } })
        val names =s.residents.mapNotNull { it.name }.filter { it != "Oma Resi" }
        assertTrue(names.toString(), names.isNotEmpty() && names.all { n ->
            n.substringBefore(' ') in people.firstNames.female + people.firstNames.male && n.substringAfter(' ') in people.surnames
        })
        // their plain lines are German, translated into Slovene
        val newcomer = Residents.villagerOf(s.residents.first { it.name != null && it.name != "Oma Resi" }, cast, day)
        val hello = checkNotNull(newcomer).lines.greet.first()
        assertEquals(hello.by.getValue("de"), hello.target)
        assertEquals(hello.by.getValue("sl"), hello.base)
    }

    @Test fun `the cast speaks German, translated into Slovene, and remembers in both`() {
        assertEquals(13, cast.size)
        assertEquals(adults + setOf("Lena", "Maxi", "Jakob"), cast.map { it.name }.toSet())
        assertEquals(setOf("franz", "toni", "kathi", "eva"), cast.filter { it.register == "vi" }.map { it.id }.toSet())
        for (v in cast) {
            assertTrue(v.id, " · " in v.role && v.likes.all { " · " in it })
            val all = with(v.lines) { greet + thanks + remember + idle + cheer + comfort + listen + bye + gift.liked + gift.ordinary + gift.rare }
            assertEquals(v.id, listOf(3, 2, 1), with(v.lines.gift) { listOf(liked.size, ordinary.size, rare.size) })
            for (l in all) {
                assertEquals(v.id, l.by.getValue("de"), l.target)
                assertEquals(v.id, l.by.getValue("sl"), l.base)
                assertTrue(v.id, l.by.getValue("en").isNotBlank() && l.by.getValue("it").isNotBlank())
            }
            assertTrue(v.id, v.lines.remember.all { l -> l.by.values.all { "{memory}" in it } })
        }
        val resi = cast.first { it.id == "resi" }
        assertEquals("Oma · Babica", resi.role)
        // what she keeps of a gift and of a request fits her remember lines, in both languages
        val gift = VillagerLogic.giftMemory(Catalog.goods.getValue("kasnudeln"), resi, day)
        assertTrue(gift.sl, gift.sl == "die Kasnudeln von dir" && gift.en.endsWith("od tebe"))
        val said = VillagerLogic.remember(resi, 3, listOf(gift), day)
        assertNotNull(said)
        assertTrue(said!!.sl, gift.sl in said.sl && gift.en in said.en)
        val request = Cultures.current.quests.requests.first { it.giver == "Oma Resi" }
        val quest = Quest("q", "Oma Resi", "👵", request.title.bi(), "s", Res.FOOD, emptyMap())
        val m = VillagerLogic.questMemory(quest, resi, day)
        assertEquals(request.memory!!.target to request.memory!!.base, m.sl to m.en)
        // a request of the tutor's is remembered in German too, fitting "Ich denke noch oft an …"
        val tutors = VillagerLogic.questMemory(Quest("t", "Oma Resi", "👵", "Neue Wörter · Nove besede", "s", Res.FOOD, emptyMap()), resi, day)
        assertEquals("den Tag, an dem du mir geholfen hast: „Neue Wörter“", tutors.sl)
        // a gift: honey is one of her favourites, the Kasnudeln are not; she thanks in German, and the learner says du to her
        val liked = GiftTalk.reply(resi, GiftTalk.reaction(Catalog.goods.getValue("honig"), "resi"), 0, day)
        assertTrue(liked.sl, resi.lines.gift.liked.any { it.by["de"] == liked.sl && it.by["sl"] == liked.en })
        assertEquals(GiftReaction.ORDINARY, GiftTalk.reaction(Catalog.goods.getValue("kasnudeln"), "resi"))
        assertEquals(GiftReaction.RARE, GiftTalk.reaction(Catalog.goods.getValue("kekse"), "resi"))
        assertTrue(GiftTalk.phrases(resi.register, "Resi", day, "resi").filter { it.right }.all { it.target.endsWith("für dich.") || it.target == "Ich hoffe, es gefällt dir." })
        // … and Sie to the teacher, the smith, the miller and the innkeeper
        val eva = cast.first { it.id == "eva" }
        val toEva = GiftTalk.phrases(eva.register, "Eva", day, "eva")
        assertTrue(toEva.filter { it.right }.all { it.target.endsWith("für Sie.") || it.target == "Ich hoffe, es gefällt Ihnen." })
        assertTrue(toEva.single { !it.right }.target.let { it.endsWith("für dich.") || it == "Ich hoffe, es gefällt dir." })
    }

    @Test fun `the stage's own lines are German too, du to the learner`() {
        assertEquals("Super!", Lines.cheer("vi").first().target)
        val cloze = Exercise.Cloze("Die ___ ist in der Küche.", listOf("Mama"))
        val lead = Lines.leadIns(cloze, "vi").first()
        assertEquals("Was fehlt?" to "Kaj manjka?", lead.target to lead.base)
        assertEquals("9 von 10 richtig. Das hast du super gemacht!", Lines.closing(9, 10, 10, "ti")!!.target)
        assertEquals("Hilfst du mir?", Lines.ask(si.lanisce.lani.ui.stage.Run.Quest("Oma Resi", "👵"), "vi").target)
        val translate = Lines.leadIns(Exercise.Translate("kruh", listOf("das Brot")), "ti").first()
        assertEquals("Wie sagt man das auf Deutsch?" to "Kako se to reče po nemško?", translate.target to translate.base)
    }

    @Test fun `a learner from English or Italian gets the same German, translated into their base`() {
        val resi = cast.first { it.id == "resi" }
        val hello = resi.lines.greet.first()
        for ((base, founded) in listOf(Lang.EN to "A family gathered around the fire", Lang.IT to "Una famiglia si è riunita intorno al fuoco")) {
            L10n.pair = LangPair(Lang.DE, base)
            assertEquals("Eine Familie hat sich um das Feuer versammelt · $founded", GameEngine.newGame(1, t0).log.single().text)
            assertEquals(hello.by.getValue("de") to hello.by.getValue(base.code), hello.target to hello.base)
            val role = parseVillagers("[${File(listOf(File("../../cultures/kaernten/villagers"), File("../cultures/kaernten/villagers")).first { it.isDirectory }, "resi.json").readText()}]").single().role
            assertEquals("Oma · ${if (base == Lang.EN) "Grandmother" else "Nonna"}", role)
            val word = Calendar.festivals.first { it.id == "weihnachten" }.words.first()
            assertEquals(word.de to (if (base == Lang.EN) word.en else word.it), word.word to word.meaning)
            val closing = Lines.closing(9, 10, 10, "ti")!!
            assertEquals("9 von 10 richtig. Das hast du super gemacht!", closing.target)
            assertEquals(if (base == Lang.EN) "9 of 10 right. You did excellently!" else "9 su 10 giuste. Sei stato bravissimo!", closing.base)
            val phrase = GiftTalk.phrases("ti", "Resi", day, "resi").first { it.target == "Ich habe etwas für dich." || it.target == "Ich hoffe, es gefällt dir." || it.target == "Hier, bitte. Das ist für dich." }
            assertTrue(phrase.base, phrase.base in if (base == Lang.EN) listOf("I have something for you.", "I hope you like it.", "Here you are, this is for you.") else listOf("Ho una cosa per te.", "Spero che ti piaccia.", "Tieni, è per te."))
        }
        L10n.pair = LangPair(Lang.DE, Lang.EN)
        val q = Quest("q", "Oma Resi", "👵", "t", "s", Res.FOOD, mapOf(Res.FOOD to 10))
        val (s, _) = GameEngine.completeQuest(village().copy(quests = listOf(q)), "q", 5, 5, t0, giverId = "resi")
        assertEquals("Resi schenkt dir zum Dank einen Reindling · Resi thanks you with a Reindling (the Carinthian yeast cake)", s.log.last().text)
        assertEquals("Dorf · Village", "${Age.VAS.sl} · ${Age.VAS.en}")
        L10n.pair = LangPair(Lang.DE, Lang.IT)
        assertEquals("Harpfe · Rastrelliera del fieno", "${BuildingType.KOZOLEC.sl} · ${BuildingType.KOZOLEC.en}")
        val e = GameEvent("ev", EventKind.STORM, 2, t0, t0 + 1000)
        assertEquals("⛈️ Ein Unwetter über den Karawanken! · Un temporale sulle Caravanche!", si.lanisce.lani.game.Events.title(village(), e))
    }
}
