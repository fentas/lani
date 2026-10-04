package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import si.lanisce.lani.data.Drill
import si.lanisce.lani.data.Drills
import si.lanisce.lani.data.StationResult
import si.lanisce.lani.data.Writes
import si.lanisce.lani.game.Challenge
import si.lanisce.lani.game.Dug
import si.lanisce.lani.game.HuntContent
import si.lanisce.lani.game.HuntStatus
import si.lanisce.lani.game.LevelReadiness
import si.lanisce.lani.game.Readiness
import si.lanisce.lani.game.Station
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.TentMove
import si.lanisce.lani.game.Treasure
import si.lanisce.lani.game.TreasureHunt
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.ReadingFile
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.StoryBooks
import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate

/**
 * "🗺️ Zemljevid zaklada · The treasure map" (companion/GAME.md, "The treasure map"; game/Treasure.kt): whether the learner
 * is ready for the next level ([readiness]), the storyteller's offer once they are ([check]), taking the map, each
 * station's run from what the app has ([challenge]), digging up the treasure ([dig]) and the new level for the node
 * ([report]: `POST /level`, or a `session_end` for an older node's tutor).
 */
class TreasureController(
    private val game: GameController,
    private val content: ContentController,
    private val grammar: GrammarController,
    private val sync: SyncController,
    private val scenes: () -> List<SceneSpec>,
    private val readings: () -> List<ReadingFile>,
    private val read: () -> Set<String>,
    private val cast: () -> List<Villager>,
    private val canSpeak: () -> Boolean,
    private val canListen: () -> Boolean,
) {
    /** The treasure just dug up, for its celebration on the map. */
    var dug by mutableStateOf<Dug?>(null)
        private set

    /** The map was opened from the village (its "‹" leads back there), else from Home. */
    var fromVillage = false

    /** The village's language: the book's, the hunt's. */
    val language: String get() = grammar.language

    /** The learner's level in it now (the treasure's new one while the node doesn't have it yet). */
    val level: String get() = LevelReadiness.norm(content.levelIn(language))

    private var drillsFor: String? = null
    private var drillsCache: List<Drill> = emptyList()

    /** The car's drills of the village's language (the app's own copy): transformations, riddles, sentences built. */
    private fun drills(): List<Drill> {
        if (drillsFor != language) {
            drillsFor = language
            drillsCache = Drills.bundled(language)
        }
        return drillsCache
    }

    /** The exercises that name pages: the modules' (loaded), the tent's questions, the letters' and the pilgrim's. */
    private fun exercises() = content.moduleExercises().map { it.second } + TentMove.exercises(0, canSpeak()) + Surprises.onRules(0)

    /** The storyteller who gives the map: the teller of the evening stories, else the riddles', else the village's host. */
    fun teller(): Villager? {
        val all = cast()
        val id = StoryBooks.stories(scenes()).firstOrNull()?.teller ?: drills().firstNotNullOfOrNull { it.teller }
        return all.firstOrNull { it.id == id }
    }

    /** The storyteller's name, as the chronicle and Home say it ("Stari Janez"); the village's host without one. */
    fun tellerName(): String = teller()?.name ?: Cultures.current.world.host.name

    /** What the evidence says now; null before the node's state is here. */
    fun readiness(today: LocalDate = LocalDate.now()): Readiness? {
        val d = content.dashboard ?: return null
        val lv = level
        val named = LevelReadiness.named(scenes(), exercises(), drills())
        // a level the treasure just brought began the day it was found, whatever the node says yet
        val found = game.state?.treasure?.takeIf { it.found.isNotEmpty() && it.to == lv }?.found?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val since = listOfNotNull(d.levelSince, found).maxOrNull()
        return LevelReadiness.of(lv, grammar.pages, named, grammar::mastery, d.reviewMarks, d.practised, since, today)
    }

    /** The hunt as it stands at the learner's level. */
    fun status(): HuntStatus = Treasure.status(game.state?.treasure, level)

    /** The hunt of the learner's level (offered, on, or just found); null when none. */
    fun hunt(): TreasureHunt? = game.state?.treasure?.takeIf { status() != HuntStatus.NONE }

    /** The stations the hunt needs passed on this phone now. */
    fun required(): List<Station> = Treasure.required(canSpeak(), canListen())

    /**
     * The node's state came (or the village changed): the new level is confirmed once the node has it, and the
     * storyteller offers the map when the learner is ready. Never forced: an offer waits until taken.
     */
    fun check() {
        val d = content.dashboard ?: return
        val node = d.levelIn(language)
        game.apply { Treasure.confirm(it, node) }
        if (status() != HuntStatus.NONE) return
        val r = readiness() ?: return
        if (!r.ready) return
        game.apply { Treasure.offer(it, r, tellerName(), LocalDate.now(), System.currentTimeMillis()) }
    }

    /** The learner takes the map (the storyteller's, or from the grammar book before it was offered): the hunt begins. */
    fun take() {
        game.apply { Treasure.take(it, level, LocalDate.now(), System.currentTimeMillis()) }
    }

    /** What the stations are built from now. */
    private suspend fun huntContent(): HuntContent {
        content.loadModuleExercises()
        return HuntContent(
            language = language,
            pages = grammar.pages,
            scenes = scenes(),
            readings = readings(),
            read = read(),
            exercises = exercises(),
            drills = drills(),
            cards = content.dashboard?.pool.orEmpty(),
            canSpeak = canSpeak(),
            canListen = canListen(),
        )
    }

    /** Station [id]'s run for today; null when it can't be played. */
    suspend fun challenge(id: String): Challenge? {
        val st = Station.of(id) ?: return null
        val s = game.state ?: return null
        return Treasure.challenge(s, st, huntContent(), LocalDate.now())
    }

    /** Every station passed: the treasure is dug up, and the new level goes to the node. */
    fun dig(): Dug? {
        val d = game.dig(required()) ?: return null
        dug = d
        report()
        return d
    }

    /** The celebration was seen. */
    fun seen() {
        dug = null
    }

    /**
     * The new level for the node (the found hunt's): `POST /level` for the bridge to persist; an older node gets it as a
     * `session_end` for the tutor to persist (the outbox's fallback, which says so). Also "📨 Pošlji znova · Send again"
     * when the node still doesn't show it.
     */
    fun report() {
        val h = game.state?.treasure?.takeIf { it.found.isNotEmpty() && !it.confirmed } ?: return
        val stations = Station.entries.mapNotNull { st ->
            val r = h.stations[st.id]?.takeIf { it.passed.isNotEmpty() } ?: return@mapNotNull null
            StationResult(st.id, st.skill, r.right, r.of.coerceAtLeast(1), r.minutes)
        }
        val e = Writes.levelUp(h.from, h.to, stations, NAMES[language] ?: language)
        game.apply { Treasure.reported(it, e.clientId) }
        sync.submitLater(e)
    }

    /** The new level waits for the node: found, not confirmed. */
    val waiting: Boolean get() = game.state?.treasure?.let { it.found.isNotEmpty() && !it.confirmed && it.to == level } == true

    private companion object {
        /** The village languages' English names, for the tutor. */
        val NAMES = mapOf("sl" to "Slovene", "en" to "English", "it" to "Italian", "de" to "German")
    }
}
