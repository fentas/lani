package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType.*
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Sleep
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.Whereabouts
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.ui.game.BubbleGroup
import si.lanisce.lani.ui.game.TownTargets
import si.lanisce.lani.ui.game.fireKeepOff
import si.lanisce.lani.ui.game.layoutBubbles
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The people round the fire ([TownPeople.aroundFire]): beside it, never in its flames; the fire still the easiest place
 * to tap with them there (the renderer's [VillageRenderer.hitTest]) and still in TalkBack's tree (its target drawn over
 * theirs, see TownView). The morning Luka warms his hands there, France, Tine and Vida with him; a December evening; a feast.
 */
class FireRingTest {
    private val cultures = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }
    private val cast: List<Villager> by lazy {
        File(cultures, "primorska/villagers").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }.sortedBy { it.order }
    }
    private val scenes: List<SceneSpec> by lazy {
        listOf(File(cultures, "../scenes"), File(cultures, "primorska/scenes")).flatMap { d -> d.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }
    }

    private fun town(age: Age = Age.VAS): GameState {
        val types = listOf(HUT, FIELD, WELL, TENT, KOZOLEC, HOUSE, LIPA, BEEHIVE, HOUSE, SMITHY, MARKET, HOUSE, SCHOOL, WATCHTOWER, HOUSE, CHURCH)
        return GameState(
            seed = 42, age = age, villagers = cast.size, morale = 80, fire = 100,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i, level = 2, builtAt = i.toLong()) },
            residents = cast.map { Resident(it.id, "2026-01-01") },
        )
    }

    private val fit = SceneFit.town(1080, 2400)
    private val june = LocalDate.of(2026, 6, 24)
    private val december = LocalDate.of(2026, 12, 9)

    /** Luka's happening at the fire on ("Luka si greje roke"), and France, Tine and Vida called there too. */
    private val luka = mapOf("luka" to TownPlace.Fire)
    private fun day(state: GameState, calls: Map<String, TownPlace> = mapOf("france" to TownPlace.Fire, "tine" to TownPlace.Fire, "vida" to TownPlace.Fire)) =
        Routine.Day(homes = Sleep.homes(scenes, state, cast), tellers = Whereabouts.tellers(scenes), calls = calls)

    private fun morning(state: GameState) =
        TownPeople.of(state, cast, 3.0, 8.5f, 6, fit.width, fit.height, standing = luka, date = june, day = day(state))

    /**
     * Whether someone at world ([x], [y]) would be in the fire of [scale]: their body (4 px to either side, 8 tall, 5
     * seated) over its flames as tall and wide as they get (Effects.flames at full strength) or its ring of stones.
     */
    private fun inFlames(x: Float, y: Float, scale: Float, seated: Boolean = false): Boolean {
        val sx = (x - y) * 4f; val sy = (x + y) * 2f
        val fh = 15f * scale
        val half = max(0.65f * fh, 6.5f * scale + 1f)
        return abs(sx) < half + 4f && sy > -(fh + 3f) && sy - (if (seated) 5f else 8f) < 3.2f * scale + 1f
    }

    @Test fun `round the fire nobody stands in its flames, nor on each other`() {
        for (scale in TownAnchors.FIRE_SCALE) {
            val spots = (0 until 16).map { TownPeople.aroundFire(it, scale) }
            for ((i, p) in spots.withIndex()) assertFalse("slot $i at $scale: ${p.toList()}", inFlames(p[0], p[1], scale))
            for (i in spots.indices) for (j in 0 until i) {
                val a = spots[i]; val b = spots[j]
                assertTrue("slots $j and $i at $scale", hypot((a[0] - a[1] - b[0] + b[1]) * 4f, (a[0] + a[1] - b[0] - b[1]) * 2f) >= 6f)
            }
            // the first on the right, the next on the left: both beside it
            assertTrue((spots[0][0] - spots[0][1]) > 0f && (spots[1][0] - spots[1][1]) < 0f)
        }
    }

    @Test fun `the morning Luka warms his hands at the fire, the others round it`() {
        for (age in Age.entries) {
            val state = town(age)
            val scale = TownAnchors.FIRE_SCALE[age.ordinal]
            val there = morning(state).filter { it.place == TownPlace.Fire && !it.walking }
            assertEquals("$age: $there", setOf("luka", "france", "tine", "vida"), there.map { it.id }.toSet())
            for (p in there) {
                assertFalse("$age: ${p.id} in the fire at ${p.x}, ${p.y}", inFlames(p.x, p.y, scale, p.seated))
                assertEquals("$age: ${p.id} faces the fire", p.x - p.y > 0f, p.flip)
            }
            // Luka, whose happening it is, first: beside the fire on its right, where the fire's visitors stand
            val l = there.single { it.id == "luka" }
            assertEquals(0, l.slot)
            val v = TownAnchors.visitorSpot(state, TownPlace.Fire, VillageLayout.composition(fit.width, fit.height, false), fit.width, fit.height)!!
            assertTrue(abs(l.x - v[0]) < 1e-4f && abs(l.y - v[1]) < 1e-4f)
            for (a in there) for (b in there) if (a !== b) assertFalse("$age: ${a.id} on ${b.id}", TownPeople.overlap(a, b))
        }
    }

    @Test fun `in the evening and at a feast they sit and stand round it, not in it`() {
        val state = town()
        val scale = TownAnchors.FIRE_SCALE[state.age.ordinal]
        val evening = TownPeople.of(state, cast, 3.0, 19f, 12, fit.width, fit.height, date = december, day = day(state, emptyMap()))
            .filter { it.place == TownPlace.Fire && !it.walking }
        assertTrue("$evening", evening.size >= 2)
        val feast = TownPeople.of(state, cast, 3.0, 15f, 6, fit.width, fit.height, festival = true, date = june, day = day(state, emptyMap()))
        assertTrue(feast.size >= 6)
        for (p in evening + feast) assertFalse("${p.id} in the fire at ${p.x}, ${p.y}", inFlames(p.x, p.y, scale, p.seated))
    }

    @Test fun `with people round it a tap on the flames is the fire's, a tap on someone theirs`() {
        val state = town()
        val placed = morning(state)
        val r = VillageRenderer()
        r.render(PixelCanvas(fit.width, fit.height), state, Frame(time = 2.0, hour = 8.5f, month = 6, people = cast, today = june.toString(), standing = luka, placed = placed))
        val rect = r.hitRects().single { it.first == null }.second
        // anywhere on the fire, a finger's reach (8 px) round: the fire, before whoever stands beside it (a building behind
        // it that shows there, above the flames, is that building's)
        var fires = 0; var all = 0
        for (y in rect[1]..rect[3]) for (x in rect[0]..rect[2]) {
            val hit = r.hitTest(x, y, 8)
            assertTrue("at $x, $y: $hit", hit == VillageHit.OnFire || hit is VillageHit.OnBuilding)
            all++; if (hit == VillageHit.OnFire) fires++
        }
        assertTrue("$fires of $all", fires >= all * 0.8)
        // the flames' middle, as the screen reader's target points
        val fire = TownAnchors.bodies(state, fit.width, fit.height).toMap().getValue("fire")
        assertEquals(VillageHit.OnFire, r.hitTest(fire.x.roundToInt(), fire.y.roundToInt(), 8))
        // each of them, on their body just under the head: them
        for (p in placed.filter { it.place == TownPlace.Fire }) {
            val a = r.villagerAnchors.getValue(p.id)
            assertEquals(p.id, VillageHit.OnVillager(p.id), r.hitTest(a.x.roundToInt(), a.y.roundToInt() + 4, 2))
        }
    }

    /**
     * Which of [boxes] ([left, top, right, bottom], drawn in this order) a screen reader still finds: Compose leaves out a
     * node the ones drawn after it cover entirely.
     */
    private fun shown(boxes: List<IntArray>): List<Boolean> {
        val xs = boxes.flatMap { listOf(it[0], it[2]) }.distinct().sorted()
        val ys = boxes.flatMap { listOf(it[1], it[3]) }.distinct().sorted()
        val covered = Array(xs.size) { BooleanArray(ys.size) }
        val out = BooleanArray(boxes.size)
        for (i in boxes.indices.reversed()) {
            val b = boxes[i]
            for (ix in xs.indices) for (iy in ys.indices) {
                if (xs[ix] < b[0] || xs[ix] >= b[2] || ys[iy] < b[1] || ys[iy] >= b[3] || covered[ix][iy]) continue
                covered[ix][iy] = true; out[i] = true
            }
        }
        return out.toList()
    }

    @Test fun `the fire's target stays in TalkBack's tree with people round it, and so do theirs`() {
        val state = town()
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val there = morning(state).filter { it.place == TownPlace.Fire }
        val fire = TownAnchors.bodies(state, fit.width, fit.height).toMap().getValue("fire")
        val density = 2.625f // a 1080 × 2400 phone
        // the village fitted (3 screen px a nominal one), the home view (up to twice that), a closer look
        for (zoom in listOf(3f, 6f, 12f)) {
            val fireBox = TownTargets.place(fire.x * zoom, fire.y * zoom, (TownTargets.FIRE_DP * density).roundToInt())
            val people = there.map { p ->
                val a = TownPeople.headAnchor(p, comp, if (p.seated) 7f * 0.7f else 7f)
                TownTargets.person(a.x * zoom, a.y * zoom, (TownTargets.DP * density).roundToInt())
            }
            fun meets(a: IntArray, b: IntArray) = a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]
            if (zoom == 3f) assertTrue("they crowd the fire's target", people.count { meets(it, fireBox) } >= 2)
            // as TownView draws them: the people's targets, then the fire's over them
            val shown = shown(people + listOf(fireBox))
            assertTrue("the fire's target at $zoom", shown.last())
            for ((i, p) in there.withIndex()) assertTrue("${p.id}'s target at $zoom", shown[i])
        }
    }

    @Test fun `the bubbles over the people round the fire keep off its flames and its target`() {
        val state = town()
        val placed = morning(state)
        val r = VillageRenderer()
        r.render(PixelCanvas(fit.width, fit.height), state, Frame(time = 2.0, hour = 8.5f, month = 6, people = cast, today = june.toString(), standing = luka, placed = placed))
        // the box the bubbles keep off has the renderer's (the fitted canvas: its pixels are nominal ones)
        val rect = r.hitRects().single { it.first == null }.second
        val area = TownAnchors.fireArea(state, fit.width, fit.height)
        assertTrue("${area.toList()} ⊇ ${rect.toList()}", area[0] <= rect[0] + 1 && area[1] <= rect[1] + 1 && area[2] >= rect[2] - 1 && area[3] >= rect[3] - 1)
        val body = TownAnchors.fireBody(state, fit.width, fit.height)
        assertEquals(TownAnchors.bodies(state, fit.width, fit.height).toMap().getValue("fire"), body)
        // Luka's happening, France's, Tine's and Vida's requests over their heads, and one over the fire itself
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val heads = placed.filter { it.place == TownPlace.Fire && !it.walking }.map { r.villagerAnchors.getValue(it.id) } + TownAnchors.fireTop(state, comp)
        assertEquals(5, heads.size)
        val density = 2.625f
        var covered = 0
        for (zoom in listOf(3f, 4.5f, 6f, 9f, 12f)) {
            val cam = Camera(zoom, 540f - body.x * zoom, 1200f - body.y * zoom)
            fun dp(x: Float, y: Float) = cam.toScreenX(x) / density to cam.toScreenY(y) / density
            val groups = heads.map { val (x, y) = dp(it.x, it.y); BubbleGroup(x, y, 1) }
            fun lay(keep: Boolean) = layoutBubbles(groups, size = 52f, spacing = 54f, lift = 40f, minX = 30f, maxX = 1080f / density - 30f, minY = 60f, keepOff = if (keep) fireKeepOff(area, body, cam, density) else null).flatten()
            // the flames, stones and smoke the renderer hits, and the screen reader's target, on screen (dp)
            val (fl, ft) = dp(rect[0].toFloat(), rect[1].toFloat()); val (fr, fb) = dp(rect[2] + 1f, rect[3] + 1f)
            val target = TownTargets.place(cam.toScreenX(body.x), cam.toScreenY(body.y), (TownTargets.FIRE_DP * density).roundToInt()).map { it / density }
            fun onFire(x: Float, y: Float, half: Float) =
                (x + half > fl && x - half < fr && y + half > ft && y - half < fb) || (x + half > target[0] && x - half < target[2] && y + half > target[1] && y - half < target[3])
            covered += lay(false).count { onFire(it.x, it.y, 26f) }
            val laid = lay(true)
            // each bubble's touch box (52 dp) as it bobs (±3 dp) off both: a tap on the fire reaches the village, which hits the fire
            for ((i, b) in laid.withIndex()) assertFalse("zoom $zoom: bubble $i at $b on the fire", onFire(b.x, b.y, 29f))
            // and off each other, their boxes (the bubble and its air) too
            for (i in laid.indices) for (j in i + 1 until laid.size) {
                assertTrue("zoom $zoom: ${laid[i]} on ${laid[j]}", abs(laid[i].x - laid[j].x) >= 52f - 0.01f || abs(laid[i].y - laid[j].y) >= 52f - 0.01f)
            }
            // still over their heads
            for ((i, b) in laid.withIndex()) assertTrue("zoom $zoom: bubble $i at $b over ${groups[i]}", b.y < groups[i].y && abs(b.x - groups[i].x) < 120f)
        }
        assertTrue("left alone, their bubbles would be on the fire ($covered)", covered >= 3)
    }
}
