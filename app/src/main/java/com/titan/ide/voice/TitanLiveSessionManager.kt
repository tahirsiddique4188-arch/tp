package com.titan.ide.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.SenderRole
import com.titan.ide.network.TitanDirectEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class LiveVoiceState {
    IDLE,           // Session is OFF
    LISTENING,      // Mic is ON and continuously listening (Full-Duplex Multi-Turn)
    AI_THINKING,    // Streaming AI generation in progress
    AI_SPEAKING     // AI is speaking audio out (User can Barge-In to interrupt anytime)
}

/**
 * TitanLiveSessionManager — Master Real-Time Live Voice Session Coordinator
 *
 * Implements:
 * 1. Default Engine: Enforces TRUE Hydrogen (gemini-4-hydrogen) — ZERO 2.0-flash hijacking!
 * 2. Dedicated Phone-Call Loop: Powered by TitanLiveCallEngine (Continuous AudioRecord + 1.5s Ring Buffer + VAD).
 * 3. Instant Full-Read TTS: Powered by TitanInstantTtsEngine (<100ms start, <350-char chunking, QUEUE_ADD).
 * 4. Hands-Free Auto-Resume: Automatically resumes listening when AI finishes speaking until "End Call" is pressed.
 * 5. Instant Barge-In: Talking interrupts AI speech immediately.
 *
 * Developer: Developer Tahir (@Tahirsiddik)
 */
class TitanLiveSessionManager(
    private val context: Context,
    private val onResponseStart: (() -> Unit)? = null,
    private val onTextChunk: (String) -> Unit,
    private val onActionTriggered: (JSONObject) -> Unit,
    private val speakTts: (String, () -> Unit) -> Unit,
    private val onSessionEnded: () -> Unit = {},
    private val onTurnCommitted: ((userText: String, aiReply: String) -> Unit)? = null
) {
    private val _sessionState = MutableStateFlow(LiveVoiceState.IDLE)
    val sessionState: StateFlow<LiveVoiceState> = _sessionState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionScope: CoroutineScope? = null
    private var activeGenerationJob: Job? = null
    private val conversationHistory = mutableListOf<ChatMessage>()

    companion object {
        private const val TAG = "TitanLiveSession"

        @Volatile
        var activeManager: TitanLiveSessionManager? = null
            private set

        fun isSessionActive(): Boolean {
            val state = activeManager?.sessionState?.value
            return state != null && state != LiveVoiceState.IDLE
        }
    }

    fun toggleLiveSession() {
        if (_sessionState.value == LiveVoiceState.IDLE) {
            startLiveSession()
        } else {
            stopLiveSession()
        }
    }

    fun startLiveSession() {
        if (_sessionState.value != LiveVoiceState.IDLE) return
        _sessionState.value = LiveVoiceState.LISTENING
        activeManager = this
        sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        startTelemetryCollectors()

        // Pre-warm Hydrogen connection
        sessionScope?.launch {
            try {
                TitanDirectEngine.speculativePreWarm(context, "gemini-4-hydrogen")
            } catch (_: Exception) {}
        }

        // Launch Live Call Engine
        TitanLiveCallEngine.startLiveCall(context) { userSpeech ->
            processUserSpeech(userSpeech)
        }

        Log.i(TAG, "Titan Live Voice Session Initialized with TRUE Hydrogen (gemini-4-hydrogen)")
    }

    fun interruptAndListenNow() {
        Log.i(TAG, "User Barge-In -> Interrupting AI speech and listening immediately")
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        TitanInstantTtsEngine.stopSpeech()
        TitanLiveCallEngine.stopSpeakingAndListen()
        _sessionState.value = LiveVoiceState.LISTENING
    }

    private fun startTelemetryCollectors() {
        sessionScope?.launch {
            launch {
                TitanLiveCallEngine.callState.collect { cState ->
                    _sessionState.value = when (cState) {
                        TitanLiveCallEngine.LiveCallState.IDLE -> LiveVoiceState.IDLE
                        TitanLiveCallEngine.LiveCallState.LISTENING -> LiveVoiceState.LISTENING
                        TitanLiveCallEngine.LiveCallState.THINKING -> LiveVoiceState.AI_THINKING
                        TitanLiveCallEngine.LiveCallState.SPEAKING -> LiveVoiceState.AI_SPEAKING
                    }
                }
            }
            launch {
                TitanLiveCallEngine.audioLevel.collect { level ->
                    if (_sessionState.value != LiveVoiceState.IDLE) {
                        _audioLevel.value = level
                    }
                }
            }
            launch {
                TitanLiveCallEngine.liveSpokenText.collect { spoken ->
                    if (spoken.isNotEmpty()) {
                        _liveSpokenText.value = spoken
                    }
                }
            }
        }
    }

    fun stopLiveSession() {
        _sessionState.value = LiveVoiceState.IDLE
        if (activeManager == this) {
            activeManager = null
        }
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        sessionScope?.cancel()
        sessionScope = null

        TitanLiveCallEngine.endLiveCall()
        TitanInstantTtsEngine.stopSpeech()
        onSessionEnded()
        com.example.voice.TitanWakeWordService.instance?.resumeListeningIfIdle()
        Log.i(TAG, "Live Voice Session Closed")
    }

    fun stopMicrophone() {
        TitanLiveCallEngine.toggleMute()
    }

    fun startContinuousSpeechListener() {
        if (_sessionState.value == LiveVoiceState.IDLE) {
            startLiveSession()
        } else {
            TitanLiveCallEngine.stopSpeakingAndListen()
        }
    }

    /**
     * Dispatches transcribed user speech to TRUE Hydrogen (gemini-4-hydrogen).
     * ZERO 2.0-flash hijacking!
     */
    fun processUserSpeech(userSpeech: String) {
        val cleanInput = userSpeech.trim()
        if (_sessionState.value == LiveVoiceState.IDLE || cleanInput.isBlank()) return

        val lower = cleanInput.lowercase()
        val isExit = lower == "stop" || lower == "bye" || lower == "goodbye" || lower == "exit" ||
                lower == "থামো" || lower == "বিদায়" || lower == "বন্ধ করো" || lower == "থাক" ||
                lower == "ruko" || lower == "band karo"
        if (isExit) {
            stopLiveSession()
            return
        }

        // Mid-Call Voice Persona Switching
        if (lower.contains("voice") || lower.contains("activate") || lower.contains("ভয়েস") || lower.contains("অ্যাক্টিভেট") || lower.contains("आवाज")) {
            val matchedPersona = com.example.data.model.VoicePersonas.ALL_PERSONAS.firstOrNull { p ->
                lower.contains(p.name.lowercase()) || lower.contains(p.id.lowercase())
            }
            if (matchedPersona != null) {
                val prefs = context.getSharedPreferences("titan_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("selected_persona_id", matchedPersona.id)
                    .putString("selected_voice_persona", matchedPersona.id)
                    .apply()
                val confirmMsg = if (cleanInput.any { it in '\u0980'..'\u09FF' }) {
                    "ঠিক আছে, এখন থেকে আমি ${matchedPersona.name}-এর ভয়েসে কথা বলব।"
                } else if (cleanInput.any { it in '\u0900'..'\u097F' }) {
                    "नमस्ते! अब से मैं ${matchedPersona.name} की आवाज में बात करूंगा। बताइए क्या मदद करूं?"
                } else {
                    "Switched to ${matchedPersona.name}'s voice. How does this sound?"
                }
                mainHandler.post {
                    onTextChunk(confirmMsg)
                    TitanLiveCallEngine.speakAndAutoResume(context, confirmMsg)
                }
                return
            }
        }

        // Auto-learn user memory facts & name
        val (learned, extractedName) = com.titan.ide.brain.TitanBrainMemoryEngine.autoLearnFromUserMessage(context, cleanInput)

        _sessionState.value = LiveVoiceState.AI_THINKING
        _liveSpokenText.value = cleanInput

        if (sessionScope == null) {
            sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = sessionScope?.launch {
            try {
                withContext(Dispatchers.Main) {
                    onResponseStart?.invoke()
                    onTextChunk("__CLEAR__")
                }

                // Check for immediate memory / name confirmation
                val isExplicitMemorySave = lower.contains("memory") ||
                        lower.contains("save kar") ||
                        lower.contains("yaad rakh") ||
                        lower.contains("mone rakh") ||
                        lower.contains("mona rakh") ||
                        lower.contains("save to memory") ||
                        lower.contains("save in memory")

                val isNameIntro = lower.contains("mara name") ||
                        lower.contains("mera name") ||
                        lower.contains("mera naam") ||
                        lower.contains("mara naam") ||
                        lower.contains("my name is") ||
                        lower.contains("amar nam")

                if ((isExplicitMemorySave || isNameIntro) && (extractedName != null || learned)) {
                    val memoryReply = com.titan.ide.brain.TitanBrainMemoryEngine.buildMemoryConfirmation(cleanInput, extractedName)
                    conversationHistory.add(ChatMessage(sender = SenderRole.USER, content = cleanInput))
                    conversationHistory.add(ChatMessage(sender = SenderRole.GEMINI, content = memoryReply))

                    withContext(Dispatchers.Main) {
                        onTextChunk(memoryReply)
                        TitanLiveCallEngine.speakAndAutoResume(context, memoryReply)
                    }
                    return@launch
                }

                val memoryContext = com.titan.ide.brain.TitanBrainMemoryEngine.getMemoryContextBlock(context)
                val subAgentCtx = com.titan.ide.agent.TitanDynamicAgentLoop.getBackgroundStatusForLiveChat()
                val liveInstruction = buildString {
                    if (memoryContext.isNotBlank()) {
                        append(memoryContext).append("\n")
                    }
                    if (subAgentCtx.isNotBlank()) {
                        append(subAgentCtx).append("\n")
                    }
                    val botName = com.example.voice.TitanAssistantConfig.getAssistantName(context)
                    val selectedLang = com.example.voice.TitanAssistantConfig.getSelectedLanguage(context)
                    val langRule = if (selectedLang.promptDirective.isNotBlank()) {
                        selectedLang.promptDirective
                    } else {
                        "Reply in the exact same language and script the user just spoke (any language worldwide)."
                    }
                    append("SYSTEM: Your name is $botName. You are on a live phone call as a natural human friend AND autonomous Android assistant. Reply warmly and concisely in short spoken sentences (NO markdown, asterisks, or lists). $langRule If the user asks you to DO ANY task on their phone (open apps, send messages, call, play video, scroll, search, change settings), reply briefly AND append `[TITAN_GOAL: <full task>]` at the very end. If the user asks you to BUILD ANY game, website, app, or software, reply briefly that you are building it in the background while keeping the conversation going AND append `[TITAN_BUILD: <complete project specification>]` at the very end.\n")
                    append("User said: ").append(cleanInput)
                }

                val promptHistory = conversationHistory.takeLast(6).toMutableList()
                promptHistory.add(ChatMessage(sender = SenderRole.USER, content = liveInstruction))

                val accumulatedResponse = StringBuilder()
                val sentenceBuffer = StringBuilder()
                var isFirstSentence = true
                var hasStartedSpeaking = false

                // HITS TRUE HYDROGEN ENDPOINT (ZERO 2.0-FLASH OVERRIDE!)
                val completeResponse = TitanDirectEngine.executeChat(
                    context = context,
                    modelId = "gemini-3.5-flash-lite",
                    messages = promptHistory.toList()
                ) { chunk ->
                    accumulatedResponse.append(chunk)
                    sentenceBuffer.append(chunk)
                    mainHandler.post { onTextChunk(accumulatedResponse.toString()) }

                    val curText = sentenceBuffer.toString()
                    val delimIdx = curText.indexOfAny(charArrayOf('।', '.', '?', '!', '\n'))
                    if (delimIdx >= 0 && delimIdx + 1 <= curText.length) {
                        val rawSentence = curText.substring(0, delimIdx + 1).trim()
                        val sentence = rawSentence
                            .replace(Regex("""\[TITAN_(?:GOAL|ACTION|BUILD):.*?\]""", RegexOption.DOT_MATCHES_ALL), "")
                            .trim()
                        val remainder = curText.substring(delimIdx + 1)
                        if (sentence.length >= 3 && !sentence.startsWith("[TITAN_")) {
                            sentenceBuffer.clear()
                            sentenceBuffer.append(remainder)
                            val first = isFirstSentence
                            isFirstSentence = false
                            hasStartedSpeaking = true
                            mainHandler.post {
                                TitanInstantTtsEngine.queueStreamingSentence(
                                    context = context,
                                    sentence = sentence,
                                    isFirst = first,
                                    isLast = false,
                                    onStart = {
                                        TitanLiveCallEngine.setCallState(TitanLiveCallEngine.LiveCallState.SPEAKING)
                                        _sessionState.value = LiveVoiceState.AI_SPEAKING
                                    }
                                )
                            }
                        }
                    }
                }.trim()

                conversationHistory.add(ChatMessage(sender = SenderRole.USER, content = cleanInput))
                conversationHistory.add(ChatMessage(sender = SenderRole.GEMINI, content = completeResponse))
                mainHandler.post {
                    val cleanChatReply = completeResponse
                        .replace(Regex("""\[TITAN_(?:GOAL|ACTION|BUILD):.*?\]""", RegexOption.DOT_MATCHES_ALL), "")
                        .trim()
                    if (cleanChatReply.isNotBlank()) {
                        onTurnCommitted?.invoke(cleanInput, cleanChatReply)
                    }
                }

                withContext(Dispatchers.Main) {
                    onTextChunk(completeResponse)
                }

                // NON-BLOCKING Background Autonomous Software/App/Game Builder (Live Voice keeps running freely!)
                val buildMatch = Regex("""\[TITAN_BUILD:\s*(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(completeResponse)
                val autonomousBuildSpec = buildMatch?.groupValues?.getOrNull(1)?.trim()
                if (!autonomousBuildSpec.isNullOrBlank()) {
                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        try {
                            val result = com.titan.ide.builder.TitanAutonomousBuilder.buildProject(
                                userPrompt = autonomousBuildSpec,
                                context = context
                            )
                            val doneMsg = "Project ${result.projectName.ifBlank { "App" }} built with ${result.files.size} files and ready for live preview."
                            mainHandler.post {
                                onTurnCommitted?.invoke("[Build] $autonomousBuildSpec", "✅ **$doneMsg**\nOpen Live Preview or Files to run it.")
                                if (isSessionActive() && _sessionState.value == LiveVoiceState.LISTENING) {
                                    onTextChunk(doneMsg)
                                    TitanLiveCallEngine.speakAndAutoResume(context, doneMsg)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Background Autonomous Builder error", e)
                        }
                    }
                }

                // NON-BLOCKING Background Sub-Agent Handoff with Visual Eyes (User keeps talking freely!)
                val goalMatch = Regex("""\[TITAN_GOAL:\s*(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(completeResponse)
                val autonomousGoal = goalMatch?.groupValues?.getOrNull(1)?.trim()
                if (!autonomousGoal.isNullOrBlank()) {
                    com.titan.ide.agent.TitanDynamicAgentLoop.dispatchBackgroundSubAgent(
                        context = context,
                        userGoal = autonomousGoal,
                        onCompleted = { resultSummary ->
                            mainHandler.post {
                                if (isSessionActive() && _sessionState.value == LiveVoiceState.LISTENING) {
                                    onTextChunk("✅ $resultSummary")
                                    TitanLiveCallEngine.speakAndAutoResume(context, resultSummary)
                                }
                            }
                        }
                    )
                }

                // Parse Actions if generated
                if (completeResponse.contains("[TITAN_ACTION:")) {
                    val regex = Regex("""\[TITAN_ACTION:\s*(\{.*?\})\]""")
                    val match = regex.find(completeResponse)
                    if (match != null) {
                        val actionJson = JSONObject(match.groupValues[1])
                        withContext(Dispatchers.Main) { onActionTriggered(actionJson) }
                    }
                }

                // Finish remaining sentence buffer properly without premature blank-chunk onDone bug
                if (hasStartedSpeaking) {
                    val remaining = sentenceBuffer.toString().replace(Regex("""\[TITAN_(?:GOAL|ACTION|BUILD):.*?\]""", RegexOption.DOT_MATCHES_ALL), "").trim()
                    mainHandler.post {
                        val resumeListening = {
                            if (isSessionActive()) {
                                TitanLiveCallEngine.setCallState(TitanLiveCallEngine.LiveCallState.LISTENING)
                                _sessionState.value = LiveVoiceState.LISTENING
                            }
                        }
                        if (remaining.isNotBlank()) {
                            TitanInstantTtsEngine.queueStreamingSentence(
                                context = context,
                                sentence = remaining,
                                isFirst = false,
                                isLast = true,
                                onStart = {
                                    TitanLiveCallEngine.setCallState(TitanLiveCallEngine.LiveCallState.SPEAKING)
                                    _sessionState.value = LiveVoiceState.AI_SPEAKING
                                },
                                onDone = resumeListening
                            )
                        } else {
                            TitanInstantTtsEngine.markStreamFinished(resumeListening)
                        }
                    }
                } else if (completeResponse.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        val cleanSpoken = completeResponse
                            .replace(Regex("""\[TITAN_(?:GOAL|ACTION|BUILD):.*?\]""", RegexOption.DOT_MATCHES_ALL), "")
                            .trim()
                        if (cleanSpoken.isNotBlank()) {
                            TitanLiveCallEngine.speakAndAutoResume(context, cleanSpoken)
                        } else if (isSessionActive()) {
                            TitanLiveCallEngine.setCallState(TitanLiveCallEngine.LiveCallState.LISTENING)
                            _sessionState.value = LiveVoiceState.LISTENING
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (isSessionActive()) {
                            val retryMsg = if (cleanInput.any { it in '\u0980'..'\u09FF' }) {
                                "নেটওয়ার্কে একটু সমস্যা হয়েছে, আবার বলো।"
                            } else {
                                "Network hiccup, please say that again."
                            }
                            onTextChunk(retryMsg)
                            TitanLiveCallEngine.speakAndAutoResume(context, retryMsg)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Live voice response error", e)
                withContext(Dispatchers.Main) {
                    onTextChunk("⚠️ Re-establishing live audio connection...")
                    TitanLiveCallEngine.speakAndAutoResume(context, "Connection re-establishing...")
                }
            }
        }
    }
}
