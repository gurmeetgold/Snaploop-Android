package com.snaploop.app.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.firebase.storage.FirebaseStorage
import com.snaploop.app.BuildConfig
import com.snaploop.app.core.DeepLinkParser
import com.snaploop.app.domain.PhotoMatch
import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventMember
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
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
                onDifferentNumber = coordinator::useDifferentPhoneNumber,
            )
            AppGate.ONBOARDING -> OnboardingScreen(onContinue = coordinator::finishOnboarding)
            AppGate.NAME_SETUP -> NameSetupScreen(
                initialName = state.user?.displayName.orEmpty(),
                onSave = coordinator::saveDisplayName,
            )
            AppGate.BIOMETRIC_CONSENT -> ParityConsentScreen(
                onAccept = coordinator::acceptBiometricConsent,
                onNotNow = coordinator::skipFaceSetup,
            )
            AppGate.FACE_SETUP -> ParityFaceSetupScreen(
                state = state,
                onCapture = coordinator::addFaceCapture,
                onReset = coordinator::resetFaceCaptures,
                onComplete = coordinator::completeFaceSetup,
                onExit = coordinator::cancelFaceSetup,
            )
            AppGate.MAIN -> MainTabs(state, coordinator)
        }

        if (state.busy && state.gate != AppGate.FACE_SETUP) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)),
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
    BrandBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PremiumCard(Modifier.padding(28.dp)) {
                BrandWordmark()
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
    BrandBackground {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(78)
            if (!canRetry) CircularProgressIndicator(Modifier.padding(top = 22.dp))
            Text(message, Modifier.padding(top = 18.dp), fontWeight = FontWeight.Bold)
            if (canRetry) {
                Text(
                    error ?: "SnapLoop could not finish restoring your session.",
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Your account and Face Setup have not been deleted.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                PrimaryButton("Retry", onRetry, Modifier.padding(top = 16.dp))
            }
        }
    }
}

private data class PhoneCountry(val region: String, val name: String, val callingCode: String)

private val supportedPhoneCountries = listOf(
    PhoneCountry("CA", "Canada", "+1"),
    PhoneCountry("US", "United States", "+1"),
    PhoneCountry("IN", "India", "+91"),
    PhoneCountry("GB", "United Kingdom", "+44"),
    PhoneCountry("AU", "Australia", "+61"),
    PhoneCountry("NZ", "New Zealand", "+64"),
    PhoneCountry("AE", "United Arab Emirates", "+971"),
    PhoneCountry("SG", "Singapore", "+65"),
    PhoneCountry("DE", "Germany", "+49"),
    PhoneCountry("FR", "France", "+33"),
    PhoneCountry("IT", "Italy", "+39"),
    PhoneCountry("ES", "Spain", "+34"),
)

@Composable
private fun AuthScreen(
    state: AppUiState,
    onSendCode: (String) -> Unit,
    onConfirmCode: (String) -> Unit,
    onDifferentNumber: () -> Unit,
) {
    val localeRegion = Locale.getDefault().country.uppercase(Locale.US)
    var selectedCountry by remember {
        mutableStateOf(supportedPhoneCountries.firstOrNull { it.region == localeRegion } ?: supportedPhoneCountries.first())
    }
    var countryMenuOpen by remember { mutableStateOf(false) }
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    BrandBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            BrandWordmark()
            Text(
                "Get every photo of you.",
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                color = SnapColors.Ink,
                modifier = Modifier.padding(top = 28.dp),
            )
            Text(
                "Photos your friends took of you on their phones, brought to your phone automatically.",
                textAlign = TextAlign.Center,
                color = Color(0xFF66636C),
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )

            PremiumCard {
                if (state.verificationId == null) {
                    Text("▣  Mobile number", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SnapColors.Ink)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            OutlinedButton(
                                onClick = { countryMenuOpen = true },
                                modifier = Modifier.height(62.dp),
                                shape = RoundedCornerShape(17.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFFE4DC)),
                            ) {
                                Text("${selectedCountry.region} ${selectedCountry.callingCode}⌄", color = SnapColors.Coral, fontWeight = FontWeight.Black)
                            }
                            DropdownMenu(expanded = countryMenuOpen, onDismissRequest = { countryMenuOpen = false }) {
                                supportedPhoneCountries.forEach { country ->
                                    DropdownMenuItem(
                                        text = { Text("${country.name}  ${country.callingCode}") },
                                        onClick = {
                                            selectedCountry = country
                                            countryMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Phone number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(17.dp),
                        )
                    }
                    PrimaryButton(
                        text = "●  Send Code",
                        onClick = {
                            normalizePhoneNumber(phone, selectedCountry)?.let(onSendCode)
                        },
                        modifier = Modifier.padding(top = 18.dp),
                        enabled = normalizePhoneNumber(phone, selectedCountry) != null,
                    )
                } else {
                    Text("◈", fontSize = 36.sp, color = SnapColors.Coral, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text("Enter the 6-digit code", fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        placeholder = { Text("6-digit code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(17.dp),
                    )
                    PrimaryButton(
                        "✓  Verify",
                        onClick = { onConfirmCode(code) },
                        modifier = Modifier.padding(top = 16.dp),
                        enabled = code.length == 6,
                    )
                    TextButton(onClick = onDifferentNumber, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Use a different number", color = SnapColors.Blue, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(44.dp))
        }
    }
}

private fun normalizePhoneNumber(input: String, country: PhoneCountry): String? {
    val allowed = input.trim().filter { it.isDigit() || it == '+' }
    if (allowed.startsWith('+')) {
        val digits = allowed.drop(1).filter(Char::isDigit)
        return if (digits.length in 8..15) "+$digits" else null
    }
    var digits = allowed.filter(Char::isDigit)
    while (digits.startsWith('0')) digits = digits.drop(1)
    if ((country.region == "CA" || country.region == "US") && digits.length == 11 && digits.startsWith('1')) {
        digits = digits.drop(1)
    }
    val callingDigits = country.callingCode.filter(Char::isDigit)
    val combined = callingDigits + digits
    return if (combined.length in 8..15) "+$combined" else null
}

@Composable
private fun OnboardingScreen(onContinue: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf(
        "Your photos, automatically" to "SnapLoop finds photos you're in across an Event without uploading everyone's full photo library.",
        "Private by design" to "Face matching happens on-device. Only Event matches and the minimum data needed to share them are published.",
        "You stay in control" to "Scanning is Event-scoped and time-bounded. You can withdraw biometric consent or delete your account at any time.",
    )
    val item = pages[page]
    BrandBackground {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(72)
            Text(item.first, fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
            Text(item.second, textAlign = TextAlign.Center, color = Color(0xFF66636C), modifier = Modifier.padding(top = 14.dp))
            Text("${page + 1} of ${pages.size}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp))
            PrimaryButton(
                if (page == pages.lastIndex) "Get Started" else "Continue",
                onClick = { if (page == pages.lastIndex) onContinue() else page++ },
                modifier = Modifier.padding(top = 14.dp),
            )
            if (page > 0) TextButton(onClick = { page-- }) { Text("Back") }
        }
    }
}

@Composable
private fun NameSetupScreen(initialName: String, onSave: (String) -> Unit) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    BrandBackground {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(66)
            Text("What should friends call you?", fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 22.dp))
            Text("This name is shown to people in your SnapLoop Events.", textAlign = TextAlign.Center, color = Color(0xFF66636C), modifier = Modifier.padding(top = 10.dp))
            PremiumCard(Modifier.padding(top = 22.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                PrimaryButton("Continue", { onSave(name) }, Modifier.padding(top = 14.dp), enabled = name.trim().length >= 2)
            }
        }
    }
}

@Composable
private fun ConsentScreen(onAccept: (String, String, Boolean, Boolean, Boolean) -> Unit) {
    var country by rememberSaveable { mutableStateOf("CA") }
    var subdivision by rememberSaveable { mutableStateOf("ON") }
    var countryMenu by remember { mutableStateOf(false) }
    var provinceMenu by remember { mutableStateOf(false) }
    var age by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf(false) }
    var ownFace by rememberSaveable { mutableStateOf(false) }
    val provinces = listOf("AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "SK", "YT")

    BrandBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(34.dp))
            BrandMark(62)
            Text("Biometric consent", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 18.dp))
            Text(
                "SnapLoop uses a face template to find photos of you. Guided selfie images stay encrypted on this device; matching is used only for SnapLoop.",
                textAlign = TextAlign.Center,
                color = Color(0xFF66636C),
                modifier = Modifier.padding(top = 10.dp),
            )
            PremiumCard(Modifier.padding(top = 20.dp)) {
                Text("Face matching is currently enabled in supported Canadian provinces/territories and India. Quebec is not enabled.", fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    OutlinedButton(onClick = { countryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (country == "CA") "Canada" else "India")
                    }
                    DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                        DropdownMenuItem(text = { Text("Canada") }, onClick = { country = "CA"; countryMenu = false })
                        DropdownMenuItem(text = { Text("India") }, onClick = { country = "IN"; subdivision = ""; countryMenu = false })
                    }
                }
                if (country == "CA") {
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        OutlinedButton(onClick = { provinceMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Province / territory: $subdivision") }
                        DropdownMenu(expanded = provinceMenu, onDismissRequest = { provinceMenu = false }) {
                            provinces.forEach { code -> DropdownMenuItem(text = { Text(code) }, onClick = { subdivision = code; provinceMenu = false }) }
                        }
                    }
                }
                ConsentCheck("I am 18 or older.", age) { age = it }
                ConsentCheck("I have read and acknowledge the biometric notice.", notice) { notice = it }
                ConsentCheck("I will use Face Setup only for my own face.", ownFace) { ownFace = it }
                PrimaryButton(
                    "I Agree & Continue",
                    onClick = { onAccept(country, if (country == "IN") "" else subdivision, age, notice, ownFace) },
                    modifier = Modifier.padding(top = 8.dp),
                    enabled = age && notice && ownFace,
                )
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun ConsentCheck(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FaceSetupScreen(
    state: AppUiState,
    onCapture: (ByteArray) -> Unit,
    onReset: () -> Unit,
    onComplete: () -> Unit,
    onExit: () -> Unit,
) {
    var scanOpen by rememberSaveable { mutableStateOf(false) }

    if (scanOpen) {
        BackHandler { scanOpen = false }
        Box(Modifier.fillMaxSize()) {
            GuidedFaceEnrollmentCamera(
                captures = state.faceCaptures,
                onCapture = onCapture,
                onReset = onReset,
                onComplete = onComplete,
            )
            TextButton(
                onClick = { scanOpen = false },
                modifier = Modifier.align(Alignment.TopStart).padding(top = 8.dp, start = 6.dp),
            ) {
                Text("✕", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }
        return
    }

    BackHandler { onExit() }
    BrandBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(42.dp))
            BrandMark(66)
            Text(
                if (state.user?.hasFaceProfile == true) "Update Your Face" else "Set Up Your Face",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "Face Setup enables SnapLoop to find photos of you on participating Event members' phones.",
                textAlign = TextAlign.Center,
                color = Color(0xFF66636C),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )
            PremiumCard(Modifier.padding(top = 6.dp)) {
                BrandMark(92)
                Text(
                    if (state.user?.hasFaceProfile == true) "Face Setup Active" else "Five guided angles create your private face template.",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                )
            }
            PrimaryButton("◎  Selfie Scan", onClick = { onReset(); scanOpen = true }, modifier = Modifier.padding(top = 18.dp))
            if (state.faceSetupMode == FaceSetupMode.INITIAL_GATE && state.user?.hasFaceProfile != true) {
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp), shape = RoundedCornerShape(18.dp)) {
                    Text("Skip for now", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onExit, modifier = Modifier.padding(top = 10.dp)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun MainTabs(state: AppUiState, coordinator: AppCoordinator) {
    state.pendingInvite?.let { event ->
        InvitationReviewScreen(event, coordinator)
        return
    }
    if (state.selectedEvent != null) {
        EventScreen(state, coordinator)
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Color.White.copy(alpha = 0.97f)) {
                listOf("⌂\nHome", "▦\nGallery", "●\nYou").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {},
                        label = { Text(label, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
                    )
                }
            }
        },
    ) { inset ->
        BrandBackground(Modifier.padding(inset)) {
            when (tab) {
                0 -> HomeScreen(state, coordinator)
                1 -> GalleryScreen(state, coordinator)
                else -> YouScreen(state, coordinator)
            }
        }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, coordinator: AppCoordinator) {
    var createOpen by remember { mutableStateOf(false) }
    var joinOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Hi, ${state.user?.displayName ?: "there"} 👋", fontSize = 30.sp, fontWeight = FontWeight.Black, color = SnapColors.Ink)
                Text(
                    "Photos your friends took of you on their phones, brought to your phone automatically.",
                    color = Color(0xFF66636C),
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            BrandMark(46)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientActionCard("＋", "Create Event", "Trip, party, family & more", Modifier.weight(1f)) { createOpen = true }
            GradientActionCard("●●", "Join Event", "Code, link or QR", Modifier.weight(1f)) { joinOpen = true }
        }

        SectionTitle("Your Events")
        if (state.events.filter { it.status != EventStatus.deletedByOrganizer }.isEmpty()) {
            PremiumCard(Modifier.padding(horizontal = 18.dp)) {
                Text("▧", fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("No Events yet", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Create an Event, or join one with a code, link or QR.", textAlign = TextAlign.Center, color = Color(0xFF66636C), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        } else {
            state.events.filter { it.status != EventStatus.deletedByOrganizer }.forEach { event ->
                EventCard(event, Modifier.padding(horizontal = 18.dp)) { coordinator.openEvent(event) }
            }
        }

        if (state.events.any { it.status == EventStatus.deletedByOrganizer }) {
            SectionTitle("Deleted")
            state.events.filter { it.status == EventStatus.deletedByOrganizer }.forEach { event ->
                EventCard(event, Modifier.padding(horizontal = 18.dp)) { coordinator.openEvent(event) }
            }
        }

        TextButton(onClick = coordinator::refreshEvents, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Refresh Events", color = SnapColors.Coral, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (createOpen) {
        CreateEventPanel(
            hasFaceProfile = state.user?.hasFaceProfile == true,
            onNeedFaceSetup = {
                createOpen = false
                coordinator.openFaceSetupFromMain()
            },
            onDismiss = { createOpen = false },
            onCreate = { name, category, location, start, end ->
                createOpen = false
                coordinator.createEvent(name, category, location, start, end)
            },
        )
    }
    if (joinOpen) {
        JoinEventPanel(
            onDismiss = { joinOpen = false },
            onResolve = {
                joinOpen = false
                coordinator.resolveJoinInput(it)
            },
        )
    }
}

@Composable
private fun InvitationReviewScreen(event: SnapEvent, coordinator: AppCoordinator) {
    BackHandler { coordinator.dismissPendingInvite() }
    BrandBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = coordinator::dismissPendingInvite) { Text("Cancel") }
                Text("Invitation", fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(64.dp))
            }
            Spacer(Modifier.height(38.dp))
            BrandMark(74)
            Text("You're invited to", color = Color(0xFF66636C), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
            Text(event.name, fontSize = 31.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            PremiumCard(Modifier.padding(top = 22.dp)) {
                Text("▣  ${formatEventRange(event)}", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("SnapLoop scans only photos taken during these Event dates.", textAlign = TextAlign.Center, color = Color(0xFF66636C), modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                event.locationName?.let { Text("⌖  $it", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) }
            }
            Text(
                "Join this Event to get your photos found on other participating members' phones.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 22.dp),
            )
            PrimaryButton("✓  Join Event", coordinator::confirmPendingInvite)
            TextButton(onClick = coordinator::dismissPendingInvite) { Text("Not now") }
        }
    }
}

private enum class EventPage { DASHBOARD, PHOTOS, SCAN, MEMBERS, INVITE, EDIT }

@Composable
private fun EventScreen(state: AppUiState, coordinator: AppCoordinator) {
    val event = state.selectedEvent ?: return
    var page by rememberSaveable(event.id) { mutableStateOf(EventPage.DASHBOARD) }
    BackHandler {
        if (page == EventPage.DASHBOARD) coordinator.closeEvent() else page = EventPage.DASHBOARD
    }

    when (page) {
        EventPage.PHOTOS -> EventPhotosPage(state, onBack = { page = EventPage.DASHBOARD }, coordinator = coordinator)
        EventPage.SCAN -> EventScanPage(state, onBack = { page = EventPage.DASHBOARD }, coordinator = coordinator)
        EventPage.MEMBERS -> MembersPage(state, onBack = { page = EventPage.DASHBOARD }, onInvite = { page = EventPage.INVITE }, coordinator = coordinator)
        EventPage.INVITE -> InvitePage(state, onBack = { page = EventPage.DASHBOARD })
        EventPage.EDIT -> EditEventPage(event, onBack = { page = EventPage.DASHBOARD }, coordinator = coordinator)
        EventPage.DASHBOARD -> EventDashboardPage(state, coordinator) { page = it }
    }
}

@Composable
private fun EventDashboardPage(state: AppUiState, coordinator: AppCoordinator, navigate: (EventPage) -> Unit) {
    val event = state.selectedEvent ?: return
    val uid = state.user?.id
    val role = currentRole(event, state.members, uid)
    BrandBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            TextButton(onClick = coordinator::closeEvent, modifier = Modifier.padding(horizontal = 10.dp)) { Text("‹ Back") }
            EventHero(event, role)
            PremiumCard(Modifier.padding(horizontal = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("◉", fontSize = 30.sp, color = if (state.members.firstOrNull { it.userId == uid }?.sharingEnabled == true) Color(0xFF008F83) else Color.Gray)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Photo Scan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (state.members.firstOrNull { it.userId == uid }?.sharingEnabled == true) "SnapLoop is ready to check this Event for new photos" else "Photo sharing is turned off for this Event",
                            color = Color(0xFF66636C),
                            fontSize = 13.sp,
                        )
                    }
                    Text(if (state.members.firstOrNull { it.userId == uid }?.sharingEnabled == true) "Ready" else "Paused", fontWeight = FontWeight.Bold)
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientActionCard("▧", "My Photos", "${state.photos.size} found of you", Modifier.weight(1f)) { navigate(EventPage.PHOTOS) }
                GradientActionCard("▦", "Scan Photos", "Check New Event Photos", Modifier.weight(1f)) { navigate(EventPage.SCAN) }
            }

            PremiumCard(Modifier.padding(horizontal = 18.dp).clickable { navigate(EventPage.MEMBERS) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("●●●", color = SnapColors.Lilac, fontWeight = FontWeight.Black)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Event Members", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${state.members.size} member${if (state.members.size == 1) "" else "s"}", color = Color(0xFF66636C))
                    }
                    Text("View all ›", color = SnapColors.Coral, fontWeight = FontWeight.Bold)
                }
            }

            if (role == EventMember.Role.organizer || role == EventMember.Role.admin) {
                PremiumCard(Modifier.padding(horizontal = 18.dp).clickable { navigate(EventPage.INVITE) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("＋", fontSize = 28.sp, color = SnapColors.Coral)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Invite People", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Share code, link, QR or Android share sheet", color = Color(0xFF66636C), fontSize = 13.sp)
                        }
                        Text("›", fontSize = 24.sp)
                    }
                }
            }

            if (role == EventMember.Role.organizer || role == EventMember.Role.admin) {
                PremiumCard(Modifier.padding(horizontal = 18.dp)) {
                    Text(if (role == EventMember.Role.admin) "Admin Controls" else "Organizer Controls", fontWeight = FontWeight.Black)
                    if (event.status == EventStatus.active) {
                        TextButton(onClick = { navigate(EventPage.EDIT) }) { Text("✎  Edit Event") }
                        TextButton(onClick = coordinator::endSelectedEvent) { Text("■  End Event", color = Color.Red) }
                    }
                    if (role == EventMember.Role.organizer) {
                        if (event.status == EventStatus.endedByOrganizer) {
                            TextButton(onClick = coordinator::reopenSelectedEvent) { Text("↻  Reopen Event") }
                        }
                        if (event.status == EventStatus.deletedByOrganizer) {
                            TextButton(onClick = coordinator::restoreSelectedEvent) { Text("↶  Restore Event") }
                        } else {
                            TextButton(onClick = coordinator::moveSelectedEventToDeleted) { Text("⌫  Move to Deleted", color = Color.Red) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EventHero(event: SnapEvent, role: EventMember.Role?) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(230.dp)
            .background(brandGradient(), RoundedCornerShape(30.dp)).padding(20.dp),
    ) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(eventStatusLabel(event), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(event.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp))
            Text("▣  ${formatEventRange(event)}", color = Color.White.copy(alpha = 0.94f), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            role?.let { Text(it.name.uppercase(Locale.US), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun EventPhotosPage(state: AppUiState, onBack: () -> Unit, coordinator: AppCoordinator) {
    BrandBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SubpageHeader("My Photos", onBack)
            InsightBanner(state.photos.size.toString(), if (state.photos.size == 1) "photo of you found in this Event" else "photos of you found in this Event")
            if (state.photos.isEmpty()) {
                PremiumCard {
                    Text("No photos of you yet", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text("Scan Event Photos, or ask other members to scan their Event photos.", textAlign = TextAlign.Center, color = Color(0xFF66636C), modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            } else {
                state.photos.forEach { match -> MatchCard(match, coordinator::dismissPhoto) }
            }
            OutlinedButton(onClick = coordinator::refreshPhotos, modifier = Modifier.fillMaxWidth()) { Text("Refresh Gallery") }
        }
    }
}

@Composable
private fun EventScanPage(state: AppUiState, onBack: () -> Unit, coordinator: AppCoordinator) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val allowed = result.values.any { it }
        if (allowed) coordinator.scanSelectedEvent()
    }
    BrandBackground {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SubpageHeader("Scan Photos", onBack)
            Spacer(Modifier.height(26.dp))
            PremiumCard {
                Text("▦", fontSize = 54.sp, color = SnapColors.Lilac, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Scan Event Photos", fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Only photos within ${state.selectedEvent?.let(::formatEventRange).orEmpty()} are considered.", textAlign = TextAlign.Center, color = Color(0xFF66636C), modifier = Modifier.align(Alignment.CenterHorizontally))
                state.scanProgress?.let { progress ->
                    Text("Scanning ${progress.checked} / ${progress.total} • ${progress.published} matches shared", textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.scanResult?.let { result ->
                    Text("Scan complete: ${result.checked} checked, ${result.published} published, ${result.remaining} remaining.", textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                PrimaryButton(
                    if (state.scanProgress == null) "✦  Start Scan" else "Scanning…",
                    onClick = {
                        val permissions = photoPermissions()
                        if (permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                            coordinator.scanSelectedEvent()
                        } else {
                            permissionLauncher.launch(permissions)
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    enabled = state.scanProgress == null,
                )
            }
        }
    }
}

@Composable
private fun MembersPage(state: AppUiState, onBack: () -> Unit, onInvite: () -> Unit, coordinator: AppCoordinator) {
    val uid = state.user?.id
    val event = state.selectedEvent ?: return
    val me = state.members.firstOrNull { it.userId == uid }
    val role = currentRole(event, state.members, uid)
    var leaveConfirm by remember { mutableStateOf(false) }

    BrandBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SubpageHeader("Members", onBack)
            PremiumCard {
                Text("Your sharing", fontWeight = FontWeight.Black, fontSize = 19.sp)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Share matched pictures from my phone in this Event", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(checked = me?.sharingEnabled == true, onCheckedChange = coordinator::setSharing, enabled = me != null)
                }
                Text(
                    if (me?.sharingEnabled == true) "Matched photos from your phone can be shared with the people they match." else "Photo sharing from this phone is off for this Event.",
                    color = Color(0xFF66636C),
                    fontSize = 13.sp,
                )
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show my own matched pictures from this phone in my Gallery", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(
                        checked = state.includeOwnMatches,
                        onCheckedChange = coordinator::setIncludeOwnMatches,
                        enabled = me?.sharingEnabled == true && state.user?.hasFaceProfile == true,
                    )
                }
                if (state.user?.hasFaceProfile != true) {
                    Text("Set up your face to see your own photo matches.", color = SnapColors.Lilac, fontSize = 13.sp)
                }
                if (role == EventMember.Role.organizer || role == EventMember.Role.admin) {
                    PrimaryButton("＋  Invite People", onInvite, Modifier.padding(top = 12.dp))
                }
                if (role != EventMember.Role.organizer) {
                    TextButton(onClick = { leaveConfirm = true }) { Text("Leave Event", color = Color.Red) }
                }
            }

            Text("Event Members", fontSize = 21.sp, fontWeight = FontWeight.Black)
            PremiumCard {
                state.members.forEachIndexed { index, member ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(brandGradient(), CircleShape), contentAlignment = Alignment.Center) {
                            Text((member.displayName ?: "•").take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                (member.displayName ?: "Event member") + if (member.userId == uid) " (You)" else "",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(member.role.name.replaceFirstChar { it.uppercase() }, color = Color(0xFF66636C), fontSize = 13.sp)
                        }
                        if (member.role == EventMember.Role.organizer) Text("Organizer", color = SnapColors.Coral, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        else if (member.role == EventMember.Role.admin) Text("Admin", color = SnapColors.Lilac, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    if (index != state.members.lastIndex) HorizontalDivider()
                }
            }
        }
    }

    if (leaveConfirm) {
        AlertDialog(
            onDismissRequest = { leaveConfirm = false },
            title = { Text("Leave this Event?") },
            text = { Text("Your membership will be removed from this Event.") },
            confirmButton = {
                TextButton(onClick = { leaveConfirm = false; coordinator.leaveSelectedEvent() }) { Text("Leave", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { leaveConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InvitePage(state: AppUiState, onBack: () -> Unit) {
    val event = state.selectedEvent ?: return
    val context = LocalContext.current
    val inviteUrl = DeepLinkParser.inviteUrl(event.inviteToken)
    val shareText = DeepLinkParser.shareText(event.name, state.user?.displayName, event.inviteToken)
    var copied by remember { mutableStateOf<String?>(null) }

    BrandBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SubpageHeader("Invite", onBack)
            BrandMark(64)
            Text("Invite people to ${event.name}", fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text("Anyone with the invite can open the Event, sign in, and choose whether to join.", textAlign = TextAlign.Center, color = Color(0xFF66636C))
            PrimaryButton("↗  Share Invite", onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, "Share SnapLoop Invite"))
            })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { copyText(context, DeepLinkParser.formatCode(event.joinCode)); copied = "Code copied" }, modifier = Modifier.weight(1f)) { Text("Copy Code") }
                OutlinedButton(onClick = { copyText(context, inviteUrl); copied = "Link copied" }, modifier = Modifier.weight(1f)) { Text("Copy Link") }
            }
            copied?.let { Text("✓ $it", color = Color(0xFF008F61), fontWeight = FontWeight.Bold) }
            PremiumCard {
                Text("Scan to join", fontSize = 19.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
                QrCodeImage(inviteUrl, Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp).size(220.dp).clip(RoundedCornerShape(18.dp)))
                Text(DeepLinkParser.formatCode(event.joinCode), fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
                Text("Event code", color = Color(0xFF66636C), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun EditEventPage(event: SnapEvent, onBack: () -> Unit, coordinator: AppCoordinator) {
    val zone = eventZone(event)
    EventForm(
        title = "Edit Event",
        initialName = event.name,
        initialCategory = event.category,
        initialLocation = event.locationName.orEmpty(),
        initialStart = event.startsAt.atZone(zone).toLocalDate(),
        initialEnd = event.endsAt.atZone(zone).toLocalDate(),
        submitLabel = "Save Changes",
        onDismiss = onBack,
        onSubmit = { name, category, location, start, end ->
            coordinator.editSelectedEvent(name, category, location, start, end)
            onBack()
        },
    )
}

@Composable
private fun GalleryScreen(state: AppUiState, coordinator: AppCoordinator) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Gallery", fontSize = 34.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 18.dp))
        InsightBanner(
            state.allPhotos.size.toString(),
            if (state.allPhotos.size == 1) "photo of you found across all Events" else "photos of you found across all Events",
            Modifier.padding(horizontal = 18.dp),
        )
        if (state.allPhotos.isEmpty()) {
            PremiumCard(Modifier.padding(horizontal = 18.dp)) {
                Text("No photos of you yet", fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("SnapLoop checks eligible Events for matched photos. You can also use Scan Photos from an Event at any time.", textAlign = TextAlign.Center, color = Color(0xFF66636C), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        } else {
            state.allPhotos.forEach { match ->
                MatchCard(match, coordinator::dismissPhoto, Modifier.padding(horizontal = 18.dp))
            }
        }
        TextButton(onClick = coordinator::refreshAllPhotos, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Refresh Gallery", color = SnapColors.Coral, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MatchCard(match: PhotoMatch, onNotMe: (PhotoMatch) -> Unit, modifier: Modifier = Modifier) {
    PremiumCard(modifier) {
        MatchThumbnail(match)
        Text("Matched photo", fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
        Text(formatMillis(match.capturedAtMillis), color = Color(0xFF66636C), fontSize = 13.sp)
        TextButton(onClick = { onNotMe(match) }, modifier = Modifier.align(Alignment.End)) { Text("Not Me", color = SnapColors.Coral) }
    }
}

@Composable
private fun MatchThumbnail(match: PhotoMatch) {
    val path = match.thumbnailPath
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = path) {
        value = if (path.isNullOrBlank()) null else runCatching {
            val bytes = FirebaseStorage.getInstance().reference.child(path).getBytes(6L * 1024L * 1024L).await()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Matched Event photo",
            modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(Modifier.fillMaxWidth().height(120.dp).background(softGradient(), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Text("▧", fontSize = 42.sp, color = SnapColors.Lilac)
        }
    }
}

@Composable
private fun YouScreen(state: AppUiState, coordinator: AppCoordinator) {
    val context = LocalContext.current
    var editName by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var signOutConfirm by remember { mutableStateOf(false) }
    var replayConfirm by remember { mutableStateOf(false) }
    var photoPermissionGranted by remember { mutableStateOf(hasPhotoPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        photoPermissionGranted = hasPhotoPermission(context)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("You", fontSize = 34.sp, fontWeight = FontWeight.Black)
        PremiumCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(66.dp).background(softGradient(), CircleShape), contentAlignment = Alignment.Center) {
                    Text((state.user?.displayName ?: "?").take(1).uppercase(), fontSize = 25.sp, fontWeight = FontWeight.Black, color = SnapColors.Lilac)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(state.user?.displayName ?: "Add your name", fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text(state.user?.phoneNumber.orEmpty(), color = Color(0xFF66636C), fontSize = 13.sp)
                    Text(if (state.user?.hasFaceProfile == true) "✓ Face Setup Active" else "Face Setup not completed", color = if (state.user?.hasFaceProfile == true) Color(0xFF008F61) else Color(0xFF66636C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                BrandMark(34)
            }
        }
        SettingsCard(if (state.user?.displayName.isNullOrBlank()) "Add Your Name" else "Edit Your Name", "Name shown to people in your Events") { editName = true }
        SettingsCard(if (state.user?.hasFaceProfile == true) "Update Face Setup" else "Set Up Your Face", "Guided face scan and Face Setup controls") { coordinator.openFaceSetupFromMain() }

        PremiumCard {
            Text("Photo Access", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(if (photoPermissionGranted) "Photos access is enabled" else "Photo access is off or not yet granted", color = Color(0xFF66636C), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (!photoPermissionGranted) {
                PrimaryButton("Allow Photo Access", { permissionLauncher.launch(photoPermissions()) }, Modifier.padding(top = 10.dp))
            } else {
                OutlinedButton(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Manage Photo Access") }
            }
        }

        SettingsCard("Privacy & Data", "Face data, deletion and account controls") { privacy = true }
        SettingsCard("Replay Onboarding", "Review how Events, matching and permissions work") { replayConfirm = true }
        SettingsCard("Sign Out", "Sign out without deleting your account") { signOutConfirm = true }
        Spacer(Modifier.height(20.dp))
    }

    if (editName) {
        EditNameDialog(
            initial = state.user?.displayName.orEmpty(),
            onDismiss = { editName = false },
            onSave = { editName = false; coordinator.saveDisplayName(it) },
        )
    }
    if (privacy) {
        PrivacyDialog(
            onDismiss = { privacy = false },
            onWithdraw = { privacy = false; coordinator.withdrawBiometrics() },
            onDelete = { privacy = false; coordinator.deleteAccount() },
        )
    }
    if (signOutConfirm) {
        AlertDialog(
            onDismissRequest = { signOutConfirm = false },
            title = { Text("Sign out of SnapLoop?") },
            confirmButton = { TextButton(onClick = { signOutConfirm = false; coordinator.signOut() }) { Text("Sign Out", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { signOutConfirm = false }) { Text("Cancel") } },
        )
    }
    if (replayConfirm) {
        AlertDialog(
            onDismissRequest = { replayConfirm = false },
            title = { Text("Replay onboarding?") },
            text = { Text("Your account, Events, photos and Face Setup will not be changed.") },
            confirmButton = { TextButton(onClick = { replayConfirm = false; coordinator.replayOnboarding() }) { Text("Replay Onboarding") } },
            dismissButton = { TextButton(onClick = { replayConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CreateEventPanel(
    hasFaceProfile: Boolean,
    onNeedFaceSetup: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String, EventCategory, String?, LocalDate, LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    EventForm(
        title = "Create an Event",
        initialName = "",
        initialCategory = EventCategory.trip,
        initialLocation = "",
        initialStart = today,
        initialEnd = today.plusDays(3),
        submitLabel = "Create Event",
        onDismiss = onDismiss,
        onSubmit = onCreate,
        hasFaceProfile = hasFaceProfile,
        onNeedFaceSetup = onNeedFaceSetup,
    )
}

@Composable
private fun EventForm(
    title: String,
    initialName: String,
    initialCategory: EventCategory,
    initialLocation: String,
    initialStart: LocalDate,
    initialEnd: LocalDate,
    submitLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String, EventCategory, String?, LocalDate, LocalDate) -> Unit,
    hasFaceProfile: Boolean = true,
    onNeedFaceSetup: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var categoryMenu by remember { mutableStateOf(false) }
    var location by rememberSaveable { mutableStateOf(initialLocation) }
    var startsOn by remember { mutableStateOf(initialStart) }
    var endsOn by remember { mutableStateOf(initialEnd) }
    val today = LocalDate.now()
    val lower = today.minusDays(15)
    val upper = today.plusDays(15)
    val dateError = endsOn.isBefore(startsOn) || startsOn.isBefore(lower) || startsOn.isAfter(upper) || endsOn.isBefore(lower) || endsOn.isAfter(upper) || java.time.temporal.ChronoUnit.DAYS.between(startsOn, endsOn) > 15

    FullScreenDialog(onDismiss) {
        BrandBackground {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Text(if (title.startsWith("Create")) "New Event" else "Edit Event", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(64.dp))
                }
                BrandMark(58)
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
                if (title.startsWith("Create")) {
                    Text("Trip, party, family celebration, wedding — bring everyone's photos together.", textAlign = TextAlign.Center, color = Color(0xFF66636C), modifier = Modifier.padding(top = 6.dp))
                }
                PremiumCard(Modifier.padding(top = 18.dp)) {
                    Text("Event name", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                        placeholder = { Text("e.g. Banff Weekend") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        supportingText = { Text("${name.length}/20") },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Type", fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(categoryDisplayName(category)) }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            EventCategory.entries.forEach { item -> DropdownMenuItem(text = { Text(categoryDisplayName(item)) }, onClick = { category = item; categoryMenu = false }) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Location", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = location, onValueChange = { location = it.take(80) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), placeholder = { Text("Optional") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                }
                PremiumCard(Modifier.padding(top = 14.dp)) {
                    Text("▣  Event dates", fontWeight = FontWeight.Black)
                    DateField("Starts", startsOn, lower, upper) { newStart ->
                        startsOn = newStart
                        if (endsOn.isBefore(newStart)) endsOn = newStart
                        if (java.time.temporal.ChronoUnit.DAYS.between(newStart, endsOn) > 15) endsOn = newStart.plusDays(3).coerceAtMost(upper)
                    }
                    DateField("Ends", endsOn, startsOn, minOf(upper, startsOn.plusDays(15))) { endsOn = it }
                    Text("SnapLoop only considers photos taken within this Event's selected date range. Dates must stay within 15 days before or after today, and an Event can span at most 15 calendar days.", color = Color(0xFF66636C), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    if (dateError) Text("Choose a valid Event date range.", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                }
                if (!hasFaceProfile) {
                    PremiumCard(Modifier.padding(top = 14.dp)) {
                        Text("Complete Face Setup before creating an Event so SnapLoop can find your photos.", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                        PrimaryButton("Complete Face Setup", { onNeedFaceSetup?.invoke() }, Modifier.padding(top = 10.dp))
                    }
                } else {
                    PrimaryButton(
                        "✦  $submitLabel",
                        onClick = { onSubmit(name.trim(), category, location.trim().takeIf(String::isNotEmpty), startsOn, endsOn) },
                        modifier = Modifier.padding(top = 18.dp),
                        enabled = name.trim().isNotEmpty() && name.length <= 20 && !dateError,
                    )
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun DateField(label: String, value: LocalDate, minimum: LocalDate, maximum: LocalDate, onValue: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val picked = LocalDate.of(year, month + 1, day)
                    if (!picked.isBefore(minimum) && !picked.isAfter(maximum)) onValue(picked)
                },
                value.year,
                value.monthValue - 1,
                value.dayOfMonth,
            ).apply {
                datePicker.minDate = minimum.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                datePicker.maxDate = maximum.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            }.show()
        }) { Text(value.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))) }
    }
}

@Composable
private fun JoinEventPanel(onDismiss: () -> Unit, onResolve: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var scanQr by rememberSaveable { mutableStateOf(false) }
    if (scanQr) {
        FullScreenDialog({ scanQr = false }) {
            QrCodeScannerScreen(
                onResult = { value -> scanQr = false; onResolve(value) },
                onCancel = { scanQr = false },
            )
        }
        return
    }
    FullScreenDialog(onDismiss) {
        BrandBackground {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Text("Join Event", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(64.dp))
                }
                Spacer(Modifier.height(48.dp))
                BrandMark(62)
                Text("Join an Event", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 18.dp))
                Text("Enter an Event code or invite link, or scan the Event QR code.", textAlign = TextAlign.Center, color = Color(0xFF66636C), modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(512) },
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    placeholder = { Text("Event code or invite link") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                PrimaryButton("→  Continue", onClick = { onResolve(text) }, modifier = Modifier.padding(top = 14.dp), enabled = text.trim().isNotEmpty())
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f)); Text("  or  ", color = Color.Gray); HorizontalDivider(Modifier.weight(1f))
                }
                OutlinedButton(onClick = { scanQr = true }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
                    Text("▣  Scan QR Code", color = SnapColors.Lilac, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: SnapEvent, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).background(brandGradient(), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Text(categorySymbol(event.category), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(event.name, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(formatEventRange(event), color = Color(0xFF66636C), fontSize = 13.sp)
                Text(eventStatusLabel(event), color = SnapColors.Coral, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 3.dp))
            }
            Text("›", fontSize = 25.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun GradientActionCard(icon: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(Modifier.fillMaxWidth().height(154.dp).background(brandGradient()).padding(16.dp)) {
            Text(icon, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, onClick: () -> Unit) {
    PremiumCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(softGradient(), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text("●", color = SnapColors.Lilac) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color(0xFF66636C), fontSize = 12.sp)
            }
            Text("›", fontSize = 24.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun InsightBanner(value: String, label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().background(brandGradient(), RoundedCornerShape(24.dp)).padding(18.dp)) {
        Column {
            Text(value, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(label, color = Color.White.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SubpageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(Modifier.width(64.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = SnapColors.Ink, modifier = Modifier.padding(horizontal = 18.dp))
}

@Composable
private fun BrandWordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BrandMark(58)
        Text(
            "SnapLoop",
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            style = TextStyle(brush = brandGradient()),
        )
    }
}

@Composable
private fun BrandMark(size: Int) {
    Box(
        Modifier.size(size.dp).background(brandGradient(), RoundedCornerShape((size * 0.24f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("S", color = Color.White, fontSize = (size * 0.52f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            Modifier.fillMaxSize().background(if (enabled) brandGradient() else Brush.linearGradient(listOf(Color.LightGray, Color.Gray))),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

@Composable
private fun PremiumCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun BrandBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFFFFBFD), Color(0xFFFFF6FC), Color(0xFFFFFCFF))),
        ),
        content = content,
    )
}

private fun brandGradient() = Brush.linearGradient(listOf(SnapColors.Orange, SnapColors.Coral, SnapColors.HotPink, SnapColors.Lilac, SnapColors.Blue))
private fun softGradient() = Brush.linearGradient(listOf(Color(0xFFFFE4DC), Color(0xFFF4DEFF), Color(0xFFDDE6FF)))

@Composable
private fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) { content() }
    }
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
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete Permanently", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy & Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Face matching is explicit-consent only and scanning is Event-scoped.")
                OutlinedButton(onClick = onWithdraw, modifier = Modifier.fillMaxWidth()) { Text("Withdraw Biometric Consent") }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete Account", color = Color.Red) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private fun categoryDisplayName(category: EventCategory): String = when (category) {
    EventCategory.trip -> "Trip"
    EventCategory.wedding -> "Wedding"
    EventCategory.party -> "Party"
    EventCategory.birthday -> "Birthday"
    EventCategory.conference -> "Conference"
    EventCategory.family -> "Family"
    EventCategory.sports -> "Sports"
    EventCategory.other -> "Other"
}

private fun categorySymbol(category: EventCategory): String = when (category) {
    EventCategory.trip -> "✈"
    EventCategory.wedding -> "♥"
    EventCategory.party -> "✦"
    EventCategory.birthday -> "★"
    EventCategory.conference -> "▣"
    EventCategory.family -> "●●"
    EventCategory.sports -> "◎"
    EventCategory.other -> "▧"
}

private fun eventStatusLabel(event: SnapEvent): String {
    if (event.status == EventStatus.endedByOrganizer) return "ENDED"
    if (event.status == EventStatus.deletedByOrganizer) return "DELETED"
    if (event.status == EventStatus.expired) return "COMPLETED"
    val now = Instant.now()
    return when {
        now.isBefore(event.startsAt) -> "UPCOMING"
        !now.isAfter(event.endsAt) -> "LIVE"
        else -> "PHOTO WINDOW"
    }
}

private fun currentRole(event: SnapEvent, members: List<EventMember>, uid: String?): EventMember.Role? {
    if (uid == null) return null
    if (event.creatorUserId == uid) return EventMember.Role.organizer
    return members.firstOrNull { it.userId == uid }?.role
}

private fun eventZone(event: SnapEvent): ZoneId = runCatching { ZoneId.of(event.photoWindowTimeZoneId ?: ZoneId.systemDefault().id) }.getOrDefault(ZoneId.systemDefault())

private fun formatEventRange(event: SnapEvent): String {
    val zone = eventZone(event)
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    val start = event.startsAt.atZone(zone).toLocalDate().format(formatter)
    val end = event.endsAt.atZone(zone).toLocalDate().format(formatter)
    return if (start == end) start else "$start – $end"
}

private fun formatMillis(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))

private fun copyText(context: Context, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("SnapLoop", value))
}

private fun photoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasPhotoPermission(context: Context): Boolean = photoPermissions().any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

private fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}
