package si.lanisce.lani.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.sync.BackupError
import si.lanisce.lani.sync.BackupKeys
import si.lanisce.lani.sync.BackupProblem
import si.lanisce.lani.sync.DataSync
import si.lanisce.lani.sync.DeviceFlow
import si.lanisce.lani.sync.GitHubRepo
import si.lanisce.lani.sync.LeaseRead
import si.lanisce.lani.sync.LeaseState
import si.lanisce.lani.sync.Party
import si.lanisce.lani.sync.RateLimited
import si.lanisce.lani.sync.SyncOutcome
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.theme.TriglavRed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "💾 Podatki · Data" in a line, for its row in "⚙️ Nastavitve · Settings" (ui/settings/SettingsScreen): git on or off, the
 * last backup.
 */
fun dataSummary(d: DataSync): String = listOfNotNull(
    d.settings.repo?.let { "🔗 ${bi("dataSync.gitOn", "repo" to it)}" } ?: "🔗 ${bi("dataSync.gitOff")}",
    d.settings.lastBackup?.let { "🔐 ${whenText(Instant.parse(it))}" },
).joinToString(" · ")

private enum class Dialog { NONE, CONNECT, REPOS, CODE, PASSPHRASE, RESTORE, SCAN, TAKE_ANYWAY, KEEP_PHONE, HAND_OVER, DISCONNECT }

/**
 * "💾 Podatki · Data" (docs/plans/05-built/git-sync.md): git (connect, the repository, the last sync, sync now), who
 * writes the data (the lease: take over, the computer's handover code, hand back), and the encrypted backup (set it up,
 * back up now, restore). [banner] shows a line in the app's snackbar.
 */
@Composable
fun DataSheet(d: DataSync, onDismiss: () -> Unit, banner: (String) -> Unit) {
    LaniSheet(onDismissRequest = onDismiss) {
        DataContent(d, banner, Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding())
    }
}

/**
 * What [DataSheet] shows, and "⚙️ Nastavitve · Settings" → "💾 Podatki · Data" as a page (ui/settings/SettingsScreen): git,
 * who writes, the backup, and their dialogs. [modifier]: where it lies (its scroll, its padding); [title] off where a
 * header names it.
 */
@Composable
fun DataContent(d: DataSync, banner: (String) -> Unit, modifier: Modifier = Modifier, title: Boolean = true) {
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    var shownCode by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun act(block: suspend CoroutineScope.() -> String?): Job = scope.launch { block()?.let { message = it } }
    LaunchedEffect(Unit) { d.keep() } // this phone's id, shown below, stays the same from now on

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // its page in the settings has the title in its header
        if (title) Text("💾 ${bi("dataSync.title")}", style = MaterialTheme.typography.titleLarge)
        Text(bi("dataSync.what"), style = MaterialTheme.typography.bodySmall, color = muted)
        if (d.busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
            Text(bi("dataSync.working"), style = MaterialTheme.typography.bodySmall)
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }

        // --- git ------------------------------------------------------------------------------------
        Heading("🔗 ${bi("dataSync.git")}")
        if (!d.connected) {
            Text(bi("dataSync.gitWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
            BigButton("🔗 ${bi("dataSync.connect")}", onClick = { dialog = Dialog.CONNECT }, color = TriglavRed)
        } else {
            Info("📦", bi("dataSync.repo"), "${d.settings.repo} · " + if (d.settings.repoPrivate) "🔒 ${bi("dataSync.private")}" else "⚠️ ${bi("dataSync.public")}")
            d.settings.login?.let { Info("👤", bi("dataSync.account"), it) }
            Info("🔄", bi("dataSync.lastSync"), d.lastAt?.let { whenText(it) + (d.last?.let { o -> " · " + outcomeText(o) } ?: "") } ?: bi("dataSync.never"))
            d.diverged?.let { dv ->
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⚠️ ${bi("dataSync.diverged", "files" to dv.files.take(4).joinToString(", ").ifBlank { "…" }, "branch" to (dv.branch ?: "—"))}", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { act { outcomeText(d.useRepository()) } }, enabled = !d.busy) { Text("⬇️ ${bi("dataSync.useRepo")}") }
                    OutlinedButton(onClick = { dialog = Dialog.KEEP_PHONE }, enabled = !d.busy) { Text("📱 ${bi("dataSync.keepPhone")}") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { act { d.syncNow().join(); d.last?.let(::outcomeText) } }, enabled = !d.busy) { Text("🔄 ${bi("dataSync.syncNow")}") }
                TextButton(onClick = { dialog = Dialog.DISCONNECT }) { Text(bi("dataSync.disconnect")) }
            }
        }

        // --- who writes -----------------------------------------------------------------------------
        Heading("📍 ${bi("dataSync.whoWrites")}")
        if (d.connected) Text(leaseLine(d), style = MaterialTheme.typography.bodyMedium)
        Text(bi("dataSync.thisPhone", "name" to d.me.name, "id" to d.me.device), style = MaterialTheme.typography.bodySmall, color = muted)
        when (d.leaseState.takeIf { d.connected }) {
            LeaseState.OFFERED, LeaseState.FREE, LeaseState.EXPIRED -> {
                BigButton("📥 ${bi("dataSync.take")}", onClick = { act { leaseText(d.take(), d) } }, enabled = !d.busy, color = TriglavRed)
                Text(bi("dataSync.takeNote"), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            LeaseState.MINE -> OutlinedButton(onClick = { dialog = Dialog.HAND_OVER }, enabled = !d.busy) { Text("🖥️ ${bi("dataSync.handOver")}") }
            LeaseState.OTHER, LeaseState.UNCLAIMED -> TextButton(onClick = { dialog = Dialog.TAKE_ANYWAY }, enabled = !d.busy) { Text(bi("dataSync.takeAnyway")) }
            else -> Unit
        }
        OutlinedButton(onClick = { dialog = Dialog.SCAN }) { Text("📷 ${bi("dataSync.scanHandover")}") }
        Text(bi("dataSync.scanWhat"), style = MaterialTheme.typography.bodySmall, color = muted)

        // --- the backup ---------------------------------------------------------------------------
        Heading("🔐 ${bi("dataSync.backup")}")
        Text(bi("dataSync.backupWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
        when {
            d.relay.isBlank() -> Text("⏳ ${bi("dataSync.noRelay")}", style = MaterialTheme.typography.bodyMedium)
            d.settings.backup == null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { act { shownCode = d.setUpCode(); dialog = Dialog.CODE; null } }) { Text("🔑 ${bi("dataSync.setUpCode")}") }
                OutlinedButton(onClick = { dialog = Dialog.PASSPHRASE }) { Text(bi("dataSync.setUpPassphrase")) }
            }
            else -> {
                Info("🕒", bi("dataSync.lastBackup"), d.settings.lastBackup?.let { whenText(Instant.parse(it)) } ?: bi("dataSync.never"))
                Button(onClick = { act { backupText { d.backupNow().let { bi("dataSync.backedUp", "size" to mb(it.size)) } } } }, enabled = !d.busy) { Text("💾 ${bi("dataSync.backupNow")}") }
            }
        }
        if (d.relay.isNotBlank()) OutlinedButton(onClick = { dialog = Dialog.RESTORE }, enabled = !d.busy) { Text("♻️ ${bi("dataSync.restore")}") }
    }

    when (dialog) {
        Dialog.NONE -> Unit
        Dialog.CONNECT -> ConnectDialog(d, onDone = { dialog = if (it) Dialog.REPOS else Dialog.NONE })
        Dialog.REPOS -> RepoDialog(d, onDone = { dialog = Dialog.NONE })
        Dialog.CODE -> CodeDialog(shownCode.orEmpty(), onDone = { shownCode = null; dialog = Dialog.NONE })
        Dialog.PASSPHRASE -> PassphraseDialog(onCancel = { dialog = Dialog.NONE }) { p ->
            dialog = Dialog.NONE
            act { d.setUpPassphrase(p); bi("dataSync.backupReady") }
        }
        Dialog.RESTORE -> RestoreDialog(d, onDone = { text -> dialog = Dialog.NONE; text?.let { message = it; banner(it) } })
        Dialog.SCAN -> HandoverScanDialog(d, onDone = { text -> dialog = Dialog.NONE; text?.let { message = it; banner(it) } })
        Dialog.TAKE_ANYWAY -> Confirm(bi("dataSync.takeAnywayTitle"), bi("dataSync.takeAnywayWhat"), bi("dataSync.takeAnyway"), onCancel = { dialog = Dialog.NONE }) {
            dialog = Dialog.NONE
            act { leaseText(d.take(force = true), d) }
        }
        Dialog.KEEP_PHONE -> Confirm(bi("dataSync.keepPhone"), bi("dataSync.keepPhoneWhat"), bi("dataSync.keepPhone"), onCancel = { dialog = Dialog.NONE }) {
            dialog = Dialog.NONE
            act {
                val r = d.keepPhone()
                if (r is SyncOutcome.LeaseRefused) { dialog = Dialog.TAKE_ANYWAY; null } else outcomeText(r)
            }
        }
        Dialog.HAND_OVER -> Confirm(bi("dataSync.handOver"), bi("dataSync.handOverWhat"), bi("dataSync.handOver"), onCancel = { dialog = Dialog.NONE }) {
            dialog = Dialog.NONE
            act { leaseText(d.handOver(), d, handedOver = true) }
        }
        Dialog.DISCONNECT -> Confirm(bi("dataSync.disconnect"), bi("dataSync.disconnectWhat"), bi("dataSync.disconnect"), onCancel = { dialog = Dialog.NONE }) {
            dialog = Dialog.NONE
            act { d.disconnect(); null }
        }
    }
}

// --- connect git ------------------------------------------------------------------------------------

/** Sign in with GitHub (the device flow), or paste a token. [onDone] true: signed in, pick a repository next. */
@Composable
private fun ConnectDialog(d: DataSync, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var code by remember { mutableStateOf<DeviceFlow.Code?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var stopped by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { stopped = true; onDone(false) },
        title = { Text("🔗 ${bi("dataSync.connect")}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (d.signInAvailable) {
                    val c = code
                    if (c == null) {
                        Button(onClick = {
                            scope.launch {
                                error = null
                                val flow = d.deviceFlow()
                                val started = runCatching { flow.start() }.getOrElse { error = it.message; return@launch }
                                code = started
                                waiting = true
                                when (val r = flow.await(started) { stopped }) {
                                    is DeviceFlow.Result.Token -> d.useToken(r.token, "device")?.let { error = it } ?: onDone(true)
                                    DeviceFlow.Result.Expired -> error = bi("dataSync.signInExpired")
                                    DeviceFlow.Result.Denied -> Unit
                                    is DeviceFlow.Result.Failed -> error = r.message
                                }
                                waiting = false
                                code = null
                            }
                        }) { Text("🐙 ${bi("dataSync.signIn")}") }
                    } else {
                        Text(bi("dataSync.codeShow"), style = MaterialTheme.typography.bodyMedium)
                        SelectionContainer { Text(c.userCode, fontFamily = FontFamily.Monospace, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                        OutlinedButton(onClick = { runCatching { uri.openUri(c.verificationUri) } }) { Text("🌐 ${bi("dataSync.openGithub")}") }
                        if (waiting) Text("⏳ ${bi("dataSync.waiting")}", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(bi("dataSync.signInOff"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("🔑 ${bi("dataSync.token")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(bi("dataSync.tokenHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = token, onValueChange = { token = it.trim() }, singleLine = true, label = { Text(bi("dataSync.tokenField")) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                error?.let { Text("⚠️ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = { scope.launch { d.useToken(token, "token")?.let { error = it } ?: onDone(true) } }, enabled = token.length >= 20) { Text(bi("dataSync.connectButton")) }
        },
        dismissButton = { TextButton(onClick = { stopped = true; onDone(false) }) { Text(bi("dataSync.cancel")) } },
    )
}

/** The repositories the token reaches, the handover's first; or a new private one. A public one asks first. */
@Composable
private fun RepoDialog(d: DataSync, onDone: () -> Unit) {
    var repos by remember { mutableStateOf<List<GitHubRepo>?>(null) }
    var suggested by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("lani-data") }
    var publicPick by remember { mutableStateOf<GitHubRepo?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        suggested = d.handedRepo()?.full
        repos = runCatching { d.repos() }.getOrElse { error = it.message; emptyList() }
            .sortedByDescending { it.full == suggested }
    }
    fun pick(r: GitHubRepo) {
        if (r.private) {
            d.choose(r)
            onDone()
        } else {
            publicPick = r
        }
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("📦 ${bi("dataSync.pickRepo")}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggested?.let { Text("🤝 ${bi("dataSync.suggested", "repo" to it)}", style = MaterialTheme.typography.bodySmall) }
                if (repos == null) CircularProgressIndicator()
                repos?.forEach { r ->
                    Text(
                        (if (r.private) "🔒 " else "⚠️ ") + r.full,
                        Modifier.fillMaxWidth().clickable(enabled = r.canPush) { pick(r) }.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (r.full == suggested) FontWeight.Bold else null,
                        color = if (r.canPush) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("➕ ${bi("dataSync.newRepo")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it.filter { c -> c.isLetterOrDigit() || c in "-_." }.take(100) }, singleLine = true, label = { Text(bi("dataSync.repoName")) })
                OutlinedButton(onClick = { scope.launch { runCatching { d.createRepo(name) }.onSuccess { d.choose(it); onDone() }.onFailure { error = it.message } } }, enabled = name.isNotBlank()) {
                    Text(bi("dataSync.create"))
                }
                error?.let { Text("⚠️ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(bi("dataSync.cancel")) } },
    )
    publicPick?.let { r ->
        Confirm(bi("dataSync.publicTitle"), bi("dataSync.publicWarning"), bi("dataSync.useAnyway"), onCancel = { publicPick = null }) {
            publicPick = null
            d.choose(r)
            onDone()
        }
    }
}

// --- the backup -------------------------------------------------------------------------------------

@Composable
private fun CodeDialog(code: String, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("🔑 ${bi("dataSync.codeTitle")}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(bi("dataSync.codeWhat"), style = MaterialTheme.typography.bodyMedium)
                SelectionContainer { Text(code.chunked(15).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text("✅ ${bi("dataSync.wroteItDown")}") } },
    )
}

@Composable
private fun PassphraseDialog(onCancel: () -> Unit, onSave: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val ok = a.trim().length >= BackupKeys.MIN_PASSPHRASE && a == b
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("🔑 ${bi("dataSync.passphraseTitle")}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bi("dataSync.passphraseWhat", "n" to BackupKeys.MIN_PASSPHRASE), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(a, { a = it }, singleLine = true, label = { Text(bi("dataSync.passphrase")) }, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(b, { b = it }, singleLine = true, label = { Text(bi("dataSync.passphraseAgain")) }, visualTransformation = PasswordVisualTransformation())
                if (b.isNotEmpty() && a != b) Text(bi("dataSync.passphraseMismatch"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSave(a) }, enabled = ok) { Text(bi("dataSync.save")) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(bi("dataSync.cancel")) } },
    )
}

/** Restore: this phone's own backups, or (a new phone) those a typed recovery code or passphrase finds. */
@Composable
private fun RestoreDialog(d: DataSync, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<si.lanisce.lani.sync.BackupEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val own = d.settings.backup != null
    LaunchedEffect(own) { if (own) found = runCatching { d.backupList() }.getOrElse { error = problemText(it); emptyList() } }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("♻️ ${bi("dataSync.restoreTitle")}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bi("dataSync.restoreWhat"), style = MaterialTheme.typography.bodySmall)
                if (!own) {
                    OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text(bi("dataSync.restoreTyped")) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false))
                    OutlinedButton(onClick = { scope.launch { found = runCatching { d.backupList(typed) }.getOrElse { error = problemText(it); null } } }, enabled = typed.length >= BackupKeys.MIN_PASSPHRASE) {
                        Text("🔎 ${bi("dataSync.find")}")
                    }
                }
                found?.let { list ->
                    if (list.isEmpty()) Text(bi("dataSync.noneFound"), style = MaterialTheme.typography.bodyMedium)
                    list.take(10).forEach { e ->
                        Text(
                            "💾 ${e.time?.let(::whenText) ?: e.name} · ${mb(e.size)}",
                            Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    runCatching { d.restore(e.name, typed.takeIf { !own }) }
                                        .onSuccess { m -> onDone("♻️ ${bi("dataSync.restored", "date" to whenText(Instant.parse(m.created)))}") }
                                        .onFailure { error = problemText(it) }
                                }
                            }.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                if (d.busy) CircularProgressIndicator()
                error?.let { Text("⚠️ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(bi("dataSync.cancel")) } },
    )
}

// --- small parts ------------------------------------------------------------------------------------

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))

@Composable
private fun Info(emoji: String, label: String, value: String) {
    Column {
        Text("$emoji $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun Confirm(title: String, text: String, yes: String, onCancel: () -> Unit, onYes: () -> Unit) = AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = { Button(onClick = onYes) { Text(yes) } },
    dismissButton = { TextButton(onClick = onCancel) { Text(bi("dataSync.cancel")) } },
)

/** "14:05" today, else "18. 10. 14:05": numbers read the same in every language. */
internal fun whenText(t: Instant): String {
    val z = t.atZone(ZoneId.systemDefault())
    return if (z.toLocalDate() == LocalDate.now()) DateTimeFormatter.ofPattern("HH:mm").format(z) else DateTimeFormatter.ofPattern("d. M. HH:mm").format(z)
}

private fun mb(bytes: Long): String = if (bytes < 1_000_000) "${(bytes + 999) / 1000} kB" else "%.1f MB".format(bytes / 1_000_000.0)

/** Who writes the data, for the lease's line. */
internal fun leaseLine(d: DataSync): String {
    val l = d.lease
    val since = l.lease?.since?.let { runCatching { whenText(Instant.parse(it)) }.getOrNull() } ?: "—"
    return when (d.leaseState) {
        LeaseState.MINE -> "📱 ${bi("dataSync.leaseMine", "since" to since)}"
        LeaseState.OFFERED -> "📥 ${bi("dataSync.leaseOffered")}"
        LeaseState.OFFERED_AWAY -> "⏳ ${bi("dataSync.leaseOfferedAway")}"
        LeaseState.FREE -> "🕊️ ${bi("dataSync.leaseFree")}"
        LeaseState.EXPIRED -> "⌛ ${bi("dataSync.leaseExpired")}"
        LeaseState.UNCLAIMED -> "🖥️ ${bi("dataSync.leaseUnclaimed")}"
        LeaseState.OTHER -> if (l is LeaseRead.Unreadable) "⚠️ ${bi("dataSync.leaseBroken")}" else {
            val h = l.lease?.holder ?: l.lease?.offer?.to
            val who = when {
                h == null -> bi("dataSync.someone")
                h.side == Party.NODE -> bi("dataSync.computer") + (h.name.ifBlank { h.device }.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                else -> bi("dataSync.anotherPhone") + (h.name.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
            }
            "🖥️ ${bi("dataSync.leaseOther", "who" to who)}"
        }
    }
}

/** What a take or a hand-over did, for the message. */
private fun leaseText(o: SyncOutcome, d: DataSync, handedOver: Boolean = false): String = when (o) {
    is SyncOutcome.Done -> if (handedOver) "✅ ${bi("dataSync.handedOver")}" else "✅ ${bi("dataSync.took")}"
    is SyncOutcome.LeaseRefused -> leaseLine(d)
    else -> outcomeText(o)
}

internal fun outcomeText(o: SyncOutcome): String = when (o) {
    is SyncOutcome.Done -> listOfNotNull(
        if (o.pulled.isEmpty() && o.pushed.isEmpty()) "✅ ${bi("dataSync.upToDate")}" else null,
        o.pulled.takeIf { it.isNotEmpty() }?.let { "⬇️ ${bi("dataSync.pulled", "n" to it.size)}" },
        o.pushed.takeIf { it.isNotEmpty() }?.let { "⬆️ ${bi("dataSync.pushed", "n" to it.size)}" },
        o.skipped.takeIf { it.isNotEmpty() }?.let { "📦 ${bi("dataSync.skipped", "files" to it.take(3).joinToString(", "))}" },
    ).joinToString(" · ")
    is SyncOutcome.Diverged -> "⚠️ ${bi("dataSync.diverged", "files" to o.files.take(4).joinToString(", ").ifBlank { "…" }, "branch" to (o.branch ?: "—"))}"
    is SyncOutcome.NotHolder -> "📱 ${bi("dataSync.notHolder")}"
    is SyncOutcome.LeaseRefused -> "🔒 ${bi("dataSync.leaseRefused")}"
    is SyncOutcome.Failed -> o.retryAt?.let { "⏳ ${bi("dataSync.rateLimited", "time" to whenText(it))}" } ?: "⚠️ ${bi("dataSync.failed", "why" to o.message.take(160))}"
}

private suspend fun backupText(block: suspend () -> String): String = try {
    "✅ " + block()
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    "⚠️ " + problemText(e)
}

internal fun problemText(e: Throwable): String = when ((e as? BackupError)?.problem) {
    BackupProblem.NOT_SET_UP -> bi("dataSync.backupNotSetUp")
    BackupProblem.NO_RELAY -> bi("dataSync.noRelay")
    BackupProblem.TOO_LARGE -> bi("dataSync.backupTooLarge")
    BackupProblem.WRONG_KEY -> bi("dataSync.backupWrongKey")
    BackupProblem.NONE_FOUND -> bi("dataSync.noneFound")
    BackupProblem.UNREACHABLE -> bi("dataSync.backupUnreachable")
    BackupProblem.REFUSED -> bi("dataSync.backupRefused")
    BackupProblem.NOT_A_BACKUP, BackupProblem.BROKEN -> bi("dataSync.backupBroken")
    null -> if (e is RateLimited) bi("dataSync.rateLimited", "time" to whenText(e.until)) else e.message ?: e.toString()
}
