package si.lanisce.lani.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.os.Looper
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Duration

/**
 * A fling into the end of a sheet's list settles once ([LaniSheet]): the list hands what is left of the fling's speed
 * to the sheet, which springs past its place and comes back to rest. Material's own sheet, in reach of the status bar,
 * changes its height as it moves, its place moves with it, and its spring starts again each time with the fling's first
 * speed: it bounced until the next touch (Jan, 2026-10-12: "an endless bouncing movement"). The window is a phone's:
 * 1200 × 2700 px, a 144 px status bar and a 63 px navigation bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xxhdpi")
class LaniSheetTest {

    @Before fun frameByFrame() {
        // a frame every 16 ms as the clock goes on, not all at once
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(FRAME_MS))
    }

    private val canvas = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

    /** [n] frames; each draws the sheet's window, where Compose lays out what moved (Robolectric draws nothing itself). */
    private fun frames(n: Int) = repeat(n) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MS))
        ShadowDialog.getLatestDialog()?.window?.decorView?.draw(canvas)
    }

    /**
     * The top of a sheet's content (as tall as the chat's: 90 % of its room), frame by frame for 4 s after the list in it
     * hands the sheet a fast fling's leftover speed upwards; the first is where it rested before.
     */
    private fun tops(sheet: @Composable (content: @Composable () -> Unit) -> Unit): List<Float> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val list = NestedScrollDispatcher()
            var top = Float.NaN
            activity.get().setContent {
                sheet {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(0.9f)
                            .nestedScroll(remember { object : NestedScrollConnection {} }, list)
                            .onGloballyPositioned { top = it.positionInWindow().y },
                    )
                }
            }
            frames(30)
            ShadowDialog.getLatestDialog().window!!.decorView.dispatchApplyWindowInsets(
                WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 144, 0, 0))
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 63))
                    .build(),
            )
            frames(60)
            val rest = top
            list.coroutineScope.launch { list.dispatchPostFling(Velocity.Zero, Velocity(0f, -20_000f)) }
            return listOf(rest) + List(4000 / FRAME_MS.toInt()) { frames(1); top }
        } finally {
            activity.pause().stop().destroy()
        }
    }

    // One test for both sheets: Compose's main dispatcher keeps the first test's frame clock, which Robolectric stops
    // after it, so a second test with a sheet would get no frames.
    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun `a fling into the end of a sheet's list springs the sheet once, then it rests`() {
        // first Material's own sheet, as the phone showed it: this window and fling bring the bug about
        val material = tops { content ->
            ModalBottomSheet(onDismissRequest = {}, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { content() }
        }
        val lastSecond = material.takeLast(1000 / FRAME_MS.toInt())
        assertTrue(
            "Material's sheet still bounces 3 s after the fling (${lastSecond.min()} to ${lastSecond.max()} px). If it rests " +
                "now, Material fixed it, and LaniSheet could give the sheets the room under the status bar back",
            lastSecond.max() - lastSecond.min() > 100f,
        )

        val t = tops { content -> LaniSheet(onDismissRequest = {}) { content() } }
        val rest = t.first()
        assertTrue("the sheet is open, under the status bar: $rest", rest > 144f && rest < 1000f)
        assertTrue("it springs up with the fling's speed: ${t.min()} from $rest", t.min() < rest - 100f)
        assertEquals("after a second and a half it rests where it was, frame after frame", listOf(rest), t.takeLast(1500 / FRAME_MS.toInt()).distinct())
    }

    @Test fun `every sheet of the app is a LaniSheet`() {
        val sources = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val direct = sources.walkTopDown().filter { it.extension == "kt" && it.name != "LaniSheet.kt" }
            .filter { Regex("""\bModalBottomSheet\s*\(""").containsMatchIn(it.readText()) }
            .map { it.name }.toList()
        assertEquals("Material's ModalBottomSheet used directly, not through LaniSheet", emptyList<String>(), direct)
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
