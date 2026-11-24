package com.example.ui.components.orb

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.voice.TitanAssistantConfig

/**
 * TitanDynamicOrbRenderer
 * Composable wrapper around Titan3DOrbFloatingCanvasView (True 60 FPS OpenGL ES 2.0 GPU 3D Shader).
 * Renders either the 3D Neural Plasma Orb or the 3D Cute Ghost with transparent alpha blending.
 */
@Composable
fun TitanDynamicOrbRenderer(
    skinId: String = TitanAssistantConfig.getSelectedOrbId(LocalContext.current),
    emotionState: OrbEmotionState = OrbEmotionState.IDLE,
    audioLevel: Float = 0f,
    size: Dp = 140.dp,
    modifier: Modifier = Modifier
) {
    val skin = remember(skinId) { TitanOrbCatalog.getOrbById(skinId) }
    val isListening = emotionState == OrbEmotionState.LISTENING
    val isSpeaking = emotionState == OrbEmotionState.SPEAKING || emotionState == OrbEmotionState.WORKING

    AndroidView(
        factory = { ctx ->
            Titan3DOrbFloatingCanvasView(ctx).apply {
                setSkin(skin)
                setVoiceState(isListening, isSpeaking)
                setAudioLevel(audioLevel)
            }
        },
        update = { view ->
            view.setSkin(skin)
            view.setVoiceState(isListening, isSpeaking)
            view.setAudioLevel(audioLevel)
        },
        modifier = modifier.size(size)
    )
}
