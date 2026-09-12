package com.snaploop.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.snaploop.app.BuildConfig
import com.snaploop.app.data.FirebaseBiometricConsentStore
import com.snaploop.app.model.BiometricConsentRecord
import com.snaploop.app.model.BiometricJurisdiction
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.launch

/** Privacy & Data wording and hierarchy aligned to the production iOS source of truth. */
@Composable
internal fun ParityPrivacyScreen(
    onDismiss: () -> Unit,
    onWithdraw: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val consentStore = remember { FirebaseBiometricConsentStore() }
    val scope = rememberCoroutineScope()
    var confirmWithdraw by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var licensesOpen by remember { mutableStateOf(false) }
    var consentDetailsOpen by remember { mutableStateOf(false) }
    var consentActive by remember { mutableStateOf<Boolean?>(null) }
    var consentError by remember { mutableStateOf<String?>(null) }

    suspend fun refreshConsent() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        consentActive = if (userId == null) {
            false
        } else {
            runCatching { consentStore.load(userId)?.isActive == true }.getOrDefault(false)
        }
    }

    LaunchedEffect(Unit) { refreshConsent() }

    val consentPresentation = PrivacyConsentParityPolicy.presentation(consentActive)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            ParityBrandBackground {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("‹ Back") }
                        Text(
                            "Privacy & Data",
                            modifier = Modifier.weight(1f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = SnapColors.Ink,
                        )
                    }

                    PrivacyCard {
                        Text("You stay in control", fontSize = 20.sp, fontWeight = FontWeight.Black, color = SnapColors.Ink)
                        Text(
                            "SnapLoop never uploads your entire photo library. Photo matching runs on participating devices and is limited to the selected Event date range. Your Face Setup selfie/reference images stay on this device; SnapLoop stores numerical face-template metadata only for the Face Match purpose you expressly consent to.",
                            color = SnapColors.Secondary,
                        )
                    }

                    PrivacyCard {
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = consentActive != null) {
                                consentError = null
                                consentDetailsOpen = true
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .background(
                                        if (consentActive == true) Color(0xFF2EAD63).copy(alpha = 0.12f)
                                        else SnapColors.Lilac.copy(alpha = 0.12f),
                                        RoundedCornerShape(11.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (consentActive == true) "✓" else "◐",
                                    color = if (consentActive == true) Color(0xFF2EAD63) else SnapColors.Lilac,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Face Match Consent", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SnapColors.Ink)
                                Text(consentPresentation.statusText, color = SnapColors.Secondary, fontSize = 12.sp)
                            }
                            Text("›", color = SnapColors.Secondary, fontSize = 22.sp)
                        }
                    }

                    PrivacyCard {
                        Text("Data retention", fontSize = 19.sp, fontWeight = FontWeight.Black, color = SnapColors.Ink)
                        Text(
                            "Face Match consent and the account-level numerical face template expire after 12 months without biometric activity. They are removed sooner when you withdraw consent, delete Face Setup, or delete your account. Event-related cloud data, including matched photo previews, is deleted within 15 days after an Event ends or is manually deleted.",
                            color = SnapColors.Secondary,
                        )
                    }

                    PrivacyCard {
                        Text("Legal resources", fontSize = 19.sp, fontWeight = FontWeight.Black, color = SnapColors.Ink)
                        PrivacyLink("Privacy Policy") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://getsnaploop.web.app/privacy.html")))
                        }
                        HorizontalDivider()
                        PrivacyLink("Face Match Biometric Notice") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://getsnaploop.web.app/privacy.html#face-match-notice")))
                        }
                        HorizontalDivider()
                        PrivacyLink("Open Source Licenses") { licensesOpen = true }
                    }

                    PrivacyCard {
                        Text("Delete Account", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.Red)
                        Text(
                            "Deletes your account, face data, Event memberships, and photo previews sourced from this account.",
                            color = SnapColors.Secondary,
                        )
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Delete SnapLoop Account", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (consentDetailsOpen && consentActive != null) {
        FaceMatchConsentReviewDialog(
            consentActive = consentActive == true,
            errorMessage = consentError,
            onDismiss = {
                consentDetailsOpen = false
                consentError = null
            },
            onRequestWithdraw = { confirmWithdraw = true },
            onAccept = { jurisdiction, ageConfirmed, noticeConfirmed, onComplete ->
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    consentError = "Your signed-in account could not be verified. Please try again."
                    onComplete(false)
                } else {
                    scope.launch {
                        val saved = runCatching {
                            consentStore.save(
                                BiometricConsentRecord(
                                    userId = userId,
                                    acceptedAt = Instant.now(),
                                    jurisdictionCountry = jurisdiction.normalizedCountry,
                                    jurisdictionSubdivision = jurisdiction.normalizedSubdivision,
                                    appVersion = BuildConfig.VERSION_NAME,
                                    locale = Locale.getDefault().toLanguageTag(),
                                    age18Attested = ageConfirmed,
                                    noticeAcknowledged = noticeConfirmed,
                                    ownFaceAttested = true,
                                )
                            )
                            consentStore.load(userId)?.isActive == true
                        }.getOrElse { false }
                        if (saved) {
                            consentActive = true
                            consentError = null
                            consentDetailsOpen = false
                        } else {
                            consentError = "Consent could not be saved by the secure Face Match service. Please try again."
                        }
                        onComplete(saved)
                    }
                }
            },
        )
    }

    if (confirmWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text("Withdraw Face Match Consent?") },
            text = {
                Text(
                    "This deletes your active Face Setup and related face-matching data and stops Face Match until you consent and set it up again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWithdraw = false
                    consentDetailsOpen = false
                    onWithdraw()
                }) {
                    Text("Withdraw Consent & Delete Face Setup", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmWithdraw = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your SnapLoop account?") },
            text = {
                Text(
                    "This permanently removes your account, face data, Event memberships, and photo previews sourced from this account. It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete Account", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (licensesOpen) {
        OpenSourceLicensesDialog(
            onDismiss = { licensesOpen = false },
            onOpenApacheLicense = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apache.org/licenses/LICENSE-2.0")),
                )
            },
        )
    }
}

@Composable
private fun FaceMatchConsentReviewDialog(
    consentActive: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRequestWithdraw: () -> Unit,
    onAccept: (BiometricJurisdiction, Boolean, Boolean, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val localeCountry = remember { Locale.getDefault().country.uppercase() }
    var country by rememberSaveable { mutableStateOf(if (localeCountry == "IN") "IN" else "CA") }
    var subdivision by rememberSaveable { mutableStateOf("ON") }
    var countryMenuOpen by remember { mutableStateOf(false) }
    var subdivisionMenuOpen by remember { mutableStateOf(false) }
    var ageConfirmed by rememberSaveable { mutableStateOf(false) }
    var noticeConfirmed by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val jurisdiction = BiometricJurisdiction(country, if (country == "IN") "" else subdivision)
    val canAccept = PrivacyConsentDetailPolicy.canAccept(jurisdiction, ageConfirmed, noticeConfirmed, saving)
    val subdivisions = listOf("AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT")

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            ParityBrandBackground {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss, enabled = !saving) { Text("‹ Back") }
                        Text("Privacy", modifier = Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ParityBrandMark(50)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Face Match Consent", fontSize = 20.sp, fontWeight = FontWeight.Black, color = SnapColors.Ink)
                            if (consentActive) Text("Consent is active", color = SnapColors.Secondary, fontSize = 12.sp)
                        }
                    }

                    PrivacyCard {
                        ConsentDetailPoint(
                            "Purpose & storage",
                            "Your selfie stays on this Android device. SnapLoop stores a numerical face template in Firebase only to find photos of you in Events you join.",
                        )
                        HorizontalDivider()
                        ConsentDetailPoint(
                            "Event matching",
                            "Your template may be sent only to authenticated Event-member devices for on-device matching. Unmatched candidate faces are temporary and are not uploaded or saved.",
                        )
                        HorizontalDivider()
                        ConsentDetailPoint(
                            "Limits",
                            "No sale, ads, account authentication, surveillance, stranger identification, sensitive-trait inference, analytics, or unrelated model training.",
                        )
                        HorizontalDivider()
                        ConsentDetailPoint(
                            "Retention & choice",
                            "Face Match is optional. Consent and your account-level template expire after 12 months without your Face Match activity; you can withdraw consent or delete Face Setup sooner.",
                        )
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://getsnaploop.web.app/privacy.html#face-match-notice")))
                            },
                            modifier = Modifier.align(Alignment.Start),
                        ) {
                            Text("Read full Face Match Notice", color = SnapColors.Lilac, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (!consentActive) {
                        PrivacyCard {
                            Text("Residence", fontWeight = FontWeight.Bold, color = SnapColors.Ink)
                            Box(Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { countryMenuOpen = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !saving,
                                ) {
                                    Text(if (country == "IN") "India" else "Canada")
                                }
                                DropdownMenu(countryMenuOpen, { countryMenuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Canada") },
                                        onClick = {
                                            country = "CA"
                                            subdivision = "ON"
                                            ageConfirmed = false
                                            countryMenuOpen = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("India") },
                                        onClick = {
                                            country = "IN"
                                            ageConfirmed = false
                                            countryMenuOpen = false
                                        },
                                    )
                                }
                            }
                            if (country == "CA") {
                                Box(Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { subdivisionMenuOpen = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !saving,
                                    ) {
                                        Text("Province or territory: ${PrivacyConsentDetailPolicy.canadianSubdivisionName(subdivision)}")
                                    }
                                    DropdownMenu(subdivisionMenuOpen, { subdivisionMenuOpen = false }) {
                                        subdivisions.forEach { code ->
                                            DropdownMenuItem(
                                                text = { Text(PrivacyConsentDetailPolicy.canadianSubdivisionName(code)) },
                                                onClick = {
                                                    subdivision = code
                                                    ageConfirmed = false
                                                    subdivisionMenuOpen = false
                                                },
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "Face Match is available in Canada except Quebec. Ontario is selected by default. No GPS or precise address is required.",
                                    color = SnapColors.Secondary,
                                    fontSize = 11.sp,
                                )
                            } else {
                                Text(
                                    "Face Match is available for residents of India. No GPS or precise address is required.",
                                    color = SnapColors.Secondary,
                                    fontSize = 11.sp,
                                )
                            }
                            if (!jurisdiction.isFaceMatchAvailable) {
                                Text("Face Match is not available in Quebec.", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        PrivacyCard {
                            Text("Confirm before continuing", fontWeight = FontWeight.Bold, color = SnapColors.Ink)
                            ConsentCheckboxRow(
                                checked = ageConfirmed,
                                enabled = !saving && jurisdiction.isFaceMatchAvailable,
                                text = PrivacyConsentDetailPolicy.residenceAttestation(jurisdiction),
                            ) { ageConfirmed = it }
                            HorizontalDivider()
                            ConsentCheckboxRow(
                                checked = noticeConfirmed,
                                enabled = !saving && jurisdiction.isFaceMatchAvailable,
                                text = "I read the Face Match Notice, confirm Face Setup will use my own face only, and expressly consent to the described biometric processing.",
                            ) { noticeConfirmed = it }
                        }
                    }

                    errorMessage?.let {
                        Text(it, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (consentActive) {
                        OutlinedButton(
                            onClick = onRequestWithdraw,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Withdraw Consent", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Done", color = SnapColors.Secondary)
                        }
                    } else {
                        ParityPrimaryButton(
                            text = if (saving) "Saving…" else "I Agree & Continue",
                            onClick = {
                                if (!canAccept) return@ParityPrimaryButton
                                saving = true
                                onAccept(jurisdiction, ageConfirmed, noticeConfirmed) { success ->
                                    saving = false
                                    if (success) ageConfirmed = false
                                }
                            },
                            enabled = canAccept,
                        )
                        TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                            Text("Not Now", color = SnapColors.Secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentDetailPoint(title: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = SnapColors.Ink, fontSize = 12.sp)
        Text(body, color = SnapColors.Secondary, fontSize = 11.sp)
    }
}

@Composable
private fun ConsentCheckboxRow(
    checked: Boolean,
    enabled: Boolean,
    text: String,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChecked(!checked) },
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = if (enabled) onChecked else null, enabled = enabled)
        Text(text, modifier = Modifier.weight(1f).padding(top = 10.dp), fontSize = 12.sp, color = SnapColors.Ink)
    }
}

@Composable
private fun PrivacyCard(content: @Composable ColumnScope.() -> Unit) {
    ParityPremiumCard {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = content,
        )
    }
}

@Composable
private fun PrivacyLink(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = SnapColors.Lilac, fontWeight = FontWeight.Bold)
        Text("›", color = SnapColors.Secondary, fontSize = 22.sp)
    }
}

@Composable
private fun OpenSourceLicensesDialog(
    onDismiss: () -> Unit,
    onOpenApacheLicense: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            ParityBrandBackground {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("‹ Back") }
                        Text("Open Source Licenses", fontSize = 22.sp, fontWeight = FontWeight.Black, color = SnapColors.Ink)
                    }
                    ParityPremiumCard {
                        Text("AuraFace-v1", fontSize = 18.sp, fontWeight = FontWeight.Black, color = SnapColors.Ink)
                        Text(
                            "SnapLoop uses an on-device ONNX Runtime build of AuraFace-v1 to generate numerical face embeddings. AuraFace-v1 is distributed under the Apache License, Version 2.0. SnapLoop's use of open-source face technology does not permit the model publisher to receive or process your Face Setup images.",
                            color = SnapColors.Secondary,
                        )
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(
                            "AuraFace-v1 · Apache License, Version 2.0",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = SnapColors.Secondary,
                        )
                        TextButton(onClick = onOpenApacheLicense, modifier = Modifier.align(Alignment.Start)) {
                            Text("Read Apache License 2.0", color = SnapColors.Lilac, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
