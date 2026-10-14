package si.lanisce.lani.app

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.ChallengeOrigin
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.game.Challenge
import si.lanisce.lani.game.ReadFirst

/**
 * An exercise attached to the next chat message, so the tutor knows what "why?" refers to; [key] is where the message's
 * data carries it (`about_exercise`; `about_roleplay` for a role-play whose debrief the chat continues).
 */
data class ChatContext(val label: String, val data: JsonObject, val key: String = "about_exercise")

/**
 * Where an exercise on screen came from, sent along when the learner asks about it (`about_exercise.source`), so the
 * tutor can find the very item and fix it (the lani-fix skill): `kind` says what it is, the other fields its ids.
 * - `review`: a review card (`card_id`, the item's id in spaced-repetition.json), the `variant` it was asked as
 *   ([ReviewPlanner.Variant], lowercase) and the `language` of the deck (absent: the home language);
 * - `module`: a practice module (`module_id`, `module_version`) and the exercise's `exercise_index` in it;
 * - `pack`: a word pack's practice (`pack_id`, `word_id`, `card_id`: the review card the word becomes, `variant`; no
 *   `variant` for the word met before its practice);
 * - `challenge`: a village challenge (`origin`, `title`, `exercise_index`) and what the exercise was made of: a review
 *   card (`card_id`), a module's exercise (`module_id`) or a festival pack's word (`pack_id`, `word_id`); no
 *   `exercise_index` for its text read before the questions;
 * - `visit_request`: a request of a visited town (`town`, `request_id`, `exercise_index`), made of the host's words;
 * - `course`: a sheet of the school's course (companion/COURSE.md): the `language`, the `unit`, the `sheet` (a worksheet's
 *   id, `test`, a homework's id, or `intro` for an intro's question) and the `exercise_index` in it.
 */
object ExerciseSource {
    /** A review card's exercise; [form]: the word's form it asked ("form": the slot, "lemma", "page": its rule). */
    fun review(cardId: String, variant: ReviewPlanner.Variant, language: String? = null, form: si.lanisce.lani.game.FormQuestion? = null): JsonObject = buildJsonObject {
        put("kind", "review")
        put("card_id", cardId)
        put("variant", variant.name.lowercase())
        language?.let { put("language", it) }
        form?.let {
            put("form", it.key)
            put("lemma", it.lemma)
            put("page", it.page)
            it.line?.from?.let { from -> put("line_from", from) }
        }
    }

    /** An exercise of the school's course: unit [unit]'s sheet [sheet] ("test", "intro", a worksheet's or homework's id). */
    fun course(language: String, unit: String, sheet: String, index: Int): JsonObject = buildJsonObject {
        put("kind", "course")
        put("language", language)
        put("unit", unit)
        put("sheet", sheet)
        put("exercise_index", index)
    }

    fun module(id: String, version: Int, index: Int): JsonObject = buildJsonObject {
        put("kind", "module")
        put("module_id", id)
        put("module_version", version)
        put("exercise_index", index)
    }

    /**
     * [cardId] is the pack word's review card, `vocab_<pack>_<word>`: the word id is what follows the pack's prefix.
     * [variant]: how its practice asks it; none for the word met before its practice.
     */
    fun pack(packId: String, cardId: String, variant: ReviewPlanner.Variant? = null): JsonObject = buildJsonObject {
        put("kind", "pack")
        put("pack_id", packId)
        cardId.removePrefix("vocab_${packId}_").takeIf { it != cardId }?.let { put("word_id", it) }
        put("card_id", cardId)
        variant?.let { put("variant", it.name.lowercase()) }
    }

    /**
     * The exercise at [index] of village challenge [c]: its origin, and the card, module or pack word it was made of; the
     * text its questions are about (the day's letter, read first and hidden while they're asked), as `text`. [index]
     * null: the challenge itself, its text read before the questions.
     */
    fun challenge(origin: ChallengeOrigin, c: Challenge, index: Int?): JsonObject = buildJsonObject {
        put("kind", "challenge")
        put("origin", originName(origin))
        put("title", c.title)
        index?.let { put("exercise_index", it) }
        c.text?.let { put("text", it.text) }
        if (index == null) return@buildJsonObject
        c.cardIds.getOrNull(index)?.let { put("card_id", it) }
        c.moduleIds.getOrNull(index)?.let { put("module_id", it) }
        c.packWords.getOrNull(index)?.let { word ->
            c.pack?.let { put("pack_id", it) }
            put("word_id", word)
        }
    }

    /** "event", "gather:food", "quest:<id>", "project:<id>", "festival:<id>", "surprise", "treasure:<station>", "trade:food-wood". */
    fun originName(o: ChallengeOrigin): String = when (o) {
        ChallengeOrigin.Event -> "event"
        is ChallengeOrigin.Gather -> "gather:${o.res.name.lowercase()}"
        is ChallengeOrigin.Quest -> "quest:${o.questId}"
        is ChallengeOrigin.Project -> "project:${o.id}"
        is ChallengeOrigin.Festival -> "festival:${o.id}"
        ChallengeOrigin.Surprise -> "surprise"
        is ChallengeOrigin.Treasure -> "treasure:${o.station}"
        is ChallengeOrigin.Trade -> "trade:${o.give.name.lowercase()}-${o.get.name.lowercase()}"
    }

    fun visitRequest(town: String, requestId: String, index: Int): JsonObject = buildJsonObject {
        put("kind", "visit_request")
        put("town", town)
        put("request_id", requestId)
        put("exercise_index", index)
    }
}

/**
 * What a screen's 💬 attaches when what is on it isn't an exercise (ui/PlayerScreen.kt `chatContext` is), as
 * `about_exercise` with its `type`, so the tutor reads it as it reads an exercise and finds it by its `source`.
 */
object ChatAbout {
    /**
     * A word met before its practice (a pack's, a building's new one: [packId]'s word [w]), type `word`: the word, what
     * it means, its gender, plural, example and note as the card shows them.
     */
    fun word(packId: String, w: PackWord): ChatContext = ChatContext(
        "${w.word} = ${w.meaning}",
        buildJsonObject {
            put("type", "word")
            put("prompt", "${w.word} = ${w.meaning}")
            put("word", w.word)
            put("meaning", w.meaning)
            w.gender?.let { put("gender", it) }
            w.plural?.let { put("plural", it) }
            w.example?.let { put("example", it) }
            w.exampleMeaning?.let { put("example_meaning", it) }
            w.noteText?.let { put("note", it) }
            put("source", ExerciseSource.pack(packId, PackSession.itemId(packId, w.id)))
        },
    )

    /**
     * A text read before its questions ([text]: a challenge's, the day's letter), type `reading`: its title and text,
     * how many [questions] come after it (not asked yet), and where it is from ([source]).
     */
    fun reading(text: ReadFirst, questions: Int, source: JsonObject?): ChatContext = ChatContext(
        text.title,
        buildJsonObject {
            put("type", "reading")
            put("prompt", text.title)
            put("text", text.text)
            put("questions", questions)
            put("answered", false)
            source?.let { put("source", it) }
        },
    )
}
