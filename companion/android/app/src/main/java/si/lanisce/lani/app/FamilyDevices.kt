package si.lanisce.lani.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.data.FamilyBook
import si.lanisce.lani.data.FamilyKeyWaiting
import si.lanisce.lani.data.FamilyLink
import si.lanisce.lani.data.FamilyMember
import si.lanisce.lani.data.FamilyNote
import si.lanisce.lani.data.FamilyNsd
import si.lanisce.lani.data.FamilyParent
import si.lanisce.lani.data.FamilyPost
import si.lanisce.lani.data.HttpDrop
import si.lanisce.lani.tutor.KeyVault
import si.lanisce.lani.tutor.live.FamilyKey
import java.io.Closeable
import java.io.IOException

/** What the family's post asks of the app. */
interface FamilyHome {
    /** A parent set a limit on this device, or took it back. */
    suspend fun cap(cap: Double?, by: String)

    /** This device left the family. */
    suspend fun forget(p: FamilyParent)

    /** A key that waited for its check, tried again. */
    suspend fun check(k: FamilyKeyWaiting): FamilyKey
}

/**
 * One device's rounds of the family's post (data/FamilyPost), without Android: what its [book] says goes to the other side and
 * what comes back changes it. [book] and [change] hold the book; a change is atomic.
 */
class FamilyCourier(
    private val book: () -> FamilyBook,
    private val change: ((FamilyBook) -> FamilyBook) -> Unit,
    private val home: FamilyHome,
    private val clock: () -> Long = System::currentTimeMillis,
) : FamilyPost.Desk {
    // --- the side that shared ------------------------------------------------------------------------------------------

    override fun keys(id: String): FamilyPost.Keys? = book().member(id)?.let { FamilyPost.Keys.of(it.bond) }

    override fun heard(id: String, seen: Long): FamilyNote? {
        change { it.heard(id, seen, clock()) }
        return book().member(id)?.pending
    }

    /** Through the relay: what each device said, and the note that waits for it, once, or again after [FamilyPost.REPOST_MS]. */
    fun postRelay(drop: FamilyLink.Drop) {
        for (m in book().members) {
            val k = FamilyPost.Keys.of(m.bond) ?: continue
            for (note in drop.take(k.boxUp, 0)) FamilyPost.openSeen(k, note)?.let { heard(m.id, it) }
            val now = book().member(m.id) ?: continue
            val p = now.pending ?: continue
            if (now.posted != null && clock() - now.posted < FamilyPost.REPOST_MS) continue
            drop.put(k.boxDown, FamilyPost.sealNote(k, p), FamilyPost.NOTE_TTL_S)
            change { it.posted(m.id, clock()) }
        }
    }

    // --- the device that came from a family ------------------------------------------------------------------------------

    /** A note from the parent's device, taken once. */
    suspend fun take(note: FamilyNote) {
        val b = book()
        val p = b.parent ?: return
        b.took(note) ?: return
        if (note.kind == FamilyNote.FORGET) home.forget(p) else home.cap(note.cap, p.name)
        change { it.took(note) ?: it }
    }

    /** Through the relay: what waits, taken, and said to have come; a device that has nothing to say says it's there now and then. */
    suspend fun collectRelay(drop: FamilyLink.Drop) {
        val p = book().parent ?: return
        val k = FamilyPost.Keys.of(p.bond) ?: return
        val notes = drop.take(k.boxDown, 0).mapNotNull { FamilyPost.openNote(k, it) }.sortedBy { it.seq }
        for (n in notes) take(n)
        val seen = maxOf(p.seen, notes.maxOfOrNull { it.seq } ?: 0)
        if (notes.isNotEmpty() || p.said == null || clock() - p.said >= FamilyPost.SAY_MS) {
            drop.put(k.boxUp, FamilyPost.sealSeen(k, seen), FamilyPost.NOTE_TTL_S)
            change { b -> b.parent?.let { b.copy(parent = it.copy(said = clock())) } ?: b }
        }
    }

    /** A visit to the parent's device at [host]:[port] on this Wi-Fi. */
    fun visit(host: String, port: Int): Boolean {
        val p = book().parent ?: return false
        val k = FamilyPost.Keys.of(p.bond) ?: return false
        return FamilyPost.visit(host, port, p.member, k, p.seen) { note ->
            runBlocking { take(note) }
            note.seq
        }
    }
}

/**
 * The family's devices on this one ("👪" in the settings; docs/plans/05-built/leftovers-app.md): the devices it shared with, what
 * a parent sets on them, the device it came from. Kept sealed in [vault]. The post goes while the app is on screen: the
 * parent's device answers on its Wi-Fi while it has something to say or the page is open, the other device comes by, and with a
 * relay set each looks in its boxes. Neither runs behind the learner's back, so a note waits until both are open on one Wi-Fi
 * or the relay is there. A child's device can't set anything: [setCap], [remove] and [drop] do nothing there.
 */
class FamilyDevices(
    private val scope: CoroutineScope,
    private val context: Context,
    private val vault: KeyVault,
    private val home: FamilyHome,
    private val child: () -> Boolean,
    private val relay: () -> String,
    private val online: () -> Boolean,
) {
    var book by mutableStateOf(FamilyBook())
        private set

    private val lock = Any()
    private var current = FamilyBook()
    private val saving = Mutex()
    private val courier = FamilyCourier({ synchronized(lock) { current } }, ::change, home)
    private val nsd by lazy { FamilyNsd(context) }
    private val poke = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null
    private var pageOpen = false
    private var office: FamilyPost.Office? = null
    private var name: Closeable? = null

    /** QA's "family:peer:<host>:<port>": two emulators can't find each other on a Wi-Fi. */
    var peer: Pair<String, Int>? = null

    /** Changes made before the book was read: they go on top of it, or a slow read would take them back. */
    private var early: MutableList<(FamilyBook) -> FamilyBook>? = mutableListOf()

    private val loaded = scope.launch(Dispatchers.IO) {
        val b = FamilyBook.parse(runCatching { vault.load() }.getOrNull())
        val changed = synchronized(lock) {
            val all = early.orEmpty()
            early = null
            current = all.fold(b) { book, f -> f(book) }
            all.isNotEmpty()
        }
        book = current
        if (changed) save()
    }

    /** The book is read. */
    suspend fun ready() = loaded.join()

    private fun change(f: (FamilyBook) -> FamilyBook) {
        val next = synchronized(lock) {
            early?.let {
                it += f
                return
            }
            f(current).also { current = it }
        }
        book = next
        scope.launch(Dispatchers.IO) { save() }
    }

    private suspend fun save() {
        saving.withLock { runCatching { vault.save(synchronized(lock) { current }.text()) } }
    }

    /** This device's family id, made the first time it shares. */
    suspend fun familyId(): String {
        ready()
        return synchronized(lock) { current.family } ?: FamilyPost.newId().also { id -> change { it.copy(family = id) } }
    }

    /** A parent's hold sent the gift: [name] is on the list. */
    fun added(family: String, member: String, name: String, bond: ByteArray) {
        change { it.added(family, member, name, bond, System.currentTimeMillis()) }
    }

    /** What the family shared arrived here. */
    fun joined(gift: FamilyLink.Gift, bond: ByteArray?) {
        if (bond != null) change { it.joined(gift, bond) }
    }

    /** A key that crossed but couldn't be checked: kept sealed, and tried again each round until it can be. */
    fun keyWaits(g: FamilyLink.Gift) {
        val key = g.key ?: return
        val provider = g.provider ?: return
        change { it.copy(keyWaiting = FamilyKeyWaiting(provider, key, g.preset)) }
    }

    fun setCap(id: String, cap: Double?) {
        if (child()) return
        change { it.setCap(id, cap) }
        poke.trySend(Unit)
    }

    fun remove(id: String) {
        if (child()) return
        change { it.remove(id) }
        poke.trySend(Unit)
    }

    fun drop(id: String) {
        if (child()) return
        change { it.drop(id) }
    }

    /**
     * This device leaves by itself: the way out when the device that set a limit is gone, since only that one could raise it. What it
     * costs is what the limit protects: the family's key goes with it. On a child's device it stands behind a parent's hold.
     */
    fun leave() {
        val p = synchronized(lock) { current.parent } ?: return
        scope.launch(Dispatchers.IO) {
            home.forget(p)
            change { it.copy(parent = null) }
        }
    }

    /** The app came to the screen, or left it. */
    fun foreground(on: Boolean) {
        val before = loop
        if (!on) {
            before?.cancel()
            loop = null
            return
        }
        if (before?.isActive == true) return
        // the office is the loop's alone, opened and closed on it: nothing keeps it open once the app has left the screen
        loop = scope.launch(Dispatchers.IO) {
            before?.join()
            loaded.join()
            try {
                while (isActive) {
                    runCatching { round() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    withTimeoutOrNull(ROUND_MS) { poke.receive() }
                }
            } finally {
                closeOffice()
            }
        }
    }

    /** The family's page opened or closed: while it's open the post looks more often, so what's set is seen to arrive. */
    fun page(open: Boolean) {
        pageOpen = open
        poke.trySend(Unit)
    }

    private suspend fun round() {
        val b = synchronized(lock) { current }
        b.keyWaiting?.let { k -> checkKey(k) }
        val drop = relay().takeIf { it.isNotBlank() }?.let(::HttpDrop)
        val saying = b.members.isNotEmpty() && (pageOpen || b.members.any { it.pending != null })
        if (saying) b.family?.let(::openOffice) else closeOffice()
        if (drop != null && b.members.isNotEmpty()) try {
            courier.postRelay(drop)
        } catch (_: IOException) {
            // the relay isn't there now: the next round tries
        }
        val p = b.parent ?: return
        if (drop != null) try {
            courier.collectRelay(drop)
        } catch (_: IOException) {
        }
        val at = peer ?: FamilyShareController.wifiAddress(context)?.let { nsd.find(FamilyPost.serviceName(p.family), FIND_MS) }
        if (at != null) courier.visit(at.first, at.second)
    }

    private suspend fun checkKey(k: FamilyKeyWaiting) {
        if (!online()) return
        val done = home.check(k)
        if (done != FamilyKey.NOT_CHECKED) change { if (it.keyWaiting == k) it.copy(keyWaiting = null) else it }
    }

    private fun openOffice(family: String) {
        if (office != null) return
        val address = FamilyShareController.wifiAddress(context) ?: return
        // QA's forwarded port reaches the emulator on its loopback: every address in a debug build, the Wi-Fi's alone otherwise
        val o = runCatching { FamilyPost.Office(address.takeUnless { BuildConfig.QA }, courier) }.getOrNull() ?: return
        office = o
        name = nsd.advertise(FamilyPost.serviceName(family), o.port)
        if (BuildConfig.QA) Log.i(TAG, "office on ${o.port}")
        scope.launch(Dispatchers.IO) { o.serve() }
    }

    private fun closeOffice() {
        runCatching { name?.close() }
        name = null
        office?.close()
        office = null
    }

    companion object {
        private const val TAG = "Family"
        private const val ROUND_MS = 45_000L
        private const val FIND_MS = 4_000L
    }
}
