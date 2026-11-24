package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import com.titan.ide.voice.TitanContinuousSpeechEngine
import kotlin.math.sin

@Composable
fun GeminiBottomInputBar(
    textValue: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onPlusClick: () -> Unit,
    onCameraClick: () -> Unit,
    onMicClick: () -> Unit, // Gemini Live Voice Overlay
    onDictationClick: () -> Unit = {}, // STT Input Dictation
    isDictating: Boolean = false,
    dictationSeconds: Int = 0,
    dictationAudioLevel: Float = 0f,
    onVoiceSettingsClick: () -> Unit = {},
    isVoiceSettingsOpen: Boolean = false,
    onToggleSearchGrounding: () -> Unit = {},
    isSearchGroundingActive: Boolean = true,
    attachedImageUri: String? = null,
    attachedFileName: String? = null,
    onRemoveAttachment: () -> Unit,
    isGenerating: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val activeLangTag by TitanContinuousSpeechEngine.activeLanguageTag.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "MicGlow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val redPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RedPulse"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DictationWavePhase"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Attachment Thumbnail Preview Box
        if (attachedImageUri != null || attachedFileName != null) {
            Surface(
                color = GeminiSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GeminiThinkingBorder),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .wrapContentSize()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (attachedImageUri != null) {
                        AsyncImage(
                            model = com.example.util.ImageUtils.resolveCoilModel(attachedImageUri),
                            contentDescription = "Attachment",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else if (attachedFileName != null) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = attachedFileName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onRemoveAttachment,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = GeminiTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Live Continuous Dictation Status Banner (Keeps TextField visible below so words type live!)
        AnimatedVisibility(visible = isDictating) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF2A1515),
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252).copy(alpha = redPulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val mins = dictationSeconds / 60
                        val secs = dictationSeconds % 60
                        Text(
                            text = String.format("%02d:%02d", mins, secs),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFF8A80)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-Detect (Multilingual)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFFCCBC)
                        )
                    }

                    // Dynamic 7-Bar Waveform
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        for (i in 0..6) {
                            val barHeight = (6 + (14 * sin(wavePhase + i * 0.8f).let { if (it < 0) -it else it } * (0.4f + dictationAudioLevel * 0.6f))).dp
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFFF8A80), Color(0xFFFF5252))
                                        )
                                    )
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDictationClick()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF5252).copy(alpha = 0.22f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stop",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8A80)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Dictation",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Main Floating Input Pill Bar (TextField ALWAYS stays visible so dictated speech appears live!)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, if (isDictating) Color(0xFFFF5252) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(28.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Soft Cyan + Button (Attachment Sheet)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGenerating) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                        .clickable(enabled = !isGenerating, onClick = onPlusClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attachments & API Key",
                        tint = if (isGenerating) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                               else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text Input Field with Live Voice-to-Text Dictation Button
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = textValue,
                        onValueChange = onTextChange,
                        enabled = !isGenerating,
                        placeholder = {
                            Text(
                                text = when {
                                    isGenerating -> "Titan is working... (please wait)"
                                    isDictating -> "Listening continuously... speak naturally"
                                    else -> "Ask Titan IDE... (or tap mic)"
                                },
                                color = if (isDictating) Color(0xFFFF8A80)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isGenerating) 0.6f else 1f),
                                fontSize = 13.5.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp, max = 120.dp)
                    )

                    // Dictation Mic Toggle Button inside Input Bar
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDictationClick()
                        },
                        enabled = !isGenerating,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDictating) Color(0xFFFF5252).copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = if (isDictating) Icons.Default.Stop else Icons.Default.GraphicEq,
                            contentDescription = "Dictate Speech to Text",
                            tint = if (isDictating) Color(0xFFFF5252)
                                   else if (isGenerating) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                   else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Camera Icon
                IconButton(
                    onClick = onCameraClick,
                    enabled = !isGenerating,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        tint = if (isGenerating) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Right Action: Spinning Send Progress if generating, Send Arrow if typing/dictating text, OR Gemini Live Mic
                if (isGenerating || textValue.trim().isNotEmpty() || attachedImageUri != null || attachedFileName != null) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isGenerating) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.primary
                            )
                            .clickable(enabled = !isGenerating, onClick = onSendClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else {
                    val rainbowBrush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF00E5FF),
                            Color(0xFFA142F4),
                            Color(0xFFFFB74D),
                            Color(0xFF00E5FF)
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .clip(CircleShape)
                            .background(rainbowBrush)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(enabled = !isGenerating, onClick = onMicClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Gemini Live Voice Mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
