package com.snaploop.app.ui

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

@Composable
internal fun GuidedFaceCamera(
    step: Int,
    enabled: Boolean,
    processing: Boolean,
    blockingError: String?,
    onGuidance: (String) -> Unit,
    onCaptured: (ByteArray) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentStep by rememberUpdatedState(step.coerceIn(0, GuidedFacePose.entries.lastIndex))
    val currentEnabled by rememberUpdatedState(enabled)
    val currentProcessing by rememberUpdatedState(processing)
    val currentBlockingError by rememberUpdatedState(blockingError)
    val currentOnGuidance by rememberUpdatedState(onGuidance)
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val evaluator = remember { GuidedFacePoseEvaluator() }
    val captureInFlight = remember { AtomicBoolean(false) }
    val stability = remember { PoseStabilityTracker() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(step) {
        stability.reset()
        captureInFlight.set(false)
        if (step == 0) evaluator.resetAll()
    }

    LaunchedEffect(processing, blockingError) {
        // A rejected embedding leaves us on the same step. Once its error is dismissed and the
        // coordinator is idle, re-arm automatic capture for that same pose.
        if (!processing && blockingError == null) {
            stability.reset()
            captureInFlight.set(false)
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.12f)
                .build()
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        val disposed = AtomicBoolean(false)

        providerFuture.addListener({
            if (disposed.get()) return@addListener
            runCatching {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider

                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                val analyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { useCase ->
                        analysis = useCase
                        useCase.setAnalyzer(cameraExecutor) { proxy ->
                            analyzeFrame(
                                imageProxy = proxy,
                                detector = detector,
                                pose = GuidedFacePose.entries[currentStep],
                                evaluator = evaluator,
                                stability = stability,
                                canCapture = currentEnabled && !currentProcessing && currentBlockingError == null && !captureInFlight.get(),
                                onGuidance = { text -> mainExecutor.execute { currentOnGuidance(text) } },
                                onStable = {
                                    if (captureInFlight.compareAndSet(false, true)) {
                                        captureAutomatic(
                                            context = context,
                                            imageCapture = capture,
                                            cameraExecutor = cameraExecutor,
                                            mainExecutor = mainExecutor,
                                            onCaptured = { bytes -> currentOnCaptured(bytes) },
                                            onError = { error ->
                                                captureInFlight.set(false)
                                                stability.reset()
                                                currentOnError(error)
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    capture,
                    analyzer,
                )
            }.onFailure { throwable ->
                mainExecutor.execute {
                    currentOnError(throwable.message ?: "Front camera could not be started.")
                }
            }
        }, mainExecutor)

        onDispose {
            disposed.set(true)
            analysis?.clearAnalyzer()
            provider?.unbindAll()
            imageCapture = null
            detector.close()
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
            .fillMaxWidth()
            .height(430.dp)
            .clip(RoundedCornerShape(28.dp)),
    )
}

@OptIn(ExperimentalGetImage::class)
private fun analyzeFrame(
    imageProxy: ImageProxy,
    detector: com.google.mlkit.vision.face.FaceDetector,
    pose: GuidedFacePose,
    evaluator: GuidedFacePoseEvaluator,
    stability: PoseStabilityTracker,
    canCapture: Boolean,
    onGuidance: (String) -> Unit,
    onStable: () -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    detector.process(input)
        .addOnSuccessListener { faces ->
            val observation = faces.singleOrNull()?.toObservation(input.width, input.height)
            onGuidance(
                when {
                    faces.size > 1 -> "Only one face should be visible"
                    else -> evaluator.guidance(pose, observation)
                }
            )

            if (!canCapture || faces.size != 1 || observation == null) {
                stability.reset()
                return@addOnSuccessListener
            }

            if (evaluator.matches(pose, observation)) {
                if (stability.observeMatch(SystemClock.elapsedRealtime())) onStable()
            } else {
                stability.reset()
            }
        }
        .addOnFailureListener {
            stability.reset()
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun Face.toObservation(imageWidth: Int, imageHeight: Int): FacePoseObservation {
    val width = max(1, imageWidth).toFloat()
    val height = max(1, imageHeight).toFloat()
    return FacePoseObservation(
        yawDegrees = headEulerAngleY,
        pitchDegrees = headEulerAngleX,
        rollDegrees = headEulerAngleZ,
        centerXFraction = boundingBox.exactCenterX() / width,
        centerYFraction = boundingBox.exactCenterY() / height,
        widthFraction = boundingBox.width() / width,
    )
}

private fun captureAutomatic(
    context: Context,
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
                    if (jpeg.isNullOrEmpty()) {
                        onError("SnapLoop could not process the automatic face capture. Hold still and try again.")
                    } else {
                        onCaptured(jpeg)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                mainExecutor.execute {
                    onError(exception.message ?: "Automatic face capture failed. Please try again.")
                }
            }
        },
    )
}

private class PoseStabilityTracker {
    private var firstMatchAtMillis: Long = 0L
    private var matchingFrames: Int = 0

    fun reset() {
        firstMatchAtMillis = 0L
        matchingFrames = 0
    }

    fun observeMatch(nowMillis: Long): Boolean {
        if (matchingFrames == 0) firstMatchAtMillis = nowMillis
        matchingFrames += 1
        return matchingFrames >= 4 && nowMillis - firstMatchAtMillis >= 450L
    }
}
