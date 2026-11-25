package com.titan.ide.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.titan.ide.network.TitanDirectEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * TitanLiveCallEngine — Zero-Beep Continuous VAD-Gated Live Phone-Call Engine
 *
 * Eliminates Samsung/Google SpeechRecognizer's 5-second timeout & repeated "ting-ting" on/off loop:
 * 1. Continuous Silent Standby: Hardware AudioRecord (16kHz Mono) monitors mic continuously in
 *    silence with ZERO SpeechRecognizer restarts, ZERO mic on/off flashing, and ZERO beeps!
 * 2. Instant Speech Handoff: The moment human speech crosses the adaptive noise floor (~60ms),
 *    AudioRecord pauses and hands off to SpeechRecognizer for that single utterance (with brief
 *    320ms chime mute), streaming live captions and returning immediately to silent AudioRecord VAD!
 */
object TitanLiveCallEngine {

    private const val TAG = "TitanLiveCallEngine"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val FRAME_MS = 20L
    private const val BYTES_PER_FRAME = (SAMPLE_RATE * FRAME_MS / 1000L).toInt() * 2

    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class LiveCallState {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING
    }

    enum class LanguageMode(val label: String, val languageTag: String) {
        AUTO("Auto (Global)", "bn-IN"),
        BENGALI("Bengali", "bn-IN"),
        ENGLISH("English", "en-IN")
    }

    private var appContext: Context? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var preferOnDeviceRecognizer = true

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var vadJob: Job? = null

    private val _callState = MutableStateFlow(LiveCallState.IDLE)
    val callState: StateFlow<LiveCallState> = _callState.asStateFlow()

    private val _activeLanguage = MutableStateFlow(LanguageMode.AUTO)
    val activeLanguage: StateFlow<LanguageMode> = _activeLanguage.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val _liveResponseText = MutableStateFlow("")
    val liveResponseText: StateFlow<String> = _liveResponseText.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private var onFinalSpeechCallback: ((String) -> Unit)? = null

    @Volatile private var isCallActive = false
    @Volatile private var isRecognizerActive = false
    @Volatile private var isChimeMuted = false
    @Volatile private var savedMusicVolume = -1
    @Volatile private var echoGuardUntilMs = 0L
    @Volatile private var lastPartialText = ""

    fun ensureStreamsUnmuted(context: Context) {
        if (!isChimeMuted) return
        isChimeMuted = false
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
            }
            if (savedMusicVolume > 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
            }
            savedMusicVolume = -1
        } catch (_: Exception) {}
    }

    private fun muteDuringRecognizerTurn(context: Context) {
        if (TitanInstantTtsEngine.isSpeaking || isChimeMuted) return
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curVol > 0) savedMusicVolume = curVol
            isChimeMuted = true
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
            }
        } catch (_: Exception) {}
    }

    fun setCallState(newState: LiveCallState) {
        if (!isCallActive && newState != LiveCallState.IDLE) return
        val prev = _callState.value
        _callState.value = newState
        val now = System.currentTimeMillis()
        if (newState == LiveCallState.SPEAKING || newState == LiveCallState.THINKING) {
            stopRecognizerInternal()
            stopSilentVad()
        } else if (newState == LiveCallState.LISTENING) {
            if (prev == LiveCallState.SPEAKING) {
                echoGuardUntilMs = now + 380L
            }
            if (!_isMuted.value && !isRecognizerActive) {
                appContext?.let { startSilentVadLoop(it) }
            }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        TitanInstantTtsEngine.preWarm(context)
    }

    fun setLanguageMode(mode: LanguageMode) {
        _activeLanguage.value = mode
    }

    private var audioFocusRequest: Any? = null
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { }

    private fun requestCallAudioFocus(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (_: Exception) {}
    }

    private fun abandonCallAudioFocus(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (audioFocusRequest as? AudioFocusRequest)?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusChangeListener)
            }
            audioFocusRequest = null
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun startLiveCall(context: Context, onUtteranceReady: (String) -> Unit) {
        val appCtx = context.applicationContext
        appContext = appCtx
        onFinalSpeechCallback = onUtteranceReady
        isCallActive = true
        isRecognizerActive = false
        preferOnDeviceRecognizer = true
        lastPartialText = ""
        _isMuted.value = false
        _liveSpokenText.value = ""
        _liveResponseText.value = ""
        _callState.value = LiveCallState.LISTENING

        TitanAudioSessionManager.requestMicOwnership("TitanLiveCallEngine")
        TitanCallForegroundService.startCall(appCtx)
        requestCallAudioFocus(appCtx)
        setSpeakerphone(appCtx, true)
        TitanInstantTtsEngine.preWarm(appCtx)

        engineScope.launch {
            try { TitanDirectEngine.speculativePreWarm(appCtx, "gemini-3.5-flash-lite") } catch (_: Exception) {}
        }

        // Start 100% SILENT continuous hardware VAD (0 beeps, 0 on/off cycling!)
        startSilentVadLoop(appCtx)
    }

    @SuppressLint("MissingPermission")
    private fun startSilentVadLoop(context: Context) {
        if (!isCallActive || _isMuted.value || _callState.value != LiveCallState.LISTENING || isRecognizerActive) return
        if (vadJob?.isActive == true) return

        vadJob = engineScope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT).coerceAtLeast(6400)
            val recorder = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBuf * 2)
                    .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                    ?: AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBuf * 2)
            } catch (e: Exception) {
                Log.e(TAG, "VAD AudioRecord init error: ${e.message}")
                null
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback directly to recognizer if AudioRecord unavailable
                mainHandler.post { launchSingleTurnRecognizer(context) }
                return@launch
            }

            audioRecord = recorder
            try {
                val sid = recorder.audioSessionId
                if (AcousticEchoCanceler.isAvailable()) echoCanceler = AcousticEchoCanceler.create(sid)?.apply { enabled = true }
                if (NoiseSuppressor.isAvailable()) noiseSuppressor = NoiseSuppressor.create(sid)?.apply { enabled = true }
                recorder.startRecording()
                Log.i(TAG, "Silent Continuous VAD active (0 beeps, 0 restarts)")
            } catch (e: Exception) {
                releaseAudioRecord()
                return@launch
            }

            val frame = ByteArray(BYTES_PER_FRAME)
            var noiseFloor = 140.0
            var speechFrames = 0

            while (isCallActive && isActive && !_isMuted.value && _callState.value == LiveCallState.LISTENING && !isRecognizerActive) {
                val read = recorder.read(frame, 0, frame.size)
                if (read <= 0) continue

                var sum = 0.0
                val count = read / 2
                val bb = ByteBuffer.wrap(frame, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until count) {
                    val s = bb.short.toDouble()
                    sum += s * s
                }
                val rms = if (count > 0) sqrt(sum / count) else 0.0
                _audioLevel.value = (rms / 2600.0).toFloat().coerceIn(0.04f, 1f)

                if (System.currentTimeMillis() < echoGuardUntilMs) {
                    speechFrames = 0
                    continue
                }

                val threshold = maxOf(noiseFloor * 2.6, 420.0)
                if (rms >= threshold) {
                    speechFrames++
                    if (speechFrames >= 4) {
                        // Real human voice detected -> cleanly release AudioRecord & hand off to STT!
                        Log.i(TAG, "Voice detected (RMS=${rms.toInt()}) -> Handing off to fast STT")
                        releaseAudioRecord()
                        mainHandler.post { launchSingleTurnRecognizer(context) }
                        return@launch
                    }
                } else {
                    speechFrames = 0
                    noiseFloor = (noiseFloor * 0.94 + rms * 0.06).coerceIn(50.0, 550.0)
                }
            }
            releaseAudioRecord()
        }
    }

    private fun stopSilentVad() {
        vadJob?.cancel()
        vadJob = null
        releaseAudioRecord()
    }

    private fun releaseAudioRecord() {
        try { echoCanceler?.release() } catch (_: Exception) {}
        echoCanceler = null
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        noiseSuppressor = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun launchSingleTurnRecognizer(ctx: Context) {
        if (!isCallActive || _isMuted.value || _callState.value != LiveCallState.LISTENING) return
        if (isRecognizerActive) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            startSilentVadLoop(ctx)
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = if (preferOnDeviceRecognizer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                } else {
                    preferOnDeviceRecognizer = false
                    SpeechRecognizer.createSpeechRecognizer(ctx)
                }
                speechRecognizer?.setRecognitionListener(recognitionListener)
            }

            val savedLang = com.example.voice.TitanAssistantConfig.getSelectedLanguage(ctx)
            val langTag = if (savedLang.code != com.example.voice.TitanAssistantConfig.LANG_AUTO) {
                savedLang.bcp47Tag
            } else {
                com.example.voice.TitanAssistantConfig.getEffectiveSttLanguageTag(ctx)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            }

            muteDuringRecognizerTurn(ctx)
            lastPartialText = ""
            isRecognizerActive = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Recognizer launch warning: ${e.message}")
            isRecognizerActive = false
            destroyRecognizerInternal()
            preferOnDeviceRecognizer = false
            startSilentVadLoop(ctx)
        }
    }

    private fun stopRecognizerInternal() {
        mainHandler.post {
            isRecognizerActive = false
            try { speechRecognizer?.cancel() } catch (_: Exception) {}
        }
    }

    private fun destroyRecognizerInternal() {
        mainHandler.post {
            isRecognizerActive = false
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() { lastPartialText = "" }
        override fun onRmsChanged(rmsdB: Float) {
            if (isCallActive && _callState.value == LiveCallState.LISTENING && !_isMuted.value) {
                _audioLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
            }
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { _audioLevel.value = 0.05f }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isCallActive || _callState.value != LiveCallState.LISTENING) return
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            if (partial.isNotBlank()) {
                lastPartialText = partial
                _liveSpokenText.value = partial
            }
        }

        override fun onResults(results: Bundle?) {
            isRecognizerActive = false
            mainHandler.postDelayed({ appContext?.let { ensureStreamsUnmuted(it) } }, 260L)
            if (!isCallActive) return
            val finalSpeech = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                .ifBlank { lastPartialText.trim() }
            lastPartialText = ""

            if (finalSpeech.isNotBlank()) {
                _liveSpokenText.value = finalSpeech
                _callState.value = LiveCallState.THINKING
                Log.i(TAG, "Live Voice Recognized: '$finalSpeech' -> Dispatching to AI")
                onFinalSpeechCallback?.invoke(finalSpeech)
            } else if (!_isMuted.value && _callState.value == LiveCallState.LISTENING) {
                // Return to 100% SILENT VAD (NEVER loop SpeechRecognizer in silence!)
                appContext?.let { startSilentVadLoop(it) }
            }
        }

        override fun onError(error: Int) {
            isRecognizerActive = false
            mainHandler.postDelayed({ appContext?.let { ensureStreamsUnmuted(it) } }, 260L)
            if (!isCallActive) return

            val rescued = lastPartialText.trim()
            lastPartialText = ""
            if (rescued.length >= 2 && (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)) {
                _liveSpokenText.value = rescued
                _callState.value = LiveCallState.THINKING
                onFinalSpeechCallback?.invoke(rescued)
                return
            }

            if (preferOnDeviceRecognizer && (error == 12 || error == 13 || error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_AUDIO)) {
                preferOnDeviceRecognizer = false
                destroyRecognizerInternal()
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                destroyRecognizerInternal()
            }

            // Return silently to hardware VAD — ZERO repeated timeout beeps!
            if (!_isMuted.value && _callState.value == LiveCallState.LISTENING) {
                mainHandler.postDelayed({
                    appContext?.let { startSilentVadLoop(it) }
                }, 250L)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    suspend fun transcribeAudioWav(context: Context, wavBytes: ByteArray): String = withContext(Dispatchers.IO) {
        try {
            val psidText = TitanDirectEngine.uploadWavAndTranscribeViaPsid(context, wavBytes)
            if (psidText.isNotBlank() && !psidText.contains("I cannot", true) && !psidText.contains("unable to", true)) {
                return@withContext psidText
            }
        } catch (_: Exception) {}
        ""
    }

    fun speakAndAutoResume(context: Context, responseText: String) {
        if (!isCallActive) return
        ensureStreamsUnmuted(context)
        _liveResponseText.value = responseText
        setCallState(LiveCallState.SPEAKING)

        TitanInstantTtsEngine.speakFullResponse(
            context = context,
            rawText = responseText,
            onStart = { setCallState(LiveCallState.SPEAKING) },
            onDone = {
                if (isCallActive && !_isMuted.value) {
                    setCallState(LiveCallState.LISTENING)
                }
            }
        )
    }

    fun stopSpeakingAndListen() {
        TitanInstantTtsEngine.stopSpeech()
        if (isCallActive && !_isMuted.value) {
            echoGuardUntilMs = 0L
            _callState.value = LiveCallState.LISTENING
            appContext?.let { launchSingleTurnRecognizer(it) }
        }
    }

    fun toggleMute() {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        if (newMute) {
            _audioLevel.value = 0f
            stopRecognizerInternal()
            stopSilentVad()
        } else if (isCallActive && _callState.value == LiveCallState.LISTENING) {
            appContext?.let { startSilentVadLoop(it) }
        }
    }

    fun toggleSpeakerphone(context: Context) {
        setSpeakerphone(context, !_isSpeakerOn.value)
    }

    private fun setSpeakerphone(context: Context, enabled: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (enabled) {
                am.mode = AudioManager.MODE_NORMAL
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try { am.clearCommunicationDevice() } catch (_: Exception) {}
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = true
                }
            } else {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                        ?.let { am.setCommunicationDevice(it) }
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = false
                }
            }
            _isSpeakerOn.value = enabled
        } catch (_: Exception) {}
    }

    fun endLiveCall() {
        if (!isCallActive) return
        isCallActive = false
        _callState.value = LiveCallState.IDLE
        _audioLevel.value = 0f
        TitanInstantTtsEngine.stopSpeech()
        stopRecognizerInternal()
        destroyRecognizerInternal()
        stopSilentVad()

        appContext?.let { ctx ->
            ensureStreamsUnmuted(ctx)
            abandonCallAudioFocus(ctx)
            try {
                (ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.mode = AudioManager.MODE_NORMAL
            } catch (_: Exception) {}
            TitanCallForegroundService.endCall(ctx)
        }
        TitanAudioSessionManager.releaseMicOwnership("TitanLiveCallEngine")
    }

    fun buildWavBytes(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * 2
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte(); header[5] = (totalDataLen shr 8 and 0xffL).toByte()
        header[6] = (totalDataLen shr 16 and 0xffL).toByte(); header[7] = (totalDataLen shr 24 and 0xffL).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[20] = 1; header[22] = 1
        header[24] = (sampleRate and 0xff).toByte(); header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte(); header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte(); header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = 2; header[34] = 16
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte(); header[41] = (totalAudioLen shr 8 and 0xffL).toByte()
        header[42] = (totalAudioLen shr 16 and 0xffL).toByte(); header[43] = (totalAudioLen shr 24 and 0xffL).toByte()
        return header + pcmData
    }
}
