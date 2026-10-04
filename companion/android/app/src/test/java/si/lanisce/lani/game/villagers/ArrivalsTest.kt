package si.lanisce.lani.game.villagers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.culture.Culture
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.Happening
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.game.TodayItem
import si.lanisce.lani.ui.game.TownOverview
import si.lanisce.lani.ui.home.HomeLogic
import si.lanisce.lani.ui.home.VillageCall
import si.lanisce.lani.ui.scene.DialogRun
import si.lanisce.lani.ui.stage.Cast
import si.lanisce.lani.ui.stage.Run
import java.time.LocalDate

/**
 * Arrivals (companion/VILLAGERS.md "Arrivals"): someone who joins the village waits to be met, and until then hosts no
 * run, asks for nothing and stands in no scene; their introduction is the culture pack's (here a fixture's: the pack's own
 * content is checked by ArrivalsContentTest), at the learner's level, late for who came days ago; a baby is shown by its
 * parents.
 */
class ArrivalsTest {
    private val day = LocalDate.of(2026, 9, 27)
    private val fixture: ArrivalsFile = json.decodeFromString(
        ArrivalsFile.serializer(),
        checkNotNull(javaClass.getResourceAsStream("/arrivals/fixture.json")).use { it.readBytes().decodeToString() },
    )
    private lateinit var was: Culture
    private lateinit var pair: LangPair

    private val micka = Villager("micka", "Babica Micka", "👵", "grandma", voice = "female", home = listOf("hut", "spot:woodpile"), register = "vi", order = 1)
    private val luka = Villager("luka", "Pastir Luka", "🐑", "shepherd", voice = "male", home = listOf("spot:meadow"), order = 2)
    private val zala = Villager("zala", "Zala", "👧", "child2", voice = "female", home = listOf("hut", "spot:meadow"), register = "ti", order = 3)
    private val cast = listOf(micka, luka, zala)

    /** The Primorska village as it stood: Micka and Luka shared memories with Jan, Zala only trained once on the stage. */
    private val jans = GameState(
        seed = 7, age = Age.ZASELEK, villagers = 3,
        buildings = listOf(Building("h", BuildingType.HUT, 0)),
        residents = listOf(Resident("micka", "2026-09-24"), Resident("luka", "2026-09-24"), Resident("zala", "2026-09-25")),
        bonds = mapOf(
            "micka" to Bond(points = 49, memories = listOf(Memory("2026-09-25", "potico, ki sva jo skupaj spekla")), met = "2026-09-25", seen = "2026-09-27"),
            "luka" to Bond(points = 81, memories = listOf(Memory("2026-09-25", "dan, ko sva skupaj iskala Belo")), met = "2026-09-25", seen = "2026-09-27"),
            "zala" to Bond(points = 1, met = "2026-09-26", seen = "2026-09-26", trainedToday = 1),
            "tone" to Bond(met = "2026-09-25", seen = "2026-09-25"),
        ),
    )

    @Before fun setUp() {
        was = Cultures.home
        pair = L10n.pair
        L10n.pair = LangPair.DEFAULT
        Cultures.use(Cultures.default().withArrivals(fixture))
    }

    @After fun tearDown() {
        Cultures.use(was)
        L10n.pair = pair
    }

    private fun quest(id: String, giver: String) = Quest(id, giver, "🙂", id, "story", Res.FOOD, mapOf(Res.FOOD to 5))

    @Test fun `an older village meets late who it never really met, once, and keeps its friends`() {
        val s = Arrivals.migrate(jans)
        assertTrue(Arrivals.MIGRATION in s.migrated)
        assertTrue(Arrivals.met(s, "micka"))
        assertTrue(Arrivals.met(s, "luka"))
        assertFalse(Arrivals.met(s, "zala")) // one training on the stage is no meeting
        assertNull(s.bonds["tone"]?.met) // a greeting as a visitor neither
        assertEquals(1, s.bonds["zala"]?.points) // what was earned stays
        assertEquals(s, Arrivals.migrate(s)) // once
        // Zala moved in two days ago: her introduction opens with the late line
        val p = Arrivals.ready(s, day).single()
        assertEquals("zala", p.id)
        assertEquals(PendingArrival.Kind.CAST, p.kind)
        assertTrue(p.late)
        val m = checkNotNull(Arrivals.meeting(p, s, cast, "A1", day))
        assertEquals("culture", m.source)
        assertTrue(m.late)
        assertEquals("Jan! Saj še ne poznaš moje vnukinje, kajne?", m.dialog.lines.first().sl)
        assertEquals("micka", m.dialog.lines.first().who)
        // the rest as it is: Zala herself, the learner's turn, Micka again
        assertEquals("Živjo! Stara sem osem let.", m.dialog.lines[1].sl)
        assertEquals(listOf("zala", "micka"), m.speakers)
        // a culture without arrivals turns nothing on
        assertEquals(jans, Arrivals.migrate(jans, hasArrivals = false))
        assertTrue(Arrivals.met(jans, "zala"))
    }

    @Test fun `who hasn't been met hosts no run, asks for nothing and stands in no scene`() {
        val s = Arrivals.migrate(jans).copy(quests = listOf(quest("berries", "Zala"), quest("kitchen", "Babica Micka")))
        assertEquals(setOf("micka", "luka"), Residents.present(s, day))
        assertEquals(setOf("micka", "luka", "zala"), Residents.living(s, day))
        assertFalse(Residents.here(s, "Zala", cast, day))
        assertEquals(listOf("kitchen"), Residents.openQuests(s, cast, day).map { it.id })
        // her request's run and the reviews: someone Jan knows is on the stage
        assertTrue(Cast.onStage(Run.Quest("Zala", "👧"), cast, s, day).id in setOf("micka", "luka"))
        assertTrue(Cast.onStage(Run.Review, cast, s, day).id in setOf("micka", "luka"))
        assertTrue(Cast.todaysCompanion(cast, s, day)?.id in setOf("micka", "luka"))
        // her happening in the kitchen doesn't come up, and she isn't "here today"
        val scene = SceneSpec(
            id = "v-kuhinji", title = "V kuhinji · In the kitchen", art = "kitchen", from = listOf("hut"),
            people = listOf(ScenePerson("zala", "Zala", "👧", "child2", "table", always = true, villager = "zala")),
            happenings = listOf(Happening("igra", "Zala se igra · Zala plays", who = "zala")),
        )
        assertTrue(Happenings.active(listOf(scene), s, day.atTime(12, 0)).isEmpty())
        assertTrue(Happenings.always(scene, Residents.present(s, day)).isEmpty())
        // once met, all of it is hers
        val met = Bonds.add(s, "zala", Bonds.DIALOG, day, meet = true)
        assertTrue(Residents.here(met, "Zala", cast, day))
        assertEquals(listOf("berries", "kitchen"), Residents.openQuests(met, cast, day).map { it.id })
        assertEquals("zala", Cast.onStage(Run.Quest("Zala", "👧"), cast, met, day).id)
        assertEquals(listOf("v-kuhinji/igra"), Happenings.active(listOf(scene), met, day.atTime(12, 0)).map { it.key })
    }

    @Test fun `someone who joins waits at their home, and in the scroll's today and Home's`() {
        val s0 = Arrivals.migrate(jans).let { Bonds.add(it, "zala", Bonds.DIALOG, day, meet = true) }
        // the village grows: a newcomer moves in today
        val (s, news) = Residents.reconcile(s0.copy(villagers = 4), cast, day)
        val newcomer = s.residents.last()
        assertTrue(newcomer.name != null && newcomer.born == null)
        assertEquals(1, news.size)
        val p = Arrivals.ready(s, day).single()
        assertEquals(newcomer.id, p.id)
        assertEquals(PendingArrival.Kind.NEWCOMER, p.kind)
        assertFalse(p.late)
        assertEquals("🧳", p.emoji)
        // a bubble at their home (the hut), and the first thing in the scroll's today
        val marker = TownMarkers.of(s, emptyList(), cast, day).first { it.id.startsWith("arrival:") }
        assertEquals("arrival:${newcomer.id}", marker.id)
        assertEquals(TownPlace.At(BuildingType.HUT), marker.place)
        assertTrue(marker.label.startsWith(newcomer.name!!))
        val today = TownOverview.of(s, emptyList(), day.atTime(10, 0), cast).today
        assertEquals(TodayItem.Arrival(newcomer.id, "🧳", marker.label, marker.place), today.first())
        // and on Home's Today card, a tap away from their introduction
        assertEquals(listOf(VillageCall.Arrival(newcomer.id, "🧳", marker.label)), HomeLogic.calls(s, cast, emptyList(), day))
        // their introduction: the template, their name and trade put in, in their gender
        val m = checkNotNull(Arrivals.meeting(p, s, Residents.people(s, cast, day) + cast, "A1", day))
        assertEquals("template", m.source)
        assertEquals("Dober dan! Jaz sem ${newcomer.name}.", m.dialog.lines[0].sl)
        assertEquals("Good day! I'm ${newcomer.name}.", m.dialog.lines[0].en)
        val role = newcomer.role!!.substringBefore(" · ").lowercase()
        val she = newcomer.voice == "female"
        assertEquals("Sem ${if (she) "nova" else "nov"} $role v vasi.", m.dialog.lines[1].sl)
        assertTrue(m.dialog.lines.none { l -> listOfNotNull(l.sl, l.en).any { '{' in it } || l.choices.any { '{' in it.sl || '{' in (it.why ?: "") } })
        assertEquals(if (she) "dan, ko sem se preselila v vas" else "dan, ko sem se preselil v vas", m.memory?.sl)
        assertEquals(listOf(newcomer.id), m.speakers)
    }

    @Test fun `the introduction played to its end makes them met, a friend of level 0, with the day as a memory`() {
        val s = Arrivals.migrate(jans)
        val p = Arrivals.readyFor(s, "zala", day)!!
        val m = Arrivals.meeting(p, s, cast, "A1", day)!!
        var run = DialogRun.start(m.dialog, p.id, repliers = m.repliers)
        // a wrong choice first: Zala reacts (she spoke last before the turn), not Micka
        while (run.step != DialogRun.Step.END) {
            run = when (run.step) {
                DialogRun.Step.LISTEN -> run.next()
                DialogRun.Step.CHOOSE -> {
                    val wrong = run.choices.indexOfFirst { !it.ok }
                    if (run.mistakes == 0 && wrong >= 0) run.choose(wrong).also { r -> assertEquals("zala", r.said.last().who) }
                    else run.choose(run.choices.indexOfFirst { it.ok })
                }
                DialogRun.Step.END -> run
            }
        }
        assertEquals(1, run.mistakes)
        assertEquals("Dobrodošel? Jaz nisem fant!", run.said.first { it.who == "zala" && it.sl.startsWith("Dobrodošel") }.sl)
        val after = Bonds.add(s, "zala", Bonds.DIALOG_WITH_MISTAKES, day, m.memory, meet = true)
        assertTrue(Arrivals.met(after, "zala"))
        assertEquals(day.toString(), after.bonds["zala"]?.met)
        assertEquals(0, Bonds.level(after, "zala"))
        assertEquals(Memory(day.toString(), "dan, ko sem prišla živet k babici", "the day I came to live with Grandma", Arrivals.KIND), after.bonds["zala"]?.memories?.last())
        assertTrue(Arrivals.ready(after, day).isEmpty())
        assertTrue(TownMarkers.of(after, emptyList(), cast, day).none { it.id.startsWith("arrival:") })
    }

    @Test fun `only the introduction makes them met, a feast or a greeting before it doesn't`() {
        val s = Arrivals.migrate(jans)
        val greeted = Bonds.add(s, "zala", 0, day)
        assertFalse(Arrivals.met(greeted, "zala"))
        assertEquals(day.toString(), greeted.bonds["zala"]?.seen)
        val feast = Bonds.add(s, "zala", 1, day)
        assertFalse(Arrivals.met(feast, "zala"))
        assertEquals(2, feast.bonds["zala"]?.points)
        // a village that doesn't introduce its people meets them as it always did
        assertEquals(day.toString(), Bonds.add(GameState(), "zala", 1, day).bonds["zala"]?.met)
    }

    @Test fun `who introduces them comes first`() {
        // neither is met: Zala waits for Micka, whose own introduction is the one to play
        val s = Arrivals.migrate(jans).let { it.copy(bonds = it.bonds - "micka") }
        assertEquals(listOf("micka"), Arrivals.ready(s, day).map { it.id })
        assertEquals("micka", Arrivals.next(s, "zala", day)?.id)
        assertEquals(listOf("micka", "zala"), Arrivals.pending(s, day).map { it.id })
        // Micka moved away: Zala introduces herself (the template, with her name)
        val alone = s.copy(residents = s.residents.filter { it.id != "micka" })
        val p = Arrivals.readyFor(alone, "zala", day)!!
        val m = Arrivals.meeting(p, alone, cast, "A1", day)!!
        assertEquals("template", m.source)
        assertEquals(listOf("zala"), m.speakers)
    }

    @Test fun `a baby is shown by its parents, once the learner knows them`() {
        val ana = Resident("n-ana-furlan", "2026-09-10", name = "Ana Furlan", emoji = "👩", art = "woman", voice = "female", role = "Tkalka · Weaver", family = "Furlan")
        val ivo = Resident("n-ivo-furlan", "2026-09-12", name = "Ivo Furlan", emoji = "👨", art = "man", voice = "male", role = "Drvar · Woodcutter", family = "Furlan")
        val baby = Resident("n-mia-furlan", day.toString(), name = "Mia Furlan", emoji = "👶", art = "baby", voice = "female", role = "Otrok · Child", born = day.toString(), family = "Furlan", parents = listOf(ana.id, ivo.id))
        val s0 = Arrivals.migrate(jans).let { it.copy(residents = it.residents + ana + ivo + baby) }
        // the parents first
        assertEquals(listOf(ana.id, ivo.id, "zala"), Arrivals.ready(s0, day).map { it.id })
        assertEquals(ana.id, Arrivals.next(s0, baby.id, day)?.id)
        val s = listOf("zala", ana.id, ivo.id).fold(s0) { st, id -> Bonds.add(st, id, Bonds.DIALOG, day, meet = true) }
        val p = Arrivals.ready(s, day).single()
        assertEquals(baby.id, p.id)
        assertEquals(PendingArrival.Kind.BIRTH, p.kind)
        assertEquals("👶", p.emoji)
        val m = Arrivals.meeting(p, s, cast, "A1", day)!!
        assertEquals(listOf(baby.id, ana.id, ivo.id), m.speakers)
        assertEquals(ana.id, m.dialog.lines[0].who)
        assertEquals(ivo.id, m.dialog.lines[1].who)
        assertEquals("Ime ji je Mia.", m.dialog.lines[1].sl)
        assertEquals("Her name is Mia.", m.dialog.lines[1].en)
        // the replies to the learner's turn: the parent who spoke last, with the other's name put in
        assertEquals(ivo.id, m.repliers[2])
        assertEquals("Hvala, Ana in jaz sva srečna.", m.dialog.lines[2].choices.first { it.ok }.reply?.sl)
        assertNull(m.dialog.talk) // no talk with a baby
        assertEquals("dan, ko si me prvič videl", m.memory?.sl)
        assertTrue(Arrivals.title(p, null).startsWith("Pri Furlanovih se je rodila Mia!"))
    }

    @Test fun `the level played is the learner's, else the highest below, else the easiest`() {
        assertEquals("A1", Arrivals.levelFor(listOf("A1", "A2"), "A1"))
        assertEquals("A2", Arrivals.levelFor(listOf("A1", "A2"), "A2"))
        assertEquals("A2", Arrivals.levelFor(listOf("A1", "A2"), "B2"))
        assertEquals("B1", Arrivals.levelFor(listOf("B1"), "A1"))
        assertNull(Arrivals.levelFor(emptyList(), "A1"))
        val s = Arrivals.migrate(jans)
        val p = Arrivals.readyFor(s, "zala", day)!!
        assertEquals("A2", Arrivals.meeting(p, s, cast, "B1", day)!!.level)
    }

    @Test fun `the tutor's arrival replaces the template, and the pack's for someone of the cast`() {
        val s = Arrivals.migrate(jans)
        val served = """{"schema":"lani.arrival/v0","language":"sl","arrivals":[{"schema":"lani.arrival/v0","id":"zala","language":"sl",
            "memory":{"sl":"dan, ko sva nabirala borovnice","en":"the day we picked blueberries"},
            "levels":{"A1":{"lines":[{"who":"zala","sl":"Živjo! Jaz sem Zala.","en":"Hi! I'm Zala."},
            {"choices":[{"sl":"Živjo!","en":"Hi!","ok":true},{"sl":"Dober večer!","en":"Good evening!","why":"It's morning.","reply":{"sl":"Večer? Sonce sije!","en":"Evening? The sun is shining!"}}]},
            {"who":"zala","sl":"Greva po borovnice?","en":"Shall we go for blueberries?"}]}}}]}"""
        val tutor = Arrivals.parseServed(served)
        assertEquals(setOf("zala"), tutor.keys)
        val m = Arrivals.meeting(Arrivals.readyFor(s, "zala", day)!!, s, cast, "A1", day, tutor)!!
        assertEquals("tutor", m.source)
        assertFalse(m.late) // the tutor's has no late line: it opens as written
        assertEquals("Živjo! Jaz sem Zala.", m.dialog.lines[0].sl)
        assertEquals("It's morning.", m.dialog.lines[1].choices[1].why)
        assertEquals("dan, ko sva nabirala borovnice", m.memory?.sl)
        assertEquals(tutor, Arrivals.parseServed(Arrivals.served(tutor)))
    }
}
