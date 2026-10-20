package si.lanisce.lani.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Hearth
import si.lanisce.lani.game.HearthKind
import si.lanisce.lani.game.Hearths
import si.lanisce.lani.game.KopaWood
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.TriglavRed
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What the HUD's hearths tile and their sheet show (companion/GAME.md "Hearths", "The village's HUD"), pure: how many of
 * the village's hearths are lit of all, each hearth (lit or out, its wood a day; the forge on its charcoal and the days of
 * it left), the wood a day, the days the store lasts, and the charcoal pile's wood when it is due or coming.
 */
object HearthsView {
    /** One hearth's row: its [emoji], [name], what it is doing ([state]), what it burns a day ([burns]), [lit]. */
    data class Row(val emoji: String, val name: String, val state: String, val burns: String, val lit: Boolean)

    /**
     * The sheet: [lit] of [all] lit, a hearth out for want of wood ([short]), the header's line ([sub]), the [rows], the
     * wood a day ([wood], all lit) and the days the store lasts them ([days]; null: they burn none), the pile's line ([kopa]).
     */
    data class View(val lit: Int, val all: Int, val short: Boolean, val sub: String, val rows: List<Row>, val wood: Int, val days: Int?, val kopa: String?) {
        /** "6/7" */
        val tile: String get() = "$lit/$all"
        val share: Float get() = if (all == 0) 1f else lit.toFloat() / all
    }

    fun of(s: GameState, today: LocalDate): View {
        val list = Hearths.of(s)
        val lit = Hearths.lit(s, today)
        val blown = s.hearths?.blown.orEmpty()
        val short = Hearths.short(s, today)
        val rows = list.map { h -> row(s, h, h.id in lit, h.id in blown, today) }
        val sub = if (short) bi("hearths.goingOut") else bi("hearths.lit", "lit" to lit.size, "all" to list.size)
        return View(lit.size, list.size, short, sub, rows, Hearths.need(s), Hearths.daysOfWood(s), Hearths.kopaWood(s, today)?.let { kopa(it, today) })
    }

    private fun row(s: GameState, h: Hearth, lit: Boolean, blown: Boolean, today: LocalDate): Row {
        val name = h.building?.type?.names?.bi() ?: Hearths.centreName(s.age)
        val emoji = h.building?.type?.emoji ?: h.kind.emoji
        val state = when {
            h.charcoal -> {
                val days = Hearths.charcoal(s, today) ?: 0
                if (lit && days > 0) bi("hearths.onCharcoal", "days" to days) else bi("hearths.noCharcoal")
            }
            blown -> bi("hearths.blown")
            !lit && h.kind == HearthKind.CAMPFIRE -> bi("hearths.smoulders")
            !lit -> bi("hearths.out")
            h.kind == HearthKind.FIRE_RING || h.kind == HearthKind.FIRE_BASKET -> bi("hearths.evening")
            else -> bi("hearths.burning")
        }
        val burns = if (h.charcoal) "♨️" else "−${h.wood} 🪵"
        return Row(emoji, name, state, burns, lit)
    }

    /** The charcoal pile's wood: coming in some days, waited for now, or given. */
    fun kopa(k: KopaWood, today: LocalDate): String {
        val who = k.keeper ?: ""
        return when {
            k.paid -> bi("hearths.kopaPaid", "keeper" to who, "wood" to k.amount)
            k.waiting -> bi("hearths.kopaWaiting", "keeper" to who, "wood" to k.amount)
            else -> bi("hearths.kopaComing", "keeper" to who, "wood" to k.amount, "days" to ChronoUnit.DAYS.between(today, k.from).toInt())
        }
    }
}

/**
 * The hearths' sheet (the HUD's 🔥 tile): every hearth lit or out and its wood a day, the forge on its charcoal, the wood a
 * day and the days it lasts, the charcoal pile's wood when it is due, and "🎧 Nabiraj les · Gather wood".
 */
@Composable
fun HearthsSheet(s: GameState, today: LocalDate, onGather: () -> Unit, onDismiss: () -> Unit) {
    val v = HearthsView.of(s, today)
    Sheet(onDismiss) {
        SheetHeader("🔥", bi("hearths.title"), v.sub)
        FillBar(v.share, if (v.short) TriglavRed else FlameOrange)
        Column {
            for (r in v.rows) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clearAndSetSemantics { contentDescription = "${r.name}: ${r.state}, ${r.burns}" },
                ) {
                    Text(if (r.lit) r.emoji else "❄️", Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge)
                        Text(r.state, style = MaterialTheme.typography.bodySmall, color = if (r.lit) MaterialTheme.colorScheme.onSurfaceVariant else TriglavRed)
                    }
                    Text(r.burns, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Text("🪵 ${bi("hearths.woodDay", "wood" to v.wood)}" + (v.days?.let { " · ${bi("hearths.woodFor", "days" to it)}" } ?: ""), style = MaterialTheme.typography.bodyMedium)
        v.kopa?.let { Text("🔥 $it", style = MaterialTheme.typography.bodyMedium, color = if (v.short) TriglavRed else MaterialTheme.colorScheme.onSurface) }
        Text(bi("hearths.note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton("🎧 ${bi("common.gatherWood")}", onClick = onGather, color = if (v.short) FlameOrange else AlpineGreen)
    }
}
