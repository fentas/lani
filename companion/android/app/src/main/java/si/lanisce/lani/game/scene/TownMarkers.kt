package si.lanisce.lani.game.scene

import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.bi

/** A place in the village a marker (and a scene) belongs to. */
sealed interface TownPlace {
    data object Fire : TownPlace
    data object Forest : TownPlace
    /**
     * The first building of [type] (by plot); the renderer picks it. With an [id], that building: a house other than the
     * first, whose own room ([Homes]) has its bubbles and its card there (see [of] for a building).
     */
    data class At(val type: BuildingType, val id: String? = null) : TownPlace
    /** A named spot of the landscape ([TownSpots]); it always exists. */
    data class Spot(val id: String) : TownPlace
    /**
     * A village project's landmark ("project:<id>", see [si.lanisce.lani.game.ProjectSpec]): the place is there once the
     * project is finished (the vineyard's terraces lead into the vineyard).
     */
    data class Landmark(val project: String) : TownPlace

    companion object {
        /** "fire", "forest", "spot:<id>", "project:<id>" or a building type in lower case. */
        fun of(from: String): TownPlace = when {
            from == "fire" -> Fire
            from == "forest" -> Forest
            from.startsWith("spot:") -> Spot(from.removePrefix("spot:"))
            from.startsWith("project:") -> Landmark(from.removePrefix("project:"))
            else -> BuildingType.entries.firstOrNull { it.name.equals(from, ignoreCase = true) }?.let { At(it) } ?: Fire
        }

        /**
         * The place of building [b] in [state]'s village: a house other than the first (by plot) is a place of its own, its
         * room's ([Homes]); any other building is its type's, as the first of its type is.
         */
        fun of(b: si.lanisce.lani.game.Building, state: GameState): TownPlace =
            if (b.type == BuildingType.HOUSE && first(state, b.type)?.id != b.id) At(b.type, b.id) else At(b.type)

        /** The first building of [type] that stands (the lowest plot), whose place [At] without an id is. */
        fun first(state: GameState, type: BuildingType): si.lanisce.lani.game.Building? =
            state.buildings.filter { it.type == type }.minByOrNull { if (it.plot >= 0) it.plot else Int.MAX_VALUE }
    }
}

/** This place as its type's: a house of its own ([TownPlace.At.id]) as the houses'. */
fun TownPlace.general(): TownPlace = if (this is TownPlace.At && id != null) TownPlace.At(type) else this

/**
 * Named spots of the landscape around the village, where villagers can stand and things can happen
 * without a building. The renderer anchors them; they exist at every age.
 */
object TownSpots {
    /**
     * A spot's emoji, its name and where-phrase ("target · base", the right preposition and case), from the keys
     * [labelKey] and [whereKey] when read, so they are in the pair of the moment. They are its culture's when the pack
     * says: the horizon's what is there ([si.lanisce.lani.game.culture.Horizon]: "Gore · The mountains", "Il mare ·
     * Morje"), any spot's its own texts ([si.lanisce.lani.game.culture.SpotTexts]: "Oglarska kopa · The charcoal pile",
     * "La carbonaia · Oglarska kopa").
     */
    data class Info(val id: String, private val icon: String, private val labelKey: String, private val whereKey: String) {
        private val world get() = si.lanisce.lani.game.culture.Cultures.current.world
        private val backdrop get() = if (id == HORIZON) world.backdrop else null
        private val own get() = world.spots[id]
        val emoji: String get() = backdrop?.emoji ?: own?.emoji ?: icon
        val label: String get() = (backdrop?.name ?: own?.name)?.bi() ?: bi(labelKey)
        val where: String get() = (backdrop?.where ?: own?.where)?.bi() ?: bi(whereKey)
    }

    /** The land at the village's horizon: the hills and mountains behind it, or the sea (see [Info]). */
    const val HORIZON = "horizon"

    /** The charcoal pile deep in the woods below the village (a tall canvas that goes down that far only, see the renderer's Foreground). */
    const val KOPA = "kopa"

    val all = listOf(
        Info("meadow", "🌼", "townMarkers.meadow", "townMarkers.inMeadow"), // in the forest south of the village
        Info("pond", "💧", "townMarkers.pond", "townMarkers.byPond"), // a forest pond
        Info("highseat", "🪜", "townMarkers.highSeat", "townMarkers.onHighSeat"), // a hunter's high seat at the forest edge
        Info("path", "🥾", "townMarkers.forestPath", "townMarkers.onForestPath"), // where the path leaves into the woods
        Info("woodpile", "🪵", "townMarkers.woodpile", "townMarkers.byWoodpile"), // at the clearing's edge (felled trees)
        Info("rocks", "🪨", "townMarkers.rocks", "townMarkers.byRocks"), // rocks and a small quarry (stone)
        Info("riverbank", "🏞️", "townMarkers.streamBank", "townMarkers.byStream"), // the stream's bank
        Info("road", "🛤️", "townMarkers.road", "townMarkers.onRoad"), // the road into the village (travellers)
        Info("kopa", "♨️", "townMarkers.kopa", "townMarkers.byKopa"), // the charcoal pile smouldering deep in the woods
        Info("horizon", "⛰️", "townMarkers.horizon", "townMarkers.atHorizon"), // the land at the horizon: a culture's own landscape
    )

    val ids: List<String> = all.map { it.id }

    fun info(id: String): Info = all.firstOrNull { it.id == id } ?: Info(id, "📍", id, id)
}

/**
 * Something to see in the village, drawn as a bubble over its place: a villager's quest, a happening
 * (someone is in the kitchen), the event. [id] is "quest:<id>", "happening:<scene>/<id>", "event:<id>",
 * "visitor:<villager>", "arrival:<villager>" (someone to meet, at their home), "festival:<id>", "surprise:<kind>" (at the
 * road) or "project:<id>" (at its site). A villager's requests are one bubble: "quest:" and the first of them, [count] how
 * many (its badge: "⚒️ 2").
 */
data class TownMarker(val id: String, val kind: Kind, val emoji: String, val label: String, val place: TownPlace, val count: Int = 1) {
    enum class Kind { QUEST, HAPPENING, EVENT }
}

object TownMarkers {
    /** Where each quest giver waits, best first; the fire when none of those is built. */
    private val giverPlaces: Map<String, List<TownPlace>> = mapOf(
        "Babica Micka" to listOf(TownPlace.At(BuildingType.HOUSE), TownPlace.At(BuildingType.HUT)),
        "Mlinar France" to listOf(TownPlace.At(BuildingType.FIELD), TownPlace.At(BuildingType.KOZOLEC)),
        "Čebelar Anton" to listOf(TownPlace.At(BuildingType.BEEHIVE), TownPlace.Forest),
        "Kovač Tone" to listOf(TownPlace.At(BuildingType.SMITHY)),
        "Učiteljica Mojca" to listOf(TownPlace.At(BuildingType.SCHOOL), TownPlace.At(BuildingType.LIPA)),
        "Stari Janez" to listOf(TownPlace.At(BuildingType.LIPA)),
        "Pastir Luka" to listOf(TownPlace.Forest),
        "Gostilničarka Vida" to listOf(TownPlace.At(BuildingType.MARKET), TownPlace.At(BuildingType.HOUSE)),
        "Vinar Marko" to listOf(TownPlace.At(BuildingType.MARKET), TownPlace.At(BuildingType.FIELD)),
        "Teta Ančka" to listOf(TownPlace.At(BuildingType.HOUSE), TownPlace.At(BuildingType.HUT)),
    )

    /** Where [scene] is in this village: the first of its places that exists. */
    fun placeOf(scene: SceneSpec, state: GameState): TownPlace =
        scene.from.map(TownPlace::of).firstOrNull { has(state, it) } ?: TownPlace.of(scene.from.firstOrNull() ?: "fire")

    /**
     * Where happening [a] is in this village: its scene's building ([ActiveHappening.home]: a house's room is in the house
     * it is dealt to, a place of its own), else its scene's place ([placeOf]).
     */
    fun placeOf(a: ActiveHappening, state: GameState): TownPlace =
        a.home?.let { id -> state.buildings.firstOrNull { it.id == id } }?.let { TownPlace.of(it, state) } ?: placeOf(a.scene, state)

    fun has(state: GameState, place: TownPlace): Boolean = when (place) {
        TownPlace.Fire, TownPlace.Forest, is TownPlace.Spot -> true
        is TownPlace.At -> state.buildings.any { it.type == place.type && (place.id == null || it.id == place.id) }
        is TownPlace.Landmark -> si.lanisce.lani.game.Projects.spec(place.project)?.let { si.lanisce.lani.game.Projects.finished(state, it) } == true
    }

    /**
     * Where the people of the cast with a happening on now are: villager id → its scene's place ([placeOf]; the first of
     * their happenings when they have more). They stand there instead of pottering about their homes, or, when the place is
     * far from the clearing ([far]: the land at the horizon, a landmark out in the woods), aren't on the map at all: only
     * their bubble shows there. Everyone is on the map at most once.
     */
    fun standing(state: GameState, active: List<ActiveHappening>): Map<String, TownPlace> {
        val out = LinkedHashMap<String, TownPlace>()
        for (a in active) a.person?.villager?.let { id -> out.putIfAbsent(id, placeOf(a, state)) }
        return out
    }

    /**
     * Whether [place] lies away from the clearing whatever the canvas: the land at the horizon (the mountains, the sea). A
     * landmark may be too, when it stands out in the woods (the renderer's layout decides, see
     * [si.lanisce.lani.game.render.TownPeople.away]).
     */
    fun far(place: TownPlace): Boolean = place == TownPlace.Spot(TownSpots.HORIZON)

    /**
     * Where [v] is in this village: the first of their homes that exists. Someone whose home isn't built
     * yet (the smith before the smithy) is a visitor, waiting on the road into the village.
     */
    fun homeOf(v: Villager, state: GameState): TownPlace =
        v.home.map(TownPlace::of).firstOrNull { has(state, it) } ?: TownPlace.Spot("road")

    /** The villager a quest giver's name belongs to ("Babica Micka"). */
    fun villagerOf(giver: String, cast: List<Villager>): Villager? = cast.firstOrNull { it.name == giver }

    /**
     * Where a quest giver stands in this village: at their home when [cast] knows them, else by the
     * built-in table. Tutor quests from someone not in the cast wait at the fire.
     */
    fun questPlace(giver: String, source: QuestSource, state: GameState, cast: List<Villager> = emptyList()): TownPlace {
        villagerOf(giver, cast)?.let { return homeOf(it, state) }
        if (source == QuestSource.TUTOR) return TownPlace.Fire
        return giverPlaces[giver].orEmpty().firstOrNull { has(state, it) } ?: TownPlace.Fire
    }

    fun eventPlace(kind: EventKind, state: GameState): TownPlace = when (kind) {
        EventKind.WOLVES, EventKind.BEAR -> TownPlace.Forest
        EventKind.MERCHANT -> TownPlace.At(BuildingType.MARKET).takeIf { has(state, it) } ?: TownPlace.Fire
        EventKind.FESTIVAL -> TownPlace.At(BuildingType.LIPA).takeIf { has(state, it) } ?: TownPlace.Fire
        EventKind.STORM -> TownPlace.Fire
    }

    /**
     * Every marker for the village now: the event first, then who joined the village and waits to be met, open quests,
     * today's visitor, then happenings, and who is at a spot of the landscape now and has something to say ([keepers],
     * [Keepers.here]: the charcoal burner by his kopa).
     */
    fun of(
        state: GameState, active: List<ActiveHappening>, cast: List<Villager> = emptyList(), today: java.time.LocalDate = java.time.LocalDate.now(),
        keepers: List<ActiveKeeper> = emptyList(),
    ): List<TownMarker> {
        val out = ArrayList<TownMarker>()
        state.event?.let { e -> out += TownMarker("event:${e.id}", TownMarker.Kind.EVENT, e.kind.emoji, "${e.kind.sl} · ${e.kind.en}", eventPlace(e.kind, state)) }
        out += arrivals(state, cast, today)
        // a request from someone of the cast who isn't in the village today waits for them; at most two of each are up
        out += questMarkers(si.lanisce.lani.game.villagers.Residents.queue(state, cast, today).up, state, cast)
        // today's visitor waits with a bubble until Jan has greeted or met them today; then they're just about
        si.lanisce.lani.game.villagers.Residents.visitorToday(state, today)
            ?.takeIf { id -> state.bonds[id]?.seen != today.toString() }
            ?.let { id -> cast.firstOrNull { it.id == id } }?.let { v ->
            out += TownMarker("visitor:${v.id}", TownMarker.Kind.HAPPENING, v.emoji, "${bi("townMarkers.visiting")}: ${v.name}", homeOf(v, state))
        }
        for (a in active) {
            out += TownMarker("happening:${a.key}", TownMarker.Kind.HAPPENING, a.person?.emoji ?: a.happening.marker, a.happening.title, placeOf(a, state))
        }
        out += Keepers.markers(state, keepers, today)
        out += villageMarkers(state, today)
        return out
    }

    /**
     * One bubble per person for their requests up now ([up], [si.lanisce.lani.game.QuestQueue]): "quest:" and the first of
     * them, their look, all their titles, and how many ([TownMarker.count]); the requests of someone the cast doesn't know one
     * bubble per place the same way. Never two alike over one head.
     */
    fun questMarkers(up: List<si.lanisce.lani.game.Quest>, state: GameState, cast: List<Villager>): List<TownMarker> =
        up.groupBy { q -> villagerOf(q.giver, cast)?.let { "person:${it.id}" } ?: "place:${questPlace(q.giver, q.source, state, cast)}" }.values.map { mine ->
            val q = mine.first()
            val titles = mine.joinToString(", ") { it.title.substringBefore(" · ") }
            val label = when {
                mine.size == 1 -> q.title
                mine.all { it.giver == q.giver } -> "${q.giver}: $titles"
                else -> titles
            }
            TownMarker("quest:${q.id}", TownMarker.Kind.QUEST, q.emoji, label, questPlace(q.giver, q.source, state, cast), count = mine.size)
        }

    /**
     * "arrival:<id>" at the home of each who joined the village and whose introduction can be played now
     * ([si.lanisce.lani.game.villagers.Arrivals.ready]): 🧳 someone who moved in, 👶 a baby.
     */
    fun arrivals(state: GameState, cast: List<Villager>, today: java.time.LocalDate): List<TownMarker> =
        si.lanisce.lani.game.villagers.Arrivals.ready(state, today).mapNotNull { p ->
            val v = cast.firstOrNull { it.id == p.id } ?: si.lanisce.lani.game.villagers.Residents.villagerOf(p.resident, cast, today) ?: return@mapNotNull null
            TownMarker("arrival:${p.id}", TownMarker.Kind.HAPPENING, p.emoji, si.lanisce.lani.game.villagers.Arrivals.title(p, v), homeOf(v, state))
        }

    /**
     * The calendar's festival that is on (under the linden, else by the fire), the day's surprise at the road, and the
     * projects the village can take a step in today, at their site (the ones started first, at most [PROJECT_BUBBLES]).
     */
    fun villageMarkers(state: GameState, today: java.time.LocalDate): List<TownMarker> = buildList {
        for (d in si.lanisce.lani.game.Calendar.open(state, today)) {
            val place = TownPlace.At(BuildingType.LIPA).takeIf { has(state, it) } ?: TownPlace.Fire
            add(TownMarker("festival:${d.festival.id}", TownMarker.Kind.HAPPENING, d.festival.emoji, si.lanisce.lani.game.Calendar.todayText(d), place))
        }
        si.lanisce.lani.game.Surprises.today(state, today)?.takeIf { !it.done && si.lanisce.lani.game.Surprises.whoHere(state, it, today) }?.let { sp ->
            add(TownMarker("surprise:${sp.kind}", TownMarker.Kind.HAPPENING, si.lanisce.lani.game.Surprises.emoji(sp.kind), si.lanisce.lani.game.Surprises.title(sp), TownPlace.Spot("road")))
        }
        val steps = si.lanisce.lani.game.Projects.options(state, today).filter { o ->
            !o.finished && o.waiting != si.lanisce.lani.game.ProjectOption.Why.LEADER && o.waiting != si.lanisce.lani.game.ProjectOption.Why.AGE &&
                o.waiting != si.lanisce.lani.game.ProjectOption.Why.TODAY
        }.sortedByDescending { it.done > 0 }.take(PROJECT_BUBBLES)
        for (o in steps) add(TownMarker("project:${o.spec.id}", TownMarker.Kind.QUEST, o.spec.emoji, "${o.spec.name} ${o.done}/${o.steps}", projectPlace(o.spec, state)))
        // the treasure map by the fire, where the storyteller tells: waiting to be taken, or the hunt on
        if (state.treasure?.found?.isEmpty() == true) add(TownMarker(TREASURE, TownMarker.Kind.HAPPENING, "🗺️", bi("treasure.title"), TownPlace.Fire))
    }

    /** The treasure map's bubble (see [si.lanisce.lani.game.Treasure]): a tap opens the map. */
    const val TREASURE = "treasure:map"

    /** At most this many project bubbles on the map at once: the rest are in the projects sheet. */
    const val PROJECT_BUBBLES = 2

    /** Where a project stands in this village: the first of its sites that exists (see [si.lanisce.lani.game.ProjectSpec.site]). */
    fun projectPlace(p: si.lanisce.lani.game.ProjectSpec, state: GameState): TownPlace =
        p.site.map(TownPlace::of).firstOrNull { has(state, it) } ?: TownPlace.Fire
}
