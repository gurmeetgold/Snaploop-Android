from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text()


def write(rel, text):
    (ROOT / rel).write_text(text)


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# P0 privacy / consent enforcement + responsive own-match toggle
# -----------------------------------------------------------------------------
rel = "app/src/main/java/com/snaploop/app/ui/AppCoordinator.kt"
s = read(rel)

old = '''    fun addFaceCapture(jpeg: ByteArray) = launchBusy {
        requireUid()
        require(jpeg.isNotEmpty()) { "Camera capture is empty." }
'''
new = '''    fun addFaceCapture(jpeg: ByteArray) = launchBusy {
        val uid = requireUid()
        if (!ensureActiveBiometricConsent(uid)) return@launchBusy
        require(jpeg.isNotEmpty()) { "Camera capture is empty." }
'''
s = replace_once(s, old, new, "consent before face capture")

old = '''    /** Opens Face Setup from the authenticated app and always permits returning to Main. */
    fun openFaceSetupFromMain() {
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        update {
            copy(
                gate = AppGate.FACE_SETUP,
                faceSetupMode = FaceSetupMode.RETURN_TO_MAIN,
                faceCaptures = 0,
                message = null,
            )
        }
    }
'''
new = '''    /** Opens Face Setup from the authenticated app only while biometric consent is active. */
    fun openFaceSetupFromMain() = launchBusy {
        val uid = requireUid()
        if (!ensureActiveBiometricConsent(uid)) return@launchBusy
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        update {
            copy(
                gate = AppGate.FACE_SETUP,
                faceSetupMode = FaceSetupMode.RETURN_TO_MAIN,
                faceCaptures = 0,
                message = null,
            )
        }
    }
'''
s = replace_once(s, old, new, "consent before opening face setup")

old = '''    fun completeFaceSetup() = launchBusy {
        val uid = requireUid()
        val returnToYou = state.value.faceSetupMode == FaceSetupMode.RETURN_TO_MAIN
'''
new = '''    fun completeFaceSetup() = launchBusy {
        val uid = requireUid()
        if (!ensureActiveBiometricConsent(uid)) return@launchBusy
        val returnToYou = state.value.faceSetupMode == FaceSetupMode.RETURN_TO_MAIN
'''
s = replace_once(s, old, new, "consent before face completion")

start = s.index('    fun setIncludeOwnMatches(enabled: Boolean) = launchBusy {')
end = s.index('\n    fun leaveSelectedEvent()', start)
old = s[start:end]
new = '''    fun setIncludeOwnMatches(enabled: Boolean) = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        val me = state.value.members.firstOrNull { it.userId == uid }
            ?: error("Your Event membership could not be loaded.")
        require(me.sharingEnabled) { "Turn on photo sharing for this Event first." }
        val previous = memberPreferences.load(event.id)
        require(previous.sharingEnabled) { "Turn on photo sharing for this Event first." }
        if (enabled) {
            if (!ensureActiveBiometricConsent(uid)) return@launchBusy
            require(faceProfiles.load(uid) != null) { "Set up your face to see your own photo matches." }
        }

        memberPreferences.setIncludeOwnMatches(event.id, enabled)
        val saved = memberPreferences.load(event.id)
        val resolvedEnabled = saved.sharingEnabled && saved.includeOwnMatches

        // Preference persistence is the user-visible transaction. Reflect it immediately instead
        // of blocking the switch behind a potentially long on-device corpus replay.
        update {
            copy(
                includeOwnMatches = resolvedEnabled,
                photos = if (resolvedEnabled) photos else photos.filter { it.ownerUserId != uid },
            )
        }

        val shouldReplay = OwnMatchReplayPolicy.shouldReplay(
            previousEnabled = previous.includeOwnMatches,
            savedEnabled = saved.includeOwnMatches,
            sharingEnabled = saved.sharingEnabled,
            event = event,
            now = Instant.now(),
            gracePeriodDays = remoteConfig.eventGracePeriodDays,
        )

        // Replay and refresh are opportunistic background work. A successful preference update
        // must never leave the switch spinning for tens of seconds while local face scanning runs.
        viewModelScope.launch {
            if (shouldReplay) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        CameraSyncCoordinator(getApplication()).use { coordinator ->
                            coordinator.scan(
                                eventId = event.id,
                                startMillis = event.startsAt.toEpochMilli(),
                                endMillis = event.endsAt.toEpochMilli(),
                                sharingEnabled = saved.sharingEnabled,
                                includeOwnMatches = saved.includeOwnMatches,
                                ownMatchesRevision = saved.revisionToken,
                                config = remoteConfig,
                            )
                        }
                    }
                }
            }
            if (state.value.selectedEvent?.id == event.id) {
                runCatching { refreshPhotosInternal(event.id, uid) }
            }
            runCatching { refreshAllPhotosInternal(uid, state.value.events) }
        }
    }
'''
s = s[:start] + new + s[end:]

old = '''    private fun requireUid(): String = auth.currentUserId ?: error("Authentication is required.")
'''
new = '''    /**
     * Consent is server-authoritative. Any stale entry point into Face Setup is closed immediately,
     * pending biometric material is discarded, and the user is returned to the consent gate.
     */
    private suspend fun ensureActiveBiometricConsent(uid: String): Boolean {
        if (consent.load(uid)?.isActive == true) return true
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        update {
            copy(
                gate = AppGate.BIOMETRIC_CONSENT,
                faceSetupMode = FaceSetupMode.INITIAL_GATE,
                faceCaptures = 0,
                message = null,
            )
        }
        return false
    }

    private fun requireUid(): String = auth.currentUserId ?: error("Authentication is required.")
'''
s = replace_once(s, old, new, "consent helper")
write(rel, s)


# -----------------------------------------------------------------------------
# Face Setup: clear processing state, centered mark, gallery-based Test My Face
# -----------------------------------------------------------------------------
rel = "app/src/main/java/com/snaploop/app/ui/ParityFaceSetupScreen.kt"
s = read(rel)

s = replace_once(s, 'import androidx.activity.compose.BackHandler\n', 'import androidx.activity.compose.BackHandler\nimport androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\n', "face picker activity imports")
s = replace_once(s, 'import androidx.compose.foundation.layout.Column\n', 'import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.Column\n', "face box import")
s = replace_once(s, 'import androidx.compose.material3.AlertDialog\n', 'import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.CircularProgressIndicator\n', "face progress import")
s = replace_once(s, 'import androidx.compose.runtime.Composable\n', 'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n', "face launched effect import")
s = replace_once(s, 'import androidx.compose.runtime.mutableStateOf\n', 'import androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope\n', "face remember imports")
s = replace_once(s, 'import androidx.compose.ui.Modifier\n', 'import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalContext\n', "face context import")
s = replace_once(s, 'import androidx.compose.ui.unit.sp\n', 'import androidx.compose.ui.unit.sp\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\n', "face coroutine imports")

old = '''    var scanOpen by rememberSaveable { mutableStateOf(false) }
    var testOpen by rememberSaveable { mutableStateOf(false) }
    var deleteConfirmationOpen by rememberSaveable { mutableStateOf(false) }
    val hasFaceProfile = state.user?.hasFaceProfile == true
'''
new = '''    var scanOpen by rememberSaveable { mutableStateOf(false) }
    var finishingSetup by rememberSaveable { mutableStateOf(false) }
    var deleteConfirmationOpen by rememberSaveable { mutableStateOf(false) }
    var testBusy by rememberSaveable { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<FaceSetupTestResult?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }
    val hasFaceProfile = state.user?.hasFaceProfile == true
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val testActions = remember(context) { FaceSetupParityActions(context) }
    val testPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val userId = state.user?.id
        if (uri != null && userId != null) {
            scope.launch {
                testBusy = true
                testResult = null
                testError = null
                runCatching {
                    val jpeg = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Could not read that photo.")
                    }
                    testActions.testSavedFace(userId, jpeg)
                }.onSuccess { testResult = it }
                    .onFailure { testError = it.message ?: "Could not test that photo." }
                testBusy = false
            }
        }
    }

    LaunchedEffect(finishingSetup, state.busy, state.message) {
        if (finishingSetup && !state.busy && state.message != null) finishingSetup = false
    }
'''
s = replace_once(s, old, new, "face state and picker")

old = '''            onComplete = {
                // Close the camera immediately once all five valid poses are captured. Persisting
                // the face profile can involve ML + network work and must not leave a frozen camera
                // on screen while that work finishes.
                scanOpen = false
                onComplete()
            },
'''
new = '''            onComplete = {
                scanOpen = false
                finishingSetup = true
                onComplete()
            },
'''
s = replace_once(s, old, new, "face completion processing state")

old = '''    if (testOpen) {
        val userId = state.user?.id
        if (userId != null) {
            ParityFaceTestCamera(userId = userId, onClose = { testOpen = false })
            return
        }
        testOpen = false
    }

    BackHandler(onBack = onExit)
'''
new = '''    if (finishingSetup) {
        BackHandler(enabled = true) { }
        ParityBrandBackground {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        "Finishing Face Setup…",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Text(
                        "Securing your face template. Please keep SnapLoop open.",
                        color = Color(0xFF66636C),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    )
                }
            }
        }
        return
    }

    BackHandler(onBack = onExit)
'''
s = replace_once(s, old, new, "remove face test camera and add completion screen")

s = replace_once(s, '                    ParityBrandMark(92)\n', '                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {\n                        ParityBrandMark(92)\n                    }\n', "center face setup brand mark")

old = '''                OutlinedButton(
                    onClick = { testOpen = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Test My Face Setup", fontWeight = FontWeight.Bold)
                }
'''
new = '''                OutlinedButton(
                    onClick = { testPhotoPicker.launch("image/*") },
                    enabled = !testBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    if (testBusy) {
                        CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Test My Face Setup", fontWeight = FontWeight.Bold)
                    }
                }
'''
s = replace_once(s, old, new, "gallery-based test my face")

insert = '''
    testResult?.let { result ->
        AlertDialog(
            onDismissRequest = { testResult = null },
            title = { Text(if (result.accepted) "Face Setup Working" else "Face Not Recognized") },
            text = { Text(result.message) },
            confirmButton = { TextButton(onClick = { testResult = null }) { Text("Done") } },
        )
    }

    testError?.let { error ->
        AlertDialog(
            onDismissRequest = { testError = null },
            title = { Text("Could Not Test Face Setup") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { testError = null }) { Text("OK") } },
        )
    }
'''
marker = '\n    if (deleteConfirmationOpen && onDelete != null) {'
s = replace_once(s, marker, insert + marker, "face test result dialogs")
write(rel, s)


# -----------------------------------------------------------------------------
# Physical Face Setup hardening: stronger relative pose gates + human-paced capture
# -----------------------------------------------------------------------------
rel = "app/src/main/java/com/snaploop/app/ui/GuidedFacePoseTracker.kt"
s = read(rel)
s = replace_once(s, 'stableFramesRequired: Int = 10,', 'stableFramesRequired: Int = 18,', "longer pose dwell")
s = replace_once(s, 'yaw in 20f..42f', 'yaw in 28f..48f', "left yaw threshold")
s = replace_once(s, 'yaw in -42f..-20f', 'yaw in -48f..-28f', "right yaw threshold")
s = replace_once(s, 'pitch in -32f..-14f', 'pitch in -38f..-18f', "tilt threshold")
write(rel, s)

rel = "app/src/main/java/com/snaploop/app/ui/GuidedFaceEnrollmentCamera.kt"
s = read(rel)
s = replace_once(s, 'if (now - lastAutoCaptureAt < 550L)', 'if (now - lastAutoCaptureAt < 1_400L)', "face capture cooldown")
write(rel, s)

rel = "app/src/test/java/com/snaploop/app/ui/GuidedFacePoseTrackerTest.kt"
s = read(rel)
s = s.replace('observation(yaw = 23f)', 'observation(yaw = 32f)')
s = s.replace('observation(yaw = 36f)', 'observation(yaw = 42f)')
s = s.replace('observation(yaw = 24f)', 'observation(yaw = 32f)')
s = s.replace('observation(yaw = 22f)', 'observation(yaw = 32f)')
s = s.replace('observation(yaw = -24f)', 'observation(yaw = -32f)')
s = s.replace('observation(pitch = -27f)', 'observation(pitch = -31f)')
write(rel, s)


# -----------------------------------------------------------------------------
# Dashboard subtitle clipping, viewer count position, canonical Gallery/You theme
# -----------------------------------------------------------------------------
rel = "app/src/main/java/com/snaploop/app/ui/ParityEventDashboard.kt"
s = read(rel)
s = replace_once(s, 'Modifier.fillMaxSize().padding(16.dp),\n                verticalArrangement = Arrangement.spacedBy(7.dp),', 'Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),\n                verticalArrangement = Arrangement.spacedBy(3.dp),', "feature tile compact content")
s = replace_once(s, 'Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)\n                Text(subtitle, color = Color.White.copy(alpha = 0.90f), fontSize = 12.sp)', 'Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)\n                Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 11.sp, maxLines = 1)', "feature tile text visibility")
write(rel, s)

rel = "app/src/main/java/com/snaploop/app/ui/ParityFullScreenPhotoViewer.kt"
s = read(rel)
old = '''                        // Preserve balanced top-bar geometry without exposing the Android-only
                        // overflow/Not-Me menu that is absent from the iOS viewer.
                        Spacer(Modifier.size(48.dp))
'''
new = '''                        // iOS places the count capsule at the trailing edge rather than
                        // artificially centering it with a mirrored spacer.
'''
s = replace_once(s, old, new, "move viewer count trailing")
write(rel, s)

rel = "app/src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt"
s = read(rel)
s = replace_once(s, 'Column(modifier.fillMaxSize().padding(vertical = 8.dp)) {', 'Column(modifier.fillMaxSize().background(SnapGradients.SoftWash).padding(vertical = 8.dp)) {', "gallery canonical background")
old = '''                    Brush.linearGradient(
                        listOf(
                            Color(0xFFFF571A).copy(alpha = 0.14f),
                            Color(0xFFFF0070).copy(alpha = 0.12f),
                            Color(0xFF7D14FF).copy(alpha = 0.12f),
                        ),
                    ),'''
new = '''                    SnapGradients.SoftWash,'''
s = replace_once(s, old, new, "gallery canonical banner")
write(rel, s)

rel = "app/src/main/java/com/snaploop/app/ui/ParityYouScreen.kt"
s = read(rel)
s = replace_once(s, '.fillMaxSize()\n            .verticalScroll(rememberScrollState())', '.fillMaxSize()\n            .background(SnapGradients.SoftWash)\n            .verticalScroll(rememberScrollState())', "you canonical background")
write(rel, s)


# -----------------------------------------------------------------------------
# Replay onboarding: compact enough for the real Android viewport / bottom nav
# -----------------------------------------------------------------------------
rel = "app/src/main/java/com/snaploop/app/ui/ParityOnboardingScreen.kt"
s = read(rel)
s = replace_once(s, 'verticalArrangement = Arrangement.spacedBy(18.dp),', 'verticalArrangement = Arrangement.spacedBy(10.dp),', "onboarding page spacing")
s = replace_once(s, 'fontSize = 22.sp,', 'fontSize = 19.sp,', "onboarding welcome size")
s = replace_once(s, 'fontSize = 30.sp,\n                fontWeight = FontWeight.Bold,', 'fontSize = 26.sp,\n                lineHeight = 29.sp,\n                fontWeight = FontWeight.Bold,', "onboarding title size")
s = replace_once(s, 'fontSize = 16.sp,\n                lineHeight = 24.sp,', 'fontSize = 14.sp,\n                lineHeight = 20.sp,', "onboarding body size")
s = replace_once(s, 'fontSize = 13.sp,\n                fontWeight = FontWeight.Medium,', 'fontSize = 12.sp,\n                lineHeight = 16.sp,\n                fontWeight = FontWeight.Medium,', "onboarding note size")
old = '''    Box(
        Modifier.size(205.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(190.dp).background(SnapGradients.SoftWash, CircleShape))
        when (kind) {
            OnboardingParityKind.FIND -> PhotoStackIllustration()
            OnboardingParityKind.FACE -> FaceSetupIllustration()
            OnboardingParityKind.TRIP -> TripFlowIllustration()
            OnboardingParityKind.RESULT -> ResultFlowIllustration()
            OnboardingParityKind.PRIVACY -> PrivacySummaryIllustration()
        }
    }
'''
new = '''    Box(
        Modifier.size(172.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(164.dp).background(SnapGradients.SoftWash, CircleShape))
        Box(
            Modifier.graphicsLayer {
                scaleX = 0.82f
                scaleY = 0.82f
            },
            contentAlignment = Alignment.Center,
        ) {
            when (kind) {
                OnboardingParityKind.FIND -> PhotoStackIllustration()
                OnboardingParityKind.FACE -> FaceSetupIllustration()
                OnboardingParityKind.TRIP -> TripFlowIllustration()
                OnboardingParityKind.RESULT -> ResultFlowIllustration()
                OnboardingParityKind.PRIVACY -> PrivacySummaryIllustration()
            }
        }
    }
'''
s = replace_once(s, old, new, "onboarding illustration scale")
s = replace_once(s, 'verticalArrangement = Arrangement.spacedBy(12.dp),', 'verticalArrangement = Arrangement.spacedBy(8.dp),', "onboarding footer spacing")
s = replace_once(s, 'modifier = Modifier.height(32.dp),', 'modifier = Modifier.height(28.dp),', "onboarding back height")
s = replace_once(s, 'Spacer(Modifier.height(32.dp))', 'Spacer(Modifier.height(28.dp))', "onboarding first footer spacer")
write(rel, s)


# -----------------------------------------------------------------------------
# Restore screen must use current SnapLoop asset, not legacy S glyph
# -----------------------------------------------------------------------------
rel = "app/src/main/java/com/snaploop/app/ui/SnapLoopApp.kt"
s = read(rel)
s = replace_once(s, '            BrandMark(78)\n            if (!canRetry)', '            ParityBrandMark(78)\n            if (!canRetry)', "restore current brand mark")
write(rel, s)


# -----------------------------------------------------------------------------
# Scanner checkpoint batching: avoid multiple encrypted full-state writes per photo
# -----------------------------------------------------------------------------
rel = "app/src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt"
s = read(rel)
old = '''                    // Persist expensive on-device extraction before any network publication.
                    // A transient upload failure can then retry from cached faces instead of
                    // decoding and embedding the same source photo again.
                    states.save(state)
'''
new = '''                    // Keep the extracted corpus in memory for this pass. The encrypted scan
                    // checkpoint is batched below so first-time scans do not serialize the entire
                    // growing state twice for every photo.
'''
s = replace_once(s, old, new, "remove duplicate per-photo state save")
old = '''                completed++
                state.lastSyncedAtMillis = System.currentTimeMillis()
                states.save(state)
'''
new = '''                completed++
                state.lastSyncedAtMillis = System.currentTimeMillis()
                // Bound crash rework while avoiding an encrypted full-state write after every
                // asset. The final checkpoint below is unconditional.
                if (completed % 4 == 0) states.save(state)
'''
s = replace_once(s, old, new, "batch scan checkpoints")
write(rel, s)


# -----------------------------------------------------------------------------
# Regression contract for this physical-device batch
# -----------------------------------------------------------------------------
test_rel = "app/src/test/java/com/snaploop/app/ui/DeviceAcceptanceBatchRegressionTest.kt"
test = r'''package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAcceptanceBatchRegressionTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$path not found")
    }

    @Test fun `face setup requires active biometric consent at every sensitive entry point`() {
        val source = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        assertTrue(source.contains("if (!ensureActiveBiometricConsent(uid)) return@launchBusy"))
        assertTrue(source.contains("private suspend fun ensureActiveBiometricConsent"))
        assertTrue(source.contains("gate = AppGate.BIOMETRIC_CONSENT"))
    }

    @Test fun `test my face uses gallery picker and completion shows explicit progress`() {
        val source = source("src/main/java/com/snaploop/app/ui/ParityFaceSetupScreen.kt")
        assertTrue(source.contains("ActivityResultContracts.GetContent()"))
        assertFalse(source.contains("ParityFaceTestCamera("))
        assertTrue(source.contains("Finishing Face Setup…"))
        assertTrue(source.contains("CircularProgressIndicator"))
    }

    @Test fun `own match preference is visible before corpus replay finishes`() {
        val source = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        assertTrue(source.contains("includeOwnMatches = resolvedEnabled"))
        assertTrue(source.contains("val shouldReplay = OwnMatchReplayPolicy.shouldReplay"))
        assertTrue(source.contains("viewModelScope.launch {\n            if (shouldReplay)"))
    }

    @Test fun `scanner batches encrypted checkpoints`() {
        val source = source("src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt")
        assertTrue(source.contains("if (completed % 4 == 0) states.save(state)"))
    }

    @Test fun `restore uses current brand mark and viewer count is trailing`() {
        val app = source("src/main/java/com/snaploop/app/ui/SnapLoopApp.kt")
        val viewer = source("src/main/java/com/snaploop/app/ui/ParityFullScreenPhotoViewer.kt")
        assertTrue(app.contains("ParityBrandMark(78)"))
        assertFalse(viewer.contains("Spacer(Modifier.size(48.dp))"))
    }

    @Test fun `gallery and you use canonical SnapLoop wash`() {
        assertTrue(source("src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt").contains("background(SnapGradients.SoftWash)"))
        assertTrue(source("src/main/java/com/snaploop/app/ui/ParityYouScreen.kt").contains("background(SnapGradients.SoftWash)"))
    }
}
'''
write(test_rel, test)

print("Device acceptance batch changes applied")
