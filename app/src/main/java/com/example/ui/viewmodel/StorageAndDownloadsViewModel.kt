package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DeviceStorageStats
import com.example.data.local.OfflineStorageManager
import com.example.data.local.db.DownloadTaskEntity
import com.example.data.local.db.LocalFileDao
import com.example.data.local.db.LocalFileItemEntity
import com.example.data.local.db.SavedWebpageDao
import com.example.data.local.db.SavedWebpageEntity
import com.example.data.network.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class StorageAndDownloadsViewModel(
    val storageManager: OfflineStorageManager,
    localFileDao: LocalFileDao,
    val downloadRepository: DownloadRepository,
    private val savedWebpageDao: SavedWebpageDao
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _storageStats = MutableStateFlow(storageManager.getStorageStats())
    val storageStats: StateFlow<DeviceStorageStats> = _storageStats.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val allFiles: StateFlow<List<LocalFileItemEntity>> = localFileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val filteredFiles: StateFlow<List<LocalFileItemEntity>> = combine(
        allFiles,
        _selectedCategory,
        _searchQuery
    ) { files, category, query ->
        files.filter { file ->
            val catMatch = category == "All" || file.category == category
            val q = query.trim().lowercase()
            val qMatch = q.isEmpty() || file.name.lowercase().contains(q)
            catMatch && qMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val downloads: StateFlow<List<DownloadTaskEntity>> = downloadRepository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val savedWebpages: StateFlow<List<SavedWebpageEntity>> = savedWebpageDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshStorageStats() {
        _storageStats.value = storageManager.getStorageStats()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun importSafDocument(uri: Uri) {
        viewModelScope.launch {
            storageManager.importSafDocument(uri)
                .onSuccess {
                    refreshStorageStats()
                    _statusMessage.value = "Imported ${it.name}"
                }
                .onFailure {
                    _statusMessage.value = it.localizedMessage ?: "Import failed"
                }
        }
    }

    fun renameFile(item: LocalFileItemEntity, newName: String) {
        viewModelScope.launch {
            storageManager.renameFile(item, newName)
                .onSuccess { _statusMessage.value = "Renamed to ${it.name}" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Rename failed" }
        }
    }

    fun deleteFile(item: LocalFileItemEntity) {
        viewModelScope.launch {
            storageManager.deleteFile(item)
            refreshStorageStats()
            _statusMessage.value = "Deleted ${item.name}"
        }
    }

    fun enqueueDownload(url: String, customFileName: String? = null) {
        viewModelScope.launch {
            downloadRepository.enqueueDownload(url, customFileName)
                .onSuccess { _statusMessage.value = "Download queued" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Invalid URL" }
        }
    }

    fun pauseDownload(id: String) {
        viewModelScope.launch { downloadRepository.pauseDownload(id) }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch { downloadRepository.resumeDownload(id) }
    }

    fun cancelDownload(id: String) {
        viewModelScope.launch { downloadRepository.cancelDownload(id) }
    }

    fun deleteDownload(task: DownloadTaskEntity) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(task)
            refreshStorageStats()
        }
    }

    fun registerSavedWebArchive(url: String, title: String, archiveFile: File) {
        viewModelScope.launch {
            val size = if (archiveFile.exists()) archiveFile.length() else 0L
            val existing = savedWebpageDao.getByUrl(url)
            savedWebpageDao.upsert(
                SavedWebpageEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    url = url,
                    title = title.ifBlank { url },
                    archivePath = archiveFile.absolutePath,
                    isBookmarked = existing?.isBookmarked ?: false,
                    fileSizeBytes = size
                )
            )
            refreshStorageStats()
            _statusMessage.value = "Saved offline archive for ${title.ifBlank { url }}"
        }
    }

    fun toggleBookmark(page: SavedWebpageEntity) {
        viewModelScope.launch {
            savedWebpageDao.setBookmarked(page.id, !page.isBookmarked)
        }
    }

    fun deleteSavedPage(page: SavedWebpageEntity) {
        viewModelScope.launch {
            if (page.archivePath.isNotBlank()) {
                val file = File(page.archivePath)
                if (file.exists()) file.delete()
            }
            savedWebpageDao.deleteById(page.id)
            refreshStorageStats()
        }
    }
}
