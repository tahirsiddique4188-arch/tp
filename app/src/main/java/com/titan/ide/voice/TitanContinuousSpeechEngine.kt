package com.titan.ide.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * TitanContinuousSpeechEngine — 100% Zero-Beep Hardware AudioRecord Dictation Engine
 * ZERO android.speech.SpeechRecognizer -> ZERO ting-ting sounds!
 * Uses adaptive noise-floor VAD + PSID-native / keyless multilingual transcription.
 *
 * Developer: Developer Tahir (@Tahirsiddik)
 */
object TitanContinuousSpeechEngine {

    private const val TAG = "TitanSpeechEngine"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val FRAME_MS = 20L
    private const val BYTES_PER_FRAME = (SAMPLE_RATE * FRAME_MS / 1000L).toInt() * 2

    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingJob: Job? = null

    @Volatile private var shouldKeepListening = false
    @Volatile private var isEchoFilterActive = false

    private var onPartialCallback: ((String) -> Unit)? = null
    private var onFinalCallback: ((String) -> Unit)? = null
    var onBargeInTriggered: (() -> Unit)? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _activeLanguageTag = MutableStateFlow("auto")
    val activeLanguageTag: StateFlow<String> = _activeLanguageTag.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startContinuousListening(
        context: Context,
        languageTag: String? = null,
        pauseOnResultForLiveTurn: Boolean = false,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit
    ) {
        val appCtx = context.applicationContext
        onPartialCallback = onPartial
        onFinalCallback = onResult
        shouldKeepListening = true
        isEchoFilterActive = false
        _isListening.value = true
        _partialText.value = ""

        startHardwareAudioRecordLoop(appCtx)
    }

    @SuppressLint("MissingPermission")
    private fun startHardwareAudioRecordLoop(context: Context) {
        if (recordingJob?.isActive == true && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return
        }

        recordingJob?.cancel()
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
                Log.e(TAG, "Dictation AudioRecord init error: ${e.message}")
                null
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                _isListening.value = false
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
                Log.e(TAG, "Dictation AudioRecord start error: ${e.message}")
                releaseAudioRecord()
                return@launch
            }

            val frameBytes = ByteArray(BYTES_PER_FRAME)
            val utterance = ByteArrayOutputStream()
            var isSpeaking = false
            var speechMs = 0L
            var silenceMs = 0L
            var noiseFloor = 120.0

            while (shouldKeepListening && isActive) {
                val read = recorder.read(frameBytes, 0, frameBytes.size)
                if (read <= 0) continue

                var sum = 0.0
                val shortsCount = read / 2
                val bb = ByteBuffer.wrap(frameBytes, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until shortsCount) {
                    val s = bb.short.toDouble()
                    sum += s * s
                }
                val rms = if (shortsCount > 0) sqrt(sum / shortsCount) else 0.0
                _audioLevel.value = (rms / 2800.0).toFloat().coerceIn(0.04f, 1f)

                if (isEchoFilterActive) continue

                if (!isSpeaking) {
                    noiseFloor = (noiseFloor * 0.94 + rms * 0.06).coerceIn(45.0, 650.0)
                }
                val threshold = maxOf(noiseFloor * 2.1, 180.0)

                if (rms >= threshold) {
                    isSpeaking = true
                    utterance.write(frameBytes, 0, read)
                    speechMs += FRAME_MS
                    silenceMs = 0L
                } else if (isSpeaking) {
                    utterance.write(frameBytes, 0, read)
                    silenceMs += FRAME_MS
                }

                if ((isSpeaking && silenceMs >= 550L && speechMs >= 240L) || (isSpeaking && speechMs >= 10_000L)) {
                    val pcm = utterance.toByteArray()
                    utterance.reset()
                    isSpeaking = false
                    speechMs = 0L
                    silenceMs = 0L

                    engineScope.launch {
                        val wav = TitanLiveCallEngine.buildWavBytes(pcm, SAMPLE_RATE)
                        val text = TitanLiveCallEngine.transcribeAudioWav(context, wav).trim()
                        if (text.isNotBlank() && shouldKeepListening) {
                            _partialText.value = text
                            mainHandler.post {
                                onPartialCallback?.invoke(text)
                                onFinalCallback?.invoke(text)
                            }
                        }
                    }
                }
            }

            releaseAudioRecord()
        }
    }

    private fun releaseAudioRecord() {
        try {
            echoCanceler?.release(); echoCanceler = null
            noiseSuppressor?.release(); noiseSuppressor = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (_: Exception) {}
        _isListening.value = false
    }

    fun setPaused(paused: Boolean) {
        isEchoFilterActive = paused
    }

    fun stopListening() {
        shouldKeepListening = false
        isEchoFilterActive = false
        _isListening.value = false
        _audioLevel.value = 0f
        _partialText.value = ""
        recordingJob?.cancel()
        recordingJob = null
        releaseAudioRecord()
    }

    fun stopContinuousListening() = stopListening()
    fun getPreferredLanguageTag(context: Context): String = "auto"
    fun setPreferredLanguageTag(context: Context, langTag: String) {}
    fun cycleLanguage(context: Context): String = "auto"
}
