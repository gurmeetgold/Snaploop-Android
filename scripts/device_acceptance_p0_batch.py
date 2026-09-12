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

def replace_between(text, start, end, replacement, label):
    i = text.find(start)
    if i < 0:
        raise RuntimeError(f"{label}: start marker missing")
    j = text.find(end, i)
    if j < 0:
        raise RuntimeError(f"{label}: end marker missing")
    return text[:i] + replacement + text[j:]

# 1) Global typography: preserve physical dp density and user font preference, but make the
# SnapLoop design system 10% more compact. This affects explicit sp values as well as Material text.
rel = 'app/src/main/java/com/snaploop/app/ui/SnapLoopTheme.kt'
s = read(rel)
s = replace_once(s, 'import androidx.compose.runtime.Composable\n', 'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.CompositionLocalProvider\n', 'theme composition import')
s = replace_once(s, 'import androidx.compose.ui.graphics.Color\n', 'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.unit.Density\n', 'theme density imports')
s = replace_once(
    s,
    '    MaterialTheme(colorScheme = colors, content = content)\n',
    '''    val systemDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = systemDensity.density,
            fontScale = systemDensity.fontScale * 0.90f,
        ),
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
''',
    'compact global typography',
)
write(rel, s)

# 2) Automatic foreground scan cadence: 30 minutes. Roster/event/preference changes still bypass
# this cooldown immediately, so a new member is not held behind the timer.
rel = 'app/src/main/java/com/snaploop/app/scanner/AutomaticScanPolicy.kt'
s = read(rel)
s = s.replace('normally no more than\n * once per Event per hour.', 'normally no more than\n * once per Event per 30 minutes.')
s = s.replace('otherwise persistent one-hour cooldown.', 'otherwise persistent 30-minute cooldown.')
s = replace_once(s, '    const val COOLDOWN_MILLIS: Long = 60L * 60L * 1000L\n', '    const val COOLDOWN_MILLIS: Long = 30L * 60L * 1000L\n', '30 minute cooldown')
write(rel, s)

rel = 'app/src/main/java/com/snaploop/app/scanner/AutomaticForegroundScanController.kt'
s = read(rel)
s = s.replace('a persistent one-hour per-Event cooldown', 'a persistent 30-minute per-Event cooldown')
write(rel, s)

# 3) Event date copy is intentionally compact on cards and Event hero: no year.
rel = 'app/src/main/java/com/snaploop/app/ui/EventDateRangeFormatter.kt'
s = read(rel)
start = 'internal object EventDateRangeFormatter {'
replacement = '''internal object EventDateRangeFormatter {
    fun format(start: LocalDate, end: LocalDate, locale: Locale = Locale.getDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
        if (start == end) return start.format(formatter)
        return "${start.format(formatter)} – ${end.format(formatter)}"
    }
}
'''
s = replace_between(s, start, '\n}', replacement.rstrip('\n'), 'compact event date formatter')
# replace_between ends at first closing brace (wrong for nested old body) is unsafe; rebuild file cleanly.
s = '''package com.snaploop.app.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Compact Event date-range copy. Event cards intentionally omit the year. */
internal object EventDateRangeFormatter {
    fun format(start: LocalDate, end: LocalDate, locale: Locale = Locale.getDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
        if (start == end) return start.format(formatter)
        return "${start.format(formatter)} – ${end.format(formatter)}"
    }
}
'''
write(rel, s)

# 4) Gallery: three columns by default; stronger pink insight surface; safe-area-aware header and
# a consistent visible Back label.
rel = 'app/src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt'
s = read(rel)
s = replace_once(s, 'import androidx.compose.foundation.layout.size\n', 'import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.statusBarsPadding\n', 'gallery status bar import')
s = replace_once(s, '    var columns by rememberSaveable(title) { mutableIntStateOf(2) }\n', '    var columns by rememberSaveable(title) { mutableIntStateOf(3) }\n', 'gallery default three columns')
s = replace_once(s, '    Column(modifier.fillMaxSize().background(SnapGradients.SoftWash).padding(vertical = 8.dp)) {\n', '    Column(modifier.fillMaxSize().background(SnapGradients.SoftWash).statusBarsPadding().padding(vertical = 8.dp)) {\n', 'gallery safe area')
s = replace_once(
    s,
    '''            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Text("‹", fontSize = 34.sp, fontWeight = FontWeight.Light)
                }
            } else {
                Box(Modifier.size(48.dp))
            }
''',
    '''            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text("‹ Back", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Box(Modifier.size(64.dp))
            }
''',
    'gallery consistent back',
)
s = replace_once(
    s,
    '    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {\n',
    '''    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
''',
    'gallery transparent insight card',
)
s = replace_once(s, '            Text(count.toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold)\n', '            Text(count.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold)\n', 'gallery count compact')
write(rel, s)

# 5) QR scanner: transparent framing guide, never a Material white card.
rel = 'app/src/main/java/com/snaploop/app/ui/QrCodeTools.kt'
s = read(rel)
s = replace_once(s, 'import androidx.compose.foundation.background\n', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.border\n', 'qr border import')
s = replace_once(
    s,
    '''        Card(
            modifier = Modifier.align(Alignment.Center).size(270.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)))
        }
''',
    '''        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(270.dp)
                .border(2.dp, Color.White.copy(alpha = 0.78f), RoundedCornerShape(28.dp)),
        )
''',
    'qr transparent scan frame',
)
write(rel, s)

# 6) Onboarding top branding respects the status bar and gets a little breathing room.
rel = 'app/src/main/java/com/snaploop/app/ui/ParityOnboardingScreen.kt'
s = read(rel)
s = replace_once(s, 'import androidx.compose.foundation.layout.size\n', 'import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.statusBarsPadding\n', 'onboarding safe area import')
s = replace_once(
    s,
    '                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),\n',
    '                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 10.dp),\n',
    'onboarding header safe area',
)
write(rel, s)

# 7) Privacy header safe area. The screen already uses the desired “‹ Back” copy.
rel = 'app/src/main/java/com/snaploop/app/ui/ParityPrivacyScreen.kt'
s = read(rel)
s = replace_once(s, 'import androidx.compose.foundation.layout.size\n', 'import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.statusBarsPadding\n', 'privacy status bar import')
s = replace_once(
    s,
    '                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),\n',
    '                    Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),\n',
    'privacy safe area',
)
write(rel, s)

# 8) Face Setup: safe top inset; tighten LEFT pose; replace result popup with a dedicated result
# surface that keeps the selected photo visible like the iOS interaction model.
rel = 'app/src/main/java/com/snaploop/app/ui/GuidedFacePoseTracker.kt'
s = read(rel)
s = replace_once(s, '            GuidedFacePose.LEFT -> yaw in 28f..48f && abs(pitch) <= 11f\n', '            GuidedFacePose.LEFT -> yaw in 36f..55f && abs(pitch) <= 10f\n', 'tighten left pose')
s = replace_once(s, '            yaw < 20f -> "Keep turning LEFT"\n            yaw > 42f -> "Come slightly back toward center"\n', '            yaw < 36f -> "Keep turning LEFT"\n            yaw > 55f -> "Come slightly back toward center"\n', 'left hints')
write(rel, s)

rel = 'app/src/main/java/com/snaploop/app/ui/ParityFaceSetupScreen.kt'
s = read(rel)
s = replace_once(s, 'package com.snaploop.app.ui\n\n', 'package com.snaploop.app.ui\n\nimport android.graphics.BitmapFactory\n', 'face result bitmap import')
s = replace_once(s, 'import androidx.compose.foundation.layout.Box\n', 'import androidx.compose.foundation.Image\nimport androidx.compose.foundation.layout.Box\n', 'face result image import')
s = replace_once(s, 'import androidx.compose.foundation.layout.padding\n', 'import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.statusBarsPadding\n', 'face safe area imports')
s = replace_once(s, 'import androidx.compose.ui.graphics.Color\n', 'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.asImageBitmap\nimport androidx.compose.ui.layout.ContentScale\n', 'face bitmap compose imports')
s = replace_once(s, '    var testResult by remember { mutableStateOf<FaceSetupTestResult?>(null) }\n', '    var testResult by remember { mutableStateOf<FaceSetupTestResult?>(null) }\n    var testJpeg by remember { mutableStateOf<ByteArray?>(null) }\n    var testScreenOpen by rememberSaveable { mutableStateOf(false) }\n', 'face dedicated result state')
s = replace_once(s, '                    testActions.testSavedFace(userId, jpeg)\n                }.onSuccess { testResult = it }\n', '                    testJpeg = jpeg\n                    testActions.testSavedFace(userId, jpeg)\n                }.onSuccess { testResult = it; testScreenOpen = true }\n', 'face test opens result screen')
s = replace_once(
    s,
    '    BackHandler(onBack = onExit)\n    ParityBrandBackground {\n',
    '''    if (testScreenOpen) {
        BackHandler { testScreenOpen = false; testResult = null; testError = null }
        ParityFaceTestResultScreen(
            jpeg = testJpeg,
            result = testResult,
            error = testError,
            onBack = { testScreenOpen = false; testResult = null; testError = null },
            onChooseAnother = { testScreenOpen = false; testResult = null; testError = null; testPhotoPicker.launch("image/*") },
        )
        return
    }

    BackHandler(onBack = onExit)
    ParityBrandBackground {
''',
    'face dedicated screen route',
)
s = replace_once(s, '            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),\n', '            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),\n', 'face setup safe area')
# Remove success alert; errors stay alert only when picker/test failed before result screen.
start = '    testResult?.let { result ->\n        AlertDialog('
end = '\n    testError?.let { error ->'
i = s.find(start)
j = s.find(end, i)
if i < 0 or j < 0:
    raise RuntimeError('face old result alert markers missing')
s = s[:i] + '    testError?.let { error ->' + s[j + len(end):]
# Append dedicated screen before final EOF.
append = '''

@Composable
private fun ParityFaceTestResultScreen(
    jpeg: ByteArray?,
    result: FaceSetupTestResult?,
    error: String?,
    onBack: () -> Unit,
    onChooseAnother: () -> Unit,
) {
    val bitmap = remember(jpeg) {
        jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    ParityBrandBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("‹ Back") }
            Text("Test My Face Setup", fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(
                "Choose a normal photo and SnapLoop will check it against your saved Face Setup.",
                color = Color(0xFF66636C),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            )
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Selected Face Setup test photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(300.dp).padding(top = 8.dp),
                )
            }
            ParityPremiumCard(Modifier.padding(top = 16.dp)) {
                Text(
                    when {
                        error != null -> "Could Not Test Face Setup"
                        result?.accepted == true -> "Face Setup Working"
                        result != null -> "Face Not Recognized"
                        else -> "Testing…"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    error ?: result?.message ?: "Checking your saved Face Setup…",
                    color = Color(0xFF66636C),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            OutlinedButton(
                onClick = onChooseAnother,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Choose Another Photo", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 6.dp)) { Text("Done", fontWeight = FontWeight.Bold) }
        }
    }
}
'''
s = s.rstrip() + append
write(rel, s)

# 9) Auth transition: once device-level onboarding is complete, never render it again for the
# coordinator's legacy ONBOARDING gate; show a neutral restoring frame while that gate is consumed.
rel = 'app/src/main/java/com/snaploop/app/ui/SnapLoopRoot.kt'
s = read(rel)
s = replace_once(s, 'import androidx.compose.runtime.Composable\n', 'import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.fillMaxSize\nimport androidx.compose.material3.CircularProgressIndicator\nimport androidx.compose.runtime.Composable\n', 'root restoring imports')
s = replace_once(s, 'import androidx.compose.ui.platform.LocalContext\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalContext\n', 'root alignment imports')
s = replace_once(
    s,
    '        AppGate.ONBOARDING -> ParityOnboardingScreen(onCompleted = coordinator::finishOnboarding)\n',
    '''        AppGate.ONBOARDING -> if (preAuthOnboardingCompleted && state.user != null) {
            ParityBrandBackground {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            ParityOnboardingScreen(onCompleted = coordinator::finishOnboarding)
        }
''',
    'suppress post-auth onboarding flash',
)
write(rel, s)

# 10) Home/Event density and stale refresh hardening.
rel = 'app/src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt'
s = read(rel)
# Home vertical density and major type sizes.
s = replace_once(s, '            verticalArrangement = Arrangement.spacedBy(22.dp),\n', '            verticalArrangement = Arrangement.spacedBy(16.dp),\n', 'home tighter sections')
s = replace_once(s, '                        fontSize = 30.sp,\n', '                        fontSize = 25.sp,\n', 'home greeting compact')
s = replace_once(s, '            Box(\n                Modifier.size(72.dp).background(SnapGradients.Violet, RoundedCornerShape(18.dp)),\n', '            Box(\n                Modifier.size(58.dp).background(SnapGradients.Violet, RoundedCornerShape(16.dp)),\n', 'event card icon compact')
s = replace_once(s, '            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {\n', '            Column(Modifier.weight(1f).padding(start = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {\n', 'event card content compact')
s = replace_once(s, '                    fontSize = 17.sp,\n                    fontWeight = FontWeight.Bold,\n', '                    fontSize = 15.sp,\n                    fontWeight = FontWeight.Bold,\n', 'event card title compact')
s = replace_once(s, '                modifier = Modifier.height(72.dp),\n', '                modifier = Modifier.height(58.dp),\n', 'event card trailing compact')
# Action cards: no icon/title collision.
s = replace_once(s, '.padding(16.dp),\n        ) {\n            Box(\n                Modifier.size(44.dp)', '.padding(12.dp),\n        ) {\n            Box(\n                Modifier.size(36.dp)', 'home action card compact icon')
s = replace_once(s, '                    title,\n                    color = Color.White,\n                    fontSize = 16.sp,', '                    title,\n                    color = Color.White,\n                    fontSize = 14.sp,', 'home action title compact')
s = replace_once(s, '                    fontSize = 11.sp,\n                    maxLines = 2,\n', '                    fontSize = 10.sp,\n                    maxLines = 2,\n', 'home action subtitle compact')
# Shell subpage header consistent visible Back.
s = replace_once(
    s,
    '''    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ChevronLeft, "Back") }
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(Modifier.size(48.dp))
    }
''',
    '''    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹ Back", fontWeight = FontWeight.SemiBold) }
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(Modifier.size(64.dp))
    }
''',
    'shell consistent back header',
)
write(rel, s)

# Coordinator refresh keeps an open cross-platform Event synchronized with the refreshed list and
# closes it safely if membership disappears instead of leaving stale navigation state behind.
rel = 'app/src/main/java/com/snaploop/app/ui/AppCoordinator.kt'
s = read(rel)
s = replace_once(
    s,
    '''    fun refreshEvents() = launchBusy {
        val uid = requireUid()
        val refreshed = eventRepository.eventsForUser(uid)
        update { copy(events = refreshed) }
        refreshAllPhotosInternal(uid, refreshed)
    }
''',
    '''    fun refreshEvents() = launchBusy {
        val uid = requireUid()
        val selectedId = state.value.selectedEvent?.id
        val refreshed = eventRepository.eventsForUser(uid)
        val refreshedSelected = selectedId?.let { id -> refreshed.firstOrNull { it.id == id } }
        update {
            copy(
                events = refreshed,
                selectedEvent = when {
                    selectedId == null -> selectedEvent
                    refreshedSelected != null -> refreshedSelected
                    else -> null
                },
                members = if (selectedId != null && refreshedSelected == null) emptyList() else members,
                photos = if (selectedId != null && refreshedSelected == null) emptyList() else photos,
                scanProgress = if (selectedId != null && refreshedSelected == null) null else scanProgress,
                scanResult = if (selectedId != null && refreshedSelected == null) null else scanResult,
            )
        }
        if (refreshedSelected != null) {
            loadEvent(refreshedSelected)
        }
        refreshAllPhotosInternal(uid, refreshed)
    }
''',
    'refresh open event hardening',
)
write(rel, s)

# 11) Event dashboard safe area, consistent Back, and compact feature tiles so subtitles cannot clip.
rel = 'app/src/main/java/com/snaploop/app/ui/ParityEventDashboard.kt'
s = read(rel)
s = replace_once(s, 'import androidx.compose.foundation.layout.size\n', 'import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.statusBarsPadding\n', 'event safe area import')
s = replace_once(s, '            .padding(vertical = 12.dp),\n', '            .statusBarsPadding()\n            .padding(vertical = 8.dp),\n', 'event dashboard safe area')
s = replace_once(
    s,
    '''        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
            }
        }
''',
    '''        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Back", fontWeight = FontWeight.SemiBold) }
        }
''',
    'event consistent back',
)
s = replace_once(s, '                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),\n', '                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),\n', 'event feature tile compact padding')
s = replace_once(s, '                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)\n                Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 11.sp, maxLines = 1)\n', '                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)\n                Text(subtitle, color = Color.White.copy(alpha = 0.94f), fontSize = 10.sp, maxLines = 1)\n', 'event feature text compact')
write(rel, s)

# 12) Scan screen safe area and consistent Back label.
rel = 'app/src/main/java/com/snaploop/app/ui/ParityEventScanScreen.kt'
s = read(rel)
s = replace_once(s, 'import androidx.compose.foundation.layout.size\n', 'import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.statusBarsPadding\n', 'scan safe area import')
s = replace_once(s, '        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp),\n', '        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),\n', 'scan safe area')
s = replace_once(
    s,
    '''            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
''',
    '''            TextButton(onClick = onBack) {
                Text("‹ Back", fontWeight = FontWeight.SemiBold)
            }
''',
    'scan consistent back',
)
s = replace_once(s, '            Spacer(Modifier.size(48.dp))\n', '            Spacer(Modifier.size(64.dp))\n', 'scan header balance')
write(rel, s)

# 13) Faster first-pass scanning: avoid JPEG encode + second JPEG decode for the analysis path.
# MediaStore returns a normalized Bitmap for ML; JPEG is only created for publication previews.
rel = 'app/src/main/java/com/snaploop/app/media/MediaStorePhotoLibrary.kt'
s = read(rel)
start = '    /** Downsamples and bakes EXIF orientation before face processing or preview publication. */\n    @Throws(PhotoUnavailableException::class)\n    fun normalizedJpeg'
end = '\n    private fun openAsset'
i = s.find(start)
j = s.find(end, i)
if i < 0 or j < 0:
    raise RuntimeError('media normalization markers missing')
new_block = '''    /** Downsamples and bakes EXIF orientation for local on-device face analysis. */
    @Throws(PhotoUnavailableException::class)
    fun normalizedBitmap(asset: LocalPhotoAsset, maxPixelSize: Int): Bitmap {
        require(maxPixelSize > 0)
        var sourceWidth = asset.width
        var sourceHeight = asset.height
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openAsset(asset).use { BitmapFactory.decodeStream(it, null, bounds) }
            sourceWidth = bounds.outWidth
            sourceHeight = bounds.outHeight
        }
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw PhotoUnavailableException("Photo ${asset.id} can no longer be decoded")
        }

        var sample = 1
        while (max(sourceWidth / sample, sourceHeight / sample) > maxPixelSize * 2) sample *= 2
        val bitmap = openAsset(asset).use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } ?: throw PhotoUnavailableException("Photo ${asset.id} is unsupported or no longer available")

        val orientation = runCatching {
            openAsset(asset).use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrElse { error ->
            bitmap.recycle()
            if (error is PhotoUnavailableException) throw error
            throw PhotoUnavailableException("Photo ${asset.id} is no longer available", error)
        }

        val upright = applyOrientation(bitmap, orientation)
        if (upright !== bitmap) bitmap.recycle()
        val scaled = scaleDown(upright, maxPixelSize)
        if (scaled !== upright) upright.recycle()
        return scaled
    }

    /** Publication preview encoder. Face analysis uses normalizedBitmap directly. */
    @Throws(PhotoUnavailableException::class)
    fun normalizedJpeg(asset: LocalPhotoAsset, maxPixelSize: Int, quality: Int = 92): ByteArray {
        require(quality in 1..100)
        val bitmap = normalizedBitmap(asset, maxPixelSize)
        return try {
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                throw PhotoUnavailableException("Photo ${asset.id} could not be encoded")
            }
            output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
'''
s = s[:i] + new_block + s[j:]
write(rel, s)

# ML aligner overload accepts an already upright/normalized Bitmap, eliminating duplicate decode.
rel = 'app/src/main/java/com/snaploop/app/face/FaceAligner.kt'
s = read(rel)
start = '    suspend fun diagnostics(imageData: ByteArray, outputSize: Int = 112): FaceAlignmentDiagnostics {'
end = '\n    private fun fivePoints'
i = s.find(start)
j = s.find(end, i)
if i < 0 or j < 0:
    raise RuntimeError('aligner diagnostics markers missing')
old_body = s[i:j]
# Reuse existing core body text by adapting first/last ownership lines.
core_start = old_body.find('        val faces = try')
core_end_marker = '        return FaceAlignmentDiagnostics(faces.size, usable, failures, aligned)\n'
core_end = old_body.find(core_end_marker)
if core_start < 0 or core_end < 0:
    raise RuntimeError('aligner core markers missing')
core = old_body[core_start:core_end + len(core_end_marker)]
core = core.replace('catch (t: Throwable) { upright.recycle(); throw SnapLoopException.Backend("face_detection_failed", "Face detection failed", t) }', 'catch (t: Throwable) { throw SnapLoopException.Backend("face_detection_failed", "Face detection failed", t) }')
core = core.replace('        upright.recycle()\n', '')
new = '''    suspend fun diagnostics(imageData: ByteArray, outputSize: Int = 112): FaceAlignmentDiagnostics {
        val upright = decodeUpright(imageData) ?: throw SnapLoopException.InvalidData("Photo could not be decoded")
        return try {
            diagnostics(upright, outputSize)
        } finally {
            upright.recycle()
        }
    }

    suspend fun diagnostics(upright: Bitmap, outputSize: Int = 112): FaceAlignmentDiagnostics {
''' + core + '    }\n'
s = s[:i] + new + s[j:]
write(rel, s)

# Android face pipeline overload for Bitmap analysis.
rel = 'app/src/main/java/com/snaploop/app/face/AndroidFacePipeline.kt'
s = read(rel)
s = replace_once(s, 'import android.content.Context\n', 'import android.content.Context\nimport android.graphics.Bitmap\n', 'pipeline bitmap import')
s = replace_once(s, '    suspend fun detectFaces(imageData: ByteArray): List<DetectedEmbedding> = process(imageData).first\n', '    suspend fun detectFaces(imageData: ByteArray): List<DetectedEmbedding> = process(imageData).first\n    suspend fun detectFaces(bitmap: Bitmap): List<DetectedEmbedding> = process(bitmap).first\n', 'pipeline bitmap API')
marker = '    private suspend fun process(imageData: ByteArray): Pair<List<DetectedEmbedding>, FacePipelineDiagnostics> {'
i = s.find(marker)
if i < 0:
    raise RuntimeError('pipeline process marker missing')
# Add bitmap overload before byte process, duplicating only alignment-to-embeddings mechanics by helper refactor.
# First convert current process to call a shared method.
start = i
end_marker = '\n    private fun validatePreModelGates'
j = s.find(end_marker, i)
if j < 0:
    raise RuntimeError('pipeline process end marker missing')
new_process = '''    private suspend fun process(imageData: ByteArray): Pair<List<DetectedEmbedding>, FacePipelineDiagnostics> =
        processAlignment(aligner.diagnostics(imageData, 112))

    private suspend fun process(bitmap: Bitmap): Pair<List<DetectedEmbedding>, FacePipelineDiagnostics> =
        processAlignment(aligner.diagnostics(bitmap, 112))

    private fun processAlignment(alignment: FaceAlignmentDiagnostics): Pair<List<DetectedEmbedding>, FacePipelineDiagnostics> {
        val embeddings = mutableListOf<DetectedEmbedding>()
        val rejections = mutableListOf<String>()
        try {
            for (face in alignment.alignedFaces) {
                val rejection = validatePreModelGates(face)
                if (rejection != null) {
                    rejections += rejection
                    continue
                }
                try {
                    embeddings += DetectedEmbedding(embed(face), face.sizeFraction)
                } catch (_: Throwable) {
                    rejections += "embedding failed"
                }
            }
            return embeddings to FacePipelineDiagnostics(
                facesDetected = alignment.facesDetected,
                facesWithUsableLandmarks = alignment.facesWithUsableLandmarks,
                alignmentFailures = alignment.alignmentFailures,
                embeddedCount = embeddings.size,
                rejectionReasons = rejections,
            )
        } finally {
            alignment.alignedFaces.forEach { it.bitmap.recycle() }
        }
    }
'''
s = s[:start] + new_process + s[j:]
write(rel, s)

# Scanner uses 1024px Bitmap analysis path; publication JPEG remains high quality and only on matches.
rel = 'app/src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt'
s = read(rel)
s = replace_once(
    s,
    '''                    val analysisJpeg = library.normalizedJpeg(
                        asset,
                        ANALYSIS_MAX_PIXEL_SIZE,
                        ANALYSIS_JPEG_QUALITY,
                    )

                    ScanCancellationRegistry.ensureActive(cancellationToken)
                    val detected = faces.detectFaces(analysisJpeg)
''',
    '''                    val analysisBitmap = library.normalizedBitmap(asset, ANALYSIS_MAX_PIXEL_SIZE)
                    val detected = try {
                        ScanCancellationRegistry.ensureActive(cancellationToken)
                        faces.detectFaces(analysisBitmap)
                    } finally {
                        analysisBitmap.recycle()
                    }
''',
    'scanner bitmap analysis',
)
s = replace_once(s, '        const val ANALYSIS_MAX_PIXEL_SIZE = 1280\n        const val ANALYSIS_JPEG_QUALITY = 84\n', '        const val ANALYSIS_MAX_PIXEL_SIZE = 1024\n', 'scanner smaller bounded analysis')
write(rel, s)

# 14) Regression tests for this acceptance batch.
test_rel = 'app/src/test/java/com/snaploop/app/ui/DeviceAcceptanceP0RegressionTest.kt'
test = r'''package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAcceptanceP0RegressionTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$path not found")
    }

    @Test fun `automatic foreground cadence is thirty minutes and roster changes still bypass`() {
        val policy = source("src/main/java/com/snaploop/app/scanner/AutomaticScanPolicy.kt")
        assertTrue(policy.contains("30L * 60L * 1000L"))
        assertTrue(policy.contains("if (triggerChanged) return true"))
    }

    @Test fun `event range omits year and keeps both endpoints`() {
        val formatter = source("src/main/java/com/snaploop/app/ui/EventDateRangeFormatter.kt")
        assertTrue(formatter.contains("DateTimeFormatter.ofPattern(\"MMM d\""))
        assertFalse(formatter.contains("yyyy"))
        assertTrue(formatter.contains(" – "))
    }

    @Test fun `gallery defaults to three and branded insight is transparent`() {
        val gallery = source("src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt")
        assertTrue(gallery.contains("mutableIntStateOf(3)"))
        assertTrue(gallery.contains("SnapGradients.Insight"))
        assertTrue(gallery.contains("containerColor = Color.Transparent"))
    }

    @Test fun `qr scanner has border only rather than white material card`() {
        val qr = source("src/main/java/com/snaploop/app/ui/QrCodeTools.kt")
        assertTrue(qr.contains(".border(2.dp, Color.White.copy(alpha = 0.78f)"))
        assertFalse(qr.contains("fillMaxSize().background(Color.White.copy(alpha = 0.08f))"))
    }

    @Test fun `post auth legacy onboarding gate does not render onboarding again`() {
        val root = source("src/main/java/com/snaploop/app/ui/SnapLoopRoot.kt")
        assertTrue(root.contains("AppGate.ONBOARDING -> if (preAuthOnboardingCompleted && state.user != null)"))
        assertTrue(root.contains("CircularProgressIndicator()"))
    }

    @Test fun `analysis path uses a bitmap without jpeg roundtrip`() {
        val scanner = source("src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt")
        val library = source("src/main/java/com/snaploop/app/media/MediaStorePhotoLibrary.kt")
        val pipeline = source("src/main/java/com/snaploop/app/face/AndroidFacePipeline.kt")
        assertTrue(scanner.contains("library.normalizedBitmap(asset, ANALYSIS_MAX_PIXEL_SIZE)"))
        assertTrue(scanner.contains("ANALYSIS_MAX_PIXEL_SIZE = 1024"))
        assertFalse(scanner.contains("ANALYSIS_JPEG_QUALITY"))
        assertTrue(library.contains("fun normalizedBitmap"))
        assertTrue(pipeline.contains("suspend fun detectFaces(bitmap: Bitmap)"))
    }

    @Test fun `reported safe areas and back labels are explicit`() {
        val privacy = source("src/main/java/com/snaploop/app/ui/ParityPrivacyScreen.kt")
        val scan = source("src/main/java/com/snaploop/app/ui/ParityEventScanScreen.kt")
        val gallery = source("src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt")
        assertTrue(privacy.contains("statusBarsPadding()"))
        assertTrue(scan.contains("‹ Back"))
        assertTrue(gallery.contains("‹ Back"))
    }

    @Test fun `test my face uses a dedicated result screen`() {
        val face = source("src/main/java/com/snaploop/app/ui/ParityFaceSetupScreen.kt")
        assertTrue(face.contains("ParityFaceTestResultScreen"))
        assertTrue(face.contains("Choose Another Photo"))
        assertTrue(face.contains("Selected Face Setup test photo"))
    }

    @Test fun `left enrollment pose requires deliberate turn`() {
        val tracker = source("src/main/java/com/snaploop/app/ui/GuidedFacePoseTracker.kt")
        assertTrue(tracker.contains("GuidedFacePose.LEFT -> yaw in 36f..55f"))
    }
}
'''
write(test_rel, test)

# Update existing source-inspection regressions that intentionally encoded the previous scan constants/default.
for path in [
    'app/src/test/java/com/snaploop/app/ui/OvernightDeviceBatchRegressionTest.kt',
    'app/src/test/java/com/snaploop/app/ui/DeviceAcceptanceBatchRegressionTest.kt',
]:
    p = ROOT / path
    if not p.exists():
        continue
    t = p.read_text()
    t = t.replace('ANALYSIS_MAX_PIXEL_SIZE = 1280', 'ANALYSIS_MAX_PIXEL_SIZE = 1024')
    t = t.replace('ANALYSIS_JPEG_QUALITY = 84', 'library.normalizedBitmap(asset, ANALYSIS_MAX_PIXEL_SIZE)')
    p.write_text(t)

# Existing pose tracker tests use the previous deliberate-left sample. Make the intended left sample
# clearly exceed the new threshold while preserving the straight-face rejection assertions.
pose_test = ROOT / 'app/src/test/java/com/snaploop/app/ui/GuidedFacePoseTrackerTest.kt'
if pose_test.exists():
    t = pose_test.read_text()
    t = t.replace('yawDegrees = 34f', 'yawDegrees = 42f')
    t = t.replace('yawDegrees = 32f', 'yawDegrees = 42f')
    pose_test.write_text(t)

print('Device acceptance P0 batch applied')
