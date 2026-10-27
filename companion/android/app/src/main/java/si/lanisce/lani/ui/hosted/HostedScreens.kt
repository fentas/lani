package si.lanisce.lani.ui.hosted

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.hosted.DataExport
import si.lanisce.lani.hosted.HostedAnswers
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.TriglavRed
import java.io.File

/** The files an import takes: a zip (some file managers say octet-stream for it). */
private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")

private enum class Step { START, LEARNER, WORKING, TUTOR }

/**
 * "☁️ Lani na tej napravi · Lani on this device" (docs/plans/05-built/hosted-mode.md, onboarding.md): the setup's way
 * "📱 Samo ta telefon / tablica · Just this device". A new learner (what /lani-setup and lani-setup ask: the name, how the
 * village speaks to them, the level, the minutes a day, and whether a child learns here; the languages the setup asked
 * already: [chosenTarget], [chosenBase], [onLanguages] to change them), an export imported (from `lani export` on a computer, or this
 * app's), or the data already on the phone; then the tutor; then Home, where the village is founded as with a node.
 * [onBack]: to the setup's choice (or, switching from a node, back to it: then the languages are asked here, as the
 * learner's are now).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HostedSetup(vm: AppViewModel, onBack: () -> Unit, chosenTarget: String? = null, chosenBase: String? = null, onLanguages: (() -> Unit)? = null) {
    val h = vm.hosted
    val onPhone = remember { h.learnerOnPhone() }
    // a learner on the phone already: go on, start afresh or import; else straight to the questions
    var step by remember { mutableStateOf(if (onPhone != null) Step.START else Step.LEARNER) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<String?>(null) }
    val own = remember { si.lanisce.lani.l10n.L10n.ownPair }
    var target by remember { mutableStateOf(chosenTarget ?: own.target.code) }
    var base by remember { mutableStateOf(chosenBase?.takeIf { it != target } ?: own.base.code.takeIf { it != target } ?: if (target == "en") "sl" else "en") }
    var level by remember { mutableStateOf("A1") }
    var goal by remember { mutableStateOf(30) }
    var child by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf<si.lanisce.lani.data.BridgeConfig?>(null) }
    val scope = rememberCoroutineScope()

    fun done(r: Result<si.lanisce.lani.data.BridgeConfig>) {
        r.onSuccess { config = it; error = null; step = Step.TUTOR }
            .onFailure { error = it.message ?: it.toString(); step = if (step == Step.WORKING && name.isNotBlank()) Step.LEARNER else if (onPhone != null) Step.START else Step.LEARNER }
    }
    // Back does what each step's "‹ Nazaj" does (the start, the step before the setup's); a moment's work isn't left
    BackHandler(enabled = step != Step.TUTOR) {
        when (step) {
            Step.START -> onBack()
            Step.LEARNER -> if (onPhone != null) step = Step.START else onBack()
            Step.WORKING, Step.TUTOR -> Unit
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        step = Step.WORKING
        scope.launch {
            val r = vm.hosted.import(uri, starting = true)
            r.onSuccess { i -> note = importedLine(i.manifest) }
            done(r.map { vm.hostedConfig() ?: error("no connection") })
        }
    }

    val white = Color.White
    val muted = Color.White.copy(alpha = 0.85f)
    val chipColors = FilterChipDefaults.filterChipColors(
        labelColor = white, selectedLabelColor = SloBlueDeep, selectedContainerColor = white, iconColor = white,
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SloBlueDeep, SloBlue))).safeDrawingPadding()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Text("📱", style = MaterialTheme.typography.displayMedium)
            Text(bi("hosted.title"), style = MaterialTheme.typography.headlineMedium, color = white)
            error?.let { Text("⚠️ $it", color = Color(0xFFFFD1D8)) }
            when (step) {
                Step.START -> {
                    Text(bi("hosted.what"), color = muted)
                    if (onPhone != null) {
                        BigButton("▶ ${bi("hosted.goOn", "name" to onPhone)}", onClick = {
                            step = Step.WORKING
                            scope.launch { done(h.goOn()) }
                        }, color = TriglavRed)
                        Text(bi("hosted.goOnWhat"), color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                    BigButton("✨ ${bi("hosted.fresh")}", onClick = { error = null; step = Step.LEARNER }, color = if (onPhone == null) TriglavRed else white.copy(alpha = 0.2f))
                    BigButton("📥 ${bi("hosted.importExport")}", onClick = { error = null; pick.launch(ZIP_TYPES) }, color = white.copy(alpha = 0.2f))
                    Text(bi("hosted.importWhat"), color = muted, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onBack) { Text("‹ ${bi("components.back")}", color = white) }
                }
                Step.LEARNER -> {
                    if (onPhone == null) Text(bi("hosted.what"), color = muted)
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = white, unfocusedTextColor = white, focusedLabelColor = white, unfocusedLabelColor = white.copy(alpha = 0.75f),
                        focusedBorderColor = white, unfocusedBorderColor = white.copy(alpha = 0.6f), cursorColor = white,
                    )
                    OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(bi("hosted.name")) }, singleLine = true, colors = fieldColors)
                    // who learns here: a child's device leaves the tutor's key and accounts to a parent (app/ParentGate)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!child, { child = false }, { Text("🧑 ${bi("hosted.forMe")}") }, colors = chipColors)
                        FilterChip(child, { child = true; if (goal == 30) goal = 15 }, { Text("🧒 ${bi("hosted.forChild")}") }, colors = chipColors)
                    }
                    if (child) Text(bi("hosted.forChildWhat"), color = muted, style = MaterialTheme.typography.bodySmall)
                    Label(bi("hosted.gender"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(gender == "female", { gender = "female" }, { Text(bi("hosted.asWoman")) }, colors = chipColors)
                        FilterChip(gender == "male", { gender = "male" }, { Text(bi("hosted.asMan")) }, colors = chipColors)
                    }
                    Text(bi("hosted.genderWhat"), color = muted, style = MaterialTheme.typography.bodySmall)
                    if (onLanguages != null) {
                        // asked by the setup already: what was chosen, and the way back to it
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                bi("hosted.languages", "target" to Lang.of(target)!!.ownName, "base" to Lang.of(base)!!.ownName),
                                color = white, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onLanguages) { Text(bi("hosted.change"), color = white) }
                        }
                    } else {
                        Label(bi("hosted.target"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (code in HostedAnswers.LANGUAGES) FilterChip(target == code, {
                                target = code
                                if (base == code) base = if (code == "en") "sl" else "en"
                            }, { Text(Lang.of(code)!!.ownName) }, colors = chipColors)
                        }
                        Label(bi("hosted.base"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (code in HostedAnswers.LANGUAGES.filter { it != target }) FilterChip(base == code, { base = code }, { Text(Lang.of(code)!!.ownName) }, colors = chipColors)
                        }
                    }
                    if (target != "sl") Text(bi("hosted.lessContent", "lang" to Lang.of(target)!!.ownName), color = muted, style = MaterialTheme.typography.bodySmall)
                    Label(bi("hosted.level"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (l in HostedAnswers.LEVELS) FilterChip(level == l, { level = l }, { Text(l) }, colors = chipColors)
                    }
                    Text(bi("hosted.levelWhat"), color = muted, style = MaterialTheme.typography.bodySmall)
                    Label(bi("hosted.goal"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (g in HostedAnswers.GOALS) FilterChip(goal == g, { goal = g }, { Text("$g min") }, colors = chipColors)
                    }
                    BigButton(
                        "🏡 ${bi("hosted.found")}",
                        enabled = name.isNotBlank() && target != base,
                        onClick = {
                            error = null
                            step = Step.WORKING
                            scope.launch { done(h.create(HostedAnswers(name.trim(), gender, target, base, level, goal, child))) }
                        },
                        color = TriglavRed, disabledColor = white.copy(alpha = 0.2f),
                    )
                    // the other ways in: an export from a computer (no learner on the phone: there's no start step)
                    if (onPhone == null) TextButton(onClick = { error = null; pick.launch(ZIP_TYPES) }) { Text("📥 ${bi("hosted.importExport")}", color = white) }
                    TextButton(onClick = { if (onPhone != null) step = Step.START else onBack() }) { Text("‹ ${bi("components.back")}", color = white) }
                }
                Step.WORKING -> {
                    CircularProgressIndicator(color = white)
                    Text(if (h.busy == "import") bi("hosted.importing") else bi("hosted.preparing"), color = muted)
                }
                Step.TUTOR -> {
                    note?.let { Text("✅ $it", color = white) }
                    TutorStep(vm, child = h.child, onDone = { config?.let(vm::hostedReady) })
                }
            }
        }
    }
}

private enum class TutorWay { CLAUDE }

/**
 * The onboarding's tutor step: "🔑 Claude (svoj ključ) · Claude (your own key)" (the key typed and checked here, at
 * once), "Nadaljuj s ChatGPT · Continue with ChatGPT" (the browser's sign-in), or later. On a child's device a parent sets
 * it up: the step says so and opens after a hold (ui/setup/ParentGated). The tutor in the app turns on by itself once a
 * credential is set (the view model). [onDone]: Home.
 */
@Composable
private fun TutorStep(vm: AppViewModel, child: Boolean, onDone: () -> Unit) {
    val live = vm.live
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val white = Color.White
    val muted = Color.White.copy(alpha = 0.85f)
    var way by remember { mutableStateOf<TutorWay?>(null) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var welcome by remember { mutableStateOf(false) }
    fun browse(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    // Back from a way chosen: the ways again, as its "‹ Nazaj"
    BackHandler(enabled = way != null) { way = null; problem = null }

    Text("🧑‍🏫 ${bi("hosted.tutorTitle")}", style = MaterialTheme.typography.titleLarge, color = white, fontWeight = FontWeight.SemiBold)
    // a child's: the way on without a tutor, while a parent hasn't opened it
    si.lanisce.lani.ui.setup.ParentGated(child, onLight = false, whileLocked = { BigButton("🏡 ${bi("hosted.toVillage")}", onClick = onDone, color = TriglavRed) }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(bi("hosted.tutorWhat"), color = muted)
            problem?.let { Text(it, color = Color(0xFFFFD1D8)) }
            when (way) {
                null -> {
                    BigButton("🔑 ${bi("setup.tutorClaude")}", onClick = {
                        problem = null
                        live.update { it.copy(provider = si.lanisce.lani.tutor.ProviderId.ANTHROPIC, models = emptyMap()) }
                        way = TutorWay.CLAUDE
                    }, color = white.copy(alpha = 0.2f))
                    if (live.openaiAvailable) BigButton(if (busy) "…" else "💬 ${bi("tutorLive.continueChatGPT")}", enabled = !busy, onClick = {
                        problem = null
                        busy = true
                        live.update { it.copy(provider = si.lanisce.lani.tutor.ProviderId.OPENAI, models = emptyMap()) }
                        scope.launch {
                            val e = live.signInWithChatGPT { url -> browse(url) }
                            busy = false
                            if (e != null) problem = "⚠️ $e"
                            // OpenAI's UI guidelines: the welcome once, at the first sign-in with the plan; then Home
                            else if (!live.settings.chatgptWelcomed) welcome = true
                            else onDone()
                        }
                    }, color = white.copy(alpha = 0.2f))
                    if (busy) TextButton(onClick = { live.cancelSignIn(); busy = false }) { Text(bi("tutorLive.cancel"), color = white) }
                    BigButton("🏡 ${bi("common.later")}", onClick = onDone, color = TriglavRed)
                    Text(bi("hosted.tutorLater"), color = muted, style = MaterialTheme.typography.bodySmall)
                }
                TutorWay.CLAUDE -> {
                    Text(bi("setup.keyWhere"), color = muted, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { browse(ANTHROPIC_KEYS) }) { Text("🌐 console.anthropic.com", color = white) }
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = white, unfocusedTextColor = white, focusedLabelColor = white, unfocusedLabelColor = white.copy(alpha = 0.75f),
                        focusedBorderColor = white, unfocusedBorderColor = white.copy(alpha = 0.6f), cursorColor = white,
                    )
                    OutlinedTextField(
                        key, { key = it.trim(); problem = null }, Modifier.fillMaxWidth(),
                        label = { Text("sk-ant-…") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    BigButton(if (busy) "…" else "✓ ${bi("tutorLive.keyCheck")}", enabled = key.isNotBlank() && !busy, onClick = {
                        busy = true
                        scope.launch {
                            val e = live.setKey(key)
                            busy = false
                            problem = si.lanisce.lani.app.TutorKey.problem(e)
                            if (e == null) {
                                key = ""
                                onDone()
                            }
                        }
                    }, color = TriglavRed, disabledColor = white.copy(alpha = 0.2f))
                    TextButton(onClick = { way = null; problem = null }) { Text("‹ ${bi("components.back")}", color = white) }
                }
            }
        }
    }
    if (welcome) si.lanisce.lani.ui.tutor.ChatGPTWelcome { welcome = false; live.welcomedChatGPT(); onDone() }
}

/** Where an Anthropic API key is made. */
private const val ANTHROPIC_KEYS = "https://console.anthropic.com/settings/keys"

@Composable
private fun Label(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
}

/** "Uvoženih 10 datotek (Ana, z računalnika itas) · Imported 10 files (Ana, from the computer itas)". */
fun importedLine(m: DataExport.Manifest): String =
    bi("hosted.imported", "n" to m.files.size, "name" to (m.learnerName ?: "?"), "from" to (m.fromName ?: "?"), "side" to (m.fromSide ?: "node"))

/**
 * The hosted mode's splash, while the phone's bridge boots as the app starts (about half a second): what it is, a
 * spinner; or what went wrong, with "Try again".
 */
@Composable
fun HostedSplash(vm: AppViewModel) {
    val h = vm.hosted
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SloBlueDeep, SloBlue))), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("☁️", style = MaterialTheme.typography.displayMedium)
            Text(bi("hosted.title"), style = MaterialTheme.typography.titleLarge, color = Color.White)
            val err = h.bootError
            if (err == null) CircularProgressIndicator(color = Color.White)
            else {
                Text("⚠️ ${bi("hosted.bootFailed")}: $err", color = Color(0xFFFFD1D8))
                BigButton(bi("common.retry"), onClick = vm::retryHostedBoot, color = TriglavRed)
            }
        }
    }
}

/** Shares [f] (an export in the cache's shared/) through the share sheet. */
fun shareExport(context: Context, f: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, f.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private enum class Ask { NONE, IMPORT, RELAY, LEAVE }

/**
 * The settings' "🔗 Povezava · Connection" page in the hosted mode (ui/settings/SettingsScreen): "☁️ Lani na tem telefonu", the learner, the
 * export (📤, to the share sheet) and the import (📥, after asking: the data now on the phone is kept aside), the relay
 * (towns and the family page in this mode), and the way to a computer, with how the data moves (git's handover, or an
 * export and `lani import`): never silently.
 */
@Composable
fun HostedSection(vm: AppViewModel, modifier: Modifier = Modifier) {
    val h = vm.hosted
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(Ask.NONE) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var relay by remember { mutableStateOf(h.relayUrl) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            picked = uri
            ask = Ask.IMPORT
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // the sheet's status line says "☁️ Lani na tem telefonu" already
        Text(bi("hosted.onPhoneLine"), style = MaterialTheme.typography.bodyMedium)
        Text(if (h.relayUrl.isBlank()) "🛰️ ${bi("hosted.noRelay")}" else "🛰️ ${bi("hosted.relayOn", "url" to h.relayUrl.removePrefix("https://"))}", style = MaterialTheme.typography.bodySmall, color = muted)
        when (h.busy) {
            "export" -> Text("📤 ${bi("hosted.exporting")}", style = MaterialTheme.typography.bodySmall)
            "import" -> Text("📥 ${bi("hosted.importing")}", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(enabled = h.busy == null, onClick = {
            scope.launch {
                h.export().onSuccess { w ->
                    vm.notices.banner = "📤 ${bi("hosted.exported", "n" to w.files, "kb" to (w.file.length() + 1023) / 1024)}"
                    runCatching { shareExport(context, w.file) }
                }.onFailure { vm.notices.error = it.message }
            }
        }) { Text("📤 ${bi("hosted.export")}") }
        OutlinedButton(enabled = h.busy == null, onClick = { pick.launch(ZIP_TYPES) }) { Text("📥 ${bi("hosted.importExport")}") }
        OutlinedButton(onClick = { relay = h.relayUrl; ask = Ask.RELAY }) { Text("🛰️ ${bi("hosted.relay")}") }
        OutlinedButton(onClick = { ask = Ask.LEAVE }) { Text("🖥️ ${bi("hosted.toComputer")}") }
    }
    when (ask) {
        Ask.NONE -> Unit
        Ask.IMPORT -> AlertDialog(
            onDismissRequest = { ask = Ask.NONE },
            title = { Text("📥 ${bi("hosted.importAsk")}") },
            text = { Text(bi("hosted.importAskWhat")) },
            confirmButton = {
                TextButton(onClick = {
                    ask = Ask.NONE
                    val uri = picked ?: return@TextButton
                    vm.importHosted(uri)
                }) { Text(bi("hosted.replace")) }
            },
            dismissButton = { TextButton(onClick = { ask = Ask.NONE }) { Text(bi("dataSync.cancel")) } },
        )
        Ask.RELAY -> AlertDialog(
            onDismissRequest = { ask = Ask.NONE },
            title = { Text("🛰️ ${bi("hosted.relay")}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(bi("hosted.relayWhat"))
                    OutlinedTextField(relay, { relay = it.trim() }, singleLine = true, placeholder = { Text("https://relay.example") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ask = Ask.NONE
                    scope.launch { h.setRelay(relay) }
                }) { Text(bi("chatSheet.save")) }
            },
            dismissButton = { TextButton(onClick = { ask = Ask.NONE }) { Text(bi("dataSync.cancel")) } },
        )
        Ask.LEAVE -> AlertDialog(
            onDismissRequest = { ask = Ask.NONE },
            title = { Text("🖥️ ${bi("hosted.toComputer")}") },
            text = { Text(bi("hosted.toComputerWhat")) },
            confirmButton = {
                TextButton(onClick = {
                    ask = Ask.NONE
                    vm.leaveHosted()
                }) { Text(bi("hosted.switch")) }
            },
            dismissButton = { TextButton(onClick = { ask = Ask.NONE }) { Text(bi("dataSync.cancel")) } },
        )
    }
}

/**
 * On the self-hosted mode's "🔗 Povezava · Connection" settings page: "☁️ Lani na tem telefonu", the way to run Lani on the phone alone, after
 * saying how the data comes along (git's handover in 💾 Data, or `lani export` on the computer and the import here) and
 * what waits for the computer still. The setup's hosted steps follow.
 */
@Composable
fun MoveToPhoneSection(vm: AppViewModel, modifier: Modifier = Modifier) {
    var asking by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("☁️ ${bi("hosted.title")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(bi("hosted.moveWhat"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { asking = true }) { Text("☁️ ${bi("hosted.moveToPhone")}") }
    }
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text("☁️ ${bi("hosted.moveToPhone")}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bi("hosted.moveHow"))
                if (vm.waitingToSync > 0) Text("⚠️ ${bi("hosted.moveWaiting", "n" to vm.waitingToSync)}", color = TriglavRed)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                asking = false
                vm.switchToHosted()
            }) { Text(bi("hosted.switch")) }
        },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(bi("dataSync.cancel")) } },
    )
}

/**
 * "🧑‍🏫 Učitelj · Tutor" on its own, over whatever is on screen: the settings' tutor page (ui/tutor/TutorSettings),
 * opened by the hosted onboarding's tutor step.
 */
@Composable
fun TutorSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    si.lanisce.lani.ui.LaniSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            si.lanisce.lani.ui.tutor.TutorSettings(vm)
        }
    }
}
