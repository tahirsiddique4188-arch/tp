package com.example.data.local.server

import android.util.Log
import com.example.util.TitanApiKeyManager
import com.titan.ide.server.LocalProjectServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLConnection

class LocalHttpServer(val port: Int = 8080) {

    private var serverSocket: ServerSocket? = null
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var isRunning = false
        private set

    var activeProjectDir: File? = null

    var htmlContent: String = "<html><body style='background:#131314;color:#00E5FF;font-family:sans-serif;'><h1>Titan Local Server Active</h1><p>Ready for web app preview and REST API Gateway.</p></body></html>"
    var cssContent: String = "body { margin: 0; padding: 16px; }"
    var jsContent: String = "console.log('Titan Code Studio Active');"

    private val proxyClient = com.example.util.TitanSecurityShield.buildSecureOkHttpClient(timeoutSeconds = 120)

    companion object {
        private const val TAG = "LocalHttpServer"
        val CLOUD_PROXY_URL: String
            get() = com.example.util.TitanSecurityShield.getChatCompletionsUrl()
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        serverScope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                Log.d(TAG, "Local HTTP & API Gateway Server started on http://127.0.0.1:$port")

                while (isRunning) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (se: java.net.SocketException) {
                        if (!isRunning) {
                            Log.d(TAG, "Server socket closed cleanly.")
                            break
                        } else {
                            throw se
                        }
                    } ?: break
                    handleClient(socket)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "Server notice: ${e.message}")
                    isRunning = false
                } else {
                    Log.d(TAG, "Server stopped gracefully.")
                }
            }
        }
    }

    private fun readHttpLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var b = input.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) baos.write(b)
            b = input.read()
        }
        return baos.toString(Charsets.UTF_8.name())
    }

    private fun readExactBytes(input: InputStream, length: Int): ByteArray {
        val safeLen = length.coerceIn(0, 16 * 1024 * 1024)
        val buffer = ByteArray(safeLen)
        var offset = 0
        while (offset < safeLen) {
            val read = input.read(buffer, offset, safeLen - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == safeLen) buffer else buffer.copyOf(offset)
    }

    private fun handleClient(socket: Socket) {
        serverScope.launch {
            try {
                socket.use { s ->
                    s.soTimeout = 30_000
                    val input = BufferedInputStream(s.getInputStream())
                    val out = s.getOutputStream()

                    val requestLine = readHttpLine(input) ?: return@use
                    val parts = requestLine.split(" ")
                    val method = parts.getOrNull(0)?.uppercase() ?: "GET"
                    val rawPath = parts.getOrNull(1) ?: "/"
                    val cleanPath = rawPath.substringBefore("?").substringBefore("#")

                    // Read incoming HTTP headers
                    val headers = mutableMapOf<String, String>()
                    var headerLine = readHttpLine(input)
                    while (!headerLine.isNullOrEmpty()) {
                        val colonIdx = headerLine.indexOf(':')
                        if (colonIdx > 0) {
                            val name = headerLine.substring(0, colonIdx).trim().lowercase()
                            val value = headerLine.substring(colonIdx + 1).trim()
                            headers[name] = value
                        }
                        headerLine = readHttpLine(input)
                    }

                    // Handle CORS preflight options request
                    if (method == "OPTIONS") {
                        val writer = PrintWriter(out, false)
                        writer.print("HTTP/1.1 204 No Content\r\n")
                        writer.print("Access-Control-Allow-Origin: *\r\n")
                        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS, PUT, DELETE\r\n")
                        writer.print("Access-Control-Allow-Headers: Authorization, Content-Type, Accept, X-Requested-With, Origin, x-api-key\r\n")
                        writer.print("Access-Control-Max-Age: 86400\r\n")
                        writer.print("Connection: close\r\n")
                        writer.print("\r\n")
                        writer.flush()
                        return@use
                    }

                    // Dispatch REST API: /v1/chat/completions
                    if (cleanPath == "/v1/chat/completions" || cleanPath == "/chat/completions") {
                        if (method == "POST") {
                            handleChatCompletions(headers, input, out)
                            return@use
                        }
                    }

                    // Dispatch REST API: /v1/models (OpenAI compatibility)
                    if (cleanPath == "/v1/models" || cleanPath == "/models") {
                        handleModelsList(out)
                        return@use
                    }

                    // Dispatch REST API: /v1/status or /v1
                    if (cleanPath == "/v1" || cleanPath == "/v1/status" || cleanPath == "/v1/health") {
                        handleStatus(out)
                        return@use
                    }

                    // Dispatch Static Project File Server (check activeProjectDir or LocalProjectServer's dir)
                    val dir = activeProjectDir ?: LocalProjectServer.activeProjectDir
                    if (dir != null && dir.exists()) {
                        val relativePath = if (cleanPath == "/" || cleanPath.isEmpty()) "index.html" else cleanPath.removePrefix("/")
                        val targetFile = File(dir, relativePath)
                        val canonicalDir = dir.canonicalPath + File.separator
                        val canonicalTarget = targetFile.canonicalPath

                        if (canonicalTarget.startsWith(canonicalDir) && targetFile.exists() && targetFile.isFile) {
                            val mimeType = getMimeType(targetFile.name)
                            val bytes = targetFile.readBytes()

                            val headerWriter = PrintWriter(out, false)
                            headerWriter.print("HTTP/1.1 200 OK\r\n")
                            headerWriter.print("Content-Type: $mimeType\r\n")
                            headerWriter.print("Content-Length: ${bytes.size}\r\n")
                            headerWriter.print("Access-Control-Allow-Origin: *\r\n")
                            headerWriter.print("Connection: close\r\n")
                            headerWriter.print("\r\n")
                            headerWriter.flush()

                            out.write(bytes)
                            out.flush()
                            return@use
                        }
                    }

                    // Fallback to in-memory code studio preview content
                    val effectiveHtml = if (LocalProjectServer.activeProjectDir != null) LocalProjectServer.fallbackHtml else htmlContent
                    val effectiveCss = if (LocalProjectServer.activeProjectDir != null) LocalProjectServer.fallbackCss else cssContent
                    val effectiveJs = if (LocalProjectServer.activeProjectDir != null) LocalProjectServer.fallbackJs else jsContent

                    val (contentType, bytes) = when {
                        cleanPath.endsWith(".css") -> "text/css; charset=utf-8" to effectiveCss.toByteArray(Charsets.UTF_8)
                        cleanPath.endsWith(".js") -> "application/javascript; charset=utf-8" to effectiveJs.toByteArray(Charsets.UTF_8)
                        else -> {
                            val combinedHtml = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                    <style>$effectiveCss</style>
                                </head>
                                <body>
                                    $effectiveHtml
                                    <script>$effectiveJs</script>
                                </body>
                                </html>
                            """.trimIndent()
                            "text/html; charset=utf-8" to combinedHtml.toByteArray(Charsets.UTF_8)
                        }
                    }

                    val headerWriter = PrintWriter(out, false)
                    headerWriter.print("HTTP/1.1 200 OK\r\n")
                    headerWriter.print("Content-Type: $contentType\r\n")
                    headerWriter.print("Content-Length: ${bytes.size}\r\n")
                    headerWriter.print("Access-Control-Allow-Origin: *\r\n")
                    headerWriter.print("Connection: close\r\n")
                    headerWriter.print("\r\n")
                    headerWriter.flush()

                    out.write(bytes)
                    out.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client handle error: ${e.message}")
            }
        }
    }

    private fun handleChatCompletions(
        headers: Map<String, String>,
        input: InputStream,
        out: OutputStream
    ) {
        // 1. Authenticate with TitanApiKeyManager
        val authHeader = headers["authorization"] ?: headers["x-api-key"] ?: ""
        val token = authHeader.removePrefix("Bearer ").removePrefix("bearer ").trim()

        if (!TitanApiKeyManager.validateKey(token)) {
            val errorJson = """{"error":{"message":"Unauthorized: Invalid, inactive, or missing Titan API Key. Please provide your active key from Titan Developer Hub in 'Authorization: Bearer titan-sk-...'","type":"invalid_request_error","code":"invalid_api_key"}}"""
            val bytes = errorJson.toByteArray(Charsets.UTF_8)
            val writer = PrintWriter(out, false)
            writer.print("HTTP/1.1 401 Unauthorized\r\n")
            writer.print("Content-Type: application/json; charset=utf-8\r\n")
            writer.print("Content-Length: ${bytes.size}\r\n")
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.flush()
            out.write(bytes)
            out.flush()
            return
        }

        // 2. Read exact byte length for UTF-8 JSON body
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val bodyBytes = if (contentLength > 0) readExactBytes(input, contentLength) else byteArrayOf()
        val bodyString = String(bodyBytes, Charsets.UTF_8)

        val reqJson = try {
            org.json.JSONObject(bodyString)
        } catch (e: Exception) {
            org.json.JSONObject()
        }

        val requestedModel = reqJson.optString("model", "gemini-3.5-flash-lite")
        val isStreamRequested = bodyString.contains("\"stream\": true") || bodyString.contains("\"stream\":true") || reqJson.optBoolean("stream", false)
        val messagesJson = reqJson.optJSONArray("messages")

        val chatMessages = mutableListOf<com.example.data.model.ChatMessage>()
        if (messagesJson != null) {
            for (i in 0 until messagesJson.length()) {
                val m = messagesJson.optJSONObject(i) ?: continue
                val role = m.optString("role", "user").lowercase()
                val content = m.optString("content", "")
                val sender = if (role == "assistant") com.example.data.model.SenderRole.GEMINI else com.example.data.model.SenderRole.USER
                chatMessages.add(com.example.data.model.ChatMessage(sender = sender, content = content))
            }
        }

        if (chatMessages.isEmpty()) {
            chatMessages.add(com.example.data.model.ChatMessage(sender = com.example.data.model.SenderRole.USER, content = "Hello"))
        }

        val reqId = "chatcmpl-" + java.util.UUID.randomUUID().toString().take(12)
        val createdTimestamp = System.currentTimeMillis() / 1000

        try {
            if (isStreamRequested) {
                val writer = PrintWriter(out, false)
                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: text/event-stream; charset=utf-8\r\n")
                writer.print("Cache-Control: no-cache\r\n")
                writer.print("Connection: keep-alive\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("\r\n")
                writer.flush()

                var previousLength = 0
                kotlinx.coroutines.runBlocking {
                    try {
                        com.titan.ide.network.TitanDirectEngine.executeChat(
                            messages = chatMessages,
                            modelId = requestedModel
                        ).collect { chunk ->
                            val currentText = chunk
                            if (currentText.length > previousLength) {
                                val delta = currentText.substring(previousLength)
                                previousLength = currentText.length

                                val chunkJson = org.json.JSONObject().apply {
                                    put("id", reqId)
                                    put("object", "chat.completion.chunk")
                                    put("created", createdTimestamp)
                                    put("model", requestedModel)
                                    val choice = org.json.JSONObject().apply {
                                        put("index", 0)
                                        put("delta", org.json.JSONObject().apply { put("content", delta) })
                                        put("finish_reason", org.json.JSONObject.NULL)
                                    }
                                    put("choices", org.json.JSONArray().put(choice))
                                }
                                val line = "data: ${chunkJson.toString()}\n\n"
                                out.write(line.toByteArray(Charsets.UTF_8))
                                out.flush()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Streaming collect error: ${e.message}")
                    }
                }

                // Send finish chunk and [DONE]
                val finishJson = org.json.JSONObject().apply {
                    put("id", reqId)
                    put("object", "chat.completion.chunk")
                    put("created", createdTimestamp)
                    put("model", requestedModel)
                    val choice = org.json.JSONObject().apply {
                        put("index", 0)
                        put("delta", org.json.JSONObject())
                        put("finish_reason", "stop")
                    }
                    put("choices", org.json.JSONArray().put(choice))
                }
                out.write("data: ${finishJson.toString()}\n\n".toByteArray(Charsets.UTF_8))
                out.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                out.flush()

            } else {
                // Non-streaming: accumulate full response
                val fullResponseBuilder = java.lang.StringBuilder()
                kotlinx.coroutines.runBlocking {
                    try {
                        com.titan.ide.network.TitanDirectEngine.executeChat(
                            messages = chatMessages,
                            modelId = requestedModel
                        ).collect { chunk ->
                            if (chunk.isNotEmpty()) {
                                fullResponseBuilder.setLength(0)
                                fullResponseBuilder.append(chunk)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Chat execute error: ${e.message}")
                    }
                }

                val finalContent = fullResponseBuilder.toString().ifEmpty {
                    "⚠️ Error: No response received from Gemini engine."
                }

                val responseJson = org.json.JSONObject().apply {
                    put("id", reqId)
                    put("object", "chat.completion")
                    put("created", createdTimestamp)
                    put("model", requestedModel)
                    val choice = org.json.JSONObject().apply {
                        put("index", 0)
                        put("message", org.json.JSONObject().apply {
                            put("role", "assistant")
                            put("content", finalContent)
                        })
                        put("finish_reason", "stop")
                    }
                    put("choices", org.json.JSONArray().put(choice))
                    put("usage", org.json.JSONObject().apply {
                        put("prompt_tokens", 10)
                        put("completion_tokens", (finalContent.length / 4).coerceAtLeast(1))
                        put("total_tokens", 10 + (finalContent.length / 4).coerceAtLeast(1))
                    })
                }

                val respBytes = responseJson.toString().toByteArray(Charsets.UTF_8)
                val writer = PrintWriter(out, false)
                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: application/json; charset=utf-8\r\n")
                writer.print("Content-Length: ${respBytes.size}\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("Connection: close\r\n")
                writer.print("\r\n")
                writer.flush()
                out.write(respBytes)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "API Gateway execution error: ${e.message}", e)
            val errJson = """{"error":{"message":"Titan Local Gateway error: ${e.message?.replace("\"", "'")}","type":"internal_server_error"}}"""
            val errBytes = errJson.toByteArray(Charsets.UTF_8)
            val writer = PrintWriter(out, false)
            writer.print("HTTP/1.1 500 Internal Server Error\r\n")
            writer.print("Content-Type: application/json; charset=utf-8\r\n")
            writer.print("Content-Length: ${errBytes.size}\r\n")
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.flush()
            out.write(errBytes)
            out.flush()
        }
    }

    private fun handleModelsList(out: OutputStream) {
        val modelsJson = """
        {
          "object": "list",
          "data": [
            {"id": "gemini-2.5-pro", "object": "model", "owned_by": "titan-gateway"},
            {"id": "gemini-2.5-flash", "object": "model", "owned_by": "titan-gateway"},
            {"id": "gemini-2.0-flash-live", "object": "model", "owned_by": "titan-gateway"},
            {"id": "gemini-2.0-flash-thinking", "object": "model", "owned_by": "titan-gateway"},
            {"id": "veo-3.1", "object": "model", "owned_by": "titan-gateway"},
            {"id": "imagen-3", "object": "model", "owned_by": "titan-gateway"}
          ]
        }
        """.trimIndent()
        val bytes = modelsJson.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out, false)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    private fun handleStatus(out: OutputStream) {
        val statusJson = """
        {
          "status": "online",
          "service": "Titan Local API Gateway",
          "version": "2.5.0",
          "port": $port,
          "cloud_endpoint": "$CLOUD_PROXY_URL",
          "chat_completions_url": "http://127.0.0.1:$port/v1/chat/completions"
        }
        """.trimIndent()
        val bytes = statusJson.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out, false)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".html", ignoreCase = true) || fileName.endsWith(".htm", ignoreCase = true) -> "text/html; charset=utf-8"
            fileName.endsWith(".css", ignoreCase = true) -> "text/css; charset=utf-8"
            fileName.endsWith(".js", ignoreCase = true) -> "application/javascript; charset=utf-8"
            fileName.endsWith(".json", ignoreCase = true) -> "application/json; charset=utf-8"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
            fileName.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
            fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
            fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            else -> URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket: ${e.message}")
        }
    }
}

