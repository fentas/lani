package si.lanisce.lani.app

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.SceneFound
import si.lanisce.lani.data.SceneStore
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.game.AdaptedDialog
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Teaser
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.ActiveKeeper
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogVariants
import si.lanisce.lani.game.scene.Happening
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.Keepers
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Stories
import si.lanisce.lani.game.scene.Story
import si.lanisce.lani.game.scene.StoryTonight
import si.lanisce.lani.game.scene.WordStickers
import si.lanisce.lani.game.scene.parseScenes
import si.lanisce.lani.ui.game.key
import si.lanisce.lani.ui.scene.DialogRun
import si.lanisce.lani.ui.scene.SceneWords
import si.lanisce.lani.ui.villagers.FriendGain
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A dialog with someone in a scene, from the first line to the reward.
 *
 * @param key the happening's key ("scene/happening")
 * @param settled the dialog is over and the village was paid (or not, see [paid])
 * @param paid what the village got; null while the dialog runs, or when the village isn't loaded
 * @param friend how the friendship grew, when the person is a villager (companion/VILLAGERS.md)
 * @param story the storyteller's story told tonight, when the happening tells one ([Stories]); [happening] then carries
 *   its title and its memory
 * @param started when the dialog began, on screen (ScreenClock): a story's time as reading practice
 */
data class SceneTalk(
    val sceneId: String,
    val key: String,
    val person: ScenePerson,
    val happening: Happening,
    val dialog: Dialog,
    val run: DialogRun,
    val settled: Boolean = false,
    val paid: HappeningPaid? = null,
    val friend: FriendGain? = null,
    val story: StoryTonight? = null,
    val started: Long = 0L,
)

/** A talk with someone at a spot of the landscape ([keeper]: the charcoal burner by his kopa), played as a scene's dialog ([talk]). */
data class KeeperTalk(val keeper: ActiveKeeper, val talk: SceneTalk)

/**
 * Close-up scenes of the village (companion/SCENES.md): the list from the node (cached, so scenes work
 * offline), which open where, what's on today; and the scene screen's state: the things found in each
 * scene, which of their words are learned, and the dialog being played.
 */
class SceneController(
    context: Context,
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    /** Credits a finished happening to the village, see [GameController.finishHappening]. */
    private val pay: (key: String, reward: Map<Res, Int>, mistakes: Int, entry: Pair<String, String>) -> HappeningPaid?,
    /** The dialog was done with a villager: the friendship grows, see [VillagerController.sceneDone]. */
    private val befriend: (SceneTalk) -> FriendGain? = { null },
    /**
     * The dialog was done: the sky, the effects and where it left people stay for the day (scene id, the run at its end, the
     * scene), see [GameController.keepScene].
     */
    private val keepScene: (String, DialogRun, SceneSpec?) -> Unit = { _, _, _ -> },
    /** The village as it is now (what the learner has heard of the stories); null when it isn't loaded. */
    private val village: () -> GameState? = { null },
    /** The learner's level in a language ("sl" → "A2"), for the story they are told ([si.lanisce.lani.data.Dashboard.levelIn]). */
    private val level: (String) -> String = { "A1" },
    /** A story was told to its end tonight: the village records it, see [GameController.storyHeard]. */
    private val storyHeard: (StoryTonight) -> Unit = {},
    /**
     * A story was heard to its end (also again): reading practice, with the learner's turns, those missed the first time,
     * and the minutes on screen (see [ReadingsController.storyHeard]).
     */
    private val storyRead: (StoryTonight, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    /**
     * A happening's dialog was heard to its end (its key, the variant's id): the next time another variant plays (see
     * [GameController.dialogHeard], [si.lanisce.lani.game.scene.DialogVariants]).
     */
    private val dialogHeard: (String, String) -> Unit = { _, _ -> },
    /**
     * Someone at a spot told his talk to its end: the next day he is there, the next talk of his story (see
     * [GameController.keeperTold], [si.lanisce.lani.game.scene.Keepers.told]).
     */
    private val keeperTold: (ActiveKeeper) -> Unit = {},
    /**
     * A dialog in a language (the scene's) as this learner meets it: its turns of rules not introduced yet trimmed or
     * echoed, their own traps in it, and how its turns are asked (companion/SCENES.md, "Adaptive turns", "Rules not yet";
     * [GrammarController.adapt]). As it is by default.
     */
    private val adapt: (Dialog, String) -> AdaptedDialog = { d, _ -> AdaptedDialog(d) },
) {
    private val app = context.applicationContext
    private val store = SceneStore(context.filesDir)
    private val io = Dispatchers.IO.limitedParallelism(1) // cache writes stay in order

    /** Every scene the node has, resolved (objects carry their words). */
    var all by mutableStateOf<List<SceneSpec>>(emptyList())
        private set
    /** Scene id → object slots the learner has found (tapped) there. */
    var found by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set
    /** The packs the scenes' words belong to, with the ids the learner has learned. */
    var packs by mutableStateOf<Map<String, Pack>>(emptyMap())
        private set
    /** The dialog being played. */
    var talk by mutableStateOf<SceneTalk?>(null)
        private set
    /** The pack words these scenes draw: their stickers, for the words' cards. */
    val stickers: WordStickers by derivedStateOf { WordStickers.of(all) }

    init {
        scope.launch {
            val (s, p, f) = withContext(io) { Triple(store.readScenes(), store.readPacks(), store.readFound()) }
            if (all.isEmpty()) all = s?.takeIf { it.isNotEmpty() } ?: fixture()
            packs = p + packs
            found = (f.keys + found.keys).associateWith { f[it].orEmpty() + found[it].orEmpty() }
        }
    }

    suspend fun reload(b: Bridge) {
        val list = try {
            b.scenes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // offline, or an older bridge without scenes: keep what we have
        }
        if (list != null) {
            all = list.ifEmpty { fixture() }
            scope.launch(io) { runCatching { store.writeScenes(list) } }
        } else if (all.isEmpty()) {
            all = fixture()
        }
    }

    fun byId(id: String): SceneSpec? = all.firstOrNull { it.id == id }

    /** The scenes that open from [place] ("fire", "forest", "hut" …) in this village now. */
    fun at(place: String, state: GameState): List<SceneSpec> =
        all.filter { place in it.from && Happenings.open(it, state) }

    /** The scenes a tap on the village opens: a building's ([si.lanisce.lani.game.scene.Homes.scenesOf]: a house its own room), else its place's. */
    fun at(tap: si.lanisce.lani.ui.game.PlaceTap, state: GameState): List<SceneSpec> =
        if (tap is si.lanisce.lani.ui.game.PlaceTap.Built) si.lanisce.lani.game.scene.Homes.scenesOf(tap.building, all, state)
        else at(tap.place.key(), state)

    /** What's on in the village now. */
    fun active(state: GameState, now: LocalDateTime = LocalDateTime.now()): List<ActiveHappening> =
        QaHooks.withForced(all, Happenings.active(all, state, now)) // a debug build's QA may count one as on

    // --- words ------------------------------------------------------------------------------

    /** The learner tapped [slot] in [scene]; returns true when it's a new find. */
    fun discover(scene: SceneSpec, slot: String): Boolean {
        val next = SceneFound.discover(found, scene, slot)
        if (next === found) return false
        found = next
        scope.launch(io) { runCatching { store.writeFound(next) } }
        return true
    }

    fun foundIn(scene: SceneSpec): Set<String> = found[scene.id].orEmpty()

    /** Fetches the packs [scene]'s words are in, so the screen knows which are learned. Offline the cached ones stay. */
    suspend fun loadPacks(scene: SceneSpec) = loadPacks(scene.objects.mapNotNull { SceneWords.packOf(scene, it) }.distinct())

    /** Fetches the packs [story]'s words are in, so the story's end knows which are learned. */
    suspend fun loadPacks(story: Story) = loadPacks(story.words.mapNotNull { it.pack }.distinct())

    /** The next "learn the story's words" run, or null when there's nothing (loaded) to learn. */
    fun toLearn(story: Story): Pair<Pack, List<PackWord>>? = SceneWords.toLearn(story, packs)

    private suspend fun loadPacks(ids: List<String>) {
        val b = bridge() ?: return
        var m = packs
        for (id in ids) {
            val p = try {
                b.pack(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue // not on the node (yet), or offline
            }
            // Words are never unlearned, and a save still in the outbox may not be on the node yet.
            m = m + (id to p.copy(learned = (p.learned + m[id]?.learned.orEmpty()).distinct()))
        }
        if (m != packs) save(m)
    }

    /** Words of [packId] were just learned (a pack run finished), before the node has them. */
    fun learned(packId: String, wordIds: List<String>) {
        val p = packs[packId] ?: return
        save(packs + (packId to p.copy(learned = (p.learned + wordIds).distinct())))
    }

    /** The next "learn these words" run of [scene], or null when there's nothing (loaded) to learn. */
    fun toLearn(scene: SceneSpec): Pair<Pack, List<PackWord>>? = SceneWords.toLearn(scene, packs)

    private fun save(m: Map<String, Pack>) {
        packs = m
        scope.launch(io) { runCatching { store.writePacks(m) } }
    }

    // --- dialogs ----------------------------------------------------------------------------

    /**
     * Starts the dialog of happening [a] (the storyteller's: tonight's story, [tonight]); false when it has none (or no
     * one to say it).
     */
    fun startTalk(a: ActiveHappening): Boolean {
        val p = a.person ?: return false
        if (a.happening.stories) {
            val t = tonight(a) ?: return false
            val d = t.dialog()
            // the header and the chronicle name the story; the teller remembers it
            val h = a.happening.copy(title = t.title, memory = t.story.memory ?: a.happening.memory)
            talk = SceneTalk(a.scene.id, a.key, p, h, d, DialogRun.start(d, p.id, seed = kotlin.random.Random.nextLong()), story = t, started = ScreenClock.app.now())
            settle()
            return true
        }
        // today's variant, with the village's number of today in it, as this learner meets it; its tap turns answered in
        // the picture above it
        val (h, file) = DialogVariants.play(a, village() ?: GameState(), LocalDate.now()) ?: return false
        val (d, modes, notYet) = adapt(file, a.scene.language)
        talk = SceneTalk(a.scene.id, a.key, p, h, d, DialogRun.start(d, p.id, seed = kotlin.random.Random.nextLong(), modes = DialogRun.tapModes(d) + modes, notYet = notYet))
        settle()
        return true
    }

    /** The story [a]'s storyteller tells tonight: the next one the learner hasn't heard, at their level in the scene's language. */
    fun tonight(a: ActiveHappening): StoryTonight? =
        Stories.tonight(Stories.of(a.scene, a.person), village()?.stories.orEmpty(), level(a.scene.language))

    /** "Nocoj: …" or "Jutri: …": the storyteller's story, for "Jutri · Tomorrow" ([Stories.teaser]). */
    fun storyTeaser(state: GameState, today: LocalDate = LocalDate.now()): Teaser? = Stories.teaser(all, state, today, level)

    fun choose(k: Int) = step { it.choose(k) }

    fun next() = step { it.next() }

    /** A turn typed into its gap ([DialogRun.type]). */
    fun type(text: String) = step { it.type(text) }

    /** "✋ Let me choose" ([DialogRun.letMeChoose]). */
    fun letMeChoose() = step { it.letMeChoose() }

    /** "📖 Namig · Hint" opened at the turn ([DialogRun.hint]): its right answer counts right, not on the rule's run. */
    fun hint() = step { it.hint() }

    /** A tap turn answered in the picture: [target] tapped ([DialogRun.tap]). */
    fun tap(target: String) = step { it.tap(target) }

    /** A turn said whole ([DialogRun.say]); false when what was heard wasn't clear (the learner says it again). */
    fun say(alternatives: List<String>): Boolean {
        val next = talk?.run?.say(alternatives) ?: return false
        step { next }
        return true
    }

    /** An echo heard and gone on from ("✓ Naprej", [DialogRun.echo]): nothing graded, its rules met. */
    fun echo() = step { it.echo() }

    /** An echo said out loud ([DialogRun.echo]); false when it wasn't close enough (say it again, or tap on). */
    fun echoSaid(alternatives: List<String>): Boolean {
        val next = talk?.run?.echo(alternatives) ?: return false
        step { next }
        return true
    }

    /** Leaves the dialog; before its end nothing is paid, and the weather and effects it brought go (the day's stay). */
    fun closeTalk() {
        talk = null
    }

    private fun step(f: (DialogRun) -> DialogRun) {
        val t = talk ?: return
        talk = t.copy(run = f(t.run))
        settle()
    }

    // --- someone at a spot of the landscape (the charcoal burner by his kopa), played over the village ----------------

    /** The talk with someone at a spot now ([Keepers]: the charcoal burner), over the village (KeeperScene); null when none. */
    var keeperTalk by mutableStateOf<KeeperTalk?>(null)
        private set

    /**
     * Starts [k]'s talk, at the learner's level in the village's language, read in their pair; false when he has none. It
     * pays his reward once a day, like a happening's dialog; he is no villager, so no friendship grows.
     */
    fun startKeeper(k: ActiveKeeper): Boolean {
        val lang = si.lanisce.lani.game.culture.Cultures.current.manifest.language
        val (d, modes, notYet) = adapt(Keepers.talk(k, level(lang), lang) ?: return false, lang)
        val p = Keepers.person(k)
        keeperTalk = KeeperTalk(k, SceneTalk(k.key, k.key, p, Keepers.happening(k), d, DialogRun.start(d, p.id, seed = kotlin.random.Random.nextLong(), modes = modes, notYet = notYet)))
        settleKeeper()
        return true
    }

    fun keeperChoose(k: Int) = stepKeeper { it.choose(k) }

    fun keeperNext() = stepKeeper { it.next() }

    fun keeperType(text: String) = stepKeeper { it.type(text) }

    fun keeperLetMeChoose() = stepKeeper { it.letMeChoose() }

    fun keeperHint() = stepKeeper { it.hint() }

    fun keeperSay(alternatives: List<String>): Boolean {
        val next = keeperTalk?.talk?.run?.say(alternatives) ?: return false
        stepKeeper { next }
        return true
    }

    fun keeperEcho() = stepKeeper { it.echo() }

    fun keeperEchoSaid(alternatives: List<String>): Boolean {
        val next = keeperTalk?.talk?.run?.echo(alternatives) ?: return false
        stepKeeper { next }
        return true
    }

    /** Leaves the talk; before its end nothing is paid. */
    fun closeKeeper() {
        keeperTalk = null
    }

    private fun stepKeeper(f: (DialogRun) -> DialogRun) {
        val t = keeperTalk ?: return
        keeperTalk = t.copy(talk = t.talk.copy(run = f(t.talk.run)))
        settleKeeper()
    }

    /**
     * At the end, pays his reward, once a day (again: nothing, as a happening done today), and the next day he is there he
     * has the next talk of his story.
     */
    private fun settleKeeper() {
        val k = keeperTalk ?: return
        val t = k.talk
        if (t.settled || t.run.step != DialogRun.Step.END) return
        val entry = t.person.emoji to "${t.person.name}: ${t.happening.title}"
        keeperTalk = k.copy(talk = t.copy(settled = true, paid = pay(t.key, SceneWords.reward(t.happening.reward), t.run.mistakes, entry)))
        keeperTold(k.keeper)
    }

    /** The words of pack [id] (a spot's, on its card), so the card knows them and which are learned; offline the cached ones stay. */
    suspend fun loadPack(id: String) = loadPacks(listOf(id))

    /**
     * The packs [ids] on the phone, fetched when they aren't yet: the words a building's upgrade asks for
     * ([si.lanisce.lani.game.BuildingWords.packsOf]); offline, the cached ones stay.
     */
    suspend fun ensurePacks(ids: List<String>) = loadPacks(ids.filter { it !in packs })

    /** At the end, pays the happening's reward, once. */
    private fun settle() {
        val t = talk ?: return
        if (t.settled || t.run.step != DialogRun.Step.END) return
        val entry = t.person.emoji to "${t.person.name}: ${t.happening.title}"
        val paid = t.copy(settled = true, paid = pay(t.key, SceneWords.reward(t.happening.reward), t.run.mistakes, entry))
        talk = paid.copy(friend = befriend(paid))
        keepScene(t.sceneId, t.run, byId(t.sceneId))
        // heard to its end: tomorrow evening the next one (a story heard again the same day changes nothing), and the
        // next time the happening comes, another of its variants
        if (t.story != null && paid.paid?.again != true) storyHeard(t.story)
        if (t.story == null) dialogHeard(t.key, t.dialog.id)
        // and read: every telling heard to its end is reading practice, its turns the questions
        if (t.story != null) storyRead(t.story, t.run.picks.size, t.run.missedTurns, minutesSince(t.started))
    }

    /** Debug builds only: scenes to try the screen with while the node has none (app/src/debug/assets). */
    private suspend fun fixture(): List<SceneSpec> {
        if (!BuildConfig.DEBUG) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { app.assets.open(FIXTURE).bufferedReader().use { parseScenes(si.lanisce.lani.l10n.Learner.current.renderJson(it.readText())) } }.getOrDefault(emptyList())
        }
    }

    private companion object {
        const val FIXTURE = "scenes-fixture.json"
    }
}
