package si.lanisce.lani.tutor

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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.BridgeConfig
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * The in-app tutor's prototype in shadow mode (plan 5, phase 0, check 6): a developer setting, off by default. It
 * watches the learner's real events (the writes the app delivered to the real tutor's node), runs the in-app loop on
 * them against the Messages API with the learner's own key, and shows what it would have said in the debug panel, next
 * to the real tutor's reply. It never acts: reads go through the paired bridge's GET routes only, writes are dry runs,
 * its replies never reach the chat or a notification, and nothing of it reaches the learner's data.
 *
 * Its state is in [store] (files/shadow-tutor), its key in [vault]. Everything it changes happens on one thread ([io]).
 */
class ShadowTutor(
    private val scope: CoroutineScope,
    val store: ShadowStore,
    private val vault: KeyVault,
    private val bridgeConfig: suspend () -> BridgeConfig?,
    /** A debug or qa build (BuildConfig.QA): the Messages API's base URL may be set (QA's mock). */
    val debug: Boolean,
    private val transportFor: (String, suspend () -> String?) -> TutorTransport = { base, key -> AnthropicTransport(base, key) },
    private val bridgeFor: (BridgeConfig) -> ShadowBridge = { ShadowBridge(it) },
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val read: (String) -> String? = Bundle::text,
    /** The learner's name (the guide's paragraphs say Jan). */
    private val learner: () -> String? = { null },
    /** Where the learner is in the app now (the screen), for the turn's message. */
    private val where: () -> String? = { null },
    /** A log line (debug and qa builds: logcat); never the key ([Redact]). */
    private val logLine: (String) -> Unit = {},
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    /** A chat's photo on the phone by its id (ChatPhotoStore), or null: the turn sends it as an image block ([ShadowImages]). */
    private val photo: (String) -> File? = { null },
    /**
     * The matrix's lanes' transport ([ShadowMatrix]): a client of their own (AnthropicTransport.laneClient), so their
     * streams never queue the primary's call behind them.
     */
    private val laneTransportFor: (String, suspend () -> String?) -> TutorTransport = { base, key -> AnthropicTransport(base, key, AnthropicTransport.laneClient) },
    /**
     * The OpenAI credentials (an API key, the ChatGPT sign-in: [OpenAICredentials]), or null (no OpenAI): the matrix's
     * OpenAI lanes and "also run on" an OpenAI model use them. The primary stays on Anthropic.
     */
    private val openai: OpenAICredentials? = null,
    /** The Responses API's client for a credential ([OpenAITransport]). */
    private val openaiTransportFor: (suspend () -> OpenAIBearer?) -> OpenAITransport = { OpenAITransport(it) },
    /** The lanes' Responses API client (a dispatcher of their own). */
    private val openaiLaneTransportFor: (suspend () -> OpenAIBearer?) -> OpenAITransport = { OpenAITransport(it, OpenAITransport.laneClient) },
    /** The ChatGPT sign-in's loopback port (0: any free one; a test's from its block). */
    private val signInPort: Int = 0,
) {
    private val specs = PhaseA.load(read)
    private val guide = Guide(read)
    private val prefix = Prefix.bundled(specs, guide, read)
    private val prices = read("prices.json")?.let(Prices::parse) ?: Prices(emptyMap())
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var worker: Job? = null

    private var s = ShadowSettings()
    private var nb = NotebookState()
    private var q: List<TurnRecord> = emptyList()
    private val logs = mutableMapOf<String, DayLog>()

    /** The lanes' reads: one GET for the same read of all lanes at an event (the primary keeps its own). */
    private val laneReads = SharedReads(scope, io, clock) { name, input ->
        val bridge = bridgeConfig()?.let(bridgeFor)
        BridgeReads({ bridge }, ::allEvents).read(name, input)
    }

    private val matrixHost = object : ShadowMatrix.Host {
        override val settings: ShadowSettings get() = s
        override val prefix: Prefix get() = this@ShadowTutor.prefix
        override val notebook: NotebookState get() = nb
        override fun ready(provider: ProviderId): Boolean = when (provider) {
            ProviderId.ANTHROPIC -> hasKey
            ProviderId.OPENAI -> openai != null && openaiReady.isNotEmpty()
        }
        override fun blocked(provider: ProviderId): String? = when (provider) {
            ProviderId.ANTHROPIC -> when {
                s.keyRefused -> "the key was refused (401)"
                s.lowCredit -> "the credit balance is too low"
                else -> null
            }
            ProviderId.OPENAI -> when {
                s.openai.refused -> "the OpenAI credential was refused (check again, or sign in again)"
                else -> s.openai.exhausted
            }
        }
        override fun listed(provider: ProviderId): List<ModelInfo>? = when (provider) {
            ProviderId.ANTHROPIC -> s.listed
            ProviderId.OPENAI -> s.openai.listed.takeIf { ready(ProviderId.OPENAI) }
        }
        override fun provider(model: String): TutorProvider = when (ProviderId.of(model)) {
            ProviderId.ANTHROPIC -> AnthropicProvider(laneTransportFor(baseUrl()) { vault.load() })
            ProviderId.OPENAI -> openaiProvider(lane = true)
        }
        override fun tools(notebook: ShadowTools.NotebookHost, onReply: suspend (ShadowReply) -> Unit, onGuide: (String) -> Unit): ToolRunner =
            ShadowTools(specs.associateBy { it.name }, laneReads, guide, notebook, onReply, learner, onGuide, clock)
        override fun event(id: String): ShadowEvent? = findEvent(id)
        override fun real(day: String): List<RealReply> = log(day).real
        override fun blocks(t: TurnRecord, events: List<ShadowEvent>): List<JsonObject> = imageBlocks(t, events) + textBlock(content(t, events))
        override fun where(): String? = this@ShadowTutor.where()
        override fun cost(model: String, usage: Usage, billing: String): Priced = prices.priced(model, usage, billing)
        override fun closingPending(): Boolean {
            if (q.any { it.kind == TurnKind.CLOSING && it.eventKind == "closing" }) return true
            // the primary's rollover queues it at its next turn: yesterday's thread, answered, under this prefix
            val th = thread ?: return false
            return th.day != dayNow() && th.prefix == prefix.hash && Threads.answered(th.messages) &&
                s.enabled && hasKey && !s.keyRefused && !s.lowCredit && spent() < s.budgetPerDay
        }
    }

    /**
     * The matrix test (docs/plans/05-phase-0.md, "The matrix"): lanes of one model at one effort beside the primary, each
     * with its own thread, queue and worker; off by default. The primary runs exactly as without it.
     */
    val matrix = ShadowMatrix(scope, File(store.dir, "matrix"), matrixHost, clock, zone, io, sleep, logLine)

    var settings by mutableStateOf(ShadowSettings())
        private set
    var hasKey by mutableStateOf(false)
        private set
    /** The OpenAI credentials set ([Billing]: the ChatGPT plan, an API key); empty: none. */
    var openaiReady by mutableStateOf<Set<String>>(emptySet())
        private set
    /** The ChatGPT account signed in (its email, for the panel only: never in the settings or the export), or null. */
    var chatgptAccount by mutableStateOf<String?>(null)
        private set
    /** Signed in with ChatGPT without the plan's use granted: no calls with it. */
    var chatgptNoPlan by mutableStateOf(false)
        private set
    /** Whether this build has OpenAI (the app's does; a test's may not). */
    val openaiAvailable: Boolean get() = openai != null
    /** Today's log (what the panel shows first). */
    var today by mutableStateOf(DayLog(""))
        private set
    var queued by mutableStateOf<List<TurnRecord>>(emptyList())
        private set
    var notebook by mutableStateOf(NotebookState())
        private set
    var thread by mutableStateOf<ThreadState?>(null)
        private set
    /** What the shadow is doing ("off", "idle", "running …", "paused: …"). */
    var status by mutableStateOf("off")
        private set
    /** The debug panel is open. */
    var panelOpen by mutableStateOf(false)
    /** The tool definitions and the core prompt, as sent (for the panel's "prefix"). */
    val prefixChars: Int = prefix.system.length + prefix.tools.toString().length
    /** The day's thread was closed (its closing turn ended: the notebook rewritten): the developer's data goes to the node (app/DevUpload). */
    var onDayClosed: () -> Unit = {}
    val toolNames: List<String> = specs.map { it.name }

    init {
        scope.launch(io) {
            s = store.settings()
            nb = store.notebook()
            q = store.queue()
            hasKey = vault.has()
            loadOpenAI()
            publish()
            matrix.start()
            if (s.enabled) wakeUp()
        }
    }

    private fun now() = clock()
    private fun dayNow() = ShadowStore.dayOf(now(), zone())
    private fun log(day: String): DayLog = logs.getOrPut(day) { store.day(day) }

    private fun saveLog(l: DayLog) {
        logs[l.day] = l
        store.saveDay(l)
        if (l.day == dayNow()) today = l
    }

    private fun publish() {
        settings = s
        notebook = nb
        queued = q
        thread = store.thread()
        today = log(dayNow())
        status = statusNow()
    }

    private fun statusNow(): String = when {
        !s.enabled -> "off"
        !hasKey -> "no key"
        s.keyRefused -> "paused: the key was refused (401)"
        s.lowCredit -> "paused: the credit balance is too low"
        spent() >= s.budgetPerDay -> "paused: today's budget (\$${"%.2f".format(s.budgetPerDay)}) is spent"
        worker?.isActive == true && q.any { it.status == TurnStatus.RUNNING } -> "running ${q.first { it.status == TurnStatus.RUNNING }.eventKind}"
        q.isNotEmpty() -> "${q.size} turn(s) waiting"
        else -> "idle"
    }

    private fun saveSettings(f: (ShadowSettings) -> ShadowSettings) {
        s = f(s)
        store.saveSettings(s)
        settings = s
        status = statusNow()
        matrix.refresh()
    }

    private fun saveQueue(next: List<TurnRecord>) {
        q = next
        store.saveQueue(q)
        queued = q
        status = statusNow()
    }

    private fun line(msg: String) = logLine(Redact.of(msg))

    /** Today's spend in dollars. */
    private fun spent(): Double = log(dayNow()).calls.sumOf { it.cost ?: 0.0 }

    private fun catalog() = ModelCatalog(s.listed)

    // --- the providers -------------------------------------------------------------------------------------------

    /** The OpenAI credential in use: the one the settings chose when set, else the ChatGPT plan, else the key; null: none. */
    fun openaiBilling(): String? {
        val ready = openaiReady
        return s.openai.use?.takeIf { it in ready } ?: when {
            Billing.CHATGPT_PLAN in ready -> Billing.CHATGPT_PLAN
            Billing.API_KEY in ready -> Billing.API_KEY
            else -> null
        }
    }

    /** The OpenAI provider with the credential in use ([lane]: the lanes' client); after a 401 the sign-in is renewed once. */
    private fun openaiProvider(lane: Boolean): TutorProvider {
        val billing = openaiBilling() ?: Billing.API_KEY
        val creds = openai
        val bearer: suspend () -> OpenAIBearer? = { creds?.bearer(billing) }
        val transport = if (lane) openaiLaneTransportFor(bearer) else openaiTransportFor(bearer)
        val renew: (suspend () -> Boolean)? = if (billing == Billing.CHATGPT_PLAN && creds != null) {
            {
                try {
                    creds.chatgpt.accessToken(force = true) != null
                } catch (e: ApiError) {
                    false
                }
            }
        } else null
        return OpenAIProvider(transport, billing, renew)
    }

    /** The provider of a primary turn on [model]: the Claude key's, or (an "also run on" an OpenAI model) OpenAI's. */
    private fun providerFor(model: String): TutorProvider = when (ProviderId.of(model)) {
        ProviderId.ANTHROPIC -> AnthropicProvider(transportFor(baseUrl()) { vault.load() })
        ProviderId.OPENAI -> openaiProvider(lane = false)
    }

    /** The prefix's fingerprint for a thread on [model] (the primary's: the Claude key's, [Prefix.hash]). */
    private fun hashFor(model: String): String = if (ProviderId.of(model) == ProviderId.ANTHROPIC) prefix.hash else openaiProvider(lane = false).prefixHash(prefix)

    /** The base URL calls go to: the API, or (a debug or qa build) the mock QA set. */
    fun baseUrl(): String = KeyGuard.baseUrl(debug, s.debugBaseUrl)

    // --- the panel's settings -----------------------------------------------------------------------------------

    /** Shows (or hides) the developer section in the settings. */
    fun showDeveloper(on: Boolean) = scope.launch(io) { saveSettings { it.copy(developer = on) } }

    fun setEnabled(on: Boolean) = scope.launch(io) {
        saveSettings { it.copy(enabled = on) }
        if (on) wakeUp()
    }

    fun update(f: (ShadowSettings) -> ShadowSettings) = scope.launch(io) {
        saveSettings(f)
        wakeUp()
    }

    /**
     * Checks [key] with GET /v1/models (not a model call) and keeps it (encrypted) when the API takes it; the models it lists
     * choose the presets' ids. Null when kept, else what went wrong.
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
        // another key (another account or workspace): the thread's thinking and cache were made under the old one, so it
        // restarts as text at its next turn
        val before = vault.load()
        if (before != null && before != k) store.thread()?.let { store.saveThread(it.copy(prefix = "another-key")); thread = store.thread() }
        vault.save(k)
        hasKey = true
        saveSettings { it.copy(listed = models, listedAt = now(), keyRefused = false, lowCredit = false) }
        line("key checked: ${models.size} model(s) listed")
        wakeUp()
        matrix.resume(ProviderId.ANTHROPIC)
        null
    }

    /** The models again with the kept key (the credit came back, a model was added). */
    suspend fun recheck(): String? = withContext(io) {
        val models = try {
            transportFor(baseUrl()) { vault.load() }.models()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            if (e is ApiError.Auth) saveSettings { it.copy(keyRefused = true) }
            return@withContext Redact.of(e.message ?: e.kind)
        }
        saveSettings { it.copy(listed = models, listedAt = now(), keyRefused = false, lowCredit = false) }
        wakeUp()
        matrix.resume(ProviderId.ANTHROPIC)
        null
    }

    fun removeKey() = scope.launch(io) {
        vault.remove()
        hasKey = false
        saveSettings { it.copy(keyRefused = false) }
    }

    // --- OpenAI's credentials (the panel's Settings) ------------------------------------------------------------------

    private suspend fun loadOpenAI() {
        val c = openai ?: return
        openaiReady = c.available()
        val session = c.chatgpt.session()
        chatgptAccount = session?.takeIf { it.connected }?.let { it.email ?: "signed in" }
        chatgptNoPlan = session?.connected == true && !session.planUsage
    }

    /**
     * A credential was set ([billing]'s, listing [models]): its models become the OpenAI catalog when it's the one in
     * use, the pause ends, and the first time OpenAI's default lanes join the matrix (a lane toggled off later stays off).
     */
    private fun afterOpenAI(models: List<ModelInfo>, billing: String) {
        saveSettings {
            val o = it.openai.copy(refused = false, exhausted = null)
            it.copy(
                openai = if (openaiBilling() == billing) o.copy(listed = models, listedAt = now(), listedWith = billing) else o,
                matrix = if (it.matrix.openaiOffered) it.matrix else it.matrix.copy(lanes = (it.matrix.lanes + MatrixSettings.OPENAI_LANES).distinct(), openaiOffered = true),
            )
        }
        matrix.resume(ProviderId.OPENAI)
    }

    /**
     * Checks an OpenAI API [key] with GET /v1/models (not a model call) and keeps it, encrypted as the Claude key is.
     * Null when kept, else what went wrong.
     */
    suspend fun setOpenAIKey(key: String): String? = withContext(io) {
        val c = openai ?: return@withContext "this build has no OpenAI"
        val k = key.trim()
        if (k.isEmpty()) return@withContext "the key is empty"
        val models = try {
            openaiTransportFor { OpenAIBearer(k, Billing.API_KEY) }.models()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            line("OpenAI key check failed: ${e.kind}")
            return@withContext if (e is ApiError.Auth) "the key was refused (401)" else Redact.of(e.message ?: e.kind, k)
        }
        c.apiKey.save(k)
        loadOpenAI()
        afterOpenAI(models, Billing.API_KEY)
        line("OpenAI key checked: ${models.size} model(s) listed")
        null
    }

    fun removeOpenAIKey() = scope.launch(io) {
        openai?.apiKey?.remove()
        loadOpenAI()
        relistOpenAI()
    }

    private var signIn: ChatGPTAuth.SignIn? = null

    /**
     * "Sign in with ChatGPT": the loopback listener and the authorize URL, [open]ed in the browser; then the code
     * exchanged, the identity checked, the plan's models listed. Null when signed in with the plan's use granted, else
     * what went wrong (declined, timed out, the plan's use not granted …).
     */
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
            // the code exchange, the identity's check and the Keystore: off the caller's (the panel's main) thread
            withContext(Dispatchers.IO) { pending.await() }
        } catch (e: CancellationException) {
            pending.cancel()
            throw e
        } catch (e: ChatGPTError) {
            line("ChatGPT sign-in: ${e.code}")
            return e.message
        } catch (e: ApiError) {
            line("ChatGPT sign-in: ${e.kind}")
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
                afterOpenAI(emptyList(), Billing.CHATGPT_PLAN)
                return@withContext "signed in; the plan's models couldn't be listed (${e.kind}): Check again"
            }
            afterOpenAI(models, Billing.CHATGPT_PLAN)
            line("ChatGPT sign-in: ${models.size} model(s) listed")
            null
        }
    }

    /** Stops a sign-in waiting for the browser. */
    fun cancelSignIn() {
        signIn?.cancel()
        signIn = null
    }

    /** Signs out of ChatGPT (the refresh token revoked); null when OpenAI confirmed it, else what to tell the learner. */
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
        relistOpenAI()
        note
    }

    /** Which OpenAI credential the calls use when both are set ([billing]; null: the ChatGPT plan first). */
    fun useOpenAI(billing: String?) = scope.launch(io) {
        saveSettings { it.copy(openai = it.openai.copy(use = billing)) }
        relistOpenAI()
    }

    /** The OpenAI models again with the credential in use (the allowance came back, a model was added). Null when listed. */
    suspend fun recheckOpenAI(): String? = withContext(io) {
        val c = openai ?: return@withContext "this build has no OpenAI"
        loadOpenAI()
        val billing = openaiBilling() ?: return@withContext "no OpenAI credential"
        val models = try {
            openaiTransportFor { c.bearer(billing) }.models()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            if (e is ApiError.Auth) saveSettings { it.copy(openai = it.openai.copy(refused = true)) }
            return@withContext Redact.of(e.message ?: e.kind)
        }
        afterOpenAI(models, billing)
        null
    }

    /** After a credential went or the choice changed: the catalog of the one in use now, or none. */
    private suspend fun relistOpenAI() {
        if (openaiBilling() == null) {
            saveSettings { it.copy(openai = it.openai.copy(listed = emptyList(), listedAt = 0, listedWith = null, refused = false, exhausted = null)) }
            return
        }
        if (s.openai.listedWith != openaiBilling()) recheckOpenAI() else matrix.refresh()
    }

    /** The notebook corrected by hand in the panel (plan 5: the learner reads and corrects it); the old version is kept. */
    fun editNotebook(text: String) = scope.launch(io) { writeNotebook(text.trim(), "by hand") }

    private fun writeNotebook(text: String, why: String) {
        nb = Notebooks.written(nb, text, why, now())
        store.saveNotebook(nb)
        notebook = nb
    }

    private val notebookHost = object : ShadowTools.NotebookHost {
        override fun note(line: String) {
            if (line.isBlank()) return
            nb = Notebooks.noted(nb, line, now())
            store.saveNotebook(nb)
            notebook = nb
        }

        override fun write(text: String) = writeNotebook(text, "notebook_write")
        override val size: Int get() = nb.text.length
    }

    // --- the app's events ----------------------------------------------------------------------------------------

    /**
     * A write the outbox delivered to the real tutor's node ([path], its [body]): the shadow's copy of the event. Only
     * read here: nothing of the shadow goes back.
     */
    fun onDelivered(clientId: String, path: String, body: String) = scope.launch(io) {
        if (!s.enabled) return@launch
        val e = ShadowEvents.of("ev_$clientId".take(40), path, body, now(), s.reactAtOnce) ?: return@launch
        add(e.image?.let { i -> e.copy(image = i.copy(bytes = photo(i.id)?.length() ?: 0)) } ?: e)
    }

    /** A role-play's turn sent straight to the node (past the outbox): recorded, not run (phase B). */
    fun onRoleplay(kind: String, conversationId: String, text: String, data: JsonObject?) = scope.launch(io) {
        if (!s.enabled) return@launch
        add(ShadowEvents.roleplay("ev_${UUID.randomUUID().toString().take(12)}", kind, conversationId, text, data, now()))
    }

    private fun add(e: ShadowEvent) {
        val l = log(ShadowStore.dayOf(e.at, zone()))
        if (l.events.any { it.id == e.id }) return
        saveLog(l.copy(events = l.events + e))
        line("event ${e.kind} (${e.handling})")
        when (e.handling) {
            Handling.AT_ONCE -> enqueue(listOf(e), e.kind)
            Handling.DIGEST -> store.saveDigest(store.digest() + e.id)
            Handling.RECORDED -> Unit
        }
        evening()
        wakeUp()
    }

    /**
     * The real tutor's reply (the bridge's SSE `reply`), matched to the event it answers: the latest of its
     * conversation the shadow saw in the last twelve hours (answers have a conversation of their own; chat and session
     * ends share "main").
     */
    fun onRealReply(conversationId: String, text: String, data: JsonObject?) = scope.launch(io) {
        if (!s.enabled) return@launch
        val at = now()
        val match = recentEvents().filter { it.conversationId == conversationId && it.at <= at + 5_000 && at - it.at < 12 * 3_600_000L && it.handling != Handling.RECORDED }
            .maxByOrNull { it.at }
        val l = log(dayNow())
        saveLog(l.copy(real = l.real + RealReply(ShadowReply(conversationId, text, at, data), match?.id)))
    }

    /** The app came on screen: a digest when it was away two hours or more (plan 5's first trigger). */
    fun onForeground() = scope.launch(io) {
        val away = store.lastAway()
        if (s.enabled && away > 0 && now() - away >= 2 * 3_600_000L) digest("the app opened after ${(now() - away) / 3_600_000L} h")
        evening()
        wakeUp()
    }

    fun onBackground() = scope.launch(io) { store.saveLastAway(now()) }

    /** The learner opened the chat: the digest's third trigger. */
    fun onChatOpened() = scope.launch(io) { if (s.enabled) digest("the learner opened the chat") }

    /** The panel's "run the digest now". */
    fun digestNow() = scope.launch(io) { digest("asked in the panel") }

    /** The evening (from 19:00), once a day: the digest's second trigger. */
    private fun evening() {
        if (!s.enabled) return
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
        val worth = ShadowEvents.worth(events)
        line("digest of ${events.size} event(s) ($why): ${worth ?: "nothing worth saying"}")
        if (worth == null) {
            val l = log(dayNow())
            val skipped = TurnRecord(
                id = newId(), eventIds = ids, kind = TurnKind.STATELESS, task = Task.ROUTINE, model = "", eventKind = "digest",
                queuedAt = now(), endedAt = now(), status = TurnStatus.SKIPPED, why = "$why; nothing worth saying (no answer wrong, no milestone): no call",
            )
            saveLog(l.copy(turns = l.turns + skipped))
            return
        }
        enqueue(events, "digest", "$why; $worth")
    }

    /**
     * "Also run on model X at effort Y" for event [eventId]: its own stateless call on [model] at [effort] (null: the
     * task's by the settings; fitted to what the model takes), metered like the rest.
     */
    fun alsoRun(eventId: String, model: String, effort: String? = null) = scope.launch(io) {
        val e = findEvent(eventId) ?: return@launch
        val task = Task.of(e.kind)
        val level = ModelCatalog.fit(effort ?: ModelCatalog.effortFor(task, s.preset, model, s.efforts), model)
        saveQueue(
            q + TurnRecord(
                id = newId(), eventIds = listOf(e.id), kind = TurnKind.ALSO, task = task, model = model,
                eventKind = e.kind, queuedAt = now(), why = "also run on $model" + (level?.let { " · $it" } ?: ""), effort = level,
            ),
        )
        wakeUp()
    }

    /** A failed turn queued again (the panel). */
    fun retry(turnId: String) = scope.launch(io) {
        val l = logs.values.firstOrNull { d -> d.turns.any { it.id == turnId } } ?: return@launch
        val t = l.turns.first { it.id == turnId }
        saveLog(l.copy(turns = l.turns.filterNot { it.id == turnId }))
        saveQueue(q + t.copy(status = TurnStatus.QUEUED, notBefore = 0, error = null, queuedAt = now()))
        saveSettings { it.copy(keyRefused = false, lowCredit = false, openai = it.openai.copy(refused = false, exhausted = null)) }
        wakeUp()
        matrix.resume()
    }

    /** The matrix's settings changed in the panel (on, a lane's toggle): its lanes follow at once. */
    fun updateMatrix(f: (MatrixSettings) -> MatrixSettings) = scope.launch(io) { saveSettings { it.copy(matrix = f(it.matrix)) } }

    private fun newId() = "t_" + UUID.randomUUID().toString().replace("-", "").take(12)

    private fun recentEvents(): List<ShadowEvent> {
        val today = dayNow()
        val yesterday = java.time.LocalDate.parse(today).minusDays(1).toString()
        return log(yesterday).events + log(today).events
    }

    private fun findEvent(id: String): ShadowEvent? =
        recentEvents().firstOrNull { it.id == id } ?: logs.values.asSequence().flatMap { it.events }.firstOrNull { it.id == id }

    /** Every event of the days in memory (recent_answers reads the reviews among them). */
    private fun allEvents(): List<ShadowEvent> = recentEvents()

    private fun enqueue(events: List<ShadowEvent>, eventKind: String, why: String? = null) {
        val task = if (eventKind == "digest") Task.ROUTINE else Task.of(eventKind)
        val cat = catalog()
        val model = cat.modelFor(task, s.preset, s.models)
        val effort = ModelCatalog.effortFor(task, s.preset, model, s.efforts)
        val threadModel = store.thread()?.takeIf { it.day == dayNow() }?.model ?: cat.modelFor(Task.CHAT, s.preset, s.models)
        // on the thread's model a turn joins the thread whatever its effort: a level of its own is an effort message there
        val kind = if (model == threadModel) TurnKind.DAY else TurnKind.STATELESS
        saveQueue(q + TurnRecord(id = newId(), eventIds = events.map { it.id }, kind = kind, task = task, model = model, eventKind = eventKind, queuedAt = now(), why = why, effort = effort))
        wakeUp()
        // the matrix's lanes get the same turn, each on its own thread at its own model and effort
        matrix.enqueue(events, eventKind, task, why)
    }

    // --- the worker -----------------------------------------------------------------------------------------------

    private fun wakeUp() {
        status = statusNow()
        if (!s.enabled) return
        if (worker?.isActive == true) {
            wake.trySend(Unit)
            return
        }
        worker = scope.launch(io) {
            try {
                work()
            } finally {
                status = statusNow()
            }
        }
    }

    /** Runs the queue's turns one at a time: the day's closing first, a chat ahead of the rest, then in order. */
    private suspend fun work() {
        while (true) {
            if (!s.enabled || !hasKey || s.keyRefused || s.lowCredit) return
            rollover()
            if (spent() >= s.budgetPerDay) return
            val at = now()
            val dayTurnStarted = q.firstOrNull { it.kind == TurnKind.DAY && it.startedAt != null }
            val ready = q.filter { it.notBefore <= at && (it.kind != TurnKind.DAY || dayTurnStarted == null || it.id == dayTurnStarted.id) }
            val next = ready.sortedWith(compareBy<TurnRecord>({ priority(it) }, { it.queuedAt })).firstOrNull()
            if (next == null) {
                // the day's turns wait behind the one begun (its backoff), the rest by their own time
                val wait = q.minOfOrNull { if (it.kind == TurnKind.DAY && dayTurnStarted != null) maxOf(it.notBefore, dayTurnStarted.notBefore) else it.notBefore }
                    ?.minus(at) ?: return
                withTimeoutOrNull(wait.coerceIn(500, 60_000)) { wake.receive() }
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

    private fun priority(t: TurnRecord): Int = when {
        t.kind == TurnKind.CLOSING -> 0
        t.eventKind == "chat" -> 1
        t.eventKind == "answer" -> 2
        t.kind == TurnKind.ALSO -> 4
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
        saveQueue(
            q + TurnRecord(
                id = newId(), eventIds = emptyList(), kind = TurnKind.CLOSING, task = Task.CHAT, model = th.model, eventKind = "closing",
                queuedAt = now(), why = "the day's end (${th.day})",
            ),
        )
    }

    private fun archive(th: ThreadState) {
        store.saveThreadArchive(th)
        store.saveThread(null)
        thread = null
    }

    private suspend fun runTurn(t0: TurnRecord) {
        var t = t0.copy(status = TurnStatus.RUNNING, startedAt = t0.startedAt ?: now(), attempts = t0.attempts + 1)
        val resumed = t0.startedAt != null
        replace(t)
        val events = t.eventIds.mapNotNull(::findEvent)
        val bridge = bridgeConfig()?.let(bridgeFor)
        val tools = ShadowTools(
            specs.associateBy { it.name },
            BridgeReads({ bridge }, ::allEvents),
            guide,
            notebookHost,
            onReply = { r -> t = t.copy(replies = t.replies + r); replace(t) },
            learner = learner,
            onGuide = { id -> val l = log(dayNow()); saveLog(l.copy(guide = l.guide + (id to (l.guide[id] ?: 0) + 1))) },
            clock = clock,
        )
        val provider = providerFor(t.model)
        val format = provider.format
        val loop = TutorLoop(provider, tools, maxCalls = if (t.kind == TurnKind.CLOSING) 3 else 8, budgetOk = { spent() < s.budgetPerDay }, sleep = sleep)

        // the level the turn asks for: queued with it, or (a closing, a turn queued before efforts were settings) its task's now
        val want = ModelCatalog.fit(t.effort ?: ModelCatalog.effortFor(t.task, s.preset, t.model, s.efforts), t.model)

        // the turn's messages, where each step is saved, and the requests' top-level effort
        var th = store.thread()
        val stateless = t.kind == TurnKind.STATELESS || t.kind == TurnKind.ALSO || (t.kind == TurnKind.DAY && th != null && th.day == dayNow() && th.model != t.model)
        val messages: MutableList<JsonObject>
        val save: suspend (List<JsonObject>) -> Unit
        val top: String?
        if (t.kind == TurnKind.CLOSING) {
            val closing = th ?: return finish(t.copy(error = "no thread to close"), TurnStatus.SKIPPED)
            // made under another prefix (an app update): it can't take another call; it restarts (or closes) as text
            if (closing.prefix != provider.prefixHash(prefix)) return finish(t.copy(error = "the prefix changed (an app update): no closing call"), TurnStatus.SKIPPED, closing)
            val why = if (t.eventKind == "restart") "The thread is long (${closing.tokens} tokens) and restarts now" else "The day (${closing.day}) is over"
            top = closing.topEffort
            messages = (if (resumed) closing.messages else format.append(closing.messages, listOf(textBlock(TurnText.closing(why))), top, onThread(closing, want))).toMutableList()
            save = { m -> val next = (th ?: closing).copy(messages = m); th = next; store.saveThread(next); thread = next }
            save(messages)
        } else if (stateless) {
            // a call of its own: its level is its top-level effort
            top = want
            messages = (
                store.turnMessages(t.id) ?: run {
                    // what the learner was shown before the turn's events (never the real answer to them)
                    val cutoff = events.minOfOrNull { it.at } ?: now()
                    val shown = shownBetween(ShadowStore.dayOf(cutoff, zone()), 0, cutoff, t.eventIds, max = 3)
                    val texts = listOf(
                        TurnText.notebook(nb), TurnText.lines("Today so far:", th?.takeIf { it.day == dayNow() }?.lines.orEmpty().takeLast(15)),
                        TurnText.shown(shown, zone(), TurnText.SHOWN_TODAY), TurnText.now(now(), zone(), where()),
                    )
                    listOf(format.user(texts.filter { it.isNotEmpty() }.map(::textBlock) + imageBlocks(t, events) + textBlock(content(t, events))))
                }
                ).toMutableList()
            save = { m -> store.saveTurnMessages(t.id, m) }
            save(messages)
        } else {
            var cur = th?.takeIf { it.day == dayNow() } ?: newThread(t.model, store.threadArchive()?.lines.orEmpty())
            // a thread made under another prefix (an app update changed a tool or the prompt) restarts, as text
            var appendEvent = !resumed || cur.messages.isEmpty()
            if (cur.prefix != provider.prefixHash(prefix) && cur.messages.isNotEmpty()) {
                val why = if (cur.prefix == "another-key") "The key changed" else "The app was updated (its tools or prompt changed)"
                cur = restarted(cur, "$why, so the thread restarted")
                val l = log(dayNow())
                saveLog(l.copy(restarts = l.restarts + cur.restarts.last()))
                appendEvent = true
            }
            if (appendEvent) {
                val fresh = cur.messages.isEmpty()
                // the real tutor's replies since the thread's last: what the learner saw, which their event answers
                val shown = shownBetween(cur.day, cur.shownUntil, events.minOfOrNull { it.at } ?: now(), t.eventIds)
                val blocks = buildList {
                    if (fresh) add(textBlock(TurnText.notebook(nb)))
                    if (fresh) TurnText.lines("Yesterday in brief:", cur.yesterday.takeLast(12)).takeIf { it.isNotEmpty() }?.let { add(textBlock(it)) }
                    TurnText.lines("Meanwhile (other models' replies, not in this thread):", cur.pending).takeIf { it.isNotEmpty() }?.let { add(textBlock(it)) }
                    TurnText.shown(shown, zone()).takeIf { it.isNotEmpty() }?.let { add(textBlock(it)) }
                    add(textBlock(TurnText.now(now(), zone(), where())))
                    addAll(imageBlocks(t, events))
                    add(textBlock(content(t, events)))
                }
                cur = cur.copy(
                    messages = format.append(cur.messages, blocks, cur.topEffort, onThread(cur, want)), pending = emptyList(),
                    shownUntil = maxOf(cur.shownUntil, shown.maxOfOrNull { it.reply.at } ?: 0),
                )
            }
            top = cur.topEffort
            th = cur
            store.saveThread(cur)
            thread = cur
            messages = cur.messages.toMutableList()
            save = { m -> val next = (th ?: cur).copy(messages = m); th = next; store.saveThread(next); thread = next }
        }

        val threadKind = when {
            t.kind == TurnKind.CLOSING -> "closing"
            t.kind == TurnKind.ALSO -> "also"
            stateless -> "stateless"
            else -> "day"
        }
        // the level it runs at: on a thread the one in effect after its user message (its effort message's, else the
        // thread's top-level one), else its own
        val level = Effort.inEffect(messages, top)
        val byMessage = Effort.byMessage(messages)
        if (t.effort != level) {
            t = t.copy(effort = level)
            replace(t)
        }
        val before = messages.size
        val r = loop.run(
            prefix, t.model, t.task, messages, callsDone = t.callIds.size, save = save,
            // the top-level effort: a thread's is the same for all its turns (a change clears its messages' cache); a
            // stateless call's is its own
            effort = top,
            maxTokens = t.task.maxTokensAt(level),
            onCall = { c ->
                // the thread's first call: no answer in it yet (onCall comes before the answer is added)
                val call = callRecord(t, c, threadKind, firstOfThread = !Threads.answered(messages), effort = level, byMessage = byMessage, images = ShadowImages.count(messages), billing = provider.billing)
                if (c.step != null) {
                    t = t.copy(callIds = t.callIds + call.id)
                    if (threadKind == "day" || threadKind == "closing") th = th?.copy(tokens = call.usage.prompt)?.also { store.saveThread(it); thread = it }
                }
                val l = log(dayNow())
                saveLog(l.copy(calls = l.calls + call))
                replace(t)
                line(
                    "call ${call.model}${call.effort?.let { " $it" + if (call.effortByMessage) " (message)" else "" } ?: ""} ${call.eventKind}: ${call.stopReason ?: call.error} in:${call.usage.input} w:${call.usage.cacheWrite} r:${call.usage.cacheRead} out:${call.usage.output} \$${call.cost}" +
                        (call.inputTransformations?.let { " input_transformations: $it" } ?: ""),
                )
            },
            onTool = { rec -> t = t.copy(toolCalls = t.toolCalls + rec); replace(t) },
        )
        t = t.copy(text = Threads.text(messages.drop(before)).take(2000))
        // no reply tool called, the answer written as text: that text is the reply (marked, and counted per day)
        val conversation = if (t.eventKind == "digest") "main" else events.firstOrNull()?.conversationId ?: "main"
        ReplyFallback.of(r, t.kind, t.replies.isNotEmpty(), conversation, now())?.let { reply ->
            t = t.copy(replies = listOf(reply), replyFromText = true)
            line("turn ${t.id} ${t.eventKind} on ${t.model} (${t.effort ?: "no effort"}): no reply tool called, its text taken as the reply")
        }
        when (val end = r.end) {
            LoopEnd.Done -> finish(t, TurnStatus.DONE, th)
            LoopEnd.Limit -> finish(t, TurnStatus.LIMIT, th)
            LoopEnd.Truncated -> finish(t, TurnStatus.TRUNCATED, th)
            is LoopEnd.Refused -> finish(t.copy(error = "the model declined (refusal${end.details?.str("category")?.let { ": $it" } ?: ""}): a neutral note, no retry"), TurnStatus.REFUSED, th)
            LoopEnd.Paused -> replace(t.copy(status = TurnStatus.QUEUED))
            is LoopEnd.Failed -> failed(t, end.error, th)
        }
    }

    /** What the turn is about: one event, or the digest's events. */
    private fun content(t: TurnRecord, events: List<ShadowEvent>): String =
        if (t.eventKind == "digest") TurnText.digest(events, t.why.orEmpty(), zone())
        else events.firstOrNull()?.let { TurnText.event(it, zone(), imageSent = it.image?.let { i -> photo(i.id) } != null) } ?: "(the event is gone)"

    /** The photo of the turn's chat, as an image block before its text ([ShadowImages]); none for a digest or a photo gone. */
    private fun imageBlocks(t: TurnRecord, events: List<ShadowEvent>): List<JsonObject> =
        if (t.eventKind == "digest") emptyList()
        else events.take(1).mapNotNull { e -> e.image?.let { i -> photo(i.id)?.let { f -> runCatching { ShadowImages.block(f) }.getOrNull() } } }

    /**
     * The level a turn at [want] sets on thread [th] by an effort message, or null (the thread's level in effect holds)
     * when its model has no per-message effort: changing the top-level effort instead would clear the thread's cache
     * (the effort docs: it shapes the rendered prompt), so such a turn runs at the thread's level, and says so.
     */
    private fun onThread(th: ThreadState, want: String?): String? = DayThreads.onThread(th, want)

    /** A new day's thread on [model]: its top-level effort the chat's now, fixed for its life. */
    private fun newThread(model: String, yesterday: List<String>): ThreadState =
        DayThreads.fresh(dayNow(), model, now(), yesterday, hashFor(model), ModelCatalog.effortFor(Task.CHAT, s.preset, model, s.efforts))

    /**
     * A fresh thread after [th] (plan 5, "Compaction: our own"): its first user message the notebook, a few lines on the
     * day so far and the last exchanges as plain text (never as assistant turns), so no history is edited.
     */
    private fun restarted(th: ThreadState, why: String): ThreadState =
        // the same top-level effort: the prefix's cache may be bound to it (the docs: model-specific), and a turn at
        // another level sets its own by an effort message
        DayThreads.restarted(th, DayThreads.restartBlocks(nb, why, th.tokens, s.limit, th.lines, lastExchanges()), now(), hashFor(th.model), th.topEffort)

    /** The real tutor's replies of [day] the learner saw between [after] and [before], not to [own] ([TurnText.shownOf]). */
    private fun shownBetween(day: String, after: Long, before: Long, own: Collection<String>, max: Int = 5): List<RealReply> =
        TurnText.shownOf(log(day).real, after, before, own, max)

    /**
     * The call's record; a call that answered keeps its usage as the API sent it when it is its thread's first or the
     * day's 20th, 40th … ([UsageSample]), while the developer setting is on.
     */
    private fun callRecord(
        t: TurnRecord, c: CallOutcome, thread: String, firstOfThread: Boolean = false, effort: String? = t.effort, byMessage: Boolean = false,
        images: Int = 0, billing: String = Billing.API_KEY,
    ): CallRecord {
        val r = c.step
        val n = log(dayNow()).calls.count { it.error == null } + 1
        val raw = r?.rawUsage?.takeIf { s.developer && UsageSample.keep(firstOfThread, n) }
        val priced = r?.let { prices.priced(it.model ?: t.model, it.usage, billing) }
        return Meter.record(t, c, now(), thread, priced, effort, effortByMessage = byMessage, images = images, raw = raw, billing = billing)
    }

    private fun replace(t: TurnRecord) {
        if (q.none { it.id == t.id }) return
        saveQueue(q.map { if (it.id == t.id) t else it })
    }

    private fun failed(t: TurnRecord, e: ApiError, th: ThreadState?) {
        val error = Redact.of(e.message ?: e.kind)
        if (ProviderId.of(t.model) == ProviderId.OPENAI) return openaiFailed(t, e, error, th)
        when {
            e is ApiError.Auth -> {
                saveSettings { it.copy(keyRefused = true) }
                replace(t.copy(status = TurnStatus.QUEUED, error = error))
            }
            e is ApiError.LowCredit -> {
                saveSettings { it.copy(lowCredit = true) }
                // a chat says why it can't answer; routine work waits for the credit
                if (t.task == Task.CHAT || t.task == Task.GRADING) finish(t.copy(error = "low credit: the tutor can't answer ($error)"), TurnStatus.FAILED, th)
                else replace(t.copy(status = TurnStatus.QUEUED, error = error))
            }
            Failures.retryAt(e, t.attempts, now()) != null -> replace(t.copy(status = TurnStatus.QUEUED, error = error, notBefore = Failures.retryAt(e, t.attempts, now())!!))
            else -> finish(t.copy(error = error), TurnStatus.FAILED, th)
        }
    }

    /**
     * A primary turn on an OpenAI model ("also run on") failed: a refused credential or one out of money (or the plan's
     * allowance) marks OpenAI so (the lanes stop too) and ends the turn, the primary's worker never waits for OpenAI;
     * the rest as any turn's.
     */
    private fun openaiFailed(t: TurnRecord, e: ApiError, error: String, th: ThreadState?) {
        when {
            e is ApiError.Auth -> {
                saveSettings { it.copy(openai = it.openai.copy(refused = true)) }
                finish(t.copy(error = error), TurnStatus.FAILED, th)
            }
            e is ApiError.LowCredit -> {
                val why = Failures.creditWhy(e, ProviderId.OPENAI)
                saveSettings { it.copy(openai = it.openai.copy(exhausted = why)) }
                finish(t.copy(error = "$why ($error)"), TurnStatus.FAILED, th)
            }
            Failures.retryAt(e, t.attempts, now()) != null -> replace(t.copy(status = TurnStatus.QUEUED, error = error, notBefore = Failures.retryAt(e, t.attempts, now())!!))
            else -> finish(t.copy(error = error), TurnStatus.FAILED, th)
        }
    }

    /** The turn is over: into its day's log; the thread gets its line, a restart when it's past the limit. */
    private fun finish(t0: TurnRecord, status: TurnStatus, th0: ThreadState? = store.thread()) {
        val t = t0.copy(status = status, endedAt = now())
        saveQueue(q.filterNot { it.id == t.id })
        store.dropTurnMessages(t.id)
        val day = t.eventIds.firstOrNull()?.let(::findEvent)?.let { ShadowStore.dayOf(it.at, zone()) } ?: dayNow()
        val l = log(day)
        saveLog(l.copy(turns = l.turns.filterNot { it.id == t.id } + t))
        var th = th0
        when (t.kind) {
            TurnKind.CLOSING -> if (th != null) {
                if (t.eventKind == "restart") {
                    val fresh = restarted(th, "The thread restarted")
                    val today = log(dayNow())
                    saveLog(today.copy(restarts = today.restarts + th.tokens))
                    store.saveThread(fresh)
                    thread = fresh
                } else {
                    archive(th)
                    onDayClosed()
                }
                // a lane's new day waits for the notebook this closing rewrote
                matrix.wakeAll()
            }
            TurnKind.DAY, TurnKind.STATELESS -> {
                val entry = DayThreads.line(t, t.endedAt ?: now(), zone())
                val current = store.thread()?.takeIf { it.day == dayNow() }
                if (current != null) {
                    val stateless = t.kind == TurnKind.STATELESS || current.model != t.model
                    th = current.copy(lines = (current.lines + entry).takeLast(60), pending = if (stateless) current.pending + entry else current.pending)
                    store.saveThread(th)
                    thread = th
                    if (!stateless && th.tokens > s.limit && q.none { it.kind == TurnKind.CLOSING }) {
                        saveQueue(q + TurnRecord(id = newId(), eventIds = emptyList(), kind = TurnKind.CLOSING, task = Task.CHAT, model = th.model, eventKind = "restart", queuedAt = now(), why = "the thread passed ${s.limit} tokens (${th.tokens})"))
                    }
                } else if (t.kind == TurnKind.STATELESS) {
                    // no thread today yet: the line waits in a thread made for it
                    val fresh = newThread(catalog().modelFor(Task.CHAT, s.preset, s.models), store.threadArchive()?.lines.orEmpty()).copy(lines = listOf(entry), pending = listOf(entry))
                    store.saveThread(fresh)
                    thread = fresh
                }
            }
            TurnKind.ALSO -> Unit
        }
        line("turn ${t.id} ${t.eventKind} on ${t.model}${t.effort?.let { " $it" } ?: ""}: $status${if (t.replyFromText) " (reply as text)" else ""}${t.error?.let { " ($it)" } ?: ""}")
    }

    /**
     * The day's last two or three exchanges, as text (a restart's first message: plan 5, "Compaction: our own"), with
     * what the learner was shown in reply (the real tutor's).
     */
    private fun lastExchanges(): List<String> {
        val l = log(dayNow())
        return DayThreads.exchanges(l.turns.filter { it.kind == TurnKind.DAY && it.status == TurnStatus.DONE }, ::findEvent, l.real)
    }

    // --- the panel's numbers and the export -----------------------------------------------------------------------

    /** Day [day]'s numbers (on the shadow's thread). */
    suspend fun stats(day: String = dayNow()): DayStats = withContext(io) { DayStats.of(log(day), nb.text.length) }

    /** The days with a log. */
    fun days(): List<String> = (store.days() + dayNow()).distinct().sorted()

    /** Day [day]'s log (the panel browses older days). */
    suspend fun dayLog(day: String): DayLog = withContext(io) { log(day) }

    /** The matrix's lanes' logs of day [day] (the panel browses older days). */
    suspend fun laneLogs(day: String): Map<String, DayLog> = withContext(io) { matrix.logs(day) }

    /**
     * Everything the shadow kept, as one JSON file in [dir] (the share sheet sends it): [exportText]'s.
     */
    suspend fun export(dir: File, app: String): File {
        val text = exportText(app)
        return withContext(io) {
            dir.mkdirs()
            File(dir, "lani-shadow-${dayNow()}.json").also { it.writeText(text) }
        }
    }

    /**
     * Everything the shadow kept, as JSON (`lani.shadow-export/v0`: the share sheet's file and the node's copy, app/DevUpload):
     * the events, turns, replies, tool calls, calls (with the raw usage kept) and numbers of every day, the notebook and
     * its versions, the thread, the queue, the settings. No key, no token: none is ever kept with them, and every text is
     * cleaned of the key, the paired node's token, [secrets] and anything shaped like one ([Redact.json]). No photo
     * either: a chat's photo the thread carried is a note of its size ([ShadowImages.omit]). [maxBytes]: the oldest days
     * are left out until it fits (their days in `days_left_out`).
     */
    suspend fun exportText(app: String, secrets: List<String> = emptyList(), maxBytes: Int? = null): String {
        val known = listOfNotNull(vault.load(), bridgeConfig()?.token) + openai?.secrets().orEmpty().filterNotNull() + secrets
        return withContext(io) {
            var days = store.days()
            var left = emptyList<String>()
            // a chat's photo sent to the API stays out: a note of its size in its place (ShadowImages.omit)
            fun render() = Redact.json(ShadowImages.omit(exportJson(app, days.map { log(it) }, left)), known).toString()
            var text = render()
            while (maxBytes != null && days.isNotEmpty() && text.toByteArray().size > maxBytes) {
                left = left + days.first()
                days = days.drop(1)
                text = render()
            }
            text
        }
    }

    private fun exportJson(app: String, days: List<DayLog>, left: List<String>): JsonObject = buildJsonObject {
        put("schema", "lani.shadow-export/v0")
        put("exported_at", Instant.ofEpochMilli(now()).toString())
        put("app", app)
        put("base_url", baseUrl())
        // OpenAI: which credential the calls use (never a credential itself, nor the account)
        put(
            "openai",
            buildJsonObject {
                put("available", openai != null)
                put("ready", JsonArray(openaiReady.sorted().map { JsonPrimitive(it) }))
                openaiBilling()?.let { put("use", it) }
            },
        )
        put("tools", JsonArray(toolNames.map { JsonPrimitive(it) }))
        put("prefix_chars", prefixChars)
        put("settings", tutorJson.encodeToJsonElement(ShadowSettings.serializer(), s))
        put("notebook", tutorJson.encodeToJsonElement(NotebookState.serializer(), nb))
        store.thread()?.let { put("thread", tutorJson.encodeToJsonElement(ThreadState.serializer(), it)) }
        put("queue", tutorJson.encodeToJsonElement(ListSerializer(TurnRecord.serializer()), q))
        put("matrix", matrix.json())
        if (left.isNotEmpty()) put("days_left_out", JsonArray(left.map { JsonPrimitive(it) }))
        put(
            "days",
            JsonArray(days.map { d ->
                buildJsonObject {
                    put("day", d.day)
                    put("stats", tutorJson.encodeToJsonElement(DayStats.serializer(), DayStats.of(d, nb.text.length)))
                    put("log", tutorJson.encodeToJsonElement(DayLog.serializer(), d))
                    // the matrix's lanes that day, each its numbers and its log (turns and calls with the lane's id)
                    val lanes = matrix.logs(d.day)
                    if (lanes.isNotEmpty()) put(
                        "lanes",
                        buildJsonObject {
                            lanes.forEach { (id, l) ->
                                put(id, buildJsonObject {
                                    put("stats", tutorJson.encodeToJsonElement(DayStats.serializer(), DayStats.of(l, nb.text.length)))
                                    put("log", tutorJson.encodeToJsonElement(DayLog.serializer(), l))
                                })
                            }
                        },
                    )
                }
            }),
        )
    }

    /** The tests': waits until the matrix's lanes are idle too. */
    internal suspend fun awaitMatrix(timeoutMs: Long = 20_000) {
        awaitIdle(timeoutMs)
        matrix.awaitIdle(timeoutMs)
        awaitIdle(timeoutMs)
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
        error("the shadow tutor didn't get idle: ${statusNow()}")
    }

}
