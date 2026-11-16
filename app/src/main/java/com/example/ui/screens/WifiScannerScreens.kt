package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.wifi.ScannedWifiNetwork
import com.example.data.wifi.WifiScanAvailability
import com.example.data.wifi.WifiScannerManager
import com.example.ui.components.ElevatedInfoCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SignalStrengthBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.WiFiGoTopBar
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SignalFair
import com.example.ui.viewmodel.BandFilter
import com.example.ui.viewmodel.WifiScannerUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WifiScannerScreen(
    uiState: WifiScannerUiState,
    wifiScannerManager: WifiScannerManager,
    favoriteSsids: Set<String>,
    onRefreshAvailability: () -> Unit,
    onStartScan: () -> Unit,
    onSelectBandFilter: (BandFilter) -> Unit,
    onToggleSort: () -> Unit,
    onNetworkClick: (ScannedWifiNetwork) -> Unit,
    onToggleFavorite: (ScannedWifiNetwork) -> Unit,
    onOpenQrScanner: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshAvailability()
        onStartScan()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshAvailability()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.wifiScanner, style = MaterialTheme.typography.headlineMedium)
                        val tsText = uiState.lastScanTimeMs?.let {
                            val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            "${strings.lastScan}: ${fmt.format(Date(it))}"
                        } ?: "Ready to scan"
                        Text(
                            text = tsText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onOpenQrScanner,
                            modifier = Modifier.testTag("scanner_qr_shortcut")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = strings.qrScanner)
                        }
                        Button(
                            onClick = {
                                if (uiState.availability == WifiScanAvailability.MISSING_PERMISSION) {
                                    permissionLauncher.launch(wifiScannerManager.requiredScanPermissions())
                                } else {
                                    onStartScan()
                                }
                            },
                            modifier = Modifier.testTag("scan_wifi_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.scanNow)
                        }
                    }
                }

                if (uiState.isScanning) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.scanning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.bandFilter == BandFilter.ALL,
                        onClick = { onSelectBandFilter(BandFilter.ALL) },
                        label = { Text(strings.filterAll) }
                    )
                    FilterChip(
                        selected = uiState.bandFilter == BandFilter.BAND_2_4_GHZ,
                        onClick = { onSelectBandFilter(BandFilter.BAND_2_4_GHZ) },
                        label = { Text(strings.filter24Ghz) }
                    )
                    FilterChip(
                        selected = uiState.bandFilter == BandFilter.BAND_5_GHZ,
                        onClick = { onSelectBandFilter(BandFilter.BAND_5_GHZ) },
                        label = { Text(strings.filter5Ghz) }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = uiState.sortStrongestFirst,
                        onClick = onToggleSort,
                        leadingIcon = {
                            Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(if (uiState.sortStrongestFirst) "dBm" else "A-Z") }
                    )
                }
            }
        }

        if (uiState.scanThrottled && uiState.availability == WifiScanAvailability.READY) {
            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 720.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(text = "OS Throttle", color = SignalFair)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = strings.scanThrottledNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        when (uiState.availability) {
            WifiScanAvailability.MISSING_PERMISSION -> {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Lock,
                        title = strings.permissionRequired,
                        message = "Android requires Location / Nearby Wi-Fi permission to scan nearby access points and read SSIDs.",
                        actionLabel = strings.grantPermission,
                        onAction = { permissionLauncher.launch(wifiScannerManager.requiredScanPermissions()) },
                        modifier = Modifier.widthIn(max = 720.dp)
                    )
                }
            }
            WifiScanAvailability.WIFI_DISABLED -> {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.WifiOff,
                        title = "Wi-Fi Off",
                        message = strings.wifiDisabled,
                        actionLabel = strings.openWifiSettings,
                        onAction = { context.startActivity(wifiScannerManager.buildWifiSettingsIntent()) },
                        modifier = Modifier.widthIn(max = 720.dp)
                    )
                }
            }
            WifiScanAvailability.LOCATION_DISABLED -> {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.LocationOff,
                        title = "Location Off",
                        message = strings.locationServiceDisabled,
                        actionLabel = strings.openSettings,
                        onAction = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                        modifier = Modifier.widthIn(max = 720.dp)
                    )
                }
            }
            WifiScanAvailability.READY -> {
                if (uiState.networks.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.Wifi,
                            title = "No Scan Results Yet",
                            message = "Tap ${strings.scanNow} to request a nearby Wi-Fi scan from Android's WifiManager.",
                            actionLabel = strings.scanNow,
                            onAction = onStartScan,
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }
                } else {
                    items(uiState.networks, key = { "${it.bssid}_${it.frequencyMhz}" }) { network ->
                        val isFav = favoriteSsids.contains(network.ssid)
                        ElevatedInfoCard(
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .clickable { onNetworkClick(network) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = network.ssid,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${network.bandLabel} • Ch ${network.channel} • ${network.securityType}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${network.levelDbm} dBm • BSSID ${network.bssid}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SignalStrengthBadge(percentage = network.signalPercentage)
                                    IconButton(onClick = { onToggleFavorite(network) }) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = if (isFav) strings.removeFromFavorites else strings.addToFavorites,
                                            tint = if (isFav) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WifiNetworkDetailsScreen(
    ssid: String,
    bssid: String,
    levelDbm: Int,
    signalPercentage: Int,
    frequencyMhz: Int,
    channel: Int,
    bandLabel: String,
    securityType: String,
    capabilities: String,
    isFavorite: Boolean,
    wifiScannerManager: WifiScannerManager,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            WiFiGoTopBar(
                title = ssid,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) strings.removeFromFavorites else strings.addToFavorites,
                            tint = PrimaryBlue
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ssid, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "$bandLabel • $securityType",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SignalStrengthBadge(percentage = signalPercentage)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    DetailRow(strings.bssid, bssid)
                    DetailRow(strings.signalStrength, "$levelDbm dBm ($signalPercentage%)")
                    DetailRow(strings.frequency, "$frequencyMhz MHz ($bandLabel)")
                    DetailRow(strings.channel, if (channel > 0) "Channel $channel" else "N/A")
                    DetailRow(strings.security, securityType)
                    DetailRow(strings.capabilities, capabilities.ifBlank { "Standard" })
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { context.startActivity(wifiScannerManager.buildWifiSettingsIntent()) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.openWifiSettings)
                    }
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isFavorite) strings.removeFromFavorites else strings.addToFavorites)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
