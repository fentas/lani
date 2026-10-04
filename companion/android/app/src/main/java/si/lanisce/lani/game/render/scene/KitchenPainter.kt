package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Poke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V kuhinji: a farmhouse kitchen as a diorama, a room on its own plot of grass with its two front walls cut down
 * to a stub so we look in. Whitewashed walls with a painted dado and a stencilled border; on the right wall the
 * green tiled stove in the corner with a pot simmering and embers in the firebox, the bench from its warm side along
 * under the window (a cushion, a folded blanket), gingham curtains and the weather outside, the plank door with a
 * horseshoe over it; on the left wall the clock with its pendulum and weights and the painted cupboard with plates on
 * its shelves and the milk and water jugs on its counter. A
 * terracotta floor with a rag rug; the table on a checked cloth laid with bread, a plate, a bowl of soup, a glass,
 * a cup, spoon, fork, knife and salt, two chairs with hearts in their backs, a basket of eggs, a sack of potatoes.
 * The petroleum lamp hangs over the table on its chain, lit from dusk. Outside: a bench by the wall with the cat
 * on it, geraniums in pots, the vegetable bed and a hen.
 *
 * Closer up ([detail] 2 and 3) the small things are drawn finer, not just bigger: round jugs, pots, cups and the glass
 * with a glaze's glint, the bread scored and floured, the soup with noodles, cutlery with tines, chains of links, the
 * clock's pine-cone weights and hour marks, wicker, burlap, geranium umbels, the cat's stripes and whiskers, the hen's
 * feathers; nail heads and grain on the door, glazing bars, stitched hems, relief on the stove's tiles.
 *
 * Tapped ([pokes]), the cat gets up and stretches with a yawn, then purrs with its eyes shut and hearts rising, and the
 * third time dashes off round the back of the house, to come trotting back twenty seconds later; the pot's lid
 * rattles and lets out a puff of steam, and hops when it boils over.
 *
 * The bench by the stove is where the grandmother sleeps, her head by its warm tiles, as by the krušna peč ([beds]:
 * "zapecek", from "za pečjo"): at night she lies there on the cushion under the red wool blanket, the lamp over the
 * table out, the stove's embers glowing further. Her sister Ančka sleeps on a straw mattress rolled out on the floor
 * before the door ("mattress"), her head at the door's end, under the coarse brown blanket; Zala on a small one
 * ("cot", a child's) in the front corner by the left wall, under a red wool blanket, at level 1 in the middle of the
 * floor. The mattresses are there only while someone sleeps on them.
 *
 * The kitchen grows with its building's level ([SceneWorld.here], see [SceneFixtures]): at level 1 it is bare, a beaten
 * earth floor, plain whitewash blackened over the open hearth in the corner (the pot on its trivet over the fire), a plain
 * bench under the window, the straw bed along the left wall and a shelf with the jugs; at level 2 the tiled stove and its
 * pipe take the hearth's place, the bench gets its cushion, the tiles, the rag rug, the painted dado, the table laid with
 * its chairs and the lamp over it, curtains and a geranium, pots of them outside; at level 3 the stencilled border, the
 * clock, the painted dresser instead of the shelf and the holy corner (bohkov kot) with its crucifix high in the front
 * corner. Asleep at level 1, the grandmother lies in the straw bed.
 */
internal class KitchenPainter : RoomPainter() {
    override val art = "kitchen"
    override val pokes = listOf(Poke("cat", listOf(1.8, 2.8, 22.0), rest = 6.0), Poke("pot", listOf(1.3, 1.9), rest = 3.0))
    override val beds = listOf(BED, MATTRESS, COT)
    override val childBeds = setOf(COT)

    override fun paint() {
        fit()
        vignette()
        lv = frame.world.hereLevel.coerceIn(1, 3)
        val asleep = peopleAt(BED).firstOrNull()
        val quiet = asleepHere
        val stove = there("stove")
        // the lamp over the table burns from dusk, and is put out for anyone asleep
        val lit = env.windows > 0.35f && !quiet && there("table")
        plot { x, y, px, py -> ground(x, y, px, py) }
        // tufts first: they lie flat, and whatever stands in front of them covers them
        tufts(80, 13) { x, y ->
            (x < x1 + tk + 0.3f && y < y1 + tk + 0.3f) || (x in 1.3f..6.5f && y in 9.7f..11.6f) ||
                (x in 9.6f..11.6f && y in 2f..11.4f) || hypot(x - henX, y - henY) < 0.9f
        }
        prop { path() }
        // the house's shade on the grass along its front
        for (k in 0..7) {
            val a = k / 7f
            shade(iso.sx(x1 + tk + 0.25f, y0 + a * (y1 - y0)), iso.sy(x1 + tk + 0.25f, y0 + a * (y1 - y0), 0f), 9f * K, 1.4f * K, 0.86f, groundId)
            shade(iso.sx(x0 + a * (x1 - x0), y1 + tk + 0.25f), iso.sy(x0 + a * (x1 - x0), y1 + tk + 0.25f, 0f), 9f * K, 1.4f * K, 0.86f, groundId)
        }

        floor()
        if (lv >= 2) rug() else rugId = floorId
        catAround(behind = true)
        walls()
        thing("window") { window() }
        if (there("curtains")) thing("curtains") { curtains() }
        thing("door") { door() }
        if (there("clock")) thing("clock", slop = 1) { clock() }
        if (there("corner")) thing("corner", slop = 1) { corner() }
        if (stove) {
            prop { pipe() }
            thing("stove") { stove() }
            thing("pot", slop = 1) { pot(2.7f, 1.8f, stoveZ + 1f) }
        } else {
            // level 1: the open hearth, the fire on it and the pot on its trivet over the fire
            thing("hearth") { hearth() }
            hearthFire()
            thing("pot", slop = 1) { trivet(); pot(fireX, fireY, trivetZ) }
        }
        // the bench under the window: a plain plank at first, the stove's warm bench with its cushion from level 2
        thing("bench") { bench(asleep == null, dressed = stove) }
        if (there("bed")) thing("bed") { strawBed(asleep == null) }
        // asleep on the bench by the stove, the head on the cushion, under the red wool blanket; before the stove, in the
        // straw bed under a coarse one
        asleep?.let {
            if (there("bed")) sleeperAt(it, x0 + 0.78f, bedY0 + 0.42f, bedZ + 1.9f, x0 + 0.78f, bedY1 - 0.3f, bedZ + 1.5f, COARSE)
            else sleeperAt(it, benchA + 0.36f, 1.45f, fl + 6.9f, benchB - 0.2f, 1.45f, fl + 6f)
        }
        if (there("cupboard")) thing("cupboard") { cupboard() } else if (there("shelf")) thing("shelf") { shelf() }
        thing("milk", slop = 1) { milk(1.86f, 4.95f, counterZ) }
        thing("water", slop = 1) { water(1.86f, 6.2f, counterZ) }
        for (p in peopleAt("door")) { footShadow(7.85f, 1.95f); personAt(p, 7.85f, 1.95f, fl, flip = true) }
        for (p in peopleAt("stove")) { footShadow(4.35f, 1.95f); personAt(p, 4.35f, 1.95f, fl, flip = true) }
        // Teta Ančka's straw mattress rolled out on the floor before the door, her head at the door's end, under the coarse
        // brown blanket
        peopleAt(MATTRESS).firstOrNull()?.let { p ->
            prop { mattress(MAT_A, MAT_C, MAT_B, MAT_D, alongY = false, far = true) }
            sleeperOn(p, MAT_A, MAT_C, MAT_B, MAT_D, fl + MAT_H, alongY = false, COARSE, far = true)
        }
        // Zala's small mattress: at level 1, before there is a table, in the middle of the floor by Micka's straw bed
        val zala = peopleAt(COT).firstOrNull()
        val bare = there("bed")
        if (bare) zala?.let { smallMattress(it, COT1_A, COT1_C, COT1_B, COT1_D) }

        // the chairs behind the table, the table and what is on it: each small thing's middle lies in no later
        // thing's hit, so a tap on it finds it
        if (there("table")) {
            thing("chair") { chair(3.8f, 4.45f); chair(5.2f, 4.45f) }
            thing("table") { table() }
            val top = tz + 0.15f
            thing("bread", slop = 1) { bread(3.46f, 5.73f, top) }
            thing("bowl", slop = 1) { bowl(5.39f, 5.61f, top) }
            thing("cup", slop = 2) { cup(4.99f, 6.57f, top) }
            thing("glass", slop = 2) { glass(6.62f, 5.61f, top) }
            thing("salt", slop = 2) { salt(3.43f, 7.02f, top) }
            thing("fork", slop = 1) { fork(4.27f, 6.93f, top) }
            thing("plate", slop = 1) { plate(5.25f, 7.11f, top) }
            thing("knife", slop = 1) { knife(6.45f, 7.05f, top) }
            thing("spoon", slop = 1) { spoon(6.04f, 5.98f, top) }
            prop { lamp(lit) }
        }
        for (p in peopleAt("table")) { footShadow(2.5f, 8.2f); personAt(p, 2.5f, 8.2f, fl) }
        // from level 2 in the front corner by the left wall, in front of the table
        if (!bare) zala?.let { smallMattress(it, COT_A, COT_C, COT_B, COT_D) }
        thing("potatoes") { sack(8.3f, 7.8f) }
        thing("eggs", slop = 1) { basket(7.25f, 8.15f) }

        // the front walls, cut down, and the garden in front of them
        stubs()
        prop { gardenBench() }
        catAround(behind = false)
        if (lv >= 2) prop { pots() }
        prop { vegetables() }
        if (env.dark < 0.5f) prop { hen(henX, henY) }
        fireflies(iso.ix(0f, 12f), iso.iy(12f, 12f, 20f), iso.ix(12f, 0f), iso.iy(12f, 12f, 0f), 12, 5)

        // light: the lamp from dusk, the embers always (the stove fired up: brighter, further), the sun through the window by day
        val oven = fxOn("oven")
        if (lit) s.light(iso.sx(lampX, lampY), iso.sy(lampX, lampY, fl + 16f), 50f, 1.05f * env.windows)
        // the embers, further and warmer while the lamp is out for a sleeper, glowing on her bench
        val embers = if (quiet) 1f else 0f
        if (stove) s.light(iso.sx(2.85f, stoveY), iso.sy(2.85f, stoveY, fl + 3.5f), 18f + 14f * embers + 24f * oven, 0.7f + 0.25f * embers + 0.5f * oven)
        // the hearth's fire lights the bare room, all the light there is at night
        else s.light(iso.sx(fireX, fireY), iso.sy(fireX, fireY, hz + 3f), 34f + 10f * embers + 22f * oven, 0.95f + 0.2f * embers + 0.4f * oven)
        // the stove fired up: smoke from the chimney over the corner
        if (stove && oven > 0.05f) {
            val sx = iso.sx(2.1f, y0 - tk / 2f); val sy = iso.sy(2.1f, y0 - tk / 2f, hw + 1f)
            s.smoke(sx, sy, 0.5f + 0.7f * oven, 31)
            s.smoke(sx + 2f * P, sy - 2f * P, 0.3f + 0.6f * oven, 32)
        }
        sunPatch()
    }

    /** Zala asleep on her small straw mattress over x [xa]..[xb], y [ya]..[yb], her head at its [xa] end, under the red wool blanket. */
    private fun smallMattress(p: PersonInScene, xa: Float, ya: Float, xb: Float, yb: Float) {
        prop { mattress(xa, ya, xb, yb, alongY = false) }
        sleeperOn(p, xa, ya, xb, yb, fl + MAT_H, alongY = false, null)
    }

    private companion object {
        /**
         * Ančka's straw mattress on the floor before the door; Zala's small one in the front corner by the left wall, at
         * level 1 in the middle of the floor: all along x.
         */
        const val MAT_A = 5.5f; const val MAT_B = 8.5f; const val MAT_C = 2.75f; const val MAT_D = 3.75f
        const val COT_A = 1.12f; const val COT_B = 3.0f; const val COT_C = 7.6f; const val COT_D = 8.5f
        const val COT1_A = 3.4f; const val COT1_B = 5.3f; const val COT1_C = 5.3f; const val COT1_D = 6.2f
    }
}
