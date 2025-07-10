package com.fluxzen.legatopages.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class PdfPageRenderer(context: Context, private val pdfUri: Uri) {

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var _pageCount: Int = 0
    private var initializationOk: Boolean = false

    val pageCount: Int
        get() = _pageCount

    init {
        try {
            parcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
            parcelFileDescriptor?.let { pfd ->
                pdfRenderer = PdfRenderer(pfd)
                _pageCount = pdfRenderer!!.pageCount 
                initializationOk = true
                Log.d("PdfPageRenderer", "Successfully initialized for URI: $pdfUri, Page count: $_pageCount")
            } ?: run {
                
                Log.w("PdfPageRenderer", "Failed to open ParcelFileDescriptor for URI: $pdfUri. URI might be invalid or file not accessible.")
                
                
            }
        } catch (e: IOException) {
            Log.e("PdfPageRenderer", "IOException during PdfPageRenderer initialization for $pdfUri", e)
            closeInternals() 
        } catch (e: SecurityException) {
            Log.e("PdfPageRenderer", "SecurityException during PdfPageRenderer initialization for $pdfUri", e)
            closeInternals()
        } catch (e: Exception) { 
            Log.e("PdfPageRenderer", "Unexpected error during PdfPageRenderer initialization for $pdfUri", e)
            closeInternals()
        }
    }

    suspend fun renderPage(pageIndex: Int, destSize: Size): Bitmap? {
        if (!initializationOk || pdfRenderer == null) {
            Log.w("PdfPageRenderer", "renderPage called but renderer not initialized or pdfRenderer is null. URI issue or prior init error.")
            return null
        }
        if (pageIndex < 0 || pageIndex >= _pageCount) {
            Log.w("PdfPageRenderer", "renderPage called with invalid pageIndex: $pageIndex (pageCount: $_pageCount).")
            return null
        }

        
        
        
        val currentRenderer = pdfRenderer!! 

        return withContext(Dispatchers.Default) { 
            try {
                currentRenderer.openPage(pageIndex).use { page ->
                    if (page.width <= 0 || page.height <= 0) {
                        Log.e("PdfPageRenderer", "Page $pageIndex has invalid dimensions: ${page.width}x${page.height}")
                        return@withContext null
                    }

                    val scale = minOf(
                        destSize.width.toFloat() / page.width,
                        destSize.height.toFloat() / page.height
                    )

                    if (scale <= 0f) { 
                         Log.e("PdfPageRenderer", "Calculated scale is non-positive: $scale. DestSize: ${destSize.width}x${destSize.height}, Page: ${page.width}x${page.height}")
                        return@withContext null
                    }

                    val bitmapWidth = (page.width * scale).toInt()
                    val bitmapHeight = (page.height * scale).toInt()

                    if (bitmapWidth <= 0 || bitmapHeight <= 0) {
                        Log.e("PdfPageRenderer", "Calculated bitmap dimensions are invalid: ${bitmapWidth}x$bitmapHeight. Scale: $scale")
                        return@withContext null
                    }

                    
                    val bitmap = createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    val matrix = Matrix().apply { 
                        postScale(scale, scale)
                    }
                    bitmap.eraseColor(Color.WHITE) 

                    
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } catch (e: Exception) {
                
                Log.e("PdfPageRenderer", "Error rendering page $pageIndex for $pdfUri", e)
                null
            }
        }
    }

    private fun closeInternals() {
        
        if (parcelFileDescriptor != null || pdfRenderer != null || initializationOk) {
            Log.d("PdfPageRenderer", "Closing PdfPageRenderer internals. Initialized: $initializationOk")
        }
        try {
            pdfRenderer?.close()
        } catch (e: Exception) {
            Log.e("PdfPageRenderer", "Error closing PdfRenderer.", e)
        }
        try {
            parcelFileDescriptor?.close()
        } catch (e: Exception) {
            Log.e("PdfPageRenderer", "Error closing ParcelFileDescriptor.", e)
        }
        
        pdfRenderer = null
        parcelFileDescriptor = null
        _pageCount = 0
        initializationOk = false
    }

    
    fun close() {
        closeInternals()
    }
}
