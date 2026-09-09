package com.snaploop.app.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Live authentication surface mirrored from pinned iOS PhoneAuthFlowView.swift. */
@Composable
internal fun ParityAuthScreen(
    activity: Activity,
    state: AppUiState,
    coordinator: AppCoordinator,
) {
    var selectedRegion by rememberSaveable {
        mutableStateOf(ParityPhoneNumberSupport.localeDefault().regionCode)
    }
    val selectedCountry = ParityPhoneNumberSupport.supportedCountries.firstOrNull {
        it.regionCode == selectedRegion
    } ?: ParityPhoneNumberSupport.localeDefault()
    var countryMenuOpen by remember { mutableStateOf(false) }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var normalizedPhoneNumber by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var tapPending by rememberSaveable { mutableStateOf(false) }

    // The iOS screen marks itself busy synchronously before Firebase app
    // verification begins. Keep equivalent immediate feedback even though the
    // coordinator launches its work on viewModelScope.
    val effectiveBusy = state.busy || tapPending
    LaunchedEffect(state.busy, state.verificationId, state.message) {
        if (state.busy || state.verificationId != null || state.message != null) {
            tapPending = false
        }
    }

    ParityBrandBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(54.dp))
            ParityBrandWordmark()

            Text(
                "Get every photo of you.",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 26.dp),
            )
            Text(
                "Photos your friends took of you on their phones, brought to your phone automatically.",
                fontSize = 14.sp,
                color = SnapColors.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )

            ParityPremiumCard(Modifier.padding(top = 2.dp)) {
                if (state.verificationId == null) {
                    PhoneEntry(
                        selectedCountry = selectedCountry,
                        countryMenuOpen = countryMenuOpen,
                        phoneNumber = phoneNumber,
                        busy = effectiveBusy,
                        onCountryMenu = { countryMenuOpen = it },
                        onCountry = {
                            selectedRegion = it.regionCode
                            countryMenuOpen = false
                        },
                        onPhone = { phoneNumber = it },
                        onSend = {
                            val e164 = ParityPhoneNumberSupport.e164(phoneNumber, selectedCountry)
                            if (e164 == null) {
                                localError = ParityPhoneNumberSupport.INVALID_PHONE_MESSAGE
                            } else {
                                localError = null
                                normalizedPhoneNumber = e164
                                tapPending = true
                                coordinator.startPhoneVerification(activity, e164)
                            }
                        },
                    )
                } else {
                    CodeEntry(
                        normalizedPhoneNumber = normalizedPhoneNumber,
                        code = code,
                        busy = effectiveBusy,
                        onCode = { code = it.filter(Char::isDigit).take(6) },
                        onVerify = {
                            localError = null
                            tapPending = true
                            coordinator.confirmCode(code)
                        },
                        onDifferentNumber = {
                            localError = null
                            code = ""
                            normalizedPhoneNumber = null
                            tapPending = false
                            coordinator.useDifferentPhoneNumber()
                        },
                    )
                }
            }

            (localError ?: state.message)?.let { error ->
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        error,
                        color = Color.Red,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }
            Spacer(Modifier.height(44.dp))
        }
    }
}

@Composable
private fun PhoneEntry(
    selectedCountry: ParityPhoneCountry,
    countryMenuOpen: Boolean,
    phoneNumber: String,
    busy: Boolean,
    onCountryMenu: (Boolean) -> Unit,
    onCountry: (ParityPhoneCountry) -> Unit,
    onPhone: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PhoneIphone, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                "Mobile number",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 7.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Button(
                    onClick = { onCountryMenu(true) },
                    enabled = !busy,
                    modifier = Modifier.height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SnapColors.Peach.copy(alpha = 0.18f),
                        contentColor = SnapColors.Coral,
                        disabledContainerColor = SnapColors.Peach.copy(alpha = 0.10f),
                        disabledContentColor = SnapColors.Coral.copy(alpha = 0.55f),
                    ),
                ) {
                    Text(selectedCountry.regionCode, fontWeight = FontWeight.Bold)
                    Text(" ${selectedCountry.callingCode}", fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.ExpandMore, contentDescription = "Choose country", modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = countryMenuOpen,
                    onDismissRequest = { onCountryMenu(false) },
                ) {
                    ParityPhoneNumberSupport.supportedCountries.forEach { country ->
                        DropdownMenuItem(
                            text = { Text("${country.name}  ${country.callingCode}") },
                            onClick = { onCountry(country) },
                        )
                    }
                }
            }

            TextField(
                value = phoneNumber,
                onValueChange = onPhone,
                enabled = !busy,
                modifier = Modifier.weight(1f).height(60.dp),
                placeholder = { Text("Phone number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = authTextFieldColors(),
            )
        }

        ParityPrimaryButton(
            text = if (busy) "Sending Code…" else "Send Code",
            onClick = onSend,
            enabled = !busy && phoneNumber.trim().isNotEmpty(),
            leadingContent = {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.Message, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
            },
        )
    }
}

@Composable
private fun CodeEntry(
    normalizedPhoneNumber: String?,
    code: String,
    busy: Boolean,
    onCode: (String) -> Unit,
    onVerify: () -> Unit,
    onDifferentNumber: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = SnapColors.Coral,
            modifier = Modifier.size(34.dp),
        )
        Text("Enter the 6-digit code", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        normalizedPhoneNumber?.let {
            Text("Sent to $it", fontSize = 14.sp, color = SnapColors.Secondary)
        }

        TextField(
            value = code,
            onValueChange = onCode,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("6-digit code", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 1.5.sp,
            ),
            colors = authTextFieldColors(),
        )

        ParityPrimaryButton(
            text = if (busy) "Verifying…" else "Verify",
            onClick = onVerify,
            enabled = !busy && code.length >= 6,
            leadingContent = {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
            },
        )

        TextButton(onClick = onDifferentNumber, enabled = !busy) {
            Text("Use a different number", color = SnapColors.Blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun authTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)
