package com.fluxzen.legatopages

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class UiState(
    val connectionStatus: String = "Ready",
    val pdfUri: Uri? = null,
    val isLeader: Boolean = false,
    val isConnected: Boolean = false,
    val isAdvertising: Boolean = false,
    val isDiscovering: Boolean = false
)

class LegatoPagesViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _remotePageRequest = MutableStateFlow<Int?>(null)
    val remotePageRequest: StateFlow<Int?> = _remotePageRequest.asStateFlow()

    private val sharedPreferences = application.getSharedPreferences("LegatoPagesPrefs", Context.MODE_PRIVATE)

    private val syncManager = SyncManager(
        context = application.applicationContext,
        onStatusUpdate = { status ->
            _uiState.update {
                it.copy(
                    connectionStatus = status,
                    isConnected = status.startsWith("Connected to"),
                    isAdvertising = status.startsWith("Advertising started"),
                    isDiscovering = status.startsWith("Discovery started")
                )
            }
        },
        onPageReceived = { page ->
            _remotePageRequest.value = page
        }
    )

    init {
        loadLastOpenedPdf()
    }

    private fun loadLastOpenedPdf() {
        val uriString = sharedPreferences.getString("lastPdfUri", null)
        if (uriString != null) {
            _uiState.update { it.copy(pdfUri = uriString.toUri()) }
        }
    }

    fun onUriSelected(file: File) {
        _uiState.update { it.copy(pdfUri = file.toUri()) }
        sharedPreferences.edit().putString("lastPdfUri", file.toUri().toString()).apply()
    }

    fun startAdvertising() {
        if (_uiState.value.isAdvertising || _uiState.value.isConnected || _uiState.value.isDiscovering) {
            return
        }
        _uiState.update { it.copy(isLeader = true) }
        syncManager.startAdvertising()
    }

    fun startDiscovery() {
        if (_uiState.value.isDiscovering || _uiState.value.isConnected || _uiState.value.isAdvertising) {
            return
        }
        _uiState.update { it.copy(isLeader = false) }
        syncManager.startDiscovery()
    }

    fun onPageChangedByUi(page: Int) {
        if (_uiState.value.isLeader) {
            syncManager.sendPage(page)
        }
    }

    fun consumeRemotePageRequest() {
        _remotePageRequest.value = null
    }

    override fun onCleared() {
        syncManager.disconnect()
        super.onCleared()
    }
}