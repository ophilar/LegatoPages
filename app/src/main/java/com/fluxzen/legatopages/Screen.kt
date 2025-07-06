package com.fluxzen.legatopages

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fluxzen.legatopages.ui.PdfViewer
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        Text("Permissions Required", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "This app needs permissions to find and connect to nearby devices.",
            textAlign = TextAlign.Center
        )
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
    onLoadNewDocumentClicked: () -> Unit,
) {
    PdfViewer(
        modifier = Modifier.fillMaxSize(),
        pdfUri = pdfUri,
        vm=vm,
        uiState = uiState,
//        onPageChanged = { page -> vm.onPageChangedByUi(page) },
//        remotePageRequest = vm.remotePageRequest,
//        consumeRemotePageRequest = { vm.consumeRemotePageRequest() },
        onLoadNewDocumentClicked = onLoadNewDocumentClicked
    )
}

//@Composable
//fun ControlPanel(
//    uiState: UiState,
//    vm: LegatoPagesViewModel,
//    onLoadNewDocumentClicked: () -> Unit,
//) {
//    Column(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Text(text = uiState.connectionStatus, style = MaterialTheme.typography.bodyMedium)
//        Spacer(modifier = Modifier.height(8.dp))
//        if (uiState.isConnected) {
//            val role = if (uiState.isLeader) "Leader" else "Follower"
//            Text("You are the $role", style = MaterialTheme.typography.bodyMedium)
//        } else {
//            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
//                Button(onClick = { vm.startAdvertising() }) {
//                    Row(verticalAlignment = Alignment.CenterVertically) {
//                        Icon(
//                            painter = painterResource(id = R.drawable.ic_sync_devices),
//                            contentDescription = "Start a sync session"
//                        )
//                        Spacer(modifier = Modifier.width(8.dp))
//                        Text("Become Leader")
//                    }
//                }
//                Button(onClick = { vm.startDiscovery() }) { Text("Become Follower") }
//            }
//        }
//        Spacer(modifier = Modifier.height(16.dp))
//        Button(onClick = onLoadNewDocumentClicked) {
//            Text("Load New Document")
//        }
//    }
//}
//
//@Composable
//fun PdfViewer(
//    modifier: Modifier = Modifier,
//    pdfUri: Uri,
//    onPageChanged: (Int) -> Unit,
//    remotePageRequest: StateFlow<Int?>?,
//    consumeRemotePageRequest: () -> Unit,
//    onLoadNewDocumentClicked: () -> Unit,
//) {
//    val vueReaderState = rememberHorizontalVueReaderState(
//        resource = VueResourceType.Local(pdfUri)
//    )
//
//    val remotePage by remotePageRequest?.collectAsState() ?: remember { mutableStateOf(null) }
//    val coroutineScope = rememberCoroutineScope()
//    val context = LocalContext.current
//
//    var containerSize by remember { mutableStateOf<IntSize?>(null) }
//    val configuration = LocalConfiguration.current
//    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
//
//    LaunchedEffect(vueReaderState.currentPage) {
//        if (vueReaderState.vueLoadState is VueLoadState.DocumentLoaded) {
//            onPageChanged(vueReaderState.currentPage - 1)
//        }
//    }
//
//
//    LaunchedEffect(remotePage) {
//        remotePage?.let { page ->
//            val targetPage = page + 1
//            if (targetPage != vueReaderState.currentPage) {
//                val currentPage = vueReaderState.currentPage
//                if (targetPage > currentPage) {
//                    coroutineScope.launch {
//                        for (i in currentPage until targetPage) {
//                            vueReaderState.nextPage()
//                            delay(1)
//                        }
//                    }
//                } else {
//                    coroutineScope.launch {
//                        for (i in currentPage downTo targetPage + 1) {
//                            vueReaderState.prevPage()
//                            delay(1)
//                        }
//                    }
//                }
//            }
//            consumeRemotePageRequest()
//        }
//    }
//
//    Box(
//        modifier = modifier
//            .onSizeChanged { containerSize = it },
//        contentAlignment = Alignment.Center
//    ) {
//        LaunchedEffect(key1 = containerSize, key2 = pdfUri, key3 = isPortrait) {
//            containerSize?.let { validSize ->
//                vueReaderState.load(
//                    context = context,
//                    coroutineScope = coroutineScope,
//                    containerSize = validSize,
//                    isPortrait = isPortrait,
//                    customResource = null
//                )
//            }
//        }
//
//        val isEnabled = vueReaderState.vueLoadState is VueLoadState.DocumentLoaded
//
//        when (vueReaderState.vueLoadState) {
//            is VueLoadState.DocumentLoaded -> {
//                HorizontalVueReader(
//                    modifier = Modifier.fillMaxSize(),
//                    horizontalVueReaderState = vueReaderState
//                )
//            }
//
//            is VueLoadState.DocumentLoading -> {
//                Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                    CircularProgressIndicator()
//                    Spacer(modifier = Modifier.height(8.dp))
//                    Text(text = "Loading Document...")
//                }
//            }
//
//            is VueLoadState.DocumentError -> {
//                Text(text = "Error loading document")
//            }
//
//            else -> {
//                Text(text = "Waiting for size...")
//            }
//        }
//
//        if (isEnabled) {
//            val controlBackground = Modifier
//                .padding(horizontal = 4.dp, vertical = 8.dp)
//                .clip(RoundedCornerShape(12.dp))
//                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f))
//                .border(
//                    width = 1.dp,
//                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
//                    shape = RoundedCornerShape(12.dp)
//                )
//
//            Row(
//                modifier = Modifier
//                    .align(Alignment.TopCenter)
//                    .padding(16.dp),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                Text(
//                    text = "Page ${vueReaderState.currentPage} of ${vueReaderState.pdfPageCount}",
//                    modifier = controlBackground.padding(horizontal = 12.dp, vertical = 8.dp),
//                    style = MaterialTheme.typography.bodyMedium
//                )
//                Spacer(Modifier.weight(1f))
//                IconButton(
//                    modifier = controlBackground,
//                    onClick = { vueReaderState.sharePDF(context) }
//                ) {
//                    Icon(Icons.Default.Share, "Share PDF")
//                }
//                Spacer(Modifier.width(8.dp))
//                IconButton(
//                    modifier = controlBackground,
//                    onClick = onLoadNewDocumentClicked
//                ) {
//                    Icon(Icons.Default.KeyboardArrowUp, "Load New Document")
//                }
//            }
//
//            Row(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .padding(16.dp)
//                    .then(controlBackground)
//                    .padding(horizontal = 8.dp),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                IconButton(
//                    onClick = { coroutineScope.launch { vueReaderState.prevPage() } },
//                    enabled = vueReaderState.currentPage > 1
//                ) {
//                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous Page")
//                }
//                Spacer(Modifier.weight(1f))
//                IconButton(onClick = { vueReaderState.rotate(-90f) }) {
//                    Icon(Icons.Default.Refresh, "Rotate")
//                }
//                Spacer(Modifier.weight(1f))
//                IconButton(
//                    onClick = { coroutineScope.launch { vueReaderState.nextPage() } },
//                    enabled = vueReaderState.currentPage < vueReaderState.pdfPageCount
//                ) {
//                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next Page")
//                }
//            }
//        }
//    }
//}