package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val preferredLanguage: String = "en",
    val darkModeEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreUserFavorite(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val bssid: String? = null,
    val securityType: String = "Unknown",
    val frequencyMhz: Long = 0L,
    val itemType: String = "wifi_network",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PublicHotspot(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val isPublic: Boolean = true,
    val name: String = "",
    val venueType: String = "cafe",
    val address: String = "",
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val ssid: String = "",
    val securityType: String = "Open",
    val isFree: Boolean = true,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class HotspotReview(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val hotspotId: String = "",
    val isPublic: Boolean = true,
    val rating: Long = 5L,
    val comment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class HotspotReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val hotspotId: String = "",
    val reason: String = "other",
    val details: String = "",
    val status: String = "open",
    @ServerTimestamp val createdAt: Timestamp? = null
)
