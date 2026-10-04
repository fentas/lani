package si.lanisce.lani.game.scene

import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.villagers.Doing
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
     * who has a happening on ([active]: [TownMarkers.standing]).
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
    )

    /**
     * The people of [scene] who are at its place now by their day ([Routine.now] at [at]): out there, arrived (not still on
     * their way: [Routine.WALK_S], as long as the map's walk takes at most; not away on an errand), at the building the scene
     * is seen in (a house's room: the house it is dealt to). France hoeing the field is in the field; Janez under the linden
     * on the square. Each at their spot, as the scene has them.
     */
    fun inScene(scene: SceneSpec, state: GameState, people: List<Villager>, at: LocalDateTime, day: Routine.Day, scenes: List<SceneSpec> = emptyList()): List<PersonInScene> {
        val here = if (Homes.isRoom(scene)) Homes.deal(scenes, state).entries.firstOrNull { it.value.id == scene.id }?.key?.let { id ->
            state.buildings.firstOrNull { it.id == id }?.let { TownPlace.of(it, state) }
        } else null
        val place = here ?: TownMarkers.placeOf(scene, state)
        return scene.people.mapNotNull { p ->
            val v = p.villager?.let { id -> people.firstOrNull { it.id == id } } ?: return@mapNotNull null
            val now = Routine.now(v, state, at, day)
            val there = now.where == Where.OUT && now.doing != Doing.CARRY && now.errand == null && (now.from == null || now.since >= Routine.WALK_S) &&
                now.place != null && (now.place == place || (!Homes.isRoom(scene) && now.place.general() == place.general()))
            if (there) PersonInScene(p.id, p.art, p.slot) else null
        }
    }
}
