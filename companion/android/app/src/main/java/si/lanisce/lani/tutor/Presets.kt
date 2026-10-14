package si.lanisce.lani.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * What a turn is for (plan 5, "Model per task"): its model by the preset, its thinking effort and its max_tokens.
 * [ROUTINE] the digest of reviews, words and readings; [GRADING] an `answer` and a `session_end`; [CHAT] the chat,
 * "Why?" on an exercise, card fixes, the notebook; [ROLEPLAY] a role-play's turns (phase B); [CONTENT] writing content
 * on request (phase C).
 */
@Serializable
enum class Task(val effort: String, val maxTokens: Int) {
    // thinking counts toward max_tokens: room for it too
    ROUTINE("low", 8_000),
    GRADING("low", 8_000),
    CHAT("medium", 16_000),
    ROLEPLAY("low", 8_000),
    CONTENT("high", 32_000);

    companion object {
        /** The task of an event of [kind] (the channel's kinds). */
        fun of(kind: String): Task = when (kind) {
            "chat", "level_up", "daily_morning", "daily_evening", "daily_weekly" -> CHAT
            "answer", "session_end" -> GRADING
            "roleplay", "roleplay_end" -> ROLEPLAY
            else -> ROUTINE
        }
    }
}

/**
 * A model family: what a preset names, resolved to an id the key lists ([ModelCatalog]). Haiku 4.5 is legacy (retired
 * from 2026-10-15 on): never a default; the panel can still pick it for a test.
 */
@Serializable
enum class Family(val preferred: List<String>) {
    HAIKU(listOf("claude-haiku-5-5")),
    SONNET(listOf("claude-sonnet-5-5", "claude-sonnet-5")),
    OPUS(listOf("claude-opus-5-5", "claude-opus-5")),
}

/** The presets (plan 5, "Presets"): the model per task, and the thread's limit. */
@Serializable
enum class Preset(val families: Map<Task, Family>, val threadLimit: Int) {
    ECONOMY(Task.entries.associateWith { Family.HAIKU }, 20_000),
    BALANCED(
        mapOf(
            Task.ROUTINE to Family.HAIKU, Task.GRADING to Family.HAIKU, Task.CHAT to Family.SONNET,
            Task.ROLEPLAY to Family.SONNET, Task.CONTENT to Family.OPUS,
        ),
        30_000,
    ),
    BEST(Task.entries.associateWith { Family.OPUS }, 60_000),
}

/**
 * The models the key lists (GET /v1/models), and which one plays a family: the plan's id when it's listed, else the
 * next preferred, else the newest current one of the family, else the family above's (Haiku → Sonnet → Opus), never a
 * legacy model by default. Before the key was checked: the plan's ids.
 */
class ModelCatalog(val listed: List<ModelInfo>) {
    fun resolve(f: Family): String {
        if (listed.isEmpty()) return f.preferred.first()
        val ids = listed.map { it.id }.toSet()
        val newest = listed.sortedByDescending { it.createdAt.orEmpty() }
        for (family in Family.entries.drop(f.ordinal)) {
            family.preferred.firstOrNull { it in ids }?.let { return it }
            newest.firstOrNull { it.id.contains(family.name.lowercase()) && !legacy(it.id) }?.let { return it.id }
        }
        return newest.firstOrNull { !legacy(it.id) }?.id ?: newest.first().id
    }

    /** The model of [task]: the panel's override for it when the key lists it, else the preset's. */
    fun modelFor(task: Task, preset: Preset, overrides: Map<Task, String> = emptyMap()): String =
        overrides[task]?.takeIf { o -> listed.isEmpty() || listed.any { it.id == o } } ?: resolve(preset.families.getValue(task))

    companion object {
        /**
         * Whether [model] takes adaptive thinking and `output_config.effort` (the 5-generation models do; Haiku 4.5 and
         * older don't: they get neither).
         */
        fun adaptive(model: String): Boolean = !Regex("""claude-(3|haiku-4|sonnet-4-[0-5]\b|opus-4-[0-5]\b)""").containsMatchIn(model)

        /** A model of an older generation than the presets' (Haiku 4.5, the 4 and 3 families): never chosen by default. */
        fun legacy(model: String): Boolean = Regex("""claude-(\d|haiku-[1-4]|sonnet-[1-4]|opus-[1-4])(-|$)""").containsMatchIn(model)
    }
}

/**
 * Dollars per million tokens of one model (plan 5's table, from the pricing page): input, 5-minute and 1-hour cache
 * writes, cache reads, output; [longAbove]: prompts over that many tokens cost [longFactor] times as much (Haiku 5.5
 * over 100k).
 */
@Serializable
data class Price(
    val model: String,
    val input: Double,
    val write5m: Double,
    val write1h: Double,
    val read: Double,
    val output: Double,
    val longAbove: Long? = null,
    val longFactor: Double = 1.0,
) {
    /** The dollars [u] costs. */
    fun of(u: Usage): Double {
        val f = if (longAbove != null && u.prompt > longAbove) longFactor else 1.0
        return f * (u.input * input + u.cacheWrite5m * write5m + u.cacheWrite1h * write1h + u.cacheRead * read + u.output * output) / 1_000_000.0
    }
}

/** The bundled price table (companion/tutor/prices.json), by model id; a model it lacks is priced by its family. */
class Prices(private val byModel: Map<String, Price>) {
    fun of(model: String): Price? = byModel[model]
        ?: Family.entries.firstOrNull { model.contains(it.name.lowercase()) }?.let { f -> f.preferred.firstNotNullOfOrNull(byModel::get) }

    /** What [u] on [model] costs, or null when nothing prices it. */
    fun cost(model: String, u: Usage): Double? = of(model)?.of(u)

    companion object {
        fun parse(raw: String): Prices {
            val o = tutorJson.parseToJsonElement(raw) as JsonObject
            val list = (o["models"] as? JsonArray).orEmpty().mapNotNull { e ->
                val m = e as? JsonObject ?: return@mapNotNull null
                fun d(k: String) = (m[k] as? JsonPrimitive)?.doubleOrNull
                Price(
                    model = m.str("model") ?: return@mapNotNull null,
                    input = d("input") ?: return@mapNotNull null,
                    write5m = d("write_5m") ?: return@mapNotNull null,
                    write1h = d("write_1h") ?: return@mapNotNull null,
                    read = d("read") ?: return@mapNotNull null,
                    output = d("output") ?: return@mapNotNull null,
                    longAbove = (m["long_above"] as? JsonPrimitive)?.longOrNull,
                    longFactor = d("long_factor") ?: 1.0,
                )
            }
            return Prices(list.associateBy { it.model })
        }

        /** The bundled table, from the app's resources. */
        fun bundled(): Prices = Bundle.text("prices.json")?.let(::parse) ?: Prices(emptyMap())
    }
}

/** The tutor's bundled files (companion/tutor, built into the app's resources under tutor/). */
object Bundle {
    fun text(path: String): String? = Bundle::class.java.getResourceAsStream("/tutor/$path")?.use { it.readBytes().decodeToString() }
}
