package com.snaploop.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max

/**
 * Android equivalent of iOS GuidedFaceEnrollmentView.
 *
 * The front camera remains inside SnapLoop. Live frames are analyzed locally and a still photo is
 * captured automatically once the requested pose is stable. No system camera activity or manual
 * shutter is involved.
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
    val currentStepOrdinal = remember { AtomicInteger(captures.coerceIn(0, GuidedEnrollmentStep.entries.lastIndex)) }
    val captureInFlight = remember { AtomicBoolean(false) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var instruction by remember { mutableStateOf(GuidedEnrollmentStep.fromCaptureCount(captures).shortInstruction) }
    var detail by remember { mutableStateOf("Center your face inside the oval") }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) cameraError = "Camera access is required for guided Face Setup."
    }

    LaunchedEffect(captures) {
        currentStepOrdinal.set(captures.coerceIn(0, GuidedEnrollmentStep.entries.lastIndex))
        captureInFlight.set(false)
        val step = GuidedEnrollmentStep.fromCaptureCount(captures)
        instruction = if (captures >= GuidedEnrollmentStep.entries.size) "Face scan complete" else step.shortInstruction
        detail = if (captures >= GuidedEnrollmentStep.entries.size) {
            "${GuidedEnrollmentStep.entries.size} useful angles captured"
        } else {
            "Follow the direction and hold briefly"
        }
        if (captures >= GuidedEnrollmentStep.entries.size) onComplete()
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
                if (disposed) return@addListener
                runCatching {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    analyzer = GuidedFaceAnalyzer(
                        detector = detector,
                        analysisBusy = analysisBusy,
                        currentStep = { GuidedEnrollmentStep.entries[currentStepOrdinal.get()] },
                        onGuidance = { nextInstruction, nextDetail ->
                            mainExecutor.execute {
                                instruction = nextInstruction
                                detail = nextDetail
                            }
                        },
                        onQualified = {
                            val now = System.currentTimeMillis()
                            if (!captureInFlight.compareAndSet(false, true)) return@GuidedFaceAnalyzer
                            mainExecutor.execute {
                                captureGuidedStill(
                                    context = context,
                                    imageCapture = capture,
                                    cameraExecutor = cameraExecutor,
                                    mainExecutor = mainExecutor,
                                    onCaptured = { jpeg ->
                                        cameraError = null
                                        onCapture(jpeg)
                                        // Coordinator acceptance normally advances captures and clears this flag.
                                        // If embedding/identity validation rejects the frame, allow a safe retry.
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            if (currentStepOrdinal.get() == captures.coerceIn(0, GuidedEnrollmentStep.entries.lastIndex)) {
                                                captureInFlight.set(false)
                                            }
                                        }, 4_000L)
                                    },
                                    onError = { message ->
                                        captureInFlight.set(false)
                                        cameraError = message
                                    },
                                )
                            }
                        },
                    )

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(cameraExecutor, analyzer!!)

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analysis,
                        capture,
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
            Text("Camera access is required for guided Face Setup.", modifier = Modifier.padding(top = 12.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 18.dp)) {
                Text("Allow Camera")
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))

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
            Text(
                "Face Scan",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 22.dp),
            )
            GuidedStepRail(captures)
            Spacer(Modifier.weight(1f))

            cameraError?.let { error ->
                Card(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(14.dp))
                }
            }

            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(instruction, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Text(detail, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Photos capture automatically — keep the phone steady.",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (captures > 0 && captures < GuidedEnrollmentStep.entries.size) {
                        TextButton(onClick = onReset, modifier = Modifier.padding(top = 4.dp)) { Text("Start Over") }
                    }
                }
            }
        }
    }
}

private enum class GuidedEnrollmentStep(
    val title: String,
    val shortInstruction: String,
) {
    FRONT("Front", "Look straight at the camera"),
    LEFT("Left", "Turn your face LEFT"),
    RIGHT("Right", "Turn your face RIGHT"),
    TILT_DOWN("Tilt Down", "Tilt slightly DOWN"),
    FINISH_FRONT("Finish", "Look straight again");

    fun qualifies(yaw: Double, pitch: Double): Boolean = when (this) {
        FRONT -> abs(yaw) <= 8.0 && abs(pitch) <= 10.0
        LEFT -> yaw in -38.0..-16.0
        RIGHT -> yaw in 16.0..38.0
        TILT_DOWN -> pitch in 9.0..28.0 && abs(yaw) <= 18.0
        FINISH_FRONT -> abs(yaw) <= 10.0 && abs(pitch) <= 12.0
    }

    fun directionHint(yaw: Double, pitch: Double): String = when (this) {
        FRONT, FINISH_FRONT -> when {
            yaw < -8.0 -> "Turn slightly RIGHT toward center"
            yaw > 8.0 -> "Turn slightly LEFT toward center"
            pitch < -10.0 -> "Raise your chin slightly"
            pitch > 10.0 -> "Lower your chin slightly"
            else -> "Hold still"
        }
        LEFT -> if (yaw > -16.0) "Keep turning LEFT" else "Come slightly back toward center"
        RIGHT -> if (yaw < 16.0) "Keep turning RIGHT" else "Come slightly back toward center"
        TILT_DOWN -> if (pitch < 9.0) "Lower your chin a little" else "Raise your chin slightly"
    }

    companion object {
        fun fromCaptureCount(captures: Int): GuidedEnrollmentStep = entries[captures.coerceIn(0, entries.lastIndex)]
    }
}

@Composable
private fun GuidedStepRail(captures: Int) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GuidedEnrollmentStep.entries.forEachIndexed { index, step ->
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
                Text(step.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private class GuidedFaceAnalyzer(
    private val detector: FaceDetector,
    private val analysisBusy: AtomicBoolean,
    private val currentStep: () -> GuidedEnrollmentStep,
    private val onGuidance: (String, String) -> Unit,
    private val onQualified: () -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private var frameCounter = 0
    private var qualifiedFrames = 0
    private var lastStep: GuidedEnrollmentStep? = null
    private var lastCaptureAt = 0L

    override fun analyze(imageProxy: ImageProxy) {
        frameCounter += 1
        if (frameCounter % 3 != 0 || !analysisBusy.compareAndSet(false, true)) {
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
            .addOnSuccessListener { faces -> processFaces(faces, imageProxy.width, imageProxy.height) }
            .addOnFailureListener {
                qualifiedFrames = 0
                onGuidance("Hold still", "Checking face position…")
            }
            .addOnCompleteListener {
                analysisBusy.set(false)
                imageProxy.close()
            }
    }

    private fun processFaces(faces: List<Face>, width: Int, height: Int) {
        if (faces.size != 1) {
            qualifiedFrames = 0
            onGuidance(
                if (faces.isEmpty()) "Place your face inside the oval" else "Only one face should be visible",
                "Keep the phone steady",
            )
            return
        }

        val face = faces.single()
        val imageArea = max(1L, width.toLong() * height.toLong()).toDouble()
        val faceArea = max(0, face.boundingBox.width()).toLong() * max(0, face.boundingBox.height()).toLong()
        if (faceArea / imageArea < 0.12) {
            qualifiedFrames = 0
            onGuidance("Move a little closer", "Keep your whole face inside the oval")
            return
        }
        if (abs(face.headEulerAngleZ.toDouble()) > 22.0) {
            qualifiedFrames = 0
            onGuidance("Keep your head upright", "Hold the phone steady")
            return
        }

        val leftEyeOk = face.leftEyeOpenProbability?.let { it >= 0.45f } ?: true
        val rightEyeOk = face.rightEyeOpenProbability?.let { it >= 0.45f } ?: true
        if (!leftEyeOk || !rightEyeOk) {
            qualifiedFrames = 0
            onGuidance("Open your eyes and hold still", "Use even lighting")
            return
        }

        val step = currentStep()
        if (lastStep != step) {
            lastStep = step
            qualifiedFrames = 0
        }
        val yaw = face.headEulerAngleY.toDouble()
        val pitch = face.headEulerAngleX.toDouble()

        if (!step.qualifies(yaw, pitch)) {
            qualifiedFrames = 0
            onGuidance(step.shortInstruction, step.directionHint(yaw, pitch))
            return
        }

        qualifiedFrames += 1
        onGuidance(step.shortInstruction, if (qualifiedFrames >= 2) "Perfect — capturing…" else "Hold still")
        val now = System.currentTimeMillis()
        if (qualifiedFrames >= 2 && now - lastCaptureAt >= 700L) {
            qualifiedFrames = 0
            lastCaptureAt = now
            onQualified()
        }
    }

    override fun close() = Unit
}

private fun createGuidedFaceDetector(): FaceDetector = FaceDetection.getClient(
    FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setMinFaceSize(0.10f)
        .build()
)

private fun captureGuidedStill(
    context: android.content.Context,
    imageCapture: ImageCapture,
    cameraExecutor: ExecutorService,
    mainExecutor: java.util.concurrent.Executor,
    onCaptured: (ByteArray) -> Unit,
    onError: (String) -> Unit,
) {
    val directory = File(context.cacheDir, "face-setup").apply { mkdirs() }
    val file = File.createTempFile("snaploop-guided-", ".jpg", directory)
    val options = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCapture.takePicture(
        options,
        cameraExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val jpeg = runCatching { prepareFaceCapture(file) }.getOrNull()
                file.delete()
                mainExecutor.execute {
                    if (jpeg != null && jpeg.isNotEmpty()) {
                        onCaptured(jpeg)
                    } else {
                        onError("SnapLoop could not process the captured frame. Hold still and try again.")
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                mainExecutor.execute {
                    onError(exception.message ?: "SnapLoop could not capture this face step.")
                }
            }
        },
    )
}
