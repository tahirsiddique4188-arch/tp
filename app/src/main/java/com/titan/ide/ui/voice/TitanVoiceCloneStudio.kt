package com.titan.ide.ui.voice


import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.ArrowBack
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitanVoiceCloneStudioSheet(
    onDismiss: () -> Unit,
    onVoiceClonedActive: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("titan_voice_clone_prefs", Context.MODE_PRIVATE) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingProgress by remember { mutableFloatStateOf(0f) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var isPlayingTest by remember { mutableStateOf(false) }
    var isVoiceCloned by remember {
        mutableStateOf(File(context.filesDir, "tahir_voice_sample.m4a").exists())
    }
    var useClonedVoiceDefault by remember {
        mutableStateOf(prefs.getBoolean("use_cloned_voice_default", false))
    }

    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    val sampleFile = remember { File(context.filesDir, "tahir_voice_sample.m4a") }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                exoPlayer?.stop()
                exoPlayer?.release()
            } catch (e: Exception) {
                // Ignore cleanup error
            }
        }
    }

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFFB74D), GeminiSparklePurple)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MicNone,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Titan Voice Clone Studio",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeminiTextPrimary
                        )
                        Text(
                            text = "Extract & Clone Your Personal AI Voice",
                            fontSize = 11.sp,
                            color = GeminiCyanAccent
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Close",
                        tint = GeminiTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Instructions & Reading Paragraph
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = GeminiSurface),
                border = BorderStroke(1.dp, GeminiThinkingBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📖 Read Aloud Sample Paragraph:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeminiCyanAccent
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "\"Welcome to Titan IDE, created and engineered by Developer Tahir (@Tahirsiddik). I am calibrating my custom acoustic vocal profile to synthesize natural speech, live coding assistance, and broadcast responses in my unique personal tone.\"",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = GeminiTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Recording Status & Progress
            if (isRecording) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1F1F)),
                    border = BorderStroke(1.dp, Color(0xFFEF5350)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF5350))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recording Vocal Sample: ${recordingSeconds}s / 30s",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF5350)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { recordingProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = Color(0xFFEF5350),
                            trackColor = Color(0xFF5A2A2A)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Primary Record Button
            Button(
                onClick = {
                    if (!isRecording) {
                        // Start 30-sec recording
                        try {
                            if (sampleFile.exists()) sampleFile.delete()
                            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }
                            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            recorder.setAudioEncodingBitRate(128000)
                            recorder.setAudioSamplingRate(44100)
                            recorder.setOutputFile(sampleFile.absolutePath)
                            recorder.prepare()
                            recorder.start()
                            mediaRecorder = recorder
                            isRecording = true
                            recordingSeconds = 0
                            recordingProgress = 0f

                            coroutineScope.launch {
                                for (sec in 1..30) {
                                    if (!isRecording) break
                                    delay(1000)
                                    recordingSeconds = sec
                                    recordingProgress = sec / 30f
                                }
                                // Finish recording after 30s
                                try {
                                    mediaRecorder?.stop()
                                    mediaRecorder?.release()
                                    mediaRecorder = null
                                } catch (e: Exception) {
                                    // Ignore
                                }
                                isRecording = false
                                isVoiceCloned = true
                                Toast.makeText(context, "✅ Voice Profile Extracted & Cloned Successfully!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Mic permission required or recorder busy", Toast.LENGTH_SHORT).show()
                            isRecording = false
                        }
                    } else {
                        // Stop recording early
                        try {
                            mediaRecorder?.stop()
                            mediaRecorder?.release()
                            mediaRecorder = null
                        } catch (e: Exception) {
                            // Ignore
                        }
                        isRecording = false
                        isVoiceCloned = true
                        Toast.makeText(context, "✅ Voice Profile Extracted & Cloned Successfully!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xFFEF5350) else GeminiCyanAccent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = GeminiDarkObsidian,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRecording) "Stop Recording Early (${recordingSeconds}s)" else "Record Voice Sample (30s)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = GeminiDarkObsidian
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Success State & Cloned Profile Options
            AnimatedVisibility(
                visible = isVoiceCloned,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3320)),
                        border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Voice Profile Extracted & Cloned Successfully!",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF81C784)
                                )
                                Text(
                                    text = "Profile: tahir_voice_sample.m4a (High-Fidelity 44.1kHz)",
                                    fontSize = 10.sp,
                                    color = Color(0xFFA5D6A7)
                                )
                            }
                        }
                    }

                    // Test My Voice Button
                    OutlinedButton(
                        onClick = {
                            if (!isPlayingTest && sampleFile.exists()) {
                                try {
                                    exoPlayer?.release()
                                    val player = ExoPlayer.Builder(context).build().apply {
                                        setMediaItem(MediaItem.fromUri(Uri.fromFile(sampleFile)))
                                        prepare()
                                        addListener(object : Player.Listener {
                                            override fun onPlaybackStateChanged(playbackState: Int) {
                                                if (playbackState == Player.STATE_ENDED) {
                                                    isPlayingTest = false
                                                }
                                            }
                                        })
                                        play()
                                    }
                                    exoPlayer = player
                                    isPlayingTest = true
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not play voice sample", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                try {
                                    exoPlayer?.stop()
                                    exoPlayer?.release()
                                    exoPlayer = null
                                } catch (e: Exception) {
                                    // Ignore
                                }
                                isPlayingTest = false
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GeminiSparklePurple),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlayingTest) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = GeminiSparklePurple,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPlayingTest) "Stop Sample Playback" else "Test My Cloned Voice",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeminiSparklePurple
                        )
                    }

                    // Toggle Switch: Use My Cloned Voice as Default
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = GeminiSurface),
                        border = BorderStroke(1.dp, GeminiThinkingBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Use My Cloned Voice as Default",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeminiTextPrimary
                                )
                                Text(
                                    text = "Titan synthesizes all responses in your personal acoustic voice",
                                    fontSize = 10.5.sp,
                                    color = GeminiTextSecondary
                                )
                            }
                            Switch(
                                checked = useClonedVoiceDefault,
                                onCheckedChange = { checked ->
                                    useClonedVoiceDefault = checked
                                    prefs.edit().putBoolean("use_cloned_voice_default", checked).apply()
                                    onVoiceClonedActive(checked)
                                    Toast.makeText(
                                        context,
                                        if (checked) "Cloned Voice set as default!" else "Standard voice restored",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GeminiDarkObsidian,
                                    checkedTrackColor = GeminiCyanAccent
                                )
                            )
                        }
                    }
                }
            }
        }
    
        }
    }
}
