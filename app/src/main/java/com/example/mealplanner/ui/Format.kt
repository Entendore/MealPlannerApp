package com.example.mealplanner.ui

import com.example.mealplanner.data.Ingredient
import com.example.mealplanner.util.Units
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

// ½ ¼ ¾ render in every Android font (safe for PDFs); the rest are UI-only.
private val SIMPLE_FRACTIONS = linkedMapOf(0.5 to "½", 0.25 to "¼", 0.75 to "¾")
private val EXOTIC_FRACTIONS = linkedMapOf(
    0.125 to "⅛", 0.375 to "⅜", 0.625 to "⅝", 0.875 to "⅞",
    1.0 / 3.0 to "⅓", 2.0 / 3.0 to "⅔"
)

fun formatQuantity(quantity: Double, allowExoticFractions: Boolean = true): String {
    val whole = floor(quantity)
    val frac = quantity - whole
    if (frac > 1e-9 && frac < 1 - 1e-9) {
        val table = if (allowExoticFractions) SIMPLE_FRACTIONS + EXOTIC_FRACTIONS else SIMPLE_FRACTIONS
        table.entries.firstOrNull { abs(frac - it.key) < 0.02 }?.let { match ->
            return if (whole >= 1) "${whole.toInt()}${match.value}" else match.value
        }
    }
    return if (quantity % 1.0 == 0.0) quantity.toInt().toString()
    else String.format(Locale.US, "%.2f", quantity).trimEnd('0').trimEnd('.')
}

/** Quantity + unit with correct pluralization, e.g. "1.5 l", "2½ cups", "3 cloves". */
fun formatAmount(quantity: Double, unit: String, allowExoticFractions: Boolean = true): String {
    val q = formatQuantity(quantity, allowExoticFractions)
    val def = Units.resolve(unit) ?: return (q + " " + unit).trim()
    return "$q ${Units.displayForm(def, quantity)}".trim()
}

/** Locale-aware currency, e.g. "$12.40" / "12,40 €" — follows the device region. */
fun formatMoney(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.getDefault()).format(amount)

/** Just the symbol, used as a text-field prefix. */
fun currencySymbol(): String =
    (NumberFormat.getCurrencyInstance() as? DecimalFormat)
        ?.decimalFormatSymbols?.currencySymbol ?: "$"

fun formatIngredientLine(ingredient: Ingredient, allowExoticFractions: Boolean = true): String {
    val amount = when {
        ingredient.quantity != 0.0 ->
            formatAmount(ingredient.quantity, ingredient.unit, allowExoticFractions)
        ingredient.unit.isNotBlank() -> ingredient.unit
        else -> ""
    }
    return (if (amount.isBlank()) "" else "$amount ") + ingredient.name
}

fun formatDuration(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m >= 60) String.format(Locale.getDefault(), "%d:%02d:%02d", m / 60, m % 60, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}
