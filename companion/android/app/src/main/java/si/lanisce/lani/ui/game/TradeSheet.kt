package si.lanisce.lani.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Trade
import si.lanisce.lani.game.TradeDeal
import si.lanisce.lani.game.TradeLimit
import si.lanisce.lani.game.TradeRate
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import java.time.LocalDate

/**
 * "⇄ Zamenjaj · Trade" (companion/GAME.md, "Trading"), from a resource's sheet: what to give ([from] first) and what to get
 * (the scarcest at first), how much (at most half the stock, no more than the other store can take), what it may bring
 * and how the run sets the rate; [onStart] starts its run of both skills ([canSpeak]: the phone can say a listening
 * exercise, else words stand in for them).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TradeSheet(s: GameState, from: Res, canSpeak: Boolean, onStart: (give: Res, get: Res, amount: Int) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    var give by remember { mutableStateOf(from) }
    var get by remember { mutableStateOf(Trade.suggest(s, from) ?: Res.entries.first { it != from }) }
    val limit = Trade.limit(s, give, get, today)
    // the most it can be at first: half the stock, or what the other store can take
    var amount by remember(give, get) { mutableIntStateOf(limit.max) }
    val n = amount.coerceIn(if (limit.max > 0) 1 else 0, limit.max)
    val caps = GameEngine.attributes(s).caps
    Sheet(onDismiss) {
        // scrolls when it doesn't fit (a small screen, large fonts): the button stays reachable
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetHeader("⇄", bi("trade.title"), bi("trade.sub"))
            Text(bi("trade.give"), style = MaterialTheme.typography.titleSmall)
            ResChoice(s, selected = give, of = Res.entries, blocked = { Trade.giveBlocker(s, it, today) }) { r ->
                give = r
                if (get == r) get = Trade.suggest(s, r) ?: Res.entries.first { it != r }
            }
            Text(bi("trade.get"), style = MaterialTheme.typography.titleSmall)
            ResChoice(s, selected = get, of = Res.entries.filter { it != give }, blocked = { r ->
                if (s.res(r) >= caps.getValue(r)) bi("trade.full", "res" to r.names) else null
            }) { get = it }
            val reason = limit.reason
            if (reason != null) {
                Text("🔒 $reason", color = TriglavRed, style = MaterialTheme.typography.bodyMedium)
            } else {
                Amount(give, n, limit) { amount = it }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // "−100 🌾 → +50 … 80 🪵": what goes out, and what comes in after a run under half right, and all right
                        val low = limit.brings(n, limit.base)
                        val high = limit.brings(n, limit.best)
                        Text(
                            "−$n ${give.emoji}  →  +$low${if (high > low) " … $high" else ""} ${get.emoji}",
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { contentDescription = bi("trade.previewSaid", "given" to n, "give" to give.names, "low" to low, "high" to high, "get" to get.names) },
                        )
                        for (line in rateRule(Trade.rate(s, Catalog.TRADE_EXERCISES))) Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val skills = Trade.mix(give, get, canSpeak).distinct()
            Text(
                "📝 ${bi("intro.exercises", "it" to Catalog.TRADE_EXERCISES)}: " +
                    bi("trade.mix", "a" to skills.first().skillName(), "b" to skills.last().skillName(), "same" to if (skills.size == 1) "yes" else "no"),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!canSpeak && (give == Res.WOOD || get == Res.WOOD)) Text("🔇 ${bi("trade.noVoice")}", style = MaterialTheme.typography.bodySmall)
            Text("🗓️ ${bi("trade.onceInfo")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigButton("⇄ ${bi("trade.open")}", onClick = { onStart(give, get, n) }, enabled = limit.available, color = AlpineGreen)
        }
    }
}

/** The four resources (or three) to pick from, each with what's in store; one that can't be picked says why. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResChoice(s: GameState, selected: Res, of: List<Res>, blocked: (Res) -> String?, onPick: (Res) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in of) {
            val why = blocked(r)
            FilterChip(
                selected = r == selected,
                onClick = { onPick(r) },
                enabled = why == null || r == selected,
                label = { Text("${r.emoji} ${r.sl} ${s.res(r)}") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = r.color().copy(alpha = 0.35f)),
                // TalkBack: "Hrana · Food: 340", and why it can't be picked; the chip says itself whether it's selected
                modifier = Modifier.semantics { contentDescription = "${r.label()}: ${s.res(r)}" + (why?.let { ", $it" } ?: "") },
            )
        }
    }
}

/** How much to give: a slider from 1 to [limit]'s most, and steps either side. */
@Composable
private fun Amount(give: Res, n: Int, limit: TradeLimit, onChange: (Int) -> Unit) {
    val max = limit.max
    val step = (max / 10).coerceAtLeast(1)
    val said = bi("trade.amount")
    Text("$said: $n ${give.emoji}", style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onChange((n - step).coerceAtLeast(1)) }, enabled = n > 1,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "−$step ${give.label()}" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("−") }
        if (max > 1) Slider(
            value = n.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(1, max)) },
            valueRange = 1f..max.toFloat(),
            modifier = Modifier.weight(1f).semantics {
                contentDescription = said
                stateDescription = "$n ${give.label()}"
            },
        ) else Text("$n", Modifier.weight(1f), textAlign = TextAlign.Center)
        OutlinedButton(
            onClick = { onChange((n + step).coerceAtMost(max)) }, enabled = n < max,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "+$step ${give.label()}" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("+") }
    }
}

/**
 * How the run sets the rate, before it ([r]: the rate of a run all right): "Tečaj 0,50 · Rate 0.50", "+0,05 za vsak
 * pravilen odgovor od 3/6 · +0.05 for each right answer from 3/6", the market's, the better trades', the most.
 */
fun rateRule(r: TradeRate): List<String> = buildList {
    add(bi("trade.howRate", "base" to Trade.decimal(Catalog.TRADE_BASE), "bonus" to Trade.decimal(Catalog.TRADE_RUN_BONUS / Catalog.TRADE_EXERCISES), "half" to (r.total + 1) / 2, "total" to r.total))
    if (r.market > 0) add("🏪 ${bi("trade.rateMarket", "rate" to Trade.decimal(r.market), "market" to si.lanisce.lani.game.BuildingType.MARKET.names)}")
    if (r.bonus > 0f) add("⚖️ ${bi("trade.rateBetter", "factor" to Trade.factor(1f + r.bonus))}")
    add(bi("trade.rateMax", "rate" to Trade.decimal(Catalog.TRADE_MAX)))
}

/**
 * Why the run gave its rate, after it: "4/6 pravilno · 4/6 right: 0,70", then what made it ("osnova 0,50 · base 0.50",
 * "+0,20 za odgovore · for the answers", or under half right "le osnova · the base only", the market's, the better trades',
 * the most).
 */
fun rateWhy(r: TradeRate): List<String> = buildList {
    add("${bi("trade.right", "correct" to r.correct, "total" to r.total)}: ${Trade.decimal(r.pct).of(L10n.pair.target)}")
    add(bi("trade.rateBase", "rate" to Trade.decimal(Catalog.TRADE_BASE)))
    if (r.run > 0) add(bi("trade.rateAnswers", "rate" to Trade.decimal(r.run)))
    else add(bi("trade.rateUnder", "half" to (r.total + 1) / 2, "total" to r.total))
    if (r.market > 0) add("🏪 ${bi("trade.rateMarket", "rate" to Trade.decimal(r.market), "market" to si.lanisce.lani.game.BuildingType.MARKET.names)}")
    if (r.bonus > 0f) add("⚖️ ${bi("trade.rateBetter", "factor" to Trade.factor(1f + r.bonus))}")
    if (r.capped) add(bi("trade.rateMax", "rate" to Trade.decimal(Catalog.TRADE_MAX)))
}

/**
 * A trade's result card ([d]): what went out and came in as the chips other runs show ("Oddano · Given −100 🌾 → Prejeto ·
 * Received +70 🪵"), the chronicle's line or why nothing was traded ([message]), and why the rate is what it is ([rateWhy]).
 * TalkBack hears it in words: "Oddano 100 Hrana, prejeto 70 Les · Given 100 Food, received 70 Wood".
 */
@Composable
fun TradeCard(d: TradeDeal, message: String?) {
    val ink = Color(0xFF231A0E)
    Surface(color = Color.White.copy(alpha = 0.92f), contentColor = ink, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (d.traded) {
                val said = bi("trade.said", "given" to d.given, "give" to d.give.names, "got" to d.got, "get" to d.get.names)
                Row(
                    Modifier.clearAndSetSemantics { contentDescription = said },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(bi("trade.gave"), style = MaterialTheme.typography.labelMedium)
                        RewardChip(d.give, d.given, delayMs = 300, negative = true)
                    }
                    Text("→", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(bi("trade.got"), style = MaterialTheme.typography.labelMedium)
                        RewardChip(d.get, d.got, delayMs = 300 + CHIP_STAGGER_MS)
                    }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            if (!d.left) for ((i, line) in rateWhy(d.rate).withIndex()) Text(
                line, textAlign = TextAlign.Center,
                style = if (i == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                fontWeight = if (i == 0) FontWeight.Bold else null,
            )
        }
    }
}
