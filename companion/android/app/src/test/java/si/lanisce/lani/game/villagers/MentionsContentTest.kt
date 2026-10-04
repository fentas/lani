package si.lanisce.lani.game.villagers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import java.io.File

/**
 * Who the curated content names (companion/VILLAGERS.md, "Who a text may name"), in all four culture packs: every scene's
 * happenings (their dialogs, titles and memories), the requests, the surprises, the festivals, the projects, the events,
 * the introductions, the villagers' own lines, the readings, the word packs and whatever else a pack has, scanned with the
 * app's [Names] in the pack's language. What the app holds back until everyone it names is here and met (a happening, a
 * request, a letter, a reading, a line, a level of an introduction) is reported; what it can't hold back may name only
 * whom it always has (a project its leader, an event its defender, the shepherd's lamb the shepherd; a festival, the
 * strangers, the riddles and the newcomers' plain lines nobody). The storyteller's legends are left
 * alone (their people are history). The report: build/reports/mentions/mentions.txt. New content is scanned as it comes;
 * a new kind of file has to say here how the app holds back what it names ([KINDS]).
 */
class MentionsContentTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { it.resolve("cultures").isDirectory }
    private val packs = listOf("primorska", "friuli", "kaernten", "lakeland")
    private lateinit var pair: LangPair

    @Before fun setUp() {
        pair = L10n.pair
    }

    @After fun tearDown() {
        L10n.pair = pair
    }

    /** How the app treats what a kind of file names. */
    private enum class Policy { HELD, RULED, NOBODY, NOTES, LEGENDS }

    /** Every kind of file a culture pack has, and its policy; a file not listed fails the test. */
    private val KINDS = mapOf(
        "scenes" to Policy.HELD, "quests.json" to Policy.HELD, "surprises.json" to Policy.RULED, "readings" to Policy.HELD,
        "villagers" to Policy.HELD, "arrivals.json" to Policy.HELD, "festivals.json" to Policy.RULED, "projects.json" to Policy.RULED,
        "events.json" to Policy.RULED, "people.json" to Policy.NOBODY, "chronicle.json" to Policy.NOBODY, "chest.json" to Policy.NOTES,
        "culture.json" to Policy.NOTES, "world.json" to Policy.NOTES, "packs" to Policy.NOTES, "voice-cast.json" to Policy.NOTES,
        "sky.json" to Policy.NOTES, "stories" to Policy.LEGENDS,
    )

    private fun castOf(id: String): List<Villager> =
        companion.resolve("cultures/$id/villagers").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }

    private fun read(f: File): JsonElement = Json.parseToJsonElement(f.readText())

    /** Every text of [e] said in [lang], with where it is (a key [lang]'s string; a `why` explains, and isn't said). */
    private fun texts(e: JsonElement?, lang: String, at: String = ""): List<Pair<String, String>> = when (e) {
        is JsonObject -> e.flatMap { (k, v) ->
            when {
                k == lang && v is JsonPrimitive && v.isString -> listOf(at to v.content)
                k == "why" || k == "review" -> emptyList()
                else -> texts(v, lang, "$at.$k")
            }
        }
        is JsonArray -> e.flatMapIndexed { i, v -> texts(v, lang, "$at[$i]") }
        else -> emptyList()
    }

    private fun Names.inTexts(e: JsonElement?, lang: String): Set<String> = texts(e, lang).flatMapTo(LinkedHashSet()) { of(it.second) }

    private fun str(o: JsonElement?, k: String): String? = ((o as? JsonObject)?.get(k) as? JsonPrimitive)?.contentOrNull

    private fun list(o: JsonElement?, k: String): List<String> = ((o as? JsonObject)?.get(k) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    @Test fun `what every pack's content names, and what may name no one`() {
        val report = StringBuilder()
        val wrong = ArrayList<String>()
        for (id in packs) {
            val dir = companion.resolve("cultures/$id")
            val lang = Cultures.manifest(id)!!.language
            val cast = castOf(id)
            val names = Names(cast, lang)
            val byName = cast.associate { it.name to it.id }
            fun shown(ids: Collection<String>) = ids.joinToString(", ") { names.names[it] ?: it }
            fun only(where: String, named: Set<String>, may: Set<String>) {
                val extra = named - may
                if (extra.isNotEmpty()) wrong += "$id $where names ${shown(extra)}${if (may.isEmpty()) "" else " (it may name ${shown(may)})"}"
            }
            report.appendLine("== $id ($lang): ${cast.size} villagers")

            // every file is of a kind the app knows what to do with
            for (f in dir.listFiles().orEmpty().sortedBy { it.name }) {
                if (f.isFile && f.extension != "json") continue
                assertTrue("$id: ${f.name} is a new kind of content: say in MentionsContentTest (KINDS) how the app holds back whom it names", f.name in KINDS)
            }

            // scenes: a happening is on only when everyone it names is here and met (Happenings.on)
            val sceneFiles = (dir.resolve("scenes").listFiles().orEmpty().toList() +
                (if (lang == "sl") companion.resolve("scenes").listFiles().orEmpty().toList() else companion.resolve("scenes/$lang").listFiles().orEmpty().toList()))
                .filter { it.isFile && it.extension == "json" }.sortedBy { it.name }
            L10n.pair = LangPair(si.lanisce.lani.l10n.Lang.of(lang)!!, if (lang == "en") si.lanisce.lani.l10n.Lang.SL else si.lanisce.lani.l10n.Lang.EN)
            val scenes: List<SceneSpec> = sceneFiles.map { parseScene(it.readText()) }
            var happenings = 0
            val others = ArrayList<String>()
            for (s in scenes) for (h in s.happenings) {
                happenings++
                val own = s.people.firstOrNull { it.id == h.who }?.villager
                val named = Mentions.inHappening(s, h, Names(cast, s.language))
                val besides = named - setOfNotNull(own)
                if (besides.isNotEmpty()) others += "  ${s.id}/${h.id} (${own ?: h.who}): ${shown(named)}"
            }
            report.appendLine("scenes: ${scenes.size}, happenings: $happenings; ${others.size} name someone besides their person (on only once they're here and met):")
            others.forEach { report.appendLine(it) }

            // requests: asked only while everyone they name is here and met (Quests.refill, Residents.openQuests)
            val quests = read(dir.resolve("quests.json"))
            val requests = (quests as JsonObject)["requests"] as JsonArray
            val asking = requests.mapNotNull { r ->
                val giver = byName[str(r, "giver")]
                val named = names.inTexts(JsonObject((r as JsonObject).filterKeys { it == "title" || it == "story" }), lang)
                if (named.isEmpty()) null else "  ${str(r, "giver")}: «${str((r["title"] as JsonObject), lang)}» ${shown(named)}${if (named - setOfNotNull(giver) != emptySet<String>()) " ← besides the giver" else ""}"
            }
            report.appendLine("requests: ${requests.size}; ${asking.size} name someone:")
            asking.forEach { report.appendLine(it) }
            val tent = quests["tent_move"]
            only("the tent's move", names.inTexts(tent, lang), setOfNotNull(byName[str(tent, "giver")]))

            // surprises: a letter comes only while everyone it names is here and met (Surprises.roll); the rest names only
            // who it's about
            val surprises = read(dir.resolve("surprises.json")) as JsonObject
            val letter = surprises["letter"] as JsonObject
            for ((i, l) in ((letter["letters"] as? JsonArray).orEmpty()).withIndex()) {
                val named = names.inTexts(l, lang)
                if (named.isNotEmpty()) report.appendLine("  letter $i to ${str(l, "to")}: ${shown(named)}")
                only("the letter to ${str(l, "to")}", named, setOfNotNull(str(l, "to")))
            }
            only("the postman's lines", names.inTexts(JsonObject(letter.filterKeys { it != "letters" }), lang), emptySet())
            only("the lamb", names.inTexts(surprises["lamb"], lang), setOfNotNull(str(surprises["lamb"], "shepherd")))
            for (k in listOf("pedlar", "pilgrim", "riddle", "strangers")) only("the $k", names.inTexts(surprises[k], lang), emptySet())

            // festivals: led by the first of their leaders who lives here, whoever that is today; they name nobody
            val festivals = (read(dir.resolve("festivals.json")) as JsonObject)["festivals"] as JsonArray
            for (f in festivals) {
                val named = names.inTexts(f, lang)
                if (named.isNotEmpty()) report.appendLine("  festival ${str(f, "id")}: ${shown(named)}")
                only("the festival ${str(f, "id")}", named, emptySet())
            }

            // projects: they wait for their leader, and their helpers needn't live here; the lines name only the leader
            // (and groups: the neighbours, the lads, the children)
            val projects = (read(dir.resolve("projects.json")) as JsonObject)["projects"] as JsonArray
            for (p in projects) {
                val named = names.inTexts(p, lang)
                val leader = str(p, "leader")
                if ((named - setOfNotNull(leader)).isNotEmpty()) report.appendLine("  project ${str(p, "id")} (${leader}): ${shown(named)}")
                only("the project ${str(p, "id")}", named, setOfNotNull(leader))
            }

            // events: the defender stands with the learner; they name only them
            val events = read(dir.resolve("events.json")) as JsonObject
            for ((k, e) in events) only("the event $k", names.inTexts(e, lang), setOfNotNull(byName[str(e, "leader")]))

            // the newcomers' plain lines and the chronicle's lines name nobody of the cast
            only("people.json", names.inTexts(read(dir.resolve("people.json")), lang), emptySet())
            only("chronicle.json", names.inTexts(read(dir.resolve("chronicle.json")), lang), emptySet())

            // introductions: a level that names someone the learner doesn't know gives way to an easier one (Arrivals.meeting)
            val arrivals = (read(dir.resolve("arrivals.json")) as JsonObject)["cast"] as JsonObject
            for ((who, a) in arrivals) {
                val by = str(a, "by")
                for ((level, t) in ((a as JsonObject)["levels"] as JsonObject)) {
                    val named = Arrivals.textsOf(t as JsonObject, lang).flatMap { names.of(it) }.toSet() - setOfNotNull(who, by)
                    if (named.isNotEmpty()) report.appendLine("  introduction of $who at $level: ${shown(named)}")
                }
            }

            // the villagers' own lines: a line that names someone the learner doesn't know isn't said (VillagerLine.at)
            for (v in cast) {
                val lines = with(v.lines) { greet + thanks + remember + idle + cheer + comfort + listen + bye + gift.liked + gift.ordinary + gift.rare }
                val named = lines.flatMap { l -> names.of(l.by[lang]) }.toSet() - v.id
                if (named.isNotEmpty()) report.appendLine("  ${v.id}'s lines: ${shown(named)}")
            }

            // the readings: the corner keeps one that names someone the learner doesn't know (Readings.corner)
            for (f in dir.resolve("readings").listFiles().orEmpty().sortedBy { it.name }) {
                val named = names.inTexts(read(f), lang)
                if (named.isNotEmpty()) report.appendLine("  reading ${f.nameWithoutExtension}: ${shown(named)}")
            }

            // notes, not held back: the chest's settings, the manifest, the region's places, the night sky's lore, the word
            // packs' examples
            val wordPacks = dir.resolve("packs").listFiles().orEmpty().toList() +
                (if (lang == "sl") companion.resolve("packs").listFiles().orEmpty().toList() else companion.resolve("packs/$lang").listFiles().orEmpty().toList())
            val notes = listOf("chest.json", "culture.json", "world.json", "sky.json").map { dir.resolve(it) }.filter { it.isFile }
            for (f in notes + wordPacks.filter { it.isFile && it.extension == "json" }.sortedBy { it.name }) {
                val named = names.inTexts(read(f), lang)
                if (named.isNotEmpty()) report.appendLine("  notes ${f.relativeTo(companion).path}: ${shown(named)}")
            }
            report.appendLine("legends left alone: ${dir.resolve("stories").listFiles().orEmpty().size}")
        }
        File("build/reports/mentions").apply { mkdirs() }.resolve("mentions.txt").writeText(report.toString())
        println(report)
        assertTrue("${wrong.size} texts name someone they may not:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }
}
