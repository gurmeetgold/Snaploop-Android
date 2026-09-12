from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def replace_once(path: Path, old: str, new: str):
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"Expected text not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))

# 1) Face Setup: force a neutral transition between captured poses so a fresh onboarding
# stream cannot consume stale/rapid frames and auto-complete LEFT/RIGHT/DOWN in one go.
pose = ROOT / "app/src/main/java/com/snaploop/app/ui/GuidedFacePoseTracker.kt"
replace_once(
    pose,
    "    private var stableFrameCount = 0\n    private var lastQualified: FacePoseObservation? = null\n",
    "    private var stableFrameCount = 0\n    private var lastQualified: FacePoseObservation? = null\n    private var transitionGateRequired = false\n",
)
replace_once(
    pose,
    "        stableFrameCount = 0\n        lastQualified = null\n    }\n\n    fun onCaptured() {\n        activePose = null\n        stableFrameCount = 0\n        lastQualified = null\n    }\n",
    "        stableFrameCount = 0\n        lastQualified = null\n        transitionGateRequired = false\n    }\n\n    fun onCaptured() {\n        activePose = null\n        stableFrameCount = 0\n        lastQualified = null\n        transitionGateRequired = true\n    }\n",
)
replace_once(
    pose,
    "        val yaw = observation.yawDegrees - neutralYaw!!\n        val pitch = observation.pitchDegrees - neutralPitch!!\n        val qualifies = when (pose) {\n",
    "        val yaw = observation.yawDegrees - neutralYaw!!\n        val pitch = observation.pitchDegrees - neutralPitch!!\n\n        if (transitionGateRequired) {\n            val backAtNeutral = abs(yaw) <= 12f && abs(pitch) <= 12f\n            resetStability()\n            if (backAtNeutral) {\n                transitionGateRequired = false\n                return Decision(\n                    readyToCapture = false,\n                    instruction = instruction(pose),\n                    detail = \"Now follow the next pose\",\n                    calibrated = true,\n                )\n            }\n            return Decision(\n                readyToCapture = false,\n                instruction = instruction(pose),\n                detail = \"Return to center briefly before the next pose\",\n                calibrated = true,\n            )\n        }\n\n        val qualifies = when (pose) {\n",
)

# 2) My Photos + Gallery: actual pull-to-refresh, not only the toolbar refresh icon.
gallery = ROOT / "app/src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt"
replace_once(
    gallery,
    "import androidx.compose.material3.AlertDialog\n",
    "import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.ExperimentalMaterial3Api\nimport androidx.compose.material3.pulltorefresh.PullToRefreshBox\n",
)
replace_once(
    gallery,
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n",
)
replace_once(
    gallery,
    "@Suppress(\"UNUSED_PARAMETER\")\n@Composable\ninternal fun ParityPhotoGallery(\n",
    "@Suppress(\"UNUSED_PARAMETER\")\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\ninternal fun ParityPhotoGallery(\n",
)
replace_once(
    gallery,
    "    var pendingLegacySave by remember { mutableStateOf<List<PhotoMatch>?>(null) }\n\n    fun setFavorite",
    "    var pendingLegacySave by remember { mutableStateOf<List<PhotoMatch>?>(null) }\n    var refreshing by remember { mutableStateOf(false) }\n\n    LaunchedEffect(photos) {\n        refreshing = false\n    }\n\n    fun requestRefresh() {\n        correctionError = null\n        refreshing = true\n        onRefresh()\n    }\n\n    fun setFavorite",
)
replace_once(
    gallery,
    "    Column(modifier.fillMaxSize().background(SnapGradients.SoftWash).statusBarsPadding().padding(vertical = 8.dp)) {\n",
    "    PullToRefreshBox(\n        isRefreshing = refreshing,\n        onRefresh = ::requestRefresh,\n        modifier = modifier.fillMaxSize().background(SnapGradients.SoftWash),\n    ) {\n        Column(Modifier.fillMaxSize().statusBarsPadding().padding(vertical = 8.dp)) {\n",
)
replace_once(
    gallery,
    "            IconButton(onClick = {\n                correctionError = null\n                onRefresh()\n            }) {\n",
    "            IconButton(onClick = ::requestRefresh) {\n",
)
replace_once(
    gallery,
    "    }\n\n    detail?.let { match ->\n",
    "        }\n    }\n\n    detail?.let { match ->\n",
)

# 3) Event edit: do not reject saves because server-only metadata refreshed updatedAt.
# Treat the form as stale only when user-editable Event fields actually changed.
concurrency = ROOT / "app/src/main/java/com/snaploop/app/ui/EventEditConcurrencyPolicy.kt"
concurrency.write_text('''package com.snaploop.app.ui\n\nimport com.snaploop.app.model.SnapEvent\n\n/** Prevents a stale Edit Event form from overwriting a newer user-editable Event revision. */\ninternal object EventEditConcurrencyPolicy {\n    fun isFresh(opened: SnapEvent, latest: SnapEvent): Boolean =\n        opened.id == latest.id &&\n            opened.name == latest.name &&\n            opened.category == latest.category &&\n            opened.coverImagePath == latest.coverImagePath &&\n            opened.locationName == latest.locationName &&\n            opened.startsAt == latest.startsAt &&\n            opened.endsAt == latest.endsAt &&\n            opened.photoWindowVersion == latest.photoWindowVersion &&\n            opened.photoWindowTimeZoneId == latest.photoWindowTimeZoneId &&\n            opened.photoWindowStartDayNumber == latest.photoWindowStartDayNumber &&\n            opened.photoWindowEndDayNumber == latest.photoWindowEndDayNumber &&\n            opened.status == latest.status\n\n    const val STALE_MESSAGE =\n        "This Event changed while you were editing it. Close Edit Event and reopen it to load the latest version before saving."\n}\n''')

# 4) Scan performance: reduce recognition decode size and encrypted-state checkpoint churn.
scanner = ROOT / "app/src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt"
replace_once(scanner, "if (completed % 4 == 0) states.save(state)", "if (completed % 12 == 0) states.save(state)")
replace_once(scanner, "const val ANALYSIS_MAX_PIXEL_SIZE = 1024", "const val ANALYSIS_MAX_PIXEL_SIZE = 768")
replace_once(
    scanner,
    "// 1280px preserves ample pixels for the minimum supported face fraction while cutting\n        // decode, EXIF rotation, JPEG encode and ML Kit work substantially on mid-range phones.\n        const val ANALYSIS_MAX_PIXEL_SIZE = 768",
    "// 768px keeps recognition input comfortably above the minimum face gate while reducing\n        // MediaStore decode/rotation and ML Kit work on mid-range Android devices.\n        const val ANALYSIS_MAX_PIXEL_SIZE = 768",
)

# 5) New Play internal build.
build = ROOT / "app/build.gradle.kts"
replace_once(build, "versionCode = 5\n        versionName = \"1.0.4\"", "versionCode = 6\n        versionName = \"1.0.5\"")

# Behavioral regression for the onboarding pose-transition bug.
test = ROOT / "app/src/test/java/com/snaploop/app/ui/GuidedFacePoseTransitionTest.kt"
test.write_text('''package com.snaploop.app.ui\n\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass GuidedFacePoseTransitionTest {\n    private fun observation(yaw: Float = 0f, pitch: Float = 0f) = FacePoseObservation(\n        yawDegrees = yaw,\n        pitchDegrees = pitch,\n        rollDegrees = 0f,\n        centerXFraction = 0.5f,\n        centerYFraction = 0.5f,\n        widthFraction = 0.4f,\n        heightFraction = 0.4f,\n    )\n\n    @Test\n    fun capturedPoseRequiresNeutralTransitionBeforeNextPoseCanQualify() {\n        val tracker = GuidedFacePoseTracker(calibrationSamplesRequired = 2, stableFramesRequired = 2)\n        assertFalse(tracker.evaluate(GuidedFacePose.FRONT, observation()).readyToCapture)\n        assertFalse(tracker.evaluate(GuidedFacePose.FRONT, observation()).readyToCapture)\n        assertTrue(tracker.evaluate(GuidedFacePose.FRONT, observation()).readyToCapture)\n\n        tracker.onCaptured()\n\n        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 30f)).readyToCapture)\n        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation()).readyToCapture)\n        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 30f)).readyToCapture)\n        assertTrue(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 30f)).readyToCapture)\n    }\n}\n''')

# Source-level guard for the release stabilization wiring.
source_test = ROOT / "app/src/test/java/com/snaploop/app/ui/InternalStabilizationRegressionTest.kt"
source_test.write_text('''package com.snaploop.app.ui\n\nimport java.io.File\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass InternalStabilizationRegressionTest {\n    @Test\n    fun galleryHasPullToRefreshAndScannerUsesFastBoundedInput() {\n        val gallery = File("src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt").readText()\n        val scanner = File("src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt").readText()\n        assertTrue(gallery.contains("PullToRefreshBox"))\n        assertTrue(gallery.contains("onRefresh = ::requestRefresh"))\n        assertTrue(scanner.contains("ANALYSIS_MAX_PIXEL_SIZE = 768"))\n        assertTrue(scanner.contains("completed % 12 == 0"))\n    }\n}\n''')

print("Applied SnapLoop Android internal stabilization patch for 1.0.5 (6).")
