package si.lanisce.lani.game.scene

import kotlin.math.max

// Small gotchas in the scenes (companion/SCENES.md, "Pokes"): Jan taps the cat in the kitchen and it stretches; again,
// and it purrs; a third time and it dashes off, back twenty seconds later. A painter declares what in its scene reacts
// ([ScenePainter.pokes]); the scene view plays it besides what the tap does anyway (the word), and the painter draws it
// from the time since it began ([SceneFrame.pokes]).

/**
 * What something in a scene does when it is tapped: [id] is one of the art's object slots (the pot) or the painter's
 * own name for something that is no word (the cat); [steps] are how long each reaction lasts (s), in the order taps in
 * a row go through them. After the last one it rests [rest] s before a tap starts it over.
 */
data class Poke(val id: String, val steps: List<Double>, val rest: Double = REST_S) {
    init {
        require(steps.isNotEmpty() && steps.all { it > 0.0 }) { "poke $id: every step lasts a while" }
    }

    /** The reaction a tap at [now] starts after [before] (the last one started), or null: it rests, or has only just begun. */
    fun after(before: Poked?, now: Double): Poked? {
        if (before == null) return Poked(0, now)
        val end = before.start + steps[before.step.coerceIn(0, steps.lastIndex)]
        if (now < end && now - before.start < MIN_S) return null // let it show a moment
        if (before.step >= steps.lastIndex) return if (now >= end + rest) Poked(0, now) else null
        return Poked(if (now < end + AGAIN_S) before.step + 1 else 0, now)
    }

    companion object {
        /** A tap this soon after the reaction before it ended (s) goes on to the next one; a later one starts over. */
        const val AGAIN_S = 4.0

        /** A reaction this young (s) isn't cut short by another tap. */
        const val MIN_S = 0.5

        /** The rest after the last reaction (s), by default. */
        const val REST_S = 5.0
    }
}

/** A poke playing: the [step] of its sequence, begun at [start] (seconds of the frame's clock, [SceneFrame.time]). */
data class Poked(val step: Int, val start: Double) {
    /** Seconds since it began, at the frame's [time]. */
    fun age(time: Double): Float = max(0.0, time - start).toFloat()
}

/**
 * The pokes of one scene view over the painter's [declared] ones: how far each thing's taps have come, and what plays
 * now. Pure: the time is always given (the frame clock's seconds).
 */
class Pokes(declared: List<Poke>) {
    private val byId = declared.associateBy { it.id }
    private val last = HashMap<String, Poked>()

    /** A tap on [id] at [now]: its next reaction begins, unless it rests or has just begun (or [id] has none). Whether one began. */
    fun tap(id: String, now: Double): Boolean {
        val poke = byId[id] ?: return false
        val next = poke.after(last[id], now) ?: return false
        last[id] = next
        return true
    }

    /** What plays at [now], for [SceneFrame.pokes]: each reaction until its time is up. */
    fun at(now: Double): Map<String, Poked> {
        if (last.isEmpty()) return emptyMap()
        var out: HashMap<String, Poked>? = null
        for ((id, p) in last) {
            val steps = byId.getValue(id).steps
            if (now >= p.start + steps[p.step]) continue
            (out ?: HashMap<String, Poked>().also { out = it })[id] = p
        }
        return out ?: emptyMap()
    }
}
