package si.lanisce.lani.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.data.Links
import si.lanisce.lani.l10n.bi

/**
 * The app's link gate ([LinkGate]), for what has a window of its own (a sheet, a dialog): the platform gives each window
 * its own [LocalUriHandler], which would open any link at once, so [GateLinks] puts the gate back there.
 */
val LocalLinkGate = staticCompositionLocalOf<UriHandler?> { null }

/**
 * [content] with the app's link gate ([LocalLinkGate]) as its [LocalUriHandler] again: in a sheet's or a dialog's
 * window ([LaniSheet], [Markdown]), whose platform handler would open a link without asking.
 */
@Composable
fun GateLinks(content: @Composable () -> Unit) {
    val gate = LocalLinkGate.current
    if (gate == null) content() else CompositionLocalProvider(LocalUriHandler provides gate, content = content)
}

/**
 * Every link tapped in the app's texts ([androidx.compose.ui.text.LinkAnnotation.Url]: the tutor's, ui/Markdown.kt)
 * goes through here, the app's [LocalUriHandler] (companion/README.md "Tutor chat", "Links"; in a sheet or a dialog
 * again through [GateLinks]): one to an allowed site ([Links.ALLOWED]: fran.si, Lani's repository) opens at once; any
 * other asks first, "🔗 Odpreti example.com? · Open example.com?", its whole address under it and, for an http one, that
 * it isn't secure; "Odpri · Open" or "Prekliči · Cancel". What isn't a link ([Links.parse]: another scheme, a host
 * hidden behind a user name) opens nothing.
 */
@Composable
fun LinkGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val asking = remember { mutableStateOf<Links.Link?>(null) }
    val gate = remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val link = Links.parse(uri) ?: return
                if (link.direct) openInBrowser(context, link.url) else asking.value = link
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides gate, LocalLinkGate provides gate, content = content)
    asking.value?.let { link ->
        LinkDialog(link, onOpen = { asking.value = null; openInBrowser(context, link.url) }, onDismiss = { asking.value = null })
    }
}

/** "Open example.com?": the link's domain plainly, its whole address, and for http that it isn't secure. */
@Composable
private fun LinkDialog(link: Links.Link, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔗 ${bi("links.openTitle", "host" to link.host)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bi("links.leaves"), style = MaterialTheme.typography.bodyMedium)
                Text(
                    link.url, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
                if (!link.secure) Text("⚠️ ${bi("links.notSecure")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text(bi("links.open")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(bi("links.cancel")) } },
    )
}

/**
 * [url] in the phone's browser (a browsable view, which an app verified for the site may take instead); no browser:
 * "Ni brskalnika … · No browser …", nothing else happens.
 */
internal fun openInBrowser(context: Context, url: String) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    if (context !is Activity) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "🔗 ${bi("links.noBrowser")}", Toast.LENGTH_LONG).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "🔗 ${bi("links.noBrowser")}", Toast.LENGTH_LONG).show()
    }
}
