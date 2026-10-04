package si.lanisce.lani.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Help
import si.lanisce.lani.game.PlotChoice
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.XpGold

/**
 * The plot chooser over the village (GAME.md "Buildings", "The learner chooses the plot"): what is being placed ([choice]),
 * and where the village goes back to when it's cancelled ([cancel]: the build sheet, the building's card) and when it's
 * done ([done]: the build sheet again after a dwelling built to make room; else the map, the building going up).
 */
@Immutable
internal data class Choosing(val choice: PlotChoice, val cancel: VillageSheet? = null, val done: VillageSheet? = null)

/** "Parcela 5 · Plot 5": the plots count from 1, the nearest the fire first. */
internal fun plotName(plot: Int): String = bi("plotChooser.plot", "n" to plot + 1)

/** TalkBack's name of [plot] while choosing: the plot, its ★, what stands there to swap with, and whether it's the chosen one. */
internal fun plotLabel(s: GameState, choice: PlotChoice, plot: Int): String {
    val parts = mutableListOf(plotName(plot))
    if (plot == choice.suggested(s)) parts += "★ ${bi("plotChooser.suggested")}"
    s.buildings.firstOrNull { it.type.onPlot && it.plot == plot && it.id != choice.moving }?.let { parts += "⇄ ${it.type.label()}" }
    if (plot == choice.chosen) parts += "✓"
    return parts.joinToString(", ")
}

/**
 * The chooser's bar at the thumb, in place of the village's: what goes where (the building, the plot chosen, its ★, what
 * it swaps with; a new building's price, paid only on "✓", a move free), and "← Nazaj · Back" (nothing spent), "★ Predlog ·
 * Suggested" (on the plot the game suggests) and "✓ Tukaj · Here" (on the plot chosen).
 */
@Composable
internal fun PlotBar(
    s: GameState, choice: PlotChoice, onBack: () -> Unit, onSuggested: () -> Unit, onHere: () -> Unit, modifier: Modifier = Modifier,
) {
    val suggested = choice.suggested(s)
    val chosen = choice.chosen?.takeIf { choice.takes(s, it) }
    val swap = choice.swapWith(s)
    val moving = choice.moving != null
    Column(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(choice.type.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(choice.type.label(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            bi(if (moving) "plotChooser.chooseNew" else "plotChooser.choose"),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // where it goes now: read out as it changes
                Text(
                    chosen?.let { p ->
                        "📍 ${plotName(p)}" + (if (p == suggested) " ★" else "") + (swap?.let { " ⇄ ${it.type.emoji} ${it.type.label()}" } ?: "")
                    } ?: "👆 ${bi("plotChooser.tapPlot")}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    bi(if (moving) "plotChooser.hintMove" else "plotChooser.hint"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (moving) Text("🆓 ${bi("plotChooser.moveFree")}", style = MaterialTheme.typography.labelMedium, color = AlpineGreen, fontWeight = FontWeight.SemiBold)
                else {
                    val cost = Catalog.spec(choice.type).cost
                    CostChips(if (choice.withMoba) Help.moba(s, cost)?.rest ?: cost else cost) { s.res(it) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            BarButton("←", Labels.BACK, color = Color(0xFFF1DDB0), ink = Color(0xFF3A2A16), onClick = onBack, modifier = Modifier.weight(1f))
            BarButton(
                "★", bi("plotChooser.suggested"), color = XpGold, ink = Color(0xFF2B2118), onClick = onSuggested,
                modifier = Modifier.weight(1f), enabled = suggested != null,
            )
            BarButton(
                "✓", bi("plotChooser.here"), color = AlpineGreen, ink = Color.White, onClick = onHere,
                modifier = Modifier.weight(1f), enabled = chosen != null,
            )
        }
    }
}
