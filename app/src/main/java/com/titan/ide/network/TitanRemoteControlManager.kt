package com.titan.ide.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemoteControlState(
    val isAppEnabled: Boolean = true,
    val noticeMessage: String = "",
    val blockMessage: String = "Titan IDE is temporarily under maintenance by Developer Tahir. Please check back soon."
)

/**
 * TitanRemoteControlManager — Optional Remote On/Off Kill-Switch & Broadcast Notice for Developer Tahir
 *
 * Host a tiny JSON file anywhere (e.g. GitHub Gist Raw or Firebase Realtime DB):
 *   {"app_enabled": true, "notice": "Welcome to Titan IDE!", "block_message": "Maintenance in progress."}
 * Paste your JSON URL into REMOTE_CONFIG_URL below. When blank or offline, fails OPEN so users are never blocked.
 */
object TitanRemoteControlManager {

    private const val TAG = "TitanRemoteControl"
    // Set your own GitHub Raw / Firebase JSON URL here when you want remote On/Off control:
    private const val REMOTE_CONFIG_URL = ""

    private val _state = MutableStateFlow(RemoteControlState())
    val state: StateFlow<RemoteControlState> = _state.asStateFlow()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun syncRemoteConfig(context: Context) = withContext(Dispatchers.IO) {
        if (REMOTE_CONFIG_URL.isBlank()) return@withContext
        try {
            val req = Request.Builder().url(REMOTE_CONFIG_URL).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string().orEmpty().trim()
                if (body.startsWith("{")) {
                    val obj = JSONObject(body)
                    val enabled = obj.optBoolean("app_enabled", true)
                    val notice = obj.optString("notice", "")
                    val blockMsg = obj.optString("block_message", _state.value.blockMessage)
                    _state.value = RemoteControlState(enabled, notice, blockMsg)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Remote config check skipped (offline/fail-open): ${e.message}")
        }
    }
}
