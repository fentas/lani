package si.lanisce.lani.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.AgeStep
import si.lanisce.lani.game.Attributes
import si.lanisce.lani.game.BuildOption
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.LogEntry
import si.lanisce.lani.game.Moba
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.Res
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.Confetti
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.bi
import java.time.Instant
import kotlin.math.min
import kotlin.math.roundToInt
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
internal fun SheetHeader(emoji: String, title: String, sub: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * What can be built. [onRoom]: the way on of a card whose person has nowhere to live ([BuildOption.room]: build or upgrade a
 * dwelling, or gather for it), one tap.
 */
@Composable
fun BuildSheet(
    s: GameState, options: List<BuildOption>, check: AdvanceCheck, onBuild: (BuildOption, Boolean) -> Unit, onAges: () -> Unit, onDismiss: () -> Unit,
    onRoom: (si.lanisce.lani.game.RoomWay) -> Unit = {},
) {
    val plots = PLOTS_PER_AGE[s.age.ordinal]
    // the neighbours' help, once the learner has 🤝 and a building could use it
    var moba by remember { mutableStateOf(false) }
    val canMoba = options.any { it.moba != null }
    Sheet(onDismiss) {
        SheetHeader("🔨", bi("common.build"), "${bi("villageSheets.plots")}: ${s.plotsTaken} / $plots")
        val next = check.next
        if (s.plotsTaken >= plots && next != null) {
            Text(
                bi("villageSheets.allPlotsTaken", "nextEmoji" to next.emoji, "nextSl" to next.names, "lowercase" to next.names.map { it.lowercase() }),
                style = MaterialTheme.typography.bodyMedium, color = FlameOrange,
            )
        }
        if (next != null) NextAgeRow(check, onAges)
        MobaSwitch(s, on = moba && canMoba, enabled = canMoba) { moba = it }
        val sorted = options.sortedWith(compareBy({ !(it.available || (moba && it.mobaAvailable)) }, { it.cost.values.sum() }))
        LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.fillMaxWidth().heightIn(max = 560.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(sorted, key = { it.type }) { o -> BuildCard(s, o, moba && canMoba, onRoom) { onBuild(o, moba && o.mobaAvailable) } }
        }
    }
}

/**
 * "🤝 Moba: sosedje pomagajo · the neighbours help": a switch over the build cards; with it on, the cards show what
 * the neighbours bring and what it costs in 🤝. Explains how to get 🤝 while there are none.
 */
@Composable
private fun MobaSwitch(s: GameState, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Surface(color = XpGold.copy(alpha = if (on) 0.24f else 0.12f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("🤝 ${bi("villageSheets.neighboursHelp")}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (s.help > 0) "${bi("villageSheets.youHaveHelp", "help" to s.help)}. ${ChestLogic.mobaText(s)}"
                    else bi("villageSheets.helpVillagersEarnRequest"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = on, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

/** A moba's line on a card or a sheet: "🤝 −35 🪵 −25 🪨 za 8 🤝 · for 8 🤝". */
@Composable
private fun MobaLine(m: Moba) {
    Text("🤝 ${m.text}", style = MaterialTheme.typography.labelMedium, color = XpGold, fontWeight = FontWeight.Bold)
}

@Composable
private fun BuildCard(s: GameState, o: BuildOption, moba: Boolean, onRoom: (si.lanisce.lani.game.RoomWay) -> Unit, onClick: () -> Unit) {
    val count = s.buildings.count { it.type == o.type }
    val withMoba = moba && o.mobaAvailable
    val enabled = o.available || withMoba
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            // A locked building must stay readable: what it does and what's missing are the point.
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.type.emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.alpha(if (enabled) 1f else 0.45f))
                Spacer(Modifier.weight(1f))
                if (count > 0) Text("×$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(o.type.sl, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(o.type.en, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(o.effect, style = MaterialTheme.typography.bodySmall, color = AlpineGreen, fontWeight = FontWeight.SemiBold)
            val m = o.moba.takeIf { moba }
            CostChips(m?.rest ?: o.cost) { s.res(it) }
            m?.let { MobaLine(it) }
            // with the neighbours the price is the rest: its chips (red where short) say what's missing
            o.reason?.takeIf { m == null }?.let { Text("🔒 $it", style = MaterialTheme.typography.labelSmall, color = TriglavRed) }
            // its person has nowhere to live: the way on, one tap (build or upgrade a dwelling, or gather for it)
            o.room?.let { w ->
                Surface(onClick = { onRoom(w) }, color = XpGold.copy(alpha = 0.18f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        roomLabel(w), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * A building's card: its effect, its repair and its next level: the resources (with the neighbours' help) and the
 * building's words it asks for ([words], companion/GAME.md "Upgrades": [UpgradeWordsCard], its "🎯 Vadi jih zdaj ·
 * Practise them now" [onPractise]); [onMove]: "↔ Premakni · Move" to another plot (the plot chooser, free of charge), null
 * for a building on no plot (the palisade, the tent at the pond) or with nowhere to go.
 */
@Composable
fun BuildingSheet(
    s: GameState, b: Building, option: BuildOption?, check: AdvanceCheck,
    onRepair: (withMoba: Boolean) -> Unit, onUpgrade: (withMoba: Boolean) -> Unit, onAges: () -> Unit, onDismiss: () -> Unit,
    onMove: (() -> Unit)? = null,
    words: si.lanisce.lani.game.UpgradeWords? = null, onPractise: () -> Unit = {},
) {
    val cost = if (b.damaged) GameEngine.repairCost(s, b.id) else emptyMap()
    val canPay = cost.all { (r, n) -> s.res(r) >= n }
    val repairMoba = if (b.damaged) GameEngine.repairMoba(s, b.id) else null
    Sheet(onDismiss) {
        SheetHeader(b.type.emoji, b.type.label(), "${bi("common.level")} ${b.level}" +(if (b.builtAt > 0) " · ${date(b.builtAt)}" else ""))
        option?.let { Text("✨ ${it.effect}", style = MaterialTheme.typography.titleMedium, color = if (b.damaged) MaterialTheme.colorScheme.onSurfaceVariant else AlpineGreen) }
        if (b.damaged) {
            Surface(color = TriglavRed.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠️ ${bi("villageSheets.damaged")}", color = TriglavRed, fontWeight = FontWeight.Bold)
                    Text(bi("villageSheets.givesNoEffectUntil"), style = MaterialTheme.typography.bodyMedium)
                    CostChips(cost) { s.res(it) }
                    repairMoba?.let { MobaLine(it) }
                }
            }
            BigButton(if (canPay) "🛠️ ${bi("villageSheets.repair")}" else "🛠️ ${bi("villageSheets.notEnoughYet")}", onClick = { onRepair(false) }, enabled = canPay, color = AlpineGreen)
            repairMoba?.let { m ->
                BigButton("🤝 ${bi("villageSheets.repairMobaHelp", "help" to m.help)}", onClick = { onRepair(true) }, enabled = m.affordable, color = XpGold)
            }
        } else {
            Text("✅ ${bi("villageSheets.goodShape")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val up = GameEngine.upgradeOption(s, b.id, words)
        if (up == null) {
            if (!b.damaged) Text("⭐ ${bi("villageSheets.topLevel")}", style = MaterialTheme.typography.bodyMedium, color = XpGold)
        } else if (!b.damaged) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⬆️ ${bi("villageSheets.levelTo", "toLevel" to up.toLevel)}: ${up.effect}", fontWeight = FontWeight.Bold)
                    CostChips(up.cost) { s.res(it) }
                    up.moba?.let { MobaLine(it) }
                    up.reason?.let { Text("🔒 $it", style = MaterialTheme.typography.labelSmall, color = TriglavRed) }
                }
            }
            // knowledge builds: the building's words it asks for, and the run of the missing ones, one tap
            up.words?.let { UpgradeWordsCard(it, onPractise) }
            // locked by the age: show the way there, one tap from its steps
            val needs = Catalog.upgradeAge(up.toLevel)
            if (s.age < needs) {
                if (needs == check.next) NextAgeRow(check, onAges)
                else Surface(onClick = onAges, color = Color.Transparent, shape = MaterialTheme.shapes.small) {
                    Text(
                        "${needs.emoji} ${needs.sl} pride po ${check.next?.let { "${it.emoji} ${it.sl}" } ?: ""} · ${needs.en} comes after the ${check.next?.en?.lowercase() ?: ""} ›",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
            BigButton("⬆️ ${bi("villageSheets.upgrade")}", onClick = { onUpgrade(false) }, enabled = up.available)
            up.moba?.let { m ->
                // the neighbours bring timber and stone, not the words
                BigButton("🤝 ${bi("villageSheets.upgradeMobaHelp", "help" to m.help)}", onClick = { onUpgrade(true) }, enabled = up.mobaAvailable, color = XpGold)
            }
        }
        onMove?.let { move ->
            BigButton("↔ ${bi("villageSheets.move")}", onClick = move, color = MaterialTheme.colorScheme.secondaryContainer)
            Text("🆓 ${bi("plotChooser.moveFree")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ResourceSheet(s: GameState, attrs: Attributes, res: Res, onGather: () -> Unit, onDismiss: () -> Unit) {
    val cap = attrs.caps[res]
    val prod = attrs.production[res] ?: 1f
    Sheet(onDismiss) {
        SheetHeader(res.emoji, res.label(), bi("villageSheets.earnedByPractisingSkill", "skillSl" to res.skillName(), "skill" to res.skillName()))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${s.res(res)}", style = MaterialTheme.typography.displaySmall, color = res.color())
            cap?.let { Text(" / $it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        cap?.let { FillBar(s.res(res).toFloat() / it.coerceAtLeast(1), res.color()) }
        if (cap != null && s.res(res) >= cap) Text(bi("villageSheets.storageFullBuildRaise"), style = MaterialTheme.typography.bodySmall, color = FlameOrange)
        if (prod > 1f) Text("⚙️ ×${"%.1f".format(prod)} ${bi("villageSheets.fromBuildings")}", style = MaterialTheme.typography.bodyMedium)
        when (res) {
            Res.FOOD -> Text("🍲 ${bi("villageSheets.villagersEat", "foodUpkeep" to attrs.foodUpkeep)}", style = MaterialTheme.typography.bodyMedium)
            Res.WOOD -> Text("🔥 ${bi("villageSheets.fireBurns", "woodUpkeep" to attrs.woodUpkeep)}", style = MaterialTheme.typography.bodyMedium)
            else -> Unit
        }
        Text(bi("villageSheets.everyCorrectSkillAnswer", "skillSlAt" to res.skillAt(), "resEmoji" to res.emoji, "skill" to res.skillName()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton(bi("villageScreen.gather", "resEmoji" to res.emoji), onClick = onGather, color = AlpineGreen)
    }
}

@Composable
fun FireSheet(s: GameState, attrs: Attributes, onGather: () -> Unit, onDismiss: () -> Unit) {
    Sheet(onDismiss) {
        SheetHeader("🔥", bi("common.fire"), if (s.fire < 25) bi("villageSheets.dying") else bi("villageSheets.burningBright"))
        FillBar(s.fire / 100f, if (s.fire < 25) TriglavRed else FlameOrange)
        Text("${s.fire} / 100", style = MaterialTheme.typography.titleMedium)
        Text(
            bi("villageSheets.fireBurnsWithout", "woodUpkeep" to attrs.woodUpkeep),
            style = MaterialTheme.typography.bodyMedium,
        )
        BigButton("🎧 ${bi("common.gatherWood")}", onClick = onGather, color = FlameOrange)
    }
}

@Composable
fun InfoSheet(s: GameState, attrs: Attributes, onDismiss: () -> Unit) {
    Sheet(onDismiss) {
        SheetHeader(s.age.emoji, bi("homeHero.myVillage"), s.age.label())
        InfoRow("👥", bi("villageScreen.villagers"), "${s.villagers} / ${attrs.populationCap}")
        InfoRow("🛡️", bi("villageSheets.defence"), "${attrs.defence}")
        InfoRow(moraleEmoji(s.morale), bi("villageSheets.morale"), "${s.morale} / 100")
        InfoRow("🔥", bi("villageScreen.fire"), "${s.fire} / 100")
        InfoRow("🍲", bi("villageSheets.foodDay"), "−${attrs.foodUpkeep} 🌾")
        InfoRow("🪵", bi("villageSheets.woodDay"), "−${attrs.woodUpkeep} 🪵")
        InfoRow("🏆", bi("villageSheets.eventsWon"), "${s.stats.eventsWon} / ${s.stats.eventsWon + s.stats.eventsLost}")
        InfoRow("📋", bi("villageSheets.questsDone"), "${s.stats.questsDone}")
        InfoRow("🤝", bi("common.help"), "${s.help}")
        InfoRow("💞", bi("common.friends"), "${si.lanisce.lani.game.villagers.Bonds.friends(s)}")
        Text(
            bi("villageSheets.defenceHelpsAgainstAttacks"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(emoji: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(emoji, Modifier.width(32.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
fun AgeSheet(
    s: GameState, check: AdvanceCheck, onAdvance: () -> Unit, onGather: (Res) -> Unit, onBuild: () -> Unit, onLearn: () -> Unit,
    onPolish: () -> Unit, onFriends: () -> Unit, onDismiss: () -> Unit,
) {
    Sheet(onDismiss) {
        SheetHeader(s.age.emoji, s.age.label(), bi("villageSheets.ageOf", "ordinal" to (s.age.ordinal + 1), "entriesSize" to Age.entries.size))
        AgeTrack(s.age)
        val next = check.next
        if (next == null) {
            Text("🏰 ${bi("villageSheets.villageBecameTown")}", style = MaterialTheme.typography.titleMedium)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Do ${next.emoji} ${next.label()}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${(check.progress * 100).roundToInt()} %", style = MaterialTheme.typography.titleMedium, color = XpGold, fontWeight = FontWeight.Bold)
            }
            FillBar(check.progress, XpGold)
            for (step in check.steps) {
                val act: Pair<String, () -> Unit>? = when (stepAction(step)) {
                    StepAction.GATHER -> bi("villageSheets.gather", "stepEmoji" to step.emoji) to { if (step.res != null) onGather(step.res) }
                    StepAction.BUILD -> "🔨 ${bi("common.build")}" to onBuild
                    StepAction.LEARN -> "📚 ${bi("common.learn")}" to onLearn
                    StepAction.POLISH -> "🔩 ${bi("villageSheets.polish")}" to onPolish
                    StepAction.HELP -> "💬 ${bi("villageSheets.help")}" to onFriends
                    null -> null
                }
                StepRow(step, act)
                if (step.kind == AgeStep.Kind.FRIENDS && !step.done) Text(
                    "${bi("villageSheets.friend")}: ♥♥ ${bi("villageSheets.orMore")}. ${bi("villageSheets.helpAndTalk")}.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 32.dp),
                )
            }
            BigButton(
                if (check.ok) "✨ ${bi("villageSheets.advanceTo", "nextSl" to next.names)}" else "🔒 ${stepsLeft(check.missing.size).replaceFirstChar { it.uppercase() }}",
                onClick = onAdvance, enabled = check.ok, color = XpGold,
            )
        }
    }
}

/**
 * One requirement of the next age: what it is, how much is there of how much, a bar, and a button to work on it. The
 * rusty words' label says how many there are, so their row shows no count.
 */
@Composable
private fun StepRow(step: AgeStep, act: Pair<String, () -> Unit>?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(if (step.done) "✅" else step.emoji, Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    step.label + if (step.broken) " 🔧 ${bi("villageSheets.repairIt")}" else "",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (step.kind != AgeStep.Kind.RUSTY) Text(
                    "${min(step.have, step.need)} / ${step.need}", style = MaterialTheme.typography.labelLarge,
                    color = if (step.done) AlpineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FillBar(step.share, if (step.done) AlpineGreen else XpGold, Modifier.height(6.dp))
        }
        if (act != null) {
            Spacer(Modifier.width(10.dp))
            Surface(onClick = act.second, color = AlpineGreen.copy(alpha = 0.16f), shape = MaterialTheme.shapes.small, modifier = Modifier.heightIn(min = 40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(act.first, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).width(72.dp))
                }
            }
        }
    }
}

/**
 * The way to the next age in one row: its name, how far along (a bar and the steps still to go, or ready),
 * opening its steps ([onClick]). For the build sheet, a building's locked upgrade and the goal card.
 */
@Composable
fun NextAgeRow(check: AdvanceCheck, onClick: () -> Unit) {
    val next = check.next ?: return
    Surface(onClick = onClick, color = XpGold.copy(alpha = if (check.ok) 0.3f else 0.14f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bi("villageSheets.nextAgeIs", "nextEmoji" to next.emoji, "nextSl" to next.names), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${(check.progress * 100).roundToInt()} %", style = MaterialTheme.typography.labelLarge, color = XpGold, fontWeight = FontWeight.Bold)
            }
            FillBar(check.progress, XpGold, Modifier.height(8.dp))
            Text(
                if (check.ok) "✨ ${bi("villageSheets.readyAdvance")} ›" else "${stepsLeft(check.missing.size)} · ${bi("villageSheets.seeWhatsMissing")} ›",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AgeTrack(current: Age) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Age.entries.forEachIndexed { i, a ->
            val reached = a.ordinal <= current.ordinal
            Box(
                Modifier.size(if (a == current) 44.dp else 34.dp).clip(CircleShape)
                    .background(if (reached) XpGold.copy(alpha = if (a == current) 0.9f else 0.4f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text(a.emoji, Modifier.alpha(if (reached) 1f else 0.4f)) }
            if (i < Age.entries.size - 1) {
                Box(Modifier.weight(1f).height(3.dp).background(if (a.ordinal < current.ordinal) XpGold else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("d. M. HH:mm")
private fun date(millis: Long): String = dateFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
private fun LogLine(e: LogEntry) {
    Row {
        Text(e.emoji, Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(e.text, style = MaterialTheme.typography.bodyMedium)
            Text(date(e.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "While you were away": what the days in between brought. */
@Composable
fun NewsSheet(news: List<LogEntry>, onDismiss: () -> Unit) {
    Sheet(onDismiss) {
        SheetHeader("🌄", bi("villageSheets.whileWereAway"))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 420.dp)) {
            news.takeLast(8).forEach { LogLine(it) }
        }
        BigButton(bi("common.continue"), onClick = onDismiss)
    }
}

/** Full-screen moment when the settlement reaches a new age. */
@Composable
fun AgeCeremony(age: Age, onDone: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val pop = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    LaunchedEffect(age) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        text.animateTo(1f, tween(900, delayMillis = 350))
    }
    BackHandler(onBack = onDone)
    Box(
        Modifier.fillMaxSize().background(Color(0xE6070D1A))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(age.emoji, style = MaterialTheme.typography.displayLarge, modifier = Modifier.scale(1.8f * pop.value))
            Spacer(Modifier.height(48.dp))
            Text(bi("villageSheets.newAge"), color = XpGold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.alpha(text.value))
            Text("${age.sl} · ${age.en}!", color = Color.White, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.alpha(text.value))
            Spacer(Modifier.height(12.dp))
            Text(
                bi("villageSheets.settlementGrowsBecauseLearn"),
                color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, modifier = Modifier.alpha(text.value),
            )
            Spacer(Modifier.height(36.dp))
            BigButton(bi("common.continue"), onClick = onDone, color = XpGold, modifier = Modifier.alpha(text.value))
        }
        Confetti(key = age)
    }
}
