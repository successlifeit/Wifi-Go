package com.example.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.data.local.db.LocalFileDao
import com.example.data.local.db.LocalFileItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class DeviceStorageStats(
    val totalBytes: Long,
    val availableBytes: Long,
    val vaultUsedBytes: Long
)

class OfflineStorageManager(
    private val context: Context,
    private val localFileDao: LocalFileDao
) {
    private val appContext = context.applicationContext

    val vaultDir: File
        get() = File(appContext.filesDir, "wifigo_vault").apply { if (!exists()) mkdirs() }

    val webArchivesDir: File
        get() = File(appContext.filesDir, "web_archives").apply { if (!exists()) mkdirs() }

    suspend fun registerVaultFile(
        file: File,
        mimeType: String? = null
    ): LocalFileItemEntity = withContext(Dispatchers.IO) {
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: guessMimeType(file.name)
        val item = LocalFileItemEntity(
            id = UUID.randomUUID().toString(),
            name = file.name,
            category = categorizeFile(file.name, resolvedMime),
            mimeType = resolvedMime,
            sizeBytes = file.length(),
            uriString = file.absolutePath,
            isSafExternal = false,
            addedAtMs = System.currentTimeMillis()
        )
        localFileDao.upsert(item)
        item
    }

    suspend fun importSafDocument(uri: Uri): Result<LocalFileItemEntity> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }

            var displayName = "imported_file_${System.currentTimeMillis()}"
            var sizeBytes = 0L
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }

            val mimeType = appContext.contentResolver.getType(uri) ?: guessMimeType(displayName)
            val safeName = sanitizeFileName(displayName)
            val destFile = uniqueVaultFile(safeName)
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Cannot read selected file")

            val actualSize = if (destFile.length() > 0L) destFile.length() else sizeBytes
            val entity = LocalFileItemEntity(
                id = UUID.randomUUID().toString(),
                name = destFile.name,
                category = categorizeFile(destFile.name, mimeType),
                mimeType = mimeType,
                sizeBytes = actualSize,
                uriString = destFile.absolutePath,
                isSafExternal = false,
                addedAtMs = System.currentTimeMillis()
            )
            localFileDao.upsert(entity)
            entity
        }
    }

    suspend fun renameFile(item: LocalFileItemEntity, newNameRaw: String): Result<LocalFileItemEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = sanitizeFileName(newNameRaw)
                require(sanitized.isNotBlank()) { "Filename cannot be blank" }
                if (!item.isSafExternal) {
                    val oldFile = File(item.uriString)
                    val targetFile = File(oldFile.parentFile ?: vaultDir, sanitized)
                    require(!targetFile.exists()) { "A file with that name already exists" }
                    if (oldFile.exists() && !oldFile.renameTo(targetFile)) {
                        throw IllegalStateException("Failed to rename file on disk")
                    }
                    val updated = item.copy(
                        name = targetFile.name,
                        uriString = targetFile.absolutePath,
                        category = categorizeFile(targetFile.name, item.mimeType)
                    )
                    localFileDao.update(updated)
                    updated
                } else {
                    val updated = item.copy(name = sanitized)
                    localFileDao.update(updated)
                    updated
                }
            }
        }

    suspend fun deleteFile(item: LocalFileItemEntity) = withContext(Dispatchers.IO) {
        if (!item.isSafExternal) {
            val file = File(item.uriString)
            if (file.exists()) file.delete()
        }
        localFileDao.deleteById(item.id)
    }

    fun getShareUri(item: LocalFileItemEntity): Uri {
        return if (item.isSafExternal) {
            Uri.parse(item.uriString)
        } else {
            val file = File(item.uriString)
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
        }
    }

    fun buildOpenIntent(item: LocalFileItemEntity): Intent {
        val uri = getShareUri(item)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildShareIntent(item: LocalFileItemEntity): Intent {
        val uri = getShareUri(item)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, item.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share ${item.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun getStorageStats(): DeviceStorageStats {
        val statFs = StatFs(appContext.filesDir.absolutePath)
        val total = statFs.totalBytes
        val avail = statFs.availableBytes
        val vaultSize = directorySize(vaultDir) + directorySize(webArchivesDir)
        return DeviceStorageStats(
            totalBytes = total,
            availableBytes = avail,
            vaultUsedBytes = vaultSize
        )
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun uniqueVaultFile(fileName: String): File {
        var candidate = File(vaultDir, fileName)
        if (!candidate.exists()) return candidate
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(vaultDir, "${base}_$counter$ext")
            counter++
        }
        return candidate
    }

    companion object {
        val CATEGORIES = listOf("All", "Documents", "Images", "Videos", "Audio", "Other")

        fun sanitizeFileName(raw: String): String {
            return raw.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").take(120)
        }

        fun guessMimeType(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
        }

        fun categorizeFile(fileName: String, mimeType: String): String {
            val lowerMime = mimeType.lowercase()
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when {
                lowerMime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> "Images"
                lowerMime.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "avi", "mov") -> "Videos"
                lowerMime.startsWith("audio/") || ext in setOf("mp3", "wav", "ogg", "m4a", "flac", "aac") -> "Audio"
                lowerMime.startsWith("text/") ||
                    lowerMime.contains("pdf") ||
                    lowerMime.contains("document") ||
                    lowerMime.contains("msword") ||
                    lowerMime.contains("spreadsheet") ||
                    ext in setOf("pdf", "doc", "docx", "txt", "csv", "json", "xml", "md", "mht", "mhtml", "html") -> "Documents"
                else -> "Other"
            }
        }

        fun formatBytes(bytes: Long): String {
            if (bytes < 0) return "Unknown size"
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }
    }
}
