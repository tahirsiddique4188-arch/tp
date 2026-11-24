package com.example.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import com.titan.ide.network.TitanDirectEngine
import com.titan.ide.voice.TitanNativeVoiceEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * TitanAgentComputerUse - Universal Android OS Computer-Use & Dynamic Perception Engine
 * 
 * Unlike static pre-programmed assistants, this engine has ZERO pre-configured tasks.
 * It provides fundamental OS interaction primitives (Perceive, Click, Type, Scroll, Gesture, Launch, Generate, Share)
 * allowing Gemini Live to autonomously control ANY app, screen, or website dynamically on the fly.
 */
object TitanAgentComputerUse {

    private const val TAG = "TitanAgentComputerUse"
    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _agentActivityStatus = MutableStateFlow("Idle")
    val agentActivityStatus: StateFlow<String> = _agentActivityStatus.asStateFlow()

    private val _isAgentBusy = MutableStateFlow(false)
    val isAgentBusy: StateFlow<Boolean> = _isAgentBusy.asStateFlow()

    // Agent execution memory variables (for sharing generated media across steps)
    private val memoryVariables = mutableMapOf<String, Any>()

    data class UiElement(
        val index: Int,
        val text: String,
        val contentDesc: String,
        val viewId: String,
        val className: String,
        val isClickable: Boolean,
        val isEditable: Boolean,
        val bounds: Rect,
        val centerX: Float,
        val centerY: Float
    )

    // =========================================================================
    // 1. SCREEN PERCEPTION ENGINE (স্ক্রিন পারসেপশন ও নোড বিশ্লেষণ)
    // =========================================================================

    /**
     * Traverses the active window and extracts all visible, interactive, or text-bearing UI elements
     */
    fun scanScreenUiElements(service: AccessibilityService): List<UiElement> {
        val root = service.rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<UiElement>()
        var counter = 0

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val viewId = node.viewIdResourceName.orEmpty()
            val className = node.className?.toString().orEmpty()
            val isClickable = node.isClickable
            val isEditable = node.isEditable

            val rect = Rect()
            node.getBoundsInScreen(rect)

            // Only capture elements with dimensions on screen
            if (rect.width() > 0 && rect.height() > 0) {
                if (text.isNotEmpty() || desc.isNotEmpty() || isClickable || isEditable) {
                    elements.add(
                        UiElement(
                            index = ++counter,
                            text = text,
                            contentDesc = desc,
                            viewId = viewId,
                            className = className.substringAfterLast('.'),
                            isClickable = isClickable,
                            isEditable = isEditable,
                            bounds = rect,
                            centerX = rect.exactCenterX(),
                            centerY = rect.exactCenterY()
                        )
                    )
                }
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(root)
        return elements
    }

    /**
     * Generates a compact, structured representation of the screen for the LLM
     */
    fun getScreenPerceptionPrompt(service: AccessibilityService): String {
        val elements = scanScreenUiElements(service)
        if (elements.isEmpty()) return "Screen is blank or inaccessible."

        val sb = StringBuilder("Current Screen Elements (${elements.size} found):\n")
        for (el in elements.take(40)) {
            val label = when {
                el.text.isNotBlank() && el.contentDesc.isNotBlank() -> "\"${el.text}\" (${el.contentDesc})"
                el.text.isNotBlank() -> "\"${el.text}\""
                el.contentDesc.isNotBlank() -> "[${el.contentDesc}]"
                el.viewId.isNotBlank() -> "<${el.viewId.substringAfterLast('/')}>"
                else -> el.className
            }
            sb.append("#${el.index} ${el.className} $label [Center: (${el.centerX.toInt()}, ${el.centerY.toInt()})]")
            if (el.isClickable) sb.append(" [Clickable]")
            if (el.isEditable) sb.append(" [Editable]")
            sb.append("\n")
        }
        return sb.toString()
    }

    // =========================================================================
    // 2. UNIVERSAL GESTURE & INTERACTION PRIMITIVES (কোর ওএস অপারেশন)
    // =========================================================================

    fun tapAt(service: AccessibilityService, x: Float, y: Float, onDone: (() -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onDone?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onDone?.invoke()
            }
        }, null)
    }

    fun longPressAt(service: AccessibilityService, x: Float, y: Float, onDone: (() -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 800)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onDone?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onDone?.invoke()
            }
        }, null)
    }

    fun swipe(
        service: AccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L,
        onDone: (() -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onDone?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onDone?.invoke()
            }
        }, null)
    }

    /**
     * Smart Node Click: Searches by text, viewId, contentDescription, or index.
     * Tries accessibility node click first; if node is not directly clickable, taps its exact screen coordinates!
     */
    fun clickNodeSmart(service: AccessibilityService, target: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val cleanTarget = target.trim().lowercase()

        // 1. Check if index number provided (e.g. "#3" or "3")
        val indexNum = target.removePrefix("#").toIntOrNull()
        val elements = scanScreenUiElements(service)
        if (indexNum != null && indexNum > 0 && indexNum <= elements.size) {
            val el = elements[indexNum - 1]
            tapAt(service, el.centerX, el.centerY)
            return true
        }

        // 2. Find matching element from scanned tree
        val matchedEl = elements.find { el ->
            el.text.lowercase().contains(cleanTarget) ||
            el.contentDesc.lowercase().contains(cleanTarget) ||
            el.viewId.lowercase().contains(cleanTarget)
        }

        if (matchedEl != null) {
            // Tap directly at its physical screen center
            tapAt(service, matchedEl.centerX, matchedEl.centerY)
            return true
        }

        // 3. Accessibility tree search fallback
        val textNodes = root.findAccessibilityNodeInfosByText(target)
        if (!textNodes.isNullOrEmpty()) {
            for (node in textNodes) {
                if (performClickRecursive(node)) return true
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    tapAt(service, rect.exactCenterX(), rect.exactCenterY())
                    return true
                }
            }
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
     * Types text into the currently focused or first available editable input field
     */
    fun typeText(service: AccessibilityService, textToType: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditableNode(root)
        return if (focused != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } else {
            false
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    fun launchApp(context: Context, appNameOrPackage: String): Boolean {
        val lower = appNameOrPackage.trim().lowercase()
        val pm = context.packageManager

        // Known direct package mappings
        val targetPackage = when {
            lower.contains("whatsapp") -> "com.whatsapp"
            lower.contains("youtube") -> "com.google.android.youtube"
            lower.contains("chrome") -> "com.android.chrome"
            lower.contains("settings") -> "com.android.settings"
            lower.contains("camera") -> {
                val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            }
            lower.contains("gallery") || lower.contains("photos") -> "com.google.android.apps.photos"
            lower.contains("telegram") -> "org.telegram.messenger"
            lower.contains("facebook") -> "com.facebook.katana"
            lower.contains("instagram") -> "com.instagram.android"
            lower.contains("maps") -> "com.google.android.apps.maps"
            lower.contains("play store") -> "com.android.vending"
            lower.contains("calculator") -> "com.google.android.calculator"
            lower.contains("clock") || lower.contains("alarm") -> "com.google.android.deskclock"
            lower.contains(".") -> appNameOrPackage.trim()
            else -> null
        }

        if (targetPackage != null) {
            val intent = pm.getLaunchIntentForPackage(targetPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        }

        // Search installed apps by display label
        try {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.contains(lower) || lower.contains(label)) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "App search error: ${e.message}")
        }
        return false
    }

    // =========================================================================
    // 3. AUTONOMOUS AGENT PLAN EXECUTOR (ডায়নামিক এজেন্ট এক্সিকিউটর)
    // =========================================================================

    /**
     * Executes a complete dynamic agent plan:
     * [{"op": "LAUNCH", "target": "..."}, {"op": "WAIT", "ms": 1000}, {"op": "CLICK", "target": "..."}, ...]
     */
    fun executeAgentPlan(service: AccessibilityService, planJsonArray: JSONArray, onComplete: ((String) -> Unit)? = null) {
        agentScope.launch {
            _isAgentBusy.value = true
            val totalSteps = planJsonArray.length()
            val logResults = mutableListOf<String>()

            for (i in 0 until totalSteps) {
                val stepObj = planJsonArray.optJSONObject(i) ?: continue
                val op = stepObj.optString("op", stepObj.optString("action", "")).uppercase()

                _agentActivityStatus.value = "Step ${i + 1}/$totalSteps: $op"
                Log.d(TAG, "Executing Agent Op: $op -> $stepObj")

                when (op) {
                    "LAUNCH", "OPEN_APP" -> {
                        val target = stepObj.optString("target", stepObj.optString("app", ""))
                        val ok = launchApp(service, target)
                        logResults.add("LAUNCH $target: ${if (ok) "Success" else "Not found"}")
                        delay(1200L) // Wait for app window to render
                    }

                    "CLICK", "TAP" -> {
                        val target = stepObj.optString("target", stepObj.optString("text", stepObj.optString("id", "")))
                        val ok = clickNodeSmart(service, target)
                        logResults.add("CLICK '$target': ${if (ok) "Clicked" else "Node not found"}")
                        delay(600L)
                    }

                    "CLICK_COORDINATES", "TAP_COORDS" -> {
                        val metrics = service.resources.displayMetrics
                        val x = if (stepObj.has("x_percent")) {
                            metrics.widthPixels * (stepObj.optDouble("x_percent") / 100.0).toFloat()
                        } else {
                            stepObj.optDouble("x", 500.0).toFloat()
                        }
                        val y = if (stepObj.has("y_percent")) {
                            metrics.heightPixels * (stepObj.optDouble("y_percent") / 100.0).toFloat()
                        } else {
                            stepObj.optDouble("y", 500.0).toFloat()
                        }
                        tapAt(service, x, y)
                        logResults.add("TAP at ($x, $y)")
                        delay(600L)
                    }

                    "TYPE", "INPUT_TEXT" -> {
                        val text = stepObj.optString("text", stepObj.optString("content", ""))
                        val target = stepObj.optString("target", "")
                        if (target.isNotBlank()) {
                            clickNodeSmart(service, target)
                            delay(400L)
                        }
                        val ok = typeText(service, text)
                        logResults.add("TYPE '$text': ${if (ok) "Typed" else "No active input field"}")
                        delay(500L)
                    }

                    "SCROLL" -> {
                        val dir = stepObj.optString("direction", "down").lowercase()
                        val metrics = service.resources.displayMetrics
                        val cx = metrics.widthPixels / 2f
                        if (dir == "up") {
                            swipe(service, cx, metrics.heightPixels * 0.3f, cx, metrics.heightPixels * 0.8f)
                        } else {
                            swipe(service, cx, metrics.heightPixels * 0.8f, cx, metrics.heightPixels * 0.3f)
                        }
                        logResults.add("SCROLL $dir")
                        delay(600L)
                    }

                    "SWIPE" -> {
                        val sx = stepObj.optDouble("start_x", 500.0).toFloat()
                        val sy = stepObj.optDouble("start_y", 1500.0).toFloat()
                        val ex = stepObj.optDouble("end_x", 500.0).toFloat()
                        val ey = stepObj.optDouble("end_y", 500.0).toFloat()
                        val duration = stepObj.optLong("duration", 300L)
                        swipe(service, sx, sy, ex, ey, duration)
                        logResults.add("SWIPE ($sx,$sy) -> ($ex,$ey)")
                        delay(500L)
                    }

                    "KEY" -> {
                        val key = stepObj.optString("key", stepObj.optString("action", "")).uppercase()
                        when (key) {
                            "BACK" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                            "HOME" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                            "RECENTS" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                            "LOCK" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                            }
                            "NOTIFICATIONS" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                        }
                        logResults.add("KEY $key")
                        delay(500L)
                    }

                    "WAIT" -> {
                        val ms = stepObj.optLong("ms", 1000L).coerceIn(100L, 10000L)
                        delay(ms)
                        logResults.add("WAIT ${ms}ms")
                    }

                    "GENERATE_MEDIA" -> {
                        val type = stepObj.optString("type", "image").lowercase()
                        val prompt = stepObj.optString("prompt", "")
                        val varName = stepObj.optString("var", "generated_media")
                        if (prompt.isNotBlank()) {
                            try {
                                val url = if (type == "video") {
                                    TitanDirectEngine.generateVideoDirect(service, prompt)
                                } else {
                                    TitanDirectEngine.generateImageDirect(service, prompt)
                                }
                                if (url.isNotBlank()) {
                                    // Download bytes and cache locally for sharing
                                    val client = okhttp3.OkHttpClient()
                                    val req = okhttp3.Request.Builder().url(url).build()
                                    val resp = client.newCall(req).execute()
                                    val bytes = resp.body?.bytes()
                                    if (bytes != null && bytes.isNotEmpty()) {
                                        val mediaDir = File(service.cacheDir, "agent_media").apply { if (!exists()) mkdirs() }
                                        val ext = if (type == "video") ".mp4" else ".jpg"
                                        val file = File(mediaDir, "media_${System.currentTimeMillis()}$ext")
                                        file.writeBytes(bytes)
                                        memoryVariables[varName] = file
                                        logResults.add("GENERATE_MEDIA: Saved $varName (${file.absolutePath})")
                                    }
                                }
                            } catch (e: Exception) {
                                logResults.add("GENERATE_MEDIA Error: ${e.message}")
                            }
                        }
                    }

                    "SHARE_MEDIA" -> {
                        val varName = stepObj.optString("media_var", "generated_media")
                        val pkg = stepObj.optString("package", "com.whatsapp")
                        val recipient = stepObj.optString("recipient", "")
                        val file = memoryVariables[varName] as? File
                        if (file != null && file.exists()) {
                            val uri = FileProvider.getUriForFile(service, "${service.packageName}.provider", file)
                            withContext(Dispatchers.Main) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (file.name.endsWith(".mp4")) "video/mp4" else "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    if (pkg.isNotBlank()) setPackage(pkg)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                service.startActivity(shareIntent)

                                if (recipient.isNotBlank()) {
                                    delay(1400L)
                                    clickNodeSmart(service, recipient)
                                }
                            }
                            logResults.add("SHARE_MEDIA: Dispatched $varName to $pkg ($recipient)")
                        } else {
                            logResults.add("SHARE_MEDIA: Media variable '$varName' not found")
                        }
                    }

                    "OBSERVE_SCREEN" -> {
                        val summary = getScreenPerceptionPrompt(service)
                        logResults.add("OBSERVE_SCREEN: Captured ${summary.lines().size} lines")
                        val speak = stepObj.optBoolean("speak_summary", false)
                        if (speak) {
                            val shortSummary = scanScreenUiElements(service)
                                .mapNotNull { it.text.ifBlank { null } }
                                .take(6)
                                .joinToString(", ")
                            if (shortSummary.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    TitanNativeVoiceEngine.speakText(service, "On screen: $shortSummary")
                                }
                            }
                        }
                    }

                    "NOTIFY", "SPEAK" -> {
                        val msg = stepObj.optString("message", stepObj.optString("text", ""))
                        if (msg.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                TitanNativeVoiceEngine.speakText(service, msg)
                            }
                            logResults.add("NOTIFY: '$msg'")
                        }
                    }
                }
            }

            _isAgentBusy.value = false
            _agentActivityStatus.value = "Idle"
            val finalSummary = logResults.joinToString("\n")
            onComplete?.invoke(finalSummary)
        }
    }

    /**
     * Parses any response string from Gemini and executes either:
     * 1. Multi-step dynamic plan: [TITAN_AGENT: {"plan": [...]}]
     * 2. Single action: [TITAN_ACTION: {...}]
     */
    fun parseAndExecuteDynamicAgent(service: AccessibilityService, responseContent: String) {
        // 1. Check for Universal Multi-step Plan [TITAN_AGENT: {...}]
        val agentRegex = Regex("""\[TITAN_AGENT:\s*(\{[\s\S]*?\})\]""")
        val agentMatch = agentRegex.find(responseContent)
        if (agentMatch != null) {
            try {
                val json = JSONObject(agentMatch.groupValues[1])
                val plan = json.optJSONArray("plan")
                if (plan != null && plan.length() > 0) {
                    executeAgentPlan(service, plan)
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Agent plan parse error: ${e.message}")
            }
        }

        // 2. Check for single or chained [TITAN_ACTION: {...}]
        val actionRegex = Regex("""\[TITAN_ACTION:\s*(\{[\s\S]*?\})\]""")
        val matches = actionRegex.findAll(responseContent)
        val planArr = JSONArray()
        for (m in matches) {
            try {
                planArr.put(JSONObject(m.groupValues[1]))
            } catch (e: Exception) {}
        }
        if (planArr.length() > 0) {
            executeAgentPlan(service, planArr)
        }
    }
}

