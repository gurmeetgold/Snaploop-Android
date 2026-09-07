package com.snaploop.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun QrCodeImage(value: String, modifier: Modifier = Modifier) {
    val bitmap = remember(value) { generateQrBitmap(value, 768) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR code",
        modifier = modifier,
    )
}

private fun generateQrBitmap(value: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (y in 0 until size) {
            for (x in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}

/** Bundled on-device QR scanner; no external camera app or cloud decoding. */
@Composable
internal fun QrCodeScannerScreen(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var error by remember { mutableStateOf<String?>(null) }
    val delivered = remember { AtomicBoolean(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) error = "Camera access is required to scan an Event QR code."
    }

    // Keep the executor alive when the permission state flips from denied to granted.
    // It is owned by this scanner screen and shut down only when the screen leaves composition.
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdownNow() }
    }

    DisposableEffect(permissionGranted, lifecycleOwner) {
        if (!permissionGranted) {
            onDispose { }
        } else {
            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
            val future = ProcessCameraProvider.getInstance(context)
            var provider: ProcessCameraProvider? = null
            var analyzer: QrAnalyzer? = null
            var disposed = false

            future.addListener({
                if (disposed) return@addListener
                runCatching {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val qrAnalyzer = QrAnalyzer(
                        scanner = scanner,
                        onValue = { value ->
                            if (delivered.compareAndSet(false, true)) {
                                mainExecutor.execute { onResult(value) }
                            }
                        },
                        onError = { message -> mainExecutor.execute { error = message } },
                    )
                    analyzer = qrAnalyzer
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(cameraExecutor, qrAnalyzer)

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure { failure ->
                    mainExecutor.execute { error = failure.message ?: "Could not open the camera." }
                }
            }, mainExecutor)

            onDispose {
                disposed = true
                runCatching { provider?.unbindAll() }
                runCatching { analyzer?.close() }
                runCatching { scanner.close() }
            }
        }
    }

    if (!permissionGranted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Scan QR Code", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(
                error ?: "Allow camera access to scan a SnapLoop Event QR code.",
                modifier = Modifier.padding(top = 12.dp),
            )
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 18.dp),
            ) { Text("Allow Camera") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)))

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("✕", color = Color.White, fontSize = 24.sp) }
            Text(
                "Scan QR Code",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f),
            )
        }

        Card(
            modifier = Modifier.align(Alignment.Center).size(270.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)))
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(shape = RoundedCornerShape(20.dp)) {
                Text(
                    error ?: "Point the camera at a SnapLoop Event QR code.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private class QrAnalyzer(
    private val scanner: BarcodeScanner,
    private val onValue: (String) -> Unit,
    private val onError: (String) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val busy = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val image = imageProxy.image
        if (image == null) {
            busy.set(false)
            imageProxy.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }?.let(onValue)
            }
            .addOnFailureListener { onError(it.message ?: "Could not read that QR code.") }
            .addOnCompleteListener {
                busy.set(false)
                imageProxy.close()
            }
    }

    override fun close() = Unit
}
