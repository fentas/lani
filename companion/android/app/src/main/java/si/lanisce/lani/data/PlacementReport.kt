package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.Introduction
import si.lanisce.lani.game.LevelPlacement
import si.lanisce.lani.game.Placed

/**
 * The placement test's result for the node (companion/COURSE.md "The placement test"; game/LevelPlacement):
 * `POST /placement` (the bridge's src/placement.ts persists it with update-db.py as a session, its `level` the one
 * suggested when the learner took a result above their level, and tells the tutor: `placement`). A node from before it
 * answers 404: then the same goes as a `session_end` whose report the tutor persists with update-db.py ([sessionEnd]).
 * Pure.
 */
object PlacementReport {
    /** A probe as the node reads it: its part, its items right, almost and of, passed or not. */
    private fun probe(t: LevelPlacement.Test, p: LevelPlacement.Probe): JsonObject {
        val part = t.parts[p.part]
        val v = p.verdicts.orEmpty()
        return buildJsonObject {
            put("level", part.level)
            put("part", part.part)
            put("parts", part.parts)
            put("right", v.count { it == Verdict.CORRECT }.coerceAtMost(100))
            put("almost", v.count { it == Verdict.ALMOST }.coerceAtMost(100))
            put("of", v.size.coerceIn(1, 100))
            put("passed", p.passed)
        }
    }

    /**
     * The body of `POST /placement`: the learner's level [from] before it, the level suggested ([r]), whether they took it
     * ([applied]; then the [units] it marked done), where the school goes on, each probe answered (at most 8); [language]
     * another's than the home one.
     */
    fun body(t: LevelPlacement.Test, r: LevelPlacement.Result, from: String, applied: Boolean, units: Int, language: String? = null): JsonObject = buildJsonObject {
        language?.let { put("language", it) }
        put("from", Introduction.LEVELS[Introduction.rank(from) ?: 0])
        put("level", r.level)
        put("applied", applied)
        r.start?.let { s -> putJsonObject("start") { put("level", s.level); put("part", s.part); put("parts", s.parts) } }
        put("units", if (applied) units.coerceIn(0, 500) else 0)
        putJsonArray("probes") { t.probes.filter { it.answered && it.verdicts.orEmpty().isNotEmpty() }.takeLast(8).forEach { add(probe(t, it)) } }
        if (t.version == LevelPlacement.Version.CHILD) put("child", true)
        put("duration_minutes", ((t.seconds + 30) / 60).coerceIn(1, 240))
    }

    /** Whether the body has anything to say: a probe answered (the node refuses one with none). */
    fun worth(t: LevelPlacement.Test): Boolean = t.probes.any { it.answered && it.verdicts.orEmpty().isNotEmpty() }

    /** "A1 2/3 passed 8/8, A2 3/3 not 3/8": each part asked. */
    fun tally(t: LevelPlacement.Test): String = t.probes.filter { it.answered }.joinToString(", ") { p ->
        val v = p.verdicts.orEmpty()
        val score = v.count { it == Verdict.CORRECT } + 0.5 * v.count { it == Verdict.ALMOST }
        "${t.parts[p.part].label} ${if (p.passed) "passed" else "not"} ${if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()}/${v.size}"
    }

    /**
     * [body] as a `session_end` for a node from before the placement test: the tutor persists the report with update-db.py
     * (its `level` only when it rises: [raise]) and welcomes the learner at their level.
     */
    fun sessionEnd(t: LevelPlacement.Test, r: LevelPlacement.Result, from: String, applied: Boolean, units: Int, raise: Boolean, language: String, body: JsonObject): Pair<String, JsonObject> {
        val items = t.verdicts.size
        val minutes = body["duration_minutes"] ?: JsonPrimitive(1)
        val start = r.start?.label ?: "after the course's last part"
        val kept = if (applied) "they took its result: $units units marked done in the school, they start at $start" else "they kept their place: nothing changed in the school"
        val notes = "The placement test in the app${if (t.version == LevelPlacement.Version.CHILD) " (a child's version)" else ""}, from $from: ${tally(t)}; it suggested ${r.level}, and $kept."
        val data = buildJsonObject {
            put("placement", body)
            putJsonObject("report") {
                if (raise) put("level", r.level)
                put("command_used", "/lani-app-placement")
                putJsonArray("skills_practiced") { add("grammar") }
                putJsonObject("skill_scores") {
                    putJsonObject("grammar") {
                        put("exercises", items)
                        put("correct", t.verdicts.count { it == Verdict.CORRECT })
                        put("time_minutes", minutes)
                    }
                }
                put("duration_minutes", minutes)
                putJsonArray("milestones") { if (raise) add("Placed at ${r.level} in $language: the placement test") }
                put("session_notes", notes)
            }
        }
        val level = if (raise) "Persist it as the report says, with update-db.py: its \"level\" sets current_level to ${r.level}." else "Persist it as the report says, with update-db.py (their level stays $from)."
        val text = "$notes $level " + if (applied) "Then welcome them at their level in one short reply; get_course shows the unit they start with." else "Don't press the test on them."
        return text to data
    }

    /**
     * [p], a result taken, as `POST /placement` says it again ("📨 Pošlji znova · Send again", once the test is gone): the
     * level it raised, what it marked, and its items as one probe (the node refuses a report with none), passed at the part
     * it started at.
     */
    fun again(p: Placed): JsonObject = buildJsonObject {
        put("from", Introduction.LEVELS[Introduction.rank(p.from) ?: 0])
        put("level", p.level)
        put("applied", true)
        p.start?.let { s -> putJsonObject("start") { put("level", s); put("part", p.part.coerceAtLeast(1)); put("parts", p.parts.coerceAtLeast(1)) } }
        put("units", p.units.coerceIn(0, 500))
        putJsonArray("probes") {
            add(buildJsonObject {
                put("level", p.start ?: p.level)
                put("part", p.part.coerceAtLeast(1))
                put("parts", p.parts.coerceAtLeast(1))
                put("right", p.right.coerceIn(0, 100))
                put("almost", p.almost.coerceIn(0, 100))
                put("of", p.of.coerceIn(1, 100))
                put("passed", true)
            })
        }
        if (p.child) put("child", true)
        put("duration_minutes", p.minutes.coerceIn(1, 240))
    }

    /** [again] as a `session_end` for a node from before the placement test ([sessionEnd]'s: its "level" raises it). */
    fun againSessionEnd(p: Placed, language: String, body: JsonObject): Pair<String, JsonObject> {
        val notes = "The placement test in the app, sent again: on ${p.day} they took its result, ${p.level}, and their profile doesn't show it yet."
        val data = buildJsonObject {
            put("placement", body)
            putJsonObject("report") {
                put("level", p.level)
                put("command_used", "/lani-app-placement")
                putJsonArray("skills_practiced") { add("grammar") }
                put("duration_minutes", p.minutes.coerceIn(1, 240))
                putJsonArray("milestones") { add("Placed at ${p.level} in $language: the placement test") }
                put("session_notes", notes)
            }
        }
        return "$notes Persist it as the report says, with update-db.py: its \"level\" sets current_level to ${p.level}. No reply needed." to data
    }

    /** For the tests: the probes' JSON objects. */
    internal fun probes(body: JsonObject): List<JsonObject> = (body["probes"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? JsonObject }
}
