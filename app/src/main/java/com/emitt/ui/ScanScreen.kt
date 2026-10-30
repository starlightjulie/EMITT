package com.emitt.ui

import androidx.compose.ui.res.stringResource
import com.emitt.R
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.emitt.data.Encoding
import com.emitt.data.Payload
import com.emitt.data.SavedItem
import com.emitt.data.summary
import com.emitt.qr.QrDecoder
import com.emitt.ui.theme.LocalLook
import java.util.concurrent.Executors

class ScanHit(val text: String, val format: String)

@Composable
fun ScanScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var hit by remember { mutableStateOf<ScanHit?>(null) }
    var torch by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        val r = bmp?.let { QrDecoder(tryHarder = true).decode(it) }
        if (r == null) vm.toast(R.string.no_code_in_image) else hit = ScanHit(r.text, r.barcodeFormat.name)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val h = hit
        if (h == null) {
            TermPanel(title = stringResource(R.string.scanner)) {
                if (granted) {
                    CameraBox(torch) { r -> if (hit == null) hit = r }
                } else {
                    StatusLine(Status.INFO, stringResource(R.string.camera_why))
                    ButtonRow { TermButton(stringResource(R.string.allow_camera), { ask.launch(Manifest.permission.CAMERA) }, Modifier.weight(1f), Icons.Rounded.CameraAlt, primary = true) }
                }
                ButtonRow {
                    if (granted) TermButton(stringResource(if (torch) R.string.light_off else R.string.light), { torch = !torch }, Modifier.weight(1f), if (torch) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn)
                    TermButton(stringResource(R.string.from_image), { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), Icons.Rounded.Image)
                }
            }
        } else {
            ScanResult(vm, h) { hit = null }
        }
    }
}

@Composable
private fun ScanResult(vm: MainViewModel, hit: ScanHit, onAgain: () -> Unit) {
    val context = LocalContext.current
    val payload = remember(hit) { Encoding.parse(hit.text) }
    TermPanel(title = stringResource(R.string.decoded, hit.format.replace('_', ' '))) {
        KvRow(stringResource(R.string.type), stringResource(payload.kind.label))
        when (payload) {
            is Payload.Wifi -> { KvRow("SSID", payload.ssid); KvRow(stringResource(R.string.security), stringResource(payload.security.label)); KvRow(stringResource(R.string.password), payload.password) }
            is Payload.Contact -> { KvRow(stringResource(R.string.name), payload.name); KvRow(stringResource(R.string.kind_phone), payload.phone); KvRow(stringResource(R.string.kind_email), payload.email) }
            else -> KvRow(stringResource(R.string.content), hit.text)
        }
        ButtonRow {
            if (payload is Payload.Url) TermButton(stringResource(R.string.open), { context.openUrl(payload.url) }, Modifier.weight(1f), Icons.AutoMirrored.Rounded.OpenInNew, primary = true)
            TermButton(stringResource(R.string.copy), { context.copyText("EMITT", hit.text); vm.toast(R.string.copied) }, Modifier.weight(1f), Icons.Rounded.ContentCopy)
        }
        SectionLabel(stringResource(R.string.re_emit))
        ButtonRow {
            TermButton(stringResource(R.string.edit), { vm.newDraft(payload); vm.output = Output.QR; vm.tab = Tab.CREATE }, Modifier.weight(1f), Icons.Rounded.Edit)
            TermButton(stringResource(R.string.to_tag), { vm.newDraft(payload); vm.output = Output.NFC; vm.tab = Tab.CREATE }, Modifier.weight(1f), Icons.Rounded.Nfc)
        }
        ButtonRow {
            TermButton(stringResource(R.string.tab_flipper), { vm.newDraft(payload); vm.output = Output.FLIPPER; vm.tab = Tab.CREATE }, Modifier.weight(1f), Icons.Rounded.SdCard)
            TermButton(stringResource(R.string.save), {
                vm.library.upsert(SavedItem(name = payload.summary().take(40).ifBlank { "QR" }, payload = payload))
                vm.toast(R.string.lib_saved)
            }, Modifier.weight(1f), Icons.Rounded.Bookmark)
        }
        ButtonRow { TermButton(stringResource(R.string.scan_again), onAgain, Modifier.weight(1f), Icons.Rounded.Refresh, primary = true) }
    }
}

@Composable
private fun CameraBox(torch: Boolean, onHit: (ScanHit) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val look = LocalLook.current
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val p = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(android.util.Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                ).build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, LumaAnalyzer { r -> handler.post { onHit(r) } })
            runCatching {
                provider.unbindAll()
                camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, p, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }
    DisposableEffect(camera, torch) {
        camera?.cameraControl?.enableTorch(torch)
        onDispose { }
    }

    val sweep = rememberInfiniteTransition(label = "sweep")
    val y by sweep.animateFloat(0.12f, 0.88f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    Box(Modifier.fillMaxWidth().aspectRatio(0.85f).clip(look.controlShape)) {
        AndroidView({ preview }, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            // Bracketed viewfinder, echoing the EMITT mark
            val s = size.minDimension * 0.36f
            val c = Offset(size.width / 2, size.height / 2)
            val arm = s * 0.3f
            val path = Path().apply {
                moveTo(c.x - s, c.y - s + arm); lineTo(c.x - s, c.y - s); lineTo(c.x - s + arm, c.y - s)
                moveTo(c.x + s - arm, c.y - s); lineTo(c.x + s, c.y - s); lineTo(c.x + s, c.y - s + arm)
                moveTo(c.x + s, c.y + s - arm); lineTo(c.x + s, c.y + s); lineTo(c.x + s - arm, c.y + s)
                moveTo(c.x - s + arm, c.y + s); lineTo(c.x - s, c.y + s); lineTo(c.x - s, c.y + s - arm)
            }
            drawPath(path, look.accent, style = Stroke(4.dp.toPx()))
            val yy = size.height * y
            drawLine(look.signal.copy(alpha = 0.8f), Offset(c.x - s, yy), Offset(c.x + s, yy), 2.dp.toPx())
        }
    }
    Dim(stringResource(R.string.scan_hint), Modifier.padding(top = 8.dp))
}

/** Decodes the luminance plane of each frame, trying a rotated copy so sideways barcodes work too. */
private class LumaAnalyzer(val onHit: (ScanHit) -> Unit) : ImageAnalysis.Analyzer {
    private val decoder = QrDecoder()
    private var buf = ByteArray(0)
    private var rot = ByteArray(0)
    private var done = false

    override fun analyze(image: ImageProxy) {
        if (done) { image.close(); return }
        val plane = image.planes[0]
        val w = image.width
        val h = image.height
        val stride = plane.rowStride
        val need = stride * h
        if (buf.size < need) buf = ByteArray(need)
        val b = plane.buffer.apply { rewind() }
        b.get(buf, 0, minOf(b.remaining(), need))
        val rotation = image.imageInfo.rotationDegrees
        image.close()

        var r = decoder.decodeLuma(buf, stride, w, h)
        if (r == null && rotation % 180 != 0) {
            if (rot.size < w * h) rot = ByteArray(w * h)
            for (yy in 0 until h) {
                val row = yy * stride
                for (xx in 0 until w) rot[xx * h + (h - 1 - yy)] = buf[row + xx]
            }
            r = decoder.decodeLuma(rot, h, h, w)
        }
        if (r != null) {
            done = true
            onHit(ScanHit(r.text, r.barcodeFormat.name))
        }
    }
}
