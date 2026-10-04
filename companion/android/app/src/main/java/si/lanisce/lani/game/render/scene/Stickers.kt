package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFixtures
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A thing of a scene cut out of its picture, for a word's card: [width] × [height] ARGB pixels, row by row, clear around
 * the thing (alpha 0) and see-through where the scene blends it (a dragonfly's wings, a shadow on the ground).
 */
class Sticker(val width: Int, val height: Int, val argb: IntArray) {
    /** How many of its pixels show (alpha over half). */
    val solid: Int get() = argb.count { (it ushr 24) >= 0x80 }
}

/**
 * Stickers: an object slot of a scene art drawn alone ([ArtPainter.solo]: the painter runs as always and only that
 * thing's pixels land) on a quiet summer noon, no people, weather, effects or pokes, at [DETAIL]; then cut to the
 * thing with its outline. It is drawn twice, on black and on white: a pixel the same on both is the thing's own, one
 * that changed is the backdrop showing through, as much as it changed (its alpha), so what the scene blends comes out
 * see-through. Pure and deterministic (the same pixels every time), a few milliseconds a sticker; the app caches them
 * (ui/words/WordPicture.kt).
 */
object Stickers {
    /** Picture pixels per scene canvas pixel: the finest detail, as the scene shows closest up. */
    const val DETAIL = 3

    /** The frame they are cut from: noon in July, still, a waxing crescent moon up (it is up at noon); bumped with [VERSION] when that changes. */
    private val FRAME = SceneFrame(time = 1.0, hour = 12f, month = 7, moon = 4f)

    /** The look of the stickers: raise it when they change, so the cached ones are drawn anew (3: the kitchen's curtains are a thing of their own). */
    const val VERSION = 3

    /** Scene canvas pixels around the thing's hit (at detail 1) that the finer picture may still reach. */
    private const val MARGIN = 4

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** The sticker of [slot] of [art], or null when the art has no such slot or draws nothing there. */
    fun cut(art: String, slot: String, detail: Int = DETAIL): Sticker? {
        if (slot !in SceneArt.objects[art].orEmpty()) return null
        // a painter of its own: this may run off the main thread while a scene is drawn with the shared one
        val painter = ScenePainters.create(art) as? ArtPainter ?: return null
        painter.solo = slot
        // in a world where it is there (a thing that goes as its room is upgraded: the level that has it)
        val frame = FRAME.copy(objects = setOf(slot), world = SceneFixtures.showing(art, slot))
        // the widest and tallest view a scene has: a thing at the stage's edge (the mill by the stream) is there whole
        val bw = ArtPainter.STAGE_W * 3 / 2; val bh = ArtPainter.STAGE_H * 2
        // where it is: its hit, drawn alone
        val hit = painter.render(PixelCanvas(bw, bh), frame).firstOrNull { it.target == SceneTarget.Thing(slot) } ?: return null
        val k = detail.coerceIn(1, Lens.MAX_DETAIL)
        val x0 = (hit.left - MARGIN) * k; val y0 = (hit.top - MARGIN) * k
        val cw = (hit.right - hit.left + 2 * MARGIN) * k; val ch = (hit.bottom - hit.top + 2 * MARGIN) * k
        val lens = Lens(k, x0, y0, bw, bh)
        val dark = PixelCanvas(cw, ch).also { painter.soloBackdrop = BLACK; painter.render(it, frame, lens) }
        val light = PixelCanvas(cw, ch).also { painter.soloBackdrop = WHITE; painter.render(it, frame, lens) }
        val out = IntArray(cw * ch)
        var l = cw; var t = ch; var r = -1; var b = -1
        for (i in out.indices) {
            val d = dark.pixels[i]; val w = light.pixels[i]
            // the backdrop's share: how far the pixel moved from black to white
            val through = max(Col.r(w) - Col.r(d), max(Col.g(w) - Col.g(d), Col.b(w) - Col.b(d))).coerceIn(0, 255)
            val a = 255 - through
            if (a < 8) continue
            val f = 255f / a
            out[i] = (a shl 24) or (min(255, (Col.r(d) * f).roundToInt()) shl 16) or (min(255, (Col.g(d) * f).roundToInt()) shl 8) or min(255, (Col.b(d) * f).roundToInt())
            val x = i % cw; val y = i / cw
            l = min(l, x); t = min(t, y); r = max(r, x); b = max(b, y)
        }
        if (r < 0) return null
        val sw = r - l + 1; val sh = b - t + 1
        val px = IntArray(sw * sh)
        for (y in 0 until sh) System.arraycopy(out, (t + y) * cw + l, px, y * sw, sw)
        return Sticker(sw, sh, px)
    }
}
