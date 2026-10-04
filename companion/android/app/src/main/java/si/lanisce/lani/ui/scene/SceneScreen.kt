package si.lanisce.lani.ui.scene

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.SceneFound
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.DayAct
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.SceneSights
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Sleep
import si.lanisce.lani.game.scene.Stage
import si.lanisce.lani.game.scene.Whereabouts
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.scene.StickerSpot
import si.lanisce.lani.game.scene.inWorld
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.sky.SkyCards
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.Confetti
import si.lanisce.lani.ui.game.EmojiBadge
import si.lanisce.lani.ui.game.RecenterButton
import si.lanisce.lani.ui.game.TownCameraState
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A close-up scene of the village (companion/SCENES.md): the picture big at the top, the words below.
 * Tapping a thing shows its word over it (said aloud) and marks it found; "learn these words" starts a
 * pack run with the scene's words not learned yet; someone with something to say has a speech marker, and
 * tapping them starts their dialog, which pays the village at the end.
 *
 * [id] is a scene of [si.lanisce.lani.app.SceneController.all]; [focus] is a happening key
 * ("scene/happening") to open straight into its dialog, or an object slot to show first.
 */
@Composable
fun SceneScreen(vm: AppViewModel, id: String, focus: String?, building: String? = null) {
    val scene = vm.scenes.byId(id)
    if (scene == null) Missing(vm, id) else Scene(vm, scene, focus, building)
}

@Composable
private fun Missing(vm: AppViewModel, id: String) {
    BackHandler { vm.openVillage() }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = vm::openVillage) { Text("‹ ${bi("common.village")}") }
        if (vm.scenes.all.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            Text(bi("sceneScreen.sceneIsntThereId", "id" to id), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Scene(vm: AppViewModel, spec: SceneSpec, focus: String?, building: String?) {
    val scenes = vm.scenes
    val speaker = vm.speaker
    val state = vm.game.state
    // What the village has built: a thing it hasn't (the mill before it's built) isn't drawn, nor a word to find here; a
    // room is seen in the building it was opened from, at its level (the kitchen of a hut at level 1 is bare).
    val world = remember(state, spec, building) { vm.sceneWorld(spec, building) }
    val scene = remember(spec, world) { spec.inWorld(world) }
    // What's on changes with the time of day: look again every minute.
    var minute by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); minute++ } }
    val now = remember(minute) { LocalDateTime.now() }
    val active = remember(state, scenes.all, now) { state?.let { scenes.active(it, now) }.orEmpty() }
    val here = active.filter { it.scene.id == scene.id }
    // the village's day, as the map has it (Routine): who is out here now, and at night who sleeps in their bed here (a tap
    // turns them over, another has them mumble; no dialog)
    val everyone = remember(state, vm.villagers.all) { vm.villagers.people(state) }
    val day = remember(state, scenes.all, active, vm.game.storyHeardAt, everyone, now.toLocalDate()) {
        state?.let { s -> Whereabouts.day(scenes.all, s, vm.villagers.all, everyone, now.toLocalDate(), vm.game.storyHeardAt, active) }
    }
    val sleepers = remember(scene, state, now, day) {
        state?.let { s -> Sleep.sleepers(scene, s, now.toLocalDate(), Routine.minuteOf(now), everyone, scenes.all, day ?: Routine.Day()) }.orEmpty()
    }
    val about = remember(scene, state, now, day) {
        state?.let { s ->
            val present = Residents.present(s, now.toLocalDate())
            Whereabouts.inScene(scene, s, everyone.filter { present == null || it.id in present }, now, day ?: Routine.Day(), scenes.all)
        }.orEmpty()
    }
    var mumble by remember { mutableStateOf<Pair<String, Long>?>(null) } // who mumbles "Pusti me spati …", since when
    LaunchedEffect(mumble) { if (mumble != null) { delay((Sleep.MUMBLE_S * 1000).toLong()); mumble = null } }
    val later = remember(state, scenes.all, now) {
        state?.let { Happenings.later(scenes.all, it, now) }.orEmpty().filter { it.second.scene.id == scene.id }
    }
    val doneToday = remember(state, scene, now) {
        scene.happenings.filter { h -> state != null && Happenings.done(state, "${scene.id}/${h.id}", now.toLocalDate()) }.map { it.id }
    }
    val talk = scenes.talk?.takeIf { it.sceneId == scene.id }
    // A villager speaks with their own voice (their speaker), anyone else with their sprite's.
    fun voiceOf(p: ScenePerson) = SceneWords.voiceOf(p.art, p.villager?.let { vm.villagers.byId(it) })

    var selected by rememberSaveable(scene.id) { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf<String?>(null) } // the slot just found for the first time
    var greeted by remember { mutableStateOf<String?>(null) } // someone without a dialog, saying hello
    var skyCard by remember { mutableStateOf<si.lanisce.lani.game.sky.SkyTap?>(null) } // the moon, a star … tapped in the sky
    var skyList by remember { mutableStateOf<List<si.lanisce.lani.game.sky.SkyTap>?>(null) } // TalkBack's "Tonight's sky"
    var allFound by remember { mutableIntStateOf(0) }
    val found = scenes.foundIn(scene)
    val anchors = rememberSceneAnchors(scene.id)

    LaunchedEffect(scene.id) { runCatching { scenes.loadPacks(scene) } }

    fun select(o: SceneObject) {
        selected = o.slot
        greeted = null
        val new = scenes.discover(scene, o.slot)
        fresh = if (new) o.slot else null
        speaker.say(o.sl.ifBlank { o.word })
        if (new && SceneFound.count(scenes.found, scene) >= scene.objects.size) {
            allFound++
            vm.notices.banner = "🎉 ${bi("sceneScreen.foundEverythingHere")}"
        }
    }

    // someone here opens something: their dialog, or the storyteller's story tonight (no dialog, but an evening to tell)
    fun opens(a: ActiveHappening): Boolean = a.talks || (a.happening.stories && scenes.tonight(a) != null)

    fun talkTo(a: ActiveHappening) {
        selected = null
        greeted = null
        if (!scenes.startTalk(a)) greeted = a.person?.id
    }

    fun tapped(t: SceneTarget?) {
        if (scenes.talk != null) return
        when (t) {
            is SceneTarget.Thing -> scene.objects.firstOrNull { it.slot == t.slot }?.let(::select) ?: run { selected = null }
            is SceneTarget.Person -> {
                // asleep: they only turn over and mumble (the scene view's poke), nothing to say
                if (sleepers.any { it.id == t.id }) { selected = null; greeted = null; return }
                val a = here.firstOrNull { it.person?.id == t.id }
                if (a != null && opens(a)) talkTo(a) else { selected = null; greeted = t.id }
            }
            null -> { selected = null; greeted = null }
        }
    }

    // The village can open a scene at someone's dialog or at one thing.
    var focused by rememberSaveable(scene.id, focus) { mutableStateOf(focus == null) }
    LaunchedEffect(focus, here) {
        if (focused || focus == null) return@LaunchedEffect
        if ('/' in focus) {
            here.firstOrNull { it.key == focus }?.let { talkTo(it); focused = true } // once the village is loaded
        } else {
            scene.objects.firstOrNull { it.slot == focus }?.let(::select)
            focused = true
        }
    }

    // A person's new line is said aloud, and they're drawn talking meanwhile.
    var talking by remember { mutableStateOf(false) }
    val lastLine = talk?.run?.said?.lastOrNull()
    LaunchedEffect(talk?.key, talk?.run?.said?.size) {
        val line = lastLine
        if (talk == null || line?.who == null) { talking = false; return@LaunchedEffect }
        delay(DialogMood.sayAfter(talk.run)) // let the panel, the bubble and the person's reaction come first
        val who = voiceOf(talk.person)
        speaker.say(line.sl, voiceName = who.voice, fallback = who.fallback, person = who.person)
        talking = true
        delay(700L + line.sl.length * 70L)
        talking = false
    }
    // The scene takes part in the dialog: the weather and effects its lines bring, how the person reacts (SceneLife.kt).
    var micOn by remember { mutableStateOf(false) }
    val cues = rememberSceneCues(state, scene, talk?.run, now.toLocalDate())
    // The words by what the picture shows now (SceneSights, as the painters draw it), what was seen before and what is
    // still hidden: every word of the scene, also what the village hasn't built yet.
    val sights = remember(now, world, state?.seed, cues.fx.target) {
        SceneSights.frame(now, world, state?.seed ?: 0L, cues.fx.target, Moon.now(), SkyCards.now())
    }
    val groups = remember(spec, sights, found) { SceneSights.group(spec, sights, found) }
    val pose = rememberPartnerPose(talk?.run, talking, micOn && talk != null)
    // its background sounds: the place's, with the weather and the effects its dialog brings (the rain, the fire flaring),
    // and on the picture's clock what it shows making a sound (the thunder after a flash, a bell's peal, what a tap sets off)
    val sceneClock = si.lanisce.lani.game.render.rememberTownClock()
    si.lanisce.lani.ui.AmbientSound(vm.ambience, si.lanisce.lani.game.ambient.Place.Scene(scene.art, cues.fx.target), cues.sky.target, sceneClock)

    BackHandler {
        when {
            scenes.talk != null -> scenes.closeTalk()
            selected != null || greeted != null -> { selected = null; greeted = null }
            else -> vm.openVillage()
        }
    }

    val present = remember(state) { state?.let { Residents.present(it, LocalDate.now()) } }
    val people = remember(scene, here, sleepers, about, talk?.person, talking, present, pose, state) {
        // whoever a dialog left somewhere for the day (act_stays: Luka by the lantern while it rains) is still there, and
        // who is here by their day (France hoeing the field)
        val stayed = Stage.stayed(scene, DayAct.of(state, now.toLocalDate(), scene.id), present)
        val base = Sleep.inBed((Happenings.peopleIn(scene, here, present) + stayed + about).distinctBy { it.id }, sleepers)
        // Whoever is talking stays until the dialog is closed, also once it's done for today.
        val all = talk?.person?.takeIf { p -> base.none { it.id == p.id } }?.let { base + PersonInScene(it.id, it.art, it.slot) } ?: base
        all.map { it.copy(talking = talking && it.id == talk?.person?.id, pose = if (it.id == talk?.person?.id) pose else it.pose) }
    }
    val bubbleColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val topLimit = WindowInsets.statusBars.getTop(density) + with(density) { 56.dp.roundToPx() }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val sceneHeight = min(maxHeight * 0.52f, maxWidth * 0.82f)
        // a dialog's typed turn with the keyboard up: the panel rises over the picture to just under the status bar, the
        // top bar out of the way, and comes back down with the keyboard (DialogRoom)
        val lifted = talk != null && keyboardUp()
        val panelTop = rememberPanelTop(sceneHeight, lifted)
        Box(Modifier.fillMaxSize()) {
            SceneView(
                scene = scene,
                people = people,
                highlight = talk?.person?.id ?: selected ?: greeted,
                onTap = ::tapped,
                modifier = Modifier.fillMaxWidth().height(sceneHeight),
                anchors = anchors,
                cues = cues,
                world = world,
                onPoke = { id, poked ->
                    vm.ambience.poke(scene.art, id, poked, sceneClock)
                    // a sleeper of the scene, or one of the household it doesn't list (Zala in the kitchen): themselves
                    val who = Sleep.sleeperOf(id)?.let { p ->
                        scene.people.firstOrNull { it.id == p }
                            ?: everyone.firstOrNull { it.id == p }?.let { v -> ScenePerson(v.id, v.name, v.emoji, v.art, "", villager = v.id) }
                    }
                    if (who != null && poked.step == 1) {
                        mumble = who.id to System.nanoTime()
                        val v = voiceOf(who)
                        speaker.say(sleepyMumble(), voiceName = v.voice, fallback = v.fallback, person = v.person)
                    }
                },
                village = state?.seed ?: 0L,
                // the night sky over an outdoor scene: the moon's card, a constellation's, a shooting star's wish
                onSky = { tap -> if (scenes.talk == null) { selected = null; greeted = null; skyCard = tap } },
                skyMark = skyCard?.mark,
                onSkyList = { up -> if (scenes.talk == null) { selected = null; greeted = null; skyList = up } },
                clock = sceneClock,
                // a tap turn: the learner answers by tapping the place, the thing or the person in the picture
                taps = talk?.run?.taps.orEmpty(),
                onTapTurn = { target ->
                    scenes.talk?.takeIf { it.sceneId == scene.id }?.run?.let { run -> countAnswers(vm, run, run.tap(target)) }
                    scenes.tap(target)
                },
            )
            AnimatedContent(
                targetState = talk != null,
                transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { it / 3 } + fadeOut()) },
                modifier = Modifier.fillMaxSize().padding(top = panelTop),
                label = "panel",
            ) { inTalk ->
                val t = scenes.talk?.takeIf { it.sceneId == scene.id }
                if (inTalk && t != null) {
                    DialogPanel(
                        vm = vm,
                        talk = t,
                        voice = voiceOf(t.person),
                        onChoose = scenes::choose,
                        onNext = scenes::next,
                        onType = scenes::type,
                        onLetMeChoose = scenes::letMeChoose,
                        onHint = scenes::hint,
                        onSay = scenes::say,
                        onEcho = scenes::echo,
                        onEchoSaid = scenes::echoSaid,
                        onClose = scenes::closeTalk,
                        onKeepTalking = { scenario -> scenes.closeTalk(); vm.openTalk(scenario) },
                        onListening = { micOn = it },
                        onLearnStory = { story -> vm.learnStory(scene.id, story) },
                    )
                } else {
                    val asleep = sleepers.mapNotNull { s ->
                        scene.people.firstOrNull { it.id == s.id }
                            ?: everyone.firstOrNull { it.id == s.id }?.let { v -> ScenePerson(v.id, v.name, v.emoji, v.art, s.slot, villager = v.id) }
                    }
                    WordsPanel(vm, scene, groups, here, later, doneToday, found, selected, asleep, onWord = ::select, onTalk = ::talkTo, onLearn = { vm.learnScene(scene.id) }, opens = ::opens)
                }
            }
        }

        // Over the picture: markers over people with something to say, the word or greeting bubble, the top bar.
        // They follow the camera (read when placing) and wait, unplaced, while their thing is out of view.
        if (talk == null) {
            for (a in here) {
                val p = a.person ?: continue
                if (!opens(a)) continue
                val target = SceneTarget.Person(p.id)
                if (!anchors.drawn(target)) continue
                Anchored({ anchors.shown(target)?.let { IntRect(it.left, it.top, it.right, it.top) } }, topLimit = 0, color = XpGold) {
                    SpeechMarker(a.happening.marker, bi("sceneScreen.talk", "pName" to p.name, "happeningTitle" to a.happening.title), XpGold) { talkTo(a) }
                }
            }
            // a word seen before opens over the picture even while its thing isn't in it (the owl's eyes by day), with when it shows
            val sel = spec.objects.firstOrNull { it.slot == selected }
            val selTarget = sel?.let { SceneTarget.Thing(it.slot) }
            if (sel != null && selTarget != null && (anchors.drawn(selTarget) || WordChips.opens(sel, groups))) {
                val sticker = SceneWords.packOf(scene, sel)?.let { scenes.stickers.picture(it, sel.word, sel.emoji) }?.let { StickerSpot(scene.art, sel.slot) }
                val bubble = @Composable {
                    WordBubble(
                        sel, SceneWords.learned(scene, sel, scenes.packs), fresh = fresh == sel.slot, speaker = speaker, color = bubbleColor,
                        sticker = sticker, note = WordChips.noteOf(sel.slot, groups),
                    )
                }
                if (anchors.drawn(selTarget)) Anchored({ anchors.shown(selTarget) }, topLimit, bubbleColor) { bubble() }
                else Box(Modifier.fillMaxWidth().height(sceneHeight).statusBarsPadding().padding(top = 56.dp, start = 10.dp, end = 10.dp), contentAlignment = Alignment.Center) { bubble() }
            }
            val who = scene.people.firstOrNull { it.id == greeted }
            val whoTarget = who?.let { SceneTarget.Person(it.id) }
            if (who != null && whoTarget != null && anchors.drawn(whoTarget)) {
                val line = here.firstOrNull { it.person?.id == who.id }?.happening?.title
                Anchored({ anchors.shown(whoTarget) }, topLimit, bubbleColor) { PersonBubble(who.emoji, who.name, line, now.hour, speaker, bubbleColor, voiceOf(who)) }
            }
            mumble?.let { (id, at) ->
                val target = SceneTarget.Person(id)
                if (anchors.drawn(target)) Anchored({ anchors.shown(target) }, topLimit, bubbleColor) { MumbleBubble(bubbleColor, at) }
            }
        }
        if (!lifted) Recenter(anchors.camera, Modifier.fillMaxWidth().height(sceneHeight).padding(10.dp))
        TopBar(scene, found.size, onBack = vm::openVillage, shown = !lifted)
        if (allFound > 0) Confetti(key = "found-${scene.id}-$allFound")
        if (talk?.settled == true) Confetti(key = "talk-${talk.key}")
    }
    skyList?.let { up -> si.lanisce.lani.ui.sky.SkyTonightSheet(vm, up, onOpen = { skyList = null; skyCard = it }, onDismiss = { skyList = null }) }
    skyCard?.let { t -> si.lanisce.lani.ui.sky.SkySheet(vm, t, onDismiss = { skyCard = null }) }
}

/** In the picture's corner once the learner has zoomed or panned: back to the whole scene. */
@Composable
private fun Recenter(cam: TownCameraState, modifier: Modifier) {
    val moved by remember(cam) { derivedStateOf { cam.moved } } // not every step of a pinch, only when it flips
    Box(modifier, contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(moved, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) { RecenterButton(cam::recenter) }
    }
}

/** The status bar's icons sit on the picture: light ones, over a soft shade, while the scene is open. */
@Composable
private fun LightStatusIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose { }
        val c = WindowCompat.getInsetsController(window, view)
        val was = c.isAppearanceLightStatusBars
        c.isAppearanceLightStatusBars = false
        onDispose { c.isAppearanceLightStatusBars = was }
    }
    Box(
        Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun TopBar(scene: SceneSpec, found: Int, onBack: () -> Unit, shown: Boolean = true) {
    LightStatusIcons()
    // out of the way while a dialog's panel has risen over the picture (the keyboard up): it would lie on the panel's header
    AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) { TopBarRow(scene, found, onBack) }
}

@Composable
private fun TopBarRow(scene: SceneSpec, found: Int, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        OverlayPill {
            TextButton(onClick = onBack) { Text("‹ ${bi("common.village")}", color = Color.White, style = MaterialTheme.typography.labelLarge) }
        }
        Spacer(Modifier.weight(1f))
        val total = scene.objects.size
        if (total > 0) {
            OverlayPill(Modifier.semantics { contentDescription = bi("sceneScreen.foundTotal", "found" to found, "total" to total) }) {
                Text(
                    "🔍 $found/$total",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * Below the picture: the scene's title, what's found and learned, its words ([groups]: here now, seen before, still
 * hidden, see [SceneWordGroups]), and who is here today.
 */
@Composable
private fun WordsPanel(
    vm: AppViewModel,
    scene: SceneSpec,
    groups: SceneSights.Groups,
    here: List<ActiveHappening>,
    later: List<Pair<TimeOfDay, ActiveHappening>>,
    doneToday: List<String>,
    found: Set<String>,
    selected: String?,
    asleep: List<ScenePerson>,
    onWord: (SceneObject) -> Unit,
    onTalk: (ActiveHappening) -> Unit,
    onLearn: () -> Unit,
    /** Whether someone here opens something (their dialog, the storyteller's story tonight). */
    opens: (ActiveHappening) -> Boolean = { it.talks },
) {
    val packs = vm.scenes.packs
    val present = vm.game.state?.let { Residents.present(it, LocalDate.now()) }
    val total = scene.objects.size
    val nFound = scene.objects.count { it.slot in found }
    val nLearned = SceneWords.learnedCount(scene, packs)
    val left = SceneWords.left(scene, packs).size
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(scene.emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(scene.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text("${scene.level} · $total ${bi("sceneScreen.things", "n" to total)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (total > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🔍 ${bi("sceneScreen.foundShort", "nFound" to nFound, "total" to total)}", style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(progress = { nFound / total.toFloat() }, modifier = Modifier.fillMaxWidth(), color = XpGold)
                Text(
                    "📚 ${bi("sceneScreen.learnedShort", "nLearned" to nLearned, "total" to total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (nFound == 0) {
                Text("👆 ${bi("sceneScreen.tapThingsPicture")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            SceneWordGroups(groups, found, selected, learned = { SceneWords.learned(scene, it, packs) }, onWord = onWord)
            if (left > 0) {
                BigButton("📚 ${bi("sceneScreen.learnTheseWordsLeft", "left" to left)}", onClick = onLearn)
            } else {
                Text("✅ ${bi("sceneScreen.knowEveryWordHere")}", style = MaterialTheme.typography.titleSmall, color = AlpineGreen)
            }
        }
        People(scene, here, later, doneToday, present, asleep, onTalk, opens)
    }
}

/** "Danes tukaj · Here today": who is here with something to say, who sleeps here, and what's done today. */
@Composable
private fun People(
    scene: SceneSpec, here: List<ActiveHappening>, later: List<Pair<TimeOfDay, ActiveHappening>>, doneToday: List<String>, present: Set<String>?,
    asleep: List<ScenePerson>, onTalk: (ActiveHappening) -> Unit, opens: (ActiveHappening) -> Boolean = { it.talks },
) {
    val always = Happenings.always(scene, present).filter { p -> here.none { a -> a.person?.id == p.id } && asleep.none { it.id == p.id } }
    if (here.isEmpty() && later.isEmpty() && doneToday.isEmpty() && always.isEmpty() && asleep.isEmpty() && scene.happenings.isEmpty()) return
    Text(bi("sceneScreen.hereToday"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
    for (a in here) {
        val p = a.person ?: continue
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                EmojiBadge(p.emoji, MaterialTheme.colorScheme.surface, size = 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${a.happening.marker} ${a.happening.title}", style = MaterialTheme.typography.bodySmall)
                    if (opens(a)) {
                        // the storyteller's story is listened to; a dialog is talked
                        val label = if (a.talks) "💬 ${bi("common.talkVerb")}" else "📖 ${bi("common.listen")}"
                        OutlinedButton(onClick = { onTalk(a) }, modifier = Modifier.padding(top = 6.dp)) { Text(label) }
                    }
                }
            }
        }
    }
    for (h in scene.happenings.filter { it.id in doneToday }) {
        val p = scene.people.firstOrNull { it.id == h.who }
        Text("✓ ${bi("sceneScreen.doneToday", "orEmpty" to (p?.emoji.orEmpty()), "hTitle" to h.title)}", style = MaterialTheme.typography.bodyMedium, color = AlpineGreen)
    }
    for (p in always) {
        Text(bi("sceneScreen.here", "pEmoji" to p.emoji, "pName" to p.name), style = MaterialTheme.typography.bodyMedium)
    }
    for (p in asleep) {
        Text("💤 ${bi("sceneScreen.asleep", "pEmoji" to p.emoji, "pName" to p.name)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (later.isNotEmpty()) {
        // Who comes later today, by the part of the day: "Zvečer · In the evening: 👵 Babica Micka".
        for ((part, list) in later.groupBy({ it.first }, { it.second })) {
            val who = list.mapNotNull { it.person }.distinctBy { it.id }.joinToString(", ") { "${it.emoji} ${it.name}" }
            Text("🕰️ ${partLabel(part)}: $who", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (here.isEmpty() && doneToday.isEmpty()) {
        Text(
            "🌙 ${bi("sceneScreen.quietHereNow")}. ${bi("sceneScreen.comeBackTomorrow")}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Zvečer · In the evening" */
private fun partLabel(part: TimeOfDay): String = when (part) {
    TimeOfDay.MORNING, TimeOfDay.DAWN -> bi("sceneScreen.morning")
    TimeOfDay.AFTERNOON -> bi("sceneScreen.afternoon")
    TimeOfDay.EVENING -> bi("sceneScreen.evening")
    TimeOfDay.NIGHT -> bi("sceneScreen.atNight")
}
