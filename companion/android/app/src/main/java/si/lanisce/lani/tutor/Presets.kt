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
 * A model family: what a preset or a matrix lane names, resolved to an id the credential lists ([ModelCatalog]): its
 * [provider], its [tier] (0 small, 1 mid, 2 top), its [preferred] ids, and what an id of it contains ([match]). The
 * presets use Anthropic's alone. Haiku 4.5 is legacy (retired from 2026-10-15 on): never a default; the panel can still
 * pick it for a test.
 *
 * OpenAI's (the models page, developers.openai.com/api/docs/models, checked 2026-10-18): GPT-6 Luna "for cost-sensitive,
 * high-volume workloads", GPT-6.1 Sol "to balance intelligence and cost" (GPT-6 Sol the older), GPT-6 Astra "our
 * flagship model for complex reasoning and coding".
 */
@Serializable
enum class Family(val provider: ProviderId, val tier: Int, val preferred: List<String>, val match: String, private val unless: String? = null) {
    HAIKU(ProviderId.ANTHROPIC, 0, listOf("claude-haiku-5-5"), "haiku"),
    SONNET(ProviderId.ANTHROPIC, 1, listOf("claude-sonnet-5-5", "claude-sonnet-5"), "sonnet"),
    OPUS(ProviderId.ANTHROPIC, 2, listOf("claude-opus-5-5", "claude-opus-5"), "opus"),
    LUNA(ProviderId.OPENAI, 0, listOf("gpt-6-luna"), "luna"),
    SOL(ProviderId.OPENAI, 1, listOf("gpt-6.1-sol", "gpt-6-sol"), "sol"),
    ASTRA(ProviderId.OPENAI, 2, listOf("gpt-6-astra"), "astra"),

    /**
     * Gemini's (the models page, ai.google.dev/gemini-api/docs/models, checked 2026-10-19): 3.5 Flash-Lite (free
     * tier), 3.8 Flash (free tier; 3.6 Flash the one before), 4 Argon (no free tier).
     */
    FLASH_LITE(ProviderId.GEMINI, 0, listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite"), "flash-lite"),
    FLASH(ProviderId.GEMINI, 1, listOf("gemini-3.8-flash", "gemini-3.6-flash"), "flash", unless = "lite"),
    ARGON(ProviderId.GEMINI, 2, listOf("gemini-4-argon"), "argon"),

    /**
     * A model the learner picked from OpenRouter's list (a matrix lane's [LaneSpec.model], a task's model): OpenRouter
     * has no families of its own; its tiers are [OpenRouterModels.DEFAULTS].
     */
    ROUTED(ProviderId.OPENROUTER, 1, listOf(OpenRouterModels.FLASH), "/");

    /** "Haiku", "Sol", "Flash-Lite". */
    val label: String get() = name.lowercase().split('_').joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }

    /** Whether [model]'s id names this family. */
    fun matches(model: String): Boolean = model.contains(match) && (unless == null || !model.contains(unless))

    companion object {
        /** [provider]'s families, small to top. */
        fun of(provider: ProviderId): List<Family> = entries.filter { it.provider == provider }

        /** The family [model] belongs to (its id names it, of its own provider), or null. */
        fun ofModel(model: String): Family? = ProviderId.of(model).let { p -> entries.firstOrNull { it.provider == p && it.matches(model) } }
    }
}

/**
 * OpenRouter's models as the tutor offers them (openrouter.ai/api/v1/models, the live list read 2026-10-19; the learner
 * picks from the list filtered to tool use): the defaults per tier and the suggestions shown first. No open model's card
 * there documents Slovene (Gemma 4, Qwen 3.5, Mistral Small name many languages, not Slovene; CJVT's Slovene GaMS models
 * aren't on OpenRouter), so none is a default.
 */
object OpenRouterModels {
    const val ARGON = "google/gemini-4-argon"
    const val FLASH = "google/gemini-3.8-flash"
    const val FLASH_LITE = "google/gemini-3.5-flash-lite"
    const val SOL = "openai/gpt-6.1-sol"
    const val LUNA = "openai/gpt-6-luna"
    const val SONNET = "anthropic/claude-sonnet-5.5"
    const val GEMMA = "google/gemma-4-31b-it"

    /** A tier's default (small, mid, top): GPT-6 Luna, Gemini 3.8 Flash, Gemini 4 Argon. */
    val DEFAULTS = listOf(LUNA, FLASH, ARGON)

    /** Shown first in the picker, in this order (the rest of the list after them). */
    val SUGGESTED = listOf(ARGON, FLASH, FLASH_LITE, SOL, LUNA, SONNET, GEMMA)

    /** The default of [tier] (0 small, 1 mid, 2 top). */
    fun forTier(tier: Int): String = DEFAULTS[tier.coerceIn(0, 2)]
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
 * The models a credential lists (GET /v1/models: the Anthropic key's, or the OpenAI key's or ChatGPT sign-in's), and
 * which one plays a family: the plan's id when it's listed, else the next preferred, else the newest current one of the
 * family, else the family above's of the same provider (Haiku → Sonnet → Opus), never a legacy model by default. Before
 * the credential was checked: the plan's ids.
 */
class ModelCatalog(val listed: List<ModelInfo>) {
    fun resolve(f: Family): String {
        if (listed.isEmpty()) return f.preferred.first()
        val ids = listed.map { it.id }.toSet()
        val newest = listed.sortedByDescending { it.createdAt.orEmpty() }
        for (family in Family.of(f.provider).filter { it.tier >= f.tier }) {
            family.preferred.firstOrNull { it in ids }?.let { return it }
            newest.firstOrNull { family.matches(it.id) && !legacy(it.id) }?.let { return it.id }
        }
        return newest.firstOrNull { !legacy(it.id) }?.id ?: newest.first().id
    }

    /**
     * The model of [f] itself (a matrix lane's, [Lanes.plan]): the plan's id when it's listed, else the next preferred,
     * else the family's newest current one; null when the credential lists none of the family (never the family
     * above's, as [resolve] falls back to). Before the credential was checked: the plan's id.
     */
    fun own(f: Family): String? {
        if (listed.isEmpty()) return f.preferred.first()
        val ids = listed.map { it.id }.toSet()
        return f.preferred.firstOrNull { it in ids }
            ?: listed.sortedByDescending { it.createdAt.orEmpty() }.firstOrNull { f.matches(it.id) && !legacy(it.id) }?.id
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
         * The effort levels [model] takes. Anthropic's (the effort docs' table, 2026-10-16): all five on the 5-generation
         * models and Opus 4.7 and 4.8; no `xhigh` on Opus 4.6, Sonnet 4.6 and Mythos Preview; none on a model without
         * adaptive thinking here (Haiku 4.5 and older). OpenAI's: [openAIEfforts].
         */
        fun efforts(model: String): List<String> = when {
            ProviderId.of(model) == ProviderId.OPENAI -> openAIEfforts(model)
            ProviderId.of(model) == ProviderId.GEMINI -> geminiLevels(model)
            ProviderId.of(model) == ProviderId.OPENROUTER -> routedEfforts(model)
            !adaptive(model) -> emptyList()
            Regex("""claude-(opus-4-6|sonnet-4-6|mythos-preview)""").containsMatchIn(model) -> Effort.LEVELS - Effort.XHIGH
            else -> Effort.LEVELS
        }

        /**
         * OpenAI's `reasoning.effort` levels per model (the model pages, developers.openai.com/api/docs/models/<id>,
         * checked 2026-10-18): GPT-6 Astra and GPT-6.1 Sol low to max ("The `none` and `minimal` reasoning efforts are
         * not supported"); GPT-6 Luna, GPT-6 Sol and the GPT-5.6 models none and low to max; GPT-5.5 none and low to
         * xhigh. Another reasoning model of theirs (gpt-5…, o…): low, medium, high, the levels all of them take (not
         * checked model by model); a model without reasoning (gpt-4…, the chat-latest aliases): none.
         */
        fun openAIEfforts(model: String): List<String> {
            val all = listOf(Effort.LOW, Effort.MEDIUM, Effort.HIGH, Effort.XHIGH, Effort.MAX)
            return when {
                Regex("""^gpt-(6-astra|6\.1-sol)(-|$)""").containsMatchIn(model) -> all
                Regex("""^gpt-(6-luna|6-sol|5\.6-(luna|terra|sol))(-|$)""").containsMatchIn(model) -> listOf(Effort.NONE) + all
                Regex("""^gpt-5\.5(-|$)""").containsMatchIn(model) && !model.contains("pro") -> listOf(Effort.NONE, Effort.LOW, Effort.MEDIUM, Effort.HIGH, Effort.XHIGH)
                Regex("""^(gpt-5|o\d)""").containsMatchIn(model) && !model.contains("chat") -> listOf(Effort.LOW, Effort.MEDIUM, Effort.HIGH)
                else -> emptyList()
            }
        }

        /**
         * Gemini's `thinkingLevel` per model (the thinking guide and the model pages, ai.google.dev, checked 2026-10-19):
         * 4 Argon and 3.8 Flash low, medium (their default), high ("`minimal` is not supported and returns an error";
         * thinking can't be turned off); 3.6 Flash, 3.5 and 3.1 Flash-Lite minimal to high (Flash-Lite's default
         * minimal). Another Gemini 3 or 4 model: low, medium, high. Gemini 2.5 takes a budget, not a level: none here.
         */
        fun geminiLevels(model: String): List<String> {
            val m = model.substringAfter('/')
            return when {
                Regex("""^gemini-(3\.6-flash|3\.\d+-flash-lite)""").containsMatchIn(m) -> listOf(Effort.MINIMAL, Effort.LOW, Effort.MEDIUM, Effort.HIGH)
                Regex("""^gemini-([3-9]|\d{2,})""").containsMatchIn(m) -> listOf(Effort.LOW, Effort.MEDIUM, Effort.HIGH)
                else -> emptyList()
            }
        }

        /**
         * The `reasoning.effort` levels OpenRouter takes for [model] (its reasoning guide: max, xhigh, high, medium, low,
         * minimal, none; per model GET /models's `reasoning.supported_efforts`): the list's when it was read
         * ([RoutedModels]), else the vendor's own levels (Gemini's, OpenAI's, Claude's), else low, medium, high.
         */
        fun routedEfforts(model: String): List<String> {
            RoutedModels.efforts(model)?.let { return it }
            val vendor = model.substringBefore('/')
            val id = model.substringAfter('/').substringBefore(':')
            return when (vendor) {
                "google" -> geminiLevels(id)
                "openai" -> openAIEfforts(id)
                "anthropic" -> if (adaptive(id.replace('.', '-'))) Effort.LEVELS else emptyList()
                else -> listOf(Effort.LOW, Effort.MEDIUM, Effort.HIGH)
            }
        }

        /**
         * [want] on [model]: as it is when the model takes it, else the nearest level below it the model takes (else its
         * lowest); null when the model takes no effort.
         */
        fun fit(want: String?, model: String): String? {
            val levels = efforts(model)
            if (levels.isEmpty()) return null
            if (want in levels) return want
            val i = Effort.ALL.indexOf(want)
            return Effort.ALL.take(maxOf(i, 0)).lastOrNull { it in levels } ?: levels.first()
        }

        /** The effort of [task] on [model]: the panel's for it ([overrides]), else [preset]'s, fitted to [model]. */
        fun effortFor(task: Task, preset: Preset, model: String, overrides: Map<Task, String> = emptyMap()): String? =
            fit(overrides[task] ?: preset.efforts.getValue(task), model)

        /**
         * The API's effort when a request sets none: `medium` on Opus 5.5 and Haiku 5.5, `high` on the other Claude
         * models; `medium` on OpenAI's (the model pages say so for GPT-6.1 Sol, GPT-6 Luna, GPT-6 Sol and GPT-5.6; GPT-6
         * Astra's page names no default: unverified).
         */
        fun defaultEffort(model: String): String? = when {
            efforts(model).isEmpty() -> null
            ProviderId.of(model) == ProviderId.OPENAI -> Effort.MEDIUM
            // Gemini: medium on Argon and 3.8 Flash, minimal on Flash-Lite (the thinking guide)
            ProviderId.of(model) == ProviderId.GEMINI -> if (model.contains("flash-lite")) Effort.MINIMAL else Effort.MEDIUM
            ProviderId.of(model) == ProviderId.OPENROUTER -> RoutedModels.defaultEffort(model) ?: Effort.MEDIUM
            Regex("""claude-(opus-5-5|haiku-5-5)(-|$)""").containsMatchIn(model) -> Effort.MEDIUM
            else -> Effort.HIGH
        }

        /**
         * Whether [model] changes effort within a thread by an effort message ([Effort], beta
         * mid-conversation-output-config-2026-07-01; the Claude API: Fable 5.1, Mythos 5.1, Opus 5.5, Opus 5, Sonnet 5.5,
         * Haiku 5.5). Not Sonnet 5 (the Sonnet family's fallback id): a turn there runs at the thread's effort.
         */
        fun perMessageEffort(model: String): Boolean =
            Regex("""claude-(opus-5-5|opus-5|sonnet-5-5|haiku-5-5|fable-5-1|mythos-5-1)(-\d{8})?$""").containsMatchIn(model) ||
                // OpenAI: a configuration_update item ("supported by the GPT-6 model family in standard, single-agent
                // mode": the reasoning guide, "Change reasoning mid-conversation")
                Regex("""^gpt-6(\.\d+)?-""").containsMatchIn(model)

        /** A model of an older generation than the presets' (Haiku 4.5, the 4 and 3 families): never chosen by default. */
        fun legacy(model: String): Boolean = Regex("""claude-(\d|haiku-[1-4]|sonnet-[1-4]|opus-[1-4])(-|$)""").containsMatchIn(model)
    }
}

/**
 * Dollars per million tokens of one model (plan 5's table, from the pricing page): input, 5-minute and 1-hour cache
 * writes, cache reads, output; [longAbove]: prompts over that many tokens cost [longFactor] times as much (Haiku 5.5
 * over 100k), the output [longOutputFactor] times when given (OpenAI's over 272k: 2× input and cache, 1.5× output).
 * OpenAI's cache writes (one lifetime, at least 30 minutes) are its [write5m] and [write1h] alike.
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
    val longOutputFactor: Double? = null,
    /**
     * The days it holds, as ISO dates, both included (null: open): an introductory price ends ([until]: Gemini 4 Argon's
     * $2 / $10 "through November 30, 2026"), the next row begins ([from]: "$4.00 starting December 1, 2026").
     */
    val from: String? = null,
    val until: String? = null,
) {
    /** Whether it holds on [day] (UTC). */
    fun holds(day: java.time.LocalDate): Boolean =
        (from == null || !day.isBefore(java.time.LocalDate.parse(from))) && (until == null || !day.isAfter(java.time.LocalDate.parse(until)))

    /** The dollars [u] costs. */
    fun of(u: Usage): Double {
        val long = longAbove != null && u.prompt > longAbove
        val f = if (long) longFactor else 1.0
        val fo = if (long) longOutputFactor ?: longFactor else 1.0
        return (f * (u.input * input + u.cacheWrite5m * write5m + u.cacheWrite1h * write1h + u.cacheRead * read) + fo * u.output * output) / 1_000_000.0
    }
}

/**
 * A call's price: what it [cost] the learner (null: nothing prices the model), and with the ChatGPT plan the
 * [apiEquivalent], what its tokens cost on the API (the learner pays nothing per token there).
 */
data class Priced(val cost: Double?, val apiEquivalent: Double? = null)

/**
 * The bundled price table (companion/tutor/prices.json), by model id; a model it lacks is priced by its family. A model
 * may have rows for spans of days ([Price.from], [Price.until]): the row of the call's day ([clock], UTC) prices it.
 */
class Prices(private val rows: List<Price>, private val clock: () -> Long = System::currentTimeMillis) {
    constructor(byModel: Map<String, Price>) : this(byModel.values.toList())

    private val byModel: Map<String, List<Price>> = rows.groupBy { it.model }

    private fun row(model: String, day: java.time.LocalDate): Price? = byModel[model]?.let { r -> r.firstOrNull { it.holds(day) } ?: r.last() }

    fun of(model: String): Price? {
        val day = java.time.Instant.ofEpochMilli(clock()).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        row(model, day)?.let { return it }
        // an OpenRouter model without its reported cost: the vendor's own row (google/gemini-4-argon: gemini-4-argon;
        // anthropic/claude-sonnet-5.5: claude-sonnet-5-5)
        if (model.contains('/')) {
            val own = model.substringAfter('/').substringBefore(':')
            return (row(own, day) ?: row(own.replace('.', '-'), day))
                ?: of(own).takeIf { ProviderId.of(own) != ProviderId.OPENROUTER }
        }
        return Family.ofModel(model)?.let { f -> f.preferred.firstNotNullOfOrNull { row(it, day) } }
    }

    /** What [u] on [model] costs, or null when nothing prices it. */
    fun cost(model: String, u: Usage): Double? = of(model)?.of(u)

    /**
     * What [u] on [model] cost paid by [billing]: its price; with the ChatGPT plan or the Gemini free tier nothing ($0),
     * and the price as its API-equivalent; with OpenRouter's credits the price OpenRouter reported ([reported], its
     * `usage.cost`), else the table's.
     */
    fun priced(model: String, u: Usage, billing: String, reported: Double? = null): Priced = when {
        Billing.free(billing) -> Priced(0.0, cost(model, u))
        reported != null -> Priced(reported)
        else -> Priced(cost(model, u))
    }

    companion object {
        fun parse(raw: String, clock: () -> Long = System::currentTimeMillis): Prices {
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
                    longOutputFactor = d("long_output_factor"),
                    from = m.str("from"),
                    until = m.str("until"),
                )
            }
            return Prices(list, clock)
        }

        /** The bundled table, from the app's resources. */
        fun bundled(): Prices = Bundle.text("prices.json")?.let(::parse) ?: Prices(emptyMap())
    }
}

/** The tutor's bundled files (companion/tutor, built into the app's resources under tutor/). */
object Bundle {
    fun text(path: String): String? = Bundle::class.java.getResourceAsStream("/tutor/$path")?.use { it.readBytes().decodeToString() }
}
