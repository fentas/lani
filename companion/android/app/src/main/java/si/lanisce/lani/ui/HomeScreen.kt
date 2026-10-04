package si.lanisce.lani.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.TalkPhase
import si.lanisce.lani.data.previewLine
import si.lanisce.lani.ui.family.FamilySheet
import si.lanisce.lani.ui.home.ChallengesSheet
import si.lanisce.lani.ui.home.ConnectionSheet
import si.lanisce.lani.ui.home.HomeAction
import si.lanisce.lani.ui.home.HomeHero
import si.lanisce.lani.ui.home.HomeLogic
import si.lanisce.lani.ui.home.LearningSection
import si.lanisce.lani.ui.home.NeedTarget
import si.lanisce.lani.ui.home.Tile
import si.lanisce.lani.ui.home.TodayCard
import si.lanisce.lani.ui.home.VillagerNeed
import si.lanisce.lani.ui.home.VillagersStrip
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.l10n.inBase

/**
 * Home, top to bottom: the village header (greeting, streak; it opens the village), "Danes · Today" (the one
 * thing to do next), the villagers who need Jan, "Učenje · Learning" (tiles and the road to the target level),
 * and the tutor's bar, which never covers any of it.
 */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val list = rememberLazyListState()
    var bar by remember { mutableIntStateOf(0) }
    val barHeight = with(LocalDensity.current) { bar.toDp() }
    Box(Modifier.fillMaxSize()) {
        HomeContent(vm, list, bottom = barHeight + 16.dp)
        StatusBarScrim(list, Modifier.align(Alignment.TopCenter))
        TutorBar(vm, Modifier.align(Alignment.BottomCenter).onSizeChanged { bar = it.height })
    }
}

private enum class Sheet { NONE, CHALLENGES, FAMILY, CONNECTION, PAIR, ROAD }

@Composable
private fun HomeContent(vm: AppViewModel, list: LazyListState, bottom: Dp) {
    val d = vm.content.dashboard
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    val talking = vm.talk.state?.takeIf { it.phase == TalkPhase.CHAT }?.scenario?.title
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = bottom)) {
        item("hero") { HomeHero(vm, d, onLink = { sheet = Sheet.CONNECTION }) }
        if (d == null) {
            item("loading") { Text(bi("common.loading"), Modifier.padding(24.dp)) }
            return@LazyColumn
        }
        // a module's or pack's villager only while they live here (or visit today): the rest is the tutor's
        val state = vm.game.state
        val modules = vm.content.modules.map { vm.villagers.shown(it, state) }
        val packs = vm.packs.list.map { vm.villagers.shown(it, state) }
        val festival = si.lanisce.lani.game.Calendar.soon(state, java.time.LocalDate.now())?.id
        val today = HomeLogic.today(d, modules, vm.content.newModules, packs, vm.packs.newIds, talking, vm.family.challenges, festival)
        val offered = (listOf(today.primary) + today.more).filterIsInstance<HomeAction.Module>().map { it.info.id }.toSet()
        val needs = vm.game.state?.let { HomeLogic.needs(it, vm.scenes.active(it), vm.villagers.people(it), vm.scenes.all, offered) }.orEmpty()

        item("today") {
            TodayCard(
                today,
                plan = vm.chat.todayPlan,
                onAction = { act(vm, it) },
                onAskTutor = vm.chat::open,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                village = HomeLogic.village(vm.game.state, story = vm.game.state?.let { vm.scenes.storyTeaser(it) }),
                onVillage = vm::openVillage,
                // who waits on the map now: someone to meet, the burner at his pile; a tap opens them over Home
                // the treasure map first (Stari Janez's map waiting, the path so far), then who waits on the map now
                calls = listOfNotNull(
                    vm.treasure.let { t -> HomeLogic.treasure(t.status(), t.hunt(), t.tellerName(), t.required(), t.waiting) },
                ) + state?.let { HomeLogic.calls(it, vm.villagers.all, si.lanisce.lani.game.scene.Keepers.here(it, java.time.LocalDateTime.now())) }.orEmpty(),
                onCall = { c ->
                    when (c) {
                        is si.lanisce.lani.ui.home.VillageCall.Arrival -> vm.villagers.meet(c.id)
                        is si.lanisce.lani.ui.home.VillageCall.Keeper -> vm.scenes.startKeeper(c.keeper)
                        is si.lanisce.lani.ui.home.VillageCall.Treasure -> vm.openTreasure(fromVillage = false)
                    }
                },
            )
        }
        if (needs.isNotEmpty()) item("villagers") {
            Column(Modifier.padding(top = 24.dp)) {
                VillagersStrip(needs, onOpen = { open(vm, it) }, onAll = if (vm.villagers.all.isNotEmpty()) vm::openVillagers else null)
            }
        }
        item("learning") {
            Column(Modifier.padding(top = 24.dp)) {
                LearningSection(
                    tiles = listOf(
                        Tile("📚", inTarget("homeScreen.newWords"), inBase("homeScreen.newWords"), HomeLogic.words(vm.packs.list, vm.packs.newIds)) { vm.openPacks() },
                        Tile("✨", inTarget("homeScreen.challenges"), inBase("homeScreen.challenges"), HomeLogic.challenges(vm.content.modules, vm.content.newModules)) { sheet = Sheet.CHALLENGES },
                        Tile("🗣️", inTarget("homeScreen.talk"), inBase("homeScreen.talk"), HomeLogic.talk(talking)) { vm.openTalk() },
                        Tile("💌", inTarget("homeScreen.family"), inBase("homeScreen.family"), HomeLogic.family(vm.family.challenges)) {
                            if (vm.family.challenges.isEmpty()) vm.openFamily() else sheet = Sheet.FAMILY
                        },
                        Tile("📖", inTarget("readings.title"), inBase("readings.title"), readingStatus(vm, d.level)) { vm.openReadings() },
                        // audio for the car: got ready here, played in Android Auto or over Bluetooth (companion/README.md, "Im Auto · In the car")
                        Tile("🚗", inTarget("road.title"), inBase("road.title"), vm.road.time?.let { si.lanisce.lani.ui.home.TileStatus(bi("road.tile", "time" to it)) } ?: si.lanisce.lani.ui.home.TileStatus(bi("road.prepare"))) { sheet = Sheet.ROAD },
                    ),
                    road = HomeLogic.road(d),
                    onProgress = { vm.openProgress() },
                ) { if (!vm.speaker.canSay) VoiceHint(vm) }
            }
        }
    }
    when (sheet) {
        Sheet.NONE -> Unit
        Sheet.CHALLENGES -> ChallengesSheet(
            vm.content.modules.map { vm.villagers.shown(it, vm.game.state) },
            vm.content.newModules,
            onOpen = { id ->
                sheet = Sheet.NONE
                vm.openModule(id)
            },
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.FAMILY -> FamilySheet(vm, onDismiss = { sheet = Sheet.NONE })
        Sheet.CONNECTION -> ConnectionSheet(vm, onScan = { sheet = Sheet.PAIR }, onDismiss = { sheet = Sheet.NONE })
        Sheet.PAIR -> PairDialog(vm, onDismiss = { sheet = Sheet.NONE })
        Sheet.ROAD -> si.lanisce.lani.ui.road.RoadSheet(vm, onDismiss = { sheet = Sheet.NONE })
    }
}

/** "Branje": the readings at the learner's [level] and a step up not read yet, the tutor's among them. */
private fun readingStatus(vm: AppViewModel, level: String): si.lanisce.lani.ui.home.TileStatus {
    val read = vm.readings.read()
    val fitting = vm.readings.all.filter {
        val fit = si.lanisce.lani.game.Readings.fit(it.level, level)
        fit == si.lanisce.lani.game.Readings.Fit.AT || fit == si.lanisce.lani.game.Readings.Fit.NEXT
    }
    val unread = fitting.filter { it.id !in read }
    return HomeLogic.reading(unread.size, unread.count { it.source == "tutor" }, vm.readings.all.size)
}

/** Starts what Home offered. */
private fun act(vm: AppViewModel, a: HomeAction) {
    when (a) {
        is HomeAction.Review -> vm.startReview()
        is HomeAction.Module -> vm.openModule(a.info.id)
        is HomeAction.Pack -> vm.startPack(a.info.id)
        is HomeAction.Talk -> vm.openTalk()
        is HomeAction.Family -> vm.openFamily(a.challenge.id)
    }
}

/** A villager's page when the cast knows them, else straight to their request or their scene. */
private fun open(vm: AppViewModel, n: VillagerNeed) {
    val id = n.villagerId?.takeIf { vm.villagers.byId(it) != null }
    if (id != null) return vm.openVillager(id)
    when (val t = n.target) {
        is NeedTarget.Quest -> vm.startQuest(t.id)
        is NeedTarget.Scene -> vm.openScene(t.id, t.focus)
    }
}

/**
 * The tutor, one tap (or an upward swipe) away. Shows the latest message, so replies are visible
 * without opening the chat.
 */
@Composable
private fun TutorBar(vm: AppViewModel, modifier: Modifier) {
    val last = vm.chat.messages.lastOrNull { !it.fromMe }
    val preview = when {
        vm.chat.typing -> bi("homeScreen.typing")
        last != null -> previewLine(last.text)
        else -> bi("homeScreen.askAnything")
    }
    Surface(
        onClick = vm.chat::open,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp)
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -12f) vm.chat.open() } },
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        tonalElevation = 4.dp,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text("🧑‍🏫")
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(bi("common.yourTutor"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(preview, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
            if (vm.chat.unread > 0) {
                Surface(color = TriglavRed, shape = CircleShape) {
                    Text("${vm.chat.unread}", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Text("⌃", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** No Slovene text-to-speech voice on the phone: listening and dictation need one. */
@Composable
private fun VoiceHint(vm: AppViewModel) {
    Card(
        onClick = vm.speaker::openVoiceSettings,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔇", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(bi("homeScreen.installSloveneVoice"), style = MaterialTheme.typography.titleSmall)
                Text(bi("homeScreen.unlocksListeningDictation"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
