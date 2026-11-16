package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import com.example.data.local.ThemeMode
import com.example.data.local.UserPreferences
import com.example.ui.components.ElevatedInfoCard
import com.example.ui.components.GoogleSignInCard
import com.example.ui.components.StatusPill
import com.example.ui.components.WiFiGoTopBar
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SignalExcellent
import com.example.ui.theme.SignalFair
import com.google.firebase.auth.FirebaseUser

@Composable
fun UserProfileScreen(
    authUser: FirebaseUser?,
    isSignInLoading: Boolean,
    signInError: String?,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val strings = LocalAppStrings.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = strings.navProfile,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            )
        }

        item {
            if (authUser != null) {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = authUser.displayName ?: "WiFiGo Member",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = authUser.email ?: authUser.uid,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            StatusPill(text = "Google Synced", color = SignalExcellent)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onSignOutClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button")
                    ) {
                        Text(strings.signOut)
                    }
                }
            } else {
                GoogleSignInCard(
                    title = strings.guestMode,
                    description = strings.guestModeDescription,
                    buttonLabel = strings.signInWithGoogle,
                    isLoading = isSignInLoading,
                    errorMessage = signInError,
                    onSignInClick = onSignInClick,
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        item {
            ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                ProfileMenuRow(Icons.Default.Settings, strings.settings, onOpenSettings)
                ProfileMenuRow(Icons.Default.Language, strings.language, onOpenLanguage)
                ProfileMenuRow(Icons.Default.Security, strings.permissions, onOpenPermissions)
                ProfileMenuRow(Icons.Default.Policy, "${strings.privacyPolicy} & ${strings.termsConditions}", onOpenPrivacyPolicy)
                ProfileMenuRow(Icons.Default.Info, strings.aboutApp, onOpenAbout)
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue)
        Spacer(modifier = Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSaveEndpoints: (String, String) -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    var showEndpointDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { WiFiGoTopBar(title = strings.settings, onBack = onBack) }
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
                    Text(strings.darkMode, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = preferences.themeMode == ThemeMode.SYSTEM,
                            onClick = { onSelectThemeMode(ThemeMode.SYSTEM) },
                            label = { Text("System") }
                        )
                        FilterChip(
                            selected = preferences.themeMode == ThemeMode.LIGHT,
                            onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                            label = { Text("Light") }
                        )
                        FilterChip(
                            selected = preferences.themeMode == ThemeMode.DARK,
                            onClick = { onSelectThemeMode(ThemeMode.DARK) },
                            label = { Text("Dark") }
                        )
                    }
                }
            }

            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.notifications, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Notify when offline file downloads complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.notificationsEnabled,
                            onCheckedChange = onToggleNotifications
                        )
                    }
                }
            }

            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Text(strings.configureEndpoint, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = preferences.speedTestDownloadUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showEndpointDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Edit Speed Test URL")
                    }
                }
            }

            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Text("Firebase Cloud Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cloud Firestore & Google Sign-In are provisioned for community hotspots, reviews, reports, and user favorite sync. Firebase Storage, FCM, and Crashlytics require manual setup in the Firebase Console.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    ProfileMenuRow(Icons.Default.Language, "${strings.language}: ${if (preferences.languageCode == "bn") "বাংলা" else "English"}", onOpenLanguage)
                    ProfileMenuRow(Icons.Default.Security, strings.permissions, onOpenPermissions)
                    ProfileMenuRow(Icons.Default.Policy, strings.privacyPolicy, onOpenPrivacyPolicy)
                    ProfileMenuRow(Icons.Default.Info, strings.aboutApp, onOpenAbout)
                }
            }
        }
    }

    if (showEndpointDialog) {
        var dlUrl by remember { mutableStateOf(preferences.speedTestDownloadUrl) }
        var ulUrl by remember { mutableStateOf(preferences.speedTestUploadUrl) }
        AlertDialog(
            onDismissRequest = { showEndpointDialog = false },
            title = { Text(strings.configureEndpoint) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dlUrl,
                        onValueChange = { dlUrl = it },
                        label = { Text("HTTPS Download Test URL") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ulUrl,
                        onValueChange = { ulUrl = it },
                        label = { Text("HTTPS Upload Test URL") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveEndpoints(dlUrl, ulUrl)
                        showEndpointDialog = false
                    }
                ) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showEndpointDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
fun LanguageSelectionScreen(
    currentCode: String,
    onSelectLanguage: (String) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    Scaffold(
        topBar = { WiFiGoTopBar(title = strings.language, onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedInfoCard(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .clickable { onSelectLanguage("en") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentCode == "en", onClick = { onSelectLanguage("en") })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("English", style = MaterialTheme.typography.titleMedium)
                        Text("Default language", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            ElevatedInfoCard(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .clickable { onSelectLanguage("bn") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentCode == "bn", onClick = { onSelectLanguage("bn") })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("বাংলা (Bengali)", style = MaterialTheme.typography.titleMedium)
                        Text("দ্বিতীয় ভাষা", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutAppScreen(onBack: () -> Unit) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    Scaffold(
        topBar = { WiFiGoTopBar(title = strings.aboutApp, onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_launcher_logo),
                            contentDescription = strings.appName,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(22.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(strings.appName, style = MaterialTheme.typography.headlineMedium, color = PrimaryBlue)
                        Text(strings.tagline, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Text(strings.developedBy, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Website: ${strings.companyWebsite}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(strings.companyWebsite))
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Visit Success Life IT")
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val strings = LocalAppStrings.current
    Scaffold(
        topBar = { WiFiGoTopBar(title = strings.privacyPolicy, onBack = onBack) }
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
                    Text("Privacy Policy — WiFiGo", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "WiFiGo by Success Life IT respects your privacy and Android OS security boundaries:\n\n" +
                            "• Wi-Fi Scanning & Location: Android requires Location / Nearby Wi-Fi permission only while scanning nearby access points or reading the connected SSID. WiFiGo never tracks your location in the background.\n" +
                            "• Camera & QR Codes: Camera access is used exclusively on-device via ML Kit to scan QR codes. Passphrases from QR codes are never uploaded or logged.\n" +
                            "• Offline Storage & Browser: Local files and saved web archives stay inside your device's private app storage or user-selected Storage Access Framework locations.\n" +
                            "• Cloud Features: Google Sign-In is optional. When signed in, only the profile preferences, favorites, public hotspots, reviews, and reports you explicitly submit are stored in Cloud Firestore.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Text(strings.termsConditions, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "WiFiGo provides legitimate network diagnostics, authorized Wi-Fi suggestion assistance, speed measurements against configurable HTTPS endpoints, and offline archiving for permitted webpages. Attempting to bypass network security, crack passwords, or submit false hotspot information is strictly prohibited.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionExplanationScreen(onBack: () -> Unit) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    var refreshTick by remember { mutableStateOf(0) }
    val requestMultipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTick++
    }

    val hasCamera = remember(refreshTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
    val hasLocation = remember(refreshTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
    val hasNearbyWifi = remember(refreshTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }
    val hasNotifications = remember(refreshTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }

    Scaffold(
        topBar = { WiFiGoTopBar(title = strings.permissions, onBack = onBack) }
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
                PermissionCard(
                    title = "Location & Nearby Wi-Fi",
                    description = "Required by Android OS to perform nearby Wi-Fi scans and display connected SSID information.",
                    granted = hasLocation && hasNearbyWifi,
                    onGrant = {
                        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                        }
                        requestMultipleLauncher.launch(perms.toTypedArray())
                    }
                )
            }
            item {
                PermissionCard(
                    title = "Camera",
                    description = "Used exclusively for scanning Wi-Fi QR codes with CameraX and on-device ML Kit.",
                    granted = hasCamera,
                    onGrant = { requestMultipleLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }
                )
            }
            item {
                PermissionCard(
                    title = "Notifications",
                    description = "Optional notifications when background file downloads complete.",
                    granted = hasNotifications,
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestMultipleLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    }
                )
            }
            item {
                ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Text("Storage Access Framework (Zero Broad Permission)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "WiFiGo uses Android's Storage Access Framework and Photo Picker so it never requests invasive broad media or external storage permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.openSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(
                text = if (granted) "Granted" else "Not Granted",
                color = if (granted) SignalExcellent else SignalFair
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!granted) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onGrant) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Grant Permission")
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalExcellent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Active", style = MaterialTheme.typography.labelMedium, color = SignalExcellent)
            }
        }
    }
}
