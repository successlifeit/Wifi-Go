package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.db.SpeedTestDao
import com.example.data.local.db.SpeedTestRecordEntity
import com.example.data.network.SpeedTestEngine
import com.example.data.network.SpeedTestPhase
import com.example.data.network.SpeedTestProgress
import com.example.data.wifi.WifiScannerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpeedTestViewModel(
    private val speedTestEngine: SpeedTestEngine,
    private val speedTestDao: SpeedTestDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val wifiScannerManager: WifiScannerManager
) : ViewModel() {

    private val _progress = MutableStateFlow(SpeedTestProgress(phase = SpeedTestPhase.IDLE))
    val progress: StateFlow<SpeedTestProgress> = _progress.asStateFlow()

    val history: StateFlow<List<SpeedTestRecordEntity>> = speedTestDao.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private var activeTestJob: Job? = null

    fun startTest() {
        if (activeTestJob?.isActive == true) return
        activeTestJob = viewModelScope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()
            val net = wifiScannerManager.getCurrentNetworkSnapshot()
            if (!net.hasInternetAccess) {
                _progress.value = SpeedTestProgress(
                    phase = SpeedTestPhase.ERROR,
                    errorMessage = "No active network with internet capability detected. Connect to Wi-Fi or mobile data and try again."
                )
                return@launch
            }

            speedTestEngine.runSpeedTest(
                downloadUrl = prefs.speedTestDownloadUrl,
                uploadUrl = prefs.speedTestUploadUrl
            ).collect { state ->
                _progress.value = state
                if (state.phase == SpeedTestPhase.COMPLETED) {
                    speedTestDao.insert(
                        SpeedTestRecordEntity(
                            downloadMbps = state.downloadMbps,
                            uploadMbps = state.uploadMbps,
                            pingMs = state.pingMs,
                            jitterMs = state.jitterMs,
                            serverHost = state.serverHost,
                            networkType = net.transportLabel,
                            ssid = net.ssid ?: net.transportLabel
                        )
                    )
                }
            }
        }
    }

    fun cancelTest() {
        activeTestJob?.cancel()
        activeTestJob = null
        _progress.value = SpeedTestProgress(phase = SpeedTestPhase.IDLE)
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            speedTestDao.deleteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            speedTestDao.clearAll()
        }
    }
}
