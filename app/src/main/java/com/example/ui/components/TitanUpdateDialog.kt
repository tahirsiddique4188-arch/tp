package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.titan.ide.update.TitanInAppUpdater
import com.titan.ide.update.UpdateUiState

@Composable
fun TitanUpdateDialogHost() {
    val context = LocalContext.current
    val updateState by TitanInAppUpdater.state.collectAsState()

    when (val s = updateState) {
        is UpdateUiState.Available -> {
            AlertDialog(
                onDismissRequest = { if (!s.info.forceUpdate) TitanInAppUpdater.dismiss() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Update Available (v${s.info.versionName})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Current: v${BuildConfig.VERSION_NAME}  ->  New: v${s.info.versionName}",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = s.info.notes,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Installs directly over your current app — no uninstall needed.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { TitanInAppUpdater.downloadAndInstall(context, s.info) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Update Now", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = if (!s.info.forceUpdate) {
                    {
                        TextButton(onClick = { TitanInAppUpdater.dismiss() }) {
                            Text("Later")
                        }
                    }
                } else null
            )
        }

        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(
                        text = "Downloading v${s.info.versionName}...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { s.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${(s.progress * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {}
            )
        }

        is UpdateUiState.UpToDate -> {
            AlertDialog(
                onDismissRequest = { TitanInAppUpdater.dismiss() },
                title = { Text("You're Up to Date", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                text = { Text("Titan IDE v${BuildConfig.VERSION_NAME} is already the latest version.") },
                confirmButton = {
                    TextButton(onClick = { TitanInAppUpdater.dismiss() }) { Text("OK") }
                }
            )
        }

        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = { TitanInAppUpdater.dismiss() },
                title = { Text("Update Notice", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                text = { Text(s.message) },
                confirmButton = {
                    TextButton(onClick = { TitanInAppUpdater.dismiss() }) { Text("OK") }
                }
            )
        }

        else -> {}
    }
}
