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
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

/**
 * CircularAudioRingBuffer — 1.5-Second In-Memory Audio Ring Buffer
 * Used when falling back to hardware AudioRecord mode.
 */
class CircularAudioRingBuffer(val capacity: Int) {
    private val buffer = ByteArray(capacity)
    private var writePos = 0
    private var isFull = false
    private val lock = Any()

    fun write(data: ByteArray, offset: Int, length: Int) {
        synchronized(lock) {
            for (i in 0 until length) {
                buffer[writePos] = data[offset + i]
                writePos = (writePos + 1) % capacity
                if (writePos == 0) isFull = true
            }
        }
    }

    fun getSnapshot(): ByteArray {
        synchronized(lock) {
            val size = if (isFull) capacity else writePos
            val out = ByteArray(size)
            if (!isFull) {
                System.arraycopy(buffer, 0, out, 0, writePos)
            } else {
                val tail = capacity - writePos
                System.arraycopy(buffer, writePos, out, 0, tail)
                System.arraycopy(buffer, 0, out, tail, writePos)
            }
            return out
        }
    }

    fun clear() {
        synchronized(lock) {
            writePos = 0
            isFull = false
        }
    }
}

/**
 * TitanLiveCallEngine — Ultra-Fast Reliable Live Phone-Call Voice Engine
 *
 * Architecture:
 * 1. Primary: Instant Real-Time Streaming STT (prefers Android 12+ On-Device SpeechRecognizer
 *    for 0-beep offline speed, seamlessly falling back to System SpeechRecognizer with brief
 *    notification/system chime suppression) — works 100% WITH OR WITHOUT PSID cookies!
 * 2. Live Partial Captions: Streams recognized words to UI in real time while you speak.
 * 3. Strict Echo Guard: Stops recognizer while Titan is thinking/speaking and resumes cleanly
 *    350ms after TTS finishes, so Titan NEVER hears its own voice.
 * 4. Fallback: Hardware AudioRecord + PSID-Native WAV upload if system recognizer is unavailable.
 *
 * Developer: Developer Tahir (@Tahirsiddik)
 */
object TitanLiveCallEngine {

    private const val TAG = "TitanLiveCallEngine"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val FRAME_MS = 20L
    private const val SAMPLES_PER_FRAME = (SAMPLE_RATE * FRAME_MS / 1000L).toInt()
    private const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2
    private const val RING_BUFFER_CAPACITY_BYTES = 48000

    private const val SILENCE_ENDPOINT_MS = 450L
    private const val MIN_SPEECH_DURATION_MS = 240L
    private const val MAX_UTTERANCE_MS = 10_000L

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
    private var restartRunnable: Runnable? = null

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingJob: Job? = null
    private val ringBuffer = CircularAudioRingBuffer(RING_BUFFER_CAPACITY_BYTES)

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

    @Volatile
    private var isCallActive = false

    @Volatile
    private var isRecognizerListening = false

    @Volatile
    private var speechStartedAtMs = 0L

    @Volatile
    private var echoGuardUntilMs = 0L

    @Volatile
    private var lastPartialText = ""

    fun setCallState(newState: LiveCallState) {
        if (!isCallActive && newState != LiveCallState.IDLE) return
        val prev = _callState.value
        _callState.value = newState
        val now = System.currentTimeMillis()
        if (newState == LiveCallState.SPEAKING && prev != LiveCallState.SPEAKING) {
            speechStartedAtMs = now
            stopRecognizerInternal()
        } else if (newState == LiveCallState.THINKING) {
            stopRecognizerInternal()
        } else if (newState == LiveCallState.LISTENING) {
            if (prev == LiveCallState.SPEAKING) {
                echoGuardUntilMs = now + 350L
                scheduleRecognizerRestart(380L)
            } else if (!isRecognizerListening && !_isMuted.value) {
                scheduleRecognizerRestart(150L)
            }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        TitanInstantTtsEngine.preWarm(context)
    }

    fun setLanguageMode(mode: LanguageMode) {
        _activeLanguage.value = mode
        Log.i(TAG, "Language mode switched to: ${mode.label}")
        if (isCallActive && _callState.value == LiveCallState.LISTENING && !_isMuted.value) {
            stopRecognizerInternal()
            scheduleRecognizerRestart(180L)
        }
    }

    private var audioFocusRequest: Any? = null
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "Live call audio focus change: $focusChange")
    }

    private fun requestCallAudioFocus(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request warning: ${e.message}")
        }
    }

    private fun abandonCallAudioFocus(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = audioFocusRequest as? AudioFocusRequest
                if (req != null) {
                    audioManager.abandonAudioFocusRequest(req)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
            audioFocusRequest = null
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus abandon warning: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun startLiveCall(context: Context, onUtteranceReady: (String) -> Unit) {
        val appCtx = context.applicationContext
        appContext = appCtx
        onFinalSpeechCallback = onUtteranceReady
        isCallActive = true
        preferOnDeviceRecognizer = true
        lastPartialText = ""
        _isMuted.value = false
        _liveSpokenText.value = ""
        _liveResponseText.value = ""
        _callState.value = LiveCallState.LISTENING

        // 0. Exclusive hardware mic ownership
        TitanAudioSessionManager.requestMicOwnership("TitanLiveCallEngine")

        // 1. Foreground Call Service
        TitanCallForegroundService.startCall(appCtx)

        // 2. Audio Focus + Loudspeaker
        requestCallAudioFocus(appCtx)
        setSpeakerphone(appCtx, true)

        // 3. Pre-Warm Instant TTS Engine
        TitanInstantTtsEngine.preWarm(appCtx)

        // 4. Speculative Pre-Warm Gemini connection
        engineScope.launch {
            try {
                TitanDirectEngine.speculativePreWarm(appCtx, "gemini-3.5-flash-lite")
            } catch (_: Exception) {}
        }

        // 5. Start Instant Real-Time Speech Recognition (with AudioRecord fallback if unavailable)
        if (SpeechRecognizer.isRecognitionAvailable(appCtx)) {
            scheduleRecognizerRestart(120L)
        } else {
            Log.w(TAG, "System SpeechRecognizer unavailable — falling back to AudioRecord + PSID STT")
            startContinuousAudioRecordStream(appCtx)
        }
    }

    private fun scheduleRecognizerRestart(delayMs: Long) {
        mainHandler.post {
            restartRunnable?.let { mainHandler.removeCallbacks(it) }
            val r = Runnable {
                if (isCallActive && !_isMuted.value && _callState.value == LiveCallState.LISTENING) {
                    startRecognizerOnMainThread()
                }
            }
            restartRunnable = r
            mainHandler.postDelayed(r, delayMs)
        }
    }

    private fun stopRecognizerInternal() {
        mainHandler.post {
            restartRunnable?.let { mainHandler.removeCallbacks(it) }
            restartRunnable = null
            isRecognizerListening = false
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
        }
    }

    private fun destroyRecognizerInternal() {
        mainHandler.post {
            restartRunnable?.let { mainHandler.removeCallbacks(it) }
            restartRunnable = null
            isRecognizerListening = false
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }

    /**
     * Suppresses Samsung/Google notification chime briefly while starting system STT
     * (never mutes STREAM_MUSIC so TTS playback is unaffected).
     */
    private fun suppressStartChimeBriefly(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                mainHandler.postDelayed({
                    try {
                        am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                        am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                    } catch (_: Exception) {}
                }, 250L)
            }
        } catch (_: Exception) {}
    }

    private fun startRecognizerOnMainThread() {
        val ctx = appContext ?: return
        if (!isCallActive || _isMuted.value || _callState.value != LiveCallState.LISTENING) return
        if (isRecognizerListening) return

        try {
            if (speechRecognizer == null) {
                speechRecognizer = createBestSpeechRecognizer(ctx)
                speechRecognizer?.setRecognitionListener(recognitionListener)
            }

            val savedLang = com.example.voice.TitanAssistantConfig.getSelectedLanguage(ctx)
            val langTag = if (savedLang.code != com.example.voice.TitanAssistantConfig.LANG_AUTO) {
                savedLang.bcp47Tag
            } else {
                when (_activeLanguage.value) {
                    LanguageMode.BENGALI -> "bn-IN"
                    LanguageMode.ENGLISH -> "en-IN"
                    LanguageMode.AUTO -> com.example.voice.TitanAssistantConfig.getEffectiveSttLanguageTag(ctx)
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            }

            if (!preferOnDeviceRecognizer) {
                suppressStartChimeBriefly(ctx)
            }

            lastPartialText = ""
            isRecognizerListening = true
            speechRecognizer?.startListening(intent)
            Log.i(TAG, "Live Voice STT listening started (onDevice=$preferOnDeviceRecognizer, lang=$langTag)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer: ${e.message}", e)
            isRecognizerListening = false
            destroyRecognizerInternal()
            preferOnDeviceRecognizer = false
            scheduleRecognizerRestart(600L)
        }
    }

    private fun createBestSpeechRecognizer(ctx: Context): SpeechRecognizer {
        if (preferOnDeviceRecognizer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
                    return SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                }
            } catch (e: Exception) {
                Log.w(TAG, "On-device SpeechRecognizer init fallback: ${e.message}")
            }
        }
        preferOnDeviceRecognizer = false
        return SpeechRecognizer.createSpeechRecognizer(ctx)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (isCallActive && _callState.value == LiveCallState.LISTENING) {
                _audioLevel.value = 0.08f
            }
        }

        override fun onBeginningOfSpeech() {
            lastPartialText = ""
            appContext?.let { ctx ->
                engineScope.launch {
                    try {
                        TitanDirectEngine.speculativePreWarm(ctx, "gemini-3.5-flash-lite")
                    } catch (_: Exception) {}
                }
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (isCallActive && _callState.value == LiveCallState.LISTENING && !_isMuted.value) {
                val norm = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                _audioLevel.value = norm
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isRecognizerListening = false
            _audioLevel.value = 0.05f
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isCallActive || _callState.value != LiveCallState.LISTENING) return
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim().orEmpty()
            if (partial.isNotBlank()) {
                lastPartialText = partial
                _liveSpokenText.value = partial
            }
        }

        override fun onResults(results: Bundle?) {
            isRecognizerListening = false
            if (!isCallActive) return
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val finalSpeech = matches?.firstOrNull()?.trim().orEmpty().ifBlank { lastPartialText.trim() }
            lastPartialText = ""

            if (finalSpeech.isNotBlank()) {
                _liveSpokenText.value = finalSpeech
                _callState.value = LiveCallState.THINKING
                Log.i(TAG, "Live Voice Recognized: '$finalSpeech' -> Dispatching to AI")
                onFinalSpeechCallback?.invoke(finalSpeech)
            } else if (!_isMuted.value && _callState.value == LiveCallState.LISTENING) {
                scheduleRecognizerRestart(450L)
            }
        }

        override fun onError(error: Int) {
            isRecognizerListening = false
            if (!isCallActive) return

            Log.w(TAG, "Live Voice STT error code: $error (onDevice=$preferOnDeviceRecognizer)")

            // If we already captured partial speech before timeout/no-match, dispatch it!
            val rescued = lastPartialText.trim()
            lastPartialText = ""
            if (rescued.length >= 2 && (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)) {
                _liveSpokenText.value = rescued
                _callState.value = LiveCallState.THINKING
                onFinalSpeechCallback?.invoke(rescued)
                return
            }

            // If on-device recognizer lacks the language pack (codes 12/13 or 5/8), switch to system recognizer immediately
            if (preferOnDeviceRecognizer && (error == 12 || error == 13 || error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_AUDIO)) {
                preferOnDeviceRecognizer = false
                destroyRecognizerInternal()
                if (!_isMuted.value && _callState.value == LiveCallState.LISTENING) {
                    scheduleRecognizerRestart(250L)
                }
                return
            }

            // Recreate recognizer cleanly on busy/client errors
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                destroyRecognizerInternal()
            }

            if (!_isMuted.value && _callState.value == LiveCallState.LISTENING) {
                val backoff = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 550L
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 1200L
                    else -> 800L
                }
                scheduleRecognizerRestart(backoff)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // =========================================================================
    // Fallback Hardware AudioRecord + PSID-Native WAV Upload (if no system STT)
    // =========================================================================
    @SuppressLint("MissingPermission")
    private fun startContinuousAudioRecordStream(context: Context) {
        if (recordingJob?.isActive == true && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return
        }

        recordingJob?.cancel()
        ringBuffer.clear()

        recordingJob = engineScope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                .coerceAtLeast(6400)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBuf * 2
                ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                    ?: AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        minBuf * 2
                    )
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord initialization error: ${e.message}", e)
                null
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                _callState.value = LiveCallState.IDLE
                return@launch
            }

            audioRecord = recorder
            try {
                val sid = recorder.audioSessionId
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sid)?.apply { enabled = true }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sid)?.apply { enabled = true }
                }
                recorder.startRecording()
            } catch (e: Exception) {
                releaseAudioRecord()
                return@launch
            }

            val frameBytes = ByteArray(BYTES_PER_FRAME)
            val utteranceStream = ByteArrayOutputStream()
            var isSpeakingUtterance = false
            var speechDurationMs = 0L
            var silenceDurationMs = 0L
            var dynamicNoiseFloor = 120.0

            while (isCallActive && isActive) {
                val read = recorder.read(frameBytes, 0, frameBytes.size)
                if (read <= 0) continue

                ringBuffer.write(frameBytes, 0, read)
                var sum = 0.0
                val shortsCount = read / 2
                val bb = ByteBuffer.wrap(frameBytes, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until shortsCount) {
                    val s = bb.short.toDouble()
                    sum += s * s
                }
                val rms = if (shortsCount > 0) sqrt(sum / shortsCount) else 0.0
                if (_callState.value == LiveCallState.LISTENING && !_isMuted.value) {
                    _audioLevel.value = (rms / 2800.0).toFloat().coerceIn(0.04f, 1f)
                }

                if (_isMuted.value || _callState.value != LiveCallState.LISTENING || System.currentTimeMillis() < echoGuardUntilMs) {
                    isSpeakingUtterance = false
                    speechDurationMs = 0L
                    silenceDurationMs = 0L
                    continue
                }

                if (!isSpeakingUtterance) {
                    dynamicNoiseFloor = (dynamicNoiseFloor * 0.94 + rms * 0.06).coerceIn(45.0, 650.0)
                }
                val voiceThreshold = maxOf(dynamicNoiseFloor * 2.1, 180.0)
                if (rms >= voiceThreshold) {
                    if (!isSpeakingUtterance) {
                        isSpeakingUtterance = true
                        utteranceStream.reset()
                        utteranceStream.write(ringBuffer.getSnapshot())
                        speechDurationMs = 0L
                        silenceDurationMs = 0L
                    }
                    utteranceStream.write(frameBytes, 0, read)
                    speechDurationMs += FRAME_MS
                    silenceDurationMs = 0L
                } else if (isSpeakingUtterance) {
                    utteranceStream.write(frameBytes, 0, read)
                    silenceDurationMs += FRAME_MS
                }

                if ((isSpeakingUtterance && silenceDurationMs >= SILENCE_ENDPOINT_MS && speechDurationMs >= MIN_SPEECH_DURATION_MS) ||
                    (isSpeakingUtterance && speechDurationMs >= MAX_UTTERANCE_MS)
                ) {
                    val capturedPcm = utteranceStream.toByteArray()
                    utteranceStream.reset()
                    isSpeakingUtterance = false
                    speechDurationMs = 0L
                    silenceDurationMs = 0L
                    engineScope.launch {
                        processCapturedUtterance(context, capturedPcm)
                    }
                }
            }
            releaseAudioRecord()
        }
    }

    private suspend fun processCapturedUtterance(context: Context, pcmBytes: ByteArray) {
        if (pcmBytes.size < 4800) return
        val wavBytes = buildWavBytes(pcmBytes, SAMPLE_RATE)
        _callState.value = LiveCallState.THINKING

        val transcribedText = transcribeAudioWav(context, wavBytes)
        val cleanSpeech = transcribedText.trim()

        if (cleanSpeech.isNotBlank() && isCallActive) {
            _liveSpokenText.value = cleanSpeech
            onFinalSpeechCallback?.invoke(cleanSpeech)
        } else if (isCallActive && !_isMuted.value) {
            setCallState(LiveCallState.LISTENING)
        }
    }

    suspend fun transcribeAudioWav(context: Context, wavBytes: ByteArray): String = withContext(Dispatchers.IO) {
        try {
            val psidText = TitanDirectEngine.uploadWavAndTranscribeViaPsid(context, wavBytes)
            if (psidText.isNotBlank() &&
                !psidText.contains("I cannot", ignoreCase = true) &&
                !psidText.contains("unable to", ignoreCase = true)
            ) {
                return@withContext psidText
            }
        } catch (e: Exception) {
            Log.w(TAG, "PSID audio transcription notice: ${e.message}")
        }
        return@withContext ""
    }

    fun speakAndAutoResume(context: Context, responseText: String) {
        if (!isCallActive) return

        _liveResponseText.value = responseText
        setCallState(LiveCallState.SPEAKING)

        TitanInstantTtsEngine.speakFullResponse(
            context = context,
            rawText = responseText,
            onStart = {
                setCallState(LiveCallState.SPEAKING)
            },
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
            scheduleRecognizerRestart(120L)
        }
    }

    fun toggleMute() {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        if (newMute) {
            _audioLevel.value = 0f
            stopRecognizerInternal()
        } else if (isCallActive && _callState.value == LiveCallState.LISTENING) {
            scheduleRecognizerRestart(150L)
        }
    }

    fun toggleSpeakerphone(context: Context) {
        val newState = !_isSpeakerOn.value
        setSpeakerphone(context, newState)
    }

    private fun setSpeakerphone(context: Context, enabled: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (enabled) {
                audioManager.mode = AudioManager.MODE_NORMAL
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try { audioManager.clearCommunicationDevice() } catch (_: Exception) {}
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }
            } else {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpiece != null) {
                        audioManager.setCommunicationDevice(earpiece)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                }
            }
            _isSpeakerOn.value = enabled
        } catch (e: Exception) {
            Log.w(TAG, "setSpeakerphone warning: ${e.message}")
        }
    }

    fun endLiveCall() {
        if (!isCallActive) return
        isCallActive = false
        _callState.value = LiveCallState.IDLE
        _audioLevel.value = 0f
        TitanInstantTtsEngine.stopSpeech()
        destroyRecognizerInternal()

        recordingJob?.cancel()
        recordingJob = null
        releaseAudioRecord()

        appContext?.let { ctx ->
            abandonCallAudioFocus(ctx)
            try {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.mode = AudioManager.MODE_NORMAL
            } catch (_: Exception) {}
            TitanCallForegroundService.endCall(ctx)
        }
        TitanAudioSessionManager.releaseMicOwnership("TitanLiveCallEngine")
        Log.i(TAG, "Live Phone Call Ended -> Audio resources released")
    }

    private fun releaseAudioRecord() {
        try {
            echoCanceler?.release()
            echoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (_: Exception) {}
    }

    fun buildWavBytes(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * 2 * channels
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte()
        header[5] = (totalDataLen shr 8 and 0xffL).toByte()
        header[6] = (totalDataLen shr 16 and 0xffL).toByte()
        header[7] = (totalDataLen shr 24 and 0xffL).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = 2; header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte()
        header[41] = (totalAudioLen shr 8 and 0xffL).toByte()
        header[42] = (totalAudioLen shr 16 and 0xffL).toByte()
        header[43] = (totalAudioLen shr 24 and 0xffL).toByte()

        return header + pcmData
    }
}
