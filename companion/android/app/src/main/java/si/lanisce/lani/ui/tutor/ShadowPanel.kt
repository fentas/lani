package si.lanisce.lani.ui.tutor

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.app.DevUpload
import si.lanisce.lani.tutor.CallRecord
import si.lanisce.lani.tutor.DayLog
import si.lanisce.lani.tutor.DayStats
import si.lanisce.lani.tutor.Effort
import si.lanisce.lani.tutor.Family
import si.lanisce.lani.tutor.Handling
import si.lanisce.lani.tutor.LaneSpec
import si.lanisce.lani.tutor.Lanes
import si.lanisce.lani.tutor.MatrixSettings
import si.lanisce.lani.tutor.ModelCatalog
import si.lanisce.lani.tutor.Preset
import si.lanisce.lani.tutor.ShadowEvent
import si.lanisce.lani.tutor.ShadowTutor
import si.lanisce.lani.tutor.Task
import si.lanisce.lani.tutor.ToolCallRecord
import si.lanisce.lani.tutor.TurnKind
import si.lanisce.lani.tutor.TurnRecord
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The shadow tutor's debug panel (docs/plans/05-phase-0.md, check 6): its settings (on/off, the key, the model and the
 * effort per task with our recommendation marked, the budget, the thread's limit, "react at once", a QA build's base
 * URL), the day's numbers (by model × effort too), each event with the shadow's runs (model, effort, cost, output,
 * latency, how it replied) and tool calls next to the real tutor's, "also run on" another model at an effort, the
 * notebook, the thread, the export through the share sheet, and [upload]'s "📤 Pošlji vozlišču · Send to the node"
 * (app/DevUpload); the matrix (its switch, the lanes' grid, today's spend per lane) and each event's lane runs. A
 * developer tool, in English.
 */
@Composable
fun ShadowPanel(shadow: ShadowTutor, upload: DevUpload? = null, onDismiss: () -> Unit) {
    // edge to edge (the app is): the panel paints under the system bars, its content kept clear of them
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("shadow-panel"), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🕵️ Shadow tutor", style = MaterialTheme.typography.titleLarge)
                        Text(shadow.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text("✕") }
                }
                HorizontalDivider()
                Body(shadow, upload)
            }
        }
    }
}

private enum class Tab(val label: String) { EVENTS("Events"), NUMBERS("Numbers"), MATRIX("Matrix"), MEMORY("Notebook"), SETTINGS("Settings") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Body(shadow: ShadowTutor, upload: DevUpload?) {
    var tab by remember { mutableStateOf(if (shadow.hasKey && shadow.settings.enabled) Tab.EVENTS else Tab.SETTINGS) }
    var day by remember { mutableStateOf<String?>(null) }
    val today = shadow.today
    var shown by remember { mutableStateOf(today) }
    LaunchedEffect(day, today) { shown = day?.takeIf { it != today.day }?.let { shadow.dayLog(it) } ?: today }
    // the matrix's lanes that day (today's as they run)
    val lanesToday = shadow.matrix.today
    var lanes by remember { mutableStateOf(lanesToday) }
    LaunchedEffect(shown.day, lanesToday) { lanes = if (shown.day == today.day) lanesToday else shadow.laneLogs(shown.day) }
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in Tab.entries) FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }, modifier = Modifier.testTag("shadow-tab-${t.name.lowercase()}"))
        }
        if (tab == Tab.EVENTS || tab == Tab.NUMBERS) DayPicker(shadow, shown.day) { day = it }
        when (tab) {
            Tab.EVENTS -> Events(shadow, shown, lanes)
            Tab.NUMBERS -> Numbers(shadow, shown, upload)
            Tab.MATRIX -> Matrix(shadow)
            Tab.MEMORY -> Memory(shadow)
            Tab.SETTINGS -> Settings(shadow)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayPicker(shadow: ShadowTutor, day: String, pick: (String) -> Unit) {
    val days = remember(shadow.today) { shadow.days().takeLast(14) }
    if (days.size < 2) return
    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in days) FilterChip(selected = d == day, onClick = { pick(d) }, label = { Text(d.substring(5), fontSize = 11.sp) })
    }
}

private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")
private fun at(ms: Long) = clock.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
private fun money(d: Double?) = d?.let { "$" + "%.4f".format(it) } ?: "—"

@Composable
private fun Small(text: String, mono: Boolean = false, bold: Boolean = false) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    fontFamily = if (mono) FontFamily.Monospace else null,
    fontWeight = if (bold) FontWeight.Bold else null,
)

@Composable
private fun Card(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().padding(vertical = 4.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) { content() }

@Composable
private fun Events(shadow: ShadowTutor, log: DayLog, lanes: Map<String, DayLog>) {
    val turns = (log.turns + shadow.queued).distinctBy { it.id }
    val byEvent = turns.flatMap { t -> t.eventIds.map { it to t } }.groupBy({ it.first }, { it.second })
    val loose = turns.filter { it.eventIds.size != 1 }.sortedByDescending { it.queuedAt }
    val matched = log.real.mapNotNull { it.eventId }.toSet()
    val calls = log.calls.groupBy { it.turnId }
    // the matrix's runs of the same events (each lane's turn), by the events they answer
    val laneTurns = (lanes.values.flatMap { it.turns } + shadow.matrix.queued.values.flatten()).distinctBy { it.id }
    val laneCalls = lanes.values.flatMap { it.calls }.groupBy { it.turnId }
    val lanesBy = laneTurns.groupBy { it.eventIds }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { shadow.digestNow() }) { Text("Run the digest now") }
                Small("${log.events.size} event(s), ${turns.size} turn(s)")
            }
        }
        if (loose.isNotEmpty()) {
            item { Small("Digests, closings and restarts", bold = true) }
            items(loose, key = { "loose-" + it.id }) { t ->
                Card {
                    TurnView(shadow, t, calls[t.id].orEmpty())
                    if (t.eventIds.isNotEmpty()) LaneRuns(shadow, lanesBy[t.eventIds].orEmpty(), laneCalls)
                }
            }
        }
        item { Small("Events, newest first", bold = true) }
        items(log.events.sortedByDescending { it.at }, key = { it.id }) { e ->
            Card {
                EventHead(e)
                byEvent[e.id].orEmpty().filter { it.eventIds.size == 1 }.forEach { TurnView(shadow, it, calls[it.id].orEmpty()) }
                log.real.filter { it.eventId == e.id }.forEach { r ->
                    Small("🧑‍🏫 the real tutor · ${at(r.reply.at)}", bold = true)
                    si.lanisce.lani.ui.Markdown(r.reply.text)
                }
                LaneRuns(shadow, lanesBy[listOf(e.id)].orEmpty(), laneCalls)
                if (e.handling != Handling.RECORDED) AlsoRun(shadow, e)
            }
        }
        val unmatched = log.real.filter { it.eventId == null || it.eventId !in log.events.map { e -> e.id } }
        if (unmatched.isNotEmpty()) {
            item { Small("The real tutor's replies matched to no event here", bold = true) }
            items(unmatched, key = { "real-" + it.reply.at + it.reply.conversationId }) { r ->
                Card {
                    Small("🧑‍🏫 ${r.reply.conversationId} · ${at(r.reply.at)}")
                    si.lanisce.lani.ui.Markdown(r.reply.text)
                }
            }
        }
        item { Spacer(Modifier.padding(24.dp)) }
        // keep [matched] read so a new match recomposes
        if (matched.isEmpty() && log.real.isNotEmpty()) item { Small("(no real reply matched yet)") }
    }
}

@Composable
private fun EventHead(e: ShadowEvent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${at(e.at)} · ${e.kind}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Small(e.handling.name.lowercase().replace('_', ' ') + (e.conversationId?.let { " · $it" } ?: ""))
    }
    if (e.text.isNotBlank()) Small(e.text.take(300))
    e.note?.let { Small("ℹ️ $it") }
}

@Composable
private fun TurnView(shadow: ShadowTutor, t: TurnRecord, calls: List<CallRecord> = emptyList()) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Small(
            (t.lane?.let { "🧪 $it · ${t.eventKind}" } ?: ("🕵️ ${t.kind.name.lowercase()} · ${t.eventKind} · ${t.model.ifBlank { "no call" }}" + (t.effort?.let { " · $it" } ?: ""))) +
                " · ${t.status.name.lowercase()} · ${t.callIds.size} call(s)" + (t.endedAt?.let { " · ${at(it)}" } ?: ""),
            bold = true,
        )
        // the run in numbers, for comparing models and efforts on the same event
        val ok = calls.filter { it.error == null }
        if (ok.isNotEmpty()) {
            val out = ok.sumOf { it.usage.output }
            val thinking = ok.mapNotNull { it.thinkingEstimate }.takeIf { it.isNotEmpty() }?.sum()
            val reply = when {
                t.replyFromText -> "reply ✍️ as text"
                t.replies.isNotEmpty() -> "reply ✓ tool"
                t.kind == TurnKind.CLOSING -> "no reply (closing)"
                else -> "no reply"
            }
            Small(
                "${money(ok.sumOf { it.cost ?: 0.0 })} · out ${count(out)}" + (thinking?.let { " (≈${count(it)} thinking)" } ?: "") +
                    " · first token ${ok.first().firstTokenMs ?: "—"} ms · ${count(ok.sumOf { it.totalMs })} ms · $reply",
            )
        }
        t.why?.let { Small("why: $it") }
        t.error?.let { Small("⚠️ $it") }
        t.replies.forEach { r ->
            Small("→ ${r.conversationId}" + (r.data?.let { " · data $it" } ?: ""))
            if (t.replyFromText) Small("✍️ as text, no reply tool")
            si.lanisce.lani.ui.Markdown(r.text)
        }
        if (t.replies.isEmpty() && t.status.name == "DONE") Small("(no reply: the turn ended without one)")
        if (t.toolCalls.isNotEmpty()) {
            Text(
                (if (open) "▾ " else "▸ ") + "${t.toolCalls.size} tool call(s): " + t.toolCalls.joinToString(", ") { it.name + if (it.dryRun) " (dry run)" else "" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { open = !open },
            )
            if (open) t.toolCalls.forEach { ToolView(it) }
        }
        if (t.text.isNotBlank() && open) Small("text outside reply: ${t.text.take(400)}")
        if (t.lane == null && (t.status.name == "FAILED" || t.status.name == "REFUSED")) TextButton(onClick = { shadow.retry(t.id) }) { Text("Queue again") }
    }
}

/** The matrix's runs of an event (or a digest): "▸ 🧪 Matrix: 9 lane(s)", each lane's turn when open. */
@Composable
private fun LaneRuns(shadow: ShadowTutor, turns: List<TurnRecord>, calls: Map<String, List<CallRecord>>) {
    if (turns.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val done = turns.count { it.status == si.lanisce.lani.tutor.TurnStatus.DONE }
    Text(
        (if (open) "▾ " else "▸ ") + "🧪 Matrix: ${turns.size} lane(s), $done done · ${money(turns.sumOf { t -> calls[t.id].orEmpty().sumOf { it.cost ?: 0.0 } })}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { open = !open }.padding(top = 4.dp),
    )
    // in the grid's order: the family, then the effort
    val order = compareBy<TurnRecord>({ Lanes.familyOf(it.model).ordinal }, { Effort.LEVELS.indexOf(it.effort) }, { it.lane })
    if (open) turns.sortedWith(order).forEach { TurnView(shadow, it, calls[it.id].orEmpty()) }
}

@Composable
private fun ToolView(c: ToolCallRecord) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp)) {
        Small("${c.name} · ${c.kind}${if (c.dryRun) " · DRY RUN" else ""}${if (c.isError) " · error" else ""} · ${c.ms} ms", bold = true)
        Small("in: ${c.input.toString().take(600)}", mono = true)
        if (c.errors.isNotEmpty()) Small("schema: ${c.errors.joinToString("; ")}")
        Small("out: ${c.result.take(600)}", mono = true)
    }
}

/** "Also run on…": the event once more on a model at an effort (each its own stateless call, metered), xhigh and max behind "more". */
@Composable
private fun AlsoRun(shadow: ShadowTutor, e: ShadowEvent) {
    var menu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val models = models(shadow)
    val task = Task.of(e.kind)
    val recommended = ModelCatalog(shadow.settings.listed).resolve(task.family)
    Box {
        TextButton(onClick = { menu = true }) { Text("Also run on…") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            models.forEach { m ->
                val levels = ModelCatalog.efforts(m)
                if (levels.isEmpty()) DropdownMenuItem(text = { Text("$m · no effort", fontSize = 13.sp) }, onClick = { menu = false; shadow.alsoRun(e.id, m) })
                levels.filter { more || it !in Effort.MORE }.forEach { level ->
                    val star = if (m == recommended && level == task.effort) " $RECOMMENDED" else ""
                    DropdownMenuItem(text = { Text("$m · $level$star", fontSize = 13.sp) }, onClick = { menu = false; shadow.alsoRun(e.id, m, level) })
                }
            }
            DropdownMenuItem(text = { Text(if (more) "fewer (hide xhigh, max)" else MORE_LEVELS, fontSize = 13.sp) }, onClick = { more = !more })
        }
    }
}

/** The models to pick from: the key's, else the presets' own. */
private fun models(shadow: ShadowTutor): List<String> =
    shadow.settings.listed.map { it.id }.ifEmpty { listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5") }

private fun count(n: Long) = "%,d".format(n)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Numbers(shadow: ShadowTutor, log: DayLog, upload: DevUpload?) {
    val s: DayStats = remember(log, shadow.notebook) { DayStats.of(log, shadow.notebook.text.length) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exported by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Card {
                Small("${log.day}", bold = true)
                Small("calls ${s.calls} · turns ${s.turns} · errors ${s.errors} · refusals ${s.refusals}")
                Small("spend ${money(s.spend)} of \$${"%.2f".format(shadow.settings.budgetPerDay)} a day")
                Small("tokens: input ${s.usage.input} · cache writes 5 min ${s.usage.cacheWrite5m}, 1 h ${s.usage.cacheWrite1h} · cache reads ${s.usage.cacheRead} · output ${s.usage.output}")
                Small("cache writes reported by lifetime (usage.cache_creation) in ${s.cacheBreakdown} call(s), only as the total in ${s.cacheTotalOnly}")
                Small("cold starts: > 5 min ${s.coldStarts5m} · > 60 min ${s.coldStarts60m}")
                Small("latency (median): first token ${s.firstTokenMedianMs ?: "—"} ms · whole ${s.totalMedianMs ?: "—"} ms")
                Small("the thread restarted at: ${s.restarts.joinToString { "$it" }.ifBlank { "—" }} · notebook ${s.notebookChars} characters")
                Small("replies: the shadow's ${s.repliesShadow} (${s.repliesFromText} ✍️ as text, no reply tool) · the real tutor's ${s.repliesReal}")
                Small("prefix: ${shadow.prefixChars} characters (tools and core prompt), ${shadow.toolNames.size} tools")
            }
        }
        if (s.byModelEffort.isNotEmpty()) item {
            Card {
                Small("By model × effort", bold = true)
                Small("output counts thinking; ≈ thinking: the output less what was shown (text, tool input), an estimate")
                s.byModelEffort.forEach { (k, r) ->
                    Small(
                        "$k: ${money(r.spend)} · ${r.calls} call(s), ${r.turns} turn(s) · out ${count(r.output)} (≈${count(r.thinking)} thinking)" +
                            " · replies ${r.repliesByTool} by tool, ${r.repliesFromText} ✍️ as text, ${r.noReply} none",
                    )
                }
            }
        }
        item {
            Card {
                Small("Spend by model", bold = true)
                s.byModel.forEach { (m, d) -> Small("$m: ${money(d)}") }
                Small("Spend by event kind", bold = true)
                s.byKind.forEach { (k, d) -> Small("$k: ${money(d)}") }
                Small("Guide topics fetched", bold = true)
                Small(s.guide.entries.joinToString { "${it.key} ×${it.value}" }.ifBlank { "—" })
            }
        }
        item { Small("Calls", bold = true) }
        items(log.calls.sortedByDescending { it.at }, key = { it.id }) { c ->
            Card {
                Small(
                    "${at(c.at)} · ${c.model}" + (c.effort?.let { " · $it" + if (c.effortByMessage) " (by message)" else "" } ?: "") +
                        " · ${c.eventKind} · ${c.thread} · ${c.stopReason ?: "—"} · ${money(c.cost)}",
                    bold = true,
                )
                Small(
                    "in ${c.usage.input} · w5m ${c.usage.cacheWrite5m} · w1h ${c.usage.cacheWrite1h} · read ${c.usage.cacheRead} · out ${c.usage.output}" +
                        (c.thinkingEstimate?.let { " (≈$it thinking)" } ?: "") + " · prompt ${c.threadAfter}",
                    mono = true,
                )
                Small("first token ${c.firstTokenMs ?: "—"} ms · ${c.totalMs} ms" + (c.cacheReport?.let { " · cache writes: $it" } ?: ""))
                c.usageRaw?.let { r -> Small("raw usage: start ${r.start ?: "—"} · delta ${r.delta ?: "—"}", mono = true) }
                c.error?.let { Small("⚠️ $it") }
            }
        }
        item {
            FlowRow(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    scope.launch {
                        val f = shadow.export(File(context.cacheDir, "shared"), "${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        exported = "${f.name}, ${f.length() / 1024} KB"
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TITLE, f.name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, f.name)) }
                    }
                }) { Text("⬆️ Export everything") }
                if (upload != null) {
                    var sending by remember { mutableStateOf(false) }
                    // the developer's data to the paired node (app/DevUpload): the export and the app's log, as it goes
                    // by itself after the day's closing and when the app goes to the background
                    OutlinedButton(enabled = !sending, onClick = {
                        sending = true
                        scope.launch { upload.sendNow(); sending = false }
                    }) { Text(if (sending) "📤 …" else "📤 Pošlji vozlišču · Send to the node") }
                }
            }
            exported?.let { Small(it) }
            upload?.last?.let { Small("📤 $it") }
        }
        item { Spacer(Modifier.padding(24.dp)) }
    }
}

/**
 * The matrix (docs/plans/05-phase-0.md, "The matrix"): on or off, the lanes as a grid (each family's model × the
 * efforts it takes, a toggle each; a model the key doesn't list said so), today's spend per lane and in all. No budget of
 * its own: it runs until it's turned off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Matrix(shadow: ShadowTutor) {
    val s = shadow.settings
    val m = s.matrix
    val matrix = shadow.matrix
    val catalog = ModelCatalog(s.listed)
    val today = matrix.today
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp).testTag("shadow-matrix")) {
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Small("🧪 The matrix", bold = true)
                        Small(
                            "Every event the primary gets, run again in each lane: one model at one effort for every task, its own " +
                                "thread. The notebook is read, never written; writes are dry runs. No budget of its own; the primary's isn't touched.",
                        )
                    }
                    Switch(
                        checked = m.enabled, onCheckedChange = { on -> shadow.updateMatrix { it.copy(enabled = on) } },
                        modifier = Modifier.testTag("matrix-enabled").semantics { contentDescription = if (m.enabled) "The matrix: on" else "The matrix: off" },
                    )
                }
                Small(matrix.status)
            }
        }
        item {
            Card {
                Small("Lanes", bold = true)
                Small("A family's model (the key's) × the efforts it takes. ${m.lanes.size} picked; the defaults: Haiku medium to max, Sonnet low to high, Opus low and medium.")
                for (f in Family.entries) {
                    val model = catalog.own(f)
                    val levels = ModelCatalog.efforts(model ?: f.preferred.first())
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Small(f.name.lowercase().replaceFirstChar { it.uppercase() } + " · " + (model ?: "⚠️ not listed (${f.preferred.first()})"), bold = true)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (level in levels) {
                            val spec = LaneSpec(f, level)
                            FilterChip(
                                selected = spec in m.lanes, enabled = model != null,
                                onClick = { shadow.updateMatrix { it.copy(lanes = if (spec in it.lanes) it.lanes - spec else it.lanes + spec) } },
                                label = { Text(level, fontSize = 12.sp) },
                                modifier = Modifier.testTag("lane-${f.name.lowercase()}-$level"),
                            )
                        }
                    }
                }
                matrix.plan.filter { it.skipped != null }.forEach { p -> Small("⚠️ ${p.family.name.lowercase()} · ${p.effort}: skipped, ${p.skipped}") }
                if (m.lanes.toSet() != MatrixSettings.DEFAULT_LANES.toSet()) {
                    TextButton(onClick = { shadow.updateMatrix { it.copy(lanes = MatrixSettings.DEFAULT_LANES) } }) { Text("The nine defaults again") }
                }
            }
        }
        item {
            Card {
                val total = today.values.sumOf { d -> d.calls.sumOf { it.cost ?: 0.0 } }
                Small("Today", bold = true)
                Small("the lanes' spend ${money(total)} in all (the primary's ${money(shadow.today.calls.sumOf { it.cost ?: 0.0 })}, its own budget \$${"%.2f".format(s.budgetPerDay)})")
                for (p in matrix.plan) {
                    val id = p.id ?: continue
                    val d = today[id]
                    val q = matrix.queued[id].orEmpty()
                    val ok = d?.calls.orEmpty().filter { it.error == null }
                    val first = ok.mapNotNull { it.firstTokenMs }.sorted().let { xs -> xs.getOrNull(xs.size / 2) }
                    val state = when {
                        q.any { it.status == si.lanisce.lani.tutor.TurnStatus.RUNNING } -> "running ${q.first { it.status == si.lanisce.lani.tutor.TurnStatus.RUNNING }.eventKind}"
                        q.isNotEmpty() -> "${q.size} waiting"
                        else -> "idle"
                    }
                    Small(
                        "$id: ${money(d?.calls.orEmpty().sumOf { it.cost ?: 0.0 })} · ${d?.turns?.size ?: 0} turn(s), ${ok.size} call(s)" +
                            " · first token ${first ?: "—"} ms · ✍️ ${d?.turns?.count { it.replyFromText } ?: 0} · errors ${d?.calls?.count { it.error != null } ?: 0} · $state",
                        mono = true,
                    )
                }
                // lanes no longer picked that ran today
                today.keys.filter { id -> matrix.plan.none { it.id == id } }.forEach { id -> Small("$id (off): ${money(today[id]?.calls.orEmpty().sumOf { it.cost ?: 0.0 })}", mono = true) }
            }
        }
        item { Spacer(Modifier.padding(24.dp)) }
    }
}

@Composable
private fun Memory(shadow: ShadowTutor) {
    var editing by remember { mutableStateOf<String?>(null) }
    val th = shadow.thread
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Small("📓 The notebook (${shadow.notebook.text.length} characters, ${shadow.notebook.versions.size} older version(s))", bold = true)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { editing = shadow.notebook.text }) { Text("Edit") }
                }
                Small(shadow.notebook.text.ifBlank { "(empty: the tutor writes it at the day's end and at a restart)" })
            }
        }
        item {
            Card {
                Small("🧵 The day's thread", bold = true)
                if (th == null) Small("(none yet today)")
                else {
                    Small("${th.day} · ${th.model} · ${th.messages.size} message(s) · ${th.tokens} tokens (limit ${shadow.settings.limit}) · restarts ${th.restarts.joinToString().ifBlank { "—" }}")
                    if (th.pending.isNotEmpty()) Small("waiting for the next message: ${th.pending.size} line(s) from other models")
                    th.lines.takeLast(30).forEach { Small("· $it") }
                }
            }
        }
        if (shadow.queued.isNotEmpty()) {
            item { Small("Queue", bold = true) }
            items(shadow.queued, key = { "q-" + it.id }) { t -> Card { TurnView(shadow, t) } }
        }
    }
    editing?.let { text ->
        var value by remember { mutableStateOf(text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("The notebook") },
            text = { OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), minLines = 6, maxLines = 14) },
            confirmButton = { TextButton(onClick = { shadow.editNotebook(value); editing = null }) { Text("Keep") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Settings(shadow: ShadowTutor) {
    val s = shadow.settings
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var keyNote by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Small("Shadow mode", bold = true)
                        Small("Watches your real events; reads through the paired node (GET only); writes are dry runs; replies only here.")
                    }
                    Switch(checked = s.enabled, onCheckedChange = { shadow.setEnabled(it) }, modifier = Modifier.testTag("shadow-enabled").semantics { contentDescription = if (s.enabled) "Shadow mode: on" else "Shadow mode: off" })
                }
            }
        }
        item {
            Card {
                Small("🔑 Claude API key", bold = true)
                if (shadow.hasKey) {
                    Small(if (s.keyRefused) "⚠️ The key was refused (401): enter a new one." else "Kept, encrypted with a key in the Android Keystore. ${s.listed.size} model(s) listed.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !checking, onClick = { checking = true; scope.launch { keyNote = shadow.recheck() ?: "✓ checked"; checking = false } }) { Text("Check again") }
                        OutlinedButton(onClick = { shadow.removeKey(); keyNote = "removed" }) { Text("Remove") }
                    }
                }
                if (!shadow.hasKey || s.keyRefused) {
                    Small(
                        "Make the key in the Console in a workspace of its own (\"Lani\") with a monthly spend limit (the Default " +
                            "workspace can't have one), expiry \"Never\" (keys can expire after 3 hours to 30 days). The check is " +
                            "GET /v1/models: not a model call.",
                    )
                    OutlinedTextField(
                        key, { key = it.trim() }, Modifier.fillMaxWidth().testTag("shadow-key"),
                        label = { Text("sk-ant-…") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Button(enabled = key.isNotBlank() && !checking, onClick = {
                        checking = true
                        scope.launch {
                            keyNote = shadow.setKey(key)?.let { "⚠️ $it" } ?: "✓ the key works: kept"
                            if (keyNote?.startsWith("✓") == true) key = ""
                            checking = false
                        }
                    }) { Text(if (checking) "Checking…" else "Check (GET /v1/models) and keep") }
                }
                keyNote?.let { Small(it) }
                Small("The key goes only to ${shadow.baseUrl()} and never into a log, the export, a prompt or a tool's result.")
            }
        }
        if (shadow.debug) item {
            Card {
                Small("🧪 QA build (debug or qa): the Messages API's base URL", bold = true)
                var url by remember(s.debugBaseUrl) { mutableStateOf(s.debugBaseUrl.orEmpty()) }
                OutlinedTextField(url, { url = it.trim() }, Modifier.fillMaxWidth().testTag("shadow-base"), singleLine = true, label = { Text("empty: https://api.anthropic.com") })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { shadow.update { it.copy(debugBaseUrl = url.ifBlank { null }) } }) { Text("Use it") }
                    OutlinedButton(onClick = { url = MOCK; shadow.update { it.copy(debugBaseUrl = MOCK) } }) { Text("The mock (10.0.2.2:8771)") }
                }
            }
        }
        item {
            Card {
                Small("Models and effort", bold = true)
                Small(
                    "We recommend, you set: the model and the effort per task. ⭐ marks our recommendation (the Balanced preset). " +
                        "Effort is how much the model thinks and writes: every output token, thinking included, is billed as output.",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in Preset.entries) FilterChip(
                        selected = s.preset == p,
                        onClick = { shadow.update { it.copy(preset = p, models = emptyMap(), efforts = emptyMap()) } },
                        label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() } + if (p == Preset.BALANCED) " ⭐" else "") },
                    )
                }
                val catalog = ModelCatalog(s.listed)
                for (t in Task.entries) {
                    val model = catalog.modelFor(t, s.preset, s.models)
                    TaskChoice(shadow, t, model, s.models.containsKey(t), ModelCatalog.effortFor(t, s.preset, model, s.efforts), s.efforts.containsKey(t), catalog.resolve(t.family))
                }
            }
        }
        item {
            Card {
                Small("Limits", bold = true)
                NumberField("Budget a day (\$)", "%.2f".format(s.budgetPerDay)) { v -> v.toDoubleOrNull()?.takeIf { it >= 0 }?.let { d -> shadow.update { it.copy(budgetPerDay = d) } } }
                NumberField("The thread's limit (tokens; the preset's: ${s.preset.threadLimit})", (s.threadLimit ?: s.preset.threadLimit).toString()) { v ->
                    v.toIntOrNull()?.takeIf { it >= 2_000 }?.let { n -> shadow.update { it.copy(threadLimit = n) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Small("React at once", bold = true)
                        Small("Reviews and session ends get a turn right away instead of in the digest.")
                    }
                    Switch(checked = s.reactAtOnce, onCheckedChange = { on -> shadow.update { it.copy(reactAtOnce = on) } })
                }
            }
        }
        item { Spacer(Modifier.padding(24.dp)) }
    }
}

/**
 * One task's model and effort ("chat: claude-sonnet-5-5 ⭐", "chat effort: medium ⭐"), each a picker with "as the preset"
 * first and our recommendation marked; the effort's offers only the levels [model] takes, xhigh and max behind "more".
 * ✎: set here, not by the preset.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskChoice(shadow: ShadowTutor, task: Task, model: String, modelSet: Boolean, effort: String?, effortSet: Boolean, recommended: String) {
    var modelMenu by remember { mutableStateOf(false) }
    var effortMenu by remember { mutableStateOf(false) }
    var more by remember(effort) { mutableStateOf(effort in Effort.MORE) }
    val name = task.name.lowercase()
    val levels = ModelCatalog.efforts(model)
    FlowRow(verticalArrangement = Arrangement.Center) {
        Box {
            TextButton(onClick = { modelMenu = true }) {
                Text("$name: $model" + (if (modelSet) " ✎" else "") + (if (model == recommended) " ⭐" else ""), fontSize = 12.sp)
            }
            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                DropdownMenuItem(text = { Text("as the preset") }, onClick = { modelMenu = false; shadow.update { it.copy(models = it.models - task) } })
                models(shadow).forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m + if (m == recommended) " $RECOMMENDED" else "", fontSize = 13.sp) },
                        onClick = { modelMenu = false; shadow.update { it.copy(models = it.models + (task to m)) } },
                    )
                }
            }
        }
        Box {
            TextButton(enabled = levels.isNotEmpty(), onClick = { effortMenu = true }) {
                Text(
                    "$name effort: ${effort ?: "none (the model takes none)"}" + (if (effortSet) " ✎" else "") + (if (effort == task.effort) " ⭐" else ""),
                    fontSize = 12.sp,
                )
            }
            DropdownMenu(expanded = effortMenu, onDismissRequest = { effortMenu = false }) {
                val preset = ModelCatalog.effortFor(task, shadow.settings.preset, model)
                DropdownMenuItem(text = { Text("as the preset (${preset ?: "—"})") }, onClick = { effortMenu = false; shadow.update { it.copy(efforts = it.efforts - task) } })
                levels.filter { more || it !in Effort.MORE }.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(level + if (level == task.effort) " $RECOMMENDED" else "", fontSize = 13.sp) },
                        onClick = { effortMenu = false; shadow.update { it.copy(efforts = it.efforts + (task to level)) } },
                    )
                }
                if (levels.any { it in Effort.MORE }) {
                    DropdownMenuItem(text = { Text(if (more) "fewer (hide xhigh, max)" else MORE_LEVELS, fontSize = 13.sp) }, onClick = { more = !more })
                }
            }
        }
    }
}

/** Our recommendation, in the pickers (a task's model and effort, "Also run on"'s). */
private const val RECOMMENDED = "⭐ priporočeno · recommended"
private const val MORE_LEVELS = "more… (xhigh, max)"

@Composable
private fun NumberField(label: String, value: String, onDone: (String) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(v, { v = it.trim() }, Modifier.weight(1f), label = { Text(label, fontSize = 11.sp) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        TextButton(onClick = { onDone(v) }) { Text("Set") }
    }
}

private const val MOCK = "http://10.0.2.2:8771"
