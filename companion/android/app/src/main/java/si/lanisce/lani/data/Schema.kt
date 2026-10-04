package si.lanisce.lani.data

/**
 * Content schema ids are "lani.<kind>/<version>" ("lani.culture/v0"). Before the project was renamed from Fluent they
 * were "fluent.<kind>/<version>", and content from then (an older bridge's, a tutor's file in a learner's data) still
 * loads: [matches] takes both (companion/bridge/src/schema.ts does the same for the bridge).
 */
object Schema {
    /** The id [id] had before the rename: "fluent.culture/v0" for "lani.culture/v0". */
    fun legacy(id: String): String = if (id.startsWith("lani.")) "fluent." + id.removePrefix("lani.") else id

    /** Whether [actual] is the schema id [id], under its name of today or of before the rename. */
    fun matches(actual: String?, id: String): Boolean = actual == id || actual == legacy(id)
}
