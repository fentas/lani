package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** A station passed on the way to the treasure: its [id], the learner's [skill] it counts for, its best try, its minutes. */
data class StationResult(val id: String, val skill: String, val right: Int, val total: Int, val minutes: Int)

/**
 * The level change the treasure brings (companion/GAME.md, "The treasure map", "The new level on the node"), for the node:
 * `POST /level` (the bridge persists it with update-db.py, whose report `level` sets the learner's current level, and tells
 * the tutor: `level_up`). A node from before the treasure answers 404: then the same goes as a `session_end` whose report
 * the tutor persists with update-db.py ([sessionEnd]), `level` included. Pure.
 */
object LevelReport {
    /** The body of `POST /level`: the level whose end was tested ([from]), the new one ([to]), the stations; [language] another's. */
    fun body(from: String, to: String, stations: List<StationResult>, language: String? = null): JsonObject = buildJsonObject {
        put("from", from)
        put("to", to)
        language?.let { put("language", it) }
        putJsonArray("stations") {
            stations.take(8).forEach { s ->
                addJsonObject {
                    put("id", s.id)
                    put("skill", s.skill)
                    put("right", s.right.coerceIn(0, s.total.coerceIn(1, 100)))
                    put("total", s.total.coerceIn(1, 100))
                    put("minutes", s.minutes.coerceIn(0, 240))
                }
            }
        }
        put("duration_minutes", stations.sumOf { it.minutes }.coerceIn(1, 240))
    }

    /** "letter 4/5, dialog 5/6, …": the stations' tries that passed. */
    fun tally(stations: List<StationResult>): String = stations.joinToString(", ") { "${it.id} ${it.right}/${it.total}" }

    /** A line for the tutor: what was passed and what the level is now. */
    fun summary(from: String, to: String, stations: List<StationResult>, language: String): String =
        "The learner found the treasure in the app: they passed the level test at the end of $from (${tally(stations)}), so their level in $language is now $to"

    /**
     * [body] as a `session_end` for a node from before the treasure: the summary, and the report the tutor persists with
     * update-db.py, its `level` the new one; then the tutor congratulates the learner and plans the new level.
     */
    fun sessionEnd(from: String, to: String, stations: List<StationResult>, language: String, body: JsonObject): Pair<String, JsonObject> {
        val skills = stations.groupBy { it.skill }
        val data = buildJsonObject {
            put("level_up", body)
            putJsonObject("report") {
                put("level", to)
                put("command_used", "/lani-app-treasure")
                putJsonArray("skills_practiced") { skills.keys.forEach { add(it) } }
                putJsonObject("skill_scores") {
                    for ((skill, list) in skills) putJsonObject(skill) {
                        put("exercises", list.sumOf { it.total })
                        put("correct", list.sumOf { it.right })
                        put("time_minutes", list.sumOf { it.minutes })
                    }
                }
                put("duration_minutes", body["duration_minutes"] ?: JsonPrimitive(1))
                putJsonArray("milestones") { add("Reached $to in $language: the treasure hunt") }
                put("session_notes", "The treasure hunt in the app: the level test at the end of $from passed (${tally(stations)}).")
            }
        }
        val text = "${summary(from, to, stations, language)}. Persist it as the report says, with update-db.py: its \"level\" sets current_level to $to. " +
            "Then congratulate them in one short reply, and plan $to: its rules come in as the dialogs meet them and as you introduce them."
        return text to data
    }
}
