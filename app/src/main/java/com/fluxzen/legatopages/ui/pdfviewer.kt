package com.fluxzen.legatopages.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fluxzen.legatopages.Device

import kotlinx.coroutines.delay

import kotlin.math.abs


@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    bookPage: Int,
    deviceArrangement: List<Device>,
    onTurnPage: (Int) -> Unit,
    statusText: String,
    onStartSessionClicked: () -> Unit,
    isLeader: Boolean
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

    val thisDeviceIndex = if (isLeader) 0 else deviceArrangement.indexOfFirst { !it.isLeader }.takeIf { it != -1 } ?: 0
    val totalDevices = deviceArrangement.size.coerceAtLeast(1)
    val actualPageIndex = bookPage + thisDeviceIndex

    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    

    LaunchedEffect(renderer, actualPageIndex, pdfContainerSize) {
        pdfContainerSize?.let { validSize ->
            if (actualPageIndex >= 0 && actualPageIndex < renderer.pageCount) {
                isLoading = true
                bitmap =
                    renderer.renderPage(actualPageIndex, Size(validSize.width, validSize.height))
                isLoading = false
            } else {
                bitmap = null
            }
        }
    }

    LaunchedEffect(controlsVisible, lastInteractionTime) {
        if (controlsVisible) {
            delay(3000) 
            if (System.currentTimeMillis() - lastInteractionTime >= 3000) {
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
        showControlsAndResetTimer()
    }

    fun turnPagePrevious() {
        val newBookPage = (bookPage - totalDevices).coerceAtLeast(0)
        onTurnPage(newBookPage)
        showControlsAndResetTimer()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .onSizeChanged { measuredSize ->
                pdfContainerSize = measuredSize
            }
            .pointerInput(Unit) {
                val swipeThresholdPx = with(density) { 50.dp.toPx() }
                val touchSlop = viewConfiguration.touchSlop
                var accumulatedDrag: Float
                var swipeHandledThisGesture: Boolean
                var totalDragDistance: Offset

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isTap = true 
                    accumulatedDrag = 0f
                    swipeHandledThisGesture = false
                    totalDragDistance = Offset.Zero


                    drag(down.id) { change ->
                        if (swipeHandledThisGesture) {
                            change.consume()
                        } else {
                            val dragAmount = change.positionChange()
                            accumulatedDrag += dragAmount.x
                            totalDragDistance += dragAmount
                            change.consume()

                            if (abs(totalDragDistance.x) > touchSlop || abs(totalDragDistance.y) > touchSlop) {
                                isTap = false
                            }

                            if (accumulatedDrag > swipeThresholdPx) {
                                turnPagePrevious()
                                swipeHandledThisGesture = true
                                isTap = false 
                            } else if (accumulatedDrag < -swipeThresholdPx) {
                                turnPageNext()
                                swipeHandledThisGesture = true
                                isTap = false
                            }
                        }
                    }

                   
                    if (isTap && !swipeHandledThisGesture) {
                        showControlsAndResetTimer()
                    }
                }
            }
    ) {

        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${actualPageIndex + 1}",
                modifier = Modifier
                    .fillMaxSize()
            )
        } else if (!isLoading) {
            Text(
                "No page to display or page index out of bounds.",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }


        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PdfControlsOverlay(
                modifier = Modifier.fillMaxSize(),
                statusText = statusText,
                pageCount = renderer.pageCount,
                currentPage = if (actualPageIndex >= 0 && actualPageIndex < renderer.pageCount) actualPageIndex + 1 else 0,
                onNextPage = { turnPageNext() },
                onPrevPage = { turnPagePrevious() },
                deviceArrangement = deviceArrangement,
                isLeader = isLeader,
                onStartSessionClicked = {
                    onStartSessionClicked()
                    showControlsAndResetTimer()
                },
                onInteraction = { showControlsAndResetTimer() }
            )
        }
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
    deviceArrangement: List<Device>,
    isLeader: Boolean,
    onStartSessionClicked: () -> Unit,
    onInteraction: () -> Unit
) {
    val controlBackground = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .pointerInput(Unit) {
            detectTapGestures(onTap = { onInteraction() })
        }

    Box(modifier = modifier.padding(8.dp)) {

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(controlBackground),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = statusText)
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {

            IconButton(onClick = { onPrevPage(); onInteraction() }, modifier = controlBackground) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
            }

            Text(text = "$currentPage / $pageCount", modifier = controlBackground.then(Modifier.pointerInput(Unit){ detectTapGestures(onTap = {onInteraction()})}))

            IconButton(onClick = { onNextPage(); onInteraction() }, modifier = controlBackground) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
            }

            if (isLeader) {
                IconButton(onClick = { onStartSessionClicked(); onInteraction() }, modifier = controlBackground) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "Start Session")
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(controlBackground)
        ) {
            deviceArrangement.forEachIndexed { index, device ->
                Icon(
                    imageVector = Icons.Default.Tablet,
                    contentDescription = "Device ${index + 1}",
                    tint = if (device.isThisDevice) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
