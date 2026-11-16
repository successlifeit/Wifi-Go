package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.OfflineStorageManager
import com.example.data.local.db.SavedWebpageEntity
import com.example.ui.components.ElevatedInfoCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.WiFiGoTopBar
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfflineBrowserScreen(
    initialUrl: String,
    archivePath: String,
    storageManager: OfflineStorageManager,
    onSaveArchive: (url: String, title: String, archiveFile: File) -> Unit,
    onOpenSavedPages: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    var currentTitle by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun normalizeUrl(raw: String): String {
        val clean = raw.trim()
        return if (clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("file://")) {
            clean
        } else {
            "https://$clean"
        }
    }

    Scaffold(
        topBar = {
            WiFiGoTopBar(
                title = strings.offlineBrowser,
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = onOpenSavedPages,
                        modifier = Modifier.testTag("saved_pages_button")
                    ) {
                        Icon(Icons.Default.Bookmarks, contentDescription = strings.savedPages)
                    }
                    IconButton(
                        onClick = {
                            val wv = webViewRef ?: return@IconButton
                            val archiveName = "page_${System.currentTimeMillis()}.mht"
                            val outFile = File(storageManager.webArchivesDir, archiveName)
                            wv.saveWebArchive(outFile.absolutePath, false) { savedPath ->
                                if (!savedPath.isNullOrBlank()) {
                                    onSaveArchive(
                                        wv.url ?: urlInput,
                                        wv.title ?: urlInput,
                                        File(savedPath)
                                    )
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Webpage archived for offline reading")
                                    }
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Unable to archive this page")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("save_webpage_button")
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = strings.saveOffline)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strings.offlineBrowserNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(enabled = canGoBack, onClick = { webViewRef?.goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(enabled = canGoForward, onClick = { webViewRef?.goForward() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = strings.refresh)
                }
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text(strings.enterUrl) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val target = normalizeUrl(urlInput)
                            urlInput = target
                            webViewRef?.loadUrl(target)
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        val target = normalizeUrl(urlInput)
                        urlInput = target
                        webViewRef?.loadUrl(target)
                    }
                ) {
                    Text(strings.go)
                }
            }

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.allowFileAccess = archivePath.isNotBlank()
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                if (!url.isNullOrBlank() && !url.startsWith("file://")) {
                                    urlInput = url
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                currentTitle = view?.title ?: ""
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        if (archivePath.isNotBlank() && File(archivePath).exists()) {
                            loadUrl("file://$archivePath")
                        } else {
                            loadUrl(normalizeUrl(initialUrl))
                        }
                        webViewRef = this
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
fun SavedWebpagesScreen(
    pages: List<SavedWebpageEntity>,
    onOpenSavedPage: (SavedWebpageEntity) -> Unit,
    onToggleBookmark: (SavedWebpageEntity) -> Unit,
    onDeletePage: (SavedWebpageEntity) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    var query by remember { mutableStateOf("") }
    val filtered = pages.filter { page ->
        val q = query.trim().lowercase()
        q.isEmpty() || page.title.lowercase().contains(q) || page.url.lowercase().contains(q)
    }

    Scaffold(
        topBar = { WiFiGoTopBar(title = strings.savedPages, onBack = onBack) }
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search saved webpages...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                )
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Language,
                        title = strings.savedPages,
                        message = "No saved offline webpages yet. Open a permitted webpage in the Offline Browser and tap the Save icon.",
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                }
            } else {
                items(filtered, key = { it.id }) { page ->
                    ElevatedInfoCard(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .clickable { onOpenSavedPage(page) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = page.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = page.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = OfflineStorageManager.formatBytes(page.fileSizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onToggleBookmark(page) }) {
                                Icon(
                                    imageVector = if (page.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = PrimaryBlue
                                )
                            }
                            IconButton(onClick = { onDeletePage(page) }) {
                                Icon(Icons.Default.Delete, contentDescription = strings.delete)
                            }
                        }
                    }
                }
            }
        }
    }
}
