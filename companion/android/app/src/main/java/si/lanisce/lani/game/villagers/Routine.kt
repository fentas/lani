package si.lanisce.lani.game.villagers

import kotlinx.serialization.Serializable
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

// A day in the village (companion/VILLAGERS.md, "A day in the village"): when each villager gets up and goes to bed, what
// they do in between (their work, a rest, a chat, the children's games, the walk from one to the next), the evening by the
// fire or at home, and the few rituals that are the only reason to be out at night. One schedule for the map and the
// scenes: someone hoeing the field on the map is in the field's scene, someone asleep on the map lies in their bed.

/**
 * A villager file's `routine` (all optional): when they get up and go to bed ("5:00", "21:30"; "0:30" is after midnight),
 * and their night [rituals]. Without it their kind's ([Routine.Kind]).
 */
@Serializable
data class RoutineSpec(
    val up: String? = null,
    val bed: String? = null,
    val rituals: List<RitualSpec> = emptyList(),
)

/**
 * A night ritual (`routine.rituals[]`): from [at] to [until] ("21:25", "4:40") they go out with a lantern to [to] (a building
 * type in lower case, "fire", "forest" or "spot:<id>"), do [doing] there ("stand", "tend", "carry", "watch"), and go home.
 * [title] says what ("Luka pogleda ovce · Luka checks the sheep", or per language). One whose place isn't in the village
 * (the watchtower before it's built) doesn't happen.
 */
@Serializable
data class RitualSpec(
    val id: String,
    val at: String,
    val until: String,
    val to: String,
    val doing: String = "stand",
    @Serializable(with = Label::class) val title: String = "",
    val lantern: Boolean = true,
)

/** What someone is doing: the map draws it (see [si.lanisce.lani.game.render.TownPeople]). */
enum class Doing {
    /** Standing about: at home, looking out, holding the lantern up. */
    STAND,
    /** Working the soil: the field, the garden by the house. */
    HOE,
    /** At the anvil. */
    HAMMER,
    /** Fetching something (a night ritual's: Micka's wood for the stove); by day a load is an errand of the work ([Now.errand]). */
    CARRY,
    /** Bent over the work: the hives, the sheep, the forge's embers. */
    TEND,
    /** Washing at the stream. */
    WASH,
    /** Splitting logs. */
    CHOP,
    /** A rod in the water. */
    FISH,
    /** Sitting: under the linden, on the log, on the bench by the house. */
    SIT,
    /** Talking with whoever is there. */
    CHAT,
    /** Children playing. */
    PLAY,
    /** Sitting by the fire. */
    WARM,
    /** Looking out: from the tower, the high seat, the road. */
    WATCH,
    /** Waiting for the learner (a request, a happening): a wave now and then. */
    WAVE,
    /** Sweeping in front of their door. */
    SWEEP,
    /** Hanging the washing on the line by the house. */
    HANG,
    /** Beating a rug hung over the line by the house. */
    BEAT,
    /** Feeding the hens by the house (a handful of grain), or the sheep in the meadow. */
    FEED,
    /** Picking berries or mushrooms at the forest's edge, a basket beside them (in their season). */
    GATHER,
    /** Sitting and mending something: a basket, a net, a shoe; the teacher's copybooks. */
    MEND,
    /**
     * In the house: lunch at the table, the housework. In their home's scene; on the map they go in at the door and aren't
     * drawn (the village has no more room outside it, or it's lunchtime).
     */
    INSIDE,
    ;

    companion object {
        /** A file's word for it ("tend"); an unknown one is [STAND]. */
        fun of(s: String): Doing = entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: STAND
    }
}

/** Where someone is now ([Routine.now]). */
enum class Where {
    /** Out in the village at [Now.place] (on the way there from [Now.from]). */
    OUT,
    /** On the way home from [Now.from] to [Now.place], to go in: the map hides them once they're there. */
    HOMEWARD,
    /** At home, awake: the dark evening, the dark morning. Not on the map; their window is lit. */
    INDOORS,
    /** In bed. Not on the map; in their bed in their home's scene ([si.lanisce.lani.game.scene.Sleep]). */
    ASLEEP,
}

/**
 * Where someone is at a moment, and what they do: [where]; [place] where they are or go ([Where.OUT], [Where.HOMEWARD]: home);
 * [doing]; [from] where they set off from [since] seconds ago (null: they've been there [since] seconds); [errand] a trip away from
 * their work at [place] they are on (fetching water, taking the hay to the kozolec), [Trip.since] seconds into it; [before]
 * what they did at this place until [begun] seconds ago (another stretch there: Luka with the sheep, then on the fallen
 * log); [lantern] a lit lantern in the hand; [ritual] the night ritual they're on; [left] how many seconds more they do it
 * there (until the stretch is over, an errand of it begins or it gets dark; the children ease out of a game by then).
 */
data class Now(
    val where: Where,
    val place: TownPlace? = null,
    val doing: Doing = Doing.STAND,
    val from: TownPlace? = null,
    val since: Double = 0.0,
    val errand: Trip? = null,
    val lantern: Boolean = false,
    val ritual: Routine.Ritual? = null,
    val before: Doing? = null,
    val begun: Double = 0.0,
    val left: Double = Double.POSITIVE_INFINITY,
) {
    /** Up and on the map (out, or still on the way home). */
    val shown: Boolean get() = where == Where.OUT || where == Where.HOMEWARD
}

/**
 * An errand under way: from the work, to [from] to load (if it isn't the work's place), the load to [to], and back to the
 * work, [since] seconds into the [Routine.ERRAND] minutes it takes.
 */
data class Trip(val from: TownPlace, val to: TownPlace, val since: Double)

/**
 * An errand within a stretch of work: at [at] (minutes of their day) they fetch a load from [from] and take it to [to] (the
 * well's water home, the field's hay to the kozolec), and are back at the work [Routine.ERRAND] minutes later.
 */
data class Errand(val at: Int, val from: TownPlace, val to: TownPlace)

/**
 * A stretch of someone's day: from [start] to [end] (minutes of their day, see [Routine.plan]) at [place], doing [doing],
 * with an errand or two within it ([errands]).
 */
data class Stint(val start: Int, val end: Int, val place: TownPlace, val doing: Doing, val errands: List<Errand> = emptyList())

object Routine {
    /** When the storyteller goes home at the latest when the learner hasn't heard tonight's story (minutes: 23:30). */
    const val STORY_UNTIL = 23 * 60 + 30

    /** How long going home, to the fire or out on a ritual takes at most (minutes of the village's clock); the map stops at arrival. */
    const val TRANSIT = 3

    /** A window stays lit this long after the last one in the house went to bed (minutes). */
    const val LIGHTS_OUT = 20

    /**
     * How long a walk from one place to the next takes at most (s of the clock): the map's walkers hurry on a long way
     * ([si.lanisce.lani.game.render.TownPeople]), and the scenes have them there from then on ([si.lanisce.lani.game.scene.Whereabouts]).
     */
    const val WALK_S = 45.0

    /** How long an errand takes, there and back (minutes): the map's carrier fits the walks, the loading and a word in. */
    const val ERRAND = 3

    /**
     * Who someone is, for their day: when they get up and go to bed (minutes after midnight), from their sprite, their
     * role as the village's storyteller, and their age: children early, grandparents early too, the young later, workers as
     * their work wants (the grandmother bakes, the shepherd and the farmer are up at dawn, the hunter before it); the
     * storyteller last of all.
     */
    enum class Kind(val up: Int, val bed: Int) {
        CHILD(7 * 60, 20 * 60),
        GRANDMA(5 * 60, 21 * 60),
        GRANDPA(6 * 60, 21 * 60 + 15),
        STORYTELLER(6 * 60 + 30, 22 * 60 + 45),
        AUNT(6 * 60 + 30, 21 * 60 + 30),
        SHEPHERD(5 * 60, 21 * 60 + 45),
        FARMER(5 * 60, 21 * 60),
        SMITH(6 * 60, 22 * 60),
        BEEKEEPER(5 * 60 + 30, 21 * 60 + 30),
        WINEMAKER(6 * 60 + 30, 22 * 60 + 30),
        INNKEEPER(6 * 60, 22 * 60 + 30),
        TEACHER(6 * 60 + 30, 22 * 60),
        HUNTER(4 * 60 + 30, 21 * 60),
        ADULT(6 * 60, 22 * 60),
    }

    /** A night ritual, its times in minutes of the villager's day (after [Plan.up]; before it: the night's, + 1440). */
    data class Ritual(val id: String, val at: Int, val until: Int, val to: TownPlace, val doing: Doing, val title: String, val lantern: Boolean)

    /**
     * Someone's day: their [kind], when they get [up] and go to [bed] (minutes of their day: from [up], so a bedtime after
     * midnight is past 1440), and their [rituals] (in the same minutes).
     */
    data class Plan(val kind: Kind, val up: Int, val bed: Int, val rituals: List<Ritual>)

    /**
     * What the day needs besides the village: where each sleeps ([homes], villager id → their house, hut or tent, from
     * [si.lanisce.lani.game.scene.Sleep.bedrooms]; else [TownMarkers.homeOf]), the storytellers and their story's
     * happening ([tellers], "janez" → "ob-ognju/zgodba"), when tonight's story was heard to its end ([storyAt], the app
     * keeps it while it runs), who waits for the learner and where ([calls]: a request, someone to meet, today's visitor),
     * who has a happening on and where ([busy], [TownMarkers.standing]), and whether a festival is on. [people]: who lives
     * here (and today's visitor), whose days are shared out together ([village]: no more than a few at one place, a handful
     * round the fire in the evening); empty, each has their own day alone.
     */
    data class Day(
        val homes: Map<String, TownPlace> = emptyMap(),
        val tellers: Map<String, String> = emptyMap(),
        val storyAt: LocalDateTime? = null,
        val calls: Map<String, TownPlace> = emptyMap(),
        val busy: Map<String, TownPlace> = emptyMap(),
        val festival: Boolean = false,
        val people: List<Villager> = emptyList(),
    )

    private val CHILD_ARTS = setOf("child1", "child2", "child3")

    /** [v]'s kind: the storyteller ([teller]), a child, and the rest by their sprite. */
    fun kindOf(v: Villager, teller: Boolean = false): Kind = when {
        teller -> Kind.STORYTELLER
        v.art in CHILD_ARTS -> Kind.CHILD
        else -> when (v.art) {
            "grandma" -> Kind.GRANDMA
            "grandpa" -> Kind.GRANDPA
            "aunt" -> Kind.AUNT
            "shepherd" -> Kind.SHEPHERD
            "farmer" -> Kind.FARMER
            "smith" -> Kind.SMITH
            "beekeeper" -> Kind.BEEKEEPER
            "winemaker" -> Kind.WINEMAKER
            "innkeeper" -> Kind.INNKEEPER
            "teacher" -> Kind.TEACHER
            "hunter" -> Kind.HUNTER
            else -> Kind.ADULT
        }
    }

    private val CLOCK = Regex("""^(\d{1,2}):(\d{2})$""")

    /** "21:30" → 1290, "0:30" → 30; null for anything else. */
    fun clock(s: String?): Int? {
        val m = s?.trim()?.let { CLOCK.matchEntire(it) } ?: return null
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    /**
     * [v]'s plan: their kind's hours, a few minutes later or earlier by their id so they don't all go in at the same
     * minute (never past the storyteller), or their file's ([Villager.routine]); their rituals. Every time is in minutes
     * of their day: from when they get up, a time before it is the night after (+ 1440).
     */
    fun plan(v: Villager, teller: Boolean = false): Plan = synchronized(plans) {
        plans.getOrPut(PlanKey(v, teller)) { planOf(v, teller) }
    }

    /** The plans worked out, by the villager (the same object: the cast's) and whether they tell the stories; at most a few hundred. */
    private val plans = object : LinkedHashMap<PlanKey, Plan>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PlanKey, Plan>?) = size > 256
    }

    private class PlanKey(val v: Villager, val teller: Boolean) {
        override fun equals(other: Any?) = other is PlanKey && other.v === v && other.teller == teller
        override fun hashCode() = System.identityHashCode(v) * 31 + if (teller) 1 else 0
    }

    private fun planOf(v: Villager, teller: Boolean): Plan {
        val kind = kindOf(v, teller)
        val spec = v.routine
        val jitter = if (kind == Kind.STORYTELLER) 0 else (Math.floorMod(v.id.hashCode(), 5) - 2) * 5 + if (kind == Kind.ADULT) Math.floorMod(v.id.hashCode() / 7, 4) * 10 else 0
        val up = clock(spec?.up) ?: kind.up
        val norm = { t: Int -> if (t < up) t + 1440 else t }
        val bed = clock(spec?.bed)?.let(norm) ?: minOf(norm(kind.bed + jitter), if (teller) Int.MAX_VALUE else norm(Kind.STORYTELLER.bed - 15))
        val rituals = spec?.rituals.orEmpty().mapNotNull { r ->
            val at = clock(r.at)?.let(norm) ?: return@mapNotNull null
            var until = clock(r.until)?.let(norm) ?: return@mapNotNull null
            if (until <= at) until += 1440
            // home again before the day begins (the walk back fits before they're up)
            until = minOf(until, up + 1440 - TRANSIT)
            if (until - at > 180 || until <= at) return@mapNotNull null
            Ritual(r.id, at, until, TownPlace.of(r.to), Doing.of(r.doing), r.title, r.lantern)
        }
        return Plan(kind, up, bed, rituals)
    }

    // ------------------------------------------------------------------ light and dark

    private val bounds = arrayOfNulls<IntArray>(13)

    /** The minute the morning gets light and the minute the evening gets dark in [month] (the map's night: [Env.dark] past 0.55). */
    fun light(month: Int): IntArray {
        val m = month.coerceIn(1, 12)
        bounds[m]?.let { return it }
        fun dark(min: Int) = Env(min / 60f, m, false).dark > 0.55f
        val morning = (3 * 60 until 12 * 60).firstOrNull { !dark(it) } ?: 6 * 60
        val evening = (12 * 60 until 24 * 60).firstOrNull { dark(it) } ?: 21 * 60
        return intArrayOf(morning, evening).also { bounds[m] = it }
    }

    /** Whether it is dark at [minute] of the clock (0 until 1440) in [month]. */
    fun dark(month: Int, minute: Double): Boolean {
        val (morning, evening) = light(month).let { it[0] to it[1] }
        val m = ((minute % 1440) + 1440) % 1440
        return m < morning || m >= evening
    }

    // ------------------------------------------------------------------ the day

    private const val MORNING = 1; private const val AFTERNOON = 2; private const val EVENING = 4
    private const val ALL = MORNING or AFTERNOON or EVENING
    private const val DAY = MORNING or AFTERNOON

    /**
     * Something to do: at [place] ("home", "neighbour": another household's door, "water": the well, else the stream,
     * "fire", "forest", a building type, "spot:<id>", "project:<id>"), how likely, when, an [errand] within it now and then
     * (where the load comes from and where it goes: "water" to "home"), what the village [needs] for it besides the place
     * (the finished mill), and in which [months] only (the berries and the mushrooms).
     */
    private class Option(
        val place: String, val doing: Doing, val weight: Float, val parts: Int, val errand: Pair<String, String>? = null,
        val min: Int = 40, val max: Int = 100, val needs: String? = null, val months: IntRange? = null,
    )

    private fun o(
        place: String, doing: Doing, weight: Float, parts: Int, errand: Pair<String, String>? = null, min: Int = 40, max: Int = 100,
        needs: String? = null, months: IntRange? = null,
    ) = Option(place, doing, weight, parts, errand, min, max, needs, months)

    /** When the forest's edge has something to pick: wild strawberries and bilberries from June, mushrooms until October. */
    private val PICKING = 6..10

    /**
     * What each kind does in the day, where; a place the village doesn't have is left out. Their work first, then the
     * chores of a household that every village has (companion/VILLAGERS.md, "A day in the village"): the garden, the
     * washing on the line and a rug to beat, sweeping in front of the door, the hens, water and wood fetched home, a
     * basket taken to a neighbour, a word at a neighbour's door, mending on the bench, berries and mushrooms at the
     * forest's edge in their season. A load to fetch is an errand of a stretch of work (the grandmother's water for her
     * garden, the farmer's hay to the kozolec), not a stretch of its own.
     */
    private fun options(kind: Kind): List<Option> = when (kind) {
        Kind.GRANDMA -> listOf(
            o("home", Doing.HOE, 1.0f, DAY), o("home", Doing.HOE, 0.6f, DAY, errand = "water" to "home", max = 90),
            o("home", Doing.SIT, 0.5f, DAY, errand = "spot:woodpile" to "home", max = 60), o("home", Doing.FEED, 0.6f, DAY, min = 30, max = 60),
            o("home", Doing.SWEEP, 0.4f, MORNING, min = 30, max = 50), o("home", Doing.HANG, 0.4f, MORNING, min = 30, max = 60),
            o("home", Doing.MEND, 0.6f, AFTERNOON or EVENING), o("home", Doing.MEND, 0.3f, AFTERNOON, errand = "home" to "neighbour", max = 70),
            o("spot:riverbank", Doing.WASH, 0.5f, AFTERNOON), o("market", Doing.CHAT, 0.5f, MORNING), o("well", Doing.CHAT, 0.5f, AFTERNOON or EVENING, max = 60),
            o("neighbour", Doing.CHAT, 0.4f, DAY, min = 30, max = 60), o("forest", Doing.GATHER, 0.4f, DAY, months = PICKING),
            o("home", Doing.SIT, 0.8f, EVENING), o("lipa", Doing.SIT, 0.4f, AFTERNOON or EVENING), o("fire", Doing.WARM, 0.5f, EVENING),
            // the village's places once built: fresh flowers at the wayside chapel, a seat in the bee meadow
            o("project:kapelica", Doing.STAND, 0.3f, MORNING, min = 30, max = 50), o("project:cebelji_travnik", Doing.SIT, 0.3f, AFTERNOON, min = 30, max = 60),
        )
        Kind.GRANDPA -> listOf(
            o("lipa", Doing.SIT, 1.0f, ALL), o("spot:pond", Doing.FISH, 0.7f, DAY), o("spot:woodpile", Doing.CHOP, 0.4f, MORNING),
            o("home", Doing.MEND, 0.6f, DAY), o("home", Doing.SIT, 0.6f, AFTERNOON or EVENING), o("well", Doing.CHAT, 0.5f, AFTERNOON or EVENING),
            o("neighbour", Doing.CHAT, 0.3f, DAY, min = 30, max = 60), o("forest", Doing.GATHER, 0.3f, DAY, months = 8..10), o("fire", Doing.WARM, 0.4f, EVENING),
            // a game of bocce in the afternoon once the court is built; on the bench under the town clock
            o("project:balinisce", Doing.CHAT, 0.9f, AFTERNOON, max = 90), o("project:mestna_ura", Doing.SIT, 0.3f, MORNING or AFTERNOON, max = 50),
        )
        Kind.STORYTELLER -> listOf(
            o("lipa", Doing.SIT, 1.3f, ALL), o("spot:pond", Doing.FISH, 0.6f, DAY), o("spot:highseat", Doing.WATCH, 0.3f, MORNING, max = 60),
            o("well", Doing.CHAT, 0.5f, AFTERNOON), o("school", Doing.STAND, 0.3f, AFTERNOON, max = 50), o("home", Doing.SIT, 0.4f, AFTERNOON),
            o("home", Doing.MEND, 0.5f, DAY), o("neighbour", Doing.CHAT, 0.3f, DAY, min = 30, max = 60), o("forest", Doing.GATHER, 0.3f, DAY, months = 8..10),
            o("fire", Doing.WARM, 0.8f, EVENING),
            // his afternoons at the bocce court, raking its sand in the morning (Na balinišču); the lookout's hundred steps
            o("project:balinisce", Doing.CHAT, 0.9f, AFTERNOON, max = 90), o("project:balinisce", Doing.SWEEP, 0.2f, MORNING, min = 30, max = 45),
            o("project:razgledni_stolp", Doing.WATCH, 0.2f, AFTERNOON, max = 45),
        )
        Kind.AUNT -> listOf(
            o("home", Doing.SIT, 0.5f, ALL), o("home", Doing.HANG, 0.7f, MORNING, min = 30, max = 60), o("home", Doing.BEAT, 0.4f, DAY, min = 30, max = 50),
            o("home", Doing.SWEEP, 0.4f, MORNING, min = 30, max = 50), o("home", Doing.MEND, 0.5f, AFTERNOON), o("home", Doing.FEED, 0.3f, DAY, min = 30, max = 50),
            o("well", Doing.CHAT, 0.8f, DAY), o("neighbour", Doing.CHAT, 0.6f, DAY, min = 30, max = 60), o("spot:road", Doing.WATCH, 0.4f, DAY, max = 60),
            o("market", Doing.CHAT, 0.6f, MORNING), o("church", Doing.STAND, 0.3f, MORNING, max = 50), o("spot:pond", Doing.FISH, 0.2f, AFTERNOON),
            o("forest", Doing.GATHER, 0.4f, DAY, months = PICKING), o("fire", Doing.WARM, 0.4f, EVENING),
            // the wayside chapel's flowers in the morning and its candles at dusk; a word under the town clock
            o("project:kapelica", Doing.STAND, 0.5f, MORNING, min = 30, max = 50), o("project:kapelica", Doing.STAND, 0.5f, EVENING, min = 30, max = 50),
            o("project:mestna_ura", Doing.CHAT, 0.3f, DAY, min = 30, max = 50),
        )
        Kind.SHEPHERD -> listOf(
            o("spot:meadow", Doing.TEND, 2.5f, DAY, max = 120), o("spot:meadow", Doing.FEED, 0.5f, DAY, min = 30, max = 60),
            o("spot:meadow", Doing.SIT, 0.6f, AFTERNOON or EVENING), o("spot:pond", Doing.FISH, 0.3f, AFTERNOON),
            o("spot:woodpile", Doing.CHOP, 0.4f, MORNING), o("forest", Doing.GATHER, 0.3f, DAY, months = PICKING), o("fire", Doing.WARM, 0.6f, EVENING),
            // the flock to the water over the cart bridge; the fire brigade's pump drill on a summer afternoon
            o("project:most", Doing.TEND, 0.4f, MORNING or AFTERNOON, max = 50), o("project:gasilski_dom", Doing.TEND, 0.3f, AFTERNOON, max = 50, months = 4..10),
        )
        Kind.FARMER -> listOf(
            // the field, the hay taken to the kozolec now and then; the mill on the stream once it grinds (the farmer is the miller)
            o("field", Doing.HOE, 2f, DAY, errand = "field" to "kozolec", max = 120), o("spot:riverbank", Doing.TEND, 1.2f, DAY, max = 90, needs = "project:mlin"),
            o("kozolec", Doing.TEND, 0.8f, DAY, max = 60), o("spot:riverbank", Doing.STAND, 0.4f, MORNING, max = 50),
            o("spot:woodpile", Doing.CHOP, 0.4f, DAY), o("home", Doing.FEED, 0.3f, MORNING, min = 30, max = 50), o("home", Doing.MEND, 0.4f, EVENING),
            o("home", Doing.SIT, 0.4f, EVENING), o("well", Doing.CHAT, 0.4f, EVENING), o("fire", Doing.WARM, 0.4f, EVENING),
            // the hay into the double hayrack in its months, the field's hay taken there; the cart over the new bridge
            o("project:toplar", Doing.TEND, 0.8f, DAY, errand = "field" to "project:toplar", max = 90, months = 6..9),
            o("project:most", Doing.STAND, 0.3f, MORNING, min = 30, max = 45),
        )
        Kind.SMITH -> listOf(
            o("smithy", Doing.HAMMER, 3f, DAY, max = 120), o("spot:woodpile", Doing.CHOP, 0.5f, DAY), o("spot:rocks", Doing.CHOP, 0.3f, MORNING),
            o("home", Doing.SWEEP, 0.2f, MORNING, min = 30, max = 45), o("well", Doing.CHAT, 0.4f, EVENING), o("smithy", Doing.SIT, 0.5f, EVENING),
            o("fire", Doing.WARM, 0.5f, EVENING),
            // the fire station's hoses and its pump; the fountain's pipes he forged, the town clock he winds
            o("project:gasilski_dom", Doing.TEND, 0.6f, AFTERNOON, max = 60), o("project:vodnjak_na_trgu", Doing.TEND, 0.3f, MORNING, min = 30, max = 45),
            o("project:mestna_ura", Doing.TEND, 0.2f, MORNING, min = 30, max = 40),
        )
        Kind.BEEKEEPER -> listOf(
            o("beehive", Doing.TEND, 2.5f, DAY, max = 110), o("spot:meadow", Doing.STAND, 0.5f, MORNING, max = 50), o("spot:pond", Doing.FISH, 0.5f, AFTERNOON),
            o("forest", Doing.GATHER, 0.4f, DAY, months = 6..9), o("home", Doing.MEND, 0.3f, AFTERNOON), o("lipa", Doing.SIT, 0.5f, EVENING),
            o("fire", Doing.WARM, 0.4f, EVENING),
            // the bee meadow in its months; a game of bocce on a summer evening
            o("project:cebelji_travnik", Doing.TEND, 1.0f, DAY, max = 90, months = 4..9), o("project:balinisce", Doing.CHAT, 0.4f, EVENING, max = 60, months = 5..9),
        )
        Kind.WINEMAKER -> listOf(
            o("market", Doing.CHAT, 1.5f, ALL), o("field", Doing.HOE, 0.6f, MORNING), o("spot:road", Doing.WATCH, 0.3f, AFTERNOON, max = 50),
            o("well", Doing.CHAT, 0.5f, AFTERNOON or EVENING), o("spot:woodpile", Doing.CHOP, 0.4f, DAY), o("home", Doing.SWEEP, 0.3f, MORNING, min = 30, max = 45),
            o("neighbour", Doing.CHAT, 0.4f, DAY, min = 30, max = 60), o("forest", Doing.GATHER, 0.3f, DAY, months = 8..10), o("fire", Doing.WARM, 0.6f, EVENING),
            // pouring at the bocce court's table, polishing the fire station's helmets
            o("project:balinisce", Doing.CHAT, 0.6f, AFTERNOON or EVENING, max = 60), o("project:gasilski_dom", Doing.TEND, 0.2f, AFTERNOON, max = 40),
        )
        Kind.INNKEEPER -> listOf(
            o("market", Doing.CHAT, 2f, ALL), o("market", Doing.CHAT, 0.8f, MORNING, errand = "water" to "market", max = 60),
            o("home", Doing.SWEEP, 0.5f, MORNING, min = 30, max = 50), o("home", Doing.HANG, 0.5f, MORNING, min = 30, max = 60),
            o("home", Doing.BEAT, 0.4f, DAY, min = 30, max = 50), o("home", Doing.HOE, 0.4f, AFTERNOON), o("home", Doing.FEED, 0.3f, DAY, min = 30, max = 50),
            o("neighbour", Doing.CHAT, 0.4f, DAY, min = 30, max = 60), o("well", Doing.CHAT, 0.4f, AFTERNOON), o("fire", Doing.WARM, 0.4f, EVENING),
            // showing the guests the view from the lookout tower
            o("project:razgledni_stolp", Doing.STAND, 0.5f, AFTERNOON, max = 60),
        )
        Kind.TEACHER -> listOf(
            o("school", Doing.STAND, 1.5f, DAY), o("lipa", Doing.SIT, 0.8f, AFTERNOON or EVENING), o("well", Doing.CHAT, 0.6f, AFTERNOON or EVENING),
            o("spot:meadow", Doing.STAND, 0.3f, AFTERNOON, max = 50), o("home", Doing.MEND, 0.4f, AFTERNOON or EVENING),
            o("neighbour", Doing.CHAT, 0.3f, DAY, min = 30, max = 60), o("fire", Doing.WARM, 0.4f, EVENING),
            // on the bench by the playground after school; telling the time under the town clock; at the fountain
            o("project:igrisce", Doing.SIT, 0.6f, AFTERNOON, max = 60), o("project:mestna_ura", Doing.STAND, 0.3f, AFTERNOON, min = 30, max = 45),
            o("project:vodnjak_na_trgu", Doing.CHAT, 0.2f, AFTERNOON, min = 30, max = 45),
        )
        Kind.HUNTER -> listOf(
            o("spot:highseat", Doing.WATCH, 2f, ALL, max = 120), o("spot:path", Doing.WATCH, 0.6f, DAY, max = 60), o("spot:pond", Doing.FISH, 0.3f, AFTERNOON),
            o("forest", Doing.GATHER, 0.4f, DAY, months = 8..10), o("spot:woodpile", Doing.CHOP, 0.3f, DAY), o("fire", Doing.WARM, 0.4f, EVENING),
        )
        Kind.CHILD -> listOf(
            o("spot:meadow", Doing.PLAY, 1.2f, ALL, min = 30, max = 70), o("spot:pond", Doing.PLAY, 0.5f, AFTERNOON, min = 30, max = 60), o("spot:pond", Doing.FISH, 0.3f, AFTERNOON),
            o("well", Doing.PLAY, 0.6f, ALL, min = 30, max = 60), o("lipa", Doing.PLAY, 0.7f, AFTERNOON or EVENING, min = 30, max = 60),
            o("home", Doing.PLAY, 0.3f, MORNING, errand = "spot:woodpile" to "home", min = 30, max = 50), o("home", Doing.FEED, 0.4f, DAY, min = 30, max = 45),
            o("spot:meadow", Doing.FEED, 0.3f, DAY, min = 30, max = 45), o("forest", Doing.GATHER, 0.5f, DAY, min = 30, max = 60, months = PICKING),
            o("fire", Doing.WARM, 0.5f, EVENING),
            // the village's places once built: the playground most of all, the fountain on hot days, the trout under the
            // cart bridge, the bee meadow's flowers, the maypole while it stands
            o("project:igrisce", Doing.PLAY, 1.2f, ALL, min = 30, max = 70), o("project:vodnjak_na_trgu", Doing.PLAY, 0.4f, AFTERNOON, min = 30, max = 45, months = 6..8),
            o("project:most", Doing.STAND, 0.2f, AFTERNOON, min = 30, max = 40, months = 5..9), o("project:cebelji_travnik", Doing.PLAY, 0.2f, AFTERNOON, min = 30, max = 40, months = 4..9),
            o("project:mlaj", Doing.PLAY, 0.4f, AFTERNOON or EVENING, min = 30, max = 45),
        )
        Kind.ADULT -> listOf(
            o("home", Doing.HOE, 0.8f, DAY), o("field", Doing.HOE, 0.8f, DAY), o("spot:woodpile", Doing.CHOP, 0.7f, DAY),
            o("home", Doing.SWEEP, 0.3f, MORNING, min = 30, max = 45), o("home", Doing.HANG, 0.4f, MORNING, min = 30, max = 60), o("home", Doing.BEAT, 0.3f, DAY, min = 30, max = 50),
            o("home", Doing.FEED, 0.3f, DAY, min = 30, max = 50), o("home", Doing.MEND, 0.4f, AFTERNOON or EVENING), o("home", Doing.SIT, 0.3f, DAY, errand = "water" to "home", max = 60),
            o("well", Doing.CHAT, 0.5f, AFTERNOON or EVENING), o("neighbour", Doing.CHAT, 0.4f, DAY, min = 30, max = 60), o("spot:pond", Doing.FISH, 0.3f, AFTERNOON),
            o("market", Doing.CHAT, 0.4f, DAY), o("forest", Doing.GATHER, 0.4f, DAY, months = PICKING), o("home", Doing.SIT, 0.4f, EVENING), o("fire", Doing.WARM, 0.4f, EVENING),
        )
    }

    /** What they do at their own workplace (the first of their homes that isn't a dwelling), by what it is. */
    private fun workAt(place: String): Doing = when (place) {
        "field" -> Doing.HOE
        "smithy" -> Doing.HAMMER
        "beehive" -> Doing.TEND
        "market", "well" -> Doing.CHAT
        "lipa" -> Doing.SIT
        "spot:woodpile", "spot:rocks" -> Doing.CHOP
        "spot:pond" -> Doing.FISH
        "spot:riverbank" -> Doing.WASH
        "spot:meadow" -> Doing.TEND
        "spot:highseat", "spot:road", "watchtower" -> Doing.WATCH
        else -> Doing.STAND
    }

    private val DWELLINGS = setOf("tent", "hut", "house")

    /** The buildings a household lives in: another household's door is a neighbour's. */
    private val HOUSEHOLDS = setOf(BuildingType.TENT, BuildingType.HUT, BuildingType.HOUSE)

    /** The buildings with a roof to be inside of (lunch at the table, the housework): a household's, the smithy, the school … */
    private val ROOFED = HOUSEHOLDS + setOf(BuildingType.SMITHY, BuildingType.SCHOOL, BuildingType.MARKET, BuildingType.BEEHIVE)

    /** The work outside that winter has none of: the soil is frozen, the washing would freeze, nothing to pick. */
    private val WARM_WORK = setOf(Doing.HOE, Doing.FISH, Doing.WASH, Doing.HANG, Doing.GATHER)

    /** Whether [key] ("home", "fire", a building type, "spot:<id>", "project:<id>": finished) is a place in [state]'s village now. */
    private fun exists(key: String, state: GameState, home: TownPlace): Boolean = when {
        key == "home" || key == "fire" || key == "forest" || key == "water" || key.startsWith("spot:") -> true
        key == "neighbour" -> neighbours(state, home).isNotEmpty()
        key.startsWith("project:") -> TownMarkers.has(state, TownPlace.of(key))
        else -> state.buildings.any { it.type.name.equals(key, ignoreCase = true) }
    }

    /** The doors of the other households than [home]'s: each house of its own, the huts and the tents as one place each. */
    private fun neighbours(state: GameState, home: TownPlace): List<TownPlace> =
        state.buildings.filter { it.type in HOUSEHOLDS }.map { TownPlace.of(it, state) }.distinct().filter { it != home }

    /**
     * The errands of a stretch from [start] to [end] at [place] (fetching [errand]'s load from its first place to its
     * second): one, or two in a long stretch (the day's dice), each at a moment of its own share of the stretch, after the
     * walk there and a while at the work, and back before it ends; none in a stretch too short for one.
     */
    private fun errandsOf(errand: Pair<String, String>?, home: TownPlace, start: Int, end: Int, state: GameState, date: LocalDate, key: String): List<Errand> {
        errand ?: return emptyList()
        if (!exists(errand.first, state, home) || !exists(errand.second, state, home)) return emptyList()
        val from = place(errand.first, home, state, date, "$key#from"); val to = place(errand.second, home, state, date, "$key#to")
        if (from == to) return emptyList()
        val first = start + 8; val last = end - 3 - ERRAND
        if (last <= first) return emptyList()
        val n = if (last - first >= 40 && Happenings.roll(state.seed, date, "$key#errands") < 0.55f) 2 else 1
        val share = (last - first) / n
        return (0 until n).map { k -> Errand(first + k * share + (Happenings.roll(state.seed, date, "$key#errand$k") * (share - if (k < n - 1) ERRAND + 2 else 0)).toInt().coerceAtLeast(0), from, to) }
    }

    /**
     * The place [key] names for someone whose home is [home]: their home, a neighbour's door (one of them by the day's dice
     * and [salt]), the water (the well once it stands, else the stream), or the place itself.
     */
    private fun place(key: String, home: TownPlace, state: GameState, date: LocalDate, salt: String): TownPlace = when (key) {
        "home" -> home
        "water" -> if (state.buildings.any { it.type == BuildingType.WELL }) TownPlace.At(BuildingType.WELL) else TownPlace.Spot("riverbank")
        "neighbour" -> neighbours(state, home).let { n -> if (n.isEmpty()) home else n[(Happenings.roll(state.seed, date, "$salt#door") * n.size).toInt().coerceIn(0, n.size - 1)] }
        else -> TownPlace.of(key)
    }

    private fun winter(date: LocalDate) = date.monthValue in listOf(12, 1, 2)

    /** Whether [place] is a building with a roof to be inside of ([ROOFED]): lunch at the table, the housework. */
    private fun roofed(place: TownPlace): Boolean = place is TownPlace.At && place.type in ROOFED

    /**
     * [v]'s day on [date] as [plan] has it, from when they get up until they go to bed: the first minutes at [home], then
     * their work (with an errand or two in it: [Stint.errands]), the chores of the house, a rest, a chat, the children's
     * games, one after another, each a while (the day's dice: tens of minutes), on the bench by the house when there's
     * nothing else to do; the school's hours on a weekday (the children and the teacher, where a school stands; not in July
     * and August), lunch in the house (a few by the fire), Sunday mass at the church (the older ones, where a church stands),
     * the market on Saturday. Winter has no hoeing, fishing, washing or picking, more wood to chop. Pure: the same villager,
     * village and day give the same day. With [day]'s [Day.people] the village's day is shared out among them ([village]):
     * this is [v]'s part of it.
     */
    fun stints(v: Villager, plan: Plan, state: GameState, date: LocalDate, home: TownPlace, day: Day = Day()): List<Stint> {
        village(state, date, day)?.stints?.get(v.id)?.let { return it }
        val key = StintKey(v.id, System.identityHashCode(v), plan, state.seed, state.buildings, placesOf(state), date, home)
        synchronized(days) { days[key] }?.let { return it }
        return stintsOf(v, plan, state, date, home).also { synchronized(days) { days[key] = it } }
    }

    /** The days worked out, by who, which village (its buildings) and which day; at most a few hundred. */
    private val days = object : LinkedHashMap<StintKey, List<Stint>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<StintKey, List<Stint>>?) = size > 512
    }

    private data class StintKey(
        val id: String, val v: Int, val plan: Plan, val seed: Long, val buildings: List<si.lanisce.lani.game.Building>, val places: Set<String>,
        val date: LocalDate, val home: TownPlace,
    )

    /**
     * The village's projects whose landmark stands ([TownMarkers.has]: finished, not down for the season), part of what a
     * day is worked out from: a place to go to once built (the bocce court, the mill), as the buildings are.
     */
    private fun placesOf(state: GameState): Set<String> =
        si.lanisce.lani.game.Catalog.projects.filter { si.lanisce.lani.game.Projects.standing(state, it) }.map { it.id }.toSet()

    /** Lunch: from half past twelve to ten past one. */
    private const val LUNCH = 12 * 60 + 30
    private const val LUNCH_END = 13 * 60 + 10

    private fun stintsOf(v: Villager, plan: Plan, state: GameState, date: LocalDate, home: TownPlace): List<Stint> {
        val kind = plan.kind
        val weekday = date.dayOfWeek
        val cold = winter(date)
        val fixed = ArrayList<Stint>()
        val holidays = date.monthValue in 7..8
        val school = state.buildings.any { it.type == BuildingType.SCHOOL } && weekday.value <= 5 && !holidays
        if (school && (kind == Kind.CHILD || kind == Kind.TEACHER)) fixed += Stint(8 * 60, 12 * 60 + 30, TownPlace.At(BuildingType.SCHOOL), Doing.STAND)
        if (weekday == DayOfWeek.SUNDAY && state.buildings.any { it.type == BuildingType.CHURCH } &&
            kind in setOf(Kind.GRANDMA, Kind.GRANDPA, Kind.STORYTELLER, Kind.AUNT, Kind.TEACHER)) fixed += Stint(9 * 60, 10 * 60, TownPlace.At(BuildingType.CHURCH), Doing.STAND)
        // lunch in the house; a few eat by the fire (the day's dice), and so does whoever has no roof yet
        if (kind != Kind.SHEPHERD && kind != Kind.HUNTER) {
            val fire = !roofed(home) || Happenings.roll(state.seed, date, "lunch:${v.id}") < LUNCH_BY_FIRE
            fixed += if (fire) Stint(LUNCH, LUNCH_END, TownPlace.Fire, Doing.WARM) else Stint(LUNCH, LUNCH_END, home, Doing.INSIDE)
        }
        fixed.sortBy { it.start }

        // their own workplace (the smithy, the bee house, the high seat …) weighs in too, whoever they are
        val own = v.home.firstOrNull { it !in DWELLINGS }?.takeIf { exists(it, state, home) }?.let { o(it, workAt(it), 1.5f, DAY) }
        val market = weekday == DayOfWeek.SATURDAY
        val month = date.monthValue
        val pool = (options(kind) + listOfNotNull(own)).filter { op ->
            exists(op.place, state, home) && (op.needs == null || exists(op.needs, state, home)) && !(holidays && op.place == "school") &&
                !(cold && op.doing in WARM_WORK) && (op.months == null || month in op.months)
        } + if (cold && kind != Kind.CHILD) listOf(o("spot:woodpile", Doing.CHOP, 1f, DAY)) else emptyList()

        val out = ArrayList<Stint>()
        var t = plan.up
        val end = plan.bed
        out += Stint(t, minOf(t + 25, end), home, Doing.STAND)
        t += 25
        var i = 0
        // the errand of the last stretch picked (a stretch of the same work with an errand, or without, may follow it)
        var lastErrand: Pair<String, String>? = null
        while (t < end && i < 60) {
            val f = fixed.firstOrNull { it.end > t && it.start < end }
            if (f != null && f.start <= t) {
                out += f.copy(start = t, end = minOf(f.end, end)); t = f.end; lastErrand = null; continue
            }
            val limit = minOf(f?.start ?: end, end)
            val part = partOf(t)
            val last = out.lastOrNull()
            val here = pool.filter { it.parts and part != 0 && !(last != null && it.place != "neighbour" && place(it.place, home, state, date, "") == last.place && it.doing == last.doing && it.errand == lastErrand) }
            val op = pick(here, state.seed, date, "${v.id}#$i") { w ->
                var x = w.weight
                if (market && w.place == "market") x *= 3f
                if (weekday == DayOfWeek.SUNDAY && (w.doing in setOf(Doing.HOE, Doing.HAMMER, Doing.HANG, Doing.BEAT) || w.errand != null)) x *= 0.3f
                x
            }
            if (op == null) {
                // nothing else to do: on the bench by the house
                out += Stint(t, limit, home, Doing.SIT); t = limit; i++; lastErrand = null; continue
            }
            lastErrand = op.errand
            val len = op.min + (Happenings.roll(state.seed, date, "${v.id}#$i#len") * (op.max - op.min)).toInt()
            var stop = minOf(t + len, limit)
            if (limit - stop < 20) stop = limit
            out += Stint(t, stop, place(op.place, home, state, date, "${v.id}#$i"), op.doing, errandsOf(op.errand, home, t, stop, state, date, "${v.id}#$i"))
            t = stop
            i++
        }
        return out
    }

    /** The part of the day minute [t] (of someone's day) is in. */
    private fun partOf(t: Int): Int {
        val clockT = t % 1440
        return when {
            clockT < 12 * 60 -> MORNING
            clockT < 17 * 60 + 30 -> AFTERNOON
            else -> EVENING
        }
    }

    private fun pick(options: List<Option>, seed: Long, date: LocalDate, key: String, weight: (Option) -> Float): Option? {
        if (options.isEmpty()) return null
        val total = options.sumOf { weight(it).toDouble() }.toFloat()
        if (total <= 0f) return options.first()
        var r = Happenings.roll(seed, date, key) * total
        for (op in options) {
            r -= weight(op)
            if (r < 0f) return op
        }
        return options.last()
    }

    // ------------------------------------------------------------------ the village's day, shared out

    /** How many eat by the fire at lunch (the day's dice), and at most ([village]). */
    private const val LUNCH_BY_FIRE = 0.12f
    private const val LUNCH_FIRE = 3

    /** How many stand round the fire by day outside lunch and the evening; from [EVENING_FIRE] the evening's gathering. */
    const val DAY_FIRE = 2

    /** From when the fire is the evening's (minutes): its gathering, as many as sit by it after dark ([eveningFire]). */
    private const val EVENING_FIRE = 17 * 60 + 30

    /** How many stand or work at one place at once outside its gatherings (the school, mass, the fire's evening). */
    const val CROWD = 3

    /** A wide place holds a few more: the meadow (the sheep and the children's games), the market. */
    private const val WIDE = 4

    /**
     * How many sit by the fire in the evening besides the storyteller ([village]): a handful; on a Saturday the village's
     * evening, more. At a festival everyone who is up gathers round it.
     */
    fun eveningFire(date: LocalDate): Int = if (date.dayOfWeek == DayOfWeek.SATURDAY) 7 else 4

    /**
     * How many may be at [place] at minute [m] of the day outside the gatherings: a few at each place ([CROWD]; a few at
     * each of a type's buildings: the fields, the huts), more where there's room (the meadow, the market); the fire a couple
     * by day, a few at lunch, the evening's gathering from half past five; the school and the church as many as come.
     */
    private fun room(place: TownPlace, m: Int, state: GameState, date: LocalDate): Int = when {
        place == TownPlace.Fire -> when {
            m % 1440 in LUNCH until LUNCH_END -> LUNCH_FIRE
            m % 1440 >= EVENING_FIRE || m >= 1440 -> eveningFire(date) + 1
            else -> DAY_FIRE
        }
        place is TownPlace.At && (place.type == BuildingType.SCHOOL || place.type == BuildingType.CHURCH) -> Int.MAX_VALUE
        place is TownPlace.At && place.id == null && place.type != BuildingType.HOUSE ->
            (if (place.type == BuildingType.MARKET) WIDE else CROWD) * state.buildings.count { it.type == place.type }.coerceAtLeast(1)
        place == TownPlace.Spot("meadow") -> WIDE
        place is TownPlace.Spot && place.id in setOf("road", "path", "highseat", "rocks") -> 2
        else -> CROWD
    }

    /** A chore at home when the place [v] wanted is full ([village]): by their kind, the season and the part of the day. */
    private fun chore(kind: Kind, date: LocalDate, t: Int, seed: Long, key: String): Doing {
        val cold = winter(date)
        val part = partOf(t)
        val all = when (kind) {
            Kind.CHILD -> listOf(Doing.FEED, Doing.PLAY)
            Kind.GRANDMA, Kind.GRANDPA, Kind.STORYTELLER -> listOf(Doing.MEND, Doing.SIT, Doing.FEED)
            else -> listOf(Doing.SWEEP, Doing.HANG, Doing.BEAT, Doing.FEED, Doing.MEND, Doing.HOE)
        }.filter { !(cold && it in WARM_WORK) && !(it == Doing.SWEEP && part != MORNING) }
        return all[(Happenings.roll(seed, date, "$key#chore") * all.size).toInt().coerceIn(0, all.size - 1)]
    }

    /** The village's day ([village]): everyone's stretches shared out, and who sits by the fire tonight. */
    private class VillagePlan(val stints: Map<String, List<Stint>>, val sitters: Set<String>)

    private data class VillageKey(
        val seed: Long, val buildings: List<si.lanisce.lani.game.Building>, val places: Set<String>, val date: LocalDate, val people: List<List<Any?>>,
        val homes: Map<String, TownPlace>, val tellers: Map<String, String>, val calls: Map<String, TownPlace>,
    )

    private val villages = object : LinkedHashMap<VillageKey, VillagePlan>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<VillageKey, VillagePlan>?) = size > 16
    }

    /** The last village worked out, by the very inputs (the map asks for it many times a frame). */
    private class LastVillage(
        val people: List<Villager>, val seed: Long, val buildings: List<si.lanisce.lani.game.Building>, val projects: Map<String, Int>,
        val down: Set<String>, val date: LocalDate,
        val homes: Map<String, TownPlace>, val tellers: Map<String, String>, val calls: Map<String, TownPlace>, val plan: VillagePlan,
    )

    @Volatile private var lastVillage: LastVillage? = null

    /**
     * The village's day on [date] for [day]'s [Day.people], shared out among them (companion/VILLAGERS.md, "A day in the
     * village"): each wants their own day ([stintsOf]); then, in the order they'd come (first come first served, their own
     * workplace before all: France in his field, Tone at his anvil), whoever would make a place too full ([room]: no more
     * than a few at one place outside its gatherings, a couple at the fire by day) does a chore at home instead (the
     * garden, the washing, the hens …), or, with no more room there either, is in the house. Who waits for the learner
     * ([Day.calls]) is at their place all day, counted there. In the evening a handful sit by the fire, the storyteller
     * first, the rest by the day's dice ([eveningFire]); at a festival everyone. Null without [Day.people]. Pure.
     */
    private fun village(state: GameState, date: LocalDate, day: Day): VillagePlan? {
        if (day.people.isEmpty()) return null
        lastVillage?.let { l ->
            if (l.people === day.people && l.seed == state.seed && l.buildings === state.buildings && l.projects === state.projects &&
                l.down === state.landmarksDown && l.date == date && l.homes === day.homes && l.tellers === day.tellers && l.calls === day.calls) return l.plan
        }
        val key = VillageKey(state.seed, state.buildings, placesOf(state), date, day.people.map { listOf(it.id, it.art, it.home, it.routine, it.sleeps) }, day.homes, day.tellers, day.calls)
        val plan = synchronized(villages) { villages[key] } ?: villageOf(state, date, day).also { synchronized(villages) { villages[key] = it } }
        lastVillage = LastVillage(day.people, state.seed, state.buildings, state.projects, state.landmarksDown, date, day.homes, day.tellers, day.calls, plan)
        return plan
    }

    private class Seg(val start: Int, val end: Int)

    private fun villageOf(state: GameState, date: LocalDate, day: Day): VillagePlan {
        val people = day.people.filter { it.art != "baby" }.distinctBy { it.id }
        val plans = people.associate { it.id to plan(it, it.id in day.tellers) }
        val homes = people.associate { it.id to (day.homes[it.id] ?: TownMarkers.homeOf(it, state)) }
        val wishes = people.associate { v -> v.id to stints(v, plans.getValue(v.id), state, date, homes.getValue(v.id)) }
        val at = HashMap<TownPlace, MutableList<Seg>>()
        // the most at [place] at once from [a] until [b]: the own workplaces' stretches are in already, whenever they begin
        fun count(place: TownPlace, a: Int, b: Int): Int {
            val segs = at[place] ?: return 0
            var most = 0
            for (m in sequenceOf(a) + segs.asSequence().map { it.start }.filter { it in (a + 1) until b }) most = maxOf(most, segs.count { it.start <= m && it.end > m })
            return most
        }
        // who waits for the learner is at their place all day
        for (v in people) day.calls[v.id]?.let { place -> plans.getValue(v.id).let { p -> at.getOrPut(place) { ArrayList() } += Seg(p.up, p.bed) } }
        val own = people.associate { v -> v.id to v.home.firstOrNull { it !in DWELLINGS }?.let { place(it, homes.getValue(v.id), state, date, "") } }
        val order = people.associate { it.id to Happenings.roll(state.seed, date, "order:${it.id}") }
        // each stretch someone wants: their own workplace's first, then all by when they start
        class Want(val id: String, val i: Int, val s: Stint, val first: Boolean)
        val wants = people.filter { it.id !in day.calls }.flatMap { v -> wishes.getValue(v.id).mapIndexed { i, s -> Want(v.id, i, s, s.place == own[v.id]) } }
            .sortedWith(compareBy<Want>({ !it.first }, { it.s.start }, { order.getValue(it.id) }))
        val out = people.associate { it.id to wishes.getValue(it.id).toMutableList() }
        for (w in wants) {
            val s = w.s
            // indoors, the school's hours, mass: as many as come
            if (s.doing == Doing.INSIDE || room(s.place, s.start, state, date) == Int.MAX_VALUE) continue
            val home = homes.getValue(w.id)
            // lunch by the fire when it's full: in the house after all
            val lunch = s.start == LUNCH && s.end == LUNCH_END
            val kept = when {
                w.first || count(s.place, s.start, s.end) < room(s.place, s.start, state, date) -> s
                !lunch && s.place != home && count(home, s.start, s.end) < room(home, s.start, state, date) ->
                    Stint(s.start, s.end, home, chore(plans.getValue(w.id).kind, date, s.start, state.seed, "${w.id}#${w.i}"))
                roofed(home) -> Stint(s.start, s.end, home, Doing.INSIDE)
                else -> s
            }
            if (kept.doing != Doing.INSIDE) at.getOrPut(kept.place) { ArrayList() } += Seg(kept.start, kept.end)
            out.getValue(w.id)[w.i] = kept
        }
        // the evening by the fire: the storytellers, then by the day's dice a handful
        val sitters = LinkedHashSet<String>()
        for (v in people) if (plans.getValue(v.id).kind == Kind.STORYTELLER) sitters += v.id
        people.filter { it.id !in sitters && Happenings.roll(state.seed, date, "fire:${it.id}") < fireChance(plans.getValue(it.id).kind) }
            .sortedBy { Happenings.roll(state.seed, date, "fire:${it.id}") / fireChance(plans.getValue(it.id).kind) }
            .take(eveningFire(date)).forEach { sitters += it.id }
        return VillagePlan(out, sitters)
    }

    /** How likely someone sits by the fire this evening (the day's dice): the children more often. */
    private fun fireChance(kind: Kind): Float = if (kind == Kind.CHILD) 0.5f else 0.35f

    // ------------------------------------------------------------------ now

    /**
     * Where [v] is at [minute] (0 until 1440, with its fraction) of [date] in [state]'s village, and what they do, as
     * [day] has the village (see [Day]); [month] for the light (the map's). Pure: the same inputs, the same answer.
     *
     * 1. Someone with a happening on ([Day.busy]) is there, day or night: by the fire telling the story, on the watchtower,
     *    in the forest listening for the owl. Night happenings win.
     * 2. A night ritual in its time: out with a lantern at its place, then home (or back to the fire, before bedtime).
     * 3. From bedtime: the walk home from where they were (the fire), then in bed. The storyteller's bedtime is after his
     *    story: once the learner has heard it (5 minutes after, when the app knows the minute; else his own, the last), or
     *    [STORY_UNTIL] when they haven't.
     * 4. Up in the dark: in the evening by the fire (the storyteller always, the others some evenings, by the day's dice) or
     *    at home, windows lit; in the dark morning at home.
     * 5. By daylight: waiting where the learner is wanted ([Day.calls]), waving now and then; else their day's [stints], on the
     *    way from the last one to this one at first, away on an errand of the work now and then ([Now.errand], [ERRAND]
     *    minutes: the water fetched from the well, the hay taken to the kozolec).
     */
    fun now(v: Villager, state: GameState, date: LocalDate, minute: Double, month: Int = date.monthValue, day: Day = Day()): Now {
        val teller = v.id in day.tellers
        val plan = plan(v, teller)
        // before they get up it is still the night of the day before
        val (d, dm) = if (minute < plan.up) date.minusDays(1) to minute + 1440 else date to minute
        val dark = dark(month, minute)
        day.busy[v.id]?.let { at ->
            return Now(Where.OUT, at, if (at == TownPlace.Fire) Doing.WARM else Doing.WAVE, lantern = dark && at != TownPlace.Fire)
        }
        val home = day.homes[v.id] ?: TownMarkers.homeOf(v, state)
        val bed = bedtime(plan, v, state, d, day)
        val base = { m: Double -> base(v, plan, state, d, m, month, day, home, bed) }
        for (r in plan.rituals) {
            if (!TownMarkers.has(state, r.to)) continue
            if (dm >= r.at && dm < r.until) {
                val before = base(r.at - 0.01)
                val from = if (before.where == Where.OUT) before.place else home
                return Now(Where.OUT, r.to, r.doing, from = from, since = (dm - r.at) * 60, lantern = r.lantern && dark, ritual = r)
            }
            if (dm >= r.until && dm < r.until + TRANSIT) {
                val after = base(dm)
                val since = (dm - r.until) * 60
                return when {
                    // back to the fire (or the day) when there's time before bed; so near it, home
                    after.where == Where.OUT && bed - r.until >= 15 -> after.copy(from = r.to, since = since, lantern = r.lantern && dark)
                    else -> Now(Where.HOMEWARD, home, Doing.STAND, from = r.to, since = since, lantern = r.lantern && dark)
                }
            }
        }
        return base(dm)
    }

    /**
     * Whether [v], not in bed at [minute] of [date], still goes to bed tonight: up in the evening (by the fire, at home, on a
     * ritual or a happening before their bedtime) or on the way home to bed. Their bed waits for them
     * ([si.lanisce.lani.game.scene.Sleep.sleepers]): nobody else lies down in it. Not in the morning once they are up, nor
     * out in the night after their bedtime (Micka at the woodpile before dawn, a happening that keeps them out): then it is
     * free. Pure, as [now].
     */
    fun toBed(v: Villager, state: GameState, date: LocalDate, minute: Double, day: Day = Day()): Boolean {
        val plan = plan(v, v.id in day.tellers)
        val (d, dm) = if (minute < plan.up) date.minusDays(1) to minute + 1440 else date to minute
        return dm >= 12 * 60 && dm < bedtime(plan, v, state, d, day) + TRANSIT
    }

    /** When [v] goes to bed tonight (minutes of their day): their plan's, and the storyteller's after his story. */
    private fun bedtime(plan: Plan, v: Villager, state: GameState, date: LocalDate, day: Day): Int {
        val key = day.tellers[v.id] ?: return plan.bed
        val told = state.happeningsDone[key] == date.toString()
        if (!told) return maxOf(plan.bed, STORY_UNTIL)
        val at = day.storyAt?.takeIf { it.toLocalDate() == date || (it.toLocalDate() == date.plusDays(1) && it.hour < 5) } ?: return plan.bed
        val m = at.hour * 60 + at.minute + if (at.toLocalDate() != date) 1440 else 0
        return maxOf(plan.bed, m + 5)
    }

    /** Where they are at [dm] (minutes of their day) without a ritual: in bed, on the way home, by the fire, at home, or out. */
    private fun base(v: Villager, plan: Plan, state: GameState, date: LocalDate, dm: Double, month: Int, day: Day, home: TownPlace, bed: Int): Now {
        if (dm >= bed) {
            val was = awake(v, plan, state, date, bed - 0.01, month, day, home)
            if (dm < bed + TRANSIT && was.where == Where.OUT && was.place != home) {
                return Now(Where.HOMEWARD, home, Doing.STAND, from = was.place, since = (dm - bed) * 60, lantern = dark(month, dm))
            }
            return Now(Where.ASLEEP, home)
        }
        return awake(v, plan, state, date, dm, month, day, home)
    }

    /** While they're up: out at their day's work, waiting for the learner, by the fire in the dark evening, or at home. */
    private fun awake(v: Villager, plan: Plan, state: GameState, date: LocalDate, dm: Double, month: Int, day: Day, home: TownPlace): Now {
        val evening = light(month)[1]
        val stints = stints(v, plan, state, date, home, day)
        fun stintAt(m: Double): Int = stints.indexOfLast { it.start <= m }.coerceAtLeast(0)
        if (dark(month, dm)) {
            // the dark morning: at home, the lamp lit (past midnight before bedtime it is still the evening)
            if (dm < 12 * 60) return Now(Where.INDOORS, home)
            // the dark evening: by the fire, or home (the walk there first, from where they were when it got dark)
            val dusk = maxOf(evening.toDouble(), plan.up.toDouble())
            val was = stints.getOrNull(stintAt(dusk - 0.01))
            val from = was?.place?.takeIf { it != TownPlace.Fire }
            val since = (dm - dusk) * 60
            if (fireTonight(v, plan, state, date, day)) return Now(Where.OUT, TownPlace.Fire, Doing.WARM, from = from, since = since, lantern = true)
            if (dm - dusk < TRANSIT && from != null && from != home) return Now(Where.HOMEWARD, home, Doing.STAND, from = from, since = since, lantern = true)
            return Now(Where.INDOORS, home)
        }
        day.calls[v.id]?.let { at -> return Now(Where.OUT, at, Doing.WAVE) }
        val i = stintAt(dm)
        val s = stints.getOrNull(i) ?: return Now(Where.OUT, home, Doing.STAND)
        // on an errand of their work: away from it a few minutes, then back at it
        s.errands.firstOrNull { dm >= it.at && dm < it.at + ERRAND }?.let { e ->
            return Now(Where.OUT, s.place, s.doing, errand = Trip(e.from, e.to, (dm - e.at) * 60))
        }
        // just up in the morning: they come out of their door; later on the way from the last place to this one, or (the
        // same place as the last stretch) there since the first of them
        val from = if (i == 0) null else stints[i - 1].place.takeIf { it != s.place }
        var first = i
        while (from == null && first > 0 && stints[first - 1].place == s.place) first--
        val before = if (first < i) stints[i - 1].doing.takeIf { it != s.doing } else null
        // until when they do it there: the next stretches of the same there too, up to an errand or the dark
        var j = i
        while (j + 1 < stints.size && stints[j + 1].place == s.place && stints[j + 1].doing == s.doing && stints[j].errands.none { it.at >= dm }) j++
        val stop = minOf(stints[j].end.toDouble(), (i..j).asSequence().flatMap { stints[it].errands }.map { it.at.toDouble() }.filter { it >= dm }.minOrNull() ?: Double.MAX_VALUE,
            if (dm < evening) evening.toDouble() else Double.MAX_VALUE)
        return Now(Where.OUT, s.place, s.doing, from = from, since = (dm - stints[first].start) * 60, before = before, begun = (dm - s.start) * 60, left = (stop - dm) * 60)
    }

    /**
     * Whether [v] sits by the fire in the dark evening of [date]: the storyteller always, at a festival everyone, the others
     * by the day's dice (the children more often); in a village whose day is shared out ([village]) a handful of them
     * ([eveningFire]).
     */
    private fun fireTonight(v: Villager, plan: Plan, state: GameState, date: LocalDate, day: Day): Boolean {
        if (plan.kind == Kind.STORYTELLER || day.festival) return true
        village(state, date, day)?.let { if (day.people.any { it.id == v.id }) return v.id in it.sitters }
        return Happenings.roll(state.seed, date, "fire:${v.id}") < fireChance(plan.kind)
    }

    /**
     * When [v] sits by the fire in the dark evening of [date] (minutes of their day: from dusk in [month] until their
     * bedtime, the storyteller's after his story), or null when they're home then. The map keeps their place round it.
     */
    fun evening(v: Villager, state: GameState, date: LocalDate, month: Int, day: Day = Day()): IntArray? {
        val plan = plan(v, v.id in day.tellers)
        if (!fireTonight(v, plan, state, date, day)) return null
        val dusk = maxOf(light(month)[1], plan.up)
        val bed = bedtime(plan, v, state, date, day)
        return if (bed > dusk) intArrayOf(dusk, bed) else null
    }

    /** The minute (0 until 1440) of the clock [at] shows, with the seconds. */
    fun minuteOf(at: LocalDateTime): Double = at.hour * 60 + at.minute + at.second / 60.0 + at.nano / 60e9

    /** [now] at [at] (the phone's clock). */
    fun now(v: Villager, state: GameState, at: LocalDateTime, day: Day = Day()): Now = now(v, state, at.toLocalDate(), minuteOf(at), at.monthValue, day)
}
