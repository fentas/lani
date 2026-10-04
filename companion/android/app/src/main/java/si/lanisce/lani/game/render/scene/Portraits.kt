package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PortraitPainter
import si.lanisce.lani.game.scene.Pose
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The portraits: the very sprite of the scenes ([PeoplePainter]), cut at the waist and shown big, pixel for
 * pixel, on a soft backdrop lit by the hour: warm by day, lamplight at night. So a villager looks the same in a
 * scene, in the intro and on the training stage, with the same poses: breathing and blinking, the mouth moving
 * when talking, both arms up at a cheer, a shrug when sad, a hand at the chin, a hand at the ear, a wave.
 *
 * The sprite is about 30 px from the hat to the waist: a canvas of about 32 px shows it at its own size (see
 * portraitPx), bigger canvases a whole number of times bigger.
 */
internal class PortraitsPainter : PortraitPainter {
    private val people = PeoplePainter()
    private val sprite = PixelCanvas(SPRITE_W, SPRITE_H)
    private val edge = BooleanArray(SPRITE_W * SPRITE_H)

    override fun render(canvas: PixelCanvas, art: String, pose: Pose, time: Double, hour: Float, seed: Int) {
        val w = canvas.width; val h = canvas.height
        canvas.reset(0)
        backdrop(canvas, Env(hour, 6, false))

        // the sprite at its own size, then its outline
        val sp = sprite
        sp.reset(0)
        sp.penId = 1
        val bx = SPRITE_W / 2; val by = SPRITE_H - 2
        // a bust: asleep, they doze where they stand
        people.draw(sp, art, bx, by, time, seed, talking = false, flip = false, walking = false, seated = false, pose = pose, upright = true)
        sp.penId = 0
        outline(sp)

        // framing: the waist at the bottom edge (a baby: its basket), but never the hat cut off
        val L = Looks.of(art, seed)
        // a whole number of times bigger; a canvas nearly big enough for the next step takes it (the waist may go)
        val s = max(1, (min(w, h) + 8) / 30)
        val cut = if (art == "baby") by + 1 else by - L.legH + 1
        val headTop = by - (if (art == "baby") 24 else L.height) - when {
            L.pack == Looks.PACK_KROSNJA -> 11 // the pedlar's load stands higher than his hat
            L.hat == Looks.HAT_FELT -> 5; L.hat == Looks.HAT_STRAW || L.hat == Looks.HAT_VEIL -> 4
            else -> if (L.hairStyle == Looks.HAIR_BUN) 5 else 2
        }
        val dx = w / 2 - bx * s - s / 2
        val dy = max(h - cut * s, 1 - headTop * s)
        val ids = sp.ids; val px = sp.pixels
        for (y in 0 until SPRITE_H) {
            val cy = y * s + dy
            if (cy >= h || cy + s <= 0) continue
            for (x in 0 until SPRITE_W) {
                val i = y * SPRITE_W + x
                if (ids[i] == 0 && !edge[i]) continue
                canvas.block(x * s + dx, cy, s, px[i])
            }
        }
    }

    /** A 1-px dark outline round the sprite, in the sprite's own pixels (so it scales with it). */
    private fun outline(sp: PixelCanvas) {
        val ids = sp.ids; val px = sp.pixels
        edge.fill(false)
        for (y in 0 until SPRITE_H) for (x in 0 until SPRITE_W) {
            val i = y * SPRITE_W + x
            if (ids[i] != 0) continue
            val near = (x > 0 && ids[i - 1] != 0) || (x < SPRITE_W - 1 && ids[i + 1] != 0) ||
                (y > 0 && ids[i - SPRITE_W] != 0) || (y < SPRITE_H - 1 && ids[i + SPRITE_W] != 0)
            if (near) edge[i] = true
        }
        for (i in edge.indices) if (edge[i]) px[i] = Pal.OUTLINE
    }

    /** A soft vignette: warm cream by day, rosy at dusk and dawn, a lamp's pool of light in a dark room at night. */
    private fun backdrop(c: PixelCanvas, env: Env) {
        val w = c.width; val h = c.height
        val S = min(w, h) / 64f
        val ox = (w - 64 * S) / 2; val oy = h - 64 * S
        val dark = env.dark
        val dusk = (1f - abs(env.sun) / 0.3f).coerceIn(0f, 1f)
        val dayC = Col.hex(0xF6E8C8); val dayE = Col.hex(0xC9B088)
        val nightC = Col.hex(0x7A5A3A); val nightE = Col.hex(0x2A2640)
        val centre = Col.mix(Col.mix(dayC, Col.hex(0xFFC890), dusk * 0.5f), nightC, dark)
        val edgeCol = Col.mix(Col.mix(dayE, Col.hex(0xB07060), dusk * 0.5f), nightE, dark)
        val cx = ox + 32 * S + (if (dark > 0.5f) 10 * S else 0f); val cy = oy + (if (dark > 0.5f) 14 * S else 30 * S)
        val rad = 40 * S
        for (y in 0 until h) for (x in 0 until w) {
            val ddx = (x + 0.5f - cx) / rad; val ddy = (y + 0.5f - cy) / rad
            val d = sqrt(ddx * ddx + ddy * ddy).coerceIn(0f, 1f)
            val f = (d * d + Dither.at(x, y) * 0.12f).coerceIn(0f, 1f)
            c.set(x, y, Col.mix(centre, edgeCol, f))
        }
        // a faint wainscot line, so it reads as a room
        val yy = (oy + 50 * S).toInt()
        for (x in 0 until w) if (Dither.at(x, yy) < 0.5f) c.blend(x, yy, edgeCol, 0.5f)
    }

    private companion object {
        const val SPRITE_W = 48
        const val SPRITE_H = 60
    }
}
