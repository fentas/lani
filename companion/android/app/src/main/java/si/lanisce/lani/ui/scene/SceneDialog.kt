package si.lanisce.lani.ui.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.onLongClick
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.SceneTalk
import si.lanisce.lani.app.SentenceQuery
import si.lanisce.lani.app.WordQuery
import si.lanisce.lani.game.DialogReviews
import si.lanisce.lani.game.WordAnswer
import si.lanisce.lani.game.HintPiece
import si.lanisce.lani.game.HintSpeech
import si.lanisce.lani.game.HintText
import si.lanisce.lani.game.TurnHints
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.TurnMode
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.Story
import si.lanisce.lani.game.scene.StoryTonight
import si.lanisce.lani.ui.AnswerKeyboard
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.DiacriticChips
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.NoSuggestions
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.NodeMic
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.WordToken
import si.lanisce.lani.ui.words.lookUpIn
import si.lanisce.lani.ui.words.lookUpWords
import si.lanisce.lani.ui.game.BookLinks
import si.lanisce.lani.ui.game.GiftLogic
import si.lanisce.lani.ui.game.GiftReveals
import si.lanisce.lani.ui.game.GiftWords
import si.lanisce.lani.ui.game.RewardChips
import si.lanisce.lani.ui.game.giftDelayMs
import si.lanisce.lani.ui.game.giftWords
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.villagers.FriendChip
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget

/** How fast a person's line types in. */
private const val TYPE_MS = 28L

/** Someone of a dialog of several people (an arrival: Micka introduces Zala): their face beside their lines, and their voice. */
data class Speaking(val emoji: String, val voice: Spoken)

/**
 * The dialog with someone in the scene, chat-like over the lower part of the screen: their lines (Slovene
 * big and typed in, spoken by the screen; English small below), the learner's turn with 2–3 choices (each
 * with 🔊; a wrong one shakes, the person reacts to it and it says why), and at the end the result with the village's
 * reward.
 *
 * A tap on a word of a line opens its card (a long press in the choices, where a tap picks). At the learner's turn
 * the 🎤 takes the answer said out loud, besides a tap. "👁 Prevod · Translation" in the header shows or hides the
 * translations under the lines and the choices, for this dialog and every later one (the phone remembers it,
 * [si.lanisce.lani.data.DialogPrefs]); while they're hidden, a tap on a line beside its words shows that line's.
 * Every dialog of a scene, the storyteller's evening story, a keeper's talk, an arrival and a guest's dialog on a visit
 * are this panel.
 */
@Composable
fun DialogPanel(
    vm: AppViewModel,
    talk: SceneTalk,
    /** Who reads their lines: a voice and its fallback ([SceneWords.voiceOf]). */
    voice: Spoken,
    onChoose: (Int) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onKeepTalking: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    /** Whether the 🎤 listens to the learner now (the person in the scene listens too). */
    onListening: (Boolean) -> Unit = {},
    /** A story told to its end: learn its words (a pack run of those not learned yet). */
    onLearnStory: ((Story) -> Unit)? = null,
    /** The others who speak in it, by id (their lines show their face and are read in their voice); the person's are [voice]. */
    speakers: Map<String, Speaking> = emptyMap(),
    /** What the button at the end says (back to the scene, by default). */
    closeLabel: String? = null,
    /** A turn typed into its gap ([DialogRun.type]; companion/SCENES.md, "Adaptive turns"). */
    onType: (String) -> Unit = {},
    /** "✋ Izberi raje · Let me choose": the turn's choices after all ([DialogRun.letMeChoose]). */
    onLetMeChoose: () -> Unit = {},
    /** A turn said whole ([DialogRun.say]): false when what was heard wasn't clear. */
    onSay: (List<String>) -> Boolean = { false },
    /** "📖 Namig · Hint" opened at the turn ([DialogRun.hint]): its right answer counts right, not on the rule's run. */
    onHint: () -> Unit = {},
    /** An echo turn gone on from ("✓ Naprej", [DialogRun.echo]; companion/SCENES.md, "Rules not yet"). */
    onEcho: () -> Unit = {},
    /** An echo turn said out loud ([DialogRun.echo]): false when it wasn't close enough (again, or tap on). */
    onEchoSaid: (List<String>) -> Boolean = { false },
) {
    val run = talk.run
    // at a story's end, which of its words are learned: its packs as the node has them
    val storyEnded = talk.story != null && run.step == DialogRun.Step.END
    LaunchedEffect(talk.story?.story?.id, storyEnded) { if (storyEnded) talk.story?.let { vm.scenes.loadPacks(it.story) } }
    // an answer on a turn that names a grammar book page counts on the rule (and may unlock the page), picked, typed or
    // said: what it adds to the run's answers, once ([DialogRun.answersOf]); a turn of a rule not yet meets it; and a turn
    // that tests the learner's own words counts on their cards ([DialogRun.wordsOf], once a card a day)
    val count: (DialogRun?) -> Unit = { next ->
        countAnswers(vm, run, next)
        vm.dialogWords.answered(talk, run, next)
    }
    // what the dialog counted on the learner's words goes to the node at its end, or when it is left before
    val ended = run.step == DialogRun.Step.END
    LaunchedEffect(talk.key, run.seed, ended) { if (ended) vm.dialogWords.finish(talk) }
    DisposableEffect(talk.key, run.seed) {
        val played = talk // the dialog as played this time (its key and its run's seed): what finish sends
        onDispose { vm.dialogWords.finish(played) }
    }
    val choose: (Int) -> Unit = { k ->
        count(run.choose(k))
        onChoose(k)
    }
    val type: (String) -> Unit = { text ->
        count(run.type(text))
        onType(text)
    }
    val say: (List<String>) -> Boolean = { alternatives ->
        val next = run.say(alternatives)
        if (next != null) count(next)
        next != null && onSay(alternatives)
    }
    val echo: () -> Unit = {
        count(run.echo())
        onEcho()
    }
    val echoSaid: (List<String>) -> Boolean = { alternatives ->
        val next = run.echo(alternatives)
        if (next != null) count(next)
        next != null && onEchoSaid(alternatives)
    }
    val prefs = vm.dialogPrefs
    val phoneMic = remember { vm.recognizer.inApp || vm.recognizer.dialog }
    val listens = canListen(vm, phoneMic)
    val (mic, takes) = rememberAnswering(vm, talk, choose, say, echoSaid)
    LaunchedEffect(mic.listening) { onListening(mic.listening) }
    val hint = rememberHint(vm, talk, onHint)
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        // edge to edge, the window doesn't shrink for the keyboard: the panel keeps above it (a typed turn), and its host
        // lets it have the picture's room meanwhile (DialogRoom); the navigation bar's part of the keyboard counted once
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Text(talk.person.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(talk.person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(talk.happening.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (run.step != DialogRun.Step.END) TranslationsToggle(prefs.translations, prefs::showTranslations)
                CloseButton(onClose)
            }
            if (run.step == DialogRun.Step.END) {
                DialogResult(
                    talk, onClose, onKeepTalking, Modifier.weight(1f), giftWords(vm),
                    onLearnStory = onLearnStory?.takeIf { talk.story?.let { t -> vm.scenes.toLearn(t.story) } != null },
                    closeLabel = closeLabel,
                    onNotebook = { s -> vm.openNotebook(s.id) },
                    yourWords = { YourWords(vm, talk) },
                )
            } else {
                if (!prefs.wordHintSeen) WordHint(prefs::sawWordHint)
                // the turn as high as it needs (it scrolls on a small screen, kept at the field while typing), the lines
                // the rest, at least the last line said (DialogRoom)
                val scroll = remember(talk.key, run.at, run.step, run.mode) { ScrollState(0) }
                LinesOverTurn(
                    lines = { Lines(vm, talk, voice, Modifier.fillMaxSize(), speakers, prefs.translations) },
                    turn = {
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // the microphone refused, and no system dialog to fall back on: a turn to say is typed
                            val micBlocked = mic.denied && !vm.recognizer.dialog
                            when (run.step) {
                                DialogRun.Step.LISTEN -> BigButton("${bi("common.next")} ›", onClick = onNext)
                                DialogRun.Step.CHOOSE -> when {
                                    run.mode == TurnMode.ECHO -> EchoTurn(vm, run, listens && !micBlocked, mic, takes, echo, hint)
                                    run.mode == TurnMode.TAP -> TapTurn(vm, run, listens, mic, takes, choose, prefs.translations, onLetMeChoose, hint)
                                    run.mode == TurnMode.SAY && listens && !micBlocked && run.gap != null -> SayTurn(vm, run, mic, takes, onLetMeChoose, hint)
                                    (run.mode == TurnMode.TYPE || run.mode == TurnMode.SAY) && run.gap != null -> TypeTurn(vm, run, type, onLetMeChoose, hint, scroll)
                                    else -> Turn(vm, run, listens, mic, takes, choose, prefs.translations, hint)
                                }
                                DialogRun.Step.END -> Unit
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * "👁 Prevod · Translation": the translations under the lines and the choices shown (✓) or hidden, in this dialog and
 * every later one (remembered on the phone). The chip says it in the target language, to keep the header short;
 * TalkBack hears both.
 */
@Composable
internal fun TranslationsToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = on,
        onClick = { onChange(!on) },
        label = {
            Text(
                "👁 ${inTarget("reading.translation")}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clearAndSetSemantics { contentDescription = bi("reading.translation") },
            )
        },
        leadingIcon = if (on) ({ Text("✓", fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics { }) }) else null,
        modifier = Modifier.padding(end = 6.dp),
    )
}

/** Once: words can be looked up. Shown until closed, a word is looked up, or the first dialog with it ends. */
@Composable
private fun WordHint(onSeen: () -> Unit) {
    DisposableEffect(Unit) { onDispose(onSeen) }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("👆 ${bi("sceneDialog.tapWordHint")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeen) { EmojiLabel("✕", Labels.CLOSE) }
        }
    }
}

/** What a tap on a word of the dialog's [line] does: its card (and the hint about it has done its job). */
internal fun lookUp(vm: AppViewModel, line: String, en: String): (WordToken) -> Unit {
    val open = lookUpIn(vm, line, en, Words.SCENE)
    return { t ->
        vm.dialogPrefs.sawWordHint()
        open(t)
    }
}

@Composable
private fun Lines(vm: AppViewModel, talk: SceneTalk, voice: Spoken, modifier: Modifier, speakers: Map<String, Speaking> = emptyMap(), translations: Boolean = true) {
    val said = talk.run.said
    // the cards of the learner's words a turn got wrong, after the line said where it ended (by its index)
    val cards = remember(talk.run.wordCards) { talk.run.wordCards.groupBy { it.after } }
    val list = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(said.size) { if (said.isNotEmpty()) list.animateScrollToItem(said.lastIndex) }
    // their room changes (the keyboard up and the turn taking more of the panel, a why opening): the last line said stays
    val last by rememberUpdatedState(said.lastIndex)
    LaunchedEffect(list) {
        var was = -1
        snapshotFlow { list.layoutInfo.viewportSize.height }.collect { h ->
            if (was >= 0 && h != was && last >= 0) list.scrollToItem(last)
            was = h
        }
    }
    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(said, key = { i, _ -> i }) { i, s ->
            val onWord = lookUp(vm, s.sl, s.en)
            val who = s.who?.let(speakers::get)
            val translation = rememberTranslation(s, translations)
            // a long press on a line said (theirs any time, the learner's own once said): its grammar, word by word; the same
            // lambda for the line, so its gestures aren't started over as the dialog goes on
            val onLong = remember(s, talk.key) {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.sentences.open(sentenceOf(talk, s))
                }
            }
            val missed = cards[i].orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.who == null) Mine(s, onWord, translation, onLong)
                else Theirs(s, who?.emoji ?: talk.person.emoji, vm.speaker, who?.voice ?: voice, typing = i == said.lastIndex, onWord = onWord, translation = translation, onLong = onLong)
                // the learner's words the turn just passed got wrong: their cards, after it (the dialog goes on)
                for (c in missed) MissedWord(vm, c.answer)
            }
        }
    }
}

/**
 * A word of the learner's a turn tested and they got wrong ([DialogRun.wordCards]): its card in short, in the conversation
 * after the turn ("📇 Tvoja beseda · Your word: žlica — spoon", and the form the sentence wanted); a tap opens the whole
 * card, its forms too.
 */
@Composable
private fun MissedWord(vm: AppViewModel, a: WordAnswer) {
    val open = { vm.words.open(WordQuery(a.word.said, a.expected, null, Words.SCENE)) }
    val here = a.word.said.takeIf { !it.equals(a.card.word, ignoreCase = true) }
    Row(Modifier.fillMaxWidth().popIn(a, from = 0.9f), horizontalArrangement = Arrangement.Center) {
        Surface(
            onClick = open,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 320.dp).semantics(mergeDescendants = true) { role = Role.Button },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📇 ${bi("dialogWords.yourWord")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    "${a.card.word} — ${a.card.means}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (here != null) {
                    Text("${bi("dialogWords.here")}: «$here»", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

/**
 * "📇 Tvoje besede · Your words: žlica ✓, krožnik ✓" at the dialog's end: the learner's words its turns tested, each by its
 * first answer (a tap opens its card), which counted as their review today, and those that come back tomorrow (lowered
 * gently by a bridge that takes a dialog's words). Nothing when the dialog tested none of theirs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YourWords(vm: AppViewModel, talk: SceneTalk) {
    val words = talk.run.wordSummary
    if (words.isEmpty()) return
    val kinds = vm.dialogWords.outcomesOf(talk).distinctBy { it.answer.card.id }.associate { it.answer.card.id to it.kind }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📇 ${bi("dialogWords.yours")}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (a in words) {
                    val mark = if (a.right) "✓" else "✗"
                    Surface(
                        onClick = { vm.words.open(WordQuery(a.word.said, a.expected, null, Words.SCENE)) },
                        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (a.right) AlpineGreen else TriglavRed),
                        modifier = Modifier.semantics { contentDescription = "${a.card.word} — ${a.card.means}: ${if (a.right) bi("dialogWords.right") else bi("sceneDialog.wrong")}" },
                    ) {
                        Text(
                            "${a.card.word} $mark",
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = if (a.right) AlpineGreen else TriglavRed,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).heightIn(min = 24.dp),
                        )
                    }
                }
            }
            fun named(k: DialogReviews.Kind) = words.filter { kinds[it.card.id] == k }.joinToString(", ") { it.card.word }
            named(DialogReviews.Kind.REVIEW).takeIf { it.isNotEmpty() }?.let {
                Text("🔁 ${bi("dialogWords.reviewed")}: $it", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            val lowers = vm.content.dashboard?.features?.contains(si.lanisce.lani.app.DialogWordsController.FEATURE) == true
            named(DialogReviews.Kind.LOWERED).takeIf { it.isNotEmpty() && lowers }?.let {
                Text("↩️ ${bi("dialogWords.backTomorrow")}: $it", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * The line [s] of [talk] for "🔍 Slovnica stavka": where it was said, and for the learner's own right line the pages of
 * its turn's rule (a wrong one tests none of its own).
 */
private fun sentenceOf(talk: SceneTalk, s: Said): SentenceQuery {
    val turn = if (s.who == null && !s.wrong) talk.dialog.lines.firstOrNull { l -> l.choices.any { it.ok && it.sl == s.sl } } else null
    return SentenceQuery(
        s.sl, s.en, scene = talk.sceneId, sceneTitle = talk.happening.title, dialog = talk.dialog.id, person = talk.person.name,
        pages = turn?.let(TurnHints::pagesOf).orEmpty(),
    )
}

/**
 * Whether a line's translation shows ([shows]): always while translations are on; while they're off, once a tap on the
 * line beside its words opened it ([toggle]; a tap again closes it). A line without a translation has nothing to open.
 */
@Stable
internal class LineTranslation(val shows: Boolean, val toggle: (() -> Unit)?)

@Composable
internal fun rememberTranslation(s: Said, translations: Boolean): LineTranslation {
    val open = remember(s) { mutableStateOf(false) }
    val toggle = remember(open) { { open.value = !open.value } }
    val has = s.en.isNotBlank()
    return LineTranslation(has && (translations || open.value), toggle.takeIf { has && !translations })
}

/**
 * A tap on a line beside its words opens or closes its hidden translation ([LineTranslation.toggle]); a long press
 * anywhere on it opens its grammar ([onLong]: "🔍 Slovnica stavka").
 */
private fun Modifier.lineGestures(t: LineTranslation, onLong: () -> Unit): Modifier {
    val toggle = t.toggle
    return pointerInput(toggle, onLong) { detectTapGestures(onTap = toggle?.let { { _ -> it() } }, onLongPress = { onLong() }) }
}

/**
 * TalkBack: the line, its translation while it shows, "Slovnica stavka · The sentence's grammar" ([onLong], also its long
 * press), its words to look up, and "Pokaži prevod · Show the translation".
 */
private fun SemanticsPropertyReceiver.describeLine(s: Said, t: LineTranslation, onWord: (WordToken) -> Unit, onLong: () -> Unit) {
    contentDescription = if (t.shows) "${s.sl} · ${s.en}" else s.sl
    val grammar = bi("sentence.title")
    lookUpWords(s.sl, onWord, first = listOf(CustomAccessibilityAction(grammar) { onLong(); true }))
    onLongClick(label = grammar) { onLong(); true }
    t.toggle?.let { toggle -> if (!t.shows) onClick(label = bi("reading.showTranslation")) { toggle(); true } }
}

/**
 * Someone's line: Slovene big (typed in when it's the newest; only what's typed can be tapped), 🔊, English small
 * below (unless translations are hidden: a tap on the line beside its words shows it).
 */
@Composable
internal fun Theirs(s: Said, emoji: String, speaker: Speaker, voice: Spoken, typing: Boolean, onWord: (WordToken) -> Unit, translation: LineTranslation, onLong: () -> Unit) {
    var shown by remember(s) { mutableIntStateOf(if (typing) 0 else s.sl.length) }
    LaunchedEffect(s) {
        while (shown < s.sl.length) {
            delay(TYPE_MS)
            shown++
        }
    }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.popIn(s, from = 0.85f, origin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f))) {
        Text(emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics { })
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Row(Modifier.lineGestures(translation, onLong).padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f, fill = false).clearAndSetSemantics { describeLine(s, translation, onWord, onLong) },
                ) {
                    // The whole line is laid out from the start (the rest invisible), so the bubble doesn't jump as it types.
                    WordText(
                        s.sl,
                        onWord,
                        shown = shown,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        onElse = translation.toggle,
                        onLongPress = onLong,
                    )
                    if (translation.shows) {
                        Text(s.en, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f))
                    }
                }
                SpeakButton(speaker, s.sl, Modifier.padding(start = 4.dp), voiceName = voice.voice, fallback = voice.fallback, person = voice.person)
            }
        }
        Spacer(Modifier.width(32.dp))
    }
}

/**
 * The learner's own line, right; a wrong choice the person reacted to is marked as the choice is (red, ✗). Its
 * translation as someone's line has it ([translation]).
 */
@Composable
internal fun Mine(s: Said, onWord: (WordToken) -> Unit, translation: LineTranslation, onLong: () -> Unit) {
    Row(Modifier.fillMaxWidth().popIn(s, from = 0.85f, origin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)), horizontalArrangement = Arrangement.End) {
        Spacer(Modifier.width(48.dp))
        val ink = if (s.wrong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
        Surface(
            color = if (s.wrong) TriglavRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            border = if (s.wrong) BorderStroke(1.dp, TriglavRed) else null,
        ) {
            Row(Modifier.lineGestures(translation, onLong).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.clearAndSetSemantics { describeLine(s, translation, onWord, onLong) }) {
                    WordText(s.sl, onWord, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink, onElse = translation.toggle, onLongPress = onLong)
                    if (translation.shows) Text(s.en, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = ink.copy(alpha = 0.75f))
                }
                if (s.wrong) {
                    Text("✗", color = TriglavRed, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics { contentDescription = bi("sceneDialog.wrong") })
                }
            }
        }
    }
}

/** One turn's takes at the 🎤: what was heard, and what the learner is told. */
@Stable
private class Takes {
    var heard by mutableStateOf<String?>(null)
    var note by mutableStateOf<String?>(null)

    /** What the recognizer heard, matched against the turn's choices: one said counts as tapped, a wrong one included. */
    fun take(vm: AppViewModel, run: DialogRun, alternatives: List<String>, phone: Boolean, onChoose: (Int) -> Unit) {
        when (val r = ChoiceMatch.match(alternatives, run.choices.map { it.sl })) {
            is ChoiceMatch.Result.Said -> {
                if (phone) vm.recognizer.confirm() // Slovene came back: the phone's recognizer takes it
                heard = r.heard
                note = if (r.index in run.tried) bi("sceneDialog.triedThatOne") else null
                onChoose(r.index)
            }
            is ChoiceMatch.Result.Unsure -> failed(
                if (r.heard.isBlank()) NodeMic.NOT_HEARD else "🎤 ${bi("sceneDialog.heardWhichOne", "heard" to r.heard)}",
            )
        }
    }

    fun failed(message: String) {
        heard = null
        note = message
    }
}

/**
 * The 🎤 of the dialog, kept over its turns (a refused microphone stays refused), and each turn's [Takes]. A take
 * that comes back once its turn is over (the node took a while, the learner tapped meanwhile) is dropped.
 */
@Composable
private fun rememberAnswering(
    vm: AppViewModel, talk: SceneTalk, onChoose: (Int) -> Unit, onSay: (List<String>) -> Boolean, onEcho: (List<String>) -> Boolean,
): Pair<DialogMic, Takes> {
    val run = talk.run
    val takes = remember(talk.key, run.at) { Takes() }
    var startedAt by remember { mutableIntStateOf(-1) }
    val current = { startedAt == run.at && run.step == DialogRun.Step.CHOOSE }
    val mic = rememberDialogMic(
        vm,
        expected = { ChoiceMatch.prompt(run.choices.map { it.sl }) },
        onHeard = { alternatives, phone ->
            when {
                !current() -> Unit
                // a turn said whole: the sentence heard, graded; unclear, it is said again
                run.mode == TurnMode.SAY -> if (onSay(alternatives)) {
                    if (phone) vm.recognizer.confirm()
                    takes.heard = alternatives.firstOrNull()
                    takes.note = null
                } else {
                    takes.failed("🎤 ${bi("adaptive.heardSayAgain", "heard" to alternatives.firstOrNull().orEmpty())}")
                }
                // an echo said: close enough goes on; else again, or tap on (never a mistake)
                run.mode == TurnMode.ECHO -> if (onEcho(alternatives)) {
                    if (phone) vm.recognizer.confirm()
                    takes.heard = alternatives.firstOrNull()
                    takes.note = null
                } else {
                    takes.failed("🎤 ${bi("sceneDialog.echoAgain", "heard" to alternatives.firstOrNull().orEmpty())}")
                }
                else -> takes.take(vm, run, alternatives, phone, onChoose)
            }
        },
        onFailed = { message -> if (current()) takes.failed(message) },
        onStart = { startedAt = run.at },
    )
    return mic to takes
}

/** "✋ Izberi raje · Let me choose": the turn's choices, never a wall. */
@Composable
private fun LetMeChoose(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text("✋ ${bi("adaptive.letMeChoose")}") }
}

/** What the turn means, for typing or saying it: "Hočeš reči · You want to say: Ten eggs, please." */
@Composable
private fun Intent(run: DialogRun) {
    run.intent?.let {
        Text("${bi("adaptive.youWantToSay")}: $it", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A turn typed ([TurnMode.TYPE]: the learner has its rule secure): what they want to say, the sentence with the gap, the
 * word typed in with the letter chips (č, š, ž), graded like a cloze; "✋ Let me choose" brings back the choices, and so
 * does a wrong answer ([DialogRun.type]). While the field has the focus, the turn's [scroll] keeps the field, the chips and
 * "Check" in view as the keyboard comes up ([KeepAtEnd]; they end the turn).
 */
@Composable
private fun TypeTurn(vm: AppViewModel, run: DialogRun, onType: (String) -> Unit, onLetMeChoose: () -> Unit, hint: TurnHelp?, scroll: ScrollState) {
    val g = run.gap ?: return
    var value by remember(run.at) { mutableStateOf(TextFieldValue("")) }
    var focused by remember(run.at) { mutableStateOf(false) }
    KeepAtEnd(scroll, focused)
    val submit = { if (value.text.isNotBlank()) onType(value.text) }
    TurnTitle("✍️ ${bi("adaptive.typeMissingWord")}", hint)
    Intent(run)
    hint?.let { HintCard(vm, it) }
    Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
        Text(g.shown, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
    NoSuggestions {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            singleLine = true,
            placeholder = { Text(bi("playerScreen.typeAnswer")) },
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = AnswerKeyboard.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
    }
    DiacriticChips(value, { value = it })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LetMeChoose(onLetMeChoose)
        BigButton(bi("playerScreen.check"), onClick = submit, enabled = value.text.isNotBlank(), modifier = Modifier.weight(1f))
    }
}

/**
 * A turn said whole ([TurnMode.SAY]: the learner has its rule mastered): what they want to say, the 🎤, graded like a
 * speak exercise ([DialogRun.say]); what wasn't clear is said again, a wrong choice said brings back the choices, and
 * "✋ Let me choose" does too.
 */
@Composable
private fun SayTurn(vm: AppViewModel, run: DialogRun, mic: DialogMic, takes: Takes, onLetMeChoose: () -> Unit, hint: TurnHelp?) {
    TurnTitle("🎤 ${bi("adaptive.sayWholeSentence")}", hint)
    Intent(run)
    hint?.let { HintCard(vm, it) }
    if (mic.asking) MicRationale(mic) else MicRow(mic, big = true)
    takes.heard?.let { Text("🎤 «$it»", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    takes.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    LetMeChoose(onLetMeChoose)
}

/**
 * An echo turn ([TurnMode.ECHO]; companion/SCENES.md, "Rules not yet"): its form is of a rule not introduced to the
 * learner yet, and nothing else is left to choose. "🔁 Poslušaj in ponovi · Listen and repeat", the right line with 🔊
 * and its translation (always: it is what they say), the 🎤 when something listens ([listens]: said close enough, it goes
 * on; else again, never a mistake), and "✓ Naprej · Next" ([onNext]). The hint chip says the rule comes later.
 */
@Composable
private fun EchoTurn(vm: AppViewModel, run: DialogRun, listens: Boolean, mic: DialogMic, takes: Takes, onNext: () -> Unit, hint: TurnHelp?) {
    val line = run.choices.firstOrNull { it.ok } ?: return
    TurnTitle("🔁 ${bi("sceneDialog.echo")}", hint)
    hint?.let { HintCard(vm, it) }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().popIn(run.at, from = 0.9f),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val ink = MaterialTheme.colorScheme.onPrimaryContainer
            Column(Modifier.weight(1f)) {
                WordText(line.sl, lookUp(vm, line.sl, line.en), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink)
                if (line.en.isNotBlank()) Text(line.en, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = ink.copy(alpha = 0.75f))
            }
            SpeakButton(vm.speaker, line.sl, Modifier.padding(start = 6.dp))
        }
    }
    if (listens) {
        if (mic.asking) MicRationale(mic) else MicRow(mic, big = false)
        takes.heard?.let { Text("🎤 «$it»", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        takes.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    BigButton("✓ ${bi("common.next")}", onClick = onNext)
}

/**
 * The learner's turn: the choices, each with 🔊 (wrong ones tried are crossed out, the last one shakes and says why; when
 * the person reacted to it in the lines above, the why is one tap away), their translations unless hidden
 * ([translations]), and the 🎤 when some recognizer can listen ([listens]): the choice said counts as tapped, a wrong
 * one included.
 */
@Composable
private fun Turn(vm: AppViewModel, run: DialogRun, listens: Boolean, mic: DialogMic, takes: Takes, onChoose: (Int) -> Unit, translations: Boolean = true, hint: TurnHelp? = null) {
    Text(bi("sceneDialog.answer"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    val marks = remember(run.choices) { ChoiceDiff.marks(run.choices.map { it.sl }) }
    run.choices.forEachIndexed { k, c ->
        ChoiceRow(
            c,
            marks = marks.getOrElse(k) { emptyList() },
            wrong = k in run.tried,
            shakeKey = run.mistakes.takeIf { k == run.wrongPick },
            speaker = vm.speaker,
            translation = translations,
            onWord = lookUp(vm, c.sl, c.en),
        ) { onChoose(k) }
    }
    Why(vm, run)
    MicAndHint(vm, listens, mic, takes, hint)
}

/**
 * The 🎤 of a turn (when something can listen) with "📖 Namig · Hint" beside it (when the turn's rule has a page), what the
 * 🎤 heard, and the hint's card once opened.
 */
@Composable
private fun MicAndHint(vm: AppViewModel, listens: Boolean, mic: DialogMic, takes: Takes, hint: TurnHelp?) {
    val chip: (@Composable () -> Unit)? = hint?.let { h -> { HintChip(h) } }
    if (listens && !mic.asking) MicRow(mic, big = false, trailing = chip)
    else {
        if (listens) MicRationale(mic)
        chip?.let { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { it() } }
    }
    if (listens) {
        takes.heard?.let { Text("🎤 «$it»", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        takes.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    hint?.let { HintCard(vm, it) }
}

/**
 * "📖 Namig · Hint" at a turn: what it says ([text]; null in a turn of rules not yet alone), the rules not introduced yet
 * the turn says ([later]: "🌱 Na vrsto pride kasneje · You'll learn this later: …"), what its 🔊 says ([speech]: empty,
 * no 🔊), whether its card is open, and what a tap on the chip does.
 */
@Stable
private class TurnHelp(val text: HintText?, val later: String?, val speech: List<HintPiece>, val open: Boolean, val toggle: () -> Unit)

/**
 * The hint of the turn now (companion/SCENES.md, "The hint"): null when its rule has no page in the book. Opening it the
 * first time tells the run ([onHint]: its right answer then doesn't lengthen the rule's run); it stays open for the turn.
 * A rule not introduced yet ([DialogRun.later]) gets no hint of its own, only the line that it comes later
 * (companion/SCENES.md, "Rules not yet").
 */
@Composable
private fun rememberHint(vm: AppViewModel, talk: SceneTalk, onHint: () -> Unit): TurnHelp? {
    val run = talk.run
    val pages = vm.grammar.pages
    val later = run.later
    val (text, speech) = remember(talk.key, run.at, run.step, pages) {
        val h = run.turn?.let { vm.grammar.hint(it, hidden = later) }
        val t = h?.let { TurnHints.text(it, vm.grammar::page) }?.takeIf { !it.isEmpty }
        // the 🔊 says what the card says: the hint's rule, and the rules that come later
        t to HintSpeech.of(h?.takeIf { t != null }, vm.grammar.laterTitles(later))
    }
    val soon = remember(talk.key, run.at, run.step, pages) { vm.grammar.later(later) }
    if (text == null && soon == null) return null
    var open by remember(talk.key, run.at) { mutableStateOf(false) }
    return TurnHelp(text, soon, speech, open) {
        open = !open
        if (open) onHint()
    }
}

/** "📖 Namig · Hint": the chip beside the 🎤 (or a turn's title) that opens the hint's card. */
@Composable
private fun HintChip(h: TurnHelp) {
    val label = bi("hint.chip")
    FilterChip(
        selected = h.open,
        onClick = h.toggle,
        label = { Text("📖 $label", style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics { contentDescription = label }) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/**
 * The hint's card: the question the missing word answers and its case, what decides the form in this sentence, the rule,
 * a model with other words, the page table's rows about it, and "📖 V knjigo" to its pages (the title left out where it
 * would give the answer away). Never the answer ([TurnHints.fair]). In its top-right corner the 🔊 says it in short
 * ([ReadHint]).
 */
@Composable
private fun HintCard(vm: AppViewModel, h: TurnHelp) {
    if (!h.open) return
    val t = h.text
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().popIn(t ?: h.later, from = 0.9f)) {
        Box(Modifier.fillMaxWidth()) {
            // what comes first clears the 🔊 in the corner: a line, else the models, the rows, the links, what comes later
            val clear = if (h.speech.isNotEmpty()) Modifier.padding(end = 40.dp) else Modifier
            val lead = when {
                t == null -> Lead.LATER
                t.lines.isNotEmpty() -> Lead.LINE
                t.models != null -> Lead.MODELS
                t.rows.isNotEmpty() -> Lead.ROW
                else -> Lead.LINKS
            }
            fun at(l: Lead, first: Boolean = true) = if (l == lead && first) clear else Modifier
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val ink = MaterialTheme.colorScheme.onSecondaryContainer
                if (t != null) {
                    t.lines.forEachIndexed { i, line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium, fontWeight = if (i == 0) FontWeight.SemiBold else null, color = ink, modifier = at(Lead.LINE, i == 0))
                    }
                    t.models?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = ink, modifier = at(Lead.MODELS)) }
                    t.rows.forEachIndexed { i, row -> Text("▸ $row", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f), modifier = at(Lead.ROW, i == 0)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = at(Lead.LINKS)) {
                        for ((id, title) in t.links) AssistChip(
                            onClick = { vm.grammar.show(id) },
                            label = { Text("📖 ${bi("grammar.toBook")}${title?.let { ": $it" }.orEmpty()}", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                // a rule not introduced yet: only that it comes later, no page to open (that would introduce it)
                h.later?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = ink.copy(alpha = 0.85f), modifier = at(Lead.LATER)) }
            }
            if (h.speech.isNotEmpty()) ReadHint(vm, h.speech, Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp))
        }
    }
}

/** Which part of the hint's card comes first (it keeps clear of the 🔊). */
private enum class Lead { LINE, MODELS, ROW, LINKS, LATER }

/**
 * The hint's 🔊 (TalkBack: "Preberi namig · Read the hint"): a tap says it in short ([HintVoice], [HintSpeech]: what
 * applies here, the rule, the question to ask, a model), a tap again stops it; it spins while its pieces are got ready
 * and shows ⏹ while it plays. Opening the card gets the phone's voice ready; closing it, or the turn going on, stops it.
 */
@Composable
private fun ReadHint(vm: AppViewModel, speech: List<HintPiece>, modifier: Modifier) {
    val voice = vm.hintVoice
    val mine = voice.current == speech
    LaunchedEffect(speech) { voice.warm(speech) }
    DisposableEffect(speech) { onDispose { if (voice.current == speech) voice.stop() } }
    val label = bi("hint.read")
    Surface(
        shape = CircleShape,
        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        contentColor = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = if (mine) bi("components.stop") else label) { voice.toggle(speech) }
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (mine && voice.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (mine) "⏹" else "🔊", style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

/** A typed or said turn's title ("✍️ Napiši manjkajočo besedo"), with "📖 Namig · Hint" beside it. */
@Composable
private fun TurnTitle(title: String, hint: TurnHelp?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        hint?.let { HintChip(it) }
    }
}

/**
 * A tap turn (companion/SCENES.md, "Tap turns"): "👆 Poišči v prizoru · Find it in the scene", the picture above it to tap
 * (the place, the thing or the person a choice stands for: that choice is picked); after a wrong place the person's
 * reaction is in the lines and its why one tap away, as after a wrong pick. The choices wait behind "✋ Izberi raje · Let
 * me choose" ([onLetMeChoose]), and TalkBack always has them: it reads the choices, not the picture.
 */
@Composable
private fun TapTurn(
    vm: AppViewModel, run: DialogRun, listens: Boolean, mic: DialogMic, takes: Takes, onChoose: (Int) -> Unit, translations: Boolean,
    onLetMeChoose: () -> Unit, hint: TurnHelp?,
) {
    if (rememberTalkBack()) { Turn(vm, run, listens, mic, takes, onChoose, translations, hint); return }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().popIn(run.at, from = 0.9f),
    ) {
        Text(
            "👆 ${bi("sceneDialog.findInScene")}",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
    Why(vm, run)
    MicAndHint(vm, listens, mic, takes, hint)
    LetMeChoose(onLetMeChoose)
}

/** Whether TalkBack (touch exploration) is on now: a tap turn then shows its choices, which it can read. */
@Composable
internal fun rememberTalkBack(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val am = remember(context) { context.getSystemService(android.view.accessibility.AccessibilityManager::class.java) }
    var on by remember(am) { mutableStateOf(am?.isTouchExplorationEnabled == true) }
    DisposableEffect(am) {
        val listener = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        am?.addTouchExplorationStateChangeListener(listener)
        onDispose { am?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * The why of the wrong answer just given: after a reaction one tap away ("💡 Zakaj? · Why?": first what the person made of
 * it), else at once, with its page of the grammar book ("📖 V knjigo").
 */
@Composable
private fun Why(vm: AppViewModel, run: DialogRun) {
    run.why?.let { why ->
        // After a reaction the why is one tap away: first what the person made of it, then, if the learner wants, why.
        var open by remember(run.at, run.mistakes) { mutableStateOf(run.reaction == null) }
        if (open) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().popIn(why, from = 0.9f)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("💡 $why", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    // the rule it breaks, in the grammar book: "📖 V knjigo"
                    BookLinks(vm, listOfNotNull(run.rule))
                }
            }
        } else {
            TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("💡 ${bi("sceneDialog.why")}") }
        }
    }
}

/**
 * What an answer counts on the grammar book's rules, picked, typed, said, tapped in the picture or echoed: what [next]
 * (the run after it) adds to [run]'s answers, once ([DialogRun.answersOf]), a page it names unlocking; and the rules not
 * introduced yet the turn passed said without asking for them, met once more ([DialogRun.meetingsOf]).
 */
internal fun countAnswers(vm: AppViewModel, run: DialogRun, next: DialogRun?) {
    for (a in run.answersOf(next)) {
        vm.grammar.answered(a.page, if (a.right) Verdict.CORRECT else Verdict.WRONG, a.said.takeIf { a.right }, a.said, a.correct, announce = true, hinted = a.hinted)
    }
    run.meetingsOf(next).takeIf { it.isNotEmpty() }?.let(vm.grammar::metInDialog)
}

/** The 🎤 (⏹ while listening, pulsing with the voice) and what it's doing. [big]: the only way to answer; [trailing]: beside it (the hint). */
@Composable
internal fun MicRow(mic: DialogMic, big: Boolean, trailing: (@Composable () -> Unit)? = null) {
    val scale by animateFloatAsState(if (mic.listening) 1f + mic.level * 0.25f else 1f, label = "mic")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledIconButton(
            onClick = mic::toggle,
            enabled = !mic.thinking,
            modifier = Modifier.size(if (big) 64.dp else 52.dp).scale(scale),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (mic.listening) TriglavRed else MaterialTheme.colorScheme.primary),
        ) {
            EmojiLabel(if (mic.listening) "⏹" else "🎤", if (mic.listening) Labels.STOP else Labels.SPEAK, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    mic.thinking -> "⏳ ${bi("speakExercise.recognizing")}"
                    mic.listening -> bi("speakExercise.listeningTapStop")
                    big -> bi("sceneDialog.tapMicSay")
                    else -> bi("sceneDialog.orSayIt")
                },
                style = MaterialTheme.typography.labelLarge,
            )
            if (mic.listening && mic.partial.isNotBlank()) {
                Text(mic.partial, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            }
        }
        trailing?.invoke()
    }
}

/** Before the system asks for the microphone: why, and where the voice goes (the node, or the phone's recognizer). */
@Composable
internal fun MicRationale(mic: DialogMic) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎤 ${bi("common.microphone")}", style = MaterialTheme.typography.titleSmall)
            Text(
                if (mic.server) bi("speakExercise.hearSpeakLaniNeeds") else bi("talkInput.hearLaniNeedsMicrophone"),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = mic::notNow, modifier = Modifier.weight(1f)) { Text(bi("common.notNow")) }
                FilledTonalButton(onClick = mic::allow, modifier = Modifier.weight(1f)) { Text(bi("common.allow")) }
            }
        }
    }
}

/**
 * One choice: a tap picks it, a long press on a word looks the word up, 🔊 says it; its translation under it unless
 * translations are hidden ([translation]).
 */
@Composable
private fun ChoiceRow(c: DialogChoice, marks: List<WordToken>, wrong: Boolean, shakeKey: Int?, speaker: Speaker, translation: Boolean, onWord: (WordToken) -> Unit, onClick: () -> Unit) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey == null) return@LaunchedEffect
        for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(45))
    }
    val border = if (wrong) TriglavRed else MaterialTheme.colorScheme.outline
    Row(Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value * density }, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onClick,
            enabled = !wrong,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(if (wrong) 2.dp else 1.dp, border),
            color = if (wrong) TriglavRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // where the choices differ in a word or two, those words stand out bold, the rest is plain
                    WordText(
                        c.sl, onWord, longPress = true, marks = marks,
                        style = MaterialTheme.typography.titleMedium, fontWeight = if (marks.isEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (translation && c.en.isNotBlank()) Text(c.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (wrong) Text("✗", color = TriglavRed, fontWeight = FontWeight.Black, modifier = Modifier.clearAndSetSemantics { contentDescription = bi("sceneDialog.wrong") })
            }
        }
        SpeakButton(speaker, c.sl, Modifier.padding(start = 6.dp))
    }
}

/** The end: how it went, what the village got, and the way on to the tutor's role-play. */
@Composable
private fun DialogResult(
    talk: SceneTalk,
    onClose: () -> Unit,
    onKeepTalking: ((String) -> Unit)?,
    modifier: Modifier,
    words: GiftWords,
    onLearnStory: ((Story) -> Unit)? = null,
    closeLabel: String? = null,
    /** A story told: its page in the story notebook. */
    onNotebook: ((Story) -> Unit)? = null,
    /** "📇 Tvoje besede · Your words": the learner's words the dialog tested ([YourWords]). */
    yourWords: @Composable () -> Unit = {},
) {
    val m = talk.run.mistakes
    val paid = talk.paid
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("🎉", style = MaterialTheme.typography.displayMedium, modifier = Modifier.popIn(talk.key).clearAndSetSemantics { })
        Text(
            if (m == 0) bi("sceneDialog.excellentNoMistakes") else bi("sceneDialog.wellDoneTalkOver"),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        // a project's step is done however it went (a wrong answer was answered again in it): no smaller reward
        if (m > 0 && talk.step == null) {
            Text(
                "${bi("sceneDialog.mistakes", "m" to m)}: ${bi("sceneDialog.smallerReward")}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
        talk.step?.let { StepEnd(it) }
        yourWords()
        talk.story?.let { StoryEnd(it, onLearnStory, onNotebook) }
        when {
            paid == null || paid.due.isEmpty() -> Unit
            paid.again -> Text(
                "✓ ${bi("sceneDialog.todaysRewardAlreadyVillage")}",
                style = MaterialTheme.typography.bodyMedium, color = AlpineGreen, textAlign = TextAlign.Center,
            )
            else -> Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🏡 ${bi("common.forYourVillage")}", style = MaterialTheme.typography.titleSmall)
                    if (paid.paid.isNotEmpty()) RewardChips(paid.paid, startDelayMs = 300)
                    if (paid.full) {
                        Text(
                            "📦 ${bi("sceneDialog.storesFull")}: ${bi("sceneDialog.buildOrUpgrade")}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        )
                    }
                    Text(bi("sceneDialog.thank", "personEmoji" to talk.person.emoji, "personName" to talk.person.name), style = MaterialTheme.typography.bodyMedium)
                    si.lanisce.lani.ui.game.helpText(paid.help)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        talk.friend?.let { FriendChip(it, Modifier.fillMaxWidth()) }
        // a friendship's gift (the level it reached): a present to open, after the chips
        GiftReveals(remember(talk.friend) { GiftLogic.of(talk.friend) }, words, startDelayMs = giftDelayMs(paid?.paid?.size ?: 0, 300))
        talk.dialog.talk?.let { scenario ->
            if (onKeepTalking != null) {
                FilledTonalButton(onClick = { onKeepTalking(scenario) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
                    Text("🗣️ ${bi("sceneDialog.keepTalking")}", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
        }
        BigButton(closeLabel ?: bi("common.backScene"), onClick = onClose)
        Spacer(Modifier.widthIn(min = 1.dp))
    }
}

/**
 * The end of a village project's step played as its scene (companion/SCENES.md "Project steps"): the project and how far it
 * is now ("🌲 Mlaj 2/5"), the step's line for the chronicle ("Luka in fantje so v gozdu izbrali visoko smreko."), and the
 * project finished when it was the last; or, when the step couldn't be done after all (the stores emptied meanwhile), why.
 */
@Composable
private fun StepEnd(st: si.lanisce.lani.app.StepTalk) {
    val spec = remember(st.project) { si.lanisce.lani.game.Projects.spec(st.project) } ?: return
    val r = st.result
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val done = if (r?.won == true) st.index + 1 else st.index
            Text(
                "${spec.emoji} ${spec.short} $done/${spec.steps.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            when {
                r == null -> Unit
                r.won -> {
                    val line = spec.steps.getOrNull(st.index)?.lineText
                    line?.let {
                        Text("📜 ${it.target}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        val base = it.base
                        if (base.isNotBlank() && base != it.target) Text(base, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    if (done >= spec.steps.size) Text("✅ ${spec.done}", style = MaterialTheme.typography.bodyMedium, color = AlpineGreen, textAlign = TextAlign.Center)
                    else Text("🌙 ${bi("projects.workedToday")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                else -> Text("🔒 ${r.message}", style = MaterialTheme.typography.bodyMedium, color = TriglavRed, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * The end of an evening's story: which one it was and at which level, "to be continued tomorrow evening" when a chapter
 * of a longer legend is still to come, that it is written down in the story notebook now ("📓 Odpri zvezek · Open the
 * notebook" at its page: [onNotebook]), and the words it teaches, with a button to learn those not learned yet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryEnd(t: StoryTonight, onLearn: ((Story) -> Unit)?, onNotebook: ((Story) -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${t.story.emoji} ${t.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(bi("sceneDialog.storyTold", "level" to t.level), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (!t.last) Text("➡️ ${bi("sceneDialog.storyContinues")}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            // what was told tonight is written down in the story notebook now (ui/notebook)
            Text("📓 ${bi("sceneDialog.storyInBook")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (onNotebook != null) OutlinedButton(onClick = { onNotebook(t.story) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("📓 ${bi("notebook.open")}", style = MaterialTheme.typography.labelLarge)
            }
            val words = t.story.words.filter { it.sl.isNotBlank() }
            if (words.isNotEmpty()) {
                Text("📚 ${bi("sceneDialog.storyWords")}", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (w in words) Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(50)) {
                        Text(
                            listOfNotNull(w.emoji, w.sl).joinToString(" ") + (w.en.takeIf { it.isNotBlank() && it != w.sl }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                if (onLearn != null) {
                    OutlinedButton(onClick = { onLearn(t.story) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("📚 ${bi("sceneDialog.learnStoryWords")}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
