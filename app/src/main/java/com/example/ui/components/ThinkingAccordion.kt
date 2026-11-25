package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun AnimatedThinkingSpark(
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SparkTransition")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SparkRotation"
    )

    val shimmerPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SparkPulse"
    )

    val neonCyan = Color(0xFF00F0FF)
    val neonPurple = Color(0xFF7928CA)
    val neonGreen = Color(0xFF00E676)

    val sparkBrush = Brush.sweepGradient(
        colors = listOf(
            neonCyan,
            neonPurple,
            neonGreen,
            neonCyan
        )
    )

    Box(
        modifier = modifier.size(26.dp),
        contentAlignment = Alignment.Center
    ) {
        // Compact outer ring with rotating gradient & shimmering pulse
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isStreaming) Modifier.scale(shimmerPulse).rotate(rotation) else Modifier)
                .clip(CircleShape)
                .background(sparkBrush)
                .padding(1.5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(GeminiDarkObsidian)
            )
        }

        // Center compact spark
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "Reasoning Spark",
            tint = if (isStreaming) neonCyan else neonPurple,
            modifier = Modifier
                .size(14.dp)
                .then(if (isStreaming) Modifier.rotate(-rotation * 0.4f) else Modifier)
        )
    }
}

@Composable
fun ThinkingAccordion(
    thinkingContent: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "RotateArrow"
    )

    if (thinkingContent.isBlank()) return

    // Animated shimmering gradient border & glowing brain icon
    val infiniteTransition = rememberInfiniteTransition(label = "ThinkingAnim")
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerOffset"
    )

    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IconPulse"
    )

    val thinkingBorderBrush = if (isStreaming) {
        Brush.linearGradient(
            colors = listOf(
                GeminiCyanAccent,
                GeminiSparklePurple,
                Color(0xFFFFB74D),
                GeminiCyanAccent
            ),
            start = androidx.compose.ui.geometry.Offset(shimmerOffset, 0f),
            end = androidx.compose.ui.geometry.Offset(shimmerOffset + 400f, 400f)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                GeminiSparklePurple.copy(alpha = 0.5f),
                GeminiThinkingBorder
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GeminiThinkingBackground)
            .border(
                width = if (isStreaming) 1.5.dp else 1.dp,
                brush = thinkingBorderBrush,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact Animated Multicolor Spark
            AnimatedThinkingSpark(isStreaming = isStreaming)

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isStreaming) "Deep Thinking in progress..." else "Thinking Process",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (isStreaming) GeminiCyanAccent else GeminiTextPrimary
                    )

                    if (isStreaming) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // Pulsing thinking dots
                        ThinkingWaveDots()
                    }
                }

                Text(
                    text = if (isExpanded) "Tap to collapse chain-of-thought" else "View hidden reasoning steps & logic",
                    fontSize = 10.sp,
                    color = GeminiTextSecondary
                )
            }

            // Expand Arrow
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = if (isStreaming) GeminiCyanAccent else GeminiTextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotationAngle)
            )
        }

        // Animated Smooth Expand/Collapse Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(250))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .padding(bottom = 6.dp)
            ) {
                // Divider with subtle gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(GeminiSparklePurple, GeminiCyanAccent.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Reasoning header indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = GeminiSparklePurple,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "INTERNAL CHAIN-OF-THOUGHT (REASONING TOKENS)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = GeminiSparklePurple
                    )
                }

                // Reasoning Text in stylized code box
                Surface(
                    color = GeminiSurface.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, GeminiThinkingBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = thinkingContent.trim(),
                        fontSize = 12.sp,
                        color = GeminiTextPrimary.copy(alpha = 0.88f),
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ThinkingWaveDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "DotsAnim")
    
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot3"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(GeminiCyanAccent.copy(alpha = dot1Alpha))
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(GeminiCyanAccent.copy(alpha = dot2Alpha))
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(GeminiCyanAccent.copy(alpha = dot3Alpha))
        )
    }
}
