package si.lanisce.lani.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.app.VisitController
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.DaySky
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.bi
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * Visiting a linked town (data/Visits.kt, app/VisitController.kt): the public state as a read-only village, the pair and
 * culture a visit shows and how they come back, and a town that isn't answering.
 */
class VisitsTest {
    private val today = LocalDate.of(2026, 9, 26)
    private val mia = "fec16ecd34869e522e575d0fb9ba45de"
    private val own = L10n.pair

    /** Mia's Italian town as her bridge shows it (GET /towns/:id/public: towns.ts publicState, cleaned by the visitor's bridge). */
    private val public = """
        {"v":1,"town":{"id":"$mia","name":"Il mio villaggio","learner":"Mia","culture":"friuli","language":"it"},
         "date":"2026-09-26","age":"VAS","founded_on":"2026-08-01","villagers":4,
         "buildings":[{"type":"TENT","plot":-1,"level":1,"damaged":false},{"type":"HUT","plot":1,"level":2,"damaged":false},
           {"type":"FIELD","plot":2,"level":1,"damaged":true},{"type":"CASTLE","plot":3,"level":1,"damaged":false},
           {"type":"WELL","plot":2,"level":1,"damaged":false},{"type":"HOUSE","plot":99,"level":1,"damaged":false},
           {"type":"PALISADE","plot":4,"level":1,"damaged":false}],
         "projects":{"mlaj":3,"mulino":2,"vinograd":99},
         "residents":[{"id":"rosa","name":"Nonna Rosa","stage":"adult"},{"id":"davide","name":"Pastore Davide","stage":"adult"},
           {"id":"n-chiara-furlan","name":"Chiara Furlan","stage":"child","emoji":"👧","art":"child2"},
           {"id":"n-marco-furlan","name":"Marco Furlan","stage":"adult","emoji":"👨","art":"man"}],
         "visitor":{"id":"bepi","name":"Nonno Bepi"},
         "sky":{"scene":"field","rain":0.6,"snow":0,"fog":0,"wind":0.3,"gloom":0,"lightning":true},
         "festival":{"id":"vendemmia","emoji":"🍇","name":{"it":"Vendemmia","sl":"Trgatev","en":"Grape harvest"},"day":"2026-09-26","late":0,"done":true},
         "fetched_at":"2026-09-26T09:30:00.000Z"}
    """.trimIndent()

    /** The friuli cast, as GET /towns/:id/villagers passes it on ({town, culture, language, villagers, fetched_at}). */
    private val cast: String by lazy {
        val dir = listOf(File("../../cultures/friuli/villagers"), File("../cultures/friuli/villagers")).first { it.isDirectory }
        val list = dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.joinToString(",", "[", "]") { it.readText() }
        """{"v":1,"town":"$mia","culture":"friuli","language":"it","villagers":$list,"fetched_at":"2026-09-26T09:30:00.000Z"}"""
    }

    /** The friuli sea, as GET /towns/:id/scenes passes it on (resolved: its objects carry their words). */
    private val scenes = """
        {"v":1,"town":"$mia","culture":"friuli","language":"it","fetched_at":"2026-09-26T09:30:00.000Z","scenes":[
          {"schema":"lani.scene/v0","id":"il-mare","language":"it","title":{"it":"Il mare","sl":"Morje","en":"The sea"},"emoji":"🌊","art":"sea",
           "from":["spot:horizon"],"pack":"il-mare","culture":"friuli","source":"curated",
           "objects":[{"slot":"sea","word":"mare","pack":"il-mare","it":"il mare","sl":"morje","en":"the sea","emoji":"🌊","gender":"m"}]}]}
    """.trimIndent()

    @Before fun home() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        VisitWorld.leave()
    }

    @After fun back() {
        VisitWorld.leave()
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = own
    }

    // --- the read-only village -----------------------------------------------------------------------------------

    @Test fun `the public state becomes a village to look at, its age, buildings on their plots, landmarks, sky and festival`() {
        VisitWorld.enter("it", "friuli")
        val s = Visits.gameState(Visits.parsePublic(public), castIds = setOf("rosa", "davide", "bepi"), today = today)
        assertEquals(Age.VAS, s.age)
        // a type this app doesn't know, a second building on a plot and a plot the age hasn't got are left out; the palisade
        // stands round the clearing whatever plot it came with
        assertEquals(
            listOf(BuildingType.TENT to NO_PLOT, BuildingType.HUT to 1, BuildingType.FIELD to 2, BuildingType.PALISADE to NO_PLOT),
            s.buildings.map { it.type to it.plot },
        )
        assertEquals(2, s.buildings.single { it.type == BuildingType.HUT }.level)
        assertTrue(s.buildings.single { it.type == BuildingType.FIELD }.damaged)
        assertEquals(s.buildings.map { it.id }.distinct().size, s.buildings.size)
        // the landmarks of the culture's projects, no more steps than a project has
        assertEquals(mapOf("mlaj" to 3, "vinograd" to si.lanisce.lani.game.Projects.spec("vinograd")!!.steps.size), s.projects)
        assertEquals(4, s.villagers)
        assertEquals("2026-08-01", s.foundedOn)
        // today's sky over the village (the visitor's today), and the festival they celebrated
        assertEquals(0.6f, DaySky.of(s, today).rain)
        assertTrue(DaySky.of(s, today).lightning)
        assertEquals(mapOf("vendemmia" to "2026-09-26"), s.festivals)
        // what the public state doesn't say is a new village's: no stores, requests, chest, friendships or chronicle
        assertTrue(s.resources.isEmpty() && s.quests.isEmpty() && s.log.isEmpty() && s.bonds.isEmpty() && s.chest.goods.isEmpty() && s.help == 0)
        assertEquals(Visits.seedOf(mia), s.seed)
    }

    @Test fun `the plots the learner chose show the same on a visit`() {
        // the host's village: fields built where the learner chose (side by side on 1, 9 and 10), a hut moved, a swap
        var host = si.lanisce.lani.game.GameState(
            seed = 1, age = Age.VAS, resources = si.lanisce.lani.game.Res.entries.associateWith { 5000 },
            buildings = listOf(
                si.lanisce.lani.game.Building("tent-1", BuildingType.TENT, 0), si.lanisce.lani.game.Building("field-2", BuildingType.FIELD, 1),
                si.lanisce.lani.game.Building("hut-3", BuildingType.HUT, 2, level = 2), si.lanisce.lani.game.Building("well-4", BuildingType.WELL, 3),
                si.lanisce.lani.game.Building("palisade-5", BuildingType.PALISADE, NO_PLOT),
            ),
        )
        for (p in listOf(9, 10)) host = si.lanisce.lani.game.GameEngine.build(host, BuildingType.FIELD, 0, plot = p)
        host = si.lanisce.lani.game.GameEngine.move(host, "hut-3", 12, 0)
        host = si.lanisce.lani.game.GameEngine.move(host, "well-4", 0, 0)
        // what the host's bridge sends (towns.ts publicState: each building's type, plot, level and damage, nothing more)
        val buildings = host.buildings.joinToString(",", "[", "]") { b ->
            """{"type":"${b.type.name}","plot":${b.plot},"level":${b.level},"damaged":${b.damaged}}"""
        }
        val json = """{"v":1,"town":{"id":"$mia","name":"Il mio villaggio","learner":"Mia","culture":"friuli","language":"it"},"date":"2026-09-26","age":"VAS","villagers":4,"buildings":$buildings}"""
        VisitWorld.enter("it", "friuli")
        val seen = Visits.gameState(Visits.parsePublic(json), castIds = emptySet(), today = today)
        // every building on the plot it stands on at home, at its level (this app and an older one read it alike)
        assertEquals(host.buildings.map { Triple(it.type, it.plot, it.level) }, seen.buildings.map { Triple(it.type, it.plot, it.level) })
        assertEquals(listOf(1, 9, 10), seen.buildings.filter { it.type == BuildingType.FIELD }.map { it.plot })
        assertEquals(12, seen.buildings.single { it.type == BuildingType.HUT }.plot)
        assertEquals(listOf(BuildingType.WELL, BuildingType.TENT), listOf(0, 3).map { p -> seen.buildings.single { it.plot == p }.type })
    }

    @Test fun `its people are the host's cast by id, the others as they are, a child at their stage, and today's visitor`() {
        VisitWorld.enter("it", "friuli")
        val s = Visits.gameState(Visits.parsePublic(public), castIds = setOf("rosa", "davide", "bepi"), today = today)
        val rosa = s.residents.single { it.id == "rosa" }
        assertNull("a cast member is looked up in the host's cast", rosa.name)
        val chiara = s.residents.single { it.id == "n-chiara-furlan" }
        assertEquals("Chiara Furlan", chiara.name)
        assertEquals(Residents.Stage.CHILD, Residents.stage(chiara, today))
        assertEquals("female", chiara.voice)
        val marco = s.residents.single { it.id == "n-marco-furlan" }
        assertNull(Residents.stage(marco, today))
        assertEquals("male", marco.voice)
        assertEquals("bepi", s.visitor?.id)
        assertEquals(today.toString(), s.visitor?.on)
        // without the cast (it didn't come), a cast member is drawn as who the town says they are
        val bare = Visits.gameState(Visits.parsePublic(public), castIds = emptySet(), today = today)
        assertEquals("Nonna Rosa", bare.residents.single { it.id == "rosa" }.name)
    }

    @Test fun `the town stands on its own land, as its learner sees it`() {
        VisitWorld.enter("it", "friuli")
        // an older bridge says nothing of the land: the classic valley, as every town looked before
        assertNull(Visits.gameState(Visits.parsePublic(public), emptySet(), today).land)
        val withLand = public.replace("\"fetched_at\"", "\"land\":{\"kind\":\"valley\",\"map\":-4242},\"fetched_at\"")
        val s = Visits.gameState(Visits.parsePublic(withLand), emptySet(), today)
        assertEquals(si.lanisce.lani.game.Land("valley", -4242), s.land)
        // the same map as the host's own village, whatever seed the visit's dice have
        val host = si.lanisce.lani.game.GameState(seed = 1234, land = si.lanisce.lani.game.Land("valley", -4242))
        assertEquals(
            si.lanisce.lani.game.render.VillageLayout.of(host).centers.map { it.toList() },
            si.lanisce.lani.game.render.VillageLayout.of(s).centers.map { it.toList() },
        )
    }

    @Test fun `a town without a village yet is a fire in the woods`() {
        val s = Visits.gameState(PublicTown(town = TownHead(id = mia), age = null), emptySet(), today)
        assertEquals(Age.OGENJ, s.age)
        assertTrue(s.buildings.isEmpty() && s.residents.isEmpty())
    }

    @Test fun `the people and places come in the visit's pair`() {
        VisitWorld.enter("it", "friuli")
        val people = Visits.parseVillagersOf(cast)
        assertEquals(13, people.items.size)
        val rosa = people.items.single { it.id == "rosa" }
        assertTrue(rosa.role, rosa.role.startsWith("Nonna"))
        assertEquals("2026-09-26T09:30:00.000Z", people.fetchedAt)
        assertFalse(people.stale)
        val sea = Visits.parseScenesOf(scenes).items.single()
        assertEquals("Il mare · The sea", sea.title)
        assertEquals("il mare", sea.objects.single().sl)
        assertEquals("the sea", sea.objects.single().en)
    }

    // --- the pair and the culture of a visit -----------------------------------------------------------------------

    @Test fun `a visit speaks the host's language, explained in the visitor's base, and home brings the own pair back`() {
        assertEquals(LangPair(Lang.SL, Lang.EN), L10n.pair)
        VisitWorld.enter("it", "friuli")
        assertTrue(VisitWorld.active)
        assertEquals(LangPair(Lang.IT, Lang.EN), L10n.pair)
        assertEquals("friuli", Cultures.current.id)
        assertEquals("In visita · Visiting", bi("visit.visiting"))
        // the town's names of things are its culture's, in the visit's pair
        assertEquals("Paese", Age.VAS.sl)
        // what sets the learner's own pair meanwhile (the widget, the background check) doesn't end the visit
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        Cultures.use("primorska")
        assertEquals(LangPair(Lang.IT, Lang.EN), L10n.pair)
        assertEquals("friuli", Cultures.current.id)
        VisitWorld.leave()
        assertFalse(VisitWorld.active)
        assertEquals(LangPair(Lang.SL, Lang.EN), L10n.pair)
        assertEquals("primorska", Cultures.current.id)
        assertEquals("Na obisku · Visiting", bi("visit.visiting"))
        assertEquals("Vas", Age.VAS.sl)
    }

    @Test fun `the pair of a visit for each learner`() {
        // Jan (Slovene from English) in Mia's Italian town: Italian · English
        assertEquals(LangPair(Lang.IT, Lang.EN), VisitWorld.pairFor("it", LangPair(Lang.SL, Lang.EN)))
        // the second learner (Italian from Slovene) in Jan's Slovene town: Slovene, explained in Italian (their base is the town's own)
        assertEquals(LangPair(Lang.SL, Lang.IT), VisitWorld.pairFor("sl", LangPair(Lang.IT, Lang.SL)))
        // someone learning Italian from German in another Italian town: as at home
        assertEquals(LangPair(Lang.IT, Lang.DE), VisitWorld.pairFor("it", LangPair(Lang.IT, Lang.DE)))
        // a language the app doesn't have: the own pair
        assertEquals(LangPair(Lang.SL, Lang.EN), VisitWorld.pairFor("xx", LangPair(Lang.SL, Lang.EN)))
        // a culture pack the app doesn't bundle keeps the own look, in the town's language
        VisitWorld.enter("it", "tuscany")
        assertNull(Cultures.visiting)
        assertEquals(Cultures.DEFAULT, Cultures.current.id)
        assertEquals(LangPair(Lang.IT, Lang.EN), L10n.pair)
    }

    // --- a town that isn't answering -----------------------------------------------------------------------------------

    @Test fun `why a town can't be seen says so kindly`() {
        assertEquals("📡 Il mio villaggio ne odgovarja · Il mio villaggio isn't answering", Visits.problem(502, "unreachable", "Il mio villaggio"))
        assertTrue(Visits.problem(502, "refused", "x").startsWith("🔒"))
        assertTrue(Visits.problem(502, "unsupported", "x").startsWith("🧰"))
        assertEquals("🧰 ${bi("towns.tutorTooOld")}", Visits.problem(404, null, "x"))
        assertTrue(Visits.problem(502, "bad_answer", "x").startsWith("⚠️"))
        val broken = Visits.outcome(200, "{not json", "x", Visits::parsePublic)
        assertTrue(broken is VisitFetch.Failed && broken.problem.startsWith("⚠️"))
        val stale = Visits.outcome(200, public.replace("\"fetched_at\"", "\"stale\":true,\"fetched_at\""), "x", Visits::parsePublic, { it.stale }, { it.fetchedAt })
        assertTrue(stale is VisitFetch.Got && stale.stale && stale.fetchedAt == "2026-09-26T09:30:00.000Z")
        assertEquals("09:30", Visits.asOf("2026-09-26T09:30:00.000Z", today, java.time.ZoneOffset.UTC))
        assertEquals("2026-09-25", Visits.asOf("2026-09-25T09:30:00.000Z", today, java.time.ZoneOffset.UTC))
        assertNull(Visits.asOf(null, today))
    }

    /** A bridge that answers from [answers] (kind → status and body), or isn't reachable when [down]. */
    private class FakeBridge(var answers: Map<String, Pair<Int, String>>, var down: Boolean = false) {
        val asked = mutableListOf<String>()
        suspend fun fetch(id: String, kind: String): Pair<Int, String>? {
            asked += "$id/$kind"
            if (down) throw IOException("no route to host")
            return answers[kind]
        }
    }

    private fun controller(b: FakeBridge) = VisitController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), b::fetch)

    private val ok get() = mapOf(Visits.PUBLIC to (200 to public), Visits.VILLAGERS to (200 to cast), Visits.SCENES to (200 to scenes))
    private val away = """{"error":"http://127.0.0.1:8974: Unable to connect","code":"unreachable"}"""

    @Test fun `a visit shows the town, its people and places`() = runBlocking {
        VisitWorld.enter("it", "friuli")
        val b = FakeBridge(ok)
        val c = controller(b)
        c.start(mia, "Il mio villaggio", "Mia", "friuli", "it").join()
        val v = c.visit!!
        assertFalse(v.loading)
        assertNull(v.problem)
        assertFalse(v.stale)
        assertEquals(setOf("$mia/public", "$mia/villagers", "$mia/scenes"), b.asked.toSet())
        assertNotNull(v.state)
        // who is out: Rosa, Davide, the Furlans and Bepi, visiting; not the cast who don't live there
        assertEquals(setOf("rosa", "davide", "bepi", "n-chiara-furlan", "n-marco-furlan"), v.people(today).map { it.id }.toSet())
        assertEquals(listOf("il-mare"), v.scenesAt("spot:horizon").map { it.id })
        assertTrue(v.scenesAt("fire").isEmpty())
        assertEquals("Nonna Rosa", v.villager("rosa")?.name)
        assertEquals("Chiara Furlan", v.villager("n-chiara-furlan")?.name)
    }

    @Test fun `a town that never answered says so, and a retry brings it`() = runBlocking {
        VisitWorld.enter("it", "friuli")
        val b = FakeBridge(ok.mapValues { 502 to away })
        val c = controller(b)
        c.start(mia, "Il mio villaggio", "Mia", "friuli", "it").join()
        assertNull(c.visit!!.state)
        assertEquals("📡 Il mio villaggio non risponde · Il mio villaggio isn't answering", c.visit!!.problem)
        b.answers = ok
        c.retry()!!.join()
        assertNotNull(c.visit!!.state)
        assertNull(c.visit!!.problem)
    }

    @Test fun `a town that stops answering shows what was had of it, marked, also on the next visit`() = runBlocking {
        VisitWorld.enter("it", "friuli")
        val b = FakeBridge(ok)
        val c = controller(b)
        c.start(mia, "Il mio villaggio", "Mia", "friuli", "it").join()
        val had = c.visit!!.state
        b.answers = ok.mapValues { 502 to away }
        c.retry()!!.join()
        assertEquals(had, c.visit!!.state)
        assertTrue(c.visit!!.stale)
        assertTrue(c.visit!!.problem!!.startsWith("📡"))
        assertEquals(13, c.visit!!.cast.size)
        c.end()
        assertNull(c.visit)
        // the learner's own tutor is away too: the town as it was last, marked
        b.down = true
        c.start(mia, "Il mio villaggio", "Mia", "friuli", "it").join()
        assertEquals(had, c.visit!!.state)
        assertTrue(c.visit!!.stale)
        assertEquals("📡 ${bi("towns.cantReachTutor")}", c.visit!!.problem)
    }

    @Test fun `the bridge's older copy of a town that is away shows, marked stale`() = runBlocking {
        VisitWorld.enter("it", "friuli")
        val b = FakeBridge(ok + (Visits.PUBLIC to (200 to public.replace("\"fetched_at\"", "\"stale\":true,\"fetched_at\""))))
        val c = controller(b)
        c.start(mia, "Il mio villaggio", "Mia", "friuli", "it").join()
        assertNotNull(c.visit!!.state)
        assertTrue(c.visit!!.stale)
        assertNull(c.visit!!.problem)
        assertEquals("2026-09-26T09:30:00.000Z", c.visit!!.fetchedAt)
    }
}
