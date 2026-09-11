from pathlib import Path

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text()

def write(path, text):
    (ROOT / path).write_text(text)

def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# 1) Scan performance: scanner-only FAST face detector + low-cost analysis JPEG.
#    Preserve high-quality publication thumbnails only for actual positive matches.
# -----------------------------------------------------------------------------
rel = 'app/src/main/java/com/snaploop/app/face/FaceAligner.kt'
s = read(rel)
old = '''internal class MlKitFaceAligner(
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.04f)
            .build()
    ),
) : AutoCloseable {'''
new = '''internal class MlKitFaceAligner(
    fastDetection: Boolean = false,
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(
                if (fastDetection) FaceDetectorOptions.PERFORMANCE_MODE_FAST
                else FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
            )
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.04f)
            .build()
    ),
) : AutoCloseable {'''
s = replace_once(s, old, new, 'scanner fast ML Kit mode')
write(rel, s)

rel = 'app/src/main/java/com/snaploop/app/face/AndroidFacePipeline.kt'
s = read(rel)
s = replace_once(
    s,
    'class AndroidFacePipeline(context: Context) : AutoCloseable {\n    private val aligner = MlKitFaceAligner()',
    'class AndroidFacePipeline(context: Context, fastDetection: Boolean = false) : AutoCloseable {\n    private val aligner = MlKitFaceAligner(fastDetection = fastDetection)',
    'pipeline fast detector opt-in',
)
write(rel, s)

rel = 'app/src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt'
s = read(rel)
s = replace_once(
    s,
    'private val faces = AndroidFacePipeline(appContext)',
    'private val faces = AndroidFacePipeline(appContext, fastDetection = true)',
    'scanner uses fast detector',
)
old = '''                // Keep the normalized bytes for this iteration so a newly-processed photo is not
                // read from MediaStore a second time just to publish its thumbnail.
                var sourceJpeg: ByteArray? = null
                var corpus = state.photoCorpus[asset.id]
                if (corpus == null) {
                    sourceJpeg = library.normalizedJpeg(
                        asset,
                        config.thumbnailMaxPixelSize,
                        (config.thumbnailJpegQuality * 100).toInt().coerceIn(1, 100),
                    )

                    ScanCancellationRegistry.ensureActive(cancellationToken)
                    val detected = faces.detectFaces(sourceJpeg)'''
new = '''                // Face analysis does not need the 2560px publication preview. Most Event photos
                // never match anybody, so analyze a much smaller normalized JPEG and only create
                // the high-quality publication thumbnail when a positive appearance must upload.
                // This mirrors the iOS strategy of keeping recognition input bounded independently
                // from the user-visible preview and removes the largest Android per-photo cost.
                var corpus = state.photoCorpus[asset.id]
                if (corpus == null) {
                    val analysisJpeg = library.normalizedJpeg(
                        asset,
                        ANALYSIS_MAX_PIXEL_SIZE,
                        ANALYSIS_JPEG_QUALITY,
                    )

                    ScanCancellationRegistry.ensureActive(cancellationToken)
                    val detected = faces.detectFaces(analysisJpeg)'''
s = replace_once(s, old, new, 'separate scan analysis jpeg')
old = '''                        val thumbnail = if (publishAppearances.isNotEmpty()) {
                            sourceJpeg ?: library.normalizedJpeg(
                                asset,
                                config.thumbnailMaxPixelSize,
                                (config.thumbnailJpegQuality * 100).toInt().coerceIn(1, 100),
                            )
                        } else {
                            byteArrayOf()
                        }'''
new = '''                        val thumbnail = if (publishAppearances.isNotEmpty()) {
                            library.normalizedJpeg(
                                asset,
                                config.thumbnailMaxPixelSize,
                                (config.thumbnailJpegQuality * 100).toInt().coerceIn(1, 100),
                            )
                        } else {
                            byteArrayOf()
                        }'''
s = replace_once(s, old, new, 'publication thumbnail only for matches')
# Add constants in companion or just before close if no companion exists.
marker = '''    override fun close() {
        faces.close()
    }
}'''
insert = '''    private companion object {
        // 1280px preserves ample pixels for the minimum supported face fraction while cutting
        // decode, EXIF rotation, JPEG encode and ML Kit work substantially on mid-range phones.
        const val ANALYSIS_MAX_PIXEL_SIZE = 1280
        const val ANALYSIS_JPEG_QUALITY = 84
    }

'''
s = replace_once(s, marker, insert + marker, 'scanner analysis constants')
write(rel, s)

# -----------------------------------------------------------------------------
# 2) Scan terminal semantics: one bad/stale asset must not look like user stopped scan.
# -----------------------------------------------------------------------------
rel = 'app/src/main/java/com/snaploop/app/ui/ScanResultPresentationPolicy.kt'
s = read(rel)
old = '''    fun kind(remaining: Int, failed: Int): Kind = when {
        failed > 0 -> Kind.RETRYABLE_FAILURE
        remaining > 0 -> Kind.DEFERRED_BATCH
        else -> Kind.COMPLETE
    }'''
new = '''    fun kind(remaining: Int, failed: Int, checked: Int): Kind = when {
        // “Scan stopped” is reserved for a pass that could not process a single asset. A stale
        // MediaStore row or one transient publication failure must not tell the user they stopped
        // a scan that actually made progress; the pending item simply remains retryable.
        failed > 0 && checked == 0 -> Kind.RETRYABLE_FAILURE
        remaining > 0 -> Kind.DEFERRED_BATCH
        else -> Kind.COMPLETE
    }'''
s = replace_once(s, old, new, 'partial scan failure presentation')
write(rel, s)

rel = 'app/src/main/java/com/snaploop/app/ui/ParityEventScanScreen.kt'
s = read(rel)
s = replace_once(
    s,
    'val presentation = ScanResultPresentationPolicy.kind(result.remaining, result.failed)',
    'val presentation = ScanResultPresentationPolicy.kind(result.remaining, result.failed, result.checked)',
    'scan presentation checked count',
)
# Center the active card vertically below header.
old = '''        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),'''
new = '''        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SnapGradients.ScanSurface)
                    .padding(horizontal = 20.dp, vertical = 22.dp),'''
s = replace_once(s, old, new, 'center and tint scan card')
# Close the new Box after Card. Match end before Column/func close using known tail.
# Last card close is followed by column/root closes; add one extra closing brace immediately after Card.
old_tail = '''            }
        }
    }
}

@Composable
private fun ScanCircleIcon'''
new_tail = '''            }
        }
        }
    }
}

@Composable
private fun ScanCircleIcon'''
s = replace_once(s, old_tail, new_tail, 'close centered scan box')
write(rel, s)

# Stronger canonical pink/lilac surface used by scan and insight banners.
rel = 'app/src/main/java/com/snaploop/app/ui/SnapLoopTheme.kt'
s = read(rel)
marker = '''    val SoftWash = Brush.linearGradient(
        listOf(
            Color(0xFFFFF2F7).copy(alpha = 0.76f),
            SnapColors.CoralSoft.copy(alpha = 0.54f),
            SnapColors.LilacSoft.copy(alpha = 0.52f),
            SnapColors.BlueSoft.copy(alpha = 0.42f),
        ),
    )
'''
addition = '''    val SoftWash = Brush.linearGradient(
        listOf(
            Color(0xFFFFF2F7).copy(alpha = 0.76f),
            SnapColors.CoralSoft.copy(alpha = 0.54f),
            SnapColors.LilacSoft.copy(alpha = 0.52f),
            SnapColors.BlueSoft.copy(alpha = 0.42f),
        ),
    )

    // iOS insight/scan cards carry a visible warm-pink wash rather than a white Material surface.
    val ScanSurface = Brush.linearGradient(
        listOf(
            Color(0xFFFFE6EE),
            Color(0xFFFFEEF5),
            Color(0xFFF4E8FF),
        ),
    )

    val Insight = Brush.linearGradient(
        listOf(
            Color(0xFFFFE1EB),
            Color(0xFFFFEAF2),
            Color(0xFFF0E2FF),
        ),
    )
'''
s = replace_once(s, marker, addition, 'pink parity gradients')
write(rel, s)

rel = 'app/src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt'
s = read(rel)
s = replace_once(
    s,
    '''                .background(
                    SnapGradients.SoftWash,
                )''',
    '''                .background(
                    SnapGradients.Insight,
                )''',
    'gallery insight pink tone',
)
write(rel, s)

# -----------------------------------------------------------------------------
# 3) My Photos consistency: one state source + background refresh on page entry.
# -----------------------------------------------------------------------------
rel = 'app/src/main/java/com/snaploop/app/ui/ParityEventDashboard.kt'
s = read(rel)
# Remove independent backend count producer which can race ahead of state.photos.
start = '''    val photosOfMe by produceState(
        initialValue = state.photos.size,
        key1 = event.id,
        key2 = uid,
        key3 = Pair(me?.sharingEnabled, state.photos),
    ) {
        if (uid == null) {
            value = 0
            return@produceState
        }
        value = runCatching {
            val matches = FirebaseMatchRepository().myPhotos(event.id, uid)
            EventDashboardParityPolicy.photosOfMeCount(
                ownerUserIds = matches.map { it.ownerUserId },
                userId = uid,
                sharingEnabled = me?.sharingEnabled == true,
            )
        }.getOrDefault(state.photos.size)
    }
'''
s = replace_once(s, start, '    val photosOfMe = state.photos.size\n', 'dashboard photo count single source')
s = s.replace('import androidx.compose.runtime.produceState\n', '')
s = s.replace('import com.snaploop.app.data.FirebaseMatchRepository\n', '')
write(rel, s)

rel = 'app/src/main/java/com/snaploop/app/ui/AppCoordinator.kt'
s = read(rel)
# Robust You return for cancel.
old = '''        if (state.value.faceSetupMode == FaceSetupMode.RETURN_TO_MAIN) {
            update { copy(gate = AppGate.MAIN, faceCaptures = 0, message = null) }
        } else {'''
new = '''        if (state.value.faceSetupMode == FaceSetupMode.RETURN_TO_MAIN) {
            update {
                copy(
                    gate = AppGate.MAIN,
                    faceCaptures = 0,
                    returnToYouAfterFaceSetup = true,
                    message = null,
                )
            }
        } else {'''
s = replace_once(s, old, new, 'Face Setup Back returns You')
# Arm the destination before the MAIN route exists so MainShell sees it on first composition.
old = '''        users.syncMyProfile(uid, state.value.user?.displayName)
        routeAuthenticated(uid)
        if (returnToYou) {
            update { copy(returnToYouAfterFaceSetup = true) }
        }'''
new = '''        users.syncMyProfile(uid, state.value.user?.displayName)
        if (returnToYou) {
            update { copy(returnToYouAfterFaceSetup = true) }
        }
        routeAuthenticated(uid)'''
s = replace_once(s, old, new, 'Face Setup completion arms You before MAIN')
# Non-blocking photo refresh for page-entry convergence.
marker = '''    fun refreshPhotos() = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        refreshPhotosInternal(event.id, uid)
    }
'''
addition = marker + '''
    /** Refresh Event My Photos without a global busy overlay when the gallery becomes visible. */
    fun refreshSelectedEventPhotosInBackground() {
        val uid = auth.currentUserId ?: return
        val eventId = state.value.selectedEvent?.id ?: return
        viewModelScope.launch {
            runCatching { refreshPhotosInternal(eventId, uid) }
        }
    }
'''
s = replace_once(s, marker, addition, 'background My Photos refresh')
write(rel, s)

rel = 'app/src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt'
s = read(rel)
marker = '''    BackHandler {
        when (page) {
            ShellEventPage.DASHBOARD -> coordinator.closeEvent()
            ShellEventPage.PHONE_INVITE -> page = ShellEventPage.INVITE
            else -> page = ShellEventPage.DASHBOARD
        }
    }

    when (page) {'''
addition = '''    BackHandler {
        when (page) {
            ShellEventPage.DASHBOARD -> coordinator.closeEvent()
            ShellEventPage.PHONE_INVITE -> page = ShellEventPage.INVITE
            else -> page = ShellEventPage.DASHBOARD
        }
    }

    LaunchedEffect(page, event.id) {
        if (page == ShellEventPage.PHOTOS) {
            coordinator.refreshSelectedEventPhotosInBackground()
        }
    }

    when (page) {'''
s = replace_once(s, marker, addition, 'refresh My Photos on entry')
write(rel, s)

# -----------------------------------------------------------------------------
# 4) Thumbnail arrival: remember when direct Storage is forbidden so every grid cell does not
#    pay the same failing network round trip before the authorized callable fallback.
# -----------------------------------------------------------------------------
rel = 'app/src/main/java/com/snaploop/app/ui/MatchedThumbnailLoader.kt'
s = read(rel)
s = replace_once(s, 'import com.google.firebase.storage.FirebaseStorage\n', 'import com.google.firebase.storage.FirebaseStorage\nimport com.google.firebase.storage.StorageException\n', 'StorageException import')
old = '''    private suspend fun fetchBytes(path: String): ByteArray {
        return try {
            // Published previews are already bounded by SnapLoop's thumbnail policy. Keep this cap
            // larger than iOS's current maximum to tolerate older rows during migration.
            storage.reference.child(path).getBytes(12L * 1024L * 1024L).await()
        } catch (_: Throwable) {
            authorizedFallback(path)
        }
    }'''
new = '''    private suspend fun fetchBytes(path: String): ByteArray {
        if (directStorageReadable == false) return authorizedFallback(path)
        return try {
            // Published previews are already bounded by SnapLoop's thumbnail policy. Keep this cap
            // larger than iOS's current maximum to tolerate older rows during migration.
            storage.reference.child(path).getBytes(12L * 1024L * 1024L).await().also {
                directStorageReadable = true
            }
        } catch (error: Throwable) {
            // Production recipient rules can intentionally reject direct object reads. Remember
            // that authorization result process-wide so the other Gallery cells go straight to the
            // authorized callable instead of each waiting for an identical failing Storage request.
            if ((error as? StorageException)?.errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
                directStorageReadable = false
            }
            authorizedFallback(path)
        }
    }'''
s = replace_once(s, old, new, 'thumbnail authorization cache')
# add shared flag before class close, targeting cacheFile section's close is hard; add after cacheDir field.
s = replace_once(
    s,
    '    private val cacheDir = File(appContext.cacheDir, "matched-thumbnails").apply { mkdirs() }\n',
    '    private val cacheDir = File(appContext.cacheDir, "matched-thumbnails").apply { mkdirs() }\n\n    private companion object {\n        @Volatile var directStorageReadable: Boolean? = null\n    }\n',
    'shared thumbnail direct-read capability',
)
write(rel, s)

# -----------------------------------------------------------------------------
# 5) Onboarding: remove narrow 172dp clipping viewport, compact/center content, and give Back
#    a valid touch height + navigation inset. This fixes split Join/privacy chips and clipped Back.
# -----------------------------------------------------------------------------
rel = 'app/src/main/java/com/snaploop/app/ui/ParityOnboardingScreen.kt'
s = read(rel)
s = replace_once(s, 'import androidx.compose.foundation.layout.offset\n', 'import androidx.compose.foundation.layout.offset\nimport androidx.compose.foundation.layout.navigationBarsPadding\n', 'onboarding nav inset import')
s = replace_once(
    s,
    '''                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),''',
    '''                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
                    .navigationBarsPadding(),''',
    'onboarding footer navigation inset',
)
s = replace_once(s, 'modifier = Modifier.height(28.dp),', 'modifier = Modifier.height(44.dp),', 'Back valid height')
s = replace_once(s, 'Spacer(Modifier.height(28.dp))', 'Spacer(Modifier.height(44.dp))', 'first page footer balance')
old = '''private fun OnboardingParityIllustration(kind: OnboardingParityKind) {
    Box(
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
}'''
new = '''private fun OnboardingParityIllustration(kind: OnboardingParityKind) {
    Box(
        Modifier.fillMaxWidth().height(164.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(154.dp).background(SnapGradients.SoftWash, CircleShape))
        // The illustration gets the page width, not a 172dp clipping viewport. Multi-column
        // illustrations such as Create/Join and privacy chips therefore keep whole words intact.
        Box(contentAlignment = Alignment.Center) {
            when (kind) {
                OnboardingParityKind.FIND -> PhotoStackIllustration()
                OnboardingParityKind.FACE -> FaceSetupIllustration()
                OnboardingParityKind.TRIP -> TripFlowIllustration()
                OnboardingParityKind.RESULT -> ResultFlowIllustration()
                OnboardingParityKind.PRIVACY -> PrivacySummaryIllustration()
            }
        }
    }
}'''
s = replace_once(s, old, new, 'onboarding unclipped illustration viewport')
# Slightly compact illustration primitives so 1.4 accessibility text still has room.
s = replace_once(s, 'FeatureBubble(Icons.Filled.AddCircle, "Create")\n        Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = SnapColors.Coral, modifier = Modifier.size(30.dp))\n        FeatureBubble(Icons.Filled.PersonAdd, "Join")', 'FeatureBubble(Icons.Filled.AddCircle, "Create")\n        Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = SnapColors.Coral, modifier = Modifier.size(26.dp))\n        FeatureBubble(Icons.Filled.PersonAdd, "Join")', 'trip flow compact arrow')
s = replace_once(s, '.size(width = 98.dp, height = 104.dp)', '.size(width = 92.dp, height = 96.dp)', 'onboarding feature bubble compact')
s = replace_once(s, 'Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 9.dp))', 'Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 7.dp))', 'feature label single line')
s = replace_once(s, 'Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))', 'Text(label, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))', 'result label compact')
s = replace_once(s, 'Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SnapColors.Ink)', 'Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SnapColors.Ink, maxLines = 1)', 'privacy chip no wrapping')
write(rel, s)

# -----------------------------------------------------------------------------
# Tests: update policy tests and add source-contract regression coverage for this whole batch.
# -----------------------------------------------------------------------------
rel = 'app/src/test/java/com/snaploop/app/ui/ScanResultPresentationPolicyTest.kt'
s = read(rel)
s = replace_once(
    s,
    '''    @Test
    fun `retryable failures are not presented as a deferred clean batch`() {
        assertEquals(
            ScanResultPresentationPolicy.Kind.RETRYABLE_FAILURE,
            ScanResultPresentationPolicy.kind(remaining = 3, failed = 1),
        )
    }''',
    '''    @Test
    fun `a pass that makes no progress and fails stays retryable`() {
        assertEquals(
            ScanResultPresentationPolicy.Kind.RETRYABLE_FAILURE,
            ScanResultPresentationPolicy.kind(remaining = 3, failed = 1, checked = 0),
        )
    }

    @Test
    fun `a partial asset failure after progress is deferred instead of falsely stopped`() {
        assertEquals(
            ScanResultPresentationPolicy.Kind.DEFERRED_BATCH,
            ScanResultPresentationPolicy.kind(remaining = 1, failed = 1, checked = 24),
        )
    }''',
    'scan result failure tests',
)
s = s.replace('ScanResultPresentationPolicy.kind(remaining = 20, failed = 0)', 'ScanResultPresentationPolicy.kind(remaining = 20, failed = 0, checked = 80)')
s = s.replace('ScanResultPresentationPolicy.kind(remaining = 0, failed = 0)', 'ScanResultPresentationPolicy.kind(remaining = 0, failed = 0, checked = 25)')
write(rel, s)

new_test = r'''package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OvernightDeviceBatchRegressionTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$path not found")
    }

    @Test fun `scanner uses bounded fast analysis but preserves publication preview quality`() {
        val scanner = source("src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt")
        val pipeline = source("src/main/java/com/snaploop/app/face/AndroidFacePipeline.kt")
        assertTrue(scanner.contains("AndroidFacePipeline(appContext, fastDetection = true)"))
        assertTrue(scanner.contains("ANALYSIS_MAX_PIXEL_SIZE = 1280"))
        assertTrue(scanner.contains("ANALYSIS_JPEG_QUALITY = 84"))
        assertTrue(scanner.contains("config.thumbnailMaxPixelSize"))
        assertTrue(pipeline.contains("fastDetection: Boolean = false"))
    }

    @Test fun `scan and gallery insight cards use branded pink surfaces and scan is centered`() {
        val scan = source("src/main/java/com/snaploop/app/ui/ParityEventScanScreen.kt")
        val gallery = source("src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt")
        assertTrue(scan.contains("Modifier.fillMaxWidth().weight(1f)"))
        assertTrue(scan.contains("contentAlignment = Alignment.Center"))
        assertTrue(scan.contains("SnapGradients.ScanSurface"))
        assertFalse(scan.contains("containerColor = Color.White.copy(alpha = 0.98f)"))
        assertTrue(gallery.contains("SnapGradients.Insight"))
    }

    @Test fun `event photo count has one state source and gallery refreshes on entry`() {
        val dashboard = source("src/main/java/com/snaploop/app/ui/ParityEventDashboard.kt")
        val shell = source("src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt")
        val coordinator = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        assertTrue(dashboard.contains("val photosOfMe = state.photos.size"))
        assertFalse(dashboard.contains("FirebaseMatchRepository().myPhotos"))
        assertTrue(shell.contains("if (page == ShellEventPage.PHOTOS)"))
        assertTrue(shell.contains("coordinator.refreshSelectedEventPhotosInBackground()"))
        assertTrue(coordinator.contains("fun refreshSelectedEventPhotosInBackground()"))
    }

    @Test fun `update Face Setup completion and Back both arm You destination`() {
        val coordinator = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        val routeIndex = coordinator.indexOf("routeAuthenticated(uid)", coordinator.indexOf("fun completeFaceSetup"))
        val flagIndex = coordinator.indexOf("returnToYouAfterFaceSetup = true", coordinator.indexOf("fun completeFaceSetup"))
        assertTrue(flagIndex >= 0 && flagIndex < routeIndex)
        val cancel = coordinator.substring(coordinator.indexOf("fun cancelFaceSetup"), coordinator.indexOf("fun skipFaceSetup"))
        assertTrue(cancel.contains("returnToYouAfterFaceSetup = true"))
    }

    @Test fun `onboarding illustrations are not clipped into narrow square and Back respects navigation inset`() {
        val onboarding = source("src/main/java/com/snaploop/app/ui/ParityOnboardingScreen.kt")
        assertTrue(onboarding.contains("Modifier.fillMaxWidth().height(164.dp)"))
        assertFalse(onboarding.contains("Modifier.size(172.dp)"))
        assertTrue(onboarding.contains(".navigationBarsPadding()"))
        assertTrue(onboarding.contains("modifier = Modifier.height(44.dp)"))
    }

    @Test fun `thumbnail loader remembers direct storage authorization denial`() {
        val loader = source("src/main/java/com/snaploop/app/ui/MatchedThumbnailLoader.kt")
        assertTrue(loader.contains("directStorageReadable == false"))
        assertTrue(loader.contains("StorageException.ERROR_NOT_AUTHORIZED"))
        assertTrue(loader.contains("directStorageReadable = false"))
    }
}
'''
write('app/src/test/java/com/snaploop/app/ui/OvernightDeviceBatchRegressionTest.kt', new_test)

print('Overnight runtime/device batch changes applied')
