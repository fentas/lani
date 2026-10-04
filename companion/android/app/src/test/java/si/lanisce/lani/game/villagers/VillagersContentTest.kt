package si.lanisce.lani.game.villagers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.game.scene.parseScene
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import si.lanisce.lani.data.json
import java.io.File

/**
 * The curated cast (the JSON files in companion/cultures/primorska/villagers) parses with the app's format, uses only
 * sprites, homes, ages and skills the app knows, and covers everyone the culture pack's quests and the scenes name.
 */
class VillagersContentTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { it.resolve("cultures").isDirectory }
    private val dir = companion.resolve("cultures/primorska/villagers")
    private val files = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
    private val cast = parseVillagers(files.joinToString(",", "[", "]") { it.readText() })
    private val src = listOf(File("src/main/java/si/lanisce/lani/game"), File("app/src/main/java/si/lanisce/lani/game"), File("companion/android/app/src/main/java/si/lanisce/lani/game")).first { it.isDirectory }
    private val kinds = mapOf(
        "greet" to { l: VillagerLines -> l.greet }, "thanks" to { l: VillagerLines -> l.thanks }, "remember" to { l: VillagerLines -> l.remember },
        "idle" to { l: VillagerLines -> l.idle }, "cheer" to { l: VillagerLines -> l.cheer }, "comfort" to { l: VillagerLines -> l.comfort },
        "listen" to { l: VillagerLines -> l.listen }, "bye" to { l: VillagerLines -> l.bye },
    )

    @Test fun `every curated villager parses, one per file, each with their own arrival order`() {
        assertEquals(files.map { it.nameWithoutExtension }, cast.map { it.id })
        assertEquals("the cast of 13, the roles the game needs", 13, cast.count { !it.extra })
        assertEquals("the extras: smaller people of the culture", listOf("joze"), cast.filter { it.extra }.map { it.id })
        assertEquals("every order once", cast.size, cast.map { it.order }.toSet().size)
        assertEquals("Micka and Luka live at the camp from the start", listOf("micka", "luka"), cast.sortedBy { it.order }.take(2).map { it.id })
        assertTrue("the first arrivals need no age and no building", cast.sortedBy { it.order }.take(2).all { v -> v.since == null && v.home.any { it.startsWith("spot:") } })
        assertTrue("the extras come after the cast", cast.filter { it.extra }.minOf { it.order } > cast.filter { !it.extra }.maxOf { it.order })
        val joze = cast.first { it.id == "joze" }
        assertEquals("the hunter lives at the high seat", listOf("spot:highseat") to "hunter", joze.home to joze.art)
        val lines = with(joze.lines) { greet + thanks + remember + idle + cheer + comfort + listen + bye }
        assertTrue("an extra has fewer lines: ${lines.size}", lines.size in 15..25)
    }

    @Test fun `sprites, voices, registers, homes, ages and skills are what the app knows`() {
        for (v in cast) {
            assertTrue("${v.id}: sprite ${v.art}", v.art in SceneArt.people)
            assertTrue("${v.id}: voice", v.voice in listOf("female", "male"))
            assertTrue("${v.id}: register", v.register in listOf("ti", "vi"))
            assertTrue("${v.id}: a home", v.home.isNotEmpty())
            for (h in v.home) {
                val spot = h.startsWith("spot:") && h.removePrefix("spot:") in TownSpots.ids
                val building = BuildingType.entries.any { it.name.equals(h, ignoreCase = true) }
                assertTrue("${v.id}: home $h", spot || building)
                assertTrue("${v.id}: home $h resolves", TownPlace.of(h) != TownPlace.Fire)
            }
            v.since?.let { s -> assertTrue("${v.id}: since $s", Age.entries.any { it.name.equals(s, ignoreCase = true) }) }
            v.skill?.let { s -> assertTrue("${v.id}: skill $s", Res.entries.any { it.name.equals(s, ignoreCase = true) }) }
            assertTrue("${v.id}: name and role", v.name.isNotBlank() && v.role.contains(" · "))
        }
        assertEquals("the teacher waits for the school", listOf("school"), cast.first { it.id == "mojca" }.home)
        // every villager speaks with a voice of the cast (companion/voice-cast.json), of their gender
        val speakers = json.parseToJsonElement(companion.resolve("voice-cast.json").readText()).jsonObject["speakers"]!!.jsonObject
        for (v in cast) {
            val s = speakers[v.speaker ?: ""]?.jsonObject
            assertTrue("${v.id}: speaker ${v.speaker} in the cast", s != null)
            assertEquals("${v.id}: speaker of their gender", v.voice, s!!["gender"]!!.jsonPrimitive.content)
            assertEquals(v.speaker, v.speakerVoice)
        }
        assertEquals("the cast has the speakers the app gives people who move in or are born here", emptyList<String>(), listOf("young-woman", "young-man", "girl", "boy").filter { it !in speakers })
        assertEquals("vas", cast.first { it.id == "mojca" }.since)
        assertEquals("the smith waits for the smithy", listOf("smithy"), cast.first { it.id == "tone" }.home)
    }

    @Test fun `lines, 2 to 6 per kind at a time of day, remember lines carry the memory, greetings grow warmer`() {
        for (v in cast) {
            for ((kind, pick) in kinds) {
                val lines = pick(v.lines)
                assertTrue("${v.id}: $kind has ${lines.size} lines", lines.size in 2..12)
                // a line with a "when" counts only at its times: an evening greeting beside the day's doesn't crowd them
                for (t in VillagerLine.PARTS) assertTrue("${v.id}: $kind has ${lines.at(t).size} lines for the $t", lines.at(t).size in 1..6)
                for (l in lines) {
                    assertTrue("${v.id}: $kind sl", l.target.isNotBlank() && l.base.isNotBlank())
                    assertTrue("${v.id}: $kind level", l.level in 0..4)
                    if (kind == "remember") assertTrue("${v.id}: remember needs {memory}", "{memory}" in l.target)
                    else assertTrue("${v.id}: only remember lines carry {memory}", "{memory}" !in l.target)
                }
            }
            assertTrue("${v.id}: a stranger's greeting", v.lines.greet.any { it.level == 0 })
            for (t in VillagerLine.PARTS) assertTrue("${v.id}: a stranger's greeting in the $t", v.lines.greet.at(t).any { it.level == 0 })
            assertTrue("${v.id}: a friend's greeting", v.lines.greet.any { it.level >= 3 })
            assertEquals("${v.id}: linesFor picks the warmest", v.lines.greet.maxOf { it.level }, Bonds.linesFor(v.lines.greet, 4).first().level)
        }
    }

    @Test fun `every quest giver of the culture and in TownMarkers kt is a villager by name`() {
        val names = cast.map { it.name }.toSet()
        val givers = Cultures.load(Cultures.DEFAULT).quests.requests.map { it.giver }.toSet()
        assertEquals(10, givers.size)
        assertEquals("quest givers without a villager", emptySet<String>(), givers - names)
        val markers = File(src, "scene/TownMarkers.kt").readText()
        val placed = Regex("\"([^\"]+)\" to listOf\\(TownPlace").findAll(markers).map { it.groupValues[1] }.toSet()
        assertEquals("giverPlaces without a villager", emptySet<String>(), placed - names)
    }

    @Test fun `everyone the culture pack names is a villager of its cast`() {
        val c = Cultures.load(Cultures.DEFAULT)
        val ids = cast.map { it.id }.toSet()
        val named = buildMap<String, List<String>> {
            put("festival leaders", c.festivals.flatMap { it.leaders })
            put("project leaders and helpers", c.projects.flatMap { listOf(it.leader) + it.helpers })
            put("tool givers", c.tools.keys.toList())
            put("thanks", c.chest.thanks.keys.toList())
            put("likes", c.chest.likes.keys.toList())
            put("children", c.chest.children)
            put("the smith", listOf(c.chest.smith.id))
            put("the shepherd", listOf(c.surprises.lamb.shepherd))
            put("the riddle's child", listOf(c.surprises.riddle.child))
            put("letters", c.surprises.letter.letters.map { it.to }.filter { it != "jan" })
        }
        for ((what, who) in named) assertEquals("$what without a villager", emptySet<String>(), who.toSet() - ids)
        assertEquals("the children say ti", setOf("ti"), cast.filter { it.id in c.chest.children }.map { it.register }.toSet())
        assertEquals("the smith lives at the smithy", listOf("smithy"), cast.first { it.id == c.chest.smith.id }.home)
    }

    @Test fun `the people in the curated scenes are villagers of the cast, and every happening leaves a memory`() {
        val ids = cast.map { it.id }.toSet()
        val scenes = companion.resolve("scenes")
        for (f in scenes.listFiles { x -> x.extension == "json" }.orEmpty()) {
            val s = parseScene(f.readText())
            for (p in s.people) assertTrue("${s.id}/${p.id}: villager ${p.villager}", p.villager in ids)
            for (h in s.happenings) assertTrue("${s.id}/${h.id}: memory", !h.memory?.sl.isNullOrBlank() && !h.memory?.en.isNullOrBlank())
        }
    }
}
