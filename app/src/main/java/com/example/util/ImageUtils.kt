package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageUtils {

    fun decodeDataUrlToBytes(uriOrData: String?): ByteArray? {
        if (uriOrData.isNullOrBlank()) return null
        if (!uriOrData.startsWith("data:image")) return null
        return try {
            val b64 = uriOrData.substringAfter("base64,", "")
            if (b64.isBlank()) null else Base64.decode(b64, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    fun resolveCoilModel(uriOrData: String?): Any? {
        if (uriOrData.isNullOrBlank()) return null
        if (uriOrData.startsWith("data:image")) {
            return decodeDataUrlToBytes(uriOrData) ?: uriOrData
        }
        return uriOrData
    }

    fun uriToBase64DataUrl(context: Context, uri: Uri, maxDimension: Int = 1024): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.isEmpty()) return null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            var sampleSize = 1
            val maxOriginal = max(options.outWidth, options.outHeight)
            while (maxOriginal > 0 && maxOriginal / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            if (bitmap != null) {
                bitmapToBase64DataUrl(bitmap)
            } else {
                val rawB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:image/jpeg;base64,$rawB64"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun bitmapToBase64DataUrl(bitmap: Bitmap, quality: Int = 85): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64String"
    }
}
