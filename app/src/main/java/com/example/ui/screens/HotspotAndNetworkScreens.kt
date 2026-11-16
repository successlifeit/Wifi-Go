package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.remote.HotspotReview
import com.example.data.remote.PublicHotspot
import com.example.data.wifi.ActiveNetworkSnapshot
import com.example.data.wifi.WifiScannerManager
import com.example.ui.components.ElevatedInfoCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GoogleSignInCard
import com.example.ui.components.StatusPill
import com.example.ui.components.WiFiGoTopBar
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryBlue
import com.example.ui.theme.SignalExcellent
import com.example.ui.viewmodel.HotspotUiState
import kotlin.math.abs

@Composable
fun PublicWifiFinderScreen(
    uiState: HotspotUiState,
    searchQuery: String,
    actionMessage: String?,
    isSignInLoading: Boolean,
    signInError: String?,
    onSearchChange: (String) -> Unit,
    onSignInClick: () -> Unit,
    onSubmitHotspot: (
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
    ) -> Unit,
    onOpenHotspotDetails: (String) -> Unit,
    onClearMessage: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSubmitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(actionMessage) {
        if (!actionMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(actionMessage)
            onClearMessage()
        }
    }

    Scaffold(
        topBar = { WiFiGoTopBar(title = strings.publicWifiFinder, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState !is HotspotUiState.GuestMode) {
                FloatingActionButton(
                    onClick = { showSubmitDialog = true },
                    containerColor = PrimaryBlue,
                    modifier = Modifier.testTag("add_hotspot_fab")
                ) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = strings.submitHotspot, tint = Color.White)
                }
            }
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
            when (uiState) {
                is HotspotUiState.GuestMode -> {
                    item {
                        GoogleSignInCard(
                            title = "Community Public Wi-Fi Finder",
                            description = "Public Wi-Fi Finder uses Cloud Firestore for community-verified hotspots, user reports, and reviews. Sign in with Google to browse or share public hotspots.",
                            buttonLabel = strings.signInWithGoogle,
                            isLoading = isSignInLoading,
                            errorMessage = signInError,
                            onSignInClick = onSignInClick,
                            modifier = Modifier.widthIn(max = 640.dp)
                        )
                    }
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.Public,
                            title = "No Hardcoded Fake Hotspots",
                            message = "WiFiGo never fabricates public Wi-Fi locations or passwords. Once signed in, real community-submitted hotspots appear here and on the spatial map.",
                            modifier = Modifier.widthIn(max = 640.dp)
                        )
                    }
                }
                is HotspotUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is HotspotUiState.Error -> {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.Public,
                            title = "Hotspot Sync Error",
                            message = uiState.message,
                            modifier = Modifier.widthIn(max = 640.dp)
                        )
                    }
                }
                is HotspotUiState.Success -> {
                    item {
                        Column(modifier = Modifier.widthIn(max = 720.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchChange,
                                placeholder = { Text(strings.searchHotspots) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            HotspotSpatialMapCard(hotspots = uiState.hotspots)
                        }
                    }

                    if (uiState.hotspots.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Public,
                                title = "No Community Hotspots Yet",
                                message = "Be the first to share a legitimate public Wi-Fi venue with the community.",
                                actionLabel = strings.submitHotspot,
                                onAction = { showSubmitDialog = true },
                                modifier = Modifier.widthIn(max = 720.dp)
                            )
                        }
                    } else {
                        items(uiState.hotspots, key = { it.id }) { hs ->
                            ElevatedInfoCard(
                                modifier = Modifier
                                    .widthIn(max = 720.dp)
                                    .clickable { onOpenHotspotDetails(hs.id) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(hs.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "${hs.venueType.replaceFirstChar { it.uppercase() }} • ${hs.city} • SSID: ${hs.ssid}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = hs.address,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusPill(
                                        text = if (hs.isFree) "Free • ${hs.securityType}" else hs.securityType,
                                        color = if (hs.isFree) SignalExcellent else PrimaryBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSubmitDialog) {
        SubmitHotspotDialog(
            onDismiss = { showSubmitDialog = false },
            onSubmit = { name, venueType, address, city, lat, lon, ssid, sec, free, notes ->
                onSubmitHotspot(name, venueType, address, city, lat, lon, ssid, sec, free, notes)
                showSubmitDialog = false
            }
        )
    }
}

@Composable
private fun HotspotSpatialMapCard(hotspots: List<PublicHotspot>) {
    ElevatedInfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hotspot Radar Map", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${hotspots.size} locations",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            listOf(0.28f, 0.55f, 0.85f).forEach { factor ->
                drawCircle(
                    color = gridColor,
                    radius = (minOf(size.width, size.height) / 2f) * factor,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            drawLine(gridColor, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = 1.dp.toPx())

            hotspots.forEachIndexed { idx, hs ->
                val xNorm = (((hs.longitude + 180.0) % 360.0) / 360.0).toFloat()
                val yNorm = (1.0 - ((hs.latitude + 90.0) / 180.0)).toFloat()
                val px = (0.15f + (abs(xNorm * 7f + idx * 0.13f) % 0.7f)) * size.width
                val py = (0.18f + (abs(yNorm * 5f + idx * 0.19f) % 0.64f)) * size.height
                drawCircle(
                    color = PrimaryBlue.copy(alpha = 0.25f),
                    radius = 12.dp.toPx(),
                    center = Offset(px, py)
                )
                drawCircle(
                    color = SecondaryBlue,
                    radius = 6.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }
    }
}

@Composable
private fun SubmitHotspotDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, Double, Double, String, String, Boolean, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var venueType by remember { mutableStateOf("cafe") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var security by remember { mutableStateOf("Open") }
    var isFree by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Public Wi-Fi Hotspot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Venue Name") }, singleLine = true)
                OutlinedTextField(value = ssid, onValueChange = { ssid = it }, label = { Text("Broadcast SSID") }, singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address / Landmark") }, singleLine = true)
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("cafe", "library", "coworking", "airport").forEach { vt ->
                        FilterChip(
                            selected = venueType == vt,
                            onClick = { venueType = vt },
                            label = { Text(vt) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Open", "Captive Portal", "WPA2").forEach { sec ->
                        FilterChip(
                            selected = security == sec,
                            onClick = { security = sec },
                            label = { Text(sec) }
                        )
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Access Notes (optional)") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().length >= 2 && ssid.isNotBlank() && address.trim().length >= 2 && city.isNotBlank(),
                onClick = {
                    onSubmit(name, venueType, address, city, 23.8103, 90.4125, ssid, security, isFree, notes)
                }
            ) { Text("Submit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun HotspotDetailsScreen(
    hotspot: PublicHotspot?,
    reviews: List<HotspotReview>,
    onSaveFavorite: (PublicHotspot) -> Unit,
    onAddReview: (String, Int, String) -> Unit,
    onSubmitReport: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var showReviewDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            WiFiGoTopBar(
                title = hotspot?.name ?: "Hotspot Details",
                onBack = onBack,
                actions = {
                    if (hotspot != null) {
                        IconButton(onClick = { onSaveFavorite(hotspot) }) {
                            Icon(Icons.Default.Star, contentDescription = strings.addToFavorites, tint = PrimaryBlue)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (hotspot == null) {
            EmptyStateCard(
                icon = Icons.Default.Public,
                title = "Hotspot Not Found",
                message = "Sign in or select a hotspot from Public WiFi Finder.",
                actionLabel = strings.back,
                onAction = onBack,
                modifier = Modifier.padding(padding).padding(16.dp)
            )
        } else {
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
                        Text(hotspot.name, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        StatusPill(
                            text = if (hotspot.isFree) "Free Public Wi-Fi" else "Paid / Customer Wi-Fi",
                            color = SignalExcellent
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DetailRow("SSID", hotspot.ssid)
                        DetailRow("Security", hotspot.securityType)
                        DetailRow("Venue", hotspot.venueType)
                        DetailRow("Address", hotspot.address)
                        DetailRow("City", hotspot.city)
                        if (!hotspot.notes.isNullOrBlank()) {
                            DetailRow("Notes", hotspot.notes)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val geoUri = Uri.parse(
                                        "geo:${hotspot.latitude},${hotspot.longitude}?q=${Uri.encode("${hotspot.name} ${hotspot.address}")}"
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW, geoUri)
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Directions")
                            }
                            OutlinedButton(
                                onClick = { showReviewDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Review")
                            }
                            IconButton(onClick = { showReportDialog = true }) {
                                Icon(Icons.Default.Flag, contentDescription = "Report Hotspot")
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Community Reviews (${reviews.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 640.dp)
                    )
                }

                if (reviews.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.RateReview,
                            title = "No Reviews Yet",
                            message = "Share signal quality and reliability feedback for this hotspot.",
                            actionLabel = "Write Review",
                            onAction = { showReviewDialog = true },
                            modifier = Modifier.widthIn(max = 640.dp)
                        )
                    }
                } else {
                    items(reviews, key = { it.id }) { rev ->
                        ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(rev.authorName, style = MaterialTheme.typography.titleSmall)
                                Text("${rev.rating}/5 ★", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(rev.comment, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showReviewDialog && hotspot != null) {
        var rating by remember { mutableIntStateOf(5) }
        var comment by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReviewDialog = false },
            title = { Text("Add Review") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { r ->
                            FilterChip(selected = rating == r, onClick = { rating = r }, label = { Text("$r★") })
                        }
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Comment") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = comment.isNotBlank(),
                    onClick = {
                        onAddReview(hotspot.id, rating, comment)
                        showReviewDialog = false
                    }
                ) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { showReviewDialog = false }) { Text("Cancel") } }
        )
    }

    if (showReportDialog && hotspot != null) {
        var reason by remember { mutableStateOf("offline") }
        var details by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Hotspot Issue") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("offline", "wrong_ssid", "wrong_location").forEach { r ->
                            FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r) })
                        }
                    }
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("Details") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = details.isNotBlank(),
                    onClick = {
                        onSubmitReport(hotspot.id, reason, details)
                        showReportDialog = false
                    }
                ) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun NetworkInfoScreen(
    snapshot: ActiveNetworkSnapshot,
    wifiScannerManager: WifiScannerManager,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    Scaffold(
        topBar = { WiFiGoTopBar(title = strings.networkInfo, onBack = onBack) }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(snapshot.transportLabel, style = MaterialTheme.typography.titleLarge)
                        }
                        StatusPill(
                            text = if (snapshot.isValidated) strings.internetOnline else strings.internetOffline,
                            color = if (snapshot.isValidated) SignalExcellent else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Wi-Fi Status", if (snapshot.isWifiConnected) strings.connected else strings.disconnected)
                    DetailRow("SSID", snapshot.ssid ?: strings.hiddenOrRestrictedSsid)
                    DetailRow("BSSID", snapshot.bssid ?: "Restricted by Android")
                    DetailRow("IP Address", snapshot.ipAddress ?: "Unavailable")
                    DetailRow("Gateway", snapshot.gateway ?: "Unavailable")
                    DetailRow("DNS Servers", snapshot.dnsServers.joinToString(", ").ifBlank { "Unavailable" })
                    DetailRow("Interface", snapshot.interfaceName ?: "Unavailable")
                    DetailRow("Link Speed", snapshot.linkSpeedMbps?.let { "$it Mbps" } ?: "Unavailable")
                    DetailRow("Signal Strength", snapshot.rssiDbm?.let { "$it dBm (${snapshot.signalPercentage}%)" } ?: "Unavailable")
                    DetailRow("Frequency / Channel", snapshot.frequencyMhz?.let { "$it MHz (Ch ${snapshot.channel})" } ?: "Unavailable")
                }
            }

            if (!snapshot.permissionNote.isNullOrBlank()) {
                item {
                    ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                        Text(
                            text = snapshot.permissionNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { context.startActivity(wifiScannerManager.buildWifiSettingsIntent()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                ) {
                    Text(strings.openWifiSettings)
                }
            }
        }
    }
}
