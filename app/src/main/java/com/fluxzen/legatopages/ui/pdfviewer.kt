package com.fluxzen.legatopages.ui


import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fluxzen.legatopages.LegatoPagesViewModel
import com.fluxzen.legatopages.UiState
import com.pratikk.jetpdfvue.HorizontalVueReader
import com.pratikk.jetpdfvue.state.HorizontalVueReaderState
import com.pratikk.jetpdfvue.state.VueLoadState
import com.pratikk.jetpdfvue.state.VueResourceType
import com.pratikk.jetpdfvue.state.rememberHorizontalVueReaderState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PdfViewer(
    modifier: Modifier = Modifier,
    uiState: UiState,
    vm: LegatoPagesViewModel,
    pdfUri: Uri,
    onLoadNewDocumentClicked: () -> Unit,
) {
   
    val vueReaderState = rememberHorizontalVueReaderState(resource = VueResourceType.Local(pdfUri))

    val remotePageRequest by vm.remotePageRequest.collectAsState()
    val coroutineScope = rememberCoroutineScope()

   
   
    LaunchedEffect(vueReaderState.currentPage) {
        if (vueReaderState.vueLoadState is VueLoadState.DocumentLoaded) {
            vm.onPageChangedByUi(vueReaderState.currentPage - 1)
        }
    }

    LaunchedEffect(remotePageRequest) {
        remotePageRequest?.let { page ->
            val targetPage = page + 1
            if (targetPage != vueReaderState.currentPage) {
                val currentPage = vueReaderState.currentPage
                if (targetPage > currentPage) {
                    coroutineScope.launch {
                        for (i in currentPage until targetPage) {
                            vueReaderState.nextPage(); delay(1)
                        }
                    }
                } else {
                    coroutineScope.launch {
                        for (i in currentPage downTo targetPage + 1) {
                            vueReaderState.prevPage(); delay(1)
                        }
                    }
                }
            }
            vm.consumeRemotePageRequest()
        }
    }

   
    Column(modifier = modifier.fillMaxSize()) {
       
        ConnectionTopBar(
            uiState = uiState,
            onBecomeLeader = { vm.startAdvertising() },
            onBecomeFollower = { vm.startDiscovery() }
        )

       
        PdfContent(
            modifier = Modifier.weight(1f),
            vueReaderState = vueReaderState,
            pdfUri = pdfUri
        )

       
        if (vueReaderState.vueLoadState is VueLoadState.DocumentLoaded) {
            NavigationBottomBar(
                vueReaderState = vueReaderState,
                onLoadNewDocumentClicked = onLoadNewDocumentClicked
            )
        }
    }
}

@Composable
fun PdfContent(
    modifier: Modifier = Modifier,
    vueReaderState: HorizontalVueReaderState,
    pdfUri: Uri,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf<IntSize?>(null) }
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .onSizeChanged { containerSize = it },
        contentAlignment = Alignment.Center
    ) {
       
        LaunchedEffect(key1 = containerSize, key2 = pdfUri, key3 = isPortrait) {
            containerSize?.let {
                vueReaderState.load(
                    context, coroutineScope, it, isPortrait,
                    customResource = null
                )
            }
        }

        when (vueReaderState.vueLoadState) {
            is VueLoadState.DocumentLoaded -> {
                HorizontalVueReader(
                    modifier = Modifier.fillMaxSize(),
                    horizontalVueReaderState = vueReaderState
                )
            }

            is VueLoadState.DocumentError -> {
                Text("Error loading document", color = Color.White)
            }

            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Loading PDF...", color = Color.White)
                }
            }
        }
    }
}
