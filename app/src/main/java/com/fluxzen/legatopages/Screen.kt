package com.fluxzen.legatopages

import android.Manifest
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.pratikk.jetpdfvue.VerticalVueReader
import com.pratikk.jetpdfvue.state.VueLoadState
import com.pratikk.jetpdfvue.state.VueResourceType
import com.pratikk.jetpdfvue.state.rememberVerticalVueReaderState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(vm: LegatoPagesViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsState()

    val permissions = listOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES
    )
    val permissionState = rememberMultiplePermissionsState(permissions)

   
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                vm.onUriSelected(uri)
            }
        }
    )

    val launchFilePickerAction: () -> Unit = {
        filePickerLauncher.launch(arrayOf("application/pdf"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!permissionState.allPermissionsGranted) {
            PermissionRequestScreen { permissionState.launchMultiplePermissionRequest() }
        } else if (uiState.pdfUri == null) {
            FileSelectScreen(onFileSelectClicked = launchFilePickerAction)
        } else {
            AppContentScreen(
                uiState = uiState,
                vm = vm,
                pdfUri = uiState.pdfUri!!,
                onLoadNewDocumentClicked = launchFilePickerAction
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(onGrantClicked: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(16.dp)) {
        Text("Permissions Required", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("This app needs permissions to find and connect to nearby devices.", textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGrantClicked) { Text("Grant Permissions") }
    }
}

@Composable
fun FileSelectScreen(onFileSelectClicked: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Welcome to Legato Pages", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onFileSelectClicked) { Text("Select a PDF Score") }
    }
}

@Composable
fun AppContentScreen(
    uiState: UiState,
    vm: LegatoPagesViewModel,
    pdfUri: Uri,
    onLoadNewDocumentClicked: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PdfViewer(
            modifier = Modifier.weight(1f),
            pdfUri = pdfUri,
            onPageChanged = { page -> vm.onPageChangedByUi(page) },
            remotePageRequest = vm.remotePageRequest,
            consumeRemotePageRequest = { vm.consumeRemotePageRequest() }
        )
        ControlPanel(uiState = uiState, vm = vm, onLoadNewDocumentClicked = onLoadNewDocumentClicked)
    }
}

@Composable
fun ControlPanel(
    uiState: UiState,
    vm: LegatoPagesViewModel,
    onLoadNewDocumentClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = uiState.connectionStatus, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.isConnected) {
            val role = if (uiState.isLeader) "Leader" else "Follower"
            Text("You are the $role", style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { vm.startAdvertising() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sync_devices),
                            contentDescription = "Start a sync session"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Become Leader")
                    }
                }
                Button(onClick = { vm.startDiscovery() }) { Text("Become Follower") }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLoadNewDocumentClicked) {
            Text("Load New Document")
        }
    }
}

@Composable
fun PdfViewer(
    modifier: Modifier = Modifier,
    pdfUri: Uri,
    onPageChanged: (Int) -> Unit,
    remotePageRequest: StateFlow<Int?>?,
    consumeRemotePageRequest: () -> Unit
) {
    val vueReaderState = rememberVerticalVueReaderState(
        resource = VueResourceType.Local(pdfUri)
    )

    val remotePage by remotePageRequest?.collectAsState() ?: remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(vueReaderState.currentPage) {
        onPageChanged(vueReaderState.currentPage - 1)
    }

    LaunchedEffect(remotePage) {
        remotePage?.let { page ->
            val targetPage = page + 1
            if (targetPage != vueReaderState.currentPage) {
                val currentPage = vueReaderState.currentPage
                if (targetPage > currentPage) {
                    coroutineScope.launch {
                        for (i in currentPage until targetPage) {
                            vueReaderState.nextPage()
                            delay(1)
                        }
                    }
                } else {
                    coroutineScope.launch {
                        for (i in currentPage downTo targetPage + 1) {
                            vueReaderState.prevPage()
                            delay(1)
                        }
                    }
                }
            }
            consumeRemotePageRequest()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val configuration = LocalConfiguration.current

            val containerSize = remember(constraints.maxWidth, constraints.maxHeight) {
                if (constraints.hasBoundedWidth && constraints.hasBoundedHeight && constraints.maxWidth > 0 && constraints.maxHeight > 0) {
                    IntSize(constraints.maxWidth, constraints.maxHeight)
                } else {
                    null
                }
            }

            LaunchedEffect(key1 = vueReaderState, key2 = configuration.orientation, key3 = containerSize) {
                containerSize?.let { validSize ->
                    vueReaderState.load(
                        context = context,
                        coroutineScope = coroutineScope,
                        containerSize = validSize,
                        isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
                        customResource = null
                    )
                }
            }
            VerticalVueReader(
                modifier = Modifier.fillMaxSize(),
                verticalVueReaderState = vueReaderState
            )
            when (vueReaderState.vueLoadState) {
                is VueLoadState.DocumentLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is VueLoadState.DocumentError -> {
                    Text("Error loading document", modifier = Modifier.align(Alignment.Center))
                }
                else -> {}
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (vueReaderState.currentPage > 1) {
                            vueReaderState.prevPage()
                        }
                    }
                },
                enabled = vueReaderState.currentPage > 1
            ) {
                Text("Previous")
            }
            Text("Page ${vueReaderState.currentPage} of ${vueReaderState.pdfPageCount}")
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (vueReaderState.currentPage < vueReaderState.pdfPageCount) {
                            vueReaderState.nextPage()
                        }
                    }
                },
                enabled = vueReaderState.currentPage < vueReaderState.pdfPageCount
            ) {
                Text("Next")
            }
        }
    }
}
