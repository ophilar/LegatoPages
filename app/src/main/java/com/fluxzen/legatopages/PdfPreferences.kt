package com.fluxzen.legatopages

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "LegatoPagesPrefs"
private const val KEY_LAST_OPENED_FILE_HASH = "lastOpenedFileHash"
private const val PREFIX_PAGE_FOR_FILE = "pageForFile_"
private const val DEFAULT_PAGE_NUMBER = 0

class PdfPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("LegatoPagesPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_OPENED_FILE_HASH = "lastOpenedFileHash"
        private const val PREFIX_PAGE_FOR_FILE = "pageForFile_"
    }

    /**
     * Saves the last viewed page for a specific file and updates
     * which file was the overall last one opened.
     */
    fun savePageForFile(fileHash: String, bookPage: Int) {
        prefs.edit {
            putString(KEY_LAST_OPENED_FILE_HASH, fileHash)
            putInt(PREFIX_PAGE_FOR_FILE + fileHash, bookPage)
        }
    }

    /**
     * Gets the last viewed page for a specific file hash.
     * Returns 0 if no page is stored for this file.
     */
    fun getPageForFile(fileHash: String): Int {
        return prefs.getInt(PREFIX_PAGE_FOR_FILE + fileHash, 0)
    }

    /**
     * Gets the file hash of the overall last opened PDF.
     * Returns null if no PDF has been opened yet.
     */
    fun getLastOpenedFileHash(): String? {
        return prefs.getString(KEY_LAST_OPENED_FILE_HASH, null)
    }

    /**
     * Clears the stored page position for a specific file.
     * (Optional, if you want to "forget" a file's position without clearing others)
     */
    fun clearPageForFile(fileHash: String) {
        prefs.edit {
            remove(PREFIX_PAGE_FOR_FILE + fileHash)
        }
    }
}