package si.lanisce.lani.ui.talk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.Link
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.data.TalkLine
import si.lanisce.lani.data.TalkPhase
import si.lanisce.lani.data.TalkState
import si.lanisce.lani.data.Words
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.ChatBubble
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.game.GiftLogic
import si.lanisce.lani.ui.game.GiftReveals
import si.lanisce.lani.ui.game.formatRes
import si.lanisce.lani.ui.game.giftWords
import si.lanisce.lani.ui.stage.Cast
import si.lanisce.lani.ui.stage.IntroSheet
import si.lanisce.lani.ui.stage.Intros
import si.lanisce.lani.ui.stage.StageCast
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.villagers.FriendChip
import si.lanisce.lani.ui.villagers.VillagerLogic
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.lookUpIn
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget

/** "Pogovor · Talk": list → intro → conversation → debrief, depending on [TalkModel]'s state. */
@Composable
fun TalkScreen(vm: AppViewModel) {
    val t = vm.talk
    val st = t.state
    val picked = t.picked
    when {
        st != null && st.phase != TalkPhase.CHAT -> TalkEnd(vm, st)
        st != null -> Conversation(vm, st)
        picked != null -> Intro(vm, picked)
        else -> ScenarioList(vm)
    }
}

/** Home: the way into role-play. */
@Composable
fun TalkCard(vm: AppViewModel, modifier: Modifier = Modifier) {
    val running = vm.talk.state?.takeIf { it.phase == TalkPhase.CHAT }
    Card(
        onClick = vm::openTalk,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(running?.scenario?.emoji ?: "🗣️", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("🗣️ ${bi("common.talk")}", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (running != null) bi("talkScreen.continue", "scenarioTitle" to running.scenario.title)
                    else bi("talkScreen.talkWayThroughReal"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String?, back: String, onBack: () -> Unit) = GradientHeader(title, subtitle, back, onBack, bottom = 20.dp)

// --- list ---------------------------------------------------------------------------------

@Composable
private fun ScenarioList(vm: AppViewModel) {
    val t = vm.talk
    BackHandler(onBack = vm::leaveTalk)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Header(
                "🗣️ ${bi("common.talk")}",
                bi("talkScreen.tutorPlaysOtherPerson"),
                vm.talkBackLabel ?: "← ${bi("common.home")}",
                vm::leaveTalk,
            )
        }
        when {
            t.scenarios.isEmpty() && t.loading -> item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
            t.scenarios.isEmpty() -> item {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        t.loadError?.let { "⚠️ $it" } ?: bi("talkScreen.noScenesYet"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    BigButton("↻ ${bi("common.retry")}", onClick = t::load)
                }
            }
            else -> items(t.scenarios, key = { it.id }) { s ->
                ScenarioCard(s, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { t.pick(s) }
            }
        }
        item {
            TextButton(onClick = vm.chat::open, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("💬 ${bi("talkScreen.wantAnotherSceneAsk")}")
            }
        }
    }
}

@Composable
private fun ScenarioCard(s: Scenario, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(s.emoji, style = MaterialTheme.typography.headlineMedium) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(s.level)
                    if (s.source == "tutor") Tag("✨ ${inTarget("talkScreen.fromTutor")}")
                }
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("🎭 ${s.character}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(6.dp)) {
        Text(text, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

// --- intro --------------------------------------------------------------------------------

/**
 * The role-play's intro: the character (a villager of the cast, or the scenario's person) opens with the
 * scenario's first line, then the setting, the goals, useful phrases, and Start.
 */
@Composable
private fun Intro(vm: AppViewModel, s: Scenario) {
    val t = vm.talk
    val info = remember(s) {
        val who = Cast.talker(s, StageCast.of(vm.villagers.all, vm.game.state), vm.game.state)
        Intros.talk(s, who)
    }
    val later = { if (vm.talkOpenedFrom) vm.leaveTalk() else t.pick(null) }
    IntroSheet(info, vm.speaker, lookUp = { l, e -> lookUpIn(vm, l, e, Words.VILLAGER) }, onStart = { t.start() }, onLater = later) {
        if (s.vocabularyHints.isNotEmpty()) Section("💬 ${bi("talkScreen.usefulPhrases")}") {
            s.vocabularyHints.forEach { h ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(h.sl, style = MaterialTheme.typography.titleMedium)
                        Text(h.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SpeakButton(vm.speaker, h.sl)
                }
            }
        }
        if (vm.link == Link.OFFLINE) Text(
            "📶 ${bi("talkScreen.tutorOfflineCanStart")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

// --- conversation -------------------------------------------------------------------------

@Composable
private fun Conversation(vm: AppViewModel, st: TalkState) {
    val t = vm.talk
    var confirmLeave by remember { mutableStateOf(false) }
    var showGoals by rememberSaveable { mutableStateOf(false) }
    val leave = { if (st.turn == 0) t.abandon() else confirmLeave = true }
    BackHandler(onBack = leave)

    // The character speaks each new line aloud.
    val last = st.lines.last()
    LaunchedEffect(st.conversationId, st.lines.size) {
        if (!last.fromMe) vm.speaker.say(last.sl, voiceName = st.scenario.characterVoice, fallback = st.scenario.characterGender, person = st.scenario.villager)
    }
    val list = rememberLazyListState()
    val count = st.lines.size + (if (st.waiting) 1 else 0) + (if (st.sceneOver) 1 else 0)
    LaunchedEffect(count, st.lines.lastOrNull()?.correction) { list.animateScrollToItem((count - 1).coerceAtLeast(0)) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Surface(color = SloBlueDeep) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = leave) { EmojiLabel("←", Labels.BACK, color = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("${st.scenario.emoji} ${st.scenario.character}", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(st.scenario.title, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Surface(
                        onClick = { showGoals = !showGoals },
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "${bi("talkScreen.goals")}: ${st.goalsDone.size} / ${st.scenario.goals.size}"
                        },
                    ) {
                        Text(
                            "🎯 ${st.goalsDone.size}/${st.scenario.goals.size}",
                            color = XpGold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).clearAndSetSemantics {},
                        )
                    }
                    TextButton(onClick = t::finish, enabled = st.turn > 0) { Text(inTarget("talkScreen.end"), color = Color.White) }
                }
                if (showGoals) Goals(st, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), onDark = true)
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(st.lines, key = { it.id }) { l -> if (l.fromMe) MyLine(vm, l) else CharacterLine(vm, st.scenario, l) }
            if (st.waiting) item(key = "typing") { Typing(vm, st) }
            if (st.sceneOver && !st.waiting) item(key = "over") {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🏁 ${bi("talkScreen.sceneOver")}", style = MaterialTheme.typography.titleMedium)
                        BigButton(bi("talkScreen.finish"), onClick = t::finish, color = AlpineGreen)
                    }
                }
            }
        }
        t.sendError?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️ ${bi("talkScreen.notSent")}: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2)
                    TextButton(onClick = t::retry) { Text("↻ ${bi("talkScreen.retry")}") }
                }
            }
        }
        HintCard(vm, st)
        if (!st.sceneOver) Box(Modifier.navigationBarsPadding()) {
            TalkInput(
                vm,
                enabled = !st.waiting,
                hintEnabled = !st.waiting && !st.hintLoading,
                onSay = t::say,
                onHint = t::hint,
            )
        } else Spacer(Modifier.navigationBarsPadding())
    }

    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(bi("talkScreen.endConversation")) },
        text = { Text(bi("talkScreen.tutorWillSendShort")) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; t.finish() }) { Text(bi("talkScreen.end")) } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmLeave = false; vm.leaveTalk() }) { Text(bi("common.later")) }
                TextButton(onClick = { confirmLeave = false }) { Text(bi("todayCard.continue")) }
            }
        },
    )
}

@Composable
private fun Goals(st: TalkState, modifier: Modifier = Modifier, onDark: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        st.scenario.goals.forEachIndexed { i, g ->
            val done = i in st.goalsDone
            Text(
                "${if (done) "✅" else "☐"}  $g",
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    onDark && done -> XpGold
                    onDark -> Color.White
                    done -> AlpineGreen
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun CharacterLine(vm: AppViewModel, s: Scenario, l: TalkLine) {
    var translated by rememberSaveable(l.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 2.dp)) {
                Text("${s.emoji} ${s.character}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                WordText(
                    l.sl,
                    lookUpIn(vm, l.sl, l.en, Words.ROLEPLAY),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp, top = 2.dp),
                )
                if (translated && l.en != null) Text(
                    l.en,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp, top = 4.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.speaker.canSay) {
                        TextButton(onClick = { vm.speaker.say(l.sl, voiceName = s.characterVoice, fallback = s.characterGender, person = s.villager) }) { EmojiLabel("🔊", Labels.LISTEN) }
                        TextButton(onClick = { vm.speaker.say(l.sl, slow = true, voiceName = s.characterVoice, fallback = s.characterGender, person = s.villager) }) { EmojiLabel("🐢", Labels.SLOW) }
                    }
                    if (l.en != null) TextButton(onClick = { translated = !translated }) {
                        Text(if (translated) bi("talkScreen.hide") else bi("talkScreen.showTranslation"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MyLine(vm: AppViewModel, l: TalkLine) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            WordText(
                (if (l.typed) "⌨️ " else "🎤 ") + l.sl,
                lookUpIn(vm, l.sl, null, Words.ROLEPLAY),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        l.correction?.let {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(top = 4.dp).widthIn(max = 320.dp),
            ) {
                Row(Modifier.padding(10.dp)) {
                    Text("✏️", modifier = Modifier.padding(end = 8.dp))
                    Markdown(it)
                }
            }
        }
    }
}

/** The character is "typing"; the tutor can take 10-30 s, so after a while say so and offer a resend. */
@Composable
private fun Typing(vm: AppViewModel, st: TalkState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.waitingSince) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val waited = ((now - st.waitingSince) / 1000).coerceAtLeast(0)
    val dots = ".".repeat((waited % 3 + 1).toInt())
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)) {
            Text("${st.scenario.emoji} $dots", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        when {
            vm.link == Link.OFFLINE -> Text("📶 ${bi("talkScreen.offlineAnswerComesWhen")}", style = MaterialTheme.typography.labelSmall)
            waited >= 60 -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏳ ${bi("talkScreen.tutorSeemsBusy")}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = vm.talk::retry) { Text("↻ ${bi("talkScreen.resend")}") }
            }
            waited >= 12 -> Text(bi("talkScreen.thinkingCanTakeHalf", "character" to st.scenario.character), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HintCard(vm: AppViewModel, st: TalkState) {
    val h = st.hint
    if (h == null && !st.hintLoading) return
    Surface(color = XpGold.copy(alpha = 0.18f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💡", modifier = Modifier.padding(end = 10.dp))
            if (h == null) {
                Text(bi("talkScreen.findingHint"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Column(Modifier.weight(1f)) {
                    Text(bi("talkScreen.couldSay"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(h.sl, style = MaterialTheme.typography.titleMedium)
                    h.en?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic) }
                }
                SpeakButton(vm.speaker, h.sl)
            }
        }
    }
}

// --- end ------------------------------------------------------------------------------------

@Composable
private fun TalkEnd(vm: AppViewModel, st: TalkState) {
    val t = vm.talk
    val back = { t.abandon(); t.pick(null) }
    val leave = { t.abandon(); t.pick(null); vm.leaveTalk() }
    // A talk with a villager goes back to them; other scenes to the list of scenes.
    val withVillager = VillagerLogic.villagerOfScenario(st.scenario.id) != null && vm.talkBackLabel != null
    BackHandler(onBack = if (withVillager) leave else back)
    // The question under the debrief, kept while its row scrolls out of sight.
    val ask = rememberTalkVoice(vm, remember { TalkDraft() })
    // A new message under the debrief (or the tutor typing) scrolls to it: items are the header, the card, the thread.
    val list = rememberLazyListState()
    LaunchedEffect(t.thread.size, t.threadWaiting) {
        if (t.thread.isNotEmpty()) list.animateScrollToItem(1 + t.thread.size + (if (t.threadWaiting) 1 else 0))
    }
    LazyColumn(Modifier.fillMaxSize().imePadding(), state = list, contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Header(
                "🎉 ${inTarget("playerScreen.bravo")}",
                "${st.scenario.emoji} ${st.scenario.title}",
                if (withVillager) vm.talkBackLabel!! else "← ${bi("talkScreen.scenes")}",
                if (withVillager) leave else back,
            )
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("🗣️", "${st.turn}", bi("talkScreen.lines"), Modifier.weight(1f))
                    Stat("🎯", "${st.goalsDone.size}/${st.scenario.goals.size}", bi("talkScreen.goalsLower"), Modifier.weight(1f))
                    Stat("✏️", "${st.corrections}", bi("talkScreen.fixes"), Modifier.weight(1f))
                }
                t.earned?.earned?.takeIf { it.isNotEmpty() }?.let {
                    Surface(color = XpGold.copy(alpha = 0.18f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "🏡 ${bi("common.village")}: ${formatRes(it)}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(14.dp).clickable(onClick = vm::village),
                        )
                    }
                }
                t.friend?.let { FriendChip(it, Modifier.fillMaxWidth()) }
                // a friendship's gift (the level it reached): a present to open
                GiftReveals(remember(t.friend) { GiftLogic.of(t.friend) }, giftWords(vm), startDelayMs = 400)
                Goals(st)
                HorizontalDivider()
                Text("🧑‍🏫 ${bi("talkScreen.debrief")}", style = MaterialTheme.typography.titleMedium)
                val debrief = st.debrief
                if (debrief == null) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(bi("talkScreen.tutorWritingDebrief"), style = MaterialTheme.typography.bodyMedium)
                } else Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Markdown(debrief, Modifier.padding(16.dp))
                }
            }
        }
        // Under the debrief: a small thread with the tutor about this role-play, and the question field.
        if (st.debrief != null) {
            items(t.thread, key = { it.id }) { m ->
                Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) { ChatBubble(m.text, m.fromMe) }
            }
            if (t.threadWaiting) item(key = "typing") { Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) { ChatBubble("…", fromMe = false) } }
            item(key = "ask") { DebriefAsk(vm, st, ask, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                t.sendError?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️ $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = t::retry) { Text("↻ ${bi("talkScreen.retry")}") }
                    }
                }
                BigButton("🔁 ${bi("talkScreen.again")}", onClick = t::again, color = AlpineGreen)
                BigButton("🗣️ ${bi("talkScreen.anotherScene")}", onClick = back)
                TextButton(onClick = leave, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            vm.talkFromScene -> "‹ ${bi("common.backScene")}"
                            vm.talkBackLabel != null -> vm.talkBackLabel!!
                            else -> "🏠 ${bi("common.home")}"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$emoji $value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
