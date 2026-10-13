package si.lanisce.lani.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import si.lanisce.lani.l10n.LangPair

/**
 * The school's course of one [language] (companion/COURSE.md; lani.course/v0 with its lani.unit/v0 units): its [levels]
 * in order, each an ordered list of units, and the [units] written so far by id (a planned one has none).
 */
data class Course(
    val language: String,
    val levels: List<CourseLevel>,
    val units: Map<String, CourseUnit>,
    val machineWritten: Boolean = true,
    val review: String? = null,
) {
    /** The units of [level] in order, written or planned. */
    fun refs(level: String): List<UnitRef> = levels.firstOrNull { it.level == level }?.units.orEmpty()

    /** The units that count for [level]: written ones (a planned unit can't be opened yet). */
    fun playable(level: String): List<UnitRef> = refs(level).filter { !it.planned && it.id in units }

    /** The level unit [id] is in. */
    fun levelOf(id: String): String? = levels.firstOrNull { l -> l.units.any { it.id == id } }?.level

    fun ref(id: String): UnitRef? = levels.firstNotNullOfOrNull { l -> l.units.firstOrNull { it.id == id } }
}

data class CourseLevel(val level: String, val units: List<UnitRef>)

/** A unit as the outline lists it: [planned] ones aren't written yet ([grammar]: the pages it is to teach). */
data class UnitRef(val id: String, val title: Map<String, String>, val planned: Boolean = false, val grammar: List<String> = emptyList()) {
    fun titleShown(pair: LangPair): String = Courses.shown(title, pair)
}

/**
 * A unit (lani.unit/v0): its [title], [goal], the grammar-book pages it teaches ([grammar]: the help panel, the intro's
 * summary card), its [words], the guided [intro], its [worksheets], its [test] and the tutor's [homework].
 */
data class CourseUnit(
    val id: String,
    val language: String,
    val level: String,
    val emoji: String,
    val title: Map<String, String>,
    val goal: Map<String, String>? = null,
    val grammar: List<String> = emptyList(),
    val words: List<String> = emptyList(),
    val intro: Intro? = null,
    val worksheets: List<Worksheet> = emptyList(),
    val test: UnitTest = UnitTest(80, emptyList()),
    val homework: List<Worksheet> = emptyList(),
    val machineWritten: Boolean = true,
    val review: String? = null,
) {
    fun titleShown(pair: LangPair): String = Courses.shown(title, pair)

    /** A worksheet of the unit or of its homework, by id. */
    fun sheet(id: String): Worksheet? = worksheets.firstOrNull { it.id == id } ?: homework.firstOrNull { it.id == id }
}

/** The guided introduction: the [head] of the table its [steps] build, and the steps. */
data class Intro(val head: List<GrammarCell>, val steps: List<IntroStep>)

/** A step of an intro: the teacher's few lines ([say], per base), [examples], the table's [rows] it adds, a quick question ([ask]). */
data class IntroStep(
    val say: Map<String, String>,
    val examples: List<GrammarExample> = emptyList(),
    val rows: List<List<GrammarCell>> = emptyList(),
    val ask: Exercise? = null,
)

/** A worksheet: its [title], an optional [intro], its [exercises]; [tutor]: homework the tutor published. */
data class Worksheet(
    val id: String,
    val title: Map<String, String>,
    val exercises: List<Exercise>,
    val intro: Intro? = null,
    val tutor: Boolean = false,
    val publishedAt: Long? = null,
) {
    fun titleShown(pair: LangPair): String = Courses.shown(title, pair)
}

/** A unit's test: [pass] percent of its items, an almost counting half. */
data class UnitTest(val pass: Int, val exercises: List<Exercise>)

/** Reading the course (GET /course, or the app's bundled copy). Pure. */
object Courses {
    const val SCHEMA = "lani.course/v0"

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonElement?.texts(): Map<String, String>? = (this as? JsonObject)?.mapNotNull { (k, v) -> v.str()?.let { k to it } }?.toMap()?.takeIf { it.isNotEmpty() }
    private fun JsonElement?.strings(): List<String> = (this as? JsonArray).orEmpty().mapNotNull { it.str() }
    private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    private fun cell(e: JsonElement): GrammarCell? = e.str()?.let { GrammarCell(words = it) } ?: e.texts()?.let { GrammarCell(text = it) }

    /** An exercise as a module's: a type this app doesn't know is [Exercise.Unsupported], never the whole unit lost. */
    fun exercise(o: JsonObject): Exercise =
        runCatching { json.decodeFromJsonElement(Exercise.serializer(), o) }
            .getOrElse { Exercise.Unsupported(o["type"]?.jsonPrimitive?.contentOrNull ?: "?") }

    private fun example(o: JsonObject): GrammarExample? {
        val texts = o.filterKeys { it != "note" && it != "by" }.mapNotNull { (k, v) -> v.str()?.let { k to it } }.toMap()
        return GrammarExample(texts, o["note"].texts()).takeIf { texts.isNotEmpty() }
    }

    fun intro(o: JsonObject?): Intro? {
        o ?: return null
        val head = ((o["table"] as? JsonObject)?.get("head") as? JsonArray).orEmpty().mapNotNull(::cell)
        val steps = o["steps"].objects().mapNotNull { s ->
            val say = s["say"].texts() ?: return@mapNotNull null
            IntroStep(
                say,
                s["examples"].objects().mapNotNull(::example),
                (s["rows"] as? JsonArray).orEmpty().mapNotNull { r -> (r as? JsonArray)?.mapNotNull(::cell)?.takeIf { head.isNotEmpty() && it.size == head.size } },
                (s["ask"] as? JsonObject)?.let(::exercise)?.takeIf { it is Exercise.Choice || it is Exercise.Cloze },
            )
        }
        return Intro(head, steps).takeIf { steps.isNotEmpty() }
    }

    fun worksheet(o: JsonObject, tutor: Boolean = false): Worksheet? {
        val id = o["id"].str() ?: return null
        val exercises = o["exercises"].objects().map(::exercise)
        if (exercises.isEmpty()) return null
        val source = o["source"].str()
        return Worksheet(
            id, o["title"].texts() ?: mapOf("en" to id), exercises, intro(o["intro"] as? JsonObject),
            tutor = tutor || source == "tutor", publishedAt = (o["published_at"] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() },
        )
    }

    /** A unit (lani.unit/v0); null when it lacks what a unit can't do without (an id, a title, a test or a worksheet). */
    fun unit(o: JsonObject): CourseUnit? {
        val id = o["id"].str() ?: return null
        val title = o["title"].texts() ?: return null
        val sheets = o["worksheets"].objects().mapNotNull { worksheet(it) }
        val t = o["test"] as? JsonObject
        val test = UnitTest(
            (t?.get("pass") as? JsonPrimitive)?.intOrNull?.coerceIn(1, 100) ?: 80,
            t?.get("exercises").objects().map(::exercise),
        )
        if (sheets.isEmpty() && test.exercises.isEmpty()) return null
        return CourseUnit(
            id = id,
            language = o["language"].str() ?: "sl",
            level = o["level"].str() ?: "A1",
            emoji = o["emoji"].str() ?: "📘",
            title = title,
            goal = o["goal"].texts(),
            grammar = o["grammar"].strings(),
            words = o["words"].strings(),
            intro = intro(o["intro"] as? JsonObject),
            worksheets = sheets,
            test = test,
            homework = o["homework"].objects().mapNotNull { worksheet(it, tutor = true) }.sortedByDescending { it.publishedAt ?: 0L },
            machineWritten = (o["machine_written"] as? JsonPrimitive)?.booleanOrNull ?: true,
            review = o["review"].str(),
        )
    }

    /**
     * The course as the bridge serves it (GET /course: the outline's `levels`, the written `units` each with its
     * `homework`), or an outline and its unit files as the app bundles them ([units]); null without levels.
     */
    fun parse(o: JsonObject, units: List<JsonObject> = o["units"].objects()): Course? {
        val parsed = units.mapNotNull(::unit).associateBy { it.id }
        val levels = o["levels"].objects().mapNotNull { l ->
            val level = l["level"].str() ?: return@mapNotNull null
            val refs = l["units"].objects().mapNotNull { u ->
                val id = u["id"].str() ?: return@mapNotNull null
                UnitRef(
                    id, u["title"].texts() ?: parsed[id]?.title ?: mapOf("en" to id),
                    // marked planned or not: without its file a unit can't be opened yet
                    planned = id !in parsed,
                    grammar = u["grammar"].strings(),
                )
            }
            // a unit written that the outline doesn't list comes after its level's
            val listed = refs.map { it.id }.toSet()
            val extra = parsed.values.filter { it.level == level && it.id !in listed }.map { UnitRef(it.id, it.title) }
            CourseLevel(level, refs + extra)
        }
        if (levels.isEmpty()) return null
        return Course(
            o["language"].str() ?: "sl", levels, parsed,
            (o["machine_written"] as? JsonPrimitive)?.booleanOrNull ?: true, o["review"].str(),
        )
    }

    /** GET /course's body (rendered for the learner already); null when it isn't a course. */
    fun parse(raw: String): Course? = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()?.let { parse(it) }

    /**
     * The course of [language] the app bundles (companion/course/<language>/, resources course/<language>/ with
     * course/index.json listing each language's unit files: app/build.gradle.kts); null for a language without one.
     */
    fun bundled(language: String): Course? {
        val index = resource("index.json")?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
        val ids = index[language].strings().takeIf { (index[language] as? JsonArray) != null } ?: return null
        val outline = resource("$language/course.json")?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
        val units = ids.mapNotNull { id -> resource("$language/units/$id.json")?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() } }
        return parse(outline, units)
    }

    private fun resource(path: String): String? =
        Courses::class.java.getResourceAsStream("/course/$path")?.use { si.lanisce.lani.l10n.Learner.current.renderJson(it.readBytes().decodeToString()) }

    /** "Biti: sem, si, je · To be: sem, si, je": [t] in the learner's pair (one when both read the same). */
    internal fun shown(t: Map<String, String>, pair: LangPair): String {
        val target = t[pair.target.code] ?: Grammar.text(t, "en")
        val base = Grammar.text(t, pair.base.code)
        return if (pair.base.code != pair.target.code && base != target) "$target · $base" else target
    }
}
