package si.lanisce.lani.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.AboutApi
import si.lanisce.lani.l10n.bi

/**
 * "🙂 O meni · About me" in the settings (docs/plans/05-built/first-minutes.md): what the first run left at its defaults,
 * changed here: how the village speaks to the learner, the minutes a day, the level now. A choice goes to the node at once
 * (POST /learner/about, [AppViewModel.keepAbout]); the aim has its own page, the voice is under "Sound and voice". An older
 * node doesn't have it: a line says so. A parent's on a child's device (the settings' page is gated).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutSettings(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var about by remember { mutableStateOf<AboutApi.About?>(null) }
    var asked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        about = vm.aboutNow()
        asked = true
    }
    fun keep(gender: String? = null, goal: Int? = null, level: String? = null) {
        busy = true
        scope.launch {
            val a = vm.keepAbout(gender, goal, level)
            if (a != null) about = a else vm.notices.banner = "⚠️ ${bi("firstRun.notKept")}"
            busy = false
        }
    }
    val a = about
    if (a == null) {
        Text(if (asked) bi("firstRun.notHere") else "…", style = MaterialTheme.typography.bodyMedium, color = muted)
        return
    }
    Text(bi("hosted.gender"), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(a.gender == "female", { keep(gender = "female") }, { Text(bi(if (vm.child) "hosted.asGirl" else "hosted.asWoman")) }, enabled = !busy)
        FilterChip(a.gender == "male", { keep(gender = "male") }, { Text(bi(if (vm.child) "hosted.asBoy" else "hosted.asMan")) }, enabled = !busy)
    }
    Text(bi("hosted.goal"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (g in si.lanisce.lani.hosted.HostedAnswers.GOALS) FilterChip(a.goal == g, { keep(goal = g) }, { Text("$g min") }, enabled = !busy)
    }
    Text(bi("hosted.level"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (l in si.lanisce.lani.hosted.HostedAnswers.LEVELS) FilterChip(a.level == l, { keep(level = l) }, { Text(l) }, enabled = !busy)
    }
    Text(bi("hosted.levelWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
}
