package si.lanisce.lani.game

import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.EventTexts
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

internal object Events {
    const val BASE_DEADLINE_HOURS = 36
    const val GRACE_DAYS = 2L
    const val FESTIVAL_MORALE = 60

    fun chance(age: Age): Float = 0.20f + 0.03f * age.ordinal

    fun questions(e: GameEvent) = 5 + e.strength

    /** Share of answers needed. Defence makes wolves and bears easier. */
    fun passRatio(s: GameState, kind: EventKind): Float = when (kind) {
        EventKind.WOLVES -> (0.75f - GameEngine.attributes(s).defence / 200f).coerceIn(0.4f, 0.8f)
        EventKind.BEAR -> (0.80f - GameEngine.attributes(s).defence / 200f).coerceIn(0.45f, 0.85f)
        EventKind.STORM, EventKind.MERCHANT -> 0.6f
        EventKind.FESTIVAL -> 0.5f
    }

    fun passMark(s: GameState, kind: EventKind, total: Int) = ceil(total * passRatio(s, kind)).toInt().coerceAtLeast(1)

    fun maybeSpawn(s: GameState, today: LocalDate, now: Long, pool: ContentPool): GameEvent? {
        if (s.event != null || s.age == Age.OGENJ) return null
        val founded = isoDay(s.foundedOn) ?: return null
        if (today < founded.plusDays(GRACE_DAYS)) return null
        val r = rng(s.seed, "event", today.toEpochDay())
        if (r.nextFloat() >= chance(s.age)) return null
        val weights = buildMap {
            put(EventKind.WOLVES, 4)
            if (s.age >= Age.ZASELEK) put(EventKind.BEAR, 2)
            if (pool.canSpeak) put(EventKind.STORM, 3)
            put(EventKind.MERCHANT, 2)
            if (s.morale >= FESTIVAL_MORALE) put(EventKind.FESTIVAL, 3)
        }
        var roll = r.nextInt(weights.values.sum())
        val kind = weights.entries.first { (_, w) -> (roll - w).also { roll = it } < 0 }.key
        val base = 1 + r.nextInt(s.age.ordinal + 1)
        val strength = (if (kind == EventKind.BEAR) base + 1 else base).coerceIn(1, 5)
        val hours = BASE_DEADLINE_HOURS + warningHours(s)
        return GameEvent("ev-$today", kind, strength, now, now + hours * HOUR_MS)
    }

    /** The culture's words for an event: its title, what the run is about, how it ends (game/culture, events.json). */
    fun texts(kind: EventKind): EventTexts = Cultures.current.events.let { e ->
        when (kind) {
            EventKind.WOLVES -> e.wolves
            EventKind.BEAR -> e.bear
            EventKind.STORM -> e.storm
            EventKind.MERCHANT -> e.merchant
            EventKind.FESTIVAL -> e.festival
        }
    }

    /** "🐺 Volkovi krožijo okoli tabora! · Wolves circle the camp!"; wolves at the palisade once there is one. */
    fun title(s: GameState, e: GameEvent): String {
        val t = texts(e.kind)
        val walled = e.kind == EventKind.WOLVES && s.buildings.any { it.type == BuildingType.PALISADE }
        val title = if (walled) t.titleWalled ?: t.title else t.title
        return "${e.kind.emoji} ${title.bi()}"
    }

    fun intro(s: GameState, e: GameEvent, total: Int, note: String?): String {
        val mark = passMark(s, e.kind, total)
        val what = texts(e.kind).intro.bi() + when (e.kind) {
            EventKind.WOLVES, EventKind.BEAR -> GameEngine.attributes(s).defence.let { d -> if (d > 0) " 🛡️ $d" else "" }
            else -> ""
        } + if (e.aid > 0) " 🤝 ${e.helpers.joinToString(", ").ifBlank { "×${e.aid}" }}" else ""
        // the clock is announced plainly on the intro ("⏱ Izziv na čas · A timed challenge: …", IntroInfo.event)
        val goal = "$mark/$total ✔"
        return listOfNotNull(what, goal, note).joinToString("\n")
    }

    /** Applies the outcome of [e]. [expired] = never played. */
    fun outcome(s0: GameState, e: GameEvent, won: Boolean, expired: Boolean, now: Long): Pair<GameState, ChallengeResult> {
        val r = rng(s0.seed, "outcome", e.id)
        val str = e.strength
        var s = s0.copy(event = null)
        var gain = emptyMap<Res, Int>()
        var loss = emptyMap<Res, Int>()
        var damaged = emptyList<String>()
        // help from friends' towns (plan 2, §3.3) lessens what a lost event takes: a building damaged less often, less lost
        val helped = TownFriendship.damageFactor(e.aid)
        fun damage(chance: Float) {
            if (s.age <= Age.TABOR || r.nextFloat() >= chance * helped) return
            val target = s.buildings.filterNot { it.damaged }.randomOrNull(r) ?: return
            s = s.copy(buildings = s.buildings.map { if (it.id == target.id) it.copy(damaged = true) else it })
            damaged = listOf(target.id)
        }
        fun share(res: Res, f: Float) = (s.res(res) * f * helped).roundToInt()

        val t = texts(e.kind)
        val message = e.kind.emoji + " " + when (e.kind) {
            EventKind.WOLVES, EventKind.BEAR -> {
                val m = if (e.kind == EventKind.BEAR) 1.5f else 1f
                if (won) {
                    gain = mapOf(Res.STONE to (8 * str * m).roundToInt(), Res.FOOD to (6 * str * m).roundToInt())
                    t.won.bi()
                } else {
                    val f = (0.06f + 0.03f * str) * m
                    loss = mapOf(Res.FOOD to share(Res.FOOD, f), Res.WOOD to share(Res.WOOD, f))
                    s = s.copy(morale = (s.morale - 5).coerceAtLeast(0))
                    damage(0.25f + 0.1f * str + if (e.kind == EventKind.BEAR) 0.2f else 0f)
                    t.lost.bi()
                }
            }
            EventKind.STORM -> if (won) {
                gain = mapOf(Res.WOOD to 12 * str)
                t.won.bi()
            } else {
                loss = mapOf(Res.WOOD to share(Res.WOOD, 0.1f + 0.03f * str))
                s = s.copy(fire = (s.fire - 20).coerceAtLeast(GameState.MIN_FIRE))
                damage(0.2f + 0.05f * str)
                t.lost.bi()
            }
            EventKind.MERCHANT -> if (won) {
                val caps = GameEngine.attributes(s).caps
                val give = Res.entries.maxBy { s.res(it) }
                val want = Res.entries.filter { it != give }.minBy { s.res(it).toFloat() / caps.getValue(it) }
                // Vida's scales: a better eye for a fair price
                val rate = (if (s.buildings.any { it.type == BuildingType.MARKET && !it.damaged }) 2.5f else 2f) * (1f + bonusEffect(s).trade)
                // Never pay for more than the storage can take: a full store would throw the rest away.
                val room = (caps.getValue(want) - s.res(want)).coerceAtLeast(0)
                val paid = minOf(s.res(give), 15 * str, ceil(room / rate).toInt())
                if (paid > 0) {
                    loss = mapOf(give to paid)
                    gain = mapOf(want to (paid * rate).roundToInt())
                    t.won.bi("paid" to paid, "give" to give.emoji, "got" to gain[want], "want" to want.emoji)
                } else {
                    gain = mapOf(want to 10 * str)
                    t.gift!!.bi()
                }
            } else t.lost.bi()
            EventKind.FESTIVAL -> when {
                expired -> {
                    s = s.copy(morale = (s.morale + 3).coerceAtMost(100))
                    t.quiet!!.bi()
                }
                won -> {
                    s = s.copy(morale = (s.morale + 15).coerceAtMost(100))
                    gain = mapOf(Res.FOOD to 10 * str, Res.WISDOM to 5 * str)
                    t.won.bi()
                }
                else -> {
                    s = s.copy(morale = (s.morale + 5).coerceAtMost(100))
                    gain = mapOf(Res.FOOD to 5 * str)
                    t.lost.bi()
                }
            }
        }
        val (afterLoss, lost) = debit(s, loss)
        val (afterGain, got) = credit(afterLoss, gain)
        val counted = e.kind != EventKind.FESTIVAL || won
        val stats = afterGain.stats.let {
            if (won) it.copy(eventsWon = it.eventsWon + 1) else if (counted) it.copy(eventsLost = it.eventsLost + 1) else it
        }
        val text = if (expired && e.kind != EventKind.FESTIVAL) "$message (⌛ ${Cultures.current.events.tooLate.bi()})" else message
        val done = afterGain.copy(stats = stats).logged(now, e.kind.emoji to text.removePrefix("${e.kind.emoji} "))
        return done to ChallengeResult(won, got, lost, damaged, text)
    }
}
