package com.fluxzen.legatopages

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.content.edit

class LegatoPagesViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _remotePageRequest = MutableStateFlow<Int?>(null)
    val remotePageRequest: StateFlow<Int?> = _remotePageRequest.asStateFlow()

    private val syncManager = SyncManager(
        application,
        onConnectionResult = { isConnected, isLeader ->
            _uiState.update {
                it.copy(
                    isConnected = isConnected,
                    isLeader = if (isConnected) isLeader else false 
                )
            }
        },
        onStatusUpdate = { status -> _uiState.update { it.copy(connectionStatus = status) } },
        onPageReceived = { page -> _remotePageRequest.value = page }
    ).apply {
        
    }

    init {
        loadLastOpenedPdf()
    }

    private fun getInternalFile(): File {
        return File(getApplication<Application>().filesDir, "selected_score.pdf")
    }

   fun onUriSelected(selectedUri: Uri) {
        viewModelScope.launch {
            try {
                val internalFile = getInternalFile()

                getApplication<Application>().contentResolver.openInputStream(selectedUri)?.use { input ->
                    internalFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Could not open input stream")

                
                
                val internalUri = internalFile.toUri()

                
                val prefs = getApplication<Application>().getSharedPreferences("LegatoPagesPrefs", Context.MODE_PRIVATE)
                prefs.edit { putBoolean("lastPdfExists", true) }

                _uiState.update { it.copy(pdfUri = internalUri) }
            } catch (e: Exception) {
                _uiState.update { it.copy(connectionStatus = "Error loading file: ${e.message}") }
            }
        }
    }

    private fun loadLastOpenedPdf() {
        val prefs = getApplication<Application>().getSharedPreferences("LegatoPagesPrefs", Context.MODE_PRIVATE)
        val pdfExists = prefs.getBoolean("lastPdfExists", false)

        if (pdfExists) {
            val internalFile = getInternalFile()
            if (internalFile.exists()) {
                
                _uiState.update { it.copy(pdfUri = internalFile.toUri()) }
            } else {
                
                prefs.edit { remove("lastPdfExists") }
            }
        }
    }

    fun onPageChangedByUi(page: Int) {
        if (_uiState.value.isLeader) {
            syncManager.sendPage(page)
        }
    }

     fun startAdvertising() {
        _uiState.update { it.copy(isLeader = true) } 
        syncManager.startAdvertising()
    }

    fun startDiscovery() {
        _uiState.update { it.copy(isLeader = false) }
        syncManager.startDiscovery()
    }
    
    fun consumeRemotePageRequest() {
        _remotePageRequest.value = null
    }
}

data class UiState(
    val pdfUri: Uri? = null,
    val connectionStatus: String = "Not Connected",
    val isLeader: Boolean = false,
    val isConnected: Boolean = false,
)
