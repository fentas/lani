package si.lanisce.lani.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import si.lanisce.lani.l10n.LangPair
import kotlin.random.Random

/**
 * A page of the grammar book (lani.grammar/v0, companion/bridge/src/grammar.ts; README "Grammar book"): one rule of the
 * [language] learned, its [title] in that language and the bases, the [rule] in each base (Markdown), the tutor's [more],
 * a [table], [examples], the [modules] that practise it, [tags] that match the learner's mistakes and grammar cards, and
 * related pages ([see]). [source]: [Grammar.CURATED] (companion/grammar) or [Grammar.TUTOR] (a page of the tutor's own);
 * [extended]: a curated page the tutor added to.
 */
data class GrammarPage(
    val id: String,
    val language: String,
    val level: String,
    val emoji: String,
    val title: Map<String, String>,
    val rule: Map<String, String>,
    val more: Map<String, String>? = null,
    val table: GrammarTable? = null,
    val examples: List<GrammarExample> = emptyList(),
    val modules: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val see: List<String> = emptyList(),
    val machineWritten: Boolean = true,
    val review: String? = null,
    val source: String = Grammar.CURATED,
    val extended: Boolean = false,
) {
    /** The tutor's own page. */
    val byTutor: Boolean get() = source == Grammar.TUTOR

    /** Its title in the learned language: "Kje? Predlogi kraja". */
    val target: String get() = title[language] ?: Grammar.text(title, "en")

    /** Its title in [base] (English when it lacks it): "Where? Prepositions of place". */
    fun titleIn(base: String): String = Grammar.text(title, base)

    /** "Kje? Predlogi kraja · Where? Prepositions of place": the title in the learner's pair. */
    fun titleShown(pair: LangPair): String {
        val b = titleIn(pair.base.code)
        return if (pair.base.code != language && b != target) "$target · $b" else target
    }

    /** The rule explained in [base], else in English. */
    fun ruleIn(base: String): String = Grammar.text(rule, base)

    /** The tutor's addition in [base], else in English; null when there is none. */
    fun moreIn(base: String): String? = more?.let { Grammar.text(it, base) }?.takeIf { it.isNotBlank() }
}

/** A table: its [head] and [rows], each row as long as the head. */
data class GrammarTable(val head: List<GrammarCell>, val rows: List<List<GrammarCell>>)

/** A cell: [words] of the learned language as they are ("ob ribniku"), or a [text] per base ("locative (mestnik)"). */
data class GrammarCell(val words: String? = null, val text: Map<String, String>? = null) {
    /** What it shows for a learner explaining in [base]. */
    fun shown(base: String): String = words ?: text?.let { Grammar.text(it, base) }.orEmpty()

    /** It is words of the learned language (heard with 🔊, its words looked up). */
    val isWords: Boolean get() = words != null
}

/** An example: the sentence in each language ([texts]: the learned one and the bases), a [note], and whether the tutor added it. */
data class GrammarExample(val texts: Map<String, String>, val note: Map<String, String>? = null, val byTutor: Boolean = false) {
    /** The sentence in the learned [language]. */
    fun target(language: String): String = texts[language].orEmpty()

    /** Its translation into [base] (English when it lacks it). */
    fun meaning(base: String): String = Grammar.text(texts, base)

    fun noteIn(base: String): String? = note?.let { Grammar.text(it, base) }?.takeIf { it.isNotBlank() }
}

/** The pure parts of the grammar book: reading pages, what names them, what they show and practise. */
object Grammar {
    const val SCHEMA = "lani.grammar/v0"
    const val CURATED = "curated"
    const val TUTOR = "tutor"

    /** A text in [lang], else English, else its first. */
    fun text(t: Map<String, String>, lang: String): String = t[lang] ?: t["en"] ?: t.values.firstOrNull().orEmpty()

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonElement?.texts(): Map<String, String>? = (this as? JsonObject)?.mapNotNull { (k, v) -> v.str()?.let { k to it } }?.toMap()?.takeIf { it.isNotEmpty() }
    private fun JsonElement?.strings(): List<String> = (this as? JsonArray).orEmpty().mapNotNull { it.str() }
    private fun cell(e: JsonElement): GrammarCell? = e.str()?.let { GrammarCell(words = it) } ?: e.texts()?.let { GrammarCell(text = it) }

    /**
     * A page as the bridge serves it (or as the app bundles it); null when it lacks what a page can't do without (an id,
     * a title, a rule). Parts it can't read are left out, so a newer bridge's page still shows.
     */
    fun parse(o: JsonObject): GrammarPage? {
        val id = o["id"].str() ?: return null
        val title = o["title"].texts() ?: return null
        val rule = o["rule"].texts() ?: return null
        val table = (o["table"] as? JsonObject)?.let { t ->
            val head = (t["head"] as? JsonArray).orEmpty().mapNotNull(::cell)
            val rows = (t["rows"] as? JsonArray).orEmpty().mapNotNull { r -> (r as? JsonArray)?.mapNotNull(::cell)?.takeIf { it.size == head.size } }
            GrammarTable(head, rows).takeIf { head.size >= 2 && rows.isNotEmpty() }
        }
        val examples = (o["examples"] as? JsonArray).orEmpty().mapNotNull { e ->
            val x = e as? JsonObject ?: return@mapNotNull null
            val texts = x.filterKeys { it != "note" && it != "by" }.mapNotNull { (k, v) -> v.str()?.let { k to it } }.toMap()
            GrammarExample(texts, x["note"].texts(), byTutor = x["by"].str() == TUTOR).takeIf { texts.isNotEmpty() }
        }
        return GrammarPage(
            id = id,
            language = o["language"].str() ?: "sl",
            level = o["level"].str() ?: "A1",
            emoji = o["emoji"].str() ?: "📖",
            title = title,
            rule = rule,
            more = o["more"].texts(),
            table = table,
            examples = examples,
            modules = o["modules"].strings(),
            tags = o["tags"].strings(),
            see = o["see"].strings(),
            machineWritten = (o["machine_written"] as? JsonPrimitive)?.booleanOrNull ?: true,
            review = o["review"].str(),
            source = o["source"].str() ?: CURATED,
            extended = (o["extended"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

    /** GET /grammar: the pages in the bridge's order. */
    fun parseList(raw: String): List<GrammarPage> =
        (json.parseToJsonElement(raw) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parse) }

    /**
     * The curated pages of [language] the app bundles (companion/grammar/<language>/, resources grammar/<language>/ with
     * grammar/index.json listing them: app/build.gradle.kts), in the book's order; none for a language without pages.
     */
    fun bundled(language: String): List<GrammarPage> {
        val index = resource("index.json")?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return emptyList()
        return index[language].strings().mapNotNull { id ->
            resource("$language/$id.json")?.let { raw -> runCatching { parse(json.parseToJsonElement(raw) as JsonObject) }.getOrNull() }
        }
    }

    private fun resource(path: String): String? =
        Grammar::class.java.getResourceAsStream("/grammar/$path")?.use { it.readBytes().decodeToString() }

    /** The book: the bridge's pages (the tutor's included, and what the tutor added), then bundled ones it doesn't list. */
    fun merge(bundled: List<GrammarPage>, bridge: List<GrammarPage>?): List<GrammarPage> {
        if (bridge == null) return bundled
        val ids = bridge.map { it.id }.toSet()
        return bridge + bundled.filter { it.id !in ids }
    }

    /**
     * Whether page [tag] names [id] (a mistake pattern, a card's category or id): it is the tag, or has the tag's words in
     * a row ("accusative" in "accusative_feminine_a_to_o"), as the bridge matches them (grammar.ts `tagMatches`).
     */
    fun tagMatches(tag: String, id: String): Boolean {
        val t = tag.lowercase().split('_', '-').filter { it.isNotEmpty() }
        val w = id.lowercase().split('_', '-').filter { it.isNotEmpty() }
        if (t.isEmpty() || t.size > w.size) return false
        return (0..w.size - t.size).any { i -> t.indices.all { j -> w[i + j] == t[j] } }
    }

    private fun GrammarPage.names(id: String?): Boolean = id != null && tags.any { tagMatches(it, id) }

    /** A grammar card (not vocabulary: a rule the learner reviews) is this page's when a tag names its category or id. */
    fun owns(p: GrammarPage, card: ReviewCard): Boolean = card.kind != "vocabulary" && (p.names(card.category) || p.names(card.id))

    /** The page a review card belongs to: the first whose tags name it; none for vocabulary. */
    fun forCard(card: ReviewCard, pages: List<GrammarPage>): GrammarPage? = pages.firstOrNull { owns(it, card) }

    /** The learner's grammar cards on [p]. */
    fun cards(p: GrammarPage, pool: List<ReviewCard>): List<ReviewCard> = pool.filter { owns(p, it) }

    /** The learner's mistakes on [p]: the patterns its tags name. */
    fun mistakes(p: GrammarPage, all: List<MistakeNote>): List<MistakeNote> = all.filter { p.names(it.id) }

    /** The pages that name module [id] among theirs. */
    fun ofModule(id: String, pages: List<GrammarPage>): List<GrammarPage> = pages.filter { id in it.modules }

    /** What "🎯 Vadi · Practise" plays for a page. */
    sealed interface Practice {
        /** Exercises that name the page (modules' and the challenges'), then those of the modules it links. */
        data class Exercises(val exercises: List<Exercise>) : Practice
        /** Nothing names it: the learner's grammar cards on it, reviewed. */
        data class Cards(val cards: List<ReviewCard>) : Practice
        /** Nothing to practise yet: the tutor is asked for a drill. */
        data object Ask : Practice
    }

    /** A practise run's length. */
    const val PRACTICE_MAX = 8

    /**
     * A short run on page [p]: the exercises that name it (of the cached modules, [moduleExercises] as module id to
     * exercise, and [more], a challenge's questions), then the other exercises of the modules it links, at most [max],
     * shuffled with [seed]; else the learner's grammar cards on it ([pool]); else [Practice.Ask].
     */
    fun practice(p: GrammarPage, moduleExercises: List<Pair<String, Exercise>>, more: List<Exercise>, pool: List<ReviewCard>, seed: Long, max: Int = PRACTICE_MAX): Practice {
        val playable = moduleExercises.filter { (_, e) -> e !is Exercise.Unsupported && e !is Exercise.Free }
        val named = (playable.map { it.second } + more).filter { it.grammar == p.id }
        // then the linked modules' others: about the rule, or next to it (Kam? beside Kje?)
        val linked = playable.filter { (m, _) -> m in p.modules }.map { it.second }
        val r = Random(seed)
        val picked = (named.distinct().shuffled(r) + linked.distinct().shuffled(r)).distinct().take(max)
        if (picked.isNotEmpty()) return Practice.Exercises(picked)
        val cards = cards(p, pool)
        return if (cards.isNotEmpty()) Practice.Cards(cards.shuffled(r).take(max)) else Practice.Ask
    }
}
