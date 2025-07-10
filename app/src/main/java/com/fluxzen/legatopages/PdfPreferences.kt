package com.fluxzen.legatopages

import android.content.Context
import androidx.core.content.edit

// Storing the file hash is more reliable than a URI, which can become invalid.
data class LastPosition(val fileHash: String, val bookPage: Int)

class PdfPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("LegatoPagesPrefs", Context.MODE_PRIVATE)

    fun saveLastPosition(fileHash: String, bookPage: Int) {
        prefs.edit {
            putString("lastFileHash", fileHash)
            putInt("lastBookPage", bookPage)
        }
    }

    fun getLastPosition(): LastPosition? {
        val fileHash = prefs.getString("lastFileHash", null)
        val bookPage = prefs.getInt("lastBookPage", 0)

        return fileHash?.let {
            LastPosition(fileHash = it, bookPage = bookPage)
        }
    }

    fun clearLastPosition() {
        prefs.edit {
            remove("lastFileHash")
            remove("lastBookPage")
        }
    }
}
