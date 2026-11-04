package si.lanisce.lani.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.game.FirstWords
import si.lanisce.lani.game.GameIntroSpeech
import si.lanisce.lani.game.GameState
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.Confetti
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.SloBlueDeep

/**
 * The first thing after the founding (docs/plans/05-built/first-minutes.md; game/FirstWords): the village the learner just
 * founded, its place said aloud, a neighbour's greeting said aloud with what it means, and two answers: a tap on the
 * greeting is their first word in the village. A wrong tap only hears it again. Then "🔥 Prižgi ogenj": the map. It takes the
 * place of the three steps of ui/game/GameIntro for a learner who came through the first run; what those explain comes as
 * the village's tips (game/Guidance), when it matters. "Skip" and Back leave it at once.
 */
@Composable
fun FirstMoment(vm: AppViewModel, village: GameState) {
    val pair = L10n.pair
    val voice = vm.hintVoice
    val auto = vm.introVoice.auto && !vm.introVoice.screenReader
    val culture = remember(vm.culture) { si.lanisce.lani.game.culture.VillageCulture.home.id }
    val choices = remember(pair) { FirstWords.choices(pair) }
    val opening = remember(pair) { FirstWords.opening(pair) }
    var won by remember { mutableStateOf(false) }
    var missed by remember { mutableStateOf(false) }
    fun leave() {
        voice.stop()
        vm.firstMomentDone(won)
    }
    BackHandler { leave() }
    LaunchedEffect(pair) {
        runCatching { vm.speaker.preload(FirstWords.learned(pair)) }
        if (auto) voice.play(opening, needsBase = false)
    }
    DisposableEffect(Unit) { onDispose { voice.stop() } }

    @Composable
    fun Place(modifier: Modifier = Modifier) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LandPreview(
            village, culture,
            Modifier.fillMaxWidth().aspectRatio(1.9f).clip(RoundedCornerShape(16.dp)).semantics { contentDescription = "village" },
        )
        Text(GameIntroSpeech.arrive(pair.target, pair), color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(GameIntroSpeech.arrive(pair.base, pair), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }

    @Composable
    fun Greeting(modifier: Modifier = Modifier) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!won) {
            Text(inBase("firstRun.who"), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Text("👋 ${FirstWords.greeting(pair.target)}", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(FirstWords.greeting(pair.base), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Text(bi("firstRun.sayBack"), color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (missed) Text(bi("firstRun.again"), color = Color(0xFFFFD1D8), textAlign = TextAlign.Center)
            for (c in choices) BigButton(c.text, onClick = {
                if (c.right) {
                    won = true
                    voice.play(FirstWords.won(pair), needsBase = false)
                } else {
                    missed = true
                    voice.play(FirstWords.again(pair), needsBase = false)
                }
            }, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.heightIn(min = 64.dp))
        } else {
            Text("🔥", style = MaterialTheme.typography.displayLarge)
            Text(bi("firstRun.won"), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            BigButton("🔥  ${bi("gameIntro.lightFire")}", onClick = ::leave, color = FlameOrange)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050A18), SloBlueDeep, Color(0xFF3A1E1A))))) {
        // a tablet on its side: the place beside the greeting, so the answers are on the screen without scrolling
        val side = maxWidth >= 840.dp && maxWidth > maxHeight
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ReadAloudButton(voice, if (won) FirstWords.won(pair) else opening, color = Color.White)
                Spacer(Modifier.weight(1f))
                if (!won) TextButton(onClick = ::leave) { Text(bi("common.skip"), color = Color.White.copy(alpha = 0.7f)) }
            }
            if (side) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Place(Modifier.weight(1f))
                Greeting(Modifier.weight(1f))
            } else Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Place(Modifier.fillMaxWidth())
                Greeting(Modifier.fillMaxWidth())
            }
        }
        if (won) Confetti(key = "first-word")
    }
}
