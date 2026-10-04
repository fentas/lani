package si.lanisce.lani.ui.villagers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.ui.stage.portraitPx
import si.lanisce.lani.ui.Portrait
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.lookUpIn
import si.lanisce.lani.ui.words.lookUpWords
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.l10n.inBase
import java.time.LocalDate
import kotlin.math.roundToInt

// Small pieces the register, the villager page, the card and the result cards share.

/** The friendship colour: hearts, bars, the "+5 ♥" chips. */
val HeartPink = Color(0xFFE0457B)

/** ♥ as a glyph in the text's colour, not the red emoji. */
const val HEART = "♥\uFE0E"

/** [text] with its hearts as glyphs in the text's colour. */
fun hearts(text: String): String = text.replace("♥", HEART)

/** A soft pink behind friendship news, for the light and the dark theme. */
@Composable
fun heartContainer(): Color = if (isSystemInDarkTheme()) Color(0xFF45202F) else Color(0xFFFFE4EE)

/**
 * The friendship level as hearts: one per level after the first (♥ reached, ♡ not yet). TalkBack reads
 * the level's name instead.
 */
@Composable
fun Hearts(level: Int, female: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp, pop: Boolean = false) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pop) {
        if (!pop) return@LaunchedEffect
        scale.snapTo(0.2f)
        scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
    }
    val name = VillagerLogic.levelName(level, female)
    Row(
        modifier.clearAndSetSemantics { contentDescription = "${bi("villagerParts.friendship")}: $name" },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..Bonds.NAMES.lastIndex) {
            val on = level >= i
            Text(
                if (on) HEART else "♡",
                color = if (on) HeartPink else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * (size.value / 16f)),
                modifier = if (on && i == level) Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value } else Modifier,
            )
        }
    }
}

/** Hearts rising and fading around their middle: a friendship reached a new level. Replays when [key] changes. */
@Composable
fun HeartBurst(key: Any?, modifier: Modifier = Modifier) {
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        val spread = listOf(-44f, -22f, 0f, 22f, 44f)
        spread.forEachIndexed { i, x ->
            val lag = (i % 2) * 0.12f
            val p = ((t.value - lag) / (1f - lag)).coerceIn(0f, 1f)
            Text(
                HEART,
                color = HeartPink,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.graphicsLayer {
                    translationX = x * density * (0.4f + p)
                    translationY = -p * (36f + 10f * (i % 3)) * density
                    val s = if (p < 0.25f) p / 0.25f else 1f
                    scaleX = 0.6f + 0.6f * s; scaleY = 0.6f + 0.6f * s
                    alpha = if (p < 0.7f) 1f else (1f - p) / 0.3f
                },
            )
        }
    }
}

/**
 * How the friendship grew, on a result card: "+5 ♥ · 🐑 Luka", the new level with hearts when one was
 * reached, and what they'll remember. A gift the level brought is a present of its own, beside it
 * (si.lanisce.lani.ui.game.GiftReveals).
 */
@Composable
fun FriendChip(g: FriendGain, modifier: Modifier = Modifier) {
    Surface(color = heartContainer(), shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (g.points > 0) Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = bi("villagerParts.friendshipPoints", "short" to g.short, "points" to g.points) },
            ) {
                Text(hearts(g.plus), color = HeartPink, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text("${g.emoji} ${g.short}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
            if (g.leveledUp) {
                Box(contentAlignment = Alignment.Center) {
                    HeartBurst(g)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = HeartPink)) { append(HEART) }
                            append(" " + g.levelText.removePrefix("♥ "))
                        },
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall,
                    )
                }
            } else if (g.firstMeeting) {
                Text("🤝 ${bi("villagerParts.nowKnowEachOther")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            g.memory?.let { m ->
                Text(
                    "💭 ${inTarget("villagerParts.willRemember", "who" to g.short, "g" to if (g.female) "f" else "m", "memory" to m.sl)}" +
                        (m.en.takeIf { it.isNotBlank() }?.let {
                            " · ${inBase("villagerParts.willRemember", "who" to g.short, "g" to if (g.female) "f" else "m", "memory" to it)}"
                        } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (g.help > 0) Text(
                "+${bi("villagerParts.help", "help" to g.help)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * A villager's pixel portrait in a rounded frame; [silhouette] darkens it to a shadow with a "?" (someone
 * who arrives in a later age).
 */
@Composable
fun VillagerPortrait(v: Villager, size: Dp, modifier: Modifier = Modifier, pose: Pose = Pose.IDLE, silhouette: Boolean = false, animate: Boolean = true) {
    val shade = if (isSystemInDarkTheme()) Color(0xFF05070C) else Color(0xFF1B2130)
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 5))
            .border(2.dp, if (silhouette) MaterialTheme.colorScheme.outlineVariant else HeartPink.copy(alpha = 0.35f), RoundedCornerShape(size / 5))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // the portrait's own pixels, scaled up whole to fill the frame
        val px = portraitPx(size)
        Portrait(
            v.art, pose,
            Modifier.size(size).then(if (silhouette) Modifier.drawWithContent { drawContent(); drawRect(shade, alpha = 0.86f) } else Modifier),
            px = px,
            animate = animate && !silhouette,
            seed = v.id.hashCode(), // newcomers who share a sprite look different
        )
        if (silhouette) Text("?", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
    }
}

/** A line a villager says: Slovene big (a tap on a word looks it up), English small below, 🔊 in their voice. */
@Composable
fun SpeechBubble(vm: AppViewModel, v: Villager, said: Said, modifier: Modifier = Modifier) {
    val popped = remember(said) { Animatable(0.85f) }
    LaunchedEffect(said) { popped.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
        modifier = modifier.graphicsLayer {
            scaleX = popped.value; scaleY = popped.value
            alpha = ((popped.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
        },
    ) {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val onWord = lookUpIn(vm, said.sl, said.en, Words.VILLAGER)
            Column(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = "${VillagerLogic.shortName(v.name)}: ${said.sl} · ${said.en}"
                    lookUpWords(said.sl, onWord)
                },
            ) {
                WordText(said.sl, onWord, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                if (said.en.isNotBlank()) Text(said.en, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f))
            }
            SpeakButton(vm.speaker, said.sl, Modifier.padding(start = 4.dp), voiceName = v.speakerVoice, fallback = VillagerLogic.voiceOf(v), person = v.id)
        }
    }
}

/**
 * The friendship in one line: hearts, the level's name and a bar to the next level ("12 / 30 ♥").
 * [pop] makes the newest heart jump (a level reached since last time).
 */
@Composable
fun FriendshipBar(points: Int, female: Boolean, modifier: Modifier = Modifier, pop: Boolean = false, compact: Boolean = false) {
    val level = Bonds.level(points)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hearts(level, female, pop = pop)
            Spacer(Modifier.width(8.dp))
            Text(VillagerLogic.levelName(level, female), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        if (!compact) {
            val next = VillagerLogic.toNext(points)
            LinearProgressIndicator(
                progress = { Bonds.progress(points) },
                color = HeartPink,
                trackColor = HeartPink.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics { },
            )
            Text(
                if (next == null) bi("villagerParts.closestBond", "HEART" to HEART)
                else "${next.first} / ${next.second} $HEART · ${bi("villagerParts.nextLevel")}: ${VillagerLogic.levelName(level + 1, female)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The scripted greeting as it plays: the lines said so far, and whether they're still speaking. */
@Stable
class Greeting {
    var said by mutableStateOf<List<Said>>(emptyList())
        internal set
    var speaking by mutableStateOf(false)
        internal set
    /** Bumped when a greeting starts, for the portrait's pose. */
    var round by mutableIntStateOf(0)
        internal set
    private var job: Job? = null

    val done: Boolean get() = said.isNotEmpty() && !speaking

    /**
     * "Pogovori se": [v] greets Jan with the warmest line their friendship allows, then brings up a memory,
     * each line spoken in their voice; afterwards they've met.
     */
    fun start(scope: CoroutineScope, vm: AppViewModel, v: Villager) {
        if (speaking) return
        job = scope.launch {
            round++
            said = emptyList()
            speaking = true
            try {
                val lines = VillagerLogic.greeting(v, vm.villagers.bond(vm.game.state, v.id), LocalDate.now())
                for (l in lines) {
                    said = said + l
                    vm.speaker.say(l.sl, voiceName = v.speakerVoice, fallback = VillagerLogic.voiceOf(v), person = v.id)
                    delay((900L + 70L * l.sl.length).coerceAtMost(5_500L))
                }
                vm.villagers.greeted(v.id)
            } finally {
                speaking = false
            }
        }
    }
}

@Composable
fun rememberGreeting(id: String): Greeting = remember(id) { Greeting() }

/** The portrait's pose: a wave on arrival, talking while they greet, a smile right after, else idle. */
@Composable
fun villagerPose(id: String, greeting: Greeting): Pose {
    var waving by remember(id) { mutableStateOf(true) }
    LaunchedEffect(id) {
        delay(2_400)
        waving = false
    }
    var happy by remember(id) { mutableStateOf(false) }
    LaunchedEffect(greeting.round, greeting.speaking) {
        if (greeting.round > 0 && !greeting.speaking) {
            happy = true
            delay(1_800)
            happy = false
        }
    }
    return when {
        greeting.speaking -> Pose.TALK
        happy -> Pose.HAPPY
        waving -> Pose.WAVE
        else -> Pose.IDLE
    }
}

/** A small tag: "📋 Prošnja · Request". */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}
