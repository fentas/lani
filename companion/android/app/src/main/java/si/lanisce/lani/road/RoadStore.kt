package si.lanisce.lani.road

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import si.lanisce.lani.data.Outbox
import java.io.File

/**
 * The road's files on the phone (the app's filesDir/road, not the cache: the clip cache's LRU never drops them):
 * road.json (the [RoadLibrary]: what plays), plan.json (what getting ready still has to do, [RoadPlan]), clips/ (the
 * Slovene clips), prompts/ (the base-language prompts), spoken/ (the quiz's Slovene the voice store lacked, voiced while
 * getting ready: [Sound.Spoken]), heard.json (when each item was last heard), ratings.json (ratings not handed to the
 * outbox yet), quiz.json (the quiz's answers not settled yet), quiz-day.json (what the quiz answered today) and
 * quiz-app.json (what the answers leave for the app: [QuizHandOff]).
 */
class RoadStore(val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "road"))

    val clips = File(dir, "clips")
    val prompts = File(dir, "prompts")
    val spoken = File(dir, "spoken")
    private val library = File(dir, "road.json")
    private val plan = File(dir, "plan.json")
    private val heard = File(dir, "heard.json")
    private val ratings = File(dir, "ratings.json")
    private val quiz = File(dir, "quiz.json")
    private val quizDay = File(dir, "quiz-day.json")
    private val quizApp = File(dir, "quiz-app.json")

    fun readLibrary(): RoadLibrary? = read(library)?.let { runCatching { RoadPlay.json.decodeFromString(RoadLibrary.serializer(), it) }.getOrNull() }

    fun writeLibrary(lib: RoadLibrary) = write(library, RoadPlay.json.encodeToString(RoadLibrary.serializer(), lib))

    /** What getting ready still has to do, or null when nothing is under way. */
    fun readPlan(): RoadPlan? = read(plan)?.let { runCatching { RoadPlay.json.decodeFromString(RoadPlan.serializer(), it) }.getOrNull() }

    fun writePlan(p: RoadPlan) = write(plan, RoadPlay.json.encodeToString(RoadPlan.serializer(), p))

    /** Getting ready is done (or given up): nothing to go on with. */
    fun clearPlan() = synchronized(LOCK) { plan.delete() }

    val planned: Boolean get() = plan.isFile

    fun clip(file: String) = File(clips, file)

    fun prompt(base: String, text: String) = File(prompts, RoadPlay.promptFile(base, text))

    fun spoken(voice: String, text: String) = File(spoken, RoadPlay.spokenFile(voice, text))

    /** [s]'s file is on the phone (a pause has none). */
    fun has(s: Sound, base: String): Boolean = when (s) {
        is Sound.Clip -> s.files.all { clip(it).isFile }
        is Sound.Prompt -> prompt(base, s.text).isFile
        is Sound.Spoken -> spoken(s.voice, s.text).isFile
        is Sound.Pause -> true
    }

    /** Every file [item] plays is on the phone. */
    fun playable(item: RoadItem, base: String): Boolean = item.sounds.all { has(it, base) }

    fun readHeard(): Map<String, Long> = read(heard)?.let { runCatching { RoadPlay.json.decodeFromString(HEARD, it) }.getOrNull() }.orEmpty()

    /** [id] (an item's, a word heard again counts as the word) heard now. */
    fun heard(id: String, at: Long = System.currentTimeMillis()) = synchronized(LOCK) {
        write(heard, RoadPlay.json.encodeToString(HEARD, readHeard() + (id.substringBefore('#') to at)))
    }

    fun readRatings(): List<Rating> = read(ratings)?.let { runCatching { RoadPlay.json.decodeFromString(RATINGS, it) }.getOrNull() }.orEmpty()

    fun rate(r: Rating) = synchronized(LOCK) { write(ratings, RoadPlay.json.encodeToString(RATINGS, RoadRatings.add(readRatings(), r))) }

    /**
     * Hands the ratings waiting here to the app's outbox ([outbox]; the app or the service sends it when the node can be
     * reached): one review write for the cards, one learn write per pack. Returns how many writes were queued.
     */
    fun commit(outbox: Outbox): Int = synchronized(LOCK) {
        val all = readRatings()
        if (all.isEmpty()) return 0
        val writes = RoadRatings.writes(all)
        writes.forEach(outbox::enqueue)
        ratings.delete()
        writes.size
    }

    // --- the quiz's answers -----------------------------------------------------------------------------------------

    fun readAnswers(): List<QuizAnswer> = read(quiz)?.let { runCatching { RoadPlay.json.decodeFromString(ANSWERS, it) }.getOrNull() }.orEmpty()

    /** A quiz's answer given now: kept until it is settled ([commitQuiz]). */
    fun answer(a: QuizAnswer) = synchronized(LOCK) { write(quiz, RoadPlay.json.encodeToString(ANSWERS, readAnswers() + a)) }

    fun readQuizDay(): QuizDay = read(quizDay)?.let { runCatching { RoadPlay.json.decodeFromString(QuizDay.serializer(), it) }.getOrNull() } ?: QuizDay()

    fun readHandOffs(): List<QuizHandOff> = read(quizApp)?.let { runCatching { RoadPlay.json.decodeFromString(HAND_OFFS, it) }.getOrNull() }.orEmpty()

    /**
     * Settles the quiz's answers waiting here ([RoadQuizCount.settle], with [lib]'s snapshot of what play counted when
     * getting ready): their reviews go to the app's [outbox] as a dialog's do, what they leave for the app is kept for it
     * ([takeHandOffs]), the day's record is written. Returns how many writes were queued.
     */
    fun commitQuiz(outbox: Outbox, lib: RoadLibrary?, title: String, today: java.time.LocalDate = java.time.LocalDate.now()): Int = synchronized(LOCK) {
        val all = readAnswers()
        if (all.isEmpty()) return 0
        val s = RoadQuizCount.settle(all, today, readQuizDay(), lib?.played, lib?.dialogWords == true, title)
        s.writes.forEach(outbox::enqueue)
        s.handOff?.let { write(quizApp, RoadPlay.json.encodeToString(HAND_OFFS, readHandOffs() + it)) }
        write(quizDay, RoadPlay.json.encodeToString(QuizDay.serializer(), s.day))
        quiz.delete()
        s.writes.size
    }

    /** What the quiz's answers left for the app (it keeps the village state), taken once: the file goes. */
    fun takeHandOffs(): List<QuizHandOff> = synchronized(LOCK) {
        val all = readHandOffs()
        quizApp.delete()
        all
    }

    private fun read(f: File): String? = synchronized(LOCK) { runCatching { f.readText() }.getOrNull() }

    private fun write(f: File, raw: String) = synchronized(LOCK) {
        dir.mkdirs()
        val tmp = File(dir, "${f.name}.tmp")
        tmp.writeText(raw)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    private companion object {
        val LOCK = Any()
        val HEARD = MapSerializer(String.serializer(), Long.serializer())
        val RATINGS = ListSerializer(Rating.serializer())
        val ANSWERS = ListSerializer(QuizAnswer.serializer())
        val HAND_OFFS = ListSerializer(QuizHandOff.serializer())
    }
}
