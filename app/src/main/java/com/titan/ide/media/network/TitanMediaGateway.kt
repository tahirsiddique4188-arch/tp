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
        topic: String,
        host1Name: String = "Vikram",
        host2Name: String = "Zara"
    ): String = withContext(Dispatchers.IO) {
        try {
            val lang = com.example.voice.TitanAssistantConfig.getSelectedLanguage(context)
            val langLine = if (lang.promptDirective.isNotBlank()) lang.promptDirective else "Write in the exact same language as the topic."
            val prompt = buildString {
                append("Write a natural, lively 2-character radio podcast dialogue between ")
                append("**$host1Name:** and **$host2Name:** about: $topic. ")
                append("$langLine ")
                append("STRICT FORMAT: Every single spoken line MUST start with either **$host1Name:** or **$host2Name:** on its own line so our dual-voice audio engine can speak each character in their own voice! ")
                append("Keep it 8 to 12 engaging alternating turns with zero stage directions in parentheses.")
            }
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
