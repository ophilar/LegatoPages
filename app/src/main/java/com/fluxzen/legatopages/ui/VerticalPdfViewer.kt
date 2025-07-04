package com.fluxzen.legatopages.ui

import android.R.attr.maxHeight
import android.R.attr.maxWidth
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.pratikk.jetpdfvue.VerticalVueReader
import com.pratikk.jetpdfvue.state.VerticalVueReaderState
import com.pratikk.jetpdfvue.state.VueFilePicker
import com.pratikk.jetpdfvue.state.VueImportSources
import com.pratikk.jetpdfvue.state.VueLoadState
import com.pratikk.jetpdfvue.state.VueResourceType
import com.pratikk.jetpdfvue.state.rememberVerticalVueReaderState
import com.pratikk.jetpdfvue.util.compressImageToThreshold
import com.pratikk.jetpdfvue.util.getFileType
import kotlinx.coroutines.launch

@Composable
fun VerticalPDFReader() {
    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val context = LocalContext.current
        val vueFilePicker = rememberSaveable(saver = VueFilePicker.Saver) {
            VueFilePicker()
        }
        var uri: Uri by rememberSaveable(stateSaver = VueFilePicker.UriSaver) {
            mutableStateOf(Uri.EMPTY)
        }
        val launcher = vueFilePicker.getLauncher(onResult = {
            uri = it.toUri()
        })
        if (uri == Uri.EMPTY) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(onClick = {
                    vueFilePicker.launchIntent(
                        context, listOf(
                            VueImportSources.CAMERA,
                            VueImportSources.GALLERY,
                            VueImportSources.PDF
                        ), launcher
                    )
                }) {
                    Text(text = "Import Document")
                }
            }
        } else {
            val localImage = rememberVerticalVueReaderState(
                resource = VueResourceType.Local(
                    uri = uri,
                    fileType = uri.getFileType(context)
                )
            )
            VerticalPdfViewer(verticalVueReaderState = localImage)
        }
    }
}

@Composable
fun VerticalPdfViewer(verticalVueReaderState: VerticalVueReaderState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = verticalVueReaderState.getImportLauncher(interceptResult = {
        it.compressImageToThreshold(2)
    })

    Box(
        modifier = Modifier,
        contentAlignment = Alignment.Center
    ) {
        val configuration = LocalConfiguration.current
        val containerSize = remember {
            IntSize(maxWidth, maxHeight)
        }

        LaunchedEffect(Unit) {
            verticalVueReaderState.load(
                context = context,
                coroutineScope = scope,
                containerSize = containerSize,
                isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
                customResource = null
            )
        }
        when (verticalVueReaderState.vueLoadState) {
            is VueLoadState.NoDocument -> {
                Button(onClick = {
                    verticalVueReaderState.launchImportIntent(
                        context = context,
                        launcher = launcher
                    )
                }) {
                    Text(text = "Import Document")
                }
            }

            is VueLoadState.DocumentError -> {
                Column {
                    Text(text = "Error:  ${verticalVueReaderState.vueLoadState.getErrorMessage}")
                    Button(onClick = {
                        scope.launch {
                            verticalVueReaderState.load(
                                context = context,
                                coroutineScope = scope,
                                containerSize = containerSize,
                                isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
                                customResource = null
                            )
                        }
                    }) {
                        Text(text = "Retry")
                    }
                }
            }

            is VueLoadState.DocumentImporting -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(text = "Importing...")
                }
            }

            is VueLoadState.DocumentLoaded -> {
                VerticalSampleA(
                    modifier = Modifier.fillMaxHeight(),
                    verticalVueReaderState = verticalVueReaderState
                ) {
                    verticalVueReaderState.launchImportIntent(
                        context = context,
                        launcher = launcher
                    )
                }

            }

            is VueLoadState.DocumentLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(text = "Loading ${verticalVueReaderState.loadPercent}")
                }
            }
        }
    }
}


@Composable
fun VerticalSampleA(
    modifier: Modifier = Modifier,
    verticalVueReaderState: VerticalVueReaderState,
    import: () -> Unit,
) {
    Box(
        modifier = modifier
    ) {
        val scope = rememberCoroutineScope()

        val background = Modifier
            .background(
                MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                MaterialTheme.shapes.small
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.small
            )
            .clip(MaterialTheme.shapes.small)
        val iconTint = MaterialTheme.colorScheme.onBackground

        VerticalVueReader(
            modifier = Modifier.fillMaxSize(),
            contentModifier = Modifier.fillMaxSize(),
            verticalVueReaderState = verticalVueReaderState
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${verticalVueReaderState.currentPage} of ${verticalVueReaderState.pdfPageCount}",
                modifier = Modifier
                    .then(background)
                    .padding(10.dp)
            )
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Row {
                val context = LocalContext.current
                IconButton(
                    modifier = background,
                    onClick = import
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Page",
                        tint = iconTint
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                IconButton(
                    modifier = background,
                    onClick = { //Share
                        verticalVueReaderState.sharePDF(context)
                    }) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = iconTint
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val showPrevious by remember {
                derivedStateOf { verticalVueReaderState.currentPage != 1 }
            }
            val showNext by remember {
                derivedStateOf { verticalVueReaderState.currentPage != verticalVueReaderState.pdfPageCount }
            }
            if (showPrevious)
                IconButton(
                    modifier = background,
                    onClick = {
                        //Prev
                        scope.launch {
                            verticalVueReaderState.prevPage()
                        }
                    }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Previous",
                        tint = iconTint
                    )
                }
            else
                Spacer(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Transparent)
                )
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            IconButton(
                modifier = background,
                onClick = {
                    //Rotate
                    verticalVueReaderState.rotate(-90f)
                }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Rotate Left",
                    tint = iconTint
                )
            }
            Spacer(modifier = Modifier.width(5.dp))
            IconButton(
                modifier = background,
                onClick = {
                    //Rotate
                    verticalVueReaderState.rotate(90f)
                }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Rotate Right",
                    tint = iconTint
                )
            }
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            if (showNext)
                IconButton(
                    modifier = background,
                    onClick = {
                        //Next
                        scope.launch {
                            verticalVueReaderState.nextPage()
                        }
                    }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Next",
                        tint = iconTint
                    )
                }
            else
                Spacer(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Transparent)
                )
        }
    }
}