from pathlib import Path
import re


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}: found {count} for {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


def replace_regex_once(path: Path, pattern: str, replacement: str) -> None:
    text = path.read_text()
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected exactly one regex match in {path}: found {count} for {pattern[:120]!r}")
    path.write_text(updated)


app = Path("app/src/main/java/com/snaploop/app/ui/SnapLoopApp.kt")
coordinator = Path("app/src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
camera = Path("app/src/main/java/com/snaploop/app/ui/GuidedFaceEnrollmentCamera.kt")
scanner = Path("app/src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt")
shell = Path("app/src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt")

# --- Authentication-gate parity surfaces ---
replace_once(
    app,
    "            AppGate.BIOMETRIC_CONSENT -> ConsentScreen(onAccept = coordinator::acceptBiometricConsent)\n",
    "            AppGate.BIOMETRIC_CONSENT -> ParityConsentScreen(\n                onAccept = coordinator::acceptBiometricConsent,\n                onNotNow = coordinator::skipFaceSetup,\n            )\n",
)
replace_once(
    app,
    "            AppGate.FACE_SETUP -> FaceSetupScreen(\n                state = state,\n                onCapture = coordinator::addFaceCapture,\n                onReset = coordinator::resetFaceCaptures,\n                onComplete = coordinator::completeFaceSetup,\n                onExit = coordinator::cancelFaceSetup,\n            )\n",
    "            AppGate.FACE_SETUP -> ParityFaceSetupScreen(\n                state = state,\n                onCapture = coordinator::addFaceCapture,\n                onReset = coordinator::resetFaceCaptures,\n                onComplete = coordinator::completeFaceSetup,\n                onExit = coordinator::cancelFaceSetup,\n            )\n",
)
replace_once(
    app,
    "        if (state.busy) {\n            Box(\n",
    "        if (state.busy && state.gate != AppGate.FACE_SETUP) {\n            Box(\n",
)

# --- Face Setup capture quality and local reference ---
replace_once(
    coordinator,
    "import com.snaploop.app.face.AndroidFacePipeline\n",
    "import com.snaploop.app.face.AndroidFacePipeline\nimport com.snaploop.app.face.FaceReferenceCropper\n",
)
replace_once(
    coordinator,
    "    private val pendingFaceEmbeddings = mutableListOf<FloatArray>()\n",
    "    private val pendingFaceEmbeddings = mutableListOf<FloatArray>()\n    private var pendingFaceReferenceJpeg: ByteArray? = null\n",
)
replace_regex_once(
    coordinator,
    r"    fun addFaceCapture\(jpeg: ByteArray\) = launchBusy \{.*?\n    \}\n\n    fun resetFaceCaptures\(\)",
    '''    fun addFaceCapture(jpeg: ByteArray) = launchBusy {
        requireUid()
        require(jpeg.isNotEmpty()) { "Camera capture is empty." }
        require(pendingFaceEmbeddings.size < FaceModelPolicy.TARGET_TEMPLATE_COUNT) {
            "Face Setup already has enough captures."
        }

        val captureIndex = pendingFaceEmbeddings.size
        val embedding = withContext(Dispatchers.Default) {
            AndroidFacePipeline(getApplication()).use { it.embeddingForSelfie(jpeg) }
        }

        // Guided poses are intentionally different views of the same person. Comparing every side
        // pose only to the first frontal embedding can reject valid enrollment angles and reduce
        // cross-device recall. Replacement identity protection remains enforced when the complete
        // profile is saved. Prefer the final straight-on frame for the visible local reference.
        if (captureIndex == 0 || captureIndex == FaceModelPolicy.TARGET_TEMPLATE_COUNT - 1) {
            pendingFaceReferenceJpeg = jpeg.copyOf()
        }
        pendingFaceEmbeddings += embedding
        update { copy(faceCaptures = pendingFaceEmbeddings.size, message = null) }
    }

    fun resetFaceCaptures()''',
)
replace_once(
    coordinator,
    "    fun resetFaceCaptures() {\n        pendingFaceEmbeddings.clear()\n        update { copy(faceCaptures = 0, message = null) }\n    }\n",
    "    fun resetFaceCaptures() {\n        pendingFaceEmbeddings.clear()\n        pendingFaceReferenceJpeg = null\n        update { copy(faceCaptures = 0, message = null) }\n    }\n",
)
replace_once(
    coordinator,
    "    fun openFaceSetupFromMain() {\n        pendingFaceEmbeddings.clear()\n",
    "    fun openFaceSetupFromMain() {\n        pendingFaceEmbeddings.clear()\n        pendingFaceReferenceJpeg = null\n",
)
replace_once(
    coordinator,
    "    fun cancelFaceSetup() {\n        pendingFaceEmbeddings.clear()\n",
    "    fun cancelFaceSetup() {\n        pendingFaceEmbeddings.clear()\n        pendingFaceReferenceJpeg = null\n",
)
replace_once(
    coordinator,
    "        prefs.edit().putBoolean(faceSetupSkippedKey(uid), true).apply()\n        pendingFaceEmbeddings.clear()\n",
    "        prefs.edit().putBoolean(faceSetupSkippedKey(uid), true).apply()\n        pendingFaceEmbeddings.clear()\n        pendingFaceReferenceJpeg = null\n",
)
replace_regex_once(
    coordinator,
    r"    fun completeFaceSetup\(\) = launchBusy \{.*?\n    \}\n\n    fun refreshEvents\(\)",
    '''    fun completeFaceSetup() = launchBusy {
        val uid = requireUid()
        require(pendingFaceEmbeddings.size == FaceModelPolicy.TARGET_TEMPLATE_COUNT) {
            "Complete all ${FaceModelPolicy.TARGET_TEMPLATE_COUNT} guided face steps."
        }
        val embeddings = pendingFaceEmbeddings.toList()
        val referenceJpeg = pendingFaceReferenceJpeg
        val average = FloatArray(FaceModelPolicy.EMBEDDING_DIMENSION)
        embeddings.forEach { vector ->
            for (i in vector.indices) average[i] += vector[i]
        }
        val normalized = Embeddings.normalize(average) ?: error("Face profile could not be normalized.")
        val now = System.currentTimeMillis()
        val poses = listOf(
            FaceTemplatePose.CENTER,
            FaceTemplatePose.SIDE_A,
            FaceTemplatePose.SIDE_B,
            FaceTemplatePose.TILTED,
            FaceTemplatePose.ALTERNATE,
        )
        val templates = embeddings.mapIndexed { index, vector ->
            FaceTemplateRecord(
                id = UUID.randomUUID().toString(),
                embedding = vector,
                pose = poses[index],
                quality = 1.0,
                createdAtMillis = now + index,
            )
        }
        faceProfiles.save(
            FaceProfile(
                userId = uid,
                faceIdentityId = null,
                embedding = normalized,
                templates = templates,
                version = FaceModelPolicy.CURRENT_VERSION,
                updatedAtMillis = now,
            )
        )

        // Save only the aligned face crop locally, and only after the server-authoritative profile
        // succeeds. This mirrors iOS and prevents the You/Update Face thumbnail from retaining the
        // full camera frame, shoulders or background.
        val croppedReference = referenceJpeg?.let { bytes ->
            withContext(Dispatchers.Default) {
                runCatching { FaceReferenceCropper.crop(bytes) }.getOrNull()
            }
        }
        if (croppedReference != null) {
            runCatching { faceReferences.save(uid, EncryptedFaceReferenceStore.Kind.GUIDED, croppedReference) }
        }

        prefs.edit().putBoolean(faceSetupSkippedKey(uid), false).apply()
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        users.syncMyProfile(uid, state.value.user?.displayName)
        routeAuthenticated(uid)
    }

    fun refreshEvents()''',
)
replace_once(
    coordinator,
    "    fun signOut() {\n        auth.signOut()\n        pendingFaceEmbeddings.clear()\n",
    "    fun signOut() {\n        auth.signOut()\n        pendingFaceEmbeddings.clear()\n        pendingFaceReferenceJpeg = null\n",
)
replace_once(
    coordinator,
    "        faceReferences.delete(uid)\n        pendingFaceEmbeddings.clear()\n        prefs.edit().putBoolean(faceSetupSkippedKey(uid), false).apply()\n",
    "        faceReferences.delete(uid)\n        pendingFaceEmbeddings.clear()\n        pendingFaceReferenceJpeg = null\n        prefs.edit().putBoolean(faceSetupSkippedKey(uid), false).apply()\n",
)
replace_once(
    coordinator,
    "        auth.signOut()\n        pendingFaceEmbeddings.clear()\n        update { AppUiState(gate = AppGate.AUTH, message = \"Account deleted.\") }\n",
    "        auth.signOut()\n        pendingFaceEmbeddings.clear()\n        pendingFaceReferenceJpeg = null\n        update { AppUiState(gate = AppGate.AUTH, message = \"Account deleted.\") }\n",
)

# Avoid a blocking global spinner during the scanner's preparation phase and clear progress on error.
replace_regex_once(
    coordinator,
    r"    fun scanSelectedEvent\(\) = launchBusy \{.*?\n    \}\n\n    fun refreshPhotos\(\)",
    '''    fun scanSelectedEvent() = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        val preference = memberPreferences.load(event.id)
        require(preference.sharingEnabled) {
            "You have turned off photo sharing for this Event. Turn it on before scanning."
        }
        update { copy(scanProgress = CameraSyncCoordinator.Progress(0, 0, 0), scanResult = null) }
        try {
            val result = withContext(Dispatchers.IO) {
                CameraSyncCoordinator(getApplication()).use { coordinator ->
                    coordinator.scan(
                        eventId = event.id,
                        startMillis = event.startsAt.toEpochMilli(),
                        endMillis = event.endsAt.toEpochMilli(),
                        sharingEnabled = preference.sharingEnabled,
                        includeOwnMatches = preference.includeOwnMatches,
                        ownMatchesRevision = preference.revisionToken,
                        config = remoteConfig,
                        onProgress = { progress -> update { copy(scanProgress = progress) } },
                    )
                }
            }
            update { copy(scanResult = result, scanProgress = null) }
            loadEvent(eventRepository.fetchEvent(event.id))
            refreshAllPhotosInternal(uid, state.value.events)
        } catch (t: Throwable) {
            update { copy(scanProgress = null) }
            throw t
        }
    }

    fun refreshPhotos()''',
)

# --- Guided camera lifecycle and layout ---
replace_once(
    camera,
    "import androidx.compose.ui.text.style.TextAlign\n",
    "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextOverflow\n",
)
replace_once(
    camera,
    "import kotlin.math.max\n",
    "import kotlinx.coroutines.delay\nimport kotlin.math.max\n",
)
replace_once(
    camera,
    "            runCatching { provider?.unbindAll() }\n            if (completionDispatched.compareAndSet(false, true)) onComplete()\n",
    "            runCatching { provider?.unbindAll() }\n            if (completionDispatched.compareAndSet(false, true)) {\n                delay(450L)\n                onComplete()\n            }\n",
)
replace_once(
    camera,
    '''                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (!terminal.get() && currentStepOrdinal.get() == stepAtCapture) {
                                        captureInFlight.set(false)
                                    }
                                }, 900L)
''',
    '''                                if (stepAtCapture < GuidedFacePose.entries.lastIndex) {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        if (!terminal.get() && currentStepOrdinal.get() == stepAtCapture) {
                                            captureInFlight.set(false)
                                        }
                                    }, 900L)
                                }
''',
)
replace_once(
    camera,
    '''                Text(
                    step.title(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
''',
    '''                Text(
                    step.title(),
                    color = Color.White,
                    fontSize = if (step == GuidedFacePose.TILT_DOWN) 9.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
''',
)

# The second encrypted scan-state write for a freshly extracted photo duplicated the expensive
# checkpoint. The end-of-asset checkpoint already persists both corpus and recipient cursors.
replace_once(
    scanner,
    "                state.photoCorpus[asset.id] = corpus\n                states.save(state)\n",
    "                state.photoCorpus[asset.id] = corpus\n",
)

# --- Main shell parity / responsive gallery ---
replace_once(
    shell,
    "            ShellBrandMark(46)\n",
    '''            IconButton(onClick = coordinator::refreshEvents) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh Events", tint = ShellColors.Coral)
            }
''',
)
replace_once(
    shell,
    '''        TextButton(
            onClick = coordinator::refreshEvents,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text("  Refresh Events", color = ShellColors.Coral, fontWeight = FontWeight.Bold)
        }
''',
    "",
)
replace_regex_once(
    shell,
    r"@Composable\nprivate fun ShellEventPhotos\(state: AppUiState, coordinator: AppCoordinator, onBack: \(\) -> Unit\) \{.*?\n\}\n\n@Composable\nprivate fun ShellEventScan",
    '''@Composable
private fun ShellEventPhotos(state: AppUiState, coordinator: AppCoordinator, onBack: () -> Unit) {
    ParityPhotoGallery(
        title = "My Photos",
        subtitle = if (state.photos.size == 1) "1 photo of you found in this Event" else "${state.photos.size} photos of you found in this Event",
        photos = state.photos,
        userId = state.user?.id,
        onRefresh = coordinator::refreshPhotos,
        onBack = onBack,
    )
}

@Composable
private fun ShellEventScan''',
)
replace_regex_once(
    shell,
    r"@Composable\nprivate fun ShellEventScan\(state: AppUiState, coordinator: AppCoordinator, onBack: \(\) -> Unit\) \{.*?\n\}\n\n@Composable\nprivate fun ShellMembers",
    '''@Composable
private fun ShellEventScan(state: AppUiState, coordinator: AppCoordinator, onBack: () -> Unit) {
    ParityEventScanScreen(state = state, coordinator = coordinator, onBack = onBack)
}

@Composable
private fun ShellMembers''',
)
replace_regex_once(
    shell,
    r"@Composable\nprivate fun ShellGallery\(state: AppUiState, coordinator: AppCoordinator\) \{.*?\n\}\n\n@Composable\nprivate fun ShellMatchCard",
    '''@Composable
private fun ShellGallery(state: AppUiState, coordinator: AppCoordinator) {
    ParityPhotoGallery(
        title = "Gallery",
        subtitle = if (state.allPhotos.size == 1) "1 photo of you across all Events" else "${state.allPhotos.size} photos of you across all Events",
        photos = state.allPhotos,
        userId = state.user?.id,
        onRefresh = coordinator::refreshAllPhotos,
    )
}

@Composable
private fun ShellMatchCard''',
)
replace_once(
    shell,
    "    if (privacy) ShellPrivacyDialog({ privacy = false }, { privacy = false; coordinator.withdrawBiometrics() }, { privacy = false; coordinator.deleteAccount() })\n",
    '''    if (privacy) {
        ParityPrivacyScreen(
            onDismiss = { privacy = false },
            onWithdraw = { privacy = false; coordinator.withdrawBiometrics() },
            onDelete = { privacy = false; coordinator.deleteAccount() },
        )
    }
''',
)
replace_once(
    shell,
    "    if (state.busy && state.scanProgress == null) {\n",
    "    if (state.busy && state.scanProgress == null && state.scanResult == null) {\n",
)

print("Applied September 7 Android parity patch")
