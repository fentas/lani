package si.lanisce.lani.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.ChallengeOrigin
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.Challenge
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.ProjectOption
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestQueue
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.Confetti
import si.lanisce.lani.ui.ExerciseRun
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.ui.stage.Cast
import si.lanisce.lani.ui.stage.IntroInfo
import si.lanisce.lani.ui.stage.IntroSheet
import si.lanisce.lani.ui.stage.Intros
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.StageFinale
import si.lanisce.lani.ui.stage.StagePerson
import si.lanisce.lani.ui.stage.StageState
import si.lanisce.lani.ui.stage.rememberStage
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.words.lookUpIn

/**
 * A village challenge: the villager's intro → the (timed) run on the stage → result with rewards. A run about a text (the
 * day's letter: [Challenge.text]) reads it first, then asks with the text hidden ("Read first, then answer", GAME.md).
 */
@Composable
fun ChallengeScreen(vm: AppViewModel, origin: ChallengeOrigin, c: Challenge) {
    var playing by remember(c) { mutableStateOf(vm.again) }
    var reward by remember(c) { mutableStateOf<Reward?>(null) }
    var started by remember(c) { mutableLongStateOf(ScreenClock.app.now()) }
    // its text read first: then its questions ([asking]), the text shown again [looks] times while one was open
    val text = c.text
    var asking by remember(c) { mutableStateOf(false) }
    var looks by remember(c) { mutableIntStateOf(0) }
    val level = remember(c) { vm.levelIn(Cultures.current.language.code) }
    // What the run is (fixed when it opens: the event or quest may be resolved by the time it ends).
    val intro = remember(c) { ChallengeRun.of(vm.game.state, origin, vm.villagers.people(vm.game.state), teller = vm.treasure.teller()?.id) }
    val stage = rememberStage(vm, intro.run)
    // the grammar page of each exercise's rule: its own (the tent challenge's questions name theirs), else its card's
    val rules = remember(c, vm.grammar.pages) {
        val pool = vm.content.dashboard?.pool.orEmpty()
        c.exercises.indices.map { i ->
            c.exercises[i].grammar ?: c.cardIds.getOrNull(i)?.let { id -> pool.firstOrNull { it.id == id } }?.let { Grammar.forCard(it, vm.grammar.pages)?.id }
        }
    }
    // the pages the run is about as a whole (a request's): met when it starts, as a module's are
    LaunchedEffect(playing) { if (playing) vm.grammar.meet(c.pages) }
    val done = reward
    when {
        done != null -> ChallengeResultView(vm, origin, c, done, stage)
        !playing -> {
            // a run against the clock gets its sounds onto the phone while its intro is read (the run waits for the rest)
            if (c.timeLimitSeconds != null) LaunchedEffect(c) {
                vm.speaker.preload(c.exercises.mapNotNull { it.heard }) { t -> si.lanisce.lani.ui.heardVoice(stage, vm.speaker, t) }
            }
            val info = remember(c) { intro.info(c, stage.who, Cast.trainingLeft(vm.game.state, stage.who.id, LocalDate.now())) }
            IntroSheet(
                info, vm.speaker, lookUp = { l, e -> lookUpIn(vm, l, e, Words.VILLAGER) }, onStart = { started = System.currentTimeMillis(); playing = true },
                onLater = vm::leaveRun,
                extra = {
                    IntroBookLinks(vm, c.pages + rules.filterNotNull())
                    // tried too often below the mark, and no drill for it: the rules it missed, one tap away
                    intro.quest?.takeIf { q -> vm.game.state?.let { QuestQueue.hard(q, it.quests) } == true }?.let { q ->
                        OutlinedButton(onClick = { vm.practiseHard(q.id) }, modifier = Modifier.fillMaxWidth()) { Text(practiseHardLabel()) }
                    }
                },
            )
        }
        text != null && !asking -> ReadFirstRun(
            vm, c.title, text, stage, rule = peekRuleText(level, pays = true), onStart = { asking = true }, onExit = { playing = false },
        )
        else -> ExerciseRun(
            // The title already starts with its emoji. An event warns that leaving ends the fight.
            vm, if (origin == ChallengeOrigin.Event) "${c.title}\n${Intros.LEAVE_WARNING}" else c.title, c.exercises, moduleId = null,
            timeLimitSeconds = c.timeLimitSeconds,
            fast = c.timeLimitSeconds != null,
            onExit = { playing = false; asking = false },
            stage = stage,
            source = { i -> ExerciseSource.challenge(origin, c, i) },
            rules = rules,
            text = text,
            onLook = { looks++ },
        ) { outs ->
            val verdicts = outs.map { it.paid } // a hinted right answer pays like an almost
            reward = vm.game.finishChallenge(origin, c, verdicts, minutesSince(started), looks, level)
                ?: Reward(emptyMap(), correct = correctCount(verdicts), total = c.exercises.size, answered = verdicts.size, almost = verdicts.count { it == Verdict.ALMOST })
        }
    }
}

private fun backdrop(origin: ChallengeOrigin, won: Boolean? = null): Brush = Brush.verticalGradient(
    when {
        won == true -> listOf(Color(0xFF0C4A35), Color(0xFF16A06E))
        won == false -> listOf(Color(0xFF2A0B12), Color(0xFF6B1426))
        origin == ChallengeOrigin.Event -> listOf(Color(0xFF1A0A12), Color(0xFF5A1422))
        else -> listOf(SloBlueDeep, Color(0xFF1F3F72))
    },
)

@Composable
private fun Bobbing(emoji: String) {
    val y = rememberInfiniteTransition(label = "bob").animateFloat(-6f, 6f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "y").value
    Text(emoji, style = MaterialTheme.typography.displayLarge, modifier = Modifier.offset(y = y.dp))
}

/**
 * What a challenge is, for its intro and its stage: a quest, the event, gathering, or a village task (a project step, a
 * festival, the day's surprise: [task] builds its intro).
 */
private class ChallengeRun(
    val run: Run, val quest: Quest?, private val event: EventKind?,
    /** Why the request waits in "Later", when it does (its intro says so). */
    private val why: QuestQueue.Why? = null,
    private val task: ((Challenge, StagePerson) -> IntroInfo)? = null,
) {
    fun info(c: Challenge, who: StagePerson, bond: Int): IntroInfo = when {
        task != null -> task.invoke(c, who)
        quest != null -> Intros.quest(quest, c, who, why)
        event != null -> Intros.event(event, c, who, bond)
        else -> Intros.gather((run as? Run.Gather)?.res ?: c.skills.firstOrNull() ?: Res.FOOD, c, who, bond)
    }

    companion object {
        /** [people]: the cast and the village's own people, to find who leads a task; [teller]: the storyteller (the treasure map's). */
        fun of(s: GameState?, origin: ChallengeOrigin, people: List<Villager>, teller: String? = null): ChallengeRun {
            val today = LocalDate.now()
            /** Villager [id] on the stage (their request's run), else today's companion. */
            fun lead(id: String?): Run = id?.let { i -> people.firstOrNull { it.id == i } }?.let { Run.Quest(it.name, it.emoji) } ?: Run.Review
            return when (origin) {
                is ChallengeOrigin.Quest -> s?.quests?.firstOrNull { it.id == origin.questId }
                    ?.let { q -> ChallengeRun(Run.Quest(q.giver, q.emoji), q, null, why = Residents.queue(s, people, today).why(q.id)) }
                    ?: ChallengeRun(Run.Review, null, null)
                ChallengeOrigin.Event -> s?.event?.kind?.let { ChallengeRun(Run.Event(it), null, it) } ?: ChallengeRun(Run.Review, null, null)
                is ChallengeOrigin.Gather -> ChallengeRun(Run.Gather(origin.res), null, null)
                is ChallengeOrigin.Project -> {
                    val spec = Projects.spec(origin.id)
                    val o = if (s != null && spec != null) Projects.option(s, spec, today) else null
                    val run = lead(spec?.leader)
                    ChallengeRun(run, null, null) { c, who -> Intros.task(c, who, run, stake = o?.let(::stepStake), bond = Catalog.PROJECT_STEP_POINTS) }
                }
                is ChallengeOrigin.Festival -> {
                    val f = Calendar.byId(origin.id)
                    val run = lead(if (s != null && f != null) Calendar.leader(s, f, today) else null)
                    val good = f?.good?.let { Catalog.goods[it] }
                    ChallengeRun(run, null, null) { c, who ->
                        Intros.task(
                            c, who, run, ask = f?.let { Intros.split(it.ask) }, reward = s?.let { Calendar.reward(it) }.orEmpty(),
                            stake = listOfNotNull(good?.let { "${it.name.emoji} ${it.name.sl} · ${it.name.en}" + if (f.goods > 1) " ×${f.goods}" else "" }, "+${Calendar.MORALE} 😊")
                                .joinToString(", "),
                            bond = Calendar.LEADER_POINTS,
                        )
                    }
                }
                ChallengeOrigin.Surprise -> {
                    val sp = s?.let { Surprises.playable(it, today) }
                    // the stranger from the road asks himself (the pilgrim); else who it's about, or today's companion
                    val run = sp?.kind?.takeIf { it in Surprises.strangers }?.let { Run.Stranger(it) } ?: lead(sp?.who)
                    ChallengeRun(run, null, null) { c, who ->
                        Intros.task(
                            c, who, run, ask = sp?.let { Intros.split(Surprises.ask(it)) }, reward = s?.let { Surprises.reward(it) }.orEmpty(),
                            bond = if (sp?.kind == Surprises.LETTER) Surprises.LETTER_POINTS else Surprises.POINTS,
                        )
                    }
                }
                // a station of the treasure hunt: the storyteller who gave the map is on the stage
                is ChallengeOrigin.Treasure -> {
                    val run = lead(teller)
                    ChallengeRun(run, null, null) { c, who -> Intros.task(c, who, run) }
                }
            }
        }

        /** "Korak stane … · The step costs …, paid once it succeeds". */
        private fun stepStake(o: ProjectOption): String =
            bi("challengeScreen.stepCosts", "cost" to Projects.costLine(o))
    }
}

@Composable
private fun ChallengeResultView(vm: AppViewModel, origin: ChallengeOrigin, c: Challenge, r: Reward, stage: StageState) {
    val result = r.result
    val won = result?.won ?: false
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { if (won) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    // a station of the treasure hunt leads back to the map
    val treasure = origin is ChallengeOrigin.Treasure
    val back: () -> Unit = if (treasure) ({ vm.openTreasure(vm.treasure.fromVillage) }) else vm::village
    BackHandler(onBack = back)
    val (emoji, title) = when {
        treasure && won -> "🗺️" to bi("treasure.stationDone")
        treasure -> "🧭" to bi("treasure.stationBlocked")
        origin is ChallengeOrigin.Gather -> (if (r.earned.isNotEmpty()) "🧺" else "🍂") to (if (r.earned.isNotEmpty()) bi("challengeScreen.gathered") else bi("challengeScreen.emptyHanded"))
        origin is ChallengeOrigin.Quest && won -> "🎁" to bi("challengeScreen.questComplete")
        origin is ChallengeOrigin.Quest -> "🤝" to bi("challengeScreen.notQuite")
        origin is ChallengeOrigin.Project && won -> "🏗️" to bi("challengeScreen.stepDone")
        origin is ChallengeOrigin.Project -> "🤝" to bi("challengeScreen.notQuite")
        origin is ChallengeOrigin.Festival && won -> c.emoji to bi("challengeScreen.happyHoliday")
        origin is ChallengeOrigin.Festival -> c.emoji to bi("challengeScreen.notQuite")
        origin == ChallengeOrigin.Surprise && won -> "🎁" to bi("challengeScreen.thank")
        origin == ChallengeOrigin.Surprise -> "🙂" to bi("challengeScreen.neverMind")
        won -> "🏆" to bi("challengeScreen.victory")
        else -> "💔" to bi("challengeScreen.defeat")
    }
    val gained = addRes(r.earned, if (won) result?.rewards.orEmpty() else emptyMap())
    val damaged = result?.damaged.orEmpty().mapNotNull { id -> vm.game.state?.buildings?.firstOrNull { it.id == id } }
    Box(Modifier.fillMaxSize().background(backdrop(origin, won = if (origin is ChallengeOrigin.Gather) null else won))) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Bobbing(emoji)
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(tallyText(r), color = XpGold, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            // the letter looked at again while answering: how often, and what it took off
            LooksLine(looksText(r.looks, r.lookCost, pays = true), color = Color.White.copy(alpha = 0.85f))
            if (stage.finale != null) Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape = MaterialTheme.shapes.large) {
                StageFinale(stage, Modifier.padding(12.dp))
            }
            if (origin == ChallengeOrigin.Event && r.answered < r.total) {
                Text("🏃 ${bi("challengeScreen.leftFight")}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            }
            result?.message?.let { Text(it, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
            // a request, a project's step or a feast not passed: say so plainly, and what it takes (it stays open)
            val open = !won && (origin is ChallengeOrigin.Quest || origin is ChallengeOrigin.Project || origin is ChallengeOrigin.Festival || treasure)
            if (open && c.passMark > 0) Text(
                "🔸 ${bi("challengeScreen.notDoneYet", "mark" to c.passMark, "total" to r.total)}",
                color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
            )
            // a request: how close Jan is now, and after a few tries the rules it missed, one tap away
            val quest = (origin as? ChallengeOrigin.Quest)?.takeIf { !won }?.let { o -> vm.game.state?.quests?.firstOrNull { it.id == o.questId } }
            quest?.let(::progressText)?.let { Text(it, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            if (quest != null && vm.game.state?.let { QuestQueue.hard(quest, it.quests) } == true) {
                OutlinedButton(onClick = { vm.practiseHard(quest.id) }) { Text(practiseHardLabel(), color = Color.White) }
            }
            HelpAndThanks(r, color = Color.White)
            r.friend?.let { si.lanisce.lani.ui.villagers.FriendChip(it) }
            if (gained.isNotEmpty()) {
                Text(if (open) bi("challengeScreen.forEffort") else bi("common.forYourVillage"), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
                Surface(color = Color.White.copy(alpha = 0.92f), shape = MaterialTheme.shapes.large) {
                    Box(Modifier.padding(12.dp)) { RewardChips(gained, startDelayMs = 400) }
                }
            }
            // the thank-you good, the festival's, a friend's gift: presents to open, after the chips
            GiftReveals(remember(r) { GiftLogic.of(r) }, giftWords(vm), startDelayMs = giftDelayMs(gained.count { it.value > 0 }, 400))
            val losses = result?.losses.orEmpty()
            if (losses.values.any { it > 0 }) {
                Text(bi("challengeScreen.losses"), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
                Surface(color = Color.White.copy(alpha = 0.92f), shape = MaterialTheme.shapes.large) {
                    Box(Modifier.padding(12.dp)) { RewardChips(losses, negative = true, startDelayMs = 900) }
                }
            }
            for (b in damaged) {
                Text("⚠️ ${bi("challengeScreen.buildingDamaged", "typeEmoji" to b.type.emoji, "typeSl" to b.type.names, "typeEn" to b.type.names)}", color = Color.White, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
            if (treasure) BigButton("🗺️  ${bi("treasure.toMap")}", onClick = back, color = if (won) XpGold else AlpineGreen)
            else BigButton("🏡  ${bi("challengeScreen.village")}", onClick = vm::village, color = if (won) XpGold else AlpineGreen)
            if (origin is ChallengeOrigin.Gather) {
                TextButton(onClick = { vm.startGather(origin.res, again = true) }) { Text("🔁 ${bi("challengeScreen.onceMore")}", color = Color.White) }
            }
            if (origin is ChallengeOrigin.Quest && !won) {
                TextButton(onClick = { vm.startQuest(origin.questId) }) { Text("🔁 ${bi("challengeScreen.onceMore")}", color = Color.White) }
            }
        }
        if (won || (origin is ChallengeOrigin.Gather && r.earned.isNotEmpty())) Confetti(key = c)
    }
}
