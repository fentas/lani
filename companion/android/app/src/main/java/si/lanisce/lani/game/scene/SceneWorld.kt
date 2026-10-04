package si.lanisce.lani.game.scene

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.GameState

/**
 * What the village has built, as the scenes show it (companion/SCENES.md, "What the village has built"): the age, which
 * buildings stand (at their highest level; a damaged one stands too) and how far each village project is. A painter
 * draws a fixture (the kozolec in the field, France's mill by the stream) only when it is there, and a project's as far
 * as its steps have come; an object or a happening that [SceneNeeds] something isn't there without it.
 *
 * A scene opened from a building is seen in it ([here]): the kitchen of a hut at level 1 is bare, the one of a hut at
 * level 3 has the clock and the holy corner, whatever the other huts are. Its level is the one that counts for its type
 * and for a need of the place's own level ([SceneNeeds.level] alone).
 *
 * [ALL], the default, is a village that has everything: the last age, every building at the top level, every project
 * finished. Tools, the snapshot tests and whatever crops a scene's things out of its picture see the whole scene.
 */
data class SceneWorld(
    val age: Age = Age.entries.last(),
    /** Building type → the highest level of one that stands; null: every type, at the top level. */
    val buildings: Map<BuildingType, Int>? = null,
    /** Project id ([Catalog.projectFrames]) → steps done; null: every project finished. */
    val projects: Map<String, Int>? = null,
    /** Wolves are about (the village's wolves event is on): the watchtower's view shows their eyes in the forest at night. */
    val wolves: Boolean = false,
    /**
     * The building the scene is seen in: the one the learner opened it from (the hut or the house tapped), else the one
     * its place is ([Homes.buildingOf]); null when that isn't known (tools, tests: the top level with [ALL]).
     */
    val here: Building? = null,
) {
    /** Whether a building of [type] stands. */
    fun has(type: BuildingType): Boolean = buildings?.containsKey(type) ?: true

    /**
     * The level of a [type] that stands, 0 for none: the one the scene is seen in ([here]) when it is of that type, else
     * the highest.
     */
    fun level(type: BuildingType): Int = when {
        here?.type == type -> here.level
        buildings == null -> Catalog.MAX_LEVEL
        else -> buildings[type] ?: 0
    }

    /**
     * The level of the place the scene is seen in ([here]): the top level in a village that has everything ([ALL]), the
     * first in one that doesn't say (a scene opened from no building).
     */
    val hereLevel: Int get() = here?.level ?: if (buildings == null) Catalog.MAX_LEVEL else 1

    /** This world seen in [building] (null: as it is). */
    fun seenIn(building: Building?): SceneWorld = if (building == null || building == here) this else copy(here = building)

    /** Steps done of [project], 0 … [stages]. */
    fun stage(project: String): Int = projects?.let { (it[project] ?: 0).coerceIn(0, stages(project)) } ?: stages(project)

    /** How many steps [project] has (1 for a project the game doesn't know). */
    fun stages(project: String): Int = STAGES[project] ?: 1

    fun finished(project: String): Boolean = stage(project) >= stages(project)

    companion object {
        /** Everything there: the default for a painter's frame. */
        val ALL = SceneWorld()

        /** Nothing built yet: the campfire's first day (a room of it is at its first level). */
        val NONE = SceneWorld(Age.OGENJ, emptyMap(), emptyMap())

        private val STAGES: Map<String, Int> by lazy { Catalog.projectFrames.associate { it.id to it.steps.size } }

        /**
         * [state]'s village, seen in [here] (see [SceneWorld.here]). The tent that moved to the pond (see
         * [si.lanisce.lani.game.TentMove]) stands there, not by the campfire: the campfire's tent is there only while a
         * tent stands in the village.
         */
        fun of(state: GameState, here: Building? = null): SceneWorld = SceneWorld(
            age = state.age,
            buildings = state.buildings.filterNot { it.type == BuildingType.TENT && it.plot == si.lanisce.lani.game.NO_PLOT }
                .groupBy { it.type }.mapValues { (_, bs) -> bs.maxOf { it.level } },
            projects = state.projects,
            wolves = state.event?.kind == si.lanisce.lani.game.EventKind.WOLVES,
            here = here,
        )

        /** Everything built, seen in a room at [level]: what a tool shows of a room's stages (and a sticker of a thing that goes as the room grows). */
        fun room(level: Int, type: BuildingType = BuildingType.HOUSE): SceneWorld = SceneWorld(here = Building("here", type, 0, level = level))
    }
}

/**
 * What a scene's object or happening needs to be there (lani.scene/v0 `needs`), besides its scene being open: an [age]
 * reached, a [building] standing (a [BuildingType] name, "KOZOLEC"), at least at [level] when it says one, a [project]
 * with at least [step] steps done (by default all of them: finished). A [level] alone is the place's own: the building the
 * scene is seen in ([SceneWorld.here], the hut or the house opened) at least at that level (the kitchen's clock from the
 * third). With [before] it is the other way round: there until the building stands (or gets to the level) or the project
 * gets that far (France talks of the mill he wants to build until it grinds; the straw bed until the room's second level).
 * All it names must hold.
 *
 * A file gives it as an object, `{"project": "most", "step": 2}`, `{"level": 2}`; a happening's `needs` may still be just
 * the age, `"vas"`, as before.
 */
@Serializable(with = SceneNeeds.Serializer::class)
data class SceneNeeds(
    val age: String? = null,
    val building: String? = null,
    val project: String? = null,
    val step: Int? = null,
    val before: Boolean = false,
    /** With [building]: a building of that type at least at this level (1 … [Catalog.MAX_LEVEL]); alone: the place's own. */
    val level: Int? = null,
) {
    /** The building named, or null for none or one the game doesn't know. */
    val buildingType: BuildingType? get() = building?.let { b -> BuildingType.entries.firstOrNull { it.name.equals(b, ignoreCase = true) } }

    /** Whether it holds in [world]. */
    fun met(world: SceneWorld): Boolean {
        age?.let { a -> Age.entries.firstOrNull { it.name.equals(a, ignoreCase = true) } }?.let { if (world.age < it) return false }
        if (building == null && project == null && level == null) return true
        val built = when {
            building != null -> buildingType?.let { t -> world.has(t) && world.level(t) >= (level ?: 1) } == true
            level != null -> world.hereLevel >= level
            else -> true
        }
        val far = project == null || world.stage(project) >= (step ?: world.stages(project))
        return (built && far) != before
    }

    object Serializer : KSerializer<SceneNeeds> {
        @Serializable
        private data class Fields(
            val age: String? = null, val building: String? = null, val project: String? = null, val step: Int? = null,
            val before: Boolean = false, val level: Int? = null,
        )

        override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

        override fun deserialize(decoder: Decoder): SceneNeeds {
            val input = decoder as? JsonDecoder ?: throw SerializationException("needs: JSON only")
            return when (val e = input.decodeJsonElement()) {
                is JsonPrimitive -> SceneNeeds(age = e.contentOrNull)
                is JsonObject -> input.json.decodeFromJsonElement(Fields.serializer(), e).let { SceneNeeds(it.age, it.building, it.project, it.step, it.before, it.level) }
                else -> throw SerializationException("needs is an age (\"vas\") or {\"building\": …, \"level\": …} / {\"project\": …, \"step\": …}")
            }
        }

        override fun serialize(encoder: Encoder, value: SceneNeeds) {
            val out = encoder as? JsonEncoder ?: throw SerializationException("needs: JSON only")
            out.encodeJsonElement(
                buildJsonObject {
                    value.age?.let { put("age", it) }
                    value.building?.let { put("building", it) }
                    value.level?.let { put("level", it) }
                    value.project?.let { put("project", it) }
                    value.step?.let { put("step", it) }
                    if (value.before) put("before", true)
                },
            )
        }
    }
}

/**
 * The things of an art its painter draws only when the village has built them: art → object slot → what the slot needs
 * (the kozolec and its hay in the field, the tent at the campfire, the well and the church on the square, France's mill
 * by the stream; in a room, what its building's level brings: the kitchen's tiled stove from the second, its clock from
 * the third, and its hearth and straw bed only until then). An object in such a slot is there only when this holds too,
 * whatever its own [SceneObject.needs] says, so a scene the tutor writes can't offer a word for a thing that isn't drawn.
 *
 * The painters draw more that grows with the village without being a word of its own (the cart bridge over the stream,
 * the maypole and the clock on the square, the double hayrack in the field, the kitchen's tiled floor and painted walls,
 * the smithy's second anvil): see the painters and companion/SCENES.md.
 */
object SceneFixtures {
    private val L2 = SceneNeeds(level = 2)
    private val L3 = SceneNeeds(level = 3)

    val slots: Map<String, Map<String, SceneNeeds>> = mapOf(
        "campfire" to mapOf("tent" to SceneNeeds(building = "TENT")),
        // level 1 bare: the open hearth, a plain bench, the straw bed, a shelf with the jugs; level 2 the tiled stove, the
        // table laid with its chairs, curtains; level 3 the clock, the painted dresser and the holy corner
        "kitchen" to mapOf(
            "hearth" to SceneNeeds(level = 2, before = true), "bed" to SceneNeeds(level = 2, before = true), "shelf" to SceneNeeds(level = 3, before = true),
            "stove" to L2, "table" to L2, "chair" to L2, "curtains" to L2,
            "bread" to L2, "plate" to L2, "bowl" to L2, "glass" to L2, "cup" to L2, "spoon" to L2, "fork" to L2, "knife" to L2, "salt" to L2,
            "cupboard" to L3, "clock" to L3, "corner" to L3,
        ),
        "field" to mapOf("kozolec" to SceneNeeds(building = "KOZOLEC"), "hay" to SceneNeeds(building = "KOZOLEC")),
        "square" to mapOf("well" to SceneNeeds(building = "WELL"), "tower" to SceneNeeds(building = "CHURCH")),
        // level 2 the big bellows and the chain; level 3 the cart wheel waiting for its tyre and the axe on the wall
        "smithy" to mapOf("bellows" to L2, "chain" to L2, "wheel" to L3, "axe" to L3),
        // level 2 the map and the globe; level 3 the clock
        "school" to mapOf("map" to L2, "globe" to L2, "clock" to L3),
        // the rooms of the houses: level 1 what a room needs, 2 what makes it a home, 3 the good things
        "livingroom" to mapOf(
            "chair" to L2, "curtains" to L2, "rug" to L2, "lamp" to L2, "clock" to L2,
            "corner" to L3, "photo" to L3, "glasses" to L3, "book" to L3,
        ),
        "workshop" to mapOf("saw" to L2, "tools" to L2, "ladder" to L2, "rake" to L2, "wheelbarrow" to L3, "horse" to L3, "lantern" to L3),
        "cellar" to mapOf("shelf" to L2, "bottle" to L2, "grapes" to L2, "wine" to L2, "ham" to L3, "cheese" to L3),
        "attic" to mapOf("wardrobe" to L2, "rug" to L2, "lamp" to L2, "ball" to L2, "mirror" to L3, "toy" to L3),
        "stream" to mapOf("mill" to SceneNeeds(project = "mlin")),
    )

    /** What [slot] of [art] needs, or null when it's always there. */
    fun of(art: String, slot: String): SceneNeeds? = slots[art]?.get(slot)

    /** Whether [slot] of [art] is there in [world]. */
    fun there(art: String, slot: String, world: SceneWorld): Boolean = of(art, slot)?.met(world) ?: true

    /** Whether object [o] of a scene of [art] is there in [world]: its slot and its own [SceneObject.needs]. */
    fun there(art: String, o: SceneObject, world: SceneWorld): Boolean = there(art, o.slot, world) && (o.needs?.met(world) ?: true)

    /**
     * A world in which [slot] of [art] is drawn, for its sticker: everything built ([SceneWorld.ALL]), else a room at the
     * first level that has it (the kitchen's straw bed, gone once the room is upgraded).
     */
    fun showing(art: String, slot: String): SceneWorld =
        if (there(art, slot, SceneWorld.ALL)) SceneWorld.ALL
        else (1..Catalog.MAX_LEVEL).map { SceneWorld.room(it) }.firstOrNull { there(art, slot, it) } ?: SceneWorld.ALL
}

/** [this] scene as [world] has it: only the objects that are there (drawn, tappable, words to learn). */
fun SceneSpec.inWorld(world: SceneWorld): SceneSpec {
    val here = objects.filter { SceneFixtures.there(art, it, world) }
    return if (here.size == objects.size) this else copy(objects = here)
}
