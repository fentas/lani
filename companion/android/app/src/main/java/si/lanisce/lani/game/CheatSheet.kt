package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.DeckCard
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.GrammarExample
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.GrammarTable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * What the village keeps of the cheat sheet ([GameState.sheet]; companion/README.md "Cheat sheet"): the tutor messages the
 * learner [pinned] (oldest first), and how often they open each card ([opens], by "<language>/<card key>": a count that
 * halves every [CheatSheet.HALF_LIFE_DAYS] days). A card never opened is new. The bridge reads it too (the tutor's list_cards).
 */
@Serializable
data class SheetState(
    val pinned: List<PinnedCard> = emptyList(),
    val opens: Map<String, CardUse> = emptyMap(),
)

/**
 * A tutor message the learner pinned ("📌 Na plonk listek · Pin to cheat sheet"): its [id] (the card's key is "pin:<id>"),
 * the [language] learned, the [title] (from its first bold words or its first line; the learner may rename it), the
 * message itself ([markdown]), the grammar pages it is about ([pages], guessed from the book), the learner's [level] then,
 * the day it was pinned ([on]) and the chat message it came from ([message]).
 */
@Serializable
data class PinnedCard(
    val id: String,
    val language: String,
    val title: String,
    val markdown: String,
    val pages: List<String> = emptyList(),
    val level: String? = null,
    val on: String = "",
    val message: String? = null,
)

/** How often a card was opened: [n] as of the day [on] (ISO); it halves every [CheatSheet.HALF_LIFE_DAYS] days after. */
@Serializable
data class CardUse(val n: Double = 0.0, val on: String = "")

/** Where a card comes from. */
enum class SheetKind {
    /** A page of the grammar book: its own card, or one made from it. */
    PAGE,
    /** A curated card no page fits (companion/cards). */
    DECK,
    /** A tutor message the learner pinned. */
    PINNED,
    /** A card the tutor published (publish_card). */
    TUTOR,
}

/** What a card shows. */
sealed interface CardFace {
    /** A bold [rule] line, a [table], [examples] and a 💡 [tip], in the learner's base (a page's, a curated card's). */
    data class Rule(val rule: String?, val table: GrammarTable?, val examples: List<GrammarExample>, val tip: String?) : CardFace

    /** The tutor's Markdown (a table or a list, as in the chat): a pinned message, a card the tutor published. */
    data class Text(val markdown: String) : CardFace
}

/**
 * A card of the sheet as it shows: its [key] ("page:biti", "deck:zaimki-osebe", "tutor:imeti", "pin:m-1a2b"), its title in
 * the learned language ([title]) and in the learner's base ([titleBase], null where it's the same or there is none), its
 * [level], the grammar [pages] it belongs to (they unlock it, and an exercise on one brings it first), its [face]; [start]:
 * there from the start; [order]: its place in the book (a curated card beside its first page).
 */
data class SheetCard(
    val key: String,
    val kind: SheetKind,
    val emoji: String,
    val title: String,
    val titleBase: String?,
    val level: String?,
    val pages: List<String>,
    val face: CardFace,
    val start: Boolean = false,
    val machineWritten: Boolean = true,
    val review: String? = null,
    val order: Int = 0,
)

/**
 * The cheat sheet ("📋 Plonk listek · Cheat sheet", companion/README.md "Cheat sheet"; GAME.md): compact reference cards of
 * the basics, one from each page of the grammar book (its own `card`, else its table, its rule's first sentence and two
 * examples), the curated ones no page fits, the tutor's, and the tutor messages the learner pinned. A page's card unlocks
 * the day the learner meets the page (the book's "met", [GrammarBook.meet]: an exercise, a module, a school unit's
 * introduction), a curated one with the first of its pages, an A1 basic ([SheetCard.start]) and the pinned and the tutor's
 * at once. Locked cards aren't listed, only counted. Opened, the sheet comes in order of relevance ([order]). Pure.
 */
object CheatSheet {
    /** An open's count halves in this many days. */
    const val HALF_LIFE_DAYS = 14.0

    /** A card opened at least this much lately (an open in the last two weeks or so) is one of the most opened. */
    const val OFTEN = 0.75

    /** At most this many pinned cards; the sheet says so when it's full. */
    const val MAX_PINNED = 50

    /** A pinned message's Markdown is kept up to this length (the tutor's cards' limit too). */
    const val MAX_MARKDOWN = 2400

    /** At most this many open counts are kept (the least used go first). */
    const val MAX_OPENS = 300

    /** A card's title is kept up to this length. */
    const val MAX_TITLE = 80

    const val PAGE = "page:"
    const val DECK = "deck:"
    const val TUTOR = "tutor:"
    const val PIN = "pin:"

    // --- the cards ---------------------------------------------------------------------------------------------------

    /**
     * Page [p]'s card for a learner explaining in [base]: its own `card` where it has one (the page's title, table and first
     * two examples where the card leaves them out), else the page's table, its rule's first sentence and two examples.
     */
    fun of(p: GrammarPage, base: String, order: Int = 0): SheetCard {
        val c = p.card
        val target = c?.title?.get(p.language) ?: p.target
        val inBase = c?.title?.let { Grammar.text(it, base) } ?: p.titleIn(base)
        return SheetCard(
            key = PAGE + p.id, kind = SheetKind.PAGE, emoji = p.emoji, title = target, titleBase = inBase.takeIf { it != target && base != p.language },
            level = p.level, pages = listOf(p.id),
            face = CardFace.Rule(
                rule = c?.rule?.let { Grammar.text(it, base) } ?: ruleLine(p.ruleIn(base)),
                table = c?.table ?: p.table,
                examples = c?.examples ?: p.examples.take(2),
                tip = c?.tip?.let { Grammar.text(it, base) },
            ),
            start = c?.start == true, machineWritten = p.machineWritten, review = p.review, order = order,
        )
    }

    /** A curated card's, or the tutor's ([DeckCard.byTutor]), for a learner explaining in [base]. */
    fun of(d: DeckCard, base: String, order: Int = 0): SheetCard {
        val target = d.title[d.language] ?: Grammar.text(d.title, base)
        val inBase = Grammar.text(d.title, base)
        return SheetCard(
            key = (if (d.byTutor) TUTOR else DECK) + d.id, kind = if (d.byTutor) SheetKind.TUTOR else SheetKind.DECK, emoji = d.emoji,
            title = target, titleBase = inBase.takeIf { it != target && base != d.language }, level = d.level, pages = d.grammar,
            face = d.markdown?.let { CardFace.Text(it) } ?: CardFace.Rule(d.rule?.let { Grammar.text(it, base) }, d.table, d.examples, d.tip?.let { Grammar.text(it, base) }),
            start = d.start, machineWritten = d.machineWritten, review = d.review, order = order,
        )
    }

    /** A pinned message's card. */
    fun of(p: PinnedCard, order: Int = 0): SheetCard =
        SheetCard(PIN + p.id, SheetKind.PINNED, "📌", p.title, null, p.level, p.pages, CardFace.Text(p.markdown), machineWritten = true, order = order)

    /**
     * Every card of [language] in the book's order: each page's (of [pages]), a curated card right after the first of its
     * pages (at the end when none is in the book), then the tutor's, then the pinned ones, oldest first.
     */
    fun cards(pages: List<GrammarPage>, deck: List<DeckCard>, pinned: List<PinnedCard>, language: String, base: String): List<SheetCard> {
        val index = pages.withIndex().associate { (i, p) -> p.id to i }
        val book = pages.filter { it.language == language }.map { of(it, base, (index[it.id] ?: 0) * 2) }
        val end = pages.size * 2
        val curated = deck.filter { it.language == language && !it.byTutor }.map { d -> of(d, base, d.grammar.mapNotNull { index[it] }.minOrNull()?.let { it * 2 + 1 } ?: end) }
        val tutor = deck.filter { it.language == language && it.byTutor }.mapIndexed { i, d -> of(d, base, end + 1 + i) }
        val mine = pinned.filter { it.language == language }.mapIndexed { i, p -> of(p, end + 1 + tutor.size + i) }
        return (book + curated + tutor + mine).sortedBy { it.order }
    }

    /**
     * Whether [card] is on the sheet for a learner who met the pages [met]: a page's once its page is met, a curated card's
     * once one of its pages is, an A1 basic from the start, the pinned and the tutor's at once.
     */
    fun unlocked(card: SheetCard, met: Set<String>): Boolean = when (card.kind) {
        SheetKind.PAGE, SheetKind.DECK -> card.start || card.pages.any { it in met }
        SheetKind.PINNED, SheetKind.TUTOR -> true
    }

    /** Where the village keeps [card]'s opens: "<language>/<key>". */
    fun useKey(language: String, card: String): String = "$language/$card"

    /** [use]'s count on [today]: halved for every [HALF_LIFE_DAYS] days since its day. */
    fun weight(use: CardUse?, today: LocalDate): Double {
        if (use == null || use.n <= 0.0) return 0.0
        val day = runCatching { LocalDate.parse(use.on) }.getOrNull() ?: return use.n
        val days = ChronoUnit.DAYS.between(day, today).coerceAtLeast(0)
        return use.n * 0.5.pow(days / HALF_LIFE_DAYS)
    }

    /** The learner opened [card] (a key of [language]) [today]: its count, decayed to today, grows by one; it is new no more. */
    fun opened(s: GameState, language: String, card: String, today: LocalDate): GameState {
        val k = useKey(language, card)
        val opens = s.sheet.opens + (k to CardUse(weight(s.sheet.opens[k], today) + 1.0, today.toString()))
        return s.copy(sheet = s.sheet.copy(opens = trim(opens, today)))
    }

    /** The counts kept: at most [MAX_OPENS], the least used dropped first. */
    private fun trim(opens: Map<String, CardUse>, today: LocalDate): Map<String, CardUse> =
        if (opens.size <= MAX_OPENS) opens else opens.entries.sortedByDescending { weight(it.value, today) }.take(MAX_OPENS).associate { it.key to it.value }

    // --- the order ---------------------------------------------------------------------------------------------------

    /**
     * The sheet as it opens: its unlocked [cards] in order of relevance ([order]), how many are still [locked], which are
     * new ([fresh]: never opened) and which came first for the exercise it was opened from ([context]).
     */
    data class Sheet(val cards: List<SheetCard>, val locked: Int, val fresh: Set<String>, val context: Set<String>) {
        /** The levels of the cards on it, in order ("A1", "A2"): the filter's chips. */
        val levels: List<String> get() = cards.mapNotNull { it.level }.distinct().sorted()
    }

    /**
     * The sheet of [all] (every card of [language]) for a learner who met the pages [met], with their opens ([uses]), opened
     * from [context] (the grammar pages of the exercise or the worksheet on screen: the exercise's own first, then the
     * module's or the unit's), [today]; [focus]: the card it was opened at, first (a pinned message's chip: "📋 Na plonk
     * listku"); [secure]: whether they have a page's rule secure or mastered (the grammar book's masteries).
     */
    fun sheet(
        all: List<SheetCard>, met: Set<String>, uses: Map<String, CardUse>, language: String, context: List<String>, today: LocalDate,
        focus: String? = null, secure: (String) -> Boolean,
    ): Sheet {
        val (open, closed) = all.partition { unlocked(it, met) }
        val fresh = open.filter { useKey(language, it.key) !in uses }.map { it.key }.toSet()
        // the card it was opened at (a pinned message's chip) first, then the order of relevance
        val ordered = order(open, context, uses, language, today, secure).sortedBy { it.key != focus }
        val ctx = context.toSet()
        return Sheet(ordered, closed.size, fresh, ordered.filter { c -> c.pages.any { it in ctx } }.map { it.key }.toSet())
    }

    /**
     * The order of relevance: (1) the cards of the [context]'s pages (in the context's order: the exercise's own page
     * first; a page's own card before a curated one, the learner's pinned, the tutor's); then (2) the most opened lately
     * (weight at least [OFTEN], most first); (3) the new ones (never opened); (4) the rest, in the book's order. A card
     * whose pages the learner all has secure or mastered ([secure]) drifts to the bottom, below the rest, unless it is
     * the context's.
     */
    fun order(cards: List<SheetCard>, context: List<String>, uses: Map<String, CardUse>, language: String, today: LocalDate, secure: (String) -> Boolean): List<SheetCard> {
        val rank = context.distinct().withIndex().associate { (i, id) -> id to i }
        fun rankOf(c: SheetCard): Int? = c.pages.mapNotNull { rank[it] }.minOrNull()
        val w = cards.associate { it.key to weight(uses[useKey(language, it.key)], today) }
        fun wOf(c: SheetCard) = w[c.key] ?: 0.0
        val (inContext, rest) = cards.partition { rankOf(it) != null }
        val first = inContext.sortedWith(compareBy<SheetCard>({ rankOf(it) }, { it.kind.ordinal }, { -wOf(it) }, { it.order }))
        val (down, up) = rest.partition { c -> c.pages.isNotEmpty() && c.pages.all(secure) }
        val often = up.filter { wOf(it) >= OFTEN }.sortedWith(compareBy<SheetCard>({ -wOf(it) }, { it.order }))
        val fresh = up.filter { wOf(it) < OFTEN && useKey(language, it.key) !in uses }.sortedBy { it.order }
        val others = up.filter { wOf(it) < OFTEN && useKey(language, it.key) in uses }.sortedBy { it.order }
        return first + often + fresh + others + down.sortedWith(compareBy<SheetCard>({ -wOf(it) }, { it.order }))
    }

    // --- a rule's line, a tutor message pinned -----------------------------------------------------------------------

    private val SENTENCE_END = Regex("""[.!?](?=\s|$)""")

    /**
     * A rule's first sentence (of its first paragraph), as a card's rule line: up to the first full stop outside bold, at
     * most [max] characters (cut at a word, "…").
     */
    fun ruleLine(rule: String, max: Int = 240): String {
        val para = rule.trim().split(Regex("""\n\s*\n""")).firstOrNull()?.trim().orEmpty()
        val end = SENTENCE_END.findAll(para).firstOrNull { m -> para.substring(0, m.range.first).windowed(2).count { it == "**" } % 2 == 0 }
        val first = end?.let { para.substring(0, it.range.last + 1) } ?: para
        if (first.length <= max) return first
        val cut = first.take(max).substringBeforeLast(' ').trimEnd(',', ';', ':')
        // an unclosed bold at the cut is closed
        return cut + (if (cut.windowed(2).count { it == "**" } % 2 == 1) "**" else "") + " …"
    }

    private val TABLE_ROW = Regex("""^\s*\|.*\|.*$""")
    private val LIST_ITEM = Regex("""^\s*(?:[-*•]\s+|\d+[.)]\s+)\S""")

    /** Whether a tutor message can be pinned: it has a table (two rows or more) or a list (two items or more). */
    fun pinnable(markdown: String): Boolean {
        val lines = markdown.lines()
        return lines.count { TABLE_ROW.matches(it) } >= 2 || lines.count { LIST_ITEM.containsMatchIn(it) } >= 2
    }

    private val BOLD = Regex("""\*\*(.+?)\*\*""")
    private val MARKS = Regex("""[*_`#>]""")
    private val BULLET = Regex("""^(?:[-*•]|\d+[.)])\s+""")
    private val RULE_LINE = Regex("""^[-|:\s]+$""")

    /** A first line this short is a pinned message's title as it is. */
    const val SHORT_TITLE = 48

    /**
     * A pinned message's title, as plain text: its heading ("# …"), else its first line when it's short ([SHORT_TITLE]),
     * else its first bold words (in its first lines), else its first line cut; at most [MAX_TITLE] characters. A table's
     * rows are no title.
     */
    fun titleOf(markdown: String): String {
        fun plain(s: String) = s.replace(BULLET, "").replace(MARKS, "").trim().trimEnd(':', ',', ';', '.', '—', '–', '-').trim()
        val lines = markdown.lines().map { it.trim() }.filter { it.isNotEmpty() && !TABLE_ROW.matches(it) && !RULE_LINE.matches(it) }
        val heading = lines.take(3).firstOrNull { it.startsWith("#") }?.let(::plain)
        val first = lines.firstOrNull()?.let(::plain).orEmpty()
        val bold = lines.take(3).firstNotNullOfOrNull { l -> BOLD.find(l)?.groupValues?.get(1)?.let(::plain)?.takeIf { it.length >= 3 } }
        val t = heading?.takeIf { it.isNotEmpty() } ?: first.takeIf { it.length in 1..SHORT_TITLE } ?: bold ?: first
        return (if (t.length <= MAX_TITLE) t else t.take(MAX_TITLE - 1).substringBeforeLast(' ') + "…").ifBlank { "📌" }
    }

    /** Short words too common to tell one page from another. */
    private val COMMON = setOf("je", "in", "v", "na", "z", "s", "k", "se", "da", "ne", "pa", "ali", "to", "ki", "za", "po", "od", "do", "iz", "o", "a", "the", "to", "of")

    /**
     * The grammar pages a pinned message is about, guessed from the book: its words in bold (the forms the tutor stressed)
     * against each page's table (and its own card's); the best page (and a second as good, nearly), when at least [MIN_MATCH] of its forms are
     * there; none else.
     */
    fun guessPages(markdown: String, pages: List<GrammarPage>): List<String> {
        val stressed = BOLD.findAll(markdown).flatMap { Forms.words(it.groupValues[1]) }.filter { it.length >= 2 && it !in COMMON }.toSet()
        if (stressed.isEmpty()) return emptyList()
        val scores = pages.map { p ->
            val forms = listOfNotNull(p.table, p.card?.table).flatMap { t -> (t.head + t.rows.flatten()).mapNotNull { it.words }.flatMap(Forms::words) }.toSet()
            p.id to (stressed intersect forms).size
        }.filter { it.second >= MIN_MATCH }.sortedByDescending { it.second }
        val best = scores.firstOrNull()?.second ?: return emptyList()
        return scores.filter { it.second * 4 >= best * 3 }.take(2).map { it.first }
    }

    /** How many of a page's forms a pinned message must stress to be about it. */
    const val MIN_MATCH = 3

    /**
     * Pins tutor message [message] ([markdown]) as a card of [language] [today], about [pages] ([guessPages]), at the
     * learner's [level]: counted as opened once (the learner chose it: not new, among the most opened for a while). The village as it was when it is pinned already or the sheet is
     * full ([MAX_PINNED]); the pinned card's id else.
     */
    fun pin(s: GameState, language: String, message: String, markdown: String, pages: List<String>, level: String?, today: LocalDate): Pair<GameState, String?> {
        if (s.sheet.pinned.any { it.message == message }) return s to null
        if (s.sheet.pinned.size >= MAX_PINNED) return s to null
        val id = "m-" + message.filter { it.isLetterOrDigit() || it == '-' }.take(40).ifEmpty { s.sheet.pinned.size.toString() }
        val card = PinnedCard(id, language, titleOf(markdown), markdown.trim().take(MAX_MARKDOWN), pages, level, today.toString(), message)
        // pinning is using it: one open, so it is among the most opened for a while, and not new
        val opens = s.sheet.opens + (useKey(language, PIN + id) to CardUse(1.0, today.toString()))
        return s.copy(sheet = s.sheet.copy(pinned = s.sheet.pinned + card, opens = opens)) to id
    }

    /** The pinned card [id] under the learner's own [title] (blank: as it was). */
    fun rename(s: GameState, id: String, title: String): GameState {
        val t = title.trim().take(MAX_TITLE).takeIf { it.isNotEmpty() } ?: return s
        return s.copy(sheet = s.sheet.copy(pinned = s.sheet.pinned.map { if (it.id == id) it.copy(title = t) else it }))
    }

    /** The pinned card [id] off the sheet, its opens with it. */
    fun unpin(s: GameState, id: String): GameState {
        val p = s.sheet.pinned.firstOrNull { it.id == id } ?: return s
        return s.copy(sheet = s.sheet.copy(pinned = s.sheet.pinned - p, opens = s.sheet.opens - useKey(p.language, PIN + id)))
    }
}
