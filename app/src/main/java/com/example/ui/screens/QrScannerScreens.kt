package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.wifi.ParsedQrPayload
import com.example.data.wifi.QrWifiParser
import com.example.data.wifi.SuggestionResult
import com.example.data.wifi.WifiScannerManager
import com.example.ui.components.ElevatedInfoCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.WiFiGoTopBar
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SignalExcellent
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerScreen(
    onQrDetected: (String) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var hasDispatchedResult by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val raw = barcodes.firstOrNull()?.rawValue
                        if (!raw.isNullOrBlank()) {
                            onQrDetected(raw)
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("No QR code detected in selected image.")
                            }
                        }
                    }
                    .addOnFailureListener {
                        scope.launch {
                            snackbarHostState.showSnackbar("Failed to decode selected image.")
                        }
                    }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Unable to read image")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                barcodeScanner.close()
            } catch (_: Exception) {
            }
        }
    }

    Scaffold(
        topBar = {
            WiFiGoTopBar(
                title = strings.qrScanner,
                onBack = onBack,
                actions = {
                    if (hasCameraPermission) {
                        IconButton(
                            onClick = {
                                torchEnabled = !torchEnabled
                                cameraRef?.cameraControl?.enableTorch(torchEnabled)
                            },
                            modifier = Modifier.testTag("qr_torch_button")
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = strings.toggleTorch
                            )
                        }
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
            if (!hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 520.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EmptyStateCard(
                            icon = Icons.Default.CameraAlt,
                            title = strings.permissionRequired,
                            message = "Camera access is required to scan Wi-Fi QR codes live. You can also choose a QR image from your gallery without Camera permission.",
                            actionLabel = strings.grantPermission,
                            onAction = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                        )
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("qr_gallery_picker_button")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.pickFromGallery)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
                    DisposableEffect(Unit) {
                        onDispose { analysisExecutor.shutdown() }
                    }

                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null && !hasDispatchedResult) {
                                            val inputImage = InputImage.fromMediaImage(
                                                mediaImage,
                                                imageProxy.imageInfo.rotationDegrees
                                            )
                                            barcodeScanner.process(inputImage)
                                                .addOnSuccessListener { barcodes ->
                                                    val value = barcodes.firstOrNull()?.rawValue
                                                    if (!value.isNullOrBlank() && !hasDispatchedResult) {
                                                        hasDispatchedResult = true
                                                        onQrDetected(value)
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy.close()
                                                }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }

                                    cameraProvider.unbindAll()
                                    cameraRef = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    Log.e("QrScanner", "CameraX bind failed", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val boxSize = minOf(size.width, size.height) * 0.65f
                        val left = (size.width - boxSize) / 2f
                        val top = (size.height - boxSize) / 2f
                        drawRect(Color.Black.copy(alpha = 0.45f))
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(left, top),
                            size = Size(boxSize, boxSize),
                            cornerRadius = CornerRadius(24.dp.toPx()),
                            blendMode = BlendMode.Clear
                        )
                        drawRoundRect(
                            color = PrimaryBlue,
                            topLeft = Offset(left, top),
                            size = Size(boxSize, boxSize),
                            cornerRadius = CornerRadius(24.dp.toPx()),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(20.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = strings.pointCameraAtQr,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qr_gallery_picker_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.pickFromGallery)
                    }
                }
            }
        }
    }
}

@Composable
fun QrScanResultScreen(
    rawPayload: String,
    wifiScannerManager: WifiScannerManager,
    onOpenInBrowser: (String) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val parsed = remember(rawPayload) { QrWifiParser.parse(rawPayload) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var revealPassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { WiFiGoTopBar(title = strings.qrResultTitle, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                when (parsed) {
                    is ParsedQrPayload.WifiCredentials -> {
                        ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = PrimaryBlue)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Wi-Fi Configuration", style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            DetailRow("SSID", parsed.ssid)
                            DetailRow(strings.security, parsed.securityType)
                            DetailRow("Hidden Network", if (parsed.isHidden) "Yes" else "No")
                            if (!parsed.password.isNullOrEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Passphrase",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (revealPassword) parsed.password else "••••••••••••",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        IconButton(onClick = { revealPassword = !revealPassword }) {
                                            Icon(
                                                imageVector = if (revealPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (revealPassword) strings.hidePassword else strings.revealPassword
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val res = wifiScannerManager.suggestWifiNetwork(
                                        ssid = parsed.ssid,
                                        password = parsed.password,
                                        securityType = parsed.securityType
                                    )
                                    val msg = when (res) {
                                        SuggestionResult.Suggested -> "Wi-Fi suggestion added. Android will prompt you to approve connecting to ${parsed.ssid}."
                                        SuggestionResult.AlreadySuggested -> "This Wi-Fi suggestion is already registered with Android."
                                        SuggestionResult.UnsupportedOlderAndroid -> "Android 10+ required for Wi-Fi Suggestion API. Opening Wi-Fi settings."
                                        SuggestionResult.UnsupportedLegacyWep -> "Legacy WEP suggestions are blocked by Android security policy. Connect via system Wi-Fi settings."
                                        is SuggestionResult.Failed -> "Suggestion failed: ${res.reason}"
                                    }
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                    if (res == SuggestionResult.UnsupportedOlderAndroid || res == SuggestionResult.UnsupportedLegacyWep) {
                                        context.startActivity(wifiScannerManager.buildWifiSettingsIntent())
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("suggest_wifi_button")
                            ) {
                                Text(strings.suggestWifiConnection)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        copyToClipboard(context, "WiFi SSID", parsed.ssid)
                                        scope.launch { snackbarHostState.showSnackbar("SSID copied") }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.copySsid)
                                }
                                if (!parsed.password.isNullOrEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            copyToClipboard(context, "WiFi Passphrase", parsed.password)
                                            scope.launch { snackbarHostState.showSnackbar("Passphrase copied") }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.copyPassword)
                                    }
                                }
                            }
                        }
                    }
                    is ParsedQrPayload.WebUrl -> {
                        ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = PrimaryBlue)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Website QR Code", style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(parsed.url, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { onOpenInBrowser(parsed.url) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open in Offline Browser")
                            }
                        }
                    }
                    is ParsedQrPayload.PlainText -> {
                        ElevatedInfoCard(modifier = Modifier.widthIn(max = 640.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, tint = PrimaryBlue)
                                Spacer(modifier = Modifier.width(10.dp))
                                StatusPill(text = "QR Text", color = SignalExcellent)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(parsed.text, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, "QR Content", parsed.text)
                                    scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Copy Text")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cb?.setPrimaryClip(ClipData.newPlainText(label, text))
}
