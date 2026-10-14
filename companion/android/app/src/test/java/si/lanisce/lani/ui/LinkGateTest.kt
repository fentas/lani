package si.lanisce.lani.ui

import android.app.Application
import android.content.Intent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import si.lanisce.lani.l10n.bi

/**
 * A tap on a link in the tutor's Markdown (README "Tutor chat", "Links"): fran.si opens at once in the browser; any
 * other asks first with its domain plainly ("Open example.com?"), and opens only on "Odpri · Open"; http says it isn't
 * secure; without a browser the app says so and goes on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LinkGateTest {
    @get:Rule val compose = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun show(md: String) = compose.setContent { LinkGate { Markdown(md) } }

    private fun started(): Intent? = shadowOf(app).nextStartedActivity

    private fun asks(host: String) = compose.onNodeWithText("🔗 ${bi("links.openTitle", "host" to host)}")

    @Test fun `a link to fran-si opens at once in the browser`() {
        show("[Fran](https://fran.si/iskanje?Query=hiša)")
        compose.onNodeWithText("Fran").performClick()
        compose.waitForIdle()
        val view = started()!!
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("https://fran.si/iskanje?Query=hiša", view.dataString)
        assertTrue(view.hasCategory(Intent.CATEGORY_BROWSABLE))
        asks("fran.si").assertDoesNotExist()
    }

    @Test fun `any other link asks first, its domain shown, and opens only on Open`() {
        show("https://example.com/page")
        compose.onNodeWithText("https://example.com/page").performClick()
        compose.waitForIdle()
        assertNull("nothing opened before the learner said so", started())
        asks("example.com").assertExists()
        compose.onNodeWithText("⚠️", substring = true).assertDoesNotExist()
        compose.onNodeWithText(bi("links.cancel")).performClick()
        compose.waitForIdle()
        asks("example.com").assertDoesNotExist()
        assertNull("cancelled: nothing opened", started())

        compose.onNodeWithText("https://example.com/page").performClick()
        compose.onNodeWithText(bi("links.open")).performClick()
        compose.waitForIdle()
        assertEquals("https://example.com/page", started()!!.dataString)
        asks("example.com").assertDoesNotExist()
    }

    @Test fun `in a window of its own (a sheet not of the app's own) the Markdown's link still asks first`() {
        // the platform gives such a window its own link handler, which opened example.com at once (QA, 0.2.177)
        compose.setContent { LinkGate { ModalBottomSheet(onDismissRequest = {}) { Markdown("[Example](https://example.com/in-a-window)") } } }
        compose.onNodeWithText("Example").performClick()
        compose.waitForIdle()
        assertNull("nothing opened before the learner said so", started())
        asks("example.com").assertExists()
    }

    @Test fun `in a sheet, a tutor text drawn without the Markdown block asks first too`() {
        compose.setContent { LinkGate { LaniSheet(onDismissRequest = {}) { Text(inlineMarkdown("[Example](https://example.com/in-a-sheet)")) } } }
        compose.onNodeWithText("Example").performClick()
        compose.waitForIdle()
        assertNull(started())
        asks("example.com").assertExists()
    }

    @Test fun `a link whose text names fran-si but goes elsewhere asks for where it goes`() {
        show("[https://fran.si](https://example.com/x)")
        compose.onNodeWithText("https://fran.si").performClick()
        compose.waitForIdle()
        assertNull(started())
        asks("example.com").assertExists()
    }

    @Test fun `http asks too, and says it isn't secure`() {
        show("[Fran](http://fran.si/)")
        compose.onNodeWithText("Fran").performClick()
        compose.waitForIdle()
        assertNull(started())
        asks("fran.si").assertExists()
        compose.onNodeWithText("⚠️ ${bi("links.notSecure")}").assertExists()
    }

    @Test fun `without a browser the app says so, and nothing breaks`() {
        shadowOf(app).checkActivities(true)
        try {
            show("[Fran](https://fran.si/)")
            compose.onNodeWithText("Fran").performClick()
            compose.waitForIdle()
            assertEquals("🔗 ${bi("links.noBrowser")}", ShadowToast.getTextOfLatestToast())
        } finally {
            shadowOf(app).checkActivities(false)
        }
    }
}
