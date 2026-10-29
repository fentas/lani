package si.lanisce.lani.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.scene.Face
import si.lanisce.lani.game.scene.Framing
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.ui.Portrait
import kotlin.math.ceil

/**
 * The canvas of a head-alone portrait ([Framing.HEAD]) [size] on the screen: whole-number scaling (crisp pixels) that
 * fills it, with a canvas of about [AVATAR_CANVAS] px: the head (15 px with the hair) and its shoulders, room by it for
 * what the face adds (an emote), shown big enough to read the face at a bubble's size.
 */
@Composable
fun avatarPx(size: Dp): Int {
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val scale = ceil(px / AVATAR_CANVAS.toDouble()).toInt().coerceAtLeast(1)
    return (px / scale).coerceAtLeast(18)
}

/** About this many canvas px wide, a head-alone portrait (see [avatarPx]). */
const val AVATAR_CANVAS = 26

/**
 * A small still face: [art] (with [seed]) with [face], the head alone, in a circle ([size]); a line's face in a
 * conversation's bubble, drawn once and kept ([si.lanisce.lani.ui.Portrait]'s stills). TalkBack hears nothing: the line it
 * stands by says who speaks.
 */
@Composable
fun FaceAvatar(art: String, seed: Int, face: Face?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer)
            .border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Portrait(art, Pose.IDLE, Modifier.size(size), px = avatarPx(size), animate = false, seed = seed, face = face ?: Face.NEUTRAL, framing = Framing.HEAD)
    }
}

/**
 * Whom the learner talks to, alive: [art] (with [seed]) as [look] has them (talking, listening, the face of their line, a
 * reaction), the head alone in a rounded square [size] (a dialog panel's header, a talk's bar). Still while [animate] is
 * off (a bigger portrait of them shows: the sheet's stage) or the system's animations are. TalkBack: "Mojca, se smeji ·
 * laughs".
 */
@Composable
fun SpeakerPortrait(art: String, seed: Int, name: String, look: Look, size: Dp, modifier: Modifier = Modifier, animate: Boolean = true) {
    val reduced = rememberReducedMotion()
    val shape = RoundedCornerShape(size / 4)
    Box(
        modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.tertiaryContainer)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .semantics { contentDescription = described(name, look.pose, look.face) },
    ) {
        Portrait(
            art, if (animate && !reduced) look.portraitPose else look.pose, Modifier.size(size), px = avatarPx(size),
            animate = animate && !reduced, seed = seed, face = look.face, framing = Framing.HEAD,
        )
    }
}
