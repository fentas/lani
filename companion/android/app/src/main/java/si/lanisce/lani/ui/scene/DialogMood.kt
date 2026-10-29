package si.lanisce.lani.ui.scene

import si.lanisce.lani.game.scene.Face
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.face

/**
 * How the talk partner stands for a while: [pose] with [face] (by default the pose's) from [since] (ms) for [holdMs],
 * then idle again.
 */
data class Mood(val pose: Pose, val since: Long = 0L, val holdMs: Long = 0L, val face: Face = pose.face) {
    /** When it is over (ms). */
    val until: Long get() = since + holdMs

    fun on(now: Long): Boolean = pose != Pose.IDLE && now < until

    companion object {
        val NONE = Mood(Pose.IDLE)
    }
}

/**
 * How the person Jan talks to in a scene reacts to the dialog (pure; like the stage's
 * [si.lanisce.lani.ui.stage.Director], the scene screen plays it), with the face that goes with it (companion/SCENES.md,
 * "Faces"): a wave and a smile when it opens, a happy hop and a smile at a right pick, a worried shrug at a wrong one, a
 * hand to the chin at a wrong one they answer ([DialogRun.reaction]), confused unless the reaction has a face of its own,
 * a hand at the ear while Jan speaks into the mic, a cheer at the end without a mistake (proud; a child laughs), else a
 * happy hop. Their own lines win: while they speak they are drawn talking, with their line's face.
 */
object DialogMood {
    const val WAVE_MS = 1_500L
    const val HAPPY_MS = 1_800L
    const val SAD_MS = 1_800L
    const val PUZZLED_MS = 1_800L
    const val END_MS = 2_500L

    /** A line is said this long after it shows ... */
    const val SAY_MS = 250L
    /** ... and a moment later after a reaction (the first line, after the wave; a reply, after the happy hop), so it shows first. */
    const val SAY_AFTER_REACTION_MS = 700L

    /**
     * The mood once the dialog went from [before] to [after] at [now] (ms): a step's reaction, else [mood] goes on. [child]:
     * the person is a child (who laughs at the end, where a grown-up is proud).
     */
    fun react(before: DialogRun?, after: DialogRun?, mood: Mood, now: Long, child: Boolean = false): Mood = when {
        after == null -> Mood.NONE
        before == null -> Mood(Pose.WAVE, now, WAVE_MS, Face.HAPPY)
        after.step == DialogRun.Step.END && before.step != DialogRun.Step.END ->
            if (after.mistakes == 0) Mood(Pose.CHEER, now, END_MS, if (child) Face.LAUGH else Face.PROUD) else Mood(Pose.HAPPY, now, END_MS, Face.HAPPY)
        after.mistakes > before.mistakes -> after.reaction?.let { r -> Mood(Pose.THINK, now, PUZZLED_MS, r.face ?: Face.CONFUSED) }
            ?: Mood(Pose.SAD, now, SAD_MS, Face.WORRIED)
        after.picks.size > before.picks.size -> Mood(Pose.HAPPY, now, HAPPY_MS, Face.HAPPY)
        else -> mood
    }

    /**
     * How they stand at [now]: drawn talking while [talking] (idle here: the sprite's talking pose), a hand at the ear
     * while Jan speaks into the mic ([listening]), the [mood] while it holds, else idle.
     */
    fun pose(mood: Mood, now: Long, talking: Boolean, listening: Boolean): Pose = when {
        talking -> Pose.IDLE
        listening -> Pose.LISTEN
        mood.on(now) -> mood.pose
        else -> Pose.IDLE
    }

    /**
     * The face someone's line [i] of [said] is said with: its own ([Said.face]), else what the moment brings: the first
     * line a smile (a greeting), a reply to the learner's right line a smile, a reaction to a wrong one confused, any other
     * the resting face. Its bubble's face shows it, and the person's face holds it a while ([rememberPartnerLook]).
     */
    fun lineFace(said: List<Said>, i: Int): Face {
        val s = said.getOrNull(i) ?: return Face.NEUTRAL
        s.face?.takeIf { it != Face.NEUTRAL }?.let { return it }
        if (s.who == null) return Face.NEUTRAL
        if (i == 0) return Face.HAPPY
        val before = said[i - 1]
        return when {
            before.who != null -> Face.NEUTRAL
            before.wrong -> Face.CONFUSED
            else -> Face.HAPPY
        }
    }

    /**
     * How long after [run]'s newest line shows it is said: later for the first line and for a reply to Jan's pick (a
     * reaction to a wrong one included).
     */
    fun sayAfter(run: DialogRun): Long {
        val said = run.said
        val first = said.size == 1 && said[0].who != null
        // a pick and its reply come together: the learner's line (no speaker), then the person's
        val reply = said.size >= 2 && said[said.size - 2].who == null && said.last().who != null
        return if (first || reply) SAY_AFTER_REACTION_MS else SAY_MS
    }
}
