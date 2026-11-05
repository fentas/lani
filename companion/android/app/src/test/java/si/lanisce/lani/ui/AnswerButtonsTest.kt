package si.lanisce.lani.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.ui.scene.TypedTurnButtons

/**
 * The two rows of a [WayOnRow], under an answer ([AnswerButtons]) and under a dialog's typed turn ([TypedTurnButtons]), in every pair of
 * the four languages, on rows from a very small phone's to a phone's, in real text (the native graphics mode measures it): "Avanti ·
 * Weiter" broke inside its words on a small phone, and "Preveri · Check" in all twelve pairs, in six down to a letter a line.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AnswerButtonsTest {
    @get:Rule val compose = createComposeRule()

    private var row by mutableIntStateOf(320)
    private var fontScale by mutableFloatStateOf(1f)
    private var labels by mutableStateOf("" to "")
    private var typed by mutableStateOf(false)
    private var chose = 0

    private fun show() = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            Box(Modifier.width(row.dp)) {
                if (typed) TypedTurnButtons(labels.first, labels.second, true, { chose++ }, {})
                else AnswerButtons(labels.first, labels.second, Color.Green, {}, {})
            }
        }
    }

    private fun text(key: String, target: Lang, base: Lang) = "${L10n.text(target, key)} · ${L10n.text(base, key)}"

    private fun pairs() = Lang.entries.flatMap { t -> Lang.entries.filter { it != t }.map { b -> t to b } }

    /** The lines of [label] as they are laid out. */
    private fun lines(label: String): List<String> {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        val r = results.single()
        return (0 until r.lineCount).map { label.substring(r.getLineStart(it), r.getLineEnd(it)) }
    }

    private fun bounds(label: String) = compose.onNodeWithText(label, useUnmergedTree = true).getBoundsInRoot()

    /** Whether [label] is on the screen: the row keeps the other Ask composed, unplaced. */
    private fun shows(label: String) = compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }

    /** Every pair on every row: no word of [emoji] + the first key's label, or of the second's, in pieces; the first's name still there. */
    private fun wholeEverywhere(firstKey: String, secondKey: String, emoji: String) {
        assertEquals("twelve pairs", 12, pairs().size)
        show()
        for ((target, base) in pairs()) for (width in listOf(200, 240, 288, 320, 328, 340, 379, 380)) for (scale in listOf(1f, 1.3f, 1.6f)) {
            val first = text(firstKey, target, base)
            val second = text(secondKey, target, base)
            labels = first to second
            row = width
            fontScale = scale
            compose.waitForIdle()
            for (label in listOf("$emoji $first", second).filter(::shows)) {
                val words = label.split(' ').filter { it.isNotBlank() }
                val broken = lines(label).flatMap { it.split(' ') }.filter { it.isNotBlank() && it !in words }
                assertTrue("$label on a row of $width dp at ${scale}x: ${lines(label)}", broken.isEmpty())
            }
            assertTrue("the way on is there", shows(second))
            compose.onNodeWithContentDescription(first, useUnmergedTree = true).let { if (!shows("$emoji $first")) it.assertExists() }
        }
    }

    @Test fun `no word of the two labels breaks, in any pair of the languages, on any row, in large text too`() =
        wholeEverywhere("playerScreen.ask", "common.next", "💬")

    @Test fun `no word under a typed turn breaks, in any pair of the languages, on any row, in large text too`() {
        typed = true
        wholeEverywhere("adaptive.letMeChoose", "playerScreen.check", "✋")
    }

    @Test fun `on a small phone a typed turn keeps Check whole on one line, its hand beside it, in the longest pair`() {
        typed = true
        show()
        labels = text("adaptive.letMeChoose", Lang.IT, Lang.DE) to text("playerScreen.check", Lang.IT, Lang.DE)
        row = 328
        compose.waitForIdle()
        assertFalse("no words on the hand", shows("✋ ${labels.first}"))
        assertEquals("on one line", 1, lines(labels.second).size)
        val hand = compose.onNodeWithContentDescription(labels.first, useUnmergedTree = true).assertExists().getBoundsInRoot()
        val check = bounds(labels.second)
        assertTrue("in the row: $hand, $check", check.left >= hand.right && check.top < hand.bottom)
    }

    @Test fun `the hand alone still brings the choices back`() {
        typed = true
        show()
        labels = "Fammi scegliere · Lass mich wählen" to "Controlla · Prüfen"
        row = 328
        compose.waitForIdle()
        compose.onNodeWithContentDescription(labels.first).performClick()
        compose.waitForIdle()
        assertEquals(1, chose)
    }

    @Test fun `where there is room a typed turn keeps the words of Let me choose`() {
        typed = true
        show()
        labels = "Fammi scegliere · Lass mich wählen" to "Controlla · Prüfen"
        row = 600
        compose.waitForIdle()
        val choose = bounds("✋ ${labels.first}")
        val check = bounds(labels.second)
        assertTrue("beside it: $choose, $check", check.left >= choose.right && check.top < choose.bottom)
    }

    @Test fun `a short pair keeps both labels side by side on a phone, the way on taking the room left`() {
        show()
        labels = "Vprašaj · Ask" to "Naprej · Next"
        row = 340
        compose.waitForIdle()
        val ask = bounds("💬 ${labels.first}")
        val next = bounds(labels.second)
        assertTrue("beside it: $ask, $next", next.left >= ask.right && next.top < ask.bottom)
        assertEquals("on one line", 1, lines(labels.second).size)
    }

    @Test fun `where a word would break Ask gives up its words and stays in the row, its name still said`() {
        show()
        labels = "Chiedi · Fragen" to "Avanti · Weiter"
        row = 288
        fontScale = 1.3f
        compose.waitForIdle()
        assertFalse("no words on Ask", shows("💬 ${labels.first}"))
        val ask = compose.onNodeWithContentDescription(labels.first, useUnmergedTree = true).assertExists().getBoundsInRoot()
        val next = bounds(labels.second)
        assertTrue("in the row: $ask, $next", next.left >= ask.right && next.top < ask.bottom)
    }

    @Test fun `on a tiny row in large text they stand one above the other`() {
        show()
        labels = "Chiedi · Fragen" to "Avanti · Weiter"
        row = 130
        fontScale = 1.6f
        compose.waitForIdle()
        val ask = bounds("💬 ${labels.first}")
        val next = bounds(labels.second)
        assertTrue("under it: $ask, $next", next.top >= ask.bottom)
    }
}
