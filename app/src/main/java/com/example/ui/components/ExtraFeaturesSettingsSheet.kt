package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ModelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraFeaturesSettingsSheet(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    selectedModel: ModelInfo,
    onOpenModelSelector: () -> Unit,
    onOpenCodeStudio: () -> Unit,
    onOpenBroadcastStudio: () -> Unit,
    onOpenAutomationDevice: () -> Unit,
    onOpenVoiceEngine: () -> Unit,
    onOpenMemoryVault: () -> Unit,
    onOpenApiKeyManager: () -> Unit,
    onNewChatClick: () -> Unit,
    isSearchGroundingEnabled: Boolean,
    onToggleSearchGrounding: () -> Unit,
    isWakeWordActive: Boolean = false,
    onToggleWakeWord: () -> Unit = {},
    onSetDefaultAssistant: () -> Unit = {},
    onOpenSwarmRoom: () -> Unit = {},
    onOpenFileExplorer: () -> Unit = {},
    onOpenProjectRefactor: () -> Unit = {},
    onOpenThemes: () -> Unit = {},
    onOpenNetworkShield: () -> Unit = {},
    onOpenVoiceSecurity: () -> Unit = {},
    onOpenAppBuilderStudio: () -> Unit = {},
    onOpenDeveloperHub: () -> Unit = {},
    onOpenVideoStudio: () -> Unit = {},
    wakeWordSensitivity: Float = 0.5f,
    onSensitivityChange: (Float) -> Unit = {},
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var showLanguageAndNameSetup by remember { mutableStateOf(false) }
    val currentLangLabel = remember(showLanguageAndNameSetup) {
        com.example.voice.TitanAssistantConfig.getSelectedLanguage(ctx).displayName
    }
    val currentBotName = remember(showLanguageAndNameSetup) {
        com.example.voice.TitanAssistantConfig.getAssistantName(ctx)
    }

    if (showLanguageAndNameSetup) {
        Dialog(
            onDismissRequest = { showLanguageAndNameSetup = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            TitanOnboardingScreen(
                isEditMode = true,
                onCompleted = { showLanguageAndNameSetup = false }
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Settings",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 0. Language & Custom Assistant Name
                item {
                    SettingsActionRow(
                        title = "Language & Name ($currentLangLabel • $currentBotName)",
                        actionText = "EDIT",
                        icon = Icons.Default.Language,
                        tint = Color(0xFF10B981),
                        onClick = { showLanguageAndNameSetup = true }
                    )
                }

                // 1. Gemini Session (PSID / 1-Tap Sign-In)
                item {
                    SettingsActionRow(
                        title = "Gemini Session",
                        actionText = "OPEN",
                        icon = Icons.Default.Key,
                        tint = Color(0xFFF59E0B),
                        onClick = {
                            onDismiss()
                            onOpenApiKeyManager()
                        }
                    )
                }

                // 2. Model Selector
                item {
                    SettingsActionRow(
                        title = "AI Model (${selectedModel.name})",
                        actionText = "CHANGE",
                        icon = Icons.Default.AutoAwesome,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            onDismiss()
                            onOpenModelSelector()
                        }
                    )
                }

                // 3. Themes
                item {
                    SettingsActionRow(
                        title = "Themes",
                        actionText = "CHANGE",
                        icon = Icons.Default.Palette,
                        tint = Color(0xFF6366F1),
                        onClick = {
                            onDismiss()
                            onOpenThemes()
                        }
                    )
                }

                // 4. Live Web Search Toggle
                item {
                    SettingsToggleRow(
                        title = "Live Web Search",
                        icon = Icons.Default.TravelExplore,
                        tint = Color(0xFF0EA5E9),
                        checked = isSearchGroundingEnabled,
                        onCheckedChange = { onToggleSearchGrounding() }
                    )
                }

                // 5. Hands-Free Wake Word Toggle
                item {
                    SettingsToggleRow(
                        title = "Hands-Free Wake Word",
                        icon = Icons.Default.Mic,
                        tint = Color(0xFF10B981),
                        checked = isWakeWordActive,
                        onCheckedChange = { onToggleWakeWord() }
                    )
                }

                // 6. Wake Sensitivity Slider (only compact, clean)
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Hearing,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Wake Sensitivity",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                Text(
                                    text = "${(wakeWordSensitivity * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = wakeWordSensitivity,
                                onValueChange = onSensitivityChange,
                                valueRange = 0.1f..1.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 7. Memory Vault
                item {
                    SettingsActionRow(
                        title = "Memory Vault",
                        actionText = "OPEN",
                        icon = Icons.Default.Storage,
                        tint = Color(0xFFEC4899),
                        onClick = {
                            onDismiss()
                            onOpenMemoryVault()
                        }
                    )
                }

                // 8. Default Assistant
                item {
                    SettingsActionRow(
                        title = "Default Assistant",
                        actionText = "SET",
                        icon = Icons.Default.Assistant,
                        tint = Color(0xFF8B5CF6),
                        onClick = { onSetDefaultAssistant() }
                    )
                }

                // 9. In-App Update Checker
                item {
                    SettingsActionRow(
                        title = "App Update (v${com.example.BuildConfig.VERSION_NAME})",
                        actionText = "CHECK",
                        icon = Icons.Default.SystemUpdate,
                        tint = Color(0xFF0EA5E9),
                        onClick = {
                            onDismiss()
                            com.titan.ide.update.TitanInAppUpdater.checkForUpdates(ctx, manual = true)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    actionText: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
            }
            Text(
                text = actionText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = tint
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
