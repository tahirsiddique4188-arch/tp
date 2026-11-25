package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ModelInfo
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * TitanLiveVoiceOverlay — Exact 1:1 Cyberpunk Sci-Fi Live Phone Call Interface Replica
 *
 * Implements:
 * 1. Deep space dark background with electric cyan (#00F0FF) & neon purple (#B026FF) circuit borders.
 * 2. Top status row with glowing green dot + "Live Call" badge flanked by animated equalizer bars.
 * 3. Bold "Titan" metallic title with Titan's own Golden Lion Emblem (ic_titan_logo), "AI Assistant"
 *    subtitle, and live chronometer timer (00:00:23 format).
 * 4. Center floating cybernetic mechanical heart / glowing core with 3D breathing and audio-reactive aura.
 * 5. Symmetric multi-bar audio equalizer waveform transitioning from cyan to purple.
 * 6. Dynamic status label ("Listening...", "Thinking...", "Speaking...") with live spoken/response transcript.
 * 7. Bottom 3 circular controls: Mute (purple glow), End Call (bright red circular button), Speaker (cyan glow).
 *
 * Developer: Developer Tahir (@Tahirsiddik)
 */
@Composable
fun TitanLiveVoiceOverlay(
    isListening: Boolean,
    isSpeaking: Boolean,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    audioLevel: Float,
    liveSpokenText: String,
    liveResponseText: String,
    activePersonaName: String = "Puck",
    activeModel: ModelInfo,
    webSocketStatusLabel: String = "🟢 Live Call",
    onModelSelected: (ModelInfo) -> Unit = {},
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onOpenVoicePicker: () -> Unit = {},
    onTapToSpeak: () -> Unit = {},
    onSendLiveMessage: (String) -> Unit = {},
    onEndLiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Live call duration timer
    var callSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            callSeconds++
        }
    }

    val timerFormatted = remember(callSeconds) {
        val hours = callSeconds / 3600
        val mins = (callSeconds % 3600) / 60
        val secs = callSeconds % 60
        String.format("%02d:%02d:%02d", hours, mins, secs)
    }

    androidx.activity.compose.BackHandler {
        onEndLiveClick()
    }

    // Smooth breathing & floating animations
    val infiniteTransition = rememberInfiniteTransition(label = "CoreFloatingAnim")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatY"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CoreBreath"
    )

    val reactiveScale = if (isSpeaking) {
        breathingScale * 1.03f
    } else if (isListening) {
        (1.0f + (audioLevel * 0.10f)).coerceIn(1.0f, 1.12f)
    } else {
        1.0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050814))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume background click */ }
    ) {
        // 1. Sci-Fi Cyberpunk Background Art (Framing borders, circuits & neon ambient lighting)
        Image(
            painter = painterResource(id = R.drawable.titan_live_call_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Semi-transparent ambient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x99050814),
                            Color(0x44050814),
                            Color(0x88050814)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==========================================
            // TOP SECTION: Status Badge & Equalizers
            // ==========================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Top Status Row: [Cyan EQ] (🟢 Live Call) [Green EQ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Cyan Audio Equalizer Bars
                    MiniEqualizerBars(
                        barColor = Color(0xFF00F0FF),
                        isAnimated = isListening || isSpeaking,
                        audioLevel = audioLevel
                    )

                    // Center "🟢 Live Call" Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF0D2818).copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00FF88).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color(0xFF00FF88))
                            .clickable { onTapToSpeak() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00FF88))
                                    .shadow(6.dp, CircleShape, spotColor = Color(0xFF00FF88))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Call",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Right Green Audio Equalizer Bars
                    MiniEqualizerBars(
                        barColor = Color(0xFF00FF88),
                        isAnimated = isListening || isSpeaking,
                        audioLevel = audioLevel
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Titan Title + Golden Lion Brand Emblem
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Titan",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic,
                        style = androidx.compose.ui.text.TextStyle(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFFBAE6FD),
                                    Color(0xFF38BDF8),
                                    Color(0xFF60A5FA)
                                )
                            )
                        ),
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Titan's Own Brand Emblem (Golden Lion Logo)
                    Image(
                        painter = painterResource(id = R.drawable.ic_titan_logo),
                        contentDescription = "Titan Emblem",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .shadow(10.dp, CircleShape, spotColor = Color(0xFFF59E0B))
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // "AI Assistant" Subtitle with accent lines
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(1.2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color(0xFF38BDF8))
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Assistant",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF93C5FD),
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(1.2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF38BDF8), Color.Transparent)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Live Timer: 🕒 00:00:23
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Call Duration",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = timerFormatted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0),
                        letterSpacing = 1.sp
                    )
                }
            }

            // ==========================================
            // CENTER HERO: Cybernetic Core & Rings
            // ==========================================
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .offset(y = floatOffset.dp)
                        .scale(reactiveScale)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTapToSpeak()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.titan_cyber_core),
                        contentDescription = "Titan Live Core",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // ==========================================
            // EQUALIZER WAVEFORM, STATUS & SPEECH
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Symmetrical Neon Gradient Audio Waveform
                SymmetricAudioWaveform(
                    isSpeaking = isSpeaking,
                    isListening = isListening,
                    audioLevel = audioLevel,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(38.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Dynamic Status Text: "Listening...", "Thinking...", "Speaking..."
                val statusText = when {
                    isSpeaking -> "Speaking..."
                    isListening && !isMuted -> "Listening..."
                    isMuted -> "Microphone Muted"
                    else -> "Thinking..."
                }

                val statusColor = when {
                    isSpeaking -> Color(0xFF00FF88)
                    isMuted -> Color(0xFFF87171)
                    isListening -> Color(0xFF00F0FF)
                    else -> Color(0xFFC084FC)
                }

                Text(
                    text = statusText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.clickable { onTapToSpeak() }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Live Spoken / Response text display bubble
                val activeTranscript = when {
                    isSpeaking && liveResponseText.isNotBlank() -> liveResponseText.take(160) + if (liveResponseText.length > 160) "..." else ""
                    liveSpokenText.isNotBlank() -> liveSpokenText
                    else -> ""
                }

                if (activeTranscript.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = activeTranscript,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFFE2E8F0),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // ==========================================
            // BOTTOM CONTROLS: Mute, End Call, Speaker
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Mute Button (Left)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                                .border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(
                                        colors = if (isMuted) listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                                        else listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6))
                                    ),
                                    shape = CircleShape
                                )
                                .shadow(8.dp, CircleShape, spotColor = Color(0xFF8B5CF6))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleMute()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (isMuted) Color(0xFFF87171) else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Mute",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0)
                        )
                    }

                    // 2. End Call Button (Center, Big Red Circular Button)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFF3B30),
                                            Color(0xFFDC2626),
                                            Color(0xFF991B1B)
                                        )
                                    )
                                )
                                .shadow(18.dp, CircleShape, spotColor = Color(0xFFFF3B30))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onEndLiveClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "End Call",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF87171)
                        )
                    }

                    // 3. Speaker Button (Right)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                                .border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(
                                        colors = if (isSpeakerOn) listOf(Color(0xFF00F0FF), Color(0xFF2563EB))
                                        else listOf(Color(0xFF64748B), Color(0xFF334155))
                                    ),
                                    shape = CircleShape
                                )
                                .shadow(8.dp, CircleShape, spotColor = Color(0xFF00F0FF))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleSpeaker()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Speaker",
                                tint = if (isSpeakerOn) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Speaker",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }
    }
}

/**
 * MiniEqualizerBars — Left & Right Animated Status Bar Equalizer
 */
@Composable
fun MiniEqualizerBars(
    barColor: Color,
    isAnimated: Boolean,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "MiniEqTransition")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MiniEqPhase"
    )

    Row(
        modifier = modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bars = 6
        for (i in 0 until bars) {
            val hFactor = if (isAnimated) {
                0.3f + 0.7f * ((sin(phase + i * 0.9f) + 1f) / 2f) * (0.5f + audioLevel * 1.5f).coerceIn(0.4f, 1f)
            } else {
                0.25f + (i % 2) * 0.15f
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(hFactor.coerceIn(0.15f, 1f))
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
        }
    }
}

/**
 * SymmetricAudioWaveform — Multi-bar audio equalizer reacting to live voice levels
 */
@Composable
fun SymmetricAudioWaveform(
    isSpeaking: Boolean,
    isListening: Boolean,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "SymmetricWaveTransition")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    Canvas(modifier = modifier) {
        val totalBars = 25
        val barWidth = 3.dp.toPx()
        val spacing = (size.width - (totalBars * barWidth)) / (totalBars - 1)
        val midIndex = totalBars / 2

        for (i in 0 until totalBars) {
            val distFromCenter = kotlin.math.abs(i - midIndex).toFloat() / midIndex
            val bellCurve = (1f - distFromCenter * 0.65f).coerceIn(0.2f, 1f)

            val dynamicHeight = if (isSpeaking || isListening) {
                val wave = ((sin(phase + i * 0.35f) + 1f) / 2f)
                val amp = 0.2f + 0.8f * wave * (0.4f + audioLevel * 2.2f).coerceIn(0.3f, 1.3f)
                (size.height * amp * bellCurve).coerceIn(4.dp.toPx(), size.height)
            } else {
                (size.height * 0.18f * bellCurve).coerceAtLeast(3.dp.toPx())
            }

            // Gradient color: Cyan (#00F0FF) in center, Purple (#C084FC) on sides
            val barColor = if (distFromCenter < 0.4f) {
                Color(0xFF00F0FF)
            } else if (distFromCenter < 0.75f) {
                Color(0xFF38BDF8)
            } else {
                Color(0xFFB026FF)
            }

            val x = i * (barWidth + spacing)
            val y = (size.height - dynamicHeight) / 2f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, dynamicHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        }
    }
}
