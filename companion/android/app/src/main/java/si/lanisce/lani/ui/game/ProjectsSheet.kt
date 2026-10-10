package si.lanisce.lani.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.ProjectOption
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.StepKind
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.villagers.HeartPink
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

/**
 * A project's words on its card (companion/SCENES.md "Project steps"): the words of its [pack] ([words]), those the
 * learner has learned ([learned], word ids), and where today's step is played ([where]: its scene's title, when the village
 * has it open; null: over the village).
 */
data class ProjectWords(val pack: String, val words: List<si.lanisce.lani.data.PackWord>, val learned: Set<String>, val where: String? = null)

/**
 * "🏗️ Skupni projekti · Village projects" (companion/GAME.md): every project of the ages reached, with its leader, its
 * steps (done, today's with what it costs, still to come), what it brings when finished, its words (today's step's first,
 * each to look up, and "📚 Learn the words": a pack run of those not learned yet), and the button for today's step.
 * [focus]: the project to show first (a bubble on the map was tapped). [people]: the cast and the village's own.
 * [words]: a project's words, when it has a pack; [onWord] opens a word's card (the word, its example, what it means).
 */
@Composable
fun ProjectsSheet(
    s: GameState, people: List<Villager>, focus: String?, onStep: (String) -> Unit, onDismiss: () -> Unit, onMeet: ((String) -> Unit)? = null,
    words: (si.lanisce.lani.game.ProjectSpec) -> ProjectWords? = { null },
    onWord: (String, String, String) -> Unit = { _, _, _ -> },
    onLearn: ((String) -> Unit)? = null,
) {
    val today = LocalDate.now()
    val options = remember(s) { Projects.options(s, today) }
    Sheet(onDismiss) {
        SheetHeader("🏗️", bi("common.villageProjects"), bi("projectsSheet.oneStepDayWhole"))
        if (Projects.workedToday(s, today)) {
            Text(
                "✅ ${bi("projectsSheet.villageWorkedTodayMore")}",
                style = MaterialTheme.typography.bodyMedium, color = AlpineGreen, fontWeight = FontWeight.SemiBold,
            )
        }
        val list = rememberLazyListState()
        // the open ones first (started, then not), finished ones last
        val sorted = remember(options) { options.sortedWith(compareBy<ProjectOption> { it.finished }.thenByDescending { it.done > 0 }) }
        LaunchedEffect(focus) { focus?.let { id -> sorted.indexOfFirst { it.spec.id == id }.takeIf { it > 0 }?.let { list.scrollToItem(it) } } }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (sorted.isEmpty()) item("none") {
                Text(
                    bi("projectsSheet.villageProjectsComeZaselek"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(sorted, key = { it.spec.id }) { o -> ProjectCard(s, o, people.firstOrNull { it.id == o.spec.leader }, onStep, onMeet, words(o.spec), onWord, onLearn) }
        }
    }
}

/** One project: its leader and progress, its steps, what it costs today, what it brings, its words, and today's step. */
@Composable
private fun ProjectCard(
    s: GameState, o: ProjectOption, leader: Villager?, onStep: (String) -> Unit, onMeet: ((String) -> Unit)? = null,
    words: ProjectWords? = null, onWord: (String, String, String) -> Unit = { _, _, _ -> }, onLearn: ((String) -> Unit)? = null,
) {
    val p = o.spec
    Surface(
        color = if (o.finished) AlpineGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Text(
                        "${bi("projectsSheet.ledBy")}: ${leader?.let { "${it.emoji} ${it.name}" } ?: (Catalog.tools[p.leader]?.name ?: p.leader)}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${o.done}/${o.steps}", style = MaterialTheme.typography.titleMedium, color = if (o.finished) AlpineGreen else XpGold, fontWeight = FontWeight.Bold)
            }
            FillBar(o.done / o.steps.toFloat(), if (o.finished) AlpineGreen else XpGold)
            if (o.finished) {
                Text("✅ ${p.done}", style = MaterialTheme.typography.bodySmall)
                Text("${bi("projectsSheet.forGood")}: ${Projects.bonusText(p)}", style = MaterialTheme.typography.labelMedium, color = AlpineGreen, fontWeight = FontWeight.SemiBold)
                return@Column
            }
            Text(p.about, style = MaterialTheme.typography.bodySmall)
            // the steps: done (their story), today's (what it costs), still to come
            p.steps.forEachIndexed { i, step ->
                when {
                    i < o.done -> Text("✅ ${step.line}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    i == o.done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("👉 ${step.task}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        // where its scene is played, when the village has it open (else over the village)
                        words?.where?.takeIf { step.play != null }?.let { Text("📍 $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        CostChips(o.cost) { s.res(it) }
                        val extra = listOfNotNull(
                            o.help.takeIf { it > 0 }?.let { "🤝 ${bi("projectsSheet.forNeighboursWhoHelp", "help" to it)} (${bi("projectsSheet.youHave")} ${s.help})" },
                            if (step.kind == StepKind.FEAST) (o.treat?.let { bi("projectsSheet.treatHelpers", "nameEmoji" to it.name.emoji, "nameSl" to it.name.nameText) }
                                ?: "🧺 ${bi("projectsSheet.treatFromChestHelpers")}") else null,
                        )
                        for (e in extra) Text(e, style = MaterialTheme.typography.labelMedium)
                    }
                    else -> Text("◻️ ${step.task}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(0.6f))
                }
            }
            Text("${bi("projectsSheet.whenFinished")}: ${Projects.bonusText(p)}", style = MaterialTheme.typography.labelMedium, color = AlpineGreen)
            words?.takeIf { it.words.isNotEmpty() }?.let { w -> ProjectWordChips(w, o.next?.play?.words.orEmpty(), onWord, onLearn) }
            o.reason?.let {
                Text(
                    (if (o.waiting == ProjectOption.Why.TODAY) "🌙 " else "🔒 ") + it, style = MaterialTheme.typography.labelMedium,
                    color = if (o.waiting == ProjectOption.Why.TODAY) MaterialTheme.colorScheme.onSurfaceVariant else TriglavRed,
                )
            }
            // the leader lives here but hasn't been met: the way on is their introduction
            if (o.waiting == ProjectOption.Why.LEADER && onMeet != null && Projects.leaderToMeet(s, p, LocalDate.now())) {
                BigButton("👋 ${bi("arrivals.meet")}: ${leader?.let { "${it.emoji} ${it.name}" } ?: p.leader}", onClick = { onMeet(p.leader) }, color = HeartPink)
            }
            BigButton("🔨 ${bi("projectsSheet.todaysStep")}", onClick = { onStep(p.id) }, enabled = o.available, color = FlameOrange)
        }
    }
}

/**
 * A project's words on its card: "🔤 Besede projekta · The project's words", today's step's first (marked: it asks for
 * them), each a chip to look up (✓ the learned ones), and "📚 Learn the words" while some aren't learned: a pack run of
 * them, so a project is also where new words come from.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProjectWordChips(w: ProjectWords, today: List<String>, onWord: (String, String, String) -> Unit, onLearn: ((String) -> Unit)?) {
    val ordered = remember(w, today) { w.words.sortedBy { if (it.id in today) 0 else 1 } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🔤 ${bi("projectsSheet.words")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (today.isNotEmpty()) Text("👉 ${bi("projectsSheet.stepWords")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (x in ordered) {
                val known = x.id in w.learned
                val now = x.id in today
                Surface(
                    onClick = { onWord(x.word, x.example ?: x.word, x.exampleMeaning ?: x.meaning) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    color = if (known) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (now) androidx.compose.foundation.BorderStroke(2.dp, FlameOrange) else null,
                    modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "${x.word} · ${x.meaning}" + (if (known) " ✓" else "") + (if (now) " 👉" else "") },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        x.emoji?.let { Text(it); Spacer(Modifier.width(4.dp)) }
                        Text(x.word + if (known) " ✓" else "", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
        if (onLearn != null && w.words.any { it.id !in w.learned }) {
            androidx.compose.material3.OutlinedButton(onClick = { onLearn(w.pack) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                Text("📚 ${bi("placeCard.learnWords")}")
            }
        }
    }
}
