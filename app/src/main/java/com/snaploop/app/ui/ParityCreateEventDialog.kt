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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Full-screen Create Event surface mirrored from pinned iOS CreateEventView.swift. */
@Composable
internal fun ParityCreateEventDialog(
    hasFaceProfile: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCompleteFaceSetup: () -> Unit,
    onSubmit: (String, EventCategory, String?, LocalDate, LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val allowed = remember(today) { CreateEventParityPolicy.allowedDates(today) }
    var name by rememberSaveable { mutableStateOf("") }
    var category by remember { mutableStateOf(EventCategory.trip) }
    var categoryMenu by remember { mutableStateOf(false) }
    var location by rememberSaveable { mutableStateOf("") }
    var startsOn by remember { mutableStateOf(today) }
    var endsOn by remember { mutableStateOf(today.plusDays(CreateEventParityPolicy.SUGGESTED_DURATION_DAYS.toLong())) }

    val cleanName = name.trim()
    val cleanLocation = location.trim().takeIf { it.isNotEmpty() }
    val dateValid = CreateEventParityPolicy.isDateRangeValid(startsOn, endsOn, today)
    val canCreate = hasFaceProfile && cleanName.isNotEmpty() && dateValid && !busy

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
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
                    Text(
                        "New Event",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.size(64.dp))
                }

                ParityBrandMark(58)
                Text(
                    "Create an Event",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Trip, party, family celebration, wedding — bring everyone's photos together.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )

                ParityPremiumCard {
                    CreateFieldLabel("Event name", Icons.Filled.TextFields)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = CreateEventParityPolicy.limitName(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Banff Weekend") },
                        supportingText = { Text("${name.length}/${CreateEventParityPolicy.MAX_NAME_CHARACTERS}") },
                        singleLine = true,
                        enabled = !busy,
                    )

                    HorizontalDivider()
                    CreateFieldLabel("Type", Icons.Filled.Category)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { categoryMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                        ) {
                            Text(createCategoryName(category))
                        }
                        DropdownMenu(
                            expanded = categoryMenu,
                            onDismissRequest = { categoryMenu = false },
                        ) {
                            EventCategory.entries.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(createCategoryName(item)) },
                                    onClick = {
                                        category = item
                                        categoryMenu = false
                                    },
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                    CreateFieldLabel("Location", Icons.Filled.LocationOn)
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        enabled = !busy,
                    )
                }

                ParityPremiumCard {
                    CreateFieldLabel("Event dates", Icons.Filled.CalendarMonth)
                    CreateDateField(
                        label = "Starts",
                        value = startsOn,
                        minimum = allowed.lower,
                        maximum = allowed.upper,
                        busy = busy,
                    ) { newStart ->
                        startsOn = newStart
                        endsOn = CreateEventParityPolicy.repairEndAfterStartChange(
                            newStart = newStart,
                            currentEnd = endsOn,
                            today = today,
                        )
                    }
                    HorizontalDivider()
                    CreateDateField(
                        label = "Ends",
                        value = endsOn,
                        minimum = startsOn,
                        maximum = CreateEventParityPolicy.allowedEndUpper(startsOn, today),
                        busy = busy,
                        onValue = { endsOn = it },
                    )
                    Text(
                        "SnapLoop only considers photos taken within this Event's selected date range. Dates must stay within 15 days before or after today, and an Event can span at most 15 calendar days.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                        fontSize = 12.sp,
                    )
                    if (!dateValid) {
                        Text(
                            "Choose a valid Event date range.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (!hasFaceProfile) {
                    ParityPremiumCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Filled.Face, contentDescription = null, tint = SnapColors.Lilac)
                            Text(
                                "  Complete Face Setup before creating an Event so SnapLoop can find your photos.",
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }
                        ParityPrimaryButton(
                            text = "Complete Face Setup",
                            onClick = onCompleteFaceSetup,
                            enabled = !busy,
                            leadingContent = {
                                Icon(Icons.Filled.Face, contentDescription = null)
                            },
                        )
                    }
                } else {
                    ParityPrimaryButton(
                        text = if (busy) "Creating…" else "Create Event",
                        enabled = canCreate,
                        onClick = {
                            onSubmit(
                                cleanName,
                                category,
                                cleanLocation,
                                startsOn,
                                endsOn,
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        },
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CreateFieldLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = SnapColors.Coral, modifier = Modifier.size(18.dp))
        Text("  $text", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun CreateDateField(
    label: String,
    value: LocalDate,
    minimum: LocalDate,
    maximum: LocalDate,
    busy: Boolean,
    onValue: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
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
                    datePicker.minDate = minimum.atStartOfDay(zone).toInstant().toEpochMilli()
                    datePicker.maxDate = maximum.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                }.show()
            },
        ) {
            Text(value.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
        }
    }
}

private fun createCategoryName(category: EventCategory): String = when (category) {
    EventCategory.trip -> "Trip"
    EventCategory.wedding -> "Wedding"
    EventCategory.party -> "Party"
    EventCategory.birthday -> "Birthday"
    EventCategory.conference -> "Conference"
    EventCategory.family -> "Family"
    EventCategory.sports -> "Sports"
    EventCategory.other -> "Other"
}
