package si.lanisce.lani.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.Aim
import si.lanisce.lani.data.AimApi
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.TargetNext
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Aims
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.theme.XpGold

/**
 * The aim as the settings show it (companion/GAME.md "A target raised later"), pure: the learner's [level], the [target]
 * the village's step counts with now ([Aims.at]), and the raise [waiting] for the village's next age (null: none).
 */
data class AimShown(val level: String, val target: String, val waiting: TargetNext?) {
    /** The chip marked: the aim the learner will have, the raise waiting, else the target. */
    val selected: String get() = waiting?.level ?: target

    /** The levels offered: those above the learner's ([Aim.choices], as the setup offers them; C2 at C2). */
    val choices: List<String> get() = Aim.choices(level)

    /** "Zdaj: A1 → A2 · Now: A1 → A2". */
    fun now(): String = bi("aim.now", "level" to level, "target" to target)

    /** The section's line in the settings: "A1 → A2", and the raise waiting ("🎯 Od naslednje dobe: B1 · …"). */
    fun summary(): String = "$level → $target" + (waiting?.let { " · 🎯 ${bi("aim.fromNextAge", "level" to it.level)}" } ?: "")

    companion object {
        /** The aim in [d] (the home state's profile) with the village at [age]; null before the learner's data is read. */
        fun of(d: Dashboard?, age: Age): AimShown? = d?.let { AimShown(it.level, Aims.at(it.targetLevel, it.targetNext, age), Aims.waiting(it.targetNext, age)) }

        /**
         * What a chosen aim did, for the banner: "🎯 Cilj B1 velja od naslednje dobe · Aim B1 from the next age" (a raise
         * waiting), "🎯 Cilj je zdaj A2 · Your aim is A2 now" (at once, or already so).
         */
        fun banner(a: AimApi.Aim): String =
            if (a.change == "next_age" && a.next != null) "🎯 ${bi("aim.keptNext", "level" to a.next)}" else "🎯 ${bi("aim.keptNow", "level" to (a.target ?: a.level))}"
    }
}

/**
 * "🎯 Cilj · Aim" in the settings: the learner's level and the aim the village's ages lead to, a raise waiting for the
 * village's next age under it (as the ages' sheet says it), the levels above theirs to choose, and the rule: a higher aim
 * starts with the next age, a lower one at once. A choice goes to the node (POST /learner/target, [AppViewModel.keepAim]),
 * which decides by that rule (at once while there is no village yet); the banner says what it did, and the learner's data
 * is read anew. A parent's on a child's device (the settings' page is gated).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AimSettings(vm: AppViewModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val age = vm.game.state?.age ?: Age.OGENJ
    val shown = AimShown.of(vm.content.dashboard, age)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    if (shown == null) {
        Text("…", style = MaterialTheme.typography.bodyMedium, color = muted) // the learner's data not read yet
        return
    }
    Text(shown.now(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(bi("setup.aimWhat", "words" to Catalog.wordsFor(Age.MESTO, shown.target), "aim" to shown.target), style = MaterialTheme.typography.bodySmall, color = muted)
    Aims.note(shown.waiting, age)?.let { note ->
        Surface(color = XpGold.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(note, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Aims.thenLine(shown.waiting, age)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = muted) }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        for (c in shown.choices) FilterChip(
            selected = c == shown.selected,
            enabled = !busy,
            onClick = {
                if (c == shown.selected) return@FilterChip
                busy = true
                scope.launch {
                    val a = vm.keepAim(c)
                    vm.notices.banner = a?.let(AimShown::banner) ?: "🎯 ${bi("aim.notKept")}"
                    busy = false
                }
            },
            label = { Text(c) },
        )
    }
    Text(bi("aim.rule"), style = MaterialTheme.typography.bodySmall, color = muted)
}
