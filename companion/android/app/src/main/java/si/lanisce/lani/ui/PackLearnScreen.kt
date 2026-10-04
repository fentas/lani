package si.lanisce.lani.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.scene.StickerSpot
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.game.RewardSummary
import si.lanisce.lani.ui.stage.Cast
import si.lanisce.lani.ui.stage.IntroSheet
import si.lanisce.lani.ui.stage.Intros
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.StageFinale
import si.lanisce.lani.ui.stage.StageState
import si.lanisce.lani.ui.stage.rememberStage
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.words.StickerOr
import si.lanisce.lani.ui.words.lookUpIn

/**
 * Learn a few words of a pack: the pack's giver asks (the intro), then meet each word, practise them all
 * with the giver on the stage, and they're saved for review. With [scene] they are a village scene's
 * words, and leaving leads back to that scene.
 */
@Composable
fun PackLearnScreen(vm: AppViewModel, pack: Pack, words: List<PackWord>, fromVillage: Boolean, scene: String? = null) {
    val leave = { if (scene != null) vm.openScene(scene) else vm.openPacks(fromVillage) }
    var started by remember { mutableLongStateOf(ScreenClock.app.now()) }
    val tasks = remember(pack.id, words) { PackSession.plan(pack, words, vm.content.dashboard?.pool.orEmpty(), vm.speaker.canSay) }
    // -1: the intro (skipped when these are more words right after a session of the same pack).
    var step by remember { mutableIntStateOf(if (vm.again) 0 else -1) }
    var saved by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }
    val stage = rememberStage(vm, remember(pack.id) { Run.Pack(pack.giver?.name, pack.giver?.emoji) })
    val finish: (List<Outcome>) -> Unit = { outs ->
        stage.finish(outs.map { it.verdict }, tasks.size)
        val q = PackSession.qualities(pack.id, words, tasks, outs.map { it.verdict }, outs.map { it.hints })
        saved = q
        vm.finishPack(pack, q, tasks.map { it.exercise }, outs.map { it.paid }, minutesSince(started))
    }
    val done = saved
    when {
        done != null -> PackFinish(vm, pack, words, done, fromVillage, scene, leave, stage)
        step < 0 -> {
            val info = remember(pack.id, words) {
                Intros.pack(pack, words, tasks.map { it.exercise }, stage.who, Cast.trainingLeft(vm.game.state, stage.who.id, LocalDate.now()))
            }
            IntroSheet(info, vm.speaker, lookUp = { l, e -> lookUpIn(vm, l, e, Words.VILLAGER) }, onStart = { started = ScreenClock.app.now(); step = 0 }, onLater = leave)
        }
        step < words.size -> Intro(vm, pack, words, step, voice = stage.voice, onStep = { step = it }, onLeave = leave)
        // ✕ before the first answer still keeps the words: they were all introduced.
        else -> ExerciseRun(
            vm, bi("packLearnScreen.practise", "packEmoji" to pack.emoji), tasks.map { it.exercise }, moduleId = null,
            onExit = { finish(emptyList()) }, stage = stage,
            source = { i -> tasks.getOrNull(i)?.let { ExerciseSource.pack(pack.id, it.card.id, it.variant) } },
            onComplete = finish,
        )
    }
}

@Composable
private fun Intro(vm: AppViewModel, pack: Pack, words: List<PackWord>, step: Int, voice: String, onStep: (Int) -> Unit, onLeave: () -> Unit) {
    BackHandler { onStep(step - 1) } // from the first word: back to the intro
    val last = step == words.lastIndex
    CompositionLocalProvider(LocalSpeaker provides vm.speaker) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLeave) { EmojiLabel("✕", Labels.CLOSE) }
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
                "${pack.emoji} ${pack.title} · ${step + 1}/${words.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally { it * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir } + fadeOut())
                    },
                    label = "word",
                ) { i -> WordCard(vm.speaker, words[i], pack.emoji, voice, vm.scenes.stickers.picture(pack.id, words[i].id, words[i].emoji)) }
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
 * One new word: picture (its [sticker] when a scene draws it and no emoji shows it), the word in its language (spoken on
 * arrival, in the [voice] of the one teaching it), meaning, grammar, example, tip. Also a building's new words
 * (ui/game/UpgradeWords.kt).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordCard(speaker: Speaker, w: PackWord, fallbackEmoji: String, voice: String, sticker: StickerSpot?) {
    LaunchedEffect(w.id) {
        delay(350) // let the card slide in first
        speaker.say(w.word, voiceName = voice)
    }
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Box(
            Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(SloBlue, SloBlueDeep))),
            contentAlignment = Alignment.Center,
        ) {
            StickerOr(sticker, 124.dp) { Text(w.emoji ?: fallbackEmoji, fontSize = 84.sp) }
        }
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.word, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
                if (speaker.canSay) {
                    Spacer(Modifier.width(8.dp))
                    SpeakButton(speaker, w.word, voiceName = voice)
                    FilledTonalIconButton(onClick = { speaker.say(w.word, slow = true, voiceName = voice) }) { EmojiLabel("🐢", Labels.SLOW) }
                }
            }
            Text(w.meaning, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            val gender = PackSession.genderTag(w.gender)
            if (gender != null || w.plural != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    gender?.let { Tag(it, genderColor(w.gender)) }
                    w.plural?.let { Tag(bi("sceneBubbles.pl", "it" to it), MaterialTheme.colorScheme.tertiary) }
                }
            }
            w.example?.let { example ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("„$example“", style = MaterialTheme.typography.titleMedium)
                            w.exampleMeaning?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        SpeakButton(speaker, example)
                    }
                }
            }
            w.noteText?.let {
                Row(Modifier.fillMaxWidth()) {
                    Text("💡", Modifier.padding(end = 8.dp))
                    Text(inlineMarkdown(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

/** Blue for masculine, red for feminine, green for neuter: the flag, plus the Alps. */
private fun genderColor(g: String?): Color = when (g) {
    "m" -> SloBlue
    "f" -> TriglavRed
    else -> AlpineGreen
}

@Composable
private fun PackFinish(
    vm: AppViewModel, pack: Pack, words: List<PackWord>, qualities: List<Pair<String, Int>>, fromVillage: Boolean,
    scene: String?, leave: () -> Unit, stage: StageState,
) {
    BackHandler(onBack = leave)
    val q = qualities.toMap()
    val left = scene?.let { id -> vm.scenes.byId(id)?.let { vm.scenes.toLearn(it)?.second?.size } ?: 0 }
        ?: (pack.words.count { it.id !in pack.learned } - words.size)
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            if (stage.finale != null) StageFinale(stage) else Text(pack.emoji, style = MaterialTheme.typography.displayLarge)
            Text("+${words.size} ${bi("packLearnScreen.words", "n" to words.size)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(pack.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    for (w in words) LearnedRow(vm.speaker, w, q[w.id] ?: 3, vm.scenes.stickers.picture(pack.id, w.id, w.emoji))
                }
            }
            Text(
                bi("packLearnScreen.theyComeBackReview"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RewardSummary(vm.game.lastReward, onVillage = vm::openVillage)
            if (left > 0) {
                OutlinedButton(
                    onClick = { if (scene != null) vm.learnScene(scene, again = true) else vm.startPack(pack.id, fromVillage, again = true) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("${bi("packLearnScreen.moreWords", "n" to minOf(left, 6))} ›")
                }
            }
            BigButton(if (scene != null) bi("common.backScene") else bi("common.continue"), onClick = leave)
        }
        Confetti(key = pack.id + words.joinToString { it.id })
    }
}

@Composable
private fun LearnedRow(speaker: Speaker, w: PackWord, quality: Int, sticker: StickerSpot?) {
    val (mark, color) = when {
        quality >= 4 -> "✓" to AlpineGreen
        quality == 3 -> "~" to XpGold
        else -> "↻" to FlameOrange
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
            StickerOr(sticker, 32.dp) { Text(w.emoji ?: "•", style = MaterialTheme.typography.titleLarge) }
        }
        Column(Modifier.weight(1f)) {
            Text(w.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(w.meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpeakButton(speaker, w.word)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(mark, color = color, fontWeight = FontWeight.Black)
        }
    }
}
