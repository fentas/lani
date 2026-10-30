package si.lanisce.lani.ui.scene

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.SceneTalk
import si.lanisce.lani.game.ambient.Sfx
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.Confetti
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.stage.Backdrop
import si.lanisce.lani.ui.stage.Look
import si.lanisce.lani.game.scene.face
import si.lanisce.lani.ui.stage.StagePlace
import si.lanisce.lani.ui.stage.rememberReducedMotion
import si.lanisce.lani.ui.villagers.VillagerLogic
import si.lanisce.lani.ui.villagers.VillagerPortrait

/**
 * A village project's step played over the village (companion/SCENES.md "Project steps"): when the village hasn't the
 * step's scene open today (the square before Vas, the chapel's road, which has no scene), its people stand side by side on
 * a backdrop of where it happens (the forest, the square, the field …, else the leader's place), the leader first and
 * big, and below it the step's dialog is played as a scene's (DialogPanel: each line in its speaker's voice and with their
 * face, a word's card on a tap, the choices, a reaction to a wrong one; a tap turn is chosen, there is no picture to tap
 * in); at its end, the step done. A line's `bell` rings over the village (the chapel's little bell as it is hung: its peal,
 * and a bell swinging in the backdrop's corner with its rings going out); the step's other cues are its scene's.
 */
@Composable
fun ProjectStepScene(vm: AppViewModel, talk: SceneTalk) {
    val step = talk.step ?: return
    // the cast's and the village's own (one who moved in may say a helper's lines)
    val people = remember(step.cast, vm.villagers.all, vm.game.state?.residents) {
        val everyone = vm.villagers.people(vm.game.state)
        step.cast.mapNotNull { p -> everyone.firstOrNull { it.id == p.id }?.let { p.id to it } }.toMap()
    }
    val speakers = remember(step.cast, people) {
        step.cast.associate { p -> p.id to Speaking(p.emoji, SceneWords.voiceOf(p.art, people[p.id]), p.art, (people[p.id]?.id ?: p.villager ?: p.id).hashCode(), p.name) }
    }
    val voice = speakers[talk.person.id]?.voice ?: Spoken(SceneWords.voiceOf(talk.person.art))
    // each new line said aloud in its speaker's voice, and drawn talking meanwhile
    var micOn by remember { mutableStateOf(false) }
    // drawn talking while the line is heard; the learner's own first, in their voice (SceneScreen's dialog)
    val talking = rememberDialogVoice(vm.speaker, talk) { who -> who to (speakers[who]?.voice ?: voice) }
    // whom the learner answers: the one a turn names, else whoever spoke last (the leader at first)
    val facing = talk.run.facing ?: talk.person.id
    val facingArt = step.cast.firstOrNull { it.id == facing }?.art
    val look = rememberPartnerLook(talk.run, talking != null && talking == facing, micOn, facing, child = facingArt?.startsWith("child") == true)
    // an age's party (companion/GAME.md "Ages and their celebrations"): all the village on a festive stage, its music as it begins
    val party = remember(step.party) { step.party?.let { si.lanisce.lani.game.AgeUps.celebration(it) } }
    val place = remember(step.project, step.index, party) { party?.let(::partyPlace) ?: placeOf(vm, step.project, step.index, talk.person.art) }
    LaunchedEffect(talk.key) {
        val c = party ?: return@LaunchedEffect
        vm.ambience.prepare(Sfx.musicSounds(c.music))
        delay(PARTY_MUSIC_MS)
        vm.ambience.music { now -> Sfx.music(c.music, now, fair = si.lanisce.lani.game.Decor.STALLS in c.decor) }
    }
    // its bell over the village: rung as the line that rings it comes, for as long as its peal (Sfx.PEAL_S)
    val fx = talk.run.fx(emptyMap())
    var fxBefore by remember(talk.key) { mutableStateOf(emptyMap<String, Float>()) }
    var rungAt by remember(talk.key) { mutableLongStateOf(0L) }
    LaunchedEffect(fx) {
        val was = fxBefore[BELL] ?: 0f
        val now = fx[BELL] ?: 0f
        vm.ambience.effects(Sfx.VILLAGE, fxBefore, fx)
        if (now - was >= Sfx.RISE) rungAt = System.currentTimeMillis()
        fxBefore = fx
    }
    LaniSheet(onDismissRequest = vm.scenes::closeTalk) {
        Box(Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(Modifier.matchParentSize()) {
                // folded away while the keyboard is up (a typed turn), so the panel has the room (DialogRoom)
                DialogStage {
                    if (party != null) PartyStage(people, step.cast, place, facing, look, talking, party)
                    else Stage(people, step.cast, place, facing, look, talking, rungAt)
                }
                DialogPanel(
                    vm = vm,
                    talk = talk,
                    voice = voice,
                    onChoose = vm.scenes::choose,
                    onNext = vm.scenes::next,
                    onType = vm.scenes::type,
                    onLetMeChoose = vm.scenes::letMeChoose,
                    onHint = vm.scenes::hint,
                    onSay = vm.scenes::say,
                    onEcho = vm.scenes::echo,
                    onEchoSaid = vm.scenes::echoSaid,
                    onClose = vm.scenes::closeTalk,
                    onKeepTalking = null,
                    modifier = Modifier.weight(1f),
                    onListening = { micOn = it },
                    speakers = speakers,
                    // their faces: on the stage over the panel, and in its header (alive) once the stage folds away for the keyboard
                    look = look,
                    livePortrait = keyboardUp(),
                    // over the village "✓ Nazaj v vas"; over Home (its call in "Danes · Today") it stays there: "✓ Naprej"
                    closeLabel = "✓ ${bi(if (vm.screen == si.lanisce.lani.Screen.Village) "arrivals.backVillage" else "common.continue")}",
                )
            }
            if (talk.settled && (talk.step.result?.won == true || party != null)) Confetti(key = "step-${talk.key}")
        }
    }
}

/**
 * Where a step over the village is seen: the backdrop of its scene's kind of place (its forest, square, field, smithy,
 * school, kitchen, fire), else the leader's.
 */
private fun placeOf(vm: AppViewModel, project: String, index: Int, leaderArt: String): StagePlace {
    val spec = si.lanisce.lani.game.Projects.spec(project)
    val art = spec?.let { si.lanisce.lani.game.Projects.play(it, index) }?.scene?.let(vm.scenes::byId)?.art
    return when (art) {
        "forest" -> StagePlace.FOREST
        "square", "market", "church" -> StagePlace.SQUARE
        "field", "hills", "stream", "pond", "apiary" -> StagePlace.FIELD
        "smithy" -> StagePlace.SMITHY
        "school" -> StagePlace.SCHOOL
        "kitchen", "livingroom", "workshop", "cellar", "attic" -> StagePlace.KITCHEN
        "campfire" -> StagePlace.FIRE
        else -> StagePlace.home(leaderArt)
    }
}

/**
 * The step's people side by side on [place]'s backdrop: the leader first, big; whoever is talking is drawn talking, the
 * one the learner answers reacts (a wave, a hop, a puzzled hand to the chin), the rest stand by. A bell rung at [rungAt]
 * (the wall clock's ms; 0: none) swings over them while its peal lasts ([RingingBell]).
 */
@Composable
private fun Stage(
    people: Map<String, si.lanisce.lani.game.villagers.Villager>, cast: List<ScenePerson>, place: StagePlace, facing: String, look: Look,
    talking: String?, rungAt: Long = 0L,
) {
    Box(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))) {
        Backdrop(place, Modifier.matchParentSize(), dim = 0.35f)
        Row(
            Modifier.matchParentSize().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            cast.forEachIndexed { i, p ->
                val v = people[p.id] ?: return@forEachIndexed
                val now = when (p.id) {
                    talking -> Pose.TALK
                    facing -> look.pose
                    else -> Pose.IDLE
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VillagerPortrait(v, if (i == 0) 100.dp else 84.dp, pose = now, face = if (p.id == facing) look.face else now.face)
                    Text(
                        "${v.emoji} ${VillagerLogic.shortName(v.name)}",
                        color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics { },
                    )
                }
            }
        }
        // over the portraits, in the top corner they leave free
        if (rungAt > 0L) RingingBell(rungAt, Modifier.matchParentSize())
    }
}

/** Where an age's party [c] is seen: by the fire at night, or on the square in the evening. */
private fun partyPlace(c: si.lanisce.lani.game.Celebration): StagePlace = when (c.place) {
    si.lanisce.lani.game.PartyPlace.FIRE -> StagePlace.NIGHT
    si.lanisce.lani.game.PartyPlace.SQUARE -> StagePlace.FEAST
}

/** How long after an age's party opens its music starts (ms): its first line said first. */
private const val PARTY_MUSIC_MS = 900L

/**
 * An age's party's stage (companion/GAME.md "Ages and their celebrations"): all the village on [place]'s backdrop, garlands
 * of flags strung across its top (lanterns in them at night), who speaks in the front row (the host first, big), everyone
 * else behind them, smaller; whoever is talking is drawn talking, the one the learner answers reacts.
 */
@Composable
private fun PartyStage(
    people: Map<String, si.lanisce.lani.game.villagers.Villager>, cast: List<ScenePerson>, place: StagePlace, facing: String, look: Look,
    talking: String?, c: si.lanisce.lani.game.Celebration,
) {
    val front = cast.filter { people[it.id] != null }.take(FRONT)
    val back = cast.filter { people[it.id] != null }.drop(FRONT)
    Box(Modifier.fillMaxWidth().height(176.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))) {
        Backdrop(place, Modifier.matchParentSize(), dim = 0.3f)
        // what the village put up for it, at the stage's sides behind its people: the lines speak of it ("Glej, kako lepo
        // gorijo", "Poglej stojnice", "Glej jih, kako plešejo!", "Kaj je zdaj na strehi?")
        PartyDecor(c.decor, night = place.hour?.let { it >= 19f } == true, modifier = Modifier.matchParentSize())
        Garlands(night = place.hour?.let { it >= 19f } == true, modifier = Modifier.matchParentSize())
        Column(Modifier.matchParentSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.Bottom) {
            // everyone else of the village behind, smaller
            if (back.isNotEmpty()) Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((-6).dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom,
            ) {
                for (p in back) people[p.id]?.let { v -> VillagerPortrait(v, 40.dp, pose = if (p.id == talking) Pose.TALK else Pose.IDLE) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                front.forEachIndexed { i, p ->
                    val v = people[p.id] ?: return@forEachIndexed
                    val now = when (p.id) {
                        talking -> Pose.TALK
                        facing -> look.pose
                        else -> Pose.IDLE
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VillagerPortrait(v, if (i == 0) 84.dp else 66.dp, pose = now, face = if (p.id == facing) look.face else now.face)
                        Text(
                            "${v.emoji} ${VillagerLogic.shortName(v.name)}",
                            color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
        // the celebration's name in the top corner, over the garlands
        Text(
            "${c.emoji} ${c.nameText.target}", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clearAndSetSemantics { },
        )
    }
}

/**
 * A party's decorations on its stage (the map's are drawn by FestivePainter): torches on poles at both sides, flames
 * flickering, glowing at [night] (the first camp's evening); the newest roof's gable with the likof's little spruce and its
 * ribbons on the ridge; a plank dance floor along the bottom, a couple turning in a polka at each side; two market stalls
 * under striped awnings, honey hearts and whistles on their counters; tall banners waving on their poles. The bunting is
 * the [Garlands]. Still, with the system's animations off.
 */
@Composable
private fun PartyDecor(decor: List<si.lanisce.lani.game.Decor>, night: Boolean, modifier: Modifier) {
    if (decor.none { it != si.lanisce.lani.game.Decor.BUNTING }) return
    val still = rememberReducedMotion()
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (true) {
            withInfiniteAnimationFrameMillis { }
            t = (System.currentTimeMillis() - start) / 1000f
        }
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val u = w / 100f
        val wood = Color(0xFF6A4A2E); val woodD = Color(0xFF4A3220)
        for (d in decor) when (d) {
            si.lanisce.lani.game.Decor.TORCHES -> for (x in listOf(5f * u, w - 5f * u)) {
                drawLine(woodD, Offset(x, h), Offset(x, h * 0.32f), strokeWidth = 1.4f * u)
                val flick = if (still) 0f else kotlin.math.sin(t * 9f + x).toFloat()
                if (night) drawCircle(Color(0x55FFB347), radius = 9f * u, center = Offset(x, h * 0.28f))
                val flame = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x - 2.2f * u, h * 0.33f); quadraticTo(x - 2.4f * u, h * 0.24f, x + flick * 0.8f * u, h * 0.17f - flick * u)
                    quadraticTo(x + 2.4f * u, h * 0.24f, x + 2.2f * u, h * 0.33f); close()
                }
                drawPath(flame, Color(0xFFFF8A2A))
                drawCircle(Color(0xFFFFE08A), radius = 1.1f * u, center = Offset(x, h * 0.29f))
            }
            si.lanisce.lani.game.Decor.SPRUCE -> {
                // the newest roof's gable at the right, its rafters, the little spruce with ribbons on the ridge
                val x0 = w * 0.64f; val x1 = w * 0.99f; val apex = Offset((x0 + x1) / 2f, h * 0.2f); val eave = h * 0.5f
                val roof = androidx.compose.ui.graphics.Path().apply { moveTo(x0, eave); lineTo(apex.x, apex.y); lineTo(x1, eave); close() }
                drawPath(roof, Color(0xFF8A4A36))
                for (k in 1..4) {
                    val f = k / 5f
                    drawLine(woodD, Offset(x0 + (apex.x - x0) * f, eave + (apex.y - eave) * f), Offset(x1 - (x1 - apex.x) * f, eave + (apex.y - eave) * f), strokeWidth = 0.5f * u)
                }
                drawLine(woodD, Offset(x0, eave), Offset(x1, eave), strokeWidth = 1f * u)
                val sway = if (still) 0f else kotlin.math.sin(t * 1.6f).toFloat() * 0.6f * u
                for (k in 0 until 3) {
                    val tw = (3.2f - k * 0.9f) * u; val ty = apex.y - (k * 2.4f + 1f) * u
                    val tier = androidx.compose.ui.graphics.Path().apply { moveTo(apex.x - tw + sway, ty + 2.6f * u); lineTo(apex.x + sway * 1.4f, ty - 1.8f * u); lineTo(apex.x + tw + sway, ty + 2.6f * u); close() }
                    drawPath(tier, Color(0xFF2E6B3A))
                }
                for ((k, col) in listOf(Color(0xFFE4002B), Color(0xFF0B4EA2), Color(0xFFFFB300)).withIndex()) {
                    val ry = apex.y - (1.2f + k * 1.6f) * u
                    drawLine(col, Offset(apex.x + sway, ry), Offset(apex.x + 4.5f * u + sway * 2f, ry + 3f * u + k * 0.6f * u), strokeWidth = 0.7f * u)
                }
            }
            si.lanisce.lani.game.Decor.FLOOR -> {
                val top = h * 0.9f
                drawRect(wood, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(w, h - top))
                for (k in 0..20) drawLine(woodD, Offset(w * k / 20f, top), Offset(w * k / 20f, h), strokeWidth = 0.3f * u)
                // a couple turning at each side, her skirt flying out
                for ((i, cx) in listOf(13f * u, w - 13f * u).withIndex()) {
                    val a = if (still) 0f else t * 2.6f + i * 1.7f
                    val bob = if (still) 0f else kotlin.math.abs(kotlin.math.sin(t * 5.2f + i)) * 0.8f * u
                    val r = 2.6f * u
                    val him = Offset(cx + kotlin.math.cos(a) * r, top - 6f * u - bob); val her = Offset(cx - kotlin.math.cos(a) * r, top - 6f * u - bob)
                    drawLine(Color(0xFF2A2A34), Offset(him.x, him.y + 1f * u), Offset(him.x, top), strokeWidth = 1.8f * u)
                    drawCircle(Color(0xFFE8B896), radius = 1.3f * u, center = Offset(him.x, him.y - 1.6f * u))
                    val skirt = androidx.compose.ui.graphics.Path().apply {
                        moveTo(her.x, her.y); lineTo(her.x - (2.4f + kotlin.math.abs(kotlin.math.sin(a)) * 1.6f) * u, top); lineTo(her.x + (2.4f + kotlin.math.abs(kotlin.math.cos(a)) * 1.6f) * u, top); close()
                    }
                    drawPath(skirt, Color(0xFFC8303A))
                    drawCircle(Color(0xFFE8B896), radius = 1.3f * u, center = Offset(her.x, her.y - 1.6f * u))
                    drawLine(Color(0xFFE8B896), him, her, strokeWidth = 0.6f * u)
                }
            }
            si.lanisce.lani.game.Decor.STALLS -> for ((i, x0) in listOf(1.5f * u, w - 21.5f * u).withIndex()) {
                val sw = 20f * u; val awn = h * 0.42f; val counter = h * 0.74f
                drawLine(woodD, Offset(x0 + 0.6f * u, awn), Offset(x0 + 0.6f * u, h), strokeWidth = 0.9f * u)
                drawLine(woodD, Offset(x0 + sw - 0.6f * u, awn), Offset(x0 + sw - 0.6f * u, h), strokeWidth = 0.9f * u)
                for (k in 0 until 6) drawRect(if (k % 2 == 0) Color(0xFFC8303A) else Color(0xFFF2EEE4), topLeft = Offset(x0 + k * sw / 6f, awn - 4f * u), size = androidx.compose.ui.geometry.Size(sw / 6f, 4f * u))
                drawRect(wood, topLeft = Offset(x0, counter), size = androidx.compose.ui.geometry.Size(sw, 2.4f * u))
                // honey hearts on one, whistles on the other
                for (k in 0 until 5) {
                    val gx = x0 + 2.5f * u + k * 3.6f * u
                    if (i == 0) drawCircle(Color(0xFFB02A2A), radius = 1.2f * u, center = Offset(gx, counter - 1.2f * u))
                    else drawLine(listOf(Color(0xFF3EA05A), Color(0xFFFFB300), Color(0xFF0B4EA2))[k % 3], Offset(gx - 1f * u, counter - 0.6f * u), Offset(gx + 1.2f * u, counter - 2.2f * u), strokeWidth = 0.9f * u)
                }
            }
            si.lanisce.lani.game.Decor.BANNERS -> for ((i, x) in listOf(4f * u, w - 4f * u).withIndex()) {
                drawLine(woodD, Offset(x, h), Offset(x, h * 0.08f), strokeWidth = 0.9f * u)
                val wave = if (still) 0f else kotlin.math.sin(t * 2.2f + i).toFloat() * 1.2f * u
                val dir = if (i == 0) 1f else -1f
                for ((k, col) in listOf(Color(0xFFF7F9FD), Color(0xFF0B4EA2), Color(0xFFE4002B)).withIndex()) {
                    val y0 = h * (0.1f + k * 0.12f)
                    val band = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x, y0); lineTo(x + dir * 7f * u + wave, y0 + 1f * u); lineTo(x + dir * 7f * u + wave * 1.2f, y0 + h * 0.12f + 1f * u); lineTo(x, y0 + h * 0.12f); close()
                    }
                    drawPath(band, col)
                }
            }
            si.lanisce.lani.game.Decor.BUNTING -> {}
        }
    }
}

/** How many of a party's people stand in its front row: who speaks first. */
private const val FRONT = 4

/**
 * Garlands of little flags strung across the top of a party's stage, sagging between their ends, the flags swinging a
 * little; lanterns among them at [night]. Still, with the system's animations off.
 */
@Composable
private fun Garlands(night: Boolean, modifier: Modifier) {
    val still = rememberReducedMotion()
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (true) {
            withInfiniteAnimationFrameMillis { }
            t = (System.currentTimeMillis() - start) / 1000f
        }
    }
    val flags = listOf(Color(0xFFE4002B), Color(0xFFF7F9FD), Color(0xFF0B4EA2), Color(0xFFFFB300), Color(0xFF3EA05A))
    Canvas(modifier) {
        val u = size.width / 100f
        for ((row, top) in listOf(4f, 13f).withIndex()) {
            val y0 = top * u * 0.6f
            val sag = 7f * u
            val n = 13
            val string = androidx.compose.ui.graphics.Path()
            for (i in 0..40) {
                val x = size.width * i / 40f
                val y = y0 + sag * kotlin.math.sin(Math.PI * i / 40f).toFloat()
                if (i == 0) string.moveTo(x, y) else string.lineTo(x, y)
            }
            drawPath(string, Color(0xFF3A2A20), style = Stroke(width = 0.5f * u))
            for (i in 1 until n) {
                val f = i / n.toFloat()
                val x = size.width * f
                val y = y0 + sag * kotlin.math.sin(Math.PI * f).toFloat()
                val swing = if (still) 0f else kotlin.math.sin(t * 2.4f + i + row) * 0.8f * u
                val flag = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x - 1.6f * u, y); lineTo(x + 1.6f * u, y); lineTo(x + swing, y + 3.6f * u); close()
                }
                drawPath(flag, flags[(i + row * 2) % flags.size])
                if (night && i % 4 == 2) {
                    drawCircle(Color(0x66FFD77A), radius = 3.2f * u, center = Offset(x, y + 3f * u))
                    drawCircle(Color(0xFFFFD77A), radius = 1.2f * u, center = Offset(x, y + 3f * u))
                }
            }
        }
    }
}

/** The effect a step's line rings a bell with over the village (bridge: OVER_VILLAGE_FX). */
private const val BELL = "bell"

/** How long the bell is shown after it rings (s): its peal ([Sfx.PEAL_S]), then its swing dying down. */
private const val RING_S = 9f

/**
 * A little bell swinging in the top right corner of the step's stage (clear of the portraits in the middle), rung at
 * [rungAt] (the wall clock's ms), its rings going out round it while its peal lasts ([Sfx.PEAL_S]), then swinging on a
 * while, dying down ([RING_S]); still, with the system's animations off.
 */
@Composable
private fun RingingBell(rungAt: Long, modifier: Modifier) {
    val still = rememberReducedMotion()
    var age by remember(rungAt) { mutableFloatStateOf(0f) }
    LaunchedEffect(rungAt) {
        while (true) {
            withInfiniteAnimationFrameMillis { }
            age = (System.currentTimeMillis() - rungAt) / 1000f
            if (age > RING_S) break
        }
    }
    if (age > RING_S) return
    // the rings while it peals, the swing dying down after
    val rings = (1f - ((age - Sfx.PEAL_S.toFloat() * 0.6f) / (Sfx.PEAL_S.toFloat() * 0.4f)).coerceIn(0f, 1f))
    val fade = (1f - ((age - Sfx.PEAL_S.toFloat()) / (RING_S - Sfx.PEAL_S.toFloat())).coerceIn(0f, 1f))
    val gold = Color(0xFFF2CC5A); val goldD = Color(0xFF9C6E1E); val ring = Color(0xFFFFF2B0)
    Canvas(modifier) {
        val u = size.minDimension / 100f
        val top = Offset(size.width - 18f * u, 14f * u)
        // its rings going out, three at a time
        if (!still) for (i in 0 until 3) {
            val ph = ((age * 1.1f + i / 3f) % 1f)
            drawCircle(ring.copy(alpha = 0.7f * (1f - ph) * rings), radius = (8f + ph * 34f) * u, center = top + Offset(0f, 12f * u), style = Stroke(width = 1.6f * u))
        }
        // the bell on its yoke, swinging (a quick swing, dying down after the peal), gone at the end
        val swing = if (still) 0f else kotlin.math.sin(age * 7.5f) * 28f * fade
        val shown = (RING_S - age).coerceIn(0f, 1f) // fading out over its last second
        rotate(swing, pivot = top) {
            drawLine(goldD, top, top + Offset(0f, 4f * u), strokeWidth = 2f * u, alpha = shown)
            val body = androidx.compose.ui.graphics.Path().apply {
                moveTo(top.x - 4f * u, top.y + 5f * u)
                cubicTo(top.x - 4f * u, top.y + 2f * u, top.x + 4f * u, top.y + 2f * u, top.x + 4f * u, top.y + 5f * u)
                lineTo(top.x + 7f * u, top.y + 15f * u)
                lineTo(top.x - 7f * u, top.y + 15f * u)
                close()
            }
            drawPath(body, gold, alpha = shown)
            drawLine(goldD, top + Offset(-7.5f * u, 15f * u), top + Offset(7.5f * u, 15f * u), strokeWidth = 2f * u, alpha = shown)
            drawCircle(goldD, radius = 2f * u, center = top + Offset(0f, 17f * u), alpha = shown)
        }
    }
}
