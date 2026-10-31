package si.lanisce.lani.game.culture

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.VisitWorld
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.render.MapLook
import si.lanisce.lani.game.render.VillageLayout
import si.lanisce.lani.game.scene.Homes
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.towns.VisitLogic
import java.io.File

/**
 * The village map and the scenes take the culture from one source ([VillageCulture]; docs/plans/05-built/culture-source.md):
 * for every course (the pair the learner learns in) and every culture a bridge can say (the bundled ones, and one this app
 * doesn't have), on every way in (the learner's own village, a visit to a town of each culture, the founding's and the
 * setup's previews, the widget), what the map's frame is drawn in is what the scene behind the door is drawn in: the same
 * culture, the same land, the very same look. And the app's sources build no picture's culture from anywhere else, so the
 * two can't come apart again.
 */
class CultureSourceTest {
    private val pair = L10n.pair

    @After fun restore() {
        VisitWorld.leave()
        L10n.pair = pair
        Cultures.said = false
        Cultures.use(null as String?)
    }

    /** The courses: Jan's, Teo's, and the others the app teaches, each in another base. */
    private val courses = listOf(
        LangPair(Lang.SL, Lang.EN), LangPair(Lang.IT, Lang.DE), LangPair(Lang.DE, Lang.SL), LangPair(Lang.EN, Lang.SL),
        LangPair(Lang.IT, Lang.SL), LangPair(Lang.SL, Lang.IT),
    )

    /** Every culture a bridge can say: the bundled ones, and one an older app doesn't have (a newer pack). */
    private val said = Cultures.ids + "nowhere"

    /** What the app plays when a bridge says [id]: the pack, else the default (as CultureSetting applies it). */
    private fun played(id: String) = runCatching { Cultures.load(id) }.getOrNull()?.id ?: Cultures.DEFAULT

    /** A village on every land there is, and on one this app doesn't know (a newer land: the classic valley). */
    private val villages: List<GameState> = Landscape.entries.map { GameEngine.newGame(7L, 0L, it) } +
        GameEngine.newGame(7L, 0L, Landscape.HILLS).let { it.copy(land = Land("glacier", 3)) }

    private val sceneFile = listOf(File("../../scenes"), File("../scenes"), File("companion/scenes")).first { it.isDirectory }

    /** A scene of the village (the kitchen, opened from a house) and one opened from no building (the forest). */
    private val scenes: List<SceneSpec> by lazy { listOf("kuhinja", "v-gozdu").map { parseScene(File(sceneFile, "$it.json").readText()) } }

    /** The culture and the land a map frame of [state] is drawn in, as the map, Home's header and the widget build it. */
    private fun mapOf(state: GameState, culture: Culture = VillageCulture.shown): MapLook =
        MapLook.of(culture.id, VillageLayout.of(state).landscape.id)

    /** The map and [world] agree: one culture, one land, the very same look. */
    private fun agree(what: String, map: MapLook, world: SceneWorld) {
        assertEquals("$what: the culture", map.id, world.culture)
        assertSame("$what: the look (culture and land)", map.look, world.look)
    }

    @Test fun `the learner's own village - the map and every scene in the culture the bridge said, for every course`() {
        for (course in courses) for (c in said) {
            L10n.pair = course
            Cultures.use(c)
            for (state in villages) {
                val map = mapOf(state)
                assertEquals("$course, $c: the culture played", played(c), map.id)
                agree("$course, $c on ${state.land?.kind}", map, SceneWorld.of(state))
                for (s in scenes) agree("$course, $c on ${state.land?.kind}, ${s.id}", map, Homes.world(s, state))
                // the widget draws the own village as the map does
                assertEquals("$course, $c: the widget", map.id, VillageCulture.home.id)
            }
            // a scene opened before the village is read is in the culture its map will have
            assertEquals("$course, $c: no village yet", played(c), SceneWorld.shown.culture)
        }
    }

    @Test fun `a visit - the town's map and its places in the host's culture, the own village's widget in the learner's`() {
        for (course in courses) for (own in Cultures.ids) for (host in said + "") {
            L10n.pair = course
            Cultures.use(own)
            VisitWorld.enter("it", host)
            val hostVillage = villages[2]
            val map = mapOf(hostVillage)
            // the host's culture as this app can play it, else the learner's own
            val expected = runCatching { Cultures.load(host) }.getOrNull()?.id ?: own
            assertEquals("$course, $own visiting $host: the town's map", expected, map.id)
            for (s in scenes) agree("$course, $own visiting $host: ${s.id}", map, VisitLogic.world(s, hostVillage, scenes))
            assertEquals("$course, $own visiting $host: the town not read yet", expected, VisitLogic.world(scenes[0], null, scenes).culture)
            assertEquals("$course, $own visiting $host: the widget", own, VillageCulture.home.id)
            VisitWorld.leave()
            agree("$course, $own home from $host", mapOf(villages[1]), SceneWorld.of(villages[1]))
        }
    }

    @Test fun `the founding's preview is the village it founds, the setup's the region until a bridge said one`() {
        for (course in courses) for (c in said) {
            L10n.pair = course
            Cultures.use(c)
            // the founding draws the land in the village's culture: the village it founds is in that culture, map and scenes
            val founding = si.lanisce.lani.game.Founding.start(VillageCulture.home.id, null, 11L)
            val village = founding.village(0L)
            assertEquals("$course, $c: the founding", played(c), founding.culture)
            agree("$course, $c: founded", MapLook.of(founding.culture, founding.landscape.id), SceneWorld.of(village))
            assertEquals("$course, $c: the founding's sea", VillageCulture.sea(VillageCulture.shown), si.lanisce.lani.game.Founding.seaIn(founding.culture))
            // the setup before any bridge said a culture: the region of the language chosen; after one did, the village's
            Cultures.said = false
            for (t in Lang.entries) assertEquals("$course, $c: the setup for $t", VillageCulture.region(t) ?: played(c), VillageCulture.preview(t))
            assertEquals("$course, $c: the setup's first screen", played(c), VillageCulture.preview(null))
            Cultures.said = true
            for (t in Lang.entries) assertEquals("$course, $c: the setup again for $t", played(c), VillageCulture.preview(t))
        }
        assertEquals("primorska", VillageCulture.region(Lang.SL))
        assertEquals("friuli", VillageCulture.region(Lang.IT))
    }

    /** The app's sources (app/src/main/java), each file's text by its path under it. */
    private val sources: Map<String, String> by lazy {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { it.relativeTo(root).path to it.readText() }
    }

    /** The argument list of every call of [fn] in [text] (balanced parentheses). */
    private fun calls(text: String, fn: Regex): List<String> = fn.findAll(text).map { m ->
        var depth = 0
        var i = m.range.last
        val start = i + 1
        while (i < text.length) {
            when (text[i]) { '(' -> depth++; ')' -> { depth--; if (depth == 0) break } }
            i++
        }
        text.substring(start, i.coerceAtMost(text.length))
    }.toList()

    /** A culture an argument list gives: what follows `culture =`, up to the next comma or line's end. */
    private val cultureArg = Regex("""\bculture\s*=\s*([^,\n)]+)""")

    /** Where a picture's culture may come from: [VillageCulture], or a parameter its callers fill from it. */
    private fun fromSource(expr: String) = expr.trim().let { it.startsWith("VillageCulture.") || it.startsWith("si.lanisce.lani.game.culture.VillageCulture.") || it == "culture" }

    @Test fun `every picture of the village takes its culture from VillageCulture`() {
        val wrong = ArrayList<String>()
        for ((path, text) in sources) {
            // the map's frames (game/render's Frame: the map, Home's header, the widget, the previews, the charcoal pile)
            for (args in calls(text, Regex("""(?<![A-Za-z.])Frame\("""))) for (m in cultureArg.findAll(args)) {
                if (!fromSource(m.groupValues[1])) wrong += "$path: Frame(culture = ${m.groupValues[1].trim()})"
            }
            // the scenes' worlds: none built or copied with a culture of its own, but in SceneWorld itself (from VillageCulture)
            if (path.endsWith("game/scene/SceneWorld.kt") || path.endsWith("game/culture/VillageCulture.kt")) continue
            val scenic = "SceneWorld" in text || listOf("game/scene/", "ui/scene/", "ui/towns/", "game/render/").any { it in path }
            if (scenic) for (args in calls(text, Regex("""(?<![A-Za-z])SceneWorld\(|\.copy\("""))) for (m in cultureArg.findAll(args)) {
                if (!fromSource(m.groupValues[1])) wrong += "$path: a scene world's culture = ${m.groupValues[1].trim()}"
            }
            // the visited culture is VillageCulture's too, as the town's map and its places read it
            if (Regex("""Cultures\.visiting\s*+=\s*+(?!null)(?!si\.lanisce\.lani\.game\.culture\.VillageCulture\.visiting)(?!VillageCulture\.visiting)""").containsMatchIn(text)) wrong += "$path: Cultures.visiting set elsewhere"
        }
        assertEquals("pictures whose culture comes from somewhere else", emptyList<String>(), wrong.distinct())
        // and the sites there are all ask it (a new picture's site joins this list)
        val asking = sources.filter { (_, t) -> "VillageCulture." in t }.keys.map { it.substringAfterLast('/') }.toSet()
        for (f in listOf("VillageScene.kt", "TownScene.kt", "VillageWidget.kt", "FoundingScreen.kt", "SetupScreen.kt", "PilePicture.kt", "WordPicture.kt", "SceneWorld.kt", "VisitLogic.kt", "Visits.kt")) {
            assertTrue("$f asks VillageCulture", f in asking)
        }
    }
}
