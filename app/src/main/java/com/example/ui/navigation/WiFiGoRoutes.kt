package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object SplashRoute

@Serializable
object OnboardingRoute

@Serializable
object HomeRoute

@Serializable
object ScannerRoute

@Serializable
data class WifiDetailsRoute(
    val ssid: String,
    val bssid: String,
    val levelDbm: Int,
    val signalPercentage: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val bandLabel: String,
    val securityType: String,
    val capabilities: String
)

@Serializable
object QrScannerRoute

@Serializable
data class QrResultRoute(val rawPayload: String)

@Serializable
object SpeedTestRoute

@Serializable
object SpeedHistoryRoute

@Serializable
object StorageRoute

@Serializable
data class FileDetailsRoute(val fileId: String)

@Serializable
data class BrowserRoute(
    val initialUrl: String = "https://www.wikipedia.org",
    val archivePath: String = ""
)

@Serializable
object SavedPagesRoute

@Serializable
object DownloadsRoute

@Serializable
data class DownloadDetailsRoute(val downloadId: String)

@Serializable
object PublicWifiFinderRoute

@Serializable
data class HotspotDetailsRoute(val hotspotId: String)

@Serializable
object NetworkInfoRoute

@Serializable
object ProfileRoute

@Serializable
object SettingsRoute

@Serializable
object LanguageRoute

@Serializable
object AboutRoute

@Serializable
object PrivacyPolicyRoute

@Serializable
object PermissionsRoute
