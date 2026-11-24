package com.titan.ide.media.network

import android.content.Context
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.SenderRole
import com.titan.ide.media.video.TitanStoryVideoComposer
import com.titan.ide.network.TitanDirectEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TitanMediaGateway - 100% On-Device Imagen, Full AI Story Video + Sound (.mp4) & Broadcast Podcast Engine
 */
object TitanMediaGateway {

    private const val TAG = "TitanMediaGateway"

    suspend fun generateImagen3Artwork(
        context: Context,
        prompt: String,
        aspectRatio: String = "1:1",
        stylePreset: String = "Photorealistic"
    ): String = withContext(Dispatchers.IO) {
        try {
            TitanDirectEngine.generateImageDirect(context, prompt, aspectRatio, stylePreset)
        } catch (e: Exception) {
            Log.e(TAG, "Direct image error: ${e.message}", e)
            ""
        }
    }

    suspend fun generateVeo2Video(
        context: Context,
        topic: String,
        duration: String = "60s",
        format: String = "9:16",
        framesCount: Int = 0,
        fps: Int = 24,
        customFrames: List<String> = emptyList(),
        startFrame: String? = null,
        endFrame: String? = null,
        videoModel: String = "veo-3.1",
        videoStyle: String = "character_story",
        voicePersona: String = "",
        onProgress: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        try {
            val userVoice = if (voicePersona.isNotBlank()) {
                voicePersona
            } else {
                val prefs = context.getSharedPreferences("titan_prefs", Context.MODE_PRIVATE)
                prefs.getString("selected_voice_persona", "aryan") ?: "aryan"
            }
            val mp4File = TitanStoryVideoComposer.generateFullStoryVideo(
                context = context,
                topic = topic,
                format = format,
                videoStyle = videoStyle,
                voicePersona = userVoice,
                onProgress = onProgress
            )
            mp4File.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Direct story video composer error: ${e.message}", e)
            ""
        }
    }

    suspend fun generateBroadcastPodcast(
        context: Context,
        topic: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val prompt = "Write an engaging 2-host radio podcast dialogue between **Vikram:** and **Zara:** about: $topic"
            TitanDirectEngine.executeChat(
                context = context,
                modelId = "gemini-3.5-flash-lite",
                messages = listOf(ChatMessage(sender = SenderRole.USER, content = prompt))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Direct podcast error: ${e.message}", e)
            ""
        }
    }
}
