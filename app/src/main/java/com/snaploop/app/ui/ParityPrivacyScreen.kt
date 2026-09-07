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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val PrivacyInk = Color(0xFF241F2A)
private val PrivacySecondary = Color(0xFF6B6670)
private val PrivacyCoral = Color(0xFFF05C68)
private val PrivacyViolet = Color(0xFF8E63F6)

/** Full-screen Privacy & Data surface matched to the iOS product wording and hierarchy. */
@Composable
internal fun ParityPrivacyScreen(
    onDismiss: () -> Unit,
    onWithdraw: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var confirmWithdraw by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFFFFFBFD), Color(0xFFFFF6FC), Color(0xFFFFFCFF))),
                ),
            ) {
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
                        )
                    }

                    PrivacyCard {
                        Text("You stay in control", fontSize = 20.sp, fontWeight = FontWeight.Black, color = PrivacyInk)
                        Text(
                            "SnapLoop does not upload your entire photo library. Photo matching runs on participating devices and only within the selected Event date range.",
                            color = PrivacySecondary,
                        )
                        Text(
                            "Your Face Setup photo stays on this device. SnapLoop stores a numerical face template so participating Event devices can find photos of you.",
                            color = PrivacySecondary,
                        )
                    }

                    PrivacyCard {
                        Text("Face Match Consent", fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text("Active · Review or withdraw at any time", color = PrivacySecondary)
                        OutlinedButton(
                            onClick = { confirmWithdraw = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Withdraw Face Match Consent", color = PrivacyCoral, fontWeight = FontWeight.Bold)
                        }
                    }

                    PrivacyCard {
                        Text("Data retention", fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text(
                            "Face Match data is retained for up to 12 months after your last use, unless you withdraw consent or delete your account sooner.",
                            color = PrivacySecondary,
                        )
                        Text(
                            "Event cloud data, including matched photo previews, is deleted within 15 days after an Event ends or when the organizer deletes the Event.",
                            color = PrivacySecondary,
                        )
                    }

                    PrivacyCard {
                        Text("Legal resources", fontSize = 19.sp, fontWeight = FontWeight.Black)
                        PrivacyLink("Privacy Policy") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://getsnaploop.web.app/privacy.html")))
                        }
                        HorizontalDivider()
                        PrivacyLink("Face Match Biometric Notice") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://getsnaploop.web.app/privacy.html#face-match-notice")))
                        }
                        HorizontalDivider()
                        Text(
                            "Open Source Licenses",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = PrivacyViolet,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    PrivacyCard {
                        Text("Delete account", fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text(
                            "Permanently delete your SnapLoop account and server-side data. This cannot be undone.",
                            color = PrivacySecondary,
                        )
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Delete Account", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (confirmWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text("Withdraw Face Match consent?") },
            text = {
                Text(
                    "SnapLoop will remove your Face Match template and stop matching you in Events. You can set up Face Match again later by giving consent again.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmWithdraw = false; onWithdraw() }) {
                    Text("Withdraw Consent", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmWithdraw = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete SnapLoop account?") },
            text = {
                Text(
                    "This permanently deletes your account and SnapLoop server-side data. This action cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete Permanently", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PrivacyCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
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
        Text(label, modifier = Modifier.weight(1f), color = PrivacyViolet, fontWeight = FontWeight.Bold)
        Text("›", color = PrivacySecondary, fontSize = 22.sp)
    }
}