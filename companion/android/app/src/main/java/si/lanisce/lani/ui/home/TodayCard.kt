package si.lanisce.lani.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.bi

/** How Home shows an action: badge, title, what it is, and the button's words. */
private data class ActionLook(val emoji: String, val title: String, val about: String, val button: String)

private fun look(a: HomeAction): ActionLook = when (a) {
    is HomeAction.Review -> ActionLook(
        "🔁",
        bi("todayCard.reviewCards", "cards" to a.cards),
        bi("todayCard.todaysReviews"),
        "▶  ${bi("todayCard.review")}",
    )
    is HomeAction.Module -> ActionLook(
        a.info.quest?.emoji ?: if (a.weak) "🎯" else "✨",
        a.info.title,
        listOfNotNull(
            a.info.quest?.let { bi("todayCard.asks", "giver" to it.giver) },
            if (a.weak) "🎯 ${bi("todayCard.weakSpot")}" else null,
            if (a.isNew && !a.weak) "✨ ${bi("todayCard.new")}" else null,
        ).ifEmpty { listOf("${a.info.level} · ${bi("todayCard.challenge")}") }.joinToString(" · "),
        "▶  ${bi("todayCard.play")}",
    )
    is HomeAction.Pack -> ActionLook(
        a.info.emoji,
        a.info.title,
        bi("todayCard.wordsLeft", "left" to a.info.left) + (a.info.giver?.let { " · ${it.emoji} ${it.name}" } ?: ""),
        "▶  ${bi("common.learn")}",
    )
    is HomeAction.Talk -> ActionLook(
        "🗣️",
        a.continuing?.let { "${bi("todayCard.continue")}: $it" } ?: bi("common.talk"),
        bi("todayCard.talkWayThroughReal"),
        "▶  ${bi("todayCard.talk")}",
    )
    is HomeAction.Family -> ActionLook(a.challenge.emoji, a.challenge.title, a.challenge.text, "✍️  ${bi("common.answer")}")
}

/**
 * "Danes · Today": the minutes and the streak, the one thing to do next with a big button, at most two
 * other ideas, and the tutor's plan for the day (folded to one line).
 */
@Composable
fun TodayCard(
    t: Today, plan: String?, onAction: (HomeAction) -> Unit, onAskTutor: () -> Unit, modifier: Modifier = Modifier,
    /** The village's day ([HomeLogic.village]): what's on there today, and what's coming. */
    village: List<String> = emptyList(),
    onVillage: () -> Unit = {},
    /** Who waits for Jan on the map now ([HomeLogic.calls]): someone to meet, the burner at his pile; a tap opens them. */
    calls: List<VillageCall> = emptyList(),
    onCall: (VillageCall) -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                bi("common.today"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            DayProgress(t)
            Primary(t.primary, onAction)
            if (t.more.isNotEmpty()) Column {
                Text(
                    bi("todayCard.also"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                t.more.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                    Idea(a) { onAction(a) }
                }
            }
            if (village.isNotEmpty() || calls.isNotEmpty()) VillageDay(village, onVillage, calls, onCall)
            plan?.let { Plan(it, onAskTutor) }
        }
    }
}

/**
 * "🏡 V vasi · In the village": today's festival, surprise or project step, and what's coming (opens the village); then
 * who waits on the map now, each opened with a tap (their introduction, his talk).
 */
@Composable
private fun VillageDay(lines: List<String>, onClick: () -> Unit, calls: List<VillageCall>, onCall: (VillageCall) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClickLabel = bi("common.openVillage"), onClick = onClick)
                .semantics(mergeDescendants = true) {}.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "🏡 ${bi("todayCard.village")}", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            for (l in lines) Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        for (c in calls) {
            val action = when (c) {
                is VillageCall.Arrival -> "👋 ${bi("arrivals.meet")}"
                is VillageCall.Keeper -> "💬 ${bi("common.talk")}"
                is VillageCall.Treasure -> "🗺️ ${bi("treasure.openMap")}"
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small).clickable(onClickLabel = action) { onCall(c) }
                    .semantics(mergeDescendants = true) {}.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(action, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics {})
            }
        }
    }
}

/** Minutes against the daily goal as a ring, the streak and the reviews beside it. */
@Composable
private fun DayProgress(t: Today) {
    val streak = when {
        t.practised && t.streak > 0 -> "🔥 ${bi("todayCard.streakDayStreak", "streak" to t.streak)} ✓"
        t.streak > 0 -> "🔥 ${bi("todayCard.practiseTodayDayStreak", "streak" to (t.streak + 1))}"
        else -> "🔥 ${bi("todayCard.startNewStreak")}"
    }
    val minutes = if (t.goal > 0 && t.minutes < t.goal) "${t.minutes} / ${t.goal} min" else "${t.minutes} min"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        MinutesRing(t.minutes, t.progress, t.goal > 0)
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(bi("todayCard.minutesToday", "minutes" to minutes) + if (t.goal > 0 && t.minutes >= t.goal) " ✓" else "", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(streak, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            if (t.due == 0) Text(
                "✅ ${bi("todayCard.reviewsDone")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

/** The day's minutes inside a ring that fills towards the goal, green once reached. */
@Composable
private fun MinutesRing(minutes: Int, progress: Float, hasGoal: Boolean) {
    val fraction by animateFloatAsState(progress, tween(800), label = "minutes")
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
    val fill = if (progress >= 1f) AlpineGreen else XpGold
    Box(Modifier.size(56.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 6.dp.toPx()
            val arc = Size(size.width - w, size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(w))
            if (hasGoal) drawArc(fill, -90f, 360f * fraction, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("$minutes′", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Primary(a: HomeAction, onAction: (HomeAction) -> Unit) {
    val l = look(a)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Badge(l.emoji, 52)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(l.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(l.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        BigButton(l.button, onClick = { onAction(a) }, color = AlpineGreen)
    }
}

@Composable
private fun Idea(a: HomeAction, onClick: () -> Unit) {
    val l = look(a)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(l.emoji, 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(l.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(l.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics {})
    }
}

@Composable
private fun Badge(emoji: String, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) { Text(emoji, style = if (size >= 48) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge) }
}

/** The tutor's plan for today: one line until tapped, then in full with a way to ask about it. */
@Composable
private fun Plan(plan: String, onAskTutor: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = if (open) bi("todayCard.hide") else bi("common.show")) { open = !open }
                .padding(top = 10.dp)
                .semantics { stateDescription = if (open) bi("todayCard.expanded") else bi("todayCard.collapsed") },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("📋 ${bi("todayCard.tutorsPlan")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (!open) Text(
                    HomeLogic.planPreview(plan),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (open) "⌃" else "⌄",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics {},
            )
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 8.dp)) {
                Markdown(plan, color = MaterialTheme.colorScheme.onPrimaryContainer)
                TextButton(onClick = onAskTutor, modifier = Modifier.semantics { contentDescription = bi("common.askTutor") }) {
                    Text("💬 ${bi("common.askTutor")}")
                }
            }
        }
    }
}
