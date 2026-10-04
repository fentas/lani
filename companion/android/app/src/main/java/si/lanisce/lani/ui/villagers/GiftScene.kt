package si.lanisce.lani.ui.villagers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.GoodSpec
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.game.GiftLogic
import si.lanisce.lani.ui.game.GiftReveals
import si.lanisce.lani.ui.game.GiftWords
import si.lanisce.lani.ui.game.giftWords
import si.lanisce.lani.ui.scene.ChoiceMatch
import si.lanisce.lani.ui.scene.MicRationale
import si.lanisce.lani.ui.scene.MicRow
import si.lanisce.lani.ui.scene.canListen
import si.lanisce.lani.ui.scene.popIn
import si.lanisce.lani.ui.scene.rememberDialogMic
import si.lanisce.lani.ui.stage.rememberReducedMotion
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.words.StickerOr
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.lookUpIn

/** The good on its way from Jan to them, this long. */
private const val HAND_MS = 650

/**
 * Giving a good (companion/VILLAGERS.md, "Giving a good"), as a small scene over the chest or their page: their portrait
 * and the good, big; Jan says it as they hand it over (two right ways and one in the wrong register, which says why; the
 * 🎤 as in a scene's dialog), the good goes over, and they answer in their voice (a good they like: a cheer and hearts,
 * a rare one: special joy, an ordinary one: warm thanks); then the hearts it brought, and a level's gift of theirs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftScene(vm: AppViewModel, m: GiftMoment) {
    val v = vm.villagers.byId(m.villager) ?: return
    val good = Catalog.goods[m.good] ?: return
    val reduced = rememberReducedMotion()
    val words = giftWords(vm)
    // the good on its way over, once the right words are said
    val hand = remember(m.villager, m.good) { Animatable(0f) }
    var arrived by remember(m.villager, m.good) { mutableStateOf(false) }
    var talking by remember(m.villager, m.good) { mutableStateOf(false) }
    LaunchedEffect(m.handed) {
        if (!m.handed) return@LaunchedEffect
        if (reduced) hand.snapTo(1f) else hand.animateTo(1f, tween(HAND_MS, easing = FastOutSlowInEasing))
        arrived = true
    }
    LaunchedEffect(arrived, m.reply) {
        val r = m.reply ?: return@LaunchedEffect
        if (!arrived) return@LaunchedEffect
        talking = true
        vm.speaker.say(r.sl, voiceName = v.speakerVoice, fallback = VillagerLogic.voiceOf(v), person = v.id)
        delay((900L + 70L * r.sl.length).coerceAtMost(5_500L))
        talking = false
    }
    ModalBottomSheet(onDismissRequest = vm.villagers::closeGift, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GiftStage(v, good, m, words, pose(m, arrived, talking), hand.value, hearts = arrived && m.reaction != GiftReaction.ORDINARY)
            if (!arrived) HandOver(vm, m)
            else Answered(vm, v, m, words)
        }
    }
}

/** Their pose: a wave hello, thinking over a wrong word, talking as they answer, then a cheer (a good they love) or a smile. */
@Composable
private fun pose(m: GiftMoment, arrived: Boolean, talking: Boolean): Pose {
    var waving by remember(m.villager, m.good) { mutableStateOf(true) }
    LaunchedEffect(m.villager, m.good) {
        delay(1_800)
        waving = false
    }
    return when {
        talking -> Pose.TALK
        arrived -> if (m.reaction == GiftReaction.ORDINARY) Pose.HAPPY else Pose.CHEER
        m.why != null -> Pose.THINK
        waving -> Pose.WAVE
        else -> Pose.IDLE
    }
}

/** Their portrait (hearts rising over a good gift) and the good, big, with its name to tap and hear; it goes over as Jan hands it. */
@Composable
private fun GiftStage(v: Villager, good: GoodSpec, m: GiftMoment, words: GiftWords, pose: Pose, hand: Float, hearts: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            VillagerPortrait(v, 112.dp, pose = pose)
            if (hearts) HeartBurst(m.gain, Modifier.align(Alignment.TopCenter))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${v.emoji} ${v.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("🎁 ${bi("villagerParts.gift")}", style = MaterialTheme.typography.labelLarge, color = HeartPink, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // over to them: left, smaller, gone
                Box(
                    Modifier.graphicsLayer {
                        translationX = -hand * 150.dp.toPx()
                        translationY = -hand * 18.dp.toPx()
                        val s = 1f - 0.6f * hand
                        scaleX = s; scaleY = s
                        alpha = 1f - hand
                    }.semantics { contentDescription = bi("giftScene.youGive", "item" to good.name.nameText) },
                ) {
                    StickerOr(words.picture(good.name), 64.dp) { Text(good.name.emoji, style = MaterialTheme.typography.displayMedium) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    WordText(
                        good.name.sl, words.lookUp?.invoke(good.name.sl, good.name.en),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (good.name.en.isNotBlank() && good.name.en != good.name.sl) {
                        Text(good.name.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                words.speaker?.let { SpeakButton(it, good.name.sl) }
            }
        }
    }
}

/**
 * Jan's turn: what they say as they give it, two right ways and one in the wrong register (each with 🔊, a word's card on
 * a long press; a wrong one crossed out with its why), and the 🎤 when something can listen: the words said count as tapped.
 */
@Composable
private fun HandOver(vm: AppViewModel, m: GiftMoment) {
    val phoneMic = remember { vm.recognizer.inApp || vm.recognizer.dialog }
    val listens = canListen(vm, phoneMic)
    var heard by remember(m.villager, m.good) { mutableStateOf<String?>(null) }
    var note by remember(m.villager, m.good) { mutableStateOf<String?>(null) }
    val mic = rememberDialogMic(
        vm,
        expected = { ChoiceMatch.prompt(m.choices.map { it.target }) },
        onHeard = { alternatives, phone ->
            when (val r = ChoiceMatch.match(alternatives, m.choices.map { it.target })) {
                is ChoiceMatch.Result.Said -> {
                    if (phone) vm.recognizer.confirm()
                    heard = r.heard
                    note = null
                    vm.villagers.say(r.index)
                }
                is ChoiceMatch.Result.Unsure -> {
                    heard = null
                    note = if (r.heard.isBlank()) si.lanisce.lani.ui.NodeMic.NOT_HEARD else "🎤 ${bi("sceneDialog.heardWhichOne", "heard" to r.heard)}"
                }
            }
        },
        onFailed = { note = it },
    )
    Text("💬 ${bi("giftScene.whatDoYouSay")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    m.choices.forEachIndexed { k, c ->
        PhraseRow(vm, c, wrong = k in m.tried, shakeKey = m.mistakes.takeIf { k == m.wrongPick }, onClick = { vm.villagers.say(k) })
    }
    m.why?.let { why ->
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().popIn(why, from = 0.9f)) {
            Text(
                "💡 $why", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (listens) {
        if (mic.asking) MicRationale(mic) else MicRow(mic, big = false)
        heard?.let { Text("🎤 «$it»", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** One thing to say: a tap says it, a long press on a word looks it up, 🔊 reads it; a wrong one is crossed out and shakes. */
@Composable
private fun PhraseRow(vm: AppViewModel, c: GiftPhrase, wrong: Boolean, shakeKey: Int?, onClick: () -> Unit) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey == null) return@LaunchedEffect
        for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(45))
    }
    Row(Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value * density }, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onClick,
            enabled = !wrong,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(if (wrong) 2.dp else 1.dp, if (wrong) TriglavRed else MaterialTheme.colorScheme.outline),
            color = if (wrong) TriglavRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    WordText(c.target, lookUpIn(vm, c.target, c.base, Words.VILLAGER), longPress = true, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (c.base.isNotBlank()) Text(c.base, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (wrong) Text("✗", color = TriglavRed, fontWeight = FontWeight.Black, modifier = Modifier.clearAndSetSemantics { contentDescription = bi("sceneDialog.wrong") })
            }
        }
        SpeakButton(vm.speaker, c.target, Modifier.padding(start = 6.dp))
    }
}

/**
 * Handed over: what Jan said (their bubble, right), their answer in their words and voice (a word's card on a tap), the
 * hearts it brought (the FriendChip: a new level, what they'll remember), a level's gift of theirs to unwrap, and done.
 */
@Composable
private fun Answered(vm: AppViewModel, v: Villager, m: GiftMoment, words: GiftWords) {
    m.said?.let { said ->
        Row(Modifier.fillMaxWidth().popIn(said, from = 0.85f, origin = TransformOrigin(1f, 0f)), horizontalArrangement = Arrangement.End) {
            Spacer(Modifier.width(48.dp))
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                    WordText(said.target, lookUpIn(vm, said.target, said.base, Words.VILLAGER), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (said.base.isNotBlank()) Text(said.base, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                }
            }
        }
    }
    m.reply?.let { SpeechBubble(vm, v, it, Modifier.fillMaxWidth()) }
    m.gain?.let { g ->
        FriendChip(g, Modifier.fillMaxWidth().popIn(g, from = 0.8f))
        // a friendship's gift (the level this reached): a present to open
        GiftReveals(remember(g) { GiftLogic.of(g) }, words, startDelayMs = 600)
    }
    Spacer(Modifier.height(4.dp))
    BigButton("✓ ${bi("common.close")}", onClick = vm.villagers::closeGift)
}
