package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.DeviceStorageStats
import com.example.data.local.OfflineStorageManager
import com.example.data.local.db.LocalFileItemEntity
import com.example.ui.components.ElevatedInfoCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.WiFiGoTopBar
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PrimaryBlue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OfflineStorageScreen(
    files: List<LocalFileItemEntity>,
    selectedCategory: String,
    searchQuery: String,
    storageStats: DeviceStorageStats,
    onSelectCategory: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onImportSafUri: (android.net.Uri) -> Unit,
    onRefreshStats: () -> Unit,
    onOpenFileDetails: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportSafUri(it) }
    }

    LaunchedEffect(Unit) { onRefreshStats() }

    val usedRatio = if (storageStats.totalBytes > 0) {
        ((storageStats.totalBytes - storageStats.availableBytes).toFloat() / storageStats.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { safLauncher.launch(arrayOf("*/*")) },
                containerColor = PrimaryBlue,
                modifier = Modifier.testTag("saf_import_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = strings.importFile, tint = MaterialTheme.colorScheme.onPrimary)
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
                Column(modifier = Modifier.widthIn(max = 720.dp)) {
                    Text(strings.offlineStorage, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    ElevatedInfoCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.storageUsage, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Vault: ${OfflineStorageManager.formatBytes(storageStats.vaultUsedBytes)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { usedRatio },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${OfflineStorageManager.formatBytes(storageStats.availableBytes)} free of ${OfflineStorageManager.formatBytes(storageStats.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text(strings.searchFiles) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OfflineStorageManager.CATEGORIES.forEach { cat ->
                            val label = when (cat) {
                                "All" -> strings.allCategories
                                "Documents" -> strings.documents
                                "Images" -> strings.images
                                "Videos" -> strings.videos
                                "Audio" -> strings.audio
                                else -> strings.otherFiles
                            }
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { onSelectCategory(cat) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            if (files.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Folder,
                        title = strings.offlineStorage,
                        message = "No files in this category yet. Import documents using Android Storage Access Framework or download files.",
                        actionLabel = strings.importFile,
                        onAction = { safLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.widthIn(max = 720.dp)
                    )
                }
            } else {
                items(files, key = { it.id }) { file ->
                    ElevatedInfoCard(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .clickable { onOpenFileDetails(file.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = categoryIcon(file.category),
                                contentDescription = file.category,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${file.category} • ${OfflineStorageManager.formatBytes(file.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileDetailsScreen(
    file: LocalFileItemEntity?,
    storageManager: OfflineStorageManager,
    onRename: (LocalFileItemEntity, String) -> Unit,
    onDelete: (LocalFileItemEntity) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(file) { mutableStateOf(file?.name ?: "") }

    Scaffold(
        topBar = { WiFiGoTopBar(title = strings.fileInfo, onBack = onBack) }
    ) { padding ->
        if (file == null) {
            EmptyStateCard(
                icon = Icons.Default.InsertDriveFile,
                title = "File Not Found",
                message = "This file may have been deleted.",
                actionLabel = strings.back,
                onAction = onBack,
                modifier = Modifier.padding(padding).padding(16.dp)
            )
        } else {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (file.category == "Images" && !file.isSafExternal) {
                    item {
                        AsyncImage(
                            model = File(file.uriString),
                            contentDescription = file.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                                .height(220.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                item {
                    ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                        Text(file.name, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(10.dp))
                        DetailRow("Category", file.category)
                        DetailRow("Size", OfflineStorageManager.formatBytes(file.sizeBytes))
                        DetailRow("MIME Type", file.mimeType)
                        DetailRow("Added", dateFormat.format(Date(file.addedAtMs)))
                    }
                }

                item {
                    Column(
                        modifier = Modifier.widthIn(max = 640.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(storageManager.buildOpenIntent(file))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No app available to open this file type", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(strings.open)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(storageManager.buildShareIntent(file))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.share)
                            }
                            OutlinedButton(
                                onClick = { showRenameDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.rename)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onDelete(file)
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

    if (showRenameDialog && file != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(strings.rename) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(file, renameText)
                        showRenameDialog = false
                    }
                ) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

private fun categoryIcon(category: String) = when (category) {
    "Documents" -> Icons.Default.Description
    "Images" -> Icons.Default.Image
    "Videos" -> Icons.Default.VideoFile
    "Audio" -> Icons.Default.AudioFile
    else -> Icons.Default.InsertDriveFile
}
