package si.lanisce.lani.ui.scene

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ISpyOffer
import si.lanisce.lani.app.ISpyPlay
import si.lanisce.lani.app.SentenceQuery
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.game.EmojiBadge
import si.lanisce.lani.ui.game.RewardChips
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.villagers.FriendChip

/**
 * «Vidim, vidim» offered on the words panel (companion/SCENES.md, "I spy"): the child, what they ask in the scene's language
 * (its meaning under it), the games left today and "🔍 Igrajva · Let's play"; once the day's games are played, "again
 * tomorrow".
 */
@Composable
fun ISpyOfferCard(offer: ISpyOffer?, doneToday: Boolean, translations: Boolean, onPlay: () -> Unit) {
    if (offer == null) {
        if (doneToday) Text("✓ 🔍 ${bi("ispy.title")}: ${bi("ispy.tomorrow")}", style = MaterialTheme.typography.bodyMedium, color = AlpineGreen)
        return
    }
    val p = offer.host.person
    Surface(color = XpGold.copy(alpha = 0.16f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            EmojiBadge(p.emoji, MaterialTheme.colorScheme.surface, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("🔍 ${bi("ispy.title")}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text("${p.name}: ${offer.lines.offer.sl}", style = MaterialTheme.typography.bodyMedium)
                if (translations && offer.lines.offer.en.isNotBlank()) {
                    Text(offer.lines.offer.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(bi("ispy.gamesLeft", "n" to offer.left), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onPlay, modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp)) { Text("🔍 ${bi("ispy.play")}") }
            }
        }
    }
}

/**
 * A game of «Vidim, vidim» over the lower part of the scene screen, as a dialog's panel is (companion/SCENES.md, "I spy"):
 * the child's lines (the scene's language big and voiced, the meaning small under it, the dialogs' 👁 hiding it, a word
 * tapped opening its card) and the learner's guesses; under them, while the round is on, "👆 Poišči v prizoru · Find it in
 * the scene" (a tap in the picture answers), "💡 Še en namig · Another clue", "🙈 Pokaži mi · Show me", and "✋ Izberi raje
 * · Let me choose" with the things in the picture now ([visible]) as chips, which TalkBack always has; once a round is
 * solved its word (a tap opens it over the thing) and "Naprej · Next"; at the end what it brought.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ISpyPanel(
    vm: AppViewModel,
    scene: SceneSpec,
    play: ISpyPlay,
    voice: Spoken,
    visible: List<SceneObject>,
    onTap: (String) -> Unit,
    onMore: () -> Unit,
    onReveal: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val run = play.run
    val prefs = vm.dialogPrefs
    val p = play.host.person
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Text(p.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val round = if (run.step == ISpyRun.Step.END) "" else " · ${run.round + 1}/${run.rounds.size}"
                    Text("🔍 ${bi("ispy.title")}$round", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (run.step != ISpyRun.Step.END) TranslationsToggle(prefs.translations, prefs::showTranslations)
                CloseButton(onClose)
            }
            if (run.step == ISpyRun.Step.END) {
                ISpyResultCard(scene, play, onClose, Modifier.weight(1f))
            } else {
                LinesOverTurn(
                    lines = { ISpyLines(vm, scene, play, voice, prefs.translations) },
                    turn = {
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when (run.step) {
                                ISpyRun.Step.FIND -> FindTurn(run, visible, onTap, onMore, onReveal)
                                ISpyRun.Step.SOLVED -> Solved(scene, run, onWord, onNext)
                                ISpyRun.Step.END -> Unit
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The lines of the game: the child's (voiced by the scene screen, the newest typed in and read out by TalkBack) and the learner's guesses. */
@Composable
private fun ISpyLines(vm: AppViewModel, scene: SceneSpec, play: ISpyPlay, voice: Spoken, translations: Boolean) {
    val said = play.run.said
    val list = rememberLazyListState()
    LaunchedEffect(said.size) { if (said.isNotEmpty()) list.animateScrollToItem(said.lastIndex) }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(said, key = { i, _ -> i }) { i, s ->
            val onWord = lookUp(vm, s.sl, s.en)
            val translation = rememberTranslation(s, translations)
            val onLong = remember(s) {
                { vm.sentences.open(SentenceQuery(s.sl, s.en, scene = scene.id, sceneTitle = bi("ispy.title"), dialog = "ispy", person = play.host.person.name)) }
            }
            // the newest of the child's lines is read out (a clue, a reaction), as it comes
            val live = Modifier.semantics { if (i == said.lastIndex && s.who != null) liveRegion = LiveRegionMode.Polite }
            Box(live) {
                if (s.who == null) Mine(s, onWord, translation, onLong)
                else Theirs(s, play.host.person.emoji, vm.speaker, voice, typing = i == said.lastIndex, onWord = onWord, translation = translation, onLong = onLong)
            }
        }
    }
}

/** A round on: find the thing in the picture, or ask for another clue, or be shown it; the things as chips for TalkBack and on request. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindTurn(run: ISpyRun, visible: List<SceneObject>, onTap: (String) -> Unit, onMore: () -> Unit, onReveal: () -> Unit) {
    val talkBack = rememberTalkBack()
    var choose by remember(run.round) { mutableStateOf(false) }
    if (!talkBack) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().popIn(run.round, from = 0.9f)) {
            Text(
                "👆 ${bi("sceneDialog.findInScene")}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (run.moreClues) FilledTonalButton(onClick = onMore, modifier = Modifier.heightIn(min = 48.dp)) { Text("💡 ${bi("ispy.moreClue")}") }
        else FilledTonalButton(onClick = onReveal, modifier = Modifier.heightIn(min = 48.dp)) { Text("🙈 ${bi("ispy.showMe")}") }
        if (!talkBack && !choose) TextButton(onClick = { choose = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("✋ ${bi("adaptive.letMeChoose")}") }
    }
    if (talkBack || choose) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (o in visible) {
                val wrong = o.slot in run.tried
                Surface(
                    onClick = { onTap(o.slot) },
                    enabled = !wrong,
                    shape = RoundedCornerShape(50),
                    color = if (wrong) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        o.sl.ifBlank { o.word } + if (wrong) " ✗" else "",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** A round solved: the thing's word (a tap opens it over the thing in the picture) and on to the next round or the end. */
@Composable
private fun Solved(scene: SceneSpec, run: ISpyRun, onWord: (String) -> Unit, onNext: () -> Unit) {
    val r = run.current ?: return
    val o = scene.objects.firstOrNull { it.slot == r.slot }
    val found = run.results.lastOrNull()?.found == true
    OutlinedButton(
        onClick = { onWord(r.slot) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = "${bi("ispy.openWord")}: ${r.word} · ${r.meaning}" },
    ) {
        Text(
            listOfNotNull(if (found) "✓" else "👀", o?.emoji, r.word).joinToString(" ") + " · ${r.meaning}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
    val last = run.round + 1 >= run.rounds.size
    BigButton(if (last) "${bi("ispy.end")} ›" else "${bi("common.next")} ›", onClick = onNext)
}

/** The end: each round's thing (found ✓ or shown 👀), what the village got, the child's friendship, the words reviewed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ISpyResultCard(scene: SceneSpec, play: ISpyPlay, onClose: () -> Unit, modifier: Modifier) {
    val run = play.run
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("🎉", style = MaterialTheme.typography.displayMedium, modifier = Modifier.popIn(play.sceneId).clearAndSetSemantics { })
        Text(bi("ispy.thanks"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        // the last line said: the child's goodbye, in the scene's language
        run.said.lastOrNull()?.let { Text("«${it.sl}»", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((i, res) in run.results.withIndex()) {
                val r = run.rounds.getOrNull(i) ?: continue
                val o = scene.objects.firstOrNull { it.slot == res.slot }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
                    Text(
                        listOfNotNull(if (res.found) "✓" else "👀", o?.emoji, r.word).joinToString(" ") + " · ${r.meaning}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).semantics {
                            contentDescription = "${if (res.found) bi("ispy.found") else bi("ispy.shown")}: ${r.word} · ${r.meaning}"
                        },
                    )
                }
            }
        }
        val paid = play.paid
        if (paid != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🏡 ${bi("common.forYourVillage")}", style = MaterialTheme.typography.titleSmall)
                    if (paid.paid.isNotEmpty()) RewardChips(paid.paid, startDelayMs = 300)
                    // nothing came in: the stores are full (the answers were counted all the same)
                    else Text(
                        "📦 ${bi("sceneDialog.storesFull")}: ${bi("sceneDialog.buildOrUpgrade")}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    si.lanisce.lani.ui.game.helpText(paid.help)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        play.friend?.let { FriendChip(it, Modifier.fillMaxWidth()) }
        if (play.reviewed > 0) {
            Text("📚 ${bi("ispy.reviewed", "n" to play.reviewed)}", style = MaterialTheme.typography.bodyMedium, color = AlpineGreen, textAlign = TextAlign.Center)
        }
        BigButton(bi("common.backScene"), onClick = onClose)
        Spacer(Modifier.heightIn(min = 1.dp))
    }
}
