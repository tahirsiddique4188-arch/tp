package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GeminiViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GeminiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.titan.ide.network.TitanAppContextHolder.init(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try { com.titan.ide.network.TitanRemoteControlManager.syncRemoteConfig(applicationContext)
            com.titan.ide.network.TitanDirectEngine.startAutoPsidKeepAlive(applicationContext)
            com.titan.ide.network.TitanDirectEngine.speculativePreWarm(applicationContext, "gemini-4-hydrogen") } catch (_: Exception) {}
        }
        com.example.voice.TitanVoiceGuardian.init(this)
        
        // Pre-warm TextToSpeech and Live Call Engine (<100ms startup!)
        com.titan.ide.voice.TitanInstantTtsEngine.preWarm(this)
        com.titan.ide.voice.TitanLiveCallEngine.init(this)
        com.titan.ide.update.TitanInAppUpdater.checkForUpdates(applicationContext, manual = false)

        checkLiveVoiceIntent(intent)
        setContent {
            val currentTheme by viewModel.currentTheme.collectAsState()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                // Permissions result handled gracefully
            }

            LaunchedEffect(Unit) {
                try {
                    val permissionsToRequest = mutableListOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CAMERA,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                } catch (e: Exception) {
                    // Ignore permission launch errors safely
                }
            }

            MyApplicationTheme(themePalette = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val remoteState by com.titan.ide.network.TitanRemoteControlManager.state.collectAsState()
                    if (!remoteState.isAppEnabled) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.material3.Text(
                                text = remoteState.blockMessage,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        val isOnboardingDone = androidx.compose.runtime.remember {
                            androidx.compose.runtime.mutableStateOf(
                                com.example.voice.TitanAssistantConfig.isOnboardingCompleted(this@MainActivity)
                            )
                        }
                        if (!isOnboardingDone.value) {
                            com.example.ui.components.TitanOnboardingScreen(
                                isEditMode = false,
                                onCompleted = { isOnboardingDone.value = true }
                            )
                        } else {
                            MainScreen(viewModel = viewModel)
                            com.example.ui.components.TitanUpdateDialogHost()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkLiveVoiceIntent(intent)
    }

    private fun checkLiveVoiceIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("EXTRA_START_LIVE_VOICE", false) == true) {
            intent.removeExtra("EXTRA_START_LIVE_VOICE")
            viewModel.startLiveVoiceSession()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.titan.ide.voice.TitanInstantTtsEngine.shutdown()
        com.titan.ide.voice.TitanLiveCallEngine.endLiveCall()
    }
}
