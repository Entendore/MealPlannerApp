package com.example.mealplanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.mealplanner.R
import com.example.mealplanner.data.MealPlanWithRecipe
import com.example.mealplanner.data.RecipeWithIngredients
import com.example.mealplanner.data.ShoppingItem
import com.example.mealplanner.ui.allergenLabelRes
import com.example.mealplanner.ui.dietLabelRes
import com.example.mealplanner.ui.formatAmount
import com.example.mealplanner.ui.formatMoney
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfExporter {

    private const val PAGE_WIDTH = 595f    // A4 at 72 dpi (points)
    private const val PAGE_HEIGHT = 842f
    private const val MARGIN = 48f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    private val DARK = Color.rgb(0x1C, 0x1B, 0x1F)
    private val PRIMARY = Color.rgb(0x2E, 0x6D, 0x32)
    private val GRAY = Color.rgb(0x75, 0x75, 0x75)
    private val LINE = Color.rgb(0xDD, 0xDD, 0xDD)
    private val ALERT = Color.rgb(0xB3, 0x26, 0x1E)

    private fun titlePaint() = TextPaint().apply {
        isAntiAlias = true; textSize = 20f; color = DARK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private fun subtitlePaint() = TextPaint().apply { isAntiAlias = true; textSize = 10f; color = GRAY }
    private fun headingPaint() = TextPaint().apply {
        isAntiAlias = true; textSize = 14f; color = PRIMARY
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private fun subHeadingPaint() = TextPaint().apply {
        isAntiAlias = true; textSize = 11f; color = DARK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private fun bodyPaint() = TextPaint().apply { isAntiAlias = true; textSize = 11f; color = DARK }
    private fun mutedPaint() = TextPaint().apply { isAntiAlias = true; textSize = 11f; color = GRAY }
    private fun alertPaint() = TextPaint().apply { isAntiAlias = true; textSize = 11f; color = ALERT }

    /** Tracks the current page + cursor (y), inserts page breaks, draws footers. */
    private class Writer(private val doc: PdfDocument, private val context: Context) {
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        var y = MARGIN
            private set

        val currentPage: Int get() = pageNo

        fun ensureSpace(needed: Float) {
            if (page == null || y + needed > PAGE_HEIGHT - MARGIN) newPage()
        }

        private fun newPage() {
            endPage()
            pageNo++
            val p = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNo).create()
            )
            page = p
            canvas = p.canvas
            y = MARGIN
        }

        fun breakPage() { newPage() }

        fun drawText(
            text: String,
            paint: TextPaint,
            x: Float = MARGIN,
            spacingAfter: Float = 0f,
            wrapWidth: Float? = null
        ) {
            val width = (wrapWidth ?: (PAGE_WIDTH - MARGIN - x)).toInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            ensureSpace(layout.height + spacingAfter)
            canvas?.save()
            canvas?.translate(x, y)
            layout.draw(canvas)
            canvas?.restore()
            y += layout.height + spacingAfter
        }

        /** Right-aligned on the current line (call ensureSpace first). */
        fun drawRightAligned(text: String, paint: TextPaint, rightX: Float) {
            canvas?.drawText(text, rightX - paint.measureText(text), y - paint.ascent(), paint)
        }

        fun drawRect(rect: RectF, paint: Paint) { canvas?.drawRect(rect, paint) }

        /** Draws a bitmap into a box; does not move the cursor (call space() after). */
        fun drawBitmap(bitmap: Bitmap, x: Float, top: Float, width: Float, height: Float) {
            canvas?.drawBitmap(
                bitmap, null, RectF(x, top, x + width, top + height),
                Paint().apply { isAntiAlias = true; isFilterBitmap = true }
            )
        }

        fun drawDivider(spacingAfter: Float = 6f) {
            ensureSpace(2f)
            canvas?.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y,
                Paint().apply { color = LINE; strokeWidth = 1f })
            y += spacingAfter
        }

        fun drawAccentBar(x: Float, width: Float, height: Float, color: Int) {
            canvas?.drawRect(RectF(x, y, x + width, y + height), Paint().apply { this.color = color })
        }

        fun space(amount: Float) { y += amount }

        fun finish() { endPage() }

        private fun endPage() {
            val c = canvas ?: return
            c.drawText(
                context.getString(R.string.pdf_page, pageNo),
                MARGIN, PAGE_HEIGHT - 20f,
                mutedPaint().apply { textSize = 9f }
            )
            page?.let { doc.finishPage(it) }
            page = null
            canvas = null
        }
    }

    private fun mealTypeName(context: Context, type: String): String = when (type) {
        "Breakfast" -> context.getString(R.string.breakfast)
        "Lunch" -> context.getString(R.string.lunch)
        else -> context.getString(R.string.dinner)
    }

    private fun generatedStamp(context: Context): String =
        context.getString(
            R.string.pdf_generated_on,
            LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.getDefault()))
        )

    // ------------------------------------------------------------------
    // Recipe rendering (shared by single-recipe and cookbook exports)
    // ------------------------------------------------------------------

    /** Decodes a photo scaled down to roughly the PDF's resolution to keep memory low. */
    private fun decodeScaled(path: String, targetPx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= targetPx) sampleSize *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } catch (e: Exception) {
            null
        }
    }

    private fun drawPhoto(w: Writer, path: String) {
        val bitmap = decodeScaled(path, targetPx = 1000) ?: return
        try {
            val scale = minOf(CONTENT_WIDTH / bitmap.width, 300f / bitmap.height, 1f)
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            w.ensureSpace(drawHeight + 12f)
            w.drawBitmap(bitmap, MARGIN, w.y, drawWidth, drawHeight)
            w.space(drawHeight + 12f)
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderRecipe(w: Writer, context: Context, item: RecipeWithIngredients) {
        val recipe = item.recipe

        w.drawText(recipe.name, titlePaint(), spacingAfter = 4f)
        w.drawText(generatedStamp(context), subtitlePaint(), spacingAfter = 14f)

        recipe.photoPath?.let { path -> drawPhoto(w, path) }

        val totalMinutes = recipe.prepTimeMinutes + recipe.cookTimeMinutes
        val parts = mutableListOf(recipe.category)
        if (totalMinutes > 0) {
            parts += context.getString(R.string.minutes_total, totalMinutes)
        }
        parts += context.resources.getQuantityString(
            R.plurals.servings_count, recipe.servings, recipe.servings)
        val cost = item.ingredients.sumOf { it.price }
        if (cost > 0) parts += context.getString(R.string.cost_total, formatMoney(cost))
        w.drawText(parts.joinToString("   ·   "), mutedPaint(), spacingAfter = 12f)

        // Dietary info: red "Contains" line + muted diets + adapted-for note
        val allergens = item.ingredients.flatMap { it.allergens }.distinct().sorted()
        if (allergens.isNotEmpty()) {
            val labels = allergens.joinToString { context.getString(allergenLabelRes(it)) }
            w.drawText(context.getString(R.string.pdf_contains, labels), alertPaint(), spacingAfter = 2f)
        }
        if (recipe.diets.isNotEmpty()) {
            val labels = recipe.diets.joinToString { context.getString(dietLabelRes(it)) }
            w.drawText(context.getString(R.string.pdf_diets, labels), mutedPaint(), spacingAfter = 12f)
        } else if (allergens.isNotEmpty()) {
            w.space(12f)
        }
        if (recipe.adaptedNote.isNotBlank()) {
            w.drawText(
                context.getString(R.string.pdf_adapted_for, recipe.adaptedNote),
                mutedPaint(),
                spacingAfter = if (recipe.diets.isEmpty()) 12f else 4f
            )
        }

        w.drawText(context.getString(R.string.ingredients), headingPaint(), spacingAfter = 6f)
        if (item.ingredients.isEmpty()) {
            w.drawText(context.getString(R.string.no_ingredients), mutedPaint())
        } else {
            item.ingredients.forEach { ingredient ->
                w.ensureSpace(18f)
                if (ingredient.quantity != 0.0) {
                    val qty = formatAmount(ingredient.quantity, ingredient.unit,
                        allowExoticFractions = false)   // PDF-safe fractions
                    w.drawRightAligned(qty, mutedPaint(), PAGE_WIDTH - MARGIN)
                }
                w.drawText("•  ${ingredient.name}", bodyPaint(),
                    x = MARGIN + 4f, spacingAfter = 4f, wrapWidth = CONTENT_WIDTH - 90f)
            }
        }
        w.space(8f)

        w.drawText(context.getString(R.string.instructions), headingPaint(), spacingAfter = 6f)
        val steps = recipe.instructions.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (steps.isEmpty()) {
            w.drawText(context.getString(R.string.no_instructions), mutedPaint())
        } else {
            // Auto-number multi-line steps unless the user already numbered them.
            val alreadyNumbered = steps.size > 1 && steps.all { it.firstOrNull()?.isDigit() == true }
            steps.forEachIndexed { index, step ->
                val line = if (steps.size > 1 && !alreadyNumbered) "${index + 1}. $step" else step
                w.drawText(line, bodyPaint(), spacingAfter = 8f)
            }
        }
        w.space(10f)

        // QR code so a printed recipe can be scanned back into the app
        RecipeQr.generateBitmap(RecipeQr.encode(item), 240)?.let { qr ->
            try {
                val qrSize = 80f
                w.ensureSpace(qrSize + 24f)
                w.drawRightAligned(context.getString(R.string.pdf_qr_caption), subtitlePaint(),
                    PAGE_WIDTH - MARGIN)
                w.space(4f)
                w.drawBitmap(qr, PAGE_WIDTH - MARGIN - qrSize, w.y, qrSize, qrSize)
                w.space(qrSize + 6f)
            } finally {
                qr.recycle()
            }
        }
    }

    private fun renderToc(
        w: Writer,
        context: Context,
        recipes: List<RecipeWithIngredients>,
        startPages: IntArray
    ) {
        w.drawText(context.getString(R.string.pdf_cookbook_title), titlePaint(), spacingAfter = 4f)
        w.drawText(
            context.resources.getQuantityString(
                R.plurals.pdf_recipes_count, recipes.size, recipes.size),
            subtitlePaint(), spacingAfter = 2f)
        w.drawText(generatedStamp(context), subtitlePaint(), spacingAfter = 14f)
        w.drawText(context.getString(R.string.pdf_toc_title), headingPaint(), spacingAfter = 8f)
        recipes.forEachIndexed { index, item ->
            w.ensureSpace(22f)
            val page = startPages.getOrElse(index) { 0 }
            if (page > 0) w.drawRightAligned(page.toString(), mutedPaint(), PAGE_WIDTH - MARGIN)
            w.drawText(item.recipe.name, bodyPaint(), spacingAfter = 8f)
        }
        w.breakPage()
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** One recipe on one or more pages, photo + QR included. */
    fun writeRecipePdf(context: Context, item: RecipeWithIngredients, output: OutputStream) {
        val doc = PdfDocument()
        val w = Writer(doc, context)
        try {
            renderRecipe(w, context, item)
            w.finish()
            doc.writeTo(output)
        } finally {
            doc.close()
        }
    }

    /** Contents page (with real page numbers) + one recipe per page. Two-pass: pass 1
     *  lays out into a throwaway doc to learn start pages; pass 2 renders for real. */
    fun writeCookbookPdf(context: Context, recipes: List<RecipeWithIngredients>, output: OutputStream) {
        val startPages = IntArray(recipes.size)
        run {
            val probe = PdfDocument()
            val w = Writer(probe, context)
            renderToc(w, context, recipes, startPages)   // numbers not drawn yet (all 0)
            recipes.forEachIndexed { index, item ->
                startPages[index] = w.currentPage
                renderRecipe(w, context, item)
                if (index != recipes.lastIndex) w.breakPage()
            }
            w.finish()
            probe.close()
        }

        val doc = PdfDocument()
        val w = Writer(doc, context)
        try {
            renderToc(w, context, recipes, startPages)
            recipes.forEachIndexed { index, item ->
                if (index > 0) w.breakPage()
                renderRecipe(w, context, item)
            }
            w.finish()
            doc.writeTo(output)
        } finally {
            doc.close()
        }
    }

    /** Weekly meal plan: one heading per day, meals grouped by Breakfast/Lunch/Dinner. */
    fun writeMealPlanPdf(
        context: Context,
        days: List<java.time.LocalDate>,
        mealsByDate: Map<String, List<MealPlanWithRecipe>>,
        output: OutputStream
    ) {
        val doc = PdfDocument()
        val w = Writer(doc, context)
        try {
            val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            val range = days.first().format(monthDay) + " – " + days.last().format(monthDay)

            w.drawText(context.getString(R.string.pdf_meal_plan_title, range), titlePaint(), spacingAfter = 4f)
            w.drawText(generatedStamp(context), subtitlePaint(), spacingAfter = 14f)

            val dayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
            val mealTypes = listOf("Breakfast", "Lunch", "Dinner")

            days.forEach { day ->
                val key = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val meals = mealsByDate[key].orEmpty()

                w.ensureSpace(70f)   // keep the day header together with its first line
                w.drawAccentBar(MARGIN, 4f, 16f, PRIMARY)
                w.drawText(day.format(dayFormatter), headingPaint(), x = MARGIN + 12f, spacingAfter = 2f)
                w.drawDivider(spacingAfter = 6f)

                if (meals.isEmpty()) {
                    w.drawText(context.getString(R.string.pdf_nothing_planned), mutedPaint(), x = MARGIN + 12f)
                } else {
                    mealTypes.forEach { type ->
                        val group = meals.filter { it.mealPlan.mealType == type }
                        if (group.isNotEmpty()) {
                            w.ensureSpace(26f)
                            w.drawText(mealTypeName(context, type), subHeadingPaint(),
                                x = MARGIN + 12f, spacingAfter = 2f)
                            group.forEach { entry ->
                                val servings =
                                    entry.mealPlan.servings.takeIf { it > 0 } ?: entry.recipe.servings
                                w.drawText(
                                    "•  " + context.getString(
                                        R.string.pdf_serving_line, entry.recipe.name, servings),
                                    bodyPaint(), x = MARGIN + 24f, spacingAfter = 3f
                                )
                            }
                        }
                    }
                }
                w.space(14f)
            }

            w.finish()
            doc.writeTo(output)
        } finally {
            doc.close()
        }
    }

    /** Shopping list with checkboxes, scaled amounts, cost column, total, budget,
     *  and red highlighting for items clashing with the user's allergens.
     *  periodLabel is pre-formatted (day or week range) by the caller. */
    fun writeShoppingListPdf(
        context: Context,
        periodLabel: String,
        items: List<ShoppingItem>,
        budget: Double?,
        userAllergies: Set<String>,
        output: OutputStream
    ) {
        val doc = PdfDocument()
        val w = Writer(doc, context)
        try {
            w.drawText(context.getString(R.string.pdf_shopping_list_title, periodLabel), titlePaint(), spacingAfter = 4f)
            w.drawText(generatedStamp(context), subtitlePaint(), spacingAfter = 2f)
            w.drawText(
                context.resources.getQuantityString(R.plurals.pdf_items_count, items.size, items.size),
                subtitlePaint(), spacingAfter = 16f)

            val boxPaint = Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 1.2f; color = DARK; isAntiAlias = true
            }

            items.forEach { item ->
                w.ensureSpace(26f)
                val qty = formatAmount(item.totalQuantity, item.unit, allowExoticFractions = false)
                val right = if (item.totalPrice > 0) "$qty  ·  ${formatMoney(item.totalPrice)}" else qty
                w.drawRightAligned(right, mutedPaint(), PAGE_WIDTH - MARGIN)

                val itemTags = item.allergens.split(',').filter { it.isNotBlank() }.toSet()
                val clash = itemTags intersect userAllergies
                w.drawRect(RectF(MARGIN, w.y, MARGIN + 12f, w.y + 12f), boxPaint)
                w.drawText(
                    if (clash.isEmpty()) item.name
                    else item.name + "  (" + clash.sorted()
                        .joinToString { context.getString(allergenLabelRes(it)) } + ")",
                    if (clash.isEmpty()) bodyPaint() else alertPaint(),
                    x = MARGIN + 20f, spacingAfter = 10f, wrapWidth = CONTENT_WIDTH - 120f
                )
            }

            w.space(8f)
            w.drawDivider()
            val total = items.sumOf { it.totalPrice }
            w.drawText(context.getString(R.string.pdf_total_line, formatMoney(total)), headingPaint(), spacingAfter = 4f)
            budget?.let { b ->
                val diff = b - total
                val status = if (diff >= 0) context.getString(R.string.budget_remaining, formatMoney(diff))
                else context.getString(R.string.budget_over, formatMoney(-diff))
                w.drawText(context.getString(R.string.pdf_budget_line, formatMoney(b), status), bodyPaint())
            }

            w.finish()
            doc.writeTo(output)
        } finally {
            doc.close()
        }
    }
}
