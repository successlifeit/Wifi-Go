package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.OfflineStorageManager
import com.example.data.local.db.DownloadTaskEntity
import com.example.data.local.db.FavoriteNetworkEntity
import com.example.data.wifi.ActiveNetworkSnapshot
import com.example.ui.components.ElevatedInfoCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SignalStrengthBadge
import com.example.ui.components.StatusPill
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryBlue
import com.example.ui.theme.SignalExcellent
import com.example.ui.theme.SignalWeak

data class DashboardShortcut(
    val title: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun HomeDashboardScreen(
    activeNetwork: ActiveNetworkSnapshot,
    recentDownloads: List<DownloadTaskEntity>,
    favoriteNetworks: List<FavoriteNetworkEntity>,
    onOpenScanner: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPublicWifi: () -> Unit,
    onOpenNetworkInfo: () -> Unit,
    onOpenDownloadDetails: (String) -> Unit
) {
    val strings = LocalAppStrings.current

    val shortcuts = listOf(
        DashboardShortcut(strings.wifiScanner, Icons.Default.Radar, "shortcut_wifi_scanner", onOpenScanner),
        DashboardShortcut(strings.qrScanner, Icons.Default.QrCodeScanner, "shortcut_qr_scanner", onOpenQrScanner),
        DashboardShortcut(strings.speedTest, Icons.Default.Speed, "shortcut_speed_test", onOpenSpeedTest),
        DashboardShortcut(strings.offlineStorage, Icons.Default.Folder, "shortcut_offline_storage", onOpenStorage),
        DashboardShortcut(strings.offlineBrowser, Icons.Default.Language, "shortcut_offline_browser", onOpenBrowser),
        DashboardShortcut(strings.downloadManager, Icons.Default.Download, "shortcut_downloads", onOpenDownloads),
        DashboardShortcut(strings.publicWifiFinder, Icons.Default.Public, "shortcut_public_wifi", onOpenPublicWifi),
        DashboardShortcut(strings.networkInfo, Icons.Default.Info, "shortcut_network_info", onOpenNetworkInfo)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(
                        text = if (activeNetwork.isValidated) strings.internetOnline else strings.internetOffline,
                        color = if (activeNetwork.isValidated) SignalExcellent else SignalWeak
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hero Connectivity Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_connection_card")
                        .clickable { onOpenNetworkInfo() },
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_banner),
                            contentDescription = null,
                            modifier = Modifier
                                .matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            SecondaryBlue.copy(alpha = 0.90f),
                                            PrimaryBlue.copy(alpha = 0.80f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (activeNetwork.isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (activeNetwork.isWifiConnected) {
                                                activeNetwork.ssid ?: strings.hiddenOrRestrictedSsid
                                            } else {
                                                activeNetwork.transportLabel
                                            },
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (activeNetwork.isWifiConnected) strings.connected else strings.disconnected,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                activeNetwork.signalPercentage?.let { pct ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "$pct%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                QuickMetricChip(
                                    label = strings.frequency,
                                    value = activeNetwork.frequencyMhz?.let { "${it} MHz" } ?: "--",
                                    modifier = Modifier.weight(1f)
                                )
                                QuickMetricChip(
                                    label = strings.signalStrength,
                                    value = activeNetwork.rssiDbm?.let { "$it dBm" } ?: "--",
                                    modifier = Modifier.weight(1f)
                                )
                                QuickMetricChip(
                                    label = "Type",
                                    value = activeNetwork.transportLabel,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Featured Speed Test & Public WiFi Finder CTA row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedInfoCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenSpeedTest() }
                        .testTag("cta_speed_test")
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(strings.speedTest, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ping, Download & Upload",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ElevatedInfoCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenPublicWifi() }
                        .testTag("cta_public_wifi")
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(strings.publicWifiFinder, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Community Hotspots",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 8 Dashboard Shortcuts Grid (2 rows x 4 or adaptive 2 columns)
        item {
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                Text(
                    text = strings.quickTools,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                shortcuts.chunked(4).forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { item ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(item.testTag)
                                    .clickable { item.onClick() },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.title,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Favorite Networks
        item {
            Text(
                text = strings.favoriteNetworks,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            )
        }
        if (favoriteNetworks.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Star,
                    title = strings.favoriteNetworks,
                    message = strings.noFavoriteNetworks,
                    actionLabel = strings.scanNow,
                    onAction = onOpenScanner,
                    modifier = Modifier.widthIn(max = 720.dp)
                )
            }
        } else {
            items(favoriteNetworks.take(4), key = { it.ssid }) { fav ->
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 720.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fav.ssid, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${fav.securityType} • ${if (fav.frequencyMhz > 0) "${fav.frequencyMhz} MHz" else "Saved"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Star, contentDescription = null, tint = PrimaryBlue)
                    }
                }
            }
        }

        // Recently Downloaded Files
        item {
            Text(
                text = strings.recentDownloads,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            )
        }
        if (recentDownloads.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Download,
                    title = strings.recentDownloads,
                    message = strings.noRecentDownloads,
                    actionLabel = strings.downloadManager,
                    onAction = onOpenDownloads,
                    modifier = Modifier.widthIn(max = 720.dp)
                )
            }
        } else {
            items(recentDownloads, key = { it.id }) { dl ->
                ElevatedInfoCard(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .clickable { onOpenDownloadDetails(dl.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dl.fileName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${dl.status} • ${OfflineStorageManager.formatBytes(dl.downloadedBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = dl.status,
                            color = if (dl.status == "COMPLETED") SignalExcellent else PrimaryBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
