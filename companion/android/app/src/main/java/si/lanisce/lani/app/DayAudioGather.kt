package si.lanisce.lani.app

import android.content.Context
import kotlinx.coroutines.CancellationException
import si.lanisce.lani.audio.DayAudio
import si.lanisce.lani.audio.DayList
import si.lanisce.lani.audio.DayPlan
import si.lanisce.lani.audio.DayWant
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackInfo
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.data.VoiceProfile
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.DialogVariants
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.ISpy
import si.lanisce.lani.game.scene.ISpyBook
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Stories
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.ui.scene.SceneWords
import si.lanisce.lani.ui.stage.Cast
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.StageCast
import si.lanisce.lani.ui.stage.StageMemory
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What the app knows of the days ahead, for their audio ([DayAudio]): gathered from the controllers when the app is open
 * (the village, the cards, the packs, the grammar book), so the background job ([si.lanisce.lani.audio.Prefetch]) needn't
 * know the game. Each part is a list of lines in the voices the app plays them in ([Speaker.voices]).
 */
class DayAudioGather(
    private val context: Context,
    /** The home deck's cards (the dashboard's pool, with their due dates). */
    private val cards: List<ReviewCard>,
    /** The packs (their progress); the ones under way are asked of the node ([pack]) for their next words. */
    private val packs: List<PackInfo>,
    private val pack: suspend (String) -> Pack?,
    private val state: GameState?,
    private val scenes: List<SceneSpec>,
    private val cast: List<Villager>,
    private val profiles: Map<String, VoiceProfile>,
    /** The learner's level in a language: the storyteller tells at it. */
    private val level: (String) -> String,
    private val grammar: List<GrammarPage>,
    private val mastery: (String) -> Mastery?,
    /** I spy's clues of a scene ([si.lanisce.lani.app.ISpyController.book]). */
    private val ispy: (String) -> ISpyBook?,
    /** "Dobro jutro!", "Dober dan!", "Dober večer!": what someone just there says ([si.lanisce.lani.ui.greeting]). */
    private val greetings: List<String>,
    private val target: String,
) {
    /** Today's list and tomorrow's ([now]: what of today is already past isn't asked for). */
    suspend fun plan(now: LocalDateTime = LocalDateTime.now()): DayPlan {
        val today = now.toLocalDate()
        val days = listOf(today, today.plusDays(1)).map { d -> day(d, if (d == today) now.hour else 0) }
        return DayPlan(System.currentTimeMillis(), target, days)
    }

    private suspend fun day(d: LocalDate, fromHour: Int): DayList {
        val first = d == LocalDate.now()
        val companion = companion(d, first)
        val on = happenings(d, fromHour)
        val parts = listOf(
            DayAudio.cards(cards, d, first, companion),
            packWords(d, first, companion),
            dialogs(d, on),
            villagers(d),
            ispy(d, on.map { it.scene.id }.toSet()),
            grammarExamples(),
        )
        return DayAudio.list(d, parts, companion)
    }

    /** Who reads the day's reviews ([Cast.today]: today's pinned one, tomorrow's by the day's dice), in their voices. */
    private fun companion(d: LocalDate, first: Boolean): List<String> {
        val who = Cast.today(StageCast.of(cast, state, d), state, d, if (first) StageMemory(context).daily(d) else null)
        return Speaker.voices(who.speaker, who.voice, who.id?.let(profiles::get)).all
    }

    /** The voices someone of the cast speaks in (their own voice first when they have one). */
    private fun voicesOf(v: Villager): List<String> = Speaker.voices(v.speakerVoice, v.voice, profiles[v.id]).all

    /**
     * The packs under way (some words learned, not done, not a festival's), at most [PACKS]: today the next words a session
     * teaches, tomorrow the ones after, in the voice of whoever teaches them ([Cast.onStage]: the pack's giver, else the
     * companion), with their examples.
     */
    private suspend fun packWords(d: LocalDate, first: Boolean, companion: List<String>): List<DayWant> {
        val going = packs.filter { it.learned > 0 && !it.done && it.festival == null }.take(PACKS)
        return going.flatMap { info ->
            val p = try {
                pack(info.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: return@flatMap emptyList()
            if (p.language != target) return@flatMap emptyList()
            val next = PackSession.nextWords(p)
            val words = if (first) next else PackSession.nextWords(p.copy(learned = p.learned + next.map { it.id }))
            val who = Cast.onStage(Run.Pack(p.giver?.name, p.giver?.emoji), StageCast.of(cast, state, d), state, d, null)
            val voices = who.id?.let { id -> cast.firstOrNull { it.id == id } }?.let(::voicesOf) ?: companion
            DayAudio.packWords(p.id, words, voices)
        }
    }

    /** The happenings on that day ([Happenings.active] through the day from [fromHour]), each once. */
    private fun happenings(d: LocalDate, fromHour: Int): List<ActiveHappening> {
        val s = state ?: return emptyList()
        val mine = scenes.filter { it.language == target }
        val on = LinkedHashMap<String, ActiveHappening>()
        for (h in HOURS.filter { it >= fromHour }.ifEmpty { listOf(fromHour) }) {
            runCatching { Happenings.active(mine, s, LocalDateTime.of(d, java.time.LocalTime.of(h, 30)), cast = cast) }.getOrDefault(emptyList())
                .forEach { on.putIfAbsent(it.key, it) }
        }
        return on.values.toList()
    }

    /**
     * The dialogs of the day's happenings ([on]): the variant each plays that day, the storyteller's chapter tonight, each
     * line in its speaker's voices.
     */
    private fun dialogs(d: LocalDate, on: List<ActiveHappening>): List<DayWant> {
        val s = state ?: return emptyList()
        return on.flatMap { a ->
            val people = a.scene.people.associateBy { it.id }
            fun voices(who: String?): List<String> {
                val p = who?.let(people::get) ?: return Clips.chain()
                val v = p.villager?.let { id -> cast.firstOrNull { it.id == id } }
                return v?.let(::voicesOf) ?: Clips.chain(SceneWords.voiceOf(p.art))
            }
            val lines = if (a.happening.stories) {
                Stories.tonight(Stories.of(a.scene, a.person), s.stories, level(a.scene.language))?.dialog()?.lines
            } else {
                runCatching { DialogVariants.play(a, s, d)?.second?.lines }.getOrNull()
            }
            DayAudio.dialog(lines.orEmpty(), ::voices, "dialog:${a.key}")
        }
    }

    /** Everyone in the village that day ([Residents.present]): their lines and the day's greetings, in their voices. */
    private fun villagers(d: LocalDate): List<DayWant> {
        val present = state?.let { Residents.present(it, d) }
        return cast.filter { present == null || it.id in present }.flatMap { v -> DayAudio.villager(v, { it.target }, greetings, voicesOf(v)) }
    }

    /**
     * I spy's clues in the scenes the learner will likely open that day: those with a happening on ([about]) or a child of
     * their own in the village, not played out ([ISpy.gamesLeft]); as a child would give them ([ISpy.clues]: of the rules
     * introduced, the kinds a round asks), in the voice of who plays there ([ISpy.host]: the scene's own child, else the one
     * of the village's children the day's dice sends by, the same all day).
     */
    private fun ispy(d: LocalDate, about: Set<String>): List<DayWant> {
        val s = state ?: return emptyList()
        val present = Residents.present(s, d)
        val children = cast.filter { ISpy.isChild(it.art) && (present == null || it.id in present) }.sortedBy { it.id }
        val notYet = { page: String -> mastery(page) == Mastery.NOT_YET }
        fun ownChild(scene: SceneSpec) = scene.people.firstOrNull { p -> ISpy.isChild(p.art) && (p.villager == null || present == null || p.villager in present) }
        val likely = scenes.filter {
            it.language == target && it.objects.isNotEmpty() && (it.id in about || ownChild(it) != null) && Happenings.open(it, s) && ISpy.gamesLeft(s, it.id, d) > 0
        }
        return likely.flatMap { scene ->
            val book = ispy(scene.id) ?: return@flatMap emptyList()
            val own = ownChild(scene)
            val voices = when {
                own != null -> own.villager?.let { id -> cast.firstOrNull { it.id == id } }?.let(::voicesOf) ?: Clips.chain(SceneWords.voiceOf(own.art))
                children.isNotEmpty() -> voicesOf(children[(Happenings.roll(s.seed, d, "ispy/${scene.id}") * children.size).toInt().coerceIn(0, children.lastIndex)])
                else -> return@flatMap emptyList()
            }
            val clues = book.things.values.flatMap { t -> ISpy.clues(t, notYet, { true }).map { it.line.sl } }
            DayAudio.texts(clues, voices, "ispy:${scene.id}")
        }
    }

    /** The grammar book's examples of the pages the learner practises (met, not secure yet). */
    private fun grammarExamples(): List<DayWant> =
        grammar.filter { it.language == target && mastery(it.id).let { m -> m == Mastery.NEW || m == Mastery.LEARNING } }
            .flatMap { p -> DayAudio.texts(p.examples.map { it.target(target) }, Clips.chain(), "grammar:${p.id}") }

    companion object {
        /** At most this many packs under way have their next words got ready. */
        const val PACKS = 3

        /** The hours a day's happenings are looked at (each part of the day, dawn too). */
        val HOURS = listOf(5, 6, 8, 10, 12, 14, 16, 18, 20, 22)
    }
}
