package com.titan.ide.server

import android.content.Context
import android.os.Environment
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * LocalProjectServer - Embedded High-Performance HTTP Server for Titan IDE
 * Serves static HTML, CSS, JS, and media assets on http://127.0.0.1:8080/
 */
object LocalProjectServer {

    private const val TAG = "LocalProjectServer"
    const val DEFAULT_PORT = 8081
    const val PORT = 8081

    @Volatile
    var activePort: Int = DEFAULT_PORT
        private set

    fun getActivePreviewUrl(): String = "http://127.0.0.1:$activePort/"

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    var activeProjectDir: File? = null
    var fallbackHtml: String = """
        <!DOCTYPE html>
        <html>
        <head><title>Titan Project</title><link rel="stylesheet" href="style.css"></head>
        <body>
            <div style="text-align:center;padding:40px;font-family:sans-serif;color:#00E5FF;background:#12151C;min-height:100vh;">
                <h1>🚀 Titan IDE Localhost</h1>
                <p>Serving live on <code>http://127.0.0.1:8081/</code></p>
                <div style="background:#1B202E;padding:20px;border-radius:12px;display:inline-block;border:1px solid #00E5FF44;">
                    Ready for live coding with In-Editor Cursor AI
                </div>
            </div>
            <script src="script.js"></script>
        </body>
        </html>
    """.trimIndent()
    var fallbackCss: String = "body { margin: 0; background: #12151C; }"
    var fallbackJs: String = "console.log('Titan IDE Local Server running');"

    fun bindProject(projectDirectory: File) {
        activeProjectDir = projectDirectory
        if (!_isRunning.value) {
            startServer(projectDirectory)
        }
    }

    fun startServer(projectDirectory: File? = null) {
        if (projectDirectory != null) {
            activeProjectDir = projectDirectory
        }
        if (_isRunning.value) return

        serverJob = scope.launch {
            var boundSocket: ServerSocket? = null
            var boundPort = DEFAULT_PORT

            for (p in 8081..8099) {
                try {
                    val s = ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(p))
                    }
                    boundSocket = s
                    boundPort = p
                    break
                } catch (be: BindException) {
                    Log.i(TAG, "Port $p busy, trying next...")
                } catch (e: Exception) {
                    Log.w(TAG, "Port $p bind exception: ${e.message}")
                }
            }

            if (boundSocket == null) {
                Log.e(TAG, "Failed to bind LocalProjectServer on any port in range 8081..8099")
                _isRunning.value = false
                return@launch
            }

            serverSocket = boundSocket
            activePort = boundPort
            _isRunning.value = true
            Log.d(TAG, "LocalProjectServer running on http://127.0.0.1:$activePort/")

            while (_isRunning.value) {
                val clientSocket = try {
                    serverSocket?.accept()
                } catch (e: Exception) {
                    null
                } ?: break

                launch(Dispatchers.IO) {
                    handleClient(clientSocket)
                }
            }
            _isRunning.value = false
        }
    }

    fun stopServer() {
        _isRunning.value = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverJob?.cancel()
        serverSocket = null
        serverJob = null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val input = BufferedReader(InputStreamReader(s.getInputStream()))
                val output = BufferedOutputStream(s.getOutputStream())

                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                var path = parts[1].substringBefore("?")
                if (path == "/" || path.isEmpty()) path = "/index.html"

                // Serve from active file directory if available
                val dir = activeProjectDir
                val projectFile = if (dir != null && dir.exists()) {
                    val candidate = File(dir, path.removePrefix("/"))
                    if (candidate.canonicalPath.startsWith(dir.canonicalPath + File.separator)) candidate else null
                } else null

                if (projectFile != null && projectFile.exists() && projectFile.isFile) {
                    val mime = getMimeType(projectFile.name)
                    val bytes = projectFile.readBytes()
                    sendResponse(output, 200, "OK", mime, bytes)
                } else {
                    // Serve from in-memory fallback
                    when {
                        path.endsWith("index.html") || path == "/" -> {
                            sendResponse(output, 200, "OK", "text/html; charset=utf-8", fallbackHtml.toByteArray(Charsets.UTF_8))
                        }
                        path.endsWith("style.css") -> {
                            sendResponse(output, 200, "OK", "text/css; charset=utf-8", fallbackCss.toByteArray(Charsets.UTF_8))
                        }
                        path.endsWith("script.js") -> {
                            sendResponse(output, 200, "OK", "application/javascript; charset=utf-8", fallbackJs.toByteArray(Charsets.UTF_8))
                        }
                        else -> {
                            val notFound = "<html><body>404 Not Found</body></html>".toByteArray(Charsets.UTF_8)
                            sendResponse(output, 404, "Not Found", "text/html", notFound)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore socket disconnects
        }
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, statusMsg: String, mimeType: String, content: ByteArray) {
        val writer = PrintWriter(output)
        writer.print("HTTP/1.1 $statusCode $statusMsg\r\n")
        writer.print("Content-Type: $mimeType\r\n")
        writer.print("Content-Length: ${content.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        output.write(content)
        output.flush()
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".html") || fileName.endsWith(".htm") -> "text/html; charset=utf-8"
            fileName.endsWith(".css") -> "text/css; charset=utf-8"
            fileName.endsWith(".js") -> "application/javascript; charset=utf-8"
            fileName.endsWith(".json") -> "application/json; charset=utf-8"
            fileName.endsWith(".png") -> "image/png"
            fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> "image/jpeg"
            fileName.endsWith(".svg") -> "image/svg+xml"
            fileName.endsWith(".webp") -> "image/webp"
            fileName.endsWith(".mp3") -> "audio/mpeg"
            else -> "text/plain; charset=utf-8"
        }
    }
}

/**
 * In-App Preview Dialog with hot-reload and webview inspector
 */
@Composable
fun TitanProjectPreviewDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = GeminiDarkObsidian),
            border = BorderStroke(1.dp, GeminiCyanAccent.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar
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
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = LocalProjectServer.getActivePreviewUrl(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeminiTextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { webViewRef?.reload() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Hot Reload",
                                tint = GeminiCyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = GeminiTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // WebView
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            loadUrl(LocalProjectServer.getActivePreviewUrl())
                            webViewRef = this
                        }
                    },
                    update = { view ->
                        webViewRef = view
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}
