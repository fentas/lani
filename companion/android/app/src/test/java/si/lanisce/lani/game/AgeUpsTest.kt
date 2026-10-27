package si.lanisce.lani.game

import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.culture.CelebrationFile
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate
import java.time.ZoneId

/**
 * Every age-up is a celebration (GAME.md "Ages and their celebrations"): the moment's beats per age and what shows in
 * each, what's new per age, the party on the evening the age came (and the next) until it is had, paying once while it is
 * the village's age, the decorations' days, the chronicle's ages to watch again (also one reached before the
 * celebrations came), the village as it was for a moment played again, and who says the party's lines.
 */
class AgeUpsTest {
    private val zone = ZoneId.systemDefault()
    private val t0 = noon(day0)

    @After fun back() { Cultures.use(Cultures.DEFAULT) }

    /** A culture whose every age has a party: Primorska's pack with [file] for each age. */
    private fun withParties() {
        val base = Cultures.load(Cultures.DEFAULT)
        Cultures.use(base.withCelebrations(Age.entries.drop(1).associateWith { file(it) }))
    }

    private fun file(age: Age) = CelebrationFile(
        schema = Cultures.CELEBRATION_SCHEMA, age = AgeUps.key(age), language = "sl", emoji = "🪗",
        name = Text(mapOf("sl" to "Veselica", "en" to "The village party")),
        about = Text(mapOf("sl" to "Vsa vas pleše.", "en" to "The whole village dances.")),
        line = Text(mapOf("sl" to "Plesali smo polko.", "en" to "We danced the polka.")),
        memory = Text(mapOf("sl" to "našo veselico", "en" to "our village party")),
        decor = listOf("bunting", "floor"), music = "harmonika", place = "square", host = "vida", guests = listOf("marko", "nejc"),
        pack = "slavje-veselica", words = listOf("harmonika"),
        levels = json.decodeFromString(JsonObject.serializer(), LEVELS),
    )

    private val day = LocalDate.of(2026, 10, 12)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m)

    /** A village that reached Vas on [day], three people living there. */
    private val vas = GameState(
        age = Age.VAS, morale = 60, grown = mapOf("vas" to day.toString()),
        residents = listOf(Resident("vida", "2026-09-01"), Resident("marko", "2026-09-01"), Resident("nejc", "2026-09-01")),
    )

    // --- the moment ------------------------------------------------------------------------------------------------

    @Test fun `each age's moment shows its own changes, one after another`() {
        val s = GameState(buildings = listOf(Building("p", BuildingType.PALISADE, NO_PLOT)))
        val grows = mapOf(
            Age.TABOR to listOf(Change.FOREST, Change.PLOTS),
            Age.ZASELEK to listOf(Change.FOREST, Change.PLOTS, Change.WEST, Change.SQUARE),
            Age.VAS to listOf(Change.FOREST, Change.PLOTS, Change.WIDE),
            Age.TRG to listOf(Change.FOREST, Change.PLOTS, Change.SQUARE, Change.COBBLES),
            Age.MESTO to listOf(Change.FOREST, Change.PLOTS),
        )
        for ((to, changes) in grows) {
            val from = Age.entries[to.ordinal - 1]
            val m = AgeUps.moment(from, to, s.copy(age = to))
            assertEquals("$to's beats", listOf(Beat.Kind.OPEN) + changes.map { Beat.Kind.GROW } + listOf(Beat.Kind.GATHER, Beat.Kind.DECORATE, Beat.Kind.SIGN, Beat.Kind.CHEER), m.beats.map { it.kind })
            assertEquals("$to's changes", changes, m.beats.mapNotNull { it.change })
            // the palisade moves out with the forest and the fire burns up with the gathering: changes, no beats of their own
            assertTrue(Change.PALISADE in m.changes && Change.FIRE in m.changes)
            // one after another, no gap; long enough to breathe (Jan: "too fast" at 15–22 s), short enough to watch
            m.beats.zipWithNext().forEach { (a, b) -> assertEquals(a.to, b.from, 1e-9) }
            assertEquals(0.0, m.beats.first().from, 1e-9)
            assertTrue("$to: ${m.length} s", m.length in 26.0..36.0)
            // every beat long enough for its caption to be read after the camera's flight
            assertTrue(m.beats.all { it.to - it.from >= 3.0 })
            assertEquals(AgeUps.newPlots(from, to), m.plots)
        }
        // no palisade, no palisade to move
        assertFalse(Change.PALISADE in AgeUps.moment(Age.ZASELEK, Age.VAS, GameState(age = Age.VAS)).changes)
    }

    @Test fun `the map grows a change in its beat, the decorations go up in theirs, the music with them`() {
        val m = AgeUps.moment(Age.ZASELEK, Age.VAS, GameState(age = Age.VAS))
        val start = m.at(0.0)
        assertEquals(Age.ZASELEK, start.growth.from)
        assertEquals(listOf(0f, 0f, 0f), listOf(start.growth.forest, start.growth.wide, start.growth.fire))
        assertTrue(start.festive.up.all { it == 0f })
        assertFalse(start.music || start.festive.gather)
        val woods = m.beats.first { it.change == Change.FOREST }
        val mid = m.at((woods.from + woods.to) / 2)
        assertTrue(mid.growth.forest in 0.01f..0.99f)
        assertEquals(0f, mid.growth.wide)
        // the new plots are marked while theirs is on, and only then
        val plots = m.beats.first { it.change == Change.PLOTS }
        assertEquals(AgeUps.newPlots(Age.ZASELEK, Age.VAS), m.at(plots.from + 0.1).growth.plots)
        assertTrue(m.at(plots.to + 0.1).growth.plots.isEmpty())
        // the decorations one after another
        val deco = m.beats.first { it.kind == Beat.Kind.DECORATE }
        val early = m.at(deco.from + 0.3)
        assertTrue(early.music && early.festive.gather)
        assertTrue(early.festive.up[0] > 0f && early.festive.up.last() == 0f)
        val end = m.at(m.length)
        assertTrue(end.done)
        assertEquals(listOf(1f, 1f, 1f, 1f, 1f, 1f), with(end.growth) { listOf(forest, west, wide, square, cobbles, fire) })
        assertTrue(end.festive.up.all { it == 1f })
        assertEquals(1f, end.festive.sign)
        // the sign turns in its beat
        val sign = m.beats.first { it.kind == Beat.Kind.SIGN }
        assertEquals(0f, m.at(sign.from - 0.1).festive.sign)
        assertTrue(m.at((sign.from + sign.to) / 2).festive.sign!! in 0.01f..0.99f)
        // a town marches in under its banners
        val town = AgeUps.moment(Age.TRG, Age.MESTO, GameState(age = Age.MESTO))
        assertNotNull(town.at(town.musicAt + 1).festive.march)
        assertNull(end.festive.march)
    }

    @Test fun `each age has its decorations and music, a culture without its own the gathering`() {
        // a pack with no celebrations: the generic gathering, no party
        Cultures.use(Cultures.load(Cultures.DEFAULT).withCelebrations(emptyMap()))
        val c = AgeUps.celebration(Age.ZASELEK)
        assertTrue(c.generic)
        assertFalse(c.party)
        assertEquals(listOf(Decor.SPRUCE, Decor.BUNTING), c.decor)
        assertEquals(Music.VALCEK, c.music)
        assertEquals(listOf(Decor.TORCHES), AgeUps.celebration(Age.TABOR).decor)
        assertEquals(Music.HARMONIKA, AgeUps.celebration(Age.VAS).music)
        assertEquals(listOf(Decor.STALLS, Decor.BUNTING), AgeUps.celebration(Age.TRG).decor)
        assertEquals(Music.GODBA, AgeUps.celebration(Age.MESTO).music)
        assertNull(AgeUps.partyNow(vas, at(day, 19)))
        // the culture's own: its name, decorations, music and party
        withParties()
        val v = AgeUps.celebration(Age.VAS)
        assertFalse(v.generic)
        assertTrue(v.party)
        assertEquals("Veselica", v.nameText.target)
        assertEquals(listOf(Decor.BUNTING, Decor.FLOOR), v.decor)
        assertEquals(PartyPlace.SQUARE, v.place)
    }

    // --- what's new ---------------------------------------------------------------------------------------------------

    @Test fun `what's new comes from the game's data, each to where it is`() {
        fun go(age: Age, scenes: List<SceneSpec> = emptyList(), cast: List<Villager> = emptyList()) = AgeUps.whatsNew(age, scenes, cast)
        val tabor = go(Age.TABOR)
        assertEquals(
            listOf(BuildingType.WELL, BuildingType.HUT, BuildingType.KOZOLEC, BuildingType.PALISADE).map { it.name }.toSet(),
            tabor.filter { it.go == NewThing.Go.BUILD && it.arg != null }.map { it.arg }.toSet(),
        )
        assertTrue(tabor.any { it.go == NewThing.Go.FESTIVALS })
        assertTrue(tabor.any { it.text.contains("2") && it.emoji == "⬆️" })
        assertTrue(tabor.any { it.emoji == "📐" && it.text.startsWith("3 ") })
        val zaselek = go(Age.ZASELEK)
        assertEquals(setOf("mlaj", "most", "mlin"), zaselek.filter { it.go == NewThing.Go.PROJECTS }.map { it.arg }.toSet())
        assertTrue(zaselek.any { it.go == NewThing.Go.BUILD && it.arg == BuildingType.SCHOOL.name })
        assertTrue(zaselek.any { it.go == NewThing.Go.SCHOOL })
        assertTrue(zaselek.any { it.go == NewThing.Go.CHEST })
        val vas = go(
            Age.VAS,
            scenes = listOf(
                SceneSpec(id = "na-vasi", title = "Na vasi · On the square", art = "square", from = listOf("lipa"), needs = "vas"),
                SceneSpec(id = "ob-ognju", title = "Ob ognju · By the fire", art = "campfire", from = listOf("fire")),
            ),
            cast = listOf(Villager(id = "mojca", name = "Učiteljica Mojca", art = "teacher", since = "zaselek"), Villager(id = "ana", name = "Ana", art = "woman", since = "vas")),
        )
        assertEquals(listOf(BuildingType.MARKET.name), vas.filter { it.go == NewThing.Go.BUILD && it.arg != null }.map { it.arg })
        assertEquals(listOf("na-vasi"), vas.filter { it.go == NewThing.Go.SCENE }.map { it.arg })
        assertEquals(listOf("ana"), vas.filter { it.go == NewThing.Go.PERSON }.map { it.arg })
        assertEquals(6, vas.count { it.go == NewThing.Go.PROJECTS })
        val trg = go(Age.TRG)
        assertTrue(trg.none { it.go == NewThing.Go.BUILD && it.arg != null })
        assertEquals(setOf("vodnjak_na_trgu", "toplar", "razgledni_stolp"), trg.filter { it.go == NewThing.Go.PROJECTS }.map { it.arg }.toSet())
        assertTrue(trg.any { it.emoji == "⇄" })
        val mesto = go(Age.MESTO)
        assertEquals(listOf("mestna_ura"), mesto.filter { it.go == NewThing.Go.PROJECTS }.map { it.arg })
        assertTrue(mesto.any { it.emoji == "📐" && it.text.startsWith("8 ") })
        assertTrue(mesto.none { it.emoji == "⬆️" }) // the top level came with Trg
        // every age's stores
        for (a in Age.entries.drop(1)) assertTrue(go(a).any { it.go == NewThing.Go.INFO && it.text.contains("${Catalog.baseCap[a.ordinal]}") })
    }

    @Test fun `the new age's card says three things first, the rest folded, nothing lost`() {
        val cast = listOf(Villager(id = "ana", name = "Ana", art = "woman", since = "zaselek"), Villager(id = "tine", name = "Tine", art = "man", since = "zaselek"))
        val d = AgeUps.digest(Age.ZASELEK, cast = cast)
        assertEquals(AgeUps.TOP, d.top.size)
        // the buildings the hamlet opens as one row, then its own new thing (the school), then who can move in, as one
        assertEquals(NewThing.Go.BUILD, d.top[0].go)
        assertTrue(d.top[0].text, d.top[0].text.startsWith("Nove stavbe: ") && d.top[0].text.contains("čebelnjak") && d.top[0].text.contains(" · New buildings: "))
        assertEquals(NewThing.Go.SCHOOL, d.top[1].go)
        assertEquals("Lahko se priselita: Ana, Tine · Can move in: Ana, Tine", d.top[2].text)
        // the projects as one row, folded with the rest
        val projects = d.more.filter { it.go == NewThing.Go.PROJECTS }
        assertEquals(1, projects.size)
        assertTrue(projects.single().text, projects.single().text.startsWith("3 skupni projekti: "))
        // nothing lost: every thing of the full list is in it, the grouped ones as their group
        val all = AgeUps.whatsNew(Age.ZASELEK, cast = cast)
        val kept = d.top + d.more
        for (n in all) assertTrue(n.text, n in kept || n.go == NewThing.Go.PROJECTS || n.go == NewThing.Go.PERSON || (n.go == NewThing.Go.BUILD && n.arg != null))
        // an age without people or buildings of its own still says its three
        assertTrue(AgeUps.digest(Age.MESTO).top.isNotEmpty())
    }

    // --- the party --------------------------------------------------------------------------------------------------

    @Test fun `the party is on the evening the age came and the next, until it is had`() {
        withParties()
        // the morning it came: tonight
        assertNull(AgeUps.partyNow(vas, at(day, 10)))
        assertEquals(Age.VAS, AgeUps.partyTonight(vas, at(day, 10))?.age)
        // the evening, the night through
        assertEquals(Age.VAS, AgeUps.partyNow(vas, at(day, 17))?.age)
        assertNull(AgeUps.partyTonight(vas, at(day, 17)))
        assertEquals(Age.VAS, AgeUps.partyNow(vas, at(day.plusDays(1), 3))?.age)
        assertTrue(AgeUps.gathering(vas, at(day, 20)))
        assertFalse(AgeUps.gathering(vas, at(day, 12)))
        // missed it: the next evening too
        assertNull(AgeUps.partyNow(vas, at(day.plusDays(1), 12)))
        assertEquals(Age.VAS, AgeUps.partyTonight(vas, at(day.plusDays(1), 12))?.age)
        assertEquals(Age.VAS, AgeUps.partyNow(vas, at(day.plusDays(1), 21))?.age)
        // and no more
        assertNull(AgeUps.partyNow(vas, at(day.plusDays(2), 19)))
        assertNull(AgeUps.partyTonight(vas, at(day.plusDays(2), 10)))
        // grown late in the evening: at once
        val late = vas.copy(grown = mapOf("vas" to day.toString()))
        assertNotNull(AgeUps.partyNow(late, at(day, 22, 30)))
        // had: no more bubble, the village still gathers that evening
        val (had, first) = AgeUps.partyHad(vas, Age.VAS, day, t0)
        assertTrue(first)
        assertNull(AgeUps.partyNow(had, at(day, 19)))
        assertNull(AgeUps.partyTonight(had, at(day, 10)))
        assertTrue(AgeUps.gathering(had, at(day, 19)))
        // a save from before (no day kept): no party evening, the chronicle plays it again
        assertNull(AgeUps.partyNow(vas.copy(grown = emptyMap()), at(day, 19)))
    }

    @Test fun `the party pays once, while it is the village's age`() {
        withParties()
        val (had, first) = AgeUps.partyHad(vas, Age.VAS, day, t0)
        assertTrue(first)
        assertEquals(70, had.morale)
        assertEquals(day.toString(), had.parties["vas"])
        assertEquals("🪗" to "Plesali smo polko. · We danced the polka.", had.log.last().emoji to had.log.last().text)
        for (id in listOf("vida", "marko", "nejc")) {
            assertEquals(AgeUps.PARTY_POINTS, had.bonds.getValue(id).points)
            assertEquals("našo veselico", had.bonds.getValue(id).memories.last().sl)
        }
        assertEquals(Catalog.HELP_HAPPENING, had.help)
        // again: nothing
        val (again, second) = AgeUps.partyHad(had, Age.VAS, day.plusDays(3), t0)
        assertFalse(second)
        assertSame(had, again)
        // an earlier age's, played again from the chronicle: nothing
        val (older, paid) = AgeUps.partyHad(vas, Age.ZASELEK, day, t0)
        assertFalse(paid)
        assertSame(vas, older)
    }

    @Test fun `the decorations stay the day the age came and the next`() {
        assertEquals(Age.VAS, AgeUps.decorOn(vas, day)?.age)
        assertEquals(Age.VAS, AgeUps.decorOn(vas, day.plusDays(1))?.age)
        assertNull(AgeUps.decorOn(vas, day.plusDays(2)))
        assertNull(AgeUps.decorOn(vas, day.minusDays(1)))
        assertNull(AgeUps.decorOn(vas.copy(grown = emptyMap()), day))
    }

    // --- the chronicle --------------------------------------------------------------------------------------------

    @Test fun `growing keeps the day, and the chronicle's line says it is an age's`() {
        val tent = Building("t", BuildingType.TENT, 0)
        val s = GameState(resources = mapOf(Res.FOOD to 50, Res.WOOD to 50), buildings = listOf(tent), lastTick = day0.toString())
        val grown = GameEngine.advance(s, 20, t0)
        assertEquals(Age.TABOR, grown.age)
        val on = java.time.Instant.ofEpochMilli(t0).atZone(zone).toLocalDate()
        assertEquals(on.toString(), grown.grown["tabor"])
        assertEquals(on, AgeUps.reachedOn(grown, Age.TABOR))
        assertEquals(Age.TABOR, AgeUps.ageOf(grown.log.last { it.emoji == Age.TABOR.emoji }))
        // other lines are no age's
        assertNull(AgeUps.ageOf(LogEntry(t0, "🔨", "Zgrajeno: Šotor")))
        assertNull(AgeUps.ageOf(LogEntry(t0, Age.VAS.emoji, "Nekaj drugega")))
    }

    @Test fun `the chronicle has every age reached to watch again, also one from before the celebrations`() {
        // a save from before: no days kept; Vas's line still in the chronicle, Zaselek's and Tabor's long gone
        val line = Cultures.current.chronicle.newAge.bi("age" to Age.VAS.names)
        val then = noon(day)
        val old = GameState(age = Age.VAS, log = listOf(LogEntry(then, Age.VAS.emoji, line)))
        val ms = AgeUps.milestones(old)
        assertEquals(listOf(Age.VAS, Age.ZASELEK, Age.TABOR), ms.map { it.age })
        assertEquals(java.time.Instant.ofEpochMilli(then).atZone(zone).toLocalDate(), ms[0].on)
        assertNull(ms[1].on)
        assertNull(ms[2].on)
        assertTrue(AgeUps.milestones(GameState()).isEmpty())
        // a kept day wins
        assertEquals(LocalDate.of(2026, 9, 1), AgeUps.reachedOn(old.copy(grown = mapOf("zaselek" to "2026-09-01")), Age.ZASELEK))
        assertNull(AgeUps.reachedOn(old, Age.TRG))
    }

    @Test fun `a moment played again shows the village as it was the day it grew`() {
        val zaselekDay = LocalDate.of(2026, 9, 1)
        val before = zaselekDay.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val after = zaselekDay.plusDays(5).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val s = GameState(
            age = Age.VAS, grown = mapOf("zaselek" to zaselekDay.toString(), "vas" to day.toString()),
            buildings = listOf(
                Building("hut", BuildingType.HUT, 2, level = 4, builtAt = before), Building("church", BuildingType.CHURCH, 3, builtAt = after),
                Building("far", BuildingType.HOUSE, 12, builtAt = 0), Building("p", BuildingType.PALISADE, NO_PLOT, builtAt = before),
                Building("market", BuildingType.MARKET, 4, builtAt = 0),
            ),
            projects = mapOf("mlaj" to 5, "kapelica" to 2),
        )
        val then = AgeUps.asItWas(s, Age.ZASELEK, zone)
        assertEquals(Age.ZASELEK, then.age)
        // the church came later, the house stands on a plot of a later age, the market is a later age's
        assertEquals(setOf("hut", "p"), then.buildings.map { it.id }.toSet())
        assertEquals(3, then.buildings.first { it.id == "hut" }.level)
        assertEquals(mapOf("mlaj" to 5), then.projects)
        // the village's own age: as it is
        assertSame(s, AgeUps.asItWas(s, Age.VAS, zone))
    }

    // --- who says the party's lines -----------------------------------------------------------------------------

    @Test fun `the party's lines are said by who is here tonight`() {
        withParties()
        val c = AgeUps.celebration(Age.VAS)
        val d = AgeUps.dialog(c, "A1", "sl")!!
        assertEquals("party:vas", d.id)
        val people = listOf(
            Villager(id = "vida", name = "Vida", art = "innkeeper"), Villager(id = "marko", name = "Marko", art = "winemaker"),
            Villager(id = "nejc", name = "Nejc", art = "child1"), Villager(id = "zala", name = "Zala", art = "child2"),
            Villager(id = "micka", name = "Micka", art = "grandma"),
        )
        // everyone here: their own lines
        assertEquals(mapOf("vida" to "vida", "marko" to "marko", "nejc" to "nejc"), AgeUps.cast(c, d, null, people))
        // the host away: a guest who is here hosts; a child away: another child
        val cast = AgeUps.cast(c, d, setOf("marko", "zala", "micka"), people)
        assertEquals("marko", cast["marko"])
        assertEquals("zala", cast["nejc"])
        assertTrue(cast["vida"] in setOf("micka"))
        // the stage: who speaks first, then everyone else who lives here and is here
        assertEquals(listOf("vida", "marko", "nejc", "micka"), AgeUps.crowd(listOf("vida", "marko", "nejc"), null, listOf("marko", "micka", "vida")))
        assertEquals(listOf("vida", "zala"), AgeUps.crowd(listOf("vida"), setOf("zala"), listOf("zala", "micka")))
        // at A2 the A2 dialog, past it the highest
        assertEquals("Na zdravje vsem!", AgeUps.dialog(c, "B1", "sl")!!.lines.first().sl)
    }

    private companion object {
        val LEVELS = """
            {"A1": {"lines": [
              {"who": "vida", "sl": "Dobrodošli na veselici!", "en": "Welcome to the party!"},
              {"who": "marko", "sl": "Na zdravje!", "en": "Cheers!"},
              {"choices": [
                {"sl": "Na zdravje!", "en": "Cheers!", "ok": true, "reply": {"sl": "Živijo!", "en": "Hurray!"}},
                {"sl": "Lahko noč!", "en": "Good night!", "why": {"en": "A toast: na zdravje."}, "reply": {"sl": "Že greš spat?", "en": "Off to bed already?"}}
              ]},
              {"who": "nejc", "sl": "Plešimo!", "en": "Let's dance!"}
            ]},
            "A2": {"lines": [
              {"who": "vida", "sl": "Na zdravje vsem!", "en": "Cheers, everyone!"},
              {"choices": [
                {"sl": "Na zdravje!", "en": "Cheers!", "ok": true},
                {"sl": "Dober tek!", "en": "Enjoy your meal!", "why": {"en": "A toast: na zdravje."}, "reply": {"sl": "Saj ne jemo!", "en": "We're not eating!"}}
              ]},
              {"who": "marko", "sl": "Zaigraj polko!", "en": "Play a polka!"},
              {"who": "nejc", "sl": "Juhu!", "en": "Yippee!"}
            ]}}
        """.trimIndent()
    }
}
