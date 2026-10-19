package si.lanisce.lani.ui.tutor

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.tutor.ChatGPTOAuth
import si.lanisce.lani.tutor.live.LiveBridge
import si.lanisce.lani.tutor.live.LiveTutor
import si.lanisce.lani.tutor.live.PendingReport
import si.lanisce.lani.tutor.live.StudioJob
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How many items [tutorStudioItems] adds (the chat's list scrolls to its end with them). */
fun studioItemCount(live: LiveTutor): Int =
    (if (live.monthReached && live.settings.monthAllowed == null) 1 else 0) + live.reports.size + live.studio.jobs.count { it.open }

/**
 * The studio and the reports in the tutor chat (docs/plans/05-built/studio.md): each open job as "✍️ Pišem …" with its
 * progress and a ✕, a report waiting for "Send to Lani's discussions?", and at the monthly cap "answer anyway this
 * month". Items of the chat's list, after its messages.
 */
fun LazyListScope.tutorStudioItems(live: LiveTutor) {
    if (live.monthReached && live.settings.monthAllowed == null) item(key = "studio-month") { MonthCard(live) }
    for (p in live.reports) item(key = "report-${p.id}") { ReportCard(p, onSend = { live.answerReport(p.id, true) }, onKeep = { live.answerReport(p.id, false) }) }
    for (j in live.studio.jobs.filter { it.open }) item(key = "job-${j.id}") { JobCard(j) { live.cancelJob(j.id) } }
}

/** One job: "✍️ Pišem … · Writing …", its title, where it is, a ✕. */
@Composable
fun JobCard(j: StudioJob, onCancel: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().testTag("studio-job").semantics { contentDescription = "studio-job:${j.status}:${j.title}" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✍️ ${bi("studio.writing")}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (j.open) TextButton(onClick = onCancel, modifier = Modifier.testTag("studio-cancel")) { Text("✕ ${bi("studio.cancel")}") }
            }
            Text(j.title, style = MaterialTheme.typography.bodyMedium)
            Text(progressLine(j), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (j.status == StudioJob.RUNNING) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

/** Where a job is, in a line: its steps and what it published, or why it waits. */
fun progressLine(j: StudioJob): String {
    val made = if (j.published.isEmpty()) "" else " · " + bi("studio.published", "n" to j.published.map { it.item }.distinct().size)
    return when (j.status) {
        StudioJob.RUNNING -> bi("studio.step", "n" to (j.allCalls + 1)) + made
        StudioJob.BATCH -> bi("studio.batch") + made
        StudioJob.QUEUED -> when {
            j.why?.contains("cap") == true -> bi("studio.cap")
            j.notBefore > System.currentTimeMillis() -> bi("studio.retry", "time" to hhmm.format(Instant.ofEpochMilli(j.notBefore).atZone(ZoneId.systemDefault())))
            j.urgency == StudioJob.WHENEVER && !j.batched -> bi("studio.night")
            else -> bi("studio.queued")
        } + made
        StudioJob.DONE -> "✓ ${bi("studio.done")}$made"
        StudioJob.FAILED -> "✗ ${bi("studio.failed", "why" to (j.why ?: j.error ?: "?").take(120))}"
        else -> bi("studio.cancelled")
    }
}

/** "📣 Send to Lani's discussions?": what would be posted, the discussion that has it already, Send or keep it here. */
@Composable
fun ReportCard(p: PendingReport, onSend: () -> Unit, onKeep: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("report-ask"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("📣 ${bi("studio.report.ask")}", style = MaterialTheme.typography.labelLarge)
            Text(p.title, style = MaterialTheme.typography.bodyMedium)
            Text(p.whatIsWrong.take(400), style = MaterialTheme.typography.bodySmall)
            p.suggestedFix?.let { Text("→ ${it.take(300)}", style = MaterialTheme.typography.bodySmall) }
            Text(
                if (p.foundNumber != null) bi("studio.report.found", "n" to p.foundNumber, "votes" to p.foundUpvotes)
                else bi("studio.report.new", "category" to if (p.kind == "wish") "Ideas" else "Content fixes"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(bi("studio.report.public"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { busy = true; onSend() }, modifier = Modifier.testTag("report-send")) { Text(bi("studio.report.send")) }
                TextButton(enabled = !busy, onClick = { busy = true; onKeep() }) { Text(bi("studio.report.keep")) }
            }
        }
    }
}

/** The monthly cap reached: the learner's own messages only, once they say so. */
@Composable
fun MonthCard(live: LiveTutor) {
    Card(Modifier.fillMaxWidth().testTag("month-cap"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("💶 " + bi("tutorLive.monthReached", "cap" to "$" + "%.2f".format(live.settings.monthlyCap)), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { live.allowMonth() }) { Text(bi("tutorLive.monthAllow")) }
        }
    }
}

/** The studio's jobs in the Tutor section: the open ones and the last few, each with its line. */
@Composable
fun StudioJobs(live: LiveTutor) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LaunchedEffect(Unit) { live.studio.load() }
    Text("✍️ ${bi("studio.title")}", style = MaterialTheme.typography.bodyMedium)
    val shown = live.studio.jobs.filter { it.open } + live.studio.jobs.filter { !it.open }.take(5)
    if (shown.isEmpty()) Text(bi("studio.none"), style = MaterialTheme.typography.bodySmall, color = muted)
    for (j in shown) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(j.title, style = MaterialTheme.typography.bodySmall)
                Text(progressLine(j), style = MaterialTheme.typography.labelSmall, color = muted)
            }
            if (j.open) TextButton(onClick = { live.cancelJob(j.id) }) { Text("✕") }
        }
    }
}

/** "🕘 Older versions" of the notebook: each by when and why, its text on a tap, and "Restore this version". */
@Composable
fun NotebookVersions(live: LiveTutor, onRestored: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var versions by remember { mutableStateOf<List<LiveBridge.NotebookVersion>?>(null) }
    var shown by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = {
        open = !open
        if (open) scope.launch { versions = live.notebookVersions() }
    }, modifier = Modifier.testTag("notebook-versions")) { Text((if (open) "▾ " else "▸ ") + "🕘 ${bi("tutorLive.versions")}") }
    if (!open) return
    val list = versions ?: return Text("…")
    if (list.isEmpty()) Text(bi("tutorLive.versionsNone"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (v in list) {
        TextButton(onClick = { shown = if (shown == v.at) null else v.at }) {
            Text("${when4(v.at)} · ${v.why} · ${v.text.length} ⓒ", style = MaterialTheme.typography.bodySmall)
        }
        if (shown == v.at) {
            Text(v.text.take(4_000), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    val e = live.restoreNotebook(v.at)
                    note = e?.let { "⚠️ $it" } ?: "✓ ${bi("tutorLive.restored")}"
                    if (e == null) {
                        onRestored(v.text)
                        versions = live.notebookVersions()
                        shown = null
                    }
                }
            }, modifier = Modifier.padding(start = 12.dp).testTag("notebook-restore")) { Text("↩️ ${bi("tutorLive.restore")}") }
        }
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

/** An ISO time as "2026-10-19 08:03" in the phone's zone (the text as it came when it isn't one). */
private fun when4(iso: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault(iso)

/**
 * OpenAI's welcome at the first sign-in with the ChatGPT plan (the UI guidelines: "Show a welcome modal only the first
 * time a user signs in to your app with ChatGPT plan usage enabled": "You're using your ChatGPT plan", "Got it"), with
 * "Manage usage" beside it.
 */
@Composable
fun ChatGPTWelcome(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(bi("tutorLive.chatgptWelcomeTitle")) },
        text = { Text(bi("tutorLive.chatgptWelcome")) },
        confirmButton = { Button(onClick = onDismiss, modifier = Modifier.testTag("chatgpt-got-it")) { Text(bi("tutorLive.gotIt")) } },
        dismissButton = {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ChatGPTOAuth.MANAGE_USAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text(bi("tutorLive.manageUsage")) }
        },
    )
}

/** The Tutor section's spend, monthly cap and batches (beside the daily cap). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyCap(live: LiveTutor) {
    val s = live.settings
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(bi("tutorLive.monthlyCap"), style = MaterialTheme.typography.bodySmall, color = muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (cap in MONTH_CAPS) androidx.compose.material3.FilterChip(
            selected = s.monthlyCap == cap, onClick = { live.update { it.copy(monthlyCap = cap) } },
            label = { Text("$" + "%.0f".format(cap)) },
        )
    }
    if (live.monthReached) {
        Text("⚠️ " + bi("tutorLive.monthReached", "cap" to "$" + "%.2f".format(s.monthlyCap)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bi("tutorLive.monthAllow"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = s.monthAllowed != null, onCheckedChange = { live.allowMonth(it) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("🌙 ${bi("studio.batches")}", style = MaterialTheme.typography.bodyMedium)
            Text(bi("studio.batchesWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        androidx.compose.material3.Switch(
            checked = s.batches, onCheckedChange = { on -> live.update { it.copy(batches = on) } },
            modifier = Modifier.semantics { contentDescription = if (s.batches) "Batches: on" else "Batches: off" },
        )
    }
}

private val MONTH_CAPS = listOf(5.0, 10.0, 20.0, 50.0, 100.0)
