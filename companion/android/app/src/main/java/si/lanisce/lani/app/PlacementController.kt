package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Outbox
import si.lanisce.lani.data.PlacementReport
import si.lanisce.lani.data.Writes
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.LevelPlacement
import si.lanisce.lani.game.LevelReadiness
import si.lanisce.lani.game.Res
import si.lanisce.lani.ui.game.Reward
import java.time.LocalDate
import kotlin.random.Random

/**
 * The placement test ("🧭 Uvrstitveni test · Placement test"; game/LevelPlacement, companion/COURSE.md "The placement
 * test"): offered at setup and in the school, never run on its own. A test begun ([begin]) asks one probe after another
 * ([probe], [answered]); its answers pay as a test's ([finish]); its [result] changes nothing until the learner takes it
 * ([take]: the units marked done, the levels opened, the level raised on the node with `POST /placement`) or keeps their
 * place ([keep]: the tutor hears it all the same). [check]: the node has the level it raised.
 */
class PlacementController(
    private val game: GameController,
    private val school: SchoolController,
    private val sync: SyncController,
    /** The learner's level in a language, as the app goes by it. */
    private val level: (String) -> String,
    /** A child learns here: the child's version. */
    private val child: () -> Boolean,
    /** The learner's level in a language as the node has it (its dashboard's); null before it is read. */
    private val node: (String) -> String? = { null },
) {
    /** The test under way (or just ended, its [result] shown); null: none. */
    var test by mutableStateOf<LevelPlacement.Test?>(null)
        private set

    /** The probe on screen; null between probes and once the test is done. */
    var probe by mutableStateOf<LevelPlacement.Probe?>(null)
        private set

    /** The probes asked so far (each probe's run is a screen of its own: its key). */
    var asked by mutableStateOf(0)
        private set

    /** What the test found, once it is done; null before. */
    var result by mutableStateOf<LevelPlacement.Result?>(null)
        private set

    /** What its answers paid (once, at its end). */
    var reward by mutableStateOf<Reward?>(null)
        private set

    /** The learner's level as the test began. */
    var from: String = "A1"
        private set

    /** The result was taken or kept: said to the node. */
    var decided by mutableStateOf(false)
        private set

    private val pairs = mutableListOf<Pair<Res, Verdict>>()
    private var random = Random(0)

    /** The course's language: the village's. */
    val language: String get() = school.language

    /** Which version a test would be now: a child's, or a grown-up's. */
    val version: LevelPlacement.Version get() = if (child()) LevelPlacement.Version.CHILD else LevelPlacement.Version.ADULT

    /** How the school offers it now ([LevelPlacement.offer]). */
    fun offer(): LevelPlacement.Offer = LevelPlacement.offer(game.state, school.course)

    /** The last result the learner took in the village's language; null: none. */
    fun last(): si.lanisce.lani.game.Placed? = game.state?.course?.placements?.get(language)

    /** A test begins (the learner's "▶️ Začni · Start"); false without a course. */
    fun begin(seed: Long = System.currentTimeMillis()): Boolean {
        // the village's own course (the bundled one is read again as the village's language comes)
        val c = school.course?.takeIf { it.language == school.language } ?: return false
        from = LevelReadiness.norm(level(c.language))
        random = Random(seed)
        pairs.clear()
        reward = null
        result = null
        decided = false
        asked = 0
        val t = LevelPlacement.start(c, game.state, from, version)
        test = t
        advance(t)
        return true
    }

    /** The next probe, or the result once the test is done. */
    private fun advance(t: LevelPlacement.Test) {
        val c = school.course
        val part = if (c == null) null else LevelPlacement.next(t)
        if (c == null || part == null) {
            test = t
            probe = null
            finish()
            return
        }
        val p = LevelPlacement.probe(c, t, part, random)
        test = t
        probe = p
        asked++
    }

    /**
     * The probe on screen answered: [items] each exercise's items' verdicts (as paid), in [seconds]; [complete]: to its
     * end, else the learner stopped the test there. Its answers count for the pay at the end.
     */
    fun answered(items: List<List<Verdict>>, seconds: Int, complete: Boolean) {
        val t = test ?: return
        val p = probe ?: return
        items.forEachIndexed { i, v -> p.items.getOrNull(i)?.let { item -> v.forEach { pairs += GameEngine.resourceOf(item.exercise) to it } } }
        advance(LevelPlacement.record(t, p, items.flatten(), seconds, complete))
    }

    /** The learner stopped between probes (✕ before answering the next one): what is known so far stands. */
    fun stop() {
        val t = test ?: return
        probe = null
        test = LevelPlacement.stop(t)
        finish()
    }

    /** The test is done: its answers paid (as a test's: the fresh-mind rule applies), its result. */
    private fun finish() {
        val t = test ?: return
        result = LevelPlacement.result(t, game.state)
        if (pairs.isNotEmpty() && reward == null) {
            reward = game.finishSheet(pairs.toList(), pairs.count { it.second == Verdict.CORRECT }, pairs.count { it.second == Verdict.ALMOST }, record = { it to false }, message = "")?.first
        }
    }

    /** The exercises of the probe on screen. */
    val exercises: List<Exercise> get() = probe?.items?.map { it.exercise }.orEmpty()

    /** "✓ Začnem tukaj · Start here": the result taken, the node told; the units marked and the level the app goes by now. */
    fun take(): Boolean {
        val t = test ?: return false
        val r = result ?: return false
        val c = school.course ?: return false
        if (decided) return false
        val id = Outbox.newId()
        val units = r.units.count { u -> game.state?.let { si.lanisce.lani.game.School.progress(it, c.language, u)?.done } == null }
        game.apply { LevelPlacement.apply(it, c, t, r, from, LocalDate.now(), System.currentTimeMillis(), report = id) }
        school.touch()
        report(t, r, applied = true, units = units, id = id)
        decided = true
        return true
    }

    /** "Ostanem, kjer sem · Keep my place" (or the result left): nothing changes; the tutor hears of the test. */
    fun keep() {
        val t = test ?: return
        val r = result ?: return
        if (decided) return
        report(t, r, applied = false, units = 0, id = Outbox.newId())
        decided = true
    }

    private fun report(t: LevelPlacement.Test, r: LevelPlacement.Result, applied: Boolean, units: Int, id: String) {
        if (!PlacementReport.worth(t)) return
        val raise = applied && r.rises(from)
        val body = PlacementReport.body(t, r, from, applied, units)
        val (text, data) = PlacementReport.sessionEnd(t, r, from, applied, units, raise, NAMES[language] ?: language, body)
        sync.submitLater(Writes.placement(body, text, data, id = id))
    }

    /** Leaves the test: forgotten (a result shown and not taken was kept: [keep] first). */
    fun close() {
        if (result != null && !decided) keep()
        test = null
        probe = null
        result = null
        reward = null
        asked = 0
    }

    /**
     * QA's "placement:answer:<part>" (a debug build): the test on screen answered to its end as a learner who knows the
     * course up to part [truth] (each item right there, wrong above; each probe its estimated time): its result shows.
     */
    fun qaAnswer(truth: Int) {
        while (true) {
            val p = probe ?: return
            val items = p.items.map { item -> List(item.items) { if (p.part <= truth) Verdict.CORRECT else Verdict.WRONG } }
            answered(items, p.items.sumOf { LevelPlacement.seconds(it.exercise, version) }, complete = true)
        }
    }

    /** The node's state came: a level the test raised is confirmed once the node has it. */
    fun check() {
        val n = node(language) ?: return
        if (last()?.confirmed == false) game.apply { LevelPlacement.confirm(it, language, n) }
    }

    private companion object {
        /** The village languages' English names, for the tutor. */
        val NAMES = mapOf("sl" to "Slovene", "en" to "English", "it" to "Italian", "de" to "German")
    }
}
