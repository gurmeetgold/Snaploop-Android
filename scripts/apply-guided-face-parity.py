from pathlib import Path

path = Path("app/src/main/java/com/snaploop/app/ui/SnapLoopApp.kt")
text = path.read_text()
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
if updated == text:
    raise SystemExit("Face Setup migration made no changes")
path.write_text(updated)
