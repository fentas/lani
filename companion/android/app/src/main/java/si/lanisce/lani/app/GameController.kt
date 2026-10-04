package si.lanisce.lani.app

import si.lanisce.lani.game.logged
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Visit
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Arrivals
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import si.lanisce.lani.ChallengeOrigin
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Family
import si.lanisce.lani.data.FriendshipList
import si.lanisce.lani.data.GameCache
import si.lanisce.lani.data.GameStore
import si.lanisce.lani.data.GameSync
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Module
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.Chest
import si.lanisce.lani.game.ChallengeResult
import si.lanisce.lani.game.ContentPool
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Guests
import si.lanisce.lani.game.LogEntry
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.TownFriendship
import si.lanisce.lani.game.scene.DayAct
import si.lanisce.lani.game.scene.DayFx
import si.lanisce.lani.game.scene.DaySky
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Stage
import si.lanisce.lani.game.scene.Stories
import si.lanisce.lani.game.scene.StoryTonight
import si.lanisce.lani.ui.game.ChestNews
import si.lanisce.lani.ui.game.GiftFrom
import si.lanisce.lani.ui.game.Reward
import si.lanisce.lani.ui.game.challengeReviews
import si.lanisce.lani.ui.game.correctCount
import si.lanisce.lani.ui.game.earnPairs
import si.lanisce.lani.ui.game.formatRes
import si.lanisce.lani.ui.scene.DialogRun
import si.lanisce.lani.ui.villagers.FriendGain
import si.lanisce.lani.widget.VillageWidget
import java.time.LocalDate
import si.lanisce.lani.game.Challenge as GameChallenge
import si.lanisce.lani.l10n.bi

/**
 * What a finished happening brought the village: [paid] (what fit in the stores) of [due] (its reward after
 * the mistakes), and [help] 🤝; nothing when it was paid today already ([again]).
 */
data class HappeningPaid(val paid: Map<Res, Int>, val due: Map<Res, Int>, val again: Boolean, val help: Int = 0) {
    /** The stores were too full for part of it. */
    val full: Boolean get() = !again && due.any { (r, n) -> (paid[r] ?: 0) < n }
}

/**
 * "Moja vas": the village, its cache on the phone and its sync with the node, and what practice
 * earns for it. Offline play works on the cache; on a conflict the node's copy wins.
 */
class GameController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: Prefs,
    private val bridge: () -> Bridge?,
    private val content: ContentController,
    private val notices: Notices,
    private val canSpeak: () -> Boolean,
    /** Loads the dashboard and modules; a sync does it first when there is no dashboard yet. */
    private val reload: suspend () -> Unit,
    /** Persists the flashcard reviews answered inside a challenge. */
    private val saveReviews: (List<Pair<String, Int>>, Int) -> Unit,
    /** Records the answers of a run on a word pack's words (a festival's) like a pack session: pack id, word id → quality, minutes. */
    private val saveWords: (String, List<Pair<String, Int>>, Int) -> Unit = { _, _, _ -> },
    /** The village's cast from the node (who can move in; see game/villagers/Residents). */
    private val cast: () -> List<Villager> = { emptyList() },
) {
    private val store = GameStore(context)
    private val io = Dispatchers.IO.limitedParallelism(1) // cache writes stay in order
    private val lock = Mutex()
    private var rev = 0L
    private var dirty = false
    private var pushJob: Job? = null
    private var pushAgain = false

    /** The village; null until the cache or the node delivered one. */
    var state by mutableStateOf<GameState?>(null)
        private set
    /** What happened while the app was closed (tick news), shown once. */
    var news by mutableStateOf<List<LogEntry>>(emptyList())
        private set
    /**
     * There is no village yet, on the node or the phone: the learner chooses where it will be before it is founded (the
     * first start's "Kje bo tvoja vas?", ui/game/FoundingScreen; see [found]).
     */
    var placeNeeded by mutableStateOf(false)
        private set
    var introSeen by mutableStateOf(true)
        private set
    /** Bumped on wins and age-ups; the village scene celebrates. */
    var celebrate by mutableIntStateOf(0)
        private set
    /** The building that just went up, for the scene's construction animation. */
    var justBuilt by mutableStateOf<String?>(null)
        private set
    /** Resources earned by the last finished run, for its finish screen. */
    var lastReward by mutableStateOf<Reward?>(null)
        private set
    /** The village has changes the last push couldn't deliver. */
    var unsynced by mutableStateOf(false)
        private set
    /** What came into the chest since it was last opened: the HUD's chest shows a dot (kept on the phone). */
    var chestNews by mutableStateOf(ChestNews())
        private set
    /**
     * A villager's quest was done: their friendship grows (set by the view model, see
     * [VillagerController.questDone]); the change shows on the result card.
     */
    var onQuestDone: (Quest) -> FriendGain? = { null }
    /** People born here or who moved in (not the cast): the tutor may give them a personality (set by the view model). */
    var onArrived: (List<Resident>) -> Unit = {}
    /**
     * The grammar pages a module practises (the book's pages that list it, the pages its exercises name; set by the view
     * model): a drill of the tutor's found for a task it shares a page with ([si.lanisce.lani.game.QuestQueue.link]).
     */
    var modulePages: (String) -> Set<String> = { emptySet() }
    /**
     * How villager [id]'s friendship changed between two states (a project step, a festival, a surprise), for the
     * result card (set by the view model).
     */
    var gainOf: (id: String, before: GameState, after: GameState) -> FriendGain? = { _, _, _ -> null }
    /**
     * A visit to another town is on (set by the view model): the village waits, no days pass and nobody moves in, since
     * its rules would read the visited town's culture meanwhile. It catches up when the visit ends ([sync]).
     */
    @Volatile
    var paused = false

    init {
        scope.launch {
            introSeen = prefs.gameIntroSeen()
            // what came in before this start and wasn't seen yet, with what came in since
            prefs.chestUnseen().takeIf { it.isNotEmpty() }?.let { chestNews = ChestNews(it + chestNews.unseen) }
            withContext(Dispatchers.IO) { store.read() }?.let { c ->
                if (state == null) {
                    state = c.state; rev = c.rev; dirty = c.dirty
                    si.lanisce.lani.game.villagers.Mentions.known = Residents.present(c.state, LocalDate.now())
                }
            }
        }
    }

    // --- sync ---------------------------------------------------------------------------------

    /** App start, resume, village open: reconcile with the node, then apply the days that passed. */
    fun sync(reloadFirst: Boolean = false): Job = scope.launch {
        if (bridge() == null || paused) return@launch
        try {
            if (reloadFirst || content.dashboard == null) runCatching { reload() }
            content.loadModuleExercises()
            lock.withLock {
                reconcile()
                tick()
            }
            guests()
            friendships()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) { // a game bug must never block practice
            notices.villageFailed(e)
        }
    }

    /** Sends village changes the node doesn't have yet (resume, reconnect). */
    fun pushIfDirty() {
        if (dirty) schedulePush()
    }

    /** Redraws the home-screen widget, in order with the cache writes it reads. */
    fun refreshWidget() {
        scope.launch(io) { VillageWidget.refresh(context) }
    }

    private suspend fun reconcile() {
        val b = bridge() ?: return
        val local = state?.let { GameCache(rev, it, dirty) }
        val remote = try {
            b.game()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline without a village: the learner chooses where it will be (it is pushed when the node answers)
            if (local == null) placeNeeded = true
            return // offline: play on the cached village, push later
        }
        when (val d = GameSync.reconcile(local, remote)) {
            is GameSync.Decision.Adopt -> setGame(d.snapshot.state, d.snapshot.rev, dirty = false)
            is GameSync.Decision.Push -> { setGame(d.cache.state, d.cache.rev, dirty = true); schedulePush() }
            // nothing anywhere: the learner chooses where the village will be first (see [found])
            GameSync.Decision.Create -> placeNeeded = true
        }
    }

    private fun tick() {
        val s0 = state ?: return
        val now = System.currentTimeMillis()
        // a village whose culture introduces its people: once, those the learner never really met are strangers again
        val r = GameEngine.tick(Arrivals.migrate(s0), LocalDate.now(), now, pool())
        // Only with a fresh module list, or tutor quests of modules we just didn't load would vanish.
        val s1 = if (content.modulesLoaded) GameEngine.withTutorQuests(r.state, content.modules, now, modulePages) else r.state
        val (s2, residents) = Residents.reconcile(s1, cast(), LocalDate.now())
        val (s2v, visit) = visitToday(s2)
        val people = residents + visit
        val (s3, gifts) = giftsDue(s2v.logged(now, *people.toTypedArray()), now)
        if (s3 != s0) commit(s3)
        announce(s1, s2)
        // Named arrivals and departures replace the upkeep's plain "+1 villager" lines.
        val plain = if (residents.isEmpty()) r.news else r.news.filterNot { it.emoji == "🧑" || it.emoji == "😢" }
        val all = plain + people.map { (e, t) -> LogEntry(now, e, t) } + gifts
        if (all.isNotEmpty()) news = news + all
    }

    /**
     * Friends who reached a gift's level before the chest existed (an older save) give it now, if they're in the
     * village today; returns the state and the gifts' lines for "while you were away".
     */
    private fun giftsDue(s: GameState, now: Long): Pair<GameState, List<LogEntry>> {
        if (cast().isEmpty()) return s to emptyList() // who is here isn't known yet
        val (n, gifts) = Chest.catchUp(s, Residents.present(s, LocalDate.now()), now)
        return n to gifts.map { LogEntry(now, it.item.emoji, it.text) }
    }

    /** Who comes by today (once a day, from the cast who don't live here yet), and its chronicle line. */
    private fun visitToday(s: GameState): Pair<GameState, List<Pair<String, String>>> {
        val today = LocalDate.now()
        val cast = cast()
        if (cast.isEmpty() || s.visitor?.on == today.toString()) return s to emptyList()
        val v = Residents.visit(s, cast, today) ?: return s.copy(visitor = Visit("", today.toString())) to emptyList()
        val who = cast.first { it.id == v.id }
        // a chronicle line: stays as the chronicle writes (its text comes per language in a later step)
        return s.copy(visitor = v) to listOf("🧳" to "${who.name} je danes na obisku · ${who.name} is visiting today")
    }

    /** Matches the residents to the population now (e.g. once the cast has loaded). */
    fun settleResidents() {
        if (paused) return
        val s0 = state ?: return
        val now = System.currentTimeMillis()
        val (s1r, residents) = Residents.reconcile(Arrivals.migrate(s0), cast(), LocalDate.now())
        val (s1, visit) = visitToday(s1r)
        val people = residents + visit
        val (s2, gifts) = giftsDue(s1.logged(now, *people.toTypedArray()), now)
        if (s2 != s0) commit(s2)
        announce(s0, s1)
        val all = people.map { (e, t) -> LogEntry(now, e, t) } + gifts
        if (all.isNotEmpty()) news = news + all
    }

    /** Someone new moved in or was born between [before] and [after]: [onArrived] tells the tutor. */
    private fun announce(before: GameState, after: GameState) {
        val new = after.residents.filter { r -> r.name != null && before.residents.none { it.id == r.id } }
        if (new.isNotEmpty()) onArrived(new)
    }

    private fun pool() = ContentPool(
        cards = content.dashboard?.pool.orEmpty(),
        moduleExercises = content.moduleExercises(),
        canSpeak = canSpeak(),
        present = presentNames(),
    )

    /** The names of who is in the village today, for new requests; null without a cast (everyone may ask). */
    private fun presentNames(): Set<String>? {
        val s = state ?: return null
        val cast = cast()
        if (cast.isEmpty()) return null
        val ids = Residents.present(s, LocalDate.now()) ?: return null
        return ids.mapNotNull { id -> cast.firstOrNull { it.id == id }?.name ?: s.residents.firstOrNull { it.id == id }?.name }.toSet()
    }

    private fun setGame(s: GameState, rev: Long, dirty: Boolean) {
        val before = state
        state = s
        placeNeeded = false
        // who the village's texts may name now: the people here whom the learner has met (game/villagers/Mentions)
        si.lanisce.lani.game.villagers.Mentions.known = Residents.present(s, LocalDate.now())
        this.rev = rev
        this.dirty = dirty
        val c = GameCache(rev, s, dirty)
        scope.launch(io) {
            runCatching { store.write(c) }
            VillageWidget.refresh(context) // the home-screen widget draws from this cache
        }
        // a good or a tool came into the chest (here, or played on another phone): new until the chest is opened
        showChest(chestNews.after(before?.chest, s.chest))
    }

    /** The chest was opened: what came into it is seen, and the HUD's dot goes. */
    fun sawChest() = showChest(chestNews.opened())

    private fun showChest(n: ChestNews) {
        if (n == chestNews) return
        chestNews = n
        scope.launch { prefs.saveChestUnseen(n.unseen) }
    }

    /**
     * Friendship with villager [id] grows (see game/villagers/Bonds and companion/VILLAGERS.md); a level may bring their
     * gift. [meet]: this is their introduction ([Arrivals]): from now on they're met.
     */
    fun befriend(id: String, points: Int, memory: Memory? = null, training: Boolean = false, meet: Boolean = false) {
        val s = state ?: return
        commit(Bonds.add(s, id, points, LocalDate.now(), memory, training, now = System.currentTimeMillis(), meet = meet))
    }

    /** Jan helped someone (a talk with a villager, a family answer): +[n] 🤝 for a moba. */
    fun helped(n: Int) {
        val s = state ?: return
        val h = GameEngine.helped(s, n)
        if (h != s) commit(h)
    }

    /** A local change: cache it and send it to the node. */
    private fun commit(s: GameState) {
        setGame(s, rev, dirty = true)
        schedulePush()
    }

    private fun schedulePush() {
        if (pushJob?.isActive == true) { pushAgain = true; return }
        pushJob = scope.launch {
            do {
                pushAgain = false
                val b = bridge() ?: return@launch
                val sent = state ?: return@launch
                val r = try {
                    b.putGame(rev, sent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    unsynced = true
                    return@launch // offline: stays dirty, pushed on the next sync
                }
                unsynced = false
                val c = GameSync.afterPut(sent, state ?: sent, r)
                setGame(c.state, c.rev, c.dirty)
                if (c.dirty) pushAgain = true
            } while (pushAgain)
        }
    }

    // --- actions ------------------------------------------------------------------------------

    /**
     * Founds the village where the learner chose ([Founding]: its region's, on its landscape, its seed), its first day
     * ticked, and sends it to the node. Only while there is none: a village is founded once (another phone's, or the
     * node's, arriving meanwhile stays).
     */
    fun found(f: si.lanisce.lani.game.Founding): Job = scope.launch {
        lock.withLock {
            if (state != null) { placeNeeded = false; return@withLock }
            setGame(f.village(System.currentTimeMillis()), 0, dirty = true)
            tick()
        }
        schedulePush()
    }

    fun finishIntro() {
        introSeen = true
        scope.launch { prefs.saveGameIntroSeen() }
    }

    fun dismissNews() { news = emptyList() }

    /** A new run starts: the previous finish screen's reward is gone. */
    fun clearReward() { lastReward = null }

    /** Back in the village from elsewhere: the construction animation doesn't replay. */
    fun clearJustBuilt() { justBuilt = null }

    /** Builds [type] ([withMoba]: the neighbours help, for 🤝); returns the new building's id, or null if it wasn't possible. */
    fun build(type: BuildingType, withMoba: Boolean = false): String? {
        val s = state ?: return null
        // with the cast: a workplace whose person has nowhere to live waits for a bed (GameEngine.buildOptions)
        val n = GameEngine.build(s, type, System.currentTimeMillis(), withMoba, cast())
        val id = n.buildings.firstOrNull { b -> s.buildings.none { it.id == b.id } }?.id ?: return null
        commit(n)
        justBuilt = id
        // a workplace brings its person (the beehive its beekeeper): they move in now, and wait to be met
        settleResidents()
        return id
    }

    /**
     * What the plot chooser chose, confirmed on [plot] (the chosen one, or the suggestion; see [si.lanisce.lani.game.PlotChoice]):
     * the new building built there and paid for now, as [build] does, or the building moved there (free; one on that plot
     * swaps places with it). Returns the building's id, or null if it wasn't possible (the plot taken meanwhile, the
     * resources gone).
     */
    fun place(choice: si.lanisce.lani.game.PlotChoice, plot: Int? = choice.chosen): String? {
        val s = state ?: return null
        val n = choice.confirm(s, System.currentTimeMillis(), cast(), plot) ?: return null
        val id = choice.moving ?: n.buildings.firstOrNull { b -> s.buildings.none { it.id == b.id } }?.id ?: return null
        commit(n)
        justBuilt = id // a new building goes up; a moved one goes up again at its new place
        // a workplace brings its person (the beehive its beekeeper): they move in now, and wait to be met
        if (choice.moving == null) settleResidents()
        return id
    }

    /**
     * What the upgrade of a building (by id) asks for of its words now ("Knowledge builds", companion/GAME.md "Upgrades"):
     * set by the view model, from the scenes, the packs and the learner's words; null asks for none.
     */
    var upgradeWords: (GameState, String) -> si.lanisce.lani.game.UpgradeWords? = { _, _ -> null }

    /** Upgrades [buildingId] one level ([withMoba]: the neighbours help); not until its words are known ([upgradeWords]). */
    fun upgrade(buildingId: String, withMoba: Boolean = false): Boolean {
        val s = state ?: return false
        val n = GameEngine.upgrade(s, buildingId, System.currentTimeMillis(), withMoba, upgradeWords(s, buildingId))
        if (n == s) return false
        commit(n)
        justBuilt = buildingId // replays the construction animation on the upgraded building
        return true
    }

    fun repair(buildingId: String, withMoba: Boolean = false): Boolean {
        val s = state ?: return false
        val n = GameEngine.repair(s, buildingId, System.currentTimeMillis(), withMoba)
        if (n == s) return false
        commit(n)
        return true
    }

    // --- towns: guests here, this village's visits there (game/Guests) -----------------------------------------

    /** What guests from linked towns brought, from the node's guest book: applied to the village once each, announced. */
    private suspend fun guests() {
        val b = bridge() ?: return
        val entries = try {
            b.townGuests() ?: return // an older bridge
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // offline: next time
        }
        lock.withLock {
            val s = state ?: return@withLock
            val (n, news) = Guests.apply(s, entries, LocalDate.now(), System.currentTimeMillis())
            if (n == s) return@withLock
            commit(n)
            if (news.isNotEmpty()) this.news = this.news + news.map { LogEntry(System.currentTimeMillis(), it.emoji, it.text) }
            news.lastOrNull()?.let { notices.banner = "${it.emoji} ${it.text}" }
        }
    }

    /**
     * A request of [giver]'s done on a visit to town [town] ([name]), answered [key]: the thank-you good ([thanks], a
     * good of [culture]) comes into the chest as the village's own. Returns that good, null when none (or done before).
     */
    fun visitHelped(key: String, town: String, name: String, giver: String, culture: String, thanks: String?): String? {
        val s = state ?: return null
        val (n, good) = Guests.helped(s, key, town, name, giver, culture, thanks, LocalDate.now(), System.currentTimeMillis())
        if (n != s) commit(n)
        return good
    }

    /** A gift ([good], or none: a message) left on a visit to town [town], answered [key]. */
    fun visitGave(key: String, town: String, name: String, good: String?): Boolean =
        change { Guests.gave(it, key, town, name, good, LocalDate.now(), System.currentTimeMillis()) }

    /** A trade at town [town]'s market, answered [key]: [give] leaves the chest and the stores, [get] comes in. */
    fun visitTraded(key: String, town: String, name: String, give: si.lanisce.lani.data.Bundle, get: si.lanisce.lani.data.Bundle): Boolean =
        change { Guests.traded(it, key, town, name, give, get, LocalDate.now(), System.currentTimeMillis()) }

    /** Villager [villager] ([who]) of town [town] ([name]) was met on a visit (a talk, a scene's dialog). */
    fun visitMet(town: String, name: String, villager: String, who: String): Boolean =
        change { Guests.met(it, town, name, villager, who, LocalDate.now(), System.currentTimeMillis()) }

    // --- friendship between towns (game/TownFriendship) --------------------------------------------------------------

    /** What friends' towns asked of the learner (their trouble, a move offered, a feast to share), shown once each. */
    private val alerted = HashSet<String>()

    /** The friendships (GET /towns/friendship): what they bring, applied once each. */
    private suspend fun friendships() {
        val b = bridge() ?: return
        val list = try {
            b.friendships() ?: return // an older bridge
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // offline: next time
        }
        applyFriendships(list)
    }

    /**
     * What the friendships in [list] (also the Friends screen's) bring the village: the shared feasts and the people who
     * moved, applied once each and announced; the tutor hears who moved in. A friend's news the learner can act on (help
     * for their trouble, a move they offered, their feast to share) shows once as a notice.
     */
    suspend fun applyFriendships(list: FriendshipList) {
        if (paused) return
        lock.withLock {
            val s = state ?: return@withLock
            val now = System.currentTimeMillis()
            val a = TownFriendship.apply(s, list, LocalDate.now(), now)
            if (a.state != s) commit(a.state)
            if (a.news.isNotEmpty()) news = news + a.news.map { LogEntry(now, it.first, it.second) }
            if (a.feasts > 0) celebrate++
            if (a.arrived.isNotEmpty()) onArrived(a.arrived)
            val alert = TownFriendship.alerts(list, a.state).firstOrNull { alerted.add(it.first) }
            (a.news.lastOrNull()?.let { "${it.first} ${it.second}" } ?: alert?.second)?.let { notices.banner = it }
        }
    }

    /** Help sent against town [town]'s ([name]) trouble, answered [key]: the village pays for it, once. */
    fun visitAided(key: String, town: String, name: String): Boolean =
        change { TownFriendship.aided(it, key, town, name, LocalDate.now(), System.currentTimeMillis()) }
    /** The questions answered between this village and linked towns (game/TownQuestionTies), counted in the ties once each. */
    fun questionsAnswered(list: si.lanisce.lani.data.TownQuestionsList): Boolean =
        change { si.lanisce.lani.game.TownQuestionTies.apply(it, list, LocalDate.now(), System.currentTimeMillis()) }

    // --- the chest -------------------------------------------------------------------------------

    /** Kovač Tone forges tool [giver]'s at the smithy (companion/GAME.md, "The chest"). */
    fun forge(giver: String): Boolean = change { Chest.forge(it, giver, System.currentTimeMillis()) }

    /** Buys one [good] from [seller] (the pedlar, the merchant, the market) for the chest; false when it couldn't. */
    fun buy(seller: String, good: String): Boolean = change { Chest.buy(it, seller, good, LocalDate.now(), System.currentTimeMillis()) }

    /** Sells one [good] to the merchant or at the market; returns what it brought (empty when it couldn't). */
    fun sell(good: String): Map<Res, Int> {
        val s = state ?: return emptyMap()
        val (n, got) = Chest.sell(s, good, System.currentTimeMillis())
        if (n != s) commit(n)
        return got
    }

    /** A feast under the linden; the village celebrates. */
    fun feast(): Boolean = change { Chest.feast(it, LocalDate.now(), System.currentTimeMillis()) }.also { if (it) celebrate++ }

    /** Jan gives [good] to villager [id] (see [VillagerController.give] for the friendship shown). */
    fun give(good: String, id: String, memory: Memory?): Boolean =
        change { Chest.give(it, good, id, LocalDate.now(), System.currentTimeMillis(), memory) }

    /**
     * The questions closing chest reading [id] were answered ([verdicts], one a question): the first time they pay 📜 as
     * answers do, less [cost] percent for the looks back at the text; returns what they paid (nothing when it was read before).
     */
    fun finishReading(id: String, verdicts: List<Verdict>, cost: Int = 0): Map<Res, Int> {
        val s = state ?: return emptyMap()
        val (n, paid) = si.lanisce.lani.game.Readings.finish(s, id, verdicts, cost)
        if (n != s) commit(n)
        return paid
    }

    /** A book of the storyteller's shelf was read to its questions' end: [key] ("primorska/book:zlatorog/A1") is read. */
    fun bookRead(key: String) {
        val s = state ?: return
        if (key !in s.read) commit(s.copy(read = s.read + key))
    }

    /**
     * Applies a local change [f] that also answers something (the grammar book's pages newly met: [GrammarBook]); null
     * when the village isn't loaded.
     */
    fun <T> record(f: (GameState) -> Pair<GameState, T>): T? {
        val s = state ?: return null
        val (n, r) = f(s)
        if (n != s) commit(n)
        return r
    }

    /** Applies a local change [f] (the treasure map's: game/Treasure); false when nothing changed. */
    fun apply(f: (GameState) -> GameState): Boolean = change(f)

    // --- the treasure map (game/Treasure) --------------------------------------------------------------------------

    /** A station of the treasure hunt's run, by its id (set by the view model: the treasure's content is gathered there). */
    var treasureRun: suspend (String) -> GameChallenge? = { null }

    /** The stations the hunt needs passed on this phone now (set by the view model). */
    var treasureRequired: () -> List<si.lanisce.lani.game.Station> = { emptyList() }

    /** Every station passed: the treasure is dug up ([si.lanisce.lani.game.Treasure.dig]); null when it can't be yet. */
    fun dig(required: List<si.lanisce.lani.game.Station>): si.lanisce.lani.game.Dug? = notices.village {
        record { si.lanisce.lani.game.Treasure.dig(it, required, LocalDate.now(), System.currentTimeMillis()) }
    }?.also { celebrate++ }

    /** Applies a local change; false when nothing changed. */
    private fun change(f: (GameState) -> GameState): Boolean {
        val s = state ?: return false
        val n = f(s)
        if (n == s) return false
        commit(n)
        return true
    }

    /** Advances to the next age; returns it, or null when the checklist isn't complete. */
    fun advance(): Age? {
        val s = state ?: return null
        val n = GameEngine.advance(s, content.wordsKnown, System.currentTimeMillis(), content.targetLevel, content.wordsRusty)
        if (n.age == s.age) return null
        commit(n)
        celebrate++
        return n.age
    }

    /** The challenge [origin] asks for, or null (with a banner) when there is nothing to practise for it. */
    suspend fun challenge(origin: ChallengeOrigin): GameChallenge? {
        content.loadModuleExercises()
        val s = state ?: return null
        val p = pool()
        val c = when (origin) {
            ChallengeOrigin.Event -> GameEngine.eventChallenge(s, p)
            is ChallengeOrigin.Gather -> GameEngine.gatherChallenge(s, origin.res, p)
            is ChallengeOrigin.Quest -> GameEngine.questChallenge(s, origin.questId, p)
            is ChallengeOrigin.Project -> GameEngine.projectChallenge(s, origin.id, p, LocalDate.now())
            is ChallengeOrigin.Festival -> GameEngine.festivalChallenge(s, origin.id, LocalDate.now())
            ChallengeOrigin.Surprise -> GameEngine.surpriseChallenge(s, p, LocalDate.now()) { id -> cast().firstOrNull { it.id == id }?.name?.substringAfterLast(' ') ?: Chest.nameOf(s, id) }
            is ChallengeOrigin.Treasure -> treasureRun(origin.station)
        }
        if (c == null || c.exercises.isEmpty()) {
            notices.banner = bi("gameController.nothingPractiseYet")
            return null
        }
        return c
    }

    // --- rewards ------------------------------------------------------------------------------

    /** How many answers of the last [earn] paid half (past the day's full-pay ones). */
    private var tired = 0

    /** Credits answers to the village, less [cost] percent (looks back at a text: [si.lanisce.lani.game.Peeks]); returns what was earned. */
    private fun earn(pairs: List<Pair<Res, Verdict>>, cost: Int = 0): Map<Res, Int> {
        tired = 0
        val s = state ?: return emptyMap()
        if (pairs.isEmpty()) return emptyMap()
        tired = (pairs.size - GameEngine.freshAnswersLeft(s)).coerceAtLeast(0)
        val (n, earned) = GameEngine.earn(s, pairs, cost)
        if (n != s) commit(n)
        return earned
    }

    /** After a review or a pack: every answer feeds the village. */
    fun rewardPractice(exercises: List<Exercise>, verdicts: List<Verdict>) = notices.village {
        lastReward = Reward(earn(earnPairs(exercises, verdicts, resourceOf = GameEngine::resourceOf)), tired = tired)
    }

    /** After a module: earn, and complete its tutor quest if it has one. */
    fun rewardModule(module: Module, verdicts: List<Verdict>) = notices.village {
        val earned = earn(earnPairs(module.exercises, verdicts, resourceOf = GameEngine::resourceOf))
        val quest = state?.quests?.firstOrNull { it.source == QuestSource.TUTOR && it.moduleId == module.id && !it.done }
        var result: ChallengeResult? = null
        var friend: FriendGain? = null
        if (quest != null) {
            // the pages of the rules it missed: what "🎯 Vadi, kar ti ne gre" practises
            val missed = si.lanisce.lani.game.Treasure.missed(module.exercises, verdicts.map { it == Verdict.CORRECT })
            val (n, r) = GameEngine.completeQuest(
                state!!, quest.id, correctCount(verdicts), module.exercises.size, System.currentTimeMillis(), giverId(quest), LocalDate.now(), missed,
            )
            commit(n)
            result = r
            if (r.won) {
                celebrate++
                friend = onQuestDone(quest)
            }
        }
        lastReward = Reward(
            earned, result, correctCount(verdicts), module.exercises.size, answered = verdicts.size, almost = verdicts.count { it == Verdict.ALMOST },
            tired = tired, friend = friend, from = quest?.let { GiftFrom(it.emoji, it.giver) },
        )
    }

    /** Conversation feeds wisdom: one entry per line Jan said. */
    fun rewardTalk(verdicts: List<Verdict>): Reward? = notices.village {
        Reward(
            earn(verdicts.map { Res.WISDOM to it }),
            correct = verdicts.count { it == Verdict.CORRECT },
            total = verdicts.size,
            almost = verdicts.count { it == Verdict.ALMOST },
            tired = tired,
        )
    }

    /** Answering a family challenge pays wisdom, and helps (🤝): someone asked Jan something. */
    fun rewardFamily() = notices.village {
        val earned = earn(List(Family.REWARD_ANSWERS) { Res.WISDOM to Verdict.CORRECT })
        helped(Catalog.HELP_FAMILY)
        lastReward = Reward(earned, tired = tired, help = Catalog.HELP_FAMILY)
    }

    /** The villager id of [q]'s giver in the cast, if they're in it (their thank-you good). */
    private fun giverId(q: Quest): String? = cast().firstOrNull { it.name == q.giver }?.id

    /**
     * A scene's happening is done (its dialog played to the end): pays its [reward] once a day, less after
     * [mistakes], and writes [entry] to the chronicle. Null when the village isn't loaded (or the step failed).
     */
    fun finishHappening(key: String, reward: Map<Res, Int>, mistakes: Int, entry: Pair<String, String>): HappeningPaid? = notices.village {
        val s = state ?: return@village null
        val today = LocalDate.now()
        val (n, paid) = GameEngine.completeHappening(s, key, reward, mistakes, today, System.currentTimeMillis(), entry)
        if (n != s) commit(n)
        HappeningPaid(paid, GameEngine.happeningPay(reward, mistakes), again = Happenings.done(s, key, today), help = n.help - s.help)
    }

    /**
     * A scene's dialog [run] ended in [scene]: the sky its cues left stays there and over the village for the rest of
     * the day (see [DaySky]), the effects it keeps stay in the scene (see [DayFx]), and so do the people it keeps where it
     * moved them (see [DayAct]; [spec], the scene, knows their spots); nothing when the village isn't loaded.
     */
    fun keepScene(scene: String, run: DialogRun, spec: SceneSpec? = null) {
        notices.village {
            val s = state ?: return@village
            val today = LocalDate.now()
            val sky = DaySky.keep(s, scene, run.dialog, run.sky(DaySky.of(s, today, scene)), today)
            val fx = DayFx.keep(sky, scene, run.dialog, run.fx(DayFx.of(s, today, scene)), today)
            val n = spec?.let { sc -> DayAct.keep(fx, scene, run.dialog, Stage.shown(sc.art, run.act(Stage.base(sc, DayAct.of(s, today, scene)))), today) } ?: fx
            if (n != s) commit(n)
        }
    }

    /**
     * The learner heard variant [dialog] of happening [key] to its end: the next time, another of its variants
     * ([si.lanisce.lani.game.scene.DialogVariants]); the bridge sees what was heard with the village.
     */
    fun dialogHeard(key: String, dialog: String) {
        notices.village {
            val s = state ?: return@village
            val n = si.lanisce.lani.game.scene.DialogVariants.record(s, key, dialog, LocalDate.now())
            if (n != s) commit(n)
        }
    }

    /**
     * Someone at a spot of the landscape told [k]'s talk to its end: the next day he is there, the next talk of his story
     * ([si.lanisce.lani.game.scene.Keepers.told]); the bridge sees how far with the village.
     */
    fun keeperTold(k: si.lanisce.lani.game.scene.ActiveKeeper) {
        notices.village {
            val s = state ?: return@village
            val n = si.lanisce.lani.game.scene.Keepers.told(s, k, LocalDate.now())
            if (n != s) commit(n)
        }
    }

    /** The learner heard [t] to its end tonight: the next evening brings the next story, or its next chapter (see [Stories]). */
    fun storyHeard(t: StoryTonight) {
        storyHeardAt = java.time.LocalDateTime.now()
        notices.village {
            val s = state ?: return@village
            commit(Stories.record(s, t, LocalDate.now()))
        }
    }

    /**
     * When tonight's story was last heard to its end, while the app runs (not kept): the storyteller walks home from the
     * fire a few minutes after it ([si.lanisce.lani.game.villagers.Routine.Day.storyAt]).
     */
    var storyHeardAt: java.time.LocalDateTime? by mutableStateOf(null)
        private set

    /**
     * Ends a challenge: earn, persist its flashcard reviews and the words it asked about (a festival's pack: whether it's
     * celebrated or only tried, on its day or a grace day), resolve the event or quest. [looks]: how often its text ([GameChallenge.text],
     * the day's letter) was brought back while a question was open; at the learner's [level] they take a little off what it
     * pays ([si.lanisce.lani.game.Peeks]), never the answers.
     */
    fun finishChallenge(origin: ChallengeOrigin, c: GameChallenge, verdicts: List<Verdict>, minutes: Int, looks: Int = 0, level: String = "A1"): Reward? = notices.village {
        val reviews = challengeReviews(c.cardIds, verdicts)
        if (reviews.isNotEmpty()) saveReviews(reviews, minutes)
        c.pack?.let { pack -> PackSession.results(c.packWords, verdicts).takeIf { it.isNotEmpty() }?.let { saveWords(pack, it, minutes) } }
        val cost = if (c.text != null) si.lanisce.lani.game.Peeks.cost(level, looks) else 0
        val earned = earn(earnPairs(c.exercises, verdicts, c.skills) { GameEngine.resourceOf(it) }, cost)
        val correct = correctCount(verdicts)
        val total = c.exercises.size
        val now = System.currentTimeMillis()
        val quest = (origin as? ChallengeOrigin.Quest)?.let { o -> state?.quests?.firstOrNull { it.id == o.questId && !it.done } }
        val today = LocalDate.now()
        val before = state
        // who grows closer with it, for the result card: the project's leader, the festival's, who the surprise is about
        val person: String? = before?.let { s0 ->
            when (origin) {
                is ChallengeOrigin.Project -> si.lanisce.lani.game.Projects.spec(origin.id)?.leader
                is ChallengeOrigin.Festival -> si.lanisce.lani.game.Calendar.byId(origin.id)?.let { si.lanisce.lani.game.Calendar.leader(s0, it, today) }
                ChallengeOrigin.Surprise -> si.lanisce.lani.game.Surprises.playable(s0, today)?.who
                else -> null
            }
        }
        val result = when (origin) {
            ChallengeOrigin.Event -> state?.let { GameEngine.resolveEvent(it, correct, total, now) }
            is ChallengeOrigin.Quest -> state?.let {
                // the pages of the rules it missed (the request's own when its questions name none)
                val rights = verdicts.map { v -> v == Verdict.CORRECT }
                val missed = si.lanisce.lani.game.Treasure.missed(c.exercises, rights).ifEmpty { if (rights.all { r -> r }) emptyList() else c.pages }
                GameEngine.completeQuest(it, origin.questId, correct, total, now, quest?.let(::giverId), today, missed)
            }
            is ChallengeOrigin.Project -> state?.let { si.lanisce.lani.game.Projects.finishStep(it, origin.id, correct, total, today, now) }
            is ChallengeOrigin.Festival -> state?.let { si.lanisce.lani.game.Calendar.celebrate(it, origin.id, correct, total, today, now) }
            ChallengeOrigin.Surprise -> state?.let { si.lanisce.lani.game.Surprises.finish(it, correct, total, today, now, cost) }
            // a station of the treasure hunt: passed, or where the path is blocked (the pages its wrong answers name)
            is ChallengeOrigin.Treasure -> state?.let { s ->
                si.lanisce.lani.game.Station.of(origin.station)?.let { st ->
                    val missed = si.lanisce.lani.game.Treasure.missed(c.exercises, verdicts.map { it == Verdict.CORRECT })
                    si.lanisce.lani.game.Treasure.finishStation(s, st, correct, total, missed, minutes, treasureRequired(), today, now)
                }
            }
            is ChallengeOrigin.Gather -> {
                // the run leaves its mark on the land: a tree felled, stone quarried, berries picked
                state?.let { s -> GameEngine.gathered(s, origin.res, correct, total, LocalDate.now(), now).takeIf { it != s } }?.let(::commit)
                null
            }
        }?.let { (n, r) -> commit(n); r } ?: ChallengeResult(
            won = correct > 0,
            rewards = emptyMap(),
            losses = emptyMap(),
            damaged = emptyList(),
            message = if (earned.isNotEmpty()) bi("gameController.youGathered", "earned" to formatRes(earned, ""))
            else "${bi("gameController.nothingThisTime")}. ${bi("recordScreen.tryAgain")}!",
        )
        if (result.won) celebrate++
        val after = state
        val friend = if (!result.won) null
        else quest?.let(onQuestDone) ?: person?.let { id -> if (before != null && after != null) gainOf(id, before, after)?.takeIf { it.points > 0 } else null }
        // who the thank-you good is from: the request's giver, the festival
        val from = when (origin) {
            is ChallengeOrigin.Quest -> quest?.let { GiftFrom(it.emoji, it.giver) }
            is ChallengeOrigin.Festival -> si.lanisce.lani.game.Calendar.byId(origin.id)?.let { GiftFrom(it.emoji, it.name, festive = true) }
            else -> null
        }
        Reward(
            earned, result, correct, total, answered = verdicts.size, almost = verdicts.count { it == Verdict.ALMOST }, tired = tired, friend = friend, from = from,
            looks = if (c.text != null) looks else 0, lookCost = cost,
        ).also { lastReward = it }
    }
}
