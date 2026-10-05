package si.lanisce.lani.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.lanisce.lani.MainActivity
import si.lanisce.lani.R
import si.lanisce.lani.l10n.LangSetting
import si.lanisce.lani.game.culture.CultureSetting
import si.lanisce.lani.widget.VillageWidget
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.l10n.inBase
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Runs every ~15 minutes while the app is closed: turns tutor events the app hasn't shown yet
 * into notifications, nudges in the evening when cards are due and nothing was practised today,
 * and refreshes the home-screen widget.
 */
class BackgroundCheck(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        LangSetting(applicationContext).apply() // notifications in the learner's languages, with the app closed
        si.lanisce.lani.l10n.LearnerSetting(applicationContext).apply() // and the content said to the learner of the profile
        CultureSetting(applicationContext).apply() // and their village's culture
        val prefs = Prefs(applicationContext)
        val config = prefs.config() ?: return Result.success()
        val foreground = withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        if (foreground) return Result.success() // the open app shows events itself

        val bridge = Bridge(config)
        return try {
            flushOutbox(bridge)
            val since = prefs.notifiedEventId()
            val events = bridge.backlog(since)
            // One event that can't be shown must not block the rest: the id below is only saved after the loop,
            // so a throwing notify would retry (and fail) the same events every period.
            if (since > 0) events.forEach { (_, ev) -> runCatching { notify(ev) } }
            events.maxOfOrNull { it.first }?.let { prefs.saveNotifiedEventId(it) }
            villageAlert(prefs, bridge)
            eveningReminder(prefs, bridge)
            widget(bridge)
            Result.success()
        } catch (e: Exception) {
            runCatching { VillageWidget.refresh(applicationContext) } // offline: redraw from the cache (time of day)
            Result.success() // offline: try again next period
        }
    }

    /** A tap opens what the notification is about ([DeepLink.of]). */
    private fun notify(ev: BridgeEvent) {
        val link = DeepLink.of(ev)
        when (ev) {
            // a check's or a question's feedback shows with the question in the app
            is BridgeEvent.Reply -> if (!ev.conversationId.startsWith("ex") && !ev.conversationId.startsWith(TownQuestions.CONVERSATION)) post(ev.hashCode(), "💬 ${inTarget("common.yourTutor")}", previewLine(ev.text, 140), link)
            is BridgeEvent.ModulePublished -> post(ev.id.hashCode(), "🎉 ${bi("backgroundCheck.newChallenge")}", ev.note ?: ev.title ?: ev.id, link)
            is BridgeEvent.PermissionRequest ->
                if (System.currentTimeMillis() - ev.at < 5 * 60_000) post(1, "🔐 ${inBase("backgroundCheck.claudeNeedsApproval")}", "${ev.tool}: ${ev.description}", link)
            is BridgeEvent.PackPublished -> post(ev.id.hashCode(), "📚 ${bi("homeScreen.newWords")}", ev.note ?: ev.title ?: ev.id, link)
            is BridgeEvent.ReadingPublished -> if (!ev.removed) post("reading:${ev.id}".hashCode(), "📖 ${bi("readings.newReading")}", ev.note ?: ev.title ?: ev.id, link)
            is BridgeEvent.GrammarPublished -> post("grammar:${ev.id}".hashCode(), "📖 ${bi("grammar.newPage")}", ev.note ?: ev.title ?: ev.id, link)
            is BridgeEvent.PartnerChallenge -> post(ev.id.hashCode(), "${ev.emoji} ${Family.fromLabel(ev.from, ev.fromSl)}", ev.text, link)
            is BridgeEvent.AppUpdate -> post(2, "⬆️ ${bi("backgroundCheck.updateVersion", "versionName" to ev.versionName)}", ev.notes ?: inBase("backgroundCheck.openToInstall"), link)
            // a newcomer's introduction: the bubble in the village says it (the arrival itself was news enough)
            is BridgeEvent.VoiceUpdated, is BridgeEvent.WordsAdded, is BridgeEvent.LexiconUpdated, is BridgeEvent.SentenceExplained, is BridgeEvent.ArrivalPublished -> Unit
            is BridgeEvent.TownGuest -> post(("guest" + ev.kind + ev.from).hashCode(), "🧳 ${TownGuests.title(ev)}", ev.from, link)
            is BridgeEvent.TownQuestion -> post(("question" + ev.kind + ev.key).hashCode(), TownQuestions.title(ev), ev.text, link)
            is BridgeEvent.ScenePublished -> when {
                ev.removed -> Unit
                ev.variant -> if (ev.note != null) post(("variant:" + ev.id + ev.note).hashCode(), bi("backgroundCheck.newScene", "evEmoji" to (ev.emoji ?: "💬")), ev.note, link)
                ev.story -> post("story:${ev.title}".hashCode(), bi("backgroundCheck.newStory", "evEmoji" to (ev.emoji ?: "📖")), ev.note ?: ev.title ?: ev.id, link)
                else -> post(ev.id.hashCode(), bi("backgroundCheck.newScene", "evEmoji" to (ev.emoji ?: "📍")), ev.note ?: ev.title ?: ev.id, link)
            }
        }
    }

    /** Sends writes queued while offline; a write the node rejects is reported once as a notification. */
    private suspend fun flushOutbox(bridge: Bridge) {
        val r = Outbox(applicationContext).flush { bridge.deliver(it) }
        r.dropped.firstOrNull()?.let { (e, why) -> post(5, "⚠️ ${bi("backgroundCheck.notSaved", "eLabel" to e.label)}", why) }
    }

    /** Posts once per village event; pushes unsent village changes, then refreshes the cached village. */
    private suspend fun villageAlert(prefs: Prefs, bridge: Bridge) {
        val store = GameStore(applicationContext)
        var cached = store.read()
        cached?.takeIf { it.dirty }?.let { c ->
            // On a conflict the node's copy wins, as in the app.
            runCatching { bridge.putGame(c.rev, c.state) }.getOrNull()?.let { r -> cached = GameSync.afterPut(c.state, c.state, r).also(store::write) }
        }
        if (cached == null || !cached.dirty) {
            runCatching { bridge.game() }.getOrNull()?.let { r ->
                cached = GameCache(r.rev, r.state).also(store::write)
            }
        }
        val now = System.currentTimeMillis()
        val e = VillageAlert.pending(cached?.state, prefs.gameEventNotified(), now) ?: return
        post(4, VillageAlert.title(e.kind), VillageAlert.text(e, now), DeepLink.Village)
        prefs.saveGameEventNotified(e.id)
    }

    /** Keeps the home-screen widget fresh: streak and due cards from the node, the village from the cache. */
    private suspend fun widget(bridge: Bridge) {
        val ctx = applicationContext
        if (!VillageWidget.installed(ctx)) return
        runCatching { bridge.dashboard() }.getOrNull()?.let { VillageWidget.saveStats(ctx, it.streak, it.dueCards.size) }
        withContext(Dispatchers.IO) { VillageWidget.refresh(ctx) }
    }

    private suspend fun eveningReminder(prefs: Prefs, bridge: Bridge) {
        val today = LocalDate.now().toString()
        if (LocalTime.now().hour !in 17..21 || prefs.lastReminderDay() == today) return
        val d = bridge.dashboard()
        if (d.dueCards.isNotEmpty() && d.daysSinceLastSession >= 1) {
            post(3, "🔥 ${inTarget("backgroundCheck.cardsWaiting", "n" to d.dueCards.size)}", bi("backgroundCheck.fiveMinutesKeepStreak"))
            prefs.saveLastReminderDay(today)
        }
    }

    private fun post(id: Int, title: String, text: String, link: DeepLink? = null) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        link?.let { intent.putExtra(DeepLink.EXTRA, DeepLink.encode(it)) }
        // One request code per notification: with a shared one, FLAG_UPDATE_CURRENT would give every
        // notification the last one's link.
        val open = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome) // a silhouette: the bubble with Č cut out
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    companion object {
        private const val CHANNEL = "tutor"

        private fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Tutor", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<BackgroundCheck>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("lani-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
