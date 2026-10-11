package com.example.mealplanner.util

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.Shadows

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrShareTest {

    // consolidation fix: uri.path is the LOGICAL FileProvider path, not the real
    // file — the PNG is verified through the ContentResolver instead.
    @Test
    fun `writePng produces a decodable file and share launches a png chooser`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)

        val uri = QrShare.writePng(context, bmp, "qr.png")
        val decoded = context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it) }
        assertNotNull(decoded)
        assertEquals(50, decoded!!.width)

        val activity = Robolectric.setupActivity(androidx.activity.ComponentActivity::class.java)
        QrShare.shareUri(activity, uri)
        val chooser = Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/png", send.type)
        assertNotNull(send.clipData)                       // grant flag carrier
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
