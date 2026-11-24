package com.example.data.model

import java.util.UUID

enum class SenderRole {
    USER,
    GEMINI
}

data class GroundingSource(
    val title: String,
    val url: String,
    val snippet: String? = null
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: SenderRole,
    val content: String,
    val thinkingProcess: String? = null,
    val attachedImageUri: String? = null,
    val attachedFileName: String? = null,
    val modelUsed: String = "gemini-4-hydrogen",
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val isSearchGrounded: Boolean = false,
    val searchSources: List<GroundingSource> = emptyList()
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val modelId: String = "gemini-4-hydrogen",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
