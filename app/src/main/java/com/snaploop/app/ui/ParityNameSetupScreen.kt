package com.snaploop.app.ui

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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaploop.app.core.ProfileNamePolicy

/** Shared pinned-iOS profile-name surface for first-run setup and authenticated editing. */
@Composable
internal fun ParityNameSetupScreen(
    initialName: String,
    onSave: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var name by rememberSaveable(initialName) {
        mutableStateOf(ProfileNamePolicy.normalizeInput(initialName))
    }
    val trimmed = ProfileNamePolicy.normalizeForSave(name)

    ParityBrandBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onBack != null) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
                    }
                    Text(
                        "Your Name",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.size(48.dp))
                }
                Spacer(Modifier.height(14.dp))
            } else {
                Spacer(Modifier.height(58.dp))
            }

            ParityBrandMark(56)
            Text(
                "What should people call you?",
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
                color = SnapColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "People in your events will see this name.",
                color = SnapColors.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            ParityPremiumCard(Modifier.padding(top = 22.dp)) {
                Text("Display name", fontWeight = FontWeight.Bold, color = SnapColors.Ink)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = ProfileNamePolicy.normalizeInput(it) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("Your name") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = SnapColors.Coral) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                )
            }

            ProfileNamePolicy.validationMessage(name)?.takeIf { name.isNotEmpty() }?.let { message ->
                Text(
                    message,
                    color = androidx.compose.ui.graphics.Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            ParityPrimaryButton(
                text = "Save Name",
                onClick = { onSave(trimmed) },
                modifier = Modifier.padding(top = 18.dp),
                enabled = ProfileNamePolicy.isValid(name),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
