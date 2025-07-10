package com.fluxzen.legatopages

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fluxzen.legatopages.ui.PdfViewerScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

sealed class ScreenState {
    object Permission : ScreenState()
    object DiscoveringSession : ScreenState()
    object SelectPdfToLoadOrDiscover : ScreenState()
    data class PdfViewer(
        val pdfUri: Uri,
        val bookPage: Int,
        var isActuallyLeading: Boolean,
        val statusText: String,
        val isLocalViewingOnly: Boolean = false
    ) : ScreenState()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    var screenState by remember { mutableStateOf<ScreenState>(ScreenState.Permission) }
    var deviceArrangement by remember { mutableStateOf(listOf<Device>()) }
    var statusTextState by remember { mutableStateOf("") }

    val context = LocalContext.current
    val pdfPreferences = remember { PdfPreferences(context.applicationContext) }
    val cacheManager = remember { CacheManager(context.applicationContext) }

    val syncManager = remember(context.applicationContext) {
        SyncManager(context.applicationContext)
    }

    fun updateStateAndSavePdf(file: File, isLocal: Boolean) {
        val fileUri = Uri.fromFile(file)
        val fileHash = cacheManager.getFileHash(file.inputStream())
        val pageToLoad = syncManager.getLastViewedPage(fileHash)

        val newScreenState = ScreenState.PdfViewer(
            pdfUri = fileUri,
            bookPage = pageToLoad,
            isActuallyLeading = false,
            statusText = if (isLocal) "PDF loaded. Start leading or find a session." else "File received. Waiting for page sync...",
            isLocalViewingOnly = isLocal
        )
        screenState = newScreenState
        pdfPreferences.saveLastPosition(fileHash, newScreenState.bookPage)
        if (isLocal) {
            statusTextState = newScreenState.statusText
        }
    }

    LaunchedEffect(syncManager) {
        syncManager.onPageTurnReceived = { pageTurn ->
            (screenState as? ScreenState.PdfViewer)?.let {
                if (it.pdfUri.toString().isNotEmpty()) {
                    val file = File(it.pdfUri.path!!)
                    val hash = cacheManager.getFileHash(file.inputStream())
                    screenState = it.copy(bookPage = pageTurn.bookPage)
                    pdfPreferences.saveLastPosition(hash, pageTurn.bookPage)
                }
            }
        }
        syncManager.onDeviceArrangementChanged = { devices -> deviceArrangement = devices }
        syncManager.onStatusUpdate = { status ->
            statusTextState = status
            (screenState as? ScreenState.PdfViewer)?.let {
                screenState = it.copy(statusText = status)
            }
        }
        syncManager.onFileReceived = { file: File ->
            val cachedFileUri = Uri.fromFile(file)
            updateStateAndSavePdf(file, isLocal = false)
        }
        syncManager.onFollowerJoined = { statusTextState = "Follower joined: ${it.deviceName}" }
        syncManager.onLeaderFound = {
            statusTextState = "Connected to leader. Waiting for file..."
        }
        syncManager.onNoLeaderFound = {
            statusTextState = "No leader found. Load a PDF to start your own session."
            screenState = ScreenState.SelectPdfToLoadOrDiscover
        }
        syncManager.onDisconnectedFromLeader = {
            statusTextState = "Disconnected from leader."
            screenState = ScreenState.SelectPdfToLoadOrDiscover
        }
        syncManager.onAdvertisingStarted = {
            (screenState as? ScreenState.PdfViewer)?.let {
                if (it.isLocalViewingOnly) {
                    screenState = it.copy(
                        isActuallyLeading = true,
                        statusText = "Broadcasting session...",
                        isLocalViewingOnly = false
                    )
                }
            }
            statusTextState = "Advertising started."
        }
        syncManager.onAdvertisingFailed = {
            statusTextState = "Advertising failed to start."
            (screenState as? ScreenState.PdfViewer)?.let {
                if (it.isLocalViewingOnly) {
                    screenState =
                        it.copy(statusText = "Failed to start advertising. Still in local viewing mode.")
                }
            }
        }
        syncManager.onLeadingStopped = {
            (screenState as? ScreenState.PdfViewer)?.let {
                screenState = it.copy(
                    isActuallyLeading = false,
                    isLocalViewingOnly = true,
                    statusText = "Stopped leading. You are now in local viewing mode."
                )
            }
        }
    }

    val permissionsList = remember {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES
        ).distinct()
    }

    val permissionState = rememberMultiplePermissionsState(permissionsList) { permissionsResult ->
        if (!permissionsResult.all { it.value }) {
            if (screenState !is ScreenState.Permission) {
                screenState = ScreenState.Permission
            }
            statusTextState = "Permissions required to use the app. Please grant all permissions to continue."
        }
    }

    LaunchedEffect(key1 = permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) {
            if (screenState is ScreenState.Permission) {
                val lastPosition = pdfPreferences.getLastPosition()
                if (lastPosition != null) {
                    val file = cacheManager.getCachedFile(lastPosition.fileHash)
                    if (file != null) {
                        val fileUri = Uri.fromFile(file)
                         val syncLoadSuccess = syncManager.loadFile(fileUri)
                        screenState = ScreenState.PdfViewer(
                            pdfUri = fileUri,
                            bookPage = lastPosition.bookPage,
                            isActuallyLeading = false,
                            statusText = if (syncLoadSuccess) "Loaded last viewed PDF." else "Loaded last PDF. Sync features might be limited.",
                            isLocalViewingOnly = !syncLoadSuccess
                        )
                        statusTextState = (screenState as ScreenState.PdfViewer).statusText
                    } else {
                        pdfPreferences.clearLastPosition()
                        screenState = ScreenState.SelectPdfToLoadOrDiscover
                        statusTextState = "Last PDF not found. Please select a PDF."
                    }
                } else {
                    screenState = ScreenState.SelectPdfToLoadOrDiscover
                    statusTextState = "Permissions granted. Load a PDF or find a session."
                }
            }
        } else {
            if (screenState is ScreenState.Permission) {
                permissionState.launchMultiplePermissionRequest()
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val cachedResult = cacheManager.cacheFileFromUri(uri)
                if (cachedResult != null) {
                    val (hash, file) = cachedResult
                    val fileUri = Uri.fromFile(file)
                    if (syncManager.loadFile(fileUri)) {
                        updateStateAndSavePdf(file, isLocal = true)
                    } else {
                        statusTextState = "Failed to load PDF."
                    }
                } else {
                    statusTextState = "Failed to cache PDF."
                }
            } else {
                statusTextState = "PDF selection cancelled."
                if (screenState !is ScreenState.PdfViewer) {
                    screenState = ScreenState.SelectPdfToLoadOrDiscover
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        if (statusTextState.isNotEmpty() && screenState !is ScreenState.PdfViewer) {
            Text(statusTextState, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }

        when (val state = screenState) {
            is ScreenState.Permission -> {
                PermissionRequestScreen { permissionState.launchMultiplePermissionRequest() }
            }
            is ScreenState.SelectPdfToLoadOrDiscover -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Text("Choose an action", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) }) {
                        Text("Load PDF")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        syncManager.startDiscovery()
                        screenState = ScreenState.DiscoveringSession
                        statusTextState = "Searching for sessions..."
                    }) { Text("Find Existing Session") }
                }
            }
            is ScreenState.DiscoveringSession -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(statusTextState.ifEmpty { "Searching for existing session..." }, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = {
                        syncManager.stopDiscovery()
                        screenState = ScreenState.SelectPdfToLoadOrDiscover
                        statusTextState = "Search cancelled."
                    }) {
                        Text("Cancel Search")
                    }
                }
            }
            is ScreenState.PdfViewer -> {
                val file = File(state.pdfUri.path!!)
                val hash = cacheManager.getFileHash(file.inputStream())
                PdfViewerScreen(
                    pdfUri = state.pdfUri,
                    bookPage = state.bookPage,
                    deviceArrangement = deviceArrangement,
                    onTurnPage = { newBookPage ->
                        (screenState as? ScreenState.PdfViewer)?.let {
                            screenState = it.copy(bookPage = newBookPage)
                            pdfPreferences.saveLastPosition(hash, newBookPage)
                        }
                        if (state.isActuallyLeading) {
                            syncManager.broadcastPageTurn(PageTurn(newBookPage))
                        }
                    },
                    statusText = state.statusText,
                    isLeader = state.isActuallyLeading,
                    isLocalViewingOnly = state.isLocalViewingOnly,
                    onStartLeadingClicked = { syncManager.startLeadingSession() },
                    onStopLeadingClicked = { syncManager.stopLeading() },
                    onLeaveSessionClicked = { syncManager.leaveSession() },
                    onFindSessionClicked = {
                        syncManager.startDiscovery()
                        screenState = ScreenState.DiscoveringSession
                    },

                    onLoadDifferentPdfClicked = { filePickerLauncher.launch(arrayOf("application/pdf")) }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            syncManager.shutdown()
        }
    }
}

@Composable
fun PermissionRequestScreen(onGrantClicked: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text("Permissions are required to discover and connect to nearby devices, and access PDF files.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrantClicked) {
            Text("Grant Permissions")
        }
    }
}
