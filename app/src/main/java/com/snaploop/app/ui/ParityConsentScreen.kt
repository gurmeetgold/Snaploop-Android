package com.snaploop.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS-parity express Face Match consent screen with an always-available exit path. */
@Composable
internal fun ParityConsentScreen(
    onAccept: (String, String, Boolean, Boolean, Boolean) -> Unit,
    onNotNow: () -> Unit,
) {
    BackHandler(onBack = onNotNow)
    val context = LocalContext.current
    var country by rememberSaveable { mutableStateOf("CA") }
    var subdivision by rememberSaveable { mutableStateOf("ON") }
    var countryMenu by remember { mutableStateOf(false) }
    var provinceMenu by remember { mutableStateOf(false) }
    var ageAndResidence by rememberSaveable { mutableStateOf(false) }
    var expressConsent by rememberSaveable { mutableStateOf(false) }
    val provinces = listOf("AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT")
    val available = country == "IN" || (country == "CA" && subdivision != "QC")

    ParityBrandBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNotNow) { Text("‹ Back") }
                Text("Privacy", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.padding(horizontal = 28.dp))
            }

            ParityBrandMark(58)
            Text("Face Match Consent", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 14.dp))
            Text(
                "Please review how Face Match works before you choose whether to use it.",
                color = Color(0xFF66636C),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
            )

            ParityPremiumCard {
                ConsentSection(
                    "Purpose & storage",
                    "SnapLoop uses Face Match only to find photos that contain you. Your guided Face Setup photo stays on this device; SnapLoop stores a numerical face template for matching.",
                )
                ConsentSection(
                    "Event matching",
                    "Participating Event devices compare photos only within that Event's selected date range. SnapLoop does not upload your entire photo library.",
                )
                ConsentSection(
                    "Limits",
                    "Face Match is used only for SnapLoop photo matching. It is not used to identify strangers, for advertising, or for law-enforcement identification.",
                )
                ConsentSection(
                    "Retention & choice",
                    "Face Match data is retained for up to 12 months after your last use unless you withdraw consent or delete your account sooner. You can withdraw consent at any time in Privacy & Data.",
                )
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://getsnaploop.web.app/privacy.html#face-match-notice")))
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Read full Face Match Notice", fontWeight = FontWeight.Bold)
                }
            }

            ParityPremiumCard(Modifier.padding(top = 12.dp)) {
                Text("Your residence", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedButton(onClick = { countryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (country == "CA") "Canada" else "India")
                    }
                    DropdownMenu(countryMenu, { countryMenu = false }) {
                        DropdownMenuItem(text = { Text("Canada") }, onClick = { country = "CA"; if (subdivision.isBlank()) subdivision = "ON"; countryMenu = false })
                        DropdownMenuItem(text = { Text("India") }, onClick = { country = "IN"; subdivision = ""; countryMenu = false })
                    }
                }
                if (country == "CA") {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedButton(onClick = { provinceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Province / territory: $subdivision")
                        }
                        DropdownMenu(provinceMenu, { provinceMenu = false }) {
                            provinces.forEach { code ->
                                DropdownMenuItem(text = { Text(code) }, onClick = { subdivision = code; provinceMenu = false })
                            }
                        }
                    }
                }
                if (!available) {
                    Text(
                        "Face Match is not available in Quebec.",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            ParityPremiumCard(Modifier.padding(top = 12.dp)) {
                ConsentRow(
                    checked = ageAndResidence,
                    onChecked = { ageAndResidence = it },
                    text = if (country == "IN") {
                        "I am 18 or older and currently reside in India."
                    } else {
                        "I am 18 or older and currently reside in the selected Canadian province or territory."
                    },
                )
                ConsentRow(
                    checked = expressConsent,
                    onChecked = { expressConsent = it },
                    text = "I have read the Face Match notice and expressly consent to SnapLoop creating and using a face template from my own Face Setup for the purposes described above.",
                )
            }

            ParityPrimaryButton(
                "I Agree & Continue",
                onClick = { onAccept(country, if (country == "IN") "" else subdivision, true, true, true) },
                modifier = Modifier.padding(top = 14.dp),
                enabled = available && ageAndResidence && expressConsent,
            )
            TextButton(onClick = onNotNow, modifier = Modifier.padding(top = 4.dp)) {
                Text("Not Now", color = Color(0xFF66636C), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.padding(vertical = 13.dp))
        }
    }
}

@Composable
private fun ConsentSection(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(title, fontWeight = FontWeight.Black)
        Text(body, color = Color(0xFF66636C), fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun ConsentRow(checked: Boolean, onChecked: (Boolean) -> Unit, text: String) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked, onCheckedChange = onChecked)
        Text(text, modifier = Modifier.weight(1f).padding(top = 10.dp), fontSize = 13.sp)
    }
}
