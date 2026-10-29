package si.lanisce.lani.ui.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.KeeperTalk
import si.lanisce.lani.game.scene.Keepers
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.Confetti
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.stage.Backdrop
import si.lanisce.lani.ui.stage.StagePlace
import si.lanisce.lani.ui.villagers.VillagerPortrait

/**
 * A talk with someone at a spot of the landscape (the charcoal burner by his kopa, [Keepers]), over the village as an
 * introduction is: his portrait on the woods behind him (at the hour it is), and below it his talk as a scene's dialog is
 * played (DialogPanel: his lines typed in and voiced, a word's card on a tap, the choices shuffled, his reaction to a wrong
 * one, the 🎤), then what it paid. He is no villager: no friendship, and no talk with the tutor to go on with.
 */
@Composable
fun KeeperScene(vm: AppViewModel, k: KeeperTalk) {
    val talk = k.talk
    val v = remember(k.keeper) { Keepers.villager(k.keeper) }
    // his speaker's voice, else his gender's; he has no voice of his own (no one's profile)
    val voice = remember(v) { Spoken(v.speakerVoice, v.voice) }
    var micOn by remember { mutableStateOf(false) }
    // his lines said aloud, drawn talking while one is heard; the learner's own first, in their voice (SceneScreen's dialog)
    val talking = rememberDialogVoice(vm.speaker, talk) { who -> who to voice } != null
    val look = rememberPartnerLook(talk.run, talking, micOn)
    LaniSheet(onDismissRequest = vm.scenes::closeKeeper) {
        Box(Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(Modifier.matchParentSize()) {
                // folded away while the keyboard is up (a typed turn), so the panel has the room (DialogRoom)
                DialogStage {
                    Box(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))) {
                        // his spot as its cycle has it today (the charcoal pile close up, smoking …); the charcoal burner of a
                        // culture without the pile's rounds at his pile as the map draws it (his "This is my pile" with it in
                        // view); else the woods behind him
                        val state = vm.game.state
                        val config = androidx.compose.ui.platform.LocalConfiguration.current
                        val density = androidx.compose.ui.platform.LocalDensity.current.density
                        val pile = (k.keeper.day != null || k.keeper.cycle == null && k.keeper.spot == si.lanisce.lani.game.scene.TownSpots.KOPA) && state != null &&
                            pileSpot(state, (config.screenWidthDp * density).toInt(), (config.screenHeightDp * density).toInt()) != null
                        if (pile && state != null) {
                            Backdrop(StagePlace.home(v.art), Modifier.matchParentSize(), dim = 0.1f)
                            // the night watch's breakout: the pile's crown ablaze until it is saved, all of it when it is lost
                            val fire = when {
                                k.race == null -> 0f
                                k.raced == true -> 0f
                                k.raced == false -> 1f
                                else -> 0.5f
                            }
                            PilePicture(state, k.keeper, k.keeper.day?.look, Modifier.matchParentSize(), fire = fire)
                        } else Backdrop(StagePlace.home(v.art), Modifier.matchParentSize(), dim = 0.35f)
                        Column(
                            Modifier.align(if (pile) Alignment.BottomStart else Alignment.BottomCenter).padding(bottom = 8.dp, start = if (pile) 8.dp else 0.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            VillagerPortrait(v, if (pile) 84.dp else 100.dp, pose = look.portraitPose, face = look.face)
                            Text(
                                "${v.emoji} ${v.name}", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics { },
                            )
                        }
                        // the stage of the pile today, for whoever doesn't see it: "Kopa se kadi · The pile smokes"
                        k.keeper.day?.shown?.about?.let { about ->
                            Text(
                                about.bi(), color = Color.White, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                // the race against the fire: the clock running down over the turn
                if (k.racing) RaceClock(vm, k)
                DialogPanel(
                    vm = vm,
                    talk = talk,
                    voice = voice,
                    onChoose = vm.scenes::keeperChoose,
                    onNext = vm.scenes::keeperNext,
                    onType = vm.scenes::keeperType,
                    onLetMeChoose = vm.scenes::keeperLetMeChoose,
                    onHint = vm.scenes::keeperHint,
                    onSay = vm.scenes::keeperSay,
                    onEcho = vm.scenes::keeperEcho,
                    onEchoSaid = vm.scenes::keeperEchoSaid,
                    onClose = vm.scenes::closeKeeper,
                    onKeepTalking = null,
                    modifier = Modifier.weight(1f),
                    onListening = { micOn = it },
                    // his face: on the stage over the panel, and in its header (alive) once the stage folds away for the keyboard
                    look = look,
                    livePortrait = keyboardUp(),
                    // over the village "✓ Nazaj v vas"; over Home (its call in "Danes · Today") it stays there: "✓ Naprej"
                    closeLabel = "✓ ${bi(if (vm.screen == si.lanisce.lani.Screen.Village) "arrivals.backVillage" else "common.continue")}",
                )
            }
            if (talk.settled && talk.run.step == DialogRun.Step.END && talk.run.mistakes == 0 && k.raced != false) Confetti(key = "keeper-${talk.key}")
        }
    }
}

/**
 * The clock of a breakout's race (companion/VILLAGERS.md, "The night watch"): "🔥 Hitro! · Quick!" and a bar running down
 * over the seconds left; when it is out the fire wins ([si.lanisce.lani.app.SceneController.keeperRaceOut]). With TalkBack
 * on, the choices read aloud, the clock gives twice the time.
 */
@Composable
private fun RaceClock(vm: AppViewModel, k: KeeperTalk) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(k.raceFrom) {
        val a11y = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        if (a11y?.isTouchExplorationEnabled == true) vm.scenes.keeperRaceSeconds(k.seconds * 2)
    }
    var left by remember { mutableStateOf(1f) }
    LaunchedEffect(k.raceFrom, k.seconds) {
        while (true) {
            val end = k.raceFrom + k.seconds * 1000L
            val now = System.currentTimeMillis()
            left = ((end - now) / (k.seconds * 1000f)).coerceIn(0f, 1f)
            if (now >= end) { vm.scenes.keeperRaceOut(); break }
            delay(100L)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("🔥 ${bi("keeperScene.quick")}", color = Color(0xFFE8501A), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        androidx.compose.material3.LinearProgressIndicator(
            progress = { left }, color = Color(0xFFE8501A), trackColor = Color(0x33E8501A),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
    }
}
