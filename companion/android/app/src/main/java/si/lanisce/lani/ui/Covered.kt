package si.lanisce.lani.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import si.lanisce.lani.NavEntry
import si.lanisce.lani.Screen

/**
 * Whether the screen around this is kept composed under another one on top of it ([ScreenStack]: Home under the screens
 * opened from it; docs/plans/05-built/nav-perf.md). While it is, nothing of it is measured, placed, drawn, touched or
 * read by TalkBack, and what would run by itself in it waits: the header village's clock, the tickers ([si.lanisce.lani.
 * ui.game.rememberNow]), a tip's being shown, its sheets. Back on top, it is there as it was, nothing composed again.
 */
val LocalCovered = compositionLocalOf { false }

/** How long after a covered screen shows again what it works out anew waits ([rememberAway]): its first frames first. */
const val SHOWN_SETTLE_MS = 300L

/**
 * True while the screen is covered ([LocalCovered]) and for [SHOWN_SETTLE_MS] after it shows again: what it works out
 * anew by the clock or from what changed meanwhile ([si.lanisce.lani.ui.game.rememberNow], a tip's facts) waits until it
 * is on view as it was, so the Back's frame only shows it (2026-10-30: on the emulator the minute's work on Home took up
 * to 57 ms of that frame). False from the start for a screen never covered.
 */
@Composable
fun rememberAway(): Boolean {
    val covered = LocalCovered.current
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(covered) {
        if (covered) {
            settling = true
        } else if (settling) {
            delay(SHOWN_SETTLE_MS)
            settling = false
        }
    }
    return covered || settling
}

/**
 * [content] composed, and while [covered] kept as it is out of the way: neither measured nor placed (nothing drawn, nothing
 * to touch), no semantics (TalkBack and QA's dumps don't see it), its BackHandlers on a dispatcher nobody presses (the
 * screen on top has Back), [LocalCovered] true for what would run by itself in it. [shown]: placed and drawn all the same,
 * behind a back gesture's shrinking screen, still covered for the rest.
 */
@Composable
fun Covered(covered: Boolean, shown: Boolean = !covered, content: @Composable () -> Unit) {
    // Back's dispatchers of its own, off while covered: a BackHandler in the covered screen never takes Back from the one
    // on top (BackHandler registers with the navigation events' dispatcher, else with the activity's OnBackPressedDispatcher)
    val events = LocalNavigationEventDispatcherOwner.current?.let { rememberNavigationEventDispatcherOwner(enabled = !covered, parent = it) }
    val real = LocalOnBackPressedDispatcherOwner.current
    val idle = remember(real) {
        real?.let { owner ->
            object : OnBackPressedDispatcherOwner {
                override val onBackPressedDispatcher = OnBackPressedDispatcher()
                override val lifecycle get() = owner.lifecycle
            }
        }
    }
    Layout(
        content = {
            val provided = listOfNotNull(
                LocalCovered provides covered,
                events?.let { LocalNavigationEventDispatcherOwner provides it },
                if (real != null && idle != null) LocalOnBackPressedDispatcherOwner provides if (covered) idle else real else null,
            )
            CompositionLocalProvider(*provided.toTypedArray(), content = content)
        },
        modifier = if (covered) Modifier.clearAndSetSemantics {} else Modifier,
    ) { measurables, constraints ->
        if (!shown) {
            layout(constraints.maxWidth, constraints.maxHeight) {}
        } else {
            val placeables = measurables.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) { placeables.forEach { it.place(0, 0) } }
        }
    }
}

/**
 * The back stack's screens as composed (MainActivity, docs/plans/05-built/nav-perf.md): the top one, each under its
 * entry's id ([states]: a list's scroll, a section open, kept while another is on top and across the process's death),
 * and [kept] (Home) composed all the time while it is the stack's root, [Covered] under the screens opened from it: Back
 * to it shows it as it was, without composing it again (62 ms of the main thread on the emulator, every Back, before).
 * Every other screen below the top is its remembered state only, composed again when Back returns to it. [peek]: Back
 * is being swiped (its progress), the screen below drawn behind the shrinking top when it is the kept one; [top] the
 * top screen's look meanwhile (it shrinks), [behind] what is drawn between them (the scrim).
 */
@Composable
fun ScreenStack(
    entries: List<NavEntry>,
    states: SaveableStateHolder,
    kept: Screen = Screen.Home,
    peek: Float = 0f,
    top: Modifier = Modifier,
    behind: @Composable () -> Unit = {},
    screen: @Composable BoxScope.(Screen) -> Unit,
) {
    val entry = entries.last()
    val root = entries.first().takeIf { it.screen == kept }
    // the kept screen first, always at this place in the composition: on top or under another, the same composition
    if (root != null) {
        val under = root.id != entry.id
        Covered(covered = under, shown = !under || (peek > 0f && entries.size == 2)) {
            states.SaveableStateProvider(root.id) { key(root.id) { Box(Modifier.fillMaxSize()) { screen(root.screen) } } }
        }
    }
    if (root?.id != entry.id) {
        behind()
        Box(Modifier.fillMaxSize().then(top)) {
            states.SaveableStateProvider(entry.id) { key(entry.id) { screen(entry.screen) } }
        }
    }
}
