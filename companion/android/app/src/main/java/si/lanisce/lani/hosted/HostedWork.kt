package si.lanisce.lani.hosted

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The tutor's daily rhythm on the phone (companion/bridge/src/rhythm.ts, read from `<data>/app/rhythm.json`): when the
 * morning plan, the evening recap and the weekly review are due, as the bridge decides it, so the app can wake its
 * process for them. Each routine runs once a day and catches up within its window (morning 4 h, evening 3 h, weekly 24 h).
 */
data class Rhythm(
    val enabled: Boolean = true,
    val morning: LocalTime? = LocalTime.of(7, 30),
    val evening: LocalTime? = LocalTime.of(20, 30),
    /** 0 = Sunday … 6 = Saturday; null: off. */
    val weeklyDay: Int? = 0,
    val weekly: LocalTime = LocalTime.of(19, 0),
    /** The local day each routine last ran ("YYYY-MM-DD"). */
    val last: Map<String, String> = emptyMap(),
) {
    /** The moments to come, soonest first: (routine, when). */
    fun upcoming(now: ZonedDateTime): List<Pair<String, ZonedDateTime>> {
        if (!enabled) return emptyList()
        val today = now.toLocalDate()
        val out = mutableListOf<Pair<String, ZonedDateTime>>()
        fun daily(name: String, t: LocalTime?) {
            if (t == null) return
            var day = today
            if (last[name] == today.toString() || !LocalDateTime.of(today, t).atZone(now.zone).isAfter(now)) day = today.plusDays(1)
            out += name to LocalDateTime.of(day, t).atZone(now.zone)
        }
        daily("morning", morning)
        daily("evening", evening)
        weeklyDay?.let { wd ->
            val dow = if (wd == 0) DayOfWeek.SUNDAY else DayOfWeek.of(wd)
            var day = today
            while (day.dayOfWeek != dow || (day == today && (last["weekly"] == today.toString() || !LocalDateTime.of(today, weekly).atZone(now.zone).isAfter(now)))) day = day.plusDays(1)
            out += "weekly" to LocalDateTime.of(day, weekly).atZone(now.zone)
        }
        return out.sortedBy { it.second }
    }

    /** What is due now (the bridge's dueRoutines): not run today, its time passed, within its window. */
    fun due(now: ZonedDateTime): List<String> {
        if (!enabled) return emptyList()
        val today = now.toLocalDate()
        val times = listOf("morning" to morning, "evening" to evening, "weekly" to weekly.takeIf { weeklyDay == now.dayOfWeek.value % 7 })
        return times.mapNotNull { (r, t) ->
            if (t == null || last[r] == today.toString()) return@mapNotNull null
            val late = java.time.Duration.between(LocalDateTime.of(today, t).atZone(now.zone), now).toMinutes() / 60.0
            r.takeIf { late >= 0 && late <= (CATCH_UP[r] ?: 0) }
        }
    }

    companion object {
        val CATCH_UP = mapOf("morning" to 4, "evening" to 3, "weekly" to 24)

        /** The bridge's rhythm.json (its defaults when it's missing or can't be read, as RhythmStore reads it). */
        fun read(file: File): Rhythm {
            val o = runCatching { Json.parseToJsonElement(file.readText()) as JsonObject }.getOrNull() ?: return Rhythm()
            val s = o["settings"] as? JsonObject
            fun time(key: String, default: LocalTime?): LocalTime? {
                val v = s?.get(key) ?: return default
                if (v !is JsonPrimitive || v.contentOrNull == null) return null
                return runCatching { LocalTime.parse(v.content) }.getOrDefault(default)
            }
            val last = (o["last"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
            return Rhythm(
                enabled = s?.get("enabled")?.jsonPrimitive?.booleanOrNull ?: true,
                morning = time("morning", LocalTime.of(7, 30)),
                evening = time("evening", LocalTime.of(20, 30)),
                weeklyDay = if (s?.containsKey("weekly_day") == true) s["weekly_day"]?.jsonPrimitive?.intOrNull else 0,
                weekly = time("weekly", LocalTime.of(19, 0)) ?: LocalTime.of(19, 0),
                last = last,
            )
        }
    }
}

/**
 * The hosted mode's background work (plan 5, "Background work on Android"; docs/plans/05-built/hosted-mode.md): one-time
 * WorkManager work for the rhythm's next moment (the morning, the evening, the week), which wakes the app's process,
 * boots the phone's bridge (its rhythm's timers run while the process lives), waits until the bridge has sent what is
 * due (the channel's daily_morning, daily_evening, daily_weekly), and schedules the next moment. The timing isn't exact,
 * which is fine: the bridge catches a moment up within its window.
 */
class HostedWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        HostedRuntime.init(applicationContext)
        if (!HostedRuntime.active(applicationContext)) return Result.success() // a node again: nothing to wake
        val file = File(HostedRuntime.dataDir(applicationContext), "app/rhythm.json")
        try {
            HostedRuntime.ensure()
            val now = ZonedDateTime.now()
            val due = Rhythm.read(file).due(now)
            // the bridge's rhythm looks 15 s after its boot and then every minute: wait until it marked what is due as run
            val end = System.currentTimeMillis() + WAIT_MS
            while (due.isNotEmpty() && System.currentTimeMillis() < end) {
                val r = Rhythm.read(file)
                if (due.all { r.last[it] == now.toLocalDate().toString() }) break
                delay(2_000)
            }
            HostedRuntime.say("rhythm: woke for ${due.ifEmpty { listOf("nothing due") }.joinToString()}")
            // HOOK(tutor-live): the live tutor's inbox runs here too, so the morning's plan is answered now, not at
            // its next 15-minute check
            for (wake in onWake) runCatching { wake(applicationContext) }
        } catch (e: Exception) {
            HostedRuntime.say("rhythm: ${e.message}")
        } finally {
            schedule(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "lani-hosted-rhythm"
        /** How long the job waits for the bridge's rhythm to send what is due. */
        const val WAIT_MS = 90_000L
        /** After the moment itself: the bridge looks every minute (15 s after a boot). */
        const val AFTER_MS = 30_000L

        /** Run after the rhythm woke the bridge (the live tutor's inbox, once it is merged). */
        val onWake = mutableListOf<suspend (Context) -> Unit>()

        /** The next moment's work, replacing any planned before (the rhythm's settings may have changed). */
        fun schedule(ctx: Context, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())) {
            val rhythm = Rhythm.read(File(HostedRuntime.dataDir(ctx), "app/rhythm.json"))
            val next = rhythm.upcoming(now).firstOrNull()
            val wm = WorkManager.getInstance(ctx)
            if (next == null) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val delayMs = java.time.Duration.between(now, next.second).toMillis().coerceAtLeast(0) + AFTER_MS
            val req = OneTimeWorkRequestBuilder<HostedWork>().setInitialDelay(delayMs, TimeUnit.MILLISECONDS).build()
            wm.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, req)
            HostedRuntime.say("rhythm: next ${next.first} at ${next.second.toLocalDateTime()} (in ${delayMs / 60_000} min)")
        }

        /** No more waking (the learner went back to a node). */
        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
    }
}
