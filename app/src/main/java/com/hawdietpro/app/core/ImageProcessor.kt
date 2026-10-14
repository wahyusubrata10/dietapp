package com.hawdietpro.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Mengecilkan foto (sisi terpanjang maks ~1024 px), memperbaiki rotasi EXIF, lalu
 * menyimpannya sebagai JPEG terkompresi di internal storage (files/photos).
 * File yang sama dipakai untuk dikirim ke Gemini dan ditampilkan di riwayat.
 */
@Singleton
class ImageProcessor @Inject constructor(@ApplicationContext private val context: Context) {

    val photoDir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    suspend fun importFromUri(uri: Uri, maxSize: Int, quality: Int): File = withContext(Dispatchers.IO) {
        val bytes = process({ context.contentResolver.openInputStream(uri) ?: error("Foto tidak bisa dibuka") }, maxSize, quality)
        save(bytes)
    }

    suspend fun importFromFile(file: File, maxSize: Int, quality: Int, deleteSource: Boolean = true): File = withContext(Dispatchers.IO) {
        val bytes = process({ file.inputStream() }, maxSize, quality)
        if (deleteSource) file.delete()
        save(bytes)
    }

    suspend fun readBytes(path: String): ByteArray = withContext(Dispatchers.IO) { File(path).readBytes() }

    private fun save(bytes: ByteArray): File {
        val out = File(photoDir, "meal_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        out.writeBytes(bytes)
        return out
    }

    private fun process(open: () -> InputStream, maxSize: Int, quality: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Format gambar tidak didukung" }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
        val decoded = open().use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Gagal membaca gambar")

        val orientation = runCatching {
            open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val longest = max(decoded.width, decoded.height)
        val scale = if (longest > maxSize) maxSize.toFloat() / longest else 1f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (result !== decoded) decoded.recycle()

        return ByteArrayOutputStream().use { stream ->
            result.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            result.recycle()
            stream.toByteArray()
        }
    }
}
