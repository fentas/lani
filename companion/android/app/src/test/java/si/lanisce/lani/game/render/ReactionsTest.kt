package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.FrameBudget
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.MARKET
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.SCHOOL
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WATCHTOWER
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.EventEnd
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.Whereabouts
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.villagers.Reactions
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Stance
import si.lanisce.lani.game.villagers.Stir
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.parseVillagers
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.hypot

/**
 * The people react to the village's events (companion/GAME.md "People react"; [Reactions], [TownReactions]): Jan's hamlet
 * of 2026-10-13 and a town of the same cast, with wolves, a bear, a storm, the merchant and a festival on, and after a fight
 * won and lost: who does what, where they go and how (nobody near the animals, nor on a way past them), and back.
 */
class ReactionsTest {
    private val cultures = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }
    private val cast: List<Villager> by lazy {
        File(cultures, "primorska/villagers").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }.sortedBy { it.order }
    }
    private val scenes: List<SceneSpec> by lazy {
        listOf(File(cultures, "../scenes"), File(cultures, "primorska/scenes")).flatMap { d -> d.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }
    }
    private val castIds = listOf("micka", "luka", "zala", "janez", "france", "tine", "anton", "tone", "ancka", "marko", "vida", "nejc", "joze")

    /** Jan's hamlet on 2026-10-13 (as VillageLifeTest has it): the cast but the teacher and three who moved in, the palisade. */
    private val jan = GameState(
        seed = 8993091993378563000L, age = Age.ZASELEK, villagers = 17, morale = 70, fire = 95, land = Land.CLASSIC,
        buildings = listOf(
            Building("tent-1", TENT, NO_PLOT), Building("field-2", FIELD, 1), Building("hut-3", HUT, 2, level = 3), Building("well-4", WELL, 3),
            Building("palisade-5", PALISADE, NO_PLOT), Building("kozolec-6", KOZOLEC, 8), Building("beehive-7", BEEHIVE, 5, level = 2),
            Building("church-8", CHURCH, 6), Building("house-9", HOUSE, 0, level = 2), Building("smithy-10", SMITHY, 4), Building("lipa-11", LIPA, 7),
        ),
        residents = castIds.map { Resident(it, "2026-09-24") } + listOf(
            Resident("n-tjasa-mozetic", "2026-10-10", name = "Tjaša Mozetič", art = "woman", voice = "female", role = "Drvarka · Woodcutter"),
            Resident("n-anze-humar", "2026-10-11", name = "Anže Humar", art = "man", voice = "male", role = "Drvar · Woodcutter"),
            Resident("n-blaz-mozetic", "2026-10-12", name = "Blaž Mozetič", art = "man", voice = "male", role = "Kovačev vajenec · Smith's apprentice"),
        ),
        projects = mapOf("most" to 2, "mlin" to 1),
    )

    /** A town of the same cast, everyone moved in: the watchtower, the market. */
    private val town: GameState by lazy {
        val types = listOf(HUT, FIELD, WELL, TENT, KOZOLEC, HOUSE, LIPA, BEEHIVE, HOUSE, SMITHY, MARKET, HOUSE, SCHOOL, WATCHTOWER, HOUSE, CHURCH)
        GameState(
            seed = 42, age = Age.VAS, villagers = cast.size + 3, morale = 80, fire = 80,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i, level = 2, builtAt = i.toLong()) } + Building("pal", PALISADE, NO_PLOT),
            residents = cast.map { Resident(it.id, "2026-01-01") } + jan.residents.filter { it.id.startsWith("n-") },
        )
    }

    private val fit = SceneFit.town(1080, 2400)
    private val october = LocalDate.of(2026, 10, 13)

    /** When the event came: the morning of [october] (epoch ms, in the system's zone as the phone's). */
    private val t0 = october.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun everyone(s: GameState, date: LocalDate) = cast + Residents.people(s, cast, date).filter { p -> cast.none { it.id == p.id } }
    private fun people(s: GameState, date: LocalDate = october) = everyone(s, date).filter { v -> s.residents.any { it.id == v.id } }
    private fun day(s: GameState, date: LocalDate = october) = Whereabouts.day(scenes, s, cast, everyone(s, date), date)

    private fun on(s: GameState, kind: EventKind, strength: Int = 3) = s.copy(event = GameEvent("ev-test", kind, strength, t0, t0 + 36 * 3_600_000L))

    /** Everyone on the map at [hour] of [october], [wall] (epoch ms) on the clock. */
    private fun placed(s: GameState, hour: Float, wall: Long, time: Double = 5.0, festival: Boolean = false) =
        TownPeople.of(s, people(s), time, hour, 10, fit.width, fit.height, festival = festival, date = october, day = day(s), wall = wall)

    private fun stances(s: GameState) = Reactions.stances(s, people(s), s.event!!.kind, s.event!!.strength)

    @Test fun `while wolves are about the defenders face them, the children, the old and the innkeeper are by the fire, the others home`() {
        for (base in listOf(jan, town)) {
            val s = on(base, EventKind.WOLVES)
            val st = stances(s)
            // the leader (the smith) and the shepherd stand up to them; up the watchtower the hunter, when there is one
            assertEquals(Stance.DEFENDER, st["tone"])
            assertEquals(Stance.DEFENDER, st["luka"])
            if (base === town) assertEquals(Stance.LOOKOUT, st["joze"]) else assertEquals(Stance.DEFENDER, st["joze"])
            assertEquals(Stance.CHILD, st["zala"]); assertEquals(Stance.ELDER, st["micka"]); assertEquals(Stance.KEEPER, st["vida"])
            assertEquals(Stance.OTHER, st["n-anze-humar"])
            val day = placed(s, 10.25f, t0 + 3_600_000L)
            val byId = day.associateBy { it.id }
            val threat = Threats.of(s, fit.width, fit.height)!!
            for (id in st.filterValues { it == Stance.DEFENDER }.keys) {
                val p = byId[id] ?: error("$id isn't out against the wolves")
                assertEquals("$id", Activity.GUARD, p.activity)
                assertFalse("$id at night's torch by day", p.lantern)
                // between the wolves and the fire, facing them at a distance
                assertTrue("$id ${threat.distance(p.x, p.y)} from them", threat.distance(p.x, p.y) >= Threats.GUARD)
                assertTrue("$id nearer the fire than they are", hypot(p.x, p.y) < hypot(threat.cx, threat.cy))
                assertEquals("$id faces them", (threat.cx - threat.cy) - (p.x - p.y) < 0f, p.flip)
            }
            if (base === town) {
                val lookout = byId.getValue("joze")
                assertEquals(Activity.WAVE, lookout.activity)
                assertEquals(TownReactions.TOWER_Z, lookout.z)
            }
            // nobody out by the fire who isn't one of the children, the old or the innkeeper; nobody else out at all
            for (p in day) {
                val stance = st[p.id] ?: continue
                when (stance) {
                    Stance.DEFENDER, Stance.LOOKOUT -> Unit
                    Stance.CHILD, Stance.ELDER, Stance.KEEPER -> assertEquals("${p.id}", TownPlace.Fire, p.place)
                    Stance.OTHER -> error("${p.id} is out (${p.activity} at ${p.place}) while the wolves are about")
                }
            }
            assertTrue("${day.count { it.place == TownPlace.Fire }} by the fire", day.count { it.place == TownPlace.Fire } >= 4)
            // at night the defenders carry torches; the children are in bed
            val night = placed(s, 22.4f, t0 + 13 * 3_600_000L).associateBy { it.id }
            for (id in st.filterValues { it == Stance.DEFENDER }.keys) assertTrue("$id's torch", night.getValue(id).lantern)
            for (id in listOf("zala", "tine", "nejc")) assertNull("$id asleep", night[id])
        }
    }

    @Test fun `the bear by the field - defenders against it, nobody near it`() {
        val s = on(town, EventKind.BEAR, strength = 2)
        val threat = Threats.of(s, fit.width, fit.height)!!
        assertEquals(1, threat.beasts.size)
        val day = placed(s, 11f, t0 + 3_600_000L)
        assertEquals(2, day.count { it.activity == Activity.GUARD })
        for (p in day) {
            val keep = if (p.activity == Activity.GUARD) Threats.GUARD else Threats.KEEP
            assertTrue("${p.id} ${threat.distance(p.x, p.y)} from the bear", threat.distance(p.x, p.y) >= keep)
        }
    }

    @Test fun `no way passes within reach of the wolves or the bear, nor anybody walking to their place`() {
        for (base in listOf(jan, town)) for (kind in listOf(EventKind.WOLVES, EventKind.BEAR)) for (hour in listOf(9.25f, 14.5f, 20.9f)) {
            val s = on(base, kind)
            val threat = Threats.of(s, fit.width, fit.height)!!
            val guards = stances(s).filterValues { it == Stance.DEFENDER }.keys
            // second by second from when they come: everyone who wasn't there already stays out of their reach (the
            // defenders out of a shorter one: they face them)
            val start = placed(s, hour, t0).associateBy { it.id }
            var walking = 0
            var t = 0.0
            while (t <= Reactions.WALK_S + 5) {
                for (p in placed(s, hour, t0 + (t * 1000).toLong(), time = 5.0 + t)) {
                    if (p.walking || p.activity == Activity.RUN) walking++
                    val from = start[p.id]
                    if (from != null && threat.distance(from.x, from.y) < Threats.KEEP) continue
                    val keep = if (p.id in guards) Threats.GUARD else Threats.KEEP
                    assertTrue("$kind ${base.age} $hour +${t}s: ${p.id} (${p.activity}) ${threat.distance(p.x, p.y)} from them", threat.distance(p.x, p.y) >= keep - 0.05f)
                }
                t += 0.5
            }
            assertTrue("$kind $hour: nobody went anywhere", walking > 0)
            // a way across the village from anywhere to anywhere goes round them
            val ways = Walkways.of(s, fit.width, fit.height)
            val pts = (0 until 24).map { i -> floatArrayOf(-9f + (i * 7 % 19), -8f + (i * 11 % 17)) }.filter { threat.distance(it[0], it[1]) >= Threats.KEEP + 0.5f }
            for (a in pts) for (b in pts) {
                val way = ways.along(listOf(a, b))
                for (i in 1 until way.size) for (k in 0..20) {
                    val x = way[i - 1][0] + (way[i][0] - way[i - 1][0]) * k / 20f; val y = way[i - 1][1] + (way[i][1] - way[i - 1][1]) * k / 20f
                    assertTrue("$kind: the way ${a.toList()} → ${b.toList()} at ${x}, $y", threat.distance(x, y) >= Threats.KEEP - 0.2f)
                }
            }
        }
    }

    @Test fun `they walk there when it comes, the children running, and are there a little later`() {
        val s = on(jan, EventKind.WOLVES)
        val soon = placed(s, 10.25f, t0 + 8_000L)
        assertTrue("on their way: ${soon.map { it.id to it.activity }}", soon.count { it.walking || it.activity == Activity.RUN } >= 3)
        assertTrue("the children run", soon.filter { it.id in setOf("zala", "tine", "nejc") }.any { it.activity == Activity.RUN })
        val later = placed(s, 10.25f, t0 + 120_000L)
        assertTrue("still walking: ${later.filter { it.walking || it.activity == Activity.RUN }.map { it.id }}", later.none { it.walking || it.activity == Activity.RUN })
        // and the same as for an event on since long (no clock: the tests' and the tools' frames)
        assertEquals(later.map { it.id to it.activity }.toSet(), placed(s, 10.25f, 0L).map { it.id to it.activity }.toSet())
    }

    @Test fun `in a storm they go home, someone hurrying between doors with a hand on the hat, someone taking the washing in`() {
        val s = on(jan, EventKind.STORM)
        val normal = placed(jan, 10.25f, 0L)
        assertTrue(normal.size >= 8)
        val going = placed(s, 10.25f, t0 + 5_000L)
        assertTrue("going home: ${going.map { it.id to it.activity }}", going.count { it.walking || it.activity == Activity.RUN } >= 4)
        assertTrue("a hat held", going.any { it.activity == Activity.HAT })
        // later only a dash now and then, the washing at the half hour: hardly anyone out
        var dashes = 0; var washing = 0
        var m = 9.0 * 60
        while (m < 17 * 60) {
            val out = placed(s, (m / 60).toFloat(), t0 + 8 * 3_600_000L)
            assertTrue("${m / 60}: ${out.map { it.id to it.activity }}", out.size <= 2)
            if (out.any { it.activity == Activity.HAT }) dashes++
            if (out.any { it.activity == Activity.HANG }) washing++
            m += 0.25
        }
        assertTrue("$dashes dashes", dashes > 0)
        assertTrue("$washing at the washing line", washing > 0)
    }

    @Test fun `the merchant - the innkeeper and a few others at his cart by daylight, a few others every quarter of an hour`() {
        val s = on(town, EventKind.MERCHANT)
        val cart = Threats.cart(s, fit.width)
        var seen = HashSet<String>()
        for (q in 0 until 8) {
            val hour = 10f + q * 0.25f + 0.1f
            val at = placed(s, hour, t0 + 3 * 3_600_000L).filter { hypot(it.x - cart[0], it.y - cart[1]) < 2.5f && it.activity == Activity.CHAT }
            assertTrue("$hour: ${at.map { it.id }}", at.any { it.id == "vida" } && at.size in 2..3)
            seen += at.map { it.id }
        }
        assertTrue("$seen", seen.size >= 5)
        // at night nobody stands at the cart
        assertTrue(placed(s, 23.5f, t0 + 3 * 3_600_000L).none { hypot(it.x - cart[0], it.y - cart[1]) < 2.5f })
    }

    @Test fun `a festival - everyone up round the fire, dancing, one of them playing`() {
        val s = on(town, EventKind.FESTIVAL)
        val at = placed(s, 15f, t0 + 3_600_000L)
        val ring = at.filter { it.place == TownPlace.Fire }
        assertTrue("${ring.size} round the fire of ${at.size}", ring.size >= at.size - 2 && ring.size >= 8)
        assertEquals(1, ring.count { it.activity == Activity.MUSIC })
        assertTrue(ring.count { it.activity == Activity.PLAY } >= ring.size - 1)
        // on their way there when it begins
        assertTrue(placed(s, 15f, t0 + 4_000L).count { it.walking } >= 3)
    }

    @Test fun `a won fight - round the fire with the defenders, cheering, then back to their day`() {
        val end = t0 + 2 * 3_600_000L
        val s = town.copy(ended = EventEnd(EventKind.WOLVES, won = true, at = end))
        val st = Reactions.stances(s, people(s), EventKind.WOLVES)
        val cheer = placed(s, 11.1f, end + 90_000L)
        val atFire = cheer.filter { it.place == TownPlace.Fire }
        assertTrue("${atFire.size} cheering", atFire.size >= 10 && atFire.all { it.activity == Activity.CHEER })
        // the defenders first round it, beside it
        for (id in st.filterValues { it == Stance.DEFENDER }.keys) assertTrue("$id", atFire.first { it.id == id }.slot < 3)
        assertEquals(Activity.CHEER, cheer.first { it.id == "joze" }.activity) // up on the tower
        // afterwards their day again, as if nothing had happened
        val after = placed(s, 11.1f, end + ((Reactions.CHEER_S + Reactions.WALK_S + 2) * 1000).toLong())
        assertEquals(placed(town, 11.1f, 0L).map { it.id to it.activity }.toSet(), after.map { it.id to it.activity }.toSet())
        // just after the cheer they walk back
        assertTrue(placed(s, 11.1f, end + ((Reactions.CHEER_S + 6) * 1000).toLong()).count { it.walking } >= 3)
    }

    @Test fun `a lost fight - back to their day, and someone patches up the building it damaged that day and the next`() {
        val end = t0 + 2 * 3_600_000L
        val damaged = town.buildings.first { it.type == HOUSE }
        val s = town.copy(
            buildings = town.buildings.map { if (it.id == damaged.id) it.copy(damaged = true) else it },
            ended = EventEnd(EventKind.WOLVES, won = false, at = end, damaged = listOf(damaged.id)),
        )
        val (who, b) = Reactions.repair(s, people(s), october)!!
        assertEquals("tone", who)
        assertEquals(damaged.id, b.id)
        val later = placed(s, 13f, end + 3_600_000L)
        val tone = later.first { it.id == "tone" }
        assertEquals(Activity.REPAIR, tone.activity)
        val c = VillageLayout.of(s).centers[damaged.plot]
        assertTrue("${tone.x}, ${tone.y} at ${c.toList()}", hypot(tone.x - c[0], tone.y - c[1]) < 2.5f)
        // not at night, nor the day after the next, nor once it's repaired
        assertTrue(placed(s, 23f, end + 3_600_000L).none { it.activity == Activity.REPAIR })
        assertNull(Reactions.repair(s, people(s), october.plusDays(2)))
        assertNotNull(Reactions.repair(s, people(s), october.plusDays(1)))
        assertNull(Reactions.repair(s.copy(buildings = town.buildings), people(s), october))
    }

    @Test fun `what each says of it, by who they are, and the call to the run`() {
        val file = si.lanisce.lani.game.culture.Cultures.reactions("primorska")!!
        val s = on(jan, EventKind.WOLVES)
        val st = stances(s)
        fun say(id: String) = Reactions.says(s, people(s).first { it.id == id }, people(s), october, t0 + 60_000L, file)?.by?.get("sl")
        assertTrue(say("tone")!! in file.wolves.defender.map { it.by["sl"] })
        assertTrue(say("zala")!! in file.wolves.child.map { it.by["sl"] })
        assertTrue(say("micka")!! in file.wolves.elder.map { it.by["sl"] })
        assertTrue(say("vida")!! in file.wolves.keeper.map { it.by["sl"] })
        assertTrue(say("n-anze-humar")!! in file.wolves.other.map { it.by["sl"] })
        assertEquals(Stance.DEFENDER, st["tone"])
        assertEquals("Branimo vas!", Reactions.call(Reactions.stir(s, t0), file)?.by?.get("sl"))
        // the same every time today (the day's dice)
        assertEquals(say("micka"), say("micka"))
        // nothing of it once it's over (and the cheer after a fight is over)
        val over = jan.copy(ended = EventEnd(EventKind.WOLVES, won = true, at = t0))
        assertNull(Reactions.says(over, people(over).first { it.id == "micka" }, people(over), october, t0 + 3_600_000L, file))
        assertNotNull(Reactions.says(over, people(over).first { it.id == "micka" }, people(over), october, t0 + 60_000L, file))
    }

    @Test fun `every pack has its reactions, in its language, short calls`() {
        for (id in listOf("primorska", "friuli", "kaernten", "lakeland")) {
            val r = si.lanisce.lani.game.culture.Cultures.reactions(id)
            assertNotNull("$id/reactions.json", r)
            val lang = si.lanisce.lani.game.culture.Cultures.manifest(id)!!.language
            assertEquals(lang, r!!.language)
            assertTrue(si.lanisce.lani.game.culture.Cultures.checkReactions(id, r).isEmpty())
            for (t in listOf(r.wolves, r.bear, r.storm, r.merchant, r.festival)) {
                assertTrue(t.shout.isNotEmpty() && t.call != null)
                for (sh in t.shout) assertTrue("$id: ${sh.by[lang]}", sh.by.getValue(lang).split(' ').size <= 3)
            }
        }
    }

    @Test fun `the village with wolves on renders within its frame budget`() {
        val s = on(town, EventKind.WOLVES)
        val r = VillageRenderer()
        val c = PixelCanvas(fit.width, fit.height)
        val frame = Frame(time = 3.0, hour = 21.8f, month = 10, people = people(s), today = october.toString(), day = day(s), wall = t0 + 10_000L)
        repeat(10) { r.render(c, s, frame.copy(time = 3.0 + it / 12.0)) }
        val n = 30
        val t1 = System.nanoTime()
        // people on their way there all the while (the most work: the walks and the reactions)
        repeat(n) { r.render(c, s, frame.copy(time = 4.0 + it / 12.0, wall = t0 + 10_000L + it * 83L)) }
        val ms = (System.nanoTime() - t1) / 1e6 / n
        println("VillageRenderer with wolves: %.2f ms/frame (%dx%d)".format(ms, fit.width, fit.height))
        assertTrue("too slow: $ms ms", ms < FrameBudget.scaled(50.0))
        // and the people placed for it alone, cheaply
        val t2 = System.nanoTime()
        repeat(n) { placed(s, 21.8f, t0 + 10_000L + it * 83L, time = 4.0 + it / 12.0) }
        val pm = (System.nanoTime() - t2) / 1e6 / n
        println("TownPeople with wolves: %.2f ms/frame".format(pm))
        assertTrue("too slow: $pm ms", pm < FrameBudget.scaled(12.0))
    }

    @Test fun `snapshots of the reactions for review`() {
        val dir = File("build/village-snapshots").apply { mkdirs() }
        val shots = listOf(
            "70-react-wolves-day" to (on(town, EventKind.WOLVES) to (10.4f to t0 + 3_600_000L)),
            "71-react-wolves-night" to (on(town, EventKind.WOLVES) to (21.6f to t0 + 3_600_000L)),
            "72-react-wolves-coming" to (on(jan, EventKind.WOLVES) to (10.4f to t0 + 9_000L)),
            "73-react-bear" to (on(town, EventKind.BEAR) to (11.2f to t0 + 3_600_000L)),
            "74-react-storm" to (on(jan, EventKind.STORM) to (10.4f to t0 + 6_000L)),
            "75-react-merchant" to (on(town, EventKind.MERCHANT) to (10.4f to t0 + 3_600_000L)),
            "76-react-festival" to (on(town, EventKind.FESTIVAL) to (16.4f to t0 + 3_600_000L)),
            "77-react-won" to (town.copy(ended = EventEnd(EventKind.WOLVES, true, t0)) to (10.4f to t0 + 80_000L)),
        )
        val rig = CameraRig.town(fit, 1080, 2400)
        for ((name, v) in shots) {
            val (s, at) = v
            val (hour, wall) = at
            val comp = VillageLayout.composition(fit.width, fit.height, compact = false)
            for (k in listOf(1, 3)) {
                val r = VillageRenderer()
                val frame = Frame(time = 4.0, hour = hour, month = 10, people = people(s), today = october.toString(), day = day(s), wall = wall)
                // the whole village, and its middle closer at the finest detail (the fire a little below the middle)
                val cam = rig.lookAt(comp.fireX.toFloat(), comp.fireY.toFloat() + 10f, (rig.minScale * k).toFloat())
                val lens = Lens.of(k, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
                val c = PixelCanvas(fit.width, fit.height)
                r.render(c, s, frame.copy(lens = lens))
                val img = java.awt.image.BufferedImage(c.width * 2, c.height * 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
                for (y in 0 until c.height * 2) for (x in 0 until c.width * 2) img.setRGB(x, y, c.pixels[(y / 2) * c.width + x / 2])
                javax.imageio.ImageIO.write(img, "png", File(dir, "$name-k$k.png"))
            }
        }
    }
}
