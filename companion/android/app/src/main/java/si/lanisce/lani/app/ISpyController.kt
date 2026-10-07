package si.lanisce.lani.app

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.PlayReviews
import si.lanisce.lani.game.WordBook
import si.lanisce.lani.game.scene.ISpy
import si.lanisce.lani.game.scene.ISpyBook
import si.lanisce.lani.game.scene.ISpyHost
import si.lanisce.lani.game.scene.ISpyLines
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.ui.scene.ISpyRun
import si.lanisce.lani.ui.scene.SceneWords
import si.lanisce.lani.ui.villagers.FriendGain
import java.time.LocalDate

/** «Vidim, vidim» offered in a scene: who plays ([host]), the games left today, and what the child says. */
data class ISpyOffer(val sceneId: String, val host: ISpyHost, val left: Int, val lines: ISpyLines)

/**
 * A game of «Vidim, vidim» in scene [sceneId] with [host]: the [run], when it [started] (on screen), and at its end what it
 * paid ([paid]), the friendship ([friend]) and how many of its finds were a review of a card ([reviewed]).
 */
data class ISpyPlay(
    val sceneId: String,
    val host: ISpyHost,
    val run: ISpyRun,
    val started: Long = 0L,
    val settled: Boolean = false,
    val paid: si.lanisce.lani.app.ISpyPaid? = null,
    val friend: FriendGain? = null,
    val reviewed: Int = 0,
)

/**
 * «Vidim, vidim nekaj, česar ti ne vidiš» in the scenes (companion/SCENES.md, "I spy"; game/scene/ISpy.kt): its content (the
 * scenes' clues and the child's lines, bundled with the app from companion/ispy), the offer in a scene, the game being
 * played, and what it brings: a thing found is found ([found]), a quick find is a review of its word's card once a day
 * ([PlayReviews], sent as a review: [saveReviews]), and at the end the village is paid and the child's friendship grows.
 */
class ISpyController(
    private val scenes: SceneController,
    private val game: GameController,
    /** The learner's words (the home language's dashboard): a thing's card. */
    private val words: () -> WordBook?,
    /** How far the learner has a grammar page ([GrammarController.mastery]); null for a page the book lacks. */
    private val mastery: (String) -> Mastery?,
    /** The grammar book's language: a scene in another (a visit's) gates no clue. */
    private val bookLanguage: () -> String,
    /** Friendship with a villager grows ([VillagerController.befriend]); the gain, for the end card. */
    private val befriend: (String, Int, Memory?) -> FriendGain?,
    /** Reviews of the learner's cards (item id → quality) and the minutes they took ([si.lanisce.lani.AppViewModel.finishReview]). */
    private val saveReviews: (List<Pair<String, Int>>, Int) -> Unit,
) {
    /** The game being played, or null. */
    var play by mutableStateOf<ISpyPlay?>(null)
        private set

    // --- the content: bundled, read in the learner's pair --------------------------------------------------------------

    private var readIn: String? = null
    private val books = HashMap<String, ISpyBook?>()
    private val lines = HashMap<String, ISpyLines?>()

    private fun fresh() {
        val base = L10n.pair.base.code
        if (readIn == base) return
        readIn = base
        books.clear()
        lines.clear()
    }

    /** Scene [id]'s clues; null when it has none (a tutor's scene: the picture-free clues then). */
    fun book(id: String): ISpyBook? {
        fresh()
        return books.getOrPut(id) { resource("scenes/$id.json")?.let { ISpy.parseBook(it, readIn!!) } }
    }

    /** What the child says in [language]; null for a language the game has no lines in. */
    fun lines(language: String): ISpyLines? {
        fresh()
        return lines.getOrPut(language) { resource("$language.json")?.let { ISpy.parseLines(it, readIn!!) } }
    }

    private fun resource(path: String): String? =
        ISpyController::class.java.getResourceAsStream("/ispy/$path")?.use { si.lanisce.lani.l10n.Learner.current.renderJson(it.readBytes().decodeToString()) }

    // --- the offer ------------------------------------------------------------------------------------------------------

    /**
     * «Vidim, vidim» in [scene] now ([today]): a child to play with ([ISpy.host]: one of the scene in the picture, [drawn],
     * else one of [children] who comes by), games left today, lines in the scene's language and something to spy among
     * the things in the picture ([visible]). Null: not now. QA's hook ("ispy:<scene>/<slot>") offers it whatever the day
     * (the scene screen then passes every child of the village, awake or not).
     */
    fun offer(scene: SceneSpec, state: GameState?, drawn: List<PersonInScene>, children: List<Villager>, cast: (String) -> Villager?, visible: Set<String>, today: LocalDate = LocalDate.now()): ISpyOffer? {
        if (state == null || scene.objects.isEmpty()) return null
        val l = lines(scene.language) ?: return null
        val forced = QaHooks.ispy?.takeIf { it.substringBefore('/') == scene.id }
        val left = if (forced != null) ISpy.GAMES_PER_DAY else ISpy.gamesLeft(state, scene.id, today)
        if (left <= 0) return null
        val host = ISpy.host(scene, drawn, children, state.seed, today, cast) ?: return null
        if (scene.objects.none { it.slot in visible }) return null
        return ISpyOffer(scene.id, host, left, l)
    }

    /** Whether a game of [scene] has been played to its end today ([ISpy.gamesLeft] at 0): "again tomorrow". */
    fun doneToday(scene: SceneSpec, state: GameState?, today: LocalDate = LocalDate.now()): Boolean =
        state != null && ISpy.gamesLeft(state, scene.id, today) == 0

    // --- the game ---------------------------------------------------------------------------------------------------------

    /**
     * Starts the game [offer] in [scene], spying among the things in the picture now ([visible]); [found]: the things found
     * there before. False when nothing there can be spied now.
     */
    fun start(scene: SceneSpec, offer: ISpyOffer, state: GameState?, visible: Set<String>, found: Set<String>, today: LocalDate = LocalDate.now()): Boolean {
        val book = book(scene.id)
        val gate = scene.language == bookLanguage()
        val notYet: (String) -> Boolean = { id -> gate && mastery(id) == Mastery.NOT_YET }
        val book0 = words()
        val card: (SceneObject) -> ReviewCard? = { o -> SceneWords.packOf(scene, o)?.let { p -> book0?.card(p, o.word, o.sl.ifBlank { o.word }) } }
        val played = ISpy.today(state, today).scenes[scene.id]?.games ?: 0
        val forced = QaHooks.ispy?.takeIf { it.substringBefore('/') == scene.id }?.substringAfter('/')
        val rounds = ISpy.rounds(
            scene, book, offer.lines, visible, notYet, card, found, ISpy.spied(state, scene.id, today), today,
            ISpy.random(state?.seed ?: 0L, today, scene.id, played), first = forced,
        )
        if (rounds.isEmpty()) return false
        val names = scene.objects.associate { o -> o.slot to ((o.sl.ifBlank { o.word }) to (book?.things?.get(o.slot)?.meaning ?: ISpy.meaningOf(o.en))) }
        play = ISpyPlay(scene.id, offer.host, ISpyRun.start(offer.host.person.id, rounds, offer.lines, names), started = ScreenClock.app.now())
        return true
    }

    /**
     * The thing [slot] tapped in the picture of [scene] ([near]: by the thing spied): the run answers, the thing is found
     * (a tap is a find, as anywhere in a scene); the game ends paid once its last round is over.
     */
    fun tap(scene: SceneSpec, slot: String, near: Boolean) {
        val p = play?.takeIf { it.sceneId == scene.id } ?: return
        if (p.run.step != ISpyRun.Step.FIND) return
        scenes.discover(scene, slot)
        play = p.copy(run = p.run.tap(slot, near))
    }

    /** "💡 Še en namig · Another clue". */
    fun more() = step { it.more() }

    /** "🙈 Pokaži mi · Show me". */
    fun reveal() = step { it.reveal() }

    /** The next round, or the end: then the village is paid ([settle]). */
    fun next(scene: SceneSpec) {
        step { it.next() }
        val p = play ?: return
        if (p.run.step == ISpyRun.Step.END && !p.settled) settle(scene, p)
    }

    private fun step(f: (ISpyRun) -> ISpyRun) {
        val p = play ?: return
        play = p.copy(run = f(p.run))
    }

    /**
     * Leaves the game. Before its end nothing is paid; a round played still spends a game of the day (its things are
     * spied), and its quick finds still count as reviews (the learner did recognise them).
     */
    fun close(scene: SceneSpec?) {
        val p = play ?: return
        play = null
        if (p.settled || p.run.results.isEmpty() || scene == null) return
        reviews(scene, p)
        game.finishISpy(p.sceneId, null, p.run.results.map { it.slot }, emptyList(), befriends = false)
    }

    /** At the end: the reviews, the village's pay, the day's record and the child's friendship. */
    private fun settle(scene: SceneSpec, p: ISpyPlay) {
        val reviewed = reviews(scene, p)
        val child = p.host.person.villager
        val today = LocalDate.now()
        val first = child != null && ISpy.befriends(game.state, child, today)
        val paid = game.finishISpy(p.sceneId, child, p.run.results.map { it.slot }, p.run.results.map { it.verdict }, befriends = first)
        val memory = p.run.lines.memory?.let { Memory(today.toString(), it.sl, it.en, "ispy") }
        val friend = if (first && child != null) befriend(child, FRIENDSHIP, memory) else null
        play = p.copy(settled = true, paid = paid, friend = friend, reviewed = reviewed)
    }

    /** The quick finds' cards that count as reviews today ([PlayReviews]), sent once; how many. */
    private fun reviews(scene: SceneSpec, p: ISpyPlay): Int {
        val today = LocalDate.now()
        val counted = PlayReviews.counted(game.state, today)
        val book = words() ?: return 0
        val due = p.run.results.filter { it.quick }.mapNotNull { r ->
            val o = scene.objects.firstOrNull { it.slot == r.slot } ?: return@mapNotNull null
            SceneWords.packOf(scene, o)?.let { pack -> book.card(pack, o.word, o.sl.ifBlank { o.word }) }
        }.filter { PlayReviews.counts(it, today, counted) }.distinctBy { it.id }
        if (due.isEmpty()) return 0
        saveReviews(PlayReviews.results(due), minutesSince(p.started))
        game.playReviewed(due.map { it.id })
        return due.size
    }

    /**
     * Logs, in a debug build, where the round's things are on the screen ([at]: slot → the centre of its area in window
     * pixels) and the thing spied ([target], its [word]): QA taps them, or its word's chip.
     */
    fun log(round: Int, target: String, word: String, at: Map<String, Pair<Int, Int>>) {
        if (!BuildConfig.DEBUG) return
        Log.d("ISpy", "round $round target $target things " + at.entries.joinToString(" ") { (s, c) -> "$s@${c.first},${c.second}" } + " word $word")
    }

    companion object {
        /** The friendship a game brings the child, once a day: a little less than a dialog ([si.lanisce.lani.game.villagers.Bonds.DIALOG]). */
        const val FRIENDSHIP = 3
    }
}
