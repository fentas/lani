package si.lanisce.lani.audio

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.ClipIndex
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.Voice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate

/**
 * A line the learner will likely hear on a day: its [text] (in the target language) and the voices [si.lanisce.lani.data.Speaker.say]
 * plays it in, best first (the wanted one, then the ones it falls back to on the phone: [si.lanisce.lani.data.Speaker.voices]);
 * [why] says where it comes from ("card", "pack:v-kuhinji", "dialog:ogenj/juha"), for the status and the tests.
 */
@Serializable
data class DayWant(val text: String, val voices: List<String>, val why: String)

/**
 * What the learner will likely hear on [day] ([DayAudio]): the [wants] in the order they're got ready (what is surest first);
 * [companion] is the voices of the day's companion, who reads the reviews ([si.lanisce.lani.ui.stage.Cast.today]): the prefetch
 * asks the node for the due cards again with it ([cards]), so the tutor's new cards are got ready too.
 */
@Serializable
data class DayList(val day: String, val wants: List<DayWant>, val companion: List<String> = listOf(Clips.FEMALE))

/**
 * The days ahead as the app last saw them (audio/day.json): today's list, then tomorrow's ([days]), in the target
 * [language], made at [made]. The background job ([Prefetch]) gets their clips: the app needn't be open.
 */
@Serializable
data class DayPlan(val made: Long, val language: String, val days: List<DayList>) {
    /** The list for [day] (today's, or yesterday's tomorrow when the app wasn't opened since) and the days after it. */
    fun from(day: LocalDate): List<DayList> = days.filter { it.day >= day.toString() }.sortedBy { it.day }
}

/**
 * What the learner will likely hear on a day, from what the app knows (the village, the cards, the packs), pure: the app
 * gathers the inputs (app/OfflineAudioController), this makes the lines. The day's clips are got ready ahead on Wi-Fi
 * ([Prefetch]), so the day plays the same without the node (companion/README.md, "Voice": "Offline on the phone").
 *
 * In this order, each line once: the due cards (as the reviews ask them, in the companion's voice), the next words of the
 * packs under way and their examples, the dialogs of the day's happenings (the storyteller's chapter tonight among them),
 * the keepers' talks, the villagers' lines (each who is in the village that day), I spy's clues in the scenes a child plays
 * in, the grammar book's examples of the pages practised. At most [MAX_WANTS].
 */
object DayAudio {
    /** At most this many lines a day (a day of Jan's is about 1,000: the cap holds the rest anyway). */
    const val MAX_WANTS = 3_000

    /** The node voices a text of at most this many characters (its /voice/say and /voice/prepare). */
    const val MAX_CHARS = 300

    /** A line worth getting: a text the node can voice (no placeholder left, not too long), in [voices]. */
    fun want(text: String?, voices: List<String>, why: String): DayWant? {
        val t = text?.trim()?.takeIf { it.isNotEmpty() && '{' !in it && it.length <= MAX_CHARS && Voice.normalize(it).isNotEmpty() } ?: return null
        return DayWant(t, voices.ifEmpty { listOf(Clips.FEMALE) }.distinct(), why)
    }

    /**
     * The review cards due on [day] ([first]: today, so the overdue ones too; else only those due that day), as the review
     * says them ([ReviewPlanner]: the front, and each form it accepts: "Kako ste? / Kako si?" both), in [voices] (the day's
     * companion). An error pattern holds the learner's wrong answer and isn't said; nor are rules ("→").
     */
    fun cards(cards: List<ReviewCard>, day: LocalDate, first: Boolean, voices: List<String>): List<DayWant> =
        cards.filter { c -> c.kind != "error_pattern" && c.due?.let { if (first) it <= day else it == day } ?: first }
            .flatMap { c ->
                val forms = ReviewPlanner.accepted(c.front)
                if (forms.isEmpty() && c.kind != "vocabulary") emptyList()
                else (listOf(c.front) + forms).mapNotNull { want(it, voices, "card") }
            }

    /** The words of a pack to learn next ([si.lanisce.lani.data.PackSession.nextWords]) and their examples, in [voices]. */
    fun packWords(pack: String, words: List<PackWord>, voices: List<String>): List<DayWant> =
        words.flatMap { w -> listOfNotNull(want(w.word, voices, "pack:$pack"), want(w.example, listOf(Clips.FEMALE), "pack:$pack")) }

    /**
     * A dialog as it is played ([why]: its happening): each person's lines in their voices ([voicesOf] of their scene id),
     * the learner's right answers in the default voice, and the replies (to the right and the wrong answers) in the voice of
     * whoever spoke last, as the scenes say them.
     */
    fun dialog(lines: List<DialogLine>, voicesOf: (String?) -> List<String>, why: String): List<DayWant> {
        val out = mutableListOf<DayWant>()
        var last: List<String> = Clips.chain()
        for (l in lines) {
            if (l.who != null) {
                last = voicesOf(l.who)
                want(l.sl, last, why)?.let(out::add)
            }
            for (c in l.choices) {
                if (c.ok) want(c.sl, Clips.chain(), why)?.let(out::add)
                want(c.reply?.sl, last, why)?.let(out::add)
            }
        }
        return out
    }

    /**
     * What [v] says in the village besides the dialogs (their lines: meeting the learner, small talk, cheering them on, saying
     * goodbye; not a memory, which is said live), and the greeting of the time of day ([greetings]), in [voices].
     */
    fun villager(v: Villager, target: (si.lanisce.lani.game.villagers.VillagerLine) -> String, greetings: List<String>, voices: List<String>): List<DayWant> {
        val l = v.lines
        val said = (l.greet + l.thanks + l.idle + l.cheer + l.comfort + l.listen + l.bye + l.gift.liked + l.gift.ordinary + l.gift.rare).map(target)
        return (greetings + said).mapNotNull { want(it, voices, "villager:${v.id}") }
    }

    /** Plain texts in [voices] (a grammar page's examples, I spy's clues), for [why]. */
    fun texts(texts: List<String>, voices: List<String>, why: String): List<DayWant> = texts.mapNotNull { want(it, voices, why) }

    /**
     * The day's list from its parts, in their order: each text once in its wanted voice (the first part that has it says
     * why), at most [MAX_WANTS].
     */
    fun list(day: LocalDate, parts: List<List<DayWant>>, companion: List<String> = Clips.chain()): DayList {
        val seen = HashSet<String>()
        val wants = parts.asSequence().flatten().filter { seen.add(key(it)) }.take(MAX_WANTS).toList()
        return DayList(day.toString(), wants, companion)
    }

    /** One line: its text as the voice store keys it, in its wanted voice. */
    fun key(w: DayWant): String = "${Voice.normalize(w.text)}|${w.voices.first()}"

    // --- against the voice store ------------------------------------------------------------------------------------

    /**
     * The voice [w] plays in from the phone and its clip URLs: the first of its voices whose clips the store has for the whole
     * text (one clip, or one a phrase), as [si.lanisce.lani.data.Speaker.say] finds it offline. Null: none yet.
     */
    fun resolve(index: ClipIndex, w: DayWant): Pair<String, List<String>>? = Clips.resolveFirst(index, w.text, w.voices)

    /** [w]'s phrases the store has no clip of in its wanted voice (the node may voice them: POST /voice/prepare). */
    fun missing(index: ClipIndex, w: DayWant): List<String> =
        Clips.parts(w.text).filter { index[Voice.normalize(it)]?.get(w.voices.first()) == null }

    /** How a line stands on the phone: in its wanted voice, in another of its voices, or not at all. */
    enum class Stand { WANTED, OTHER, MISSING }

    /** [w] on the phone: its clips in the store's voice ([resolve]) all there ([has]). */
    fun stand(index: ClipIndex, w: DayWant, has: (String) -> Boolean): Stand {
        val v = w.voices.firstOrNull { voice -> Clips.resolve(index, w.text, voice)?.all { has(fileOf(it)) } == true } ?: return Stand.MISSING
        return if (v == w.voices.first()) Stand.WANTED else Stand.OTHER
    }

    /** A clip URL's file name, as the phone keeps it ("/voice/file/<sha1>.mp3" → "<sha1>.mp3"). */
    fun fileOf(url: String): String = url.substringBefore('?').substringAfterLast('/')
}
