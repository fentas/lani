package si.lanisce.lani.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.ComponentEnabledSetting
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.MainActivity
import si.lanisce.lani.game.culture.CultureSetting
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** The icon's choices and its day: SharedPreferences "app_icon". */
class AppIconPrefs(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("app_icon", Context.MODE_PRIVATE)

    /** The learner's choice, or null: the default for the launcher ([AppIcons.defaultMode]). */
    var mode: AppIcons.Mode?
        get() = AppIcons.Mode.of(store.getString(MODE, null))
        set(v) = store.edit().putString(MODE, v?.id).putBoolean(FORCE, true).apply()

    /** The day the icon was last decided (ISO). */
    val decidedOn: String? get() = store.getString(DECIDED, null)

    /** The alias last shown. */
    val shown: String? get() = store.getString(SHOWN, null)

    /** The next background moment decides even on a day already decided (a new choice, the developer's button). */
    var force: Boolean
        get() = store.getBoolean(FORCE, false)
        set(v) = store.edit().putBoolean(FORCE, v).apply()

    /** The developer's "Try the next icon": this alias instead of the day's picture, until cleared. */
    var test: String?
        get() = store.getString(TEST, null)
        set(v) = store.edit().putString(TEST, v).putBoolean(FORCE, true).apply()

    fun decided(today: LocalDate, alias: String?) =
        store.edit().putString(DECIDED, today.toString()).putString(SHOWN, alias).putBoolean(FORCE, false).apply()

    private companion object {
        const val MODE = "mode"
        const val DECIDED = "decided_on"
        const val SHOWN = "shown"
        const val FORCE = "force"
        const val TEST = "test"
    }
}

/**
 * The launcher's entries: the aliases of MainActivity ([AppIcons.all]). [show] enables the new one first, then turns the
 * others off, in one atomic call on Android 13+ (there is never a moment without a launcher entry). The aliases disabled
 * in the manifest go back to DEFAULT, which the system doesn't treat as a disabled component, so a task started from one
 * isn't closed; only [AppIcons.DEFAULT], enabled in the manifest, is set to DISABLED.
 */
class AppIconSwitch(context: Context) {
    private val ctx = context.applicationContext
    private val pm = ctx.packageManager
    private val pkg = MainActivity::class.java.`package`!!.name

    private fun component(alias: String) = ComponentName(ctx.packageName, pkg + alias)

    /** The aliases the launcher sees now. */
    fun enabled(): List<String> = AppIcons.all.map { it.alias }.filter { alias ->
        when (pm.getComponentEnabledSetting(component(alias))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> alias == AppIcons.DEFAULT
            else -> false
        }
    }

    fun show(alias: String) {
        val off = enabled().filter { it != alias }
        val changes = buildList {
            add(alias to PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            for (o in off) add(o to if (o == AppIcons.DEFAULT) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            pm.setComponentEnabledSettings(changes.map { (a, state) -> ComponentEnabledSetting(component(a), state, PackageManager.DONT_KILL_APP) })
        } else {
            for ((a, state) in changes) pm.setComponentEnabledSetting(component(a), state, PackageManager.DONT_KILL_APP)
        }
        if (BuildConfig.QA) Log.i(TAG, "icon: $alias (off: ${off.joinToString()})")
    }

    /** Safe in the foreground: when no launcher entry is enabled, enable [alias]; it never turns one off. */
    fun ensureOne(alias: String) {
        if (enabled().isEmpty()) pm.setComponentEnabledSetting(component(alias), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    /** The default launcher's package, for [AppIcons.defaultMode]. */
    fun homePackage(): String? = runCatching {
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    companion object {
        const val TAG = "AppIcon"
    }
}

/**
 * When the icon changes: at the day's first background moment, 15 minutes after the app went to the background (a few
 * seconds when the learner just chose, or the developer's button asked), or by the early-morning job; never while the app
 * is in the foreground. It reads only what is saved on the phone: the village ([GameStore]), its culture, the calendar.
 */
class AppIconWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val foreground = withContext(Dispatchers.Main) { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        AppIconWork.run(ctx, foreground)
        return Result.success()
    }
}

object AppIconWork {
    private const val LEAVE = "lani-icon"
    private const val DAILY = "lani-icon-daily"
    private var watching = false

    /** At the app's start: the repair, the early-morning job, and the watch on the app going to the background. */
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        val prefs = AppIconPrefs(app)
        runCatching { AppIconSwitch(app).ensureOne(prefs.shown ?: AppIcons.DEFAULT) }
        val morning = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atTime(LocalTime.of(4, 30))).toMinutes()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            DAILY, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AppIconWorker>(24, TimeUnit.HOURS).setInitialDelay(morning, TimeUnit.MINUTES).build(),
        )
        if (watching) return
        watching = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                WorkManager.getInstance(app).cancelUniqueWork(LEAVE)
            }

            override fun onStop(owner: LifecycleOwner) = leave(app)
        })
    }

    /** The app went to the background: decide in 15 minutes (a few seconds when the learner asked for a change). */
    fun leave(ctx: Context, delaySeconds: Long? = null) {
        val app = ctx.applicationContext
        val delay = delaySeconds ?: if (AppIconPrefs(app).force) 5 else 15 * 60L
        WorkManager.getInstance(app).enqueueUniqueWork(
            LEAVE, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AppIconWorker>().setInitialDelay(delay, TimeUnit.SECONDS).build(),
        )
    }

    /** The decision, and the switch it asks for. */
    fun run(ctx: Context, foreground: Boolean, today: LocalDate = LocalDate.now()) {
        val prefs = AppIconPrefs(ctx)
        val sw = AppIconSwitch(ctx)
        val culture = CultureSetting(ctx).apply()
        val state = runCatching { GameStore(ctx).read()?.state }.getOrNull()
        val mode = prefs.mode ?: AppIcons.defaultMode(sw.homePackage())
        val wanted = prefs.test ?: AppIcons.choose(state, culture, today, mode, runCatching { AppIcons.festivalOn(state, today) }.getOrDefault(false))?.alias
        val enabled = sw.enabled()
        when (AppIcons.decide(foreground, today, prefs.decidedOn, prefs.force, enabled, wanted)) {
            AppIcons.Action.WAIT, AppIcons.Action.NONE -> Unit
            AppIcons.Action.REPAIR -> sw.ensureOne(prefs.shown ?: AppIcons.DEFAULT)
            AppIcons.Action.DECIDED -> prefs.decided(today, wanted)
            AppIcons.Action.SWITCH -> {
                sw.show(wanted!!)
                prefs.decided(today, wanted)
            }
        }
    }
}

/** After an update: the icon's repair and the day's picture at the next background moment (the aliases kept their state). */
class AppIconUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = AppIconPrefs(context)
        runCatching { AppIconSwitch(context).ensureOne(prefs.shown ?: AppIcons.DEFAULT) }
        prefs.force = true
        AppIconWork.leave(context, delaySeconds = 60)
    }
}
