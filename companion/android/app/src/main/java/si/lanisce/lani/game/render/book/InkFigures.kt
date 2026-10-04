package si.lanisce.lani.game.render.book

import si.lanisce.lani.game.render.book.InkPal.BLUE
import si.lanisce.lani.game.render.book.InkPal.EARTH
import si.lanisce.lani.game.render.book.InkPal.GOLD
import si.lanisce.lani.game.render.book.InkPal.INK
import si.lanisce.lani.game.render.book.InkPal.LEAF
import si.lanisce.lani.game.render.book.InkPal.PAPER
import si.lanisce.lani.game.render.book.InkPal.PINE
import si.lanisce.lani.game.render.book.InkPal.RED
import si.lanisce.lani.game.render.book.InkPal.SOFT
import si.lanisce.lani.game.render.book.InkPal.STONE
import si.lanisce.lani.game.render.book.InkPal.WATER
import si.lanisce.lani.game.render.book.InkPal.WHITE
import si.lanisce.lani.game.render.book.InkPal.WOOD
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The figures of the book pictures ([si.lanisce.lani.game.scene.Vignettes.figures]), in ink: chibi people (big heads,
 * dot eyes, as the village draws them) about 25 units tall, the creatures of the legends and the animals, facing right
 * unless the pen is flipped. Each stands on its pen's anchor. [gold]: what shines is gilded (Zlatorog's horns on a white
 * chamois, a girl's fair hair, a fairy's star, the knight's sword).
 */
internal object InkFigures {
    private fun f(vararg v: Float) = v
    private const val HALF = PI.toFloat()

    fun draw(p: Pen, id: String, gold: Boolean) {
        when (id) {
            "king" -> king(p)
            "girl" -> girl(p, gold)
            "woman" -> woman(p)
            "boy" -> boy(p)
            "old-man" -> oldMan(p)
            "peasant" -> peasant(p)
            "hunter" -> hunter(p)
            "knight" -> knight(p, gold)
            "soldier" -> soldier(p)
            "giant" -> giant(p.scaled(1.7f))
            "fairy" -> fairy(p, gold)
            "water-man" -> waterMan(p)
            "devil" -> devil(p)
            "goblin" -> goblin(p.scaled(0.72f))
            "dragon" -> dragon(p, gold)
            "chamois" -> chamois(p, gold)
            "horse" -> horse(p)
            "ox" -> ox(p)
            "boar" -> boar(p)
            "dog" -> dog(p)
            "cat" -> cat(p)
            "bird" -> bird(p)
            "boat" -> boat(p)
            "sheep" -> sheep(p)
        }
    }

    // --- people's parts --------------------------------------------------------------------------------------------

    /** The head round (0, [cv]), its face: two dots for eyes. */
    private fun head(p: Pen, cv: Float = -19f, r: Float = 5.5f, fill: Int = PAPER, eyes: Boolean = true) {
        p.oval(0f, cv, r, r, fill = fill)
        if (eyes) { p.dot(-1.9f, cv + 0.6f); p.dot(1.9f, cv + 0.6f) }
    }

    /** Hair over the top of the head, [col]. */
    private fun fringe(p: Pen, col: Int, cv: Float = -19f, r: Float = 5.5f) =
        p.shape(p.ovalPoints(0f, cv - 0.6f, r + 0.4f, r + 0.2f, 12, HALF, 2 * HALF), wash = col, a = 0.7f)

    private fun legs(p: Pen, top: Float = -5f, col: Int? = null) {
        if (col != null) {
            p.shape(f(-3.2f, top, -0.6f, top, -1f, 0f, -3f, 0f), wash = col, a = 0.55f)
            p.shape(f(0.6f, top, 3.2f, top, 3f, 0f, 1f, 0f), wash = col, a = 0.55f)
        } else {
            p.line(-2f, top, -2f, 0f); p.line(2f, top, 2f, 0f)
        }
        p.line(-3.5f, 0f, -1.2f, 0f); p.line(1.2f, 0f, 3.5f, 0f)
    }

    private fun tunic(p: Pen, col: Int, a: Float = 0.55f, hatch: Int = 0, wide: Float = 0f) =
        p.shape(f(-4f - wide, -13f, 4f + wide, -13f, 5.5f + wide, -4f, -5.5f - wide, -4f), wash = col, a = a, hatch = hatch)

    private fun dress(p: Pen, col: Int, a: Float = 0.55f, flare: Float = 7f, hem: Float = 0f) =
        p.shape(f(-4f, -13f, 4f, -13f, flare, hem, -flare, hem), wash = col, a = a)

    /** Both arms, from the shoulders down to the hands at ([lu], [lv]) and ([ru], [rv]). */
    private fun arms(p: Pen, lu: Float = -6f, lv: Float = -6f, ru: Float = 6f, rv: Float = -6f) {
        p.line(-4f, -12f, lu, lv); p.line(-3.4f, -12f, lu + 0.6f, lv)
        p.line(4f, -12f, ru, rv); p.line(3.4f, -12f, ru - 0.6f, rv)
    }

    private fun beard(p: Pen, col: Int = WHITE, long: Float = 0f) {
        p.shape(f(-3.8f, -16.5f, 3.8f, -16.5f, 2.4f, -11f - long * 0.5f, 0f, -9f - long, -2.4f, -11f - long * 0.5f), fill = col)
        p.line(-1f, -14f, -0.6f, -11f, SOFT); p.line(1f, -14f, 0.6f, -11f, SOFT)
    }

    /** A hat with a brim: [brim] wide either side at [v], its crown [h] high. */
    private fun hat(p: Pen, col: Int, brim: Float = 7f, v: Float = -22.5f, h: Float = 4f, hatch: Int = 0) {
        p.shape(f(-4f, v, -3.5f, v - h, 3.5f, v - h, 4f, v), wash = col, a = 0.7f, hatch = hatch)
        p.shape(f(-brim, v, brim, v, brim - 1f, v + 1.2f, -brim + 1f, v + 1.2f), wash = col, a = 0.7f)
    }

    // --- people -----------------------------------------------------------------------------------------------------

    private fun king(p: Pen) {
        p.shape(f(-5.5f, -13f, 5.5f, -13f, 8f, 0f, -8f, 0f), wash = RED, a = 0.6f, hatch = 3)
        p.shape(f(-4f, -13f, 4f, -13f, 5f, 0f, -5f, 0f), wash = RED, a = 0.45f)
        p.shape(f(-5f, -1.5f, 5f, -1.5f, 5.2f, 0f, -5.2f, 0f), fill = WHITE)
        p.line(0f, -12f, 0f, -1.5f)
        arms(p)
        // the sceptre
        p.line(6f, -6f, 7.2f, -18f)
        p.oval(7.3f, -19f, 1.3f, 1.3f, wash = GOLD, a = 0.9f)
        head(p)
        beard(p, long = 2f)
        crown(p, -22f)
    }

    /** A crown on a head: its band at [v]. */
    fun crown(p: Pen, v: Float, wd: Float = 4.6f) {
        p.shape(f(-wd, v, -wd, v - 5f, -wd / 2, v - 2.6f, 0f, v - 6f, wd / 2, v - 2.6f, wd, v - 5f, wd, v), wash = GOLD, a = 0.85f)
        p.dot(0f, v - 1.2f, RED)
    }

    private fun girl(p: Pen, gold: Boolean) {
        val hair = if (gold) GOLD else WOOD
        p.shape(f(-6f, -21f, 6f, -21f, 6.5f, -9f, -6.5f, -9f), wash = hair, a = 0.65f)
        dress(p, RED, flare = 6.5f, hem = -1f)
        p.shape(f(-2.5f, -10f, 2.5f, -10f, 3.5f, -1.5f, -3.5f, -1.5f), fill = WHITE)
        p.line(-2f, -1f, -2f, 0f); p.line(2f, -1f, 2f, 0f)
        arms(p, lu = -5.5f, ru = 5.5f)
        head(p)
        fringe(p, hair)
    }

    private fun woman(p: Pen) {
        dress(p, BLUE, flare = 7f)
        p.shape(f(-2.8f, -11f, 2.8f, -11f, 3.8f, -1f, -3.8f, -1f), fill = WHITE)
        arms(p, lu = -5.5f, ru = 5.5f)
        head(p)
        // the headscarf, tied under the chin
        p.shape(p.ovalPoints(0f, -19.2f, 6.3f, 6.3f, 12, HALF, 2 * HALF), wash = RED, a = 0.7f, hatch = 3)
        p.shape(f(-1.2f, -13.6f, 1.2f, -13.6f, 0f, -11.4f), wash = RED, a = 0.8f)
    }

    private fun boy(p: Pen) {
        legs(p, -5f, EARTH)
        tunic(p, WHITE, a = 0.9f)
        p.shape(f(-5f, -6f, 5f, -6f, 5.5f, -4f, -5.5f, -4f), wash = WOOD, a = 0.7f)
        arms(p, lu = -6.5f, lv = -7f)
        // the shepherd's crook
        p.line(-6.5f, -7f, -7f, 0f, WOOD); p.line(-6.5f, -7f, -6.5f, -21f, INK)
        p.arc(-5f, -21f, 1.6f, 1.6f, HALF, 2 * HALF)
        head(p)
        // a cap
        p.shape(p.ovalPoints(0f, -19.5f, 5.8f, 5.6f, 10, HALF, 2 * HALF), wash = EARTH, a = 0.7f)
        p.line(2f, -20f, 7f, -20f)
    }

    private fun oldMan(p: Pen) {
        legs(p, -3f)
        p.shape(f(-4.5f, -13f, 4.5f, -13f, 5.5f, -2.5f, -5.5f, -2.5f), wash = STONE, a = 0.55f, hatch = 4)
        arms(p, ru = 6.5f, rv = -8f)
        p.line(6.5f, -9f, 7.5f, 0f, WOOD)
        p.line(6.5f, -9f, 7.5f, 0f)
        head(p)
        beard(p, long = 3f)
        hat(p, WOOD, brim = 7.5f)
    }

    private fun peasant(p: Pen) {
        legs(p, -5f, EARTH)
        tunic(p, WHITE, a = 0.9f, wide = 1f)
        p.shape(f(-5f, -13f, -2f, -13f, -2.5f, -4f, -6.5f, -4f), wash = RED, a = 0.6f)
        p.shape(f(5f, -13f, 2f, -13f, 2.5f, -4f, 6.5f, -4f), wash = RED, a = 0.6f)
        arms(p, lu = -7f, lv = -5f, ru = 7f, rv = -5f)
        head(p)
        p.line(-2f, -16.4f, 2f, -16.4f)
        hat(p, INK, brim = 7.5f, h = 3.5f, hatch = 2)
    }

    private fun hunter(p: Pen) {
        legs(p, -5f, STONE)
        // the rifle over the shoulder, behind him
        p.line(-6f, -2f, 7f, -27f)
        p.shape(f(-7f, -1f, -5f, -4f, -3.6f, -3f, -5.4f, 0f), wash = WOOD, a = 0.8f)
        tunic(p, PINE, a = 0.6f)
        p.line(-3.5f, -13f, 3f, -5f, WOOD)
        arms(p, lu = -5.5f, lv = -5f, ru = 5f, rv = -10f)
        head(p)
        p.line(-2f, -16.2f, 2f, -16.2f)
        hat(p, PINE)
        // the chamois tuft on his hat
        p.line(3.5f, -26f, 5f, -30f, SOFT); p.line(3.5f, -26f, 3.5f, -30.5f); p.line(3.5f, -26f, 6f, -29f)
    }

    private fun knight(p: Pen, gold: Boolean) {
        legs(p, -5f, STONE)
        tunic(p, STONE, a = 0.5f, hatch = 2)
        arms(p)
        // the sword, the shield
        p.line(6f, -6f, 9f, -20f, if (gold) GOLD else INK); p.line(6.8f, -6f, 9.8f, -20f, if (gold) GOLD else INK)
        p.line(4.5f, -8f, 8f, -7f)
        if (gold) for (i in 0 until 6) {
            val t = i * PI.toFloat() / 3
            p.line(9.5f + 3f * cos(t), -20f + 3f * sin(t), 9.5f + 5f * cos(t), -20f + 5f * sin(t), GOLD)
        }
        p.oval(-6.5f, -9f, 3.6f, 4.6f, wash = RED, a = 0.6f)
        p.line(-6.5f, -13f, -6.5f, -5f, WHITE); p.line(-9.5f, -9.5f, -3.5f, -9.5f, WHITE)
        // the helmet, its visor and plume
        head(p, fill = STONE, eyes = false)
        p.oval(0f, -19f, 5.5f, 5.5f, wash = STONE, a = 0.4f, hatch = 3)
        p.line(-3.5f, -18.5f, 3.5f, -18.5f)
        p.line(0f, -24.5f, 0f, -18.5f, SOFT)
        p.blob(f(0.5f, -1.5f, 2f), f(-26f, -27.5f, -28f), f(1.6f, 1.4f, 1.3f), RED, 0.7f)
    }

    private fun soldier(p: Pen) {
        legs(p, -5f)
        p.line(6f, -31f, 6f, 1f, WOOD); p.line(6.6f, -31f, 6.6f, 1f)
        p.shape(f(4.8f, -31f, 7.8f, -31f, 6.3f, -35f), fill = STONE)
        tunic(p, EARTH, a = 0.6f)
        p.line(-4.8f, -8f, 4.8f, -8f)
        arms(p, ru = 6f, rv = -10f)
        p.oval(-6f, -9f, 3f, 3.6f, wash = WOOD, a = 0.6f)
        p.dot(-6f, -9f)
        head(p)
        // a round helmet
        p.shape(p.ovalPoints(0f, -20f, 6f, 6f, 12, HALF, 2 * HALF), wash = STONE, a = 0.7f, hatch = 3)
        p.line(0f, -26f, 0f, -28f)
    }

    private fun giant(p: Pen) {
        legs(p, -5f, WOOD)
        tunic(p, WOOD, a = 0.5f, hatch = 3, wide = 1.5f)
        p.line(-5.5f, -8f, 5.5f, -8f)
        arms(p, lu = -7.5f, lv = -5f, ru = 7f, rv = -7f)
        // the club
        p.shape(f(6.4f, -6f, 7.6f, -6f, 10.4f, -18f, 8.4f, -18.5f), wash = WOOD, a = 0.7f)
        p.oval(9.8f, -20.5f, 2.6f, 3.4f, wash = WOOD, a = 0.7f, hatch = 2)
        // shaggy hair, a big beard
        p.shape(f(-6.5f, -18f, -6f, -24f, -2f, -26f, 3f, -25.5f, 6.5f, -22f, 6.5f, -16f, 5.5f, -18f, -5.5f, -18f), wash = WOOD, a = 0.7f, hatch = 2)
        head(p)
        beard(p, STONE, long = 2f)
        p.line(-3f, -21f, -1f, -20.5f); p.line(3f, -21f, 1f, -20.5f)
    }

    private fun fairy(p: Pen, gold: Boolean) {
        val up = -3f
        // a glow round her
        for (i in 0 until 14) {
            val t = i * 2 * PI.toFloat() / 14
            p.dot(11f * cos(t), -14f + up + 13f * sin(t), SOFT)
        }
        p.shape(f(-6f, -21f + up, 6f, -21f + up, 7f, -7f + up, -7f, -7f + up), wash = if (gold) GOLD else EARTH, a = 0.35f)
        p.shape(f(-4f, -13f + up, 4f, -13f + up, 8f, -1f + up, 3f, 0f + up, -3f, 0f + up, -8f, -1f + up), fill = WHITE)
        p.line(-2f, -8f + up, -3f, -1f + up, SOFT); p.line(2f, -8f + up, 3f, -1f + up, SOFT)
        p.line(-4f, -12f + up, -8f, -16f + up); p.line(4f, -12f + up, 8f, -16f + up)
        head(p, -19f + up)
        fringe(p, if (gold) GOLD else EARTH, -19f + up)
        // a star in her hair
        p.line(-3f, -26.5f + up, -3f, -23.5f + up, GOLD); p.line(-4.5f, -25f + up, -1.5f, -25f + up, GOLD)
    }

    private fun waterMan(p: Pen) {
        // the water he rises from
        for (i in -2..2) p.arc(i * 4f, 0.5f, 2f, 1.2f, HALF, 2 * HALF, WATER)
        p.shape(f(-6.5f, -21f, 6.5f, -21f, 7f, -8f, 4f, -10f, -4f, -10f, -7f, -8f), wash = PINE, a = 0.6f)
        p.shape(f(-4f, -13f, 4f, -13f, 6f, -1f, -6f, -1f), wash = LEAF, a = 0.65f, hatch = 4)
        arms(p, lu = -7f, lv = -9f, ru = 7f, rv = -9f)
        head(p)
        fringe(p, PINE)
        // weeds and drops off his hair
        p.line(-6.5f, -20f, -8f, -12f, PINE); p.line(6.5f, -20f, 8f, -13f, PINE)
        p.dot(-8.5f, -9f, WATER); p.dot(8.5f, -10f, WATER)
        hat(p, LEAF, brim = 6.5f, h = 3f)
    }

    private fun devil(p: Pen) {
        // the tail, its arrow
        p.lines(-4f, -5f, -9f, -6f, -11f, -10f, -10f, -14f)
        p.shape(f(-10f, -14f, -12f, -16.5f, -8.2f, -15.6f), fill = INK)
        legs(p, -5f, INK)
        tunic(p, RED, a = 0.6f, hatch = 2)
        arms(p, lu = -6.5f, lv = -8f, ru = 6.5f, rv = -8f)
        // the pitchfork
        p.line(7f, -4f, 7f, -26f)
        p.line(5f, -26f, 9f, -26f); p.line(5f, -26f, 5f, -29f); p.line(7f, -26f, 7f, -29.5f); p.line(9f, -26f, 9f, -29f)
        head(p, fill = PAPER)
        p.oval(0f, -19f, 5.5f, 5.5f, wash = RED, a = 0.45f)
        p.dot(-1.9f, -18.4f); p.dot(1.9f, -18.4f)
        p.lines(-2f, -16f, 0f, -15f, 2f, -16f)
        // horns
        p.lines(-3f, -23.5f, -5f, -26f, -4f, -28f)
        p.lines(3f, -23.5f, 5f, -26f, 4f, -28f)
    }

    private fun goblin(p: Pen) {
        legs(p, -5f)
        tunic(p, LEAF, a = 0.6f)
        arms(p, lu = -6.5f, lv = -9f, ru = 6.5f, rv = -9f)
        // the big ears
        p.shape(f(-5f, -21f, -10f, -23f, -5.2f, -17f), fill = PAPER)
        p.shape(f(5f, -21f, 10f, -23f, 5.2f, -17f), fill = PAPER)
        head(p)
        p.lines(-2.5f, -16.5f, 0f, -15.5f, 2.5f, -16.5f)
        // the pointed red cap
        p.shape(f(-5.6f, -21.5f, 5.6f, -21.5f, 2f, -30f, 5f, -33f, -0.5f, -29f), wash = RED, a = 0.75f, hatch = 3)
    }

    // --- creatures and animals (they face right) --------------------------------------------------------------------

    private fun dragon(p: Pen, gold: Boolean) {
        val scale = if (gold) GOLD else LEAF
        // the tail, curling back, its arrow
        p.shape(f(-7f, -13f, -14f, -9f, -20f, -6f, -25f, -8f, -24f, -6f, -19f, -3.5f, -13f, -6f, -7f, -8f), wash = scale, a = 0.55f)
        p.shape(f(-25f, -8f, -28f, -11f, -26.5f, -5f), fill = INK)
        // the wing behind
        p.shape(f(-2f, -15f, -12f, -34f, -8f, -28f, -4f, -32f, -1f, -26f, 3f, -29f, 4f, -16f), wash = scale, a = 0.35f, hatch = 3)
        p.line(-2f, -15f, -12f, -34f); p.line(0f, -16f, -4f, -32f); p.line(2f, -16f, 3f, -29f)
        // legs, body, the spikes on its back
        p.shape(f(-6f, -8f, -4f, -8f, -5f, 0f, -7.5f, 0f), wash = scale, a = 0.6f)
        p.shape(f(4f, -8f, 6f, -8f, 7f, 0f, 4.5f, 0f), wash = scale, a = 0.6f)
        p.line(-8f, 0f, -4.5f, 0f); p.line(4f, 0f, 8f, 0f)
        p.oval(0f, -11f, 10f, 5.5f, wash = scale, a = 0.6f)
        p.shape(p.ovalPoints(0f, -11f, 9f, 4.5f, 10, 0f, HALF), wash = PAPER, a = 0.6f, edge = null)
        for (u in listOf(-6f, -2f, 2f, 6f)) p.shape(f(u - 1.5f, -15.8f, u, -19f, u + 1.5f, -16f), fill = INK)
        // the neck up to the head, its open jaw, a horn, the eye
        p.shape(f(6f, -14f, 10f, -22f, 13f, -22f, 10f, -10f), wash = scale, a = 0.6f)
        p.shape(f(10f, -22f, 13f, -26f, 19f, -25f, 20f, -23f, 15f, -22.5f, 19f, -21f, 17f, -19.5f, 12f, -19f), wash = scale, a = 0.65f)
        p.line(13f, -26f, 11f, -29f); p.line(14f, -26f, 13.5f, -29.5f)
        p.dot(15f, -24f)
        p.lines(20f, -22f, 23f, -23f, 22f, -20.5f, 25f, -21f, col = InkPal.FIRE)
    }

    private fun chamois(p: Pen, gold: Boolean) {
        val coat = if (gold) WHITE else EARTH
        p.line(-5f, -7f, -5.6f, 0f); p.line(-3f, -7f, -2.4f, 0f); p.line(4f, -7f, 3.6f, 0f); p.line(6f, -7f, 6.6f, 0f)
        p.oval(0f, -10f, 7.2f, 4f, fill = if (gold) WHITE else null, wash = if (gold) null else coat, a = 0.6f)
        if (!gold) p.shape(p.ovalPoints(0f, -10f, 6.5f, 3.4f, 8, 0f, HALF), hatch = 3, edge = null)
        p.line(-7f, -11.5f, -8.5f, -12.5f)
        p.shape(f(4f, -12.5f, 6.5f, -16f, 8.5f, -15.5f, 7.8f, -10f), fill = if (gold) WHITE else null, wash = if (gold) null else coat, a = 0.6f)
        p.oval(9.3f, -16.8f, 2.6f, 1.9f, fill = if (gold) WHITE else null, wash = if (gold) null else coat, a = 0.6f)
        p.line(10.5f, -16f, 11.8f, -16.4f)
        p.dot(9.6f, -17.2f)
        if (!gold) p.line(8.4f, -18.3f, 10.8f, -15.4f, SOFT)
        p.line(7.4f, -18f, 6.2f, -19.5f)
        // the hooked horns
        val horn = if (gold) GOLD else INK
        p.lines(8.4f, -18.5f, 8.2f, -23f, 9.6f, -23.2f, 10.2f, -22f, col = horn)
        p.lines(9.4f, -18.5f, 9.4f, -22.6f, 10.8f, -22.8f, col = horn)
        if (gold) {
            p.lines(8.9f, -18.5f, 8.8f, -23f, col = GOLD)
            for (i in 0 until 5) {
                val t = -HALF / 2 - i * HALF / 5
                p.line(9f + 4.2f * cos(t), -21f + 4.2f * sin(t), 9f + 6f * cos(t), -21f + 6f * sin(t), GOLD)
            }
        }
    }

    private fun horse(p: Pen) {
        for (u in listOf(-7f, -4.5f, 5f, 7.5f)) {
            p.line(u, -10f, u - 0.3f, 0f); p.line(u + 1.2f, -10f, u + 1f, 0f)
            p.line(u - 0.8f, 0f, u + 1.6f, 0f)
        }
        p.lines(-10f, -15f, -12.5f, -12f, -13f, -5f)
        p.lines(-10f, -14f, -11.5f, -10f, -11.2f, -5f)
        p.oval(0f, -13f, 10.5f, 5f, wash = WOOD, a = 0.55f)
        p.shape(p.ovalPoints(0f, -13f, 9.5f, 4f, 8, 0f, HALF), hatch = 3, edge = null)
        p.shape(f(7f, -15f, 9.5f, -24f, 12.5f, -24f, 11f, -13f), wash = WOOD, a = 0.55f)
        p.shape(f(10f, -25f, 16f, -22.5f, 17.5f, -20.5f, 15f, -19.5f, 11.5f, -21.5f), wash = WOOD, a = 0.55f)
        p.dot(12.6f, -22.6f)
        p.line(11f, -25f, 11.5f, -27.5f)
        // the mane
        for (i in 0 until 5) p.line(9.5f - i * 0.3f, -24f + i * 2f, 7.8f - i * 0.3f, -22.5f + i * 2f)
    }

    private fun ox(p: Pen) {
        for (u in listOf(-8f, -5f, 5f, 8f)) {
            p.shape(f(u - 1f, -8f, u + 1f, -8f, u + 1f, 0f, u - 1f, 0f), wash = EARTH, a = 0.6f)
        }
        p.lines(-10f, -14f, -12f, -10f, -12f, -5f)
        p.shape(f(-12.8f, -5f, -11.2f, -5f, -12f, -3f), fill = INK)
        p.oval(-0.5f, -12f, 10.5f, 5.5f, wash = EARTH, a = 0.6f)
        p.shape(f(3f, -17f, 7f, -19f, 9.5f, -16f), wash = EARTH, a = 0.6f)
        p.shape(p.ovalPoints(-0.5f, -12f, 9.5f, 4.5f, 8, 0f, HALF), hatch = 3, edge = null)
        p.shape(f(8.5f, -17f, 14f, -15.5f, 15f, -10f, 11f, -9f), wash = EARTH, a = 0.65f)
        p.dot(12f, -14f)
        p.lines(10.5f, -16.8f, 9f, -19.5f, 10f, -22f)
        p.lines(13f, -16.2f, 15.5f, -18.5f, 15f, -21f)
    }

    private fun boar(p: Pen) {
        for (u in listOf(-5f, -2.5f, 3f, 5.5f)) p.line(u, -4f, u, 0f)
        p.lines(-8.5f, -8f, -10f, -9.5f, -9f, -10.5f)
        p.oval(0f, -7f, 8.5f, 5f, wash = WOOD, a = 0.65f, hatch = 2)
        for (i in 0 until 7) p.line(-6f + i * 2f, -11.5f - (i % 2), -5.4f + i * 2f, -13.5f - (i % 2))
        p.shape(f(6f, -10f, 11.5f, -7.5f, 11.5f, -5f, 7f, -3f), wash = WOOD, a = 0.65f)
        p.line(11.5f, -7.5f, 11.5f, -5f)
        p.lines(9.5f, -5f, 10.5f, -7f, 9.8f, -8.5f, col = WHITE)
        p.dot(8f, -8.5f)
        p.shape(f(6f, -10.5f, 7f, -13f, 8f, -10f), wash = WOOD, a = 0.7f)
    }

    private fun dog(p: Pen) {
        for (u in listOf(-3.5f, -2f, 2.5f, 4f)) p.line(u, -4f, u, 0f)
        p.lines(-4.5f, -7f, -7f, -10f, -6.5f, -11.5f)
        p.oval(0f, -6f, 5f, 2.8f, wash = EARTH, a = 0.6f)
        p.oval(5.6f, -9f, 2.6f, 2.3f, wash = EARTH, a = 0.6f)
        p.shape(f(7.5f, -9.5f, 9.5f, -8.8f, 7.8f, -7.6f), wash = EARTH, a = 0.6f)
        p.dot(9.4f, -9f)
        p.dot(6f, -9.8f)
        p.shape(f(4.2f, -10.5f, 3.4f, -7f, 5f, -8.5f), wash = WOOD, a = 0.8f)
    }

    private fun cat(p: Pen) {
        for (u in listOf(-3f, -1.8f, 2.2f, 3.4f)) p.line(u, -3.5f, u, 0f)
        p.lines(-4.2f, -5.5f, -6.8f, -8.5f, -6.4f, -12f, -5f, -12.5f)
        p.oval(0f, -5.2f, 4.6f, 2.5f, wash = STONE, a = 0.6f, hatch = 3)
        p.oval(5f, -8f, 2.4f, 2.2f, wash = STONE, a = 0.6f)
        p.shape(f(3.4f, -9.4f, 3.6f, -12f, 5f, -10f), wash = STONE, a = 0.7f)
        p.shape(f(5.4f, -10f, 6.8f, -12f, 7f, -9.4f), wash = STONE, a = 0.7f)
        p.dot(5.8f, -8.4f)
        p.line(7.2f, -7.4f, 9.4f, -7.8f, SOFT); p.line(7.2f, -7f, 9.4f, -6.4f, SOFT)
    }

    private fun bird(p: Pen) {
        p.line(-1f, -2f, -1f, 0f); p.line(1f, -2f, 1f, 0f)
        p.shape(f(-3f, -5f, -9f, -3f, -8.5f, -1.5f, -3f, -3f), wash = STONE, a = 0.6f)
        p.oval(0f, -4f, 4f, 2.5f, wash = STONE, a = 0.55f)
        p.shape(p.ovalPoints(0f, -4f, 3.4f, 2f, 8, 0f, HALF), hatch = 2, edge = null)
        p.oval(3.5f, -6.3f, 1.9f, 1.7f, wash = STONE, a = 0.55f)
        p.shape(f(5.2f, -6.8f, 7f, -6.2f, 5.2f, -5.7f), fill = GOLD)
        p.dot(3.9f, -6.8f)
        p.lines(-2.5f, -5f, 1f, -5.6f, 2f, -4f)
    }

    private fun boat(p: Pen) {
        for (i in -3..3) p.arc(i * 4f, 0.8f, 2f, 1f, HALF, 2 * HALF, WATER)
        p.line(0f, -4f, 0f, -24f)
        p.shape(f(0.6f, -23f, 0.6f, -6f, 10f, -7f), wash = RED, a = 0.55f, hatch = 4)
        p.oval(4f, -15f, 1.6f, 1.6f, wash = GOLD, a = 0.8f)
        p.shape(f(-12f, -5f, 12f, -5f, 8.5f, 0f, -8.5f, 0f), wash = WOOD, a = 0.6f)
        p.line(-10.5f, -3f, 10.5f, -3f, SOFT)
        p.shape(f(0f, -24f, 3f, -23f, 0f, -22f), fill = RED)
    }

    private fun sheep(p: Pen) {
        for (u in listOf(-4f, -2f, 2.5f, 4.5f)) p.line(u, -4f, u, 0f)
        p.blob(f(-4f, -1f, 2f, 4f, 0f), f(-7f, -8.5f, -8f, -6.5f, -5.5f), f(3f, 3.2f, 3f, 2.8f, 3f), WHITE, 1f, INK, 3, shadeBelow = -5.5f)
        p.oval(6.6f, -8f, 2.2f, 1.7f, fill = INK)
        p.line(5.6f, -9.5f, 4.6f, -10.5f)
        p.dot(7.4f, -8.4f, WHITE)
    }
}

/** The pen, [s] times bigger (a giant), standing where it stood. */
internal fun Pen.scaled(s: Float): Pen = Pen(ink, ax, ay, k * s, flip)
