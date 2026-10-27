package si.lanisce.lani.ui.school

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Course
import si.lanisce.lani.data.Prints
import si.lanisce.lani.data.UnitRef
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.CourseGate
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.School
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.backLabel
import si.lanisce.lani.ui.game.NextAgeRow
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.XpGold

// "🏫 Šola · School" (companion/GAME.md, "The school"): the class from the hamlet on, the course's levels and units a bit
// at a time (School.reveal: the rest folded), the unit suggested next (★), what opens the next level; and the school on the
// linden's and the school's cards.

/** The school's name now, by where the class meets: "Pouk ob ognju", "Pouk pod lipo", "Šola"; "Šola" before it begins. */
internal fun schoolTitle(s: GameState?): String = when {
    s == null || !School.open(s) -> bi("school.title")
    else -> when (School.place(s)) {
        School.Place.FIRE -> bi("school.byFire")
        School.Place.LINDEN -> bi("school.classroom")
        School.Place.SCHOOL -> bi("school.title")
    }
}

/** The teacher says [key] (in the language learned, with its meaning and 🔊), their portrait beside it. */
@Composable
internal fun TeacherSays(vm: AppViewModel, teacher: Villager?, key: String, vararg args: Pair<String, Any?>) {
    val said = inTarget(key, *args)
    val meant = inBase(key, *args)
    val talking by rememberTalking(vm)
    Row(verticalAlignment = Alignment.Top) {
        if (teacher != null) TeacherPortrait(teacher, if (talking) si.lanisce.lani.game.scene.Pose.TALK else si.lanisce.lani.game.scene.Pose.IDLE, 56.dp)
        else Text("👩‍🏫", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.width(10.dp))
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                teacher?.let { Text(it.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(said, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    SpeakButton(vm.speaker, said, voiceName = teacher?.speakerVoice ?: si.lanisce.lani.data.Clips.FEMALE, fallback = teacher?.voice, person = teacher?.id)
                }
                if (meant != said) Text(meant, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Who teaches and where: under the linden, the teacher coming over from the next village; indoors, the school's. */
@Composable
internal fun WhoTeaches(vm: AppViewModel, s: GameState) {
    val t = vm.school.teacher()
    val name = t?.name ?: inTarget("school.teacher")
    if (vm.school.teacherLives(s) && School.indoors(s)) Text("👩‍🏫 ${bi("school.teacherHere", "teacher" to name)}", style = MaterialTheme.typography.bodyMedium)
    else Text("👩‍🏫 ${bi("school.teacherComes", "teacher" to name)}", style = MaterialTheme.typography.bodyMedium)
}

/** "🏫 Šola · School": the course of the village's language, level by level. Back: where it was opened (a scene, Napredek, Home). */
@Composable
fun SchoolScreen(vm: AppViewModel) {
    val s = vm.game.state
    val course = vm.school.course
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            GradientHeader(
                "🏫 ${schoolTitle(s)}",
                if (s == null || School.open(s)) bi("school.about") else beginsIn(),
                // where it was opened: the class's scene ("‹ V šoli · At school"), Napredek, the village, Home
                vm.backLabel,
                onBack = vm::back,
            )
        }
        if (s == null || course == null) {
            item { Text(bi("school.noCourse"), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge) }
            return@LazyColumn
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!School.open(s)) BeginsCard(vm, s)
                else {
                    TeacherSays(
                        vm, vm.school.teacher(),
                        when (School.place(s)) {
                            School.Place.FIRE -> "school.welcomeFire"
                            School.Place.LINDEN -> "school.welcome"
                            School.Place.SCHOOL -> "school.welcomeIndoors"
                        },
                    )
                    WhoTeaches(vm, s)
                    School.next(s, course)?.let { NextCard(vm, course, it) }
                }
            }
        }
        // a level a bit at a time: the open levels and the first closed one, each with its units in view and the rest folded
        for (view in School.reveal(s, course)) {
            item("level-${view.state.level}") { LevelSection(vm, s, course, view) }
        }
        item { ReadSetting(vm, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        if (course.machineWritten) item {
            Text("🤖 ${bi("school.machineWritten")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
    }
}

/**
 * "🔊 Beri razlago · Read the explanations" ([si.lanisce.lani.data.IntroVoice.auto]): each step of a unit's introduction read
 * aloud as it appears (on by default); off, nothing plays by itself, a step's 🔊 still reads it. Here and in the app's
 * sound settings.
 */
@Composable
fun ReadSetting(vm: AppViewModel, modifier: Modifier = Modifier) {
    val voice = vm.introVoice
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = voice.auto, role = Role.Switch, onValueChange = voice::readAuto).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔊 ${bi("school.readSetting")}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = voice.auto, onCheckedChange = null)
        }
        Text(bi("school.readSettingHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Pouk se začne z dobo Zaselek · Class begins with the Zaselek age": what begins the class. */
internal fun beginsIn(): String = bi("school.beginsInHamlet", "age" to Age.ZASELEK.names)

/**
 * Before the class begins: it begins with the hamlet (Zaselek, right after the camp), and the village's way there, one tap
 * to its ages' sheet (what the next age still needs).
 */
@Composable
internal fun BeginsCard(vm: AppViewModel, s: GameState) {
    val check = GameEngine.advanceCheck(s, vm.content.wordsKnown, vm.content.targetLevel, vm.content.wordsRusty)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${Age.ZASELEK.emoji}🏫 ${beginsIn()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(bi("school.beginsAbout", "teacher" to (vm.school.teacher()?.name ?: inTarget("school.teacher"))), style = MaterialTheme.typography.bodyMedium)
            // never a wall: the way to the hamlet, one tap away
            NextAgeRow(check) { vm.openVillageAges() }
        }
    }
}

/** The unit suggested next (★), one tap into it. */
@Composable
private fun NextCard(vm: AppViewModel, course: Course, ref: UnitRef) {
    val unit = course.units[ref.id] ?: return
    Card(onClick = { vm.openUnit(ref.id) }, colors = CardDefaults.cardColors(containerColor = XpGold.copy(alpha = 0.22f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(unit.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("★ ${bi("school.next")} · ${unit.level}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(unit.titleShown(L10n.pair), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/**
 * A course level: its progress, what opens it (closed), its units as [School.reveal] shows them (✓ done, ★ next, ▶️ begun,
 * open; a level done behind one line, "✅ 18 opravljenih enot", that shows them), the rest folded into one quiet line
 * ("📝 Še 9 enot v A2") that lists them, and its booklet.
 */
@Composable
private fun LevelSection(vm: AppViewModel, s: GameState, course: Course, view: School.LevelView) {
    val level = view.state
    val next = School.next(s, course)
    var doneShown by rememberSaveable(level.level) { mutableStateOf(false) }
    var listing by rememberSaveable(level.level) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (level.open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Text(level.level, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(bi("school.level", "level" to level.level), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    if (level.of == 0) bi("school.noUnitsYet") else bi("school.unitsDone", "done" to level.done, "of" to level.of) +
                        if (level.planned > 0) " · ${bi("school.morePlanned", "n" to level.planned)}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!level.open) Text("🔒", style = MaterialTheme.typography.titleMedium)
        }
        if (level.of > 0) LinearProgressIndicator(
            progress = { level.done.toFloat() / level.of }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = XpGold,
        )
        if (!level.open) GateCard(vm, s, level)
        if (view.complete) QuietLine(
            "✅ ${bi("school.unitsAllDone", "n" to view.rows.size)}", if (doneShown) "⌃" else "⌄",
            action = if (doneShown) bi("school.hideUnits") else bi("school.showUnits"),
            state = if (doneShown) bi("school.unitsShown") else bi("school.unitsHidden"),
        ) { doneShown = !doneShown }
        if (!view.complete || doneShown) for (ref in view.rows) UnitRow(vm, s, course, ref, School.status(s, course, ref, next))
        if (view.folded.isNotEmpty()) QuietLine(foldedLine(view), "›", action = bi("school.listUnits")) { listing = true }
        // the level's sheets on paper, a booklet (companion/COURSE.md, "Printed sheets"); a row's 🖨️ says here what went wrong
        PrintButton(vm, Prints.booklet(course, level.level), bi("school.printAll"), troubleOf = { it.level == level.level })
    }
    if (listing) FoldedSheet(vm, course, view) { listing = false }
}

/** "📝 Še 9 enot v A2 · 9 more units in A2" (rows above it), "📝 14 enot v A2 · 14 units in A2" (none): a level's folded units. */
internal fun foldedLine(view: School.LevelView): String =
    "📝 ${bi(if (view.rows.isEmpty()) "school.foldedAll" else "school.foldedMore", "n" to view.folded.size, "level" to view.state.level)}"

/**
 * A quiet line under a level's units: [text], a [mark] at its end; a tap does [onClick]. TalkBack reads it as a button
 * with its count, [action] what the tap does, [state] (shown or hidden) when it folds something in place.
 */
@Composable
internal fun QuietLine(text: String, mark: String, action: String, state: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onClick)
            .semantics { if (state != null) stateDescription = state }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(mark, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics {})
    }
}

/**
 * A level's folded units ([School.LevelView.folded]), listed read-only: a written one with what it waits for (later, or
 * locked with its level) and its 🖨️, a planned one "📝 Kmalu · Coming".
 */
@Composable
private fun FoldedSheet(vm: AppViewModel, course: Course, view: School.LevelView, onDismiss: () -> Unit) {
    LaniSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(foldedLine(view), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Text(
                        if (view.state.open) bi("school.foldedNote") else bi("school.foldedClosedNote", "level" to view.state.level),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(view.folded, key = { it.id }) { ref -> FoldedRow(vm, course, view.state.open, ref) }
            item { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(bi("common.close")) } }
        }
    }
}

/** A folded unit: its picture, title and where it stands; nothing to tap but its 🖨️. */
@Composable
private fun FoldedRow(vm: AppViewModel, course: Course, open: Boolean, ref: UnitRef) {
    val unit = course.units[ref.id]
    val line = when {
        unit == null || ref.planned -> "📝 ${bi("school.planned")}"
        !open -> "🔒 ${bi("school.locked")}"
        else -> "○ ${bi("school.later")}"
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(if (unit == null) 0.6f else 1f), verticalAlignment = Alignment.CenterVertically) {
        Text(unit?.emoji ?: "📘", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(ref.titleShown(L10n.pair), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (unit != null) Prints.sheet(course, ref.id)?.let { PrintIcon(vm, it) }
    }
}

/** What a closed level waits for, each with its way on: the classroom, the school (the village), its level, the units to go. */
@Composable
private fun GateCard(vm: AppViewModel, s: GameState, level: School.LevelState) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(bi("school.levelGate", "level" to level.level), style = MaterialTheme.typography.labelLarge)
            for (n in level.needs) {
                val (text, action) = when (n) {
                    School.Need.Hamlet -> "${Age.ZASELEK.emoji} ${bi("school.needHamlet", "age" to Age.ZASELEK.names)}" to { vm.openVillageAges() }
                    School.Need.School -> "🏫 ${bi("school.needSchool")}" to { vm.village() }
                    is School.Need.SchoolLevel -> "⬆️ ${bi("school.needSchoolLevel", "level" to n.level)}" to {
                        School.schoolBuilding(s)?.let { vm.openBuildingCard(it.id) } ?: vm.village()
                    }
                    is School.Need.Units -> "🏫 ${bi("school.unitsToGo", "n" to n.left, "level" to n.level)}" to null
                }
                if (action == null) Text(text, style = MaterialTheme.typography.bodyMedium)
                else TextButton(onClick = action, modifier = Modifier.heightIn(min = 48.dp)) { Text("$text ›") }
            }
        }
    }
}

/** A unit's row: its picture, title and where it stands; a tap opens it (not a planned or locked one). */
@Composable
private fun UnitRow(vm: AppViewModel, s: GameState, course: Course, ref: UnitRef, status: School.Status) {
    val unit = course.units[ref.id]
    val p = School.progress(s, course.language, ref.id)
    val (mark, line) = when (status) {
        School.Status.DONE -> "✅" to (if (p?.testedOut == true) bi("school.testedOut") else bi("school.done"))
        School.Status.NEXT -> "★" to bi("school.next")
        School.Status.OPEN -> (if (p != null) "▶️" else "○") to (if (p != null) bi("school.started") else bi("school.open"))
        School.Status.LOCKED -> "🔒" to bi("school.locked")
        School.Status.PLANNED -> "📝" to bi("school.planned")
    }
    val enabled = status != School.Status.PLANNED && status != School.Status.LOCKED && unit != null
    Card(
        onClick = { vm.openUnit(ref.id) }, enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = if (status == School.Status.NEXT) XpGold.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().alpha(if (status == School.Status.PLANNED) 0.55f else 1f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(unit?.emoji ?: "📘", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ref.titleShown(L10n.pair), style = MaterialTheme.typography.titleSmall, fontWeight = if (status == School.Status.NEXT) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // its sheet on paper, a written unit's (a locked one's too: to learn from on the way)
            if (unit != null) Prints.sheet(course, ref.id)?.let { PrintIcon(vm, it) }
            Text(mark, style = MaterialTheme.typography.titleMedium, color = if (status == School.Status.DONE) AlpineGreen else Color.Unspecified)
        }
    }
}

/**
 * The school on a building's card (ui/game/BuildingCard): on the linden's while the class meets under it, "🏫 Pouk pod
 * lipo"; on the school's, "🏫 Šola · School". [onOpen] as the school opens (the card closes).
 */
@Composable
fun SchoolOnCard(vm: AppViewModel, s: GameState, b: Building, onOpen: () -> Unit) {
    if (vm.school.course == null) return
    val goes = (b.type == BuildingType.LIPA && School.open(s) && School.place(s) == School.Place.LINDEN) || b.type == BuildingType.SCHOOL
    if (goes) BigButton("🏫 ${if (b.type == BuildingType.SCHOOL) bi("school.title") else bi("school.classroom")}", color = AlpineGreen, onClick = {
        onOpen()
        vm.openSchool()
    })
}

/** What the school's next level asks of the course: "🏫 Za stopnjo 2: tečaj A2 · 3 enote še" or done. */
@Composable
fun CourseGateCard(g: CourseGate) {
    Surface(color = if (g.done) AlpineGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${if (g.done) "✅" else "🏫"} ${bi("school.gateTitle", "toLevel" to g.toLevel, "level" to g.level)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (!g.done) Text(bi("school.unitsToGo", "n" to g.left, "level" to g.level), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** "Danes · Today"'s idea from the school: the unit suggested next, once the class meets; null before, or all done. */
fun lessonIdea(vm: AppViewModel): si.lanisce.lani.ui.home.HomeAction.Lesson? {
    val course = vm.school.course ?: return null
    val s = vm.game.state?.takeIf(School::open) ?: return null
    val ref = School.next(s, course) ?: return null
    val unit = course.units[ref.id] ?: return null
    return si.lanisce.lani.ui.home.HomeAction.Lesson(ref.id, ref.titleShown(L10n.pair), unit.emoji, unit.level, School.progress(s, course.language, ref.id) != null)
}

/**
 * Home's "🏫 Šola · School" tile: the unit suggested next, else all done; before the class begins, that it begins with
 * the hamlet. Null without a course.
 */
fun schoolTile(vm: AppViewModel): si.lanisce.lani.ui.home.TileStatus? {
    val course = vm.school.course ?: return null
    val s = vm.game.state ?: return si.lanisce.lani.ui.home.TileStatus(bi("school.about"))
    if (!School.open(s)) return si.lanisce.lani.ui.home.TileStatus("🔒 ${beginsIn()}")
    val next = School.next(s, course) ?: return si.lanisce.lani.ui.home.TileStatus("✅ ${bi("school.allDone")}")
    return si.lanisce.lani.ui.home.TileStatus("★ ${next.titleShown(L10n.pair)}", attention = School.progress(s, course.language, next.id) == null)
}
