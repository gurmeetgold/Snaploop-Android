from pathlib import Path


def require_replace(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Expected source block not found: {label}")
    return text.replace(old, new, 1)


coordinator = Path("app/src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
text = coordinator.read_text()

if "restoreCanRetry" not in text:
    text = require_replace(
        text,
        "import kotlinx.coroutines.withContext\n",
        "import kotlinx.coroutines.withContext\nimport kotlinx.coroutines.withTimeoutOrNull\n",
        "coroutine imports",
    )
    text = require_replace(
        text,
        "    val busy: Boolean = false,\n    val message: String? = null,",
        "    val busy: Boolean = false,\n    val restoreCanRetry: Boolean = false,\n    val restoreError: String? = null,\n    val message: String? = null,",
        "AppUiState restore fields",
    )
    text = require_replace(
        text,
        "    private val pendingFaceJpegs = mutableListOf<ByteArray>()\n",
        "    private val pendingFaceJpegs = mutableListOf<ByteArray>()\n    private val pendingFaceEmbeddings = mutableListOf<FloatArray>()\n",
        "face capture storage",
    )

    old_init = '''    init {
        if (BuildConfig.FIREBASE_CONFIG_PRESENT) {
            viewModelScope.launch {
                remoteConfig = runCatching {
                    SnapLoopRemoteConfig(FirebaseRemoteConfig.getInstance()).initialize()
                }.getOrDefault(RemoteConfigValues())
                restore()
            }
        }
    }
'''
    new_init = '''    init {
        if (BuildConfig.FIREBASE_CONFIG_PRESENT) {
            // Remote Config is best-effort. Session routing must never wait indefinitely for it.
            viewModelScope.launch {
                remoteConfig = withTimeoutOrNull(5_000L) {
                    runCatching {
                        SnapLoopRemoteConfig(FirebaseRemoteConfig.getInstance()).initialize()
                    }.getOrDefault(RemoteConfigValues())
                } ?: RemoteConfigValues()
            }
            restore()
        }
    }
'''
    text = require_replace(text, old_init, new_init, "coordinator init")

    old_restore = '''    fun restore() = launchBusy {
        val uid = auth.currentUserId
        if (uid == null) {
            pendingFaceJpegs.clear()
            update { AppUiState(gate = AppGate.AUTH) }
            return@launchBusy
        }
        routeAuthenticated(uid)
    }
'''
    new_restore = '''    fun restore() {
        viewModelScope.launch {
            update {
                copy(
                    gate = AppGate.RESTORING,
                    busy = true,
                    restoreCanRetry = false,
                    restoreError = null,
                    message = null,
                )
            }
            try {
                val uid = auth.currentUserId
                if (uid == null) {
                    pendingFaceJpegs.clear()
                    pendingFaceEmbeddings.clear()
                    update { AppUiState(gate = AppGate.AUTH) }
                    return@launch
                }

                val restored = withTimeoutOrNull(15_000L) {
                    routeAuthenticated(uid)
                    true
                } ?: false

                if (!restored) {
                    update {
                        copy(
                            gate = AppGate.RESTORING,
                            restoreCanRetry = true,
                            restoreError = "Session restore timed out. Check your internet connection and try again.",
                        )
                    }
                }
            } catch (t: Throwable) {
                update {
                    copy(
                        gate = AppGate.RESTORING,
                        restoreCanRetry = true,
                        restoreError = userMessage(t),
                    )
                }
            } finally {
                update { copy(busy = false) }
            }
        }
    }
'''
    text = require_replace(text, old_restore, new_restore, "restore function")

    old_capture = '''        if (pendingFaceJpegs.isNotEmpty()) {
            val first = withContext(Dispatchers.Default) {
                AndroidFacePipeline(getApplication()).use { it.embeddingForSelfie(pendingFaceJpegs.first()) }
            }
            val similarity = Embeddings.cosine(first, embedding) ?: 0.0
            require(similarity >= FaceModelPolicy.EVALUATION_MATCH_THRESHOLD) {
                "This capture does not appear to be the same person. Retake it."
            }
        }
        pendingFaceJpegs += jpeg
        if (pendingFaceJpegs.size == 1) {
            faceReferences.save(uid, EncryptedFaceReferenceStore.Kind.GUIDED, jpeg)
        }
        update { copy(faceCaptures = pendingFaceJpegs.size, message = "Face capture ${pendingFaceJpegs.size} of ${FaceModelPolicy.TARGET_TEMPLATE_COUNT} accepted.") }
'''
    new_capture = '''        if (pendingFaceEmbeddings.isNotEmpty()) {
            val similarity = Embeddings.cosine(pendingFaceEmbeddings.first(), embedding) ?: 0.0
            require(similarity >= FaceModelPolicy.EVALUATION_MATCH_THRESHOLD) {
                "This capture does not appear to be the same person. Retake this step."
            }
        }
        pendingFaceJpegs += jpeg
        pendingFaceEmbeddings += embedding
        if (pendingFaceJpegs.size == 1) {
            faceReferences.save(uid, EncryptedFaceReferenceStore.Kind.GUIDED, jpeg)
        }
        // The guided screen advances visibly after every accepted capture; no modal success dialog.
        update { copy(faceCaptures = pendingFaceJpegs.size, message = null) }
'''
    text = require_replace(text, old_capture, new_capture, "face capture acceptance")

    text = require_replace(
        text,
        '''    fun resetFaceCaptures() {
        pendingFaceJpegs.clear()
        update { copy(faceCaptures = 0, message = null) }
    }
''',
        '''    fun resetFaceCaptures() {
        pendingFaceJpegs.clear()
        pendingFaceEmbeddings.clear()
        update { copy(faceCaptures = 0, message = null) }
    }
''',
        "reset face captures",
    )
    text = require_replace(
        text,
        '''    fun replayFaceSetupForUpdate() {
        pendingFaceJpegs.clear()
        update { copy(gate = AppGate.FACE_SETUP, faceCaptures = 0, message = null) }
    }
''',
        '''    fun replayFaceSetupForUpdate() {
        pendingFaceJpegs.clear()
        pendingFaceEmbeddings.clear()
        update { copy(gate = AppGate.FACE_SETUP, faceCaptures = 0, message = null) }
    }
''',
        "replay face setup",
    )

    old_complete = '''        require(pendingFaceJpegs.size >= 3) { "Capture at least 3 guided selfies." }
        val embeddings = withContext(Dispatchers.Default) {
            AndroidFacePipeline(getApplication()).use { pipeline ->
                pendingFaceJpegs.map { pipeline.embeddingForSelfie(it) }
            }
        }
'''
    new_complete = '''        require(pendingFaceJpegs.size == FaceModelPolicy.TARGET_TEMPLATE_COUNT) {
            "Complete all ${FaceModelPolicy.TARGET_TEMPLATE_COUNT} guided face steps."
        }
        require(pendingFaceEmbeddings.size == pendingFaceJpegs.size) {
            "Face Setup capture state is incomplete. Start over."
        }
        val embeddings = pendingFaceEmbeddings.toList()
'''
    text = require_replace(text, old_complete, new_complete, "complete face setup requirement")

    # Keep both in-memory lists synchronized wherever Face Setup is cleared.
    for label, old, new in [
        (
            "complete face setup cleanup",
            "        pendingFaceJpegs.clear()\n        users.syncMyProfile(uid, state.value.user?.displayName)",
            "        pendingFaceJpegs.clear()\n        pendingFaceEmbeddings.clear()\n        users.syncMyProfile(uid, state.value.user?.displayName)",
        ),
        (
            "sign out cleanup",
            "        pendingFaceJpegs.clear()\n        if (uid != null) faceReferences.delete(uid)",
            "        pendingFaceJpegs.clear()\n        pendingFaceEmbeddings.clear()\n        if (uid != null) faceReferences.delete(uid)",
        ),
        (
            "withdraw cleanup",
            "        pendingFaceJpegs.clear()\n        update { copy(gate = AppGate.BIOMETRIC_CONSENT",
            "        pendingFaceJpegs.clear()\n        pendingFaceEmbeddings.clear()\n        update { copy(gate = AppGate.BIOMETRIC_CONSENT",
        ),
        (
            "delete cleanup",
            "        pendingFaceJpegs.clear()\n        update { AppUiState(gate = AppGate.AUTH, message = \"Account deleted.\")",
            "        pendingFaceJpegs.clear()\n        pendingFaceEmbeddings.clear()\n        update { AppUiState(gate = AppGate.AUTH, message = \"Account deleted.\")",
        ),
    ]:
        text = require_replace(text, old, new, label)

    coordinator.write_text(text)


ui = Path("app/src/main/java/com/snaploop/app/ui/SnapLoopApp.kt")
text = ui.read_text()

if "FileProvider.getUriForFile" not in text:
    text = text.replace("import android.graphics.Bitmap\n", "")
    text = text.replace("import java.io.ByteArrayOutputStream\n", "")
    text = require_replace(
        text,
        "import androidx.core.content.ContextCompat\n",
        "import androidx.core.content.ContextCompat\nimport androidx.core.content.FileProvider\n",
        "FileProvider import",
    )
    text = require_replace(
        text,
        "import java.time.ZoneId\n",
        "import java.io.File\nimport java.time.ZoneId\n",
        "File import",
    )
    text = require_replace(
        text,
        '            AppGate.RESTORING -> LoadingScreen("Restoring your SnapLoop session…")',
        '''            AppGate.RESTORING -> LoadingScreen(
                message = "Restoring your SnapLoop session…",
                canRetry = state.restoreCanRetry,
                error = state.restoreError,
                onRetry = coordinator::restore,
            )''',
        "restoring screen call",
    )

    old_loading = '''@Composable
private fun LoadingScreen(message: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(message, Modifier.padding(top = 18.dp))
    }
}
'''
    new_loading = '''@Composable
private fun LoadingScreen(
    message: String,
    canRetry: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!canRetry) CircularProgressIndicator()
        Text(message, Modifier.padding(top = 18.dp))
        if (canRetry) {
            Text(
                error ?: "SnapLoop could not finish restoring your session.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Your account and Face Setup have not been deleted.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
        }
    }
}
'''
    text = require_replace(text, old_loading, new_loading, "loading screen")

    start = text.index("@Composable\nprivate fun FaceSetupScreen(")
    end = text.index("\n@Composable\nprivate fun MainTabs", start)
    new_face = '''@Composable
private fun FaceSetupScreen(
    captures: Int,
    onCapture: (ByteArray) -> Unit,
    onReset: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    val prompts = listOf(
        "Look straight ahead" to "Keep your face centered and look directly at the camera.",
        "Turn slightly left" to "Turn your head a little to your left while keeping both eyes visible.",
        "Turn slightly right" to "Turn your head a little to your right while keeping both eyes visible.",
        "Tilt slightly" to "Tilt your head slightly while keeping your full face inside the frame.",
        "Natural angle" to "Finish with a relaxed, natural front-facing angle.",
    )

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (success && file != null && file.exists()) {
            val jpeg = runCatching { file.readBytes() }.getOrNull()
            file.delete()
            if (!jpeg.isNullOrEmpty()) onCapture(jpeg)
        } else {
            file?.delete()
        }
    }
    val launchCapture = {
        val directory = File(context.cacheDir, "face-setup").apply { mkdirs() }
        val file = File.createTempFile("snaploop-face-", ".jpg", directory)
        pendingCaptureFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        cameraLauncher.launch(uri)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCapture()
    }

    val complete = captures >= prompts.size
    val current = captures.coerceIn(0, prompts.lastIndex)

    ScreenColumn {
        BrandHeader(
            "Face Setup",
            "Complete all five guided face captures so SnapLoop can recognize you reliably across different angles.",
        )
        if (!complete) {
            GradientCard(
                title = "Step ${captures + 1} of ${prompts.size} — ${prompts[current].first}",
                subtitle = prompts[current].second,
            )
            Text("Progress: $captures of ${prompts.size} accepted", fontWeight = FontWeight.Bold)
            Text(
                "Use good lighting, keep only your face in frame, and remove anything covering your eyes. " +
                    "If the camera opens on the rear lens, switch it to the front camera.",
            )
            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (granted) launchCapture() else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Capture ${captures + 1} of ${prompts.size}") }
        } else {
            GradientCard(
                title = "All 5 face steps captured",
                subtitle = "Your guided face set is ready to save.",
            )
        }
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(),
            enabled = complete,
        ) { Text("Complete Face Setup") }
        if (captures > 0) {
            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Start Over")
            }
        }
        Text(
            "All five guided captures are required. Each accepted capture is checked locally before the next step unlocks.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
'''
    text = text[:start] + new_face + text[end:]

    old_jpeg = '''private fun Bitmap.toJpeg(): ByteArray = ByteArrayOutputStream().use { output ->
    compress(Bitmap.CompressFormat.JPEG, 95, output)
    output.toByteArray()
}

'''
    text = require_replace(text, old_jpeg, "", "thumbnail JPEG helper")
    ui.write_text(text)


manifest = Path("app/src/main/AndroidManifest.xml")
text = manifest.read_text()
if ".fileprovider" not in text:
    marker = '        <activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTop">'
    provider = '''        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

'''
    text = require_replace(text, marker, provider + marker, "FileProvider manifest entry")
    manifest.write_text(text)


paths = Path("app/src/main/res/xml/file_paths.xml")
if not paths.exists():
    paths.write_text('''<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="face_setup" path="face-setup/" />
</paths>
''')
