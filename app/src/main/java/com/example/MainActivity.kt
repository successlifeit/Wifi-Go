package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.local.OfflineStorageManager
import com.example.data.local.ThemeMode
import com.example.data.network.DownloadRepository
import com.example.data.network.SpeedTestEngine
import com.example.data.remote.FirestoreHotspotRepository
import com.example.data.remote.FirestoreUserRepository
import com.example.data.remote.GoogleAuthManager
import com.example.data.wifi.WifiScannerManager
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.navigation.AboutRoute
import com.example.ui.navigation.BrowserRoute
import com.example.ui.navigation.DownloadDetailsRoute
import com.example.ui.navigation.DownloadsRoute
import com.example.ui.navigation.FileDetailsRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.HotspotDetailsRoute
import com.example.ui.navigation.LanguageRoute
import com.example.ui.navigation.NetworkInfoRoute
import com.example.ui.navigation.OnboardingRoute
import com.example.ui.navigation.PermissionsRoute
import com.example.ui.navigation.PrivacyPolicyRoute
import com.example.ui.navigation.ProfileRoute
import com.example.ui.navigation.PublicWifiFinderRoute
import com.example.ui.navigation.QrResultRoute
import com.example.ui.navigation.QrScannerRoute
import com.example.ui.navigation.SavedPagesRoute
import com.example.ui.navigation.ScannerRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SpeedHistoryRoute
import com.example.ui.navigation.SpeedTestRoute
import com.example.ui.navigation.SplashRoute
import com.example.ui.navigation.StorageRoute
import com.example.ui.navigation.WifiDetailsRoute
import com.example.ui.screens.AboutAppScreen
import com.example.ui.screens.DownloadDetailsScreen
import com.example.ui.screens.DownloadManagerScreen
import com.example.ui.screens.FileDetailsScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.HotspotDetailsScreen
import com.example.ui.screens.LanguageSelectionScreen
import com.example.ui.screens.NetworkInfoScreen
import com.example.ui.screens.OfflineBrowserScreen
import com.example.ui.screens.OfflineStorageScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PermissionExplanationScreen
import com.example.ui.screens.PrivacyPolicyScreen
import com.example.ui.screens.PublicWifiFinderScreen
import com.example.ui.screens.QrScanResultScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.SavedWebpagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SpeedTestHistoryScreen
import com.example.ui.screens.SpeedTestScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.screens.WifiNetworkDetailsScreen
import com.example.ui.screens.WifiScannerScreen
import com.example.ui.theme.WiFiGoTheme
import com.example.ui.viewmodel.HotspotFinderViewModel
import com.example.ui.viewmodel.HotspotUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SpeedTestViewModel
import com.example.ui.viewmodel.StorageAndDownloadsViewModel
import com.example.ui.viewmodel.WifiScannerViewModel
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val credentialManager = CredentialManager.create(this)
        GoogleAuthManager.attemptAutoSignIn(
            context = this,
            credentialManager = credentialManager,
            scope = lifecycleScope,
            onAuthSuccess = {},
            onUnauthenticated = {}
        )

        setContent {
            WiFiGoRootApp(credentialManager = credentialManager)
        }
    }
}

private data class BottomTabItem(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun WiFiGoRootApp(credentialManager: CredentialManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val wifiScannerManager = remember { WifiScannerManager(context.applicationContext) }

    val mainViewModel: MainViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as WiFiGoApp
                val fsUserRepo = try {
                    val dbId = app.getString(R.string.firestore_database_id)
                    FirestoreUserRepository(FirebaseFirestore.getInstance(dbId))
                } catch (_: Exception) {
                    null
                }
                MainViewModel(
                    preferencesRepository = app.preferencesRepository,
                    wifiScannerManager = wifiScannerManager,
                    favoriteNetworkDao = app.database.favoriteNetworkDao(),
                    downloadDao = app.database.downloadDao(),
                    firestoreUserRepository = fsUserRepo
                )
            }
        }
    )

    val wifiScannerViewModel: WifiScannerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WifiScannerViewModel(wifiScannerManager) }
        }
    )

    val speedTestViewModel: SpeedTestViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as WiFiGoApp
                SpeedTestViewModel(
                    speedTestEngine = SpeedTestEngine(),
                    speedTestDao = app.database.speedTestDao(),
                    preferencesRepository = app.preferencesRepository,
                    wifiScannerManager = wifiScannerManager
                )
            }
        }
    )

    val storageAndDownloadsViewModel: StorageAndDownloadsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as WiFiGoApp
                val storageManager = OfflineStorageManager(app, app.database.localFileDao())
                val downloadRepository = DownloadRepository(app, app.database.downloadDao(), storageManager)
                StorageAndDownloadsViewModel(
                    storageManager = storageManager,
                    localFileDao = app.database.localFileDao(),
                    downloadRepository = downloadRepository,
                    savedWebpageDao = app.database.savedWebpageDao()
                )
            }
        }
    )

    val prefs by mainViewModel.preferences.collectAsStateWithLifecycle()
    val activeNetwork by mainViewModel.activeNetwork.collectAsStateWithLifecycle()
    val favorites by mainViewModel.favoriteNetworks.collectAsStateWithLifecycle()
    val recentDownloads by mainViewModel.recentDownloads.collectAsStateWithLifecycle()
    val authUser by mainViewModel.authUser.collectAsStateWithLifecycle()

    LaunchedEffect(authUser?.uid) {
        if (authUser != null) {
            mainViewModel.syncCurrentPreferencesToCloud()
        }
    }

    var isSignInLoading by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }

    val onGoogleSignInClick = {
        isSignInLoading = true
        signInError = null
        GoogleAuthManager.onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            scope = coroutineScope,
            onAuthSuccess = {
                isSignInLoading = false
                mainViewModel.syncCurrentPreferencesToCloud()
            },
            onAuthError = { msg ->
                isSignInLoading = false
                signInError = msg
            },
            onAuthCancelled = {
                isSignInLoading = false
            }
        )
    }

    val onSignOutClick = {
        GoogleAuthManager.signOut(
            credentialManager = credentialManager,
            scope = coroutineScope
        )
    }

    val darkTheme = when (prefs.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val appStrings = stringsForLanguage(prefs.languageCode)

    val hotspotViewModel: HotspotFinderViewModel = viewModel(
        key = authUser?.uid ?: "guest",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as WiFiGoApp
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                HotspotFinderViewModel(
                    hotspotRepository = FirestoreHotspotRepository(db),
                    userRepository = FirestoreUserRepository(db),
                    favoriteNetworkDao = app.database.favoriteNetworkDao(),
                    currentUserId = authUser?.uid,
                    currentUserName = authUser?.displayName
                )
            }
        }
    )

    CompositionLocalProvider(LocalAppStrings provides appStrings) {
        WiFiGoTheme(darkTheme = darkTheme) {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDest = navBackStackEntry?.destination

            val bottomTabs = listOf(
                BottomTabItem(HomeRoute, appStrings.navHome, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
                BottomTabItem(ScannerRoute, appStrings.navScanner, Icons.Filled.Radar, Icons.Outlined.Radar, "nav_scanner"),
                BottomTabItem(DownloadsRoute, appStrings.navDownloads, Icons.Filled.Download, Icons.Outlined.Download, "nav_downloads"),
                BottomTabItem(StorageRoute, appStrings.navStorage, Icons.Filled.Folder, Icons.Outlined.Folder, "nav_storage"),
                BottomTabItem(ProfileRoute, appStrings.navProfile, Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
            )

            val showTopLevelNav = currentDest?.let { dest ->
                dest.hasRoute<HomeRoute>() ||
                    dest.hasRoute<ScannerRoute>() ||
                    dest.hasRoute<DownloadsRoute>() ||
                    dest.hasRoute<StorageRoute>() ||
                    dest.hasRoute<ProfileRoute>()
            } ?: false

            fun navigateTab(route: Any) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (showTopLevelNav && !useNavigationRail) {
                            NavigationBar {
                                bottomTabs.forEach { tab ->
                                    val selected = when (tab.route) {
                                        is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                        is ScannerRoute -> currentDest?.hasRoute<ScannerRoute>() == true
                                        is DownloadsRoute -> currentDest?.hasRoute<DownloadsRoute>() == true
                                        is StorageRoute -> currentDest?.hasRoute<StorageRoute>() == true
                                        is ProfileRoute -> currentDest?.hasRoute<ProfileRoute>() == true
                                        else -> false
                                    }
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTab(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (showTopLevelNav && useNavigationRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                bottomTabs.forEach { tab ->
                                    val selected = when (tab.route) {
                                        is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                        is ScannerRoute -> currentDest?.hasRoute<ScannerRoute>() == true
                                        is DownloadsRoute -> currentDest?.hasRoute<DownloadsRoute>() == true
                                        is StorageRoute -> currentDest?.hasRoute<StorageRoute>() == true
                                        is ProfileRoute -> currentDest?.hasRoute<ProfileRoute>() == true
                                        else -> false
                                    }
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTab(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = SplashRoute,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable<SplashRoute> {
                                SplashScreen(
                                    onboardingCompleted = prefs.onboardingCompleted,
                                    onNavigateToOnboarding = {
                                        navController.navigate(OnboardingRoute) {
                                            popUpTo<SplashRoute> { inclusive = true }
                                        }
                                    },
                                    onNavigateToHome = {
                                        navController.navigate(HomeRoute) {
                                            popUpTo<SplashRoute> { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable<OnboardingRoute> {
                                OnboardingScreen(
                                    onFinishOnboarding = {
                                        mainViewModel.completeOnboarding()
                                        navController.navigate(HomeRoute) {
                                            popUpTo<OnboardingRoute> { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable<HomeRoute> {
                                HomeDashboardScreen(
                                    activeNetwork = activeNetwork,
                                    recentDownloads = recentDownloads,
                                    favoriteNetworks = favorites,
                                    onOpenScanner = { navigateTab(ScannerRoute) },
                                    onOpenQrScanner = { navController.navigate(QrScannerRoute) },
                                    onOpenSpeedTest = { navController.navigate(SpeedTestRoute) },
                                    onOpenStorage = { navigateTab(StorageRoute) },
                                    onOpenBrowser = { navController.navigate(BrowserRoute()) },
                                    onOpenDownloads = { navigateTab(DownloadsRoute) },
                                    onOpenPublicWifi = { navController.navigate(PublicWifiFinderRoute) },
                                    onOpenNetworkInfo = { navController.navigate(NetworkInfoRoute) },
                                    onOpenDownloadDetails = { id -> navController.navigate(DownloadDetailsRoute(id)) }
                                )
                            }

                            composable<ScannerRoute> {
                                val scanState by wifiScannerViewModel.uiState.collectAsStateWithLifecycle()
                                WifiScannerScreen(
                                    uiState = scanState,
                                    wifiScannerManager = wifiScannerManager,
                                    favoriteSsids = favorites.map { it.ssid }.toSet(),
                                    onRefreshAvailability = wifiScannerViewModel::refreshAvailabilityAndResults,
                                    onStartScan = wifiScannerViewModel::startScan,
                                    onSelectBandFilter = wifiScannerViewModel::setBandFilter,
                                    onToggleSort = wifiScannerViewModel::toggleSortOrder,
                                    onNetworkClick = { net ->
                                        navController.navigate(
                                            WifiDetailsRoute(
                                                ssid = net.ssid,
                                                bssid = net.bssid,
                                                levelDbm = net.levelDbm,
                                                signalPercentage = net.signalPercentage,
                                                frequencyMhz = net.frequencyMhz,
                                                channel = net.channel,
                                                bandLabel = net.bandLabel,
                                                securityType = net.securityType,
                                                capabilities = net.capabilities
                                            )
                                        )
                                    },
                                    onToggleFavorite = { net ->
                                        mainViewModel.toggleFavoriteNetwork(
                                            ssid = net.ssid,
                                            bssid = net.bssid,
                                            securityType = net.securityType,
                                            frequencyMhz = net.frequencyMhz
                                        )
                                    },
                                    onOpenQrScanner = { navController.navigate(QrScannerRoute) }
                                )
                            }

                            composable<WifiDetailsRoute> { backStackEntry ->
                                val route = backStackEntry.toRoute<WifiDetailsRoute>()
                                val isFav = favorites.any { it.ssid == route.ssid }
                                WifiNetworkDetailsScreen(
                                    ssid = route.ssid,
                                    bssid = route.bssid,
                                    levelDbm = route.levelDbm,
                                    signalPercentage = route.signalPercentage,
                                    frequencyMhz = route.frequencyMhz,
                                    channel = route.channel,
                                    bandLabel = route.bandLabel,
                                    securityType = route.securityType,
                                    capabilities = route.capabilities,
                                    isFavorite = isFav,
                                    wifiScannerManager = wifiScannerManager,
                                    onToggleFavorite = {
                                        mainViewModel.toggleFavoriteNetwork(
                                            ssid = route.ssid,
                                            bssid = route.bssid,
                                            securityType = route.securityType,
                                            frequencyMhz = route.frequencyMhz
                                        )
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<QrScannerRoute> {
                                QrScannerScreen(
                                    onQrDetected = { raw ->
                                        navController.navigate(QrResultRoute(raw)) {
                                            popUpTo<QrScannerRoute> { inclusive = true }
                                        }
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<QrResultRoute> { backStackEntry ->
                                val route = backStackEntry.toRoute<QrResultRoute>()
                                QrScanResultScreen(
                                    rawPayload = route.rawPayload,
                                    wifiScannerManager = wifiScannerManager,
                                    onOpenInBrowser = { url ->
                                        navController.navigate(BrowserRoute(initialUrl = url))
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<SpeedTestRoute> {
                                val progress by speedTestViewModel.progress.collectAsStateWithLifecycle()
                                SpeedTestScreen(
                                    progress = progress,
                                    configuredEndpoint = prefs.speedTestDownloadUrl,
                                    onStartTest = speedTestViewModel::startTest,
                                    onCancelTest = speedTestViewModel::cancelTest,
                                    onOpenHistory = { navController.navigate(SpeedHistoryRoute) },
                                    onOpenSettings = { navController.navigate(SettingsRoute) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<SpeedHistoryRoute> {
                                val records by speedTestViewModel.history.collectAsStateWithLifecycle()
                                SpeedTestHistoryScreen(
                                    records = records,
                                    onDeleteRecord = speedTestViewModel::deleteRecord,
                                    onClearAll = speedTestViewModel::clearHistory,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<StorageRoute> {
                                val files by storageAndDownloadsViewModel.filteredFiles.collectAsStateWithLifecycle()
                                val selectedCat by storageAndDownloadsViewModel.selectedCategory.collectAsStateWithLifecycle()
                                val query by storageAndDownloadsViewModel.searchQuery.collectAsStateWithLifecycle()
                                val stats by storageAndDownloadsViewModel.storageStats.collectAsStateWithLifecycle()
                                OfflineStorageScreen(
                                    files = files,
                                    selectedCategory = selectedCat,
                                    searchQuery = query,
                                    storageStats = stats,
                                    onSelectCategory = storageAndDownloadsViewModel::setCategory,
                                    onSearchChange = storageAndDownloadsViewModel::setSearchQuery,
                                    onImportSafUri = storageAndDownloadsViewModel::importSafDocument,
                                    onRefreshStats = storageAndDownloadsViewModel::refreshStorageStats,
                                    onOpenFileDetails = { id -> navController.navigate(FileDetailsRoute(id)) }
                                )
                            }

                            composable<FileDetailsRoute> { backStackEntry ->
                                val route = backStackEntry.toRoute<FileDetailsRoute>()
                                val allFiles by storageAndDownloadsViewModel.allFiles.collectAsStateWithLifecycle()
                                val file = allFiles.firstOrNull { it.id == route.fileId }
                                FileDetailsScreen(
                                    file = file,
                                    storageManager = storageAndDownloadsViewModel.storageManager,
                                    onRename = storageAndDownloadsViewModel::renameFile,
                                    onDelete = storageAndDownloadsViewModel::deleteFile,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<BrowserRoute> { backStackEntry ->
                                val route = backStackEntry.toRoute<BrowserRoute>()
                                OfflineBrowserScreen(
                                    initialUrl = route.initialUrl,
                                    archivePath = route.archivePath,
                                    storageManager = storageAndDownloadsViewModel.storageManager,
                                    onSaveArchive = storageAndDownloadsViewModel::registerSavedWebArchive,
                                    onOpenSavedPages = { navController.navigate(SavedPagesRoute) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<SavedPagesRoute> {
                                val pages by storageAndDownloadsViewModel.savedWebpages.collectAsStateWithLifecycle()
                                SavedWebpagesScreen(
                                    pages = pages,
                                    onOpenSavedPage = { page ->
                                        navController.navigate(
                                            BrowserRoute(initialUrl = page.url, archivePath = page.archivePath)
                                        )
                                    },
                                    onToggleBookmark = storageAndDownloadsViewModel::toggleBookmark,
                                    onDeletePage = storageAndDownloadsViewModel::deleteSavedPage,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<DownloadsRoute> {
                                val downloads by storageAndDownloadsViewModel.downloads.collectAsStateWithLifecycle()
                                DownloadManagerScreen(
                                    downloads = downloads,
                                    onEnqueueDownload = storageAndDownloadsViewModel::enqueueDownload,
                                    onPause = storageAndDownloadsViewModel::pauseDownload,
                                    onResume = storageAndDownloadsViewModel::resumeDownload,
                                    onCancel = storageAndDownloadsViewModel::cancelDownload,
                                    onDelete = storageAndDownloadsViewModel::deleteDownload,
                                    onOpenDetails = { id -> navController.navigate(DownloadDetailsRoute(id)) }
                                )
                            }

                            composable<DownloadDetailsRoute> { backStackEntry ->
                                val route = backStackEntry.toRoute<DownloadDetailsRoute>()
                                val downloads by storageAndDownloadsViewModel.downloads.collectAsStateWithLifecycle()
                                val task = downloads.firstOrNull { it.id == route.downloadId }
                                DownloadDetailsScreen(
                                    task = task,
                                    downloadRepository = storageAndDownloadsViewModel.downloadRepository,
                                    onPause = storageAndDownloadsViewModel::pauseDownload,
                                    onResume = storageAndDownloadsViewModel::resumeDownload,
                                    onDelete = storageAndDownloadsViewModel::deleteDownload,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<PublicWifiFinderRoute> {
                                val hsState by hotspotViewModel.uiState.collectAsStateWithLifecycle()
                                val query by hotspotViewModel.searchQuery.collectAsStateWithLifecycle()
                                val actionMsg by hotspotViewModel.actionMessage.collectAsStateWithLifecycle()
                                PublicWifiFinderScreen(
                                    uiState = hsState,
                                    searchQuery = query,
                                    actionMessage = actionMsg,
                                    isSignInLoading = isSignInLoading,
                                    signInError = signInError,
                                    onSearchChange = hotspotViewModel::setSearchQuery,
                                    onSignInClick = onGoogleSignInClick,
                                    onSubmitHotspot = hotspotViewModel::submitHotspot,
                                    onOpenHotspotDetails = { id ->
                                        hotspotViewModel.selectHotspot(id)
                                        navController.navigate(HotspotDetailsRoute(id))
                                    },
                                    onClearMessage = hotspotViewModel::clearActionMessage,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<HotspotDetailsRoute> { backStackEntry ->
                                val route = backStackEntry.toRoute<HotspotDetailsRoute>()
                                val hsState by hotspotViewModel.uiState.collectAsStateWithLifecycle()
                                val reviews by hotspotViewModel.selectedReviews.collectAsStateWithLifecycle()
                                LaunchedEffect(route.hotspotId) {
                                    hotspotViewModel.selectHotspot(route.hotspotId)
                                }
                                val hotspot = (hsState as? HotspotUiState.Success)
                                    ?.hotspots
                                    ?.firstOrNull { it.id == route.hotspotId }
                                HotspotDetailsScreen(
                                    hotspot = hotspot,
                                    reviews = reviews,
                                    onSaveFavorite = hotspotViewModel::saveHotspotAsFavorite,
                                    onAddReview = hotspotViewModel::addReview,
                                    onSubmitReport = hotspotViewModel::submitReport,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<NetworkInfoRoute> {
                                NetworkInfoScreen(
                                    snapshot = activeNetwork,
                                    wifiScannerManager = wifiScannerManager,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<ProfileRoute> {
                                UserProfileScreen(
                                    authUser = authUser,
                                    isSignInLoading = isSignInLoading,
                                    signInError = signInError,
                                    onSignInClick = onGoogleSignInClick,
                                    onSignOutClick = onSignOutClick,
                                    onOpenSettings = { navController.navigate(SettingsRoute) },
                                    onOpenLanguage = { navController.navigate(LanguageRoute) },
                                    onOpenPermissions = { navController.navigate(PermissionsRoute) },
                                    onOpenPrivacyPolicy = { navController.navigate(PrivacyPolicyRoute) },
                                    onOpenAbout = { navController.navigate(AboutRoute) }
                                )
                            }

                            composable<SettingsRoute> {
                                SettingsScreen(
                                    preferences = prefs,
                                    onSelectThemeMode = mainViewModel::setThemeMode,
                                    onToggleNotifications = mainViewModel::setNotificationsEnabled,
                                    onSaveEndpoints = mainViewModel::setSpeedTestEndpoints,
                                    onOpenLanguage = { navController.navigate(LanguageRoute) },
                                    onOpenPermissions = { navController.navigate(PermissionsRoute) },
                                    onOpenPrivacyPolicy = { navController.navigate(PrivacyPolicyRoute) },
                                    onOpenAbout = { navController.navigate(AboutRoute) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<LanguageRoute> {
                                LanguageSelectionScreen(
                                    currentCode = prefs.languageCode,
                                    onSelectLanguage = mainViewModel::setLanguage,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<AboutRoute> {
                                AboutAppScreen(onBack = { navController.popBackStack() })
                            }

                            composable<PrivacyPolicyRoute> {
                                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
                            }

                            composable<PermissionsRoute> {
                                PermissionExplanationScreen(onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}
