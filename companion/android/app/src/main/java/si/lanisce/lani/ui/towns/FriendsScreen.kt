package si.lanisce.lani.ui.towns

import si.lanisce.lani.ui.backLabel
import android.Manifest
import android.content.Intent
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
import si.lanisce.lani.data.LinkRequest
import si.lanisce.lani.data.TownChild
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
 * language, age and a picture in emojis; "Povabi · Invite" shows an invitation as a QR code of its web link (first saying
 * when only the towns of this computer can accept it), "Sprejmi · Accept" takes one (scanned, pasted, or opened from a
 * link: then filled in). A child's app invites and accepts too, and its links wait for a parent ("⏳ Čaka na starša ·
 * Waiting for your parent", each to take back); a parent's app shows their children ("👪 Tvoji otroci · Your children"):
 * what waits for their yes, with the other town's village, and their own child's town of the node to link as its parent.
 * "Obišči · Visit" opens a linked town, read-only, in its language (VisitScreen). Each town's card shows the friendship
 * both towns agree on ("Friendship between towns"), what its next level brings, and what can be done now: help against
 * its trouble, a feast to share, a move offered or to offer, each with its button; and what each town shares of its
 * learning with the other, with "📈 Napredek · Progress" (TownShare.kt, "Progress between towns"). A town that waits for
 * its own parent shows that, and nothing else yet.
 */
@Composable
fun FriendsScreen(vm: AppViewModel) {
    val towns = vm.towns
    var accepting by rememberSaveable { mutableStateOf(false) }
    // the step before an invitation that only this computer's towns can accept
    var onlyHere by rememberSaveable { mutableStateOf(false) }
    // an invitation opened from a link: the Accept dialog, filled in
    LaunchedEffect(towns.incoming) { if (towns.incoming != null && vm.hosted.townsAvailable) accepting = true }
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
                    vm.backLabel,
                    vm::back,
                )
            }
            // Lani on this phone without a relay: no town of its own to link yet (the setting is in the connection sheet)
            if (!vm.hosted.townsAvailable) item(key = "relay") { Note("🛰️ ${bi("hosted.townsNeedRelay")}") }
            if (vm.hosted.townsAvailable && FriendsLogic.canLink(list)) item(key = "actions") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = { if (FriendsLogic.onlyThisNode(list?.self)) onlyHere = true else towns.makeInvite() },
                        enabled = !towns.inviting, modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Text("✉️ ${bi("towns.invite")}", textAlign = TextAlign.Center)
                    }
                    FilledTonalButton(onClick = { accepting = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text("🔗 ${bi("towns.accept")}", textAlign = TextAlign.Center)
                    }
                }
            }
            when {
                // a child's links wait for a parent: who says yes
                list?.self?.parentApproves == true -> item(key = "child") { Note(FriendsLogic.parentLine(list.self)) }
                // a child's bridge from before: only a parent on the node links it
                list?.self?.child == true -> item(key = "child") { Note("👨‍👩‍👦 ${bi("towns.parentLinksChild")}") }
            }
            // a parent's children: what waits for their yes, and their own child's town of this node to link
            val kids = FriendsLogic.children(towns.children?.children)
            if (kids.isNotEmpty()) {
                item(key = "kids-title") { SectionTitle("👪 ${bi("towns.yourChildren")}") }
                items(kids, key = { "kid:" + it.id.ifBlank { it.profile.orEmpty() } }) { c -> ChildCard(c, towns.busy, onLink = { towns.linkChild(c) }, onDecide = { r, yes -> towns.decide(c, r, yes) }) }
            }
            // a child's own links that wait for the parent, and how those of this week went
            val waiting = list?.requests.orEmpty()
            if (waiting.isNotEmpty()) {
                item(key = "waiting-title") { SectionTitle("⏳ ${bi("towns.waitingForParent")}") }
                items(waiting, key = { "req:" + it.id }) { r -> WaitingRow(r, busy = r.id in towns.busy, onTakeBack = { towns.withdraw(r) }) }
            }
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
                if (row.pending) {
                    // a child's town that waits for its parent: that, and nothing else yet
                    FriendCard(row, null, emptyList(), busy = false, onVisit = {}, onAction = {})
                } else {
                    // the friendship with this town (data/Friendship.kt): its level, what it brings next, what can be done now
                    val f = towns.friendships?.towns?.firstOrNull { it.id == row.id }
                    val state = vm.game.state
                    val child = list?.self?.child == true
                    val notices = remember(f, state, child) {
                        f?.let { FriendsLogic.notices(it, state, state?.let { s -> Chest.feastOption(s, LocalDate.now()) }, child) }.orEmpty()
                    }
                    val town = list?.towns?.firstOrNull { it.id == row.id }
                    FriendCard(row, f, notices, busy = towns.sending, onVisit = { vm.visitTown(row.id) }, onAction = { a -> f?.let { act(vm, it, a) } }) {
                        // what each shares of its learning with the other, and "📈 Napredek · Progress" (TownShare.kt)
                        TownShareRow(vm, row.id, town?.learner.orEmpty(), town?.sharing)
                    }
                }
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
    if (onlyHere) OnlyHereDialog(onInvite = { onlyHere = false; towns.makeInvite() }, onDismiss = { onlyHere = false })
    towns.invite?.let { InviteDialog(it, onDismiss = towns::closeInvite) }
    if (accepting) AcceptDialog(vm, child = list?.self?.parentApproves == true, onDismiss = { accepting = false; towns.clearIncoming() })
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

/**
 * A child of this learner's: their town; "🔗 Poveži kot starš · Link as their parent" for their town on this node; what
 * waits for this learner's yes, each with the other town's village and "✓ Dovoli · Allow" / "Ne dovoli · Don't allow".
 */
@Composable
private fun ChildCard(c: TownChild, busy: Set<String>, onLink: () -> Unit, onDecide: (LinkRequest, Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = MaterialTheme.shapes.large, elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(FriendsLogic.childTitle(c), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (c.status != "ok") Text("📡 ${bi("towns.cantBeReached")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (FriendsLogic.canLinkAsParent(c)) {
                Text(bi("towns.linkAsParentAbout", "child" to c.learner.ifBlank { c.name }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = onLink, enabled = c.id !in busy, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                    Text("🔗 ${bi("towns.linkAsParent")}", textAlign = TextAlign.Center)
                }
            }
            val open = c.requests.filter { it.open }
            if (c.guardian && open.isEmpty() && c.status == "ok") Text("✓ ${bi("towns.nothingWaits")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (r in open) Surface(color = XpGold.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🤝 ${bi("towns.wantsFriend", "child" to c.learner.ifBlank { c.name }, "town" to r.town.name)}", style = MaterialTheme.typography.bodyMedium)
                    Text(FriendsLogic.requestTitle(r), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    // who it is: their village as it looks now (a town that doesn't answer: that)
                    Text(FriendsLogic.previewLine(r.preview) ?: "📡 ${bi("towns.cantBeReached")}", style = MaterialTheme.typography.titleMedium)
                    Text(FriendsLogic.viaLine(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onDecide(r, false) }, enabled = r.id !in busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(bi("towns.dontAllow")) }
                        FilledTonalButton(onClick = { onDecide(r, true) }, enabled = r.id !in busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("✓ ${bi("towns.allow")}") }
                    }
                }
            }
        }
    }
}

/** A child's link that waits for the parent (to take back), or how the parent decided this week. */
@Composable
private fun WaitingRow(r: LinkRequest, busy: Boolean, onTakeBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(FriendsLogic.requestTitle(r), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(FriendsLogic.requestLine(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (r.open) TextButton(onClick = onTakeBack, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("↩️ ${bi("towns.takeBack")}") }
        }
    }
}

/** Before an invitation only this computer's towns can accept (no tailnet address, no relay): it says so first. */
@Composable
private fun OnlyHereDialog(onInvite: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🖥️ ${bi("towns.onlyThisComputer")}", style = MaterialTheme.typography.titleLarge)
                Text(bi("towns.onlyThisComputerWhy"), style = MaterialTheme.typography.bodyMedium)
                BigButton("✉️ ${bi("towns.showCodeAnyway")}", onClick = onInvite)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(bi("common.close")) }
            }
        }
    }
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
private fun FriendCard(r: FriendRow, f: FriendTown?, notices: List<FriendNotice>, busy: Boolean, onVisit: () -> Unit, onAction: (FriendAction) -> Unit, progress: @Composable () -> Unit = {}) {
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
            // waiting for a child's parent is no fault: said quietly
            r.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (r.pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) }
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
            progress()
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
                // who can accept it: only this computer's towns (an older bridge didn't ask first), this tailnet's
                when (invite.reachNow) {
                    Towns.REACH_NODE -> Note("🖥️ ${bi("towns.onlyThisComputer")}")
                    Towns.REACH_TAILNET -> Text("📡 ${bi("towns.onlyTailnet")}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                if (invite.parentApproves) Note("👪 ${bi("towns.parentApproves")}")
                // the web link: the app's own scanner reads it, and a phone's camera opens the app with it (an App Link)
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                    QrImage(invite.shareLink, 240.dp, Modifier.semantics { contentDescription = bi("towns.invitation") })
                }
                Text(Towns.groupCode(invite.code), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                minutes?.let { Text("⏳ ${bi("towns.worksOnceFor", "minutes" to it)}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                BigButton("📤 ${bi("towns.shareInvitation")}", onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        // a line of what it is, and the web link alone on its line (a messenger makes it tappable)
                        putExtra(Intent.EXTRA_TEXT, FriendsLogic.shareText(invite))
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

/**
 * Accepting an invitation: paste its text, scan its QR code with the camera (asked for only then), or one opened from a
 * link, filled in (accepted only by the learner's tap). A [child]'s says that a parent says yes first.
 */
@Composable
private fun AcceptDialog(vm: AppViewModel, child: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf(vm.towns.incoming.orEmpty()) }
    // another link opened while it shows
    LaunchedEffect(vm.towns.incoming) { vm.towns.incoming?.let { text = it } }
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
                Text(if (vm.towns.incoming != null && text == vm.towns.incoming) "✉️ ${bi("towns.invitationFilledIn")}" else bi("towns.scanOrPaste"), style = MaterialTheme.typography.bodyMedium)
                if (child) Note("👪 ${bi("towns.parentApproves")}")
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
                    placeholder = { Text("https://lani.page/town/#…") },
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
