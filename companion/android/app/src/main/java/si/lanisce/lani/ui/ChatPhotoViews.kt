package si.lanisce.lani.ui

import android.Manifest
import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import si.lanisce.lani.data.ChatPhoto
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import java.io.File

// The tutor chat's photos (companion/README.md "Tutor chat", "Photos"): 📷 by the input (take one, or pick one from the
// gallery), the photo above the input before it goes, its thumbnail in the bubble, the photo full screen. The camera is
// CameraX's, as the QR scanner's (PairScan.kt), asked for only when a photo is taken; the gallery is Android's photo
// picker, which needs no storage permission (the app sees only the photo picked).

/** What [PhotoButton]'s menu chose. */
internal enum class PhotoSource { CAMERA, GALLERY }

/** "📷" by the chat's input: its menu takes a photo ("📸 Fotografiraj · Take a photo") or picks one ("🖼️ Iz galerije"). */
@Composable
internal fun PhotoButton(enabled: Boolean, onChoose: (PhotoSource) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, enabled = enabled) { EmojiLabel("📷", bi("chatSheet.photoAdd")) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("📸 ${bi("chatSheet.photoTake")}") }, onClick = { menu = false; onChoose(PhotoSource.CAMERA) })
            DropdownMenuItem(text = { Text("🖼️ ${bi("chatSheet.photoPick")}") }, onClick = { menu = false; onChoose(PhotoSource.GALLERY) })
        }
    }
}

/** The photo the next message carries, above the input: its thumbnail and "📷 Gre s sporočilom"; ✕ lets it go. */
@Composable
internal fun DraftPhotoCard(photo: ChatPhoto?, preparing: Boolean, load: suspend (ChatPhoto, Int) -> Bitmap?, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                if (photo == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else PhotoImage(photo, 56, load, ContentScale.Crop, description = null)
            }
            Text(
                "📷 ${bi(if (preparing || photo == null) "chatSheet.photoPreparing" else "chatSheet.photoReady")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            if (photo != null) TextButton(onClick = onClear) { EmojiLabel("✕", bi("chatSheet.photoRemove")) }
        }
    }
}

/**
 * A photo in its bubble: the thumbnail at its proportions (at most 240 dp wide; [textBelow]: a gap before the text), a
 * tap opens it full screen, a long press opens the bubble's menu ([onLongPress]); a deleted one says so. TalkBack reads "Fotografija · Photo" and offers
 * to open it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BubblePhoto(photo: ChatPhoto, color: Color, load: suspend (ChatPhoto, Int) -> Bitmap?, onOpen: () -> Unit, onLongPress: () -> Unit, textBelow: Boolean = true) {
    if (photo.deleted) {
        Text("📷 ${bi("chatSheet.photoDeleted")}", color = color.copy(alpha = 0.8f), fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall)
        return
    }
    val label = bi("chatSheet.photo")
    val ratio = if (photo.width > 0 && photo.height > 0) (photo.width.toFloat() / photo.height).coerceIn(0.5f, 2f) else 1f
    Box(
        Modifier
            .padding(bottom = if (textBelow) 6.dp else 0.dp)
            .widthIn(max = 240.dp)
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .combinedClickable(onClickLabel = bi("chatSheet.photoOpen"), role = Role.Image, onLongClick = onLongPress, onClick = onOpen)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        PhotoImage(photo, 240, load, ContentScale.Crop, description = null, missing = { Text("📷", color = color) })
    }
}

/**
 * Photo [photo] at [dp] on its long side (the phone's copy, else fetched from the node by [load]); a spinner while it
 * loads, [missing] when neither has it.
 */
@Composable
private fun PhotoImage(
    photo: ChatPhoto,
    dp: Int,
    load: suspend (ChatPhoto, Int) -> Bitmap?,
    scale: ContentScale,
    description: String?,
    missing: @Composable () -> Unit = { Text("📷") },
) {
    val px = with(LocalDensity.current) { dp.dp.roundToPx() }
    val image by produceState<Result<Bitmap?>?>(null, photo.id, px) { value = runCatching { load(photo, px) } }
    when (val r = image) {
        null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else -> r.getOrNull()?.let { Image(it.asImageBitmap(), description, contentScale = scale, modifier = Modifier.fillMaxSize()) } ?: missing()
    }
}

/**
 * Photo [photo] full screen, on black: two fingers zoom it (up to 6×) and move it, a double tap zooms in or back; ✕ or
 * back closes it. [description] is what TalkBack reads (the message's text, if any).
 */
@Composable
internal fun PhotoViewer(photo: ChatPhoto, description: String, load: suspend (ChatPhoto, Int) -> Bitmap?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(IntSize.Zero) }
        fun clamp(o: Offset, z: Float): Offset {
            val mx = box.width * (z - 1) / 2
            val my = box.height * (z - 1) / 2
            return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
        }
        val image by produceState<Result<Bitmap?>?>(null, photo.id) { value = runCatching { load(photo, 2048) } }
        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { box = it }) {
            when (val r = image) {
                null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                else -> r.getOrNull()?.let { bmp ->
                    Image(
                        bmp.asImageBitmap(),
                        contentDescription = description,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, move, by, _ ->
                                    zoom = (zoom * by).coerceIn(1f, 6f)
                                    pan = if (zoom == 1f) Offset.Zero else clamp(pan + move, zoom)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { at ->
                                    if (zoom > 1f) {
                                        zoom = 1f
                                        pan = Offset.Zero
                                    } else {
                                        zoom = 2.5f
                                        pan = clamp(Offset(box.width / 2f - at.x, box.height / 2f - at.y) * 1.5f, zoom)
                                    }
                                })
                            }
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            },
                    )
                } ?: Text(
                    "📷 ${bi("chatSheet.photoMissing")}",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            Column(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp), horizontalAlignment = Alignment.End) {
                FilledIconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
                ) { EmojiLabel("✕", Labels.CLOSE) }
            }
            if (zoom == 1f && image?.getOrNull() != null) Text(
                bi("chatSheet.photoZoom"),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
            )
        }
    }
}

/** "🗑️ Izbriši fotografijo?": what goes (the phone's and the node's copy), what stays (the text), what the tutor saw. */
@Composable
internal fun DeletePhotoDialog(onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🗑️ ${bi("chatSheet.photoDeleteAsk")}") },
        text = { Text(inTarget("chatSheet.photoDeleteWhat") + "\n" + inBase("chatSheet.photoDeleteWhat")) },
        confirmButton = { TextButton(onClick = onDelete) { Text(bi("common.delete")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(bi("chatSheet.cancel")) } },
    )
}

/**
 * "📸 Fotografiraj · Take a photo", full screen: asks for the camera (only now, as the QR scanner does), the back
 * camera's picture, and the shutter; the photo taken goes to [onTaken] as a file in the app's cache (made ready and sent
 * from there, then deleted). Without the camera allowed it says why, and offers to allow it (or the settings).
 */
@Composable
internal fun PhotoCaptureDialog(onTaken: (File) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(cameraGranted(context)) }
    var denied by remember { mutableStateOf(false) }
    var taking by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    fun take() {
        taking = true
        val dir = File(context.cacheDir, "chat-camera").apply { mkdirs() }
        val f = File(dir, "${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(f).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onTaken(f)
                override fun onError(exception: ImageCaptureException) {
                    f.delete()
                    taking = false
                    failed = true
                }
            },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            if (granted) PhotoCamera(capture, onFailed = { failed = true }, modifier = Modifier.fillMaxSize())
            else if (denied) Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("📷 ${bi("chatSheet.photoNoCamera")}.", color = Color(0xFFFFD1D8), textAlign = TextAlign.Center)
                // after "don't ask again", only the system settings can allow the camera
                val activity = context as? Activity
                val canAsk = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                if (canAsk) BigButton("📷 ${bi("pairScan.allowCamera")}", onClick = { denied = false; permission.launch(Manifest.permission.CAMERA) })
                else BigButton("⚙️ ${bi("common.settings")}", onClick = { openAppSettings(context) })
            }
            if (failed) Text(
                "📷 ${bi("chatSheet.photoFailed")}",
                color = Color(0xFFFFD1D8),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
            )
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) { Text(bi("common.close"), color = Color.White) }
                FilledIconButton(
                    onClick = ::take,
                    enabled = granted && !taking,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.size(72.dp),
                ) {
                    if (taking) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Color.Black)
                    else EmojiLabel("📸", bi("chatSheet.photoTake"), style = MaterialTheme.typography.headlineMedium)
                }
                Box(Modifier.size(64.dp))
            }
        }
    }
    // back from the system settings with the camera allowed: the picture
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (denied && cameraGranted(context)) {
            granted = true
            denied = false
        }
    }
}

/** The back camera's picture (the front one on a phone without), bound with [capture] for the shutter. */
@Composable
private fun PhotoCamera(capture: ImageCapture, onFailed: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val provider = remember { ProcessCameraProvider.getInstance(context) }
    var disposed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            provider.addListener({ runCatching { provider.get().unbindAll() } }, ContextCompat.getMainExecutor(context))
        }
    }
    AndroidView(
        factory = { ctx ->
            val view = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE // a TextureView: fine inside a dialog window
                scaleType = PreviewView.ScaleType.FIT_CENTER // the whole picture the photo takes
            }
            provider.addListener({
                if (disposed) return@addListener
                runCatching {
                    val p = provider.get()
                    val camera = if (p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    view.display?.rotation?.let { capture.targetRotation = it }
                    p.unbindAll()
                    p.bindToLifecycle(owner, camera, preview, capture)
                }.onFailure { onFailed() }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
        modifier = modifier,
    )
}
