package si.lanisce.lani.ui

import si.lanisce.lani.game.ChipLabels
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ChatContext
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.Hints
import si.lanisce.lani.data.Module
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.data.Spoken
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.AdaptedChoice
import si.lanisce.lani.game.LookUp
import si.lanisce.lani.game.QuestQueue
import si.lanisce.lani.game.SheetWatch
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.ui.game.practiseHardLabel
import si.lanisce.lani.ui.game.progressText
import androidx.compose.ui.text.style.TextAlign
import si.lanisce.lani.game.TurnMode
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.game.ReadFirst
import si.lanisce.lani.ui.game.BookLinks
import si.lanisce.lani.ui.game.IntroBookLinks
import si.lanisce.lani.ui.game.Paper
import si.lanisce.lani.ui.game.PeekButton
import si.lanisce.lani.ui.game.ReadFirstText
import si.lanisce.lani.ui.game.RewardSummary
import si.lanisce.lani.ui.game.giftWords
import si.lanisce.lani.ui.stage.Cast
import si.lanisce.lani.ui.stage.IntroSheet
import si.lanisce.lani.ui.stage.Intros
import si.lanisce.lani.ui.stage.LocalStage
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.StageBand
import si.lanisce.lani.ui.stage.StageState
import si.lanisce.lani.ui.stage.rememberStage
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.words.ExerciseTaps
import si.lanisce.lani.ui.words.KnownWords
import si.lanisce.lani.ui.words.TextParts
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.WordToken
import si.lanisce.lani.ui.words.lookUpIn

/** The outcome of one exercise, as shown in feedback and reported afterwards. */
data class Outcome(
    val verdict: Verdict,
    val answer: String,
    val correct: String?,
    val explain: String?,
    val tutor: String? = null,
    /** Character ranges in [correct] to highlight, and why the answer wasn't exact. */
    val marks: List<IntRange> = emptyList(),
    val hint: Grading.Hint = Grading.Hint.NONE,
    /** [answer] is what the speech recognizer heard, not typed text. */
    val spoken: Boolean = false,
    /** 💡 hint levels (or hinted reorder tiles) used; the verdict stays, the reward shrinks. */
    val hints: Int = 0,
    /** A worksheet exercise's items checked (a gap, a cell, a line, a word sorted): each pays and counts on its own. */
    val sheet: List<si.lanisce.lani.data.ItemMark> = emptyList(),
    /**
     * The cheat sheet opened between the exercise appearing and its answer: the cards opened then and their pages (null:
     * it stayed closed). Help, not a hint: the verdict and its pay stay, a right answer's review quality is at most 4.
     */
    val lookedUp: LookUp? = null,
) {
    /** The verdict the village pays: a correct answer with hints counts as almost (one looked up on the cheat sheet pays in full). */
    val paid: Verdict get() = Hints.paid(verdict, hints)

    /** The SM-2 quality of this answer, from its own ([base]: the verdict's): lowered by its hints, a right one looked up at most 4 ([Hints.quality]). */
    fun quality(base: Int): Int = Hints.quality(base, hints, lookedUp != null)
}

/** Grades a typed answer: forgives diacritics and stem typos, never endings (see [Grading]). */
private fun typed(answer: String, accept: List<String>, explain: String?): Outcome {
    val r = Grading.check(answer, accept)
    return Outcome(r.verdict, answer, r.expected, explain, marks = r.marks, hint = r.hint)
}

/**
 * Plays a list of exercises one at a time with immediate feedback. Shared by reviews, modules and
 * village challenges. [onComplete] is called once with every outcome, in order.
 *
 * @param timeLimitSeconds shows a countdown bar; at zero the run ends with what was answered
 * @param fast timed-event mode: a correct answer flashes and moves on by itself
 * @param endless more exercises than the clock allows (the case storm): the goodbye's tally counts what was answered,
 *   not the run's length ("3 od 3", not "3 od 30")
 * @param onExit where ✕ leads before anything was answered (default: home)
 * @param stage the training stage above the exercises: the companion reacts to each prompt and answer
 * @param before shown under the title until the first answer (the review's switch between the decks of languages)
 * @param source where the exercise at an index came from ([ExerciseSource]): sent to the tutor with a question about it
 * @param rules the grammar book's page of each exercise's rule, where the exercise doesn't name one itself (a review
 *   card's, by its tags); an answer on a rule unlocks its page, and the feedback leads there
 * @param slips what a wrong answer at an index counts on its rule as (what was said, what was right), where it isn't the
 *   answer and the right option as they are: a word's form question keeps the whole sentences, as a dialog's turn does
 * @param text the text the questions are about, read before the run and hidden now (the day's letter): "👁 Pokaži
 *   besedilo · Show the text" brings it back over the question and hides it again; [onLook] counts each time it's shown
 *   while the question is still open (companion/GAME.md, "Read first, then answer")
 * @param language the language of the exercises when it isn't the world's (another language's review at home): their
 *   words are looked up in it, and marked new against its deck
 * @param pages the grammar pages the run is about besides its exercises' (a school unit's): its cheat sheet (📋 in the top
 *   bar) has their cards right after the exercise's own
 *
 * The words of an exercise can be looked up (companion/README.md, "Exercises"): a tap in the prompt and the sentence with
 * the gap, a long press in the answers; never what the exercise tests while it's open ([ExerciseTaps]), everything once
 * answered. Free: no hint, no lower grade. Words new to the learner ([KnownWords]) are dotted.
 */
@Composable
fun ExerciseRun(
    vm: AppViewModel,
    title: String,
    exercises: List<Exercise>,
    moduleId: String?,
    timeLimitSeconds: Int? = null,
    fast: Boolean = false,
    endless: Boolean = false,
    onExit: (() -> Unit)? = null,
    stage: StageState? = null,
    before: (@Composable () -> Unit)? = null,
    source: (Int) -> JsonObject? = { null },
    rules: List<String?>? = null,
    slips: (Int, Outcome) -> Pair<String, String>? = { _, _ -> null },
    text: ReadFirst? = null,
    onLook: () -> Unit = {},
    language: String? = null,
    pages: List<String> = emptyList(),
    onComplete: (List<Outcome>) -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    var stopped by remember { mutableStateOf(false) }
    /** The text shown again over the question ([text]). */
    var peeking by remember { mutableStateOf(false) }
    val outcomes = remember { mutableStateListOf<Outcome>() }
    var remaining by remember { mutableIntStateOf(timeLimitSeconds ?: 0) }
    // against the clock (companion/GAME.md, "Events"): its sounds got ready, then 3, 2, 1, then the clock, which waits
    // while a sound loads or plays
    var clock by remember { mutableStateOf(if (timeLimitSeconds != null) Clock.READYING else Clock.RUNNING) }
    var count by remember { mutableIntStateOf(COUNT_FROM) }
    var slowSounds by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    /** The grammar page of exercise [i]'s rule: its own, else the run's. */
    fun ruleAt(i: Int): String? = exercises.getOrNull(i)?.grammar ?: rules?.getOrNull(i)
    /** Pages newly met that the feedback on screen announces. */
    var noted by remember { mutableStateOf<List<String>>(emptyList()) }
    /** A counting card's steps answered so far (the chat's attachment before the card's end). */
    var steps by remember(index) { mutableStateOf<List<si.lanisce.lani.data.ItemMark>>(emptyList()) }
    // Stopping early still reports what was answered, so progress is never lost.
    val complete = { outs: List<Outcome> ->
        stage?.finish(outs.map { it.verdict }, if (endless) outs.size else exercises.size)
        vm.grammar.announceRest() // pages met in quick answers without a feedback box
        onComplete(outs)
    }
    // nothing answered yet: back where the run began (its intro, or the screen under it)
    val stop = {
        if (outcomes.isEmpty() && (timeLimitSeconds == null || remaining > 0)) (onExit ?: vm::leaveRun)()
        else if (!stopped) {
            stopped = true
            complete(outcomes.toList())
        }
    }
    val advance: () -> Unit = {
        outcome?.let { outcomes += it }
        outcome = null
        noted.forEach(vm.grammar::announced)
        noted = emptyList()
        peeking = false
        index++
    }
    // a choice on a rule the learner has secure is typed, one they have mastered said, with their own trap among its
    // options (companion/GAME.md, "Mastery and adaptive turns"); not against the clock
    val adapted = remember(index, exercises) {
        (exercises.getOrNull(index) as? Exercise.Choice)?.takeIf { !fast && timeLimitSeconds == null }
            ?.let { vm.grammar.adapt(it, ruleAt(index), vm.canListen()) }
    }
    /**
     * The cheat sheet over the exercise on screen, from its appearing (the clock running) to its answer: what the learner
     * looks up then goes with the answer ([Outcome.lookedUp]; companion/README.md "Cheat sheet").
     */
    var watch by remember { mutableStateOf<SheetWatch?>(null) }
    /** An answer: shown, and counted on its rule (whose page it may unlock); what was looked up on the cheat sheet with it. */
    val answered: (Outcome) -> Unit = { given ->
        val o = given.copy(lookedUp = watch?.let(vm.cheat::answered))
        watch = null
        val ex = exercises[index]
        if (ex is Exercise.Counting) {
            // a counting card's steps: the counting rule, the gender, the dual, the plural (data/Counting.kt)
            for (r in si.lanisce.lani.data.Counting.ruleAnswers(ex, o.sheet)) vm.grammar.answered(r.rule, r.verdict, r.said, r.answer, r.correct)
        } else if (ex is Exercise.FormsTable) {
            // a forms card's gaps: each on its slot's page (data/FormsCard.kt)
            for (r in si.lanisce.lani.data.FormsCard.ruleAnswers(ex, o.sheet)) vm.grammar.answered(r.rule, r.verdict, r.said, r.answer, r.correct)
        } else if (o.sheet.isNotEmpty()) {
            // a worksheet's items: one answer on each rule they name, as one exercise would (companion/GAME.md, "The school")
            val items = si.lanisce.lani.data.Sheets.items(ex).map { if (it.grammar == null) it.copy(grammar = ruleAt(index)) else it }
            for (r in si.lanisce.lani.data.Sheets.ruleAnswers(items, o.sheet, si.lanisce.lani.data.Sheets.sentences(ex))) {
                vm.grammar.answered(r.rule, r.verdict, r.said, r.answer, r.correct)
            }
        } else ruleAt(index)?.let { r ->
            val slip = slips(index, o)
            vm.grammar.answered(r, o.verdict, saidOf(ex, vm.speaker::isSlovene), slip?.first ?: o.answer, slip?.second ?: o.correct)
        }
        // a timed run's right answer flashes by without a feedback box: its news waits for the run's end
        if (!(fast && o.verdict == Verdict.CORRECT)) noted = vm.grammar.fresh.toList()
        outcome = o
    }
    // an exercise on screen and not answered: the cheat sheet opened now counts on it (not before the clock runs: 3, 2, 1)
    LaunchedEffect(index, outcome == null, clock == Clock.RUNNING, stopped) {
        watch?.let(vm.cheat::unwatch)
        watch = if (outcome == null && index < exercises.size && clock == Clock.RUNNING && !stopped) vm.cheat.watch() else null
    }
    DisposableEffect(Unit) { onDispose { watch?.let(vm.cheat::unwatch) } }
    // answers given and the run not over: what would open elsewhere from a sheet over it waits (AppViewModel.runBusy)
    val busy = outcomes.isNotEmpty() && !stopped
    LaunchedEffect(busy) { vm.runBusy = busy }
    // and should the run go from the screen all the same (a notification's target, the screens built anew in another
    // language), what was answered is kept as its end would keep it, never lost; still on top (built anew), it doesn't
    // start over: back where it began. Not while the activity is only recreated (its configuration: the run goes on)
    val latestComplete by rememberUpdatedState(complete)
    val activity = androidx.activity.compose.LocalActivity.current
    val entry = remember { vm.nav.entries.last().id }
    DisposableEffect(Unit) {
        onDispose {
            vm.runBusy = false
            if (activity?.isChangingConfigurations == true) return@onDispose
            if (!stopped && outcomes.isNotEmpty()) {
                stopped = true
                latestComplete(outcomes.toList())
                if (vm.nav.entries.last().id == entry) vm.leaveRun()
            }
        }
    }
    BackHandler(onBack = stop)
    // back hides the text shown again first
    BackHandler(enabled = peeking) { peeking = false }
    LaunchedEffect(index, clock == Clock.RUNNING) {
        if (clock != Clock.RUNNING) return@LaunchedEffect
        if (index >= exercises.size && !stopped) { stopped = true; complete(outcomes.toList()) }
        else if (index < exercises.size) stage?.prompt(index, exercises[index], fast)
    }
    if (timeLimitSeconds != null) LaunchedEffect(Unit) {
        // every sound it asks about on the phone first (begun on the intro already): none waits for the node on the clock
        val sounds = exercises.mapNotNull { it.heard }
        if (sounds.isNotEmpty()) coroutineScope {
            val slow = launch { delay(SLOW_SOUNDS_MS); slowSounds = true }
            withTimeoutOrNull(PRELOAD_MS) { vm.speaker.preload(sounds) { t -> heardVoice(stage, vm.speaker, t) } }
            slow.cancel()
        }
        clock = Clock.COUNTING
        while (count > 0) { delay(COUNT_MS); count-- }
        clock = Clock.RUNNING
        var ms = 0L
        while (remaining > 0) {
            delay(TICK_MS)
            waiting = vm.speaker.busy
            if (!waiting) {
                ms += TICK_MS
                if (ms >= 1_000) { ms -= 1_000; remaining-- }
            }
        }
        outcome?.let { outcomes += it; outcome = null } // an answer on screen still counts
        stop()
    }
    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        exercises.getOrNull(index)?.let { stage?.answered(o.verdict, o.hints, it, fast) }
        if (fast && o.verdict == Verdict.CORRECT) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(650)
            advance()
        }
    }
    val known = rememberKnownWords(vm, language)
    val look = rememberLook(vm, exercises.getOrNull(index), index, answered = outcome != null, known, language)
    if (index >= exercises.size || stopped) return

    CompositionLocalProvider(LocalSpeaker provides vm.speaker, LocalVocabulary provides rememberVocabulary(vm), LocalStage provides stage, LocalLook provides look) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = stop) { if (outcomes.isEmpty()) EmojiLabel("✕", Labels.CLOSE) else Text(bi("playerScreen.done")) }
            LinearProgressIndicator(
                progress = { index.toFloat() / exercises.size },
                modifier = Modifier.weight(1f).height(10.dp).clip(CircleShape),
                color = XpGold,
            )
            // the cheat sheet, this exercise's cards first (then the pages the run is about: a school unit's): help, not a
            // hint (a right answer after it pays in full, its review quality at most 4: [watch])
            CheatButton(vm) {
                val own = when (val e = exercises.getOrNull(index)) {
                    is Exercise.Counting -> e.pages
                    is Exercise.FormsTable -> e.pages
                    else -> emptyList()
                }
                (listOfNotNull(ruleAt(index)) + own + pages + cheatContext(vm, ruleAt(index), exercises, rules, moduleId)).distinct()
            }
            // the exercise on screen goes with it (its answer too, once answered); none yet while a timed run counts down
            ChatButton(vm) {
                exercises.getOrNull(index)?.takeIf { clock == Clock.RUNNING && !stopped }?.let { chatContext(it, outcome, moduleId, source(index), steps) }
            }
        }
        if (timeLimitSeconds != null) Countdown(remaining, timeLimitSeconds, waiting = waiting && clock == Clock.RUNNING)
        stage?.let { StageBand(it, Modifier.padding(top = 8.dp)) }
        if (clock != Clock.RUNNING) {
            // before the clock: the sounds getting ready (said when it takes a moment), then 3, 2, 1
            GetReady(if (clock == Clock.COUNTING) count else null, slowSounds, Modifier.weight(1f))
            return@Column
        }
        // Teo's tablet on its side (2560 × 1600: 1280 × 800 dp), a phone on its side: the feedback card beside the question,
        // which stays whole to read; upright, under it as before ([FeedbackPlace]). The question keeps its place in the
        // tree either way (what was typed survives a turn of the tablet)
        val config = LocalConfiguration.current
        val beside = FeedbackPlace.beside(config.screenWidthDp, config.screenHeightDp)
        val feedback: @Composable (Boolean) -> Unit = { side ->
            outcome?.let { o ->
                if (fast && o.verdict == Verdict.CORRECT) CorrectFlash()
                else Feedback(
                    vm, o, slovene = sloveneOf(exercises[index], vm.speaker::isSlovene),
                    onAsk = { vm.chat.askAbout(chatContext(exercises[index], o, moduleId, source(index))) }, next = advance,
                    // the "why" belongs to a rule: "📖 V knjigo", and "Nova stran v knjigi" for a page it just unlocked
                    book = { BookLinks(vm, listOfNotNull(ruleAt(index)) + noted, fresh = noted) },
                    side = side,
                )
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                if (before != null && outcomes.isEmpty() && outcome == null) before()
                if (text != null) PeekButton(peeking, Modifier.padding(top = 8.dp)) {
                    // a look counts while the question is open; after its answer, the text is there to check, free
                    if (!peeking && outcome == null) onLook()
                    peeking = !peeking
                }
                Box(Modifier.weight(1f)) {
                    // the question stays under the text (what was typed is kept), out of TalkBack's way while it's covered
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)
                            .then(if (peeking) Modifier.clearAndSetSemantics { } else Modifier),
                    ) {
                        key(index) {
                            ExerciseView(vm, moduleId, index, exercises[index], locked = outcome != null, done = answered, adapted = adapted, onStep = { steps = it })
                        }
                    }
                    if (peeking && text != null) Surface(
                        color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 6.dp, shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(4.dp)) {
                            Paper { ReadFirstText(vm, text) }
                        }
                    }
                }
            }
            if (beside) AnimatedVisibility(outcome != null, enter = slideInHorizontally { it } + fadeIn(), modifier = Modifier.fillMaxHeight()) {
                Box(
                    Modifier.width(FeedbackPlace.width(config.screenWidthDp).dp).fillMaxHeight().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) { feedback(true) }
            }
        }
        if (!beside) AnimatedVisibility(outcome != null, enter = slideInVertically { it }) { feedback(false) }
    }
    }
}

/**
 * Where an answer's feedback card goes (ui/PlayerScreen): beside the question on a screen wide and on its side (Teo's
 * tablet in landscape, 1280 × 800 dp; a phone turned), so the question stays whole to read; under it on one upright.
 */
internal object FeedbackPlace {
    /** At least [MIN_WIDE_DP] wide and wider than tall. */
    fun beside(widthDp: Int, heightDp: Int): Boolean = widthDp >= MIN_WIDE_DP && widthDp > heightDp

    /** The card's width beside the question: 40 % of the screen, at least [MIN_DP] (its buttons side by side), at most [MAX_DP]. */
    fun width(widthDp: Int): Int = (widthDp * 0.4f).toInt().coerceIn(MIN_DP, MAX_DP)

    const val MIN_WIDE_DP = 720
    const val MIN_DP = 340
    const val MAX_DP = 520
}

/** Where a timed run's clock is: its sounds getting ready, counting 3, 2, 1, or running. */
private enum class Clock { READYING, COUNTING, RUNNING }

/** 3, 2, 1: from this. */
private const val COUNT_FROM = 3
private const val COUNT_MS = 800L
/** How often the clock looks whether a sound is loading or playing. */
private const val TICK_MS = 100L
/** "Pripravljam zvoke …" once getting them ready takes this long. */
private const val SLOW_SOUNDS_MS = 400L
/** The longest a run waits for its sounds before it starts anyway (the clock then waits for each as it loads). */
private const val PRELOAD_MS = 20_000L

/**
 * Before a timed run's clock starts: "Pripravljam zvoke … · Getting the sounds ready" while they are on their way ([slow]),
 * then [count] 3, 2, 1, big (TalkBack hears each).
 */
@Composable
private fun GetReady(count: Int?, slow: Boolean, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (count != null && count > 0) {
            Text(bi("playerScreen.getReady"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$count", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = FlameOrange,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        } else if (slow) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("🔊 ${bi("playerScreen.soundsReady")}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

/** Time left in a timed run; turns red in the last quarter; ⏸ while it waits for a sound ([waiting]). */
@Composable
private fun Countdown(remaining: Int, total: Int, waiting: Boolean = false) {
    val fraction by animateFloatAsState(remaining.toFloat() / total.coerceAtLeast(1), tween(900, easing = LinearEasing), label = "timer")
    val urgent = remaining * 4 <= total
    val pulse = if (urgent) {
        rememberInfiniteTransition(label = "urgent").animateFloat(1f, 1.15f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "pulse").value
    } else 1f
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (waiting) EmojiLabel("⏸", bi("playerScreen.clockWaits"))
        else Text("⏱", Modifier.scale(pulse))
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
            color = if (urgent) TriglavRed else FlameOrange,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "%d:%02d".format(remaining / 60, remaining % 60),
            style = MaterialTheme.typography.labelLarge,
            color = if (urgent) TriglavRed else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Fast mode: a short green burst instead of the feedback card. */
@Composable
private fun CorrectFlash() {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Surface(
        color = AlpineGreen,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().height(72.dp).scale(scale.value),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("${inTarget("playerScreen.correct")} ✅", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
    }
}

/**
 * 💬 in the exercise top bar, with a dot for unread tutor messages. Keeps the bottom free for answers. The chat opens
 * with [about] attached, what is on screen now (the exercise, with its answer once answered; the word met; the text to
 * read), shown as the chip above the input that ✕ takes away: nothing is sent until the learner writes. Null: plain.
 */
@Composable
fun ChatButton(vm: AppViewModel, about: () -> ChatContext? = { null }) {
    Box {
        val unread = if (vm.chat.unread > 0) " · ${bi("playerScreen.unreadNew", "n" to vm.chat.unread)}" else ""
        IconButton(onClick = { vm.chat.openFrom(runCatching(about).getOrNull()) }) { EmojiLabel("💬", Labels.ASK + unread) }
        if (vm.chat.unread > 0) {
            Box(Modifier.align(Alignment.TopEnd).offset((-6).dp, 6.dp).size(10.dp).clip(CircleShape).background(TriglavRed))
        }
    }
}

/** A module: its intro (the quest giver asks, or today's companion), the run on the stage, the finish. */
@Composable
fun PlayerScreen(vm: AppViewModel, module: Module) {
    var started by remember { mutableLongStateOf(ScreenClock.app.now()) }
    var playing by remember { mutableStateOf(vm.again) }
    var result by remember { mutableStateOf<List<Outcome>?>(null) }
    // Its village quest (a tutor quest), else its quest block: fixed when the screen opens.
    val quest = remember(module.id) { vm.game.state?.quests?.firstOrNull { it.source == QuestSource.TUTOR && it.moduleId == module.id } }
    val meta = remember(module.id) { vm.content.modules.firstOrNull { it.id == module.id }?.quest }
    val stage = rememberStage(vm, remember(module.id) { Run.Module(quest?.giver ?: meta?.giver, quest?.emoji ?: meta?.emoji) })
    val done = result
    // the pages of its rules: what its exercises name, and the pages that name it among their modules
    val rules = remember(module.id, vm.grammar.pages) {
        module.exercises.mapNotNull { it.grammar } + Grammar.ofModule(module.id, vm.grammar.pages).map { it.id }
    }
    LaunchedEffect(playing) { if (playing) vm.grammar.meetModule(module.id) }
    if (done == null && !playing) {
        val info = remember(module.id) {
            // a request waiting in "Later" (behind its drill, or its turn) says why; it plays all the same
            val why = vm.game.state?.let { s -> quest?.let { Residents.queue(s, vm.villagers.people(s), LocalDate.now()).why(it.id) } }
            Intros.module(module, quest, meta, stage.who, Cast.trainingLeft(vm.game.state, stage.who.id, LocalDate.now()), why)
        }
        IntroSheet(
            info, vm.speaker, lookUp = { l, e -> lookUpIn(vm, l, e, Words.VILLAGER) }, onStart = { started = ScreenClock.app.now(); playing = true },
            onLater = vm::leaveRun,
            extra = {
                IntroBookLinks(vm, rules)
                // tried too often below the mark, and no drill for it: the rules it missed, one tap away
                quest?.takeIf { q -> vm.game.state?.let { QuestQueue.hard(q, it.quests) } == true }?.let { q ->
                    OutlinedButton(onClick = { vm.practiseHard(q.id) }, modifier = Modifier.fillMaxWidth()) { Text(practiseHardLabel()) }
                }
            },
        )
        return
    }
    if (done == null) {
        ExerciseRun(
            vm, module.title, module.exercises, module.id, onExit = { playing = false }, stage = stage,
            source = { i -> ExerciseSource.module(module.id, module.version, i) },
        ) { outs ->
            result = outs
            vm.game.rewardModule(module, outs.map { it.paid })
            // a request of the tutor's: passed or not, and what was missed (the tutor puts it into the reviews)
            val request = quest?.let { q ->
                val won = vm.game.lastReward?.result?.won == true
                si.lanisce.lani.ui.game.questEnd(q, vm.game.state?.quests?.firstOrNull { it.id == q.id }, won, module.exercises.size, outs.map { it.verdict })
            }
            vm.chat.sessionEnd(summary(module, outs), sessionData(module, outs, started, request))
        }
        return
    }
    val correct = done.count { it.verdict == Verdict.CORRECT }
    val almost = done.count { it.verdict == Verdict.ALMOST }
    Finish(
        title = if (correct + almost >= done.size * 0.7) "${inTarget("playerScreen.bravo")} 🎉" else "${inTarget("playerScreen.goodWork")} 💪",
        lines = listOf("✅ $correct   🟡 $almost   ❌ ${done.size - correct - almost}", "+${correct * 10 + almost * 5 + 25} XP"),
        onDone = vm::leaveRun,
        stage = stage,
    ) {
        // a tutor's request passed: their thank-you good is a present to open
        RewardSummary(vm.game.lastReward, onVillage = vm::openVillage, words = giftWords(vm))
        // not passed: how close Jan is now, and after a few tries the rules it missed, one tap away
        val after = quest?.let { q -> vm.game.state?.quests?.firstOrNull { it.id == q.id } }?.takeIf { !it.done }
        after?.let(::progressText)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
        if (after != null && vm.game.state?.let { QuestQueue.hard(after, it.quests) } == true) {
            OutlinedButton(onClick = { vm.practiseHard(after.id) }) { Text(practiseHardLabel()) }
        }
        Spacer(Modifier.height(12.dp))
        Text(bi("playerScreen.tutorLookingAtResults"), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExerciseView(
    vm: AppViewModel, moduleId: String?, index: Int, ex: Exercise, locked: Boolean, done: (Outcome) -> Unit, adapted: AdaptedChoice? = null,
    onStep: (List<si.lanisce.lani.data.ItemMark>) -> Unit = {},
) {
    when (ex) {
        is Exercise.Flashcard -> FlashcardEx(vm, ex, locked, done)
        is Exercise.Choice -> AdaptiveChoiceEx(vm, adapted?.takeIf { it.exercise.prompt == ex.prompt } ?: AdaptedChoice(ex, TurnMode.CHOOSE, null), locked, done)
        is Exercise.Cloze -> {
            // a gap by ear: the whole sentence heard, the missing word typed
            if (ex.audio != null) {
                Prompt("", ex.instruction ?: bi("content.hearGap"))
                AudioPrompt(ex.audio)
                TypedEx(ex.text.replace("___", "______"), ex.hint, locked, done, accept = ex.accept) { a -> typed(a, ex.accept, ex.explain) }
            } else TypedEx(ex.text.replace("___", "______"), ex.hint, locked, done, ex.instruction, accept = ex.accept) { a -> typed(a, ex.accept, ex.explain) }
        }
        is Exercise.Dictation -> {
            Prompt("", ex.instruction ?: bi("common.writeWhatHear"))
            AudioPrompt(ex.audio)
            TypedEx("", null, locked, done, accept = ex.accept) { a -> typed(a, ex.accept, ex.explain) }
        }
        is Exercise.Reorder -> ReorderEx(ex, locked, done)
        is Exercise.Translate ->
            if (ex.grade == "claude") TutorGradedEx(vm, moduleId, index, ex.prompt, "Accepted: ${ex.accept.joinToString(" | ")}", ex.accept.first(), locked, done)
            else TypedEx(ex.prompt, null, locked, done, ex.instruction, ex.say, ex.accept) { a -> typed(a, ex.accept, ex.explain) }
        is Exercise.Free -> TutorGradedEx(vm, moduleId, index, ex.prompt, ex.rubric, null, locked, done)
        is Exercise.Multi -> MultiEx(ex, locked, done)
        is Exercise.Scenario -> ScenarioEx(vm, ex, locked, done)
        is Exercise.Speak -> SpeakEx(vm, ex, locked, done)
        is Exercise.Unsupported -> UnsupportedEx(ex, done)
        // the school's worksheet types (ui/school/SheetExercises.kt)
        is Exercise.GapText -> si.lanisce.lani.ui.school.GapTextEx(vm, ex, locked, done)
        is Exercise.Table -> si.lanisce.lani.ui.school.TableEx(vm, ex, locked, done)
        is Exercise.Transform -> si.lanisce.lani.ui.school.TransformEx(vm, ex, locked, done)
        is Exercise.Sort -> si.lanisce.lani.ui.school.SortEx(vm, ex, locked, done)
        // a counting card of the review (ui/CountingCard.kt)
        is Exercise.Counting -> CountingEx(vm, ex, locked, done, onStep)
        // a forms card of the review (ui/FormsCardView.kt)
        is Exercise.FormsTable -> FormsCardEx(vm, ex, locked, done, onStep)
        // a task of the numbers module (ui/numbers/NumeralEx.kt)
        is Exercise.Numeral -> si.lanisce.lani.ui.numbers.NumeralEx(ex, locked, done)
        // a task of the case trainer (ui/cases/CaseEx.kt)
        is Exercise.CaseTask -> si.lanisce.lani.ui.cases.CaseTaskEx(vm, ex, locked, done)
    }
}

/**
 * What to do (small, muted) above what it's about (large). [say] adds a read-aloud button
 * for Slovene main text. Its words in the language learned can be looked up with a tap (not what the exercise tests while
 * it's open: [ExerciseLook]), and the first time the tip about it shows under it ([WordTip]).
 */
@Composable
internal fun Prompt(main: String, instruction: String? = null, say: String? = null) {
    val look = LocalLook.current
    if (instruction != null) {
        Text(instruction, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
    if (main.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shown = remember(main) { inlineMarkdown(main) }
            ExText(
                shown,
                remember(shown, look) { look?.taps?.prompt(shown.text, shown.emphasized()).orEmpty() },
                style = if (instruction != null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                tag = "prompt",
            )
            val speaker = LocalSpeaker.current
            // Not a sentence with a gap ("Midva ______ iz Trnovega."): the feedback plays it whole.
            val spoken = say ?: main.takeIf { "__" !in it && speaker?.isSlovene(it) == true }
            if (spoken != null && speaker != null) SpeakButton(speaker, spoken, Modifier.padding(start = 8.dp))
        }
    }
    look?.let { WordTip(it) }
    Spacer(Modifier.height(20.dp))
}

/** The voice an exercise's sound is heard in: on the stage the companion's (a line in the language learned), else the narrator's. */
internal fun heardVoice(stage: StageState?, speaker: si.lanisce.lani.data.Speaker, text: String): String =
    stage?.voice?.takeIf { speaker.isSlovene(text) } ?: Clips.FEMALE

/** Audio-only prompt: the Slovene is heard, never shown (unless no voice is installed). */
@Composable
internal fun AudioPrompt(text: String) {
    val speaker = LocalSpeaker.current
    if (speaker?.canSay == true) {
        // On the stage, the companion reads it (their voice's clip, else text-to-speech).
        val voice = heardVoice(LocalStage.current, speaker, text)
        LaunchedEffect(text) { speaker.say(text, voiceName = voice) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // a long press on either records the line again (a word heard wrong)
            SayButton(speaker, text, Modifier.weight(1f).heightIn(min = 88.dp), voiceName = voice, shape = MaterialTheme.shapes.large) {
                Text("🔊  ${bi("playerScreen.play")}", style = MaterialTheme.typography.titleLarge)
            }
            SayButton(speaker, text, Modifier.heightIn(min = 88.dp), slow = true, voiceName = voice, tonal = false, shape = MaterialTheme.shapes.large) {
                EmojiLabel("🐢", Labels.SLOW, style = MaterialTheme.typography.titleLarge)
            }
        }
    } else {
        var shown by remember { mutableStateOf(false) }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("🔇 ${bi("playerScreen.noSloveneVoiceInstalled")}", style = MaterialTheme.typography.titleSmall)
                if (shown) Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
                Row {
                    TextButton(onClick = { shown = true }) { Text(bi("playerScreen.showText")) }
                    speaker?.let { s -> TextButton(onClick = s::openVoiceSettings) { Text(bi("playerScreen.install")) } }
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun FlashcardEx(vm: AppViewModel, ex: Exercise.Flashcard, locked: Boolean, done: (Outcome) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val voice = LocalStage.current?.voice ?: Clips.FEMALE
    LaunchedEffect(Unit) { if (ex.speak) vm.speaker.say(ex.front, voiceName = voice) }
    val look = LocalLook.current
    ElevatedCard(onClick = { shown = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // its word once turned over (before, it's what the card asks)
            ExText(AnnotatedString(ex.front), look?.taps?.front(ex.front, shown).orEmpty(), style = MaterialTheme.typography.displaySmall, tag = "front")
            if (ex.speak) SpeakButton(vm.speaker, ex.front)
            if (shown) {
                Spacer(Modifier.height(12.dp))
                ExText(AnnotatedString(ex.back), look?.taps?.back(ex.back).orEmpty(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, tag = "back")
                ex.note?.let { ExText(AnnotatedString(it), look?.taps?.back(it).orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp), tag = "note") }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    if (!shown) BigButton(bi("common.show"), onClick = { shown = true })
    else if (!locked) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { done(Outcome(Verdict.WRONG, "didn't know", ex.back, ex.note)) }, Modifier.weight(1f).heightIn(min = 56.dp)) { Text("✗ ${inTarget("playerScreen.didntKnow")}") }
        OutlinedButton(onClick = { done(Outcome(Verdict.CORRECT, "knew it", ex.back, ex.note)) }, Modifier.weight(1f).heightIn(min = 56.dp)) { Text("✓ ${inTarget("playerScreen.knewIt")}") }
    }
}

/**
 * A choice exercise as the learner meets it ([AdaptedChoice]): its options (their own trap among them), or, on a rule
 * they have secure, its sentence's gap typed like a cloze, on one they have mastered, the whole sentence said like a speak
 * exercise; "✋ Izberi raje · Let me choose" brings back the options (never a wall).
 */
@Composable
private fun AdaptiveChoiceEx(vm: AppViewModel, a: AdaptedChoice, locked: Boolean, done: (Outcome) -> Unit) {
    val ex = a.exercise
    val g = a.gap
    var chosen by remember { mutableStateOf(false) }
    when {
        chosen || g == null || a.mode == TurnMode.CHOOSE ->
            if (ex.audio != null) {
                Prompt("", ex.instruction)
                AudioPrompt(ex.audio)
                ChoiceEx(ex.prompt, ex.options, ex.answer, ex.explain, locked, done, sayOptions = ex.sayOptions, almost = ex.almost)
            } else ChoiceEx(ex.prompt, ex.options, ex.answer, ex.explain, locked, done, ex.instruction, ex.say, ex.sayOptions, ex.almost)
        a.mode == TurnMode.TYPE -> {
            TypedEx(g.shown, null, locked, done, "✍️ ${bi("adaptive.typeMissingWord")}", accept = g.accept) { t -> typed(t, g.accept, ex.explain) }
            if (!locked) LetMeChooseButton { chosen = true }
        }
        else -> {
            val speak = remember(g) {
                Exercise.Speak(say = g.sentence, prompt = g.shown, show = false, instruction = "🎤 ${bi("adaptive.sayWholeSentence")}", explain = ex.explain, grammar = ex.grammar)
            }
            SpeakEx(vm, speak, locked, done)
            if (!locked) LetMeChooseButton { chosen = true }
        }
    }
}

/** "✋ Izberi raje · Let me choose": the exercise's options after all. */
@Composable
private fun LetMeChooseButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).padding(top = 8.dp)) { Text("✋ ${bi("adaptive.letMeChoose")}") }
}

/**
 * Pick one of [options]: [answer] is right, one of the near misses [almost] almost (a real answer that doesn't fit here,
 * [Grading.picked]: paid as almost, with the [explain]), the rest wrong.
 */
@Composable
private fun ChoiceEx(
    prompt: String, options: List<String>, answer: Int, explain: String?, locked: Boolean, done: (Outcome) -> Unit,
    instruction: String? = null, say: String? = null, sayOptions: Boolean = false, almost: List<Int> = emptyList(),
) {
    var picked by remember { mutableStateOf<Int?>(null) }
    val speakable = speakableOptions(options, sayOptions)
    val look = LocalLook.current
    Prompt(prompt, instruction, say)
    options.forEachIndexed { i, opt ->
        val color = when {
            picked == null -> CardDefaults.cardColors()
            i == answer -> CardDefaults.cardColors(containerColor = AlpineGreen.copy(alpha = 0.18f))
            i == picked && i in almost -> CardDefaults.cardColors(containerColor = FlameOrange.copy(alpha = 0.22f))
            i == picked -> CardDefaults.cardColors(containerColor = TriglavRed.copy(alpha = 0.18f))
            else -> CardDefaults.cardColors()
        }
        OptionRow(opt, speakable, i, Modifier.padding(vertical = 5.dp)) {
            Card(
                onClick = {
                    if (picked == null && !locked) {
                        picked = i
                        done(Outcome(Grading.picked(i, answer, almost), opt, options[answer], explain))
                    }
                },
                colors = color,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                // a long press on a word looks it up (a tap picks the option), where the option isn't what's tested
                OptionText(look, i, opt, Modifier.fillMaxWidth().padding(18.dp))
            }
        }
    }
}

/**
 * Which options get a 🔊: those in the target language (or all, with [sayOptions]); null when none do. An exercise's
 * options are in one language: when some read as the target and none as the base, all get one ("Pojdite naravnost,
 * potem levo." has no č/š/ž and none of the frequent words, beside options that have).
 */
@Composable
private fun speakableOptions(options: List<String>, sayOptions: Boolean): List<Boolean>? {
    val speaker = LocalSpeaker.current?.takeIf { it.canSay } ?: return null
    return remember(options, sayOptions) {
        val each = options.map { sayOptions || speaker.isSlovene(unpictured(it)) }
        val pair = L10n.pair
        if (true in each && options.none { Spoken.looks(unpictured(it), pair.base, pair.target) }) each.map { true } else each
    }.takeIf { true in it }
}

/**
 * [text] without the picture in front of it, what its 🔊 says: "🪷 ob ribniku" → "ob ribniku" (an option shows its place
 * with an emoji, the voice says only the words).
 */
internal fun unpictured(text: String): String {
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val type = Character.getType(cp)
        val picture = type == Character.OTHER_SYMBOL.toInt() || type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.FORMAT.toInt() || Character.isWhitespace(cp)
        if (!picture) break
        i += Character.charCount(cp)
    }
    return if (i == 0 || i == text.length) text else text.substring(i)
}

/**
 * An answer card with its 🔊 beside it, not inside: hearing an option never picks it. When any
 * option can be heard, every row keeps the slot so the cards line up.
 */
@Composable
private fun OptionRow(text: String, speakable: List<Boolean>?, index: Int, modifier: Modifier = Modifier, card: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { card() }
        if (speakable != null) {
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                val speaker = LocalSpeaker.current
                if (speakable[index] && speaker != null) SpeakButton(speaker, unpictured(text))
            }
        }
    }
}

/** Option [i] ([text]) of the exercise on screen: its words by a long press before the answer (a tap picks it), a tap after. */
@Composable
private fun OptionText(look: ExerciseLook?, i: Int, text: String, modifier: Modifier) {
    ExText(
        AnnotatedString(text), remember(look, i, text) { look?.taps?.option(i, text).orEmpty() }, modifier,
        style = MaterialTheme.typography.titleMedium, hold = look?.taps?.answered == false, line = { unpictured(text) }, tag = "option",
    )
}

@Composable
private fun MultiEx(ex: Exercise.Multi, locked: Boolean, done: (Outcome) -> Unit) {
    val selected = remember { mutableStateListOf<Int>() }
    var checked by remember { mutableStateOf(false) }
    val speakable = speakableOptions(ex.options, ex.sayOptions)
    val look = LocalLook.current
    Prompt(ex.prompt, ex.instruction ?: bi("playerScreen.selectAllApply"))
    ex.options.forEachIndexed { i, opt ->
        val right = i in ex.answers
        val color = when {
            !checked -> if (i in selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
            right -> CardDefaults.cardColors(containerColor = AlpineGreen.copy(alpha = 0.18f))
            i in selected -> CardDefaults.cardColors(containerColor = TriglavRed.copy(alpha = 0.18f))
            else -> CardDefaults.cardColors()
        }
        OptionRow(opt, speakable, i, Modifier.padding(vertical = 4.dp)) {
            Card(
                onClick = { if (!checked && !locked) { if (i in selected) selected.remove(i) else selected += i } },
                colors = color,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = i in selected, onCheckedChange = null)
                    OptionText(look, i, opt, Modifier.weight(1f).padding(start = 4.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    // how many are ticked, on the button itself: a multi-select reads as one before anything is picked
    if (!locked) BigButton(multiCheckLabel(selected.size), enabled = selected.isNotEmpty(), onClick = {
        checked = true
        val verdict = Grading.multi(selected, ex.answers)
        done(Outcome(verdict, selected.sorted().joinToString(", ") { ex.options[it] }, ex.answers.joinToString(", ") { ex.options[it] }, ex.explain))
    })
}

/** A select-all exercise's Check button with [n] options ticked: "Preveri · Check (2)"; "0 izbranih · 0 selected" before any. */
internal fun multiCheckLabel(n: Int): String = if (n <= 0) bi("playerScreen.noneSelected") else "${bi("playerScreen.check")} ($n)"

@Composable
private fun ScenarioEx(vm: AppViewModel, ex: Exercise.Scenario, locked: Boolean, done: (Outcome) -> Unit) {
    LaunchedEffect(Unit) { ex.line?.let(vm.speaker::say) }
    val look = LocalLook.current
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            ExText(AnnotatedString(ex.scene), look?.taps?.scene(ex.scene).orEmpty(), style = MaterialTheme.typography.titleMedium, tag = "scene")
            if (ex.line != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            ex.speaker?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                            val said = "„${ex.line}“"
                            ExText(AnnotatedString(said), look?.taps?.line(said).orEmpty(), style = MaterialTheme.typography.titleLarge, line = { ex.line }, tag = "line")
                        }
                    }
                    SpeakButton(vm.speaker, ex.line, Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    if (ex.options != null && ex.answer != null) ChoiceEx(ex.prompt, ex.options, ex.answer, ex.explain, locked, done, almost = ex.almost)
    else TypedEx(ex.prompt, null, locked, done, accept = ex.accept.orEmpty()) { a -> typed(a, ex.accept.orEmpty(), ex.explain) }
}

@Composable
private fun UnsupportedEx(ex: Exercise.Unsupported, done: (Outcome) -> Unit) {
    Prompt("🧩 ${bi("playerScreen.newExerciseType")}: ${ex.kind}")
    Text(inBase("playerScreen.updateToPlay"))
    Spacer(Modifier.height(20.dp))
    BigButton(bi("common.skip"), onClick = { done(Outcome(Verdict.ALMOST, "skipped (unsupported: ${ex.kind})", null, null)) })
}

/**
 * Shared typed-answer UI for cloze, dictation, match-graded translate and typed scenarios: known-word
 * suggestions above the input, and 💡 hints built from [accept] (none when it's empty).
 */
@Composable
internal fun TypedEx(
    prompt: String, placeholder: String?, locked: Boolean, done: (Outcome) -> Unit,
    instruction: String? = null, say: String? = null, accept: List<String> = emptyList(), grade: (String) -> Outcome,
) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var shown by remember { mutableStateOf<Hints.Shown?>(null) }
    // Picking a suggestion that is a word of the answer is help too: it counts as one hint (once).
    var assisted by remember { mutableStateOf(false) }
    val answerWords = remember(accept) { accept.flatMap { Grading.normalize(it).split(' ') }.toSet() }
    val level = shown?.level ?: 0
    val used = (level + if (assisted) 1 else 0).coerceAtMost(Hints.MAX_LEVEL)
    val vocabulary = LocalVocabulary.current
    val stage = LocalStage.current
    if (prompt.isNotBlank() || instruction != null) Prompt(prompt, instruction, say)
    if (vocabulary.isNotEmpty()) Suggestions(
        value, vocabulary, { value = it }, enabled = !locked, reserve = true,
        onPicked = { w -> if (Grading.normalize(w) in answerWords) assisted = true },
    )
    NoSuggestions {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !locked,
            placeholder = { Text(placeholder ?: bi("playerScreen.typeAnswer")) },
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = AnswerKeyboard,
        )
    }
    AnimatedVisibility(shown != null && !locked) {
        shown?.let { s -> HintStrip(s, enabled = !locked) { c -> value = insertAtCursor(value, c.toString()) } }
    }
    Spacer(Modifier.height(8.dp))
    if (!locked) {
        DiacriticChips(value, { value = it })
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (accept.isNotEmpty()) HintButton(
                used, onClick = { shown = Hints.hint(level + 1, value.text, accept); stage?.hint() },
                enabled = level < Hints.MAX_LEVEL, of = Hints.MAX_LEVEL,
            )
            BigButton(
                bi("playerScreen.check"),
                onClick = { done(grade(value.text).copy(hints = used)) },
                enabled = value.text.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReorderEx(ex: Exercise.Reorder, locked: Boolean, done: (Outcome) -> Unit) {
    // Tiles are indices into tokens + distractors, so repeated words stay distinct.
    val tiles = remember { ex.tokens + ex.distractors }
    // what a chip shows: its bare word, lowercase unless a name, so "posto." or "Jutri" doesn't give its place away
    val labels = remember { val names = ChipLabels.names(L10n.pair.target.code); tiles.map { ChipLabels.label(it, names) } }
    val ending = remember { ChipLabels.ending(ex.solutions.firstOrNull() ?: ex.tokens) }
    val pool = remember { tiles.indices.shuffled() }
    val placed = remember { mutableStateListOf<Int>() }
    var hints by remember { mutableIntStateOf(0) }
    val stage = LocalStage.current
    val look = LocalLook.current
    // a tile's word for its meaning (a long press: a tap lays it); its card shows no line, so not the order, till answered
    val solution = remember { ex.solutions.firstOrNull()?.joinToString(" ").orEmpty() }
    val tile: @Composable (Int) -> Unit = { i ->
        ExText(AnnotatedString(labels[i]), look?.taps?.tile(labels[i]).orEmpty(), hold = !locked, line = { if (locked) solution else "" }, tag = "tile")
    }
    Prompt(ex.prompt, ex.instruction ?: bi("playerScreen.putWordsOrder"), ex.say)
    // word chips by ear: the sentence heard, its words tapped in order
    ex.audio?.let { AudioPrompt(it) }
    ElevatedCard(Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in placed.toList()) InputChip(selected = true, onClick = { if (!locked) placed.remove(i) }, label = { tile(i) })
            if (ending.isNotEmpty()) Text(ending, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
    Spacer(Modifier.height(20.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in pool) if (i !in placed) FilterChip(selected = false, onClick = { if (!locked) placed += i }, label = { tile(i) })
    }
    Spacer(Modifier.height(20.dp))
    if (!locked) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        // 💡 lays the next right tile (taking back a wrong one first), never the last one.
        val next = Hints.nextTile(tiles, placed.toList(), ex.solutions)
        HintButton(hints, onClick = { next?.let { placed.clear(); placed += it; hints++; stage?.hint() } }, enabled = next != null)
        BigButton(
            bi("playerScreen.check"),
            enabled = placed.size >= 2,
            modifier = Modifier.weight(1f),
            onClick = {
                val order = placed.map { tiles[it] }
                val ok = Grading.reorder(order, ex.solutions)
                done(Outcome(if (ok) Verdict.CORRECT else Verdict.WRONG, order.joinToString(" "), ex.solutions.first().joinToString(" "), ex.explain, hints = hints))
            },
        )
    }
}

@Composable
private fun TutorGradedEx(
    vm: AppViewModel, moduleId: String?, index: Int, prompt: String, rubric: String, reference: String?,
    locked: Boolean, done: (Outcome) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var conv by remember { mutableStateOf<String?>(null) }
    val reply = conv?.let { vm.chat.gradedReplies[it] }
    LaunchedEffect(reply) {
        if (reply != null) {
            val score = reply.score ?: 0
            val v = when { score >= 8 -> Verdict.CORRECT; score >= 5 -> Verdict.ALMOST; else -> Verdict.WRONG }
            done(Outcome(v, value.text, reference, null, tutor = reply.text))
        }
    }
    Prompt(prompt)
    NoSuggestions {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            enabled = conv == null && !locked,
            placeholder = { Text(bi("playerScreen.writeSlovene")) },
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = AnswerKeyboard,
        )
    }
    Spacer(Modifier.height(8.dp))
    when {
        locked -> Unit
        conv == null -> {
            DiacriticChips(value, { value = it })
            Spacer(Modifier.height(16.dp))
            BigButton(bi("playerScreen.sendTutor"), enabled = value.text.isNotBlank(), onClick = {
                conv = vm.chat.requestGrading(
                    "Please grade this answer.",
                    buildJsonObject {
                        moduleId?.let { put("module_id", it) }
                        put("exercise_index", index)
                        put("prompt", prompt)
                        put("rubric", rubric)
                        put("answer", value.text)
                    },
                )
            })
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.padding(end = 12.dp))
            Text(bi("playerScreen.tutorGrading"), Modifier.weight(1f))
            TextButton(onClick = { done(Outcome(Verdict.ALMOST, value.text, reference, null, tutor = "_Skipped — the tutor's feedback will appear in the chat._")) }) { Text(inTarget("playerScreen.skip")) }
        }
    }
}

/**
 * An answer's feedback card: right, almost or not quite, the right answer and what was typed against it, the sentence to
 * hear, the tutor's word and the why, "💬 Vprašaj" and "Naprej". Under the question its middle scrolls in at most 45 % of
 * the screen; [side] (beside the question, [FeedbackPlace]) in the height there is, its buttons kept in view.
 */
@Composable
private fun Feedback(vm: AppViewModel, o: Outcome, slovene: String?, onAsk: () -> Unit, next: () -> Unit, book: @Composable () -> Unit = {}, side: Boolean = false) {
    val (label, color) = when (o.verdict) {
        Verdict.CORRECT -> "${inTarget("playerScreen.correct")} ✅" to AlpineGreen
        Verdict.ALMOST -> "${inTarget("playerScreen.almost")} 🟡" to FlameOrange
        Verdict.WRONG -> "${inTarget("playerScreen.notQuite")} ❌" to TriglavRed
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f)),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleLarge, color = color)
            // after the answer every word can be looked up, the one it tested too; the explanation's in lines, a long one
            // scrolls in the card (its buttons stay)
            val look = LocalLook.current
            val explained: BlockText = { styled, style, m ->
                ExText(styled, remember(styled, look) { look?.taps?.explanation(styled.text, styled.emphasized()).orEmpty() }, m, style = style, tag = "explain")
            }
            Column(
                Modifier.then(if (side) Modifier.weight(1f, fill = false) else Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.45f).dp))
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (o.hints > 0 && o.verdict != Verdict.WRONG) {
                    Text("💡 ${bi("playerScreen.hint")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                hintText(o.hint)?.let { Markdown(ExplainFormat.format(it), block = explained) }
                if (o.verdict != Verdict.CORRECT && o.correct != null) {
                    // a typed answer near the right one: what was typed set against it under it, as a worksheet's item
                    // has it (struck through, the right letters green: AnswerDiffText), the right one plain above
                    val diff = o.marks.isNotEmpty() && !o.spoken && o.answer.isNotBlank()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExText(
                            highlighted(o.correct, if (diff) emptyList() else o.marks, color), remember(o, look) { look?.taps?.correct(o.correct).orEmpty() },
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), tag = "correct",
                        )
                        SpeakButton(vm.speaker, o.correct)
                    }
                    if (diff) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${bi("playerScreen.wrote")}: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
                            AnswerDiffText(o.answer, o.correct, o.verdict, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (o.marks.isNotEmpty()) {
                        Text(if (o.spoken) "${bi("talkInput.iHeard")}: ${o.answer}" else "${bi("playerScreen.wrote")}: ${o.answer}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Always something to hear, also after a right answer: the whole Slovene sentence (a cloze with
                // its gap filled), unless the corrected answer above already is exactly that.
                val shownAbove = o.verdict != Verdict.CORRECT && o.correct != null
                slovene?.takeIf { !shownAbove || Grading.normalize(it) != Grading.normalize(o.correct) }?.let { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExText(
                            AnnotatedString(s), remember(s, look) { look?.taps?.sentence(s).orEmpty() },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), tag = "sentence",
                        )
                        SpeakButton(vm.speaker, s)
                    }
                }
                o.tutor?.let { Markdown(it) }
                o.explain?.let { Markdown(ExplainFormat.format(it), block = explained) }
                book()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onAsk, modifier = Modifier.heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
                    Text("💬 ${bi("playerScreen.ask")}")
                }
                BigButton(bi("common.next"), onClick = next, color = color, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The Slovene to hear in the feedback box: the full answer sentence, whatever the exercise type. */
/**
 * A choice's sentence with a gap ("Nimam ___. (I don't have time.)", a word form question's "Jaz ____ na klop. (sesti)")
 * filled with its right option, when the options are in the language learned (none reads as the base), without what
 * the brackets at its end add: "Nimam časa."; null for a choice without a gap.
 */
private fun gapFilled(ex: Exercise.Choice): String? {
    if (!Regex("_{2,}").containsMatchIn(ex.prompt)) return null
    val a = ex.options.getOrNull(ex.answer)?.let(::unpictured) ?: return null
    val pair = L10n.pair
    if (!ex.sayOptions && ex.options.any { Spoken.looks(unpictured(it), pair.base, pair.target) }) return null
    return ex.prompt.replace(Regex("_{2,}"), a).replace(Regex("""\s*\([^()]*\)\s*$"""), "").trim().takeIf { it.isNotEmpty() }
}

internal fun sloveneOf(ex: Exercise, isSlovene: (String) -> Boolean): String? = when (ex) {
    is Exercise.Flashcard -> ex.front.takeIf { ex.speak }
    is Exercise.Choice -> ex.say ?: ex.audio
        ?: gapFilled(ex)
        ?: ex.options.getOrNull(ex.answer)?.let(::unpictured)?.takeIf { ex.sayOptions || isSlovene(it) }
        ?: ex.prompt.takeIf { it.isNotBlank() && isSlovene(it) }
    // without what the brackets at its end add (a translation, a word form question's lemma: "Jaz ___. (sesti)")
    is Exercise.Cloze -> ex.accept.firstOrNull()?.let { ex.text.replace("___", it).replace(Regex("""\s*\([^()]*\)\s*$"""), "") }
    is Exercise.Reorder -> ex.solutions.firstOrNull()?.joinToString(" ")
    is Exercise.Translate -> ex.accept.firstOrNull()
    is Exercise.Scenario -> ex.accept?.firstOrNull() ?: ex.answer?.let { ex.options?.getOrNull(it) }
    is Exercise.Dictation -> ex.audio
    is Exercise.Speak -> ex.say
    is Exercise.Multi, is Exercise.Free, is Exercise.Unsupported -> null
    // a gap text heard whole, its gaps filled; the others are lists of their own
    is Exercise.GapText -> si.lanisce.lani.data.Sheets.filled(ex, ex.gaps.map { it.accept.firstOrNull() })
    is Exercise.Table, is Exercise.Transform, is Exercise.Sort -> null
    // a counting card's four lines are on it, with their 🔊; a forms card's rows too
    is Exercise.Counting, is Exercise.FormsTable -> null
    // a number's word ("sedemindvajset"), a date said whole, a clock time ("ob pol šestih")
    is Exercise.Numeral -> ex.word.takeIf { it.isNotBlank() }
    // the case trainer's sentence, its answer in it
    is Exercise.CaseTask -> ex.sentence
}

/**
 * What a right answer said in the language learned, for its grammar page's "Tvoji stavki · Your sentences": a gap filled
 * with the option picked ("Otroci so v šoli."), the option itself when the options are in that language ("ob ribniku";
 * none reads as the base), else the exercise's sentence ([sloveneOf]); without a translation in brackets after it.
 */
internal fun saidOf(ex: Exercise, isSlovene: (String) -> Boolean): String? {
    val s = when (ex) {
        is Exercise.Choice -> {
            val a = ex.options.getOrNull(ex.answer)?.let(::unpictured)
            val pair = L10n.pair
            val learned = a != null && (ex.sayOptions || ex.options.none { Spoken.looks(unpictured(it), pair.base, pair.target) })
            when {
                a != null && learned && "___" in ex.prompt -> ex.prompt.replace(Regex("_{2,}"), a)
                a != null && learned -> a
                else -> ex.say ?: ex.prompt.takeIf { it.isNotBlank() && isSlovene(it) }
            }
        }
        else -> sloveneOf(ex, isSlovene)
    }
    return s?.replace(Regex("""\s*\([^()]*\)\s*$"""), "")?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * One-line label plus the full exercise and outcome, for questions to the tutor; [source] ([ExerciseSource]) says which
 * item it was, so a wrong or confusing one can be found and fixed. Without an outcome ([o] null: the exercise's 💬 in the
 * top bar before it was answered) the exercise alone, `answered: false`, with what it expects (`expected`, for the tutor:
 * help without giving it away) and a label that gives nothing away either (no sound's text, no card's back).
 */
internal fun chatContext(ex: Exercise, o: Outcome?, moduleId: String?, source: JsonObject? = null, steps: List<si.lanisce.lani.data.ItemMark> = emptyList()): ChatContext {
    val (type, prompt) = typeAndPrompt(ex)
    val data = buildJsonObject {
        put("type", type)
        put("prompt", prompt)
        if (ex is Exercise.Choice) put("options", buildJsonArray { ex.options.forEach { add(JsonPrimitive(it)) } })
        // its near misses, which a pick of is almost (the tutor's `almost`)
        if (ex is Exercise.Choice && ex.almost.isNotEmpty()) put("almost", buildJsonArray { ex.almost.mapNotNull(ex.options::getOrNull).forEach { add(JsonPrimitive(it)) } })
        if (ex is Exercise.Multi) put("options", buildJsonArray { ex.options.forEach { add(JsonPrimitive(it)) } })
        // a counting card: its noun and gender, each step (asked, its options, typed or not) and how those answered went
        if (ex is Exercise.Counting) put("counting", countingSteps(ex, o?.sheet ?: steps))
        // a forms card: its word, each gap (where, its options, typed or not) and how those answered went
        if (ex is Exercise.FormsTable) put("forms_card", formsCardGaps(ex, o?.sheet ?: steps))
        if (o == null) {
            put("answered", false)
            expectedOf(ex)?.let { put("expected", it) }
            moduleId?.let { put("module_id", it) }
            source?.let { put("source", it) }
            return@buildJsonObject
        }
        // a worksheet's items: each answer, what was right, where it stands (a counting card's steps are in its counting)
        if (o.sheet.isNotEmpty() && ex !is Exercise.Counting && ex !is Exercise.FormsTable) {
            val items = si.lanisce.lani.data.Sheets.items(ex)
            put("items", buildJsonArray {
                o.sheet.forEachIndexed { i, m ->
                    add(buildJsonObject {
                        items.getOrNull(i)?.let { put("in", it.frame) }
                        put("answer", m.answer)
                        put("expected", m.expected)
                        put("verdict", m.verdict.name.lowercase())
                        if (m.lookedUp) put("looked_up", true)
                    })
                }
            })
        }
        put("learner_answer", o.answer)
        o.correct?.let { put("expected", it) }
        put("verdict", o.verdict.name.lowercase())
        if (o.hint != Grading.Hint.NONE) put("hint", o.hint.name.lowercase())
        if (o.hints > 0) put("hints_used", o.hints)
        o.lookedUp?.let { put("looked_up", it.json) }
        o.explain?.let { put("explain", it) }
        moduleId?.let { put("module_id", it) }
        source?.let { put("source", it) }
    }
    val label = if (o == null) shownPrompt(ex).take(80) else "${prompt.take(50)} → ${o.answer.take(30)}"
    return ChatContext(label, data)
}

/** A counting card's steps for the tutor: the noun, its gender, each step's number, line, options, and its answer so far. */
private fun countingSteps(ex: Exercise.Counting, marks: List<si.lanisce.lani.data.ItemMark>): JsonObject = buildJsonObject {
    put("lemma", ex.lemma)
    put("gender", ex.gender)
    put("typed", ex.typed)
    put("steps", buildJsonArray {
        ex.steps.forEachIndexed { i, s ->
            add(buildJsonObject {
                put("n", s.n)
                put("expected", s.line)
                if (!ex.typed) put("options", buildJsonArray { s.options.forEach { add(JsonPrimitive(it)) } })
                marks.getOrNull(i)?.let { m ->
                    put("answer", m.answer)
                    put("verdict", m.verdict.name.lowercase())
                    if (m.verdict == Verdict.WRONG) put("kind", si.lanisce.lani.data.Counting.kind(s, m.answer).wire)
                }
            })
        }
    })
}

/** A forms card's gaps for the tutor: the word, its part of speech, each gap's slot, where it stands, its options and its answer so far. */
private fun formsCardGaps(ex: Exercise.FormsTable, marks: List<si.lanisce.lani.data.ItemMark>): JsonObject = buildJsonObject {
    put("lemma", ex.lemma)
    put("pos", ex.pos)
    ex.gender?.let { put("gender", it) }
    put("typed", ex.typed)
    put("fresh", ex.fresh)
    put("gaps", buildJsonArray {
        ex.gaps.forEachIndexed { i, g ->
            add(buildJsonObject {
                put("key", g.key)
                put("where", g.where)
                put("expected", listOf(g.before, g.answer + g.after).filter { it.isNotBlank() }.joinToString(" "))
                if (!ex.typed || g.chosen) put("options", buildJsonArray { g.options.forEach { add(JsonPrimitive(it)) } })
                marks.getOrNull(i)?.let { m ->
                    put("answer", m.answer)
                    put("verdict", m.verdict.name.lowercase())
                    if (m.verdict == Verdict.WRONG) ex.data?.let { si.lanisce.lani.data.FormsCard.kind(it, g.key, m.answer) }?.let { put("kind", it) }
                }
            })
        }
    })
    // the rest of the table on screen: what is greyed still (not introduced); the sections without a gap asked now
    // aren't shown in the review at all (their titles: hidden)
    put("preview", buildJsonArray { ex.sections.flatMap { s -> s.rows.flatMap { r -> r.cells } }.filterNotNull().filter { it.state == si.lanisce.lani.data.FormsCellState.PREVIEW }.forEach { add(JsonPrimitive(it.key)) } })
    if (ex.hidden.isNotEmpty()) put("hidden", buildJsonArray { ex.hidden.forEach { add(JsonPrimitive(it)) } })
}

/** What [ex] expects as its answer, for the tutor ([chatContext] before an answer); null where it isn't one text. */
private fun expectedOf(ex: Exercise): String? = when (ex) {
    is Exercise.Flashcard -> ex.back
    is Exercise.Choice -> ex.options.getOrNull(ex.answer)
    is Exercise.Cloze -> ex.accept.firstOrNull()
    is Exercise.Reorder -> ex.solutions.firstOrNull()?.joinToString(" ")
    is Exercise.Translate -> ex.accept.firstOrNull()
    is Exercise.Scenario -> ex.accept?.firstOrNull() ?: ex.answer?.let { ex.options?.getOrNull(it) }
    is Exercise.Dictation -> ex.accept.firstOrNull() ?: ex.audio
    is Exercise.Speak -> ex.say
    is Exercise.Multi -> ex.answers.mapNotNull { ex.options.getOrNull(it) }.joinToString(" · ").ifEmpty { null }
    is Exercise.GapText -> si.lanisce.lani.data.Sheets.filled(ex, ex.gaps.map { it.accept.firstOrNull() })
    is Exercise.Counting -> ex.steps.joinToString(" · ") { it.line }
    is Exercise.FormsTable -> ex.gaps.joinToString(" · ") { g -> listOf(g.before, g.answer + g.after).filter { it.isNotBlank() }.joinToString(" ") }
    is Exercise.Numeral -> ex.shown
    is Exercise.CaseTask -> si.lanisce.lani.data.CaseTrainer.expectedLine(ex)
    is Exercise.Free, is Exercise.Unsupported, is Exercise.Table, is Exercise.Transform, is Exercise.Sort -> null
}

/**
 * What [ex] shows before it's answered, in one line, for the chat's label of it: as [typeAndPrompt] says it, but a sound
 * only as 🎧 (its text is what's heard, often the answer) and a card's front only.
 */
private fun shownPrompt(ex: Exercise): String = when (ex) {
    is Exercise.Flashcard -> ex.front
    is Exercise.Choice -> listOfNotNull(ex.audio?.let { "🎧" }, ex.prompt.takeIf { it.isNotBlank() }).joinToString(" · ")
    is Exercise.Cloze -> listOfNotNull(ex.audio?.let { "🎧" }, ex.text).joinToString(" · ")
    is Exercise.Reorder -> listOfNotNull(ex.audio?.let { "🎧" }, ex.prompt.takeIf { it.isNotBlank() }).joinToString(" · ")
    is Exercise.Dictation -> "🎧"
    is Exercise.Speak -> "🎤 ${ex.prompt}"
    // the noun, not its lines (they are what it asks)
    is Exercise.Counting -> "🔢 ${ex.lemma}: 1 · 2 · 3 · 5"
    // the word and where its gaps are, not its forms (they are what it asks)
    is Exercise.FormsTable -> "📋 ${ex.lemma}: ${ex.gaps.map { it.where.substringBefore(" — ") }.distinct().joinToString(", ")}"
    // a number heard only as 🎧 (its words are the answer)
    is Exercise.Numeral -> if (ex.audio != null) "🔢 🎧" else "🔢 ${ex.prompt}"
    // the sentence asked about (a gap's prompt): never its answer
    is Exercise.CaseTask -> "🧩 ${ex.prompt.ifBlank { ex.sentence }}"
    else -> typeAndPrompt(ex).second
}.ifBlank { typeAndPrompt(ex).first }

/** An exercise's type ("translate") and what it asked, in one line, as the tutor sees it. */
private fun typeAndPrompt(ex: Exercise): Pair<String, String> = when (ex) {
    is Exercise.Flashcard -> "flashcard" to "${ex.front} = ${ex.back}"
    is Exercise.Choice -> "choice" to listOfNotNull(ex.audio?.let { "🎧 $it" }, ex.prompt.takeIf { it.isNotBlank() }).joinToString(" · ")
    is Exercise.Cloze -> "cloze" to listOfNotNull(ex.audio?.let { "🎧 $it" }, ex.text).joinToString(" · ")
    is Exercise.Reorder -> "reorder" to listOfNotNull(ex.audio?.let { "🎧 $it" }, ex.prompt.takeIf { it.isNotBlank() }).joinToString(" · ")
    is Exercise.Translate -> "translate" to ex.prompt
    is Exercise.Free -> "free" to ex.prompt
    is Exercise.Multi -> "multi" to ex.prompt
    is Exercise.Scenario -> "scenario" to listOfNotNull(ex.scene, ex.line?.let { "„$it“" }, ex.prompt).joinToString(" · ")
    is Exercise.Dictation -> "dictation" to "🎧 ${ex.audio}"
    is Exercise.Speak -> "speak" to "🎤 ${ex.prompt} → ${ex.say}"
    is Exercise.Unsupported -> ex.kind to ex.kind
    is Exercise.GapText -> "gaptext" to listOfNotNull(ex.instruction, ex.text).joinToString(" · ")
    is Exercise.Table -> "table" to listOfNotNull(ex.instruction, ex.prompt ?: ex.lemma).joinToString(" · ")
    is Exercise.Transform -> "transform" to listOfNotNull(ex.instruction, ex.cue).joinToString(" · ")
    is Exercise.Sort -> "sort" to listOfNotNull(ex.instruction, ex.prompt, ex.columns.joinToString(" | ") { it.label }).joinToString(" · ")
    is Exercise.Counting -> "counting" to "${ex.lemma} (${ex.gender}): 1 · 2 · 3 · 5${if (ex.typed) ", typed" else ""}"
    is Exercise.FormsTable -> "forms_card" to "${ex.lemma} (${ex.pos}): ${ex.gaps.joinToString(", ") { it.key }}${if (ex.typed) ", typed" else ""}"
    is Exercise.Numeral -> "numbers_${ex.mode}" to listOfNotNull(ex.audio?.let { "🎧 $it" }, ex.prompt.takeIf { it.isNotBlank() }).joinToString(" · ")
    is Exercise.CaseTask -> "cases_${ex.kind}" to listOfNotNull(ex.prompt.ifBlank { ex.sentence }, ex.tokens.getOrNull(ex.word)?.takeIf { ex.kind == "which" }?.let { "[$it]" }).joinToString(" ")
}

/**
 * What a review card's exercise was answered with, for the node's log of recent answers (POST /reviews `answers`,
 * companion/bridge/src/answers.ts): the card, how it was asked ([variant]; null where no planner chose it, as a
 * challenge's exercise), the exercise and what it asked, what was typed or heard against what was expected, the verdict
 * and the grading hint, what was looked up on the cheat sheet during it (`looked_up`: the cards opened, their pages; an
 * older bridge leaves it out). A flashcard is self-rated: no answer to keep. So when Jan reports a card later without
 * "Ask", the tutor can see what was typed.
 */
internal fun reviewAnswer(ex: Exercise, o: Outcome, cardId: String, variant: ReviewPlanner.Variant?): JsonObject {
    val (type, prompt) = typeAndPrompt(ex)
    return buildJsonObject {
        put("item_id", cardId)
        variant?.let { put("variant", it.name.lowercase()) }
        put("type", type)
        put("prompt", prompt.take(160))
        if (ex !is Exercise.Flashcard) {
            put("answer", o.answer.take(200))
            (if (ex is Exercise.Counting || ex is Exercise.FormsTable) expectedOf(ex) else o.correct)?.let { put("expected", it.take(200)) }
        }
        put("verdict", o.verdict.name.lowercase())
        if (o.hint != Grading.Hint.NONE) put("hint", o.hint.name.lowercase())
        if (o.spoken) put("spoken", true)
        if (o.hints > 0) put("hints_used", o.hints)
        o.lookedUp?.let { put("looked_up", it.json) }
    }
}

/** Why an answer is almost or the ending to look at, for the feedback ([Grading.Hint]); a worksheet item's too. */
internal fun hintText(h: Grading.Hint): String? = when (h) {
    Grading.Hint.NONE -> null
    Grading.Hint.DIACRITICS -> bi("playerScreen.mind")
    Grading.Hint.ACCENT -> bi("playerScreen.mindAccents")
    Grading.Hint.UMLAUT -> bi("playerScreen.mindUmlauts")
    Grading.Hint.APOSTROPHE -> bi("playerScreen.mindApostrophe")
    Grading.Hint.TYPO -> bi("playerScreen.looksLikeTypoSo")
    Grading.Hint.ENDING -> bi("playerScreen.checkEndingSloveneCarries")
}

/** The correct answer in bold, with the letters that differ from the learner's answer underlined in [color]. */
private fun highlighted(text: String, marks: List<IntRange>, color: Color): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
    for (r in marks) {
        if (r.first >= 0 && r.last < text.length) {
            addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Black), r.first, r.last + 1)
        }
    }
}

private fun summary(m: Module, outcomes: List<Outcome>): String {
    val c = outcomes.count { it.verdict == Verdict.CORRECT }
    val early = if (outcomes.size < m.exercises.size) " Stopped early after ${outcomes.size} of ${m.exercises.size} exercises." else ""
    return "Finished module \"${m.title}\" (${m.id} v${m.version}) in the app: $c/${outcomes.size} correct.$early"
}

private fun sessionData(m: Module, outcomes: List<Outcome>, started: Long, quest: JsonObject? = null): JsonObject = buildJsonObject {
    put("module_id", m.id)
    put("module_version", m.version)
    put("targets", buildJsonArray { m.targets.forEach { add(JsonPrimitive(it)) } })
    put("duration_minutes", minutesSince(started))
    put("results", buildJsonArray {
        outcomes.forEachIndexed { i, o ->
            add(buildJsonObject {
                put("index", i)
                put("type", m.exercises[i]::class.simpleName?.lowercase() ?: "?")
                put("verdict", o.verdict.name.lowercase())
                put("answer", o.answer)
                o.correct?.let { put("expected", it) }
                if (o.hints > 0) put("hints_used", o.hints)
                o.lookedUp?.let { put("looked_up", it.json) }
            })
        }
    })
    quest?.let { put("quest", it) }
}
