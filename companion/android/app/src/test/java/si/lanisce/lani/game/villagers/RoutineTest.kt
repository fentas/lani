package si.lanisce.lani.game.villagers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.BuildingType.*
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Sleep
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.Whereabouts
import si.lanisce.lani.game.scene.parseScene
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A day in the village ([Routine]), with the four culture packs' casts: when each gets up and goes to bed, what they do by
 * day, the evening by the fire or at home, the night rituals, the storyteller last, a happening winning over the night.
 */
class RoutineTest {
    private val cultures = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }

    private fun cast(culture: String): List<Villager> =
        File(cultures, "$culture/villagers").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }.sortedBy { it.order }

    private fun scenes(culture: String): List<SceneSpec> {
        val dirs = listOfNotNull(File(cultures, "$culture/scenes").takeIf { it.isDirectory }, if (culture == "primorska") File(cultures, "../scenes") else null)
        return dirs.flatMap { d -> d.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }
    }

    private val primorska by lazy { cast("primorska") }
    private val scenes by lazy { scenes("primorska") }

    /** The Primorska village with everyone moved in: the tent, the hut, four houses, the workplaces, the linden, the tower. */
    private fun town(cast: List<Villager> = primorska, vararg more: Building) = GameState(
        seed = 11, age = Age.VAS,
        buildings = listOf(
            Building("t", TENT, 0), Building("u", HUT, 1), Building("h1", HOUSE, 2, builtAt = 1), Building("h2", HOUSE, 3, builtAt = 2),
            Building("h3", HOUSE, 4, builtAt = 3), Building("h4", HOUSE, 5, builtAt = 4), Building("s", SMITHY, 6), Building("b", BEEHIVE, 7),
            Building("f", FIELD, 8), Building("k", KOZOLEC, 9), Building("l", LIPA, 10), Building("w", WELL, 11), Building("m", MARKET, 12),
            Building("sc", SCHOOL, 13), Building("wt", WATCHTOWER, 14), Building("c", CHURCH, 15),
        ) + more,
        residents = cast.map { Resident(it.id, "2026-01-01") },
    )

    private fun v(id: String, cast: List<Villager> = primorska) = cast.first { it.id == id }

    private fun day(state: GameState, cast: List<Villager> = primorska, sc: List<SceneSpec> = scenes, storyAt: LocalDateTime? = null, busy: Map<String, TownPlace> = emptyMap()) =
        Routine.Day(homes = Sleep.homes(sc, state, cast), tellers = Whereabouts.tellers(sc), storyAt = storyAt, busy = busy)

    private fun at(h: Int, m: Int = 0) = h * 60.0 + m

    /** A Wednesday in December: dark from before five in the evening until after seven in the morning. */
    private val winter = LocalDate.of(2026, 12, 9)

    /** A Wednesday in June: light until after nine in the evening. */
    private val summer = LocalDate.of(2026, 6, 24)

    @Test fun `the files' bedtimes and rituals are read, in minutes of the villager's day`() {
        val luka = Routine.plan(v("luka"))
        assertEquals(Routine.Kind.SHEPHERD, luka.kind)
        assertEquals(5 * 60, luka.up)
        assertEquals(21 * 60 + 50, luka.bed)
        val ovce = luka.rituals.single()
        assertEquals("ovce", ovce.id)
        assertEquals(21 * 60 + 25, ovce.at); assertEquals(21 * 60 + 45, ovce.until)
        assertEquals(TownPlace.Spot("meadow"), ovce.to)
        assertEquals(Doing.TEND, ovce.doing)
        assertTrue(ovce.title, ovce.title.startsWith("Luka pogleda ovce"))
        // before she gets up it's the night before: Micka's stove at 4:35 is at the end of her day
        val micka = Routine.plan(v("micka"))
        val pec = micka.rituals.single()
        assertEquals(4 * 60 + 35 + 1440, pec.at)
        assertTrue("home before she's up", pec.until <= micka.up + 1440 - Routine.TRANSIT)
        assertEquals(TownPlace.Spot("woodpile"), pec.to)
        // Janez is the storyteller by the scenes; alone he'd be a grandfather
        assertEquals(Routine.Kind.STORYTELLER, Routine.plan(v("janez"), teller = true).kind)
        assertEquals(Routine.Kind.GRANDPA, Routine.kindOf(v("janez")))
        assertEquals(Routine.Kind.CHILD, Routine.kindOf(v("zala")))
        assertEquals(mapOf("janez" to "ob-ognju/zgodba"), Whereabouts.tellers(scenes))
    }

    @Test fun `without a file's routine the kind decides, spread by a few minutes, never after the storyteller`() {
        val newcomers = (1..40).map { Villager(id = "n-oseba-$it", name = "Oseba $it", art = if (it % 2 == 0) "woman" else "man", home = listOf("house", "hut", "tent")) }
        val beds = newcomers.map { Routine.plan(it).bed }
        assertTrue("spread: ${beds.toSet()}", beds.toSet().size >= 5)
        assertTrue(beds.all { it in 21 * 60 + 30..Routine.Kind.STORYTELLER.bed - 15 })
        val child = Routine.plan(Villager(id = "n-mali", name = "Mali", art = "child1"))
        assertTrue(child.bed in 19 * 60 + 45..20 * 60 + 15)
        assertEquals(Routine.Kind.CHILD.up, child.up)
        assertNull(Routine.clock("25:00")); assertNull(Routine.clock("abc")); assertEquals(30, Routine.clock("0:30"))
        // a bedtime after midnight is in the night after
        val owl = Villager(id = "sova", name = "Sova", art = "man", routine = RoutineSpec(up = "9:00", bed = "0:30"))
        assertEquals(24 * 60 + 30, Routine.plan(owl).bed)
    }

    @Test fun `in every village children go to bed first and the storyteller last, and the rituals are at night`() {
        for (culture in listOf("primorska", "friuli", "kaernten", "lakeland")) {
            val cast = cast(culture)
            val tellers = Whereabouts.tellers(scenes(culture))
            assertEquals(culture, 1, tellers.size)
            val teller = cast.first { it.id in tellers }
            val plans = cast.associate { it.id to Routine.plan(it, it.id in tellers) }
            val last = plans.getValue(teller.id).bed
            for ((id, p) in plans) {
                if (id != teller.id) assertTrue("$culture/$id to bed before the storyteller (${p.bed} < $last)", p.bed < last)
                assertNotNull("$culture/$id has a routine in their file", cast.first { it.id == id }.routine)
            }
            val children = cast.filter { Routine.kindOf(it) == Routine.Kind.CHILD }
            assertEquals(culture, 3, children.size)
            val grown = cast - children.toSet()
            assertTrue(culture, children.maxOf { plans.getValue(it.id).bed } < grown.minOf { plans.getValue(it.id).bed })
            val rituals = plans.values.flatMap { it.rituals }
            assertTrue("$culture: ${rituals.size} rituals", rituals.size >= 5)
            for ((id, p) in plans) for (r in p.rituals) {
                val clock = r.at % 1440
                assertTrue("$culture/$id/${r.id} at night ($clock)", clock >= 20 * 60 + 30 || clock < 5 * 60)
                assertTrue("$culture/$id/${r.id} short", r.until - r.at in 10..40)
                assertTrue("$culture/$id/${r.id} somewhere: ${r.to}", r.to != TownPlace.Fire && TownMarkers.has(town(cast), r.to))
                assertTrue("$culture/$id/${r.id} has a title", r.title.isNotBlank())
            }
            // every village has the stove before dawn, the sheep, the forge, the hives, the inn and the tower
            assertEquals(culture, setOf(TownPlace.Spot("woodpile"), TownPlace.Spot("meadow"), TownPlace.At(SMITHY), TownPlace.At(BEEHIVE), TownPlace.At(MARKET), TownPlace.At(WATCHTOWER)), rituals.map { it.to }.toSet())
        }
    }

    @Test fun `by day everyone is out, at a place the village has, and walks from one to the next`() {
        val s = town()
        val d = day(s)
        for (date in listOf(summer, summer.plusDays(3), winter)) {
            var m = 9.0 * 60
            while (m < 16 * 60) {
                for (p in primorska) {
                    val now = Routine.now(p, s, date, m, date.monthValue, d)
                    assertEquals("${p.id} at $m on $date", Where.OUT, now.where)
                    assertTrue("${p.id} at ${now.place}", TownMarkers.has(s, now.place!!))
                }
                m += 17
            }
        }
        // a day is stretches of tens of minutes, each somewhere, and the next one begins with the walk from the last
        for (p in primorska) {
            val plan = Routine.plan(p, p.id == "janez")
            val stints = Routine.stints(p, plan, s, summer, d.homes.getValue(p.id))
            assertEquals(plan.up, stints.first().start)
            for (i in 1 until stints.size) {
                assertEquals(stints[i - 1].end, stints[i].start)
                if (stints[i].start < 17 * 60) assertTrue("${p.id}: ${stints[i]}", stints[i].end - stints[i].start >= 20 || stints[i].end == plan.bed)
            }
            val change = stints.drop(1).first { it.place != stints[stints.indexOf(it) - 1].place }
            val walking = Routine.now(p, s, summer, change.start + 0.2, 6, d)
            if (walking.where == Where.OUT && d.calls[p.id] == null && !Routine.dark(6, change.start + 0.2)) {
                assertNotNull("${p.id} sets off from the last place", walking.from)
                assertEquals(12.0, walking.since, 0.01)
            }
        }
        // pure: the same day again is the same; another day is another
        val france = v("france")
        val a = Routine.stints(france, Routine.plan(france), s, summer, TownPlace.At(HOUSE, "h2"))
        assertEquals(a, Routine.stints(france, Routine.plan(france), s, summer, TownPlace.At(HOUSE, "h2")))
        assertNotEquals(a, Routine.stints(france, Routine.plan(france), s, summer.plusDays(1), TownPlace.At(HOUSE, "h2")))
    }

    @Test fun `each works where their work is, the children go to school on a weekday, and all eat at home`() {
        val s = town()
        val d = day(s)
        fun share(id: String, date: LocalDate, pred: (Now) -> Boolean): Float {
            var n = 0; var hit = 0; var m = 6.0 * 60
            while (m < 18 * 60) { n++; if (pred(Routine.now(v(id), s, date, m, date.monthValue, d))) hit++; m += 5 }
            return hit.toFloat() / n
        }
        val week = (0 until 7).map { summer.plusDays(it.toLong()) }
        fun week(id: String, pred: (Now) -> Boolean) = week.map { share(id, it, pred) }.average()
        assertTrue(week("tone") { it.place == TownPlace.At(SMITHY) && it.doing == Doing.HAMMER } > 0.35)
        assertTrue(week("france") { it.place == TownPlace.At(FIELD) || it.place == TownPlace.At(KOZOLEC) } > 0.35)
        assertTrue(week("anton") { it.place == TownPlace.At(BEEHIVE) && it.doing == Doing.TEND } > 0.35)
        assertTrue(week("luka") { it.place == TownPlace.Spot("meadow") } > 0.45)
        assertTrue(week("vida") { it.place == TownPlace.At(MARKET) } > 0.35)
        assertTrue(week("joze") { it.place == TownPlace.Spot("highseat") } > 0.35)
        assertTrue(week("janez") { it.place == TownPlace.At(LIPA) } > 0.25)
        // Wednesday morning at ten: the children and the teacher at school; in July the school is out
        for (id in listOf("zala", "tine", "nejc", "mojca")) {
            assertEquals(id, TownPlace.At(SCHOOL), Routine.now(v(id), s, summer, at(10), 6, d).place)
            // (the teacher lives at the school: she's at home there)
            if (id != "mojca") for (h in 8..16) assertNotEquals("$id at $h", TownPlace.At(SCHOOL), Routine.now(v(id), s, LocalDate.of(2026, 7, 15), at(h), 7, d).place)
        }
        // lunch at home: France in his house, Micka at the hut (her kitchen), Luka stays with his sheep
        assertEquals(d.homes.getValue("france"), Routine.now(v("france"), s, summer, at(12, 45), 6, d).place)
        assertEquals(TownPlace.At(HUT), Routine.now(v("micka"), s, summer, at(12, 45), 6, d).place)
        assertNotEquals(d.homes.getValue("luka"), Routine.now(v("luka"), s, summer, at(12, 45), 6, d).place)
        // a Sunday morning: the grandmother at mass
        assertEquals(TownPlace.At(CHURCH), Routine.now(v("micka"), s, LocalDate.of(2026, 6, 28), at(9, 30), 6, d).place)
        // someone waiting for the learner stands where it matters and waves; at night they're in bed
        val calls = d.copy(calls = mapOf("micka" to TownPlace.At(HUT)))
        assertEquals(Now(Where.OUT, TownPlace.At(HUT), Doing.WAVE), Routine.now(v("micka"), s, summer, at(15), 6, calls))
        assertEquals(Where.ASLEEP, Routine.now(v("micka"), s, summer, at(23), 6, calls).where)
    }

    @Test fun `a load is an errand of the work, once or twice a stretch, never a stretch of carrying to and fro`() {
        val s = town()
        val d = day(s)
        var errands = 0
        for (k in 0 until 7) for (p in primorska) {
            val date = summer.plusDays(k.toLong())
            val stints = Routine.stints(p, Routine.plan(p, p.id == "janez"), s, date, d.homes.getValue(p.id))
            for (st in stints) {
                assertNotEquals("${p.id}: $st", Doing.CARRY, st.doing)
                assertTrue("${p.id}: $st", st.errands.size <= 2)
                for ((i, e) in st.errands.withIndex()) {
                    errands++
                    assertTrue("${p.id}: $st", e.at >= st.start + 5 && e.at + Routine.ERRAND <= st.end)
                    if (i > 0) assertTrue("${p.id}: $st", e.at >= st.errands[i - 1].at + Routine.ERRAND)
                    // away on it: still at their work by their day, the trip under way; back at it after
                    val on = Routine.now(p, s, date, e.at + 1.0, 6, d)
                    if (on.where != Where.OUT || d.calls[p.id] != null || Routine.dark(6, e.at + 1.0)) continue
                    assertEquals(st.place, on.place)
                    assertEquals(Trip(e.from, e.to, 60.0), on.errand)
                    val after = Routine.now(p, s, date, e.at + Routine.ERRAND + 0.5, 6, d)
                    assertNull(after.errand)
                    assertEquals(st.place, after.place)
                }
            }
        }
        // the farmer's hay, the grandmother's water and wood, the innkeeper's water
        assertTrue("$errands errands in a week", errands >= 10)
    }

    @Test fun `in the dark only the fire, a ritual or the way home brings anyone out`() {
        val s = town()
        val d = day(s)
        for (date in listOf(winter, summer)) {
            var m = 0.0
            while (m < 1440) {
                if (Routine.dark(date.monthValue, m)) for (p in primorska) {
                    val now = Routine.now(p, s, date, m, date.monthValue, d)
                    if (now.where == Where.OUT) {
                        val why = now.place == TownPlace.Fire || now.ritual != null
                        assertTrue("${p.id} out at ${now.place} at $m on $date", why)
                        if (now.place != TownPlace.Fire || now.from != null) assertTrue("${p.id} with a lantern at $m", now.lantern || now.from == null)
                    }
                    if (now.where == Where.HOMEWARD) assertTrue("${p.id} going home at $m for ${now.since} s", now.since < Routine.TRANSIT * 60 + 1)
                }
                m += 7
            }
        }
    }

    @Test fun `a winter night, the fire empties as they go to bed, the rituals come one after another, Janez goes last`() {
        val s = town()
        val d = day(s)
        fun now(id: String, h: Int, m: Int = 0) = Routine.now(v(id), s, winter, at(h, m), 12, d)
        // seven in the evening: Janez by the fire; everyone else by the fire or at home (the dice), nobody elsewhere
        assertEquals(TownPlace.Fire, now("janez", 19).place)
        for (p in primorska) assertTrue(p.id, now(p.id, 19).let { it.where == Where.INDOORS || it.place == TownPlace.Fire })
        // the children are in bed from eight, the grown-ups later, one by one
        assertEquals(Where.ASLEEP, now("tine", 20, 5).where)
        assertEquals(Where.ASLEEP, now("zala", 20, 10).where)
        assertTrue(now("france", 20, 10).where != Where.ASLEEP)
        // the rituals: Anton at his hives, Jože on the tower, Luka with the sheep, Tone at the forge, Vida locking up
        for ((id, time, place) in listOf(
            Triple("anton", 21 * 60, TownPlace.At(BEEHIVE)), Triple("joze", 21 * 60 + 10, TownPlace.At(WATCHTOWER)),
            Triple("luka", 21 * 60 + 35, TownPlace.Spot("meadow")), Triple("tone", 21 * 60 + 52, TownPlace.At(SMITHY)),
            Triple("vida", 22 * 60 + 8, TownPlace.At(MARKET)),
        )) {
            val n = Routine.now(v(id), s, winter, time.toDouble(), 12, d)
            assertEquals(id, Where.OUT, n.where)
            assertEquals(id, place, n.place)
            assertTrue(id, n.lantern)
            assertNotNull(id, n.ritual)
        }
        // after the sheep, so near his bedtime, Luka goes home to his tent and to bed
        assertEquals(Where.HOMEWARD, now("luka", 21, 46).where)
        assertEquals(TownPlace.Spot("meadow"), now("luka", 21, 46).from)
        assertEquals(Where.ASLEEP, now("luka", 21, 55).where)
        // eleven: everyone asleep but Janez, still waiting by the fire with tonight's story
        for (p in primorska) if (p.id != "janez") assertEquals(p.id, Where.ASLEEP, now(p.id, 23).where)
        assertEquals(Where.OUT, now("janez", 23).where)
        // half past eleven: he gives up and walks home, then he's in bed too
        assertEquals(Where.HOMEWARD, now("janez", 23, 31).where)
        assertEquals(TownPlace.Fire, now("janez", 23, 31).from)
        assertEquals(Where.ASLEEP, now("janez", 23, 40).where)
        assertEquals(Where.ASLEEP, now("janez", 2).where)
        // before dawn Micka fetches wood and lights the stove; then she's up in the dark kitchen
        assertEquals(TownPlace.Spot("woodpile"), now("micka", 4, 45).place)
        assertEquals(Doing.CARRY, now("micka", 4, 45).doing)
        assertEquals(Where.HOMEWARD, now("micka", 4, 56).where)
        assertEquals(Where.INDOORS, now("micka", 5, 30).where)
        assertEquals(Where.ASLEEP, now("zala", 5, 30).where)
    }

    @Test fun `Janez stays by the fire until the learner has heard the story, then goes home to bed`() {
        val s = town()
        val told = s.copy(happeningsDone = mapOf("ob-ognju/zgodba" to winter.toString()))
        // heard early in the evening: he keeps the fire till his bedtime, the last one there, then goes home
        val d = day(told)
        assertEquals(TownPlace.Fire, Routine.now(v("janez"), told, winter, at(22, 30), 12, d).place)
        assertEquals(Where.HOMEWARD, Routine.now(v("janez"), told, winter, at(22, 46), 12, d).where)
        assertEquals(Where.ASLEEP, Routine.now(v("janez"), told, winter, at(23), 12, d).where)
        // heard late (the app knows when): home a few minutes after it
        val late = day(told, storyAt = winter.atTime(23, 10))
        assertEquals(TownPlace.Fire, Routine.now(v("janez"), told, winter, at(23, 12), 12, late).place)
        assertEquals(Where.HOMEWARD, Routine.now(v("janez"), told, winter, at(23, 16), 12, late).where)
        assertEquals(Where.ASLEEP, Routine.now(v("janez"), told, winter, at(23, 25), 12, late).where)
        // the happening is off from half past eleven: nobody waits by the fire past it
        val fire = si.lanisce.lani.game.scene.StoryFixtures.campfire()
        val story = fire.copy(happenings = fire.happenings.filter { it.stories }.map { it.copy(chance = 1f) })
        fun on(h: Int, m: Int) = Happenings.active(listOf(story), s, winter.atTime(h, m)).map { it.happening.id }
        assertEquals(listOf("zgodba"), on(23, 20))
        assertTrue(on(23, 35).isEmpty())
        assertTrue(on(2, 0).isEmpty())
        assertEquals(listOf("zgodba"), on(19, 0))
    }

    @Test fun `a happening wins over the night, whoever has one on is up and there`() {
        val s = town()
        val tower = day(s, busy = mapOf("luka" to TownPlace.At(WATCHTOWER), "janez" to TownPlace.Fire))
        val luka = Routine.now(v("luka"), s, winter, at(1), 12, tower)
        assertEquals(Where.OUT, luka.where); assertEquals(TownPlace.At(WATCHTOWER), luka.place)
        assertEquals(Doing.WAVE, luka.doing); assertTrue(luka.lantern)
        assertEquals(Doing.WARM, Routine.now(v("janez"), s, winter, at(1), 12, tower).doing)
        // by day too: at the happening's place, not at their work
        assertEquals(TownPlace.At(WATCHTOWER), Routine.now(v("luka"), s, summer, at(10), 6, tower).place)
    }

    @Test fun `a ritual whose place the village hasn't built doesn't happen`() {
        val s = town().let { t -> t.copy(buildings = t.buildings.filter { it.type != WATCHTOWER }) }
        val n = Routine.now(v("joze"), s, winter, at(21, 10), 12, day(s))
        assertNull(n.ritual)
        assertFalse(n.place == TownPlace.At(WATCHTOWER))
    }
}
