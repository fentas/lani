package si.lanisce.lani.ui.towns

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.time.LocalDate
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.FriendTown
import si.lanisce.lani.data.GuestEntry
import si.lanisce.lani.data.InviteLink
import si.lanisce.lani.data.InviteParse
import si.lanisce.lani.data.InviteProblem
import si.lanisce.lani.data.TownInvite
import si.lanisce.lani.data.Towns
import si.lanisce.lani.game.Chest
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.QrCamera
import si.lanisce.lani.ui.StatusBarScrim
import si.lanisce.lani.ui.cameraGranted
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.XpGold

/**
 * "Prijatelji · Friends": the towns linked with this learner's (companion/README.md, "Towns"), each with its learner,
 * language, age and a picture in emojis; "Povabi · Invite" shows an invitation as a QR code, "Sprejmi · Accept" takes
 * one (scanned or pasted). A child's app shows the towns only: a parent links them on the node. "Obišči · Visit" opens
 * a linked town, read-only, in its language (VisitScreen). Each town's card shows the friendship both towns agree on
 * ("Friendship between towns"), what its next level brings, and what can be done now: help against its trouble, a feast
 * to share, a move offered or to offer, each with its button.
 */
@Composable
fun FriendsScreen(vm: AppViewModel) {
    val towns = vm.towns
    var accepting by rememberSaveable { mutableStateOf(false) }
    BackHandler { vm.openVillage() }
    LaunchedEffect(Unit) {
        towns.load()
        vm.questions.load()
    }
    val list = towns.list
    val rows = remember(list) { list?.let { FriendsLogic.rows(it) }.orEmpty() }
    val lazy = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), state = lazy, contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                GradientHeader(
                    "🤝 ${bi("towns.friends")}",
                    list?.self?.let { "${bi("towns.yourTown")}: ${FriendsLogic.selfLine(it)}" } ?: bi("towns.friendsAbout"),
                    "‹ ${bi("common.village")}",
                    vm::openVillage,
                )
            }
            if (FriendsLogic.canLink(list)) item(key = "actions") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = towns::makeInvite, enabled = !towns.inviting, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text("✉️ ${bi("towns.invite")}", textAlign = TextAlign.Center)
                    }
                    FilledTonalButton(onClick = { accepting = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text("🔗 ${bi("towns.accept")}", textAlign = TextAlign.Center)
                    }
                }
            }
            if (list?.self?.child == true) item(key = "child") { Note("👨‍👩‍👦 ${bi("towns.parentLinksChild")}") }
            when {
                list == null && towns.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                list == null -> item(key = "problem") {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(towns.problem ?: "📡 ${bi("towns.cantReachTutor")}", style = MaterialTheme.typography.bodyLarge)
                        BigButton("↻ ${bi("common.retry")}", onClick = { towns.load() })
                    }
                }
                rows.isEmpty() -> item(key = "none") {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🏡 ${bi("towns.noFriendsYet")}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (list.self.child) bi("towns.noFriendsChild") else bi("towns.noFriendsInvite"),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> item(key = "title") {
                    Text(
                        "🗺️ ${bi("towns.linkedTowns")}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
            }
            items(rows, key = { it.id }) { row ->
                // the friendship with this town (data/Friendship.kt): its level, what it brings next, what can be done now
                val f = towns.friendships?.towns?.firstOrNull { it.id == row.id }
                val state = vm.game.state
                val child = list?.self?.child == true
                val notices = remember(f, state, child) {
                    f?.let { FriendsLogic.notices(it, state, state?.let { s -> Chest.feastOption(s, LocalDate.now()) }, child) }.orEmpty()
                }
                FriendCard(row, f, notices, busy = towns.sending, onVisit = { vm.visitTown(row.id) }, onAction = { a -> f?.let { act(vm, it, a) } })
            }
            if (list != null && towns.problem != null) item(key = "stale") { Note(towns.problem!!) }
            // what guests brought: help, gifts and their messages (plain text, as written), trades
            if (towns.guests.isNotEmpty()) item(key = "guests") {
                Text(
                    "📬 ${bi("guests.fromGuests")}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                )
            }
            items(towns.guests.take(30), key = { "g:" + it.key }) { GuestRow(it) }
            // questions between towns: guests' to answer, the learner's and their answers (TownQuestionScreens.kt)
            questionItems(vm)
        }
        StatusBarScrim(lazy, Modifier.align(Alignment.TopCenter))
    }
    towns.invite?.let { InviteDialog(it, onDismiss = towns::closeInvite) }
    if (accepting) AcceptDialog(vm, onDismiss = { accepting = false })
}

/** A guest's visit: who and what they brought; a message as they wrote it, plain text in quotes. */
@Composable
private fun GuestRow(e: GuestEntry) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(VisitLogic.guestLine(e), style = MaterialTheme.typography.bodyMedium)
            e.gift?.message?.takeIf { it.isNotBlank() }?.let { Text("„$it“", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
            Text(e.from.name + e.at.take(10).takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Note(text: String) {
    Surface(
        color = XpGold.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
}

/** What a notice's button does: help sent, a feast held, a move offered, said yes to or declined. */
private fun act(vm: AppViewModel, f: FriendTown, a: FriendAction) {
    when (a) {
        is FriendAction.Aid -> vm.sendAid(f)
        FriendAction.Feast -> vm.holdFeast()
        is FriendAction.Offer -> vm.offerMove(f, a.dir)
        is FriendAction.Answer -> f.moves.list.firstOrNull { it.id == a.move }?.let { vm.answerMove(f, it, a.yes) }
    }
}

/** A friendship's notice: its line and what can be done about it (a button, and "not now" where it asks). */
@Composable
private fun FriendNoticeBox(n: FriendNotice, busy: Boolean, onAction: (FriendAction) -> Unit) {
    Surface(color = XpGold.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(n.text, style = MaterialTheme.typography.bodyMedium)
            if (n.action != null || n.second != null) {
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    n.second?.let { s -> TextButton(onClick = { onAction(s) }, enabled = !busy, modifier = Modifier.heightIn(min = 44.dp)) { Text(n.secondLabel) } }
                    n.action?.let { a ->
                        FilledTonalButton(onClick = { onAction(a) }, enabled = n.enabled && !busy, modifier = Modifier.heightIn(min = 44.dp)) {
                            Text(n.label, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendCard(r: FriendRow, f: FriendTown?, notices: List<FriendNotice>, busy: Boolean, onVisit: () -> Unit, onAction: (FriendAction) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(AlpineGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Text("🏘️", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { }) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(r.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (r.thumbnail.isNotBlank()) Text(r.thumbnail, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = r.subtitle })
            r.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            // the friendship both towns agree on, what the next level brings, and what can be done now
            f?.let {
                Spacer(Modifier.height(4.dp))
                Text(FriendsLogic.levelLine(it), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { FriendsLogic.progress(it) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    color = XpGold,
                    strokeCap = StrokeCap.Round,
                )
                Text(FriendsLogic.nextLine(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (n in notices) FriendNoticeBox(n, busy, onAction)
            // a town that isn't answering can still be visited: its bridge keeps what it had of it
            if (r.canVisit) FilledTonalButton(onClick = onVisit, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text("🚶 ${bi("towns.visit")}")
            }
        }
    }
}

/** The invitation: its QR code, its code to read out, how long it works, and sharing its link. */
@Composable
private fun InviteDialog(invite: TownInvite, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val minutes = remember(invite) { FriendsLogic.minutesLeft(invite.expiresAt) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("✉️ ${bi("towns.invitation")}", style = MaterialTheme.typography.titleLarge)
                Text(bi("towns.showThisCode"), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                    QrImage(invite.link, 240.dp, Modifier.semantics { contentDescription = bi("towns.invitation") })
                }
                Text(Towns.groupCode(invite.code), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                minutes?.let { Text("⏳ ${bi("towns.worksOnceFor", "minutes" to it)}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                BigButton("📤 ${bi("towns.shareInvitation")}", onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, invite.link)
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, null)) }
                })
                TextButton(onClick = onDismiss) { Text(bi("common.close")) }
            }
        }
    }
}

/** [text] as a QR code, [size] wide, dark on the light background around it. */
@Composable
private fun QrImage(text: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        }.getOrNull()
    } ?: return
    Canvas(modifier.size(size).aspectRatio(1f)) {
        val cell = this.size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** Accepting an invitation: paste its text, or scan its QR code with the camera (asked for only then). */
@Composable
private fun AcceptDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        scanning = ok
        if (!ok) message = "📷 ${bi("pairScan.needsCamera")}."
    }
    fun accept(t: String) {
        message = null
        vm.towns.accept(t) { error -> if (error == null) onDismiss() else message = error }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("🔗 ${bi("towns.acceptInvitation")}", style = MaterialTheme.typography.titleLarge)
                Text(bi("towns.scanOrPaste"), style = MaterialTheme.typography.bodyMedium)
                if (scanning) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
                        QrCamera(onCode = { code ->
                            // someone else's QR code: keep looking
                            val parsed = InviteLink.parse(code)
                            if (parsed is InviteParse.Invalid && parsed.problem == InviteProblem.NOT_INVITE) {
                                message = parsed.problem.message
                            } else if (scanning) {
                                scanning = false
                                text = code
                                accept(code)
                            }
                        }, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    OutlinedButton(onClick = {
                        message = null
                        if (cameraGranted(context)) scanning = true else permission.launch(Manifest.permission.CAMERA)
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("📷 ${bi("common.scanQrCode")}") }
                }
                OutlinedTextField(
                    value = text, onValueChange = { text = it; message = null },
                    label = { Text(bi("towns.invitation")) },
                    placeholder = { Text("lani://town?…") },
                    minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (vm.towns.accepting) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text(bi("towns.askingTown"))
                    }
                }
                BigButton("🤝 ${bi("towns.accept")}", onClick = { accept(text) }, enabled = text.isNotBlank() && !vm.towns.accepting)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(bi("common.close")) }
            }
        }
    }
}
