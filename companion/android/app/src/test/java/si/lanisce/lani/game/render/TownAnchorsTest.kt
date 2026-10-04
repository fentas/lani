package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
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
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.TownPlace
import kotlin.math.hypot

class TownAnchorsTest {
    private fun village(age: Age, vararg types: BuildingType) =
        GameState(seed = 42, age = age, buildings = types.mapIndexed { i, t -> Building("b$i", t, i) })

    private val camp = village(Age.OGENJ, TENT, TENT)
    private val town = village(
        Age.MESTO, FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BuildingType.BEEHIVE, SMITHY, HOUSE, WATCHTOWER,
        FIELD, HOUSE, MARKET, HOUSE, SCHOOL, KOZOLEC, HOUSE, BuildingType.BEEHIVE, HOUSE, WELL, HOUSE, MARKET,
    )

    @Test fun `a tap chooses the plot it is on, or the nearest within reach`() {
        val s = village(Age.VAS, BuildingType.TENT, BuildingType.FIELD)
        val plots = (2 until 14).toList()
        for (p in plots) {
            val c = TownAnchors.plotCenter(s, p, 360, 800)
            assertEquals("$p", p, TownAnchors.plotAt(s, 360, 800, c.x, c.y, plots, reach = 14f))
            // a little off its middle, still that plot
            assertEquals("$p", p, TownAnchors.plotAt(s, 360, 800, c.x + 3f, c.y - 1f, plots, reach = 14f))
        }
        // a plot not to choose isn't chosen, nor anything far from every plot
        val one = TownAnchors.plotCenter(s, 1, 360, 800)
        assertTrue(TownAnchors.plotAt(s, 360, 800, one.x, one.y, plots, reach = 14f) != 1)
        assertEquals(null, TownAnchors.plotAt(s, 360, 800, 2f, 2f, plots, reach = 14f))
        // the rectangle round them holds every middle
        val r = TownAnchors.plotsRect(s, 360, 800, plots, pad = 10f)!!
        for (p in plots) TownAnchors.plotCenter(s, p, 360, 800).let { assertTrue("$p", it.x in r.left..r.right && it.y in r.top..r.bottom) }
        assertEquals(null, TownAnchors.plotsRect(s, 360, 800, emptyList(), pad = 10f))
    }

    @Test fun `short canvases keep the classic framing`() {
        assertEquals(Composition(120, 98, 38), VillageLayout.composition(240, 160, false))
        assertEquals(Composition(135, 181, 121), VillageLayout.composition(270, 297, false))
        // a phone on its side: the horizon above the back row of plots (see VillageLayout.HORIZON)
        assertEquals(Composition(200, 110, 50), VillageLayout.composition(400, 180, false))
        assertEquals(Composition(120, 56, 30), VillageLayout.composition(240, 90, true))
        // the Home header's strip: the Alps keep their height over a small one, a taller one has the whole village below its horizon
        assertEquals(Composition(180, 145, 85), VillageLayout.composition(360, 234, true))
        // a tall one keeps the sky below half of the extra height
        val tall = VillageLayout.composition(360, 800, false)
        assertTrue("fire at ${tall.fireY}", tall.fireY in 340..390)
        assertEquals(tall.fireY - VillageLayout.HORIZON, tall.horizon)
    }

    /** The canvases the full-screen town has (see [SceneFit.town]): a phone upright, a tall tablet, a phone on its side. */
    private val towns = listOf(360 to 800, 320 to 512, 400 to 180)

    @Test fun `every anchor is on the canvas, above its place`() {
        for (s in listOf(camp, town)) for ((w, h) in towns) {
            val a = TownAnchors.of(s, w, h)
            val comp = VillageLayout.composition(w, h, false)
            // a house other than the first is a place of its own, its room's (game/scene/Homes)
            val houses = s.buildings.count { it.type == HOUSE && it.plot in 0 until VillageLayout.MAX_PLOTS }
            assertEquals(s.buildings.map { it.type }.toSet().size + 2 + maxOf(houses - 1, 0), a.size)
            for ((p, pt) in a) assertTrue("$p at $pt on $w×$h", pt.x in 0f..w.toFloat() && pt.y in 0f..h.toFloat())
            val fire = a.getValue(TownPlace.Fire)
            assertEquals(comp.fireX.toFloat(), fire.x)
            assertTrue(fire.y < comp.fireY - 8)
        }
    }

    @Test fun `a building's anchor is the top centre of the first one of its type`() {
        val comp = VillageLayout.composition(360, 800, false)
        val house = TownAnchors.buildingTop(town, HOUSE, comp)!!
        val first = town.buildings.filter { it.type == HOUSE }.minBy { it.plot }
        val c = Terrain.CLASSIC.centers[first.plot]
        assertEquals(comp.fireX + (c[0] - c[1]) * 4f, house.x, 0.01f)
        // above the plot's back corner by the sprite's height
        val back = comp.fireY + (c[0] - 1.5f + c[1] - 1.5f) * 2f
        assertEquals(back - spriteHeight(HOUSE) - 2f, house.y, 0.01f)
        assertNull(TownAnchors.buildingTop(camp, HOUSE, comp))
    }

    @Test fun `the forest spot is at the forest edge, on screen, clear of the other bubbles`() {
        for (s in listOf(camp, town)) {
            val a = TownAnchors.of(s, 360, 800)
            val f = a.getValue(TownPlace.Forest)
            assertTrue("in the forest: $f", TownAnchors.isForest(s, 360, 800, f.x, f.y))
            val home = TownAnchors.home(s, 360, 800)
            assertTrue("in the home view: $f in $home", f.x in home.left..home.right && f.y in home.top..home.bottom)
            for ((p, pt) in a) if (p != TownPlace.Forest) assertTrue("$p crowds the forest", hypot(pt.x - f.x, pt.y - f.y) >= 26f)
        }
    }

    @Test fun `forest taps and clearing taps`() {
        val comp = VillageLayout.composition(360, 800, false)
        assertFalse(TownAnchors.isForest(camp, 360, 800, comp.fireX.toFloat(), comp.fireY.toFloat()))
        assertTrue(TownAnchors.isForest(camp, 360, 800, 10f, 780f))
        assertFalse("the sky is not the forest", TownAnchors.isForest(camp, 360, 800, 10f, 20f))
        assertFalse("off the canvas", TownAnchors.isForest(camp, 360, 800, -5f, 780f))
    }

    @Test fun `home frames the clearing and every building`() {
        for (s in listOf(camp, town)) {
            val home = TownAnchors.home(s, 360, 800)
            for ((p, pt) in TownAnchors.of(s, 360, 800)) if (p is TownPlace.At || p == TownPlace.Fire) {
                assertTrue("$p at $pt outside $home", pt.x in home.left..home.right && pt.y in home.top..home.bottom)
            }
        }
        assertTrue(TownAnchors.home(camp, 360, 800).width < TownAnchors.home(town, 360, 800).width)
    }

    @Test fun `visitors stand by their place`() {
        val comp = VillageLayout.composition(360, 800, false)
        assertNotNull(TownAnchors.visitorSpot(town, TownPlace.At(HOUSE), comp, 360, 800))
        assertNull(TownAnchors.visitorSpot(camp, TownPlace.At(HOUSE), comp, 360, 800))
        val f = TownAnchors.visitorSpot(camp, TownPlace.Forest, comp, 360, 800)!!
        val sx = comp.fireX + (f[0] - f[1]) * 4f; val sy = comp.fireY + (f[0] + f[1]) * 2f
        val spot = TownAnchors.of(camp, 360, 800).getValue(TownPlace.Forest)
        assertTrue("near the forest spot", hypot(sx - spot.x, sy - spot.y) < 16f)
        // a tall canvas has the woods below the village: the visitor waits on the path into the meadow
        val fg = Foreground.of(camp.age, 360, 800)!!
        assertTrue("on the path", fg.onPath(sx, sy, slack = 1f))
        assertTrue("in the woods", TownAnchors.isForest(camp, 360, 800, sx, sy))
        // a short canvas has no woods: the visitor waits just inside the clearing below the forest spot
        val short = VillageLayout.composition(400, 180, false)
        val g = TownAnchors.visitorSpot(camp, TownPlace.Forest, short, 400, 180)!!
        assertFalse("just inside the clearing", TownAnchors.isForest(camp, 400, 180, short.fireX + (g[0] - g[1]) * 4f, short.fireY + (g[0] + g[1]) * 2f))
    }

    @Test fun `named spots resolve inside the canvas for every age`() {
        val woodsOnly = setOf("meadow", "pond", "highseat", "path", "kopa")
        for (age in Age.entries) {
            val s = GameState(seed = 1, age = age)
            for ((w, h) in towns + listOf(360 to 780, 360 to 480)) {
                val comp = VillageLayout.composition(w, h, false)
                val woods = Foreground.of(age, w, h) != null
                for (id in TownAnchors.SPOTS) {
                    val p = TownAnchors.spotAnchor(s, w, h, id)
                    if (!woods && id in woodsOnly) { assertNull("$id without the woods", p); continue }
                    // the charcoal pile only where the canvas goes down that far
                    if (id == "kopa" && !Foreground.of(age, w, h)!!.kopa) { assertNull("the kopa at $age on $w×$h", p); continue }
                    assertNotNull("$id at $age on $w×$h", p)
                    val (x, y) = p!!.let { it[0].toFloat() to it[1].toFloat() }
                    assertTrue("$id at $age on $w×$h: ($x, $y)", x in 4f..(w - 4f) && y in (comp.horizon + 4f)..(h - 4f))
                    // the woods' spots are in the woods, the clearing's spots in the clearing
                    assertEquals("$id at $age on $w×$h", id in woodsOnly || id == "woodpile" && woods, TownAnchors.isForest(s, w, h, x, y))
                }
                assertNull(TownAnchors.spotAnchor(s, w, h, "nowhere"))
            }
        }
        // the woods' spots sit by their things
        val fg = Foreground.of(Age.VAS, 360, 800)!!
        val v = GameState(seed = 1, age = Age.VAS)
        assertTrue(TownAnchors.spotAnchor(v, 360, 800, "path")!!.let { fg.onPath(it[0].toFloat(), it[1].toFloat(), slack = 1f) })
        assertTrue(fg.meadowD(TownAnchors.spotAnchor(v, 360, 800, "meadow")!!.let { it[0].toFloat() } , fg.meadowY - 5f) < 0.6f)
        assertTrue(hypot(TownAnchors.spotAnchor(v, 360, 800, "pond")!![0] - fg.pondX, TownAnchors.spotAnchor(v, 360, 800, "pond")!![1] - fg.pondY) < 16f)
        assertTrue(hypot(TownAnchors.spotAnchor(v, 360, 800, "highseat")!![0] - fg.seatX, TownAnchors.spotAnchor(v, 360, 800, "highseat")!![1] - fg.seatY) < 12f)
        // the burner sits just left of his pile
        val kopa = TownAnchors.spotAnchor(v, 360, 800, "kopa")!!
        assertTrue("${kopa.toList()} by (${fg.kopaX}, ${fg.kopaY})", fg.kopa && kopa[0] < fg.kopaX - 6f && hypot(kopa[0] - fg.kopaX, kopa[1] - fg.kopaY) < 14f)
    }

    @Test fun `the charcoal pile and the high seat are places to tap, where the canvas has them`() {
        val s = village(Age.VAS, HUT, FIELD)
        for (hour in listOf(12f, 23f)) {
            val r = VillageRenderer()
            r.render(PixelCanvas(360, 800), s, Frame(time = 2.0, hour = hour, month = 6))
            val bodies = TownAnchors.bodies(s, 360, 800).toMap()
            // the screen reader's targets hit the same places as a tap on their pixels
            val kopa = bodies.getValue("spot:kopa")
            assertEquals("at $hour", VillageHit.OnSpot("kopa"), r.hitTest(kopa.x.toInt(), kopa.y.toInt(), 2))
            val seat = bodies.getValue("spot:highseat")
            assertEquals("at $hour", VillageHit.OnSpot("highseat"), r.hitTest(seat.x.toInt(), seat.y.toInt(), 2))
            // the whole dome is the pile's, and its glowing vents at night
            val fg = Foreground.of(s.age, 360, 800)!!
            for (dx in -5..5 step 5) assertEquals("at $hour, $dx", VillageHit.OnSpot("kopa"), r.hitTest((fg.kopaX + dx).toInt(), (fg.kopaY - 2f).toInt(), 0))
            // the woods round it are the forest's, as they were
            assertTrue(r.hitTest((fg.kopaX + 40f).toInt(), (fg.kopaY + 30f).toInt(), 0) != VillageHit.OnSpot("kopa"))
            assertTrue(TownAnchors.isForest(s, 360, 800, fg.kopaX + 40f, fg.kopaY + 30f))
        }
        // a canvas with the woods but not so far down (a town's clearing is wide), and one without the woods: no pile, no target;
        // a bubble there is where the woods' are
        val town = village(Age.MESTO, HUT, FIELD)
        for ((st, w, h) in listOf(Triple(town, 360, 480), Triple(s, 400, 180))) {
            assertTrue(Foreground.of(st.age, w, h)?.kopa != true)
            assertNull(TownAnchors.spotAnchor(st, w, h, "kopa"))
            assertTrue(TownAnchors.bodies(st, w, h).none { it.first == "spot:kopa" })
            val all = TownAnchors.withSpots(st, w, h)
            assertEquals(all.getValue(TownPlace.Forest), all.getValue(TownPlace.Spot("kopa")))
            val r = VillageRenderer()
            val c = PixelCanvas(w, h)
            r.render(c, st, Frame(time = 2.0, hour = 23f, month = 6))
            for (y in 0 until h step 2) for (x in 0 until w step 2) assertTrue(r.hitTest(x, y, 0) != VillageHit.OnSpot("kopa"))
        }
        assertTrue(Foreground.of(Age.MESTO, 360, 480) != null)
    }

    @Test fun `the burner sits by his pile, and isn't on a map without it`() {
        val s = village(Age.VAS, HUT)
        val burner = si.lanisce.lani.game.villagers.Villager("oglar", "Oglar Miha", "🧔", "burner", voice = "male", home = listOf("spot:kopa"))
        val at = mapOf("oglar" to TownPlace.Spot("kopa"))
        // by night (when the village is abed) and by day he sits there, facing his pile
        for (hour in listOf(21.5f, 23f, 14f)) {
            val placed = TownPeople.of(s, listOf(burner), 3.0, hour, 6, 360, 800, standing = at).single()
            assertEquals(Activity.WARM, placed.activity)
            assertFalse(placed.flip)
            val g = TownAnchors.visitorSpot(s, TownPlace.Spot("kopa"), VillageLayout.composition(360, 800, false), 360, 800)!!
            assertEquals(g[0], placed.x, 0.01f)
            assertEquals(g[1], placed.y, 0.01f)
        }
        // and a tap on him is his
        val r = VillageRenderer()
        r.render(PixelCanvas(360, 800), s, Frame(time = 3.0, hour = 22f, month = 6, people = listOf(burner), standing = at))
        val head = r.villagerAnchors.getValue("oglar")
        assertEquals(VillageHit.OnVillager("oglar"), r.hitTest(head.x.toInt(), head.y.toInt() + 4, 3))
        // a canvas without the pile: he's away (only his bubble shows, where the woods' are)
        val town = village(Age.MESTO, HUT)
        assertTrue(TownPeople.away(town, TownPlace.Spot("kopa"), 360, 480))
        assertTrue(TownPeople.of(town, listOf(burner), 3.0, 23f, 6, 360, 480, standing = at).isEmpty())
        assertFalse(TownPeople.away(s, TownPlace.Spot("kopa"), 360, 800))
    }

    @Test fun `the woods below the village lie under the clearing on a tall canvas only`() {
        assertNull(Foreground.of(Age.OGENJ, 240, 160))
        assertNull(Foreground.of(Age.MESTO, 400, 180))
        for (s in listOf(camp, town)) for ((w, h) in listOf(360 to 800, 360 to 780, 320 to 512)) {
            val fg = Foreground.of(s.age, w, h)!!
            val comp = VillageLayout.composition(w, h, false)
            assertTrue("the path starts at the clearing's edge", fg.edgeY > comp.fireY + 20f && fg.pathTop < fg.edgeY)
            for (y in listOf(fg.meadowY, fg.pondY, fg.stackY, fg.seatY)) assertTrue("$y on a $w×$h canvas", y > fg.edgeY && y < h - 8f)
            for (x in listOf(fg.meadowX, fg.pondX, fg.stackX, fg.seatX)) assertTrue("$x on a $w×$h canvas", x > 8f && x < w - 8f)
            assertTrue("the meadow is forest for taps", TownAnchors.isForest(s, w, h, fg.meadowX, fg.meadowY))
            assertTrue("the meadow's middle is meadow", fg.meadowD(fg.meadowX, fg.meadowY) < 0.5f && fg.meadowD(fg.meadowX + 60f, fg.meadowY) > 1.4f)
            assertTrue("the pond's middle is water", fg.pondD(fg.pondX, fg.pondY) < 0.5f)
            assertTrue("the path crosses the meadow", fg.onPath(fg.pathX(fg.meadowY), fg.meadowY))
            assertFalse("no tree on the path", fg.treeFree(fg.pathX(fg.meadowY + 40f), fg.meadowY + 40f))
            assertTrue("trees deep in the woods", fg.treeFree(fg.pathX(fg.meadowY + 40f) + 60f, fg.meadowY + 40f))
            // the forest's bubble points at the meadow, inside the home view
            val a = TownAnchors.of(s, w, h).getValue(TownPlace.Forest)
            assertEquals(fg.meadowX, a.x, 0.01f)
            val home = TownAnchors.home(s, w, h)
            assertTrue("$a in $home", a.y in home.top..home.bottom)
        }
    }

    @Test fun `a tap on a place's body hits that place`() {
        val r = VillageRenderer()
        val c = PixelCanvas(360, 800)
        r.render(c, town, Frame(time = 2.0, hour = 11f, month = 6))
        val bodies = TownAnchors.bodies(town, 360, 800).toMap()
        val fire = bodies.getValue("fire")
        assertEquals(VillageHit.OnFire, r.hitTest(fire.x.toInt(), fire.y.toInt(), 2))
        var hits = 0
        for (b in town.buildings) {
            val p = bodies.getValue(b.id)
            if ((r.hitTest(p.x.toInt(), p.y.toInt(), 2) as? VillageHit.OnBuilding)?.building?.id == b.id) hits++
        }
        assertTrue("$hits of ${town.buildings.size} buildings hit at their body", hits >= town.buildings.size - 2) // a few hide behind taller ones
        val forest = bodies.getValue("forest")
        assertTrue(TownAnchors.isForest(town, 360, 800, forest.x, forest.y))
    }

    @Test fun `every named spot has a bubble anchor on the canvas`() {
        for ((w, h) in listOf(240 to 160, 360 to 800)) {
            val state = si.lanisce.lani.game.GameState(seed = 3, age = si.lanisce.lani.game.Age.TABOR)
            val all = TownAnchors.withSpots(state, w, h)
            for (id in si.lanisce.lani.game.scene.TownSpots.ids) {
                val p = all[si.lanisce.lani.game.scene.TownPlace.Spot(id)]
                org.junit.Assert.assertNotNull("$id on $w×$h", p)
                org.junit.Assert.assertTrue("$id inside $w×$h: $p", p!!.x in 0f..w.toFloat() && p.y in 0f..h.toFloat())
            }
        }
    }
}
