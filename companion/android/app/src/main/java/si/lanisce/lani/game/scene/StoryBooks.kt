package si.lanisce.lani.game.scene

import kotlin.random.Random

/**
 * What the learner has of the stories heard by the fire, story by story: a "book" a story, its evenings heard and the
 * levels they were heard at, the stories that go on from it ([Story.continues]) joined to it, and the telling's turns as
 * questions. The story notebook ([Notebook], companion/SCENES.md "The story notebook") writes each down; its "🎯 Preveri
 * se · Test yourself" asks the questions. Pure: it follows from the stories and what the learner has heard
 * ([GameState.stories][si.lanisce.lani.game.GameState.stories]).
 */
object StoryBooks {
    /** The stories told in [scenes] (the village's), each once, in the rotation's order. */
    fun stories(scenes: List<SceneSpec>): List<Story> = scenes.flatMap { it.stories }.distinctBy { it.id }

    /**
     * The books of [stories] (in the rotation's order) the learner has heard something of: one per story that goes on from
     * none of them, with the stories that go on from it (a continuation of a continuation too) after it, as far as they
     * were begun. In the rotation's order.
     */
    fun shelf(stories: List<Story>, heard: Map<String, StoryHeard>): List<StoryBook> {
        val ids = stories.map { it.id }.toSet()
        fun root(s: Story): Boolean = s.continues == null || s.continues == s.id || s.continues !in ids
        val children = stories.filterNot(::root).groupBy { it.continues!! }
        return stories.filter(::root).mapNotNull { r ->
            // the story and what goes on from it, depth first, each once
            val chain = ArrayList<Story>()
            fun walk(s: Story) {
                if (chain.any { it.id == s.id }) return
                chain += s
                children[s.id].orEmpty().forEach(::walk)
            }
            walk(r)
            val parts = chain.flatMapIndexed { i, s ->
                val at = Stories.levelsHeard(s, heard[s.id])
                // a continuation not begun yet isn't in the book
                if (i > 0 && at.isEmpty()) emptyList()
                else s.parts.indices.map { c -> BookPart(s, c, at.filterValues { it > c }.keys) }
            }
            StoryBook(r, parts).takeIf { b -> b.parts.any { it.heardAt.isNotEmpty() } }
        }
    }

    /** The level [book] opens at for a learner at [level]: theirs when it was heard at it, else the highest it was heard at. */
    fun openAt(book: StoryBook, level: String): String? = book.levels.let { l -> l.firstOrNull { it == level.uppercase() } ?: l.lastOrNull() }

    /**
     * The teller's lines of [t] in paragraphs: the runs of his lines between two of the learner's turns (the turns
     * become the questions, the replies to them aren't told).
     */
    fun paragraphs(t: StoryTelling): List<List<DialogLine>> {
        val out = ArrayList<List<DialogLine>>()
        var run = ArrayList<DialogLine>()
        for (l in t.lines) {
            if (l.choices.isNotEmpty()) {
                if (run.isNotEmpty()) out += run
                run = ArrayList()
            } else if (!l.sl.isNullOrBlank()) {
                run += l
            }
        }
        if (run.isNotEmpty()) out += run
        return out
    }

    private val sentenceEnd = Regex("(?<=[.!?…])\\s+(?=[\\p{Lu}\\p{N}\"„“»«'‹(])")

    /** [text]'s sentences. */
    fun sentences(text: String): List<String> = text.trim().split(sentenceEnd).map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * The questions of [book] at [level]: every turn of the learner's in the evenings heard at it, asked with the teller's
     * line before it, its choices in an order of their own (the same every time), the right one the answer.
     */
    fun questions(book: StoryBook, level: String): List<BookQuestion> = book.parts.filter { level in it.heardAt }.flatMap { part ->
        val lines = part.part.levels[level]?.lines.orEmpty()
        lines.mapIndexedNotNull { i, l ->
            if (l.choices.isEmpty()) return@mapIndexedNotNull null
            val before = lines.take(i).lastOrNull { it.choices.isEmpty() && !it.sl.isNullOrBlank() }
            val answer = l.choices.firstOrNull { it.ok } ?: return@mapIndexedNotNull null
            val seed = "${part.story.id}/${part.chapter}/$level/$i".hashCode()
            BookQuestion(before?.sl.orEmpty(), before?.en.orEmpty(), l.choices.shuffled(Random(seed)), answer)
        }
    }
}

/** A story heard: [story]'s (and the stories going on from it) evenings, [parts]. */
data class StoryBook(val story: Story, val parts: List<BookPart>) {
    val id: String get() = story.id

    /** The levels it can be read at (some evening heard at each), easiest first. */
    val levels: List<String> get() = Stories.LEVELS.filter { l -> parts.any { l in it.heardAt } }

    /** How many of its evenings were heard at [level]. */
    fun heard(level: String): Int = parts.count { level in it.heardAt }

    /** How many of its evenings were heard at all. */
    val heard: Int get() = parts.count { it.heardAt.isNotEmpty() }

    /** Every evening of it heard. */
    val complete: Boolean get() = parts.all { it.heardAt.isNotEmpty() }

    /** How many pictures it has, in the evenings heard. */
    val pictures: Int get() = parts.filter { it.heardAt.isNotEmpty() }.sumOf { it.story.picturesOf(it.chapter).size }
}

/** One evening of a book: [story]'s part [chapter] (from 0), heard at the levels [heardAt]. */
data class BookPart(val story: Story, val chapter: Int, val heardAt: Set<String>) {
    val part: StoryChapter get() = story.parts[chapter]
}

/** A turn of the telling as a question: the teller's line before it ([ask] · [askBase]), its [options], the [answer]. */
data class BookQuestion(val ask: String, val askBase: String, val options: List<DialogChoice>, val answer: DialogChoice) {
    fun right(choice: DialogChoice): Boolean = choice.ok
}
