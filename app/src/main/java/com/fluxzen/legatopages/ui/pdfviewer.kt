package com.fluxzen.legatopages.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    bookPage: Int,
    thisDeviceIndex: Int,
    totalDevices: Int,
    onTurnPage: (Int) -> Unit,
    statusText: String,
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
    var pdfContainerSize: IntSize? by remember { mutableStateOf(null) }

    val pageTurnIncrement = totalDevices
    val actualPageIndex = bookPage + thisDeviceIndex

    LaunchedEffect(renderer, actualPageIndex, pdfContainerSize) {
        pdfContainerSize?.let { validSize ->
            if (actualPageIndex < renderer.pageCount) {
                isLoading = true
                bitmap =
                    renderer.renderPage(actualPageIndex, Size(validSize.width, validSize.height))
                isLoading = false
            } else {

                bitmap = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 4.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                .background(Color.DarkGray)
                .onSizeChanged { measuredSize ->
                    pdfContainerSize = measuredSize
                },
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
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    totalDrag = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDrag += dragAmount
                                },
                                onDragEnd = {
                                    val swipeThreshold = 100

                                    if (totalDrag < -swipeThreshold) {
                                        val newBookPage = bookPage + pageTurnIncrement

                                        if (newBookPage < renderer.pageCount) {
                                            onTurnPage(newBookPage)
                                        }
                                    } else if (totalDrag > swipeThreshold) {
                                        val newBookPage =
                                            (bookPage - pageTurnIncrement).coerceAtLeast(0)
                                        onTurnPage(newBookPage)
                                    }
                                }
                            )
                        }
                )
            } else {

                Text("No page to display.", color = Color.White)
            }
        }
    }
}