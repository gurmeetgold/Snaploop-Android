from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}: found {count} for {old[:80]!r}")
    path.write_text(text.replace(old, new, 1))


shell = Path("app/src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt")
app = Path("app/src/main/java/com/snaploop/app/ui/SnapLoopApp.kt")

# Keep the scan page's own progress/status readable instead of covering it with a global spinner.
replace_once(
    shell,
    "    if (state.busy) {\n        Box(\n",
    "    if (state.busy && state.scanProgress == null) {\n        Box(\n",
)

# The Not Me workflow has been retired. Do not expose a dead/destructive correction action.
replace_once(
    shell,
    "            state.photos.forEach { match -> ShellMatchCard(match, coordinator::dismissPhoto) }",
    "            state.photos.forEach { match -> ShellMatchCard(match) }",
)
replace_once(
    shell,
    "                ShellMatchCard(match, coordinator::dismissPhoto, Modifier.padding(horizontal = 18.dp))",
    "                ShellMatchCard(match, Modifier.padding(horizontal = 18.dp))",
)
replace_once(
    shell,
    "private fun ShellMatchCard(match: PhotoMatch, onNotMe: (PhotoMatch) -> Unit, modifier: Modifier = Modifier) {",
    "private fun ShellMatchCard(match: PhotoMatch, modifier: Modifier = Modifier) {",
)
replace_once(
    shell,
    '''        TextButton(onClick = { onNotMe(match) }, modifier = Modifier.align(Alignment.End)) {\n            Text("Not Me", color = ShellColors.Coral)\n        }\n''',
    "",
)

# Show the encrypted local guided Face Setup reference on You, falling back to the initial only if
# an older install has no saved reference image.
replace_once(
    shell,
    '''                Box(Modifier.size(66.dp).background(shellSoftGradient(), CircleShape), contentAlignment = Alignment.Center) {\n                    Text((state.user?.displayName ?: "?").take(1).uppercase(), fontSize = 25.sp, fontWeight = FontWeight.Black, color = ShellColors.Lilac)\n                }''',
    '''                FaceReferenceThumbnail(\n                    userId = state.user?.id,\n                    fallbackInitial = state.user?.displayName ?: "?",\n                    modifier = Modifier.size(66.dp),\n                )''',
)

# Remove stale product copy that advertises the retired Not Me workflow during onboarding.
replace_once(
    app,
    '''        "You stay in control" to "Scanning is Event-scoped and time-bounded. You can mark a match Not Me, withdraw biometric consent, or delete your account.",''',
    '''        "You stay in control" to "Scanning is Event-scoped and time-bounded. You can withdraw biometric consent or delete your account at any time.",''',
)

print("Applied SnapLoop UI regression patch")
