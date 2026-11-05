package si.lanisce.lani.ui.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import si.lanisce.lani.l10n.bi

/**
 * A dialog's header on the room its title and the translations' chip share, in real text: 180 dp is what a small phone leaves them
 * beside the portrait, the ⋯ and the ✕ (about 182 in the shots, read off at 320 dpi). There "Oma Resi wäscht am Bach · …" had 40 dp beside "👁 Übersetzung", thirteen lines of a word
 * or less, and with the keyboard up a typed turn's field was out of sight.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DialogHeaderTest {
    @get:Rule val compose = createComposeRule()

    private val title = "Oma Resi wäscht am Bach · Oma Resi does the washing at the stream"
    private var row by mutableIntStateOf(180)
    private var fontScale by mutableFloatStateOf(1f)

    private fun show() = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            Box(Modifier.width(row.dp)) { TitleAndToggle("Oma Resi", title, true, {}) }
        }
    }

    private fun lines(): List<String> {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        val r = results.single()
        return (0 until r.lineCount).map { title.substring(r.getLineStart(it), r.getLineEnd(it)) }
    }

    /** The width of the chip's label as it stands (the other chip is composed, unplaced): a 👁 alone, or with its word. */
    private fun labelWidth(): Dp = compose.onAllNodesWithContentDescription(bi("reading.translation"), useUnmergedTree = true)
        .fetchSemanticsNodes().single { it.layoutInfo.isPlaced }.let { n -> with(compose.density) { n.size.width.toDp() } }

    @Test fun `no word of the title breaks, on any row, in large text too`() {
        show()
        val words = title.split(' ')
        for (width in listOf(120, 150, 180, 220, 260, 320)) for (scale in listOf(1f, 1.3f, 1.6f)) {
            row = width
            fontScale = scale
            compose.waitForIdle()
            val broken = lines().flatMap { it.split(' ') }.filter { it.isNotBlank() && it !in words }
            assertTrue("on a row of $width dp at ${scale}x: ${lines()}", broken.isEmpty())
        }
    }

    @Test fun `on a small phone the chip gives up its word and the title stays a few lines, its name still said`() {
        show()
        row = 180
        compose.waitForIdle()
        assertTrue("a few lines: ${lines()}", lines().size <= 6)
        assertTrue("its 👁 alone: ${labelWidth()}", labelWidth() < 30.dp)
    }

    @Test fun `with room for both the chip keeps its word, the title at the start of the row`() {
        show()
        row = 300
        compose.waitForIdle()
        assertTrue("its word: ${labelWidth()}", labelWidth() > 40.dp)
        val t = compose.onNodeWithText(title, useUnmergedTree = true).getBoundsInRoot()
        assertEquals("at the start", 0.dp, t.left)
    }
}
