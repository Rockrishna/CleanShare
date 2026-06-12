package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    viewModel: FileViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 4

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_screen"),
        color = CosmicSlateBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Elegant Google-like Onboarding Header Row (Brand logo and Skip Button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Brand Icon & Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(CosmicCyanAccent.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, CosmicCyanAccent.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = CosmicCyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "CleanShare Guide",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = CosmicWhiteText,
                        letterSpacing = (-0.25).sp
                    )
                }

                if (currentPage < totalPages - 1) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = CosmicGrayMuted),
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text(
                            text = "Skip",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Central Interactive Concept Area (Slide transition of step contents)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn(animationSpec = tween(350)))
                                .togetherWith(slideOutHorizontally { width -> -width } + fadeOut(animationSpec = tween(280)))
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn(animationSpec = tween(350)))
                                .togetherWith(slideOutHorizontally { width -> width } + fadeOut(animationSpec = tween(280)))
                        }
                    },
                    label = "pageTransition",
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Embedded high-fidelity micro-animated illustration
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (pageIndex) {
                                0 -> StepIllustrationImport()
                                1 -> StepIllustrationScrub()
                                2 -> StepIllustrationRename()
                                3 -> StepIllustrationShare()
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Text Descriptions
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            val (title, infoText) = getPageContent(pageIndex)
                            Text(
                                text = title,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CosmicWhiteText,
                                textAlign = TextAlign.Center,
                                letterSpacing = (-0.5).sp,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            Text(
                                text = infoText,
                                fontSize = 14.sp,
                                color = CosmicGrayMuted,
                                textAlign = TextAlign.Center,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Navigation Deck (Indicators and Active buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until totalPages) {
                        val isSelected = i == currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = spring(stiffness = 300f),
                            label = "dotWidth"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (isSelected) CosmicCyanAccent else CosmicBorder.copy(alpha = 0.5f),
                            animationSpec = tween(300),
                            label = "dotColor"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }

                // Action controls
                Button(
                    onClick = {
                        if (currentPage < totalPages - 1) {
                            currentPage++
                        } else {
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CosmicCyanAccent,
                        contentColor = CosmicSlateBg
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("onboarding_next_button")
                ) {
                    AnimatedContent(
                        targetState = currentPage == totalPages - 1,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                        },
                        label = "buttonTextTransition"
                    ) { isLastPage ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isLastPage) "Get Started" else "Next",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (isLastPage) Icons.Default.Launch else Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Micro-Illustration Composable for Page 0: Import (Files picking & share intent)
@Composable
fun StepIllustrationImport() {
    val infiniteTransition = rememberInfiniteTransition(label = "importAnim")
    
    // Smooth infinite floating offsets
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fileFloat"
    )

    // Bouncing vault lid scale
    val vaultScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vaultScale"
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(CosmicCyanAccent.copy(alpha = 0.16f), Color.Transparent)
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Vault body (Bottom layered depth card)
        Surface(
            modifier = Modifier
                .size(100.dp, 80.dp)
                .offset(y = 20.dp)
                .graphicsLayer {
                    scaleX = vaultScale
                    scaleY = vaultScale
                },
            color = CosmicCardSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, CosmicBorder)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = CosmicCyanLight,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Floating shared file card
        Surface(
            modifier = Modifier
                .size(60.dp, 70.dp)
                .offset(y = (floatOffset - 35f).dp, x = (-5).dp)
                .graphicsLayer {
                    rotationZ = -8f
                },
            color = CosmicCyanAccent,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = CosmicSlateBg,
                    modifier = Modifier.size(18.dp)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .background(CosmicSlateBg.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NEW",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CosmicSlateBg
                    )
                }
            }
        }
    }
}

// Micro-Illustration Composable for Page 1: Metadata Scrubbing diagram
@Composable
fun StepIllustrationScrub() {
    val infiniteTransition = rememberInfiniteTransition(label = "scrubAnim")
    
    // Cycle state using timestamp frame mapping
    val animationState by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycles"
    )

    // Derived flags representing animation phases
    val isScrubbing = animationState > 0.45f
    val sweepProgress = if (animationState in 0.35f..0.55f) {
        (animationState - 0.35f) / 0.20f
    } else if (animationState > 0.55f) 1.0f else 0f

    Surface(
        modifier = Modifier
            .size(220.dp, 160.dp),
        color = CosmicCardSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CosmicBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Mock Exif Metadata Card content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Photo mockup header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CosmicCyanLight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = null,
                            tint = CosmicCyanDark,
                            modifier = Modifier.size(20.dp).align(Alignment.Center)
                        )
                    }
                    Column {
                        Text(
                            text = "IMG_4812_EXIF.JPG",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicWhiteText
                        )
                        Text(
                            text = "Standard Exif Tag Block",
                            fontSize = 8.sp,
                            color = CosmicGrayMuted
                        )
                    }
                }

                Divider(color = CosmicDivider, thickness = 1.dp)

                // List of EXIF metadata rows with dynamic color scrub status animations
                ExifTagRow(
                    label = "📍 GPS Location",
                    value = if (isScrubbing) "Hidden" else "37.7749° N, 122.4194° W",
                    scrubbed = isScrubbing
                )
                ExifTagRow(
                    label = "📷 Camera Spec",
                    value = if (isScrubbing) "Cleaned" else "Pixel 9 Pro XL f/1.7",
                    scrubbed = isScrubbing
                )
                ExifTagRow(
                    label = "📅 Epoch Time",
                    value = if (isScrubbing) "Defaulted" else "June 11, 2026 19:37",
                    scrubbed = isScrubbing
                )
            }

            // Laser scanner sweeping wave lines representing sanitizer processing
            if (sweepProgress > 0f && sweepProgress < 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.04f)
                        .graphicsLayer {
                            translationY = sweepProgress * 160.dp.value * 2.5f
                        }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, CosmicCyanAccent, Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun ExifTagRow(label: String, value: String, scrubbed: Boolean) {
    val containerBg by animateColorAsState(
        targetValue = if (scrubbed) Color(0xFF1B4332).copy(alpha = 0.5f) else Color(0xFF3F1315).copy(alpha = 0.5f),
        animationSpec = tween(400),
        label = "tagBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (scrubbed) Color(0xFF52B788) else Color(0xFFFA5252),
        animationSpec = tween(400),
        label = "tagColor"
    )
    val indicatorIcon = if (scrubbed) Icons.Default.VerifiedUser else Icons.Default.ErrorOutline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(0.5.dp, textColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = indicatorIcon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = CosmicWhiteText,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = value,
            fontSize = 9.sp,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

// Micro-Illustration Composable for Page 2: File Renaming (Original name replaced with static opaque hashes)
@Composable
fun StepIllustrationRename() {
    val infiniteTransition = rememberInfiniteTransition(label = "renameAnim")
    
    // Cycle renaming state progress
    val cycles by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "renamerPhase"
    )

    // Simple status mapping
    val isRenamed = cycles > 0.5f

    Box(
        modifier = Modifier
            .size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Original filename dialog/card card
        AnimatedVisibility(
            visible = !isRenamed,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(),
            modifier = Modifier.offset(y = (-15).dp)
        ) {
            Surface(
                modifier = Modifier.size(175.dp, 60.dp),
                color = Color(0xFF3F1315).copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFA5252).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "DSC_2026_0611_98371.JPG",
                        fontSize = 11.sp,
                        color = Color(0xFFFA5252),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Exposes Date & Sequence Specs",
                        fontSize = 8.sp,
                        color = CosmicGrayMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Beautiful Morph Arrow
        Icon(
            imageVector = Icons.Default.SwapVert,
            contentDescription = null,
            tint = CosmicCyanAccent,
            modifier = Modifier
                .offset(y = 15.dp)
                .size(32.dp)
                .graphicsLayer {
                    rotationZ = if (isRenamed) 180f else 0f
                }
        )

        // Anonymized target clean card
        AnimatedVisibility(
            visible = isRenamed,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(),
            modifier = Modifier.offset(y = (-15).dp)
        ) {
            Surface(
                modifier = Modifier.size(175.dp, 60.dp),
                color = Color(0xFF1B4332).copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "clean_7d2f9a.jpg",
                        fontSize = 12.sp,
                        color = Color(0xFF22C55E),
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Opaque Safe Hash Identifier",
                        fontSize = 8.sp,
                        color = CosmicGrayMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Micro-Illustration Composable for Page 3: final Clean & Share!
@Composable
fun StepIllustrationShare() {
    val infiniteTransition = rememberInfiniteTransition(label = "shareAnim")

    // Node wave pulse effect
    val pulseStrength by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsers"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alphas"
    )

    // Flying share bubble offset
    val paperPlaneSlide by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "planeSlide"
    )

    Box(
        modifier = Modifier
            .size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Interconnected network circles
        Box(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer {
                    scaleX = pulseStrength
                    scaleY = pulseStrength
                    alpha = pulseAlpha
                }
                .background(CosmicCyanAccent.copy(alpha = 0.3f), CircleShape)
                .border(2.dp, CosmicCyanAccent, CircleShape)
        )

        // Secure Shield Node
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = CosmicCyanAccent,
            tonalElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = CosmicSlateBg,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Flying paper airplane share proxy
        Surface(
            modifier = Modifier
                .offset(x = paperPlaneSlide.dp, y = (-paperPlaneSlide / 1.5f).dp)
                .size(36.dp),
            shape = CircleShape,
            color = CosmicCardSurface,
            border = BorderStroke(1.dp, CosmicBorder)
        ) {
            Icon(
                imageVector = Icons.Default.Send, // beautifully resembling sandboxed dispatch air plane standard
                contentDescription = null,
                tint = CosmicCyanAccent,
                modifier = Modifier
                    .padding(6.dp)
                    .graphicsLayer {
                        rotationZ = -30f
                    }
            )
        }
    }
}

// Utility mapper to load titles & descriptions dynamically
private fun getPageContent(pageIndex: Int): Pair<String, String> {
    return when (pageIndex) {
        0 -> "Import Your Files" to "Tap 'Pick Photos or Files' to load items, or directly forward files into CleanShare from other apps via the default Android share sheet."
        1 -> "Strip Hidden Details" to "Securely strip location GPS tracks, epochs, camera lenses, and software tags metadata before sharing to maintain your absolute physical privacy."
        2 -> "Sanitize Filenames" to "Break tracking patterns by bulk renaming files with random hashes or index sequences. Stand out or stay completely anonymous."
        3 -> "Securely Dispatched" to "Your files are scrubbed local-offline in safe isolation sandbox buffers. Simply click 'Clean & Share' to launch the system intent menu instantly."
        else -> "" to ""
    }
}
