package si.lanisce.lani.game.scene

import kotlinx.serialization.Serializable
import si.lanisce.lani.game.GameState
import java.time.LocalDate

// Stage directions (companion/SCENES.md, "Stage directions"): a line or a reply sends people of its scene to spots of the
// art's stage ([SceneArt.stage]), in a pose: Luka walks into the tent and sits by the lantern, Zala hides behind the door.
// Like the sky and the effects, a cue starts with its line and a reaction's passes with the learner's next pick; where
// the dialog leaves them goes back when it closes, unless it says "act_stays" (then until midnight: GameState.sceneAct).

/** How someone is on their spot: standing, sitting, crouching, hidden (only a clue shows), peeking out, lying. */
enum class Stance(val id: String) {
    STAND("stand"), SIT("sit"), CROUCH("crouch"), HIDE("hide"), PEEK("peek"), LIE("lie");

    companion object {
        /** The stance of a pose's name; null for one this app doesn't know (a later app's): the cue is let go. */
        fun of(id: String?): Stance? = entries.firstOrNull { it.id == id }

        /** Every pose's name, as the files write them. */
        val ids: List<String> = entries.map { it.id }
    }
}

/**
 * A stage direction for one person: go [to] a spot of the art's stage, in [pose] (a [Stance]'s name). `to` alone: they
 * stand there; `pose` alone: they take it where they are.
 */
@Serializable
data class ActCue(val to: String? = null, val pose: String? = null)

/** Where someone is on the stage: at [spot] (one of the art's, [SceneArt.stage]) in [pose] (a [Stance]'s name). */
@Serializable
data class Placement(val spot: String, val pose: String = "stand") {
    val stance: Stance get() = Stance.of(pose) ?: Stance.STAND

    /** After [cue]: its spot, standing there unless it says a pose; or its pose, here. */
    operator fun plus(cue: ActCue): Placement = Placement(cue.to ?: spot, cue.pose ?: if (cue.to != null) Stance.STAND.id else pose)

    companion object {
        fun at(spot: String) = Placement(spot)
    }
}

/** Whether a line or a reply to a right choice moves someone (a reaction's cue passes with it, see [Dialog.actKept]). */
val Dialog.hasAct: Boolean get() = lines.any { l -> l.act.isNotEmpty() || l.answers.any { it.reply?.act.orEmpty().isNotEmpty() } }

/** Whether where the dialog leaves people stays for the rest of the day: only when it says so ([Dialog.actStays]). */
val Dialog.actKept: Boolean get() = hasAct && actStays == true

/**
 * Where the people the dialog's cues moved are, once it has come to line [at] with the learner's right [picks] (see
 * [skyAt]), from [base]: everyone's placement before it (their spot in the scene, and what today keeps of earlier
 * dialogs). Only those its cues named; someone [base] lacks (not in the scene) is let go.
 */
fun Dialog.actAt(at: Int, picks: List<Int>, base: Map<String, Placement>): Map<String, Placement> {
    val moved = LinkedHashMap<String, Placement>()
    played(at, picks) { _, _, act -> for ((who, cue) in act) (moved[who] ?: base[who])?.let { moved[who] = it + cue } }
    return moved
}

/** [cue] applied over [placed] (a reaction's, while it lasts), from [base] for whoever it names first. */
fun actOver(placed: Map<String, Placement>, cue: Map<String, ActCue>, base: Map<String, Placement>): Map<String, Placement> {
    if (cue.isEmpty()) return placed
    val out = LinkedHashMap(placed)
    for ((who, c) in cue) (out[who] ?: base[who])?.let { out[who] = it + c }
    return out
}

/**
 * Where finished dialogs left people today (with `"act_stays": true`: Luka by the lantern while it rains), by scene id →
 * person id, kept in [GameState.sceneAct] on the day [on] (ISO date), until midnight.
 */
@Serializable
data class DayAct(val on: String, val scenes: Map<String, Map<String, Placement>> = emptyMap()) {
    companion object {
        /** Today's lasting placements in [scene]. */
        fun of(state: GameState?, today: LocalDate, scene: String): Map<String, Placement> =
            state?.sceneAct?.takeIf { it.on == today.toString() }?.scenes?.get(scene).orEmpty()

        /** [state] once [dialog] of [scene] ended with the people it moved at [end]: kept when the dialog keeps them. */
        fun keep(state: GameState, scene: String, dialog: Dialog, end: Map<String, Placement>, today: LocalDate): GameState {
            if (!dialog.actKept || end.isEmpty()) return state
            val day = today.toString()
            val scenes = state.sceneAct?.takeIf { it.on == day }?.scenes.orEmpty()
            val next = DayAct(day, scenes + (scene to (scenes[scene].orEmpty() + end)))
            return if (next == state.sceneAct) state else state.copy(sceneAct = next)
        }
    }
}

/** Someone on their way from [from] to [to] since [start] (seconds of [SkyEase.clock]): a walk, a dash into hiding, a pop out. */
data class Move(val from: Placement, val to: Placement, val start: Double) {
    /** Still on the way at [now] ([Stage.MOVE_S] at most: the painters finish every move by then). */
    fun on(now: Double): Boolean = from != to && now - start < Stage.MOVE_S
}

/**
 * The stage on screen: where each person is headed ([target]) and who is on their way there. A new placement starts a
 * move from where they were headed; the painter draws it from the time since ([PersonInScene.moveAge]), so the scene's
 * canvas and its closer look agree.
 */
data class StageEase(val moves: Map<String, Move> = emptyMap()) {
    val target: Map<String, Placement> get() = moves.mapValues { it.value.to }

    /** On toward [next] (everyone's placement: the scene's spots, today's, the dialog's) from [now]. */
    fun toward(next: Map<String, Placement>, now: Double): StageEase {
        if (next == target) return this
        val out = LinkedHashMap<String, Move>()
        for ((id, to) in next) {
            val m = moves[id]
            out[id] = when {
                m == null -> Move(to, to, now) // someone new is simply there
                m.to == to -> m
                else -> Move(m.to, to, now)
            }
        }
        return StageEase(out)
    }

    /** Whether someone is on their way at [now]. */
    fun moving(now: Double): Boolean = moves.values.any { it.on(now) }

    /** [people] as they are on the stage at [now]: at their placement, or on their way there. */
    fun apply(people: List<PersonInScene>, now: Double): List<PersonInScene> = people.map { p ->
        val m = moves[p.id]
        // someone asleep stays in their bed
        if (m == null || p.pose == Pose.SLEEP) p
        else if (m.on(now)) p.copy(slot = m.to.spot, stance = m.to.stance, from = m.from.spot, fromStance = m.from.stance, moveAge = (now - m.start).toFloat())
        else p.copy(slot = m.to.spot, stance = m.to.stance, from = null, fromStance = Stance.STAND, moveAge = 0f)
    }

    companion object {
        val NONE = StageEase()

        /** Resting at [placed]. */
        fun still(placed: Map<String, Placement>) = StageEase(placed.mapValues { Move(it.value, it.value, 0.0) })
    }
}

/** The stage's rules that don't depend on a painter. */
object Stage {
    /** A move (a walk, a dash into hiding, a pop out and a walk on) is over by then, in seconds; the painters keep to it. */
    const val MOVE_S = 2.6

    /** The spots of [art]'s stage by name; empty: it has none (no one can be moved there). */
    fun spots(art: String): Map<String, SceneArt.Spot> = SceneArt.stage[art].orEmpty().associateBy { it.name }

    /**
     * Where everyone of [scene] is before a dialog moves them: their spot ([ScenePerson.slot]) standing, over it what today
     * keeps ([kept], [DayAct]), as far as the art's stage has the spot and the pose.
     */
    fun base(scene: SceneSpec, kept: Map<String, Placement>): Map<String, Placement> {
        val spots = spots(scene.art)
        if (spots.isEmpty()) return emptyMap()
        val home = scene.people.filter { it.slot in spots }.associate { it.id to Placement.at(it.slot) }
        return home + kept.filter { (id, p) -> id in home && fits(spots, p) }
    }

    /**
     * Whoever of [scene] a finished dialog left somewhere for the day ([kept], [DayAct]: Luka by the lantern while it rains)
     * and is in the village ([present] ids; null for everyone): still there once their happening is done, at their spot
     * (the stage then puts them where they were left).
     */
    fun stayed(scene: SceneSpec, kept: Map<String, Placement>, present: Set<String>?): List<PersonInScene> =
        scene.people.filter { it.id in kept && (present == null || it.villager == null || it.villager in present) }.map { PersonInScene(it.id, it.art, it.slot) }

    /** [placed] without what [art]'s stage can't show: a spot it hasn't, a pose the spot hasn't (a later app's, a typo). */
    fun shown(art: String, placed: Map<String, Placement>): Map<String, Placement> {
        val spots = spots(art)
        return placed.filterValues { fits(spots, it) }
    }

    private fun fits(spots: Map<String, SceneArt.Spot>, p: Placement): Boolean {
        val s = spots[p.spot] ?: return false
        return Stance.of(p.pose) != null && p.pose in s.poses
    }

    /** The object slots of [art] a tap turn's places [taps] need to be tappable: the things among them, and what hides its spots. */
    fun tapSlots(art: String, taps: Collection<String>): Set<String> {
        if (taps.isEmpty()) return emptySet()
        val slots = SceneArt.objects[art].orEmpty()
        return taps.filter { it in slots }.toSet() + SceneArt.stage[art].orEmpty().filter { it.name in taps }.mapNotNull { it.cover }
    }

    /**
     * Which of a tap turn's places [taps] (companion/SCENES.md, "Tap turns") a tap on [art]'s picture is: the person tapped
     * ([target]) or their spot ([spotOf]); the thing tapped, or a spot it covers (the door for behind the door); else the
     * spot whose area the tap is in ([spot], the painter's). Null: none of them.
     */
    fun tapped(art: String, target: SceneTarget?, spot: String?, taps: Collection<String>, spotOf: (String) -> String?): String? {
        when (target) {
            is SceneTarget.Person -> {
                if (target.id in taps) return target.id
                spotOf(target.id)?.takeIf { it in taps }?.let { return it }
            }
            is SceneTarget.Thing -> {
                if (target.slot in taps) return target.slot
                SceneArt.stage[art].orEmpty().firstOrNull { it.cover == target.slot && it.name in taps }?.let { return it.name }
            }
            null -> Unit
        }
        return spot?.takeIf { it in taps }
    }
}
