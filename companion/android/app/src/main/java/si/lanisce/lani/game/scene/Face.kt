package si.lanisce.lani.game.scene

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A face: what someone's expression says, apart from how they stand ([Pose]): a person can wave and laugh
 * (companion/SCENES.md, "Faces"). The content names one with the key `"face"` (on a spoken line, a choice's reply, a
 * turn's puzzled reaction) and the tutor in a role-play's reply data; [NEUTRAL], the resting face, is never written.
 *
 * Drawn on the people's sprites ([si.lanisce.lani.game.render.scene.PeoplePainter]): the eyes, the brows, the mouth and the
 * blush in the sprite's own pixels, a small pixel emote by the head where it helps the read ([emote]). The same in a scene
 * and in a portrait, which is the sprite cut at the waist.
 */
enum class Face(val key: String) {
    /** Resting, friendly: the dot eyes and the small mouth. The default; never written. */
    NEUTRAL("neutral"),
    /** A smile. */
    HAPPY("happy"),
    /** The eyes shut in arcs, the mouth open, pink cheeks; a ♪ by the head. */
    LAUGH("laugh"),
    /** The eyes wide, the brows up, a round mouth; a ! by the head. */
    SURPRISED("surprised"),
    /** A question asked: one brow up, the eyes on you, a little smile; a ? by the head. */
    CURIOUS("curious"),
    /** "Hm? Kako, prosim?": a misunderstanding; one eye squinted, the brows askew, the mouth wavy; a ? that wobbles. */
    CONFUSED("confused"),
    /** The eyes up, the mouth to one side; … in a small thought bubble. */
    THINKING("thinking"),
    /** The brows up in the middle, a small mouth; a sweat drop. */
    WORRIED("worried"),
    /** Kind disappointment: the eyes down, the brows up in the middle, the corners of the mouth down. */
    SAD("sad"),
    /** Mild and comic, never scary: the brows down in the middle, a pout, red cheeks; a small cross mark. */
    CROSS("cross"),
    /** Blushing, the eyes aside, a small wobbly smile. */
    SHY("shy"),
    /** The eyes shut, a smug smile, the chin up; sparkles. */
    PROUD("proud");

    /** The small pixel emote drawn by the head with this face, or null (the face alone reads). */
    val emote: Emote?
        get() = when (this) {
            LAUGH -> Emote.NOTE
            SURPRISED -> Emote.BANG
            CURIOUS -> Emote.QUESTION
            CONFUSED -> Emote.PUZZLED
            THINKING -> Emote.DOTS
            WORRIED -> Emote.SWEAT
            CROSS -> Emote.VEIN
            PROUD -> Emote.SPARKLES
            NEUTRAL, HAPPY, SAD, SHY -> null
        }

    /**
     * The face it eases through on the way back to [NEUTRAL] once its line is over (a laugh fades to a smile, surprise to
     * attention); null: straight back.
     */
    val softer: Face?
        get() = when (this) {
            LAUGH, PROUD, SHY -> HAPPY
            SURPRISED, CONFUSED -> CURIOUS
            else -> null
        }

    /** Whether the eyes are shut in this face (a laugh, pride): no blink then. */
    val eyesShut: Boolean get() = this == LAUGH || this == PROUD

    companion object {
        /** The names the content and the tutor may write: every face but [NEUTRAL], in their order. */
        val written: List<Face> = entries.filter { it != NEUTRAL }

        /** The face named [key], leniently: any case, blanks around it; an unknown name or none is [NEUTRAL]. */
        fun of(key: String?): Face {
            val k = key?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return NEUTRAL
            return entries.firstOrNull { it.key == k } ?: NEUTRAL
        }

        /** The face named [key], or null without one or for an unknown name (the content's `face`, as the app reads it). */
        fun orNull(key: String?): Face? = of(key).takeIf { it != NEUTRAL }
    }
}

/**
 * The face each pose brings when nothing names one: a hop of joy smiles, a cheer laughs, a shrug is sad, a hand at the chin
 * thinks, a wave smiles. Every mood of the scenes, the stage and the dialogs gets its face so.
 */
val Pose.face: Face
    get() = when (this) {
        Pose.HAPPY, Pose.WAVE -> Face.HAPPY
        Pose.CHEER -> Face.LAUGH
        Pose.SAD -> Face.SAD
        Pose.THINK -> Face.THINKING
        Pose.IDLE, Pose.TALK, Pose.LISTEN, Pose.SLEEP -> Face.NEUTRAL
    }

/** The small pixel signs by the head (drawn, never emoji): what a face adds where it helps the read. */
enum class Emote { NOTE, BANG, QUESTION, PUZZLED, DOTS, SWEAT, VEIN, SPARKLES }

/**
 * The content's `"face"` (a spoken line's, a reply's, a puzzled reaction's), read leniently: a name the app doesn't know (a
 * newer bridge's), a value of another type and "neutral" are all [Face.NEUTRAL], no face of its own. Written as its name.
 * Use it on a `Face?` that is null without the key: `@Serializable(with = FaceSerializer::class) val face: Face? = null`.
 */
object FaceSerializer : KSerializer<Face> {
    override val descriptor = PrimitiveSerialDescriptor("si.lanisce.lani.Face", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Face {
        val raw = if (decoder is JsonDecoder) (decoder.decodeJsonElement() as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        else decoder.decodeString()
        return Face.of(raw)
    }

    override fun serialize(encoder: Encoder, value: Face) {
        encoder.encodeString(value.key)
    }
}

/** The face named, if it is one: null for none and for [Face.NEUTRAL] (the resting face, the content's no face). */
val Face?.named: Face? get() = this?.takeIf { it != Face.NEUTRAL }
