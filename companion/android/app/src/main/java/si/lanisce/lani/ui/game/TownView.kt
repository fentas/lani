package si.lanisce.lani.ui.game

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.render.CameraRig
import si.lanisce.lani.game.render.CanvasPoint
import si.lanisce.lani.game.render.PlotMarks
import si.lanisce.lani.game.render.SceneFit
import si.lanisce.lani.game.render.TownAnchors
import si.lanisce.lani.game.render.TownScene
import si.lanisce.lani.game.render.VillageHit
import si.lanisce.lani.game.render.VillageRenderer
import si.lanisce.lani.game.render.TownClock
import si.lanisce.lani.game.render.rememberTownClock
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.game.sky.SkyTap
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.bi
import kotlin.math.roundToInt

/** What a tap on the village landed on. */
sealed interface PlaceTap {
    val place: TownPlace

    data object Fire : PlaceTap { override val place: TownPlace get() = TownPlace.Fire }
    data object Forest : PlaceTap { override val place: TownPlace get() = TownPlace.Forest }
    /** A building; [place] its type's, or a house's own (its room's bubbles and card: [TownPlace.of]). */
    data class Built(val building: Building, override val place: TownPlace = TownPlace.At(building.type)) : PlaceTap
    /** A named spot of the landscape (see [TownSpots]): the woodpile, the rocks, the meadow's bushes, the pond, the stream bank, the horizon. */
    data class Spot(val id: String) : PlaceTap { override val place: TownPlace get() = TownPlace.Spot(id) }
    /** A finished project's landmark that leads into a scene (the vineyard's terraces into the vineyard). */
    data class Landmark(val project: String) : PlaceTap { override val place: TownPlace get() = TownPlace.Landmark(project) }
}

/** The screen area (left, top, right, bottom px) that the HUD and the bottom bar leave free. */
fun freeArea(w: Float, h: Float, top: Float, bottom: Float, margin: Float): FloatArray =
    floatArrayOf(0f, top + margin, w, (h - bottom - margin).coerceAtLeast(top + margin + 1f))

/** Of the room the home view has to spare above and below the village, this much goes above: the woods below are worth more than sky. */
const val HOME_ABOVE = 0.35f

/**
 * What a tap landed on: a place (or nothing), someone who lives here (with where they stand, nominal canvas px), a project's
 * landmark, or a wild animal (its word).
 */
private sealed interface Tapped {
    data class Place(val tap: PlaceTap?) : Tapped
    data class Person(val id: String, val at: CanvasPoint?) : Tapped
    data class Project(val id: String) : Tapped
    data class Animal(val word: String) : Tapped
    /** The moon, a star, a planet, a constellation, the Milky Way or a shooting star (see [si.lanisce.lani.game.sky.SkyTap]). */
    data class Sky(val tap: SkyTap) : Tapped
}

/**
 * The full-screen village: the pixel scene under a pinch-zoom camera (finer as it comes closer), the bubbles
 * over its places and people, and taps mapped back through the camera to what's there.
 *
 * @param topInset screen pixels the HUD covers at the top; [bottomInset] the bottom bar's
 * @param anchors bubble anchors (canvas px) for [state] on this view's canvas; see [townAnchors]
 * @param people the villagers who live here, drawn around their homes
 * @param onPlace a tap on the fire, the forest, a building or a spot; null for a tap on nothing (the meadow, the sky)
 * @param onVillager a tap on someone who lives here (their villager id) or on their TalkBack node, with where they stand (nominal canvas px)
 * @param followId the villager a marker belongs to, if they're on the map: its bubble follows them
 * @param onProject a tap on a village project's landmark, or on its TalkBack node (the project's id)
 * @param horizon something opens at the horizon (a culture's own landscape): TalkBack gets a node for it too
 * @param standing who has a happening on now and where ([si.lanisce.lani.game.scene.TownMarkers.standing]): they stand
 *   there, or aren't drawn when it's far off
 * @param face the villager whose face a marker's small bubble shows: someone away from the clearing (see
 *   [si.lanisce.lani.game.render.TownPeople.away]), whose bubble sits at the far place instead of them
 * @param onAnimal a tap right on a wild animal round the village (see [si.lanisce.lani.game.render.Wildlife]): its word;
 *   null: the animals aren't tappable, a tap on one is a tap on where it stands
 * @param onSky a tap on the night sky's moon, a star, a planet, a constellation, the Milky Way or a shooting star; null:
 *   the sky isn't tappable
 * @param skyMark what of the sky to show brighter, its card being open ([SkyTap.mark])
 * @param onSkyList TalkBack's "🌌 Nebo nocoj · Tonight's sky": what of the sky the picture shows now, to open from a list
 *   ([si.lanisce.lani.game.sky.SkyTargets.up]); null: no such action
 * @param clock the picture's clock; the caller's, to time the sounds of what it shows on it too (the thunder after a flash)
 * @param plots the plot chooser's marks (see [PlotMarks]): while it is on, a tap chooses a plot ([onPlot]: the plot, or
 *   a building standing on one to swap with) instead of opening what's there, and TalkBack finds the plots ([plotLabel])
 * @param day the villagers' day ([si.lanisce.lani.game.villagers.Routine.Day]: where each sleeps, the storytellers, who
 *   waits for the learner), for where they are and whose windows are lit
 */
@Composable
fun TownView(
    state: GameState,
    cam: TownCameraState,
    fit: SceneFit,
    anchors: Map<TownPlace, CanvasPoint>,
    markers: List<TownMarker>,
    visitors: List<TownPlace>,
    topInset: Int,
    bottomInset: Int,
    threat: Boolean,
    badge: (TownMarker) -> String?,
    onPlace: (PlaceTap?) -> Unit,
    onMarker: (TownMarker) -> Unit,
    modifier: Modifier = Modifier,
    justBuilt: String? = null,
    celebrate: Int = 0,
    highlightPlot: Int? = null,
    people: List<Villager> = emptyList(),
    onVillager: (String, CanvasPoint?) -> Unit = { _, _ -> },
    followId: (TownMarker) -> String? = { null },
    onProject: (String) -> Unit = {},
    horizon: Boolean = false,
    standing: Map<String, TownPlace> = emptyMap(),
    face: (TownMarker) -> Villager? = { null },
    onAnimal: ((String) -> Unit)? = null,
    onSky: ((SkyTap) -> Unit)? = null,
    skyMark: String? = null,
    onSkyList: ((List<SkyTap>) -> Unit)? = null,
    clock: TownClock = rememberTownClock(),
    plots: PlotMarks? = null,
    onPlot: (Int) -> Unit = {},
    plotLabel: (Int) -> String = { "$it" },
    day: si.lanisce.lani.game.villagers.Routine.Day = si.lanisce.lani.game.villagers.Routine.Day(),
) {
    val density = LocalDensity.current
    val renderer = remember { VillageRenderer() }
    val latestState by rememberUpdatedState(state)
    val tapPlace by rememberUpdatedState(onPlace)
    val tapPerson by rememberUpdatedState(onVillager)
    val tapProject by rememberUpdatedState(onProject)
    val tapAnimal by rememberUpdatedState(onAnimal)
    val tapSky by rememberUpdatedState(onSky)
    val skyList by rememberUpdatedState(onSkyList)
    val choosing by rememberUpdatedState(plots)
    val tapPlot by rememberUpdatedState(onPlot)
    // QA's "zoom" (a debug build's hook, app/QaHooks): closer, as the "Približaj · Zoom in" action
    val qaZooms = si.lanisce.lani.app.QaHooks.zooms
    var qaSeen by remember { mutableStateOf(qaZooms) }
    LaunchedEffect(qaZooms) { if (qaZooms > qaSeen && cam.rig != null) { qaSeen = qaZooms; cam.zoomBy(2f) } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val rig = remember(fit, w, h) { CameraRig.town(fit, w, h) }
        val homeRect = remember(state.age, fit) { TownAnchors.home(state, fit.width, fit.height) }
        val margin = with(density) { 12.dp.toPx() }
        val home = remember(rig, homeRect, topInset, bottomInset) {
            rig.frame(homeRect, freeArea(w.toFloat(), h.toFloat(), topInset.toFloat(), bottomInset.toFloat(), margin), maxFrame = rig.minScale * 2, above = HOME_ABOVE)
        }
        SideEffect { if (cam.rig != rig || cam.home != home) cam.update(rig, home) }

        /** What's under a screen point: through the camera to nominal pixels, then through the last frame's lens to its pixels. */
        fun hit(p: Offset): Tapped {
            val c = cam.camera
            val lens = renderer.lens
            val nx = c.toCanvasX(p.x); val ny = c.toCanvasY(p.y)
            val x = lens.cx(nx); val y = lens.cy(ny)
            val slop = (with(density) { 16.dp.toPx() } / (c.scale / lens.k)).roundToInt().coerceIn(2, 8 * lens.k)
            /** Nothing drawn there: the forest round the clearing, the land at the horizon, or nothing. */
            fun ground(): Tapped = Tapped.Place(
                when {
                    TownAnchors.isForest(latestState, fit.width, fit.height, nx, ny) -> PlaceTap.Forest
                    // the hills and mountains behind the village (or the sea): what nothing else there took
                    TownAnchors.isHorizon(fit.width, fit.height, nx, ny) -> PlaceTap.Spot(TownSpots.HORIZON)
                    else -> null
                },
            )
            return when (val hit = renderer.hitTest(x.toInt(), y.toInt(), slop)) {
                is VillageHit.OnBuilding -> Tapped.Place((latestState.buildings.firstOrNull { it.id == hit.building.id } ?: hit.building).let { PlaceTap.Built(it, TownPlace.of(it, latestState)) })
                VillageHit.OnFire -> Tapped.Place(PlaceTap.Fire)
                is VillageHit.OnVillager -> Tapped.Person(hit.id, renderer.villagerAnchors[hit.id])
                is VillageHit.OnSpot -> Tapped.Place(PlaceTap.Spot(hit.id))
                is VillageHit.OnLandmark -> Tapped.Project(hit.project)
                is VillageHit.OnAnimal -> if (tapAnimal != null) Tapped.Animal(hit.word) else ground()
                // the night sky: the moon, a star, a planet … within a finger's reach (nominal px), else the land
                null -> tapSky?.let { renderer.skyAt(nx, ny, with(density) { 20.dp.toPx() } / c.scale, System.currentTimeMillis()) }?.let { Tapped.Sky(it) } ?: ground()
            }
        }

        /**
         * The plot chooser's tap: right on a building that stands on a plot to choose (swapping), that plot; else the plot
         * to choose nearest the finger (within its reach), or null.
         */
        fun plotAt(p: Offset, pick: PlotMarks): Int? {
            val c = cam.camera
            val lens = renderer.lens
            val nx = c.toCanvasX(p.x); val ny = c.toCanvasY(p.y)
            val slop = (with(density) { 12.dp.toPx() } / (c.scale / lens.k)).roundToInt().coerceIn(2, 8 * lens.k)
            val on = (renderer.hitTest(lens.cx(nx).toInt(), lens.cy(ny).toInt(), slop) as? VillageHit.OnBuilding)?.building?.id
            // the building's plot where it stands now (the map may show it elsewhere: the one it would swap with)
            on?.let { pick.standing[it] }?.let { return it }
            val reach = maxOf(14f, with(density) { 28.dp.toPx() } / c.scale)
            return TownAnchors.plotAt(latestState, fit.width, fit.height, nx, ny, pick.all, reach)
        }

        if (cam.rig != null) {
            TownScene(
                state = state,
                fit = fit,
                camera = { cam.camera },
                renderer = renderer,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(rig) {
                        detectTapGestures(onDoubleTap = { cam.doubleTap(it) }, onTap = {
                            val pick = choosing
                            if (pick != null) { plotAt(it, pick)?.let(tapPlot); return@detectTapGestures }
                            when (val t = hit(it)) {
                                is Tapped.Person -> tapPerson(t.id, t.at)
                                is Tapped.Place -> tapPlace(t.tap)
                                is Tapped.Project -> tapProject(t.id)
                                is Tapped.Animal -> tapAnimal?.invoke(t.word)
                                is Tapped.Sky -> tapSky?.invoke(t.tap)
                            }
                        })
                    }
                    .pointerInput(rig) { detectTownGestures(cam::gesture, cam::release) }
                    .semantics {
                        contentDescription = bi("townView.village")
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(bi("common.zoom")) { cam.zoomBy(2f); true },
                            CustomAccessibilityAction(bi("common.zoomOut")) { cam.zoomBy(0.5f); true },
                            CustomAccessibilityAction(bi("common.recenter")) { cam.recenter(); true },
                            // the moon's card, drawn in pixels: for TalkBack an action; and what's up now, a list to open
                            onSky?.let { CustomAccessibilityAction("🌙 ${bi("sky.moon")}") { tapSky?.invoke(SkyTap.Moon); true } },
                            onSkyList?.let { CustomAccessibilityAction("🌌 ${bi("sky.tonight")}") { skyList?.invoke(renderer.skyUp()); true } },
                        )
                    },
                highlightPlot = highlightPlot,
                justBuilt = justBuilt,
                celebrate = celebrate,
                visitors = visitors,
                detail = cam.detail,
                people = people,
                clock = clock,
                standing = standing,
                skyMark = skyMark,
                plots = plots,
                day = day,
            )
            if (plots != null) PlotTargets(state, fit, plots, plotLabel, { cam.camera }) { tapPlot(it) }
            else {
                PlaceTargets(state, fit, horizon, { cam.camera }, fire = false) { tapPlace(it) }
                LandmarkTargets(state, fit, { cam.camera }) { tapProject(it) }
                PeopleTargets(people, renderer, { cam.camera }, { clock.tick }) { id -> tapPerson(id, renderer.villagerAnchors[id]) }
                if (onAnimal != null) AnimalTargets(renderer, { cam.camera }, { clock.tick }) { word -> tapAnimal?.invoke(word) }
                // the fire's last, over whoever stands round it: a target covered entirely is left out of TalkBack's tree
                PlaceTargets(state, fit, horizon, { cam.camera }, fire = true) { tapPlace(it) }
            }
            // off the fire's flames and its target: a tap on the fire is the fire's, whoever stands beside it with a bubble
            val fireArea = remember(state.fire, state.age, fit) { TownAnchors.fireArea(state, fit.width, fit.height) }
            val fireBody = remember(state.fire, state.age, fit) { TownAnchors.fireBody(state, fit.width, fit.height) }
            TownBubbles(
                markers, anchors, { cam.camera }, threat, badge, onMarker, topInset = topInset,
                followId = followId, anchorOf = { id -> renderer.villagerAnchors[id] }, tick = { clock.tick }, face = face,
                fireArea = fireArea, fireBody = fireBody,
            )
        }
    }
}

/**
 * An invisible node over each place (every building, the forest, the landscape's spots; with [fire] the fire's alone,
 * a little bigger: [TownTargets.FIRE_DP]) with its name and a click action, so TalkBack can find and open what's drawn in
 * pixels. No pointer input: touches go on to the scene underneath, which hits the same place.
 */
@Composable
private fun PlaceTargets(state: GameState, fit: SceneFit, horizon: Boolean, camera: () -> si.lanisce.lani.game.render.Camera, fire: Boolean, onPlace: (PlaceTap) -> Unit) {
    val bodies = remember(state.buildings, state.age, state.fire, fit, horizon) { TownAnchors.bodies(state, fit.width, fit.height, horizon) }
    val density = LocalDensity.current
    val dp = if (fire) TownTargets.FIRE_DP else TownTargets.DP
    val size = with(density) { dp.dp.roundToPx() }
    for ((id, p) in bodies) {
        if ((id == "fire") != fire) continue
        val tap = when {
            id == "fire" -> PlaceTap.Fire
            id == "forest" -> PlaceTap.Forest
            id.startsWith("spot:") -> PlaceTap.Spot(id.removePrefix("spot:"))
            else -> state.buildings.firstOrNull { it.id == id }?.let { PlaceTap.Built(it, TownPlace.of(it, state)) }
        } ?: continue
        val label = when (tap) {
            is PlaceTap.Built -> tap.place.label() + ", ${bi("townView.level")} ${tap.building.level}" + if (tap.building.damaged) ", ${bi("common.damaged")}" else ""
            else -> tap.place.label()
        }
        key(id) {
            Box(
                Modifier
                    .offset { val c = camera(); TownTargets.place(c.toScreenX(p.x), c.toScreenY(p.y), size).let { IntOffset(it[0], it[1]) } }
                    .size(dp.dp)
                    .semantics {
                        contentDescription = label
                        role = Role.Button
                        onClick { onPlace(tap); true }
                    },
            )
        }
    }
}

/**
 * While the plot chooser is on: an invisible node over each plot to choose ([PlotMarks.all]), named by [label] ("Parcela 5
 * · Plot 5, ★ …"), with a click action choosing it, so TalkBack can choose a plot drawn in pixels. No pointer input.
 */
@Composable
private fun PlotTargets(state: GameState, fit: SceneFit, plots: PlotMarks, label: (Int) -> String, camera: () -> si.lanisce.lani.game.render.Camera, onPlot: (Int) -> Unit) {
    val centres = remember(state.land, state.age, plots.all, fit) { plots.all.map { it to TownAnchors.plotCenter(state, it, fit.width, fit.height) } }
    val density = LocalDensity.current
    val half = with(density) { 20.dp.roundToPx() }
    for ((plot, p) in centres) key("plot:$plot") {
        Box(
            Modifier
                .offset { val c = camera(); IntOffset(c.toScreenX(p.x).roundToInt() - half, c.toScreenY(p.y).roundToInt() - half) }
                .size(40.dp)
                .semantics {
                    contentDescription = label(plot)
                    role = Role.Button
                    selected = plot == plots.chosen
                    onClick { onPlot(plot); true }
                },
        )
    }
}

/**
 * An invisible node over each village project's landmark (see [TownAnchors.landmarks]), named after its project and how
 * far it is built, with a click action opening the project, so TalkBack can find what's drawn in pixels. No pointer
 * input: touches go on to the scene underneath, which hits the same landmark.
 */
@Composable
private fun LandmarkTargets(state: GameState, fit: SceneFit, camera: () -> si.lanisce.lani.game.render.Camera, onProject: (String) -> Unit) {
    val marks = remember(state.projects, state.buildings, state.age, fit) { TownAnchors.landmarks(state, fit.width, fit.height) }
    val density = LocalDensity.current
    val half = with(density) { 20.dp.roundToPx() }
    for ((spot, p) in marks) {
        val m = spot.landmark
        val spec = Projects.spec(m.project) ?: continue
        val label = "${spec.emoji} ${spec.name}" + if (m.finished) "" else ", ${bi("townView.beingBuilt", "stage" to m.stage, "stages" to m.stages)}"
        key("project:${m.project}") {
            Box(
                Modifier
                    .offset { val c = camera(); IntOffset(c.toScreenX(p.x).roundToInt() - half, c.toScreenY(p.y).roundToInt() - half) }
                    .size(40.dp)
                    .semantics {
                        contentDescription = label
                        role = Role.Button
                        onClick { onProject(m.project); true }
                    },
            )
        }
    }
}

/**
 * An invisible node over each person out on the map, named after them, so TalkBack can find and open the
 * people drawn in pixels; it follows them about (the renderer's anchors, read each tick). No pointer input.
 */
@Composable
private fun PeopleTargets(people: List<Villager>, renderer: VillageRenderer, camera: () -> si.lanisce.lani.game.render.Camera, tick: () -> Long, onVillager: (String) -> Unit) {
    val density = LocalDensity.current
    val size = with(density) { TownTargets.DP.dp.roundToPx() }
    for (v in people) key(v.id) {
        Box(
            Modifier
                .offset {
                    tick()
                    val a = renderer.villagerAnchors[v.id]
                    if (a == null) IntOffset(-10_000, -10_000) // indoors: nowhere to tap
                    else { val c = camera(); TownTargets.person(c.toScreenX(a.x), c.toScreenY(a.y), size).let { IntOffset(it[0], it[1]) } }
                }
                .size(TownTargets.DP.dp)
                .semantics {
                    contentDescription = v.name
                    role = Role.Button
                    onClick { onVillager(v.id); true }
                },
        )
    }
}

/**
 * An invisible node over each wild animal out on the map (see [si.lanisce.lani.game.render.Wildlife]), named with its word,
 * so TalkBack can find and look up the animals drawn in pixels; it follows them (the renderer's last frame, read each tick),
 * and is off the screen while that animal isn't out. No pointer input.
 */
@Composable
private fun AnimalTargets(renderer: VillageRenderer, camera: () -> si.lanisce.lani.game.render.Camera, tick: () -> Long, onAnimal: (String) -> Unit) {
    val density = LocalDensity.current
    val half = with(density) { 20.dp.roundToPx() }
    for (kind in si.lanisce.lani.game.render.Wild.entries) key("wild:${kind.name}") {
        Box(
            Modifier
                .offset {
                    tick()
                    val a = renderer.wildlife.firstOrNull { it.kind == kind }?.at
                    if (a == null) IntOffset(-10_000, -10_000)
                    else { val c = camera(); IntOffset(c.toScreenX(a.x).roundToInt() - half, c.toScreenY(a.y).roundToInt() - half * 3 / 2) }
                }
                .size(40.dp)
                .semantics {
                    contentDescription = kind.word
                    role = Role.Button
                    onClick { onAnimal(kind.word); true }
                },
        )
    }
}

/** Bubble anchors for [state] on [fit]'s canvas, recomputed only when what they depend on changes. */
@Composable
fun townAnchors(state: GameState, fit: SceneFit): Map<TownPlace, CanvasPoint> =
    remember(state.buildings, state.age, state.fire, state.projects, fit) { TownAnchors.withSpots(state, fit.width, fit.height) }
