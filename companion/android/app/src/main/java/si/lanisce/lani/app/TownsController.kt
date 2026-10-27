package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.AcceptOutcome
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.FriendTown
import si.lanisce.lani.data.FriendTrouble
import si.lanisce.lani.data.FriendshipList
import si.lanisce.lani.data.Friendships
import si.lanisce.lani.data.GuestEntry
import si.lanisce.lani.data.TownMove
import si.lanisce.lani.data.InviteParse
import si.lanisce.lani.data.InviteLink
import si.lanisce.lani.data.LinkRequest
import si.lanisce.lani.data.TownChild
import si.lanisce.lani.data.TownChildren
import si.lanisce.lani.data.TownInvite
import si.lanisce.lani.data.Towns
import si.lanisce.lani.data.TownsList
import si.lanisce.lani.l10n.bi
import java.io.IOException

/** What a friendship's write came to: done (the answer's [body]), or why not. */
sealed interface FriendWrite {
    data class Done(val body: String) : FriendWrite
    /** Why not, for the learner ("target · base"). */
    data class Failed(val problem: String) : FriendWrite
}

/**
 * "Prijatelji · Friends" (data/Towns.kt): the towns linked with this learner's, asked through the learner's own
 * bridge, and the invitations: one made to show as a QR code, one scanned, pasted or opened from a link to accept; the
 * friendship with each town (data/Friendship.kt) and what can be done for it: help sent, a move offered or answered. A
 * child's links wait for a parent ([TownsList.requests], [withdraw]); a parent's app says yes or no to their children's
 * ([children], [decide]) and links their own child's town of the node ([linkChild]).
 */
class TownsController(
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val notices: Notices,
) {
    /** The linked towns as the bridge said last; null until it answered. */
    var list by mutableStateOf<TownsList?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    /** Why the list couldn't be had ("target · base"); null when it could. */
    var problem by mutableStateOf<String?>(null)
        private set
    /** The invitation to show, once made. */
    var invite by mutableStateOf<TownInvite?>(null)
        private set
    /** What guests from linked towns brought, newest first (GET /towns/guests): help, gifts and messages, trades. */
    var guests by mutableStateOf<List<GuestEntry>>(emptyList())
        private set
    var inviting by mutableStateOf(false)
        private set
    var accepting by mutableStateOf(false)
        private set
    /**
     * Each linked town's friendship (GET /towns/friendship, data/Friendship.kt): the level both towns agree on, what it
     * brings, the friend's trouble, the feasts, the moves; null until it came (or from an older bridge).
     */
    var friendships by mutableStateOf<FriendshipList?>(null)
        private set
    /** A friendship's write is on its way (the buttons wait). */
    var sending by mutableStateOf(false)
        private set
    /**
     * A parent's children's towns (GET /towns/children): what waits for their yes, the node's children to link; null until
     * it came, from an older bridge, and in a child's app.
     */
    var children by mutableStateOf<TownChildren?>(null)
        private set
    /** An invitation opened from a link (lani://town?…, https://lani.page/town/#…): the Accept dialog shows it filled in. */
    var incoming by mutableStateOf<String?>(null)
        private set
    /** The decisions (a request's id) and links (a town's id) on their way: their buttons wait. */
    var busy by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Told of every friendship list that came: the village applies what it brings (GameController.applyFriendships). */
    var onFriendships: (FriendshipList) -> Unit = {}
    /** The id of each friendship write not answered yet ("aid:<town>:<event>" …), so a retry sends the same. */
    private val ids = HashMap<String, String>()

    fun friendship(id: String): FriendTown? = friendships?.towns?.firstOrNull { it.id == id }

    fun load() = scope.launch {
        val b = bridge() ?: run {
            problem = "📡 ${bi("towns.cantReachTutor")}"
            return@launch
        }
        loading = true
        try {
            val l = b.towns()
            if (l == null) problem = "🧰 ${bi("towns.tutorTooOld")}" else {
                list = l
                problem = null
            }
            // a parent's children: what waits for their yes (an older bridge has no such route: 404, none)
            if (l?.self?.child == false) children = runCatching { b.townChildren() }.getOrNull()?.let { (code, body) -> if (code == 200) runCatching { Towns.parseChildren(body) }.getOrNull() else null }
            // what guests brought (a child's parent sees the messages here too); an older bridge has none
            guests = runCatching { b.townGuests() }.getOrNull() ?: guests
            // the friendships (an older bridge has none): shown, and what they bring applied to the village
            runCatching { b.friendships() }.getOrNull()?.let {
                friendships = it
                onFriendships(it)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problem = "📡 ${bi("towns.cantReachTutor")}"
        } finally {
            loading = false
        }
    }

    /** Makes an invitation; [invite] shows it. A failure is a snackbar message. */
    fun makeInvite() = scope.launch {
        val b = bridge() ?: return@launch
        inviting = true
        try {
            val (status, body) = b.townInvite()
            if (status == 200) invite = Towns.parseInvite(body) else notices.banner = Towns.problem(status, Towns.errorCode(body))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notices.banner = "📡 ${bi("towns.cantReachTutor")}"
        } finally {
            inviting = false
        }
    }

    fun closeInvite() {
        invite = null
    }

    /** An invitation opened from a link: Friends' Accept dialog shows it filled in (the learner accepts it, never by itself). */
    fun receive(text: String) {
        incoming = text.trim()
    }

    fun clearIncoming() {
        incoming = null
    }

    // --- a child's links that wait for a parent (companion/README.md, "A child's town") ------------------------------------

    /** A POST of a child's links' routes, with [key] busy meanwhile: its status and body, or null when the bridge isn't there. */
    private suspend fun post(key: String, path: String, body: JsonObject = buildJsonObject { }): Pair<Int, String>? {
        val b = bridge() ?: run {
            notices.banner = "📡 ${bi("towns.cantReachTutor")}"
            return null
        }
        busy = busy + key
        return try {
            b.townPost(path, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notices.banner = "📡 ${bi("towns.cantReachTutor")}"
            null
        } finally {
            busy = busy - key
        }
    }

    /** The parent's yes ([approve]) or no to [r], a link of their [child]'s that waits for them: sent to the child's town. */
    fun decide(child: TownChild, r: LinkRequest, approve: Boolean) = scope.launch {
        val (status, body) = post(r.id, "/towns/children/${child.id}/requests/${r.id}", buildJsonObject { put("approve", approve); put("town", r.town.id) }) ?: return@launch
        notices.banner = when {
            status != 200 -> Towns.problem(status, Towns.errorCode(body))
            approve -> "🤝 ${bi("towns.approvedLine", "child" to child.learner.ifBlank { child.name }, "town" to r.town.name)}"
            else -> "👋 ${bi("towns.notThisTime", "town" to r.town.name)}"
        }
        load()
    }

    /** A parent links their own [child]'s town of this node, as its guardian (the app's `lani-town link`). */
    fun linkChild(child: TownChild) = scope.launch {
        val (status, body) = post(child.id, "/towns/children/${child.id}/link") ?: return@launch
        notices.banner = if (status == 200) "👪 ${bi("towns.childLinked", "child" to child.learner.ifBlank { child.name })}" else Towns.problem(status, Towns.errorCode(body))
        load()
    }

    /** A child takes back [r], a link of theirs that waits for the parent (the other town lets go of it). */
    fun withdraw(r: LinkRequest) = scope.launch {
        val (status, body) = post(r.id, "/towns/requests/${r.id}/withdraw") ?: return@launch
        if (status != 200) notices.banner = Towns.problem(status, Towns.errorCode(body))
        load()
    }

    // --- friendship between towns (data/Friendship.kt) -----------------------------------------------------------------

    /**
     * A friendship's write to town [t] ([what]: "aid", "moves", "moves/<id>/accept" …), its body with an id when [idKey]
     * names the write (the same id on a retry): the answer's body, or why not ("target · base").
     */
    private suspend fun write(t: FriendTown, what: String, idKey: String?, kind: String, body: (String?) -> JsonObject): FriendWrite {
        val b = bridge() ?: return FriendWrite.Failed("📡 ${bi("towns.cantReachTutor")}")
        val id = idKey?.let { ids.getOrPut(it) { Friendships.newId(kind) } }
        sending = true
        try {
            val (status, raw) = b.friendshipWrite(t.id, what, body(id))
            // answered either way: a retry of something refused is a new try
            if (status in 200..499) idKey?.let { ids.remove(it) }
            return if (status == 200) FriendWrite.Done(raw) else FriendWrite.Failed(Friendships.problem(status, Towns.errorCode(raw), t.name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return FriendWrite.Failed("📡 ${bi("towns.cantReachTutor")}")
        } finally {
            sending = false
        }
    }

    /** Help against town [t]'s trouble [e] (POST /towns/:id/aid): once per event (a retry sends the same id). */
    suspend fun aid(t: FriendTown, e: FriendTrouble): FriendWrite =
        write(t, "aid", "aid:${t.id}:${e.id}", "aid") { id -> buildJsonObject { put("id", id); put("event", e.id) } }

    /** Offers town [t] a move ([dir] "in": someone of theirs moves here; "out": someone of here moves there). */
    suspend fun offerMove(t: FriendTown, dir: String): FriendWrite =
        write(t, "moves", "move:${t.id}:$dir", "move") { id -> buildJsonObject { put("id", id); put("dir", dir) } }

    /** Says yes ([yes]) to move [m] town [t] offered, or declines it (or withdraws this town's own offer). */
    suspend fun answerMove(t: FriendTown, m: TownMove, yes: Boolean): FriendWrite =
        write(t, "moves/${m.id}/${if (yes) "accept" else "decline"}", null, "move") { buildJsonObject { } }

    /**
     * Accepts the invitation in [text] (scanned or pasted): checked here first, then by the bridge, which asks the
     * inviting town. [done] gets null once linked (the list reloads, a banner says with whom), else the message.
     */
    fun accept(text: String, done: (String?) -> Unit) {
        val link = when (val p = InviteLink.parse(text)) {
            is InviteParse.Invalid -> return done(p.problem.message)
            is InviteParse.Ok -> p.link
        }
        val b = bridge() ?: return done("📡 ${bi("towns.cantReachTutor")}")
        scope.launch {
            accepting = true
            val outcome = try {
                // as lani://town?…, which every bridge reads (one older than the web links reads only that)
                val (status, body) = b.townAccept(link.text())
                Towns.acceptOutcome(status, body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AcceptOutcome.Failed("📡 ${bi("towns.cantReachTutor")}")
            } finally {
                accepting = false
            }
            when (outcome) {
                is AcceptOutcome.Linked -> {
                    notices.banner = when (outcome.pending) {
                        Towns.WAIT_PARENT -> "⏳ ${bi("towns.waitsForParent", "town" to outcome.name)}"
                        Towns.WAIT_THEIR_PARENT -> "⏳ ${bi("towns.waitsForTheirParent", "learner" to outcome.learner.ifBlank { outcome.name })}"
                        else -> "🤝 ${bi("towns.linkedWith", "town" to outcome.name, "learner" to outcome.learner)}"
                    }
                    incoming = null
                    load()
                    done(null)
                }
                is AcceptOutcome.Failed -> done(outcome.message)
            }
        }
    }
}
