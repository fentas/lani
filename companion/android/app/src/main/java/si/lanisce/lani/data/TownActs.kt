package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.bi
import java.util.UUID
import kotlin.random.Random

// Things to do on a visit (plan 2, §3.2; companion/README.md, "Things to do on a visit"). The app asks its own bridge,
// which asks the host's: GET /towns/:id/requests (the host's open requests, each with words of its packs) and
// /towns/:id/market (the goods its chest can spare), POST /towns/:id/help, /gift and /trade (what the visitor did, each
// once by its id), POST /towns/:id/talk (a role-play with a host villager or its market seller, played by the
// learner's own tutor in the host's language). The host's app reads GET /towns/guests (what guests brought) and applies
// it to its village once (game/Guests.kt).

/** Goods (good ids of [culture]'s chest) and resources (Res names): one side of a trade. */
@Serializable
data class Bundle(val culture: String = "", val goods: Map<String, Int> = emptyMap(), val res: Map<String, Int> = emptyMap()) {
    val empty: Boolean get() = goods.values.none { it > 0 } && res.values.none { it > 0 }

    fun toJson(): JsonObject = buildJsonObject {
        if (culture.isNotBlank()) put("culture", culture)
        put("goods", buildJsonObject { goods.filterValues { it > 0 }.forEach { (k, v) -> put(k, v) } })
        put("res", buildJsonObject { res.filterValues { it > 0 }.forEach { (k, v) -> put(k, v) } })
    }
}

/** Who a guest is: their town's id, its village's name, its learner, culture and language. */
@Serializable
data class GuestFrom(val id: String = "", val name: String = "", val learner: String = "", val culture: String = "", val language: String = "")

@Serializable
data class GuestHelp(val request: String = "", val giver: String = "", val amount: Int = 0, val thanks: String? = null)

/** A good of a culture pack's chest: [culture]'s good [id]. */
@Serializable
data class GoodRef(val culture: String = "", val id: String = "")

@Serializable
data class GuestGift(val good: GoodRef? = null, val message: String? = null)

/** A trade at this town's market: what it [got] (the guest's culture's goods and resources) and [gave] (its own goods). */
@Serializable
data class GuestTrade(val got: Bundle = Bundle(), val gave: Bundle = Bundle(), val score: Int = 0)

/** Help a friend's town sent against this town's trouble: [event] (its id) of [kind] (WOLVES, BEAR, STORM). */
@Serializable
data class GuestAid(val event: String = "", val kind: String = "")

/**
 * What a linked town's learner brought this town, once ([key]): [help] (they did a request here), [gift] (a good and/or
 * a message: plain text from another person, shown as such), [trade] (at the market) or [aid] (help against trouble).
 */
@Serializable
data class GuestEntry(
    val key: String,
    val id: String = "",
    val kind: String = "",
    val from: GuestFrom = GuestFrom(),
    val at: String = "",
    val help: GuestHelp? = null,
    val gift: GuestGift? = null,
    val trade: GuestTrade? = null,
    val aid: GuestAid? = null,
)

/** A word to practise a host's request with: in the host's language ([word]), what it means per language, its emoji. */
@Serializable
data class RequestWord(val id: String, val word: String, val meaning: Map<String, String> = emptyMap(), val emoji: String? = null)

/** A host villager's request a visitor may take: who asks, the title and story per language, words to practise it. */
@Serializable
data class TownRequest(
    val id: String,
    val giver: String = "",
    val emoji: String = "🙂",
    val skill: String = "FOOD",
    val title: Map<String, String> = emptyMap(),
    val story: Map<String, String> = emptyMap(),
    val words: List<RequestWord> = emptyList(),
)

@Serializable
data class TownRequests(
    val town: String = "",
    val culture: String = "",
    val language: String = "",
    val requests: List<TownRequest> = emptyList(),
    @SerialName("fetched_at") val fetchedAt: String? = null,
    val stale: Boolean = false,
)

@Serializable
data class MarketSeller(val emoji: String = "🏪", val name: Map<String, String> = emptyMap())

/** A host's market: the goods its chest can spare (good id → how many), who sells, and its [rates] for this town (by the friendship). */
@Serializable
data class TownMarket(
    val town: String = "",
    val culture: String = "",
    val language: String = "",
    val wares: Map<String, Int> = emptyMap(),
    val seller: MarketSeller? = null,
    val rates: MarketRates = MarketRates(),
    @SerialName("fetched_at") val fetchedAt: String? = null,
    val stale: Boolean = false,
)

/** The host's thank-you good for a request done: [culture]'s good [good]. */
@Serializable
data class Thanks(val culture: String = "", val good: String = "")

/** The host's answer to a visit's write: done ([ok], its [key]); for help, the 🤝 it got and the giver's [thanks]. */
@Serializable
data class VisitAnswer(
    val ok: Boolean = false,
    val key: String = "",
    val kind: String = "",
    val amount: Int = 0,
    val giver: String = "",
    val thanks: Thanks? = null,
    val duplicate: Boolean = false,
)

/** What a visit's write came to. */
sealed interface VisitWrite {
    data class Done(val answer: VisitAnswer) : VisitWrite
    /** Why not, for the learner ("target · base"). */
    data class Refused(val problem: String) : VisitWrite
}

/** A guest's visit as the host's app announces it (a banner, a notification). */
object TownGuests {
    /** "Darilo od Jan · A gift from Jan", "Jan je bil na obisku · Jan came to visit". */
    fun title(ev: BridgeEvent.TownGuest): String {
        val who = ev.learner.ifBlank { ev.from }
        return when (ev.kind) {
            TownActs.GIFT -> bi("guests.giftFrom", "who" to who)
            TownActs.HELP, TownActs.AID -> bi("guests.helpFrom", "who" to who)
            TownActs.MOVE -> bi("friendship.moveNews", "who" to who)
            else -> bi("guests.tradeWith", "who" to who)
        }
    }
}

object TownActs {
    const val HELP = "help"
    const val GIFT = "gift"
    const val TRADE = "trade"
    /** Help against a friend's trouble (M5, data/Friendship.kt). */
    const val AID = "aid"
    /** A move between two towns: news of it (the town_guest event's kind). */
    const val MOVE = "move"
    /** A message left with a gift: one line, at most this long (the bridges cut it there too). */
    const val MAX_MESSAGE = 200
    /** The least score of a haggle's debrief that makes a deal (the bridge's DEAL_SCORE). */
    const val DEAL_SCORE = 6
    /** Words in a request's run. */
    const val WORDS = 6

    fun parseRequests(body: String): TownRequests = json.decodeFromString(TownRequests.serializer(), body)
    fun parseMarket(body: String): TownMarket = json.decodeFromString(TownMarket.serializer(), body)
    fun parseAnswer(body: String): VisitAnswer = json.decodeFromString(VisitAnswer.serializer(), body)

    /** GET /towns/guests: what guests brought, newest first; the ones that can't be read are left out. */
    fun parseGuests(body: String): List<GuestEntry> {
        val list = (json.parseToJsonElement(body) as? JsonObject)?.get("entries") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return list.mapNotNull { runCatching { json.decodeFromJsonElement(GuestEntry.serializer(), it) }.getOrNull() }
    }

    /** A text per language in [lang], else English, else its first. */
    fun textIn(t: Map<String, String>, lang: Lang): String = t[lang.code] ?: t[Lang.EN.code] ?: t.values.firstOrNull().orEmpty()

    /** A text per language as "target · base" in [pair]: "La cucina di Nonna Rosa · Nonna Rosa's kitchen". */
    fun label(t: Map<String, String>, pair: LangPair): String {
        val target = textIn(t, pair.target)
        val base = t[pair.base.code] ?: t[Lang.EN.code]
        return if (base == null || base == target) target else "$target · $base"
    }

    /** The host's answer to a write ([status], [body]) to town [town] (its name): done, or why not. */
    fun outcome(status: Int, body: String, town: String): VisitWrite {
        if (status == 200) {
            val a = runCatching { parseAnswer(body) }.getOrNull()
            return if (a?.ok == true) VisitWrite.Done(a) else VisitWrite.Refused("⚠️ ${bi("towns.answerNotUnderstood")}")
        }
        return VisitWrite.Refused(problem(status, Towns.errorCode(body), town))
    }

    /** Why a visit's write didn't go through, "target · base". */
    fun problem(status: Int, code: String?, town: String): String = when (code) {
        "not_open" -> "📜 ${bi("visit.requestNotOpen")}"
        "no_deal" -> "🤝 ${bi("visit.noDeal")}"
        "unfair" -> "⚖️ ${bi("visit.tooLittle")}"
        "not_for_sale" -> "🏪 ${bi("visit.notForSale")}"
        "rate_limited" -> "⏳ ${bi("visit.enoughToday")}"
        else -> Visits.problem(status, code, town)
    }

    /** A new id for a write; a retry of the same write sends it again. */
    fun newId(kind: String): String = "$kind-${UUID.randomUUID()}"

    /** A message as it may be sent: one line, printable, at most [MAX_MESSAGE] characters. */
    fun cleanMessage(s: String): String = s.replace(Regex("[\\u0000-\\u001f\\u007f<>]"), " ").replace(Regex("\\s+"), " ").trim().take(MAX_MESSAGE)

    /**
     * A request's run: up to [WORDS] of its words as choices, in turn "what does <word> mean?" (the meanings in the
     * learner's base) and "how do you say <meaning>?" (the words in the town's language), like a festival's. The same
     * for the same [seed].
     */
    fun exercises(r: TownRequest, pair: LangPair, seed: Long): List<Exercise> {
        val rnd = Random(seed xor r.id.hashCode().toLong())
        val meaningOf = { w: RequestWord -> w.meaning[pair.base.code] ?: w.meaning[Lang.EN.code] ?: w.meaning.values.firstOrNull().orEmpty() }
        val all = r.words.filter { it.word.isNotBlank() && meaningOf(it).isNotBlank() }.distinctBy { it.word }
        if (all.size < 2) return emptyList()
        val tag = "${r.emoji} ${r.giver}"
        return all.shuffled(rnd).take(WORDS).mapIndexed { i, w ->
            val others = all.filter { it != w }.shuffled(rnd).take(3)
            val explain = "${w.emoji?.let { "$it " }.orEmpty()}${w.word} · ${meaningOf(w)}"
            if (i % 2 == 0) {
                val opts = (others.map(meaningOf) + meaningOf(w)).distinct().shuffled(rnd)
                Exercise.Choice(bi("visit.whatMeans", "word" to w.word), opts, opts.indexOf(meaningOf(w)), explain = explain, instruction = tag)
            } else {
                val opts = (others.map { it.word } + w.word).distinct().shuffled(rnd)
                Exercise.Choice(bi("visit.howSay", "meaning" to meaningOf(w)), opts, opts.indexOf(w.word), explain = explain, instruction = tag, say = w.word)
            }
        }
    }

    /** Right answers a request's run needs: two in three. */
    fun passMark(total: Int): Int = (total * 2 + 2) / 3

    fun passed(correct: Int, total: Int): Boolean = total > 0 && correct >= passMark(total)

    /**
     * The market's haggle, from the tutor's debrief ({"debrief": true, "deal": true, "score": 0-10}): the score when
     * they agreed and it is [least] or more (the market's rate: [DEAL_SCORE], less between friends); null for no deal (none
     * agreed, too low a score, or a debrief without them).
     */
    fun deal(data: JsonObject?, least: Int = DEAL_SCORE): Int? {
        val deal = (data?.get("deal") as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "true") } == true
        val score = (data?.get("score") as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
        return score?.takeIf { deal && it in least..10 }
    }

    /**
     * `data` of the session_end a request done on a visit sends the learner's tutor: what was asked, in the town's
     * [language], so its words and mistakes go to the learner's profile for that language (plan 2, §3.4).
     */
    fun sessionData(r: TownRequest, town: String, townName: String, language: String, exercises: List<Exercise>, correct: List<Boolean>, minutes: Int): JsonObject = buildJsonObject {
        put("visit", buildJsonObject {
            put("kind", "request")
            put("town", buildJsonObject { put("id", town); put("name", townName) })
            put("language", language)
            put("request", r.id)
            put("giver", r.giver)
        })
        put("language", language)
        put("duration_minutes", minutes.coerceIn(1, 240))
        put("results", buildJsonArray {
            exercises.zip(correct).forEach { (e, ok) ->
                val c = e as? Exercise.Choice ?: return@forEach
                add(buildJsonObject {
                    put("prompt", c.prompt)
                    put("answer", c.options.getOrElse(c.answer) { "" })
                    put("explain", c.explain.orEmpty())
                    put("correct", ok)
                })
            }
        })
    }
}
