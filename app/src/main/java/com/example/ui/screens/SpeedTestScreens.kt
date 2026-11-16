package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.db.SpeedTestRecordEntity
import com.example.data.network.SpeedTestPhase
import com.example.data.network.SpeedTestProgress
import com.example.ui.components.ElevatedInfoCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SpeedGauge
import com.example.ui.components.StatusPill
import com.example.ui.components.WiFiGoTopBar
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SignalExcellent
import com.example.ui.theme.SignalWeak
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SpeedTestScreen(
    progress: SpeedTestProgress,
    configuredEndpoint: String,
    onStartTest: () -> Unit,
    onCancelTest: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val isRunning = progress.phase in setOf(
        SpeedTestPhase.PING,
        SpeedTestPhase.DOWNLOAD,
        SpeedTestPhase.UPLOAD
    )

    val phaseText = when (progress.phase) {
        SpeedTestPhase.IDLE -> "Ready"
        SpeedTestPhase.PING -> strings.testingPing
        SpeedTestPhase.DOWNLOAD -> strings.testingDownload
        SpeedTestPhase.UPLOAD -> strings.testingUpload
        SpeedTestPhase.COMPLETED -> "Completed"
        SpeedTestPhase.ERROR -> "Failed"
    }

    Scaffold(
        topBar = {
            WiFiGoTopBar(
                title = strings.speedTest,
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.testTag("speed_history_button")
                    ) {
                        Icon(Icons.Default.History, contentDescription = strings.testHistory)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SpeedGauge(
                            mbps = if (progress.phase == SpeedTestPhase.COMPLETED) progress.downloadMbps else progress.currentMbps,
                            phaseLabel = phaseText
                        )
                        if (isRunning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress.progressFraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SpeedStatTile(strings.pingMs, String.format("%.1f ms", progress.pingMs))
                            SpeedStatTile(strings.jitterMs, String.format("%.1f ms", progress.jitterMs))
                            SpeedStatTile(strings.downloadMbps, String.format("%.2f Mbps", progress.downloadMbps))
                            SpeedStatTile(strings.uploadMbps, String.format("%.2f Mbps", progress.uploadMbps))
                        }
                    }
                }
            }

            if (progress.phase == SpeedTestPhase.ERROR && !progress.errorMessage.isNullOrBlank()) {
                item {
                    ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                        StatusPill(text = "Measurement Unavailable", color = SignalWeak)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = progress.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onOpenSettings) {
                            Text(strings.configureEndpoint)
                        }
                    }
                }
            }

            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Text(strings.configureEndpoint, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = configuredEndpoint.ifBlank { "No endpoint configured" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (isRunning) {
                        OutlinedButton(
                            onClick = onCancelTest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(strings.cancel)
                        }
                    } else {
                        Button(
                            onClick = onStartTest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("start_speed_test_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text(strings.startTest)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedStatTile(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SpeedTestHistoryScreen(
    records: List<SpeedTestRecordEntity>,
    onDeleteRecord: (Long) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            WiFiGoTopBar(
                title = strings.testHistory,
                onBack = onBack,
                actions = {
                    if (records.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text("Clear")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (records.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Speed,
                        title = strings.testHistory,
                        message = "No speed tests recorded yet. Run a live speed test to store actual measurements.",
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                }
            } else {
                items(records, key = { it.id }) { rec ->
                    ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusPill(text = rec.networkType, color = SignalExcellent)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = rec.ssid,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "↓ ${String.format("%.2f", rec.downloadMbps)} Mbps  •  ↑ ${String.format("%.2f", rec.uploadMbps)} Mbps",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Ping ${String.format("%.1f", rec.pingMs)} ms • Jitter ${String.format("%.1f", rec.jitterMs)} ms • ${dateFormat.format(Date(rec.timestampMs))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteRecord(rec.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = strings.delete)
                            }
                        }
                    }
                }
            }
        }
    }
}
