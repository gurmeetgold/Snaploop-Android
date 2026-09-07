package com.snaploop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val parityBrandGradient = Brush.linearGradient(
    listOf(
        Color(0xFFFF7A59),
        Color(0xFFF05C68),
        Color(0xFFE850A8),
        Color(0xFF8E63F6),
        Color(0xFF5C7CF2),
    ),
)

@Composable
internal fun ParityBrandBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFFFFFBFD),
                    Color(0xFFFFF6FC),
                    Color(0xFFFFFCFF),
                ),
            ),
        ),
        content = content,
    )
}

@Composable
internal fun ParityBrandMark(size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .background(parityBrandGradient, RoundedCornerShape((size * 0.24f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "S",
            color = Color.White,
            fontSize = (size * 0.52f).sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
internal fun ParityPremiumCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal fun ParityPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (enabled) parityBrandGradient
                    else Brush.linearGradient(listOf(Color.LightGray, Color.Gray)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
            )
        }
    }
}
