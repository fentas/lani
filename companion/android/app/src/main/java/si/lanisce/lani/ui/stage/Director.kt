package si.lanisce.lani.ui.stage

import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.at
import java.time.LocalDate

/**
 * One reaction on the stage: how the companion stands, what they say, whether it is voiced, and how
 * long the pose holds before they settle back to idle (null: until the next cue).
 */
data class Cue(val pose: Pose, val line: VillagerLine?, val voice: Boolean, val holdMs: Long?)

/**
 * Decides the companion's reactions to the exercise flow (pure; the stage composable plays them).
 * Lines come from the companion's own lines by their friendship level, topped up with [Lines], picked
 * by the day's dice so they vary, never the same line twice in a row; only the lines that fit the time of
 * day ([clock]: a line's `when`, no "Lahko noč" in the morning). The companion's voice never talks over
 * the exercise's own audio: a cue that would is shown, not voiced.
 *
 * @param greet the run has no intro before it (the reviews): the first prompt is a greeting
 * @param clock the part of the day now (the phone's, [TimeOfDay.now])
 */
class Director(
    private val who: StagePerson,
    private val seed: Long,
    private val day: LocalDate,
    private val greet: Boolean = false,
    private val clock: () -> TimeOfDay = { TimeOfDay.now() },
) {
    private val last = HashMap<String, String>()
    private var beat = 0

    /**
     * A new exercise [ex] at [index]; [prev] is the one before it. Null keeps the last cue: in a [fast] run,
     * the cheer for the answer just before stays up rather than a lead-in for the same kind of exercise.
     */
    fun prompt(index: Int, ex: Exercise, prev: Exercise?, fast: Boolean = false): Cue? {
        val greeting = if (index == 0 && greet) {
            Lines.warmest(who.lines.greet.at(clock()), who.level)?.let(::remember) ?: say("greet", Lines.greet(who.register))
        } else null
        if (listening(ex)) {
            val listen = say("listen", own(who.lines.listen, Lines.listen(who.register)))
            val line = if (greeting == null || listen == null) listen ?: greeting
            else VillagerLine.join(greeting, listen)
            return Cue(Pose.LISTEN, line, voice = false, holdMs = null)
        }
        if (fast && prev != null && kind(prev) == kind(ex)) return null
        val line = greeting ?: say("lead-in", Lines.leadIns(ex, who.register))
        // Every exercise gets its line on screen; the voice only when the kind changes, so five flashcards
        // in a row don't hear "Se spomniš te besede?" five times.
        val voice = !autoAudioAtPrompt(ex) && (index == 0 || prev == null || kind(prev) != kind(ex))
        return Cue(Pose.TALK, line, voice, holdMs = talkMs(line))
    }

    /** Jan answered [ex]: cheer, a gentle "skoraj", or comfort, never mockery. [fast]: a timed run moves on by itself. */
    fun answer(verdict: Verdict, hints: Int, ex: Exercise, fast: Boolean): Cue {
        val quiet = autoAudioAtAnswer(ex) || (fast && verdict == Verdict.CORRECT)
        return when (verdict) {
            Verdict.CORRECT -> {
                val pose = if (hints > 0 || dice("pose") < 0.5f) Pose.HAPPY else Pose.CHEER
                Cue(pose, say("cheer", own(who.lines.cheer, Lines.cheer(who.register))), !quiet, holdMs = 2_400)
            }
            Verdict.ALMOST -> Cue(Pose.HAPPY, say("almost", Lines.almost(who.register)), !quiet, holdMs = 2_400)
            Verdict.WRONG -> Cue(Pose.SAD, say("comfort", own(who.lines.comfort, Lines.comfort(who.register))), !quiet, holdMs = 2_800)
        }
    }

    /** Jan asked for a hint: the companion thinks along, quietly (Jan is typing). */
    fun hint(): Cue = Cue(Pose.THINK, say("think", Lines.think(who.register)), voice = false, holdMs = 2_200)

    /** The end: a wave, the tally ([correct] of the run's [total], [answered] of them answered), and goodbye. */
    fun end(correct: Int, answered: Int, total: Int): Cue {
        val tally = Lines.closing(correct, answered, total.coerceAtLeast(answered), who.register)
        val bye = say("bye", own(who.lines.bye, Lines.bye(who.register)))
        val line = when {
            tally == null -> bye
            bye == null -> tally
            else -> VillagerLine.join(tally, bye)
        }
        return Cue(Pose.WAVE, line, voice = true, holdMs = talkMs(line) + 1_200)
    }

    /** Their own lines for the time of day, topped up with the built-in ones for it while they have fewer than two. */
    private fun own(own: List<VillagerLine>, generic: List<VillagerLine>): List<VillagerLine> {
        val time = clock()
        return Lines.pool(own.at(time), generic.at(time), who.level)
    }

    private fun dice(key: String): Float = Happenings.roll(seed, day, "stage/${who.name}/$key/$beat")

    private fun say(moment: String, pool: List<VillagerLine>): VillagerLine? {
        beat++
        val line = Lines.pick(pool.at(clock()), who.level, dice(moment), avoid = last[moment]) ?: return null
        last[moment] = line.target
        return line
    }

    private fun remember(line: VillagerLine): VillagerLine {
        beat++
        last["greet"] = line.target
        return line
    }

    companion object {
        /** A listening exercise: the Slovene is heard (a gap or word chips by ear too), and the companion cups an ear. */
        fun listening(ex: Exercise): Boolean = ex.heard != null

        /** The exercise plays its own audio as it comes up: the companion's lead-in stays silent. */
        fun autoAudioAtPrompt(ex: Exercise): Boolean = when (ex) {
            is Exercise.Flashcard -> ex.speak
            is Exercise.Scenario -> ex.line != null
            else -> ex.heard != null
        }

        /** The exercise plays its own audio once answered (a hidden speaking model): the reaction stays silent. */
        fun autoAudioAtAnswer(ex: Exercise): Boolean = ex is Exercise.Speak && !ex.show

        /** The kind of exercise, for "did the kind change?". */
        fun kind(ex: Exercise): String = if (listening(ex)) "listen" else ex::class.simpleName.orEmpty()

        /** About how long a line takes to say, for the talking pose. */
        fun talkMs(line: VillagerLine?): Long = if (line == null) 1_200 else 900L + 65L * line.target.length
    }
}
