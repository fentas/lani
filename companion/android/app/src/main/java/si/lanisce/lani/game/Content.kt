package si.lanisce.lani.game

import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.l10n.bi
import kotlin.random.Random

/** One exercise of a challenge with the resource it pays and the card behind it, or the module it is from. */
internal data class Item(val exercise: Exercise, val skill: Res, val cardId: String?, val moduleId: String? = null)

/** Picked exercises plus a bilingual note when a fallback stood in for missing content. */
internal data class Picked(val items: List<Item>, val note: String?)

/** Builds exercises for one skill from the content pool, falling back to card-based variants. */
internal object Content {
    private val LISTEN_SL: String get() = bi("common.listenPickMeaning")

    /**
     * [n] exercises that train [res], from the published modules first, then built from the cards ([cards]: a request's or
     * a project's own; their modules only add variety). [timed]: against the clock (an event): a listening run writes by
     * ear, never a whole dictation ([ListeningMix]).
     */
    fun pick(res: Res, n: Int, pool: ContentPool, random: Random, cards: List<ReviewCard>? = null, timed: Boolean = false): Picked {
        // Whole deck in pick order: some builders skip cards they can't use.
        val deck = pickCards(cards ?: pool.cards, (cards ?: pool.cards).size, random)
        val modules = pool.moduleExercises
            .filter { (_, ex) -> ex !is Exercise.Unsupported && GameEngine.resourceOf(ex) == res }
            .filter { (_, ex) -> pool.canSpeak || res != Res.WOOD }
            .shuffled(random)
            .map { (id, ex) -> Item(ex, res, null, moduleId = id) }
        // Quests bring their own cards; modules only add variety there.
        val fromModules = if (cards != null) modules.take(n / 3) else modules.take(n)
        if (res == Res.WOOD && pool.canSpeak) return Picked(listening(n, fromModules, deck, pool.cards, random, timed), null)
        val rest = n - fromModules.size
        val (fromCards, fallback) = when (res) {
            Res.FOOD -> vocabulary(deck.take(n), pool, random) to null
            // (a phone that can speak listens: above)
            Res.WOOD -> vocabulary(deck.take(n), pool, random).map { it.copy(skill = Res.WOOD) } to
                "🔇 ${bi("content.noVoicePhoneSo")}"
            Res.STONE -> sentences(deck, pool.cards, random, Res.STONE) to
                "🧱 ${bi("content.noGrammarDrillsYet")}"
            Res.WISDOM -> deck.asSequence().mapNotNull { translate(it, Res.WISDOM) }.take(n).toList() to
                "✍️ ${bi("content.noConversationDrillsYet")}"
        }
        val filler = fromCards.take(rest)
        // The fallback note says "no drills yet", so only show it when there really are none.
        val note = if (filler.isNotEmpty() && cards == null && (fromModules.isEmpty() || res == Res.WOOD)) fallback else null
        return Picked((fromModules + filler).shuffled(random), note)
    }

    /**
     * Weaker cards first, with some shuffle so the same few don't come up every time. Each card draws its
     * sort key once: a key drawn inside the comparator changes between comparisons, and from 32 cards on
     * the sort then fails with "Comparison method violates its general contract".
     */
    fun pickCards(cards: List<ReviewCard>, n: Int, random: Random): List<ReviewCard> =
        cards.shuffled(random).map { it to ReviewPlanner.familiarity(it) * 2 + random.nextInt(3) }
            .sortedBy { it.second }.map { it.first }.take(n.coerceAtLeast(0))

    private fun vocabulary(deck: List<ReviewCard>, pool: ContentPool, random: Random): List<Item> =
        ReviewPlanner.plan(deck, pool.cards, canSpeak = false, random = random)
            .map { Item(it.exercise, GameEngine.resourceOf(it.exercise), it.card.id) }

    /**
     * A listening run of [n] ([ListeningMix]): mostly heard (a phrase heard, its meaning picked), at most every third
     * written: a short phrase's dictation, or against the clock ([timed]) word chips or a gap by ear. The modules' own
     * come first ([modules]): a long dictation of theirs is left out, and against the clock one is written by ear.
     */
    private fun listening(n: Int, modules: List<Item>, deck: List<ReviewCard>, all: List<ReviewCard>, random: Random, timed: Boolean): List<Item> {
        val slots = ListeningMix.typed(n)
        val written = ArrayList<Item>()
        val heard = ArrayList<Item>()
        for (m in modules) {
            val d = m.exercise as? Exercise.Dictation
            when {
                d == null -> heard += m
                timed -> byEar(d.audio, d.accept, d.explain, all, random, chipsFirst(written))?.let { written += m.copy(exercise = it) }
                ListeningMix.short(d.audio) -> written += m
            }
        }
        for (card in deck) {
            if (written.size >= slots && heard.size >= n - slots || heard.size >= n) break
            // a written one when it's their turn (one in three), else heard
            val turn = written.size < slots && written.size * (ListeningMix.EVERY - 1) <= heard.size
            val w = if (turn) write(card, all, random, timed, chipsFirst(written)) else null
            if (w != null) { written += Item(w, Res.WOOD, card.id); continue }
            val l = listen(card, all, random)
            if (l != null) heard += Item(l, Res.WOOD, card.id)
            else if (written.size < slots) write(card, all, random, timed, chipsFirst(written))?.let { written += Item(it, Res.WOOD, card.id) }
        }
        return ListeningMix.arrange(n, written.shuffled(random), heard.shuffled(random))
    }

    /** [card] written after hearing it: its short phrase's dictation, or against the clock ([timed]) by ear. */
    private fun write(card: ReviewCard, all: List<ReviewCard>, random: Random, timed: Boolean, chipsFirst: Boolean): Exercise? {
        if (card.kind != "vocabulary") return null
        val accept = ReviewPlanner.accepted(card.front)
        val said = accept.firstOrNull() ?: return null
        return if (timed) byEar(said, accept, "= ${card.back}", all, random, chipsFirst)
        else dictation(card)?.takeIf { ListeningMix.short(said) }
    }

    /** Word chips next, when the run's written ones so far aren't more chips than gaps: the two in turn. */
    private fun chipsFirst(written: List<Item>): Boolean =
        written.count { it.exercise is Exercise.Reorder } <= written.count { it.exercise is Exercise.Cloze }

    /**
     * [said] written by ear: word chips ([chipsFirst], a phrase of three words and more), else a gap; the other when the
     * one can't be made; null for a single word.
     */
    private fun byEar(said: String, accept: List<String>, explain: String?, all: List<ReviewCard>, random: Random, chipsFirst: Boolean): Exercise? {
        fun chips(): Exercise? {
            val extra = all.flatMap { ReviewPlanner.accepted(it.front).firstOrNull()?.split(' ').orEmpty() }.shuffled(random).take(4)
            return ListeningMix.chips(said, accept, extra, bi("content.hearOrder"), explain, extras = 1 + random.nextInt(2))
        }
        fun gap(): Exercise? = ListeningMix.gapWords(said).takeIf { it.isNotEmpty() }?.let { ListeningMix.gap(said, it.random(random), bi("content.hearGap"), explain) }
        return if (chipsFirst) chips() ?: gap() else gap() ?: chips()
    }

    private fun sentences(deck: List<ReviewCard>, all: List<ReviewCard>, random: Random, res: Res): List<Item> =
        deck.asSequence().mapNotNull { card ->
            val accept = ReviewPlanner.accepted(card.front)
            val words = accept.firstOrNull()?.split(' ').orEmpty()
            if (card.kind == "vocabulary" && words.size >= 2) {
                val lower = words.map { it.lowercase() }.toSet()
                val extra = all.filter { it.id != card.id }
                    .flatMap { ReviewPlanner.accepted(it.front).firstOrNull()?.split(' ').orEmpty() }
                    .filter { it.lowercase() !in lower }.distinct().shuffled(random).take(3)
                Item(
                    Exercise.Reorder(
                        prompt = ReviewPlanner.meaning(card.back),
                        tokens = words,
                        solutions = accept.map { it.split(' ') }.filter { it.size == words.size },
                        distractors = extra,
                        instruction = bi("content.buildSentence"),
                    ),
                    res, card.id,
                )
            } else translate(card, res)
        }.toList()

    // Prompts and options show a card's meaning without its notes, as reviews do (ReviewPlanner.meaning): a note may name
    // the answer ("yes (da = written, ja = spoken)"). The explanation after the answer keeps the whole back.
    private fun translate(card: ReviewCard, res: Res): Item? {
        val accept = ReviewPlanner.accepted(card.front).ifEmpty { return null }
        return Item(Exercise.Translate(ReviewPlanner.meaning(card.back), accept, instruction = bi("common.writeSlovene")), res, card.id)
    }

    private fun listen(card: ReviewCard, all: List<ReviewCard>, random: Random): Exercise? {
        val right = ReviewPlanner.meaning(card.back)
        val wrong = all.filter { it.id != card.id }.map { ReviewPlanner.meaning(it.back) }.filter { it != right }.distinct()
        if (wrong.isEmpty()) return null
        val opts = (wrong.shuffled(random).take(3) + right).shuffled(random)
        return Exercise.Choice("", opts, opts.indexOf(right), audio = card.front, instruction = LISTEN_SL)
    }

    private fun dictation(card: ReviewCard): Exercise? {
        if (card.kind != "vocabulary") return null
        val said = ReviewPlanner.accepted(card.front).firstOrNull() ?: return null
        return Exercise.Dictation(said, listOf(said), explain = "= ${card.back}", instruction = bi("common.writeWhatHear"))
    }
}
