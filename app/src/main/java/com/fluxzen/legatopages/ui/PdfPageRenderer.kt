package com.fluxzen.legatopages.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "PdfPageRenderer"
private const val PDF_READ_MODE = "r"

class PdfPageRenderer private constructor(
    private var pdfRenderer: PdfRenderer,
    private var parcelFileDescriptor: ParcelFileDescriptor,
) {
    private val lock = ReentrantLock()

    val pageCount: Int = pdfRenderer.pageCount

    suspend fun renderPage(pageIndex: Int, destSize: androidx.compose.ui.geometry.Size): Bitmap? {
        return withContext(Dispatchers.Default) {
            lock.withLock {

                if (pageIndex < 0 || pageIndex >= pageCount) {
                    Log.w(
                        TAG,
                        "renderPage called with invalid pageIndex: $pageIndex (pageCount: $pageCount)."
                    )
                    return@withContext null
                }

                try {
                    pdfRenderer.openPage(pageIndex).use { page ->
                        if (page.width <= 0 || page.height <= 0) {
                            Log.e(
                                TAG,
                                "Page $pageIndex has invalid dimensions: ${page.width}x${page.height}"
                            )
                            return@withContext null
                        }

                        val scale = minOf(
                            destSize.width / page.width,
                            destSize.height / page.height
                        )

                        if (scale <= 0f) {
                            Log.e(
                                TAG,
                                "Calculated scale is non-positive: $scale. DestSize: ${destSize.width}x${destSize.height}, Page: ${page.width}x${page.height}"
                            )
                            return@withContext null
                        }

                        val bitmapWidth = (page.width * scale).toInt()
                        val bitmapHeight = (page.height * scale).toInt()

                        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
                            Log.e(
                                TAG,
                                "Calculated bitmap dimensions are invalid: ${bitmapWidth}x$bitmapHeight. Scale: $scale"
                            )
                            return@withContext null
                        }

                        val bitmap =
                            createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                        val matrix = Matrix().apply {
                            postScale(scale, scale)
                        }
                        bitmap.eraseColor(Color.WHITE)

                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error rendering page $pageIndex", e)
                    null
                }
            }
        }
    }

    private fun closeInternals() {
        Log.d(
            TAG, "Closing PdfPageRenderer internals."
        )
        try {
            pdfRenderer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing PdfRenderer.", e)
        }
        try {
            parcelFileDescriptor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ParcelFileDescriptor.", e)
        }
    }

    fun close() {
        lock.withLock {
            closeInternals()
        }
    }

    companion object {
        suspend fun create(context: Context, pdfUri: Uri): Result<PdfPageRenderer> {
            return withContext(Dispatchers.IO) {
                var pfd: ParcelFileDescriptor? = null // Declare here to close on failure
                try {
                    // This code now runs safely on a background thread.
                    pfd = context.contentResolver.openFileDescriptor(pdfUri, PDF_READ_MODE)
                        ?: throw IOException("Failed to open ParcelFileDescriptor for URI: $pdfUri")
                    val renderer = PdfRenderer(pfd)
                    val pageRenderer = PdfPageRenderer(renderer, pfd)
                    Log.d(
                        TAG,
                        "Successfully initialized for URI: $pdfUri, Page count: ${pageRenderer.pageCount}"
                    )
                    Result.success(pageRenderer)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during PdfPageRenderer creation for $pdfUri", e)
                    pfd?.close()
                    Result.failure(e)
                }
            }
        }
    }
}