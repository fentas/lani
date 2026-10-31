package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.AppIcons.Action
import si.lanisce.lani.data.AppIcons.Mode
import si.lanisce.lani.data.AppIcons.Sign
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.villagers.Resident
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/** The launcher's icon (data/AppIcons.kt, docs/plans/05-built/app-icon.md): its aliases, what it shows, when it switches. */
class AppIconsTest {
    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 31)
    private fun ms(d: LocalDate) = d.atTime(10, 0).toInstant(utc).toEpochMilli()
    private fun village(age: Age = Age.VAS, grown: LocalDate? = null, built: LocalDate? = null, newcomer: LocalDate? = null) = GameState(
        age = age,
        foundedOn = "2026-05-01",
        grown = grown?.let { mapOf(age.name.lowercase() to it.toString()) } ?: emptyMap(),
        buildings = built?.let { listOf(Building("b1", BuildingType.HUT, 0, builtAt = ms(it))) } ?: emptyList(),
        residents = listOf(Resident("micka", "2026-05-01")) + (newcomer?.let { listOf(Resident("vida", it.toString())) } ?: emptyList()),
    )
    private fun look(s: GameState?, day: LocalDate = today, culture: String = "primorska", mode: Mode = Mode.HAPPENING, festival: Boolean = false) =
        AppIcons.choose(s, culture, day, mode, festival, utc)?.alias

    // --- the aliases ------------------------------------------------------------------------------------------------

    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val manifestAliases = Regex("""<activity-alias android:name="(\.icon\.[A-Za-z]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()

    @Test
    fun `the manifest's aliases are exactly the app's, in the generator's order`() {
        assertEquals(176, AppIcons.all.size)
        assertEquals(AppIcons.all.map { it.alias }, manifestAliases)
        assertEquals(AppIcons.all.size, AppIcons.all.map { it.alias }.toSet().size)
    }

    @Test
    fun `only the fire is enabled in the manifest, and MainActivity is no launcher entry itself`() {
        val enabled = Regex("""android:name="(\.icon\.[A-Za-z]+)"[^>]*android:enabled="true"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf(AppIcons.DEFAULT), enabled)
        assertEquals(AppIcons.DEFAULT, AppIcons.all.first().alias)
        val main = manifest.substringAfter("android:name=\".MainActivity\"").substringBefore("</activity>")
        assertFalse(main.contains("android.intent.category.LAUNCHER"))
        assertEquals(AppIcons.all.size, Regex("android.intent.category.LAUNCHER").findAll(manifest).count())
    }

    @Test
    fun `no alias is ever renamed or removed`() {
        // app-icon-aliases.txt only grows: an alias the system had enabled that disappeared would leave no launcher icon
        val pinned = File("src/test/resources/app-icon-aliases.txt").readLines().filter { it.isNotBlank() }
        val missing = pinned - AppIcons.all.map { it.alias }.toSet()
        assertTrue("aliases gone: $missing", missing.isEmpty())
        assertTrue(pinned.size >= 176)
    }

    @Test
    fun `every alias has its icon in the generator's resources`() {
        val names = Regex("""android:icon="@mipmap/(ic_icon_[a-z_]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(176, names.size)
        for (n in names) assertTrue(n, File("src/main/res/mipmap-anydpi-v26/$n.xml").exists())
    }

    // --- what it shows --------------------------------------------------------------------------------------------

    @Test
    fun `the age and the culture`() {
        assertEquals(".icon.Ogenj", look(null))
        assertEquals(".icon.Ogenj", look(village(Age.OGENJ), culture = "friuli"))
        assertEquals(".icon.Tabor", look(village(Age.TABOR), culture = "friuli"))
        assertEquals(".icon.FriuliZaselek", look(village(Age.ZASELEK), culture = "friuli"))
        assertEquals(".icon.KaerntenVas", look(village(Age.VAS), culture = "kaernten"))
        assertEquals(".icon.LakelandTrg", look(village(Age.TRG), culture = "lakeland"))
        assertEquals(".icon.PrimorskaMesto", look(village(Age.MESTO)))
        // a culture without a look of its own draws the default culture's
        assertEquals(".icon.PrimorskaVas", look(village(), culture = "atlantis"))
    }

    @Test
    fun `snow from December to February`() {
        assertEquals(".icon.PrimorskaVasSnow", look(village(), day = LocalDate.of(2027, 2, 10)))
        assertEquals(".icon.PrimorskaVasSnow", look(village(), day = LocalDate.of(2026, 12, 1)))
        assertEquals(".icon.PrimorskaVas", look(village(), day = LocalDate.of(2027, 3, 1)))
        assertEquals(".icon.OgenjSnow", look(null, day = LocalDate.of(2027, 1, 20)))
    }

    @Test
    fun `the signs by priority`() {
        val y = today.minusDays(1)
        // a new age beats a festival, a festival beats built, built beats someone new
        assertEquals(".icon.PrimorskaVasNewage", look(village(grown = today, built = today, newcomer = today), festival = true))
        assertEquals(".icon.PrimorskaVasFestival", look(village(grown = today.minusDays(5), built = today, newcomer = today), festival = true))
        assertEquals(".icon.PrimorskaVasBuilt", look(village(built = y, newcomer = today)))
        assertEquals(".icon.PrimorskaVasNewcomer", look(village(newcomer = y)))
        // news lasts its day and the next
        assertEquals(".icon.PrimorskaVas", look(village(grown = today.minusDays(2), built = today.minusDays(2), newcomer = today.minusDays(2))))
        // the founders aren't newcomers
        assertEquals(".icon.PrimorskaVas", look(village().copy(foundedOn = today.toString(), residents = listOf(Resident("micka", today.toString())))))
        // a project finished counts as built
        assertEquals(".icon.PrimorskaVasBuilt", look(village().copy(projectsFinished = mapOf("most" to today.toString()))))
    }

    @Test
    fun `the fire has no new age and no newcomer, the camp has every sign`() {
        assertEquals(".icon.Ogenj", look(village(Age.OGENJ, grown = today, newcomer = today)))
        assertEquals(".icon.OgenjBuilt", look(village(Age.OGENJ, built = today)))
        assertEquals(".icon.TaborNewage", look(village(Age.TABOR, grown = today)))
        assertEquals(".icon.TaborNewcomer", look(village(Age.TABOR, newcomer = today)))
    }

    @Test
    fun `the December lights are the festival's look on snow, in every mode but keep`() {
        val christmas = LocalDate.of(2026, 12, 24)
        assertEquals(".icon.PrimorskaVasFestivalSnow", look(village(), day = christmas))
        assertEquals(".icon.PrimorskaVasFestivalSnow", look(village(built = christmas), day = christmas, mode = Mode.AGE_SEASON))
        assertEquals(".icon.OgenjFestivalSnow", look(null, day = LocalDate.of(2027, 1, 6)))
        assertEquals(".icon.PrimorskaVasSnow", look(village(), day = LocalDate.of(2027, 1, 7)))
        // a new age still beats the lights
        assertEquals(".icon.PrimorskaVasNewageSnow", look(village(grown = christmas), day = christmas))
    }

    @Test
    fun `age and season shows no sign but the lights, keep shows nothing`() {
        assertEquals(".icon.PrimorskaVas", look(village(grown = today, built = today), festival = true, mode = Mode.AGE_SEASON))
        assertNull(look(village(), mode = Mode.KEEP))
    }

    @Test
    fun `every look the chooser can pick is an alias`() {
        val aliases = AppIcons.all.map { it.alias }.toSet()
        val days = listOf(today, LocalDate.of(2026, 12, 24), LocalDate.of(2027, 2, 1))
        for (age in Age.entries) for (c in AppIcons.CULTURES) for (d in days) for (f in listOf(false, true))
            for (s in listOf(village(age), village(age, grown = d, built = d, newcomer = d)))
                for (m in listOf(Mode.HAPPENING, Mode.AGE_SEASON)) {
                    val a = AppIcons.choose(s, c, d, m, f, utc)!!.alias
                    assertTrue(a, a in aliases)
                }
    }

    @Test
    fun `the default mode follows the launcher`() {
        assertEquals(Mode.HAPPENING, AppIcons.defaultMode("com.google.android.apps.nexuslauncher"))
        assertEquals(Mode.HAPPENING, AppIcons.defaultMode("com.nothing.launcher"))
        assertEquals(Mode.HAPPENING, AppIcons.defaultMode("com.android.launcher3"))
        assertEquals(Mode.AGE_SEASON, AppIcons.defaultMode("com.sec.android.app.launcher"))
        assertEquals(Mode.AGE_SEASON, AppIcons.defaultMode("com.miui.home"))
        assertEquals(Mode.AGE_SEASON, AppIcons.defaultMode(null))
    }

    @Test
    fun `the sign's enum order is the priority`() {
        assertEquals(listOf(Sign.NEWAGE, Sign.FESTIVAL, Sign.BUILT, Sign.NEWCOMER), Sign.entries)
    }

    // --- when it switches -----------------------------------------------------------------------------------------

    @Test
    fun `never in the foreground`() {
        assertEquals(Action.WAIT, AppIcons.decide(true, today, null, true, listOf(".icon.Ogenj"), ".icon.Tabor"))
        assertEquals(Action.WAIT, AppIcons.decide(true, today, null, false, emptyList(), ".icon.Tabor"))
    }

    @Test
    fun `at most once a day`() {
        assertEquals(Action.SWITCH, AppIcons.decide(false, today, today.minusDays(1).toString(), false, listOf(".icon.Ogenj"), ".icon.Tabor"))
        assertEquals(Action.NONE, AppIcons.decide(false, today, today.toString(), false, listOf(".icon.Ogenj"), ".icon.Tabor"))
        // the learner's new choice, or the developer's button, decides again the same day
        assertEquals(Action.SWITCH, AppIcons.decide(false, today, today.toString(), true, listOf(".icon.Ogenj"), ".icon.Tabor"))
        // already showing it: the day is decided without a switch
        assertEquals(Action.DECIDED, AppIcons.decide(false, today, null, false, listOf(".icon.Tabor"), ".icon.Tabor"))
    }

    @Test
    fun `the repair`() {
        // two entries left by a switch cut short: the day's decision shows one
        assertEquals(Action.SWITCH, AppIcons.decide(false, today, null, false, listOf(".icon.Ogenj", ".icon.Tabor"), ".icon.Tabor"))
        // none left (an alias gone, a state broken): one is enabled again, even on a decided day and with keep
        assertEquals(Action.REPAIR, AppIcons.decide(false, today, today.toString(), false, emptyList(), ".icon.Tabor"))
        assertEquals(Action.REPAIR, AppIcons.decide(false, today, null, false, emptyList(), null))
        assertEquals(Action.NONE, AppIcons.decide(false, today, null, false, listOf(".icon.Tabor"), null))
    }
}
