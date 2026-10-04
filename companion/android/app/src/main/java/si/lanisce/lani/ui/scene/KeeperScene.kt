package si.lanisce.lani.ui.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import si.lanisce.lani.ui.stage.Backdrop
import si.lanisce.lani.ui.stage.StagePlace
import si.lanisce.lani.ui.villagers.VillagerPortrait

/**
 * A talk with someone at a spot of the landscape (the charcoal burner by his kopa, [Keepers]), over the village as an
 * introduction is: his portrait on the woods behind him (at the hour it is), and below it his talk as a scene's dialog is
 * played (DialogPanel: his lines typed in and voiced, a word's card on a tap, the choices shuffled, his reaction to a wrong
 * one, the 🎤), then what it paid. He is no villager: no friendship, and no talk with the tutor to go on with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeeperScene(vm: AppViewModel, k: KeeperTalk) {
    val talk = k.talk
    val v = remember(k.keeper) { Keepers.villager(k.keeper) }
    // his speaker's voice, else his gender's; he has no voice of his own (no one's profile)
    val voice = remember(v) { Spoken(v.speakerVoice, v.voice) }
    var talking by remember { mutableStateOf(false) }
    var micOn by remember { mutableStateOf(false) }
    LaunchedEffect(talk.key, talk.run.said.size) {
        val line = talk.run.said.lastOrNull()
        if (line?.who == null) { talking = false; return@LaunchedEffect }
        delay(DialogMood.sayAfter(talk.run))
        vm.speaker.say(line.sl, voiceName = voice.voice, fallback = voice.fallback)
        talking = true
        delay(700L + line.sl.length * 70L)
        talking = false
    }
    val pose = rememberPartnerPose(talk.run, talking, micOn)
    ModalBottomSheet(onDismissRequest = vm.scenes::closeKeeper, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Column(Modifier.matchParentSize()) {
                // folded away while the keyboard is up (a typed turn), so the panel has the room (DialogRoom)
                DialogStage {
                    Box(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))) {
                        Backdrop(StagePlace.home(v.art), Modifier.matchParentSize(), dim = 0.35f)
                        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            VillagerPortrait(v, 100.dp, pose = if (talking) Pose.TALK else pose)
                            Text(
                                "${v.emoji} ${v.name}", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics { },
                            )
                        }
                    }
                }
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
                    closeLabel = "✓ ${bi("arrivals.backVillage")}",
                )
            }
            if (talk.settled && talk.run.step == DialogRun.Step.END && talk.run.mistakes == 0) Confetti(key = "keeper-${talk.key}")
        }
    }
}
