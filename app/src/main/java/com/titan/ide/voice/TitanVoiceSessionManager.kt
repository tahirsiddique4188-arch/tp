package com.titan.ide.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceState {
    IDLE,       // Microphone is OFF
    LISTENING,  // Listening to user speech via continuous AudioRecord (silent, 0 beeps)
    THINKING,   // AI processing text response
    SPEAKING    // AI speaking via neural audio (Echo Guard active)
}

/**
 * TitanVoiceSessionManager - Bypass-Style Ultra-Clean Voice Engine
 * Uses continuous hardware AudioRecord for zero-lag speech-to-text with zero system beeps,
 * rock-solid Echo Guard audio playback, and continuous conversation flow.
 *
 * Developer: Developer Tahir (@Tahirsiddik)
 */
class TitanVoiceSessionManager private constructor() {

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _lastSpokenText = MutableStateFlow("")
    val lastSpokenText: StateFlow<String> = _lastSpokenText.asStateFlow()

    private var lastResultCallback: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "TitanVoiceSession"

        @Volatile
        private var instance: TitanVoiceSessionManager? = null

        fun getInstance(): TitanVoiceSessionManager {
            return instance ?: synchronized(this) {
                instance ?: TitanVoiceSessionManager().also { instance = it }
            }
        }
    }

    fun toggleSession(context: Context, onResult: (String) -> Unit) {
        if (_isSessionActive.value) {
            stopSession()
        } else {
            startListening(context, onResult)
        }
    }

    fun startListening(context: Context, onResult: (String) -> Unit) {
        lastResultCallback = onResult
        _isSessionActive.value = true
        _state.value = VoiceState.LISTENING

        TitanContinuousSpeechEngine.startContinuousListening(
            context = context,
            onPartial = { partial ->
                if (partial.isNotBlank()) _lastSpokenText.value = partial
            },
            onResult = { text ->
                if (text.isNotBlank()) {
                    _lastSpokenText.value = text
                    onResult(text)
                }
            }
        )
    }

    fun playAiSpeech(context: Context, audioUrlOrBase64: String, onFinished: () -> Unit) {
        // Echo Guard: Pause continuous speech engine so AI does not hear itself
        TitanContinuousSpeechEngine.setPaused(true)
        _state.value = VoiceState.SPEAKING
        _audioLevel.value = 0.5f

        TitanInstantTtsEngine.speakFullResponse(
            context = context,
            rawText = audioUrlOrBase64,
            onStart = {
                TitanContinuousSpeechEngine.setPaused(true)
                _state.value = VoiceState.SPEAKING
            },
            onDone = {
                _audioLevel.value = 0f
                _state.value = VoiceState.LISTENING
                TitanContinuousSpeechEngine.setPaused(false)
                onFinished()
            }
        )
    }

    fun stopSession() {
        _isSessionActive.value = false
        _state.value = VoiceState.IDLE
        _audioLevel.value = 0f

        TitanContinuousSpeechEngine.stopListening()
        TitanInstantTtsEngine.stopSpeech()
        TitanNativeVoiceEngine.stopSpeech()
        Log.d(TAG, "Voice session stopped by user")
    }
}
