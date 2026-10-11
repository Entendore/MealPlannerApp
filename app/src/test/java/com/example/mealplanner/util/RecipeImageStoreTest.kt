package com.example.mealplanner.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeImageStoreTest {

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun writeSourceBitmap(w: Int, h: Int): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.RED)
        val file = File(context().cacheDir, "src-${UUID.randomUUID()}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return file
    }

    @Test
    fun `large images are downscaled stored as jpeg and compressed`() {
        val src = writeSourceBitmap(4000, 3000)
        val path = RecipeImageStore.save(context(), Uri.fromFile(src))
        assertNotNull(path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        assertEquals(1600, maxOf(bounds.outWidth, bounds.outHeight))
        assertEquals("image/jpeg", bounds.outMimeType)
        assertTrue(File(path!!).exists())
        src.delete()
    }

    @Test
    fun `portrait images also cap at the max dimension`() {
        val src = writeSourceBitmap(2000, 4000)
        val path = RecipeImageStore.save(context(), Uri.fromFile(src))!!
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        assertEquals(1600, maxOf(bounds.outWidth, bounds.outHeight))
        src.delete()
    }

    @Test
    fun `copyFile produces an independent file that survives deleting the original`() {
        val src = writeSourceBitmap(64, 64)
        val copy = RecipeImageStore.copyFile(src.absolutePath)!!
        assertNotEqualsCompat(src.absolutePath, copy)
        src.delete()                                    // original gone…
        assertTrue(File(copy).exists())                 // …copy unaffected
        assertNotNull(BitmapFactory.decodeFile(copy))
        RecipeImageStore.delete(copy)
        assertFalse(File(copy).exists())
    }

    private fun assertNotEqualsCompat(a: String, b: String) {
        if (a == b) throw AssertionError("expected different paths")
    }

    @Test
    fun `delete tolerates null and missing paths`() {
        RecipeImageStore.delete(null)
        RecipeImageStore.delete("/nope/missing.jpg")    // must not throw
    }

    @Test
    fun `save returns null for unparseable sources`() {
        val f = File(context().cacheDir, "bad-${UUID.randomUUID()}.jpg")
            .apply { writeText("not an image") }
        assertNullCompat(RecipeImageStore.save(context(), Uri.fromFile(f)))
        f.delete()
    }

    private fun assertNullCompat(value: Any?) {
        if (value != null) throw AssertionError("expected null")
    }
}
