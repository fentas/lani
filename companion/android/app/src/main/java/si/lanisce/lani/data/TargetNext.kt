package si.lanisce.lani.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A raised target level that starts with the village's next age (learner-profile.json `learner.target_level_next`,
 * companion/GAME.md "Ages", "A target raised later"): [level] counts from the age [fromAge] on (the village's age as
 * game.json names it: "VAS"), the step the village is on keeps its target's words. The bridge applies it when the
 * village is saved at that age (src/aim.ts); the app counts with it already ([si.lanisce.lani.game.Aims]).
 */
data class TargetNext(val level: String, val fromAge: String, val set: String? = null) {
    companion object {
        private val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")
        private val AGES = listOf("OGENJ", "TABOR", "ZASELEK", "VAS", "TRG", "MESTO")

        /** The profile's `target_level_next`, when it is a good one (a level and an age); else null, as the bridge reads it. */
        fun parse(e: JsonElement?): TargetNext? {
            val o = e as? JsonObject ?: return null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()
            val level = s("level")?.uppercase()?.takeIf { it in LEVELS } ?: return null
            val age = s("from_age")?.uppercase()?.takeIf { it in AGES } ?: return null
            return TargetNext(level, age, s("set")?.takeIf { it.isNotEmpty() })
        }

        /** The profile's target (`target_level`) as a level, B2 when it names none or none that is one (the bridge's targetOf). */
        fun target(e: JsonElement?): String =
            (e as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.uppercase()?.takeIf { it in LEVELS } ?: "B2"
    }
}
