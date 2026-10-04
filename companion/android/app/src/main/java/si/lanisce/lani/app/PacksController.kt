package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackInfo
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.data.Writes
import si.lanisce.lani.l10n.bi

/** Word packs ("Nove besede"): the list, which ones are new, and saving learned words. */
class PacksController(
    private val prefs: Prefs,
    private val bridge: () -> Bridge?,
    private val notices: Notices,
    private val sync: SyncController,
) {
    var list by mutableStateOf<List<PackInfo>>(emptyList())
        private set
    /** Tutor packs published since Jan last opened them. */
    var newIds by mutableStateOf<Set<String>>(emptySet())
        private set
    private var saving: Job? = null

    suspend fun reload(b: Bridge) {
        val seen = prefs.seenModules()
        list = runCatching { b.packs() }.getOrDefault(list) // an older bridge has no packs
        newIds = list.filter { it.source == "tutor" && it.learned == 0 && "pack:${it.id}" !in seen }.map { it.id }.toSet()
    }

    /** Pack [id] and the next words to learn from it; null (with a banner when all are known) when there are none. */
    suspend fun next(id: String): Pair<Pack, List<PackWord>>? {
        saving?.join() // the words just learned must be saved before choosing the next ones
        val p = bridge()?.pack(id) ?: return null
        prefs.markSeen("pack:$id")
        newIds = newIds - id
        val words = PackSession.nextWords(p)
        if (words.isEmpty()) {
            notices.banner = "✅ ${bi("packsController.knowEveryWordPack")}"
            return null
        }
        return p to words
    }

    /** Saves the learned words as review items on the node (through the outbox). */
    fun save(pack: Pack, results: List<Pair<String, Int>>, minutes: Int) = saveWords(pack.id, results, minutes)

    /**
     * Saves words of pack [packId] answered elsewhere (a festival's run in the village) the same way: the node adds
     * the ones Jan doesn't know yet and skips the rest.
     */
    fun saveWords(packId: String, results: List<Pair<String, Int>>, minutes: Int) {
        saving = sync.submitLater(Writes.learnPack(packId, results, minutes))
    }
}
