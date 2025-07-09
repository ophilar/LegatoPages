package com.fluxzen.legatopages.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Size
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfPageRenderer(context: Context, pdfUri: Uri) {

    private val parcelFileDescriptor: ParcelFileDescriptor? by lazy {
        context.contentResolver.openFileDescriptor(
            pdfUri,
            "r"
        )
    }

    private val pdfRenderer: PdfRenderer? by lazy {
        parcelFileDescriptor?.let { PdfRenderer(it) }
    }

    val pageCount: Int by lazy { pdfRenderer?.pageCount ?: 0 }

    suspend fun renderPage(pageIndex: Int, destSize: Size): Bitmap? {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= pageCount) {
            return null
        }

        return withContext(Dispatchers.Default) {
            val page = pdfRenderer!!.openPage(pageIndex)

            val scale = minOf(
                destSize.width.toFloat() / page.width,
                destSize.height.toFloat() / page.height
            )

            val bitmap = createBitmap((page.width * scale).toInt(), (page.height * scale).toInt())
            val matrix = Matrix().apply {
                postScale(scale, scale)
            }
            bitmap.eraseColor(android.graphics.Color.WHITE)

            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        }
    }

    fun close() {
        if (pdfRenderer != null) {
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
        }
    }
}