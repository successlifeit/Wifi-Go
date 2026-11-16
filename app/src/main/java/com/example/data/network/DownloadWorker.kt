package com.example.data.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.R
import com.example.WiFiGoApp
import com.example.data.local.OfflineStorageManager
import com.example.data.local.db.WiFiGoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val db = WiFiGoDatabase.getInstance(applicationContext)
        val dao = db.downloadDao()
        val storageManager = OfflineStorageManager(applicationContext, db.localFileDao())

        val task = dao.getById(downloadId) ?: return@withContext Result.failure()
        if (task.status == STATUS_PAUSED || task.status == STATUS_CANCELLED) {
            return@withContext Result.success()
        }

        val targetFile = if (task.localPath.isNotBlank()) {
            File(task.localPath)
        } else {
            File(storageManager.vaultDir, OfflineStorageManager.sanitizeFileName(task.fileName))
        }
        targetFile.parentFile?.mkdirs()

        val existingBytes = if (targetFile.exists()) targetFile.length() else 0L
        dao.updateProgress(
            id = downloadId,
            downloaded = existingBytes,
            total = task.totalBytes,
            status = STATUS_RUNNING
        )

        val requestBuilder = Request.Builder().url(task.url)
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    if (response.code == 416 && existingBytes > 0L && existingBytes == task.totalBytes) {
                        finalizeDownload(dao, storageManager, downloadId, targetFile, task.mimeType)
                        return@withContext Result.success()
                    }
                    dao.updateStatus(
                        id = downloadId,
                        status = STATUS_FAILED,
                        error = "Server returned HTTP ${response.code}",
                        localPath = targetFile.absolutePath
                    )
                    return@withContext Result.failure(
                        Data.Builder().putString("error", "HTTP ${response.code}").build()
                    )
                }

                val isResumed = response.code == 206
                val append = isResumed && existingBytes > 0L
                val body = response.body ?: throw IllegalStateException("Empty download response")
                val contentLength = body.contentLength()
                val totalBytes = when {
                    isResumed && contentLength > 0L -> existingBytes + contentLength
                    contentLength > 0L -> contentLength
                    else -> -1L
                }

                val responseMime = response.header("Content-Type")?.substringBefore(';')?.trim()
                val resolvedMime = responseMime?.takeIf { it.isNotBlank() } ?: task.mimeType

                var downloaded = if (append) existingBytes else 0L
                var lastDbUpdateMs = 0L

                body.byteStream().use { input ->
                    FileOutputStream(targetFile, append).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            if (isStopped) {
                                val current = dao.getById(downloadId)
                                val nextStatus = if (current?.status == STATUS_CANCELLED) {
                                    if (targetFile.exists()) targetFile.delete()
                                    STATUS_CANCELLED
                                } else {
                                    STATUS_PAUSED
                                }
                                dao.updateStatus(
                                    id = downloadId,
                                    status = nextStatus,
                                    error = null,
                                    localPath = targetFile.absolutePath
                                )
                                return@withContext Result.success()
                            }

                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastDbUpdateMs >= 300L) {
                                dao.updateProgress(
                                    id = downloadId,
                                    downloaded = downloaded,
                                    total = totalBytes,
                                    status = STATUS_RUNNING,
                                    updatedAt = now
                                )
                                lastDbUpdateMs = now
                            }
                        }
                        output.flush()
                    }
                }

                dao.updateProgress(
                    id = downloadId,
                    downloaded = targetFile.length(),
                    total = targetFile.length(),
                    status = STATUS_COMPLETED
                )
                finalizeDownload(dao, storageManager, downloadId, targetFile, resolvedMime)
                notifyCompleted(task.fileName)
                Result.success()
            }
        } catch (e: Exception) {
            val current = dao.getById(downloadId)
            if (current?.status == STATUS_PAUSED || current?.status == STATUS_CANCELLED) {
                return@withContext Result.success()
            }
            dao.updateStatus(
                id = downloadId,
                status = STATUS_FAILED,
                error = e.localizedMessage ?: "Download interrupted",
                localPath = targetFile.absolutePath
            )
            Result.failure()
        }
    }

    private suspend fun finalizeDownload(
        dao: com.example.data.local.db.DownloadDao,
        storageManager: OfflineStorageManager,
        downloadId: String,
        targetFile: File,
        mimeType: String
    ) {
        dao.updateStatus(
            id = downloadId,
            status = STATUS_COMPLETED,
            error = null,
            localPath = targetFile.absolutePath
        )
        storageManager.registerVaultFile(targetFile, mimeType)
    }

    private fun notifyCompleted(fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val notification = NotificationCompat.Builder(applicationContext, WiFiGoApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download complete")
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(fileName.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
