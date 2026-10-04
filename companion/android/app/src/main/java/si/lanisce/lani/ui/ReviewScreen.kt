package si.lanisce.lani.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.Hints
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.ui.game.RewardSummary
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.StageFinale
import si.lanisce.lani.ui.stage.StageState
import si.lanisce.lani.ui.stage.rememberStage
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.l10n.inBase

/**
 * The daily reviews, with today's companion on the stage (no intro: they greet as the first card comes up). With
 * [polish], rusty words reviewed now (due or not), and back where they were started from. Each language has its own
 * deck ([language]: another language's, practised on visits; null: the home language's); until the first answer, the
 * screen switches between the decks with cards due.
 */
@Composable
fun ReviewScreen(vm: AppViewModel, cards: List<ReviewCard>, polish: Boolean = false, language: String? = null) {
    val started = remember { ScreenClock.app.now() }
    val home = language == null
    val tasks = remember(cards) {
        // Another language's cards and distractors are its own; the voices and the recognizer are the village's
        // language's, so its cards are read and answered in writing. A familiar word of the home language now and then
        // asks one of its forms instead (companion/GAME.md, "A word's forms"); with QA's "forms" hook, every one does.
        val planned = ReviewPlanner.plan(
            cards, vm.content.deck(language)?.pool.orEmpty(), home && vm.speaker.canSay, canRecognize = home && vm.recognizer.ready,
            forms = if (home) vm.forms.questions() else null,
            formCap = if (si.lanisce.lani.app.QaHooks.forms) cards.size else maxOf(1, cards.size / si.lanisce.lani.game.WordForms.EVERY),
        )
        if (home) planned else planned.map { it.copy(exercise = silent(it.exercise)) }
    }
    var qualities by remember { mutableStateOf<List<Int>?>(null) }
    val stage = rememberStage(vm, Run.Review, greet = true)

    val done = qualities
    if (done == null) {
        val deckName = vm.content.dashboard?.languages?.firstOrNull { it.code == language }?.nameIn { inTarget(it) }
        val title = when {
            polish -> "🔩 ${bi("engine.rusty", "n" to cards.size)}"
            deckName != null -> "🔄 ${bi("reviewScreen.review")} · $deckName"
            else -> "🔄 ${bi("reviewScreen.review")}"
        }
        ExerciseRun(
            vm, title, tasks.map { it.exercise }, moduleId = null, stage = stage,
            before = if (polish) null else ({ DeckSwitch(vm, language) }),
            source = { i -> tasks.getOrNull(i)?.let { ExerciseSource.review(it.card.id, it.variant, language, it.form) } },
            // a grammar card's rule (the home language's book: its tags name the card); a form question names its own
            rules = if (home) remember(tasks, vm.grammar.pages) { tasks.map { Grammar.forCard(it.card, vm.grammar.pages)?.id } } else null,
            // a wrong form counts on its rule with the sentences: "Jaz sedi na klop." for "Jaz sedem na klop."
            slips = { i, o -> tasks.getOrNull(i)?.form?.let { f -> f.said(o.answer) to f.gap.sentence } },
        ) { outcomes ->
            val q = tasks.zip(outcomes) { t, o -> Hints.penalize(ReviewPlanner.quality(t.variant, o.verdict), o.hints) }
            qualities = q
            vm.game.rewardPractice(tasks.map { it.exercise }, outcomes.map { it.paid })
            // the forms asked: what the next asks of each word, and the tutor's note
            val forms = if (home) vm.forms.answered(tasks.take(outcomes.size), outcomes.map { it.verdict }, outcomes.map { it.answer }) else null
            vm.finishReview(tasks.map { it.card.id }.zip(q), minutesSince(started), language, forms)
        }
        return
    }
    val correct = done.count { it >= 3 }
    val perfect = done.count { it == 5 }
    // Words this review left rusty: they hold the village's next age back until polished, now or in tomorrow's review.
    // Only the home language's: the village waits for its own words, not for a visit's.
    val rusty = if (!home) emptyList() else tasks.zip(done).filter { (t, q) -> t.card.rustsAt(q) }.map { it.first.card }
    Finish(
        title = "${inTarget("reviewScreen.excellent")} 🎉",
        lines = listOfNotNull(
            inBase("reviewScreen.recalled", "correct" to correct, "total" to done.size),
            if (perfect > 0) "⚡ ${inBase("reviewScreen.onHardestLevel", "perfect" to perfect)}" else null,
            "+${correct * 10 + 25} XP",
            if (rusty.isNotEmpty()) "🔩 ${bi("reviewScreen.gotRusty", "n" to rusty.size)}" else null,
        ),
        onDone = if (polish) vm::leaveRun else vm::home,
        stage = stage,
    ) {
        RewardSummary(vm.game.lastReward, onVillage = vm::openVillage)
        if (rusty.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            BigButton("🔩 ${bi("reviewScreen.polishNow", "n" to rusty.size)}", onClick = { vm.startPolish(rusty) })
        }
    }
}

/** Another language's card without the village language's voice: nothing read aloud. */
private fun silent(e: Exercise): Exercise = when (e) {
    is Exercise.Flashcard -> e.copy(speak = false)
    is Exercise.Choice -> e.copy(say = null, sayOptions = false)
    else -> e
}

/**
 * The decks with cards due, one chip each: the home language's, then the others' ("Slovenščina 12", "Italijanščina
 * 3"). Nothing when only one deck has cards. A chip starts that deck's review.
 */
@Composable
private fun DeckSwitch(vm: AppViewModel, current: String?) {
    val home = vm.content.dashboard ?: return
    val decks = listOf<Pair<String?, Int>>(null to home.dueCards.size) +
        home.otherLanguages.map { it.code to (vm.content.decks[it.code]?.dueCards?.size ?: 0) }
    val shown = decks.filter { (code, n) -> n > 0 || code == current }
    if (shown.size < 2) return
    val homeCode = home.languages.firstOrNull { it.home }?.code ?: home.language
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((code, n) in shown) {
            val summary = home.languages.firstOrNull { it.code == (code ?: homeCode) }
            val name = summary?.nameIn { inTarget(it) } ?: (code ?: homeCode ?: "?")
            val said = inBase("reviewScreen.deckOf", "name" to (summary?.nameIn { inBase(it) } ?: name), "n" to n)
            FilterChip(
                selected = code == current,
                onClick = { if (code != current) vm.startReview(code) },
                label = { Text("$name $n") },
                modifier = Modifier.semantics { contentDescription = said },
            )
        }
    }
}

/** Shared end-of-run screen with confetti; with a [stage], the companion waves goodbye at the top. */
@Composable
fun Finish(title: String, lines: List<String>, onDone: () -> Unit, stage: StageState? = null, extra: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
        // Centred, and scrollable when a small screen or a large font doesn't fit it.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (stage?.finale != null) StageFinale(stage)
            else Text("🏆", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            for (l in lines) Text(l, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            extra()
            Spacer(Modifier.height(24.dp))
            BigButton(bi("common.continue"), onClick = onDone)
        }
        }
        Confetti(key = title + lines)
    }
}
