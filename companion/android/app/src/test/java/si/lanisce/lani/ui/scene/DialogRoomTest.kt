package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.ui.scene.DialogRoom.LINES_MIN_DP
import si.lanisce.lani.ui.scene.DialogRoom.STRIP_DP
import si.lanisce.lani.ui.scene.DialogRoom.TURN_MIN_DP
import kotlin.math.min

/**
 * The room a dialog's typed turn has with the keyboard up (DialogRoom; the learner's report: "the keyboard goes over the
 * whole bottom part"). In dp (density 1): phones from small to tall, their status bar and keyboard (with the navigation
 * bar under it), the panel's header, and a typed turn as SceneDialog's TypeTurn lays it out.
 */
class DialogRoomTest {
    /** A phone: its size, its status bar, and the keyboard's height from the bottom (the navigation bar included). */
    private data class Phone(val name: String, val width: Int, val height: Int, val status: Int, val keyboard: Int)

    private val phones = listOf(
        Phone("small 360×640", 360, 640, 24, 308),
        Phone("Nexus 5-ish 360×732", 360, 732, 24, 300),
        Phone("mid 360×800", 360, 800, 24, 320),
        Phone("Pixel 4a-ish 393×851", 393, 851, 28, 324),
        Phone("QA's emulator 411×914", 411, 914, 52, 330),
    )

    /** The panel's header: the person's face (44 dp) and name, "👁 Prevod", ✕; 10 dp over it. */
    private val header = 64

    // A typed turn (TypeTurn) at the normal font size, top to bottom, 8 dp apart, 10 dp padding over and under it.
    private val padding = 10
    private val gap = 8
    private val title = 48 // "✍️ Napiši manjkajočo besedo" with "📖 Namig" beside it (a 48 dp chip)
    private val intent = 20 // "Hočeš reči · You want to say: …"
    private val sentence = 48 // the sentence with the gap
    private val field = 56
    private val chips = 48 // č š ž Č Š Ž
    private val check = 56 // "✋ Izberi raje" beside "Preveri · Check"
    private val turn = padding + title + gap + intent + gap + sentence + gap + field + gap + chips + gap + check + padding

    /** Its end, scrolled down to (KeepAtEnd): the field, the chips and Check, under the padding. */
    private val end = field + gap + chips + gap + check + padding

    /** The end with the sentence over it. */
    private val sentenceToEnd = sentence + gap + end

    private fun picture(p: Phone) = min(p.height * 52 / 100, p.width * 82 / 100) // the scene's picture, as SceneScreen sizes it

    /** Where the turn ends up on [p], with the keyboard up: its top and height, the lines' height. */
    private data class Placed(val panelTop: Int, val body: Int, val turnTop: Int, val turnHeight: Int, val lines: Int)

    private fun place(p: Phone, keyboard: Boolean = true): Placed {
        val top = DialogRoom.panelTop(picture(p), p.status, STRIP_DP, keyboard)
        val bottom = p.height - p.keyboard // imePadding: the panel's content ends at the keyboard's top
        val body = bottom - top - header
        val t = min(turn, DialogRoom.turnMax(body, LINES_MIN_DP, TURN_MIN_DP))
        val lines = DialogRoom.linesHeight(body, t)
        return Placed(top, body, top + header + lines, t, lines)
    }

    @Test fun `a typed turn's field, chips and Check are above the keyboard on every phone, the sentence with the gap too`() {
        for (p in phones) {
            val at = place(p)
            val keyboardTop = p.height - p.keyboard
            assertEquals("${p.name}: the turn ends at the keyboard's top", keyboardTop, at.turnTop + at.turnHeight)
            assertTrue("${p.name}: the turn starts under the header", at.turnTop >= at.panelTop + header)
            // scrolled to its end, what shows of the turn holds the field, the chips and Check
            assertTrue("${p.name}: field, chips and Check fit (${at.turnHeight} dp for $end)", at.turnHeight >= end)
            // and the gap's sentence over them, but on the smallest phone (a few dp of it under the header there)
            if (p.height >= 732) assertTrue("${p.name}: the sentence with the gap fits too (${at.turnHeight} dp for $sentenceToEnd)", at.turnHeight >= sentenceToEnd)
            else assertTrue("${p.name}: most of the sentence with the gap", at.turnHeight >= sentenceToEnd - sentence / 4)
        }
    }

    @Test fun `on a phone of usual size the last line said shows over the turn, and on a tall one the turn needs no scrolling`() {
        for (p in phones.filter { it.height >= 732 }) {
            assertTrue("${p.name}: the last line said (${place(p).lines} dp)", place(p).lines >= LINES_MIN_DP)
        }
        for (p in phones.filter { it.height >= 851 }) {
            assertEquals("${p.name}: the whole turn shows", turn, place(p).turnHeight)
        }
    }

    @Test fun `with the picture staying the keyboard would leave no room for the turn, which is why it gives way`() {
        val tall = phones.last()
        val stays = place(tall, keyboard = false)
        assertEquals(picture(tall), stays.panelTop)
        assertTrue("the panel's body under the picture (${stays.body} dp) is less than the turn's end", stays.body < sentenceToEnd)
    }

    @Test fun `the panel rises to a strip under the status bar while the keyboard is up, never lower than the picture's foot`() {
        assertEquals(337, DialogRoom.panelTop(337, 52, STRIP_DP, keyboard = false))
        assertEquals(52 + STRIP_DP, DialogRoom.panelTop(337, 52, STRIP_DP, keyboard = true))
        assertEquals(40, DialogRoom.panelTop(40, 52, STRIP_DP, keyboard = true)) // a picture shorter still: where it ends
    }

    @Test fun `the turn takes what it needs, the lines keep the last line, and a tight panel goes to the turn`() {
        // roomy: all but the lines' least
        assertEquals(500 - LINES_MIN_DP, DialogRoom.turnMax(500, LINES_MIN_DP, TURN_MIN_DP))
        // tight: the turn keeps its least, the lines what's left
        assertEquals(TURN_MIN_DP, DialogRoom.turnMax(TURN_MIN_DP + 20, LINES_MIN_DP, TURN_MIN_DP))
        assertEquals(20, DialogRoom.linesHeight(TURN_MIN_DP + 20, TURN_MIN_DP))
        // tighter than the turn's least: all of it
        assertEquals(180, DialogRoom.turnMax(180, LINES_MIN_DP, TURN_MIN_DP))
        assertEquals(0, DialogRoom.linesHeight(180, 180))
        assertEquals(0, DialogRoom.turnMax(0, LINES_MIN_DP, TURN_MIN_DP))
    }
}
