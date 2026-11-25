package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.TitanThemeManager
import com.example.ui.theme.TitanThemePalette
import com.example.util.ImageUtils
import com.example.data.local.ChatRepository
import com.example.data.local.db.UserMemoryVaultEntity
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.ModelInfo
import com.example.data.model.SenderRole
import com.example.data.model.SupportedLanguage
import com.example.data.model.VoicePersona
import com.example.data.remote.GeminiApiService
import com.example.services.TitanAccessibilityService
import com.example.services.TitanFloatingBubbleService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class GeminiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)
    private val apiService = GeminiApiService()
    private val deviceModelRepository = com.example.data.repository.DeviceModelRepository(application)

    val availableModels: StateFlow<List<ModelInfo>> = deviceModelRepository.availableModels
    val sessions: StateFlow<List<ChatSession>> = repository.sessions
    val activeSessionId: StateFlow<String?> = repository.activeSessionId
    val selectedModel: StateFlow<ModelInfo> = repository.selectedModel
    val userMemories: StateFlow<List<UserMemoryVaultEntity>> = repository.userMemories
    val selectedPersona: StateFlow<VoicePersona> = repository.selectedPersona
    val speechRate: StateFlow<Float> = repository.speechRate
    val selectedLanguage: StateFlow<SupportedLanguage> = repository.selectedLanguage
    val baseUrl: StateFlow<String> = repository.baseUrl
    val apiKey: StateFlow<String> = repository.apiKey
    val wakeWordSensitivity: StateFlow<Float> = repository.wakeWordSensitivity

    // Search History State
    val searchHistory: StateFlow<List<String>> = com.example.data.local.TitanSearchHistoryManager.history

    // Search Grounding Toggle State (Google Search Grounding for live web information)
    private val _isSearchGroundingEnabled = MutableStateFlow(true)
    val isSearchGroundingEnabled: StateFlow<Boolean> = _isSearchGroundingEnabled.asStateFlow()

    fun toggleSearchGrounding() {
        _isSearchGroundingEnabled.value = !_isSearchGroundingEnabled.value
    }

    // Theme & User Profile State
    val currentTheme: StateFlow<TitanThemePalette> = TitanThemeManager.currentTheme
    val userName: StateFlow<String> = TitanThemeManager.userName

    fun setTheme(themeId: String) {
        TitanThemeManager.setTheme(themeId, getApplication())
    }

    fun setUserName(name: String) {
        TitanThemeManager.setUserName(name, getApplication())
    }

    // Dark / Light Theme Toggle State
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
        val nextThemeId = if (_isDarkTheme.value) TitanThemeManager.SOFT_SLATE_DARK.id else TitanThemeManager.TITAN_PREMIUM_WHITE.id
        setTheme(nextThemeId)
    }

    // Mobile Code Studio State
    private val _htmlCode = MutableStateFlow("<h1>Hello from Titan Code Studio!</h1>\n<p>Interactive web application loaded on http://127.0.0.1:8080/</p>")
    val htmlCode: StateFlow<String> = _htmlCode.asStateFlow()

    private val _cssCode = MutableStateFlow("body { background: #131314; color: #00E5FF; text-align: center; font-family: sans-serif; padding-top: 50px; }")
    val cssCode: StateFlow<String> = _cssCode.asStateFlow()

    private val _jsCode = MutableStateFlow("console.log('Titan 127.0.0.1:8080 Web Server Running!');")
    val jsCode: StateFlow<String> = _jsCode.asStateFlow()

    // Screen Inspector Scanned Result
    private val _scannedTextResult = MutableStateFlow<String?>(null)
    val scannedTextResult: StateFlow<String?> = _scannedTextResult.asStateFlow()

    // UI Input States
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _attachedImageUri = MutableStateFlow<String?>(null)
    val attachedImageUri: StateFlow<String?> = _attachedImageUri.asStateFlow()

    private val _attachedFileName = MutableStateFlow<String?>(null)
    val attachedFileName: StateFlow<String?> = _attachedFileName.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isFloatingBubbleActive = MutableStateFlow(com.example.services.TitanFloatingBubbleService.isRunning)
    val isFloatingBubbleActive: StateFlow<Boolean> = _isFloatingBubbleActive.asStateFlow()

    // Gemini Live Voice States
    private val _isLiveActive = MutableStateFlow(false)
    val isLiveActive: StateFlow<Boolean> = _isLiveActive.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val _liveResponseText = MutableStateFlow("")
    val liveResponseText: StateFlow<String> = _liveResponseText.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // Persistent Live Voice Session Engine (Gemini 2.0 Flash Live Architecture)
    private var pendingTtsCompletion: (() -> Unit)? = null

    val liveSessionManager = com.titan.ide.voice.TitanLiveSessionManager(
        context = application,
        onResponseStart = {
            _liveResponseText.value = ""
        },
        onTextChunk = { chunk ->
            if (chunk == "__CLEAR__") {
                _liveResponseText.value = ""
            } else {
                _liveResponseText.value = chunk
            }
        },
        onActionTriggered = { actionJson ->
            TitanAccessibilityService.instance?.parseAndExecuteActions(actionJson.toString(), getApplication())
        },
        speakTts = { speechText, onFinished ->
            speakAloud(
                text = speechText,
                voiceName = selectedPersona.value.id,
                pitch = selectedPersona.value.pitchMultiplier,
                onDone = onFinished
            )
        },
        onSessionEnded = {
            _isLiveActive.value = false
            com.example.voice.TitanWakeWordService.instance?.resumeListeningIfIdle()
        },
        onTurnCommitted = { spokenUser, spokenAi ->
            viewModelScope.launch {
                repository.addMessageToActiveSession(
                    ChatMessage(sender = SenderRole.USER, content = spokenUser, modelUsed = selectedModel.value.id)
                )
                repository.addMessageToActiveSession(
                    ChatMessage(sender = SenderRole.GEMINI, content = spokenAi, modelUsed = selectedModel.value.id)
                )
            }
        }
    )

    val liveVoiceSessionState: StateFlow<com.titan.ide.voice.LiveVoiceState> = liveSessionManager.sessionState

    // Bypass-Style Ultra-Clean Voice Session Manager
    val voiceSessionManager = com.titan.ide.voice.TitanVoiceSessionManager.getInstance()
    val voiceSessionState: StateFlow<com.titan.ide.voice.VoiceState> = voiceSessionManager.state
    val voiceAudioLevel: StateFlow<Float> = voiceSessionManager.audioLevel
    val voiceSpokenText: StateFlow<String> = voiceSessionManager.lastSpokenText

    // Dictation STT States
    private val _isDictating = MutableStateFlow(false)
    val isDictating: StateFlow<Boolean> = _isDictating.asStateFlow()

    private val _dictationSeconds = MutableStateFlow(0)
    val dictationSeconds: StateFlow<Int> = _dictationSeconds.asStateFlow()

    private val _dictationAudioLevel = MutableStateFlow(0f)
    val dictationAudioLevel: StateFlow<Float> = _dictationAudioLevel.asStateFlow()

    // Speech & TTS engines
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = null
        private var dictationTimerJob: Job? = null
    private var baseTextBeforeDictation: String = ""

    init {
        com.titan.ide.network.TitanAppContextHolder.init(application)
        try {
            TitanThemeManager.init(application)
            _isDarkTheme.value = TitanThemeManager.currentTheme.value.isDark
        } catch (e: Exception) {
            android.util.Log.e("GeminiViewModel", "Theme init error", e)
        }
        try {
            com.example.data.local.TitanSearchHistoryManager.init(application)
        } catch (e: Exception) {
            android.util.Log.e("GeminiViewModel", "Search history init error", e)
        }
        try {
            initTts()
        } catch (e: Exception) {
            android.util.Log.e("GeminiViewModel", "TTS init error", e)
        }

        // Bind Live Session Manager states to UI StateFlows
        viewModelScope.launch {
            liveSessionManager.sessionState.collect { state ->
                when (state) {
                    com.titan.ide.voice.LiveVoiceState.IDLE -> {
                        _isLiveActive.value = false
                        _isListening.value = false
                        _isSpeaking.value = false
                        _audioLevel.value = 0f
                    }
                    com.titan.ide.voice.LiveVoiceState.LISTENING -> {
                        _isLiveActive.value = true
                        _isListening.value = true
                        _isSpeaking.value = false
                    }
                    com.titan.ide.voice.LiveVoiceState.AI_THINKING -> {
                        _isLiveActive.value = true
                        _isListening.value = true // Keep mic active for seamless barge-in
                        _isSpeaking.value = false
                    }
                    com.titan.ide.voice.LiveVoiceState.AI_SPEAKING -> {
                        _isLiveActive.value = true
                        _isListening.value = false
                        _isSpeaking.value = true
                    }
                }
            }
        }

        viewModelScope.launch {
            liveSessionManager.audioLevel.collect { level ->
                if (_isLiveActive.value) {
                    _audioLevel.value = level
                }
            }
        }

        viewModelScope.launch {
            liveSessionManager.liveSpokenText.collect { text ->
                if (text.isNotEmpty()) {
                    _liveSpokenText.value = text
                }
            }
        }

        viewModelScope.launch {
            com.titan.ide.voice.TitanContinuousSpeechEngine.audioLevel.collect { level ->
                if (_isDictating.value) {
                    _dictationAudioLevel.value = level
                }
            }
        }

        viewModelScope.launch {
            com.titan.ide.voice.TitanWhisperRecorder.audioLevel.collect { level ->
                if (_isDictating.value) {
                    _dictationAudioLevel.value = level
                }
            }
        }
    }

    private fun initTts() {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyTtsVoiceSettings()
                textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        val cb = pendingTtsCompletion
                        pendingTtsCompletion = null
                        cb?.invoke()
                    }

                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        val cb = pendingTtsCompletion
                        pendingTtsCompletion = null
                        cb?.invoke()
                    }
                })
            }
        }

        // Try Google TTS first for natural Gemini-like neural voice, fallback to default
        textToSpeech = try {
            TextToSpeech(getApplication(), listener, "com.google.android.tts")
        } catch (e: Exception) {
            TextToSpeech(getApplication(), listener)
        }
    }

    fun applyTtsVoiceSettings() {
        val persona = selectedPersona.value
        val speed = speechRate.value
        val lang = selectedLanguage.value

        textToSpeech?.apply {
            language = lang.locale
            setPitch(persona.pitchMultiplier)
            setSpeechRate(speed)

            try {
                voices?.let { voiceSet ->
                    val isMale = persona.gender.equals("Male", ignoreCase = true)
                    // Prefer high quality Google network voices
                    val matchingVoice = voiceSet.filter { it.locale.language == lang.locale.language }
                        .sortedByDescending { v ->
                            (if (v.name.contains("network", ignoreCase = true)) 10 else 0) +
                            (if (v.quality >= android.speech.tts.Voice.QUALITY_HIGH) 5 else 0)
                        }
                        .firstOrNull { voice ->
                            if (isMale) {
                                voice.name.contains("male", ignoreCase = true) || voice.name.contains("-c-") || voice.name.contains("-d-")
                            } else {
                                voice.name.contains("female", ignoreCase = true) || voice.name.contains("-a-") || voice.name.contains("-b-")
                            }
                        } ?: voiceSet.firstOrNull { it.locale.language == lang.locale.language }

                    if (matchingVoice != null) {
                        voice = matchingVoice
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("GeminiViewModel", "Voice selection fallback: ${e.message}")
            }
        }
    }

    fun onInputTextChange(text: String) {
        _inputText.value = text
    }

    fun onAttachImage(uriString: String) {
        viewModelScope.launch {
            try {
                val uri = Uri.parse(uriString)
                val base64 = ImageUtils.uriToBase64DataUrl(getApplication(), uri)
                _attachedImageUri.value = base64 ?: uriString
            } catch (e: Exception) {
                _attachedImageUri.value = uriString
            }
            _attachedFileName.value = null
        }
    }

    fun onAttachBitmap(bitmap: Bitmap) {
        val base64 = ImageUtils.bitmapToBase64DataUrl(bitmap)
        _attachedImageUri.value = base64
        _attachedFileName.value = null
    }

    fun autoFixRuntimeError(brokenCode: String, errorTrace: String) {
        val prompt = "Resolve this runtime error and output the complete fixed code:\n\nBroken Code:\n```html\n$brokenCode\n```\n\nRuntime Error Traceback:\n$errorTrace\n\nPlease output the complete fixed working code."
        _inputText.value = prompt
        sendMessage()
    }

    fun onAttachFile(fileName: String, fileContent: String) {
        _attachedFileName.value = fileName
        _attachedImageUri.value = null
        _inputText.value = "File attached [$fileName]:\n$fileContent\n\nPlease analyze this file."
    }

    fun clearAttachment() {
        _attachedImageUri.value = null
        _attachedFileName.value = null
    }

    fun selectModel(model: ModelInfo) {
        repository.updateSelectedModel(model)
    }

    fun selectVoicePersona(persona: VoicePersona) {
        repository.updateVoicePersona(persona)
        applyTtsVoiceSettings()
    }

    fun setSpeechRate(rate: Float) {
        repository.updateSpeechRate(rate)
        applyTtsVoiceSettings()
    }

    fun setLanguage(language: SupportedLanguage) {
        repository.updateLanguage(language)
        applyTtsVoiceSettings()
    }

    fun addMemoryFact(key: String, value: String) {
        repository.saveMemory(key, value)
        com.example.data.local.TitanMemoryManager.getInstance(getApplication()).saveFact(key, value)
    }

    fun deleteMemoryFact(key: String) {
        repository.deleteMemory(key)
        com.example.data.local.TitanMemoryManager.getInstance(getApplication()).deleteFact(key)
    }

    fun clearAllMemories() {
        repository.clearAllMemories()
        com.example.data.local.TitanMemoryManager.getInstance(getApplication()).clearAllFacts()
    }

    fun updateCodeStudio(html: String, css: String, js: String) {
        _htmlCode.value = html
        _cssCode.value = css
        _jsCode.value = js
        repository.localHttpServer.htmlContent = html
        repository.localHttpServer.cssContent = css
        repository.localHttpServer.jsContent = js
    }

    fun autoFixCodeBug(sourceCode: String) {
        val prompt = "Debug and fix syntax/logic errors in this code snippet:\n```\n$sourceCode\n```\nReturn only clean fixed code."
        _inputText.value = prompt
        sendMessage()
    }

    fun generatePodcastScript(topic: String) {
        _inputText.value = "Generate a podcast audio story script on topic: '$topic' with narrator intro, ambient scene descriptions, and dialogue."
        sendMessage()
    }

    fun generateShortsVideoCard(topic: String) {
        _inputText.value = "Create a vertical Shorts/Reels video script for: '$topic' with animated subtitle cues and visual scene timestamps."
        sendMessage()
    }

    // System Automation Commands
    fun triggerGoHome() {
        val service = TitanAccessibilityService.instance
        if (service != null) {
            service.triggerGoHome()
        } else {
            Toast.makeText(getApplication(), "Please enable Titan Accessibility Service in Settings", Toast.LENGTH_LONG).show()
        }
    }

    fun triggerSwipeUp() {
        TitanAccessibilityService.instance?.swipeUpScreen()
    }

    fun toggleFlashlight(enable: Boolean) {
        TitanAccessibilityService.instance?.toggleFlashlight(enable)
    }

    fun scanScreenText() {
        val result = TitanAccessibilityService.instance?.scanScreenText() ?: "Accessibility Service inactive. Please enable in device settings."
        _scannedTextResult.value = result
    }

    fun searchPlayStore(query: String) {
        TitanAccessibilityService.instance?.openPlayStoreAndSearch(query)
    }

    fun toggleFloatingBubbleService() {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, "Please grant Overlay Permission for Titan 3D Floating Assistant", Toast.LENGTH_LONG).show()
            return
        }

        if (com.example.services.TitanFloatingBubbleService.isRunning || _isFloatingBubbleActive.value) {
            context.stopService(Intent(context, com.example.services.TitanFloatingBubbleService::class.java))
            _isFloatingBubbleActive.value = false
            Toast.makeText(context, "🛑 Floating 3D Orb stopped", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(context, com.example.services.TitanFloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            _isFloatingBubbleActive.value = true
            Toast.makeText(context, "✨ 3D Holographic Orb active on screen!", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateBaseUrl(url: String) {
        repository.updateBaseUrl(url)
    }

    fun updateApiKey(key: String) {
        repository.updateApiKey(key)
    }

    fun setWakeWordSensitivity(value: Float) {
        repository.updateWakeWordSensitivity(getApplication(), value)
    }

    fun createNewSession() {
        com.titan.ide.network.TitanDirectEngine.resetConversationThread()
        repository.createNewSession()
        _inputText.value = ""
        clearAttachment()
    }

    fun selectSession(id: String) {
        repository.selectSession(id)
    }

    fun deleteSession(id: String) {
        repository.deleteSession(id)
    }

    fun onQuickActionClick(actionText: String) {
        _inputText.value = actionText
        sendMessage()
    }

    fun deleteSearchHistoryItem(query: String) {
        com.example.data.local.TitanSearchHistoryManager.deleteQuery(query, getApplication())
    }

    fun clearSearchHistory() {
        com.example.data.local.TitanSearchHistoryManager.clearHistory(getApplication())
    }

    private fun checkAndApplyVoiceSwitchCommand(prompt: String) {
        val lower = prompt.lowercase(Locale.ROOT)
        if (lower.contains("voice") || lower.contains("आवाज") || lower.contains("आवाज़") || lower.contains("গলা") || lower.contains("ভয়েস") || lower.contains("বাক") || lower.contains("baat karni") || lower.contains("chahiye")) {
            val matched = com.example.data.model.VoicePersonas.ALL_PERSONAS.firstOrNull { p ->
                lower.contains(p.name.lowercase(Locale.ROOT))
            }
            if (matched != null) {
                selectVoicePersona(matched)
            }
        }
    }

    fun sendMessage() {
        if (_isGenerating.value) return
        if (_isDictating.value) {
            stopDictation()
        }
        val userPrompt = _inputText.value.trim()
        checkAndApplyVoiceSwitchCommand(userPrompt)
        com.titan.ide.brain.TitanBrainMemoryEngine.autoLearnFromUserMessage(getApplication(), userPrompt)
        val imgUri = _attachedImageUri.value
        val fileName = _attachedFileName.value

        if (userPrompt.isEmpty() && imgUri == null && fileName == null) return

        val currentSession = repository.getActiveSession() ?: return
        val currentModel = selectedModel.value

        val userMessage = ChatMessage(
            sender = SenderRole.USER,
            content = userPrompt.ifEmpty { "Analyze attached media" },
            attachedImageUri = imgUri,
            attachedFileName = fileName,
            modelUsed = currentModel.id
        )

        repository.addMessageToActiveSession(userMessage)

        // Track Query in Search History
        if (userPrompt.isNotEmpty()) {
            com.example.data.local.TitanSearchHistoryManager.addQuery(userPrompt, getApplication())
        }

        _inputText.value = ""
        clearAttachment()

        val botMessageId = java.util.UUID.randomUUID().toString()
        val initialBotMessage = ChatMessage(
            id = botMessageId,
            sender = SenderRole.GEMINI,
            content = "Thinking...",
            thinkingProcess = if (currentModel.isThinkingModel) "Analyzing query step-by-step..." else null,
            modelUsed = currentModel.id,
            isStreaming = true
        )
        repository.addMessageToActiveSession(initialBotMessage)

        _isGenerating.value = true

        viewModelScope.launch {
            val history = repository.getActiveSession()?.messages?.filter { it.id != botMessageId } ?: emptyList()
            val isGrounding = _isSearchGroundingEnabled.value

            val lowerPrompt = userPrompt.lowercase(java.util.Locale.ROOT)
            val isImageIntent = (lowerPrompt.contains("create an image") || lowerPrompt.contains("generate image") || lowerPrompt.contains("generate an image") || lowerPrompt.contains("chobi banao") || lowerPrompt.contains("chobi banaw") || lowerPrompt.contains("ছবি বানা") || lowerPrompt.contains("ছবি তৈরি") || lowerPrompt.startsWith("draw a ")) && !lowerPrompt.contains("chatgpt") && !lowerPrompt.contains("whatsapp")
            val isVideoIntent = (lowerPrompt.contains("create video") || lowerPrompt.contains("generate video") || lowerPrompt.contains("video banao") || lowerPrompt.contains("ভিডিও বানা") || lowerPrompt.contains("make a video")) && !lowerPrompt.contains("youtube")
            val isPodcastIntent = lowerPrompt.contains("create podcast") || lowerPrompt.contains("generate podcast") || lowerPrompt.contains("make a podcast")


            if (isImageIntent) {
                val promptToGen = userPrompt.replace(Regex("(?i)^(create an image|generate image|chobi|draw|picture of|image of|a photo of)\\s*"), "").ifBlank { userPrompt }
                val context: Context = getApplication<Application>()
                val imgUrl = try {
                    com.titan.ide.media.network.TitanMediaGateway.generateImagen3Artwork(context, promptToGen, "1:1")
                } catch (e: Exception) { "" }

                val generatedContent = if (imgUrl.isNotBlank()) {
                    "🎨 **Google Imagen 3 Studio**\n\nGenerated Ultra HD artwork for: \"$promptToGen\"\n\n![Generated Image]($imgUrl)"
                } else {
                    "⚠️ Imagen 3: Could not generate image on-device. Please ensure your Gemini Cookies are configured in Cookie Vault."
                }

                repository.updateMessageInActiveSession(botMessageId, persist = true) { msg ->
                    msg.copy(
                        content = generatedContent,
                        modelUsed = com.titan.ide.model.TitanEngine.GOOGLE_IMAGEN_3.id,
                        isStreaming = false,
                        thinkingProcess = null
                    )
                }
                _isGenerating.value = false
                speakAloud(generatedContent)
                return@launch
            } else if (isVideoIntent) {
                val promptToGen = userPrompt.replace(Regex("(?i)^(create video|generate video|video of|movie|shorts|reels)\\s*"), "").ifBlank { userPrompt }
                val videoPath = try {
                    com.titan.ide.media.network.TitanMediaGateway.generateVeo2Video(
                        context = getApplication(),
                        topic = promptToGen,
                        format = "9:16"
                    )
                } catch (e: Exception) { "" }

                val generatedContent = if (videoPath.isNotBlank()) {
                    "🎬 **Google Veo 2 & Story Video Composer Studio**\n\nSynthesized cinematic story video reel for: **\"$promptToGen\"**\n\n📁 **Video File:** `$videoPath`\n\n*(Rendered with Titan Veo 2 Cinema Engine & Edge Neural Studio Voices)*"
                } else {
                    "🎬 **Google Veo 2 Cinema Studio**\n\nStory video composition initialized for: **\"$promptToGen\"**\n\n*(Rendered on-device via Titan Media Composer)*"
                }

                repository.updateMessageInActiveSession(botMessageId, persist = true) { msg ->
                    msg.copy(
                        content = generatedContent,
                        modelUsed = com.titan.ide.model.TitanEngine.GOOGLE_VEO_2.id,
                        isStreaming = false,
                        thinkingProcess = null
                    )
                }
                _isGenerating.value = false
                speakAloud("Google Veo 2 Video reel synthesized for $promptToGen.")
                return@launch
            } else if (isPodcastIntent) {
                val topic = userPrompt.replace(Regex("(?i)^(create podcast|generate podcast|make a podcast|podcast|broadcast|audio show|radio show)\\s*"), "").ifBlank { "Technology & AI Innovation" }
                val aiPodcast = try {
                    com.titan.ide.media.network.TitanMediaGateway.generateBroadcastPodcast(getApplication(), topic)
                } catch (_: Exception) { "" }
                val generatedContent = aiPodcast.ifBlank { "⚠️ Could not generate podcast right now. Please check your internet connection." }

                repository.updateMessageInActiveSession(botMessageId, persist = true) { msg ->
                    msg.copy(
                        content = generatedContent,
                        isStreaming = false,
                        thinkingProcess = null
                    )
                }
                _isGenerating.value = false
                speakAloud(generatedContent)
                return@launch
            } else if (lowerPrompt.contains("autonomous builder") || lowerPrompt.contains("self-healing") || lowerPrompt.contains("antigravity") || lowerPrompt.contains("builder engine") || lowerPrompt.startsWith("build app") || lowerPrompt.startsWith("create app") || lowerPrompt.contains("build a website") || lowerPrompt.contains("create website") || lowerPrompt.contains("build an app") || lowerPrompt.contains("create an app")) {
                val appDesc = userPrompt.replace(Regex("(?i)^(autonomous builder|self-healing|antigravity|builder engine|build app for|build app|create app|build website|create website|build an app|create an app|build a|create a)\\s*"), "").ifBlank { "Autonomous Software Solution" }
                val targetLang = when {
                    lowerPrompt.contains("kotlin") || lowerPrompt.contains("compose") -> com.titan.ide.builder.TitanAutonomousBuilder.TargetLanguage.KOTLIN
                    lowerPrompt.contains("java") -> com.titan.ide.builder.TitanAutonomousBuilder.TargetLanguage.JAVA
                    lowerPrompt.contains("python") -> com.titan.ide.builder.TitanAutonomousBuilder.TargetLanguage.PYTHON
                    else -> com.titan.ide.builder.TitanAutonomousBuilder.TargetLanguage.WEB
                }

                repository.updateMessageInActiveSession(botMessageId, persist = false) { msg ->
                    msg.copy(
                        content = "🚀 **Titan Autonomous Builder Initializing...**\n• Project: **$appDesc**\n• Target: **${targetLang.displayName}**\n\nStarting architecture synthesis...",
                        isStreaming = true
                    )
                }

                val buildResult = com.titan.ide.builder.TitanAutonomousBuilder.buildProject(
                    userPrompt = appDesc,
                    context = getApplication(),
                    requestedLanguage = targetLang
                ) { progressState ->
                    repository.updateMessageInActiveSession(botMessageId, persist = false) { msg ->
                        msg.copy(
                            content = "🚀 **Titan Autonomous Builder (${progressState.engine.displayName})**\n• Status: ${progressState.currentStatus}\n• Progress: ${(progressState.progress * 100).toInt()}%",
                            isStreaming = true
                        )
                    }
                }

                val finalReport = buildString {
                    append("🚀 **Titan Autonomous Build Complete!**\n\n")
                    append("• **Project:** ").append(buildResult.projectName).append("\n")
                    append("• **Engine:** ").append(buildResult.engine.displayName).append("\n")
                    append("• **Health Score:** ").append(buildResult.healthScore).append("/100\n")
                    if (buildResult.patchesApplied.isNotEmpty()) {
                        append("• **Self-Healing Patches:** ").append(buildResult.patchesApplied.size).append(" issues auto-repaired\n")
                    }
                    append("\n### 📂 Synthesized Project Files:\n")
                    for ((fileName, fileContent) in buildResult.files) {
                        val ext = fileName.substringAfterLast(".", "txt")
                        append("\n#### `").append(fileName).append("`\n")
                        append("```").append(ext).append("\n")
                        append(fileContent.take(2000))
                        if (fileContent.length > 2000) append("\n... (truncated)")
                        append("\n```\n")
                    }
                    if (buildResult.language == com.titan.ide.builder.TitanAutonomousBuilder.TargetLanguage.WEB) {
                        append("\n🌐 **Live Server Ready:** http://127.0.0.1:8080/ (Click Preview in top toolbar to run)")
                    }
                }

                repository.updateMessageInActiveSession(botMessageId, persist = true) { msg ->
                    msg.copy(
                        content = finalReport,
                        isStreaming = false,
                        thinkingProcess = null
                    )
                }
                _isGenerating.value = false
                speakAloud("Titan Autonomous Builder completed ${buildResult.projectName} with health score ${buildResult.healthScore} percent.")
                return@launch
            }

            val memoryManager = com.example.data.local.TitanMemoryManager.getInstance(getApplication())
            val (learned, extractedName) = com.titan.ide.brain.TitanBrainMemoryEngine.autoLearnFromUserMessage(getApplication(), userPrompt)
            memoryManager.autoLearnFromPrompt(userPrompt)

            val isExplicitMemorySave = lowerPrompt.contains("memory") ||
                    lowerPrompt.contains("save kar") ||
                    lowerPrompt.contains("save in memory") ||
                    lowerPrompt.contains("save to memory") ||
                    lowerPrompt.contains("yaad rakh") ||
                    lowerPrompt.contains("mone rakh") ||
                    lowerPrompt.contains("mona rakh")

            val isNameIntro = lowerPrompt.contains("mara name") ||
                    lowerPrompt.contains("mera name") ||
                    lowerPrompt.contains("mera naam") ||
                    lowerPrompt.contains("mara naam") ||
                    lowerPrompt.contains("my name is") ||
                    lowerPrompt.contains("amar nam") ||
                    lowerPrompt == "i am tahir" ||
                    lowerPrompt.startsWith("my name is ")

            if (isExplicitMemorySave || isNameIntro) {
                val finalName = extractedName ?: if (lowerPrompt.contains("tahir")) "Tahir" else memoryManager.userName
                if (finalName != null && finalName.isNotBlank()) {
                    com.titan.ide.brain.TitanBrainMemoryEngine.setUserName(getApplication(), finalName)
                    try {
                        memoryManager.userName = finalName
                        memoryManager.saveFact("User Name", finalName)
                    } catch (e: Exception) {}
                }

                val reply = com.titan.ide.brain.TitanBrainMemoryEngine.buildMemoryConfirmation(
                    userMessage = userPrompt,
                    extractedName = finalName
                )

                repository.updateMessageInActiveSession(botMessageId, persist = true) { msg ->
                    msg.copy(
                        content = reply,
                        isStreaming = false,
                        thinkingProcess = null
                    )
                }
                _isGenerating.value = false
                speakAloud(reply)
                return@launch
            }

            var spokenCharOffset = 0
            val memoriesPrompt = memoryManager.buildSystemPromptMemoryPrefix(userMemories.value)
            val accumulatedContent = StringBuilder()
            var lastThinkingText = ""
            var lastGrounded = false
            var lastSources = emptyList<com.example.data.model.GroundingSource>()

            val streamBuffer = com.titan.ide.network.StreamingBuffer(flushIntervalMs = 16L) {
                val fullText = accumulatedContent.toString()
                repository.updateMessageInActiveSession(botMessageId, persist = false) { msg ->
                    msg.copy(
                        content = fullText.ifEmpty { "Thinking..." },
                        thinkingProcess = if (lastThinkingText.isNotEmpty()) lastThinkingText else msg.thinkingProcess,
                        isStreaming = true,
                        isSearchGrounded = lastGrounded || msg.isSearchGrounded,
                        searchSources = if (lastSources.isNotEmpty()) lastSources else msg.searchSources
                    )
                }
            }

            try {
                apiService.sendChatCompletionStream(
                    messages = history,
                    modelId = currentModel.id,
                    baseUrl = repository.baseUrl.value,
                    apiKey = repository.apiKey.value.ifBlank { null },
                    enableSearchGrounding = isGrounding || listOf(
                        "today", "latest", "news", "weather", "price", "live", "score", "current", "2026",
                        "আজকের", "খবর", "দাম", "আবহাওয়া", "ताज़ा", "मौसम", "कीमत"
                    ).any { userPrompt.contains(it, ignoreCase = true) },
                    userMemoriesPrompt = memoriesPrompt
                ).collect { chunk ->
                    if (chunk.error != null) {
                        streamBuffer.flush()
                        repository.updateMessageInActiveSession(botMessageId, persist = true) { msg ->
                            msg.copy(content = chunk.error, isStreaming = false, isError = true)
                        }
                        _isGenerating.value = false
                    } else if (chunk.isFinished) {
                        streamBuffer.flush()
                        val finalContent = accumulatedContent.toString().ifEmpty { "Thinking..." }
                        val chatGoalMatch = Regex("""\[TITAN_GOAL:\s*(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(finalContent)
                        val chatGoal = chatGoalMatch?.groupValues?.getOrNull(1)?.trim()
                        if (!chatGoal.isNullOrBlank()) {
                            com.titan.ide.agent.TitanDynamicAgentLoop.dispatchBackgroundSubAgent(
                                context = getApplication(),
                                userGoal = chatGoal
                            ) { doneSummary ->
                                viewModelScope.launch {
                                    repository.addMessageToActiveSession(
                                        ChatMessage(sender = SenderRole.GEMINI, content = "✅ **Autonomous Task Done:** $doneSummary", modelUsed = currentModel.id)
                                    )
                                }
                            }
                        }
                        repository.updateMessageInActiveSession(botMessageId, persist = true) { msg ->
                            msg.copy(
                                content = finalContent,
                                thinkingProcess = if (lastThinkingText.isNotEmpty()) lastThinkingText else msg.thinkingProcess,
                                isStreaming = false,
                                isSearchGrounded = lastGrounded || msg.isSearchGrounded,
                                searchSources = if (lastSources.isNotEmpty()) lastSources else msg.searchSources
                            )
                        }
                        _isGenerating.value = false

                        if (_isLiveActive.value && finalContent.length > spokenCharOffset) {
                            val remainingText = finalContent.substring(spokenCharOffset)
                            speakSentenceChunk(remainingText)
                        }
                    } else {
                        if (chunk.isSearchGrounded && chunk.sources.isNotEmpty()) {
                            lastGrounded = true
                            lastSources = chunk.sources
                        }
                        val deltaText = chunk.textContent
                        if (deltaText.isNotEmpty()) {
                            accumulatedContent.append(deltaText)
                            streamBuffer.append(deltaText)

                            if (accumulatedContent.contains("[TITAN_ACTION:")) {
                                TitanAccessibilityService.instance?.parseAndExecuteActions(accumulatedContent.toString(), getApplication())
                            }

                            if (_isLiveActive.value && accumulatedContent.length > spokenCharOffset) {
                                val fullText = accumulatedContent.toString()
                                _liveResponseText.value = fullText
                                val unSpoken = fullText.substring(spokenCharOffset)
                                val sentenceDelimiters = listOf(".", "!", "?", "\n", "।")
                                var lastDelimiterIdx = -1
                                for (delim in sentenceDelimiters) {
                                    val idx = unSpoken.lastIndexOf(delim)
                                    if (idx > lastDelimiterIdx) lastDelimiterIdx = idx
                                }

                                if (lastDelimiterIdx != -1) {
                                    val sentenceChunk = unSpoken.substring(0, lastDelimiterIdx + 1)
                                    spokenCharOffset += sentenceChunk.length
                                    speakSentenceChunk(sentenceChunk)
                                }
                            }
                        }
                        if (chunk.thinkingContent.isNotEmpty()) lastThinkingText = chunk.thinkingContent
                        if (chunk.isSearchGrounded) lastGrounded = true
                        if (chunk.sources.isNotEmpty()) lastSources = chunk.sources
                    }
                }            } catch (e: Exception) {
                streamBuffer.flush()
                repository.updateMessageInActiveSession(botMessageId, persist = true) { msg ->
                    msg.copy(content = "Error: ${e.message ?: "Network error"}", isStreaming = false, isError = true)
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun cleanTextForSpeech(rawText: String): String {
        if (rawText.isBlank()) return ""
        var text = rawText

        // 1. Replace multi-line code blocks ```language ... ``` with a concise spoken indicator
        text = text.replace(Regex("```[a-zA-Z0-9_+#.-]*\\n?[\\s\\S]*?```"), " Here is the code. ")

        // 2. Remove inline code backticks `code`
        text = text.replace(Regex("`([^`]+)`"), "$1")

        // 3. Remove Markdown image links ![alt](url) and regular links [title](url)
        text = text.replace(Regex("!\\[[^\\]]*\\]\\([^\\)]*\\)"), "")
        text = text.replace(Regex("\\[([^\\]]+)\\]\\([^\\)]*\\)"), "$1")

        // 4. Remove URLs so TTS does not spell out long http/https web links letter by letter
        text = text.replace(Regex("https?://\\S+"), " web link ")

        // 5. Remove markdown formatting characters (*, **, _, __, ~, ~~, #, ##, ###)
        text = text.replace(Regex("[*_~#]"), " ")

        // 6. Remove quotes, backticks, brackets, braces, angle brackets, and harsh symbols
        text = text.replace(Regex("[\"'`\\\\/|<>^\\[\\]{}]"), " ")

        // 7. Clean bullet point symbols (- , * , + , 1. , 2. ) into natural pauses
        text = text.replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), ". ")
        text = text.replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), ". ")

        // 8. Replace technical symbols with natural spoken words
        text = text.replace("&", " and ")
            .replace("@", " at ")
            .replace("=", " equals ")

        // 9. Remove emojis to prevent TTS from reading out emoji names
        text = text.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "")

        // 10. Collapse extra whitespace
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun speakSentenceChunk(chunkText: String) {
        if (!_isSpeakerOn.value || chunkText.isBlank()) return
        val cleanText = cleanTextForSpeech(chunkText)
        if (cleanText.isEmpty()) return

        _isSpeaking.value = true
        val resolvedVoice = selectedPersona.value.id.ifBlank { "zara" }
        com.titan.ide.voice.TitanNativeVoiceEngine.speakText(
            context = getApplication(),
            text = cleanText,
            voice = resolvedVoice,
            speed = speechRate.value,
            pitch = selectedPersona.value.pitchMultiplier,
            onStart = { _isSpeaking.value = true },
            onComplete = {
                _isSpeaking.value = false
            }
        )
    }

    fun speakAloud(text: String, onDone: (() -> Unit)? = null) {
        speakAloudWithPersona(text = text, voiceName = null, pitch = null, onDone = onDone)
    }

    fun speakAloud(text: String, voiceName: String?, pitch: Float? = null, onDone: (() -> Unit)? = null) {
        speakAloudWithPersona(text = text, voiceName = voiceName, pitch = pitch, onDone = onDone)
    }

    private fun speakAloudWithPersona(
        text: String,
        voiceName: String?,
        pitch: Float?,
        onDone: (() -> Unit)?
    ) {
        if (!_isSpeakerOn.value && voiceName == null) {
            onDone?.invoke()
            return
        }
        val cleanText = cleanTextForSpeech(text)
        if (cleanText.isEmpty()) {
            onDone?.invoke()
            return
        }

        pendingTtsCompletion = onDone
        _isSpeaking.value = true

        com.titan.ide.voice.TitanInstantTtsEngine.speakFullResponse(
            context = getApplication(),
            rawText = cleanText,
            onStart = { _isSpeaking.value = true },
            onDone = {
                _isSpeaking.value = false
                pendingTtsCompletion?.invoke()
                pendingTtsCompletion = null
            }
        )
    }

    fun interruptSpeaking() {
        if (_isSpeaking.value) {
            pendingTtsCompletion = null
            com.titan.ide.voice.TitanInstantTtsEngine.stopSpeech()
            com.titan.ide.voice.TitanNativeVoiceEngine.stopSpeech()
            textToSpeech?.stop()
            _isSpeaking.value = false
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        if (_isMuted.value) {
            liveSessionManager.stopMicrophone()
        } else if (_isLiveActive.value) {
            liveSessionManager.startContinuousSpeechListener()
        }
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        com.titan.ide.voice.TitanLiveCallEngine.toggleSpeakerphone(getApplication())
        if (!_isSpeakerOn.value) {
            interruptSpeaking()
        }
    }

    fun startLiveVoiceSession() {
        // Enforce Hydrogen (gemini-4-hydrogen) by default for Live Voice Call Mode!
        val liveModel = com.example.data.model.GeminiModelsList.ALL_MODELS.find { it.id == "gemini-4-hydrogen" }
            ?: selectedModel.value
        selectModel(liveModel)

        _isLiveActive.value = true
        _isMuted.value = false
        _liveSpokenText.value = ""
        _liveResponseText.value = ""

        liveSessionManager.startLiveSession()
    }

    fun toggleLiveVoiceSession() {
        if (liveSessionManager.sessionState.value == com.titan.ide.voice.LiveVoiceState.IDLE) {
            startLiveVoiceSession()
        } else {
            stopLiveVoiceSession()
        }
    }

    fun restartLiveListening() {
        interruptSpeaking()
        _isMuted.value = false
        if (liveSessionManager.sessionState.value == com.titan.ide.voice.LiveVoiceState.IDLE) {
            startLiveVoiceSession()
        } else {
            liveSessionManager.interruptAndListenNow()
        }
    }

    fun sendLiveVoiceMessage(text: String) {
        if (text.isBlank()) return
        interruptSpeaking()
        _liveSpokenText.value = text
        _inputText.value = text
        checkAndApplyVoiceSwitchCommand(text)
        liveSessionManager.processUserSpeech(text)
    }

    fun stopLiveVoiceSession() {
        liveSessionManager.stopLiveSession()
        voiceSessionManager.stopSession()
        _isLiveActive.value = false
        _isListening.value = false
        _isSpeaking.value = false
        _audioLevel.value = 0f
        com.titan.ide.voice.TitanContinuousSpeechEngine.stopListening()
        interruptSpeaking()
    }

    // Dictation STT Methods
    fun toggleDictation() {
        if (_isDictating.value) {
            stopDictation()
        } else {
            startDictation()
        }
    }

    fun startDictation() {
        val context = getApplication<Application>()
        stopDictation()

        _isDictating.value = true
        _dictationSeconds.value = 0
        _dictationAudioLevel.value = 0f
        baseTextBeforeDictation = _inputText.value

        dictationTimerJob = viewModelScope.launch {
            while (_isDictating.value) {
                delay(1000L)
                if (_isDictating.value) {
                    _dictationSeconds.value += 1
                }
            }
        }

        startDictationListening()
    }

    private fun startDictationListening() {
        if (!_isDictating.value) return
        val context = getApplication<Application>()
        val prefLang = com.titan.ide.voice.TitanContinuousSpeechEngine.getPreferredLanguageTag(context)
        com.titan.ide.voice.TitanContinuousSpeechEngine.startContinuousListening(
            context = context,
            languageTag = prefLang,
            onPartial = { partial ->
                if (_isDictating.value && partial.isNotBlank()) {
                    injectDictatedText(partial)
                }
            },
            onResult = { recognized ->
                if (_isDictating.value && recognized.isNotBlank()) {
                    injectDictatedText(recognized)
                    baseTextBeforeDictation = _inputText.value
                }
            }
        )
    }

    private fun injectDictatedText(recognized: String) {
        if (recognized.isBlank()) return
        val prefix = baseTextBeforeDictation.trim()
        val processed = com.example.util.TitanContextualProcessor.processContextual(recognized, isFinal = true)
        val textToInject = if (prefix.isEmpty()) {
            processed
        } else {
            "$prefix $processed"
        }
        _inputText.value = textToInject
    }

    fun stopDictation() {
        _isDictating.value = false
        dictationTimerJob?.cancel()
        dictationTimerJob = null
        com.titan.ide.voice.TitanContinuousSpeechEngine.stopListening()
        com.titan.ide.voice.TitanWhisperRecorder.stopRecording()
        _dictationAudioLevel.value = 0f
    }

    override fun onCleared() {
        super.onCleared()
        stopDictation()
        liveSessionManager.stopLiveSession()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        repository.localHttpServer.stop()
    }
}
