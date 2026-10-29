package si.lanisce.lani.ui.stage

import si.lanisce.lani.game.scene.Face
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.l10n.bi

/**
 * How the person someone talks to looks now (companion/SCENES.md, "Faces"): their [pose], their [face], whether they're
 * [talking] (their line being said), and since when the face shows ([faceSince], uptime ms: what it adds by the head pops
 * in then).
 */
data class Look(val pose: Pose = Pose.IDLE, val face: Face = Face.NEUTRAL, val talking: Boolean = false, val faceSince: Long = 0L) {
    /** The pose for a portrait, which has no talking of its own: [Pose.TALK] while talking and otherwise at rest. */
    val portraitPose: Pose get() = if (talking && pose == Pose.IDLE) Pose.TALK else pose

    companion object {
        val REST = Look()
    }
}

/**
 * The line someone said last: its [face] (null: none of its own), when it showed ([shownAt]) and when it was last heard to
 * its end ([quietAt]: its showing, until it has been said).
 */
data class LineFace(val face: Face?, val shownAt: Long, val quietAt: Long = shownAt)

/**
 * How a face follows the conversation, pure (the dialogs' [si.lanisce.lani.ui.scene.DialogMood] and the free talk use it):
 * a line's face shows while it is said and holds [HOLD_MS] after it was heard, then eases back to the resting face, through
 * its softer one ([Face.softer]: a laugh fades through a smile) for [SOFT_MS]. While someone listens (the learner's 🎤
 * open) they lean in, attentive; a reaction to what the learner did (a mood: a smile at a right answer) shows until its
 * time is up, unless a line said since has a face of its own.
 */
object Expression {
    /** A line's face holds this long after it was heard. */
    const val HOLD_MS = 3_500L

    /** Then its softer face, this long, on the way back to rest. */
    const val SOFT_MS = 900L

    /** The face [line] shows at [now] ([talking]: it is being said), or null once it is back at rest. */
    fun held(line: LineFace?, now: Long, talking: Boolean): Face? {
        val f = line?.face?.takeIf { it != Face.NEUTRAL } ?: return null
        if (talking) return f
        val t = now - line.quietAt
        return when {
            t < HOLD_MS -> f
            t < HOLD_MS + SOFT_MS -> f.softer
            else -> null
        }
    }

    /**
     * The face at [now]: listening, attentive ([Face.NEUTRAL], the pose leans in); talking, the [line]'s own, else the
     * [mood]'s while it lasts; else the newer of the mood (while it lasts) and the line's held face; else at rest.
     * [moodFace] and [moodSince] are the reaction's face and when it began, [moodOn] whether it lasts still.
     */
    fun face(line: LineFace?, moodFace: Face?, moodSince: Long, moodOn: Boolean, now: Long, talking: Boolean, listening: Boolean): Face {
        if (listening) return Face.NEUTRAL
        val held = held(line, now, talking)
        val mood = moodFace?.takeIf { moodOn }
        return when {
            talking -> held ?: mood ?: Face.NEUTRAL
            mood != null && (held == null || line == null || moodSince >= line.quietAt) -> mood
            else -> held ?: Face.NEUTRAL
        }
    }

    /** When the face at [now] may change by itself next (a hold or a mood running out), or null: nothing pending. */
    fun next(line: LineFace?, moodUntil: Long, now: Long, talking: Boolean): Long? {
        val times = buildList {
            if (moodUntil > now) add(moodUntil)
            if (line?.face != null && !talking) {
                listOf(line.quietAt + HOLD_MS, line.quietAt + HOLD_MS + SOFT_MS).filter { it > now }.forEach(::add)
            }
        }
        return times.minOrNull()
    }
}

/** "se smeji · laughs": a face for TalkBack, in the pair of the moment; empty for the resting face. */
fun faceLabel(face: Face?): String = when (face) {
    null, Face.NEUTRAL -> ""
    else -> bi("face.${face.key}")
}

/** "Mojca, se smeji · laughs": who and how, for a portrait's TalkBack; the pose's word when the face says nothing. */
fun described(name: String, pose: Pose, face: Face?): String =
    listOf(name, faceLabel(face).ifEmpty { poseLabel(pose) }).filter { it.isNotEmpty() }.joinToString(", ")
