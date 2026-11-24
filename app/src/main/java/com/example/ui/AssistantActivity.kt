package com.example.ui

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.data.model.ChatMessage
import com.example.data.model.SenderRole
import com.example.data.remote.GeminiApiService
import com.example.ui.components.TitanNeuralOrb
import com.example.ui.theme.GeminiCyanAccent
import com.example.ui.theme.GeminiDarkObsidian
import com.example.ui.theme.GeminiSurface
import com.example.ui.theme.GeminiTextPrimary
import com.example.ui.theme.GeminiTextSecondary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class AssistantActivity : ComponentActivity() {

        private var textToSpeech: TextToSpeech? = null
    private val apiService = GeminiApiService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initTts()

        setContent {
            var spokenText by remember { mutableStateOf("") }
            var responseText by remember { mutableStateOf("") }
            var isListening by remember { mutableStateOf(false) }
            var isSpeaking by remember { mutableStateOf(false) }
            var isThinking by remember { mutableStateOf(false) }

            fun startListening() {
                spokenText = ""
                responseText = ""
                isSpeaking = false
                isThinking = false
                isListening = true

                com.titan.ide.voice.TitanContinuousSpeechEngine.startContinuousListening(
                    context = this@AssistantActivity,
                    onPartial = { partial ->
                        if (partial.isNotBlank()) spokenText = partial
                    },
                    onResult = { text ->
                        isListening = false
                        com.titan.ide.voice.TitanContinuousSpeechEngine.stopListening()
                        if (text.isNotBlank()) {
                            spokenText = text
                            isThinking = true
                            queryTitan(text) { answer ->
                                isThinking = false
                                responseText = answer
                                isSpeaking = true
                                speakResponse(answer) {
                                    isSpeaking = false
                                }
                            }
                        }
                    }
                )
            }

            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    startListening()
                } else {
                    Toast.makeText(this@AssistantActivity, "Microphone permission required for Titan Voice", Toast.LENGTH_SHORT).show()
                }
            }

            fun requestAndListen() {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        this@AssistantActivity,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    startListening()
                } else {
                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }

            LaunchedEffect(Unit) {
                requestAndListen()
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = false, onClick = {}),
                color = Color.Black.copy(alpha = 0.88f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Close button top-right
                    IconButton(
                        onClick = { finish() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(GeminiSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = GeminiTextPrimary
                        )
                    }

                    // Open Titan Full App button top-left
                    IconButton(
                        onClick = {
                            val intent = Intent(this@AssistantActivity, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            finish()
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(GeminiSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = "Open Full App",
                            tint = GeminiCyanAccent
                        )
                    }

                    // Center Content with Neural Orb
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TitanNeuralOrb(
                            isListening = isListening,
                            isSpeaking = isSpeaking,
                            isThinking = isThinking,
                            size = 150.dp
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = if (isSpeaking) "Titan Speaking"
                            else if (isThinking) "Thinking..."
                            else if (isListening) "Listening to 'Hey Titan'..."
                            else "Tap to Speak",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeminiTextPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Spoken Query Text
                        if (spokenText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GeminiSurface.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "\"$spokenText\"",
                                    fontSize = 14.sp,
                                    color = GeminiCyanAccent,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Response Text
                        if (responseText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GeminiDarkObsidian,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = responseText,
                                    fontSize = 13.sp,
                                    color = GeminiTextPrimary,
                                    lineHeight = 19.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    // Mic tap button bottom
                    FloatingActionButton(
                        onClick = { requestAndListen() },
                        containerColor = GeminiCyanAccent,
                        contentColor = Color(0xFF131314),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Listen",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                textToSpeech?.setSpeechRate(1.1f)
            }
        }
    }

    private fun queryTitan(query: String, onResult: (String) -> Unit) {
        lifecycleScope.launch {
            val isAllowed = com.example.voice.TitanVoiceGuardian.checkVoiceSecurity(query, this@AssistantActivity) { warning ->
                onResult(warning)
                speakResponse(warning) {}
            }
            if (!isAllowed) return@launch

            val userMsg = ChatMessage(
                sender = SenderRole.USER,
                content = query,
                modelUsed = "gemini-4-hydrogen"
            )
            var fullAnswer = ""
            apiService.sendChatCompletionStream(
                messages = listOf(userMsg),
                modelId = "gemini-4-hydrogen",
                baseUrl = GeminiApiService.DEFAULT_BASE_URL
            ).collectLatest { chunk ->
                if (chunk.textContent.isNotEmpty()) {
                    fullAnswer = chunk.textContent
                    if (fullAnswer.contains("[TITAN_ACTION:")) {
                        com.example.services.TitanAccessibilityService.instance?.parseAndExecuteActions(fullAnswer, this@AssistantActivity)
                    }
                }
                if (chunk.isFinished) {
                    onResult(fullAnswer)
                }
            }
            if (fullAnswer.isNotEmpty()) {
                onResult(fullAnswer)
            }
        }
    }

    private fun speakResponse(text: String, onDone: () -> Unit) {
        com.titan.ide.voice.TitanInstantTtsEngine.speakFullResponse(
            context = this,
            rawText = text,
            onDone = onDone
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        com.titan.ide.voice.TitanContinuousSpeechEngine.stopListening()
        com.titan.ide.voice.TitanInstantTtsEngine.stopSpeech()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
