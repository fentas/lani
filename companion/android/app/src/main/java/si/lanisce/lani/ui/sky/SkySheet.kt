package si.lanisce.lani.ui.sky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.WordQuery
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.FestivalPacks
import si.lanisce.lani.game.Readings
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.sky.Said
import si.lanisce.lani.game.sky.SkyCard
import si.lanisce.lani.game.sky.SkyCards
import si.lanisce.lani.game.sky.SkyNow
import si.lanisce.lani.game.sky.SkyTap
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.game.ReadingSheet
import si.lanisce.lani.ui.game.Sheet
import si.lanisce.lani.ui.game.SheetHeader
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.lookUpIn
import java.time.ZoneId

/**
 * A tap on the night sky (companion/GAME.md, "The night sky"): the card of what was tapped ([SkyCards]), the moon's phase
 * and days, a constellation's names and where it stands, a planet as the morning or the evening star, the Milky Way, a
 * shooting star's wish. Every line in the village's language can be tapped word by word for the word's card, the sky's
 * words are chips to tap, "📚 Learn them" learns the sky's word pack, and the moon's card opens the old almanac.
 */
@Composable
fun SkySheet(vm: AppViewModel, tap: SkyTap, onDismiss: () -> Unit, fromVillage: Boolean = true) {
    val culture = Cultures.current
    val sky = remember(culture.id) { Cultures.sky(culture.id) }
    val pair = L10n.pair
    val level = vm.levelIn(culture.language.code)
    val card = remember(tap, sky, pair, level) {
        SkyCards.of(tap, SkyNow.at(System.currentTimeMillis(), SkyCards.place(sky)), sky, level, ZoneId.systemDefault(), pair)
    }
    if (card == null) {
        // nothing to say about it (a lone star without a name): no card
        LaunchedEffect(tap) { onDismiss() }
        return
    }
    var reading by remember(tap) { mutableStateOf(false) }
    val readable = remember(card.reading, culture.id) {
        card.reading?.let { id -> Cultures.library(culture.id).firstOrNull { it.id == id } }?.let(Readings.Readable::of)
    }
    if (reading && readable != null) {
        val state = vm.game.state
        ReadingSheet(
            vm, readable, reader = readable.by?.let { vm.villagers.byId(it) },
            read = state != null && Readings.done(state, readable.reading.id),
            onAnswered = { verdicts, minutes, looks, cost -> vm.readings.answered(readable.reading, verdicts, minutes, looks, cost) },
            onDismiss = { reading = false },
        )
        return
    }
    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SheetHeader(card.emoji, card.title.target, card.title.base?.takeIf { it != card.title.target }) }
            SpeakButton(vm.speaker, card.title.target, Modifier.padding(start = 8.dp))
        }
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Body(vm, card)
            Actions(vm, card, readable?.let { { reading = true } }, fromVillage, onDismiss)
        }
    }
}

/**
 * "🌌 Nebo nocoj · Tonight's sky", TalkBack's way into the sky drawn in pixels (the village's or an outdoor scene's custom
 * action): what the picture shows now ([up], [si.lanisce.lani.game.sky.SkyTargets.up]: the moon, the planets, the
 * constellations, the named stars, the Milky Way), each a row with its card's name that opens the card ([onOpen]); a
 * star that shows its figure's card is listed once, as the figure.
 */
@Composable
fun SkyTonightSheet(vm: AppViewModel, up: List<SkyTap>, onOpen: (SkyTap) -> Unit, onDismiss: () -> Unit) {
    val culture = Cultures.current
    val sky = remember(culture.id) { Cultures.sky(culture.id) }
    val pair = L10n.pair
    val level = vm.levelIn(culture.language.code)
    val rows = remember(up, sky, pair, level) {
        val now = SkyNow.at(System.currentTimeMillis(), SkyCards.place(sky))
        up.mapNotNull { t -> SkyCards.of(t, now, sky, level, ZoneId.systemDefault(), pair)?.let { t to it } }.distinctBy { it.second.title.target }
    }
    Sheet(onDismiss) {
        SheetHeader("🌌", bi("sky.tonight"))
        if (rows.isEmpty()) Text(bi("sky.nothingUp"), style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            for ((tap, card) in rows) Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpen(tap) }.semantics(mergeDescendants = true) {}.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(card.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.title.target, style = MaterialTheme.typography.titleMedium)
                    card.title.base?.takeIf { it != card.title.target }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Body(vm: AppViewModel, card: SkyCard) {
    // the people's names, and the lines to read (the phase, what the moon does, the wish, tonight's shower)
    for (s in card.folk) Line(vm, s, bold = true)
    for (s in card.lines) Line(vm, s, bold = s == card.lines.firstOrNull() && card.folk.isEmpty())
    if (card.facts.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (f in card.facts) Text(f, style = MaterialTheme.typography.bodyMedium)
    }
    card.lore?.let { lore ->
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WordText("📜 ${lore.target}", onWord = lookUpIn(vm, lore.target, lore.base, Words.SCENE), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    SpeakButton(vm.speaker, lore.target, Modifier.padding(start = 6.dp))
                }
                lore.base?.takeIf { it != lore.target }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (card.words.isNotEmpty()) {
        val pack = remember(card.pack) { card.pack?.let { FestivalPacks.pack(it) } }
        Text(bi("sky.words"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in card.words) {
                val meaning = pack?.words?.firstOrNull { it.word.equals(w, ignoreCase = true) }?.meaning
                SuggestionChip(
                    onClick = { vm.words.open(WordQuery(w, card.lore?.target.orEmpty(), meaning, Words.SCENE)) },
                    label = { Text(if (meaning != null) "$w · $meaning" else w) },
                )
            }
        }
    }
}

/** A line in the village's language (its words to tap, 🔊) over its translation. */
@Composable
private fun Line(vm: AppViewModel, s: Said, bold: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WordText(
                s.target, onWord = lookUpIn(vm, s.target, s.base, Words.SCENE),
                style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (bold) FontWeight.SemiBold else null,
                modifier = Modifier.weight(1f, fill = false),
            )
            SpeakButton(vm.speaker, s.target, Modifier.padding(start = 6.dp))
        }
        s.base?.takeIf { it != s.target }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Actions(vm: AppViewModel, card: SkyCard, onRead: (() -> Unit)?, fromVillage: Boolean, onDismiss: () -> Unit) {
    if (onRead != null) {
        val title = remember(card.reading) { card.reading?.let { id -> Cultures.library(Cultures.current.id).firstOrNull { it.id == id }?.title } }
        OutlinedButton(onClick = onRead, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("📖 " + (title?.bi() ?: bi("sky.readMore")))
        }
    }
    // the sky's word pack, when the app has it: learn its words not learned yet
    val pack = card.pack?.takeIf { card.words.isNotEmpty() && FestivalPacks.pack(it) != null }
    if (pack != null) FilledTonalButton(
        onClick = { onDismiss(); vm.startPack(pack, fromVillage = fromVillage) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text("📚 ${bi("sky.learn")}") }
}
