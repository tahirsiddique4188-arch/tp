package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.components.orb.OrbEmotionState
import com.example.ui.components.orb.TitanDynamicOrbRenderer
import com.example.voice.TitanAssistantConfig

/**
 * TitanNeuralOrb
 * Universal animated assistant orb component.
 * Renders the user's selected skin from the 25-Orb catalog
 * with full emotion and state reactivity.
 */
@Composable
fun TitanNeuralOrb(
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    audioLevel: Float = 0f,
    isWhiteTheme: Boolean = false,
    orbId: String = TitanAssistantConfig.getSelectedOrbId(LocalContext.current),
    isWorking: Boolean = false,
    isThinking: Boolean = false,
    isSleeping: Boolean = false,
    isLookingAround: Boolean = false
) {
    val emotionState = when {
        isSleeping -> OrbEmotionState.SLEEPING
        isWorking -> OrbEmotionState.WORKING
        isSpeaking -> OrbEmotionState.SPEAKING
        isThinking -> OrbEmotionState.THINKING
        isListening -> OrbEmotionState.LISTENING
        isLookingAround -> OrbEmotionState.LOOKING_AROUND
        else -> OrbEmotionState.IDLE
    }

    TitanDynamicOrbRenderer(
        skinId = orbId,
        emotionState = emotionState,
        audioLevel = audioLevel,
        size = size,
        modifier = modifier
    )
}
