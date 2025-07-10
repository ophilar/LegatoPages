package com.fluxzen.legatopages.ui

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fluxzen.legatopages.Device
import com.fluxzen.legatopages.PageTurnDirection
import com.fluxzen.legatopages.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val HIDE_CONTROLS_DELAY_MS = 3000L
private const val MIN_ZOOM_SCALE = 1f
private const val MAX_ZOOM_SCALE = 5f
private const val BUTTON_BACKGROUND_ALPHA = 0.6f
private const val PERMANENT_CONTROLS_VISIBLE_ALPHA = 0.7f
private const val PERMANENT_CONTROLS_HIDDEN_ALPHA = 0.3f
private const val STATUS_BAR_WIDTH_FRACTION = 0.8f
private val ARROW_ICON_SIZE = 48.dp

private const val ANIMATION_LABEL_PERMANENT_CONTROLS = "PermanentControlAlpha"

@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    bookPage: Int,
    deviceArrangement: List<Device>,
    onTurnPage: (PageTurnDirection) -> Unit,
    onGoToPage: (Int) -> Unit,
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
        onDispose {
            renderer.close()
        }
    }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var pdfContainerSize by remember { mutableStateOf<IntSize?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val currentBookPage by rememberUpdatedState(bookPage)
    val currentOnTurnPage by rememberUpdatedState(onTurnPage)
    val currentDeviceArrangement by rememberUpdatedState(deviceArrangement)

    val (thisDeviceIndex, _) = if (isLocalViewingOnly) {
        0 to 1
    } else {
        val index =
            currentDeviceArrangement.indexOfFirst { it.isThisDevice }.takeIf { it != -1 } ?: 0
        val count = currentDeviceArrangement.size.coerceAtLeast(1)
        index to count
    }

    val actualPageIndex = currentBookPage + thisDeviceIndex

    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var hideControlsJob by remember { mutableStateOf<Job?>(null) }
    val isFollower = !isLocalViewingOnly && !isLeader

    fun scheduleHideControls() {
        hideControlsJob?.cancel()
        hideControlsJob = coroutineScope.launch {
            delay(HIDE_CONTROLS_DELAY_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(renderer, actualPageIndex, pdfContainerSize) {
        pdfContainerSize?.let { validSize ->
            if (actualPageIndex >= 0 && actualPageIndex < renderer.pageCount) {
                isLoading = true
                val renderSize = with(density) {
                    Size(validSize.width.toFloat(), validSize.height.toFloat())
                }
                bitmap = renderer.renderPage(actualPageIndex, renderSize)
                isLoading = false
            } else {
                bitmap = null
            }
        }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            scheduleHideControls()
        } else {
            hideControlsJob?.cancel()
        }
    }

    fun turnPageNext() {
        currentOnTurnPage(PageTurnDirection.NEXT)
        scale = 1f
        offset = Offset.Zero
    }

    fun turnPagePrevious() {
        currentOnTurnPage(PageTurnDirection.PREVIOUS)
        scale = 1f
        offset = Offset.Zero
    }

    var showPageDialog by remember { mutableStateOf(false) }

    val transparentButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Black.copy(alpha = BUTTON_BACKGROUND_ALPHA),
        contentColor = Color.White
    )
    val permanentControlAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) PERMANENT_CONTROLS_VISIBLE_ALPHA else PERMANENT_CONTROLS_HIDDEN_ALPHA,
        label = ANIMATION_LABEL_PERMANENT_CONTROLS
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .onSizeChanged { measuredSize ->
                pdfContainerSize = measuredSize
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = true)
                    var dragOccurred = false
                    var zoomOccurred = false
                    var horizontalSwipeLock = false

                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        if (zoom != 1f) zoomOccurred = true

                        val pan = event.calculatePan()

                        if (event.changes.size > 1) {
                            scale = (scale * zoom).coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        } else if (!zoomOccurred && !horizontalSwipeLock) {
                            if (abs(pan.x) > viewConfiguration.touchSlop) {
                                dragOccurred = true
                                horizontalSwipeLock = true
                                controlsVisible = false
                                if (pan.x < 0) {
                                    turnPageNext()
                                } else {
                                    turnPagePrevious()
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!dragOccurred && !zoomOccurred) {
                        controlsVisible = !controlsVisible
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
                IconButton(
                    onClick = ::turnPagePrevious,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = permanentControlAlpha), CircleShape)
                        .alpha(permanentControlAlpha)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Page",
                        tint = Color.White,
                        modifier = Modifier.size(ARROW_ICON_SIZE)
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            isLocalViewingOnly -> {
                                Button(
                                    onClick = onStartLeadingClicked,
                                    colors = transparentButtonColors
                                ) { Text("Start Leading") }
                                Button(
                                    onClick = onFindSessionClicked,
                                    colors = transparentButtonColors
                                ) { Text("Find Session") }
                            }

                            isLeader -> {
                                Button(
                                    onClick = onStopLeadingClicked,
                                    colors = transparentButtonColors
                                ) { Text("Stop Leading") }
                            }

                            else -> {
                                Button(
                                    onClick = onLeaveSessionClicked,
                                    colors = transparentButtonColors
                                ) { Text("Leave Session") }
                            }
                        }
                        Button(
                            onClick = onLoadDifferentPdfClicked,
                            colors = transparentButtonColors
                        ) { Text("Load New") }
                    }
                }

                IconButton(
                    onClick = ::turnPageNext,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = permanentControlAlpha), CircleShape)
                        .alpha(permanentControlAlpha)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Page",
                        tint = Color.White,
                        modifier = Modifier.size(ARROW_ICON_SIZE)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth(STATUS_BAR_WIDTH_FRACTION)
                    .background(
                        Color.Black.copy(alpha = permanentControlAlpha),
                        RoundedCornerShape(8.dp)
                    )
                    .alpha(permanentControlAlpha)
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
                        .clickable(enabled = !isFollower) {
                            if (!isFollower) {
                                showPageDialog = true
                            }
                        }
                        .padding(start = 4.dp)
                )
            }
        }

        if (showPageDialog) {
            GoToPageDialog(
                totalPages = renderer.pageCount,
                onDismiss = { showPageDialog = false },
                onConfirm = { page ->
                    onGoToPage(page - 1)
                    showPageDialog = false
                }
            )
        }
    }
}

@Composable
private fun GoToPageDialog(
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val isError = text.toIntOrNull()?.let { it < 1 || it > totalPages } ?: text.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_go_to_page)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.dialog_label_page_number, totalPages)) },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                if (isError) {
                    Text(
                        text = stringResource(R.string.dialog_error_invalid_page),
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
                Text(stringResource(R.string.button_go))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.legato_button_cancel))
            }
        }
    )
}
