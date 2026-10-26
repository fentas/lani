package si.lanisce.lani.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildOption
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.LogEntry
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.PlotChoice
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.render.CanvasPoint
import si.lanisce.lani.game.render.SceneFit
import si.lanisce.lani.game.render.TownAnchors
import si.lanisce.lani.game.render.TownPeople
import si.lanisce.lani.game.scene.Keepers
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.NewWordsCard
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.stage.rememberReducedMotion
import si.lanisce.lani.ui.villagers.VillagerCard
import si.lanisce.lani.l10n.bi
import java.time.LocalDateTime
import androidx.compose.ui.draw.alpha
import kotlin.math.roundToInt

/** Which sheet is open over the village. */
sealed interface VillageSheet {
    data object Build : VillageSheet
    data object Info : VillageSheet
    data object Fire : VillageSheet
    data object Ages : VillageSheet
    /** "🧰 Skrinja · The chest": help, tools, goods (to give, sell and buy), the feast. */
    data object Chest : VillageSheet
    /** "📖 Preberi · Read": what a thing in the chest holds to read; back to the chest when closed. */
    data class Reading(val readable: si.lanisce.lani.game.Readings.Readable) : VillageSheet
    /** "🏗️ Skupni projekti · Village projects", at [focus] (a project id) or the first. */
    data class Projects(val focus: String? = null) : VillageSheet
    data class Resource(val res: Res) : VillageSheet
    /** "⇄ Zamenjaj · Trade", from resource [from]'s sheet: one resource for another after a short run (game/Trade). */
    data class Trade(val from: Res) : VillageSheet
    data class Building(val id: String) : VillageSheet
}

/**
 * "Moja vas" as a place to explore: the village fills the screen (pinch, pan, double tap), bubbles show what's
 * going on where, places open cards and scenes, and the scroll holds the village's collections and its history.
 */
@Composable
fun VillageScreen(vm: AppViewModel) {
    BackHandler(onBack = vm::home)
    if (!vm.game.introSeen) {
        GameIntro(onDone = vm.game::finishIntro)
        return
    }
    val s = vm.game.state
    if (s == null) {
        VillageLoading(vm::home)
        return
    }
    // the village's background sounds now: the birds by day, the crickets and the owl at night, its weather, the sea by the
    // coast, and on the picture's clock the thunder after each flash of a storm
    val townClock = si.lanisce.lani.game.render.rememberTownClock()
    si.lanisce.lani.ui.AmbientSound(
        vm.ambience,
        si.lanisce.lani.game.ambient.Place.Village(
            sea = si.lanisce.lani.game.culture.Cultures.current.world.backdrop?.sea == true,
            storm = s.event?.kind == si.lanisce.lani.game.EventKind.STORM,
            beach = si.lanisce.lani.game.render.VillageLayout.of(s).sea != null,
        ),
        si.lanisce.lani.game.ambient.Soundscape.villageSky(s, java.time.LocalDate.now()),
        townClock,
    )
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val attrs = remember(s) { GameEngine.attributes(s) }
    // with the cast: a workplace whose person has nowhere to live waits, and says how to make room (a dwelling's upgrade
    // with the words it asks for)
    val options = rememberBuildOptions(vm, s)
    val check = rememberAdvanceCheck(vm, s)
    val goal = remember(s, options, check, attrs) { nextGoal(s, options, check, attrs.caps) }
    // Where the Build sheet's outline goes: the goal's building lands beside its partner (Placement), others take the
    // next free plot.
    val buildPlot = remember(s, goal) {
        (goal as? NextGoal.Build)?.let { si.lanisce.lani.game.Placement.plotFor(s, it.option.type) }
            ?: (0 until PLOTS_PER_AGE[s.age.ordinal]).firstOrNull { p -> s.buildings.none { it.type.onPlot && it.plot == p } }
    }
    // back from a building's words run: its card again, the upgrade one tap away; from a building's card in a scene, the
    // ages. Both are taken (a card left waiting would open on a later visit)
    var sheet by remember {
        val card = vm.takeBuildingCard()
        val ages = vm.takeAgesCard()
        mutableStateOf<VillageSheet?>(card?.let { VillageSheet.Building(it) } ?: VillageSheet.Ages.takeIf { ages })
    }
    /** The plot chooser over the map: a building to put on a plot, or one to move (see [PlotBar]). */
    var choosing by remember { mutableStateOf<Choosing?>(null) }
    /** An age's moment over the village (companion/GAME.md "Ages and their celebrations"): grown just now, or played again. */
    var moment by remember { mutableStateOf<MomentRun?>(null) }
    /** The scroll's sheet is open (ui/game/VillageScroll.kt), and the collection it opened at (null: its grid). */
    var scrollOpen by remember { mutableStateOf(false) }
    var scrollAt by remember { mutableStateOf<Collection?>(null) }
    var card by remember { mutableStateOf<PlaceTap?>(null) }
    /** Someone tapped on the map (or their bubble): their card sits over the village. */
    var villager by remember { mutableStateOf<String?>(null) }
    /** The moon, a star, a planet … tapped in the night sky: its card. */
    var skyCard by remember { mutableStateOf<si.lanisce.lani.game.sky.SkyTap?>(null) }
    /** TalkBack's "Tonight's sky": what the sky shows now, a list to open. */
    var skyList by remember { mutableStateOf<List<si.lanisce.lani.game.sky.SkyTap>?>(null) }
    var villagerAt by remember { mutableStateOf<CanvasPoint?>(null) }

    // What's on changes with the time of day: look again every minute.
    val minute = rememberNow(30_000) / 60_000
    // QA's "hour:<h>" (a debug build's hook): the village as at that hour of today, what's on and who is where
    val qaHour = si.lanisce.lani.app.QaHooks.sceneHour
    val scenes = vm.scenes.all
    val active = remember(s, scenes, minute, qaHour) { vm.scenes.active(s, si.lanisce.lani.app.QaHooks.sceneNow(LocalDateTime.now())) }
    // an age's party tonight (its evenings), and its decorations on the map the day it came and the next (AgeUps); the
    // hour QA asks the village to be seen at counts
    val partyClock = remember(minute, qaHour) { si.lanisce.lani.app.QaHooks.sceneNow(LocalDateTime.now()) }
    val partyNow = remember(s, partyClock) { si.lanisce.lani.game.AgeUps.partyNow(s, partyClock) }
    val partyTonight = remember(s, partyClock) { si.lanisce.lani.game.AgeUps.partyTonight(s, partyClock) }
    val mapFestive = remember(s, partyClock) {
        si.lanisce.lani.game.AgeUps.decorOn(s, partyClock.toLocalDate())?.let { c ->
            si.lanisce.lani.game.Festive(c.decor, sign = 1f, gather = si.lanisce.lani.game.AgeUps.gathering(s, partyClock))
        }
    }
    // who is at a spot of the landscape now without living here (the charcoal burner by his kopa some evenings)
    val qaKopa = si.lanisce.lani.app.QaHooks.kopa
    val keepers = remember(s, minute, qaHour, qaKopa) { Keepers.here(s, si.lanisce.lani.app.QaHooks.sceneNow(LocalDateTime.now())) }
    val cast = vm.villagers.all
    val markers = remember(s, active, cast, keepers, partyNow) { TownMarkers.of(s, active, cast, keepers = keepers, party = partyNow) }
    /**
     * The people who live here, drawn about their homes, today's visitor (on the road, or at their building), the
     * stranger of the day's surprise at the road (the pedlar, the pilgrim), and who sits at a spot now (the burner).
     */
    val visitorId = Residents.visitorToday(s, java.time.LocalDate.now())
    val stranger = si.lanisce.lani.game.Surprises.stranger(s, java.time.LocalDate.now())
    val people = remember(s, cast, visitorId, stranger, keepers) {
        vm.villagers.people(s).filter { vm.villagers.livesHere(s, it.id) || it.id == visitorId } + listOfNotNull(stranger) + keepers.map(Keepers::villager)
    }
    // what's on today, and how many of the learner's own words each happening's dialog tests (the scroll's "📇 3 tvoje besede")
    val overview = remember(s, scenes, minute, cast, vm.content.dashboard, keepers, vm.forms.cache) {
        TownOverview.of(s, scenes, LocalDateTime.now(), cast, vm::levelIn, keepers, words = { a -> vm.dialogWords.inHappening(a, s) })
    }
    // Everyone is on the map at most once: who has a happening on stands at its place (far off, only their bubble shows
    // there, with their face), and a plain villager waits only where someone not drawn about the village has one. Who is
    // at a spot now stays there (the burner sits by his pile).
    val standing = remember(s, active, keepers) { TownMarkers.standing(s, active) + keepers.associate { it.keeper.id to it.place } }
    // the villagers' day (Routine): where each sleeps, the storytellers, who waits for the learner (their bubbles), the same
    // for the scenes (Whereabouts)
    val routineDay = remember(s, scenes, cast, active, vm.game.storyHeardAt, minute) {
        val today = java.time.LocalDate.now()
        si.lanisce.lani.game.scene.Whereabouts.day(scenes, s, cast, vm.villagers.people(s, today), today, vm.game.storyHeardAt)
    }
    val visitors = remember(s, active, people) { TownPeople.waiting(s, active, people) }
    val badges = remember(active) { active.associate { "happening:${it.key}" to it.happening.marker.takeIf { m -> m != it.person?.emoji } } }
    val cam = rememberTownCamera()
    // Zoomed out, places are close together: one bubble each; closer in, they fan out.
    val zoomedOut by remember { derivedStateOf { cam.rig?.let { cam.camera.scale < it.minScale * 1.5f } ?: false } }
    val shown = remember(markers, zoomedOut) { if (zoomedOut) clusterBubbles(markers) else foldBubbles(markers) }
    // The HUD (companion/GAME.md, "The village's HUD"): its bar, the row of cards under it (the learner's choice on this
    // phone; the plot chooser hides it by itself), a card's section in a sheet
    val hudPrefs = vm.hudPrefs
    val hudState = remember { HudState(hudPrefs) }
    /** Counts the plot chooser's openings: the HUD's ring tapped while it is on shows the cards for that one only. */
    var chooserRun by remember { mutableIntStateOf(0) }
    /** What the HUD's bar covers at the top (the status bar's included), and its row of cards under it when shown. */
    var hudBarH by remember { mutableIntStateOf(0) }
    var hudRowH by remember { mutableIntStateOf(0) }
    fun hudCards() = hudState.cards(chooserRun.takeIf { choosing != null })
    /**
     * What the HUD covers at the top, for the camera's home view and framing and the bubbles: its bar and, while they show,
     * its cards (at once as they are shown or hidden: the plot chooser frames its plots below the bar alone); 0 until the
     * bar is measured.
     */
    fun hudInset() = hudBarH + if (hudCards()) hudRowH else 0
    var barH by remember { mutableIntStateOf(0) }
    var cardH by remember { mutableIntStateOf(0) }
    var plotBarH by remember { mutableIntStateOf(0) }

    /** Opens the plot chooser over the map (the sheets and cards close; [Choosing.cancel] and [Choosing.done] lead back). */
    val choose: (Choosing) -> Unit = { c ->
        if (c.choice.open(s)) {
            sheet = null; card = null; villager = null; scrollOpen = false; hudState.section = null
            chooserRun++
            choosing = c
        }
    }
    // QA's "build:<type>" (a debug build, app/QaHooks): the plot chooser for it, once the village is ready for one
    val qaBuild = si.lanisce.lani.app.QaHooks.build
    LaunchedEffect(qaBuild, s) {
        val type = qaBuild ?: return@LaunchedEffect
        val c = Choosing(PlotChoice.build(s, type))
        if (c.choice.open(s)) {
            si.lanisce.lani.app.QaHooks.tookBuild()
            choose(c)
        }
    }
    /**
     * A building chosen on the build sheet or the goal card: one that stands on a plot goes to the plot chooser first (the
     * learner picks the plot; it's paid for there, on "✓"), the palisade straight up round the clearing.
     */
    val build: (BuildOption, Boolean) -> Unit = { o, withMoba ->
        if (o.type.onPlot) choose(Choosing(PlotChoice.build(s, o.type, withMoba), cancel = sheet.takeIf { it == VillageSheet.Build }))
        else if (vm.game.build(o.type, withMoba) != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            sheet = null
            scrollOpen = false
        }
    }
    /** The chooser confirmed on [plot]: built (or moved) there, and back to where it leads; nothing when it couldn't be. */
    val place: (Int?) -> Unit = { plot ->
        choosing?.let { c ->
            val placed = vm.game.place(c.choice, plot) != null
            if (placed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            choosing = null
            // it couldn't be (the resources gone meanwhile): back where it came from, which says why
            sheet = if (placed) c.done else c.cancel
        }
    }
    val advance: () -> Unit = {
        val from = s.age
        vm.game.advance()?.let { to ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            sheet = null
            scrollOpen = false
            card = null
            villager = null
            hudState.section = null
            // the moment: the village growing on the map, its celebration, then what's new
            val grown = vm.game.state ?: s
            moment = MomentRun(si.lanisce.lani.game.AgeUps.moment(from, to, grown), grown, replay = false)
        }
    }
    /** "🎉 Poglej spet · Watch again" (the chronicle): [age]'s moment again, on the village as it was the day it grew. */
    val replay: (Age) -> Unit = { a ->
        val before = Age.entries.getOrNull(a.ordinal - 1)
        if (before != null && a <= s.age) {
            scrollOpen = false
            card = null
            villager = null
            hudState.section = null
            val drawn = si.lanisce.lani.game.AgeUps.asItWas(s, a)
            moment = MomentRun(si.lanisce.lani.game.AgeUps.moment(before, a, drawn), drawn, replay = true)
        }
    }
    // QA's "ageup:<age>" (a debug build, app/QaHooks): the dev village grown to it, its moment plays
    val qaAgeUp = si.lanisce.lani.app.QaHooks.ageUp
    LaunchedEffect(qaAgeUp, s.age) {
        val to = qaAgeUp ?: return@LaunchedEffect
        val before = Age.entries.getOrNull(to.ordinal - 1) ?: return@LaunchedEffect
        if (s.age != to) return@LaunchedEffect
        si.lanisce.lani.app.QaHooks.tookAgeUp()
        sheet = null; scrollOpen = false; card = null; villager = null; hudState.section = null
        moment = MomentRun(si.lanisce.lani.game.AgeUps.moment(before, to, s), s, replay = false)
    }
    val reduced = rememberReducedMotion()
    val talkBack = si.lanisce.lani.ui.scene.rememberTalkBack()
    moment?.let { rememberMomentClock(it, reduced, talkBack) }
    // the moment's music as its decorations go up, and its fireworks with the cheer
    val playing = moment
    LaunchedEffect(playing) {
        val run = playing ?: return@LaunchedEffect
        val m = run.moment
        vm.ambience.prepare(si.lanisce.lani.game.ambient.Sfx.musicSounds(m.celebration.music))
        snapshotFlow { run.elapsed >= m.musicAt || run.card }.first { it }
        vm.ambience.music { now -> si.lanisce.lani.game.ambient.Sfx.music(m.celebration.music, now, fair = si.lanisce.lani.game.Decor.STALLS in m.celebration.decor) }
        val cheer = m.beats.firstOrNull { it.kind == si.lanisce.lani.game.Beat.Kind.CHEER } ?: return@LaunchedEffect
        snapshotFlow { run.elapsed >= cheer.from || run.card }.first { it }
        if (!reduced) vm.game.cheer()
    }
    val townRenderer = remember { si.lanisce.lani.game.render.VillageRenderer() }
    val scrollPrefs = rememberScrollPrefs()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF16202E))) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val fit = remember(w, h) { SceneFit.town(w, h) }
        val anchors = townAnchors(s, fit)
        val away = remember(s, standing, fit) { TownPeople.awayIds(s, standing, fit.width, fit.height) }
        val margin = with(density) { 12.dp.toPx() }
        fun area(extra: Int = 0) = freeArea(w.toFloat(), h.toFloat(), hudInset().toFloat(), (barH + extra).toFloat(), margin)
        fun anchorOf(p: TownPlace) = anchors[p] ?: anchors.getValue(TownPlace.Fire)

        /** A tap on a place: its card, or (no scenes there) straight to the building's or the fire's sheet, as before. */
        fun tapPlace(tap: PlaceTap?, force: Boolean = false) {
            villager = null
            if (tap == null) { card = null; return }
            val none = vm.scenes.at(tap, s).isEmpty()
            when {
                force -> card = tap
                none && tap is PlaceTap.Built -> { card = null; sheet = VillageSheet.Building(tap.building.id) }
                none && tap == PlaceTap.Fire -> { card = null; sheet = VillageSheet.Fire }
                // nothing to go to at the horizon: a tap on the far hills is a tap on nothing, as it always was
                none && tap == PlaceTap.Spot(TownSpots.HORIZON) -> card = null
                // a landmark that leads nowhere (yet): its project
                none && tap is PlaceTap.Landmark -> { card = null; sheet = VillageSheet.Projects(tap.project) }
                else -> card = tap
            }
        }
        fun placeTap(p: TownPlace): PlaceTap? = when (p) {
            TownPlace.Fire -> PlaceTap.Fire
            TownPlace.Forest -> PlaceTap.Forest
            is TownPlace.Spot -> PlaceTap.Spot(p.id)
            is TownPlace.Landmark -> PlaceTap.Landmark(p.project)
            // a house of its own is that house, else the first of the type
            is TownPlace.At -> (p.id?.let { id -> s.buildings.firstOrNull { it.id == id } } ?: s.buildings.filter { it.type == p.type }.minByOrNull { it.plot })
                ?.let { PlaceTap.Built(it, TownPlace.of(it, s)) }
        }
        /** The villager a bubble belongs to, when they're out on the map (its bubble follows them; a tap opens them). */
        fun villagerOf(m: TownMarker): String? = markerVillager(m, s, active, people, standing, away, keepers)
        val act: (TownMarker) -> Unit = { m ->
            when {
                m.id.startsWith("more:") || m.id.startsWith("cluster:") -> tapPlace(placeTap(m.place), force = true)
                // someone who joined the village: their introduction
                m.id.startsWith("arrival:") -> { card = null; villager = null; vm.villagers.meet(m.id.removePrefix("arrival:")) }
                // someone at a spot now (the burner by his pile): his talk
                m.id.startsWith(Keepers.MARKER) -> { card = null; villager = null; Keepers.of(keepers, m.id)?.let { vm.scenes.startKeeper(it) } }
                m.id.startsWith("project:") -> { card = null; sheet = VillageSheet.Projects(m.id.removePrefix("project:")) }
                // the treasure map by the fire: the map
                m.id == TownMarkers.TREASURE -> { card = null; vm.openTreasure(fromVillage = true) }
                m.id.startsWith("festival:") -> { card = null; vm.startFestival(m.id.removePrefix("festival:")) }
                // an age's party tonight (companion/GAME.md "Ages and their celebrations"): all the village, its dialog
                m.id.startsWith(TownMarkers.PARTY) -> { card = null; vm.startParty(s.age) }
                m.id == "surprise:${si.lanisce.lani.game.Surprises.PEDLAR}" -> { card = null; sheet = VillageSheet.Chest }
                m.id.startsWith("surprise:") -> { card = null; vm.startSurprise() }
                m.id.startsWith("visitor:") -> { card = null; villagerAt = null; villager = m.id.removePrefix("visitor:") }
                m.kind == TownMarker.Kind.QUEST && villagerOf(m) != null -> { card = null; villagerAt = null; villager = villagerOf(m) }
                // someone's requests: their card lists them (they may be away from the clearing); several of someone the
                // cast doesn't know (the tutor's): the scroll's villagers, where the requests are listed; one: straight in
                m.kind == TownMarker.Kind.QUEST && m.id.startsWith("quest:") -> {
                    card = null
                    val q = s.quests.firstOrNull { it.id == m.id.removePrefix("quest:") }
                    val who = q?.let { TownMarkers.villagerOf(it.giver, people) ?: TownMarkers.villagerOf(it.giver, cast) }?.id
                    when {
                        who != null -> { villagerAt = null; villager = who }
                        m.count > 1 -> { villager = null; hudState.section = null; scrollAt = Collection.PEOPLE; scrollOpen = true }
                        else -> vm.startQuest(m.id.removePrefix("quest:"))
                    }
                }
                m.kind == TownMarker.Kind.QUEST -> { card = null; vm.startQuest(m.id.removePrefix("quest:")) }
                m.kind == TownMarker.Kind.EVENT -> { card = null; vm.startEvent() }
                else -> m.id.removePrefix("happening:").let { key -> card = null; vm.openScene(key.substringBefore('/'), key) }
            }
        }

        // The scroll (ui/game/VillageScroll.kt): the village's collections and its history, and what's new in it
        val scrollData = remember(s, overview, scrollPrefs.seen, scrollPrefs.since, cast) {
            ScrollData.of(
                s, overview, vm.villagers.people(s).filter { vm.villagers.livesHere(s, it.id) }, vm.grammar.chapter(s),
                meter = { id -> vm.grammar.view(id)?.meter }, seen = scrollPrefs.seen, since = scrollPrefs.since,
            )
        }
        val scrollRows = ScrollRowActions(
            place = { p -> scrollOpen = false; hudState.section = null; cam.show(anchorOf(p), area()); tapPlace(placeTap(p), force = true) },
            scene = { sc -> scrollOpen = false; hudState.section = null; vm.openScene(sc.id, null) },
            person = { id -> scrollOpen = false; hudState.section = null; villagerAt = null; villager = id },
            register = { scrollOpen = false; hudState.section = null; vm.openVillagers() },
            rule = { id -> vm.grammar.show(id) },
            friends = { scrollOpen = false; hudState.section = null; vm.openFriends() },
            reading = { scrollOpen = false; hudState.section = null; vm.openReadings(fromVillage = true) },
            school = if (vm.school.course != null) { { scrollOpen = false; hudState.section = null; vm.openSchool(fromVillage = true) } } else null,
            quest = { q -> scrollOpen = false; vm.startQuest(q.id) },
            replay = replay,
        )

        // The plot chooser: the village as it would be, the plots to choose from marked, the bubbles away meanwhile
        val choice = choosing?.choice
        val drawn = remember(s, choice) { choice?.preview(s) ?: s }
        val plotMarks = remember(s, choice) { choice?.marks(s) }
        // it frames the plots to choose from (closer where they're few), between the HUD (its cards hidden by themselves) and
        // the chooser's bar, once both are there
        LaunchedEffect(choice?.let { it.moving ?: it.type.name }) {
            val c = choice ?: return@LaunchedEffect
            val (bar, top) = snapshotFlow { plotBarH to hudInset() }.first { (b, t) -> b > 0 && t > 0 }
            val rig = cam.rig ?: return@LaunchedEffect
            val plots = c.marks(s).all + listOfNotNull(c.building(s)?.plot)
            val rect = TownAnchors.plotsRect(s, fit.width, fit.height, plots, pad = 26f) ?: return@LaunchedEffect
            cam.flyTo(rig.frame(rect, freeArea(w.toFloat(), h.toFloat(), top.toFloat(), bar.toFloat(), margin), maxFrame = rig.minScale * 2))
        }

        // The village layer; hidden from TalkBack while the scroll lies over it. While the HUD's sheet is out, a tap outside
        // it (and the bar and the cards) closes it and does nothing else, a drag closes it and pans the map.
        val hudOpen = latest(hudState.section != null)
        /** What of the HUD a tap may land on without closing its sheet ("top": the bar and the cards, "sheet"), by name. */
        val hudRects = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
        Box(
            Modifier.fillMaxSize()
                .outsideCloses(
                    active = hudOpen,
                    inside = { p -> hudRects.values.any { it.contains(p) } },
                    onOutside = { hudState.section = null },
                )
                .then(if (scrollOpen) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            TownView(
                state = moment?.drawn ?: drawn, cam = cam, fit = fit, anchors = anchors, visitors = visitors, clock = townClock,
                // QA's "bubbles:<n>" (a debug build's hook): only the n nearest the fire
                markers = if (choice != null || moment != null) emptyList() else si.lanisce.lani.app.QaHooks.fewer(shown) { m ->
                    val p = anchorOf(m.place)
                    val f = anchorOf(TownPlace.Fire)
                    kotlin.math.hypot(p.x - f.x, p.y - f.y)
                },
                topInset = hudInset(), bottomInset = barH,
                // the recenter button hangs under the HUD while the camera is away: the bubbles keep below it too
                bubbleInset = hudInset() + if (cam.moved) with(density) { 56.dp.roundToPx() } else 0,
                threat = s.event?.kind?.threat() == true,
                badge = { m -> countBadge(m, markers) ?: badges[m.id] },
                onPlace = { tapPlace(it) },
                onMarker = act,
                justBuilt = vm.game.justBuilt,
                celebrate = vm.game.celebrate,
                highlightPlot = if (sheet == VillageSheet.Build) buildPlot else null,
                people = people,
                onVillager = { id, at ->
                    card = null
                    val keeper = Keepers.of(keepers, id)
                    when {
                        // the strangers at the road: the pedlar opens his stall in the chest, the pilgrim asks the way
                        id == si.lanisce.lani.game.Surprises.PEDLAR -> { villager = null; sheet = VillageSheet.Chest }
                        id == si.lanisce.lani.game.Surprises.PILGRIM -> { villager = null; vm.startSurprise() }
                        // the burner by his pile: his talk (he has no card: he doesn't live here)
                        keeper != null -> { villager = null; vm.scenes.startKeeper(keeper) }
                        else -> { villagerAt = at; villager = id }
                    }
                },
                followId = ::villagerOf,
                // a finished landmark that leads into a scene (the vineyard) opens its place card, the others their project
                onProject = { id -> villager = null; tapPlace(PlaceTap.Landmark(id)) },
                horizon = remember(s, scenes) { vm.scenes.at("spot:${TownSpots.HORIZON}", s).isNotEmpty() },
                standing = standing,
                day = routineDay,
                at = si.lanisce.lani.app.QaHooks::mapNow,
                face = { m -> markerFace(m, active, people, away) },
                // a wild animal at the forest's edge: its word's card (🔊, "Add to my words"), looked up without a line
                onAnimal = { word -> card = null; villager = null; vm.words.open(si.lanisce.lani.app.WordQuery(word, "", null, si.lanisce.lani.data.Words.SCENE)) },
                // the night sky: the moon's card, a constellation's, a planet's, a shooting star's wish
                onSky = { tap -> card = null; villager = null; skyCard = tap },
                skyMark = skyCard?.mark,
                onSkyList = { up -> card = null; villager = null; skyList = up },
                plots = plotMarks,
                onPlot = { p ->
                    choosing?.let { c ->
                        val picked = c.choice.pick(s, p)
                        if (picked != c.choice) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); choosing = c.copy(choice = picked) }
                    }
                },
                plotLabel = { p -> choice?.let { plotLabel(s, it, p) } ?: plotName(p) },
                // an age's moment (the village growing, its decorations going up), and the decorations of the days after
                growth = { moment?.frame?.growth },
                festive = { moment?.frame?.festive ?: mapFestive },
                renderer = townRenderer,
            )
            // A new building goes up where the learner can see it.
            LaunchedEffect(vm.game.justBuilt) {
                val b = vm.game.justBuilt?.let { id -> s.buildings.firstOrNull { it.id == id } } ?: return@LaunchedEffect
                cam.reveal(TownAnchors.top(b, s, fit.width, fit.height), area())
            }
            // QA's "show:<spot>": the camera at that spot of the landscape, as a place of the scroll flies it there; "fire",
            // the merchant's "cart", the wolves or the bear ("threat") too
            val qaShow = si.lanisce.lani.app.QaHooks.show
            LaunchedEffect(qaShow) {
                qaShow?.let { (spot, _) ->
                    val comp = si.lanisce.lani.game.render.VillageLayout.composition(fit.width, fit.height, compact = false)
                    fun at(w: FloatArray) = CanvasPoint(comp.fireX + (w[0] - w[1]) * 4f, comp.fireY + (w[0] + w[1]) * 2f - 6f)
                    val p = when (spot) {
                        "fire" -> anchorOf(TownPlace.Fire)
                        "cart" -> at(si.lanisce.lani.game.render.Threats.cart(s, fit.width))
                        "threat" -> si.lanisce.lani.game.render.Threats.of(s, fit.width, fit.height)?.let { at(floatArrayOf(it.cx, it.cy)) } ?: anchorOf(TownPlace.Fire)
                        else -> anchorOf(TownPlace.Spot(spot))
                    }
                    cam.show(p, area())
                }
            }
            // The place card doesn't cover its place: the anchor (the place's top) stays well above the card.
            LaunchedEffect(card, cardH) {
                val c = card ?: return@LaunchedEffect
                if (cardH > 0) cam.reveal(anchorOf(c.place), area(cardH + with(density) { 64.dp.roundToPx() }))
            }
            // Nor does a villager's card cover them.
            LaunchedEffect(villager, cardH) {
                val at = villagerAt ?: return@LaunchedEffect
                if (villager != null && cardH > 0) cam.reveal(at, area(cardH + with(density) { 64.dp.roundToPx() }))
            }

            // the moment's camera: each beat where it happens (home for its opening and its cheer); still with the animations off
            val beatNow by remember(moment) { androidx.compose.runtime.derivedStateOf { moment?.let { if (it.card) null else it.frame.beat } } }
            LaunchedEffect(moment, beatNow) {
                val run = moment ?: return@LaunchedEffect
                val rig = cam.rig ?: return@LaunchedEffect
                if (reduced) return@LaunchedEffect
                val b = beatNow ?: run { cam.recenter(); return@LaunchedEffect }
                val shot = si.lanisce.lani.game.render.MomentShots.of(b, run.moment, run.drawn, fit.width, fit.height)
                val ms = ((b.to - b.from) * 1000 * 0.55).toInt().coerceIn(500, 1400)
                val a = area()
                val rect = shot.rect
                val at = shot.at
                when {
                    shot.home -> cam.home?.let { cam.flyTo(it, ms) }
                    rect != null -> cam.flyTo(rig.frame(rect, a, maxFrame = rig.minScale * 2), ms)
                    at != null -> cam.flyTo(rig.lookAt(at.x, at.y, (rig.minScale * shot.zoom).coerceIn(rig.minScale.toFloat(), rig.maxScale.toFloat()), (a[0] + a[2]) / 2f, (a[1] + a[3]) / 2f), ms)
                }
            }

            if (moment == null) {
            // Keeps the HUD readable over a bright sky, and the bar over the forest.
            Box(Modifier.fillMaxWidth().height(with(density) { hudInset().toDp() } + 40.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))

            // the HUD: what it shows (today in the village, what's coming: always something to come back for) and does;
            // whatever leads on from its sheet closes it
            val v = overview.village
            val today = java.time.LocalDate.now()
            val soon = remember(v, overview.today, partyNow, partyTonight) {
                VillageHudLogic.soon(v, overview.today.filterIsInstance<TodayItem.Arrival>(), today, party = partyNow?.let { it to false } ?: partyTonight?.let { it to true })
            }
            val hud = HudView(
                s, attrs, check, goal,
                goalAction = goal.action(
                    onGather = { r -> vm.startGather(r) }, onAdvance = advance, onBuild = { build(it, false) },
                    onPacks = { vm.openPacks(fromVillage = true) }, onVillagers = vm::openVillagers, onPolish = { vm.startPolish() },
                ),
                chestNew = vm.game.chestNews.shows(s.chest),
                festival = v.festival?.takeIf { !it.done },
                soon = soon,
                soonSeen = hudPrefs.soonSeen,
                stepsSeen = hudPrefs.stepsSeen,
            )
            val section = hudState.section
            // a section seen: its news no longer dotted (today's lines and tonight's story; the goal's steps)
            LaunchedEffect(section, soon) { if (section == HudSection.SOON) hudPrefs.sawSoon(soon.filter { it.news }.map { it.key }) }
            LaunchedEffect(section, goal, check) { if (section == HudSection.GOAL) hudPrefs.sawSteps(VillageHudLogic.stepKeys(goal, check)) }
            // an open sheet closes first on back
            BackHandler(enabled = section != null) { hudState.section = null }
            VillageHud(
                hud, cards = hudCards(), section = section,
                actions = HudActions(
                    back = vm::home,
                    resource = { r -> sheet = VillageSheet.Resource(r) },
                    age = { sheet = VillageSheet.Ages },
                    info = { sheet = VillageSheet.Info },
                    fire = { sheet = VillageSheet.Fire },
                    chest = { sheet = VillageSheet.Chest },
                    projects = { id -> sheet = VillageSheet.Projects(id) },
                    progress = { vm.openProgress(fromVillage = true) },
                    goal = { sheet = VillageSheet.Ages },
                    festival = { id -> vm.startFestival(id) },
                    arrival = { id -> vm.villagers.meet(id) },
                    surprise = {
                        if (si.lanisce.lani.game.Surprises.today(s, today)?.kind == si.lanisce.lani.game.Surprises.PEDLAR) sheet = VillageSheet.Chest
                        else vm.startSurprise()
                    },
                    step = { st ->
                        hudPrefs.sawSteps(VillageHudLogic.stepKeys(goal, check))
                        when (stepAction(st)) {
                            StepAction.GATHER -> st.res?.let { vm.startGather(it) }
                            StepAction.BUILD -> sheet = VillageSheet.Build
                            StepAction.LEARN -> vm.openPacks(fromVillage = true)
                            StepAction.POLISH -> vm.startPolish()
                            StepAction.HELP -> vm.openVillagers()
                            null -> {}
                        }
                    },
                    // the ring tapped while choosing a plot: the cards for now; the learner's choice stays
                    cards = { hudState.toggleCards(chooserRun.takeIf { choosing != null }) },
                    sheet = { sec -> hudState.section = sec },
                    // an age's party: now, its dialog; later tonight, its time (the line says when)
                    party = { a -> if (partyNow != null) { hudState.section = null; vm.startParty(a) } },
                ),
                moved = cam.moved, onRecenter = cam::recenter, reduced = rememberReducedMotion(),
                // its sheet lies over the bottom bar
                modifier = Modifier.zIndex(1f),
                onTop = { bar, row -> hudBarH = bar; hudRowH = row },
                onInside = { name, r -> if (r == null) hudRects.remove(name) else hudRects[name] = r },
            )
            ChronicleToast(
                s.log.lastOrNull(),
                Modifier.align(Alignment.TopCenter).offset { IntOffset(0, hudInset() + (if (cam.moved) 56.dp else 8.dp).roundToPx()) },
            )

            val plotting = choosing
            if (plotting != null) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                // back: nothing is spent, the village as it was, and the sheet it came from again
                BackHandler { choosing = null; sheet = plotting.cancel }
                PlotBar(
                    s, plotting.choice,
                    onBack = { choosing = null; sheet = plotting.cancel },
                    onSuggested = { place(plotting.choice.suggested(s)) },
                    onHere = { place(plotting.choice.chosen) },
                    modifier = Modifier.onSizeChanged { plotBarH = it.height },
                )
            } else Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                AnimatedVisibility(villager != null, enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
                    villager?.let { id ->
                        VillagerCard(vm, id, onDismiss = { villager = null }, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).onSizeChanged { cardH = it.height })
                    }
                }
                AnimatedVisibility(card != null, enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
                    val tap = card
                    if (tap != null) {
                        BackHandler { card = null }
                        val story = spotStory(vm, tap)
                        PlaceCard(
                            tap = tap, state = s,
                            here = markers.filter { it.place == tap.place }.sortedBy { BUBBLE_ORDER.indexOf(it.kind) },
                            scenes = vm.scenes.at(tap, s),
                            onMarker = act,
                            // a building's room is seen in the one tapped, at its level
                            onScene = { sc -> card = null; vm.openScene(sc.id, building = (tap as? PlaceTap.Built)?.building?.id) },
                            onBuilding = { (tap as? PlaceTap.Built)?.let { sheet = VillageSheet.Building(it.building.id) } },
                            onFire = { sheet = VillageSheet.Fire },
                            onGather = { r -> card = null; vm.startGather(r) },
                            onClose = { card = null },
                            onProject = { (tap as? PlaceTap.Landmark)?.let { card = null; sheet = VillageSheet.Projects(it.project) } },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).onSizeChanged { cardH = it.height },
                            // a spot its culture tells about (the charcoal pile): its history and its words
                            story = story,
                            onWord = { w, line, meant -> vm.words.open(si.lanisce.lani.app.WordQuery(w, line, meant.takeIf { it.isNotBlank() }, si.lanisce.lani.data.Words.SCENE)) },
                            onLearn = story?.pack?.let { p -> { card = null; vm.startPack(p, fromVillage = true) } },
                            speaker = vm.speaker,
                        )
                    }
                }
                TownBar(
                    // only what's new and worth opening: who joined, the grammar pages met this week
                    new = scrollData.badge,
                    event = s.event,
                    canBuild = options.any { it.available },
                    onScroll = { card = null; villager = null; hudState.section = null; scrollAt = null; scrollOpen = true },
                    onBuild = { card = null; sheet = VillageSheet.Build },
                    onEvent = vm::startEvent,
                    modifier = Modifier.onSizeChanged { barH = it.height },
                )
            }
            }
        }
        // an age's moment over the map: its captions, the sign's name, then its card with what's new
        moment?.let { run ->
            val to = run.moment.to
            val whatsNew = remember(run, vm.scenes.all, cast) { si.lanisce.lani.game.AgeUps.whatsNew(to, vm.scenes.all, cast) }
            // played again from the chronicle: its party now, whatever the hour; else tonight's
            val party = if (run.replay) si.lanisce.lani.game.AgeUps.celebration(to).takeIf { it.party } else partyNow
            AgeUpOverlay(
                run, sign = { townRenderer.signAnchor }, camera = { cam.camera },
                actions = MomentActions(
                    done = { moment = null; cam.recenter() },
                    party = { moment = null; cam.recenter(); vm.startParty(to) },
                    open = { n ->
                        moment = null
                        cam.recenter()
                        when (n.go) {
                            si.lanisce.lani.game.NewThing.Go.BUILD -> sheet = VillageSheet.Build
                            si.lanisce.lani.game.NewThing.Go.PROJECTS -> sheet = VillageSheet.Projects(n.arg)
                            si.lanisce.lani.game.NewThing.Go.SCENE -> n.arg?.let { vm.openScene(it, null) }
                            si.lanisce.lani.game.NewThing.Go.PERSON -> vm.openVillagers()
                            si.lanisce.lani.game.NewThing.Go.CHEST -> sheet = VillageSheet.Chest
                            si.lanisce.lani.game.NewThing.Go.SCHOOL -> vm.openSchool(fromVillage = true)
                            si.lanisce.lani.game.NewThing.Go.INFO -> sheet = VillageSheet.Info
                            si.lanisce.lani.game.NewThing.Go.FESTIVALS -> hudState.section = HudSection.SOON
                            si.lanisce.lani.game.NewThing.Go.NONE -> Unit
                        }
                    },
                ),
                party = party,
                tonight = if (run.replay) null else partyTonight,
                whatsNew = whatsNew,
            )
        }

        if (scrollOpen) VillageScroll(scrollData, scrollRows, scrollPrefs, start = scrollAt, onClose = { scrollOpen = false })
    }

    // the story notebook, in the chest where the village has a storyteller
    val notebook = si.lanisce.lani.ui.notebook.rememberNotebook(vm)
    val tells = remember(vm.scenes.all) { si.lanisce.lani.game.scene.StoryBooks.stories(vm.scenes.all).isNotEmpty() }
    when (val sh = sheet) {
        null -> Unit
        VillageSheet.Build -> BuildSheet(
            s, options, check, onBuild = build, onAges = { sheet = VillageSheet.Ages }, onDismiss = { sheet = null },
            // make room for whom a workplace brings: gather for the dwelling, or build or upgrade it (the sheet stays open,
            // the workplace can go up next; a dwelling to build goes to the plot chooser, and back to the sheet)
            onRoom = { w ->
                val short = w.short
                when {
                    // the dwelling's upgrade asks for its words first: their run, then its card
                    w.upgrade != null && w.words != null -> { sheet = null; vm.practiseUpgrade(w.upgrade) }
                    short != null -> { sheet = null; vm.startGather(short) }
                    w.upgrade != null -> if (vm.game.upgrade(w.upgrade, false)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    else -> choose(Choosing(PlotChoice.build(s, w.type), cancel = VillageSheet.Build, done = VillageSheet.Build))
                }
            },
            course = vm.school.course,
        )
        VillageSheet.Info -> InfoSheet(s, attrs, onDismiss = { sheet = null })
        VillageSheet.Fire -> HearthsSheet(s, java.time.LocalDate.now(), onGather = { sheet = null; card = null; vm.startGather(Res.WOOD) }, onDismiss = { sheet = null })
        VillageSheet.Ages -> AgeSheet(
            s, check, onAdvance = advance,
            onGather = { r -> sheet = null; scrollOpen = false; vm.startGather(r) },
            onBuild = { sheet = VillageSheet.Build },
            onLearn = { sheet = null; vm.openPacks(fromVillage = true) },
            onPolish = { sheet = null; vm.startPolish() },
            onFriends = { sheet = null; vm.openVillagers() },
            onDismiss = { sheet = null },
        )
        VillageSheet.Chest -> {
            // what's new is marked in it this time; opened, it's seen (and so is what's bought while it's open)
            val fresh = remember { vm.game.chestNews.unseen }
            DisposableEffect(Unit) {
                vm.game.sawChest()
                onDispose { vm.game.sawChest() }
            }
            ChestSheet(
                s, people.filter { vm.villagers.livesHere(s, it.id) },
                ChestActions(
                    forge = { g -> if (vm.game.forge(g)) haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    give = { good, id -> vm.villagers.offer(good, id) }, // the gift moment (GiftScene) over the chest
                    sell = { good -> vm.game.sell(good) },
                    feast = { if (vm.game.feast()) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); sheet = null } },
                    buy = { seller, good -> if (vm.game.buy(seller, good)) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    read = { r -> sheet = VillageSheet.Reading(r) },
                    readings = { sheet = null; vm.openReadings(fromVillage = true) },
                    // over the chest: closed, the chest is there again
                    openNotebook = { vm.openNotebook() },
                    openTreasure = { sheet = null; vm.openTreasure(fromVillage = true) },
                ),
                onDismiss = { sheet = null },
                fresh = fresh,
                notebook = notebook.takeIf { tells },
            )
        }
        is VillageSheet.Reading -> ReadingSheet(
            vm, sh.readable, reader = sh.readable.by?.let { vm.villagers.byId(it) },
            read = si.lanisce.lani.game.Readings.done(s, sh.readable.reading.id),
            onAnswered = { verdicts, minutes, looks, cost -> vm.readings.answered(sh.readable.reading, verdicts, minutes, looks, cost) },
            onDismiss = { sheet = VillageSheet.Chest },
            onReadAloud = { sheet = null; vm.openReadAloud(sh.readable.reading.id, fromVillage = true, fromCorner = false) },
        )
        is VillageSheet.Resource -> ResourceSheet(
            s, attrs, sh.res, onGather = { sheet = null; vm.startGather(sh.res) }, onDismiss = { sheet = null },
            onTrade = { sheet = VillageSheet.Trade(sh.res) },
        )
        is VillageSheet.Trade -> TradeSheet(
            s, sh.from, canSpeak = vm.speaker.canSay,
            onStart = { give, get, amount -> sheet = null; vm.startTrade(give, get, amount) },
            onDismiss = { sheet = VillageSheet.Resource(sh.from) },
        )
        is VillageSheet.Projects -> {
            // the projects' words: their packs as the node has them (loaded for the sheet: which are learned), else as the
            // app bundles them; and where today's step is played, when the village has its scene open
            val packIds = remember(s.age) { si.lanisce.lani.game.Projects.open(s).mapNotNull { it.pack } }
            LaunchedEffect(packIds) { for (id in packIds) runCatching { vm.scenes.loadPack(id) } }
            ProjectsSheet(
                // a debug build's QA readied a step: the sheet opens at its project
                s, vm.villagers.people(s), sh.focus ?: si.lanisce.lani.app.QaHooks.project,
                onStep = { id -> sheet = null; scrollOpen = false; vm.startProject(id) },
                onDismiss = { sheet = null },
                onMeet = { id -> sheet = null; scrollOpen = false; vm.villagers.meet(id) },
                words = { spec -> vm.projectWords(spec) },
                onWord = { w, line, meant -> vm.words.open(si.lanisce.lani.app.WordQuery(w, line, meant.takeIf { it.isNotBlank() }, si.lanisce.lani.data.Words.SCENE)) },
                onLearn = { pack -> sheet = null; scrollOpen = false; vm.startPack(pack, fromVillage = true) },
            )
        }
        is VillageSheet.Building -> s.buildings.firstOrNull { it.id == sh.id }?.let { b ->
            // "↔ Premakni · Move": to the plot chooser, and back to this card when cancelled
            val move = PlotChoice.move(s, b).takeIf { it.building(s) != null && it.open(s) }
            BuildingCard(
                vm, s, b, options, check,
                onUpgraded = { sheet = null; card = null },
                onPractise = { sheet = null; card = null },
                onAges = { sheet = VillageSheet.Ages },
                onDismiss = { sheet = null },
                onMove = move?.let { m -> { choose(Choosing(m, cancel = sh)) } },
            )
        }
    }
    skyList?.let { up -> si.lanisce.lani.ui.sky.SkyTonightSheet(vm, up, onOpen = { skyList = null; skyCard = it }, onDismiss = { skyList = null }) }
    skyCard?.let { t -> si.lanisce.lani.ui.sky.SkySheet(vm, t, onDismiss = { skyCard = null }) }
}

@Composable
private fun VillageLoading(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SloBlueDeep, Color(0xFF1A2438))))) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = FlameOrange)
            Spacer(Modifier.height(16.dp))
            Text(bi("villageScreen.lightingFire"), color = Color.White)
        }
        HudButton("←", Labels.BACK, onBack, Modifier.statusBarsPadding().padding(12.dp))
    }
}

/** Back to the home view: a crosshair over the village (and over a zoomed scene). */
@Composable
internal fun RecenterButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = HudPlate, shape = CircleShape,
        modifier = Modifier.size(48.dp).clearAndSetSemantics { contentDescription = bi("common.recenter"); onClick { onClick(); true } },
    ) {
        Canvas(Modifier.padding(13.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val stroke = 2.dp.toPx()
            drawCircle(Color.White, radius = size.minDimension * 0.34f, center = c, style = Stroke(stroke))
            val r0 = size.minDimension * 0.18f; val r1 = size.minDimension * 0.5f
            for ((dx, dy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
                drawLine(Color.White, Offset(c.x + dx * r0, c.y + dy * r0), Offset(c.x + dx * r1, c.y + dy * r1), stroke, StrokeCap.Round)
            }
            drawCircle(XpGold, radius = stroke, center = c)
        }
    }
}

/** The slim bar at the thumb: the scroll (its count: what's new in it), Build, and the event when there is one. */
@Composable
private fun TownBar(
    new: Int, event: GameEvent?, canBuild: Boolean,
    onScroll: () -> Unit, onBuild: () -> Unit, onEvent: () -> Unit, modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BarButton(
            "📜", bi("villageScreen.scroll"),
            color = Color(0xFFF1DDB0), ink = Color(0xFF3A2A16), badge = new.takeIf { it > 0 }, onClick = onScroll, modifier = Modifier.weight(1f),
        )
        BarButton(
            if (canBuild) "🔨" else "🔒", bi("common.build"),
            color = FlameOrange, ink = Color.White, onClick = onBuild, modifier = Modifier.weight(1f),
        )
        if (event != null) {
            val threat = event.kind.threat()
            val wobble = rememberInfiniteTransition(label = "event").animateFloat(-6f, 6f, infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "r").value
            BarButton(
                event.kind.emoji, event.kind.action().substringAfter(' '),
                color = if (threat) TriglavRed else XpGold, ink = if (threat) Color.White else Color(0xFF2B2118),
                onClick = onEvent, modifier = Modifier.weight(1f), tilt = if (threat) wobble else 0f,
            )
        }
    }
}

/** A big button of the bar at the thumb: [emoji] over its [label]; greyed and not clickable unless [enabled]. */
@Composable
internal fun BarButton(
    emoji: String, label: String, color: Color, ink: Color, onClick: () -> Unit, modifier: Modifier = Modifier,
    badge: Int? = null, tilt: Float = 0f, enabled: Boolean = true,
) {
    Box(modifier.alpha(if (enabled) 1f else 0.45f)) {
        Surface(onClick = onClick, enabled = enabled, color = color, shape = RoundedCornerShape(18.dp), shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
            Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.rotate(tilt).clearAndSetSemantics { })
                Text(label, color = ink, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (badge != null) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp).size(24.dp).clip(CircleShape).background(TriglavRed)
                    .clearAndSetSemantics { contentDescription = bi("villageScreen.newInScroll", "n" to badge) },
                contentAlignment = Alignment.Center,
            ) { Text("$badge", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** Minute-resolution clock for countdowns. */
@Composable
fun rememberNow(periodMs: Long = 30_000): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(periodMs)
            value = System.currentTimeMillis()
        }
    }
    return now
}

fun EventKind.action(): String = when (this) {
    EventKind.WOLVES, EventKind.BEAR -> "⚔️ ${bi("villageScreen.defend")}"
    EventKind.STORM -> "🛖 ${bi("villageScreen.shelter")}"
    EventKind.MERCHANT -> "🤝 ${bi("villageScreen.trade")}"
    EventKind.FESTIVAL -> "🎶 ${bi("common.celebrate")}"
}

fun EventKind.headline(): String = when (this) {
    EventKind.WOLVES -> bi("villageScreen.wolvesAttack")
    EventKind.BEAR -> bi("villageScreen.bearVillage")
    EventKind.STORM -> bi("villageScreen.stormComing")
    EventKind.MERCHANT -> bi("villageScreen.merchantArrived")
    EventKind.FESTIVAL -> bi("villageScreen.festivalTime")
}

/** Threats in red, visitors in warm gold. */
fun EventKind.threat() = this == EventKind.WOLVES || this == EventKind.BEAR || this == EventKind.STORM

/** The goal's one-tap action: its card's first button, the HUD panel's ([label], its [color], what it does: [run]). */
data class GoalAction(val label: String, val color: Color, val run: () -> Unit)

/** What [NextGoal]'s one action says and does (the HUD's goal card and sheet). */
fun NextGoal.action(
    onGather: (Res) -> Unit, onAdvance: () -> Unit, onBuild: (BuildOption) -> Unit, onPacks: () -> Unit, onVillagers: () -> Unit,
    onPolish: () -> Unit,
): GoalAction = when (this) {
    NextGoal.FeedFire -> GoalAction("🎧 ${bi("common.gatherWood")}", FlameOrange) { onGather(Res.WOOD) }
    is NextGoal.Advance -> GoalAction("✨ ${bi("villageScreen.advanceTo", "toSl" to to.names)}", XpGold, onAdvance)
    is NextGoal.Build -> GoalAction("🔨 ${bi("villageScreen.buildNow")}", FlameOrange) { onBuild(option) }
    is NextGoal.Grow -> GoalAction(bi("villageScreen.gatherFor", "gatherEmoji" to gather.emoji, "lowercase" to gather.names.map { it.lowercase() }), AlpineGreen) { onGather(gather) }
    is NextGoal.Learn -> GoalAction("📚 ${bi("villageScreen.learnNewWords")}", AlpineGreen, onPacks)
    is NextGoal.Friends -> GoalAction("👥 ${bi("common.villagers")}", AlpineGreen, onVillagers)
    is NextGoal.Polish -> GoalAction("🔩 ${bi("reviewScreen.polishNow", "n" to rusty)}", AlpineGreen, onPolish)
    is NextGoal.Gather -> GoalAction(bi("villageScreen.gather", "resEmoji" to res.emoji), AlpineGreen) { onGather(res) }
}

/** The newest chronicle line, sliding in briefly after something happened. */
@Composable
private fun ChronicleToast(last: LogEntry?, modifier: Modifier) {
    var seen by remember { mutableStateOf(last) }
    var shown by remember { mutableStateOf<LogEntry?>(null) }
    LaunchedEffect(last) {
        if (last != null && last != seen) {
            shown = last
            delay(3_200)
            shown = null
        }
        seen = last
    }
    AnimatedVisibility(shown != null, modifier, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        val e = shown ?: last ?: return@AnimatedVisibility
        Surface(color = HudPlate, shape = RoundedCornerShape(50), modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("${e.emoji}  ${e.text}", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), maxLines = 2)
        }
    }
}

/** Thin rounded fill bar used by resource and fire sheets. */
@Composable
fun FillBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
        color = color,
        strokeCap = StrokeCap.Round,
    )
}

/**
 * What the card of spot [tap] tells beyond its name when its culture tells it (world.json `spots`: the charcoal pile): its
 * history at the learner's level in the village's language, and its words, from its pack as the node has it (loaded for the
 * card; the cached one offline). Null for any other place.
 */
@Composable
private fun spotStory(vm: AppViewModel, tap: PlaceTap): SpotStory? {
    val id = (tap as? PlaceTap.Spot)?.id ?: return null
    val culture = si.lanisce.lani.game.culture.Cultures.current
    val texts = culture.world.spots[id] ?: return null
    LaunchedEffect(texts.pack) { texts.pack?.let { vm.scenes.loadPack(it) } }
    val pack = texts.pack?.let { vm.scenes.packs[it] }
    val level = vm.levelIn(culture.manifest.language)
    val lore = remember(texts, level) { Keepers.lore(texts, level, culture.language) }
    val words = pack?.words.orEmpty().filter { texts.words.isEmpty() || it.id in texts.words }
    return SpotStory(lore, words, pack?.learned.orEmpty().toSet(), texts.pack)
}
