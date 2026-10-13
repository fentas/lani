package si.lanisce.lani.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.BuildOption
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState

/**
 * What can be built and upgraded in [s] (with the cast: a workplace whose person has nowhere to live waits), each upgrade
 * with the words it asks for ([AppViewModel.upgradeWords]): made again when the village, its people, the learner's words
 * or the scenes and packs those come from change.
 */
@Composable
fun rememberBuildOptions(vm: AppViewModel, s: GameState): List<BuildOption> =
    remember(s, vm.villagers.all, vm.content.dashboard, vm.scenes.packs, vm.scenes.all) {
        GameEngine.buildOptions(s, vm.villagers.all) { id -> vm.upgradeWords(s, id) }
    }

/** How far [s] is from its next age, with the learner's words as they stand. */
@Composable
fun rememberAdvanceCheck(vm: AppViewModel, s: GameState): AdvanceCheck =
    remember(s, vm.content.wordsKnown, vm.content.targetLevel, vm.content.wordsRusty) {
        GameEngine.advanceCheck(s, vm.content.wordsKnown, vm.content.targetLevel, vm.content.wordsRusty)
    }

/**
 * Building [b]'s card ([BuildingSheet]) wherever it opens, the village or the building's scene (companion/GAME.md,
 * "Upgrades"): the packs of its words on the phone first, the repair and the upgrade done here (a long buzz), its words
 * and "🎯 Vadi jih zdaj · Practise them now", whose run opens the card again where it was opened
 * ([AppViewModel.practiseUpgrade]). [options] and [check] are the village's ([rememberBuildOptions],
 * [rememberAdvanceCheck]); [onUpgraded] once the upgrade is done; [onPractise] as its words' run starts (the card
 * closes); [onAges] the next age's row; [onMove] as [BuildingSheet]'s.
 */
@Composable
fun BuildingCard(
    vm: AppViewModel, s: GameState, b: Building, options: List<BuildOption>, check: AdvanceCheck,
    onUpgraded: () -> Unit, onPractise: () -> Unit, onAges: () -> Unit, onDismiss: () -> Unit, onMove: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(b.id) { vm.loadBuildingWords(b.id) }
    BuildingSheet(
        s, b, options.firstOrNull { it.type == b.type }, check,
        onRepair = { moba -> if (vm.game.repair(b.id, moba)) haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
        onUpgrade = { moba ->
            if (vm.game.upgrade(b.id, moba)) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpgraded()
            }
        },
        onAges = onAges,
        onDismiss = onDismiss,
        onMove = onMove,
        words = vm.upgradeWords(s, b.id),
        onPractise = { onPractise(); vm.practiseUpgrade(b.id) },
        course = vm.school.gate(s, b.id),
        extra = { si.lanisce.lani.ui.school.SchoolOnCard(vm, s, b, onOpen = onDismiss) },
    )
}
