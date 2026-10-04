package si.lanisce.lani.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.PackInfo
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.game.Calendar
import java.time.LocalDate
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget

/** "Nove besede · New words": every word pack with its progress. */
@Composable
fun PacksScreen(vm: AppViewModel, fromVillage: Boolean) {
    BackHandler { vm.leavePacks(fromVillage) }
    val all = vm.packs.list.map { vm.villagers.shown(it, vm.game.state) }
    val packs = PackSession.order(all) { it.id in vm.packs.newIds }
    // the festivals' packs in a section of their own, the next festival first: learn them early, or celebrate to learn
    val today = LocalDate.now()
    val feasts = PackSession.feasts(all) { id -> Calendar.byId(id)?.let { Calendar.occurrence(it, today) ?: Calendar.next(it, today) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            GradientHeader(
                "📚 ${bi("homeScreen.newWords")}",
                vm.content.wordsKnown.let { n -> "${bi("packsScreen.youKnowWords", "n" to n)}. ${bi("packsScreen.eachNewGrows")}." },
                backLabel(fromVillage),
                onBack = { vm.leavePacks(fromVillage) },
            )
        }
        if (packs.isEmpty() && feasts.isEmpty()) item {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(bi("packsScreen.noPacksYet"), style = MaterialTheme.typography.titleMedium)
                Text(bi("packsScreen.askTutorWordsAbout"), style = MaterialTheme.typography.bodyMedium)
                BigButton("💬 ${bi("common.askTutor")}", onClick = vm.chat::open)
            }
        }
        items(packs, key = { it.id }) { p ->
            PackCard(p, isNew = p.id in vm.packs.newIds, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { vm.startPack(p.id, fromVillage) }
        }
        if (feasts.isNotEmpty()) item("feasts") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("🎉 ${bi("packsScreen.feastsHolidays")}", style = MaterialTheme.typography.titleMedium)
                Text(
                    bi("packsScreen.learnFeastsWordsWhen"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(feasts, key = { it.id }) { p ->
            val day = p.festival?.let(Calendar::byId)?.let { Calendar.whenText(it, today) }
            PackCard(p, isNew = false, feast = day, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { vm.startPack(p.id, fromVillage) }
        }
        if (packs.isNotEmpty() || feasts.isNotEmpty()) item {
            TextButton(onClick = vm.chat::open, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("💬 ${bi("packsScreen.wantOtherWordsAsk")}")
            }
        }
    }
}

/** A pack's card; a festival's pack says so ([feast]: when the festival is, "11. novembra · 11 November"). */
@Composable
private fun PackCard(p: PackInfo, isNew: Boolean, modifier: Modifier = Modifier, feast: String? = null, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(if (p.done) 0.dp else 2.dp),
        colors = if (p.done) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(p.emoji, style = MaterialTheme.typography.headlineMedium) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LevelTag(p.level)
                    if (isNew) Surface(color = TriglavRed, shape = RoundedCornerShape(50)) {
                        Text(inTarget("learningGrid.newBadge"), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    if (p.festival != null) Surface(color = XpGold.copy(alpha = 0.22f), shape = RoundedCornerShape(50)) {
                        Text("🎉 ${bi("packsScreen.feast")}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                feast?.let { Text("📅 $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                p.giver?.let { Text("${it.emoji} ${it.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                p.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            ProgressRing(p.learned, p.total)
        }
    }
}

@Composable
private fun LevelTag(level: String) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(6.dp)) {
        Text(level, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** "12/20" inside a ring that fills gold, and green with a tick once the pack is done. */
@Composable
fun ProgressRing(learned: Int, total: Int, size: Int = 56) {
    val fraction by animateFloatAsState((learned.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f), tween(800), label = "ring")
    val done = total > 0 && learned >= total
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = if (done) AlpineGreen else XpGold
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 5.dp.toPx()
            val arc = Size(this.size.width - w, this.size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(w))
            drawArc(fill, -90f, 360f * fraction, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        if (done) Text("✓", color = AlpineGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        else Text("$learned/$total", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

/** Home and village: the next suggested pack and how many words it has left. */
@Composable
fun NewWordsCard(vm: AppViewModel, modifier: Modifier = Modifier, fromVillage: Boolean = false) {
    if (vm.packs.list.isEmpty()) return
    val festival = Calendar.soon(vm.game.state, LocalDate.now())?.id
    val next = PackSession.suggest(vm.packs.list.map { vm.villagers.shown(it, vm.game.state) }, vm.content.dashboard?.level ?: "A1", festival) { it.id in vm.packs.newIds }
    Card(
        onClick = { vm.openPacks(fromVillage) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📚 ${bi("homeScreen.newWords")}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${vm.packs.list.size} ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (next == null) {
                Text("✅ ${bi("packsScreen.knowEveryPackWord")}. ${bi("packsScreen.askTutorForMore")}.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(next.emoji, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(next.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (next.id in vm.packs.newIds) Surface(color = TriglavRed, shape = RoundedCornerShape(50)) {
                            Text(inTarget("learningGrid.newBadge"), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    Text(
                        bi("todayCard.wordsLeft", "left" to next.left) + (next.giver?.let { " · ${it.emoji} ${it.name}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProgressRing(next.learned, next.total, size = 48)
            }
            BigButton("▶  ${bi("common.learn")}", onClick = { vm.startPack(next.id, fromVillage) }, color = AlpineGreen)
        }
    }
}
