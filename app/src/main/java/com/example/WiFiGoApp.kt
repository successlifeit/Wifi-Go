package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.db.WiFiGoDatabase

class WiFiGoApp : Application() {

    val database: WiFiGoDatabase by lazy { WiFiGoDatabase.getInstance(this) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "WiFiGo Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress and completion notifications for WiFiGo offline downloads"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "wifigo_downloads"
    }
}
