package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreUserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In required before accessing cloud user data.")

    fun observeUserProfile(userId: String): Flow<FirestoreUserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        FirestoreUserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertUserProfile(
        displayName: String,
        email: String?,
        preferredLanguage: String,
        darkModeEnabled: Boolean,
        notificationsEnabled: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val existing = docRef.get().await()
        if (!existing.exists()) {
            val payload = mapOf(
                "userId" to uid,
                "displayName" to displayName.take(80).ifBlank { "WiFiGo User" },
                "email" to email?.take(160),
                "preferredLanguage" to if (preferredLanguage == "bn") "bn" else "en",
                "darkModeEnabled" to darkModeEnabled,
                "notificationsEnabled" to notificationsEnabled,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
        } else {
            val updates = mapOf(
                "displayName" to displayName.take(80).ifBlank { "WiFiGo User" },
                "email" to email?.take(160),
                "preferredLanguage" to if (preferredLanguage == "bn") "bn" else "en",
                "darkModeEnabled" to darkModeEnabled,
                "notificationsEnabled" to notificationsEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(updates, SetOptions.merge()).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}")
    }

    fun observeFavorites(userId: String): Flow<List<FirestoreUserFavorite>> = flow {
        val path = "users/$userId/favorites"
        emitAll(
            db.collection("users").document(userId).collection("favorites")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        FirestoreUserFavorite::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveFavorite(
        favoriteId: String,
        name: String,
        bssid: String?,
        securityType: String,
        frequencyMhz: Long,
        itemType: String,
        notes: String?
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val safeId = favoriteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(100).ifBlank {
            db.collection("users").document(uid).collection("favorites").document().id
        }
        val docRef = db.collection("users").document(uid).collection("favorites").document(safeId)
        val payload = mapOf(
            "userId" to uid,
            "name" to name.take(120),
            "bssid" to bssid?.take(64),
            "securityType" to securityType.take(40).ifBlank { "Unknown" },
            "frequencyMhz" to frequencyMhz.coerceIn(0L, 7500L),
            "itemType" to if (itemType == "public_hotspot") "public_hotspot" else "wifi_network",
            "notes" to notes?.take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        docRef.set(payload).await()
        safeId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/favorites")
    }

    suspend fun deleteFavorite(favoriteId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("favorites").document(favoriteId)
        docRef.delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/favorites/$favoriteId")
    }
}

class FirestoreHotspotRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In required before accessing cloud hotspots.")

    fun observePublicHotspots(): Flow<List<PublicHotspot>> = flow {
        requireUserId()
        val path = "hotspots"
        emitAll(
            db.collection(path)
                .whereEqualTo("isPublic", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        PublicHotspot::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser != null) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getHotspotById(hotspotId: String): Result<PublicHotspot?> = runCatching {
        requireUserId()
        val snap = db.collection("hotspots").document(hotspotId).get().await()
        snap.toObject(PublicHotspot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "hotspots/$hotspotId")
    }

    suspend fun createHotspot(
        name: String,
        venueType: String,
        address: String,
        city: String,
        latitude: Double,
        longitude: Double,
        ssid: String,
        securityType: String,
        isFree: Boolean,
        notes: String?
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("hotspots").document()
        val payload = mapOf(
            "ownerId" to uid,
            "isPublic" to true,
            "name" to name.trim().take(100),
            "venueType" to venueType,
            "address" to address.trim().take(200),
            "city" to city.trim().take(80),
            "latitude" to latitude.coerceIn(-90.0, 90.0),
            "longitude" to longitude.coerceIn(-180.0, 180.0),
            "ssid" to ssid.trim().take(64),
            "securityType" to securityType,
            "isFree" to isFree,
            "notes" to notes?.trim()?.take(400)?.ifBlank { null },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        docRef.set(payload).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "hotspots")
    }

    fun observeReviewsForHotspot(hotspotId: String): Flow<List<HotspotReview>> = flow {
        requireUserId()
        val path = "hotspot_reviews"
        emitAll(
            db.collection(path)
                .whereEqualTo("isPublic", true)
                .whereEqualTo("hotspotId", hotspotId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        HotspotReview::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser != null) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addReview(
        hotspotId: String,
        authorName: String,
        rating: Int,
        comment: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("hotspot_reviews").document()
        val payload = mapOf(
            "userId" to uid,
            "authorName" to authorName.trim().take(80).ifBlank { "WiFiGo User" },
            "hotspotId" to hotspotId,
            "isPublic" to true,
            "rating" to rating.coerceIn(1, 5).toLong(),
            "comment" to comment.trim().take(500),
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "hotspot_reviews")
    }

    fun observeUserReports(userId: String): Flow<List<HotspotReport>> = flow {
        val path = "hotspot_reports"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        HotspotReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun submitReport(
        hotspotId: String,
        reason: String,
        details: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("hotspot_reports").document()
        val payload = mapOf(
            "userId" to uid,
            "hotspotId" to hotspotId,
            "reason" to reason,
            "details" to details.trim().take(500),
            "status" to "open",
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "hotspot_reports")
    }
}
