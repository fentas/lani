package si.lanisce.lani.data

import android.content.Context
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
import si.lanisce.lani.l10n.LangSetting
import si.lanisce.lani.game.culture.CultureSetting
import si.lanisce.lani.widget.VillageWidget
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Runs every ~15 minutes while the app is closed: turns tutor events the app hasn't shown yet
 * into notifications, nudges in the evening when cards are due and nothing was practised today,
 * and refreshes the home-screen widget. What each says and its channel: [Notices]; posting: [Notifier]
 * (companion/README.md, "Notifications").
 */
class BackgroundCheck(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        LangSetting(applicationContext).apply() // notifications in the learner's languages, with the app closed
        si.lanisce.lani.l10n.LearnerSetting(applicationContext).apply() // and the content said to the learner of the profile
        CultureSetting(applicationContext).apply() // and their village's culture
        val prefs = Prefs(applicationContext)
        // Lani on this phone: its bridge boots in this process too, when the app's screens didn't (hosted/HostedRuntime)
        si.lanisce.lani.hosted.HostedRuntime.init(applicationContext)
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
            val child = child(prefs)
            // One event that can't be shown must not block the rest: the id below is only saved after the loop,
            // so a throwing notify would retry (and fail) the same events every period.
            if (since > 0) events.forEach { (_, ev) -> runCatching { notify(ev, child) } }
            events.maxOfOrNull { it.first }?.let { prefs.saveNotifiedEventId(it) }
            villageAlert(prefs, bridge, child)
            eveningReminder(prefs, bridge, child)
            widget(bridge)
            Result.success()
        } catch (e: Exception) {
            runCatching { VillageWidget.refresh(applicationContext) } // offline: redraw from the cache (time of day)
            Result.success() // offline: try again next period
        }
    }

    /** A tap opens what the notification is about ([DeepLink.of]); what it says and where it goes: [Notices.of]. */
    private fun notify(ev: BridgeEvent, child: Boolean) {
        Notices.of(ev, child)?.let(::post)
    }

    /** Sends writes queued while offline; a write the node rejects is reported once as a notification. */
    private suspend fun flushOutbox(bridge: Bridge) {
        val r = Outbox(applicationContext).flush { bridge.deliver(it) }
        r.dropped.firstOrNull()?.let { (e, why) -> post(Notices.notSaved(e.label, why)) }
    }

    /**
     * Posts once per village event (a [child]'s kindly); pushes unsent village changes, then refreshes the cached village.
     * The event over (fought in the app, its time up), its notification goes.
     */
    private suspend fun villageAlert(prefs: Prefs, bridge: Bridge, child: Boolean) {
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
        if (VillageAlert.calm(cached?.state, now)) Notifier.cancel(applicationContext, listOf(Seen(NotifyChannels.VILLAGE)))
        val e = VillageAlert.pending(cached?.state, prefs.gameEventNotified(), now) ?: return
        post(Notices.villageAlert(e, now, child))
        prefs.saveGameEventNotified(e.id)
    }

    /** Keeps the home-screen widget fresh: streak and due cards from the node, the village from the cache. */
    private suspend fun widget(bridge: Bridge) {
        val ctx = applicationContext
        if (!VillageWidget.installed(ctx)) return
        runCatching { bridge.dashboard() }.getOrNull()?.let { VillageWidget.saveStats(ctx, it.streak, it.dueCards.size) }
        withContext(Dispatchers.IO) { VillageWidget.refresh(ctx) }
    }

    /**
     * In the evening, cards due and nothing practised today: one nudge. Off on a child's profile unless a parent turned it
     * on ("🔔 Obvestila · Notifications" in the settings), and then without a number or a streak.
     */
    private suspend fun eveningReminder(prefs: Prefs, bridge: Bridge, child: Boolean) {
        if (!NotifyChannels.reminderOn(prefs.eveningReminder(), child)) return
        val today = LocalDate.now().toString()
        if (LocalTime.now().hour !in 17..21 || prefs.lastReminderDay() == today) return
        val d = bridge.dashboard()
        if (d.dueCards.isNotEmpty() && d.daysSinceLastSession >= 1) {
            post(Notices.reminder(d.dueCards.size, child))
            prefs.saveLastReminderDay(today)
        }
    }

    /**
     * The learner of this phone is a child: as the app last said ([Prefs.learnerChild]: a town's own row tells it too), or
     * the pairing's, or the hosted onboarding's (an app not opened since this version).
     */
    private suspend fun child(prefs: Prefs): Boolean = prefs.learnerChild() == true || prefs.pairing()?.child == true ||
        java.io.File(si.lanisce.lani.phone.AppBridge.home(applicationContext), si.lanisce.lani.hosted.HostedRuntime.CHILD_FILE).isFile

    private fun post(n: Notice) = Notifier.post(applicationContext, n)

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<BackgroundCheck>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("lani-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
