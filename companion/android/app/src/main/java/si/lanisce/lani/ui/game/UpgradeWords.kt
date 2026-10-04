package si.lanisce.lani.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.game.BuildingWords
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.NeedWord
import si.lanisce.lani.game.UpgradeRun
import si.lanisce.lani.game.UpgradeWords
import si.lanisce.lani.game.WordState
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.ChatButton
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.ExerciseRun
import si.lanisce.lani.ui.Finish
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.LocalSpeaker
import si.lanisce.lani.ui.Outcome
import si.lanisce.lani.ui.WordCard
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.StageState
import si.lanisce.lani.ui.stage.rememberStage
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.XpGold
import kotlin.math.min

/** How many of the missing words a building's card shows; the rest as "+n". */
private const val SHOWN = 8

/**
 * "🧱 Za stopnjo 2: besede · For level 2: words  5 / 8" on a building's card (companion/GAME.md, "Upgrades"): how many of
 * the building's words its upgrade asks for are known, where they are from, the ones still missing (🔩 a rusty one), and
 * "🎯 Vadi jih zdaj · Practise them now" ([onPractise]): a short run of exactly those, after which the upgrade is one tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpgradeWordsCard(w: UpgradeWords, onPractise: () -> Unit) {
    val done = w.done
    val color = if (done) AlpineGreen else XpGold
    Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (done) "✅" else "🧱", Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium)
                Text(
                    bi("upgradeWords.title", "level" to w.toLevel), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                Text(
                    "${min(w.have, w.need)} / ${w.need}", style = MaterialTheme.typography.labelLarge,
                    color = if (done) AlpineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FillBar(if (w.need <= 0) 1f else (w.have.toFloat() / w.need).coerceIn(0f, 1f), color, Modifier.height(6.dp))
            w.from?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!done) {
                val missing = w.missing
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in missing.take(SHOWN)) WordChip(m)
                    if (missing.size > SHOWN) {
                        Text("+${missing.size - SHOWN}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
                if (w.rustBlocks) Text(
                    "🔩 ${bi("upgradeWords.noRust", "level" to BuildingWords.NO_RUST_FROM)}",
                    style = MaterialTheme.typography.labelSmall, color = FlameOrange,
                )
                BigButton("🎯 ${bi("upgradeWords.practise")}", onClick = onPractise, color = AlpineGreen)
            }
        }
    }
}

/** A missing word: its picture and the word; a rusty one with 🔩. */
@Composable
private fun WordChip(m: NeedWord) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(50)) {
        Text(
            listOfNotNull(if (m.state == WordState.RUSTY) "🔩" else m.emoji, m.word).joinToString(" "),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * "🎯 Vadi jih zdaj · Practise them now": the words building [buildingId]'s upgrade to [toLevel] still asks for, in one
 * short run ([run], see [BuildingWords.plan]): the new ones met one by one, then every word asked (the new ones twice, a
 * pack's two rounds; the met and rusty ones once, a review). What was answered is saved and counts at once
 * ([AppViewModel.finishUpgradeWords]); the finish offers the upgrade itself when it can be done now.
 */
@Composable
fun UpgradeWordsScreen(vm: AppViewModel, buildingId: String, toLevel: Int, run: UpgradeRun) {
    val started = remember { ScreenClock.app.now() }
    val stage = rememberStage(vm, Run.Review, greet = run.learn.isEmpty())
    val tasks = remember(run) {
        BuildingWords.plan(run, vm.content.dashboard?.pool.orEmpty(), vm.speaker.canSay, canRecognize = vm.recognizer.ready)
    }
    val fresh = remember(run) { run.learn.associateBy { PackSession.itemId(it.pack, it.id) } }
    // the new words, met one by one first (none: straight to the questions)
    var step by remember { mutableIntStateOf(0) }
    var outs by remember { mutableStateOf<List<Outcome>?>(null) }
    val title = "🧱 ${bi("upgradeWords.title", "level" to toLevel)}"
    val done = outs
    when {
        done != null -> UpgradeWordsFinish(vm, buildingId, done, stage)
        step < run.learn.size -> NewWord(vm, run.learn, step, stage.voice, title, onStep = { step = it })
        else -> ExerciseRun(
            vm, title, tasks.map { it.exercise }, moduleId = null, onExit = vm::leaveRun, stage = stage,
            source = { i ->
                tasks.getOrNull(i)?.let { t ->
                    fresh[t.card.id]?.let { ExerciseSource.pack(it.pack, t.card.id, t.variant) } ?: ExerciseSource.review(t.card.id, t.variant)
                }
            },
        ) { o ->
            outs = o
            val results = BuildingWords.results(run, tasks, o.map { it.verdict }, o.map { it.hints })
            vm.finishUpgradeWords(results, run, tasks.take(o.size).map { it.exercise }, o.map { it.paid }, minutesSince(started))
        }
    }
}

/** A new word of the run met, as a pack's: its card (picture, word said, meaning, example); back goes to the one before. */
@Composable
private fun NewWord(vm: AppViewModel, words: List<NeedWord>, step: Int, voice: String, title: String, onStep: (Int) -> Unit) {
    BackHandler { if (step == 0) vm.leaveRun() else onStep(step - 1) }
    val w = words[step]
    val last = step == words.lastIndex
    CompositionLocalProvider(LocalSpeaker provides vm.speaker) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::leaveRun) { EmojiLabel("✕", Labels.CLOSE) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    words.indices.forEach { i ->
                        Box(
                            Modifier.weight(1f).height(8.dp).clip(CircleShape)
                                .background(if (i <= step) XpGold else MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
                ChatButton(vm)
            }
            Text(
                "$title · ${step + 1}/${words.size}", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                val learn = w.learn ?: return@Column
                androidx.compose.runtime.key(w.pack, w.id) {
                    WordCard(vm.speaker, learn, w.emoji ?: "🧱", voice, vm.scenes.stickers.picture(w.pack, w.id, learn.emoji))
                }
            }
            BigButton(
                if (last) "▶  ${bi("packLearnScreen.letsPractise")}" else bi("common.next"),
                onClick = { onStep(step + 1) },
                color = if (last) AlpineGreen else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * The run's end: how the answers went, how the building's words stand now, and the way on: "⬆️ Nadgradi zdaj · Upgrade
 * now" when the upgrade can be done (the words known, the resources there), "🎯 Vadi jih zdaj" again while words are
 * missing; "Naprej · Continue" back to the village, the building's card open.
 */
@Composable
private fun UpgradeWordsFinish(vm: AppViewModel, buildingId: String, outs: List<Outcome>, stage: StageState) {
    val s = vm.game.state
    // after the save: the answers count at once (the dashboard's words), so this is the card's new count
    val words = s?.let { vm.upgradeWords(it, buildingId) }
    val up = s?.let { GameEngine.upgradeOption(it, buildingId, words) }
    val correct = outs.count { it.verdict == Verdict.CORRECT }
    val almost = outs.count { it.verdict == Verdict.ALMOST }
    Finish(
        title = if (words?.done != false) "${inTarget("upgradeWords.knowledgeBuilds")} 🎉" else "${inTarget("playerScreen.goodWork")} 💪",
        lines = listOfNotNull(
            "✅ $correct   🟡 $almost   ❌ ${outs.size - correct - almost}",
            words?.let { w -> "${if (w.done) "✅" else "🧱"} ${bi("upgradeWords.title", "level" to w.toLevel)}: ${min(w.have, w.need)}/${w.need}" },
        ),
        onDone = vm::leaveRun,
        stage = stage,
    ) {
        RewardSummary(vm.game.lastReward, onVillage = vm::leaveRun)
        Spacer(Modifier.height(12.dp))
        when {
            up?.available == true -> BigButton(
                "⬆️ ${bi("upgradeWords.upgradeNow")}", onClick = { vm.upgradeNow(buildingId) }, color = AlpineGreen,
            )
            words != null && !words.done -> BigButton(
                "🎯 ${bi("upgradeWords.practise")} (${words.missing.size})", onClick = { vm.practiseUpgrade(buildingId) },
            )
        }
    }
}
