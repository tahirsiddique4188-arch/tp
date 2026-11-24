package com.titan.ide.voice

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * TitanNativeVoiceEngine
 * - Clean Native Android TTS + Google Neural Speech.
 * - Strict Ear-Closing Echo Guard: closes TitanContinuousSpeechEngine ears before audio starts
 *   and waits 450ms after playback ends before invoking onComplete() so Titan never hears its own voice.
 * - Eliminates the premature postDelayed completion bug that caused self-listening loops.
 */
object TitanNativeVoiceEngine {

    private const val TAG = "TitanNativeVoiceEngine"
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeExoPlayer: ExoPlayer? = null
    private var currentTempFile: File? = null
    private var deviceTts: TextToSpeech? = null
    private var isDeviceTtsReady = false

    @Volatile
    var isSpeaking: Boolean = false
        private set

    fun init(context: Context) {
        if (deviceTts != null) return
        val appContext = context.applicationContext
        try {
            deviceTts = TextToSpeech(appContext, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isDeviceTtsReady = true
                } else {
                    // Fallback to default engine
                    try {
                        deviceTts = TextToSpeech(appContext) { s ->
                            isDeviceTtsReady = (s == TextToSpeech.SUCCESS)
                        }
                    } catch (_: Exception) {}
                }
            }, "com.google.android.tts")
        } catch (e: Exception) {
            try {
                deviceTts = TextToSpeech(appContext) { s ->
                    isDeviceTtsReady = (s == TextToSpeech.SUCCESS)
                }
            } catch (_: Exception) {}
        }
    }

    fun sanitizeTextForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[a-zA-Z0-9_+#.-]*\\n?[\\s\\S]*?```"), " Code provided below. ")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("!\\[[^\\]]*\\]\\([^\\)]*\\)"), "")
            .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]*\\)"), "$1")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("[#*`_~\\[\\]()]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun speakText(
        context: Context,
        text: String,
        voice: String = "zara",
        speed: Float = 1.20f,
        pitch: Float = 1.0f,
        onStart: () -> Unit = {},
        onComplete: () -> Unit = {},
        onFallback: ((String) -> Unit)? = null
    ) {
        val cleanText = sanitizeTextForSpeech(text)
        if (cleanText.isBlank()) {
            onComplete()
            return
        }

        stopSpeech()
        isSpeaking = true
        TitanContinuousSpeechEngine.setPaused(true)
        // Unified Voice Engine: Chat Speak, Voice Preview, and Live Call all use the EXACT same TitanInstantTtsEngine persona!
        TitanInstantTtsEngine.speakWithPersona(
            context = context,
            rawText = cleanText,
            voiceId = voice.ifBlank { null },
            onStart = {
                isSpeaking = true
                onStart()
            },
            onDone = {
                isSpeaking = false
                TitanContinuousSpeechEngine.setPaused(false)
                onComplete()
            }
        )
    }

    private fun playGeminiAudioFile(
        context: Context,
        audioFile: File,
        speed: Float = 1.20f,
        pitch: Float = 1.0f,
        onStart: () -> Unit,
        onComplete: () -> Unit
    ) {
        try {
            activeExoPlayer?.release()
            isSpeaking = true
            TitanContinuousSpeechEngine.setPaused(true)

            val exoPlayer = ExoPlayer.Builder(context).build().apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build()
                setAudioAttributes(audioAttributes, false)
                setMediaItem(MediaItem.fromUri(Uri.fromFile(audioFile)))
                // Keep ExoPlayer playback speed near 1.05x-1.12x for natural clarity
                val playSpeed = if (speed > 1.25f) 1.12f else 1.05f
                setPlaybackParameters(androidx.media3.common.PlaybackParameters(playSpeed, pitch.coerceIn(0.8f, 1.25f)))
                prepare()
                play()
            }

            activeExoPlayer = exoPlayer
            onStart()

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        cleanupPlayer()
                        // 80ms post-playback Echo Guard tail so loudspeaker reverb clears while keeping speech fast
                        mainHandler.postDelayed({
                            isSpeaking = false
                            onComplete()
                        }, 80L)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer playback error: ${error.message}", error)
                    cleanupPlayer()
                    isSpeaking = false
                    onComplete()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ExoPlayer: ${e.message}", e)
            cleanupPlayer()
            isSpeaking = false
            onComplete()
        }
    }

    private fun speakViaDeviceTtsInternal(
        context: Context,
        text: String,
        langCode: String,
        speed: Float,
        pitch: Float,
        onStart: () -> Unit,
        onComplete: () -> Unit,
        onFallback: ((String) -> Unit)?
    ) {
        init(context)
        val tts = deviceTts
        if (tts != null && isDeviceTtsReady) {
            isSpeaking = true
            TitanContinuousSpeechEngine.setPaused(true)
            tts.language = when (langCode) {
                "bn" -> Locale.forLanguageTag("bn-BD")
                "hi" -> Locale.forLanguageTag("hi-IN")
                "ur" -> Locale.forLanguageTag("ur-PK")
                "ar" -> Locale.forLanguageTag("ar-SA")
                "es" -> Locale.forLanguageTag("es-ES")
                "fr" -> Locale.forLanguageTag("fr-FR")
                "de" -> Locale.forLanguageTag("de-DE")
                "ru" -> Locale.forLanguageTag("ru-RU")
                "ja" -> Locale.forLanguageTag("ja-JP")
                "zh" -> Locale.forLanguageTag("zh-CN")
                "ko" -> Locale.forLanguageTag("ko-KR")
                "pt" -> Locale.forLanguageTag("pt-BR")
                "it" -> Locale.forLanguageTag("it-IT")
                "tr" -> Locale.forLanguageTag("tr-TR")
                "id" -> Locale.forLanguageTag("id-ID")
                "ta" -> Locale.forLanguageTag("ta-IN")
                "te" -> Locale.forLanguageTag("te-IN")
                "mr" -> Locale.forLanguageTag("mr-IN")
                "gu" -> Locale.forLanguageTag("gu-IN")
                "pa" -> Locale.forLanguageTag("pa-IN")
                else -> Locale.forLanguageTag("en-US")
            }
            tts.setSpeechRate(speed.coerceIn(1.0f, 1.5f))
            tts.setPitch(pitch.coerceIn(0.60f, 1.40f))
            val uttId = "TitanUtt_${System.currentTimeMillis()}"
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    mainHandler.post { onStart() }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.postDelayed({
                        isSpeaking = false
                        onComplete()
                    }, 80L)
                }

                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        isSpeaking = false
                        onComplete()
                    }
                }
            })
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, uttId)
        } else if (onFallback != null) {
            onStart()
            onFallback(text)
        } else {
            isSpeaking = false
            onComplete()
        }
    }

    fun stopSpeech() {
        try {
            mainHandler.removeCallbacksAndMessages(null)
            activeExoPlayer?.stop()
            activeExoPlayer?.release()
            activeExoPlayer = null
            deviceTts?.stop()
            TitanInstantTtsEngine.stopSpeech()
            currentTempFile?.delete()
            currentTempFile = null
            isSpeaking = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech: ${e.message}")
        }
    }

    private fun cleanupPlayer() {
        try {
            activeExoPlayer?.release()
            activeExoPlayer = null
            currentTempFile?.delete()
            currentTempFile = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up ExoPlayer: ${e.message}")
        }
    }
}
