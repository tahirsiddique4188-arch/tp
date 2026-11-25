package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.AssistantActivity
import com.example.ui.components.orb.Titan3DOrbFloatingCanvasView
import com.example.ui.components.orb.TitanOrbCatalog
import com.example.voice.TitanAssistantConfig

class TitanFloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: FrameLayout? = null
    private var orbCanvasView: Titan3DOrbFloatingCanvasView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastClickTime = 0L
    private var pendingSingleClick: Runnable? = null

    companion object {
        private const val CHANNEL_ID = "TitanFloatingBubbleChannel"
        private const val NOTIF_ID = 1001
        var isRunning = false
            private set
        var activeService: TitanFloatingBubbleService? = null
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        activeService = this
        createNotificationChannel()
        startForeground(NOTIF_ID, createNotification())
        setup3DHolographicOrb()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Titan 3D Holographic Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Titan 3D Holographic Orb Active")
            .setContentText("Tap once for Live Voice • Double-tap to open Titan IDE")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun setup3DHolographicOrb() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w("TitanFloatingBubble", "Cannot draw overlays: Permission not granted")
            Toast.makeText(this, "⚠️ Please grant Screen Overlay permission to show 3D Orb", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val displayMetrics = resources.displayMetrics
        val orbSize = (82 * displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            orbSize,
            orbSize,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = displayMetrics.widthPixels - orbSize - 32
            y = displayMetrics.heightPixels / 3
        }

        val orbId = TitanAssistantConfig.getSelectedOrbId(this)
        val orbSkin = TitanOrbCatalog.getOrbById(orbId)

        val frame = FrameLayout(this)
        val canvasView = Titan3DOrbFloatingCanvasView(this).apply {
            setSkin(orbSkin)
        }
        orbCanvasView = canvasView

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
        }
        frame.addView(canvasView, lp)
        floatingView = frame

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = (initialX + dx).toInt()
                        params.y = (initialY + dy).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 350) {
                            // Double tap detected: Open Titan IDE Studio
                            pendingSingleClick?.let { handler.removeCallbacks(it) }
                            pendingSingleClick = null
                            val intent = Intent(this, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(intent)
                            Toast.makeText(this, "Opening Titan IDE Studio...", Toast.LENGTH_SHORT).show()
                            lastClickTime = 0L
                        } else {
                            // Single tap: Launch Live Voice Assistant
                            lastClickTime = currentTime
                            val runnable = Runnable {
                                val voiceIntent = Intent(this, AssistantActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(voiceIntent)
                                Toast.makeText(this, "Titan 3D Live Voice Listening...", Toast.LENGTH_SHORT).show()
                            }
                            pendingSingleClick = runnable
                            handler.postDelayed(runnable, 320)
                        }
                    } else {
                        // Snap smoothly to left or right screen edge
                        val screenWidth = displayMetrics.widthPixels
                        val targetX = if (params.x + orbSize / 2 < screenWidth / 2) 20 else screenWidth - orbSize - 20
                        params.x = targetX
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(floatingView, params)
            Log.d("TitanFloatingBubble", "Successfully added 3D Holographic Orb overlay to screen")
        } catch (e: Exception) {
            Log.e("TitanFloatingBubble", "Error adding floating 3D orb: ${e.message}")
        }
    }

    fun updateOrbSkin() {
        handler.post {
            try {
                val orbId = TitanAssistantConfig.getSelectedOrbId(this)
                val orbSkin = TitanOrbCatalog.getOrbById(orbId)
                orbCanvasView?.setSkin(orbSkin)
            } catch (e: Exception) {
                Log.e("TitanFloatingBubble", "Error updating 3D orb skin: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        activeService = null
        pendingSingleClick?.let { handler.removeCallbacks(it) }
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
