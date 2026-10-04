package si.lanisce.lani.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.PairParse
import si.lanisce.lani.data.PairPayload
import si.lanisce.lani.data.PairProblem
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.l10n.bi
import java.util.concurrent.Executors

internal fun cameraGranted(c: Context) = ContextCompat.checkSelfPermission(c, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private sealed interface Scan {
    /** Looking for a code; [hint] when the camera saw a QR code that isn't Lani's. */
    data class Looking(val hint: String? = null) : Scan
    data object Pairing : Scan
    data class Failed(val message: String) : Scan
    data object NoCamera : Scan
}

/**
 * "📷 Skeniraj QR · Scan the QR code", full screen: asks for the camera (only now), reads the QR code that
 * `companion/bin/lani-pair` shows, and pairs ([AppViewModel.pair]). Closes itself once paired.
 */
@Composable
fun PairDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(cameraGranted(context)) }
    var state by remember { mutableStateOf<Scan>(Scan.Looking()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) state = Scan.NoCamera
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    fun onCode(text: String) {
        if (state !is Scan.Looking) return
        val parsed = PairPayload.parse(text)
        if (parsed is PairParse.Invalid && parsed.problem == PairProblem.NOT_PAIRING) {
            state = Scan.Looking(hint = parsed.problem.message) // someone else's QR code: keep looking
            return
        }
        state = Scan.Pairing
        vm.pair(text) { error -> if (error == null) onDismiss() else state = Scan.Failed(error) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("📷 ${bi("common.scanQrCode")}", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                bi("pairScan.computerRunCompanionBin"),
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when (val s = state) {
                    is Scan.Looking -> if (granted) QrCamera(onCode = ::onCode, modifier = Modifier.fillMaxSize())
                    Scan.Pairing -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Color.White)
                        Text(bi("pairScan.pairingTutor"), color = Color.White, textAlign = TextAlign.Center)
                    }
                    is Scan.Failed -> Text(s.message, color = Color(0xFFFFD1D8), textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
                    Scan.NoCamera -> Text(
                        "📷 ${bi("pairScan.needsCamera")}. ${bi("pairScan.onlyReadsCode")}.",
                        color = Color(0xFFFFD1D8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            (state as? Scan.Looking)?.hint?.let { Text(it, color = Color(0xFFFFD1D8), textAlign = TextAlign.Center) }
            when (state) {
                is Scan.Failed -> BigButton("🔄 ${bi("pairScan.tryAgain")}", onClick = { state = Scan.Looking() }, color = TriglavRed)
                Scan.NoCamera -> {
                    // After "don't ask again", only the system settings can allow the camera.
                    val activity = context as? Activity
                    val canAsk = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                    if (canAsk) BigButton("📷 ${bi("pairScan.allowCamera")}", onClick = { state = Scan.Looking(); permission.launch(Manifest.permission.CAMERA) }, color = TriglavRed)
                    else BigButton("⚙️ ${bi("common.settings")}", onClick = { openAppSettings(context) }, color = TriglavRed)
                }
                else -> Unit
            }
            TextButton(onClick = onDismiss) { Text(bi("common.close"), color = Color.White) }
        }
    }
    // Back from the system settings with the camera allowed: scan.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state == Scan.NoCamera && cameraGranted(context)) {
            granted = true
            state = Scan.Looking()
        }
    }
}

/** The back camera's preview; every frame goes to ZXing (QR codes only) until [onCode] gets one. Also a town's invitation (ui/towns). */
@Composable
internal fun QrCamera(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onCode)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val provider = remember { ProcessCameraProvider.getInstance(context) }
    var disposed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            provider.addListener({ runCatching { provider.get().unbindAll() } }, ContextCompat.getMainExecutor(context))
            executor.shutdown()
        }
    }
    AndroidView(
        factory = { ctx ->
            val view = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE // a TextureView: fine inside a dialog window
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            provider.addListener({
                if (disposed) return@addListener
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor, QrAnalyzer { text -> view.post { if (!disposed) latest(text) } })
                runCatching {
                    provider.get().unbindAll()
                    provider.get().bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
        modifier = modifier,
    )
}

/** ZXing on the camera's luminance plane: QR codes only, dark on light or light on dark (a terminal's colours). */
private class QrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.ALSO_INVERTED to true,
            ),
        )
    }

    override fun analyze(image: ImageProxy) {
        image.use {
            val plane = it.planes[0]
            val bytes = ByteArray(plane.buffer.remaining()).also { b -> plane.buffer.get(b) }
            val text = try {
                val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
            } catch (e: ReaderException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            } finally {
                reader.reset()
            }
            text?.let(onText)
        }
    }
}
