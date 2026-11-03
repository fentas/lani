package si.lanisce.lani.game.scene

/**
 * The village speaks at the learner's level (companion/SCENES.md, "Dialogs by level"): a scene's dialogs and happenings may
 * say the level they are written for ([Dialog.level], [Happening.level]). A learner meets what is at or below their level
 * in the scene's language, their own level's first ([DialogVariants.pick] by [rankOf]); what has no level is for everyone,
 * as every dialog was before levels, so a scene without levels reads exactly as it did. An A1 learner never meets a B1
 * or B2 dialog: [forLearner] leaves them out of the scenes the village is played from (SceneController.all, a visit's).
 */
object DialogLevels {
    /** The levels, easiest first. */
    val LEVELS = Stories.LEVELS

    /** A level's place, A1 0 … C2 5; none (for everyone) -1, below every level; one not known above them all (held back). */
    fun rank(level: String?): Int = level?.let { l -> LEVELS.indexOf(l.trim().uppercase()).takeIf { it >= 0 } ?: LEVELS.size } ?: -1

    /** The learner's place: their level's ([rank]), A1 for one not known. */
    fun learnerRank(level: String?): Int = level?.let { l -> LEVELS.indexOf(l.trim().uppercase()) }?.takeIf { it >= 0 } ?: 0

    /** The level [d] is written for: its own, else that of [h], the happening that plays it; null: everyone's. */
    fun of(d: Dialog?, h: Happening?): String? = d?.level ?: h?.level

    /** The rank of a happening [h]'s dialog [id] in [scene] ([of], [rank]): the higher, the nearer the learner's own level. */
    fun rankOf(scene: SceneSpec, h: Happening, id: String): Int = rank(of(scene.dialogs.firstOrNull { it.id == id }, h))

    /** Whether a learner at [learner] meets what is written for [level] (none: everyone does). */
    fun fits(level: String?, learner: String?): Boolean = rank(level) <= learnerRank(learner)

    /**
     * [s] as a learner at [learner] in its language meets it: without the happenings above their level, nor the dialogs above
     * it (its own level, or that of every happening that plays it), each happening's variants without them; a happening whose
     * [Happening.dialog] went plays the first variant left, and one left with nothing to play goes. The same scene when nothing
     * in it is above them (every scene before levels).
     */
    fun forLearner(s: SceneSpec, learner: String?): SceneSpec {
        val at = learnerRank(learner)
        fun above(level: String?) = rank(level) > at
        if (s.happenings.none { above(it.level) } && s.dialogs.none { above(it.level) }) return s
        val kept = s.happenings.filter { !above(it.level) }
        // a dialog only happenings held back play is theirs: of their level
        val playedBy = s.happenings.flatMap { h -> DialogVariants.of(h).map { it to h } }.groupBy({ it.first }, { it.second })
        val gone = s.dialogs.filter { d -> above(d.level) || playedBy[d.id]?.let { hs -> hs.all { above(it.level) } } == true }.mapTo(HashSet()) { it.id }
        val happenings = kept.mapNotNull { h ->
            if (DialogVariants.of(h).none { it in gone }) return@mapNotNull h
            val left = h.dialogs.filter { it !in gone }
            val one = h.dialog?.takeIf { it !in gone } ?: left.firstOrNull()
            if (one == null && !h.stories) null else h.copy(dialog = one, dialogs = left)
        }
        return s.copy(happenings = happenings, dialogs = s.dialogs.filter { it.id !in gone })
    }

    /** [scenes] as the learner meets them, each at their level in its language ([level]: "sl" → "B1"). */
    fun forLearner(scenes: List<SceneSpec>, level: (String) -> String): List<SceneSpec> {
        if (scenes.none { s -> s.happenings.any { it.level != null } || s.dialogs.any { it.level != null } }) return scenes
        val byLanguage = HashMap<String, String>()
        return scenes.map { s -> forLearner(s, byLanguage.getOrPut(s.language) { level(s.language) }) }
    }
}
