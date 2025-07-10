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

class PdfPageRenderer(context: Context, private val pdfUri: Uri) {

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var _pageCount: Int = 0
    private var initializationOk: Boolean = false
    private val lock = ReentrantLock()

    val pageCount: Int
        get() = _pageCount

    init {
        try {
            parcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, PDF_READ_MODE)
            if (parcelFileDescriptor != null) {
                pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
                _pageCount = pdfRenderer!!.pageCount
                initializationOk = true
                Log.d(
                    TAG,
                    "Successfully initialized for URI: $pdfUri, Page count: $_pageCount"
                )
            } else {
                Log.w(
                    TAG,
                    "Failed to open ParcelFileDescriptor for URI: $pdfUri. URI might be invalid or file not accessible."
                )
                initializationOk = false
            }
        } catch (e: IOException) {
            Log.e(
                TAG,
                "IOException during PdfPageRenderer initialization for $pdfUri",
                e
            )
            closeInternals()
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "SecurityException during PdfPageRenderer initialization for $pdfUri",
                e
            )
            closeInternals()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Unexpected error during PdfPageRenderer initialization for $pdfUri",
                e
            )
            closeInternals()
        }
    }

    suspend fun renderPage(pageIndex: Int, destSize: androidx.compose.ui.geometry.Size): Bitmap? {
        if (!initializationOk) {
            Log.w(TAG, "renderPage called but renderer not initialized.")
            return null
        }
        return withContext(Dispatchers.Default) {
            lock.withLock {
                if (pdfRenderer == null) {
                    Log.w(TAG, "renderPage called after close().")
                    return@withContext null
                }
                if (pageIndex < 0 || pageIndex >= _pageCount) {
                    Log.w(
                        TAG,
                        "renderPage called with invalid pageIndex: $pageIndex (pageCount: $_pageCount)."
                    )
                    return@withContext null
                }

                val currentRenderer = pdfRenderer!!

                try {
                    currentRenderer.openPage(pageIndex).use { page ->
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
                    Log.e(TAG, "Error rendering page $pageIndex for $pdfUri", e)
                    null
                }
            }
        }
    }

    private fun closeInternals() {
        if (parcelFileDescriptor != null || pdfRenderer != null || initializationOk) {
            Log.d(
                TAG,
                "Closing PdfPageRenderer internals. Initialized: $initializationOk"
            )
        }
        try {
            pdfRenderer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing PdfRenderer.", e)
        }
        try {
            parcelFileDescriptor?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ParcelFileDescriptor.", e)
        }
        pdfRenderer = null
        parcelFileDescriptor = null
        _pageCount = 0
        initializationOk = false
    }

    fun close() {
        lock.withLock {
            closeInternals()
        }
    }
}