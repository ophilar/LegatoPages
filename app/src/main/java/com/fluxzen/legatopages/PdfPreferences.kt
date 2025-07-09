package com.fluxzen.legatopages

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

data class LastPosition(val uri: Uri, val bookPage: Int)

class PdfPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("LegatoPagesPrefs", Context.MODE_PRIVATE)

    fun saveLastPosition(uri: Uri, bookPage: Int) {
        prefs.edit {
            putString("lastPdfUri", uri.toString())
            putInt("lastBookPage", bookPage)
        }
    }

    fun getLastPosition(): LastPosition? {
        val uriString = prefs.getString("lastPdfUri", null)
        val bookPage = prefs.getInt("lastBookPage", 0)

        return uriString?.let {
            LastPosition(uri = it.toUri(), bookPage = bookPage)
        }
    }
}