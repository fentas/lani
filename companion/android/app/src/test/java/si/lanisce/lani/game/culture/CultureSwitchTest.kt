package si.lanisce.lani.game.culture

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
import si.lanisce.lani.game.Tomorrow
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A second culture pack switches everything the village says: a tiny made-up pack in Italian (test resources,
 * cultures-test/tinyland) played by an Italian learner, then Primorska again. The rules and numbers stay the game's.
 */
class CultureSwitchTest {
    private val day = LocalDate.of(2026, 9, 1)
    private val t0 = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val pair = L10n.pair

    private val tiny: Culture by lazy {
        Cultures.parse("tinyland") { name -> javaClass.getResourceAsStream("/cultures-test/tinyland/$name")?.use { it.readBytes().decodeToString() } }
    }

    private val cards = listOf("pane" to "bread", "mela" to "apple", "latte" to "milk", "pecora" to "sheep", "cane" to "dog")
        .mapIndexed { i, (it, en) -> ReviewCard("c$i", it, en, category = if (i < 3) "food" else "animals") }

    private fun village(age: Age = Age.VAS, vararg types: BuildingType) = GameEngine.newGame(3, t0).copy(
        age = age, resources = Res.entries.associateWith { 2000 }, villagers = 4, morale = 60,
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i) }, lastTick = day.toString(), foundedOn = day.minusDays(20).toString(),
    )

    @Before fun italian() {
        Cultures.use(tiny)
        L10n.pair = LangPair(Lang.IT, Lang.EN)
    }

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    @Test fun `the names of things are the culture's`() {
        assertEquals("tinyland", Cultures.current.id)
        assertEquals("Villaggio · Village", "${Age.VAS.sl} · ${Age.VAS.en}")
        assertEquals("Fienile · Hayrack", "${BuildingType.KOZOLEC.sl} · ${BuildingType.KOZOLEC.en}")
        assertEquals("Cibo", Res.FOOD.sl)
        assertEquals("Lupi", EventKind.WOLVES.sl)
        // the string tables' "Serve {sl}" gets the Italian age
        val blocked = GameEngine.buildOptions(village(Age.TABOR)).first { it.type == BuildingType.CHURCH }.reason
        assertEquals("Serve Borgo · Needs Hamlet", blocked)
        // who is on the training stage: the culture's host, defenders and gatherers (else today's companion)
        assertEquals("Nonna Pina", si.lanisce.lani.ui.stage.Cast.HOST)
        assertEquals("Pastore Nino", si.lanisce.lani.ui.stage.Cast.defender(EventKind.WOLVES))
        assertEquals(null, si.lanisce.lani.ui.stage.Cast.defender(EventKind.STORM))
        assertEquals("Nonna Pina", si.lanisce.lani.ui.stage.Cast.gatherer(Res.FOOD))
    }

    @Test fun `the chronicle speaks the culture's words`() {
        assertEquals("Una famiglia si è riunita intorno al fuoco · A family gathered around the fire", GameEngine.newGame(1, t0).log.single().text)
        val built = GameEngine.build(village(Age.VAS), BuildingType.HUT, t0)
        assertEquals("Costruito: Capanna · Hut", built.log.last().text)
        val s = GameEngine.gathered(village(), Res.WOOD, 7, 7, day, t0)
        assertEquals("Hai abbattuto due alberi · You felled two trees", s.log.last().text)
    }

    @Test fun `requests, their givers and thanks are the culture's`() {
        val (_, added) = si.lanisce.lani.game.Quests.refill(village(), day, t0, ContentPool(cards))
        assertEquals(setOf("Nonna Pina", "Pastore Nino"), added.map { it.giver }.toSet())
        assertTrue(added.any { it.title == "La cucina di Pina · Pina's kitchen" })
        val q = Quest("q", "Nonna Pina", "👵", "t", "s", Res.FOOD, mapOf(Res.FOOD to 10))
        val (s, r) = GameEngine.completeQuest(village().copy(quests = listOf(q)), "q", 5, 5, t0, giverId = "pina")
        assertEquals("👵 Nonna Pina: Grazie mille! · Thanks a lot!", r.message)
        assertEquals("frico", r.thanks?.id)
        // who thanks: the cast's name to friends ("Pina"), else from the id, as for a newcomer
        assertEquals("Pina ti ringrazia con il frico · Pina thanks you with a frico", s.log.last().text)
    }

    @Test fun `the goods, the tools and the smith are the culture's, their prices the game's`() {
        assertEquals(listOf("pane", "frico", "mele", "gubana"), Catalog.goods.keys.toList())
        assertEquals(Catalog.GOOD_PRICES.getValue("rich"), Catalog.goods.getValue("gubana").value)
        assertEquals(listOf("gubana"), Catalog.pedlarGoods)
        assertEquals(setOf("gigi"), Catalog.children)
        assertEquals(Catalog.TOOL_EFFECTS.getValue("wood"), Catalog.tools.getValue("nino").effect)
        var s = Bonds.add(village(), "nino", 30, day, now = t0)
        assertEquals("Nino ti regala un'ascia 🪓 · Nino gives you an axe: +10 % 🪵", s.log.last().text)
        // Beppe is this culture's smith: he forges, and isn't here yet
        s = s.copy(buildings = listOf(Building("b0", BuildingType.SMITHY, 0)), chest = s.chest.copy(goods = mapOf("pane" to 1)), residents = listOf(si.lanisce.lani.game.villagers.Resident("nino", day.toString())))
        assertEquals("Il fabbro Beppe non vive ancora in paese · Smith Beppe doesn't live here yet", Chest.forgeOption(s, "nino")?.reason)
        val forged = Chest.forge(s.copy(residents = emptyList()), "nino", t0)
        assertEquals("Il fabbro Beppe ha migliorato l'ascia ★★ · Smith Beppe improved the axe: +15 % 🪵", forged.log.last().text)
    }

    @Test fun `the calendar, the surprises and the projects are the culture's`() {
        assertEquals(listOf("sagra"), Calendar.festivals.map { it.id })
        val sagra = Calendar.festivals.single()
        assertEquals(LocalDate.of(2026, 9, 27), sagra.rule.date(2026))
        assertEquals("Domani: Sagra 🍝 · Tomorrow: The village fair", Calendar.countdown(sagra, 1))
        assertEquals("11 novembre · 11 November", Calendar.dateText(LocalDate.of(2026, 11, 11)))

        assertEquals("nino", Surprises.SHEPHERD)
        assertEquals("pellegrino", Surprises.strangers.getValue(Surprises.PILGRIM).name.lowercase())
        val sp = Surprise(day.toString(), Surprises.PILGRIM, 0)
        val way = Surprises.exercises(sp, 1, canSpeak = false).single() as si.lanisce.lani.data.Exercise.Choice
        assertEquals("🥾 Pellegrino: «Dov'è la strada?»\nCome glielo dici · How do you tell him: “Straight on.”", way.prompt)
        assertEquals("Sempre dritto.", way.options[way.answer])
        assertEquals("✉️ Una lettera da Nova Gorica · A letter from Nova Gorica", Surprises.title(Surprise(day.toString(), Surprises.LETTER, 0, "pina")))

        assertEquals(listOf("mlaj"), Catalog.projects.map { it.id })
        val spec = Catalog.projects.single()
        assertEquals("L'albero di maggio · The maypole", spec.name)
        assertEquals("nino", spec.leader)
        // the frame (steps, costs, landmark) is the game's
        assertEquals(Catalog.projectFrames.first { it.id == "mlaj" }.steps.map { it.first }, spec.steps.map { it.kind })
        val (s, r) = Projects.finishStep(village(Age.ZASELEK).copy(help = 50), "mlaj", 5, 5, day, t0)
        assertEquals("🌲 Nino ha scelto un abete. · Nino chose a fir.", r.message)
        val teaser = Tomorrow.teasers(s, day).first { it.kind == si.lanisce.lani.game.Teaser.Kind.PROJECT }
        assertEquals("Domani: L'albero di maggio, passo 2 di 5 · Tomorrow: the maypole, step 2 of 5", teaser.text)
    }

    @Test fun `events and the people who move in are the culture's`() {
        val e = GameEvent("ev", EventKind.STORM, 2, t0, t0 + 1000)
        assertEquals("⛈️ Arriva la bora! · The bora is coming!", si.lanisce.lani.game.Events.title(village(), e))
        val lost = si.lanisce.lani.game.Events.outcome(village().copy(event = e), e, won = false, expired = true, now = t0).second
        assertEquals("⛈️ La bora ha bagnato la legna · The bora soaked the firewood (⌛ troppo tardi · too late)", lost.message)

        var s = GameState(seed = 9, age = Age.VAS, villagers = 6, buildings = listOf(Building("b0", BuildingType.FIELD, 0)))
        val news = ArrayList<String>()
        for (i in 0 until 6) {
            val (n, lines) = Residents.reconcile(s, listOf(si.lanisce.lani.game.villagers.Villager("pina", "Nonna Pina", art = "grandma")), day.plusDays(i.toLong()))
            news += lines.map { it.second }
            s = n
        }
        val names = s.residents.mapNotNull { it.name }
        assertTrue(names.toString(), names.isNotEmpty() && names.all { n -> n.substringBefore(' ') in listOf("Giulia", "Sara", "Marco", "Luca") && n.substringAfter(' ') in listOf("Bressan", "Zorzut") })
        assertTrue(news.toString(), news.first() == "Nonna Pina si è trasferita in paese · Nonna Pina moved into the village")
        assertTrue(news.toString(), news.drop(1).all { "in paese · " in it && ", a " in it })
        assertTrue(s.residents.filter { it.name != null }.all { it.role in listOf("Boscaiola · Woodcutter", "Boscaiolo · Woodcutter", "Contadina · Farmer", "Contadino · Farmer") })
    }

    @Test fun `a learner who explains in Slovene gets the pack's Slovene where it has it, else English`() {
        L10n.pair = LangPair(Lang.IT, Lang.SL)
        assertEquals("Villaggio · Vas", "${Age.VAS.sl} · ${Age.VAS.en}")
        assertEquals("Borgo · Hamlet", "${Age.ZASELEK.sl} · ${Age.ZASELEK.en}")
        val sp = Surprise(day.toString(), Surprises.PILGRIM, 0)
        val way = Surprises.exercises(sp, 1, canSpeak = false).single() as si.lanisce.lani.data.Exercise.Choice
        assertEquals("🥾 Pellegrino: «Dov'è la strada?»\nCome glielo dici · Kako mu rečeš: “Naravnost.”", way.prompt)
    }

    @Test fun `and back, Primorska reads as it always did`() {
        back()
        assertEquals("Vas · Village", "${Age.VAS.sl} · ${Age.VAS.en}")
        assertEquals("Ob ognju se je zbrala družina · A family gathered around the fire", GameEngine.newGame(1, t0).log.single().text)
        assertEquals(11, Calendar.festivals.size)
    }
}
