package si.lanisce.lani.ui.school

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import si.lanisce.lani.ui.stage.StageLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.GrammarCell
import si.lanisce.lani.data.Intro
import si.lanisce.lani.data.IntroSpeech
import si.lanisce.lani.data.IntroVoice
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.data.Sheets
import si.lanisce.lani.game.School
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.AnswerKeyboard
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.DiacriticChips
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.NoSuggestions
import si.lanisce.lani.ui.Portrait
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.inlineMarkdown
import si.lanisce.lani.ui.stage.portraitPx
import si.lanisce.lani.ui.stage.rememberReducedMotion
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.words.WordText

// A unit's guided introduction (companion/COURSE.md, "The introduction"): the new material in small steps as a teacher
// brings it, each a few lines, an example or two and a bit more of the table, then a quick question about it; the answer
// shown with its why, nothing lost on a miss; the table grows as the steps go; at the end the rule's page as a summary.
// Resumable, and skippable ("⏭ Poznam to · I know this").

/** Unit [id]'s intro, where the learner left it. */
@Composable
fun IntroScreen(vm: AppViewModel, id: String, fromVillage: Boolean) {
    val unit = vm.school.unit(id)
    val intro = unit?.intro
    if (unit == null || intro == null) {
        LaunchedEffect(id) { vm.openSchool(fromVillage) }
        return
    }
    val back = { vm.openUnit(unit.id, fromVillage) }
    BackHandler(onBack = back)
    val p = vm.game.state?.let { School.progress(it, unit.language, unit.id) }
    // resumed where it was left (from the start again once it was done)
    var step by remember { mutableIntStateOf(if (p?.introDone == true) 0 else (p?.intro ?: 0).coerceIn(0, intro.steps.size)) }
    LaunchedEffect(Unit) { if (step == 0) vm.school.introProgress(unit, 0, done = false) }
    // the lesson begins: she walks in once, at the first step shown
    val walkIn = remember { step }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { EmojiLabel("✕", Labels.CLOSE) }
            LinearProgressIndicator(
                progress = { step.toFloat() / intro.steps.size }, modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape), color = XpGold,
            )
            TextButton(onClick = { vm.school.introProgress(unit, intro.steps.size, done = true); back() }) { Text("⏭ ${bi("school.knowThis")}") }
        }
        Text("📖 ${unit.titleShown(L10n.pair)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        if (step >= intro.steps.size) {
            Summary(vm, unit, intro, Modifier.weight(1f)) {
                vm.school.introProgress(unit, intro.steps.size, done = true)
                back()
            }
            return@Column
        }
        key(step) {
            IntroStepView(vm, unit, intro, step, Modifier.weight(1f), walkIn = step == walkIn) {
                vm.school.introProgress(unit, step + 1, done = false)
                step++
            }
        }
    }
}

/**
 * One step on the school's stage: the teacher big beside what she says now, the explanation on the board below her, the
 * examples, the table so far (this step's rows marked new), its question, "Naprej". Read aloud as it appears ([IntroVoice]:
 * the setting "🔊 Beri razlago", not while TalkBack reads), what is read marked, and she points at it with her pointer
 * ([IntroPointer]: the table's row of a form, the example, the question, the words read), turned toward it, back to the
 * learner when she asks; 🔊 and 🐢 read it again, a tap on the text stops it, leaving the step too. [walkIn]: the lesson
 * begins with this step, and she walks in first. Nothing moves with the system's animations off or the keyboard up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IntroStepView(vm: AppViewModel, unit: CourseUnit, intro: Intro, i: Int, modifier: Modifier, walkIn: Boolean = false, next: () -> Unit) {
    val s = intro.steps[i]
    val base = L10n.pair.base.code
    var answered by remember { mutableStateOf(s.ask == null) }
    val voice = vm.introVoice
    val t = vm.school.teacher()
    val reader = IntroVoice.Reader(t?.speakerVoice ?: si.lanisce.lani.data.Clips.FEMALE, t?.voice, t?.id)
    val say = Grammar.text(s.say, base)
    val examples = s.examples.map { it.target(unit.language) }
    val script = remember(unit.id, i, say) {
        // Slovene: what the app's check says is, and the words the intro itself shows ("**Biti**")
        val known = IntroSpeech.vocabulary(intro, unit.language, unit.title[unit.language], vm.speaker::isSlovene)
        IntroSpeech.step("${unit.id}/$i/${say.hashCode()}", say, base, examples, s.ask) { t -> vm.speaker.isSlovene(t) || IntroSpeech.within(t, known) }
    }
    val answerId = "${script.id}/answer"
    val now = voice.now?.takeIf { it.id == script.id || it.id == answerId }?.mark
    val reading = voice.reading == script.id || voice.reading == answerId
    val reduced = rememberReducedMotion()
    val keyboard = WindowInsets.isImeVisible
    // read as it appears (after her walk-in); stopped as it's left
    LaunchedEffect(script.id) {
        if (voice.auto && !voice.screenReader) {
            delay(if (walkIn && !reduced) WALK_MS + READ_AFTER_MS else READ_AFTER_MS)
            voice.play(script, reader)
        }
    }
    DisposableEffect(script.id) {
        onDispose {
            voice.stop(script.id)
            voice.stop(answerId)
        }
    }
    var reaction by remember { mutableStateOf<Grading.Verdict?>(null) }
    LaunchedEffect(reaction) { if (reaction != null) { delay(REACT_MS); reaction = null } }
    val talking by rememberTalking(vm)
    // a smile or a shrug at an answer (the cheer is the test's, when the unit is passed)
    val pose = when {
        reaction == Grading.Verdict.CORRECT || reaction == Grading.Verdict.ALMOST -> Pose.HAPPY
        reaction == Grading.Verdict.WRONG -> Pose.SAD
        talking -> Pose.TALK
        else -> Pose.IDLE
    }

    // --- what she points at, and where -------------------------------------------------------------------------------
    val rows = intro.steps.take(i + 1).flatMap { it.rows }
    val rowWords = remember(rows) { rows.map { r -> r.map { c -> if (c.isWords) IntroPointer.words(c.shown(base)) else emptySet() } } }
    val line = (now as? IntroSpeech.Mark.Part)?.let { m -> script.stitched.firstOrNull { it.mark == m } }
    val before = remember { arrayOfNulls<IntroPointer.Target>(1) }
    val target = remember(now) { IntroPointer.target(now, line, rowWords, before[0]).also { before[0] = it } }
    // asking the question: she points at it, then turns to the learner
    var asking by remember { mutableStateOf(false) }
    LaunchedEffect(now) {
        asking = false
        if (now == IntroSpeech.Mark.Question) {
            delay(IntroPointer.QUESTION_POINT_MS)
            asking = true
        }
    }
    val points = IntroPointer.points(reading && target != null, reduced, keyboard, asking)
    val bounds = remember { mutableStateMapOf<Any, Rect>() }
    var runAt by remember { mutableStateOf<Rect?>(null) }
    var viewport by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var hand by remember { mutableStateOf<Hand?>(null) }
    val density = LocalDensity.current
    val tip = target?.takeIf { points }?.let { tg ->
        val seen = IntroPointer.bounds(tg) { k -> if (k is IntroPointer.Target.Run) runAt else bounds[k] }
            ?.let { r -> viewport?.let { v -> if (r.overlaps(v)) r.intersect(v) else null } ?: r }
        // a card's or a row's tip in from its start, to her right: she turns toward the content to point at it
        with(density) { IntroPointer.tip(tg, seen, minSeen = 16.dp.toPx(), inset = TIP_INSET_DP.dp.toPx()) }
    }
    // the tip moves to a new target from where it was, then follows it (a scroll)
    val move = remember { Animatable(1f) }
    val kept = remember { arrayOfNulls<Any>(3) } // the target last seen, where the tip was shown last, where it moves from
    val changed = target != kept[0]
    if (changed) {
        kept[0] = target
        kept[2] = kept[1]
    }
    LaunchedEffect(target) { moveTip(move) }
    val shown = tip?.let { IntroPointer.between(kept[2] as Offset?, it, if (changed) 0f else move.value) }?.also { kept[1] = it }
    // turned toward what she points at; else, while she explains, toward the content
    val aim = when {
        reduced || keyboard || !reading || asking -> null
        shown != null -> shown
        else -> viewport?.center
    }

    val compact = StageLayout.compact(LocalConfiguration.current.screenHeightDp, density.fontScale, keyboard)
    val stage = if (compact) STAGE_SMALL_DP.dp else STAGE_DP.dp
    Box(modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            // the stage: the teacher, what she says now, the step and its 🔊 🐢
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                if (t != null) {
                    TeacherStage(
                        t.art, t.id.hashCode(), portraitPx(stage), pose, aim, walkIn = walkIn, animate = !reduced && !keyboard,
                        modifier = Modifier.size(stage).clip(RoundedCornerShape(18.dp)).border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                            .semantics { contentDescription = t.name },
                        onHand = { hand = it },
                    )
                } else Text("👩‍🏫", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bi("school.stepOf", "n" to i + 1, "of" to intro.steps.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        ReadButtons(reading, onRead = { slow -> if (reading && !slow) voice.stop() else voice.play(script, reader, slow) })
                    }
                    Saying(saying(now, line, script, examples, s.ask) ?: t?.name.orEmpty(), lines = if (compact) 1 else 2)
                }
            }
            Column(Modifier.weight(1f).onGloballyPositioned { viewport = it.boundsInRoot() }.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // a tap on what is read stops it (and does nothing else then)
                Column(Modifier.tapToStop(reading) { voice.stop() }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // the board: the explanation
                    val whole = now == IntroSpeech.Mark.Whole
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().onGloballyPositioned { bounds[IntroPointer.Target.Explanation] = it.boundsInRoot() },
                        border = if (whole) androidx.compose.foundation.BorderStroke(2.dp, XpGold) else null,
                    ) {
                        Markdown(
                            say, Modifier.padding(12.dp),
                            marked = { l -> (now as? IntroSpeech.Mark.Part)?.takeIf { it.line == l }?.segments },
                            onMarked = { runAt = it },
                        )
                    }
                    // the phone can't say the learner's own language: said once, in the step where it first happened
                    var note by remember { mutableStateOf(false) }
                    LaunchedEffect(voice.noBaseVoice, reading) {
                        if (voice.noBaseVoice && reading && !voice.noBaseVoiceNoted) {
                            voice.notedNoBaseVoice()
                            note = true
                        }
                    }
                    if (note) Text("🔇 ${bi("school.noBaseVoice", "lang" to L10n.pair.base.ownName)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.examples.forEachIndexed { k, e ->
                        ExampleRow(
                            vm, e.target(unit.language), e.meaning(base), e.noteIn(base), marked = now == IntroSpeech.Mark.Example(k),
                            modifier = Modifier.onGloballyPositioned { bounds[IntroPointer.Target.Example(k)] = it.boundsInRoot() },
                        )
                    }
                    if (intro.head.isNotEmpty() && rows.isNotEmpty()) GrowingTable(
                        vm, intro.head, rows, fresh = s.rows.size,
                        marked = (target as? IntroPointer.Target.Rows)?.rows?.takeIf { reading }.orEmpty(),
                        onRow = { r, at -> bounds[IntroPointer.Row(r)] = at },
                    )
                }
                s.ask?.let { ask ->
                    IntroAsk(
                        vm, ask, reader, asked = now == IntroSpeech.Mark.Question, said = now == IntroSpeech.Mark.Answer,
                        modifier = Modifier.onGloballyPositioned { bounds[IntroPointer.Target.Question] = it.boundsInRoot() },
                        onSaid = { bounds[IntroPointer.Target.Answer] = it },
                    ) { v, sentence ->
                        answered = true
                        reaction = v
                        // the sentence of the answer in her voice, as an exercise's feedback says it
                        if (sentence != null && voice.auto && !voice.screenReader) voice.play(IntroSpeech.Script(answerId, null, listOf(IntroSpeech.Line(sentence, true, IntroSpeech.Mark.Answer))), reader)
                        else voice.stop()
                    }
                }
            }
            BigButton(bi("common.next"), enabled = answered, onClick = next, color = if (answered) AlpineGreen else MaterialTheme.colorScheme.primary)
        }
        // her pointer, over the stage and the board
        PointerStick(hand, shown, visible = points && shown != null, origin = origin, modifier = Modifier.matchParentSize())
    }
}

/** What she says now, for the stage's bubble: the run, the example, the question, the answer; null between. */
private fun saying(now: IntroSpeech.Mark?, line: IntroSpeech.Line?, script: IntroSpeech.Script, examples: List<String>, ask: Exercise?): String? = when (now) {
    null -> null
    IntroSpeech.Mark.Whole -> script.explanation?.plain
    is IntroSpeech.Mark.Part -> line?.text
    is IntroSpeech.Mark.Example -> examples.getOrNull(now.index)
    IntroSpeech.Mark.Question -> ask?.let(IntroSpeech::question)
    IntroSpeech.Mark.Answer -> script.lines.lastOrNull { it.mark == IntroSpeech.Mark.Answer }?.text
}

/** The stage's speech bubble: what she says now (her name between). */
@Composable
private fun Saying(text: String, lines: Int) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp).clearAndSetSemantics { }, style = MaterialTheme.typography.bodyMedium,
            maxLines = lines, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The teacher's size on the stage: as the dialogs' stage portraits; small on a short screen, a large font, the keyboard up. */
private const val STAGE_DP = 118
private const val STAGE_SMALL_DP = 56

/** How far in from a card's start (at most a quarter of it) her pointer's tip goes. */
private const val TIP_INSET_DP = 96

/** How long after a step appears it is read (it has drawn, the eye found the teacher). */
private const val READ_AFTER_MS = 450L

/** How long the teacher's reaction to an answer shows (a cheer, a smile, a shrug). */
private const val REACT_MS = 2_400L

/** 🔊 (⏹ while it reads) and 🐢: the step read again, normally or slowly; ⏹ stops it. */
@Composable
private fun ReadButtons(reading: Boolean, onRead: (slow: Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalIconButton(onClick = { onRead(false) }) { if (reading) EmojiLabel("⏹", Labels.STOP) else EmojiLabel("🔊", bi("school.readAloud")) }
        FilledTonalIconButton(onClick = { onRead(true) }) { EmojiLabel("🐢", Labels.SLOW) }
    }
}

/**
 * The teacher's portrait, alive as the stage's: breathing and blinking (unless the system's animations are off), in [pose]
 * (talking while her voice plays, her reaction to an answer); 👩‍🏫 without a teacher in the cast.
 */
@Composable
internal fun TeacherPortrait(t: si.lanisce.lani.game.villagers.Villager?, pose: Pose, size: androidx.compose.ui.unit.Dp) {
    if (t == null) {
        Text("👩‍🏫", style = MaterialTheme.typography.headlineMedium)
        return
    }
    val reduced = rememberReducedMotion()
    Portrait(
        t.art, pose, animate = !reduced, px = portraitPx(size), seed = t.id.hashCode(),
        modifier = Modifier.size(size).clip(RoundedCornerShape(14.dp)).semantics { contentDescription = t.name },
    )
}

/**
 * A voice of the app speaks now ([Speaker.talking]: a 🔊, the intro read aloud): the teacher is drawn talking. Looked at a
 * few times a second while the screen shows ([Speaker.talking] isn't Compose state: its players call back on their own).
 */
@Composable
internal fun rememberTalking(vm: AppViewModel): androidx.compose.runtime.State<Boolean> = produceState(false, vm) {
    while (true) {
        value = vm.introVoice.speaking || vm.speaker.talking
        delay(TALK_POLL_MS)
    }
}

private const val TALK_POLL_MS = 120L

/**
 * Taps on what this modifies stop the reading while [active]: the tap is taken (a link or a 🔊 under it doesn't fire), a
 * drag still scrolls.
 */
private fun Modifier.tapToStop(active: Boolean, stop: () -> Unit): Modifier = pointerInput(active) {
    if (!active) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var tap: PointerInputChange? = null
            while (true) {
                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                if (!c.pressed) {
                    tap = c
                    break
                }
            }
            tap
        }
        if (up != null) {
            up.consume()
            stop()
        }
    }
}

/** An example of the language: its words tappable, 🔊, what it means and its note; [marked] while it is read aloud. */
@Composable
internal fun ExampleRow(vm: AppViewModel, said: String, meant: String, note: String? = null, marked: Boolean = false, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (marked) XpGold.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (marked) androidx.compose.foundation.BorderStroke(2.dp, XpGold) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                WordText(said, onWord = si.lanisce.lani.ui.words.lookUpIn(vm, said, meant, si.lanisce.lani.data.Words.READING), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (meant != said) Text(meant, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                note?.let { Text(inlineMarkdown(it), style = MaterialTheme.typography.bodySmall) }
            }
            val t = vm.school.teacher()
            SpeakButton(vm.speaker, said, voiceName = t?.speakerVoice ?: si.lanisce.lani.data.Clips.FEMALE, fallback = t?.voice, person = t?.id)
        }
    }
}

/** The table the steps build: its head, the rows so far, the last [fresh] ones (this step's) marked. */
@Composable
internal fun GrowingTable(
    vm: AppViewModel, head: List<GrammarCell>, rows: List<List<GrammarCell>>, fresh: Int,
    marked: Set<Int> = emptySet(), onRow: ((Int, Rect) -> Unit)? = null,
) {
    val base = L10n.pair.base.code
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().border(1.dp, line, MaterialTheme.shapes.small).clip(MaterialTheme.shapes.small)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
            for (c in head) Text(c.shown(base), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(6.dp))
        }
        rows.forEachIndexed { r, row ->
            val new = r >= rows.size - fresh
            // a row she points at is marked as what is read
            val bg = when {
                r in marked -> XpGold.copy(alpha = 0.45f)
                new -> XpGold.copy(alpha = 0.18f)
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            Row(Modifier.fillMaxWidth().background(bg).then(if (onRow != null) Modifier.onGloballyPositioned { onRow(r, it.boundsInRoot()) } else Modifier)) {
                for (c in row) {
                    val m = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 5.dp)
                    val text = c.shown(base)
                    if (c.isWords) WordText(text, si.lanisce.lani.ui.words.lookUpIn(vm, text, null, si.lanisce.lani.data.Words.READING), modifier = m, style = MaterialTheme.typography.bodyLarge, fontWeight = if (new) FontWeight.Bold else null)
                    else Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = m)
                }
            }
        }
    }
}

/**
 * A step's quick question, graded lightly: a tap choice or a one-word gap; the answer shown with its why, a miss costs
 * nothing (nothing pays, nothing counts on the rule). [onAnswered] lets the step go on.
 */
@Composable
private fun IntroAsk(
    vm: AppViewModel, ask: Exercise, reader: IntroVoice.Reader, asked: Boolean, said: Boolean, modifier: Modifier = Modifier,
    onSaid: (Rect) -> Unit = {}, onAnswered: (Grading.Verdict, String?) -> Unit,
) {
    var verdict by remember { mutableStateOf<Grading.Verdict?>(null) }
    var picked by remember { mutableStateOf<Int?>(null) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth(),
        border = if (asked) androidx.compose.foundation.BorderStroke(2.dp, XpGold) else null,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("❓ ${bi("school.quickQuestion")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (ask) {
                is Exercise.Choice -> {
                    ask.instruction?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    Text(inlineMarkdown(ask.prompt), style = MaterialTheme.typography.titleLarge)
                    ask.options.forEachIndexed { o, opt ->
                        val color = when {
                            verdict == null -> CardDefaults.cardColors()
                            o == ask.answer -> CardDefaults.cardColors(containerColor = AlpineGreen.copy(alpha = 0.2f))
                            o == picked -> CardDefaults.cardColors(containerColor = TriglavRed.copy(alpha = 0.2f))
                            else -> CardDefaults.cardColors()
                        }
                        Card(
                            onClick = {
                                if (verdict == null) {
                                    picked = o
                                    val v = if (o == ask.answer) Grading.Verdict.CORRECT else Grading.Verdict.WRONG
                                    verdict = v
                                    onAnswered(v, gapFilled(ask.prompt, ask.options.getOrNull(ask.answer)))
                                }
                            },
                            colors = color, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(opt, Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium) }
                    }
                    verdict?.let { v -> AskResult(vm, v, ask.options.getOrNull(ask.answer).orEmpty(), ask.explain, gapFilled(ask.prompt, ask.options.getOrNull(ask.answer)), reader, said, onSaid) }
                }
                is Exercise.Cloze -> {
                    var value by remember { mutableStateOf(TextFieldValue("")) }
                    ask.instruction?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    Text(inlineMarkdown(ask.text.replace(Sheets.GAP, "______")), style = MaterialTheme.typography.titleLarge)
                    NoSuggestions {
                        OutlinedTextField(
                            value = value, onValueChange = { value = it }, enabled = verdict == null, singleLine = true,
                            keyboardOptions = AnswerKeyboard, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(ask.hint ?: bi("playerScreen.typeAnswer")) },
                        )
                    }
                    if (verdict == null) {
                        DiacriticChips(value, { value = it })
                        BigButton(bi("playerScreen.check"), enabled = value.text.isNotBlank(), onClick = {
                            val v = Grading.check(value.text, ask.accept).verdict
                            verdict = v
                            onAnswered(v, gapFilled(ask.text, ask.accept.firstOrNull()))
                        })
                    }
                    verdict?.let { v -> AskResult(vm, v, ask.accept.firstOrNull().orEmpty(), ask.explain, gapFilled(ask.text, ask.accept.firstOrNull()), reader, said, onSaid) }
                }
                else -> Unit
            }
        }
    }
}

/** A sentence with its gap filled with [answer], without a translation in brackets at its end; null when there's no gap. */
private fun gapFilled(text: String, answer: String?): String? {
    if (answer == null || !Sheets.GAP.containsMatchIn(text)) return null
    return text.replace(Sheets.GAP, answer).replace(Regex("""\s*\([^()]*\)\s*$"""), "").trim()
}

@Composable
private fun AskResult(vm: AppViewModel, v: Grading.Verdict, right: String, explain: String?, sentence: String?, reader: IntroVoice.Reader, said: Boolean, onSaid: (Rect) -> Unit) {
    val (label, color) = when (v) {
        Grading.Verdict.CORRECT -> "${inTarget("playerScreen.correct")} ✅" to AlpineGreen
        Grading.Verdict.ALMOST -> "${inTarget("playerScreen.almost")} 🟡" to si.lanisce.lani.ui.theme.FlameOrange
        Grading.Verdict.WRONG -> "${bi("school.missNoMatter")} 🙂" to TriglavRed
    }
    Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    if (v != Grading.Verdict.CORRECT) Text("→ $right", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    sentence?.let { s ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).onGloballyPositioned { onSaid(it.boundsInRoot()) }.background(if (said) XpGold.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.small))
            SpeakButton(vm.speaker, s, voiceName = reader.voiceName, fallback = reader.fallback, person = reader.person)
        }
    }
    explain?.let { Markdown(it) }
}

/** The end: the rule's page as a summary card (its rule and its whole table), the book one tap away, then the worksheets. */
@Composable
private fun Summary(vm: AppViewModel, unit: CourseUnit, intro: Intro, modifier: Modifier, done: () -> Unit) {
    val base = L10n.pair.base.code
    val page = unit.grammar.firstNotNullOfOrNull { vm.grammar.page(it) }
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🧾 ${bi("school.summary")}", style = MaterialTheme.typography.titleLarge)
            if (page != null) {
                Text("${page.emoji} ${page.titleShown(L10n.pair)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Markdown(page.ruleIn(base))
                page.table?.let { GrowingTable(vm, it.head, it.rows, fresh = 0) }
                TextButton(onClick = { vm.grammar.show(page.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("📖 ${bi("school.toTheBook")}") }
            } else {
                val rows = intro.steps.flatMap { it.rows }
                if (intro.head.isNotEmpty() && rows.isNotEmpty()) GrowingTable(vm, intro.head, rows, fresh = 0)
            }
        }
        Box(Modifier.padding(top = 8.dp)) { BigButton("✓ ${bi("school.toWorksheets")}", color = AlpineGreen, onClick = done) }
    }
}
