package com.snaploop.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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
            Text("Invite by Phone", fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text(
                "Invite someone directly, or choose a number from your contacts.",
                textAlign = TextAlign.Center,
                color = Color(0xFF6B6670),
                fontSize = 14.sp,
            )

            ParityPremiumCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = SnapColors.Coral)
                    Text("  Add a person", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        OutlinedButton(onClick = { countryMenuOpen = true }) {
                            Text("${country.regionCode}  ${country.callingCode}", fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.ExpandMore, contentDescription = null)
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
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { raw -> phone = PhoneNumberNormalizer.localDisplayNumber(raw, country) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Phone number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }

                OutlinedButton(
                    onClick = {
                        contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Icon(Icons.Filled.Contacts, contentDescription = null)
                    Text("  Choose from Contacts", fontWeight = FontWeight.Bold)
                }
            }

            ParityPrimaryButton(
                text = if (isSending) "Sending…" else "Send Invite",
                onClick = ::sendInvite,
                enabled = !isSending && phone.trim().isNotEmpty(),
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
                    Text("Invitations", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    statuses.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Message,
                                contentDescription = null,
                                tint = if (row.delivery == "in_app") SnapColors.Aqua else SnapColors.Coral,
                            )
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(row.phoneNumber, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (row.delivery == "in_app") "In-app invitation" else "SMS invitation",
                                    color = Color(0xFF6B6670),
                                    fontSize = 12.sp,
                                )
                            }
                            Text(
                                row.status.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            ParityPremiumCard {
                Text("How it works", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    "Enter a phone number or choose a contact. Existing SnapLoop users receive the invitation directly in the app. If they are not on SnapLoop yet, you can send them an SMS invite link.",
                    color = Color(0xFF6B6670),
                    fontSize = 13.sp,
                )
                Text(
                    "They join only after accepting the invitation.",
                    color = Color(0xFF6B6670),
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
