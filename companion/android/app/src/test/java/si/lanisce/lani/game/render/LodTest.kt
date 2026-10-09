package si.lanisce.lani.game.render

import si.lanisce.lani.FrameBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WATCHTOWER
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.WorldMarks
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Villager
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The levels of detail, the people who live here and the marks practice leaves on the land: unit checks, and PNG
 * snapshots in build/village-snapshots (40-…) for visual review.
 */
class LodTest {
    private val dir = File("build/village-snapshots").apply { mkdirs() }

    private fun village(age: Age, vararg types: BuildingType, villagers: Int = 8, event: EventKind? = null, world: WorldMarks = WorldMarks()) = GameState(
        seed = 42, age = age, villagers = villagers, morale = 70, fire = 70,
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i, level = if (i % 4 == 3) 2 else 1) },
        event = event?.let { GameEvent("e", it, 3, 0, 0) },
        world = world,
    )

    private val villageTypes = arrayOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE)

    /** A test cast: the people of companion/cultures/primorska/villagers with their homes, and two who moved in. */
    private val cast = listOf(
        Villager(id = "micka", name = "Babica Micka", emoji = "👵", art = "grandma", home = listOf("house", "hut")),
        Villager(id = "janez", name = "Stari Janez", emoji = "👴", art = "grandpa", home = listOf("lipa", "spot:road")),
        Villager(id = "luka", name = "Pastir Luka", emoji = "🐑", art = "shepherd", home = listOf("spot:meadow")),
        Villager(id = "tone", name = "Kovač Tone", emoji = "⚒️", art = "smith", home = listOf("spot:woodpile")),
        Villager(id = "ancka", name = "Teta Ančka", emoji = "📻", art = "aunt", home = listOf("spot:pond")),
        Villager(id = "anton", name = "Čebelar Anton", emoji = "🐝", art = "beekeeper", home = listOf("beehive", "spot:riverbank")),
        Villager(id = "n-ana-furlan", name = "Ana Furlan", emoji = "👩", art = "woman", home = listOf("house", "hut", "tent")),
        Villager(id = "n-zan-furlan", name = "Žan Furlan", emoji = "👨", art = "man", home = listOf("house", "hut", "tent")),
        Villager(id = "n-tim-furlan", name = "Tim Furlan", emoji = "👶", art = "baby", home = listOf("house", "hut", "tent")),
    )

    private val fit = SceneFit.town(1080, 2400)
    private val rig = CameraRig.town(fit, 1080, 2400)
    private val today = "2026-09-24"

    // ---------------------------------------------------------------- units

    @Test fun `the detail level follows the zoom in doublings of the fitted scale`() {
        assertEquals(1, Lens.detail(4f, 4)); assertEquals(1, Lens.detail(5.9f, 4)); assertEquals(2, Lens.detail(6f, 4))
        assertEquals(2, Lens.detail(9.9f, 4)); assertEquals(3, Lens.detail(10f, 4)); assertEquals(3, Lens.detail(16f, 4))
        assertEquals(1, Lens.detail(3f, 3)); assertEquals(2, Lens.detail(4.5f, 3)); assertEquals(3, Lens.detail(7.5f, 3))
        // the rig's crisp scales (the town fitted at 3 ×): whole screen pixels per canvas pixel at each level
        assertEquals(1, rig.detail(4f)); assertEquals(2, rig.detail(6f)); assertEquals(3, rig.detail(9f))
        assertEquals(4f, rig.crisp(4.4f)); assertEquals(6f, rig.crisp(5.4f)); assertEquals(9f, rig.crisp(8f)); assertEquals(12f, rig.crisp(11f)); assertEquals(12f, rig.crisp(13f)); assertEquals(15f, rig.crisp(16f))
        assertEquals(9f, rig.crisp(9f, down = true)); assertEquals(6f, rig.crisp(7.9f, down = true)); assertEquals(3f, rig.crisp(1f))
        for (s in 3..15) {
            val c = rig.crisp(s.toFloat())
            assertEquals("crisp $s → $c", 0f, c % rig.detail(c))
            assertTrue(c in 3f..15f)
        }
        // settling keeps the point under the fingers and lands on a crisp scale
        val c = rig.lookAt(180f, 400f, 6f)
        val z = rig.zoomAt(c, 300f, 900f, 1.37f)
        val r = rig.settle(z, 300f, 900f)
        assertEquals(0f, r.scale % rig.detail(r.scale))
        assertTrue(r.crisp)
        assertEquals(z.toCanvasX(300f), r.toCanvasX(300f), 1.5f)
        // the home view frames at a crisp scale too
        val home = rig.frame(TownAnchors.home(village(Age.VAS, *villageTypes), fit.width, fit.height), floatArrayOf(0f, 300f, 1080f, 2100f), maxFrame = rig.minScale * 2)
        assertEquals(0f, home.scale % rig.detail(home.scale))
    }

    @Test fun `a lens shows the camera's window at k pixels per nominal pixel, inside the canvas`() {
        val cam = rig.lookAt(180f, 400f, 6f)
        val l = Lens.of(2, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
        assertEquals(2, l.k)
        // the view is 180 × 400 nominal px, so the k = 2 canvas just covers it: the window starts where the view does
        assertEquals(cam.toCanvasX(0f) * 2, l.x0.toFloat(), 1f)
        assertEquals(cam.toCanvasY(0f) * 2, l.y0.toFloat(), 1f)
        assertEquals(135f, l.bx(l.cx(135f)), 0.001f)
        // at 15× (k = 3) the view is 72 × 160 nominal px: the window has a margin around it and stays inside the canvas
        val far = rig.lookAt(fit.width - 2f, fit.height - 2f, 15f)
        val l3 = Lens.of(3, far, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
        assertEquals(3, l3.k)
        assertTrue("${l3.x0}", l3.x0 in 0..(fit.width * 3 - fit.width))
        assertTrue("${l3.y0}", l3.y0 in 0..(fit.height * 3 - fit.height))
        assertTrue(Lens().nominal); assertFalse(l.nominal)
    }

    @Test fun `felled trees grow back in stages over two weeks`() {
        assertEquals(Regrowth.STUMP, Regrowth.of(0)); assertEquals(Regrowth.STUMP, Regrowth.of(2))
        assertEquals(Regrowth.SAPLING, Regrowth.of(3)); assertEquals(Regrowth.SAPLING, Regrowth.of(6))
        assertEquals(Regrowth.YOUNG, Regrowth.of(7)); assertEquals(Regrowth.YOUNG, Regrowth.of(13))
        assertEquals(Regrowth.GROWN, Regrowth.of(14)); assertEquals(Regrowth.GROWN, Regrowth.of(400))
        assertEquals(23, Regrowth.days("2026-09-01", "2026-09-24"))
        assertEquals(1, Regrowth.days("2026-02-28", "2026-03-01"))
        assertEquals(2, Regrowth.days("2024-02-28", "2024-03-01")) // a leap year
        assertEquals(0, Regrowth.days("2026-09-24", "2026-09-24"))
        assertEquals(0, Regrowth.days("2026-09-30", "2026-09-24")) // a clock set back: still fresh, never negative
        assertEquals(Regrowth.GROWN_DAYS, Regrowth.days("garbage", "2026-09-24"))
        assertEquals(Regrowth.GROWN_DAYS, Regrowth.days("2026-09-24", ""))
    }

    @Test fun `the people who live here go about their day by daylight and are home at night`() {
        val s = village(Age.VAS, *villageTypes)
        val w = fit.width; val h = fit.height
        val day = TownPeople.of(s, cast, time = 3.0, hour = 11f, month = 6, w = w, h = h)
        assertEquals("everyone but the baby", cast.size - 1, day.size)
        val comp = VillageLayout.composition(w, h, false)
        for (p in day) {
            val a = TownPeople.headAnchor(p, comp, 7f)
            assertTrue("${p.id} at $a", a.x in 0f..w.toFloat() && a.y in comp.horizon.toFloat()..h.toFloat())
            assertEquals(p.villager, cast.first { it.id == p.id })
        }
        for (a in day) for (b in day) if (a !== b && !a.walking && !b.walking) assertFalse("${a.id} on ${b.id}", TownPeople.overlap(a, b))
        // over the day they work, rest and walk from one place to the next: the smith at his anvil, Luka with the sheep,
        // Micka in her garden and at the well, and whoever is on the way between
        val seen = HashMap<String, MutableSet<Activity>>()
        for (hour in 6..19) for (t in 0 until 12) {
            for (p in TownPeople.of(s, cast, time = t * 2.3, hour = hour + t / 12f, month = 6, w = w, h = h)) seen.getOrPut(p.id) { HashSet() } += p.activity
        }
        assertTrue("${seen["tone"]}", Activity.HAMMER in seen.getValue("tone"))
        assertTrue("${seen["luka"]}", seen.getValue("luka").any { it == Activity.TEND || it == Activity.SIT })
        val all = seen.values.flatten().toSet()
        for (a in listOf(Activity.HOE, Activity.HAMMER, Activity.TEND, Activity.SIT, Activity.WALK)) assertTrue("$a in $all", a in all)
        // half past eleven at night: everyone is home in bed; a dark December evening: who is out sits by the fire, or is on
        // the way there or home
        assertTrue(TownPeople.of(s, cast, time = 3.0, hour = 23.5f, month = 6, w = w, h = h).isEmpty())
        val evening = TownPeople.of(s, cast, time = 3.0, hour = 19.5f, month = 12, w = w, h = h, date = java.time.LocalDate.of(2026, 12, 9))
        assertTrue("$evening", evening.all { it.activity == Activity.WARM || it.walking })
        assertTrue(TownPeople.of(s, cast, 3.0, 11f, 6, w, h, storm = true).isEmpty())
        // a festival: everyone round the fire
        assertTrue(TownPeople.of(s, cast, 3.0, 20f, 6, w, h, festival = true).all { it.x * it.x + it.y * it.y < 30f })
        // pure: the same inputs give the same answer
        assertEquals(day, TownPeople.of(s, cast, time = 3.0, hour = 11f, month = 6, w = w, h = h))
    }

    @Test fun `taps land on the people, the spots and the pond, and the bubbles follow the people`() {
        val s = village(Age.VAS, *villageTypes)
        val r = VillageRenderer()
        val c = PixelCanvas(fit.width, fit.height)
        r.render(c, s, Frame(time = 2.0, hour = 11f, month = 6, people = cast, today = today))
        assertEquals(cast.size - 1, r.villagerAnchors.size)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        var hit = 0
        for (p in TownPeople.of(s, cast, 2.0, 11f, 6, fit.width, fit.height, date = java.time.LocalDate.parse(today))) {
            val a = r.villagerAnchors.getValue(p.id)
            val feetY = comp.fireY + (p.x + p.y) * 2f - p.z
            if (r.hitTest(a.x.toInt(), (feetY - 3f).toInt(), 3) == VillageHit.OnVillager(p.id)) hit++
        }
        assertTrue("$hit people hit", hit >= 6)
        val pile = TownAnchors.spotAnchor(s, fit.width, fit.height, "woodpile")!!
        assertEquals(VillageHit.OnSpot("woodpile"), r.hitTest(pile[0] - 2, pile[1] - 8, 4))
        // the rocks, in a village where no building stands in front of them
        val hamlet = village(Age.ZASELEK, FIELD, HUT, WELL, KOZOLEC, TENT, PALISADE, HUT, FIELD)
        val rh = VillageRenderer().also { it.render(PixelCanvas(fit.width, fit.height), hamlet, Frame(time = 2.0, hour = 11f, month = 6, today = today)) }
        val rocks = TownAnchors.spotAnchor(hamlet, fit.width, fit.height, "rocks")!!
        assertEquals(VillageHit.OnSpot("rocks"), rh.hitTest(rocks[0] - 5, rocks[1] - 3, 2))
        val fg = Foreground.of(s.age, fit.width, fit.height)!!
        assertEquals(VillageHit.OnSpot("pond"), r.hitTest(fg.pondX.toInt(), fg.pondY.toInt(), 1))
        assertEquals(VillageHit.OnSpot("meadow"), r.hitTest((fg.meadowX - 25f).toInt(), (fg.meadowY - 10f).toInt(), 4))
        // the bank of the stream above the bridge
        val wx = Terrain.CLASSIC.streamX(2.6f)
        assertEquals(VillageHit.OnSpot("riverbank"), r.hitTest((comp.fireX + (wx - 2.6f) * 4f).toInt(), (comp.fireY + (wx + 2.6f) * 2f).toInt(), 1))
        // and the water up and down the stream's course through the clearing opens its place too (where the palisade's
        // stakes stand in it, at its water gates, they are the palisade's; behind the church it is the church's)
        for (sy in floatArrayOf(-3f, 1.8f, 12f)) {
            val sx = Terrain.CLASSIC.streamX(sy)
            assertEquals("the stream at $sy", VillageHit.OnSpot("riverbank"), r.hitTest((comp.fireX + (sx - sy) * 4f).toInt(), (comp.fireY + (sx + sy) * 2f).toInt(), 1))
        }
        // the bodies TalkBack finds include the spots
        val bodies = TownAnchors.bodies(s, fit.width, fit.height).toMap()
        for (id in listOf("spot:woodpile", "spot:rocks", "spot:pond", "spot:meadow", "spot:riverbank")) assertNotNull(id, bodies[id])
        // the same village at a closer look hits the same person through the lens
        val cam = rig.lookAt(r.villagerAnchors.getValue("tone").x, r.villagerAnchors.getValue("tone").y, 12f)
        val lens = Lens.of(3, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
        val c3 = PixelCanvas(fit.width, fit.height)
        r.render(c3, s, Frame(time = 2.0, hour = 11f, month = 6, people = cast, today = today, lens = lens))
        assertEquals(lens, r.lens)
        val tone = TownPeople.of(s, cast, 2.0, 11f, 6, fit.width, fit.height, date = java.time.LocalDate.parse(today)).first { it.id == "tone" }
        val fx = lens.cx(comp.fireX + (tone.x - tone.y) * 4f); val fy = lens.cy(comp.fireY + (tone.x + tone.y) * 2f)
        assertEquals(VillageHit.OnVillager("tone"), r.hitTest(fx.toInt(), fy.toInt() - 12, 6))
    }

    // ---------------------------------------------------------------- snapshots

    /** Renders the nominal region the k-lens covers at k = 1 (scaled up by k: the "bigger pixels" look) and at the lens's detail, for comparing. */
    private fun lodShot(name: String, s: GameState, frame: Frame, cx: Float, cy: Float) {
        val r = VillageRenderer()
        for (k in 1..3) {
            val cam = rig.lookAt(cx, cy, (rig.minScale * k).toFloat())
            val lens = Lens.of(k, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val c = PixelCanvas(fit.width, fit.height)
            r.render(c, s, frame.copy(lens = lens))
            // the same region of the world in every picture: at k = 1 the window's part of the nominal canvas, blown up
            val img = BufferedImage(fit.width * 2, fit.height * 2, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until fit.height * 2) for (x in 0 until fit.width * 2) img.setRGB(x, y, c.pixels[(y / 2) * fit.width + x / 2])
            ImageIO.write(img, "png", File(dir, "$name-k$k.png"))
            if (k == 1) {
                val l3 = Lens.of(3, rig.lookAt(cx, cy, 12f), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
                val crop = BufferedImage(fit.width * 2, fit.height * 2, BufferedImage.TYPE_INT_RGB)
                for (y in 0 until fit.height * 2) for (x in 0 until fit.width * 2) {
                    val nx = l3.bx(x / 2f).toInt().coerceIn(0, fit.width - 1); val ny = l3.by(y / 2f).toInt().coerceIn(0, fit.height - 1)
                    crop.setRGB(x, y, c.pixels[ny * fit.width + nx])
                }
                ImageIO.write(crop, "png", File(dir, "$name-k1-blownup.png"))
            }
        }
    }

    @Test fun `render the same village at every level of detail`() {
        val s = village(Age.VAS, *villageTypes, villagers = 10)
        val frame = Frame(time = 3.0, hour = 11f, month = 6, people = cast, today = today)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        lodShot("40-lod-fire", s, frame, comp.fireX.toFloat(), comp.fireY.toFloat() - 6f)
        val house = TownAnchors.top(s.buildings.first { it.type == HOUSE }, s, fit.width, fit.height)
        lodShot("41-lod-house", s, frame, house.x, house.y + 10f)
        lodShot("42-lod-house-evening", s, frame.copy(hour = 19.5f, month = 10), house.x, house.y + 10f)
        val fg = Foreground.of(s.age, fit.width, fit.height)!!
        lodShot("43-lod-woods", s, frame.copy(month = 9), fg.meadowX, fg.meadowY - 8f)
    }

    @Test fun `render the people at their homes through the day`() {
        val s = village(Age.VAS, *villageTypes, villagers = 10)
        val r = VillageRenderer()
        for ((name, hour) in listOf("morning" to 8f, "noon" to 12.5f, "night" to 23f)) {
            val c = PixelCanvas(fit.width, fit.height)
            r.render(c, s, Frame(time = 4.0, hour = hour, month = 6, people = cast, today = today))
            write(c, File(dir, "44-people-$name.png"), 2)
        }
        // and closer: k = 2 over the clearing at noon, k = 3 at the woodpile and the pond
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val fg = Foreground.of(s.age, fit.width, fit.height)!!
        for ((name, at, k) in listOf(
            Triple("45-people-noon-k2", floatArrayOf(comp.fireX.toFloat(), comp.fireY - 4f), 2),
            Triple("46-people-woodpile-k3", floatArrayOf(fg.stackX - 4f, fg.stackY + 2f), 3),
            Triple("47-people-pond-k3", floatArrayOf(fg.pondX + 4f, fg.pondY + 6f), 3),
            Triple("48-people-meadow-k3", floatArrayOf(fg.meadowX - 12f, fg.meadowY + 2f), 3),
        )) {
            val cam = rig.lookAt(at[0], at[1], (rig.minScale * k).toFloat())
            val lens = Lens.of(k, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val c = PixelCanvas(fit.width, fit.height)
            r.render(c, s, Frame(time = 5.0, hour = 12.5f, month = 6, people = cast, today = today, lens = lens))
            write(c, File(dir, "$name.png"), 2)
        }
    }

    @Test fun `render the woodpile as trees are felled and grow back`() {
        val ages = listOf(0, 1, 3, 4, 8, 10, 15, 20)
        fun daysAgo(n: Int) = java.time.LocalDate.parse(today).minusDays(n.toLong()).toString()
        val fg = Foreground.of(Age.ZASELEK, fit.width, fit.height)!!
        for ((n, marks) in listOf(0 to emptyList(), 1 to listOf(0), 3 to listOf(5, 2, 0), 8 to ages.reversed())) {
            val world = WorldMarks(felled = marks.map(::daysAgo), quarried = if (n >= 3) listOf(daysAgo(1), daysAgo(0), daysAgo(0)) else emptyList(), picked = if (n >= 3) listOf(daysAgo(0)) else emptyList())
            val s = village(Age.ZASELEK, FIELD, HUT, WELL, KOZOLEC, TENT, PALISADE, HUT, FIELD, world = world)
            val r = VillageRenderer()
            val cam = rig.lookAt(fg.stackX + 4f, fg.stackY - 6f, rig.minScale * 2f)
            val lens = Lens.of(2, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val c = PixelCanvas(fit.width, fit.height)
            r.render(c, s, Frame(time = 3.0, hour = 10f, month = 8, people = cast, today = today, lens = lens))
            write(c, File(dir, "49-felled-$n-k2.png"), 2)
            if (n == 8) {
                // the whole village with everything marked, at the nominal detail
                val full = PixelCanvas(fit.width, fit.height)
                r.render(full, s, Frame(time = 3.0, hour = 10f, month = 8, people = cast, today = today))
                write(full, File(dir, "49-felled-$n-k1-full.png"), 2)
                // the marks change the picture: stumps where trees stood, more logs on the pile
                val plain = PixelCanvas(fit.width, fit.height)
                r.render(plain, s.copy(world = WorldMarks()), Frame(time = 3.0, hour = 10f, month = 8, people = cast, today = today))
                assertNotEquals(full.pixels.toList(), plain.pixels.toList())
            }
        }
    }

    @Test fun `every level of detail renders within the frame budget`() {
        val s = village(Age.VAS, *villageTypes, villagers = 10)
        val r = VillageRenderer()
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val c = PixelCanvas(fit.width, fit.height)
        val n = 40
        for (k in 1..3) {
            val cam = rig.lookAt(comp.fireX.toFloat(), comp.fireY.toFloat(), (rig.minScale * k).toFloat())
            val lens = Lens.of(k, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val frame = Frame(time = 0.0, hour = 21f, month = 9, people = cast, today = today, lens = lens)
            repeat(15) { r.render(c, s, frame.copy(time = it / 12.0)) }
            r.profile = true
            val t0 = System.nanoTime()
            repeat(n) { r.render(c, s, frame.copy(time = 2 + it / 12.0)) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            r.profile = false
            println("VillageRenderer: %.2f ms/frame (1080×2400 town, night, k=%d, %d×%d): %s".format(ms, k, fit.width, fit.height, r.lastProfile))
            assertTrue("k=$k too slow: $ms ms", ms < FrameBudget.scaled(30.0))
        }
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }
}
