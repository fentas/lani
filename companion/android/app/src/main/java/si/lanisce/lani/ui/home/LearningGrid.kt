package si.lanisce.lani.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget

/** A section's heading on Home, with an optional text button on the right. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: Pair<String, () -> Unit>? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text("$label ›") } }
    }
}

/** Home's cards below "Danes": the plain surface, lifted a little. */
@Composable
fun homeCardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

/** One way into learning: emoji, name in Slovene and English, and a live status line. */
data class Tile(val emoji: String, val sl: String, val en: String, val status: TileStatus, val onClick: () -> Unit)

/**
 * "Učenje · Learning", right under "Danes · Today": compact tiles, three to a row on a phone, four on a small tablet, six on
 * a wide screen ([tiles]: every way into learning, each with its live status), then [extra] (the voice hint).
 */
@Composable
fun LearningSection(tiles: List<Tile>, extra: @Composable () -> Unit = {}) {
    Column {
        SectionTitle(bi("learningGrid.learning"))
        BoxWithConstraints(Modifier.padding(horizontal = 16.dp)) {
            val columns = learningColumns(maxWidth)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in tiles.chunked(columns)) {
                    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (t in row) TileCard(t, Modifier.weight(1f).fillMaxHeight())
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                extra()
            }
        }
    }
}

/** How many learning tiles go in a row in [width]: 3 on a phone, 4 from a small tablet's 600 dp, 6 from 840 dp. */
fun learningColumns(width: androidx.compose.ui.unit.Dp): Int = when {
    width >= 840.dp -> 6
    width >= 600.dp -> 4
    else -> 3
}

/** "📊 Napredek · Progress" at the end of Home: the road to the target level, a tap its screen. */
@Composable
fun ProgressSection(road: Road, onProgress: () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp)) { RoadCard(road, onProgress) }
}

@Composable
private fun TileCard(t: Tile, modifier: Modifier) {
    Card(
        onClick = t.onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, colors = homeCardColors(),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(t.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.weight(1f))
                if (t.status.attention) Box(Modifier.padding(top = 4.dp).size(9.dp).clip(CircleShape).background(TriglavRed).clearAndSetSemantics {})
            }
            Spacer(Modifier.height(6.dp))
            Text(t.sl, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(t.en, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Spacer(Modifier.weight(1f))
            Text(
                t.status.text,
                style = MaterialTheme.typography.labelSmall,
                color = if (t.status.attention) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (t.status.attention) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "📊 Napredek · Progress" with the road to the target level: A1 ━━○─── A2 ─── B1 ─── B2. */
@Composable
private fun RoadCard(r: Road, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = homeCardColors(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📊", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(bi("common.progress"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(bi("learningGrid.roadTarget", "target" to r.target), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {})
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {}) {
                r.steps.forEachIndexed { i, level ->
                    Text(
                        level,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (i == 0) FontWeight.Black else FontWeight.Medium,
                        color = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (i < r.steps.lastIndex) LinearProgressIndicator(
                        progress = { if (i == 0) r.progress else 0f },
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp).height(6.dp).clip(RoundedCornerShape(50)),
                        color = XpGold,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
            Text(
                bi("learningGrid.wordsNext", "words" to r.words, "goal" to r.goal, "next" to r.next),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Izzivi · Challenges": the tutor's modules, new ones first; tapping one plays it. */
@Composable
fun ChallengesSheet(modules: List<ModuleInfo>, new: Set<String>, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val sorted = modules.sortedByDescending { it.id in new }
    LaniSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✨ ${bi("learningGrid.challenges")}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                    if (new.isNotEmpty()) Text(bi("learningGrid.new", "newSize" to new.size), style = MaterialTheme.typography.labelMedium, color = TriglavRed)
                }
                Text(
                    bi("learningGrid.fromTutorWeakSpots"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
                )
            }
            if (sorted.isEmpty()) item {
                Text("${bi("learningGrid.noChallengesYet")}. ${bi("learningGrid.askYourTutor")}.", Modifier.padding(20.dp))
            }
            itemsIndexed(sorted, key = { _, m -> m.id }) { i, m ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ModuleRow(m, isNew = m.id in new) { onOpen(m.id) }
            }
        }
    }
}

@Composable
private fun ModuleRow(m: ModuleInfo, isNew: Boolean, onClick: () -> Unit) {
    // What the module is, in words: a villager's request, or the mistakes it targets.
    val about = m.quest?.let { "${it.emoji} ${it.giver} · ${bi("common.request")}" }
        ?: m.targets.takeIf { it.isNotEmpty() }?.let { "🎯 ${it.size} ${bi("learningGrid.weakSpots", "n" to it.size)}" }
        ?: "📝 ${bi("learningGrid.practice")}"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(m.level, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (isNew) FontWeight.Bold else FontWeight.Medium)
            Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (isNew) Text(
            inTarget("learningGrid.newBadge"),
            color = TriglavRed,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics {})
    }
}
