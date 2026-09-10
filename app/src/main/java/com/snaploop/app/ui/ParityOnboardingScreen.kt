package com.snaploop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun ParityOnboardingScreen(onCompleted: () -> Unit) {
    val pages = OnboardingParityContract.pages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        SnapColors.CoralSoft.copy(alpha = 0.30f),
                        MaterialTheme.colorScheme.background,
                        SnapColors.LilacSoft.copy(alpha = 0.28f),
                    ),
                ),
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.graphicsLayer {
                        scaleX = 0.82f
                        scaleY = 0.82f
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
                ) {
                    ParityBrandWordmark()
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${page + 1} of ${pages.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SnapColors.Secondary,
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { index ->
                OnboardingParityPageContent(pages[index], index)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        pages.indices.forEach { index ->
                            Box(
                                Modifier
                                    .size(width = if (index == page) 24.dp else 8.dp, height = 8.dp)
                                    .background(
                                        if (index == page) SnapColors.Coral
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                        RoundedCornerShape(50),
                                    ),
                            )
                        }
                    }

                    ParityPrimaryButton(
                        text = if (page == pages.lastIndex) {
                            "${pages[page].primaryCta}  ✓"
                        } else {
                            "${pages[page].primaryCta}  →"
                        },
                        onClick = {
                            if (page == pages.lastIndex) {
                                onCompleted()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(page + 1) }
                            }
                        },
                    )

                    if (page > 0) {
                        TextButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Back", color = SnapColors.Secondary, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingParityPageContent(item: OnboardingParityPage, index: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        OnboardingParityIllustration(item.kind)

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (index == 0) {
                Row(
                    Modifier
                        .background(Color.White.copy(alpha = 0.78f), CircleShape)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SnapColors.Coral)
                    Text(
                        "Welcome to SnapLoop!",
                        style = TextStyle(
                            brush = SnapGradients.Brand,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                        ),
                    )
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SnapColors.Lilac)
                }
            }

            Text(
                item.title,
                color = SnapColors.Ink,
                fontSize = 26.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                item.body,
                color = SnapColors.Secondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                noteIcon(item.kind),
                contentDescription = null,
                tint = SnapColors.Lilac,
                modifier = Modifier.size(18.dp),
            )
            Text(
                item.note,
                color = SnapColors.Ink.copy(alpha = 0.78f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun OnboardingParityIllustration(kind: OnboardingParityKind) {
    Box(
        Modifier.size(172.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(164.dp).background(SnapGradients.SoftWash, CircleShape))
        Box(
            Modifier.graphicsLayer {
                scaleX = 0.82f
                scaleY = 0.82f
            },
            contentAlignment = Alignment.Center,
        ) {
            when (kind) {
                OnboardingParityKind.FIND -> PhotoStackIllustration()
                OnboardingParityKind.FACE -> FaceSetupIllustration()
                OnboardingParityKind.TRIP -> TripFlowIllustration()
                OnboardingParityKind.RESULT -> ResultFlowIllustration()
                OnboardingParityKind.PRIVACY -> PrivacySummaryIllustration()
            }
        }
    }
}

@Composable
private fun PhotoStackIllustration() {
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        SymbolCard(
            icon = Icons.Filled.PhotoLibrary,
            tint = SnapColors.Blue,
            modifier = Modifier.offset(x = (-50).dp, y = 16.dp).rotate(-10f),
        )
        SymbolCard(
            icon = Icons.Filled.Groups,
            tint = SnapColors.Lilac,
            modifier = Modifier.offset(x = 48.dp, y = 8.dp).rotate(9f),
        )
        SymbolCard(
            icon = Icons.Filled.Person,
            tint = SnapColors.Coral,
            modifier = Modifier.offset(y = (-22).dp),
        )
    }
}

@Composable
private fun SymbolCard(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 86.dp, height = 98.dp)
            .background(Color.White, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(42.dp))
    }
}

@Composable
private fun FaceSetupIllustration() {
    Box(Modifier.size(width = 170.dp, height = 190.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 140.dp, height = 170.dp)
                .border(3.dp, SnapColors.Lilac.copy(alpha = 0.35f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Face, contentDescription = null, tint = SnapColors.Lilac, modifier = Modifier.size(84.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(42.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = SnapColors.Mint, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun TripFlowIllustration() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureBubble(Icons.Filled.AddCircle, "Create")
        Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = SnapColors.Coral, modifier = Modifier.size(30.dp))
        FeatureBubble(Icons.Filled.PersonAdd, "Join")
    }
}

@Composable
private fun ResultFlowIllustration() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IllustrationLabel(Icons.Filled.Groups, "Event phones", SnapColors.Lilac)
        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = SnapColors.Coral, modifier = Modifier.size(30.dp))
        IllustrationLabel(Icons.Filled.Person, "Photos of you", SnapColors.Blue)
    }
}

@Composable
private fun FeatureBubble(icon: ImageVector, label: String) {
    Column(
        Modifier
            .size(width = 98.dp, height = 104.dp)
            .background(Color.White, RoundedCornerShape(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = SnapColors.Coral, modifier = Modifier.size(42.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 9.dp))
    }
}

@Composable
private fun IllustrationLabel(icon: ImageVector, label: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(52.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PrivacySummaryIllustration() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(Icons.Filled.Security, contentDescription = null, tint = SnapColors.Blue, modifier = Modifier.size(58.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PermissionChip(Icons.Filled.CheckCircle, "Event dates only")
            PermissionChip(Icons.Filled.PhoneAndroid, "On-device match")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PermissionChip(Icons.Filled.PhotoLibrary, "No full upload")
            PermissionChip(Icons.Filled.Delete, "15-day deletion")
        }
    }
}

@Composable
private fun PermissionChip(icon: ImageVector, text: String) {
    Row(
        Modifier.background(Color.White, CircleShape).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = SnapColors.Ink, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SnapColors.Ink)
    }
}

private fun noteIcon(kind: OnboardingParityKind): ImageVector = when (kind) {
    OnboardingParityKind.FIND -> Icons.Filled.AutoAwesome
    OnboardingParityKind.FACE -> Icons.Filled.Lock
    OnboardingParityKind.TRIP -> Icons.Filled.Groups
    OnboardingParityKind.RESULT -> Icons.Filled.CheckCircle
    OnboardingParityKind.PRIVACY -> Icons.Filled.Delete
}
