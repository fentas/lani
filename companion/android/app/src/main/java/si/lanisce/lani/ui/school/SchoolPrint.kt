package si.lanisce.lani.ui.school

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.Link
import si.lanisce.lani.data.PrintJob
import si.lanisce.lani.data.PrintMissing
import si.lanisce.lani.data.Prints
import si.lanisce.lani.l10n.bi
import java.io.IOException
import kotlin.coroutines.resume

// "🖨️ Natisni list · Print sheet" on a unit and its row, "🖨️ Vsi listi · All sheets" on a level (companion/COURSE.md,
// "Printed sheets"): the bridge's page (the generator's own, fit.js in it) loaded into a WebView off screen, fit.js let
// fit each unit to its pages, then Android's print dialog: its preview, a printer, "Save as PDF", sharing.

/** Why a print can't be asked for now (no tutor, or the tutor out of reach); null when it can. */
internal fun printBlocked(vm: AppViewModel): String? = if (vm.bridgeUrl == null || vm.link == Link.OFFLINE) bi("school.printOffline") else null

/**
 * A unit's or a level's print button, and below it why it can't print now or why the last try of [troubleOf]'s didn't
 * (a level's: its units' rows too).
 */
@Composable
internal fun PrintButton(vm: AppViewModel, job: PrintJob?, label: String, modifier: Modifier = Modifier, troubleOf: (PrintJob) -> Boolean = { it == job }) {
    job ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blocked = printBlocked(vm)
    val busy = vm.school.printing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { startPrint(vm, context, scope, job) }, enabled = blocked == null && busy == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (busy == job) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(bi("school.printing"))
            } else Text("🖨️ $label")
        }
        PrintNote(vm, blocked, vm.school.printTrouble?.takeIf { troubleOf(it.first) }?.second)
    }
}

/** A unit row's 🖨️ (its sheet): a tap prints it; why it can't, or didn't, is said under its level's "All sheets". */
@Composable
internal fun PrintIcon(vm: AppViewModel, job: PrintJob) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled = printBlocked(vm) == null && vm.school.printing == null
    val label = bi("school.printSheet")
    IconButton(onClick = { startPrint(vm, context, scope, job) }, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        if (vm.school.printing == job) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text("🖨️", style = MaterialTheme.typography.titleMedium, modifier = Modifier.alpha(if (enabled) 1f else 0.4f))
    }
}

/** "📶 Za tiskanje mora biti mentor dosegljiv." or what went wrong. */
@Composable
internal fun PrintNote(vm: AppViewModel, blocked: String?, trouble: String?) {
    val text = blocked?.let { "📶 $it" } ?: trouble?.let { "⚠️ $it" } ?: return
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Gets [job]'s page from the bridge and opens the print dialog with it; what goes wrong is kept for [PrintNote]. */
private fun startPrint(vm: AppViewModel, context: Context, scope: CoroutineScope, job: PrintJob) {
    val activity = context.findActivity() ?: return
    if (vm.school.printing != null) return
    vm.school.printing = job
    vm.school.printTrouble = null
    scope.launch {
        try {
            SheetPrinter.print(activity, vm.school.printPage(job), Prints.jobName(job))
        } catch (e: CancellationException) {
            throw e
        } catch (e: PrintMissing) {
            vm.school.printTrouble = job to bi("school.printMissing")
        } catch (e: Exception) {
            Log.w(SheetPrinter.TAG, "print ${Prints.jobName(job)}", e)
            vm.school.printTrouble = job to bi("school.printFailed", "why" to (e.message ?: e.javaClass.simpleName))
        } finally {
            vm.school.printing = null
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Prints a page of the bridge's: a WebView of its own, never on screen, loads it (it stands alone: its CSS, fonts and
 * fit.js inlined, nothing fetched), fit.js fits each unit to A4 (data-fitted on <html> once done: the generator waits for
 * the same in Chromium), then the platform's print dialog prints it A4 portrait without margins of its own: the page's
 * @page sets them (12 mm), as Chromium's PDF has them.
 */
internal object SheetPrinter {
    const val TAG = "LaniPrint"
    /** How long the page may take to load and fit (the booklet: every unit fitted in turn). */
    private const val READY_MS = 30_000L

    /** The pages whose print dialog is open: the dialog's adapter draws from their WebView until it closes. */
    private val open = mutableSetOf<WebView>()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun print(activity: Activity, html: String, jobName: String) {
        val web = WebView(activity)
        web.settings.apply {
            javaScriptEnabled = true // fit.js
            textZoom = 100 // the phone's font size must not change what fits
            // a WebView raises text under 8 px to 8 px, Chromium (the generator's) doesn't: the sheet's smallest is 6.4 pt
            // (8.5 px) now, and a smaller one must fit alike too
            minimumFontSize = 1
            minimumLogicalFontSize = 1
            blockNetworkLoads = true
            allowFileAccess = false
            allowContentAccess = false
        }
        val loaded = CompletableDeferred<Unit>()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                loaded.complete(Unit)
            }
        }
        try {
            web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            withTimeout(READY_MS) {
                loaded.await()
                while (eval(web, "document.documentElement.dataset.fitted || ''") != "\"yes\"") delay(100)
            }
        } catch (e: TimeoutCancellationException) {
            web.destroy()
            throw IOException("the page didn't get ready")
        } catch (e: Throwable) {
            web.destroy()
            throw e
        }
        // what fit.js chose, unit by unit, as the generator reports it (QA compares the two)
        Log.i(TAG, "$jobName: ${eval(web, "document.getElementById('fit-report').textContent")}")
        val inner = web.createPrintDocumentAdapter(jobName)
        val adapter = object : PrintDocumentAdapter() {
            override fun onStart() = inner.onStart()
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) =
                inner.onLayout(old, new, cancel, callback, extras)
            override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) =
                inner.onWrite(pages, destination, cancel, callback)
            override fun onFinish() {
                inner.onFinish()
                open.remove(web)
                web.destroy()
            }
        }
        open += web
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        (activity.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(jobName, adapter, attributes)
    }

    private suspend fun eval(web: WebView, js: String): String = suspendCancellableCoroutine { c -> web.evaluateJavascript(js) { c.resume(it ?: "null") } }
}
