package si.lanisce.lani.game.scene

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Introduction
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.Learner
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Every scene of every culture gives a beginner a round of «Vidim, vidim» (companion/SCENES.md "I spy"), as the app plays it:
 * the scene's things in the scene's language (the packs' words), its clue file read as ISpyController reads it, the child's
 * lines in its language, and the clues a learner at A1 with nothing introduced gets ([Introduction.mastery]: the pages
 * above A1 not yet). In each world the scene is seen in (everything built, nothing built, the building it is in at each
 * level) at every part of the day and in every season, wherever the picture shows something:
 * - a round can start ([ISpy.canStart], what the offer asks), with a thing whose two clues are its own, about the picture;
 * - with everything built, a whole game ([ISpy.ROUNDS] rounds) where that many things show;
 * - and even a learner with no page of the book at all gets a round (the picture-free clues: the noun's first letter and
 *   its letters, [ISpy.fallback]).
 * The scenes are read from the files, so a new culture's scenes (companion/cultures/<culture>/scenes) are held to it too.
 */
class ISpyRoundsTest {
    private val companion = CuratedContent.companion
    private fun obj(f: File) = json.parseToJsonElement(f.readText()).jsonObject
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** pack id → word id → the word. */
    private val packs: Map<String, Map<String, JsonObject>> by lazy {
        (companion.resolve("packs").walkTopDown().filter { it.isFile && it.extension == "json" } +
            companion.resolve("cultures").listFiles().orEmpty().asSequence().flatMap { c -> c.resolve("packs").listFiles { f -> f.extension == "json" }.orEmpty().asSequence() })
            .mapNotNull { f -> runCatching { obj(f) }.getOrNull() }
            .filter { it.str("id") != null && it["words"] != null }
            .associate { p -> p.str("id")!! to p["words"]!!.jsonArray.map { it.jsonObject }.associateBy { it.str("id")!! } }
    }

    /** language → page id → level. */
    private val pages: Map<String, Map<String, String?>> by lazy {
        companion.resolve("grammar").listFiles().orEmpty().filter { it.isDirectory }.associate { d ->
            d.name to d.listFiles { f -> f.extension == "json" }.orEmpty().associate { f -> obj(f).let { it.str("id")!! to it.str("level") } }
        }
    }

    /** Every scene with its culture: the default culture's (companion/scenes), then each culture pack's. */
    private val scenes: List<Pair<String, SceneSpec>> by lazy {
        val files = companion.resolve("scenes").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.map { Cultures.DEFAULT to it } +
            companion.resolve("cultures").listFiles().orEmpty().sortedBy { it.name }
                .flatMap { c -> c.resolve("scenes").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.map { c.name to it } }
        files.map { (culture, f) ->
            val s = parseScene(f.readText())
            culture to s.copy(objects = s.objects.map { o ->
                val w = packs[o.pack ?: s.pack]?.get(o.word)
                o.copy(sl = w?.str(s.language) ?: w?.str("sl") ?: "", en = w?.str("en") ?: "", gender = w?.str("gender"))
            })
        }
    }

    private val books = HashMap<String, ISpyBook?>()
    private fun book(id: String): ISpyBook? = books.getOrPut(id) {
        companion.resolve("ispy/scenes/$id.json").takeIf { it.isFile }?.let { ISpy.parseBook(Learner.current.renderJson(it.readText()), "en") }
    }

    private val lines = HashMap<String, ISpyLines?>()
    private fun lines(language: String): ISpyLines? = lines.getOrPut(language) {
        companion.resolve("ispy/$language.json").takeIf { it.isFile }?.let { ISpy.parseLines(Learner.current.renderJson(it.readText()), "en") }
    }

    /** A beginner's pages not yet in [language]: above A1 and not introduced, as GrammarController has them. */
    private fun beginner(language: String): (String) -> Boolean = { id ->
        val book = pages[language].orEmpty()
        id in book && Introduction.mastery(book[id], "A1", false, null, emptyList(), emptyList()) == Mastery.NOT_YET
    }

    /** The worlds [scene] is seen in: everything built, nothing built, and the building it is in at each level. */
    private fun worlds(culture: String, scene: SceneSpec): List<Pair<String, SceneWorld>> {
        val types = scene.from.mapNotNull { (TownPlace.of(it) as? TownPlace.At)?.type }.distinct()
        return listOf("built" to SceneWorld(culture = culture), "bare" to SceneWorld.NONE.copy(culture = culture)) +
            types.flatMap { t -> (1..3).map { lv -> "${t.name.lowercase()} $lv" to SceneWorld.room(lv, t).copy(culture = culture) } }
    }

    /** Dawn, morning, midday, afternoon, dusk, night. */
    private val hours = listOf(5.5f, 9f, 12.5f, 16f, 19.75f, 23f)
    private val months = listOf(1, 4, 7, 10)
    private val villages = listOf(0L, 7L)

    private fun eachFrame(f: (culture: String, scene: SceneSpec, at: String, visible: Set<String>, built: Boolean, today: LocalDate) -> Unit) {
        for ((culture, scene) in scenes) for ((wn, world) in worlds(culture, scene)) for (month in months) for (hour in hours) for (v in villages) {
            val at = LocalDateTime.of(2026, month, 12, hour.toInt(), ((hour % 1f) * 60).toInt())
            val frame = SceneSights.frame(at, world, v)
            val visible = scene.objects.filter { SceneSights.visibleNow(scene.art, it, frame) }.map { it.slot }.toSet()
            if (visible.isEmpty()) continue
            f(culture, scene, "${scene.id} [$culture] $wn, $month/${"%.2f".format(hour)}, village $v", visible, wn == "built", at.toLocalDate())
        }
    }

    @Test fun `every scene of every culture has the child's lines in its language`() {
        val fails = scenes.map { it.second }.filter { lines(it.language) == null }.map { "${it.id}: no companion/ispy/${it.language}.json" }
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
        assertTrue("${scenes.size} scenes", scenes.size >= 70)
    }

    @Test fun `a beginner gets a round in every scene, whatever is built, at any hour and season, about the picture`() {
        val fails = LinkedHashSet<String>()
        var frames = 0
        eachFrame { _, scene, at, visible, built, today ->
            val l = lines(scene.language) ?: return@eachFrame
            val book = book(scene.id)
            val notYet = beginner(scene.language)
            frames++
            val spyable = ISpy.spyable(scene, book, l, visible, notYet)
            if (spyable.isEmpty()) { fails += "$at: no round for a beginner (in the picture: ${visible.sorted()})"; return@eachFrame }
            // the round asks for the picture: a thing whose two clues are its own, not its letters only
            val own = spyable.filter { (r, _) -> r.clues.count { it.kind in ISpy.KINDS } >= 2 }
            if (own.isEmpty()) fails += "$at: only picture-free clues for a beginner (${spyable.map { it.first.slot }})"
            if (built) {
                val game = ISpy.rounds(scene, book, l, visible, notYet, { null }, emptySet(), emptySet(), today, Random(1))
                if (game.size < minOf(ISpy.ROUNDS, visible.size)) fails += "$at: a game of ${game.size} rounds for a beginner, ${visible.size} things in the picture"
            }
        }
        assertTrue(fails.take(60).joinToString("\n") + if (fails.size > 60) "\n… ${fails.size} in all" else "", fails.isEmpty())
        assertTrue("$frames frames", frames > 2000)
    }

    @Test fun `even with no page of the book a round can start in every scene, by the nouns' letters`() {
        val fails = LinkedHashSet<String>()
        eachFrame { _, scene, at, visible, _, _ ->
            val l = lines(scene.language) ?: return@eachFrame
            if (!ISpy.canStart(scene, book(scene.id), l, visible) { true }) fails += "$at: no round without grammar (in the picture: ${visible.sorted()})"
        }
        assertTrue(fails.take(60).joinToString("\n") + if (fails.size > 60) "\n… ${fails.size} in all" else "", fails.isEmpty())
    }
}
