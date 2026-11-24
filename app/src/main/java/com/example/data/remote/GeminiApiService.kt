package com.example.data.remote

import com.example.data.model.ChatMessage
import com.example.data.model.GroundingSource
import com.titan.ide.network.TitanDirectEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * GeminiApiService - 100% On-Device Direct Chat Completion Service
 * Powered exclusively by TitanDirectEngine via on-device PSID / PSIDTS cookies.
 * ZERO Render Proxy Servers, ZERO Google Cloud API Keys required.
 */
class GeminiApiService {

    companion object {
        val DEFAULT_BASE_URL: String
            get() = com.example.util.TitanSecurityShield.getGatewayBaseUrl()
        private const val TAG = "GeminiApiService"

        const val SYSTEM_PROMPT = """You are Titan IDE by Developer Tahir (@Tahirsiddik), an AI IDE and autonomous Android assistant. Answer directly in the user's language with zero filler. If the user asks you to control their phone or perform a multi-app task (open app, send WhatsApp, generate image in another app, call, play video, scroll), confirm briefly and append `[TITAN_GOAL: <full task>]` at the end."""
    }

    data class StreamChunk(
        val textContent: String = "",
        val thinkingContent: String = "",
        val isFinished: Boolean = false,
        val isSearchGrounded: Boolean = false,
        val sources: List<GroundingSource> = emptyList(),
        val error: String? = null
    )

    fun sendChatCompletionStream(
        messages: List<ChatMessage>,
        modelId: String,
        baseUrl: String = DEFAULT_BASE_URL,
        apiKey: String? = null,
        enableSearchGrounding: Boolean = false,
        userMemoriesPrompt: String? = null
    ): Flow<StreamChunk> = kotlinx.coroutines.flow.flow {
        var finalMessages = messages
        var groundingSources: List<GroundingSource> = emptyList()

        if (enableSearchGrounding) {
            val lastQuery = messages.lastOrNull { it.sender == com.example.data.model.SenderRole.USER }?.content.orEmpty()
            if (lastQuery.isNotBlank()) {
                try {
                    val sources = com.titan.ide.network.TitanWebSearchGrounding.search(lastQuery, maxResults = 3)
                    if (sources.isNotEmpty()) {
                        groundingSources = sources
                        val groundingBlock = com.titan.ide.network.TitanWebSearchGrounding.buildGroundingPromptBlock(sources)
                        val lastIdx = finalMessages.indexOfLast { it.sender == com.example.data.model.SenderRole.USER }
                        if (lastIdx != -1) {
                            val originalUserMsg = finalMessages[lastIdx]
                            val augmented = originalUserMsg.copy(content = "$groundingBlock\n${originalUserMsg.content}")
                            finalMessages = finalMessages.toMutableList().apply { set(lastIdx, augmented) }
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        if (groundingSources.isNotEmpty()) {
            emit(StreamChunk(isSearchGrounded = true, sources = groundingSources))
        }

        TitanDirectEngine.executeChat(
            messages = finalMessages,
            modelId = modelId
        ).collect { delta ->
            if (delta.isNotBlank()) {
                emit(StreamChunk(textContent = delta, isFinished = false, isSearchGrounded = groundingSources.isNotEmpty(), sources = groundingSources))
            }
        }
        emit(StreamChunk(textContent = "", isFinished = true, isSearchGrounded = groundingSources.isNotEmpty(), sources = groundingSources))
    }
}
