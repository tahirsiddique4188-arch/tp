package com.example.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.util.AssistantRoleManager
import com.example.util.TitanApiKeyManager
import com.example.util.TitanLocalServerManager
import com.example.voice.TitanWakeWordService
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.GeminiViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: GeminiViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val userMemories by viewModel.userMemories.collectAsState()
    val selectedPersona by viewModel.selectedPersona.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isSearchGroundingEnabled by viewModel.isSearchGroundingEnabled.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val wakeWordSensitivity by viewModel.wakeWordSensitivity.collectAsState()

    val htmlCode by viewModel.htmlCode.collectAsState()
    val cssCode by viewModel.cssCode.collectAsState()
    val jsCode by viewModel.jsCode.collectAsState()

    val scannedTextResult by viewModel.scannedTextResult.collectAsState()
    val isFloatingBubbleActive by viewModel.isFloatingBubbleActive.collectAsState()

    val availableModels by viewModel.availableModels.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val attachedImageUri by viewModel.attachedImageUri.collectAsState()
    val attachedFileName by viewModel.attachedFileName.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isLiveActive by viewModel.isLiveActive.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val liveSpokenText by viewModel.liveSpokenText.collectAsState()
    val liveResponseText by viewModel.liveResponseText.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val voiceSessionState by viewModel.voiceSessionState.collectAsState()
    val voiceAudioLevel by viewModel.voiceAudioLevel.collectAsState()
    val voiceSpokenText by viewModel.voiceSpokenText.collectAsState()
    val isDictating by viewModel.isDictating.collectAsState()
    val dictationSeconds by viewModel.dictationSeconds.collectAsState()
    val dictationAudioLevel by viewModel.dictationAudioLevel.collectAsState()

    // Sheet visibility toggles
    var showModelSelector by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showCodeStudio by remember { mutableStateOf(false) }
    var showCreativeStudio by remember { mutableStateOf(false) }
    var creativeStudioTab by remember { mutableIntStateOf(0) }
    var showAutomationDevice by remember { mutableStateOf(false) }
    var showVoiceEngine by remember { mutableStateOf(false) }
    var showMemoryVault by remember { mutableStateOf(false) }
    var showExtraFeaturesSheet by remember { mutableStateOf(false) }
    var showInChatVoiceSettings by remember { mutableStateOf(false) }
    var livePreviewHtml by remember { mutableStateOf<String?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var isWakeWordActive by remember { mutableStateOf(TitanWakeWordService.isServiceRunning) }
    var showThemeSelector by remember { mutableStateOf(false) }
    var showSearchHistory by remember { mutableStateOf(false) }

    // Full-Stack Mobile IDE Sheet States
    var showSwarmScreen by remember { mutableStateOf(false) }
    var showFileExplorerSheet by remember { mutableStateOf(false) }
    var showProjectRefactorSheet by remember { mutableStateOf(false) }
    var showDeveloperHubSheet by remember { mutableStateOf(false) }
    var showNetworkShieldSheet by remember { mutableStateOf(false) }
    var showVoiceSecuritySheet by remember { mutableStateOf(false) }
    var showAppBuilderStudio by remember { mutableStateOf(false) }
    var showVideoPlayerCardDialog by remember { mutableStateOf(false) }
    var activeProjectFolder by remember { mutableStateOf<File?>(null) }

    val listState = rememberLazyListState()

    val activeSession = remember(sessions, activeSessionId) {
        sessions.find { it.id == activeSessionId }
    }
    val messages = activeSession?.messages ?: emptyList()

    // Initialize Local Server & API Key Manager
    LaunchedEffect(Unit) {
        TitanLocalServerManager.startServer()
        TitanApiKeyManager.init(context)
    }

    // Auto scroll on new message and follow along during streaming
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val lastMessageContentLength = messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(lastMessageContentLength, isGenerating) {
        if (isGenerating && messages.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= messages.size - 2) {
                listState.scrollToItem(messages.size - 1)
            }
        }
    }

    // Scroll Progress Calculation
    val scrollProgress by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 1) return@derivedStateOf 0f
            
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf 0f
            
            val lastVisibleIndex = visibleItems.last().index
            val progress = (lastVisibleIndex.toFloat() + 1) / totalItems.toFloat()
            progress.coerceIn(0f, 1f)
        }
    }

    val animatedScrollProgress by animateFloatAsState(
        targetValue = if (messages.size > 5) scrollProgress else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "ScrollProgressAnimation"
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onAttachImage(it.toString()) }
    }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { viewModel.onAttachBitmap(it) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraCaptureLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission needed for Vision Coding", Toast.LENGTH_SHORT).show()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(fileUri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val fileName = fileUri.lastPathSegment ?: "document.txt"
                viewModel.onAttachFile(fileName, text.take(3000))
            } catch (e: Exception) {
                viewModel.onInputTextChange("Attached file URI: $fileUri")
            }
        }
    }

    var userInitiatedLiveVoice by remember { mutableStateOf(false) }
    var userInitiatedDictation by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && userInitiatedLiveVoice) {
            userInitiatedLiveVoice = false
            viewModel.startLiveVoiceSession()
        } else {
            userInitiatedLiveVoice = false
            if (!isGranted) {
                Toast.makeText(context, "Microphone permission is required for Gemini Live Voice", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val dictationAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && userInitiatedDictation) {
            userInitiatedDictation = false
            viewModel.toggleDictation()
        } else {
            userInitiatedDictation = false
            if (!isGranted) {
                Toast.makeText(context, "Microphone permission is required for speech-to-text dictation", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            GeminiNavigationDrawerContent(
                sessions = sessions,
                activeSessionId = activeSessionId,
                onSessionSelect = { id ->
                    viewModel.selectSession(id)
                    scope.launch { drawerState.close() }
                },
                onSessionDelete = { id -> viewModel.deleteSession(id) },
                onNewChatClick = {
                    viewModel.createNewSession()
                    scope.launch { drawerState.close() }
                },
                onOpenFileExplorer = {
                    showFileExplorerSheet = true
                    scope.launch { drawerState.close() }
                },
                onOpenThemes = {
                    showThemeSelector = true
                    scope.launch { drawerState.close() }
                },
                onOpenDeveloperHub = {
                    showDeveloperHubSheet = true
                    scope.launch { drawerState.close() }
                },
                onOpenAppBuilderStudio = {
                    showAppBuilderStudio = true
                    scope.launch { drawerState.close() }
                },
                onOpenCreativeStudio = {
                    showCreativeStudio = true
                    scope.launch { drawerState.close() }
                },
                onOpenAutomation = {
                    showAutomationDevice = true
                    scope.launch { drawerState.close() }
                },
                onOpenSwarmRoom = {
                    showSwarmScreen = true
                    scope.launch { drawerState.close() }
                },
                onOpenVoicePersona = {
                    showVoiceEngine = true
                    scope.launch { drawerState.close() }
                },
                onOpenVoiceSecurity = {
                    showVoiceSecuritySheet = true
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                GeminiTopAppBar(
                    selectedModel = selectedModel,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onModelSelectorClick = { showModelSelector = true },
                    onCodeStudioClick = { showCodeStudio = true },
                    onCreativeStudioClick = { creativeStudioTab = 0; showCreativeStudio = true },
                    onVideoStudioClick = { creativeStudioTab = 1; showCreativeStudio = true },
                    onPodcastStudioClick = { creativeStudioTab = 2; showCreativeStudio = true },
                    onLiveVoiceClick = { viewModel.toggleLiveVoiceSession() },
                    onApiHubClick = { showDeveloperHubSheet = true },
                    onAutomationClick = { showAutomationDevice = true },
                    onVoiceEngineClick = { showVoiceEngine = true },
                    onMemoryVaultClick = { showMemoryVault = true },
                    onExtraSettingsClick = { showExtraFeaturesSheet = true },
                    onHistoryClick = { showSearchHistory = true },
                    onNewChatClick = { viewModel.createNewSession() },
                    onNetworkShieldClick = { showNetworkShieldSheet = true },
                    onAppBuilderStudioClick = { showAppBuilderStudio = true },
                    onCookieVaultClick = { showAttachmentSheet = true },
                    onWarRoomClick = {
                        viewModel.onInputTextChange("Build a web app")
                        viewModel.sendMessage()
                    }
                )
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // In-Chat Voice Settings Panel (Emma, Rohan, Aarav, Ananya & Speed Multiplier)
                    AnimatedVisibility(visible = showInChatVoiceSettings) {
                        VoiceSettingsInChatPanel(
                            currentPersona = selectedPersona,
                            currentSpeed = speechRate,
                            onPersonaSelected = { persona -> viewModel.selectVoicePersona(persona) },
                            onSpeedChanged = { rate -> viewModel.setSpeechRate(rate) },
                            onTestVoiceClick = {
                                viewModel.speakAloud("Hello, I am ${selectedPersona.name}. This is a voice test at ${String.format("%.1fx", speechRate)} speed.")
                            },
                            onCloseClick = { showInChatVoiceSettings = false }
                        )
                    }

                    GeminiBottomInputBar(
                        textValue = inputText,
                        onTextChange = { viewModel.onInputTextChange(it) },
                        onSendClick = { viewModel.sendMessage() },
                        onPlusClick = { showAttachmentSheet = true },
                        onCameraClick = {
                            showImageSourceDialog = true
                        },
                        onMicClick = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.toggleLiveVoiceSession()
                            } else {
                                userInitiatedLiveVoice = true
                                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onDictationClick = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.toggleDictation()
                            } else {
                                userInitiatedDictation = true
                                dictationAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        isDictating = isDictating,
                        dictationSeconds = dictationSeconds,
                        dictationAudioLevel = dictationAudioLevel,
                        onVoiceSettingsClick = {
                            showInChatVoiceSettings = !showInChatVoiceSettings
                        },
                        isVoiceSettingsOpen = showInChatVoiceSettings,
                        onToggleSearchGrounding = {
                            viewModel.toggleSearchGrounding()
                        },
                        isSearchGroundingActive = isSearchGroundingEnabled,
                        attachedImageUri = attachedImageUri,
                        attachedFileName = attachedFileName,
                        onRemoveAttachment = { viewModel.clearAttachment() },
                        isGenerating = isGenerating
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    WelcomeView(
                        userName = userName,
                        onBuildAppClick = { showAppBuilderStudio = true },
                        onCodeStudioClick = { showCodeStudio = true },
                        onCookieVaultClick = { showAttachmentSheet = true },
                        onVideoStudioClick = { creativeStudioTab = 1; showCreativeStudio = true },
                        onSwarmRoomClick = { showSwarmScreen = true },
                        onRefactorCodeClick = { showProjectRefactorSheet = true },
                        onOpenApiHubClick = { showDeveloperHubSheet = true },
                        onQuickActionClick = { actionText -> viewModel.onQuickActionClick(actionText) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                ChatMessageBubble(
                                    message = message,
                                    onSpeakClick = { text -> viewModel.speakAloud(text) },
                                    onLiveRunClick = { html -> livePreviewHtml = html },
                                    onOpenCodeEditor = { h, c, j ->
                                        viewModel.updateCodeStudio(h, c, j)
                                        showCodeStudio = true
                                    },
                                    onSendMessage = { prompt ->
                                        viewModel.onInputTextChange(prompt)
                                        viewModel.sendMessage()
                                    }
                                )
                            }
                        }

                        // Animated Scroll Progress Bar at the top of chat area
                        if (messages.size > 5) {
                            LinearProgressIndicator(
                                progress = { animatedScrollProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .align(Alignment.TopCenter),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent,
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }
                }
            }
        }
    }

    // Modal Sheets
    if (showModelSelector) {
        ModelSelectorModal(
            currentModel = selectedModel,
            availableModels = availableModels,
            onModelSelected = { model -> viewModel.selectModel(model) },
            onDismiss = { showModelSelector = false }
        )
    }

    if (showAttachmentSheet) {
        AttachmentSheet(
            onUploadFileClick = { documentPickerLauncher.launch("*/*") },
            onGalleryClick = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            baseUrl = baseUrl,
            apiKey = apiKey,
            onUpdateBaseUrl = { viewModel.updateBaseUrl(it) },
            onUpdateApiKey = { viewModel.updateApiKey(it) },
            onDismiss = { showAttachmentSheet = false }
        )
    }

    if (showCodeStudio) {
        CodeStudioSheet(
            htmlCode = htmlCode,
            cssCode = cssCode,
            jsCode = jsCode,
            onCodeChange = { h, c, j -> viewModel.updateCodeStudio(h, c, j) },
            onAutoFixBug = { src -> viewModel.autoFixCodeBug(src) },
            onDismiss = { showCodeStudio = false }
        )
    }

    if (showCreativeStudio) {
        com.titan.ide.media.ui.TitanMediaStudio(
            initialTab = creativeStudioTab,
            onDismiss = { showCreativeStudio = false }
        )
    }

    if (showAutomationDevice) {
        AutomationDeviceSheet(
            isAccessibilityEnabled = true,
            isFloatingBubbleActive = isFloatingBubbleActive,
            onToggleAccessibilityService = {},
            onToggleFloatingBubble = { viewModel.toggleFloatingBubbleService() },
            onTriggerGoHome = { viewModel.triggerGoHome() },
            onTriggerSwipeUp = { viewModel.triggerSwipeUp() },
            onToggleFlashlight = { on -> viewModel.toggleFlashlight(on) },
            onScanScreenText = { viewModel.scanScreenText() },
            onSearchPlayStore = { q -> viewModel.searchPlayStore(q) },
            scannedTextResult = scannedTextResult,
            onDismiss = { showAutomationDevice = false }
        )
    }

    if (showVoiceEngine) {
        VoicePersonaSheet(
            currentPersona = selectedPersona,
            currentSpeed = speechRate,
            currentLanguage = selectedLanguage,
            onPersonaSelected = { persona -> viewModel.selectVoicePersona(persona) },
            onSpeedChanged = { rate -> viewModel.setSpeechRate(rate) },
            onLanguageSelected = { lang -> viewModel.setLanguage(lang) },
            onTestVoiceClick = { persona ->
                viewModel.speakAloud(
                    text = persona.sampleText,
                    voiceName = persona.id,
                    pitch = persona.pitchMultiplier
                )
            },
            onDismiss = { showVoiceEngine = false }
        )
    }

    if (showMemoryVault) {
        MemoryVaultSheet(
            memories = userMemories,
            onAddMemory = { k, v -> viewModel.addMemoryFact(k, v) },
            onDeleteMemory = { k -> viewModel.deleteMemoryFact(k) },
            onClearAllMemories = { viewModel.clearAllMemories() },
            onDismiss = { showMemoryVault = false }
        )
    }

    if (showExtraFeaturesSheet) {
        ExtraFeaturesSettingsSheet(
            isDarkTheme = isDarkTheme,
            onToggleTheme = { viewModel.toggleTheme() },
            selectedModel = selectedModel,
            onOpenModelSelector = { showModelSelector = true },
            onOpenCodeStudio = { showCodeStudio = true },
            onOpenBroadcastStudio = { showCreativeStudio = true },
            onOpenAutomationDevice = { showAutomationDevice = true },
            onOpenVoiceEngine = { showVoiceEngine = true },
            onOpenMemoryVault = { showMemoryVault = true },
            onOpenApiKeyManager = { showAttachmentSheet = true },
            onNewChatClick = { viewModel.createNewSession() },
            isSearchGroundingEnabled = isSearchGroundingEnabled,
            onToggleSearchGrounding = { viewModel.toggleSearchGrounding() },
            isWakeWordActive = isWakeWordActive,
            onToggleWakeWord = {
                val serviceIntent = Intent(context, TitanWakeWordService::class.java)
                if (isWakeWordActive) {
                    serviceIntent.action = TitanWakeWordService.ACTION_STOP
                    context.stopService(serviceIntent)
                    isWakeWordActive = false
                    Toast.makeText(context, "Titan Wake-Word Engine stopped", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        serviceIntent.action = TitanWakeWordService.ACTION_START
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        isWakeWordActive = true
                        Toast.makeText(context, "Titan Wake-Word Engine Active. Say 'Hey Titan'!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Service error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSetDefaultAssistant = {
                AssistantRoleManager.requestDefaultAssistantRole(context)
            },
            onOpenSwarmRoom = { showSwarmScreen = true },
            onOpenFileExplorer = { showFileExplorerSheet = true },
            onOpenProjectRefactor = { showProjectRefactorSheet = true },
            onOpenThemes = { showThemeSelector = true },
            onOpenNetworkShield = { showNetworkShieldSheet = true },
            onOpenVoiceSecurity = { showVoiceSecuritySheet = true },
            onOpenAppBuilderStudio = { showAppBuilderStudio = true },
            onOpenDeveloperHub = { showDeveloperHubSheet = true },
            onOpenVideoStudio = { showVideoPlayerCardDialog = true },
            wakeWordSensitivity = wakeWordSensitivity,
            onSensitivityChange = { viewModel.setWakeWordSensitivity(it) },
            onDismiss = { showExtraFeaturesSheet = false }
        )
    }

    if (showNetworkShieldSheet) {
        NetworkShieldSheet(
            onDismiss = { showNetworkShieldSheet = false }
        )
    }

    if (showVoiceSecuritySheet) {
        VoiceSecuritySheet(
            onDismiss = { showVoiceSecuritySheet = false }
        )
    }

    // Theme Selector Sheet
    if (showThemeSelector) {
        ThemeSelectorSheet(
            currentTheme = currentTheme,
            userName = userName,
            onThemeSelected = { themeId -> viewModel.setTheme(themeId) },
            onUserNameChanged = { newName -> viewModel.setUserName(name = newName) },
            onDismiss = { showThemeSelector = false }
        )
    }

    // Search History Sheet
    if (showSearchHistory) {
        SearchHistorySheet(
            history = searchHistory,
            onQueryClick = { query ->
                viewModel.onInputTextChange(query)
                viewModel.sendMessage()
            },
            onDeleteItem = { query -> viewModel.deleteSearchHistoryItem(query) },
            onClearAll = { viewModel.clearSearchHistory() },
            onDismiss = { showSearchHistory = false }
        )
    }

    // Titan Swarm Command Screen (Sub-Agents)
    if (showSwarmScreen) {
        com.titan.ide.swarm.TitanSwarmRoomScreen(
            onDismiss = { showSwarmScreen = false }
        )
    }

    // File Explorer Sheet
    if (showFileExplorerSheet) {
        TitanFileExplorerSheet(
            onLiveRunProject = { projFolder ->
                activeProjectFolder = projFolder
                livePreviewHtml = "http://127.0.0.1:8080/"
            },
            onEditWithAiPrompt = { path, code ->
                viewModel.onInputTextChange("Refactor code in file [$path]:\n```\n$code\n```\nPlease modify and fix.")
            },
            onDismiss = { showFileExplorerSheet = false }
        )
    }

    // AI Surgical Refactor Sheet
    if (showProjectRefactorSheet) {
        ProjectRefactorSheet(
            projectFolder = activeProjectFolder,
            onApplyAiRefactorPrompt = { prompt ->
                viewModel.onInputTextChange(prompt)
                viewModel.sendMessage()
            },
            onDismiss = { showProjectRefactorSheet = false }
        )
    }

    // Titan Developer API Hub & Local Gateway Sheet
    if (showDeveloperHubSheet) {
        ApiDeveloperHubScreen(
            onDismiss = { showDeveloperHubSheet = false },
            onScaffoldApi = { prompt ->
                viewModel.onQuickActionClick(prompt)
            }
        )
    }

    // Dedicated Titan App Builder Studio Screen
    if (showAppBuilderStudio) {
        Dialog(
            onDismissRequest = { showAppBuilderStudio = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.titan.ide.builder.studio.TitanAppBuilderScreen(
                onDismiss = { showAppBuilderStudio = false }
            )
        }
    }


    // Continuous Long Video & YouTube Shorts Studio Full-Screen Page
    if (showVideoPlayerCardDialog) {
        com.titan.ide.media.ui.TitanMediaStudio(
            initialTab = 1,
            onDismiss = { showVideoPlayerCardDialog = false }
        )
    }

    // Vision Coding Image Source Chooser (Live Camera vs Gallery)
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = {
                Text(
                    text = "Vision Coding & Image Input",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Capture a live photo of handwriting, whiteboard wireframe, or UI screenshot, or pick from gallery to convert directly into code.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImageSourceDialog = false
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Take Live Photo (Camera)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showImageSourceDialog = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text("Pick from Gallery", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    livePreviewHtml?.let { code ->
        LivePreviewDialog(
            htmlCode = if (code.startsWith("http")) "" else code,
            serverUrl = if (code.startsWith("http")) code else "http://127.0.0.1:8080/",
            onDismiss = { livePreviewHtml = null },
            onFixBugWithAi = { brokenCode, errorTrace ->
                viewModel.autoFixRuntimeError(brokenCode, errorTrace)
            }
        )
    }

    if (isLiveActive) {
        TitanLiveVoiceOverlay(
            isListening = isListening,
            isSpeaking = isSpeaking,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            audioLevel = audioLevel,
            liveSpokenText = liveSpokenText,
            liveResponseText = liveResponseText,
            activePersonaName = "${selectedPersona.name} • ${selectedPersona.characterTag}",
            activeModel = selectedModel,
            webSocketStatusLabel = "🟢 HTTP SSE Stream Live (Reliable)",
            onModelSelected = { model -> viewModel.selectModel(model) },
            onToggleMute = { viewModel.toggleMute() },
            onToggleSpeaker = { viewModel.toggleSpeaker() },
            onOpenVoicePicker = { showVoiceEngine = true },
            onTapToSpeak = { viewModel.restartLiveListening() },
            onSendLiveMessage = { text -> viewModel.sendLiveVoiceMessage(text) },
            onEndLiveClick = { viewModel.stopLiveVoiceSession() }
        )
    }
}

@Composable
fun WelcomeView(
    userName: String,
    onBuildAppClick: () -> Unit,
    onCodeStudioClick: () -> Unit = {},
    onCookieVaultClick: () -> Unit = {},
    onVideoStudioClick: () -> Unit = {},
    onSwarmRoomClick: () -> Unit = {},
    onRefactorCodeClick: () -> Unit,
    onOpenApiHubClick: () -> Unit = {},
    onQuickActionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dynamicGreeting = remember(userName) {
        com.example.ui.theme.TitanThemeManager.getDynamicGreeting(userName)
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 14.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        // Glowing Orb & Dynamic Greeting Header
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF2563EB),
                                Color(0xFF7C3AED),
                                Color(0xFF06B6D4)
                            )
                        )
                    )
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = dynamicGreeting,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Prompt Inspiration Chips
        Text(
            text = "QUICK PROMPTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PromptInspirationChip(label = "🎨 8K Wallpaper") {
                onQuickActionClick("Generate a high-resolution 1024x1024 AI image: A futuristic cyberpunk city at sunset, neon lights, 8k wallpaper render.")
            }
            PromptInspirationChip(label = "🎬 Sci-Fi Drone") {
                onQuickActionClick("Generate a cinematic AI video preview: Drone flight through a neon lit futuristic sci-fi metropolis at night.")
            }
            PromptInspirationChip(label = "🛠️ FastAPI CRUD") {
                onQuickActionClick("[Build Custom REST API]: Scaffold a complete production-ready single-file FastAPI REST API with CRUD endpoints, zero placeholders, and local run instructions.")
            }
            PromptInspirationChip(label = "🎮 Retro Game") {
                onQuickActionClick("Build an interactive HTML5 retro arcade game with Canvas and touch controls.")
            }
            PromptInspirationChip(label = "📱 Compose UI") {
                onQuickActionClick("Build a complete modern Android Jetpack Compose dashboard screen with clean state management.")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "STUDIOS & ENGINES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Grid of Action Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            QuickActionCard(
                icon = Icons.Default.AutoAwesome,
                title = "App Builder",
                gradient = listOf(Color(0xFF10B981), Color(0xFF00E5FF)),
                onClick = onBuildAppClick
            )

            QuickActionCard(
                icon = Icons.Default.Code,
                title = "Code Studio",
                gradient = listOf(Color(0xFF2563EB), Color(0xFF6366F1)),
                onClick = onCodeStudioClick
            )

            QuickActionCard(
                icon = Icons.Default.Storage,
                title = "Cookie Vault",
                gradient = listOf(Color(0xFFF59E0B), Color(0xFFEF4444)),
                onClick = onCookieVaultClick
            )

            QuickActionCard(
                icon = Icons.Default.Videocam,
                title = "Video Studio",
                gradient = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
                onClick = onVideoStudioClick
            )

            QuickActionCard(
                icon = Icons.Default.Dns,
                title = "API Hub",
                gradient = listOf(Color(0xFF0284C7), Color(0xFF10B981)),
                onClick = onOpenApiHubClick
            )

            QuickActionCard(
                icon = Icons.Default.AutoAwesome,
                title = "Titan Swarm",
                gradient = listOf(Color(0xFF00E5FF), Color(0xFF6366F1)),
                onClick = onSwarmRoomClick
            )
        }
    }
}

@Composable
private fun PromptInspirationChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(gradient.map { it.copy(alpha = 0.15f) })),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = gradient.first(),
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
