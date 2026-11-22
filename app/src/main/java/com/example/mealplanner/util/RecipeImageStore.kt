package com.example.mealplanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

/**
 * Copies picked gallery images into internal storage so the path stays valid
 * forever. New images are EXIF-rotated, downscaled to MAX_DIM on the long
 * edge, and JPEG-compressed — covers thumbnails, hero views and PDFs cheaply.
 */
object RecipeImageStore {

    private const val MAX_DIM = 1600      // px
    private const val JPEG_QUALITY = 85

    /** Decodes, EXIF-rotates, downscales and compresses. Returns the stored absolute path. */
    fun save(context: Context, uri: Uri): String? = try {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DIM) sample *= 2
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val rotated = if (rotation != 0f) {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(rotation) }, true)
                .also { if (it != raw) raw.recycle() }
        } else raw

        val scale = minOf(1f, MAX_DIM.toFloat() / maxOf(rotated.width, rotated.height))
        val finalBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                rotated,
                (rotated.width * scale).toInt().coerceAtLeast(1),
                (rotated.height * scale).toInt().coerceAtLeast(1),
                true
            ).also { if (it != rotated) rotated.recycle() }
        } else rotated

        val dir = File(context.filesDir, "recipe_images").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        finalBitmap.recycle()
        file.absolutePath
    } catch (e: Exception) {
        null
    }

    /** Physical copy for recipe duplication — deleting a copy never orphans the original. */
    fun copyFile(path: String?): String? {
        if (path == null) return null
        return try {
            val src = File(path)
            if (!src.exists()) return null
            val dst = File(src.parentFile, "${UUID.randomUUID()}.jpg")
            src.inputStream().use { input -> dst.outputStream().use { input.copyTo(it) } }
            dst.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }
}
