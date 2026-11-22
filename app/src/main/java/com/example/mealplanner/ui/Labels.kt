package com.example.mealplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mealplanner.R

/**
 * Resource-id mappings for canonical data keys (stored in English, displayed
 * localized). Centralized here — when you extend AllergenUtil.ALLERGENS or
 * DIETS, add the matching string + branch in the same commit (pinned by
 * LabelsTest).
 */
fun allergenLabelRes(tag: String): Int = when (tag) {
    "Gluten" -> R.string.allergen_gluten
    "Dairy" -> R.string.allergen_dairy
    "Eggs" -> R.string.allergen_eggs
    "Nuts" -> R.string.allergen_nuts
    "Peanuts" -> R.string.allergen_peanuts
    "Soy" -> R.string.allergen_soy
    "Fish" -> R.string.allergen_fish
    "Shellfish" -> R.string.allergen_shellfish
    else -> R.string.allergen_sesame
}

fun dietLabelRes(tag: String): Int = when (tag) {
    "Vegetarian" -> R.string.diet_vegetarian
    "Vegan" -> R.string.diet_vegan
    "Pescatarian" -> R.string.diet_pescatarian
    "Gluten-free" -> R.string.diet_gluten_free
    "Dairy-free" -> R.string.diet_dairy_free
    else -> R.string.diet_keto
}

fun mealTypeLabelRes(type: String): Int = when (type) {
    "Breakfast" -> R.string.breakfast
    "Lunch" -> R.string.lunch
    else -> R.string.dinner
}

@Composable fun allergenLabel(tag: String): String = stringResource(allergenLabelRes(tag))
@Composable fun dietLabel(tag: String): String = stringResource(dietLabelRes(tag))
@Composable fun mealTypeLabel(type: String): String = stringResource(mealTypeLabelRes(type))
