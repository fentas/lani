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
 * The second learner's village (plan 2, step 3b): the friuli culture pack, Italian with Slovene and English, switches the whole
 * game for a learner of Italian from Slovene: names, the chronicle, requests and their thanks, goods, tools and the
 * smith, the calendar with its Italian feasts on their days and their word packs, the surprises, the projects, the
 * events, the people who move in, the cast's lines and the stage's own. Primorska reads as it always did after
 * (CultureParityTest holds every text).
 */
class FriuliCultureTest {
    private val day = LocalDate.of(2026, 9, 1)
    private val t0 = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val pair = L10n.pair

    private val adults = setOf(
        "Nonna Rosa", "Pastore Davide", "Nonno Bepi", "Mugnaio Franco", "Zia Nives", "Apicoltore Aldo", "Fabbro Bruno",
        "Vignaiolo Matteo", "Ostessa Marisa", "Maestra Elena",
    )

    private val cards = listOf("il pane" to "kruh", "la mela" to "jabolko", "il latte" to "mleko", "la pecora" to "ovca", "il cane" to "pes")
        .mapIndexed { i, (it, sl) -> ReviewCard("c$i", it, sl, category = if (i < 3) "cibo" else "animals") }

    private fun village(age: Age = Age.VAS, vararg types: BuildingType) = GameEngine.newGame(3, t0).copy(
        age = age, resources = Res.entries.associateWith { 2000 }, villagers = 4, morale = 60,
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i) }, lastTick = day.toString(), foundedOn = day.minusDays(20).toString(),
    )

    /** The friuli cast as the bridge serves it (GET /villagers: the files of cultures/friuli/villagers). */
    private val cast by lazy {
        val dir = listOf(File("../../cultures/friuli/villagers"), File("../cultures/friuli/villagers")).first { it.isDirectory }
        parseVillagers(dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.joinToString(",", "[", "]") { it.readText() })
    }

    @Before fun italian() {
        Cultures.use("friuli")
        L10n.pair = LangPair(Lang.IT, Lang.SL)
    }

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    @Test fun `the pack is complete, in Italian, and marked as machine-written`() {
        val c = Cultures.current
        assertEquals("friuli", c.id)
        assertEquals(Lang.IT, c.language)
        assertEquals("machine-written, not reviewed by a native speaker (the German translations, de, added by machine too)", c.manifest.review)
        for (r in Res.entries) assertTrue(r.name, c.name(r).has(Lang.IT) && c.name(r).has(Lang.SL) && c.name(r).has(Lang.EN))
        for (a in Age.entries) assertTrue(a.name, c.name(a).has(Lang.IT) && c.name(a).has(Lang.SL))
        for (t in BuildingType.entries) assertTrue(t.name, c.name(t).has(Lang.IT) && c.name(t).has(Lang.SL))
        assertEquals(Catalog.projectFrames.map { it.id }, c.projects.map { it.id })
    }

    @Test fun `the names of things, and who is on the stage, are the village's`() {
        assertEquals("Paese · Vas", "${Age.VAS.sl} · ${Age.VAS.en}")
        assertEquals("Fienile · Senik", "${BuildingType.KOZOLEC.sl} · ${BuildingType.KOZOLEC.en}")
        assertEquals("Nonna Rosa", Cast.HOST)
        assertEquals("Fabbro Bruno", Cast.defender(EventKind.WOLVES))
        assertEquals("Nonno Bepi", Cast.defender(EventKind.STORM))
        assertEquals("Mugnaio Franco", Cast.gatherer(Res.FOOD))
        assertEquals("Una famiglia si è riunita intorno al fuoco · Ob ognju se je zbrala družina", GameEngine.newGame(1, t0).log.single().text)
    }

    @Test fun `requests come from the Italian cast and thank with its goods`() {
        val (_, added) = si.lanisce.lani.game.Quests.refill(village(), day, t0, ContentPool(cards))
        assertTrue(added.map { it.giver }.toString(), added.isNotEmpty() && added.all { it.giver in adults })
        assertTrue(added.map { it.title }.toString(), added.all { " · " in it.title })
        val givers = Cultures.current.quests.requests.map { it.giver }.toSet()
        assertEquals(adults, givers)
        assertTrue(Cultures.current.quests.requests.all { it.memory != null })
        val q = Quest("q", "Nonna Rosa", "👵", "t", "s", Res.FOOD, mapOf(Res.FOOD to 10))
        val (s, r) = GameEngine.completeQuest(village().copy(quests = listOf(q)), "q", 5, 5, t0, giverId = "rosa")
        // one of Rosa's: her gubana, wool socks or apples
        val g = Chest.thanksFor("rosa", "q", village().seed, day)
        assertTrue(g.id, g.id in Catalog.thanksRotation.getValue("rosa").goods)
        assertEquals(g.id, r.thanks?.id)
        assertEquals("Rosa ti ringrazia con ${g.name.acc} · Rosa ti v zahvalo da ${g.name.accText.base}", s.log.last().text)
        // the gubana as it reads
        val gubana = Catalog.goods.getValue("gubana").name
        assertEquals("una gubana · gubano", "${gubana.acc} · ${gubana.accText.base}")
    }

    @Test fun `a request about a rule unlocks its page in the Italian book, explained in Slovene`() {
        val book = si.lanisce.lani.data.Grammar.bundled("it")
        val named = Cultures.current.quests.requests.mapNotNull { it.grammar }
        assertTrue(named.toString(), named.size >= 10 && named.all { id -> book.any { it.id == id } })
        // the requests come day by day; one about a rule comes soon (Fabbro Bruno's orders, Maestra Elena's il or la …)
        val quest = (0L..30L).asSequence().mapNotNull { d ->
            si.lanisce.lani.game.Quests.refill(village(), day.plusDays(d), t0, ContentPool(cards)).second.firstOrNull { it.grammar != null }
        }.first()
        val template = Cultures.current.quests.requests.first { it.giver == quest.giver && it.story.bi() == quest.story }
        assertEquals(template.grammar, quest.grammar)
        // its challenge is about the page: the intro lists it, and playing it meets it
        val s = village().copy(quests = listOf(quest))
        val challenge = checkNotNull(GameEngine.questChallenge(s, quest.id, ContentPool(cards)))
        assertEquals(listOf(quest.grammar), challenge.pages)
        val (met, fresh) = si.lanisce.lani.game.GrammarBook.meet(s, "it", challenge.pages, day)
        assertEquals(listOf(quest.grammar), fresh)
        assertTrue(met.grammar.containsKey("it/${quest.grammar}") && !met.grammar.containsKey("sl/${quest.grammar}"))
        assertEquals(listOf(quest.grammar), si.lanisce.lani.game.GrammarBook.chapter(book, met, "it").met.map { it.id })
        // the page reads in the second learner's pair: Italian, explained in Slovene
        val page = book.first { it.id == quest.grammar }
        assertEquals("${page.target} · ${page.titleIn("sl")}", page.titleShown(L10n.pair))
        assertTrue(page.ruleIn("sl").isNotBlank() && page.examples.all { it.meaning("sl").isNotBlank() })
    }

    @Test fun `the tent's questions and the pilgrim's ways name the Italian book's pages, and practise them`() {
        val ids = si.lanisce.lani.data.Grammar.bundled("it").map { it.id }.toSet()
        val tent = si.lanisce.lani.game.TentMove.exercises(3, canSpeak = false)
        assertEquals(listOf("dove-preposizioni", "dove-preposizioni", "preposizioni-articolate", "dove-preposizioni"), tent.map { it.grammar })
        val ways = Surprises.exercises(Surprise(day.toString(), Surprises.PILGRIM, 2), 11, canSpeak = true)
        assertTrue(ways.toString(), ways.size == 3 && ways.all { it.grammar in ids })
        val rules = Surprises.onRules(1)
        assertEquals(5, rules.size) // the five ways; no letter's question is about a rule
        assertTrue(rules.map { it.grammar }.toString(), rules.all { it.grammar in ids } && "tu-lei" in rules.map { it.grammar })
    }

    @Test fun `goods, tools and the smith are the village's, their prices the game's`() {
        assertTrue(listOf("gubana", "frico", "montasio", "ribolla", "prosciutto", "panettone").all { it in Catalog.goods })
        assertEquals(listOf("prosciutto", "spezie", "olio", "caffe"), Catalog.pedlarGoods)
        assertEquals(setOf("giulia", "tommaso", "lorenzo"), Catalog.children)
        assertEquals(Catalog.GOOD_PRICES.getValue("precious"), Catalog.goods.getValue("prosciutto").value)
        var s = Bonds.add(village(), "davide", 30, day, now = t0)
        assertEquals("Davide ti regala un'ascia 🪓 · Davide ti podari sekiro: +10 % 🪵", s.log.last().text)
        s = s.copy(buildings = listOf(Building("b0", BuildingType.SMITHY, 0)), chest = s.chest.copy(goods = mapOf("mele" to 1)))
        val forged = Chest.forge(s, "davide", t0)
        assertEquals("Il fabbro Bruno ha migliorato l'ascia ★★ · Kovač Bruno je izboljšal sekiro: +15 % 🪵", forged.log.last().text)
    }

    @Test fun `the Italian feasts fall on their days, with their words`() {
        val f = Calendar.festivals.associateBy { it.id }
        assertEquals(
            listOf("capodanno", "epifania", "carnevale", "patria_friuli", "pasqua", "pasquetta", "liberazione", "repubblica", "ferragosto", "vendemmia", "san_martino", "san_nicolo", "natale", "santo_stefano"),
            Calendar.festivals.map { it.id },
        )
        // 2026: Easter is on 5 April
        assertEquals(LocalDate.of(2026, 1, 6), f.getValue("epifania").rule.date(2026))
        assertEquals(LocalDate.of(2026, 2, 17), f.getValue("carnevale").rule.date(2026))
        assertEquals(LocalDate.of(2026, 4, 5), f.getValue("pasqua").rule.date(2026))
        assertEquals(LocalDate.of(2026, 4, 6), f.getValue("pasquetta").rule.date(2026))
        assertEquals(LocalDate.of(2026, 6, 2), f.getValue("repubblica").rule.date(2026))
        assertEquals(LocalDate.of(2026, 9, 26), f.getValue("vendemmia").rule.date(2026))
        assertEquals(LocalDate.of(2026, 12, 26), f.getValue("santo_stefano").rule.date(2026))
        assertEquals("Domani: Natale 🎄 · Jutri: Božič 🎄", Calendar.countdown(f.getValue("natale"), 1))
        for (fest in Calendar.festivals) {
            // every feast has its word pack (cultures/friuli/packs), in Italian, meant in Slovene
            assertTrue(fest.id, fest.words.size >= Calendar.EXERCISES + 2)
            assertTrue(fest.id, fest.words.all { it.lang == "it" && it.it == it.word && it.sl == it.meaning })
            assertTrue(fest.id, fest.leaders.first() in Catalog.likes)
            assertTrue(fest.id, fest.good == null || fest.good in Catalog.goods)
        }
        // on the day: its words asked in Italian, the Slovene meanings the choices
        val run = Calendar.run(f.getValue("natale"), 7, LocalDate.of(2026, 12, 25))
        val (word, first) = run.first()
        val means = first as Exercise.Choice
        assertEquals("Che cosa vuol dire «${word.word}»? · Kaj pomeni »${word.word}«?", means.prompt)
        assertEquals(word.sl, means.options[means.answer])
        val (other, second) = run[1]
        val say = second as Exercise.Choice
        assertEquals("Come si dice in italiano «${other.sl}»? · Kako se po italijansko reče »${other.sl}«?", say.prompt)
        assertEquals(other.it, say.options[say.answer])
    }

    @Test fun `the surprises, the projects and the events are the village's`() {
        assertEquals("davide", Surprises.SHEPHERD)
        assertEquals("Cramaro", Surprises.strangers.getValue(Surprises.PEDLAR).name)
        val way = Surprises.exercises(Surprise(day.toString(), Surprises.PILGRIM, 0), 1, canSpeak = false).first() as Exercise.Choice
        assertTrue(way.prompt, way.prompt.startsWith("🥾 Il pellegrino: «Dov'è la strada per Castelmonte?»") && "Kako mu rečeš" in way.prompt)
        assertTrue(way.options[way.answer] in Cultures.current.surprises.pilgrim.ways.map { it.options.first() })

        val spec = Catalog.projects.first { it.id == "mlaj" }
        assertEquals("L'albero della cuccagna · Drog z nagradami", spec.name)
        assertEquals("davide", spec.leader)
        val (_, r) = Projects.finishStep(village(Age.ZASELEK).copy(help = 50), "mlaj", 5, 5, day, t0)
        assertEquals("🌲 Davide e i ragazzi hanno scelto un abete alto nel bosco. · Davide in fantje so v gozdu izbrali visoko jelko.", r.message)

        val e = GameEvent("ev", EventKind.STORM, 2, t0, t0 + 1000)
        assertEquals("⛈️ Un temporale sul Collio! · Nevihta nad Brdi!", si.lanisce.lani.game.Events.title(village(), e))
    }

    @Test fun `the people who move in have Friulian names and Italian news`() {
        var s = GameState(seed = 9, age = Age.VAS, villagers = 6, buildings = listOf(Building("b0", BuildingType.FIELD, 0)))
        val news = ArrayList<String>()
        val people = Cultures.current.people
        for (i in 0 until 6) {
            val (n, lines) = Residents.reconcile(s, cast.filter { it.id == "rosa" }, day.plusDays(i.toLong()))
            news += lines.map { it.second }
            s = n
        }
        assertEquals("Nonna Rosa si è trasferita in paese · Nonna Rosa se je preselila v vas", news.first())
        val names = s.residents.mapNotNull { it.name }.filter { it != "Nonna Rosa" }
        assertTrue(names.toString(), names.isNotEmpty() && names.all { n ->
            n.substringBefore(' ') in people.firstNames.female + people.firstNames.male && n.substringAfter(' ') in people.surnames
        })
        // their plain lines are Italian, translated into Slovene
        val newcomer = Residents.villagerOf(s.residents.first { it.name != null && it.name != "Nonna Rosa" }, cast, day)
        val hello = checkNotNull(newcomer).lines.greet.first()
        assertEquals(hello.by.getValue("it"), hello.target)
        assertEquals(hello.by.getValue("sl"), hello.base)
    }

    @Test fun `the cast speaks Italian, translated into Slovene, and remembers in both`() {
        assertEquals(13, cast.size)
        assertEquals(adults + setOf("Giulia", "Tommaso", "Lorenzo"), cast.map { it.name }.toSet())
        for (v in cast) {
            assertTrue(v.id, " · " in v.role && v.likes.all { " · " in it })
            val all = with(v.lines) { greet + thanks + remember + idle + cheer + comfort + listen + bye + gift.liked + gift.ordinary + gift.rare }
            assertEquals(v.id, listOf(3, 2, 1), with(v.lines.gift) { listOf(liked.size, ordinary.size, rare.size) })
            for (l in all) {
                assertEquals(v.id, l.by.getValue("it"), l.target)
                assertEquals(v.id, l.by.getValue("sl"), l.base)
                assertTrue(v.id, l.by.getValue("en").isNotBlank())
            }
            assertTrue(v.id, v.lines.remember.all { l -> l.by.values.all { "{memory}" in it } })
        }
        val rosa = cast.first { it.id == "rosa" }
        assertEquals("Nonna · Babica", rosa.role)
        // what she keeps of a gift and of a request fits her remember lines, in both languages
        val gift = VillagerLogic.giftMemory(Catalog.goods.getValue("frico"), rosa, day)
        assertTrue(gift.sl, gift.sl.endsWith("che mi hai regalato") && gift.en.endsWith("od tebe"))
        val said = VillagerLogic.remember(rosa, 3, listOf(gift), day)
        assertNotNull(said)
        assertTrue(said!!.sl, gift.sl in said.sl && gift.en in said.en)
        val request = Cultures.current.quests.requests.first { it.giver == "Nonna Rosa" }
        val quest = Quest("q", "Nonna Rosa", "👵", request.title.bi(), "s", Res.FOOD, emptyMap())
        val m = VillagerLogic.questMemory(quest, rosa, day)
        assertEquals(request.memory!!.target to request.memory!!.base, m.sl to m.en)
        // a gift: honey is one of her favourites, the frico is not; she thanks in Italian, and the learner hands it over with tu
        val liked = GiftTalk.reply(rosa, GiftTalk.reaction(Catalog.goods.getValue("miele"), "rosa"), 0, day)
        assertTrue(liked.sl, rosa.lines.gift.liked.any { it.by["it"] == liked.sl && it.by["sl"] == liked.en })
        assertEquals(GiftReaction.ORDINARY, GiftTalk.reaction(Catalog.goods.getValue("frico"), "rosa"))
        assertEquals(GiftReaction.RARE, GiftTalk.reaction(Catalog.goods.getValue("panettone"), "rosa"))
        assertTrue(GiftTalk.phrases(rosa.register, "Rosa", day, "rosa").filter { it.right }.all { it.target.endsWith("per te.") || it.target == "Spero che ti piaccia." })
    }

    @Test fun `the stage's own lines are Italian too, tu to a child`() {
        assertEquals("Bravo!", Lines.cheer("vi").first().target)
        val cloze = Exercise.Cloze("La ___ è in cucina.", listOf("mamma"))
        val lead = Lines.leadIns(cloze, "vi").first()
        assertEquals("Che cosa manca?" to "Kaj manjka?", lead.target to lead.base)
        assertEquals("9 su 10 giuste. Sei stato bravissimo!", Lines.closing(9, 10, 10, "ti")!!.target)
        assertEquals("Mi aiuti?", Lines.ask(si.lanisce.lani.ui.stage.Run.Quest("Nonna Rosa", "👵"), "vi").target)
    }
}
