package si.lanisce.lani.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.clickable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.NavStack
import si.lanisce.lani.Screen
import si.lanisce.lani.ui.game.rememberNow

/**
 * The back stack's screens as composed (ui/Covered.kt, docs/plans/05-built/nav-perf.md): Home kept composed under the
 * screens opened from it, out of the way while covered (nothing of it shown, touched or found by TalkBack, its Back taken
 * by nobody, its tickers stopped), and on top again as it was, nothing composed anew; any other screen below the top is
 * its remembered state only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreenStackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** How many times each screen's composition began and ended (a remembered observer of it). */
    private val began = mutableMapOf<String, Int>()
    private val ended = mutableMapOf<String, Int>()
    private val backs = mutableListOf<String>()
    private var homeCovered: Boolean? = null
    private var homePlaced = 0

    private fun name(s: Screen) = s.javaClass.simpleName

    private inner class Life(val n: String) : RememberObserver {
        override fun onRemembered() { began[n] = (began[n] ?: 0) + 1 }
        override fun onForgotten() { ended[n] = (ended[n] ?: 0) + 1 }
        override fun onAbandoned() = Unit
    }

    @Composable
    private fun Screens(nav: NavStack, peek: Float = 0f) {
        val states = rememberSaveableStateHolder()
        ScreenStack(nav.entries, states, peek = peek) { s ->
            val n = name(s)
            remember(n) { Life(n) }
            Box(Modifier.fillMaxSize().then(if (s == Screen.Home) Modifier.onPlaced { homePlaced++ } else Modifier)) {
                var taps by rememberSaveable { mutableIntStateOf(0) }
                Text("$n $taps", Modifier.clickable { taps++ })
                if (s == Screen.Home) {
                    homeCovered = LocalCovered.current
                    BackHandler { backs += "home" }
                }
            }
        }
    }

    private fun home(): NavStack = NavStack(Screen.Home)

    @Test fun `Home is kept under the screens opened from it and comes back as it was`() {
        val nav = home()
        compose.setContent { Screens(nav) }
        compose.onNodeWithText("Home 0").performClick()
        compose.onNodeWithText("Home 1").assertExists()
        compose.runOnIdle { nav.go(Screen.Village) }
        compose.onNodeWithText("Village 0").assertExists()
        // under the village: composed still, not shown nor found
        compose.onNodeWithText("Home 1").assertDoesNotExist()
        assertEquals(1, began["Home"])
        assertEquals(null, ended["Home"])
        assertEquals(true, homeCovered)
        compose.runOnIdle { nav.go(Screen.Scene("ob-ognju")) }
        compose.onNodeWithText("Scene 0").assertExists()
        assertEquals("a screen below the top other than Home is its state only", 1, ended["Village"])
        compose.runOnIdle { nav.back() }
        compose.onNodeWithText("Village 0").assertExists()
        compose.runOnIdle { nav.back() }
        compose.onNodeWithText("Home 1").assertExists()
        assertEquals("Home was never composed again", 1, began["Home"])
        assertEquals(null, ended["Home"])
        assertEquals(false, homeCovered)
        assertEquals(2, began["Village"])
    }

    @Test fun `a covered Home takes no Back, its own Back works again once on top`() {
        val nav = home()
        compose.setContent { Screens(nav) }
        compose.runOnIdle { nav.go(Screen.Settings()) }
        compose.onNodeWithText("Settings 0").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertTrue("Home's BackHandler under Settings took Back: $backs", backs.isEmpty()) }
        compose.runOnIdle { nav.back() }
        compose.onNodeWithText("Home 0").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(listOf("home"), backs) }
    }

    @Test fun `a covered screen's clock stops, and says the time at once when it shows again`() {
        var covered by mutableStateOf(false)
        var now = 0L
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalCovered provides covered) { now = rememberNow(3_600_000) }
        }
        val first = compose.runOnIdle { now }
        compose.runOnIdle { covered = true }
        Thread.sleep(20)
        compose.waitForIdle()
        assertEquals("nothing while covered", first, now)
        compose.runOnIdle { covered = false }
        compose.waitForIdle()
        assertTrue("the time now as it shows again: $first, then $now", now > first)
    }

    @Test fun `behind the back gesture Home is drawn, still covered`() {
        val nav = home()
        var peek by mutableStateOf(0f)
        compose.setContent { Screens(nav, peek) }
        compose.runOnIdle { nav.go(Screen.Friends) }
        compose.waitForIdle()
        val before = homePlaced
        compose.runOnIdle { peek = 0.4f }
        compose.waitForIdle()
        assertTrue("placed (drawn) behind the shrinking screen", homePlaced > before)
        // but out of TalkBack's reach, and its tickers and tips still wait
        compose.onNodeWithText("Home 0").assertDoesNotExist()
        assertEquals(true, homeCovered)
    }

    @Test fun `a stack whose root isn't Home keeps nothing`() {
        val nav = NavStack(Screen.Setup)
        compose.setContent { Screens(nav) }
        compose.runOnIdle { nav.go(Screen.Settings("connection")) }
        compose.onNodeWithText("Settings 0").assertExists()
        assertEquals(1, ended["Setup"])
    }
}
