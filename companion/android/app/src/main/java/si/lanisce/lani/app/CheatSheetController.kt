package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Cards
import si.lanisce.lani.data.DeckCard
import si.lanisce.lani.game.CheatSheet
import si.lanisce.lani.game.GrammarBook
import si.lanisce.lani.game.LookUp
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.PinnedCard
import si.lanisce.lani.game.SheetCard
import si.lanisce.lani.game.SheetLookups
import si.lanisce.lani.game.SheetWatch
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

/**
 * The cheat sheet open over the screen: the grammar pages of what it was opened from ([context]: an exercise's own page
 * first, then its run's or module's, a worksheet's unit's), whose cards come first; empty from Home and the chat.
 * [focus]: the card it opens at, first and open (a pinned message's "📋 Na plonk listku").
 */
data class CheatOpen(val context: List<String> = emptyList(), val focus: String? = null)

/**
 * "📋 Plonk listek · Cheat sheet" (companion/README.md "Cheat sheet"; game/CheatSheet.kt): its cards (the grammar book's
 * pages', the curated ones no page fits, the tutor's, the pinned), the sheet open over any screen ([open]), opening a card,
 * pinning a tutor message. Opening it is help, not a hint: opened during an exercise ([watch]), a right answer pays in
 * full but is right with help (its review quality at most 4), and what was looked up goes with the answer to the tutor.
 */
class CheatSheetController(
    private val game: GameController,
    private val grammar: GrammarController,
    private val notices: Notices,
) {
    /** The sheet's language: the grammar book's (the learner's own village's). */
    val language: String get() = grammar.language

    private var bundledFor: String? = null
    private var bundled: List<DeckCard> = emptyList()
    private var fromBridge: List<DeckCard>? = null

    /** The curated cards no page fits, then the tutor's. */
    var deck by mutableStateOf<List<DeckCard>>(emptyList())
        private set

    /** The sheet over whatever is on screen, or none. */
    var open by mutableStateOf<CheatOpen?>(null)
        private set

    init {
        refreshBundled()
    }

    private fun refreshBundled() {
        val l = language
        if (l == bundledFor) return
        bundledFor = l
        bundled = Cards.bundled(l)
        fromBridge = null
        deck = Cards.merge(bundled, fromBridge)
    }

    /** The bridge's cards (the tutor's included); an older bridge, or none now, keeps the bundled ones. */
    suspend fun reload(b: Bridge) {
        refreshBundled()
        val got = try {
            b.cards()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // offline: the sheet stays as it was
        }
        fromBridge = got?.filter { it.language == language }
        deck = Cards.merge(bundled, fromBridge)
    }

    /**
     * Opens the sheet; [context]: the grammar pages of the exercise or the worksheet it is opened from, the most relevant
     * first; [focus]: the card it opens at (its key).
     */
    fun show(context: List<String> = emptyList(), focus: String? = null) {
        open = CheatOpen(context.distinct().filter { grammar.page(it) != null }, focus)
        // over an exercise not answered yet: it is looked up (a worksheet's items as they are now kept)
        lookups.sheetOpened()
    }

    // --- looked up during an exercise (companion/README.md "Cheat sheet") ------------------------------------------------

    /** Which exercise the sheet's opens count on now: the one on screen, not answered yet. */
    private val lookups = SheetLookups()

    /**
     * An exercise appeared (ui/PlayerScreen's ExerciseRun): until its answer ([answered]) or the run left ([unwatch]), the
     * sheet opened (by the exercise's 📋, or the chat's over it) and the cards opened in it count on it. Opened before
     * (from Home, the chat), the sheet counts for nothing.
     */
    fun watch(): SheetWatch = lookups.exercise()

    /** Exercise [w] is gone unanswered (the run left, its clock ran out). */
    fun unwatch(w: SheetWatch) = lookups.gone(w)

    /**
     * Exercise [w] was answered: what was looked up during it (null: the sheet stayed closed), its pages counted as looked
     * up today ([CheatSheet.lookedUp]: the tutor's get_village and get_course show the most looked up).
     */
    fun answered(w: SheetWatch): LookUp? {
        val l = lookups.answered(w) ?: return null
        if (l.pages.isNotEmpty()) game.record { CheatSheet.lookedUp(it, language, l.pages, LocalDate.now()) to Unit }
        return l
    }

    /** A worksheet came on screen: [now] gives its items' answers as they are (the sheet's first open over it keeps them). */
    fun itemsOnScreen(now: () -> List<String>) {
        lookups.items = now
    }

    /** The worksheet of [now] went off screen. */
    fun itemsGone(now: () -> List<String>) {
        if (lookups.items === now) lookups.items = null
    }

    /** For a worksheet's items as they are now ([now]), checked: which were answered after the sheet was opened over it. */
    fun itemsAfter(now: List<String>): List<Boolean> = lookups.itemsAfter(now)

    fun close() {
        open = null
    }

    /** The learner's pinned cards of the sheet's language. */
    val pinned: List<PinnedCard> get() = game.state?.sheet?.pinned.orEmpty().filter { it.language == language }

    /** Every card of the sheet's language, in the book's order. */
    fun all(): List<SheetCard> = CheatSheet.cards(grammar.pages, deck, pinned, language, L10n.pair.base.code)

    /** The pages the learner met (their cards are unlocked). */
    private fun met(): Set<String> = game.state?.let { GrammarBook.met(it, language).keys }.orEmpty()

    /** The sheet as it opens now: in order of relevance for [context] ([focus] first), how many cards are still locked, which are new. */
    fun sheet(context: List<String>, focus: String? = null): CheatSheet.Sheet {
        val masteries = HashMap<String, Mastery?>()
        val secure = { id: String -> masteries.getOrPut(id) { grammar.mastery(id) }.let { it == Mastery.SECURE || it == Mastery.MASTERED } }
        return CheatSheet.sheet(all(), met(), game.state?.sheet?.opens.orEmpty(), language, context, LocalDate.now(), focus, secure)
    }

    /** How many cards are new (unlocked, never opened): Home's tile says so. */
    fun newCount(): Int {
        val met = met()
        val uses = game.state?.sheet?.opens.orEmpty()
        return all().count { CheatSheet.unlocked(it, met) && CheatSheet.useKey(language, it.key) !in uses }
    }

    /** How many cards are on the sheet now. */
    fun unlockedCount(): Int {
        val met = met()
        return all().count { CheatSheet.unlocked(it, met) }
    }

    /** The learner opened card [key]: counted (it is new no more); over an exercise, looked up for it ([watch]). */
    fun opened(key: String) {
        game.record { CheatSheet.opened(it, language, key, LocalDate.now()) to Unit }
        lookups.cardOpened(key) { all().firstOrNull { it.key == key }?.pages.orEmpty() }
    }

    /**
     * The school's hook (and anything else that introduces rules): the learner met the grammar pages [ids] (a unit's
     * introduction, a worksheet): the book's pages unlock and so do their cards ([GrammarController.meet]); with
     * [announce], a notice says so at once (outside a run). Returns the pages newly met.
     */
    fun met(ids: Collection<String>, announce: Boolean = true): List<String> = grammar.meet(ids, announce)

    /** The pinned card of chat message [id], if it is pinned. */
    fun pinnedFrom(id: String): PinnedCard? = pinned.firstOrNull { it.message == id }

    /** Whether chat message [m] can go on the sheet: the tutor's, with a table or a list. */
    fun pinnable(m: ChatMessage): Boolean = !m.fromMe && CheatSheet.pinnable(m.text)

    /**
     * "📌 Na plonk listek · Pin to cheat sheet": tutor message [m] as a card, its title from its first bold words or its
     * first line, its pages guessed from the book. A notice says it's there (or that the sheet is full).
     */
    fun pin(m: ChatMessage) {
        if (pinnedFrom(m.id) != null) return
        val pages = CheatSheet.guessPages(m.text, grammar.pages.filter { it.language == language })
        val id = game.record { CheatSheet.pin(it, language, m.id, m.text, pages, grammar.level, LocalDate.now()) }
        notices.banner = if (id != null) "📌 ${bi("cheatSheet.onSheet")}" else if (pinned.size >= CheatSheet.MAX_PINNED) "📋 ${bi("cheatSheet.full", "n" to CheatSheet.MAX_PINNED)}" else null
    }

    fun rename(id: String, title: String) {
        game.record { CheatSheet.rename(it, id, title) to Unit }
    }

    fun unpin(id: String) {
        game.record { CheatSheet.unpin(it, id) to Unit }
    }

    /** The tutor published a card (card_published): the sheet has it at once, marked new. */
    fun published(title: String?, note: String?) {
        notices.banner = "📋 ${note ?: "${bi("cheatSheet.newCard")}: ${title.orEmpty()}"}"
    }
}
