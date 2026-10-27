package si.lanisce.lani.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.Screen
import si.lanisce.lani.inRun
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.settings.SettingsSection
import si.lanisce.lani.ui.villagers.VillagerLogic

/**
 * A screen's name, for the way back to it (docs/plans/05-built/navigation.md, "One way back"): its [emoji], [both]
 * languages ("Vas · Village") and the [target] language alone ("Vas"), where room is short (TalkBack still says [both]).
 */
data class ScreenName(val emoji: String, val both: String, val target: String)

/** [s]'s name as the way back to it names it: "Vas · Village", the scene's title, the villager's name, the unit's title. */
fun AppViewModel.nameOf(s: Screen): ScreenName {
    fun named(emoji: String, key: String) = ScreenName(emoji, bi(key), inTarget(key))
    fun plain(emoji: String, text: String) = ScreenName(emoji, text, text.substringBefore(" · "))
    return when (s) {
        Screen.Home -> named("🏠", "common.home")
        Screen.Village -> named("🏘️", "common.village")
        Screen.Packs -> named("📚", "homeScreen.newWords")
        Screen.Readings -> named("📗", "readings.title")
        is Screen.ReadAloud -> named("🎤", "readAloud.title")
        Screen.Talk -> named("🗣️", "homeScreen.talk")
        Screen.Progress -> named("📊", "common.progress")
        Screen.MyWords -> named("🔤", "myWords.title")
        is Screen.Family -> named("💌", "homeScreen.family")
        is Screen.Scene -> scenes.byId(s.id)?.let { plain(it.emoji, it.title) } ?: named("📍", "nav.scene")
        Screen.Villagers -> named("👥", "common.villagers")
        is Screen.Villager -> villagers.byId(s.id)?.let { v -> VillagerLogic.shortName(v.name).let { ScreenName(v.emoji, it, it) } }
            ?: named("👥", "common.villagers")
        Screen.Friends -> named("🤝", "towns.friends")
        is Screen.TownProgress -> towns.list?.towns?.firstOrNull { it.id == s.id }?.name?.takeIf { it.isNotBlank() }?.let { plain("📈", it) }
            ?: named("📈", "townProgress.title")
        is Screen.Visit, is Screen.VisitScene -> visits.visit?.name?.takeIf { it.isNotBlank() }?.let { plain("🧳", it) } ?: named("🧳", "common.village")
        is Screen.TownQuestion -> named("❓", "townProgress.question")
        Screen.Treasure -> named("🗺️", "treasure.title")
        Screen.School -> named("🏫", "school.title")
        is Screen.CourseUnit -> unitName(s.id) ?: named("🏫", "school.title")
        is Screen.UnitIntro -> unitName(s.id) ?: named("🏫", "school.title")
        Screen.Numbers -> named("🔢", "numbers.title")
        is Screen.Settings -> SettingsSection.of(s.section)?.let { named(it.emoji, it.key) } ?: named("⚙️", "common.settings")
        Screen.GrammarBook -> named("📖", "townScroll.grammar")
        // a run is never below another screen (Nav.kt); its title where one names it all the same
        else -> if (s.inRun) named("🎯", "nav.exercise") else named("‹", "components.back")
    }
}

/** A unit of the school's course by its title ("Biti: sem, si, je · To be: …"), the target's alone where room is short. */
private fun AppViewModel.unitName(id: String): ScreenName? = school.unit(id)?.let { u ->
    val pair = L10n.pair
    ScreenName(u.emoji, u.titleShown(pair), u.title[pair.target.code] ?: u.titleShown(pair).substringBefore(" · "))
}

/** The screen Back leads to from the one on view; Home at the root (a screen's own "‹" there leads Home). */
val AppViewModel.backTo: Screen get() = nav.below ?: Screen.Home

/** "‹ Vas · Village": the way back from the screen on view, named by the screen below it. */
val AppViewModel.backLabel: String get() = "‹ ${nameOf(backTo).both}"

/** "‹ Vas": [backLabel] in the target language alone, where room is short. */
val AppViewModel.backShort: String get() = "‹ ${nameOf(backTo).target}"

/**
 * The way back on a screen's header: "‹ " and the name of the screen below ([backLabel]); a tap goes there as the
 * system's Back does from the screen ([back]: [AppViewModel.back] unless the screen asks first, as Talk does).
 */
@Composable
fun BackButton(vm: AppViewModel, color: Color = Color.Unspecified, modifier: Modifier = Modifier, back: () -> Unit = vm::back) {
    val label = vm.backLabel
    TextButton(onClick = back, modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = label }) { Text(label, color = color) }
}

/**
 * What the system's Back does now, from a control on screen: the layer on top closes first (a card, a sheet, a step),
 * as a back gesture would (the HUD's "‹" over the village); null outside an activity (a preview).
 */
@Composable
fun systemBack(): () -> Unit {
    val owner = LocalOnBackPressedDispatcherOwner.current
    return { owner?.onBackPressedDispatcher?.onBackPressed() }
}

// --- the predictive back gesture (MainActivity's PredictiveBackHandler) ---------------------------------------------------

/** How the gesture's progress shows: quick at first, then slowly to its end (Material's predictive back's easing). */
private val BackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * The screen on view while a back gesture is held ([progress] 0..1, from the [edge] it began at): it shrinks to 90 % and
 * moves a little with the finger, its corners rounding, as Android's predictive back shows a screen going.
 */
fun predictiveBack(progress: Float, edge: Int): Modifier =
    if (progress <= 0f) Modifier else Modifier.graphicsLayer {
        val p = BackEasing.transform(progress)
        val scale = 1f - 0.1f * p
        scaleX = scale
        scaleY = scale
        translationX = (if (edge == BackEventCompat.EDGE_RIGHT) -1f else 1f) * 24.dp.toPx() * p
        shape = RoundedCornerShape((32 * p).dp)
        clip = true
        shadowElevation = 12.dp.toPx() * p
    }

/** Behind the shrinking screen: the dark it leaves. */
@Composable
fun BackScrim(progress: Float) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * progress.coerceIn(0f, 1f))))
}

/**
 * Over the shrinking screen, at the side the finger came from: where letting go leads, "‹ 🏘️ Vas · Village" (a pill of
 * the village's wood), coming in with the gesture.
 */
@Composable
fun BoxScope.BackPeek(vm: AppViewModel, progress: Float, edge: Int) {
    val p = BackEasing.transform(progress)
    val name = vm.nameOf(vm.backTo)
    val left = edge != BackEventCompat.EDGE_RIGHT
    Surface(
        color = Color(0xFFF1DDB0),
        contentColor = Color(0xFF3A2A16),
        shape = RoundedCornerShape(50),
        shadowElevation = 8.dp,
        // below the system's arrow at the edge, not on it
        modifier = Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .graphicsLayer {
                // each part faded as it's drawn: no layer's edge around the pill's shadow
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = p
                translationX = (if (left) 1f else -1f) * (-48 + 64 * p).dp.toPx()
                translationY = 88.dp.toPx()
            }
            .clearAndSetSemantics { },
    ) {
        Text(
            "‹ ${name.emoji} ${name.both}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}
