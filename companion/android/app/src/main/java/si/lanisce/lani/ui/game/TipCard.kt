package si.lanisce.lani.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.HintVoice
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.GuideFacts
import si.lanisce.lani.game.Guidance
import si.lanisce.lani.game.Tip
import si.lanisce.lani.game.TipAction
import si.lanisce.lani.game.TipPlace
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.XpGold
import java.time.LocalDate

// "💡 Namigi · Tips" on the screens (companion/GAME.md "Guidance"): the one tip of a place ([rememberTip]), its card
// ([TipCard]), its words for an adult and a child ([text], [speech]). The moments and the queue are game/Guidance's.

/**
 * What the screens know for the moments now ([GuideFacts]): the dashboard's reviews, the packs, the chat, the case trainer,
 * the linked towns, the school's next unit, the map's bubbles, what can be built, the scene each place opens, an upgrade's
 * state; in [scene], which one and the things found so far.
 */
fun guideFacts(vm: AppViewModel, s: GameState, today: LocalDate = LocalDate.now(), now: Long = System.currentTimeMillis(), scene: String? = null): GuideFacts {
    val d = vm.content.dashboard
    val cast = vm.villagers.all
    val markers = runCatching { TownMarkers.of(s, vm.scenes.active(s), cast, today) }.getOrDefault(emptyList())
    val options = runCatching { GameEngine.buildOptions(s, cast) { id -> vm.upgradeWords(s, id) } }.getOrDefault(emptyList())
    return GuideFacts(
        today = today, now = now, ready = d != null,
        due = d?.dueCards?.size ?: 0,
        reviewed = d?.reviewMarks?.isNotEmpty() == true,
        packDone = vm.packs.list.firstOrNull { it.total > 0 && it.learned >= it.total && it.festival == null }?.title,
        chatted = vm.chat.messages.any { it.fromMe },
        cases = vm.caseTrainer.available && vm.caseTrainer.open,
        towns = vm.towns.list?.towns?.size ?: 0,
        lesson = si.lanisce.lani.ui.school.lessonIdea(vm)?.unit,
        markers = markers,
        canBuild = options.filter { it.available }.map { it.type }.toSet(),
        scenes = { p -> runCatching { scenesAt(vm, s, p) }.getOrNull() },
        upgrade = { id -> runCatching { GameEngine.upgradeOption(s, id, vm.upgradeWords(s, id), vm.school.gate(s, id)) }.getOrNull() },
        scene = scene,
        found = vm.scenes.found.values.sumOf { it.size },
    )
}

/** The first scene that opens at [p] in [s] now: a building's own (a house's room), else its place's. */
private fun scenesAt(vm: AppViewModel, s: GameState, p: TownPlace): String? = when (p) {
    is TownPlace.At -> (p.id?.let { id -> s.buildings.firstOrNull { it.id == id } } ?: TownPlace.first(s, p.type))
        ?.let { b -> vm.scenes.at(PlaceTap.Built(b, TownPlace.of(b, s)), s).firstOrNull()?.id }
    else -> vm.scenes.at(p.key(), s).firstOrNull()?.id
}

/**
 * The one tip at [place] now ([Guidance.next]), or null: none on a visit, in a quiet moment only when [quiet] (no exercise,
 * dialog, talk, card or sheet open); the tips begin in the village once the dashboard is in ([Guidance.begin]), and a tip
 * shown is kept as shown ([si.lanisce.lani.app.GameController.tipShown]). [scene]: the scene the learner is in.
 */
@Composable
fun rememberTip(vm: AppViewModel, place: TipPlace, quiet: Boolean, scene: String? = null): Tip? {
    val s = vm.game.state ?: return null
    if (si.lanisce.lani.data.VisitWorld.active) return null
    val minute = rememberNow(30_000) / 60_000
    val d = vm.content.dashboard
    val facts = remember(s, d, vm.packs.list, vm.chat.messages.size, vm.scenes.all, vm.scenes.found, vm.villagers.all, vm.school.course, scene, minute) {
        guideFacts(vm, s, scene = scene)
    }
    LaunchedEffect(s.tips.since.isEmpty(), facts.ready) { if (s.tips.since.isEmpty() && facts.ready) vm.game.tipsBegin(facts) }
    val tip = remember(s, facts, place, quiet) { runCatching { Guidance.next(s, facts, place, quiet) }.getOrNull() }
    LaunchedEffect(tip?.moment) { tip?.let(vm.game::tipShown) }
    return tip
}

/**
 * What a tip's button does where it isn't the map's (Home's learning tips, a scene's): their screen. A map tip's action is the
 * village screen's (it opens its sheets): [VillageScreen].
 */
fun runTip(vm: AppViewModel, a: TipAction) {
    when (a) {
        TipAction.Review -> vm.startReview()
        TipAction.Packs -> vm.openPacks()
        TipAction.Chat -> vm.chat.open()
        TipAction.Talk -> vm.openTalk()
        TipAction.Grammar -> vm.openGrammarBook()
        TipAction.Cases -> vm.openCases()
        TipAction.Friends -> vm.openFriends()
        is TipAction.Lesson -> a.unit?.let(vm::openUnit) ?: vm.openSchool()
        is TipAction.Scene -> vm.openScene(a.id, building = a.building)
        is TipAction.Quest -> vm.startQuest(a.id)
        is TipAction.Retry -> vm.practiseRetry(a.id)
        is TipAction.Meet -> vm.villagers.meet(a.id)
        is TipAction.Words -> vm.practiseUpgrade(a.id)
        TipAction.Surprise -> vm.startSurprise()
        is TipAction.Festival -> vm.startFestival(a.id)
        // the village's own: the village opens
        is TipAction.Trade, TipAction.Build, is TipAction.Building, TipAction.Chest, is TipAction.Projects, is TipAction.Villager -> vm.openVillage()
        TipAction.None -> Unit
    }
}

/**
 * A tip's card (companion/GAME.md "Guidance"): "💡 Namig · Tip", its picture, its line (a child's: short, in the base with the
 * course language's word for the thing in bold, bigger), 🔊 to hear it ([voice]: the narrator's voice for the language
 * learned, the phone's for the base; a tap on the line too), ONE button that does it ([onAction]), "👍 Razumem · Got it"
 * ([onGotIt]) and "⏰ Pozneje · Later" ([onLater]: the next day). [onClose]: a ✕ that only folds it away again (the map's
 * bubble stays), null for none. [autoRead]: read it aloud as it opens (a child's, opened by a tap on its bubble).
 */
@Composable
fun TipCard(
    tip: Tip,
    child: Boolean,
    voice: HintVoice?,
    onAction: () -> Unit,
    onGotIt: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    autoRead: Boolean = false,
    elevated: Boolean = true,
) {
    val line = remember(tip, child, L10n.pair) { tip.text(child) }
    val speech = remember(tip, child, L10n.pair) { tip.speech(child) }
    LaunchedEffect(tip.moment) {
        voice?.warm(speech)
        if (autoRead) voice?.play(speech, needsBase = false)
    }
    val playing = voice?.current == speech
    val read = { voice?.toggle(speech, needsBase = false); Unit }
    Surface(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "💡 ${bi("tips.tip")}" },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = if (elevated) 10.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, XpGold.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(tip.emoji, XpGold.copy(alpha = 0.25f), size = if (child) 64 else 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "💡 ${bi("tips.tip")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        marked(line),
                        style = if (child) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.clickable(enabled = voice != null, onClickLabel = bi("gameIntro.readAloud"), onClick = read),
                    )
                }
                if (voice != null) TextButton(
                    onClick = read,
                    modifier = Modifier.size(48.dp).clearAndSetSemantics {
                        contentDescription = if (playing) Labels.STOP else bi("gameIntro.readAloud")
                        onClick { read(); true }
                    },
                ) { Text(if (playing) "⏹" else "🔊", style = MaterialTheme.typography.titleLarge) }
                if (onClose != null) Surface(onClick = onClose, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { si.lanisce.lani.ui.EmojiLabel("✕", Labels.CLOSE) }
                }
            }
            if (tip.action != TipAction.None) {
                BigButton(actionLabel(tip.action), onClick = onAction, color = AlpineGreen, modifier = Modifier.padding(end = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onGotIt, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("👍 ${bi("tips.gotIt")}", fontWeight = if (tip.action == TipAction.None) FontWeight.Bold else null)
                }
                TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("⏰ ${bi("common.later")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The one button's words: what it opens, "target · base". */
fun actionLabel(a: TipAction): String = when (a) {
    is TipAction.Trade -> "⇄ ${bi(a.label)}"
    TipAction.Build -> "🔨 ${bi(a.label)}"
    is TipAction.Building -> when (a.label) {
        "tips.do.upgrade" -> "⬆️ ${bi(a.label)}"
        "tips.do.repair" -> "🛠️ ${bi(a.label)}"
        else -> "🏠 ${bi(a.label)}"
    }
    is TipAction.Words -> "🎯 ${bi(a.label)}"
    is TipAction.Scene -> "🚪 ${bi(a.label)}"
    is TipAction.Lesson -> "▶️ ${bi(a.label)}"
    TipAction.Chest -> "🧰 ${bi(a.label)}"
    is TipAction.Projects -> "🏗️ ${bi(a.label)}"
    is TipAction.Quest -> "💬 ${bi(a.label)}"
    is TipAction.Retry -> "🎯 ${bi(a.label)}"
    is TipAction.Villager -> "👋 ${bi(a.label)}"
    is TipAction.Meet -> "👋 ${bi(a.label)}"
    TipAction.Surprise -> "🎁 ${bi(a.label)}"
    is TipAction.Festival -> "🎉 ${bi(a.label)}"
    TipAction.Review -> "🔁 ${bi(a.label)}"
    TipAction.Packs -> "📚 ${bi(a.label)}"
    TipAction.Chat -> "💬 ${bi(a.label)}"
    TipAction.Talk -> "🗣️ ${bi(a.label)}"
    TipAction.Grammar -> "📖 ${bi(a.label)}"
    TipAction.Cases -> "🧩 ${bi(a.label)}"
    TipAction.Friends -> "🤝 ${bi(a.label)}"
    TipAction.None -> "👍 ${bi(a.label)}"
}
