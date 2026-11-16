package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.wifi.ScannedWifiNetwork
import com.example.data.wifi.WifiScanAvailability
import com.example.data.wifi.WifiScannerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BandFilter {
    ALL,
    BAND_2_4_GHZ,
    BAND_5_GHZ
}

data class WifiScannerUiState(
    val availability: WifiScanAvailability = WifiScanAvailability.READY,
    val isScanning: Boolean = false,
    val networks: List<ScannedWifiNetwork> = emptyList(),
    val sortStrongestFirst: Boolean = true,
    val bandFilter: BandFilter = BandFilter.ALL,
    val lastScanTimeMs: Long? = null,
    val scanThrottled: Boolean = false
)

class WifiScannerViewModel(
    private val wifiScannerManager: WifiScannerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiScannerUiState())
    val uiState: StateFlow<WifiScannerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            wifiScannerManager.observeScanBroadcasts().collect { updated ->
                val raw = wifiScannerManager.readLatestScanResults()
                _uiState.update { state ->
                    state.copy(
                        isScanning = false,
                        networks = applyFilterAndSort(raw, state.bandFilter, state.sortStrongestFirst),
                        lastScanTimeMs = if (raw.isNotEmpty() || updated) System.currentTimeMillis() else state.lastScanTimeMs,
                        scanThrottled = !updated
                    )
                }
            }
        }
        refreshAvailabilityAndResults()
    }

    fun refreshAvailabilityAndResults() {
        val availability = wifiScannerManager.checkScanAvailability()
        val raw = if (availability == WifiScanAvailability.READY) {
            wifiScannerManager.readLatestScanResults()
        } else {
            emptyList()
        }
        _uiState.update { state ->
            state.copy(
                availability = availability,
                networks = applyFilterAndSort(raw, state.bandFilter, state.sortStrongestFirst),
                lastScanTimeMs = if (raw.isNotEmpty() && state.lastScanTimeMs == null) System.currentTimeMillis() else state.lastScanTimeMs
            )
        }
    }

    fun startScan() {
        val availability = wifiScannerManager.checkScanAvailability()
        if (availability != WifiScanAvailability.READY) {
            _uiState.update { it.copy(availability = availability, isScanning = false, networks = emptyList()) }
            return
        }

        _uiState.update { it.copy(availability = availability, isScanning = true, scanThrottled = false) }
        val started = wifiScannerManager.triggerScan()
        val latest = wifiScannerManager.readLatestScanResults()
        if (!started) {
            _uiState.update { state ->
                state.copy(
                    isScanning = false,
                    scanThrottled = true,
                    networks = applyFilterAndSort(latest, state.bandFilter, state.sortStrongestFirst),
                    lastScanTimeMs = if (latest.isNotEmpty()) System.currentTimeMillis() else state.lastScanTimeMs
                )
            }
        } else if (latest.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(
                    networks = applyFilterAndSort(latest, state.bandFilter, state.sortStrongestFirst),
                    lastScanTimeMs = System.currentTimeMillis()
                )
            }
        }
    }

    fun setBandFilter(filter: BandFilter) {
        val latest = wifiScannerManager.readLatestScanResults()
        _uiState.update { state ->
            state.copy(
                bandFilter = filter,
                networks = applyFilterAndSort(latest, filter, state.sortStrongestFirst)
            )
        }
    }

    fun toggleSortOrder() {
        val latest = wifiScannerManager.readLatestScanResults()
        _uiState.update { state ->
            val nextSort = !state.sortStrongestFirst
            state.copy(
                sortStrongestFirst = nextSort,
                networks = applyFilterAndSort(latest, state.bandFilter, nextSort)
            )
        }
    }

    private fun applyFilterAndSort(
        list: List<ScannedWifiNetwork>,
        filter: BandFilter,
        strongestFirst: Boolean
    ): List<ScannedWifiNetwork> {
        val filtered = when (filter) {
            BandFilter.ALL -> list
            BandFilter.BAND_2_4_GHZ -> list.filter { it.frequencyMhz in 2400..2500 }
            BandFilter.BAND_5_GHZ -> list.filter { it.frequencyMhz in 4900..5900 }
        }
        return if (strongestFirst) {
            filtered.sortedByDescending { it.levelDbm }
        } else {
            filtered.sortedBy { it.ssid.lowercase() }
        }
    }
}
