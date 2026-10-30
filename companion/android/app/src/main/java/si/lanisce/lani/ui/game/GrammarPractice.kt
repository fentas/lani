package si.lanisce.lani.ui.game

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.ExerciseRun
import si.lanisce.lani.ui.Finish
import si.lanisce.lani.ui.Outcome
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.ui.reviewAnswer
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.rememberStage

/**
 * "🎯 Vadi · Practise" on a grammar page: a short run of what names its rule ([Grammar.Practice.Exercises]: the modules'
 * exercises, the tent challenge's questions; the tutor gets the results as a session to keep), or of the learner's
 * grammar cards on it ([Grammar.Practice.Cards]: reviewed, due or not, like a polishing run). Every answer counts on the
 * page and feeds the village like practice; the finish leads back to the page.
 */
@Composable
fun GrammarPracticeScreen(vm: AppViewModel, pageId: String, practice: Grammar.Practice, forQuest: String? = null) {
    val page = remember(pageId) { vm.grammar.page(pageId) }
    // the drill of a request tried below the mark ("🎯 Vadi in poskusi znova"): passed, the request is back today
    val quest = remember(forQuest) { forQuest?.let { id -> vm.game.state?.quests?.firstOrNull { it.id == id } } }
    var back by remember { mutableStateOf(false) }
    val started = remember { ScreenClock.app.now() }
    val stage = rememberStage(vm, Run.Review, greet = true)
    val tasks = remember(practice) {
        (practice as? Grammar.Practice.Cards)?.let {
            ReviewPlanner.plan(it.cards, vm.content.dashboard?.pool.orEmpty(), vm.speaker.canSay, canRecognize = vm.recognizer.ready)
        }
    }
    val exercises: List<Exercise> = remember(practice) {
        when (practice) {
            is Grammar.Practice.Exercises -> practice.exercises
            is Grammar.Practice.Cards -> tasks.orEmpty().map { it.exercise }
            Grammar.Practice.Ask -> emptyList()
        }
    }
    var result by remember { mutableStateOf<List<Outcome>?>(null) }
    val title = if (page == null && quest != null) "🎯 ${quest.title.substringBefore(" · ")}" else "${page?.emoji ?: "📖"} ${page?.target ?: pageId}"
    val done = result
    if (done == null) {
        // an exercise naming another page (Kam? in a run on Kje?) counts there; the rest count here
        ExerciseRun(vm, title, exercises, moduleId = null, onExit = vm::leaveRun, stage = stage, rules = exercises.map { pageId.takeIf { page != null } }) { outs ->
            result = outs
            // the request's drill: right (or almost) as often as its pass mark asks, and it comes back today
            if (quest != null) back = vm.drillDone(quest.id, outs.count { it.verdict != Verdict.WRONG } >= si.lanisce.lani.game.Quests.passMark(outs.size))
            vm.game.rewardPractice(exercises, outs.map { it.paid })
            val minutes = minutesSince(started)
            if (tasks != null) vm.finishReview(
                tasks.zip(outs) { t, o -> t.card.id to o.quality(ReviewPlanner.quality(t.variant, o.verdict)) }, minutes,
                answers = JsonArray(tasks.zip(outs) { t, o -> reviewAnswer(t.exercise, o, t.card.id, t.variant) }), from = si.lanisce.lani.data.ReviewFrom.GRAMMAR,
            )
            else if (outs.isNotEmpty()) vm.chat.sessionEnd(summary(pageId, page?.titleIn("en"), outs), sessionData(pageId, exercises, outs, minutes))
        }
        return
    }
    val correct = done.count { it.verdict == Verdict.CORRECT }
    val almost = done.count { it.verdict == Verdict.ALMOST }
    val meter = vm.grammar.view(pageId)?.meter
    Finish(
        title = if (correct + almost >= done.size * 0.7) "${inTarget("playerScreen.bravo")} 🎉" else "${inTarget("playerScreen.goodWork")} 💪",
        lines = listOfNotNull(
            "✅ $correct   🟡 $almost   ❌ ${done.size - correct - almost}",
            meter?.let { "📈 ${bi("grammar.howWell")}: ${stars(it)}" },
            "+${correct * 10 + almost * 5 + 25} XP",
        ),
        onDone = vm::leaveRun,
        stage = stage,
    ) {
        RewardSummary(vm.game.lastReward, onVillage = vm::openVillage)
        Spacer(Modifier.height(8.dp))
        // the request's drill: back today, one tap to try it again; else it comes back tomorrow, as ever
        if (quest != null) {
            Text(
                if (back) "🔁 ${bi("quests.backToday", "title" to quest.title.substringBefore(" · "))}" else "🌙 ${bi("quests.stillTomorrow")}",
                style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (back) si.lanisce.lani.ui.BigButton("🔁 ${bi("quests.tryAgainNow")}", onClick = { vm.startQuest(quest.id) }, color = si.lanisce.lani.ui.theme.AlpineGreen)
        }
        if (page != null) TextButton(onClick = { vm.grammar.show(pageId) }) {
            Text("📖 ${bi("grammar.toBook")}", style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun summary(id: String, title: String?, outs: List<Outcome>): String {
    val c = outs.count { it.verdict == Verdict.CORRECT }
    return "Practised the grammar book's page \"${title ?: id}\" ($id) in the app: $c/${outs.size} correct."
}

private fun sessionData(id: String, exercises: List<Exercise>, outs: List<Outcome>, minutes: Int) = buildJsonObject {
    put("grammar_page", id)
    put("duration_minutes", minutes)
    put("results", buildJsonArray {
        outs.forEachIndexed { i, o ->
            add(buildJsonObject {
                put("index", i)
                put("type", exercises[i]::class.simpleName?.lowercase() ?: "?")
                exercises[i].grammar?.let { put("grammar", it) }
                put("verdict", o.verdict.name.lowercase())
                put("answer", o.answer)
                o.correct?.let { put("expected", it) }
                if (o.hints > 0) put("hints_used", o.hints)
                o.lookedUp?.let { put("looked_up", it.json) }
            })
        }
    })
}
