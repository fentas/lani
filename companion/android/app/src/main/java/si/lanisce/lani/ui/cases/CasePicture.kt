package si.lanisce.lani.ui.cases

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.scene.SceneView

/**
 * The village's picture of the place a "Kje ali kam?" task is about ([sceneId]: the scene that draws it, the school, the
 * market, the forest …), as the scene shows it now, without its people: what the sentence is about, at a glance. Not to
 * touch (a pinch or a drag would scroll the run): one picture, read by TalkBack as the place's.
 */
@Composable
fun CasePicture(vm: AppViewModel, sceneId: String) {
    val scene = vm.scenes.byId(sceneId) ?: return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val h = min(maxWidth * 0.5f, 190.dp)
        Box(
            Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(16.dp))
                .clearAndSetSemantics { contentDescription = bi("cases.picture", "place" to scene.title) },
        ) {
            SceneView(scene = scene, people = emptyList(), highlight = null, onTap = {}, modifier = Modifier.fillMaxWidth().height(h), village = vm.game.state?.seed ?: 0L)
            // the picture isn't to touch: a pointer on it stops at this cover (the sibling under it isn't hit), not consumed,
            // so a drag still scrolls the run
            Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
        }
    }
    Spacer(Modifier.height(12.dp))
}
