package si.lanisce.lani.ui.villagers

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.villagers.Arrival
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.at
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

/**
 * A friendship change, for the result cards ("+5 ♥ Micka") and the level-up notice ("♥ Luka: Znanec ·
 * Acquaintance"). [before] and [after] are the bond's points; [memory] is what they keep of it.
 */
data class FriendGain(
    val id: String,
    val name: String,
    val emoji: String,
    val points: Int,
    val before: Int,
    val after: Int,
    val female: Boolean = false,
    val memory: Memory? = null,
    /** They hadn't met before this. */
    val firstMeeting: Boolean = false,
    /** What they gave Jan as the friendship reached a level: their tool, or a small good. */
    val gifts: List<si.lanisce.lani.game.Gift> = emptyList(),
    /** 🤝 earned with it (a talk with them). */
    val help: Int = 0,
) {
    val levelBefore: Int get() = Bonds.level(before)
    val level: Int get() = Bonds.level(after)
    val leveledUp: Boolean get() = level > levelBefore

    /** "Luka" of "Pastir Luka". */
    val short: String get() = VillagerLogic.shortName(name)

    /** "+5 ♥" */
    val plus: String get() = "+$points ♥"

    /** "♥ Luka: Znanec · Acquaintance", "♥ Micka: Znanka · Acquaintance" */
    val levelText: String get() = "♥ $short: ${VillagerLogic.levelName(level, female)}"
}

/** One line a villager says: Slovene and English. */
data class Said(val sl: String, val en: String)

/**
 * A villager in the register: their bond, whether they live here ([resident] is who they are in the
 * village), whether they need Jan now, and for those who don't live here yet [why] ("Pride, ko zgradiš
 * šolo · …"); [silhouette] for someone of a later age Jan hasn't met.
 */
data class RegisterEntry(
    val villager: Villager,
    val bond: Bond,
    val livesHere: Boolean,
    val needs: Boolean,
    val resident: Resident? = null,
    val why: String? = null,
    val silhouette: Boolean = false,
) {
    val met: Boolean get() = bond.met != null
}

/**
 * The register's sections: who lives here, one group per family (grown-ups first, then the children by
 * age) or single person, closest friends first; then the cast who haven't moved in yet.
 */
/**
 * The register: who lives here ([here], by family), today's visitor ([notYet]: only someone who is here today;
 * the rest of the cast is met as they come), and how many more will come over time ([toCome]).
 */
data class Register(val here: List<List<RegisterEntry>>, val notYet: List<RegisterEntry>, val toCome: Int = 0)

/**
 * The pure parts of the villagers' screens (companion/VILLAGERS.md): which lines they say at a friendship
 * level on a day, their memories in those lines, the register's order, and what a done quest leaves behind.
 */
object VillagerLogic {
    /** The generic remember line, for a villager whose cast entry has none. */
    private val REMEMBER = VillagerLine(
        mapOf("sl" to "Še vedno mislim na {memory}.", "it" to "Ricordo ancora {memory}.", "de" to "Ich denke noch oft an {memory}.", "en" to "I still think of {memory}."), level = 1,
    )

    /**
     * The greeting of someone who has none of their own for the time of day ([hello]): good day by day, good evening from
     * the evening on, never a wrong-time one.
     */
    private val HELLO = listOf(
        VillagerLine(mapOf("sl" to "Dober dan!", "it" to "Buongiorno!", "de" to "Guten Tag!", "en" to "Good day!"), times = setOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON)),
        VillagerLine(mapOf("sl" to "Dober večer!", "it" to "Buonasera!", "de" to "Guten Abend!", "en" to "Good evening!"), times = setOf(TimeOfDay.EVENING, TimeOfDay.NIGHT)),
    )

    /** A plain greeting for [time], for someone with none of their own that fits it: "Dober dan!", "Dober večer!". */
    fun hello(time: TimeOfDay): VillagerLine = HELLO.first { it.fits(time) }

    /** [Bonds.NAMES] for a woman: she is Jan's "znanka", "prijateljica". */
    val NAMES_F: List<String> get() = listOf(
        bi("villagerLogic.stranger"),
        bi("villagerLogic.acquaintance"),
        bi("villagerLogic.friend"),
        bi("villagerLogic.goodFriend"),
        bi("common.likeFamily"),
    )

    /** The name of friendship [level] with a villager, in their gender: "Znanec · Acquaintance", "Znanka · Acquaintance". */
    fun levelName(level: Int, female: Boolean): String = (if (female) NAMES_F else Bonds.NAMES)[level.coerceIn(0, Bonds.NAMES.lastIndex)]

    /** "12 / 30": points toward the next level, or null at the top. */
    fun toNext(points: Int): Pair<Int, Int>? {
        val l = Bonds.level(points)
        if (l >= Bonds.THRESHOLDS.lastIndex) return null
        return points to Bonds.THRESHOLDS[l + 1]
    }

    /** "24. 9." of "2026-09-24" (as it is when it isn't a date). */
    fun shortDate(iso: String): String = runCatching { LocalDate.parse(iso) }.getOrNull()?.let { "${it.dayOfMonth}. ${it.monthValue}." } ?: iso

    /** [shortDate] as a message argument: "24. 9." in Slovene, the day and month in each other message's language ("24. September"). */
    fun dateArg(iso: String): Any = runCatching { LocalDate.parse(iso) }.getOrNull()?.let { Dates.short(it) } ?: iso

    /** "24 September" of "2026-09-24". */
    fun enDate(iso: String): String = runCatching { LocalDate.parse(iso) }.getOrNull()
        ?.let { "${it.dayOfMonth} ${it.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)}" } ?: iso

    /** A person in a scene is villager [v]: by their `villager` id, else by name. */
    fun isVillager(p: si.lanisce.lani.game.scene.ScenePerson?, v: Villager): Boolean =
        p != null && (p.villager == v.id || (p.villager == null && p.name == v.name))

    /** "Luka" of "Pastir Luka", "Micka" of "Babica Micka": what friends call them. */
    fun shortName(name: String): String = name.trim().substringAfterLast(' ')

    fun female(v: Villager): Boolean = v.voice != Clips.MALE

    /** The node voice their lines are read in. */
    fun voiceOf(v: Villager): String = if (v.voice == Clips.MALE) Clips.MALE else Clips.FEMALE

    /** The age they come to the village in; null when they're there from the start (or it's unknown). */
    fun since(v: Villager): Age? = v.since?.let { s -> Age.entries.firstOrNull { it.name.equals(s.trim(), ignoreCase = true) } }

    /** The day's pick of [list]: the same all day, another tomorrow. [salt] keeps different lists apart. */
    fun <T> pick(list: List<T>, day: LocalDate, salt: String = ""): T? {
        if (list.isEmpty()) return null
        val h = (day.toEpochDay() * 31 + salt.hashCode()).let { it xor (it ushr 17) }
        return list[Math.floorMod(h, list.size.toLong()).toInt()]
    }

    /** The warmest lines of [pool] the [level] allows (the highest level among them), or none. */
    fun warmest(pool: List<VillagerLine>, level: Int): List<VillagerLine> {
        val open = Bonds.linesFor(pool, level)
        val top = open.firstOrNull()?.level ?: return emptyList()
        return open.filter { it.level == top }
    }

    /**
     * Their greeting for today at [time] (now): the warmest `greet` line of the level that fits the time of day (a "Dober
     * dan" by day, a "Dober večer" in the evening), or a plain one for the time ([hello]).
     */
    fun greet(v: Villager, level: Int, day: LocalDate, time: TimeOfDay = TimeOfDay.now()): Said {
        val line = pick(warmest(v.lines.greet.at(time), level), day, v.id + ":greet") ?: hello(time)
        return Said(line.target, line.base)
    }

    /**
     * The memory they bring up today: the newest when it's from today or yesterday (it's on their mind),
     * else one of them by the day.
     */
    fun memoryFor(memories: List<Memory>, day: LocalDate, salt: String = ""): Memory? {
        val newest = memories.lastOrNull() ?: return null
        val fresh = runCatching { LocalDate.parse(newest.on) }.getOrNull()?.let { !it.isBefore(day.minusDays(1)) } ?: false
        return if (fresh) newest else pick(memories, day, "$salt:memory")
    }

    /** [line] with the memory put in: its Slovene in the Slovene, its English (or Slovene) in the English. */
    fun fill(line: VillagerLine, m: Memory): Said =
        Said(line.target.replace("{memory}", m.sl), line.base.replace("{memory}", m.en.ifBlank { m.sl }))

    /** A `remember` line with one of their memories (one that fits [time]), or null: no memories, or no line open at [level]. */
    fun remember(v: Villager, level: Int, memories: List<Memory>, day: LocalDate, time: TimeOfDay = TimeOfDay.now()): Said? {
        val m = memoryFor(memories, day, v.id) ?: return null
        val pool = v.lines.remember.at(time).filter { "{memory}" in it.target }.ifEmpty { listOf(REMEMBER) }
        val line = pick(warmest(pool, level), day, v.id + ":remember") ?: return null
        return fill(line, m)
    }

    /** "Pogovori se": the greeting for the time of day, then a memory if they have one to share. */
    fun greeting(v: Villager, bond: Bond, day: LocalDate, time: TimeOfDay = TimeOfDay.now()): List<Said> {
        val level = Bonds.level(bond.points)
        return listOfNotNull(greet(v, level, day, time), remember(v, level, bond.memories, day, time))
    }

    /** Something they say on their card when nothing is going on: an `idle` line of the day that fits [time]; none when none does. */
    fun idle(v: Villager, level: Int, day: LocalDate, time: TimeOfDay = TimeOfDay.now()): Said? =
        pick(Bonds.linesFor(v.lines.idle.at(time), level), day, v.id + ":idle")?.let { Said(it.target, it.base) }

    // --- the register -----------------------------------------------------------------------

    /**
     * The register (companion/VILLAGERS.md, "Who lives here"): the residents, a family together, closest
     * friends and then those who need Jan ([needs]: their ids) first; then the cast who haven't moved in,
     * the ones Jan knows first, then by how soon they come.
     */
    fun register(cast: List<Villager>, state: GameState?, needs: Set<String>, today: LocalDate): Register {
        fun bond(id: String) = state?.bonds?.get(id) ?: Bond()
        val residents = state?.residents.orEmpty()
        val here = residents.mapIndexedNotNull { i, r ->
            Residents.villagerOf(r, cast, today)?.let { v -> i to RegisterEntry(v, bond(v.id), livesHere = true, needs = v.id in needs, resident = r) }
        }
        val units = here.groupBy { (_, e) -> e.resident?.family ?: "\u0000${e.villager.id}" }.values.map { members ->
            members.sortedWith(compareBy<Pair<Int, RegisterEntry>> { (_, e) -> grownUp(e.resident, today).not() }.thenBy { (_, e) -> e.resident?.born.orEmpty() }.thenBy { it.first })
        }.sortedWith(
            compareByDescending<List<Pair<Int, RegisterEntry>>> { u -> u.maxOf { it.second.bond.points } }
                .thenByDescending { u -> u.any { it.second.needs } }
                .thenBy { u -> u.minOf { it.first } },
        ).map { u -> u.map { it.second } }

        val living = residents.map { it.id }.toSet()
        // Only today's visitor of those who don't live here yet: the others are met as they come.
        val visitor = state?.let { Residents.visitorToday(it, today) }
        val toCome = cast.count { it.id !in living && it.id != visitor }
        val notYet = cast.withIndex().filter { it.value.id !in living && it.value.id == visitor }.map { (i, v) ->
            val w = whyNotHere(v, state)
            val b = bond(v.id)
            val known = b.met != null || v.id in needs
            i to RegisterEntry(v, b, livesHere = false, needs = v.id in needs, why = "🧳 ${bi("villagerLogic.visitingToday")}", silhouette = false)
        }.sortedWith(
            compareByDescending<Pair<Int, RegisterEntry>> { it.second.bond.points }
                .thenByDescending { it.second.needs }
                .thenBy { whyNotHere(it.second.villager, state).rank }
                .thenBy { since(it.second.villager)?.ordinal ?: 0 }
                .thenBy { it.second.villager.order }
                .thenBy { it.first },
        ).map { it.second }
        return Register(units, notYet, toCome)
    }

    /** Grown up: moved in (not born here), or born here long enough ago. */
    fun grownUp(r: Resident?, today: LocalDate): Boolean =
        r?.born == null || Residents.stage(r, today) == Residents.Stage.ADULT

    /** Why a cast member doesn't live here yet; [rank] orders them by how soon they come, [later] is another age. */
    data class Why(val text: String, val rank: Int, val later: Boolean = false)

    /**
     * Their age isn't reached ("Pride v dobi 🏘️ Vas"), their building isn't built ("Pride, ko zgradiš 🏫
     * šolo"), there's no room ("Čaka na prostor"), or the village just has to grow (food, good spirits).
     */
    fun whyNotHere(v: Villager, state: GameState?): Why {
        val age = since(v)
        if (state == null) return Why(bi("villagerLogic.doesntLiveHereYet"), 3)
        if (age != null && state.age < age) return Why(bi("villagerLogic.comesAgeenAge", "ageEmoji" to age.emoji, "ageSl" to age.names, "ageEn" to age.names), 3, later = true)
        val homes = v.home.map { h -> BuildingType.entries.firstOrNull { it.name.equals(h, ignoreCase = true) } }
        val ready = v.home.isEmpty() || v.home.any { it.startsWith("spot:") } || homes.any { t -> t != null && state.buildings.any { it.type == t } }
        if (!ready) homes.firstOrNull { it != null }?.let { t -> return Why(whenBuilt(t), 2) }
        if (state.villagers >= GameEngine.attributes(state).populationCap) return Why("🏠 ${bi("villagerLogic.waitingRoomBuildTent")}", 1)
        return Why("🌾 ${bi("villagerLogic.comingSoonVillageGrows")}", 0)
    }

    /** "Pride, ko zgradiš 🏫 šolo · Comes when you build the 🏫 school" (the building in the accusative). */
    fun whenBuilt(t: BuildingType): String {
        val (verb, what) = when (t) {
            BuildingType.TENT -> "zgradiš" to "šotor"
            BuildingType.FIELD -> "urediš" to "njivo"
            BuildingType.WELL -> "izkoplješ" to "vodnjak"
            BuildingType.HUT -> "zgradiš" to "kočo"
            BuildingType.KOZOLEC -> "postaviš" to "kozolec"
            BuildingType.BEEHIVE -> "postaviš" to "čebelnjak"
            BuildingType.PALISADE -> "postaviš" to "palisado"
            BuildingType.WATCHTOWER -> "zgradiš" to "stražni stolp"
            BuildingType.SMITHY -> "zgradiš" to "kovačnico"
            BuildingType.LIPA -> "posadiš" to "lipo"
            BuildingType.HOUSE -> "zgradiš" to "hišo"
            BuildingType.CHURCH -> "zgradiš" to "cerkev"
            BuildingType.SCHOOL -> "zgradiš" to "šolo"
            BuildingType.MARKET -> "zgradiš" to "tržnico"
        }
        val en = when (t) {
            BuildingType.FIELD -> "make"
            BuildingType.WELL -> "dig"
            BuildingType.LIPA -> "plant"
            else -> "build"
        }
        // the building: Slovene in the accusative after its verb, another language by its name ("sobald es 🏫 Schule gibt")
        val building = Localized { if (it == Lang.SL) what else t.names.of(it) }
        return bi("villagerLogic.comesWhenBuilt", "verb" to verb, "tEmoji" to t.emoji, "what" to building, "en" to en, "lowercase" to t.names.map { it.lowercase() })
    }

    /** "Furlanovi · The Furlan family" */
    fun familyName(family: String): String = bi("villagerLogic.family", "plural" to Residents.plural(family).removeSuffix("h"), "family" to family)

    /** "🍼 Rojena 24. 9. · Born on 24 September", for a child born in the village. */
    fun born(r: Resident?): String? = r?.born?.let { b ->
        "🍼 ${bi("villagerLogic.born", "g" to if (r.voice == "female") "f" else "m", "date" to dateArg(b), "enDate" to enDate(b))}"
    }

    /**
     * "Kdo pride · Who's coming", from [Residents.outlook]'s first arrival: who, and what they wait for (room:
     * a nudge to build housing). Null when no one is coming.
     */
    fun coming(outlook: List<Arrival>, cast: List<Villager>): String? {
        val a = outlook.firstOrNull() ?: return null
        val who = when (a.kind) {
            Arrival.Kind.CAST -> {
                val she = cast.firstOrNull { it.name == a.who }?.let(::female) ?: false
                "${a.emoji} ${bi("villagerLogic.movingInNext", "who" to a.who, "g" to if (she) "f" else "m")}"
            }
            Arrival.Kind.BIRTH -> "🍼 ${bi("villagerLogic.expectingBaby", "plural" to Residents.plural(a.who), "family" to a.who)}"
            Arrival.Kind.NEWCOMER -> "🧳 ${bi("villagerLogic.someoneNewWantsMove")}"
        }
        val wait = when (a.waitingFor) {
            null -> "🌾 ${bi("villagerLogic.villageGrowsEnoughFood")}"
            ROOM -> "🏠 ${bi("villagerLogic.noRoomBuildTent")}"
            else -> "🏗️ ${bi("villagerLogic.needs")}: ${a.waitingFor}"
        }
        return "$who\n$wait"
    }

    /** [Arrival.waitingFor] when the houses are full. */
    private val ROOM: String get() = bi("common.room")

    /** Ids of the villagers who need Jan: an open request of theirs ([Quest.giver] is their name), or [busy] (in a scene now). */
    fun needing(cast: List<Villager>, quests: List<Quest>, busy: Set<String> = emptySet()): Set<String> {
        val asking = quests.filter { !it.done }.map { it.giver }.toSet()
        return cast.filter { it.name in asking || it.id in busy }.map { it.id }.toSet()
    }

    /** "Še ga ne poznaš" / "Še je ne poznaš" (her): you haven't met yet. */
    fun notMet(v: Villager): String = bi("villagerLogic.notMetYet", "g" to gender(v))

    /** "f" or "m", for a message's {g, select, …}: the Slovene agreement ("rada", "je"). */
    fun gender(v: Villager): String = if (female(v)) "f" else "m"

    // --- friendship ---------------------------------------------------------------------------

    /** The friendship change between two village states, for villager [v] (or an unknown [id]), with the gifts it brought. */
    fun gain(id: String, v: Villager?, before: GameState?, after: GameState?, memory: Memory? = null): FriendGain? {
        after ?: return null
        val a = after.bonds[id]?.points ?: return null
        val b = before?.bonds?.get(id)?.points ?: 0
        return FriendGain(
            id = id, name = v?.name ?: id, emoji = v?.emoji ?: "🙂", points = a - b, before = b, after = a,
            female = v?.let(::female) ?: false, memory = memory, firstMeeting = before?.bonds?.get(id)?.met == null,
            gifts = before?.let { si.lanisce.lani.game.Chest.giftsBetween(it, after, id) }.orEmpty(),
        )
    }

    // --- gifts and goods (companion/GAME.md, "The chest") -----------------------------------------
    // A friend's gift at a new level isn't announced on their card or page, not even as "a surprise": it simply comes
    // (Jan). The friendship's ♥ and its next level show; what the level brings doesn't.

    /** The goods [id] likes best, as chips: "🥮 Potica" (the rare goods, which everyone likes, aside). */
    fun likedGoods(id: String): List<si.lanisce.lani.game.GoodSpec> = si.lanisce.lani.game.Chest.likes(id).filter { !it.rare }

    /**
     * What a villager keeps of a good Jan gave them, fitting "Še vedno mislim na {memory}": "potico od tebe" (or
     * "od vas" for those Jan says vi to).
     */
    fun giftMemory(g: si.lanisce.lani.game.GoodSpec, v: Villager?, day: LocalDate): Memory {
        // a culture pack that says how its people remember a gift ("la gubana che mi hai regalato")
        si.lanisce.lani.game.culture.Cultures.current.chest.lines.giftMemory?.let { m ->
            return Memory(day.toString(), m.inTarget("item" to g.name.theText), m.inBase("item" to g.name.theText), "gift")
        }
        val from = if (v?.register == "vi") "od vas" else "od tebe"
        return Memory(day.toString(), "${g.name.acc} $from", "the ${g.name.en.replaceFirstChar { it.lowercase() }.removePrefix("a ").removePrefix("an ")} from you", "gift")
    }

    /** "Rad ima" / "Rada ima" (she): what they like. */
    fun likes(v: Villager): String = if (female(v)) bi("villagerLogic.likesF") else bi("villagerLogic.likesM")

    // --- memories -------------------------------------------------------------------------------

    /**
     * What the giver keeps of a done quest, in their words and fitting "Še vedno mislim na {memory}": the
     * local quests' own memories (by their Slovene title), else "the day you helped me: <title>", in
     * their [Villager.register] (ti or vi).
     */
    fun questMemory(q: Quest, v: Villager?, day: LocalDate): Memory {
        val sl = titleSl(q.title)
        // the culture pack's own memory of the request (its template, by the title in the target language)
        val pack = si.lanisce.lani.game.culture.Cultures.current.quests
        pack.requests.firstOrNull { it.memory != null && it.title.target == sl }?.memory?.let {
            return Memory(day.toString(), it.target, it.base, "quest")
        }
        // the shepherd's request to move the tent to the pond
        pack.tentMove?.takeIf { q.id == si.lanisce.lani.game.TentMove.ID }?.memory?.let { return Memory(day.toString(), it.target, it.base, "quest") }
        QUEST_MEMORIES[sl]?.let { (msl, men) -> return Memory(day.toString(), si.lanisce.lani.l10n.Learner.said(msl), men, "quest") }
        val en = titleEn(q.title)
        // an Italian village remembers it in Italian (the villagers say tu to the learner, a child)
        if (si.lanisce.lani.l10n.L10n.pair.target == si.lanisce.lani.l10n.Lang.IT) {
            return Memory(day.toString(), "il giorno che mi hai aiutato: «$sl»", en.takeIf { it != sl }?.let { "the day you helped me: “$it”" } ?: "", "quest")
        }
        // … a German one in German, in the accusative its remember lines take after "an" (they say du to the learner too)
        if (si.lanisce.lani.l10n.L10n.pair.target == si.lanisce.lani.l10n.Lang.DE) {
            return Memory(day.toString(), "den Tag, an dem du mir geholfen hast: „$sl“", en.takeIf { it != sl }?.let { "the day you helped me: “$it”" } ?: "", "quest")
        }
        val helped = if (v?.register == "vi") "ko ste mi pomagali" else si.lanisce.lani.l10n.Learner.said("ko si mi {m:pomagal|f:pomagala}")
        return Memory(day.toString(), "dan, $helped: „$sl“", "the day you helped me: “$en”", "quest")
    }

    /** "Mickina kuhinja" of "Mickina kuhinja · Micka's kitchen". */
    fun titleSl(title: String): String = title.substringBefore(" · ").trim()

    /** "Micka's kitchen" of "Mickina kuhinja · Micka's kitchen" (the whole title when it has no English half). */
    fun titleEn(title: String): String = title.substringAfter(" · ", title).trim()

    /**
     * The local quests (game/Quests.kt) as the giver remembers them: accusative after "mislim na", said by
     * the giver ("sva" is the two of them, which suits both ti and vi; two women's is the feminine dual, {m:…|f:…}).
     */
    val QUEST_MEMORIES: Map<String, Pair<String, String>> = mapOf(
        "Mickina kuhinja" to ("potico, ki sva jo {m:spekla|f:spekli} skupaj" to "the potica we baked together"),
        "Nedeljsko kosilo" to ("nedeljsko kosilo z vso družino" to "Sunday lunch with the whole family"),
        "Gostje pri Micki" to ("goste iz Nemčije, ki sva jih lepo {m:pozdravila|f:pozdravili}" to "the guests from Germany we welcomed so nicely"),
        "Francetov seznam" to ("seznam za mlin, ki sva ga znova napisala" to "the list for the mill we wrote again"),
        "Žetev" to ("žetev, ko je bil kozolec poln snopov" to "the harvest, when the hayrack was full of sheaves"),
        "Antonov med" to ("med, ki sva ga točila skupaj" to "the honey we extracted together"),
        "Kranjska sivka" to ("kranjsko sivko, ki sva ji prisluhnila" to "the Carniolan bee we listened to"),
        "Lukovi klici" to ("klice čez dolino" to "the calls across the valley"),
        "Izgubljena ovca" to ("ovco, ki sva jo našla v gozdu" to "the sheep we found in the forest"),
        "Zvonci v megli" to ("zvonce v megli" to "the bells in the fog"),
        "Ančkin radio" to ("novice na Radiu Koper, ki sva jih {m:poslušala|f:poslušali}" to "the news on Radio Koper we listened to"),
        "Od kod so tujci?" to ("tujce, ki so prišli v vas" to "the strangers who came to the village"),
        "Tonetova naročila" to ("naročila iz Gorice" to "the orders from Gorizia"),
        "Podkev za konja" to ("podkev, ki sva jo skovala" to "the horseshoe we forged"),
        "Stari plug" to ("stari plug, ki sva ga popravila" to "the old plough we repaired"),
        "Mojčina ura" to ("uro o končnicah" to "the lesson on endings"),
        "Dvojina" to ("dvojino, ki sva jo {m:vadila|f:vadili}" to "the dual we practised"),
        "Napake na tabli" to ("napake na tabli, ki sva jih {m:popravila|f:popravili}" to "the mistakes on the blackboard we fixed"),
        "Janezove zgodbe" to ("zgodbe pod lipo, ki sva jih dokončala skupaj" to "the stories under the linden we finished together"),
        "Petkrat na Triglavu" to ("najin pogovor o Triglavu" to "our talk about Triglav"),
        "Vaška lipa" to ("pismo o vaški lipi" to "the letter about the village linden"),
        "Trgatev v Brdih" to ("trgatev v Brdih in kozarec rebule" to "the grape harvest in Brda and a glass of rebula"),
        "Pismo v Gorico" to ("pismo za kupca v Gorici" to "the letter for the customer in Gorizia"),
        "Vidina gostilna" to ("goste iz Nemčije v gostilni" to "the guests from Germany at the inn"),
        "Novi gostje" to ("nove goste v gostilni" to "the new guests at the inn"),
        "Prosim in hvala" to ("lepo besedo, ki je našla lepo mesto" to "the kind word that found a good place"),
    )

    /**
     * The tutor's `data.memory` at the end of a talk ({sl, en}, or just a Slovene string), as something
     * the villager remembers from [day]; null when there's none.
     */
    fun memoryOf(data: JsonObject?, day: LocalDate): Memory? {
        val m = data?.get("memory") ?: return null
        fun str(k: String) = ((m as? JsonObject)?.get(k) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        // in the village's language ({"sl", "en"} for Jan; {"it", "sl", "en"} in an Italian village), translated into the base
        val pair = si.lanisce.lani.l10n.L10n.pair
        val saidIn = if (str(pair.target.code) != null) pair.target.code else "sl"
        val said = (if (m is JsonPrimitive) m.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } else str(saidIn)) ?: return null
        val meant = pair.base.code.takeIf { it != saidIn }?.let(::str) ?: str("en")?.takeIf { saidIn != "en" }
        return Memory(day.toString(), said, meant.orEmpty(), "talk")
    }

    /** The message kind that tells the tutor someone new lives in the village. */
    const val ARRIVED = "villager_arrived"

    /**
     * `villager_arrived` for someone born here or who moved in: the chronicle's line as the text, and
     * data {id, name, role, family, kind: newcomer|birth|moved, art, voice, parents; culture, language, from for someone
     * of a friend's town}. Null for cast members.
     */
    fun arrival(r: Resident): Pair<String, JsonObject>? {
        val name = r.name ?: return null
        val birth = r.born != null
        val she = r.voice == "female"
        val first = name.substringBefore(' ')
        val text = if (birth) "Pri ${r.family?.let(Residents::plural) ?: "nas"} se je rodil${if (she) "a" else ""} $first · $first was born in the village"
        else "V vas se je priselil${if (she) "a" else ""} $name${r.role?.let { ", ${it.substringBefore(" · ").lowercase()}" }.orEmpty()} · $name moved into the village"
        val data = buildJsonObject {
            put("id", r.id)
            put("name", name)
            r.role?.let { put("role", it) }
            r.family?.let { put("family", it) }
            put("kind", if (birth) "birth" else if (r.culture != null) "moved" else "newcomer")
            // from a friend's town (game/TownFriendship): their culture and language, the town they came from
            r.culture?.let { put("culture", it) }
            r.language?.let { put("language", it) }
            r.from?.let { put("from", it) }
            r.art?.let { put("art", it) }
            r.voice?.let { put("voice", it) }
            if (r.parents.isNotEmpty()) put("parents", buildJsonArray { r.parents.forEach { add(it) } })
        }
        return text to data
    }

    /** "villager:luka" → "luka"; null for other role-plays. */
    fun villagerOfScenario(scenarioId: String): String? = scenarioId.removePrefix(SCENARIO_PREFIX).takeIf { scenarioId.startsWith(SCENARIO_PREFIX) && it.isNotBlank() }

    const val SCENARIO_PREFIX = "villager:"

    /**
     * A role-play with [v] built on the phone, for debug builds while the node can't build one
     * (`GET /villagers/:id/scenario`): their greeting opens it, and the goals are the contract's.
     */
    fun localScenario(v: Villager, bond: Bond, day: LocalDate, time: TimeOfDay = TimeOfDay.now()): Scenario {
        val hello = greet(v, Bonds.level(bond.points), day, time)
        val vi = v.register == "vi"
        return Scenario(
            id = SCENARIO_PREFIX + v.id,
            title = bi("villagerLogic.chatWith", "vName" to v.name, "vName2" to (shortName(v.name))),
            emoji = v.emoji,
            level = "A1",
            setting = v.story.ifBlank { bi("villagerLogic.village") },
            role = "${v.name}, ${v.personality}".trimEnd(',', ' '),
            voice = voiceOf(v),
            goals = listOf(
                if (vi) bi("villagerLogic.greetThemFormalVi") else bi("villagerLogic.greetThem"),
                bi("villagerLogic.askHowTheyAre", "vi" to if (vi) "yes" else "no"),
                bi("villagerLogic.talkAboutLikes", "g" to gender(v)),
                bi("villagerLogic.sayGoodbye"),
            ),
            openerSl = hello.sl,
            openerEn = hello.en,
            source = "debug",
        )
    }
}
