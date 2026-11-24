package com.example.ui.components


import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.ArrowBack
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.*
import com.example.util.GitHubCloner
import com.titan.ide.server.LocalProjectServer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeStudioSheet(
    htmlCode: String,
    cssCode: String,
    jsCode: String,
    onCodeChange: (String, String, String) -> Unit,
    onAutoFixBug: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("index.html") }
    var tempHtml by remember(htmlCode) { mutableStateOf(htmlCode) }
    var tempCss by remember(cssCode) { mutableStateOf(cssCode) }
    var tempJs by remember(jsCode) { mutableStateOf(jsCode) }
    var showPreview by remember { mutableStateOf(false) }

    // In-Editor Cursor AI State
    var showCursorAiBar by remember { mutableStateOf(false) }
    var cursorAiPrompt by remember { mutableStateOf("") }
    var isApplyingCursorAi by remember { mutableStateOf(false) }

    // Live Console Logs (DevTools)
    val consoleLogs = remember { mutableStateListOf<String>() }
    var showConsoleLogs by remember { mutableStateOf(false) }

    // Starter Apps Dialog State
    var showStartersDialog by remember { mutableStateOf(false) }

    // GitHub Clone Dialog State
    var showGitCloneDialog by remember { mutableStateOf(false) }
    var gitRepoUrl by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = GeminiDarkObsidian,
            contentColor = GeminiTextPrimary
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = GeminiCyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Titan Code Studio Pro",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeminiTextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = GeminiCyanAccent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SHIELDED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeminiCyanAccent,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "/sdcard/TitanProjects/ • http://127.0.0.1:8080/",
                            fontSize = 10.sp,
                            color = GeminiTextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Starters Template Gallery
                    IconButton(onClick = { showStartersDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "Templates",
                            tint = GeminiCyanAccent
                        )
                    }

                    // In-Editor Cursor AI Toggle Button
                    IconButton(onClick = { showCursorAiBar = !showCursorAiBar }) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "In-Editor Cursor AI",
                            tint = if (showCursorAiBar) GeminiCyanAccent else GeminiSparklePurple
                        )
                    }

                    // GitHub Clone Button
                    IconButton(onClick = { showGitCloneDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Clone GitHub Repo",
                            tint = GeminiCyanAccent
                        )
                    }

                    // Auto-Fix Bug Button
                    IconButton(
                        onClick = {
                            val currentCode = when (activeTab) {
                                "index.html" -> tempHtml
                                "style.css" -> tempCss
                                else -> tempJs
                            }
                            onAutoFixBug(currentCode)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Auto-Fix",
                            tint = GeminiSparklePurple
                        )
                    }

                    // Preview Toggle Button
                    IconButton(
                        onClick = {
                            onCodeChange(tempHtml, tempCss, tempJs)
                            showPreview = !showPreview
                        }
                    ) {
                        Icon(
                            imageVector = if (showPreview) Icons.Default.Code else Icons.Default.PlayArrow,
                            contentDescription = "Preview",
                            tint = GeminiCyanAccent
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Close",
                            tint = GeminiTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // In-Editor Cursor AI Banner (Interactive AI Assistant)
            AnimatedVisibility(
                visible = showCursorAiBar,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = GeminiSurface),
                    border = BorderStroke(1.dp, GeminiCyanAccent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = GeminiSparklePurple,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cursor AI In-Editor Assistant",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeminiCyanAccent
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = cursorAiPrompt,
                            onValueChange = { cursorAiPrompt = it },
                            placeholder = {
                                Text(
                                    "E.g., 'Add dark mode toggle', 'Add neon glow to buttons', 'Create particle effect'",
                                    fontSize = 12.sp,
                                    color = GeminiTextSecondary
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.5.sp, color = GeminiTextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GeminiCyanAccent,
                                unfocusedBorderColor = GeminiThinkingBorder,
                                focusedContainerColor = GeminiDarkObsidian,
                                unfocusedContainerColor = GeminiDarkObsidian
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick prompt suggestion chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "✨ Neon Cyberpunk Glow",
                                "🌙 Dark Mode Switcher",
                                "⚡ Particle Confetti FX",
                                "📱 Responsive Grid Layout",
                                "🎮 Add Interactive Score"
                            ).forEach { chipText ->
                                Surface(
                                    color = GeminiSurfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, GeminiThinkingBorder),
                                    modifier = Modifier.clickable {
                                        cursorAiPrompt = chipText
                                    }
                                ) {
                                    Text(
                                        text = chipText,
                                        fontSize = 10.5.sp,
                                        color = GeminiCyanAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (cursorAiPrompt.isNotBlank()) {
                                    isApplyingCursorAi = true
                                    val (newHtml, newCss, newJs) = applyCursorAiEdit(
                                        prompt = cursorAiPrompt,
                                        html = tempHtml,
                                        css = tempCss,
                                        js = tempJs
                                    )
                                    tempHtml = newHtml
                                    tempCss = newCss
                                    tempJs = newJs
                                    onCodeChange(tempHtml, tempCss, tempJs)
                                    isApplyingCursorAi = false
                                    showPreview = true
                                    Toast.makeText(
                                        context,
                                        "✨ Cursor AI updated code & hot-reloaded 127.0.0.1:8080!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GeminiCyanAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = GeminiDarkObsidian,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Apply with Cursor AI & Hot-Reload Preview",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeminiDarkObsidian
                            )
                        }
                    }
                }
            }

            // File Explorer Tab Selector + Console Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("index.html", "style.css", "script.js").forEach { fileName ->
                        val isSelected = activeTab == fileName
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onCodeChange(tempHtml, tempCss, tempJs)
                                activeTab = fileName
                            },
                            label = { Text(fileName, fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeminiCyanAccent,
                                selectedLabelColor = GeminiDarkObsidian,
                                containerColor = GeminiSurface,
                                labelColor = GeminiTextPrimary
                            )
                        )
                    }
                }

                // DevTools Console Button
                FilledTonalButton(
                    onClick = { showConsoleLogs = !showConsoleLogs },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (showConsoleLogs) GeminiSparklePurple.copy(alpha = 0.3f) else GeminiSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = if (showConsoleLogs) GeminiSparklePurple else GeminiTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Console (${consoleLogs.size})",
                        fontSize = 11.sp,
                        color = if (showConsoleLogs) GeminiSparklePurple else GeminiTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Content Area
            if (showPreview) {
                // Embedded Web Server 127.0.0.1:8080 Live Preview Window
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        consoleMessage?.let {
                                            val tag = it.messageLevel().name
                                            consoleLogs.add("[$tag] ${it.message()} (line ${it.lineNumber()})")
                                        }
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                loadUrl("http://127.0.0.1:8080/")
                            }
                        },
                        update = { webView ->
                            webView.loadUrl("http://127.0.0.1:8080/")
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // Code Editor Window
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GeminiSurface),
                    border = BorderStroke(1.dp, GeminiThinkingBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Editing $activeTab",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeminiCyanAccent
                            )
                            Text(
                                text = "Cursor AI In-Memory Sync",
                                fontSize = 10.sp,
                                color = GeminiTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val currentContent = when (activeTab) {
                            "index.html" -> tempHtml
                            "style.css" -> tempCss
                            else -> tempJs
                        }

                        OutlinedTextField(
                            value = currentContent,
                            onValueChange = { newText ->
                                when (activeTab) {
                                    "index.html" -> tempHtml = newText
                                    "style.css" -> tempCss = newText
                                    else -> tempJs = newText
                                }
                                onCodeChange(tempHtml, tempCss, tempJs)
                            },
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = GeminiTextPrimary
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }

            // Live Console Logs Drawer
            AnimatedVisibility(
                visible = showConsoleLogs,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = GeminiDarkObsidian),
                    border = BorderStroke(1.dp, GeminiThinkingBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .padding(top = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DevTools JavaScript Console",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeminiCyanAccent
                            )
                            Text(
                                text = "Clear",
                                fontSize = 11.sp,
                                color = GeminiSparklePurple,
                                modifier = Modifier.clickable { consoleLogs.clear() }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (consoleLogs.isEmpty()) {
                            Text(
                                text = "No logs yet. Run code to see console.log() and JavaScript errors.",
                                fontSize = 10.5.sp,
                                color = GeminiTextSecondary
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(consoleLogs) { logEntry ->
                                    val isError = logEntry.contains("ERROR", ignoreCase = true)
                                    Text(
                                        text = logEntry,
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isError) Color(0xFFEF5350) else GeminiTextPrimary,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Run Localhost:8080 Button
                Button(
                    onClick = {
                        onCodeChange(tempHtml, tempCss, tempJs)
                        showPreview = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeminiCyanAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GeminiDarkObsidian, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run 127.0.0.1:8080", color = GeminiDarkObsidian, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // In-Editor AI Hot-Button
                OutlinedButton(
                    onClick = { showCursorAiBar = !showCursorAiBar },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GeminiSparklePurple),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GeminiSparklePurple, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cursor AI", color = GeminiSparklePurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    
        }
    }

    // Starter Apps Template Picker Dialog
    if (showStartersDialog) {
        AlertDialog(
            onDismissRequest = { showStartersDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Widgets, contentDescription = null, tint = GeminiCyanAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Starter App Kits", color = GeminiTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Select a starter template:",
                        fontSize = 12.sp,
                        color = GeminiTextSecondary
                    )

                    listOf(
                        StarterKit("🕹️ Neon Arcade Flappy Bird", "Arcade mini-game with score physics & audio effects", "flappy"),
                        StarterKit("🌦️ Cyber Weather Dashboard", "Real-time futuristic weather UI with live forecast cards", "weather"),
                        StarterKit("📱 Cyberpunk Scientific Calculator", "Sleek glassmorphism calculator with history tape", "calc"),
                        StarterKit("⏱️ Neon Stopwatch & Interval Timer", "Lap counter with animated pulse ring & sound beeps", "timer"),
                        StarterKit("📝 Markdown Todo & Notes Vault", "LocalStorage persistent checklist with categories", "todo")
                    ).forEach { kit ->
                        Surface(
                            color = GeminiDarkObsidian,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, GeminiThinkingBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val (newH, newC, newJ) = loadStarterTemplate(kit.id)
                                    tempHtml = newH
                                    tempCss = newC
                                    tempJs = newJ
                                    onCodeChange(tempHtml, tempCss, tempJs)
                                    showStartersDialog = false
                                    showPreview = true
                                    Toast.makeText(context, "Loaded ${kit.title}!", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(kit.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GeminiCyanAccent)
                                Text(kit.desc, fontSize = 10.5.sp, color = GeminiTextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStartersDialog = false }) {
                    Text("Cancel", color = GeminiTextSecondary)
                }
            },
            containerColor = GeminiSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Git Clone Dialog
    if (showGitCloneDialog) {
        AlertDialog(
            onDismissRequest = { showGitCloneDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = GeminiCyanAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Git Clone Repository", color = GeminiTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Clone public GitHub repo.",
                        fontSize = 12.sp,
                        color = GeminiTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = gitRepoUrl,
                        onValueChange = { gitRepoUrl = it },
                        placeholder = { Text("https://github.com/username/repo", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                var isCloningRepo by remember { mutableStateOf(false) }
                val cloneScope = rememberCoroutineScope()
                Button(
                    enabled = !isCloningRepo && gitRepoUrl.isNotBlank(),
                    onClick = {
                        isCloningRepo = true
                        cloneScope.launch {
                            val result = GitHubCloner.cloneRepository(context, gitRepoUrl.trim())
                            isCloningRepo = false
                            if (result.isSuccess) {
                                val projectDir = result.getOrNull() ?: return@launch
                                val htmlFile = java.io.File(projectDir, "index.html")
                                val cssFile = java.io.File(projectDir, "style.css").let { if (it.exists()) it else java.io.File(projectDir, "styles.css") }
                                val jsFile = java.io.File(projectDir, "script.js").let { if (it.exists()) it else java.io.File(projectDir, "app.js") }
                                if (htmlFile.exists()) tempHtml = htmlFile.readText(Charsets.UTF_8)
                                if (cssFile.exists()) tempCss = cssFile.readText(Charsets.UTF_8)
                                if (jsFile.exists()) tempJs = jsFile.readText(Charsets.UTF_8)
                                LocalProjectServer.bindProject(projectDir)
                                onCodeChange(tempHtml, tempCss, tempJs)
                                showGitCloneDialog = false
                                showPreview = true
                                Toast.makeText(context, "✅ Cloned ${projectDir.name} & serving on 127.0.0.1:8080!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ Clone failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeminiCyanAccent)
                ) {
                    Text(if (isCloningRepo) "Cloning..." else "Clone & Serve", color = GeminiDarkObsidian, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGitCloneDialog = false }) {
                    Text("Cancel", color = GeminiTextSecondary)
                }
            },
            containerColor = GeminiSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

private data class StarterKit(val title: String, val desc: String, val id: String)

private fun loadStarterTemplate(kitId: String): Triple<String, String, String> {
    return when (kitId) {
        "flappy" -> Triple(
            """<!DOCTYPE html><html><head><title>Neon Flappy</title><link rel="stylesheet" href="style.css"></head><body><div class="game-box"><canvas id="gameCanvas" width="320" height="460"></canvas><p>Tap screen to flap wings</p></div><script src="script.js"></script></body></html>""".trimIndent(),
            """body{margin:0;background:#12151C;display:flex;justify-content:center;align-items:center;height:100vh;color:#00E5FF;font-family:sans-serif}.game-box{text-align:center}canvas{background:#1B202E;border:2px solid #00E5FF;border-radius:12px;box-shadow:0 0 20px rgba(0,229,255,0.3)}""".trimIndent(),
            """const c=document.getElementById('gameCanvas'),ctx=c.getContext('2d');let bY=200,bV=0,score=0;function jump(){bV=-6;}window.addEventListener('click',jump);function loop(){ctx.clearRect(0,0,320,460);bV+=0.28;bY+=bV;ctx.fillStyle='#00E5FF';ctx.beginPath();ctx.arc(60,bY,12,0,Math.PI*2);ctx.fill();ctx.fillStyle='#fff';ctx.fillText('Score: '+score,10,20);requestAnimationFrame(loop);}loop();""".trimIndent()
        )
        "weather" -> Triple(
            """<!DOCTYPE html><html><head><title>Weather Pro</title><link rel="stylesheet" href="style.css"></head><body><div class="card"><h2>⛅ Dhaka / Delhi</h2><h1>28°C</h1><p>Sunny & Clear Skies</p><div class="details"><span>💧 62%</span><span>💨 14 km/h</span></div></div></body></html>""".trimIndent(),
            """body{margin:0;background:#12151C;display:flex;justify-content:center;align-items:center;height:100vh;font-family:sans-serif}.card{background:#1B202E;padding:28px;border-radius:20px;border:1px solid #00E5FF44;color:#fff;text-align:center;box-shadow:0 8px 32px rgba(0,0,0,0.5)}h1{font-size:48px;color:#00E5FF;margin:10px 0}.details{display:flex;justify-content:space-around;margin-top:20px;color:#D0BCFF}""".trimIndent(),
            """console.log('Weather Dashboard Loaded');""".trimIndent()
        )
        "calc" -> Triple(
            """<!DOCTYPE html><html><head><title>Calculator</title><link rel="stylesheet" href="style.css"></head><body><div class="calc"><input type="text" id="disp" readonly value="0"><div class="grid"><button onclick="c('7')">7</button><button onclick="c('8')">8</button><button onclick="c('9')">9</button><button onclick="op('/')">÷</button><button onclick="c('4')">4</button><button onclick="c('5')">5</button><button onclick="c('6')">6</button><button onclick="op('*')">×</button><button onclick="c('1')">1</button><button onclick="c('2')">2</button><button onclick="c('3')">3</button><button onclick="op('-')">-</button><button onclick="clr()">C</button><button onclick="c('0')">0</button><button onclick="eq()">=</button><button onclick="op('+')">+</button></div></div><script src="script.js"></script></body></html>""".trimIndent(),
            """body{margin:0;background:#12151C;display:flex;justify-content:center;align-items:center;height:100vh;font-family:sans-serif}.calc{background:#1B202E;padding:20px;border-radius:18px;border:1px solid #00E5FF44;box-shadow:0 0 25px rgba(0,229,255,0.2)}#disp{width:100%;height:50px;background:#12151C;border:1px solid #00E5FF;color:#00E5FF;font-size:24px;text-align:right;padding:8px;border-radius:8px;box-sizing:border-box;margin-bottom:14px}.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}button{padding:14px;background:#242A3D;color:#fff;border:none;border-radius:8px;font-size:16px;cursor:pointer}button:hover{background:#00E5FF;color:#12151C}""".trimIndent(),
            """let d=document.getElementById('disp');function c(v){if(d.value==='0')d.value='';d.value+=v;}function op(o){d.value+=' '+o+' ';}function clr(){d.value='0';}function eq(){try{d.value=eval(d.value);}catch(e){d.value='Err';}}""".trimIndent()
        )
        else -> Triple(
            """<!DOCTYPE html><html><head><title>Titan App</title><link rel="stylesheet" href="style.css"></head><body><h1>⚡ Titan App Active</h1></body></html>""".trimIndent(),
            """body{background:#12151C;color:#00E5FF;text-align:center;padding:50px;font-family:sans-serif}""".trimIndent(),
            """console.log('App ready');""".trimIndent()
        )
    }
}

/**
 * In-Editor Cursor AI code modification engine that applies prompt transformations
 * directly to index.html, style.css, and script.js and hot-reloads 127.0.0.1:8080
 */
private fun applyCursorAiEdit(prompt: String, html: String, css: String, js: String): Triple<String, String, String> {
    val p = prompt.lowercase()
    var newHtml = html
    var newCss = css
    var newJs = js

    if (p.contains("dark") || p.contains("theme") || p.contains("mode")) {
        if (!newHtml.contains("toggle-theme-btn")) {
            newHtml = newHtml.replace("</body>", "  <button id=\"toggle-theme-btn\" class=\"titan-theme-toggle\">🌙 Toggle Mode</button>\n</body>")
        }
        newCss += "\n\n/* Cursor AI Dark Mode Styling */\n.titan-theme-toggle { position: fixed; top: 16px; right: 16px; padding: 8px 16px; border-radius: 20px; border: 1px solid #00E5FF; background: #1B202E; color: #00E5FF; font-weight: bold; cursor: pointer; z-index: 999; }\nbody.light-mode { background: #F8F9FA !important; color: #1A1A1A !important; }"
        newJs += "\n\n// Cursor AI Theme Toggle\ndocument.getElementById('toggle-theme-btn')?.addEventListener('click', () => { document.body.classList.toggle('light-mode'); });"
    } else if (p.contains("neon") || p.contains("glow") || p.contains("cyber")) {
        newCss += "\n\n/* Cursor AI Neon Cyberpunk Glow */\nbutton, .btn { box-shadow: 0 0 15px #00E5FF, inset 0 0 10px #00E5FF !important; text-shadow: 0 0 8px #00E5FF !important; border-radius: 12px !important; transition: all 0.3s ease; }\nbutton:hover { transform: scale(1.05); box-shadow: 0 0 25px #D0BCFF, inset 0 0 15px #D0BCFF !important; }"
    } else if (p.contains("particle") || p.contains("confetti") || p.contains("fx")) {
        if (!newHtml.contains("particleCanvas")) {
            newHtml = newHtml.replace("</body>", "  <canvas id=\"particleCanvas\" style=\"position:fixed;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:998;\"></canvas>\n</body>")
        }
        newJs += """
            // Cursor AI Interactive Particle Fireworks
            const canvas = document.getElementById('particleCanvas');
            if (canvas) {
                const ctx = canvas.getContext('2d');
                canvas.width = window.innerWidth;
                canvas.height = window.innerHeight;
                const particles = [];
                for (let i = 0; i < 40; i++) {
                    particles.push({
                        x: Math.random() * canvas.width,
                        y: Math.random() * canvas.height,
                        radius: Math.random() * 3 + 1,
                        dx: (Math.random() - 0.5) * 2,
                        dy: (Math.random() - 0.5) * 2,
                        color: ['#00E5FF', '#D0BCFF', '#FF007F'][Math.floor(Math.random() * 3)]
                    });
                }
                function animateParticles() {
                    ctx.clearRect(0, 0, canvas.width, canvas.height);
                    particles.forEach(p => {
                        ctx.beginPath();
                        ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
                        ctx.fillStyle = p.color;
                        ctx.fill();
                        p.x += p.dx;
                        p.y += p.dy;
                        if (p.x < 0 || p.x > canvas.width) p.dx = -p.dx;
                        if (p.y < 0 || p.y > canvas.height) p.dy = -p.dy;
                    });
                    requestAnimationFrame(animateParticles);
                }
                animateParticles();
            }
        """.trimIndent()
    } else {
        // General feature injection
        newCss += "\n\n/* Cursor AI Feature: $prompt */\n.titan-ai-featured { animation: titanPulse 2s infinite alternate; }\n@keyframes titanPulse { from { opacity: 0.8; } to { opacity: 1; } }"
        if (!newHtml.contains("titan-ai-badge")) {
            newHtml = newHtml.replace("</body>", "  <div class=\"titan-ai-badge\" style=\"position:fixed;bottom:12px;left:12px;background:#1B202E;color:#00E5FF;padding:6px 12px;border-radius:8px;font-size:11px;border:1px solid #00E5FF44;\">✨ Powered by Titan Cursor AI</div>\n</body>")
        }
    }

    return Triple(newHtml, newCss, newJs)
}
