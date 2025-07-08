package com.fluxzen.legatopages.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    bookPage: Int,
    thisDeviceIndex: Int,
    totalDevices: Int,
    onTurnPage: (Int) -> Unit,
    statusText: String
) {
    val context = LocalContext.current

    
    
    val renderer = remember(pdfUri) { PdfPageRenderer(context, pdfUri) }
    DisposableEffect(renderer) {
        onDispose {
            renderer.close()
        }
    }

    
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val pageTurnIncrement = totalDevices
    
    val actualPageIndex = bookPage + thisDeviceIndex

    
    LaunchedEffect(renderer, actualPageIndex) {
        if (actualPageIndex >= renderer.pageCount) return@LaunchedEffect
        isLoading = true
        bitmap = renderer.renderPage(actualPageIndex)
        isLoading = false
    }

    Column(Modifier.fillMaxSize()) {
        
        Surface(shadowElevation = 4.dp) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Device ${thisDeviceIndex + 1} of $totalDevices",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.weight(1f))
                Text(statusText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "PDF Page ${actualPageIndex + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        
                        .pointerInput(totalDevices, bookPage) { 
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume() 
                                    
                                    
                                    val swipeThreshold = 50 

                                    if (dragAmount < -swipeThreshold) { 
                                        val newBookPage = bookPage + pageTurnIncrement
                                        if ((newBookPage + thisDeviceIndex) < renderer.pageCount) {
                                            onTurnPage(newBookPage)
                                        }
                                    } else if (dragAmount > swipeThreshold) { 
                                        val newBookPage = (bookPage - pageTurnIncrement).coerceAtLeast(0)
                                        onTurnPage(newBookPage)
                                    }
                                }
                            )
                        }
                )
            } else {
                Text("Could not load page.", color = Color.White)
            }
        }
    }
}