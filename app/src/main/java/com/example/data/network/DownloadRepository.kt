package com.example.data.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.OfflineStorageManager
import com.example.data.local.db.DownloadDao
import com.example.data.local.db.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.net.URI
import java.util.UUID

class DownloadRepository(
    context: Context,
    private val downloadDao: DownloadDao,
    private val storageManager: OfflineStorageManager
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    val allDownloads: Flow<List<DownloadTaskEntity>> = downloadDao.observeAll()
    val recentDownloads: Flow<List<DownloadTaskEntity>> = downloadDao.observeRecent()

    fun observeDownload(id: String): Flow<DownloadTaskEntity?> = downloadDao.observeById(id)

    suspend fun enqueueDownload(rawUrl: String, customFileName: String? = null): Result<String> =
        runCatching {
            val normalizedUrl = rawUrl.trim()
            require(
                normalizedUrl.startsWith("https://", ignoreCase = true) ||
                    normalizedUrl.startsWith("http://", ignoreCase = true)
            ) {
                "Enter a valid HTTP or HTTPS URL"
            }

            val derivedName = customFileName?.trim()?.takeIf { it.isNotBlank() }
                ?: deriveFileNameFromUrl(normalizedUrl)
            val safeName = OfflineStorageManager.sanitizeFileName(derivedName)
            val uniqueName = resolveUniqueName(safeName)
            val targetFile = File(storageManager.vaultDir, uniqueName)
            val mimeType = OfflineStorageManager.guessMimeType(uniqueName)
            val category = OfflineStorageManager.categorizeFile(uniqueName, mimeType)

            val id = UUID.randomUUID().toString()
            val entity = DownloadTaskEntity(
                id = id,
                url = normalizedUrl,
                fileName = uniqueName,
                mimeType = mimeType,
                category = category,
                totalBytes = -1L,
                downloadedBytes = 0L,
                status = DownloadWorker.STATUS_QUEUED,
                localPath = targetFile.absolutePath,
                errorMessage = null
            )
            downloadDao.upsert(entity)
            scheduleWorker(id)
            id
        }

    suspend fun pauseDownload(id: String) {
        val existing = downloadDao.getById(id) ?: return
        downloadDao.updateStatus(
            id = id,
            status = DownloadWorker.STATUS_PAUSED,
            error = null,
            localPath = existing.localPath
        )
        workManager.cancelUniqueWork(workName(id))
    }

    suspend fun resumeDownload(id: String) {
        val existing = downloadDao.getById(id) ?: return
        downloadDao.updateStatus(
            id = id,
            status = DownloadWorker.STATUS_QUEUED,
            error = null,
            localPath = existing.localPath
        )
        scheduleWorker(id)
    }

    suspend fun cancelDownload(id: String) {
        val existing = downloadDao.getById(id) ?: return
        downloadDao.updateStatus(
            id = id,
            status = DownloadWorker.STATUS_CANCELLED,
            error = null,
            localPath = existing.localPath
        )
        workManager.cancelUniqueWork(workName(id))
        if (existing.localPath.isNotBlank()) {
            val file = File(existing.localPath)
            if (file.exists()) file.delete()
        }
    }

    suspend fun deleteDownload(task: DownloadTaskEntity) {
        workManager.cancelUniqueWork(workName(task.id))
        if (task.localPath.isNotBlank()) {
            val file = File(task.localPath)
            if (file.exists()) file.delete()
        }
        downloadDao.deleteById(task.id)
    }

    fun buildOpenIntent(task: DownloadTaskEntity): Intent? {
        if (task.localPath.isBlank()) return null
        val file = File(task.localPath)
        if (!file.exists()) return null
        val uri: Uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, task.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildShareIntent(task: DownloadTaskEntity): Intent? {
        if (task.localPath.isBlank()) return null
        val file = File(task.localPath)
        if (!file.exists()) return null
        val uri: Uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = task.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, task.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share ${task.fileName}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun scheduleWorker(id: String) {
        val input = Data.Builder()
            .putString(DownloadWorker.KEY_DOWNLOAD_ID, id)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(input)
            .build()
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(id: String) = "wifigo_download_$id"

    private fun deriveFileNameFromUrl(url: String): String {
        val pathSegment = try {
            URI(url).path?.substringAfterLast('/')
        } catch (_: Exception) {
            null
        }
        return pathSegment?.takeIf { it.isNotBlank() && it.contains('.') }
            ?: "download_${System.currentTimeMillis()}.bin"
    }

    private fun resolveUniqueName(baseName: String): String {
        var candidate = File(storageManager.vaultDir, baseName)
        if (!candidate.exists()) return baseName
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val ext = if (dot > 0) baseName.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            val next = "${stem}_$index$ext"
            candidate = File(storageManager.vaultDir, next)
            if (!candidate.exists()) return next
            index++
        }
        return baseName
    }
}
