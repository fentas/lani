package si.lanisce.lani.ui.villagers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.MeetingTalk
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.Confetti
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.scene.DialogPanel
import si.lanisce.lani.ui.scene.DialogRun
import si.lanisce.lani.ui.scene.DialogStage
import si.lanisce.lani.ui.scene.SceneWords
import si.lanisce.lani.ui.scene.Speaking
import si.lanisce.lani.ui.scene.Spoken
import si.lanisce.lani.ui.scene.rememberPartnerLook
import si.lanisce.lani.ui.stage.Look
import si.lanisce.lani.game.scene.face
import si.lanisce.lani.ui.scene.rememberDialogVoice
import si.lanisce.lani.ui.stage.Backdrop
import si.lanisce.lani.ui.stage.StagePlace

/**
 * Meeting someone who joined the village (companion/VILLAGERS.md "Arrivals"), over the village: the people of the
 * introduction on the backdrop of the newcomer's place (the newcomer, and whoever introduces them: Micka and Zala, the
 * parents and their baby), and below it the dialog as a scene's is played (DialogPanel: their lines typed in and voiced,
 * a word's card on a tap, the learner's turns with shuffled choices, a reaction to a wrong one); at its end the friendship
 * that starts, and a talk with them to go on with.
 */
@Composable
fun ArrivalScene(vm: AppViewModel, m: MeetingTalk) {
    val talk = m.talk
    val id = m.meeting.id
    val people: Map<String, Villager> = remember(m.meeting.speakers, vm.villagers.all, vm.game.state?.residents) {
        m.meeting.speakers.mapNotNull { who -> vm.villagers.byId(who)?.let { who to it } }.toMap()
    }
    val speakers = remember(people) { people.mapValues { (_, v) -> Speaking(v.emoji, SceneWords.voiceOf(v.art, v), v.art, v.id.hashCode(), v.name) } }
    val voice = speakers[id]?.voice ?: Spoken(SceneWords.voiceOf(talk.person.art))
    // each new line said aloud in its speaker's voice, and drawn talking meanwhile
    var micOn by remember { mutableStateOf(false) }
    // drawn talking while the line is heard; the learner's own first, in their voice (SceneScreen's dialog)
    val talking = rememberDialogVoice(vm.speaker, talk) { who -> who to (speakers[who]?.voice ?: voice) }
    // who the learner answers now: the one a turn names (Zala at her welcome), else whoever spoke last (the newcomer at first)
    val facing = talk.run.facing ?: id
    val facingArt = people[facing]?.art
    val look = rememberPartnerLook(talk.run, talking != null && talking == facing, micOn, facing, child = facingArt?.startsWith("child") == true || facingArt == "baby")
    LaniSheet(onDismissRequest = vm.villagers::closeMeeting) {
        Box(Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(Modifier.matchParentSize()) {
                // folded away while the keyboard is up (a typed turn), so the panel has the room (DialogRoom)
                DialogStage { Stage(people, id, talk.person.art, facing, look, talking) }
                DialogPanel(
                    vm = vm,
                    talk = talk,
                    voice = voice,
                    onChoose = vm.villagers::meetChoose,
                    onNext = vm.villagers::meetNext,
                    onHint = vm.villagers::meetHint,
                    onClose = vm.villagers::closeMeeting,
                    // a talk with them, played by the tutor ("villager:<id>")
                    onKeepTalking = { scenario ->
                        vm.villagers.closeMeeting()
                        VillagerLogic.villagerOfScenario(scenario)?.let { vm.talkWithVillager(it) } ?: vm.openTalk(scenario)
                    },
                    modifier = Modifier.weight(1f),
                    onListening = { micOn = it },
                    speakers = speakers,
                    // their faces: on the stage over the panel, and in its header (alive) once the stage folds away for the keyboard
                    look = look,
                    livePortrait = si.lanisce.lani.ui.scene.keyboardUp(),
                    // over the village "✓ Nazaj v vas"; over Home (its call in "Danes · Today") it stays there: "✓ Naprej"
                    closeLabel = "✓ ${bi(if (vm.screen == si.lanisce.lani.Screen.Village) "arrivals.backVillage" else "common.continue")}",
                )
            }
            if (talk.settled && talk.run.step == DialogRun.Step.END) Confetti(key = "arrival-${talk.key}")
        }
    }
}

/**
 * "👋 Seznani se · Meet them", for someone who lives here and hasn't been met ([si.lanisce.lani.app.VillagerController.waitsToMeet]):
 * their card and page offer it instead of a talk. While they wait for someone to introduce them (Zala for Micka), it
 * names that one, whose introduction comes first.
 */
@Composable
fun MeetButton(vm: AppViewModel, v: Villager, modifier: Modifier = Modifier, onMeet: () -> Unit = {}) {
    val s = vm.game.state
    val next = s?.let { si.lanisce.lani.game.villagers.Arrivals.next(it, v.id, java.time.LocalDate.now()) }
    val first = next?.takeIf { it.id != v.id }?.let { vm.villagers.byId(it.id, s) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = { onMeet(); vm.villagers.meet(v.id) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = HeartPink, contentColor = Color.White),
        ) {
            Text(
                "👋 ${bi("arrivals.meet")}" + (first?.let { " · ${it.emoji} ${it.name}" } ?: ""),
                style = MaterialTheme.typography.labelLarge, maxLines = 2,
            )
        }
        if (first != null) Text(
            "${first.emoji} ${bi("arrivals.firstMeet", "g" to VillagerLogic.gender(first))}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The people of the introduction side by side on the newcomer's place: the newcomer first, big; whoever is talking
 * is drawn talking, the one the learner answers reacts (a wave, a hop, a puzzled hand to the chin), the rest stand by.
 */
@Composable
private fun Stage(people: Map<String, Villager>, id: String, art: String, facing: String, look: Look, talking: String?) {
    Box(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))) {
        Backdrop(StagePlace.home(people[id]?.art ?: art), Modifier.matchParentSize(), dim = 0.35f)
        Row(
            Modifier.matchParentSize().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            val order = listOf(id) + people.keys.filter { it != id }
            for (who in order) {
                val v = people[who] ?: continue
                val p = when {
                    who == talking -> Pose.TALK
                    who == facing -> look.pose
                    else -> Pose.IDLE
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VillagerPortrait(v, if (who == id) 100.dp else 84.dp, pose = p, face = if (who == facing) look.face else p.face)
                    Text(
                        "${v.emoji} ${VillagerLogic.shortName(v.name)}",
                        color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = if (who == id) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}
