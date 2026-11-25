package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.services.TitanAccessibilityService
import com.example.services.TitanFloatingBubbleService
import com.example.ui.components.orb.OrbCategory
import com.example.ui.components.orb.OrbEmotionState
import com.example.ui.components.orb.TitanDynamicOrbRenderer
import com.example.ui.components.orb.TitanOrbCatalog
import com.example.ui.components.orb.TitanOrbSkin
import com.example.ui.theme.LocalTitanTheme
import com.example.voice.TitanAssistantConfig
import com.example.voice.TitanWakeWordService
import com.titan.ide.voice.TitanLiveSessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationDeviceSheet(
    isAccessibilityEnabled: Boolean,
    isFloatingBubbleActive: Boolean,
    onToggleAccessibilityService: () -> Unit,
    onToggleFloatingBubble: () -> Unit,
    onTriggerGoHome: () -> Unit,
    onTriggerSwipeUp: () -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onScanScreenText: () -> Unit,
    onSearchPlayStore: (String) -> Unit,
    scannedTextResult: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalTitanTheme.current

    var assistantNameInput by remember { mutableStateOf(TitanAssistantConfig.getAssistantName(context)) }
    var isAssistantModeActive by remember { mutableStateOf(TitanAssistantConfig.isAssistantModeActive(context)) }
    var idleMinutes by remember { mutableStateOf(TitanAssistantConfig.getIdleTimeoutMs(context) / 60000) }
    var permissionRefreshKey by remember { mutableStateOf(0) }
    var selectedOrbId by remember { mutableStateOf(TitanAssistantConfig.getSelectedOrbId(context)) }
    var selectedCategory by remember { mutableStateOf(OrbCategory.ALL) }
    var testEmotionState by remember { mutableStateOf(OrbEmotionState.IDLE) }

    // Multi-permission runtime launcher
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionRefreshKey++
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(context, "✅ All runtime permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "⚠️ Some permissions were not granted.", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission checking helper
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    val isMicrophoneGranted = remember(permissionRefreshKey) { isPermissionGranted(Manifest.permission.RECORD_AUDIO) }
    val isCameraGranted = remember(permissionRefreshKey) { isPermissionGranted(Manifest.permission.CAMERA) }
    val isContactsGranted = remember(permissionRefreshKey) { isPermissionGranted(Manifest.permission.READ_CONTACTS) }
    val isCallGranted = remember(permissionRefreshKey) { isPermissionGranted(Manifest.permission.CALL_PHONE) }
    val isOverlayGranted = remember(permissionRefreshKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
    }
    val isAccessibilityActive = remember(permissionRefreshKey) {
        TitanAccessibilityService.isServiceRunning()
    }
    val isBatteryOptimizedIgnored = remember(permissionRefreshKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = theme.background,
            contentColor = theme.textPrimary
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981).copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Assistant & Orbs",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                                Text(
                                    text = "Phone automation & 3D AI Orbs",
                                    fontSize = 11.5.sp,
                                    color = theme.textSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = theme.textSecondary
                            )
                        }
                    }
                }

                // 1. Dynamic Autonomous Assistant Mode Switch
                item {
                    val isDynamicAgentActive by com.titan.ide.agent.TitanDynamicAgentLoop.isAssistantModeActive.collectAsState()
                    val dynamicAgentLog by com.titan.ide.agent.TitanDynamicAgentLoop.statusLog.collectAsState()

                    Surface(
                        color = if (isDynamicAgentActive) Color(0xFF0F291E) else theme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isDynamicAgentActive) Color(0xFF10B981) else theme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (isDynamicAgentActive) Color(0xFF10B981) else Color(0xFFEF4444))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Autonomous Assistant",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDynamicAgentActive) Color(0xFF34D399) else theme.textPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Live screen & phone control",
                                        fontSize = 11.5.sp,
                                        color = theme.textSecondary
                                    )
                                }

                                Switch(
                                    checked = isDynamicAgentActive,
                                    onCheckedChange = {
                                        com.titan.ide.agent.TitanDynamicAgentLoop.toggleAssistantMode(context)
                                    }
                                )
                            }

                            if (dynamicAgentLog.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = theme.background.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = dynamicAgentLog,
                                        fontSize = 11.5.sp,
                                        color = if (isDynamicAgentActive) Color(0xFF7DD3FC) else theme.textSecondary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Master Background Wake-Word Switch
                item {
                    Surface(
                        color = if (isAssistantModeActive) Color(0xFF0F291E) else theme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isAssistantModeActive) Color(0xFF10B981).copy(alpha = 0.6f) else theme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isAssistantModeActive) Color(0xFF10B981) else Color(0xFFEF4444))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Wake Word Listening",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAssistantModeActive) Color(0xFF34D399) else theme.textPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Call '$assistantNameInput' hands-free",
                                    fontSize = 11.5.sp,
                                    color = theme.textSecondary
                                )
                            }

                            Switch(
                                checked = isAssistantModeActive,
                                onCheckedChange = { active ->
                                    isAssistantModeActive = active
                                    TitanAssistantConfig.setAssistantModeActive(context, active)
                                    if (active) {
                                        val startIntent = Intent(context, TitanWakeWordService::class.java).apply {
                                            action = TitanWakeWordService.ACTION_START
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(startIntent)
                                        } else {
                                            context.startService(startIntent)
                                        }
                                        Toast.makeText(context, "$assistantNameInput is listening", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val stopIntent = Intent(context, TitanWakeWordService::class.java).apply {
                                            action = TitanWakeWordService.ACTION_STOP
                                        }
                                        context.stopService(stopIntent)
                                        Toast.makeText(context, "Assistant mode paused", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                // 2. Custom Assistant Name Editor
                item {
                    Surface(
                        color = theme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, theme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Assistant Name",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = assistantNameInput,
                                    onValueChange = { assistantNameInput = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    placeholder = { Text("e.g. Titan, Jarvis") },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = theme.outline
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val clean = assistantNameInput.trim().ifBlank { "Titan" }
                                        TitanAssistantConfig.setAssistantName(context, clean)
                                        TitanWakeWordService.restartWithNewName(context)
                                        Toast.makeText(context, "Saved: '$clean'", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                                ) {
                                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = theme.background.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Say: 'Hey $assistantNameInput'",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF7DD3FC),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }

                // 3. 3D Floating Holographic Orb Control (Screen Overlay)
                item {
                    val activeSkin = remember(selectedOrbId) { TitanOrbCatalog.getOrbById(selectedOrbId) }
                    Surface(
                        color = if (isFloatingBubbleActive) Color(0xFF0C1929) else theme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, if (isFloatingBubbleActive) Color(0xFF00F0FF) else theme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isFloatingBubbleActive) Color(0xFF00F0FF).copy(alpha = 0.2f) else theme.background),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "✨", fontSize = 18.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "3D Floating Orb on Screen",
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFloatingBubbleActive) Color(0xFF38BDF8) else theme.textPrimary
                                        )
                                        Text(
                                            text = if (isFloatingBubbleActive) "● ACTIVE OVERLAY ON PHONE SCREEN" else "○ Inactive • Floating assistant",
                                            fontSize = 11.sp,
                                            fontWeight = if (isFloatingBubbleActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isFloatingBubbleActive) Color(0xFF10B981) else theme.textSecondary
                                        )
                                    }
                                }

                                Switch(
                                    checked = isFloatingBubbleActive,
                                    onCheckedChange = { onToggleFloatingBubble() }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (!isOverlayGranted) {
                                Surface(
                                    color = Color(0xFF451A03),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "⚠️ Screen Overlay Permission Required",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFDE68A)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Overlay permission required for floating orb.",
                                            fontSize = 11.sp,
                                            color = Color(0xFFFEF3C7)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    val intent = Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:${context.packageName}")
                                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                    context.startActivity(intent)
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("🔓 Grant Screen Overlay Permission", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onToggleFloatingBubble,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFloatingBubbleActive) Color(0xFFEF4444) else activeSkin.primaryColor
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isFloatingBubbleActive) "🛑 Stop Floating 3D Orb" else "✨ Launch 3D Floating Orb on Screen",
                                        color = if (isFloatingBubbleActive) Color.White else Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = theme.background.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Tap: Live Voice • Double-tap: Open Studio",
                                        fontSize = 10.5.sp,
                                        color = theme.textSecondary
                                    )
                                    Text(
                                        text = "",
                                        fontSize = 10.5.sp,
                                        color = theme.textSecondary
                                    )
                                    Text(
                                        text = "",
                                        fontSize = 10.5.sp,
                                        color = theme.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. 3D Holographic Orb Studio (Interactive 3D Orbs)
                item {
                    val activeSkin = remember(selectedOrbId) { TitanOrbCatalog.getOrbById(selectedOrbId) }

                    Surface(
                        color = theme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, activeSkin.primaryColor.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Section Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(activeSkin.primaryColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = activeSkin.emoji, fontSize = 20.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "3D Holographic AI Orbs",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textPrimary
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = activeSkin.primaryColor.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, activeSkin.primaryColor)
                                ) {
                                    Text(
                                        text = "${activeSkin.emoji} ${activeSkin.name}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeSkin.primaryColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Big Interactive Preview Card
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = theme.background.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, activeSkin.primaryColor.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Animated Preview of Selected Skin
                                    TitanDynamicOrbRenderer(
                                        skinId = selectedOrbId,
                                        emotionState = testEmotionState,
                                        size = 120.dp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "${activeSkin.emoji} ${activeSkin.name}",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeSkin.primaryColor
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = activeSkin.description,
                                        fontSize = 11.sp,
                                        color = theme.textSecondary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Emotion Tester Label
                                    Text(
                                        text = "Emotion Preview:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = theme.textSecondary
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Interactive Emotion State Test Buttons (Scrollable Row)
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        val emotionList = listOf(
                                            Pair("😊 Idle", OrbEmotionState.IDLE),
                                            Pair("👀 Look", OrbEmotionState.LOOKING_AROUND),
                                            Pair("🎙️ Listen", OrbEmotionState.LISTENING),
                                            Pair("🧠 Think", OrbEmotionState.THINKING),
                                            Pair("🗣️ Speak", OrbEmotionState.SPEAKING),
                                            Pair("⚙️ Work", OrbEmotionState.WORKING),
                                            Pair("😴 Sleep", OrbEmotionState.SLEEPING)
                                        )

                                        items(emotionList) { (label, emotion) ->
                                            val isCurrent = (testEmotionState == emotion)
                                            Surface(
                                                onClick = { testEmotionState = emotion },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isCurrent) activeSkin.primaryColor else theme.surface,
                                                border = BorderStroke(1.dp, if (isCurrent) activeSkin.primaryColor else theme.outline)
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrent) Color.Black else theme.textPrimary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Category Filter Chips
                            Text(
                                text = "Category:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textPrimary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(OrbCategory.values()) { category ->
                                    val isSelected = (selectedCategory == category)
                                    Surface(
                                        onClick = { selectedCategory = category },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFF38BDF8) else theme.background,
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else theme.outline)
                                    ) {
                                        Text(
                                            text = category.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.Black else theme.textPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2-Column Grid of 25 Orbs
                            val filteredOrbs = remember(selectedCategory) {
                                TitanOrbCatalog.getOrbsByCategory(selectedCategory)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                filteredOrbs.chunked(2).forEach { rowOrbs ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowOrbs.forEach { skin ->
                                            val isThisSelected = (selectedOrbId == skin.id)
                                            Surface(
                                                onClick = {
                                                    selectedOrbId = skin.id
                                                    TitanAssistantConfig.setSelectedOrbId(context, skin.id)
                                                    TitanFloatingBubbleService.activeService?.updateOrbSkin()
                                                    Toast.makeText(context, "✅ '${skin.name}' orb selected!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isThisSelected) skin.primaryColor.copy(alpha = 0.12f) else theme.background,
                                                border = BorderStroke(
                                                    if (isThisSelected) 1.5.dp else 1.dp,
                                                    if (isThisSelected) skin.primaryColor else theme.outline
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    // Mini Animated Orb Preview
                                                    TitanDynamicOrbRenderer(
                                                        skinId = skin.id,
                                                        emotionState = if (isThisSelected) testEmotionState else OrbEmotionState.IDLE,
                                                        size = 64.dp
                                                    )

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    Text(
                                                        text = if (skin.emoji.isNotBlank()) "${skin.emoji} ${skin.name}" else skin.name,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isThisSelected) skin.primaryColor else theme.textPrimary,
                                                        maxLines = 1
                                                    )



                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (isThisSelected) skin.primaryColor else theme.surface,
                                                        border = BorderStroke(1.dp, if (isThisSelected) skin.primaryColor else theme.outline)
                                                    ) {
                                                        Text(
                                                            text = if (isThisSelected) "✓ Active" else "Select",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isThisSelected) Color.Black else theme.textSecondary,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (rowOrbs.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Permissions Dashboard (A to Z Permissions Checklist)
                item {
                    Surface(
                        color = theme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, theme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFFA855F7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Permissions",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.textPrimary
                                    )
                                }

                                Button(
                                    onClick = {
                                        val reqList = mutableListOf(
                                            Manifest.permission.RECORD_AUDIO,
                                            Manifest.permission.CAMERA,
                                            Manifest.permission.READ_CONTACTS,
                                            Manifest.permission.CALL_PHONE
                                        )
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            reqList.add(Manifest.permission.POST_NOTIFICATIONS)
                                            reqList.add(Manifest.permission.READ_MEDIA_IMAGES)
                                        } else {
                                            reqList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        }
                                        multiplePermissionLauncher.launch(reqList.toTypedArray())
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7))
                                ) {
                                    Text("Grant All", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Required for phone automation:",
                                fontSize = 11.5.sp,
                                color = theme.textSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Permission Rows
                            PermissionStatusRow(
                                title = "Accessibility Service",
                                desc = "Screen control",
                                isGranted = isAccessibilityActive,
                                onGrant = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            )

                            PermissionStatusRow(
                                title = "Overlay Window",
                                desc = "Display over apps",
                                isGranted = isOverlayGranted,
                                onGrant = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )

                            PermissionStatusRow(
                                title = "Microphone",
                                desc = "Voice and wake word",
                                isGranted = isMicrophoneGranted,
                                onGrant = {
                                    multiplePermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                }
                            )

                            PermissionStatusRow(
                                title = "Contacts",
                                desc = "Contact lookup",
                                isGranted = isContactsGranted,
                                onGrant = {
                                    multiplePermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                }
                            )

                            PermissionStatusRow(
                                title = "Camera and Torch",
                                desc = "Camera and torch control",
                                isGranted = isCameraGranted,
                                onGrant = {
                                    multiplePermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                }
                            )

                            PermissionStatusRow(
                                title = "Phone Calls",
                                desc = "Direct call dialer",
                                isGranted = isCallGranted,
                                onGrant = {
                                    multiplePermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                                }
                            )

                            PermissionStatusRow(
                                title = "Battery Saver Bypass",
                                desc = "Unrestricted background run",
                                isGranted = isBatteryOptimizedIgnored,
                                onGrant = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {}
                                    }
                                }
                            )
                        }
                    }
                }

                // 5. Idle Silence Sleep Watchdog (2 - 5 min)
                item {
                    Surface(
                        color = theme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, theme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sleep Timer",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sleeps after ${idleMinutes} min of silence",
                                fontSize = 11.5.sp,
                                color = theme.textSecondary
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (min in 2..5) {
                                    val isSelected = (idleMinutes.toInt() == min)
                                    Surface(
                                        onClick = {
                                            idleMinutes = min.toLong()
                                            TitanAssistantConfig.setIdleTimeoutMinutes(context, min)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFFF59E0B) else theme.background,
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFF59E0B) else theme.outline),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "$min Min",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            color = if (isSelected) Color.Black else theme.textPrimary,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Dynamic Agentic Capability Examples Card
                item {
                    Surface(
                        color = theme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, theme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFEC4899),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sample Commands",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Dynamic multi-step voice actions:",
                                fontSize = 11.5.sp,
                                color = theme.textSecondary
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            DynamicExampleItem("🎨 'Generate an image of a cybernetic cat and send to Alex'")
                            DynamicExampleItem("🎬 'Play top instrumental songs on YouTube'")
                            DynamicExampleItem("📱 'Open WhatsApp and text Sarah'")
                            DynamicExampleItem("🔍 'Summarize what is currently displayed on screen'")
                            DynamicExampleItem("🔦 'Turn on flashlight / Open camera'")
                        }
                    }
                }

                // 7. Test Live Trigger Button
                item {
                    Button(
                        onClick = {
                            onDismiss()
                            try {
                                val toneG = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                                toneG.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                            } catch (e: Exception) {}
                            TitanLiveSessionManager.activeManager?.startLiveSession()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🎙️ Test: Launch Live Assistant", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    desc: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = desc,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        if (isGranted) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF10B981).copy(alpha = 0.18f),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
            ) {
                Text(
                    text = "✓ Active",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF34D399),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        } else {
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = "Grant",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun DynamicExampleItem(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

