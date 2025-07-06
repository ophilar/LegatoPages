package com.fluxzen.legatopages.ui

// (Add all necessary imports here for Icons, Compose, etc.)
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fluxzen.legatopages.UiState
import com.pratikk.jetpdfvue.state.HorizontalVueReaderState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionTopBar(
    uiState: UiState,
    onBecomeLeader: () -> Unit,
    onBecomeFollower: () -> Unit,
) {
    // A TopAppBar provides better structure and background handling
    TopAppBar(
        title = {
            Text(
                if (uiState.isConnected) {
                    val role = if (uiState.isLeader) "Leader" else "Follower"
                    "Role: $role (${uiState.connectionStatus})"
                } else {
                    uiState.connectionStatus
                }
            )
        },
        actions = {
            if (!uiState.isConnected) {
                TextButton(onClick = onBecomeLeader) { Text("Leader") }
                TextButton(onClick = onBecomeFollower) { Text("Follower") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
fun NavigationBottomBar(
    vueReaderState: HorizontalVueReaderState,
    onLoadNewDocumentClicked: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    BottomAppBar(
        containerColor = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
        actions = {
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

            // Page Indicator
            Text(
                text = "${vueReaderState.currentPage} / ${vueReaderState.pdfPageCount}",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.weight(1f))

            IconButton(onClick = onLoadNewDocumentClicked) {
                Icon(Icons.Filled.Info, "Load New Document", modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { vueReaderState.rotate(-90f) }) {
                Icon(Icons.Filled.Refresh, "Rotate", modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { vueReaderState.sharePDF(context) }) {
                Icon(Icons.Filled.Share, "Share PDF", modifier = Modifier.size(28.dp))
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
    )
}