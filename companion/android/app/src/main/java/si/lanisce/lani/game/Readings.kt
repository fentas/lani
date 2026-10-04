package si.lanisce.lani.game

import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.ReadingFile
import si.lanisce.lani.game.villagers.Mentions
import si.lanisce.lani.l10n.Lang

/**
 * "📖 Preberi · Read" (companion/GAME.md, "The chest"): a thing in the chest may hold a reading of the culture pack
 * (readings/<id>.json): Micka's recipes, Janez's books, Prešeren's poems. It's read line by line, each line with its
 * translation, and a question or two close it. Answering them the first time pays 📜 as any answers do
 * ([GameEngine.earn]: the day's first answers in full, later ones half); after that the reading is there to read, and
 * pays nothing. The reading corner ("📖 Branje · Reading", GAME.md "The reading corner") lists every reading of the
 * culture pack and the tutor's by level ([corner]). Pure, like the engine.
 */
object Readings {
    /** What a reading is (bridge/src/readings.ts READING_KINDS says the same). */
    val KINDS = listOf("recipe", "proverbs", "letter", "page", "story", "article")

    /** How hard it is to read (READING_LEVELS). */
    val LEVELS = listOf("A1", "A2", "B1", "B2")

    /** What a closing question asks (QUESTION_TYPES); a question without a type is a plain one. */
    val QUESTION_TYPES = listOf("main_idea", "detail", "word", "true_false", "inference")

    /** At most this many questions close a reading (the first readings have 1 to 3). */
    const val MAX_QUESTIONS = 5

    /** At most this many new words a reading offers (MAX_WORDS). */
    const val MAX_WORDS = 12

    /** A reading in the chest: [reading], held by [item]; [by]: who reads it aloud (a villager id), if anyone. */
    data class Readable(val reading: ReadingFile, val item: ItemName, val by: String?) {
        companion object {
            /** A reading of the corner, held by nothing: its kind's emoji and its title. */
            fun of(r: ReadingFile): Readable = Readable(r, ItemName(emojiOf(r.kind), r.title), r.by)
        }
    }

    /** A kind's emoji: 🥣 a recipe, 📜 proverbs, ✉️ a letter, 📄 a page, 📖 a story, 📰 an article. */
    fun emojiOf(kind: String): String = when (kind) {
        "recipe" -> "🥣"
        "proverbs" -> "📜"
        "letter" -> "✉️"
        "story" -> "📖"
        "article" -> "📰"
        else -> "📄"
    }

    /**
     * What villager [giver]'s tool at [tier] holds to read: the better one's reading first, then the first one's (the
     * better tool took its place in the chest, but what it held stays to read). Its reader is the reading's, else the giver.
     */
    fun ofTool(giver: String, tier: Int): List<Readable> {
        val line = Catalog.tools[giver] ?: return emptyList()
        val c = Cultures.current
        return (if (tier >= 2) listOf(2, 1) else listOf(1)).mapNotNull { t ->
            c.toolReading(giver, t)?.let { Readable(it, line.name(t), it.by ?: giver) }
        }.distinctBy { it.reading.id }
    }

    /** What good [id] holds to read, if anything. */
    fun ofGood(id: String): Readable? {
        val good = Catalog.goods[id] ?: return null
        return Cultures.current.goodReading(id)?.let { Readable(it, good.name, it.by) }
    }

    /** Reading [id]'s key in [GameState.read]: with the culture's id ("primorska/potica"). */
    fun key(id: String): String = "${Cultures.current.id}/$id"

    /** Reading [id]'s questions were answered before (it paid then). */
    fun done(s: GameState, id: String): Boolean = key(id) in s.read

    /**
     * Reading [id]'s questions were answered, [verdicts] one a question: the first time each pays 📜 as an answer does and
     * the reading is marked read; again, nothing. [cost]: the percent the looks back at the text took off the pay
     * ([Peeks.cost]). Returns the village and what it paid.
     */
    fun finish(s: GameState, id: String, verdicts: List<Verdict>, cost: Int = 0): Pair<GameState, Map<Res, Int>> {
        if (done(s, id) || verdicts.isEmpty()) return s to emptyMap()
        val (paid, earned) = GameEngine.earn(s, verdicts.map { Res.WISDOM to it }, cost)
        return paid.copy(read = paid.read + key(id)) to earned
    }

    // --- the reading corner ---------------------------------------------------------------------------------------

    /** The texts of [r] in [lang] as they are read: its intro, a recipe's servings, ingredients and steps, or its lines. */
    fun texts(r: ReadingFile, lang: Lang): List<String> = buildList {
        r.intro?.let { add(it.of(lang)) }
        for (g in r.ingredients) add(listOfNotNull(g.amount, g.unit?.of(lang), g.item.of(lang)).joinToString(" "))
        r.steps.forEach { add(it.of(lang)) }
        r.lines.forEach { add(it.text.of(lang)) }
    }.filter { it.isNotBlank() }

    private val word = Regex("[\\p{L}\\p{N}]")

    /** How many words [r] has to read in [lang] (not its title, nor a proverb's meaning): bridge/src/readings.ts readingLength. */
    fun length(r: ReadingFile, lang: Lang): Int = texts(r, lang).sumOf { t -> t.split(Regex("\\s+")).count { word.containsMatchIn(it) } }

    /** Words a minute a learner at [level] reads a text at that level, about: 60 at A1, 80 at A2, 100 at B1, 120 at B2. */
    fun pace(level: String): Int = 60 + 20 * LEVELS.indexOf(level).coerceAtLeast(0)

    /** About how many minutes [words] take at [level] (at least 1). */
    fun minutes(words: Int, level: String): Int = ((words + pace(level) - 1) / pace(level)).coerceAtLeast(1)

    /** How a reading's level sits with the learner's: at it, the next one up (i+1), easier, or harder still. */
    enum class Fit { AT, NEXT, EASIER, HARDER }

    fun fit(reading: String, learner: String): Fit {
        val r = LEVELS.indexOf(reading).coerceAtLeast(0)
        // a learner above B2 reads the B2 readings as theirs
        val l = LEVELS.indexOf(learner.uppercase()).takeIf { it >= 0 } ?: if (learner.uppercase().startsWith("C")) LEVELS.lastIndex else 0
        return when {
            r == l -> Fit.AT
            r == l + 1 -> Fit.NEXT
            r < l -> Fit.EASIER
            else -> Fit.HARDER
        }
    }

    /**
     * The reading corner's list: [all] for a learner at [level] who has read [read] (reading ids), those at their level
     * first, then the next level up, then the easier ones, then the harder ones; in each, the unread before the read,
     * then the shorter first. A reading that names someone the learner doesn't know yet ([Mentions]: Marko's letter
     * before he lives here and is met; [known], null: anyone), or is read aloud by them, waits.
     */
    fun corner(all: List<ReadingFile>, level: String, read: Set<String>, lang: Lang, known: Set<String>? = Mentions.known): List<ReadingFile> =
        all.distinctBy { it.id }.filter { knows(it, lang, known) }.sortedWith(
            compareBy<ReadingFile>({ fit(it.level, level).ordinal }, { it.id in read }, { length(it, lang) }, { it.id }),
        )

    /** Whether [r] names (or is read by) only people [known] (Mentions): its title and what is read, in [lang]. */
    fun knows(r: ReadingFile, lang: Lang, known: Set<String>?, cast: List<si.lanisce.lani.game.villagers.Villager> = Mentions.cast): Boolean {
        if (known == null || cast.isEmpty()) return true
        val names = Mentions.names(lang.code, cast)
        val named = names.of(r.title.of(lang)) + texts(r, lang).flatMap { names.of(it) } + listOfNotNull(r.by?.takeIf { it in names.names })
        return Mentions.known(named, known)
    }
}
