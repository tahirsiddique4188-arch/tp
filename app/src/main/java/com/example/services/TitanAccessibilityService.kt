package com.example.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class TitanAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TitanAccessibility"
        var instance: TitanAccessibilityService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null

        val ACTION_TAG_REGEX = Regex("\\[TITAN_ACTION:\\s*(\\{[\\s\\S]*?\\})\\]")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Titan Accessibility Automation Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Active window node inspection
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    /**
     * Intercepts and executes "[TITAN_ACTION: {...}]" command protocols embedded in AI responses.
     */
    fun parseAndExecuteActions(content: String, context: Context): List<String> {
        // Universal Autonomous Computer-Use Agent (Zero hardcoding, executes dynamic plans on the fly)
        TitanAgentComputerUse.parseAndExecuteDynamicAgent(this, content)

        val results = mutableListOf<String>()
        val matches = ACTION_TAG_REGEX.findAll(content)
        for (match in matches) {
            val jsonStr = match.groupValues[1]
            try {
                val json = JSONObject(jsonStr)
                val action = json.optString("action", json.optString("type", "")).uppercase()
                when (action) {
                    "OPEN_APP" -> {
                        val pkg = json.optString("package", json.optString("app", ""))
                        val res = openApp(pkg)
                        results.add("OPEN_APP: $res")
                    }
                    "CLOSE_APP", "GO_HOME", "HOME" -> {
                        triggerGoHome()
                        results.add("CLOSE_APP: Returned to Home Screen")
                    }
                    "GO_BACK", "BACK" -> {
                        triggerGoBack()
                        results.add("GO_BACK: Pressed Back")
                    }
                    "PLAY_YOUTUBE" -> {
                        val query = json.optString("query", json.optString("song", json.optString("video", "")))
                        val res = playYouTube(query)
                        results.add("PLAY_YOUTUBE: $res")
                    }
                    "GENERATE_IMAGE_AND_SHARE", "GENERATE_IMAGE" -> {
                        val prompt = json.optString("prompt", json.optString("query", ""))
                        val pkg = json.optString("share_to", json.optString("package", "com.whatsapp"))
                        val recipient = json.optString("recipient", json.optString("friend", ""))
                        val caption = json.optString("caption", "")
                        val res = generateImageAndShare(context, prompt, pkg, recipient, caption)
                        results.add("GENERATE_IMAGE: $res")
                    }
                    "SEND_WHATSAPP", "WHATSAPP" -> {
                        val recipient = json.optString("recipient", json.optString("contact", json.optString("name", "")))
                        val message = json.optString("message", json.optString("text", ""))
                        val res = sendWhatsAppMessage(recipient, message)
                        results.add("SEND_WHATSAPP: $res")
                    }
                    "CLICK_NODE" -> {
                        val target = json.optString("target", json.optString("text", json.optString("id", "")))
                        val res = clickNodeByTextOrId(target)
                        results.add("CLICK_NODE: $res")
                    }
                    "TYPE_TEXT" -> {
                        val text = json.optString("text", json.optString("content", ""))
                        val res = autoInsertTextIntoFocusedField(text)
                        results.add("TYPE_TEXT: ${if (res) "Inserted text" else "Active field not focused"}")
                    }
                    "SCROLL" -> {
                        val dir = json.optString("direction", "up").lowercase()
                        if (dir == "down") swipeDownScreen() else swipeUpScreen()
                        results.add("SCROLL: Scrolled $dir")
                    }
                    "READ_SCREEN" -> {
                        val text = scanScreenText()
                        results.add("READ_SCREEN: ${text.take(200)}...")
                    }
                    "TORCH" -> {
                        val enable = json.optBoolean("enable", json.optBoolean("state", true))
                        toggleFlashlight(enable)
                        results.add("TORCH: ${if (enable) "Turned ON" else "Turned OFF"}")
                    }
                    "TAKE_PHOTO" -> {
                        val front = json.optString("camera", "").lowercase().contains("front") || json.optBoolean("front", false)
                        val res = takeSilentPhoto(front)
                        results.add("TAKE_PHOTO: $res")
                    }
                    "MAKE_CALL" -> {
                        val num = json.optString("number", json.optString("phone", ""))
                        val sim = json.optInt("sim_slot", 1)
                        val res = makePhoneCall(num, sim)
                        results.add("MAKE_CALL: $res")
                    }
                    "SET_ALARM" -> {
                        val hour = json.optInt("hour", 7)
                        val minute = json.optInt("minute", 0)
                        val message = json.optString("message", "Titan Alarm")
                        val res = setAlarm(hour, minute, message)
                        results.add("SET_ALARM: $res")
                    }
                    "VOLUME" -> {
                        val level = json.optInt("level", json.optInt("volume", 80))
                        val stream = json.optString("stream", "media")
                        val res = setVolumeLevel(level, stream)
                        results.add("VOLUME: $res")
                    }
                    "SYSTEM_TOGGLE" -> {
                        val setting = json.optString("setting", json.optString("toggle", "wifi"))
                        val enable = json.optBoolean("enable", true)
                        val res = toggleSystemSetting(setting, enable)
                        results.add("SYSTEM_TOGGLE: $res")
                    }
                    "LOCK_PHONE" -> {
                        val res = lockPhone()
                        results.add("LOCK_PHONE: $res")
                    }
                    "SOS", "SOS_TRIGGER" -> {
                        val res = com.example.voice.TitanVoiceGuardian.triggerTitanSos(context)
                        results.add("SOS: $res")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing action JSON: $jsonStr, err: ${e.message}")
            }
        }
        return results
    }

    // Supported Actions Implementation

    /**
     * 1. OPEN_APP: Launch packages or app names
     */
    fun openApp(appOrPackage: String): String {
        val lower = appOrPackage.trim().lowercase()
        val targetPackage = when {
            lower.contains("youtube") -> "com.google.android.youtube"
            lower.contains("whatsapp") -> "com.whatsapp"
            lower.contains("settings") -> "com.android.settings"
            lower.contains("chrome") -> "com.android.chrome"
            lower.contains("camera") -> {
                val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(camIntent)
                return "Launched Camera app"
            }
            lower.contains("telegram") -> "org.telegram.messenger"
            lower.contains("facebook") -> "com.facebook.katana"
            lower.contains("maps") -> "com.google.android.apps.maps"
            lower.contains("play store") -> "com.android.vending"
            lower.contains("clock") || lower.contains("alarm") -> "com.google.android.deskclock"
            lower.contains(".") -> appOrPackage.trim()
            else -> null
        }

        if (targetPackage != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                return "Opened $targetPackage"
            }
        }

        // Search installed applications by display label
        try {
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                val label = packageManager.getApplicationLabel(app).toString().lowercase()
                if (label.contains(lower) || lower.contains(label)) {
                    val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        return "Opened ${app.packageName}"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching installed apps: ${e.message}")
        }

        // Fallback: search on Play Store
        openPlayStoreAndSearch(appOrPackage)
        return "Searching Play Store for $appOrPackage"
    }

    /**
     * 2. CLICK_NODE: Search node by text or ID in rootInActiveWindow and perform ACTION_CLICK
     */
    fun clickNodeByTextOrId(target: String): String {
        val rootNode = rootInActiveWindow ?: return "Screen tree unavailable"
        if (target.isBlank()) return "Empty target for click"

        // Try searching by text
        val nodesByText = rootNode.findAccessibilityNodeInfosByText(target)
        if (!nodesByText.isNullOrEmpty()) {
            for (node in nodesByText) {
                if (performClickRecursive(node)) {
                    return "Clicked node matching text: '$target'"
                }
            }
        }

        // Try searching by view ID
        if (target.contains(":id/")) {
            val nodesById = rootNode.findAccessibilityNodeInfosByViewId(target)
            if (!nodesById.isNullOrEmpty()) {
                for (node in nodesById) {
                    if (performClickRecursive(node)) {
                        return "Clicked node matching ID: '$target'"
                    }
                }
            }
        }

        // Traverse entire tree and match partial text or description
        val found = searchAndClickDeep(rootNode, target.lowercase())
        return if (found) "Clicked node matching '$target'" else "Node '$target' not found on current screen"
    }

    private fun searchAndClickDeep(node: AccessibilityNodeInfo?, query: String): Boolean {
        if (node == null) return false
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if ((text.contains(query) || desc.contains(query)) && performClickRecursive(node)) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (searchAndClickDeep(node.getChild(i), query)) return true
        }
        return false
    }

    private fun performClickRecursive(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    /**
     * 3. READ_SCREEN: Traverse screen tree, dump visible text
     */
    fun scanScreenText(): String {
        val rootNode = rootInActiveWindow ?: return "Screen context unavailable."
        val textList = mutableListOf<String>()
        extractTextFromNode(rootNode, textList)
        return textList.joinToString("\n").ifEmpty { "No text visible on screen." }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length > 1 && !list.contains(text)) {
            list.add(text)
        } else if (!desc.isNullOrEmpty() && desc.length > 1 && !list.contains(desc)) {
            list.add(desc)
        }
        for (i in 0 until node.childCount) {
            extractTextFromNode(node.getChild(i), list)
        }
    }

    /**
     * 4. TORCH: Toggle camera flash
     */
    fun toggleFlashlight(enable: Boolean) {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, enable)
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight error: ${e.message}")
        }
    }

    /**
     * 5. TAKE_PHOTO: Silently trigger Front/Back camera snap or launch camera
     */
    fun takeSilentPhoto(useFront: Boolean = false): String {
        return try {
            val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (useFront) {
                    putExtra("android.intent.extras.CAMERA_FACING", 1)
                    putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                }
            }
            startActivity(camIntent)
            "Camera opened for ${if (useFront) "Front" else "Rear"} photo capture."
        } catch (e: Exception) {
            Log.e(TAG, "Photo error: ${e.message}")
            "Camera snapshot error: ${e.message}"
        }
    }

    /**
     * PLAY_YOUTUBE: Search and play video or song on YouTube
     */
    fun playYouTube(query: String): String {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            "Playing '$query' on YouTube"
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8"))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(webIntent)
            "Opened YouTube search for '$query'"
        }
    }

    /**
     * MAKE_CALL: Open dialer or make call with SIM preference
     */
    fun makePhoneCall(number: String, simSlot: Int = 1): String {
        val cleanNumber = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleanNumber.isEmpty()) return "Invalid phone number"
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("simSlot", simSlot - 1)
            putExtra("com.android.phone.extra.slot", simSlot - 1)
        }
        startActivity(intent)
        return "Dialer opened for $cleanNumber (SIM $simSlot)"
    }

    /**
     * SET_ALARM: Set alarm on clock app
     */
    fun setAlarm(hour: Int, minute: Int, message: String): String {
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            "Alarm set for %02d:%02d ($message)".format(hour, minute)
        } catch (e: Exception) {
            "Error setting alarm: ${e.message}"
        }
    }

    /**
     * VOLUME: Adjust media, ring, or alarm stream volume
     */
    fun setVolumeLevel(levelPercent: Int, streamType: String): String {
        return try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val stream = when (streamType.lowercase()) {
                "ring", "ringer" -> android.media.AudioManager.STREAM_RING
                "alarm" -> android.media.AudioManager.STREAM_ALARM
                "notification" -> android.media.AudioManager.STREAM_NOTIFICATION
                else -> android.media.AudioManager.STREAM_MUSIC
            }
            val maxVol = audioManager.getStreamMaxVolume(stream)
            val targetVol = (maxVol * (levelPercent.coerceIn(0, 100) / 100f)).toInt()
            audioManager.setStreamVolume(stream, targetVol, android.media.AudioManager.FLAG_SHOW_UI)
            "Volume set to $levelPercent% for $streamType"
        } catch (e: Exception) {
            "Volume adjust error: ${e.message}"
        }
    }

    /**
     * SYSTEM_TOGGLE: Toggle system settings panels (Wi-Fi, Bluetooth, Airplane Mode)
     */
    fun toggleSystemSetting(setting: String, enable: Boolean): String {
        val lower = setting.lowercase()
        return try {
            val action = when {
                lower.contains("wifi") || lower.contains("wi-fi") -> android.provider.Settings.ACTION_WIFI_SETTINGS
                lower.contains("bluetooth") || lower.contains("bt") -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
                lower.contains("airplane") -> android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS
                else -> android.provider.Settings.ACTION_SETTINGS
            }
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            startActivity(intent)
            "Opened $setting settings panel"
        } catch (e: Exception) {
            "Setting toggle error: ${e.message}"
        }
    }

    /**
     * 7. LOCK_PHONE: Lock screen via GLOBAL_ACTION_LOCK_SCREEN
     */
    fun lockPhone(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val success = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            if (success) "Device locked successfully" else "Failed to lock device"
        } else {
            "Lock screen requires Android 9+"
        }
    }

    // Helper Global Actions
    fun triggerGoHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun triggerGoBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun triggerOpenNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun triggerShowRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun swipeUpScreen() {
        val path = Path()
        val displayMetrics = resources.displayMetrics
        val startX = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.8f
        val endY = displayMetrics.heightPixels * 0.2f

        path.moveTo(startX, startY)
        path.lineTo(startX, endY)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        dispatchGesture(gesture, null, null)
    }

    fun autoInsertTextIntoFocusedField(textToInsert: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditableNode(rootNode)
        return if (focusedNode != null) {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToInsert)
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } else {
            false
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val result = findEditableNode(node.getChild(i))
            if (result != null) return result
        }
        return null
    }

    fun swipeDownScreen() {
        val path = Path()
        val displayMetrics = resources.displayMetrics
        val startX = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.2f
        val endY = displayMetrics.heightPixels * 0.8f

        path.moveTo(startX, startY)
        path.lineTo(startX, endY)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        dispatchGesture(gesture, null, null)
    }

    fun scrollScreen(down: Boolean = true) {
        if (down) {
            swipeDownScreen()
        } else {
            swipeUpScreen()
        }
    }

    fun scrollScreen(direction: String) {
        if (direction.lowercase() == "up") {
            swipeUpScreen()
        } else {
            swipeDownScreen()
        }
    }

    fun generateImageAndShare(context: Context, prompt: String, targetPackage: String, recipientName: String, caption: String): String {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imgUrl = com.titan.ide.network.TitanDirectEngine.generateImageDirect(context, prompt)
                if (imgUrl.isNotBlank()) {
                    val client = okhttp3.OkHttpClient()
                    val req = okhttp3.Request.Builder().url(imgUrl).build()
                    val resp = client.newCall(req).execute()
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val mediaDir = File(context.cacheDir, "shared_media").apply { if (!exists()) mkdirs() }
                        val imgFile = File(mediaDir, "titan_artwork_${System.currentTimeMillis()}.jpg")
                        imgFile.writeBytes(bytes)

                        val contentUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            imgFile
                        )

                        withContext(Dispatchers.Main) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, contentUri)
                                if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                                if (targetPackage.isNotBlank()) setPackage(targetPackage)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(shareIntent)

                            if (recipientName.isNotBlank()) {
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    clickNodeByTextOrId(recipientName)
                                }, 1400L)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "generateImageAndShare error: ${e.message}")
            }
        }
        return "Generating artwork for '$prompt' and dispatching to $targetPackage"
    }

    fun sendWhatsAppMessage(recipient: String, message: String): String {
        val cleanNumber = recipient.filter { it.isDigit() || it == '+' }
        if (cleanNumber.length >= 7) {
            val encodedMsg = java.net.URLEncoder.encode(message, "UTF-8")
            val url = "https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMsg"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(intent)
                return "Opened WhatsApp for $cleanNumber"
            } catch (e: Exception) {}
        }

        openApp("com.whatsapp")
        if (recipient.isNotBlank()) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                clickNodeByTextOrId(recipient)
                if (message.isNotBlank()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        autoInsertTextIntoFocusedField(message)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            clickNodeByTextOrId("Send")
                        }, 500L)
                    }, 800L)
                }
            }, 1500L)
        }
        return "Dispatching WhatsApp to '$recipient': $message"
    }

    fun openPlayStoreAndSearch(query: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$query")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$query")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(webIntent)
        }
    }

    fun dumpInteractiveScreenTree(): String {
        val root = rootInActiveWindow ?: return "No active window"
        val sb = StringBuilder()
        sb.append("Package: ").append(root.packageName ?: "unknown").append("\n")
        collectUiNodes(root, sb, 0)
        return sb.toString().take(3200)
    }

    private fun collectUiNodes(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 14 || sb.length > 3000) return
        val txt = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val clickable = node.isClickable
        val editable = node.isEditable
        if (txt.isNotEmpty() || desc.isNotEmpty() || clickable || editable) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            sb.append("- ")
            if (editable) sb.append("[INPUT] ")
            else if (clickable) sb.append("[BTN] ")
            if (txt.isNotEmpty()) sb.append("text=\"").append(txt.take(40)).append("\" ")
            if (desc.isNotEmpty()) sb.append("desc=\"").append(desc.take(40)).append("\" ")
            sb.append("xy=(").append(rect.centerX()).append(",").append(rect.centerY()).append(")\n")
        }
        for (i in 0 until node.childCount) {
            collectUiNodes(node.getChild(i), sb, depth + 1)
        }
    }

    fun tapCoordinates(x: Float, y: Float): String {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val ok = dispatchGesture(gesture, null, null)
        return if (ok) "Tapped ($x,$y)" else "Tap failed"
    }

    /**
     * Autonomous Screen Hierarchy Dump & Parse
     * Uses native AccessibilityNodeInfo tree traversal to find coordinates and perform actions.
     * ZERO root or shell uiautomator dump commands.
     */
    fun dumpScreenHierarchyAndFindElement(targetText: String, onFound: ((x: Int, y: Int) -> Unit)? = null) {
        val root = rootInActiveWindow
        if (root != null) {
            val foundNode = findNodeRecursive(root, targetText)
            if (foundNode != null) {
                val rect = android.graphics.Rect()
                foundNode.getBoundsInScreen(rect)
                val cx = rect.centerX()
                val cy = rect.centerY()
                onFound?.invoke(cx, cy)
                val clicked = foundNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!clicked) {
                    tapCoordinates(cx.toFloat(), cy.toFloat())
                }
                return
            }
        }
        clickNodeByTextOrId(targetText)
    }

    private fun findNodeRecursive(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val lowerTarget = target.lowercase()
        val text = node.text?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()

        if (text.contains(lowerTarget) || desc.contains(lowerTarget) || viewId.contains(lowerTarget)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findNodeRecursive(child, target)
            if (found != null) return found
        }
        return null
    }
}

