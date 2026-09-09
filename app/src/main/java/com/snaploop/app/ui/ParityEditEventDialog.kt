package com.snaploop.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.SnapEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Full-screen Edit Event surface mirrored from pinned iOS EditEventView.swift. */
@Composable
internal fun ParityEditEventDialog(
    event: SnapEvent,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, EventCategory, String?, LocalDate, LocalDate) -> Unit,
) {
    val zone = remember(event.id, event.photoWindowTimeZoneId) { EventEditParityPolicy.eventZone(event) }
    val initialStart = remember(event.id, event.startsAt) { event.startsAt.atZone(zone).toLocalDate() }
    val initialEnd = remember(event.id, event.endsAt) { event.endsAt.atZone(zone).toLocalDate() }

    var name by rememberSaveable(event.id, event.updatedAt) { mutableStateOf(event.name) }
    var category by remember(event.id, event.updatedAt) { mutableStateOf(event.category) }
    var categoryMenu by remember { mutableStateOf(false) }
    var location by rememberSaveable(event.id, event.updatedAt) { mutableStateOf(event.locationName.orEmpty()) }
    var startsOn by remember(event.id, event.updatedAt) { mutableStateOf(initialStart) }
    var endsOn by remember(event.id, event.updatedAt) { mutableStateOf(initialEnd) }

    val cleanName = name.trim()
    val cleanLocation = location.trim().takeIf { it.isNotEmpty() }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
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
                    IconButton(onClick = onDismiss, enabled = !busy) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
                    }
                    Text(
                        "Edit Event",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.size(48.dp))
                }

                ParityBrandMark(54)
                Text(
                    "Edit Event",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                ParityPremiumCard {
                    EditFieldLabel("Event name", Icons.Filled.TextFields)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Event name") },
                        singleLine = true,
                        enabled = !busy,
                    )

                    HorizontalDivider()
                    EditFieldLabel("Type", Icons.Filled.Category)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { categoryMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                        ) {
                            Text(editCategoryName(category))
                        }
                        DropdownMenu(
                            expanded = categoryMenu,
                            onDismissRequest = { categoryMenu = false },
                        ) {
                            EventCategory.entries.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(editCategoryName(item)) },
                                    onClick = {
                                        category = item
                                        categoryMenu = false
                                    },
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                    EditFieldLabel("Location", Icons.Filled.LocationOn)
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        enabled = !busy,
                    )
                }

                ParityPremiumCard {
                    EditFieldLabel("Event dates", Icons.Filled.CalendarMonth)
                    Text(
                        "Organizer and Admins can change dates. Other members are notified; they do not need to approve the change.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                        fontSize = 12.sp,
                    )
                    EditDateField("Starts", startsOn, busy) { newStart ->
                        startsOn = newStart
                        endsOn = EventEditParityPolicy.repairEndAfterStartChange(
                            newStart = newStart,
                            currentEnd = endsOn,
                        )
                    }
                    HorizontalDivider()
                    EditDateField("Ends", endsOn, busy) { endsOn = it }
                    Text(
                        "Dates must stay within 15 days before or after today, and the event can span at most 15 calendar days.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                        fontSize = 12.sp,
                    )
                }

                ParityPrimaryButton(
                    text = if (busy) "Saving…" else "Save Event",
                    enabled = cleanName.isNotEmpty() && !busy,
                    onClick = {
                        onSubmit(cleanName, category, cleanLocation, startsOn, endsOn)
                    },
                    leadingContent = {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    },
                )
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun EditFieldLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = SnapColors.Coral, modifier = Modifier.size(18.dp))
        Text("  $text", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun EditDateField(
    label: String,
    value: LocalDate,
    busy: Boolean,
    onValue: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(
            enabled = !busy,
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onValue(LocalDate.of(year, month + 1, day)) },
                    value.year,
                    value.monthValue - 1,
                    value.dayOfMonth,
                ).show()
            },
        ) {
            Text(value.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
        }
    }
}

private fun editCategoryName(category: EventCategory): String = when (category) {
    EventCategory.trip -> "Trip"
    EventCategory.wedding -> "Wedding"
    EventCategory.party -> "Party"
    EventCategory.birthday -> "Birthday"
    EventCategory.conference -> "Conference"
    EventCategory.family -> "Family"
    EventCategory.sports -> "Sports"
    EventCategory.other -> "Other"
}
