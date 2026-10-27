package si.lanisce.lani.ui.towns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.WordQuery
import si.lanisce.lani.data.ShareLevels
import si.lanisce.lani.data.TownProgress
import si.lanisce.lani.data.TownProgresses
import si.lanisce.lani.data.Towns
import si.lanisce.lani.data.Words
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.StatusBarScrim
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.XpGold

private val WEEKDAYS = listOf(
    "progressScreen.mon", "progressScreen.tue", "progressScreen.wed", "progressScreen.thu", "progressScreen.fri",
    "progressScreen.sat", "progressScreen.sun",
)

/**
 * "📈 Napredek · Progress" of linked town [id] (companion/README.md, "Progress between towns"): what its learner shares
 * with this one, at the level they chose: the summary on top (the streak 🔥, this week's days as dots, the words known),
 * then the words being learned (a tap: the word's card; 🔊), then "Težave · Hard for them" (each pattern and its words),
 * each with "💌 Pošlji izziv · Send a challenge" (the family's challenge, prefilled; gently worded for a child), and how
 * this learner's challenges went. At the end what this learner shares with them, and a parent's weekly note.
 */
@Composable
fun TownProgressScreen(vm: AppViewModel, id: String) {
    val c = vm.townProgress
    val row = vm.towns.list?.towns?.firstOrNull { it.id == id }
    BackHandler { vm.closeTownProgress() }
    LaunchedEffect(id) { c.load(id) }
    val p = c.progress?.takeIf { it.town.id == id }
    val learner = p?.town?.learner?.ifBlank { null } ?: row?.learner.orEmpty()
    val name = p?.town?.name?.ifBlank { null } ?: row?.name.orEmpty()
    val language = p?.town?.language?.ifBlank { null } ?: row?.language.orEmpty()
    var draft by remember { mutableStateOf<ChallengeDraft?>(null) }
    val lazy = rememberLazyListState()
    val base = L10n.pair.base.code
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), state = lazy, contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                GradientHeader(
                    "📈 ${bi("townProgress.title")}",
                    "${learner.ifBlank { "?" }} · ${Towns.flag(language)} ${name.ifBlank { "?" }}",
                    "‹ ${bi("towns.friends")}",
                    vm::closeTownProgress,
                )
            }
            when {
                p == null && c.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                p == null -> item(key = "problem") {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(c.problem ?: "📡 ${bi("towns.cantReachTutor")}", style = MaterialTheme.typography.bodyLarge)
                        BigButton("↻ ${bi("common.retry")}", onClick = { c.load(id, fresh = true) })
                    }
                }
                else -> {
                    if (p.stale) item(key = "stale") { Note("📡 ${bi("townProgress.lastKnown", "date" to p.date)}") }
                    item(key = "level") { LevelNote(p, learner) }
                    p.summary?.let { s ->
                        item(key = "summary") { SummaryCard(p, s) }
                    }
                    p.details?.let { d ->
                        if (d.learning.isNotEmpty()) {
                            item(key = "learningTitle") { Heading("🌱 ${bi("townProgress.learningNow")}", bi("townProgress.learningAbout")) }
                            items(d.learning, key = { "w:" + it.id }) { w ->
                                WordRow(vm, w.word, TownProgressLogic.meaning(w.meanings, base, p.base), TownProgressLogic.stateMark(w.state), language)
                            }
                        }
                        item(key = "hardTitle") { Heading("🧗 ${bi("townProgress.hard", "name" to learner.ifBlank { "?" })}", bi("townProgress.hardAbout")) }
                        if (d.hard.patterns.isEmpty() && d.hard.words.isEmpty()) item(key = "hardNone") { Note("🌤️ ${bi("townProgress.nothingHard")}") }
                        items(d.hard.patterns, key = { "p:" + it.id }) { pat ->
                            PatternCard(vm, pat, p, base, language, onChallenge = { draft = it })
                        }
                        if (d.hard.words.isNotEmpty()) item(key = "hardWords") {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = MaterialTheme.shapes.large, elevation = CardDefaults.cardElevation(1.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("🔁 ${bi("townProgress.missedLately")}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    for (w in d.hard.words) {
                                        WordRow(vm, w.word, TownProgressLogic.meaning(w.meanings, base, p.base), "×${w.misses}", language, inset = false) {
                                            draft = TownProgressLogic.draftFor(p, w.word, w.meanings)
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "due") {
                            Caption("📅 ${bi("townProgress.due", "today" to d.due.today, "week" to d.due.week)}", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        }
                    }
                    if (p.challenges.isNotEmpty()) {
                        item(key = "chTitle") { Heading("💌 ${bi("townProgress.yourChallenges")}", null) }
                        items(p.challenges, key = { "c:" + it.id }) { r ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${TownProgressLogic.typeName(r.type)}: „${r.text}“", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(TownProgressLogic.resultLine(r.answered, r.score, r.note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item(key = "send") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            BigButton("💌 ${bi("townProgress.sendChallenge")}", onClick = { draft = TownProgressLogic.draftBlank(p) })
                        }
                    }
                    item(key = "mine") { MineCard(vm, id, learner, row?.sharing, p) }
                }
            }
        }
        StatusBarScrim(lazy, Modifier.align(Alignment.TopCenter))
    }
    val shown = p
    draft?.let { d -> if (shown != null) ChallengeDialog(vm, id, shown, learner, d, onDismiss = { draft = null }) }
}

/** What they share with this learner, and why: "👀 Teo ti kaže: 🔎 Podrobno · Teo shows you: 🔎 Details"; a parent's note. */
@Composable
private fun LevelNote(p: TownProgress, learner: String) {
    val who = learner.ifBlank { "?" }
    val lines = buildList {
        add("👀 ${bi("townProgress.whatTheyShow", "name" to who)}: ${TownProgressLogic.levelName(p.level)}")
        // a parent who lowered it on the node sees what they chose, without the note
        if (p.guardian && p.level == ShareLevels.DETAILS) add("👪 ${bi("townProgress.asParent", "name" to who)}")
        if (p.level == ShareLevels.VILLAGE) add("🏡 ${bi("townProgress.justVillage", "name" to who)}")
    }
    Note(lines.joinToString("\n"))
}

@Composable
private fun SummaryCard(p: TownProgress, s: si.lanisce.lani.data.ProgressSummary) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = MaterialTheme.shapes.large, elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("🔥 ${s.streak}", bi("townProgress.streak"), FlameOrange, Modifier.weight(1f))
                Stat("📚 ${s.words.known}", bi("townProgress.wordsKnown"), AlpineGreen, Modifier.weight(1f))
                Stat("🆕 ${s.week.newWords}", bi("townProgress.newThisWeek"), XpGold, Modifier.weight(1f))
            }
            // this week's days as dots, Monday first, each with its letter
            val desc = inBase("townProgress.weekDesc", "n" to s.week.daysPractised, "minutes" to s.week.minutes)
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = false) { contentDescription = desc }, horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0 until 7) {
                    val on = s.week.days.getOrElse(i) { false }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(if (on) AlpineGreen else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary) }
                        Text(inTarget(WEEKDAYS[i]), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TownProgressLogic.weekLine(p)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            val level = s.course?.let { course ->
                val unit = course.current?.title?.let { t -> t[L10n.pair.target.code] ?: t[L10n.pair.base.code] ?: t["en"] ?: t.values.firstOrNull() }
                "📝 ${bi("townProgress.school", "done" to course.done, "of" to course.of)}" + unit?.let { " · $it" }.orEmpty()
            }
            Text("🎓 ${bi("townProgress.level")}: ${s.level}" + s.targetLevel?.let { " → $it" }.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            level?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!s.practisedToday && s.streak > 0) Caption("⏳ ${bi("townProgress.notToday")}")
        }
    }
}

/**
 * A word of theirs: the word, its meaning, a mark ([mark]: 🌱, 🔁, ✓ or ×2); a tap opens its card (in its [language]);
 * 🔊 says it in that language; [onChallenge]: "💌" sends a challenge with it.
 */
@Composable
private fun WordRow(vm: AppViewModel, word: String, meaning: String?, mark: String, language: String, inset: Boolean = true, onChallenge: (() -> Unit)? = null) {
    val lang = Lang.of(language)
    val own = lang == L10n.ownPair.target
    Surface(
        onClick = { vm.words.open(WordQuery(word, "", meaning, Words.SEARCH, language = if (own) null else language)) },
        color = if (inset) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (inset) 16.dp else 0.dp, vertical = 2.dp).heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mark, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(word, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                meaning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (lang != null && vm.speaker.canSay) TextButton(onClick = { if (!vm.speaker.sayIn(word, lang)) vm.notices.banner = "🔇 ${bi("townProgress.noVoice")}" }) {
                Text("🔊", modifier = Modifier.semantics { contentDescription = inBase("townProgress.listen", "word" to word) })
            }
            onChallenge?.let { send ->
                TextButton(onClick = send) { Text("💌", modifier = Modifier.semantics { contentDescription = inBase("townProgress.sendChallenge") }) }
            }
        }
    }
}

/** A pattern that is hard for them: its name, how often and which way, its words (each a row), and a challenge about it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternCard(vm: AppViewModel, pat: si.lanisce.lani.data.HardPattern, p: TownProgress, base: String, language: String, onChallenge: (ChallengeDraft) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = MaterialTheme.shapes.large, elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pat.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${TownProgressLogic.trendMark(pat.trend)} ${pat.count}×", style = MaterialTheme.typography.labelLarge, color = if (pat.trend == "worse") FlameOrange else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (it in pat.items) WordRow(vm, it.word, TownProgressLogic.meaning(it.meanings, base, p.base), "•", language, inset = false) {
                onChallenge(TownProgressLogic.draftFor(p, it.word, it.meanings, pat))
            }
            TextButton(onClick = { onChallenge(TownProgressLogic.draftForPattern(p, pat)) }, modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp)) {
                Text("💌 ${bi("townProgress.sendChallenge")}")
            }
        }
    }
}

/** What this learner shares with them (the same choice as on the friend's card), and a parent's weekly note of theirs. */
@Composable
private fun MineCard(vm: AppViewModel, id: String, learner: String, listed: si.lanisce.lani.data.Sharing?, p: TownProgress) {
    val s = vm.townProgress.sharingOf(id, listed) ?: return
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = MaterialTheme.shapes.large, elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TownShareRow(vm, id, learner, listed, progressButton = false)
            if (p.guardian) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("📬 ${bi("townProgress.weeklyNote")}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Caption(bi("townProgress.weeklyAbout", "name" to learner.ifBlank { "?" }))
                }
                Switch(checked = s.weekly, onCheckedChange = { vm.townProgress.setWeekly(id, it) }, enabled = !vm.townProgress.saving)
            }
        }
    }
}

/**
 * Sending a challenge: its kind, its text and hint (prefilled from a word or a pattern; for a child, gently worded), and
 * "💌 Pošlji · Send". It reaches their app as a family challenge from this learner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeDialog(vm: AppViewModel, id: String, p: TownProgress, learner: String, d: ChallengeDraft, onDismiss: () -> Unit) {
    var type by rememberSaveable(d.key) { mutableStateOf(d.type) }
    var text by rememberSaveable(d.key) { mutableStateOf(d.text) }
    var hint by rememberSaveable(d.key) { mutableStateOf(d.hint) }
    var message by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().imePadding()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("💌 ${bi("townProgress.challengeTo", "name" to learner.ifBlank { "?" })}", style = MaterialTheme.typography.titleLarge)
                if (p.child) Caption("🌟 ${bi("townProgress.kindly")}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in TownProgresses.CHALLENGE_TYPES) FilterChip(selected = type == t, onClick = { type = t }, label = { Text(TownProgressLogic.typeName(t)) })
                }
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(TownProgresses.MAX_TEXT); message = null },
                    label = { Text(bi("townProgress.challengeText")) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hint, onValueChange = { hint = it.take(TownProgresses.MAX_HINT) },
                    label = { Text(bi("townProgress.challengeHint")) }, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (vm.townProgress.sending) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(bi("towns.askingTown"))
                }
                BigButton("💌 ${bi("townProgress.send")}", enabled = text.isNotBlank() && !vm.townProgress.sending, onClick = {
                    message = null
                    vm.townProgress.sendChallenge(id, d.key, type, text, hint, learner) { why -> if (why == null) onDismiss() else message = why }
                })
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(bi("common.close")) }
            }
        }
    }
}

@Composable
private fun Heading(title: String, about: String?) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        about?.let { Caption(it) }
    }
}

@Composable
private fun Note(text: String) {
    Surface(color = XpGold.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Stat(value: String, label: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(color = accent.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small, modifier = modifier) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            // "dni zapored · days in a row" on two lines: never cut off, also with large system fonts
            for (part in label.split(" · ")) Text(part, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
