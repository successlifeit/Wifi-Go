package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ThemeMode
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.db.DownloadDao
import com.example.data.local.db.DownloadTaskEntity
import com.example.data.local.db.FavoriteNetworkDao
import com.example.data.local.db.FavoriteNetworkEntity
import com.example.data.remote.FirestoreUserProfile
import com.example.data.remote.FirestoreUserRepository
import com.example.data.remote.GoogleAuthManager.authStateFlow
import com.example.data.wifi.ActiveNetworkSnapshot
import com.example.data.wifi.WifiScannerManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val wifiScannerManager: WifiScannerManager,
    private val favoriteNetworkDao: FavoriteNetworkDao,
    downloadDao: DownloadDao,
    private val firestoreUserRepository: FirestoreUserRepository?
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferences()
        )

    val activeNetwork: StateFlow<ActiveNetworkSnapshot> = wifiScannerManager.observeActiveNetwork()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ActiveNetworkSnapshot()
        )

    val favoriteNetworks: StateFlow<List<FavoriteNetworkEntity>> = favoriteNetworkDao.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val recentDownloads: StateFlow<List<DownloadTaskEntity>> = downloadDao.observeRecent()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val authUser: StateFlow<FirebaseUser?> = try {
        Firebase.auth.authStateFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = Firebase.auth.currentUser
            )
    } catch (_: Exception) {
        flowOf<FirebaseUser?>(null).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    val cloudProfile: StateFlow<FirestoreUserProfile?> = authUser
        .flatMapLatest { user ->
            if (user == null || firestoreUserRepository == null) {
                flowOf(null)
            } else {
                firestoreUserRepository.observeUserProfile(user.uid)
                    .catch { emit(null) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch {
            preferencesRepository.setLanguageCode(code)
            syncCurrentPreferencesToCloud()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
            syncCurrentPreferencesToCloud()
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
            syncCurrentPreferencesToCloud()
        }
    }

    fun setSpeedTestEndpoints(downloadUrl: String, uploadUrl: String) {
        viewModelScope.launch {
            preferencesRepository.setSpeedTestEndpoints(downloadUrl, uploadUrl)
        }
    }

    fun toggleFavoriteNetwork(
        ssid: String,
        bssid: String,
        securityType: String,
        frequencyMhz: Int
    ) {
        viewModelScope.launch {
            val exists = favoriteNetworks.value.any { it.ssid == ssid }
            if (exists) {
                favoriteNetworkDao.deleteBySsid(ssid)
                if (authUser.value != null) {
                    firestoreUserRepository?.deleteFavorite(ssid)
                }
            } else {
                favoriteNetworkDao.upsert(
                    FavoriteNetworkEntity(
                        ssid = ssid,
                        bssid = bssid,
                        securityType = securityType,
                        frequencyMhz = frequencyMhz
                    )
                )
                if (authUser.value != null) {
                    firestoreUserRepository?.saveFavorite(
                        favoriteId = ssid,
                        name = ssid,
                        bssid = bssid.ifBlank { null },
                        securityType = securityType,
                        frequencyMhz = frequencyMhz.toLong(),
                        itemType = "wifi_network",
                        notes = null
                    )
                }
            }
        }
    }

    fun syncCurrentPreferencesToCloud() {
        val user = authUser.value ?: return
        val repo = firestoreUserRepository ?: return
        val prefs = preferences.value
        viewModelScope.launch {
            repo.upsertUserProfile(
                displayName = user.displayName ?: "WiFiGo User",
                email = user.email,
                preferredLanguage = prefs.languageCode,
                darkModeEnabled = prefs.themeMode == ThemeMode.DARK,
                notificationsEnabled = prefs.notificationsEnabled
            )
        }
    }
}
