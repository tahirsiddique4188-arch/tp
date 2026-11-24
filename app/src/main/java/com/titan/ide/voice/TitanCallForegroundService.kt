package com.titan.ide.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * TitanCallForegroundService — Claude-Style Ongoing Phone Call Foreground Service
 *
 * Implements:
 * 1. Non-dismissible Ongoing Call Notification with live chronometer timer (00:00).
 * 2. Runs continuously even if user locks screen or switches apps.
 * 3. Enforces AudioManager.MODE_IN_COMMUNICATION & Speakerphone during call.
 * 4. Provides quick "End Call" notification action.
 * 5. Cleans up audio mode and releases partial wake lock on termination.
 *
 * Developer: Developer Tahir (@Tahirsiddik)
 */
class TitanCallForegroundService : Service() {

    companion object {
        private const val TAG = "TitanCallService"
        const val CHANNEL_ID = "titan_ongoing_phone_call_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START_CALL = "com.titan.action.START_CALL"
        const val ACTION_END_CALL = "com.titan.action.END_CALL"

        @Volatile
        var isCallServiceRunning: Boolean = false
            private set

        fun startCall(context: Context) {
            val intent = Intent(context, TitanCallForegroundService::class.java).apply {
                action = ACTION_START_CALL
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting TitanCallForegroundService: ${e.message}")
            }
        }

        fun endCall(context: Context) {
            val intent = Intent(context, TitanCallForegroundService::class.java).apply {
                action = ACTION_END_CALL
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error ending TitanCallForegroundService: ${e.message}")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Titan:OngoingCallWakeLock")?.apply {
            setReferenceCounted(false)
            try {
                acquire(3 * 60 * 60 * 1000L) // 3 hours safeguard
            } catch (_: Exception) {}
        }

        // Set audio routing to Phone Call mode
        setCallAudioMode(true)
        isCallServiceRunning = true
        Log.i(TAG, "TitanCallForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_END_CALL -> {
                terminateCallService()
                return START_NOT_STICKY
            }
            ACTION_START_CALL -> {
                val notification = buildOngoingCallNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            else -> {
                val notification = buildOngoingCallNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    private fun buildOngoingCallNotification(): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endCallIntent = Intent(this, TitanCallForegroundService::class.java).apply {
            action = ACTION_END_CALL
        }
        val endCallPendingIntent = PendingIntent.getService(
            this,
            1,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Titan")
            .setContentText("On-going call")
            .setSmallIcon(R.drawable.ic_titan_logo)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_titan_logo))
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Call", endCallPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Titan Ongoing Call",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live call status and timer for Titan ongoing phone call"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun setCallAudioMode(inCall: Boolean) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (inCall) {
                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager?.isSpeakerphoneOn = true
            } else {
                audioManager?.isSpeakerphoneOn = false
                audioManager?.mode = AudioManager.MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio mode adjustment error: ${e.message}")
        }
    }

    private fun terminateCallService() {
        if (!isCallServiceRunning) return
        isCallServiceRunning = false
        TitanLiveCallEngine.endLiveCall()
        setCallAudioMode(false)
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "TitanCallForegroundService terminated cleanly")
    }

    override fun onDestroy() {
        terminateCallService()
        super.onDestroy()
    }
}
