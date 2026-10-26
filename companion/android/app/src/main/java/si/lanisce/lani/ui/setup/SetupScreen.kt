package si.lanisce.lani.ui.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ParentGate
import si.lanisce.lani.app.SetupFlow
import si.lanisce.lani.app.SetupFlow.Step
import si.lanisce.lani.app.SetupFlow.Way
import si.lanisce.lani.data.PairFailure
import si.lanisce.lani.data.PairTrouble
import si.lanisce.lani.game.Founding
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.PairDialog
import si.lanisce.lani.ui.game.LandPreview
import si.lanisce.lani.ui.hosted.HostedSetup
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.TriglavRed

private val White = Color.White
private val Muted = Color.White.copy(alpha = 0.85f)
private val Faint = Color.White.copy(alpha = 0.2f)
private val Warn = Color(0xFFFFD1D8)

/**
 * The first launch (docs/plans/05-built/onboarding.md; its state: app/SetupFlow, kept by the view model): a welcome
 * without a gender and the language to learn, the language explanations are in (the welcome in both now), then how Lani
 * runs here: on this device alone (the hosted mode's steps, ui/hosted/HostedSetup) or with the family's computer (its QR
 * code; its address and token behind "Ročno · Enter manually"). What goes wrong says so in plain words, with its way out.
 */
@Composable
fun SetupScreen(vm: AppViewModel) {
    // the settings' "☁️ Switch to this phone": the hosted mode's steps at once; back goes to the node
    if (vm.setupHosted) return HostedSetup(vm, onBack = vm::cancelSwitch)
    val flow = vm.setupFlow
    fun go(f: SetupFlow) {
        vm.setupFlow = f
    }
    BackHandler(enabled = flow.step != Step.WELCOME && !flow.busy) { go(flow.back()) }
    // the language to learn chosen: every label reads in the pair from now on (the screens are built anew in it)
    LaunchedEffect(flow.target, flow.base) { if (flow.target != null) vm.setupLanguages(flow.pair) }
    var scanning by remember { mutableStateOf(false) }

    when (flow.step) {
        Step.WELCOME -> Welcome(flow, onTarget = { go(flow.chooseTarget(it)) })
        Step.LANGUAGES -> Languages(flow, onBase = { go(flow.chooseBase(it)) }, onNext = { go(flow.next()) }, onBack = { go(flow.back()) })
        Step.WAYS -> Ways(onWay = { go(flow.choose(it)) }, onBack = { go(flow.back()) })
        Step.COMPUTER -> Computer(onScan = { scanning = true }, onManual = { go(flow.manual()) }, onBack = { go(flow.back()) })
        Step.MANUAL -> Manual(vm, flow, ::go)
        Step.DEVICE -> HostedSetup(vm, onBack = { go(flow.back()) }, chosenTarget = flow.target?.code, chosenBase = flow.base.code, onLanguages = { go(flow.copy(step = Step.LANGUAGES)) })
    }
    // paired: the app goes Home, and this screen with it; a code that asks for the address typed goes there
    if (scanning) PairDialog(vm, onDismiss = { scanning = false }, onManual = { scanning = false; go(vm.setupFlow.manual()) })
}

/** The setup's page: the blue of the first screens, centred while it fits, scrolling above the keyboard when it doesn't. */
@Composable
internal fun SetupPage(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SloBlueDeep, SloBlue))).safeDrawingPadding()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** A little of the village: the first age's fire and huts on the land of the language's region, drawn as the founding draws it. */
@Composable
private fun VillageArt(target: Lang?) {
    val culture = remember(target) {
        target?.let { t -> Cultures.ids.firstOrNull { id -> Cultures.manifest(id)?.let { it.language == t.code && it.status == "complete" } == true } }
            ?: Cultures.current.manifest.id
    }
    val village = remember(culture) { runCatching { Founding.start(culture, null, ART_SEED).preview() }.getOrNull() }
    if (village != null) LandPreview(
        village,
        Modifier.fillMaxWidth().widthIn(max = 520.dp).aspectRatio(1.9f).clip(RoundedCornerShape(16.dp)).semantics { contentDescription = "village" },
    ) else Text("🏔️", style = MaterialTheme.typography.displayLarge)
}

/** The welcome's place: the same every first launch (a reroll of the founding's dice, liked once). */
private const val ART_SEED = 7L

/**
 * The first screen, before anyone said who they are or what they learn: "Dobrodošli!" in each language (without a
 * gender), a sentence of what Lani is in the phone's language, and the language to learn, each in its own name.
 */
@Composable
private fun Welcome(flow: SetupFlow, onTarget: (Lang) -> Unit) {
    val phone = flow.base
    SetupPage {
        VillageArt(flow.target)
        Text(L10n.text(phone, "setup.hello"), style = MaterialTheme.typography.displaySmall, color = White, textAlign = TextAlign.Center)
        Text(
            SetupFlow.LANGUAGES.filter { it != phone }.joinToString(" · ") { L10n.text(it, "setup.hello") },
            style = MaterialTheme.typography.titleMedium, color = Muted, textAlign = TextAlign.Center,
        )
        Text(L10n.text(phone, "setup.what"), color = White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text(L10n.text(phone, "setup.learnWhich"), color = White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        for (l in SetupFlow.LANGUAGES) LanguageTile(l, phone, chosen = l == flow.target, onClick = { onTarget(l) })
    }
}

/** A language to learn: its own name, and its name in the phone's language below. */
@Composable
private fun LanguageTile(l: Lang, phone: Lang, chosen: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (chosen) White else Faint,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.ownName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (chosen) SloBlueDeep else White)
                val there = L10n.text(phone, "languages.${l.code}")
                if (!there.equals(l.ownName, ignoreCase = true)) Text(there, style = MaterialTheme.typography.bodySmall, color = if (chosen) SloBlueDeep else Muted)
            }
            Text(L10n.text(l, "setup.hi"), color = if (chosen) SloBlueDeep else Muted, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The language explanations are in: the welcome reads in the language learned and in it now. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Languages(flow: SetupFlow, onBase: (Lang) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    val target = flow.target ?: return
    val chips = FilterChipDefaults.filterChipColors(labelColor = White, selectedLabelColor = SloBlueDeep, selectedContainerColor = White)
    SetupPage {
        VillageArt(target)
        // the welcome in the pair: the language learned big, the explanations' under it
        Text(inTarget("setup.hello"), style = MaterialTheme.typography.displaySmall, color = White, textAlign = TextAlign.Center)
        Text(inBase("setup.hello"), style = MaterialTheme.typography.titleMedium, color = Muted, textAlign = TextAlign.Center)
        Text(inTarget("setup.what"), color = White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text(inBase("setup.what"), color = Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Text(bi("setup.explainIn"), color = White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            for (l in SetupFlow.LANGUAGES.filter { it != target }) FilterChip(flow.base == l, { onBase(l) }, { Text(l.ownName) }, colors = chips)
        }
        BigButton("${bi("common.next")} ›", onClick = onNext, color = TriglavRed)
        TextButton(onClick = onBack) { Text("‹ ${bi("components.back")}", color = White) }
    }
}

/**
 * "Kako želiš uporabljati Lani? · How do you want to use Lani?": this device alone, or with the family's computer; what
 * each gives, in a line or two for a parent or a child.
 */
@Composable
private fun Ways(onWay: (Way) -> Unit, onBack: () -> Unit) {
    SetupPage {
        Text(bi("setup.howUse"), style = MaterialTheme.typography.headlineSmall, color = White, textAlign = TextAlign.Center)
        WayCard("📱", bi("setup.deviceTitle"), bi("setup.deviceWhat"), onClick = { onWay(Way.DEVICE) })
        WayCard("🖥️", bi("setup.computerTitle"), bi("setup.computerWhat"), onClick = { onWay(Way.COMPUTER) })
        TextButton(onClick = onBack) { Text("‹ ${bi("components.back")}", color = White) }
    }
}

@Composable
private fun WayCard(emoji: String, title: String, what: String, onClick: () -> Unit) {
    Surface(
        color = Faint, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick).semantics { contentDescription = title },
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.displaySmall)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = White)
                Text(what, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = White)
        }
    }
}

/** With the family's computer: its QR code scanned, the one big button; typing its address is behind "Ročno". */
@Composable
private fun Computer(onScan: () -> Unit, onManual: () -> Unit, onBack: () -> Unit) {
    SetupPage {
        Text("🖥️", style = MaterialTheme.typography.displayMedium)
        Text(bi("setup.computerTitle"), style = MaterialTheme.typography.headlineSmall, color = White, textAlign = TextAlign.Center)
        Text(bi("setup.scanHow"), color = Muted, textAlign = TextAlign.Center)
        BigButton("📷 ${bi("setup.scan")}", onClick = onScan, color = TriglavRed)
        TextButton(onClick = onManual) { Text("⌨️ ${bi("setup.manual")}", color = White) }
        TextButton(onClick = onBack) { Text("‹ ${bi("components.back")}", color = White) }
    }
}

/**
 * "Ročno · Enter manually": the text under the QR code pasted (it pairs as the code does), or the computer's address and
 * token typed, as before. One line says where they come from.
 */
@Composable
private fun Manual(vm: AppViewModel, flow: SetupFlow, go: (SetupFlow) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = White, unfocusedTextColor = White, focusedLabelColor = White, unfocusedLabelColor = White.copy(alpha = 0.75f),
        focusedBorderColor = White, unfocusedBorderColor = White.copy(alpha = 0.6f), cursorColor = White,
    )
    fun connect() {
        val now = vm.setupFlow.connecting()
        go(now)
        if (!now.busy) return
        if (now.pasted) vm.pair(now.url.trim()) { t -> go(if (t == null) vm.setupFlow.done() else vm.setupFlow.failed(t)) }
        else scope.launch {
            val t = vm.setup(now.url, now.token)
            go(if (t == null) vm.setupFlow.done() else vm.setupFlow.failed(t))
        }
    }
    SetupPage {
        Text("⌨️ ${bi("setup.manual")}", style = MaterialTheme.typography.headlineSmall, color = White)
        Text(bi("setup.manualHint"), color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            flow.url, { go(vm.setupFlow.edit(url = it)) }, Modifier.fillMaxWidth(),
            label = { Text(bi("setup.address")) }, singleLine = true, colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        if (!flow.pasted) OutlinedTextField(
            flow.token, { go(vm.setupFlow.edit(token = it)) }, Modifier.fillMaxWidth(),
            label = { Text(bi("setup.token")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        flow.trouble?.let { t -> TroubleCard(t, onRetry = ::connect, onScan = { go(vm.setupFlow.copy(step = Step.COMPUTER, trouble = null)) }, onManual = { go(vm.setupFlow.out()) }, context = context) }
        BigButton(
            if (flow.busy) bi("setupAndPermission.connecting") else bi("setupAndPermission.connect"),
            enabled = flow.canConnect, onClick = ::connect, color = TriglavRed, disabledColor = Faint,
        )
        TextButton(onClick = { go(flow.back()) }, enabled = !flow.busy) { Text("‹ ${bi("components.back")}", color = White) }
    }
}

/**
 * What went wrong, in plain words, with its way out: "Poskusi znova · Try again" (and, the computer out of reach, the
 * Tailscale app), "Skeniraj znova · Scan again" for a code that can't be used, the address typed for a computer whose Lani
 * can't pair by code yet; what was typed wrong is fixed in its field. Also in the scanner (ui/PairScan).
 */
@Composable
internal fun TroubleCard(t: PairTrouble, onRetry: () -> Unit, onScan: () -> Unit, onManual: () -> Unit, context: Context = LocalContext.current) {
    Surface(color = Color.Black.copy(alpha = 0.25f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.message, color = Warn)
            when (t.out) {
                PairFailure.Out.RETRY -> {
                    BigButton("🔄 ${bi("pairScan.tryAgain")}", onClick = onRetry, color = TriglavRed)
                    if (t.failure == PairFailure.UNREACHABLE) TextButton(onClick = { openTailscale(context) }) { Text("🛡️ ${bi("setup.openTailscale")}", color = White) }
                }
                PairFailure.Out.SCAN -> BigButton("📷 ${bi("setup.scanAgain")}", onClick = onScan, color = TriglavRed)
                PairFailure.Out.MANUAL -> BigButton("⌨️ ${bi("setup.manual")}", onClick = onManual, color = TriglavRed)
                PairFailure.Out.FIX -> Unit
            }
        }
    }
}

/** The Tailscale app, else its page in the store (Play, else the web's). */
internal fun openTailscale(context: Context) {
    val app = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(TAILSCALE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$TAILSCALE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$TAILSCALE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    for (i in listOf(app, store, web)) {
        try {
            context.startActivity(i)
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}

private const val TAILSCALE = "com.tailscale.ipn"

/**
 * A child's device (app/ParentGate): "👪 To nastavi starš · A parent sets this up" and a button a parent holds for two
 * seconds, its fill growing as it's held; then [content] (the tutor's key, an account). A grown-up's shows [content].
 * [onLight]: on a sheet's light surface; false on the setup's blue (white text).
 */
@Composable
fun ParentGated(child: Boolean, modifier: Modifier = Modifier, onLight: Boolean = true, content: @Composable () -> Unit) {
    var gate by remember(child) { mutableStateOf(ParentGate(child)) }
    if (!gate.locked) return content()
    val ink = if (onLight) MaterialTheme.colorScheme.onSurface else White
    val muted = if (onLight) MaterialTheme.colorScheme.onSurfaceVariant else Muted
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("👪 ${bi("setup.parentSets")}", style = MaterialTheme.typography.titleSmall, color = ink)
        Text(bi("setup.parentSetsWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
        HoldToOpen("🔒 ${bi("setup.parentHold")}", onLight = onLight, onOpen = { gate = gate.held(ParentGate.HOLD_MS) })
    }
}

/** A button that opens only when held ([ParentGate.HOLD_MS]): it fills as it's held, and empties again when let go early. */
@Composable
private fun HoldToOpen(label: String, onLight: Boolean, onOpen: () -> Unit) {
    var pressing by remember { mutableStateOf(false) }
    val fill = remember { Animatable(0f) }
    LaunchedEffect(pressing) {
        if (pressing) {
            fill.animateTo(1f, tween(ParentGate.HOLD_MS.toInt(), easing = LinearEasing))
            onOpen()
        } else fill.animateTo(0f, tween(200))
    }
    val back = if (onLight) MaterialTheme.colorScheme.surfaceVariant else Faint
    val bar = if (onLight) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else White.copy(alpha = 0.35f)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(back)
            .semantics { contentDescription = label }
            .pointerInput(Unit) { detectTapGestures(onPress = { pressing = true; tryAwaitRelease(); pressing = false }) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(fill.value).background(bar))
        Text(label, color = if (onLight) MaterialTheme.colorScheme.onSurface else White, modifier = Modifier.padding(12.dp))
    }
}
