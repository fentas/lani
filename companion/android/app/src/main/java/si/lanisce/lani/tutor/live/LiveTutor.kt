package si.lanisce.lani.tutor.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.tutor.AnthropicProvider
import si.lanisce.lani.tutor.AnthropicTransport
import si.lanisce.lani.tutor.ApiError
import si.lanisce.lani.tutor.Billing
import si.lanisce.lani.tutor.Bundle
import si.lanisce.lani.tutor.CallOutcome
import si.lanisce.lani.tutor.CallRecord
import si.lanisce.lani.tutor.ChatGPTAuth
import si.lanisce.lani.tutor.ChatGPTError
import si.lanisce.lani.tutor.DayLog
import si.lanisce.lani.tutor.DayStats
import si.lanisce.lani.tutor.DayThreads
import si.lanisce.lani.tutor.Effort
import si.lanisce.lani.tutor.Failures
import si.lanisce.lani.tutor.Online
import si.lanisce.lani.tutor.Owed
import si.lanisce.lani.tutor.Family
import si.lanisce.lani.tutor.GeminiCredentials
import si.lanisce.lani.tutor.GeminiPacer
import si.lanisce.lani.tutor.GeminiProvider
import si.lanisce.lani.tutor.GeminiTerms
import si.lanisce.lani.tutor.GeminiTransport
import si.lanisce.lani.tutor.Guide
import si.lanisce.lani.tutor.Handling
import si.lanisce.lani.tutor.KeyGuard
import si.lanisce.lani.tutor.KeyVault
import si.lanisce.lani.tutor.LoopEnd
import si.lanisce.lani.tutor.Meter
import si.lanisce.lani.tutor.ModelCatalog
import si.lanisce.lani.tutor.ModelInfo
import si.lanisce.lani.tutor.NotebookState
import si.lanisce.lani.tutor.Notebooks
import si.lanisce.lani.tutor.OpenAIBearer
import si.lanisce.lani.tutor.OpenAICredentials
import si.lanisce.lani.tutor.OpenAIProvider
import si.lanisce.lani.tutor.OpenAITransport
import si.lanisce.lani.tutor.OpenRouterAuth
import si.lanisce.lani.tutor.OpenRouterCredentials
import si.lanisce.lani.tutor.OpenRouterError
import si.lanisce.lani.tutor.OpenRouterKey
import si.lanisce.lani.tutor.OpenRouterModels
import si.lanisce.lani.tutor.OpenRouterProvider
import si.lanisce.lani.tutor.OpenRouterTransport
import si.lanisce.lani.tutor.PartialJson
import si.lanisce.lani.tutor.PhaseA
import si.lanisce.lani.tutor.Prefix
import si.lanisce.lani.tutor.Prices
import si.lanisce.lani.tutor.ProviderId
import si.lanisce.lani.tutor.Redact
import si.lanisce.lani.tutor.RoutedModels
import si.lanisce.lani.tutor.ReplyData
import si.lanisce.lani.tutor.ReplyFallback
import si.lanisce.lani.tutor.ShadowEvent
import si.lanisce.lani.tutor.ShadowImages
import si.lanisce.lani.tutor.ShadowReply
import si.lanisce.lani.tutor.Task
import si.lanisce.lani.tutor.ThreadFormat
import si.lanisce.lani.tutor.ThreadState
import si.lanisce.lani.tutor.Threads
import si.lanisce.lani.tutor.ToolInputDelta
import si.lanisce.lani.tutor.TurnKind
import si.lanisce.lani.tutor.TurnRecord
import si.lanisce.lani.tutor.TurnStatus
import si.lanisce.lani.tutor.TurnText
import si.lanisce.lani.tutor.TutorLoop
import si.lanisce.lani.tutor.TutorProvider
import si.lanisce.lani.tutor.TutorStore
import si.lanisce.lani.tutor.TutorTransport
import si.lanisce.lani.tutor.textBlock
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Where the live tutor stands, for the settings and the chat (the UI says it in the learner's languages). */
enum class LiveState {
    /** Claude Code on the node answers (the default). */
    OFF,
    /** On, but no credential: a key or the ChatGPT sign-in first. */
    NO_CREDENTIAL,
    /** On, the node not reached (offline, or not paired). */
    NO_NODE,
    /** The node's bridge has no tutor session yet: it needs an update. */
    OLD_NODE,
    IDLE,
    RUNNING,
    /** Turns waiting (a retry's time, the queue). */
    WAITING,
    /** The phone has no usable network ([Online]): the turns wait for it, none fails meanwhile. */
    OFFLINE,
    /** The key was refused (401), or the sign-in ended. */
    PAUSED_KEY,
    /** The credit is out, a spend limit was reached, or the ChatGPT plan's usage limit. */
    PAUSED_CREDIT,
    /** Today's cap is spent. */
    PAUSED_BUDGET,
    /** The month's cap is spent: only the learner's own chats, once they said so ([LiveTutor.allowMonth]). */
    PAUSED_MONTH,
}

/**
 * The live tutor (docs/plans/05-built/tutor-live.md; plan 5, phase A): the tutor in the app answers for real, on the
 * learner's own API key, as a client of the paired bridge's channel ([LiveBridge]: the node now, the phone's own bridge
 * later). Off by default: Claude Code on the node answers, as before; turning it on (who answers: "In the app")
 * attaches it to the bridge, and turning it off gives the channel back to Claude Code.
 *
 * - **Events:** the bridge's tutor inbox, a long poll while the app is up ([onForeground]), a WorkManager check while it
 *   isn't ([backgroundCheck], tutor/live/TutorWork). An event is acknowledged once its turn is done, so what the app
 *   hasn't answered goes back to Claude Code when the learner switches back. What writes content (phase C) is left for
 *   Claude Code.
 * - **Turns:** plan 5's loop ([TutorLoop]) with the provider and model per task (the presets, per-task overrides); a
 *   chat, an answer, the rhythm at once; reviews and session ends in the day's digest (at once with "react at once");
 *   the day's thread with our compaction (the notebook rewritten at the day's end and past the thread's limit, on the
 *   bridge: app/tutor/notebook.md); a role-play's turns on their own short thread; a routine task on another model as
 *   a stateless call. The thread restarts after an app update that changes the prefix.
 * - **Tools:** the bridge's for real ([LiveTools]: reply reaches the chat as Claude Code's does), guide and
 *   dictionary_lookup the app's own.
 * - **The chat:** a reply streams into the chat sheet as it's written ([onStreaming], from the reply tool's streamed
 *   input), the final message from the bridge replaces it; what goes wrong (the cap, the key, the credit, offline) is
 *   told briefly in the chat ([onNote]), never silently.
 * - **Spend:** priced per call ([Prices]); a daily cap stops it and says so; today's and this month's spend shown.
 *
 * Everything it changes happens on one thread ([io]); its credentials are the shadow's ([KeyVault], [OpenAICredentials]).
 */
class LiveTutor(
    private val scope: CoroutineScope,
    val store: LiveStore,
    private val vault: KeyVault,
    private val openai: OpenAICredentials?,
    private val bridgeConfig: suspend () -> BridgeConfig?,
    /** A debug or qa build (BuildConfig.QA): the Messages API's base URL may be set (QA's mock). */
    val debug: Boolean,
    private val transportFor: (String, suspend () -> String?) -> TutorTransport = { base, key -> AnthropicTransport(base, key) },
    private val openaiTransportFor: (suspend () -> OpenAIBearer?) -> OpenAITransport = { OpenAITransport(it) },
    private val bridgeFor: (BridgeConfig) -> LiveBridge = { LiveBridge(it) },
    private val fran: FranLookup? = FranLookup(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val read: (String) -> String? = Bundle::text,
    /** The phone's name, for the bridge's status ("smoke phone", Build.MODEL). */
    private val device: String = "Android",
    private val logLine: (String) -> Unit = {},
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    /** A note's text in the learner's languages, by its l10n key ("tutorLive.note.budget"). */
    private val texts: (String, Map<String, Any?>) -> String = { key, _ -> key },
    /** The ChatGPT sign-in's loopback port (0: any free one). */
    private val signInPort: Int = 0,
    /** The learner's GitHub sign-in (git sync's token): report_problem posts to Lani's discussions with it, asked first. */
    private val githubToken: suspend () -> String? = { null },
    private val discussionsFor: (suspend () -> String?) -> Discussions = { Discussions(it) },
    private val batchesFor: (String, suspend () -> String?) -> AnthropicBatches = { base, key -> AnthropicBatches(base, key) },
    /** Charging on an unmetered network (Wi-Fi): the night's content may run. */
    private val nightOk: () -> Boolean = { false },
    /** How often the open app asks a waiting batch (the tests': a few milliseconds). */
    studioPollMs: Long = 30_000,
    /** The Gemini key and its tier (the shadow's vault: one for both), or null: no Gemini in this build. */
    private val gemini: GeminiCredentials? = null,
    /**
     * The phone is where Google allows only the Gemini API's paid services ([Region.geminiPaidOnly]: the EEA,
     * Switzerland, the UK): no free tier is offered, and a key marked free is treated as paid ([GeminiTerms.freeCalls]).
     */
    private val paidOnlyRegion: () -> Boolean = { false },
    /** The Gemini API's client for a key, its pacer, and what a 429 naming the free tier calls. */
    private val geminiTransportFor: (suspend () -> String?, GeminiPacer?, () -> Unit) -> GeminiTransport = { k, p, f -> GeminiTransport(k, pacer = p, onFreeTier = f) },
    /** OpenRouter's sign-in and key, or null: no OpenRouter in this build. */
    private val openrouter: OpenRouterCredentials? = null,
    private val openrouterTransportFor: (suspend () -> String?) -> OpenRouterTransport = { OpenRouterTransport(it) },
    /**
     * The phone's network ([AndroidOnline]): while it's down the queue waits for it, and a network error then goes back
     * to the queue untried (no retries burned, the turn's try not counted).
     */
    private val online: Online = Online.ALWAYS,
) {
    private val specs = StudioSpecs.load(StudioSpecs.CHAT, read)
    private val guide = Guide(read)
    private val prefix = StudioSpecs.chatPrefix(specs, guide, read)
    private val prices = read("prices.json")?.let(Prices::parse) ?: Prices(emptyMap())
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var worker: Job? = null
    private var poller: Job? = null
    @Volatile private var foreground = false
    /** When the notebook was last read from the node (it is read again after ten minutes, or after a notebook tool). */
    private var notebookAt = 0L

    private var s = LiveSettings()
    private var q: List<TurnRecord> = emptyList()
    private val logs = mutableMapOf<String, DayLog>()
    /** The tools the node's session listed (its channel's and its own); empty until the session was read. */
    @Volatile private var nodeTools: Set<String> = emptySet()

    // --- what the UI reads ------------------------------------------------------------------------------------------

    var settings by mutableStateOf(LiveSettings())
        private set
    var state by mutableStateOf(LiveState.OFF)
        private set
    /** Turns waiting or running. */
    var waiting by mutableStateOf(0)
        private set
    var hasKey by mutableStateOf(false)
        private set
    /** The OpenAI credentials set ([Billing]); empty: none. */
    var openaiReady by mutableStateOf<Set<String>>(emptySet())
        private set
    /** The ChatGPT account signed in (its e-mail, for the settings only), or null. */
    var chatgptAccount by mutableStateOf<String?>(null)
        private set
    var today by mutableStateOf(DayLog(""))
        private set
    var notebook by mutableStateOf(NotebookState())
        private set
    /** Whether this build has OpenAI. */
    val openaiAvailable: Boolean get() = openai != null
    val geminiAvailable: Boolean get() = gemini != null
    val openrouterAvailable: Boolean get() = openrouter != null
    /** A Gemini key is kept. */
    var geminiReady by mutableStateOf(false)
        private set
    /** The Gemini key's tier ([GeminiTerms]: free, free-seen, paid; null: not said). */
    var geminiTier by mutableStateOf<String?>(null)
        private set
    /** Signed in with OpenRouter (its key kept). */
    var openrouterReady by mutableStateOf(false)
        private set
    /** The OpenRouter key's credit (GET /api/v1/key at its check), or null. */
    var openrouterKey by mutableStateOf<OpenRouterKey?>(null)
        private set
    /**
     * The Gemini key's calls go as the free tier's ($0, no Argon: [GeminiTerms.freeCalls]): it's marked free, where
     * Google allows the free tier ([geminiPaidOnly] not).
     */
    val geminiFree: Boolean get() = GeminiTerms.freeCalls(geminiTier, geminiPaidOnly)

    /** The Gemini key is marked free: Google may use what's sent (the section and the chat say so, also where it's treated as paid). */
    val geminiMarkedFree: Boolean get() = GeminiTerms.isFree(geminiTier)

    /** Only the paid Gemini API may be offered where the phone is (the EEA, Switzerland, the UK): no free-tier choice. */
    val geminiPaidOnly: Boolean get() = paidOnlyRegion()

    /** The learner's name (the guide's paragraphs say Jan), where the learner is in the app, a chat photo's file. */
    var learner: () -> String? = { null }
    var where: () -> String? = { null }
    var photo: (String) -> File? = { null }
    /** The village's language (fran.si answers for Slovene only). */
    var language: () -> String? = { "sl" }
    /** A reply as it's written (the chat sheet's live bubble), to [conversation]; null: it's over. */
    var onStreaming: (conversation: String, text: String?) -> Unit = { _, _ -> }
    /** A short note for the learner in the chat (the cap reached, the key refused, offline); null-safe to set late. */
    var onNote: ((String) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) scope.launch(io) { flushNotes() }
        }
    /** The WorkManager check: on while the live tutor is (tutor/live/TutorWork), and a run now when a turn waits. */
    var schedule: (on: Boolean, now: Boolean) -> Unit = { _, _ -> }
    /** The studio's background work (tutor/live/StudioWork): now, a batch's poll, the night's. */
    var studioSchedule: (StudioWake) -> Unit = {}

    /** The reports waiting for the learner's "Send to Lani's discussions?" (the chat shows them). */
    var reports by mutableStateOf<List<PendingReport>>(emptyList())
        private set
    /** This month's spend reached the monthly cap (the chat asks "answer anyway?"). */
    var monthReached by mutableStateOf(false)
        private set

    private val studioSpecs = StudioSpecs.load(StudioSpecs.STUDIO, read)

    /**
     * The studio (docs/plans/05-built/studio.md): content jobs on the content model, through the bridge's publishing
     * tools and the file tools, saved on the bridge after each step; what can wait through the Message Batches API.
     */
    val studio: Studio = Studio(
        scope, io, StudioSpecs.studioPrefix(studioSpecs, read), studioSpecs.associateBy { it.name }, guide,
        bridge = { bridge() },
        providerFor = { m -> providerFor(m) },
        batchesFor = { m -> if (ProviderId.of(m) == ProviderId.ANTHROPIC) batchesFor(baseUrl()) { vault.load() } else null },
        nodeTools = { nodeTools },
        fran = { fran.takeIf { language() == "sl" } },
        learner = { learner() ?: s.learnerName },
        notebook = { refreshNotebook(force = false) },
        enabled = { s.on && credentialSet() && !s.keyRefused && s.lowCredit == null },
        budgetOk = { spent() < s.dailyCap && monthSpend() < s.monthlyCap },
        record = { job, c, thread, batch -> studioCall(job, c, thread, batch) },
        onApiError = { job, e -> studioError(job, e) },
        onFinished = { job -> studioDone(job) },
        report = { use, conversation -> report(use, conversation) },
        nightOk = nightOk,
        schedule = { w -> studioSchedule(w) },
        clock = clock, zone = zone, where = { where() }, line = ::line, sleep = sleep, pollEveryMs = studioPollMs,
    )

    init {
        scope.launch(io) {
            s = store.settings()
            q = store.queue()
            notebook = store.cachedNotebook()
            reports = store.pendingReports()
            hasKey = vault.has()
            loadOpenAI()
            loadOthers()
            publish()
            if (s.on) {
                schedule(true, false)
                wakeUp()
                // the node's tools again (a process started anew: the session was read at attach); then the studio's jobs
                scope.launch {
                    runCatching { bridge()?.session() }.getOrNull()?.let { nodeTools = (it.tools + it.appTools).toSet() }
                    if (studioReady()) studio.wake()
                }
            }
        }
    }

    private fun now() = clock()
    private fun dayNow() = TutorStore.dayOf(now(), zone())
    private fun log(day: String): DayLog = logs.getOrPut(day) { store.day(day) }
    private fun line(msg: String) = logLine(Redact.of("live: $msg"))

    private fun saveLog(l: DayLog) {
        logs[l.day] = l
        store.saveDay(l)
        if (l.day == dayNow()) today = l
    }

    private fun publish() {
        settings = s
        waiting = q.size
        today = log(dayNow())
        state = stateNow()
    }

    private fun stateNow(): LiveState = when {
        !s.on -> LiveState.OFF
        !credentialSet() -> LiveState.NO_CREDENTIAL
        s.keyRefused -> LiveState.PAUSED_KEY
        s.lowCredit != null -> LiveState.PAUSED_CREDIT
        spent() >= s.dailyCap -> LiveState.PAUSED_BUDGET
        monthReached && s.monthAllowed != monthNow() -> LiveState.PAUSED_MONTH
        !online.now() && q.isNotEmpty() -> LiveState.OFFLINE
        offline -> LiveState.NO_NODE
        oldNode -> LiveState.OLD_NODE
        worker?.isActive == true && q.any { it.status == TurnStatus.RUNNING } -> LiveState.RUNNING
        q.isNotEmpty() -> LiveState.WAITING
        else -> LiveState.IDLE
    }

    private var offline = false
    private var oldNode = false

    private fun saveSettings(f: (LiveSettings) -> LiveSettings) {
        s = f(s)
        store.saveSettings(s)
        settings = s
        state = stateNow()
    }

    private fun saveQueue(next: List<TurnRecord>) {
        q = next
        store.saveQueue(q)
        waiting = q.size
        state = stateNow()
    }

    /** Today's spend in dollars. */
    fun spent(day: String = dayNow()): Double = Meter.spend(log(day).calls)

    /** This month's spend in dollars (the days of the month with a log). */
    suspend fun spentThisMonth(): Double = withContext(io) { monthSpend() }

    private fun monthNow() = dayNow().take(7)

    /** This month's spend (on [io]): the month's days with a log, today's as it is. */
    private fun monthSpend(): Double {
        val month = monthNow()
        val total = (store.days().filter { it.startsWith(month) } + dayNow()).distinct().sumOf { spent(it) }
        monthReached = total >= s.monthlyCap
        return total
    }

    /** "Answer anyway this month": at the monthly cap the learner's own chats run (nothing routine, no studio job). */
    fun allowMonth(on: Boolean = true) = scope.launch(io) {
        saveSettings { it.copy(monthAllowed = if (on) monthNow() else null) }
        wakeUp()
    }

    /** The ChatGPT plan's welcome was shown (once). */
    fun welcomedChatGPT() = scope.launch(io) { saveSettings { it.copy(chatgptWelcomed = true) } }

    /** Day [day]'s numbers. */
    suspend fun stats(day: String = dayNow()): DayStats = withContext(io) { DayStats.of(log(day), notebook.text.length) }

    // --- the credentials (the Tutor section) ------------------------------------------------------------------------

    private fun credentialSet(): Boolean = when (s.provider) {
        ProviderId.ANTHROPIC -> hasKey
        ProviderId.OPENAI -> openaiReady.isNotEmpty()
        ProviderId.GEMINI -> geminiReady
        ProviderId.OPENROUTER -> openrouterReady
    }

    /** Whether the provider chosen has its credential (a key kept, or the ChatGPT sign-in). */
    fun credentialReady(): Boolean = credentialSet()

    /** The credentials read again (the shadow's panel shares the vault: a key set there counts here too). */
    suspend fun refreshCredentials() = withContext(io) {
        hasKey = vault.has()
        loadOpenAI()
        loadOthers()
        state = stateNow()
    }

    /** The base URL Claude calls go to: the API, or (a debug or qa build) the mock QA set. */
    fun baseUrl(): String = KeyGuard.baseUrl(debug, s.debugBaseUrl)

    /** The OpenAI credential in use: the one chosen when set, else the ChatGPT plan, else the key; null: none. */
    fun openaiBilling(): String? = s.openai?.takeIf { it in openaiReady } ?: when {
        Billing.CHATGPT_PLAN in openaiReady -> Billing.CHATGPT_PLAN
        Billing.API_KEY in openaiReady -> Billing.API_KEY
        else -> null
    }

    /** Whose models [LiveSettings.listed] are now: "anthropic", the OpenAI billing in use, "gemini", "openrouter". */
    private fun listedKey(): String? = when (s.provider) {
        ProviderId.ANTHROPIC -> "anthropic"
        ProviderId.OPENAI -> openaiBilling()
        ProviderId.GEMINI -> "gemini"
        ProviderId.OPENROUTER -> "openrouter"
    }

    /** How a call on [model] is paid: the Claude or Gemini key, the ChatGPT plan, the Gemini free tier, OpenRouter's credits. */
    private fun billingFor(model: String): String = when (ProviderId.of(model)) {
        ProviderId.ANTHROPIC -> Billing.API_KEY
        ProviderId.OPENAI -> openaiBilling() ?: Billing.API_KEY
        ProviderId.GEMINI -> if (geminiFree) Billing.FREE_TIER else Billing.API_KEY
        ProviderId.OPENROUTER -> Billing.OPENROUTER
    }

    private suspend fun loadOthers() {
        gemini?.let { c ->
            geminiReady = c.has()
            geminiTier = c.tier()
        }
        openrouter?.let { openrouterReady = it.has() }
        if (s.listedWith == "openrouter") RoutedModels.learn(s.listed)
    }

    // --- Gemini (a Google AI Studio key) ------------------------------------------------------------------------------

    /**
     * Checks a Gemini [key] with GET /v1beta/models (not a model call) and keeps it, encrypted with a Keystore key (the
     * shadow's vault: one key for both), with the learner's word on its [tier] ([GeminiTerms.FREE], [GeminiTerms.PAID];
     * null: not said). Null when kept, else what went wrong.
     */
    suspend fun setGeminiKey(key: String, tier: String?): String? = withContext(io) {
        val c = gemini ?: return@withContext "this build has no Gemini"
        val k = key.trim()
        if (k.isEmpty()) return@withContext "the key is empty"
        val models = try {
            geminiTransportFor({ k }, null, {}).models()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            line("Gemini key check failed: ${e.kind}")
            return@withContext if (e is ApiError.Auth) "the key was refused" else Redact.of(e.message ?: e.kind, k)
        }
        val before = c.key.load()
        if (before != null && before != k) store.thread()?.takeIf { ProviderId.of(it.model) == ProviderId.GEMINI }?.let { store.saveThread(it.copy(prefix = "another-key")) }
        c.key.save(k)
        c.setTier(tier)
        loadOthers()
        saveSettings {
            val mine = it.provider == ProviderId.GEMINI
            it.copy(listed = if (mine) models else it.listed, listedAt = now(), listedWith = if (mine) "gemini" else it.listedWith, keyRefused = false, lowCredit = null)
        }
        line("Gemini key checked: ${models.size} model(s)")
        if (geminiMarkedFree) tellGeminiFree()
        wakeUp()
        null
    }

    /** The learner's word on the Gemini key's tier: on the free tier the chat says once what that means. */
    fun setGeminiTier(tier: String?) = scope.launch(io) {
        gemini?.setTier(tier)
        loadOthers()
        if (geminiMarkedFree) tellGeminiFree()
        state = stateNow()
    }

    fun removeGeminiKey() = scope.launch(io) {
        gemini?.remove()
        loadOthers()
        saveSettings { it.copy(keyRefused = false, listed = if (it.listedWith == "gemini") emptyList() else it.listed) }
    }

    /** A 429 named the free tier's quota: the key is on it (said in the section; once in the chat). */
    private suspend fun seenFreeTier() {
        val c = gemini ?: return
        if (GeminiTerms.isFree(c.tier())) return
        c.setTier(GeminiTerms.FREE_SEEN)
        loadOthers()
        line("Gemini: a 429 named the free tier's quota: the key is on the free tier")
        tellGeminiFree()
    }

    /** Once in the chat, while the key is on the free tier: what Google's terms say of it. */
    private fun tellGeminiFree() {
        if (s.geminiFreeTold) return
        saveSettings { it.copy(geminiFreeTold = true) }
        tell(null, "tutorLive.note.geminiFree", emptyMap())
    }

    // --- OpenRouter ("Continue with OpenRouter") ----------------------------------------------------------------------

    private var routerSignIn: OpenRouterAuth.SignIn? = null

    /**
     * "Continue with OpenRouter": the browser's sign-in, the learner's own key kept, its credit and OpenRouter's list of
     * models that take tools read. Null when signed in, else what went wrong.
     */
    suspend fun signInWithOpenRouter(open: (String) -> Unit): String? {
        val c = openrouter ?: return "this build has no OpenRouter"
        routerSignIn?.cancel()
        val pending = try {
            withContext(Dispatchers.IO) { c.auth.begin(signInPort) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "the sign-in couldn't start: ${Redact.of(e.message ?: e.javaClass.simpleName)}"
        }
        routerSignIn = pending
        try {
            open(pending.url)
            withContext(Dispatchers.IO) { pending.await() }
        } catch (e: CancellationException) {
            pending.cancel()
            throw e
        } catch (e: OpenRouterError) {
            return e.message
        } catch (e: Exception) {
            return "the sign-in failed: ${Redact.of(e.message ?: e.javaClass.simpleName)}"
        } finally {
            if (routerSignIn === pending) routerSignIn = null
        }
        return withContext(io) {
            loadOthers()
            routerCheck()
        }
    }

    fun cancelOpenRouterSignIn() {
        routerSignIn?.cancel()
        routerSignIn = null
    }

    suspend fun signOutOfOpenRouter(): String? = withContext(io) {
        val note = openrouter?.auth?.signOut()
        loadOthers()
        openrouterKey = null
        saveSettings { it.copy(listed = if (it.listedWith == "openrouter") emptyList() else it.listed) }
        state = stateNow()
        note
    }

    /** The OpenRouter key's credit and the list of models again: the credential's check. Null when fine. */
    private suspend fun routerCheck(): String? {
        val t = openrouterTransportFor { openrouter?.key() }
        val info = try {
            t.key()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            if (e is ApiError.Auth) saveSettings { it.copy(keyRefused = true) }
            return Redact.of(e.message ?: e.kind)
        }
        openrouterKey = info
        val models = runCatching { t.models() }.getOrNull()
        models?.let(RoutedModels::learn)
        saveSettings {
            val mine = it.provider == ProviderId.OPENROUTER
            it.copy(
                listed = if (mine && models != null) models else it.listed, listedAt = now(), listedWith = if (mine && models != null) "openrouter" else it.listedWith,
                keyRefused = false, lowCredit = null,
            )
        }
        wakeUp()
        return if (models == null) "signed in; OpenRouter's models couldn't be listed: Check again" else null
    }

    private suspend fun loadOpenAI() {
        val c = openai ?: return
        openaiReady = c.available()
        chatgptAccount = c.chatgpt.session()?.takeIf { it.connected }?.let { it.email ?: "signed in" }
    }

    /**
     * Checks a Claude [key] with GET /v1/models (not a model call) and keeps it, encrypted with a Keystore key (the
     * shadow's vault: one key for both). Null when kept, else what went wrong.
     */
    suspend fun setKey(key: String): String? = withContext(io) {
        val k = key.trim()
        if (k.isEmpty()) return@withContext "the key is empty"
        val models = try {
            transportFor(baseUrl()) { k }.models()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            line("key check failed: ${e.kind}")
            return@withContext if (e is ApiError.Auth) "the key was refused (401)" else Redact.of(e.message ?: e.kind, k)
        }
        val before = vault.load()
        // another key (another account or workspace): the thread's thinking was made under the old one, so it restarts
        if (before != null && before != k) store.thread()?.let { store.saveThread(it.copy(prefix = "another-key")) }
        vault.save(k)
        hasKey = true
        saveSettings {
            val mine = it.provider == ProviderId.ANTHROPIC
            it.copy(listed = if (mine) models else it.listed, listedAt = now(), listedWith = if (mine) "anthropic" else it.listedWith, keyRefused = false, lowCredit = null)
        }
        line("key checked: ${models.size} model(s)")
        wakeUp()
        null
    }

    fun removeKey() = scope.launch(io) {
        vault.remove()
        hasKey = false
        saveSettings { it.copy(keyRefused = false, listed = if (it.listedWith == "anthropic") emptyList() else it.listed) }
    }

    /** An OpenAI API [key], checked with GET /v1/models and kept. Null when kept, else what went wrong. */
    suspend fun setOpenAIKey(key: String): String? = withContext(io) {
        val c = openai ?: return@withContext "this build has no OpenAI"
        val k = key.trim()
        if (k.isEmpty()) return@withContext "the key is empty"
        val models = try {
            openaiTransportFor { OpenAIBearer(k, Billing.API_KEY) }.models()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            return@withContext if (e is ApiError.Auth) "the key was refused (401)" else Redact.of(e.message ?: e.kind, k)
        }
        c.apiKey.save(k)
        loadOpenAI()
        afterOpenAI(models, Billing.API_KEY)
        null
    }

    fun removeOpenAIKey() = scope.launch(io) {
        openai?.apiKey?.remove()
        loadOpenAI()
        state = stateNow()
    }

    private fun afterOpenAI(models: List<ModelInfo>, billing: String) {
        saveSettings {
            val use = it.openai ?: billing
            it.copy(
                keyRefused = false, lowCredit = null,
                listed = if (it.provider == ProviderId.OPENAI && openaiBilling() == billing) models else it.listed,
                listedWith = if (it.provider == ProviderId.OPENAI && openaiBilling() == billing) billing else it.listedWith,
                openai = use.takeIf { u -> u in openaiReady },
            )
        }
        wakeUp()
    }

    private var signIn: ChatGPTAuth.SignIn? = null

    /** "Continue with ChatGPT": the browser's sign-in, then the plan's models. Null when signed in with the plan, else what went wrong. */
    suspend fun signInWithChatGPT(open: (String) -> Unit): String? {
        val c = openai ?: return "this build has no OpenAI"
        signIn?.cancel()
        val pending = try {
            withContext(Dispatchers.IO) { c.chatgpt.begin(signInPort) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "the sign-in couldn't start: ${Redact.of(e.message ?: e.javaClass.simpleName)}"
        }
        signIn = pending
        val session = try {
            open(pending.url)
            withContext(Dispatchers.IO) { pending.await() }
        } catch (e: CancellationException) {
            pending.cancel()
            throw e
        } catch (e: ChatGPTError) {
            return e.message
        } catch (e: ApiError) {
            return Redact.of(e.message ?: e.kind)
        } catch (e: Exception) {
            return "the sign-in failed: ${Redact.of(e.message ?: e.javaClass.simpleName)}"
        } finally {
            if (signIn === pending) signIn = null
        }
        return withContext(io) {
            loadOpenAI()
            if (!session.planUsage) return@withContext "signed in, but the use of the ChatGPT plan wasn't granted: sign in again and allow it"
            val models = try {
                openaiTransportFor { c.bearer(Billing.CHATGPT_PLAN) }.models()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                emptyList()
            }
            afterOpenAI(models, Billing.CHATGPT_PLAN)
            null
        }
    }

    fun cancelSignIn() {
        signIn?.cancel()
        signIn = null
    }

    suspend fun signOutOfChatGPT(): String? = withContext(io) {
        val c = openai ?: return@withContext null
        val note = try {
            c.chatgpt.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "signed out on the phone: ${Redact.of(e.message ?: e.javaClass.simpleName)}"
        }
        loadOpenAI()
        state = stateNow()
        note
    }

    /** The models again with the credential in use (the credit came back, a model was added); clears a pause. Null when listed. */
    suspend fun recheck(): String? = withContext(io) {
        if (s.provider == ProviderId.OPENROUTER) return@withContext routerCheck()
        val models = try {
            when (s.provider) {
                ProviderId.ANTHROPIC -> transportFor(baseUrl()) { vault.load() }.models()
                ProviderId.OPENAI -> {
                    val billing = openaiBilling() ?: return@withContext "no OpenAI credential"
                    openaiTransportFor { openai?.bearer(billing) }.models()
                }
                ProviderId.GEMINI -> geminiTransportFor({ gemini?.key?.load() }, null, {}).models()
                ProviderId.OPENROUTER -> emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            if (e is ApiError.Auth) saveSettings { it.copy(keyRefused = true) }
            return@withContext Redact.of(e.message ?: e.kind)
        }
        saveSettings { it.copy(listed = models, listedAt = now(), listedWith = listedKey(), keyRefused = false, lowCredit = null) }
        wakeUp()
        null
    }

    /** The settings changed in the Tutor section (the provider, the preset, a model or effort per task, the cap …). */
    fun update(f: (LiveSettings) -> LiveSettings) = scope.launch(io) {
        val before = s
        saveSettings(f)
        if (before.provider != s.provider && s.listedWith != listedKey()) saveSettings { it.copy(listed = emptyList(), listedWith = null) }
        wakeUp()
    }

    // --- on and off: who answers ------------------------------------------------------------------------------------

    /**
     * Who answers: [on] the app (attached to the paired bridge: its channel's events come here, Claude Code hears nothing
     * new), else Claude Code on the node again (detached: what the app hasn't answered goes back to it). Null when done,
     * else what went wrong (nothing paired, the node's bridge too old, the node not reached).
     */
    suspend fun setOn(on: Boolean): String? = withContext(io) {
        if (on == s.on) {
            if (on) startPolling()
            return@withContext null
        }
        val b = bridge()
        if (on) {
            if (!credentialSet()) return@withContext "no credential: a key, or Continue with ChatGPT, first"
            b ?: return@withContext "this phone isn't paired with a node"
            val session = try {
                b.session()
            } catch (e: LiveBridgeError) {
                if (e.code == 404) {
                    oldNode = true
                    state = stateNow()
                    return@withContext "the node's bridge has no tutor session yet: update it"
                }
                return@withContext "the node wasn't reached: ${e.message}"
            }
            if (session.protocol != PROTOCOL) return@withContext "the node's tutor session speaks protocol ${session.protocol}, the app ${PROTOCOL}: update the app or the node"
            nodeTools = (session.tools + session.appTools).toSet()
            oldNode = false
            try {
                if (!b.attach(device)) return@withContext "the node didn't take the attach"
            } catch (e: LiveBridgeError) {
                return@withContext "the node wasn't reached: ${e.message}"
            }
            saveSettings { it.copy(on = true) }
            line("attached: the node's events come to the app (${nodeTools.size} tools)")
            refreshNotebook(b)
            schedule(true, false)
            startPolling()
            wakeUp()
            if (studioReady()) studio.wake()
            null
        } else {
            poller?.cancel()
            worker?.cancel()
            // the studio's jobs for events end (their events go back with the detach); the learner's own requests wait
            studio.handBack()
            saveSettings { it.copy(on = false) }
            schedule(false, false)
            // what the app hadn't answered goes back to Claude Code with the detach: the queue lets it go
            val left = q
            if (left.isNotEmpty()) {
                val l = log(dayNow())
                saveLog(l.copy(turns = l.turns + left.map { it.copy(status = TurnStatus.SKIPPED, endedAt = now(), why = (it.why?.let { w -> "$w; " } ?: "") + "handed back to Claude Code") }))
            }
            saveQueue(emptyList())
            store.saveDigest(emptyList())
            onStreaming("main", null)
            val said = if (b == null) "this phone isn't paired with a node" else try {
                flushAcks(b)
                val back = b.detach()
                line("detached: $back event(s) back to Claude Code")
                null
            } catch (e: LiveBridgeError) {
                "the node wasn't reached: it is told when the app is back on, or lets go by itself after three days"
            }
            if (said != null) store.saveAcks(store.acks()) // kept for later
            detachPending = said != null && b != null
            publish()
            said
        }
    }

    /** A detach the node didn't get yet (offline when the learner switched back): sent at the next foreground. */
    private var detachPending = false

    private suspend fun bridge(): LiveBridge? = bridgeConfig()?.let(bridgeFor)

    // --- the app's life ------------------------------------------------------------------------------------------------

    /** The app came on screen: the long poll runs, a digest when it was away two hours or more. */
    fun onForeground() = scope.launch(io) {
        foreground = true
        if (detachPending && !s.on) {
            bridge()?.let { b -> runCatching { b.detach() }.onSuccess { detachPending = false } }
        }
        if (!s.on) return@launch
        // the credit may have come back: a new day tries again (a chat then says so if it hasn't)
        if (s.lowCredit != null && s.told["credit"] != dayNow()) saveSettings { it.copy(lowCredit = null) }
        val away = store.lastAway()
        if (away > 0 && now() - away >= 2 * 3_600_000L) digest("the app opened after ${(now() - away) / 3_600_000L} h")
        evening()
        startPolling()
        wakeUp()
        if (studioReady()) studio.wake()
    }

    /** The app left the screen: the long poll stops (the WorkManager check takes over); a turn waiting runs there now. */
    fun onBackground() = scope.launch(io) {
        foreground = false
        store.saveLastAway(now())
        poller?.cancel()
        if (s.on && q.isNotEmpty()) schedule(true, true)
        if (s.on) studio.waiting()?.let { studioSchedule(it) }
    }

    /** The learner opened the chat: the digest's third trigger. */
    fun onChatOpened() = scope.launch(io) { if (s.on) digest("the learner opened the chat") }

    /**
     * The WorkManager check while the app isn't up: the inbox once (no wait), what waits for an answer runs, for at most
     * [budgetMs]. The digest waits for the app.
     */
    suspend fun backgroundCheck(budgetMs: Long = 4 * 60_000L) {
        if (!withContext(io) { s.on } || foreground) return
        val b = bridge() ?: return
        try {
            flushAcks(b)
            val inbox = b.inbox(withContext(io) { s.cursor }, 0)
            if (!inbox.attached) reattach(b) else withContext(io) { take(inbox.items) }
        } catch (e: LiveBridgeError) {
            line("background check: the node wasn't reached (${e.code})")
            return
        }
        withContext(io) { wakeUp() }
        // offline meanwhile: the turns wait for the network, and the next check (it needs one) runs them
        withTimeoutOrNull(budgetMs) { while (worker?.isActive == true && online.now()) delay(200) }
        flushAcks(b)
        // a content job that came with the inbox: the studio's own background work runs it
        withContext(io) { studio.waiting() }?.let { studioSchedule(it) }
    }

    /** The studio's background work (tutor/live/StudioWork): its jobs for at most [budgetMs]. */
    suspend fun studioBackground(budgetMs: Long = 8 * 60_000L) {
        if (!withContext(io) { s.on } || foreground) return
        if (nodeTools.isEmpty()) runCatching { bridge()?.session() }.getOrNull()?.let { nodeTools = (it.tools + it.appTools).toSet() }
        if (!studioReady()) return
        studio.background(budgetMs)
        bridge()?.let { b -> runCatching { flushAcks(b) } }
    }

    /** Whether the paired bridge has the studio's side (its file tools: a phase C bridge). */
    fun studioReady(): Boolean = nodeTools.isEmpty() || StudioSpecs.TEXT_EDITOR in nodeTools

    // --- the inbox ---------------------------------------------------------------------------------------------------

    private fun startPolling() {
        if (!s.on || !foreground || poller?.isActive == true) return
        poller = scope.launch { poll() }
    }

    private suspend fun poll() {
        var backoff = 2_000L
        while (withContext(io) { s.on } && foreground) {
            val b = bridge()
            if (b == null) {
                offline = true
                withContext(io) { state = stateNow() }
                sleep(10_000)
                continue
            }
            try {
                flushAcks(b)
                val inbox = b.inbox(withContext(io) { s.cursor }, 25)
                offline = false
                if (!inbox.attached) {
                    reattach(b)
                    continue
                }
                withContext(io) { take(inbox.items) }
                backoff = 2_000
            } catch (e: CancellationException) {
                throw e
            } catch (e: LiveBridgeError) {
                offline = e.offline
                if (e.code == 404) oldNode = true
                withContext(io) { state = stateNow() }
                line("inbox: ${if (e.offline) "the node wasn't reached" else "HTTP ${e.code}"}; again in ${backoff / 1000} s")
                sleep(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000)
            }
        }
    }

    /** The node let go (a lease, a restart of another kind): the app takes the channel again while it's on. */
    private suspend fun reattach(b: LiveBridge) {
        if (!withContext(io) { s.on }) return
        try {
            b.attach(device)
            line("the node had let go: attached again")
        } catch (e: LiveBridgeError) {
            sleep(5_000)
        }
    }

    /** New inbox items: each into the day's log and the queue, the digest, or left for Claude Code. */
    private fun take(items: List<LiveBridge.Item>) {
        val fresh = items.filter { it.id > s.cursor }.sortedBy { it.id }
        if (fresh.isEmpty()) return
        for (item in fresh) add(LiveEvents.of(item, s.reactAtOnce).let { e -> e.image?.let { i -> e.copy(image = i.copy(bytes = photo(i.id)?.length() ?: 0)) } ?: e })
        saveSettings { it.copy(cursor = fresh.last().id) }
    }

    private fun add(e: ShadowEvent) {
        val l = log(TutorStore.dayOf(e.at, zone()))
        if (l.events.any { it.id == e.id }) return
        saveLog(l.copy(events = l.events + e))
        line("event ${e.kind} (${e.handling})")
        when (e.handling) {
            // the dictionary's gaps wait for the night's batch (half price) where there is one
            Handling.AT_ONCE -> if (e.kind == "gloss_gaps" && batchesNow()) studioEvent(e) else enqueue(listOf(e), e.kind)
            Handling.DIGEST -> store.saveDigest(store.digest() + e.id)
            // content writing: the studio's job, when the node has its side; else left in the inbox for Claude Code
            Handling.RECORDED -> if (e.kind in LiveEvents.CONTENT && nodeTools.isNotEmpty() && studioReady()) studioEvent(e)
        }
        evening()
        wakeUp()
    }

    private fun evening() {
        if (!s.on) return
        val t = Instant.ofEpochMilli(now()).atZone(zone())
        val day = dayNow()
        if (t.hour >= 19 && s.eveningDigest != day && store.digest().isNotEmpty()) {
            saveSettings { it.copy(eveningDigest = day) }
            digest("the evening")
        }
    }

    private fun digest(why: String) {
        val ids = store.digest()
        if (ids.isEmpty()) return
        val events = ids.mapNotNull(::findEvent)
        store.saveDigest(emptyList())
        val worth = LiveEvents.worth(events)
        line("digest of ${events.size} event(s) ($why): ${worth ?: "nothing worth saying"}")
        if (worth == null) {
            val l = log(dayNow())
            val skipped = TurnRecord(
                id = newId(), eventIds = ids, kind = TurnKind.STATELESS, task = Task.ROUTINE, model = "", eventKind = "digest",
                queuedAt = now(), endedAt = now(), status = TurnStatus.SKIPPED, why = "$why; nothing worth saying: no call",
            )
            saveLog(l.copy(turns = l.turns + skipped))
            acknowledge(ids)
            return
        }
        enqueue(events, "digest", "$why; $worth")
    }

    /** The inbox ids of [eventIds] done here: acknowledged on the bridge (kept until it took them). */
    private fun acknowledge(eventIds: List<String>) {
        val ids = eventIds.mapNotNull(LiveEvents::inboxId)
        if (ids.isEmpty()) return
        store.saveAcks((store.acks() + ids).distinct())
        scope.launch { bridge()?.let { runCatching { flushAcks(it) } } }
    }

    private suspend fun flushAcks(b: LiveBridge) {
        val ids = withContext(io) { store.acks() }
        if (ids.isEmpty()) return
        b.ack(ids)
        withContext(io) { store.saveAcks(store.acks() - ids.toSet()) }
    }

    // --- turns ---------------------------------------------------------------------------------------------------------

    private fun newId() = "t_" + UUID.randomUUID().toString().replace("-", "").take(12)

    private fun recentEvents(): List<ShadowEvent> {
        val day = dayNow()
        return log(LocalDate.parse(day).minusDays(1).toString()).events + log(day).events
    }

    private fun findEvent(id: String): ShadowEvent? = recentEvents().firstOrNull { it.id == id } ?: logs.values.asSequence().flatMap { it.events }.firstOrNull { it.id == id }

    /** Whether [p]'s credential is kept (a key, a sign-in). */
    fun ready(p: ProviderId): Boolean = when (p) {
        ProviderId.ANTHROPIC -> hasKey
        ProviderId.OPENAI -> openaiReady.isNotEmpty()
        ProviderId.GEMINI -> geminiReady
        ProviderId.OPENROUTER -> openrouterReady
    }

    /**
     * The model of [task] with the provider and preset set: the advanced override, else the preset's family for the
     * provider ([Preset.family]: Anthropic's, OpenAI's own picks, the tier's on the others). An override may be another
     * provider's model while its credential is kept (the presets across providers: the chat on Claude, the content on
     * Gemini 4 Argon); its calls go to that provider. An override stays as the learner set it (also GPT-6 Luna, which no
     * picker offers any more: [Family.matrixOnly]).
     */
    fun modelFor(task: Task): String {
        s.models[task]?.let { m -> ProviderId.of(m).takeIf { it != s.provider && ready(it) }?.let { return m } }
        val listed = s.listed.takeIf { s.listedWith == listedKey() }.orEmpty()
        val cat = ModelCatalog(listed)
        val tier = s.preset.families.getValue(task).tier
        return when (s.provider) {
            ProviderId.ANTHROPIC -> cat.modelFor(task, s.preset, s.models.filterValues { ProviderId.of(it) == ProviderId.ANTHROPIC })
            ProviderId.OPENAI, ProviderId.GEMINI -> {
                val set = s.models[task]?.takeIf { ProviderId.of(it) == s.provider && (listed.isEmpty() || listed.any { m -> m.id == it }) }
                val pick = s.preset.family(task, s.provider)
                // Gemini's free tier has no Argon: its tasks go to Flash there
                val f = if (s.provider == ProviderId.GEMINI && geminiFree && pick.tier > Family.FLASH.tier) Family.FLASH else pick
                set ?: cat.own(f) ?: f.preferred.first()
            }
            // OpenRouter: the model the learner picked for the task, else the tier's default (OpenRouterModels.DEFAULTS)
            ProviderId.OPENROUTER -> s.models[task]?.takeIf { ProviderId.of(it) == ProviderId.OPENROUTER } ?: OpenRouterModels.forTier(tier)
        }
    }

    fun effortFor(task: Task, model: String = modelFor(task)): String? = ModelCatalog.effortFor(task, s.preset, model, s.efforts)

    private fun enqueue(events: List<ShadowEvent>, eventKind: String, why: String? = null) {
        val roleplay = eventKind.startsWith("roleplay")
        val task = if (eventKind == "digest") Task.ROUTINE else Task.of(eventKind)
        val model = modelFor(task)
        val threadModel = store.thread()?.takeIf { it.day == dayNow() }?.model ?: modelFor(Task.CHAT)
        val kind = when {
            roleplay -> TurnKind.ROLEPLAY
            model == threadModel -> TurnKind.DAY
            else -> TurnKind.STATELESS
        }
        saveQueue(q + TurnRecord(id = newId(), eventIds = events.map { it.id }, kind = kind, task = task, model = model, eventKind = eventKind, queuedAt = now(), why = why, effort = effortFor(task, model)))
        wakeUp()
    }

    private fun wakeUp() {
        state = stateNow()
        if (!s.on) return
        if (worker?.isActive == true) {
            wake.trySend(Unit)
            return
        }
        worker = scope.launch(io) {
            try {
                work()
            } finally {
                state = stateNow()
            }
        }
    }

    private suspend fun work() {
        while (true) {
            if (!s.on || !credentialSet() || s.keyRefused) return
            rollover()
            if (spent() >= s.dailyCap) {
                if (q.isNotEmpty()) tell("budget", "tutorLive.note.budget", mapOf("cap" to "%.2f".format(s.dailyCap)))
                return
            }
            // the monthly cap: only the learner's own chats, once they said "answer anyway"; a role-play goes on
            val month = monthSpend() < s.monthlyCap
            if (!month && q.isNotEmpty() && q.none { monthOk(it) }) {
                tell("month", "tutorLive.note.month", mapOf("cap" to "%.2f".format(s.monthlyCap)))
                state = stateNow()
                return
            }
            // the phone offline (Android holds a background app's network too): the queue waits for the network, no turn fails
            if (q.isNotEmpty() && !online.now()) {
                state = stateNow()
                if (q.any(::waits)) tell("offline", "tutorLive.note.offline", emptyMap())
                line("offline: ${q.size} turn(s) wait for the network")
                withTimeoutOrNull(60_000) { online.await() }
                continue
            }
            val at = now()
            val started = q.firstOrNull { it.kind == TurnKind.DAY && it.startedAt != null }
            // with the credit out only what the learner waits for runs (and is told why it can't answer)
            val ready = q.filter { it.notBefore <= at && (it.kind != TurnKind.DAY || started == null || it.id == started.id) && (s.lowCredit == null || waits(it)) && (month || monthOk(it)) }
            val next = ready.sortedWith(compareBy<TurnRecord>({ priority(it) }, { it.queuedAt })).firstOrNull()
            if (next == null) {
                val wait = q.filter { s.lowCredit == null || waits(it) }.minOfOrNull { it.notBefore }?.minus(at) ?: return
                withTimeoutOrNull(wait.coerceIn(500, 60_000)) { wake.receive() }
                continue
            }
            if (s.lowCredit != null) {
                // a chat says why it can't answer; routine work waits for the credit
                finish(next.copy(error = "low credit: ${s.lowCredit}"), TurnStatus.FAILED)
                tell("credit", "tutorLive.note.credit", mapOf("why" to s.lowCredit))
                continue
            }
            try {
                runTurn(next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                line("turn ${next.id} broke: ${e.message}")
                finish(next.copy(error = Redact.of(e.message ?: e.javaClass.simpleName)), TurnStatus.FAILED)
            }
        }
    }

    /**
     * Whether [t] may run past the monthly cap (plan 5: "only chats the learner sends, after asking. Never in the middle
     * of a role-play"): a chat or an answer once the learner said "answer anyway" this month, a role-play's line.
     */
    private fun monthOk(t: TurnRecord): Boolean =
        (t.kind == TurnKind.ROLEPLAY) || ((t.eventKind == "chat" || t.eventKind == "answer") && s.monthAllowed == monthNow())

    /** Whether the learner waits for [t]'s answer (a chat, an answer, a role-play's line). */
    private fun waits(t: TurnRecord) = t.eventKind == "chat" || t.eventKind == "answer" || t.kind == TurnKind.ROLEPLAY

    private fun priority(t: TurnRecord): Int = when {
        t.kind == TurnKind.CLOSING -> 0
        t.kind == TurnKind.ROLEPLAY -> 1
        t.eventKind == "chat" -> 1
        t.eventKind == "answer" -> 2
        else -> 3
    }

    /** A new day: the old day's thread is closed with a notebook_write (plan 5, "At the day's end"). */
    private fun rollover() {
        val th = store.thread() ?: return
        if (th.day == dayNow() || q.any { it.kind == TurnKind.CLOSING }) return
        if (!Threads.answered(th.messages)) {
            archive(th)
            return
        }
        saveQueue(q + TurnRecord(id = newId(), eventIds = emptyList(), kind = TurnKind.CLOSING, task = Task.CHAT, model = th.model, eventKind = "closing", queuedAt = now(), why = "the day's end (${th.day})"))
    }

    private fun archive(th: ThreadState) {
        store.saveThreadArchive(th)
        store.saveThread(null)
    }

    private fun providerFor(model: String): TutorProvider = when (ProviderId.of(model)) {
        ProviderId.GEMINI -> {
            val c = gemini
            GeminiProvider(geminiTransportFor({ c?.key?.load() }, c?.pacer) { scope.launch(io) { seenFreeTier() } }, billingFor(model))
        }
        ProviderId.OPENROUTER -> OpenRouterProvider(openrouterTransportFor { openrouter?.key() })
        ProviderId.ANTHROPIC -> AnthropicProvider(transportFor(baseUrl()) { vault.load() })
        ProviderId.OPENAI -> {
            val billing = openaiBilling() ?: Billing.API_KEY
            val c = openai
            val renew: (suspend () -> Boolean)? = if (billing == Billing.CHATGPT_PLAN && c != null) {
                { runCatching { c.chatgpt.accessToken(force = true) != null }.getOrDefault(false) }
            } else null
            OpenAIProvider(openaiTransportFor { c?.bearer(billing) }, billing, renew)
        }
    }

    private fun hashFor(model: String): String = providerFor(model).prefixHash(prefix)

    /** The notebook from the node (the thread's first message), the cache kept; the cache when the node can't be read. */
    private suspend fun refreshNotebook(b: LiveBridge? = null, force: Boolean = true): NotebookState {
        if (!force && now() - notebookAt < 10 * 60_000L) return notebook
        val node = b ?: bridge()
        val got = node?.let { runCatching { it.notebook() }.getOrNull() }
        if (got != null) {
            val updated = got.updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0
            val cur = store.cachedNotebook()
            val next = if (cur.text == got.text) cur.copy(updatedAt = maxOf(cur.updatedAt, updated)) else NotebookState(got.text, (cur.versions + si.lanisce.lani.tutor.NotebookVersion(cur.updatedAt, cur.text, "on the node")).filter { it.text.isNotBlank() }.takeLast(10), updated)
            store.saveNotebook(next)
            notebook = next
            notebookAt = now()
        }
        return notebook
    }

    /** The learner's own correction of the notebook ("What my tutor knows"): on the node, the cache following. */
    suspend fun editNotebook(text: String): String? {
        val b = bridge() ?: return "this phone isn't paired with a node"
        return try {
            b.saveNotebook(text.trim())
            withContext(io) {
                val next = Notebooks.written(store.cachedNotebook(), text.trim(), "by hand", now())
                store.saveNotebook(next)
                notebook = next
            }
            null
        } catch (e: LiveBridgeError) {
            "the node wasn't reached"
        }
    }

    /** The notebook as the node has it now (the Tutor section opens it). */
    suspend fun loadNotebook(): NotebookState = withContext(io) { refreshNotebook() }

    private fun replace(t: TurnRecord) {
        if (q.none { it.id == t.id }) return
        saveQueue(q.map { if (it.id == t.id) t else it })
    }

    private suspend fun runTurn(t0: TurnRecord) {
        var t = t0.copy(status = TurnStatus.RUNNING, startedAt = t0.startedAt ?: now(), attempts = t0.attempts + 1)
        val resumed = t0.startedAt != null
        replace(t)
        val events = t.eventIds.mapNotNull(::findEvent)
        val b = bridge()
        val conversation = if (t.eventKind == "digest" || t.kind == TurnKind.CLOSING) "main" else events.firstOrNull()?.conversationId ?: "main"
        var replied = false
        val github = githubToken() != null
        val local = buildMap<String, suspend (si.lanisce.lani.tutor.ToolUse) -> Pair<String, Boolean>> {
            // "make me exercises for X": a brief for the studio, which writes it on the content model
            put(StudioSpecs.REQUEST) { use ->
                if (!studioReady()) "request_content: the tutor's bridge has no studio yet (it needs an update): say so, nothing queued" to true
                else studio.request(use.input, conversation, modelFor(Task.CONTENT), effortFor(Task.CONTENT), s.batches)
            }
            if (github) put("report_problem") { use -> report(use, conversation) }
        }
        val tools = LiveTools(
            specs.associateBy { it.name }, b, nodeTools, guide, fran.takeIf { language() == "sl" },
            onReply = { r -> replied = true; t = t.copy(replies = t.replies + r); replace(t) },
            learner = { learner() ?: s.learnerName },
            onGuide = { id -> val l = log(dayNow()); saveLog(l.copy(guide = l.guide + (id to (l.guide[id] ?: 0) + 1))) },
            onNotebook = { withContext(io) { refreshNotebook(b) } },
            clock = clock,
            kinds = StudioSpecs::kindOf,
            local = local,
            // data.score only on the reply to an answer (a town's check, a debrief); a session's end persisted first
            replyKind = { conv -> ReplyData.kindOf(events, conv) },
            persist = ReplyData.persistFirst(events),
        )
        val provider = providerFor(t.model)
        val format = provider.format
        val loop = TutorLoop(provider, tools, maxCalls = if (t.kind == TurnKind.CLOSING) 3 else 8, budgetOk = { spent() < s.dailyCap }, sleep = sleep, online = online::now)
        val want = ModelCatalog.fit(t.effort ?: ModelCatalog.effortFor(t.task, s.preset, t.model, s.efforts), t.model)
        val nb = refreshNotebook(b, force = false)

        var th = store.thread()
        val stateless = t.kind == TurnKind.STATELESS || (t.kind == TurnKind.DAY && th != null && th.day == dayNow() && th.model != t.model)
        val messages: MutableList<JsonObject>
        val save: suspend (List<JsonObject>) -> Unit
        val top: String?
        val threadKind: String
        when {
            t.kind == TurnKind.CLOSING -> {
                val closing = th ?: return finish(t.copy(error = "no thread to close"), TurnStatus.SKIPPED)
                if (closing.prefix != provider.prefixHash(prefix)) return finish(t.copy(error = "the prefix changed (an app update): no closing call"), TurnStatus.SKIPPED, closing)
                val why = if (t.eventKind == "restart") "The thread is long (${closing.tokens} tokens) and restarts now" else "The day (${closing.day}) is over"
                top = closing.topEffort
                messages = (if (resumed) closing.messages else format.append(closing.messages, listOf(textBlock(TurnText.closing(why))), top, DayThreads.onThread(closing, want))).toMutableList()
                save = { m -> val next = (th ?: closing).copy(messages = m); th = next; store.saveThread(next) }
                save(messages)
                threadKind = "closing"
            }
            t.kind == TurnKind.ROLEPLAY -> {
                // a role-play's own short thread (plan 5): the scenario and its turns, kept by its conversation
                var rp = store.roleplay(conversation)?.takeIf { it.model == t.model && it.prefix == provider.prefixHash(prefix) }
                    ?: DayThreads.fresh(dayNow(), t.model, now(), emptyList(), provider.prefixHash(prefix), want)
                if (!resumed || rp.messages.isEmpty()) {
                    val blocks = buildList {
                        if (rp.messages.isEmpty()) add(textBlock(TurnText.notebook(nb)))
                        add(textBlock(TurnText.now(now(), zone(), where())))
                        add(textBlock(content(t, events)))
                    }
                    rp = rp.copy(messages = format.append(rp.messages, blocks, rp.topEffort, null))
                }
                store.saveRoleplay(conversation, rp)
                top = rp.topEffort
                messages = rp.messages.toMutableList()
                save = { m -> rp = rp.copy(messages = m); store.saveRoleplay(conversation, rp) }
                threadKind = "roleplay"
            }
            stateless -> {
                top = want
                messages = (
                    store.turnMessages(t.id) ?: run {
                        val texts = listOf(
                            TurnText.notebook(nb), TurnText.lines("Today so far:", th?.takeIf { it.day == dayNow() }?.lines.orEmpty().takeLast(15)),
                            TurnText.now(now(), zone(), where()),
                        )
                        listOf(format.user(texts.filter { it.isNotEmpty() }.map(::textBlock) + imageBlocks(t, events) + textBlock(content(t, events))))
                    }
                    ).toMutableList()
                save = { m -> store.saveTurnMessages(t.id, m) }
                save(messages)
                threadKind = "stateless"
            }
            else -> {
                var cur = th?.takeIf { it.day == dayNow() } ?: DayThreads.fresh(dayNow(), t.model, now(), store.threadArchive()?.lines.orEmpty(), hashFor(t.model), effortFor(Task.CHAT, t.model))
                var appendEvent = !resumed || cur.messages.isEmpty()
                if (cur.prefix != provider.prefixHash(prefix) && cur.messages.isNotEmpty()) {
                    val why = if (cur.prefix == "another-key") "The key changed" else "The app was updated (its tools or prompt changed)"
                    cur = restarted(cur, "$why, so the thread restarted", nb)
                    val l = log(dayNow())
                    saveLog(l.copy(restarts = l.restarts + cur.restarts.last()))
                    appendEvent = true
                }
                if (appendEvent) {
                    val fresh = cur.messages.isEmpty()
                    val blocks = buildList {
                        if (fresh) add(textBlock(TurnText.notebook(nb)))
                        if (fresh) TurnText.lines("Yesterday in brief:", cur.yesterday.takeLast(12)).takeIf { it.isNotEmpty() }?.let { add(textBlock(it)) }
                        TurnText.lines("Meanwhile (other models' replies, not in this thread):", cur.pending).takeIf { it.isNotEmpty() }?.let { add(textBlock(it)) }
                        add(textBlock(TurnText.now(now(), zone(), where())))
                        addAll(imageBlocks(t, events))
                        add(textBlock(content(t, events)))
                    }
                    cur = cur.copy(messages = format.append(cur.messages, blocks, cur.topEffort, DayThreads.onThread(cur, want)), pending = emptyList())
                }
                top = cur.topEffort
                th = cur
                store.saveThread(cur)
                messages = cur.messages.toMutableList()
                save = { m -> val next = (th ?: cur).copy(messages = m); th = next; store.saveThread(next) }
                threadKind = "day"
            }
        }

        val level = Effort.inEffect(messages, top)
        val byMessage = Effort.byMessage(messages)
        if (t.effort != level) {
            t = t.copy(effort = level)
            replace(t)
        }
        // the reply as it's written, into the chat sheet's live bubble (the chat's conversation only)
        val stream: ((ToolInputDelta) -> Unit)? = if (t.kind == TurnKind.CLOSING) null else { d ->
            if (d.name == "reply") {
                val conv = PartialJson.string(d.json, "conversation_id") ?: conversation
                PartialJson.string(d.json, "text")?.let { text -> if (conv == "main") onStreaming(conv, text) }
            }
        }
        val before = messages.size
        val r = try {
            loop.run(
                prefix, t.model, t.task, messages, callsDone = t.callIds.size, save = save,
                effort = top, maxTokens = t.task.maxTokensAt(level), onToolInput = stream,
                onCall = { c ->
                    val call = callRecord(t, c, threadKind, level, byMessage, ShadowImages.count(messages), provider.billing)
                    if (c.step != null) {
                        t = t.copy(callIds = t.callIds + call.id)
                        if (threadKind == "day" || threadKind == "closing") th = th?.copy(tokens = call.usage.prompt)?.also { store.saveThread(it) }
                    }
                    val l = log(dayNow())
                    saveLog(l.copy(calls = l.calls + call))
                    replace(t)
                    line("call ${call.model}${call.effort?.let { " $it" } ?: ""} ${call.eventKind}: ${call.stopReason ?: call.error} in:${call.usage.input} w:${call.usage.cacheWrite} r:${call.usage.cacheRead} out:${call.usage.output} \$${call.cost}")
                },
                onTool = { rec -> t = t.copy(toolCalls = t.toolCalls + rec); replace(t) },
                // never a silent turn: the loop asks for the reply as the calls run out, and for a last word in text
                owed = Owed.of(t.eventKind, t.kind) { replied },
            )
        } finally {
            if (!replied) onStreaming(conversation, null)
        }
        t = t.copy(text = Threads.text(messages.drop(before)).take(2000))
        // no reply tool called, the answer written as text: that text is the reply, sent as the reply tool would
        ReplyFallback.of(r, t.kind, t.replies.isNotEmpty(), conversation, now())?.let { reply ->
            val sent = b?.let { runCatching { it.tool("reply", buildJsonObject { put("conversation_id", reply.conversationId); put("text", reply.text) }, "text-${t.id}") }.getOrNull() }
            if (sent != null && !sent.isError) t = t.copy(replies = listOf(reply), replyFromText = true)
            line("turn ${t.id} ${t.eventKind}: no reply tool called, its text sent as the reply")
        }
        when (val end = r.end) {
            LoopEnd.Done -> finish(t, TurnStatus.DONE, th)
            LoopEnd.Limit -> finish(t, TurnStatus.LIMIT, th)
            LoopEnd.Truncated -> finish(t, TurnStatus.TRUNCATED, th)
            is LoopEnd.Refused -> {
                finish(t.copy(error = "the model declined (refusal)"), TurnStatus.REFUSED, th)
                if (waits(t)) tell(null, "tutorLive.note.refused", emptyMap())
            }
            LoopEnd.Paused -> {
                replace(t.copy(status = TurnStatus.QUEUED))
                tell("budget", "tutorLive.note.budget", mapOf("cap" to "%.2f".format(s.dailyCap)))
            }
            is LoopEnd.Failed -> failed(t, end.error, th)
        }
    }

    /** What the turn is about: one event, or the digest's events. */
    private fun content(t: TurnRecord, events: List<ShadowEvent>): String =
        if (t.eventKind == "digest") TurnText.digest(events, t.why.orEmpty(), zone())
        else events.firstOrNull()?.let { e ->
            val sent = e.image?.let { i -> photo(i.id) } != null
            TurnText.event(e, zone()) + if (sent) "\n(The photo is the image block just before this event.)" else ""
        } ?: "(the event is gone)"

    private fun imageBlocks(t: TurnRecord, events: List<ShadowEvent>): List<JsonObject> =
        if (t.eventKind == "digest") emptyList()
        else events.take(1).mapNotNull { e -> e.image?.let { i -> photo(i.id)?.let { f -> runCatching { ShadowImages.block(f) }.getOrNull() } } }

    private fun restarted(th: ThreadState, why: String, nb: NotebookState): ThreadState {
        val l = log(dayNow())
        val exchanges = DayThreads.exchanges(l.turns.filter { it.kind == TurnKind.DAY && it.status == TurnStatus.DONE }, ::findEvent)
        return DayThreads.restarted(th, DayThreads.restartBlocks(nb, why, th.tokens, s.limit, th.lines, exchanges), now(), hashFor(th.model), th.topEffort)
    }

    private fun callRecord(t: TurnRecord, c: CallOutcome, thread: String, effort: String?, byMessage: Boolean, images: Int, billing: String): CallRecord {
        val priced = c.step?.let { prices.priced(it.model ?: t.model, it.usage, billing, it.cost) }
        return Meter.record(t, c, now(), thread, priced, effort, effortByMessage = byMessage, images = images, billing = billing)
    }

    private fun failed(t: TurnRecord, e: ApiError, th: ThreadState?) {
        val error = Redact.of(e.message ?: e.kind)
        val provider = ProviderId.of(t.model)
        when {
            e is ApiError.Auth -> {
                saveSettings { it.copy(keyRefused = true) }
                replace(t.copy(status = TurnStatus.QUEUED, error = error))
                tell("key", keyNote(provider), emptyMap())
            }
            e is ApiError.LowCredit -> {
                val why = Failures.creditWhy(e, provider)
                saveSettings { it.copy(lowCredit = why) }
                if (waits(t)) finish(t.copy(error = "$why ($error)"), TurnStatus.FAILED, th) else replace(t.copy(status = TurnStatus.QUEUED, error = error))
                tell("credit", "tutorLive.note.credit", mapOf("why" to why))
            }
            // offline (the phone, or Android holding the background app's network): back to the queue, which waits for the
            // network; the try isn't counted
            Failures.offline(e, online) -> {
                replace(t.copy(status = TurnStatus.QUEUED, error = error, attempts = (t.attempts - 1).coerceAtLeast(0)))
                if (waits(t)) tell("offline", "tutorLive.note.offline", emptyMap())
            }
            Failures.retryAt(e, t.attempts, now()) != null -> {
                replace(t.copy(status = TurnStatus.QUEUED, error = error, notBefore = Failures.retryAt(e, t.attempts, now())!!))
                // the learner waits for it: told once that it's slow to come (offline, or the API busy)
                if (waits(t) && t.attempts >= 2) tell("offline", if (e is ApiError.Network) "tutorLive.note.offline" else "tutorLive.note.busy", emptyMap())
            }
            else -> {
                finish(t.copy(error = error), TurnStatus.FAILED, th)
                if (waits(t)) tell(null, "tutorLive.note.failed", emptyMap())
            }
        }
    }

    /** The turn is over: into its day's log, its events acknowledged; the thread gets its line, a restart past the limit. */
    private fun finish(t0: TurnRecord, status: TurnStatus, th0: ThreadState? = store.thread()) {
        val t = t0.copy(status = status, endedAt = now())
        saveQueue(q.filterNot { it.id == t.id })
        store.dropTurnMessages(t.id)
        val day = t.eventIds.firstOrNull()?.let(::findEvent)?.let { TutorStore.dayOf(it.at, zone()) } ?: dayNow()
        val l = log(day)
        saveLog(l.copy(turns = l.turns.filterNot { it.id == t.id } + t))
        acknowledge(t.eventIds)
        val th = th0
        when (t.kind) {
            TurnKind.CLOSING -> if (th != null) {
                if (t.eventKind == "restart") {
                    val fresh = restarted(th, "The thread restarted", notebook)
                    val today = log(dayNow())
                    saveLog(today.copy(restarts = today.restarts + th.tokens))
                    store.saveThread(fresh)
                } else archive(th)
            }
            TurnKind.ROLEPLAY -> {
                val conv = t.eventIds.firstOrNull()?.let(::findEvent)?.conversationId ?: "rp"
                // the role-play's end: its thread goes, its debrief a line of the day
                if (t.eventKind == "roleplay_end") {
                    store.saveRoleplay(conv, null)
                    store.thread()?.takeIf { it.day == dayNow() }?.let { cur ->
                        val entry = DayThreads.line(t, t.endedAt ?: now(), zone())
                        store.saveThread(cur.copy(lines = (cur.lines + entry).takeLast(60), pending = cur.pending + entry))
                    }
                }
            }
            TurnKind.DAY, TurnKind.STATELESS -> {
                val entry = DayThreads.line(t, t.endedAt ?: now(), zone())
                val current = store.thread()?.takeIf { it.day == dayNow() }
                if (current != null) {
                    val other = t.kind == TurnKind.STATELESS || current.model != t.model
                    val next = current.copy(lines = (current.lines + entry).takeLast(60), pending = if (other) current.pending + entry else current.pending)
                    store.saveThread(next)
                    if (!other && next.tokens > s.limit && q.none { it.kind == TurnKind.CLOSING }) {
                        saveQueue(q + TurnRecord(id = newId(), eventIds = emptyList(), kind = TurnKind.CLOSING, task = Task.CHAT, model = next.model, eventKind = "restart", queuedAt = now(), why = "the thread passed ${s.limit} tokens (${next.tokens})"))
                    }
                } else if (t.kind == TurnKind.STATELESS) {
                    val model = modelFor(Task.CHAT)
                    store.saveThread(DayThreads.fresh(dayNow(), model, now(), store.threadArchive()?.lines.orEmpty(), hashFor(model), effortFor(Task.CHAT, model)).copy(lines = listOf(entry), pending = listOf(entry)))
                }
            }
            TurnKind.ALSO -> Unit
        }
        line("turn ${t.id} ${t.eventKind} on ${t.model}${t.effort?.let { " $it" } ?: ""}: $status${t.error?.let { " ($it)" } ?: ""}")
    }

    // --- the studio ----------------------------------------------------------------------------------------------------

    /** Whether what can wait goes through the Message Batches API now (the setting, and the provider is Anthropic's). */
    private fun batchesNow(): Boolean = s.batches && s.provider == ProviderId.ANTHROPIC && studioReady() && nodeTools.isNotEmpty()

    /**
     * An event that asks for content as the studio's job: an arrival soon (the culture pack's template plays until it's
     * written), dialog variants and a story for the night, the dictionary's gaps through the night's batch on the
     * routine model.
     */
    private fun studioEvent(e: ShadowEvent) {
        val routine = e.kind == "gloss_gaps"
        val task = if (routine) Task.ROUTINE else Task.CONTENT
        val model = modelFor(task)
        val urgency = if (e.kind == "villager_arrived") StudioJob.SOON else StudioJob.WHENEVER
        val key = when (e.kind) {
            "villager_arrived" -> "studio.kind.villagerArrived"
            "dialog_variants_heard" -> "studio.kind.dialogVariantsHeard"
            "stories_low" -> "studio.kind.storiesLow"
            "gloss_gaps" -> "studio.kind.glossGaps"
            else -> null
        }
        val title = key?.let { texts(it, emptyMap()) } ?: e.kind
        scope.launch(io) { runCatching { studio.event(e, task, model, effortFor(task, model), urgency, s.batches, title) }.onFailure { line("studio: the event's job wasn't made: ${it.message}") } }
    }

    /** A studio call's record in the day's log, priced (at half price through a batch); its cost. */
    private fun studioCall(job: StudioJob, c: si.lanisce.lani.tutor.CallOutcome, thread: String, batch: Boolean): Double? {
        val turn = TurnRecord(id = job.id, eventIds = job.eventIds, kind = TurnKind.STATELESS, task = job.task, model = job.model, eventKind = "studio", queuedAt = job.createdAt)
        val billing = billingFor(job.model)
        val priced = c.step?.let { prices.priced(it.model ?: job.model, it.usage, billing, it.cost) }
            ?.let { p -> if (batch) si.lanisce.lani.tutor.Priced(p.cost?.let { it / 2 }, p.apiEquivalent?.let { it / 2 }) else p }
        val call = Meter.record(turn, c, now(), if (batch) "$thread-batch" else thread, priced, job.effort, billing = billing)
        val l = log(dayNow())
        saveLog(l.copy(calls = l.calls + call))
        line("studio call ${call.model}${job.effort?.let { " $it" } ?: ""}${if (batch) " (batch, half price)" else ""}: ${call.stopReason ?: call.error} in:${call.usage.input} w:${call.usage.cacheWrite} r:${call.usage.cacheRead} out:${call.usage.output} \$${call.cost}")
        monthSpend()
        state = stateNow()
        return priced?.cost
    }

    /** The studio's API error, as a turn's would be: the key refused, the credit out. */
    private fun studioError(job: StudioJob, e: ApiError) {
        val provider = ProviderId.of(job.model)
        when (e) {
            is ApiError.Auth -> {
                saveSettings { it.copy(keyRefused = true) }
                tell("key", keyNote(provider), emptyMap())
            }
            is ApiError.LowCredit -> {
                val why = Failures.creditWhy(e, provider)
                saveSettings { it.copy(lowCredit = why) }
                tell("credit", "tutorLive.note.credit", mapOf("why" to why))
            }
            else -> Unit
        }
    }

    /**
     * A job ended: its events acknowledged (a cancel too: the learner chose), a line in the day's thread (the chat's
     * tutor knows what the studio made), a turn in the day's log; a request that failed, or that ended without the
     * studio's own reply, is told in the chat.
     */
    private fun studioDone(job: StudioJob) {
        acknowledge(job.eventIds)
        val made = job.published.map { "${it.tool.removePrefix("publish_")} ${it.item}" }.distinct()
        val at = Instant.ofEpochMilli(job.endedAt ?: now()).atZone(zone()).toLocalTime().withSecond(0).withNano(0)
        val entry = "$at studio (${job.model}): «${job.title}» ${job.status}: ${if (made.isEmpty()) "nothing published" else "published " + made.joinToString(", ").take(300)}"
        store.thread()?.takeIf { it.day == dayNow() }?.let { cur -> store.saveThread(cur.copy(lines = (cur.lines + entry).takeLast(60), pending = cur.pending + entry)) }
        val l = log(dayNow())
        val status = when (job.status) {
            StudioJob.DONE -> TurnStatus.DONE
            StudioJob.CANCELLED -> TurnStatus.SKIPPED
            else -> TurnStatus.FAILED
        }
        val turn = TurnRecord(
            id = job.id, eventIds = job.eventIds, kind = TurnKind.STATELESS, task = job.task, model = job.model, eventKind = "studio",
            queuedAt = job.createdAt, startedAt = job.startedAt, endedAt = job.endedAt ?: now(), status = status, error = job.error, why = entry,
            effort = job.effort,
        )
        saveLog(l.copy(turns = l.turns.filterNot { it.id == job.id } + turn))
        if (job.origin == StudioJob.REQUEST) when {
            job.status == StudioJob.FAILED -> tell(null, "studio.note.failed", mapOf("title" to job.title))
            job.status == StudioJob.DONE && !job.replied -> tell(null, "studio.note.done", mapOf("title" to job.title))
        }
    }

    /** The learner's ✕ on a studio job. */
    fun cancelJob(id: String) = scope.launch(io) { studio.cancel(id) }

    // --- reports: Lani's GitHub discussions, asked first --------------------------------------------------------------

    /**
     * report_problem with the learner's GitHub sign-in (plan 5, "Reports: GitHub discussions"): the discussions looked
     * up first, then the learner asked in the chat ("Send to Lani's discussions?"): their vote on the one found, or a new
     * one. Without a sign-in (or when the lookup can't be made) the bridge keeps it in the wishlist, as before.
     */
    private suspend fun report(use: si.lanisce.lani.tutor.ToolUse, conversation: String): Pair<String, Boolean> {
        val i = use.input
        fun str(k: String) = (i[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        val title = str("title")?.trim().orEmpty()
        val what = str("what_is_wrong")?.trim().orEmpty()
        val kind = str("kind") ?: "fix"
        if (title.isEmpty() || what.isEmpty()) return "report_problem: title and what_is_wrong are needed; nothing kept" to true
        // no GitHub sign-in: the wishlist, as before
        if (githubToken() == null) return wishlist(use, null)
        val found = try {
            discussionsFor(githubToken).find(title)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DiscussionsError) {
            line("report: the discussions weren't looked up (${e.kind})")
            return wishlist(use, "the discussions couldn't be looked up (${e.kind.name.lowercase()})")
        } catch (e: java.io.IOException) {
            return wishlist(use, "the discussions couldn't be reached")
        }
        val p = PendingReport(
            id = "r_" + UUID.randomUUID().toString().replace("-", "").take(10), kind = kind, title = title.take(120), whatIsWrong = what.take(2_000),
            suggestedFix = str("suggested_fix")?.take(2_000), item = i["item"] as? JsonObject,
            nativeCheck = (i["native_check"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true", conversationId = conversation, at = now(),
            foundId = found?.id, foundNumber = found?.number, foundUrl = found?.url, foundTitle = found?.title, foundUpvotes = found?.upvotes ?: 0,
            foundUpvoted = found?.upvoted ?: false, input = i,
        )
        withContext(io) {
            val next = (store.pendingReports() + p).takeLast(10)
            store.savePendingReports(next)
            reports = next
        }
        line("report: asked the learner (${if (found != null) "found #${found.number}" else "a new discussion"})")
        val there = if (found != null) "an existing discussion has it (#${found.number} «${found.title}», ${found.upvotes} vote(s)): their vote goes there"
        else "no discussion has it yet: a new one in ${if (kind == "wish") "Ideas" else "Content fixes"}"
        return "Asked the learner in the chat (\"Send to Lani's discussions?\"): $there. Nothing is posted until they confirm; if they don't, it's kept in their wishlist for the developer. Tell them in one line." to false
    }

    /** The bridge's report_problem (the wishlist), [why] the reason it wasn't posted (null: no sign-in, its text as it is). */
    private suspend fun wishlist(use: si.lanisce.lani.tutor.ToolUse, why: String?): Pair<String, Boolean> {
        val b = bridge() ?: return "report_problem: the bridge isn't reached: nothing kept" to true
        val r = try {
            b.tool("report_problem", use.input, use.id)
        } catch (e: LiveBridgeError) {
            return "report_problem didn't reach the bridge: nothing kept" to true
        }
        return (if (why == null) r.text else "${r.text} ($why)") to r.isError
    }

    /**
     * The learner's answer to "Send to Lani's discussions?" for report [id]: [send] votes on the discussion found or
     * starts one, and the link is kept in their data (app/reports.json); else (or when posting fails) it goes to the
     * wishlist. A short note in the chat says what happened.
     */
    fun answerReport(id: String, send: Boolean) = scope.launch(io) {
        val p = store.pendingReports().firstOrNull { it.id == id } ?: return@launch
        val rest = store.pendingReports().filterNot { it.id == id }
        store.savePendingReports(rest)
        reports = rest
        val b = bridge()
        suspend fun keep(state: String, via: String, url: String? = null, number: Int? = null) {
            runCatching {
                b?.saveReport(buildJsonObject {
                    put("title", p.title)
                    put("kind", p.kind)
                    p.item?.let { put("item", it) }
                    url?.let { put("url", it) }
                    number?.let { put("number", it) }
                    put("state", state)
                    put("via", via)
                    put("at", Instant.ofEpochMilli(now()).toString())
                })
            }
        }
        suspend fun toWishlist(state: String) {
            runCatching { b?.tool("report_problem", p.input, "report-${p.id}") }
            keep(state, "wishlist")
        }
        if (!send) {
            toWishlist("declined")
            tell(null, "studio.report.kept", emptyMap())
            return@launch
        }
        val d = discussionsFor(githubToken)
        try {
            if (p.foundId != null && p.foundNumber != null && p.foundUrl != null) {
                val found = Discussion(p.foundId, p.foundNumber, p.foundTitle ?: p.title, p.foundUrl, p.foundUpvotes, p.foundUpvoted, false, null)
                d.upvote(found)
                keep("upvoted", "discussion", p.foundUrl, p.foundNumber)
                tell(null, "studio.report.voted", mapOf("n" to p.foundNumber, "url" to p.foundUrl))
            } else {
                val made = d.create(ReportDraft(p.kind, p.title, p.whatIsWrong, p.suggestedFix, p.item, p.nativeCheck))
                keep("posted", "discussion", made.url, made.number)
                tell(null, "studio.report.posted", mapOf("n" to made.number, "url" to made.url))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            line("report: not posted (${(e as? DiscussionsError)?.kind ?: e.javaClass.simpleName})")
            toWishlist("kept")
            tell(null, "studio.report.failed", emptyMap())
        }
    }

    // --- the notebook's versions ("What my tutor knows") ------------------------------------------------------------

    /** The notebook's older versions on the bridge, newest first; empty when it can't be read. */
    suspend fun notebookVersions(): List<LiveBridge.NotebookVersion> = runCatching { bridge()?.notebookVersions() }.getOrNull().orEmpty()

    /** An older version back ([at]); null when done, else what went wrong. */
    suspend fun restoreNotebook(at: String): String? {
        val b = bridge() ?: return "this phone isn't paired with a node"
        return try {
            val n = b.restoreNotebook(at)
            withContext(io) {
                val next = Notebooks.written(store.cachedNotebook(), n.text, "restored", now())
                store.saveNotebook(next)
                notebook = next
            }
            null
        } catch (e: LiveBridgeError) {
            if (e.code == 404) "that version isn't there any more" else "the node wasn't reached"
        }
    }

    // --- notes to the learner -------------------------------------------------------------------------------------

    /**
     * Tells the learner [key]'s note in the chat, briefly: once a day per [kind] (null: every time). While no chat listens
     * (the app closed: the WorkManager check) it waits for the chat.
     */
    /** The chat's note for a refused credential: a sign-in to renew (ChatGPT, OpenRouter), or a key to enter again. */
    private fun keyNote(p: ProviderId): String = when (p) {
        ProviderId.OPENAI -> "tutorLive.note.signIn"
        ProviderId.OPENROUTER -> "tutorLive.note.signInRouter"
        else -> "tutorLive.note.key"
    }

    private fun tell(kind: String?, key: String, args: Map<String, Any?>) {
        val day = dayNow()
        if (kind != null && s.told[kind] == day) return
        if (kind != null) saveSettings { it.copy(told = it.told + (kind to day)) }
        val text = texts(key, args)
        line("note: $key")
        store.saveNotes(store.notes() + text)
        flushNotes()
    }

    private fun flushNotes() {
        val f = onNote ?: return
        val notes = store.notes()
        if (notes.isEmpty()) return
        store.saveNotes(emptyList())
        notes.forEach { runCatching { f(it) } }
    }

    // --- the tests' ---------------------------------------------------------------------------------------------------

    /** The app is on screen without the platform's lifecycle (a test, QA's hook). */
    internal fun foregroundForTest(on: Boolean) {
        foreground = on
    }

    /** The tests': waits until what was handed in has run and the worker stopped. */
    internal suspend fun awaitIdle(timeoutMs: Long = 10_000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            withContext(io) {}
            if (worker?.isActive != true) {
                withContext(io) {}
                if (worker?.isActive != true) return
            }
            delay(5)
        }
        error("the live tutor didn't get idle: ${stateNow()}")
    }

    /** The queue now (the tests'). */
    internal fun queue(): List<TurnRecord> = q

    companion object {
        /** The bridge's tutor session's protocol this app speaks (companion/bridge/src/features/tutor.ts TUTOR_PROTOCOL). */
        const val PROTOCOL = 1
    }
}
