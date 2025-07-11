package com.fluxzen.legatopages

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val syncManager = SyncManager(application.applicationContext)

    override fun onCleared() {
        super.onCleared()

        syncManager.shutdown()
        Log.d("MainViewModel", "SyncManager shutdown")
    }
}

