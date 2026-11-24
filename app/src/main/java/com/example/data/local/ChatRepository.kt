package com.example.data.local

import android.content.Context
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.ChatHistoryEntity
import com.example.data.local.db.UserMemoryVaultEntity
import com.example.data.local.server.LocalHttpServer
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.GeminiModelsList
import com.example.data.model.ModelInfo
import com.example.data.model.SupportedLanguage
import com.example.data.model.VoicePersona
import com.example.data.model.VoicePersonas
import com.example.data.remote.GeminiApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ChatRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val chatDao = db.chatHistoryDao()
    private val memoryDao = db.userMemoryVaultDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    val localHttpServer = LocalHttpServer(8080)

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _selectedModel = MutableStateFlow<ModelInfo>(GeminiModelsList.DEFAULT_MODEL)
    val selectedModel: StateFlow<ModelInfo> = _selectedModel.asStateFlow()

    private val _userMemories = MutableStateFlow<List<UserMemoryVaultEntity>>(emptyList())
    val userMemories: StateFlow<List<UserMemoryVaultEntity>> = _userMemories.asStateFlow()

    private val _selectedPersona = MutableStateFlow<VoicePersona>(VoicePersonas.ZARA)
    val selectedPersona: StateFlow<VoicePersona> = _selectedPersona.asStateFlow()

    private val _speechRate = MutableStateFlow(1.3f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _selectedLanguage = MutableStateFlow<SupportedLanguage>(VoicePersonas.SUPPORTED_LANGUAGES[0])
    val selectedLanguage: StateFlow<SupportedLanguage> = _selectedLanguage.asStateFlow()

    private val _baseUrl = MutableStateFlow(GeminiApiService.DEFAULT_BASE_URL)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _wakeWordSensitivity = MutableStateFlow(0.5f)
    val wakeWordSensitivity: StateFlow<Float> = _wakeWordSensitivity.asStateFlow()

    init {
        localHttpServer.start()
        loadPersistedData()
    }

    private fun loadPersistedData() {
        scope.launch {
            // Load Room DB Chat History
            chatDao.getAllSessions().collect { entities ->
                val loadedSessions = entities.map { entity ->
                    ChatSession(
                        id = entity.id,
                        title = entity.title,
                        modelId = entity.modelId,
                        messages = deserializeMessages(entity.messagesJson),
                        createdAt = entity.createdAt,
                        updatedAt = entity.updatedAt
                    )
                }

                if (loadedSessions.isNotEmpty()) {
                    _sessions.value = loadedSessions
                    if (_activeSessionId.value == null) {
                        _activeSessionId.value = loadedSessions.first().id
                    }
                } else {
                    createNewSession()
                }
            }
        }

        scope.launch {
            // Load User Memory Vault
            memoryDao.getAllMemories().collect { memories ->
                _userMemories.value = memories
                // If vault is empty, populate initial default identity memory
                if (memories.isEmpty()) {
                    saveMemory("creator_identity", "Titan AI Agent developed by Developer Tahir (@Tahirsiddik)")
                }
            }
        }

        val prefs = context.getSharedPreferences("titan_prefs", Context.MODE_PRIVATE)
        _wakeWordSensitivity.value = prefs.getFloat("wake_word_sensitivity", 0.5f)
        val savedPersonaId = prefs.getString("selected_persona_id", VoicePersonas.ZARA.id) ?: VoicePersonas.ZARA.id
        _selectedPersona.value = VoicePersonas.getById(savedPersonaId)
    }

    fun createNewSession(): ChatSession {
        val newSession = ChatSession(
            title = "New Chat",
            modelId = _selectedModel.value.id
        )
        _sessions.value = listOf(newSession) + _sessions.value
        _activeSessionId.value = newSession.id
        persistSession(newSession)
        return newSession
    }

    fun selectSession(id: String) {
        _activeSessionId.value = id
    }

    fun deleteSession(id: String) {
        val sessionToDelete = id
        _sessions.value = _sessions.value.filter { it.id != id }
        scope.launch { chatDao.deleteSessionById(sessionToDelete) }

        if (_activeSessionId.value == id) {
            if (_sessions.value.isNotEmpty()) {
                _activeSessionId.value = _sessions.value.first().id
            } else {
                createNewSession()
            }
        }
    }

    fun updateSelectedModel(model: ModelInfo) {
        _selectedModel.value = model
    }

    fun updateVoicePersona(persona: VoicePersona) {
        _selectedPersona.value = persona
        val prefs = context.getSharedPreferences("titan_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_persona_id", persona.id).putString("selected_voice_persona", persona.id).apply()
    }

    fun updateSpeechRate(rate: Float) {
        _speechRate.value = rate
    }

    fun updateLanguage(language: SupportedLanguage) {
        _selectedLanguage.value = language
    }

    fun saveMemory(key: String, value: String) {
        scope.launch {
            memoryDao.saveMemory(UserMemoryVaultEntity(key = key, value = value))
        }
    }

    fun deleteMemory(key: String) {
        scope.launch {
            memoryDao.deleteMemory(key)
        }
    }

    fun clearAllMemories() {
        scope.launch {
            memoryDao.clearAllMemories()
        }
    }

    fun addMessageToActiveSession(message: ChatMessage) {
        val activeId = _activeSessionId.value ?: return

        // Auto Memory Fact Extraction (e.g., "আমার নাম তাহির", "My name is Tahir")
        checkAndExtractUserMemory(message.content)

        _sessions.value = _sessions.value.map { session ->
            if (session.id == activeId) {
                val updatedMessages = session.messages + message
                val autoTitle = if (session.title == "New Chat" && message.sender == com.example.data.model.SenderRole.USER) {
                    message.content.take(30) + if (message.content.length > 30) "..." else ""
                } else {
                    session.title
                }
                val updatedSession = session.copy(
                    title = autoTitle,
                    messages = updatedMessages,
                    updatedAt = System.currentTimeMillis()
                )
                persistSession(updatedSession)
                updatedSession
            } else {
                session
            }
        }
    }

    private fun checkAndExtractUserMemory(prompt: String) {
        if (prompt.contains("আমার নাম", ignoreCase = true) || prompt.contains("my name is", ignoreCase = true)) {
            val name = prompt.replace("আমার নাম", "", ignoreCase = true)
                .replace("my name is", "", ignoreCase = true)
                .replace("মনে রেখো", "", ignoreCase = true)
                .trim()
            if (name.isNotEmpty()) {
                saveMemory("user_name", name)
            }
        }
    }

    fun updateMessageInActiveSession(messageId: String, persist: Boolean = true, transform: (ChatMessage) -> ChatMessage) {
        val activeId = _activeSessionId.value ?: return
        val currentSessions = _sessions.value
        val sessionIndex = currentSessions.indexOfFirst { it.id == activeId }
        if (sessionIndex == -1) return
        val session = currentSessions[sessionIndex]

        val updatedMessages = session.messages.map { msg ->
            if (msg.id == messageId) transform(msg) else msg
        }
        val updatedSession = session.copy(messages = updatedMessages)
        val newSessions = currentSessions.toMutableList()
        newSessions[sessionIndex] = updatedSession
        _sessions.value = newSessions

        if (persist) {
            persistSession(updatedSession)
        }
    }

    private fun persistSession(session: ChatSession) {
        scope.launch {
            val entity = ChatHistoryEntity(
                id = session.id,
                title = session.title,
                modelId = session.modelId,
                messagesJson = serializeMessages(session.messages),
                createdAt = session.createdAt,
                updatedAt = session.updatedAt
            )
            chatDao.insertOrUpdateSession(entity)
        }
    }

    private fun serializeMessages(messages: List<ChatMessage>): String {
        val array = JSONArray()
        messages.forEach { msg ->
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("sender", msg.sender.name)
                put("content", msg.content)
                put("thinkingProcess", msg.thinkingProcess ?: "")
                put("attachedImageUri", msg.attachedImageUri ?: "")
                put("attachedFileName", msg.attachedFileName ?: "")
                put("modelUsed", msg.modelUsed)
                put("timestamp", msg.timestamp)
                put("isError", msg.isError)
                put("isSearchGrounded", msg.isSearchGrounded)
                val sourcesArray = JSONArray()
                msg.searchSources.forEach { s ->
                    val sObj = JSONObject().apply {
                        put("title", s.title)
                        put("url", s.url)
                        put("snippet", s.snippet ?: "")
                    }
                    sourcesArray.put(sObj)
                }
                put("searchSources", sourcesArray)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeMessages(json: String): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        if (json.isEmpty()) return list
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val sourcesList = mutableListOf<com.example.data.model.GroundingSource>()
                val sArr = obj.optJSONArray("searchSources")
                if (sArr != null) {
                    for (j in 0 until sArr.length()) {
                        val sItem = sArr.getJSONObject(j)
                        sourcesList.add(
                            com.example.data.model.GroundingSource(
                                title = sItem.optString("title"),
                                url = sItem.optString("url"),
                                snippet = sItem.optString("snippet").ifEmpty { null }
                            )
                        )
                    }
                }

                val msg = ChatMessage(
                    id = obj.optString("id"),
                    sender = com.example.data.model.SenderRole.valueOf(obj.optString("sender", "USER")),
                    content = obj.optString("content"),
                    thinkingProcess = obj.optString("thinkingProcess").ifEmpty { null },
                    attachedImageUri = obj.optString("attachedImageUri").ifEmpty { null },
                    attachedFileName = obj.optString("attachedFileName").ifEmpty { null },
                    modelUsed = obj.optString("modelUsed", "gemini-3.5-flash-lite"),
                    timestamp = obj.optLong("timestamp"),
                    isStreaming = false,
                    isError = obj.optBoolean("isError", false),
                    isSearchGrounded = obj.optBoolean("isSearchGrounded", false),
                    searchSources = sourcesList
                )
                list.add(msg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun updateBaseUrl(url: String) {
        _baseUrl.value = url.ifBlank { GeminiApiService.DEFAULT_BASE_URL }
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    fun updateWakeWordSensitivity(context: Context, value: Float) {
        _wakeWordSensitivity.value = value
        val prefs = context.getSharedPreferences("titan_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("wake_word_sensitivity", value).apply()
    }

    fun getActiveSession(): ChatSession? {
        val activeId = _activeSessionId.value ?: return null
        return _sessions.value.find { it.id == activeId }
    }
}
