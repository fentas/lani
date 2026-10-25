package si.lanisce.lani.game.culture

import si.lanisce.lani.data.Schema
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.villagers.VillagerLines
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.Learner
import si.lanisce.lani.l10n.Message
import java.util.concurrent.ConcurrentHashMap

/** A culture pack that can't be used: its id and what's wrong, each with the file and the place in it. */
class CultureError(val id: String, val problems: List<String>) :
    Exception("culture pack \"$id\": " + problems.joinToString("\n  ", prefix = if (problems.size > 1) "\n  " else ""))

/**
 * The culture packs the app bundles (companion/cultures/<id>/, copied into the app's resources as cultures/<id>/ by
 * app/build.gradle.kts, with cultures/index.json listing them), and the one the village is in: [current], the learner's
 * (the bridge says which, GET /culture; see CultureSetting), else [DEFAULT]. A pack is read and checked once: the
 * shape of each file, the pack's language in every text, the arguments of every message, and what its lists name.
 */
object Cultures {
    const val SCHEMA = "lani.culture/v0"
    const val DEFAULT = "primorska"

    /** The files of a complete pack besides culture.json, in the order they're read. */
    val FILES = listOf("world", "quests", "festivals", "surprises", "chest", "projects", "events", "people", "chronicle")

    private val json = Json { ignoreUnknownKeys = false; explicitNulls = false }
    private val loaded = ConcurrentHashMap<String, Culture>()

    @Volatile
    private var active: Culture? = null

    /** Told when a pack asked for can't be used ([use] falls back to the default); the app logs it. */
    @Volatile
    var onProblem: ((CultureError) -> Unit)? = null

    /**
     * While visiting another town (plan 2, §3; [si.lanisce.lani.data.VisitWorld]): its culture, shown instead of the
     * village's own until the visit ends, whatever sets the own one meanwhile ([use]); null at home.
     */
    @Volatile
    var visiting: Culture? = null

    /** The culture on screen now: a visited town's, else the village's own. */
    val current: Culture get() = visiting ?: active ?: default().also { active = it }

    /** The village's own culture, also during a visit (what its chest's goods are). */
    val home: Culture get() = active ?: default().also { active = it }

    fun default(): Culture = load(DEFAULT)

    /** The bundled packs' ids (a stub among them, too). */
    val ids: List<String> by lazy {
        resource("index.json")?.let { json.decodeFromString(ListSerializer(String.serializer()), it) }.orEmpty()
    }

    /** [id]'s manifest, whether complete or a stub; null when there's no such pack. */
    fun manifest(id: String): Manifest? = resource("$id/culture.json")?.let { runCatching { json.decodeFromString(Manifest.serializer(), it) }.getOrNull() }

    /** The bundled pack [id], read and checked once; throws [CultureError] when it's missing, a stub or broken. */
    fun load(id: String): Culture = loaded[id] ?: parse(id) { name -> resource("$id/$name") }.also { loaded[id] = it }

    /**
     * The village is in [id]'s culture from now on. A pack that can't be used (unknown, a stub, broken) leaves the default,
     * and [onProblem] hears why. Returns the culture in use.
     */
    fun use(id: String?): Culture {
        val c = id?.takeIf { it != DEFAULT }?.let {
            try {
                load(it)
            } catch (e: CultureError) {
                onProblem?.invoke(e)
                null
            }
        } ?: default()
        active = c
        return c
    }

    /** Tests: the village is in [c]'s culture. */
    fun use(c: Culture) {
        active = c
    }

    /** A bundled file of the packs, said to the learner of this phone ({learner}, {m:…|f:…}: l10n/Learner.kt). */
    private fun resource(path: String): String? =
        Cultures::class.java.getResourceAsStream("/cultures/$path")?.use { Learner.current.renderJson(it.readBytes().decodeToString()) }

    /**
     * The learner the content speaks to changed ([Learner.current]): every pack is read again for them, the village's at
     * the next [use] (or [current]).
     */
    fun forget() {
        loaded.clear()
        libraries.clear()
        skies.clear()
        reactionFiles.clear()
        active = null
    }

    /**
     * Reads pack [id] from [read] (a file name → its text, null when missing) and checks it. Throws [CultureError] with
     * every problem found, each with its file and where in it.
     */
    fun parse(id: String, read: (String) -> String?): Culture {
        val problems = ArrayList<String>()
        // said to the learner of this phone: {learner} and {m:…|f:…} rendered before anything reads a text
        val said: (String) -> String? = { name -> read(name)?.let(Learner.current::renderJson) }
        fun <T> file(name: String, s: KSerializer<T>): T? {
            val raw = said("$name.json") ?: return null.also { problems += "$id/$name.json: missing" }
            return try {
                json.decodeFromString(s, raw)
            } catch (e: SerializationException) {
                null.also { problems += "$id/$name.json: ${e.message?.lines()?.first()}" }
            } catch (e: IllegalArgumentException) {
                null.also { problems += "$id/$name.json: ${e.message?.lines()?.first()}" }
            }
        }
        val m = file("culture", Manifest.serializer()) ?: throw CultureError(id, problems)
        if (!Schema.matches(m.schema, SCHEMA)) problems += "$id/culture.json schema: \"${m.schema}\", not \"$SCHEMA\""
        if (m.id != id) problems += "$id/culture.json id: \"${m.id}\", but the pack is \"$id\""
        if (Lang.of(m.language)?.code != m.language) problems += "$id/culture.json language: \"${m.language}\" isn't one of the app's (${Lang.entries.joinToString { it.code }})"
        if (m.status !in listOf("complete", "stub")) problems += "$id/culture.json status: \"${m.status}\", not complete or stub"
        if (m.status == "stub") problems += "$id: a stub (only its manifest): nothing to play yet"
        if (problems.isNotEmpty()) throw CultureError(id, problems)

        val world = file("world", WorldFile.serializer())
        val quests = file("quests", QuestsFile.serializer())
        val festivals = file("festivals", FestivalsFile.serializer())
        val surprises = file("surprises", SurprisesFile.serializer())
        val chest = file("chest", ChestFile.serializer())
        val projects = file("projects", ProjectsFile.serializer())
        val events = file("events", EventsFile.serializer())
        val people = file("people", PeopleFile.serializer())
        val chronicle = file("chronicle", ChronicleFile.serializer())
        if (problems.isNotEmpty()) throw CultureError(id, problems)
        // what the chest's things hold to read (readings/<id>.json), each read once
        val readings = readingIds(chest!!).mapNotNull { r -> file("readings/$r", ReadingFile.serializer())?.let { r to it } }.toMap()
        if (problems.isNotEmpty()) throw CultureError(id, problems)

        val c = Check(id, m.language)
        c.manifest(m)
        c.world(world!!)
        c.quests(quests!!)
        c.chest(chest)
        for ((r, reading) in readings) c.reading(r, reading)
        c.festivals(festivals!!, chest)
        c.surprises(surprises!!, chest)
        c.projects(projects!!)
        c.events(events!!)
        c.people(people!!)
        c.chronicle(chronicle!!)
        if (c.problems.isNotEmpty()) throw CultureError(id, c.problems)
        return Culture(
            m, world, quests, festivals, surprises, chest, projects, events, people, chronicle, readings, arrivals(id, m.language, said),
            projectSteps(id, m.language, projects.projects.map { it.id }, said), celebrations(id, m.language, said),
        )
    }

    /**
     * How pack [id] celebrates its ages (celebrations/<age>.json, optional: an age without one has the generic gathering,
     * [si.lanisce.lani.game.AgeUps]), read leniently: their dialogs are read in the learner's pair when the party is played,
     * and the bridge checks the files (celebrations.ts). One in another language than the pack's, for another age, or that
     * can't be read, is left out ([onProblem] hears why).
     */
    private fun celebrations(id: String, language: String, read: (String) -> String?): Map<Age, CelebrationFile> =
        Age.entries.drop(1).mapNotNull { a ->
            val name = a.name.lowercase()
            val raw = read("celebrations/$name.json") ?: return@mapNotNull null
            val f = runCatching { si.lanisce.lani.data.json.decodeFromString(CelebrationFile.serializer(), raw) }
                .onFailure { onProblem?.invoke(CultureError(id, listOf("$id/celebrations/$name.json: ${it.message?.lines()?.first()}"))) }.getOrNull()
                ?: return@mapNotNull null
            val wrong = when {
                !Schema.matches(f.schema, CELEBRATION_SCHEMA) -> "schema \"${f.schema}\", not \"$CELEBRATION_SCHEMA\""
                f.age != name -> "age \"${f.age}\", but the file is $name.json"
                f.language != language -> "language \"${f.language}\", not the pack's \"$language\""
                else -> null
            }
            if (wrong != null) null.also { onProblem?.invoke(CultureError(id, listOf("$id/celebrations/$name.json: $wrong"))) } else a to f
        }.toMap()

    /** The schema of celebrations/<age>.json. */
    const val CELEBRATION_SCHEMA = "lani.celebration/v0"

    /**
     * Pack [id]'s projects' steps as short scenes (project-steps/<project>.json, optional: a project without one plays its
     * steps as the practice it always had), read leniently: their dialogs are read in the learner's pair when one is
     * played (game/Projects.kt), and the bridge checks the files (project-steps.ts). One in another language than the
     * pack's, for another project, or that can't be read, is left out ([onProblem] hears why).
     */
    private fun projectSteps(id: String, language: String, projects: List<String>, read: (String) -> String?): Map<String, ProjectStepsFile> =
        projects.mapNotNull { p ->
            val raw = read("project-steps/$p.json") ?: return@mapNotNull null
            val f = runCatching { si.lanisce.lani.data.json.decodeFromString(ProjectStepsFile.serializer(), raw) }
                .onFailure { onProblem?.invoke(CultureError(id, listOf("$id/project-steps/$p.json: ${it.message?.lines()?.first()}"))) }.getOrNull()
                ?: return@mapNotNull null
            val wrong = when {
                !Schema.matches(f.schema, PROJECT_STEPS_SCHEMA) -> "schema \"${f.schema}\", not \"$PROJECT_STEPS_SCHEMA\""
                f.project != p -> "project \"${f.project}\", but the file is $p.json"
                f.language != language -> "language \"${f.language}\", not the pack's \"$language\""
                else -> null
            }
            if (wrong != null) null.also { onProblem?.invoke(CultureError(id, listOf("$id/project-steps/$p.json: $wrong"))) } else p to f
        }.toMap()

    /** The schema of project-steps/<project>.json. */
    const val PROJECT_STEPS_SCHEMA = "lani.project-steps/v0"

    /**
     * How pack [id]'s people are introduced (arrivals.json, optional: a pack without it introduces nobody), read leniently:
     * its dialogs are read in the learner's pair when one is played (game/villagers/Arrivals.kt), and the bridge checks
     * the file (arrivals.ts). One in another language than the pack's, or that can't be read, is left out.
     */
    private fun arrivals(id: String, language: String, read: (String) -> String?): si.lanisce.lani.game.villagers.ArrivalsFile? {
        val raw = read("arrivals.json") ?: return null
        val a = runCatching { si.lanisce.lani.data.json.decodeFromString(si.lanisce.lani.game.villagers.ArrivalsFile.serializer(), raw) }
            .onFailure { onProblem?.invoke(CultureError(id, listOf("$id/arrivals.json: ${it.message?.lines()?.first()}"))) }.getOrNull()
        return a?.takeIf { it.language == language && it.templates.containsKey("newcomer") && it.templates.containsKey("birth") }
    }

    /** The sky files read so far, by pack id; a pack without one (or with one that can't be used) has none. */
    private val skies = ConcurrentHashMap<String, java.util.Optional<SkyFile>>()

    /** The schema of sky.json. */
    const val SKY_SCHEMA = "lani.sky/v0"

    /**
     * Pack [id]'s night sky (sky.json, optional; companion/GAME.md "The night sky"): where its village lies under the sky
     * and what its people call the moon, the stars and the planets. Read and checked once; null for a pack without one
     * (its village has the default place and the catalog's names) or one that can't be used ([onProblem] hears why).
     */
    fun sky(id: String): SkyFile? = skies.getOrPut(id) {
        val raw = resource("$id/sky.json") ?: return@getOrPut java.util.Optional.empty()
        val problems = ArrayList<String>()
        val sky = try {
            json.decodeFromString(SkyFile.serializer(), raw)
        } catch (e: SerializationException) {
            null.also { problems += "$id/sky.json: ${e.message?.lines()?.first()}" }
        } catch (e: IllegalArgumentException) {
            null.also { problems += "$id/sky.json: ${e.message?.lines()?.first()}" }
        }
        if (sky != null) problems += checkSky(id, sky)
        if (problems.isNotEmpty()) onProblem?.invoke(CultureError(id, problems))
        java.util.Optional.ofNullable(sky.takeIf { problems.isEmpty() })
    }.orElse(null)

    /** The reactions files read so far, by pack id; a pack without one (or with one that can't be used) has none. */
    private val reactionFiles = ConcurrentHashMap<String, java.util.Optional<ReactionsFile>>()

    /** The schema of reactions.json. */
    const val REACTIONS_SCHEMA = "lani.reactions/v0"

    /**
     * What pack [id]'s people say while an event is on or just over (reactions.json, optional; companion/GAME.md "People
     * react"). Read and checked once; null for a pack without one or one that can't be used ([onProblem] hears why).
     */
    fun reactions(id: String): ReactionsFile? = reactionFiles.getOrPut(id) {
        val raw = resource("$id/reactions.json") ?: return@getOrPut java.util.Optional.empty()
        val problems = ArrayList<String>()
        val r = try {
            json.decodeFromString(ReactionsFile.serializer(), raw)
        } catch (e: SerializationException) {
            null.also { problems += "$id/reactions.json: ${e.message?.lines()?.first()}" }
        } catch (e: IllegalArgumentException) {
            null.also { problems += "$id/reactions.json: ${e.message?.lines()?.first()}" }
        }
        if (r != null) problems += checkReactions(id, r)
        if (problems.isNotEmpty()) onProblem?.invoke(CultureError(id, problems))
        java.util.Optional.ofNullable(r.takeIf { problems.isEmpty() })
    }.orElse(null)

    /** What the people of the village on screen say in its events: the current culture's ([reactions]). */
    val currentReactions: ReactionsFile? get() = reactions(current.id)

    /**
     * The problems of [r], pack [id]'s reactions.json: its schema and language, every text in the pack's language with no
     * arguments, every call short (a word or three: it's a button) and every shout too (a word or two over a head).
     */
    fun checkReactions(id: String, r: ReactionsFile): List<String> {
        val lang = manifest(id)?.language ?: return listOf("$id: no such culture pack")
        val c = Check(id, lang)
        c.reactions(r)
        return c.problems
    }

    /** The sky of the village on screen: the current culture's ([sky]). */
    val currentSky: SkyFile? get() = sky(current.id)

    /** Tests: pack [id]'s sky is [s] (null: read from its file again). */
    internal fun useSky(id: String, s: SkyFile?) {
        if (s == null) skies.remove(id) else skies[id] = java.util.Optional.of(s)
    }

    /**
     * The problems of [s], pack [id]'s sky.json: its schema and language, every text in the pack's language, the phases
     * and the things and showers it names (the app's: [si.lanisce.lani.game.sky.Stars.figures], the Pleiades, a star
     * with a proper name, the planets, the Milky Way), the levels of its lore, the reading it names (one of the pack's).
     */
    fun checkSky(id: String, s: SkyFile): List<String> {
        val lang = manifest(id)?.language ?: return listOf("$id: no such culture pack")
        val c = Check(id, lang)
        c.sky(s, library(id).map { it.id }.toSet())
        return c.problems
    }

    private val libraries = ConcurrentHashMap<String, List<ReadingFile>>()

    /**
     * Every reading pack [id] bundles (readings/, listed in readings/index.json by app/build.gradle.kts): what the chest's
     * things hold and the reading corner's others, each read and checked once. A reading with problems is left out, and
     * [onProblem] hears why.
     */
    fun library(id: String): List<ReadingFile> = libraries.getOrPut(id) {
        val ids = resource("$id/readings/index.json")?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() }.orEmpty()
        val lang = manifest(id)?.language ?: return@getOrPut emptyList()
        val problems = ArrayList<String>()
        val out = ids.mapNotNull { r ->
            val raw = resource("$id/readings/$r.json") ?: return@mapNotNull null.also { problems += "$id/readings/$r.json: missing" }
            val e = try {
                json.decodeFromString(ReadingFile.serializer(), raw)
            } catch (x: SerializationException) {
                return@mapNotNull null.also { problems += "$id/readings/$r.json: ${x.message?.lines()?.first()}" }
            } catch (x: IllegalArgumentException) {
                return@mapNotNull null.also { problems += "$id/readings/$r.json: ${x.message?.lines()?.first()}" }
            }
            val c = Check(id, lang)
            c.reading(r, e)
            if (c.problems.isEmpty()) e else null.also { problems += c.problems }
        }
        if (problems.isNotEmpty()) onProblem?.invoke(CultureError(id, problems))
        out
    }

    /**
     * Checks [e], a reading served for pack [id]'s village (the tutor's, or the culture pack's newer than the app): its
     * problems, none when it can be read (the same checks as a bundled one's, [library]).
     */
    fun check(id: String, e: ReadingFile): List<String> {
        val lang = manifest(id)?.language ?: return listOf("$id: no such culture pack")
        return Check(id, lang).apply { reading(e.id, e) }.problems
    }

    /** The readings [chest]'s tools and goods hold (their `read`), each once, in the file's order. */
    fun readingIds(chest: ChestFile): List<String> =
        (chest.tools.flatMap { listOfNotNull(it.first.read, it.better.read) } + chest.goods.mapNotNull { it.read }).distinct()

    /** What the shape alone can't say: the pack's language in every text, the messages' arguments, what the lists name. */
    private class Check(val id: String, val lang: String) {
        val problems = ArrayList<String>()
        private var file = ""

        private fun fail(where: String, what: String) {
            problems += "$id/$file.json $where: $what"
        }

        /** [t] (at [where]) in the pack's language unless [anyLanguage], each message parsing and using only [args]. */
        fun text(where: String, t: Text?, vararg args: String, anyLanguage: Boolean = false) {
            if (t == null) return
            if (!anyLanguage && lang !in t.by) fail(where, "no \"$lang\" text (the pack's language)")
            for ((l, s) in t.by) {
                if (!Regex("[a-z]{2,3}").matches(l)) fail(where, "\"$l\" isn't a language code (sl, en, it …)")
                if (s.isBlank()) fail("$where.$l", "empty")
                val msg = try {
                    Message.parse(s)
                } catch (e: IllegalArgumentException) {
                    fail("$where.$l", e.message ?: "doesn't parse")
                    continue
                }
                val extra = msg.args - args.toSet()
                if (extra.isNotEmpty()) {
                    fail("$where.$l", "uses {${extra.joinToString("}, {")}}, which it isn't given" + if (args.isEmpty()) " (it gets none)" else " (it gets {${args.joinToString("}, {")}})")
                }
            }
        }

        private fun nonEmpty(where: String, list: Collection<*>) {
            if (list.isEmpty()) fail(where, "empty")
        }

        private fun unique(where: String, ids: List<String>) {
            val twice = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (twice.isNotEmpty()) fail(where, "twice: ${twice.joinToString()}")
        }

        private fun options(where: String, options: List<String>) {
            if (options.size < 2) fail(where, "needs the answer and at least one other option")
            if (options.toSet().size != options.size) fail(where, "an option twice")
            if (options.any { it.isBlank() }) fail(where, "an empty option")
        }

        fun sky(s: SkyFile, readings: Set<String>) {
            file = "sky"
            if (!Schema.matches(s.schema, SKY_SCHEMA)) fail("schema", "\"${s.schema}\", not \"$SKY_SCHEMA\"")
            if (s.language != lang) fail("language", "\"${s.language}\", not the pack's \"$lang\"")
            if (s.where.lat !in -90.0..90.0 || s.where.lon !in -180.0..180.0) fail("where", "not a place on the earth (lat ${s.where.lat}, lon ${s.where.lon})")
            text("where.name", s.where.name)
            val levels = si.lanisce.lani.game.Readings.LEVELS
            val phases = si.lanisce.lani.game.sky.SkyCards.PHASES
            fun lore(where: String, list: List<SkyLore>, phased: Boolean = false) {
                list.forEachIndexed { i, l ->
                    if (l.level !in levels) fail("$where[$i].level", "\"${l.level}\", not one of ${levels.joinToString()}")
                    if (l.phase != null && (!phased || l.phase !in phases)) fail("$where[$i].phase", "\"${l.phase}\"" + if (phased) ", not one of ${phases.joinToString()}" else ": only the moon's lore has phases")
                    text("$where[$i].text", l.text)
                }
            }
            fun words(where: String, list: List<String>) {
                if (list.any { it.isBlank() }) fail(where, "an empty word")
                unique(where, list)
            }
            val m = s.moon
            text("moon.name", m.name)
            val missing = phases - m.phases.keys
            val extra = m.phases.keys - phases.toSet()
            if (missing.isNotEmpty()) fail("moon.phases", "missing ${missing.joinToString()}")
            if (extra.isNotEmpty()) fail("moon.phases", "not a phase: ${extra.joinToString()} (the phases are ${phases.joinToString()})")
            for ((k, t) in m.phases) text("moon.phases.$k", t)
            text("moon.waxing", m.waxing)
            text("moon.waning", m.waning)
            lore("moon.lore", m.lore, phased = true)
            if (m.reading != null && m.reading !in readings) fail("moon.reading", "no reading \"${m.reading}\" in $id/readings")
            if (s.pack != null && si.lanisce.lani.game.FestivalPacks.pack(s.pack) == null) fail("pack", "no word pack \"${s.pack}\" in $id/packs")
            words("moon.words", m.words)
            val known = si.lanisce.lani.game.sky.SkyCards.thingIds
            unique("things", s.things.map { it.id })
            s.things.forEachIndexed { i, t ->
                if (t.id !in known) fail("things[$i].id", "\"${t.id}\", not a thing of the sky the app draws (${known.joinToString()})")
                text("things[$i].name", t.name)
                text("things[$i].folk", t.folk)
                text("things[$i].morning", t.morning)
                text("things[$i].evening", t.evening)
                lore("things[$i].lore", t.lore)
                words("things[$i].words", t.words)
            }
            s.meteors?.let { me ->
                text("meteors.name", me.name)
                text("meteors.wish", me.wish)
                lore("meteors.lore", me.lore)
                words("meteors.words", me.words)
                unique("meteors.showers", me.showers.map { it.id })
                me.showers.forEachIndexed { i, sh ->
                    if (si.lanisce.lani.game.sky.Meteors.shower(sh.id) == null) fail("meteors.showers[$i].id", "\"${sh.id}\", not one of ${si.lanisce.lani.game.sky.Meteors.SHOWERS.joinToString { it.id }}")
                    text("meteors.showers[$i].name", sh.name)
                    text("meteors.showers[$i].folk", sh.folk)
                    text("meteors.showers[$i].tonight", sh.tonight)
                }
            }
        }

        fun manifest(m: Manifest) {
            file = "culture"
            text("region", m.region)
            text("name", m.name)
            text("about", m.about, anyLanguage = true)
        }

        fun world(w: WorldFile) {
            file = "world"
            if (w.host.name.isBlank()) fail("host.name", "empty")
            fun <E : Enum<E>> names(what: String, map: Map<String, Named>, entries: List<E>) {
                val want = entries.map { it.name.lowercase() }
                val missing = want - map.keys
                val extra = map.keys - want.toSet()
                if (missing.isNotEmpty()) fail(what, "missing ${missing.joinToString()}")
                if (extra.isNotEmpty()) fail(what, "not the game's: ${extra.joinToString()} (it has ${want.joinToString()})")
                for ((k, n) in map) {
                    text("$what.$k.name", n.name)
                    text("$what.$k.partitive", n.partitive)
                }
            }
            names("resources", w.resources, Res.entries)
            names("ages", w.ages, Age.entries)
            names("buildings", w.buildings, BuildingType.entries)
            names("events", w.events, EventKind.entries)
            unique("places", w.places.map { it.id })
            w.places.forEachIndexed { i, p ->
                text("places[$i].name", p.name)
                text("places[$i].about", p.about, anyLanguage = true)
            }
            w.backdrop?.let { b ->
                if (b.emoji.isBlank()) fail("backdrop.emoji", "empty")
                text("backdrop.name", b.name)
                text("backdrop.where", b.where)
                text("backdrop.about", b.about, anyLanguage = true)
            }
            for ((id, s) in w.spots) spot("spots.$id", id, s)
        }

        /**
         * A spot's texts ([SpotTexts]): a spot the game has, in the pack's language; a story at levels there are; someone
         * who is there now and then drawn with a sprite the game has, at parts of the day there are, with a talk at a level.
         */
        private fun spot(where: String, id: String, s: SpotTexts) {
            if (id !in si.lanisce.lani.game.scene.TownSpots.ids) fail(where, "not a spot of the game's (it has ${si.lanisce.lani.game.scene.TownSpots.ids.joinToString()})")
            if (s.emoji?.isBlank() == true) fail("$where.emoji", "empty")
            text("$where.name", s.name)
            text("$where.where", s.where)
            text("$where.about", s.about)
            for ((lv, t) in s.story) {
                if (lv !in si.lanisce.lani.game.villagers.Arrivals.LEVELS) fail("$where.story.$lv", "not a level (${si.lanisce.lani.game.villagers.Arrivals.LEVELS.joinToString()})")
                text("$where.story.$lv", t)
            }
            if (s.words.isNotEmpty() && s.pack == null) fail("$where.words", "words without their pack")
            val k = s.keeper ?: return
            val at = "$where.keeper"
            if (k.id.isBlank() || k.name.isBlank() || k.emoji.isBlank()) fail(at, "needs an id, a name and an emoji")
            if (k.art !in si.lanisce.lani.game.scene.SceneArt.people) fail("$at.art", "\"${k.art}\" isn't a people sprite")
            if (k.voice !in listOf("female", "male")) fail("$at.voice", "\"${k.voice}\", not female or male")
            val times = si.lanisce.lani.game.scene.TimeOfDay.entries.map { it.name.lowercase() }
            k.times.filter { it !in times }.forEach { fail("$at.when", "\"$it\" isn't a part of the day (${times.joinToString()})") }
            if (k.chance !in 0f..1f) fail("$at.chance", "${k.chance}, not 0 to 1")
            k.reward.keys.filter { it !in skills }.forEach { fail("$at.reward", "\"$it\", not one of ${skills.joinToString()}") }
            text("$at.role", k.role)
            text("$at.title", k.title)
            levels("$at.levels", k.levels)
            // the story he tells across visits: each talk once, at a level, each telling too
            unique("$at.talks", k.talks.map { it.id })
            k.talks.forEachIndexed { i, t ->
                if (t.id.isBlank()) fail("$at.talks[$i].id", "empty")
                text("$at.talks[$i].title", t.title)
                levels("$at.talks[$i].levels", t.levels)
                t.again.forEachIndexed { j, g -> levels("$at.talks[$i].again[$j].levels", g.levels) }
            }
            // his own story, a part at a time: each once, after as many evenings as the one before or more
            unique("$at.tales", k.tales.map { it.id })
            k.tales.forEachIndexed { i, t ->
                if (t.after < 0) fail("$at.tales[$i].after", "${t.after}, not 0 or more")
                if (i > 0 && t.after < k.tales[i - 1].after) fail("$at.tales[$i].after", "${t.after}: before the part ahead of it (${k.tales[i - 1].after})")
                text("$at.tales[$i].title", t.title)
                levels("$at.tales[$i].levels", t.levels)
            }
            s.cycle?.let { cycle("$where.cycle", it) }
        }

        /**
         * A spot's cycle ([SpotCycle]): its season two days of the year, the first before the last; its stages each once, as
         * long as a stage can be (1 to 60 days, the least first), drawn as the app can, the smoke a colour there is; at most
         * one stage that delivers, to someone the delivery names; the breakout's and the nights' stages its own; every talk
         * at a level, each text in the pack's language.
         */
        private fun cycle(where: String, c: SpotCycle) {
            val from = StandsEntry.monthDay(c.season.from)
            val until = StandsEntry.monthDay(c.season.until)
            if (from == null) fail("$where.season.from", "\"${c.season.from}\" isn't a day of the year (MM-DD)")
            if (until == null) fail("$where.season.until", "\"${c.season.until}\" isn't a day of the year (MM-DD)")
            if (from != null && until != null && !until.isAfter(from)) fail("$where.season", "until ${c.season.until} isn't after from ${c.season.from}")
            fun range(at: String, r: List<Int>, least: Int, most: Int) {
                if (r.size !in 1..2 || r.any { it !in least..most } || r.size == 2 && r[1] < r[0]) fail(at, "$r: [least, most] days, from $least to $most")
            }
            range("$where.start", c.start, 0, 90)
            range("$where.pause", c.pause, 0, 90)
            if (c.stages.isEmpty()) fail("$where.stages", "no stage")
            unique("$where.stages", c.stages.map { it.id })
            val ids = c.stages.map { it.id }.toSet()
            fun stage(at: String, st: CycleStage, ranged: Boolean) {
                if (st.id.isBlank()) fail("$at.id", "empty")
                if (ranged) range("$at.days", st.days, 1, 60)
                if (st.art !in si.lanisce.lani.game.scene.SpotCycles.ARTS) fail("$at.art", "\"${st.art}\" isn't how the app draws a spot (${si.lanisce.lani.game.scene.SpotCycles.ARTS.joinToString()})")
                if (st.smoke != null && st.smoke !in si.lanisce.lani.game.scene.SpotCycles.SMOKES) fail("$at.smoke", "\"${st.smoke}\", not ${si.lanisce.lani.game.scene.SpotCycles.SMOKES.joinToString()}")
                if (st.chance != null && st.chance !in 0f..1f) fail("$at.chance", "${st.chance}, not 0 to 1")
                val times = si.lanisce.lani.game.scene.TimeOfDay.entries.map { it.name.lowercase() }
                st.times?.filter { it !in times }?.forEach { fail("$at.when", "\"$it\" isn't a part of the day") }
                text("$at.title", st.title)
                text("$at.about", st.about)
                st.done?.let { levels("$at.done", it) }
                talks("$at.talks", st.talks)
            }
            c.stages.forEachIndexed { i, st -> stage("$where.stages[$i]", st, ranged = true) }
            if (c.stages.count { it.delivers } > 1) fail("$where.stages", "more than one stage delivers")
            c.rest?.let { stage("$where.rest", it, ranged = false) }
            c.winter?.let { stage("$where.winter", it, ranged = false) }
            c.lost?.let { stage("$where.lost", it, ranged = false) }
            c.waiting?.let { stage("$where.waiting", it, ranged = false) }
            if (c.stages.any { it.wood } && c.delivery == null) fail("$where.stages", "a stage takes the village's wood (\"wood\": true), but nothing is delivered for it (\"delivery\")")
            c.meeting?.let { levels("$where.meeting", it) }
            talks("$where.greet", c.greet)
            c.missed?.let { levels("$where.missed", it) }
            c.breakout?.let { b ->
                b.stages.filter { it !in ids }.forEach { fail("$where.breakout.stages", "\"$it\" isn't a stage of the cycle") }
                if (b.chance !in 0f..1f) fail("$where.breakout.chance", "${b.chance}, not 0 to 1")
                b.seconds.forEach { (lv, n) -> if (lv !in si.lanisce.lani.game.villagers.Arrivals.LEVELS || n !in 5..120) fail("$where.breakout.seconds.$lv", "$n: a level's seconds, 5 to 120") }
                text("$where.breakout.title", b.title)
                levels("$where.breakout.levels", b.levels)
                levels("$where.breakout.saved", b.saved)
                levels("$where.breakout.lost", b.lost)
                b.missed?.let { levels("$where.breakout.missed", it) }
                text("$where.breakout.covered", b.covered)
                text("$where.breakout.burnt", b.burnt)
            }
            unique("$where.nights", c.nights.map { it.id })
            c.nights.forEachIndexed { i, n ->
                n.stages.filter { it !in ids }.forEach { fail("$where.nights[$i].stages", "\"$it\" isn't a stage of the cycle") }
                if (n.chance !in 0f..1f) fail("$where.nights[$i].chance", "${n.chance}, not 0 to 1")
                levels("$where.nights[$i].told", n.told)
            }
            c.delivery?.let { d ->
                if (d.to.isBlank()) fail("$where.delivery.to", "empty")
                if (d.days !in 1..90) fail("$where.delivery.days", "${d.days}, not 1 to 90")
                if (c.stages.none { it.delivers }) fail("$where.delivery", "no stage delivers (\"delivers\": true)")
                text("$where.delivery.line", d.line)
                d.says.forEachIndexed { i, t -> text("$where.delivery.says[$i]", t) }
                d.waits.forEachIndexed { i, t -> text("$where.delivery.waits[$i]", t) }
                d.cold.forEachIndexed { i, t -> text("$where.delivery.cold[$i]", t) }
            }
        }

        /** A cycle's talks ([CycleTalk]): each at a level, at parts of the day there are, for a weather there is. */
        private fun talks(where: String, list: List<si.lanisce.lani.game.culture.CycleTalk>) {
            val times = si.lanisce.lani.game.scene.TimeOfDay.entries.map { it.name.lowercase() }
            list.forEachIndexed { i, t ->
                t.times.filter { it !in times }.forEach { fail("$where[$i].when", "\"$it\" isn't a part of the day") }
                if (t.sky != null && t.sky !in si.lanisce.lani.game.scene.SpotCycles.SKIES) fail("$where[$i].sky", "\"${t.sky}\", not ${si.lanisce.lani.game.scene.SpotCycles.SKIES.joinToString()}")
                levels("$where[$i].levels", t.levels)
            }
        }

        /** A keeper's talk by level ([where]): at a level at least, each a level there is, with its lines. */
        private fun levels(where: String, levels: kotlinx.serialization.json.JsonObject) {
            if (levels.isEmpty()) fail(where, "no level: {\"A1\": {\"lines\": […]}}")
            for ((lv, v) in levels) {
                if (lv !in si.lanisce.lani.game.villagers.Arrivals.LEVELS) fail("$where.$lv", "not a level")
                else if (((v as? kotlinx.serialization.json.JsonObject)?.get("lines") as? kotlinx.serialization.json.JsonArray).isNullOrEmpty()) fail("$where.$lv.lines", "no lines")
            }
        }

        private val skills = Res.entries.map { it.name.lowercase() }

        fun quests(q: QuestsFile) {
            file = "quests"
            nonEmpty("requests", q.requests)
            q.requests.forEachIndexed { i, r ->
                if (r.giver.isBlank()) fail("requests[$i].giver", "empty")
                if (r.skill !in skills) fail("requests[$i].skill", "\"${r.skill}\", not one of ${skills.joinToString()}")
                text("requests[$i].title", r.title)
                text("requests[$i].story", r.story)
                text("requests[$i].memory", r.memory)
            }
            text("lines.asks", q.lines.asks, "giver")
            text("lines.expired", q.lines.expired, "giver")
            text("lines.thanked", q.lines.thanked, "giver")
            text("lines.try_again", q.lines.tryAgain, "giver")
            text("lines.thanks_good", q.lines.thanksGood, "name", "item")
            q.tentMove?.let { t ->
                if (t.giver.isBlank()) fail("tent_move.giver", "empty")
                if (t.skill !in skills) fail("tent_move.skill", "\"${t.skill}\", not one of ${skills.joinToString()}")
                for ((k, v) in listOf("title" to t.title, "story" to t.story, "memory" to t.memory, "said" to t.said, "listen" to t.listen, "read" to t.read, "moved" to t.moved)) text("tent_move.$k", v)
                text("tent_move.heard", t.heard, "said")
                nonEmpty("tent_move.questions", t.questions)
                t.questions.forEachIndexed { i, e ->
                    text("tent_move.questions[$i].ask", e.ask)
                    text("tent_move.questions[$i].explain", e.explain)
                    options("tent_move.questions[$i].options", e.options)
                }
            }
        }

        private lateinit var goods: Set<String>

        private fun good(where: String, id: String?) {
            if (id != null && id !in goods) fail(where, "no good \"$id\" in chest.json")
        }

        fun chest(c: ChestFile) {
            file = "chest"
            goods = c.goods.map { it.id }.toSet()
            nonEmpty("goods", c.goods)
            unique("goods", c.goods.map { it.id })
            c.goods.forEachIndexed { i, g ->
                text("goods[$i].name", g.name)
                text("goods[$i].acc", g.acc)
                text("goods[$i].the", g.the)
                if (g.price !in Catalog.GOOD_PRICES) fail("goods[$i].price", "\"${g.price}\", not one of ${Catalog.GOOD_PRICES.keys.joinToString()}")
            }
            unique("tools", c.tools.map { it.giver })
            c.tools.forEachIndexed { i, t ->
                if (t.effect !in Catalog.TOOL_EFFECTS) fail("tools[$i].effect", "\"${t.effect}\", not one of ${Catalog.TOOL_EFFECTS.keys.joinToString()}")
                for ((tier, item) in listOf("first" to t.first, "better" to t.better)) {
                    text("tools[$i].$tier.name", item.name)
                    text("tools[$i].$tier.acc", item.acc)
                    text("tools[$i].$tier.the", item.the)
                }
            }
            for ((who, g) in c.thanks) good("thanks.$who", g)
            good("thanks_default", c.thanksDefault)
            thanksRotation(c)
            nonEmpty("small_gifts", c.smallGifts)
            c.smallGifts.forEachIndexed { i, g -> good("small_gifts[$i]", g) }
            for ((who, list) in c.likes) list.forEachIndexed { i, g -> good("likes.$who[$i]", g) }
            c.likesDefault.forEachIndexed { i, g -> good("likes_default[$i]", g) }
            text("smith.title", c.smith.title)
            val sellers = listOf("pedlar", "merchant", "market")
            if (sellers.any { it !in c.sellers }) fail("sellers", "needs ${sellers.joinToString()}")
            for ((k, s) in c.sellers) text("sellers.$k.name", s.name)
            text("lines.gift_tool", c.lines.giftTool, "name", "item", "emoji", "effect")
            text("lines.gift", c.lines.gift, "name", "item", "emoji")
            text("lines.given", c.lines.given, "name", "item")
            text("lines.bought", c.lines.bought, "good", "price")
            text("lines.sold", c.lines.sold, "good", "got", "res")
            text("lines.forged", c.lines.forged, "smith", "item", "stars", "effect")
            text("lines.feast", c.lines.feast, "served")
            text("lines.gift_memory", c.lines.giftMemory, "item")
        }

        /**
         * `thanks_rotation`: for someone who has a `thanks` good, 2 to 4 goods of the chest, that one first, none twice,
         * none rare (a rare one only in a festival's season); each seasonal good one of the chest (its festivals are
         * checked with festivals.json, [seasons]).
         */
        private fun thanksRotation(c: ChestFile) {
            val byId = c.goods.associateBy { it.id }
            for ((who, r) in c.thanksRotation) {
                val at = "thanks_rotation.$who"
                val first = c.thanks[who]
                if (first == null) fail(at, "\"$who\" has no thanks good (thanks.$who comes first in the rotation)")
                if (r.goods.size !in 2..4) fail("$at.goods", "2 to 4 goods, not ${r.goods.size}")
                else if (first != null && r.goods.first() != first) fail("$at.goods[0]", "\"${r.goods.first()}\", not their thanks good \"$first\"")
                unique("$at.goods", r.goods)
                r.goods.forEachIndexed { i, g ->
                    good("$at.goods[$i]", g)
                    if (byId[g]?.rare == true) fail("$at.goods[$i]", "\"$g\" is rare: only a seasonal thank-you may be")
                }
                for ((f, g) in r.seasonal) good("$at.seasonal.$f", g)
            }
        }

        /** The festivals [c]'s `thanks_rotation` gives a seasonal good for: each one of festivals.json [f]. */
        private fun seasons(f: FestivalsFile, c: ChestFile) {
            val ids = f.festivals.map { it.id }.toSet()
            val quiet = f.festivals.filter { it.quiet }.map { it.id }.toSet()
            for ((who, r) in c.thanksRotation) for (fid in r.seasonal.keys) {
                if (fid !in ids) fail("thanks_rotation.$who.seasonal", "no festival \"$fid\" in festivals.json")
                // a quiet day is no season for a festive thank-you
                if (fid in quiet) fail("thanks_rotation.$who.seasonal", "\"$fid\" is a quiet day: no seasonal good")
            }
        }

        /**
         * Reading [r] (readings/[r].json): named for its id, of a kind and level the game knows, every text in the pack's
         * language and in every language its title has (a translation of every line), a recipe with its servings,
         * ingredients and steps, the others with their lines (a proverb's meaning), and one to three questions of 3 or 4
         * options each.
         */
        fun reading(r: String, e: ReadingFile) {
            file = "readings/$r"
            if (e.id != r) fail("id", "\"${e.id}\", but the file is $r.json")
            if (e.kind !in si.lanisce.lani.game.Readings.KINDS) fail("kind", "\"${e.kind}\", not one of ${si.lanisce.lani.game.Readings.KINDS.joinToString()}")
            if (e.level !in si.lanisce.lani.game.Readings.LEVELS) fail("level", "\"${e.level}\", not one of ${si.lanisce.lani.game.Readings.LEVELS.joinToString()}")
            val langs = e.title.by.keys
            if (langs.none { it != lang }) fail("title", "no translation (a language besides \"$lang\")")
            fun line(where: String, t: Text?) {
                text(where, t)
                // the pack's language [text] asks for itself
                val missing = if (t == null) emptyList() else langs - t.by.keys - lang
                if (missing.isNotEmpty()) fail(where, "no \"${missing.joinToString("\", \"")}\" text (the title has it)")
            }
            line("title", e.title)
            line("intro", e.intro)
            line("servings", e.servings)
            e.ingredients.forEachIndexed { i, g ->
                line("ingredients[$i].part", g.part)
                line("ingredients[$i].unit", g.unit)
                line("ingredients[$i].item", g.item)
                if (g.amount != null && g.amount.isBlank()) fail("ingredients[$i].amount", "empty")
            }
            e.steps.forEachIndexed { i, t -> line("steps[$i]", t) }
            e.lines.forEachIndexed { i, l ->
                line("lines[$i].text", l.text)
                line("lines[$i].means", l.means)
                if (e.kind == "proverbs" && l.means == null) fail("lines[$i].means", "missing: what the proverb means")
                if (e.kind != "proverbs" && l.means != null) fail("lines[$i].means", "only a proverb has a meaning")
            }
            if (e.kind == "recipe") {
                if (e.servings == null) fail("servings", "missing: how much the recipe makes")
                nonEmpty("ingredients", e.ingredients)
                nonEmpty("steps", e.steps)
                if (e.lines.isNotEmpty()) fail("lines", "a recipe has ingredients and steps, not lines")
            } else {
                nonEmpty("lines", e.lines)
                if (e.servings != null || e.ingredients.isNotEmpty() || e.steps.isNotEmpty()) fail("steps", "only a recipe has servings, ingredients and steps")
            }
            if (e.questions.size !in 1..si.lanisce.lani.game.Readings.MAX_QUESTIONS) fail("questions", "${e.questions.size}, not 1 to ${si.lanisce.lani.game.Readings.MAX_QUESTIONS}")
            val all = si.lanisce.lani.game.Readings.texts(e, Lang.of(lang) ?: Lang.SL).joinToString("\n")
            e.questions.forEachIndexed { i, q ->
                line("questions[$i].ask", q.ask)
                line("questions[$i].explain", q.explain)
                if (q.type != null && q.type !in si.lanisce.lani.game.Readings.QUESTION_TYPES) {
                    fail("questions[$i].type", "\"${q.type}\", not one of ${si.lanisce.lani.game.Readings.QUESTION_TYPES.joinToString()}")
                }
                if (q.type == "true_false") {
                    if (q.answer == null) fail("questions[$i].answer", "missing: whether the statement holds")
                    if (q.options.isNotEmpty()) fail("questions[$i].options", "a true/false question has none")
                } else {
                    options("questions[$i].options", q.options)
                    if (q.options.size !in 3..4) fail("questions[$i].options", "${q.options.size}, not 3 or 4 (the answer first)")
                    if (q.answer != null) fail("questions[$i].answer", "only a true/false question has one")
                }
                if (q.type == "word" && q.word.isNullOrBlank()) fail("questions[$i].word", "missing: the word asked about")
                if (q.word != null && q.type != "word") fail("questions[$i].word", "only a word question names a word")
                if (q.word != null && !standsIn(q.word, all)) fail("questions[$i].word", "\"${q.word}\" isn't in the text")
            }
            if (e.words.size > si.lanisce.lani.game.Readings.MAX_WORDS) fail("words", "${e.words.size}, more than ${si.lanisce.lani.game.Readings.MAX_WORDS}")
            e.words.forEachIndexed { i, w ->
                text("words[$i].means", w.means, anyLanguage = true)
                val missing = langs - w.means.by.keys - lang
                if (missing.isNotEmpty()) fail("words[$i].means", "no \"${missing.joinToString("\", \"")}\" meaning (the title has it)")
                if (w.word.isBlank()) fail("words[$i].word", "empty")
                if (!standsIn(w.form ?: w.word, all)) fail("words[$i]", "\"${w.form ?: w.word}\" isn't in the text")
            }
        }

        /** [word] stands in [text] as a word of its own (any case). */
        private fun standsIn(word: String, text: String): Boolean =
            Regex("(^|[^\\p{L}])${Regex.escape(word.trim().lowercase())}($|[^\\p{L}])").containsMatchIn(text.lowercase())

        /** What people say (the villager format's lines) is in the pack's language too. */
        fun said(where: String, lines: VillagerLines) {
            val kinds = listOf(
                "greet" to lines.greet, "thanks" to lines.thanks, "remember" to lines.remember, "idle" to lines.idle,
                "cheer" to lines.cheer, "comfort" to lines.comfort, "listen" to lines.listen, "bye" to lines.bye,
            )
            for ((k, list) in kinds) list.forEachIndexed { i, l -> if (lang !in l.by) fail("$where.$k[$i]", "no \"$lang\" text (the pack's language)") }
        }

        fun festivals(f: FestivalsFile, c: ChestFile) {
            file = "festivals"
            unique("festivals", f.festivals.map { it.id })
            f.festivals.forEachIndexed { i, e ->
                val at = "festivals[$i]"
                val forms = listOfNotNull(e.date.easter?.let { "easter" }, e.date.last?.let { "last" }, e.date.day?.let { "day" })
                if (forms.size != 1) fail("$at.date", "one of {\"month\", \"day\"}, {\"easter\": days} or {\"month\", \"last\": weekday}")
                else try {
                    Culture.rule(e.date).date(2026)
                } catch (x: RuntimeException) {
                    fail("$at.date", x.message ?: "not a date")
                }
                if (e.date.month != null && e.date.month !in 1..12) fail("$at.date.month", "${e.date.month}, not 1 … 12")
                nonEmpty("$at.leaders", e.leaders)
                text("$at.name", e.name)
                text("$at.ask", e.ask)
                text("$at.about", e.about)
                text("$at.line", e.line)
                good("$at.good", e.good)
                // a quiet day is remembered, not celebrated: no festive good
                if (e.quiet && e.good != null) fail("$at.good", "a quiet day (quiet) brings no good")
                if (e.goods < 1) fail("$at.goods", "at least 1")
                if (e.words.isBlank()) fail("$at.words", "the id of its word pack")
            }
            text("lines.tag", f.lines.tag)
            text("lines.means", f.lines.means, "word")
            text("lines.say", f.lines.say, "meaning")
            text("lines.tried", f.lines.tried)
            // the chest's seasonal thank-you goods name these festivals
            file = "chest"
            seasons(f, c)
            file = "festivals"
        }

        fun surprises(s: SurprisesFile, c: ChestFile) {
            file = "surprises"
            fun kind(k: String, title: Text, intro: Text, ask: Text, vararg introArgs: String) {
                text("$k.title", title)
                text("$k.intro", intro, *introArgs)
                text("$k.ask", ask)
            }
            s.pedlar.let { p ->
                kind("pedlar", p.title, p.intro, p.ask)
                nonEmpty("pedlar.goods", p.goods)
                p.goods.forEachIndexed { i, g -> good("pedlar.goods[$i]", g) }
                p.goods.filter { g -> c.goods.any { it.id == g && !it.rare } }.forEach { fail("pedlar.goods", "\"$it\" isn't a rare good") }
            }
            s.letter.let { l ->
                kind("letter", l.title, l.intro, l.ask, "to", "name", "g")
                text("letter.intro_mine", l.introMine)
                text("letter.ask_mine", l.askMine)
                text("letter.read", l.read)
                text("letter.won", l.won, "name")
                text("letter.won_mine", l.wonMine)
                text("letter.lost", l.lost)
                nonEmpty("letter.letters", l.letters)
                l.letters.forEachIndexed { i, e ->
                    text("letter.letters[$i].to_name", e.toName)
                    text("letter.letters[$i].text", e.text)
                    nonEmpty("letter.letters[$i].questions", e.questions)
                    e.questions.forEachIndexed { j, q ->
                        text("letter.letters[$i].questions[$j].ask", q.ask)
                        text("letter.letters[$i].questions[$j].explain", q.explain)
                        options("letter.letters[$i].questions[$j].options", q.options)
                    }
                }
            }
            s.lamb.let { l ->
                kind("lamb", l.title, l.intro, l.ask)
                for ((k, t) in listOf("where" to l.where, "listen" to l.listen, "read" to l.read, "won" to l.won, "lost" to l.lost)) text("lamb.$k", t)
                text("lamb.heard", l.heard, "said")
                if (l.places.size < 4) fail("lamb.places", "at least 4 (the answer and three others)")
                l.places.forEachIndexed { i, p -> text("lamb.places[$i]", p) }
            }
            s.pilgrim.let { p ->
                kind("pilgrim", p.title, p.intro, p.ask)
                text("pilgrim.prompt", p.prompt, "say")
                for ((k, t) in listOf("show" to p.show, "won" to p.won, "lost" to p.lost)) text("pilgrim.$k", t)
                nonEmpty("pilgrim.ways", p.ways)
                p.ways.forEachIndexed { i, w ->
                    text("pilgrim.ways[$i].say", w.say, anyLanguage = true)
                    options("pilgrim.ways[$i].options", w.options)
                    nonEmpty("pilgrim.ways[$i].explain", w.explain)
                    w.explain.forEachIndexed { j, g -> text("pilgrim.ways[$i].explain[$j]", g) }
                }
            }
            s.riddle.let { r ->
                kind("riddle", r.title, r.intro, r.ask, "name")
                text("riddle.won", r.won, "name")
                text("riddle.lost", r.lost, "name")
                nonEmpty("riddle.riddles", r.riddles)
                r.riddles.forEachIndexed { i, e ->
                    text("riddle.riddles[$i].riddle", e.riddle)
                    options("riddle.riddles[$i].options", e.options)
                    text("riddle.riddles[$i].answer", e.answer, anyLanguage = true)
                    text("riddle.riddles[$i].meaning", e.meaning, anyLanguage = true)
                }
            }
            for (k in listOf("pedlar", "pilgrim")) if (k !in s.strangers) fail("strangers", "needs the $k")
            for ((k, v) in s.strangers) {
                if (v.id != k) fail("strangers.$k.id", "\"${v.id}\", not \"$k\"")
                said("strangers.$k.lines", v.lines)
            }
        }

        fun projects(p: ProjectsFile) {
            file = "projects"
            unique("projects", p.projects.map { it.id })
            p.projects.forEachIndexed { i, e ->
                val at = "projects[$i]"
                val frame = Catalog.projectFrames.firstOrNull { it.id == e.id }
                if (frame == null) fail("$at.id", "\"${e.id}\" isn't one of the game's projects (${Catalog.projectFrames.joinToString { it.id }})")
                else if (frame.steps.size != e.steps.size) fail("$at.steps", "${e.steps.size} steps, the project has ${frame.steps.size}")
                text("$at.name", e.name)
                text("$at.in_text", e.inText, anyLanguage = true)
                text("$at.about", e.about)
                e.steps.forEachIndexed { j, s ->
                    text("$at.steps[$j].task", s.task)
                    text("$at.steps[$j].line", s.line)
                }
                text("$at.done", e.done)
                text("$at.memory", e.memory)
                text("$at.where", e.where)
                e.stands?.let { st ->
                    val raised = StandsEntry.monthDay(st.raised)
                    val until = StandsEntry.monthDay(st.until)
                    if (raised == null) fail("$at.stands.raised", "\"${st.raised}\" isn't a day of the year (MM-DD: \"04-30\")")
                    if (until == null) fail("$at.stands.until", "\"${st.until}\" isn't a day of the year (MM-DD: \"05-31\")")
                    if (raised != null && until != null && until.isBefore(raised)) fail("$at.stands.until", "\"${st.until}\" is before \"${st.raised}\": a season within one year")
                    if (st.minDays !in 0..366) fail("$at.stands.min_days", "${st.minDays}, not 0 … 366")
                    text("$at.stands.up", st.up)
                    text("$at.stands.down", st.down)
                }
            }
            text("lines.try_again", p.lines.tryAgain, "leader")
        }

        fun events(e: EventsFile) {
            file = "events"
            for ((k, t) in listOf("wolves" to e.wolves, "bear" to e.bear, "storm" to e.storm, "merchant" to e.merchant, "festival" to e.festival)) {
                text("$k.title", t.title)
                text("$k.title_walled", t.titleWalled)
                text("$k.intro", t.intro)
                if (k == "merchant") text("$k.won", t.won, "paid", "give", "got", "want") else text("$k.won", t.won)
                text("$k.lost", t.lost)
                text("$k.gift", t.gift)
                text("$k.quiet", t.quiet)
            }
            if (e.merchant.gift == null) fail("merchant.gift", "missing: the merchant's gift when there's nothing to trade")
            if (e.festival.quiet == null) fail("festival.quiet", "missing: a feast day that passed without anyone")
            text("too_late", e.tooLate)
        }

        fun reactions(r: ReactionsFile) {
            file = "reactions"
            if (!Schema.matches(r.schema, REACTIONS_SCHEMA)) fail("schema", "\"${r.schema}\", not \"$REACTIONS_SCHEMA\"")
            if (r.language != lang) fail("language", "\"${r.language}\", not the pack's \"$lang\"")
            val all = listOf("wolves" to r.wolves, "bear" to r.bear, "storm" to r.storm, "merchant" to r.merchant, "festival" to r.festival, "won" to r.won, "lost" to r.lost)
            for ((k, t) in all) {
                fun lines(role: String, list: List<Text>) = list.forEachIndexed { i, l -> text("$k.$role[$i]", l) }
                t.shout.forEachIndexed { i, l ->
                    text("$k.shout[$i]", l)
                    l.by[lang]?.let { if (it.trim().split(Regex("\\s+")).size > 3) fail("$k.shout[$i]", "\"$it\": a shout is a word or two (three at most)") }
                }
                text("$k.call", t.call)
                t.call?.by?.get(lang)?.let { if (it.trim().split(Regex("\\s+")).size > 4) fail("$k.call", "\"$it\": a call is a button, four words at most") }
                lines("defender", t.defender); lines("child", t.child); lines("elder", t.elder); lines("keeper", t.keeper)
                lines("other", t.other); lines("repair", t.repair); lines("home", t.home)
                if (t.other.isEmpty()) fail("$k.other", "empty: whoever has no line of their own says one of these")
            }
            for (k in listOf("wolves", "bear", "storm", "merchant", "festival")) if (all.first { it.first == k }.second.call == null) fail("$k.call", "missing: the card's button to the event's run")
        }

        fun people(p: PeopleFile) {
            file = "people"
            nonEmpty("first_names.female", p.firstNames.female)
            nonEmpty("first_names.male", p.firstNames.male)
            nonEmpty("surnames", p.surnames)
            if (p.trades.none { it.needs == null }) fail("trades", "needs one that needs no building")
            val buildings = BuildingType.entries.map { it.name.lowercase() }
            p.trades.forEachIndexed { i, t ->
                if (t.needs != null && t.needs !in buildings) fail("trades[$i].needs", "\"${t.needs}\" isn't a building (${buildings.joinToString()})")
                text("trades[$i].female", t.female)
                text("trades[$i].male", t.male)
            }
            for ((k, g) in listOf("baby" to p.stages.baby, "child" to p.stages.child, "youth" to p.stages.youth)) {
                text("stages.$k.female", g.female)
                text("stages.$k.male", g.male)
            }
            text("newborn", p.newborn)
            text("story", p.story, "family", "since", anyLanguage = true)
            said("lines.adult", p.lines.adult)
            said("lines.child", p.lines.child)
            text("news.moved_in_cast", p.news.movedInCast, "name", "g")
            text("news.born", p.news.born, "first", "family", "family_at", "g")
            text("news.moved_in", p.news.movedIn, "name", "role", "g")
            text("news.left", p.news.left, "name", "g")
        }

        fun chronicle(c: ChronicleFile) {
            file = "chronicle"
            text("founded", c.founded)
            text("built", c.built, "building")
            text("upgraded", c.upgraded, "building", "level")
            text("repaired", c.repaired, "building")
            text("new_age", c.newAge, "age")
            for ((k, t) in listOf(
                "felled" to c.felled, "felled_more" to c.felledMore, "quarried" to c.quarried,
                "quarried_more" to c.quarriedMore, "picked" to c.picked, "picked_more" to c.pickedMore,
            )) text(k, t)
            text("waited", c.waited, "days")
            text("hungry", c.hungry, "days")
            text("cold", c.cold, "days")
            text("joined", c.joined, "n")
            text("left", c.left, "n")
        }
    }
}
