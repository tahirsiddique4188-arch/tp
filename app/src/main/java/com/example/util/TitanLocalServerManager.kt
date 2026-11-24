package com.example.util

import com.example.data.local.server.LocalHttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object TitanLocalServerManager {

    private val server = LocalHttpServer(port = 8080)

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _activeProjectName = MutableStateFlow<String?>("Default Workspace")
    val activeProjectName: StateFlow<String?> = _activeProjectName.asStateFlow()

    val serverPort: Int
        get() = server.port

    val localApiBaseUrl: String
        get() = "http://127.0.0.1:$serverPort/v1"

    val localChatCompletionsUrl: String
        get() = "http://127.0.0.1:$serverPort/v1/chat/completions"

    val CLOUD_CHAT_COMPLETIONS_URL: String
        get() = TitanSecurityShield.getChatCompletionsUrl()

    fun startServer(projectFolder: File? = null) {
        if (projectFolder != null) {
            server.activeProjectDir = projectFolder
            _activeProjectName.value = projectFolder.name
        }
        server.start()
        _isServerRunning.value = true
    }

    fun stopServer() {
        server.stop()
        _isServerRunning.value = false
    }

    fun toggleServer(projectFolder: File? = null) {
        if (server.isRunning) {
            stopServer()
        } else {
            startServer(projectFolder)
        }
    }

    fun setActiveProjectDir(projectFolder: File) {
        server.activeProjectDir = projectFolder
        _activeProjectName.value = projectFolder.name
        if (!server.isRunning) {
            startServer(projectFolder)
        }
    }

    fun getActiveProjectDir(): File? = server.activeProjectDir

    fun updateInMemoryCode(html: String, css: String, js: String) {
        server.htmlContent = html
        server.cssContent = css
        server.jsContent = js
    }
}
