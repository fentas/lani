package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Sharing
import si.lanisce.lani.data.TownProgress
import si.lanisce.lani.data.TownProgresses
import si.lanisce.lani.data.Towns
import si.lanisce.lani.l10n.bi

/**
 * Learning progress between linked towns (data/TownProgress.kt; ui/towns/TownProgressScreen): a linked town's progress as
 * it shares it with this learner, asked through the learner's own bridge; what this learner shares with each town (the
 * friend's card's "👁️ Kaj vidi … · What … sees"); a parent's weekly note; a challenge sent from the progress screen.
 */
class TownProgressController(
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val notices: Notices,
) {
    /** The town on the progress screen, and its progress as its bridge answered last; null until it came. */
    var town by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf<TownProgress?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    /** Why the progress couldn't be had ("target · base"); null when it could. */
    var problem by mutableStateOf<String?>(null)
        private set
    /** A share level or the weekly note is being saved, or a challenge sent (their buttons wait). */
    var saving by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    /** Each link's sharing as changed here or answered last, by town id: newer than GET /towns' until it is loaded again. */
    private val sharings = mutableStateMapOf<String, Sharing>()
    /** The id of each challenge not answered yet, by its draft (a retry sends the same). */
    private val ids = HashMap<String, String>()

    /** Town [id]'s sharing: as changed here or answered last, else [listed] (GET /towns'). */
    fun sharingOf(id: String, listed: Sharing?): Sharing? = sharings[id] ?: listed

    /** GET /towns came again: its rows are the newest. */
    fun forgetSharings() = sharings.clear()

    /** Town [id]'s progress; [fresh]: asked again now. The screen shows what came, or why not. */
    fun load(id: String, fresh: Boolean = false) = scope.launch {
        if (town != id) {
            town = id
            progress = null
        }
        problem = null
        val b = bridge() ?: run {
            problem = "📡 ${bi("towns.cantReachTutor")}"
            return@launch
        }
        loading = true
        try {
            val (status, body) = b.townProgress(id, fresh)
            if (town != id) return@launch
            if (status == 200) {
                val p = TownProgresses.parse(body)
                progress = p
                p.sharing?.let { sharings[id] = it }
            } else {
                val why = TownProgresses.parseProblem(body)
                why.sharing?.let { sharings[id] = it }
                problem = TownProgresses.problem(status, why.code.ifBlank { Towns.errorCode(body) })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (town == id) problem = "📡 ${bi("towns.cantReachTutor")}"
        } finally {
            loading = false
        }
    }

    /** Leaves the progress screen: what it showed is dropped (it is asked again next time). */
    fun close() {
        town = null
        progress = null
        problem = null
    }

    private suspend fun putSharing(id: String, body: kotlinx.serialization.json.JsonObject): String? {
        val b = bridge() ?: return "📡 ${bi("towns.cantReachTutor")}"
        saving = true
        return try {
            val (status, raw) = b.townSharing(id, body)
            if (status == 200) {
                sharings[id] = TownProgresses.parseSharing(raw)
                null
            } else TownProgresses.problem(status, Towns.errorCode(raw))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "📡 ${bi("towns.cantReachTutor")}"
        } finally {
            saving = false
        }
    }

    /** What this learner shares with town [id] from now on ([level]); a refusal (a child's guardian link) is a banner. */
    fun setShare(id: String, level: String, learner: String) = scope.launch {
        val why = putSharing(id, buildJsonObject { put("share", level) })
        notices.banner = why ?: "👁️ ${bi("townProgress.nowSees", "name" to learner.ifBlank { "?" })}: ${si.lanisce.lani.ui.towns.TownProgressLogic.levelName(level)}"
        if (why == null && town == id) load(id, fresh = true)
    }

    /** A weekly note of town [id]'s progress, on or off (a parent's setting). */
    fun setWeekly(id: String, on: Boolean) = scope.launch {
        putSharing(id, buildJsonObject { put("weekly", on) })?.let { notices.banner = it }
    }

    /**
     * Sends town [id]'s learner a challenge ([type], [text], [hint]) as a family challenge there; [draft] names it, so a retry
     * sends the same id. [done] gets null once it went (the screen reloads, a banner says so), else why not.
     */
    fun sendChallenge(id: String, draft: String, type: String, text: String, hint: String, learner: String, done: (String?) -> Unit) {
        val b = bridge() ?: return done("📡 ${bi("towns.cantReachTutor")}")
        val cid = ids.getOrPut(draft) { TownProgresses.newId() }
        scope.launch {
            sending = true
            val why = try {
                val (status, raw) = b.townChallenge(id, buildJsonObject {
                    put("id", cid)
                    put("type", type)
                    put("text", text.trim().take(TownProgresses.MAX_TEXT))
                    hint.trim().takeIf { it.isNotEmpty() }?.let { put("hint", it.take(TownProgresses.MAX_HINT)) }
                })
                // answered either way: a retry of something refused is a new try
                if (status in 200..499) ids.remove(draft)
                if (status == 200) null else TownProgresses.problem(status, Towns.errorCode(raw))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "📡 ${bi("towns.cantReachTutor")}"
            } finally {
                sending = false
            }
            if (why == null) {
                notices.banner = "💌 ${bi("townProgress.challengeSent", "name" to learner.ifBlank { "?" })}"
                if (town == id) load(id, fresh = true)
            }
            done(why)
        }
    }
}
