package si.lanisce.lani.game.scene

import kotlinx.serialization.Serializable
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Teaser
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

/** What the learner has heard of one story ([GameState.stories], by story id). */
@Serializable
data class StoryHeard(
    /** Times it was told to its end. */
    val told: Int = 0,
    /** Chapters heard of the telling under way (0: none under way). */
    val chapter: Int = 0,
    /** The level of the last part heard. */
    val level: String? = null,
    /** ISO date of the last part heard. */
    val on: String? = null,
    /**
     * How many of its evenings were heard at each level ("A1" → 4, "A2" → 1): what its book holds at that level
     * ([StoryBooks]). A village from before the books has none: [Stories.levelsHeard] reads it from the rest.
     */
    val levels: Map<String, Int> = emptyMap(),
    /**
     * The ISO date each of its evenings (from 1) was first heard: the notebook's "28. septembra, ob ognju"
     * ([Notebook]). A village from before the notebook kept only the last one ([on]).
     */
    val days: Map<Int, String> = emptyMap(),
)

/** Tonight's story: which one, which of its evenings ([chapter], from 0), at which [level], and whether it was told before. */
data class StoryTonight(val story: Story, val chapter: Int, val level: String, val retold: Boolean) {
    val part: StoryChapter get() = story.parts[chapter]
    val telling: StoryTelling get() = part.levels.getValue(level)
    val chapters: Int get() = story.parts.size
    /** The last of its evenings: after it, the story is told to its end. */
    val last: Boolean get() = chapter == chapters - 1

    /** "Zlatorog · Goldhorn"; a chapter's "Martin Krpan (2/4)". */
    val title: String get() = if (chapters > 1) "${story.title} (${chapter + 1}/$chapters)" else story.title

    /** What is told tonight, a sentence in the learner's pair (the chapter's, else the story's). */
    val teaser: DialogReply? get() = part.teaser ?: story.teaser

    /** The evening as a scene dialog, the teller's lines said by the happening's person. */
    fun dialog(): Dialog = Dialog("story:${story.id}:${chapter + 1}:$level", telling.lines, skyStays = telling.skyStays, fxStays = telling.fxStays)
}

/**
 * The storyteller's evening story (companion/SCENES.md, "Stories: the evening story"). Pure: tonight's story follows from
 * the stories told in the scene (the rotation's order), what the learner has heard ([GameState.stories]) and their level
 * in the scene's language, so it stays the same all evening and the next one is known ("Jutri · Tomorrow").
 */
object Stories {
    /** The levels, easiest first. */
    val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    /** Who tells: the person's villager (the stories' teller), else their id. */
    fun tellerOf(person: ScenePerson?): String? = person?.let { it.villager ?: it.id }

    /** The stories [person] tells in [scene], in the rotation's order: those with at least one level for every evening. */
    fun of(scene: SceneSpec, person: ScenePerson?): List<Story> {
        val teller = tellerOf(person) ?: return emptyList()
        return scene.stories.filter { it.teller == teller && it.levelsTold.isNotEmpty() }
    }

    /**
     * The level [story] is told at for a learner at [level], raised by [up] (a retelling goes a level up): the highest
     * it has up to that, else its easiest. A1 → A1, A2 → A2, higher → the highest there is.
     */
    fun levelFor(story: Story, level: String, up: Int = 0): String? {
        val have = story.levelsTold
        if (have.isEmpty()) return null
        val want = (LEVELS.indexOf(level.uppercase()).coerceAtLeast(0) + up).coerceAtMost(LEVELS.lastIndex)
        return have.lastOrNull { LEVELS.indexOf(it) <= want } ?: have.first()
    }

    /**
     * Tonight's story of [stories] (in the rotation's order) for a learner at [level] who has [heard] what they have: the
     * next chapter of one under way; else the first of those told the fewest times (every story once before any is
     * retold: the new ones first, then the continuations, then the retellings), at the learner's level, a level up for
     * each time it was told before. A continuation ([Story.continues]) waits for the story it goes on from: it is told
     * once that one was told once more than it.
     */
    fun tonight(stories: List<Story>, heard: Map<String, StoryHeard>, level: String): StoryTonight? {
        val told = stories.filter { it.levelsTold.isNotEmpty() }
        if (told.isEmpty()) return null
        val going = told.firstOrNull { s -> (heard[s.id]?.chapter ?: 0) in 1 until s.parts.size }
        val story = going ?: next(told, heard)
        val h = heard[story.id] ?: StoryHeard()
        val at = levelFor(story, level, h.told) ?: return null
        return StoryTonight(story, if (going != null) h.chapter else 0, at, retold = h.told > 0)
    }

    /**
     * The story of [told] to begin tonight: of those ready (a continuation once the story it goes on from was told once
     * more than it), the ones told the fewest times; of those the first in the rotation, a continuation after the others.
     */
    private fun next(told: List<Story>, heard: Map<String, StoryHeard>): Story {
        val byId = told.associateBy { it.id }
        fun times(s: Story) = heard[s.id]?.told ?: 0
        fun parent(s: Story) = s.continues?.takeIf { it != s.id }?.let(byId::get)
        val ready = told.filter { s -> parent(s)?.let { times(it) > times(s) } ?: true }.ifEmpty { told }
        val fewest = ready.minOf(::times)
        return ready.filter { times(it) == fewest }.minBy { if (parent(it) != null) 1 else 0 }
    }

    /**
     * How many of [story]'s evenings the learner heard at each level ([StoryHeard.levels]). A village from before the books
     * kept only the last part's level: a story told to its end was heard whole at it; a telling under way, its chapters so
     * far, and whole the level below, where it was told before.
     */
    fun levelsHeard(story: Story, h: StoryHeard?): Map<String, Int> {
        if (h == null) return emptyMap()
        if (h.levels.isNotEmpty()) return h.levels
        val lv = h.level ?: return emptyMap()
        val all = story.parts.size
        return when {
            h.told > 0 && h.chapter > 0 -> {
                val below = story.levelsTold.lastOrNull { LEVELS.indexOf(it) < LEVELS.indexOf(lv) } ?: lv
                if (below == lv) mapOf(lv to all) else mapOf(below to all, lv to h.chapter)
            }
            h.told > 0 -> mapOf(lv to all)
            h.chapter > 0 -> mapOf(lv to h.chapter)
            else -> emptyMap()
        }
    }

    /**
     * [heard] once [t] was heard to its end on [today]: its next chapter, or told once more; at which level; and the day of
     * the evening, the first time it was heard.
     */
    fun heard(heard: Map<String, StoryHeard>, t: StoryTonight, today: LocalDate): Map<String, StoryHeard> {
        val h = heard[t.story.id] ?: StoryHeard()
        val next = if (t.last) h.copy(told = h.told + 1, chapter = 0) else h.copy(chapter = t.chapter + 1)
        val was = levelsHeard(t.story, h)
        val levels = was + (t.level to maxOf(was[t.level] ?: 0, t.chapter + 1))
        // the evenings heard before the days were kept, as far as they can be told, then tonight's if it's new
        val known = daysHeard(t.story, h).mapValues { it.value.toString() }
        val days = if (t.chapter + 1 in known) known else known + (t.chapter + 1 to today.toString())
        return heard + (t.story.id to next.copy(level = t.level, on = today.toString(), levels = levels, days = days))
    }

    /**
     * The day each of [story]'s evenings (from 1) was first heard ([StoryHeard.days]). A village from before the notebook
     * kept only the day of the last evening heard ([StoryHeard.on]): the evenings of that telling before it are counted
     * back an evening each (a legend goes on the next evening), those of an earlier telling aren't known.
     */
    fun daysHeard(story: Story, h: StoryHeard?): Map<Int, LocalDate> {
        if (h == null) return emptyMap()
        val out = HashMap<Int, LocalDate>()
        for ((n, d) in h.days) runCatching { LocalDate.parse(d) }.getOrNull()?.let { out[n] = it }
        val on = h.on?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return out
        val last = when {
            h.chapter > 0 -> h.chapter
            h.told > 0 -> story.parts.size
            else -> return out
        }
        for (n in 1..last) if (n !in out) out[n] = on.minusDays((last - n).toLong())
        return out
    }

    /** [state] once the learner heard [t] on [today]. */
    fun record(state: GameState, t: StoryTonight, today: LocalDate): GameState = state.copy(stories = heard(state.stories, t, today))

    /** Tomorrow's story, once the learner has heard [t] tonight. */
    fun after(stories: List<Story>, heard: Map<String, StoryHeard>, level: String, t: StoryTonight, today: LocalDate): StoryTonight? =
        tonight(stories, heard(heard, t, today), level)

    /**
     * "Jutri · Tomorrow" for the stories: while tonight's is still to come, "Nocoj: Janez pove zgodbo o Zlatorogu ·
     * Tonight: …" (when the teller is on this evening); once it is heard, tomorrow's, "Jutri: Janez pove, kako je Krpan
     * srečal cesarja · Tomorrow: …". A story told before is told "once more". Null when nobody in the village's open
     * scenes tells stories. [level]: the learner's level in a language ([si.lanisce.lani.data.Dashboard.levelIn]).
     */
    fun teaser(scenes: List<SceneSpec>, state: GameState, today: LocalDate, level: (String) -> String): Teaser? {
        for (scene in scenes) {
            if (!Happenings.open(scene, state)) continue
            for (h in scene.happenings) {
                if (!h.stories) continue
                val person = scene.people.firstOrNull { it.id == h.who } ?: continue
                val stories = of(scene, person)
                val a = ActiveHappening(scene, h, person)
                val lv = level(scene.language)
                val now = tonight(stories, state.stories, lv) ?: continue
                if (Happenings.done(state, a.key, today)) {
                    return Teaser(Teaser.Kind.STORY, now.story.emoji, bi(if (now.retold) "tomorrow.storyAgain" else "tomorrow.story", "what" to what(now)))
                }
                // tonight's, when the teller is on this evening (here, on this weekday, by the day's dice)
                if (!Happenings.on(a, state, today, h.`when`.firstOrNull() ?: TimeOfDay.EVENING)) continue
                return Teaser(Teaser.Kind.STORY, now.story.emoji, bi(if (now.retold) "tomorrow.storyTonightAgain" else "tomorrow.storyTonight", "what" to what(now)))
            }
        }
        return null
    }

    /** What [t] tells, in each language of the learner's pair: its teaser (the target's, what it means), else its title. */
    fun what(t: StoryTonight): Localized {
        val said = t.teaser?.sl?.takeIf { it.isNotBlank() } ?: t.title
        val meant = t.teaser?.en?.takeIf { it.isNotBlank() } ?: said
        return Localized { lang -> if (lang == L10n.pair.target) said else meant }
    }
}
