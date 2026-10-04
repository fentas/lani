package si.lanisce.lani.app

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.ChallengeOrigin
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.game.Challenge

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
 * - `pack`: a word pack's practice (`pack_id`, `word_id`, `card_id`: the review card the word becomes, `variant`);
 * - `challenge`: a village challenge (`origin`, `title`, `exercise_index`) and what the exercise was made of: a review
 *   card (`card_id`), a module's exercise (`module_id`) or a festival pack's word (`pack_id`, `word_id`);
 * - `visit_request`: a request of a visited town (`town`, `request_id`, `exercise_index`), made of the host's words.
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

    fun module(id: String, version: Int, index: Int): JsonObject = buildJsonObject {
        put("kind", "module")
        put("module_id", id)
        put("module_version", version)
        put("exercise_index", index)
    }

    /** [cardId] is the pack word's review card, `vocab_<pack>_<word>`: the word id is what follows the pack's prefix. */
    fun pack(packId: String, cardId: String, variant: ReviewPlanner.Variant): JsonObject = buildJsonObject {
        put("kind", "pack")
        put("pack_id", packId)
        cardId.removePrefix("vocab_${packId}_").takeIf { it != cardId }?.let { put("word_id", it) }
        put("card_id", cardId)
        put("variant", variant.name.lowercase())
    }

    /**
     * The exercise at [index] of village challenge [c]: its origin, and the card, module or pack word it was made of; the
     * text its questions are about (the day's letter, read first and hidden while they're asked), as `text`.
     */
    fun challenge(origin: ChallengeOrigin, c: Challenge, index: Int): JsonObject = buildJsonObject {
        put("kind", "challenge")
        put("origin", originName(origin))
        put("title", c.title)
        put("exercise_index", index)
        c.text?.let { put("text", it.text) }
        c.cardIds.getOrNull(index)?.let { put("card_id", it) }
        c.moduleIds.getOrNull(index)?.let { put("module_id", it) }
        c.packWords.getOrNull(index)?.let { word ->
            c.pack?.let { put("pack_id", it) }
            put("word_id", word)
        }
    }

    /** "event", "gather:food", "quest:<id>", "project:<id>", "festival:<id>", "surprise". */
    fun originName(o: ChallengeOrigin): String = when (o) {
        ChallengeOrigin.Event -> "event"
        is ChallengeOrigin.Gather -> "gather:${o.res.name.lowercase()}"
        is ChallengeOrigin.Quest -> "quest:${o.questId}"
        is ChallengeOrigin.Project -> "project:${o.id}"
        is ChallengeOrigin.Festival -> "festival:${o.id}"
        ChallengeOrigin.Surprise -> "surprise"
        is ChallengeOrigin.Treasure -> "treasure:${o.station}"
    }

    fun visitRequest(town: String, requestId: String, index: Int): JsonObject = buildJsonObject {
        put("kind", "visit_request")
        put("town", town)
        put("request_id", requestId)
        put("exercise_index", index)
    }
}
