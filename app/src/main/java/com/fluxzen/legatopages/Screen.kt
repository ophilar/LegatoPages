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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fluxzen.legatopages.ui.PdfViewerScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var bookPage by remember { mutableIntStateOf(0) }
    var thisDeviceIndex by remember { mutableIntStateOf(0) }
    var totalDevices by remember { mutableIntStateOf(1) }
    var statusText by remember { mutableStateOf("Not Connected") }

    val context = LocalContext.current
    val pdfPrefs = remember { PdfPreferences(context) }

    LaunchedEffect(Unit) {
        pdfPrefs.getLastPosition()?.let { lastPosition ->
            pdfUri = lastPosition.uri
            bookPage = lastPosition.bookPage
        }
    }
    
    val syncManager = remember {
        SyncManager(
            context = context,
            onPageTurnReceived = { pageTurn -> bookPage = pageTurn.bookPage},
            onDeviceCountChanged = { count -> 
                totalDevices = count
                thisDeviceIndex = 0
            },
            onStatusUpdate = { status -> statusText = status }
        )
    }


    DisposableEffect(Unit) {
        syncManager.start()
        onDispose {
            syncManager.stop()
        }
    }


    val onTurnPage = { newBookPage: Int ->
        bookPage = newBookPage
        syncManager.broadcastPageTurn(PageTurn(newBookPage))
        pdfUri?.let { uri ->
            pdfPrefs.saveLastPosition(uri, newBookPage)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val newBookPage = 0 
                pdfPrefs.saveLastPosition(uri, newBookPage) 
                pdfUri = uri
                bookPage = newBookPage
            }
        }
    )
    val launchFilePicker = remember { { filePickerLauncher.launch(arrayOf("application/pdf")) } }


    val permissions = listOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION
    )
    val permissionState = rememberMultiplePermissionsState(permissions)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!permissionState.allPermissionsGranted) {
            PermissionRequestScreen { permissionState.launchMultiplePermissionRequest() }
        } else if (pdfUri == null) {
            FileSelectScreen(onFileSelectClicked = launchFilePicker)
        } else {

            PdfViewerScreen(
                pdfUri = pdfUri!!,
                bookPage = bookPage,
                thisDeviceIndex = thisDeviceIndex,
                totalDevices = totalDevices,
                onTurnPage = onTurnPage,
                statusText = statusText,
                onLoadNewDocumentClicked = launchFilePicker
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