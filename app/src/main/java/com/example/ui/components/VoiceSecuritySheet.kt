package com.example.ui.components


import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.ArrowBack
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalTitanTheme
import com.example.voice.TitanVoiceGuardian

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSecuritySheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalTitanTheme.current

    val isGuardianEnabled by TitanVoiceGuardian.isGuardianEnabled.collectAsState()
    val isAwayMode by TitanVoiceGuardian.isAwayModeActive.collectAsState()
    val isDrivingMode by TitanVoiceGuardian.isDrivingModeActive.collectAsState()
    val enrolledCount by TitanVoiceGuardian.enrolledCount.collectAsState()
    val alarmTriggered by TitanVoiceGuardian.alarmTriggered.collectAsState()

    var sosContactsText by remember {
        mutableStateOf(TitanVoiceGuardian.getSosContacts(context).joinToString(", "))
    }
    var isEditingSos by remember { mutableStateOf(false) }
    var showVoiceCloneStudio by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = theme.background,
            contentColor = theme.textPrimary
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
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
                                .background(theme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Voice Security & Biometric Guardian",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.4.sp,
                                color = theme.textPrimary
                            )
                            Text(
                                text = "Developer Tahir (@Tahirsiddik) Protected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Light,
                                color = theme.textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Close",
                            tint = theme.textSecondary
                        )
                    }
                }
            }

            // Dynamic User-Editable Wake Word Card
            item {
                val titanPrefs = remember { context.getSharedPreferences("titan_prefs", android.content.Context.MODE_PRIVATE) }
                var customWakeWord by remember {
                    mutableStateOf(titanPrefs.getString("custom_wake_word", "Hey Titan") ?: "Hey Titan")
                }
                var isSavedFeedback by remember { mutableStateOf(false) }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Wake Word",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hands-free standby hotword",
                            fontSize = 11.5.sp,
                            color = theme.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customWakeWord,
                                onValueChange = { 
                                    customWakeWord = it
                                    isSavedFeedback = false
                                },
                                label = { Text("Wake Word", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val clean = customWakeWord.trim().ifBlank { "Hey Titan" }
                                    titanPrefs.edit().putString("custom_wake_word", clean).apply()
                                    val name = clean.replace("Hey", "", ignoreCase = true)
                                        .replace("Hello", "", ignoreCase = true)
                                        .trim().ifBlank { "Titan" }
                                    com.example.voice.TitanAssistantConfig.setAssistantName(context, name)
                                    com.example.voice.TitanWakeWordService.restartWithNewName(context)
                                    isSavedFeedback = true
                                    android.widget.Toast.makeText(context, "Saved: '$clean'", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        if (isSavedFeedback) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Active: '$customWakeWord'",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }

            // Alarm Alert banner if active
            if (alarmTriggered) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Intruder Alarm Active!",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                            Button(
                                onClick = { TitanVoiceGuardian.stopIntruderAlarm() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.heightIn(min = 36.dp, max = 40.dp)
                            ) {
                                Text("Stop Alarm", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Voice Clone Studio Card (Extract & Clone Personal AI Voice)
            item {
                Surface(
                    onClick = { showVoiceCloneStudio = true },
                    shape = RoundedCornerShape(12.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MicNone,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Voice Clone Studio",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = theme.textPrimary
                                )
                                Text(
                                    text = "Clone your personal voice",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Light,
                                    color = theme.textSecondary
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF)
                        )
                    }
                }
            }

            // 1. Away / Guard Mode Card
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Guard Mode",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = theme.textPrimary
                                )
                                Text(
                                    text = "Intruder siren & lock screen",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Light,
                                    color = theme.textSecondary
                                )
                            }
                            Switch(
                                checked = isAwayMode,
                                onCheckedChange = { active ->
                                    TitanVoiceGuardian.setAwayMode(active, context)
                                    val msg = if (active) "Titan Away Mode armed." else "Titan Away Mode disarmed."
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        if (isAwayMode) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        TitanVoiceGuardian.triggerIntruderDefense(context) {}
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.heightIn(min = 36.dp, max = 40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test Intruder Defense", fontSize = 12.sp, color = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }

            // 2. Owner Voice Enrollment (5 Sentences)
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Voice Profile",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = theme.textPrimary
                                )
                                Text(
                                    text = "$enrolledCount/5 verified",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Light,
                                    color = theme.primary
                                )
                            }
                            Icon(
                                imageVector = if (enrolledCount >= 5) Icons.Default.CheckCircle else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (enrolledCount >= 5) Color(0xFF10B981) else theme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TitanVoiceGuardian.ENROLLMENT_SENTENCES.forEachIndexed { index, sentence ->
                            val isRecorded = index < enrolledCount
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = theme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(0.5.dp, if (isRecorded) Color(0xFF10B981).copy(alpha = 0.4f) else theme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${index + 1}. \"$sentence\"",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = if (isRecorded) theme.textPrimary else theme.textSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (isRecorded) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Verified",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                TitanVoiceGuardian.recordSentenceSample(index, context)
                                                Toast.makeText(context, "Sample ${index + 1} enrolled for Developer Tahir", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.heightIn(min = 32.dp, max = 36.dp)
                                        ) {
                                            Text("Enroll", fontSize = 11.sp, color = theme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Smart SOS & Emergency Contacts
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Emergency SOS",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = theme.textPrimary
                                )
                                Text(
                                    text = "Alert to contacts",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Light,
                                    color = theme.textSecondary
                                )
                            }
                            IconButton(
                                onClick = { isEditingSos = !isEditingSos },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isEditingSos) Icons.Default.Done else Icons.Default.Edit,
                                    contentDescription = "Edit Contacts",
                                    tint = theme.primary
                                )
                            }
                        }

                        if (isEditingSos) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = sosContactsText,
                                onValueChange = {
                                    sosContactsText = it
                                    val list = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                                    TitanVoiceGuardian.saveSosContacts(list, context)
                                },
                                label = { Text("Emergency Numbers (comma-separated)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Emergency Numbers: $sosContactsText",
                                fontSize = 12.sp,
                                color = theme.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val res = TitanVoiceGuardian.triggerTitanSos(context)
                                Toast.makeText(context, res, Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 36.dp, max = 40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sos,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Trigger Titan SOS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }

            // 4. Driving Mode Card
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Driving Mode",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = theme.textPrimary
                            )
                            Text(
                                text = "Auto-respond while driving",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Light,
                                color = theme.textSecondary
                            )
                        }
                        Switch(
                            checked = isDrivingMode,
                            onCheckedChange = { active ->
                                TitanVoiceGuardian.setDrivingMode(active, context)
                                val msg = if (active) "Driving Mode enabled." else "Driving Mode disabled."
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    
        }
    }

    if (showVoiceCloneStudio) {
        com.titan.ide.ui.voice.TitanVoiceCloneStudioSheet(
            onDismiss = { showVoiceCloneStudio = false }
        )
    }
}

