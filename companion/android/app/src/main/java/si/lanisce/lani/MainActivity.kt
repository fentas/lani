package si.lanisce.lani

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import si.lanisce.lani.data.DeepLink
import si.lanisce.lani.data.ScreenClock
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import si.lanisce.lani.ui.ChatSheet
import si.lanisce.lani.ui.HomeScreen
import si.lanisce.lani.ui.PackLearnScreen
import si.lanisce.lani.ui.PacksScreen
import si.lanisce.lani.ui.PermissionDialog
import si.lanisce.lani.ui.PlayerScreen
import si.lanisce.lani.ui.ReviewScreen
import si.lanisce.lani.ui.SetupScreen
import si.lanisce.lani.ui.family.FamilyScreen
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
        enableEdgeToEdge()
        // A tapped notification's link; not again when the activity is recreated from saved state.
        if (savedInstanceState == null) openLink(intent)
        // The speaker everywhere (not only in exercises), so tutor texts can speak their Slovene. The labels read the
        // learner's language pair when they're built: another pair builds the screens anew.
        setContent { LaniTheme { CompositionLocalProvider(LocalSpeaker provides vm.speaker) { key(vm.langPair, vm.culture, vm.learner) { App(vm) } } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
    }

    private fun openLink(intent: Intent?) {
        // what QA asks of a debug build (a release ignores it), before the link it may open
        si.lanisce.lani.app.QaHooks.handle(intent?.getStringExtra(si.lanisce.lani.app.QaHooks.EXTRA), road = vm::qaRoad, forms = vm::qaForms, step = vm::qaStep)
        intent?.removeExtra(si.lanisce.lani.app.QaHooks.EXTRA)
        DeepLink.parse(intent?.getStringExtra(DeepLink.EXTRA))?.let(vm::open)
        intent?.removeExtra(DeepLink.EXTRA)
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

    val screen = vm.screen
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (screen) {
                Screen.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                Screen.Setup -> SetupScreen(vm)
                Screen.Home -> HomeScreen(vm)
                is Screen.Review -> key(screen) { ReviewScreen(vm, screen.cards, screen.polish, screen.language) }
                is Screen.Player -> PlayerScreen(vm, screen.module)
                is Screen.GrammarPractice -> key(screen) { GrammarPracticeScreen(vm, screen.page, screen.practice) }
                Screen.Village -> VillageScreen(vm)
                is Screen.Challenge -> ChallengeScreen(vm, screen.origin, screen.challenge)
                is Screen.Packs -> PacksScreen(vm, screen.fromVillage)
                is Screen.Readings -> si.lanisce.lani.ui.reading.ReadingsScreen(vm, screen.fromVillage)
                is Screen.ReadAloud -> key(screen) { si.lanisce.lani.ui.reading.ReadAloudScreen(vm, screen.id, screen.fromVillage, screen.fromCorner) }
                is Screen.PackLearn -> PackLearnScreen(vm, screen.pack, screen.words, screen.fromVillage, screen.scene)
                is Screen.UpgradeWords -> key(screen) { si.lanisce.lani.ui.game.UpgradeWordsScreen(vm, screen.building, screen.toLevel, screen.run) }
                Screen.Talk -> TalkScreen(vm)
                is Screen.Progress -> ProgressScreen(vm, screen.fromVillage)
                is Screen.Family -> FamilyScreen(vm, screen.challengeId)
                is Screen.Scene -> SceneScreen(vm, screen.id, screen.focus, screen.building)
                Screen.Villagers -> VillagersScreen(vm)
                is Screen.Villager -> VillagerScreen(vm, screen.id)
                Screen.Friends -> FriendsScreen(vm)
                is Screen.Visit -> VisitScreen(vm)
                is Screen.VisitScene -> VisitSceneScreen(vm, screen.scene)
                is Screen.VisitRequest -> key(screen) { VisitRequestScreen(vm, screen.request) }
                is Screen.TownQuestion -> key(screen) { TownQuestionScreen(vm, screen.key, screen.asked) }
                is Screen.Treasure -> si.lanisce.lani.ui.game.TreasureScreen(vm)
            }
            VoiceBadge(vm.family.voice, Modifier.align(Alignment.TopCenter))
        }
    }
    // the story notebook over any screen (the chest, the reading corner, the storyteller, a story's end), under a word's card opened from it
    vm.notebook?.let { si.lanisce.lani.ui.notebook.NotebookSheet(vm, it) }
    // a dialog line's grammar (a long press on it), under the page and the word's card opened from it
    vm.sentences.card?.let { si.lanisce.lani.ui.words.SentenceSheet(vm, it) }
    // a grammar page over any screen (a "why", a run's intro, the scroll), under a word's card opened from it
    vm.grammar.open?.let { GrammarSheet(vm, it) }
    if (vm.chat.isOpen) ChatSheet(vm)
    // giving a good (from the chest, a villager's page): the gift moment, under a word's card opened from it
    vm.villagers.gift?.let { GiftScene(vm, it) }
    // meeting someone who joined the village (a bubble at their home, the scroll, their card): their introduction
    vm.villagers.meeting?.let { si.lanisce.lani.ui.villagers.ArrivalScene(vm, it) }
    // someone at a spot of the landscape (the charcoal burner by his kopa, his bubble or himself tapped): his talk
    vm.scenes.keeperTalk?.let { si.lanisce.lani.ui.scene.KeeperScene(vm, it) }
    // a village project's step played over the village (no scene of its own open today): its people and its dialog
    vm.scenes.talk?.takeIf { it.step?.over == true }?.let { si.lanisce.lani.ui.scene.ProjectStepScene(vm, it) }
    vm.words.card?.let { WordSheet(vm, it) }
    // no village yet, anywhere: the learner chooses where it will be before anything else of the game
    val founding = vm.game.placeNeeded && vm.game.state == null && (screen == Screen.Home || screen == Screen.Village)
    if (founding) si.lanisce.lani.ui.game.FoundingScreen(vm)
    if (!founding && vm.game.news.isNotEmpty() && vm.game.introSeen && (screen == Screen.Home || screen == Screen.Village)) {
        NewsSheet(vm.game.news, onDismiss = vm.game::dismissNews)
    }
    vm.permission?.let { PermissionDialog(it, vm::answerPermission) }
}
