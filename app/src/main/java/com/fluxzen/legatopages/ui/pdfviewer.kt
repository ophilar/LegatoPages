package com.fluxzen.legatopages.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fluxzen.legatopages.Device
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    bookPage: Int,
    deviceArrangement: List<Device>,
    onTurnPage: (Int) -> Unit,
    statusText: String,
    isLeader: Boolean,
    isLocalViewingOnly: Boolean,
    onStartLeadingClicked: () -> Unit,
    onStopLeadingClicked: () -> Unit,
    onLeaveSessionClicked: () -> Unit,
    onFindSessionClicked: () -> Unit,
    onLoadDifferentPdfClicked: () -> Unit,
) {
    val context = LocalContext.current
    val renderer = remember(pdfUri) { PdfPageRenderer(context, pdfUri) }
    DisposableEffect(renderer) {
        onDispose { renderer.close() }
    }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var pdfContainerSize by remember { mutableStateOf<IntSize?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val thisDeviceIndex = if (isLeader) 0 else deviceArrangement.indexOfFirst { !it.isLeader }.takeIf { it != -1 } ?: 0
    val totalDevices = deviceArrangement.size.coerceAtLeast(1)
    val actualPageIndex = bookPage + thisDeviceIndex

    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current

    LaunchedEffect(renderer, actualPageIndex, pdfContainerSize) {
        pdfContainerSize?.let { validSize ->
            if (actualPageIndex in 0 until renderer.pageCount) {
                isLoading = true
                val renderSize = with(density) {
                    Size((validSize.width * 1.5f).roundToInt(), (validSize.height * 1.5f).roundToInt())
                }
                bitmap = renderer.renderPage(actualPageIndex, renderSize)
                isLoading = false
            } else {
                bitmap = null
            }
        }
        // Reset zoom and pan on page turn
        scale = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(controlsVisible, lastInteractionTime) {
        if (controlsVisible) {
            delay(5000)
            if (System.currentTimeMillis() - lastInteractionTime >= 5000) {
                controlsVisible = false
            }
        }
    }

    fun showControlsAndResetTimer() {
        controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    fun turnPageNext() {
        val newBookPage = bookPage + totalDevices
        if ((newBookPage + thisDeviceIndex) < renderer.pageCount) {
            onTurnPage(newBookPage)
        }
    }

    fun turnPagePrevious() {
        val newBookPage = (bookPage - totalDevices).coerceAtLeast(0)
        onTurnPage(newBookPage)
    }

    var showPageDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .onSizeChanged { measuredSize ->
                pdfContainerSize = measuredSize
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragOccurred = false
                    var zoomOccurred = false
                    var lastEvent: PointerEvent?

                    do {
                        val event = awaitPointerEvent()
                        lastEvent = event
                        if (event.changes.size > 1) {
                            zoomOccurred = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        } else if (!zoomOccurred) {
                            val drag = event.changes.firstOrNull()
                            if (drag != null && drag.pressed) {
                                if (drag.positionChange().getDistance() > viewConfiguration.touchSlop) {
                                    dragOccurred = true
                                }
                            }
                        }
                        event.changes.forEach {
                            if (it.pressed) {
                                it.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (dragOccurred && !zoomOccurred) {
                        val lastChange = lastEvent.changes.first()
                        val dragDistance = down.position - lastChange.position
                        if (abs(dragDistance.x) > viewConfiguration.touchSlop * 2) {
                           if (dragDistance.x > 0) {
                               turnPageNext()
                           } else {
                               turnPagePrevious()
                           }
                        }
                    } else if (!zoomOccurred) {
                        showControlsAndResetTimer()
                    }
                }
            }
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${actualPageIndex + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        } else if (!isLoading) {
            Text(
                "End of document",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        val buttonAlpha by animateFloatAsState(targetValue = if (controlsVisible) 1f else 0.2f, label = "Button Alpha")

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { turnPagePrevious(); showControlsAndResetTimer() }, modifier = Modifier.alpha(buttonAlpha)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page", tint = Color.White, modifier = Modifier.size(48.dp))
                }

                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            isLocalViewingOnly -> {
                                Button(onClick = onStartLeadingClicked) { Text("Start Leading") }
                                Button(onClick = onFindSessionClicked) { Text("Find Session") }
                            }
                            isLeader -> {
                                Button(onClick = onStopLeadingClicked) { Text("Stop Leading") }
                            }
                            else -> { // Follower
                                Button(onClick = onLeaveSessionClicked) { Text("Leave Session") }
                            }
                        }
                        Button(onClick = onLoadDifferentPdfClicked) { Text("Load New") }
                    }
                }

                IconButton(onClick = { turnPageNext(); showControlsAndResetTimer() }, modifier = Modifier.alpha(buttonAlpha)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(vertical = 4.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$statusText | ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Page ${actualPageIndex + 1}/${renderer.pageCount}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .clickable { showPageDialog = true }
                            .padding(start = 4.dp)
                    )
                }
            }
        }

        if (showPageDialog) {
            GoToPageDialog(
                totalPages = renderer.pageCount,
                onDismiss = { showPageDialog = false },
                onConfirm = { page ->
                    onTurnPage(page - 1)
                    showPageDialog = false
                    showControlsAndResetTimer()
                }
            )
        }
    }
}

@Composable
private fun GoToPageDialog(
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val isError = text.toIntOrNull()?.let { it < 1 || it > totalPages } ?: text.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to Page") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() } },
                    label = { Text("Page (1 - $totalPages)") },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                if (isError) {
                    Text(
                        text = "Enter a valid page number.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { text.toIntOrNull()?.let(onConfirm) },
                enabled = !isError && text.isNotEmpty()
            ) {
                Text("Go")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
