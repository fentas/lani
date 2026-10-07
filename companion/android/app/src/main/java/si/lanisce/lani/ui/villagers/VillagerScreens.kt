package si.lanisce.lani.ui.villagers

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.TalkPhase
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestQueue
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.StatusBarScrim
import si.lanisce.lani.ui.game.emoji
import si.lanisce.lani.ui.game.formatRes
import si.lanisce.lani.ui.game.label
import si.lanisce.lani.ui.game.laterTitle
import si.lanisce.lani.ui.game.practiseHardLabel
import si.lanisce.lani.ui.game.progressText
import si.lanisce.lani.ui.game.whyText
import si.lanisce.lani.ui.game.where
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import java.time.LocalDateTime

// "Prebivalci · Villagers" (companion/VILLAGERS.md): the register of the village's people, one villager's
// page (who they are, the friendship, what they remember, talking to them), and the compact card the
// village map shows when someone is tapped.

/**
 * What a villager is up to today: their requests up now ([quests], two at most) and those waiting in "Later" ([waiting],
 * why each waits; companion/GAME.md "Quests"), all the village's quests ([all]: a task with a drill for it isn't "hard"), and
 * the happenings with them now and later.
 */
private class Today(
    val quests: List<Quest>, val waiting: List<QuestQueue.Waiting>, val all: List<Quest>,
    val now: List<ActiveHappening>, val later: List<Pair<TimeOfDay, ActiveHappening>>,
)

private fun todayOf(vm: AppViewModel, v: Villager, s: GameState?): Today {
    if (s == null) return Today(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    val t = LocalDateTime.now()
    // as the map shows them; someone not in the village today (a tutor's request waits for them) keeps theirs on the card
    val queue = Residents.queue(s, vm.villagers.all, t.toLocalDate())
    val up = queue.upOf(v.name)
    val waiting = queue.laterOf(v.name)
    val own = if (up.isEmpty() && waiting.isEmpty()) QuestQueue.of(s.quests, s.quests.filter { !it.done && it.giver == v.name }, t.toLocalDate()) else null
    return Today(
        quests = own?.up ?: up,
        waiting = own?.later ?: waiting,
        all = s.quests,
        now = vm.scenes.active(s, t).filter { VillagerLogic.isVillager(it.person, v) },
        later = Happenings.later(vm.scenes.all, s, t).filter { VillagerLogic.isVillager(it.second.person, v) },
    )
}

/** "na travniku · in the meadow": where they are in this village (their first home that exists). */
private fun whereOf(v: Villager, s: GameState?): String = s?.let { TownMarkers.homeOf(v, it).where() } ?: ""

// --- the register -------------------------------------------------------------------------------------

/**
 * "Prebivalci · Villagers": who lives in the village (families together, closest friends first), who's
 * coming next, and the cast who haven't moved in yet, with why; those of a later age as silhouettes.
 */
@Composable
fun VillagersScreen(vm: AppViewModel) {
    var card by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler { if (card != null) card = null else vm.openVillage() }
    val s = vm.game.state
    val cast = vm.villagers.all
    val scenes = vm.scenes.all
    val reg = remember(s, cast, scenes) {
        val busy = s?.let { st -> vm.scenes.active(st).mapNotNull { a -> cast.firstOrNull { VillagerLogic.isVillager(a.person, it) }?.id }.toSet() }.orEmpty()
        VillagerLogic.register(cast, s, VillagerLogic.needing(cast, s?.quests.orEmpty(), busy), LocalDate.now())
    }
    val coming = remember(s, cast) { VillagerLogic.coming(vm.villagers.outlook(s), cast) }
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                GradientHeader(
                    "👥 ${bi("common.villagers")}",
                    bi("villagerScreens.whoLivesVillageHow"),
                    "‹ ${bi("common.village")}",
                    vm::openVillage,
                )
            }
            if (reg.here.isEmpty() && reg.notYet.isEmpty()) item {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(bi("villagerScreens.villagersHaventArrivedYet"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        bi("villagerScreens.onceTutorConnectedYoull"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BigButton("↻ ${bi("common.retry")}", onClick = vm::openVillagers)
                }
            }
            coming?.let { text -> item(key = "coming") { Coming(text) } }
            if (reg.here.isNotEmpty()) item(key = "hereTitle") {
                val n = reg.here.sumOf { it.size }
                SectionTitle("🏡 ${bi("villagerScreens.livesHereN", "n" to n)}")
            }
            for (unit in reg.here) {
                val family = unit.firstOrNull()?.resident?.family
                if (unit.size > 1 && family != null) item(key = "family:$family") {
                    Text(
                        "👪 ${VillagerLogic.familyName(family)}",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp).semantics { heading() },
                    )
                }
                items(unit, key = { it.villager.id }) { e ->
                    RegisterCard(
                        vm, e, s, indent = unit.size > 1,
                        onTalk = { card = e.villager.id }, onOpen = { vm.openVillager(e.villager.id) },
                    )
                }
            }
            if (reg.notYet.isNotEmpty()) item(key = "notYetTitle") { SectionTitle("🧳 ${bi("villagerScreens.visitingToday")}") }
            items(reg.notYet, key = { it.villager.id }) { e ->
                RegisterCard(vm, e, s, onTalk = { card = e.villager.id }, onOpen = { vm.openVillager(e.villager.id) })
            }
            if (reg.toCome > 0) item(key = "toCome") {
                Text(
                    "✨ ${bi("villagerScreens.morePeopleWillCome", "toCome" to reg.toCome)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        StatusBarScrim(list, Modifier.align(Alignment.TopCenter))
        // A tap on 💬 shows the villager's card, as on the village map.
        if (card != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(onClickLabel = Labels.CLOSE) { card = null })
        AnimatedVisibility(
            card != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            val id = card
            if (id != null) VillagerCard(vm, id, onDismiss = { card = null }, modifier = Modifier.navigationBarsPadding().padding(12.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** "Kdo pride · Who's coming": who moves in next and what they wait for. */
@Composable
private fun Coming(text: String) {
    Surface(
        color = XpGold.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) { },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🧳 ${bi("villagerScreens.whosComing")}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RegisterCard(vm: AppViewModel, e: RegisterEntry, s: GameState?, onTalk: () -> Unit, onOpen: () -> Unit, indent: Boolean = false) {
    val v = e.villager
    val today = remember(v, s) { todayOf(vm, v, s) }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(start = if (indent) 28.dp else 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(if (e.needs) 4.dp else 2.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                VillagerPortrait(v, 64.dp)
                if (e.needs) Box(
                    Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).clip(CircleShape).background(XpGold),
                    contentAlignment = Alignment.Center,
                ) { Text("!", color = Color(0xFF2B2118), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clearAndSetSemantics { }) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${v.emoji} ${v.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (v.role.isNotBlank()) Text(v.role, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                FriendshipBar(e.bond.points, VillagerLogic.female(v), compact = true)
                val quest = today.quests.firstOrNull()
                val now = today.now.firstOrNull()
                when {
                    // the first of their requests up, and how many more ("+1")
                    quest != null -> Status("📋 ${quest.title}" + if (today.quests.size > 1) "  +${today.quests.size - 1}" else "", strong = true)
                    now != null && s != null -> Status("💬 ${now.happening.title} · ${TownMarkers.placeOf(now, s).where()}", strong = true)
                }
                if (!e.livesHere) e.why?.let { Status("🧳 $it") }
                VillagerLogic.born(e.resident)?.let { Status(it) }
                when {
                    !e.met && e.livesHere -> Status("${VillagerLogic.notMet(v)} · 📍 ${whereOf(v, s)}")
                    !e.met -> Status(VillagerLogic.notMet(v))
                    e.livesHere -> Status("🏠 ${whereOf(v, s)}")
                }
            }
            FilledTonalIconButton(onClick = onTalk, modifier = Modifier.padding(start = 4.dp)) {
                EmojiLabel("💬", "${bi("common.talkVerb")}: ${v.name}")
            }
        }
    }
}

@Composable
private fun Status(text: String, strong: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.bodySmall,
        color = if (strong) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun SilhouetteCard(e: RegisterEntry) {
    val v = e.villager
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).semantics(mergeDescendants = true) { },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            VillagerPortrait(v, 64.dp, silhouette = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(v.role.ifBlank { "? ? ?" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.why?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// --- one villager's page ----------------------------------------------------------------------------

/** One villager: who they are, the friendship, what they remember, their requests, and talking to them. */
@Composable
fun VillagerScreen(vm: AppViewModel, id: String) {
    BackHandler { vm.openVillagers() }
    val v = vm.villagers.byId(id)
    if (v == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = vm::openVillagers) { Text("‹ ${bi("common.villagers")}") }
            Text(bi("villagerScreens.weDontKnowVillager"), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val s = vm.game.state
    val bond = vm.villagers.bond(s, id)
    val level = Bonds.level(bond.points)
    val female = VillagerLogic.female(v)
    val greeting = rememberGreeting(id)
    val pose = villagerPose(id, greeting)
    val pop = remember(id, level) { vm.villagers.levelUpSince(id, level) }
    val today = remember(v, s) { todayOf(vm, v, s) }
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Hero(vm, v, s, bond, pose, pop) }
            item { TalkPanel(vm, v, greeting, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            // the storyteller: the notebook of the stories he told
            if (tellsStories(vm, id)) item {
                si.lanisce.lani.ui.notebook.NotebookRow(si.lanisce.lani.ui.notebook.rememberNotebook(vm), Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { vm.openNotebook() }
            }
            if (today.quests.isNotEmpty() || today.waiting.isNotEmpty() || today.now.isNotEmpty() || today.later.isNotEmpty()) item {
                Section("📅 ${bi("common.today")}", Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { TodayRows(vm, v, s, today) }
            }
            item {
                Section("♥ ${bi("villagerParts.friendship")}", Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    FriendshipBar(bond.points, female, pop = pop)
                    val met = bond.met
                    Text(
                        if (met != null) "🤝 ${bi("villagerScreens.metOn", "date" to VillagerLogic.dateArg(met), "enDate" to VillagerLogic.enDate(met))}"
                        else "${VillagerLogic.notMet(v)}: ${bi("villagerScreens.sayHello", "g" to if (female) "f" else "m")}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${bi("villagerScreens.howItGrows")}: 📋 ${bi("chestSheet.aRequest")} +${Bonds.QUEST}   💬 ${bi("villagerScreens.sceneDialog")} +${Bonds.DIALOG}   " +
                            "🗣️ ${bi("villagerScreens.talkWithTutor")} +${Bonds.TALK}   🎁 ${bi("villagerScreens.aGift")} +${si.lanisce.lani.game.Catalog.GIFT_POINTS}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (vm.villagers.livesHere(s, id)) item { Gifts(vm, v, s, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            item { Memories(v, bond, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            item { About(v, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        }
        StatusBarScrim(list, Modifier.align(Alignment.TopCenter))
    }
}

/** The top of the page: the big portrait (waving hello), name, role, where they are, the hearts. */
@Composable
private fun Hero(vm: AppViewModel, v: Villager, s: GameState?, bond: Bond, pose: Pose, pop: Boolean) {
    val female = VillagerLogic.female(v)
    val level = Bonds.level(bond.points)
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(SloBlueDeep, SloBlue)))) {
        Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)) {
            TextButton(onClick = vm::openVillagers) { Text("‹ ${bi("common.villagers")}", color = Color.White) }
            Row(Modifier.padding(start = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    VillagerPortrait(v, 128.dp, pose = pose)
                    if (pop) HeartBurst(level, Modifier.align(Alignment.TopCenter))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${v.emoji} ${v.name}", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (v.role.isNotBlank()) Text(v.role, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f))
                    val resident = s?.residents?.firstOrNull { it.id == v.id }
                    val line = when {
                        resident != null -> listOfNotNull(
                            whereOf(v, s).takeIf { it.isNotBlank() }?.let { "🏠 $it" },
                            VillagerLogic.born(resident)
                                ?: "📅 ${bi("villagerScreens.hereSince", "date" to VillagerLogic.dateArg(resident.since), "enDate" to VillagerLogic.enDate(resident.since))}",
                        ).joinToString("\n")
                        else -> "🧳 ${VillagerLogic.whyNotHere(v, s).text}"
                    }
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    Surface(color = Color.White.copy(alpha = 0.92f), shape = RoundedCornerShape(50)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Hearts(level, female, pop = pop)
                            Spacer(Modifier.width(6.dp))
                            Text(VillagerLogic.levelName(level, female), style = MaterialTheme.typography.labelMedium, color = Color(0xFF3A2A30), modifier = Modifier.clearAndSetSemantics { })
                        }
                    }
                }
            }
        }
    }
}

/**
 * "💬 Pogovori se · Talk": the scripted greeting (and a memory), voiced; then "🗣️ Pogovor s tutorjem ·
 * Talk freely", a role-play with the tutor playing them.
 */
@Composable
private fun TalkPanel(vm: AppViewModel, v: Villager, greeting: Greeting, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val short = VillagerLogic.shortName(v.name)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (l in greeting.said) SpeechBubble(vm, v, l, Modifier.fillMaxWidth())
            // someone who lives here and hasn't been met: their introduction first, before a talk
            if (vm.villagers.waitsToMeet(vm.game.state, v.id)) {
                MeetButton(vm, v, Modifier.fillMaxWidth())
            } else if (greeting.said.isEmpty()) {
                Button(
                    onClick = { greeting.start(scope, vm, v) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = HeartPink, contentColor = Color.White),
                ) { Text("💬 ${bi("common.talkVerb")}", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
            } else {
                FreeTalkButton(vm, v, enabled = !greeting.speaking)
                TextButton(onClick = { greeting.start(scope, vm, v) }, enabled = !greeting.speaking, modifier = Modifier.fillMaxWidth()) {
                    Text("🔁 ${bi("villagerScreens.sayHelloAgain")}")
                }
            }
            Text(
                if (v.register == "vi") "🎩 ${bi("villagerScreens.saysVi", "short" to short, "g" to VillagerLogic.gender(v))}"
                else "🙂 ${bi("villagerScreens.saysTi", "short" to short, "g" to VillagerLogic.gender(v))}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "🗣️ Pogovor s tutorjem · Talk freely": the tutor plays the villager; a spinner while the scene is prepared. */
@Composable
private fun FreeTalkButton(vm: AppViewModel, v: Villager, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val loading = vm.villagers.opening == v.id
    val running = vm.talk.state?.takeIf { it.phase == TalkPhase.CHAT && it.scenario.id != VillagerLogic.SCENARIO_PREFIX + v.id }
    FilledTonalButton(
        onClick = { vm.talkWithVillager(v.id) },
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🗣️ ${bi("villagerScreens.talkFreely")}", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            if (running != null) Text(
                "${bi("villagerScreens.firstFinish")}: ${running.scenario.title}",
                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Their requests (up now, then those waiting) and the happenings with them today, each opening its flow. */
@Composable
private fun TodayRows(vm: AppViewModel, v: Villager, s: GameState?, today: Today) {
    for (q in today.quests) {
        Surface(
            onClick = { vm.startQuest(q.id) },
            color = XpGold.copy(alpha = 0.16f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tag("📋 ${bi("villagerScreens.request")}", MaterialTheme.colorScheme.tertiary)
                Text(q.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val sl = q.story.substringBefore(" · ").trim()
                val en = q.story.substringAfter(" · ", "").trim()
                Text(sl, style = MaterialTheme.typography.bodyMedium)
                if (en.isNotBlank()) Text(en, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                progressText(q)?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOf(formatRes(q.reward), "+${Bonds.QUEST} $HEART").filter { it.isNotBlank() }.joinToString("  "),
                        style = MaterialTheme.typography.labelLarge, color = AlpineGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    )
                    Text("${bi("villagerScreens.helpThem", "g" to VillagerLogic.gender(v))} ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (QuestQueue.hard(q, today.all)) PractiseHardButton { vm.practiseHard(q.id) }
    }
    WaitingRows(today.waiting) { vm.startQuest(it.id) }
    for (a in today.now) {
        Surface(
            onClick = { vm.openScene(a.scene.id, a.key) },
            color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.happening.marker, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${bi("villagerScreens.now")}: ${a.happening.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    s?.let { Text("${a.scene.emoji} ${TownMarkers.placeOf(a, it).where()} · +${Bonds.DIALOG} $HEART", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
    for ((t, a) in today.later) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.emoji(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(10.dp))
            Text("${t.label()}: ${a.happening.title}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * "🎁 Darila · Gifts" (companion/GAME.md, "The chest"): what they gave Jan, and the goods they like, with the ones in
 * the chest to give them (once a day). What they'll give at the next friendship level isn't said: it comes as a
 * surprise.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Gifts(vm: AppViewModel, v: Villager, s: GameState?, modifier: Modifier = Modifier) {
    val st = s ?: return
    val female = VillagerLogic.female(v)
    val today = LocalDate.now()
    Section("🎁 ${bi("villagerScreens.gifts")}", modifier) {
        st.chest.tools[v.id]?.let { t ->
            val item = si.lanisce.lani.game.Catalog.tools[v.id]?.name(t.tier)
            if (item != null) Text("🧰 ${bi("villagerScreens.chest")}: ${item.label}", style = MaterialTheme.typography.bodyMedium)
        }
        val liked = VillagerLogic.likedGoods(v.id)
        Text(
            "${bi("villagerScreens.wouldLove", "g" to if (female) "f" else "m")}: " + liked.joinToString(", ") { "${it.name.emoji} ${it.name.sl}" } +
                " ✨ ${bi("villagerScreens.andRareGoods")}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val have = si.lanisce.lani.game.Chest.likes(v.id).filter { (st.chest.goods[it.id] ?: 0) > 0 }
        if (have.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (g in have) {
                val blocked = si.lanisce.lani.game.Chest.giveBlocker(st, g.id, v.id, today)
                FilledTonalButton(onClick = { vm.villagers.offer(g.id, v.id) }, enabled = blocked == null, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("🎁 ${bi("villagerScreens.give")} ${g.name.emoji} ×${st.chest.goods[g.id]}")
                }
            }
        }
        // anything else in the chest: warm thanks, fewer hearts (the gift moment all the same)
        val other = st.chest.goods.filter { (id, n) -> n > 0 && have.none { it.id == id } && si.lanisce.lani.game.Chest.forThem(v.id, id) }
            .keys.sorted().mapNotNull { si.lanisce.lani.game.Catalog.goods[it] }
        if (other.isNotEmpty()) {
            Text(
                "${bi("villagerScreens.somethingElse")} (+${si.lanisce.lani.game.Catalog.PLAIN_GIFT_POINTS} $HEART)",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (g in other) OutlinedButton(onClick = { vm.villagers.offer(g.id, v.id) }, enabled = st.chest.given[v.id] != today.toString(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("${g.name.emoji} ×${st.chest.goods[g.id]}", modifier = Modifier.semantics { contentDescription = "${bi("villagerScreens.give")}: ${g.name.label}" })
                }
            }
        }
        if (st.chest.given[v.id] == today.toString()) Text(
            "✓ ${bi("villagerScreens.gaveToday", "g" to VillagerLogic.gender(v))}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What they remember of Jan, newest first, in their words: "Še vedno mislim na …". */
@Composable
private fun Memories(v: Villager, bond: Bond, modifier: Modifier = Modifier) {
    Section("💭 ${bi("villagerScreens.memories")}", modifier) {
        if (bond.memories.isEmpty()) {
            Text(
                bi("villagerScreens.noMemoriesYet", "g" to VillagerLogic.gender(v)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Section
        }
        Text(bi("villagerScreens.iStillThink"), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val shown = bond.memories.asReversed()
        shown.forEachIndexed { i, m -> MemoryRow(m, last = i == shown.lastIndex) }
    }
}

@Composable
private fun MemoryRow(m: Memory, last: Boolean) {
    val kind = when (m.kind) {
        "quest" -> "📋"
        "talk" -> "🗣️"
        "training" -> "🏋️"
        "gift" -> "🎁"
        "ispy" -> "🔍"
        else -> "💬"
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        // the timeline: a dot per memory, joined by a line
        Column(Modifier.width(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(HeartPink))
            if (!last) Box(Modifier.width(2.dp).height(52.dp).background(HeartPink.copy(alpha = 0.3f)))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(bottom = 10.dp)) {
            Text("$kind ${VillagerLogic.shortDate(m.on)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(m.sl, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (m.en.isNotBlank()) Text(m.en, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Their story and what they like. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun About(v: Villager, modifier: Modifier = Modifier) {
    if (v.story.isBlank() && v.likes.isEmpty()) return
    Section("📖 ${bi("villagerScreens.story")}", modifier) {
        if (v.story.isNotBlank()) Text(v.story, style = MaterialTheme.typography.bodyLarge)
        if (v.likes.isNotEmpty()) {
            Text("❤️ ${VillagerLogic.likes(v)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (l in v.likes) Surface(color = heartContainer(), shape = RoundedCornerShape(50)) {
                    Text(l, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        content()
    }
}

/** Whether villager [id] tells the village's stories by the fire (the storyteller: his card and page open the notebook). */
private fun tellsStories(vm: AppViewModel, id: String): Boolean =
    si.lanisce.lani.game.scene.StoryBooks.stories(vm.scenes.all).any { it.teller == id }

// --- the card ---------------------------------------------------------------------------------------

/**
 * A villager, compact: the portrait, name, friendship, what they need now, "Pogovori se" (their greeting,
 * then the talk with the tutor) and "Več · More ›" (their page). The village map shows it when someone is
 * tapped; the register offers it too.
 */
@Composable
fun VillagerCard(vm: AppViewModel, id: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val v = vm.villagers.byId(id) ?: return
    val s = vm.game.state
    val bond = vm.villagers.bond(s, id)
    val level = Bonds.level(bond.points)
    val greeting = rememberGreeting(id)
    val pose = villagerPose(id, greeting)
    val scope = rememberCoroutineScope()
    val today = remember(v, s) { todayOf(vm, v, s) }
    // out at night on one of their rituals (Luka checking the sheep): what they're doing, "🏮 Luka pogleda ovce v staji"
    val ritual = remember(v, s) { s?.let { si.lanisce.lani.game.villagers.Routine.now(v, it, java.time.LocalDateTime.now()).ritual?.title?.takeIf(String::isNotBlank) } }
    BackHandler(onBack = onDismiss)
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = 560.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VillagerPortrait(v, 72.dp, pose = pose)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${v.emoji} ${v.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (v.role.isNotBlank()) Text(v.role, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    FriendshipBar(bond.points, VillagerLogic.female(v), compact = true)
                    // what they'd love from the chest (what they'll give at the next level stays a surprise)
                    if (vm.villagers.livesHere(s, id)) Text(
                        "❤️ " + VillagerLogic.likedGoods(id).joinToString(" ") { it.name.emoji },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    )
                }
                IconButton(onClick = onDismiss) { EmojiLabel("✕", Labels.CLOSE, style = MaterialTheme.typography.titleMedium) }
            }
            when {
                today.quests.isNotEmpty() -> CardRequests(vm, today, onDismiss)
                today.now.isNotEmpty() -> {
                    val a = today.now.first()
                    Surface(
                        onClick = { onDismiss(); vm.openScene(a.scene.id, a.key) },
                        color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${a.happening.marker} ${a.happening.title}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
                ritual != null && greeting.said.isEmpty() -> Text("🏮 $ritual", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                greeting.said.isEmpty() -> VillagerLogic.idle(v, level, LocalDate.now())?.let { SpeechBubble(vm, v, it, Modifier.fillMaxWidth()) }
            }
            // nothing up, only requests waiting in "Later" (one never started, asked again another day): still one tap away
            if (today.quests.isEmpty() && today.waiting.isNotEmpty()) CardRequests(vm, today, onDismiss)
            for (l in greeting.said) SpeechBubble(vm, v, l, Modifier.fillMaxWidth())
            // the storyteller: the notebook of the stories he told (over the card; closed, the card is there again)
            if (tellsStories(vm, id)) si.lanisce.lani.ui.notebook.NotebookRow(si.lanisce.lani.ui.notebook.rememberNotebook(vm)) { vm.openNotebook() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // someone who lives here and hasn't been met: their introduction first (companion/VILLAGERS.md "Arrivals")
                if (vm.villagers.waitsToMeet(s, id)) {
                    MeetButton(vm, v, Modifier.weight(1f), onMeet = onDismiss)
                } else if (greeting.said.isEmpty()) {
                    FilledTonalButton(
                        onClick = { greeting.start(scope, vm, v) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium,
                    ) { Text("💬 ${bi("common.talkVerb")}", textAlign = TextAlign.Center) }
                } else {
                    FreeTalkButton(vm, v, modifier = Modifier.weight(1f), enabled = !greeting.speaking)
                }
                OutlinedButton(onClick = { onDismiss(); vm.openVillager(id) }, modifier = Modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) {
                    Text("${bi("villagerScreens.more")} ›")
                }
            }
        }
    }
}

/**
 * The card's requests (companion/GAME.md "Quests"): "🙋 Prosi te za pomoč · Asks for your help", one row per request up
 * (its title, how close Jan is or what it pays; "🎯 Vadi, kar ti ne gre" under one tried too often), then the ones waiting,
 * dim, each with why. Every row opens its request.
 */
@Composable
private fun CardRequests(vm: AppViewModel, today: Today, onDismiss: () -> Unit) {
    Surface(color = XpGold.copy(alpha = 0.16f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (today.quests.isNotEmpty()) Text(
                "🙋 ${bi("villagerScreens.asksForHelp", "QUEST" to Bonds.QUEST, "HEART" to HEART)}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (q in today.quests) {
                Surface(
                    onClick = { onDismiss(); vm.startQuest(q.id) },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📋 ${q.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                progressText(q) ?: formatRes(q.reward),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                if (QuestQueue.hard(q, today.all)) PractiseHardButton { onDismiss(); vm.practiseHard(q.id) }
            }
            WaitingRows(today.waiting) { onDismiss(); vm.startQuest(it.id) }
        }
    }
}

/** "📜 Kasneje · Later": the requests waiting, dim, each with why ("⏸️ Najprej · First: …"); a tap opens one all the same. */
@Composable
private fun WaitingRows(waiting: List<QuestQueue.Waiting>, onOpen: (Quest) -> Unit) {
    if (waiting.isEmpty()) return
    Text(laterTitle(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (w in waiting) {
        Surface(onClick = { onOpen(w.quest) }, color = Color.Transparent, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${w.quest.emoji} ${w.quest.title}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(whyText(w.why), progressText(w.quest)).joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "🎯 Vadi, kar ti ne gre · Practise what's hard": the one-tap way on for a request tried too often below the mark. */
@Composable
private fun PractiseHardButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = MaterialTheme.shapes.small) {
        Text(practiseHardLabel(), textAlign = TextAlign.Center)
    }
}
