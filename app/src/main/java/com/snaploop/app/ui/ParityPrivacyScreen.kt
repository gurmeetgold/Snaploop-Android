package com.snaploop.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.snaploop.app.data.FirebaseBiometricConsentStore

/** Privacy & Data wording and hierarchy aligned to the production iOS source of truth. */
@Composable
internal fun ParityPrivacyScreen(
    onDismiss: () -> Unit,
    onWithdraw: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val consentStore = remember { FirebaseBiometricConsentStore() }
    var confirmWithdraw by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var licensesOpen by remember { mutableStateOf(false) }
    var consentActive by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        consentActive = if (userId == null) {
            false
        } else {
            runCatching { consentStore.load(userId)?.isActive == true }.getOrDefault(false)
        }
    }

    val consentPresentation = PrivacyConsentParityPolicy.presentation(consentActive)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            ParityBrandBackground {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
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
                        Text("Face Match Consent", fontSize = 19.sp, fontWeight = FontWeight.Black, color = SnapColors.Ink)
                        Text(consentPresentation.statusText, color = SnapColors.Secondary)
                        if (consentPresentation.canWithdraw) {
                            OutlinedButton(
                                onClick = { confirmWithdraw = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Withdraw Face Match Consent", color = SnapColors.Coral, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    PrivacyCard {
                        Text("Data retention", fontSize = 19.sp, fontWeight = FontWeight.Black, color = SnapColors.Ink)
                        Text(
                            "Face Match consent and the account-level numerical face template expire after 12 months without biometric activity. They are removed sooner when you withdraw consent, delete Face Setup, or delete your account.",
                            color = SnapColors.Secondary,
                        )
                        Text(
                            "Event-related cloud data, including matched photo previews, is deleted within 15 days after an Event ends or is manually deleted.",
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
                TextButton(onClick = { confirmWithdraw = false; onWithdraw() }) {
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
