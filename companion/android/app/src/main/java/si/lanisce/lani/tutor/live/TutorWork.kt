package si.lanisce.lani.tutor.live

import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.hosted.HostedRuntime
import si.lanisce.lani.hosted.HostedWork
import si.lanisce.lani.phone.PhoneTransport
import si.lanisce.lani.tutor.DataStoreSecretStore
import si.lanisce.lani.tutor.KeyVault
import si.lanisce.lani.tutor.KeystoreCipher
import si.lanisce.lani.tutor.GeminiCredentials
import si.lanisce.lani.tutor.OpenAICredentials
import si.lanisce.lani.tutor.OpenRouterCredentials
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The in-app tutors' parts that live as long as the app's process (plan 5, "Background work on Android": turns run in
 * a scope that outlives the screens): the credentials the shadow and the live tutor share (one ChatGPT sign-in, so
 * its token refreshes one at a time), and the live tutor itself, which the view model and the WorkManager check
 * ([TutorWork]) both use.
 */
object TutorRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var vault: KeyVault? = null
    @Volatile private var openai: OpenAICredentials? = null
    @Volatile private var gemini: GeminiCredentials? = null
    @Volatile private var openrouter: OpenRouterCredentials? = null
    @Volatile private var live: LiveTutor? = null

    /** The Claude key's vault (the Keystore's key, the DataStore's ciphertext). */
    fun vault(app: Context): KeyVault = vault ?: synchronized(this) { vault ?: KeyVault(KeystoreCipher(), DataStoreSecretStore(app.applicationContext)).also { vault = it } }

    /** The OpenAI key and the ChatGPT sign-in. */
    fun openai(app: Context): OpenAICredentials = openai ?: synchronized(this) { openai ?: OpenAICredentials.android(app.applicationContext).also { openai = it } }

    /** The Gemini key and its tier (one pacer for the key: the free tier's quota is the project's). */
    fun gemini(app: Context): GeminiCredentials = gemini ?: synchronized(this) { gemini ?: GeminiCredentials.android(app.applicationContext).also { gemini = it } }

    /** The OpenRouter sign-in's key. */
    fun openrouter(app: Context): OpenRouterCredentials = openrouter ?: synchronized(this) { openrouter ?: OpenRouterCredentials.android(app.applicationContext).also { openrouter = it } }

    /** The client to the phone's own bridge (the hosted mode): every call in-process ([PhoneLink]), booting it when it isn't up. */
    private fun phoneClient(app: Context) = PhoneLink.client {
        HostedRuntime.init(app)
        HostedRuntime.ensure() ?: throw java.io.IOException("Lani on this phone isn't running")
    }

    /** The live tutor of this process (files/tutor-live). */
    fun live(app: Application, debug: Boolean, texts: (String, Map<String, Any?>) -> String = { k, _ -> k }): LiveTutor = live ?: synchronized(this) {
        live ?: LiveTutor(
            scope,
            LiveStore(File(app.filesDir, "tutor-live")),
            vault(app),
            openai(app),
            bridgeConfig = { Prefs(app).config() },
            debug = debug,
            // Lani on this phone (no node): the tutor session is the phone's own bridge, in-process (hosted/HostedRuntime)
            bridgeFor = { c -> if (PhoneTransport.isPhone(c.baseUrl)) LiveBridge(c, phoneClient(app)) else LiveBridge(c) },
            device = android.os.Build.MODEL ?: "Android",
            // its calls, turns and errors (never a key: tutor/KeyVault Redact)
            logLine = { android.util.Log.i("LiveTutor", it) },
            texts = texts,
            // the learner's GitHub sign-in, as git sync keeps it: report_problem posts to Lani's discussions with it
            githubToken = { runCatching { si.lanisce.lani.sync.KeystoreBox(app.applicationContext).get(si.lanisce.lani.sync.SecretBox.GIT_TOKEN) }.getOrNull() },
            nightOk = { StudioWork.nightOk(app) },
            gemini = gemini(app),
            openrouter = openrouter(app),
        ).also { t ->
            t.photo = { id -> File(app.filesDir, "chat-photos/$id.jpg").takeIf { it.isFile } }
            t.schedule = { on, now -> TutorWork.schedule(app, on, now) }
            t.studioSchedule = { wake -> StudioWork.schedule(app, wake) }
            live = t
        }
    }
}

/**
 * The live tutor's check while the app isn't up (plan 5, "Background work on Android"): every ~15 minutes while it is
 * on, and once at once when the app left with a turn waiting. It reads the bridge's tutor inbox once and runs what
 * waits for an answer (a chat sent before the app closed, the rhythm's morning), for a few minutes at most; the digest
 * waits for the app. A reply reaches the learner as a notification, through the app's own background check.
 */
class TutorWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val foreground = withContext(Dispatchers.Main) { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        if (foreground) return Result.success() // the open app polls itself
        val live = TutorRuntime.live(applicationContext as Application, si.lanisce.lani.BuildConfig.QA, { k, a -> si.lanisce.lani.l10n.bi(k, *a.toList().toTypedArray()) })
        // Lani on this phone: its bridge boots in this process and sends what the rhythm has due first (the morning's
        // plan, the evening, the week), so this check answers it now (hosted/HostedWork)
        HostedRuntime.init(applicationContext)
        if (HostedRuntime.active(applicationContext)) runCatching { HostedWork.rhythmNow(applicationContext) }
        return try {
            live.backgroundCheck()
            Result.success()
        } catch (e: Exception) {
            Result.success() // offline: the next period tries again
        }
    }

    companion object {
        private const val PERIODIC = "lani-tutor-live"
        private const val ONCE = "lani-tutor-live-now"

        /** The periodic check on ([on]) or off; [now]: a run right away too (a turn waits as the app leaves). */
        fun schedule(ctx: Context, on: Boolean, now: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            val net = Constraints(requiredNetworkType = NetworkType.CONNECTED)
            if (!on) {
                wm.cancelUniqueWork(PERIODIC)
                wm.cancelUniqueWork(ONCE)
                return
            }
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<TutorWork>(15, TimeUnit.MINUTES).setConstraints(net).build())
            if (now) wm.enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<TutorWork>().setConstraints(net).build())
        }
    }
}
