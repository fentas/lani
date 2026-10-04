package si.lanisce.lani.game.scene

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.villagers.Mentions
import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A happening that is on now, in its scene, with the person it's about; [home]: the building its scene is seen in, a
 * house's room the house that has it ([Homes.buildingOf]), where its bubble shows; [dialog]: the variant it plays today
 * ([DialogVariants.pick]; null: the happening's [Happening.dialog]).
 */
data class ActiveHappening(
    val scene: SceneSpec, val happening: Happening, val person: ScenePerson?, val home: String? = null, val dialog: String? = null,
) {
    /** Stable id: scene/happening. Also the key in [GameState.happeningsDone] and [GameState.dialogsHeard]. */
    val key: String get() = "${scene.id}/${happening.id}"

    /** Whether its person has something to say: a dialog (a storyteller's story isn't one). */
    val talks: Boolean get() = (dialog ?: happening.dialog) != null
}

/**
 * Which scenes are open and which happenings are on. Pure and deterministic: the same village, day and
 * time of day always give the same answer, so the village doesn't reshuffle while the learner looks at it.
 */
object Happenings {
    /** At most this many happenings at once, so the village doesn't turn into a notice board. */
    const val MAX_ACTIVE = 4

    /**
     * Whether [scene] can be entered: its place exists in the village (the fire, the forest and the landscape's spots always
     * do; a project's landmark once the project is finished) and the age is reached.
     */
    fun open(scene: SceneSpec, state: GameState): Boolean {
        if (!reached(scene.needs, state.age)) return false
        return scene.from.any { place ->
            place == "fire" || place == "forest" || place.startsWith("spot:") || state.buildings.any { it.type.name.equals(place, ignoreCase = true) } ||
                (place.startsWith("project:") && TownMarkers.has(state, TownPlace.of(place)))
        }
    }

    /**
     * Whether happening [h] of [scene] is meant for guests only (companion/SCENES.md, "Dialogs for guests"): it says so, or
     * its dialog does. The village's own learner never meets it; a visitor from a linked town does.
     */
    fun forGuests(scene: SceneSpec, h: Happening): Boolean =
        h.guests || DialogVariants.of(h).let { ids -> ids.isNotEmpty() && scene.dialogs.any { it.id in ids && it.guests } }

    /**
     * The happenings on at [now], at most [MAX_ACTIVE], those finished today left out. [guest]: a visitor sees them in a
     * linked town, the ones meant for guests too; at home those never show. The storyteller's evening story
     * ([Happening.stories]) comes first: there is one every evening. [cast]: the village's villagers, whose names a
     * dialog may say ([Mentions]; on a visit, the host's).
     */
    fun active(
        scenes: List<SceneSpec>, state: GameState, now: LocalDateTime, guest: Boolean = false, cast: List<Villager> = Mentions.cast,
    ): List<ActiveHappening> {
        val today = now.toLocalDate()
        val time = TimeOfDay.of(now.hour)
        val dawn = TimeOfDay.isDawn(now.hour, now.monthValue)
        val world = SceneWorld.of(state)
        val busy = HashSet<String>() // one happening per person at a time
        // a house's room is in the house it is dealt to (none: its people don't live here yet), and each scene is seen in
        // its building: a room's happenings go with its level (Micka's Sunday lunch wants the table)
        val dealt = Homes.deal(scenes, state)
        val homes = HashMap<String, Building?>()
        fun home(s: SceneSpec) = homes.getOrPut(s.id) { Homes.buildingOf(s, state, null, dealt) }
        // the storyteller waits by the fire for the learner until half past eleven, then goes home to bed (Routine)
        val late = now.hour * 60 + now.minute >= si.lanisce.lani.game.villagers.Routine.STORY_UNTIL || now.hour < 5
        return scenes.asSequence()
            .filter { open(it, state) && Homes.placed(it, state, dealt) }
            .flatMap { s -> s.happenings.asSequence().filter { (guest || !forGuests(s, it)) && !(late && it.stories) }.map { h -> ActiveHappening(s, h, s.people.firstOrNull { it.id == h.who }, home(s)?.id) } }
            .filter { a -> on(a, state, today, time, world.seenIn(home(a.scene)), dawn, cast, guest) }
            .map { a -> if (a.happening.stories) a else a.copy(dialog = variant(a, state, today, cast, guest)) }
            .sortedWith(compareBy<ActiveHappening> { !it.happening.stories }.thenBy { roll(state.seed, today, it.key + "#order") })
            .filter { busy.add(it.scene.id + "/" + it.happening.who) }
            .take(MAX_ACTIVE)
            .toList()
    }

    /**
     * Whether one happening is on (what it [Happening.needs]: the age, a building, a project; the weekday, the time of
     * day, today's chance; a story to tell, for the storyteller's; a variant to play, [variant]) and not done today.
     * [world] is [state]'s, as the happening's scene sees it (in its building: [ActiveHappening.home], else
     * [Homes.buildingOf]); [dawn]: whether it is dawn now, the first hours of the [time]'s morning ([TimeOfDay.isDawn]),
     * when a happening "when" dawn is on. Its person, and everyone of [cast] its title and memory name, must be in the
     * village and met, and so must everyone its dialog of today names ([Mentions.inDialog]: "Anton toči med!").
     * [guest]: a visit, where the day's dice picks the variant.
     */
    fun on(
        a: ActiveHappening, state: GameState, today: LocalDate, time: TimeOfDay,
        world: SceneWorld = SceneWorld.of(state, a.home?.let { id -> state.buildings.firstOrNull { it.id == id } } ?: Homes.buildingOf(a.scene, state)),
        dawn: Boolean = false,
        cast: List<Villager> = Mentions.cast,
        guest: Boolean = false,
    ): Boolean {
        val h = a.happening
        if (a.person == null) return false
        if (h.stories && Stories.of(a.scene, a.person).isEmpty()) return false
        // someone of the cast is only here if they live here or visit today
        val present = si.lanisce.lani.game.villagers.Residents.present(state, today)
        if (present != null && a.person.villager != null && a.person.villager !in present) return false
        // nor does the village talk of someone who isn't here, or whom the learner doesn't know yet
        if (present != null && cast.isNotEmpty() && !Mentions.known(Mentions.inTitle(h, Mentions.names(a.scene.language, cast)), present)) return false
        if (h.needs?.met(world) == false) return false
        if (h.weekdays.isNotEmpty() && today.dayOfWeek.value !in h.weekdays) return false
        if (h.`when`.isNotEmpty() && time !in h.`when` && !(dawn && TimeOfDay.DAWN in h.`when`)) return false
        // a place where someone sleeps is quiet at night: only what is meant for the night comes up there (see Sleep)
        if (h.`when`.isEmpty() && Sleep.night(time) && Sleep.bedded(a.scene.art)) return false
        if (done(state, a.key, today)) return false
        if (roll(state.seed, today, a.key) >= h.chance) return false
        // a happening with dialogs plays one of them: with none today (all heard lately, or naming whom the learner
        // doesn't know yet) it rests
        return h.stories || DialogVariants.of(h).isEmpty() || variant(a, state, today, cast, guest) != null
    }

    /**
     * The variant happening [a] plays [today] ([DialogVariants.pick]): of its dialogs in the scene that name only people of
     * [cast] who are here and met, one not heard yet, else a review; on a visit ([guest]) the day's dice picks. Null: none.
     */
    fun variant(a: ActiveHappening, state: GameState, today: LocalDate, cast: List<Villager> = Mentions.cast, guest: Boolean = false): String? {
        val all = DialogVariants.of(a.happening)
        if (all.isEmpty()) return null
        val present = si.lanisce.lani.game.villagers.Residents.present(state, today)
        val names = if (present != null && cast.isNotEmpty()) Mentions.names(a.scene.language, cast) else null
        // (a dialog the scene lacks names nobody: the talk just doesn't start, as it always was)
        val playable = all.filter { id -> names == null || Mentions.known(Mentions.inDialog(a.scene.dialogs.firstOrNull { it.id == id }, names), present) }
        val dice = if (guest) roll(state.seed, today, a.key + "#variant") else null
        return DialogVariants.pick(all, playable, state.dialogsHeard[a.key], today, dice)
    }

    fun done(state: GameState, key: String, today: LocalDate): Boolean = state.happeningsDone[key] == today.toString()

    /** The first hour of each part of the day (see [TimeOfDay.of]). */
    private val starts = listOf(TimeOfDay.MORNING to 5, TimeOfDay.AFTERNOON to 11, TimeOfDay.EVENING to 17, TimeOfDay.NIGHT to 22)

    /**
     * Happenings that will be on later today (not now), with the part of the day they start in (one at dawn is in the
     * morning's: its dawn may begin after 5, in winter).
     */
    fun later(scenes: List<SceneSpec>, state: GameState, now: LocalDateTime, cast: List<Villager> = Mentions.cast): List<Pair<TimeOfDay, ActiveHappening>> {
        val seen = active(scenes, state, now, cast = cast).map { it.key }.toHashSet()
        val out = ArrayList<Pair<TimeOfDay, ActiveHappening>>()
        val probes = (starts + (TimeOfDay.MORNING to TimeOfDay.dawn(now.monthValue).first)).sortedBy { it.second }
        for ((part, hour) in probes) {
            if (hour <= now.hour) continue
            for (a in active(scenes, state, now.toLocalDate().atTime(hour, 0), cast = cast)) {
                if (seen.add(a.key)) out += part to a
            }
        }
        return out
    }

    /**
     * Who is in [scene] now: the ones there [ScenePerson.always] (someone of the cast only while in the village:
     * [present], ids, null for everyone), and those of its [active] happenings.
     */
    fun peopleIn(scene: SceneSpec, active: List<ActiveHappening>, present: Set<String>? = null): List<PersonInScene> {
        val here = active.filter { it.scene.id == scene.id }.mapNotNull { it.person }
        return (always(scene, present) + here).distinctBy { it.id }.map { PersonInScene(it.id, it.art, it.slot) }
    }

    /** The people always in [scene] who are in the village ([present] ids; null for everyone). */
    fun always(scene: SceneSpec, present: Set<String>?): List<ScenePerson> =
        scene.people.filter { it.always && (present == null || it.villager == null || it.villager in present) }

    private fun reached(needs: String?, age: Age): Boolean {
        val want = needs?.let { n -> Age.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } } ?: return true
        return age.ordinal >= want.ordinal
    }

    /** A number in [0, 1) from the village, the day and a key: the day's dice. */
    fun roll(seed: Long, day: LocalDate, key: String): Float {
        var h = seed * 0x9E3779B97F4A7C15uL.toLong() + day.toEpochDay() * 0xBF58476D1CE4E5B9uL.toLong() + key.hashCode()
        h = (h xor (h ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        h = (h xor (h ushr 27)) * 0x94D049BB133111EBuL.toLong()
        h = h xor (h ushr 31)
        return ((h ushr 40).toFloat() / (1L shl 24).toFloat())
    }
}
