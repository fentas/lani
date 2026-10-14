package si.lanisce.lani.game.scene

import si.lanisce.lani.data.Course
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.School
import si.lanisce.lani.game.villagers.Villager

/**
 * Something someone in a scene has for the learner now (companion/SCENES.md, "What someone has here"): what the village's
 * bubbles over them promise, and the class's lesson.
 */
sealed interface Offer {
    /** The class's lesson ([School.lesson]): the teacher's, where the class meets. */
    data class Lesson(val lesson: School.Lesson) : Offer

    /** Their happening here that opens something: a dialog, or the storyteller's story tonight. */
    data class Talk(val happening: ActiveHappening) : Offer

    /** One of their requests up now, as the map's bubble over them counts them ([TownMarkers.questMarkers]). */
    data class Request(val quest: Quest) : Offer
}

/**
 * What the people of a scene have for the learner (companion/SCENES.md, "What someone has here"; GAME.md "The school"): the
 * same as the village's bubbles over them, from the same sources (the happenings on, the requests up:
 * [si.lanisce.lani.game.villagers.Residents.queue]), and the class's lesson in the scene where the class meets. Pure.
 */
object SceneOffers {
    /** The thing in the school's scene that leads to the class's units: the blackboard. */
    const val BLACKBOARD = "blackboard"

    /** Where the class meets now ([School.place]), as a place of the village: the school, the linden, the fire. */
    fun classPlace(s: GameState): TownPlace = when (School.place(s)) {
        School.Place.SCHOOL -> TownPlace.At(BuildingType.SCHOOL)
        School.Place.LINDEN -> TownPlace.At(BuildingType.LIPA)
        School.Place.FIRE -> TownPlace.Fire
    }

    /**
     * Whether the class meets in [scene]: the village's language has a [course], the class has begun (the hamlet on:
     * [School.open]) and the scene is at the class's place in this village ([classPlace]: the school's scene once it stands,
     * the linden's before, the fire's before that). Nowhere before the hamlet, nor without a course.
     */
    fun classHere(scene: SceneSpec, s: GameState, course: Course?): Boolean =
        course != null && School.open(s) && !Homes.isRoom(scene) && TownMarkers.placeOf(scene, s) == classPlace(s)

    /** The class's lesson in [scene] ([School.lesson]), while the class meets there ([classHere]); null elsewhere, or all done. */
    fun lesson(scene: SceneSpec, s: GameState, course: Course?): School.Lesson? =
        if (course != null && classHere(scene, s, course)) School.lesson(s, course) else null

    /** Whether a tap on thing [slot] leads to the class's units: the blackboard, where the class meets ([classHere]). */
    fun opensSchool(slot: String, classHere: Boolean): Boolean = classHere && slot == BLACKBOARD

    /**
     * The requests up ([up]: [si.lanisce.lani.game.villagers.Residents.queue]'s) by the villager of [cast] who asks them, as
     * the map shows them in one bubble over each ([TownMarkers.questMarkers]: its count); a request of someone [cast]
     * doesn't know is nobody's here.
     */
    fun requestsBy(up: List<Quest>, cast: List<Villager>): Map<String, List<Quest>> {
        val out = LinkedHashMap<String, MutableList<Quest>>()
        for (q in up) TownMarkers.villagerOf(q.giver, cast)?.let { v -> out.getOrPut(v.id) { ArrayList() } += q }
        return out
    }

    /**
     * What [p] has in this scene now, in order: the class's [lesson] when [p] is the [teacher] (villager id) and the class
     * meets here, their happenings of [here] that open something ([opens]), their requests of [requests] ([requestsBy]).
     */
    fun of(
        p: ScenePerson, here: List<ActiveHappening>, opens: (ActiveHappening) -> Boolean, requests: Map<String, List<Quest>>,
        lesson: School.Lesson?, teacher: String?,
    ): List<Offer> = buildList {
        val v = p.villager
        if (lesson != null && v != null && v == teacher) add(Offer.Lesson(lesson))
        for (a in here) if (a.person?.id == p.id && opens(a)) add(Offer.Talk(a))
        if (v != null) for (q in requests[v].orEmpty()) add(Offer.Request(q))
    }

    /**
     * The emoji of the marker over someone with [offers] (one marker each): 📚 while the class's lesson waits, else their
     * first happening's, else 📋 for their requests; null with nothing.
     */
    fun marker(offers: List<Offer>): String? = when (val o = offers.firstOrNull()) {
        null -> null
        is Offer.Lesson -> "📚"
        is Offer.Talk -> o.happening.happening.marker
        is Offer.Request -> "📋"
    }

    /** How many requests [offers] has: the marker's count, as the map's bubble has it. */
    fun requests(offers: List<Offer>): Int = offers.count { it is Offer.Request }

    /**
     * Whether a tap on someone with [offers] takes the one straight away (a dialog, a request) rather than showing the
     * choice of them: only one, and not the lesson (its choice has the class's units besides).
     */
    fun direct(offers: List<Offer>): Offer? = offers.singleOrNull()?.takeIf { it !is Offer.Lesson }
}
