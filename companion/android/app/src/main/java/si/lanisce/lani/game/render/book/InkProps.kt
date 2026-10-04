package si.lanisce.lani.game.render.book

import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.book.InkPal.EARTH
import si.lanisce.lani.game.render.book.InkPal.FIRE
import si.lanisce.lani.game.render.book.InkPal.GOLD
import si.lanisce.lani.game.render.book.InkPal.INK
import si.lanisce.lani.game.render.book.InkPal.LEAF
import si.lanisce.lani.game.render.book.InkPal.PAPER
import si.lanisce.lani.game.render.book.InkPal.PINE
import si.lanisce.lani.game.render.book.InkPal.RED
import si.lanisce.lani.game.render.book.InkPal.SOFT
import si.lanisce.lani.game.render.book.InkPal.STONE
import si.lanisce.lani.game.render.book.InkPal.WHITE
import si.lanisce.lani.game.render.book.InkPal.WOOD
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The props of the book pictures ([si.lanisce.lani.game.scene.Vignettes.props]), in ink: things, trees, the sky's (a
 * cloud, the sun, the moon, stars), each standing on (or, up in the sky, round) its pen's anchor. [gold]: gilded (the
 * bell, a sword's shine, the treasure, a golden flower); [night]: the stars and the moon light up the dark.
 */
internal object InkProps {
    private fun f(vararg v: Float) = v
    private val HALF = PI.toFloat()

    fun draw(p: Pen, id: String, gold: Boolean, night: Boolean) {
        when (id) {
            "crown" -> InkFigures.crown(p, 0f, 5f)
            "sword" -> sword(p, gold)
            "bell" -> bell(p, gold)
            "tree" -> tree(p)
            "spruce" -> spruce(p)
            "cloud" -> cloud(p, night)
            "sun" -> sun(p)
            "moon" -> moon(p)
            "stars" -> stars(p, night)
            "flower" -> flower(p, gold)
            "rock" -> rock(p)
            "barrel" -> barrel(p)
            "chest" -> chest(p, gold)
            "sack" -> sack(p)
            "club" -> club(p)
            "table" -> table(p)
            "fire" -> fire(p)
            "house" -> house(p)
            "chapel" -> chapel(p)
            "wall" -> wall(p)
        }
    }

    private fun sword(p: Pen, gold: Boolean) {
        p.shape(f(-1f, -14f, 1f, -14f, 1f, -2f, 0f, 0f, -1f, -2f), fill = WHITE)
        p.line(0f, -13f, 0f, -3f, SOFT)
        p.shape(f(-4.5f, -15.5f, 4.5f, -15.5f, 4.5f, -14f, -4.5f, -14f), wash = GOLD, a = 0.8f)
        p.line(0f, -15.5f, 0f, -19.5f, WOOD); p.line(0.6f, -15.5f, 0.6f, -19.5f)
        p.oval(0.3f, -20.5f, 1.2f, 1.2f, wash = GOLD, a = 0.8f)
        if (gold) for (i in 0 until 8) {
            val t = i * PI.toFloat() / 4
            p.line(0.3f + 4f * cos(t), -15f + 4f * sin(t), 0.3f + 6.5f * cos(t), -15f + 6.5f * sin(t), GOLD)
        }
    }

    private fun bell(p: Pen, gold: Boolean) {
        p.arc(0f, -15.5f, 1.8f, 1.8f, HALF, 2 * HALF)
        val shape = f(-2.4f, -14f, 2.4f, -14f, 3.4f, -11.5f, 4.2f, -6f, 6.6f, -2f, 7.2f, 0f, -7.2f, 0f, -6.6f, -2f, -4.2f, -6f, -3.4f, -11.5f)
        p.shape(shape, wash = GOLD, a = if (gold) 0.85f else 0.5f)
        p.shape(f(1.5f, -14f, 2.4f, -14f, 3.4f, -11.5f, 4.2f, -6f, 6.6f, -2f, 7.2f, 0f, 3.5f, 0f, 2.5f, -6f), hatch = 3, edge = null)
        p.shape(shape, edge = INK)
        p.line(-6f, -2.4f, 6f, -2.4f)
        p.oval(0f, 1.4f, 1.3f, 1.3f, fill = INK)
        if (gold) for (i in 0 until 3) p.arc(0f, -7f, 9f + i * 3f, 7f + i * 2.5f, -0.35f, 0.35f, SOFT)
    }

    private fun tree(p: Pen) {
        p.shape(f(-1.6f, 0f, 1.6f, 0f, 1.2f, -13f, -1.2f, -13f), wash = WOOD, a = 0.6f)
        p.line(0f, -10f, -5f, -16f); p.line(0.5f, -11f, 5f, -17f)
        p.blob(f(0f, -7f, 7f, -4f, 5f, 0f), f(-22f, -18f, -18f, -27f, -27f, -30f), f(7.5f, 6.5f, 6.5f, 6f, 6f, 5f), LEAF, 0.55f, INK, 3, shadeRight = 2f)
        for (i in 0 until 5) p.lines(-6f + i * 3f, -20f + (i % 2) * 3f, -5f + i * 3f, -21f + (i % 2) * 3f, col = SOFT)
    }

    private fun spruce(p: Pen) {
        p.shape(f(-1f, 0f, 1f, 0f, 1f, -5f, -1f, -5f), wash = WOOD, a = 0.6f)
        for ((i, tier) in listOf(Triple(-4.5f, 9.5f, 13f), Triple(-10.5f, 7.5f, 12f), Triple(-16.5f, 5.5f, 11f), Triple(-22.5f, 3.5f, 10f)).withIndex()) {
            val (base, half, ht) = tier
            p.shape(f(-half, base, half, base, 0f, base - ht), wash = PINE, a = 0.55f + i * 0.03f)
            p.shape(f(0.5f, base - ht + 1f, half, base, 1f, base), hatch = 2, edge = null)
        }
    }

    private fun cloud(p: Pen, night: Boolean) =
        p.blob(f(-9f, -4f, 2f, 8f, 0f), f(-3f, -6.5f, -7.5f, -4f, -2.5f), f(4f, 5f, 5.5f, 4.2f, 4.5f), if (night) STONE else WHITE, if (night) 0.6f else 1f, INK, 3, shadeBelow = -2.5f)

    private fun sun(p: Pen) {
        for (i in 0 until 12) {
            val t = i * PI.toFloat() / 6
            val r1 = 6.5f; val r2 = if (i % 2 == 0) 10f else 8.5f
            p.line(r1 * cos(t), -6f + r1 * sin(t), r2 * cos(t), -6f + r2 * sin(t))
        }
        p.oval(0f, -6f, 5f, 5f, wash = GOLD, a = 0.75f)
        p.dot(-1.6f, -7f); p.dot(1.6f, -7f)
        p.arc(0f, -6f, 2.4f, 2f, 0.3f, HALF - 0.3f)
    }

    private fun moon(p: Pen) {
        val outer = p.ovalPoints(0f, -6f, 5.5f, 5.5f, 14, HALF * 0.35f, HALF * 1.65f)
        val inner = p.ovalPoints(2.8f, -6.8f, 4.4f, 4.6f, 14, HALF * 1.55f, HALF * 0.45f)
        p.shape(outer + inner, fill = WHITE)
        p.shape(outer + inner, wash = GOLD, a = 0.3f)
    }

    private fun stars(p: Pen, night: Boolean) {
        val col = if (night) WHITE else GOLD
        for (i in 0 until 7) {
            val u = (Noise.rnd(i, 31) - 0.5f) * 36f
            val v = -Noise.rnd(i, 32) * 14f
            val r = if (i % 3 == 0) 1.6f else 1f
            p.line(u - r, v, u + r, v, col); p.line(u, v - r, u, v + r, col)
            if (!night) p.dot(u, v, INK)
        }
    }

    private fun flower(p: Pen, gold: Boolean) {
        p.line(0f, 0f, 0f, -7f, LEAF)
        p.shape(f(0f, -2f, -3.5f, -4.5f, -1f, -3.5f), wash = LEAF, a = 0.8f)
        p.shape(f(0f, -3.5f, 3.5f, -5.5f, 1f, -4.8f), wash = LEAF, a = 0.8f)
        for (i in 0 until 5) {
            val t = -HALF / 2 + i * 2 * HALF / 5
            p.oval(1.9f * cos(t), -8.6f + 1.9f * sin(t), 1.35f, 1.35f, wash = if (gold) GOLD else RED, a = 0.75f)
        }
        p.dot(0f, -8.6f, GOLD)
    }

    private fun rock(p: Pen) {
        p.shape(f(-9f, 0f, -10f, -5f, -6f, -10f, 2f, -12f, 8f, -8f, 10f, 0f), wash = STONE, a = 0.55f)
        p.shape(f(2f, -12f, 8f, -8f, 10f, 0f, 3f, 0f, 4f, -6f), hatch = 3, edge = null)
        p.shape(f(-9f, 0f, -10f, -5f, -6f, -10f, 2f, -12f, 8f, -8f, 10f, 0f), edge = INK)
        p.lines(-4f, -8f, -2f, -4f, -3f, -1f)
        p.lines(2f, -12f, 4f, -6f, 3f, 0f, col = SOFT)
    }

    private fun barrel(p: Pen) {
        p.shape(f(-4.5f, 0f, -5.6f, -4f, -5.6f, -10f, -4.5f, -14f, 4.5f, -14f, 5.6f, -10f, 5.6f, -4f, 4.5f, 0f), wash = WOOD, a = 0.55f)
        for (u in listOf(-2.5f, 0f, 2.5f)) p.line(u, -13.5f, u, -0.5f, SOFT)
        p.line(-5.5f, -3f, 5.5f, -3f); p.line(-5.5f, -11f, 5.5f, -11f)
        p.oval(0f, -14f, 4.5f, 1.2f, wash = WOOD, a = 0.4f)
        p.shape(f(2f, -13f, 4.5f, -14f, 5.6f, -10f, 5.6f, -4f, 4.5f, 0f, 2f, 0f), hatch = 3, edge = null)
    }

    private fun chest(p: Pen, gold: Boolean) {
        if (gold) p.blob(f(-3f, 0f, 3f), f(-8.5f, -10f, -8.5f), f(2.5f, 3f, 2.5f), GOLD, 0.8f, INK)
        p.shape(f(-7f, 0f, 7f, 0f, 7f, -7f, -7f, -7f), wash = WOOD, a = 0.6f)
        if (!gold) p.shape(f(-7f, -7f, 7f, -7f, 6f, -10.5f, 0f, -12f, -6f, -10.5f), wash = WOOD, a = 0.6f, hatch = 3)
        for (u in listOf(-4.5f, 4.5f)) p.line(u, 0f, u, -7f)
        p.oval(0f, -5f, 1.2f, 1.4f, wash = GOLD, a = 0.9f)
        for ((u, v) in listOf(-9f to 0f, -10.5f to -1.2f, 9f to -0.4f, 10.2f to 0.6f, 8f to 0.8f)) p.oval(u, v, 1.2f, 0.8f, wash = GOLD, a = 0.85f)
    }

    private fun sack(p: Pen) {
        val shape = f(-5f, 0f, 5f, 0f, 6.2f, -5f, 4f, -10f, 1.5f, -12f, -1.5f, -12f, -4f, -10f, -6.2f, -5f)
        p.shape(shape, fill = WHITE)
        p.shape(f(2f, -10f, 4f, -10f, 6.2f, -5f, 5f, 0f, 2.5f, 0f), hatch = 3, edge = null)
        p.shape(shape)
        p.line(-1.8f, -12.2f, 1.8f, -12.2f)
        p.line(-1f, -12.2f, -2.4f, -14.8f); p.line(1f, -12.2f, 2.4f, -14.8f)
        p.line(-2f, -8f, -3f, -3f, SOFT); p.line(1f, -9f, 1.5f, -4f, SOFT)
    }

    private fun club(p: Pen) {
        p.shape(f(-0.9f, 0f, 0.9f, 0f, 2f, -12f, -0.2f, -12.4f), wash = WOOD, a = 0.65f)
        p.oval(1.2f, -16.5f, 3.4f, 4.8f, wash = WOOD, a = 0.65f, hatch = 2)
        for ((u, v) in listOf(-1.2f to -18f, 3.2f to -15f, 1f to -20f, 3.6f to -19f)) p.dot(u, v)
    }

    private fun table(p: Pen) {
        p.shape(f(-10.5f, -8f, -7f, -8f, -7f, 0f, -10.5f, 0f), wash = STONE, a = 0.55f, hatch = 3)
        p.shape(f(7f, -8f, 10.5f, -8f, 10.5f, 0f, 7f, 0f), wash = STONE, a = 0.55f, hatch = 3)
        p.shape(f(-13.5f, -11.5f, 13.5f, -11.5f, 13.5f, -8f, -13.5f, -8f), wash = STONE, a = 0.5f)
        p.line(-13f, -10f, 13f, -10f, SOFT)
    }

    private fun fire(p: Pen) {
        p.shape(f(-7f, -0.5f, 6f, -3.5f, 6.5f, -2f, -6.5f, 1f), wash = WOOD, a = 0.7f)
        p.shape(f(7f, -0.5f, -6f, -3.5f, -6.5f, -2f, 6.5f, 1f), wash = WOOD, a = 0.7f)
        p.shape(f(-5f, -1.5f, -3.5f, -7f, -1.5f, -5f, 0f, -13f, 2f, -6f, 3.5f, -9f, 5f, -1.5f), wash = FIRE, a = 0.8f)
        p.shape(f(-2.5f, -1.5f, -1f, -5f, 0.2f, -8.5f, 1.5f, -4f, 2.5f, -1.5f), wash = GOLD, a = 0.85f, edge = null)
        for ((u, v) in listOf(-2f to -15f, 2.5f to -17f, 0.5f to -19.5f)) p.dot(u, v, GOLD)
    }

    private fun house(p: Pen) {
        p.shape(f(-10f, 0f, 10f, 0f, 10f, -10f, -10f, -10f), fill = PAPER)
        p.shape(f(-10f, 0f, 10f, 0f, 10f, -10f, -10f, -10f), wash = EARTH, a = 0.2f)
        p.shape(f(5f, -18f, 8f, -18f, 8f, -12f, 5f, -12f), wash = STONE, a = 0.6f)
        p.shape(f(-12.5f, -10f, 0f, -21f, 12.5f, -10f), wash = WOOD, a = 0.55f, hatch = 2)
        p.lines(6.5f, -19f, 5f, -22f, 7f, -25f, 5.5f, -28f, col = SOFT)
        p.shape(f(2f, 0f, 5.5f, 0f, 5.5f, -6.5f, 2f, -6.5f), wash = WOOD, a = 0.7f)
        p.shape(f(-7f, -7f, -3f, -7f, -3f, -3.5f, -7f, -3.5f), fill = SOFT)
        p.line(-5f, -7f, -5f, -3.5f, PAPER)
    }

    private fun chapel(p: Pen) {
        p.oval(0f, -1f, 14f, 3.2f, wash = STONE, a = 0.5f, hatch = 3)
        p.blob(f(9f, 11.5f), f(-4.5f, -5f), f(2.4f, 2f), LEAF, 0.6f, INK)
        p.shape(f(-5f, -3f, 8f, -3f, 8f, -10f, -5f, -10f), fill = PAPER)
        p.shape(f(-5f, -3f, 8f, -3f, 8f, -10f, -5f, -10f), wash = EARTH, a = 0.15f)
        p.shape(f(-6f, -10f, 1.5f, -15f, 9f, -10f), wash = RED, a = 0.55f, hatch = 2)
        p.shape(f(-10f, -3f, -4.5f, -3f, -4.5f, -19f, -10f, -19f), fill = PAPER)
        p.shape(f(-6.5f, -3f, -4.5f, -3f, -4.5f, -19f, -6.5f, -19f), hatch = 2, edge = null)
        p.shape(f(-10f, -3f, -4.5f, -3f, -4.5f, -19f, -10f, -19f))
        p.shape(f(-10.5f, -19f, -7.25f, -27f, -4f, -19f), wash = RED, a = 0.6f)
        p.line(-7.25f, -27f, -7.25f, -30f); p.line(-8.5f, -29f, -6f, -29f)
        p.shape(f(-8f, -15.5f, -6.5f, -15.5f, -6.5f, -13f, -8f, -13f), fill = INK)
        p.shape(f(1f, -3f, 3f, -3f, 3f, -6.5f, 1f, -6.5f), fill = WOOD)
    }

    private fun wall(p: Pen) {
        p.shape(f(-18f, 0f, 18f, 0f, 18f, -6f, 12f, -7f, 4f, -6.5f, -6f, -7.2f, -18f, -6.2f), fill = PAPER)
        p.shape(f(-18f, 0f, 18f, 0f, 18f, -6f, 12f, -7f, 4f, -6.5f, -6f, -7.2f, -18f, -6.2f), wash = STONE, a = 0.5f)
        for (row in 0 until 3) {
            val v = -row * 2.3f
            p.line(-18f, v - 2.3f, 18f, v - 2.3f, SOFT)
            var u = -18f + (row % 2) * 2.5f
            while (u < 18f) { p.line(u, v, u, v - 2.3f); u += 5f + (row * 7 + u.toInt()).mod(3) }
        }
        p.shape(f(-18f, 0f, 18f, 0f, 18f, -6f, 12f, -7f, 4f, -6.5f, -6f, -7.2f, -18f, -6.2f))
    }
}
