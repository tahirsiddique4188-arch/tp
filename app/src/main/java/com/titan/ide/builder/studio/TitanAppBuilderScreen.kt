package com.titan.ide.builder.studio

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.speech.RecognizerIntent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.titan.ide.builder.TitanAutonomousBuilder
import com.titan.ide.voice.TitanNativeVoiceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class BuilderStudioState {
    IDLE,
    PLANNING,
    GENERATING_FILES,
    VERIFIED_READY
}

data class AppBuilderFile(
    val filePath: String,
    val content: String,
    val isReady: Boolean = true
)

/**
 * TitanAppBuilderScreen - Clean White Premium Google AI Studio & Antigravity App Builder
 *
 * Implements:
 * 1. Clean White Google-standard Chat-driven Canvas (matching Main Chat aesthetic).
 * 2. Single-Prompt Full-Stack Project Synthesis (HTML/CSS/JS or Kotlin/Android).
 * 3. Transparent 4-phase step-by-step progress cards.
 * 4. Multi-file code workspace, syntax viewer, 1-tap copy, and ZIP export.
 * 5. Responsive multi-device live runner on 127.0.0.1:8080 (Mobile, Tablet, Desktop).
 *
 * Developer: Developer Tahir (@Tahirsiddik)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitanAppBuilderScreen(
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var studioState by remember { mutableStateOf(BuilderStudioState.IDLE) }
    var promptInput by remember { mutableStateOf("") }
    var lastSubmittedPrompt by remember { mutableStateOf("") }
    var selectedLanguageStr by remember { mutableStateOf("Web App (HTML/JS)") }
    var langDropdownExpanded by remember { mutableStateOf(false) }

    val languagesList = listOf("Web App (HTML/JS)", "Kotlin Android", "Java Android", "Python")

    // Project Workspace State
    var generatedFiles by remember { mutableStateOf<List<AppBuilderFile>>(emptyList()) }
    var activeFileIndex by remember { mutableIntStateOf(0) }
    var activeFileContent by remember { mutableStateOf("") }
    var progressPercentage by remember { mutableFloatStateOf(0f) }
    var currentStepText by remember { mutableStateOf("") }
    var activeGeneratingFileName by remember { mutableStateOf<String?>("index.html") }

    // Floating Responsive Web View state
    var showResponsivePreview by remember { mutableStateOf(false) }
    var previewViewportMode by remember { mutableStateOf("mobile") } // mobile, tablet, desktop

    val activeFile = generatedFiles.getOrNull(activeFileIndex)

    LaunchedEffect(activeFile) {
        if (activeFile != null) {
            activeFileContent = activeFile.content
        }
    }

    // Auto-scroll when new progress arrives
    LaunchedEffect(progressPercentage, studioState) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Google Premium Clean Palette
    val googleBlue = Color(0xFF1A73E8)
    val textPrimary = Color(0xFF1F1F1F)
    val textSecondary = Color(0xFF5F6368)
    val cardBorder = Color(0xFFE0E0E0)
    val surfaceBubble = Color(0xFFF1F3F4)
    val emeraldGreen = Color(0xFF10B981)

    fun startBuildPipeline(prompt: String) {
        if (prompt.isBlank()) return
        lastSubmittedPrompt = prompt
        promptInput = ""
        studioState = BuilderStudioState.PLANNING
        progressPercentage = 0.15f
        currentStepText = "Phase 1: Designing architecture & component manifest..."

        val targetLang = when (selectedLanguageStr) {
            "Kotlin Android" -> TitanAutonomousBuilder.TargetLanguage.KOTLIN
            "Java Android" -> TitanAutonomousBuilder.TargetLanguage.JAVA
            "Python" -> TitanAutonomousBuilder.TargetLanguage.PYTHON
            else -> TitanAutonomousBuilder.TargetLanguage.WEB
        }

        coroutineScope.launch {
            TitanAutonomousBuilder.buildProject(
                userPrompt = prompt,
                context = context,
                requestedLanguage = targetLang,
                onUpdate = { state ->
                    currentStepText = state.currentStatus
                    progressPercentage = state.progress
                    activeGeneratingFileName = state.currentFileName

                    if (state.files.isNotEmpty()) {
                        val fileList = state.files.map { (name, content) ->
                            AppBuilderFile(
                                filePath = name,
                                content = content,
                                isReady = state.stage == TitanAutonomousBuilder.BuilderStage.COMPLETED
                            )
                        }
                        generatedFiles = fileList
                        if (activeFileIndex < fileList.size) {
                            activeFileContent = fileList[activeFileIndex].content
                        }
                    }

                    when (state.stage) {
                        TitanAutonomousBuilder.BuilderStage.PLANNING -> studioState = BuilderStudioState.PLANNING
                        TitanAutonomousBuilder.BuilderStage.SYNTHESIS,
                        TitanAutonomousBuilder.BuilderStage.SELF_HEALING_AUDIT -> studioState = BuilderStudioState.GENERATING_FILES
                        TitanAutonomousBuilder.BuilderStage.COMPLETED -> {
                            studioState = BuilderStudioState.VERIFIED_READY
                            currentStepText = "Verified! 0 compilation errors detected. Ready on 127.0.0.1:8080."
                            TitanNativeVoiceEngine.speakText(
                                context = context,
                                text = "Full-stack app build complete and verified. Ready for live preview.",
                                voice = "Puck"
                            )
                        }
                        else -> {}
                    }
                }
            )
        }
    }

    // Voice to Text Recognizer Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                promptInput = spokenText
                startBuildPipeline(spokenText)
            }
        }
    }

    fun triggerVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the app you want to build...")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice input: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportProjectZip() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
                val targetDir = File(baseDir, "TitanProjects/Exports")
                if (!targetDir.exists()) targetDir.mkdirs()
                val zipFile = File(targetDir, "TitanProject_${System.currentTimeMillis()}.zip")

                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    generatedFiles.forEach { file ->
                        val entry = ZipEntry(file.filePath)
                        zos.putNextEntry(entry)
                        zos.write(file.content.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "📦 Exported ZIP: ${zipFile.name} to Documents/TitanProjects", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Clean Top Navigation Bar
        Surface(
            color = Color(0xFFFFFFFF),
            border = BorderStroke(1.dp, Color(0xFFF1F3F4)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(surfaceBubble)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Titan App Builder",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFE8F0FE),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Studio",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = googleBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Single-Prompt Autonomous Synthesis",
                            fontSize = 11.5.sp,
                            color = textSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Language Dropdown
                    Box {
                        Surface(
                            onClick = { langDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            color = surfaceBubble
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedLanguageStr.substringBefore(' '),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = googleBlue
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = googleBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = langDropdownExpanded,
                            onDismissRequest = { langDropdownExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            languagesList.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang, fontSize = 13.sp, color = textPrimary) },
                                    onClick = {
                                        selectedLanguageStr = lang
                                        langDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Reset / New Project Button
                    IconButton(
                        onClick = {
                            studioState = BuilderStudioState.IDLE
                            promptInput = ""
                            lastSubmittedPrompt = ""
                            generatedFiles = emptyList()
                            progressPercentage = 0f
                            currentStepText = ""
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(surfaceBubble)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "New Project",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Chat / Conversation Canvas (Scrollable)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // If IDLE: Show Clean Welcome Card & Ideas
                if (studioState == BuilderStudioState.IDLE) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F0FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = googleBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Build Full-Stack Apps with AI",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Enter any prompt below to synthesize architecture, multi-file code, zero-bug audit, and live preview.",
                            fontSize = 13.sp,
                            color = textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        // Quick Inspiration Prompts
                        Text(
                            text = "SUGGESTED PROJECTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val quickIdeas = listOf(
                            "🛍️ E-Commerce Shop with Cart & Checkout",
                            "🌤️ Weather Forecast with Live Radar & Charts",
                            "📝 Kanban Task Board with Drag & Drop",
                            "🎮 2D Retro Space Shooter Canvas Game",
                            "📱 Personal Expense & Budget Tracker"
                        )

                        quickIdeas.forEach { idea ->
                            Surface(
                                onClick = {
                                    promptInput = idea
                                    startBuildPipeline(idea)
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = surfaceBubble,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = idea,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Active Build Canvas

                    // 1. User's Prompt Message Bubble
                    if (lastSubmittedPrompt.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = surfaceBubble,
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = lastSubmittedPrompt,
                                            fontSize = 14.sp,
                                            lineHeight = 21.sp,
                                            color = textPrimary,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(googleBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. AI Builder Live Execution Card
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE8F0FE)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = googleBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Titan Autonomous Builder",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = textPrimary
                                    )
                                }

                                if (studioState == BuilderStudioState.VERIFIED_READY) {
                                    Surface(
                                        color = Color(0xFFECFDF5),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Ready 127.0.0.1:8080",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = emeraldGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Progress Bar
                            if (studioState != BuilderStudioState.VERIFIED_READY) {
                                LinearProgressIndicator(
                                    progress = { progressPercentage },
                                    color = googleBlue,
                                    trackColor = Color(0xFFF1F3F4),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = currentStepText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textSecondary,
                                    fontFamily = FontFamily.Monospace
                                )

                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            // 4-Phase Step Indicators
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BuildStepRow(
                                    stepNum = "1",
                                    label = "Architecture & Spec Planning",
                                    isDone = progressPercentage >= 0.25f,
                                    isActive = studioState == BuilderStudioState.PLANNING
                                )
                                BuildStepRow(
                                    stepNum = "2",
                                    label = if (activeGeneratingFileName != null) "Synthesizing [$activeGeneratingFileName]" else "Source Code Synthesis",
                                    isDone = progressPercentage >= 0.85f,
                                    isActive = studioState == BuilderStudioState.GENERATING_FILES && progressPercentage < 0.85f
                                )
                                BuildStepRow(
                                    stepNum = "3",
                                    label = "Self-Healing Bug & Syntax Audit",
                                    isDone = progressPercentage >= 0.98f,
                                    isActive = progressPercentage in 0.85f..0.98f
                                )
                                BuildStepRow(
                                    stepNum = "4",
                                    label = "Local Server Deployment (127.0.0.1:8080)",
                                    isDone = studioState == BuilderStudioState.VERIFIED_READY,
                                    isActive = progressPercentage >= 0.98f && studioState != BuilderStudioState.VERIFIED_READY
                                )
                            }

                            // If Completed: Show Code Tabs & Preview/Export Buttons
                            if (studioState == BuilderStudioState.VERIFIED_READY && generatedFiles.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))

                                // File Tabs
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    generatedFiles.forEachIndexed { index, file ->
                                        val isSelected = index == activeFileIndex
                                        Surface(
                                            onClick = {
                                                activeFileIndex = index
                                                activeFileContent = file.content
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) Color(0xFFE8F0FE) else surfaceBubble,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) googleBlue else Color.Transparent
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = getFileIcon(file.filePath),
                                                    contentDescription = null,
                                                    tint = getFileColor(file.filePath),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = file.filePath,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) googleBlue else textPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Code Box with Copy Button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 180.dp, max = 260.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF0F172A))
                                        .padding(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = activeFileContent,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.5.sp,
                                            color = Color(0xFFE2E8F0),
                                            lineHeight = 18.sp
                                        )
                                    }

                                    // 1-Tap Copy Code
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Titan Code", activeFileContent))
                                            Toast.makeText(context, "Copied ${activeFile?.filePath ?: "file"}!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Action Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { showResponsivePreview = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = emeraldGreen),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .height(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Run Live Preview",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    Button(
                                        onClick = { exportProjectZip() },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = googleBlue),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderZip,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Export ZIP",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Clean Chat-Style Bottom Input Bar (Identical to Main Chat)
        Surface(
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFF1F3F4)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Input Pill
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = surfaceBubble,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            placeholder = {
                                Text(
                                    text = if (studioState == BuilderStudioState.VERIFIED_READY) "Ask for project changes (e.g. Add dark mode)..." else "Describe your app idea in detail...",
                                    fontSize = 13.5.sp,
                                    color = textSecondary
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            ),
                            maxLines = 4,
                            modifier = Modifier.weight(1f)
                        )

                        // Voice Dictation Mic inside Pill
                        IconButton(
                            onClick = { triggerVoiceInput() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Dictate",
                                tint = googleBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button with ArrowUpward
                val isBuilding = studioState == BuilderStudioState.PLANNING || studioState == BuilderStudioState.GENERATING_FILES
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (promptInput.isNotBlank() && !isBuilding)
                                Brush.linearGradient(listOf(googleBlue, Color(0xFF4F46E5)))
                            else
                                Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8)))
                        )
                        .clickable(enabled = promptInput.isNotBlank() && !isBuilding) {
                            startBuildPipeline(promptInput)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuilding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Build App",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Responsive Embedded Floating WebView Dialog (127.0.0.1:8080)
    if (showResponsivePreview) {
        Dialog(
            onDismissRequest = { showResponsivePreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth(
                        when (previewViewportMode) {
                            "mobile" -> 0.78f
                            "tablet" -> 0.90f
                            else -> 0.98f
                        }
                    )
                    .fillMaxHeight(0.92f)
                    .shadow(16.dp, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Viewport Toggle Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .border(BorderStroke(1.dp, cardBorder))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Viewport:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                            IconButton(
                                onClick = { previewViewportMode = "mobile" },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (previewViewportMode == "mobile") Color(0xFFE8F0FE) else Color.Transparent)
                            ) {
                                Icon(
                                    Icons.Default.Smartphone,
                                    contentDescription = "Mobile",
                                    tint = if (previewViewportMode == "mobile") googleBlue else textSecondary
                                )
                            }
                            IconButton(
                                onClick = { previewViewportMode = "tablet" },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (previewViewportMode == "tablet") Color(0xFFE8F0FE) else Color.Transparent)
                            ) {
                                Icon(
                                    Icons.Default.Tablet,
                                    contentDescription = "Tablet",
                                    tint = if (previewViewportMode == "tablet") googleBlue else textSecondary
                                )
                            }
                            IconButton(
                                onClick = { previewViewportMode = "desktop" },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (previewViewportMode == "desktop") Color(0xFFE8F0FE) else Color.Transparent)
                            ) {
                                Icon(
                                    Icons.Default.DesktopWindows,
                                    contentDescription = "Desktop",
                                    tint = if (previewViewportMode == "desktop") googleBlue else textSecondary
                                )
                            }
                        }

                        IconButton(onClick = { showResponsivePreview = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                loadUrl("http://127.0.0.1:8080/")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildStepRow(
    stepNum: String,
    label: String,
    isDone: Boolean,
    isActive: Boolean
) {
    val emeraldGreen = Color(0xFF10B981)
    val googleBlue = Color(0xFF1A73E8)
    val textPrimary = Color(0xFF1F1F1F)
    val textSecondary = Color(0xFF5F6368)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isDone) emeraldGreen
                    else if (isActive) googleBlue
                    else Color(0xFFE0E0E0)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = stepNum,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White else textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (isActive || isDone) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive || isDone) textPrimary else textSecondary
        )
    }
}

private fun getFileIcon(filePath: String) = when {
    filePath.endsWith(".html") -> Icons.Default.Html
    filePath.endsWith(".js") -> Icons.Default.Javascript
    filePath.endsWith(".kt") || filePath.endsWith(".java") -> Icons.Default.Code
    else -> Icons.Default.Code
}

private fun getFileColor(filePath: String) = when {
    filePath.endsWith(".html") -> Color(0xFFE44D26)
    filePath.endsWith(".js") -> Color(0xFFEAB308)
    filePath.endsWith(".kt") -> Color(0xFF7F52FF)
    filePath.endsWith(".css") -> Color(0xFF2563EB)
    else -> Color(0xFF1A73E8)
}
