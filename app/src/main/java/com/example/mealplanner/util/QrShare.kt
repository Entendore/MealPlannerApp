package com.example.mealplanner.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

/** Writes a bitmap to the FileProvider cache dir and launches the share sheet. */
object QrShare {

    /** Writes the bitmap to the FileProvider cache dir and returns a shareable Uri. */
    fun writePng(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val dir = File(context.cacheDir, "share")
            .apply { mkdirs(); listFiles()?.forEach { it.delete() } }   // no stale files
        val file = File(dir, fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    fun shareUri(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)   // some targets only read ClipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    fun share(context: Context, bitmap: Bitmap, fileName: String) =
        shareUri(context, writePng(context, bitmap, fileName))
}
