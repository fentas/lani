package si.lanisce.lani.data

import android.content.Context
import kotlinx.serialization.Serializable
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.l10n.bi
import java.io.File

/** The village as stored on the node: [rev] increases with every accepted write. */
@Serializable
data class GameSnapshot(val rev: Long, val state: GameState)

/** Local copy of the village. [dirty] = changed offline, not yet accepted by the node. */
@Serializable
data class GameCache(val rev: Long, val state: GameState, val dirty: Boolean = false)

/** Answer to PUT /game. */
sealed interface GamePut {
    data class Saved(val rev: Long) : GamePut
    /** Someone else wrote first; the node's copy wins. */
    data class Conflict(val snapshot: GameSnapshot) : GamePut
}

/** Pure merge rules between the local cache and the node's copy. */
object GameSync {
    sealed interface Decision {
        /** Take the node's copy as is. */
        data class Adopt(val snapshot: GameSnapshot) : Decision
        /** Local changes are based on the node's latest: send them. */
        data class Push(val cache: GameCache) : Decision
        /** Nothing anywhere: start a new village. */
        data object Create : Decision
    }

    /** [remote] null = the node has no village (404). */
    fun reconcile(local: GameCache?, remote: GameSnapshot?): Decision = when {
        remote == null -> if (local != null) Decision.Push(local.copy(rev = 0, dirty = true)) else Decision.Create
        local == null || !local.dirty -> Decision.Adopt(remote)
        local.rev == remote.rev -> Decision.Push(local)
        else -> Decision.Adopt(remote) // edited elsewhere meanwhile: the node's copy wins
    }

    /** The cache after a PUT of [sent] (the state that was sent), given the [current] local state. */
    fun afterPut(sent: GameState, current: GameState, result: GamePut): GameCache = when (result) {
        is GamePut.Saved -> GameCache(result.rev, current, dirty = current != sent)
        is GamePut.Conflict -> GameCache(result.snapshot.rev, result.snapshot.state, dirty = false)
    }

    fun encode(c: GameCache): String = json.encodeToString(GameCache.serializer(), c)
    fun decode(raw: String): GameCache? = runCatching { json.decodeFromString(GameCache.serializer(), raw) }.getOrNull()
}

/** Village event notifications from the background check: once per event, only while it runs. */
object VillageAlert {
    fun pending(state: GameState?, notifiedId: String?, now: Long): GameEvent? =
        state?.event?.takeIf { it.id != notifiedId && it.deadline > now }

    fun title(kind: EventKind): String = when (kind) {
        EventKind.WOLVES -> "🐺 ${bi("gameSync.wolvesAttackingVillage")}"
        EventKind.BEAR -> "🐻 ${bi("gameSync.bearVillage")}"
        EventKind.STORM -> "⛈️ ${bi("gameSync.stormComing")}"
        EventKind.MERCHANT -> "🧳 ${bi("gameSync.merchantHasArrived")}"
        EventKind.FESTIVAL -> "🎉 ${bi("gameSync.festivalVillage")}"
    }

    fun text(e: GameEvent, now: Long): String {
        val h = ((e.deadline - now) / 3_600_000).coerceAtLeast(0)
        val left = if (h >= 1) "$h h" else "<1 h"
        return "${bi("gameSync.timeLeft", "left" to left)}. ${bi("gameSync.openLaniToRespond")}"
    }
}

/**
 * The village cached in filesDir, for offline play and the background check. The app and the
 * background check write it from different threads, so writes take a process-wide lock (they
 * share the temp file).
 */
class GameStore(context: Context) {
    private val file = File(context.filesDir, "game.json")

    fun read(): GameCache? = synchronized(LOCK) { runCatching { file.readText() }.getOrNull() }?.let(GameSync::decode)

    fun write(c: GameCache) {
        val raw = GameSync.encode(c)
        synchronized(LOCK) {
            val tmp = File(file.parentFile, "game.json.tmp")
            tmp.writeText(raw)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    private companion object {
        val LOCK = Any()
    }
}
