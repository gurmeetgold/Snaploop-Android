package com.snaploop.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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

private const val DISPLAY_NAME_MAX_CHARACTERS = 20

@Composable
internal fun ParityNameSetupScreen(
    initialName: String,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) {
        mutableStateOf(initialName.take(DISPLAY_NAME_MAX_CHARACTERS))
    }
    val trimmed = name.trim()

    ParityBrandBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(58.dp))
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
                "People in your Events will see this name.",
                color = SnapColors.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            ParityPremiumCard(Modifier.padding(top = 22.dp)) {
                Text("Display name", fontWeight = FontWeight.Bold, color = SnapColors.Ink)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(DISPLAY_NAME_MAX_CHARACTERS) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("Your name") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = SnapColors.Coral) },
                    supportingText = {
                        Text(
                            "${name.length}/$DISPLAY_NAME_MAX_CHARACTERS",
                            color = SnapColors.Secondary,
                        )
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                )
            }

            ParityPrimaryButton(
                text = "Save Name",
                onClick = { onSave(trimmed) },
                modifier = Modifier.padding(top = 18.dp),
                enabled = trimmed.length >= 2,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
