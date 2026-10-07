package si.lanisce.lani

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
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
import java.io.File

/**
 * Wires the feature controllers together and owns navigation ([screen]). The state lives in the
 * controllers: [content] (dashboard, modules), [packs], [game] (the village), [chat], [talk],
 * [update], [progress] and [sync] (the offline outbox). They share [notices] for the snackbar.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
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
    /** Natural voice clips from the node; played through [speaker] after family recordings. */
    val clips = Clips(app, viewModelScope).also { speaker.clips = it }
    /** The hint's 🔊 in a dialog: its TL;DR in the learner's base with the Slovene in the app's voice; stopped by [speaker]. */
    val hintVoice = si.lanisce.lani.data.HintVoice(app, viewModelScope, speaker).also { speaker.hint = it }
    /** "🚗 Za pot · For the road": the car's sessions got ready on the phone (road/RoadPrep), played by road/RoadService. */
    val road = si.lanisce.lani.road.RoadPrep(app, viewModelScope)
    /** The phone's own player for the road's sessions (Bluetooth without Android Auto). */
    val roadRemote = si.lanisce.lani.road.RoadRemote(app)
    val stt = si.lanisce.lani.data.Stt(app, viewModelScope) // Whisper on the node, for speaking (data/Stt.kt)
    /** The background sounds of the village and its scenes (data/Ambience.kt): they duck under [speaker]. */
    val ambience = si.lanisce.lani.data.Ambience(app, viewModelScope, talking = { speaker.talking })
    private var bridge: Bridge? = null
    private val node = { bridge }

    val notices = Notices()
    val sync = SyncController(app, viewModelScope, node, notices, onSaved = ::refresh)
    val content: ContentController = ContentController(app, prefs, node, onDashboard = {
        game.refreshWidget()
        content.dashboard?.let {
            if (languages.profileSays(it.pair)) showLanguages()
            if (learners.profileSays(it.addressee)) relearn()
        }
    })
    val packs = PacksController(prefs, node, notices, sync)
    val game: GameController = GameController(
        app, viewModelScope, prefs, node, content, notices,
        canSpeak = { speaker.canSay }, reload = ::reload, saveReviews = ::finishReview,
        saveWords = packs::saveWords,
        cast = { villagers.all },
    )
    // the chat by month (files/chat/2026-09.json); chat.json is what was kept before the archive (ChatStore)
    val chat = ChatController(viewModelScope, prefs, sync, notices, ChatStore(File(app.filesDir, "chat"), File(app.filesDir, "chat.json")))
    /** The village book's grammar chapter: its pages, the one open over any screen, the pages met (app/GrammarController). */
    val grammar = GrammarController(node, game, content, chat, notices).also { g ->
        // a session's report tells the tutor which rules are ripe to introduce (companion/GAME.md, "Rules not yet")
        chat.sessionExtras = g::sessionExtras
    }
    /** A word tapped in a line: its card, adding it to the learner's words, asking the tutor about it. */
    val words = WordsController(viewModelScope, node, sync, chat, notices)
    /** A word's forms: the form questions of the reviews, the table on a word's card (companion/GAME.md, "A word's forms"). */
    val forms = si.lanisce.lani.app.FormsController(app, viewModelScope, node, grammar)
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
    val talk = TalkModel(
        viewModelScope,
        send = { kind, conv, text, data -> (bridge ?: throw java.io.IOException("not connected")).message(kind, conv, text, data) },
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
        saveReviews = { results, minutes -> finishReview(results, minutes) },
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
    /** The app is on screen; the event stream only runs then. */
    private var foreground = false
    /** A notification link that arrived before the connection. */
    private var pendingLink: DeepLink? = null

    var screen by mutableStateOf<Screen>(Screen.Loading)
        private set
    val link: Link get() = events.link
    /** The node refused this phone's token (unpaired with lani-pair --revoke): time to scan a new code. */
    val unpaired: Boolean get() = events.unpaired
    /** The tutor this phone talks to: its address, and who it paired with by QR code (null for a typed token). */
    var bridgeUrl by mutableStateOf<String?>(null)
        private set
    var paired by mutableStateOf<PairedInfo?>(null)
        private set
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
            if (c == null) screen = Screen.Setup else connect(c)
        }
        // everyone who lives in the village has a voice profile on the node (their own voice, or pitch and pace)
        viewModelScope.launch {
            snapshotFlow { villagers.voicePeople(game.state) }.distinctUntilChanged().collect { clips.syncProfiles(it) }
        }
        // an upgrade asks for the building's words ("Knowledge builds", companion/GAME.md "Upgrades")
        game.upgradeWords = ::upgradeWords
        // a drill of the tutor's is found for the task it shares a grammar page with (companion/GAME.md "Quests")
        game.modulePages = ::modulePages
    }

    /** The grammar pages module [id] practises: the book's pages that list it, and those its cached exercises name. */
    private fun modulePages(id: String): Set<String> =
        (si.lanisce.lani.data.Grammar.ofModule(id, grammar.pages).map { it.id } +
            content.moduleExercises().filter { it.first == id }.mapNotNull { it.second.grammar }).toSet()

    /** Validates the config against the bridge, then stores it. Returns an error message or null. */
    suspend fun setup(url: String, token: String): String? {
        val c = BridgeConfig(url.trim().trimEnd('/'), token.trim())
        return try {
            Bridge(c).modules()
            prefs.saveConfig(c)
            paired = null
            connect(c)
            null
        } catch (e: Exception) {
            e.message ?: "connection failed"
        }
    }

    /**
     * Pairs with the tutor in a scanned QR code (data/Pairing.kt): checks the code, trades it for this phone's own
     * token, and connects. [done] gets null when paired, else the message to show ("Slovene · English").
     */
    fun pair(scanned: String, done: (String?) -> Unit) = viewModelScope.launch {
        val p = when (val r = PairPayload.parse(scanned)) {
            is PairParse.Ok -> r.payload
            is PairParse.Invalid -> return@launch done(r.problem.message)
        }
        // Another learner's bridge would get this phone's village, chat and queued answers: not without a fresh start.
        if (!Pairing.sameLearner(prefs.config()?.baseUrl, prefs.pairing(), p)) {
            return@launch done(Pairing.otherLearner(paired?.learner ?: content.dashboard?.name))
        }
        when (val o = PairFlow(PairingApi::post).pair(p, android.os.Build.MODEL ?: "Android")) {
            is PairOutcome.Failed -> done(o.message)
            is PairOutcome.Paired -> {
                prefs.savePairing(o.config, o.info)
                paired = o.info
                connect(o.config)
                notices.banner = Pairing.pairedBanner(o.info)
                done(null)
            }
        }
    }

    private fun connect(c: BridgeConfig) {
        bridgeUrl = c.baseUrl
        bridge = Bridge(c)
        family.connect(c)
        clips.connect(c)
        stt.connect(c)
        screen = Screen.Home
        refresh()
        game.sync()
        update.check()
        BackgroundCheck.schedule(getApplication())
        if (foreground) events.restart()
        pendingLink?.let(::open)
    }

    fun refresh(): Job = launchSafely { reload() }

    private suspend fun reload() {
        val b = bridge ?: return
        // on a visit the host's language and culture are shown: the learner's own content waits until they're home
        if (VisitWorld.active) return
        family.refresh()
        questions.load() // guests' questions, and the answers to the learner's (their ties with each town)
        clips.refresh()
        content.reload(b)
        reloadCulture(b)
        grammar.reload(b)
        content.dashboard?.let { forms.reload(b, it.pool) }
        readings.reload(b)
        packs.reload(b)
        scenes.reload(b)
        villagers.reload(b)
        clips.syncProfiles(villagers.voicePeople(game.state))
        // the node has the new level now, or the learner is ready for the next: the storyteller offers the map
        treasure.check()
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
            is BridgeEvent.Reply ->
                if (ev.conversationId.startsWith("rp-")) talk.onReply(ev)
                else if (ev.conversationId.startsWith("ex")) chat.onGraded(ev)
                else if (ev.conversationId.startsWith("fam_")) {
                    family.refresh() // the tutor's feedback on a family challenge
                    notices.banner = "🧑‍🏫 ${bi("appViewModel.tutorRepliedFamilyAnswer")}"
                } else if (!questions.onReply(ev)) chat.onReply(ev) // tq_…: a check's or a question's feedback
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
            // the tutor kept a rule it explained: the learner met it (in the chat), so its page is in the book now
            is BridgeEvent.GrammarPublished -> launchSafely {
                bridge?.let { grammar.reload(it) }
                val met = grammar.meet(listOf(ev.id)).isNotEmpty()
                grammar.announced(ev.id)
                notices.banner = ev.note?.let { "📖 $it" }
                    ?: if (met || !ev.extended) grammar.newPage(ev.id) else "📖 ${bi("grammar.tutorAdded")}: ${grammar.page(ev.id)?.target ?: ev.title ?: ev.id}"
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
                if (screen == Screen.Friends) towns.load()
            }
            is BridgeEvent.TownQuestion -> {
                // a guest's question to answer, or the answer to the learner's: in Friends, and on its own screen
                notices.banner = "${TownQuestions.title(ev)}: ${ev.text}"
                questions.onEvent(ev)
            }
        }
    }

    /** Shows the app in [p] from now on (the connection sheet); null follows the learner profile again. */
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
        commitRoadRatings()
    }

    /**
     * The app left the screen. The event stream stops, so the background check notifies what comes
     * next (a live stream marks events as seen, and they would never be notified); speech stops.
     */
    fun onBackground() {
        foreground = false
        events.stop()
        speaker.stop()
        update.onBackground()
    }

    /** A notification was tapped: go where it points, once connected, without interrupting a run. */
    fun open(link: DeepLink) {
        if (bridge == null) {
            pendingLink = link
            return
        }
        pendingLink = null
        if (link == DeepLink.Chat) return chat.open() // a sheet, fine over any screen
        if (screen.inRun) return // leaving would lose the answers; the news shows in the app anyway
        if (VisitWorld.active) endVisit() // home first: the link leads into the learner's own village
        when (link) {
            DeepLink.Chat -> Unit
            DeepLink.Talk -> openTalk()
            DeepLink.Village -> openVillage()
            DeepLink.Packs -> openPacks()
            DeepLink.Readings -> openReadings()
            is DeepLink.Scene -> openScene(link.id, link.focus)
            is DeepLink.Module -> openModule(link.id)
            is DeepLink.Family -> {
                family.refresh() // the challenge may be newer than the list
                openFamily(link.challengeId)
            }
            is DeepLink.Question -> openQuestion(link.key, link.asked)
        }
    }

    /** Saves a review; [language]: another language's deck (null: the home language's, whose rusty words it polishes). */
    fun finishReview(results: List<Pair<String, Int>>, minutes: Int, language: String? = null, forms: kotlinx.serialization.json.JsonArray? = null): Job {
        if (language == null) content.reviewed(results)
        return sync.submitLater(Writes.reviews(results, minutes, language, forms))
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

    /** The building whose card the village opens when it's shown again (after its words' run); null: none. */
    private var buildingCard: String? = null

    /** The building card to open as the village shows (once), see [practiseUpgrade]. */
    fun takeBuildingCard(): String? = buildingCard.also { buildingCard = null }

    /**
     * "🎯 Vadi jih zdaj · Practise them now" on building [id]'s card: a short run of exactly the words its upgrade still
     * asks for ([si.lanisce.lani.game.BuildingWords.run]: new ones learned, met and rusty ones reviewed); afterwards
     * the village opens the card again, the upgrade one tap away. Also again from the run's own end, for what is still
     * missing.
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
     * reviews like a review's ([reviewed]: card id → quality), both counted at once, and the answers paid to the village.
     */
    fun finishUpgradeWords(result: si.lanisce.lani.game.RunResults, run: si.lanisce.lani.game.UpgradeRun, exercises: List<Exercise>, verdicts: List<Verdict>, minutes: Int) {
        game.rewardPractice(exercises, verdicts)
        for ((pack, words) in result.learned) {
            packs.saveWords(pack, words, minutes)
            scenes.learned(pack, words.map { it.first })
            content.learnedWords(pack, run.learn.filter { it.pack == pack }.mapNotNull { it.learn }, words)
        }
        if (result.reviewed.isNotEmpty()) finishReview(result.reviewed, minutes)
    }

    /**
     * The upgrade of building [id], from its words' run: done, the village shows it going up (its card doesn't open
     * again); false when it still can't be done (the card shows why).
     */
    fun upgradeNow(id: String): Boolean {
        if (!game.upgrade(id)) return false
        buildingCard = null
        screen = Screen.Village // not village(): the construction animation plays
        game.sync()
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
            )
        }
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
        )
    }

    /** Ratings pressed in the car that the road's service didn't hand to the outbox yet go now, and out to the node. */
    private fun commitRoadRatings() {
        viewModelScope.launch {
            val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { road.store.commit(si.lanisce.lani.data.Outbox(getApplication<Application>())) }.getOrDefault(0)
            }
            if (n > 0) sync.flush()
        }
    }

    // --- navigation ---------------------------------------------------------------------------

    /** Where "Later" on a run's intro leads: the screen the run was started from. */
    private var runFrom: Screen = Screen.Home

    /** The run on screen was started again from its own finish screen ("once more"): it skips its intro. */
    var again by mutableStateOf(false)
        private set

    /** Opens a practice run; the last run's reward no longer shows. */
    private fun startRun(s: Screen, again: Boolean = false) {
        game.clearReward()
        if (!screen.inRun) runFrom = screen
        this.again = again
        screen = s
    }

    /** Leaves a run that hasn't started (its intro's "Later"): back where it was started from. */
    fun leaveRun() {
        when (val from = runFrom) {
            Screen.Village -> village()
            is Screen.Packs -> openPacks(from.fromVillage)
            is Screen.Scene -> openScene(from.id, from.focus, from.building)
            is Screen.Villager -> openVillager(from.id)
            Screen.Villagers -> openVillagers()
            is Screen.Visit, is Screen.VisitScene -> if (visits.visit != null) openVisit() else home()
            is Screen.Treasure -> openTreasure(from.fromVillage)
            else -> home()
        }
    }

    fun home() {
        screen = Screen.Home
        refresh()
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
        if (screen.inRun) return@launchSafely
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

    fun openPacks(fromVillage: Boolean = false) {
        screen = Screen.Packs(fromVillage)
        refresh()
    }

    /** Back from the packs: to the village or home, wherever they were opened. */
    fun leavePacks(fromVillage: Boolean) = if (fromVillage) village() else home()

    /** "📖 Branje · Reading", the reading corner; [fromVillage]: opened from the village book or the chest. */
    fun openReadings(fromVillage: Boolean = false) {
        screen = Screen.Readings(fromVillage)
        launchSafely { bridge?.let { readings.reload(it) } } // the tutor may have written something new
    }

    /** Back from the reading corner: to the village or home. */
    fun leaveReadings(fromVillage: Boolean) = if (fromVillage) village() else home()

    /** Reads reading [id] aloud ("🎤 Beri na glas"); [fromCorner]: back leads to the reading corner, else to the village or home. */
    fun openReadAloud(id: String, fromVillage: Boolean, fromCorner: Boolean = true) {
        if (VisitWorld.active) return // a visited town's readings aren't the learner's
        screen = Screen.ReadAloud(id, fromVillage, fromCorner)
    }

    fun leaveReadAloud(fromVillage: Boolean, fromCorner: Boolean) = when {
        fromCorner -> openReadings(fromVillage)
        fromVillage -> village()
        else -> home()
    }

    /**
     * The learner's level in [language] ("sl" → "A2"; A1 before the dashboard is loaded): the storyteller tells at it. The
     * level a treasure just brought counts before the node has it ([ContentController.levelIn]).
     */
    fun levelIn(language: String): String = content.levelIn(language)

    // --- the treasure map (companion/GAME.md, "The treasure map") ------------------------------------------------------

    /** Opens the treasure map; [fromVillage]: its "‹" leads back to the village, else Home. */
    fun openTreasure(fromVillage: Boolean = screen is Screen.Village) {
        grammar.close()
        treasure.fromVillage = fromVillage
        screen = Screen.Treasure(fromVillage)
    }

    /** Back from the treasure map: where it was opened from. */
    fun leaveTreasure() {
        treasure.seen()
        if (treasure.fromVillage) village() else home()
    }

    /** Takes the map (the storyteller's, or from the grammar book) and shows the path. */
    fun takeMap() {
        treasure.take()
        if (screen !is Screen.Treasure) openTreasure()
    }

    /** A station of the treasure hunt, with the storyteller on the stage. */
    fun startStation(station: si.lanisce.lani.game.Station) = startChallenge(ChallengeOrigin.Treasure(station.id))

    /** Learns the next words of pack [id]; [again] (more words right after a session) skips the intro. */
    fun startPack(id: String, fromVillage: Boolean = false, again: Boolean = false) = launchSafely {
        packs.next(id)?.let { (p, words) -> startRun(Screen.PackLearn(p, words, fromVillage), again) }
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
        startRun(Screen.PackLearn(p, words, fromVillage = true, scene = id), again)
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
        startRun(Screen.PackLearn(p, words, fromVillage = true, scene = scene))
    }

    fun openFamily(challengeId: String? = null) = startRun(Screen.Family(challengeId))

    fun openVillage() {
        screen = Screen.Village
        game.sync()
    }

    /** The register of the village's people. */
    fun openVillagers() {
        screen = Screen.Villagers
        launchSafely { bridge?.let { villagers.reload(it) } } // the tutor may have brought someone new
    }

    /** One villager's page. */
    fun openVillager(id: String) {
        screen = Screen.Villager(id)
    }

    /** "Prijatelji · Friends": the towns linked with this one (the screen loads them). */
    fun openFriends() {
        screen = Screen.Friends
    }

    /** A question between towns ([key]): a guest's to answer, or ([asked]) the learner's with the answer; back to Friends. */
    fun openQuestion(key: String, asked: Boolean) {
        game.clearReward()
        questions.clearCheck()
        screen = Screen.TownQuestion(key, asked)
        questions.load()
    }

    /**
     * Visits linked town [id] (plan 2, §3; companion/README.md, "Visiting"): its village read-only, in its language
     * explained in the learner's base, in its culture's look. The learner's own village waits meanwhile (no days pass on
     * the phone, nothing of theirs is loaded), and the phone's voice speaks the town's language.
     */
    fun visitTown(id: String) {
        val t = towns.list?.towns?.firstOrNull { it.id == id } ?: return
        if (screen.inRun) return
        game.paused = true
        VisitWorld.enter(t.language, t.culture)
        speaker.clips = null // the node's voices speak the learner's own language; the phone speaks the town's
        speaker.refresh()
        showWorld()
        visits.start(t.id, t.name, t.learner, t.culture, t.language)
        screen = Screen.Visit(t.id)
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
        visits.visit?.let { screen = Screen.Visit(it.id) }
    }

    /** Walks into place [scene] of the visited town (its words to tap, a guest's dialog). */
    fun openVisitScene(scene: String) {
        visits.visit?.let { screen = Screen.VisitScene(it.id, scene) }
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

    /** Opens Talk at role-play [s] of a visit; leaving it leads back to the visit. */
    private fun openVisitTalk(s: si.lanisce.lani.data.Scenario) {
        val from = screen.takeIf { it is Screen.Visit || it is Screen.VisitScene } ?: visits.visit?.let { Screen.Visit(it.id) }
        openTalk()
        talkReturn = from
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

    /** Home from a visit: to the learner's own village, or ([friends]) back to the Friends list it started from. */
    fun leaveVisit(friends: Boolean = false) {
        endVisit()
        if (friends) openFriends() else openVillage()
    }

    /** The visit is over: the learner's own pair, culture and voices again, and their village's days go on. */
    private fun endVisit() {
        visits.end()
        VisitWorld.leave()
        speaker.clips = clips
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
        openTalk()
        talkReturn = from
        if (talk.state?.phase != TalkPhase.CHAT) {
            talk.pick(s)
            talk.start()
        }
    }

    /**
     * Steps into a scene of the village; [focus] is a happening key or an object slot; [building] the one the learner
     * tapped (the hut or house whose room it is, at its level). Back into the same scene from a word run or a talk, it is
     * the building it was last opened from.
     */
    fun openScene(id: String, focus: String? = null, building: String? = null) {
        if (building != null) sceneBuilding = id to building
        screen = Screen.Scene(id, focus, building ?: sceneBuilding?.takeIf { it.first == id }?.second)
    }

    /** The scene last opened from a building, and that building's id (see [openScene]). */
    private var sceneBuilding: Pair<String, String>? = null

    /** [state]'s village as scene [scene] sees it: in the building it was opened from, else its place's ([Homes.world]). */
    fun sceneWorld(scene: si.lanisce.lani.game.scene.SceneSpec, building: String? = (screen as? Screen.Scene)?.building): SceneWorld =
        game.state?.let { si.lanisce.lani.game.scene.Homes.world(scene, it, building, scenes.all) } ?: SceneWorld.ALL

    /** Back to the village from a run or a sheet; the construction animation doesn't replay. */
    fun village() {
        game.clearJustBuilt()
        screen = Screen.Village
        game.sync()
    }

    /** A gathering run; [again] (once more, from its result) skips the intro. */
    fun startGather(res: Res, again: Boolean = false) = startChallenge(ChallengeOrigin.Gather(res), again)
    fun startEvent() = startChallenge(ChallengeOrigin.Event)

    /** Today's step of village project [id], with its leader (companion/GAME.md, "Village projects"). */
    fun startProject(id: String) = startChallenge(ChallengeOrigin.Project(id))

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

    /** Where leaving Talk goes: the scene it was opened from, else Home. */
    private var talkReturn by mutableStateOf<Screen?>(null)

    fun openTalk() {
        chat.close()
        talkReturn = null
        screen = Screen.Talk
        talk.load()
    }

    /** Leaves Talk for where it was opened from. */
    fun leaveTalk() {
        val back = talkReturn
        talkReturn = null
        if (back != null) screen = back else home()
    }

    /** Talk was opened from a scene's dialog; leaving it goes back there. */
    val talkFromScene: Boolean get() = talkReturn is Screen.Scene

    /** Talk was opened for one role-play from somewhere else (a scene): its intro's "Later" leads back there. */
    val talkOpenedFrom: Boolean get() = talkReturn != null

    /** Where leaving Talk goes, for its back links: "‹ Prizor · Scene", "‹ Luka", or null (Home). */
    val talkBackLabel: String?
        get() = when (val r = talkReturn) {
            is Screen.Scene -> "‹ ${bi("sceneView.scene")}"
            is Screen.Villager -> "‹ " + (villagers.byId(r.id)?.name?.let(VillagerLogic::shortName) ?: r.id)
            Screen.Villagers -> "‹ ${bi("common.villagers")}"
            Screen.Village -> "‹ ${bi("common.village")}"
            is Screen.Visit, is Screen.VisitScene -> "‹ " + (visits.visit?.name?.takeIf { it.isNotBlank() } ?: bi("common.village"))
            else -> null
        }

    /** Opens the Talk screen at role-play [scenario] (e.g. after a scene's dialog), unless a conversation is running. */
    fun openTalk(scenario: String) {
        val from = screen.takeIf { it is Screen.Scene }
        openTalk()
        talkReturn = from
        launchSafely {
            val s = bridge?.scenarios()?.firstOrNull { it.id == scenario } ?: return@launchSafely
            if (talk.state == null) talk.pick(s)
        }
    }

    fun openProgress(fromVillage: Boolean = false) {
        screen = Screen.Progress(fromVillage)
        progress.load()
    }

    fun leaveProgress(fromVillage: Boolean) = if (fromVillage) village() else home()

    private fun launchSafely(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launchSafely(notices::fail, block)

    override fun onCleared() {
        hintVoice.release()
        speaker.shutdown()
        ambience.release()
    }
}
