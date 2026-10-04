package si.lanisce.lani.game.scene

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.sky.SkyNow
import java.time.LocalDateTime

/**
 * One hint of when or how a thing of a scene comes into its picture ([SceneSights.whenShown]): a part of the day, the
 * moon, the seasons, the day's dice, or what the village must build first. The words panel shows it on the chip of a
 * word not there now (companion/SCENES.md, "The words panel").
 */
data class SceneHint(
    val kind: Kind,
    /** [Kind.BUILD]: the building to put up, at least at [level] when it says one above 1. */
    val building: BuildingType? = null,
    /** [Kind.BUILD]: the building's level; [Kind.UPGRADE]: the level the place itself must have. */
    val level: Int? = null,
    /** [Kind.PROJECT]: the village project ([si.lanisce.lani.game.Catalog.projectFrames]) to take far enough. */
    val project: String? = null,
    /** [Kind.AGE]: the age to reach. */
    val age: Age? = null,
) {
    enum class Kind {
        /** The first hours of the morning, round sunrise ([Wildlife.Part.DAWN]). */
        DAWN,
        /** In daylight. */
        DAY,
        /** Round sunset ([Wildlife.Part.DUSK]). */
        DUSK,
        /** After dusk, in the dark. */
        NIGHT,
        /** While the moon is up (and not new): its phase decides when. */
        MOON,
        SPRING, SUMMER, AUTUMN, WINTER,
        /** Spring to autumn: the animals that sleep through the winter. */
        NOT_WINTER,
        /** Not every day: the day's dice ([Wildlife.out]). */
        SOMETIMES,
        /** Gone for now with what a dialog set off (the market's pigeons flown away): back by tomorrow at the latest. */
        LATER,
        /** Once the village builds [building] (at [level]). */
        BUILD,
        /** Once the place the scene is seen in is upgraded to [level]. */
        UPGRADE,
        /** Once village project [project] is far enough. */
        PROJECT,
        /** Once the village reaches [age]. */
        AGE,
        /** Only before the place was upgraded (the kitchen's open hearth): it doesn't come back here. */
        GONE,
    }

    companion object {
        val DAWN = SceneHint(Kind.DAWN)
        val DAY = SceneHint(Kind.DAY)
        val DUSK = SceneHint(Kind.DUSK)
        val NIGHT = SceneHint(Kind.NIGHT)
        val MOON = SceneHint(Kind.MOON)
        val NOT_WINTER = SceneHint(Kind.NOT_WINTER)
        val SOMETIMES = SceneHint(Kind.SOMETIMES)
        val LATER = SceneHint(Kind.LATER)
        val GONE = SceneHint(Kind.GONE)
    }
}

/**
 * When the things of a scene are in its picture (companion/SCENES.md, "The words panel"): the one place that says which
 * object slots a painter draws as things to tap in a frame, and why one isn't there. A thing is there when the village
 * has what it needs ([SceneFixtures], [SceneObject.needs]) and the frame shows it: the campfire's stars after dusk and its
 * moon while it is up, the square's sun by day ([sky]; the painters ask [starsOut], [sunUp] and [moonUp]), a wild animal
 * while it is out ([Wildlife.out]), and not what a dialog's effect sent away for the day ([byEffect]: the market's pigeons
 * flown off, no bats over the water). Nothing else in a picture comes and goes as a word: the rain, the snow, the seasons
 * and who is there change how things look, not which are there (SceneSightsRenderTest keeps it so, art by art).
 *
 * Pure: the words panel groups a scene's words by it ([group]), and its chips agree with the picture because the painters
 * draw by the same conditions.
 */
object SceneSights {
    /** How a thing of the sky shows: after dusk, by day, while the moon is up. */
    enum class SkyThing { STARS, SUN, MOON }

    /** The things that come and go with the sky: art → object slot → which. */
    val sky: Map<String, Map<String, SkyThing>> = mapOf(
        "campfire" to mapOf("stars" to SkyThing.STARS, "moon" to SkyThing.MOON),
        // at night the moon stands in the sun's place as scenery, not the word
        "square" to mapOf("sun" to SkyThing.SUN),
    )

    /** The stars show (and can be tapped) once it is dark enough. */
    fun starsOut(env: Env): Boolean = env.dark > 0.3f

    /** The sun is up, just over the ridges. */
    fun sunUp(env: Env): Boolean = env.sun > -0.12f

    /** The moon is up and not new ([Env.moonShows]: the real one when the frame has the real sky). */
    fun moonUp(env: Env): Boolean = env.moonShows

    /** What flies off with an effect a dialog sets (its level how far): art → object slot → the effect. */
    val flies: Map<String, Map<String, String>> = mapOf("market" to mapOf("pigeon" to "pigeons"))

    /** Flown so far ([flies]' effect at [level]) that it's gone from the picture. */
    fun flownAway(level: Float): Boolean = level >= 0.995f

    /**
     * Whether [slot] of [art] is sent away by the effects [fx] a dialog set (and left for the day): the pigeons flown off,
     * the bats a cue has none of ([Wildlife.bats]).
     */
    fun byEffect(art: String, slot: String, fx: Map<String, Float>): Boolean {
        if (fx.isEmpty()) return false
        flies[art]?.get(slot)?.let { e -> fx[e]?.let { if (flownAway(it)) return true } }
        if (slot == BAT && slot in Wildlife.slots(art) && "bats" in SceneArt.effects[art].orEmpty()) fx["bats"]?.let { return Wildlife.bats(it) == 0 }
        return false
    }

    /** The light of [frame] as far as what shows goes (the grey of the weather changes none of it). */
    fun env(frame: SceneFrame): Env = Env(frame.hour, frame.month, false, 0f, frame.moon, frame.skyNow)

    /**
     * The inputs of a picture at [now] that decide which things it shows, as the scene view draws it: the hour to the
     * minute, the month, the day, the moon ([moon]: its age; [skyNow]: the real sky, as the view has it), the village's
     * wild animals ([village]), what it has built ([world]) and the effects a finished dialog left ([fx]).
     */
    fun frame(
        now: LocalDateTime,
        world: SceneWorld = SceneWorld.ALL,
        village: Long = 0L,
        fx: Map<String, Float> = emptyMap(),
        moon: Float = si.lanisce.lani.game.render.Moon.FULL,
        skyNow: SkyNow? = null,
    ): SceneFrame = SceneFrame(
        hour = now.hour + now.minute / 60f + now.second / 3600f,
        month = now.monthValue,
        fx = fx,
        world = world,
        moon = moon,
        date = now.toLocalDate(),
        village = village,
        day = now.toLocalDate().toEpochDay(),
        skyNow = skyNow,
    )

    /** Whether [slot] of [art] shows in [frame] as a thing to tap, what the village has built aside. */
    fun shows(art: String, slot: String, frame: SceneFrame): Boolean {
        sky[art]?.get(slot)?.let { thing ->
            val env = env(frame)
            return when (thing) {
                SkyThing.STARS -> starsOut(env)
                SkyThing.SUN -> sunUp(env)
                SkyThing.MOON -> moonUp(env)
            }
        }
        if (slot in Wildlife.slots(art) && slot !in frame.wildOf(art)) return false
        return !byEffect(art, slot, frame.fx)
    }

    /** Whether [o], a thing of a scene of [art], is in the picture of [frame]: there in its world, and shown now. */
    fun visibleNow(art: String, o: SceneObject, frame: SceneFrame): Boolean =
        SceneFixtures.there(art, o, frame.world) && shows(art, o.slot, frame)

    /**
     * When [o], a thing of a scene of [art], is in the picture: what [world] still has to build for it (nothing once it
     * has; [SceneHint.Kind.GONE] when it went as the place was upgraded), what a dialog's effects [fx] sent away for now
     * ([SceneHint.Kind.LATER]), then the parts of the day, the moon, the seasons and the day's dice it comes with. Empty:
     * it is always there.
     */
    fun whenShown(art: String, o: SceneObject, world: SceneWorld = SceneWorld.ALL, fx: Map<String, Float> = emptyMap()): List<SceneHint> {
        val out = ArrayList<SceneHint>()
        for (need in listOfNotNull(SceneFixtures.of(art, o.slot), o.needs)) if (!need.met(world)) out += hints(need, world)
        if (byEffect(art, o.slot, fx)) out += SceneHint.LATER
        when (sky[art]?.get(o.slot)) {
            SkyThing.STARS -> out += SceneHint.NIGHT
            SkyThing.SUN -> out += SceneHint.DAY
            SkyThing.MOON -> out += SceneHint.MOON
            null -> {}
        }
        Wildlife.animals[art]?.firstOrNull { it.word && it.id == o.slot }?.let { a ->
            for (part in Wildlife.Part.entries) if (part in a.parts) out += part.hint
            out += seasons(a.months)
            if (a.chance < 1f) out += SceneHint.SOMETIMES
        }
        return out.distinct()
    }

    /** Whether [o] can still come into the picture of a scene of [art] in [world]: not a thing gone with an upgrade. */
    fun canCome(art: String, o: SceneObject, world: SceneWorld): Boolean =
        listOfNotNull(SceneFixtures.of(art, o.slot), o.needs).none { it.before && !it.met(world) }

    /**
     * A scene's words as the words panel groups them: what the picture shows [now] (found or not), what was [seen]
     * before and isn't there now, and what is still [hidden] with when or how to find it; each in the scene's order.
     */
    data class Groups(
        val now: List<SceneObject>,
        val seen: List<Pair<SceneObject, List<SceneHint>>>,
        val hidden: List<Pair<SceneObject, List<SceneHint>>>,
    ) {
        /** Every hint the seen and the hidden words show, in the order they first come. */
        val hints: List<SceneHint> get() = (seen + hidden).flatMap { it.second }.distinct()
    }

    /**
     * [scene]'s words (all of them, not only what its world has: [SceneSpec.inWorld]) grouped for the picture of [frame],
     * whose world is the scene's, with the slots [found] so far. A thing not found that can't come back ([canCome]) is
     * left out.
     */
    fun group(scene: SceneSpec, frame: SceneFrame, found: Set<String>): Groups {
        val now = ArrayList<SceneObject>()
        val seen = ArrayList<Pair<SceneObject, List<SceneHint>>>()
        val hidden = ArrayList<Pair<SceneObject, List<SceneHint>>>()
        for (o in scene.objects.distinctBy { it.slot }) when {
            visibleNow(scene.art, o, frame) -> now += o
            o.slot in found -> seen += o to whenShown(scene.art, o, frame.world, frame.fx)
            canCome(scene.art, o, frame.world) -> hidden += o to whenShown(scene.art, o, frame.world, frame.fx)
        }
        return Groups(now, seen, hidden)
    }

    private const val BAT = "bat"

    private val Wildlife.Part.hint: SceneHint
        get() = when (this) {
            Wildlife.Part.DAWN -> SceneHint.DAWN
            Wildlife.Part.DAY -> SceneHint.DAY
            Wildlife.Part.DUSK -> SceneHint.DUSK
            Wildlife.Part.NIGHT -> SceneHint.NIGHT
        }

    /** The seasons of [months]: none for the whole year, [SceneHint.NOT_WINTER] for spring to autumn, else each one. */
    private fun seasons(months: IntRange): List<SceneHint> {
        val by = mapOf(
            SceneHint.Kind.SPRING to 3..5, SceneHint.Kind.SUMMER to 6..8, SceneHint.Kind.AUTUMN to 9..11, SceneHint.Kind.WINTER to listOf(12, 1, 2),
        ).filterValues { ms -> ms.any { it in months } }.keys
        return when {
            by.size == 4 -> emptyList()
            by == setOf(SceneHint.Kind.SPRING, SceneHint.Kind.SUMMER, SceneHint.Kind.AUTUMN) -> listOf(SceneHint.NOT_WINTER)
            else -> by.map { SceneHint(it) }
        }
    }

    /** What [need] (not met in [world]) asks for, as hints: the age, the building, the place's level, the project. */
    private fun hints(need: SceneNeeds, world: SceneWorld): List<SceneHint> {
        if (need.before) return listOf(SceneHint.GONE)
        val out = ArrayList<SceneHint>()
        need.age?.let { a -> Age.entries.firstOrNull { it.name.equals(a, ignoreCase = true) } }
            ?.takeIf { world.age < it }?.let { out += SceneHint(SceneHint.Kind.AGE, age = it) }
        val type = need.buildingType
        when {
            need.building != null -> if (type != null && !(world.has(type) && world.level(type) >= (need.level ?: 1))) out += SceneHint(SceneHint.Kind.BUILD, building = type, level = need.level)
            need.level != null -> if (world.hereLevel < need.level) out += SceneHint(SceneHint.Kind.UPGRADE, level = need.level)
        }
        need.project?.let { p -> if (world.stage(p) < (need.step ?: world.stages(p))) out += SceneHint(SceneHint.Kind.PROJECT, project = p) }
        return out
    }
}
