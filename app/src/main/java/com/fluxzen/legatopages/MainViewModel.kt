package com.fluxzen.legatopages

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val syncManager = SyncManager(application.applicationContext)
}

