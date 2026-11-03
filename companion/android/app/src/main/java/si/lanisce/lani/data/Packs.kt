package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.villagers.BaseLabel
import si.lanisce.lani.game.villagers.Label
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import kotlin.random.Random

// --- word packs (lani.pack/v0, see companion/bridge/src/packs.ts) ---------------------

@Serializable
data class PackGiver(val name: String, val emoji: String = "🧑")

/**
 * A word of a pack, in the pack's language ([lang]: "sl" in Jan's packs, "it" in the Italian ones; the word says it,
 * see [parsePack]) with its translations: [word] is what is learned (and typed), [meaning] what it means in the learner's
 * base language (English when the pack hasn't got theirs), the same for the example and the note.
 */
@Serializable
data class PackWord(
    val id: String,
    val sl: String? = null,
    val en: String = "",
    val it: String? = null,
    val de: String? = null,
    val emoji: String? = null,
    /** m | f | n */
    val gender: String? = null,
    val plural: String? = null,
    @SerialName("example_sl") val exampleSl: String? = null,
    @SerialName("example_en") val exampleEn: String? = null,
    @SerialName("example_it") val exampleIt: String? = null,
    @SerialName("example_de") val exampleDe: String? = null,
    /** In English. */
    val note: String? = null,
    @SerialName("note_sl") val noteSl: String? = null,
    @SerialName("note_it") val noteIt: String? = null,
    @SerialName("note_de") val noteDe: String? = null,
    /** The pack's language, which the word is in: its pack's `language` (see [parsePack]); Slovene when unknown. */
    val lang: String? = null,
    /**
     * One spelling, two words (the kitchen's bowl, skleda, and the green's, krogla): this word is its own card, and another
     * card of its spelling has it only when it means it ([PackSession.means] with [meanings]). A bridge from before it never
     * says so: there the two stay one word.
     */
    val homonym: Boolean = false,
) {
    private val language: String get() = lang ?: Lang.SL.code

    /** What the word means, in every language but its own, part by part ([PackSession.meaningParts]). */
    val meanings: Set<String> get() = listOf("sl", "en", "it", "de").filter { it != language }.mapNotNull(::text).flatMap(PackSession::meaningParts).toSet()

    private fun text(code: String): String? = when (code) {
        "sl" -> sl
        "en" -> en.takeIf { it.isNotEmpty() }
        "it" -> it
        "de" -> de
        else -> null
    }

    private fun example(code: String): String? = when (code) {
        "sl" -> exampleSl
        "en" -> exampleEn
        "it" -> exampleIt
        "de" -> exampleDe
        else -> null
    }

    /**
     * The language the translations are in for a learner explaining in [base]: theirs when the word has it, else English;
     * an English word's "en" is the word itself, so then another language it has.
     */
    private fun baseOf(base: Lang): String = base.code.takeIf { it != language && text(it) != null }
        ?: Lang.EN.code.takeIf { it != language }
        ?: Lang.entries.map { it.code }.firstOrNull { it != language && text(it) != null } ?: language

    /** What is learned: "družina", "la famiglia". */
    val word: String get() = text(language) ?: sl ?: en

    /** What it means for the learner of the moment: "family" for Jan, "družina" for a learner of Italian from Slovene. */
    val meaning: String get() = meaningIn(L10n.pair.base)

    fun meaningIn(base: Lang): String = text(baseOf(base)) ?: en

    /** Its example in the pack's language, and in the learner's base (else English). */
    val example: String? get() = example(language)
    val exampleMeaning: String? get() = example(baseOf(L10n.pair.base)) ?: exampleEn?.takeIf { language != Lang.EN.code }

    /** Its note, in the learner's base language when the pack has one, else in English. */
    val noteText: String? get() = when (L10n.pair.base) {
        Lang.SL -> noteSl
        Lang.IT -> noteIt
        Lang.DE -> noteDe
        Lang.EN -> null
    } ?: note
}

/** A pack in GET /packs: metadata and progress, no words. */
@Serializable
data class PackInfo(
    val id: String,
    /** "Družina · Family"; a pack may name it per language, read in the learner's pair ([Label]). */
    @Serializable(with = Label::class) val title: String,
    val emoji: String = "📚",
    val level: String = "A1",
    /** In English, or per language (read in the learner's base, [BaseLabel]). */
    @Serializable(with = BaseLabel::class) val description: String? = null,
    val giver: PackGiver? = null,
    /** curated | tutor */
    val source: String = "curated",
    val total: Int = 0,
    val learned: Int = 0,
    /** The calendar festival whose words these are (game/Calendar.kt's id), for a festival's pack ("🎉 praznik · feast"). */
    val festival: String? = null,
    /** The village project whose words these are (game/Projects.kt's id), for a project's pack ("🏗️ projekt · project"). */
    val project: String? = null,
) {
    val left: Int get() = (total - learned).coerceAtLeast(0)
    val done: Boolean get() = total > 0 && learned >= total
}

/** GET /packs/:id: the words, plus the ids Jan already has. */
@Serializable
data class Pack(
    val id: String,
    @Serializable(with = Label::class) val title: String,
    val emoji: String = "📚",
    val level: String = "A1",
    @Serializable(with = BaseLabel::class) val description: String? = null,
    val giver: PackGiver? = null,
    val source: String = "curated",
    val words: List<PackWord>,
    val learned: List<String> = emptyList(),
    /** The calendar festival whose words these are (see [PackInfo.festival]). */
    val festival: String? = null,
    /** The village project whose words these are (see [PackInfo.project]). */
    val project: String? = null,
    /** The language of its words: "sl" (Jan's packs, and when it doesn't say), "it" … */
    val language: String = "sl",
)

fun parsePacks(raw: String): List<PackInfo> = json.decodeFromString(raw)

/** A pack, each word knowing the pack's language (what [PackWord.word] is in). */
fun parsePack(raw: String): Pack = json.decodeFromString<Pack>(si.lanisce.lani.l10n.Learner.current.renderJson(raw)).let { p -> p.copy(words = p.words.map { if (it.lang == null) it.copy(lang = p.language) else it }) }

/** Choosing words, building their practice, and grading it for SM-2. */
object PackSession {
    /** The spaced-repetition item a pack word becomes (as the bridge names it). */
    fun itemId(packId: String, wordId: String) = "vocab_${packId}_$wordId"

    /**
     * A word as the bridge compares it with the learner's cards (`normWord`, bridge/src/packs.ts): lower case, no . ! ? ¿ ¡
     * or comma, single spaces. A pack's word with the same key as a card the learner has is one they have: the node
     * doesn't teach it again.
     */
    fun wordKey(s: String): String = s.lowercase(SLOVENE).replace(PUNCTUATION, "").replace(SPACES, " ").trim()

    /**
     * The words a card's [content] holds, each as a [wordKey] (the bridge's `vocabularyWords`): every alternative ("dober dan
     * / živjo (informal)" → "dober dan", "živjo"), without what's in brackets.
     */
    fun wordKeys(content: String): List<String> =
        content.split(" / ").map { wordKey(it.replace(BRACKETS, "")) }.filter { it.isNotEmpty() }

    /**
     * What a text means, part by part, compared loosely (the bridge's `meaningParts`, bridge/src/packs.ts): each of its
     * meanings ("fair, market", "a / b") and what's in its brackets, lower case, an article left out.
     */
    fun meaningParts(s: String): Set<String> =
        (listOf(s.replace(BRACKETS, "")) + IN_BRACKETS.findAll(s).map { it.groupValues[1] })
            .flatMap { it.split(" / ", ",", ";") }
            .map { wordKey(it).replace(MEANING_ARTICLE, "") }
            .filter { it.isNotEmpty() }
            .toSet()

    /** Whether a card with the back [back] means one of [meanings] (a word's: [PackWord.meanings]). */
    fun means(back: String, meanings: Set<String>): Boolean = meaningParts(back).any { it in meanings }

    /** Whether the line [translation] says a word of [meaning], another ending too: krogla in "kroglo", wheel in "wheels". */
    fun mentions(meaning: String, translation: String): Boolean {
        val said = WORD.findAll(translation.lowercase()).map { it.value }.toList()
        return meaningParts(meaning).flatMap { p -> WORD.findAll(p).map { it.value } }.filter { it.length >= 3 }.any { g ->
            said.any { t -> g.commonPrefixWith(t).length.let { n -> n >= maxOf(3, g.length - 2) && t.length - n <= 3 } }
        }
    }

    private val SLOVENE = java.util.Locale.forLanguageTag("sl")
    private val PUNCTUATION = Regex("[.!?¿¡,]")
    private val SPACES = Regex("\\s+")
    private val BRACKETS = Regex("\\s*\\([^)]*\\)")
    private val IN_BRACKETS = Regex("\\(([^)]*)\\)")
    private val WORD = Regex("\\p{L}+")
    private val MEANING_ARTICLE = Regex("^(?:(?:the|an?|to|il|lo|la|i|gli|le|un|uno|una|der|die|das|ein|eine) +|(?:l|un)['’])")

    /** The next words to learn: all that are left when that's 8 or fewer, else 6. */
    fun nextWords(pack: Pack): List<PackWord> {
        val left = pack.words.filter { it.id !in pack.learned }
        return if (left.size <= 8) left else left.take(6)
    }

    /** "m · on", "f · ona", "n · ono": the gender with its pronoun. */
    fun genderTag(g: String?): String? = when (g) {
        "m" -> bi("packs.on")
        "f" -> bi("packs.ona")
        "n" -> bi("packs.ono")
        else -> null
    }

    fun card(packId: String, w: PackWord, known: Boolean = false) = ReviewCard(
        id = itemId(packId, w.id),
        front = w.word,
        back = w.meaning,
        kind = "vocabulary",
        repetitions = if (known) 1 else 0,
        lastQuality = if (known) 4 else 0,
    )

    /**
     * Two rounds over the new words: recognition first (familiarity 0), then recall, as if seen
     * once (familiarity 1: typing, tiles, dictation). Distractors come from the whole pack and [pool].
     */
    fun plan(pack: Pack, words: List<PackWord>, pool: List<ReviewCard>, canSpeak: Boolean, random: Random = Random.Default): List<ReviewPlanner.Task> {
        val distractors = pack.words.map { card(pack.id, it) } + pool
        val first = ReviewPlanner.plan(words.map { card(pack.id, it) }.shuffled(random), distractors, canSpeak, random)
        var second = ReviewPlanner.plan(words.map { card(pack.id, it, known = true) }.shuffled(random), distractors, canSpeak, random)
        // Never the same word twice in a row across the rounds.
        if (second.size > 1 && second.first().card.id == first.lastOrNull()?.card?.id) second = second.drop(1) + second.first()
        return first + second
    }

    /** SM-2 quality for one practice answer on a word just introduced. */
    fun quality(v: Verdict?): Int = when (v) {
        Verdict.CORRECT -> 4
        Verdict.ALMOST -> 3
        Verdict.WRONG -> 2
        null -> 3 // introduced, not practised (the run was stopped early)
    }

    /**
     * One quality per word: its weakest answer, or 3 when it was only introduced. [hints] (per
     * answer, like [verdicts]) lower a hinted answer's quality, see [Hints.penalize].
     */
    fun qualities(
        packId: String, words: List<PackWord>, tasks: List<ReviewPlanner.Task>, verdicts: List<Verdict>,
        hints: List<Int> = emptyList(),
    ): List<Pair<String, Int>> {
        val byCard = tasks.zip(verdicts).withIndex().groupBy(
            { it.value.first.card.id },
            { (i, tv) -> Hints.penalize(quality(tv.second), hints.getOrElse(i) { 0 }) },
        )
        return words.map { w -> w.id to (byCard[itemId(packId, w.id)]?.minOrNull() ?: quality(null)) }
    }

    /**
     * One SM-2 quality per word of a run that records a pack's words as it goes (a festival's: [words] is the word id
     * under each exercise): its weakest answer ([quality]), in the order the words first came. Words not answered (the
     * run was left) aren't recorded.
     */
    fun results(words: List<String>, verdicts: List<Verdict>): List<Pair<String, Int>> =
        verdicts.withIndex().mapNotNull { (i, v) -> words.getOrNull(i)?.let { it to quality(v) } }
            .groupBy({ it.first }, { it.second }).map { (w, q) -> w to q.min() }

    /**
     * What this app does with the packs' levels, as it asks for them (GET /packs?packs=1, [Bridge.PACKS]): 1, it orders
     * them by the learner's level itself ([suggest], [order]) and folds those two levels or more above it ([later]). The
     * bridge gives an app that doesn't say (an older one) only the packs up to a level above its learner's, and every pack
     * the learner has started (bridge/src/packs.ts, `forApp`).
     */
    const val VERSION = 1

    private val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    /** How many levels [pack] (a pack's level) is above [level] (the learner's): 0 at or below it, or either unknown. */
    fun above(pack: String, level: String): Int {
        val p = LEVELS.indexOf(pack)
        val l = LEVELS.indexOf(level)
        return if (p < 0 || l < 0) 0 else (p - l).coerceAtLeast(0)
    }

    /**
     * Whether [p] waits under "Za pozneje · For later" ([later]) for a learner at [level]: a curated pack not started, two
     * levels or more above theirs, not a festival's or a project's (their own sections). A pack the learner started, and
     * every tutor pack, stays where it was.
     */
    fun isLater(p: PackInfo, level: String): Boolean =
        p.source != "tutor" && p.learned == 0 && p.festival == null && p.project == null && above(p.level, level) >= 2

    /**
     * The pack to suggest next: a tutor pack Jan hasn't opened, then the pack of the festival that's on or coming
     * ([festival], a calendar id: learn its words early), then one in progress, then the first untouched one at the
     * learner's level, then the first unfinished one at or below it, then one above, the nearest level first (an A1
     * learner who has every A1 and A2 pack gets a B1 one, never a B2 one first). The other festivals' packs wait on the
     * packs screen.
     */
    fun suggest(packs: List<PackInfo>, level: String, festival: String? = null, isNew: (PackInfo) -> Boolean = { false }): PackInfo? {
        val open = packs.filter { !it.done && it.total > 0 }
        val plain = open.filter { it.festival == null }
        return open.firstOrNull { it.source == "tutor" && isNew(it) }
            ?: festival?.let { f -> open.firstOrNull { it.festival == f } }
            ?: plain.firstOrNull { it.learned > 0 }
            ?: plain.firstOrNull { it.level == level }
            ?: plain.firstOrNull { above(it.level, level) == 0 }
            ?: plain.minByOrNull { above(it.level, level) }
    }

    /**
     * Packs screen order: new tutor packs, in progress, untouched, then finished (the festivals' packs: see [feasts]; the
     * projects': see [projects]). With the learner's [level], the packs that wait for later ([isLater]) are left out:
     * they are [later]'s.
     */
    fun order(packs: List<PackInfo>, level: String? = null, isNew: (PackInfo) -> Boolean = { false }): List<PackInfo> =
        packs.filter { it.festival == null && it.project == null && (level == null || !isLater(it, level)) }.sortedBy { p ->
            when {
                p.done -> 3
                p.source == "tutor" && isNew(p) -> 0
                p.learned > 0 -> 1
                else -> 2
            }
        }

    /**
     * The packs for "Za pozneje · For later", folded at the end of the packs screen: those two levels or more above the
     * learner's [level] not started ([isLater]), the nearest level first. A tap on one still learns it.
     */
    fun later(packs: List<PackInfo>, level: String): List<PackInfo> =
        packs.filter { isLater(it, level) }.sortedBy { above(it.level, level) }

    /** "B1–B2", "B2": the levels of [packs] ([later]'s), lowest to highest. */
    fun levelsOf(packs: List<PackInfo>): String =
        packs.map { it.level }.distinct().sortedBy { LEVELS.indexOf(it) }.let { if (it.size > 1) "${it.first()}–${it.last()}" else it.firstOrNull().orEmpty() }

    /**
     * The festivals' packs for their own section of the packs screen ("🎉 Prazniki · Feasts"), the next festival first
     * ([next]: a festival id → its next day, see game/Calendar), the finished ones last.
     */
    fun feasts(packs: List<PackInfo>, next: (String) -> java.time.LocalDate?): List<PackInfo> =
        packs.filter { it.festival != null }.sortedWith(compareBy<PackInfo> { it.done }.thenBy { next(it.festival!!) ?: java.time.LocalDate.MAX })

    /**
     * The village projects' packs for their own section of the packs screen ("🏗️ Skupni projekti · Village projects"):
     * those of the projects [open] (the ages reached), in progress first, then untouched, the finished ones last.
     */
    fun projects(packs: List<PackInfo>, open: Set<String>): List<PackInfo> =
        packs.filter { it.project != null && it.project in open }.sortedBy { p -> if (p.done) 2 else if (p.learned > 0) 0 else 1 }
}
