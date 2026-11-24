package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivePreviewDialog(
    htmlCode: String,
    title: String = "Live Web Runner",
    serverUrl: String = "http://127.0.0.1:8080/",
    onDismiss: () -> Unit,
    onFixBugWithAi: (code: String, errorTrace: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var runtimeError by remember { mutableStateOf<String?>(null) }
    var isErrorExpanded by remember { mutableStateOf(true) }
    var isMobile375View by remember { mutableStateOf(false) }

    // Inject console error capture script into in-memory HTML if needed
    val instrumentedHtml = remember(htmlCode) {
        val errorScript = """
            <script>
            (function() {
                window.onerror = function(msg, url, line, col, err) {
                    console.error('JS Runtime Error: ' + msg + ' (line ' + line + ')');
                };
                window.onunhandledrejection = function(e) {
                    var detail = e.reason ? (e.reason.stack || e.reason.message || e.reason) : 'Promise Rejected';
                    console.error('Unhandled Promise Rejection: ' + detail);
                };
            })();
            </script>
        """.trimIndent()

        if (htmlCode.contains("<head>", ignoreCase = true)) {
            htmlCode.replaceFirst("(?i)<head>".toRegex(), "<head>$errorScript")
        } else if (htmlCode.contains("<html>", ignoreCase = true)) {
            htmlCode.replaceFirst("(?i)<html>".toRegex(), "<html><head>$errorScript</head>")
        } else {
            "$errorScript\n$htmlCode"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = GeminiDarkObsidian
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Action Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GeminiSurface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeminiTextPrimary
                                )
                                Text(
                                    text = "127.0.0.1:8080 • Canvas 60 FPS Accelerated",
                                    fontSize = 11.sp,
                                    color = GeminiTextSecondary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Mobile 375px vs Full Responsive View Toggle (Bypass IDE signature feature)
                            IconButton(
                                onClick = { isMobile375View = !isMobile375View },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMobile375View) Icons.Default.Smartphone else Icons.Default.DesktopWindows,
                                    contentDescription = if (isMobile375View) "Switch to Responsive Full View" else "Switch to Mobile 375px View",
                                    tint = if (isMobile375View) Color(0xFF00E676) else GeminiCyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Open in Browser (Chrome)
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = "Open in Chrome",
                                    tint = GeminiCyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Reload Button
                            IconButton(
                                onClick = {
                                    runtimeError = null
                                    if (htmlCode.isNotBlank()) {
                                        webViewInstance?.loadDataWithBaseURL(
                                            "http://127.0.0.1:8080/",
                                            instrumentedHtml,
                                            "text/html; charset=utf-8",
                                            "UTF-8",
                                            null
                                        )
                                    } else if (serverUrl.isNotBlank()) {
                                        webViewInstance?.loadUrl(serverUrl)
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    tint = GeminiCyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Close Button
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(GeminiSurfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = GeminiTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Loading Indicator
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF00E676),
                            trackColor = GeminiThinkingBorder
                        )
                    }

                    // Interactive Hardware Accelerated WebView (Responsive Full or Mobile 375px Device Frame)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(if (isMobile375View) Color(0xFF1E1E24) else Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = if (isMobile375View) {
                                Modifier
                                    .width(375.dp)
                                    .fillMaxHeight()
                                    .padding(vertical = 12.dp)
                                    .border(2.dp, Color(0xFF3F3F46), RoundedCornerShape(24.dp))
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.White)
                            } else {
                                Modifier.fillMaxSize()
                            }
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        setLayerType(View.LAYER_TYPE_HARDWARE, null)

                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                isLoading = true
                                            }
                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                isLoading = false
                                            }
                                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                                runtimeError = error?.description?.toString()
                                            }
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                                if (consoleMessage != null && consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                                    val errMsg = consoleMessage.message()
                                                    val line = consoleMessage.lineNumber()
                                                    val src = consoleMessage.sourceId()
                                                    runtimeError = "$errMsg\n(Source: $src, line $line)"
                                                }
                                                return super.onConsoleMessage(consoleMessage)
                                            }
                                        }

                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            allowFileAccess = true
                                            allowContentAccess = true
                                            databaseEnabled = true
                                            loadWithOverviewMode = true
                                            useWideViewPort = true
                                            setSupportZoom(true)
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                            mediaPlaybackRequiresUserGesture = false
                                        }

                                        if (htmlCode.isNotBlank()) {
                                            com.example.util.TitanLocalServerManager.updateInMemoryCode(instrumentedHtml, "", "")
                                            loadDataWithBaseURL(
                                                "http://127.0.0.1:8080/",
                                                instrumentedHtml,
                                                "text/html; charset=utf-8",
                                                "UTF-8",
                                                null
                                            )
                                        } else if (serverUrl.isNotBlank()) {
                                            loadUrl(serverUrl)
                                        }
                                        webViewInstance = this
                                    }
                                },
                                update = { webView ->
                                    webViewInstance = webView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // 1-Tap Auto-Fix Error Banner at Bottom
                runtimeError?.let { errLog ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xEB251010),
                        border = BorderStroke(1.5.dp, Color(0xFFFF5252))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isErrorExpanded = !isErrorExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = "Error",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Runtime Error Detected",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFFFF8A80)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isErrorExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                        contentDescription = "Toggle error details",
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { runtimeError = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss error",
                                            tint = GeminiTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = isErrorExpanded) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Surface(
                                        color = Color(0xFF160909),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, Color(0x66FF5252)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 110.dp)
                                    ) {
                                        Text(
                                            text = errLog,
                                            color = Color(0xFFFFCDD2),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .verticalScroll(rememberScrollState())
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            val capturedCode = htmlCode
                                            val capturedErr = errLog
                                            onDismiss()
                                            onFixBugWithAi(capturedCode, capturedErr)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00E676),
                                            contentColor = Color(0xFF131314)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Fix Bug with AI (1-Tap Auto-Repair)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
