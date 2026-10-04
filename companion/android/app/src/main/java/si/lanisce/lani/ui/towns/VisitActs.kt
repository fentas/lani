package si.lanisce.lani.ui.towns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.app.TownVisit
import si.lanisce.lani.app.VisitHelp
import si.lanisce.lani.data.Bundle
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.data.TownActs
import si.lanisce.lani.data.TownQuestions
import si.lanisce.lani.data.TownRequest
import si.lanisce.lani.data.Visits
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.ExerciseRun
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.ui.theme.SloBlueDeep

/** The things to do on a visit (companion/README.md, "Things to do on a visit"), as the visit's bar opens them. */
enum class VisitAct { REQUESTS, GIFT, MARKET, ASK }

/**
 * Under the visited town, two by two: "📜 Prošnje · Requests", "🎁 Darilo · Gift", "⚖️ Tržnica · Market" and "❓ Vprašaj ·
 * Ask" (a question for the town's learner: TownQuestionScreens.kt).
 */
@Composable
fun VisitActsBar(onOpen: (VisitAct) -> Unit, modifier: Modifier = Modifier) {
    val acts = listOf(
        VisitAct.REQUESTS to "📜 ${bi("visit.requests")}", VisitAct.GIFT to "🎁 ${bi("visit.gift")}",
        VisitAct.MARKET to "⚖️ ${bi("visit.market")}", VisitAct.ASK to "❓ ${bi("questions.ask")}",
    )
    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in acts.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((act, label) in row) {
                FilledTonalButton(onClick = { onOpen(act) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** One of the things to do on a visit, over the lower part of the town: its title, a close button and its content. */
@Composable
fun VisitActPanel(vm: AppViewModel, v: TownVisit, act: VisitAct, onClose: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onClose)
    LaunchedEffect(act, v.id) { if (act == VisitAct.REQUESTS || act == VisitAct.MARKET) vm.visits.loadActs() }
    Surface(modifier = modifier.fillMaxWidth().widthIn(max = 600.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = when (act) {
                    VisitAct.REQUESTS -> "📜 ${bi("visit.requests")}"
                    VisitAct.GIFT -> "🎁 ${bi("visit.gift")}"
                    VisitAct.MARKET -> "⚖️ ${bi("visit.market")}"
                    VisitAct.ASK -> "❓ ${bi("questions.askWho", *TownQuestions.whoArgs(v.learner.ifBlank { v.name }))}"
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = onClose) { EmojiLabel("✕", Labels.CLOSE, style = MaterialTheme.typography.titleMedium) }
            }
            when (act) {
                VisitAct.REQUESTS -> Requests(vm)
                VisitAct.GIFT -> Gift(vm, v, onSent = onClose)
                VisitAct.MARKET -> Market(vm, v)
                VisitAct.ASK -> AskPanel(vm, v)
            }
        }
    }
}

@Composable
private fun Waiting(problem: String?) {
    if (problem != null) Text(problem, style = MaterialTheme.typography.bodyMedium)
    else Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** The town's open requests: who asks and what; "✋ Pomagaj · Help" opens its run. */
@Composable
private fun Requests(vm: AppViewModel) {
    val list = vm.visits.requests
    if (list == null) return Waiting(vm.visits.actsProblem)
    if (list.isEmpty()) {
        Text("😊 ${bi("visit.noRequests")}", style = MaterialTheme.typography.bodyMedium)
        return
    }
    for (r in list) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${r.emoji} ${r.giver}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(TownActs.label(r.title, L10n.pair), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TownActs.textIn(r.story, L10n.pair.target).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                FilledTonalButton(
                    onClick = { vm.openVisitRequest(r.id) }, enabled = r.words.size >= 2,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("✋ ${bi("visit.help")}") }
            }
        }
    }
}

/**
 * A gift for the town: a good of the learner's own chest (or none), and a short message in the town's language (at
 * most [TownActs.MAX_MESSAGE] characters, one line). Both go to the host: the good into its chest, the message to its
 * screen as plain text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Gift(vm: AppViewModel, v: TownVisit, onSent: () -> Unit) {
    val s = vm.game.state
    val goods = remember(s) { s?.let { VisitLogic.offerable(it).first }.orEmpty() }
    var good by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    Text(bi("visit.giftAbout", "town" to v.name), style = MaterialTheme.typography.bodyMedium)
    if (goods.isEmpty()) Text("🧺 ${bi("visit.chestEmpty")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((id, n) in goods) {
            val name = Cultures.home.goods[id]?.name ?: continue
            FilterChip(selected = good == id, onClick = { good = if (good == id) null else id }, label = { Text("${name.emoji} ${name.sl} ×$n") })
        }
    }
    OutlinedTextField(
        value = message,
        onValueChange = { message = it.replace('\n', ' ').take(TownActs.MAX_MESSAGE) },
        label = { Text(bi("visit.messageInLanguage")) },
        supportingText = { Text("${message.length}/${TownActs.MAX_MESSAGE}") },
        modifier = Modifier.fillMaxWidth(),
    )
    problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    BigButton(
        "🎁 ${bi("visit.send")}",
        onClick = { vm.sendVisitGift(good, message) { p -> if (p == null) onSent() else problem = p } },
        enabled = !vm.visits.sending && (good != null || message.isNotBlank()),
    )
}

/**
 * The town's market: what it can spare (pick up to three kinds), what the learner offers of their own (goods and
 * resources), and "🗣️ Barantaj · Haggle": the learner's tutor plays the seller in the town's language; the trade happens
 * when the haggle goes well (the tutor's debrief).
 */
@Composable
private fun Market(vm: AppViewModel, v: TownVisit) {
    val m = vm.visits.market
    if (m == null) return Waiting(vm.visits.actsProblem)
    val s = vm.game.state
    var want by remember(m) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var goods by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var res by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    m.seller?.let { Text("${it.emoji} ${TownActs.label(it.name, L10n.pair)}", style = MaterialTheme.typography.titleSmall) }
    Text(bi("visit.marketSells"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    if (m.wares.isEmpty()) Text("🏪 ${bi("visit.marketEmpty")}", style = MaterialTheme.typography.bodyMedium)
    for ((id, spare) in m.wares) {
        val name = VisitLogic.good(m.culture.ifBlank { v.culture }, id) ?: continue
        Stepper("${name.emoji} ${name.nameText.bi()}", want[id] ?: 0, spare) { by -> want = VisitLogic.step(want, id, by, spare) }
    }
    if (s != null) {
        val (mine, stores) = remember(s) { VisitLogic.offerable(s) }
        Text(bi("visit.youOffer"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        for ((id, n) in mine) {
            val name = Cultures.home.goods[id]?.name ?: continue
            Stepper("${name.emoji} ${name.nameText.bi()}", goods[id] ?: 0, minOf(n, VisitLogic.MAX_EACH)) { by -> goods = VisitLogic.step(goods, id, by, minOf(n, VisitLogic.MAX_EACH)) }
        }
        for ((r, n) in stores) {
            Stepper("${r.emoji} ${r.names.bi()}", res[r.name] ?: 0, minOf(n, VisitLogic.MAX_RES), step = VisitLogic.RES_STEP) { by ->
                res = VisitLogic.step(res, r.name, by, minOf(n, VisitLogic.MAX_RES))
            }
        }
    }
    val give = Bundle(Cultures.home.id, goods, res)
    val get = Bundle(m.culture.ifBlank { v.culture }, want)
    // between friends the market asks less (the friendship's rates: data/Friendship.kt)
    if (m.rates.level > 0) {
        Text("💞 ${bi("friendship.marketRates", "level" to si.lanisce.lani.game.TownFriendship.levelName(m.rates.level))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
    VisitLogic.haggleHint(s, give, get, m.wares, m.rates.fairShare)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    BigButton("🗣️ ${bi("visit.haggle")}", onClick = { vm.haggleOnVisit(give, get) }, enabled = VisitLogic.canHaggle(s, give, get, m.wares, m.rates.fairShare))
}

@Composable
private fun Stepper(label: String, n: Int, max: Int, step: Int = 1, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onStep(-step) }, enabled = n > 0, modifier = Modifier.heightIn(min = 44.dp)) { Text("−") }
        Text("$n", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
        OutlinedButton(onClick = { onStep(step) }, enabled = n < max, modifier = Modifier.heightIn(min = 44.dp)) { Text("+") }
    }
}

/**
 * A request of the visited town, done (companion/README.md, "Things to do on a visit"): who asks and why, a run of its
 * words in the town's language explained in the learner's base, and then the help sent: +🤝 for the host, the giver's
 * thank-you good for the learner. The run goes to the learner's tutor as practice in that language.
 */
@Composable
fun VisitRequestScreen(vm: AppViewModel, request: String) {
    val v = vm.visits.visit
    val r = vm.visits.requests?.firstOrNull { it.id == request }
    if (v == null || r == null) {
        LaunchedEffect(Unit) { vm.openVisit() }
        return
    }
    val exercises = remember(r) { TownActs.exercises(r, L10n.pair, Visits.seedOf(v.id)) }
    var playing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<VisitHelp?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var started by remember { mutableLongStateOf(ScreenClock.app.now()) }
    BackHandler { if (!playing) vm.openVisit() }
    when {
        playing -> ExerciseRun(
            vm, "${r.emoji} ${TownActs.label(r.title, L10n.pair)}", exercises, moduleId = null, onExit = { playing = false },
            source = { i -> ExerciseSource.visitRequest(v.id, r.id, i) },
        ) { outs ->
            playing = false
            waiting = true
            vm.finishVisitRequest(r, exercises, outs.map { it.paid }, minutesSince(started)) { result = it; waiting = false }
        }
        else -> RequestCard(r, v, exercises.size, result, waiting,
            onStart = { started = ScreenClock.app.now(); result = null; playing = true },
            onSendAgain = { waiting = true; vm.sendHelp(r) { result = it; waiting = false } },
            onBack = vm::openVisit,
        )
    }
}

@Composable
private fun RequestCard(r: TownRequest, v: TownVisit, total: Int, result: VisitHelp?, waiting: Boolean, onStart: () -> Unit, onSendAgain: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SloBlueDeep, Color(0xFF1F3F72))))) {
        Column(
            Modifier.align(Alignment.Center).statusBarsPadding().navigationBarsPadding().padding(24.dp).widthIn(max = 480.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(r.emoji, style = MaterialTheme.typography.displaySmall)
            Text(r.giver, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            Text(TownActs.label(r.title, L10n.pair), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            when (result) {
                null -> {
                    TownActs.textIn(r.story, L10n.pair.target).takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White, textAlign = TextAlign.Center) }
                    TownActs.textIn(r.story, L10n.pair.base).takeIf { it.isNotBlank() && it != TownActs.textIn(r.story, L10n.pair.target) }?.let {
                        Text(it, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    Text("${TownActs.passMark(total)}/$total ✔ · 🤝 → ${v.name}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                    if (waiting) CircularProgressIndicator(color = Color.White) else BigButton("✋ ${bi("visit.help")}", onClick = onStart, enabled = total > 0)
                }
                is VisitHelp.Helped -> {
                    Text("🎉 ${bi("visit.helpedThanks", "giver" to r.giver)}", color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    result.thanks?.let { Text("${it.emoji} +1 ${it.sl}", color = Color.White, style = MaterialTheme.typography.titleSmall) }
                    Text("🤝 +${result.amount} → ${v.name}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                }
                VisitHelp.Again -> {
                    Text("💪 ${bi("visit.almostTryAgain")}", color = Color.White, textAlign = TextAlign.Center)
                    BigButton("↻ ${bi("common.retry")}", onClick = onStart)
                }
                is VisitHelp.Failed -> {
                    Text(result.problem, color = Color.White, textAlign = TextAlign.Center)
                    if (waiting) CircularProgressIndicator(color = Color.White) else BigButton("↻ ${bi("visit.sendAgain")}", onClick = onSendAgain)
                }
            }
            Spacer(Modifier.heightIn(min = 4.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹ ${v.name.ifBlank { bi("common.village") }}", color = Color.White) }
        }
    }
}
