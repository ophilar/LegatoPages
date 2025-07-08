package com.fluxzen.legatopages.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap

class PdfPageRenderer(context: Context, pdfUri: Uri) {

    private val parcelFileDescriptor: ParcelFileDescriptor? =
        context.contentResolver.openFileDescriptor(pdfUri, "r")

    private val pdfRenderer: PdfRenderer? =
        parcelFileDescriptor?.let { PdfRenderer(it) }

    val pageCount: Int = pdfRenderer?.pageCount ?: 0

    suspend fun renderPage(pageIndex: Int): Bitmap? {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= pageCount) {
            return null
        }
        
        return withContext(Dispatchers.Default) {
            val page = pdfRenderer.openPage(pageIndex)
            val bitmap = createBitmap(page.width, page.height)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        }
    }

    fun close() {
        pdfRenderer?.close()
        parcelFileDescriptor?.close()
    }
}