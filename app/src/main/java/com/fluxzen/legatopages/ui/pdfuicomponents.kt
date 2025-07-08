package com.fluxzen.legatopages.ui


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fluxzen.legatopages.UiState
import com.pratikk.jetpdfvue.state.HorizontalVueReaderState
import kotlinx.coroutines.launch

@Composable
fun ConnectionTopBar(
    uiState: UiState,
    onBecomeLeader: () -> Unit,
    onBecomeFollower: () -> Unit,
) {
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!uiState.isConnected) {
                Button(onClick = onBecomeLeader) { Text("Leader") }
                Button(onClick = onBecomeFollower) { Text("Follower") }
            } else {
                val role = if (uiState.isLeader) "Leader" else "Follower"
                Text("Role: $role", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            Text(uiState.connectionStatus)
        }
    }
}

@Composable
fun NavigationBottomBar(
    vueReaderState: HorizontalVueReaderState,
    onLoadNewDocumentClicked: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
           
            Text(
                text = "${vueReaderState.currentPage} / ${vueReaderState.pdfPageCount}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

           
            IconButton(onClick = onLoadNewDocumentClicked) {
                Icon(Icons.Filled.KeyboardArrowUp, "Load New Document")
            }
            IconButton(onClick = { vueReaderState.rotate(-90f) }) {
                Icon(Icons.Default.Refresh, "Rotate")
            }
            IconButton(onClick = { vueReaderState.sharePDF(context) }) {
                Icon(Icons.Default.Share, "Share PDF")
            }

            Spacer(Modifier.weight(1f))

           
            IconButton(
                onClick = { coroutineScope.launch { vueReaderState.prevPage() } },
                enabled = vueReaderState.currentPage > 1
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    "Previous Page",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(
                onClick = { coroutineScope.launch { vueReaderState.nextPage() } },
                enabled = vueReaderState.currentPage < vueReaderState.pdfPageCount
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    "Next Page",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}