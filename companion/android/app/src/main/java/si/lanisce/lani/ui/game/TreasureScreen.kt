package si.lanisce.lani.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.roundToInt
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.game.HuntStatus
import si.lanisce.lani.game.LevelReadiness
import si.lanisce.lani.game.Readiness
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Station
import si.lanisce.lani.game.Treasure
import si.lanisce.lani.game.TreasureHunt
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.Confetti
import si.lanisce.lani.ui.Portrait
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold

/** The map's paper and ink: an old sheet, the same by day and night, like the village's scroll. */
private val MapPaper = Color(0xFFF4E6C4)
private val MapPaperDark = Color(0xFFE2CC98)
private val MapInk = Color(0xFF3B2A18)
private val MapMuted = Color(0xFF7A6246)
private val MapPath = Color(0xFF9A6B3A)

/**
 * "🗺️ Zemljevid zaklada · The treasure map" (companion/GAME.md, "The treasure map"): the storyteller's offer (taken, or
 * later), the signs of readiness, the path with its stations (each to try, passed, or blocked with what to practise), the
 * treasure dug up, and the new level: whether the tutor has it yet.
 */
@Composable
fun TreasureScreen(vm: AppViewModel) {
    val t = vm.treasure
    val s = vm.game.state
    val level = t.level
    val status = t.status()
    val hunt = t.hunt()
    val teller = remember(vm.villagers.all, vm.scenes.all) { t.teller() }
    val readiness = remember(s, vm.content.dashboard, vm.grammar.pages, vm.scenes.all) { t.readiness() }
    val dug = t.dug
    BackHandler(onBack = vm::leaveTreasure)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MapPaper, MapPaperDark)))) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(level, onBack = vm::leaveTreasure)
            when {
                // just dug up, or found and the node doesn't have the level yet
                dug != null || (status == HuntStatus.FOUND && t.waiting) -> Found(vm, hunt, teller)
                status == HuntStatus.ON && hunt != null -> ThePath(vm, hunt)
                status == HuntStatus.OFFERED -> Offer(vm, teller, readiness)
                else -> {
                    // a treasure found before: its keepsake; then the next level's signs
                    if (status == HuntStatus.FOUND && hunt != null) Treasure.keepsake(hunt.to)?.let { k ->
                        Note("🪙 ${bi("treasure.found")} ${k.emoji} ${k.name}")
                    }
                    Signs(vm, teller, readiness)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        if (dug != null) Confetti(key = dug)
    }
}

@Composable
private fun Header(level: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onBack, shape = CircleShape, color = Color.Transparent,
            modifier = Modifier.size(44.dp).clearAndSetSemantics { role = Role.Button; contentDescription = bi("common.close"); onClick { onBack(); true } },
        ) { Box(contentAlignment = Alignment.Center) { Text("‹", color = MapMuted, style = MaterialTheme.typography.headlineMedium) } }
        Text("🗺️", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(bi("treasure.title"), fontFamily = Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MapInk)
            Text(bi("treasure.level", "level" to level), fontFamily = Serif, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall, color = MapMuted)
        }
    }
}

/** The storyteller says [key]: their portrait, the line in the target language with 🔊, and what it means. */
@Composable
private fun TellerSays(vm: AppViewModel, teller: Villager?, key: String, vararg args: Pair<String, Any?>) {
    val said = inTarget(key, *args)
    val meant = inBase(key, *args)
    Row(verticalAlignment = Alignment.Top) {
        if (teller != null) Portrait(teller.art, modifier = Modifier.size(64.dp), px = 64)
        else Text("🧓", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.width(10.dp))
        Surface(color = Color.White.copy(alpha = 0.7f), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                teller?.let { Text(it.name, style = MaterialTheme.typography.labelMedium, color = MapMuted) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(said, style = MaterialTheme.typography.bodyLarge, color = MapInk, fontFamily = Serif, modifier = Modifier.weight(1f))
                    SpeakButton(vm.speaker, said, voiceName = teller?.speakerVoice ?: si.lanisce.lani.data.Clips.FEMALE, fallback = teller?.voice, person = teller?.id)
                }
                if (meant != said) Text(meant, style = MaterialTheme.typography.bodySmall, color = MapMuted, fontStyle = FontStyle.Italic)
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MapInk)
}

/** The map, offered: the storyteller's line, what the path is, the signs, and taking it (or later: it waits). */
@Composable
private fun Offer(vm: AppViewModel, teller: Villager?, r: Readiness?) {
    val from = r?.from ?: vm.treasure.level
    TellerSays(vm, teller, "treasure.offer")
    Note(bi("treasure.offerAbout", "n" to vm.treasure.required().size, "from" to from, "to" to (LevelReadiness.next(from) ?: from)))
    r?.let { SignsCard(vm, it) }
    BigButton("🗺️  ${bi("treasure.take")}", onClick = vm::takeMap, color = XpGold)
    TextButton(onClick = vm::leaveTreasure, modifier = Modifier.fillMaxWidth()) { Text(bi("common.later"), color = MapMuted) }
}

/** No map yet: what the signs say, and "🗺️ Sem pripravljen · I'm ready" to start the hunt anyway (nothing is lost). */
@Composable
private fun Signs(vm: AppViewModel, teller: Villager?, r: Readiness?) {
    val from = r?.from ?: vm.treasure.level
    val to = LevelReadiness.next(from)
    if (to == null) {
        Note("🏔️ ${bi("treasure.level", "level" to from)}")
        return
    }
    val name = teller?.name ?: vm.treasure.tellerName()
    TellerSays(vm, teller, "treasure.offer")
    Note(bi("treasure.offerAbout", "n" to vm.treasure.required().size, "from" to from, "to" to to))
    r?.let {
        Note(if (it.ready) "✅ ${bi("treasure.readyYes", "teller" to name)}" else "🌱 ${bi("treasure.notReady", "teller" to name)}")
        SignsCard(vm, it)
    }
    BigButton("🗺️  ${bi("treasure.imReady")}", onClick = vm::takeMap, color = XpGold)
}

/** The three signs, each with ✓ or ○, and the level's pages still to secure (a tap: the page). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignsCard(vm: AppViewModel, r: Readiness) {
    Surface(color = Color.White.copy(alpha = 0.55f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(bi("treasure.signs"), style = MaterialTheme.typography.titleSmall, color = MapInk, fontFamily = Serif, fontWeight = FontWeight.Bold)
            Sign(r.grammar, "📖 " + bi("treasure.signGrammar", "from" to r.from, "have" to r.secure.size, "need" to r.needSecure))
            Sign(
                r.remembered,
                "🔁 " + bi(
                    "treasure.signReviews", "pct" to (r.retention * 100).roundToInt(), "n" to r.reviews,
                    "need" to (LevelReadiness.RETENTION * 100).roundToInt(), "min" to LevelReadiness.MIN_REVIEWS,
                ),
            )
            Sign(r.seasoned, "📅 " + bi("treasure.signDays", "from" to r.from, "have" to r.practised, "need" to LevelReadiness.MIN_PRACTICE_DAYS))
            if (r.atLevel < LevelReadiness.MIN_DAYS_AT_LEVEL) {
                Text(bi("treasure.signSince", "days" to r.atLevel, "min" to LevelReadiness.MIN_DAYS_AT_LEVEL), style = MaterialTheme.typography.bodySmall, color = MapMuted)
            }
            val pages = r.toSecure.mapNotNull { vm.grammar.page(it) }.take(8)
            if (!r.grammar && pages.isNotEmpty()) {
                Text(bi("treasure.toSecure"), style = MaterialTheme.typography.labelMedium, color = MapMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in pages) Surface(
                        onClick = { vm.grammar.show(p.id) }, shape = RoundedCornerShape(10.dp), color = MapPaperDark,
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) { Text("${p.emoji} ${p.target}", Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = MapInk, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun Sign(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(if (ok) "✓" else "○", color = if (ok) AlpineGreen else MapMuted, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MapInk)
    }
}

/** How a station stands today. */
private enum class Mark { PASSED, OPEN, BLOCKED, SKIPPED }

private fun mark(h: TreasureHunt, st: Station, skipped: Boolean, today: LocalDate): Mark = when {
    h.passed(st) -> Mark.PASSED
    skipped -> Mark.SKIPPED
    Treasure.canTry(h, st, today) -> Mark.OPEN
    else -> Mark.BLOCKED
}

/** Where each station stands on the drawn map (share of its width and height), and the treasure's X at the end. */
private val SPOTS = mapOf(
    Station.LETTER to Offset(0.12f, 0.86f),
    Station.DIALOG to Offset(0.38f, 0.72f),
    Station.GRAMMAR to Offset(0.18f, 0.46f),
    Station.LISTENING to Offset(0.52f, 0.40f),
    Station.RIDDLE to Offset(0.84f, 0.56f),
    Station.SPEAKING to Offset(0.62f, 0.14f),
)
private val X_SPOT = Offset(0.88f, 0.16f)

/** The hunt on: the map drawn with its path, then each station with what to do there, and the treasure to dig up. */
@Composable
private fun ThePath(vm: AppViewModel, h: TreasureHunt) {
    val today = LocalDate.now()
    val required = vm.treasure.required()
    val done = required.count { h.passed(it) }
    Note(bi("treasure.progress", "done" to done, "n" to required.size))
    TheMap(h, required, today, onStation = { st -> if (st in required && Treasure.canTry(h, st, today)) vm.startStation(st) })
    Text(bi("treasure.path"), style = MaterialTheme.typography.titleMedium, color = MapInk, fontFamily = Serif, fontWeight = FontWeight.Bold)
    for (st in Station.entries) StationRow(vm, h, st, mark(h, st, st !in required, today))
    if (Treasure.complete(h, required)) {
        Note("🗺️ ${bi("treasure.allPassed")}")
        BigButton("⛏️  ${bi("treasure.dig")}", onClick = { vm.treasure.dig() }, color = XpGold)
    }
}

/** The drawn map: a winding path from station to station to the X, each station a coin of its state (a tap: try it). */
@Composable
private fun TheMap(h: TreasureHunt, required: List<Station>, today: LocalDate, onStation: (Station) -> Unit) {
    val complete = Treasure.complete(h, required)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(280.dp).background(MapPaperDark.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .border(2.dp, MapPath.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).clearAndSetSemantics { },
    ) {
        val w = constraints.maxWidth.toFloat()
        val hgt = constraints.maxHeight.toFloat()
        val points = Station.entries.map { SPOTS.getValue(it) } + X_SPOT
        Canvas(Modifier.fillMaxSize()) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = p.x * size.width
                val y = p.y * size.height
                if (i == 0) path.moveTo(x, y) else {
                    val q = points[i - 1]
                    val mx = (q.x + p.x) / 2 * size.width + (if (i % 2 == 0) 30f else -30f)
                    val my = (q.y + p.y) / 2 * size.height
                    path.quadraticTo(mx, my, x, y)
                }
            }
            drawPath(path, MapPath, style = Stroke(width = 5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))))
            // the X where the treasure lies
            val x = X_SPOT.x * size.width
            val y = X_SPOT.y * size.height
            val d = 16f
            val red = if (complete) XpGold else TriglavRed.copy(alpha = 0.8f)
            drawLine(red, Offset(x - d, y - d), Offset(x + d, y + d), strokeWidth = 7f, cap = StrokeCap.Round)
            drawLine(red, Offset(x - d, y + d), Offset(x + d, y - d), strokeWidth = 7f, cap = StrokeCap.Round)
        }
        for (st in Station.entries) {
            val p = SPOTS.getValue(st)
            val m = mark(h, st, st !in required, today)
            val fill = when (m) {
                Mark.PASSED -> AlpineGreen
                Mark.OPEN -> XpGold
                Mark.BLOCKED -> TriglavRed.copy(alpha = 0.8f)
                Mark.SKIPPED -> Color.Gray.copy(alpha = 0.5f)
            }
            val dp = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.unit.IntOffset((p.x * w - 22.dp.toPx()).roundToInt(), (p.y * hgt - 22.dp.toPx()).roundToInt()) }
            Box(
                Modifier.offset { dp }.size(44.dp).background(fill, CircleShape).border(2.dp, MapInk.copy(alpha = 0.6f), CircleShape)
                    .clickable(enabled = m == Mark.OPEN) { onStation(st) },
                contentAlignment = Alignment.Center,
            ) { Text(if (m == Mark.PASSED) "✓" else st.emoji, style = MaterialTheme.typography.titleMedium, color = if (m == Mark.PASSED) Color.White else MapInk) }
        }
        if (complete) Text("🪙", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.offset {
            androidx.compose.ui.unit.IntOffset((X_SPOT.x * w).roundToInt() - 30, (X_SPOT.y * hgt).roundToInt() + 20)
        })
    }
}

/** One station: its name, where it is, how it stands, and what to do: go, come back tomorrow (and practise), passed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StationRow(vm: AppViewModel, h: TreasureHunt, st: Station, m: Mark) {
    val r = h.record(st)
    Surface(color = Color.White.copy(alpha = if (m == Mark.SKIPPED) 0.3f else 0.6f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.label, style = MaterialTheme.typography.titleSmall, color = MapInk, fontWeight = FontWeight.Bold)
                    Text(st.whereShown, style = MaterialTheme.typography.bodySmall, color = MapMuted)
                }
                when (m) {
                    Mark.PASSED -> Text("✓ ${bi("treasure.passed")}", color = AlpineGreen, style = MaterialTheme.typography.labelLarge)
                    Mark.OPEN -> Surface(onClick = { vm.startStation(st) }, color = XpGold, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 44.dp)) {
                        Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text("▶ ${bi("treasure.go")}", color = MapInk, fontWeight = FontWeight.Bold)
                        }
                    }
                    Mark.BLOCKED -> Text("⏳ ${bi("treasure.tomorrow")}", color = MapMuted, style = MaterialTheme.typography.labelLarge)
                    Mark.SKIPPED -> Unit
                }
            }
            if (m == Mark.SKIPPED) Text(
                "🔇 " + bi(if (st == Station.SPEAKING) "treasure.skippedListen" else "treasure.skippedSpeak"),
                style = MaterialTheme.typography.bodySmall, color = MapMuted,
            )
            if (r.of > 0) Text(bi("treasure.best", "right" to r.right, "of" to r.of), style = MaterialTheme.typography.bodySmall, color = MapMuted)
            // not passed: where the path is blocked, and what to practise meanwhile (one tap)
            if (m != Mark.PASSED && r.tries > 0) Blocked(vm, st, r.weak)
        }
    }
}

/** "⛔ Pri skalah: Dajalnik · By the rocks: The dative", with the page to practise, or the practice of the station's skill. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Blocked(vm: AppViewModel, st: Station, weak: List<String>) {
    val pages = weak.mapNotNull { vm.grammar.page(it) }
    val pair = L10n.pair
    fun where(lang: si.lanisce.lani.l10n.Lang) = st.where(lang).replaceFirstChar { it.uppercase() }
    val what = if (pages.isNotEmpty()) {
        val t = pages.joinToString(", ") { it.target }
        val b = pages.joinToString(", ") { it.titleIn(pair.base.code) }
        "${where(pair.target)}: $t · ${where(pair.base)}: $b"
    } else "${where(pair.target)}: ${inTarget(STATION_KEYS.getValue(st))} · ${where(pair.base)}: ${inBase(STATION_KEYS.getValue(st))}"
    Text("⛔ $what", style = MaterialTheme.typography.bodyMedium, color = TriglavRed)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in pages) {
            Action("🎯 ${bi("grammar.practise")}: ${p.target}") { vm.practiseGrammar(p.id) }
            Action("📖 ${p.target}") { vm.grammar.show(p.id) }
        }
        if (pages.isEmpty()) when (st) {
            Station.LETTER -> Action("📖 ${bi("treasure.practiceReading")}") { vm.openReadings() }
            Station.LISTENING -> Action("🪵 ${bi("engine.intoForest")}") { vm.startGather(Res.WOOD) }
            Station.RIDDLE -> Action("📚 ${bi("homeScreen.newWords")}") { vm.openPacks() }
            Station.GRAMMAR -> Action("🪨 ${bi("engine.toQuarry")}") { vm.startGather(Res.STONE) }
            Station.DIALOG, Station.SPEAKING -> Action("💬 ${bi("common.talk")}") { vm.openTalk() }
        }
    }
}

/** The stations' names, for the blocked line's halves. */
private val STATION_KEYS = mapOf(
    Station.LETTER to "treasure.station.letter", Station.DIALOG to "treasure.station.dialog", Station.GRAMMAR to "treasure.station.grammar",
    Station.LISTENING to "treasure.station.listening", Station.RIDDLE to "treasure.station.riddle", Station.SPEAKING to "treasure.station.speaking",
)

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MapPaperDark, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 44.dp)) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
            Text(label, color = MapInk, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The treasure dug up: the chest, the new level, the storyteller's congratulation, what it brought the stores and the
 * village's keepsake, and whether the tutor has the new level yet (a tap sends it again).
 */
@Composable
private fun Found(vm: AppViewModel, h: TreasureHunt?, teller: Villager?) {
    val t = vm.treasure
    val dug = t.dug
    val to = dug?.to ?: h?.to ?: t.level
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(dug) { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🪙", style = MaterialTheme.typography.displayLarge, modifier = Modifier.scale(1.6f * pop.value))
        Spacer(Modifier.height(12.dp))
        Text(bi("treasure.found"), style = MaterialTheme.typography.headlineSmall, color = MapInk, fontFamily = Serif, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(bi("treasure.nowAt", "to" to to), style = MaterialTheme.typography.titleMedium, color = AlpineGreen, textAlign = TextAlign.Center)
    }
    TellerSays(vm, teller, "treasure.congrats")
    if (dug != null) {
        Text(bi("common.forYourVillage"), style = MaterialTheme.typography.labelLarge, color = MapMuted)
        Surface(color = Color.White.copy(alpha = 0.85f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RewardChips(dug.paid, startDelayMs = 400)
                Text("+${dug.help} 🤝", color = MapInk, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(color = XpGold.copy(alpha = 0.25f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dug.keepsake.emoji, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(dug.keepsake.name, style = MaterialTheme.typography.titleMedium, color = MapInk, fontWeight = FontWeight.Bold)
                    Text(bi("treasure.keepsakeEffect", "pct" to (Treasure.KEEPSAKE_BONUS * 100).roundToInt()), style = MaterialTheme.typography.bodySmall, color = MapMuted)
                }
            }
        }
    }
    if (t.waiting) {
        Note("⏳ ${bi("treasure.tutorWaits")}")
        // sent when it was dug up (the outbox keeps it until the node answers): again only from the next day
        if (h?.found?.let { it < LocalDate.now().toString() } == true) {
            TextButton(onClick = t::report, modifier = Modifier.heightIn(min = 48.dp)) { Text("📨 ${bi("treasure.sendAgain")}", color = MapInk) }
        }
    } else Note("🧑‍🏫 ${bi("treasure.tutorTold")}")
    Note("🌱 ${bi("treasure.newRules", "to" to to)}")
    BigButton(bi("common.continue"), onClick = vm::leaveTreasure, color = AlpineGreen)
}
