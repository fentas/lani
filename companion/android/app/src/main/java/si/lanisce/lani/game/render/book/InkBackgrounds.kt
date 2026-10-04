package si.lanisce.lani.game.render.book

import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.book.InkPal.EARTH
import si.lanisce.lani.game.render.book.InkPal.GOLD
import si.lanisce.lani.game.render.book.InkPal.GRASS
import si.lanisce.lani.game.render.book.InkPal.INK
import si.lanisce.lani.game.render.book.InkPal.LEAF
import si.lanisce.lani.game.render.book.InkPal.NIGHT
import si.lanisce.lani.game.render.book.InkPal.PAPER
import si.lanisce.lani.game.render.book.InkPal.PINE
import si.lanisce.lani.game.render.book.InkPal.RED
import si.lanisce.lani.game.render.book.InkPal.SKY
import si.lanisce.lani.game.render.book.InkPal.SOFT
import si.lanisce.lani.game.render.book.InkPal.STONE
import si.lanisce.lani.game.render.book.InkPal.WATER
import si.lanisce.lani.game.render.book.InkPal.WHITE
import si.lanisce.lani.game.render.book.InkPal.WOOD
import kotlin.math.PI
import kotlin.math.sin

/**
 * The backgrounds of the book pictures ([si.lanisce.lani.game.scene.Vignettes.backgrounds]), in ink on the 160 × 96
 * paper: the sky (day, or hatched dark at night), the land behind, and the ground in front. Each returns its ground: the
 * row where a thing at y 0 stands (the bridge's is its deck).
 */
internal object InkBackgrounds {
    fun draw(ink: Ink, id: String, night: Boolean): Float = when (id) {
        "mountains" -> mountains(ink, night)
        "lake" -> lake(ink, night)
        "castle" -> castle(ink, night)
        "village" -> village(ink, night)
        "forest" -> forest(ink, night)
        "church" -> church(ink, night)
        "sea" -> sea(ink, night)
        "cave" -> cave(ink)
        "river" -> river(ink, night)
        "bridge" -> bridge(ink, night)
        "town" -> town(ink, night)
        else -> meadow(ink, night)
    }

    private fun f(vararg v: Float) = v

    /** The sky down to [horizon]: a thin blue wash, fading down, and a few woodcut streaks; at night dark hatching and stars. */
    fun sky(ink: Ink, horizon: Int, night: Boolean) {
        for (y in 0 until horizon) for (x in 0 until ink.w) {
            if (night) {
                ink.washAt(x, y, NIGHT, 0.62f)
                if (ink.hatched(x, y, 2) && Noise.rnd(x / 3, y / 3, ink.seed) < 0.75f) ink.c.blend(x, y, INK, 0.55f)
                if (Math.floorMod(Noise.hash(x, y, ink.seed), 173) == 0) ink.dot(x, y, WHITE)
            } else {
                val t = y.toFloat() / horizon
                ink.washAt(x, y, SKY, 0.42f * (1f - t) + 0.06f)
            }
        }
        if (night) return
        // the streaks a woodcut sky has: a few short lines
        for (i in 0 until 4) {
            val y = 8f + (Noise.rnd(i, 1, ink.seed) * (horizon - 24).coerceAtLeast(4)).toInt()
            val x = 10f + (Noise.rnd(i, 2, ink.seed) * 110).toInt()
            val len = 10f + (Noise.rnd(i, 3, ink.seed) * 22).toInt()
            ink.line(x, y, x + len, y, SOFT)
            ink.line(x + 4, y + 2, x + len - 6, y + 2, SOFT)
        }
    }

    /** The ground from [top] down: a wash of [col], a wavy edge, and tufts of grass. */
    fun ground(ink: Ink, top: Int, col: Int = GRASS, tufts: Boolean = true) {
        for (y in top until ink.h) for (x in 0 until ink.w) ink.washAt(x, y, col, 0.45f)
        var prev = top.toFloat()
        for (x in 0..ink.w step 4) {
            val y = top + sin(x * 0.07f + ink.seed) * 1.2f
            ink.line((x - 4).toFloat(), prev, x.toFloat(), y, SOFT)
            prev = y
        }
        if (!tufts) return
        for (i in 0 until 26) {
            val x = (Noise.rnd(i, 7, ink.seed) * ink.w).toInt().toFloat()
            val y = top + 4f + (Noise.rnd(i, 8, ink.seed) * (ink.h - top - 6)).toInt()
            ink.line(x, y, x - 1f, y - 3f, INK)
            ink.line(x, y, x + 1f, y - 3f, INK)
            if (i % 3 == 0) ink.line(x + 3f, y, x + 3f, y - 2f, SOFT)
        }
    }

    /** A range of hills or mountains: the ridge through [ridge] (x, y pairs) filled down to [base], washed and outlined. */
    fun range(ink: Ink, ridge: FloatArray, base: Float, col: Int, a: Float, edge: Int = SOFT, hatch: Int = 0) {
        val n = ridge.size / 2
        val xs = FloatArray(n + 2); val ys = FloatArray(n + 2)
        for (i in 0 until n) { xs[i] = ridge[2 * i]; ys[i] = ridge[2 * i + 1] }
        xs[n] = ridge[2 * n - 2]; ys[n] = base
        xs[n + 1] = ridge[0]; ys[n + 1] = base
        ink.wash(xs, ys, col, a)
        if (hatch > 0) ink.hatch(xs, ys, hatch)
        for (i in 0 until n - 1) ink.line(xs[i], ys[i], xs[i + 1], ys[i + 1], edge)
    }

    /** A spruce as the distance shows it: a dark little triangle. */
    fun farSpruce(ink: Ink, x: Float, y: Float, s: Float = 1f) =
        ink.area(x, y - 9 * s, x + 3 * s, y, x - 3 * s, y, wash = PINE, a = 0.6f, edge = SOFT)

    /** A cottage: its walls from [x] ([wd] wide, [ht] high) up from [base], a steep roof, a window and a door. */
    fun cottage(ink: Ink, x: Float, base: Float, wd: Float, ht: Float, roof: Int = WOOD) {
        ink.area(x, base - ht, x + wd, base - ht, x + wd, base, x, base, fill = PAPER, wash = EARTH, a = 0.18f)
        ink.area(x - 3f, base - ht, x + wd / 2, base - ht - wd * 0.7f, x + wd + 3f, base - ht, wash = roof, a = 0.55f, hatch = 2)
        val wx = x + wd * 0.2f; val wy = base - ht * 0.7f
        ink.area(wx, wy, wx + 3f, wy, wx + 3f, wy + 3f, wx, wy + 3f, fill = SOFT)
        val dx = x + wd * 0.62f; val dy = base - ht * 0.62f
        ink.area(dx, dy, dx + 3f, dy, dx + 3f, base, dx, base, wash = WOOD, a = 0.7f)
    }

    /** A church tower from [x] ([wd] wide), [top] to [base], with a spire, its bell's window and a cross. */
    fun tower(ink: Ink, x: Float, top: Float, base: Float, wd: Float) {
        ink.area(x, top, x + wd, top, x + wd, base, x, base, fill = PAPER, wash = EARTH, a = 0.15f, edge = null)
        ink.area(x + wd * 0.65f, top, x + wd, top, x + wd, base, x + wd * 0.65f, base, hatch = 3, edge = null)
        ink.area(x, top, x + wd, top, x + wd, base, x, base)
        val tip = top - wd * 1.6f
        ink.area(x - 1f, top, x + wd / 2, tip, x + wd + 1f, top, wash = RED, a = 0.6f)
        ink.area(x + wd / 2, tip, x + wd + 1f, top, x + wd / 2, top, hatch = 2, edge = null)
        ink.line(x + wd / 2, tip, x + wd / 2, tip - 4f)
        ink.line(x + wd / 2 - 1.5f, tip - 3f, x + wd / 2 + 1.5f, tip - 3f)
        ink.area(x + wd / 2 - 1.5f, top + 3f, x + wd / 2 + 1.5f, top + 3f, x + wd / 2 + 1.5f, top + 8f, x + wd / 2 - 1.5f, top + 8f, fill = SOFT)
    }

    /** A tree trunk from [top] to [base], [wd] wide, its bark flecked. */
    fun trunk(ink: Ink, x: Float, top: Float, base: Float, wd: Float) {
        val (tx, ty) = ink.rect(x, top, x + wd, base)
        ink.wash(tx, ty, WOOD, 0.45f)
        ink.scan(tx, ty) { px, py -> if (Math.floorMod(px * 3 + py / 4, 5) == 0) ink.dot(px, py, SOFT) }
        ink.line(x, top, x, base)
        ink.line(x + wd, top, x + wd, base)
    }

    private fun mountains(ink: Ink, night: Boolean): Float {
        sky(ink, 72, night)
        // the far range, blue with distance
        range(ink, f(0f, 50f, 14f, 42f, 26f, 47f, 40f, 36f, 52f, 44f, 64f, 40f, 90f, 46f, 112f, 34f, 130f, 42f, 146f, 30f, 160f, 40f), 82f, SKY, 0.35f)
        // the big peak with three heads, its shaded flank, its snow, its gullies
        range(ink, f(10f, 78f, 34f, 52f, 50f, 38f, 60f, 30f, 66f, 33f, 74f, 18f, 82f, 28f, 88f, 25f, 100f, 40f, 122f, 58f, 150f, 78f), 82f, STONE, 0.42f, INK)
        ink.area(74f, 18f, 82f, 28f, 88f, 25f, 100f, 40f, 122f, 58f, 150f, 78f, 110f, 78f, 92f, 52f, 80f, 34f, hatch = 3, edge = null)
        ink.area(60f, 30f, 66f, 33f, 74f, 18f, 82f, 28f, 88f, 25f, 95f, 34f, 86f, 33f, 80f, 38f, 74f, 32f, 68f, 39f, 62f, 36f, 55f, 36f, fill = WHITE)
        for ((x0, y0, x1) in listOf(Triple(74f, 24f, 68f), Triple(64f, 38f, 56f), Triple(84f, 34f, 92f), Triple(96f, 42f, 104f))) {
            ink.line(x0, y0, x1, y0 + 20f, INK)
            ink.line(x0 + 1, y0 + 6f, x1 + 3, y0 + 18f, SOFT)
        }
        // a lower peak on the left
        range(ink, f(0f, 60f, 10f, 48f, 18f, 52f, 28f, 44f, 40f, 58f, 48f, 72f), 82f, STONE, 0.3f, INK)
        ink.area(28f, 44f, 40f, 58f, 48f, 72f, 36f, 72f, 32f, 56f, hatch = 3, edge = null)
        // spruces at the foot, the meadow, two boulders
        for (i in 0 until 9) farSpruce(ink, 8f + i * 17f + Noise.rnd(i, 3, ink.seed) * 6f, 81f + (i % 2), 0.9f)
        ground(ink, 81)
        for ((x, y) in listOf(18f to 90f, 138f to 92f)) ink.area(x - 5, y, x - 3, y - 4, x + 2, y - 5, x + 5, y, wash = STONE, a = 0.5f)
        return 88f
    }

    private fun lake(ink: Ink, night: Boolean): Float {
        sky(ink, 58, night)
        range(ink, f(0f, 30f, 16f, 22f, 30f, 30f, 44f, 20f, 60f, 34f, 70f, 42f, 76f, 58f), 58f, STONE, 0.35f, INK)
        ink.area(44f, 20f, 60f, 34f, 70f, 42f, 76f, 58f, 56f, 58f, hatch = 3, edge = null)
        range(ink, f(80f, 58f, 96f, 38f, 110f, 42f, 124f, 30f, 140f, 36f, 160f, 26f), 58f, SKY, 0.4f)
        for (i in 0 until 12) farSpruce(ink, 4f + i * 6f, 58f, 0.6f)
        // the water and its ripples
        for (y in 58 until 78) for (x in 0 until ink.w) ink.washAt(x, y, WATER, 0.42f + (y - 58) * 0.006f)
        ink.line(0f, 58f, 160f, 58f, SOFT)
        for (row in 0 until 6) {
            val y = 61f + row * 3f
            var x = (row * 11 % 17).toFloat()
            while (x < 160f) {
                val len = 5f + Noise.rnd(x.toInt(), row, ink.seed) * 8f
                ink.line(x, y, x + len, y, if (row % 2 == 0) SOFT else INK)
                x += len + 7f + Noise.rnd(row, x.toInt(), ink.seed) * 9f
            }
        }
        ground(ink, 78)
        // reeds at the shore
        for (i in 0 until 14) {
            val x = if (i < 7) 4f + i * 3f else 128f + (i - 7) * 4f
            val top = 70f - (i % 4) * 2
            ink.line(x, 80f, x + (i % 3 - 1), top, INK)
            if (i % 3 == 0) ink.line(x + (i % 3 - 1), top, x + 1, top - 4f, WOOD)
        }
        return 88f
    }

    private fun castle(ink: Ink, night: Boolean): Float {
        sky(ink, 72, night)
        range(ink, f(0f, 58f, 30f, 50f, 60f, 56f, 80f, 60f), 82f, SKY, 0.3f)
        range(ink, f(90f, 60f, 110f, 56f, 136f, 48f, 160f, 54f), 82f, SKY, 0.3f)
        // the hill
        range(ink, f(22f, 80f, 44f, 60f, 62f, 50f, 80f, 47f, 100f, 50f, 118f, 60f, 140f, 80f), 82f, GRASS, 0.5f, INK)
        ink.area(100f, 50f, 118f, 60f, 140f, 80f, 110f, 80f, hatch = 3, edge = null)
        // the walls, their battlements, the gate
        ink.area(62f, 36f, 98f, 36f, 98f, 49f, 62f, 49f, fill = PAPER, wash = STONE, a = 0.3f)
        for (x in 62 until 98 step 5) ink.area(x.toFloat(), 33f, x + 3f, 33f, x + 3f, 36f, x.toFloat(), 36f, wash = STONE, a = 0.3f)
        ink.area(88f, 36f, 98f, 36f, 98f, 49f, 88f, 49f, hatch = 3, edge = null)
        ink.area(76f, 49f, 76f, 43f, 78f, 41f, 80f, 40f, 82f, 41f, 84f, 43f, 84f, 49f, fill = SOFT)
        // two towers, their pointed roofs, a flag
        for ((x, top, wd) in listOf(Triple(55f, 26f, 9f), Triple(95f, 20f, 10f))) {
            ink.area(x, top, x + wd, top, x + wd, 50f, x, 50f, fill = PAPER, wash = STONE, a = 0.35f, edge = null)
            ink.area(x + wd * 0.6f, top, x + wd, top, x + wd, 50f, x + wd * 0.6f, 50f, hatch = 3, edge = null)
            ink.area(x, top, x + wd, top, x + wd, 50f, x, 50f)
            ink.area(x - 1.5f, top, x + wd / 2, top - 12f, x + wd + 1.5f, top, wash = RED, a = 0.6f)
            ink.area(x + wd / 2, top - 12f, x + wd + 1.5f, top, x + wd / 2, top, hatch = 3, edge = null)
            ink.line(x + wd / 2, top + 6f, x + wd / 2, top + 9f)
            ink.line(x + wd / 2, top + 15f, x + wd / 2, top + 18f)
        }
        ink.line(100f, 8f, 100f, 1f)
        ink.area(100f, 1f, 108f, 3f, 100f, 5f, wash = RED, a = 0.7f)
        // the road up
        ink.line(80f, 50f, 70f, 62f, SOFT); ink.line(70f, 62f, 90f, 72f, SOFT); ink.line(90f, 72f, 76f, 82f, SOFT)
        ground(ink, 81)
        return 88f
    }

    private fun village(ink: Ink, night: Boolean): Float {
        sky(ink, 70, night)
        range(ink, f(0f, 52f, 24f, 44f, 50f, 50f, 80f, 42f, 110f, 48f, 136f, 40f, 160f, 48f), 77f, GRASS, 0.3f)
        for (i in 0 until 10) farSpruce(ink, 20f + i * 14f + Noise.rnd(i, 4, ink.seed) * 8f, 50f + (i % 3) * 2f, 0.6f)
        // the church in the middle, the houses round it
        ink.area(80f, 52f, 104f, 52f, 104f, 70f, 80f, 70f, fill = PAPER, wash = EARTH, a = 0.15f)
        ink.area(78f, 52f, 92f, 42f, 106f, 52f, wash = RED, a = 0.5f, hatch = 2)
        tower(ink, 70f, 30f, 70f, 10f)
        cottage(ink, 14f, 72f, 22f, 12f)
        cottage(ink, 42f, 68f, 18f, 10f, RED)
        cottage(ink, 116f, 70f, 20f, 11f)
        cottage(ink, 140f, 74f, 16f, 10f, RED)
        ground(ink, 76)
        // the path into the village
        for (y in 77 until ink.h) for (x in 0 until ink.w) {
            val l = 70f - (y - 76) * 0.6f; val r = 86f + (y - 76) * 0.7f
            if (x > l && x < r) ink.washAt(x, y, EARTH, 0.35f)
        }
        ink.line(70f, 76f, 58f, 96f, SOFT); ink.line(86f, 76f, 100f, 96f, SOFT)
        return 88f
    }

    private fun forest(ink: Ink, night: Boolean): Float {
        sky(ink, 60, night)
        // the far spruces, a wall of them
        for (i in 0 until 16) {
            val x = i * 10f + Noise.rnd(i, 5, ink.seed) * 6f
            val top = 22f + Noise.rnd(i, 6, ink.seed) * 16f
            ink.area(x, top, x + 7f, 74f, x - 7f, 74f, wash = PINE, a = 0.35f, edge = SOFT)
        }
        // the big trunks at the sides and their crowns
        for ((x, wd) in listOf(4f to 7f, 26f to 5f, 128f to 5f, 148f to 8f)) trunk(ink, x, 0f, 86f, wd)
        ink.blob(f(0f, 14f, 30f, 22f), f(6f, 2f, 10f, 20f), f(14f, 12f, 11f, 9f), LEAF, 0.55f, INK, 3) { x, y -> x + y > 30 }
        ink.blob(f(128f, 146f, 160f, 138f), f(8f, 2f, 12f, 20f), f(11f, 13f, 12f, 9f), LEAF, 0.55f, INK, 3) { x, y -> x + y > 160 }
        ground(ink, 78, LEAF)
        // ferns
        for (i in 0 until 7) {
            val x = 36f + i * 14f + Noise.rnd(i, 9, ink.seed) * 6f
            val y = 84f + (i % 3) * 3f
            ink.line(x, y, x - 4f, y - 4f, INK); ink.line(x, y, x + 4f, y - 5f, INK); ink.line(x, y, x, y - 6f, INK)
        }
        return 88f
    }

    private fun church(ink: Ink, night: Boolean): Float {
        sky(ink, 72, night)
        range(ink, f(0f, 56f, 40f, 48f, 80f, 58f, 120f, 46f, 160f, 54f), 73f, GRASS, 0.3f)
        // the nave, its roof, windows and door; the tower on its left
        ink.area(76f, 40f, 140f, 40f, 140f, 72f, 76f, 72f, fill = PAPER, wash = EARTH, a = 0.15f)
        ink.area(72f, 40f, 108f, 24f, 144f, 40f, wash = RED, a = 0.5f, hatch = 2)
        for (x in listOf(90f, 124f)) ink.area(x, 60f, x, 52f, x + 2f, 50f, x + 4f, 52f, x + 4f, 60f, fill = SOFT, edge = null)
        ink.area(104f, 72f, 104f, 62f, 108f, 58f, 112f, 62f, 112f, 72f, wash = WOOD, a = 0.7f)
        tower(ink, 58f, 26f, 72f, 14f)
        // the churchyard wall, and the graves
        ink.area(0f, 72f, 160f, 72f, 160f, 77f, 0f, 77f, wash = STONE, a = 0.4f)
        for (x in 0 until 160 step 7) ink.line(x.toFloat(), 72f, x.toFloat(), 77f, SOFT)
        ground(ink, 77)
        for ((i, x) in listOf(12f, 30f, 48f, 138f).withIndex()) {
            val y = 86f + (i % 2) * 3f
            if (i % 2 == 0) {
                ink.line(x, y, x, y - 9f)
                ink.line(x - 3f, y - 6f, x + 3f, y - 6f)
            } else {
                ink.area(x - 3f, y, x - 3f, y - 6f, x, y - 8f, x + 3f, y - 6f, x + 3f, y, wash = STONE, a = 0.5f)
            }
        }
        return 88f
    }

    private fun sea(ink: Ink, night: Boolean): Float {
        sky(ink, 48, night)
        // the sea to the horizon, and its waves
        for (y in 48 until 80) for (x in 0 until ink.w) ink.washAt(x, y, WATER, 0.4f + (y - 48) * 0.006f)
        ink.line(0f, 48f, 160f, 48f, SOFT)
        for (row in 0 until 9) {
            val y = 51f + row * 3.2f
            var x = (row * 7 % 13).toFloat()
            val size = 2f + row * 0.35f
            while (x < 160f) {
                val col = if (row < 3) SOFT else INK
                ink.line(x, y, x + size, y - size * 0.6f, col)
                ink.line(x + size, y - size * 0.6f, x + size * 2, y, col)
                x += size * 2 + 6f + Noise.rnd(x.toInt(), row, ink.seed) * 10f
            }
        }
        // the cliff on the left with its little tower, the far headland
        ink.area(0f, 30f, 12f, 30f, 22f, 36f, 32f, 50f, 38f, 66f, 30f, 80f, 0f, 80f, wash = STONE, a = 0.5f)
        ink.area(22f, 36f, 32f, 50f, 38f, 66f, 30f, 80f, 18f, 60f, hatch = 3, edge = null)
        tower(ink, 6f, 18f, 31f, 8f)
        range(ink, f(118f, 48f, 132f, 42f, 148f, 44f, 160f, 40f), 48f, STONE, 0.3f)
        // the beach, its pebbles
        ground(ink, 80, EARTH, tufts = false)
        for (i in 0 until 30) {
            val x = (Noise.rnd(i, 11, ink.seed) * 160f).toInt(); val y = (83f + Noise.rnd(i, 12, ink.seed) * 12f).toInt()
            ink.dot(x, y, SOFT)
            if (i % 4 == 0) ink.dot(x + 1, y, INK)
        }
        return 88f
    }

    private fun cave(ink: Ink): Float {
        // the rock all round, dark and cross-hatched
        for (y in 0 until ink.h) for (x in 0 until ink.w) {
            ink.washAt(x, y, STONE, 0.55f)
            ink.washAt(x, y, NIGHT, 0.35f)
            if (ink.hatched(x, y, 2, cross = Noise.v2(x / 9f, y / 9f, ink.seed) > 0.45f)) ink.dot(x, y, INK)
        }
        // the hall inside, lit by a torch: a low wide vault, its walls darker towards the rock
        val ax = FloatArray(17); val ay = FloatArray(17)
        for (i in 0 until 17) {
            val t = i / 16f
            ax[i] = 6f + t * 148f
            ay[i] = 96f - (sin(t * PI.toFloat()) * 1.25f).coerceAtMost(1f) * 80f + (Noise.rnd(i, 13, ink.seed) - 0.5f) * 5f
        }
        val vault = { x: Int -> 96f - (sin(((x - 6f) / 148f).coerceIn(0f, 1f) * PI.toFloat()) * 1.25f).coerceAtMost(1f) * 80f }
        ink.solid(ax, ay, PAPER)
        ink.wash(ax, ay, EARTH, 0.28f)
        ink.scan(ax, ay) { x, y ->
            // near the rock, the wall in shade: hatched, closer still crossed
            val inside = minOf(y - vault(x), x - 6f, 154f - x)
            val glow = kotlin.math.hypot(x - 128f, y - 42f)
            if (glow < 22f) ink.washAt(x, y, GOLD, 0.25f * (1f - glow / 22f))
            if (inside < 12f && y < 84 && ink.hatched(x, y, if (inside < 6f) 2 else 3, cross = inside < 5f)) ink.dot(x, y, SOFT)
        }
        ink.outline(ax, ay, true, INK)
        // stalactites off the vault, stalagmites off the floor
        for (i in 2 until 15 step 2) {
            val x = ax[i]; val y = ay[i]
            ink.area(x - 2f, y - 1f, x + 2f, y - 1f, x, y + 5f + (i % 3) * 3f, fill = SOFT)
        }
        for (x in listOf(22f, 34f, 140f)) ink.area(x - 2.5f, 84f, x + 2.5f, 84f, x, 76f - (x.toInt() % 3) * 2f, wash = STONE, a = 0.7f)
        // a torch on the wall
        ink.line(128f, 44f, 130f, 54f, WOOD)
        ink.area(126f, 44f, 128f, 36f, 131f, 44f, wash = GOLD, a = 0.85f, edge = null)
        ink.area(127.5f, 44f, 128.5f, 40f, 129.5f, 44f, fill = InkPal.FIRE, edge = null)
        // its floor, and stones
        for (y in 84 until 94) for (x in 10 until 150) if (Noise.rnd(x / 4, y / 3, ink.seed) < 0.25f && ink.hatched(x, y, 3)) ink.dot(x, y, SOFT)
        ink.line(10f, 84f, 150f, 84f, SOFT)
        return 88f
    }

    private fun river(ink: Ink, night: Boolean): Float {
        sky(ink, 56, night)
        range(ink, f(0f, 44f, 30f, 36f, 60f, 44f, 90f, 34f, 124f, 42f, 160f, 36f), 56f, GRASS, 0.3f)
        ground(ink, 56)
        // the river winding from the back to the front
        val left = f(74f, 56f, 64f, 60f, 70f, 65f, 58f, 72f, 50f, 80f, 44f, 88f, 30f, 96f)
        val right = f(82f, 56f, 76f, 60f, 86f, 65f, 84f, 72f, 100f, 80f, 116f, 88f, 136f, 96f)
        val rx = FloatArray(14) { i -> if (i < 7) left[2 * i] else right[2 * (13 - i)] }
        val ry = FloatArray(14) { i -> if (i < 7) left[2 * i + 1] else right[2 * (13 - i) + 1] }
        ink.solid(rx, ry, PAPER)
        ink.wash(rx, ry, WATER, 0.5f)
        ink.scan(rx, ry) { x, y -> if (y > 60 && Math.floorMod(y, 4) == 0 && Noise.rnd(x / 5, y, ink.seed) < 0.45f) ink.dot(x, y, if (y % 8 == 0) WHITE else SOFT) }
        for (i in 0 until 6) {
            ink.line(left[2 * i], left[2 * i + 1], left[2 * i + 2], left[2 * i + 3])
            ink.line(right[2 * i], right[2 * i + 1], right[2 * i + 2], right[2 * i + 3])
        }
        // a willow on the left bank, reeds on the right
        trunk(ink, 16f, 42f, 74f, 4f)
        ink.blob(f(10f, 18f, 26f, 18f), f(40f, 34f, 42f, 46f), f(9f, 9f, 8f, 7f), LEAF, 0.5f, INK, 3) { x, _ -> x > 20 }
        for (i in 0 until 10) ink.line(10f + i * 2f, 44f, 8f + i * 2.4f, 58f + (i % 3) * 2, SOFT)
        for (i in 0 until 8) {
            val x = 132f + i * 3f
            ink.line(x, 82f, x + (i % 3 - 1), 70f - (i % 3) * 3, INK)
        }
        return 88f
    }

    private fun bridge(ink: Ink, night: Boolean): Float {
        sky(ink, 50, night)
        // the gorge's walls, the land beyond
        ink.area(0f, 26f, 18f, 32f, 30f, 50f, 34f, 96f, 0f, 96f, wash = STONE, a = 0.5f, hatch = 3)
        ink.area(160f, 22f, 142f, 30f, 128f, 48f, 124f, 96f, 160f, 96f, wash = STONE, a = 0.5f, hatch = 3)
        range(ink, f(30f, 50f, 60f, 40f, 90f, 46f, 128f, 38f), 60f, GRASS, 0.3f)
        // the river far below
        for (y in 78 until ink.h) for (x in 34 until 124) ink.washAt(x, y, WATER, 0.55f)
        for (row in 0 until 5) {
            var x = 36f + row * 3
            while (x < 118f) { ink.line(x, 80f + row * 3.5f, x + 4f, 80f + row * 3.5f, SOFT); x += 11f }
        }
        // the arch under the deck, its stones
        val ox = FloatArray(13); val oy = FloatArray(13); val ix = FloatArray(13); val iy = FloatArray(13)
        for (i in 0 until 13) {
            val t = i / 12f
            val s = sin(t * PI.toFloat())
            ox[i] = 36f + t * 88f; oy[i] = 64f + (1f - s) * 32f
            ix[i] = 44f + t * 72f; iy[i] = 72f + (1f - s) * 24f
        }
        val sx = ox + floatArrayOf(124f) + ix.reversedArray() + floatArrayOf(36f)
        val sy = oy + floatArrayOf(64f) + iy.reversedArray() + floatArrayOf(64f)
        ink.solid(sx, sy, PAPER); ink.wash(sx, sy, STONE, 0.4f); ink.hatch(sx, sy, 4)
        for (i in 0 until 12) {
            ink.line(ix[i], iy[i], ix[i + 1], iy[i + 1])
            ink.line(ix[i], iy[i], ox[i], oy[i], SOFT)
        }
        // the piers on the banks, the deck, the parapet
        for ((x0, x1) in listOf(4f to 36f, 124f to 156f)) ink.area(x0, 64f, x1, 64f, x1, 96f, x0, 96f, wash = STONE, a = 0.45f, hatch = 3)
        ink.area(4f, 58f, 156f, 58f, 156f, 64f, 4f, 64f, fill = PAPER, wash = STONE, a = 0.35f)
        for (x in 8 until 156 step 8) ink.line(x.toFloat(), 58f, x.toFloat(), 64f, SOFT)
        ink.area(4f, 54f, 156f, 54f, 156f, 58f, 4f, 58f, fill = PAPER, wash = STONE, a = 0.25f)
        return 60f
    }

    private fun meadow(ink: Ink, night: Boolean): Float {
        sky(ink, 66, night)
        range(ink, f(0f, 48f, 20f, 40f, 46f, 44f, 70f, 36f, 96f, 44f, 124f, 34f, 160f, 42f), 71f, SKY, 0.28f)
        range(ink, f(0f, 60f, 30f, 52f, 64f, 58f, 100f, 50f, 132f, 56f, 160f, 50f), 71f, GRASS, 0.35f, INK)
        for (i in 0 until 5) ink.blob(f(12f + i * 34f), f(55f - (i % 2) * 3f), f(3.2f), LEAF, 0.6f, SOFT)
        ground(ink, 70)
        // a fence across the meadow
        for (x in 92 until 160 step 10) ink.line(x.toFloat(), 80f, x.toFloat(), 70f, INK)
        ink.line(90f, 73f, 160f, 72f, INK); ink.line(90f, 77f, 160f, 76f, INK)
        // flowers
        for (i in 0 until 18) {
            val x = (Noise.rnd(i, 21, ink.seed) * 160f).toInt(); val y = (78f + Noise.rnd(i, 22, ink.seed) * 16f).toInt()
            ink.dot(x, y, if (i % 3 == 0) RED else if (i % 3 == 1) WHITE else GOLD)
        }
        return 88f
    }

    private fun town(ink: Ink, night: Boolean): Float {
        sky(ink, 72, night)
        // roofs and a spire behind the wall
        for (i in 0 until 7) {
            val x = 6f + i * 22f
            ink.area(x, 46f, x + 9f, 34f + (i % 3) * 5f, x + 18f, 46f, wash = RED, a = 0.45f, hatch = 3)
        }
        ink.area(122f, 46f, 126f, 30f, 128f, 10f, 130f, 30f, 134f, 46f, wash = STONE, a = 0.45f, hatch = 2)
        // the wall, its battlements and courses, the gate tower
        ink.area(0f, 46f, 160f, 46f, 160f, 72f, 0f, 72f, fill = PAPER, wash = STONE, a = 0.3f)
        for (x in 0 until 160 step 6) ink.area(x.toFloat(), 43f, x + 3.5f, 43f, x + 3.5f, 46f, x.toFloat(), 46f, wash = STONE, a = 0.3f)
        for (row in 0 until 4) for (x in 0 until 160 step 10) ink.line(x + (row % 2) * 5f, 50f + row * 6f, x + (row % 2) * 5f + 6f, 50f + row * 6f, SOFT)
        ink.area(64f, 26f, 96f, 26f, 96f, 72f, 64f, 72f, fill = PAPER, wash = STONE, a = 0.35f, edge = null)
        ink.area(86f, 26f, 96f, 26f, 96f, 72f, 86f, 72f, hatch = 3, edge = null)
        ink.area(64f, 26f, 96f, 26f, 96f, 72f, 64f, 72f)
        for (x in 64 until 96 step 6) ink.area(x.toFloat(), 22f, x + 3.5f, 22f, x + 3.5f, 26f, x.toFloat(), 26f, wash = STONE, a = 0.35f)
        ink.area(72f, 72f, 72f, 56f, 76f, 50f, 80f, 48f, 84f, 50f, 88f, 56f, 88f, 72f, fill = SOFT, hatch = 2, hatchCol = INK)
        // the square's cobbles
        ground(ink, 72, STONE, tufts = false)
        for (row in 0 until 7) {
            val y = 76f + row * 3f
            var x = (row % 2) * 4f
            while (x < 160f) { ink.line(x, y, x + 5f, y, SOFT); x += 8f }
        }
        return 88f
    }
}
