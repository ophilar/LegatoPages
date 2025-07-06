package com.fluxzen.legatopages.ui


import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
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
    onLoadNewDocumentClicked: () -> Unit
) {
    val vueReaderState = rememberHorizontalVueReaderState(resource = VueResourceType.Local(pdfUri))
    val remotePageRequest by vm.remotePageRequest.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var containerSize by remember { mutableStateOf<IntSize?>(null) }
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT


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


    LaunchedEffect(vueReaderState.currentPage) {
        if (vueReaderState.vueLoadState is VueLoadState.DocumentLoaded) {
            vm.onPageChangedByUi(vueReaderState.currentPage - 1)
        }
    }


    Scaffold(
        modifier = modifier,
        topBar = {
            ConnectionTopBar(
                uiState = uiState,
                onBecomeLeader = { vm.startAdvertising() },
                onBecomeFollower = { vm.startDiscovery() }
            )
        },
        bottomBar = {
            if (vueReaderState.vueLoadState is VueLoadState.DocumentLoaded) {
                NavigationBottomBar(
                    vueReaderState = vueReaderState,
                    onLoadNewDocumentClicked = onLoadNewDocumentClicked
                )
            }
        },
        containerColor = Color.Transparent
    ) { scaffoldPadding ->

        Box(
            modifier = Modifier
                .padding(scaffoldPadding)
                .fillMaxSize()
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

            val isLoaded = vueReaderState.vueLoadState is VueLoadState.DocumentLoaded

            if (isLoaded) {

                HorizontalVueReader(
                    modifier = Modifier.fillMaxSize(),
                    horizontalVueReaderState = vueReaderState
                )
            } else {

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Loading Document...")
                }
            }
        }
    }
}