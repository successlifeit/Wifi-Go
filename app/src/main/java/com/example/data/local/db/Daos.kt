package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedTestDao {
    @Query("SELECT * FROM speed_test_records ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<SpeedTestRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SpeedTestRecordEntity): Long

    @Query("DELETE FROM speed_test_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM speed_test_records")
    suspend fun clearAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks ORDER BY updatedAtMs DESC LIMIT 5")
    fun observeRecent(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET downloadedBytes = :downloaded, totalBytes = :total, status = :status, updatedAtMs = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, downloaded: Long, total: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE download_tasks SET status = :status, errorMessage = :error, localPath = :localPath, updatedAtMs = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String? = null, localPath: String = "", updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SavedWebpageDao {
    @Query("SELECT * FROM saved_webpages ORDER BY savedAtMs DESC")
    fun observeAll(): Flow<List<SavedWebpageEntity>>

    @Query("SELECT * FROM saved_webpages WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): SavedWebpageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(page: SavedWebpageEntity)

    @Query("UPDATE saved_webpages SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmarked(id: String, bookmarked: Boolean)

    @Query("DELETE FROM saved_webpages WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface FavoriteNetworkDao {
    @Query("SELECT * FROM favorite_networks ORDER BY savedAtMs DESC")
    fun observeAll(): Flow<List<FavoriteNetworkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(network: FavoriteNetworkEntity)

    @Query("DELETE FROM favorite_networks WHERE ssid = :ssid")
    suspend fun deleteBySsid(ssid: String)
}

@Dao
interface LocalFileDao {
    @Query("SELECT * FROM local_file_items ORDER BY addedAtMs DESC")
    fun observeAll(): Flow<List<LocalFileItemEntity>>

    @Query("SELECT * FROM local_file_items WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<LocalFileItemEntity?>

    @Query("SELECT * FROM local_file_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LocalFileItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalFileItemEntity)

    @Update
    suspend fun update(item: LocalFileItemEntity)

    @Query("DELETE FROM local_file_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
