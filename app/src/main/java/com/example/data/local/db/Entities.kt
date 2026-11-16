package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_test_records")
data class SpeedTestRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val jitterMs: Double,
    val serverHost: String,
    val networkType: String,
    val ssid: String,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val category: String,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: String, // QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
    val localPath: String = "",
    val errorMessage: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_webpages")
data class SavedWebpageEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val archivePath: String,
    val isBookmarked: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val savedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_networks")
data class FavoriteNetworkEntity(
    @PrimaryKey val ssid: String,
    val bssid: String = "",
    val securityType: String = "Unknown",
    val frequencyMhz: Int = 0,
    val notes: String = "",
    val savedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_file_items")
data class LocalFileItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String, // Documents, Images, Videos, Audio, Other
    val mimeType: String,
    val sizeBytes: Long,
    val uriString: String,
    val isSafExternal: Boolean = false,
    val addedAtMs: Long = System.currentTimeMillis()
)
