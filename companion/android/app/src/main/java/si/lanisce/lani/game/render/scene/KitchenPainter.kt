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
 * pipe take the hearth's place, the bench gets its cushion, the tiles, the painted dado, the table laid with its chairs
 * and the lamp over it, curtains and a geranium; at level 3 the clock, the painted dresser instead of the shelf and the
 * holy corner (bohkov kot) with its crucifix high in the front corner; at level 4 the stencilled border and the rag rug;
 * at level 5 the pots of geraniums outside. Asleep at level 1, the grandmother lies in the straw bed.
 *
 * In Friuli ([look]: the kitchen's "hearth" is "fogolar"; companion/SCENES.md "Culture looks") it is Nonna Rosa's
 * Friulian kitchen: the fogolâr in the corner at every level, a knee-high hearth of grey stone with the fire on its top
 * between two andirons, and the copper cauldron for the polenta (the cjaldîr, the "pot") hanging over the fire on its
 * chain, a wooden stick in it, polenta in it in the evening; at level 1 the chain hangs from a pole across the black
 * corner, logs stacked beside the hearth and the round polenta board (taulîr) on the wall. From level 2 the fogolâr has
 * its white hood over it with a shelf round its foot (copper pans, the polenta board, a plate, a lace edging), a bench
 * along the left wall as well as under the window, and the spolert beside it, the iron range with its cream enamel, its
 * oven, its fire door glowing and a coffee pot on its plate, its pipe up into the hood (the "stove", il fornello). Zia
 * Nives's radio stands on a shelf in the front corner; from level 3 that corner (l'angolo) has a lace edging, a vase and
 * the family's photo, no holy corner. The dresser is walnut with plain panels, the dado ochre, the border a frieze of
 * vines and grapes, and the window looks out on the Collio's rows of vines and a cypress. From level 2 the oil lamp (lum)
 * hangs from the hood's shelf and lights the table from dusk, instead of the petroleum lamp over the table, which would
 * hide the fogolâr. The hearth is a thing of the kitchen at every level there ([CultureLook.fixes]); the stove, the pot,
 * the bench and the people keep their places.
 */
internal class KitchenPainter : RoomPainter() {
    override val art = "kitchen"
    override val pokes = listOf(Poke("cat", listOf(1.8, 2.8, 22.0), rest = 6.0), Poke("pot", listOf(1.3, 1.9), rest = 3.0))
    override val beds = listOf(BED, MATTRESS, COT)
    override val childBeds = setOf(COT)

    /** Friuli's kitchen ([look]: "hearth" "fogolar"): the fogolâr, the spolert, the cauldron on its chain, the radio. */
    private var friuli = false

    override fun paint() {
        fit()
        vignette()
        lv = frame.world.hereLevel.coerceIn(1, 5)
        readLook()
        friuli = variant("hearth") == "fogolar"
        val asleep = peopleAt(BED).firstOrNull()
        val quiet = asleepHere
        val stove = there("stove")
        // the lamp over the table burns from dusk, and is put out for anyone asleep
        val lit = env.windows > 0.35f && !quiet && there("table")
        // the grass under the floor isn't shaded: the floor is drawn over all of it below, opaque, nothing in between reads it
        under = flatSpans(fl, x0, y0, x1, y1)
        plot { x, y, px, py -> ground(x, y, px, py) }
        under = null
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
        // the rag rug before the stove comes with the fourth level, with the stencilled border
        if (lv >= BORDER_LEVEL) rug() else rugId = floorId
        catAround(behind = true)
        walls()
        thing("window") { window() }
        if (there("curtains")) thing("curtains") { curtains() }
        thing("door") { door() }
        if (there("clock")) thing("clock", slop = 1) { clock() }
        if (friuli) {
            // Zia Nives's radio on its shelf in the front corner (from level 3 the corner itself); the fogolâr in the back
            // corner and all round it; the benches round it: under the window beside it, and along the left wall from level 2
            radioCorner()
            fogolarCorner(stove)
            thing("bench") { bench(asleep == null, dressed = stove, benchA = FB_A); if (stove) leftBench() }
        } else {
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
        }
        if (there("bed")) thing("bed") { strawBed(asleep == null) }
        // asleep on the bench by the stove, the head on the cushion, under the red wool blanket; before the stove, in the
        // straw bed under a coarse one
        asleep?.let {
            if (there("bed")) sleeperAt(it, x0 + 0.78f, bedY0 + 0.42f, bedZ + 1.9f, x0 + 0.78f, bedY1 - 0.3f, bedZ + 1.5f, COARSE)
            else sleeperAt(it, (if (friuli) FB_A else benchA) + 0.36f, 1.45f, fl + 6.9f, benchB - 0.2f, 1.45f, fl + 6f)
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
            // in Friuli the oil lamp hangs from the hood's shelf instead (fogolarCorner), and the fogolâr shows over the table
            if (!friuli) prop { lamp(lit) }
        }
        for (p in peopleAt("table")) { footShadow(2.5f, 8.2f); personAt(p, 2.5f, 8.2f, fl) }
        // from level 2 in the front corner by the left wall, in front of the table
        if (!bare) zala?.let { smallMattress(it, COT_A, COT_C, COT_B, COT_D) }
        thing("potatoes") { sack(8.3f, 7.8f) }
        thing("eggs", slop = 1) { basket(7.25f, 8.15f) }

        // the front walls, cut down, and the garden in front of them
        stubs()
        // the bench at every level (the cat's), the pots of geraniums at the top (RoomPainter.garden)
        prop { gardenBench() }
        catAround(behind = false)
        if (lv >= POTS_LEVEL) prop { pots() }
        prop { vegetables() }
        if (env.dark < 0.5f) prop { hen(henX, henY) }
        fireflies(iso.ix(0f, 12f), iso.iy(12f, 12f, 20f), iso.ix(12f, 0f), iso.iy(12f, 12f, 0f), 12, 5)

        // light: the lamp from dusk, the embers always (the stove fired up: brighter, further), the sun through the window by day
        val oven = fxOn("oven")
        if (lit) {
            if (friuli) s.light(iso.sx(LUM_X, LUM_Y), iso.sy(LUM_X, LUM_Y, hb - 3.6f), 54f, 1.05f * env.windows)
            else s.light(iso.sx(lampX, lampY), iso.sy(lampX, lampY, fl + 16f), 50f, 1.05f * env.windows)
        }
        // the embers, further and warmer while the lamp is out for a sleeper, glowing on her bench; on a rainy day the fire
        // kept up (everyone in, a grey light through the window); none from a cold hearth (the house's out of wood, blown out)
        val embers = if (quiet) 1f else if (frame.sky.rain > 0.3f) 0.7f else 0f
        if (!hearthCold) {
            if (friuli) {
                // the fogolâr's fire lights the room at every level; the spolert's fire door a little beside it
                s.light(iso.sx(FOG_FX, FOG_FY), iso.sy(FOG_FX, FOG_FY, hz + 3f), 34f + 10f * embers + 22f * oven, 0.95f + 0.2f * embers + 0.4f * oven)
                if (stove) s.light(iso.sx(3.15f, SP_Y), iso.sy(3.15f, SP_Y, fl + 5.5f), 10f + 10f * oven, 0.5f + 0.3f * oven)
            } else if (stove) s.light(iso.sx(2.85f, stoveY), iso.sy(2.85f, stoveY, fl + 3.5f), 18f + 14f * embers + 24f * oven, 0.7f + 0.25f * embers + 0.5f * oven)
            // the hearth's fire lights the bare room, all the light there is at night
            else s.light(iso.sx(fireX, fireY), iso.sy(fireX, fireY, hz + 3f), 34f + 10f * embers + 22f * oven, 0.95f + 0.2f * embers + 0.4f * oven)
        }
        // the fogolâr's smoke out of its chimney, thicker when the fire is built up
        if (friuli && lv >= 2 && !hearthCold) {
            val sx = iso.sx(1.6f, 1.45f); val sy = iso.sy(1.6f, 1.45f, hw + 1f)
            s.smoke(sx, sy, 0.3f + 0.7f * oven, 31)
        }
        // the stove fired up: smoke from the chimney over the corner
        if (!friuli && stove && oven > 0.05f) {
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

    // ------------------------------------------------------------------ Friuli: the fogolâr, the spolert, the radio

    /** The spolert's iron plate, the hood's foot (its shelf's top) over the fogolâr. */
    private val spZ = fl + 7.6f
    private val hb = fl + 16.5f

    /**
     * The fogolâr in the back corner and what is round it ([friuli]): the hearth (a thing at every level), the fire on it,
     * the cauldron on its chain (the "pot"); at level 1 the pole the chain hangs from, the logs and the polenta board on
     * the wall; from level 2 ([stove]) the spolert beside it, the hood over both with its shelf and what stands on it.
     */
    private fun fogolarCorner(stove: Boolean) {
        if (!stove) prop { pole() }
        thing("hearth") { fogolar() }
        hearthFire(FOG_FX, FOG_FY)
        prop { andirons() }
        val chainTop = if (stove) hb else fl + 17.5f
        thing("pot", slop = 1) { cauldron(FOG_FX, FOG_FY, hz + 2.3f, chainTop) }
        if (!stove) {
            prop { logs() }
            prop { board(x0 + 0.06f, 2.9f, fl + 11f, onWall = true) }
            return
        }
        thing("stove") { spolert() }
        prop { spolertPipe() }
        prop { hood() }
        prop { ledge() }
        val lit = env.windows > 0.35f && !asleepHere && there("table")
        prop { lucerna(lit) }
    }

    /**
     * The oil lamp (lum) hanging on its little chain from the hood's shelf at its left end, the light of the table from
     * dusk: a brass font with its spout, the flame at the spout, a ring over it; put out for anyone asleep.
     */
    private fun lucerna(lit: Boolean) {
        val p = P.toFloat()
        val cx = iso.sx(LUM_X, LUM_Y); val hook = iso.sy(LUM_X, LUM_Y, hb - 0.7f); val fy = iso.sy(LUM_X, LUM_Y, hb - 3.4f)
        val chx = floor(cx).toInt()
        if (P > 1) chain(chx, floor(hook).toInt(), floor(fy - 2.2f * p).toInt(), brassL, brassD)
        else for (yy in floor(hook).toInt() until floor(fy - 2.2f * p).toInt()) c.set(chx, yy, if (yy % 2 == 0) brassL else brassD)
        // the ring over the font, the font, its spout to the left with the wick
        arc(cx, fy - 1.6f * p, 0.9f * p, 0.8f * p, brassD)
        c.fillEllipse(cx + 0.3f, fy + 0.3f, 2.4f * p, 1.4f * p, brassD)
        c.fillEllipse(cx, fy, 2.3f * p, 1.25f * p, brass)
        c.fillEllipse(cx - 0.8f * p, fy - 0.5f * p, 0.9f * p, 0.45f * p, brassL)
        c.line(floor(cx - 2f * p).toInt(), floor(fy - 0.2f * p).toInt(), floor(cx - 3.4f * p).toInt(), floor(fy - 0.9f * p).toInt(), brassD)
        c.fillRect(floor(cx + 1.6f * p).toInt(), floor(fy + 1.1f * p).toInt(), max(1, P), max(1, P), brassD)
        if (lit) glow {
            val wx = cx - 3.6f * p; val wy = fy - 1.4f * p
            c.fillEllipse(wx, wy - 0.6f * p, 0.7f * p, 1.3f * p, Pal.FLAME[2])
            c.fillEllipse(wx, wy - 0.4f * p, 0.4f * p, 0.8f * p, Pal.FLAME[0])
        }
        if (lit) s.fx {
            c.penEmissive = true
            c.ditherCircle(cx - 3.6f * p, fy - 1.6f * p, 7f * K, Pal.FLAME[1], 0.14f * env.windows)
            c.penEmissive = false
        }
    }

    /** The fogolâr: a knee-high block of grey stones in the corner, a niche with the firewood's ends in its front, its top of flat slabs grey with ash. */
    private fun fogolar() {
        onY(FOG_Y1, x0, FOG_X1, fl, hz) { x, z, px, py -> hearthStone(x - x0, z, px, py, 1f, niche = true, nicheA = 0.3f, nicheB = 1.12f) }
        onX(FOG_X1, y0, FOG_Y1, fl, hz) { y, z, px, py -> hearthStone(y - y0 + 3.1f, z, px, py, 0.8f, niche = false) }
        onFlat(hz, x0, y0, FOG_X1, FOG_Y1) { x, y, px, py ->
            val d = hypot(x - FOG_FX, (y - FOG_FY) * 1.2f)
            val fu = (x - x0) / 0.62f; val fv = (y - y0) / 0.55f
            val slab = floor(fu).toInt() + floor(fv).toInt() * 7
            val n = Noise.rnd(px, py, 81)
            when {
                d < 0.42f -> if (n < 0.3f) Col.hex(0x1E1A18) else Col.hex(0x2E2826)
                d < 0.72f + Noise.v2(x * 4f, y * 4f, 83) * 0.22f -> if (n < 0.25f) Col.hex(0x6A6660) else if (n < 0.6f) Col.hex(0x8E8A84) else Col.hex(0xA8A49C)
                x > FOG_X1 - 0.1f || y > FOG_Y1 - 0.1f -> Col.hex(0xC0B8A8)
                fu - floor(fu) < 0.08f || fv - floor(fv) < 0.1f -> Col.hex(0x5A534C)
                else -> Col.scale(if (Noise.rnd(slab, 85) < 0.5f) Pal.STONE_M else Col.hex(0x9A948A), if (n > 0.96f) 0.86f else 1f)
            }
        }
    }

    /** The two iron andirons (cjavedâi) either side of the fire, the logs lying across them: a bar each with a knob at its front. */
    private fun andirons() {
        for (dx in floatArrayOf(-0.42f, 0.42f)) {
            val ax = FOG_FX + dx; val ay = FOG_FY + 0.3f
            iso.post(ax, ay, hz, hz + 1.6f, ironD)
            iso.line(ax, ay - 0.5f, hz + 0.9f, ax, ay, hz + 0.9f, ironD)
            knob(ax, ay, hz + 1.9f, ironL, Col.hex(0x8C8896), ironD)
        }
    }

    /**
     * The cjaldîr: the copper cauldron for the polenta hanging over the fire at ([x], [y]), its round bottom at [zb], on its
     * iron bail and the chain up to [chainTop] (the pole, or into the hood); the wooden stick in it and, in the evening and
     * while the fire is built up, polenta in it (else water); it steams. Tapped ([pokes]) it sways on its chain and puffs,
     * tapped again the polenta plops, a bubble bursting up out of it.
     */
    private fun cauldron(x: Float, y: Float, zb: Float, chainTop: Float) {
        val p = P.toFloat()
        val pk = poked("pot"); val a = pk?.age(t) ?: 0f
        val swing = if (pk != null && pk.step == 0 && a < 1.3f) sin(a * 9f) * 2.2f * p * (1f - a / 1.3f) else 0f
        val cx = iso.sx(x, y) + swing; val base = iso.sy(x, y, zb)
        val rx = 5.2f * p; val ry = 2.5f * p; val top = base - 7f * p
        val l = Col.hex(0xE8945A); val m = Col.hex(0xC2693A); val d = Col.hex(0x8A4426); val hi = Col.hex(0xFFC898)
        // the chain from the pole or the hood down to the bail's hook, swaying with it
        val hookY = top - 5.5f * p
        val cTop = iso.sy(x, y, chainTop)
        val chx = floor(cx).toInt()
        if (P > 1) chain(chx, floor(cTop).toInt(), floor(hookY).toInt(), ironL, ironD)
        else for (yy in floor(cTop).toInt() until floor(hookY).toInt()) c.set(chx, yy, if (yy % 2 == 0) ironL else ironD)
        // the round bottom, the body, the rolled rim
        c.fillEllipse(cx, base - 2.6f * p, rx * 0.86f, 2.9f * p, d)
        c.fillEllipse(cx - 0.6f * p, base - 3f * p, rx * 0.74f, 2.4f * p, m)
        drum(cx, base - 2.6f * p, top, rx, ry, l, m, d, hi)
        if (P > 1) {
            // hammered: little dents catching the light, a dark band under the rim
            for (k in 0 until 9 * P) c.set(floor(cx - rx * 0.8f + Noise.rnd(k, 61) * rx * 1.6f).toInt(), floor(top + 2.5f * p + Noise.rnd(k, 62) * 4f * p).toInt(), if (k % 3 == 0) hi else Col.mix(m, d, 0.5f))
            c.hline(floor(cx - rx).toInt() + 1, ceil(cx + rx).toInt() - 2, floor(top + 1.6f * p).toInt(), d)
        }
        c.fillEllipse(cx, top, rx, ry, l)
        val polenta = fxOn("oven") > 0.05f || s.hour in 17f..21.5f
        val surf = if (polenta) Col.hex(0xF0C24A) else Col.hex(0x6E8088)
        c.fillEllipse(cx, top + 0.3f * p, rx - 1f * p, ry - 0.8f * p, d)
        c.fillEllipse(cx, top + 0.6f * p, rx - 1.4f * p, ry - 1.1f * p, surf)
        if (polenta) c.fillEllipse(cx - 1.2f * p, top + 0.3f * p, rx * 0.4f, ry * 0.3f, Col.hex(0xFAD878))
        // the wooden stick standing in it, leaning on the rim
        val sx0 = cx + 1.2f * p; val sy0 = top + 0.4f * p; val sx1 = cx + 4.6f * p; val sy1 = top - 8.5f * p
        for (o in 0 until P) c.line(floor(sx0).toInt() + o, floor(sy0).toInt(), floor(sx1).toInt() + o, floor(sy1).toInt(), if (o == 0) Pal.WOOD_L else Pal.WOOD_M)
        // the iron bail from rim to rim, up to the hook
        for (o in 0 until max(1, P / 2)) arc(cx, top - 0.5f * p - o, rx + 0.4f * p, 5f * p, ironD, PI.toFloat(), 2f * PI.toFloat())
        c.set(floor(cx - rx - 0.4f * p).toInt(), floor(top - 0.5f * p).toInt(), ironL); c.set(floor(cx + rx + 0.4f * p).toInt() - 1, floor(top - 0.5f * p).toInt(), ironL)
        if (hearthCold && fxOn("oven") < 0.02f) return
        s.smoke(cx - 1.5f * p, top - 4f * p, 0.55f, 11); s.smoke(cx + 1.5f * p, top - 3f * p, 0.45f, 12)
        if (pk != null) s.fx {
            val steam = Col.hex(0xF4F6FA)
            if (pk.step == 0) PokeArt.puff(c, cx + rx * 0.5f, top - 1f * p, a / 1.3f, 5f * p, 5, steam, 3, 0.75f)
            else {
                // the polenta plops: a bubble bursts up out of it and falls back, a burst of steam
                val u = (a / 0.9f).coerceIn(0f, 1f)
                if (u < 1f) c.fillCircle(cx - 0.5f * p, top - sin(u * PI.toFloat()) * 4.5f * p, (1.3f - u * 0.5f) * p, if (polenta) Col.hex(0xF6CC52) else Col.hex(0xB8C8D0))
                PokeArt.puff(c, cx, top - 2f * p, a / 1.9f, 8f * p, 6, steam, 5, 0.85f)
            }
        }
    }

    /** Level 1: the pole the chain hangs from, set across the black corner from wall to wall over the fire. */
    private fun pole() {
        val z = fl + 17.5f
        rod(x0, 2.35f, z, 2.5f, y0, z, Pal.WOOD_D, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
    }

    /** Level 1: firewood stacked on the floor between the fogolâr and the bench, round logs with their cut ends to the room. */
    private fun logs() {
        val xa = 2.62f; val xb = 3.4f; val r = 0.62f
        for ((k, lz) in listOf(fl + r, fl + r * 2.7f, fl + r * 4.4f).withIndex()) for (j in 0 until 3 - k) {
            val ly = 1.2f + j * 0.27f + k * 0.13f
            // the bark along it, lit on top, in shadow under
            val n = (2 * r * K).toInt().coerceAtLeast(2)
            for (o in 0..n) {
                val f = o.toFloat() / n
                val z = lz - r + f * 2f * r
                iso.line(xa, ly, z, xb, ly, z, if (f > 0.75f) Pal.LOG_L else if (f < 0.3f) Pal.LOG_D else Pal.LOG_M)
            }
            if (P > 1) iso.line(xa + 0.1f, ly, lz + 0.15f, xb - 0.15f, ly, lz + 0.1f, Col.scale(Pal.LOG_M, 0.8f))
            // the cut end: pale wood, a ring and the heart darker
            val ex = iso.sx(xb, ly); val ey = iso.sy(xb, ly, lz)
            c.fillEllipse(ex, ey, r * K * 0.85f, r * K * 1.05f, Pal.LOG_D)
            c.fillEllipse(ex - 0.3f, ey - 0.3f, r * K * 0.72f, r * K * 0.92f, Col.hex(0xC89A62))
            if (P > 1) { arc(ex - 0.3f, ey - 0.3f, r * K * 0.45f, r * K * 0.6f, Col.hex(0xA87A48)); c.set(floor(ex).toInt(), floor(ey).toInt(), Col.hex(0x8A5E36)) }
        }
    }

    /**
     * The round polenta board (taulîr) of beech with its handle, its middle at ([x], [y], [z]): hanging on the left wall
     * ([onWall]), or leaning against the hood on its shelf (facing the room, in the plane y = [y]).
     */
    private fun board(x: Float, y: Float, z: Float, onWall: Boolean) {
        val wood = Col.hex(0xD8B07A); val grainC = Col.hex(0xC09462); val rim = Col.hex(0xA07040)
        val ra = 0.42f; val rz = 2.3f
        val boardAt = { u: Float, v: Float, px: Int ->
            val r = u * u + v * v
            when {
                r > 1f -> if (abs(u) < 0.16f && v > 0f && v < 1.38f) (if (v > 1.15f && abs(u) < 0.07f) 0 else rim) else 0
                r > 0.8f -> rim
                abs(Noise.v2(u * 2f + 3f, v * 6f, 41) - 0.5f) < 0.05f -> grainC
                u < -0.4f && v > 0.2f -> Col.mix(wood, Col.hex(0xFFFFFF), 0.18f)
                (px / max(1, P)) % 7 == 0 && Noise.rnd(px, 3, 9) < 0.2f -> grainC
                else -> wood
            }
        }
        if (onWall) onX(x, y - ra - 0.02f, y + ra + 0.02f, z - rz - 0.1f, z + rz * 1.4f) { yy, zz, px, _ -> boardAt((yy - y) / ra, (zz - z) / rz, px) }
        else onY(y, x - ra - 0.02f, x + ra + 0.02f, z - rz - 0.1f, z + rz * 1.4f) { xx, zz, px, _ -> boardAt((xx - x) / ra, (zz - z) / rz, px) }
    }

    /**
     * The spolert, the iron range beside the fogolâr (the "stove" from level 2): on four short legs, cream enamel panels in a
     * black iron frame, the oven door with its brass handle, the fire door with the embers glowing through its grate, the ash
     * drawer; its black plate with two hob rings, a brass rail along its front, the blue coffee pot on the plate.
     */
    private fun spolert() {
        val za = fl + 1.3f
        for ((lx, ly) in listOf(SP_A + 0.08f to SP_Y - 0.08f, SP_B - 0.08f to SP_Y - 0.08f, SP_B - 0.08f to y0 + 0.12f)) iso.post(lx, ly, fl, za, ironD)
        onY(SP_Y, SP_A, SP_B, za, spZ) { x, z, px, py -> spolertFront(x - SP_A, z - za, px, py) }
        c.penEmissive = false
        onX(SP_B, y0, SP_Y, za, spZ) { y, z, px, _ -> spolertSide(y - y0, z - za, px) }
        // the plate, a little proud of the body, and its rings
        iso.box(SP_A - 0.05f, y0, spZ, SP_B - SP_A + 0.1f, SP_Y - y0 + 0.08f, 0.7f, Col.hex(0x46424E), Col.hex(0x2E2A32), Col.hex(0x3A3640))
        onFlat(spZ + 0.7f, SP_A - 0.05f, y0, SP_B + 0.05f, SP_Y + 0.08f) { x, y, _, _ ->
            val d = min(hypot(x - 2.78f, y - 1.55f), hypot(x - 3.2f, y - 1.6f))
            when {
                abs(d - 0.17f) < 0.04f -> ironL
                abs(d - 0.09f) < 0.03f -> Col.hex(0x1E1A20)
                x > SP_B - 0.02f || y > SP_Y + 0.04f -> Col.hex(0x8C8896)
                else -> 0
            }
        }
        // the brass rail along its front on two brackets
        val ry = SP_Y + 0.3f; val rz = spZ - 0.6f
        for (bx in floatArrayOf(SP_A + 0.1f, SP_B - 0.1f)) rod(bx, SP_Y, rz, bx, ry, rz, brassD, brass, brassD, brassD)
        rod(SP_A, ry, rz, SP_B, ry, rz, brass, brass, brass, brassD)
        // the coffee pot on the near ring: blue enamel, a white rim, its spout and lid knob
        val cx = iso.sx(3.2f, 1.6f); val by = iso.sy(3.2f, 1.6f, spZ + 0.7f)
        val p = P.toFloat()
        val bl = Col.hex(0x5E8ED0); val bm = Col.hex(0x3A68B0); val bd = Col.hex(0x264A86)
        drum(cx, by, by - 5f * p, 2.1f * p, 1f * p, bl, bm, bd, Col.hex(0xA6C6EE))
        c.fillEllipse(cx, by - 5f * p, 2.1f * p, 1f * p, china)
        c.fillEllipse(cx, by - 5.4f * p, 1.4f * p, 0.7f * p, bm)
        c.fillRect(floor(cx - 0.5f * p).toInt(), floor(by - 7f * p).toInt(), max(1, P), max(1, P), ironD)
        c.line(floor(cx - 2f * p).toInt(), floor(by - 3.5f * p).toInt(), floor(cx - 3.6f * p).toInt(), floor(by - 5.2f * p).toInt(), bm)
        loop(cx + 2.4f * p, by - 3f * p, 1.3f * p, 1.6f * p, 0.7f * p, true, bd, bm)
        if (fxOn("steam") > 0.05f || s.hour in 6f..10f) wisps(cx - 3.6f * p, by - 6f * p, fxOn("steam"), 29)
    }

    /** The spolert's front at [a] cells along it and [h] nominal px up its body: the iron frame, the oven door, the fire door, the ash drawer. */
    private fun spolertFront(a: Float, h: Float, px: Int, py: Int): Int {
        val w = SP_B - SP_A; val hh = spZ - fl - 1.3f
        val cream = Col.hex(0xEFE7D2); val creamD = Col.hex(0xD8CEB4); val frame = Col.hex(0x2E2A32); val frameL = Col.hex(0x5E5A64)
        c.penEmissive = false
        if (a < 0.05f || w - a < 0.05f || h < 0.35f || h > hh - 0.35f) return if (a < 0.05f || h > hh - 0.35f) frameL else frame
        // the fire door, top right: a grate the embers glow through
        if (a > w * 0.56f && h in hh * 0.5f..hh - 0.7f) {
            val fa = a - w * 0.56f; val fz = h - hh * 0.5f
            if (fa < 0.05f || fz > hh * 0.5f - 0.95f) return frameL
            if (w - a < 0.1f || fz < 0.3f) return frame
            if (((fz / 0.45f).toInt() % 2) == 1) {
                if (hearthCold) return Col.mix(ironD, Col.hex(0x5A524C), if (Noise.rnd((a * 30f).toInt(), (fz * 4f).toInt(), 3) < 0.3f) 0.5f else 0f)
                c.penEmissive = true
                val flick = Noise.rnd((a * 30f).toInt(), (t * 6).toInt(), 3) - fxOn("oven") * 0.35f
                return if (flick < 0f) Pal.FLAME[0] else if (flick < 0.3f) Pal.FLAME[1] else if (flick < 0.75f) Pal.FLAME[2] else Pal.FLAME[3]
            }
            return ironD
        }
        // the ash drawer under it
        if (a > w * 0.56f && h < hh * 0.5f - 0.3f) return if (abs(h - hh * 0.25f) < 0.3f && abs(a - w * 0.78f) < 0.08f) brass else if (h > hh * 0.5f - 0.7f || w - a < 0.1f) frame else creamD
        // the oven door, its brass bar for a handle
        if (a < w * 0.52f) {
            if (a > w * 0.5f) return frame
            if (abs(h - (hh - 1.4f)) < 0.3f && a in 0.1f..(w * 0.44f)) return brassL
            if (P > 1 && (abs(a - 0.08f) < 0.02f || abs(h - 0.6f) < 0.18f)) return Col.mix(cream, Col.hex(0xFFFFFF), 0.4f)
            return if (a > w * 0.44f || h < 0.75f) creamD else cream
        }
        return frame
    }

    /** The spolert's side at [a] cells along it and [h] up: a cream enamel panel in its iron frame, a brass boss in its middle. */
    private fun spolertSide(a: Float, h: Float, px: Int): Int {
        val w = SP_Y - y0; val hh = spZ - fl - 1.3f
        val frame = Col.hex(0x262228)
        if (a < 0.06f || w - a < 0.06f || h < 0.35f || h > hh - 0.35f) return frame
        if (hypot((a - w / 2f) * 4f, h - hh / 2f) < 0.55f) return brassD
        return if (P > 1 && (px / P) % 9 == 0) Col.hex(0xC4BAA0) else Col.hex(0xCFC5AA)
    }

    /** The spolert's pipe from the back of its plate up into the fogolâr's hood. */
    private fun spolertPipe() {
        val px = iso.ix(3.05f, 1.3f); val top = iso.iy(3.05f, 1.3f, hb + 1f); val bot = iso.iy(3.05f, 1.3f, spZ + 0.7f)
        val p = P
        for (x in px - 2 * p until px + p) {
            val u = (x - px + 2 * p + 0.5f) / (3f * p)
            c.vline(x, top, bot, if (u < 0.25f) ironL else if (u > 0.72f) ironD else iron)
        }
        val jy = iso.iy(3.05f, 1.3f, spZ + 4.5f)
        c.hline(px - 2 * p - 1, px + p, jy, ironL); c.hline(px - 2 * p - 1, px + p, jy + max(1, p / 2), ironD)
    }

    /** The hood's +y face's and +x face's run from the shelf up to the walls' top (where they meet the chimney's flue). */
    private fun hoodY(z: Float): Float = HOOD_Y - (HOOD_Y - HOOD_YT) * ((z - hb) / (hw - hb)).coerceIn(0f, 1f)
    private fun hoodX(z: Float): Float = HOOD_X - (HOOD_X - HOOD_XT) * ((z - hb) / (hw - hb)).coerceIn(0f, 1f)

    /** The fogolâr's hood over the hearth and the spolert ([RoomPainter.fogolarHood]). */
    private fun hood() = fogolarHood(HOOD_X, HOOD_Y, HOOD_XT, HOOD_YT, hb)

    /**
     * The shelf round the hood's foot and what is on it: a lace edging hanging from its front, two copper pans, the polenta
     * board leaning against the hood, a plate on the side.
     */
    private fun ledge() {
        val d = 0.27f; val lz = hb - 0.7f
        iso.box(HOOD_X - 0.02f, y0, lz, d, HOOD_Y - y0, 0.7f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(x0, HOOD_Y - 0.02f, lz, HOOD_X + d - x0, d, 0.7f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.line(x0, HOOD_Y + d, hb, HOOD_X + d, HOOD_Y + d, hb, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
        // the lace edging under its front, scalloped, with little holes closer up
        onY(HOOD_Y + d + 0.01f, x0 + 0.04f, HOOD_X + d - 0.02f, lz - 1.1f, lz) { x, z, px, py -> lace(x - x0, z - lz, px, py, 1f) }
        onX(HOOD_X + d + 0.01f, y0 + 0.04f, HOOD_Y + d - 0.02f, lz - 1.1f, lz) { y, z, px, py -> lace(y - y0, z - lz, px, py, 0.84f) }
        // on it: the polenta board leaning on the hood, a copper pan each side of it, a plate on the side shelf
        val oy = HOOD_Y + 0.12f
        copperPan(1.42f, oy, hb, 2.6f)
        board(2.32f, HOOD_Y + 0.05f, hb + 2.3f, onWall = false)
        copperPan(3.1f, oy, hb, 2.1f)
        val px = iso.sx(HOOD_X + 0.12f, 1.55f); val py = iso.sy(HOOD_X + 0.12f, 1.55f, hb + 2.2f)
        val p = P.toFloat()
        c.fillEllipse(px, py, 1.3f * p, 2.3f * p, chinaD); c.fillEllipse(px + 0.3f, py - 0.2f, 1.2f * p, 2.1f * p, china)
        arc(px + 0.3f, py - 0.2f, 1.2f * p - 0.5f, 2.1f * p - 0.5f, chinaBlue)
    }

    /** From level 2 the bench along the left wall, the fogolâr's other side: a thick plank on legs and a stretcher. */
    private fun leftBench() {
        val xa = x0 + 0.08f; val xb = x0 + 0.82f; val bz = fl + 4.6f
        for (ly in floatArrayOf(LB_A + 0.15f, LB_B - 0.15f)) { iso.post(xb - 0.12f, ly, fl, bz, Pal.WOOD_D); iso.post(xa + 0.15f, ly, fl, bz, Pal.WOOD_D) }
        rod(xb - 0.12f, LB_A + 0.15f, fl + 1.6f, xb - 0.12f, LB_B - 0.15f, fl + 1.6f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(xa, LB_A, bz, xb - xa, LB_B - LB_A, 0.9f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (P > 1) grain(xa, LB_A, xb, LB_B, bz + 0.9f, 5, alongY = true)
        iso.line(xb, LB_A, bz + 0.9f, xb, LB_B, bz + 0.9f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
    }

    /**
     * Zia Nives's radio on its shelf high in the front corner: a walnut cabinet with its cloth-covered speaker, the dial lit
     * while someone listens at the table, two knobs. From level 3 the corner itself (a thing of the kitchen): a lace edging
     * along the shelf, a little vase of flowers beside the radio and the family's photo in its frame on the wall.
     */
    private fun radioCorner() {
        if (there("corner")) thing("corner", slop = 1) { radioShelf(full = true) } else prop { radioShelf(full = false) }
    }

    private fun radioShelf(full: Boolean) {
        val cy = y1 - 0.5f; val sz = fl + 13.4f
        val dark = Col.hex(0x6A4426); val darkL = Col.hex(0x8E6038); val darkD = Col.hex(0x4A2C18)
        if (full) {
            // the family's photo in its frame beside the corner: two figures in sepia, a child between them
            onX(x0 + 0.03f, cy - 1.72f, cy - 0.92f, sz + 1.6f, sz + 6.4f) { y, z, _, _ ->
                val u = (y - (cy - 1.72f)) / 0.8f; val v = (z - sz - 1.6f) / 4.8f
                when {
                    u < 0.1f || u > 0.9f || v < 0.07f || v > 0.93f -> if (u < 0.1f || v > 0.93f) darkL else dark
                    u < 0.17f || u > 0.83f || v < 0.13f || v > 0.87f -> Col.hex(0xEDE3C8)
                    hypot((u - 0.36f) * 1.2f, v - 0.62f) < 0.09f || hypot((u - 0.64f) * 1.2f, v - 0.65f) < 0.09f || hypot((u - 0.5f) * 1.2f, v - 0.42f) < 0.07f -> Col.hex(0x8A6A4A)
                    (abs(u - 0.36f) < 0.12f && v in 0.18f..0.55f) || (abs(u - 0.64f) < 0.12f && v in 0.18f..0.58f) || (abs(u - 0.5f) < 0.08f && v in 0.18f..0.36f) -> Col.hex(0x5A4430)
                    else -> Col.hex(0xC8B08A)
                }
            }
        }
        // the shelf on its bracket
        rod(x0, cy, sz - 2.4f, x0 + 0.5f, cy, sz - 0.1f, darkD, dark, darkD, darkD)
        iso.box(x0, cy - 0.55f, sz - 0.5f, 0.66f, 1.1f, 0.5f, darkL, dark, darkD)
        if (full) onX(x0 + 0.67f, cy - 0.54f, cy + 0.54f, sz - 1.5f, sz - 0.5f) { y, z, px, py -> lace(y - cy, z - (sz - 0.5f), px, py, 0.9f) }
        // the radio: its walnut case, rounded on top, the speaker's cloth behind a fretwork, the dial, two knobs
        val ra = cy - 0.36f; val rb = cy + 0.34f; val rt = sz + 3.8f; val xf = x0 + 0.52f
        iso.box(x0 + 0.04f, ra, sz, xf - x0 - 0.04f, rb - ra, 3.4f, darkL, Col.hex(0x7A4E2C), Col.hex(0x5A361E))
        val listening = peopleAt("table").isNotEmpty()
        onX(xf, ra, rb, sz, rt) { y, z, px, py ->
            val u = (y - ra) / (rb - ra); val h = z - sz
            c.penEmissive = false
            when {
                h > 3.4f + 0.4f * (1f - (2f * u - 1f) * (2f * u - 1f)) -> 0
                h > 3.4f -> darkL
                u < 0.1f || u > 0.9f || h < 0.25f -> dark
                // the dial: a cream strip, a red needle, lit while someone listens
                h in 0.55f..1.2f -> if (abs(u - 0.62f) < 0.04f) Col.hex(0xC0302A) else if (listening) { c.penEmissive = true; Col.hex(0xFFE7A0) } else Col.hex(0xE8DCB0)
                // the speaker's cloth behind its fretwork bars
                h in 1.5f..3.15f && u in 0.18f..0.82f -> if (P > 1 && abs(((u * 6f) % 1f) - 0.5f) < 0.12f) dark else if ((px + py) % 2 == 0 && P > 1) Col.hex(0xC8B488) else Col.hex(0xD6C49A)
                h < 0.5f && (abs(u - 0.24f) < 0.09f || abs(u - 0.76f) < 0.09f) -> Col.hex(0x2E1E14)
                else -> Col.hex(0x7A4E2C)
            }
        }
        c.penEmissive = false
        // the rounded top's crown
        iso.line(xf, ra + 0.08f, sz + 3.45f, xf, rb - 0.08f, sz + 3.45f, darkL)
        if (!full) return
        // a little vase of flowers beside it
        val vx = iso.ix(x0 + 0.34f, cy + 0.42f); val vy = iso.iy(x0 + 0.34f, cy + 0.42f, sz)
        c.fillRect(vx - P, vy - 2 * P, 2 * P + 1, 2 * P, Col.hex(0xF0EEE6)); c.set(vx - P, vy - 2 * P, Col.hex(0xFFFFFF))
        for (k in -1..1) {
            c.line(vx, vy - 2 * P, vx + k * 2 * P, vy - (5 + abs(k)) * P, Col.hex(0x5E8A4A))
            c.set(vx + k * 2 * P, vy - (5 + abs(k)) * P - 1, if (k == 0) Col.hex(0xF0C23A) else Col.hex(0xC03A6A))
        }
    }

    private companion object {
        /** Friuli's fogolâr in the corner out to (FOG_X1, FOG_Y1), the fire on its top at (FOG_FX, FOG_FY). */
        const val FOG_X1 = 2.45f; const val FOG_Y1 = 2.4f; const val FOG_FX = 1.72f; const val FOG_FY = 1.7f
        /** The spolert beside it along the right wall, x SP_A … SP_B, out to SP_Y. */
        const val SP_A = 2.52f; const val SP_B = 3.42f; const val SP_Y = 2.15f
        /** The hood's foot out to (HOOD_X, HOOD_Y), its top at the walls' (HOOD_XT, HOOD_YT). */
        const val HOOD_X = 3.5f; const val HOOD_Y = 2.65f; const val HOOD_XT = 2.25f; const val HOOD_YT = 1.85f
        /** The bench under the window from FB_A (beside the spolert); the bench along the left wall from LB_A to LB_B. */
        const val FB_A = 3.55f; const val LB_A = 2.6f; const val LB_B = 4.15f
        /** The oil lamp under the hood shelf's left end. */
        const val LUM_X = 1.42f; const val LUM_Y = 2.98f

        /**
         * Ančka's straw mattress on the floor before the door; Zala's small one in the front corner by the left wall, at
         * level 1 in the middle of the floor: all along x.
         */
        const val MAT_A = 5.5f; const val MAT_B = 8.5f; const val MAT_C = 2.75f; const val MAT_D = 3.75f
        const val COT_A = 1.12f; const val COT_B = 3.0f; const val COT_C = 7.6f; const val COT_D = 8.5f
        const val COT1_A = 3.4f; const val COT1_B = 5.3f; const val COT1_C = 5.3f; const val COT1_D = 6.2f
    }
}
