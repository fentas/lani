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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import si.lanisce.lani.data.HintVoice
import si.lanisce.lani.game.Founding
import si.lanisce.lani.game.HintPiece
import si.lanisce.lani.game.SetupSpeech
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.PairDialog
import si.lanisce.lani.ui.game.LandPreview
import si.lanisce.lani.ui.game.ReadAloudButton
import si.lanisce.lani.ui.hosted.HostedSetup
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.TriglavRed

private val White = Color.White
private val Muted = Color.White.copy(alpha = 0.85f)
private val Faint = Color.White.copy(alpha = 0.2f)
private val Warn = Color(0xFFFFD1D8)

/**
 * The first run (docs/plans/05-built/first-minutes.md; its state: app/SetupFlow, kept by the view model): a welcome without a
 * gender and the language to learn, then the name and "For a child", then the founding (over Home). A small link on the
 * first screen leads to the other ways in: the family's computer (its QR code; its address and token behind "Ročno · Enter
 * manually") and an export imported (ui/hosted/HostedSetup, which also holds a learner already on this device). Every step
 * reads itself aloud as it opens and on its 🔊 (game/SetupSpeech); a language's tile says its name. What goes wrong says so
 * in plain words, with its way out.
 */
@Composable
fun SetupScreen(vm: AppViewModel) {
    // the settings' "☁️ Switch to this phone": the hosted mode's steps at once; back goes to the node
    if (vm.setupHosted) return HostedSetup(vm, onBack = vm::cancelSwitch)
    val flow = vm.setupFlow
    fun go(f: SetupFlow) {
        vm.setupFlow = f
    }
    // Back: a step back, as "‹ Nazaj"
    BackHandler(enabled = flow.step != Step.WELCOME && !flow.busy) { go(flow.back()) }
    // the language to learn chosen, or the other ways in opened: every label reads in the pair from now on (the screens are
    // built anew in it)
    LaunchedEffect(flow.target, flow.base, flow.step == Step.OTHER) { if (flow.target != null || flow.step == Step.OTHER) vm.setupLanguages(flow.pair) }
    var scanning by remember { mutableStateOf(false) }
    // a learner on this device already (an earlier start, or what git pulled): go on, start afresh or import
    val onDevice = remember { vm.hosted.learnerOnPhone() != null }
    val hosted = flow.step == Step.DEVICE || (flow.step == Step.NAME && onDevice)
    // the step read as it opens (its own pieces: the hosted steps read theirs)
    val speech = remember(flow.step, flow.target, flow.base) { stepSpeech(flow) }
    if (!hosted) SetupReading(vm, readKey(flow), speech)
    // a language's tile or chip: its name, in it ("italiano"), as it's chosen (TalkBack says the tile's text itself)
    fun sayName(l: Lang) {
        if (!vm.introVoice.screenReader) vm.hintVoice.play(SetupSpeech.name(l), needsBase = false)
    }

    if (hosted) HostedSetup(
        vm, onBack = { go(flow.back()) }, chosenTarget = flow.target?.code, chosenBase = flow.base.code,
        onLanguages = { go(flow.copy(step = Step.WELCOME)) }, importing = flow.step == Step.DEVICE,
    ) else when (flow.step) {
        Step.WELCOME -> Welcome(vm, flow, speech, onTarget = { sayName(it); go(flow.chooseTarget(it)) }, onOther = { go(flow.other()) })
        Step.NAME -> Name(vm, flow, speech, onBase = { if (it != flow.base) sayName(it); go(vm.setupFlow.chooseBase(it)) }, go = ::go)
        Step.OTHER -> Other(vm, speech, onWay = { go(flow.choose(it)) }, onFamily = { go(flow.family()) }, onBack = { go(flow.back()) })
        Step.FAMILY -> Family(vm, speech, ::go)
        Step.COMPUTER -> Computer(vm, speech, onScan = { scanning = true }, onManual = { go(flow.manual()) }, onBack = { go(flow.back()) })
        Step.MANUAL -> Manual(vm, flow, speech, ::go)
        Step.DEVICE -> Unit
    }
    // paired: the app goes Home, and this screen with it; a code that asks for the address typed goes there
    if (scanning) PairDialog(vm, onDismiss = { scanning = false }, onManual = { scanning = false; go(vm.setupFlow.manual()) })
}

/** What [flow]'s step says (game/SetupSpeech); the hosted steps say their own. */
private fun stepSpeech(flow: SetupFlow): List<HintPiece> = when (flow.step) {
    Step.WELCOME -> SetupSpeech.welcome(flow.base)
    Step.NAME -> SetupSpeech.who(flow.pair)
    Step.OTHER -> SetupSpeech.other(flow.pair)
    Step.FAMILY -> SetupSpeech.family(flow.pair)
    Step.COMPUTER -> SetupSpeech.computer(flow.pair)
    Step.MANUAL -> SetupSpeech.manual(flow.pair)
    Step.DEVICE -> emptyList()
}

/** A step once: the welcome again in another phone language (QA's hook) is another. */
private fun readKey(flow: SetupFlow): String = if (flow.step == Step.WELCOME) "WELCOME:${flow.base.code}" else flow.step.name

/**
 * The first launch's step [key] read aloud as it opens ([speech]), as the intro and the founding are: when the school's
 * "🔊 Beri razlago · Read the explanations" is on (the default) and no screen reader is (TalkBack reads the screen
 * itself). Once per step: its screen built anew in a language just chosen doesn't read it again (the chip said its
 * name). Off, the reading of the step before stops; a name just said is let finish.
 */
@Composable
internal fun SetupReading(vm: AppViewModel, key: String, speech: List<HintPiece>) {
    LaunchedEffect(key) {
        if (vm.setupRead == key) return@LaunchedEffect
        vm.setupRead = key
        val v = vm.hintVoice
        if (vm.introVoice.auto && !vm.introVoice.screenReader && speech.isNotEmpty()) v.play(speech, needsBase = false)
        else if ((v.current?.size ?: 0) > 1) v.stop()
    }
}

/**
 * The setup's page: the blue of the first screens, centred while it fits, scrolling above the keyboard when it doesn't;
 * at most 640 dp wide (a tablet). [side]: the picture and the greeting, beside the rest on a wide screen on its side (Teo's
 * tablet), above it otherwise. The step's 🔊 ([speech], [voice]) at the top right, a tap reads it again or stops it.
 */
@Composable
internal fun SetupPage(
    voice: HintVoice? = null,
    speech: List<HintPiece> = emptyList(),
    side: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SloBlueDeep, SloBlue))).safeDrawingPadding()) {
        val height = maxHeight
        val top = if (voice != null && speech.isNotEmpty()) 52.dp else 24.dp
        if (side != null && maxWidth >= 840.dp && maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                for (part in listOf(side, content)) Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).heightIn(min = height).padding(top = top, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = part,
                )
            }
        } else Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = height).padding(start = 24.dp, end = 24.dp, top = top, bottom = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                side?.invoke(this)
                content()
            }
        }
        ReadAloudButton(
            voice, speech, Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.18f)),
            color = White, stopOnLeave = false,
        )
    }
}

/** A little of the village: the first age's fire and huts on the land of the language's region, drawn as the founding draws it. */
@Composable
private fun VillageArt(target: Lang?) {
    // the region of the language chosen, or the village's once a bridge has said it (setting up again): VillageCulture
    val culture = remember(target) { si.lanisce.lani.game.culture.VillageCulture.preview(target) }
    val village = remember(culture) { runCatching { Founding.start(culture, null, ART_SEED).preview() }.getOrNull() }
    if (village != null) LandPreview(
        village,
        culture,
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
private fun Welcome(vm: AppViewModel, flow: SetupFlow, speech: List<HintPiece>, onTarget: (Lang) -> Unit, onOther: () -> Unit) {
    val phone = flow.base
    SetupPage(vm.hintVoice, speech, side = {
        VillageArt(flow.target)
        // Lani's wordmark (companion/android/tools/app_icon.py: its L is the bell tower of the launcher's icon)
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(si.lanisce.lani.R.drawable.lani_wordmark), contentDescription = "Lani",
            modifier = Modifier.height(56.dp),
        )
        Text(L10n.text(phone, "setup.hello"), style = MaterialTheme.typography.displaySmall, color = White, textAlign = TextAlign.Center)
        Text(
            SetupFlow.LANGUAGES.filter { it != phone }.joinToString(" · ") { L10n.text(it, "setup.hello") },
            style = MaterialTheme.typography.titleMedium, color = Muted, textAlign = TextAlign.Center,
        )
        Text(L10n.text(phone, "setup.what"), color = White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }) {
        Text(L10n.text(phone, "setup.learnWhich"), color = White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
        for (l in SetupFlow.LANGUAGES) LanguageTile(l, phone, chosen = l == flow.target, onClick = { onTarget(l) })
        // for those who have a code or a file: off the main path, in the phone's language
        TextButton(onClick = onOther) { Text(L10n.text(phone, "setup.otherLink"), color = White, textAlign = TextAlign.Center) }
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

/**
 * The name, and "🧒 Za otroka · For a child" there too because it changes what a child gets; "🏡 Ustanovi mojo vas" makes the
 * learner's data on this device (the view model; the defaults: SetupFlow.answers) and the founding comes. The language the
 * explanations are in, the phone's by default, is one tap away under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Name(vm: AppViewModel, flow: SetupFlow, speech: List<HintPiece>, onBase: (Lang) -> Unit, go: (SetupFlow) -> Unit) {
    val target = flow.target ?: return
    val chips = FilterChipDefaults.filterChipColors(labelColor = White, selectedLabelColor = SloBlueDeep, selectedContainerColor = White)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = White, unfocusedTextColor = White, focusedLabelColor = White, unfocusedLabelColor = White.copy(alpha = 0.75f),
        focusedBorderColor = White, unfocusedBorderColor = White.copy(alpha = 0.6f), cursorColor = White,
    )
    fun start() {
        if (!vm.setupFlow.canStart) return
        go(vm.setupFlow.starting())
        vm.startOnDevice(vm.setupFlow) { why -> go(vm.setupFlow.startFailed(why)) }
    }
    SetupPage(vm.hintVoice, speech, side = {
        VillageArt(target)
        // the welcome in the pair: the language learned big, the explanations' under it
        Text(inTarget("setup.hello"), style = MaterialTheme.typography.displaySmall, color = White, textAlign = TextAlign.Center)
        Text(inBase("setup.hello"), style = MaterialTheme.typography.titleMedium, color = Muted, textAlign = TextAlign.Center)
    }) {
        OutlinedTextField(
            flow.name, { go(vm.setupFlow.named(it)) }, Modifier.fillMaxWidth(),
            label = { Text(bi("hosted.name")) }, singleLine = true, colors = fieldColors, enabled = !flow.busy,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { start() }),
        )
        // who learns here: a child's content, a child's tutor, a parent's keys (app/ParentGate)
        Row(Modifier.fillMaxWidth()) {
            FilterChip(flow.child, { go(vm.setupFlow.forChild(!flow.child)) }, { Text("🧒 ${bi("hosted.forChild")}") }, colors = chips, enabled = !flow.busy)
        }
        if (flow.child) Text(bi("hosted.forChildWhat"), color = Muted, style = MaterialTheme.typography.bodySmall)
        flow.problem?.let { Text("⚠️ $it", color = Warn) }
        BigButton(
            if (flow.busy) bi("hosted.preparing") else "🏡 ${bi("hosted.found")}",
            enabled = flow.canStart, onClick = ::start, color = TriglavRed, disabledColor = Faint,
        )
        // the phone's language, or the other one when it's the language learned: one tap to change it
        Text(bi("setup.explainIn"), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            for (l in SetupFlow.LANGUAGES.filter { it != target }) FilterChip(flow.base == l, { onBase(l) }, { Text(l.ownName) }, colors = chips, enabled = !flow.busy)
        }
        TextButton(onClick = { go(vm.setupFlow.back()) }, enabled = !flow.busy) { Text("‹ ${bi("components.back")}", color = White) }
    }
}

/** The other ways in, for those who have a code or a file (the first screen's small link): the family's computer, an export. */
@Composable
private fun Other(vm: AppViewModel, speech: List<HintPiece>, onWay: (Way) -> Unit, onFamily: () -> Unit, onBack: () -> Unit) {
    SetupPage(vm.hintVoice, speech) {
        Text(bi("setup.otherTitle"), style = MaterialTheme.typography.headlineSmall, color = White, textAlign = TextAlign.Center)
        WayCard("👪", bi("family.scanTitle"), bi("family.scanWhat"), onClick = onFamily)
        WayCard("🖥️", bi("setup.computerTitle"), bi("setup.computerWhat"), onClick = { onWay(Way.COMPUTER) })
        WayCard("📥", bi("hosted.importExport"), bi("hosted.importWhat"), onClick = { onWay(Way.DEVICE) })
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

/**
 * "👪 Nekdo v družini že uporablja Lani · Someone in my family already uses Lani" (data/FamilyLink): the camera for the code a
 * set-up device shows, then the wait while a parent confirms there; what's shared kept for when the learner's data is made,
 * and the name's step next. A code that gives nothing says so, and the camera comes back.
 */
@Composable
private fun Family(vm: AppViewModel, speech: List<HintPiece>, go: (SetupFlow) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(si.lanisce.lani.ui.cameraGranted(context)) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(android.Manifest.permission.CAMERA) }
    val fetch = vm.familyFetch
    SetupPage(vm.hintVoice, speech) {
        Text("👪 ${bi("family.scanTitle")}", style = MaterialTheme.typography.headlineSmall, color = White, textAlign = TextAlign.Center)
        Text(bi("family.scanWhat"), color = Muted, textAlign = TextAlign.Center)
        if (fetch == AppViewModel.FamilyFetch.WAITING) {
            androidx.compose.material3.CircularProgressIndicator(color = White)
            Text(bi("family.waiting"), color = White, textAlign = TextAlign.Center)
        } else {
            if (fetch == AppViewModel.FamilyFetch.REFUSED) Text("⌛ ${bi("family.refused")}", color = Warn, textAlign = TextAlign.Center)
            if (fetch == AppViewModel.FamilyFetch.UNREACHABLE) Text("📶 ${bi("family.unreachable")}", color = Warn, textAlign = TextAlign.Center)
            if (granted) si.lanisce.lani.ui.QrCamera(
                onCode = { text -> vm.scanFamily(text) { gift -> go(vm.setupFlow.fromFamily(gift)) } },
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
            )
        }
        TextButton(onClick = { vm.familyFetch = null; go(vm.setupFlow.back()) }, enabled = fetch != AppViewModel.FamilyFetch.WAITING) { Text("‹ ${bi("components.back")}", color = White) }
    }
}

/** With the family's computer: its QR code scanned, the one big button; typing its address is behind "Ročno". */
@Composable
private fun Computer(vm: AppViewModel, speech: List<HintPiece>, onScan: () -> Unit, onManual: () -> Unit, onBack: () -> Unit) {
    SetupPage(vm.hintVoice, speech) {
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
private fun Manual(vm: AppViewModel, flow: SetupFlow, speech: List<HintPiece>, go: (SetupFlow) -> Unit) {
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
    SetupPage(vm.hintVoice, speech) {
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
 * [onLight]: on a sheet's light surface; false on the setup's blue (white text). [whileLocked]: below the hold while it's
 * closed (the setup's way on without it).
 */
@Composable
fun ParentGated(
    child: Boolean, modifier: Modifier = Modifier, onLight: Boolean = true,
    whileLocked: (@Composable () -> Unit)? = null, content: @Composable () -> Unit,
) {
    var gate by remember(child) { mutableStateOf(ParentGate(child)) }
    if (!gate.locked) return content()
    val ink = if (onLight) MaterialTheme.colorScheme.onSurface else White
    val muted = if (onLight) MaterialTheme.colorScheme.onSurfaceVariant else Muted
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("👪 ${bi("setup.parentSets")}", style = MaterialTheme.typography.titleSmall, color = ink)
        Text(bi("setup.parentSetsWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
        HoldToOpen("🔒 ${bi("setup.parentHold")}", onLight = onLight, onOpen = { gate = gate.held(ParentGate.HOLD_MS) })
        whileLocked?.invoke()
    }
}

/** A button that opens only when held ([ParentGate.HOLD_MS]): it fills as it's held, and empties again when let go early. */
@Composable
internal fun HoldToOpen(label: String, onLight: Boolean, onOpen: () -> Unit) {
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
