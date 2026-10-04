package si.lanisce.lani.ui.reading

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.game.Readings
import si.lanisce.lani.game.culture.ReadingFile
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.backLabel
import si.lanisce.lani.game.scene.StoryBooks
import si.lanisce.lani.ui.game.ReadingLogic
import si.lanisce.lani.ui.game.ReadingSheet
import si.lanisce.lani.ui.notebook.NotebookRow
import si.lanisce.lani.ui.notebook.rememberNotebook
import si.lanisce.lani.ui.theme.AlpineGreen

/**
 * "📖 Branje · Reading" (companion/GAME.md, "The reading corner"): the readings of the learner's village, the culture
 * pack's and the tutor's, those at the learner's level first, then a step up, the easier ones, the harder ones; each with
 * its level, length and whether it was read. One opens in the reading view ([ReadingSheet]): its lines, questions, new
 * words, and "🎤 Beri na glas · Read aloud". On top, one row for the story notebook ("📓 Moj zvezek zgodb · My story
 * notebook", ui/notebook: the stories heard by the fire, written down; its own thing, listed here as one item).
 * [fromVillage]: opened from the village book or the chest; back leads there.
 */
@Composable
fun ReadingsScreen(vm: AppViewModel, fromVillage: Boolean) {
    BackHandler { vm.leaveReadings(fromVillage) }
    val lang = L10n.pair.target
    val level = vm.levelIn(lang.code)
    val read = vm.readings.read()
    // a reading that names someone Jan doesn't know yet waits for them (Mentions: who is here and met, with the village)
    val list = remember(vm.readings.all, level, read, lang, vm.game.state, vm.villagers.all) { Readings.corner(vm.readings.all, level, read, lang) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // the story notebook, where the village has a storyteller: one row
    val notebook = rememberNotebook(vm)
    val tells = remember(vm.scenes.all) { StoryBooks.stories(vm.scenes.all).isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item("header") {
            GradientHeader(
                "📖 ${bi("readings.title")}",
                "${bi("readings.yourLevel", "level" to level)}. ${bi("readings.about")}",
                backLabel(fromVillage),
                onBack = { vm.leaveReadings(fromVillage) },
            )
        }
        if (tells) item("notebook") {
            NotebookRow(notebook, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) { vm.openNotebook() }
        }
        if (list.isEmpty()) item("empty") {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(bi("readings.none"), style = MaterialTheme.typography.titleMedium)
                BigButton("💬 ${bi("readings.askForOne")}", onClick = { askForReading(vm, level) })
            }
        }
        var group: Readings.Fit? = null
        for (r in list) {
            val fit = Readings.fit(r.level, level)
            if (fit != group) {
                group = fit
                item("group:${fit.name}") { GroupTitle(fit) }
            }
            item(r.id) {
                ReadingCard(r, read = r.id in read, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { open = r.id }
            }
        }
        if (list.isNotEmpty()) item("ask") {
            TextButton(onClick = { askForReading(vm, level) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("💬 ${bi("readings.askForOne")}")
            }
        }
    }
    open?.let(vm.readings::byId)?.let { r ->
        ReadingSheet(
            vm, Readings.Readable.of(r), reader = r.by?.let { vm.villagers.byId(it) }, read = r.id in read,
            onAnswered = { verdicts, minutes, looks, cost -> vm.readings.answered(r, verdicts, minutes, looks, cost) },
            onDismiss = { open = null },
            onReadAloud = {
                open = null
                vm.openReadAloud(r.id, fromVillage, fromCorner = true)
            },
        )
    }
}

/** Asks the tutor for something new to read at [level] (the chat opens for the answer). */
private fun askForReading(vm: AppViewModel, level: String) =
    vm.chat.ask(bi("readings.askText"), buildJsonObject { put("reading_request", buildJsonObject { put("level", level) }) })

@Composable
private fun GroupTitle(fit: Readings.Fit) {
    val key = when (fit) {
        Readings.Fit.AT -> "readings.atYourLevel"
        Readings.Fit.NEXT -> "readings.stepUp"
        Readings.Fit.EASIER -> "readings.easier"
        Readings.Fit.HARDER -> "readings.harder"
    }
    Text(
        bi(key), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** A reading: its kind's emoji, level, length and minutes, the title in the target and the base, read (✓), the tutor's (🧑‍🏫). */
@Composable
private fun ReadingCard(r: ReadingFile, read: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val words = remember(r) { Readings.length(r, L10n.pair.target) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(if (read) 0.dp else 2.dp),
        colors = if (read) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(Readings.emojiOf(r.kind), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics { }) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(6.dp)) {
                        Text(r.level, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Text(
                        "${inTarget(ReadingLogic.kindKey(r.kind))} · ${bi("readings.length", "words" to words, "minutes" to Readings.minutes(words, r.level))}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(r.title.target, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (r.title.base != r.title.target) Text(r.title.base, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.source == "tutor") Text("🧑‍🏫 ${bi("reading.byTutor")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (read) {
                val label = bi("reading.readBefore")
                Text("✓", color = AlpineGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics { contentDescription = label })
            }
        }
    }
}
