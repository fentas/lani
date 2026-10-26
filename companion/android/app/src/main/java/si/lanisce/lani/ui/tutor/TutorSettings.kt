package si.lanisce.lani.ui.tutor

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.tutor.Billing
import si.lanisce.lani.tutor.ChatGPTOAuth
import si.lanisce.lani.tutor.Effort
import si.lanisce.lani.tutor.Family
import si.lanisce.lani.tutor.GeminiTerms
import si.lanisce.lani.tutor.ModelCatalog
import si.lanisce.lani.tutor.OpenRouterLinks
import si.lanisce.lani.tutor.OpenRouterModels
import si.lanisce.lani.tutor.Preset
import si.lanisce.lani.tutor.ProviderId
import si.lanisce.lani.tutor.Task
import si.lanisce.lani.tutor.live.LiveState
import si.lanisce.lani.tutor.live.LiveTutor

/**
 * "🧑‍🏫 Učitelj · Tutor" (docs/plans/05-built/tutor-live.md), in the connection sheet: who answers, Claude Code on the
 * node (as before, the default) or the tutor in the app; then, for the app, the provider and its credential (a Claude
 * API key, an OpenAI API key or "Continue with ChatGPT", a Gemini key from Google AI Studio, "Continue with
 * OpenRouter"), the preset with "advanced" per task, the daily cap and
 * the spend, "react at once", what the tutor knows (its notebook). The credentials are the shadow tutor's (one vault):
 * encrypted with a Keystore key, never shown again, logged or exported.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TutorSettings(vm: AppViewModel, modifier: Modifier = Modifier) {
    val live = vm.live
    val s = live.settings
    val scope = rememberCoroutineScope()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    /** Lani on this phone (hosted/HostedController): no node, the tutor in the app is the only one. */
    val hosted = vm.hosted.active
    /** "In the app" chosen here (before it's on: the credential first); always, on the phone. */
    var wantApp by remember { mutableStateOf(s.on || hosted) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(live.today.calls.size) {
        live.refreshCredentials()
        month = live.spentThisMonth()
    }
    fun switch(on: Boolean) {
        busy = true
        note = null
        scope.launch {
            note = live.setOn(on)?.let { "⚠️ $it" }
            busy = false
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🧑‍🏫 ${bi("tutorLive.title")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (hosted) Text(bi("hosted.tutorOnly"), style = MaterialTheme.typography.bodySmall, color = muted)
        else Text(bi("tutorLive.who"), style = MaterialTheme.typography.bodySmall, color = muted)
        if (!hosted) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = !wantApp && !s.on, enabled = !busy, modifier = Modifier.testTag("tutor-node"),
                onClick = { wantApp = false; if (s.on) switch(false) },
                label = { Text("🖥️ ${bi("tutorLive.node")}") },
            )
            FilterChip(
                selected = wantApp || s.on, enabled = !busy, modifier = Modifier.testTag("tutor-app"),
                onClick = { wantApp = true; if (!s.on && live.credentialReady()) switch(true) },
                label = { Text("📱 ${bi("tutorLive.app")}") },
            )
        }
        Text(
            // on the phone, off means no credential yet: no Claude Code to answer there
            if (hosted && live.state == LiveState.OFF) bi("hosted.tutorOff") else stateLine(live),
            style = MaterialTheme.typography.bodySmall,
            color = if (live.state.name.startsWith("PAUSED") || live.state == LiveState.OLD_NODE) MaterialTheme.colorScheme.error else muted,
            modifier = Modifier.semantics { contentDescription = "tutor-state:${live.state.name}" },
        )
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (!wantApp && !s.on) {
            Text(bi("tutorLive.nodeWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
            return@Column
        }
        // the AI disclosure (the Usage Policy's, for consumer chatbots; plan 5): before anything is set
        Text("🤖 ${bi("tutorLive.disclosure")}", style = MaterialTheme.typography.bodySmall)

        // --- the provider and its credential -------------------------------------------------------------------------
        Text(bi("tutorLive.provider"), style = MaterialTheme.typography.bodySmall, color = muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = s.provider == ProviderId.ANTHROPIC, onClick = { live.update { it.copy(provider = ProviderId.ANTHROPIC, models = emptyMap()) } }, label = { Text("Anthropic · Claude") })
            if (live.openaiAvailable) FilterChip(selected = s.provider == ProviderId.OPENAI, onClick = { live.update { it.copy(provider = ProviderId.OPENAI, models = emptyMap()) } }, label = { Text("OpenAI · GPT") })
            if (live.geminiAvailable) FilterChip(
                selected = s.provider == ProviderId.GEMINI, modifier = Modifier.testTag("tutor-gemini"),
                onClick = { live.update { it.copy(provider = ProviderId.GEMINI, models = emptyMap()) } }, label = { Text("Google · Gemini") },
            )
            if (live.openrouterAvailable) FilterChip(
                selected = s.provider == ProviderId.OPENROUTER, modifier = Modifier.testTag("tutor-openrouter"),
                onClick = { live.update { it.copy(provider = ProviderId.OPENROUTER, models = emptyMap()) } }, label = { Text("OpenRouter") },
            )
        }
        val kept = { if (wantApp && !s.on) switch(true) }
        when (s.provider) {
            ProviderId.ANTHROPIC -> ClaudeKey(live, kept)
            ProviderId.OPENAI -> OpenAICredential(live, kept)
            ProviderId.GEMINI -> GeminiCredential(live, kept)
            ProviderId.OPENROUTER -> OpenRouterCredential(live, kept)
        }
        if (live.debug) DebugBase(live)
        if (!live.credentialReady()) return@Column
        if (!s.on && wantApp) Button(enabled = !busy, onClick = { switch(true) }, modifier = Modifier.testTag("tutor-on")) { Text("📱 ${bi("tutorLive.turnOn")}") }

        // --- the preset, advanced ------------------------------------------------------------------------------------
        Text(bi("tutorLive.preset"), style = MaterialTheme.typography.bodySmall, color = muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in Preset.entries) FilterChip(
                selected = s.preset == p,
                onClick = { live.update { it.copy(preset = p, models = emptyMap(), efforts = emptyMap()) } },
                label = { Text(presetLabel(p)) },
            )
        }
        var advanced by remember { mutableStateOf(s.models.isNotEmpty() || s.efforts.isNotEmpty()) }
        TextButton(onClick = { advanced = !advanced }) { Text((if (advanced) "▾ " else "▸ ") + bi("tutorLive.advanced")) }
        if (advanced) for (t in Task.entries) TaskLine(live, t)

        // --- the cap and the spend ------------------------------------------------------------------------------------
        Text(bi("tutorLive.cap"), style = MaterialTheme.typography.bodySmall, color = muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (cap in CAPS) FilterChip(selected = s.dailyCap == cap, onClick = { live.update { it.copy(dailyCap = cap) } }, label = { Text(money(cap)) })
        }
        Text(
            "💶 " + bi("tutorLive.spend", "today" to money(si.lanisce.lani.tutor.Meter.spend(live.today.calls)), "cap" to money(s.dailyCap), "month" to money(month ?: 0.0)),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("tutor-spend"),
        )
        // the monthly cap beside the daily one, and what can wait at half price (docs/plans/05-built/studio.md)
        MonthlyCap(live)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bi("tutorLive.reactAtOnce"), style = MaterialTheme.typography.bodyMedium)
                Text(bi("tutorLive.reactWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Switch(
                checked = s.reactAtOnce, onCheckedChange = { on -> live.update { it.copy(reactAtOnce = on) } },
                modifier = Modifier.testTag("tutor-react").semantics { contentDescription = if (s.reactAtOnce) "React at once: on" else "React at once: off" },
            )
        }
        if (s.on) StudioJobs(live)
        if (s.on) Notebook(live)
    }
}

/** The state in a line: who answers now, or why the tutor in the app waits. */
@Composable
private fun stateLine(live: LiveTutor): String = when (live.state) {
    LiveState.OFF -> bi("tutorLive.state.OFF")
    LiveState.NO_CREDENTIAL -> bi("tutorLive.state.NO_CREDENTIAL")
    LiveState.NO_NODE -> bi("tutorLive.state.NO_NODE")
    LiveState.OLD_NODE -> bi("tutorLive.state.OLD_NODE")
    LiveState.IDLE -> bi("tutorLive.state.IDLE")
    LiveState.RUNNING -> bi("tutorLive.state.RUNNING")
    LiveState.WAITING -> bi("tutorLive.state.WAITING", "n" to live.waiting)
    LiveState.OFFLINE -> bi("tutorLive.state.OFFLINE", "n" to live.waiting)
    LiveState.PAUSED_KEY -> bi("tutorLive.state.PAUSED_KEY")
    LiveState.PAUSED_CREDIT -> bi("tutorLive.state.PAUSED_CREDIT")
    LiveState.PAUSED_BUDGET -> bi("tutorLive.state.PAUSED_BUDGET")
    LiveState.PAUSED_MONTH -> bi("tutorLive.state.PAUSED_MONTH")
}

private fun presetLabel(p: Preset): String = when (p) {
    Preset.ECONOMY -> bi("tutorLive.economy")
    Preset.BALANCED -> bi("tutorLive.balanced") + " ⭐"
    Preset.BEST -> bi("tutorLive.best")
}

/** A task's name under "advanced". */
private fun taskLabel(t: Task): String = when (t) {
    Task.ROUTINE -> bi("tutorLive.task.ROUTINE")
    Task.GRADING -> bi("tutorLive.task.GRADING")
    Task.CHAT -> bi("tutorLive.task.CHAT")
    Task.ROLEPLAY -> bi("tutorLive.task.ROLEPLAY")
    Task.CONTENT -> bi("tutorLive.task.CONTENT")
}

private fun money(d: Double) = "$" + "%.2f".format(d)

private val CAPS = listOf(0.5, 1.0, 2.0, 5.0, 10.0)

/** The Claude API key: entered once, checked with GET /v1/models (not a model call), then only "kept". */
@Composable
private fun ClaudeKey(live: LiveTutor, kept: () -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text("🔑 ${bi("tutorLive.claudeKey")}", style = MaterialTheme.typography.bodyMedium)
    if (live.hasKey && !live.settings.keyRefused) {
        Text(bi("tutorLive.keyKept"), style = MaterialTheme.typography.bodySmall, color = muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !checking, onClick = { checking = true; scope.launch { note = live.recheck()?.let { "⚠️ $it" } ?: "✓ ${bi("tutorLive.checked")}"; checking = false } }) { Text(bi("tutorLive.checkAgain")) }
            TextButton(onClick = { live.removeKey(); note = null }) { Text(bi("tutorLive.keyRemove")) }
        }
    } else {
        if (live.settings.keyRefused) Text("⚠️ ${bi("tutorLive.keyRefused")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Text(bi("tutorLive.keyHint"), style = MaterialTheme.typography.bodySmall, color = muted)
        OutlinedTextField(
            key, { key = it.trim() }, Modifier.fillMaxWidth().testTag("tutor-key"),
            label = { Text("sk-ant-…") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(enabled = key.isNotBlank() && !checking, modifier = Modifier.testTag("tutor-key-check"), onClick = {
            checking = true
            scope.launch {
                val e = live.setKey(key)
                note = e?.let { "⚠️ $it" } ?: "✓ ${bi("tutorLive.keyWorks")}"
                if (e == null) {
                    key = ""
                    kept()
                }
                checking = false
            }
        }) { Text(if (checking) "…" else bi("tutorLive.keyCheck")) }
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

/** OpenAI: "Continue with ChatGPT" (the plan pays), or an API key; "Using ChatGPT plan" while the plan is in use. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenAICredential(live: LiveTutor, kept: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var welcome by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (welcome) ChatGPTWelcome { welcome = false; live.welcomedChatGPT() }
    fun browse(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val account = live.chatgptAccount
    if (account != null && Billing.CHATGPT_PLAN in live.openaiReady) {
        Text("✓ ${bi("tutorLive.signedInAs", "who" to account)} · ${bi("tutorLive.chatPlan")}", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { browse(ChatGPTOAuth.MANAGE_USAGE) }) { Text(bi("tutorLive.manageUsage")) }
            TextButton(enabled = !busy, onClick = { busy = true; scope.launch { note = live.signOutOfChatGPT(); busy = false } }) { Text(bi("tutorLive.signOut")) }
        }
    } else {
        Text(bi("tutorLive.chatgptWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
        Button(enabled = !busy, onClick = {
            busy = true
            note = null
            scope.launch {
                val e = live.signInWithChatGPT { url -> browse(url) }
                note = e?.let { "⚠️ $it" }
                // OpenAI's UI guidelines: the welcome once, at the first sign-in with the plan
                if (e == null && !live.settings.chatgptWelcomed) welcome = true
                if (e == null) kept()
                busy = false
            }
        }) { Text(if (busy) "…" else bi("tutorLive.continueChatGPT")) }
        if (busy) TextButton(onClick = { live.cancelSignIn(); busy = false }) { Text(bi("tutorLive.cancel")) }
    }
    Text("🔑 ${bi("tutorLive.openaiKey")}", style = MaterialTheme.typography.bodyMedium)
    if (Billing.API_KEY in live.openaiReady) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(bi("tutorLive.keyKept"), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.weight(1f))
            TextButton(onClick = { live.removeOpenAIKey() }) { Text(bi("tutorLive.keyRemove")) }
        }
    } else {
        OutlinedTextField(
            key, { key = it.trim() }, Modifier.fillMaxWidth().testTag("tutor-openai-key"),
            label = { Text("sk-…") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(enabled = key.isNotBlank() && !busy, onClick = {
            busy = true
            scope.launch {
                val e = live.setOpenAIKey(key)
                note = e?.let { "⚠️ $it" } ?: "✓ ${bi("tutorLive.keyWorks")}"
                if (e == null) {
                    key = ""
                    kept()
                }
                busy = false
            }
        }) { Text(if (busy) "…" else bi("tutorLive.keyCheck")) }
    }
    if (live.openaiReady.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = live.openaiBilling() == Billing.CHATGPT_PLAN, onClick = { live.update { it.copy(openai = Billing.CHATGPT_PLAN) } }, label = { Text("ChatGPT") })
        FilterChip(selected = live.openaiBilling() == Billing.API_KEY, onClick = { live.update { it.copy(openai = Billing.API_KEY) } }, label = { Text("API") })
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

/**
 * Gemini, direct: a key from Google AI Studio, checked with the model list (not a model call), and the learner's word
 * on its tier (the API doesn't tell; a 429 that names the free tier's quota does). On the free tier, what Google's terms
 * say of it, plainly (docs/plans/05-built/providers.md, "The free tier and privacy").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeminiCredential(live: LiveTutor, kept: () -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var tier by remember(live.geminiTier) { mutableStateOf(live.geminiTier?.let { if (GeminiTerms.isFree(it)) GeminiTerms.FREE else it }) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text("🔑 ${bi("tutorLive.geminiKey")}", style = MaterialTheme.typography.bodyMedium)
    val ready = live.geminiReady && !live.settings.keyRefused
    if (!ready) Text(bi("tutorLive.geminiHint"), style = MaterialTheme.typography.bodySmall, color = muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = tier == GeminiTerms.FREE, modifier = Modifier.testTag("gemini-free"), onClick = { tier = GeminiTerms.FREE; if (ready) live.setGeminiTier(GeminiTerms.FREE) }, label = { Text(bi("tutorLive.geminiFree")) })
        FilterChip(selected = tier == GeminiTerms.PAID, modifier = Modifier.testTag("gemini-paid"), onClick = { tier = GeminiTerms.PAID; if (ready) live.setGeminiTier(GeminiTerms.PAID) }, label = { Text(bi("tutorLive.geminiPaid")) })
    }
    if (live.geminiTier == GeminiTerms.FREE_SEEN) Text(bi("tutorLive.geminiSeen"), style = MaterialTheme.typography.bodySmall, color = muted)
    if (tier == GeminiTerms.FREE || (ready && live.geminiFree)) {
        Text("⚠️ ${bi("tutorLive.geminiFreeNote")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("gemini-free-note"))
    }
    if (ready) {
        Text(bi("tutorLive.keyKept"), style = MaterialTheme.typography.bodySmall, color = muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { busy = true; scope.launch { note = live.recheck()?.let { "⚠️ $it" } ?: "✓ ${bi("tutorLive.checked")}"; busy = false } }) { Text(bi("tutorLive.checkAgain")) }
            TextButton(onClick = { live.removeGeminiKey(); note = null }) { Text(bi("tutorLive.keyRemove")) }
        }
    } else {
        if (live.settings.keyRefused && live.geminiReady) Text("⚠️ ${bi("tutorLive.keyRefused")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        OutlinedTextField(
            key, { key = it.trim() }, Modifier.fillMaxWidth().testTag("tutor-gemini-key"),
            label = { Text("AIza…") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(enabled = key.isNotBlank() && !busy, modifier = Modifier.testTag("tutor-gemini-check"), onClick = {
            busy = true
            scope.launch {
                val e = live.setGeminiKey(key, tier)
                note = e?.let { "⚠️ $it" } ?: "✓ ${bi("tutorLive.keyWorks")}"
                if (e == null) {
                    key = ""
                    kept()
                }
                busy = false
            }
        }) { Text(if (busy) "…" else bi("tutorLive.keyCheck")) }
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

/**
 * OpenRouter, "one sign-in, every model": "Continue with OpenRouter" (OAuth PKCE: the browser, then back to Lani with
 * a key of the learner's own, billed to their OpenRouter credits), the key's credit, the models per task under
 * "advanced"; that Claude works best directly, and OpenRouter's fee on credits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenRouterCredential(live: LiveTutor, kept: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun browse(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Text(bi("tutorLive.openrouterWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
    Text("ℹ️ ${bi("tutorLive.openrouterClaude")}", style = MaterialTheme.typography.bodySmall, color = muted)
    if (live.openrouterReady && !live.settings.keyRefused) {
        Text("✓ ${bi("tutorLive.openrouterSignedIn")}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("openrouter-signed-in"))
        live.openrouterKey?.let { k ->
            if (k.limit != null && k.remaining != null) Text(bi("tutorLive.openrouterCredit", "left" to money(k.remaining), "limit" to money(k.limit)), style = MaterialTheme.typography.bodySmall, color = muted)
            k.usageMonthly?.let { Text(bi("tutorLive.openrouterUsed", "month" to money(it)), style = MaterialTheme.typography.bodySmall, color = muted) }
        }
        Text(bi("tutorLive.openrouterModels"), style = MaterialTheme.typography.bodySmall, color = muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { busy = true; scope.launch { note = live.recheck()?.let { "⚠️ $it" } ?: "✓ ${bi("tutorLive.checked")}"; busy = false } }) { Text(bi("tutorLive.checkAgain")) }
            TextButton(onClick = { browse(OpenRouterLinks.KEYS) }) { Text(bi("tutorLive.openrouterKeys")) }
            TextButton(enabled = !busy, onClick = { busy = true; scope.launch { note = live.signOutOfOpenRouter(); busy = false } }) { Text(bi("tutorLive.signOut")) }
        }
    } else {
        if (live.settings.keyRefused && live.openrouterReady) Text("⚠️ ${bi("tutorLive.keyRefused")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy, modifier = Modifier.testTag("tutor-openrouter-signin"), onClick = {
            busy = true
            note = null
            scope.launch {
                val e = live.signInWithOpenRouter { url -> browse(url) }
                note = e?.let { "⚠️ $it" }
                if (e == null) kept()
                busy = false
            }
        }) { Text(if (busy) "…" else bi("tutorLive.continueOpenRouter")) }
        if (busy) TextButton(onClick = { live.cancelOpenRouterSignIn(); busy = false }) { Text(bi("tutorLive.cancel")) }
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

/** A debug or qa build's base URL for Claude's calls (QA's mock); a release build never shows it. */
@Composable
private fun DebugBase(live: LiveTutor) {
    var url by remember(live.settings.debugBaseUrl) { mutableStateOf(live.settings.debugBaseUrl.orEmpty()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(url, { url = it.trim() }, Modifier.weight(1f).testTag("tutor-base"), singleLine = true, label = { Text("🧪 API base URL (QA)") })
        TextButton(onClick = { live.update { it.copy(debugBaseUrl = url.ifBlank { null }) } }) { Text("OK") }
    }
}

/** One task's model and effort under "advanced": the preset's (⭐ our recommendation), or one set here (✎). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskLine(live: LiveTutor, task: Task) {
    val s = live.settings
    val model = live.modelFor(task)
    val effort = live.effortFor(task, model)
    var modelMenu by remember { mutableStateOf(false) }
    var effortMenu by remember { mutableStateOf(false) }
    val listed = s.listed.map { it.id }.filter { ProviderId.of(it) == s.provider }
    val own = when (s.provider) {
        // OpenRouter's list (the models that take tools), its suggestions first
        ProviderId.OPENROUTER -> (OpenRouterModels.SUGGESTED.filter { listed.isEmpty() || it in listed } + listed).distinct()
        else -> (listed.ifEmpty { Family.of(s.provider).flatMap { it.preferred.take(1) } }).filter { !ModelCatalog.legacy(it) }
    }
    // the other providers with a credential kept: their families' models (OpenRouter's suggestions)
    val others = ProviderId.entries.filter { it != s.provider && live.ready(it) }.flatMap { p ->
        if (p == ProviderId.OPENROUTER) OpenRouterModels.SUGGESTED else Family.of(p).map { it.preferred.first() }
    }
    // never GPT-6 Luna for the tutor (the matrix, 2026-10: Family.matrixOnly); a task set to it before keeps it
    val models = (own + others).filter { !Family.matrixOnly(it) }
    // ⭐ our recommendation for the model's provider: the Balanced preset's effort there (Sol's medium on OpenAI)
    val recommended = Preset.BALANCED.effort(task, ProviderId.of(model))
    FlowRow(verticalArrangement = Arrangement.Center) {
        Text(taskLabel(task), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp, top = 12.dp))
        Box {
            TextButton(onClick = { modelMenu = true }) { Text(model + if (task in s.models) " ✎" else "") }
            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                DropdownMenuItem(text = { Text(bi("tutorLive.asPreset")) }, onClick = { modelMenu = false; live.update { it.copy(models = it.models - task) } })
                models.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { modelMenu = false; live.update { it.copy(models = it.models + (task to m)) } }) }
            }
        }
        Box {
            val levels = ModelCatalog.efforts(model).filter { it !in Effort.MORE || it == effort }
            TextButton(enabled = levels.isNotEmpty(), onClick = { effortMenu = true }) { Text("${bi("tutorLive.effort")}: ${effort ?: "—"}" + if (task in s.efforts) " ✎" else "") }
            DropdownMenu(expanded = effortMenu, onDismissRequest = { effortMenu = false }) {
                DropdownMenuItem(text = { Text(bi("tutorLive.asPreset")) }, onClick = { effortMenu = false; live.update { it.copy(efforts = it.efforts - task) } })
                ModelCatalog.efforts(model).forEach { l -> DropdownMenuItem(text = { Text(l + if (l == recommended) " ⭐" else "") }, onClick = { effortMenu = false; live.update { it.copy(efforts = it.efforts + (task to l)) } }) }
            }
        }
    }
}

/** "📓 Kaj ve učitelj · What my tutor knows": the notebook on the node, read and corrected by the learner. */
@Composable
private fun Notebook(live: LiveTutor) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = {
        open = !open
        if (open) scope.launch { text = live.loadNotebook().text }
    }) { Text((if (open) "▾ " else "▸ ") + "📓 ${bi("tutorLive.notebook")}") }
    if (!open) return
    if (editing) {
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 4)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { note = live.editNotebook(text)?.let { "⚠️ $it" }; if (note == null) editing = false } }) { Text(bi("tutorLive.save")) }
            TextButton(onClick = { editing = false }) { Text(bi("tutorLive.cancel")) }
        }
    } else {
        Text(text.ifBlank { bi("tutorLive.notebookEmpty") }, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { editing = true }) { Text("✏️ ${bi("tutorLive.notebookEdit")}") }
        // the bridge keeps the last versions (app/tutor/notebook-versions.json): see one, put it back
        NotebookVersions(live) { restored -> text = restored }
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

/**
 * The chat's line while the tutor in the app answers: an AI answers here (the Usage Policy's disclosure for consumer
 * chatbots), on whose model; "Using ChatGPT plan" when the plan pays (OpenAI's UI guidelines).
 */
@Composable
fun AiDisclosure(live: LiveTutor) {
    val plan = live.settings.provider == ProviderId.OPENAI && live.openaiBilling() == Billing.CHATGPT_PLAN
    val free = live.settings.provider == ProviderId.GEMINI && live.geminiFree
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "🤖 ${bi("tutorLive.chatDisclosure", "model" to live.modelFor(Task.CHAT))}" + (if (plan) " · ${bi("tutorLive.chatPlan")}" else "") +
                if (free) " · ${bi("tutorLive.geminiTag")}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp).weight(1f, fill = false).testTag("tutor-disclosure"),
        )
        // OpenAI's UI guidelines: "Manage usage" near the composer while the plan pays
        if (plan) TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ChatGPTOAuth.MANAGE_USAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text(bi("tutorLive.manageUsage"), style = MaterialTheme.typography.labelSmall) }
    }
}
