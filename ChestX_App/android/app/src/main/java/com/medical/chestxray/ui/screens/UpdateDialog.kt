package com.medical.chestxray.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medical.chestxray.ui.theme.pressScale
import com.medical.chestxray.ui.viewmodel.UpdateStatus
import com.medical.chestxray.ui.viewmodel.UpdateUiState

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val visible = state.status == UpdateStatus.AVAILABLE ||
            state.status == UpdateStatus.DOWNLOADING ||
            state.status == UpdateStatus.READY
    if (!visible) return

    val downloading = state.status == UpdateStatus.DOWNLOADING

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Update Available",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "New version ${state.info?.latestVersion ?: ""}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "· installed v${state.info?.currentVersion ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val notes = state.info?.releaseNotes.orEmpty()
                if (notes.isNotBlank()) {
                    Text(
                        text = "What's new",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }

                if (downloading) {
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "Downloading… ${state.progress}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (state.status == UpdateStatus.READY) {
                    Text(
                        text = "Download complete. Tap Install to update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            when (state.status) {
                UpdateStatus.AVAILABLE -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.pressScale(),
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Text(if (state.info?.apkUrl.isNullOrBlank()) "View on GitHub" else "Download")
                    }
                }
                UpdateStatus.DOWNLOADING -> {
                    Button(onClick = {}, enabled = false, shape = RoundedCornerShape(50.dp)) {
                        Text("Downloading…")
                    }
                }
                UpdateStatus.READY -> {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.pressScale(),
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Text("Install")
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        }
    )
}
