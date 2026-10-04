package si.lanisce.lani.game.villagers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.Quests
import si.lanisce.lani.game.Res
import java.time.LocalDate

class ResidentsTest {
    private val day = LocalDate.of(2026, 9, 24)
    private val micka = Villager("micka", "Babica Micka", "👵", "grandma", voice = "female", home = listOf("hut", "spot:path"), order = 1)
    private val luka = Villager("luka", "Pastir Luka", "🐑", "shepherd", voice = "male", home = listOf("spot:meadow"), order = 2)
    private val mojca = Villager("mojca", "Učiteljica Mojca", "👩‍🏫", "teacher", voice = "female", home = listOf("school"), order = 3)
    private val tone = Villager("tone", "Kovač Tone", "⚒️", "smith", voice = "male", home = listOf("spot:woodpile"), since = "vas", order = 4)
    private val cast = listOf(luka, micka, mojca, tone)
    private val camp = GameState(seed = 5, age = Age.TABOR, villagers = 2)

    private fun quest(id: String, giver: String, source: QuestSource = QuestSource.LOCAL) =
        Quest(id, giver, "🙂", id, "story", Res.FOOD, mapOf(Res.FOOD to 5), source = source, moduleId = if (source == QuestSource.TUTOR) id else null)

    @Test fun `only who lives here or visits today asks for help`() {
        val s = camp.copy(
            residents = listOf(Resident("micka", day.toString()), Resident("luka", day.toString())),
            quests = listOf(quest("plug", "Kovač Tone"), quest("kuhinja", "Babica Micka"), quest("dvojina", "Učiteljica Mojca", QuestSource.TUTOR), quest("sosed", "Soseda Zdenka", QuestSource.TUTOR)),
        )
        assertTrue(Residents.here(s, "Babica Micka", cast, day))
        assertFalse(Residents.here(s, "Kovač Tone", cast, day))
        assertTrue(Residents.here(s, "Soseda Zdenka", cast, day)) // not one of the cast: someone the tutor made up
        assertEquals(listOf("kuhinja", "sosed"), Residents.openQuests(s, cast, day).map { it.id })
        // Tone visiting today asks too; tomorrow he's gone again
        val visit = s.copy(visitor = Visit("tone", day.toString()))
        assertTrue(Residents.here(visit, "Kovač Tone", cast, day))
        assertFalse(Residents.here(visit, "Kovač Tone", cast, day.plusDays(1)))
        // nobody lives here yet (an older bridge without the cast): everyone may ask
        assertTrue(Residents.here(camp, "Kovač Tone", cast, day))
    }

    @Test fun `a request goes with its giver when they're not in the village`() {
        val s = camp.copy(quests = listOf(quest("plug", "Kovač Tone"), quest("kuhinja", "Babica Micka"), quest("dvojina", "Učiteljica Mojca", QuestSource.TUTOR)))
        val (kept, expired) = Quests.prune(s, 0L, present = setOf("Babica Micka", "Pastir Luka"))
        // the tutor's request stays (it waits for Mojca to come); the smith's is gone, not expired
        assertEquals(listOf("kuhinja", "dvojina"), kept.quests.map { it.id })
        assertTrue(expired.isEmpty())
        assertEquals(3, Quests.prune(s, 0L, present = null).first.quests.size)
    }

    @Test fun `without a cast nothing changes`() {
        val (s, news) = Residents.reconcile(camp, emptyList(), day)
        assertEquals(camp, s)
        assertTrue(news.isEmpty())
    }

    @Test fun `the cast moves in first, in their order`() {
        val (s, news) = Residents.reconcile(camp, cast, day)
        assertEquals(listOf("micka", "luka"), s.residents.map { it.id })
        assertTrue(news[0].second.startsWith("Babica Micka se je preselila v vas"))
        assertTrue(news[1].second.startsWith("Pastir Luka se je preselil v vas"))
    }

    @Test fun `the teacher waits for the school, newcomers come meanwhile`() {
        val (s, _) = Residents.reconcile(camp.copy(villagers = 3), cast, day)
        assertEquals(3, s.residents.size)
        assertFalse(s.residents.any { it.id == "mojca" })
        assertTrue(s.residents[2].name != null) // someone new moved in
        val waiting = Residents.outlook(s, cast, day)
        assertTrue(waiting.any { it.who == "Učiteljica Mojca" && it.waitingFor == "Šola · School" })
        val school = s.copy(villagers = 4, buildings = listOf(Building("s", BuildingType.SCHOOL, 0)))
        assertTrue(Residents.reconcile(school, cast, day).first.residents.any { it.id == "mojca" })
    }

    @Test fun `an extra moves in as a newcomer, taking turns with the made-up names`() {
        val joze = Villager("joze", "Lovec Jože", "🦌", "hunter", voice = "male", home = listOf("spot:highseat"), since = "zaselek", order = 50, extra = true)
        val withJoze = cast + joze
        // before the hamlet he doesn't come; the cast first, then a newcomer
        val (early, _) = Residents.reconcile(camp.copy(villagers = 3), withJoze, day)
        assertEquals(listOf("micka", "luka"), early.residents.take(2).map { it.id })
        assertFalse(early.residents.any { it.id == "joze" })
        // in a hamlet, after the cast who can come: first the extra, then a made-up name, not the extra before the cast
        val hamlet = camp.copy(age = Age.ZASELEK, villagers = 4)
        val (s, news) = Residents.reconcile(hamlet, withJoze, day)
        assertEquals(listOf("micka", "luka", "joze"), s.residents.take(3).map { it.id })
        assertTrue(news.any { it.second.startsWith("Lovec Jože se je preselil v vas") })
        assertTrue("then a newcomer with a made-up name", s.residents[3].name != null)
        assertEquals("the outlook names him while he's next", "Lovec Jože", Residents.outlook(hamlet.copy(villagers = 2, residents = s.residents.take(2)), withJoze, day).first().who)
        // he isn't waited for like the cast (the teacher waits for her school)
        assertTrue(Residents.outlook(s, withJoze, day).none { it.who == "Lovec Jože" })
        // and he is someone of the village, with his own lines
        assertEquals("hunter", Residents.villagerOf(s.residents[2], withJoze, day)!!.art)
    }

    @Test fun `a building brings its person, ahead of their order, as soon as a bed is free`() {
        val zala = Villager("zala", "Zala", "👧", "child2", voice = "female", home = listOf("house", "hut", "spot:meadow"), order = 3)
        val france = Villager("france", "Mlinar France", "🧑‍🌾", "farmer", voice = "male", home = listOf("field", "kozolec"), order = 5)
        val anton = Villager("anton", "Čebelar Anton", "🐝", "beekeeper", voice = "male", home = listOf("beehive", "spot:pond"), since = "tabor", order = 8)
        val smith = Villager("tone", "Kovač Tone", "⚒️", "smith", voice = "male", home = listOf("smithy"), since = "zaselek", order = 9)
        val marko = Villager("marko", "Vinar Marko", "🍇", "winemaker", voice = "male", home = listOf("market", "spot:road"), since = "zaselek", order = 10)
        val vida = Villager("vida", "Gostilničarka Vida", "🍷", "innkeeper", voice = "female", home = listOf("market", "house"), since = "zaselek", order = 11)
        val ancka = Villager("ancka", "Teta Ančka", "📻", "aunt", voice = "female", home = listOf("house", "hut", "spot:road"), since = "tabor", order = 7)
        val all = listOf(micka, luka, zala, france, ancka, anton, smith, marko, vida)
        // who each building brings: a workplace its first worker who comes in a later age; a dwelling, the field nobody
        assertEquals("anton", Residents.brings(BuildingType.BEEHIVE, all)?.id)
        assertEquals("tone", Residents.brings(BuildingType.SMITHY, all)?.id)
        assertEquals("the market its winemaker, not the innkeeper after him", "marko", Residents.brings(BuildingType.MARKET, all)?.id)
        for (t in listOf(BuildingType.FIELD, BuildingType.HOUSE, BuildingType.HUT, BuildingType.TENT, BuildingType.LIPA)) assertEquals("$t", null, Residents.brings(t, all))
        assertEquals(null, Residents.broughtBy(vida, all))
        // a hamlet of two with four beds (the camp's two, a tent's two): the beehive brings Anton at once, ahead of the
        // upkeep's growth, and the village grows by him
        val hamlet = GameState(
            seed = 5, age = Age.ZASELEK, villagers = 2, residents = listOf(Resident("micka", day.toString()), Resident("luka", day.toString())),
            buildings = listOf(Building("t", BuildingType.TENT, 0), Building("b", BuildingType.BEEHIVE, 1)),
        )
        val (s, news) = Residents.reconcile(hamlet, all, day)
        assertEquals(listOf("micka", "luka", "anton"), s.residents.map { it.id })
        assertEquals("within the beds: the residents stay the population", 3, s.villagers)
        assertEquals(listOf("🐝" to "Čebelar Anton se je preselil v vas · Čebelar Anton moved into the village"), news)
        assertEquals("once", s, Residents.reconcile(s, all, day).first)
        // with room for one more, the next by order still comes: Zala, not someone the beehive already brought
        val (grown, _) = Residents.reconcile(s.copy(villagers = 4), all, day)
        assertEquals(listOf("micka", "luka", "anton", "zala"), grown.residents.map { it.id })
        // every bed taken: a smithy and a market bring nobody yet; a house, and they come at once
        val works = grown.copy(buildings = grown.buildings + Building("s", BuildingType.SMITHY, 2) + Building("m", BuildingType.MARKET, 3))
        assertEquals(grown.residents, Residents.reconcile(works, all, day).first.residents)
        assertEquals("they wait for a bed, first", listOf("Kovač Tone", "Vinar Marko"), Residents.outlook(works, all, day).take(2).map { it.who })
        val (busy, _) = Residents.reconcile(works.copy(buildings = works.buildings + Building("h", BuildingType.HOUSE, 4)), all, day)
        assertEquals(listOf("micka", "luka", "anton", "zala", "tone", "marko"), busy.residents.map { it.id })
        assertEquals(6, busy.villagers)
        // hard times: whom a building brought stays while others can go (with it, they'd come straight back)
        val (fewer, left) = Residents.reconcile(busy.copy(villagers = 4), all, day)
        assertEquals(listOf("micka", "anton", "tone", "marko"), fewer.residents.map { it.id })
        assertTrue(left.all { it.second.contains("iz vasi") })
        // before their age a building brings nobody (a pack or the tutor may have them later than their workplace)
        val early = anton.copy(since = "vas")
        assertEquals(listOf("micka", "luka"), Residents.reconcile(hamlet, listOf(micka, luka, early), day).first.residents.map { it.id })
    }

    @Test fun `cast from a later age waits for it`() {
        val (s, _) = Residents.reconcile(camp.copy(villagers = 6), cast, day)
        assertFalse(s.residents.any { it.id == "tone" })
        assertTrue(Residents.reconcile(s.copy(age = Age.VAS, villagers = 7), cast, day).first.residents.any { it.id == "tone" })
    }

    @Test fun `newcomers form families, and families have children, spaced out`() {
        var s = camp.copy(villagers = 2)
        s = Residents.reconcile(s, listOf(micka, luka), day).first
        var date = day
        repeat(12) {
            date = date.plusDays(3)
            s = Residents.reconcile(s.copy(villagers = s.villagers + 1), listOf(micka, luka), date).first
        }
        val families = s.residents.filter { it.family != null }.groupBy { it.family }
        assertTrue("a family of two formed: $families", families.values.any { m -> m.count { it.born == null } >= 2 })
        val babies = s.residents.filter { it.born != null }
        assertTrue("a child was born", babies.isNotEmpty())
        for ((_, kids) in babies.groupBy { it.family }) {
            val dates = kids.map { LocalDate.parse(it.born) }.sorted()
            dates.zipWithNext().forEach { (a, b) -> assertTrue(java.time.temporal.ChronoUnit.DAYS.between(a, b) >= Residents.BIRTH_SPACING_DAYS) }
        }
        assertEquals(s.villagers, s.residents.size)
        assertEquals(s.residents.size, s.residents.map { it.id }.toSet().size)
    }

    @Test fun `newcomers leave first, the cast stays`() {
        val grown = Residents.reconcile(camp.copy(villagers = 4), listOf(micka, luka), day).first
        val (s, news) = Residents.reconcile(grown.copy(villagers = 2), listOf(micka, luka), day)
        assertEquals(listOf("micka", "luka"), s.residents.map { it.id })
        assertTrue(news.all { it.second.contains("iz vasi") })
    }

    @Test fun `the village is capped`() {
        val (s, _) = Residents.reconcile(camp.copy(villagers = 50), cast, day)
        assertEquals(Residents.MAX_RESIDENTS, s.residents.size)
    }

    @Test fun `children grow up`() {
        val baby = Resident("n-ana-furlan", day.toString(), name = "Ana Furlan", voice = "female", born = day.toString(), family = "Furlan")
        assertEquals(Residents.Stage.BABY, Residents.stage(baby, day))
        assertEquals(Residents.Stage.CHILD, Residents.stage(baby, day.plusDays(60)))
        assertEquals(Residents.Stage.ADULT, Residents.stage(baby, day.plusDays(500)))
        assertEquals("baby", Residents.villagerOf(baby, cast, day)!!.art)
        assertEquals("Deklica · Girl", Residents.villagerOf(baby, cast, day.plusDays(60))!!.role)
    }

    @Test fun `people who move in or are born here get a voice by age and gender`() {
        val girl = Resident("n-ana-furlan", day.toString(), name = "Ana Furlan", voice = "female", born = day.toString(), family = "Furlan")
        assertEquals("girl", Residents.villagerOf(girl, cast, day.plusDays(60))!!.speaker)
        assertEquals("a youth: the young woman", "young-woman", Residents.villagerOf(girl, cast, day.plusDays(300))!!.speaker)
        val boy = girl.copy(id = "n-tim-furlan", name = "Tim Furlan", voice = "male")
        assertEquals("boy", Residents.villagerOf(boy, cast, day)!!.speaker)
        val newcomer = Resident("n-rok-humar", day.toString(), name = "Rok Humar", art = "man", voice = "male", role = "Drvar · Woodcutter", family = "Humar")
        val rok = Residents.villagerOf(newcomer, cast, day)!!
        assertEquals("young-man", rok.speaker)
        assertEquals("male", rok.voice)
        assertEquals("young-man", rok.speakerVoice)
        assertEquals("the cast keeps theirs (none set here: their gender)", "female", Residents.villagerOf(Resident("micka", day.toString()), cast, day)!!.speakerVoice)
    }

    @Test fun `family names in the plural locative`() {
        assertEquals("Furlanovih", Residents.plural("Furlan"))
        assertEquals("Mozetičevih", Residents.plural("Mozetič"))
        assertEquals("Humarjevih", Residents.plural("Humar"))
        assertEquals("Černetovih", Residents.plural("Černe"))
        assertEquals("Lozejevih", Residents.plural("Lozej"))
    }
}
