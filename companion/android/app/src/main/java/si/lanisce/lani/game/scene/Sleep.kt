package si.lanisce.lani.game.scene

import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.villagers.Arrivals
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.Where
import java.time.LocalDate

// At night people sleep in their beds (companion/SCENES.md, "Asleep at night"): Pastir Luka in the sleeping bag in the
// tent, Babica Micka on the warm bench by the tiled stove in the kitchen, "za pečjo", Ančka and Zala beside her, Stari
// Janez on the bench round his stove and Lovec Jože on its other run, Vida and Nejc in the attic, France and Tine in the
// workshop, Marko by his barrels and Čebelar Anton on a trestle bed by the cellar's steps, Kovač Tone on his cot by the forge,
// Učiteljica Mojca in her curtained bed in the school. A painter says where its beds are ([ScenePainter.beds]); who sleeps
// in which house is the households', the villagers' files' ([Villager.sleeps]) and their homes' ([bedrooms]); when, the
// villager's day ([Routine]).

/**
 * Who sleeps where, and what a tap on a sleeper does. Pure: the same village, cast and minute always give the same
 * sleepers.
 */
object Sleep {
    /** A tap on a sleeper: they turn over and the Zzz grows (s). */
    const val TURN_S = 2.4

    /** A second tap soon after: a sleepy mumble, "Pusti me spati …" (s). */
    const val MUMBLE_S = 3.6

    /** Then they rest this long before a tap turns them over again (s). */
    const val REST_S = 8.0

    private const val PREFIX = "zzz:"

    /** The poke of the sleeper [personId] (a person of the scene): its id in [SceneFrame.pokes]. */
    fun pokeId(personId: String): String = PREFIX + personId

    /** Whose poke [pokeId] is, when it is a sleeper's; null for any other. */
    fun sleeperOf(pokeId: String): String? = pokeId.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

    /** What a tap on the sleeper [personId] sets off: they turn over; tapped again within a few seconds, they mumble. */
    fun poke(personId: String): Poke = Poke(pokeId(personId), listOf(TURN_S, MUMBLE_S), rest = REST_S)

    /** Whether the time of day is the night ([TimeOfDay.NIGHT], 22–5): a place with a bed is quiet then (see [Happenings.on]). */
    fun night(time: TimeOfDay): Boolean = time == TimeOfDay.NIGHT

    /** The beds of [art]'s painter, where its sleepers lie ([ScenePainter.beds]); none for most places. */
    fun beds(art: String): List<String> = ScenePainters.of(art).beds

    /** Which of [art]'s beds are a child's ([ScenePainter.childBeds]). */
    fun childBeds(art: String): Set<String> = ScenePainters.of(art).childBeds

    /** Whether [art] is a place where someone sleeps: it is quiet there at night (see [Happenings.on]). */
    fun bedded(art: String): Boolean = beds(art).isNotEmpty()

    /** The buildings someone can sleep in: the dwellings, and the workplaces with a bed (the smith's cot by his forge, the teacher's in the school). */
    private val ROOFS = setOf(BuildingType.TENT, BuildingType.HUT, BuildingType.HOUSE, BuildingType.SMITHY, BuildingType.SCHOOL, BuildingType.MARKET, BuildingType.BEEHIVE)

    /** Where someone without a roof of their own sleeps: the first of these that stands. */
    private val SHELTER = listOf(BuildingType.HUT, BuildingType.HOUSE, BuildingType.TENT)

    /**
     * Where someone who lives in [home] ([si.lanisce.lani.game.villagers.Villager.home], best first) goes to bed: the first
     * building of it that stands in the village (Babica Micka's house or hut, Luka's tent). A spot of the landscape is no bed:
     * someone at home on the meadow by day sleeps in the first building of their list.
     */
    fun bedroom(home: List<String>, state: GameState): BuildingType? =
        home.asSequence()
            .mapNotNull { h -> BuildingType.entries.firstOrNull { it.name.equals(h, ignoreCase = true) } }
            .firstOrNull { t -> state.buildings.any { it.type == t } }

    /** Where someone sleeps: the [building] (null: nowhere yet, under the stars by the fire) and the [scene] with their bed (null: none shows it). */
    data class Bedroom(val building: Building?, val scene: SceneSpec?)

    /**
     * Where each of [people] sleeps in [state]'s village (villager id → [Bedroom]), from [scenes]:
     *
     * 1. a household sleeps in its room: the house dealt their room ([Homes.deal]: Janez in the living room of his house,
     *    Vida and Nejc in the attic, France and Tine in the workshop, Marko in the cellar);
     * 2. who says where they sleep ([Villager.sleeps]) sleeps there once it stands: a building ("smithy": Tone, "school":
     *    Mojca) as in 3, or a room of a house ("house:livingroom": Jože with Janez, "house:cellar": Anton with Marko), in the
     *    house dealt that room and a bed of its scene; while no house has the room, in a house, where we don't see them;
     * 3. the others in the first building of their home that has a roof ([ROOFS]: Luka's tent, Micka's house or hut, the
     *    smithy, the school, the bee house); the cast whose home lists it sleep in the scene with beds that opens from it and
     *    is nobody's household (the tent, the kitchen: Micka, Ančka and Zala; the smithy: Tone; the school: Mojca), in the
     *    building that scene is seen in;
     * 4. anyone else in the first hut, house or tent that stands; with none, nowhere (by the fire).
     */
    fun bedrooms(scenes: List<SceneSpec>, state: GameState, people: List<Villager>): Map<String, Bedroom> {
        val dealt = Homes.deal(scenes, state)
        val out = LinkedHashMap<String, Bedroom>()
        val ids = people.map { it.id }.toSet()
        for ((houseId, room) in dealt) {
            val house = state.buildings.firstOrNull { it.id == houseId } ?: continue
            for (id in room.household) if (id in ids && id !in out) out[id] = Bedroom(house, room.takeIf { bedded(it.art) })
        }
        val roomed = dealt.values.flatMap { it.household }.toSet()
        for (v in people) {
            if (v.id in out) continue
            v.sleeps?.let { sleeps(it, v, scenes, state, dealt, roomed) }?.let { out[v.id] = it; continue }
            val type = v.home.asSequence()
                .mapNotNull { h -> roof(h) }
                .firstOrNull { t -> state.buildings.any { it.type == t } }
            out[v.id] = if (type == null) Bedroom(SHELTER.firstNotNullOfOrNull { t -> first(state, t, dealt) }, null)
            else under(type, v, scenes, state, dealt, roomed)
        }
        return out
    }

    /** The building type with a roof [key] names ("smithy", "House"); null for any other word. */
    private fun roof(key: String): BuildingType? = BuildingType.entries.firstOrNull { it.name.equals(key, ignoreCase = true) }?.takeIf { it in ROOFS }

    /**
     * [v]'s bed under the roof of a [type] that stands: in the scene with beds that opens from it and is nobody's household
     * (not for a made-up newcomer, nor someone with a room of their own), in the building that scene is seen in; else in the
     * first building of the type, where we don't see them.
     */
    private fun under(type: BuildingType, v: Villager, scenes: List<SceneSpec>, state: GameState, dealt: Map<String, SceneSpec>, roomed: Set<String>): Bedroom {
        val key = type.name.lowercase()
        val scene = if (v.source == "village" || v.id in roomed) null else scenes.firstOrNull { s ->
            bedded(s.art) && s.household.isEmpty() && key in s.from && Happenings.open(s, state) && Homes.placed(s, state, dealt)
        }
        return Bedroom(scene?.let { Homes.buildingOf(it, state, null, dealt) } ?: first(state, type, dealt), scene)
    }

    /**
     * Where [v] sleeps by their file's [spec] ([Villager.sleeps]): "smithy" under that roof ([under]); "house:cellar" in the
     * house dealt the cellar ([Homes.deal]), in a bed of its scene, or in the scene with that art that opens from another
     * roof ("hut:kitchen"); while no building has the room, in the first of its type, unseen. Null when none of the type
     * stands, or [spec] names no roof: then their home decides.
     */
    private fun sleeps(spec: String, v: Villager, scenes: List<SceneSpec>, state: GameState, dealt: Map<String, SceneSpec>, roomed: Set<String>): Bedroom? {
        val type = roof(spec.substringBefore(':').trim()) ?: return null
        if (state.buildings.none { it.type == type }) return null
        val art = spec.substringAfter(':', "").trim().ifEmpty { return under(type, v, scenes, state, dealt, roomed) }
        if (type == BuildingType.HOUSE) {
            dealt.entries.firstOrNull { it.value.art == art }?.let { (houseId, room) ->
                state.buildings.firstOrNull { it.id == houseId }?.let { return Bedroom(it, room.takeIf { bedded(art) }) }
            }
        } else {
            val key = type.name.lowercase()
            scenes.firstOrNull { s -> s.art == art && key in s.from && Happenings.open(s, state) && Homes.placed(s, state, dealt) }?.let { s ->
                return Bedroom(Homes.buildingOf(s, state, null, dealt) ?: first(state, type, dealt), s.takeIf { bedded(art) })
            }
        }
        return Bedroom(first(state, type, dealt), null)
    }

    /** The first building of [type] that stands (the lowest plot); of the houses one whose room is nobody's household first. */
    private fun first(state: GameState, type: BuildingType, dealt: Map<String, SceneSpec>): Building? {
        val all = state.buildings.filter { it.type == type }.sortedBy { if (it.plot >= 0) it.plot else Int.MAX_VALUE }
        return all.firstOrNull { b -> dealt[b.id]?.household.isNullOrEmpty() } ?: all.firstOrNull()
    }

    /** Where each of [people] goes home to on the map: their bedroom's building ([bedrooms]); none for who has none. */
    fun homes(scenes: List<SceneSpec>, state: GameState, people: List<Villager>): Map<String, TownPlace> =
        bedrooms(scenes, state, people).mapNotNull { (id, b) -> b.building?.let { id to TownPlace.of(it, state) } }.toMap()

    /**
     * Who sleeps in [scene] at [minute] of [date] (see [Routine.now]): the villagers of [people] whose bed is in it
     * ([bedrooms]), who live in the village and have been met, and are in bed now ([Where.ASLEEP]: not on a ritual, not on
     * their way home, not up for a happening, which [day] names in [Routine.Day.busy]). One to a bed of [beds], each their
     * own: the household first, then who lodges with them ([Villager.sleeps]: Jože with Janez), each in their arrival order;
     * a child in a child's bed ([childBeds]) first, else any; a grown-up never in a child's. Someone still up tonight keeps
     * theirs ([Routine.toBed]: Janez at the fire, Jože doesn't take his place on the zapeček); one up for the day, or out
     * for the night, leaves it to the next (Ančka moves onto the warm bench when Micka gets up). Each is the scene's person
     * for them (their slot the bed, lying: [Pose.SLEEP]), or, not one of the scene's people, themselves.
     */
    fun sleepers(
        scene: SceneSpec,
        state: GameState,
        date: LocalDate,
        minute: Double,
        people: List<Villager>,
        scenes: List<SceneSpec>,
        day: Routine.Day = Routine.Day(),
        beds: List<String> = beds(scene.art),
        childBeds: Set<String> = childBeds(scene.art),
        month: Int = date.monthValue,
        bedrooms: Map<String, Bedroom> = bedrooms(scenes, state, people),
    ): List<PersonInScene> {
        if (beds.isEmpty()) return emptyList()
        // who lives here: an older village without named residents has everyone at home
        val living = state.residents.map { it.id }.toSet().takeIf { it.isNotEmpty() }
        val homes = day.homes.ifEmpty { bedrooms.mapNotNull { (id, b) -> b.building?.let { id to TownPlace.of(it, state) } }.toMap() }
        val today = day.copy(homes = homes)
        val asleep = HashSet<String>()
        // whose bed is here tonight: who is in it, and who is still up and comes to it later
        val claims = people.filter { v ->
            if (bedrooms[v.id]?.scene?.id != scene.id || (living != null && v.id !in living) || !Arrivals.met(state, v.id)) return@filter false
            if (Routine.now(v, state, date, minute, month, today).where == Where.ASLEEP) asleep.add(v.id) else Routine.toBed(v, state, date, minute, today)
        }.sortedWith(compareBy({ it.id !in scene.household }, { it.order }))
        val free = beds.toMutableList()
        val bedOf = HashMap<String, String>()
        fun lay(v: Villager, bed: String?) {
            bed ?: return
            free -= bed
            bedOf[v.id] = bed
        }
        val (children, grown) = claims.partition { Routine.kindOf(it) == Routine.Kind.CHILD }
        // the children in the children's beds, the grown-ups in the others, then a child left over wherever there's room
        val over = children.filter { c -> free.firstOrNull { it in childBeds }?.also { lay(c, it) } == null }
        for (v in grown) lay(v, free.firstOrNull { it !in childBeds })
        for (c in over) lay(c, free.firstOrNull())
        return claims.filter { it.id in asleep }.mapNotNull { v ->
            val bed = bedOf[v.id] ?: return@mapNotNull null
            val p = scene.people.firstOrNull { it.villager == v.id }
            PersonInScene(p?.id ?: v.id, p?.art ?: v.art, bed, pose = Pose.SLEEP)
        }.sortedBy { beds.indexOf(it.slot) }
    }

    /** [people] (who is in the scene, see [Happenings.peopleIn]) with the [sleepers] in their beds instead of about the place. */
    fun inBed(people: List<PersonInScene>, sleepers: List<PersonInScene>): List<PersonInScene> =
        if (sleepers.isEmpty()) people else people.filter { p -> sleepers.none { it.id == p.id } } + sleepers
}
