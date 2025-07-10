package com.fluxzen.legatopages

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fluxzen.legatopages.ui.PdfViewerScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

private const val PDF_MIME_TYPE = "application/pdf"

sealed class ScreenState {
    abstract val statusText: String

    data class Permission(override val statusText: String = "") : ScreenState()
    data class DiscoveringSession(override val statusText: String = "") : ScreenState()
    data class SelectPdfToLoadOrDiscover(override val statusText: String = "") : ScreenState()

    data class PdfViewer(
        val pdfUri: Uri,
        val bookPage: Int,
        var isActuallyLeading: Boolean,
        override val statusText: String,
        val fileHash: String,
        val isLocalViewingOnly: Boolean = false,
    ) : ScreenState()
}

enum class PageTurnDirection {
    NEXT,
    PREVIOUS
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    var screenState by remember { mutableStateOf<ScreenState>(ScreenState.Permission()) }
    var deviceArrangement by remember { mutableStateOf(listOf<Device>()) }

    val context = LocalContext.current
    val pdfPreferences = remember { PdfPreferences(context.applicationContext) }
    val cacheManager = remember { CacheManager(context.applicationContext) }

    val syncManager = remember(context.applicationContext) {
        SyncManager(context.applicationContext)
    }

    fun updateStateAndSavePdf(file: File, isLocal: Boolean) {
        val fileUri = Uri.fromFile(file)
        val fileHash = cacheManager.getFileHash(file.inputStream())

        val pageToLoad = if (isLocal) 0 else pdfPreferences.getPageForFile(fileHash)

        val newScreenState = ScreenState.PdfViewer(
            pdfUri = fileUri,
            bookPage = pageToLoad,
            isActuallyLeading = false,
            statusText = if (isLocal) "PDF loaded. Start leading or find a session." else "File received. Waiting for page sync...",
            fileHash = fileHash,
            isLocalViewingOnly = isLocal
        )
        screenState = newScreenState

        pdfPreferences.savePageForFile(fileHash, pageToLoad)
    }

    fun executePageTurn(direction: PageTurnDirection) {
        (screenState as? ScreenState.PdfViewer)?.let { state ->

            if (!state.isLocalViewingOnly && !state.isActuallyLeading) return

            val totalDevices =
                if (state.isLocalViewingOnly) 1 else deviceArrangement.size.coerceAtLeast(1)
            val newBookPage = when (direction) {
                PageTurnDirection.NEXT -> state.bookPage + totalDevices
                PageTurnDirection.PREVIOUS -> (state.bookPage - totalDevices).coerceAtLeast(0)
            }


            screenState = state.copy(bookPage = newBookPage)
            pdfPreferences.savePageForFile(state.fileHash, newBookPage)


            if (state.isActuallyLeading) {
                syncManager.broadcastPageTurn(PageTurn(newBookPage))
            }
        }
    }
    LaunchedEffect(syncManager) {
        syncManager.onTurnRequestReceived = { direction ->
            executePageTurn(direction)
        }
        syncManager.onPageTurnReceived = { pageTurn ->
            (screenState as? ScreenState.PdfViewer)?.let {
                screenState = it.copy(bookPage = pageTurn.bookPage)
                pdfPreferences.savePageForFile(it.fileHash, pageTurn.bookPage)
            }
        }
        syncManager.onDeviceArrangementChanged = { devices -> deviceArrangement = devices }
        syncManager.onStatusUpdate = { status ->
            screenState = when (val currentState = screenState) {
                is ScreenState.Permission -> currentState.copy(statusText = status)
                is ScreenState.DiscoveringSession -> currentState.copy(statusText = status)
                is ScreenState.SelectPdfToLoadOrDiscover -> currentState.copy(statusText = status)
                is ScreenState.PdfViewer -> currentState.copy(statusText = status)
            }
        }
        syncManager.onFileReceived = { file: File ->
            updateStateAndSavePdf(file, isLocal = false)
        }
        syncManager.onFollowerJoined = { device ->
            (screenState as? ScreenState.PdfViewer)?.let {
                screenState = it.copy(statusText = "Follower joined: ${device.deviceName}")
            }
        }
        syncManager.onLeaderFound = {
            screenState = (screenState as? ScreenState.DiscoveringSession)?.copy(
                statusText = "Connected to leader. Waiting for file..."
            ) ?: ScreenState.DiscoveringSession("Connected to leader. Waiting for file...")
        }
        syncManager.onNoLeaderFound = {
            screenState =
                ScreenState.SelectPdfToLoadOrDiscover("No leader found. Load a PDF to start your own session.")
        }
        syncManager.onDisconnectedFromLeader = {
            screenState = ScreenState.SelectPdfToLoadOrDiscover("Disconnected from leader.")
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
        }
        syncManager.onAdvertisingFailed = {
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
                screenState =
                    ScreenState.Permission("Permissions required to use the app. Please grant all permissions to continue.")
            }
        }
    }

    LaunchedEffect(key1 = permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) {
            if (screenState is ScreenState.Permission) {

                val lastFileHash = pdfPreferences.getLastOpenedFileHash()
                if (lastFileHash != null) {
                    val file = cacheManager.getCachedFile(lastFileHash)
                    if (file != null) {
                        val fileUri = Uri.fromFile(file)

                        syncManager.loadFile(fileUri)
                        val pageToResume = pdfPreferences.getPageForFile(lastFileHash)
                        screenState = ScreenState.PdfViewer(
                            pdfUri = fileUri,
                            bookPage = pageToResume,
                            isActuallyLeading = false,
                            statusText = "Last PDF loaded. Start leading or find a session.",
                            fileHash = lastFileHash,
                            isLocalViewingOnly = true
                        )
                    } else {
                        pdfPreferences.clearPageForFile(lastFileHash)
                        screenState =
                            ScreenState.SelectPdfToLoadOrDiscover("Last PDF not found. Please select a PDF.")
                    }
                } else {
                    screenState =
                        ScreenState.SelectPdfToLoadOrDiscover("Permissions granted. Load a PDF or find a session.")
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
                    val (_, file) = cachedResult
                    if (syncManager.loadFile(Uri.fromFile(file))) {
                        updateStateAndSavePdf(file, isLocal = true)
                    } else {
                        screenState =
                            (screenState as? ScreenState.PdfViewer)?.copy(statusText = "Failed to load PDF.")
                                ?: ScreenState.SelectPdfToLoadOrDiscover("Failed to load PDF.")
                    }
                } else {
                    screenState =
                        (screenState as? ScreenState.PdfViewer)?.copy(statusText = "Failed to cache PDF.")
                            ?: ScreenState.SelectPdfToLoadOrDiscover("Failed to cache PDF.")
                }
            } else {
                val newStatus = "PDF selection cancelled."
                screenState = if (screenState !is ScreenState.PdfViewer) {
                    ScreenState.SelectPdfToLoadOrDiscover(newStatus)
                } else {
                    (screenState as ScreenState.PdfViewer).copy(statusText = newStatus)
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
        if (screenState.statusText.isNotEmpty() && screenState !is ScreenState.PdfViewer) {
            Text(
                screenState.statusText,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }

        when (val state = screenState) {
            is ScreenState.Permission -> {
                PermissionRequestScreen { permissionState.launchMultiplePermissionRequest() }
            }

            is ScreenState.SelectPdfToLoadOrDiscover -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        stringResource(R.string.choose_action),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { filePickerLauncher.launch(arrayOf(PDF_MIME_TYPE)) }) {
                        Text(stringResource(R.string.button_load_pdf))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        syncManager.startDiscovery()

                        screenState = ScreenState.DiscoveringSession("Searching for sessions...")
                    }) { Text("Find Existing Session") }
                }
            }

            is ScreenState.DiscoveringSession -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.statusText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = {
                        syncManager.stopDiscovery()
                        screenState = ScreenState.SelectPdfToLoadOrDiscover("Search cancelled.")
                    }) {
                        Text("Cancel Search")
                    }
                }
            }

            is ScreenState.PdfViewer -> {
                PdfViewerScreen(
                    pdfUri = state.pdfUri,
                    bookPage = state.bookPage,
                    deviceArrangement = deviceArrangement,
                    onTurnPage = { direction ->
                        if (state.isLocalViewingOnly || state.isActuallyLeading) {
                            executePageTurn(direction)
                        } else {
                            syncManager.requestPageTurn(direction)
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

                        screenState = ScreenState.DiscoveringSession("Searching for sessions...")
                    },
                    onLoadDifferentPdfClicked = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                    onGoToPage = { pageIndex ->
                        if (state.isLocalViewingOnly || state.isActuallyLeading) {
                            screenState = state.copy(bookPage = pageIndex)
                            pdfPreferences.savePageForFile(state.fileHash, pageIndex)
                            if (state.isActuallyLeading) {
                                syncManager.broadcastPageTurn(PageTurn(pageIndex))
                            }
                        }
                    }
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
        Text(
            stringResource(R.string.permissions_required_rationale),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrantClicked) {
            Text(stringResource(R.string.button_grant_permissions))
        }
    }
}