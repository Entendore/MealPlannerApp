package com.example.mealplanner.util

import android.content.Context
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mealplanner.data.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PdfExporterTest {

    @Test
    fun shoppingListPdfProducesARenderableSinglePageDocument() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "test-shopping.pdf")
        FileOutputStream(file).use {
            PdfExporter.writeShoppingListPdf(
                context, "Test day",
                listOf(ShoppingItem("Milk", "l", 1.5, 2.3, "Dairy")),
                budget = 10.0, userAllergies = setOf("Dairy"), output = it
            )
        }
        assertTrue(file.length() > 500)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pdf ->
            assertEquals(1, PdfRenderer(pdf).use { it.pageCount })
        }
        file.delete()
    }
}
