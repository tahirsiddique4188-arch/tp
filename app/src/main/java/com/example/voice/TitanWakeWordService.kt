package com.example.voice

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.ChatMessage
import com.example.data.model.SenderRole
import com.titan.ide.voice.TitanAudioSessionManager
import com.titan.ide.voice.TitanInstantTtsEngine
import com.titan.ide.voice.TitanLiveSessionManager
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

/**
 * TitanWakeWordService — Zero-Beep Silent VAD-Gated Custom-Name Wake Word Service
 *
 * ZERO background "ting-ting" beeps:
 * 1. During silence, ONLY hardware AudioRecord VAD runs (never starts SpeechRecognizer in silence!).
 * 2. Only when clear human speech is detected (~80ms above noise floor), it briefly hands off
 *    to SpeechRecognizer (with 320ms chime mute) to check for your custom wake word, then
 *    returns immediately to 100% silent AudioRecord VAD!
 */
class TitanWakeWordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var vadJob: Job? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    @Volatile private var isWakeActive = false
    @Volatile private var isRecognizerListening = false
    @Volatile private var preferOnDevice = true
    @Volatile private var lastWakeTriggeredAtMs = 0L

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "custom_wake_word" || key == "assistant_custom_name" || key == "assistant_app_language") {
            updateNotification()
        }
    }

    companion object {
        const val CHANNEL_ID = "TitanWakeWordChannel"
        const val NOTIF_ID = 2002
        const val ACTION_START = "ACTION_START_WAKE_WORD"
        const val ACTION_STOP = "ACTION_STOP_WAKE_WORD"
        const val TAG = "TitanWakeWordService"

        var isServiceRunning = false
            private set

        @Volatile
        var instance: TitanWakeWordService? = null
            private set

        fun restartWithNewName(context: Context) {
            val inst = instance
            if (inst != null && isServiceRunning) {
                inst.updateNotification()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        val notif = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        acquireCpuWakeLock()
        getSharedPreferences("titan_prefs", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
        getSharedPreferences("titan_assistant_prefs", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)

        isServiceRunning = true
        isWakeActive = true
        if (!TitanLiveSessionManager.isSessionActive()) {
            startSilentWakeVad()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        isWakeActive = true
        acquireCpuWakeLock()
        updateNotification()
        if (!TitanLiveSessionManager.isSessionActive() && vadJob?.isActive != true && !isRecognizerListening) {
            startSilentWakeVad()
        }
        return START_STICKY
    }

    fun pauseListening() {
        vadJob?.cancel()
        vadJob = null
        releaseAudioRecord()
        handler.post {
            isRecognizerListening = false
            try { speechRecognizer?.cancel() } catch (_: Exception) {}
            TitanAudioSessionManager.releaseMicOwnership("TitanWakeWordService")
        }
    }

    fun resumeListeningIfIdle() {
        if (!isServiceRunning || !isWakeActive) return
        if (TitanLiveSessionManager.isSessionActive() || TitanAudioSessionManager.isMicInUse()) return
        acquireCpuWakeLock()
        handler.postDelayed({
            if (isServiceRunning && isWakeActive && !TitanLiveSessionManager.isSessionActive() && !TitanAudioSessionManager.isMicInUse()) {
                startSilentWakeVad()
            }
        }, 500L)
    }

    @SuppressLint("MissingPermission")
    private fun startSilentWakeVad() {
        if (!isServiceRunning || !isWakeActive || isRecognizerListening || TitanLiveSessionManager.isSessionActive()) return
        if (vadJob?.isActive == true) return
        if (!TitanAudioSessionManager.requestMicOwnership("TitanWakeWordService")) return

        vadJob = serviceScope.launch(Dispatchers.IO) {
            val sr = 16000
            val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(6400)
            val recorder = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
                    .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                    ?: AudioRecord(MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
            } catch (_: Exception) { null }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                TitanAudioSessionManager.releaseMicOwnership("TitanWakeWordService")
                return@launch
            }

            audioRecord = recorder
            try {
                val sid = recorder.audioSessionId
                if (AcousticEchoCanceler.isAvailable()) echoCanceler = AcousticEchoCanceler.create(sid)?.apply { enabled = true }
                if (NoiseSuppressor.isAvailable()) noiseSuppressor = NoiseSuppressor.create(sid)?.apply { enabled = true }
                recorder.startRecording()
            } catch (_: Exception) {
                releaseAudioRecord()
                TitanAudioSessionManager.releaseMicOwnership("TitanWakeWordService")
                return@launch
            }

            val frame = ByteArray(640) // 20ms
            var noiseFloor = 140.0
            var speechFrames = 0

            while (isServiceRunning && isWakeActive && isActive && !TitanLiveSessionManager.isSessionActive() && !isRecognizerListening) {
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
                val threshold = maxOf(noiseFloor * 2.8, 460.0)
                if (rms >= threshold) {
                    speechFrames++
                    if (speechFrames >= 4) {
                        releaseAudioRecord()
                        handler.post { startSingleWakeRecognizer() }
                        return@launch
                    }
                } else {
                    speechFrames = 0
                    noiseFloor = (noiseFloor * 0.94 + rms * 0.06).coerceIn(50.0, 500.0)
                }
            }
            releaseAudioRecord()
            TitanAudioSessionManager.releaseMicOwnership("TitanWakeWordService")
        }
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

    private fun destroyRecognizer() {
        handler.post {
            isRecognizerListening = false
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }

    @Volatile private var wakeMuted = false
    @Volatile private var wakeSavedMusicVol = -1

    private fun restoreWakeVolume() {
        if (!wakeMuted) return
        wakeMuted = false
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
            }
            if (wakeSavedMusicVol > 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, wakeSavedMusicVol, 0)
            }
            wakeSavedMusicVol = -1
        } catch (_: Exception) {}
    }

    private fun suppressChimeBriefly() {
        if (TitanInstantTtsEngine.isSpeaking || wakeMuted) return
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (cur > 0) wakeSavedMusicVol = cur
            wakeMuted = true
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun startSingleWakeRecognizer() {
        if (!isServiceRunning || !isWakeActive || isRecognizerListening || TitanLiveSessionManager.isSessionActive()) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            startSilentWakeVad()
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                } else {
                    preferOnDevice = false
                    SpeechRecognizer.createSpeechRecognizer(this)
                }
                speechRecognizer?.setRecognitionListener(wakeRecognitionListener)
            }

            val langTag = TitanAssistantConfig.getEffectiveSttLanguageTag(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }

            suppressChimeBriefly()
            isRecognizerListening = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isRecognizerListening = false
            restoreWakeVolume()
        destroyRecognizer()
            preferOnDevice = false
            TitanAudioSessionManager.releaseMicOwnership("TitanWakeWordService")
            handler.postDelayed({ startSilentWakeVad() }, 500L)
        }
    }

    private val wakeRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isRecognizerListening = false }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            if (text.isNotBlank()) {
                checkWakeMatch(text, isFinal = false)
            }
        }

        override fun onResults(results: Bundle?) {
            isRecognizerListening = false
            handler.postDelayed({ restoreWakeVolume() }, 260L)
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            val handled = if (text.isNotBlank()) checkWakeMatch(text, isFinal = true) else false
            if (!handled && isServiceRunning && isWakeActive && !TitanLiveSessionManager.isSessionActive()) {
                TitanAudioSessionManager.releaseMicOwnership("TitanWakeWordService")
                handler.postDelayed({ startSilentWakeVad() }, 300L)
            }
        }

        override fun onError(error: Int) {
            isRecognizerListening = false
            handler.postDelayed({ restoreWakeVolume() }, 260L)
            if (!isServiceRunning || !isWakeActive || TitanLiveSessionManager.isSessionActive()) return
            if (preferOnDevice && (error == 12 || error == 13 || error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_AUDIO)) {
                preferOnDevice = false
                restoreWakeVolume()
        destroyRecognizer()
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                restoreWakeVolume()
        destroyRecognizer()
            }
            TitanAudioSessionManager.releaseMicOwnership("TitanWakeWordService")
            handler.postDelayed({ startSilentWakeVad() }, 400L)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun checkWakeMatch(rawText: String, isFinal: Boolean): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastWakeTriggeredAtMs < 3500L) return false
        if (TitanLiveSessionManager.isSessionActive()) return false

        val lower = rawText.lowercase(Locale.ROOT).trim()
        val wakeWords = TitanAssistantConfig.getWakeWordList(this)
        val matchedKw = wakeWords.firstOrNull { kw -> kw.isNotBlank() && lower.contains(kw) } ?: return false

        val trailing = lower.substringAfter(matchedKw).trim(' ', ',', '.', '?', '!')
        if (!isFinal && trailing.length < 3) return false

        lastWakeTriggeredAtMs = now
        pauseListening()
        wakeScreenBriefly()
        restoreWakeVolume()
        playWakeChime()

        if (trailing.length >= 4) {
            serviceScope.launch {
                try {
                    val botName = TitanAssistantConfig.getAssistantName(this@TitanWakeWordService)
                    val selectedLang = TitanAssistantConfig.getSelectedLanguage(this@TitanWakeWordService)
                    val langRule = if (selectedLang.promptDirective.isNotBlank()) selectedLang.promptDirective else "Reply in the user's exact language."
                    val prompt = "SYSTEM: Your name is $botName. Reply concisely in 1-2 spoken sentences (no markdown). $langRule If the user asks to perform a phone task, append [TITAN_GOAL: <task>]. If the user asks to build an app/game/website, append [TITAN_BUILD: <spec>].\nUser said: $trailing"
                    val reply = com.titan.ide.network.TitanDirectEngine.executeChat(
                        context = this@TitanWakeWordService,
                        modelId = "gemini-3.5-flash-lite",
                        messages = listOf(ChatMessage(sender = SenderRole.USER, content = prompt))
                    ).trim()

                    val goalMatch = Regex("""\[TITAN_GOAL:\s*(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(reply)
                    val goal = goalMatch?.groupValues?.getOrNull(1)?.trim()
                    if (!goal.isNullOrBlank()) {
                        com.titan.ide.agent.TitanDynamicAgentLoop.dispatchBackgroundSubAgent(
                            context = this@TitanWakeWordService,
                            userGoal = goal,
                            onCompleted = { summary ->
                                handler.post {
                                    TitanInstantTtsEngine.speakFullResponse(this@TitanWakeWordService, summary) {
                                        resumeListeningIfIdle()
                                    }
                                }
                            }
                        )
                    }

                    val spoken = reply
                        .replace(Regex("""\[TITAN_(?:GOAL|ACTION|BUILD):.*?\]""", RegexOption.DOT_MATCHES_ALL), "")
                        .replace(Regex("\\s*⚠️\\s*Network.*$", RegexOption.DOT_MATCHES_ALL), "")
                        .trim()
                        .ifBlank { TitanAssistantConfig.getAwakenGreeting(this@TitanWakeWordService) }

                    handler.post {
                        TitanInstantTtsEngine.speakFullResponse(this@TitanWakeWordService, spoken) {
                            resumeListeningIfIdle()
                        }
                    }
                } catch (_: Exception) {
                    resumeListeningIfIdle()
                }
            }
        } else {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("EXTRA_START_LIVE_VOICE", true)
            }
            try {
                startActivity(launchIntent)
            } catch (_: Exception) {
                resumeListeningIfIdle()
            }
        }
        return true
    }

    private fun playWakeChime() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            handler.postDelayed({ try { tg.release() } catch (_: Exception) {} }, 260L)
        } catch (_: Exception) {}
    }

    private fun acquireCpuWakeLock() {
        try {
            if (cpuWakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanIDE::WakeWordLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseCpuWakeLock() {
        try { if (cpuWakeLock?.isHeld == true) cpuWakeLock?.release() } catch (_: Exception) {}
        cpuWakeLock = null
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (!pm.isInteractive) {
                val screenLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "TitanIDE::WakeScreenPulse"
                )
                screenLock.acquire(4500L)
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Titan Always-On Wake Word", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Continuous hands-free wake word listening"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply { putExtra("EXTRA_START_LIVE_VOICE", true) }
        val pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val assistantName = TitanAssistantConfig.getAssistantName(this)
        val langLabel = TitanAssistantConfig.getSelectedLanguage(this).displayName
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$assistantName Wake Word Active ($langLabel)")
            .setContentText("Say 'Hey $assistantName' or '$assistantName' anytime")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, createNotification())
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        isWakeActive = false
        isServiceRunning = false
        if (instance == this) instance = null
        try {
            getSharedPreferences("titan_prefs", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
            getSharedPreferences("titan_assistant_prefs", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
        } catch (_: Exception) {}
        vadJob?.cancel()
        releaseAudioRecord()
        restoreWakeVolume()
        destroyRecognizer()
        TitanAudioSessionManager.releaseMicOwnership("TitanWakeWordService")
        releaseCpuWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
