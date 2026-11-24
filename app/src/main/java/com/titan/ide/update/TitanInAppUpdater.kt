package com.titan.ide.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class TitanUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val forceUpdate: Boolean = false
)

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    object UpToDate : UpdateUiState()
    data class Available(val info: TitanUpdateInfo) : UpdateUiState()
    data class Downloading(val info: TitanUpdateInfo, val progress: Float) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

/**
 * TitanInAppUpdater — Zero-Uninstall OTA In-App APK Auto-Updater
 * Checks GitHub Releases `update.json` published automatically by Titan Forge v3.0,
 * downloads the permanently-signed Release APK with live progress, and triggers
 * Android's native PackageInstaller via FileProvider so user data stays 100% intact.
 */
object TitanInAppUpdater {

    private const val TAG = "TitanInAppUpdater"
    private const val PREFS_NAME = "titan_updater_prefs"
    private const val KEY_CUSTOM_URL = "custom_update_json_url"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun setCustomUpdateJsonUrl(context: Context, url: String) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CUSTOM_URL, url.trim()).apply()
    }

    private fun resolveUpdateJsonUrl(context: Context): String {
        val custom = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_URL, "")?.trim().orEmpty()
        if (custom.startsWith("http")) return custom

        val repo = BuildConfig.TITAN_UPDATE_REPO.trim()
        if (repo.contains("/")) {
            return "https://github.com/$repo/releases/download/latest/update.json"
        }
        return ""
    }

    fun checkForUpdates(context: Context, manual: Boolean = false) {
        val url = resolveUpdateJsonUrl(context)
        if (url.isBlank()) {
            if (manual) _state.value = UpdateUiState.UpToDate
            return
        }

        if (manual) _state.value = UpdateUiState.Checking
        scope.launch {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", "TitanIDE-Updater/${BuildConfig.VERSION_NAME}")
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (manual) _state.value = UpdateUiState.UpToDate
                        return@use
                    }
                    val body = resp.body?.string().orEmpty().trim()
                    if (body.isBlank()) {
                        if (manual) _state.value = UpdateUiState.UpToDate
                        return@use
                    }
                    val json = JSONObject(body)
                    val remoteCode = json.optInt("versionCode", 0)
                    val remoteName = json.optString("versionName", "New").ifBlank { "New" }
                    val apkUrl = json.optString("apkUrl", "").trim()
                    val notes = json.optString("notes", "Performance improvements and new features.")
                    val force = json.optBoolean("forceUpdate", false)

                    if (remoteCode > BuildConfig.VERSION_CODE && apkUrl.startsWith("http")) {
                        _state.value = UpdateUiState.Available(
                            TitanUpdateInfo(
                                versionCode = remoteCode,
                                versionName = remoteName,
                                apkUrl = apkUrl,
                                notes = notes,
                                forceUpdate = force
                            )
                        )
                    } else if (manual) {
                        _state.value = UpdateUiState.UpToDate
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check skipped: ${e.message}")
                if (manual) {
                    _state.value = UpdateUiState.Error("Could not check for updates. Check internet connection.")
                }
            }
        }
    }

    fun downloadAndInstall(context: Context, info: TitanUpdateInfo) {
        val appCtx = context.applicationContext
        _state.value = UpdateUiState.Downloading(info, 0f)

        scope.launch {
            try {
                val req = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "TitanIDE-Updater/${BuildConfig.VERSION_NAME}")
                    .get()
                    .build()

                val outFile = File(appCtx.cacheDir, "titan_update_${info.versionCode}.apk")
                if (outFile.exists()) outFile.delete()

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        _state.value = UpdateUiState.Error("Download failed (HTTP ${resp.code}).")
                        return@use
                    }
                    val body = resp.body ?: run {
                        _state.value = UpdateUiState.Error("Empty APK response.")
                        return@use
                    }
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L
                    var lastReportPct = -1

                    body.byteStream().use { input ->
                        FileOutputStream(outFile).use { output ->
                            val buf = ByteArray(16 * 1024)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                downloadedBytes += read
                                if (totalBytes > 0) {
                                    val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    if (pct != lastReportPct) {
                                        lastReportPct = pct
                                        _state.value = UpdateUiState.Downloading(info, pct / 100f)
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (!outFile.exists() || outFile.length() < 100_000L) {
                    _state.value = UpdateUiState.Error("Downloaded APK is incomplete.")
                    return@launch
                }

                _state.value = UpdateUiState.Idle
                triggerApkInstall(appCtx, outFile)
            } catch (e: Exception) {
                Log.e(TAG, "APK download failed", e)
                _state.value = UpdateUiState.Error("Update download failed: ${e.message}")
            }
        }
    }

    private fun triggerApkInstall(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                return
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch APK installer", e)
            _state.value = UpdateUiState.Error("Could not open installer: ${e.message}")
        }
    }

    fun dismiss() {
        _state.value = UpdateUiState.Idle
    }
}
