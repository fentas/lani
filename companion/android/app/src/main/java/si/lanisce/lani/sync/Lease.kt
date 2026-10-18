package si.lanisce.lani.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** JSON for what the sync writes: nulls written out (the lease's "holder": null, a tree entry's "sha": null). */
internal val syncJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
}

/** JSON as it is read: missing fields take their defaults. */
internal val syncRead = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** A side of the lease: the node (self-hosted) or a phone (the hosted mode), by its device and the name it shows. */
@Serializable
data class Party(
    val side: String,
    val device: String = "",
    val name: String = "",
    val mode: String = if (side == NODE) "self-hosted" else "hosted",
) {
    val isNode: Boolean get() = side == NODE

    /** The node is the node on any machine; a phone is one device. */
    fun sameAs(other: Party?): Boolean = other != null && other.side == side && (side == NODE || other.device == device)

    companion object {
        const val NODE = "node"
        const val PHONE = "phone"
    }
}

/** The side a lease is handed over to ([to]'s side, and device when it names one), by whom and when. */
@Serializable
data class Offer(
    val side: String,
    val device: String = "",
    val name: String = "",
    val mode: String = if (side == Party.NODE) "self-hosted" else "hosted",
    val by: Party? = null,
    val at: String? = null,
) {
    val to: Party get() = Party(side, device, name, mode)

    fun isFor(me: Party): Boolean = side == me.side && (device.isEmpty() || me.isNode || device == me.device)
}

/** What a lease is for one side (the same states as .claude/hooks/lani_lease.py). */
enum class LeaseState {
    /** It holds the lease: it writes. */
    MINE,
    /** Handed over to it: it may take the lease. */
    OFFERED,
    /** It handed the lease over, and the other side hasn't taken it yet: it may take it back. */
    OFFERED_AWAY,
    /** Released, or no lease and no data yet: anyone may take it. */
    FREE,
    /** The holder didn't renew it in time: it may be taken without a handover. */
    EXPIRED,
    /** No lease, but data a node wrote: taken only by a takeover the learner confirms. */
    UNCLAIMED,
    /** Another side or device holds it, or it is offered to another: taken only by a confirmed takeover. */
    OTHER;

    val takeable: Boolean get() = this in setOf(MINE, OFFERED, OFFERED_AWAY, FREE, EXPIRED)
}

/**
 * Who writes the learner's data repository: app/lease.json (docs/plans/05-built/git-sync.md, "The lease"). The rules are
 * .claude/hooks/lani_lease.py's and companion/bridge/src/lease.ts's: keep the three in step.
 *
 * [holder] writes; null while a handover waits for the side the [offer] names, or after a release. [since]: when the
 * holder took it; [renewed]: its last renewal (once a day with a sync of the holder's); [expires]: renewed + 14 days. A
 * lease that ran out may be taken without a handover; it never lets anyone write by itself.
 */
@Serializable
data class Lease(
    val schema: String = SCHEMA,
    val holder: Party? = null,
    val offer: Offer? = null,
    val since: String? = null,
    val renewed: String? = null,
    val expires: String? = null,
) {
    fun expired(now: Instant): Boolean = time(expires)?.let { now.isAfter(it) } ?: false

    fun toJson(): String = syncJson.encodeToString(serializer(), this) + "\n"

    /** This lease renewed by its holder [me] when its last renewal is a day old or more; null when nothing is due. */
    fun renewedBy(me: Party, now: Instant): Lease? {
        if (!me.sameAs(holder)) return null
        val last = time(renewed)
        if (last != null && Duration.between(last, now) < RENEW_EVERY) return null
        return copy(renewed = iso(now), expires = iso(now.plus(TERM)))
    }

    companion object {
        const val SCHEMA = "lani.lease/v1"
        const val FILE = "app/lease.json"
        val TERM: Duration = Duration.ofDays(14)
        val RENEW_EVERY: Duration = Duration.ofDays(1)

        fun iso(t: Instant): String = t.truncatedTo(ChronoUnit.SECONDS).toString()

        fun time(s: String?): Instant? = s?.let {
            try {
                Instant.parse(it)
            } catch (e: DateTimeParseException) {
                null
            }
        }

        /** A lease read from its file; null when there is no file ([text] null); [Unreadable] when it can't be read. */
        fun parse(text: String?): LeaseRead = when {
            text == null -> LeaseRead.None
            else -> runCatching { syncRead.decodeFromString(serializer(), text) }.getOrNull()
                ?.takeIf { it.schema.startsWith("lani.lease/") && (it.holder == null || it.holder.side in SIDES) && (it.offer == null || it.offer.side in SIDES) }
                ?.let { LeaseRead.Found(it) }
                ?: LeaseRead.Unreadable
        }

        private val SIDES = setOf(Party.NODE, Party.PHONE)

        /** A lease [me] holds from [now]. */
        fun held(me: Party, now: Instant) = Lease(holder = me, since = iso(now), renewed = iso(now), expires = iso(now.plus(TERM)))

        /** A lease handed over to [to] by [by]: nobody writes until [to] takes it (or [by] takes it back). */
        fun offered(to: Party, by: Party, now: Instant) = Lease(
            holder = null,
            offer = Offer(to.side, to.device, to.name, to.mode, by, iso(now)),
            since = iso(now), renewed = iso(now), expires = iso(now.plus(TERM)),
        )
    }
}

/** What app/lease.json says: no file, a lease, or a file that can't be read (nobody may write then). */
sealed interface LeaseRead {
    data object None : LeaseRead
    data object Unreadable : LeaseRead
    data class Found(override val lease: Lease) : LeaseRead

    val lease: Lease? get() = (this as? Found)?.lease

    /** What it is for [me] ([hasData]: whether the repository holds a learner's data, for a repository without a lease). */
    fun stateFor(me: Party, now: Instant = Instant.now(), hasData: Boolean = true): LeaseState = when (this) {
        None -> if (me.isNode) LeaseState.MINE else if (hasData) LeaseState.UNCLAIMED else LeaseState.FREE
        Unreadable -> LeaseState.OTHER
        is Found -> {
            val l = lease
            when {
                l.holder != null -> if (me.sameAs(l.holder)) LeaseState.MINE else if (l.expired(now)) LeaseState.EXPIRED else LeaseState.OTHER
                l.offer != null -> when {
                    l.offer.isFor(me) -> LeaseState.OFFERED
                    me.sameAs(l.offer.by) -> LeaseState.OFFERED_AWAY
                    l.expired(now) -> LeaseState.EXPIRED
                    else -> LeaseState.OTHER
                }
                else -> LeaseState.FREE
            }
        }
    }

    fun mayWrite(me: Party, now: Instant = Instant.now()): Boolean = stateFor(me, now) == LeaseState.MINE
}

/** "the phone (Pixel 8)", "the node (jan-desktop)": for the lease's lines in English (logs, commit messages). */
internal fun Party?.who(): String = if (this == null) "nobody" else "the $side" + (name.ifBlank { device }.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
