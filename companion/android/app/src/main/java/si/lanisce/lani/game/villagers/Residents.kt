package si.lanisce.lani.game.villagers

import kotlinx.serialization.Serializable
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.Gendered
import si.lanisce.lani.game.culture.PeopleFile
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.culture.localized
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Someone who lives in the village. The population ([GameState.villagers], grown by housing, food and
 * morale in the daily upkeep) is the list of residents: every head has a name. Cast members (curated or
 * published by the tutor) are looked up by [id]; people born here or who moved in carry who they are.
 */
@Serializable
data class Resident(
    val id: String,
    /** ISO date they moved in, or were born. */
    val since: String,
    // Generated people only (null for cast members):
    val name: String? = null,
    val emoji: String? = null,
    val art: String? = null,
    /** female | male */
    val voice: String? = null,
    /** "Drvar · Woodcutter" */
    val role: String? = null,
    /** ISO date of birth, for children born in the village. */
    val born: String? = null,
    /** The family name they share ("Furlan"); families have children. */
    val family: String? = null,
    val parents: List<String> = emptyList(),
    // Someone who moved in from a friend's town (plan 2, §3.3; game/TownFriendship): their culture, their language (they
    // speak it with the learner now and then), the town they came from.
    val culture: String? = null,
    val language: String? = null,
    val from: String? = null,
)

/** A cast member who doesn't live here yet, coming by on day [on] (ISO date). */
@Serializable
data class Visit(val id: String, val on: String)

/** Someone who would move in (or be born) next, and what they're waiting for, if anything. */
data class Arrival(val kind: Kind, val who: String, val emoji: String, val waitingFor: String? = null) {
    enum class Kind { CAST, BIRTH, NEWCOMER }
}

/**
 * Who lives in the village. Pure: [reconcile] matches the residents to the population after each day's
 * upkeep, choosing who arrives (the cast first, then births and newcomers) and who leaves (newcomers first).
 */
object Residents {
    /** The village never grows beyond this many named people. */
    const val MAX_RESIDENTS = 30
    /** A family has at most one child per this many days. */
    const val BIRTH_SPACING_DAYS = 21L

    /** How old a child born here is, by days since birth; what the culture calls them at that age ("Deklica · Girl"). */
    enum class Stage(val days: Long) {
        BABY(0),
        CHILD(45),
        YOUTH(240),
        ADULT(420);

        private fun named(): Gendered? = Cultures.current.people.stages.let { st ->
            when (this) {
                BABY -> st.baby
                CHILD -> st.child
                YOUTH -> st.youth
                ADULT -> null
            }
        }

        /** "Deček · Boy"; empty for a grown-up (their trade shows). */
        val label: String get() = named()?.male?.bi().orEmpty()
        val labelFemale: String get() = named()?.female?.bi().orEmpty()
    }

    /** The chance a day that someone who doesn't live here yet comes by. */
    const val VISIT_CHANCE = 0.6f

    /**
     * Who comes by on [today]: one of the cast who doesn't live here yet (up to the next age's people), the
     * ones who'll move in soonest most often, or nobody. The same all day (the day's dice).
     */
    fun visit(state: GameState, cast: List<Villager>, today: LocalDate): Visit? {
        if (cast.isEmpty()) return null
        val living = state.residents.map { it.id }.toSet()
        val next = Age.entries.getOrNull(state.age.ordinal + 1) ?: state.age
        val guests = cast.filter { it.id !in living && reached(it.since, next) && it.art != "baby" }
            .sortedWith(compareBy({ it.order }, { it.id }))
        if (guests.isEmpty()) return null
        if (si.lanisce.lani.game.scene.Happenings.roll(state.seed, today, "visit") >= VISIT_CHANCE) return null
        // weights n, n-1 … 1: the next to move in visits most often
        val weights = guests.indices.map { guests.size - it }
        var pick = si.lanisce.lani.game.scene.Happenings.roll(state.seed, today, "visitor") * weights.sum()
        for ((i, g) in guests.withIndex()) {
            pick -= weights[i]
            if (pick < 0f) return Visit(g.id, today.toString())
        }
        return Visit(guests.last().id, today.toString())
    }

    /** Today's visitor's id, if someone came by today. */
    fun visitorToday(state: GameState, today: LocalDate): String? =
        state.visitor?.takeIf { it.on == today.toString() && it.id.isNotEmpty() }?.id

    /**
     * Who is in the village today for the learner: the residents and today's visitor (their ids) whom the learner has met
     * ([Arrivals.met]: someone not met yet waits for their introduction, and hosts, asks and stands in a scene only after
     * it). Null while nobody has a name yet (an older bridge without the cast): then everyone counts as present.
     */
    fun present(state: GameState, today: LocalDate): Set<String>? =
        living(state, today)?.filterTo(LinkedHashSet()) { Arrivals.met(state, it) }

    /** Everyone in the village today, met or not: the residents and today's visitor; null as for [present]. */
    fun living(state: GameState, today: LocalDate): Set<String>? =
        if (state.residents.isEmpty()) null else state.residents.map { it.id }.toSet() + listOfNotNull(visitorToday(state, today))

    /**
     * Whether the person called [name] is in the village today: someone of the [cast] only while they live here
     * or visit today, and once the learner has met them; anyone else (a person the tutor made up, a stand-in) always;
     * everyone while nobody lives here yet (see [present]). [cast] may hold the residents born here or who moved in too.
     */
    fun here(state: GameState, name: String, cast: List<Villager>, today: LocalDate): Boolean {
        val ids = present(state, today) ?: return true
        val v = cast.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: return true
        return v.id in ids
    }

    /**
     * The open requests of the people in the village today (see [here]); a tutor's request waits for its giver to come, and
     * any request for everyone it names ([Mentions.inQuest]) to be here and met.
     */
    fun openQuests(state: GameState, cast: List<Villager>, today: LocalDate): List<Quest> {
        val present = present(state, today)
        val names = if (present == null || cast.isEmpty()) null else Mentions.names(Cultures.home.manifest.language, cast)
        return state.quests.filter { q ->
            !q.done && here(state, q.giver, cast, today) && (names == null || Mentions.known(Mentions.inQuest(q, names), present))
        }
    }

    /**
     * The [openQuests] as they show today ([si.lanisce.lani.game.QuestQueue]): at most two of each villager up (the map's
     * bubbles, their card, the Scroll's requests), the rest waiting in the Scroll's "Later".
     */
    fun queue(state: GameState, cast: List<Villager>, today: LocalDate): si.lanisce.lani.game.QuestQueue.Queue =
        si.lanisce.lani.game.QuestQueue.of(state.quests, openQuests(state, cast, today), today)

    fun stage(r: Resident, today: LocalDate): Stage? {
        val born = r.born?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val days = ChronoUnit.DAYS.between(born, today)
        return Stage.entries.last { days >= it.days }
    }

    /**
     * Matches [GameState.residents] to [GameState.villagers]: arrivals while there are fewer residents,
     * departures while there are more. A building that stands brings its person ([broughtBy]: the beehive its beekeeper)
     * as soon as a bed is free (the population below [si.lanisce.lani.game.Attributes.populationCap]): they come ahead
     * of their order and of the upkeep's growth, and the village grows by them ([GameState.villagers] + 1, within the
     * beds), so the residents stay the population. Returns the new state and chronicle lines (emoji to text). Does
     * nothing without a [cast] (an older bridge): the cast has to come first.
     */
    fun reconcile(state: GameState, cast: List<Villager>, today: LocalDate): Pair<GameState, List<Pair<String, String>>> {
        if (cast.isEmpty()) return state to emptyList()
        var residents = state.residents.filter { r -> r.name != null || cast.any { it.id == r.id } }
        val news = ArrayList<Pair<String, String>>()
        var target = state.villagers.coerceIn(0, MAX_RESIDENTS)
        // whom a standing building brought stays while anyone else can go: they'd come straight back with it
        val brought = cast.filter { v -> broughtBy(v, cast)?.let { b -> stands(state, b) } == true }.map { it.id }.toSet()
        while (residents.size > target) {
            // newcomers leave first, those who moved in from a friend's town last of them
            val leaving = residents.lastOrNull { it.name != null && it.born == null && it.culture == null }
                ?: residents.lastOrNull { it.name != null && it.born == null } ?: residents.lastOrNull { it.name != null }
                ?: residents.lastOrNull { it.id !in brought } ?: residents.last()
            residents = residents - leaving
            val she = (leaving.voice ?: cast.firstOrNull { it.id == leaving.id }?.voice) == "female"
            news += "🧳" to peopleFile.news.left.bi("name" to nameOf(leaving, cast), "g" to if (she) "f" else "m")
        }
        // a building brings its person as soon as a bed is free, ahead of the upkeep's growth: the village grows by them
        var grown = 0
        val beds = GameEngine.attributes(state).populationCap
        for (v in bringing(state.copy(residents = residents), cast)) {
            if (residents.size >= MAX_RESIDENTS || maxOf(residents.size, state.villagers + grown) >= beds) break
            residents = residents + Resident(v.id, today.toString())
            news += v.emoji to peopleFile.news.movedInCast.bi("name" to v.name, "g" to if (v.voice == "female") "f" else "m")
            grown++
        }
        target = (target + grown).coerceAtMost(MAX_RESIDENTS)
        var guard = 0
        while (residents.size < target && guard++ < MAX_RESIDENTS) {
            val s = state.copy(residents = residents)
            val next = arrive(s, cast, today) ?: break
            residents = residents + next.first
            news += next.second
        }
        return state.copy(residents = residents, villagers = state.villagers + grown) to news
    }

    /** Dwellings bring nobody: people live in them, and the housing counts them. */
    private val DWELLINGS = setOf(BuildingType.TENT, BuildingType.HUT, BuildingType.HOUSE)

    /**
     * The building that brings [v] into the village (companion/VILLAGERS.md, "A building brings its person"): their
     * workplace, the first of their `home` when it is a building other than a dwelling (tent, hut, house), for someone of
     * the cast who comes in a later age (a `since`: the founders come in their order from the first days, the field
     * brings nobody); of those who work there, the first by order (the market brings the winemaker, and the innkeeper
     * and her son come in their order). In Primorska: the beehive Čebelar Anton, the smithy Kovač Tone, the market Vinar
     * Marko, the school Učiteljica Mojca; every culture pack's cast has the same homes. Null for anyone else.
     */
    fun broughtBy(v: Villager, cast: List<Villager>): BuildingType? {
        val b = workplace(v) ?: return null
        val first = cast.withIndex().filter { (_, c) -> workplace(c) == b }.minWithOrNull(compareBy({ it.value.order }, { it.index }))?.value
        return b.takeIf { first?.id == v.id }
    }

    /** Who a building of [type] brings ([broughtBy]), if anyone. */
    fun brings(type: BuildingType, cast: List<Villager>): Villager? = cast.firstOrNull { broughtBy(it, cast) == type }

    private fun workplace(v: Villager): BuildingType? {
        if (v.extra || v.since == null) return null
        val h = v.home.firstOrNull()?.takeIf { !it.startsWith("spot:") } ?: return null
        return BuildingType.entries.firstOrNull { it.name.equals(h, ignoreCase = true) }?.takeIf { it !in DWELLINGS }
    }

    private fun stands(state: GameState, type: BuildingType): Boolean = state.buildings.any { it.type == type }

    /** The cast a standing building brings and who don't live here yet, their age reached, in their order. */
    private fun bringing(state: GameState, cast: List<Villager>): List<Villager> =
        cast.withIndex()
            .filter { (_, v) -> state.residents.none { it.id == v.id } && reached(v.since, state.age) && broughtBy(v, cast)?.let { stands(state, it) } == true }
            .sortedWith(compareBy({ it.value.order }, { it.index }))
            .map { it.value }

    /**
     * Who comes next, and what they're waiting for: first whom a standing building brings and who waits for a bed, then
     * whoever the next free room goes to (waiting for room when the houses are full), then cast members whose building
     * isn't built yet (the teacher waits for the school).
     */
    fun outlook(state: GameState, cast: List<Villager>, today: LocalDate): List<Arrival> {
        if (cast.isEmpty()) return emptyList()
        val full = state.villagers >= GameEngine.attributes(state).populationCap
        val room = if (full) bi("residents.room") else null
        val out = ArrayList<Arrival>()
        // whom a standing building brings comes first, as soon as a bed is free
        val brought = bringing(state, cast)
        for (v in brought) out += Arrival(Arrival.Kind.CAST, v.name, v.emoji, bi("residents.room"))
        val next = eligible(state, cast).firstOrNull { v -> homeReady(v, state) && brought.none { it.id == v.id } }
        val extra = nextExtra(state, cast)
        when {
            next != null -> out += Arrival(Arrival.Kind.CAST, next.name, next.emoji, room)
            birthFamily(state, today) != null -> out += Arrival(Arrival.Kind.BIRTH, birthFamily(state, today)!!, "🍼", room)
            state.residents.size < MAX_RESIDENTS && extra != null -> out += Arrival(Arrival.Kind.CAST, extra.name, extra.emoji, room)
            state.residents.size < MAX_RESIDENTS -> out += Arrival(Arrival.Kind.NEWCOMER, "", "🧳", room)
        }
        for ((v, b) in waitingFor(state, cast)) out += Arrival(Arrival.Kind.CAST, v.name, v.emoji, "${b.sl} · ${b.en}")
        return out
    }

    /** A cast member who could move in now: their age reached, their home existing (a spot always does). */
    private fun nextCast(state: GameState, cast: List<Villager>): Villager? =
        eligible(state, cast).firstOrNull { v -> homeReady(v, state) }

    /** Cast members whose age is reached but whose building isn't built yet, with that building. */
    private fun waitingFor(state: GameState, cast: List<Villager>): List<Pair<Villager, BuildingType>> =
        eligible(state, cast).mapNotNull { v ->
            if (homeReady(v, state)) null
            else v.home.firstNotNullOfOrNull { h -> BuildingType.entries.firstOrNull { it.name.equals(h, ignoreCase = true) } }?.let { v to it }
        }

    /** The cast (not the extras) who don't live here yet and whose age is reached, in their order. */
    private fun eligible(state: GameState, cast: List<Villager>): List<Villager> =
        cast.withIndex()
            .filter { (_, v) -> !v.extra && state.residents.none { it.id == v.id } && reached(v.since, state.age) }
            .sortedWith(compareBy({ it.value.order }, { it.index }))
            .map { it.value }

    /**
     * The extra ([Villager.extra]) who moves in instead of the next newcomer, if one is due: they take turns with the
     * names the village makes up (an extra when no more of them live here than newcomers who moved in), in their order,
     * once their age is reached and their home exists.
     */
    private fun nextExtra(state: GameState, cast: List<Villager>): Villager? {
        val extras = cast.filter { it.extra }
        if (extras.isEmpty()) return null
        val living = extras.count { v -> state.residents.any { it.id == v.id } }
        val newcomers = state.residents.count { it.name != null && it.born == null }
        if (living > newcomers) return null
        return extras.withIndex()
            .filter { (_, v) -> state.residents.none { it.id == v.id } && reached(v.since, state.age) && homeReady(v, state) }
            .minWithOrNull(compareBy({ it.value.order }, { it.index }))?.value
    }

    private fun homeReady(v: Villager, state: GameState): Boolean =
        v.home.isEmpty() || v.home.any { h ->
            h.startsWith("spot:") || state.buildings.any { it.type.name.equals(h, ignoreCase = true) }
        }

    /** Whether [age] is one someone who comes [since] (an age's name; null: from the start) lives in the village by. */
    internal fun reached(since: String?, age: Age): Boolean {
        val want = since?.let { n -> Age.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } } ?: return true
        return age.ordinal >= want.ordinal
    }

    /** The family (name) that can have a child now: two grown-ups of the family, none born too recently. */
    private fun birthFamily(state: GameState, today: LocalDate): String? {
        val grown = state.residents.filter { it.family != null && (it.born == null || stage(it, today) == Stage.ADULT) }
        return grown.groupBy { it.family!! }
            .filter { (_, members) -> members.size >= 2 }
            .keys
            .firstOrNull { f ->
                val lastBirth = state.residents.filter { it.family == f && it.born != null }.maxOfOrNull { it.born!! }
                lastBirth == null || ChronoUnit.DAYS.between(LocalDate.parse(lastBirth), today) >= BIRTH_SPACING_DAYS
            }
    }

    private fun arrive(state: GameState, cast: List<Villager>, today: LocalDate): Pair<Resident, Pair<String, String>>? {
        val day = today.toString()
        nextCast(state, cast)?.let { v ->
            val she = v.voice == "female"
            return Resident(v.id, day) to (v.emoji to peopleFile.news.movedInCast.bi("name" to v.name, "g" to if (she) "f" else "m"))
        }
        val rnd = kotlin.random.Random(state.seed xor today.toEpochDay() xor state.residents.size.toLong())
        val taken = (cast.map { it.name } + state.residents.mapNotNull { it.name }).toSet()
        birthFamily(state, today)?.let { family ->
            val female = rnd.nextBoolean()
            val first = pick(if (female) FEMALE else MALE, taken, rnd) ?: return@let
            val parents = state.residents.filter { it.family == family && it.born == null }.map { it.id }.take(2)
            val r = Resident(
                id = slug("$first-$family", state), since = day, name = "$first $family", emoji = "👶",
                art = "baby", voice = if (female) "female" else "male", role = peopleFile.newborn.bi(), born = day, family = family, parents = parents,
            )
            // "pri Furlanovih" in Slovene: the family's name as a place ([plural]); as it is elsewhere
            val at = localized { lang -> if (lang == Lang.SL) plural(family) else family }
            return r to ("🍼" to peopleFile.news.born.bi("first" to first, "family" to family, "family_at" to at, "g" to if (female) "f" else "m"))
        }
        if (state.residents.size >= MAX_RESIDENTS) return null
        // an extra of the culture's (the hunter), taking turns with the newcomers
        nextExtra(state, cast)?.let { v ->
            val she = v.voice == "female"
            return Resident(v.id, day) to (v.emoji to peopleFile.news.movedInCast.bi("name" to v.name, "g" to if (she) "f" else "m"))
        }
        // A newcomer: often the partner of someone living here alone, so families form (and have children).
        val single = state.residents.groupBy { it.family }
            .filter { (f, members) -> f != null && members.size == 1 && members[0].born == null && members[0].name != null }
            .values.firstOrNull()?.first()
        val partner = single != null && rnd.nextFloat() < 0.6f
        val female = if (partner) single!!.voice != "female" else rnd.nextBoolean()
        val first = pick(if (female) FEMALE else MALE, taken, rnd) ?: return null
        val family = if (partner) single!!.family!!
        else SURNAMES.filter { s -> state.residents.none { it.family == s } }.let { if (it.isEmpty()) SURNAMES else it }.random(rnd)
        val (trade, art) = trade(state, female, rnd)
        val role = trade.bi()
        val r = Resident(
            id = slug("$first-$family", state), since = day, name = "$first $family", emoji = if (female) "👩" else "👨",
            art = art, voice = if (female) "female" else "male", role = role, family = family,
        )
        // the trade mid-sentence: lower-case, but a German noun keeps its capital ("die Holzfällerin")
        return r to ("🧳" to peopleFile.news.movedIn.bi("name" to "$first $family", "role" to trade.mapIn { lang, t -> if (lang == "de") t else t.lowercase() }, "g" to if (female) "f" else "m"))
    }

    /** "pri Furlanovih" (at the Furlans'): -ič/-č/-š/-ž/-j/-c take -evih, -r takes -jevih, -e takes -tovih. */
    fun plural(family: String): String = when {
        family.last() in "čšžjc" -> "${family}evih"
        family.endsWith("r") -> "${family}jevih"
        family.endsWith("e") -> "${family}tovih"
        else -> "${family}ovih"
    }

    private fun slug(base: String, state: GameState): String {
        val ascii = base.lowercase().replace("č", "c").replace("š", "s").replace("ž", "z").replace(Regex("[^a-z0-9]+"), "-").trim('-')
        var id = "n-$ascii"
        var k = 2
        while (state.residents.any { it.id == id }) id = "n-$ascii-${k++}"
        return id
    }

    private fun pick(pool: List<String>, taken: Set<String>, rnd: kotlin.random.Random): String? =
        pool.filter { n -> taken.none { it == n || it.startsWith("$n ") || it.endsWith(" $n") } }.randomOrNull(rnd)

    /** A trade that fits the village's buildings (the culture's trades, people.json), and the sprite that goes with it. */
    private fun trade(state: GameState, female: Boolean, rnd: kotlin.random.Random): Pair<Text, String> {
        val options = peopleFile.trades.filter { t -> t.needs == null || state.buildings.any { it.type.name.equals(t.needs, ignoreCase = true) } }
            .map { if (female) it.female else it.male }
        return options.random(rnd) to (if (female) "woman" else "man")
    }

    /** The person behind a resident: the cast member, or one made from who they are, with plain lines for their age. */
    fun villagerOf(r: Resident, cast: List<Villager>, today: LocalDate): Villager? {
        cast.firstOrNull { it.id == r.id }?.let { return it }
        val name = r.name ?: return null
        val stage = stage(r, today)
        val young = stage == Stage.BABY || stage == Stage.CHILD || stage == Stage.YOUTH
        val art = when (stage) {
            Stage.BABY -> "baby"
            Stage.CHILD, Stage.YOUTH -> listOf("child1", "child2", "child3")[Math.floorMod(r.id.hashCode(), 3)]
            else -> r.art ?: "man"
        }
        return Villager(
            id = r.id, name = name, emoji = if (stage == Stage.BABY) "👶" else if (young) (if (r.voice == "female") "👧" else "👦") else (r.emoji ?: "🙂"),
            art = art, voice = r.voice ?: "female", speaker = speakerOf(r.voice ?: "female", stage),
            role = if (stage != null && stage != Stage.ADULT) (if (r.voice == "female") stage.labelFemale else stage.label) else (r.role ?: ""),
            home = listOf("house", "hut", "tent"), register = if (young) "ti" else "vi",
            story = r.from?.let { "Moved here on ${r.since} from ${it}, a friend's town (${r.culture}); speaks ${r.language} and the village's language." }
                ?: r.family?.let { peopleFile.story.of(Lang.EN, mapOf("family" to it, "since" to r.since)) } ?: "",
            // someone from a friend's town speaks their language now and then, with the learner's base translation
            lines = if (young) peopleFile.lines.child
            else if (r.culture != null && r.language != null && r.culture != Cultures.current.id) si.lanisce.lani.game.TownFriendship.bilingual(peopleFile.lines.adult, r.culture, r.language)
            else peopleFile.lines.adult,
            source = "village",
        )
    }

    /**
     * The voice-cast speaker of someone born here or who moved in, by gender and age (mirrored in the
     * bridge's newcomerSpeaker): children the girl and the boy, youths and grown-ups the young woman and
     * man. Without that speaker on the node, [si.lanisce.lani.data.Speaker.say] falls back to the gender.
     */
    fun speakerOf(voice: String, stage: Stage?): String {
        val female = voice == "female"
        return when (stage) {
            Stage.BABY, Stage.CHILD -> if (female) "girl" else "boy"
            else -> if (female) "young-woman" else "young-man"
        }
    }

    /** Everyone living here as villagers: the cast who live here, and the others. */
    fun people(state: GameState, cast: List<Villager>, today: LocalDate): List<Villager> =
        state.residents.mapNotNull { villagerOf(it, cast, today) }

    private fun nameOf(r: Resident, cast: List<Villager>): String = r.name ?: cast.firstOrNull { it.id == r.id }?.name ?: r.id

    // --- the culture's people (game/culture, people.json): names, trades, plain lines --------------------------------

    private val peopleFile: PeopleFile get() = Cultures.current.people
    private val FEMALE: List<String> get() = peopleFile.firstNames.female
    private val MALE: List<String> get() = peopleFile.firstNames.male
    /** Family names of the region (common in Primorska). */
    private val SURNAMES: List<String> get() = peopleFile.surnames
}
