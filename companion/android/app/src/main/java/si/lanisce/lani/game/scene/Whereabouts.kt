package si.lanisce.lani.game.scene

import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.villagers.Doing
import si.lanisce.lani.game.villagers.Reactions
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.Where
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The village's day as the map and the scenes both see it ([Routine]): one [Routine.Day] for both, so someone hoeing the
 * field on the map is in the field's scene, and someone in bed on the map lies in their bed in their home's scene.
 */
object Whereabouts {
    /** The storytellers of [scenes] (their villager id) and the key of their story's happening ("janez" → "ob-ognju/zgodba"). */
    fun tellers(scenes: List<SceneSpec>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (s in scenes) for (h in s.happenings) if (h.stories) {
            val v = s.people.firstOrNull { it.id == h.who }?.villager ?: continue
            out.putIfAbsent(v, "${s.id}/${h.id}")
        }
        return out
    }

    /**
     * Who waits for the learner by daylight, and where: the giver of an open request at its place, who joined and waits to
     * be met at their home, today's visitor until the learner has greeted them: the people behind the village's bubbles
     * ([TownMarkers.of]) that aren't a happening's.
     */
    fun calls(state: GameState, cast: List<Villager>, today: LocalDate): Map<String, TownPlace> {
        val out = LinkedHashMap<String, TownPlace>()
        for (m in TownMarkers.of(state, emptyList(), cast, today)) {
            val id = when {
                m.id.startsWith("quest:") -> state.quests.firstOrNull { it.id == m.id.removePrefix("quest:") }?.let { q -> TownMarkers.villagerOf(q.giver, cast)?.id }
                m.id.startsWith("arrival:") -> m.id.removePrefix("arrival:")
                m.id.startsWith("visitor:") -> m.id.removePrefix("visitor:")
                else -> null
            } ?: continue
            out.putIfAbsent(id, m.place)
        }
        return out
    }

    /**
     * The day of [state]'s village for [people] (who live here): where each sleeps ([Sleep.homes]), the storytellers of
     * [scenes], when tonight's story was heard ([storyAt]), who waits for the learner ([calls], of [cast] on [today]) and
     * who has a happening on ([active]: [TownMarkers.standing]); the village's day shared out among those of [people] who
     * live here and today's visitor ([village]).
     */
    fun day(
        scenes: List<SceneSpec>, state: GameState, cast: List<Villager>, people: List<Villager>, today: LocalDate,
        storyAt: LocalDateTime? = null, active: List<ActiveHappening> = emptyList(),
    ): Routine.Day = Routine.Day(
        homes = Sleep.homes(scenes, state, people),
        tellers = tellers(scenes),
        storyAt = storyAt,
        calls = calls(state, cast, today),
        busy = TownMarkers.standing(state, active),
        people = village(state, people, today),
    )

    /**
     * Of [people], whose day the village shares out ([Routine.Day.people]): who lives here and today's visitor, not the babies
     * (they're carried) nor the strangers at the road. Everyone when nobody lives here yet (an older bridge without the cast).
     */
    fun village(state: GameState, people: List<Villager>, today: LocalDate): List<Villager> {
        val here = si.lanisce.lani.game.villagers.Residents.living(state, today)
        return people.filter { (here == null || it.id in here) && it.art != "baby" && it.art != "pedlar" && it.art != "pilgrim" }.distinctBy { it.id }
    }

    /**
     * The people of [scene] who are at its place now by their day ([Routine.now] at [at]): out there, arrived (not still on
     * their way: [Routine.WALK_S], as long as the map's walk takes at most; not away on an errand), at the building the scene
     * is seen in ([seenIn]: the hut or the house it was opened from; else a house's room the house it is dealt to). France
     * hoeing the field is in the field; Janez under the linden on the square. Each at their spot, as the scene has them.
     *
     * While wolves, a bear or a storm are about, who is where the reaction has them ([reacting]): by the fire, up the
     * watchtower, at home in their room.
     */
    fun inScene(
        scene: SceneSpec, state: GameState, people: List<Villager>, at: LocalDateTime, day: Routine.Day, scenes: List<SceneSpec> = emptyList(),
        seenIn: si.lanisce.lani.game.Building? = null,
    ): List<PersonInScene> {
        val place = placeOf(scene, state, scenes, seenIn)
        state.event?.takeIf { Reactions.quieting(it.kind) }?.let { e -> return reacting(scene, state, people, at, day, place, e) }
        return scene.people.mapNotNull { p ->
            val v = p.villager?.let { id -> people.firstOrNull { it.id == id } } ?: return@mapNotNull null
            val now = Routine.now(v, state, at, day)
            val there = now.where == Where.OUT && now.doing != Doing.CARRY && now.errand == null && (now.from == null || now.since >= Routine.WALK_S) &&
                now.place != null && (now.place == place || (!Homes.isRoom(scene) && now.place.general() == place.general()))
            if (there) PersonInScene(p.id, p.art, p.slot) else null
        }
    }

    /**
     * Where [scene] is in [state]'s village: the building it is seen in ([seenIn]), else a house's room the house it is
     * dealt to ([Homes.deal]), else its place ([TownMarkers.placeOf]).
     */
    fun placeOf(scene: SceneSpec, state: GameState, scenes: List<SceneSpec> = emptyList(), seenIn: si.lanisce.lani.game.Building? = null): TownPlace {
        seenIn?.takeIf { b -> state.buildings.any { it.id == b.id } }?.let { return TownPlace.of(it, state) }
        val here = if (Homes.isRoom(scene)) Homes.deal(scenes, state).entries.firstOrNull { it.value.id == scene.id }?.key?.let { id ->
            state.buildings.firstOrNull { it.id == id }?.let { TownPlace.of(it, state) }
        } else null
        return here ?: TownMarkers.placeOf(scene, state)
    }

    /**
     * Where [v] is while [e] (wolves, a bear, a storm: [Reactions.quieting]) is on, at [at] of [day], as the map has them
     * (companion/GAME.md "People react"): who is up (out on the map by their day) goes where their stance sends
     * them ([Reactions.placeOf]: by the fire, up the watchtower, home; in a storm everyone home); the defenders and the
     * lookout get up for it from wherever they are; who is in the house stays in, at home ([Routine.Day.homes]); who is
     * asleep stays in bed (in no scene but their bed's: [Sleep]). Null: nowhere a scene shows (a defender at the forest's
     * edge, asleep, no home), or no stance (not one of the village's).
     */
    fun during(v: Villager, e: si.lanisce.lani.game.GameEvent, stances: Map<String, si.lanisce.lani.game.villagers.Stance>, state: GameState, at: LocalDateTime, day: Routine.Day): TownPlace? {
        val st = stances[v.id] ?: return null
        val getsUp = Reactions.threat(e.kind) && (st == si.lanisce.lani.game.villagers.Stance.LOOKOUT || st == si.lanisce.lani.game.villagers.Stance.DEFENDER)
        val now = Routine.now(v, state, at, day)
        if (now.where == Where.ASLEEP && !getsUp) return null
        val home = day.homes[v.id]
        // out on the map (not in the house for lunch or the housework, not home already): where the reaction sends them
        val up = now.where == Where.OUT && now.doing != Doing.INSIDE
        return if (up || getsUp) Reactions.placeOf(e.kind, st, home) else home
    }

    /**
     * The spots where more of the village than [scene]'s own people can be while the village reacts, after the art's person
     * spots: by the fire beside the stump and in front of the woodpile, standing. Each with its stance.
     */
    private val GATHER: Map<String, List<Pair<String, Stance>>> = mapOf(
        "campfire" to listOf("behind-stump" to Stance.STAND, "behind-logs" to Stance.STAND),
    )

    /**
     * Who of [people] is at [place] (where [scene] is) while [e] is on ([during]): the scene's own people at their spots, then
     * whoever else the reaction has there (in a room the household first, then the cast, then the village's own people)
     * at the art's free person spots and its gathering spots ([GATHER]), as many as there are: the children and the old by
     * the fire, the lookout up the watchtower, everyone at home in the storm (Babica Micka, Teta Ančka and Zala in the
     * kitchen, Stari Janez and Lovec Jože in the living room). A room is its own house's, a place of the village else.
     */
    private fun reacting(scene: SceneSpec, state: GameState, people: List<Villager>, at: LocalDateTime, day: Routine.Day, place: TownPlace, e: si.lanisce.lani.game.GameEvent): List<PersonInScene> {
        val room = Homes.isRoom(scene)
        val stances = Reactions.stances(state, people, e.kind, e.strength)
        val here = people.filter { v -> during(v, e, stances, state, at, day)?.let { if (room) it == place else it.general() == place.general() } == true }
            .distinctBy { it.id }
        if (here.isEmpty()) return emptyList()
        val out = ArrayList<PersonInScene>()
        val taken = HashSet<String>()
        val spots = SceneArt.personSlots[scene.art].orEmpty().map { it to Stance.STAND } + GATHER[scene.art].orEmpty()
        fun free(): Pair<String, Stance>? = spots.firstOrNull { it.first !in taken }
        // the scene's own people at their spots (one at each: a second for the same spot takes a free one)
        val placed = HashSet<String>()
        for (p in scene.people) {
            val v = p.villager?.let { id -> here.firstOrNull { it.id == id } } ?: continue
            if (v.id in placed) continue
            val spot = if (p.slot !in taken) p.slot to Stance.STAND else free() ?: continue
            taken += spot.first; placed += v.id
            out += PersonInScene(p.id, p.art, spot.first, stance = spot.second)
        }
        // then the others the reaction has here, as themselves: the household first, the cast, the village's own people
        val others = here.filter { it.id !in placed && scene.people.none { p -> p.id == it.id } }
            .sortedWith(compareBy({ it.id !in scene.household }, { it.source == "village" }, { it.order }))
        for (v in others) {
            val spot = free() ?: break
            taken += spot.first
            out += PersonInScene(v.id, v.art, spot.first, stance = spot.second)
        }
        return out
    }
}
