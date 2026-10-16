package si.lanisce.lani.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * What a turn is for (plan 5, "Model per task"): [family] and [effort] our recommendation for it (the Balanced preset;
 * the learner sets both per task), its max_tokens. [ROUTINE] the digest of reviews, words and readings; [GRADING] an
 * `answer` and a `session_end`; [CHAT] the chat, "Why?" on an exercise, card fixes, the notebook; [ROLEPLAY] a
 * role-play's turns (phase B); [CONTENT] writing content on request (phase C).
 *
 * Why these (Jan, 2026-10-16): Haiku 5.5 at `low` answered a review in plain text and never called `reply`; Anthropic
 * recommends `high` on Haiku 5.5 for strict instruction following, and it costs ~$0.001–0.002 more a reaction. Chat on
 * Sonnet at `medium`; role-play on Sonnet at `low` (latency: the line is voiced; to measure in phase B); content on Opus
 * at `medium` (its own default; what the learner drills again and again, so its Slovene matters most; `high` for large
 * pieces). Grading on Haiku at `high`, to be measured against Sonnet at `medium`.
 */
@Serializable
enum class Task(val family: Family, val effort: String, val maxTokens: Int) {
    // thinking counts toward max_tokens: room for a long think and the reply
    ROUTINE(Family.HAIKU, Effort.HIGH, 16_000),
    GRADING(Family.HAIKU, Effort.HIGH, 16_000),
    CHAT(Family.SONNET, Effort.MEDIUM, 16_000),
    ROLEPLAY(Family.SONNET, Effort.LOW, 8_000),
    CONTENT(Family.OPUS, Effort.MEDIUM, 32_000);

    /** max_tokens at [effort]: `xhigh` and `max` think longer, so they get more room. */
    fun maxTokensAt(effort: String?): Int = when (effort) {
        Effort.XHIGH -> maxOf(maxTokens, 32_000)
        Effort.MAX -> maxOf(maxTokens, 64_000)
        else -> maxTokens
    }

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

/**
 * The presets (plan 5, "Presets"): the model and the effort per task, and the thread's limit. [ECONOMY] the cheapest:
 * Haiku 5.5 at its default (`medium`, role-play `low`), content on Sonnet 5.5 at `medium`; [BALANCED] our recommendation
 * (each task's [Task.family] and [Task.effort]); [BEST] Opus 5.5 at `medium` for all but content at `high`.
 */
@Serializable
enum class Preset(val families: Map<Task, Family>, val efforts: Map<Task, String>, val threadLimit: Int) {
    ECONOMY(
        Task.entries.associateWith { if (it == Task.CONTENT) Family.SONNET else Family.HAIKU },
        Task.entries.associateWith { if (it == Task.ROLEPLAY) Effort.LOW else Effort.MEDIUM },
        20_000,
    ),
    BALANCED(Task.entries.associateWith { it.family }, Task.entries.associateWith { it.effort }, 30_000),
    BEST(Task.entries.associateWith { Family.OPUS }, Task.entries.associateWith { if (it == Task.CONTENT) Effort.HIGH else Effort.MEDIUM }, 60_000),
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

        /**
         * The effort levels [model] takes (the effort docs' table, 2026-10-16): all five on the 5-generation models and
         * Opus 4.7 and 4.8; no `xhigh` on Opus 4.6, Sonnet 4.6 and Mythos Preview; none on a model without adaptive
         * thinking here (Haiku 4.5 and older).
         */
        fun efforts(model: String): List<String> = when {
            !adaptive(model) -> emptyList()
            Regex("""claude-(opus-4-6|sonnet-4-6|mythos-preview)""").containsMatchIn(model) -> Effort.LEVELS - Effort.XHIGH
            else -> Effort.LEVELS
        }

        /**
         * [want] on [model]: as it is when the model takes it, else the nearest level below it the model takes (else its
         * lowest); null when the model takes no effort.
         */
        fun fit(want: String?, model: String): String? {
            val levels = efforts(model)
            if (levels.isEmpty()) return null
            if (want in levels) return want
            val i = Effort.LEVELS.indexOf(want)
            return Effort.LEVELS.take(maxOf(i, 0)).lastOrNull { it in levels } ?: levels.first()
        }

        /** The effort of [task] on [model]: the panel's for it ([overrides]), else [preset]'s, fitted to [model]. */
        fun effortFor(task: Task, preset: Preset, model: String, overrides: Map<Task, String> = emptyMap()): String? =
            fit(overrides[task] ?: preset.efforts.getValue(task), model)

        /** The API's effort when a request sets none: `medium` on Opus 5.5 and Haiku 5.5, `high` on the others. */
        fun defaultEffort(model: String): String? = when {
            efforts(model).isEmpty() -> null
            Regex("""claude-(opus-5-5|haiku-5-5)(-|$)""").containsMatchIn(model) -> Effort.MEDIUM
            else -> Effort.HIGH
        }

        /**
         * Whether [model] changes effort within a thread by an effort message ([Effort], beta
         * mid-conversation-output-config-2026-07-01; the Claude API: Fable 5.1, Mythos 5.1, Opus 5.5, Opus 5, Sonnet 5.5,
         * Haiku 5.5). Not Sonnet 5 (the Sonnet family's fallback id): a turn there runs at the thread's effort.
         */
        fun perMessageEffort(model: String): Boolean =
            Regex("""claude-(opus-5-5|opus-5|sonnet-5-5|haiku-5-5|fable-5-1|mythos-5-1)(-\d{8})?$""").containsMatchIn(model)

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
