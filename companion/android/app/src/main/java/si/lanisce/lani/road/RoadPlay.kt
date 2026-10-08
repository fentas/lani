package si.lanisce.lani.road

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import si.lanisce.lani.data.ClipIndex
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.Voice
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.game.scene.answers
import java.security.MessageDigest

/*
 * "🚗 Za pot · For the road" (companion/README.md, "Im Auto · In the car"): an audio-only session for the car, nothing to
 * read or type. Everything here is pure: what an item sounds like, in which order, with which pauses, and only from
 * clips the voice store has already (never a line the node would have to voice now).
 */

/** One sound of an item. */
@Serializable
sealed interface Sound {
    /** Slovene (the target language) from the voice store: [files] are the clip URLs' file names, played in turn. */
    @Serializable @SerialName("clip")
    data class Clip(val text: String, val files: List<String>) : Sound

    /**
     * The learner's base language (English for Jan), rendered by the phone's own text-to-speech ([RoadPlay.promptFile]).
     * [cue]: a turn's "You say: …", what to say (left out when only listening, [RoadPlay.easy]).
     */
    @Serializable @SerialName("prompt")
    data class Prompt(val text: String, val cue: Boolean = false) : Sound

    /** A moment of silence: [gap] says what for (null: between two sounds). */
    @Serializable @SerialName("pause")
    data class Pause(val ms: Long, val gap: Gap? = null) : Sound

    /**
     * The target language that the voice store didn't have when getting ready (a quiz's wrong option, its fixed phrases):
     * voiced in [voice] while getting ready, by the node if it can within its quota (POST /voice/prepare), else by the
     * phone's own voice in the target language; one file either way ([RoadStore.spoken]). An item whose spoken file
     * couldn't be had isn't played.
     */
    @Serializable @SerialName("spoken")
    data class Spoken(val text: String, val voice: String) : Sound
}

/**
 * What a pause is for: [SAY] the learner says the Slovene before hearing it (next plays the answer at once,
 * [RoadControls.next]), [REPEAT] repeats it after hearing it.
 */
@Serializable
enum class Gap { SAY, REPEAT }

/**
 * What an item is: a review card, a word, a dialog, a story's evening, a phrase to shadow, one of a drill's ([RoadDrills]),
 * a quiz's question ([QUIZ], [RoadQuiz]), or the quiz's fixed phrases ([KIT]: never played as an item).
 */
@Serializable
enum class Kind { CARD, WORD, DIALOG, STORY, PHRASE, TRANSFORM, RAPID, BUILD, RIDDLE, QUIZ, KIT }

/**
 * What "✓ Znal sem · I knew it" or "✗ Nisem · I didn't" records: a review card's answer ([pack] null: POST /reviews), or
 * a pack word learned ([pack]: POST /packs/<pack>/learn), as the app's own review and pack runs do.
 */
@Serializable
data class Rate(val id: String, val pack: String? = null)

/**
 * One thing to hear: a card, a word, a dialog, an evening's story, a phrase to shadow or a drill's item. [id] is unique in
 * the library ("card:<id>", "word:<pack>/<id>", "dialog:<scene>/<id>", "story:<id>/<chapter>", "phrase:<normalized
 * text>", "transform:<set>/<answer>", "rapid:<set>/<key>", "build:<id>", "riddle:<id>"; a word heard again "…#again", a
 * dialog or a story played straight "…#easy"). [title] and [subtitle]
 * are what the car's screen shows (the Slovene and its meaning, a dialog's scene); nothing needs reading.
 */
@Serializable
data class RoadItem(
    val id: String,
    val kind: Kind,
    val title: String,
    val subtitle: String = "",
    val sounds: List<Sound>,
    val rate: Rate? = null,
    /** A review card's due date (ISO), so a library prepared yesterday still knows what's due today. */
    val due: String? = null,
    /** A quiz's question ([Kind.QUIZ]): what it asks; [sounds] are then every sound it may play, for getting ready. */
    val quiz: QuizQuestion? = null,
    /** The quiz's fixed phrases ([Kind.KIT]); [sounds] are then all their alternatives. */
    val kit: QuizKit? = null,
) {
    /** Roughly how long it plays, for the blocks' length and the hours of material. */
    val seconds: Double get() = sounds.sumOf { RoadPlay.seconds(it) }

    /** The file names it plays (clips and prompts), for checking they are on the phone. */
    fun files(promptFile: (String) -> String): List<String> = sounds.flatMap {
        when (it) {
            is Sound.Clip -> it.files
            is Sound.Prompt -> listOf(promptFile(it.text))
            is Sound.Spoken -> listOf(RoadPlay.spokenFile(it.voice, it.text))
            is Sound.Pause -> emptyList()
        }
    }
}

/** What the road has on the phone (road/road.json): every item, prepared at [prepared] (epoch ms) in [base]. */
@Serializable
data class RoadLibrary(
    val prepared: Long = 0,
    /** The base language's code ("en"): the prompts are in it. */
    val base: String = "en",
    val items: List<RoadItem> = emptyList(),
    /** The target language's code ("sl"): the quiz's [Sound.Spoken] are voiced in it. */
    val target: String = "sl",
    /** The cards play counted on the day of getting ready (the village state's then): the quiz counts a card once a day. */
    val played: si.lanisce.lani.game.PlayReviewDay? = null,
    /** The bridge took a dialog's words then: a quiz's slip on a word's meaning may lower its card. */
    val dialogWords: Boolean = false,
) {
    fun of(kind: Kind): List<RoadItem> = items.filter { it.kind == kind }

    /** The quiz's fixed phrases ([Kind.KIT]), when the library has the quiz. */
    val kit: QuizKit? get() = items.firstOrNull { it.kind == Kind.KIT }?.kit

    /** The review cards due on [today] (ISO date). */
    fun due(today: String): List<RoadItem> = of(Kind.CARD).filter { (it.due ?: "") <= today }

    val seconds: Double get() = items.sumOf { it.seconds }
}

/** Finds a text's clips in the voice store's index: the URLs in the first of the voices that has them, or null. */
fun interface ClipLookup {
    fun urls(text: String, voices: List<String>): List<String>?

    companion object {
        /** The phone's copy of the node's index ([Clips.index]); nothing is asked of the node. */
        fun of(index: ClipIndex) = ClipLookup { text, voices -> Clips.resolveFirst(index, text, voices)?.second }
    }
}

/** How items sound: the pauses, and building each kind (Pimsleur-like: say it aloud before you hear it). */
object RoadPlay {
    /** The learner's own lines, and generic texts: the narrators, female first ([Clips.chain]). */
    val NARRATOR: List<String> = Clips.chain()

    /** A pause between two items. */
    const val GAP_MS = 1_200L

    /** Where the learner would repeat the answer, when only listening ([easy]): a breath. */
    const val BREATH_MS = 600L

    /** A dialog or a story played straight ([easy]): its id is the item's with this ("dialog:kuhinja/potica#easy"). */
    const val EASY = "#easy"

    /** How many words [text] has (a dash or an ellipsis is none). */
    fun words(text: String): Int = text.split(Regex("\\s+")).count { w -> w.any { it.isLetterOrDigit() } }

    /** Time to say [answer] aloud before hearing it: 1.5 s a word and 1 s more, at least 2.5 s, at most 8 s. */
    fun thinkMs(answer: String): Long = (1_500L * words(answer) + 1_000L).coerceIn(2_500L, 8_000L)

    /** Time to repeat [answer] after hearing it: a fifth shorter (2 s to 6.4 s). */
    fun repeatMs(answer: String): Long = thinkMs(answer) * 4 / 5

    /** Roughly how long [s] takes: Slovene clips about 12 characters a second, the phone's English about 14. */
    fun seconds(s: Sound): Double = when (s) {
        is Sound.Clip -> 0.5 + s.text.length / 12.0
        is Sound.Spoken -> 0.5 + s.text.length / 12.0
        is Sound.Prompt -> 0.4 + s.text.length / 14.0
        is Sound.Pause -> s.ms / 1000.0
    }

    /**
     * The pieces an item plays one after the other, as [RoadSources] makes its media source (a period of the player's
     * timeline each): for each, the index of its sound in [sounds]. A clip is a piece per file, a prompt and a pause one.
     */
    fun pieces(sounds: List<Sound>): List<Int> =
        sounds.flatMapIndexed { i, s -> List(if (s is Sound.Clip) s.files.size else 1) { i } }

    /** A prompt's rendered file name: the same text in the same language is rendered once. */
    fun promptFile(base: String, text: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest("$base|$text".toByteArray())
        return d.joinToString("") { "%02x".format(it) }.take(20) + ".wav"
    }

    /**
     * A spoken text's file name ([Sound.Spoken]): the same text in the same voice is had once, from the node (an MP3) or
     * the phone's voice (a WAV); the player tells them apart by their content.
     */
    fun spokenFile(voice: String, text: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest("$voice|${text.trim()}".toByteArray())
        return d.joinToString("") { "%02x".format(it) }.take(20) + ".snd"
    }

    /** A clip URL's file name, as the phone's clip cache names it ([Clips.file]). */
    fun fileOf(url: String): String = url.substringAfterLast('/').substringBefore('?')

    private fun clip(text: String, voices: List<String>, clips: ClipLookup): Sound.Clip? =
        clips.urls(text, voices)?.takeIf { it.isNotEmpty() }?.let { Sound.Clip(text, it.map(::fileOf)) }

    /** Meaning, pause to say it, the Slovene, pause to repeat it, the Slovene again. */
    private fun flash(meaning: String, sl: Sound.Clip): List<Sound> = listOf(
        Sound.Prompt(meaning), Sound.Pause(thinkMs(sl.text), Gap.SAY), sl, Sound.Pause(repeatMs(sl.text), Gap.REPEAT), sl, Sound.Pause(GAP_MS),
    )

    /**
     * A due review card as an audio flashcard; null when it isn't a phrase one can say (a rule, a placeholder: the
     * review screen shows those as silent flashcards) or its Slovene has no clip.
     */
    fun card(card: ReviewCard, due: String?, clips: ClipLookup): RoadItem? {
        if (ReviewPlanner.accepted(card.front).isEmpty()) return null
        val sl = clip(card.front, NARRATOR, clips) ?: return null
        val meaning = ReviewPlanner.meaning(card.back)
        if (meaning.isBlank()) return null
        return RoadItem("card:${card.id}", Kind.CARD, card.front, meaning, flash(meaning, sl), Rate(card.id), due)
    }

    /**
     * A pack word as an audio flashcard, then its example when the example has a clip (its meaning, a pause to say it,
     * the Slovene); null when the word has no clip.
     */
    fun word(pack: String, w: PackWord, meaning: String, example: Pair<String, String>?, clips: ClipLookup): RoadItem? {
        val sl = clip(w.word, NARRATOR, clips) ?: return null
        if (meaning.isBlank()) return null
        val ex = example?.let { (s, m) -> clip(s, NARRATOR, clips)?.takeIf { m.isNotBlank() }?.let { it to m } }
        val sounds = flash(meaning, sl) + ex?.let { (c, m) -> listOf(Sound.Prompt(m), Sound.Pause(thinkMs(c.text), Gap.SAY), c, Sound.Pause(GAP_MS)) }.orEmpty()
        return RoadItem("word:$pack/${w.id}", Kind.WORD, w.word, meaning, sounds, Rate(w.id, pack))
    }

    /** A word heard again a few items later: its meaning, a pause to say it, the Slovene once. */
    fun again(item: RoadItem): RoadItem? {
        val sl = item.sounds.firstOrNull { it is Sound.Clip } as? Sound.Clip ?: return null
        val sounds = listOf(Sound.Prompt(item.subtitle), Sound.Pause(thinkMs(sl.text), Gap.SAY), sl, Sound.Pause(GAP_MS))
        return item.copy(id = "${item.id}#again", sounds = sounds)
    }

    /**
     * A line list (a scene dialog, or a story's telling) as a short audio play. The others' lines in their voices
     * ([voicesOf] a line's `who`; null: the teller or narrator); after each paragraph (the lines between two of the
     * learner's turns, as [si.lanisce.lani.game.scene.StoryBooks.paragraphs] counts them) what [after] adds for it (a
     * story's recap); before each of the learner's turns what to say in the base language ([youSay]: "You say: …"), a
     * pause to say it, the right answer, a pause to repeat it, and the reply. A line without a clip is left out; a play
     * missing more than a quarter of its lines, or all its turns, is null.
     */
    fun play(
        lines: List<DialogLine>,
        voicesOf: (String?) -> List<String>,
        youSay: (String) -> String,
        clips: ClipLookup,
        after: (Int) -> List<Sound> = { emptyList() },
    ): List<Sound>? {
        val out = mutableListOf<Sound>()
        var said = 0
        var missed = 0
        var turns = 0
        var lastWho: String? = null
        var paragraph = 0
        var inParagraph = false
        fun endParagraph() {
            if (!inParagraph) return
            out += after(paragraph)
            paragraph++
            inParagraph = false
        }
        for (line in lines) {
            if (line.choices.isNotEmpty()) {
                endParagraph()
                val answer = line.answers.firstOrNull { it.en.isNotBlank() && clip(it.sl, NARRATOR, clips) != null }
                if (answer == null) { missed++; continue }
                said++
                turns++
                out += turn(answer, voicesOf(lastWho), youSay, clips)
                continue
            }
            val sl = line.sl?.takeIf { it.isNotBlank() } ?: continue
            inParagraph = true
            lastWho = line.who ?: lastWho
            val c = clip(sl, voicesOf(line.who), clips)
            if (c == null) { missed++; continue }
            said++
            out += c
            out += Sound.Pause(700)
        }
        endParagraph()
        if (said == 0 || missed * 4 > said + missed) return null
        if (turns == 0 && lines.any { it.choices.isNotEmpty() }) return null
        return out + Sound.Pause(GAP_MS)
    }

    private fun turn(answer: DialogChoice, replyVoices: List<String>, youSay: (String) -> String, clips: ClipLookup): List<Sound> {
        val sl = clip(answer.sl, NARRATOR, clips) ?: return emptyList()
        val reply = answer.reply?.sl?.takeIf { it.isNotBlank() }?.let { clip(it, replyVoices, clips) }
        return listOf(Sound.Prompt(youSay(answer.en), cue = true), Sound.Pause(thinkMs(sl.text), Gap.SAY), sl, Sound.Pause(repeatMs(sl.text), Gap.REPEAT)) +
            listOfNotNull(reply?.let { Sound.Pause(300) }, reply, reply?.let { Sound.Pause(700) })
    }

    /**
     * [item] played straight, for listening only ("🌙 Mirno · Easy listening"): without "You say: …" and the pause to say
     * it, a breath where the learner would repeat it. A dialog is then an audio play (the lines and the right answers in
     * turn); a story keeps its setup and recaps.
     */
    fun easy(item: RoadItem): RoadItem = item.copy(
        id = item.id.substringBefore('#') + EASY,
        sounds = item.sounds.mapNotNull { s ->
            when {
                s is Sound.Prompt && s.cue -> null
                s is Sound.Pause && s.gap == Gap.SAY -> null
                s is Sound.Pause && s.gap == Gap.REPEAT -> Sound.Pause(BREATH_MS)
                else -> s
            }
        },
    )

    /** How long a phrase to shadow is, in words ([words]). */
    val SHADOW_WORDS = 2..8

    /**
     * A dialog's phrases to shadow ("🗣️ Odmev · Shadowing"): the others' lines in their voices ([voicesOf] a line's
     * `who`), each of the learner's turns' right answer and the reply to it, as they are said in the dialog; those of
     * [SHADOW_WORDS] words that have a clip.
     */
    fun phrases(lines: List<DialogLine>, voicesOf: (String?) -> List<String>, clips: ClipLookup): List<Sound.Clip> {
        val out = mutableListOf<Sound.Clip>()
        var lastWho: String? = null
        fun add(text: String?, voices: List<String>) {
            val t = text?.takeIf { words(it) in SHADOW_WORDS } ?: return
            clip(t, voices, clips)?.let(out::add)
        }
        for (line in lines) {
            if (line.choices.isNotEmpty()) {
                val answer = line.answers.firstOrNull { clip(it.sl, NARRATOR, clips) != null } ?: continue
                add(answer.sl, NARRATOR)
                add(answer.reply?.sl, voicesOf(lastWho))
                continue
            }
            lastWho = line.who ?: lastWho
            add(line.sl, voicesOf(line.who))
        }
        return out
    }

    /**
     * A phrase to shadow: the phrase, a pause to say it along in its rhythm (as long as the pause to say an answer,
     * [thinkMs]), the phrase again. No English, nothing graded; [where] its scene.
     */
    fun phrase(c: Sound.Clip, where: String): RoadItem = RoadItem(
        "phrase:${Voice.normalize(c.text)}", Kind.PHRASE, c.text, where,
        listOf(c, Sound.Pause(thinkMs(c.text), Gap.REPEAT), c, Sound.Pause(GAP_MS)),
    )

    /** A scene's dialog as a play: [title] its scene ("Kuhinja · Kitchen"). */
    fun dialog(scene: String, title: String, d: Dialog, voicesOf: (String?) -> List<String>, youSay: (String) -> String, clips: ClipLookup): RoadItem? {
        val sounds = play(d.lines, voicesOf, youSay, clips) ?: return null
        val first = d.lines.firstOrNull { !it.sl.isNullOrBlank() }?.sl.orEmpty()
        return RoadItem("dialog:$scene/${d.id}", Kind.DIALOG, title, first, sounds)
    }

    /**
     * An evening of a story in the teller's voice ([teller]), told at [level] ([RoadStory.evening]): a short setup in the
     * base language (the evening's [teaser], else what its first paragraph tells), each paragraph in Slovene only, and a
     * recap in the base language after every paragraph at A1, every second at A2, at the end from B1. [notes]: the story's
     * written-down paragraphs of this evening at [level], if it has them (the recaps' text); [lang] the story's language,
     * [base] the learner's.
     */
    fun story(
        id: String,
        chapter: Int,
        title: String,
        lines: List<DialogLine>,
        teller: List<String>,
        youSay: (String) -> String,
        clips: ClipLookup,
        level: String = "A1",
        teaser: String? = null,
        notes: List<DialogReply> = emptyList(),
        lang: String = "sl",
        base: String = "en",
    ): RoadItem? {
        val evening = RoadStory.evening(lines, level, teaser, notes, lang, base)
        val recap = { p: Int -> evening.recaps[p]?.let { listOf(Sound.Pause(400), Sound.Prompt(it), Sound.Pause(900)) }.orEmpty() }
        val told = play(lines, { who -> if (who == null) teller else NARRATOR }, youSay, clips, recap) ?: return null
        val setup = evening.setup?.let { listOf(Sound.Prompt(it), Sound.Pause(900)) }.orEmpty()
        return RoadItem("story:$id/$chapter", Kind.STORY, title, "", setup + told)
    }

    val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t"; encodeDefaults = false }
}
