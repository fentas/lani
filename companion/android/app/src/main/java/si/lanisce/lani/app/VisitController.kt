package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Bundle
import si.lanisce.lani.data.PublicTown
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.data.TownActs
import si.lanisce.lani.data.TownMarket
import si.lanisce.lani.data.TownRequest
import si.lanisce.lani.data.Towns
import si.lanisce.lani.data.VisitContent
import si.lanisce.lani.data.VisitFetch
import si.lanisce.lani.data.VisitWrite
import si.lanisce.lani.data.Visits
import si.lanisce.lani.data.parseScenario
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.ItemName
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.ui.game.key
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.scene.DialogRun
import si.lanisce.lani.ui.towns.VisitLogic
import java.time.LocalDate

/**
 * A town being visited (data/Visits.kt): who it is, and what the learner's bridge brought of it: its public state
 * ([town]) and the read-only village drawn from it ([state]), its people ([cast]) and places ([scenes]).
 *
 * @param problem why the town (or part of it) can't be seen now, "target · base"; null when all of it came
 * @param stale what shows is an older copy: the town, or the learner's own tutor, isn't answering now
 * @param fetchedAt when what shows was had (ISO)
 */
data class TownVisit(
    val id: String,
    val name: String,
    val learner: String,
    val culture: String,
    val language: String,
    val loading: Boolean = true,
    val town: PublicTown? = null,
    val state: GameState? = null,
    val cast: List<Villager> = emptyList(),
    val scenes: List<SceneSpec> = emptyList(),
    val problem: String? = null,
    val stale: Boolean = false,
    val fetchedAt: String? = null,
) {
    /**
     * Who is out in the town: the people who live there (the host's cast by id, the others as they are, a child at their
     * stage) and today's visitor.
     */
    fun people(today: LocalDate = LocalDate.now()): List<Villager> {
        val s = state ?: return emptyList()
        val living = s.residents.map { it.id }.toSet() + listOfNotNull(s.visitor?.id)
        val others = Residents.people(s, cast, today).filter { p -> cast.none { it.id == p.id } }
        return (cast + others).filter { it.id in living }
    }

    fun villager(id: String, today: LocalDate = LocalDate.now()): Villager? =
        cast.firstOrNull { it.id == id } ?: state?.residents?.firstOrNull { it.id == id }?.let { Residents.villagerOf(it, cast, today) }

    /** The host's places that open from [place] ("spot:horizon", "fire", a building) in the town as it is. */
    fun scenesAt(place: String): List<SceneSpec> = state?.let { s -> scenes.filter { place in it.from && Happenings.open(it, s) } }.orEmpty()

    /** The host's places a tap opens: a building's (a house its own room, [si.lanisce.lani.game.scene.Homes.scenesOf]), else its place's. */
    fun scenesAt(tap: si.lanisce.lani.ui.game.PlaceTap): List<SceneSpec> =
        if (tap is si.lanisce.lani.ui.game.PlaceTap.Built) state?.let { si.lanisce.lani.game.scene.Homes.scenesOf(tap.building, scenes, it) }.orEmpty()
        else scenesAt(tap.place.key())

    fun scene(id: String): SceneSpec? = scenes.firstOrNull { it.id == id }
}

/**
 * A haggle at a visited town's market: what the learner offers ([give], their own) for what ([get], the town's goods),
 * and the least score that makes a deal there ([dealScore]: the market's rate, less between friends).
 */
data class Haggle(val town: String, val name: String, val give: Bundle, val get: Bundle, val dealScore: Int = TownActs.DEAL_SCORE)

/** What a request done on a visit came to. */
sealed interface VisitHelp {
    /** Helped: the host got [amount] 🤝, the learner the giver's [thanks] good (as their own culture's; null: none). */
    data class Helped(val thanks: ItemName?, val amount: Int) : VisitHelp
    /** The run didn't pass: nothing was sent; it can be tried again. */
    data object Again : VisitHelp
    /** The help couldn't be sent ([problem]); it can be sent again. */
    data class Failed(val problem: String) : VisitHelp
}

/**
 * Visiting a linked town (plan 2, §3; companion/README.md, "Visiting"): asks the learner's own bridge for the town's
 * public state, people and places, and keeps what came. A town that isn't answering shows what was had of it last,
 * marked; never had, a friendly "isn't answering" with a retry. Nothing of the host changes but through the things to
 * do on a visit ("Things to do on a visit"): its requests and market ([loadActs]), a request done ([help]), a gift or a
 * message ([gift]), a trade agreed in a haggle ([trade]), a talk with its people ([scenario]), a guest's dialog in its
 * places ([startSceneTalk]); each write is sent once by its id (a retry sends the same id).
 *
 * The texts are read in the pair shown when they come (a villager's role, a scene's words): the view model shows the
 * host's world ([si.lanisce.lani.data.VisitWorld]) before [start].
 */
class VisitController(
    private val scope: CoroutineScope,
    /** Asks the learner's bridge for part [kind] of town [id] ([Visits.PUBLIC] …): the status and body; null when not connected. */
    private val fetch: suspend (id: String, kind: String) -> Pair<Int, String>?,
    /** Sends a visit's write to town [id] ([TownActs.HELP] …): the status and body; null when not connected. */
    private val send: suspend (id: String, kind: String, body: JsonObject) -> Pair<Int, String>? = { _, _, _ -> null },
    /** Asks for a role-play in town [id] ({villager} or {market}): the status and body; null when not connected. */
    private val talk: suspend (id: String, body: JsonObject) -> Pair<Int, String>? = { _, _ -> null },
) {
    var visit by mutableStateOf<TownVisit?>(null)
        private set

    /** The visited town's open requests; null until they came, or when they can't be had ([actsProblem]). */
    var requests by mutableStateOf<List<TownRequest>?>(null)
        private set
    /** The visited town's market: the goods it can spare; null until it came. */
    var market by mutableStateOf<TownMarket?>(null)
        private set
    /** Why the requests or the market can't be had now, "target · base". */
    var actsProblem by mutableStateOf<String?>(null)
        private set
    /** A write is on its way (the buttons wait). */
    var sending by mutableStateOf(false)
        private set
    /** A guest's dialog in one of the town's places (companion/SCENES.md, "Dialogs for guests"): nothing is paid. */
    var sceneTalk by mutableStateOf<SceneTalk?>(null)
        private set

    /** What each town showed last in this app's run: shown, marked, when it can't be had again. */
    private val last = HashMap<String, TownVisit>()
    private var job: Job? = null
    /** The id of each write not answered yet ("help:<request>" …), so a retry sends the same. */
    private val ids = HashMap<String, String>()
    /** The haggles started, by their role-play's scenario id: what each is about, traded when the tutor says deal. */
    private val haggles = HashMap<String, Haggle>()

    /** Opens town [id] (as the Friends list shows it) and asks for it. */
    fun start(id: String, name: String, learner: String, culture: String, language: String): Job {
        val before = last[id]
        visit = before?.copy(name = name.ifBlank { before.name }, learner = learner.ifBlank { before.learner }, loading = true, problem = null)
            ?: TownVisit(id, name, learner, culture, language)
        requests = null
        market = null
        actsProblem = null
        sceneTalk = null
        return load()
    }

    /** Asks again (the retry after "isn't answering"). */
    fun retry(): Job? = visit?.let { load() }

    /** The visit is over. */
    fun end() {
        job?.cancel()
        visit?.takeIf { it.state != null }?.let { last[it.id] = it.copy(loading = false) }
        visit = null
        sceneTalk = null
    }

    // --- things to do on a visit ---------------------------------------------------------------------------------

    /** Asks for the town's open requests and its market (the things-to-do sheet opens with them). */
    fun loadActs(): Job? {
        val v = visit ?: return null
        return scope.launch {
            val r = async { part(v, "requests", TownActs::parseRequests, { it.stale }, { it.fetchedAt }) }
            val m = async { part(v, "market", TownActs::parseMarket, { it.stale }, { it.fetchedAt }) }
            val (rr, mm) = r.await() to m.await()
            if (visit?.id != v.id) return@launch
            (rr as? VisitFetch.Got)?.let { requests = it.value.requests }
            (mm as? VisitFetch.Got)?.let { market = it.value }
            actsProblem = listOf(rr, mm).firstNotNullOfOrNull { (it as? VisitFetch.Failed)?.problem }
        }
    }

    /** A write of [kind] to town [town] ([name]; [key]: which write, for its id), answered: done, or why not. */
    private suspend fun write(kind: String, key: String, town: String, name: String, body: (String) -> JsonObject): VisitWrite {
        val id = ids.getOrPut(key) { TownActs.newId(kind) }
        sending = true
        try {
            val (status, raw) = send(town, kind, body(id)) ?: return VisitWrite.Refused("📡 ${bi("towns.cantReachTutor")}")
            val out = TownActs.outcome(status, raw, name)
            // answered either way: a retry of something refused is a new try
            if (status in 200..499) ids.remove(key)
            return out
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return VisitWrite.Refused("📡 ${bi("towns.cantReachTutor")}")
        } finally {
            sending = false
        }
    }

    /** Request [r] done (its run passed): the host gets 🤝, the answer carries the giver's thank-you good. */
    suspend fun help(r: TownRequest): VisitWrite {
        val v = visit ?: return VisitWrite.Refused("🧳 ${bi("visit.visitOver")}")
        return write(TownActs.HELP, "help:${v.id}:${r.id}", v.id, v.name) { id ->
            buildJsonObject { put("id", id); put("request", r.id) }
        }.also { w -> if (w is VisitWrite.Done && visit?.id == v.id) requests = requests?.filter { it.id != r.id } }
    }

    /** A gift: [good] (of the learner's own chest) and/or [message] (at most [TownActs.MAX_MESSAGE] characters). */
    suspend fun gift(good: String?, message: String): VisitWrite {
        val v = visit ?: return VisitWrite.Refused("🧳 ${bi("visit.visitOver")}")
        val text = TownActs.cleanMessage(message)
        return write(TownActs.GIFT, "gift:${v.id}:${good.orEmpty()}:$text", v.id, v.name) { id ->
            buildJsonObject {
                put("id", id)
                if (good != null) put("good", good)
                if (text.isNotBlank()) put("message", text)
            }
        }
    }

    /**
     * The trade of haggle [h], agreed with [score] in the role-play [conversation] (its id is the trade's). Also after
     * the visit (the tutor's debrief may come later): the haggle knows its town.
     */
    suspend fun trade(conversation: String, h: Haggle, score: Int): VisitWrite =
        write(TownActs.TRADE, "trade:$conversation", h.town, h.name) {
            buildJsonObject {
                put("id", conversation)
                put("give", h.give.toJson())
                put("get", h.get.toJson())
                put("score", score)
            }
        }.also { w -> if (w is VisitWrite.Done && visit?.id == h.town) market = market?.let { m -> m.copy(wares = VisitLogic.left(m.wares, h.get)) } }

    /**
     * A role-play in the visited town ([body]: {villager} or {market: {give, get}}), built by the learner's bridge; the
     * scenario, or why not.
     */
    suspend fun scenario(body: JsonObject): Pair<Scenario?, String?> {
        val v = visit ?: return null to "🧳 ${bi("visit.visitOver")}"
        return try {
            val (status, raw) = talk(v.id, body) ?: return null to "📡 ${bi("towns.cantReachTutor")}"
            if (status == 200) (runCatching { parseScenario(raw) }.getOrNull() to null).let { if (it.first == null) null to "⚠️ ${bi("towns.answerNotUnderstood")}" else it }
            else null to Visits.problem(status, Towns.errorCode(raw), v.name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null to "📡 ${bi("towns.cantReachTutor")}"
        }
    }

    /** A haggle's role-play [scenarioId] is about [h]. */
    fun haggling(scenarioId: String, h: Haggle) {
        haggles[scenarioId] = h
    }

    /** What haggle [scenarioId] is about; null for another role-play. */
    fun haggle(scenarioId: String): Haggle? = haggles[scenarioId]

    // --- a guest's dialog in one of the town's places ---------------------------------------------------------------

    /** Starts the dialog of happening [a] (today's variant, with the host village's number in it); false when it has none. */
    fun startSceneTalk(a: ActiveHappening): Boolean {
        val p = a.person ?: return false
        val (h, d) = si.lanisce.lani.game.scene.DialogVariants.play(a, visit?.state ?: GameState(), java.time.LocalDate.now()) ?: return false
        sceneTalk = SceneTalk(a.scene.id, a.key, p, h, d, DialogRun.start(d, p.id, seed = kotlin.random.Random.nextLong()))
        return true
    }

    /** The learner answers with choice [k] (or goes on: [next]); at the end it is settled: nothing is paid on a visit. */
    fun chooseInScene(k: Int) = stepScene { it.choose(k) }

    fun nextInScene() = stepScene { it.next() }

    /** "📖 Namig · Hint" opened at the learner's turn ([DialogRun.hint]). */
    fun hintInScene() = stepScene { it.hint() }

    private fun stepScene(f: (DialogRun) -> DialogRun) {
        val t = sceneTalk ?: return
        val run = f(t.run)
        sceneTalk = t.copy(run = run, settled = run.step == DialogRun.Step.END)
    }

    fun closeSceneTalk() {
        sceneTalk = null
    }

    private fun load(): Job {
        val v = visit!!
        job?.cancel()
        visit = v.copy(loading = true)
        return scope.launch {
            val pub = async { part(v, Visits.PUBLIC, Visits::parsePublic, { it.stale }, { it.fetchedAt }) }
            val people = async { part<VisitContent<Villager>>(v, Visits.VILLAGERS, Visits::parseVillagersOf, { it.stale }, { it.fetchedAt }) }
            val places = async { part<VisitContent<SceneSpec>>(v, Visits.SCENES, Visits::parseScenesOf, { it.stale }, { it.fetchedAt }) }
            val done = settle(v, pub.await(), people.await(), places.await())
            if (visit?.id == v.id) {
                visit = done
                if (done.state != null) last[done.id] = done.copy(loading = false)
            }
        }.also { job = it }
    }

    private suspend fun <T> part(v: TownVisit, kind: String, parse: (String) -> T, stale: (T) -> Boolean, at: (T) -> String?): VisitFetch<T> {
        val (status, body) = try {
            fetch(v.id, kind) ?: return VisitFetch.Failed("📡 ${bi("towns.cantReachTutor")}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return VisitFetch.Failed("📡 ${bi("towns.cantReachTutor")}")
        }
        return Visits.outcome(status, body, v.name, parse, stale, at)
    }

    companion object {
        /**
         * What a visit shows once its three parts came ([pub], [people], [places]): what came, and for what didn't, what
         * [v] had (marked stale); the first problem, if any.
         */
        fun settle(
            v: TownVisit,
            pub: VisitFetch<PublicTown>,
            people: VisitFetch<VisitContent<Villager>>,
            places: VisitFetch<VisitContent<SceneSpec>>,
            today: LocalDate = LocalDate.now(),
        ): TownVisit {
            val cast = (people as? VisitFetch.Got)?.value?.items ?: v.cast
            val scenes = (places as? VisitFetch.Got)?.value?.items ?: v.scenes
            val town = (pub as? VisitFetch.Got)?.value ?: v.town
            val problem = listOf(pub, people, places).firstNotNullOfOrNull { (it as? VisitFetch.Failed)?.problem }
            val stale = listOf(pub, people, places).any { it is VisitFetch.Got && it.stale } || (pub is VisitFetch.Failed && town != null)
            return v.copy(
                loading = false,
                town = town,
                state = town?.let { Visits.gameState(it, cast.map { c -> c.id }.toSet(), today) },
                cast = cast,
                scenes = scenes,
                problem = problem,
                stale = stale,
                fetchedAt = (pub as? VisitFetch.Got)?.fetchedAt ?: v.fetchedAt,
                name = town?.town?.name?.takeIf { it.isNotBlank() } ?: v.name,
                learner = town?.town?.learner?.takeIf { it.isNotBlank() } ?: v.learner,
            )
        }
    }
}
