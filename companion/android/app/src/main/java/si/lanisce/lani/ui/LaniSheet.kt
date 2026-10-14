package si.lanisce.lani.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Every sheet of the app (a word's card, the tutor chat, the grammar book, a village sheet …): Material's
 * [ModalBottomSheet], always fully open (no half-open stop), that ends under the status bar.
 *
 * Why there: Material (1.4) pads a sheet's content by the part of the status bar the sheet covers, so a sheet in reach
 * of the status bar changes its height as it moves, and its resting place (its anchor) moves with it. A fling into the
 * end of a sheet's list hands what is left of its speed to the sheet, which springs past its place and back; whenever
 * the anchor moves meanwhile, the spring starts again from where the sheet is, with the fling's first speed. Up in the
 * status bar the anchor moved on every frame: the sheet bounced until the next touch (Jan, 0.2.107: "an endless
 * bouncing movement"). Here the sheet's room ends at the status bar and its content is padded only at the bottom (the
 * navigation bar, the keyboard), so its size doesn't depend on where it is: it springs once and rests. A sheet's
 * fillMaxHeight(f) is a share of that room (the status bar left out). LaniSheetTest flings a sheet into its end and
 * keeps every sheet of the app on this one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaniSheet(onDismissRequest: () -> Unit, containerColor: Color = BottomSheetDefaults.ContainerColor, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = containerColor,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    ) {
        // the sheet's window has a link handler of its own: the app's gate again (ui/Links.kt)
        val column = this
        GateLinks { column.content() }
    }
}
