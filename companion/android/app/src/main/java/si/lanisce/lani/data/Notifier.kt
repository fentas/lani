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
import si.lanisce.lani.MainActivity
import si.lanisce.lani.R

/**
 * Android's side of the notifications (companion/README.md, "Notifications"; the decisions are [NotifyChannels]'): the
 * channels made (and the old one let go), a [Notice] posted, what the learner saw taken away.
 */
object Notifier {
    /** The notifications' id under their tag: one notification per tag. */
    private const val ID = 0

    /**
     * Makes the channels, named in the learner's own pair (called again when the pair changes: their names follow, nothing
     * else of a made channel changes), and lets the old "tutor" channel go once they exist, its settings carried over
     * ([NotifyChannels.specs]). Idempotent: at the app's start, on a new pair, before every post.
     */
    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            val old = nm.getNotificationChannel(NotifyChannels.OLD)
            for (s in NotifyChannels.specs(old?.let { NotifyChannels.Old(it.importance, it.canShowBadge()) })) {
                val made = nm.getNotificationChannel(s.channel.id) != null
                val ch = NotificationChannel(s.channel.id, NotifyChannels.name(s.channel), s.importance).apply {
                    setShowBadge(s.badge)
                    // the old channel's sound and vibration as the learner left them, for the ones made from it
                    if (old != null && !made) {
                        setSound(old.sound, old.audioAttributes)
                        enableVibration(old.shouldVibrate())
                        old.vibrationPattern?.let { vibrationPattern = it }
                        lockscreenVisibility = old.lockscreenVisibility
                    }
                }
                nm.createNotificationChannel(ch)
            }
            if (NotifyChannels.deleteOld(old != null, nm.notificationChannels.map { it.id }.toSet())) nm.deleteNotificationChannel(NotifyChannels.OLD)
        }
        ensureRoadChannel(ctx)
    }

    /**
     * The car's "getting ready" channel ([RoadChannel]): made without a dot, named in the learner's own pair, the old
     * "road-prep" channel's importance and lock screen carried over and the old one let go. Idempotent: with the others,
     * and before the road's service posts.
     */
    fun ensureRoadChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            val old = nm.getNotificationChannel(RoadChannel.OLD)
            val made = nm.getNotificationChannel(RoadChannel.ID) != null
            val ch = NotificationChannel(RoadChannel.ID, RoadChannel.name(), RoadChannel.importance(old?.let { NotifyChannels.Old(it.importance, it.canShowBadge()) })).apply {
                setShowBadge(RoadChannel.BADGE)
                setSound(null, null)
                enableVibration(false)
                if (old != null && !made) lockscreenVisibility = old.lockscreenVisibility
            }
            nm.createNotificationChannel(ch)
            if (RoadChannel.deleteOld(old != null, nm.getNotificationChannel(RoadChannel.ID) != null)) nm.deleteNotificationChannel(RoadChannel.OLD)
        }
    }

    /** Posts [n] (allowed to: Android 13's permission given); a tap opens what it is about ([Notice.link]) and takes it away. */
    fun post(ctx: Context, n: Notice) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(ctx)
        n.replaces?.let { cancel(ctx, listOf(it)) }
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        n.link?.let { intent.putExtra(DeepLink.EXTRA, DeepLink.encode(it)) }
        // One request code per notification: with a shared one, FLAG_UPDATE_CURRENT would give every notification the last
        // one's link.
        val open = PendingIntent.getActivity(ctx, n.tag.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val built = NotificationCompat.Builder(ctx, n.channel.id)
            .setSmallIcon(R.drawable.ic_notify) // Lani's L, white on transparent
            .setContentTitle(n.title)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            // One UI shows a count on the icon: one per thing waiting, never the cards due
            .setNumber(1)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(n.tag, ID, built) }
    }

    /** Takes away the notifications [seen] matches (the learner saw their thing in the app): the launcher's dot goes with them. */
    fun cancel(ctx: Context, seen: List<Seen>) {
        if (seen.isEmpty()) return
        cancelIf(ctx) { tag -> seen.any { it.matches(tag) } }
    }

    /** Takes away the app's notifications whose tag [gone] says. */
    fun cancelIf(ctx: Context, gone: (String?) -> Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.activeNotifications.filter { gone(it.tag) }.forEach { nm.cancel(it.tag, it.id) } }
    }
}
