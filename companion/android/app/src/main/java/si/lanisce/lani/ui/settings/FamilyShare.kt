package si.lanisce.lani.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.FamilyComputer
import si.lanisce.lani.app.FamilyShareController.Phase
import si.lanisce.lani.data.FamilyMember
import si.lanisce.lani.data.FamilyMembersApi
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.setup.HoldToOpen
import si.lanisce.lani.ui.towns.QrImage
import si.lanisce.lani.ui.tutor.CAPS
import java.text.DateFormat
import java.time.Instant
import java.util.Date

/**
 * "👪 Družina · Family" in the settings (docs/plans/05-built/leftovers-app.md). With a computer: a new member there and their code,
 * the members a parent looks after and each device's budget. Everywhere: the QR code that shares the tutor device to device, and the
 * devices it went to, each with a budget and a remove where a channel reaches it, and saying so plainly where one doesn't. Every step
 * that changes something is a parent's hold. Leaving the page ends a code. Not on a child's device.
 */
@Composable
fun FamilyShare(vm: AppViewModel) {
    val onComputer = !vm.hosted.active
    DisposableEffect(Unit) {
        vm.familyDevices.page(true)
        onDispose { vm.familyDevices.page(false) }
    }
    if (onComputer) {
        LaunchedEffect(Unit) { vm.familyComputer.load() }
        FamilyOnComputer(vm)
    }
    // on a computer this is the second way, and a page of its own would hide the first
    var keyOnly by rememberSaveable { mutableStateOf(false) }
    if (!onComputer || vm.familyComputer.older || keyOnly) FamilyCode(vm)
    else TextButton(onClick = { keyOnly = true }) { Text("📱 ${bi("family.keyOnly")}") }
    FamilyDevicesList(vm)
}

/** The QR code that shares the tutor, what it is before one is shown, and the parent's decision once someone claimed it. */
@Composable
private fun FamilyCode(vm: AppViewModel) {
    val c = vm.familyShare
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var what by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { what = vm.familyWhat() }
    DisposableEffect(Unit) { onDispose { c.close() } }
    // what it is before a code is shown; once someone claimed one, only what the parent decides on
    if (c.phase !is Phase.Asking && c.phase !is Phase.Sent) {
        Text(bi("family.what"), style = MaterialTheme.typography.bodyMedium)
        if (vm.data.relay.isNotBlank()) Text("🛰️ ${bi("family.viaRelay")}", style = MaterialTheme.typography.bodySmall, color = muted)
        Text(bi("family.chatgptNot"), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    when (val p = c.phase) {
        Phase.Idle, Phase.Over, Phase.NoWifi -> {
            if (p == Phase.NoWifi) Text("📶 ${bi("family.noWifi")}", color = MaterialTheme.colorScheme.error)
            if (p == Phase.Over) Text("⌛ ${bi("family.over")}", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = c::show, modifier = Modifier.fillMaxWidth()) { Text("📱 ${bi("family.show")}") }
        }
        is Phase.Showing -> {
            Text(bi("family.lasts"), style = MaterialTheme.typography.bodyMedium)
            // dark on white whatever the theme: a camera reads it
            Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), contentAlignment = Alignment.Center) { QrImage(p.text, 240.dp) }
            OutlinedButton(onClick = c::refuse, modifier = Modifier.fillMaxWidth()) { Text(bi("family.cancel")) }
        }
        is Phase.Asking -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("👪 ${bi("family.confirmTitle", "device" to p.device)}", style = MaterialTheme.typography.titleMedium)
            Text(bi("family.willShare"), style = MaterialTheme.typography.titleSmall)
            for (w in what) Text("• $w", style = MaterialTheme.typography.bodyMedium)
            Text(bi("family.whoPays"), style = MaterialTheme.typography.bodySmall, color = muted)
            HoldToOpen("🔒 ${bi("family.hold")}", onLight = true, onOpen = c::confirm)
            OutlinedButton(onClick = c::refuse, modifier = Modifier.fillMaxWidth()) { Text(bi("family.no")) }
        }
        is Phase.Sent -> {
            Text("✅ ${bi("family.sent", "device" to p.device)}", style = MaterialTheme.typography.titleMedium)
            Text(bi("family.stopping"), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

/** A family's computer: the code of a member just added, else how to add one; then who's in the family. */
@Composable
private fun FamilyOnComputer(vm: AppViewModel) {
    val f = vm.familyComputer
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    f.problem?.let { Text("⚠️ ${bi(problemKey(it))}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    val code = f.code
    when {
        f.older -> Text(bi("family.computerOlder"), style = MaterialTheme.typography.bodySmall, color = muted)
        code != null -> MemberCode(f, code)
        f.family?.canAdd == false -> Text(bi(if (f.family?.whyNot == "off") "family.addOff" else "family.addFull"), style = MaterialTheme.typography.bodyMedium)
        f.family != null -> AddMember(vm, f)
    }
    f.family?.let { family ->
        val rows = family.members.filter { it.devices.isNotEmpty() || !it.self }
        if (rows.isNotEmpty()) {
            Text("🖥️ ${bi("family.onComputer")}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            for (m in rows) MemberCard(f, m)
        }
    }
}

/** A member's name, the two languages and "for a child"; a parent's hold makes them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMember(vm: AppViewModel, f: FamilyComputer) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var name by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf(vm.langPair.target.code) }
    var base by rememberSaveable { mutableStateOf(vm.langPair.base.code) }
    var child by rememberSaveable { mutableStateOf(false) }
    Text(bi("family.addWhat"), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text(bi("family.memberName")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text(bi("connectionSheet.target"), style = MaterialTheme.typography.bodySmall, color = muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (l in Lang.entries) FilterChip(selected = target == l.code, onClick = { if (l.code == base) base = target; target = l.code }, label = { Text(l.ownName) })
    }
    Text(bi("connectionSheet.base"), style = MaterialTheme.typography.bodySmall, color = muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (l in Lang.entries) FilterChip(selected = base == l.code, onClick = { if (l.code == target) target = base; base = l.code }, label = { Text(l.ownName) })
    }
    // the whole row: on a tablet the switch alone is a small target at the far end of the line
    Row(Modifier.fillMaxWidth().toggleable(value = child, role = Role.Switch, onValueChange = { child = it }), verticalAlignment = Alignment.CenterVertically) {
        Text("🧒 ${bi("hosted.forChild")}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = child, onCheckedChange = null)
    }
    if (f.busy) Text("⏳ ${bi("family.adding")}", style = MaterialTheme.typography.bodyMedium)
    else if (name.isNotBlank()) HoldToOpen("🔒 ${bi("family.holdAdd")}", onLight = true) { f.add(name, target, base, child) }
}

/** The code for a member's device, what's left on the computer, and the way back. */
@Composable
private fun MemberCode(f: FamilyComputer, c: FamilyMembersApi.Code) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    c.name?.let { Text("✅ ${bi("family.memberAdded", "name" to it)}", style = MaterialTheme.typography.titleMedium) }
    if (c.townLinked) Text("🤝 ${bi("family.townLinked")}", style = MaterialTheme.typography.bodySmall, color = muted)
    c.pair?.let { text ->
        Text(bi("family.pairHow"), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), contentAlignment = Alignment.Center) { QrImage(text, 240.dp) }
    }
    if (c.left.isNotEmpty()) {
        Text(bi("family.leftOnComputer"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        for (l in c.left) {
            Text(bi(leftKey(l.what)), style = MaterialTheme.typography.bodySmall, color = muted)
            SelectionContainer { Text(l.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        }
    }
    // a code pairs a device onto this learner's account, a child's too: minted by a parent's hold, as the first one was
    HoldToOpen("🔒 ${bi("family.holdNewCode")}", onLight = true) { f.newCode(c.profile) }
    Button(onClick = f::done, modifier = Modifier.fillMaxWidth()) { Text("✓ ${bi("family.done")}") }
}

@Composable
private fun MemberCard(f: FamilyComputer, m: FamilyMembersApi.Member) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((if (m.child) "🧒 " else "🙂 ") + m.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (!m.up) Text("💤 ${bi("family.bridgeDown")}", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            if (m.devices.isEmpty()) Text(bi("family.noDeviceYet"), style = MaterialTheme.typography.bodySmall, color = muted)
            for (d in m.devices) {
                DeviceRow(
                    title = "📱 ${d.name}",
                    status = d.lastSeen?.let { runCatching { bi("family.seen", "when" to shortWhen(Instant.parse(it).toEpochMilli())) }.getOrNull() } ?: bi("family.neverSeen"),
                    cap = d.cap, capPending = false,
                    onCap = { f.budget(m.profile, d.id, it) },
                    removeLabel = bi("family.holdUnpair"), removeNote = bi("family.unpairWhat"), onRemove = { f.unpair(m.profile, d.id) },
                )
            }
            if (!m.self) HoldToOpen("🔒 ${bi("family.holdAnotherDevice")}", onLight = true) { f.newCode(m.profile) }
        }
    }
}

/** The devices this one shared with, what reaches them and what doesn't. */
@Composable
private fun FamilyDevicesList(vm: AppViewModel) {
    val d = vm.familyDevices
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val members = d.book.members
    if (members.isEmpty() && !vm.hosted.active) return
    Text("📱 ${bi("family.devices")}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    if (members.isEmpty()) Text(bi("family.noDevices"), style = MaterialTheme.typography.bodySmall, color = muted)
    for (m in members) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BondRow(vm, m)
        }
    }
    // the plain truth of it, whatever the list says
    Text(bi(if (vm.data.relay.isBlank()) "family.reachWifi" else "family.reachRelay"), style = MaterialTheme.typography.bodySmall, color = muted)
    if (members.isNotEmpty()) Text(bi("family.olderDevices"), style = MaterialTheme.typography.bodySmall, color = muted)
}

@Composable
private fun BondRow(vm: AppViewModel, m: FamilyMember) {
    val d = vm.familyDevices
    val status = when {
        m.leaving -> "⏳ ${bi("family.leaving")}"
        m.pending != null -> "⏳ ${bi("family.waitingThere")}"
        m.heard != null -> bi("family.seen", "when" to shortWhen(m.heard))
        else -> bi("family.neverSeen")
    }
    if (m.leaving) {
        Text("📱 ${m.name}", style = MaterialTheme.typography.titleSmall)
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HoldToOpen("🔒 ${bi("family.holdDrop")}", onLight = true) { d.drop(m.id) }
    } else DeviceRow(
        title = "📱 ${m.name}", status = status, cap = m.cap, capPending = m.pending != null,
        onCap = { d.setCap(m.id, it) },
        removeLabel = bi("family.holdRemove"), removeNote = bi("family.removeWhat"), onRemove = { d.remove(m.id) },
    )
}

/** A device: its name and how it stands, and behind a tap its budget and its removal, each a parent's hold. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceRow(title: String, status: String, cap: Double?, capPending: Boolean, onCap: (Double?) -> Unit, removeLabel: String, removeNote: String, onRemove: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var open by rememberSaveable(title) { mutableStateOf(false) }
    var chosen by remember(cap) { mutableStateOf(cap) }
    TextButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(status, style = MaterialTheme.typography.bodySmall, color = muted)
            Text("💶 " + (cap?.let { bi("family.capIs", "cap" to money(it)) } ?: bi("family.capNone")) + (if (capPending) " · ${bi("family.capOnItsWay")}" else ""), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        Spacer(Modifier.width(8.dp))
        Text(if (open) "▾" else "›", color = muted)
    }
    if (!open) return
    Text(bi("family.capWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = chosen == null, onClick = { chosen = null }, label = { Text(bi("family.capNoneChip")) })
        for (c in CAPS) FilterChip(selected = chosen == c, onClick = { chosen = c }, label = { Text(money(c)) })
    }
    if (chosen != cap) HoldToOpen("🔒 ${bi("family.holdCap")}", onLight = true) { onCap(chosen) }
    Text(removeNote, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 6.dp))
    HoldToOpen("🔒 $removeLabel", onLight = true, onOpen = onRemove)
}

private fun money(d: Double) = "$" + "%.2f".format(d)

private fun shortWhen(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))

/** What's left on the computer, as a string's key. */
private fun leftKey(what: String): String = when (what) {
    "start" -> "family.leftStart"
    "publish" -> "family.leftPublish"
    "url" -> "family.leftUrl"
    else -> "family.leftTutor"
}

/** The bridge's word for why not, as a string's key. */
private fun problemKey(code: String): String = when (code) {
    "full" -> "family.addFull"
    "off" -> "family.addOff"
    "offline" -> "family.computerOffline"
    "this_device" -> "family.thisDevice"
    else -> "family.computerRefused"
}
