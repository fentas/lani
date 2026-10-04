package si.lanisce.lani.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.render.VillageScene
import si.lanisce.lani.l10n.bi

/**
 * The compact village behind the Home header, with a scrim so the greeting and pills stay readable at the
 * top and the village chip at the bottom. Tapping it opens the village.
 */
@Composable
fun HeaderVillage(state: GameState, celebrate: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.clickable(onClickLabel = bi("common.openVillage"), onClick = onOpen)) {
        VillageScene(state, Modifier.fillMaxSize(), compact = true, celebrate = celebrate)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.6f),
                    0.5f to Color.Black.copy(alpha = 0.2f),
                    0.8f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.25f),
                ),
            ),
        )
    }
}
