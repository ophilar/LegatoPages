package com.fluxzen.legatopages

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.android.gms.nearby.connection.Payload
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class CacheManager(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
    private val cacheDuration = TimeUnit.DAYS.toMillis(7)

    fun getFileHash(inputStream: InputStream): String {
        val md = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)
        var read: Int
        while (inputStream.read(buffer).also { read = it } > 0) {
            md.update(buffer, 0, read)
        }
        return BigInteger(1, md.digest()).toString(16).padStart(32, '0')
    }

    fun cacheFileFromUri(uri: Uri): Pair<String, File>? {
        try {
            val fileName = getFileName(uri) ?: "cached_file_${System.currentTimeMillis()}"
            val tempFile = File(cacheDir, "$fileName.tmp")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                tempFile.inputStream().use { hashInputStream ->
                    val hash = getFileHash(hashInputStream)
                    val finalFile = File(cacheDir, hash)

                    if (finalFile.exists()) {
                        tempFile.delete()
                    } else {
                        tempFile.renameTo(finalFile)
                    }
                    return hash to finalFile
                }
            }
        } catch (e: Exception) {
            Log.e("CacheManager", "Error caching file from URI", e)
        }
        return null
    }


    fun saveReceivedFile(payload: Payload): File? {
        try {
            when (payload.type) {
                Payload.Type.FILE -> {
                    payload.asFile()?.let { payloadFile ->

                        if (!cacheDir.exists()) {
                            cacheDir.mkdirs()
                        }


                        val tempFileName = "received_payload_${payload.id}.tmp"
                        val cachedFile = File(cacheDir, tempFileName)

                        try {

                            payloadFile.asParcelFileDescriptor().use { pfd ->
                                FileInputStream(pfd.fileDescriptor).use { inputStream ->
                                    FileOutputStream(cachedFile).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            }

                            Log.d(
                                "CacheManager",
                                "File payload copied to: ${cachedFile.absolutePath}"
                            )
                            return cachedFile
                        } catch (e: IOException) {
                            Log.e("CacheManager", "Error copying file from payload to cache", e)

                            if (cachedFile.exists()) {
                                cachedFile.delete()
                            }
                        }
                    } ?: Log.e("CacheManager", "File payload was null for ID: ${payload.id}")
                }

                Payload.Type.STREAM -> {


                    Log.d("CacheManager", "Handling stream payload ID: ${payload.id}")
                    val tempFileName = "received_stream_${payload.id}.tmp"
                    val cachedFile = File(cacheDir, tempFileName)
                    try {
                        payload.asStream()?.asInputStream()?.use { inputStream ->
                            FileOutputStream(cachedFile).use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        } ?: run {
                            Log.e(
                                "CacheManager",
                                "Stream payload's InputStream was null for ID: ${payload.id}"
                            )
                            return null
                        }
                        Log.d(
                            "CacheManager",
                            "Stream payload copied to: ${cachedFile.absolutePath}"
                        )
                        return cachedFile
                    } catch (e: IOException) {
                        Log.e("CacheManager", "Error saving stream payload to cache", e)
                        if (cachedFile.exists()) {
                            cachedFile.delete()
                        }
                    }
                }

                Payload.Type.BYTES -> {
                    Log.w(
                        "CacheManager",
                        "BYTES payload received in saveReceivedFile. This method is intended for FILE or STREAM payloads."
                    )

                }

                else -> Log.w("CacheManager", "Unhandled payload type: ${payload.type}")
            }
        } catch (e: Exception) {
            Log.e("CacheManager", "Error saving received file for payload ID: ${payload.id}", e)
        }
        return null
    }

    fun getCachedFile(fileHash: String): File? {
        val file = File(cacheDir, fileHash)
        return if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
            file
        } else {
            null
        }
    }

    fun getCachedFile(fileName: String, expectedHash: String): File? {
        val file = File(cacheDir, fileName)
        if (!file.exists()) return null


        try {
            file.inputStream().use {
                if (getFileHash(it) == expectedHash) {
                    file.setLastModified(System.currentTimeMillis())
                    return file
                }
            }
        } catch (e: IOException) {
            Log.e("CacheManager", "Error reading cached file for hashing: ${file.name}", e)
            return null
        }
        return null
    }

    fun cleanCache() {
        val cutoff = System.currentTimeMillis() - cacheDuration
        cacheDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                if (file.delete()) {
                    Log.d("CacheManager", "Deleted old cached file: ${file.name}")
                } else {
                    Log.w("CacheManager", "Failed to delete old cached file: ${file.name}")
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex >= 0) {
                        result = cursor.getString(columnIndex)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                if (cut != null) {
                    result = result.substring(cut + 1)
                }
            }
        }
        return result
    }
}
