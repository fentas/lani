package si.lanisce.lani.game.scene

/**
 * The things in a scene's picture that are places of the village with scenes of their own (companion/SCENES.md, "Into another
 * scene"): the stream's mill is the mill project's landmark, so once the mill grinds its word's bubble offers "🚪 Vstopi · Go
 * in" into the mill (the scenes that open from "project:mlin"), as the mill's card on the map does. Before that the stream
 * draws no mill to tap (its `needs`), so there is never a way in to a place not built yet. Pure.
 */
object SceneDoors {
    /** "art/slot" → the place of the village that thing is ("project:<id>", a building type, "spot:<id>"). */
    private val DOORS: Map<String, String> = mapOf("stream/mill" to "project:mlin")

    /** The place thing [slot] of [art] is, or null for a thing that leads nowhere. */
    fun placeOf(art: String, slot: String): String? = DOORS["$art/$slot"]

    /**
     * The scenes thing [slot] of [scene] leads into: those [open] gives for its place now (the scene controller's, the ones
     * the village has open: built, the age reached), never [scene] itself; none for a thing that leads nowhere.
     */
    fun into(scene: SceneSpec, slot: String, open: (String) -> List<SceneSpec>): List<SceneSpec> =
        placeOf(scene.art, slot)?.let(open).orEmpty().filter { it.id != scene.id }
}
