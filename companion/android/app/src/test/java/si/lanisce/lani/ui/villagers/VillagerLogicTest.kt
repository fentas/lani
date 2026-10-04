package si.lanisce.lani.ui.villagers

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.villagers.Arrival
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Visit
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.VillagerLines
import si.lanisce.lani.game.villagers.parseVillagers
import java.io.File
import java.time.LocalDate

class VillagerLogicTest {
    private val day = LocalDate.of(2026, 9, 24)
    private val cast = parseVillagers(File("src/debug/assets/villagers-fixture.json").readText())
    private val micka = cast.first { it.id == "micka" }
    private val luka = cast.first { it.id == "luka" }
    private val tone = cast.first { it.id == "tone" }

    private fun quest(giver: String, title: String, done: Boolean = false) =
        Quest("q-$title", giver, "🙂", title, "Zgodba · Story", Res.FOOD, mapOf(Res.FOOD to 30), done = done)

    private fun obj(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject

    // --- the debug cast --------------------------------------------------------------------------

    @Test fun `the debug cast follows the format`() {
        assertEquals(listOf("micka", "luka", "tone"), cast.map { it.id })
        for (v in cast) {
            assertTrue(v.id, Regex("[a-z0-9-]+").matches(v.id))
            assertTrue(v.id, v.voice == "female" || v.voice == "male")
            assertTrue(v.id, v.register == "ti" || v.register == "vi")
            val l = v.lines
            for ((name, pool) in listOf("greet" to l.greet, "thanks" to l.thanks, "remember" to l.remember, "cheer" to l.cheer, "comfort" to l.comfort, "listen" to l.listen, "idle" to l.idle, "bye" to l.bye)) {
                assertTrue("${v.id} $name: 2–6 lines", pool.size in 2..6)
                assertTrue("${v.id} $name: English", pool.all { it.base.isNotBlank() })
            }
            assertTrue("${v.id} greets strangers", l.greet.any { it.level == 0 })
            assertTrue("${v.id} remembers", l.remember.all { "{memory}" in it.target && "{memory}" in it.base })
        }
        assertEquals(Age.ZASELEK, VillagerLogic.since(tone))
    }

    // --- lines -------------------------------------------------------------------------------------

    @Test fun `they greet with the warmest line their friendship allows`() {
        assertEquals("Dober dan!", VillagerLogic.greet(luka, 0, day).sl)
        assertEquals("Ej, Jan! Kako si?", VillagerLogic.greet(luka, 1, day).sl)
        assertEquals("Jan, prijatelj! Greva na planino?", VillagerLogic.greet(luka, 4, day).sl) // like family: the warmest there is
        // someone without greetings says a plain one for the time of day
        val nobody = Villager(id = "x", name = "X", art = "child1")
        assertEquals("Dober dan!", VillagerLogic.greet(nobody, 3, day, TimeOfDay.MORNING).sl)
        assertEquals("Dober dan!", VillagerLogic.greet(nobody, 3, day, TimeOfDay.AFTERNOON).sl)
        assertEquals("Dober večer!", VillagerLogic.greet(nobody, 3, day, TimeOfDay.EVENING).sl)
        assertEquals("Dober večer!" to "Good evening!", VillagerLogic.greet(nobody, 3, day, TimeOfDay.NIGHT).let { it.sl to it.en })
    }

    @Test fun `among equally warm lines the day picks, the same all day`() {
        val v = Villager(
            id = "ana", name = "Teta Ana", art = "aunt",
            lines = VillagerLines(greet = listOf(VillagerLine("Živjo!", "Hi!", 1), VillagerLine("Oj!", "Hey!", 1), VillagerLine("Dober dan.", "Good day.", 0))),
        )
        assertEquals(VillagerLogic.greet(v, 1, day), VillagerLogic.greet(v, 1, day))
        val month = (0L until 30L).map { VillagerLogic.greet(v, 1, day.plusDays(it)).sl }.toSet()
        assertEquals(setOf("Živjo!", "Oj!"), month) // both come up; the colder line never does
    }

    @Test fun `a remember line brings up a memory in both languages`() {
        val m = Memory(day.toString(), "ovco, ki sva jo našla v gozdu", "the sheep we found in the forest", "quest")
        val said = VillagerLogic.remember(luka, 1, listOf(m), day)
        assertEquals(Said("Še vedno mislim na ovco, ki sva jo našla v gozdu.", "I still think of the sheep we found in the forest."), said)
        assertNull(VillagerLogic.remember(luka, 1, emptyList(), day)) // nothing to remember
        assertNull(VillagerLogic.remember(tone, 1, listOf(m), day)) // Tone shares memories from Friend on
        // A memory without English keeps its Slovene in the English line.
        val bare = VillagerLogic.fill(VillagerLine("Hvala še enkrat za {memory}.", "Thanks again for {memory}."), m.copy(en = ""))
        assertEquals("Thanks again for ovco, ki sva jo našla v gozdu.", bare.en)
    }

    @Test fun `the freshest memory is on their mind, older ones come up by the day`() {
        val old = Memory("2026-08-01", "juho ob ognju", "the soup by the fire")
        val older = Memory("2026-07-01", "prvi sneg", "the first snow")
        val fresh = Memory(day.minusDays(1).toString(), "nedeljsko kosilo", "Sunday lunch")
        assertEquals(fresh, VillagerLogic.memoryFor(listOf(old, fresh), day))
        val picks = (0L until 30L).mapNotNull { VillagerLogic.memoryFor(listOf(older, old), day.plusDays(it)) }.toSet()
        assertEquals(setOf(old, older), picks)
    }

    @Test fun `a villager without remember lines still remembers, from Acquaintance on`() {
        val v = Villager(id = "x", name = "Mlinar France", art = "farmer", voice = "male")
        val m = Memory(day.toString(), "žetev", "the harvest")
        assertNull(VillagerLogic.remember(v, 0, listOf(m), day))
        assertEquals("Še vedno mislim na žetev.", VillagerLogic.remember(v, 1, listOf(m), day)?.sl)
    }

    @Test fun `the greeting is a hello, then a memory when there is one to share`() {
        assertEquals(listOf(Said("Dober dan! Kdo pa si ti?", "Good day! And who are you?")), VillagerLogic.greeting(micka, Bond(), day))
        val m = Memory(day.toString(), "potico, ki sva jo spekla skupaj", "the potica we baked together", "quest")
        val lines = VillagerLogic.greeting(micka, Bond(points = 12, memories = listOf(m)), day)
        assertEquals(listOf("Oh, Jan! Pridi naprej.", "Še vedno mislim na potico, ki sva jo spekla skupaj."), lines.map { it.sl })
    }

    // --- the register ------------------------------------------------------------------------------

    private val ana = Resident("n-ana-furlan", "2026-08-01", name = "Ana Furlan", emoji = "👩", art = "woman", voice = "female", role = "Tkalka · Weaver", family = "Furlan")
    private val jure = Resident("n-jure-furlan", "2026-08-10", name = "Jure Furlan", emoji = "👨", art = "man", voice = "male", role = "Drvar · Woodcutter", family = "Furlan")
    private val neza = Resident(
        "n-neza-furlan", "2026-09-01", name = "Neža Furlan", emoji = "👶", art = "baby", voice = "female", role = "Otrok · Child",
        born = "2026-09-01", family = "Furlan", parents = listOf("n-ana-furlan", "n-jure-furlan"),
    )
    private val village = GameState(
        age = Age.TABOR, villagers = 5,
        residents = listOf(Resident("micka", "2026-07-01"), ana, Resident("luka", "2026-07-02"), neza, jure),
    )

    @Test fun `the register lists who lives here, friends first, families together`() {
        val s = village.copy(bonds = mapOf("luka" to Bond(points = 12, met = "2026-09-20"), "n-jure-furlan" to Bond(points = 3, met = "2026-09-21")))
        val r = VillagerLogic.register(cast, s, needs = setOf("micka"), today = day)
        assertEquals(
            listOf(listOf("luka"), listOf("n-ana-furlan", "n-jure-furlan", "n-neza-furlan"), listOf("micka")),
            r.here.map { u -> u.map { it.villager.id } },
        )
        assertTrue(r.here.flatten().all { it.livesHere })
        assertEquals("Dojenčica · Baby", r.here[1][2].villager.role) // the child's age stage
        // who doesn't live here yet isn't listed (met as they come), only counted
        assertTrue(r.notYet.isEmpty())
        assertEquals(1, r.toCome)

        // Equal friendship: who needs Jan first, else who came first.
        val even = VillagerLogic.register(cast, village, needs = setOf("luka"), today = day)
        assertEquals(listOf("luka", "micka", "n-ana-furlan"), even.here.map { it.first().villager.id })
    }

    @Test fun `today's visitor is listed among those who don't live here`() {
        val s = village.copy(visitor = Visit("tone", day.toString()), quests = listOf(quest("Kovač Tone", "Stari plug · The old plough")))
        val r = VillagerLogic.register(cast, s, needs = VillagerLogic.needing(cast, s.quests), today = day)
        val tone = r.notYet.single()
        assertEquals("tone", tone.villager.id)
        assertFalse(tone.silhouette)
        assertTrue(tone.needs)
        assertEquals("🧳 danes na obisku · visiting today", tone.why)
        assertEquals(0, r.toCome)
        // yesterday's visit is over
        assertTrue(VillagerLogic.register(cast, s, emptySet(), day.plusDays(1)).notYet.isEmpty())
    }

    @Test fun `why someone doesn't live here yet`() {
        val mojca = Villager(id = "mojca", name = "Učiteljica Mojca", art = "teacher", home = listOf("school"), since = "vas")
        val vas = GameState(age = Age.VAS, villagers = 2)
        assertEquals("Pride, ko zgradiš 🏫 šolo · Comes when you build the 🏫 school", VillagerLogic.whyNotHere(mojca, vas).text)
        assertEquals("Pride v dobi 🏘️ Vas · Comes in the Village age", VillagerLogic.whyNotHere(mojca, vas.copy(age = Age.TABOR)).text)
        assertTrue(VillagerLogic.whyNotHere(mojca, vas.copy(age = Age.TABOR)).later)
        assertTrue(VillagerLogic.whyNotHere(luka, GameState(villagers = 2)).text.startsWith("🏠 Čaka na prostor")) // two live by the fire, and there's no tent
        assertTrue(VillagerLogic.whyNotHere(luka, GameState(villagers = 1)).text.startsWith("🌾 Kmalu pride"))
        assertEquals("Pride, ko posadiš 🌳 lipo · Comes when you plant the 🌳 linden tree", VillagerLogic.whenBuilt(BuildingType.LIPA))
    }

    @Test fun `who's coming, and what they wait for`() {
        val room = "prostor · room"
        assertEquals(
            "⚒️ Kovač Tone se bo preselil v vas · Kovač Tone is moving in next\n🏠 Ni prostora: zgradi šotor, kočo ali hišo · No room: build a tent, a hut or a house",
            VillagerLogic.coming(listOf(Arrival(Arrival.Kind.CAST, "Kovač Tone", "⚒️", room)), cast),
        )
        assertTrue(VillagerLogic.coming(listOf(Arrival(Arrival.Kind.CAST, "Babica Micka", "👵")), cast)!!.startsWith("👵 Babica Micka se bo preselila v vas"))
        assertTrue(VillagerLogic.coming(listOf(Arrival(Arrival.Kind.BIRTH, "Furlan", "🍼", room)), cast)!!.startsWith("🍼 Pri Furlanovih pričakujejo otroka"))
        assertTrue(VillagerLogic.coming(listOf(Arrival(Arrival.Kind.NEWCOMER, "", "🧳")), cast)!!.contains("nekdo nov"))
        assertNull(VillagerLogic.coming(emptyList(), cast))
    }

    @Test fun `the tutor hears who moved in or was born, not the cast`() {
        val (text, data) = VillagerLogic.arrival(ana)!!
        assertEquals("V vas se je priselila Ana Furlan, tkalka · Ana Furlan moved into the village", text)
        assertEquals(
            obj("""{"id":"n-ana-furlan","name":"Ana Furlan","role":"Tkalka · Weaver","family":"Furlan","kind":"newcomer","art":"woman","voice":"female"}"""),
            data,
        )
        val (born, birth) = VillagerLogic.arrival(neza)!!
        assertEquals("Pri Furlanovih se je rodila Neža · Neža was born in the village", born)
        assertEquals("birth", (birth["kind"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals(2, (birth["parents"] as kotlinx.serialization.json.JsonArray).size)
        assertNull(VillagerLogic.arrival(Resident("micka", "2026-07-01"))) // the cast is known to the tutor
    }

    @Test fun `families and children by name`() {
        assertEquals("Furlanovi · The Furlan family", VillagerLogic.familyName("Furlan"))
        assertEquals("Mozetičevi · The Mozetič family", VillagerLogic.familyName("Mozetič"))
        assertEquals("Rutarjevi · The Rutar family", VillagerLogic.familyName("Rutar"))
        assertEquals("🍼 Rojena 1. 9. · Born on 1 September", VillagerLogic.born(neza))
        assertNull(VillagerLogic.born(ana))
        assertTrue(VillagerLogic.grownUp(ana, day))
        assertFalse(VillagerLogic.grownUp(neza, day))
    }

    @Test fun `who needs Jan - an open request, or a happening with them now`() {
        val quests = listOf(quest("Babica Micka", "Mickina kuhinja · Micka's kitchen"), quest("Pastir Luka", "Lukovi klici · Luka's calls", done = true))
        assertEquals(setOf("micka"), VillagerLogic.needing(cast, quests))
        assertEquals(setOf("micka", "tone"), VillagerLogic.needing(cast, quests, busy = setOf("tone")))
    }

    @Test fun `they speak of Jan in their gender's words`() {
        assertEquals("Še je ne poznaš · You haven't met yet", VillagerLogic.notMet(micka))
        assertEquals("Še ga ne poznaš · You haven't met yet", VillagerLogic.notMet(luka))
        assertEquals("Znanka · Acquaintance", VillagerLogic.levelName(1, female = true))
        assertEquals("Dober prijatelj · Good friend", VillagerLogic.levelName(3, female = false))
        assertEquals("Kot družina · Like family", VillagerLogic.levelName(99, female = true))
        assertEquals(12 to 30, VillagerLogic.toNext(12))
        assertNull(VillagerLogic.toNext(150))
        assertEquals("24. 9.", VillagerLogic.shortDate("2026-09-24"))
        assertEquals("24 September", VillagerLogic.enDate("2026-09-24"))
    }

    // --- friendship ----------------------------------------------------------------------------------

    @Test fun `a gain knows the level it reached`() {
        val before = GameState(bonds = mapOf("micka" to Bond(points = 7)))
        val after = Bonds.add(before, "micka", Bonds.QUEST, day)
        val g = VillagerLogic.gain("micka", micka, before, after)!!
        assertEquals(10, g.points)
        assertEquals(17, g.after)
        assertTrue(g.leveledUp)
        assertEquals("+10 ♥", g.plus)
        assertEquals("♥ Micka: Znanka · Acquaintance", g.levelText)
        assertTrue(g.firstMeeting)
        val again = VillagerLogic.gain("micka", micka, after, Bonds.add(after, "micka", Bonds.TALK, day))!!
        assertFalse(again.firstMeeting)
        assertFalse(again.leveledUp)

        val luka1 = VillagerLogic.gain("luka", luka, GameState(), Bonds.add(GameState(), "luka", 10, day))!!
        assertEquals("♥ Luka: Znanec · Acquaintance", luka1.levelText)
        val small = VillagerLogic.gain("luka", luka, GameState(), Bonds.add(GameState(), "luka", 5, day))!!
        assertFalse(small.leveledUp)
        assertNull(VillagerLogic.gain("luka", luka, GameState(), null)) // no village loaded
    }

    @Test fun `a done quest stays in the giver's words`() {
        val lost = quest("Pastir Luka", "Izgubljena ovca · The lost sheep")
        val m = VillagerLogic.questMemory(lost, luka, day)
        assertEquals(Memory("2026-09-24", "ovco, ki sva jo našla v gozdu", "the sheep we found in the forest", "quest"), m)
        assertEquals("Še vedno mislim na ovco, ki sva jo našla v gozdu.", VillagerLogic.fill(luka.lines.remember.first(), m).sl)

        // A tutor's quest: the day Jan helped, in the giver's ti or vi.
        val tutor = Quest("tutor-x-v1", "Kovač Tone", "⚒️", "Naročilo za Gorico · An order for Gorizia", "", Res.STONE, emptyMap(), QuestSource.TUTOR)
        assertEquals("dan, ko ste mi pomagali: „Naročilo za Gorico“", VillagerLogic.questMemory(tutor, tone, day).sl)
        assertEquals("the day you helped me: “An order for Gorizia”", VillagerLogic.questMemory(tutor, tone, day).en)
        assertEquals("dan, ko si mi pomagal: „Naročilo za Gorico“", VillagerLogic.questMemory(tutor.copy(giver = "Pastir Luka"), luka, day).sl)
        // A title without an English half is used as it is.
        assertEquals("the day you helped me: “Pogovor”", VillagerLogic.questMemory(tutor.copy(title = "Pogovor"), luka, day).en)
    }

    @Test fun `the debrief's memory is read from the tutor's data`() {
        val m = VillagerLogic.memoryOf(obj("""{"debrief": true, "memory": {"sl": " najin pogovor o burji ", "en": "our talk about the burja"}}"""), day)
        assertEquals(Memory("2026-09-24", "najin pogovor o burji", "our talk about the burja", "talk"), m)
        assertEquals("tvojo zgodbo", VillagerLogic.memoryOf(obj("""{"memory": "tvojo zgodbo"}"""), day)?.sl)
        assertNull(VillagerLogic.memoryOf(obj("""{"debrief": true}"""), day))
        assertNull(VillagerLogic.memoryOf(obj("""{"memory": {"sl": " ", "en": "x"}}"""), day))
        assertNull(VillagerLogic.memoryOf(null, day))
    }

    @Test fun `a villager role-play is known by its scenario id`() {
        assertEquals("luka", VillagerLogic.villagerOfScenario("villager:luka"))
        assertNull(VillagerLogic.villagerOfScenario("villager:"))
        assertNull(VillagerLogic.villagerOfScenario("v-pekarni"))

        val s = VillagerLogic.localScenario(tone, Bond(points = 12), day)
        assertEquals("villager:tone", s.id)
        assertEquals("A, vi ste, Jan. Dober dan.", s.openerSl)
        assertEquals("male", s.characterVoice)
        assertEquals("Kovač Tone", s.character)
        assertEquals(4, s.goals.size)
        assertTrue(s.goals.first().contains("vikaj"))
    }

    @Test fun `short names and voices`() {
        assertEquals("Micka", VillagerLogic.shortName("Babica Micka"))
        assertEquals("Vida", VillagerLogic.shortName("Gostilničarka Vida"))
        assertEquals("Tone", VillagerLogic.shortName("Tone"))
        assertEquals("female", VillagerLogic.voiceOf(micka))
        assertEquals("male", VillagerLogic.voiceOf(tone))
        assertNotNull(VillagerLogic.idle(luka, 0, day))
    }
}
