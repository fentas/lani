package si.lanisce.lani.game.scene

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import si.lanisce.lani.game.villagers.Label

// The storyteller's evening stories (lani.story/v0; the format is described in companion/SCENES.md, "Stories: the
// evening story"). The bridge serves a scene where someone tells stories (a happening with `stories`) with that teller's
// stories, read in the learner's pair like the scene's dialogs ([inPair]); [Stories] picks tonight's.

/** A legend the teller tells by the fire: in one evening ([levels]) or in [chapters], one an evening. */
@Serializable
data class Story(
    val id: String,
    /** "Zlatorog · Goldhorn"; a file gives it per language (read in the learner's pair, [Label]). */
    @Serializable(with = Label::class) val title: String,
    /** Who tells it: a villager id (the scene person's [ScenePerson.villager]). */
    val teller: String,
    val language: String = "sl",
    val emoji: String = "📖",
    /** Its place in the rotation; the served list is in that order already. */
    val order: Int? = null,
    /** What the teller tells, a whole sentence: "Janez pove zgodbo o Zlatorogu · Janez tells the story of Zlatorog". */
    val teaser: DialogReply? = null,
    /** What the teller remembers afterwards: "zgodbo o Zlatorogu, ki sem ti jo povedal". */
    val memory: DialogReply? = null,
    /** Told in one evening: level ("A1") → the telling. */
    val levels: Map<String, StoryTelling> = emptyMap(),
    /** Told over several evenings, one an evening, in order. */
    val chapters: List<StoryChapter> = emptyList(),
    /** The words it teaches, resolved from their packs. */
    val words: List<StoryWord> = emptyList(),
    /**
     * The story it goes on from (its id): a new chapter or a sequel of one the learner liked ("Martin Krpan se vrne"). It is
     * told after that one ([Stories.tonight]), and its book joins that one's ([StoryBooks]).
     */
    val continues: String? = null,
    /** Its pictures ([StoryPicture]): drawn from the vignette library between the paragraphs, a chapter at least one. */
    val pictures: List<StoryPicture> = emptyList(),
    /**
     * What the learner would have written down of each telling ([StoryNote]): the notebook's text. A story without them (the
     * tutor's, or one an older bridge served) is written down from its telling ([Notebook.prose]).
     */
    val notes: List<StoryNote> = emptyList(),
    /** curated | tutor */
    val source: String = "curated",
) {
    /** Its evenings: the chapters, or its one telling. */
    val parts: List<StoryChapter> get() = chapters.ifEmpty { listOf(StoryChapter(teaser = teaser, levels = levels)) }

    /** The levels it can be told at (every part has them), easiest first. */
    val levelsTold: List<String> get() = Stories.LEVELS.filter { l -> parts.all { l in it.levels } }

    /** The pictures of its evening [chapter] (from 0), in their order. */
    fun picturesOf(chapter: Int): List<StoryPicture> = pictures.filter { it.chapter == chapter + 1 }

    /** The notes of its evening [chapter] (from 0) told at [level], when it has them. */
    fun noteOf(chapter: Int, level: String): StoryNote? = notes.firstOrNull { it.chapter == chapter + 1 && it.level == level && it.text.isNotEmpty() }
}

/**
 * One telling written down (companion/SCENES.md, "The story notebook"): evening [chapter] (from 1) told at [level], as the
 * learner would have written it down listening: the story as prose in the past tense, without the teller's asides and
 * the learner's turns, at the telling's level. [text]: its paragraphs, what is written ([DialogReply.sl], in the story's
 * language) and what it means ([DialogReply.en], in the learner's base). [after]: where the evening's pictures go, the
 * paragraph each follows in their order (0: at the evening's head); without it, a picture follows the paragraph its own
 * `after` names, or the last.
 */
@Serializable
data class StoryNote(
    val chapter: Int = 1,
    val level: String,
    val text: List<DialogReply> = emptyList(),
    val after: List<Int> = emptyList(),
)

/**
 * A picture of a story (companion/SCENES.md, "The story notebook"): drawn from the vignette library ([Vignettes]): a
 * background [bg], [figures] and [props] where they stand, and a [caption]; the notebook shows it as a pencil sketch
 * ([si.lanisce.lani.game.render.book.Sketch]). It stands in evening [chapter] (from 1) after the telling's [after]th
 * paragraph (0: at the chapter's head; a telling with fewer has it at its end). [night]: the sky hatched dark.
 */
@Serializable
data class StoryPicture(
    val chapter: Int = 1,
    val after: Int = 0,
    val bg: String,
    val figures: List<PictureThing> = emptyList(),
    val props: List<PictureThing> = emptyList(),
    val night: Boolean = false,
    val caption: DialogReply? = null,
)

/**
 * A figure or a prop of a [StoryPicture]: vignette [id] at [x] across (0 left … 1 right), [y] up from the background's
 * ground (0 standing in front … 1 at the top), [size] times its own, [flip]ped to face left, [gold]en where it shines
 * (Zlatorog's horns, a crown, a bell).
 */
@Serializable
data class PictureThing(
    val id: String,
    val x: Float = 0.5f,
    val y: Float = 0f,
    val size: Float = 1f,
    val flip: Boolean = false,
    val gold: Boolean = false,
)

/** One evening of a long legend. */
@Serializable
data class StoryChapter(
    @Serializable(with = Label::class) val title: String? = null,
    /** What the teller tells that evening: "Janez pove, kako je Krpan srečal cesarja · …". */
    val teaser: DialogReply? = null,
    val levels: Map<String, StoryTelling> = emptyMap(),
)

/** One telling at one level: a dialog of the teller's lines (no `who`: the teller) and the learner's turns. */
@Serializable
data class StoryTelling(
    val lines: List<DialogLine>,
    @SerialName("sky_stays") val skyStays: Boolean? = null,
    @SerialName("fx_stays") val fxStays: Boolean? = null,
)

/** A word the story teaches, as its pack has it ([sl] the word in the story's language, [en] its meaning). */
@Serializable
data class StoryWord(
    val id: String,
    val pack: String? = null,
    val sl: String = "",
    val en: String = "",
    val emoji: String? = null,
    val gender: String? = null,
)
