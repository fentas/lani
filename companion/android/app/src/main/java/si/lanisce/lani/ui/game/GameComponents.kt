package si.lanisce.lani.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.lanisce.lani.game.Res
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.l10n.bi

/** Accent per resource: wheat, timber, granite, parchment ink. */
fun Res.color(): Color = when (this) {
    Res.FOOD -> Color(0xFFE8B23A)
    Res.WOOD -> Color(0xFFB0773F)
    Res.STONE -> Color(0xFF8C9BAE)
    Res.WISDOM -> Color(0xFFA27CE0)
}

/** Dark translucent plate for text over the village scene; readable on any sky. */
val HudPlate = Color(0xB3141B2A)

/**
 * One TalkBack label for a HUD chip: [description] instead of an emoji and a bare number, still a
 * button that opens its sheet. (A chip's children would otherwise be read one by one.)
 */
fun Modifier.hudSemantics(description: String, onClick: () -> Unit): Modifier = clearAndSetSemantics {
    role = Role.Button
    contentDescription = description
    onClick { onClick(); true }
}

/** A number that counts up (or down) to [value] instead of jumping. */
@Composable
fun animatedCount(value: Int, durationMs: Int = 900): Int =
    animateIntAsState(value, tween(durationMs), label = "count").value

/**
 * A HUD resource counter: counts to the new amount, bumps and shows a floating "+n" on gains.
 * The whole chip is the touch target.
 */
@Composable
fun ResourceChip(res: Res, amount: Int, cap: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shown = animatedCount(amount)
    val bump = remember { Animatable(1f) }
    val float = remember { Animatable(0f) }
    var last by remember { mutableIntStateOf(amount) }
    var gain by remember { mutableIntStateOf(0) }
    LaunchedEffect(amount) {
        if (amount > last) {
            gain = amount - last
            launch { bump.snapTo(1.25f); bump.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
            float.snapTo(1f)
            float.animateTo(0f, tween(1400))
        }
        last = amount
    }
    val full = cap != null && amount >= cap
    Box(modifier) {
        val described = "${res.sl} · ${res.en}: $amount" + (cap?.let { " / $it" } ?: "") + if (full) ", ${bi("gameComponents.full")}" else ""
        Surface(
            onClick = onClick,
            color = HudPlate,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).hudSemantics(described, onClick),
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(res.emoji, Modifier.scale(bump.value))
                    Spacer(Modifier.width(4.dp))
                    // shrinks rather than cuts digits off when the chip is narrow (large fonts): 300 must never read 30
                    BasicText(
                        "$shown",
                        style = MaterialTheme.typography.labelLarge.copy(color = if (full) res.color() else Color.White, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                    )
                }
                if (cap != null) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth((shown.toFloat() / cap.coerceAtLeast(1)).coerceIn(0f, 1f)).height(3.dp).background(res.color()))
                    }
                }
            }
        }
        if (float.value > 0f) {
            Text(
                "+$gain",
                color = res.color(),
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-18 * (1f - float.value) - 8).dp).alpha(float.value),
            )
        }
    }
}

/** How much later each of [RewardChips]' chips pops in than the one before. */
internal const val CHIP_STAGGER_MS = 180

/** One "+12 🌾" chip that pops in after [delayMs] and counts up from zero. */
@Composable
fun RewardChip(res: Res, amount: Int, delayMs: Int = 0, negative: Boolean = false) {
    val scale = remember { Animatable(0f) }
    val rise = remember { Animatable(40f) }
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        launch { rise.animateTo(0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }
        launch { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
        target = amount
    }
    val n = animatedCount(target, 700)
    // Solid chip with dark or white text, whichever reads better on it (pale tints vanish on white cards).
    val tint = if (negative) TriglavRed else res.color()
    val ink = if (tint.luminance() > 0.45f) Color(0xFF231A0E) else Color.White
    Surface(
        color = tint,
        shape = RoundedCornerShape(50),
        shadowElevation = 2.dp,
        modifier = Modifier.offset(y = rise.value.dp).scale(scale.value),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (negative) "−$n" else "+$n", fontWeight = FontWeight.Black, color = ink, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(6.dp))
            Text(res.emoji, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RewardChips(m: Map<Res, Int>, negative: Boolean = false, startDelayMs: Int = 0) {
    val items = Res.entries.mapNotNull { r -> m[r]?.takeIf { it > 0 }?.let { r to it } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, (r, n) -> RewardChip(r, n, startDelayMs + i * CHIP_STAGGER_MS, negative) }
    }
}

/** Static cost chips; red when the village can't pay that part yet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CostChips(cost: Map<Res, Int>, have: (Res) -> Int) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in Res.entries) {
            val n = cost[r] ?: continue
            val ok = have(r) >= n
            Surface(color = (if (ok) AlpineGreen else TriglavRed).copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                Text(
                    "${r.emoji} $n",
                    color = if (ok) MaterialTheme.colorScheme.onSurface else TriglavRed,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * "What your practice built": the resources a finished review or module earned, on its finish screen, and what came
 * into the chest (a tutor's request passed: their thank-you good) as presents to open ([words]: the name's word card).
 */
@Composable
fun RewardSummary(reward: Reward?, onVillage: () -> Unit, words: GiftWords = GiftWords()) {
    val r = reward ?: return
    val bonus = r.result?.takeIf { it.won }?.rewards.orEmpty()
    if (r.earned.isEmpty() && r.result == null && r.helpEarned == 0) return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🏡 ${bi("common.forYourVillage")}", style = MaterialTheme.typography.titleSmall)
            val chips = addRes(r.earned, bonus)
            RewardChips(chips, startDelayMs = 300)
            GiftReveals(remember(r) { GiftLogic.of(r) }, words, startDelayMs = giftDelayMs(chips.count { it.value > 0 }, 300))
            r.result?.let { Text(it.message, style = MaterialTheme.typography.bodyMedium) }
            HelpAndThanks(r)
            r.friend?.let { si.lanisce.lani.ui.villagers.FriendChip(it) }
            tiredText(r)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            TextButton(onClick = onVillage) { Text("${bi("gameComponents.seeVillage")} ›") }
        }
    }
}

/** What helping brought besides resources: 🤝 for a moba (the thank-you good is a present to open: [GiftReveals]). */
@Composable
fun HelpAndThanks(r: Reward, color: Color = MaterialTheme.colorScheme.onSurface) {
    helpText(r.helpEarned)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
}

/** Round emoji avatar, e.g. a quest giver. */
@Composable
fun EmojiBadge(emoji: String, bg: Color, size: Int = 48) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(emoji, style = if (size >= 48) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
    }
}
