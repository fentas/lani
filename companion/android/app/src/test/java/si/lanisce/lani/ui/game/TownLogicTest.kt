package si.lanisce.lani.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.LogEntry
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.scene.Happening
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownPlace
import java.time.LocalDateTime
import kotlin.math.abs

class TownLogicTest {
    // Fixture scenes: the contract's shape, no bridge needed.
    private val babica = ScenePerson("babica", "Babica Micka", "👵", "grandma", "left")
    private val otroci = ScenePerson("otroci", "Otroci", "🧒", "child1", "right")
    private val campfire = SceneSpec(
        id = "ob-ognju", title = "Ob ognju · At the campfire", emoji = "🔥", art = "campfire", from = listOf("fire"),
        people = listOf(babica, otroci),
        happenings = listOf(
            Happening("juha", "Babica kuha juho · Grandma is cooking soup", who = "babica", `when` = listOf(TimeOfDay.EVENING), marker = "🍲"),
            Happening("igra", "Otroci se igrajo · The children are playing", who = "otroci", `when` = listOf(TimeOfDay.AFTERNOON)),
        ),
    )
    private val kitchen = SceneSpec(
        id = "kuhinja", title = "V kuhinji · In the kitchen", emoji = "🍳", art = "kitchen", from = listOf("hut", "house"),
        people = listOf(babica.copy(slot = "stove")),
    )
    private val square = SceneSpec(id = "na-vasi", title = "Na vasi · On the square", art = "square", from = listOf("lipa"), needs = "vas")
    private val forest = SceneSpec(
        id = "v-gozdu", title = "V gozdu · In the forest", emoji = "🌲", art = "forest", from = listOf("forest"),
        people = listOf(ScenePerson("luka", "Pastir Luka", "🐑", "shepherd", "path", always = true)),
    )
    private val scenes = listOf(campfire, kitchen, square, forest)

    private val quest = Quest("q1", "Pastir Luka", "🐑", "Izgubljena ovca · The lost sheep", "…", Res.WOOD, mapOf(Res.WOOD to 30))
    private val tutor = Quest("t1", "Gostilničarka Vida", "🍷", "Naročila · Orders", "…", Res.WISDOM, emptyMap(), source = QuestSource.TUTOR, moduleId = "m")
    private val camp = GameState(
        seed = 7, age = Age.TABOR,
        buildings = listOf(Building("t", BuildingType.TENT, 0), Building("h", BuildingType.HUT, 1), Building("t2", BuildingType.TENT, 2)),
        quests = listOf(quest, tutor, quest.copy(id = "old", done = true)),
        event = GameEvent("e", EventKind.WOLVES, 2, 0, 1),
        log = listOf(LogEntry(1, "⛺", "Postavili smo šotor."), LogEntry(2, "🛖", "Zgradili smo kočo.")),
    )
    private val afternoon = LocalDateTime.of(2026, 9, 24, 14, 0)

    @Test fun `one bubble sits right over its place`() {
        assertEquals(listOf(BubbleOffset(0f, 0f)), fanOut(1))
        assertEquals(emptyList<BubbleOffset>(), fanOut(0))
    }

    @Test fun `several bubbles fan out in an arc, the middle higher`() {
        val three = fanOut(3, spacing = 50f, rise = 10f)
        assertEquals(listOf(-50f, 0f, 50f), three.map { it.x })
        assertEquals(0f, three[0].y, 0.001f); assertEquals(-10f, three[1].y, 0.001f); assertEquals(0f, three[2].y, 0.001f)
        val two = fanOut(2, spacing = 50f)
        assertEquals(listOf(-25f, 25f), two.map { it.x })
        // symmetric, no two bubbles on the same spot
        val six = fanOut(6, perRow = 4)
        assertEquals(6, six.toSet().size)
        assertTrue("a second row goes above", six.drop(4).all { it.y < -40f })
    }

    @Test fun `a fan at the screen edge shifts inwards`() {
        val left = fanOut(3, spacing = 50f, minX = -20f)
        assertEquals(-20f, left.minOf { it.x }, 0.001f)
        assertEquals(100f, left.maxOf { it.x } - left.minOf { it.x }, 0.001f)
        val right = fanOut(3, spacing = 50f, maxX = 10f)
        assertEquals(10f, right.maxOf { it.x }, 0.001f)
        val squeezed = fanOut(3, spacing = 50f, minX = -30f, maxX = 30f) // no room: centred on the room
        assertEquals(0f, (squeezed.minOf { it.x } + squeezed.maxOf { it.x }) / 2f, 0.001f)
    }

    /** Not covering each other: [size] is a bubble with 8 dp of air, and at most the air is shared. */
    private fun apart(a: BubbleOffset, b: BubbleOffset, size: Float) = abs(a.x - b.x) >= size - 8f || abs(a.y - b.y) >= size - 8f

    /** Their boxes ([size], the bubble and its air) don't overlap at all, corners included. */
    private fun clear(a: BubbleOffset, b: BubbleOffset, size: Float) = abs(a.x - b.x) >= size - 0.01f || abs(a.y - b.y) >= size - 0.01f

    @Test fun `places far apart keep their own fans`() {
        val laid = layoutBubbles(listOf(BubbleGroup(100f, 300f, 1), BubbleGroup(300f, 320f, 3)), size = 50f, spacing = 54f, lift = 40f)
        assertEquals(listOf(BubbleOffset(100f, 260f)), laid[0])
        assertEquals(fanOut(3, spacing = 54f).map { BubbleOffset(300f + it.x, 280f + it.y) }, laid[1])
    }

    @Test fun `bubbles of neighbouring places never cover each other`() {
        val size = 50f
        val cases = listOf(
            listOf(BubbleGroup(100f, 300f, 1), BubbleGroup(112f, 296f, 1)), // side by side, almost on top of each other
            listOf(BubbleGroup(100f, 300f, 1), BubbleGroup(100f, 280f, 1)), // one right above the other
            listOf(BubbleGroup(100f, 300f, 2), BubbleGroup(140f, 290f, 3), BubbleGroup(90f, 250f, 1)), // fans that cross
            listOf(BubbleGroup(60f, 300f, 4), BubbleGroup(80f, 310f, 5), BubbleGroup(300f, 300f, 2)), // big fans at the edge
        )
        for (groups in cases) {
            val laid = layoutBubbles(groups, size = size, spacing = 54f, lift = 40f, minX = 30f, maxX = 330f, minY = 60f)
            val all = laid.flatMapIndexed { g, l -> l.map { g to it } }
            for (i in all.indices) for (j in i + 1 until all.size) {
                if (all[i].first == all[j].first) continue
                assertTrue("$groups: ${all[i]} covers ${all[j]}", clear(all[i].second, all[j].second, size))
            }
            for ((_, b) in all) assertTrue("$b off screen", b.x in 30f..330f && b.y >= 60f)
        }
    }

    @Test fun `bubbles stay below the HUD, and fade as their place scrolls far past the top`() {
        val near = layoutBubbles(listOf(BubbleGroup(100f, 80f, 1)), size = 50f, spacing = 54f, lift = 40f, minY = 120f)
        assertEquals(BubbleOffset(100f, 120f), near[0][0]) // would be at 40, under the HUD
        val far = layoutBubbles(listOf(BubbleGroup(100f, -100f, 1)), size = 50f, spacing = 54f, lift = 40f, minY = 120f)
        assertEquals(BubbleOffset(100f, 120f), far[0][0]) // still there, pointing up at it
        assertEquals(1f, bubblePresence(80f, minY = 120f, size = 50f), 0.001f) // just under the HUD: shown
        assertEquals(1f, bubblePresence(-80f, minY = 120f, size = 50f), 0.001f) // a bubble's height or two past it: still shown
        assertEquals(0.5f, bubblePresence(-130f, minY = 120f, size = 50f), 0.001f)
        assertEquals(0f, bubblePresence(-180f, minY = 120f, size = 50f), 0.001f) // far off: gone
    }

    @Test fun `pushed up under the HUD, a bubble goes aside instead of back down onto the one below`() {
        // two places one behind the other: the back one's bubble goes up over the front one's, which the HUD doesn't allow
        val laid = layoutBubbles(listOf(BubbleGroup(200f, 300f, 1), BubbleGroup(200f, 290f, 1)), size = 50f, spacing = 54f, lift = 40f, minX = 30f, maxX = 380f, minY = 230f)
        assertEquals(BubbleOffset(200f, 260f), laid[0].single())
        val b = laid[1].single()
        assertEquals("just under the HUD, beside it: $b", 230f, b.y, 0.01f)
        assertEquals("right beside it: $b", 50f, abs(b.x - 200f), 0.01f)
        // just touching it there (sharing no more than the air): it stays over it
        val touch = layoutBubbles(listOf(BubbleGroup(200f, 300f, 1), BubbleGroup(200f, 290f, 1)), size = 50f, spacing = 54f, lift = 40f, minX = 30f, maxX = 380f, minY = 212f)
        assertEquals(BubbleOffset(200f, 212f), touch[1].single())
        // a row too narrow for both side by side: they share a little of their air (the bubbles themselves don't touch)
        val narrow = layoutBubbles(listOf(BubbleGroup(78f, 300f, 1), BubbleGroup(78f, 290f, 1)), size = 50f, spacing = 54f, lift = 40f, minX = 30f, maxX = 124f, minY = 230f)
        val n = narrow[1].single()
        assertTrue("$n beside ${narrow[0]}", n.y == 230f && n.x in 30f..124f && abs(n.x - 78f) >= 50f * 0.85f - 0.01f && abs(n.x - 78f) < 50f)
    }

    @Test fun `the lowest place keeps its fan, the one behind moves up or aside`() {
        val over = layoutBubbles(listOf(BubbleGroup(100f, 300f, 1), BubbleGroup(101f, 285f, 1)), size = 50f, spacing = 54f, lift = 40f)
        assertEquals(BubbleOffset(100f, 260f), over[0][0])
        assertTrue("above: ${over[1]}", over[1][0].y <= 216f && abs(over[1][0].x - 101f) < 10f)
        val beside = layoutBubbles(listOf(BubbleGroup(100f, 300f, 1), BubbleGroup(135f, 299f, 1)), size = 50f, spacing = 54f, lift = 40f)
        assertEquals(BubbleOffset(100f, 260f), beside[0][0])
        assertTrue("to the right: ${beside[1]}", beside[1][0].x >= 144f && abs(beside[1][0].y - 259f) < 10f)
    }

    /** A jump shows as a big move for a tiny step of the anchors; a fast slide scales down with the step. */
    @Test fun `bubbles slide, never jump, as the camera moves`() {
        var prev: List<BubbleOffset>? = null
        for (k in 0..600) {
            // the second place drifts from far left, across the first, to far right, rising a little
            val x = 20f + k * 0.5f
            val laid = layoutBubbles(listOf(BubbleGroup(150f, 300f, 2), BubbleGroup(x, 290f - k * 0.07f, 1)), size = 50f, spacing = 54f, lift = 40f, minX = 30f, maxX = 330f).flatten()
            prev?.let { p -> for (i in laid.indices) assertTrue("step $k: ${p[i]} -> ${laid[i]}", abs(laid[i].x - p[i].x) + abs(laid[i].y - p[i].y) < 4f) }
            prev = laid
        }
        // zooming in on three places in a row (a chain of pushes): their distances grow together, the bubbles part smoothly
        prev = null
        for (k in 0..300) {
            val z = 1f + k * 0.01f
            val laid = layoutBubbles(listOf(BubbleGroup(150f, 300f, 1), BubbleGroup(150f + 24f * z, 300f - 9f * z, 2), BubbleGroup(150f - 20f * z, 300f - 30f * z, 1)), size = 50f, spacing = 54f, lift = 40f).flatten()
            prev?.let { p -> for (i in laid.indices) assertTrue("zoom $k: ${p[i]} -> ${laid[i]}", abs(laid[i].x - p[i].x) + abs(laid[i].y - p[i].y) < 5f) }
            prev = laid
        }
    }

    // --- bubbles off the fire ---------------------------------------------------------------------------------------

    private val fireState = GameState(age = Age.VAS, fire = 100)
    private val fireFit = si.lanisce.lani.game.render.SceneFit.town(1080, 2400)
    private val fireArea = si.lanisce.lani.game.render.TownAnchors.fireArea(fireState, fireFit.width, fireFit.height)
    private val fireBody = si.lanisce.lani.game.render.TownAnchors.fireBody(fireState, fireFit.width, fireFit.height)
    private val fireComp = si.lanisce.lani.game.render.VillageLayout.composition(fireFit.width, fireFit.height, compact = false)
    private val fireX = fireComp.fireX.toFloat()
    private val fireY = fireComp.fireY.toFloat()
    private val fireReach = si.lanisce.lani.game.render.TownAnchors.fireReach(si.lanisce.lani.game.render.TownAnchors.FIRE_SCALE[Age.VAS.ordinal])
    private val phone = 2.625f // dp: a 1080 × 2400 phone

    /** The camera at [zoom] screen px a nominal one, the fire in the middle of the screen. */
    private fun fireCam(zoom: Float) = si.lanisce.lani.game.render.Camera(zoom, 540f - fireX * zoom, 1200f - fireY * zoom)

    /** The bubbles over [heads] (nominal px, above someone's head: one bubble each, two for a pair), as TownBubbles lays them out. */
    private fun laidAt(zoom: Float, heads: List<Pair<Float, Float>>, n: (Int) -> Int = { 1 }, keep: Boolean = true, minY: Float = 60f): List<List<BubbleOffset>> {
        val cam = fireCam(zoom)
        val groups = heads.mapIndexed { i, (x, y) -> BubbleGroup(cam.toScreenX(x) / phone, cam.toScreenY(y) / phone, n(i)) }
        val keepOff = if (keep) fireKeepOff(fireArea, fireBody, cam, phone) else null
        return layoutBubbles(groups, size = 52f, spacing = 54f, lift = 40f, minX = 30f, maxX = 1080f / phone - 30f, minY = minY, keepOff = keepOff)
    }

    @Test fun `the fire's tap area has its flames, stones and target`() {
        val cam = fireCam(3f)
        val box = fireKeepOff(fireArea, fireBody, cam, phone)
        // its stones to either side, its flames and smoke above, its front stones below (nominal px through the camera)
        assertTrue(box.left <= cam.toScreenX(fireArea[0]) / phone && box.right >= cam.toScreenX(fireArea[2]) / phone)
        assertTrue(box.top <= cam.toScreenY(fireArea[1]) / phone && box.bottom >= cam.toScreenY(fireArea[3]) / phone)
        // and the screen reader's target (48 dp) round its middle, bigger than the fire when it's this small
        val bx = cam.toScreenX(fireBody.x) / phone; val by = cam.toScreenY(fireBody.y) / phone
        assertEquals(48f, box.right - box.left, 0.01f)
        assertTrue(box.left <= bx - 24f && box.right >= bx + 24f && box.top <= by - 24f && box.bottom >= by + 24f)
        assertTrue(fireArea[1] < fireBody.y && fireBody.y < fireArea[3])
    }

    @Test fun `a bubble next to the fire moves off its tap area, its pointer still at its person`() {
        // France at the field's edge right of the fire, Luka warming his hands on its left (the tops of their heads)
        val beside = listOf(fireX + fireReach + 6f to fireY - 12f, fireX - fireReach - 6f to fireY - 11f)
        var covered = 0
        for (zoom in listOf(3f, 4.5f, 6f, 9f, 12f)) {
            val box = fireKeepOff(fireArea, fireBody, fireCam(zoom), phone)
            covered += laidAt(zoom, beside, keep = false).flatten().count { box.meets(it.x, it.y, 52f) }
            val laid = laidAt(zoom, beside)
            for ((i, head) in beside.withIndex()) {
                val b = laid[i].single()
                val ax = fireCam(zoom).toScreenX(head.first) / phone; val ay = fireCam(zoom).toScreenY(head.second) / phone
                // off the fire as it bobs (±3 dp), with a little air
                assertTrue("zoom $zoom, ${listOf("France", "Luka")[i]}: $b on the fire $box", !box.meets(b.x, b.y, 52f, air = 3f))
                // still over them: above their head, its pointer slanting at most a bubble and a half to the side
                assertTrue("zoom $zoom: $b over $ax, $ay", b.y < ay - 26f && abs(b.x - ax) <= 78f)
                // away from the fire: France's to the right of where it was, Luka's to the left
                if (i == 0) assertTrue(b.x >= ax - 0.01f) else assertTrue(b.x <= ax + 0.01f)
            }
            assertTrue("zoom $zoom: ${laid[0]} on ${laid[1]}", apart(laid[0].single(), laid[1].single(), 52f))
        }
        assertTrue("left alone, their bubbles would be on the fire ($covered)", covered >= 2)
    }

    @Test fun `bubbles kept off the fire don't land on each other`() {
        // France (a request and his happening) and Luka beside it, Tine just behind it, a bubble over the fire itself
        val heads = listOf(fireX + fireReach + 6f to fireY - 12f, fireX - fireReach - 6f to fireY - 11f, fireX + 3f to fireY - 20f, fireX to fireY - 23f)
        for (zoom in listOf(3f, 4.5f, 6f, 9f, 12f)) {
            val box = fireKeepOff(fireArea, fireBody, fireCam(zoom), phone)
            val laid = laidAt(zoom, heads, n = { if (it == 0) 2 else 1 })
            val all = laid.flatMapIndexed { g, l -> l.map { g to it } }
            for ((g, b) in all) assertTrue("zoom $zoom, group $g: $b on the fire $box", !box.meets(b.x, b.y, 52f, air = 3f))
            for (i in all.indices) for (j in i + 1 until all.size) {
                if (all[i].first == all[j].first) continue
                assertTrue("zoom $zoom: ${all[i]} covers ${all[j]}", clear(all[i].second, all[j].second, 52f))
            }
        }
    }

    /** A crowd round the fire: France (a request and his happening), Luka, Tine behind it, the fire's own requests, Babica and Tone. */
    private val crowd = listOf(
        fireX + fireReach + 6f to fireY - 12f, fireX - fireReach - 6f to fireY - 11f, fireX + 3f to fireY - 20f,
        fireX to fireY - 23f, fireX - fireReach - 14f to fireY - 16f, fireX + fireReach + 16f to fireY - 18f,
    )
    private val crowdN = { g: Int -> when (g) { 0 -> 2; 3 -> 3; else -> 1 } }

    @Test fun `a crowd round the fire with the HUD near, none under it, on the fire or on each other`() {
        val fireMid = 1200f / phone // the fire's middle on screen (dp)
        var reached = 0
        // the HUD's bottom from far above the fire down to just over the crowd's heads
        for (zoom in listOf(3f, 4.5f, 6f, 9f, 12f)) for (hud in 380 downTo 100 step 8) {
            val cam = fireCam(zoom)
            val minY = fireMid - hud
            // room for the crowd's nine bubbles between the HUD and their own row: two rows of them
            val room = minY <= crowd.minOf { cam.toScreenY(it.second) } / phone - 40f - 2 * 52f
            val box = fireKeepOff(fireArea, fireBody, cam, phone)
            val touched = laidAt(zoom, crowd, crowdN, minY = Float.NEGATIVE_INFINITY).flatten().any { it.y < minY }
            if (touched) reached++
            val laid = laidAt(zoom, crowd, crowdN, minY = minY)
            val all = laid.flatMapIndexed { g, l -> l.map { g to it } }
            val at = "zoom $zoom, HUD $hud dp over the fire"
            for ((g, b) in all) {
                assertTrue("$at, group $g: $b under the HUD ($minY)", b.y >= minY - 0.01f)
                assertTrue("$at, group $g: $b on the fire $box", !box.meets(b.x, b.y, 52f, air = 3f))
                assertTrue("$at, group $g: $b off screen", b.x in 30f - 0.01f..1080f / phone - 30f + 0.01f)
                // with room, still over their person or place: its pointer goes down to them
                val ay = cam.toScreenY(crowd[g].second) / phone
                if (room) assertTrue("$at, group $g: $b over $ay", b.y <= ay - 26f)
            }
            for (i in all.indices) for (j in i + 1 until all.size) {
                // with the stack clear of the HUD not even their air; pushed down under it at most their air (the bubbles
                // themselves don't touch)
                assertTrue("$at: ${all[i]} covers ${all[j]}", if (!touched) clear(all[i].second, all[j].second, 52f) else apart(all[i].second, all[j].second, 52f))
            }
        }
        assertTrue("the crowd's stack reaches the HUD ($reached)", reached >= 10)
    }

    @Test fun `the fire's card open, the fire just under the HUD, the village's bubbles keep off it and each other`() {
        // QA's village with Luka warming his hands (--happening ob-ognju/roke), its fire's card open: the places' bubbles
        // (dp on a 1080 × 2400 phone) round the fire just under the HUD, no room in the row under it for the last one
        val groups = listOf(
            289.52f to 427.43f, 156.95f to 346.67f, 142.93f to 417.98f, 180.57f to 391.01f, 205.71f to 368.76f,
            120.38f to 526.48f, 179.81f to 501.33f, 292.57f to 679.01f, 131.05f to 360.38f,
        ).map { (x, y) -> BubbleGroup(x, y, 1) }
        val fire = BubbleBox(181.71f, 359.62f, 229.71f, 407.92f)
        val minY = 344.29f
        val laid = layoutBubbles(groups, size = 52f, spacing = 54f, lift = 40f, minX = 30f, maxX = 381.43f, minY = minY, keepOff = fire).flatten()
        for (b in laid) assertTrue("$b under the HUD or on the fire", b.y >= minY - 0.01f && !fire.meets(b.x, b.y, 52f, air = 3f) && b.x in 30f..381.43f)
        for (i in laid.indices) for (j in i + 1 until laid.size) assertTrue("${laid[i]} covers ${laid[j]}", apart(laid[i], laid[j], 52f))
    }

    @Test fun `with the HUD far off, the crowd round the fire lays out as before`() {
        for (zoom in listOf(3f, 6f, 12f)) assertEquals(laidAt(zoom, crowd, crowdN, minY = Float.NEGATIVE_INFINITY), laidAt(zoom, crowd, crowdN, minY = 0f))
    }

    @Test fun `a bubble clear of the fire stays where it was, and one passing it slides round it`() {
        // far off: just as without the fire
        val far = listOf(fireX + 60f to fireY - 30f)
        assertEquals(laidAt(6f, far, keep = false), laidAt(6f, far))
        // someone walking past in front of the fire with a bubble over them: it slides over the fire, never jumps
        for (zoom in listOf(3f, 6f, 12f)) {
            var prev: BubbleOffset? = null
            val box = fireKeepOff(fireArea, fireBody, fireCam(zoom), phone)
            for (k in 0..400) {
                val b = laidAt(zoom, listOf(fireX - 40f + k * 0.2f to fireY + 6f)).single().single()
                assertTrue("zoom $zoom, step $k: $b on the fire", !box.meets(b.x, b.y, 52f, air = 3f))
                prev?.let { p -> assertTrue("zoom $zoom, step $k: $p -> $b", abs(b.x - p.x) + abs(b.y - p.y) < 6f) }
                prev = b
            }
        }
    }

    // --- bubbles holding their spots, and gliding ------------------------------------------------------------------

    /**
     * The crowd round the fire as the camera zooms in from 3 to 12 screen px a nominal one (a hundredth at a time), with the
     * HUD far off and from 380 down to 100 dp over the fire, each frame given where the bubbles were a frame ago against their
     * places ([hold]: layoutBubbles' was) or not: the jumps of a bubble from one frame to the next over 40 dp, the flips
     * among them back to where it was within the last 20 frames, and the overlaps of two boxes where the HUD is clear of the
     * stack by a bubble. Every frame keeps the rules: none under the HUD, on the fire or off screen, none sharing more than
     * their air.
     */
    private fun zoomSweep(hold: Boolean): IntArray {
        var jumps = 0; var flips = 0; var overlaps = 0
        val fireMid = 1200f / phone
        /** The top of the stack with the HUD far off, at each zoom. */
        val top = FloatArray(901)
        for (hud in listOf(null) + (380 downTo 100 step 8)) {
            val minY = if (hud == null) Float.NEGATIVE_INFINITY else fireMid - hud
            var prev: List<List<BubbleOffset>>? = null
            var prevAt: List<BubbleGroup>? = null
            val seen = ArrayList<List<BubbleOffset>>()
            for (k in 0..900) {
                val zoom = 3f + k * 0.01f
                val cam = fireCam(zoom)
                val groups = crowd.mapIndexed { i, (x, y) -> BubbleGroup(cam.toScreenX(x) / phone, cam.toScreenY(y) / phone, crowdN(i)) }
                val box = fireKeepOff(fireArea, fireBody, cam, phone)
                val p = prev; val pa = prevAt
                val was = if (hold && p != null && pa != null) { g: Int, i: Int -> p[g].getOrNull(i)?.let { BubbleOffset(it.x - pa[g].x, it.y - pa[g].y) } } else null
                val laid = layoutBubbles(groups, size = 52f, spacing = 54f, lift = 40f, minX = 30f, maxX = 1080f / phone - 30f, minY = minY, keepOff = box, was = was)
                val all = laid.flatten()
                val group = laid.flatMapIndexed { g, l -> l.map { g } }
                // with the HUD far off the stack's top is a bubble below where it is now: nothing pushed down under it
                if (hud == null) top[k] = all.minOf { it.y }
                val free = top[k] >= minY + 52f
                val at = "hold $hold, HUD $hud, zoom $zoom"
                for (b in all) assertTrue("$at: $b under the HUD, on the fire or off screen", b.y >= minY - 0.01f && !box.meets(b.x, b.y, 52f, air = 3f) && b.x in 29.99f..1080f / phone - 29.99f)
                for (i in all.indices) for (j in i + 1 until all.size) {
                    if (group[i] == group[j]) continue
                    assertTrue("$at: ${all[i]} covers ${all[j]}", apart(all[i], all[j], 52f))
                    if (free && !clear(all[i], all[j], 52f)) overlaps++
                }
                seen.lastOrNull()?.let { last ->
                    for (i in all.indices) if (kotlin.math.hypot(all[i].x - last[i].x, all[i].y - last[i].y) > 40f) {
                        jumps++
                        if (seen.takeLast(20).dropLast(1).any { kotlin.math.hypot(it[i].x - all[i].x, it[i].y - all[i].y) < 20f }) flips++
                    }
                }
                seen += all
                prev = laid; prevAt = groups
            }
        }
        return intArrayOf(jumps, flips, overlaps)
    }

    @Test fun `zooming on the crowd round the fire, the bubbles hold their spots instead of flipping between two`() {
        val free = zoomSweep(hold = false)
        val held = zoomSweep(hold = true)
        // clear of the HUD, no two boxes overlap, corners included (the corner the pushes left at zoom 9.3 to 9.5 too)
        assertEquals("overlaps", 0, free[2])
        assertEquals("overlaps", 0, held[2])
        // held from one frame to the next: far fewer jumps, hardly a flip back (395 and 11; free 713 and 127; the layout
        // before the boxes were kept fully apart: 564 and 56). The jumps left are steps: the stack meeting the HUD, a squeeze
        assertTrue("held ${held.toList()}, free ${free.toList()}", held[0] * 10 <= free[0] * 6 && held[1] * 8 <= free[1] && held[1] <= 15)
    }

    @Test fun `squeezed in between two, a bubble goes up, and comes back down only with room to spare`() {
        // Luka and France side by side, Tine's bubble between them: the gap opens and closes by a hair, back and forth
        fun lay(gap: Float, was: ((Int, Int) -> BubbleOffset?)?) = layoutBubbles(
            listOf(BubbleGroup(200f - gap / 2f, 400f, 1), BubbleGroup(200f + gap / 2f, 400f, 1), BubbleGroup(200f, 390f, 1)),
            size = 52f, spacing = 54f, lift = 40f, minX = 30f, maxX = 380f, was = was,
        )
        val tight = lay(103f, null)[2].single()
        assertTrue("up off them: $tight", tight.y <= 360f - 52f + 0.01f)
        assertEquals("room for it: down between them", 350f, lay(105f, null)[2].single().y, 0.01f)
        var last = lay(103f, null)
        var steps = 0
        // up while there's no room to spare on either side (4 dp), down once there is, then down while it fits
        for (gap in listOf(103f, 104.5f, 103.5f, 105f, 103f, 106f, 109f, 111f, 110f, 113f, 112.5f, 114f, 104.5f, 106f)) {
            val p = last
            val at = listOf(BubbleGroup(200f - gap / 2f, 400f, 1), BubbleGroup(200f + gap / 2f, 400f, 1), BubbleGroup(200f, 390f, 1))
            val now = lay(gap, { g, i -> p[g].getOrNull(i)?.let { BubbleOffset(it.x - at[g].x, it.y - at[g].y) } })
            if (abs(now[2].single().y - p[2].single().y) > 40f) steps++
            for (a in now.flatten()) for (b in now.flatten()) if (a !== b) assertTrue("gap $gap: $a on $b", clear(a, b, 52f))
            last = now
        }
        assertEquals("down once, with room to spare", 1, steps)
        assertEquals(350f, last[2].single().y, 0.01f)
    }

    /** 60 frames a second, [n] of them: the glide's frame times (ns). */
    private fun frames(n: Int, from: Int = 0) = (from until from + n).map { it * 16_666_667L }

    @Test fun `a bubble glides to its new spot, its pointer still at its place`() {
        val glide = BubbleGlide()
        val place = BubbleOffset(200f, 400f)
        val spot = BubbleOffset(200f, 360f)
        assertEquals(listOf(spot), glide.step(0L, listOf("a"), listOf(spot), listOf(place)))
        // the layout steps it aside, a bubble and then some: it goes there smoothly, not overshooting, in about 0.2 s
        val aside = BubbleOffset(306f, 360f)
        var x = 200f
        for ((n, t) in frames(30, from = 1).withIndex()) {
            val b = glide.step(t, listOf("a"), listOf(aside), listOf(place)).single()
            assertTrue("frame $n: $x -> ${b.x}", b.x >= x - 0.001f && b.x - x <= 20f && b.x <= aside.x + 0.001f)
            assertEquals(360f, b.y, 0.001f)
            if (n == 15) assertTrue("there in a quarter of a second: $b", aside.x - b.x < 2f)
            x = b.x
        }
        assertEquals(aside.x, x, 0.5f)
        assertEquals("what the layout holds it to", BubbleOffset(106f, -40f), glide.offset("a"))
        // stepped again on its way there: it turns, no jump
        glide.step(frames(1, from = 40).single(), listOf("a"), listOf(spot), listOf(place))
        val mid = glide.step(frames(1, from = 43).single(), listOf("a"), listOf(spot), listOf(place)).single()
        val turned = glide.step(frames(1, from = 44).single(), listOf("a"), listOf(aside), listOf(place)).single()
        assertTrue("$mid -> $turned", abs(turned.x - mid.x) < 20f)
    }

    @Test fun `a gliding bubble moves along with the camera and keeps below the HUD`() {
        val glide = BubbleGlide()
        // panning: the place and its spot move together, the bubble with them at once, no lag
        for ((n, t) in frames(20).withIndex()) {
            val place = BubbleOffset(200f, 400f - n * 25f)
            val spot = BubbleOffset(200f, 360f - n * 25f)
            assertEquals(spot, glide.step(t, listOf("a"), listOf(spot), listOf(place), minY = -1000f).single())
        }
        // under the HUD its spot stays put while its place goes on up: the bubble stays put too, not up under the HUD
        val glide2 = BubbleGlide()
        for ((n, t) in frames(20).withIndex()) {
            val place = BubbleOffset(200f, 180f - n * 25f)
            val spot = BubbleOffset(200f, 150f)
            val b = glide2.step(t, listOf("a"), listOf(spot), listOf(place), minY = 150f).single()
            assertEquals("frame $n", spot, b)
        }
        // stepping aside under the HUD, it glides along just under it
        val b = glide2.step(frames(1, from = 20).single(), listOf("a"), listOf(BubbleOffset(300f, 150f)), listOf(BubbleOffset(200f, -320f)), minY = 150f).single()
        assertTrue("$b", b.y >= 150f && b.x < 300f)
    }

    @Test fun `a new bubble starts at its spot, one gone is forgotten`() {
        val glide = BubbleGlide()
        glide.step(0L, listOf("a"), listOf(BubbleOffset(100f, 100f)), listOf(BubbleOffset(100f, 140f)))
        val two = glide.step(16_666_667L, listOf("a", "b"), listOf(BubbleOffset(100f, 100f), BubbleOffset(300f, 80f)), listOf(BubbleOffset(100f, 140f), BubbleOffset(300f, 120f)))
        assertEquals(BubbleOffset(300f, 80f), two[1])
        glide.step(33_333_333L, listOf("b"), listOf(BubbleOffset(300f, 80f)), listOf(BubbleOffset(300f, 120f)))
        assertEquals(null, glide.offset("a"))
        assertEquals(BubbleOffset(0f, -40f), glide.offset("b"))
    }

    @Test fun `markers group by place, the event first`() {
        val m = listOf(
            TownMarker("event:e", TownMarker.Kind.EVENT, "🐺", "Volkovi", TownPlace.Forest),
            TownMarker("quest:q1", TownMarker.Kind.QUEST, "🐑", "Ovca", TownPlace.Forest),
            TownMarker("quest:t1", TownMarker.Kind.QUEST, "🍷", "Naročila", TownPlace.Fire),
        )
        val g = markersByPlace(m)
        assertEquals(listOf("event:e", "quest:q1"), g.getValue(TownPlace.Forest).map { it.id })
        assertEquals(listOf("quest:t1"), g.getValue(TownPlace.Fire).map { it.id })
    }

    @Test fun `a crowded place shows the people there first and folds the rest`() {
        val fire = TownPlace.Fire
        val quests = (1..6).map { TownMarker("quest:q$it", TownMarker.Kind.QUEST, "⚒️", "Q$it", fire) }
        val soup = TownMarker("happening:ob-ognju/juha", TownMarker.Kind.HAPPENING, "👵", "Juha", fire)
        val sheep = TownMarker("quest:ovca", TownMarker.Kind.QUEST, "🐑", "Ovca", TownPlace.Forest)
        val shown = foldBubbles(quests + soup + sheep)
        val atFire = shown.filter { it.place == fire }
        assertEquals(MAX_BUBBLES, atFire.size)
        assertEquals("happening:ob-ognju/juha", atFire.first().id)
        assertEquals("more:fire", atFire.last().id)
        assertEquals("+3", atFire.last().emoji) // 7 at the fire: 4 shown, the other 3 behind "+3"
        assertEquals(listOf("quest:ovca"), shown.filter { it.place == TownPlace.Forest }.map { it.id })
        assertEquals(quests.take(3), foldBubbles(quests.take(3))) // few: all of them, as they came
    }

    @Test fun `zoomed out, each place shows one bubble`() {
        val wolves = TownMarker("event:e", TownMarker.Kind.EVENT, "🐺", "Volkovi", TownPlace.Forest)
        val sheep = TownMarker("quest:ovca", TownMarker.Kind.QUEST, "🐑", "Ovca", TownPlace.Forest)
        val soup = TownMarker("happening:ob-ognju/juha", TownMarker.Kind.HAPPENING, "👵", "Juha", TownPlace.Fire)
        val all = listOf(sheep, wolves, soup)
        val c = clusterBubbles(all)
        assertEquals(listOf("cluster:forest", "happening:ob-ognju/juha"), c.map { it.id })
        assertEquals(TownMarker.Kind.EVENT, c[0].kind) // the wolves' look wins: red, urgent
        assertEquals("🐺", c[0].emoji)
        assertTrue(c[0].label, c[0].label.contains("Volkovi") && c[0].label.contains("Ovca"))
        assertEquals(2, clusterSize(c[0], all))
        assertEquals(null, clusterSize(c[1], all))
    }

    @Test fun `today lists the event, what's on now and later, and the open requests`() {
        val o = TownOverview.of(camp, scenes, afternoon)
        assertEquals(TimeOfDay.AFTERNOON, o.time)
        val kinds = o.today.map {
            when (it) {
                is TodayItem.Event -> "event:${it.place}"
                is TodayItem.Happening -> "${if (it.now) "now" else "later"}:${it.on.key}@${it.time}"
                is TodayItem.Task -> "quest:${it.quest.id}@${it.place}"
                is TodayItem.Arrival -> "arrival:${it.id}@${it.place}"
                is TodayItem.Keeper -> "keeper:${it.keeper.id}@${it.place}"
            }
        }
        assertEquals(
            listOf("event:Forest", "now:ob-ognju/igra@AFTERNOON", "later:ob-ognju/juha@EVENING", "quest:q1@Forest", "quest:t1@Fire"),
            kinds,
        )
        assertEquals(4, o.todayCount) // later happenings don't count as "going on"
        // late at night nothing is left for later
        assertTrue(TownOverview.laterToday(scenes, camp, afternoon.withHour(23)).isEmpty())
    }

    @Test fun `places list the village's own places with the scenes that open there`() {
        val o = TownOverview.of(camp, scenes, afternoon)
        assertEquals(listOf(TownPlace.Fire, TownPlace.Forest, TownPlace.At(BuildingType.TENT), TownPlace.At(BuildingType.HUT)), o.places.map { it.place })
        assertEquals(listOf("ob-ognju"), o.places[0].scenes.map { it.id })
        assertEquals(listOf("v-gozdu"), o.places[1].scenes.map { it.id })
        assertEquals(2, o.places[2].count)
        assertEquals(listOf("kuhinja"), o.places[3].scenes.map { it.id })
        assertEquals(listOf("event:e", "quest:q1"), o.places[1].here.map { it.id })
        // the square needs the linden tree and the Vas age
        val locked = o.locked.single()
        assertEquals("na-vasi", locked.scene.id)
        assertEquals(2, locked.needs.size)
        assertTrue(locked.needs[0], locked.needs[0].contains("Vas"))
        assertTrue(locked.needs[1], locked.needs[1].contains("Lipa"))
    }

    @Test fun `residents are the requesters and the people of open scenes, once each`() {
        val o = TownOverview.of(camp, scenes, afternoon)
        assertEquals(listOf("Pastir Luka", "Gostilničarka Vida", "Otroci", "Babica Micka"), o.residents.map { it.name })
        val luka = o.residents[0]
        assertTrue(luka.doing[0] is Doing.Asks)
        assertTrue("and he's in the forest scene", luka.doing.any { it is Doing.Lives && it.scene.id == "v-gozdu" })
        val otroci = o.residents[2]
        assertEquals(1, otroci.doing.size) // busy playing, not listed twice for the same scene
        assertTrue(otroci.doing[0] is Doing.Busy)
        val babica = o.residents[3]
        assertEquals(setOf("ob-ognju", "kuhinja"), babica.doing.map { (it as Doing.Lives).scene.id }.toSet())
    }

    @Test fun `no scenes (an older bridge) still gives a useful overview`() {
        val o = TownOverview.of(camp, emptyList(), afternoon)
        assertEquals(3, o.today.size)
        assertTrue(o.places.all { it.scenes.isEmpty() })
        assertTrue(o.locked.isEmpty())
        assertEquals(listOf("Pastir Luka", "Gostilničarka Vida"), o.residents.map { it.name })
        assertEquals(listOf(2L, 1L), o.chronicle.map { it.at })
    }

    @Test fun `places read naturally in both languages`() {
        assertEquals("ob ognju · by the fire", TownPlace.Fire.where())
        assertEquals("pod lipo · under the linden tree", TownPlace.At(BuildingType.LIPA).where())
        assertEquals("hut", TownPlace.At(BuildingType.HUT).key())
        for (t in BuildingType.entries) {
            val w = TownPlace.At(t).where()
            assertTrue(w, w.contains(" · ") && abs(w.indexOf(" · ")) > 2)
        }
        assertEquals("🌙", TimeOfDay.NIGHT.emoji())
    }

    @Test fun `visitors on the road fold into one bubble with a count`() {
        val road = si.lanisce.lani.game.scene.TownPlace.Spot("road")
        val qs = (1..6).map { TownMarker("quest:q$it", TownMarker.Kind.QUEST, "⚒️", "Q$it", road) }
        val folded = foldBubbles(qs + TownMarker("quest:f", TownMarker.Kind.QUEST, "🐑", "F", si.lanisce.lani.game.scene.TownPlace.Fire))
        val visitors = folded.single { it.place == road }
        org.junit.Assert.assertEquals("cluster:spot:road", visitors.id)
        org.junit.Assert.assertEquals(6, clusterSize(visitors, qs))
        org.junit.Assert.assertEquals(1, folded.count { it.place == si.lanisce.lani.game.scene.TownPlace.Fire })
    }

    @Test fun `the surprise's bubble keeps its emoji and follows the stranger at the road`() {
        val today = java.time.LocalDate.of(2026, 9, 24)
        for (kind in listOf(si.lanisce.lani.game.Surprises.PEDLAR, si.lanisce.lani.game.Surprises.PILGRIM)) {
            val s = GameState(age = Age.TABOR, surprise = si.lanisce.lani.game.Surprise(today.toString(), kind, 1))
            val m = si.lanisce.lani.game.scene.TownMarkers.of(s, emptyList(), emptyList(), today).single { it.id == "surprise:$kind" }
            org.junit.Assert.assertEquals(si.lanisce.lani.game.Surprises.emoji(kind), m.emoji)
            org.junit.Assert.assertEquals(si.lanisce.lani.game.scene.TownPlace.Spot("road"), m.place)
            val stranger = si.lanisce.lani.game.Surprises.stranger(s, today)!!
            org.junit.Assert.assertEquals(kind, markerVillager(m, s, emptyList(), listOf(stranger)))
            // not drawn (an older screen without him): the bubble stays at the road
            org.junit.Assert.assertNull(markerVillager(m, s, emptyList(), emptyList()))
        }
        // a child's riddle at the road isn't a stranger's
        val riddle = TownMarker("surprise:riddle", TownMarker.Kind.HAPPENING, "❓", "R", si.lanisce.lani.game.scene.TownPlace.Spot("road"))
        org.junit.Assert.assertNull(markerVillager(riddle, GameState(), emptyList(), si.lanisce.lani.game.Surprises.strangers.values.toList()))
    }
}
