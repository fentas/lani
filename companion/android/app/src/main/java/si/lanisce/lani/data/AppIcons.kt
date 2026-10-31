package si.lanisce.lani.data

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.AgeUps
import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The launcher's icon is the village's picture of the day (docs/plans/05-built/app-icon.md): its age (the fire, the camp,
 * the hamlet, the village, the market town, the town), its culture from the hamlet on, green or under snow, and at most
 * one sign of what's happening (a new age, a festival, something built, someone new). Each picture is an activity alias
 * of MainActivity in the manifest, generated with its drawables by companion/android/tools/app_icon.py build; only
 * [DEFAULT] is enabled there, and [AppIconSwitch] enables another while the app is in the background, at most once a
 * day ([AppIconWork]). Pure: what to show, from the saved state and the calendar.
 */
object AppIcons {
    /** A sign of what's happening, by priority: the first that holds shows ([choose]). */
    enum class Sign { NEWAGE, FESTIVAL, BUILT, NEWCOMER }

    /** One picture: one alias. */
    data class Look(val age: Age, val culture: String?, val sign: Sign?, val snow: Boolean) {
        /** ".icon.Ogenj", ".icon.FriuliZaselekSnow", ".icon.PrimorskaVasFestival": the generator's alias_name. */
        val alias: String
            get() = ".icon." + (culture?.cap() ?: "") + age.name.lowercase().cap() + (sign?.name?.lowercase()?.cap() ?: "") +
                if (snow) "Snow" else ""
    }

    /** How the icon changes: the setting "🎨 Ikona · App icon". */
    enum class Mode(val id: String) {
        /** The age, the season, and the day's sign. */
        HAPPENING("happening"),
        /** The age, snow and the December lights. */
        AGE_SEASON("age_season"),
        /** Never switches. */
        KEEP("keep");

        companion object {
            fun of(id: String?): Mode? = entries.firstOrNull { it.id == id }
        }
    }

    /** The cultures with a look of their own from the hamlet on (the camp's two ages are every culture's). */
    val CULTURES = listOf("primorska", "friuli", "kaernten", "lakeland")

    /** The alias the manifest enables: the fire, as every village starts (and no culture yet before setup). */
    const val DEFAULT = ".icon.Ogenj"

    /** Every alias, in the manifest's order (the generator's icon_looks). */
    val all: List<Look> = buildList {
        for (age in Age.entries) {
            val cultures: List<String?> = if (age <= Age.TABOR) listOf(null) else CULTURES
            val signs: List<Sign?> = listOf(null) + if (age == Age.OGENJ) listOf(Sign.FESTIVAL, Sign.BUILT)
            else listOf(Sign.FESTIVAL, Sign.NEWAGE, Sign.BUILT, Sign.NEWCOMER)
            for (c in cultures) for (s in signs) for (snow in listOf(false, true)) add(Look(age, c, s, snow))
        }
    }

    /**
     * Launchers known to keep the icon in place when the alias changes: Launcher3's model points a home-screen item
     * whose activity went away at the package's enabled launcher activity (PackageUpdatedTask). Pixel's and AOSP's.
     * Every other launcher (One UI, MIUI, Nothing's, unknown) starts with [Mode.AGE_SEASON] until it is tested.
     */
    val KEEPS_IN_PLACE = setOf("com.google.android.apps.nexuslauncher", "com.android.launcher3", "com.android.launcher")

    fun defaultMode(homePackage: String?): Mode = if (homePackage in KEEPS_IN_PLACE) Mode.HAPPENING else Mode.AGE_SEASON

    /** December to February: the hill, the roofs and the spruce under snow. */
    fun snow(today: LocalDate): Boolean = today.monthValue in setOf(12, 1, 2)

    /** The December lights: the festival's look, on snow, from December 20 to January 6. */
    fun lights(today: LocalDate): Boolean =
        (today.monthValue == 12 && today.dayOfMonth >= 20) || (today.monthValue == 1 && today.dayOfMonth <= 6)

    /**
     * The picture for [today]: null with [Mode.KEEP]. [festival] says whether the village keeps a festival today (its
     * day, or a grace day while it isn't celebrated, or the village's own festival event: [festivalOn]).
     */
    fun choose(s: GameState?, culture: String, today: LocalDate, mode: Mode, festival: Boolean, zone: ZoneId = ZoneId.systemDefault()): Look? {
        if (mode == Mode.KEEP) return null
        val age = s?.age ?: Age.OGENJ
        val c = if (age <= Age.TABOR) null else culture.takeIf { it in CULTURES } ?: CULTURES.first()
        val snow = snow(today)
        val sign = when {
            s == null -> if (lights(today)) Sign.FESTIVAL else null
            mode == Mode.AGE_SEASON -> if (lights(today)) Sign.FESTIVAL else null
            else -> signOf(s, today, festival || lights(today), zone)
        }
        return Look(age, c, sign, snow)
    }

    /** The day's highest sign: a new age (its day and the next), a festival, built (2 days), someone new (2 days). */
    fun signOf(s: GameState, today: LocalDate, festival: Boolean, zone: ZoneId = ZoneId.systemDefault()): Sign? {
        val since = today.minusDays(1)
        val recent = { d: LocalDate? -> d != null && !d.isBefore(since) && !d.isAfter(today) }
        if (s.age > Age.OGENJ && recent(AgeUps.reachedOn(s, s.age, zone))) return Sign.NEWAGE
        if (festival) return Sign.FESTIVAL
        val built = s.buildings.any { it.builtAt > 0 && recent(Instant.ofEpochMilli(it.builtAt).atZone(zone).toLocalDate()) } ||
            s.projectsFinished.values.any { recent(day(it)) }
        if (built) return Sign.BUILT
        if (s.age > Age.OGENJ && s.residents.any { it.since != s.foundedOn && recent(day(it.since)) }) return Sign.NEWCOMER
        return null
    }

    /** Whether the village keeps a festival [today]: the culture's (its day, or a grace day while not celebrated) or its own. */
    fun festivalOn(s: GameState?, today: LocalDate): Boolean {
        if (s == null) return false
        if (s.event?.kind == EventKind.FESTIVAL) return true
        return Calendar.on(s, today).any { it.late == 0 || !it.done }
    }

    /**
     * What the background job does now: nothing while the app is in the foreground (a switch there would close the task
     * started from the old alias), nothing on a day already decided unless the learner asked ([force]), otherwise show
     * [wanted] when the launcher's entries aren't exactly it, and with no wanted picture ([Mode.KEEP]) only repair.
     */
    fun decide(foreground: Boolean, today: LocalDate, decidedOn: String?, force: Boolean, enabled: List<String>, wanted: String?): Action = when {
        foreground -> Action.WAIT
        wanted == null -> if (enabled.isEmpty()) Action.REPAIR else Action.NONE
        !force && decidedOn == today.toString() -> if (enabled.isEmpty()) Action.REPAIR else Action.NONE
        enabled == listOf(wanted) -> Action.DECIDED
        else -> Action.SWITCH
    }

    enum class Action {
        /** In the foreground: try again from the background. */
        WAIT,
        /** Nothing to do. */
        NONE,
        /** The day is decided: the icon already shows the picture. */
        DECIDED,
        /** Show the wanted picture. */
        SWITCH,
        /** No launcher entry is enabled: enable one (the last shown, else [DEFAULT]). */
        REPAIR,
    }

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }

    private fun day(iso: String): LocalDate? = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
}
