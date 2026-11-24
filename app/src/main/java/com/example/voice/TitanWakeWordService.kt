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
import android.media.AudioManager
import android.media.ToneGenerator
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
import java.util.Locale

/**
 * TitanWakeWordService — Reliable Always-On Custom-Name Wake Word Service
 *
 * Key Upgrades:
 * 1. Uses Real-Time On-Device / System SpeechRecognizer (works 100% WITH OR WITHOUT PSID!).
 * 2. Matches user's custom assistant name dynamically (`TitanAssistantConfig.getWakeWordList`)
 *    in Auto or user's selected Indian language.
 * 3. One-Shot Wake Command ("Hey Myra, YouTube open koro" / "Myra, aaj weather kemon?"):
 *    executes automation, app build, or direct spoken reply immediately!
 * 4. Name-Only Wake ("Hey Myra"): wakes screen, plays subtle chime, and opens Live Voice Call!
 */
class TitanWakeWordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var restartRunnable: Runnable? = null

    @Volatile private var isWakeActive = false
    @Volatile private var isRecognizerListening = false
    @Volatile private var preferOnDevice = true
    @Volatile private var lastWakeTriggeredAtMs = 0L

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "custom_wake_word" || key == "assistant_custom_name" || key == "assistant_app_language") {
            updateNotification()
            if (isWakeActive && !TitanLiveSessionManager.isSessionActive()) {
                pauseListening()
                scheduleStartListening(350L)
            }
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
                inst.pauseListening()
                inst.scheduleStartListening(350L)
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
            scheduleStartListening(400L)
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
        if (!TitanLiveSessionManager.isSessionActive() && !isRecognizerListening) {
            scheduleStartListening(300L)
        }
        return START_STICKY
    }

    fun pauseListening() {
        handler.post {
            restartRunnable?.let { handler.removeCallbacks(it) }
            restartRunnable = null
            isRecognizerListening = false
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            TitanAudioSessionManager.releaseMicOwnership("TitanWakeWordService")
        }
    }

    fun resumeListeningIfIdle() {
        if (!isServiceRunning || !isWakeActive) return
        if (TitanLiveSessionManager.isSessionActive() || TitanAudioSessionManager.isMicInUse()) return
        acquireCpuWakeLock()
        scheduleStartListening(500L)
    }

    private fun scheduleStartListening(delayMs: Long) {
        handler.post {
            restartRunnable?.let { handler.removeCallbacks(it) }
            val r = Runnable {
                if (isServiceRunning && isWakeActive && !TitanLiveSessionManager.isSessionActive()) {
                    startWakeRecognizer()
                }
            }
            restartRunnable = r
            handler.postDelayed(r, delayMs)
        }
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

    private fun suppressChimeBriefly() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                handler.postDelayed({
                    try {
                        am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                        am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                    } catch (_: Exception) {}
                }, 220L)
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun startWakeRecognizer() {
        if (!isServiceRunning || !isWakeActive || isRecognizerListening || TitanLiveSessionManager.isSessionActive()) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        if (!TitanAudioSessionManager.requestMicOwnership("TitanWakeWordService")) return

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

            if (!preferOnDevice) suppressChimeBriefly()
            isRecognizerListening = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Wake recognizer start warning: ${e.message}")
            isRecognizerListening = false
            destroyRecognizer()
            preferOnDevice = false
            scheduleStartListening(1200L)
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
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            val handled = if (text.isNotBlank()) checkWakeMatch(text, isFinal = true) else false
            if (!handled && isServiceRunning && isWakeActive && !TitanLiveSessionManager.isSessionActive()) {
                scheduleStartListening(600L)
            }
        }

        override fun onError(error: Int) {
            isRecognizerListening = false
            if (!isServiceRunning || !isWakeActive || TitanLiveSessionManager.isSessionActive()) return
            if (preferOnDevice && (error == 12 || error == 13 || error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_AUDIO)) {
                preferOnDevice = false
                destroyRecognizer()
                scheduleStartListening(400L)
                return
            }
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                destroyRecognizer()
            }
            scheduleStartListening(900L)
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

        // Wait for final result if the user is still speaking a full one-shot command
        val trailing = lower.substringAfter(matchedKw).trim(' ', ',', '.', '?', '!')
        if (!isFinal && trailing.length < 3) return false

        lastWakeTriggeredAtMs = now
        pauseListening()
        wakeScreenBriefly()
        playWakeChime()

        if (trailing.length >= 4) {
            // One-Shot Command Execution
            Log.i(TAG, "One-Shot Wake Command ('$matchedKw' -> '$trailing')")
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
                        .trim()
                        .ifBlank { TitanAssistantConfig.getAwakenGreeting(this@TitanWakeWordService) }

                    handler.post {
                        TitanInstantTtsEngine.speakFullResponse(this@TitanWakeWordService, spoken) {
                            resumeListeningIfIdle()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "One-shot wake execution error: ${e.message}")
                    resumeListeningIfIdle()
                }
            }
        } else {
            // Wake Word Only -> Launch Live Voice Call Immediately
            Log.i(TAG, "Wake Word '$matchedKw' matched -> Opening Live Voice Call!")
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("EXTRA_START_LIVE_VOICE", true)
            }
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open MainActivity from wake word: ${e.message}")
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
        try {
            if (cpuWakeLock?.isHeld == true) cpuWakeLock?.release()
        } catch (_: Exception) {}
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Titan Always-On Wake Word",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous hands-free wake word listening"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("EXTRA_START_LIVE_VOICE", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
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
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIF_ID, createNotification())
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
        destroyRecognizer()
        TitanAudioSessionManager.releaseMicOwnership("TitanWakeWordService")
        releaseCpuWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
