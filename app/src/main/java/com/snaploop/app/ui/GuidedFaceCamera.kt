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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

internal data class GuidedFaceLiveState(
    val instruction: String,
    val detail: String,
    val framingStatus: FaceFramingStatus,
    val poseQualified: Boolean,
)

@Composable
internal fun GuidedFaceCamera(
    step: Int,
    enabled: Boolean,
    processing: Boolean,
    blockingError: String?,
    onLiveState: (GuidedFaceLiveState) -> Unit,
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
    val currentOnLiveState by rememberUpdatedState(onLiveState)
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val evaluator = remember { GuidedFacePoseEvaluator() }
    val captureInFlight = remember { AtomicBoolean(false) }
    val stability = remember { PoseStabilityTracker() }

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(step) {
        stability.reset()
        captureInFlight.set(false)
    }

    LaunchedEffect(processing, blockingError) {
        // A rejected embedding stays on the same step. Dismissing the error re-arms auto capture.
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
                .setMinFaceSize(0.10f)
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
                                onLiveState = { state -> mainExecutor.execute { currentOnLiveState(state) } },
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
            detector.close()
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize(),
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
    onLiveState: (GuidedFaceLiveState) -> Unit,
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
            if (faces.size != 1) {
                stability.reset()
                onLiveState(
                    GuidedFaceLiveState(
                        instruction = if (faces.isEmpty()) "Place your face inside the oval" else "Only one face should be visible",
                        detail = "Keep the phone steady",
                        framingStatus = FaceFramingStatus.NOT_DETECTED,
                        poseQualified = false,
                    )
                )
                return@addOnSuccessListener
            }

            val observation = faces.single().toObservation(input.width, input.height)
            val framing = evaluator.framingStatus(observation)
            val qualified = framing == FaceFramingStatus.READY && evaluator.matches(pose, observation)
            val instruction = when (framing) {
                FaceFramingStatus.NOT_DETECTED -> "Place your face inside the oval"
                FaceFramingStatus.NEEDS_ADJUSTMENT ->
                    if (observation.areaFraction < 0.12f) "Move a little closer" else "Center your face inside the oval"
                FaceFramingStatus.READY -> evaluator.instruction(pose)
            }
            val detail = when (framing) {
                FaceFramingStatus.NOT_DETECTED -> "Keep the phone steady"
                FaceFramingStatus.NEEDS_ADJUSTMENT -> "Keep your whole face inside the oval"
                FaceFramingStatus.READY -> evaluator.detail(pose, observation)
            }
            onLiveState(GuidedFaceLiveState(instruction, detail, framing, qualified))

            if (!canCapture || !qualified) {
                stability.reset()
                return@addOnSuccessListener
            }

            if (stability.observeMatch(SystemClock.elapsedRealtime())) onStable()
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
        heightFraction = boundingBox.height() / height,
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
                    if (jpeg == null || jpeg.isEmpty()) {
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
        // Mirrors iOS's "hold briefly" behavior while suppressing one-frame false positives.
        return matchingFrames >= 3 && nowMillis - firstMatchAtMillis >= 300L
    }
}
