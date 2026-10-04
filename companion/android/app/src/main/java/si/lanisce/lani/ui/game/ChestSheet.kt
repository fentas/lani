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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.Chest
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Readings
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.at
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.Portrait
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.villagers.HeartPink
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import java.time.LocalDate

/** What the chest sheet can do: forge a tool, give, sell or buy a good, host a feast, read. */
class ChestActions(
    val forge: (giver: String) -> Unit,
    val give: (good: String, villager: String) -> Unit,
    val sell: (good: String) -> Unit,
    val feast: () -> Unit,
    /** Buys one good from a seller (the pedlar, the merchant, the market). */
    val buy: (seller: String, good: String) -> Unit = { _, _ -> },
    /** Opens what a tool or a good holds to read ("📖 Preberi · Read": Micka's recipe, Janez's book). */
    val read: (Readings.Readable) -> Unit = {},
    /** "📖 Branje · Reading": the reading corner, everything there is to read. */
    val readings: (() -> Unit)? = null,
    /** "📓 Moj zvezek zgodb · My story notebook": the stories heard by the fire, written down (ui/notebook). */
    val openNotebook: () -> Unit = {},
    /** "🗺️ Zemljevid zaklada · The treasure map": the storyteller's map, kept in the chest until taken. */
    val openTreasure: () -> Unit = {},
)

/**
 * "🧰 Skrinja · The chest" (companion/GAME.md): the help for a moba, the tools the villagers gave (what they do,
 * the smith forging them), the story notebook ([notebook]: the stories heard by the fire, written down; null where the
 * village has no storyteller), the goods (who likes them, give or sell), and the village feast. A friend's next gift isn't listed:
 * it comes as a surprise. [people]: who lives here. [fresh]: what came in since it was last opened ([ChestNews] keys),
 * marked new.
 */
@Composable
fun ChestSheet(
    s: GameState, people: List<Villager>, actions: ChestActions, onDismiss: () -> Unit, fresh: Set<String> = emptySet(),
    notebook: List<si.lanisce.lani.game.scene.NotebookEntry>? = null,
) {
    val today = LocalDate.now()
    Sheet(onDismiss) {
        SheetHeader("🧰", bi("chestSheet.chest"), bi("chestSheet.villagersGiftsGoods"))
        Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HelpCard(s)
            val stalls = remember(s) { Chest.stalls(s, today) }
            // the pedlar is here for the day only: his stall comes first
            if (stalls.any { it.id == Chest.PEDLAR }) BuySection(s, stalls, actions)
            Section("🧰 ${bi("chestSheet.tools")}")
            if (s.chest.tools.isEmpty()) Hint("${bi("chestSheet.friendsGiveTool")}: ${bi("chestSheet.helpThem")}.")
            for ((giver, tool) in s.chest.tools) {
                val line = Catalog.tools[giver] ?: continue
                ToolRow(s, giver, tool.tier, tool.forged, line.name, actions, new = ChestNews.tool(giver) in fresh)
            }
            // the treasures: the storyteller's map while it waits or the hunt is on, and the keepsakes the treasures held
            val map = s.treasure?.takeIf { it.found.isEmpty() }
            val kept = s.treasures.keys.sorted().mapNotNull { si.lanisce.lani.game.Treasure.keepsake(it) }
            if (map != null || kept.isNotEmpty()) {
                Section("🗝️ ${bi("treasure.chest")}")
                if (map != null) Surface(
                    onClick = actions.openTreasure, shape = RoundedCornerShape(12.dp), color = XpGold.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🗺️", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(bi("treasure.title"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Hint(if (map.taken.isEmpty()) bi("treasure.mapWaits") else bi("treasure.bookRow", "to" to map.to))
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                }
                for (k in kept) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${k.name} · ${k.level}", style = MaterialTheme.typography.titleSmall)
                        Hint(bi("treasure.keepsakeEffect", "pct" to (si.lanisce.lani.game.Treasure.KEEPSAKE_BONUS * 100).toInt()))
                    }
                }
            }
            // the story notebook: its own thing, not a reading
            notebook?.let { entries ->
                Section("📓 ${bi("notebook.title")}")
                si.lanisce.lani.ui.notebook.NotebookRow(entries, onClick = actions.openNotebook)
            }
            actions.readings?.let { open ->
                TextButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("📖 ${bi("readings.everything")} ›") }
            }
            // no "gifts waiting": a friend's gift comes as a surprise (Jan)
            Section("🧺 ${bi("chestSheet.goods")}")
            val market = Chest.market(s)
            Hint(
                market?.let { bi("chestSheet.canSellNow", "emoji" to it.emoji, "label" to it.label) }
                    ?: bi("chestSheet.sellMerchantWhenHe"),
            )
            if (s.chest.goods.isEmpty()) Hint(bi("chestSheet.villagersThankGoodsWhen"))
            for ((id, n) in s.chest.goods.toSortedMap()) {
                val good = Catalog.goods[id] ?: continue
                GoodRow(s, good, n, people, today, actions, new = ChestNews.good(id) in fresh)
            }
            if (stalls.none { it.id == Chest.PEDLAR }) BuySection(s, stalls, actions)
            FeastCard(s, today, actions)
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "✨ Novo · New": came in since the chest was last opened. */
@Composable
private fun NewTag() {
    Surface(color = TriglavRed, shape = RoundedCornerShape(50)) {
        Text(
            "✨ ${bi("chestSheet.new")}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 🤝: how much, how it's earned, what a moba does here. */
@Composable
private fun HelpCard(s: GameState) {
    Surface(color = XpGold.copy(alpha = 0.16f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤝 ${s.help}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(bi("common.help"), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "${bi("chestSheet.earnByHelping")}: 📋 ${bi("chestSheet.aRequest")} +${Catalog.HELP_QUEST}, " +
                    "🗣️ ${bi("chestSheet.aTalk")} +${Catalog.HELP_TALK}, 💬 ${bi("chestSheet.aScene")} +${Catalog.HELP_HAPPENING}, " +
                    "👪 ${bi("chestSheet.family")} +${Catalog.HELP_FAMILY}.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "🔨 ${bi("chestSheet.spendMobaNeighboursHelp")}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(ChestLogic.mobaText(s), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToolRow(s: GameState, giver: String, tier: Int, forged: Int, name: String, actions: ChestActions, new: Boolean = false) {
    val line = Catalog.tools.getValue(giver)
    val item = line.name(tier)
    val effect = Chest.effectOf(giver, si.lanisce.lani.game.Tool(tier, forged))
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${item.sl} · ${item.en}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("🎁 $name" + if (line.forge) "  ${Chest.stars(forged)}" else "", style = MaterialTheme.typography.labelMedium, color = HeartPink)
                }
                if (new) NewTag()
            }
            effect?.let { Text(Chest.effectHelp(it), style = MaterialTheme.typography.bodySmall, color = AlpineGreen, fontWeight = FontWeight.SemiBold) }
            ReadButtons(s, remember(giver, tier) { Readings.ofTool(giver, tier) }, actions)
            Chest.forgeOption(s, giver)?.let { o ->
                Text("⚒️ ${bi("chestSheet.atSmithy")}: ${o.effect}", style = MaterialTheme.typography.bodySmall)
                CostChips(o.cost) { s.res(it) }
                o.good?.let { Text("+ ${it.name.emoji} ${it.name.sl} (${bi("chestSheet.hisPay")})", style = MaterialTheme.typography.labelMedium) }
                o.reason?.let { Text("🔒 $it", style = MaterialTheme.typography.labelSmall, color = TriglavRed) }
                FilledTonalButton(onClick = { actions.forge(giver) }, enabled = o.available, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("⚒️ ${bi("chestSheet.improve")} ${Chest.stars(o.step)}")
                }
            }
        }
    }
}

/** "📖 Preberi · Read" for what a thing holds to read; with two (the better tool's and the first's), each by its title. ✓: read before. */
@Composable
private fun ReadButtons(s: GameState, reads: List<Readings.Readable>, actions: ChestActions) {
    for (r in reads) {
        val label = if (reads.size == 1) bi("reading.read") else "${inTarget("reading.read")}: ${r.reading.title.target}"
        FilledTonalButton(onClick = { actions.read(r) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("📖 $label" + if (Readings.done(s, r.reading.id)) " ✓" else "")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoodRow(s: GameState, good: si.lanisce.lani.game.GoodSpec, n: Int, people: List<Villager>, today: LocalDate, actions: ChestActions, new: Boolean = false) {
    val likers = remember(s, good, people) { ChestLogic.likers(s, good.id, people, today) }
    val price = Chest.price(s, good.id)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(good.name.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${good.name.sl} ×$n", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(good.name.en, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (new) {
                    NewTag()
                    Spacer(Modifier.width(8.dp))
                }
                Text("💰 ${good.value}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ReadButtons(s, remember(good.id) { listOfNotNull(Readings.ofGood(good.id)) }, actions)
            if (likers.isEmpty()) Hint(bi("chestSheet.nobodyHereFond"))
            else {
                Text("❤️ ${bi("chestSheet.giveSomeoneWhoLikes", "GIFT_POINTS" to Catalog.GIFT_POINTS)}:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (l in likers) FilledTonalButton(onClick = { actions.give(good.id, l.id) }, enabled = l.blocked == null, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("🎁 ${l.emoji} ${l.name}")
                    }
                }
                likers.firstOrNull { it.blocked != null }?.let { Hint("${it.name}: ${it.blocked}") }
            }
            if (price != null) FilledTonalButton(onClick = { actions.sell(good.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("${Chest.market(s)?.emoji ?: "🧳"} ${bi("chestSheet.sell")} → +${price.second} ${price.first.emoji}")
            }
        }
    }
}

/**
 * "🛒 Kupi · Buy": who sells today (the pedlar on his day, the merchant while he's here, the market from Trg) and what,
 * at twice a good's value in 🌾 and 🪵, a few of each a day.
 */
@Composable
private fun BuySection(s: GameState, stalls: List<si.lanisce.lani.game.Stall>, actions: ChestActions) {
    Section("🛒 ${bi("chestSheet.buy")}")
    if (stalls.isEmpty()) {
        Hint(bi("chestSheet.canBuyFromPedlar"))
        return
    }
    for (stall in stalls) {
        Text("${stall.emoji} ${stall.label}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (stall.id == Chest.PEDLAR) {
            // the krošnjar himself, under his krošnja, calling his wares
            val pedlar = Surprises.strangers.getValue(Surprises.PEDLAR)
            // his call for the time of day ("Dober dan!" by day, "Dober večer!" in the evening)
            val call = pedlar.lines.greet.at(TimeOfDay.now()).firstOrNull()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Portrait(pedlar.art, Pose.TALK, Modifier.size(64.dp), px = 40, seed = pedlar.id.hashCode())
                Spacer(Modifier.width(10.dp))
                Column {
                    call?.let { Text("»${it.target}«", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                    call?.let { Text(it.base, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Hint(bi("chestSheet.everyoneLovesRareGood", "RARE_GIFT_POINTS" to Catalog.RARE_GIFT_POINTS))
        }
        for (o in stall.offers) OfferRow(s, o, actions)
    }
}

@Composable
private fun OfferRow(s: GameState, o: si.lanisce.lani.game.Offer, actions: ChestActions) {
    val blocked = Chest.buyBlocker(s, o)
    Surface(color = if (o.good.rare) XpGold.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.good.name.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.good.name.sl + if (o.good.rare) "  ✨ ${bi("chestSheet.rare")}" else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(o.good.name.en, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("×${o.left}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CostChips(o.price) { s.res(it) }
            blocked?.let { Text("🔒 $it", style = MaterialTheme.typography.labelSmall, color = TriglavRed) }
            FilledTonalButton(onClick = { actions.buy(o.seller, o.good.id) }, enabled = blocked == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("🛒 ${bi("chestSheet.buy")}")
            }
        }
    }
}

/** "🎪 Veselica · A village feast": once a week under the linden, from Zaselek. */
@Composable
private fun FeastCard(s: GameState, today: LocalDate, actions: ChestActions) {
    val o = Chest.feastOption(s, today) ?: return
    Section("🎪 ${bi("chestSheet.villageFeast")}")
    Surface(color = XpGold.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${bi("chestSheet.onceAWeek")}. +${Catalog.FEAST_MORALE} 😊, +${o.points} ♥ ${bi("chestSheet.withEveryone")}",
                style = MaterialTheme.typography.bodySmall,
            )
            CostChips(o.cost) { s.res(it) }
            if (o.goods.isNotEmpty()) Text("${bi("chestSheet.tables")}: " + o.goods.joinToString(" ") { it.name.emoji } + " (+1 ♥ ${bi("chestSheet.each")})", style = MaterialTheme.typography.labelMedium)
            else Hint(bi("chestSheet.treatsFromChestMake"))
            o.reason?.let { Text("🔒 $it", style = MaterialTheme.typography.labelSmall, color = TriglavRed) }
            BigButton("🎪 ${bi("chestSheet.hostFeast")}", onClick = actions.feast, enabled = o.available, color = XpGold)
        }
    }
}
