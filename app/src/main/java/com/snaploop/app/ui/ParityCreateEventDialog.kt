package com.snaploop.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    .padding(CreateEventVisualParitySpec.PAGE_PADDING_DP.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CreateEventVisualParitySpec.CONTENT_SPACING_DP.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !busy,
                        shape = RoundedCornerShape(CreateEventVisualParitySpec.CANCEL_RADIUS_DP.dp),
                        border = BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = CreateEventVisualParitySpec.CANCEL_BORDER_ALPHA),
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SnapColors.Coral),
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "New Event",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = CreateEventVisualParitySpec.HEADER_TITLE_SP.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.size(84.dp))
                }

                ParityBrandMark(CreateEventVisualParitySpec.HERO_MARK_DP)
                Text(
                    "Create an Event",
                    fontSize = CreateEventVisualParitySpec.HERO_TITLE_SP.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Trip, party, family celebration, wedding — bring everyone's photos together.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    fontSize = CreateEventVisualParitySpec.BODY_SP.sp,
                    textAlign = TextAlign.Center,
                )

                ParityPremiumCard {
                    CreateFieldLabel("Event name", Icons.Filled.TextFields)
                    CreateFilledTextField(
                        value = name,
                        onValueChange = { name = CreateEventParityPolicy.limitName(it) },
                        placeholder = "e.g. Banff Weekend",
                        enabled = !busy,
                    )

                    HorizontalDivider()
                    CreateFieldLabel("Type", Icons.Filled.Category)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { categoryMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            border = null,
                            shape = RoundedCornerShape(CreateEventVisualParitySpec.CATEGORY_RADIUS_DP.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SnapColors.Coral),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                Text(
                                    createCategoryName(category),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Start,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Icon(Icons.Filled.UnfoldMore, contentDescription = null)
                            }
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
                    CreateFilledTextField(
                        value = location,
                        onValueChange = { location = it.take(80) },
                        placeholder = "Optional",
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
                        fontSize = CreateEventVisualParitySpec.DATE_COPY_SP.sp,
                    )
                    if (!dateValid) {
                        Text(
                            "Choose a valid Event date range.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = CreateEventVisualParitySpec.DATE_COPY_SP.sp,
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
                                fontSize = CreateEventVisualParitySpec.BODY_SP.sp,
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
private fun CreateFilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
) {
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = CreateEventVisualParitySpec.INPUT_FILL_ALPHA)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(CreateEventVisualParitySpec.INPUT_RADIUS_DP.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill.copy(alpha = 0.65f),
        ),
    )
}

@Composable
private fun CreateFieldLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(CreateEventVisualParitySpec.FIELD_ICON_DP.dp),
        )
        Text(
            "  $text",
            fontWeight = FontWeight.SemiBold,
            fontSize = CreateEventVisualParitySpec.FIELD_LABEL_SP.sp,
        )
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
            border = null,
            shape = RoundedCornerShape(CreateEventVisualParitySpec.DATE_CAPSULE_RADIUS_DP.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.085f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
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
            Text(
                value.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                fontWeight = FontWeight.SemiBold,
            )
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
