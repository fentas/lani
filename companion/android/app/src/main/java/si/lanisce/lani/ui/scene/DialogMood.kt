package si.lanisce.lani.ui.scene

import si.lanisce.lani.game.scene.Pose

/** How the talk partner stands for a while: [pose] from [since] (ms) for [holdMs], then idle again. */
data class Mood(val pose: Pose, val since: Long = 0L, val holdMs: Long = 0L) {
    /** When it is over (ms). */
    val until: Long get() = since + holdMs

    fun on(now: Long): Boolean = pose != Pose.IDLE && now < until

    companion object {
        val NONE = Mood(Pose.IDLE)
    }
}

/**
 * How the person Jan talks to in a scene reacts to the dialog (pure; like the stage's
 * [si.lanisce.lani.ui.stage.Director], the scene screen plays it): a wave when it opens, a happy hop at a right
 * pick, a sad tilt at a wrong one, a puzzled look (a hand to the chin) at a wrong one they answer ([DialogRun.reaction]),
 * a hand at the ear while Jan speaks into the mic, a cheer at the end without a mistake (else a happy hop). Their own
 * lines win: while they speak they are drawn talking.
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

    /** The mood once the dialog went from [before] to [after] at [now] (ms): a step's reaction, else [mood] goes on. */
    fun react(before: DialogRun?, after: DialogRun?, mood: Mood, now: Long): Mood = when {
        after == null -> Mood.NONE
        before == null -> Mood(Pose.WAVE, now, WAVE_MS)
        after.step == DialogRun.Step.END && before.step != DialogRun.Step.END ->
            Mood(if (after.mistakes == 0) Pose.CHEER else Pose.HAPPY, now, END_MS)
        after.mistakes > before.mistakes -> if (after.reaction != null) Mood(Pose.THINK, now, PUZZLED_MS) else Mood(Pose.SAD, now, SAD_MS)
        after.picks.size > before.picks.size -> Mood(Pose.HAPPY, now, HAPPY_MS)
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
