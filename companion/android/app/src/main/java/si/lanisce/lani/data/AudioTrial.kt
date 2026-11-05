package si.lanisce.lani.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The audio trial on this device (docs/plans/05-built/first-minutes.md, "What this leaves for the audio trial"; Jan: "all
 * voices with no account and no card, then Piper and the phone's voices with nothing lost"). Pure: [AudioTrialStore] keeps
 * it in the device's preferences, not in the learner's data, so it is in no export, no backup and no git sync.
 *
 * [id] is all the voice service knows of this install: 32 hex characters from [SecureRandom], made once, sent as `lani_<id>`.
 * Nothing else that could tell a device goes with it.
 */
@Serializable
data class AudioTrial(
    val id: String = "",
    /** [NONE], [ACTIVE], [ENDED] or [CLOSED], as the voice service last said; [ENDED] also by the date or a 403 here. */
    val status: String = NONE,
    /** When it began, ms; 0 while none did. Set once it did: the voice service's clips may be on the phone. */
    val startedAt: Long = 0,
    /** When it ends, ms; when it ended, once it did. */
    val endsAt: Long = 0,
    /** How long it lasts, as the voice service says. */
    val days: Int = 0,
    /** The card that says what it is, or the one that says it ended, was closed: never again. */
    val startSeen: Boolean = false,
    val endSeen: Boolean = false,
    /** After [CLOSED]: asked again from this moment on, the next day. */
    val askAfter: Long = 0,
    /** When the voice service last said how it stands: read again a day later at the soonest. */
    val readAt: Long = 0,
) {
    /** What the app asks the voice service: to start one, or how the one under way stands. */
    enum class Ask { START, READ }

    /** A card on Home, each once. */
    enum class Card { START, END }

    /** Its voices are the village's now: until the end, by the voice service's date or word. */
    fun live(now: Long): Boolean = status == ACTIVE && now < endsAt

    /** The voice routes while it lasts: the language learned is in their base; null with no service or no trial. */
    fun voices(glas: String, lang: String, now: Long): BridgeConfig? =
        if (glas.isBlank() || id.isEmpty() || !live(now)) null else BridgeConfig("${glas.trimEnd('/')}/v1/app/$lang", token(id))

    /**
     * What is due at [now], at most: a trial not started yet, or not offered yesterday; how the one under way stands once a
     * day; nothing after its end.
     */
    fun ask(now: Long): Ask? = when (status) {
        NONE -> Ask.START
        CLOSED -> Ask.START.takeIf { now >= askAfter }
        ACTIVE -> Ask.READ.takeIf { live(now) && now - readAt >= DAY_MS }
        else -> null
    }

    /** This install's id, made the first time it is needed: never on a node, never without a voice service. */
    fun withId(make: () -> String = ::newId): AudioTrial = if (id.isEmpty()) copy(id = make()) else this

    /**
     * What the voice service said ([a], at [now]). [CLOSED] says nothing of a trial under way, which goes on until its date or
     * a 403; before one it is only asked again tomorrow. After the end nothing changes it.
     */
    fun answered(a: TrialAnswer, now: Long): AudioTrial = when {
        status == ENDED -> this
        a.status == ACTIVE || a.status == ENDED -> copy(status = a.status, startedAt = a.startedAt, endsAt = a.endsAt, days = a.days, askAfter = 0, readAt = now)
        a.status == CLOSED && status == ACTIVE -> copy(readAt = now)
        a.status == CLOSED -> copy(status = CLOSED, askAfter = now + DAY_MS, readAt = now)
        a.status == NONE -> copy(status = NONE, readAt = now)
        else -> this
    }

    /** A voice route said it is over (403 trial_ended), whatever the date said: at once. */
    fun ended(now: Long): AudioTrial = if (status == ACTIVE) copy(status = ENDED, endsAt = minOf(endsAt, now)) else this

    /** Its date passed: over, whether or not the voice service could say so. */
    fun settled(now: Long): AudioTrial = if (status == ACTIVE && !live(now)) copy(status = ENDED) else this

    /** The card to show on Home, on a learner on this device ([hosted]): once each, what it is, then that it ended. */
    fun card(hosted: Boolean, now: Long): Card? = when {
        !hosted -> null
        live(now) && !startSeen -> Card.START
        status == ENDED && !endSeen -> Card.END
        else -> null
    }

    /** [c] was closed: never again. */
    fun seen(c: Card): AudioTrial = if (c == Card.START) copy(startSeen = true) else copy(endSeen = true)

    /** A trial that began or ended here: the settings say so. */
    val said: Boolean get() = status == ACTIVE || status == ENDED

    /** The day it ends on, or ended on, here. */
    fun endDay(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(endsAt).atZone(zone).toLocalDate()

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val NONE = "none"
        const val ACTIVE = "active"
        const val ENDED = "ended"
        const val CLOSED = "closed"
        const val DAY_MS = 24 * 60 * 60_000L

        /** What a voice route's 403 says when the trial is over. */
        const val ENDED_CODE = "trial_ended"

        /** What goes on the wire for [id]: the voice service keeps only a hash of it. */
        fun token(id: String): String = "lani_$id"

        /** A new install id. */
        fun newId(random: SecureRandom = SecureRandom()): String =
            ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

        /** What [encode] wrote; nothing kept, or nothing readable: no trial yet. */
        fun decode(s: String?): AudioTrial = s?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: AudioTrial()

        /** [raw], a 403's body, says the trial is over: {"error":{"code":"trial_ended",…}}. */
        fun endedBy(raw: String): Boolean = runCatching {
            ((json.parseToJsonElement(raw) as JsonObject)["error"] as JsonObject)["code"]?.jsonPrimitive?.contentOrNull == ENDED_CODE
        }.getOrDefault(false)
    }
}

/** What GET or POST /v1/trial says. [startedAt], [endsAt] in ms, [days]: of [AudioTrial.ACTIVE] and [AudioTrial.ENDED]. */
data class TrialAnswer(val status: String, val startedAt: Long = 0, val endsAt: Long = 0, val days: Int = 0) {
    companion object {
        /** The answer; null for one that isn't any of them, so it changes nothing. */
        fun parse(raw: String): TrialAnswer? = runCatching {
            val o = json.parseToJsonElement(raw) as JsonObject
            when (val s = o["status"]?.jsonPrimitive?.contentOrNull) {
                AudioTrial.ACTIVE, AudioTrial.ENDED -> TrialAnswer(
                    s,
                    Instant.parse(o.getValue("started_at").jsonPrimitive.content).toEpochMilli(),
                    Instant.parse(o.getValue("ends_at").jsonPrimitive.content).toEpochMilli(),
                    o.getValue("days").jsonPrimitive.int,
                )
                AudioTrial.CLOSED, AudioTrial.NONE -> TrialAnswer(s)
                else -> null
            }
        }.getOrNull()
    }
}

/**
 * The voice service's trial routes ([glas]: its address). Sends the install's token and nothing else. Null when the answer
 * says nothing ([TrialAnswer.parse]); an IOException when it can't be reached: both change nothing, and it is asked again later.
 */
class TrialApi(glas: String, id: String) {
    private val config = BridgeConfig(glas.trimEnd('/'), AudioTrial.token(id))
    private val http = Http.client(connectSeconds = 5, readSeconds = 15)

    /** POST /v1/trial: starts it, or reads the one started already. */
    suspend fun start(): TrialAnswer? = answer(config.request(PATH).post("{}".toRequestBody(Http.jsonType)))

    /** GET /v1/trial. */
    suspend fun read(): TrialAnswer? = answer(config.request(PATH))

    private suspend fun answer(req: okhttp3.Request.Builder): TrialAnswer? {
        val (code, raw) = http.exchange(req.build())
        return if (code in 200..299) TrialAnswer.parse(raw) else null
    }

    private companion object {
        const val PATH = "/v1/trial"
    }
}

/**
 * The trial on this device: SharedPreferences "audio-trial", read without waiting, since every voice request asks
 * ([AudioPlan.voices]) and a background job may be the first to. A debug build's QA also keeps its voice service here.
 */
class AudioTrialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("audio-trial", Context.MODE_PRIVATE)

    fun read(): AudioTrial = AudioTrial.decode(prefs.getString(TRIAL, null))

    fun write(t: AudioTrial) = prefs.edit().putString(TRIAL, t.encode()).apply()

    /** QA's hook "audio:trial:url:<url>": the voice service of a build with none. Read only in a debug build ([AudioPlan.glas]). */
    var qaGlas: String?
        get() = prefs.getString(QA_GLAS, null)
        set(value) = prefs.edit().putString(QA_GLAS, value).apply()

    private companion object {
        const val TRIAL = "trial"
        const val QA_GLAS = "qa_glas"
    }
}
