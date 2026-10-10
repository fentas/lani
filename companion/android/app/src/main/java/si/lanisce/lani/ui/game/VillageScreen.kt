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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import si.lanisce.lani.game.Attributes
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
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.NewWordsCard
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.villagers.VillagerCard
import si.lanisce.lani.l10n.bi
import java.time.LocalDateTime
import androidx.compose.foundation.layout.fillMaxHeight
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
    data class Building(val id: String) : VillageSheet
}

/**
 * "Moja vas" as a place to explore: the village fills the screen (pinch, pan, double tap), bubbles show what's
 * going on where, places open cards and scenes, and the scroll (the book from Vas on) holds the overview.
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
        ),
        si.lanisce.lani.game.ambient.Soundscape.villageSky(s, java.time.LocalDate.now()),
        townClock,
    )
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val attrs = remember(s) { GameEngine.attributes(s) }
    // with the cast: a workplace whose person has nowhere to live waits, and says how to make room (a dwelling's upgrade
    // with the words it asks for)
    val options = remember(s, vm.villagers.all, vm.content.dashboard, vm.scenes.packs, vm.scenes.all) {
        GameEngine.buildOptions(s, vm.villagers.all) { id -> vm.upgradeWords(s, id) }
    }
    val check = remember(s, vm.content.wordsKnown, vm.content.targetLevel, vm.content.wordsRusty) {
        GameEngine.advanceCheck(s, vm.content.wordsKnown, vm.content.targetLevel, vm.content.wordsRusty)
    }
    val goal = remember(s, options, check, attrs) { nextGoal(s, options, check, attrs.caps) }
    // Where the Build sheet's outline goes: the goal's building lands beside its partner (Placement), others take the
    // next free plot.
    val buildPlot = remember(s, goal) {
        (goal as? NextGoal.Build)?.let { si.lanisce.lani.game.Placement.plotFor(s, it.option.type) }
            ?: (0 until PLOTS_PER_AGE[s.age.ordinal]).firstOrNull { p -> s.buildings.none { it.type.onPlot && it.plot == p } }
    }
    // back from a building's words run: its card again, the upgrade one tap away
    var sheet by remember { mutableStateOf<VillageSheet?>(vm.takeBuildingCard()?.let { VillageSheet.Building(it) }) }
    /** The plot chooser over the map: a building to put on a plot, or one to move (see [PlotBar]). */
    var choosing by remember { mutableStateOf<Choosing?>(null) }
    var ceremony by remember { mutableStateOf<Age?>(null) }
    var scrollOpen by remember { mutableStateOf(false) }
    var scrollAt by remember { mutableStateOf(ScrollSection.TODAY) }
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
    val scenes = vm.scenes.all
    val active = remember(s, scenes, minute) { vm.scenes.active(s) }
    // who is at a spot of the landscape now without living here (the charcoal burner by his kopa some evenings)
    val keepers = remember(s, minute) { Keepers.here(s, LocalDateTime.now()) }
    val cast = vm.villagers.all
    val markers = remember(s, active, cast, keepers) { TownMarkers.of(s, active, cast, keepers = keepers) }
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
    var hudH by remember { mutableIntStateOf(0) }
    /** The goal pill's row under the HUD: bubbles and the home view keep out from under it too. */
    var pillH by remember { mutableIntStateOf(0) }
    var barH by remember { mutableIntStateOf(0) }
    var cardH by remember { mutableIntStateOf(0) }
    var plotBarH by remember { mutableIntStateOf(0) }

    /** Opens the plot chooser over the map (the sheets and cards close; [Choosing.cancel] and [Choosing.done] lead back). */
    val choose: (Choosing) -> Unit = { c ->
        if (c.choice.open(s)) {
            sheet = null; card = null; villager = null; scrollOpen = false
            choosing = c
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
        vm.game.advance()?.let {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            sheet = null
            scrollOpen = false
            ceremony = it
        }
    }
    val openScroll: (ScrollSection) -> Unit = { scrollAt = it; card = null; villager = null; scrollOpen = true }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF16202E))) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val fit = remember(w, h) { SceneFit.town(w, h) }
        val anchors = townAnchors(s, fit)
        val away = remember(s, standing, fit) { TownPeople.awayIds(s, standing, fit.width, fit.height) }
        val margin = with(density) { 12.dp.toPx() }
        fun area(extra: Int = 0) = freeArea(w.toFloat(), h.toFloat(), (hudH + pillH).toFloat(), (barH + extra).toFloat(), margin)
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
                m.id == "surprise:${si.lanisce.lani.game.Surprises.PEDLAR}" -> { card = null; sheet = VillageSheet.Chest }
                m.id.startsWith("surprise:") -> { card = null; vm.startSurprise() }
                m.id.startsWith("visitor:") -> { card = null; villagerAt = null; villager = m.id.removePrefix("visitor:") }
                m.kind == TownMarker.Kind.QUEST && villagerOf(m) != null -> { card = null; villagerAt = null; villager = villagerOf(m) }
                // someone's requests: their card lists them (they may be away from the clearing); several of someone the
                // cast doesn't know: the Scroll's requests; one: straight in
                m.kind == TownMarker.Kind.QUEST && m.id.startsWith("quest:") -> {
                    card = null
                    val q = s.quests.firstOrNull { it.id == m.id.removePrefix("quest:") }
                    val who = q?.let { TownMarkers.villagerOf(it.giver, people) ?: TownMarkers.villagerOf(it.giver, cast) }?.id
                    when {
                        who != null -> { villagerAt = null; villager = who }
                        m.count > 1 -> openScroll(ScrollSection.TODAY)
                        else -> vm.startQuest(m.id.removePrefix("quest:"))
                    }
                }
                m.kind == TownMarker.Kind.QUEST -> { card = null; vm.startQuest(m.id.removePrefix("quest:")) }
                m.kind == TownMarker.Kind.EVENT -> { card = null; vm.startEvent() }
                else -> m.id.removePrefix("happening:").let { key -> card = null; vm.openScene(key.substringBefore('/'), key) }
            }
        }

        // The plot chooser: the village as it would be, the plots to choose from marked, the bubbles away meanwhile
        val choice = choosing?.choice
        val drawn = remember(s, choice) { choice?.preview(s) ?: s }
        val plotMarks = remember(s, choice) { choice?.marks(s) }
        // it frames the plots to choose from (closer where they're few), above its bar, once it opens
        LaunchedEffect(choice?.let { it.moving ?: it.type.name }) {
            val c = choice ?: return@LaunchedEffect
            val bar = snapshotFlow { plotBarH }.first { it > 0 }
            val rig = cam.rig ?: return@LaunchedEffect
            val plots = c.marks(s).all + listOfNotNull(c.building(s)?.plot)
            val rect = TownAnchors.plotsRect(s, fit.width, fit.height, plots, pad = 26f) ?: return@LaunchedEffect
            cam.flyTo(rig.frame(rect, freeArea(w.toFloat(), h.toFloat(), (hudH + pillH).toFloat(), bar.toFloat(), margin), maxFrame = rig.minScale * 2))
        }

        // The village layer; hidden from TalkBack while the scroll lies over it.
        Box(Modifier.fillMaxSize().then(if (scrollOpen) Modifier.clearAndSetSemantics { } else Modifier)) {
            TownView(
                state = drawn, cam = cam, fit = fit, anchors = anchors, visitors = visitors, clock = townClock,
                // QA's "bubbles:<n>" (a debug build's hook): only the n nearest the fire
                markers = if (choice != null) emptyList() else si.lanisce.lani.app.QaHooks.fewer(shown) { m ->
                    val p = anchorOf(m.place)
                    val f = anchorOf(TownPlace.Fire)
                    kotlin.math.hypot(p.x - f.x, p.y - f.y)
                },
                topInset = hudH + pillH, bottomInset = barH,
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
            )
            // A new building goes up where the learner can see it.
            LaunchedEffect(vm.game.justBuilt) {
                val b = vm.game.justBuilt?.let { id -> s.buildings.firstOrNull { it.id == id } } ?: return@LaunchedEffect
                cam.reveal(TownAnchors.top(b, s, fit.width, fit.height), area())
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

            // Keeps the HUD readable over a bright sky, and the bar over the forest.
            Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))

            TownHud(
                s, attrs, check,
                chestNew = vm.game.chestNews.shows(s.chest),
                onBack = vm::home,
                onAge = { sheet = VillageSheet.Ages },
                onRes = { sheet = VillageSheet.Resource(it) },
                onInfo = { sheet = VillageSheet.Info },
                onFire = { sheet = VillageSheet.Fire },
                onChest = { sheet = VillageSheet.Chest },
                onProjects = { sheet = VillageSheet.Projects() },
                onProgress = { vm.openProgress(fromVillage = true) },
                modifier = Modifier.onSizeChanged { hudH = it.height },
            )
            Column(
                Modifier.fillMaxWidth().offset { IntOffset(0, hudH) }.padding(horizontal = 10.dp).onSizeChanged { pillH = it.height },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoalPill(goal, Modifier.weight(1f, fill = false)) { openScroll(ScrollSection.GOAL) }
                    Spacer(Modifier.weight(0.01f).widthIn(min = 8.dp))
                    AnimatedVisibility(cam.moved, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        RecenterButton(cam::recenter)
                    }
                }
                // the festival that is on, else what's coming ("Jutri · Tomorrow"): always something to come back for
                val v = overview.village
                val festival = v.festival?.takeIf { !it.done }
                if (festival != null) {
                    TeaserPill(festival.festival.emoji, si.lanisce.lani.game.Calendar.todayText(festival).replace(" ${festival.festival.emoji}", ""), gold = true) {
                        vm.startFestival(festival.festival.id)
                    }
                } else v.teasers.firstOrNull()?.let { t -> TeaserPill(t.emoji, t.text) { openScroll(ScrollSection.TODAY) } }
            }
            ChronicleToast(s.log.lastOrNull(), Modifier.align(Alignment.TopCenter).offset { IntOffset(0, hudH + pillH + with(density) { 8.dp.roundToPx() }) })

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
                    book = s.age >= Age.VAS,
                    today = overview.todayCount,
                    event = s.event,
                    canBuild = options.any { it.available },
                    onScroll = { openScroll(ScrollSection.TODAY) },
                    onBuild = { card = null; sheet = VillageSheet.Build },
                    onEvent = vm::startEvent,
                    modifier = Modifier.onSizeChanged { barH = it.height },
                )
            }
        }

        TownScroll(
            visible = scrollOpen,
            book = s.age >= Age.VAS,
            start = scrollAt,
            overview = overview,
            state = s,
            actions = remember(s, fit, anchors) {
                ScrollActions(
                    onEvent = { scrollOpen = false; vm.startEvent() },
                    onQuest = { q -> scrollOpen = false; vm.startQuest(q.id) },
                    onScene = { sc, key -> scrollOpen = false; vm.openScene(sc.id, key) },
                    onShow = { p -> scrollOpen = false; cam.show(anchorOf(p), area()) },
                    onPlace = { p -> scrollOpen = false; cam.show(anchorOf(p), area()); tapPlace(placeTap(p), force = true) },
                    onVillagers = { scrollOpen = false; vm.openVillagers() },
                    onFriends = { scrollOpen = false; vm.openFriends() },
                    onReading = { scrollOpen = false; vm.openReadings(fromVillage = true) },
                    onFestival = { id -> scrollOpen = false; vm.startFestival(id) },
                    onSurprise = {
                        scrollOpen = false
                        if (si.lanisce.lani.game.Surprises.today(s, java.time.LocalDate.now())?.kind == si.lanisce.lani.game.Surprises.PEDLAR) sheet = VillageSheet.Chest
                        else vm.startSurprise()
                    },
                    onProjects = { id -> sheet = VillageSheet.Projects(id) },
                    onArrival = { id -> scrollOpen = false; vm.villagers.meet(id) },
                    onKeeper = { k -> scrollOpen = false; vm.scenes.startKeeper(k) },
                )
            },
            onClose = { scrollOpen = false },
            // the rules met: the book turns to a page, the scroll shows it over itself
            grammar = GrammarShelf(vm, vm.grammar.chapter(s), vm.grammar::view, open = vm.grammar::show),
        ) {
            GoalSection(
                s, goal, check, vm,
                onBuild = { build(it, false) }, onAdvance = advance,
                onGather = { r -> scrollOpen = false; vm.startGather(r) },
                onBuildSheet = { sheet = VillageSheet.Build },
                onAges = { sheet = VillageSheet.Ages },
            )
        }
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
        )
        VillageSheet.Info -> InfoSheet(s, attrs, onDismiss = { sheet = null })
        VillageSheet.Fire -> FireSheet(s, attrs, onGather = { sheet = null; card = null; vm.startGather(Res.WOOD) }, onDismiss = { sheet = null })
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
        is VillageSheet.Resource -> ResourceSheet(s, attrs, sh.res, onGather = { sheet = null; vm.startGather(sh.res) }, onDismiss = { sheet = null })
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
            // the words its upgrade asks for: their packs on the phone first
            LaunchedEffect(b.id) { vm.loadBuildingWords(b.id) }
            BuildingSheet(s, b, options.firstOrNull { it.type == b.type }, check, onAges = { sheet = VillageSheet.Ages }, onMove = move?.let { m -> { choose(Choosing(m, cancel = sh)) } }, onRepair = { moba ->
                if (vm.game.repair(b.id, moba)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }, onUpgrade = { moba ->
                if (vm.game.upgrade(b.id, moba)) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    sheet = null
                    card = null
                }
            }, onDismiss = { sheet = null }, words = vm.upgradeWords(s, b.id), onPractise = { sheet = null; card = null; vm.practiseUpgrade(b.id) })
        }
    }
    skyList?.let { up -> si.lanisce.lani.ui.sky.SkyTonightSheet(vm, up, onOpen = { skyList = null; skyCard = it }, onDismiss = { skyList = null }) }
    skyCard?.let { t -> si.lanisce.lani.ui.sky.SkySheet(vm, t, onDismiss = { skyCard = null }) }
    ceremony?.let { AgeCeremony(it) { ceremony = null } }
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

/** A round emoji button over the scene: 48 dp to touch, [description] for TalkBack. */
@Composable
private fun HudButton(label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, color = HudPlate, shape = CircleShape, modifier = modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { EmojiLabel(label, description, color = Color.White, style = MaterialTheme.typography.titleMedium) }
    }
}

/**
 * The floating HUD: back and the four resources, then the age, the village's vital signs and the chest (🤝; a dot when
 * something came into it since it was opened, [chestNew]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TownHud(
    s: GameState, attrs: Attributes, check: AdvanceCheck, chestNew: Boolean, onBack: () -> Unit, onAge: () -> Unit, onRes: (Res) -> Unit, onInfo: () -> Unit, onFire: () -> Unit,
    onChest: () -> Unit, onProjects: () -> Unit, onProgress: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HudButton("←", Labels.BACK, onBack)
            for (r in Res.entries) ResourceChip(r, s.res(r), attrs.caps[r], onClick = { onRes(r) }, modifier = Modifier.weight(1f))
        }
        // wraps onto a second line with large fonts instead of squeezing the age's name away
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            AgeChip(s, check, onAge)
            StatChip("👥", "${s.villagers}/${attrs.populationCap}", "${bi("villageScreen.villagers")}: ${s.villagers} / ${attrs.populationCap}", onClick = onInfo)
            StatChip(moraleEmoji(s.morale), "${s.morale}", "${bi("villageScreen.morale")}: ${s.morale}", onClick = onInfo)
            StatChip("🔥", "${s.fire}", "${bi("villageScreen.fire")}: ${s.fire}" + if (s.fire < 25) ", ${bi("villageScreen.dying")}" else "", warn = s.fire < 25, onClick = onFire)
            StatChip(
                "🧰", "🤝 ${s.help}",
                "${bi("villageScreen.chest")}. ${bi("common.help")}: ${s.help}" + if (chestNew) ", ${bi("villageScreen.somethingNew")}" else "",
                dot = chestNew, onClick = onChest,
            )
            if (s.age >= Age.ZASELEK) {
                val open = si.lanisce.lani.game.Projects.open(s)
                val done = open.count { si.lanisce.lani.game.Projects.finished(s, it) }
                StatChip("🏗️", "$done/${open.size}", "${bi("villageScreen.villageProjects")}: ${bi("villageScreen.finished", "done" to done, "total" to open.size)}", onClick = onProjects)
            }
            Surface(onClick = onProgress, color = HudPlate, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { EmojiLabel("📊", Labels.PROGRESS, style = MaterialTheme.typography.titleSmall) }
            }
        }
    }
}

fun moraleEmoji(m: Int) = when {
    m >= 70 -> "😄"
    m >= 40 -> "🙂"
    else -> "😟"
}

/**
 * A HUD stat; TalkBack reads [description] ("Ogenj · Fire: 40") instead of the emoji and the bare number. [dot]: a red
 * dot on it, something new there.
 */
@Composable
private fun StatChip(emoji: String, value: String, description: String, warn: Boolean = false, dot: Boolean = false, onClick: () -> Unit) {
    val pulse = if (warn) {
        rememberInfiniteTransition(label = "warn").animateFloat(0.6f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a").value
    } else 1f
    Box {
        Surface(
            onClick = onClick,
            color = if (warn) TriglavRed.copy(alpha = 0.8f * pulse) else HudPlate,
            shape = RoundedCornerShape(50),
            modifier = Modifier.heightIn(min = 40.dp).hudSemantics(description, onClick),
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Text(value, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (dot) NewDot(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}

/**
 * The age in the HUD with the way to the next one: a small bar filling up and the next age's emoji; it glows
 * gold when the village can advance. Opens the ages sheet with every step.
 */
@Composable
private fun AgeChip(s: GameState, check: AdvanceCheck, onClick: () -> Unit) {
    val next = check.next
    val pulse = if (check.ok) {
        rememberInfiniteTransition(label = "ready").animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a").value
    } else 1f
    val percent = (check.progress * 100).roundToInt()
    val description = "${bi("villageScreen.age")}: ${s.age.label()}" + when {
        next == null -> ""
        check.ok -> ". ${bi("villageScreen.readyAdvance", "nextSl" to next.names)}"
        else -> ". ${bi("villageScreen.towardsAge", "nextSl" to next.names, "lowercase" to next.names.map { it.lowercase() }, "percent" to percent)}"
    }
    Surface(
        onClick = onClick,
        color = if (check.ok) XpGold.copy(alpha = 0.9f * pulse) else HudPlate,
        shape = RoundedCornerShape(50),
        modifier = Modifier.heightIn(min = 40.dp).hudSemantics(description, onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.age.emoji)
            Spacer(Modifier.width(6.dp))
            Text(s.age.sl, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (next != null) {
                Spacer(Modifier.width(8.dp))
                if (check.ok) Text("✨ ${next.emoji}", color = Color.White, fontWeight = FontWeight.Bold)
                else {
                    Box(Modifier.width(38.dp).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(check.progress.coerceIn(0.06f, 1f)).clip(CircleShape).background(XpGold))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(next.emoji, modifier = Modifier.alpha(0.8f))
                }
            }
        }
    }
}

/** A line under the goal: the festival that is on ([gold]), or what's coming ("Jutri · Tomorrow"). */
@Composable
private fun TeaserPill(emoji: String, text: String, gold: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (gold) XpGold.copy(alpha = 0.92f) else HudPlate, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 40.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            Text(
                text, color = if (gold) Color(0xFF2B2118) else Color.White, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The next goal in one line under the HUD; opens the scroll at "Naslednji cilj". */
@Composable
private fun GoalPill(goal: NextGoal, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = HudPlate, shape = RoundedCornerShape(50), modifier = modifier.heightIn(min = 40.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎯", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            Text("${goal.short()} ›", color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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

/** The slim bar at the thumb: the scroll (the book from Vas on), Build, and the event when there is one. */
@Composable
private fun TownBar(
    book: Boolean, today: Int, event: GameEvent?, canBuild: Boolean,
    onScroll: () -> Unit, onBuild: () -> Unit, onEvent: () -> Unit, modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BarButton(
            if (book) "📖" else "📜", if (book) bi("villageScreen.book") else bi("villageScreen.scroll"),
            color = Color(0xFFF1DDB0), ink = Color(0xFF3A2A16), badge = today.takeIf { it > 0 }, onClick = onScroll, modifier = Modifier.weight(1f),
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
                    .clearAndSetSemantics { contentDescription = bi("villageScreen.today", "badge" to badge) },
                contentAlignment = Alignment.Center,
            ) { Text("$badge", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** The scroll's "Naslednji cilj": the goal card, new words, building and the next age. */
@Composable
private fun GoalSection(
    s: GameState, goal: NextGoal, check: AdvanceCheck, vm: AppViewModel,
    onBuild: (BuildOption) -> Unit, onAdvance: () -> Unit, onGather: (Res) -> Unit, onBuildSheet: () -> Unit, onAges: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GoalCard(
            s, goal, check,
            words = vm.content.wordsKnown,
            due = vm.content.dashboard?.dueCards?.size ?: 0,
            rusty = vm.content.wordsRusty,
            onReview = vm::startReview,
            onPolish = { vm.startPolish() },
            onPacks = { vm.openPacks(fromVillage = true) },
            onBuild = onBuild, onAdvance = onAdvance, onGather = onGather, onAges = onAges, onVillagers = vm::openVillagers,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = onBuildSheet, color = FlameOrange.copy(alpha = 0.16f), shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("🔨 ${bi("common.build")}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp)) }
            }
            Surface(onClick = onAges, color = XpGold.copy(alpha = 0.18f), shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(check.next?.let { "${it.emoji} ${it.sl} ›" } ?: "🏰 ${s.age.sl}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp))
                }
            }
        }
        NewWordsCard(vm, fromVillage = true)
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

@Composable
private fun GoalCard(
    s: GameState, goal: NextGoal, check: AdvanceCheck, words: Int, due: Int, rusty: Int, onReview: () -> Unit, onPolish: () -> Unit,
    onPacks: () -> Unit, onBuild: (BuildOption) -> Unit, onAdvance: () -> Unit, onGather: (Res) -> Unit, onAges: () -> Unit,
    onVillagers: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (goal) {
                NextGoal.FeedFire -> {
                    GoalTitle("🔥", bi("common.fireDying"), bi("villageScreen.listeningPracticeBringsWood"))
                    BigButton("🎧 ${bi("common.gatherWood")}", onClick = { onGather(Res.WOOD) }, color = FlameOrange)
                }
                is NextGoal.Advance -> {
                    GoalTitle(goal.to.emoji, bi("villageScreen.readyGrow"), bi("villageScreen.canBecome", "toLabel" to (goal.to.label()), "toEn" to goal.to.names))
                    BigButton("✨ ${bi("villageScreen.advanceTo", "toSl" to goal.to.names)}", onClick = onAdvance, color = XpGold)
                }
                is NextGoal.Build -> {
                    GoalTitle(goal.option.type.emoji, "${bi("townLogic.build")}: ${goal.option.type.label()}", goal.option.effect)
                    CostChips(goal.option.cost) { s.res(it) }
                    BigButton("🔨 ${bi("villageScreen.buildNow")}", onClick = { onBuild(goal.option) }, color = FlameOrange)
                }
                is NextGoal.Grow -> {
                    GoalTitle(goal.to.emoji, "${bi("villageScreen.nextAge")}: ${goal.to.label()}", "${(check.progress * 100).roundToInt()} % · ${stepsLeft(goal.missing.size)}")
                    FillBar(check.progress, XpGold)
                    Checklist(goal.missing)
                    BigButton(bi("villageScreen.gatherFor", "gatherEmoji" to goal.gather.emoji, "lowercase" to goal.gather.names.map { it.lowercase() }), onClick = { onGather(goal.gather) }, color = AlpineGreen)
                    // rusty words hold the age back too: a minute's review, whenever
                    if (rusty > 0) BigButton("🔩 ${bi("reviewScreen.polishNow", "n" to rusty)}", onClick = onPolish)
                }
                is NextGoal.Learn -> {
                    GoalTitle(
                        "📚", bi("villageScreen.learnMoreWords"),
                        bi("villageScreen.everyPlotBuilt", "toEmoji" to goal.to.emoji, "toSl" to goal.to.names, "words" to words, "toEn" to goal.to.names),
                    )
                    Checklist(goal.missing)
                    BigButton("📚 ${bi("villageScreen.learnNewWords")}", onClick = onPacks, color = AlpineGreen)
                    if (due > 0) BigButton("🔄 ${bi("villageScreen.reviewDue", "due" to due)}", onClick = onReview)
                }
                is NextGoal.Friends -> {
                    GoalTitle(
                        "💞", bi("villageScreen.makeFriends"),
                        bi("villageScreen.ageNeedsFriends", "toEmoji" to goal.to.emoji, "toSl" to goal.to.names, "lowercase" to goal.to.names.map { it.lowercase() }),
                    )
                    Checklist(goal.missing)
                    BigButton("👥 ${bi("common.villagers")}", onClick = onVillagers, color = AlpineGreen)
                }
                is NextGoal.Polish -> {
                    GoalTitle("🔩", bi("villageScreen.polishRusty", "n" to goal.rusty), bi("villageScreen.rustyCountAgain", "n" to goal.rusty))
                    Checklist(goal.missing)
                    BigButton("🔩 ${bi("reviewScreen.polishNow", "n" to goal.rusty)}", onClick = onPolish, color = AlpineGreen)
                }
                is NextGoal.Gather -> {
                    GoalTitle(goal.res.emoji, bi("villageScreen.gatherRes", "resLabel" to (goal.res.label())), bi("villageScreen.practiseSkill", "skillSl" to goal.res.skillName(), "skill" to goal.res.skillName()))
                    BigButton(bi("villageScreen.gather", "resEmoji" to goal.res.emoji), onClick = { onGather(goal.res) }, color = AlpineGreen)
                }
            }
            if (goal !is NextGoal.Grow && goal !is NextGoal.Learn && goal !is NextGoal.Friends && goal !is NextGoal.Polish && goal !is NextGoal.Advance && check.next != null) {
                NextAgeRow(check, onAges)
            }
        }
    }
}

@Composable
private fun GoalTitle(emoji: String, title: String, sub: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)) }
        }
    }
}

@Composable
fun Checklist(missing: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (missing.isEmpty()) Text("☑ ${bi("villageScreen.allDone")}", color = AlpineGreen, fontWeight = FontWeight.Bold)
        for (m in missing) Row {
            Text("☐", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(m, style = MaterialTheme.typography.bodyMedium)
        }
    }
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
