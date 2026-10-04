package si.lanisce.lani.ui.towns

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.TownVisit
import si.lanisce.lani.data.Visits
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.sky.SkyCards
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.SceneSights
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.inWorld
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.game.EmojiBadge
import kotlinx.coroutines.delay
import si.lanisce.lani.ui.scene.Anchored
import si.lanisce.lani.ui.scene.DialogMood
import si.lanisce.lani.ui.scene.DialogPanel
import si.lanisce.lani.ui.scene.OverlayPill
import si.lanisce.lani.ui.scene.PersonBubble
import si.lanisce.lani.ui.scene.SceneWordGroups
import si.lanisce.lani.ui.scene.WordChips
import si.lanisce.lani.ui.scene.SceneView
import si.lanisce.lani.ui.scene.SceneWords
import si.lanisce.lani.ui.scene.WordBubble
import si.lanisce.lani.ui.scene.keyboardUp
import si.lanisce.lani.ui.scene.rememberPanelTop
import si.lanisce.lani.ui.scene.rememberSceneAnchors
import si.lanisce.lani.ui.scene.rememberSceneCues
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A place of the visited town (companion/SCENES.md): the picture at the top, its words below, in the town's language
 * explained in the learner's base. A thing tapped shows its word over it and says it; someone there greets the learner,
 * or talks with them: the place's dialogs, the ones meant for guests too ("Sei di fuori?"). Nothing is paid here; a
 * dialog played to its end is someone met (the learner's village keeps it).
 */
@Composable
fun VisitSceneScreen(vm: AppViewModel, id: String) {
    val v = vm.visits.visit
    val spec = v?.scene(id)
    BackHandler { vm.openVisit() }
    if (v == null || spec == null) {
        LaunchedEffect(Unit) { vm.openVisit() }
        return
    }
    VisitPlace(vm, v, spec)
}

@Composable
private fun VisitPlace(vm: AppViewModel, v: TownVisit, spec: SceneSpec) {
    val state = v.state
    // the host's village as the place sees it: a room at its building's level
    val world = remember(state, spec, v.scenes) { state?.let { si.lanisce.lani.game.scene.Homes.world(spec, it, null, v.scenes) } ?: SceneWorld.ALL }
    val scene = remember(spec, world) { spec.inWorld(world) }
    val now = remember { LocalDateTime.now() }
    // a guest meets what is meant for guests too ("Sei di fuori?"); a dialog may name the host's people who are there
    val active = remember(state, v.scenes) { state?.let { Happenings.active(v.scenes, it, now, guest = true, cast = v.cast) }.orEmpty().filter { it.scene.id == scene.id } }
    val present = remember(state) { state?.let { Residents.present(it, LocalDate.now()) } }
    val talk = vm.visits.sceneTalk?.takeIf { it.sceneId == scene.id }
    val people = remember(scene, active, present, talk?.person) {
        val base = Happenings.peopleIn(scene, active, present)
        talk?.person?.takeIf { p -> base.none { it.id == p.id } }?.let { base + PersonInScene(it.id, it.art, it.slot) } ?: base
    }
    val anchors = rememberSceneAnchors(scene.id)
    val cues = rememberSceneCues(state, scene, talk?.run, LocalDate.now())
    val sceneClock = si.lanisce.lani.game.render.rememberTownClock()
    si.lanisce.lani.ui.AmbientSound(vm.ambience, si.lanisce.lani.game.ambient.Place.Scene(scene.art, cues.fx.target), cues.sky.target, sceneClock)
    fun voiceOf(p: ScenePerson) = SceneWords.voiceOf(p.art, p.villager?.let { v.villager(it) })
    // a person's new line is said aloud; the dialog played to its end: the learner met them
    LaunchedEffect(talk?.key, talk?.run?.said?.size) {
        val t = talk ?: return@LaunchedEffect
        val line = t.run.said.lastOrNull() ?: return@LaunchedEffect
        if (line.who == null) return@LaunchedEffect
        delay(DialogMood.sayAfter(t.run))
        val who = voiceOf(t.person)
        vm.speaker.say(line.sl, voiceName = who.voice, fallback = who.fallback, person = who.person)
    }
    LaunchedEffect(talk?.key, talk?.settled) {
        val t = talk ?: return@LaunchedEffect
        if (t.settled) vm.metOnVisit(t.person.villager ?: t.person.id, t.person.name)
    }
    BackHandler(enabled = talk != null) { vm.visits.closeSceneTalk() }
    var selected by rememberSaveable(scene.id) { mutableStateOf<String?>(null) }
    var greeted by remember { mutableStateOf<String?>(null) }
    var found by remember(scene.id) { mutableStateOf(listOf<String>()) }
    // the words by what the picture shows now (SceneSights, as the painters draw it), looked at again every minute
    var minute by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); minute++ } }
    val sights = remember(minute, world, state?.seed, cues.fx.target) {
        SceneSights.frame(LocalDateTime.now(), world, state?.seed ?: 0L, cues.fx.target, Moon.now(), SkyCards.now())
    }
    val groups = remember(spec, sights, found) { SceneSights.group(spec, sights, found.toSet()) }
    val density = LocalDensity.current
    val topLimit = WindowInsets.statusBars.getTop(density) + with(density) { 56.dp.roundToPx() }
    val bubbleColor = MaterialTheme.colorScheme.surface

    fun select(o: SceneObject) {
        selected = o.slot
        greeted = null
        if (o.slot !in found) found = found + o.slot
        vm.speaker.say(o.sl.ifBlank { o.word })
    }
    fun talkTo(a: ActiveHappening) {
        selected = null
        greeted = null
        if (!vm.visits.startSceneTalk(a)) greeted = a.person?.id
    }
    fun tapped(t: SceneTarget?) {
        if (talk != null) return
        when (t) {
            is SceneTarget.Thing -> scene.objects.firstOrNull { it.slot == t.slot }?.let(::select) ?: run { selected = null }
            is SceneTarget.Person -> {
                val a = active.firstOrNull { it.person?.id == t.id && it.talks }
                if (a != null) talkTo(a) else { selected = null; greeted = t.id }
            }
            null -> { selected = null; greeted = null }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val sceneHeight = min(maxHeight * 0.52f, maxWidth * 0.82f)
        // the keyboard up in a dialog: its panel rises over the picture, as in a scene at home (DialogRoom)
        val lifted = talk != null && keyboardUp()
        val panelTop = rememberPanelTop(sceneHeight, lifted)
        Box(Modifier.fillMaxSize()) {
            SceneView(
                scene = scene, people = people, highlight = talk?.person?.id ?: selected ?: greeted, onTap = ::tapped,
                modifier = Modifier.fillMaxWidth().height(sceneHeight), anchors = anchors, cues = cues, world = world,
                onPoke = { id, poked -> vm.ambience.poke(scene.art, id, poked, sceneClock) },
                village = state?.seed ?: 0L, clock = sceneClock,
            )
            // a guest's dialog: over the words, played to its end (nothing is paid on a visit; the learner met them)
            if (talk != null) DialogPanel(
                vm = vm, talk = talk, voice = voiceOf(talk.person),
                onChoose = vm.visits::chooseInScene, onNext = vm.visits::nextInScene, onClose = vm.visits::closeSceneTalk, onHint = vm.visits::hintInScene,
                onKeepTalking = null, modifier = Modifier.fillMaxSize().padding(top = panelTop),
            ) else Column(
                Modifier.fillMaxSize().padding(top = sceneHeight).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(scene.emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clearAndSetSemantics { })
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(scene.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        Text(Visits.banner(v.name, v.learner), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (scene.objects.isNotEmpty()) {
                    Text("🔍 ${bi("sceneScreen.foundShort", "nFound" to scene.objects.count { it.slot in found }, "total" to scene.objects.size)}", style = MaterialTheme.typography.titleSmall)
                    if (found.isEmpty()) Text("👆 ${bi("sceneScreen.tapThingsPicture")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    // here now, seen before, still hidden: as at home (SceneWordGroups), what this visit found
                    SceneWordGroups(groups, found.toSet(), selected, learned = { false }, onWord = ::select)
                }
                // who is here: they greet; someone with a dialog (one meant for guests among them) talks with the learner
                val talking = active.filter { it.person != null && it.talks }.distinctBy { it.person!!.id }
                val there = (Happenings.always(scene, present) + talking.mapNotNull { it.person }).distinctBy { it.id }
                if (there.isNotEmpty()) {
                    Text(bi("sceneScreen.hereToday"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    for (p in there) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            EmojiBadge(p.emoji, MaterialTheme.colorScheme.surface, size = 44)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                val a = talking.firstOrNull { it.person?.id == p.id }
                                if (a != null) {
                                    Text(a.happening.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(onClick = { talkTo(a) }, modifier = Modifier.padding(top = 6.dp).heightIn(min = 44.dp)) {
                                        Text("💬 ${bi("visit.talk")}")
                                    }
                                }
                            }
                        }
                    }
                }
                Text("🧳 ${bi("visit.guestHere")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // over the picture: the word of the thing tapped (one seen before while it isn't there now, with when it shows), or
        // the greeting of someone there
        val sel = spec.objects.firstOrNull { it.slot == selected }
        val selTarget = sel?.let { SceneTarget.Thing(it.slot) }
        if (sel != null && selTarget != null && (anchors.drawn(selTarget) || WordChips.opens(sel, groups))) {
            val bubble = @Composable { WordBubble(sel, learned = false, fresh = false, speaker = vm.speaker, color = bubbleColor, note = WordChips.noteOf(sel.slot, groups)) }
            if (anchors.drawn(selTarget)) Anchored({ anchors.shown(selTarget) }, topLimit, bubbleColor) { bubble() }
            else Box(Modifier.fillMaxWidth().height(sceneHeight).statusBarsPadding().padding(top = 56.dp, start = 10.dp, end = 10.dp), contentAlignment = Alignment.Center) { bubble() }
        }
        val who = scene.people.firstOrNull { it.id == greeted }
        val whoTarget = who?.let { SceneTarget.Person(it.id) }
        if (who != null && whoTarget != null && anchors.drawn(whoTarget)) {
            Anchored({ anchors.shown(whoTarget) }, topLimit, bubbleColor) {
                PersonBubble(who.emoji, who.name, null, now.hour, vm.speaker, bubbleColor, SceneWords.voiceOf(who.art, who.villager?.let { v.villager(it) }))
            }
        }
        // the way back, out of the way while the dialog's panel has risen over the picture (it would lie on its header)
        AnimatedVisibility(!lifted, enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OverlayPill {
                    TextButton(onClick = vm::openVisit) {
                        Text("‹ ${v.name.ifBlank { bi("common.village") }}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
