package si.lanisce.lani.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.scene.Homes
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.WordToken
import si.lanisce.lani.ui.words.lookUpWords
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.l10n.bi

/** What a spot of the landscape is for: a line under its name, and the practice it opens (with its button label), if any. */
private data class SpotUse(val sub: String, val gather: Res?, val action: String)

private fun spotUse(id: String): SpotUse {
    // what the culture says about the spot (world.json `spots`), where it says
    val about = si.lanisce.lani.game.culture.Cultures.current.world.spots[id]?.about?.bi()
    return when (id) {
        "woodpile" -> SpotUse(bi("placeCard.firewoodFire"), Res.WOOD, "🎧 ${bi("common.gatherWood")}")
        "rocks" -> SpotUse(bi("placeCard.stoneWalls"), Res.STONE, "🪨 ${bi("placeCard.gatherStone")}")
        "meadow" -> SpotUse(bi("placeCard.berriesMushrooms"), Res.FOOD, "🧺 ${bi("placeCard.gatherFood")}")
        "pond" -> SpotUse(bi("placeCard.quietPlaceListen"), Res.WOOD, "🎧 ${bi("placeCard.listen")}")
        "riverbank" -> SpotUse(bi("placeCard.waterMurmursListen"), Res.WOOD, "🎧 ${bi("placeCard.listen")}")
        // the culture's own landscape at the horizon: what its pack says about it
        TownSpots.HORIZON -> SpotUse(si.lanisce.lani.game.culture.Cultures.current.world.backdrop?.about?.bi() ?: TownSpots.info(id).where, null, "")
        // the hunter's high seat, the charcoal pile in the woods: what they are
        "highseat" -> SpotUse(about ?: bi("placeCard.highSeat"), null, "")
        TownSpots.KOPA -> SpotUse(about ?: bi("placeCard.kopa"), null, "")
        else -> SpotUse(about ?: TownSpots.info(id).where, null, "")
    }
}

/**
 * What a spot's card tells beyond its name, when its culture tells it ([si.lanisce.lani.game.culture.SpotTexts]): a few
 * sentences of its history at the learner's level ([lore]), and its words ([words], of [pack]; [learned] their ids the
 * learner knows), each to look up.
 */
data class SpotStory(
    val lore: si.lanisce.lani.game.scene.Keepers.Lore?,
    val words: List<si.lanisce.lani.data.PackWord> = emptyList(),
    val learned: Set<String> = emptySet(),
    val pack: String? = null,
)

/**
 * Who lives in the house whose room [scenes] is, by name, for the house's card (" · Stari Janez"); nothing for a place that
 * opens anything else, or a room that is anyone's (the kitchen).
 */
internal fun household(scenes: List<SceneSpec>): String {
    val room = scenes.singleOrNull()?.takeIf { Homes.isRoom(it) } ?: return ""
    val names = room.household.mapNotNull { v -> room.people.firstOrNull { it.villager == v }?.name }
    return if (names.isEmpty()) "" else " · " + names.joinToString(", ")
}

/**
 * A small card for a place tapped in the village: its name, who's there now, a way into each scene that
 * opens there, and the building itself (upgrade, repair), the fire's strength, or what the forest and the
 * landscape's spots are for (the practice they open). In a visited town ([guest]) it shows the place and the ways into
 * its scenes only, and that the learner is a guest there: nothing of the host's changes.
 *
 * A spot its culture tells about ([story]: the charcoal pile) shows its history at the learner's level, in the village's
 * language with its meaning below, every word of it to look up ([onWord]: the word, its sentence and what that means), and
 * its words, each to look up, with "📚 Learn the words" ([onLearn], none in a visited town).
 */
@Composable
fun PlaceCard(
    tap: PlaceTap,
    state: GameState,
    here: List<TownMarker>,
    scenes: List<SceneSpec>,
    onMarker: (TownMarker) -> Unit,
    onScene: (SceneSpec) -> Unit,
    onBuilding: () -> Unit,
    onFire: () -> Unit,
    onGather: (Res) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onProject: () -> Unit = {},
    guest: Boolean = false,
    story: SpotStory? = null,
    onWord: (word: String, line: String, meant: String) -> Unit = { _, _, _ -> },
    onLearn: (() -> Unit)? = null,
    speaker: si.lanisce.lani.data.Speaker? = null,
) {
    val place = tap.place
    val use = (tap as? PlaceTap.Spot)?.let { spotUse(it.id) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(place.emoji(), MaterialTheme.colorScheme.primaryContainer, size = 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    val sub = when (tap) {
                        is PlaceTap.Built -> "${bi("common.level")} ${tap.building.level}" + household(scenes) + if (tap.building.damaged) "  ⚠️ ${bi("common.damaged")}" else ""
                        PlaceTap.Fire -> if (guest) "🔥 ${bi("visit.fireWarm")}" else "🔥 ${state.fire} / 100" + if (state.fire < 25) " · ${bi("placeCard.dying")}" else ""
                        PlaceTap.Forest -> bi("placeCard.woodAnimalsQuiet")
                        is PlaceTap.Spot -> use!!.sub
                        is PlaceTap.Landmark -> si.lanisce.lani.game.Projects.spec(tap.project)?.about.orEmpty()
                    }
                    Text(
                        sub, style = MaterialTheme.typography.bodySmall,
                        color = if (tap is PlaceTap.Built && tap.building.damaged || !guest && tap == PlaceTap.Fire && state.fire < 25) TriglavRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(onClick = onClose, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { EmojiLabel("✕", Labels.CLOSE) }
                }
            }
            // who's there now (the event, happenings), then the requests waiting here as one row of chips
            val now = here.filter { it.kind != TownMarker.Kind.QUEST }
            val asks = here.filter { it.kind == TownMarker.Kind.QUEST }
            if (now.isNotEmpty()) {
                Text(bi("placeCard.hereNow"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                for (m in now.take(3)) {
                    Surface(onClick = { onMarker(m) }, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
                        Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(m.emoji, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(10.dp))
                            Text(m.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("›", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            if (asks.isNotEmpty()) {
                // someone's requests are one chip (their bubble): counted each
                Text("📋 ${bi("placeCard.requests", "asksSize" to asks.sumOf { it.count })}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(asks + now.drop(3), key = { it.id }) { m ->
                        Surface(
                            onClick = { onMarker(m) }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.heightIn(min = 44.dp).widthIn(max = 200.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.emoji)
                                Spacer(Modifier.width(6.dp))
                                Text(m.label.substringBefore(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (story != null && (story.lore != null || story.words.isNotEmpty())) SpotStorySection(story, onWord, if (guest) null else onLearn, speaker)
            for (sc in scenes) {
                FilledTonalButton(onClick = { onScene(sc) }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp).heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                    Text("🚪 ${bi("placeCard.go")}: ${sc.emoji} ${sc.title} ›", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (guest) {
                Text(
                    "🧳 ${bi("visit.guestHere")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            } else when (tap) {
                is PlaceTap.Built -> OutlinedButton(onClick = onBuilding, modifier = Modifier.fillMaxWidth().padding(end = 8.dp).heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                    Text("🔨 ${bi("placeCard.building")} ›")
                }
                PlaceTap.Fire -> OutlinedButton(onClick = onFire, modifier = Modifier.fillMaxWidth().padding(end = 8.dp).heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                    Text("🪵 ${bi("placeCard.feedFire")} ›")
                }
                PlaceTap.Forest -> OutlinedButton(onClick = { onGather(Res.WOOD) }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp).heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                    Text("🎧 ${bi("common.gatherWood")}")
                }
                is PlaceTap.Spot -> use!!.gather?.let { res ->
                    OutlinedButton(onClick = { onGather(res) }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp).heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                        Text(use.action)
                    }
                }
                // the project it is the landmark of
                is PlaceTap.Landmark -> OutlinedButton(onClick = onProject, modifier = Modifier.fillMaxWidth().padding(end = 8.dp).heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                    Text("🏗️ ${bi("common.villageProjects")} ›")
                }
            }
        }
    }
}

/**
 * A spot's history and its words on its card: "📜 Nekoč · Long ago", the story in the village's language (every word to
 * look up, 🔊 to hear it) with its meaning below, then the words as chips (each to look up; ✓ the learned ones) and
 * "📚 Learn the words". Scrolls within the card when it is long, so the map stays in view.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpotStorySection(story: SpotStory, onWord: (String, String, String) -> Unit, onLearn: (() -> Unit)?, speaker: si.lanisce.lani.data.Speaker?) {
    Column(
        Modifier.fillMaxWidth().padding(end = 8.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        story.lore?.let { lore ->
            Text("📜 ${bi("placeCard.history")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.Top) {
                val look: (WordToken) -> Unit = { t -> onWord(t.text, lore.said, lore.meant) }
                Column(
                    Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = if (lore.meant.isBlank()) lore.said else "${lore.said} · ${lore.meant}"
                        lookUpWords(lore.said, look)
                    },
                ) {
                    WordText(lore.said, look, style = MaterialTheme.typography.bodyMedium)
                    if (lore.meant.isNotBlank()) Text(
                        lore.meant, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (speaker != null) SpeakButton(speaker, lore.said, Modifier.padding(start = 4.dp))
            }
        }
        if (story.words.isNotEmpty()) {
            Text("🔤 ${bi("placeCard.words")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (w in story.words) {
                    val known = w.id in story.learned
                    Surface(
                        onClick = { onWord(w.word, w.example ?: w.word, w.exampleMeaning ?: w.meaning) },
                        shape = RoundedCornerShape(50), color = if (known) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "${w.word} · ${w.meaning}" + if (known) " ✓" else "" },
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            w.emoji?.let {
                                Text(it)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(w.word + if (known) " ✓" else "", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
            if (onLearn != null && story.words.any { it.id !in story.learned }) {
                OutlinedButton(onClick = onLearn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                    Text("📚 ${bi("placeCard.learnWords")}")
                }
            }
        }
    }
}
