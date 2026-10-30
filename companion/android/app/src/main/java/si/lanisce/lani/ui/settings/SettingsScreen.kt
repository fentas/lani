package si.lanisce.lani.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.AmbienceSettings
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.PairDialog
import si.lanisce.lani.ui.backLabel
import si.lanisce.lani.ui.home.OfflineAudioSettings
import si.lanisce.lani.ui.home.linkLabel
import si.lanisce.lani.ui.theme.TriglavRed

/**
 * The sections of "⚙️ Nastavitve · Settings", in their order on its page: what a learner looks for most first (the tutor,
 * the data), the connection, which they set once, last; the developer's only once shown (the version tapped seven times).
 * [id] is the section's in the back stack ([si.lanisce.lani.Screen.Settings]), [key] its name's.
 */
enum class SettingsSection(val id: String, val emoji: String, val key: String) {
    TUTOR("tutor", "🧑‍🏫", "common.tutor"),
    DATA("data", "💾", "dataSync.title"),
    LANGUAGES("languages", "🌐", "connectionSheet.languages"),
    SOUND("sound", "🔊", "settings.sound"),
    TIPS("tips", "💡", "tips.title"),
    CONNECTION("connection", "🔗", "connectionSheet.connection"),
    DEVELOPER("developer", "🛠️", "developer.title"),
    ;

    companion object {
        fun of(id: String?): SettingsSection? = entries.firstOrNull { it.id == id }
    }
}

/**
 * "⚙️ Nastavitve · Settings" (docs/plans/05-built/navigation.md, "Settings"), from the ⚙️ on Home: its sections, each a row
 * with what it holds now in a line, a tap its page ([section]): "🧑‍🏫 Učitelj · Tutor" (who answers, the provider and its
 * key, the spend), "💾 Podatki · Data" (git, the handover, the backup), "🌐 Jeziki · Languages", "🔊 Zvok in glas · Sound
 * and voice" (the learner's own voice, "🗣️ Moj glas · My voice"; the background sounds, the explanations read aloud, a
 * talk's line sent by itself, the offline audio and voice), "🔗 Povezava · Connection"
 * (the node or Lani on this phone, pairing again by QR code). On a child's device the tutor's and the data's pages are a
 * parent's (a hold opens them: ui/setup/ParentGated). The version line at the end, tapped seven times, shows
 * "🛠️ Razvijalec · Developer".
 */
@Composable
fun SettingsScreen(vm: AppViewModel, section: String?) {
    val s = SettingsSection.of(section)
    if (s == null) Sections(vm) else Page(vm, s)
}

@Composable
private fun Sections(vm: AppViewModel) {
    val shown = SettingsSection.entries.filter { it != SettingsSection.DEVELOPER || vm.shadow.settings.developer }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item("header") {
            GradientHeader("⚙️ ${bi("common.settings")}", linkLabel(vm.link, vm.unpaired, vm.hosted.active), vm.backLabel, vm::back)
        }
        item("rows") {
            Column(Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 720.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (it in shown) SectionRow(it, summary(vm, it), locked = vm.child && it.parents) { vm.openSettings(it.id) }
            }
        }
        item("version") { Version(vm, Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp)) }
    }
}

/** The tutor's and the data's are a parent's on a child's device (keys and accounts: app/ParentGate). */
private val SettingsSection.parents: Boolean get() = this == SettingsSection.TUTOR || this == SettingsSection.DATA || this == SettingsSection.TIPS

/** What a section holds now, in a line for its row. */
private fun summary(vm: AppViewModel, s: SettingsSection): String = when (s) {
    SettingsSection.TUTOR -> when {
        vm.hosted.active -> if (vm.tutorReady) "📱 ${bi("tutorLive.app")}" else bi("hosted.tutorOff")
        vm.live.settings.on -> "📱 ${bi("tutorLive.app")}"
        else -> "🖥️ ${bi("tutorLive.node")}"
    }
    SettingsSection.DATA -> si.lanisce.lani.ui.data.dataSummary(vm.data)
    SettingsSection.LANGUAGES -> vm.langPair.let { "${it.target.ownName} · ${it.base.ownName}" }
    SettingsSection.SOUND -> bi("settings.soundAbout")
    SettingsSection.TIPS -> if (vm.game.state?.tips?.off == true) bi("tips.offNow") else bi("tips.onNow")
    SettingsSection.CONNECTION -> linkLabel(vm.link, vm.unpaired, vm.hosted.active)
    SettingsSection.DEVELOPER -> vm.shadow.status
}

/** A section's row: its emoji, its name, what it holds now, "›"; [locked]: a parent's (🛡️). */
@Composable
private fun SectionRow(s: SettingsSection, summary: String, locked: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(bi(s.key) + if (locked) " · 🛡️" else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

/** A section's page: "‹ Nastavitve · Settings", its name, what it holds. */
@Composable
private fun Page(vm: AppViewModel, s: SettingsSection) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        GradientHeader("${s.emoji} ${bi(s.key)}", null, vm.backLabel, vm::back, bottom = 20.dp)
        Column(Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 720.dp).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (s) {
                // keys and accounts (the tutor's provider, git's GitHub, the backup): a parent's on a child's device
                SettingsSection.TUTOR -> si.lanisce.lani.ui.setup.ParentGated(vm.child) { si.lanisce.lani.ui.tutor.TutorSettings(vm, heading = false) }
                SettingsSection.DATA -> si.lanisce.lani.ui.setup.ParentGated(vm.child) {
                    si.lanisce.lani.ui.data.DataContent(vm.data, banner = { vm.notices.banner = it }, title = false)
                }
                SettingsSection.LANGUAGES -> Languages(vm)
                SettingsSection.SOUND -> {
                    // the voice the learner's own lines are said in (the node's choice of voices; none in the hosted mode's yet)
                    MyVoiceSettings(vm.myVoice, Modifier.padding(bottom = 8.dp))
                    AmbienceSettings(vm.ambience)
                    si.lanisce.lani.ui.school.ReadSetting(vm)
                    si.lanisce.lani.ui.talk.BySelfSetting(vm)
                    // Lani on this phone has no node's clips nor its offline voice yet: the phone's text-to-speech speaks
                    if (!vm.hosted.active) OfflineAudioSettings(vm.offlineAudio, Modifier.padding(top = 8.dp))
                }
                SettingsSection.CONNECTION -> Connection(vm)
                SettingsSection.DEVELOPER -> Developer(vm)
                // a child's tips are a parent's to turn off (a hold opens them)
                SettingsSection.TIPS -> si.lanisce.lani.ui.setup.ParentGated(vm.child) { Tips(vm) }
            }
        }
    }
}

/**
 * "🔗 Povezava · Connection": which tutor and learner this phone talks to, and pairing again by QR code (a new code after
 * the phone was unpaired, or instead of a typed token); in the hosted mode, Lani on this phone (the export and import, the
 * relay, the way to a computer); the way to run Lani on the phone alone.
 */
@Composable
private fun Connection(vm: AppViewModel) {
    val p = vm.paired
    val learner = p?.learner?.takeIf { it.isNotBlank() } ?: vm.content.dashboard?.name
    val hosted = vm.hosted.active
    var scanning by rememberSaveable { mutableStateOf(false) }
    Text(linkLabel(vm.link, vm.unpaired, hosted), style = MaterialTheme.typography.bodyLarge, color = if (vm.unpaired) TriglavRed else MaterialTheme.colorScheme.onSurface)
    learner?.let { InfoLine("🧒", bi("connectionSheet.learner"), it + if (p?.child == true) " · 🛡️" else "") }
    if (hosted) {
        si.lanisce.lani.ui.hosted.HostedSection(vm, Modifier.padding(top = 6.dp))
        return
    }
    vm.bridgeUrl?.let { InfoLine("🖥️", bi("common.tutor"), it.removePrefix("https://").removePrefix("http://")) }
    InfoLine("📱", bi("connectionSheet.phone"), if (p != null) "${p.deviceName} (${bi("connectionSheet.pairedByQrCode")})" else bi("connectionSheet.typedToken"))
    Text(
        if (vm.unpaired) bi("connectionSheet.runLaniPairComputer") else bi("connectionSheet.pairPhoneAgainRun"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
    )
    // needed about once: quiet while paired, the big one once the node unpaired this phone
    if (vm.unpaired) si.lanisce.lani.ui.BigButton("📷 ${bi("common.scanQrCode")}", onClick = { scanning = true }, color = TriglavRed)
    else OutlinedButton(onClick = { scanning = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("📷 ${bi("common.scanQrCode")}") }
    si.lanisce.lani.ui.hosted.MoveToPhoneSection(vm, Modifier.padding(top = 6.dp))
    if (scanning) PairDialog(vm, onDismiss = { scanning = false })
}

/**
 * "💡 Namigi · Tips" (companion/GAME.md "Guidance"): the game's tips on or off (synced with the village: another device
 * follows), and "🔁 Pokaži jih spet · Show them again" (what was dealt with comes again, one at a time).
 */
@Composable
private fun Tips(vm: AppViewModel) {
    val off = vm.game.state?.tips?.off == true
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("💡 ${bi("tips.title")}", style = MaterialTheme.typography.bodyLarge)
            Text(bi("tips.about"), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        Switch(checked = !off, onCheckedChange = { on -> vm.game.tipsOff(!on) }, enabled = vm.game.state != null)
    }
    OutlinedButton(
        onClick = { vm.game.tipsAgain(); vm.notices.banner = "💡 ${bi("tips.againDone")}" },
        enabled = vm.game.state != null, modifier = Modifier.heightIn(min = 48.dp),
    ) { Text("🔁 ${bi("tips.again")}") }
    Text(bi("tips.againAbout"), style = MaterialTheme.typography.bodySmall, color = muted)
}

@Composable
private fun InfoLine(emoji: String, label: String, value: String) {
    Column {
        Text("$emoji $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * "🌐 Jeziki · Languages": the pair the app shows, the learner profile's or one chosen here (for testing, and the second
 * learner's phone). Another pair builds every screen anew, this page too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Languages(vm: AppViewModel) {
    val now = vm.langPair
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = vm.langPairChosen == null,
            onClick = { vm.chooseLangPair(null) },
            label = { Text(bi("connectionSheet.asInProfile", "pair" to vm.langPairOfProfile)) },
        )
        for ((label, pick) in listOf(
            bi("connectionSheet.target") to { l: Lang -> now.withTarget(l) },
            bi("connectionSheet.base") to { l: Lang -> now.withBase(l) },
        )) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (l in Lang.entries) {
                    val p = pick(l)
                    FilterChip(selected = vm.langPairChosen != null && p == now, onClick = { vm.chooseLangPair(p) }, label = { Text(l.ownName) })
                }
            }
        }
    }
}

/**
 * "🛠️ Razvijalec · Developer", shown once the version line was tapped seven times (in the release app too): the shadow
 * tutor (tutor/ShadowTutor, docs/plans/05-phase-0.md check 6), on or off, and its debug panel. Turning it on without a key
 * opens the panel, which asks for one. While they are shown, the app's log and the shadow's export go to the paired node
 * (app/DevUpload), and the page says so.
 */
@Composable
private fun Developer(vm: AppViewModel) {
    val shadow = vm.shadow
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // the shadow is hidden while the tutor in the app answers: it would shadow itself
        if (vm.live.settings.on) Text("🕵️ ${bi("developer.shadowHidden")}", style = MaterialTheme.typography.bodySmall, color = muted)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🕵️ ${bi("developer.shadowTutor")}", style = MaterialTheme.typography.bodyLarge)
                Text(shadow.status, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Switch(
                checked = shadow.settings.enabled,
                onCheckedChange = { on ->
                    shadow.setEnabled(on)
                    if (on && !shadow.hasKey) shadow.panelOpen = true
                },
            )
        }
        Text(bi("developer.shadowWhat"), style = MaterialTheme.typography.bodySmall, color = muted)
        // the developer's data on the node while these are on (app/DevUpload, companion/README.md "Developer data on the node")
        Text("📤 ${bi("developer.toNode")}", style = MaterialTheme.typography.bodySmall, color = muted)
        OutlinedButton(onClick = { shadow.panelOpen = true }) { Text("🔍 ${bi("developer.openPanel")}") }
        TextButton(onClick = { shadow.showDeveloper(false); vm.back() }) { Text(bi("developer.hide")) }
    }
}

/** "Lani 0.2.x" (a name and a number: the same in every language); tapped seven times, the developer's section shows. */
@Composable
private fun Version(vm: AppViewModel, modifier: Modifier = Modifier) {
    val shadow = vm.shadow
    var taps by remember { mutableIntStateOf(0) }
    Text(
        "Lani ${si.lanisce.lani.BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 12.dp).clickable {
            if (shadow.settings.developer) return@clickable
            taps++
            if (taps >= 7) {
                taps = 0
                shadow.showDeveloper(true)
                vm.notices.banner = "🛠️ ${bi("developer.shown")}"
            }
        },
    )
}
