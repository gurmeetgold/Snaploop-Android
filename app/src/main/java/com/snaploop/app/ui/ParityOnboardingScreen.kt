package com.snaploop.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoStack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class ParityOnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val noteIcon: ImageVector,
    val note: String,
    val primaryCta: String,
)

private val parityOnboardingPages = listOf(
    ParityOnboardingPage(
        icon = Icons.Filled.PhotoStack,
        title = "Find every photo you're in",
        body = "After an Event (e.g. trip), your best photos may be sitting on everyone else's phones. SnapLoop automatically finds the photos you're in and brings them to your phone.",
        noteIcon = Icons.Filled.CheckCircle,
        note = "No more asking everyone to send you their photos.",
        primaryCta = "See How It Works",
    ),
    ParityOnboardingPage(
        icon = Icons.Filled.Face,
        title = "Set up your face once",
        body = "Take a quick selfie scan so SnapLoop can recognize you in Event photos. Your selfie and reference images stay only on this device and are not uploaded to SnapLoop.",
        noteIcon = Icons.Filled.Lock,
        note = "To enable matching, SnapLoop stores only face-template metadata — not your selfie photo.",
        primaryCta = "Continue",
    ),
    ParityOnboardingPage(
        icon = Icons.Filled.Groups,
        title = "Create an Event or join one",
        body = "Create an Event (e.g. trip, party or family gathering) for your group, or join a friend's Event with an invite. Everyone chooses whether to participate, and each Event has its own people and date range.",
        noteIcon = Icons.Filled.Groups,
        note = "Nobody is added silently — each person chooses to join.",
        primaryCta = "Continue",
    ),
    ParityOnboardingPage(
        icon = Icons.Filled.PhotoLibrary,
        title = "Your photos come to you",
        body = "SnapLoop finds photos of you from participating Event members' phones and shares those matches with you automatically. Photos where you are not matched are not shared with you.",
        noteIcon = Icons.Filled.CheckCircle,
        note = "Save the photos you like to your own photo library.",
        primaryCta = "Continue",
    ),
    ParityOnboardingPage(
        icon = Icons.Filled.Security,
        title = "Private by design",
        body = "SnapLoop never uploads your entire photo library. It checks only photos within your Event's selected date range, and face matching happens on your device.",
        noteIcon = Icons.Filled.Delete,
        note = "All Event-related cloud data, including matched photo previews, is deleted within 15 days after the Event ends.",
        primaryCta = "Start Using SnapLoop",
    ),
)

@Composable
internal fun ParityOnboardingScreen(onCompleted: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { parityOnboardingPages.size })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage

    ParityBrandBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ParityBrandWordmark(compact = true)
                Spacer(Modifier.weight(1f))
                Text(
                    "${page + 1} of ${parityOnboardingPages.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SnapColors.Secondary,
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { index ->
                val item = parityOnboardingPages[index]
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(190.dp)
                            .background(SnapGradients.SoftWash, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(118.dp)
                                .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(30.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = if (index % 2 == 0) SnapColors.Lilac else SnapColors.Coral,
                                modifier = Modifier.size(72.dp),
                            )
                        }
                    }

                    if (index == 0) {
                        Text(
                            "✨  Welcome to SnapLoop!  ✨",
                            color = SnapColors.Coral,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                    }

                    Text(
                        item.title,
                        color = SnapColors.Ink,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Text(
                        item.body,
                        color = SnapColors.Secondary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 23.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                    ParityPremiumCard(Modifier.padding(top = 18.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(item.noteIcon, contentDescription = null, tint = SnapColors.Lilac, modifier = Modifier.size(20.dp))
                            Text(
                                item.note,
                                color = SnapColors.Ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.97f)).padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    parityOnboardingPages.indices.forEach { index ->
                        Box(
                            Modifier
                                .size(width = if (index == page) 24.dp else 8.dp, height = 8.dp)
                                .background(
                                    if (index == page) SnapColors.Coral else Color.Black.copy(alpha = 0.15f),
                                    RoundedCornerShape(50),
                                ),
                        )
                    }
                }

                ParityPrimaryButton(
                    text = parityOnboardingPages[page].primaryCta,
                    onClick = {
                        if (page == parityOnboardingPages.lastIndex) {
                            onCompleted()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(page + 1) }
                        }
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )

                if (page > 0) {
                    TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } }) {
                        Text("Back", color = SnapColors.Secondary, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}
