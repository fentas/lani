package si.lanisce.lani.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.Link
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.ui.game.HeaderVillage
import si.lanisce.lani.ui.game.timeLeft
import si.lanisce.lani.ui.greeting
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.bi
import java.time.LocalTime

/**
 * The top of Home: the village edge to edge behind the greeting and the streak, XP and achievements
 * (they open Progress), and at the bottom a chip that opens the village and says what's up there.
 */
@Composable
fun HomeHero(vm: AppViewModel, d: Dashboard?, onLink: () -> Unit = {}) {
    val village = vm.game.state
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(SloBlueDeep, SloBlue)))) {
        if (village != null) HeaderVillage(village, vm.game.celebrate, onOpen = vm::openVillage, modifier = Modifier.matchParentSize())
        else Triglav(Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter))
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${greeting(LocalTime.now().hour)}, ${d?.name ?: ""}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                LinkDot(vm.link, vm.unpaired, onClick = onLink)
            }
            if (vm.unpaired) UnpairedChip(onLink)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val streak = d?.streak ?: 0
                Pill("🔥", "$streak", "${bi("homeHero.streak")}: $streak", FlameOrange, pulse = streak > 0 && d?.practisedToday == false, onClick = { vm.openProgress() })
                Pill("⭐", "${d?.xp ?: 0} XP", "${bi("homeHero.xp")}: ${d?.xp ?: 0}", XpGold, onClick = { vm.openProgress() })
                Pill("🏆", "${d?.achievements ?: 0}", "${bi("homeHero.achievements")}: ${d?.achievements ?: 0}", Color.White, onClick = { vm.openProgress() })
            }
            SyncChip(vm.waitingToSync)
            UpdateBanner(vm)
            if (village != null) {
                Spacer(Modifier.height(96.dp)) // the village shows through
                VillageChip(HomeLogic.villageHint(village, vm.villagers.outlook(village)), onClick = vm::openVillage)
            } else {
                Spacer(Modifier.height(56.dp))
            }
        }
    }
}

/** "🔥 Moja vas · My village ›", or the event on now, or the dying fire: tapping it opens the village. */
@Composable
private fun VillageChip(hint: VillageHint, onClick: () -> Unit) {
    val (color, text) = when (hint) {
        is VillageHint.Event -> {
            val k = hint.event.kind
            val left = timeLeft(hint.event.deadline, System.currentTimeMillis())
            (if (HomeLogic.threat(k)) TriglavRed else XpGold.copy(alpha = 0.95f)) to "${k.emoji} ${k.sl} · ${k.en} · ⏳ $left"
        }
        VillageHint.FireLow -> FlameOrange to "🔥 ${bi("common.fireDying")}"
        is VillageHint.NeedsRoom -> SloBlueDeep.copy(alpha = 0.85f) to HomeLogic.roomText(hint.arrival)
        is VillageHint.Calm -> Color.Black.copy(alpha = 0.45f) to "${hint.age.emoji} ${bi("homeHero.myVillage")} · ${hint.age.sl}"
    }
    // TalkBack (and QA) always hear where it leads, whatever the chip says.
    val label = if (hint is VillageHint.Calm) text else "${bi("homeHero.myVillage")}: $text"
    Surface(
        onClick = onClick,
        color = color,
        shape = RoundedCornerShape(50),
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                color = if (hint is VillageHint.Event && !HomeLogic.threat(hint.event.kind)) Color.Black else Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text("›", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun UpdateBanner(vm: AppViewModel) {
    val u = vm.update.release ?: return
    val progress = vm.update.progress
    Surface(
        onClick = { if (progress == null) vm.update.install() },
        color = Color.Black.copy(alpha = 0.35f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("⬆️ ${bi("homeHero.updateAvailable", "versionName" to u.versionName)}", color = Color.White, fontWeight = FontWeight.Bold)
            u.notes?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(6.dp))
            when {
                progress != null -> LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = XpGold)
                vm.update.ready == u.versionCode && vm.update.silent ->
                    Text("✅ ${bi("homeHero.installsWhenLeaveApp")}", color = XpGold, style = MaterialTheme.typography.labelLarge)
                else -> Text(bi("homeHero.tapInstall"), color = XpGold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** "⏳ 3 čaka na povezavo": writes queued offline, shown until the node has them. */
@Composable
private fun SyncChip(count: Int) {
    if (count <= 0) return
    Surface(color = Color.Black.copy(alpha = 0.35f), shape = RoundedCornerShape(50), modifier = Modifier.padding(top = 10.dp)) {
        Text(
            "⏳ ${bi("homeHero.countWaitingSync", "count" to count)}",
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Three peaks and two waves, as on the Slovenian coat of arms: the header before the village has loaded. */
@Composable
private fun Triglav(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val peaks = Path().apply {
            moveTo(0f, h)
            lineTo(w * 0.22f, h * 0.55f)
            lineTo(w * 0.34f, h * 0.72f)
            lineTo(w * 0.5f, h * 0.25f)
            lineTo(w * 0.66f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.55f)
            lineTo(w, h)
            close()
        }
        drawPath(peaks, Color.White.copy(alpha = 0.14f))
        val cap = Path().apply {
            moveTo(w * 0.46f, h * 0.37f); lineTo(w * 0.5f, h * 0.25f); lineTo(w * 0.54f, h * 0.37f); close()
        }
        drawPath(cap, Color.White.copy(alpha = 0.35f))
        for (row in 0..1) {
            val y = h * (0.86f + row * 0.07f)
            val wave = Path().apply {
                moveTo(0f, y)
                var x = 0f
                while (x < w) { quadraticTo(x + 20f, y - 10f, x + 40f, y); quadraticTo(x + 60f, y + 10f, x + 80f, y); x += 80f }
            }
            drawPath(wave, Color.White.copy(alpha = 0.18f), style = Stroke(4f, cap = StrokeCap.Round))
        }
    }
}

/** The tutor's status as a dot; a tap opens the connection (who this phone is paired with, pairing again). */
@Composable
private fun LinkDot(link: Link, unpaired: Boolean, onClick: () -> Unit) {
    val color = when (link) {
        Link.ONLINE -> AlpineGreen
        Link.CONNECTING -> XpGold
        Link.OFFLINE -> TriglavRed
    }
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = linkLabel(link, unpaired) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
    }
}

fun linkLabel(link: Link, unpaired: Boolean) = when {
    unpaired -> "🔒 ${bi("homeHero.phoneNoLongerPaired")}"
    link == Link.ONLINE -> bi("homeHero.tutorOnline")
    link == Link.CONNECTING -> bi("homeHero.connecting")
    else -> bi("homeHero.offline")
}

/** The tutor refused this phone's token: it was unpaired. A tap leads to scanning a new code. */
@Composable
private fun UnpairedChip(onClick: () -> Unit) {
    Surface(onClick = onClick, color = TriglavRed, shape = RoundedCornerShape(50), modifier = Modifier.padding(top = 10.dp).heightIn(min = 40.dp)) {
        Text(
            "🔒 ${bi("homeHero.notPairedAnyMore")} ›",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Pill(emoji: String, value: String, description: String, tint: Color, pulse: Boolean = false, onClick: () -> Unit = {}) {
    val scale = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(1f, 1.18f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "flame").value
    } else 1f
    Surface(
        onClick = onClick,
        color = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = description },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, Modifier.scale(scale))
            Spacer(Modifier.width(6.dp))
            Text(value, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}
