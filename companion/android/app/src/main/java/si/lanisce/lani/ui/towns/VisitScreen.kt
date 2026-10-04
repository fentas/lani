package si.lanisce.lani.ui.towns

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.TownVisit
import si.lanisce.lani.data.Towns
import si.lanisce.lani.data.Visits
import si.lanisce.lani.game.render.SceneFit
import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.game.HudPlate
import si.lanisce.lani.ui.game.PlaceCard
import si.lanisce.lani.ui.game.PlaceTap
import si.lanisce.lani.ui.game.TownView
import si.lanisce.lani.ui.game.key
import si.lanisce.lani.ui.game.label
import si.lanisce.lani.ui.game.rememberTownCamera
import si.lanisce.lani.ui.game.townAnchors
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.villagers.SpeechBubble
import si.lanisce.lani.ui.villagers.VillagerLogic
import si.lanisce.lani.ui.villagers.VillagerPortrait
import java.time.LocalDate

/**
 * A linked town, visited (plan 2, §3; companion/README.md, "Visiting"): its village drawn from its public state in its
 * culture's look, its people walking about, in its language explained in the learner's base. A tap on a place shows its
 * card and the ways into its scenes ("Tukaj si gost · You're a guest here": nothing to build), a tap on someone who they
 * are, their greeting and a talk with them. The bar below holds the things to do here (VisitActs.kt): a request of its
 * people, a gift or a message, the market, a question for its learner (TownQuestionScreens.kt). "⌂ Domov · Home" leads
 * back to the learner's own village, back to Friends. A
 * town that isn't answering shows what was had of it last, marked, or says so with a retry.
 */
@Composable
fun VisitScreen(vm: AppViewModel) {
    val v = vm.visits.visit
    BackHandler { vm.leaveVisit(friends = true) }
    if (v == null) {
        // the visit ended under this screen: home
        LaunchedEffect(Unit) { vm.leaveVisit() }
        return
    }
    val s = v.state
    if (s == null) {
        Arrival(v, onRetry = { vm.visits.retry() }, onHome = { vm.leaveVisit() })
        return
    }
    // the host village's background sounds, as at home
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
    val cam = rememberTownCamera()
    val people = remember(v) { v.people() }
    var card by remember { mutableStateOf<PlaceTap?>(null) }
    var villager by remember { mutableStateOf<String?>(null) }
    var act by remember { mutableStateOf<VisitAct?>(null) }
    var topH by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF16202E))) {
        val fit = remember(constraints.maxWidth, constraints.maxHeight) { SceneFit.town(constraints.maxWidth, constraints.maxHeight) }
        val anchors = townAnchors(s, fit)
        fun tapPlace(tap: PlaceTap?) {
            villager = null
            act = null
            // the far hills with nothing to walk into: a tap on nothing, as at home
            card = tap?.takeUnless { it == PlaceTap.Spot(TownSpots.HORIZON) && v.scenesAt(it.place.key()).isEmpty() }
        }
        TownView(
            state = s, cam = cam, fit = fit, anchors = anchors, markers = emptyList(), visitors = emptyList(), clock = townClock,
            topInset = topH, bottomInset = 0, threat = false, badge = { null },
            onPlace = ::tapPlace,
            onMarker = {},
            people = people,
            onVillager = { id, _ -> card = null; act = null; villager = id },
            onProject = { id -> tapPlace(PlaceTap.Landmark(id)) },
            horizon = remember(v) { v.scenesAt("spot:${TownSpots.HORIZON}").isNotEmpty() },
            // the host's people keep their day (where they sleep, their storyteller) by the host's village
            day = remember(v, people) {
                si.lanisce.lani.game.villagers.Routine.Day(
                    homes = si.lanisce.lani.game.scene.Sleep.homes(v.scenes, s, people),
                    tellers = si.lanisce.lani.game.scene.Whereabouts.tellers(v.scenes),
                )
            },
        )
        Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))))
        VisitBanner(v, onHome = { vm.leaveVisit() }, onRetry = { vm.visits.retry() }, modifier = Modifier.onSizeChanged { topH = it.height })
        // above the keyboard too (edge to edge, the window doesn't shrink for it): the gift's message, a question's answer
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding()) {
            AnimatedVisibility(villager != null, enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
                villager?.let { id -> v.villager(id)?.let { GuestCard(vm, it, onDismiss = { villager = null }, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) } }
            }
            AnimatedVisibility(act != null, enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
                act?.let { a -> VisitActPanel(vm, v, a, onClose = { act = null }, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
            }
            // the things to do here: a request, a gift, the market (not over a card: one thing at a time)
            if (villager == null && card == null && act == null) VisitActsBar(onOpen = { act = it })
            AnimatedVisibility(card != null, enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
                val tap = card
                if (tap != null) {
                    BackHandler { card = null }
                    PlaceCard(
                        tap = tap, state = s, here = emptyList(),
                        scenes = v.scenesAt(tap),
                        onMarker = {},
                        onScene = { sc -> card = null; vm.openVisitScene(sc.id) },
                        onBuilding = {}, onFire = {}, onGather = {},
                        onClose = { card = null },
                        guest = true,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * Over the visited town: "🧳 Na obisku · Visiting: Il mio villaggio (Mia)" and "⌂ Domov · Home"; its age, how many live
 * there and the festival that is on; a note when what shows is older (the town isn't answering), with a retry.
 */
@Composable
private fun VisitBanner(v: TownVisit, onHome: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val t = v.town
    Column(modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(color = HudPlate, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Visits.banner(v.name, v.learner), color = Color.White, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    val age = v.state?.age
                    val line = listOfNotNull(
                        Towns.flag(v.language),
                        age?.let { "${it.emoji} ${it.label()}" },
                        t?.villagers?.takeIf { it > 0 }?.let { "👥 $it" },
                        t?.festival?.let(Visits::festivalLine),
                    ).joinToString("  ")
                    Text(line, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(onClick = onHome, modifier = Modifier.heightIn(min = 44.dp)) { Text("⌂ ${bi("common.home")}") }
            }
        }
        if (v.loading) CircularProgressIndicator(Modifier.size(20.dp).align(Alignment.End), color = Color.White, strokeWidth = 2.dp)
        else if (v.stale || v.problem != null) {
            Surface(onClick = onRetry, color = HudPlate, shape = RoundedCornerShape(50)) {
                val asOf = Visits.asOf(v.fetchedAt)?.takeIf { v.stale }?.let { "🕰️ ${bi("visit.asOf", "time" to it)}" }
                Text(
                    listOfNotNull(v.problem, asOf, "↻").joinToString("  "), color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** On the way, or the town never came: "Il mio villaggio ne odgovarja · isn't answering", a retry, home. */
@Composable
private fun Arrival(v: TownVisit, onRetry: () -> Unit, onHome: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SloBlueDeep, Color(0xFF1A2438))))) {
        Column(
            Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("🧳", style = MaterialTheme.typography.displaySmall)
            Text(Visits.banner(v.name, v.learner), color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (v.loading) {
                CircularProgressIndicator(color = Color.White)
                Text(bi("visit.onTheWay"), color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            } else {
                val problem = v.problem ?: "📡 ${bi("visit.notAnswering", "town" to v.name.ifBlank { "?" })}"
                Text(problem, color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                // a town (or tutor) that is away may be back later
                if (problem.startsWith("📡")) Text(bi("visit.tryLater"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                BigButton("↻ ${bi("common.retry")}", onClick = onRetry)
            }
            OutlinedButton(onClick = onHome, modifier = Modifier.heightIn(min = 48.dp)) { Text("⌂ ${bi("common.home")}", color = Color.White) }
        }
    }
}

/**
 * Someone of the visited town: who they are and their greeting to a stranger, in the town's language (a word tapped
 * opens its card), and "💬 Pogovori se · Talk": a role-play with them in the town's language, played by the learner's
 * own tutor at the learner's level in it.
 */
@Composable
private fun GuestCard(vm: AppViewModel, v: Villager, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val hello = remember(v) { VillagerLogic.greeting(v, Bond(), LocalDate.now()) }
    BackHandler(onBack = onDismiss)
    Surface(modifier = modifier.fillMaxWidth().widthIn(max = 560.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VillagerPortrait(v, 64.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${v.emoji} ${v.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (v.role.isNotBlank()) Text(v.role, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDismiss) { EmojiLabel("✕", Labels.CLOSE, style = MaterialTheme.typography.titleMedium) }
            }
            for (l in hello) SpeechBubble(vm, v, l, Modifier.fillMaxWidth())
            // the town's cast can be talked with (its bridge knows their persona); someone born there only greets
            if (vm.visits.visit?.cast?.any { it.id == v.id } == true) {
                FilledTonalButton(onClick = { vm.talkOnVisit(v.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("💬 ${bi("visit.talk")}", textAlign = TextAlign.Center)
                }
            }
        }
    }
}
