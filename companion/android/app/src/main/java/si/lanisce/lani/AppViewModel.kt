package si.lanisce.lani

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import si.lanisce.lani.app.ChatController
import si.lanisce.lani.app.ChatStore
import si.lanisce.lani.app.ContentController
import si.lanisce.lani.app.EventStream
import si.lanisce.lani.app.GameController
import si.lanisce.lani.app.GrammarController
import si.lanisce.lani.app.Notices
import si.lanisce.lani.app.PacksController
import si.lanisce.lani.app.QuestionsController
import si.lanisce.lani.app.ProgressController
import si.lanisce.lani.app.ReadingsController
import si.lanisce.lani.app.SceneController
import si.lanisce.lani.app.VillagerController
import si.lanisce.lani.app.SyncController
import si.lanisce.lani.app.TownsController
import si.lanisce.lani.app.Haggle
import si.lanisce.lani.app.VisitController
import si.lanisce.lani.app.VisitHelp
import si.lanisce.lani.app.WordsController
import si.lanisce.lani.app.SentenceController
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Bundle
import si.lanisce.lani.data.TownActs
import si.lanisce.lani.data.TownGuests
import si.lanisce.lani.data.Towns
import si.lanisce.lani.data.TownQuestions
import si.lanisce.lani.data.TownRequest
import si.lanisce.lani.data.VisitWrite
import si.lanisce.lani.app.UpdateController
import si.lanisce.lani.app.launchSafely
import si.lanisce.lani.data.BackgroundCheck
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.DeepLink
import si.lanisce.lani.data.DialogPrefs
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Family
import si.lanisce.lani.data.FamilyHub
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PairFlow
import si.lanisce.lani.data.PairOutcome
import si.lanisce.lani.data.PairFailure
import si.lanisce.lani.data.PairTrouble
import si.lanisce.lani.app.SetupFlow
import si.lanisce.lani.data.PairParse
import si.lanisce.lani.data.PairPayload
import si.lanisce.lani.data.PairedInfo
import si.lanisce.lani.data.Pairing
import si.lanisce.lani.data.PairingApi
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.data.Recognizer
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.data.TalkPhase
import si.lanisce.lani.data.VisitWorld
import si.lanisce.lani.data.Writes
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.Res
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.LangSetting
import si.lanisce.lani.game.culture.CultureSetting
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.inWorld
import si.lanisce.lani.ui.notebook.NotebookAt
import si.lanisce.lani.ui.scene.SceneWords
import si.lanisce.lani.ui.talk.TalkModel
import si.lanisce.lani.ui.villagers.VillagerLogic
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import java.io.File

/**
 * Wires the feature controllers together and owns navigation ([nav]: the back stack, [screen] its top). The state lives
 * in the controllers: [content] (dashboard, modules), [packs], [game] (the village), [chat], [talk], [update], [progress]
 * and [sync] (the offline outbox). They share [notices] for the snackbar. [savedState] keeps the back stack across the
 * process's death.
 */
class AppViewModel(app: Application, private val savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    /** The learner's languages (l10n/LangSetting.kt), applied before anything below builds a label. */
    private val languages = LangSetting(app)
    /** The pair every label is shown in; MainActivity builds the screens anew when it changes. */
    var langPair by mutableStateOf(languages.apply())
        private set
    /** The pair chosen on this phone; null follows the learner profile ([langPairOfProfile]). */
    var langPairChosen by mutableStateOf(languages.chosen)
        private set
    val langPairOfProfile: LangPair get() = languages.profile
    /** Who the content speaks to (l10n/LearnerSetting.kt): applied before anything below reads a text of the content. */
    private val learners = si.lanisce.lani.l10n.LearnerSetting(app)
    /** The learner the content speaks to, by name and gender; MainActivity builds the screens anew when it changes. */
    var learner by mutableStateOf(learners.apply())
        private set
    /** The learner's village's culture pack (game/culture/CultureSetting.kt), applied before the village builds a text. */
    private val cultures = CultureSetting(app)
    /** The culture pack the village is in; MainActivity builds the screens anew when it changes. */
    var culture by mutableStateOf(cultures.apply())
        private set
    /** The landscape the node suggests for a new village (`lani-profile add --landscape`), offered first when it's founded. */
    var nodeLandscape by mutableStateOf<String?>(null)
        private set
    val speaker = Speaker(app)
    val recognizer = Recognizer(app)
    /** Family voices and partner challenges; family recordings play through [speaker]. */
    val family = FamilyHub(app, viewModelScope).also { speaker.voice = it.voice }
    /**
     * Natural voice clips from the node; played through [speaker] after family recordings. The learner's own lines are in
     * the voice the node says (its cast's `player`), until it does their profile's gender's narrator ([Clips.playerOf]).
     */
    val clips = Clips(app, viewModelScope).also {
        speaker.clips = it
        it.playerGender = { learners.profile.gender }
    }
    /** The hint's 🔊 in a dialog: its TL;DR in the learner's base with the Slovene in the app's voice; stopped by [speaker]. */
    val hintVoice = si.lanisce.lani.data.HintVoice(app, viewModelScope, speaker).also { speaker.hint = it }
    /** A unit's introduction read aloud in the teacher's voice (data/IntroVoice.kt); stopped by [speaker], as the hint's. */
    val introVoice = si.lanisce.lani.data.IntroVoice(app, viewModelScope, speaker).also { speaker.intro = it }.also { v ->
        v.explains = { content.dashboard?.features?.contains(si.lanisce.lani.data.IntroVoice.FEATURE) == true }
    }
    /**
     * "🔊 Zvok brez povezave · Offline audio": the day's clips got ready on Wi-Fi within the cap, and the phone's offline voice
     * (Piper), which [speaker] speaks with when no clip can be had (app/OfflineAudioController).
     */
    val offlineAudio = si.lanisce.lani.app.OfflineAudioController(app, viewModelScope, config = { prefs.config() }).also { speaker.offline = it.piper }
    /**
     * A visit's note when nothing can say a line (no clip of the town's, none voiced by the node, no offline voice of its
     * language, no Android voice for it): once a visit, the offline voice to download (app/VisitVoiceController).
     */
    val visitVoice = si.lanisce.lani.app.VisitVoiceController(viewModelScope, offlineAudio, offlineAudio.settings).also { v ->
        speaker.onSilent = { if (VisitWorld.active) v.silent() }
    }
    /** On a visit, the visited town's clips through the node ([Clips] at the town's routes); null at home. */
    private var visitClips: Clips? = null
    /** "🚗 Za pot · For the road": the car's sessions got ready on the phone (road/RoadPrep), played by road/RoadService. */
    val road = si.lanisce.lani.road.RoadPrep(app, viewModelScope)
    /** The phone's own player for the road's sessions (Bluetooth without Android Auto). */
    val roadRemote = si.lanisce.lani.road.RoadRemote(app)
    val stt = si.lanisce.lani.data.Stt(app, viewModelScope) // Whisper on the node, for speaking (data/Stt.kt)
    /** The background sounds of the village and its scenes (data/Ambience.kt): they duck under [speaker]. */
    val ambience = si.lanisce.lani.data.Ambience(app, viewModelScope, talking = { speaker.talking })
    private var bridge: Bridge? = null
    private val node = { bridge }
    /** The connection the app has now (a visit's clips are asked at the town's routes of the same node). */
    private var connected: BridgeConfig? = null

    val notices = Notices()
    val sync = SyncController(app, viewModelScope, node, notices, onSaved = ::refresh)
    val content: ContentController = ContentController(app, prefs, node, onDashboard = {
        timeHome()
        game.refreshWidget()
        content.dashboard?.let {
            if (languages.profileSays(it.pair)) showLanguages()
            if (learners.profileSays(it.addressee)) relearn()
        }
    })
    val packs = PacksController(prefs, node, notices, sync)
    val game: GameController = GameController(
        app, viewModelScope, prefs, node, content, notices,
        canSpeak = { speaker.canSay }, reload = ::reload,
        saveReviews = { results, minutes, answers -> finishReview(results, minutes, answers = answers, from = si.lanisce.lani.data.ReviewFrom.CHALLENGE) },
        saveWords = packs::saveWords,
        cast = { villagers.all },
    )
    // the chat by month (files/chat/2026-09.json); chat.json is what was kept before the archive (ChatStore)
    val chat = ChatController(
        viewModelScope, prefs, sync, notices, ChatStore(File(app.filesDir, "chat"), File(app.filesDir, "chat.json")),
        // its photos (files/chat-photos/<id>.jpg), the node's copy fetched for one the phone hasn't
        photos = si.lanisce.lani.data.ChatPhotoStore(File(app.filesDir, "chat-photos")),
        node = node,
    )
    /**
     * The in-app tutor's prototype in shadow mode (docs/plans/05-phase-0.md, check 6): a developer setting, off by
     * default. It sees the writes the node took (the learner's events) and the real tutor's replies, and shows what it
     * would have said in its debug panel; nothing of it reaches the node but GETs, the chat or a notification.
     */
    val shadow = si.lanisce.lani.tutor.ShadowTutor(
        viewModelScope,
        si.lanisce.lani.tutor.ShadowStore(File(app.filesDir, "shadow-tutor")),
        // the Claude key, shared with the live tutor (one vault and one ChatGPT sign-in a process: tutor/live/TutorRuntime)
        si.lanisce.lani.tutor.live.TutorRuntime.vault(app),
        bridgeConfig = { prefs.config() },
        debug = si.lanisce.lani.BuildConfig.QA, // a debug or qa build: the base URL may be QA's mock
        learner = { content.dashboard?.name },
        where = ::whereNow,
        // its calls, turns and transport errors (never the key: tutor/KeyVault Redact); it runs only when the developer
        // turned it on, and its lines go to the node with the app's log (devUpload)
        logLine = { android.util.Log.i("ShadowTutor", it) },
        // a chat's photo, from the phone's copy: the turn sends it to the API as an image block (never in the export)
        photo = { id -> chat.photos?.file(id)?.takeIf { it.isFile } },
        // OpenAI (the matrix's OpenAI lanes, "also run on" an OpenAI model): an API key and/or "Sign in with ChatGPT",
        // each encrypted with a Keystore key of its own (docs/plans/05-built/providers.md)
        openai = si.lanisce.lani.tutor.live.TutorRuntime.openai(app),
        // Gemini (a Google AI Studio key) and OpenRouter (its sign-in), the live tutor's vaults: their lanes and "also run on"
        gemini = si.lanisce.lani.tutor.live.TutorRuntime.gemini(app),
        // the EEA, Switzerland, the UK: Google allows only the Gemini API's paid services there (tutor/Region)
        paidOnlyRegion = { si.lanisce.lani.tutor.live.AndroidRegion.geminiPaidOnly(app) },
        openrouter = si.lanisce.lani.tutor.live.TutorRuntime.openrouter(app),
        // offline (also Android holding the background app's network): its queue and the lanes' wait for the network
        online = si.lanisce.lani.tutor.live.AndroidOnline.of(app),
        // hidden while the live tutor answers: it would shadow itself
    ).also { s -> sync.onDelivered = { e -> if (!live.settings.on) s.onDelivered(e.clientId, e.path, e.body) } }

    /**
     * The tutor in the app (docs/plans/05-built/tutor-live.md): off by default, Claude Code on the node answers as before.
     * "🧑‍🏫 Učitelj · Tutor" → "In the app" attaches it to the paired bridge's channel: it answers for real, on the
     * learner's own key, its replies stream into the chat. It lives as long as the process (tutor/live/TutorRuntime), so
     * the WorkManager check uses the same.
     */
    val live: si.lanisce.lani.tutor.live.LiveTutor = si.lanisce.lani.tutor.live.TutorRuntime.live(
        app, si.lanisce.lani.BuildConfig.QA, { k, a -> bi(k, *a.toList().toTypedArray()) },
    ).also { t ->
        t.learner = { content.dashboard?.name }
        t.where = ::whereNow
        t.language = { grammar.language }
        t.photo = { id -> chat.photos?.file(id)?.takeIf { it.isFile } }
        // the reply as it's written, in the chat's typing bubble; the final message (the bridge's reply event) replaces it
        t.onStreaming = { conversation, text -> if (conversation == "main") chat.streaming = text }
        t.onNote = { text -> viewModelScope.launch { chat.note(text) } }
    }
    /**
     * The developer's data on the node (companion/README.md "Developer data on the node"): the shadow's export and the
     * app's log, cleaned of secrets, sent to the paired node by itself while the developer setting is on: after the
     * shadow's day closed, when the app goes to the background (at most every 30 minutes), by the panel's button.
     */
    val devUpload = si.lanisce.lani.app.DevUpload(
        viewModelScope,
        File(app.filesDir, "dev-upload.json"),
        developer = { shadow.settings.developer },
        node = {
            val b = bridge
            val c = prefs.config()
            if (b == null || c == null) null
            else object : si.lanisce.lani.app.DevUpload.Node {
                override val secrets = listOf(c.token)
                override suspend fun post(path: String, body: String, type: String) = b.devUpload(path, body, type)
            }
        },
        export = { secrets, max -> shadow.exportText(appName(), secrets, max) },
        readLog = { si.lanisce.lani.app.AppLog.read(android.os.Process.myPid()) },
        about = { "${appName()}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), ${android.os.Build.MODEL}, pid ${android.os.Process.myPid()}" },
        logLine = { android.util.Log.i(si.lanisce.lani.app.DevUpload.TAG, it) },
    ).also { u -> shadow.onDayClosed = { u.onClosing() } }

    /**
     * "💾 Podatki · Data" (sync/DataSync, docs/plans/05-built/git-sync.md): the phone's data directory (<filesDir>/lani/data)
     * synced with a repository on GitHub, the lease and the handover's QR code, the encrypted backup. Opt-in: nothing runs
     * until git is connected or a backup set up. The hosted mode's bridge calls [DataSync.touched] after its writes and
     * asks [DataSync.mayWrite].
     */
    val data = si.lanisce.lani.sync.DataSync(
        File(app.filesDir, "lani"), viewModelScope, si.lanisce.lani.sync.KeystoreBox(app),
        deviceName = android.os.Build.MODEL ?: "Android",
        clientId = si.lanisce.lani.BuildConfig.GITHUB_CLIENT_ID,
        relayUrl = si.lanisce.lani.BuildConfig.RELAY_URL,
        appVersion = "${si.lanisce.lani.BuildConfig.APPLICATION_ID} ${si.lanisce.lani.BuildConfig.VERSION_NAME}",
        log = { android.util.Log.i("DataSync", it) },
    )
    /** Whether the Data sheet is open (QA's "data"; the learner opens the same as the settings' page "💾 Podatki · Data"). */
    var dataOpen by mutableStateOf(false)

    /**
     * "☁️ Lani na tem telefonu · Lani on this phone" (hosted/HostedController, docs/plans/05-built/hosted-mode.md): no node,
     * the bridge in the app's process (QuickJS), the learner's data in <filesDir>/lani/data. The connection is then the
     * phone's own address, so everything below talks to it as to a node (phone/PhoneTransport). Opt-in, at setup or by
     * the settings' switch; a paired phone never runs it.
     */
    val hosted = si.lanisce.lani.hosted.HostedController(app, viewModelScope, prefs, data, ::appName, android.os.Build.MODEL ?: "Android").also { h ->
        // a pull, an import or a new relay put the bridge over new data: everything is read again
        h.onNewData = {
            refresh()
            game.sync(reloadFirst = true)
            if (foreground) events.restart()
        }
    }
    /** The setup opens on the hosted mode's steps (the settings' switch from a node: back goes to the node). */
    var setupHosted by mutableStateOf(false)
        private set

    /**
     * Whether a tutor answers in the hosted mode: the live tutor on (tutor/live), with its credential. On the phone it is
     * the only tutor: it is turned on as soon as a credential is set (init below), and the chat and a role-play say so
     * while there is none.
     */
    val tutorReady: Boolean get() = live.settings.on && live.credentialReady()
    /** The "🧑‍🏫 Učitelj · Tutor" section on its own (ui/hosted/TutorSheet): the hosted onboarding's tutor step opens it. */
    var tutorSheetOpen by mutableStateOf(false)

    fun openTutorSettings() {
        tutorSheetOpen = true
    }

    /**
     * When the view model began, or the onboarding finished: the hosted mode's way to Home is measured from here (the
     * log, tag LaniHosted).
     */
    private var startedAt = System.nanoTime()
    private var homeTimed = false

    /** The app as the shadow's export names it: its id, version and build. */
    fun appName() = "${si.lanisce.lani.BuildConfig.APPLICATION_ID} ${si.lanisce.lani.BuildConfig.VERSION_NAME} (${si.lanisce.lani.BuildConfig.VERSION_CODE})"
    /** The village book's grammar chapter: its pages, the one open over any screen, the pages met (app/GrammarController). */
    val grammar = GrammarController(node, game, content, chat, notices).also { g ->
        // a session's report tells the tutor which rules are ripe to introduce (companion/GAME.md, "Rules not yet")
        chat.sessionExtras = g::sessionExtras
    }
    /**
     * "📋 Plonk listek · Cheat sheet": the basics as compact cards (the grammar book's pages', curated ones, the tutor's,
     * the tutor messages pinned), unlocked as the pages are met, opened from Home, the chat and any exercise (app/CheatSheetController).
     */
    val cheat = si.lanisce.lani.app.CheatSheetController(game, grammar, notices)
    /** A word tapped in a line: its card, adding it to the learner's words, asking the tutor about it. */
    val words = WordsController(viewModelScope, node, sync, chat, notices)
    /** A word's forms: the form questions of the reviews, the table on a word's card (companion/GAME.md, "A word's forms"). */
    val forms = si.lanisce.lani.app.FormsController(app, viewModelScope, node, grammar)
    /**
     * "📚 Šola · School" (companion/GAME.md, "The school"; companion/COURSE.md): the course, the classroom under the linden,
     * the units and their sheets, what they pay and leave in the progress.
     */
    /**
     * "🔢 Štetje · Counting": counting cards of the learner's learned nouns, on once the unit stevila's intro is done, made
     * by the node from the dictionary, a few a day (app/CountingController, data/Counting.kt).
     */
    val counting = si.lanisce.lani.app.CountingController(viewModelScope, node, game, content, forms, notices)
    /**
     * "📋 Oblike · Forms": forms cards of the learner's words, their whole tables, the forms the school introduced asked;
     * made by the node from the dictionary, a few a day or at once from a word's card (app/FormsCardController,
     * data/FormsCard.kt).
     */
    val formsCards: si.lanisce.lani.app.FormsCardController = si.lanisce.lani.app.FormsCardController(viewModelScope, node, game, content, forms, notices, course = { school.course })
    val school: si.lanisce.lani.app.SchoolController = si.lanisce.lani.app.SchoolController(
        node, game, content, grammar, cheat, chat, forms, cast = { villagers.all },
        onIntroDone = { u -> counting.introDone(u); formsCards.introDone(u); numbers.introDone(u) },
    )
    /** "🔢 Števila · Numbers": the numbers module, open with the unit stevila's intro (app/NumbersController, data/NumbersPractice). */
    val numbers: si.lanisce.lani.app.NumbersController = si.lanisce.lani.app.NumbersController(
        viewModelScope, game, chat, notices, course = { school.course }, canSay = { speaker.canSay }, canListen = { canListen() },
    )
    /**
     * The learner's own words in the dialogs: which turns test them, and what a first answer on one does to its card, once
     * a day (companion/SCENES.md, "Your words in the dialogs").
     */
    val dialogWords = si.lanisce.lani.app.DialogWordsController(content, forms, sync, game, home = { grammar.language })
    /** "🔍 Slovnica stavka · The sentence's grammar": a dialog line's words, their forms and why (a long press on it). */
    val sentences = SentenceController(viewModelScope, node, chat, grammar)
    /** "📖 Branje · Reading": the reading corner's readings, and the reading practice reported to the node. */
    val readings = ReadingsController(app, viewModelScope, node, sync, game, notices, words::sourceFor)
    /** What the scene dialogs remember on this phone. */
    val dialogPrefs = DialogPrefs(app)
    /** What the tutor chat remembers on this phone (its tip about swiping a message). */
    val chatPrefs = si.lanisce.lani.data.ChatPrefs(app)
    /** What the village's HUD remembers on this phone: minimized or not, the news its panel showed. */
    val hudPrefs = si.lanisce.lani.data.HudPrefs(app)
    val talk = TalkModel(
        viewModelScope,
        send = { kind, conv, text, data ->
            (bridge ?: throw java.io.IOException("not connected")).message(kind, conv, text, data)
            if (!live.settings.on) shadow.onRoleplay(kind, conv, text, data) // seen by the shadow tutor (phase B: not run)
        },
        fetch = { bridge?.scenarios().orEmpty() },
        reward = game::rewardTalk,
        befriend = { id, points, memory -> villagers.talked(id, points, memory) },
        finished = { talkFinished(it) },
        debriefed = { scenario, conversation, data -> talkDebriefed(scenario, conversation, data) },
        stray = { chat.onReply(it) }, // an answer under a debrief left behind shows in the chat
    )
    val update = UpdateController(app, viewModelScope, node, notices)
    val progress = ProgressController(app, viewModelScope, node, notices)
    /** The people of the village and the friendship with each. */
    val villagers = VillagerController(app, viewModelScope, node, notices, game, post = { sync.submitLater(it) }, level = ::levelIn).also { v ->
        v.dialogWords = { d, language -> dialogWords.words(d, language) }
        game.onQuestDone = v::questDone
        game.onArrived = v::arrived
        game.gainOf = { id, before, after -> si.lanisce.lani.ui.villagers.VillagerLogic.gain(id, v.byId(id, after), before, after) }
    }
    /** Close-up scenes of the village, what's on today, and the scene screen's words and dialogs. */
    val scenes = SceneController(
        app, viewModelScope, node, pay = game::finishHappening, befriend = villagers::sceneDone, keepScene = game::keepScene,
        village = { game.state }, level = ::levelIn, storyHeard = game::storyHeard, storyRead = readings::storyHeard,
        dialogHeard = game::dialogHeard, keeperTold = game::keeperTold,
        // the learner's own traps, and turns typed or said as far as they have the rule (companion/SCENES.md, "Adaptive turns")
        adapt = { d, language -> grammar.adapt(d, language, canListen()) },
        // the turns that test the learner's own words count on their cards (companion/SCENES.md, "Your words in the dialogs")
        words = dialogWords::words,
        // a village project's step played as its scene: the step done (companion/SCENES.md, "Project steps")
        stepPlayed = game::projectStepPlayed,
        // an age's party played to its end: the village keeps it (companion/GAME.md, "Ages and their celebrations")
        partyPlayed = game::partyPlayed,
        // the night watch at the charcoal pile: a breakout covered in time, or the pile burnt down
        cycleDecided = game::cycleDecided,
    ).also { s -> grammar.scenes = { s.all } }

    /**
     * «Vidim, vidim» (companion/SCENES.md, "I spy"): a child plays I spy with the learner in a scene's picture; a quick find
     * is a review of the word's card once a day ([si.lanisce.lani.game.PlayReviews]), the end pays the village.
     */
    val ispy = si.lanisce.lani.app.ISpyController(
        scenes, game,
        words = { content.dashboard?.let { si.lanisce.lani.game.WordBook.of(it) } },
        mastery = grammar::mastery,
        bookLanguage = { grammar.language },
        befriend = { id, points, memory -> villagers.befriend(id, points, memory, announce = false) },
        saveReviews = { results, minutes, answers -> finishReview(results, minutes, answers = answers, from = si.lanisce.lani.data.ReviewFrom.ISPY) },
    )

    /** Some recognizer can listen to the learner: the node's, or the phone's (unless it's known not to take the language). */
    fun canListen(): Boolean =
        stt.available == true || ((recognizer.inApp || recognizer.dialog) && recognizer.support != si.lanisce.lani.data.Recognizer.Support.NO)

    /**
     * "🗺️ Zemljevid zaklada · The treasure map" (companion/GAME.md, "The treasure map"): the offer once the learner is ready
     * for the next level, the hunt's stations, the treasure and the new level for the node.
     */
    val treasure = si.lanisce.lani.app.TreasureController(
        game, content, grammar, sync,
        scenes = { scenes.all }, readings = { readings.all }, read = { readings.read() }, cast = { villagers.all },
        canSpeak = { speaker.canSay }, canListen = ::canListen,
    ).also { t ->
        // the level the treasure brought counts before the node has it
        content.treasure = { game.state?.treasure }
        game.treasureRun = { id -> t.challenge(id) }
        game.treasureRequired = t::required
    }

    /** The towns linked with this learner's, and the invitations ("Prijatelji · Friends"). */
    val towns = TownsController(viewModelScope, node, notices).also { t ->
        // what the friendships bring (a shared feast, someone who moved) comes into the village as the list comes
        t.onFriendships = { l -> viewModelScope.launch { game.applyFriendships(l) } }
    }
    /**
     * Learning progress between linked towns (companion/README.md, "Progress between towns"; ui/towns/TownProgressScreen):
     * what each linked town shares with this learner, what this learner shares with each, a challenge sent from there.
     */
    val townProgress = si.lanisce.lani.app.TownProgressController(viewModelScope, node, notices)

    /**
     * At home, a word of a town visited on its card is said in its language ([Speaker.sayIn]): the clips of a linked town of
     * that language through the node, kept by town while the app runs; the towns' list loaded once when it isn't yet.
     */
    private val foreignClips = mutableMapOf<String, Clips>()

    private fun foreignClipsFor(lang: String): Clips? {
        val c = connected ?: return null
        val list = towns.list ?: run { towns.load(); return null }
        val town = list.towns.firstOrNull { it.language == lang } ?: return null
        return foreignClips.getOrPut(town.id) {
            Clips(getApplication(), viewModelScope, visit = true).also { v ->
                v.playerGender = clips.playerGender
                v.connect(c, si.lanisce.lani.data.ClipRoutes.visit(town.id))
                v.refresh()
            }
        }
    }

    init {
        // what the learner has to practise now orders the map's window of requests (companion/GAME.md "Quests"): the
        // dashboard's due and rusty cards, the mistakes' weak patterns and the modules that target them; made again only
        // when the dashboard or the module list changes
        var practiced: Triple<Any?, Any?, si.lanisce.lani.game.Practice>? = null
        si.lanisce.lani.game.RequestWindow.practice = {
            val d = content.dashboard
            val m = content.modules
            practiced?.takeIf { it.first === d && it.second === m }?.third
                ?: si.lanisce.lani.game.Practice.of(d, m).also { practiced = Triple(d, m, it) }
        }
        speaker.foreignClips = ::foreignClipsFor
        // a word of another language nothing could say: that language's offline voice offered, or the note
        speaker.onSilentIn = { _, lang -> visitVoice.silentIn(lang) }
    }

    /** A linked town being visited, in its language (plan 2, §3; ui/towns/VisitScreen), and the things to do there. */
    val visits = VisitController(
        viewModelScope,
        fetch = { id, kind -> bridge?.townPart(id, kind) },
        send = { id, kind, body -> bridge?.townWrite(id, kind, body) },
        talk = { id, body -> bridge?.townTalk(id, body) },
    )
    /**
     * Questions between towns (plan 2, §3.2, "The host takes part"; ui/towns/TownQuestionScreens): the learner's to linked
     * towns on visits, guests' to them; answering one pays like a family challenge's answer.
     */
    val questions = QuestionsController(
        viewModelScope,
        fetch = { bridge?.townQuestions() },
        post = { path, body -> bridge?.send("POST", path, body.toString()) },
        notices = notices,
        onList = { game.questionsAnswered(it) },
        onAnswered = { game.rewardFamily() },
    )
    private val events = EventStream(viewModelScope, prefs, node, notices, onOpen = { flushOutbox() }, onEvent = ::handle)
    /**
     * The network back after it was lost, while the app is on screen: the outbox sent at once and the live events
     * reconnected (a word asked for offline reaches the tutor then, not at the outbox's next retry).
     */
    private val networkBack = si.lanisce.lani.app.NetworkBack(viewModelScope, si.lanisce.lani.tutor.live.AndroidOnline.of(app)) {
        if (foreground && bridge != null) events.restart()
        flushOutbox()
    }
    /** The app is on screen; the event stream only runs then. */
    private var foreground = false
    /** A notification link that arrived before the connection. */
    private var pendingLink: DeepLink? = null

    /** The back stack (Nav.kt, docs/plans/05-built/navigation.md): Back returns to the screen below the one on view. */
    val nav = NavStack()

    /** The screen on view: the back stack's top. */
    val screen: Screen get() = nav.top

    /** The back stack saved before the process died, to open again once connected; null when there is none (or it was). */
    private var restoring: String? = savedState.get<String>(NAV_KEY)
    val link: Link get() = events.link
    /** The node refused this phone's token (unpaired with lani-pair --revoke): time to scan a new code. */
    val unpaired: Boolean get() = events.unpaired
    /** The tutor this phone talks to: its address, and who it paired with by QR code (null for a typed token). */
    var bridgeUrl by mutableStateOf<String?>(null)
        private set
    var paired by mutableStateOf<PairedInfo?>(null)
        private set

    /**
     * The learner of this device is a child: their node's profile says so (lani-profile --child, told at pairing), or the
     * hosted onboarding was for a child. Keys and accounts are then a parent's (app/ParentGate: the tutor's and the data's
     * settings say so, and open after a hold).
     */
    val child: Boolean get() = paired?.child == true || (hosted.active && hosted.child)
    var permission by mutableStateOf<BridgeEvent.PermissionRequest?>(null)
        private set
    /**
     * The story notebook open over whatever is on screen (ui/notebook/NotebookSheet, companion/SCENES.md "The story
     * notebook"): at the entry of the story it names, or at its contents.
     */
    var notebook by mutableStateOf<NotebookAt?>(null)
        private set

    /** Opens the story notebook: at [story]'s entry (a story it goes on from, or the story itself), else at its contents. */
    fun openNotebook(story: String? = null) { notebook = NotebookAt(story) }

    fun closeNotebook() { notebook = null }

    /** Writes and village changes the node doesn't have yet. */
    val waitingToSync: Int get() = sync.pending + if (game.unsynced) 1 else 0

    init {
        viewModelScope.launch {
            val c = prefs.config()
            paired = prefs.pairing()
            when {
                c == null -> nav.reset(Screen.Setup)
                // Lani on this phone: its bridge boots first (the splash, Screen.Loading, meanwhile; ~0.5 s)
                hosted.isHosted(c) -> if (hosted.boot(c)) connect(c)
                else -> connect(c)
            }
        }
        // everyone who lives in the village has a voice profile on the node (their own voice, or pitch and pace)
        viewModelScope.launch {
            snapshotFlow { villagers.voicePeople(game.state) }.distinctUntilChanged().collect { clips.syncProfiles(it) }
        }
        // Lani on this phone has no node: the tutor in the app is the only one, on once a credential is set
        viewModelScope.launch {
            snapshotFlow { hosted.active && !live.settings.on && live.settings.provider.let { live.credentialReady() } }
                .distinctUntilChanged()
                .collect { want ->
                    if (want) live.setOn(true)?.let { si.lanisce.lani.hosted.HostedRuntime.say("the tutor in the app didn't turn on: $it") }
                }
        }
        // the learner opening the chat runs the shadow tutor's digest (plan 5, "Events to turns")
        viewModelScope.launch {
            snapshotFlow { chat.isOpen }.distinctUntilChanged().collect { if (it) { if (live.settings.on) live.onChatOpened() else shadow.onChatOpened() } }
        }
        // the day's audio is planned once the village and the cards are there (a reload may end before the village loads)
        viewModelScope.launch {
            snapshotFlow { game.state != null && content.dashboard != null }.distinctUntilChanged().collect { if (it) offlineAudio.plan(::dayGather) }
        }
        // an upgrade asks for the building's words ("Knowledge builds", companion/GAME.md "Upgrades")
        game.upgradeWords = ::upgradeWords
        // a drill of the tutor's is found for the task it shares a grammar page with (companion/GAME.md "Quests")
        game.modulePages = ::modulePages
        // the school's next level waits for a course level's units; a school built or upgraded opens one (companion/GAME.md "The school")
        game.courseGate = school::gate
        game.onBuilt = school::touch
    }

    /** The grammar pages module [id] practises: the book's pages that list it, and those its cached exercises name. */
    private fun modulePages(id: String): Set<String> =
        (si.lanisce.lani.data.Grammar.ofModule(id, grammar.pages).map { it.id } +
            content.moduleExercises().filter { it.first == id }.mapNotNull { it.second.grammar }).toSet()

    /**
     * The first launch's state (app/SetupFlow, ui/setup/SetupScreen): kept here, so the screens built anew in the pair the
     * learner chose keep their step. The explanations start in the phone's language.
     */
    var setupFlow by mutableStateOf(SetupFlow.start(java.util.Locale.getDefault().language))

    /**
     * The setup's languages ([SetupFlow.pair]): every label reads in them from now on, as the learner profile's pair until
     * the learner's profile says its own (a node's, or the one the hosted onboarding makes from them).
     */
    fun setupLanguages(p: LangPair) {
        if (languages.profileSays(p)) showLanguages()
    }

    /** Validates the config against the bridge, then stores it. Null when connected, else what went wrong, in plain words. */
    suspend fun setup(url: String, token: String): PairTrouble? {
        val c = BridgeConfig(url.trim().trimEnd('/'), token.trim())
        return try {
            Bridge(c).modules()
            prefs.saveConfig(c)
            paired = null
            connect(c)
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.i("LaniSetup", "the typed connection failed: ${e.javaClass.simpleName} ${e.message?.take(80)}")
            PairTrouble.typed(e)
        }
    }

    /**
     * Pairs with the tutor in a scanned QR code (data/Pairing.kt): checks the code, trades it for this phone's own
     * token, and connects. [done] gets null when paired, else what went wrong and its way out ("Slovene · English").
     */
    fun pair(scanned: String, done: (PairTrouble?) -> Unit) = viewModelScope.launch {
        val p = when (val r = PairPayload.parse(scanned)) {
            is PairParse.Ok -> r.payload
            is PairParse.Invalid -> return@launch done(PairTrouble.of(r.problem))
        }
        // Another learner's bridge would get this phone's village, chat and queued answers: not without a fresh start.
        if (!Pairing.sameLearner(prefs.config()?.baseUrl, prefs.pairing(), p)) {
            return@launch done(PairTrouble.of(PairFailure.OTHER_LEARNER, who = paired?.learner ?: content.dashboard?.name))
        }
        when (val o = PairFlow(PairingApi::post).pair(p, android.os.Build.MODEL ?: "Android")) {
            is PairOutcome.Failed -> done(PairTrouble.of(o))
            is PairOutcome.Paired -> {
                prefs.savePairing(o.config, o.info)
                paired = o.info
                connect(o.config)
                notices.banner = Pairing.pairedBanner(o.info)
                offlineAudio.afterPairing() // "Prenesi glas brez povezave?", once
                done(null)
            }
        }
    }

    private fun connect(c: BridgeConfig) {
        bridgeUrl = c.baseUrl
        bridge = Bridge(c)
        connected = c
        family.connect(c)
        clips.connect(c)
        stt.connect(c)
        // Home, or the screens the learner was on before the process died (the stack saved, Nav.kt)
        val back = restoring
        restoring = null
        if (back == null || !nav.restore(back)) nav.reset(Screen.Home)
        // everything read anew (Home's own [shown] doesn't again while it runs); a notification tapped before the app ran
        // opens once it's read (its scene, its page, the cards due are there then)
        val loading = refresh()
        pendingLink?.let { l ->
            pendingLink = null
            viewModelScope.launch {
                loading.join()
                open(l)
            }
        }
        game.sync()
        update.check()
        BackgroundCheck.schedule(getApplication())
        // the hosted mode: no node's clips to get ahead; its rhythm wakes the app's process for its moments (hosted/HostedWork)
        offlineAudio.onPhone = hosted.isHosted(c)
        if (hosted.isHosted(c)) si.lanisce.lani.hosted.HostedWork.schedule(getApplication())
        if (foreground) events.restart()
    }

    // --- "☁️ Lani na tem telefonu" (hosted/HostedController) --------------------------------------------------------

    /** The hosted mode's connection, once kept (after its onboarding's data or import). */
    suspend fun hostedConfig(): BridgeConfig? = prefs.config()?.takeIf(hosted::isHosted)

    /** The onboarding is done (the learner's data made or imported, the bridge up): Home, where the village is founded. */
    fun hostedReady(c: BridgeConfig) {
        setupHosted = false
        paired = null
        startedAt = System.nanoTime()
        homeTimed = false
        viewModelScope.launch {
            game.forgetLocal() // the phone's bridge has the village now (or none: the founding comes)
            connect(c)
        }
    }

    /** The splash's "Try again" after the phone's bridge didn't boot. */
    fun retryHostedBoot() {
        viewModelScope.launch { prefs.config()?.takeIf(hosted::isHosted)?.let { if (hosted.boot(it)) connect(it) } }
    }

    /** An export in the hosted mode's data (the settings' 📥, asked first): checked, the data before kept aside. */
    fun importHosted(uri: android.net.Uri) {
        viewModelScope.launch {
            hosted.import(uri, starting = false)
                .onSuccess { i ->
                    notices.banner = "📥 ${si.lanisce.lani.ui.hosted.importedLine(i.manifest)}"
                    game.forgetLocal() // the export's village, not the phone's copy of the one before
                    hosted.onNewData()
                }
                .onFailure { notices.error = it.message }
        }
    }

    /**
     * The settings' "🖥️ To my own computer" (after saying how the data moves): the phone's bridge stops, the connection
     * goes, setup pairs with the node. The phone's data stays for coming back.
     */
    fun leaveHosted() {
        viewModelScope.launch {
            events.stop()
            si.lanisce.lani.hosted.HostedWork.cancel(getApplication())
            hosted.leave()
            bridge = null
            bridgeUrl = null
            paired = null
            setupHosted = false
            // to the computer's code, in the learner's languages
            setupFlow = SetupFlow(step = SetupFlow.Step.COMPUTER, target = L10n.ownPair.target, base = L10n.ownPair.base, way = SetupFlow.Way.COMPUTER)
            nav.reset(Screen.Setup)
        }
    }

    /** The settings' "☁️ Switch to this phone" (after saying how the data comes): the hosted mode's steps; back to the node. */
    fun switchToHosted() {
        events.stop()
        setupHosted = true
        nav.reset(Screen.Setup)
    }

    /** Back from the hosted steps to the node this phone was paired with. */
    fun cancelSwitch() {
        setupHosted = false
        viewModelScope.launch { prefs.config()?.let(::connect) ?: nav.reset(Screen.Setup) }
    }

    /** The hosted mode's boot to Home, once: from the view model's start to the first dashboard (the log, tag LaniHosted). */
    private fun timeHome() {
        if (homeTimed || !hosted.active) return
        homeTimed = true
        val boot = si.lanisce.lani.hosted.HostedRuntime.lastBoot
        si.lanisce.lani.hosted.HostedRuntime.say("home: ${"%.0f".format((System.nanoTime() - startedAt) / 1e6)} ms after the app started or the onboarding ended (the bridge: $boot)")
    }

    fun refresh(): Job = launchSafely { reload() }.also { reloading = it }

    /** The reload running now, if any ([shown] doesn't start another beside it). */
    private var reloading: Job? = null

    private suspend fun reload() {
        val b = bridge ?: return
        // on a visit the host's language and culture are shown: the learner's own content waits until they're home
        if (VisitWorld.active) return
        // first the culture the bridge plays: what the village, its founding and everything after read (a new phone has
        // the default's until then, and a step below that fails mustn't keep it from coming)
        reloadCulture(b)
        family.refresh()
        questions.load() // guests' questions, and the answers to the learner's (their ties with each town)
        towns.load() // the linked towns, their guests and friendships: Friends' badge on Home and the village (FriendsNews)
        clips.refresh()
        content.reload(b)
        grammar.reload(b)
        cheat.reload(b)
        school.reload(b)
        content.dashboard?.let { forms.reload(b, it.pool) }
        // the day's new counting cards, once the forms are on the phone (it names the nouns the dictionary counts)
        counting.reload(b)
        // and the day's new forms cards (the pages introduced told: the cards they open rows of come back soon)
        formsCards.reload(b)
        readings.reload(b)
        packs.reload(b)
        scenes.reload(b)
        villagers.reload(b)
        // a village already a hamlet when the school came (an older app): its class begins now, its teacher named
        school.touch()
        clips.syncProfiles(villagers.voicePeople(game.state))
        // the node has the new level now, or the learner is ready for the next: the storyteller offers the map
        treasure.check()
        // what the learner will likely hear today and tomorrow, for the background job to get on Wi-Fi
        offlineAudio.plan(::dayGather)
    }

    /** The days ahead's audio, from what the app has now (app/DayAudioGather); null before the learner's cards are loaded. */
    private fun dayGather(): si.lanisce.lani.app.DayAudioGather? {
        val b = bridge ?: return null
        val d = content.dashboard ?: return null
        if (game.state == null && !game.placeNeeded) return null // the village is still loading: its day comes with it
        val target = L10n.ownPair.target.code
        return si.lanisce.lani.app.DayAudioGather(
            getApplication(), cards = d.pool, packs = packs.list,
            pack = { id -> runCatching { b.pack(id) }.getOrNull() ?: scenes.packs[id] },
            state = game.state, scenes = scenes.all, cast = villagers.all, profiles = clips.profiles, level = ::levelIn,
            grammar = if (grammar.language == target) grammar.pages else emptyList(), mastery = grammar::mastery, ispy = ispy::book,
            greetings = listOf(6, 12, 20).map { si.lanisce.lani.ui.greeting(it) }, target = target,
            course = school.course, teacher = school.teacher(), base = L10n.ownPair.base.code, isSlovene = speaker::isSlovene,
            explains = d.features.contains(si.lanisce.lani.data.IntroVoice.FEATURE),
        )
    }

    /** The culture pack the bridge plays for this learner; an older bridge (or none right now) keeps the one we have. */
    private suspend fun reloadCulture(b: Bridge) {
        val info = try {
            b.culture()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val id = info?.id
        if (info != null) nodeLandscape = info.landscape
        if (id != null && cultures.profileSays(id)) {
            culture = cultures.apply()
            readings.onCulture()
        }
    }

    private fun handle(ev: BridgeEvent) {
        when (ev) {
            is BridgeEvent.Reply -> {
                // the real tutor's reply, next to the shadow tutor's in its panel (it only reads it)
                if (!live.settings.on) shadow.onRealReply(ev.conversationId, ev.text, ev.data)
                if (ev.conversationId.startsWith("rp-")) talk.onReply(ev)
                else if (ev.conversationId.startsWith("ex")) chat.onGraded(ev)
                else if (ev.conversationId.startsWith("fam_")) {
                    family.refresh() // the tutor's feedback on a family challenge
                    notices.banner = "🧑‍🏫 ${bi("appViewModel.tutorRepliedFamilyAnswer")}"
                } else if (!questions.onReply(ev)) chat.onReply(ev) // tq_…: a check's or a question's feedback
            }
            is BridgeEvent.ModulePublished -> {
                notices.banner = "🎉 ${ev.note ?: inBase("appViewModel.newDrillUnlocked", "title" to (ev.title ?: ev.id))}"
                clips.refresh()
                game.sync(reloadFirst = true) // a tutor quest may have arrived
            }
            is BridgeEvent.AppUpdate -> update.check()
            is BridgeEvent.VoiceUpdated -> clips.refresh()
            is BridgeEvent.ScenePublished -> {
                val what = if (ev.story) bi("appViewModel.newStory") else bi("appViewModel.newScene")
                // a happening's new variant is news only with the tutor's note (it plays when it comes)
                if (!ev.removed && (!ev.variant || ev.note != null)) notices.banner = "${ev.emoji ?: if (ev.story) "📖" else "📍"} ${ev.note ?: "$what: ${ev.title ?: ev.id}"}"
                refresh()
            }
            is BridgeEvent.PackPublished -> {
                notices.banner = "📚 ${ev.note ?: "${bi("homeScreen.newWords")}: ${ev.title ?: ev.id}"}"
                refresh()
            }
            // the tutor wrote something to read (or took it back): the reading corner has it
            is BridgeEvent.ReadingPublished -> {
                if (!ev.removed) notices.banner = "📖 ${ev.note ?: "${bi("readings.newReading")}: ${ev.title ?: ev.id}"}"
                launchSafely { bridge?.let { readings.reload(it) } }
            }
            // the tutor gave homework for a unit of the school (or took it back): the unit has it
            is BridgeEvent.WorksheetPublished -> launchSafely {
                bridge?.let { school.reload(it) }
                if (!ev.removed) notices.banner = "📝 ${ev.note ?: "${bi("school.newHomework")}: ${ev.title ?: ev.id}"}"
            }
            // the tutor kept a rule it explained: the learner met it (in the chat), so its page is in the book now
            is BridgeEvent.GrammarPublished -> launchSafely {
                bridge?.let { grammar.reload(it) }
                val met = grammar.meet(listOf(ev.id)).isNotEmpty()
                grammar.announced(ev.id)
                notices.banner = ev.note?.let { "📖 $it" }
                    ?: if (met || !ev.extended) grammar.newPage(ev.id) else "📖 ${bi("grammar.tutorAdded")}: ${grammar.page(ev.id)?.target ?: ev.title ?: ev.id}"
            }
            // the tutor put a card on the cheat sheet (or took one back): the sheet has it at once, marked new
            is BridgeEvent.CardPublished -> launchSafely {
                bridge?.let { cheat.reload(it) }
                if (!ev.removed) cheat.published(ev.title, ev.note)
            }
            is BridgeEvent.PartnerChallenge -> {
                family.refresh()
                notices.banner = "${ev.emoji} ${Family.fromLabel(ev.from, ev.fromSl)}: ${ev.text}"
            }
            is BridgeEvent.PermissionRequest ->
                // Prompts replayed after a reconnect may already be answered in the terminal.
                if (System.currentTimeMillis() - ev.at < 5 * 60_000) permission = ev
            is BridgeEvent.WordsAdded -> {
                words.onWordsAdded(ev.itemIds)
                refresh() // the review counts grow
            }
            is BridgeEvent.LexiconUpdated -> words.onLexiconUpdated(ev.word)
            // a word asked for from its card got its meaning: the card shows it added, else a quiet notice
            is BridgeEvent.WordRequestDone -> words.onWordRequestDone(ev)
            is BridgeEvent.SentenceExplained -> sentences.onExplained(ev.sentence)
            // the tutor wrote how someone who joined the village is introduced: it plays from now on, instead of the template
            is BridgeEvent.ArrivalPublished -> launchSafely {
                villagers.reloadArrivals()
                ev.note?.let { notices.banner = "🧳 $it" }
            }
            is BridgeEvent.TownGuest -> {
                // a guest's visit: the village applies it (not during a visit of one's own: then when home again)
                notices.banner = "🧳 ${TownGuests.title(ev)}"
                game.sync()
                towns.load() // Friends' badge on Home and the village (FriendsNews), and Friends itself
            }
            is BridgeEvent.TownQuestion -> {
                // a guest's question to answer, or the answer to the learner's: in Friends, and on its own screen
                notices.banner = "${TownQuestions.title(ev)}: ${ev.text}"
                questions.onEvent(ev)
            }
            // a parent's weekly note of a linked town's progress: a notice (a notification while the app is closed)
            is BridgeEvent.TownProgressNote -> notices.banner = "📈 ${ev.text()}"
            // a child's link that waits for the parent, or how it went: a notice, and Friends shows it as it is now
            is BridgeEvent.TownLink -> {
                Towns.linkBanner(ev)?.let { notices.banner = it }
                towns.load()
            }
        }
    }

    /**
     * Where the learner is, for the shadow tutor's turns ("where the learner is", plan 5): named here, as R8 shortens the
     * screens' class names in a release build.
     */
    private fun whereNow(): String = when (val s = screen) {
        Screen.Home -> "Home"
        Screen.Village -> "the village"
        is Screen.Review -> if (s.polish) "polishing rusty words" else "a review"
        is Screen.Player -> "an exercise (module ${s.module.id})"
        is Screen.GrammarPractice -> "the grammar book's practice (${s.page})"
        is Screen.Scene -> "a scene (${s.id})"
        is Screen.Challenge -> "a village challenge"
        is Screen.Packs, is Screen.PackLearn -> "the word packs"
        is Screen.Readings, is Screen.ReadAloud -> "the reading corner"
        Screen.Talk -> "a role-play"
        is Screen.Visit, is Screen.VisitScene, is Screen.VisitRequest -> "a visit to another town"
        is Screen.TownProgress -> "a linked town's progress"
        is Screen.School, is Screen.CourseUnit, is Screen.UnitIntro, is Screen.Sheet -> "the school"
        is Screen.Numbers, is Screen.NumbersRun -> "the numbers module (🔢 Števila)"
        is Screen.Progress -> "the progress screen (📊 Napredek)"
        is Screen.MyWords -> "the learner's words (🔤 Moje besede)"
        is Screen.GrammarBook -> "the grammar book's index (📖 Slovnica)"
        is Screen.Settings -> "the settings"
        is Screen.Villagers, is Screen.Villager -> "the village's people"
        is Screen.Friends -> "the linked towns (🤝 Prijatelji)"
        is Screen.Treasure -> "the treasure map"
        else -> "the app"
    } + if (chat.isOpen) ", the tutor chat open" else ""

    /** Shows the app in [p] from now on (the settings' languages page); null follows the learner profile again. */
    fun chooseLangPair(p: LangPair?) {
        languages.choose(p)
        langPairChosen = p
        showLanguages()
    }

    /**
     * The profile names another learner, or another gender (the setup asked): the content speaks to them from now on. The
     * bundled culture pack and festivals' words are read again for them; what the bridge serves comes rendered for them.
     */
    private fun relearn() {
        learner = learners.apply()
        si.lanisce.lani.game.culture.Cultures.forget()
        si.lanisce.lani.game.FestivalPacks.forget()
        culture = cultures.apply()
    }

    /** The learner's pair applied; what shows ([langPair]) is a visit's while one is on. */
    private fun showLanguages() {
        languages.apply()
        langPair = L10n.pair
    }

    fun answerPermission(allow: Boolean) {
        val p = permission ?: return
        permission = null
        launchSafely { bridge?.permission(p.requestId, allow) }
    }

    /** Resume, reconnect: send queued writes and unsent village changes now. */
    fun flushOutbox(): Job = viewModelScope.launch {
        sync.flush()
        game.pushIfDirty()
    }

    /** The app is on screen again: live events resume where they stopped. */
    fun onForeground() {
        foreground = true
        if (bridge != null) events.start()
        networkBack.start()
        commitRoadRatings()
        shadow.onForeground() // a digest when the app was away two hours or more
        data.onForeground() // the data repository pulled (git connected only)
        live.onForeground() // the live tutor's long poll of the bridge's inbox (when it's on)
    }

    /**
     * The app left the screen. The event stream stops, so the background check notifies what comes
     * next (a live stream marks events as seen, and they would never be notified); speech stops.
     */
    fun onBackground() {
        foreground = false
        events.stop()
        networkBack.stop()
        speaker.stop()
        update.onBackground()
        shadow.onBackground()
        live.onBackground() // its poll stops; the WorkManager check takes over (tutor/live/TutorWork)
        devUpload.onBackground() // the developer's data to the node (developer setting on, at most every 30 minutes)
        data.onBackground() // the phone's writes pushed, the day's backup (each only when set up)
    }

    /**
     * A notification (or the widget, a lani:// link) was tapped: its target opens on the way the app has to it from Home
     * (docs/plans/05-built/navigation.md, "Notifications and links"): Home, the place it is in (the village for a scene, the
     * school for a unit, Friends for a town's question or progress), the target; Back walks that way home. Once connected;
     * never over a run with answers (the news shows in the app anyway); a visit ends first, the link leads into the
     * learner's own village.
     */
    fun open(link: DeepLink) {
        // back from a sign-in in the browser (lani://tutor): where it began is on screen already (the tutor's settings, the
        // hosted onboarding's tutor step, the developer's panel), or the app starts over and is where it was (Nav's restore)
        if (link is DeepLink.Settings && (tutorSheetOpen || shadow.panelOpen || screen == Screen.Settings(link.section) || (bridge == null && link.section == "tutor"))) return
        if (bridge == null) {
            pendingLink = link
            return
        }
        pendingLink = null
        if (link == DeepLink.Chat) return chat.open() // a sheet, fine over any screen
        // an invitation opened from a link: kept for Friends' Accept dialog (also while a run goes on: then it waits there)
        if (link is DeepLink.Invitation) towns.receive(link.text)
        if (runBusy) return // leaving would lose the answers; the news shows in the app anyway
        // the reviews asked for (the widget's count of a while ago), none due now: the learner stays where they are
        if (link == DeepLink.Review && content.dashboard != null && content.deck(null)?.dueCards.isNullOrEmpty()) return
        closeSheets()
        nav.reset(Screen.Home) // a visit in the stack ends with it (onLeft): the link leads into the learner's own village
        when (link) {
            DeepLink.Chat, DeepLink.Home -> Unit
            DeepLink.Talk -> openTalk()
            DeepLink.Village -> openVillage()
            DeepLink.Packs -> openPacks()
            DeepLink.Readings -> openReadings()
            DeepLink.Review -> startReview()
            is DeepLink.Scene -> {
                openVillage()
                openScene(link.id, link.focus)
            }
            is DeepLink.Module -> openModule(link.id)
            is DeepLink.Family -> {
                family.refresh() // the challenge may be newer than the list
                openFamily(link.challengeId)
            }
            is DeepLink.Question -> {
                openFriends()
                openQuestion(link.key, link.asked)
            }
            is DeepLink.TownProgress -> {
                towns.load() // the town's row (its name, its sharing) for the screen
                openFriends()
                openTownProgress(link.town)
            }
            is DeepLink.School -> {
                openSchool()
                link.unit?.takeIf { school.unit(it) != null }?.let { openUnit(it) }
            }
            DeepLink.Friends, is DeepLink.Invitation -> openFriends()
            // the tutor's new grammar page: the book open at it (the page over the book's index)
            is DeepLink.Grammar -> {
                openGrammarBook()
                if (grammar.page(link.id) != null) grammar.show(link.id)
            }
            is DeepLink.Settings -> openSettings(link.section)
        }
    }

    /** Every sheet over the screens (the chat, the cheat sheet, a grammar page, a word's card …) closed: a link leads elsewhere. */
    private fun closeSheets() {
        chat.close()
        cheat.close()
        grammar.close()
        sentences.close()
        words.close()
        formsCards.closeView()
        notebook = null
    }

    /**
     * Saves a review; [language]: another language's deck (null: the home language's, whose rusty words it polishes);
     * [answers]: what each card was answered with ([si.lanisce.lani.ui.reviewAnswer]), for the node's log of recent answers;
     * [from]: where in the app ([si.lanisce.lani.data.ReviewFrom]), so the tutor knows where nothing is typed.
     */
    fun finishReview(
        results: List<Pair<String, Int>>, minutes: Int, language: String? = null, forms: kotlinx.serialization.json.JsonArray? = null,
        answers: kotlinx.serialization.json.JsonArray? = null, from: String, counting: kotlinx.serialization.json.JsonArray? = null,
        formsCards: kotlinx.serialization.json.JsonArray? = null,
    ): Job {
        if (language == null) content.reviewed(results)
        return sync.submitLater(Writes.reviews(results, minutes, language, forms, answers = answers, from = from, counting = counting, formsCards = formsCards))
    }

    /** Sends Jan's answer to the tutor (and the family page); answering pays wisdom in the village. */
    fun answerFamily(id: String, text: String, spoken: Boolean, done: (Boolean) -> Unit) = launchSafely {
        try {
            family.answer(id, text, spoken)
        } catch (e: Exception) {
            done(false)
            throw e
        }
        game.rewardFamily()
        done(true)
    }

    /** Saves the words (SR items on the node) and credits the practice to the village. */
    fun finishPack(pack: Pack, results: List<Pair<String, Int>>, exercises: List<Exercise>, verdicts: List<Verdict>, minutes: Int) {
        game.rewardPractice(exercises, verdicts)
        packs.save(pack, results, minutes)
        scenes.learned(pack.id, results.map { it.first }) // a scene shows them learned before the node has them
        content.learnedWords(pack.id, pack.words, results) // and those answered right count at once (a building's words too)
    }

    // --- "Znanje gradi · Knowledge builds": an upgrade asks for the building's words (companion/GAME.md, "Upgrades") -----

    /** The learner's words as the village's upgrades see them, made once per dashboard. */
    private var wordBook: Pair<si.lanisce.lani.data.Dashboard, si.lanisce.lani.game.WordBook>? = null

    /**
     * What the upgrade of building [id] of [s] asks for of its words now ([si.lanisce.lani.game.BuildingWords]: its
     * scene's things, then its packs', as far as the phone has them), with how each stands. Null asks for none: at the
     * top level, before the learner's words are loaded (they aren't known then), and on a visit (the village waits).
     */
    fun upgradeWords(s: si.lanisce.lani.game.GameState, id: String): si.lanisce.lani.game.UpgradeWords? {
        if (VisitWorld.active) return null
        val d = content.dashboard ?: return null
        val book = wordBook?.takeIf { it.first === d }?.second ?: si.lanisce.lani.game.WordBook.of(d).also { wordBook = d to it }
        return si.lanisce.lani.game.BuildingWords.forUpgrade(s, id, scenes.all, scenes.packs, book)
    }

    /** The packs building [id]'s words come from, on the phone (fetched once; offline what's cached stays). */
    fun loadBuildingWords(id: String) = launchSafely {
        val s = game.state ?: return@launchSafely
        val b = s.buildings.firstOrNull { it.id == id } ?: return@launchSafely
        scenes.ensurePacks(si.lanisce.lani.game.BuildingWords.packsOf(s, b, scenes.all))
    }

    /** Each upgrade's practice run is a screen of its own ([Screen.UpgradeWords.n]). */
    private var upgradeRuns = 0

    /**
     * The building whose card opens again after its words' run, where the run was started from: the village, or the
     * building's scene ([leaveRun]); null: none.
     */
    private var buildingCard: String? = null

    /** The building card to open as the village or a scene shows (once), see [practiseUpgrade]. */
    fun takeBuildingCard(): String? = buildingCard.also { buildingCard = null }

    /** Whether the village opens the ages' sheet as it shows (once): asked for from a building's card in a scene. */
    private var agesCard = false

    fun takeAgesCard(): Boolean = agesCard.also { agesCard = false }

    /** To the village, its ages' sheet open: the way to the age a building's next level waits for (its card in a scene). */
    fun openVillageAges() {
        agesCard = true
        openVillage()
    }

    /**
     * "🎯 Vadi jih zdaj · Practise them now" on building [id]'s card: a short run of exactly the words its upgrade still
     * asks for ([si.lanisce.lani.game.BuildingWords.run]: new ones learned, met and rusty ones reviewed); afterwards
     * the card opens again where it was opened (the village, or the building's scene), the upgrade one tap away. Also
     * again from the run's own end, for what is still missing.
     */
    fun practiseUpgrade(id: String) = launchSafely {
        val s = game.state ?: return@launchSafely
        val b = s.buildings.firstOrNull { it.id == id } ?: return@launchSafely
        scenes.ensurePacks(si.lanisce.lani.game.BuildingWords.packsOf(s, b, scenes.all))
        val w = upgradeWords(s, id) ?: return@launchSafely
        val run = si.lanisce.lani.game.BuildingWords.run(w)
        if (run.isEmpty) {
            notices.banner = "✅ ${bi("appViewModel.knowAllTheseWords")}"
            return@launchSafely
        }
        buildingCard = id
        startRun(Screen.UpgradeWords(id, w.toLevel, run, ++upgradeRuns))
    }

    /**
     * An upgrade's practice run ended: its new words saved like a pack's ([learned]: pack → word id → quality) and its
     * reviews like a review's ([reviewed]: card id → quality, with what each was answered with: [answers]), both counted
     * at once, and the answers paid to the village.
     */
    fun finishUpgradeWords(
        result: si.lanisce.lani.game.RunResults, run: si.lanisce.lani.game.UpgradeRun, exercises: List<Exercise>, verdicts: List<Verdict>, minutes: Int,
        answers: kotlinx.serialization.json.JsonArray? = null,
    ) {
        game.rewardPractice(exercises, verdicts)
        for ((pack, words) in result.learned) {
            packs.saveWords(pack, words, minutes)
            scenes.learned(pack, words.map { it.first })
            content.learnedWords(pack, run.learn.filter { it.pack == pack }.mapNotNull { it.learn }, words)
        }
        if (result.reviewed.isNotEmpty()) finishReview(result.reviewed, minutes, answers = answers, from = si.lanisce.lani.data.ReviewFrom.UPGRADE)
    }

    /**
     * The upgrade of building [id], from its words' run: done, the village shows it going up (its card doesn't open
     * again); false when it still can't be done (the card shows why).
     */
    fun upgradeNow(id: String): Boolean {
        if (!game.upgrade(id)) return false
        buildingCard = null
        nav.go(Screen.Village) // where the run began, or the village under the building's scene: it goes up there
        return true
    }

    // --- the road (companion/README.md, "Im Auto · In the car") ------------------------------------

    /**
     * "🚗 Pripravi za pot · Get ready for the road": the cards due, the words of the packs under way, the scenes' dialogs,
     * the stories and the drills, with only the clips the voice store has; then, in the background (road/RoadPrepService),
     * the clips downloaded as they are (`?count=0` asks the node not to count them, so nothing is voiced again for them)
     * and the prompts rendered by the phone. [mini]: QA's small library (app/QaHooks, "road:mini").
     */
    fun prepareRoad(mini: Boolean = false) {
        viewModelScope.launch {
            val api = prefs.config()?.let { si.lanisce.lani.data.ClipsApi(it) }
            road.start(
                gather = { gatherRoad(api) },
                youSay = { inBase("road.youSay", "text" to it) },
                cache = clips.dir,
                drillWords = si.lanisce.lani.road.RoadDrills.Words({ inBase("road.buildSay", "text" to it) }, { inBase("road.buildAdd", "text" to it) }),
                mini = mini,
                quizWords = quizWords(),
            )
        }
    }

    /**
     * What the car's quiz says (road/RoadQuiz): its questions' prompts in the learner's base language, its fixed phrases
     * in the target language ("Ena.", "Prav!", "Sedem od desetih.") with the base's as the fallback.
     */
    private fun quizWords(): si.lanisce.lani.road.QuizWords {
        val target = L10n.pair.target.code
        fun both(key: String) = inTarget(key) to inBase(key)
        fun number(n: Int): String = if (n == 0) inTarget("road.quizNone")
            else si.lanisce.lani.data.Drills.number(n, target)?.said(target)?.takeIf { it.isNotBlank() } ?: "$n"
        return si.lanisce.lani.road.QuizWords(
            whatMeans = inBase("road.quizWhatMeans"),
            howSay = { inBase("road.quizHowSay", "text" to it) },
            youWant = { inBase("road.quizYouWant", "text" to it) },
            whatAnswer = inBase("road.quizWhatAnswer"),
            whichRight = inBase("road.quizWhichRight"),
            numbers = listOf("road.quizOne", "road.quizTwo", "road.quizThree", "road.quizFour").map(::both),
            right = both("road.quizRight"),
            wrong = both("road.quizWrong"),
            score = { n -> inTarget("road.quizScore", "n" to number(n)).replaceFirstChar { it.titlecase() } to inBase("road.quizScore", "n" to n) },
        )
    }

    /**
     * What QA asks of the road in a debug build (app/QaHooks): "mini" gets a small library ready (a couple of items of
     * each kind), "car" browses and plays the sessions as Android Auto does (road/RoadCarCheck, its findings in the log).
     */
    fun qaRoad(what: String) {
        when (what) {
            "mini" -> prepareRoad(mini = true)
            "car" -> si.lanisce.lani.road.RoadCarCheck.run(getApplication())
        }
    }

    /**
     * QA's "season:<day>" (a debug build, app/QaHooks.kt): the landmarks with a season stand as on QA's day now (the dev
     * bridge's copy; "season:" as today again), without a line in the chronicle, and the village shown.
     */
    fun qaSeason() {
        game.apply { si.lanisce.lani.game.ProjectSeasons.update(it, java.time.LocalDate.now(), System.currentTimeMillis(), tell = false) }
        openVillage()
    }

    /**
     * QA's "step:<project>/<n>" (a debug build, app/QaHooks.kt): the village (the dev bridge's copy) ready for that step
     * today, and the village shown, where QA opens the projects sheet at it.
     */
    fun qaStep(project: String, n: Int) {
        game.apply { si.lanisce.lani.app.QaHooks.readyStep(it, project, n, java.time.LocalDate.now()) }
        openVillage()
    }

    /**
     * QA's "ageup:<age>" (a debug build, app/QaHooks.kt): the village (the dev bridge's copy) grown to that age today, and the
     * village shown, where its moment plays (companion/GAME.md, "Ages and their celebrations").
     */
    fun qaAgeUp(age: si.lanisce.lani.game.Age) {
        game.apply { si.lanisce.lani.app.QaHooks.grownTo(it, age, java.time.LocalDate.now(), System.currentTimeMillis()) }
        openVillage()
    }

    /**
     * QA's "event:<what>" (a debug build, app/QaHooks.kt): the village (the dev bridge's copy) with that event on from now,
     * or the one on ended ("won", "lost"), or none (""), and the village shown, its people reacting (companion/GAME.md
     * "People react").
     */
    fun qaEvent(what: String) {
        game.apply { si.lanisce.lani.app.QaHooks.eventState(it, what, System.currentTimeMillis()) }
        openVillage()
    }

    /**
     * QA's "hearths:<what>" (a debug build, app/QaHooks.kt): the village (the dev bridge's copy) as [QaHooks.hearthsState]
     * makes it (an age's village, short of wood, lit again, a feast, the forge cold), and the village shown (companion/GAME.md
     * "Hearths").
     */
    fun qaHearths(what: String) {
        game.apply { si.lanisce.lani.app.QaHooks.hearthsState(it, what, java.time.LocalDate.now(), System.currentTimeMillis()) }
        openVillage()
    }

    /** QA's "school" (app/QaHooks): the dev village ready for the classroom under the linden, and the school open. */
    fun qaSchool() {
        game.apply { si.lanisce.lani.app.QaHooks.readySchool(it, System.currentTimeMillis()) }
        openSchool()
    }

    /**
     * QA's "sheet:<unit>/<sheet>" (app/QaHooks): the dev village ready for the class (as "school"), and sheet [sheet] of unit
     * [unit] played at once ("test": the unit's test), as its button in the unit starts it.
     */
    fun qaSheet(unit: String, sheet: String) {
        if (school.unit(unit) == null) return
        game.apply { si.lanisce.lani.app.QaHooks.readySchool(it, System.currentTimeMillis()) }
        val kind = if (sheet == "test") si.lanisce.lani.app.SheetKind.TEST else si.lanisce.lani.app.SheetKind.WORKSHEET
        startSheet(unit, kind, sheet)
    }

    /**
     * QA's "school:<unit>" (app/QaHooks): the dev village ready for the class (as "school"), unit [id] as never begun and
     * its level open, and the school open, where QA goes to the unit through its level.
     */
    fun qaSchoolUnit(id: String) {
        val u = school.unit(id) ?: return
        val before = school.course?.playable(u.level)?.map { it.id }?.takeWhile { it != u.id }.orEmpty()
        game.apply { si.lanisce.lani.app.QaHooks.readyUnit(it, u.language, u.id, u.level, java.time.LocalDate.now(), System.currentTimeMillis(), before) }
        openSchool()
    }

    /**
     * QA's "school:reveal" (app/QaHooks): the dev village's copy with the school standing, A1 done and A2 begun, and the
     * school open (its levels opened as it opens): a level a bit at a time.
     */
    fun qaSchoolReveal() {
        val course = school.course ?: return
        game.apply { si.lanisce.lani.app.QaHooks.readyReveal(it, course, java.time.LocalDate.now(), System.currentTimeMillis()) }
        openSchool()
    }

    /**
     * QA's "build:<type>" (a debug build, app/QaHooks.kt): the village (the dev bridge's copy) ready to build one (its
     * stores full, a free plot: the next age's when its own are taken) and shown, where the plot chooser opens for it.
     */
    fun qaBuild() {
        game.apply { si.lanisce.lani.app.QaHooks.readyBuild(it, si.lanisce.lani.game.GameEngine.attributes(it).caps) }
        openVillage()
    }

    /**
     * What QA asks of the offline audio in a debug build (app/QaHooks, "audio:<what>"): "prefetch" the day's plan made and its
     * clips got now, "voice" the offline voice downloaded, "say:<text>" / "slow:<text>" a text said as a 🔊 says it
     * ("say:<text>|<voice>|<person>": as that person says it in that voice, as a villager's line),
     * "revoice:<text>|<url>" its narrator clip re-voiced on the phone's side to <url> (else one the node hasn't got), as the
     * node's re-voice swaps it ([Clips.qaRevoice]), "refresh" the node's index again.
     */
    fun qaAudio(what: String) {
        when {
            what == "prefetch" -> offlineAudio.planAndFetch(::dayGather)
            what == "voice" -> offlineAudio.downloadVoice()
            what.startsWith("say:") -> what.removePrefix("say:").split('|').let { p ->
                speaker.say(p[0], voiceName = p.getOrNull(1)?.ifBlank { null } ?: si.lanisce.lani.data.Clips.FEMALE, person = p.getOrNull(2)?.ifBlank { null })
            }
            what.startsWith("slow:") -> speaker.say(what.removePrefix("slow:"), slow = true)
            what.startsWith("revoice:") -> what.removePrefix("revoice:").split('|', limit = 2).let { clips.qaRevoice(it[0], it.getOrNull(1)?.takeIf(String::isNotBlank)) }
            what == "refresh" -> (visitClips ?: clips).refresh()
            // the phone's text-to-speech as if it had no voice for any language (a tablet without the town's), and back
            what == "tts:off" -> speaker.qaNoTts = true
            what == "tts:" -> speaker.qaNoTts = false
            // a visit's note and offer of the offline voice to come again (the declines forgotten)
            what == "visitvoice:reset" -> visitVoice.qaReset()
            // this visit's note may come again, the declines kept: the 🔇 note after a "Ne zdaj"
            what == "visitvoice:again" -> visitVoice.qaAgain()
            // the offline voice of a language off the phone ("voice:remove:sl"), as the settings' 🗑️ takes it
            what.startsWith("voice:remove:") -> offlineAudio.removeFor(what.removePrefix("voice:remove:"))
        }
    }

    /**
     * QA's "request:<ids>" (a debug build, app/QaHooks.kt): the village (the dev bridge's copy) with Mojca's request of those
     * cards ([QaHooks.withRequest]), and its challenge started as from its bubble.
     */
    fun qaRequest(cardIds: List<String>) {
        game.apply { si.lanisce.lani.app.QaHooks.withRequest(it, cardIds, java.time.LocalDate.now(), System.currentTimeMillis()) }
        startQuest(si.lanisce.lani.app.QaHooks.REQUEST)
    }

    /** QA's "review" hook (app/QaHooks): a review of up to five of the learner's cards, due or not. */
    fun qaReview() {
        val cards = content.dashboard?.pool.orEmpty().take(5)
        if (cards.isNotEmpty()) startRun(Screen.Review(cards, run = ++reviewRuns))
    }

    /**
     * QA's "counting" hook (app/QaHooks): today's counting cards asked for now, then a review of a few of them, due or not
     * (app/CountingController.forQa); "counting:type" before it types their steps.
     */
    fun qaCounting() {
        launchSafely {
            val b = bridge ?: return@launchSafely
            content.dashboard?.let { forms.reload(b, it.pool) }
            val cards = counting.forQa(b)
            if (cards.isNotEmpty()) startRun(Screen.Review(cards, run = ++reviewRuns))
        }
    }

    /**
     * QA's forms card hooks (app/QaHooks): "review" a review of up to three forms cards, due or not ([word]: that word's,
     * made on demand first; app/FormsCardController.forQa); "unit" the unit [word]'s intro done, as "⏭ Poznam to" ends it
     * (its pages met, the node told: the cards it opens rows of come back); "word" the word's card open.
     */
    /** QA's "word:<word>|<line>|<en>": that word's card, as a tap on it in that line (meant [en]) opens it in a scene. */
    fun qaWord(what: String) {
        val parts = what.split('|')
        val w = parts[0].trim().takeIf { it.isNotEmpty() } ?: return
        words.open(si.lanisce.lani.app.WordQuery(w, parts.getOrNull(1)?.trim().orEmpty(), parts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }, si.lanisce.lani.data.Words.SCENE))
    }

    fun qaFormsCard(what: String, word: String) {
        when (what) {
            "unit" -> school.unit(word)?.let { u -> school.introProgress(u, u.intro?.steps?.size ?: 0, done = true) }
            "word" -> words.open(si.lanisce.lani.app.WordQuery(word, "", null, si.lanisce.lani.data.Words.SCENE))
            else -> launchSafely {
                val b = bridge ?: return@launchSafely
                content.dashboard?.let { forms.reload(b, it.pool) }
                val cards = formsCards.forQa(b, word.ifBlank { null })
                if (cards.isNotEmpty()) startRun(Screen.Review(cards, run = ++reviewRuns))
            }
        }
    }

    /** A word's forms card reviewed now ("▶ Vadi zdaj · Practise now" in the word's overview, after "🃏 Vadi oblike"). */
    fun playFormsCard(cardId: String) {
        if (busyRun()) return
        val card = content.dashboard?.formsCards?.firstOrNull { it.id == cardId } ?: return
        formsCards.closeView()
        words.close()
        startRun(Screen.Review(listOf(card), run = ++reviewRuns))
    }

    /**
     * QA's "counting:intro" (app/QaHooks): the dev village's copy ready for the class (as "school"), the unit stevila as
     * never begun, and its intro open, where QA skips it ("⏭ Poznam to · I know this"): its end turns counting on.
     */
    fun qaCountingIntro() {
        game.apply { si.lanisce.lani.app.QaHooks.readyCounting(si.lanisce.lani.app.QaHooks.readySchool(it, System.currentTimeMillis())) }
        startIntro(si.lanisce.lani.data.Counting.UNIT)
    }

    /**
     * QA's "forms" hook (app/QaHooks): a review of up to five of the home deck's word cards that have a form reached, due
     * or not, each asking one (companion/GAME.md, "A word's forms"); their forms asked of the bridge first.
     */
    fun qaForms() {
        launchSafely {
            val b = bridge ?: return@launchSafely
            val d = content.dashboard ?: return@launchSafely
            val words = d.pool.filter { si.lanisce.lani.data.WordFormsWire.lemmaOf(it) != null }
            forms.reload(b, words)
            val ask = forms.questions()
            val cards = words.filter { ask(it) != null }.take(5)
            if (cards.isNotEmpty()) startRun(Screen.Review(cards, run = ++reviewRuns))
        }
    }

    private suspend fun gatherRoad(api: si.lanisce.lani.data.ClipsApi?): si.lanisce.lani.road.RoadInputs {
        val b = bridge ?: throw java.io.IOException(bi("road.needsNode"))
        val a = api ?: throw java.io.IOException(bi("road.needsNode"))
        val gather = si.lanisce.lani.road.RoadGather
        val until = java.time.LocalDate.now().plusDays(gather.DUE_AHEAD_DAYS).toString()
        val cards = gather.cards(b.stateRaw(), until)
        val target = L10n.pair.target
        val words = mutableListOf<Pair<String, si.lanisce.lani.data.PackWord>>()
        for (id in gather.packOrder(runCatching { b.packs() }.getOrDefault(packs.list))) {
            if (words.size >= gather.MAX_WORDS) break
            val p = runCatching { b.pack(id) }.getOrNull() ?: continue
            if (p.language != target.code) continue
            words += p.words.filter { it.id !in p.learned }.map { id to it }
        }
        val index = Clips.parseIndex(a.index())
        // the car's drills: the node's (an older one has none: 404), else the app's own; those of a rule not introduced
        // to the learner yet are left out (the grammar book's pages, in the book's language)
        val drills = si.lanisce.lani.data.Drills.merge(si.lanisce.lani.data.Drills.bundled(target.code), runCatching { b.drills() }.getOrNull())
        val rules = drills.flatMap { d -> d.transforms.map { it.rule } + d.rapid.mapNotNull { it.rule } + d.builds.flatMap { it.rules } }.toSet()
        val notYet = if (target.code != grammar.language) emptySet() else rules.filter { grammar.mastery(it) == si.lanisce.lani.game.Mastery.NOT_YET }.toSet()
        return si.lanisce.lani.road.RoadInputs(
            cards = cards, words = words, scenes = scenes.all, villagers = villagers.all, profiles = clips.profiles,
            index = index, level = levelIn(target.code), target = target, base = L10n.pair.base, drills = drills, notYet = notYet,
            quiz = gatherQuiz(b, target),
        )
    }

    /**
     * What the car's quiz asks (road/RoadQuiz): the learner's cards with their schedule, their words a dialog's turn
     * tests, the form each word asks (the forms asked of the bridge first, a short while), the grammar book's masteries
     * and its choice exercises (the modules', the tent's, the letters'), and what play counted today. In another language
     * than the book's, no forms, rules or exercises.
     */
    private suspend fun gatherQuiz(b: si.lanisce.lani.data.Bridge, target: si.lanisce.lani.l10n.Lang): si.lanisce.lani.road.QuizInputs {
        val d = content.dashboard
        val pool = d?.pool.orEmpty()
        val home = target.code == grammar.language
        if (home) {
            kotlinx.coroutines.withTimeoutOrNull(20_000) { forms.reload(b, pool.filter { si.lanisce.lani.data.WordFormsWire.lemmaOf(it) != null }) }
            kotlinx.coroutines.withTimeoutOrNull(20_000) { content.loadModuleExercises() }
        }
        val seed = System.currentTimeMillis()
        val exercises = if (!home) emptyList() else (
            content.moduleExercises().map { it.second } +
                si.lanisce.lani.game.TentMove.exercises(seed, false) + si.lanisce.lani.game.Surprises.onRules(seed)
            ).filterIsInstance<si.lanisce.lani.data.Exercise.Choice>().filter { it.grammar != null }
        return si.lanisce.lani.road.QuizInputs(
            cards = pool,
            words = if (home) dialogWords.words() else si.lanisce.lani.game.MyWords.NONE,
            forms = if (home) forms.chosen(pool) else emptyMap(),
            mastery = if (home) grammar.pages.mapNotNull { p -> grammar.mastery(p.id)?.let { p.id to it } }.toMap() else emptyMap(),
            exercises = exercises,
            played = game.state?.playReviews,
            dialogWords = d?.features?.contains(si.lanisce.lani.app.DialogWordsController.FEATURE) == true,
        )
    }

    /**
     * Ratings pressed in the car and the quiz's answers that the road's service didn't hand to the outbox yet go now, and
     * out to the node; what the quiz's answers left for the app is counted ([takeQuiz]).
     */
    private fun commitRoadRatings() {
        viewModelScope.launch {
            val (n, handOffs) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val outbox = si.lanisce.lani.data.Outbox(getApplication<Application>())
                val quiz = runCatching { road.store.commitQuiz(outbox, road.store.readLibrary(), "🚗 ${bi("road.quiz")}") }.getOrDefault(0)
                (runCatching { road.store.commit(outbox) }.getOrDefault(0) + quiz) to runCatching { road.store.takeHandOffs() }.getOrDefault(emptyList())
            }
            takeQuiz(handOffs)
            if (n > 0) sync.flush()
        }
    }

    /**
     * What the car's quiz answered, for what the app keeps (road/RoadQuizCount, companion/GAME.md "Words met in play"):
     * each rule's answer in the grammar book as a dialog's pick counts it, each form asked in its card's record; the cards
     * whose schedule it changed today are play's for today (once a day), those it reviewed done for today on the phone.
     */
    private fun takeQuiz(handOffs: List<si.lanisce.lani.road.QuizHandOff>) {
        if (handOffs.isEmpty()) return
        val today = java.time.LocalDate.now().toString()
        for (h in handOffs) {
            for (r in h.rules) {
                val verdict = if (r.right) si.lanisce.lani.data.Grading.Verdict.CORRECT else si.lanisce.lani.data.Grading.Verdict.WRONG
                grammar.answered(r.page, verdict, r.said.takeIf { r.right }, r.said, r.correct.takeIf { !r.right })
            }
            for (f in h.forms) forms.answeredInCar(f.card, f.key, f.right, f.repetitions, h.day)
            if (h.day != today) continue
            if (h.changed.isNotEmpty()) game.playReviewed(h.changed)
            if (h.reviewed.isNotEmpty()) content.reviewedInDialog(h.reviewed)
        }
    }

    // --- navigation (Nav.kt, docs/plans/05-built/navigation.md) ------------------------------------------------------

    /** The run on screen was started again from its own finish screen ("once more"): it skips its intro. */
    var again by mutableStateOf(false)
        private set

    /**
     * A run on screen has answers that leaving it now would cut short (ui/PlayerScreen's ExerciseRun says so): what would
     * open elsewhere from a sheet over it (a link, the chat's role-play, a grammar page's practice) waits until it's done.
     */
    var runBusy by mutableStateOf(false)

    /** [runBusy], and the learner told why nothing opens: the exercise first. */
    private fun busyRun(): Boolean {
        if (runBusy) notices.banner = "🏁 ${bi("nav.finishRunFirst")}"
        return runBusy
    }

    /**
     * Back: the screen below the one on view (the stack's, Nav.kt), as the system's Back does when nothing on the screen
     * takes it first; nothing at the root (the system's Back leaves the app there).
     */
    fun back() {
        nav.back()
    }

    init {
        nav.onRemoved = ::left
        // the stack saved for after the process's death (the screens that can be opened again from their ids)
        viewModelScope.launch {
            snapshotFlow { nav.entries }.collect {
                when (it.first().screen) {
                    Screen.Home -> savedState[NAV_KEY] = nav.saved()
                    // the setup again (another node, the phone's own): what was open belongs to the data before
                    Screen.Setup -> savedState.remove<String>(NAV_KEY)
                    else -> Unit
                }
            }
        }
        // a screen on view again (opened, or Back to it): what it shows read anew
        viewModelScope.launch {
            var before: Screen? = null
            snapshotFlow { nav.entries.last() }.distinctUntilChanged().collect { e ->
                // the village left: its construction plays once, not again when Back leads there later
                if (before == Screen.Village && e.screen != Screen.Village) game.clearJustBuilt()
                before = e.screen
                shown(e.screen)
            }
        }
    }

    /** What a screen reads anew when it comes on view (opened, or Back to it). */
    private fun shown(s: Screen) {
        when (s) {
            Screen.Home, Screen.Packs -> if (reloading?.isActive != true) refresh()
            // the role-plays (also when the app opens again on Talk after the process ended)
            Screen.Talk -> talk.load()
            Screen.Village -> game.sync()
            Screen.Progress -> progress.load()
            Screen.Readings -> launchSafely { bridge?.let { readings.reload(it) } } // the tutor may have written something new
            Screen.Villagers -> launchSafely { bridge?.let { villagers.reload(it) } } // the tutor may have brought someone new
            Screen.School -> school.touch()
            Screen.Friends -> friendsSeen()
            else -> Unit
        }
    }

    /** What a screen leaving the stack takes with it. */
    private fun left(s: Screen) {
        when (s) {
            // the visit is over: the learner's own pair, culture and voices again
            is Screen.Visit -> if (visits.visit != null) endVisit()
            is Screen.Treasure -> treasure.seen()
            is Screen.TownProgress -> townProgress.close()
            // what Friends showed is seen (its guests: not on the badge again)
            Screen.Friends -> friendsSeen()
            // a word's card and its forms opened from the list go with it
            Screen.MyWords -> {
                words.close()
                formsCards.closeView()
            }
            // a dialog left open doesn't wait for a later visit of another scene
            is Screen.Scene -> if (scenes.talk?.sceneId == s.id && scenes.talk?.step?.over != true) scenes.closeTalk()
            else -> Unit
        }
    }

    /** Opens a practice run over the screen on view (Back after it leads there); the last run's reward no longer shows. */
    private fun startRun(s: Screen, again: Boolean = false) {
        game.clearReward()
        closeSheets() // a grammar page's "🎯 Vadi" over the chat: the run on top, not under them
        this.again = again
        nav.go(s)
    }

    /**
     * Leaves a run that hasn't started (its intro's "Later"), or one that's done: back where it was started from. A
     * building's words run opens its card there again ([buildingCard]: the village's, or the building's scene's); anywhere
     * else it is dropped, so it doesn't open on a later visit.
     */
    fun leaveRun() {
        back()
        if (screen != Screen.Village && screen !is Screen.Scene) buildingCard = null
    }

    // --- the school (companion/GAME.md, "The school") ---------------------------------------------------------------

    /** "🏫 Šola · School": the course's levels and units; the levels opened since are kept first. */
    fun openSchool() {
        nav.go(Screen.School)
    }

    /** Unit [id] of the course: its intro, its worksheets, its test; Back leads where it was opened (Today, Napredek, the school). */
    fun openUnit(id: String) {
        if (school.unit(id) == null) return
        nav.go(Screen.CourseUnit(id))
    }

    /** Unit [id]'s guided introduction, where the learner left it. */
    fun startIntro(id: String) {
        if (school.unit(id)?.intro == null) return
        nav.go(Screen.UnitIntro(id))
    }

    /** Each sheet's run is a screen of its own ([Screen.Sheet.run]). */
    private var sheetRuns = 0

    /** Sheet [sheet] of unit [unit] ([kind]: a worksheet, the test, homework): its run, back to the unit after. */
    fun startSheet(unit: String, kind: si.lanisce.lani.app.SheetKind, sheet: String) {
        if (busyRun()) return
        startRun(Screen.Sheet(unit, kind, sheet, ++sheetRuns))
    }

    /**
     * "📚 Pouk · Lesson" in the class's scene (companion/GAME.md "The school"; [si.lanisce.lani.game.scene.SceneOffers]): a
     * unit begun goes on at its next step, as its main button does (its intro where it was left, the next worksheet, the
     * test: [School.lessonStep]); the unit suggested next opens at its page. Back comes to the scene.
     */
    fun startLesson(lesson: si.lanisce.lani.game.School.Lesson) {
        val unit = school.unit(lesson.unit) ?: return
        school.touch()
        val p = game.state?.let { si.lanisce.lani.game.School.progress(it, unit.language, unit.id) }
        when (val step = si.lanisce.lani.game.School.lessonStep(lesson, unit, p)) {
            si.lanisce.lani.game.School.Step.Intro -> startIntro(unit.id)
            is si.lanisce.lani.game.School.Step.Sheet -> startSheet(unit.id, si.lanisce.lani.app.SheetKind.WORKSHEET, step.id)
            si.lanisce.lani.game.School.Step.Test -> startSheet(unit.id, si.lanisce.lani.app.SheetKind.TEST, "test")
            si.lanisce.lani.game.School.Step.Done, null -> openUnit(unit.id)
        }
    }

    // --- the numbers (data/NumbersPractice.kt; companion/README.md "Numbers") -------------------------------------------

    /** "🔢 Števila · Numbers": its screen, open or closed (what opens it, one tap to the unit's intro). */
    fun openNumbers() {
        nav.go(Screen.Numbers)
    }

    /** Each numbers run is a screen of its own ([Screen.NumbersRun.run]). */
    private var numberRuns = 0

    /** A run of the numbers now ([si.lanisce.lani.app.NumbersController.plan]); none while closed. [again]: "▶ Še enkrat" from its end. */
    fun startNumbers(again: Boolean = false) {
        if (!again && busyRun()) return
        val tasks = numbers.plan()
        if (tasks.isEmpty()) return
        startRun(Screen.NumbersRun(tasks, ++numberRuns), again)
    }

    /**
     * QA's numbers hooks (app/QaHooks, "numbers…"): "locked" the class ready with the unit stevila as never begun, "ready"
     * with its intro done (as "⏭ Poznam to"), both with the module never played; "time" the unit kdaj-cas's intro done;
     * "ranges:<id>" the ranges open up to it; "again" numbers missed due today; "near" the newest range one right answer
     * from the next; then the module's screen.
     */
    fun qaNumbers(what: String) {
        val now = System.currentTimeMillis()
        val today = java.time.LocalDate.now()
        when {
            what == "locked" -> game.apply { si.lanisce.lani.app.QaHooks.readyNumbers(si.lanisce.lani.app.QaHooks.readyCounting(si.lanisce.lani.app.QaHooks.readySchool(it, now))) }
            what == "ready" -> {
                game.apply { si.lanisce.lani.app.QaHooks.readyNumbers(si.lanisce.lani.app.QaHooks.readyCounting(si.lanisce.lani.app.QaHooks.readySchool(it, now))) }
                school.unit(si.lanisce.lani.data.NumbersPractice.UNIT)?.let { u -> school.introProgress(u, u.intro?.steps?.size ?: 0, done = true) }
            }
            what == "time" -> school.unit(si.lanisce.lani.data.NumbersPractice.TIME_UNIT)?.let { u -> school.introProgress(u, u.intro?.steps?.size ?: 0, done = true) }
            what.startsWith("ranges:") -> game.apply { si.lanisce.lani.app.QaHooks.numbersRanges(it, what.removePrefix("ranges:"), today) }
            what == "again" -> game.apply { si.lanisce.lani.app.QaHooks.numbersAgain(it, today) }
            what == "near" -> game.apply { si.lanisce.lani.app.QaHooks.numbersNear(it) }
        }
        openNumbers()
    }

    /** The village with building [id]'s card open (the school's level a course level waits for: its upgrade). */
    fun openBuildingCard(id: String) {
        buildingCard = id
        nav.go(Screen.Village)
    }

    /** Home: everything over it closed (Back from it leaves the app). */
    fun home() {
        nav.go(Screen.Home)
    }

    /** Each review run is a screen of its own, also the same cards polished again ([Screen.Review.run]). */
    private var reviewRuns = 0

    /**
     * The due cards of the home language's review deck, or of another language's ([language], practised on visits): each
     * language has its own deck, and the review screen switches between those with cards due.
     */
    fun startReview(language: String? = null) {
        val cards = content.deck(language)?.dueCards.orEmpty()
        if (cards.isNotEmpty()) startRun(Screen.Review(cards, run = ++reviewRuns, language = language))
    }

    /**
     * A short review of rusty words (their last review failed), due or not: a right answer polishes one at once, and
     * SM-2 still brings it back in tomorrow's review. [cards]: the dashboard's rusty words, or the ones a review
     * just left rusty.
     */
    fun startPolish(cards: List<ReviewCard> = content.dashboard?.rusty.orEmpty()) {
        if (cards.isNotEmpty()) startRun(Screen.Review(cards.take(20), polish = true, run = ++reviewRuns))
    }

    fun openModule(id: String) = launchSafely {
        content.open(id)?.let { startRun(Screen.Player(it)) }
    }

    /** Each grammar practise run is a screen of its own ([Screen.GrammarPractice.run]). */
    private var grammarRuns = 0

    /**
     * "🎯 Vadi · Practise" on grammar page [id]: a short run of what names it (the modules' exercises, the tent challenge's
     * questions, the surprises' letters and ways), else the learner's grammar cards on it; with nothing yet, the tutor is
     * asked for a drill.
     */
    fun practiseGrammar(id: String) = launchSafely {
        if (busyRun()) return@launchSafely
        val page = grammar.page(id) ?: return@launchSafely
        content.loadModuleExercises()
        val seed = System.currentTimeMillis()
        val challenge = si.lanisce.lani.game.TentMove.exercises(seed, speaker.canSay) + si.lanisce.lani.game.Surprises.onRules(seed)
        when (val p = si.lanisce.lani.data.Grammar.practice(page, content.moduleExercises(), challenge, content.dashboard?.pool.orEmpty(), seed)) {
            si.lanisce.lani.data.Grammar.Practice.Ask -> grammar.askDrill(id)
            else -> {
                grammar.close()
                startRun(Screen.GrammarPractice(id, p, ++grammarRuns))
            }
        }
    }

    /** "📚 Nove besede · New words": the word packs. */
    fun openPacks() {
        nav.go(Screen.Packs)
    }

    /** "📗 Branje · Reading", the reading corner. */
    fun openReadings() {
        nav.go(Screen.Readings)
    }

    /** Reads reading [id] aloud ("🎤 Beri na glas"). */
    fun openReadAloud(id: String) {
        if (VisitWorld.active) return // a visited town's readings aren't the learner's
        nav.go(Screen.ReadAloud(id))
    }

    /**
     * The learner's level in [language] ("sl" → "A2"; A1 before the dashboard is loaded): the storyteller tells at it. The
     * level a treasure just brought counts before the node has it ([ContentController.levelIn]).
     */
    fun levelIn(language: String): String =
        si.lanisce.lani.app.QaHooks.level?.takeIf { si.lanisce.lani.app.QaHooks.enabled } ?: content.levelIn(language) // QA's "level:<lv>" (a debug build)

    // --- the treasure map (companion/GAME.md, "The treasure map") ------------------------------------------------------

    /** "🗺️ Zemljevid zaklada · The treasure map": the storyteller's offer, the path, the treasure. */
    fun openTreasure() {
        grammar.close()
        nav.go(Screen.Treasure)
    }

    /** Back from the treasure map, where it was opened from (the map seen: Nav's [left]). */
    fun leaveTreasure() = back()

    /** Takes the map (the storyteller's, or from the grammar book) and shows the path. */
    fun takeMap() {
        treasure.take()
        if (screen !is Screen.Treasure) openTreasure()
    }

    /** A station of the treasure hunt, with the storyteller on the stage. */
    fun startStation(station: si.lanisce.lani.game.Station) = startChallenge(ChallengeOrigin.Treasure(station.id))

    /** Learns the next words of pack [id]; [again] (more words right after a session) skips the intro. */
    fun startPack(id: String, again: Boolean = false) = launchSafely {
        packs.next(id)?.let { (p, words) -> startRun(Screen.PackLearn(p, words), again) }
    }

    /** Learns scene [id]'s words not learned yet (a pack run of them), of the things the village has there; back leads to the scene. */
    fun learnScene(id: String, again: Boolean = false) = launchSafely {
        val s = scenes.byId(id)?.let { it.inWorld(sceneWorld(it, sceneBuilding?.takeIf { b -> b.first == id }?.second)) } ?: return@launchSafely
        scenes.loadPacks(s)
        val (p, words) = scenes.toLearn(s) ?: run {
            notices.banner = if (SceneWords.left(s, scenes.packs).isEmpty()) "✅ ${bi("appViewModel.knowAllTheseWords")}"
            else "📡 ${bi("appViewModel.theseWordsCantLoaded")}"
            return@launchSafely
        }
        startRun(Screen.PackLearn(p, words, scene = id), again)
    }

    /** Learns the words of [story], just told in scene [scene] (a pack run of those not learned yet); back leads to the scene. */
    fun learnStory(scene: String, story: si.lanisce.lani.game.scene.Story) = launchSafely {
        scenes.loadPacks(story)
        val (p, words) = scenes.toLearn(story) ?: run {
            notices.banner = if (story.words.isNotEmpty() && story.words.all { w -> scenes.packs[w.pack]?.learned?.contains(w.id) == true }) "✅ ${bi("appViewModel.knowAllTheseWords")}"
            else "📡 ${bi("appViewModel.theseWordsCantLoaded")}"
            return@launchSafely
        }
        scenes.closeTalk()
        startRun(Screen.PackLearn(p, words, scene = scene))
    }

    fun openFamily(challengeId: String? = null) {
        game.clearReward()
        nav.go(Screen.Family(challengeId))
    }

    /** "🏘️ Moja vas · My village": the map. */
    fun openVillage() {
        nav.go(Screen.Village)
    }

    /** The register of the village's people. */
    fun openVillagers() {
        nav.go(Screen.Villagers)
    }

    /** One villager's page. */
    fun openVillager(id: String) {
        nav.go(Screen.Villager(id))
    }

    /** "🤝 Prijatelji · Friends": the towns linked with this one (the screen loads them). */
    fun openFriends() {
        nav.go(Screen.Friends)
    }

    /** "📈 Napredek · Progress" of linked town [id]: what its learner shares with this one (ui/towns/TownProgressScreen). */
    fun openTownProgress(id: String) {
        if (busyRun()) return
        nav.go(Screen.TownProgress(id))
    }

    /** Back from a town's progress (to Friends, or wherever it was opened). */
    fun closeTownProgress() = back()

    /** A question between towns ([key]): a guest's to answer, or ([asked]) the learner's with the answer. */
    fun openQuestion(key: String, asked: Boolean) {
        game.clearReward()
        questions.clearCheck()
        nav.go(Screen.TownQuestion(key, asked))
        questions.load()
    }

    /**
     * Visits linked town [id] (plan 2, §3; companion/README.md, "Visiting"): its village read-only, in its language
     * explained in the learner's base, in its culture's look. The learner's own village waits meanwhile (no days pass on
     * the phone, nothing of theirs is loaded), and the phone's voice speaks the town's language.
     */
    fun visitTown(id: String) {
        val t = towns.list?.towns?.firstOrNull { it.id == id } ?: return
        if (busyRun()) return
        game.paused = true
        VisitWorld.enter(t.language, t.culture)
        // the town's own voices (companion/README.md, "Voices on a visit"): its clips through the node, else the node voices
        // the line in the town's cast voice; then the phone's offline voice of the town's language, Android's, else a note
        speaker.clips = Clips(getApplication(), viewModelScope, visit = true).also { c ->
            c.playerGender = clips.playerGender
            connected?.let { c.connect(it, si.lanisce.lani.data.ClipRoutes.visit(t.id)) }
            c.refresh()
            visitClips = c
        }
        visitVoice.start(t.language)
        speaker.refresh()
        showWorld()
        visits.start(t.id, t.name, t.learner, t.culture, t.language)
        nav.go(Screen.Visit(t.id))
    }

    // --- friendship between towns (companion/README.md, "Friendship between towns") ----------------------------------

    /** Sends help against friend [t]'s trouble: once their town took it, the village pays for it (TownFriendship.AID_COST). */
    fun sendAid(t: si.lanisce.lani.data.FriendTown) = launchSafely {
        val e = t.event ?: return@launchSafely
        val s = game.state
        if (s == null || !si.lanisce.lani.game.TownFriendship.canAid(s)) {
            return@launchSafely run { notices.banner = "🧺 ${bi("friendship.aidNeeds", "cost" to si.lanisce.lani.game.TownFriendship.costText())}" }
        }
        when (val w = towns.aid(t, e)) {
            is si.lanisce.lani.app.FriendWrite.Done -> {
                val key = runCatching { TownActs.parseAnswer(w.body).key }.getOrDefault("")
                if (key.isNotBlank()) game.visitAided(key, t.id, t.name)
                notices.banner = "🤝 ${bi("friendship.aidSent", "town" to t.name)}"
                towns.load()
            }
            is si.lanisce.lani.app.FriendWrite.Failed -> notices.banner = w.problem
        }
    }

    /** Offers friend [t] a move: someone of theirs moves here ([dir] "in") or someone of here moves there ("out"). */
    fun offerMove(t: si.lanisce.lani.data.FriendTown, dir: String) = launchSafely {
        when (val w = towns.offerMove(t, dir)) {
            is si.lanisce.lani.app.FriendWrite.Done -> {
                notices.banner = "🧳 ${bi("friendship.offered", "town" to t.name)}"
                towns.load()
            }
            is si.lanisce.lani.app.FriendWrite.Failed -> notices.banner = w.problem
        }
    }

    /** Says yes ([yes]) to friend [t]'s move [m], or declines it (withdraws this town's own): the list reloads, a move done comes in. */
    fun answerMove(t: si.lanisce.lani.data.FriendTown, m: si.lanisce.lani.data.TownMove, yes: Boolean) = launchSafely {
        when (val w = towns.answerMove(t, m, yes)) {
            is si.lanisce.lani.app.FriendWrite.Done -> towns.load()
            is si.lanisce.lani.app.FriendWrite.Failed -> notices.banner = w.problem
        }
    }

    /** A feast under the linden from the Friends screen (to share a friend's): the list reloads to see it shared. */
    fun holdFeast() {
        if (game.feast()) {
            notices.banner = "🎪 ${bi("friendship.feastHeld")}"
            towns.load()
        }
    }

    /** Back in the visited town from one of its places. */
    fun openVisit() {
        visits.visit?.let { nav.go(Screen.Visit(it.id)) }
    }

    /** Walks into place [scene] of the visited town (its words to tap, a guest's dialog). */
    fun openVisitScene(scene: String) {
        visits.visit?.let { nav.go(Screen.VisitScene(it.id, scene)) }
    }

    // --- things to do on a visit (companion/README.md, "Things to do on a visit") ------------------------------

    /** Opens a request of the visited town: its run in the town's language, then the help sent. */
    fun openVisitRequest(request: String) {
        visits.visit?.let { startRun(Screen.VisitRequest(it.id, request)) }
    }

    /**
     * A request's run on a visit ended: [exercises] answered with [verdicts]. The learner's tutor gets it as a session in
     * the town's language (its words and mistakes go to that language's profile); a pass sends the help (+🤝 for the
     * host) and brings the giver's thank-you good. [done] gets the good's name (or null: none), or the problem.
     */
    fun finishVisitRequest(r: TownRequest, exercises: List<Exercise>, verdicts: List<Verdict>, minutes: Int, done: (VisitHelp) -> Unit) = launchSafely {
        val v = visits.visit ?: return@launchSafely done(VisitHelp.Failed("🧳 ${bi("visit.visitOver")}"))
        val ok = verdicts.map { it != Verdict.WRONG }
        val summary = "A request of ${r.giver}'s done on a visit to ${v.name} (${v.language}): ${ok.count { it }}/${exercises.size}"
        sync.submitLater(Writes.message("session_end", "main", summary, TownActs.sessionData(r, v.id, v.name, v.language, exercises, ok, minutes)))
        if (!TownActs.passed(ok.count { it }, exercises.size)) return@launchSafely done(VisitHelp.Again)
        sendHelp(r, done)
    }

    /** Sends the help for request [r] (again, after a problem): its thanks come into the chest. */
    fun sendHelp(r: TownRequest, done: (VisitHelp) -> Unit) = launchSafely {
        val v = visits.visit ?: return@launchSafely done(VisitHelp.Failed("🧳 ${bi("visit.visitOver")}"))
        when (val w = visits.help(r)) {
            is VisitWrite.Done -> {
                val good = game.visitHelped(w.answer.key, v.id, v.name, r.giver, w.answer.thanks?.culture ?: v.culture, w.answer.thanks?.good)
                done(VisitHelp.Helped(good?.let { si.lanisce.lani.game.culture.Cultures.home.goods[it]?.name }, w.answer.amount))
            }
            is VisitWrite.Refused -> done(VisitHelp.Failed(w.problem))
        }
    }

    /** Leaves a gift ([good], of the own chest) and/or a [message] in the visited town; [done] gets null, or the problem. */
    fun sendVisitGift(good: String?, message: String, done: (String?) -> Unit) = launchSafely {
        val v = visits.visit ?: return@launchSafely done("🧳 ${bi("visit.visitOver")}")
        when (val w = visits.gift(good, message)) {
            is VisitWrite.Done -> {
                game.visitGave(w.answer.key, v.id, v.name, good)
                notices.banner = "🎁 ${bi("visit.giftLeft", "town" to v.name)}"
                done(null)
            }
            is VisitWrite.Refused -> done(w.problem)
        }
    }

    /** Talks with villager [villager] of the visited town: played by the learner's own tutor, in the town's language. */
    fun talkOnVisit(villager: String) = launchSafely {
        val (s, problem) = visits.scenario(kotlinx.serialization.json.buildJsonObject { put("villager", villager) })
        if (s == null) notices.banner = problem else openVisitTalk(s)
    }

    /** Haggles at the visited town's market over [give] (the learner's own) for [get] (the town's goods). */
    fun haggleOnVisit(give: Bundle, get: Bundle) = launchSafely {
        val v = visits.visit ?: return@launchSafely
        val body = kotlinx.serialization.json.buildJsonObject {
            put("market", kotlinx.serialization.json.buildJsonObject { put("give", give.toJson()); put("get", get.toJson()) })
        }
        val (s, problem) = visits.scenario(body)
        if (s == null) return@launchSafely run { notices.banner = problem }
        visits.haggling(s.id, Haggle(v.id, v.name, give, get, visits.market?.rates?.dealScore ?: TownActs.DEAL_SCORE))
        openVisitTalk(s)
    }

    /** Opens Talk at role-play [s] of a visit; Back leads to the visit. */
    private fun openVisitTalk(s: si.lanisce.lani.data.Scenario) {
        openTalkFor()
        if (talk.state?.phase != TalkPhase.CHAT) {
            talk.pick(s)
            talk.start()
        }
    }

    /** Someone of the visited town was met in one of its places (a guest's dialog, played to its end). */
    fun metOnVisit(villager: String, who: String) {
        val v = visits.visit ?: return
        game.visitMet(v.id, v.name, villager, who)
    }

    /** A talk ended: one with someone of a visited town ("town:<town>:<villager>") is a meeting the village keeps. */
    private fun talkFinished(st: si.lanisce.lani.data.TalkState) {
        val parts = st.scenario.id.split(':')
        if (parts.size != 3 || parts[0] != "town") return
        val name = visits.visit?.takeIf { it.id == parts[1] }?.name ?: game.state?.towns?.get(parts[1])?.name.orEmpty()
        game.visitMet(parts[1], name, parts[2], st.scenario.title)
    }

    /** A market haggle's debrief: a deal (the tutor's, with a score of 6 or more) makes the trade; else nothing changes hands. */
    private fun talkDebriefed(scenarioId: String, conversation: String, data: kotlinx.serialization.json.JsonObject?) {
        if (!scenarioId.startsWith("market:")) return
        val h = visits.haggle(scenarioId) ?: return
        val score = TownActs.deal(data, h.dealScore) ?: return run { notices.banner = "🤝 ${bi("visit.noDeal")}" }
        launchSafely {
            when (val w = visits.trade(conversation, h, score)) {
                is VisitWrite.Done -> {
                    game.visitTraded(w.answer.key, h.town, h.name, h.give, h.get)
                    notices.banner = "⚖️ ${bi("visit.traded", "gave" to si.lanisce.lani.game.Guests.text(h.give), "got" to si.lanisce.lani.game.Guests.text(h.get))}"
                }
                is VisitWrite.Refused -> notices.banner = w.problem
            }
        }
    }

    /**
     * Home from a visit ("⌂ Domov · Home", or Back on the visited town): the visit ends (Nav: [left]), and the screen it was
     * started from shows (Friends, or what opened it).
     */
    fun leaveVisit() {
        nav.backTo { it is Screen.Visit }
        back()
    }

    /** The visit is over: the learner's own pair, culture and voices again, and their village's days go on. */
    private fun endVisit() {
        visits.end()
        VisitWorld.leave()
        speaker.stop()
        speaker.clips = clips
        visitClips = null
        visitVoice.end()
        speaker.refresh()
        showWorld()
        game.paused = false
    }

    /** What the screens show changed (a visit began or ended): MainActivity builds them anew in it. */
    private fun showWorld() {
        langPair = L10n.pair
        culture = Cultures.current.id
    }

    /**
     * Talks freely with villager [id]: the tutor plays them in a role-play the node builds for the friendship
     * (companion/VILLAGERS.md); leaving Talk comes back here. A conversation already running shows instead.
     */
    fun talkWithVillager(id: String) = viewModelScope.launch {
        if (villagers.opening != null) return@launch
        val from = screen
        val s = try {
            villagers.scenario(id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            notices.banner = "📡 ${bi("appViewModel.talkCantStartRight")}"
            return@launch
        }
        if (screen != from) return@launch // Jan went elsewhere meanwhile
        openTalkFor()
        if (talk.state?.phase != TalkPhase.CHAT) {
            talk.pick(s)
            talk.start()
        }
    }

    /**
     * Steps into a scene of the village; [focus] is a happening key or an object slot; [building] the one the learner
     * tapped (the hut or house whose room it is, at its level). Back into the same scene from a word run or a talk, it is
     * the building it was last opened from. [from]: the scene on view, whose picture it is gone into from (the mill from
     * the stream, [si.lanisce.lani.game.scene.SceneDoors]): it opens on that one, Back returns there; the same scene opened
     * again where it lies keeps it.
     */
    fun openScene(id: String, focus: String? = null, building: String? = null, from: String? = null) {
        if (building != null) sceneBuilding = id to building
        val inside = from ?: (nav.top as? Screen.Scene)?.takeIf { it.id == id }?.from
        nav.go(Screen.Scene(id, focus, building ?: sceneBuilding?.takeIf { it.first == id }?.second, inside))
    }

    /** The scene last opened from a building, and that building's id (see [openScene]). */
    private var sceneBuilding: Pair<String, String>? = null

    /**
     * [state]'s village as scene [scene] sees it: in the building it was opened from, else its place's ([Homes.world]);
     * the school's blackboard with the learner's lesson ([si.lanisce.lani.game.scene.Blackboard.of]).
     */
    fun sceneWorld(scene: si.lanisce.lani.game.scene.SceneSpec, building: String? = (screen as? Screen.Scene)?.building): SceneWorld =
        game.state?.let { s ->
            val world = si.lanisce.lani.game.scene.Homes.world(scene, s, building, scenes.all)
            if (scene.art == "school") world.copy(board = si.lanisce.lani.game.scene.Blackboard.of(s, school.course)) else world
        } ?: SceneWorld.ALL

    /** To the village (Back to it, when it is below). */
    fun village() = openVillage()

    /** A gathering run; [again] (once more, from its result) skips the intro. */
    fun startGather(res: Res, again: Boolean = false) = startChallenge(ChallengeOrigin.Gather(res), again)

    /** "⇄ Zamenjaj · Trade": [amount] of [give] for [get], after a short run of both skills (companion/GAME.md, "Trading"). */
    fun startTrade(give: Res, get: Res, amount: Int) = startChallenge(ChallengeOrigin.Trade(give, get, amount))
    fun startEvent() = startChallenge(ChallengeOrigin.Event)

    /**
     * Today's step of village project [id], with its leader (companion/GAME.md, "Village projects"): its short scene that
     * does it (companion/SCENES.md, "Project steps"), in the step's scene when the village has it open, else over the
     * village; a step without one (an older content file) is its practice, as before.
     */
    fun startProject(id: String) {
        if (!startProjectStep(id)) startChallenge(ChallengeOrigin.Project(id))
    }

    /**
     * Project [spec]'s words on its card (companion/SCENES.md "Project steps"): its pack as the node has it (which are
     * learned), else as the app bundles it, and where today's step is played (its scene's title, when the village has it
     * open); null when it has no pack.
     */
    fun projectWords(spec: si.lanisce.lani.game.ProjectSpec): si.lanisce.lani.ui.game.ProjectWords? {
        val id = spec.pack ?: return null
        val p = scenes.packs[id] ?: si.lanisce.lani.game.FestivalPacks.pack(id) ?: return null
        val s = game.state
        val o = s?.let { si.lanisce.lani.game.Projects.option(it, spec, java.time.LocalDate.now()) }
        val where = s?.let { st -> o?.next?.play?.let { play -> si.lanisce.lani.game.Projects.sceneOf(play, scenes.all, st, Cultures.current.manifest.language) } }
            ?.let { "${it.emoji} ${it.title}" }
        return si.lanisce.lani.ui.game.ProjectWords(id, p.words, p.learned.toSet(), where)
    }

    /** Today's step of project [id] as its short scene ([startProject]); false when it has none (or none to play now). */
    private fun startProjectStep(id: String): Boolean {
        val s = game.state ?: return false
        val spec = si.lanisce.lani.game.Projects.spec(id) ?: return false
        val today = java.time.LocalDate.now()
        val o = si.lanisce.lani.game.Projects.option(s, spec, today)
        val play = o.next?.play ?: return false
        if (!o.available) {
            o.reason?.let { notices.banner = "${spec.emoji} $it" }
            return true
        }
        val lang = Cultures.current.manifest.language
        // its scene, when the village has it open now (built, the age reached) and it is in the village's language
        val scene = si.lanisce.lani.game.Projects.sceneOf(play, scenes.all, s, lang)
        val people = villagers.people(s, today)
        if (!scenes.startStep(spec, o.done, scene, si.lanisce.lani.game.villagers.Residents.present(s, today), people)) return false
        if (scene != null) openScene(scene.id)
        return true
    }

    /**
     * The party of [age] over the village (companion/GAME.md, "Ages and their celebrations"): its dialog with all the
     * village, the first time while it is the village's age paying (AgeUps.partyHad); false when it has none (a gathering,
     * a culture without its own) or there is no village.
     */
    fun startParty(age: si.lanisce.lani.game.Age): Boolean {
        val s = game.state ?: return false
        val today = java.time.LocalDate.now()
        val c = si.lanisce.lani.game.AgeUps.celebration(age)
        // its words: the pack as the node has it (which are learned), else as the app bundles it
        c.pack?.let { p -> launchSafely { scenes.loadPack(p) } }
        return scenes.startParty(c, si.lanisce.lani.game.villagers.Residents.present(s, today), villagers.people(s, today), s.residents.map { it.id })
    }

    /** The festival [id] that is on today (companion/GAME.md, "The calendar"). */
    fun startFestival(id: String) = startChallenge(ChallengeOrigin.Festival(id))

    /** Today's surprise at the road; the pedlar opens the chest to buy instead. */
    fun startSurprise() = startChallenge(ChallengeOrigin.Surprise)

    /** Local quests are challenges; tutor quests open their module. */
    fun startQuest(id: String) {
        val q = game.state?.quests?.firstOrNull { it.id == id } ?: return
        if (q.source == QuestSource.TUTOR) q.moduleId?.let(::openModule)
        else startChallenge(ChallengeOrigin.Quest(id))
    }

    /**
     * "🎯 Vadi, kar ti ne gre · Practise what's hard" on request [id] (tried too often below the mark, no drill for it;
     * companion/GAME.md "Quests"): the grammar book's practice of the rule it missed most (its wrong answers' pages, else the
     * request's own, else its module's); with no page known, the tutor is asked for a shorter drill for it. The request stays open.
     */
    fun practiseHard(id: String) {
        val q = game.state?.quests?.firstOrNull { it.id == id } ?: return
        val page = (q.missed + listOfNotNull(q.grammar) + q.moduleId?.let(::modulePages).orEmpty()).firstOrNull { grammar.page(it) != null }
        if (page != null) {
            practiseGrammar(page)
            return
        }
        chat.ask(
            bi("quests.drillRequest", "title" to q.title.substringBefore(" · ")),
            // the tutor writes a drill with `helps` = this module (lani-studio): the task then waits behind it
            kotlinx.serialization.json.buildJsonObject {
                put("quest_drill", kotlinx.serialization.json.buildJsonObject {
                    put("id", q.id)
                    q.moduleId?.let { put("module", it) }
                    put("title", q.title)
                    put("giver", q.giver)
                    put("tries", q.tries)
                    put("best", q.best)
                    put("of", q.bestOf)
                    put("pass_mark", si.lanisce.lani.game.QuestQueue.passMark(q))
                })
            },
        )
    }

    private fun startChallenge(origin: ChallengeOrigin, again: Boolean = false) = launchSafely {
        game.challenge(origin)?.let { startRun(Screen.Challenge(origin, it), again) }
    }

    /** "🗣️ Pogovor · Talk": role-play, its list of scenes; Back leads where it was opened (Home, the chat's screen …). */
    fun openTalk() {
        if (busyRun()) return
        chat.close()
        talkForOne = false
        nav.go(Screen.Talk) // its scenes load as it shows ([shown])
    }

    /** Talk opened for one role-play (a scene's dialog, a villager, a visit's talk or market), not its list ([talkOpenedFrom]). */
    private var talkForOne by mutableStateOf(false)

    /** Talk for one role-play from where it was opened ([openTalk] with a scenario, a villager's, a visit's). */
    private fun openTalkFor() {
        openTalk()
        talkForOne = true
    }

    /** Leaves Talk for where it was opened from. */
    fun leaveTalk() = back()

    /**
     * Talk was opened for one role-play from somewhere else (a scene, a villager, a visit): its intro's "Later", and the
     * end of a villager's talk, lead back there, not to the list of scenes.
     */
    val talkOpenedFrom: Boolean get() = screen == Screen.Talk && talkForOne

    /** Opens the Talk screen at role-play [scenario] (e.g. after a scene's dialog), unless a conversation is running. */
    fun openTalk(scenario: String) {
        openTalkFor()
        launchSafely {
            val s = bridge?.scenarios()?.firstOrNull { it.id == scenario } ?: return@launchSafely
            if (talk.state == null) talk.pick(s)
        }
    }

    /** "📊 Napredek · Progress". */
    fun openProgress() {
        nav.go(Screen.Progress)
    }

    /** "🔤 Moje besede · My words": the learner's words, a dictionary lookup. */
    fun openMyWords() {
        nav.go(Screen.MyWords)
    }

    /** "📖 Slovnica · Grammar": the grammar book's index, its pages opened over it. */
    fun openGrammarBook() {
        nav.go(Screen.GrammarBook)
    }

    /** "⚙️ Nastavitve · Settings", at [section] ([Screen.Settings]) or its sections. */
    fun openSettings(section: String? = null) {
        // a section has the sections' list under it (Back leads there), wherever it was opened from
        if (section != null) nav.go(Screen.Settings())
        nav.go(Screen.Settings(section))
    }

    // --- "🤝 Prijatelji · Friends" one tap away (Home, the village's HUD): what's new there -------------------------------

    private val friendsPrefs = app.getSharedPreferences("friends_news", android.content.Context.MODE_PRIVATE)

    /** When Friends was last seen: guests who came after it are news ([si.lanisce.lani.ui.towns.FriendsNews]). */
    var friendsSeenAt by mutableStateOf(friendsPrefs.getString("seen", null).orEmpty())
        private set

    /** What waits in Friends: the badge on its entries on Home and the village's HUD. */
    val friendsNews: Int
        get() = si.lanisce.lani.ui.towns.FriendsNews.count(towns.friendships, towns.guests, questions.list, friendsSeenAt)

    /** Friends shown: the guests so far are seen. */
    private fun friendsSeen() {
        val newest = si.lanisce.lani.ui.towns.FriendsNews.newest(towns.guests)
        if (newest > friendsSeenAt) {
            friendsSeenAt = newest
            friendsPrefs.edit().putString("seen", newest).apply()
        }
    }

    private fun launchSafely(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launchSafely(notices::fail, block)

    private companion object {
        /** The back stack in the saved state ([NavStack.saved]). */
        const val NAV_KEY = "nav"
    }

    override fun onCleared() {
        hintVoice.release()
        introVoice.release()
        speaker.shutdown()
        ambience.release()
    }
}
