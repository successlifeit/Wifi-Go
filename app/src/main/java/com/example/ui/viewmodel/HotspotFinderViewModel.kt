package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.db.FavoriteNetworkDao
import com.example.data.local.db.FavoriteNetworkEntity
import com.example.data.remote.FirestoreHotspotRepository
import com.example.data.remote.FirestoreUserRepository
import com.example.data.remote.HotspotReport
import com.example.data.remote.HotspotReview
import com.example.data.remote.PublicHotspot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HotspotUiState {
    data object GuestMode : HotspotUiState
    data object Loading : HotspotUiState
    data class Success(val hotspots: List<PublicHotspot>) : HotspotUiState
    data class Error(val message: String) : HotspotUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HotspotFinderViewModel(
    private val hotspotRepository: FirestoreHotspotRepository,
    private val userRepository: FirestoreUserRepository,
    private val favoriteNetworkDao: FavoriteNetworkDao,
    private val currentUserId: String?,
    private val currentUserName: String?
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _selectedHotspotId = MutableStateFlow<String?>(null)

    private val rawHotspotsFlow = if (currentUserId == null) {
        flowOf<HotspotUiState>(HotspotUiState.GuestMode)
    } else {
        hotspotRepository.observePublicHotspots()
            .map<List<PublicHotspot>, HotspotUiState> { list ->
                HotspotUiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: 0L })
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing public hotspots", rootError)
                emit(HotspotUiState.Error(rootError.message ?: "Failed to load community hotspots"))
            }
    }

    val uiState: StateFlow<HotspotUiState> = combine(rawHotspotsFlow, _searchQuery) { state, q ->
        if (state is HotspotUiState.Success) {
            val cleanQ = q.trim().lowercase()
            if (cleanQ.isEmpty()) {
                state
            } else {
                HotspotUiState.Success(
                    state.hotspots.filter { hs ->
                        hs.name.lowercase().contains(cleanQ) ||
                            hs.city.lowercase().contains(cleanQ) ||
                            hs.address.lowercase().contains(cleanQ) ||
                            hs.ssid.lowercase().contains(cleanQ) ||
                            hs.venueType.lowercase().contains(cleanQ)
                    }
                )
            }
        } else {
            state
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = if (currentUserId == null) HotspotUiState.GuestMode else HotspotUiState.Loading
    )

    val selectedReviews: StateFlow<List<HotspotReview>> = _selectedHotspotId
        .flatMapLatest { hotspotId ->
            if (currentUserId == null || hotspotId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                hotspotRepository.observeReviewsForHotspot(hotspotId)
                    .catch { emit(emptyList()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userReports: StateFlow<List<HotspotReport>> = if (currentUserId == null) {
        flowOf(emptyList())
    } else {
        hotspotRepository.observeUserReports(currentUserId)
            .catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectHotspot(hotspotId: String) {
        _selectedHotspotId.value = hotspotId
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun submitHotspot(
        name: String,
        venueType: String,
        address: String,
        city: String,
        latitude: Double,
        longitude: Double,
        ssid: String,
        securityType: String,
        isFree: Boolean,
        notes: String
    ) {
        if (currentUserId == null) {
            _actionMessage.value = "Sign in with Google to share a public hotspot"
            return
        }
        viewModelScope.launch {
            hotspotRepository.createHotspot(
                name = name,
                venueType = venueType,
                address = address,
                city = city,
                latitude = latitude,
                longitude = longitude,
                ssid = ssid,
                securityType = securityType,
                isFree = isFree,
                notes = notes
            ).onSuccess {
                _actionMessage.value = "Hotspot shared with community"
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to submit hotspot"
            }
        }
    }

    fun addReview(hotspotId: String, rating: Int, comment: String) {
        if (currentUserId == null) {
            _actionMessage.value = "Sign in with Google to post a review"
            return
        }
        viewModelScope.launch {
            hotspotRepository.addReview(
                hotspotId = hotspotId,
                authorName = currentUserName ?: "WiFiGo User",
                rating = rating,
                comment = comment
            ).onSuccess {
                _actionMessage.value = "Review published"
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to post review"
            }
        }
    }

    fun submitReport(hotspotId: String, reason: String, details: String) {
        if (currentUserId == null) {
            _actionMessage.value = "Sign in with Google to report a hotspot issue"
            return
        }
        viewModelScope.launch {
            hotspotRepository.submitReport(hotspotId, reason, details)
                .onSuccess { _actionMessage.value = "Report submitted" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to submit report" }
        }
    }

    fun saveHotspotAsFavorite(hotspot: PublicHotspot) {
        viewModelScope.launch {
            favoriteNetworkDao.upsert(
                FavoriteNetworkEntity(
                    ssid = hotspot.ssid,
                    bssid = "",
                    securityType = hotspot.securityType,
                    frequencyMhz = 2400,
                    notes = "${hotspot.name} • ${hotspot.city}"
                )
            )
            if (currentUserId != null) {
                userRepository.saveFavorite(
                    favoriteId = "hs_${hotspot.id}",
                    name = hotspot.name,
                    bssid = null,
                    securityType = hotspot.securityType,
                    frequencyMhz = 2400L,
                    itemType = "public_hotspot",
                    notes = "${hotspot.ssid} • ${hotspot.address}"
                )
            }
            _actionMessage.value = "Saved ${hotspot.name} to favorites"
        }
    }

    private companion object {
        const val TAG = "HotspotFinderVM"
    }
}
