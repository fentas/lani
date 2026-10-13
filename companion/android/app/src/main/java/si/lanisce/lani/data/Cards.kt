package si.lanisce.lani.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * A card of the cheat sheet that isn't a grammar page's (lani.card/v0, companion/bridge/src/cards.ts; companion/README.md
 * "Cheat sheet"): a curated one no page fits (companion/cards/<language>), or the tutor's (publish_card). Its [title] per
 * language (a tutor's plain title under ""), either the curated face ([rule] per base, a [table], [examples], a [tip]) or
 * the tutor's [markdown]; the pages it belongs to ([grammar]: they unlock it and bring it first in their exercises);
 * [start]: an A1 basic, there from the start. [source]: [Grammar.CURATED] or [Grammar.TUTOR].
 */
data class DeckCard(
    val id: String,
    val language: String,
    val level: String,
    val emoji: String,
    val title: Map<String, String>,
    val rule: Map<String, String>? = null,
    val table: GrammarTable? = null,
    val examples: List<GrammarExample> = emptyList(),
    val tip: Map<String, String>? = null,
    val markdown: String? = null,
    val grammar: List<String> = emptyList(),
    val start: Boolean = false,
    val machineWritten: Boolean = true,
    val review: String? = null,
    val source: String = Grammar.CURATED,
) {
    val byTutor: Boolean get() = source == Grammar.TUTOR
}

/** The cheat sheet's own cards: reading them as the bridge serves them, the bundled ones, the two merged. */
object Cards {
    const val SCHEMA = "lani.card/v0"

    /** A card as the bridge serves it (or the app bundles it); null without an id, a title, or a face (its rule line or Markdown). */
    fun parse(o: JsonObject): DeckCard? {
        val id = Grammar.strOf(o["id"]) ?: return null
        val title = Grammar.textsOf(o["title"]) ?: Grammar.strOf(o["title"])?.let { mapOf("" to it) } ?: return null
        val rule = Grammar.textsOf(o["rule"])
        val markdown = Grammar.strOf(o["markdown"])
        if (rule == null && markdown == null) return null
        return DeckCard(
            id = id,
            language = Grammar.strOf(o["language"]) ?: "sl",
            level = Grammar.strOf(o["level"]) ?: "A1",
            emoji = Grammar.strOf(o["emoji"]) ?: "📋",
            title = title,
            rule = rule,
            table = Grammar.table(o["table"]),
            examples = Grammar.examples(o["examples"]),
            tip = Grammar.textsOf(o["tip"]),
            markdown = markdown,
            grammar = Grammar.stringsOf(o["grammar"]),
            start = (o["start"] as? JsonPrimitive)?.booleanOrNull == true,
            machineWritten = (o["machine_written"] as? JsonPrimitive)?.booleanOrNull ?: true,
            review = Grammar.strOf(o["review"]),
            source = Grammar.strOf(o["source"]) ?: Grammar.CURATED,
        )
    }

    /** GET /cards: the curated cards, then the tutor's. */
    fun parseList(raw: String): List<DeckCard> =
        (json.parseToJsonElement(si.lanisce.lani.l10n.Learner.current.renderJson(raw)) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parse) }

    /**
     * The curated cards of [language] the app bundles (companion/cards/<language>/, resources cards/<language>/ with
     * cards/index.json listing them: app/build.gradle.kts); none for a language without them.
     */
    fun bundled(language: String): List<DeckCard> {
        val index = resource("index.json")?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return emptyList()
        return Grammar.stringsOf(index[language]).mapNotNull { id ->
            resource("$language/$id.json")?.let { raw -> runCatching { parse(json.parseToJsonElement(raw) as JsonObject) }.getOrNull() }
        }
    }

    private fun resource(path: String): String? =
        Cards::class.java.getResourceAsStream("/cards/$path")?.use { si.lanisce.lani.l10n.Learner.current.renderJson(it.readBytes().decodeToString()) }

    /** The bridge's cards (the tutor's included), then bundled ones it doesn't list; the bundled alone from an older bridge. */
    fun merge(bundled: List<DeckCard>, bridge: List<DeckCard>?): List<DeckCard> {
        if (bridge == null) return bundled
        val ids = bridge.map { it.id }.toSet()
        return bridge + bundled.filter { it.id !in ids }
    }
}
