package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.OfflineStorageManager
import com.example.data.local.db.DownloadTaskEntity
import com.example.data.network.DownloadRepository
import com.example.data.network.DownloadWorker
import com.example.ui.components.ElevatedInfoCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.WiFiGoTopBar
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SignalExcellent
import com.example.ui.theme.SignalFair
import com.example.ui.theme.SignalWeak

@Composable
fun DownloadManagerScreen(
    downloads: List<DownloadTaskEntity>,
    onEnqueueDownload: (String, String?) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (DownloadTaskEntity) -> Unit,
    onOpenDetails: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var showAddDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var fileNameInput by remember { mutableStateOf("") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        showAddDialog = true
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        showAddDialog = true
                    }
                },
                containerColor = PrimaryBlue,
                modifier = Modifier.testTag("add_download_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = strings.newDownload, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.downloadManager, style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = { showAddDialog = true }) {
                        Text(strings.newDownload)
                    }
                }
            }

            if (downloads.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Download,
                        title = strings.downloadManager,
                        message = strings.noRecentDownloads,
                        actionLabel = strings.newDownload,
                        onAction = { showAddDialog = true },
                        modifier = Modifier.widthIn(max = 720.dp)
                    )
                }
            } else {
                items(downloads, key = { it.id }) { task ->
                    val statusColor = when (task.status) {
                        DownloadWorker.STATUS_COMPLETED -> SignalExcellent
                        DownloadWorker.STATUS_FAILED, DownloadWorker.STATUS_CANCELLED -> SignalWeak
                        DownloadWorker.STATUS_PAUSED -> SignalFair
                        else -> PrimaryBlue
                    }
                    ElevatedInfoCard(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .clickable { onOpenDetails(task.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${OfflineStorageManager.formatBytes(task.downloadedBytes)} / ${if (task.totalBytes > 0) OfflineStorageManager.formatBytes(task.totalBytes) else "Unknown"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(text = task.status, color = statusColor)
                        }

                        if (task.status == DownloadWorker.STATUS_RUNNING || task.status == DownloadWorker.STATUS_QUEUED) {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (task.totalBytes > 0) {
                                LinearProgressIndicator(
                                    progress = { (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }

                        if (!task.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = task.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (task.status) {
                                DownloadWorker.STATUS_RUNNING, DownloadWorker.STATUS_QUEUED -> {
                                    OutlinedButton(onClick = { onPause(task.id) }) {
                                        Icon(Icons.Default.Pause, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.pause)
                                    }
                                    OutlinedButton(onClick = { onCancel(task.id) }) {
                                        Icon(Icons.Default.Cancel, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.cancel)
                                    }
                                }
                                DownloadWorker.STATUS_PAUSED, DownloadWorker.STATUS_FAILED -> {
                                    Button(onClick = { onResume(task.id) }) {
                                        Icon(
                                            imageVector = if (task.status == DownloadWorker.STATUS_PAUSED) Icons.Default.PlayArrow else Icons.Default.Refresh,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (task.status == DownloadWorker.STATUS_PAUSED) strings.resume else strings.retry)
                                    }
                                    IconButton(onClick = { onDelete(task) }) {
                                        Icon(Icons.Default.Delete, contentDescription = strings.delete)
                                    }
                                }
                                else -> {
                                    IconButton(onClick = { onDelete(task) }) {
                                        Icon(Icons.Default.Delete, contentDescription = strings.delete)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(strings.newDownload) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Direct File URL (HTTPS)") },
                        placeholder = { Text(strings.downloadUrlHint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fileNameInput,
                        onValueChange = { fileNameInput = it },
                        label = { Text("Optional File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEnqueueDownload(urlInput, fileNameInput.ifBlank { null })
                        urlInput = ""
                        fileNameInput = ""
                        showAddDialog = false
                    }
                ) { Text(strings.downloadManager) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
fun DownloadDetailsScreen(
    task: DownloadTaskEntity?,
    downloadRepository: DownloadRepository,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (DownloadTaskEntity) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    Scaffold(
        topBar = { WiFiGoTopBar(title = "Download Details", onBack = onBack) }
    ) { padding ->
        if (task == null) {
            EmptyStateCard(
                icon = Icons.Default.Download,
                title = "Download Not Found",
                message = "This download record no longer exists.",
                actionLabel = strings.back,
                onAction = onBack,
                modifier = Modifier.padding(padding).padding(16.dp)
            )
        } else {
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
                        Text(task.fileName, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(10.dp))
                        DetailRow("Status", task.status)
                        DetailRow("Category", task.category)
                        DetailRow("Downloaded", OfflineStorageManager.formatBytes(task.downloadedBytes))
                        DetailRow(
                            "Total Size",
                            if (task.totalBytes > 0) OfflineStorageManager.formatBytes(task.totalBytes) else "Unknown"
                        )
                        DetailRow("Source URL", task.url)
                        if (!task.errorMessage.isNullOrBlank()) {
                            DetailRow("Error", task.errorMessage)
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.widthIn(max = 640.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (task.status == DownloadWorker.STATUS_COMPLETED) {
                            Button(
                                onClick = {
                                    val intent = downloadRepository.buildOpenIntent(task)
                                    if (intent != null) {
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.open)
                            }
                            OutlinedButton(
                                onClick = {
                                    downloadRepository.buildShareIntent(task)?.let {
                                        context.startActivity(it)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.share)
                            }
                        } else if (task.status == DownloadWorker.STATUS_RUNNING) {
                            OutlinedButton(
                                onClick = { onPause(task.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.pause)
                            }
                        } else if (task.status == DownloadWorker.STATUS_PAUSED || task.status == DownloadWorker.STATUS_FAILED) {
                            Button(
                                onClick = { onResume(task.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.resume)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                onDelete(task)
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.delete, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
