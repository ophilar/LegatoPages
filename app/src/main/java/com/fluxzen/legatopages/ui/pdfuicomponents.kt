package com.fluxzen.legatopages.ui

import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!uiState.isConnected) {
           
            Button(onClick = onBecomeLeader) { Text("Leader") }
            Button(onClick = onBecomeFollower) { Text("Follower") }
            Spacer(Modifier.weight(1f))
            Text(uiState.connectionStatus)
        } else {
           
            val role = if (uiState.isLeader) "Leader" else "Follower"
            Text("Role: $role", style = MaterialTheme.typography.titleMedium)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
       
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

        Spacer(Modifier.weight(1f))

       
        Text(
            text = "${vueReaderState.currentPage} / ${vueReaderState.pdfPageCount}",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.weight(1f))

       
        IconButton(onClick = onLoadNewDocumentClicked) {
            Icon(Icons.Default.KeyboardArrowUp, "Load New Document", modifier = Modifier.size(28.dp))
        }

       
        IconButton(onClick = { vueReaderState.rotate(-90f) }) {
            Icon(Icons.Default.Refresh, "Rotate", modifier = Modifier.size(28.dp))
        }

       
        IconButton(onClick = { vueReaderState.sharePDF(context) }) {
            Icon(Icons.Default.Share, "Share PDF", modifier = Modifier.size(28.dp))
        }

        Spacer(Modifier.weight(1f))

       
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