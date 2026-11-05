package si.lanisce.lani.ui.scene

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.l10n.bi

/**
 * The "▾" at the foot of a turn and of an exercise's item: there while what scrolls goes on below, a tap brings the rest up, and it
 * goes at the end. An item's last row ("Senza microfono") can lie wholly below the fold with nothing else to say so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MoreBelowTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var scroll: ScrollState

    private fun page(content: () -> Int) {
        compose.setContent {
            scroll = rememberScrollState()
            Box(Modifier.size(300.dp, 400.dp)) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) { Spacer(Modifier.height(content().dp)) }
                MoreBelow(scroll, Modifier.align(Alignment.BottomEnd))
            }
        }
        compose.waitForIdle()
    }

    private fun cue() = compose.onNodeWithContentDescription(bi("progressScreen.more"))

    @Test fun `what fits shows no cue`() {
        page { 300 }
        cue().assertDoesNotExist()
    }

    @Test fun `what goes on below shows it, and taps bring the end up, where it goes`() {
        page { 1000 }
        cue().assertExists()
        assertEquals("nothing moved yet", 0, scroll.value)
        cue().performClick()
        compose.waitForIdle()
        assertTrue("a tap moved it down: ${scroll.value}", scroll.value > 0)
        while (scroll.canScrollForward) {
            cue().performClick()
            compose.waitForIdle()
        }
        cue().assertDoesNotExist()
    }

    @Test fun `a page that fits doesn't flash it on its first frame, before its end is measured`() {
        compose.mainClock.autoAdvance = false
        page { 300 }
        compose.mainClock.advanceTimeByFrame()
        cue().assertDoesNotExist()
    }

    @Test fun `what stops fitting shows it again`() {
        var tall by mutableStateOf(300)
        page { tall }
        cue().assertDoesNotExist()
        tall = 1000
        compose.waitForIdle()
        cue().assertExists()
    }
}
