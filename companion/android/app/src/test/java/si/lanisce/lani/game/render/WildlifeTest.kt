package si.lanisce.lani.game.render

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * The wild animals round the village on the map (see [Wildlife]): who is about by the time of day and the season, a
 * different mix every day, outside the palisade, tapped right on, within the frame budget; and PNG snapshots in
 * build/village-snapshots (60-…) for visual review.
 */
class WildlifeTest {
    private val dir = File("build/village-snapshots").apply { mkdirs() }

    private val villageTypes = arrayOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE)

    /** A village of [age] with the buildings of [types] its plots have room for. */
    private fun village(age: Age = Age.VAS, types: Array<BuildingType> = villageTypes, seed: Long = 42, event: EventKind? = null) = GameState(
        seed = seed, age = age, villagers = 8, morale = 70, fire = 70,
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i, level = if (i % 4 == 3) 2 else 1) }
            .filter { !it.type.onPlot || it.plot < si.lanisce.lani.game.PLOTS_PER_AGE[age.ordinal] },
        event = event?.let { GameEvent("e", it, 3, 0, 0) },
    )

    private val fit = SceneFit.town(1080, 2400)
    private val start = LocalDate.parse("2026-09-01").toEpochDay()

    /** How many of its pixels each animal shows at least (about half its sprite, at the nominal detail: a trunk may stand in front). */
    private val SHOWS = mapOf(
        Wild.DEER to 12, Wild.FOX to 14, Wild.HEDGEHOG to 8, Wild.HARE to 9, Wild.BOARS to 22, Wild.BADGER to 12,
        Wild.OWL to 5, Wild.SQUIRREL to 6,
    )

    /** The first day from [from] on whose [window] at [hour] in [month] has [kinds] about (at night: the evening's date). */
    private fun dayWith(seed: Long, hour: Float, month: Int, vararg kinds: Wild, from: Long = start): String {
        for (d in from until from + 4000) {
            val today = LocalDate.ofEpochDay(d).toString()
            val plan = Wildlife.plan(seed, today, hour, month).map { it.kind }
            if (kinds.all { it in plan }) return today
        }
        error("no day with ${kinds.toList()}")
    }

    // ---------------------------------------------------------------- when

    @Test fun `the windows follow the sun of the month`() {
        // September: the sun rises about 6:50 and sets about 19:20
        assertEquals(WildTime.NIGHT, Wildlife.time(3f, 9))
        assertEquals(WildTime.DAWN, Wildlife.time(6.5f, 9))
        assertEquals(WildTime.DAY, Wildlife.time(12f, 9))
        assertEquals(WildTime.DUSK, Wildlife.time(19f, 9))
        assertEquals(WildTime.NIGHT, Wildlife.time(22.5f, 9))
        // June's evening is long: 19:30 is still day, 21:30 dusk; December's is dark by 17:30
        assertEquals(WildTime.DAY, Wildlife.time(19.5f, 6))
        assertEquals(WildTime.DUSK, Wildlife.time(21.5f, 6))
        assertEquals(WildTime.NIGHT, Wildlife.time(18.5f, 12))
        // the small hours are the night before's
        assertEquals(Wildlife.day("2026-09-24", 23f, 9), Wildlife.day("2026-09-25", 2f, 9))
        assertEquals(Wildlife.plan(42, "2026-09-24", 23f, 9), Wildlife.plan(42, "2026-09-25", 2f, 9))
        assertEquals(Wildlife.day("2026-09-24", 12f, 9), Wildlife.day("2026-09-24", 20f, 9))
        assertEquals(20720L, Wildlife.epochDay("2026-09-24"))
        assertEquals(0L, Wildlife.epochDay("1970-01-01"))
        assertNull(Wildlife.epochDay("garbage"))
    }

    @Test fun `who is about depends on the time of day and the season`() {
        for (d in 0 until 400) for (w in WildTime.entries) for (month in 1..12) {
            val plan = Wildlife.plan(42, start + d, w, month).map { it.kind }
            val allowed = Wildlife.candidates(w) + Wild.TRACKS
            assertTrue("$w $month: $plan", plan.all { it in allowed })
            assertTrue("at most three kinds and the tracks: $plan", plan.count { it != Wild.TRACKS } <= 3)
            assertEquals("no kind twice: $plan", plan.size, plan.toSet().size)
            val winter = month == 12 || month <= 2
            // asleep in winter: the hedgehog, the bats, the bear; tracks only in the snow
            if (winter) assertTrue("$month: $plan", Wild.HEDGEHOG !in plan && Wild.BATS !in plan && Wild.BEAR !in plan)
            else assertTrue("$month: $plan", Wild.TRACKS !in plan)
        }
        // different to day: the night's animals never by day, the day's never at night
        assertEquals(setOf(Wild.SQUIRREL, Wild.HARE, Wild.BIRDS), Wildlife.candidates(WildTime.DAY).toSet())
        assertTrue(Wildlife.candidates(WildTime.NIGHT).containsAll(listOf(Wild.BOARS, Wild.BADGER, Wild.OWL, Wild.BEAR)))
        assertTrue(Wildlife.candidates(WildTime.NIGHT).none { it in Wildlife.candidates(WildTime.DAY) })
        assertTrue(Wild.DEER in Wildlife.candidates(WildTime.DAWN) && Wild.DEER in Wildlife.candidates(WildTime.DUSK))
        assertTrue(Wild.BATS in Wildlife.candidates(WildTime.DUSK))
    }

    @Test fun `a different mix every day, the same all day`() {
        val seed = 42L
        // the same day and window: the same animals, at every hour of the window and every call
        val a = Wildlife.plan(seed, "2026-09-24", 22f, 9)
        assertEquals(a, Wildlife.plan(seed, "2026-09-24", 22f, 9))
        assertEquals(a, Wildlife.plan(seed, "2026-09-24", 23.5f, 9))
        assertEquals(a, Wildlife.plan(seed, "2026-09-25", 4f, 9))
        // two days apart: another mix (or another place for them)
        val b = Wildlife.plan(seed, "2026-09-26", 22f, 9)
        assertNotEquals(a, b)
        // over a month of nights many different mixes, some with nothing out, the places changing
        val nights = (0 until 30).map { Wildlife.plan(seed, start + it, WildTime.NIGHT, 9) }
        assertTrue("${nights.map { n -> n.map { it.kind } }.toSet()}", nights.map { n -> n.map { it.kind }.toSet() }.toSet().size >= 6)
        assertTrue(nights.map { n -> n.map { it.pick } }.toSet().size >= 25)
        // another village has other animals on the same day
        assertNotEquals((0 until 30).map { Wildlife.plan(seed, start + it, WildTime.NIGHT, 9) }, (0 until 30).map { Wildlife.plan(7L, start + it, WildTime.NIGHT, 9) })
    }

    @Test fun `the rare ones are rare, the common ones common`() {
        val n = 3000
        fun share(w: WildTime, month: Int, kind: Wild) = (0 until n).count { d -> Wildlife.plan(42, start + d, w, month).any { it.kind == kind } } / n.toFloat()
        val bear = share(WildTime.NIGHT, 9, Wild.BEAR)
        assertTrue("the bear's eyes on $bear of the nights", bear > 0.005f && bear < 0.06f)
        assertTrue("deer at dawn", share(WildTime.DAWN, 9, Wild.DEER) > 0.55f)
        assertTrue("boars at night", share(WildTime.NIGHT, 9, Wild.BOARS) in 0.25f..0.5f)
        assertTrue("fewer deer in winter", share(WildTime.DAWN, 1, Wild.DEER) < share(WildTime.DAWN, 9, Wild.DEER))
        assertTrue("tracks in the snow", share(WildTime.DAY, 1, Wild.TRACKS) > 0.6f)
        val quiet = (0 until n).count { d -> Wildlife.plan(42, start + d, WildTime.DAY, 9).isEmpty() } / n.toFloat()
        assertTrue("some windows nothing shows: $quiet", quiet in 0.08f..0.3f)
    }

    @Test fun `every animal's word is in the Forest animals pack, with an emoji that shows it`() {
        val pack = si.lanisce.lani.data.json.parseToJsonElement(si.lanisce.lani.game.scene.CuratedContent.companion.resolve("packs/zivali-v-gozdu.json").readText()).jsonObject
        val words = pack.getValue("words").jsonArray.associate { w -> w.jsonObject.getValue("sl").jsonPrimitive.content to w.jsonObject["emoji"]?.jsonPrimitive?.content }
        val shows = mapOf(
            Wild.SQUIRREL to "🐿️", Wild.HARE to "🐇", Wild.BIRDS to "🐦", Wild.DEER to "🦌", Wild.FOX to "🦊", Wild.HEDGEHOG to "🦔",
            Wild.BATS to "🦇", Wild.BOARS to "🐗", Wild.BADGER to "🦡", Wild.OWL to "🦉", Wild.BEAR to "🐻", Wild.TRACKS to "🐾",
        )
        for (w in Wild.entries) assertEquals("${w.word} in the pack", shows[w], words[w.word])
    }

    // ---------------------------------------------------------------- where

    private fun render(s: GameState, frame: Frame, w: Int = fit.width, h: Int = fit.height): Pair<VillageRenderer, PixelCanvas> {
        val r = VillageRenderer()
        val c = PixelCanvas(w, h)
        r.render(c, s, frame)
        return r to c
    }

    @Test fun `they keep outside the palisade, but for the ones that fly or sit in the trees`() {
        var grounded = 0
        val kinds = HashSet<Wild>()
        for ((w, h) in listOf(fit.width to fit.height, 400 to 180)) for (age in listOf(Age.OGENJ, Age.ZASELEK, Age.VAS, Age.MESTO)) {
            val s = village(age)
            val fg = Foreground.of(age, w, h)
            val comp = VillageLayout.composition(w, h, false)
            for (d in 0 until 12) for ((hour, month) in listOf(12f to 6, 6.5f to 9, 19f to 9, 23f to 9, 12f to 1)) {
                val today = LocalDate.ofEpochDay(start + d * 3).toString()
                val (r, c) = render(s, Frame(time = 3.0, hour = hour, month = month, today = today), w, h)
                for (a in r.wildlife) {
                    kinds += a.kind
                    // in plain view: most of it shows, whatever stands about it
                    if (a.kind != Wild.BEAR && a.kind != Wild.TRACKS && a.kind != Wild.BIRDS && a.kind != Wild.BATS) {
                        var shows = 0
                        for (y in a.at.y.toInt() - 10..a.at.y.toInt() + 1) for (x in a.at.x.toInt() - 8..a.at.x.toInt() + 8) {
                            if (x in 0 until c.width && y in 0 until c.height && r.hitTest(x, y, 0) == VillageHit.OnAnimal(a.kind.word)) shows++
                        }
                        assertTrue("$age ${w}x$h $hour/$month $today: ${a.kind} at ${a.at} shows $shows px", shows >= SHOWS.getValue(a.kind))
                    }
                    if (a.kind.aloft) continue
                    grounded++
                    val dx = a.at.x - comp.fireX; val dy = a.at.y - comp.fireY
                    val m = VillageLayout.screenMetric(dx, dy)
                    val edge = Terrain.CLASSIC.forestEdge(age, VillageLayout.angle(dx, dy))
                    assertTrue("$age ${w}x$h $hour/$month $today: ${a.kind} at ${a.at} is inside (${m} < $edge)", m >= edge - 0.5f)
                    // off the stream and the pond
                    val wx = ((dx / 4f + dy / 2f) / 2f); val wy = ((dy / 2f - dx / 4f) / 2f)
                    assertTrue("${a.kind} in the stream", kotlin.math.abs(wx - Terrain.CLASSIC.streamX(wy)) > 1.2f)
                    if (fg != null) assertTrue("${a.kind} in the pond", fg.pondD(a.at.x, a.at.y) > 1.2f)
                    // on the canvas, below the far treeline
                    assertTrue("${a.kind} at ${a.at}", a.at.x in 0f..w.toFloat() && a.at.y in comp.horizon.toFloat()..h.toFloat())
                }
            }
        }
        assertTrue("$grounded on the ground", grounded > 100)
        assertTrue("$kinds", kinds.size >= 10)
    }

    /** A pixel of [kind] in the last frame of [r] (its canvas [c]), found by tapping round where it is. */
    private fun pixelOf(r: VillageRenderer, c: PixelCanvas, kind: Wild): IntArray? {
        val a = r.wildlife.firstOrNull { it.kind == kind } ?: return null
        for (dy in -12..3) for (dx in -9..9) {
            val x = a.at.x.toInt() + dx; val y = a.at.y.toInt() + dy
            if (x !in 0 until c.width || y !in 0 until c.height) continue
            if (r.hitTest(x, y, 0) == VillageHit.OnAnimal(kind.word)) return intArrayOf(x, y)
        }
        return null
    }

    @Test fun `a tap right on an animal looks up its word, the rest keep their taps`() {
        val s = village()
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        for ((hour, month, kind) in listOf(
            Triple(23f, 9, Wild.BOARS), Triple(23f, 9, Wild.OWL), Triple(12f, 6, Wild.HARE), Triple(12f, 6, Wild.SQUIRREL),
            Triple(6.8f, 9, Wild.DEER), Triple(19f, 9, Wild.FOX), Triple(19f, 9, Wild.BATS), Triple(12f, 6, Wild.BIRDS),
        )) {
            val today = dayWith(s.seed, hour, month, kind)
            val (r, c) = render(s, Frame(time = 3.0, hour = hour, month = month, today = today))
            val p = pixelOf(r, c, kind)
            assertTrue("$kind on $today at $hour is drawn and tappable: ${r.wildlife}", p != null)
            assertEquals(VillageHit.OnAnimal(kind.word), r.hitTest(p!![0], p[1], 8))
            // the fire keeps its taps, and nothing wild comes near it (the grass by it is where a double tap zooms)
            assertEquals(VillageHit.OnFire, r.hitTest(comp.fireX, comp.fireY - 3))
            for (dy in -30..30 step 2) for (dx in -60..60 step 2) {
                if (VillageLayout.screenMetric(dx.toFloat(), dy.toFloat()) > 60f) continue
                assertFalse("$kind by the fire at $dx,$dy", r.hitTest(comp.fireX + dx, comp.fireY + dy, 8) is VillageHit.OnAnimal)
            }
            // every building still gets its taps at the middle of its body
            for ((id, at) in TownAnchors.bodies(s, fit.width, fit.height)) {
                if (s.buildings.none { it.id == id && it.type.onPlot }) continue
                val hit = r.hitTest(at.x.toInt(), at.y.toInt(), 4)
                assertTrue("$id at $at: $hit", hit is VillageHit.OnBuilding)
            }
        }
    }

    @Test fun `an animal near something else only takes a tap right on it`() {
        val s = village()
        val today = dayWith(s.seed, 23f, 9, Wild.BOARS)
        val (r, c) = render(s, Frame(time = 3.0, hour = 23f, month = 9, today = today))
        val p = pixelOf(r, c, Wild.BOARS)!!
        assertEquals(VillageHit.OnAnimal("divji prašič"), r.hitTest(p[0], p[1], 8))
        // a tap a little off it, where nothing else is, still finds it; farther off it doesn't
        val a = r.wildlife.first { it.kind == Wild.BOARS }.at
        var near = 0; var far = 0
        for (dy in -16..8) for (dx in -20..20) {
            val x = a.x.toInt() + dx; val y = a.y.toInt() + dy
            val exact = r.hitTest(x, y, 0)
            val hit = r.hitTest(x, y, 8)
            if (exact == null && hit == VillageHit.OnAnimal("divji prašič")) near++
            // wherever something else is drawn right there, it keeps the tap
            if (exact != null && exact !is VillageHit.OnAnimal) assertFalse("$x,$y: $exact lost to $hit", hit is VillageHit.OnAnimal)
            if (dx * dx + dy * dy > 30 * 30 && hit is VillageHit.OnAnimal) far++
        }
        assertTrue("$near near misses", near > 0)
        assertEquals(0, far)
    }

    @Test fun `nobody is about when wolves are, in a storm or at a festival, nor on the Home header`() {
        val today = dayWith(42, 23f, 9, Wild.BOARS)
        for (e in listOf(EventKind.WOLVES, EventKind.BEAR, EventKind.STORM, EventKind.FESTIVAL)) {
            val (r, _) = render(village(event = e), Frame(time = 3.0, hour = 23f, month = 9, today = today))
            assertTrue("$e: ${r.wildlife}", r.wildlife.isEmpty())
        }
        val (r, _) = render(village(), Frame(time = 3.0, hour = 23f, month = 9, today = today, compact = true), 360, 264)
        assertTrue(r.wildlife.isEmpty())
        // the same night, every detail level shows the same animals at the same places
        val rig = CameraRig.town(fit, 1080, 2400)
        val (base, _) = render(village(), Frame(time = 3.0, hour = 23f, month = 9, today = today))
        val boar = base.wildlife.first { it.kind == Wild.BOARS }.at
        for (k in 2..3) {
            val lens = Lens.of(k, rig.lookAt(boar.x, boar.y, (rig.minScale * k).toFloat()), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val (fine, c) = render(village(), Frame(time = 3.0, hour = 23f, month = 9, today = today, lens = lens))
            assertEquals(base.wildlife, fine.wildlife)
            // and a tap on the boar through the lens finds her
            val x = lens.cx(boar.x).toInt(); val y = lens.cy(boar.y).toInt() - 2 * k
            val hit = (0..6 * k).asSequence().flatMap { dx -> sequenceOf(x + dx, x - dx) }.map { fine.hitTest(it, y, 0) }.firstOrNull { it is VillageHit.OnAnimal }
            assertEquals("k=$k", VillageHit.OnAnimal("divji prašič"), hit)
            assertTrue(c.width > 0)
        }
    }

    // ---------------------------------------------------------------- budget

    @Test fun `the animals stay within the frame budget`() {
        val s = village()
        // a busy night: boars, the owl and more; a busy dusk: bats over the roofs, a fox, deer
        val night = dayWith(s.seed, 22f, 9, Wild.BOARS, Wild.OWL)
        val dusk = dayWith(s.seed, 19f, 9, Wild.BATS, Wild.DEER)
        val r = VillageRenderer()
        val rig = CameraRig.town(fit, 1080, 2400)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val c = PixelCanvas(fit.width, fit.height)
        for ((today, hour) in listOf(night to 22f, dusk to 19f)) for (k in 1..3) {
            val lens = Lens.of(k, rig.lookAt(comp.fireX.toFloat(), comp.fireY.toFloat(), (rig.minScale * k).toFloat()), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val frame = Frame(time = 0.0, hour = hour, month = 9, today = today, lens = lens)
            repeat(15) { r.render(c, s, frame.copy(time = it / 12.0)) }
            assertTrue(r.wildlife.isNotEmpty())
            val n = 40
            val t0 = System.nanoTime()
            repeat(n) { r.render(c, s, frame.copy(time = 2 + it / 12.0)) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("VillageRenderer with wildlife: %.2f ms/frame (1080×2400 town, %s, k=%d): %s".format(ms, today, k, r.wildlife.map { it.kind }))
            assertTrue("k=$k too slow: $ms ms", ms < 30.0)
        }
        // what the animals cost: the same night with them and without (a quiet one), frame for frame in turn
        val quiet = (0 until 400).map { LocalDate.ofEpochDay(start + it).toString() }.first { Wildlife.plan(s.seed, it, 22f, 9).isEmpty() }
        val busy = Frame(time = 0.0, hour = 22f, month = 9, today = night); val calm = busy.copy(today = quiet)
        repeat(10) { r.render(c, s, busy.copy(time = it / 12.0)); r.render(c, s, calm.copy(time = it / 12.0)) }
        var with = 0L; var without = 0L
        repeat(40) {
            val a = System.nanoTime(); r.render(c, s, busy.copy(time = 2 + it / 12.0))
            val b = System.nanoTime(); r.render(c, s, calm.copy(time = 2 + it / 12.0))
            with += b - a; without += System.nanoTime() - b
        }
        println("VillageRenderer: %.2f ms/frame with the night's animals, %.2f ms without".format(with / 40e6, without / 40e6))
        assertTrue("the animals cost ${(with - without) / 40e6} ms a frame", (with - without) / 40e6 < 1.5)
        // a phone on its side
        val side = PixelCanvas(400, 180)
        val frame = Frame(time = 0.0, hour = 22f, month = 9, today = night)
        repeat(20) { r.render(side, s, frame.copy(time = it / 12.0)) }
        val t0 = System.nanoTime()
        repeat(60) { r.render(side, s, frame.copy(time = 2 + it / 12.0)) }
        val ms = (System.nanoTime() - t0) / 1e6 / 60
        println("VillageRenderer with wildlife: %.2f ms/frame (400x180, night)".format(ms))
        assertTrue("too slow: $ms ms", ms < 15.0)
        // where they can be is worked out once for a village, not every frame
        val t1 = System.nanoTime()
        val fresh = VillageRenderer()
        fresh.render(c, s, Frame(time = 0.0, hour = 22f, month = 9, today = night))
        val places = fresh.wildPlacesShown!!
        println(
            "Wildlife.places + a frame: %.1f ms (%d at the edge, %d in the meadow by day, %d at night, %d by bushes, %d trunks, %d tree tops, %d over the treeline)".format(
                (System.nanoTime() - t1) / 1e6, places.edge.size, places.meadow.size, places.meadowNight.size, places.bushes.size, places.perch.size,
                places.crowns.size, places.sky.size,
            ),
        )
        // room for a different place from day to day
        assertTrue(places.edge.size >= 8 && places.meadowNight.size >= 6 && places.meadow.size >= 3 && places.crowns.size >= 6 && places.sky.size >= 10)
    }

    // ---------------------------------------------------------------- snapshots

    @Test fun `render the animals by day, at dawn, at dusk, at night and in winter`() {
        val s = village()
        val rig = CameraRig.town(fit, 1080, 2400)
        val shots = listOf(
            Triple("60-wild-day", 12f to 6, arrayOf(Wild.SQUIRREL, Wild.HARE, Wild.BIRDS)),
            Triple("61-wild-dawn", 6.6f to 9, arrayOf(Wild.DEER, Wild.FOX)),
            Triple("62-wild-dusk", 19.2f to 9, arrayOf(Wild.BATS, Wild.HEDGEHOG)),
            Triple("63-wild-night", 23f to 9, arrayOf(Wild.BOARS, Wild.OWL)),
            Triple("64-wild-night-badger", 23f to 7, arrayOf(Wild.BADGER, Wild.FOX)),
            Triple("65-wild-winter", 12f to 1, arrayOf(Wild.TRACKS)),
            Triple("66-wild-bear", 23f to 9, arrayOf(Wild.BEAR)),
        )
        for ((name, at, kinds) in shots) {
            val (hour, month) = at
            val today = dayWith(s.seed, hour, month, *kinds)
            val frame = Frame(time = 3.0, hour = hour, month = month, today = today)
            val (r, c) = render(s, frame)
            write(c, File(dir, "$name-canvas.png"), 2)
            println("$name ($today): ${r.wildlife.map { "${it.kind} @ ${it.at.x.toInt()},${it.at.y.toInt()}" }}")
            // what the phone shows at the home view (the HUD and the bar darkened), at half the screen's size
            val area = si.lanisce.lani.ui.game.freeArea(1080f, 2400f, 300f, 230f, 32f)
            val home = rig.frame(TownAnchors.home(s, fit.width, fit.height), area, maxFrame = rig.minScale * 2, above = si.lanisce.lani.ui.game.HOME_ABOVE)
            val img = BufferedImage(540, 1200, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until 1200) for (x in 0 until 540) {
                val cx = home.toCanvasX(x * 2f).toInt(); val cy = home.toCanvasY(y * 2f).toInt()
                var px = if (cx in 0 until fit.width && cy in 0 until fit.height) c.pixels[cy * fit.width + cx] else r.skyColor
                if (y * 2 < 300 || y * 2 > 2400 - 230) px = Col.scale(px, 0.45f)
                img.setRGB(x, y, px)
            }
            ImageIO.write(img, "png", File(dir, "$name-home.png"))
            for (a in r.wildlife.distinctBy { it.kind }) {
                // the nominal picture blown up round each animal, and the same place at k = 3
                write(c, File(dir, "$name-${a.kind.name.lowercase()}-k1.png"), 6, centre = a.at)
                val lens = Lens.of(3, rig.lookAt(a.at.x, a.at.y - 6f, rig.minScale * 3f), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
                val (_, fine) = render(s, frame.copy(lens = lens))
                write(fine, File(dir, "$name-${a.kind.name.lowercase()}-k3.png"), 2, centre = CanvasPoint(lens.cx(a.at.x), lens.cy(a.at.y)), box = 180 to 120)
            }
        }
        // a phone on its side, at night and by day
        for ((name, at) in listOf("67-wild-side-night" to (23f to 9), "68-wild-side-day" to (12f to 6))) {
            val (hour, month) = at
            val today = dayWith(s.seed, hour, month, *(if (hour > 20f) arrayOf(Wild.BOARS) else arrayOf(Wild.SQUIRREL)))
            val (r, c) = render(s, Frame(time = 3.0, hour = hour, month = month, today = today), 400, 180)
            write(c, File(dir, "$name.png"), 4)
            println("$name ($today): ${r.wildlife.map { "${it.kind} @ ${it.at.x.toInt()},${it.at.y.toInt()}" }}")
        }
    }

    /** The canvas at [scale], or (with [centre]) a [box] window round that point of it. */
    private fun write(c: PixelCanvas, f: File, scale: Int, centre: CanvasPoint? = null, box: Pair<Int, Int> = 90 to 60) {
        val w = if (centre != null) box.first else c.width; val h = if (centre != null) box.second else c.height
        val x0 = if (centre != null) (centre.x.toInt() - w / 2).coerceIn(0, c.width - w) else 0
        val y0 = if (centre != null) (centre.y.toInt() - h / 2 - 6).coerceIn(0, c.height - h) else 0
        val img = BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) img.setRGB(x, y, c.pixels[(y0 + y / scale) * c.width + x0 + x / scale])
        ImageIO.write(img, "png", f)
    }
}
