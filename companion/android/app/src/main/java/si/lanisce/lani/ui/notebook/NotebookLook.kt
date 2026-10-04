package si.lanisce.lani.ui.notebook

import si.lanisce.lani.game.render.book.Sketch
import si.lanisce.lani.game.scene.StoryPicture

/** Where the story notebook opens: at [story]'s entry (a story it goes on from, or the story itself), else at its contents. */
data class NotebookAt(val story: String? = null)

/**
 * How the story notebook looks (companion/SCENES.md, "The story notebook"), in plain numbers the app ([NotebookSheet]) and
 * the render test share: a school exercise book's paper, faint blue rules a line of handwriting apart, a red margin, the
 * text in blue ink in a handwriting (Kalam), the translation under it in pencil, a sketch on a card taped or clipped in,
 * a little askew. The sizes are sp, so the handwriting and its rules grow with the phone's font scale.
 */
object NotebookLook {
    /** A ruled line: the pitch the handwriting keeps (sp). */
    const val LINE = 34f

    /** Where on a ruled line the handwriting sits, from the line's top (of [LINE]). */
    const val BASELINE = 0.74f

    /** The handwriting (sp): the text, the translation in pencil, a date, an evening's title, the story's title. */
    const val TEXT = 21f
    const val PENCIL = 16.5f
    const val DATE = 18f
    const val HEADING = 23f
    const val TITLE = 31f

    /** The margin's red line, from the page's left edge; the writing a little after it; the right edge (dp). */
    const val MARGIN = 44f
    const val GUTTER = 10f
    const val EDGE = 12f

    /** Room kept on the right of a paragraph for its 🔊 (and 👁) (dp). */
    const val TOOLS = 44f

    /** A sketch's card: as wide as this much of the writing's width. */
    const val CARD = 0.9f

    /** Paper, rules, margin, ink, pencil, tape and a card's shadow (ARGB); a warm night version for the dark theme. */
    class Pal(val paper: Long, val rule: Long, val margin: Long, val ink: Long, val pencil: Long, val tape: Long, val shadow: Long, val clip: Long)

    val DAY = Pal(
        paper = 0xFFFBF7EC, rule = 0xFFC3D3EA, margin = 0xFFE3A09B, ink = 0xFF23408E, pencil = 0xFF65625C,
        tape = 0xB0EDE0B8, shadow = 0x2E3A2A10, clip = 0xFF8C9096,
    )
    val NIGHT = Pal(
        paper = 0xFF27251F, rule = 0xFF3A4658, margin = 0xFF74403D, ink = 0xFFB9CCF3, pencil = 0xFFAFA89A,
        tape = 0x8CB5A983, shadow = 0x66000000, clip = 0xFFB9BEC6,
    )

    /** How a sketch is fastened to the page: with two strips of tape at its top corners, or a paper clip on its top edge. */
    enum class Fastening { TAPE, CLIP }

    /** A sketch's [tilt] (degrees), its [fastening], and where the clip sits across its top ([at], 0 left … 1 right). */
    data class Pin(val tilt: Float, val fastening: Fastening, val at: Float)

    /** How [p]'s sketch is pinned in: always the same for the same picture, each a little different. */
    fun pin(p: StoryPicture): Pin {
        val h = Sketch.seedOf(p)
        val tilt = TILTS[Math.floorMod(h, TILTS.size)]
        val fastening = if (Math.floorMod(h ushr 4, 3) == 0) Fastening.CLIP else Fastening.TAPE
        return Pin(tilt, fastening, if (Math.floorMod(h ushr 7, 2) == 0) 0.22f else 0.74f)
    }

    private val TILTS = floatArrayOf(-1.6f, -1.1f, -0.6f, 0.8f, 1.2f, 1.7f)
}
