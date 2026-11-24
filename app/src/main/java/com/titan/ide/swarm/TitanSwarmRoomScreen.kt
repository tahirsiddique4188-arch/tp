package com.titan.ide.swarm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun TitanSwarmRoomScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val agents by TitanSwarmOrchestrator.subAgents.collectAsState()
    val isOrchestrating by TitanSwarmOrchestrator.isOrchestrating.collectAsState()
    val masterSummary by TitanSwarmOrchestrator.masterSummary.collectAsState()
    var goalInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            color = Color(0xFF0A0E17),
            contentColor = Color(0xFFE6EDF3)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF00E5FF))
                        }
                        Column {
                            Text(
                                text = "Titan Swarm",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF)
                            )
                            Text(
                                text = "${agents.size} Sub-Agents Active",
                                fontSize = 11.sp,
                                color = Color(0xFF8B949E)
                            )
                        }
                    }
                    IconButton(onClick = { TitanSwarmOrchestrator.clearSwarm() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color(0xFFFF6B6B))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Goal Input Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it },
                        placeholder = { Text("Enter goal for swarm...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (goalInput.isNotBlank()) {
                                val g = goalInput.trim()
                                goalInput = ""
                                TitanSwarmOrchestrator.dispatchGoalToSwarm(context, g)
                            }
                        },
                        enabled = !isOrchestrating,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        if (isOrchestrating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = "Run Swarm", tint = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sub-Agent Cards & Master Summary
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(agents, key = { it.id }) { agent ->
                        val badgeColor = when (agent.status) {
                            SubAgentStatus.SPAWNING -> Color(0xFFFFB74D)
                            SubAgentStatus.WORKING -> Color(0xFF00E5FF)
                            SubAgentStatus.COMPLETED -> Color(0xFF00E676)
                            SubAgentStatus.FAILED -> Color(0xFFFF5252)
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF131A29),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(badgeColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${agent.name} • ${agent.role}",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = agent.status.name,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Task: ${agent.taskPrompt}",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF9BA7B4)
                                )
                                if (agent.resultOutput.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = agent.resultOutput,
                                        fontSize = 12.sp,
                                        color = Color(0xFFDCE6F2)
                                    )
                                }
                            }
                        }
                    }

                    if (masterSummary.isNotBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF0D2136),
                                border = BorderStroke(1.2.dp, Color(0xFF00E5FF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("TITAN PRIME • FINAL SYNTHESIS", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(masterSummary, fontSize = 13.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
