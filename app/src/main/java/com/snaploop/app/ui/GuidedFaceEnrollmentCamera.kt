package com.snaploop.app.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Guided Face Setup using the exact live analysis frame that qualifies for a requested pose.
 *
 * The live stream first calibrates the device/user neutral Euler offset and then requires several
 * consecutive stable frames for every pose. This is intentionally stricter than a one-frame gate:
 * some OEM front cameras (observed on Redmi/Xiaomi hardware) can report noisy/bias-shifted Euler
 * angles that would otherwise let a straight face race through Left, Right and Tilt.
 */
@Composable
internal fun GuidedFaceEnrollmentCamera(
    captures: Int,
    onCapture: (ByteArray) -> Unit,
    onReset: () -> Unit,
    onComplete: () -> Unit,
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
    val currentStepOrdinal = remember {
        AtomicInteger(captures.coerceIn(0, GuidedFacePose.entries.lastIndex))
    }
    val captureInFlight = remember { AtomicBoolean(false) }
    val terminal = remember { AtomicBoolean(false) }
    val completionDispatched = remember { AtomicBoolean(false) }
    val poseTracker = remember { GuidedFacePoseTracker() }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var instruction by remember {
        mutableStateOf(GuidedFacePose.fromCaptureCount(captures).shortInstruction())
    }
    var detail by remember { mutableStateOf("Center your face inside the oval") }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) cameraError = "Camera access is required for guided Face Setup."
    }

    LaunchedEffect(captures) {
        if (captures == 0) {
            poseTracker.reset()
            completionDispatched.set(false)
            terminal.set(false)
        }

        currentStepOrdinal.set(captures.coerceIn(0, GuidedFacePose.entries.lastIndex))
        captureInFlight.set(false)
        val step = GuidedFacePose.fromCaptureCount(captures)
        instruction = if (captures >= GuidedFacePose.entries.size) {
            "Face scan complete"
        } else {
            step.shortInstruction()
        }
        detail = if (captures >= GuidedFacePose.entries.size) {
            "${GuidedFacePose.entries.size} useful angles captured"
        } else if (captures == 0) {
            "Look straight briefly while SnapLoop calibrates this camera"
        } else {
            "Follow the direction — SnapLoop captures automatically"
        }

        if (captures >= GuidedFacePose.entries.size) {
            terminal.set(true)
            captureInFlight.set(true)
            runCatching { provider?.unbindAll() }
            if (completionDispatched.compareAndSet(false, true)) {
                delay(450L)
                onComplete()
            }
        }
    }

    DisposableEffect(permissionGranted, lifecycleOwner) {
        if (!permissionGranted) {
            onDispose { }
        } else {
            val detector = createGuidedFaceDetector()
            val analysisBusy = AtomicBoolean(false)
            val future = ProcessCameraProvider.getInstance(context)
            var disposed = false
            var analyzer: GuidedFaceAnalyzer? = null

            future.addListener({
                if (disposed || terminal.get()) return@addListener
                runCatching {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val guidedAnalyzer = GuidedFaceAnalyzer(
                        detector = detector,
                        analysisBusy = analysisBusy,
                        poseTracker = poseTracker,
                        isTerminal = terminal::get,
                        currentStep = {
                            GuidedFacePose.entries[
                                currentStepOrdinal.get().coerceIn(0, GuidedFacePose.entries.lastIndex)
                            ]
                        },
                        onGuidance = { nextInstruction, nextDetail ->
                            mainExecutor.execute {
                                if (!terminal.get()) {
                                    instruction = nextInstruction
                                    detail = nextDetail
                                }
                            }
                        },
                        onQualified = qualified@{ jpeg ->
                            if (terminal.get()) return@qualified false
                            val stepAtCapture = currentStepOrdinal.get()
                            if (!captureInFlight.compareAndSet(false, true)) {
                                return@qualified false
                            }
                            mainExecutor.execute {
                                if (terminal.get()) return@execute
                                cameraError = null
                                onCapture(jpeg)
                                // A valid frame normally advances the coordinator immediately. If the
                                // embedding/identity layer rejects it, allow a fresh stable sequence
                                // quickly rather than freezing the enrollment screen.
                                if (stepAtCapture < GuidedFacePose.entries.lastIndex) {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        if (!terminal.get() && currentStepOrdinal.get() == stepAtCapture) {
                                            captureInFlight.set(false)
                                        }
                                    }, 900L)
                                }
                            }
                            true
                        },
                    )
                    analyzer = guidedAnalyzer

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(cameraExecutor, guidedAnalyzer)

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analysis,
                    )
                    cameraError = null
                }.onFailure { error ->
                    mainExecutor.execute {
                        cameraError = error.message ?: "Could not open the front camera."
                    }
                }
            }, mainExecutor)

            onDispose {
                disposed = true
                terminal.set(true)
                runCatching { provider?.unbindAll() }
                runCatching { analyzer?.close() }
                runCatching { detector.close() }
                cameraExecutor.shutdownNow()
            }
        }
    }

    if (!permissionGranted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Face Scan", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(
                "Camera access is required for guided Face Setup.",
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 18.dp),
            ) { Text("Allow Camera") }
            TextButton(onClick = { context.findActivity()?.onBackPressedDispatcher?.onBackPressed() }) {
                Text("Cancel")
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))

        Canvas(Modifier.fillMaxSize()) {
            val ovalWidth = size.width * 0.70f
            val ovalHeight = size.height * 0.46f
            drawOval(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = Offset((size.width - ovalWidth) / 2f, size.height * 0.25f),
                size = Size(ovalWidth, ovalHeight),
                style = Stroke(width = 6f),
            )
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { context.findActivity()?.onBackPressedDispatcher?.onBackPressed() },
                ) {
                    Text("✕", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Face Scan",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(48.dp))
            }

            GuidedStepRail(captures)
            Spacer(Modifier.weight(1f))

            cameraError?.let { error ->
                Card(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${captures.coerceAtMost(GuidedFacePose.entries.size)} of ${GuidedFacePose.entries.size}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        instruction,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        detail,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "No shutter button — each angle must stay stable briefly before it is captured.",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    if (captures > 0 && captures < GuidedFacePose.entries.size) {
                        TextButton(onClick = onReset, modifier = Modifier.padding(top = 4.dp)) {
                            Text("Start Over")
                        }
                    }
                }
            }
        }
    }
}

private fun GuidedFacePose.shortInstruction(): String = when (this) {
    GuidedFacePose.FRONT -> "Look straight at the camera"
    GuidedFacePose.LEFT -> "Turn your face LEFT"
    GuidedFacePose.RIGHT -> "Turn your face RIGHT"
    GuidedFacePose.TILT_DOWN -> "Tilt slightly DOWN"
    GuidedFacePose.FINISH_FRONT -> "Look straight again"
}

private fun GuidedFacePose.title(): String = when (this) {
    GuidedFacePose.FRONT -> "Front"
    GuidedFacePose.LEFT -> "Left"
    GuidedFacePose.RIGHT -> "Right"
    GuidedFacePose.TILT_DOWN -> "Tilt Down"
    GuidedFacePose.FINISH_FRONT -> "Finish"
}

private fun GuidedFacePose.Companion.fromCaptureCount(captures: Int): GuidedFacePose =
    GuidedFacePose.entries[captures.coerceIn(0, GuidedFacePose.entries.lastIndex)]

@Composable
private fun GuidedStepRail(captures: Int) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GuidedFacePose.entries.forEachIndexed { index, step ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(34.dp).background(
                        when {
                            index < captures -> Color(0xFF26A269)
                            index == captures -> Color.White
                            else -> Color.Black.copy(alpha = 0.35f)
                        },
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (index < captures) "✓" else (index + 1).toString(),
                        color = if (index == captures) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    step.title(),
                    color = Color.White,
                    fontSize = if (step == GuidedFacePose.TILT_DOWN) 9.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

private class GuidedFaceAnalyzer(
    private val detector: FaceDetector,
    private val analysisBusy: AtomicBoolean,
    private val poseTracker: GuidedFacePoseTracker,
    private val isTerminal: () -> Boolean,
    private val currentStep: () -> GuidedFacePose,
    private val onGuidance: (String, String) -> Unit,
    private val onQualified: (ByteArray) -> Boolean,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private var lastCaptureAt = 0L

    override fun analyze(imageProxy: ImageProxy) {
        if (isTerminal() || !analysisBusy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            analysisBusy.set(false)
            imageProxy.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { faces ->
                if (!isTerminal()) processFaces(faces, imageProxy)
            }
            .addOnFailureListener {
                if (!isTerminal()) onGuidance("Hold briefly", "Checking face position…")
            }
            .addOnCompleteListener {
                analysisBusy.set(false)
                imageProxy.close()
            }
    }

    private fun processFaces(faces: List<Face>, imageProxy: ImageProxy) {
        if (faces.size != 1) {
            onGuidance(
                if (faces.isEmpty()) "Place your face inside the oval" else "Only one face should be visible",
                "Keep the phone steady",
            )
            return
        }

        val face = faces.single()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val uprightWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val uprightHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
        val width = max(1, uprightWidth).toFloat()
        val height = max(1, uprightHeight).toFloat()
        val box = face.boundingBox
        val observation = FacePoseObservation(
            yawDegrees = face.headEulerAngleY,
            pitchDegrees = face.headEulerAngleX,
            rollDegrees = face.headEulerAngleZ,
            centerXFraction = box.exactCenterX() / width,
            centerYFraction = box.exactCenterY() / height,
            widthFraction = max(0, box.width()).toFloat() / width,
            heightFraction = max(0, box.height()).toFloat() / height,
        )

        val step = currentStep()
        val decision = poseTracker.evaluate(step, observation)
        if (!decision.readyToCapture) {
            onGuidance(decision.instruction, decision.detail)
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastCaptureAt < 1_400L) return

        val jpeg = runCatching { imageProxy.toFrontFacingJpeg() }.getOrNull()
        if (jpeg == null) {
            onGuidance("Hold briefly", "Capturing this angle…")
            return
        }

        if (onQualified(jpeg)) {
            lastCaptureAt = now
            poseTracker.onCaptured()
            onGuidance("Captured", "Great — moving to the next angle")
        }
    }

    override fun close() = Unit
}

private fun createGuidedFaceDetector(): FaceDetector = FaceDetection.getClient(
    FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.15f)
        .enableTracking()
        .build(),
)

private fun ImageProxy.toFrontFacingJpeg(): ByteArray {
    val source = toBitmap()
    val rotation = imageInfo.rotationDegrees
    val upright = if (rotation == 0) {
        source
    } else {
        Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true,
        ).also { if (it !== source) source.recycle() }
    }

    val mirrored = Bitmap.createBitmap(
        upright,
        0,
        0,
        upright.width,
        upright.height,
        Matrix().apply { postScale(-1f, 1f) },
        true,
    )
    if (mirrored !== upright) upright.recycle()

    return ByteArrayOutputStream().use { output ->
        check(mirrored.compress(Bitmap.CompressFormat.JPEG, 93, output)) {
            "Could not encode Face Setup frame"
        }
        mirrored.recycle()
        output.toByteArray()
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
