package com.titan.ide.agent

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.Display
import com.example.util.TitanSecurityShield
import com.titan.ide.network.TitanDirectEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * TitanScreenVisionEngine — Real-Time Visual "Eyes" for Titan Autonomous Sub-Agents
 *
 * Uses Android 11+ (API 30) `AccessibilityService.takeScreenshot()` (no MediaProjection popup needed!)
 * to capture the exact screen frame, downscale cleanly to JPEG Base64, and inspect visual UI elements
 * (unlabeled icons, WebViews, generated images, WhatsApp/ChatGPT buttons) that Accessibility trees alone miss.
 */
object TitanScreenVisionEngine {

    private const val TAG = "TitanScreenVision"
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    /**
     * Captures a compact JPEG Base64 screenshot of the active display using AccessibilityService (API 30+).
     */
    suspend fun captureScreenJpegBase64(service: AccessibilityService): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return withTimeoutOrNull(3500L) {
            suspendCancellableCoroutine { cont ->
                try {
                    service.takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        screenshotExecutor,
                        object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                                var hardwareBitmap: Bitmap? = null
                                var softBitmap: Bitmap? = null
                                var scaledBitmap: Bitmap? = null
                                try {
                                    val buffer = screenshot.hardwareBuffer
                                    val colorSpace = screenshot.colorSpace
                                    hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                                    buffer.close()
                                    if (hardwareBitmap == null) {
                                        if (cont.isActive) cont.resume(null)
                                        return
                                    }
                                    softBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    val maxW = 640
                                    val scale = if (softBitmap.width > maxW) maxW.toFloat() / softBitmap.width else 1f
                                    scaledBitmap = if (scale < 1f) {
                                        Bitmap.createScaledBitmap(
                                            softBitmap,
                                            (softBitmap.width * scale).toInt().coerceAtLeast(1),
                                            (softBitmap.height * scale).toInt().coerceAtLeast(1),
                                            true
                                        )
                                    } else softBitmap

                                    val baos = ByteArrayOutputStream()
                                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 68, baos)
                                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                                    if (cont.isActive) cont.resume(b64)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Screenshot encode error: ${e.message}")
                                    if (cont.isActive) cont.resume(null)
                                } finally {
                                    try { if (scaledBitmap != null && scaledBitmap !== softBitmap) scaledBitmap.recycle() } catch (_: Exception) {}
                                    try { softBitmap?.recycle() } catch (_: Exception) {}
                                    try { hardwareBitmap?.recycle() } catch (_: Exception) {}
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                Log.w(TAG, "takeScreenshot failed with errorCode=$errorCode")
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "takeScreenshot call error: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    /**
     * Analyzes the live screenshot visually so the autonomous agent can SEE unlabeled icons,
     * generated images, loading spinners, and exact button positions.
     */
    suspend fun describeScreenForGoal(
        context: Context,
        jpegBase64: String,
        goal: String,
        screenWidth: Int,
        screenHeight: Int
    ): String = withContext(Dispatchers.IO) {
        val client = TitanSecurityShield.buildSecureOkHttpClient(12)
        val prompt = "You are the visual eyes of an Android phone agent (screen ${screenWidth}x${screenHeight}). " +
            "User goal: \"$goal\". Briefly describe in 2-3 concise lines: (1) what screen/app is visible, " +
            "(2) whether anything is still loading/generating, and (3) the exact visual location (top/middle/bottom, left/right, approx x,y) of the key button or icon needed next."

        // 0. Primary: 100% PSID-Native Visual Screen Eyes (Zero API Key!)
        try {
            val jpegBytes = Base64.decode(jpegBase64, Base64.DEFAULT)
            val psidVision = TitanDirectEngine.uploadImageAndAskViaPsid(context, jpegBytes, prompt)
            if (psidVision.isNotBlank()) return@withContext psidVision.take(500)
        } catch (e: Exception) {
            Log.w(TAG, "PSID vision primary notice: ${e.message}")
        }

        // 1. Official Gemini API Vision (if configured)
        val geminiKey = TitanDirectEngine.getApiKey(context, "gemini")
        if (geminiKey.startsWith("AIza")) {
            try {
                val payload = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                            put(JSONObject().put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", jpegBase64)
                            }))
                        })
                    }))
                }
                val req = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$geminiKey")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val txt = JSONObject(resp.body?.string() ?: "{}")
                            .optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")
                            ?.optJSONObject(0)?.optString("text", "")?.trim().orEmpty()
                        if (txt.isNotBlank()) return@withContext txt
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini vision notice: ${e.message}")
            }
        }

        ""
    }
}
