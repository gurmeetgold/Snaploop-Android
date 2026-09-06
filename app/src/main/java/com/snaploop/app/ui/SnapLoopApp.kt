package com.snaploop.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.snaploop.app.BuildConfig
import com.snaploop.app.model.SnapEvent
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SnapLoopApp(
    activity: Activity,
    coordinator: AppCoordinator,
) {
    val state by coordinator.state.collectAsState()

    if (!BuildConfig.FIREBASE_CONFIG_PRESENT) {
        FirebaseConfigurationRequired()
        return
    }

    Box(Modifier.fillMaxSize()) {
        when (state.gate) {
            AppGate.RESTORING -> LoadingScreen(
                message = "Restoring your SnapLoop session…",
                canRetry = state.restoreCanRetry,
                error = state.restoreError,
                onRetry = coordinator::restore,
            )
            AppGate.AUTH -> AuthScreen(
                state = state,
                onSendCode = { coordinator.startPhoneVerification(activity, it) },
                onConfirmCode = coordinator::confirmCode,
            )
            AppGate.ONBOARDING -> OnboardingScreen(onContinue = coordinator::finishOnboarding)
            AppGate.NAME_SETUP -> NameSetupScreen(
                initialName = state.user?.displayName.orEmpty(),
                onSave = coordinator::saveDisplayName,
            )
            AppGate.BIOMETRIC_CONSENT -> ConsentScreen(onAccept = coordinator::acceptBiometricConsent)
            AppGate.FACE_SETUP -> FaceSetupScreen(
                captures = state.faceCaptures,
                onCapture = coordinator::addFaceCapture,
                onReset = coordinator::resetFaceCaptures,
                onComplete = coordinator::completeFaceSetup,
            )
            AppGate.MAIN -> MainTabs(state, coordinator)
        }

        if (state.busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(shape = RoundedCornerShape(24.dp)) {
                    CircularProgressIndicator(Modifier.padding(28.dp))
                }
            }
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = coordinator::clearMessage,
            confirmButton = { TextButton(onClick = coordinator::clearMessage) { Text("OK") } },
            title = { Text("SnapLoop") },
            text = { Text(message) },
        )
    }
}

@Composable
private fun FirebaseConfigurationRequired() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(28.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SnapLoop", fontWeight = FontWeight.Black, fontSize = 34.sp)
                Text("Firebase configuration is required for this development build.")
                Text("Add app/google-services.json from the existing SnapLoop Firebase project, then rebuild.")
            }
        }
    }
}

@Composable
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

@Composable
private fun AuthScreen(
    state: AppUiState,
    onSendCode: (String) -> Unit,
    onConfirmCode: (String) -> Unit,
) {
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    ScreenColumn {
        BrandHeader(
            title = "SnapLoop",
            subtitle = "Never miss a photo you're in",
        )
        Spacer(Modifier.height(18.dp))
        if (state.verificationId == null) {
            Text("Sign in with your phone number", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Use international format, including the + country code.")
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Phone number") },
                placeholder = { Text("+1 555 123 4567") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
            )
            Button(onClick = { onSendCode(phone.filterNot(Char::isWhitespace)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        } else {
            Text("Enter verification code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(8) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Verification code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
            )
            Button(onClick = { onConfirmCode(code) }, modifier = Modifier.fillMaxWidth()) {
                Text("Verify & Sign In")
            }
        }
    }
}

@Composable
private fun OnboardingScreen(onContinue: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf(
        "Your photos, automatically" to "SnapLoop finds photos you're in across an event without uploading everyone's full photo library.",
        "Private by design" to "Face matching happens on-device. Only event matches and the minimum data needed to share them are published.",
        "You stay in control" to "Scanning is user-triggered, event-scoped and time-bounded. You can mark a match Not Me, withdraw biometric consent, or delete your account.",
    )
    val item = pages[page]
    ScreenColumn(
        verticalArrangement = Arrangement.Center,
    ) {
        BrandHeader(item.first, item.second)
        Spacer(Modifier.height(16.dp))
        Text("${page + 1} of ${pages.size}", fontWeight = FontWeight.Bold)
        Button(
            onClick = {
                if (page == pages.lastIndex) onContinue() else page++
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (page == pages.lastIndex) "Get Started" else "Continue")
        }
        if (page > 0) {
            TextButton(onClick = { page-- }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun NameSetupScreen(initialName: String, onSave: (String) -> Unit) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    ScreenColumn(verticalArrangement = Arrangement.Center) {
        BrandHeader("What should friends call you?", "This name is shown to people in your SnapLoop events.")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your name") },
            singleLine = true,
        )
        Button(onClick = { onSave(name) }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}

@Composable
private fun ConsentScreen(
    onAccept: (String, String, Boolean, Boolean, Boolean) -> Unit,
) {
    var country by rememberSaveable { mutableStateOf("CA") }
    var subdivision by rememberSaveable { mutableStateOf("ON") }
    var age by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf(false) }
    var ownFace by rememberSaveable { mutableStateOf(false) }

    ScreenColumn {
        BrandHeader(
            "Biometric consent",
            "SnapLoop uses a face template to find photos of you. Your guided selfie stays encrypted on this device; the face template is protected and used only for SnapLoop matching.",
        )
        Text(
            "Face matching is currently enabled only in supported Canadian provinces/territories and India. Consent is explicit and can be withdrawn from Privacy & Data.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = country,
            onValueChange = { country = it.uppercase(Locale.US).take(2) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Country code (CA or IN)") },
            singleLine = true,
        )
        if (country.uppercase(Locale.US) == "CA") {
            OutlinedTextField(
                value = subdivision,
                onValueChange = { subdivision = it.uppercase(Locale.US).take(2) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Province / territory code") },
                supportingText = { Text("Example: ON, BC, AB, NS") },
                singleLine = true,
            )
        }
        ConsentCheck("I am 18 or older.", age) { age = it }
        ConsentCheck("I have read and acknowledge the biometric notice.", notice) { notice = it }
        ConsentCheck("I will use Face Setup only for my own face.", ownFace) { ownFace = it }
        Button(
            onClick = {
                onAccept(
                    country.uppercase(Locale.US),
                    if (country.uppercase(Locale.US) == "IN") "" else subdivision.uppercase(Locale.US),
                    age,
                    notice,
                    ownFace,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = age && notice && ownFace,
        ) {
            Text("I Agree & Continue")
        }
    }
}

@Composable
private fun ConsentCheck(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
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

@Composable
private fun MainTabs(state: AppUiState, coordinator: AppCoordinator) {
    if (state.selectedEvent != null) {
        EventScreen(state, coordinator)
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Home", "Gallery", "You").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {},
                        label = { Text(label, fontWeight = FontWeight.Bold) },
                    )
                }
            }
        }
    ) { inset ->
        when (tab) {
            0 -> HomeScreen(state, coordinator, Modifier.padding(inset))
            1 -> GalleryScreen(state, coordinator, Modifier.padding(inset))
            else -> YouScreen(state, coordinator, Modifier.padding(inset))
        }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, coordinator: AppCoordinator, modifier: Modifier = Modifier) {
    var createOpen by remember { mutableStateOf(false) }
    var joinOpen by remember { mutableStateOf(false) }

    ScreenColumn(modifier) {
        BrandHeader("SnapLoop", "Photos your friends took of you on their phones, brought to your phone automatically.")
        GradientCard("Create Event", "Trip, party, family & more", onClick = { createOpen = true })
        GradientCard("Join Event", "Code, link or QR", onClick = { joinOpen = true })
        SectionTitle("Your Events")
        if (state.events.isEmpty()) {
            Text("No events yet. Create one or join with an invite code.")
        } else {
            state.events.forEach { event ->
                EventCard(event = event, onClick = { coordinator.openEvent(event) })
            }
        }
        TextButton(onClick = coordinator::refreshEvents, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Refresh Events")
        }
    }

    if (createOpen) {
        CreateEventDialog(
            onDismiss = { createOpen = false },
            onCreate = { name, days ->
                createOpen = false
                coordinator.createEvent(name, days)
            },
        )
    }
    if (joinOpen) {
        JoinEventDialog(
            onDismiss = { joinOpen = false },
            onJoin = {
                joinOpen = false
                coordinator.joinEvent(it)
            },
        )
    }
}

@Composable
private fun GalleryScreen(state: AppUiState, coordinator: AppCoordinator, modifier: Modifier = Modifier) {
    ScreenColumn(modifier) {
        BrandHeader("Gallery", "Open an event to see the photos SnapLoop found for you.")
        if (state.events.isEmpty()) {
            Text("Your matched event photos will appear here after scanning.")
        } else {
            state.events.forEach { event ->
                EventCard(event, onClick = { coordinator.openEvent(event) })
            }
        }
    }
}

@Composable
private fun EventScreen(state: AppUiState, coordinator: AppCoordinator) {
    val event = state.selectedEvent ?: return
    val context = LocalContext.current
    val currentUid = state.user?.id
    val me = state.members.firstOrNull { it.userId == currentUid }
    var leaveConfirm by remember { mutableStateOf(false) }

    val photoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val allowed = result[Manifest.permission.READ_MEDIA_IMAGES] == true ||
            (Build.VERSION.SDK_INT >= 34 && result[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true) ||
            result[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        if (allowed) coordinator.scanSelectedEvent()
    }

    ScreenColumn {
        TextButton(onClick = coordinator::closeEvent) { Text("‹ Back") }
        BrandHeader(event.name, "Event code ${event.joinCode}")
        EventMetadata(event)
        SectionTitle("People")
        Text("${state.members.size} participant${if (state.members.size == 1) "" else "s"}")
        state.members.take(8).forEach { member ->
            Text("• ${member.displayName ?: "SnapLoop member"}${if (member.userId == currentUid) " (You)" else ""}")
        }
        HorizontalDivider()
        SectionTitle("Sharing & Scan")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Share matches from my phone", fontWeight = FontWeight.Bold)
                Text("Scanning remains manual and event-scoped.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = me?.sharingEnabled == true,
                onCheckedChange = coordinator::setSharing,
                enabled = me != null,
            )
        }
        Button(
            onClick = {
                val permissions = if (Build.VERSION.SDK_INT >= 34) {
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                } else if (Build.VERSION.SDK_INT >= 33) {
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                val alreadyAllowed = permissions.any {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                if (alreadyAllowed) coordinator.scanSelectedEvent() else photoPermissionLauncher.launch(permissions)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Scan Event Photos") }

        state.scanProgress?.let { progress ->
            Text("Scanning ${progress.checked} / ${progress.total} • ${progress.published} matches published")
        }
        state.scanResult?.let { result ->
            Text("Scan complete: ${result.checked} checked, ${result.published} published, ${result.remaining} remaining.")
        }

        HorizontalDivider()
        SectionTitle("Photos of You")
        Text("${state.photos.size} photo${if (state.photos.size == 1) "" else "s"} found across this event")
        if (state.photos.isEmpty()) {
            Text("No matches yet. Ask participants to complete Face Setup, then run a scan.")
        } else {
            state.photos.forEach { photo ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Matched photo", fontWeight = FontWeight.Bold)
                        Text(formatMillis(photo.capturedAtMillis), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { coordinator.dismissPhoto(photo) }) { Text("Not Me") }
                    }
                }
            }
        }
        OutlinedButton(onClick = coordinator::refreshPhotos, modifier = Modifier.fillMaxWidth()) { Text("Refresh Gallery") }
        OutlinedButton(onClick = { leaveConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Leave Event") }
    }

    if (leaveConfirm) {
        AlertDialog(
            onDismissRequest = { leaveConfirm = false },
            title = { Text("Leave ${event.name}?") },
            text = { Text("You will stop participating in this event. Existing privacy controls remain available.") },
            confirmButton = {
                TextButton(onClick = {
                    leaveConfirm = false
                    coordinator.leaveSelectedEvent()
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { leaveConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun YouScreen(state: AppUiState, coordinator: AppCoordinator, modifier: Modifier = Modifier) {
    var editName by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    ScreenColumn(modifier) {
        BrandHeader("You", state.user?.displayName ?: "Your SnapLoop profile")
        SettingsCard("Edit Your Name") { editName = true }
        SettingsCard("Update Face Setup") {
            coordinator.resetFaceCaptures()
            coordinator.replayFaceSetupForUpdate()
        }
        SettingsCard("Privacy & Data") { privacy = true }
        SettingsCard("Replay Onboarding") { coordinator.replayOnboarding() }
        SettingsCard("Sign Out") { coordinator.signOut() }
    }

    if (editName) {
        EditNameDialog(
            initial = state.user?.displayName.orEmpty(),
            onDismiss = { editName = false },
            onSave = {
                editName = false
                coordinator.saveDisplayName(it)
            },
        )
    }
    if (privacy) {
        PrivacyDialog(
            onDismiss = { privacy = false },
            onWithdraw = {
                privacy = false
                coordinator.withdrawBiometrics()
            },
            onDelete = {
                privacy = false
                coordinator.deleteAccount()
            },
        )
    }
}

@Composable
private fun EventCard(event: SnapEvent, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(event.name, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Text("Code ${event.joinCode} • ${event.category.name.replaceFirstChar { it.uppercase() }}")
            Text("${formatMillis(event.startsAt.toEpochMilli())} – ${formatMillis(event.endsAt.toEpochMilli())}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EventMetadata(event: SnapEvent) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Photo window", fontWeight = FontWeight.Bold)
            Text("${formatMillis(event.startsAt.toEpochMilli())} – ${formatMillis(event.endsAt.toEpochMilli())}")
            event.locationName?.takeIf { it.isNotBlank() }?.let { Text(it) }
        }
    }
}

@Composable
private fun GradientCard(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(
                    Brush.linearGradient(listOf(SnapColors.Coral, SnapColors.HotPink, SnapColors.Lilac, SnapColors.Blue)),
                    RoundedCornerShape(28.dp),
                )
                .padding(22.dp)
        ) {
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(subtitle, color = Color.White, fontSize = 16.sp)
            }
        }
    }
    if (onClick == null) content() else Card(onClick = onClick, shape = RoundedCornerShape(28.dp)) { content() }
}

@Composable
private fun SettingsCard(title: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(24.dp)) {
        Text(title, Modifier.fillMaxWidth().padding(22.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BrandHeader(title: String, subtitle: String) {
    Text(title, fontSize = 36.sp, fontWeight = FontWeight.Black)
    Text(subtitle, fontSize = 17.sp)
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black)
}

@Composable
private fun ScreenColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
private fun CreateEventDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("3") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Event name") }, singleLine = true)
                OutlinedTextField(
                    days,
                    { days = it.filter(Char::isDigit).take(2) },
                    label = { Text("Photo window (days)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, days.toIntOrNull() ?: 3) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun JoinEventDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Event") },
        text = {
            OutlinedTextField(
                code,
                { code = it.uppercase(Locale.US).filter(Char::isLetterOrDigit).take(12) },
                label = { Text("Event code") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onJoin(code) }) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditNameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Your Name") },
        text = { OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit, onWithdraw: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete SnapLoop account?") },
            text = { Text("This requests permanent deletion of your SnapLoop account and server-side data. This action cannot be undone.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete Permanently") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy & Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Face matching is explicit-consent only. Scanning runs only when you start it.")
                OutlinedButton(onClick = onWithdraw, modifier = Modifier.fillMaxWidth()) { Text("Withdraw Biometric Consent") }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete Account") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private fun formatMillis(value: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    return java.time.Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(formatter)
}
