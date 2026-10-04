package si.lanisce.lani.game.render.book

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PictureThing
import si.lanisce.lani.game.scene.StoryPicture

/**
 * A picture of a story (companion/SCENES.md, "The story notebook"), drawn by hand in ink (the notebook goes over it in
 * pencil: [Sketch]): its
 * background ([InkBackgrounds]), then its props and figures ([InkProps], [InkFigures]) where the story file puts them, the
 * far ones first, and a printer's rule round it on warm paper. Pure Kotlin on a [PixelCanvas] of [W] × [H], so it renders
 * in the JVM tests as on the phone; the same picture always comes out the same.
 */
object BookPictures {
    const val W = 160
    const val H = 96

    /** The paper left round the rule. */
    private const val MARGIN = 3

    /** Where a thing at y 1 stands: the top of the picture, inside the rule. */
    private const val TOP = 12f

    /** Pixels a unit of a figure or a prop at size 1: a person about 31 px tall. */
    private const val SCALE = 1.25f

    /** [p] on a new canvas. */
    fun render(p: StoryPicture): PixelCanvas = PixelCanvas(W, H).also { render(it, p) }

    /** Draws [p] on [c] ([W] × [H]). */
    fun render(c: PixelCanvas, p: StoryPicture) {
        // the paper's grain and the background's strokes are the background's own: a figure added changes only where it stands
        val seed = p.bg.hashCode()
        c.reset(InkPal.PAPER)
        paper(c, seed, 0, 0, c.width, c.height)
        val ink = Ink(c, seed)
        val ground = InkBackgrounds.draw(ink, p.bg, p.night)
        val things = p.props.map { it to false } + p.figures.map { it to true }
        for ((t, figure) in things.sortedWith(compareByDescending<Pair<PictureThing, Boolean>> { it.first.y.coerceIn(0f, 1f) }.thenBy { it.second })) {
            val x = 8f + t.x.coerceIn(0f, 1f) * (c.width - 16)
            val y = ground - t.y.coerceIn(0f, 1f) * (ground - TOP)
            val pen = Pen(ink, x, y, SCALE * t.size.coerceIn(0.5f, 2f), t.flip)
            if (figure) InkFigures.draw(pen, t.id, t.gold) else InkProps.draw(pen, t.id, t.gold, p.night)
        }
        frame(c, seed)
    }

    /** Warm paper: its tone a little uneven, a fleck here and there. */
    private fun paper(c: PixelCanvas, seed: Int, x0: Int, y0: Int, x1: Int, y1: Int) {
        for (y in y0 until y1) for (x in x0 until x1) {
            val n = Noise.v2(x / 7f, y / 7f, seed + 5)
            var col = Col.mix(InkPal.PAPER, InkPal.PAPER_SHADE, n * 0.55f)
            if (Math.floorMod(Noise.hash(x, y, seed + 9), 61) == 0) col = Col.mix(col, InkPal.SOFT, 0.35f)
            c.set(x, y, col)
        }
    }

    /** The margin cleared back to paper, and the rule round the picture. */
    private fun frame(c: PixelCanvas, seed: Int) {
        val w = c.width; val h = c.height
        paper(c, seed, 0, 0, w, MARGIN)
        paper(c, seed, 0, h - MARGIN, w, h)
        paper(c, seed, 0, 0, MARGIN, h)
        paper(c, seed, w - MARGIN, 0, w, h)
        c.hline(MARGIN, w - 1 - MARGIN, MARGIN, InkPal.INK)
        c.hline(MARGIN, w - 1 - MARGIN, h - 1 - MARGIN, InkPal.INK)
        c.vline(MARGIN, MARGIN, h - 1 - MARGIN, InkPal.INK)
        c.vline(w - 1 - MARGIN, MARGIN, h - 1 - MARGIN, InkPal.INK)
        c.hline(MARGIN - 2, w + 1 - MARGIN, MARGIN - 2, InkPal.SOFT)
        c.hline(MARGIN - 2, w + 1 - MARGIN, h + 1 - MARGIN, InkPal.SOFT)
        c.vline(MARGIN - 2, MARGIN - 2, h + 1 - MARGIN, InkPal.SOFT)
        c.vline(w + 1 - MARGIN, MARGIN - 2, h + 1 - MARGIN, InkPal.SOFT)
    }
}
