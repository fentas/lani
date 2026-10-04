package si.lanisce.lani.game

import si.lanisce.lani.data.Exercise
import kotlin.math.ceil

/**
 * How long a run takes, and how long an event's run gets against the clock (companion/GAME.md, "Events"). An event had
 * 50 s and 4 s a question whatever its questions were; hearing a sentence and typing it takes longer than that, so a
 * run that listens gets [SHARE] of what its questions take ([seconds], the intro's estimate), when that is more. Never
 * less than before: a run without listening keeps the old time. Pure.
 */
internal object RunTime {
    /** A run that listens gets this share of the time its questions take. */
    const val SHARE = 0.6f

    /** About how long [ex] takes, in seconds: what the intro's "⏱ ~5 min" adds up, and what a listening run's clock is made of. */
    fun seconds(ex: Exercise): Int = when (ex) {
        is Exercise.Flashcard -> 10
        is Exercise.Choice -> if (ex.audio != null) 20 else 15
        is Exercise.Multi -> 25
        is Exercise.Cloze -> 25
        is Exercise.Dictation, is Exercise.Reorder, is Exercise.Scenario -> 30
        is Exercise.Translate -> if (ex.grade == "claude") 90 else 35
        is Exercise.Speak -> 40
        is Exercise.Free -> 120
        is Exercise.Unsupported -> 5
    }

    /** The old flat time: 50 s and 4 s a question. */
    fun flat(questions: Int): Int = 50 + 4 * questions

    /** Whether [ex] is heard: a sound whose meaning is picked, a dictation, a gap or word chips by ear. */
    fun heard(ex: Exercise): Boolean = ex.heard != null

    /** The clock goes in half minutes: it is announced as such ("2 minuti in pol · 2½ minutes"). */
    const val STEP = 30

    /**
     * The seconds [exercises] get against the clock: the flat time, or for a run that listens [SHARE] of what its questions
     * take, when that is more; up to the next half minute.
     */
    fun limit(exercises: List<Exercise>): Int {
        val flat = flat(exercises.size)
        val time = if (exercises.none(::heard)) flat else maxOf(flat, ceil(exercises.sumOf(::seconds) * SHARE).toInt())
        return ceil(time / STEP.toDouble()).toInt() * STEP
    }
}
