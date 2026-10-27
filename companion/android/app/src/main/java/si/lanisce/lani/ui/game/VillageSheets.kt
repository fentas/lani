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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.LogEntry
import si.lanisce.lani.game.Moba
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.School
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.Confetti
import si.lanisce.lani.ui.LaniSheet
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

@Composable
internal fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    LaniSheet(onDismissRequest = onDismiss) {
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

/** What the build sheet shows of what the village could build ([BuildSheetLogic.of]). */
data class BuildList(
    /** The cards, what can be built now first: available (or with the neighbours), then with a way on, then short of something. */
    val cards: List<BuildOption>,
    /** What the next age opens: "🔒 Pozneje (🛖 Zaselek): čebelnjak, stolp, kovačnica …", the sheet's quiet last line; null at the last age. */
    val later: String?,
    /** The last free plots are kept for what the next age needs: "🅿️ Prostor ostane za: Cerkev, Lipa", or null. */
    val kept: String?,
    /** Every plot of the age built: a tent can grow, a building can be replaced (each on its card). */
    val full: Boolean,
)

/** What the build sheet shows (companion/GAME.md, "Buildings", "The build sheet"), pure: the tests read it without a screen. */
object BuildSheetLogic {
    /** How many names the "later" line shows before "…". */
    const val LATER_SHOWN = 3

    /**
     * Of [options] (the village's [GameEngine.buildOptions], or what can go up in a building's place), only what can be
     * built now or has a way on, what the next age needs ([check]) first: the age has it, it isn't at its most, and no plot
     * kept for the next age holds it out (that says one line, [BuildList.kept]); with every plot built, what has its way
     * through the plots ([BuildOption.plotWay]) and what takes none (the palisade). What the next age opens is one quiet line ([BuildList.later]), not a card each.
     * [replacing]: the options for a building's place (its sheet says so; no "later" line).
     */
    fun of(s: GameState, options: List<BuildOption>, replacing: Boolean = false, check: AdvanceCheck? = null): BuildList {
        val full = !replacing && si.lanisce.lani.game.Placement.freePlots(s).isEmpty()
        fun open(o: BuildOption): Boolean {
            val spec = Catalog.spec(o.type)
            return s.age >= spec.minAge && (spec.max == null || s.buildings.count { it.type == o.type } < spec.max)
        }
        val kept = options.filter { open(it) && it.type.onPlot && !full && !GameEngine.placeable(s, it.type) && it.room == null }
        val cards = options.filter { o -> open(o) && o !in kept && (!o.type.onPlot || !full || o.plotWay != null) }
            // what the next age needs first (its "🎯 Potrebno za …"), then what can be built now, then what has a way on
            .sortedWith(compareBy({ check == null || !needed(s, check, it.type) }, { !(it.available || it.mobaAvailable) }, { it.room == null && it.plotWay == null }, { it.cost.values.sum() }))
        val next = Age.entries.getOrNull(s.age.ordinal + 1)
        val later = next?.takeIf { !replacing }?.let { a ->
            val types = Catalog.specs.values.filter { it.minAge == a }.map { it.type }
            if (types.isEmpty()) null
            else {
                val names = si.lanisce.lani.game.culture.localized { lang ->
                    types.take(LATER_SHOWN).joinToString(", ") { it.names.of(lang).lowercase() } + if (types.size > LATER_SHOWN) " …" else ""
                }
                "🔒 ${bi("buildSheet.later", "age" to a.names, "list" to names)} ${a.emoji}"
            }
        }
        return BuildList(cards, later, kept.firstNotNullOfOrNull { it.reason }?.let { "🅿️ $it" }, full)
    }

    /** Whether the next age counts [type] and needs more of it than stand: its card says "🎯 Potrebno za Vas". */
    fun needed(s: GameState, check: AdvanceCheck, type: BuildingType): Boolean {
        val next = check.next ?: return false
        return Catalog.ageRules.getValue(next).needs.any { (types, n) -> type in types && s.buildings.count { it.type in types && !it.damaged } < n }
    }
}

/**
 * "🔨 Gradi · Build": only what can be built now or has a way on ([BuildSheetLogic]), each card what it gives and costs,
 * "🎯 Potrebno za Vas" on what the next age needs, then one quiet line for what comes later. [onRoom]: the way on of a card
 * whose person has nowhere to live ([BuildOption.room]: build or upgrade a dwelling, or gather for it), one tap;
 * [onPlotWay]: with every plot built, a card's way through the plots (a dwelling grows, a building not needed is replaced);
 * [onGather]: a card short of something, gathering it. [course]: the school's course of the village's language; while no
 * school stands, a line under the school's card says where the class meets already and what the school brings
 * ([School.buildNote]). [replacing]: the sheet of what goes up in that building's place ("🔁 Nadomesti · Replace").
 */
@Composable
fun BuildSheet(
    s: GameState, options: List<BuildOption>, check: AdvanceCheck, onBuild: (BuildOption, Boolean) -> Unit, onDismiss: () -> Unit,
    onRoom: (si.lanisce.lani.game.RoomWay) -> Unit = {},
    onPlotWay: (BuildOption, si.lanisce.lani.game.PlotWay) -> Unit = { _, _ -> },
    onGather: (Res) -> Unit = {},
    course: si.lanisce.lani.data.Course? = null,
    replacing: Building? = null,
) {
    val plots = PLOTS_PER_AGE[s.age.ordinal]
    // replacing: what can go up on its plot, as if it stood free
    val list = remember(s, options, replacing, check) {
        BuildSheetLogic.of(replacing?.let { r -> s.copy(buildings = s.buildings.filter { it.id != r.id }) } ?: s, options, replacing != null, check)
    }
    // the neighbours' help, once the learner has 🤝 and a building could use it
    var moba by remember { mutableStateOf(false) }
    val canMoba = list.cards.any { it.moba != null }
    Sheet(onDismiss) {
        if (replacing == null) SheetHeader("🔨", bi("common.build"), "${bi("villageSheets.plots")}: ${s.plotsTaken} / $plots")
        else SheetHeader("🔁", "${bi("replace.title", "building" to replacing.type.names)} ${replacing.type.emoji}", bi("replace.note"))
        if (list.full && replacing == null) Text("🧱 ${bi("buildSheet.full")}", style = MaterialTheme.typography.bodyMedium, color = FlameOrange)
        if (canMoba) MobaSwitch(s, on = moba, enabled = true) { moba = it }
        // the class meets already (by the fire, under the linden): said across the sheet after the cards, when the school is one
        val note = if (replacing == null && list.cards.any { it.type == BuildingType.SCHOOL }) School.buildNote(s, course) else null
        val card: @Composable (BuildOption) -> Unit = { o ->
            BuildCard(
                s, o, moba && canMoba, needed = BuildSheetLogic.needed(s, check, o.type), next = check.next, onRoom = onRoom,
                onPlotWay = { w -> onPlotWay(o, w) }, onGather = onGather,
            ) { onBuild(o, moba && o.mobaAvailable) }
        }
        // two cards across on a phone, three on a tablet's wider sheet; the school's line under the school's row
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = if (maxWidth >= 520.dp) 3 else 2
            val at = list.cards.indexOfFirst { it.type == BuildingType.SCHOOL }
            val split = if (note == null || at < 0) list.cards.size else minOf(list.cards.size, at - at % cols + cols)
            LazyVerticalGrid(
                GridCells.Fixed(cols),
                Modifier.fillMaxWidth().heightIn(max = 600.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(list.cards.take(split), key = { it.type }) { o -> card(o) }
                if (note != null) item(key = "school-note", span = { GridItemSpan(maxLineSpan) }) {
                    Surface(color = AlpineGreen.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${BuildingType.SCHOOL.emoji} $note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                items(list.cards.drop(split), key = { it.type }) { o -> card(o) }
                // the plots kept for the next age, then what comes later: quiet lines, no cards
                for ((k, line) in listOfNotNull(list.kept?.let { "kept" to it }, list.later?.let { "later" to it })) item(key = k, span = { GridItemSpan(maxLineSpan) }) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
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

/**
 * One card of the build sheet: its emoji (how many stand), its name in both languages, "🎯 Potrebno za Vas" when the next age
 * needs it ([needed]), what it gives, its price (with the neighbours: what's left, and their 🤝), and, when it can't be
 * built now, why and its way on, one tap: a bed made for whom it brings ([onRoom]), its way through the plots
 * ([onPlotWay]), or what to gather ([onGather]).
 */
@Composable
private fun BuildCard(
    s: GameState, o: BuildOption, moba: Boolean, needed: Boolean, next: Age?, onRoom: (si.lanisce.lani.game.RoomWay) -> Unit,
    onPlotWay: (si.lanisce.lani.game.PlotWay) -> Unit, onGather: (Res) -> Unit, onClick: () -> Unit,
) {
    val count = s.buildings.count { it.type == o.type }
    val withMoba = moba && o.mobaAvailable
    val enabled = o.available || withMoba
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            // what can't be built yet must stay readable: what it does and what's missing are the point
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.type.emoji, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                if (count > 0) Text("×$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(o.type.sl, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(o.type.en, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (needed && next != null) Surface(color = XpGold.copy(alpha = 0.22f), shape = RoundedCornerShape(50)) {
                Text(
                    "🎯 ${bi("buildSheet.needed", "age" to next.names)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), maxLines = 2,
                )
            }
            Text(o.effect, style = MaterialTheme.typography.bodySmall, color = AlpineGreen, fontWeight = FontWeight.SemiBold)
            val m = o.moba.takeIf { moba }
            CostChips(m?.rest ?: o.cost) { s.res(it) }
            m?.let { MobaLine(it) }
            // its way on, one tap: a bed for whom it brings, its way through the plots, else what to gather
            val way: Pair<String, () -> Unit>? = when {
                o.room != null -> roomLabel(o.room) to { onRoom(o.room) }
                o.plotWay != null -> plotWayLabel(o.plotWay) to { onPlotWay(o.plotWay) }
                !enabled -> (m?.rest ?: o.cost).mapValues { (r, n) -> n - s.res(r) }.filterValues { it > 0 }.maxByOrNull { it.value }
                    ?.let { (r, n) -> "${r.emoji} ${bi("buildingCard.gatherMore", "n" to n, "res" to r.emoji)} ›" to { onGather(r) } }
                else -> null
            }
            // why not, when nothing else says it (the chips say what the price lacks)
            if (way == null || o.room != null) o.reason?.takeIf { m == null && !enabled }?.let { Text("🔒 $it", style = MaterialTheme.typography.labelSmall, color = TriglavRed) }
            way?.let { (label, go) ->
                Surface(onClick = go, color = XpGold.copy(alpha = 0.18f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        Text(
                            label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A build card's way through the plots: "⬆️ Preraste: Šotor → Koča ›", or "🔁 Namesto: Šotor ⛺ ›". */
fun plotWayLabel(w: si.lanisce.lani.game.PlotWay): String = when (w) {
    is si.lanisce.lani.game.PlotWay.Grow -> "⬆️ ${bi("buildSheet.growWay", "from" to w.from.name.lowercase(), "a" to w.from.names, "b" to w.to.names)} ›"
    is si.lanisce.lani.game.PlotWay.Replace -> "🔁 ${bi("rebuild.instead", "building" to w.what.names)} ${w.what.emoji} ›"
}

/**
 * A resource's sheet (the HUD's chip): what's in store, what the village uses, "Nabiraj · Gather" it, and [onTrade]: "⇄
 * Zamenjaj · Trade" it for another ([TradeSheet], companion/GAME.md "Trading").
 */
@Composable
fun ResourceSheet(s: GameState, attrs: Attributes, res: Res, onGather: () -> Unit, onDismiss: () -> Unit, onTrade: (() -> Unit)? = null) {
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
            Res.WOOD -> Text("🔥 ${bi("hearths.burn", "wood" to attrs.woodUpkeep)}", style = MaterialTheme.typography.bodyMedium)
            else -> Unit
        }
        Text(bi("villageSheets.everyCorrectSkillAnswer", "skillSlAt" to res.skillAt(), "resEmoji" to res.emoji, "skill" to res.skillName()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton(bi("villageScreen.gather", "resEmoji" to res.emoji), onClick = onGather, color = AlpineGreen)
        onTrade?.let { trade ->
            androidx.compose.material3.OutlinedButton(onClick = trade, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("⇄ ${bi("trade.open")}")
            }
        }
    }
}

@Composable
fun InfoSheet(s: GameState, attrs: Attributes, onDismiss: () -> Unit) {
    Sheet(onDismiss) {
        SheetHeader(s.age.emoji, bi("homeHero.myVillage"), s.age.label())
        InfoRow("👥", bi("villageScreen.villagers"), "${s.villagers} / ${attrs.populationCap}")
        InfoRow("🛡️", bi("villageSheets.defence"), "${attrs.defence}")
        InfoRow(moraleEmoji(s.morale), bi("villageSheets.morale"), "${s.morale} / 100")
        InfoRow("🔥", bi("hearths.title"), HearthsView.of(s, java.time.LocalDate.now()).tile)
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

/**
 * The next age's sheet, the ring's (companion/GAME.md "The village's HUD"): the age and the track of all of them, how far
 * the way to the next one is, ONE action for its next step at the top ([next]: the goal card's, "✨ Napreduj · Advance"
 * once it can), then every step with its count, a bar and its own button, and at its foot the switch for the row of cards
 * under the HUD's bar ([cards], null: none; the ring's long press does the same).
 */
@Composable
fun AgeSheet(
    s: GameState, check: AdvanceCheck, onAdvance: () -> Unit, onGather: (Res) -> Unit, onBuild: () -> Unit, onLearn: () -> Unit,
    onPolish: () -> Unit, onFriends: () -> Unit, onDismiss: () -> Unit,
    next: Pair<String, () -> Unit>? = null, cards: Boolean? = null, onCards: (Boolean) -> Unit = {},
) {
    Sheet(onDismiss) {
        // the age by name, the track shows where it is among them (no second "4 / 6" beside the ring's steps)
        SheetHeader(s.age.emoji, s.age.label())
        AgeTrack(s.age)
        val to = check.next
        if (to == null) {
            Text("🏰 ${bi("villageSheets.villageBecameTown")}", style = MaterialTheme.typography.titleMedium)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bi("villageSheets.nextAgeIs", "nextEmoji" to to.emoji, "nextSl" to to.names), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // the steps done of all, as the ring says them ("4/9"); the bar is how far each has come
                VillageHudLogic.steps(check)?.let { (done, all) ->
                    Text("$done/$all", style = MaterialTheme.typography.titleMedium, color = XpGold, fontWeight = FontWeight.Bold)
                }
            }
            FillBar(check.progress, XpGold)
            // the one thing to do now: advancing once it can, else the next step's own
            if (check.ok) BigButton("✨ ${bi("villageSheets.advanceTo", "nextSl" to to.names)}", onClick = onAdvance, color = XpGold)
            else next?.let { (label, go) ->
                BigButton(label, onClick = go, color = AlpineGreen)
                Text(
                    "${stepsLeft(check.missing.size).replaceFirstChar { it.uppercase() }}:", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        }
        // the row of cards under the HUD's bar, shown or hidden (the learner's choice on this phone)
        cards?.let { on ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("🗂️ ${bi("villageSheets.cardsUnderBar")}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = onCards)
            }
        }
    }
}

/**
 * The one action for a step of the next age, as the ages' sheet says it at its top: "🌾 Naberi hrano · Gather food", "🔨
 * Gradi · Build ⛪ ›", "📚 Uči se novih besed · Learn new words", "🔩 Očisti jih zdaj · Polish them now", "💬 Pomagaj
 * vaščanom … · Help the villagers …".
 */
fun primaryLabel(step: AgeStep): String = when (stepAction(step)) {
    StepAction.GATHER -> step.res?.let { r -> bi("villageScreen.gatherFor", "gatherEmoji" to r.emoji, "lowercase" to r.names.map { it.lowercase() }) } ?: step.label
    StepAction.BUILD -> "🔨 ${bi("common.build")} ${step.emoji} ›"
    StepAction.LEARN -> "📚 ${bi("villageScreen.learnNewWords")}"
    StepAction.POLISH -> "🔩 ${bi("reviewScreen.polishNow", "n" to (step.need - step.have))}"
    StepAction.HELP -> "💬 ${bi("villageSheets.helpAndTalk")}"
    null -> step.label
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
