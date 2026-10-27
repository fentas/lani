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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
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
import si.lanisce.lani.game.scene.ISpy
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.Offer
import si.lanisce.lani.game.scene.SceneOffers
import si.lanisce.lani.game.School
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
import si.lanisce.lani.ui.game.label
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
    val now = remember(minute) { si.lanisce.lani.app.QaHooks.sceneNow(LocalDateTime.now()) }
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
    val about = remember(scene, state, now, day, world) {
        state?.let { s ->
            val present = Residents.present(s, now.toLocalDate())
            Whereabouts.inScene(scene, s, everyone.filter { present == null || it.id in present }, now, day ?: Routine.Day(), scenes.all, seenIn = world.here)
        }.orEmpty()
    }
    // wolves, a bear or a storm about (companion/GAME.md "People react"): who the reaction has here (at home in their room,
    // by the fire, up the watchtower), each with a marker and a row under "Danes tukaj" saying what they say of it; at home,
    // a line for inside ("Doma smo na suhem.")
    val reacting = remember(state) { state?.event?.takeIf { si.lanisce.lani.game.villagers.Reactions.quieting(it.kind) } }
    val homeHere = remember(scene, state, world, day, about) {
        val s = state
        if (s == null || reacting == null) emptySet()
        else {
            val place = Whereabouts.placeOf(scene, s, scenes.all, world.here)
            about.mapNotNull { p -> (scene.people.firstOrNull { it.id == p.id }?.villager ?: p.id).takeIf { day?.homes?.get(it) == place } }.toSet()
        }
    }
    /** Someone drawn here: the scene's person, or one of the village the scene doesn't list (Lovec Jože at home with Janez). */
    fun personOf(id: String?): ScenePerson? = id?.let { i ->
        scene.people.firstOrNull { it.id == i }
            ?: everyone.firstOrNull { it.id == i }?.let { v -> ScenePerson(v.id, v.name, v.emoji, v.art, "", villager = v.id) }
    }
    /** What [p] says of the event now ([reacting]): at home a line for inside. */
    fun reactionOf(p: ScenePerson): si.lanisce.lani.ui.villagers.Said? =
        p.villager?.let { id -> everyone.firstOrNull { it.id == id } }?.let { v ->
            si.lanisce.lani.ui.villagers.VillagerLogic.reaction(v, state, now.toLocalDate(), everyone, home = v.id in homeHere)
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
    // «Vidim, vidim» being played here (companion/SCENES.md, "I spy")
    val ispy = vm.ispy.play?.takeIf { it.sceneId == scene.id }
    // A villager speaks with their own voice (their speaker), anyone else with their sprite's.
    fun voiceOf(p: ScenePerson) = SceneWords.voiceOf(p.art, p.villager?.let { vm.villagers.byId(it) })

    var selected by rememberSaveable(scene.id) { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf<String?>(null) } // the slot just found for the first time
    var greeted by remember { mutableStateOf<String?>(null) } // someone without a dialog, saying hello
    var skyCard by remember { mutableStateOf<si.lanisce.lani.game.sky.SkyTap?>(null) } // the moon, a star … tapped in the sky
    var skyList by remember { mutableStateOf<List<si.lanisce.lani.game.sky.SkyTap>?>(null) } // TalkBack's "Tonight's sky"
    // the building this scene is in (the house of a room, the smithy …; none for the fire): its card from the top bar, the
    // upgrade a tap away without going back to the village; upgraded, the room redraws at its new level. Back from its
    // words' run, the card is open again (AppViewModel.practiseUpgrade).
    val home = remember(state, world) { world.here?.let { h -> state?.buildings?.firstOrNull { it.id == h.id } } }
    var homeOpen by remember { mutableStateOf(vm.takeBuildingCard()?.let { it == home?.id } == true) }
    var upgradedTo by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(home?.id) { home?.let { vm.loadBuildingWords(it.id) } }
    // what its upgrade asks for comes from its scenes and their packs, as the learner knows them
    val homeReady = remember(state, home, vm.scenes.packs, vm.scenes.all, vm.content.dashboard) {
        val s = state
        if (s == null || home == null || home.damaged) false
        else si.lanisce.lani.game.GameEngine.upgradeOption(s, home.id, vm.upgradeWords(s, home.id), vm.school.gate(s, home.id))?.available == true
    }
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

    // What each of the scene's people has for the learner (SceneOffers; companion/SCENES.md "What someone has here"): the
    // class's lesson by the teacher where the class meets, their happenings here that open something, and their requests
    // up, as many as the map's bubble over them counts (the same queue).
    val villagers = vm.villagers.all
    val course = vm.school.course
    val requests = remember(state, villagers, now.toLocalDate()) {
        state?.let { s -> SceneOffers.requestsBy(Residents.queue(s, villagers, now.toLocalDate()).up, villagers) }.orEmpty()
    }
    val classHere = remember(state, scene, course) { state != null && SceneOffers.classHere(scene, state, course) }
    val lesson = remember(state, scene, course) { state?.let { SceneOffers.lesson(scene, it, course) } }
    val teacher = remember(villagers) { vm.school.teacher() }
    fun offersOf(p: ScenePerson): List<Offer> = SceneOffers.of(p, here, ::opens, requests, lesson, teacher?.id)
    /** Someone with several things tapped (in the picture, their marker): the choice of them, over the scene. */
    var choosing by remember { mutableStateOf<String?>(null) }

    fun take(o: Offer) {
        choosing = null
        selected = null
        greeted = null
        when (o) {
            is Offer.Lesson -> vm.startLesson(o.lesson)
            is Offer.Talk -> talkTo(o.happening)
            is Offer.Request -> vm.startQuest(o.quest.id)
        }
    }

    /** [p] tapped: the one thing they have straight away (a dialog, a request), the choice of several (the lesson's too), else a hello. */
    fun approach(p: ScenePerson) {
        val offers = offersOf(p)
        val one = SceneOffers.direct(offers)
        when {
            one != null -> take(one)
            offers.isNotEmpty() -> { selected = null; greeted = null; choosing = p.id }
            else -> { selected = null; greeted = p.id }
        }
    }

    // a wrong guess near the thing spied is warm: their areas in the picture within a few pixels
    fun near(a: String, b: String?): Boolean {
        val ha = anchors.hits.lastOrNull { it.target == SceneTarget.Thing(a) } ?: return false
        val hb = b?.let { s -> anchors.hits.lastOrNull { it.target == SceneTarget.Thing(s) } } ?: return false
        val dx = maxOf(hb.left - ha.right, ha.left - hb.right, 0)
        val dy = maxOf(hb.top - ha.bottom, ha.top - hb.bottom, 0)
        return dx * dx + dy * dy <= ISpy.WARM_PX * ISpy.WARM_PX
    }

    // «Vidim, vidim»: a thing guessed, in the picture or among the chips
    fun guess(slot: String) {
        val g = vm.ispy.play?.takeIf { it.sceneId == scene.id } ?: return
        if (scene.objects.none { it.slot == slot }) return
        selected = null
        greeted = null
        vm.ispy.tap(scene, slot, near(slot, g.run.current?.slot))
    }

    fun tapped(t: SceneTarget?) {
        if (scenes.talk != null) return
        // a round of «Vidim, vidim» on: a thing tapped is the learner's guess (a person, the scenery: nothing)
        if (vm.ispy.play?.takeIf { it.sceneId == scene.id }?.run?.step == ISpyRun.Step.FIND) {
            (t as? SceneTarget.Thing)?.slot?.let(::guess)
            return
        }
        when (t) {
            is SceneTarget.Thing -> scene.objects.firstOrNull { it.slot == t.slot }?.let(::select) ?: run { selected = null }
            is SceneTarget.Person -> {
                // asleep: they only turn over and mumble (the scene view's poke), nothing to say
                if (sleepers.any { it.id == t.id }) { selected = null; greeted = null; return }
                scene.people.firstOrNull { it.id == t.id }?.let(::approach) ?: run { selected = null; greeted = t.id }
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

    // A person's new line is said aloud in their voice, and they're drawn talking meanwhile (a project's step has several
    // people: each says their own lines); the learner's own line first, in their voice (DialogVoice). The panel, the bubble
    // and the person's reaction come first; the next line, as the learner taps on, cuts it.
    fun speakerOf(id: String?): ScenePerson? = talk?.step?.cast?.firstOrNull { it.id == id } ?: talk?.person
    val talkingId = rememberDialogVoice(speaker, talk) { who ->
        val p = speakerOf(who) ?: talk!!.person
        p.id to voiceOf(p)
    }
    // whom the learner answers: the person of the dialog, or in a project's step whoever spoke last
    val facing = if (talk?.step != null) talk.run.said.lastOrNull { it.who != null }?.who ?: talk.person.id else talk?.person?.id
    val talking = talkingId != null && talkingId == facing
    // The lines of «Vidim, vidim» come out one after another (a game adds several at once: its line and the first clue): each
    // of the child's typed in and said in the child's voice to its end before the next starts, the child drawn talking
    // meanwhile (LinesInTurn). A tap on another 🔊 stops the line at once (the next waits for it); leaving the game stops it.
    val ispyLines = remember(ispy?.game) { LinesInTurn() }
    LaunchedEffect(ispy?.game) {
        val g = ispy ?: return@LaunchedEffect
        val who = voiceOf(g.host.person)
        val lines = snapshotFlow { vm.ispy.play?.takeIf { it.game == g.game && it.sceneId == scene.id }?.run?.said.orEmpty() }
        ispyLines.follow(lines) { line -> speaker.sayInTurn(line.sl, voiceName = who.voice, fallback = who.fallback, person = who.person) }
    }
    val ispyTalking = ispyLines.talking
    // The scene takes part in the dialog: the weather and effects its lines bring, how the person reacts (SceneLife.kt).
    var micOn by remember { mutableStateOf(false) }
    // where the happenings on here have their people while they wait (Nejc in his bed), and the one being played until its
    // dialog closes, done or not (Happening.act)
    val waiting = remember(here, talk?.happening) { (here.map { it.happening } + listOfNotNull(talk?.happening)).filter { it.act.isNotEmpty() }.distinctBy { it.id } }
    val cues = rememberSceneCues(state, scene, talk?.run, now.toLocalDate(), waiting)
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
            vm.ispy.play?.sceneId == scene.id -> { vm.ispy.close(scene); selected = null }
            choosing != null -> choosing = null
            selected != null || greeted != null -> { selected = null; greeted = null }
            else -> vm.openVillage()
        }
    }

    val present = remember(state) { state?.let { Residents.present(it, LocalDate.now()) } }
    val ispyHost = ispy?.host?.person?.id
    val cast = talk?.step?.cast.orEmpty()
    val drawn = remember(scene, here, sleepers, about, talk?.person, cast, talkingId, facing, present, pose, state, ispyTalking, ispyHost) {
        // whoever a dialog left somewhere for the day (act_stays: Luka by the lantern while it rains) is still there, and
        // who is here by their day (France hoeing the field)
        val stayed = Stage.stayed(scene, DayAct.of(state, now.toLocalDate(), scene.id), present)
        val base = Sleep.inBed((Happenings.peopleIn(scene, here, present) + stayed + about).distinctBy { it.id }, sleepers)
        // a project's step: its people on the stage where it puts them; whoever else stood there (or is one of them under the
        // scene's own name) makes way
        val ids = cast.map { it.id }.toSet()
        val spots = cast.map { it.slot }.toSet()
        fun villagerOf(id: String) = scene.people.firstOrNull { it.id == id }?.villager ?: id
        val staged = if (cast.isEmpty()) base else base.filter { it.id !in ids && villagerOf(it.id) !in ids && it.slot !in spots } + cast.map { PersonInScene(it.id, it.art, it.slot) }
        // Whoever is talking stays until the dialog is closed, also once it's done for today.
        val all = talk?.person?.takeIf { p -> staged.none { it.id == p.id } }?.let { staged + PersonInScene(it.id, it.art, it.slot) } ?: staged
        all.map { it.copy(talking = (talkingId != null && it.id == talkingId) || (ispyTalking && it.id == ispyHost), pose = if (it.id == facing) pose else it.pose) }
    }
    // «Vidim, vidim» (companion/SCENES.md, "I spy"): a child in the picture, or one of the village's who comes by (awake, not
    // busy elsewhere; QA's hook takes any child), offers it while the day has games left; in a game the child stays
    val children = remember(everyone, state, now, day, present, si.lanisce.lani.app.QaHooks.ispy) {
        val forced = si.lanisce.lani.app.QaHooks.ispy?.substringBefore('/') == scene.id
        everyone.filter { v ->
            ISpy.isChild(v.art) && (forced || state == null || (
                (present == null || v.id in present) && v.id !in (day?.busy.orEmpty()) &&
                    si.lanisce.lani.game.villagers.Routine.now(v, state, now, day ?: Routine.Day()).where != si.lanisce.lani.game.villagers.Where.ASLEEP
                ))
        }
    }
    val offer = remember(scene, state, drawn, children, groups, ispy != null, talk != null, si.lanisce.lani.app.QaHooks.ispy) {
        if (talk != null || ispy != null) null
        else vm.ispy.offer(scene, state, drawn, children, { id -> everyone.firstOrNull { it.id == id } }, groups.now.map { it.slot }.toSet(), now.toLocalDate())
    }
    val host = ispy?.host ?: offer?.host
    val people = remember(drawn, host, ispyTalking) {
        val p = host?.takeIf { h -> h.comes && drawn.none { it.id == h.person.id } }?.person
        if (p == null) drawn else drawn + PersonInScene(p.id, p.art, p.slot, talking = ispyTalking && p.id == ispyHost)
    }
    fun startISpy(o: si.lanisce.lani.app.ISpyOffer) {
        selected = null
        greeted = null
        if (!vm.ispy.start(scene, o, state, groups.now.map { it.slot }.toSet(), found)) vm.notices.banner = bi("ispy.nothing")
    }
    // where the picture is in the window: a debug build logs where a round's things are on the screen, for QA to tap them
    var viewAt by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(ispy?.run?.round, ispy?.run?.step, anchors.hits.isNotEmpty(), viewAt) {
        val r = ispy?.run?.takeIf { it.step == ISpyRun.Step.FIND } ?: return@LaunchedEffect
        val at = groups.now.mapNotNull { o ->
            anchors.bounds(SceneTarget.Thing(o.slot))?.let { b -> o.slot to ((viewAt.x + b.center.x).toInt() to (viewAt.y + b.center.y).toInt()) }
        }.toMap()
        vm.ispy.log(r.round, r.current?.slot.orEmpty(), r.current?.word.orEmpty(), at)
    }
    // a tap turn's places on the screen: a debug build logs them (tag TapTurn), for QA to tap them in the picture
    LaunchedEffect(talk?.key, talk?.run?.at, talk?.run?.mode, anchors.hits.isNotEmpty(), viewAt) {
        val run = talk?.run ?: return@LaunchedEffect
        if (!si.lanisce.lani.BuildConfig.QA || run.mode != si.lanisce.lani.game.TurnMode.TAP) return@LaunchedEffect
        val at = run.taps.mapNotNull { t ->
            (anchors.bounds(SceneTarget.Thing(t)) ?: anchors.bounds(SceneTarget.Person(t)))?.let { b -> "$t@${(viewAt.x + b.center.x).toInt()},${(viewAt.y + b.center.y).toInt()}" }
        }
        val right = run.dialog.lines.getOrNull(run.at)?.choices?.firstOrNull { it.ok }?.tap
        android.util.Log.d("TapTurn", "turn ${run.at} right $right places ${at.joinToString(" ")}")
    }
    // where the scene's things and people are on the screen (their areas: left, top, right, bottom): a debug build logs them
    // (tag SceneAt) once the camera has come to rest, for QA to tap them in the picture (the blackboard, the teacher) where
    // nothing lies over them
    LaunchedEffect(scene.id, anchors.hits.size, viewAt, people.map { it.id }, anchors.camera.camera) {
        if (!si.lanisce.lani.BuildConfig.QA || anchors.hits.isEmpty()) return@LaunchedEffect
        delay(400)
        fun at(t: SceneTarget, id: String) = anchors.bounds(t)?.translate(viewAt.x.toInt(), viewAt.y.toInt())?.let { b -> "$id@${b.left},${b.top},${b.right},${b.bottom}" }
        val things = scene.objects.mapNotNull { at(SceneTarget.Thing(it.slot), it.slot) }
        val who = people.mapNotNull { at(SceneTarget.Person(it.id), it.id) }
        android.util.Log.d("SceneAt", "scene ${scene.id} things ${things.joinToString(" ")} people ${who.joinToString(" ")}")
    }
    val bubbleColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val topLimit = WindowInsets.statusBars.getTop(density) + with(density) { 56.dp.roundToPx() }
    // the top bar's pills on the screen (TopBarRow): the markers and bubbles over the picture keep off them
    val pills = remember { mutableStateMapOf<String, IntRect>() }
    // the markers over the picture on the screen, by who they're over: a later one keeps off them
    val markerRects = remember { mutableStateMapOf<String, IntRect>() }
    val keepOff = { pills.values.map { it.translate(-viewAt.x.toInt(), -viewAt.y.toInt()) } }

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
                highlight = talk?.person?.id ?: ispy?.run?.solved ?: selected ?: greeted,
                onTap = ::tapped,
                modifier = Modifier.fillMaxWidth().height(sceneHeight).onGloballyPositioned { viewAt = it.positionInWindow() },
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
                targetState = when {
                    talk != null -> 1
                    ispy != null -> 2
                    else -> 0
                },
                transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { it / 3 } + fadeOut()) },
                modifier = Modifier.fillMaxSize().padding(top = panelTop),
                label = "panel",
            ) { mode ->
                val t = scenes.talk?.takeIf { it.sceneId == scene.id }
                val g = vm.ispy.play?.takeIf { it.sceneId == scene.id }
                if (mode == 2 && g != null) {
                    ISpyPanel(
                        vm, scene, g, voiceOf(g.host.person), shown = ispyLines.out, visible = groups.now,
                        onTap = ::guess,
                        onMore = vm.ispy::more,
                        onReveal = vm.ispy::reveal,
                        onNext = { selected = null; vm.ispy.next(scene) },
                        onClose = { vm.ispy.close(scene); selected = null },
                        onWord = { slot -> greeted = null; selected = slot },
                    )
                } else if (mode == 1 && t != null) {
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
                        // a project's step: its people's lines show their faces and are read in their voices
                        speakers = t.step?.cast.orEmpty().associate { p -> p.id to Speaking(p.emoji, voiceOf(p)) },
                    )
                } else {
                    val asleep = sleepers.mapNotNull { s ->
                        scene.people.firstOrNull { it.id == s.id }
                            ?: everyone.firstOrNull { it.id == s.id }?.let { v -> ScenePerson(v.id, v.name, v.emoji, v.art, s.slot, villager = v.id) }
                    }
                    // "Danes tukaj": a card for each who is here (in the picture, asleep, or with a happening on) with something,
                    // and the class's lesson by the teacher while the class meets here, also while she isn't in the picture
                    val cards = buildList {
                        for (p in scene.people) {
                            val ons = here.filter { it.person?.id == p.id }
                            if (ons.isEmpty() && people.none { it.id == p.id } && sleepers.none { it.id == p.id }) continue
                            val offers = offersOf(p)
                            if (ons.isEmpty() && offers.isEmpty()) continue
                            add(HereCard(p.id, p.emoji, p.name, ons, offers, school = classHere && p.villager != null && p.villager == teacher?.id))
                        }
                        if (lesson != null && none { c -> c.offers.any { it is Offer.Lesson } }) add(
                            HereCard(teacher?.id ?: "teacher", teacher?.emoji ?: "👩‍🏫", teacher?.name ?: si.lanisce.lani.l10n.inTarget("school.teacher"), emptyList(), listOf(Offer.Lesson(lesson)), school = true),
                        )
                    }.sortedByDescending { c -> c.offers.any { it is Offer.Lesson } }
                    // wolves, a bear or a storm about: who the reaction has here, each with what they say of it
                    val reacted = if (reacting == null) emptyList() else about.filter { d -> sleepers.none { it.id == d.id } }
                        .mapNotNull { d -> personOf(d.id)?.let { p -> p to reactionOf(p) } }
                    WordsPanel(
                        vm, scene, groups, here, later, doneToday, found, selected, asleep, onWord = ::select, onTalk = ::talkTo, onLearn = { vm.learnScene(scene.id) }, opens = ::opens,
                        ispy = { ISpyOfferCard(offer, offer == null && vm.ispy.doneToday(scene, state, now.toLocalDate()), vm.dialogPrefs.translations) { offer?.let(::startISpy) } },
                        cards = cards, onTake = ::take, onSchool = { vm.openSchool(fromVillage = true) },
                        reaction = reacting?.kind, reacted = reacted, onGreet = ::approach,
                    )
                }
            }
        }

        // Over the picture: markers over people with something to say, the word or greeting bubble, the top bar.
        // They follow the camera (read when placing) and wait, unplaced, while their thing is out of view.
        if (talk == null) {
            // one marker over each who is here (awake) with something for the learner: 📚 the class's lesson, a happening's
            // emoji, 📋 requests, their requests counted as on the map ("!", "2"); each keeps off the markers before it (two
            // people side by side: the teacher by her pupils), as off the top bar's pills
            val markers = ArrayList<String>()
            fun markerKeepOff(before: List<String>): () -> List<IntRect> = {
                keepOff() + before.mapNotNull { k -> markerRects[k]?.translate(-viewAt.x.toInt(), -viewAt.y.toInt()) }
            }
            // wolves, a bear or a storm about: over each the reaction has here, its emoji ("⛈️"), a tap their line of it
            if (ispy == null && reacting != null) for (d in about) {
                if (sleepers.any { it.id == d.id }) continue
                val p = personOf(d.id) ?: continue
                val target = SceneTarget.Person(p.id)
                if (!anchors.drawn(target)) continue
                val key = "person:${p.id}"
                val says = reactionOf(p)?.let { "${p.name}: ${it.sl}" } ?: p.name
                // small and quiet, on the dark: nothing waits for the learner, a tap greets them with their line
                Anchored({ anchors.shown(target)?.let { IntRect(it.left, it.top, it.right, it.top) } }, topLimit = 0, color = REACTED, keepOff = markerKeepOff(markers.toList())) {
                    SpeechMarker(reacting.kind.emoji, says, REACTED, modifier = Modifier.pill(markerRects, key), small = true) { approach(p) }
                }
                markers += key
            }
            if (ispy == null) for (p in scene.people) {
                if (people.none { it.id == p.id } || sleepers.any { it.id == p.id }) continue
                if (reacting != null && about.any { it.id == p.id }) continue
                val offers = offersOf(p)
                val emoji = SceneOffers.marker(offers) ?: continue
                val target = SceneTarget.Person(p.id)
                if (!anchors.drawn(target)) continue
                val key = "person:${p.id}"
                Anchored({ anchors.shown(target)?.let { IntRect(it.left, it.top, it.right, it.top) } }, topLimit = 0, color = XpGold, keepOff = markerKeepOff(markers.toList())) {
                    SpeechMarker(emoji, markerSays(vm, p, offers), XpGold, requests = SceneOffers.requests(offers), modifier = Modifier.pill(markerRects, key)) { approach(p) }
                }
                markers += key
            }
            // the child who offers «Vidim, vidim» (unless they have their own marker over them)
            if (ispy == null && offer != null && scene.people.firstOrNull { it.id == offer.host.person.id }?.let(::offersOf).isNullOrEmpty()) {
                val target = SceneTarget.Person(offer.host.person.id)
                if (anchors.drawn(target)) {
                    Anchored({ anchors.shown(target)?.let { IntRect(it.left, it.top, it.right, it.top) } }, topLimit = 0, color = XpGold, keepOff = markerKeepOff(markers.toList())) {
                        SpeechMarker("🔍", "${offer.host.person.name}: ${bi("ispy.title")}. ${bi("ispy.play")}", XpGold, modifier = Modifier.pill(markerRects, "ispy")) { startISpy(offer) }
                    }
                }
            }
            // a word seen before opens over the picture even while its thing isn't in it (the owl's eyes by day), with when it shows
            val sel = spec.objects.firstOrNull { it.slot == selected }
            val selTarget = sel?.let { SceneTarget.Thing(it.slot) }
            if (sel != null && selTarget != null && (anchors.drawn(selTarget) || WordChips.opens(sel, groups))) {
                val sticker = SceneWords.packOf(scene, sel)?.let { scenes.stickers.picture(it, sel.word, sel.emoji) }?.let { StickerSpot(scene.art, sel.slot) }
                // the blackboard where the class meets: its word, and the class's lesson and units a tap away
                val school = SceneOffers.opensSchool(sel.slot, classHere)
                val bubble = @Composable {
                    WordBubble(
                        sel, SceneWords.learned(scene, sel, scenes.packs), fresh = fresh == sel.slot, speaker = speaker, color = bubbleColor,
                        sticker = sticker, note = WordChips.noteOf(sel.slot, groups),
                        action = if (school) { { SchoolEntries(vm, lesson, onLesson = { take(Offer.Lesson(it)) }, onSchool = { selected = null; vm.openSchool(fromVillage = true) }) } } else null,
                    )
                }
                if (anchors.drawn(selTarget)) Anchored({ anchors.shown(selTarget) }, topLimit, bubbleColor, keepOff = keepOff) { bubble() }
                else Box(Modifier.fillMaxWidth().height(sceneHeight).statusBarsPadding().padding(top = 56.dp, start = 10.dp, end = 10.dp), contentAlignment = Alignment.Center) { bubble() }
            }
            val who = personOf(greeted)
            val whoTarget = who?.let { SceneTarget.Person(it.id) }
            if (who != null && whoTarget != null && anchors.drawn(whoTarget)) {
                val line = here.firstOrNull { it.person?.id == who.id }?.happening?.title
                // wolves about, the merchant come, a fight just won (companion/GAME.md "People react"): what they say of it;
                // at home where the event sent them, a line for inside
                val said = remember(who, state, minute, homeHere) { reactionOf(who) }
                Anchored({ anchors.shown(whoTarget) }, topLimit, bubbleColor, keepOff = keepOff) { PersonBubble(who.emoji, who.name, line, now.hour, speaker, bubbleColor, voiceOf(who), said?.let { it.sl to it.en }) }
            }
            mumble?.let { (id, at) ->
                val target = SceneTarget.Person(id)
                if (anchors.drawn(target)) Anchored({ anchors.shown(target) }, topLimit, bubbleColor, keepOff = keepOff) { MumbleBubble(bubbleColor, at) }
            }
        }
        if (!lifted) Recenter(anchors.camera, Modifier.fillMaxWidth().height(sceneHeight).padding(10.dp))
        TopBar(
            scene, found.size, onBack = vm::openVillage, shown = !lifted, home = home, homeReady = homeReady, onHome = { homeOpen = true }, pills = pills,
            // in the school, and wherever the class meets (by the fire, under the linden): "📚" to its course (companion/GAME.md,
            // "The school")
            onSchool = if ((home?.type == si.lanisce.lani.game.BuildingType.SCHOOL || classHere) && course != null) { { vm.openSchool(fromVillage = true) } } else null,
        )
        if (allFound > 0) Confetti(key = "found-${scene.id}-$allFound")
        if (talk?.settled == true) Confetti(key = "talk-${talk.key}")
        upgradedTo?.let { Confetti(key = "upgraded-${home?.id}-$it") }
    }
    val s = state
    if (homeOpen && home != null && s != null) {
        // the village's card for it (ui/game/BuildingCard), over the scene: without going in, moving, replacing or growing
        si.lanisce.lani.ui.game.BuildingCard(
            vm, s, home, si.lanisce.lani.ui.game.rememberAdvanceCheck(vm, s),
            si.lanisce.lani.ui.game.BuildingCardActions(
                onClose = { homeOpen = false },
                onUpgraded = {
                    // done here: the card closes and the room shows its new level; the village shows it going up on the way back
                    homeOpen = false
                    upgradedTo = home.level + 1
                    vm.notices.banner = "⬆️ ${home.type.emoji} ${home.type.label()} · ${bi("common.level")} ${home.level + 1}"
                },
                onPractise = { homeOpen = false },
                onAges = { homeOpen = false; vm.openVillageAges() },
                onGather = { r -> homeOpen = false; vm.startGather(r) },
                onSchool = { homeOpen = false; vm.openSchool(fromVillage = true) },
                grows = false,
            ),
            sheet = true,
        )
    }
    // someone with several things tapped: the choice of them (the class's lesson first, the class's units, their dialogs,
    // their requests)
    choosing?.let { id -> scene.people.firstOrNull { it.id == id } }?.let { p ->
        val offers = offersOf(p)
        // nothing left meanwhile (done, the hour moved on): no choice
        LaunchedEffect(offers.isEmpty()) { if (offers.isEmpty()) choosing = null }
        if (offers.isNotEmpty()) OffersSheet(
            vm, p, offers, school = classHere && p.villager != null && p.villager == teacher?.id, opens = ::opens, onTake = ::take,
            onSchool = { choosing = null; vm.openSchool(fromVillage = true) }, onDismiss = { choosing = null },
        )
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
private fun TopBar(
    scene: SceneSpec, found: Int, onBack: () -> Unit, shown: Boolean = true,
    home: si.lanisce.lani.game.Building? = null, homeReady: Boolean = false, onHome: () -> Unit = {},
    pills: MutableMap<String, IntRect> = HashMap(), onSchool: (() -> Unit)? = null,
) {
    LightStatusIcons()
    // out of the way while a dialog's panel has risen over the picture (the keyboard up): it would lie on the panel's header
    AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) { TopBarRow(scene, found, onBack, home, homeReady, onHome, pills, onSchool) }
}

/** Where this pill is on the screen, in [pills] under [key] while it shows: what the bubbles over the picture keep off. */
@Composable
private fun Modifier.pill(pills: MutableMap<String, IntRect>, key: String): Modifier {
    DisposableEffect(key) { onDispose { pills.remove(key) } }
    return onGloballyPositioned { c ->
        val b = c.boundsInWindow()
        val r = IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
        if (pills[key] != r) pills[key] = r
    }
}

/** The room between two pills of the scene's top bar: enough to see each as one, over a busy picture. */
private val PILL_GAP = 10.dp

/**
 * Back to the village; the building the scene is in ([home]: its emoji and level, ⬆️ when its upgrade can be done now
 * ([homeReady]), ⚠️ while it is damaged), whose card [onHome] opens; the school ([onSchool]); what's found of the scene's
 * words. Where each pill is goes into [pills].
 *
 * The pills lie side by side, [PILL_GAP] apart, each with its faint rim ([OverlayPill]: 6 dp apart and rimless, the
 * nearly round "⛪ 1" read as tucked under "‹ Vas · Village" over the dark organ loft), none ever under another: what's
 * found keeps its place on the right, the pills on the left share what it leaves, and the way back gives way first: where
 * "‹ Vas · Village" doesn't fit (a narrow phone, a long base: "‹ Vas · Villaggio", a big font), it says the village's word
 * alone ("‹ Vas"), TalkBack still both.
 */
@Composable
internal fun TopBarRow(
    scene: SceneSpec, found: Int, onBack: () -> Unit, home: si.lanisce.lani.game.Building?, homeReady: Boolean, onHome: () -> Unit,
    pills: MutableMap<String, IntRect>, onSchool: (() -> Unit)? = null,
) {
    val gap = Arrangement.spacedBy(PILL_GAP)
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
        // the left ones, in what the found pill leaves: the building and the school first, the way back in the rest
        Row(Modifier.weight(1f), horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
            val back = "‹ ${bi("common.village")}"
            OverlayPill(Modifier.weight(1f, fill = false).pill(pills, "back")) {
                TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = back }) {
                    FitLabel(
                        back, "‹ ${si.lanisce.lani.l10n.inTarget("common.village")}", Color.White, MaterialTheme.typography.labelLarge,
                        Modifier.clearAndSetSemantics { },
                    )
                }
            }
            home?.let { b ->
                val mark = if (b.damaged) " ⚠️" else if (homeReady) " ⬆️" else ""
                // the building's name in each language of the label ("Koča, stopnja 3 … · Hut, level 3 …"), as the chronicle's
                val said = bi("sceneScreen.buildingCard", "building" to b.type.names, "level" to b.level) +
                    (if (b.damaged) ", ${bi("villageSheets.damaged")}" else if (homeReady) ", ${bi("sceneScreen.canUpgrade")}" else "")
                OverlayPill(Modifier.pill(pills, "home")) {
                    // TalkBack reads what it is and that it opens, not the emoji
                    TextButton(onClick = onHome, modifier = Modifier.semantics { contentDescription = said }) {
                        Text(
                            "${b.type.emoji} ${b.level}$mark", color = if (homeReady) XpGold else Color.White, style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, softWrap = false, modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
            }
            onSchool?.let { open ->
                OverlayPill(Modifier.pill(pills, "school")) {
                    TextButton(onClick = open) {
                        Text("📚 ${si.lanisce.lani.l10n.inTarget("school.title")}", color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        val total = scene.objects.size
        if (total > 0) {
            OverlayPill(Modifier.pill(pills, "found").semantics { contentDescription = bi("sceneScreen.foundTotal", "found" to found, "total" to total) }) {
                Text(
                    "🔍 $found/$total",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * [full] on one line where it fits in the room it is given, else [short] (ending in "…" where even that doesn't): a pill's
 * label that gives way on a narrow screen instead of pushing the next pill aside.
 */
@Composable
internal fun FitLabel(full: String, short: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        val fits = !constraints.hasBoundedWidth || measurer.measure(full, style, maxLines = 1, softWrap = false).size.width <= room
        Text(if (fits) full else short, color = color, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
    /** «Vidim, vidim» offered here (its card, [ISpyOfferCard]), before who is here today. */
    ispy: @Composable () -> Unit = {},
    /** Who is here with something ([HereCard]): the class's lesson, their happenings, their requests. */
    cards: List<HereCard> = emptyList(),
    onTake: (Offer) -> Unit = {},
    /** The class's units (the school's screen), by the teacher where the class meets. */
    onSchool: () -> Unit = {},
    /** Wolves, a bear or a storm about: who the reaction has here ([reacted]), each with what they say of it; [onGreet] greets one. */
    reaction: si.lanisce.lani.game.EventKind? = null,
    reacted: List<Pair<ScenePerson, si.lanisce.lani.ui.villagers.Said?>> = emptyList(),
    onGreet: (ScenePerson) -> Unit = {},
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
                Text(si.lanisce.lani.game.Hearths.sceneTitle(scene), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
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
        ispy()
        People(vm, scene, here, later, doneToday, present, asleep, onTalk, opens, cards, onTake, onSchool, reaction, reacted, onGreet)
    }
}

/**
 * Someone under "Danes tukaj" ([People]): [id] (a scene person's, or the teacher's villager id while she isn't in the
 * picture), their happenings here on now, what they have ([offers]: [SceneOffers.of]); [school]: the teacher where the class
 * meets, with the class's units besides.
 */
internal class HereCard(
    val id: String, val emoji: String, val name: String, val happenings: List<ActiveHappening>, val offers: List<Offer>, val school: Boolean,
)

/**
 * "Danes tukaj · Here today": who is here with something to say, who sleeps here, and what's done today; while wolves, a
 * bear or a storm are about ([reaction]), who the reaction has here ([reacted]: at home, by the fire) under the event's
 * name, each with what they say of it, a tap greeting them ([onGreet]).
 */
@Composable
private fun People(
    vm: AppViewModel, scene: SceneSpec, here: List<ActiveHappening>, later: List<Pair<TimeOfDay, ActiveHappening>>, doneToday: List<String>, present: Set<String>?,
    asleep: List<ScenePerson>, onTalk: (ActiveHappening) -> Unit, opens: (ActiveHappening) -> Boolean = { it.talks },
    cards: List<HereCard> = emptyList(), onTake: (Offer) -> Unit = {}, onSchool: () -> Unit = {},
    reaction: si.lanisce.lani.game.EventKind? = null, reacted: List<Pair<ScenePerson, si.lanisce.lani.ui.villagers.Said?>> = emptyList(),
    onGreet: (ScenePerson) -> Unit = {},
) {
    val always = Happenings.always(scene, present).filter { p ->
        cards.none { it.id == p.id } && here.none { a -> a.person?.id == p.id } && asleep.none { it.id == p.id } && reacted.none { it.first.id == p.id }
    }
    if (cards.isEmpty() && here.isEmpty() && later.isEmpty() && doneToday.isEmpty() && always.isEmpty() && asleep.isEmpty() && scene.happenings.isEmpty() && reacted.isEmpty()) return
    Text(bi("sceneScreen.hereToday"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
    if (reaction != null && reacted.isNotEmpty()) ReactedHere(reaction, reacted, onGreet)
    for (c in cards) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                EmojiBadge(c.emoji, MaterialTheme.colorScheme.surface, size = 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    // the class's lesson first, by the teacher: one tap into it, the class's units beside
                    val lesson = c.offers.firstNotNullOfOrNull { (it as? Offer.Lesson)?.lesson }
                    if (lesson != null || c.school) SchoolEntries(vm, lesson, onLesson = { onTake(Offer.Lesson(it)) }, onSchool = onSchool, modifier = Modifier.padding(bottom = 4.dp))
                    for (a in c.happenings) {
                        Text("${a.happening.marker} ${a.happening.title}", style = MaterialTheme.typography.bodySmall)
                        if (opens(a)) {
                            // the storyteller's story is listened to; a dialog is talked
                            val label = if (a.talks) "💬 ${bi("common.talkVerb")}" else "📖 ${bi("common.listen")}"
                            OutlinedButton(onClick = { onTalk(a) }, modifier = Modifier.padding(top = 6.dp)) { Text(label) }
                        }
                    }
                    // their requests up, as the map's bubble over them counts them: one tap into each
                    for (o in c.offers) if (o is Offer.Request) RequestRow(o.quest, Modifier.padding(top = 6.dp)) { onTake(o) }
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
    } else if (here.isEmpty() && doneToday.isEmpty() && cards.isEmpty() && reacted.isEmpty()) {
        Text(
            "🌙 ${bi("sceneScreen.quietHereNow")}. ${bi("sceneScreen.comeBackTomorrow")}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Who the village's event has here (companion/GAME.md "People react"): under its name ("⛈️ Nevihta · Storm"), a row for
 * each with their face, their name and what they say of it in the village's language, its meaning under it; a tap greets
 * them, as a tap on them in the picture does.
 */
@Composable
private fun ReactedHere(kind: si.lanisce.lani.game.EventKind, reacted: List<Pair<ScenePerson, si.lanisce.lani.ui.villagers.Said?>>, onGreet: (ScenePerson) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${kind.emoji} ${kind.sl} · ${kind.en}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            for ((p, said) in reacted) {
                Surface(onClick = { onGreet(p) }, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge(p.emoji, MaterialTheme.colorScheme.surfaceVariant, size = 36)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            said?.let {
                                Text(it.sl, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (it.en.isNotBlank()) Text(it.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The quiet marker over who an event has in a scene ([ReactedHere]): dark and see-through, unlike the gold of an offer. */
private val REACTED = Color(0xB3202630)

/** "Zvečer · In the evening" */
private fun partLabel(part: TimeOfDay): String = when (part) {
    TimeOfDay.MORNING, TimeOfDay.DAWN -> bi("sceneScreen.morning")
    TimeOfDay.AFTERNOON -> bi("sceneScreen.afternoon")
    TimeOfDay.EVENING -> bi("sceneScreen.evening")
    TimeOfDay.NIGHT -> bi("sceneScreen.atNight")
}

// --- what someone has here (companion/SCENES.md "What someone has here"; GAME.md "The school") --------------------------

/** The class's lesson's unit, its title as shown ("Biti: sem, si, je · To be …"). */
private fun lessonUnit(vm: AppViewModel, l: School.Lesson) = vm.school.unit(l.unit)

/**
 * What the lesson's button says: the unit begun goes on at its next step ("▶️ Naprej: Uvod · Next: Introduction", a
 * worksheet's title, the test), the unit suggested next begins ("▶️ Uči se · Learn").
 */
private fun lessonGo(vm: AppViewModel, l: School.Lesson): String {
    val unit = lessonUnit(vm, l) ?: return "▶️ ${bi("common.learn")}"
    val p = vm.game.state?.let { School.progress(it, unit.language, unit.id) }
    val step: si.lanisce.lani.l10n.Localized = when (val st = School.lessonStep(l, unit, p)) {
        School.Step.Intro -> si.lanisce.lani.l10n.Localized { si.lanisce.lani.l10n.L10n.text(it, "school.intro") }
        is School.Step.Sheet -> unit.sheet(st.id)?.let { ws -> si.lanisce.lani.l10n.Localized { lang -> si.lanisce.lani.data.Grammar.text(ws.title, lang.code) } }
            ?: return "▶️ ${bi("common.learn")}"
        School.Step.Test -> si.lanisce.lani.l10n.Localized { si.lanisce.lani.l10n.L10n.text(it, "school.test") }
        School.Step.Done, null -> return "▶️ ${bi("common.learn")}"
    }
    return "▶️ ${bi("school.continueStep", "step" to step)}"
}

/** "Učiteljica Mojca: Pouk · Lesson: Biti …, 2 prošnji · 2 requests": what the marker over [p] stands for, for TalkBack. */
private fun markerSays(vm: AppViewModel, p: ScenePerson, offers: List<Offer>): String {
    val first = when (val o = offers.first()) {
        is Offer.Talk -> bi("sceneScreen.talk", "pName" to p.name, "happeningTitle" to o.happening.happening.title)
        is Offer.Lesson -> "${p.name}: ${bi("school.lesson")}: ${lessonUnit(vm, o.lesson)?.titleShown(si.lanisce.lani.l10n.L10n.pair) ?: o.lesson.unit}"
        is Offer.Request -> "${p.name}: ${o.quest.title}"
    }
    val n = SceneOffers.requests(offers)
    return if (n == 0 || offers.first() is Offer.Request && n == 1) first else "$first, ${bi("sceneScreen.requests", "n" to n)}"
}

/**
 * The class's entries by the teacher where the class meets, and on the blackboard's word: "📚 Pouk · Lesson" with the unit
 * it goes on with, one tap into it ([onLesson], [lesson]: none with every unit open done), and "🏫 Vse enote · All units"
 * (the school's screen: [onSchool]).
 */
@Composable
internal fun SchoolEntries(vm: AppViewModel, lesson: School.Lesson?, onLesson: (School.Lesson) -> Unit, onSchool: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (lesson != null) {
            val unit = lessonUnit(vm, lesson)
            Text("📚 ${bi("school.lesson")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "${unit?.emoji ?: "★"} ${unit?.titleShown(si.lanisce.lani.l10n.L10n.pair) ?: lesson.unit}",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
            androidx.compose.material3.Button(
                onClick = { onLesson(lesson) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = XpGold, contentColor = Color(0xFF2B2118)),
                modifier = Modifier.padding(top = 2.dp).heightIn(min = 48.dp),
            ) { Text(lessonGo(vm, lesson)) }
        }
        TextButton(onClick = onSchool, modifier = Modifier.heightIn(min = 48.dp)) { Text("🏫 ${bi("school.allUnits")} ›") }
    }
}

/** One of someone's requests up: "📋 <title>", how close the learner is or what it pays; a tap opens it. */
@Composable
private fun RequestRow(q: si.lanisce.lani.game.Quest, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("📋 ${q.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    si.lanisce.lani.ui.game.progressText(q) ?: si.lanisce.lani.ui.game.formatRes(q.reward),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/**
 * The choice of what [p] has ([offers]), when a tap on them (or their marker) finds more than one, or the class's lesson:
 * the lesson first with the class's units ([school]), their dialogs, their requests.
 */
@Composable
private fun OffersSheet(
    vm: AppViewModel, p: ScenePerson, offers: List<Offer>, school: Boolean, opens: (ActiveHappening) -> Boolean,
    onTake: (Offer) -> Unit, onSchool: () -> Unit, onDismiss: () -> Unit,
) {
    si.lanisce.lani.ui.LaniSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val v = p.villager?.let { vm.villagers.byId(it) }
                if (v != null) si.lanisce.lani.ui.villagers.VillagerPortrait(v, 56.dp)
                else EmojiBadge(p.emoji, MaterialTheme.colorScheme.surfaceVariant, size = 56)
                Spacer(Modifier.width(12.dp))
                Text("${p.emoji} ${p.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            }
            val lesson = offers.firstNotNullOfOrNull { (it as? Offer.Lesson)?.lesson }
            if (lesson != null || school) SchoolEntries(vm, lesson, onLesson = { onTake(Offer.Lesson(it)) }, onSchool = onSchool)
            for (o in offers) if (o is Offer.Talk) {
                val a = o.happening
                Surface(
                    onClick = { onTake(o) }, color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${a.happening.marker} ${a.happening.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (a.talks) "💬 ${bi("common.talkVerb")}" else "📖 ${bi("common.listen")}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            val asks = offers.filterIsInstance<Offer.Request>()
            if (asks.isNotEmpty()) Text(
                "🙋 ${bi("villagerScreens.asksForHelp", "QUEST" to si.lanisce.lani.game.villagers.Bonds.QUEST, "HEART" to si.lanisce.lani.ui.villagers.HEART)}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (o in asks) RequestRow(o.quest) { onTake(o) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
