from pathlib import Path

app_path = Path("app/src/main/java/com/snaploop/app/ui/SnapLoopApp.kt")
text = app_path.read_text()
start_marker = "@Composable\nprivate fun FaceSetupScreen("
end_marker = "\n@Composable\nprivate fun MainTabs"
start = text.index(start_marker)
end = text.index(end_marker, start)
replacement = '''@Composable
private fun FaceSetupScreen(
    captures: Int,
    onCapture: (ByteArray) -> Unit,
    onReset: () -> Unit,
    onComplete: () -> Unit,
) {
    GuidedFaceEnrollmentCamera(
        captures = captures,
        onCapture = onCapture,
        onReset = onReset,
        onComplete = onComplete,
    )
}
'''
updated = text[:start] + replacement + text[end:]
if updated != text:
    app_path.write_text(updated)

camera_path = Path("app/src/main/java/com/snaploop/app/ui/GuidedFaceEnrollmentCamera.kt")
camera = camera_path.read_text()
camera = camera.replace(
    '''                        onQualified = {\n                            val now = System.currentTimeMillis()\n                            if (!captureInFlight.compareAndSet(false, true)) return@GuidedFaceAnalyzer\n''',
    '''                        onQualified = capture@{\n                            val stepAtCapture = currentStepOrdinal.get()\n                            if (!captureInFlight.compareAndSet(false, true)) return@capture\n''',
)
camera = camera.replace(
    '''                                            if (currentStepOrdinal.get() == captures.coerceIn(0, GuidedEnrollmentStep.entries.lastIndex)) {\n''',
    '''                                            if (currentStepOrdinal.get() == stepAtCapture) {\n''',
)
camera_path.write_text(camera)
