package com.snaploop.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaploop.app.core.DeepLinkParser
import com.snaploop.app.invite.EventInviteClient
import com.snaploop.app.invite.EventInviteDelivery
import com.snaploop.app.invite.EventInviteStatusRow
import com.snaploop.app.invite.PhoneCountry
import com.snaploop.app.invite.PhoneNumberNormalizer
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import kotlinx.coroutines.launch

/**
 * Android counterpart of pinned iOS InvitePeopleView.
 * Backend invitation routing remains authoritative: existing SnapLoop users get
 * an in-app invite; only an `sms` delivery response opens the native SMS composer.
 */
@Composable
internal fun ParityPhoneInviteScreen(
    event: SnapEvent,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { EventInviteClient() }
    var country by remember { mutableStateOf(PhoneCountry.localeDefault()) }
    var countryMenuOpen by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statuses by remember { mutableStateOf<List<EventInviteStatusRow>>(emptyList()) }

    suspend fun refreshStatuses() {
        statuses = runCatching { client.list(event.id) }.getOrDefault(emptyList())
    }

    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (index >= 0) {
                    phone = PhoneNumberNormalizer.localDisplayNumber(cursor.getString(index).orEmpty(), country)
                    errorMessage = null
                }
            }
        }
    }

    fun openSmsComposer(recipient: String) {
        val inviteUrl = DeepLinkParser.inviteUrl(event.inviteToken)
        val body = "Join ${event.name} on SnapLoop: $inviteUrl"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(recipient)}")
            putExtra("sms_body", body)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            message = "SMS invitation ready to send."
            errorMessage = null
        } else {
            message = "This person is not on SnapLoop yet. Use Share Invite to send the Event link."
            errorMessage = null
        }
    }

    fun sendInvite() {
        if (isSending) return
        if (event.status != EventStatus.active) {
            errorMessage = "This Event is not accepting new invitations. Reopen it first if you're the organizer."
            return
        }
        val normalized = PhoneNumberNormalizer.e164(phone, country)
        if (normalized == null) {
            errorMessage = "Enter or choose a valid phone number."
            return
        }
        scope.launch {
            isSending = true
            message = null
            errorMessage = null
            try {
                val delivery = client.invite(event.id, normalized)
                phone = PhoneNumberNormalizer.localDisplayNumber(delivery.phoneNumber, country)
                when (delivery.kind) {
                    EventInviteDelivery.Kind.IN_APP -> message = "Invitation delivered in SnapLoop."
                    EventInviteDelivery.Kind.SMS -> openSmsComposer(delivery.phoneNumber)
                }
                refreshStatuses()
            } catch (t: Throwable) {
                errorMessage = EventInviteClient.userMessage(t)
            } finally {
                isSending = false
            }
        }
    }

    LaunchedEffect(event.id) { refreshStatuses() }

    ParityBrandBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Invite by Phone",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.size(48.dp))
            }

            ParityBrandMark(56)
            Text("Invite by Phone", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = SnapColors.Ink)
            Text(
                "Invite someone directly, or choose a number from your contacts.",
                textAlign = TextAlign.Center,
                color = SnapColors.Secondary,
                fontSize = 14.sp,
            )

            ParityPremiumCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = SnapColors.Coral)
                    Text("  Add a person", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = SnapColors.Ink)
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Row(
                            Modifier
                                .height(PhoneInviteVisualParitySpec.COUNTRY_SELECTOR_HEIGHT_DP.dp)
                                .background(
                                    SnapColors.Peach.copy(alpha = PhoneInviteVisualParitySpec.COUNTRY_FILL_ALPHA),
                                    RoundedCornerShape(PhoneInviteVisualParitySpec.CONTROL_RADIUS_DP.dp),
                                )
                                .clickable { countryMenuOpen = true }
                                .padding(horizontal = PhoneInviteVisualParitySpec.COUNTRY_HORIZONTAL_PADDING_DP.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(country.regionCode, color = SnapColors.Coral, fontWeight = FontWeight.Bold)
                            Text(country.callingCode, color = SnapColors.Coral, fontWeight = FontWeight.Bold)
                            Icon(
                                Icons.Filled.ExpandMore,
                                contentDescription = "Choose country",
                                tint = SnapColors.Coral,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = countryMenuOpen,
                            onDismissRequest = { countryMenuOpen = false },
                        ) {
                            PhoneCountry.supported.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text("${value.name}  ${value.callingCode}") },
                                    onClick = {
                                        country = value
                                        phone = PhoneNumberNormalizer.localDisplayNumber(phone, value)
                                        countryMenuOpen = false
                                    },
                                )
                            }
                        }
                    }

                    TextField(
                        value = phone,
                        onValueChange = { raw -> phone = PhoneNumberNormalizer.localDisplayNumber(raw, country) },
                        modifier = Modifier
                            .weight(1f)
                            .height(PhoneInviteVisualParitySpec.PHONE_FIELD_HEIGHT_DP.dp),
                        singleLine = true,
                        placeholder = { Text("Phone number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(PhoneInviteVisualParitySpec.CONTROL_RADIUS_DP.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = SnapColors.Coral,
                        ),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(PhoneInviteVisualParitySpec.CONTACT_BUTTON_HEIGHT_DP.dp)
                        .background(
                            SnapColors.Mint.copy(alpha = PhoneInviteVisualParitySpec.CONTACT_FILL_ALPHA),
                            RoundedCornerShape(PhoneInviteVisualParitySpec.CONTROL_RADIUS_DP.dp),
                        )
                        .clickable {
                            contactPicker.launch(
                                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Contacts, contentDescription = null, tint = SnapColors.Mint)
                    Text("  Choose from Contacts", color = SnapColors.Mint, fontWeight = FontWeight.Bold)
                }
            }

            val phoneBlank = phone.trim().isEmpty()
            ParityPrimaryButton(
                text = if (isSending) "Sending…" else "Send Invite",
                onClick = ::sendInvite,
                modifier = Modifier.alpha(
                    if (phoneBlank) PhoneInviteVisualParitySpec.SEND_DISABLED_ALPHA else 1f,
                ),
                enabled = !isSending && !phoneBlank,
            )

            message?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF138A52))
                    Text("  $it", color = Color(0xFF138A52), fontSize = 13.sp)
                }
            }
            errorMessage?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, contentDescription = null, tint = Color.Red)
                    Text("  $it", color = Color.Red, fontSize = 13.sp)
                }
            }

            if (statuses.isNotEmpty()) {
                ParityPremiumCard {
                    Text("Invitations", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SnapColors.Ink)
                    statuses.forEachIndexed { index, row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val inApp = row.delivery == "in_app"
                            val tint = if (inApp) SnapColors.Mint else SnapColors.Coral
                            Box(
                                Modifier
                                    .size(PhoneInviteVisualParitySpec.STATUS_ICON_SIZE_DP.dp)
                                    .background(
                                        tint.copy(alpha = PhoneInviteVisualParitySpec.STATUS_ICON_FILL_ALPHA),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (inApp) Icons.Filled.CheckCircle else Icons.Filled.Message,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(row.phoneNumber, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    if (inApp) "In-app invitation" else "SMS invitation",
                                    color = SnapColors.Secondary,
                                    fontSize = 12.sp,
                                )
                            }
                            Text(
                                row.status.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(
                                        SnapColors.Peach.copy(
                                            alpha = PhoneInviteVisualParitySpec.STATUS_CAPSULE_FILL_ALPHA,
                                        ),
                                        CircleShape,
                                    )
                                    .padding(
                                        horizontal = PhoneInviteVisualParitySpec.STATUS_CAPSULE_HORIZONTAL_PADDING_DP.dp,
                                        vertical = PhoneInviteVisualParitySpec.STATUS_CAPSULE_VERTICAL_PADDING_DP.dp,
                                    ),
                            )
                        }
                        if (index != statuses.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }

            ParityPremiumCard {
                Text("How it works", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SnapColors.Ink)
                Text(
                    "Enter a phone number or choose a contact. Existing SnapLoop users receive the invitation directly in the app. If they are not on SnapLoop yet, you can send them an SMS invite link.",
                    color = SnapColors.Secondary,
                    fontSize = 13.sp,
                )
                Text(
                    "They join only after accepting the invitation.",
                    color = SnapColors.Secondary,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
