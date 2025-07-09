package com.fluxzen.legatopages.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fluxzen.legatopages.R

@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    bookPage: Int,
    thisDeviceIndex: Int,
    totalDevices: Int,
    onTurnPage: (Int) -> Unit?,
    statusText: String,
    onLoadNewDocumentClicked: () -> Unit
) {
    val context = LocalContext.current
    val renderer = remember(pdfUri) { PdfPageRenderer(context, pdfUri) }
    DisposableEffect(renderer) {
        onDispose { renderer.close() }
    }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var pdfContainerSize by remember { mutableStateOf<IntSize?>(null) }

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

    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray).onSizeChanged { measuredSize ->
        pdfContainerSize = measuredSize
    }) {
        
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${actualPageIndex + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(totalDevices, bookPage) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            },
                            onDragEnd = {
                                val swipeThreshold = 100
                                if (totalDrag < -swipeThreshold) {
                                    val newBookPage = bookPage + totalDevices
                                    if ((newBookPage + thisDeviceIndex) < renderer.pageCount) {
                                        onTurnPage(newBookPage)
                                    }
                                } else if (totalDrag > swipeThreshold) {
                                    val newBookPage = (bookPage - totalDevices).coerceAtLeast(0)
                                    onTurnPage(newBookPage)
                                }
                            }
                        )
                    }
            )
        } else if (!isLoading) { 
            Text("No page to display.", modifier = Modifier.align(Alignment.Center), color = Color.White)
        }

        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        
        PdfControlsOverlay(
            modifier = Modifier.fillMaxSize(),
            statusText = statusText,
            pageCount = renderer.pageCount,
            currentPage = actualPageIndex + 1,
            onNextPage = {
                val newBookPage = bookPage + totalDevices
                if ((newBookPage + thisDeviceIndex) < renderer.pageCount) {
                    onTurnPage(newBookPage)
                }
            },
            onPrevPage = {
                val newBookPage = (bookPage - totalDevices).coerceAtLeast(0)
                onTurnPage(newBookPage)
            },
            onLoadNewDocumentClicked = onLoadNewDocumentClicked
        )
    }
}

@Composable
fun PdfControlsOverlay(
    modifier: Modifier = Modifier,
    statusText: String,
    pageCount: Int,
    currentPage: Int,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onLoadNewDocumentClicked: () -> Unit
) {
    val controlBackground = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
        .padding(horizontal = 12.dp, vertical = 8.dp)
        
    Box(modifier = modifier.padding(8.dp)) {
        
        Row(
            modifier = Modifier.align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = statusText, modifier = controlBackground)
        }
        
        Row(
            modifier = Modifier.align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            
            IconButton(onClick = onPrevPage, modifier = controlBackground) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
            }

            Text(text = "$currentPage / $pageCount", modifier = controlBackground)

            IconButton(onClick = onNextPage, modifier = controlBackground) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
            }

            IconButton(onClick = onLoadNewDocumentClicked, modifier = controlBackground) {
                Icon(painterResource(R.drawable.file_open_24px), contentDescription = "Load New Document")
            }
        }
    }
}