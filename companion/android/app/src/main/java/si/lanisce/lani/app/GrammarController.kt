package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.FormSlot
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.MistakeNote
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.AdaptedChoice
import si.lanisce.lani.game.AdaptedDialog
import si.lanisce.lani.game.Adaptive
import si.lanisce.lani.game.Confusion
import si.lanisce.lani.game.Forms
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.GrammarBook
import si.lanisce.lani.game.Introduction
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.RuleMeetings
import si.lanisce.lani.game.RuleRecord
import si.lanisce.lani.game.RuleSlip
import si.lanisce.lani.game.Traps
import si.lanisce.lani.game.TurnHint
import si.lanisce.lani.game.TurnHints
import si.lanisce.lani.game.WordForms
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import java.time.LocalDate

/**
 * Everything a grammar page shows for the learner: the [page], what they met of it ([record]), their grammar [cards] and
 * [mistakes] on it, how well it sits ([meter], 0 to 5, null: not practised yet), how far they have it ([mastery]: how its
 * turns are asked), which pages they have met ([met]: the related ones lead there), and how often the dialogs said its
 * rule before it was introduced ([meetings]).
 */
data class RuleView(
    val page: GrammarPage,
    val record: RuleRecord?,
    val cards: List<ReviewCard>,
    val mistakes: List<MistakeNote>,
    val meter: Int?,
    val met: Set<String>,
    val mastery: Mastery = Mastery.NEW,
    val meetings: RuleMeetings? = null,
)

/**
 * The grammar book (companion/GAME.md, "The grammar book"; data/Grammar.kt, game/GrammarBook.kt): its pages (the bundled
 * curated ones, then the bridge's with the tutor's), the page open over any screen ([open]), the pages met in a run and
 * not announced yet ([fresh]: the next answer's feedback says "📖 Nova stran v knjigi"), and asking the tutor about a page.
 */
class GrammarController(
    private val bridge: () -> Bridge?,
    private val game: GameController,
    private val content: ContentController,
    private val chat: ChatController,
    private val notices: Notices,
) {
    /** The book's language: the learner's own village's (a visit's language has no book here). */
    val language: String get() = L10n.ownPair.target.code

    private var bundledFor: String? = null
    private var bundled: List<GrammarPage> = emptyList()
    private var fromBridge: List<GrammarPage>? = null

    var pages by mutableStateOf<List<GrammarPage>>(emptyList())
        private set

    /** The page shown over whatever is on screen (a "why", a challenge's intro, the scroll), or none. */
    var open by mutableStateOf<String?>(null)
        private set

    /** Pages met during a run that no feedback has announced yet. */
    val fresh = mutableStateListOf<String>()

    init {
        refreshBundled()
    }

    /** The bundled pages of the book's language, read again when the language changed. */
    private fun refreshBundled() {
        val l = language
        if (l == bundledFor) return
        bundledFor = l
        bundled = Grammar.bundled(l)
        fromBridge = null
        pages = Grammar.merge(bundled, fromBridge)
    }

    /** The bridge's pages (the tutor's included); an older bridge, or none now, keeps the bundled ones. */
    suspend fun reload(b: Bridge) {
        refreshBundled()
        val got = try {
            b.grammar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // offline: the book stays as it was
        }
        fromBridge = got?.filter { it.language == language }
        pages = Grammar.merge(bundled, fromBridge)
    }

    fun page(id: String?): GrammarPage? = id?.let { i -> pages.firstOrNull { it.id == i } }

    /** Shows page [id] over the screen, meeting it (a page opened from an intro is met there). */
    fun show(id: String) {
        if (page(id) == null) return
        meet(listOf(id), announce = false)
        fresh.remove(id)
        open = id
    }

    fun close() {
        open = null
    }

    /** The chapter in the village book: the pages met, the rules only met in the dialogs so far, and how many are still to come. */
    fun chapter(s: GameState?): GrammarBook.Chapter = GrammarBook.chapter(pages, s, language)

    /**
     * The learner's level in the book's language ("A1": the level their readings and stories are at,
     * [ContentController.levelIn]: the treasure's new level before the node has it); A1 before the node said.
     */
    val level: String get() = content.levelIn(language)

    /** Everything page [id] shows for the learner now. */
    fun view(id: String): RuleView? {
        val p = page(id) ?: return null
        val s = game.state
        val d = content.dashboard
        val cards = Grammar.cards(p, d?.pool.orEmpty())
        val mistakes = Grammar.mistakes(p, d?.mistakes.orEmpty())
        val met = s?.let { GrammarBook.met(it, language) }.orEmpty()
        val record = met[id]
        return RuleView(
            p, record, cards, mistakes, GrammarBook.meter(record, cards, mistakes), met.keys,
            // above the learner's level and not introduced: not yet (its turns don't ask for it)
            Introduction.mastery(p.level, level, id in met, record, cards, mistakes),
            s?.let { Introduction.meetings(it, language)[id] },
        )
    }

    // --- mastery, adaptive turns and the learner's own traps (companion/GAME.md, "Mastery and adaptive turns") -------

    /** How far the learner has rule [id]; null for a page the book lacks. */
    fun mastery(id: String): Mastery? = view(id)?.mastery

    /** Whether the learner met page [id] (it unlocked: introduced). */
    fun met(id: String): Boolean = game.state?.let { GrammarBook.isMet(it, language, id) } == true

    /**
     * What keeps a word's form from being asked now (companion/GAME.md, "A word's forms"): [WordForms.locks] by this
     * learner's mastery of each page, each page's worked out once for the function returned (a review asks it often).
     */
    fun formLocks(): (FormSlot) -> List<String> {
        val masteries = HashMap<String, Mastery?>()
        val met = HashMap<String, Boolean>()
        return { slot ->
            WordForms.locks(
                slot,
                { id -> if (id in masteries) masteries[id] else mastery(id).also { masteries[id] = it } },
                { id -> met.getOrPut(id) { met(id) } },
            )
        }
    }

    /** The pages whose tags name mistake pattern [pattern] ("genitive_after_iz" → rodilnik-predlogi). */
    fun pagesOf(pattern: String): Set<String> {
        val m = listOf(MistakeNote(pattern, 0))
        return pages.filter { Grammar.mistakes(it, m).isNotEmpty() }.map { it.id }.toSet()
    }

    private var confusionsOf: List<MistakeNote>? = null
    private var confusionsMemo: List<Confusion> = emptyList()

    /** The learner's confusions (their mistakes' examples, [Traps.confusions]), for their own traps. */
    fun confusions(): List<Confusion> {
        val m = content.dashboard?.mistakes.orEmpty()
        if (m !== confusionsOf) {
            confusionsOf = m
            confusionsMemo = Traps.confusions(m)
        }
        return confusionsMemo
    }

    /** The scenes, whose lines are words known to be of the language (set by the view model). */
    var scenes: () -> List<SceneSpec> = { emptyList() }
    private var knownFrom: Triple<Any?, Any?, Any?>? = null
    private var knownWords: Set<String> = emptySet()

    /**
     * Whether [word] (lower case) is known to be a word of the book's language: in a scene's lines, a page's table or
     * examples, or one of the learner's cards. A trap made by a change of ending must be one ([Traps.ending]).
     */
    fun known(word: String): Boolean {
        val all = scenes()
        val pool = content.dashboard?.pool
        val from = knownFrom
        if (from == null || from.first !== all || from.second !== pages || from.third !== pool) {
            knownFrom = Triple(all, pages, pool)
            val texts = sequence {
                for (s in all) if (s.language == language) {
                    for (d in s.dialogs) for (l in d.lines) {
                        l.sl?.let { yield(it) }
                        for (c in l.choices) {
                            yield(c.sl)
                            c.reply?.let { yield(it.sl) }
                        }
                    }
                    for (o in s.objects) {
                        yield(o.sl)
                        o.plural?.let { yield(it) }
                        o.exampleSl?.let { yield(it) }
                    }
                }
                for (p in pages) {
                    p.examples.forEach { e -> yield(e.target(p.language)) }
                    p.table?.let { t -> (t.head + t.rows.flatten()).mapNotNull { it.words }.forEach { yield(it) } }
                }
                pool.orEmpty().forEach { yield(it.front) }
            }
            knownWords = texts.flatMap { Forms.words(it).asSequence() }.toSet()
        }
        return word.lowercase() in knownWords
    }

    /** The turn's puzzled reaction when its scene gives none: "Hm? Kako, prosim?", meant as the learner's base says it. */
    fun puzzled(): DialogReply = DialogReply(inTarget("adaptive.puzzled"), inBase("adaptive.puzzled"))

    /** The why of the learner's own trap: what they had, what was right. */
    private fun ownWhy(c: Confusion): String = inBase("adaptive.ownTrap", "wrong" to c.example.first, "right" to c.example.second)

    /**
     * A dialog in [language] as this learner meets it (companion/SCENES.md, "Adaptive turns", "Own traps", "Rules not yet"):
     * the turns of rules not introduced yet trimmed to what they can choose, or echoed; their own traps in the other form
     * turns; and how each turn is asked ([canSay]: something can listen). A dialog in another language than the book's (a
     * visit), as it is; in a book without pages (a village of another language), every rule is new.
     */
    fun adapt(d: Dialog, language: String, canSay: Boolean): AdaptedDialog {
        if (language != this.language) return AdaptedDialog(d)
        // QA's typed turns (QaHooks "turns:type", a debug build): every rule secure
        return Adaptive.dialog(d, QaHooks.mastery(::mastery), canSay) { gated, skip -> Traps.dialog(gated, confusions(), ::pagesOf, ::known, puzzled(), ::ownWhy, skip) }
    }

    // --- rules not yet: met in the dialogs, introduced when ripe (companion/GAME.md, "Rules not yet") ---------------

    /**
     * The learner passed dialog turns that said rules [ids] not introduced yet without asking for them: each is met once
     * more ([Introduction.met]). A rule of the next level met often enough on enough days ([Introduction.unlocks]) opens its
     * page, as a why's link would: "📖 Nova stran v knjigi" says so at once, and its turns ask for it from then on.
     */
    fun metInDialog(ids: Collection<String>) {
        val known = ids.distinct().filter { page(it) != null }
        if (known.isEmpty()) return
        val met = game.record { s -> Introduction.met(s, language, known, LocalDate.now()).let { it to Introduction.meetings(it, language) } } ?: return
        val open = known.filter { id -> Introduction.unlocks(met[id], page(id)?.level, level) && mastery(id) == Mastery.NOT_YET }
        if (open.isNotEmpty()) meet(open, announce = true)
    }

    /**
     * The rules ripe to introduce ([Introduction.ripe]: met in the dialogs [Introduction.RIPE] times or more, of the next
     * level, not introduced yet), for the tutor, most met first: each one's id, title, level, meetings, first and last day.
     */
    fun ripe(): List<Pair<GrammarPage, RuleMeetings>> {
        val s = game.state ?: return emptyList()
        val lv = level
        return Introduction.meetings(s, language).mapNotNull { (id, m) ->
            val p = page(id) ?: return@mapNotNull null
            (p to m).takeIf { Introduction.ripe(m, p.level, lv) && mastery(id) == Mastery.NOT_YET }
        }.sortedByDescending { it.second.times }
    }

    /**
     * What a session's report to the tutor carries about the rules not yet (data.grammar_ripe, companion/GAME.md "Rules not
     * yet"): the rules ripe to introduce, so the tutor can introduce one (a module, the page extended); nothing when none.
     * An older bridge passes it on as it is.
     */
    fun sessionExtras(): Map<String, JsonElement> {
        val ripe = ripe().takeIf { it.isNotEmpty() } ?: return emptyMap()
        return mapOf(
            "grammar_ripe" to buildJsonObject {
                put("level", level)
                put("rules", buildJsonArray {
                    for ((p, m) in ripe) add(buildJsonObject {
                        put("id", p.id)
                        put("title", p.titleIn("en"))
                        put("level", p.level)
                        put("met", m.times)
                        put("days", m.days)
                        put("first", m.first)
                        put("last", m.last)
                    })
                })
            },
        )
    }

    /** "🌱 Na vrsto pride kasneje · You'll learn this later: <titles>" for a turn's rules not yet ([ids]); null for none the book has. */
    fun later(ids: List<String>): String? {
        val titles = ids.mapNotNull { page(it)?.titleShown(L10n.pair) }.distinct().takeIf { it.isNotEmpty() } ?: return null
        return "🌱 ${bi("grammar.later")}: ${titles.joinToString(", ")}"
    }

    /**
     * The titles of a turn's rules not yet ([ids]) for the hint's 🔊 ([si.lanisce.lani.game.HintSpeech]): each page's in
     * the language learned and in the learner's base.
     */
    fun laterTitles(ids: List<String>): List<Pair<String, String>> =
        ids.mapNotNull(::page).map { it.target to it.titleIn(L10n.pair.base.code) }.distinct()

    /** A choice exercise on rule [rule] as this learner meets it: their own trap among its options, and how it is asked. */
    fun adapt(ex: Exercise.Choice, rule: String?, canSay: Boolean): AdaptedChoice {
        val known = rule?.takeIf { page(it) != null }
        return Adaptive.choice(ex, known, known?.let(::mastery), canSay) { options, answer ->
            Traps.options(options, answer, known, confusions(), ::pagesOf, ::known)
        }
    }

    /**
     * The learner meets the rules of [ids] (of pages there are): the pages not met yet unlock. In a run ([announce] false)
     * the next feedback says so; elsewhere a notice does.
     */
    fun meet(ids: Collection<String>, announce: Boolean = false): List<String> {
        val known = ids.filter { page(it) != null }
        if (known.isEmpty()) return emptyList()
        val met = game.record { GrammarBook.meet(it, language, known, LocalDate.now()) }.orEmpty()
        if (announce) met.lastOrNull()?.let { notices.banner = newPage(it) } else fresh.addAll(met.filter { it !in fresh })
        return met
    }

    /** The pages that module [id] practises (their `modules` name it): met when it is played. */
    fun meetModule(id: String) = meet(Grammar.ofModule(id, pages).map { it.id })

    /**
     * An answer ([verdict]) on rule [id]: the page unlocks (the next feedback announces it; with [announce], a notice
     * does at once: a dialog's pick has no feedback box) and the answer is counted; a right one keeps the whole sentence
     * ([said]), a wrong one what was written and what was right. A right one after the turn's hint ([hinted]) leaves the
     * rule's run as it was ([GrammarBook.answered]).
     */
    fun answered(id: String, verdict: Verdict, said: String?, answer: String, correct: String?, announce: Boolean = false, hinted: Boolean = false) {
        if (page(id) == null) return
        val right = verdict == Verdict.CORRECT
        val slip = if (verdict == Verdict.WRONG && correct != null && answer.isNotBlank()) RuleSlip(answer.take(120), correct.take(120)) else null
        val newly = game.record { GrammarBook.answered(it, language, id, right, said?.take(160), slip, LocalDate.now(), hinted) } ?: false
        if (!newly) return
        if (announce) notices.banner = newPage(id) else if (id !in fresh) fresh += id
    }

    /**
     * "📖 Namig · Hint" at a learner's turn ([line]; companion/SCENES.md, "The hint"): its rule, the question and its
     * trigger, a model with another word; null when the turn's rule has no page in the book. A dialog in another language
     * than the book's (a visit) has none: its pages aren't in this book. The pages [hidden] (rules not introduced yet, which
     * the turn doesn't ask for: [later]) are left out, as if the book lacked them.
     */
    fun hint(line: DialogLine, language: String = this.language, hidden: Collection<String> = emptyList()): TurnHint? =
        if (language != this.language) null else TurnHints.of(line, language) { id -> if (id in hidden) null else page(id) }

    /** The feedback said it: page [id] is no longer news. */
    fun announced(id: String) {
        fresh.remove(id)
    }

    /** A run ended: pages met in it that no feedback announced (a timed run's quick answers) get a notice. */
    fun announceRest() {
        fresh.lastOrNull()?.let { notices.banner = newPage(it) }
        fresh.clear()
    }

    /** "📖 Nova stran v knjigi · New page in the book: Kje? Predlogi kraja". */
    fun newPage(id: String): String = "📖 ${bi("grammar.newPage")}: ${page(id)?.target ?: id}"

    /** "💬 Vprašaj · Ask the tutor": the chat opens with the page attached (data.about_grammar). */
    fun ask(id: String) {
        val p = page(id) ?: return
        close()
        chat.askAbout(ChatContext("📖 ${p.titleShown(L10n.pair)}", about(p, id), key = "about_grammar"))
    }

    /** Nothing to practise the page with yet: the tutor is asked for a drill (data.grammar_drill), the chat opens. */
    fun askDrill(id: String) {
        val p = page(id) ?: return
        close()
        chat.ask(bi("grammar.drillRequest", "title" to p.target), buildJsonObject { put("grammar_drill", about(p, id)) })
    }

    /** The page as the tutor reads it along: its id and title, the rule in the learner's base, what they met of it. */
    private fun about(p: GrammarPage, id: String) = buildJsonObject {
        val base = L10n.pair.base.code
        put("id", id)
        put("title", p.titleIn("en"))
        put("title_target", p.target)
        put("level", p.level)
        put("source", p.source)
        put("rule", p.ruleIn(base))
        put("modules", buildJsonArray { p.modules.forEach { add(JsonPrimitive(it)) } })
        view(id)?.let { v ->
            v.record?.let { r ->
                put("met_on", r.on)
                put("right", r.right)
                put("wrong", r.wrong)
                if (r.slips.isNotEmpty()) put("slips", buildJsonArray { r.slips.forEach { s -> add(buildJsonObject { put("answer", s.answer); put("correct", s.correct) }) } })
            }
            if (v.mistakes.isNotEmpty()) put("mistakes", buildJsonArray { v.mistakes.forEach { add(JsonPrimitive(it.id)) } })
            v.meter?.let { put("how_well", it) }
            // how the app asks its turns now: not at all (not_yet: only met), choosing (new, learning), typing (secure), saying (mastered)
            put("mastery", v.mastery.name.lowercase())
            // how often the dialogs said it before it was introduced
            v.meetings?.takeIf { it.times > 0 }?.let { put("met_in_dialogs", it.times) }
        }
    }
}
