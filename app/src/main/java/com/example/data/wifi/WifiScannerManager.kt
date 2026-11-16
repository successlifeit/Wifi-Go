package com.example.data.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.net.InetAddress

data class ScannedWifiNetwork(
    val ssid: String,
    val bssid: String,
    val levelDbm: Int,
    val signalPercentage: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val bandLabel: String,
    val securityType: String,
    val capabilities: String,
    val timestampMicros: Long
)

data class ActiveNetworkSnapshot(
    val isWifiConnected: Boolean = false,
    val hasInternetAccess: Boolean = false,
    val isValidated: Boolean = false,
    val transportLabel: String = "Disconnected",
    val ssid: String? = null,
    val bssid: String? = null,
    val rssiDbm: Int? = null,
    val signalPercentage: Int? = null,
    val linkSpeedMbps: Int? = null,
    val frequencyMhz: Int? = null,
    val channel: Int? = null,
    val ipAddress: String? = null,
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
    val interfaceName: String? = null,
    val permissionNote: String? = null
)

enum class WifiScanAvailability {
    READY,
    MISSING_PERMISSION,
    WIFI_DISABLED,
    LOCATION_DISABLED
}

class WifiScannerManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager: WifiManager? =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager: ConnectivityManager? =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val locationManager: LocationManager? =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun requiredScanPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasScanPermissions(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNearby = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            hasFine || hasNearby
        } else {
            hasFine
        }
    }

    fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun isWifiEnabled(): Boolean = wifiManager?.isWifiEnabled == true

    fun checkScanAvailability(): WifiScanAvailability {
        if (!hasScanPermissions()) return WifiScanAvailability.MISSING_PERMISSION
        if (!isWifiEnabled()) return WifiScanAvailability.WIFI_DISABLED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !isLocationEnabled()) {
            return WifiScanAvailability.LOCATION_DISABLED
        }
        return WifiScanAvailability.READY
    }

    fun triggerScan(): Boolean {
        if (checkScanAvailability() != WifiScanAvailability.READY) return false
        return try {
            @Suppress("DEPRECATION")
            wifiManager?.startScan() ?: false
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun readLatestScanResults(): List<ScannedWifiNetwork> {
        if (!hasScanPermissions()) return emptyList()
        val results = try {
            wifiManager?.scanResults ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
        return results.map { mapScanResult(it) }
    }

    fun observeScanBroadcasts(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    trySend(updated)
                }
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        awaitClose {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentNetworkSnapshot(): ActiveNetworkSnapshot {
        val cm = connectivityManager ?: return ActiveNetworkSnapshot()
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }

        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        val transportLabel = when {
            isWifi -> "Wi-Fi"
            isCellular -> "Cellular"
            isEthernet -> "Ethernet"
            caps != null -> "Other Network"
            else -> "Disconnected"
        }

        val wifiInfo = extractWifiInfo(caps)
        val rawSsid = wifiInfo?.ssid?.removeSurrounding("\"")
        val cleanSsid = when {
            rawSsid == null -> null
            rawSsid == WifiManager.UNKNOWN_SSID || rawSsid == "<unknown ssid>" -> null
            rawSsid.isBlank() -> null
            else -> rawSsid
        }

        val rssi = wifiInfo?.rssi?.takeIf { it in -127..0 }
        val freq = wifiInfo?.frequency?.takeIf { it > 0 }
        val linkSpeed = wifiInfo?.linkSpeed?.takeIf { it > 0 }
        val signalPct = rssi?.let { calculateSignalPercentage(it) }
        val bssid = wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" && it.isNotBlank() }

        val ipAddress = linkProps?.let { extractIpv4Address(it) }
        val gateway = linkProps?.let { extractGateway(it) }
        val dnsServers = linkProps?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        val permissionNote = if (isWifi && cleanSsid == null && !hasScanPermissions()) {
            "Grant Location / Nearby Wi-Fi permission to allow Android to reveal the connected SSID."
        } else null

        return ActiveNetworkSnapshot(
            isWifiConnected = isWifi,
            hasInternetAccess = hasInternet,
            isValidated = isValidated,
            transportLabel = transportLabel,
            ssid = cleanSsid,
            bssid = bssid,
            rssiDbm = rssi,
            signalPercentage = signalPct,
            linkSpeedMbps = linkSpeed,
            frequencyMhz = freq,
            channel = freq?.let { frequencyToChannel(it) },
            ipAddress = ipAddress,
            gateway = gateway,
            dnsServers = dnsServers,
            interfaceName = linkProps?.interfaceName,
            permissionNote = permissionNote
        )
    }

    fun observeActiveNetwork(): Flow<ActiveNetworkSnapshot> = callbackFlow {
        trySend(getCurrentNetworkSnapshot())
        val cm = connectivityManager
        if (cm == null) {
            awaitClose { }
            return@callbackFlow
        }

        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onAvailable(network: Network) {
                    trySend(getCurrentNetworkSnapshot())
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    trySend(getCurrentNetworkSnapshot())
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    trySend(getCurrentNetworkSnapshot())
                }

                override fun onLost(network: Network) {
                    trySend(getCurrentNetworkSnapshot())
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(getCurrentNetworkSnapshot())
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    trySend(getCurrentNetworkSnapshot())
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    trySend(getCurrentNetworkSnapshot())
                }

                override fun onLost(network: Network) {
                    trySend(getCurrentNetworkSnapshot())
                }
            }
        }

        val request = NetworkRequest.Builder().build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            try {
                cm.registerDefaultNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractWifiInfo(caps: NetworkCapabilities?): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val transportInfo = caps?.transportInfo
            if (transportInfo is WifiInfo) return transportInfo
        }
        return try {
            wifiManager?.connectionInfo
        } catch (_: SecurityException) {
            null
        }
    }

    private fun extractIpv4Address(linkProperties: LinkProperties): String? {
        val v4 = linkProperties.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address }
        return v4?.hostAddress ?: linkProperties.linkAddresses.firstOrNull()?.address?.hostAddress
    }

    private fun extractGateway(linkProperties: LinkProperties): String? {
        val defaultRoute = linkProperties.routes.firstOrNull { route ->
            route.isDefaultRoute && route.gateway != null
        } ?: linkProperties.routes.firstOrNull { it.gateway != null }
        val gw: InetAddress? = defaultRoute?.gateway
        return gw?.hostAddress?.takeIf { it != "0.0.0.0" && it != "::" }
    }

    fun suggestWifiNetwork(ssid: String, password: String?, securityType: String): SuggestionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return SuggestionResult.UnsupportedOlderAndroid
        }
        val wm = wifiManager ?: return SuggestionResult.Failed("Wi-Fi service unavailable")
        val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
        val upperSec = securityType.uppercase()
        when {
            upperSec.contains("WPA3") || upperSec.contains("SAE") -> {
                if (!password.isNullOrEmpty()) builder.setWpa3Passphrase(password)
            }
            upperSec.contains("WPA") -> {
                if (!password.isNullOrEmpty()) builder.setWpa2Passphrase(password)
            }
            upperSec.contains("WEP") -> {
                return SuggestionResult.UnsupportedLegacyWep
            }
            else -> {
                // Open network suggestion
            }
        }
        val status = try {
            wm.addNetworkSuggestions(listOf(builder.build()))
        } catch (e: SecurityException) {
            return SuggestionResult.Failed(e.localizedMessage ?: "Permission denied")
        }
        return when (status) {
            WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> SuggestionResult.Suggested
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> SuggestionResult.AlreadySuggested
            else -> SuggestionResult.Failed("Android returned status code $status")
        }
    }

    fun buildWifiSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
    }

    private fun mapScanResult(result: ScanResult): ScannedWifiNetwork {
        @Suppress("DEPRECATION")
        val rawSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.wifiSsid?.toString()?.removeSurrounding("\"") ?: result.SSID
        } else {
            result.SSID
        }
        val ssid = rawSsid?.takeIf { it.isNotBlank() } ?: "<Hidden Network>"
        val freq = result.frequency
        return ScannedWifiNetwork(
            ssid = ssid,
            bssid = result.BSSID ?: "Unavailable",
            levelDbm = result.level,
            signalPercentage = calculateSignalPercentage(result.level),
            frequencyMhz = freq,
            channel = frequencyToChannel(freq),
            bandLabel = frequencyToBandLabel(freq),
            securityType = parseSecurityType(result.capabilities ?: ""),
            capabilities = result.capabilities ?: "",
            timestampMicros = result.timestamp
        )
    }

    companion object {
        fun calculateSignalPercentage(rssiDbm: Int): Int {
            return WifiManager.calculateSignalLevel(rssiDbm, 101).coerceIn(0, 100)
        }

        fun frequencyToChannel(frequencyMhz: Int): Int {
            return when {
                frequencyMhz == 2484 -> 14
                frequencyMhz in 2412..2472 -> (frequencyMhz - 2412) / 5 + 1
                frequencyMhz in 5170..5885 -> (frequencyMhz - 5000) / 5
                frequencyMhz in 5955..7115 -> (frequencyMhz - 5950) / 5
                else -> 0
            }
        }

        fun frequencyToBandLabel(frequencyMhz: Int): String {
            return when {
                frequencyMhz in 2400..2500 -> "2.4 GHz"
                frequencyMhz in 4900..5900 -> "5 GHz"
                frequencyMhz in 5925..7125 -> "6 GHz"
                else -> "${frequencyMhz} MHz"
            }
        }

        fun parseSecurityType(capabilities: String): String {
            val caps = capabilities.uppercase()
            return when {
                caps.contains("SAE") || caps.contains("WPA3") -> "WPA3"
                caps.contains("WPA2") -> "WPA2"
                caps.contains("WPA") -> "WPA"
                caps.contains("WEP") -> "WEP"
                caps.contains("OWE") -> "OWE (Enhanced Open)"
                else -> "Open"
            }
        }
    }
}

sealed interface SuggestionResult {
    data object Suggested : SuggestionResult
    data object AlreadySuggested : SuggestionResult
    data object UnsupportedOlderAndroid : SuggestionResult
    data object UnsupportedLegacyWep : SuggestionResult
    data class Failed(val reason: String) : SuggestionResult
}
