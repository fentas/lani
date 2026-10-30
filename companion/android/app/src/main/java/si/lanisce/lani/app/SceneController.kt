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
    /** A village project's step played as its short scene (companion/SCENES.md "Project steps"); null for any other dialog. */
    val step: StepTalk? = null,
    /** The number its dialog counts, as its texts have it ([DialogVariants.counted]): the picture draws that many; null for none. */
    val counted: Int? = null,
)

/**
 * A village project's step played as a short scene that does it (companion/SCENES.md "Project steps"): [project] and its
 * step [index] (0: the first), whom it stages ([cast]: the leader first, each with where they stand in the scene; over the
 * village their spot is empty), whether it is played [over] the village (no scene of its own open today), and once played to
 * its end what came of it ([result]: the step done, its chronicle line; or why it couldn't be done now). Or an age's party
 * ([party]: the age; companion/GAME.md "Ages and their celebrations"), played over the village with all the village on its
 * stage ([cast]: who speaks first, the host first, then everyone else who is here): [result] won the first time it pays.
 */
data class StepTalk(
    val project: String,
    val index: Int,
    val cast: List<ScenePerson>,
    val over: Boolean,
    val result: si.lanisce.lani.game.ChallengeResult? = null,
    val party: si.lanisce.lani.game.Age? = null,
)

/**
 * A talk with someone at a spot of the landscape ([keeper]: the charcoal burner by his kopa), played as a scene's dialog
 * ([talk]), in the village's [language]. The night his pile breaks out its last turn is a [race] against the clock (from
 * [raceFrom], epoch millis, once the turn is up; [seconds] long): [raced] true once the learner chose right in time (saved),
 * false when the time ran out (the pile burnt down), null before.
 */
data class KeeperTalk(
    val keeper: ActiveKeeper,
    val talk: SceneTalk,
    val language: String = "sl",
    val race: si.lanisce.lani.game.scene.Keepers.Race? = null,
    val raceFrom: Long = 0L,
    val seconds: Int = 0,
    val raced: Boolean? = null,
) {
    /** The race is on now: its turn is up, not decided yet. */
    val racing: Boolean get() = race != null && raced == null && raceFrom > 0L && talk.run.step == DialogRun.Step.CHOOSE && talk.run.at == race.turn
}

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
    /**
     * The turns of a dialog (as played) in a language that test the learner's own words, in a scene (its things, for a tap
     * turn) or none ([DialogWordsController.words]; companion/SCENES.md, "Your words in the dialogs"). None by default.
     */
    private val words: (Dialog, String, SceneSpec?) -> Map<Int, si.lanisce.lani.game.TurnWords> = { _, _, _ -> emptyMap() },
    /**
     * A village project's step was played as its scene to its end: the step done (or why not) and how the leader's
     * friendship grew ([GameController.projectStepPlayed]); null when the village isn't loaded.
     */
    private val stepPlayed: (String) -> Pair<si.lanisce.lani.game.ChallengeResult, FriendGain?>? = { null },
    /**
     * An age's party was played to its end: the village keeps it, the first time it pays ([GameController.partyPlayed]):
     * what came of it; null when the village isn't loaded.
     */
    private val partyPlayed: (si.lanisce.lani.game.Age) -> si.lanisce.lani.game.ChallengeResult? = { null },
    /**
     * The night watch at a spot's cycle decided a round ([spot], [round]): the breakout covered in time, or the pile burnt
     * down ([lost]); see [GameController.cycleDecided], [si.lanisce.lani.game.scene.SpotCycles.decide].
     */
    private val cycleDecided: (spot: String, round: String, lost: Boolean) -> Unit = { _, _, _ -> },
    /** The clock (epoch millis): the race against the fire. */
    private val clock: () -> Long = System::currentTimeMillis,
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
            val run = DialogRun.start(d, p.id, seed = kotlin.random.Random.nextLong(), words = words(d, a.scene.language, null))
            talk = SceneTalk(a.scene.id, a.key, p, h, d, run, story = t, started = ScreenClock.app.now())
            settle()
            return true
        }
        // today's variant, with the village's number of today in it, as this learner meets it; its tap turns answered in
        // the picture above it
        val now = village() ?: GameState()
        val today = LocalDate.now()
        // the hour the scene is seen at (QA's "hour:" too), so a count of the hour is the one its clock shows and strikes
        val hour = si.lanisce.lani.app.QaHooks.sceneNow(java.time.LocalDateTime.now()).hour
        val (h, file) = DialogVariants.play(a, now, today, hour) ?: return false
        val (d, modes, notYet) = adapt(file, a.scene.language)
        val run = DialogRun.start(d, p.id, seed = kotlin.random.Random.nextLong(), modes = DialogRun.tapModes(d) + modes, notYet = notYet, words = words(d, a.scene.language, a.scene))
        talk = SceneTalk(a.scene.id, a.key, p, h, d, run, counted = DialogVariants.counted(a, now, today, hour))
        settle()
        return true
    }

    /** The story [a]'s storyteller tells tonight: the next one the learner hasn't heard, at their level in the scene's language. */
    fun tonight(a: ActiveHappening): StoryTonight? =
        Stories.tonight(Stories.of(a.scene, a.person), village()?.stories.orEmpty(), level(a.scene.language))

    /** "Nocoj: …" or "Jutri: …": the storyteller's story, for "Jutri · Tomorrow" ([Stories.teaser]). */
    fun storyTeaser(state: GameState, today: LocalDate = LocalDate.now()): Teaser? = Stories.teaser(all, state, today, level)

    /** Choice [k] of the turn answered, as shown: tapped, or [by] another way (said into the 🎤: not said again). */
    fun choose(k: Int, by: si.lanisce.lani.ui.scene.Said.By = si.lanisce.lani.ui.scene.Said.By.PICK) = step { it.choose(k, by) }

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

    // --- a village project's step as a short scene (companion/SCENES.md "Project steps") -----------------------------------

    /**
     * Starts step [index] of project [spec] as its short scene, at the learner's level in the village's language, read in
     * their pair, its lines said by who is here today ([si.lanisce.lani.game.Projects.cast]: [present], null for everyone;
     * [people], the cast and the village's own): in [scene], its people on the scene's stage and its tap turns answered in the
     * picture, or without one over the village (ProjectStepScene). As this learner meets it: its turns of rules not yet
     * trimmed, their traps and words in it. False when the step has no scene of its own (it is played as a practice).
     */
    fun startStep(spec: si.lanisce.lani.game.ProjectSpec, index: Int, scene: SceneSpec?, present: Set<String>?, people: List<si.lanisce.lani.game.villagers.Villager>): Boolean {
        val lang = si.lanisce.lani.game.culture.Cultures.current.manifest.language
        val file = si.lanisce.lani.game.Projects.dialog(spec, index, level(lang), lang) ?: return false
        val cast = si.lanisce.lani.game.Projects.cast(spec, file, present, people)
        val (d, modes, notYet) = adapt(si.lanisce.lani.game.Projects.recast(file, cast), lang)
        val ids = si.lanisce.lani.game.Projects.speakers(spec, d)
        val stage = scene?.let { si.lanisce.lani.game.Projects.placed(it, ids, people) }
            ?: ids.map { id -> people.firstOrNull { it.id == id }.let { v -> ScenePerson(id, v?.name ?: id, v?.emoji ?: spec.emoji, v?.art ?: "man", "", villager = id) } }
        val leader = stage.first()
        val step = spec.steps[index]
        val h = Happening(id = "${spec.id}-${index + 1}", title = "${spec.short} ${index + 1}/${spec.steps.size}: ${step.task}", who = leader.id, marker = spec.emoji)
        val tap = if (scene != null) DialogRun.tapModes(d) else emptyMap()
        val run = DialogRun.start(
            d, leader.id, seed = kotlin.random.Random.nextLong(), repliers = si.lanisce.lani.game.Projects.repliers(d, leader.id),
            modes = tap + modes, notYet = notYet, words = words(d, lang, scene),
        )
        val key = si.lanisce.lani.game.Projects.dialogId(spec, index)
        talk = SceneTalk(scene?.id ?: key, key, leader, h, d, run, step = StepTalk(spec.id, index, stage, over = scene == null))
        settle()
        return true
    }

    // --- an age's party (companion/GAME.md "Ages and their celebrations") --------------------------------------------------

    /**
     * Starts [c]'s party over the village, at the learner's level in the village's language, read in their pair, its lines
     * said by who is here tonight ([si.lanisce.lani.game.AgeUps.cast]: [present], null for everyone; [people], the cast and
     * the village's own), all the village on its stage ([residents]: who lives here, by id). As this learner meets it: its
     * turns of rules not yet trimmed, their traps and words in it. False when it has no dialog (a gathering).
     */
    fun startParty(c: si.lanisce.lani.game.Celebration, present: Set<String>?, people: List<si.lanisce.lani.game.villagers.Villager>, residents: List<String>): Boolean {
        val lang = si.lanisce.lani.game.culture.Cultures.current.manifest.language
        val file = si.lanisce.lani.game.AgeUps.dialog(c, level(lang), lang) ?: return false
        val cast = si.lanisce.lani.game.AgeUps.cast(c, file, present, people)
        val (d, modes, notYet) = adapt(si.lanisce.lani.game.Projects.recast(file, cast), lang)
        val speakers = si.lanisce.lani.game.AgeUps.speakers(c, d).map { cast[it] ?: it }.distinct()
        val ids = si.lanisce.lani.game.AgeUps.crowd(speakers, present, residents)
        val stage = ids.map { id -> people.firstOrNull { it.id == id }.let { v -> ScenePerson(id, v?.name ?: id, v?.emoji ?: c.emoji, v?.art ?: "man", "", villager = id) } }
        val host = stage.firstOrNull() ?: return false
        val h = Happening(id = "party-${si.lanisce.lani.game.AgeUps.key(c.age)}", title = c.name, who = host.id, marker = c.emoji)
        val run = DialogRun.start(
            d, host.id, seed = kotlin.random.Random.nextLong(), repliers = si.lanisce.lani.game.Projects.repliers(d, host.id),
            modes = modes, notYet = notYet, words = words(d, lang, null),
        )
        val key = si.lanisce.lani.game.AgeUps.dialogId(c.age)
        talk = SceneTalk(key, key, host, h, d, run, step = StepTalk("", 0, stage, over = true, party = c.age))
        settle()
        return true
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
        val lv = level(lang)
        val (d, adapted, notYet) = adapt(Keepers.talk(k, lv, lang) ?: return false, lang)
        // the breakout's race: its last turn, always chosen (no typing against the clock)
        val race = Keepers.race(k, lv)?.let { r -> d.lines.indexOfLast { it.choices.isNotEmpty() }.takeIf { it == d.lines.lastIndex }?.let { r.copy(turn = it) } }
        val modes = race?.let { adapted - it.turn } ?: adapted
        val p = Keepers.person(k)
        val run = DialogRun.start(d, p.id, seed = kotlin.random.Random.nextLong(), modes = modes, notYet = notYet, words = words(d, lang, null))
        keeperTalk = KeeperTalk(k, SceneTalk(k.key, k.key, p, Keepers.happening(k), d, run), lang, race, seconds = race?.seconds ?: 0)
        stepKeeper { it }
        return true
    }

    /**
     * The race's time ran out ([KeeperTalk.racing]): the fire wins, the pile burns down, and the talk goes on with what
     * follows that ([Keepers.Race.lost]).
     */
    fun keeperRaceOut() {
        val t = keeperTalk ?: return
        val race = t.race ?: return
        if (!t.racing) return
        val run = t.talk.run.instead(race.read(race.lost, t.language))
        keeperTalk = t.copy(talk = t.talk.copy(run = run), raced = false)
        cycleDecided(t.keeper.spot, race.round, true)
        settleKeeper()
    }

    /** More time for the race (TalkBack reads the choices aloud): [seconds] in all. */
    fun keeperRaceSeconds(seconds: Int) {
        val t = keeperTalk ?: return
        if (t.race != null && t.raced == null && seconds > t.seconds) keeperTalk = t.copy(seconds = seconds)
    }

    fun keeperChoose(k: Int, by: si.lanisce.lani.ui.scene.Said.By = si.lanisce.lani.ui.scene.Said.By.PICK) = stepKeeper { it.choose(k, by) }

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
        var t = keeperTalk ?: return
        var run = f(t.talk.run)
        val race = t.race
        if (race != null && t.raced == null) {
            val late = t.raceFrom > 0L && clock() > t.raceFrom + t.seconds * 1000L
            when {
                // the race's turn passed, chosen right: in time, the pile is saved; too late, the fire was faster
                run.at > race.turn && !late -> {
                    run = run.extended(race.read(race.saved, t.language))
                    t = t.copy(raced = true)
                    cycleDecided(t.keeper.spot, race.round, false)
                }
                run.at > race.turn || late && run.step == DialogRun.Step.CHOOSE && run.at == race.turn -> {
                    val back = if (run.at > race.turn) t.talk.run else run
                    run = back.instead(race.read(race.lost, t.language))
                    t = t.copy(raced = false)
                    cycleDecided(t.keeper.spot, race.round, true)
                }
                // the turn is up: the clock starts
                run.step == DialogRun.Step.CHOOSE && run.at == race.turn && t.raceFrom == 0L -> t = t.copy(raceFrom = clock())
            }
        }
        keeperTalk = t.copy(talk = t.talk.copy(run = run))
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
        // an age's party played to its end: the village keeps it (the first time it pays), nothing else paid
        t.step?.party?.let { age ->
            talk = t.copy(settled = true, step = t.step.copy(result = partyPlayed(age)))
            return
        }
        // a project's step played to its end: the step is done (its cost paid, its line in the chronicle), nothing else paid
        t.step?.let { st ->
            val (result, friend) = stepPlayed(st.project) ?: (null to null)
            talk = t.copy(settled = true, step = st.copy(result = result), friend = friend)
            if (!st.over) keepScene(t.sceneId, t.run, byId(t.sceneId))
            return
        }
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
        if (!BuildConfig.QA) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { app.assets.open(FIXTURE).bufferedReader().use { parseScenes(si.lanisce.lani.l10n.Learner.current.renderJson(it.readText())) } }.getOrDefault(emptyList())
        }
    }

    private companion object {
        const val FIXTURE = "scenes-fixture.json"
    }
}
