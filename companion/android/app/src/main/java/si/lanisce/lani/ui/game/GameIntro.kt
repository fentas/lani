package si.lanisce.lani.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.Res
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.l10n.inBase

/** First visit: arriving in the valley, what feeds the village, what threatens it. */
@Composable
fun GameIntro(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    BackHandler { if (step > 0) step-- else onDone() }
    val flicker = rememberInfiniteTransition(label = "fire").animateFloat(0.9f, 1.12f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "f").value
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF050A18), SloBlueDeep, Color(0xFF3A1E1A))),
        ),
    ) {
        // A warm glow from the fire, breathing.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp).alpha(0.55f * flicker)
                .background(Brush.verticalGradient(listOf(Color.Transparent, FlameOrange.copy(alpha = 0.6f)))),
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone) { Text(bi("common.skip"), color = Color.White.copy(alpha = 0.7f)) }
            }
            AnimatedContent(
                step,
                modifier = Modifier.weight(1f),
                transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut() },
                label = "intro",
            ) { i ->
                // Centred, and scrollable when a small screen or a large font doesn't fit a step.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (i) {
                        0 -> {
                            Text("🏔️", style = MaterialTheme.typography.displayLarge)
                            Text("🔥", style = MaterialTheme.typography.displayMedium, modifier = Modifier.scale(flicker))
                            Title(inTarget("gameIntro.myVillage"))
                            Body(inTarget("gameIntro.arrive"))
                            Sub(inBase("gameIntro.arrive"))
                        }
                        1 -> {
                            Title(inTarget("gameIntro.learningBuilds"))
                            Sub(inBase("gameIntro.learningBuilds"))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (r in Res.entries) ResRow(r)
                            }
                        }
                        else -> {
                            Text("🐺  ⛈️  🧳", style = MaterialTheme.typography.displaySmall)
                            Title(inTarget("gameIntro.buildDefendGrow"))
                            Body(inTarget("gameIntro.villagersEat"))
                            Sub(inBase("gameIntro.villagersEat"))
                        }
                    }
                }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                repeat(3) { i ->
                    Box(Modifier.padding(4.dp).size(if (i == step) 10.dp else 8.dp).clip(CircleShape).background(if (i == step) XpGold else Color.White.copy(alpha = 0.3f)))
                }
            }
            BigButton(
                if (step < 2) bi("common.next") else "🔥  ${bi("gameIntro.lightFire")}",
                onClick = { if (step < 2) step++ else onDone() },
                color = if (step < 2) Color.White.copy(alpha = 0.18f) else FlameOrange,
            )
        }
    }
}

@Composable
private fun Title(t: String) = Text(t, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)

@Composable
private fun Body(t: String) = Text(t, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

@Composable
private fun Sub(t: String) = Text(t, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)

@Composable
private fun ResRow(r: Res) {
    Surface(color = Color.White.copy(alpha = 0.08f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.label(), color = r.color(), fontWeight = FontWeight.Bold)
                Text("← ${r.skillLabel()}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

