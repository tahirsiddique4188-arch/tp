package com.titan.ide.voice

import android.content.Context
import android.util.Log

/**
 * TitanAudioSessionManager — Centralized Thread-Safe Hardware Microphone Session Manager
 *
 * Coordinates microphone ownership between:
 * - TitanLiveCallEngine (Top priority: active phone call)
 * - TitanContinuousSpeechEngine (Speech mode)
 * - TitanWhisperRecorder (Offline STT recording)
 * - TitanWakeWordService (Background passive wake-word detection)
 *
 * Ensures only ONE component captures the microphone hardware at any given time.
 * Automatically halts background wake word when live call is active, and resumes it when call ends.
 *
 * Developer: Developer Tahir (@Tahirsiddik)
 */
object TitanAudioSessionManager {

    private const val TAG = "TitanAudioSession"

    @Volatile
    var currentOwner: String? = null
        private set

    private val lock = Any()

    /**
     * Atomically requests exclusive hardware microphone ownership.
     * Forcibly pauses or cancels competing microphone sessions.
     */
    fun requestMicOwnership(requester: String): Boolean {
        synchronized(lock) {
            Log.i(TAG, "Mic ownership requested by: $requester (previous: $currentOwner)")

            if (currentOwner == requester) {
                return true
            }

            // If another component has the mic, yield it gracefully
            when (currentOwner) {
                "TitanLiveCallEngine" -> {
                    if (requester != "TitanLiveCallEngine") {
                        // Live call has supreme priority unless explicitly ended
                        Log.w(TAG, "Denying mic ownership to $requester: TitanLiveCallEngine is active")
                        return false
                    }
                }
                "TitanContinuousSpeechEngine" -> {
                    if (requester != "TitanContinuousSpeechEngine") {
                        try {
                            TitanContinuousSpeechEngine.stopListening()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error stopping TitanContinuousSpeechEngine: ${e.message}")
                        }
                    }
                }
                "TitanWhisperRecorder" -> {
                    if (requester != "TitanWhisperRecorder") {
                        try {
                            TitanWhisperRecorder.stopRecording()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error stopping TitanWhisperRecorder: ${e.message}")
                        }
                    }
                }
                "TitanWakeWordService" -> {
                    if (requester != "TitanWakeWordService") {
                        try {
                            com.example.voice.TitanWakeWordService.instance?.pauseListening()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error pausing TitanWakeWordService: ${e.message}")
                        }
                    }
                }
            }

            // Always pause passive background wake word when active engines start
            if (requester != "TitanWakeWordService") {
                try {
                    com.example.voice.TitanWakeWordService.instance?.pauseListening()
                } catch (_: Exception) {}
            }

            currentOwner = requester
            return true
        }
    }

    /**
     * Atomically releases hardware microphone ownership.
     * When top priority sessions close, resumes passive background wake word if idle.
     */
    fun releaseMicOwnership(requester: String) {
        synchronized(lock) {
            if (currentOwner == requester) {
                Log.i(TAG, "Mic ownership released by: $requester")
                currentOwner = null

                // Auto-resume background WakeWordService if no live call is running
                try {
                    com.example.voice.TitanWakeWordService.instance?.resumeListeningIfIdle()
                } catch (e: Exception) {
                    Log.w(TAG, "Error resuming TitanWakeWordService: ${e.message}")
                }
            }
        }
    }

    fun isMicInUse(): Boolean {
        synchronized(lock) {
            return currentOwner != null && currentOwner != "TitanWakeWordService"
        }
    }
}
