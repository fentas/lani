package si.lanisce.lani.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.data.Ambience
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.ambient.Place
import si.lanisce.lani.game.ambient.Soundscape
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.render.TownClock
import si.lanisce.lani.game.render.VillageLayout
import si.lanisce.lani.game.scene.Sky
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.game.TownCameraState
import java.time.LocalDate

/**
 * While this is on screen, the learner is at [place] under [sky]: its background sounds play ([Ambience]), and fade out
 * when it goes (unless the next screen has said where the learner is by then). [clock] is the picture's (its flashes and
 * its bells are timed on it, the thunder and the strikes too); none for a screen without one.
 */
@Composable
fun AmbientSound(ambience: Ambience, place: Place, sky: Sky = Sky.CLEAR, clock: TownClock? = null) {
    val owner = remember { Any() }
    SideEffect { ambience.show(owner, place, sky, clock) }
    DisposableEffect(ambience) { onDispose { ambience.leave(owner) } }
}

/**
 * The background sounds of [state]'s village while the town view shows it, at home and on a visit: its birds, crickets and
 * owl, its weather, the sea of its culture's horizon and of its coast, and on the picture's [clock] the thunder after each
 * flash of a storm. The coast's sea comes nearer as [cam]'s view comes onto the beach ([Soundscape.beachView], from the
 * canvas point in the middle of the view); it follows the camera here, in a composable of its own, so a pan recomposes
 * nothing else, and only when the level steps to its next quarter.
 */
@Composable
fun VillageSound(ambience: Ambience, state: GameState, cam: TownCameraState, clock: TownClock) {
    val land = VillageLayout.of(state)
    val beachView by remember(land, cam) {
        derivedStateOf {
            val rig = cam.rig ?: return@derivedStateOf 0f
            val c = cam.camera
            Soundscape.beachView(land, rig.canvasW, rig.canvasH, c.toCanvasX(rig.viewW / 2f), c.toCanvasY(rig.viewH / 2f))
        }
    }
    AmbientSound(
        ambience,
        Place.Village(
            sea = Cultures.current.world.backdrop?.sea == true,
            storm = state.event?.kind == EventKind.STORM,
            beach = land.sea != null,
            beachView = beachView,
        ),
        Soundscape.villageSky(state, LocalDate.now()),
        clock,
    )
}

/**
 * "🐦 Zvoki v ozadju · Background sounds": on or off, and their volume. Moving the slider plays the stream by the mill
 * for a few seconds, so the level can be heard where it's set.
 */
@Composable
fun AmbienceSettings(ambience: Ambience, modifier: Modifier = Modifier) {
    var moved by remember { mutableIntStateOf(0) }
    var hearing by remember { mutableIntStateOf(0) }
    LaunchedEffect(moved) {
        if (moved == 0) return@LaunchedEffect
        hearing = moved
        delay(PREVIEW_MS)
        hearing = 0
    }
    if (hearing != 0 && ambience.on) AmbientSound(ambience, Place.Scene("stream"))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("🐦 ${bi("ambience.title")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(
            Modifier.toggleable(value = ambience.on, role = Role.Switch, onValueChange = ambience::turn).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(bi("ambience.what"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = ambience.on, onCheckedChange = null)
        }
        val volume = bi("ambience.volume")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔈", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = ambience.volume,
                onValueChange = { ambience.setVolume(it, save = false); moved++ },
                onValueChangeFinished = { ambience.setVolume(ambience.volume) },
                enabled = ambience.on,
                modifier = Modifier.weight(1f).semantics { contentDescription = volume },
            )
            Text("🔊", style = MaterialTheme.typography.bodyLarge)
        }
        Text(bi("ambience.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How long the stream plays after the slider last moved. */
private const val PREVIEW_MS = 3_000L
