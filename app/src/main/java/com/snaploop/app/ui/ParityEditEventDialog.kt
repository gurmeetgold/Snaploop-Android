package com.snaploop.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Full-screen edit surface mirrored from pinned iOS EditEventView.swift. */
@Composable
internal fun ParityEditEventDialog(
    event: SnapEvent,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, EventCategory, String?, LocalDate, LocalDate) -> Unit,
) {
    val zone = EventEditParityPolicy.eventZone(event)
    val initialStart = remember(event.id, event.updatedAt) { event.startsAt.atZone(zone).toLocalDate() }
    val initialEnd = remember(event.id, event.updatedAt) { event.endsAt.atZone(zone).toLocalDate() }
    var name by remember(event.id, event.updatedAt) { mutableStateOf(event.name) }
    var category by remember(event.id, event.updatedAt) { mutableStateOf(event.category) }
    var location by remember(event.id, event.updatedAt) { mutableStateOf(event.locationName.orEmpty()) }
    var startsOn by remember(event.id, event.updatedAt) { mutableStateOf(initialStart) }
    var endsOn by remember(event.id, event.updatedAt) { mutableStateOf(initialEnd) }
    var categoryMenu by remember { mutableStateOf(false) }

    val cleanName = name.trim()
    val cleanLocation = location.trim().takeIf { it.isNotEmpty() }
    val datesChanged = startsOn != initialStart || endsOn != initialEnd
    val detailsChanged = cleanName != event.name || category != event.category || cleanLocation != event.locationName
    val today = LocalDate.now(zone)
    val lower = today.minusDays(15)
    val upper = today.plusDays(15)
    val dateInvalid = datesChanged && (
        endsOn.isBefore(startsOn) ||
            startsOn.isBefore(lower) || startsOn.isAfter(upper) ||
            endsOn.isBefore(lower) || endsOn.isAfter(upper) ||
            ChronoUnit.DAYS.between(startsOn, endsOn) > 15
        )
    val canSave = cleanName.isNotEmpty() && cleanName.length <= 20 &&
        !dateInvalid && (detailsChanged || datesChanged) && !busy

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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, enabled = !busy) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
                    }
                    Text(
                        "Edit Event",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(48.dp).weight(0f))
                    Spacer(Modifier.padding(horizontal = 24.dp))
                }

                ParityBrandMark(58)
                Text("Edit Event", fontSize = 28.sp, fontWeight = FontWeight.Black)

                ParityPremiumCard {
                    Text("Event name", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("${name.length}/20") },
                    )

                    Text("Type", fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { categoryMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                        ) {
                            Text(parityCategoryName(category))
                        }
                        DropdownMenu(
                            expanded = categoryMenu,
                            onDismissRequest = { categoryMenu = false },
                        ) {
                            EventCategory.entries.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(parityCategoryName(item)) },
                                    onClick = {
                                        category = item
                                        categoryMenu = false
                                    },
                                )
                            }
                        }
                    }

                    Text("Location", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Optional") },
                        singleLine = true,
                    )
                }

                ParityPremiumCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = SnapColors.Coral)
                        Text("  Event dates", fontWeight = FontWeight.Black)
                    }
                    ParityEditDateField(
                        label = "Starts",
                        value = startsOn,
                        eventZone = zone,
                        minimum = lower,
                        maximum = upper,
                        busy = busy,
                    ) { newStart ->
                        startsOn = newStart
                        if (endsOn.isBefore(newStart)) endsOn = newStart
                        if (ChronoUnit.DAYS.between(newStart, endsOn) > 15) {
                            endsOn = minOf(upper, newStart.plusDays(3))
                        }
                    }
                    ParityEditDateField(
                        label = "Ends",
                        value = endsOn,
                        eventZone = zone,
                        minimum = maxOf(lower, startsOn),
                        maximum = minOf(upper, startsOn.plusDays(15)),
                        busy = busy,
                        onValue = { endsOn = it },
                    )
                    Text(
                        if (!datesChanged && (initialStart.isBefore(lower) || initialEnd.isAfter(upper))) {
                            "Existing Event dates are preserved unless you change them."
                        } else {
                            "Changed dates must stay within 15 days before or after today, and an Event can span at most 15 calendar days."
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                        fontSize = 12.sp,
                    )
                    if (dateInvalid) {
                        Text(
                            "Choose a valid Event date range.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                ParityPrimaryButton(
                    text = if (busy) "Saving…" else "Save Event",
                    enabled = canSave,
                    onClick = {
                        onSubmit(
                            cleanName,
                            category,
                            cleanLocation,
                            startsOn,
                            endsOn,
                        )
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ParityEditDateField(
    label: String,
    value: LocalDate,
    eventZone: ZoneId,
    minimum: LocalDate,
    maximum: LocalDate,
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
                    { _, year, month, day ->
                        val picked = LocalDate.of(year, month + 1, day)
                        if (!picked.isBefore(minimum) && !picked.isAfter(maximum)) onValue(picked)
                    },
                    value.year,
                    value.monthValue - 1,
                    value.dayOfMonth,
                ).apply {
                    datePicker.minDate = minimum.atStartOfDay(eventZone).toInstant().toEpochMilli()
                    datePicker.maxDate = maximum.plusDays(1).atStartOfDay(eventZone).toInstant().toEpochMilli() - 1
                }.show()
            },
        ) {
            Text(value.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
        }
    }
}

private fun parityCategoryName(category: EventCategory): String = when (category) {
    EventCategory.trip -> "Trip"
    EventCategory.wedding -> "Wedding"
    EventCategory.party -> "Party"
    EventCategory.birthday -> "Birthday"
    EventCategory.conference -> "Conference"
    EventCategory.family -> "Family"
    EventCategory.sports -> "Sports"
    EventCategory.other -> "Other"
}
