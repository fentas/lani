package si.lanisce.lani.game.scene

import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.render.Noise

/**
 * The houses and their rooms, and the building a scene is seen in (companion/SCENES.md, "Houses and their rooms", "What the
 * village has built").
 *
 * **Rooms.** Every scene that opens from a house is a room of one ([isRoom]): Micka's kitchen, Stari Janez's living room,
 * France's workshop, Marko's wine cellar, Vida and Nejc's attic. A house opens one room, its own ([deal]): the rooms whose
 * household ([SceneSpec.household]) lives in the village go to the houses in the order their people moved in (the oldest
 * house to the first household), a room the hut already has (the kitchen, while a hut stands) last; a house left over
 * opens one of those by its id. So the same house opens the same room every time, and a new house gets the room of whoever
 * moved in next; a room whose people don't live here yet is in no house. The hut keeps the kitchen.
 *
 * **Seen in.** A room shows its own building's level ([SceneWorld.here]): the kitchen of a hut at level 1 bare, the one at
 * level 3 with the clock and the holy corner.
 */
object Homes {
    /** Whether [scene] is a room of a house: it opens from one. */
    fun isRoom(scene: SceneSpec): Boolean = "house" in scene.from

    /**
     * House id → its room, of [scenes] open in [state]'s village (see [Homes]). The houses in the order they were built
     * (the id breaks a tie); empty when there is no house or no room.
     */
    fun deal(scenes: List<SceneSpec>, state: GameState): Map<String, SceneSpec> {
        val houses = state.buildings.filter { it.type == BuildingType.HOUSE }.sortedWith(compareBy({ it.builtAt }, { it.id }))
        if (houses.isEmpty()) return emptyMap()
        val rooms = scenes.filter { isRoom(it) && Happenings.open(it, state) }.distinctBy { it.id }
        if (rooms.isEmpty()) return emptyMap()
        // when each household moved in: its first resident's place in the village's list (they are kept in order); without
        // the cast's names (an older bridge) everyone counts, in the scenes' order
        val arrived = state.residents.withIndex().associate { it.value.id to it.index }
        fun since(r: SceneSpec): Int? = when {
            r.household.isEmpty() -> Int.MAX_VALUE // anyone's: after the households
            state.residents.isEmpty() -> 0
            else -> r.household.mapNotNull { arrived[it] }.minOrNull()
        }
        val hut = state.buildings.any { it.type == BuildingType.HUT }
        // the hut's own room (the kitchen) comes after the others while a hut stands: the hut opens it already
        val lived = rooms.mapNotNull { r -> since(r)?.let { Triple(r, if (hut && "hut" in r.from) 1 else 0, it) } }
            .sortedWith(compareBy({ it.second }, { it.third })).map { it.first }
        val pool = lived.ifEmpty { return emptyMap() }
        val out = LinkedHashMap<String, SceneSpec>()
        for ((i, h) in houses.withIndex()) out[h.id] = if (i < pool.size) pool[i] else pool[Math.floorMod(Noise.hash(h.id.hashCode(), 57), pool.size)]
        return out
    }

    /**
     * The scenes a tap on [building] opens: a house its own room ([deal]); any other building every scene that opens from
     * its type, as before.
     */
    fun scenesOf(building: Building, scenes: List<SceneSpec>, state: GameState): List<SceneSpec> {
        // a house opens its own room; none when nobody of any room's household lives here yet
        if (building.type == BuildingType.HOUSE && scenes.any { isRoom(it) && Happenings.open(it, state) }) return listOfNotNull(deal(scenes, state)[building.id])
        val key = building.type.name.lowercase()
        return scenes.filter { key in it.from && Happenings.open(it, state) }
    }

    /**
     * Whether [scene] has a place in [state]'s village: a room of a house is in one ([dealt]: [deal]'s answer) or opens
     * from another building that stands (the kitchen from the hut); any other scene whenever it is open.
     */
    fun placed(scene: SceneSpec, state: GameState, dealt: Map<String, SceneSpec>): Boolean {
        if (!isRoom(scene) || dealt.isEmpty()) return true
        if (dealt.values.any { it.id == scene.id }) return true
        return scene.from.any { f -> f != "house" && (TownPlace.of(f) as? TownPlace.At)?.let { at -> state.buildings.any { it.type == at.type } } == true }
    }

    /**
     * The building [scene] is seen in: [opened] (a building's id: the hut or the house the learner tapped) when it stands
     * and the scene opens from its type; else the one its place is, where its bubbles show ([TownMarkers.placeOf]): of its
     * places in order, a house's room the first house that has it ([dealt]), another type's the first of that type that
     * stands (the lowest plot). Null for a scene of no building (the fire, a spot).
     */
    fun buildingOf(scene: SceneSpec, state: GameState, opened: String? = null, dealt: Map<String, SceneSpec> = emptyMap()): Building? {
        val types = scene.from.mapNotNull { (TownPlace.of(it) as? TownPlace.At)?.type }
        opened?.let { id -> state.buildings.firstOrNull { it.id == id && it.type in types }?.let { return it } }
        for (t in types) {
            if (t == BuildingType.HOUSE && dealt.isNotEmpty()) {
                val home = dealt.entries.firstOrNull { it.value.id == scene.id }?.key ?: continue
                state.buildings.firstOrNull { it.id == home }?.let { return it }
                continue
            }
            state.buildings.filter { it.type == t }.minByOrNull { if (it.plot >= 0) it.plot else Int.MAX_VALUE }?.let { return it }
        }
        return null
    }

    /** [state]'s village as [scene] sees it: seen in its building ([buildingOf]; [scenes]: every scene, for the houses' rooms). */
    fun world(scene: SceneSpec, state: GameState, opened: String? = null, scenes: List<SceneSpec> = emptyList()): SceneWorld =
        SceneWorld.of(state, buildingOf(scene, state, opened, if (isRoom(scene)) deal(scenes, state) else emptyMap()))
}
