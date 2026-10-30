package si.lanisce.lani

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import si.lanisce.lani.data.DeepLink
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.ui.LinkGate
import si.lanisce.lani.ui.LocalSpeaker
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import si.lanisce.lani.ui.BackPeek
import si.lanisce.lani.ui.ChatSheet
import si.lanisce.lani.ui.HomeScreen
import si.lanisce.lani.ui.PackLearnScreen
import si.lanisce.lani.ui.PacksScreen
import si.lanisce.lani.ui.PermissionDialog
import si.lanisce.lani.ui.PlayerScreen
import si.lanisce.lani.ui.ReviewScreen
import si.lanisce.lani.ui.family.FamilyScreen
import si.lanisce.lani.ui.setup.SetupScreen
import si.lanisce.lani.ui.family.VoiceBadge
import si.lanisce.lani.ui.game.ChallengeScreen
import si.lanisce.lani.ui.game.GrammarPracticeScreen
import si.lanisce.lani.ui.game.GrammarSheet
import si.lanisce.lani.ui.game.NewsSheet
import si.lanisce.lani.ui.game.VillageScreen
import si.lanisce.lani.ui.talk.TalkScreen
import si.lanisce.lani.ui.progress.ProgressScreen
import si.lanisce.lani.ui.scene.SceneScreen
import si.lanisce.lani.ui.villagers.GiftScene
import si.lanisce.lani.ui.villagers.VillagerScreen
import si.lanisce.lani.ui.villagers.VillagersScreen
import si.lanisce.lani.ui.towns.FriendsScreen
import si.lanisce.lani.ui.towns.TownQuestionScreen
import si.lanisce.lani.ui.towns.VisitRequestScreen
import si.lanisce.lani.ui.towns.VisitSceneScreen
import si.lanisce.lani.ui.towns.VisitScreen
import si.lanisce.lani.ui.words.WordSheet
import si.lanisce.lani.ui.theme.LaniTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Session durations count the time on screen, not a pack left open while the phone sleeps.
        ScreenClock.app.install()
        // the map's window of requests starts at 3 on a phone, 4 on a tablet (companion/GAME.md "Quests")
        si.lanisce.lani.game.RequestWindow.base = si.lanisce.lani.game.RequestWindow.baseFor(resources.configuration.smallestScreenWidthDp)
        enableEdgeToEdge()
        // A tapped notification's link; not again when the activity is recreated from saved state.
        if (savedInstanceState == null) openLink(intent)
        // The speaker everywhere (not only in exercises), so tutor texts can speak their Slovene. The labels read the
        // learner's language pair when they're built: another pair builds the screens anew. Every link tapped goes
        // through the gate (fran.si at once, any other after asking: ui/Links.kt).
        setContent { LaniTheme { LinkGate { CompositionLocalProvider(LocalSpeaker provides vm.speaker) { key(vm.langPair, vm.culture, vm.learner) { App(vm) } } } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
    }

    private fun openLink(intent: Intent?) {
        // what QA asks of a debug build (a release ignores it), before the link it may open
        si.lanisce.lani.app.QaHooks.handle(intent?.getStringExtra(si.lanisce.lani.app.QaHooks.EXTRA), road = vm::qaRoad, forms = vm::qaForms, step = vm::qaStep, audio = vm::qaAudio, season = vm::qaSeason, ready = vm::qaBuild,
            chat = { vm.chat.onReply(si.lanisce.lani.data.BridgeEvent.Reply("main", it, null)) }, cheat = { vm.cheat.show(it) }, school = vm::qaSchool, schoolUnit = vm::qaSchoolUnit, schoolReveal = vm::qaSchoolReveal,
            counting = { if (it == "intro") vm.qaCountingIntro() else vm.qaCounting() },
            shadow = { base -> vm.shadow.showDeveloper(true); if (base.isNotBlank()) vm.shadow.update { it.copy(debugBaseUrl = base) }; vm.shadow.panelOpen = true },
            dev = { what -> if (what == "again") vm.devUpload.forgetThrottle() else if (what == "off") vm.shadow.showDeveloper(false) },
            pair = { code -> vm.chooseLangPair(si.lanisce.lani.l10n.LangPair.parse(code)) }, grow = vm::qaAgeUp, formsCard = vm::qaFormsCard,
            data = { vm.dataOpen = true }, review = vm::qaReview, numbers = vm::qaNumbers, event = vm::qaEvent, hearths = vm::qaHearths,
            request = vm::qaRequest, word = vm::qaWord, friends = { vm.openFriends() }, sheet = vm::qaSheet, grammar = { vm.grammar.show(it) }, talk = { vm.talkWithVillager(it) }, cases = vm::qaCases, tips = vm::qaTips,
            tutorLive = { base -> vm.live.update { it.copy(debugBaseUrl = base.ifBlank { null }) } })
        intent?.removeExtra(si.lanisce.lani.app.QaHooks.EXTRA)
        DeepLink.parse(intent?.getStringExtra(DeepLink.EXTRA))?.let(vm::open)
        intent?.removeExtra(DeepLink.EXTRA)
        // a link that opened the app, taken once: a town's invitation (lani://town?…, fluent://town?…,
        // https://lani.page/town/#…: the manifest's filters) opens Friends' Accept dialog, filled in; any other lani:// link
        // (lani://review, lani://scene/<id>, lani://tutor …: data/DeepLink.fromUri) its screen
        if (intent?.action == Intent.ACTION_VIEW) {
            (DeepLink.ofUri(intent.dataString) ?: DeepLink.fromUri(intent.dataString))?.let(vm::open)
            intent.data = null
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onResume() {
        super.onResume()
        vm.ambience.away(false)
        vm.update.check()
        vm.speaker.refresh()
        vm.recognizer.refresh()
        vm.game.sync()
        vm.flushOutbox()
    }

    // Paused, not only stopped: the voice dialog of the phone's recognizer comes over the app while it listens, and the
    // background sounds must be quiet then.
    override fun onPause() {
        super.onPause()
        vm.ambience.away(true)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.onBackground()
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.notices.banner) {
        vm.notices.banner?.let { snackbar.showSnackbar(it); vm.notices.banner = null }
    }
    LaunchedEffect(vm.notices.error) {
        vm.notices.error?.let { snackbar.showSnackbar("⚠️ $it"); vm.notices.error = null }
    }
    // Speech follows the pair's target: another pair asks the phone's voice, its recognizer and the node's again.
    LaunchedEffect(vm.langPair) {
        vm.speaker.refresh()
        vm.recognizer.refresh()
        vm.stt.refresh()
    }

    // Ask once for notifications (tutor replies, new drills, evening reminder).
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(vm.screen == Screen.Home) {
        if (vm.screen == Screen.Home && Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val entry = vm.nav.entries.last()
    val screen = entry.screen
    // each screen's remembered state (a list's scroll, a section open) kept under its entry while another is on top, and
    // across the process's death (the stack is saved with it: AppViewModel); a screen gone from the stack drops it
    val states = rememberSaveableStateHolder()
    val ids = vm.nav.entries.map { it.id }
    val held = remember { HashSet<Long>() }
    LaunchedEffect(ids) {
        for (gone in held - ids.toSet()) states.removeState(gone)
        held.retainAll(ids.toSet())
        held.addAll(ids)
    }
    // Back to the screen below, as a predictive gesture (Android 14 and later): the screen shrinks towards the finger's
    // side while the way back's name shows behind it; let go, it's there; cancelled, the screen comes back. The screens'
    // own Back (a card, a step, a dialog) comes first; at the root nothing takes it, and the system leaves the app.
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    PredictiveBackHandler(enabled = vm.nav.canGoBack) { events ->
        try {
            events.collect { e ->
                backProgress = e.progress
                backEdge = e.swipeEdge
            }
            backProgress = 0f
            vm.back()
        } catch (e: kotlinx.coroutines.CancellationException) {
            backProgress = 0f
            throw e
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (backProgress > 0f) si.lanisce.lani.ui.BackScrim(backProgress)
            Box(Modifier.fillMaxSize().then(si.lanisce.lani.ui.predictiveBack(backProgress, backEdge))) {
                states.SaveableStateProvider(entry.id) { key(entry.id) { ScreenOf(vm, screen) } }
            }
            if (backProgress > 0f) BackPeek(vm, backProgress, backEdge)
            VoiceBadge(vm.family.voice, Modifier.align(Alignment.TopCenter))
            // on a visit, once, when a line can't be heard (at home, a word of another language): that language's offline voice
            // offered, or a note that nothing speaks it (ui/towns/VisitVoiceNote); nothing while there is none
            si.lanisce.lani.ui.towns.VisitVoiceNote(vm, Modifier.align(Alignment.TopCenter))
        }
    }
    // the story notebook over any screen (the chest, the reading corner, the storyteller, a story's end), under a word's card opened from it
    vm.notebook?.let { si.lanisce.lani.ui.notebook.NotebookSheet(vm, it) }
    // a dialog line's grammar (a long press on it), under the page and the word's card opened from it
    vm.sentences.card?.let { si.lanisce.lani.ui.words.SentenceSheet(vm, it) }
    // a grammar page over any screen (a "why", a run's intro, the scroll), under a word's card opened from it
    vm.grammar.open?.let { GrammarSheet(vm, it) }
    if (vm.chat.isOpen) ChatSheet(vm)
    // the cheat sheet over any screen (Home, the chat, an exercise), under a grammar page and a word's card opened from it
    vm.cheat.open?.let { si.lanisce.lani.ui.CheatSheetSheet(vm, it) }
    // giving a good (from the chest, a villager's page): the gift moment, under a word's card opened from it
    vm.villagers.gift?.let { GiftScene(vm, it) }
    // meeting someone who joined the village (a bubble at their home, the scroll, their card): their introduction
    vm.villagers.meeting?.let { si.lanisce.lani.ui.villagers.ArrivalScene(vm, it) }
    // someone at a spot of the landscape (the charcoal burner by his kopa, his bubble or himself tapped): his talk
    vm.scenes.keeperTalk?.let { si.lanisce.lani.ui.scene.KeeperScene(vm, it) }
    // a village project's step played over the village (no scene of its own open today): its people and its dialog
    vm.scenes.talk?.takeIf { it.step?.over == true }?.let { si.lanisce.lani.ui.scene.ProjectStepScene(vm, it) }
    // "🗣️ Moj glas · My voice" offered once, at the first line of the learner's a dialog says, over it (ui/settings/MyVoiceSettings)
    if (vm.myVoice.offering) si.lanisce.lani.ui.settings.MyVoiceOffer(vm)
    vm.words.card?.let { WordSheet(vm, it) }
    // a word's whole table to read ("📋 Vse oblike"), over the word's card or a forms card's end it was opened from
    vm.formsCards.view?.let { si.lanisce.lani.ui.FormsOverviewSheet(vm, it) }
    // the shadow tutor's debug panel (a developer setting: tutor/ShadowTutor), over everything
    if (vm.shadow.panelOpen) si.lanisce.lani.ui.tutor.ShadowPanel(vm.shadow, vm.devUpload) { vm.shadow.panelOpen = false }
    // "🧑‍🏫 Učitelj · Tutor" on its own (ui/hosted/TutorSheet): the hosted onboarding's tutor step opens it
    if (vm.tutorSheetOpen) si.lanisce.lani.ui.hosted.TutorSheet(vm) { vm.tutorSheetOpen = false }
    // "💾 Podatki · Data" as a sheet (ui/data/DataSheet) over any screen: QA's hook "data" (vm.dataOpen); the learner has the
    // same as the settings' page (Nastavitve › Podatki, ui/settings/SettingsScreen)
    if (vm.dataOpen) si.lanisce.lani.ui.data.DataSheet(vm.data, onDismiss = { vm.dataOpen = false }, banner = { vm.notices.banner = it })
    // no village yet, anywhere: the learner chooses where it will be before anything else of the game
    val founding = vm.game.placeNeeded && vm.game.state == null && (screen == Screen.Home || screen == Screen.Village)
    if (founding) si.lanisce.lani.ui.game.FoundingScreen(vm)
    if (!founding && vm.game.news.isNotEmpty() && vm.game.introSeen && (screen == Screen.Home || screen == Screen.Village)) {
        NewsSheet(vm.game.news, onDismiss = vm.game::dismissNews)
    }
    vm.permission?.let { PermissionDialog(it, vm::answerPermission) }
    // just paired: the offline voice is offered once (the settings' "Zvok in glas · Sound and voice" page has it too)
    if (vm.permission == null) si.lanisce.lani.ui.home.OfflineVoiceOffer(vm.offlineAudio)
}

/** The screen on view ([Screen]), each in its own composable; its entry in the back stack keys it (AppViewModel.nav). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ScreenOf(vm: AppViewModel, screen: Screen) {
    when (screen) {
        // the hosted mode's splash while the phone's bridge boots (ui/hosted), else a spinner until the connection is read
        Screen.Loading -> if (vm.hosted.active) si.lanisce.lani.ui.hosted.HostedSplash(vm) else CircularProgressIndicator(Modifier.align(Alignment.Center))
        Screen.Setup -> SetupScreen(vm)
        Screen.Home -> HomeScreen(vm)
        is Screen.Review -> ReviewScreen(vm, screen.cards, screen.polish, screen.language)
        is Screen.Player -> PlayerScreen(vm, screen.module)
        is Screen.GrammarPractice -> GrammarPracticeScreen(vm, screen.page, screen.practice, screen.forQuest)
        Screen.Village -> VillageScreen(vm)
        is Screen.Challenge -> ChallengeScreen(vm, screen.origin, screen.challenge)
        Screen.Packs -> PacksScreen(vm)
        Screen.Readings -> si.lanisce.lani.ui.reading.ReadingsScreen(vm)
        is Screen.ReadAloud -> si.lanisce.lani.ui.reading.ReadAloudScreen(vm, screen.id)
        is Screen.PackLearn -> PackLearnScreen(vm, screen.pack, screen.words, screen.scene)
        is Screen.UpgradeWords -> si.lanisce.lani.ui.game.UpgradeWordsScreen(vm, screen.building, screen.toLevel, screen.run)
        Screen.Talk -> TalkScreen(vm)
        Screen.Progress -> ProgressScreen(vm)
        Screen.MyWords -> si.lanisce.lani.ui.words.MyWordsScreen(vm)
        is Screen.Family -> FamilyScreen(vm, screen.challengeId)
        is Screen.Scene -> SceneScreen(vm, screen.id, screen.focus, screen.building)
        Screen.Villagers -> VillagersScreen(vm)
        is Screen.Villager -> VillagerScreen(vm, screen.id)
        Screen.Friends -> FriendsScreen(vm)
        is Screen.TownProgress -> si.lanisce.lani.ui.towns.TownProgressScreen(vm, screen.id)
        is Screen.Visit -> VisitScreen(vm)
        is Screen.VisitScene -> VisitSceneScreen(vm, screen.scene)
        is Screen.VisitRequest -> VisitRequestScreen(vm, screen.request)
        is Screen.TownQuestion -> TownQuestionScreen(vm, screen.key, screen.asked)
        Screen.Treasure -> si.lanisce.lani.ui.game.TreasureScreen(vm)
        Screen.School -> si.lanisce.lani.ui.school.SchoolScreen(vm)
        is Screen.CourseUnit -> si.lanisce.lani.ui.school.UnitScreen(vm, screen.id)
        is Screen.UnitIntro -> si.lanisce.lani.ui.school.IntroScreen(vm, screen.id)
        is Screen.Sheet -> si.lanisce.lani.ui.school.SheetScreen(vm, screen.unit, screen.kind, screen.sheet)
        Screen.Numbers -> si.lanisce.lani.ui.numbers.NumbersScreen(vm)
        is Screen.NumbersRun -> si.lanisce.lani.ui.numbers.NumbersRunScreen(vm, screen)
        is Screen.Cases -> si.lanisce.lani.ui.cases.CasesScreen(vm, screen.focus)
        is Screen.CasesRun -> key(screen) { si.lanisce.lani.ui.cases.CasesRunScreen(vm, screen) }
        is Screen.Settings -> si.lanisce.lani.ui.settings.SettingsScreen(vm, screen.section)
        Screen.GrammarBook -> si.lanisce.lani.ui.grammar.GrammarBookScreen(vm)
    }
}
