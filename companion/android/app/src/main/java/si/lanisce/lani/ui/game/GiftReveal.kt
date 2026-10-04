package si.lanisce.lani.ui.game

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.ItemName
import si.lanisce.lani.game.scene.StickerSpot
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.Burst
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.stage.rememberReducedMotion
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.villagers.HeartPink
import si.lanisce.lani.ui.words.StickerOr
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.WordToken
import si.lanisce.lani.ui.words.lookUpIn
import kotlin.math.PI
import kotlin.math.sin

/**
 * What a revealed gift's name can do: open the card of a tapped word ([lookUp], given the name and its translation),
 * and say the name ([speaker]). Without them it's plain text. [picture]: the sticker a thing shows instead of its emoji
 * when a scene draws it and no emoji shows it (the horseshoe, the scythe), see [si.lanisce.lani.game.scene.WordStickers.named].
 */
class GiftWords(
    val lookUp: ((line: String, en: String) -> (WordToken) -> Unit)? = null,
    val speaker: Speaker? = null,
    val picture: (ItemName) -> StickerSpot? = { null },
)

/** [vm]'s word card and voice, for a reveal's name: a good's name is a word to learn. */
fun giftWords(vm: AppViewModel): GiftWords =
    GiftWords({ line, en -> lookUpIn(vm, line, en, Words.VILLAGER) }, vm.speaker, picture = { vm.scenes.stickers.named(it.sl, it.emoji) })

/** When the gifts after [chips] reward chips that start at [startMs] pop in: once the last chip is in. */
fun giftDelayMs(chips: Int, startMs: Int): Int = startMs + chips * CHIP_STAGGER_MS + 250

/** Wrapped this long, a present opens by itself. */
private const val UNWRAP_MS = 1200L
/** Open this long, it goes into the chest. */
private const val STORE_AFTER_MS = 1500L
private const val FLIGHT_MS = 650
/** The next present of a run pops in this much after the one before. */
private const val NEXT_GIFT_MS = 700

/** The gifts a run brought ([GiftLogic.of]), one under another, the first after [startDelayMs]. */
@Composable
fun GiftReveals(items: List<Received>, words: GiftWords = GiftWords(), startDelayMs: Int = 0, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, r -> GiftReveal(r, words, startDelayMs + i * NEXT_GIFT_MS) }
    }
}

private enum class Phase { HIDDEN, WRAPPED, OPEN, STORED }

/**
 * A good (or a tool) that came into the chest, as a present: it pops in wrapped after [delayMs], a tap (or a moment)
 * unwraps it with a burst: the thing big, its name to tap and learn (the word card), the name in the base language,
 * who gave it and what it's for; then it flies into the chest ("✓ V skrinji · In the chest"), where it's new until
 * the chest is opened. TalkBack hears "Darilo: Potica · A gift: Potica" as it opens; with the system's animations
 * off it shows open at once.
 */
@Composable
fun GiftReveal(r: Received, words: GiftWords = GiftWords(), delayMs: Int = 0, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    var phase by remember(r) { mutableStateOf(if (reduced) Phase.STORED else Phase.HIDDEN) }
    val pop = remember(r) { Animatable(if (reduced) 1f else 0f) }
    val fly = remember(r) { Animatable(0f) }
    val bump = remember(r) { Animatable(1f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(r) {
        if (reduced) return@LaunchedEffect
        delay(delayMs.toLong())
        phase = Phase.WRAPPED
        launch { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
        // a tap opens it sooner
        withTimeoutOrNull(UNWRAP_MS) { snapshotFlow { phase }.first { it != Phase.WRAPPED } }
        phase = Phase.OPEN
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(STORE_AFTER_MS)
        fly.animateTo(1f, tween(FLIGHT_MS, easing = FastOutSlowInEasing))
        phase = Phase.STORED
        bump.snapTo(1.45f)
        bump.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    val open = phase >= Phase.OPEN
    // the card, the thing in it and the chest it flies into
    var card by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var thing by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var chest by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var flier by remember { mutableStateOf(IntSize.Zero) }
    val arc = with(LocalDensity.current) { 56.dp.toPx() }
    val described = if (open) r.announce else bi("giftReveal.wrapped")
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(2.dp, XpGold),
        shadowElevation = 4.dp,
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth()
            .graphicsLayer {
                scaleX = pop.value; scaleY = pop.value
                alpha = pop.value.coerceIn(0f, 1f)
            }
            // not there for TalkBack either until it pops in
            .then(if (phase == Phase.HIDDEN) Modifier.clearAndSetSemantics { } else Modifier)
            .animateContentSize(),
    ) {
        Box(Modifier.onGloballyPositioned { card = it }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // said as it opens: "Darilo: Potica · A gift: Potica"
                Text(
                    "🎁 ${bi("villagerParts.gift")}",
                    style = MaterialTheme.typography.labelLarge, color = HeartPink, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = described
                    },
                )
                if (!open) Wrapped(onOpen = { if (phase == Phase.WRAPPED) phase = Phase.OPEN })
                else {
                    Opened(r, words, reduced, onThing = { thing = it })
                    ChestBadge(stored = phase == Phase.STORED, bump = bump.value, onChest = { chest = it })
                }
            }
            // the thing on its way into the chest: up, over and in, smaller as it goes
            val c = card?.takeIf { it.isAttached }
            val from = if (c == null) null else thing?.takeIf { it.isAttached }?.centerIn(c)
            val to = if (c == null) null else chest?.takeIf { it.isAttached }?.centerIn(c)
            if (phase == Phase.OPEN && fly.value > 0f && from != null && to != null) {
                val t = fly.value
                val at = lerp(from, to, t) - Offset(0f, arc * sin(PI.toFloat() * t))
                Box(
                    Modifier.onSizeChanged { flier = it }.graphicsLayer {
                        translationX = at.x - flier.width / 2f
                        translationY = at.y - flier.height / 2f
                        val s = 1f - 0.6f * t
                        scaleX = s; scaleY = s
                    }.clearAndSetSemantics { },
                ) {
                    StickerOr(words.picture(r.item), 48.dp) { Text(r.item.emoji, style = MaterialTheme.typography.displaySmall) }
                }
            }
        }
    }
}

/** The centre of this node in [card]'s coordinates. */
private fun LayoutCoordinates.centerIn(card: LayoutCoordinates): Offset =
    card.localPositionOf(this, Offset(size.width / 2f, size.height / 2f))

/** The present, wobbling: a tap opens it. */
@Composable
private fun Wrapped(onOpen: () -> Unit) {
    val tilt = rememberInfiniteTransition(label = "wrapped").animateFloat(-9f, 9f, infiniteRepeatable(tween(240), RepeatMode.Reverse), label = "tilt").value
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = bi("giftReveal.open"), role = Role.Button, onClick = onOpen)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎁", style = MaterialTheme.typography.displayMedium, modifier = Modifier.graphicsLayer { rotationZ = tilt }.clearAndSetSemantics { })
        Text(bi("giftReveal.tapToOpen"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What was in it: the thing big over a burst, its name (tap a word for its card, 🔊), the translation, who, what for. */
@Composable
private fun Opened(r: Received, words: GiftWords, reduced: Boolean, onThing: (LayoutCoordinates) -> Unit) {
    val grow = remember(r) { Animatable(if (reduced) 1f else 0.3f) }
    LaunchedEffect(r) { grow.animateTo(1f, if (reduced) snap() else spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow)) }
    Box(Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
        // wider than its row: the card's edge is where it stops
        if (!reduced) Burst(r, Modifier.requiredSize(240.dp, 160.dp))
        Box(
            Modifier.graphicsLayer { scaleX = grow.value; scaleY = grow.value }
                .onGloballyPositioned(onThing).clearAndSetSemantics { },
        ) {
            StickerOr(words.picture(r.item), 76.dp) { Text(r.item.emoji, style = MaterialTheme.typography.displayMedium) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        WordText(
            r.item.sl, words.lookUp?.invoke(r.item.sl, r.item.en),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (r.count > 1) Text(" ×${r.count}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = XpGold)
        words.speaker?.let { SpeakButton(it, r.item.sl, Modifier.padding(start = 8.dp)) }
    }
    if (r.item.en.isNotBlank() && r.item.en != r.item.sl) {
        Text(r.item.en, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    r.from?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)) }
    if (r.use.isNotBlank()) Text(r.use, style = MaterialTheme.typography.bodySmall, color = AlpineGreen, textAlign = TextAlign.Center)
}

/** The chest the thing goes into: waiting ("→ V skrinjo · Into the chest"), then it bumps, new ("✓ V skrinji"). */
@Composable
private fun ChestBadge(stored: Boolean, bump: Float, onChest: (LayoutCoordinates) -> Unit) {
    val shown by animateFloatAsState(if (stored) 1f else 0.45f, label = "chest")
    Row(Modifier.padding(top = 8.dp).alpha(shown), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.onGloballyPositioned(onChest)) {
            Text("🧰", style = MaterialTheme.typography.titleLarge, modifier = Modifier.graphicsLayer { scaleX = bump; scaleY = bump }.clearAndSetSemantics { })
            if (stored) NewDot(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            if (stored) "✓ ${bi("giftReveal.inChest")}" else "→ ${bi("giftReveal.intoChest")}",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The red dot of something new (the HUD's chest, a gift just put in it). */
@Composable
fun NewDot(modifier: Modifier = Modifier) {
    Box(modifier.size(11.dp).clip(CircleShape).background(TriglavRed).border(1.5.dp, Color.White, CircleShape))
}
