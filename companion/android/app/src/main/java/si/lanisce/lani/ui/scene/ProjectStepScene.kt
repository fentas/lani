package si.lanisce.lani.ui.scene

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.SceneTalk
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.Confetti
import si.lanisce.lani.ui.stage.Backdrop
import si.lanisce.lani.ui.stage.StagePlace
import si.lanisce.lani.ui.villagers.VillagerLogic
import si.lanisce.lani.ui.villagers.VillagerPortrait

/**
 * A village project's step played over the village (companion/SCENES.md "Project steps"): when the village hasn't the
 * step's scene open today (the square before Vas, the chapel's road, which has no scene), its people stand side by side on
 * a backdrop of where it happens (the forest, the square, the field …, else the leader's place), the leader first and
 * big, and below it the step's dialog is played as a scene's (DialogPanel: each line in its speaker's voice and with their
 * face, a word's card on a tap, the choices, a reaction to a wrong one; a tap turn is chosen, there is no picture to tap
 * in); at its end, the step done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectStepScene(vm: AppViewModel, talk: SceneTalk) {
    val step = talk.step ?: return
    // the cast's and the village's own (one who moved in may say a helper's lines)
    val people = remember(step.cast, vm.villagers.all, vm.game.state?.residents) {
        val everyone = vm.villagers.people(vm.game.state)
        step.cast.mapNotNull { p -> everyone.firstOrNull { it.id == p.id }?.let { p.id to it } }.toMap()
    }
    val speakers = remember(step.cast, people) { step.cast.associate { p -> p.id to Speaking(p.emoji, SceneWords.voiceOf(p.art, people[p.id])) } }
    val voice = speakers[talk.person.id]?.voice ?: Spoken(SceneWords.voiceOf(talk.person.art))
    // each new line said aloud in its speaker's voice, and drawn talking meanwhile
    var talking by remember { mutableStateOf<String?>(null) }
    var micOn by remember { mutableStateOf(false) }
    LaunchedEffect(talk.key, talk.run.said.size) {
        val line = talk.run.said.lastOrNull()
        val who = line?.who
        if (line == null || who == null) { talking = null; return@LaunchedEffect }
        delay(DialogMood.sayAfter(talk.run))
        val v = speakers[who]?.voice ?: voice
        vm.speaker.say(line.sl, voiceName = v.voice, fallback = v.fallback, person = v.person)
        talking = who
        delay(700L + line.sl.length * 70L)
        talking = null
    }
    // whom the learner answers: whoever spoke last (the leader at first)
    val facing = talk.run.said.lastOrNull { it.who != null }?.who ?: talk.person.id
    val pose = rememberPartnerPose(talk.run, talking != null && talking == facing, micOn)
    val place = remember(step.project, step.index) { placeOf(vm, step.project, step.index, talk.person.art) }
    ModalBottomSheet(onDismissRequest = vm.scenes::closeTalk, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Column(Modifier.matchParentSize()) {
                // folded away while the keyboard is up (a typed turn), so the panel has the room (DialogRoom)
                DialogStage { Stage(people, step.cast, place, facing, pose, talking) }
                DialogPanel(
                    vm = vm,
                    talk = talk,
                    voice = voice,
                    onChoose = vm.scenes::choose,
                    onNext = vm.scenes::next,
                    onType = vm.scenes::type,
                    onLetMeChoose = vm.scenes::letMeChoose,
                    onHint = vm.scenes::hint,
                    onSay = vm.scenes::say,
                    onEcho = vm.scenes::echo,
                    onEchoSaid = vm.scenes::echoSaid,
                    onClose = vm.scenes::closeTalk,
                    onKeepTalking = null,
                    modifier = Modifier.weight(1f),
                    onListening = { micOn = it },
                    speakers = speakers,
                    closeLabel = "✓ ${bi("arrivals.backVillage")}",
                )
            }
            if (talk.settled && talk.step.result?.won == true) Confetti(key = "step-${talk.key}")
        }
    }
}

/**
 * Where a step over the village is seen: the backdrop of its scene's kind of place (its forest, square, field, smithy,
 * school, kitchen, fire), else the leader's.
 */
private fun placeOf(vm: AppViewModel, project: String, index: Int, leaderArt: String): StagePlace {
    val spec = si.lanisce.lani.game.Projects.spec(project)
    val art = spec?.let { si.lanisce.lani.game.Projects.play(it, index) }?.scene?.let(vm.scenes::byId)?.art
    return when (art) {
        "forest" -> StagePlace.FOREST
        "square", "market", "church" -> StagePlace.SQUARE
        "field", "hills", "stream", "pond", "apiary" -> StagePlace.FIELD
        "smithy" -> StagePlace.SMITHY
        "school" -> StagePlace.SCHOOL
        "kitchen", "livingroom", "workshop", "cellar", "attic" -> StagePlace.KITCHEN
        "campfire" -> StagePlace.FIRE
        else -> StagePlace.home(leaderArt)
    }
}

/**
 * The step's people side by side on [place]'s backdrop: the leader first, big; whoever is talking is drawn talking, the
 * one the learner answers reacts (a wave, a hop, a puzzled hand to the chin), the rest stand by.
 */
@Composable
private fun Stage(people: Map<String, si.lanisce.lani.game.villagers.Villager>, cast: List<ScenePerson>, place: StagePlace, facing: String, pose: Pose, talking: String?) {
    Box(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))) {
        Backdrop(place, Modifier.matchParentSize(), dim = 0.35f)
        Row(
            Modifier.matchParentSize().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            cast.forEachIndexed { i, p ->
                val v = people[p.id] ?: return@forEachIndexed
                val now = when (p.id) {
                    talking -> Pose.TALK
                    facing -> pose
                    else -> Pose.IDLE
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VillagerPortrait(v, if (i == 0) 100.dp else 84.dp, pose = now)
                    Text(
                        "${v.emoji} ${VillagerLogic.shortName(v.name)}",
                        color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}
